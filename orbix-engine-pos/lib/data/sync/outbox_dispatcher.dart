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

    final ops = batch.map(_rowToSyncOp).toList();
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
    if (batch.length >= kPushBatchSize) {
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

  SyncOp _rowToSyncOp(OutboxData row) {
    final payload = jsonDecode(row.payloadJson) as Map<String, dynamic>;
    return SyncOp(
      clientOpId: row.clientOpId,
      opType: row.opType,
      seq: row.seq,
      occurredAt: row.occurredAt,
      dependsOn: row.dependsOn,
      payload: payload,
    );
  }
}
