// Tests for OutboxRepository.backfillTillSessionId and
// OutboxDispatcher's server-id back-fill after TILL_SESSION_OPEN ACCEPTED.
//
// Coverage:
// - backfillTillSessionId patches tillSessionId in PENDING dependent ops
// - back-fill does not overwrite a non-zero existing tillSessionId
// - dispatcher stamps TillSession.serverUid after TILL_SESSION_OPEN ACCEPTED
// - dispatcher back-fills dependent PENDING ops' payloads

import 'dart:convert';

import 'package:drift/native.dart';
import 'package:drift/drift.dart' show Value;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:orbix_engine_pos/data/local/database.dart';
import 'package:orbix_engine_pos/data/sync/outbox_dispatcher.dart';
import 'package:orbix_engine_pos/data/sync/outbox_repository.dart';
import 'package:orbix_engine_pos/data/sync/sync_api_client.dart';
import 'package:orbix_engine_pos/data/sync/sync_models.dart';
import 'package:orbix_engine_pos/data/sync/sync_repository.dart';

class MockSyncApiClient extends Mock implements SyncApiClient {}
class MockSyncRepository extends Mock implements SyncRepository {}
class FakeSyncPushRequest extends Fake implements SyncPushRequest {}
class FakeTillSessionCloseRequest extends Fake implements TillSessionCloseRequest {}

PosDatabase _makeDb() => PosDatabase.forTesting(NativeDatabase.memory());

SyncPushResult _makeResult(List<SyncOpResult> results) => SyncPushResult(
      batchAcceptedCount: results.where((r) => r.verdict == SyncVerdict.accepted).length,
      batchRejectedCount: 0,
      serverReceivedAt: DateTime.utc(2026, 5, 30),
      resyncRequired: false,
      results: results,
    );

void main() {
  setUpAll(() {
    registerFallbackValue(FakeSyncPushRequest());
    registerFallbackValue(FakeTillSessionCloseRequest());
  });

  late PosDatabase db;
  late OutboxRepository outboxRepo;

  setUp(() {
    db = _makeDb();
    outboxRepo = OutboxRepository(db);
  });

  tearDown(() => db.close());

  // ---------------------------------------------------------------------------
  // OutboxRepository.backfillTillSessionId
  // ---------------------------------------------------------------------------

  group('backfillTillSessionId', () {
    test('patches PENDING ops whose dependsOn matches session', () async {
      await outboxRepo.initSeq();
      String sessionOpId = '';
      String pickupOpId = '';
      String pettyOpId = '';

      await db.transaction(() async {
        sessionOpId = await outboxRepo.enqueueInTxn(db,
            opType: OutboxOpType.tillSessionOpen,
            payload: {'tillId': 1, 'openingFloatAmount': '100000.0000'});
        pickupOpId = await outboxRepo.enqueueInTxn(db,
            opType: OutboxOpType.cashPickup,
            payload: {'amount': '10000.0000', 'authorisedBy': 0, 'tillSessionId': 0},
            dependsOn: sessionOpId);
        pettyOpId = await outboxRepo.enqueueInTxn(db,
            opType: OutboxOpType.pettyCash,
            payload: {
              'amount': '2500.0000',
              'category': 'TRANSPORT',
              'authorisedBy': 0,
              'tillSessionId': 0,
            },
            dependsOn: sessionOpId);
      });

      await outboxRepo.backfillTillSessionId(
        sessionClientOpId: sessionOpId,
        serverSessionId: 777,
      );

      final pickup = await outboxRepo.byClientOpId(pickupOpId);
      final petty = await outboxRepo.byClientOpId(pettyOpId);
      final pickupPayload = jsonDecode(pickup!.payloadJson) as Map<String, dynamic>;
      final pettyPayload = jsonDecode(petty!.payloadJson) as Map<String, dynamic>;

      expect(pickupPayload['tillSessionId'], 777);
      expect(pettyPayload['tillSessionId'], 777);
    });

    test('does not patch when tillSessionId is already non-zero', () async {
      await outboxRepo.initSeq();
      String sessionOpId = '';
      String pickupOpId = '';

      await db.transaction(() async {
        sessionOpId = await outboxRepo.enqueueInTxn(db,
            opType: OutboxOpType.tillSessionOpen,
            payload: {'tillId': 1, 'openingFloatAmount': '100000.0000'});
        pickupOpId = await outboxRepo.enqueueInTxn(db,
            opType: OutboxOpType.cashPickup,
            payload: {'amount': '10000.0000', 'authorisedBy': 0, 'tillSessionId': 999},
            dependsOn: sessionOpId);
      });

      await outboxRepo.backfillTillSessionId(
        sessionClientOpId: sessionOpId,
        serverSessionId: 777,
      );

      final pickup = await outboxRepo.byClientOpId(pickupOpId);
      final payload = jsonDecode(pickup!.payloadJson) as Map<String, dynamic>;
      // Should NOT have been overwritten.
      expect(payload['tillSessionId'], 999);
    });

    test('does not patch CONFIRMED ops', () async {
      await outboxRepo.initSeq();
      String sessionOpId = '';
      String pickupOpId = '';

      await db.transaction(() async {
        sessionOpId = await outboxRepo.enqueueInTxn(db,
            opType: OutboxOpType.tillSessionOpen,
            payload: {'tillId': 1, 'openingFloatAmount': '100000.0000'});
        pickupOpId = await outboxRepo.enqueueInTxn(db,
            opType: OutboxOpType.cashPickup,
            payload: {'amount': '5000.0000', 'authorisedBy': 0, 'tillSessionId': 0},
            dependsOn: sessionOpId);
      });

      // Mark pickup as CONFIRMED (already pushed).
      await outboxRepo.markConfirmed(pickupOpId);

      await outboxRepo.backfillTillSessionId(
        sessionClientOpId: sessionOpId,
        serverSessionId: 888,
      );

      final pickup = await outboxRepo.byClientOpId(pickupOpId);
      final payload = jsonDecode(pickup!.payloadJson) as Map<String, dynamic>;
      // CONFIRMED ops must not be touched.
      expect(payload['tillSessionId'], 0);
    });
  });

  // ---------------------------------------------------------------------------
  // Dispatcher integration: TILL_SESSION_OPEN ACCEPTED → back-fill dependent ops
  // ---------------------------------------------------------------------------

  group('dispatcher back-fill after TILL_SESSION_OPEN ACCEPTED', () {
    late MockSyncApiClient mockApi;
    late MockSyncRepository mockSync;
    late OutboxDispatcher dispatcher;

    setUp(() {
      mockApi = MockSyncApiClient();
      mockSync = MockSyncRepository();
      dispatcher = OutboxDispatcher(
        outboxRepo: outboxRepo,
        apiClient: mockApi,
        syncRepo: mockSync,
      );
      when(() => mockSync.pull()).thenAnswer((_) async => false);
    });

    test('back-fills tillSessionId into PENDING cashPickup op after session ACCEPTED', () async {
      await outboxRepo.initSeq();
      String sessionOpId = '';
      String pickupOpId = '';

      await db.transaction(() async {
        sessionOpId = await outboxRepo.enqueueInTxn(db,
            opType: OutboxOpType.tillSessionOpen,
            payload: {'tillId': 1, 'openingFloatAmount': '100000.0000'});

        await db.into(db.tillSessions).insert(TillSessionsCompanion.insert(
          clientOpId: Value(sessionOpId),
          tillId: 1,
          businessDate: '2026-05-30',
          openedBy: 1,
          openedAt: DateTime.now(),
          openingFloat: 100000.0,
          status: 'OPEN',
        ));

        pickupOpId = await outboxRepo.enqueueInTxn(db,
            opType: OutboxOpType.cashPickup,
            payload: {'amount': '20000.0000', 'authorisedBy': 0, 'tillSessionId': 0},
            dependsOn: sessionOpId);
      });

      // Simulate the server returning DEFERRED for pickup (dependsOn not yet settled),
      // then ACCEPTED for session.
      when(() => mockApi.push(any())).thenAnswer((_) async {
        return _makeResult([
          SyncOpResult(
            clientOpId: sessionOpId,
            verdict: SyncVerdict.accepted,
            serverEntityId: '12345', // server-assigned session id
            serverEntityUid: 'SESSION-UID',
          ),
          SyncOpResult(
            clientOpId: pickupOpId,
            verdict: SyncVerdict.deferred,
          ),
        ]);
      });

      await dispatcher.flush();

      // The pickup payload should now have tillSessionId = 12345.
      final pickup = await outboxRepo.byClientOpId(pickupOpId);
      final payload = jsonDecode(pickup!.payloadJson) as Map<String, dynamic>;
      expect(payload['tillSessionId'], 12345);

      // The session outbox row must be CONFIRMED.
      final sessionOp = await outboxRepo.byClientOpId(sessionOpId);
      expect(sessionOp!.status, OutboxStatus.confirmed);
      expect(sessionOp.serverEntityId, '12345');

      // The TillSession Drift row must have serverUid stamped.
      final sessionRows = await db.select(db.tillSessions).get();
      expect(sessionRows[0].serverUid, '12345');
    });
  });
}
