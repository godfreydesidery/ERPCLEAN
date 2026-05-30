// Tests for CashMovementRepository (US-POS-013 cash pickup, US-POS-014 petty cash).
//
// Uses an in-memory Drift DB — no live server required.
//
// Coverage:
// - recordCashPickup enqueues a CASH_PICKUP outbox op with the correct amount
// - recordCashPickup rejects amount <= 0
// - recordPettyCash enqueues a PETTY_CASH outbox op with the correct payload
// - recordPettyCash rejects amount <= 0
// - cashPickupTotalForSession sums pickup amounts for the session
// - pettyCashTotalForSession sums petty-cash amounts for the session
// - Movements for other sessions are not included in the totals

import 'dart:convert';

import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:orbix_engine_pos/data/local/database.dart';
import 'package:orbix_engine_pos/data/sync/outbox_repository.dart';
import 'package:orbix_engine_pos/features/cash/cash_movement_repository.dart';

PosDatabase _makeDb() => PosDatabase.forTesting(NativeDatabase.memory());

Map<String, dynamic> _decode(String json) =>
    jsonDecode(json) as Map<String, dynamic>;

void main() {
  late PosDatabase db;
  late OutboxRepository outbox;
  late CashMovementRepository repo;

  setUp(() {
    db = _makeDb();
    outbox = OutboxRepository(db);
    repo = CashMovementRepository(db: db, outbox: outbox);
  });

  tearDown(() => db.close());

  // ---------------------------------------------------------------------------
  // Cash pickup (US-POS-013)
  // ---------------------------------------------------------------------------

  group('recordCashPickup', () {
    test('enqueues a CASH_PICKUP outbox op with correct amount', () async {
      const sessionOpId = 'SESSION-001';
      const amount = 50000.0;

      final result = await repo.recordCashPickup(
        tillSessionClientOpId: sessionOpId,
        amount: amount,
        note: 'Moved to safe',
      );

      expect(result.clientOpId, isNotEmpty);

      final rows = await db.select(db.outbox).get();
      expect(rows.length, 1);
      final row = rows.first;
      expect(row.opType, OutboxOpType.cashPickup);
      expect(row.status, OutboxStatus.pending);
      expect(row.dependsOn, sessionOpId);

      // Payload must include 4dp amount string
      final payload = _decode(row.payloadJson);
      expect(payload['amount'], '50000.0000');
      expect(payload['note'], 'Moved to safe');
    });

    test('omits empty note from payload', () async {
      await repo.recordCashPickup(
        tillSessionClientOpId: 'S1',
        amount: 10000.0,
        note: '',
      );
      final row = (await db.select(db.outbox).get()).first;
      final payload = _decode(row.payloadJson);
      expect(payload.containsKey('note'), isFalse);
    });

    test('rejects amount <= 0', () async {
      expect(
        () => repo.recordCashPickup(
            tillSessionClientOpId: 'S1', amount: 0),
        throwsA(isA<ArgumentError>()),
      );
      expect(
        () => repo.recordCashPickup(
            tillSessionClientOpId: 'S1', amount: -100),
        throwsA(isA<ArgumentError>()),
      );
    });

    test('generates unique clientOpIds for each pickup', () async {
      final r1 = await repo.recordCashPickup(
          tillSessionClientOpId: 'S1', amount: 1000);
      final r2 = await repo.recordCashPickup(
          tillSessionClientOpId: 'S1', amount: 2000);

      expect(r1.clientOpId, isNot(equals(r2.clientOpId)));
    });
  });

  // ---------------------------------------------------------------------------
  // Petty cash (US-POS-014)
  // ---------------------------------------------------------------------------

  group('recordPettyCash', () {
    test('enqueues a PETTY_CASH outbox op with correct payload', () async {
      const sessionOpId = 'SESSION-002';
      const amount = 5000.0;

      final result = await repo.recordPettyCash(
        tillSessionClientOpId: sessionOpId,
        amount: amount,
        category: PettyCashCategory.transport,
        paidTo: 'Driver Ali',
        description: 'Delivery fuel',
      );

      expect(result.clientOpId, isNotEmpty);

      final rows = await db.select(db.outbox).get();
      expect(rows.length, 1);
      final row = rows.first;
      expect(row.opType, OutboxOpType.pettyCash);
      expect(row.status, OutboxStatus.pending);
      expect(row.dependsOn, sessionOpId);

      final payload = _decode(row.payloadJson);
      expect(payload['amount'], '5000.0000');
      expect(payload['category'], 'TRANSPORT');
      expect(payload['paidTo'], 'Driver Ali');
      expect(payload['description'], 'Delivery fuel');
    });

    test('rejects amount <= 0', () async {
      expect(
        () => repo.recordPettyCash(
            tillSessionClientOpId: 'S1',
            amount: 0,
            category: PettyCashCategory.other),
        throwsA(isA<ArgumentError>()),
      );
    });

    test('all PettyCashCategory values produce correct wire names', () {
      expect(PettyCashCategory.transport.wireValue, 'TRANSPORT');
      expect(PettyCashCategory.office.wireValue, 'OFFICE');
      expect(PettyCashCategory.maintenance.wireValue, 'MAINTENANCE');
      expect(PettyCashCategory.other.wireValue, 'OTHER');
    });
  });

  // ---------------------------------------------------------------------------
  // Session totals (manifest + X-report queries)
  // ---------------------------------------------------------------------------

  group('cashPickupTotalForSession', () {
    test('sums pickup amounts for the session', () async {
      const sessionOpId = 'SESSION-003';

      // Enqueue two pickups for the session
      await repo.recordCashPickup(
          tillSessionClientOpId: sessionOpId, amount: 30000);
      await repo.recordCashPickup(
          tillSessionClientOpId: sessionOpId, amount: 20000);

      final total = await repo.cashPickupTotalForSession(sessionOpId);
      expect(total, closeTo(50000.0, 0.01));
    });

    test('excludes movements from other sessions', () async {
      const sessionA = 'SESSION-A';
      const sessionB = 'SESSION-B';

      await repo.recordCashPickup(tillSessionClientOpId: sessionA, amount: 40000);
      await repo.recordCashPickup(tillSessionClientOpId: sessionB, amount: 15000);

      final totalA = await repo.cashPickupTotalForSession(sessionA);
      expect(totalA, closeTo(40000.0, 0.01));

      final totalB = await repo.cashPickupTotalForSession(sessionB);
      expect(totalB, closeTo(15000.0, 0.01));
    });

    test('returns 0 when no pickups exist for the session', () async {
      final total = await repo.cashPickupTotalForSession('NO-SESSION');
      expect(total, 0.0);
    });
  });

  group('pettyCashTotalForSession', () {
    test('sums petty-cash amounts for the session', () async {
      const sessionOpId = 'SESSION-004';

      await repo.recordPettyCash(
          tillSessionClientOpId: sessionOpId,
          amount: 3000,
          category: PettyCashCategory.office);
      await repo.recordPettyCash(
          tillSessionClientOpId: sessionOpId,
          amount: 7000,
          category: PettyCashCategory.transport);

      final total = await repo.pettyCashTotalForSession(sessionOpId);
      expect(total, closeTo(10000.0, 0.01));
    });

    test('returns 0 when no payouts exist for the session', () async {
      final total = await repo.pettyCashTotalForSession('NO-SESSION');
      expect(total, 0.0);
    });
  });

  group('cashPickupCountForSession', () {
    test('counts pickups correctly', () async {
      const sessionOpId = 'SESSION-005';
      await repo.recordCashPickup(tillSessionClientOpId: sessionOpId, amount: 1000);
      await repo.recordCashPickup(tillSessionClientOpId: sessionOpId, amount: 2000);

      expect(await repo.cashPickupCountForSession(sessionOpId), 2);
    });
  });

  group('pettyCashCountForSession', () {
    test('counts petty-cash payouts correctly', () async {
      const sessionOpId = 'SESSION-006';
      await repo.recordPettyCash(
          tillSessionClientOpId: sessionOpId,
          amount: 500,
          category: PettyCashCategory.other);

      expect(await repo.pettyCashCountForSession(sessionOpId), 1);
    });
  });
}
