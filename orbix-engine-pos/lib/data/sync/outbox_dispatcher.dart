/// Outbox dispatcher — drains the local outbox to the server.
///
/// Replaces the 30-line stub with the full offline-sync spine implementation
/// (US-POS-017/018). Design: docs/design/slice-sync-spine.md §2.
///
/// Guarantees:
/// - Idempotent by construction: every op carries a client-generated ULID that
///   the server deduplicates on. Safe to retry the whole batch after a flaky
///   connection.
/// - Per-op isolation: one REJECTED op never blocks its siblings.
/// - Ordering via dependsOn: DEFERRED ops are retried next cycle, not dropped.
/// - REJECTED ops surface in NEEDS_REVIEW for operator attention, never silently
///   dropped.
///
/// Connectivity: dispatches on reconnect (call flush()) and on a periodic timer.
/// Degrades cleanly offline — queue grows, UI stays usable.
library;

import 'dart:async';
import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:drift/drift.dart';
import 'package:logger/logger.dart';

import '../local/database.dart';
import 'outbox_repository.dart';
import 'sync_api_client.dart';
import 'sync_models.dart';
import 'sync_repository.dart';

/// Maximum ops per push batch (server cap per slice-sync-spine.md §5.1).
const kPushBatchSize = 100;

class OutboxDispatcher {
  OutboxDispatcher({
    required OutboxRepository outboxRepo,
    required SyncApiClient apiClient,
    required SyncRepository syncRepo,
    /// Device / till identifier sent in every push batch.
    /// Sourced from [PosConfigStore] via [deviceIdProvider]; defaults to 'TILL-1'.
    String? deviceId,
    Duration? pushInterval,
    Duration? pullInterval,
    Logger? logger,
  })  : _outbox = outboxRepo,
        _api = apiClient,
        _sync = syncRepo,
        _deviceId = deviceId ?? 'TILL-1',
        _pushInterval = pushInterval ?? const Duration(seconds: 5),
        _pullInterval = pullInterval ?? const Duration(seconds: 30),
        _log = logger ?? Logger();

  final OutboxRepository _outbox;
  final SyncApiClient _api;
  final SyncRepository _sync;
  final String _deviceId;
  final Duration _pushInterval;
  final Duration _pullInterval;
  final Logger _log;

  Timer? _pushTimer;
  Timer? _pullTimer;
  bool _flushing = false;
  bool _pulling = false;

  // ---------------------------------------------------------------------------
  // Lifecycle
  // ---------------------------------------------------------------------------

  void start() {
    _pushTimer ??= Timer.periodic(_pushInterval, (_) => flush());
    _pullTimer ??= Timer.periodic(_pullInterval, (_) => pullDeltas());
    _log.i('OutboxDispatcher started push=${_pushInterval.inSeconds}s pull=${_pullInterval.inSeconds}s');
    // On start, reset any INFLIGHT rows left by a previous crash.
    _outbox.resetInflight().ignore();
  }

  void stop() {
    _pushTimer?.cancel();
    _pushTimer = null;
    _pullTimer?.cancel();
    _pullTimer = null;
    _log.i('OutboxDispatcher stopped');
  }

  // ---------------------------------------------------------------------------
  // Push (outbox drain)
  // ---------------------------------------------------------------------------

  /// Flush all PENDING + DEFERRED ops to the server.
  /// Called by the timer and by the connectivity trigger.
  Future<void> flush() async {
    if (_flushing) return; // Guard against concurrent runs.
    _flushing = true;
    try {
      await _flushOnce();
    } catch (e, st) {
      _log.w('OutboxDispatcher.flush error (will retry): $e', error: e, stackTrace: st);
    } finally {
      _flushing = false;
    }
  }

  Future<void> _flushOnce() async {
    final batch = await _outbox.pendingOps(limit: kPushBatchSize);
    if (batch.isEmpty) return;

    _log.d('OutboxDispatcher._flushOnce ops=${batch.length}');

    // Resolve which ops have a confirmed dependency in a PRIOR push cycle.
    // Ops whose dependsOn op has not yet been confirmed locally are held back
    // from this batch so the session ACCEPTED verdict + back-fill can happen
    // before the dependent op is sent. This prevents the server receiving
    // tillSessionId=0 in the same batch where the session is first created.
    final confirmedOpIds = await _outbox.confirmedClientOpIds();
    final eligibleBatch = batch.where((row) {
      if (row.dependsOn == null) return true;
      // Include the op only if its dependency is already confirmed locally.
      return confirmedOpIds.contains(row.dependsOn);
    }).toList();

    if (eligibleBatch.isEmpty) {
      _log.d('OutboxDispatcher._flushOnce: all pending ops have unconfirmed '
          'dependencies — skipping batch, will retry next cycle');
      return;
    }

    final ops = eligibleBatch.map((r) => _rowToSyncOp(r, confirmedIds: confirmedOpIds)).toList();
    final request = SyncPushRequest(
      deviceId: _deviceId,
      clientContractVersion: kSyncContractVersion,
      ops: ops,
    );

    SyncPushResult result;
    try {
      result = await _api.push(request);
    } on DioException catch (e) {
      if (e.response?.statusCode == 426) {
        _log.e('OutboxDispatcher: CONTRACT_TOO_OLD — upgrade required. Halting push.');
        stop();
        return;
      }
      if (e.response?.statusCode == 409) {
        _log.w('OutboxDispatcher: CONTRACT_TOO_NEW — server behind. Backing off.');
        return;
      }
      // Network error — ops stay PENDING, will retry next tick.
      _log.w('OutboxDispatcher: network error ${e.message}');
      return;
    }

    // Apply per-op verdicts.
    for (final opResult in result.results) {
      await _applyVerdict(opResult);
    }

    // Handle resync flag (server wants us to drop caches and bootstrap).
    if (result.resyncRequired) {
      _log.w('OutboxDispatcher: server requested resync after push');
      pullDeltas().ignore();
    }

    // Recurse immediately if there are strictly-PENDING ops beyond the batch
    // limit (e.g. batch_size < total pending ops). Do NOT recurse for DEFERRED
    // ops — those are retried on the next timer tick once their dependsOn op
    // is ACCEPTED, not by hammering the server in the same cycle.
    if (eligibleBatch.length >= kPushBatchSize) {
      _log.d('OutboxDispatcher: batch full — flushing again for remaining PENDING ops');
      await _flushOnce();
    }
  }

  Future<void> _applyVerdict(SyncOpResult opResult) async {
    switch (opResult.verdict) {
      case SyncVerdict.accepted:
      case SyncVerdict.duplicate:
        // ACCEPTED and DUPLICATE are functionally identical — mark confirmed,
        // stamp server ids so the local sale row can be updated.
        await _outbox.markConfirmed(
          opResult.clientOpId,
          serverEntityId: opResult.serverEntityId,
          serverNumber: opResult.serverNumber,
        );
        // For TILL_SESSION_OPEN: back-fill the server session id into all
        // pending dependent ops (CASH_PICKUP, PETTY_CASH, POS_SALE) so the
        // server's applyCashPickup / applyPettyCash can find the session by id.
        if (opResult.serverEntityId != null) {
          await _backfillTillSessionId(
            sessionClientOpId: opResult.clientOpId,
            serverSessionId: opResult.serverEntityId!,
          );
          // Also stamp it on the TillSessions Drift row so the next flush
          // can read it without querying the outbox.
          await _stampTillSession(opResult);
        }
        // Flip PosSales.synced for POS_SALE ops.
        await _stampPosSale(opResult);
        _log.d('OutboxDispatcher verdict=CONFIRMED clientOpId=${opResult.clientOpId}');

      case SyncVerdict.rejected:
        // Permanent failure — surface for operator attention, do not auto-retry.
        await _outbox.markNeedsReview(opResult.clientOpId);
        _log.w('OutboxDispatcher verdict=NEEDS_REVIEW clientOpId=${opResult.clientOpId} '
            'error=${opResult.errorCode}: ${opResult.errorMessage}');

      case SyncVerdict.deferred:
        // dependsOn op not yet applied — mark DEFERRED so it re-queues next cycle.
        await _outbox.markDeferred(opResult.clientOpId);
        _log.d('OutboxDispatcher verdict=DEFERRED clientOpId=${opResult.clientOpId}');
    }
  }

  /// Back-fill the server-assigned session id into all PENDING / DEFERRED outbox
  /// ops that depend on [sessionClientOpId]. This ensures that CASH_PICKUP and
  /// PETTY_CASH ops have a valid tillSessionId in their payload before the next
  /// push, satisfying SyncServiceImpl.applyCashPickup / applyPettyCash which call
  /// payloadLong(op, "tillSessionId") as a required field.
  ///
  /// POS_SALE ops get tillSessionId back-filled too (PostPosSaleRequestDto.tillSessionId).
  Future<void> _backfillTillSessionId({
    required String sessionClientOpId,
    required String serverSessionId,
  }) async {
    final serverIdLong = int.tryParse(serverSessionId);
    if (serverIdLong == null) {
      _log.w('OutboxDispatcher._backfillTillSessionId: serverSessionId is not '
          'a number: $serverSessionId');
      return;
    }

    await _outbox.backfillTillSessionId(
      sessionClientOpId: sessionClientOpId,
      serverSessionId: serverIdLong,
      log: _log,
    );
  }

  /// Stamp the server entity id on the local TillSessions row.
  Future<void> _stampTillSession(SyncOpResult opResult) async {
    final db = _outbox.db;
    // Only applicable to TILL_SESSION_OPEN ops.
    // The outbox row doesn't carry opType, but the TillSessions table is keyed
    // by clientOpId — writing to a non-matching row is a no-op.
    await (db.update(db.tillSessions)
          ..where((t) => t.clientOpId.equals(opResult.clientOpId)))
        .write(TillSessionsCompanion(
      serverUid: Value(opResult.serverEntityId),
    ));
  }

  Future<void> _stampPosSale(SyncOpResult opResult) async {
    if (opResult.serverEntityId == null && opResult.serverNumber == null) return;
    // Attempt to stamp the PosSale row. No-op if the row doesn't exist (other op types).
    final db = _outbox.db;
    await (db.update(db.posSales)
          ..where((t) => t.clientOpId.equals(opResult.clientOpId)))
        .write(PosSalesCompanion(
      synced: const Value(true),
      serverEntityId: Value(opResult.serverEntityId),
      serverEntityUid: Value(opResult.serverEntityUid),
      serverNumber: Value(opResult.serverNumber),
    ));
  }

  // ---------------------------------------------------------------------------
  // Pull (reference-data delta)
  // ---------------------------------------------------------------------------

  Future<void> pullDeltas() async {
    if (_pulling) return;
    _pulling = true;
    try {
      await _sync.pull();
    } catch (e, st) {
      _log.w('OutboxDispatcher.pullDeltas error: $e', error: e, stackTrace: st);
    } finally {
      _pulling = false;
    }
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  SyncOp _rowToSyncOp(OutboxData row, {Set<String>? confirmedIds}) {
    final payload = jsonDecode(row.payloadJson) as Map<String, dynamic>;
    // If the op's dependsOn has already been confirmed in a PRIOR batch, strip
    // the dependsOn field from the wire message. The server's dependsOn guard
    // only scans ops within the current batch — sending a stale dependsOn would
    // cause the server to DEFER the op unnecessarily.
    final dependsOnWire = (row.dependsOn != null &&
            (confirmedIds == null || !confirmedIds.contains(row.dependsOn)))
        ? row.dependsOn
        : null;
    return SyncOp(
      clientOpId: row.clientOpId,
      opType: row.opType,
      seq: row.seq,
      occurredAt: row.occurredAt,
      dependsOn: dependsOnWire,
      payload: payload,
    );
  }
}
