/// Repository for mid-shift cash movements: cash pickup (US-POS-013) and
/// petty cash (US-POS-014).
///
/// Both operations enqueue an outbox op in the same Drift transaction as any
/// local audit record, so they are durably queued even while the network is
/// down. Payloads match what SyncServiceImpl.applyCashPickup /
/// applyPettyCash expect on the backend.
library;

import 'dart:convert';

import 'package:logger/logger.dart';

import '../../data/local/database.dart';
import '../../data/sync/outbox_repository.dart';

/// Petty-cash categories that mirror the backend PettyCashCategory enum.
enum PettyCashCategory { transport, office, maintenance, other }

extension PettyCashCategoryX on PettyCashCategory {
  String get label => switch (this) {
        PettyCashCategory.transport => 'Transport',
        PettyCashCategory.office => 'Office supplies',
        PettyCashCategory.maintenance => 'Maintenance',
        PettyCashCategory.other => 'Other',
      };

  /// Wire value sent in the outbox payload — must match the backend enum name.
  String get wireValue => name.toUpperCase();
}

/// Result of a cash movement enqueue — carries the clientOpId for chaining.
class CashMovementResult {
  final String clientOpId;
  const CashMovementResult({required this.clientOpId});
}

class CashMovementRepository {
  CashMovementRepository({
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
  // Cash pickup (US-POS-013)
  // ---------------------------------------------------------------------------

  /// Record a cash pickup from the till drawer.
  ///
  /// [tillSessionClientOpId] — the TILL_SESSION_OPEN outbox op id (used as
  ///   dependsOn so the server can locate the session).
  /// [tillSessionServerId] — the server-assigned session id (Long as String),
  ///   stored as [tillSessionId] in the payload for the backend's lookup.
  ///   May be null if the session has not yet been synced; the backend will
  ///   re-resolve via the outbox dependsOn chain.
  /// [amount] — amount removed from the drawer (TZS, must be > 0).
  /// [note] — free-text reason / reference (optional).
  /// [authorisedBy] — user id of the authorising supervisor (default 0 if
  ///   offline supervisor approval is not wired up yet).
  Future<CashMovementResult> recordCashPickup({
    required String tillSessionClientOpId,
    String? tillSessionServerId,
    required double amount,
    String? note,
    int authorisedBy = 0,
  }) async {
    if (amount <= 0) throw ArgumentError('Cash pickup amount must be > 0');

    _log.i('CashMovementRepository.recordCashPickup amount=$amount '
        'session=$tillSessionClientOpId');

    String clientOpId = '';
    await _db.transaction(() async {
      // Payload fields match SyncServiceImpl.applyCashPickup:
      //   tillSessionId (Long), amount (decimal string), authorisedBy (Long), note (String)
      // The backend resolves the session from tillSessionId.  When
      // tillSessionServerId is not yet known (session not yet synced), we
      // omit it — the server DEFERS this op until the session op settles and
      // re-looks it up.  In practice, the dispatcher sends the session op first
      // (lower seq + dependsOn = null), so by the time this op is sent the
      // session exists.
      final payload = <String, dynamic>{
        'amount': amount.toStringAsFixed(4),
        'authorisedBy': authorisedBy,
        if (tillSessionServerId != null)
          'tillSessionId': int.tryParse(tillSessionServerId) ?? 0,
        if (note != null && note.isNotEmpty) 'note': note,
      };

      clientOpId = await _outbox.enqueueInTxn(
        _db,
        opType: OutboxOpType.cashPickup,
        payload: payload,
        dependsOn: tillSessionClientOpId,
        occurredAt: DateTime.now(),
      );
    });

    _log.d('CashMovementRepository.recordCashPickup clientOpId=$clientOpId');
    return CashMovementResult(clientOpId: clientOpId);
  }

  // ---------------------------------------------------------------------------
  // Petty cash (US-POS-014)
  // ---------------------------------------------------------------------------

  /// Record a petty-cash payout from the till drawer.
  ///
  /// [tillSessionClientOpId] — the TILL_SESSION_OPEN outbox op id (dependsOn).
  /// [tillSessionServerId] — server session id string; see note in [recordCashPickup].
  /// [amount] — payout amount (TZS, must be > 0).
  /// [category] — payout category.
  /// [paidTo] — payee name/description (optional but encouraged).
  /// [description] — detailed reason for the payout (optional).
  /// [authorisedBy] — authorising supervisor user id (default 0 if not wired).
  Future<CashMovementResult> recordPettyCash({
    required String tillSessionClientOpId,
    String? tillSessionServerId,
    required double amount,
    required PettyCashCategory category,
    String? paidTo,
    String? description,
    int authorisedBy = 0,
  }) async {
    if (amount <= 0) throw ArgumentError('Petty cash amount must be > 0');

    _log.i('CashMovementRepository.recordPettyCash amount=$amount '
        'category=${category.wireValue} session=$tillSessionClientOpId');

    String clientOpId = '';
    await _db.transaction(() async {
      // Payload fields match SyncServiceImpl.applyPettyCash:
      //   tillSessionId (Long), amount (decimal string), authorisedBy (Long),
      //   category (PettyCashCategory enum name), paidTo (String), description (String)
      final payload = <String, dynamic>{
        'amount': amount.toStringAsFixed(4),
        'category': category.wireValue,
        'authorisedBy': authorisedBy,
        if (tillSessionServerId != null)
          'tillSessionId': int.tryParse(tillSessionServerId) ?? 0,
        if (paidTo != null && paidTo.isNotEmpty) 'paidTo': paidTo,
        if (description != null && description.isNotEmpty)
          'description': description,
      };

      clientOpId = await _outbox.enqueueInTxn(
        _db,
        opType: OutboxOpType.pettyCash,
        payload: payload,
        dependsOn: tillSessionClientOpId,
        occurredAt: DateTime.now(),
      );
    });

    _log.d('CashMovementRepository.recordPettyCash clientOpId=$clientOpId');
    return CashMovementResult(clientOpId: clientOpId);
  }

  // ---------------------------------------------------------------------------
  // Session summary queries (used by X-report and till-close manifest)
  // ---------------------------------------------------------------------------

  /// Sum of all cash-pickup amounts for the given session (from outbox payload).
  /// Works offline — reads local outbox only.
  Future<double> cashPickupTotalForSession(String sessionClientOpId) async {
    final ops = await _outbox.opsForSession(sessionClientOpId);
    return _sumAmountField(
      ops.where((o) => o.opType == OutboxOpType.cashPickup),
    );
  }

  /// Sum of all petty-cash amounts for the given session (from outbox payload).
  /// Works offline — reads local outbox only.
  Future<double> pettyCashTotalForSession(String sessionClientOpId) async {
    final ops = await _outbox.opsForSession(sessionClientOpId);
    return _sumAmountField(
      ops.where((o) => o.opType == OutboxOpType.pettyCash),
    );
  }

  /// Count of cash-pickup ops for the given session.
  Future<int> cashPickupCountForSession(String sessionClientOpId) async {
    final ops = await _outbox.opsForSession(sessionClientOpId);
    return ops.where((o) => o.opType == OutboxOpType.cashPickup).length;
  }

  /// Count of petty-cash ops for the given session.
  Future<int> pettyCashCountForSession(String sessionClientOpId) async {
    final ops = await _outbox.opsForSession(sessionClientOpId);
    return ops.where((o) => o.opType == OutboxOpType.pettyCash).length;
  }

  /// All individual cash-pickup ops for the session (for X-report display).
  Future<List<CashMovementEntry>> cashPickupsForSession(String sessionClientOpId) async {
    final ops = await _outbox.opsForSession(sessionClientOpId);
    return ops
        .where((o) => o.opType == OutboxOpType.cashPickup)
        .map((o) {
          final p = _decode(o.payloadJson);
          return CashMovementEntry(
            clientOpId: o.clientOpId,
            occurredAt: o.occurredAt,
            amount: _parseAmount(p['amount']),
            note: p['note'] as String?,
            category: null,
            paidTo: null,
          );
        })
        .toList();
  }

  /// All individual petty-cash ops for the session (for X-report display).
  Future<List<CashMovementEntry>> pettyCashForSession(String sessionClientOpId) async {
    final ops = await _outbox.opsForSession(sessionClientOpId);
    return ops
        .where((o) => o.opType == OutboxOpType.pettyCash)
        .map((o) {
          final p = _decode(o.payloadJson);
          return CashMovementEntry(
            clientOpId: o.clientOpId,
            occurredAt: o.occurredAt,
            amount: _parseAmount(p['amount']),
            note: p['description'] as String?,
            category: p['category'] as String?,
            paidTo: p['paidTo'] as String?,
          );
        })
        .toList();
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  double _sumAmountField(Iterable<OutboxData> ops) {
    double total = 0;
    for (final op in ops) {
      final p = _decode(op.payloadJson);
      total += _parseAmount(p['amount']);
    }
    return total;
  }

  double _parseAmount(dynamic v) {
    if (v is num) return v.toDouble();
    if (v is String) return double.tryParse(v) ?? 0.0;
    return 0.0;
  }

  Map<String, dynamic> _decode(String json) =>
      jsonDecode(json) as Map<String, dynamic>;
}

/// Immutable view of a single cash-movement outbox entry (for X-report rows).
class CashMovementEntry {
  final String clientOpId;
  final DateTime occurredAt;
  final double amount;
  final String? note;
  final String? category;
  final String? paidTo;

  const CashMovementEntry({
    required this.clientOpId,
    required this.occurredAt,
    required this.amount,
    this.note,
    this.category,
    this.paidTo,
  });
}
