/// Repository for till-session lifecycle: open, query active, stamp server id.
///
/// Writes the TillSession row to Drift AND enqueues a TILL_SESSION_OPEN outbox
/// op in the same transaction so both are atomically durable. Offline-first:
/// the op queues without a network connection and pushes when the dispatcher
/// next runs.
///
/// Design: slice-sync-spine.md §2 / US-POS-017.
library;

import 'package:drift/drift.dart';
import 'package:logger/logger.dart';

import '../../data/local/database.dart';
import '../../data/sync/outbox_repository.dart';

/// Immutable view of the active till session (hydrated from the Drift row).
class ActiveTillSession {
  const ActiveTillSession({
    required this.localId,
    required this.clientOpId,
    required this.tillId,
    required this.tillCode,
    required this.tillName,
    required this.openedBy,
    required this.openedAt,
    required this.openingFloat,
    required this.businessDate,
    required this.cashierName,
    required this.branchName,
    required this.status,
    this.serverEntityId,
  });

  final int localId;
  final String clientOpId;
  final int tillId;
  final String tillCode;
  final String tillName;
  final int openedBy;
  final DateTime openedAt;
  final double openingFloat;
  final String businessDate;
  final String cashierName;
  final String branchName;
  final String status; // OPEN | CLOSED
  /// Server-assigned till session id (Long as String). Null until synced.
  final String? serverEntityId;
}

/// Result of [TillSessionRepository.openSession].
class OpenSessionResult {
  const OpenSessionResult({
    required this.localId,
    required this.clientOpId,
    required this.session,
  });

  final int localId;
  final String clientOpId;
  final ActiveTillSession session;
}

class TillSessionRepository {
  TillSessionRepository({
    required PosDatabase db,
    required OutboxRepository outbox,
    Logger? logger,
  })  : _db = db,
        _outbox = outbox,
        _log = logger ?? Logger();

  final PosDatabase _db;
  final OutboxRepository _outbox;
  final Logger _log;

  // ---------------------------------------------------------------------------
  // Open
  // ---------------------------------------------------------------------------

  /// Write a TillSession row and enqueue TILL_SESSION_OPEN in one transaction.
  ///
  /// Returns the new [OpenSessionResult] with the local id and the clientOpId
  /// that acts as the local session key for dependent ops (dependsOn).
  Future<OpenSessionResult> openSession({
    required int tillId,
    required String tillCode,
    required String tillName,
    required int openedBy,
    required String cashierName,
    required String branchName,
    required double openingFloat,
    required String businessDate,
  }) async {
    _log.i('TillSessionRepository.openSession till=$tillCode float=$openingFloat');

    int localId = 0;
    String sessionClientOpId = '';

    await _db.transaction(() async {
      // Payload must match SyncServiceImpl.applyTillSessionOpen:
      //   tillId (Long) — the server-side till id
      //   openingFloatAmount (BigDecimal string)
      final payload = <String, dynamic>{
        'tillId': tillId,
        'openingFloatAmount': openingFloat.toStringAsFixed(4),
      };

      sessionClientOpId = await _outbox.enqueueInTxn(
        _db,
        opType: OutboxOpType.tillSessionOpen,
        payload: payload,
        occurredAt: DateTime.now(),
      );

      localId = await _db.into(_db.tillSessions).insert(
            TillSessionsCompanion.insert(
              clientOpId: Value(sessionClientOpId),
              tillId: tillId,
              businessDate: businessDate,
              openedBy: openedBy,
              openedAt: DateTime.now(),
              openingFloat: openingFloat,
              status: 'OPEN',
            ),
          );
    });

    _log.d('TillSessionRepository.openSession localId=$localId clientOpId=$sessionClientOpId');

    final session = ActiveTillSession(
      localId: localId,
      clientOpId: sessionClientOpId,
      tillId: tillId,
      tillCode: tillCode,
      tillName: tillName,
      openedBy: openedBy,
      openedAt: DateTime.now(),
      openingFloat: openingFloat,
      businessDate: businessDate,
      cashierName: cashierName,
      branchName: branchName,
      status: 'OPEN',
    );

    return OpenSessionResult(
      localId: localId,
      clientOpId: sessionClientOpId,
      session: session,
    );
  }

  // ---------------------------------------------------------------------------
  // Query
  // ---------------------------------------------------------------------------

  /// Returns the currently OPEN till session, or null if none.
  Future<ActiveTillSession?> activeSession({
    required String cashierName,
    required String branchName,
    String tillCode = 'TILL-?',
    String tillName = '',
  }) async {
    final row = await (_db.select(_db.tillSessions)
          ..where((t) => t.status.equals('OPEN'))
          ..orderBy([(t) => OrderingTerm.desc(t.id)])
          ..limit(1))
        .getSingleOrNull();

    if (row == null) return null;

    // Resolve tillCode / tillName from the till id — for v1 we use the stored
    // ids and pass labels from the caller (login context knows them).
    return ActiveTillSession(
      localId: row.id,
      clientOpId: row.clientOpId ?? '',
      tillId: row.tillId,
      tillCode: tillCode,
      tillName: tillName,
      openedBy: row.openedBy,
      openedAt: row.openedAt,
      openingFloat: row.openingFloat,
      businessDate: row.businessDate,
      cashierName: cashierName,
      branchName: branchName,
      status: row.status,
      serverEntityId: row.serverUid, // stored as serverUid in the TillSessions table
    );
  }

  /// Reactive stream of the active (OPEN) session row — null when none.
  /// Used by providers to push UI updates when the session opens/closes.
  Stream<TillSession?> watchActiveSessionRow() {
    return (_db.select(_db.tillSessions)
          ..where((t) => t.status.equals('OPEN'))
          ..orderBy([(t) => OrderingTerm.desc(t.id)])
          ..limit(1))
        .watchSingleOrNull();
  }

  // ---------------------------------------------------------------------------
  // Stamp server entity id (called by dispatcher after TILL_SESSION_OPEN ACCEPTED)
  // ---------------------------------------------------------------------------

  /// Persist the server-assigned session id so it can be injected into
  /// dependent op payloads before they are pushed.
  Future<void> stampServerEntityId({
    required String sessionClientOpId,
    required String serverEntityId,
  }) async {
    await (_db.update(_db.tillSessions)
          ..where((t) => t.clientOpId.equals(sessionClientOpId)))
        .write(TillSessionsCompanion(
      serverUid: Value(serverEntityId),
    ));
    _log.d('TillSessionRepository.stampServerEntityId '
        'session=$sessionClientOpId serverEntityId=$serverEntityId');
  }
}
