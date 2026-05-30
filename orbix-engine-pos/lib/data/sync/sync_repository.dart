/// Sync repository — pull, bootstrap, and till-close reconciliation.
///
/// Wraps the SyncApiClient + local Drift tables for cursor storage and
/// reference-data upsert/delete. All methods are safe to call concurrently
/// (the dispatcher serialises calls in practice).
///
/// Design: slice-sync-spine.md §3, §4.
library;

import 'dart:convert';

import 'package:drift/drift.dart';
import 'package:logger/logger.dart';

import '../local/database.dart';
import 'outbox_repository.dart';
import 'sync_api_client.dart';
import 'sync_models.dart';

const _globalCursorDataset = 'global';

class SyncRepository {
  SyncRepository({
    required PosDatabase db,
    required SyncApiClient apiClient,
    required OutboxRepository outboxRepo,
    Logger? logger,
  })  : _db = db,
        _api = apiClient,
        _outbox = outboxRepo,
        _log = logger ?? Logger();

  final PosDatabase _db;
  final SyncApiClient _api;
  final OutboxRepository _outbox;
  final Logger _log;

  // ---------------------------------------------------------------------------
  // Cursor management
  // ---------------------------------------------------------------------------

  Future<String?> _readCursor() async {
    final row = await (_db.select(_db.syncCursors)
          ..where((t) => t.dataset.equals(_globalCursorDataset)))
        .getSingleOrNull();
    return row?.token;
  }

  Future<void> _saveCursor(String token) async {
    await _db.into(_db.syncCursors).insertOnConflictUpdate(
          SyncCursorsCompanion.insert(
            dataset: _globalCursorDataset,
            token: token,
            updatedAt: DateTime.now().toUtc(),
          ),
        );
  }

  Future<void> _clearCursor() async {
    await (_db.delete(_db.syncCursors)
          ..where((t) => t.dataset.equals(_globalCursorDataset)))
        .go();
  }

  // ---------------------------------------------------------------------------
  // Bootstrap (first run / reinstall / forced resync)
  // ---------------------------------------------------------------------------

  /// Full snapshot + opening cursor. Wipes reference caches then reapplies.
  /// Does NOT wipe the outbox — only reference data.
  Future<void> bootstrap() async {
    _log.i('SyncRepository.bootstrap starting');
    await _clearCursor();

    SyncPullResult result = await _api.bootstrap();
    await _applyPullResult(result);

    while (result.hasMore) {
      result = await _api.pull(cursor: result.nextCursor);
      await _applyPullResult(result);
    }

    _log.i('SyncRepository.bootstrap complete cursor=${result.nextCursor}');
  }

  // ---------------------------------------------------------------------------
  // Pull (incremental delta)
  // ---------------------------------------------------------------------------

  /// Pull deltas since the stored cursor. Loops until hasMore=false.
  /// Returns true if a resync was triggered (caller should handle UI state).
  Future<bool> pull() async {
    final cursor = await _readCursor();
    if (cursor == null) {
      // No cursor — must bootstrap.
      _log.w('SyncRepository.pull: no cursor found, falling back to bootstrap');
      await bootstrap();
      return false;
    }

    _log.d('SyncRepository.pull cursor=$cursor');
    SyncPullResult result = await _api.pull(cursor: cursor);

    if (result.resyncRequired) {
      _log.w('SyncRepository.pull: server requested resync');
      await bootstrap();
      return true;
    }

    await _applyPullResult(result);

    while (result.hasMore) {
      result = await _api.pull(cursor: result.nextCursor);
      if (result.resyncRequired) {
        _log.w('SyncRepository.pull: server requested resync mid-page');
        await bootstrap();
        return true;
      }
      await _applyPullResult(result);
    }

    _log.d('SyncRepository.pull complete nextCursor=${result.nextCursor}');
    return false;
  }

  // ---------------------------------------------------------------------------
  // Apply a pull result — upsert/delete per dataset, advance cursor.
  // ---------------------------------------------------------------------------

  Future<void> _applyPullResult(SyncPullResult result) async {
    for (final entry in result.datasets.entries) {
      final dataset = entry.key;
      final data = entry.value;
      await _applyDataset(dataset, data);
    }
    await _saveCursor(result.nextCursor);
  }

  Future<void> _applyDataset(String dataset, SyncDataset data) async {
    _log.d('SyncRepository._applyDataset dataset=$dataset '
        'upserts=${data.upserts.length} deletes=${data.deletes.length}');

    switch (dataset) {
      case 'catalog':
        await _applyCatalogDataset(data);
      case 'customer':
        await _applyCustomerDataset(data);
      case 'price':
        await _applyPriceDataset(data);
      case 'balance':
        // balance dataset: currently informational — no local table persisted.
        // TODO: persist per-item balance when stock-on-hand display is needed.
        _log.d('SyncRepository: balance dataset received (not persisted in v1)');
      default:
        _log.d('SyncRepository: unknown dataset "$dataset" — ignored (forward compat)');
    }
  }

  Future<void> _applyCatalogDataset(SyncDataset data) async {
    for (final row in data.upserts) {
      final id = _parseInt(row['id']);
      if (id == null) continue;
      await _db.into(_db.items).insertOnConflictUpdate(ItemsCompanion.insert(
        id: Value(id),
        uid: Value(row['uid'] as String?),
        code: row['code'] as String? ?? '',
        name: row['name'] as String? ?? '',
        shortName: Value(row['shortName'] as String?),
        price: (row['price'] as num?)?.toDouble() ?? 0.0,
        vatGroup: row['vatGroup'] as String? ?? '',
        itemGroupId: _parseInt(row['itemGroupId']) ?? 0,
        isActive: Value(
          (row['status'] as String?)?.toUpperCase() == 'ACTIVE' ||
              (row['isActive'] as bool?) == true,
        ),
      ));

      // Upsert barcodes if present
      final barcodes = row['barcodes'] as List<dynamic>?;
      if (barcodes != null) {
        for (final b in barcodes) {
          final bMap = b as Map<String, dynamic>;
          final bcode = bMap['barcode'] as String?;
          if (bcode == null) continue;
          await _db.into(_db.barcodes).insertOnConflictUpdate(
                BarcodesCompanion.insert(
                  barcode: bcode,
                  itemId: id,
                  packQty: Value((bMap['packQty'] as num?)?.toDouble() ?? 1.0),
                ),
              );
        }
      }
    }

    for (final idStr in data.deletes) {
      final id = int.tryParse(idStr);
      if (id == null) continue;
      await (_db.delete(_db.items)..where((t) => t.id.equals(id))).go();
    }
  }

  Future<void> _applyCustomerDataset(SyncDataset data) async {
    for (final row in data.upserts) {
      final id = _parseInt(row['id']);
      if (id == null) continue;
      await _db.into(_db.customers).insertOnConflictUpdate(CustomersCompanion.insert(
        id: Value(id),
        uid: Value(row['uid'] as String?),
        code: row['code'] as String? ?? '',
        name: row['name'] as String? ?? '',
        isWalkIn: Value((row['walkIn'] as bool?) ?? false),
        isActive: Value((row['active'] as bool?) ?? true),
      ));
    }

    for (final idStr in data.deletes) {
      final id = int.tryParse(idStr);
      if (id == null) continue;
      await (_db.delete(_db.customers)..where((t) => t.id.equals(id))).go();
    }
  }

  Future<void> _applyPriceDataset(SyncDataset data) async {
    for (final row in data.upserts) {
      final itemId = _parseInt(row['itemId']);
      if (itemId == null) continue;
      final priceListCode = row['priceListCode'] as String? ?? 'DEFAULT';
      // Delete any existing row for this item+pricelist, then insert fresh.
      await (_db.delete(_db.priceRows)
            ..where((t) =>
                t.itemId.equals(itemId) & t.priceListCode.equals(priceListCode)))
          .go();
      await _db.into(_db.priceRows).insert(PriceRowsCompanion.insert(
            itemId: itemId,
            priceListCode: priceListCode,
            price: (row['price'] as num?)?.toDouble() ?? 0.0,
            currency: Value(row['currency'] as String? ?? 'TZS'),
          ));
    }
    // price deletes by priceRow id — not supported in v1 (use catalog archive instead).
  }

  // ---------------------------------------------------------------------------
  // Till-close reconciliation (slice-sync-spine.md §4)
  // ---------------------------------------------------------------------------

  /// Attempt to close the till session.
  ///
  /// Builds the manifest from the outbox, calls the server, and:
  /// - CLOSED: confirms all ops (marks them CONFIRMED), updates session status.
  /// - RECONCILE_INCOMPLETE: returns the result for the UI to display.
  ///
  /// Throws [TillCloseBlockedError] if the manifest is incomplete.
  Future<TillSessionCloseResult> closeTillSession({
    required String sessionClientOpId,
    required double declaredCash,
  }) async {
    _log.i('SyncRepository.closeTillSession session=$sessionClientOpId');

    // Build manifest from confirmed/pending ops for this session.
    final ops = await _outbox.opsForSession(sessionClientOpId);
    final saleOps = ops.where((o) => o.opType == OutboxOpType.posSale).toList();
    final pickupOps = ops.where((o) => o.opType == OutboxOpType.cashPickup).toList();
    final pettyOps = ops.where((o) => o.opType == OutboxOpType.pettyCash).toList();

    double saleTotal = 0;
    for (final op in saleOps) {
      final payload = jsonDecode(op.payloadJson) as Map<String, dynamic>;
      final rawTotal = payload['total'];
      double t = 0.0;
      if (rawTotal is num) {
        t = rawTotal.toDouble();
      } else if (rawTotal is String) {
        t = double.tryParse(rawTotal) ?? 0.0;
      }
      saleTotal += t;
    }

    // Sum pickup and petty-cash amounts from the outbox payload.
    double sumPayloadAmount(Iterable<OutboxData> ops) {
      double total = 0;
      for (final op in ops) {
        final p = jsonDecode(op.payloadJson) as Map<String, dynamic>;
        final v = p['amount'];
        if (v is num) {
          total += v.toDouble();
        } else if (v is String) {
          total += double.tryParse(v) ?? 0.0;
        }
      }
      return total;
    }

    final pickupTotal = sumPayloadAmount(pickupOps);
    final pettyTotal = sumPayloadAmount(pettyOps);

    // Express as 4dp string per Money schema (TZS = no decimals but server accepts 4dp)
    String moneyStr(double v) => v.toStringAsFixed(4);

    // The manifest's clientOpIds must match what the server's collectSessionClientOpIds
    // returns: only transactional ops (POS_SALE, CASH_PICKUP, PETTY_CASH).
    // The TILL_SESSION_OPEN op is excluded — the server doesn't count it as a
    // transactional op when reconciling.
    final transactionalOpIds = ops
        .where((o) => o.opType != OutboxOpType.tillSessionOpen)
        .map((o) => o.clientOpId)
        .toList();

    final manifest = TillCloseManifest(
      posSaleCount: saleOps.length,
      posSaleTotal: moneyStr(saleTotal),
      cashPickupCount: pickupOps.length,
      cashPickupTotal: moneyStr(pickupTotal),
      pettyCashCount: pettyOps.length,
      pettyCashTotal: moneyStr(pettyTotal),
      clientOpIds: transactionalOpIds,
    );

    final request = TillSessionCloseRequest(
      tillSessionClientOpId: sessionClientOpId,
      declaredCash: moneyStr(declaredCash),
      manifest: manifest,
    );

    final result = await _api.closeSession(request);

    if (result.status == TillCloseStatus.closed) {
      // Mark all confirmed ops as such + flip PosSales.synced
      for (final opId in result.confirmedClientOpIds) {
        await _outbox.markConfirmed(opId);
      }
      // Mark the till session as CLOSED
      await _db.transaction(() async {
        await (_db.update(_db.tillSessions)
              ..where((t) => t.clientOpId.equals(sessionClientOpId)))
            .write(const TillSessionsCompanion(
          status: Value('CLOSED'),
        ));
        // Flip synced on PosSales whose clientOpId is in confirmedClientOpIds
        for (final opId in result.confirmedClientOpIds) {
          await (_db.update(_db.posSales)
                ..where((t) => t.clientOpId.equals(opId)))
              .write(const PosSalesCompanion(
            synced: Value(true),
          ));
        }
      });
      _log.i('SyncRepository.closeTillSession CLOSED variance=${result.variance}');
    } else {
      _log.w('SyncRepository.closeTillSession RECONCILE_INCOMPLETE '
          'missing=${result.missingClientOpIds} unexpected=${result.unexpectedClientOpIds}');
    }

    return result;
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  int? _parseInt(dynamic value) {
    if (value == null) return null;
    if (value is int) return value;
    if (value is String) return int.tryParse(value);
    if (value is double) return value.toInt();
    return null;
  }
}
