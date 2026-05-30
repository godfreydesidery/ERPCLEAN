/// Repository for recording a completed POS sale.
///
/// Writes PosSale + PosSaleLines to Drift AND enqueues a POS_SALE outbox op
/// in the same transaction. The op payload matches PostPosSaleRequestDto
/// exactly so SyncServiceImpl.applyPosSale can process it without conversion.
///
/// Offline-first: the sale is persisted locally immediately; the outbox op
/// carries dependsOn = sessionClientOpId so the server can reject it if the
/// session hasn't been confirmed yet (which in practice means the dispatcher
/// sends the session op first in sequence order).
///
/// Design: slice-sync-spine.md §2 / US-POS-018.
library;

import 'dart:convert';

import 'package:drift/drift.dart';
import 'package:logger/logger.dart';

import '../../data/local/database.dart';
import '../../data/sync/outbox_repository.dart';
import '../_demo/mocks.dart' show CartLine, PaymentMethod;
import '../payment/payment_screen.dart' show TenderLine;

/// Wire value for a [PaymentMethod] sent to the server (PosPaymentMethod enum).
String _tenderMethodWire(PaymentMethod m) => switch (m) {
      PaymentMethod.cash => 'CASH',
      PaymentMethod.card => 'CARD',
      PaymentMethod.mobileMoney => 'MOBILE_MONEY',
      PaymentMethod.giftCard => 'GIFT_CARD',
      PaymentMethod.voucher => 'VOUCHER',
    };

/// Result of a [PosSaleRepository.recordSale] call.
class RecordSaleResult {
  const RecordSaleResult({
    required this.localSaleId,
    required this.clientOpId,
    required this.receiptNumber,
  });

  final int localSaleId;
  final String clientOpId;

  /// Client-generated receipt number stamped on the sale.
  final String receiptNumber;
}

class PosSaleRepository {
  PosSaleRepository({
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
  // Record a completed sale
  // ---------------------------------------------------------------------------

  /// Persist the sale in Drift and enqueue a POS_SALE op in one transaction.
  ///
  /// [sessionLocalId]      — local Drift till_sessions.id (used for FK).
  /// [sessionClientOpId]   — the TILL_SESSION_OPEN clientOpId (used as dependsOn
  ///                         and as tillSessionClientOpId in the payload).
  /// [sessionServerId]     — server-assigned session Long id (String); null when
  ///                         the session has not yet synced. The dispatcher
  ///                         back-fills it via [patchTillSessionIdInPendingOps].
  /// [tillId]              — server-side till id (sent in payload as tillId; in
  ///                         v1 PostPosSaleRequestDto doesn't have tillId but
  ///                         does have tillSessionId; see payload note below).
  /// [sectionId]           — server-side POS section id (branch POS config).
  /// [customerId]          — server-side customer id.
  /// [userId]              — logged-in user id (stored as openedBy / supervisorId).
  /// [lines]               — cart lines; only lines with a synced price.
  /// [tenders]             — payment breakdown from the payment screen.
  /// [deviceId]            — till device id (used to build the receipt number).
  Future<RecordSaleResult> recordSale({
    required int sessionLocalId,
    required String sessionClientOpId,
    String? sessionServerId,
    required int sectionId,
    required int customerId,
    required int userId,
    required List<CartLine> lines,
    required List<TenderLine> tenders,
    required String deviceId,
    required String businessDate,
  }) async {
    if (lines.isEmpty) throw ArgumentError('Cannot record a sale with no lines');
    if (tenders.isEmpty) throw ArgumentError('Cannot record a sale with no tenders');

    final saleAt = DateTime.now().toUtc();
    final total = lines.fold(0.0, (s, l) => s + l.net);

    // Client-generated number: <deviceId>-<date>-<ms suffix>.
    // Server may override with its own sequential number on ACCEPTED.
    final datePart = businessDate.replaceAll('-', '');
    final suffix = (saleAt.millisecondsSinceEpoch % 100000).toString().padLeft(5, '0');
    final receiptNumber = '$deviceId-$datePart-$suffix';

    _log.i('PosSaleRepository.recordSale number=$receiptNumber '
        'total=$total session=$sessionClientOpId');

    int localSaleId = 0;
    String saleClientOpId = '';

    await _db.transaction(() async {
      // Build the payload matching PostPosSaleRequestDto.
      // The server resolves tillSessionId from the payload; when serverId is not
      // yet known the dispatcher back-fills it before the next push.
      final payload = <String, dynamic>{
        'number': receiptNumber,
        // clientOpId is the outbox op's clientOpId — stamped below.
        'tillSessionId': sessionServerId != null
            ? int.tryParse(sessionServerId) ?? 0
            : 0, // back-filled by dispatcher after session ACCEPTED
        'tillSessionClientOpId': sessionClientOpId, // for back-fill lookup
        'sectionId': sectionId,
        'customerId': customerId,
        'supervisorId': userId,
        'saleAt': saleAt.toIso8601String(),
        'total': total.toStringAsFixed(4),
        'lines': lines
            .map((l) => {
                  'itemId': _itemIdFromLine(l),
                  'qty': l.qty.toStringAsFixed(4),
                  'unitPrice': l.item.price.toStringAsFixed(4),
                  if (l.discountPct > 0)
                    'discountPct': l.discountPct.toStringAsFixed(4),
                })
            .toList(),
        'payments': tenders
            .map((t) => {
                  'method': _tenderMethodWire(t.method),
                  'amount': t.amount.toStringAsFixed(4),
                })
            .toList(),
      };

      saleClientOpId = await _outbox.enqueueInTxn(
        _db,
        opType: OutboxOpType.posSale,
        payload: payload,
        dependsOn: sessionClientOpId,
        occurredAt: saleAt,
      );

      // Stamp clientOpId into the payload now that we have it.
      // We can't do it before enqueue because enqueue generates the ULID.
      final payloadWithId = Map<String, dynamic>.from(payload)
        ..['clientOpId'] = saleClientOpId;
      await (_db.update(_db.outbox)
            ..where((t) => t.clientOpId.equals(saleClientOpId)))
          .write(OutboxCompanion(
        payloadJson: Value(jsonEncode(payloadWithId)),
      ));

      // Insert the local PosSale domain row.
      localSaleId = await _db.into(_db.posSales).insert(
            PosSalesCompanion.insert(
              clientOpId: saleClientOpId,
              tillSessionId: sessionLocalId,
              customerId: customerId,
              total: total,
              saleAt: saleAt,
              status: 'POSTED',
            ),
          );

      // Insert PosSaleLines.
      for (final line in lines) {
        await _db.into(_db.posSaleLines).insert(
              PosSaleLinesCompanion.insert(
                saleId: localSaleId,
                itemId: _itemIdFromLine(line),
                qty: line.qty,
                unitPrice: line.item.price,
                lineTotal: line.net,
              ),
            );
      }
    });

    _log.d('PosSaleRepository.recordSale localId=$localSaleId '
        'clientOpId=$saleClientOpId');

    return RecordSaleResult(
      localSaleId: localSaleId,
      clientOpId: saleClientOpId,
      receiptNumber: receiptNumber,
    );
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /// Extract item id from CartLine.
  /// CartLine.item is a MockItem (has code); we must look up the Drift item id
  /// by code so the server payload carries the correct server-side itemId.
  ///
  /// If the item is not found in Drift (e.g. mock-only item), falls back to 0.
  int _itemIdFromLine(CartLine line) {
    // Items synced from Drift are stored in the catalog with a numeric id.
    // MockItem.code is the catalog code; CatalogItem carries the Drift id.
    // Since CartLine.item is a MockItem (no Drift id), we use a convention:
    // mock items that were added via addCatalogItem() originate from a CatalogItem
    // that holds the server itemId in its id field. However, MockItem only stores
    // the code string. For v1 we store the itemId in the MockItem.barcode field
    // when constructing from CatalogItem (see mocks.dart addCatalogItem).
    //
    // ARCHITECTURE NOTE: this is a known rough edge in v1. MockItem should carry
    // the server itemId directly; tracked as a follow-up.
    // For now: parse barcode as itemId when it looks numeric, else use 0.
    final parsed = int.tryParse(line.item.barcode);
    if (parsed != null && parsed > 0) return parsed;
    return 0; // fallback — server will REJECT if item id is missing
  }

}
