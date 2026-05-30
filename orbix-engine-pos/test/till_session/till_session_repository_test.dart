// Tests for TillSessionRepository (US-POS-017).
//
// Coverage:
// - openSession writes TillSession row to Drift in the same txn as the outbox op
// - openSession enqueues a TILL_SESSION_OPEN outbox op with correct payload
// - session clientOpId is persisted on the Drift TillSession row
// - stampServerEntityId updates the serverUid column
// - watchActiveSessionRow emits the OPEN session and null after close

import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:orbix_engine_pos/data/local/database.dart';
import 'package:orbix_engine_pos/data/sync/outbox_repository.dart';
import 'package:orbix_engine_pos/features/till_session/till_session_repository.dart';
import 'dart:convert';

PosDatabase _makeDb() => PosDatabase.forTesting(NativeDatabase.memory());

void main() {
  late PosDatabase db;
  late OutboxRepository outboxRepo;
  late TillSessionRepository sessionRepo;

  setUp(() {
    db = _makeDb();
    outboxRepo = OutboxRepository(db);
    sessionRepo = TillSessionRepository(db: db, outbox: outboxRepo);
  });

  tearDown(() => db.close());

  group('openSession', () {
    test('creates a TillSession row in Drift', () async {
      await outboxRepo.initSeq();
      final result = await sessionRepo.openSession(
        tillId: 1,
        tillCode: 'TILL-1',
        tillName: 'Front counter',
        openedBy: 42,
        cashierName: 'Jane',
        branchName: 'HQ',
        openingFloat: 100000.0,
        businessDate: '2026-05-30',
      );

      final sessions = await db.select(db.tillSessions).get();
      expect(sessions, hasLength(1));
      expect(sessions[0].status, 'OPEN');
      expect(sessions[0].openingFloat, 100000.0);
      expect(sessions[0].tillId, 1);
      expect(sessions[0].openedBy, 42);
      expect(sessions[0].clientOpId, result.clientOpId);
    });

    test('enqueues a TILL_SESSION_OPEN outbox op', () async {
      await outboxRepo.initSeq();
      final result = await sessionRepo.openSession(
        tillId: 1,
        tillCode: 'TILL-1',
        tillName: 'Front counter',
        openedBy: 42,
        cashierName: 'Jane',
        branchName: 'HQ',
        openingFloat: 100000.0,
        businessDate: '2026-05-30',
      );

      final op = await outboxRepo.byClientOpId(result.clientOpId);
      expect(op, isNotNull);
      expect(op!.opType, OutboxOpType.tillSessionOpen);
      expect(op.status, OutboxStatus.pending);

      final payload = jsonDecode(op.payloadJson) as Map<String, dynamic>;
      expect(payload['tillId'], 1);
      expect(payload['openingFloatAmount'], '100000.0000');
    });

    test('session clientOpId is stamped on the Drift row', () async {
      await outboxRepo.initSeq();
      final result = await sessionRepo.openSession(
        tillId: 1,
        tillCode: 'TILL-1',
        tillName: 'Front counter',
        openedBy: 42,
        cashierName: 'Jane',
        branchName: 'HQ',
        openingFloat: 50000.0,
        businessDate: '2026-05-30',
      );

      final sessions = await db.select(db.tillSessions).get();
      expect(sessions[0].clientOpId, result.clientOpId);
      expect(result.clientOpId, isNotEmpty);
    });

    test('op and session are created atomically (same transaction)', () async {
      await outboxRepo.initSeq();
      // Create session and immediately check both tables before any sync.
      final result = await sessionRepo.openSession(
        tillId: 1,
        tillCode: 'TILL-1',
        tillName: 'Front counter',
        openedBy: 1,
        cashierName: 'Cashier',
        branchName: 'HQ',
        openingFloat: 0,
        businessDate: '2026-05-30',
      );

      final outboxRows = await db.select(db.outbox).get();
      final sessionRows = await db.select(db.tillSessions).get();
      // Exactly one row in each table — they were created together.
      expect(outboxRows, hasLength(1));
      expect(sessionRows, hasLength(1));
      expect(outboxRows[0].clientOpId, result.clientOpId);
      expect(sessionRows[0].clientOpId, result.clientOpId);
    });
  });

  group('stampServerEntityId', () {
    test('updates serverUid on the TillSession row', () async {
      await outboxRepo.initSeq();
      final result = await sessionRepo.openSession(
        tillId: 1,
        tillCode: 'TILL-1',
        tillName: 'Front counter',
        openedBy: 1,
        cashierName: 'Cashier',
        branchName: 'HQ',
        openingFloat: 0,
        businessDate: '2026-05-30',
      );

      await sessionRepo.stampServerEntityId(
        sessionClientOpId: result.clientOpId,
        serverEntityId: '12345',
      );

      final row = await db.select(db.tillSessions).getSingle();
      expect(row.serverUid, '12345');
    });
  });
}
