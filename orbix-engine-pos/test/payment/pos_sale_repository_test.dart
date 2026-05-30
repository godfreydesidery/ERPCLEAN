// Tests for PosSaleRepository (US-POS-018).
//
// Coverage:
// - recordSale writes PosSale + PosSaleLines in one Drift transaction
// - recordSale enqueues a POS_SALE outbox op with dependsOn = sessionClientOpId
// - payload contains lines, payments, tillSessionId, clientOpId
// - dependsOn ordering: session op (lower seq) before sale op

import 'dart:convert';

import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:orbix_engine_pos/data/local/database.dart';
import 'package:orbix_engine_pos/data/sync/outbox_repository.dart';
import 'package:orbix_engine_pos/features/_demo/mocks.dart'
    show CartLine, MockItem, PaymentMethod;
import 'package:orbix_engine_pos/features/payment/payment_screen.dart'
    show TenderLine;
import 'package:orbix_engine_pos/features/payment/pos_sale_repository.dart';

PosDatabase _makeDb() => PosDatabase.forTesting(NativeDatabase.memory());

MockItem _item(String code, double price, {int id = 0}) => MockItem(
      code: code,
      barcode: id > 0 ? id.toString() : '',
      name: 'Item $code',
      group: 'Test',
      price: price,
      uom: 'EA',
    );

CartLine _line(MockItem item, double qty) => CartLine(item: item, qty: qty);

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

  group('recordSale', () {
    test('writes PosSale and PosSaleLines in Drift', () async {
      await outboxRepo.initSeq();
      final lines = [
        _line(_item('A', 5000, id: 10), 2),
        _line(_item('B', 3000, id: 20), 1),
      ];
      final tenders = [TenderLine(method: PaymentMethod.cash, amount: 13000)];

      await saleRepo.recordSale(
        sessionLocalId: 1,
        sessionClientOpId: 'SESSION-OP-1',
        sectionId: 1,
        customerId: 1,
        userId: 1,
        lines: lines,
        tenders: tenders,
        deviceId: 'TILL-1',
        businessDate: '2026-05-30',
      );

      final sales = await db.select(db.posSales).get();
      expect(sales, hasLength(1));
      expect(sales[0].total, closeTo(13000, 0.01));
      expect(sales[0].status, 'POSTED');
      expect(sales[0].synced, isFalse);

      final saleLines = await db.select(db.posSaleLines).get();
      expect(saleLines, hasLength(2));
    });

    test('enqueues POS_SALE op with dependsOn = sessionClientOpId', () async {
      await outboxRepo.initSeq();
      final result = await saleRepo.recordSale(
        sessionLocalId: 1,
        sessionClientOpId: 'SESSION-OP-ABC',
        sectionId: 1,
        customerId: 1,
        userId: 1,
        lines: [_line(_item('X', 10000, id: 5), 1)],
        tenders: [TenderLine(method: PaymentMethod.cash, amount: 10000)],
        deviceId: 'TILL-1',
        businessDate: '2026-05-30',
      );

      final op = await outboxRepo.byClientOpId(result.clientOpId);
      expect(op, isNotNull);
      expect(op!.opType, OutboxOpType.posSale);
      expect(op.dependsOn, 'SESSION-OP-ABC');
      expect(op.status, OutboxStatus.pending);
    });

    test('payload contains clientOpId, lines, payments, and tillSessionClientOpId', () async {
      await outboxRepo.initSeq();
      final result = await saleRepo.recordSale(
        sessionLocalId: 1,
        sessionClientOpId: 'SESSION-OP-X',
        sessionServerId: '99',
        sectionId: 1,
        customerId: 1,
        userId: 1,
        lines: [_line(_item('P', 20000, id: 7), 3)],
        tenders: [TenderLine(method: PaymentMethod.mobileMoney, amount: 60000)],
        deviceId: 'TILL-1',
        businessDate: '2026-05-30',
      );

      final op = await outboxRepo.byClientOpId(result.clientOpId);
      final payload = jsonDecode(op!.payloadJson) as Map<String, dynamic>;

      expect(payload['clientOpId'], result.clientOpId);
      expect(payload['tillSessionClientOpId'], 'SESSION-OP-X');
      expect(payload['tillSessionId'], 99); // from sessionServerId

      final lines = payload['lines'] as List;
      expect(lines, hasLength(1));
      expect(lines[0]['itemId'], 7);
      expect(lines[0]['qty'], '3.0000');
      expect(lines[0]['unitPrice'], '20000.0000');

      final payments = payload['payments'] as List;
      expect(payments, hasLength(1));
      expect(payments[0]['method'], 'MOBILE_MONEY');
      expect(payments[0]['amount'], '60000.0000');
    });

    test('dependsOn order: session op seq < sale op seq', () async {
      await outboxRepo.initSeq();
      // Enqueue session op first (simulating till-open).
      String sessionOpId = '';
      await db.transaction(() async {
        sessionOpId = await outboxRepo.enqueueInTxn(
          db,
          opType: OutboxOpType.tillSessionOpen,
          payload: {'tillId': 1, 'openingFloatAmount': '100000.0000'},
        );
      });

      final result = await saleRepo.recordSale(
        sessionLocalId: 1,
        sessionClientOpId: sessionOpId,
        sectionId: 1,
        customerId: 1,
        userId: 1,
        lines: [_line(_item('Q', 5000, id: 3), 1)],
        tenders: [TenderLine(method: PaymentMethod.cash, amount: 5000)],
        deviceId: 'TILL-1',
        businessDate: '2026-05-30',
      );

      final sessionOp = await outboxRepo.byClientOpId(sessionOpId);
      final saleOp = await outboxRepo.byClientOpId(result.clientOpId);

      // Session op must have a lower sequence number than the sale op.
      expect(sessionOp!.seq, lessThan(saleOp!.seq));
      expect(saleOp.dependsOn, sessionOpId);
    });
  });
}
