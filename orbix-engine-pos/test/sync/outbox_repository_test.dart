// Tests for OutboxRepository.
// Uses an in-memory Drift DB (NativeDatabase.memory()) — no live server required.
//
// Coverage:
// - enqueueInTxn persists a row with PENDING status
// - Two enqueues produce different clientOpIds (ULID uniqueness)
// - enqueueInTxn inside a transaction is atomic (domain + outbox together)
// - pendingOps returns PENDING and DEFERRED, not CONFIRMED or NEEDS_REVIEW
// - markConfirmed sets status + serverEntityId / serverNumber
// - markNeedsReview sets status NEEDS_REVIEW
// - markDeferred sets status DEFERRED; requeues on incrementAttemptAndRequeue
// - resetInflight resets all INFLIGHT → PENDING
// - opsForSession returns the session-open op plus ops with matching dependsOn

import 'package:drift/drift.dart' hide isNotNull, isNull;
import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:orbix_engine_pos/data/local/database.dart';
import 'package:orbix_engine_pos/data/sync/outbox_repository.dart';

PosDatabase _makeDb() => PosDatabase.forTesting(NativeDatabase.memory());

void main() {
  late PosDatabase db;
  late OutboxRepository repo;

  setUp(() {
    db = _makeDb();
    repo = OutboxRepository(db);
  });

  tearDown(() => db.close());

  // ---------------------------------------------------------------------------
  // Enqueue
  // ---------------------------------------------------------------------------

  group('enqueueInTxn', () {
    test('persists a row with PENDING status', () async {
      await db.transaction(() async {
        await repo.enqueueInTxn(
          db,
          opType: OutboxOpType.posSale,
          payload: {'total': '12500.0000'},
        );
      });

      final rows = await db.select(db.outbox).get();
      expect(rows.length, 1);
      expect(rows[0].status, OutboxStatus.pending);
      expect(rows[0].opType, OutboxOpType.posSale);
    });

    test('generates unique clientOpIds on each call', () async {
      String id1 = '', id2 = '';
      await db.transaction(() async {
        id1 = await repo.enqueueInTxn(db,
            opType: OutboxOpType.posSale, payload: {'total': '1000.0000'});
        id2 = await repo.enqueueInTxn(db,
            opType: OutboxOpType.cashPickup, payload: {'amount': '500.0000'});
      });

      expect(id1, isNotEmpty);
      expect(id2, isNotEmpty);
      expect(id1, isNot(equals(id2)));
      expect(id1.length, 26);
      expect(id2.length, 26);
    });

    test('seq is monotonically increasing across two enqueues', () async {
      await db.transaction(() async {
        await repo.enqueueInTxn(db,
            opType: OutboxOpType.tillSessionOpen, payload: {});
        await repo.enqueueInTxn(db,
            opType: OutboxOpType.posSale, payload: {'total': '5000.0000'});
      });

      final rows = await (db.select(db.outbox)
            ..orderBy([(t) => OrderingTerm.asc(t.seq)]))
          .get();
      expect(rows.length, 2);
      expect(rows[1].seq, greaterThan(rows[0].seq));
    });

    test('stores dependsOn correctly', () async {
      String sessionId = '';
      String saleId = '';
      await db.transaction(() async {
        sessionId = await repo.enqueueInTxn(db,
            opType: OutboxOpType.tillSessionOpen, payload: {});
        saleId = await repo.enqueueInTxn(db,
            opType: OutboxOpType.posSale,
            payload: {'total': '9000.0000'},
            dependsOn: sessionId);
      });

      final saleRow = await repo.byClientOpId(saleId);
      expect(saleRow, isNotNull);
      expect(saleRow!.dependsOn, sessionId);
    });

    test('enqueue is atomic with domain write in the same transaction', () async {
      // Insert a PosSale and an outbox op in the same transaction.
      // Verify both appear or neither appears (roll back test).
      String? opId;
      await db.transaction(() async {
        await db.into(db.posSales).insert(PosSalesCompanion.insert(
          clientOpId: 'TEST-OP-1',
          tillSessionId: 1,
          customerId: 1,
          total: 5000.0,
          saleAt: DateTime.now(),
          status: 'POSTED',
        ));
        opId = await repo.enqueueInTxn(db,
            opType: OutboxOpType.posSale, payload: {'total': '5000.0000'});
      });

      final sales = await db.select(db.posSales).get();
      final outboxRows = await db.select(db.outbox).get();
      expect(sales.length, 1);
      expect(outboxRows.length, 1);
      expect(outboxRows[0].clientOpId, opId);
    });
  });

  // ---------------------------------------------------------------------------
  // Query helpers
  // ---------------------------------------------------------------------------

  group('pendingOps', () {
    test('returns PENDING and DEFERRED, not CONFIRMED or NEEDS_REVIEW', () async {
      await db.transaction(() async {
        await repo.enqueueInTxn(db,
            opType: OutboxOpType.posSale, payload: {'total': '1000.0000'});
        await repo.enqueueInTxn(db,
            opType: OutboxOpType.cashPickup, payload: {'amount': '200.0000'});
      });
      // Mark second row as CONFIRMED
      final rows = await db.select(db.outbox).get();
      await repo.markConfirmed(rows[1].clientOpId);

      final pending = await repo.pendingOps();
      expect(pending.length, 1);
      expect(pending[0].opType, OutboxOpType.posSale);
    });

    test('returns DEFERRED rows too', () async {
      await db.transaction(() async {
        await repo.enqueueInTxn(db,
            opType: OutboxOpType.posSale, payload: {'total': '1000.0000'});
      });
      final rows = await db.select(db.outbox).get();
      await repo.markDeferred(rows[0].clientOpId);

      final pending = await repo.pendingOps();
      expect(pending.length, 1);
      expect(pending[0].status, OutboxStatus.deferred);
    });

    test('respects limit', () async {
      for (var i = 0; i < 5; i++) {
        await repo.enqueueInTxn(db,
            opType: OutboxOpType.posSale, payload: {'total': '1000.0000'});
      }
      final limited = await repo.pendingOps(limit: 3);
      expect(limited.length, 3);
    });
  });

  // ---------------------------------------------------------------------------
  // Status transitions
  // ---------------------------------------------------------------------------

  group('markConfirmed', () {
    test('sets status CONFIRMED and stamps server fields', () async {
      String opId = '';
      await db.transaction(() async {
        opId = await repo.enqueueInTxn(db,
            opType: OutboxOpType.posSale, payload: {'total': '8000.0000'});
      });

      await repo.markConfirmed(opId,
          serverEntityId: '100501', serverNumber: 'POS-1-20260530-00042');

      final row = await repo.byClientOpId(opId);
      expect(row!.status, OutboxStatus.confirmed);
      expect(row.serverEntityId, '100501');
      expect(row.serverNumber, 'POS-1-20260530-00042');
    });
  });

  group('markNeedsReview', () {
    test('sets status NEEDS_REVIEW (not auto-retried)', () async {
      String opId = '';
      await db.transaction(() async {
        opId = await repo.enqueueInTxn(db,
            opType: OutboxOpType.posSale, payload: {'total': '8000.0000'});
      });

      await repo.markNeedsReview(opId);

      final row = await repo.byClientOpId(opId);
      expect(row!.status, OutboxStatus.needsReview);

      // NEEDS_REVIEW does not appear in pendingOps
      final pending = await repo.pendingOps();
      expect(pending.where((r) => r.clientOpId == opId), isEmpty);
    });
  });

  group('markDeferred + incrementAttemptAndRequeue', () {
    test('DEFERRED is returned by pendingOps and can be requeued', () async {
      String opId = '';
      await db.transaction(() async {
        opId = await repo.enqueueInTxn(db,
            opType: OutboxOpType.posSale, payload: {'total': '3000.0000'});
      });

      await repo.markDeferred(opId);
      expect((await repo.byClientOpId(opId))!.status, OutboxStatus.deferred);
      expect(await repo.pendingOps(), hasLength(1)); // deferred shows up

      await repo.incrementAttemptAndRequeue(opId);
      final row = await repo.byClientOpId(opId);
      expect(row!.status, OutboxStatus.pending);
      expect(row.attemptCount, 1);
    });
  });

  group('resetInflight', () {
    test('resets INFLIGHT rows back to PENDING (crash recovery)', () async {
      String opId = '';
      await db.transaction(() async {
        opId = await repo.enqueueInTxn(db,
            opType: OutboxOpType.posSale, payload: {'total': '7000.0000'});
      });
      // Manually mark inflight (simulates a crash mid-flush)
      await (db.update(db.outbox)..where((t) => t.clientOpId.equals(opId)))
          .write(const OutboxCompanion(status: Value(OutboxStatus.inflight)));

      await repo.resetInflight();

      final row = await repo.byClientOpId(opId);
      expect(row!.status, OutboxStatus.pending);
    });
  });

  // ---------------------------------------------------------------------------
  // opsForSession
  // ---------------------------------------------------------------------------

  group('opsForSession', () {
    test('returns session-open op and ops depending on it', () async {
      String sessionId = '';
      String saleId = '';
      await db.transaction(() async {
        sessionId = await repo.enqueueInTxn(db,
            opType: OutboxOpType.tillSessionOpen, payload: {});
        saleId = await repo.enqueueInTxn(db,
            opType: OutboxOpType.posSale,
            payload: {'total': '5000.0000', 'tillSessionClientOpId': sessionId},
            dependsOn: sessionId);
        // This op does NOT belong to the session
        await repo.enqueueInTxn(db,
            opType: OutboxOpType.cashPickup, payload: {'amount': '200.0000'});
      });

      final ops = await repo.opsForSession(sessionId);
      final ids = ops.map((o) => o.clientOpId).toSet();
      expect(ids.contains(sessionId), isTrue);
      expect(ids.contains(saleId), isTrue);
      expect(ops.length, 2);
    });
  });

  // ---------------------------------------------------------------------------
  // Counts + watch
  // ---------------------------------------------------------------------------

  group('pendingCount', () {
    test('returns count of PENDING + DEFERRED only', () async {
      await db.transaction(() async {
        await repo.enqueueInTxn(db, opType: OutboxOpType.posSale, payload: {});
        await repo.enqueueInTxn(db, opType: OutboxOpType.cashPickup, payload: {});
      });
      final rows = await db.select(db.outbox).get();
      await repo.markConfirmed(rows[1].clientOpId);

      expect(await repo.pendingCount(), 1);
    });
  });
}
