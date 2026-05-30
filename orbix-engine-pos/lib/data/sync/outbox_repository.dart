/// Outbox repository — transactional enqueue + query helpers for the device outbox.
///
/// All writes to the outbox MUST happen inside the same Drift transaction as the
/// corresponding domain write (PosSale insert, TillSession insert, etc.) so that
/// a crash never creates a domain record without a matching outbox op, and never
/// creates an outbox op without the domain record it refers to.
///
/// Design: slice-sync-spine.md §2.1, §2.4.
library;

import 'dart:convert';

import 'package:drift/drift.dart';
import 'package:logger/logger.dart';

import '../local/database.dart';
import '../../core/ulid.dart';

// Drift generates the data-class for the Outbox table as OutboxData.
typedef OutboxEntry = OutboxData;

/// Outbox status constants.
class OutboxStatus {
  static const pending = 'PENDING';
  static const inflight = 'INFLIGHT';
  static const confirmed = 'CONFIRMED';
  static const rejected = 'REJECTED';
  static const deferred = 'DEFERRED';
  static const needsReview = 'NEEDS_REVIEW';
}

/// Op type constants (matches SyncOpType in the OpenAPI spec).
class OutboxOpType {
  static const tillSessionOpen = 'TILL_SESSION_OPEN';
  static const posSale = 'POS_SALE';
  static const posSaleVoid = 'POS_SALE_VOID';
  static const cashPickup = 'CASH_PICKUP';
  static const pettyCash = 'PETTY_CASH';
  static const tillSessionClose = 'TILL_SESSION_CLOSE';
}

class OutboxRepository {
  OutboxRepository(this._db, {Logger? logger}) : _log = logger ?? Logger();

  final PosDatabase _db;
  final Logger _log;

  /// Exposed for the dispatcher to stamp domain rows (PosSales, etc.)
  /// without a separate db reference.
  PosDatabase get db => _db;

  // ---------------------------------------------------------------------------
  // Internal sequence counter — monotonic per device lifetime.
  // Persisted via SELECT MAX(seq)+1 on startup so it survives restarts.
  // ---------------------------------------------------------------------------

  int _nextSeq = 1;

  Future<void> initSeq() async {
    final rows = await _db.select(_db.outbox).get();
    final maxSeq = rows.isEmpty ? 0 : rows.map((r) => r.seq).reduce((a, b) => a > b ? a : b);
    _nextSeq = maxSeq + 1;
  }

  int _consumeSeq() => _nextSeq++;

  // ---------------------------------------------------------------------------
  // Enqueue — always call inside a Drift transaction.
  // ---------------------------------------------------------------------------

  /// Enqueue an op inside an existing Drift transaction.
  /// Pass the PosDatabase; Drift will use the ambient transaction automatically.
  Future<String> enqueueInTxn(
    PosDatabase db, {
    required String opType,
    required Map<String, dynamic> payload,
    String? dependsOn,
    DateTime? occurredAt,
  }) async {
    final clientOpId = generateUlid();
    final now = DateTime.now().toUtc();
    await db.into(db.outbox).insert(OutboxCompanion.insert(
      clientOpId: clientOpId,
      opType: opType,
      seq: Value(_consumeSeq()),
      dependsOn: Value(dependsOn),
      payloadJson: jsonEncode(payload),
      occurredAt: occurredAt?.toUtc() ?? now,
      createdAt: now,
      status: const Value(OutboxStatus.pending),
    ));
    _log.d('Outbox.enqueueInTxn clientOpId=$clientOpId opType=$opType seq=${_nextSeq - 1}');
    return clientOpId;
  }

  // ---------------------------------------------------------------------------
  // Query helpers
  // ---------------------------------------------------------------------------

  /// Returns PENDING + DEFERRED rows ordered by seq ASC.
  Future<List<OutboxData>> pendingOps({int limit = 100}) async {
    return (_db.select(_db.outbox)
          ..where((t) => t.status.isIn([OutboxStatus.pending, OutboxStatus.deferred]))
          ..orderBy([(t) => OrderingTerm.asc(t.seq)])
          ..limit(limit))
        .get();
  }

  /// Fetch a single row by clientOpId.
  Future<OutboxData?> byClientOpId(String clientOpId) async {
    return (_db.select(_db.outbox)
          ..where((t) => t.clientOpId.equals(clientOpId)))
        .getSingleOrNull();
  }

  /// All confirmed/pending ops associated with a session (used to build the
  /// close manifest). Includes the session-open op plus any op whose
  /// dependsOn or payload.tillSessionClientOpId references sessionClientOpId.
  Future<List<OutboxData>> opsForSession(String sessionClientOpId) async {
    final all = await (_db.select(_db.outbox)
          ..where((t) => t.status.isIn([
                OutboxStatus.confirmed,
                OutboxStatus.pending,
                OutboxStatus.deferred,
                OutboxStatus.inflight,
              ])))
        .get();
    return all.where((r) {
      if (r.clientOpId == sessionClientOpId) return true;
      if (r.dependsOn == sessionClientOpId) return true;
      final payload = _decodeJson(r.payloadJson);
      return payload['tillSessionClientOpId'] == sessionClientOpId;
    }).toList();
  }

  // ---------------------------------------------------------------------------
  // Status transitions (called by the dispatcher)
  // ---------------------------------------------------------------------------

  Future<void> markConfirmed(
    String clientOpId, {
    String? serverEntityId,
    String? serverNumber,
  }) async {
    await (_db.update(_db.outbox)
          ..where((t) => t.clientOpId.equals(clientOpId)))
        .write(OutboxCompanion(
      status: const Value(OutboxStatus.confirmed),
      serverEntityId: Value(serverEntityId),
      serverNumber: Value(serverNumber),
      lastAttemptAt: Value(DateTime.now().toUtc()),
    ));
  }

  Future<void> markNeedsReview(String clientOpId) async {
    await (_db.update(_db.outbox)
          ..where((t) => t.clientOpId.equals(clientOpId)))
        .write(const OutboxCompanion(
      status: Value(OutboxStatus.needsReview),
    ));
  }

  Future<void> markDeferred(String clientOpId) async {
    await (_db.update(_db.outbox)
          ..where((t) => t.clientOpId.equals(clientOpId)))
        .write(const OutboxCompanion(
      status: Value(OutboxStatus.deferred),
    ));
  }

  Future<void> incrementAttemptAndRequeue(String clientOpId) async {
    final row = await byClientOpId(clientOpId);
    if (row == null) return;
    await (_db.update(_db.outbox)
          ..where((t) => t.clientOpId.equals(clientOpId)))
        .write(OutboxCompanion(
      attemptCount: Value(row.attemptCount + 1),
      lastAttemptAt: Value(DateTime.now().toUtc()),
      status: const Value(OutboxStatus.pending),
    ));
  }

  /// Reset all INFLIGHT rows back to PENDING (called on startup/reconnect to
  /// recover from a crash mid-flush).
  Future<void> resetInflight() async {
    await (_db.update(_db.outbox)
          ..where((t) => t.status.equals(OutboxStatus.inflight)))
        .write(const OutboxCompanion(
      status: Value(OutboxStatus.pending),
    ));
  }

  /// Count of PENDING + DEFERRED rows — used by the UI to show "N ops pending".
  Future<int> pendingCount() async {
    final rows = await (_db.select(_db.outbox)
          ..where((t) => t.status.isIn([OutboxStatus.pending, OutboxStatus.deferred])))
        .get();
    return rows.length;
  }

  /// Returns the set of all CONFIRMED outbox clientOpIds.
  /// Used by the dispatcher to decide which dependent ops are ready to push.
  Future<Set<String>> confirmedClientOpIds() async {
    final rows = await (_db.select(_db.outbox)
          ..where((t) => t.status.equals(OutboxStatus.confirmed)))
        .get();
    return {for (final r in rows) r.clientOpId};
  }

  /// Watch NEEDS_REVIEW ops — displayed in the cashier "needs attention" list.
  Stream<List<OutboxData>> watchNeedsReview() {
    return (_db.select(_db.outbox)
          ..where((t) => t.status.equals(OutboxStatus.needsReview))
          ..orderBy([(t) => OrderingTerm.asc(t.createdAt)]))
        .watch();
  }

  // ---------------------------------------------------------------------------
  // tillSessionId back-fill (called by dispatcher after TILL_SESSION_OPEN ACCEPTED)
  // ---------------------------------------------------------------------------

  /// Patch all PENDING / DEFERRED ops that depend on [sessionClientOpId] to
  /// include [serverSessionId] as their `tillSessionId` payload field.
  ///
  /// This satisfies SyncServiceImpl.applyCashPickup / applyPettyCash which require
  /// tillSessionId as a required Long payload field. Without this patch, ops
  /// taken offline (before the session synced) would arrive with tillSessionId=0
  /// and be REJECTED by the server.
  ///
  /// [log] is optional; pass the caller's logger for consistent log context.
  Future<void> backfillTillSessionId({
    required String sessionClientOpId,
    required int serverSessionId,
    Logger? log,
  }) async {
    // Fetch all PENDING + DEFERRED rows whose dependsOn matches the session.
    final rows = await (_db.select(_db.outbox)
          ..where((t) =>
              t.dependsOn.equals(sessionClientOpId) &
              t.status.isIn([OutboxStatus.pending, OutboxStatus.deferred])))
        .get();

    for (final row in rows) {
      final payload = _decodeJson(row.payloadJson);
      final existing = payload['tillSessionId'];
      // Only patch if missing or zero — don't overwrite a valid value.
      if (existing != null && existing != 0 && existing != '0') continue;
      payload['tillSessionId'] = serverSessionId;
      await (_db.update(_db.outbox)
            ..where((t) => t.clientOpId.equals(row.clientOpId)))
          .write(OutboxCompanion(
        payloadJson: Value(jsonEncode(payload)),
      ));
      log?.d('OutboxRepository.backfillTillSessionId patched '
          '${row.opType} ${row.clientOpId} tillSessionId=$serverSessionId');
    }
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  Map<String, dynamic> _decodeJson(String source) =>
      jsonDecode(source) as Map<String, dynamic>;
}
