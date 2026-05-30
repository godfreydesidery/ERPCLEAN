// Tests for refund_screen Drift data source (US-POS-012).
//
// Coverage:
// - recentSales returns empty list when no sales exist
// - recentSales returns sales joined to lines (with item details)
// - recentSales sorts by saleAt descending (newest first)
// - recentSales limits by the limit parameter
// - recentSales shows clientOpId as receiptNo when serverNumber is null
// - recentSales shows serverNumber as receiptNo when present
// - recordRefund enqueues a POS_SALE outbox op with kind=REFUND and dependsOn

import 'dart:convert';

import 'package:drift/drift.dart' hide isNull, isNotNull;
import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:orbix_engine_pos/data/local/database.dart';
import 'package:orbix_engine_pos/data/sync/outbox_repository.dart';
import 'package:orbix_engine_pos/features/payment/pos_sale_repository.dart';

PosDatabase _makeDb() => PosDatabase.forTesting(NativeDatabase.memory());

Future<void> _seedItem(PosDatabase db, {required int id, required String code}) async {
  await db.into(db.items).insert(ItemsCompanion.insert(
    id: Value(id),
    code: code,
    name: 'Item $code',
    price: 0.0,
    vatGroup: 'STD',
    itemGroupId: 1,
  ));
}

Future<int> _seedSale(
  PosDatabase db, {
  required String clientOpId,
  String? serverNumber,
  DateTime? saleAt,
  double total = 10000,
}) async {
  return db.into(db.posSales).insert(PosSalesCompanion.insert(
    clientOpId: clientOpId,
    tillSessionId: 1,
    customerId: 1,
    total: total,
    saleAt: saleAt ?? DateTime.now(),
    status: 'POSTED',
    serverNumber: Value(serverNumber),
  ));
}

Future<void> _seedLine(
  PosDatabase db, {
  required int saleId,
  required int itemId,
  double qty = 1,
  double unitPrice = 10000,
}) async {
  await db.into(db.posSaleLines).insert(PosSaleLinesCompanion.insert(
    saleId: saleId,
    itemId: itemId,
    qty: qty,
    unitPrice: unitPrice,
    lineTotal: unitPrice * qty,
  ));
}

void main() {
  late PosDatabase db;
  late OutboxRepository outboxRepo;
  late PosSaleRepository saleRepo;

  setUp(() {
    db = _makeDb();
    outboxRepo = OutboxRepository(db);
    saleRepo = PosSaleRepository(db: db, outbox: outboxRepo);
  });

  tearDown(() => db.close());

  // ---------------------------------------------------------------------------
  // recentSales
  // ---------------------------------------------------------------------------

  group('recentSales', () {
    test('returns empty list when no sales', () async {
      final sales = await saleRepo.recentSales();
      expect(sales, isEmpty);
    });

    test('returns sale with line details joined from Items', () async {
      await outboxRepo.initSeq();
      await _seedItem(db, id: 10, code: 'COCA');
      final saleId = await _seedSale(db, clientOpId: 'OP-1');
      await _seedLine(db, saleId: saleId, itemId: 10, qty: 2, unitPrice: 2000);

      final sales = await saleRepo.recentSales();
      expect(sales, hasLength(1));
      expect(sales[0].clientOpId, 'OP-1');
      expect(sales[0].lines, hasLength(1));
      expect(sales[0].lines[0].itemCode, 'COCA');
      expect(sales[0].lines[0].qty, 2);
      expect(sales[0].lines[0].unitPrice, closeTo(2000, 0.01));
    });

    test('uses serverNumber as receiptNo when present', () async {
      await outboxRepo.initSeq();
      await _seedSale(db,
          clientOpId: 'OP-SRV', serverNumber: 'POS-1-20260530-00042');

      final sales = await saleRepo.recentSales();
      expect(sales[0].receiptNo, 'POS-1-20260530-00042');
    });

    test('uses clientOpId as receiptNo when serverNumber is null', () async {
      await outboxRepo.initSeq();
      await _seedSale(db, clientOpId: 'OP-LOCAL');

      final sales = await saleRepo.recentSales();
      expect(sales[0].receiptNo, 'OP-LOCAL');
    });

    test('sorts newest first', () async {
      await outboxRepo.initSeq();
      final older = DateTime.now().subtract(const Duration(hours: 1));
      final newer = DateTime.now();
      await _seedSale(db, clientOpId: 'OP-OLD', saleAt: older);
      await _seedSale(db, clientOpId: 'OP-NEW', saleAt: newer);

      final sales = await saleRepo.recentSales();
      expect(sales[0].clientOpId, 'OP-NEW');
      expect(sales[1].clientOpId, 'OP-OLD');
    });

    test('respects limit parameter', () async {
      await outboxRepo.initSeq();
      for (var i = 0; i < 5; i++) {
        await _seedSale(db,
            clientOpId: 'OP-$i',
            saleAt: DateTime.now().subtract(Duration(minutes: i)));
      }

      final sales = await saleRepo.recentSales(limit: 3);
      expect(sales, hasLength(3));
    });
  });

  // ---------------------------------------------------------------------------
  // recordRefund
  // ---------------------------------------------------------------------------

  group('recordRefund', () {
    test('enqueues POS_SALE op with kind=REFUND and dependsOn=sessionOpId',
        () async {
      await outboxRepo.initSeq();

      final refundClientOpId = await saleRepo.recordRefund(
        sessionLocalId: 1,
        sessionClientOpId: 'SESSION-OP',
        originalSaleClientOpId: 'ORIG-SALE-OP',
        customerId: 1,
        userId: 1,
        lines: [
          const RefundLine(itemId: 5, qty: 1, unitPrice: 5000, lineTotal: 5000),
        ],
        reason: 'Damaged item',
        deviceId: 'TILL-1',
        businessDate: '2026-05-30',
      );

      final op = await outboxRepo.byClientOpId(refundClientOpId);
      expect(op, isNotNull);
      expect(op!.opType, OutboxOpType.posSale);
      expect(op.dependsOn, 'SESSION-OP');
      expect(op.status, OutboxStatus.pending);

      final payload = jsonDecode(op.payloadJson) as Map<String, dynamic>;
      expect(payload['kind'], 'REFUND');
      expect(payload['originalSaleClientOpId'], 'ORIG-SALE-OP');

      final lines = payload['lines'] as List;
      expect(lines, hasLength(1));
      expect(lines[0]['itemId'], 5);
      expect(lines[0]['qty'], '1.0000');
    });

    test('includes originalSaleUid in payload when provided', () async {
      await outboxRepo.initSeq();

      final refundClientOpId = await saleRepo.recordRefund(
        sessionLocalId: 1,
        sessionClientOpId: 'SESSION-OP',
        originalSaleClientOpId: 'ORIG-OP',
        originalSaleServerUid: 'SERVER-UID-123',
        customerId: 1,
        userId: 1,
        lines: [
          const RefundLine(itemId: 3, qty: 2, unitPrice: 3000, lineTotal: 6000),
        ],
        reason: 'Wrong item',
        deviceId: 'TILL-1',
        businessDate: '2026-05-30',
      );

      final op = await outboxRepo.byClientOpId(refundClientOpId);
      final payload = jsonDecode(op!.payloadJson) as Map<String, dynamic>;
      expect(payload['originalSaleUid'], 'SERVER-UID-123');
    });
  });
}
