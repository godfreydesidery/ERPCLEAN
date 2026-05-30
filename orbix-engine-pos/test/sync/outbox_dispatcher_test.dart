// Tests for OutboxDispatcher verdict handling.
// Uses an in-memory DB and a mock SyncApiClient — no live server required.
//
// Coverage:
// - ACCEPTED verdict: outbox row CONFIRMED, PosSale.synced flipped
// - DUPLICATE verdict: treated identically to ACCEPTED (idempotent retry)
// - REJECTED verdict: row moves to NEEDS_REVIEW, not retried
// - DEFERRED verdict: row stays DEFERRED, re-pushed next cycle
// - Entire batch is sent; partial failure does not block siblings
// - 426 CONTRACT_TOO_OLD halts the dispatcher

import 'package:dio/dio.dart';
import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:orbix_engine_pos/data/local/database.dart';
import 'package:orbix_engine_pos/data/sync/outbox_dispatcher.dart';
import 'package:orbix_engine_pos/data/sync/outbox_repository.dart';
import 'package:orbix_engine_pos/data/sync/sync_api_client.dart';
import 'package:orbix_engine_pos/data/sync/sync_models.dart';
import 'package:orbix_engine_pos/data/sync/sync_repository.dart';

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

class MockSyncApiClient extends Mock implements SyncApiClient {}
class MockSyncRepository extends Mock implements SyncRepository {}
class FakeSyncPushRequest extends Fake implements SyncPushRequest {}
class FakeTillSessionCloseRequest extends Fake implements TillSessionCloseRequest {}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

PosDatabase _makeDb() => PosDatabase.forTesting(NativeDatabase.memory());

SyncPushResult _makeResult(List<SyncOpResult> results) => SyncPushResult(
      batchAcceptedCount: results.where((r) => r.verdict == SyncVerdict.accepted).length,
      batchRejectedCount: results.where((r) => r.verdict == SyncVerdict.rejected).length,
      serverReceivedAt: DateTime.utc(2026, 5, 30, 8, 14, 5),
      resyncRequired: false,
      results: results,
    );

SyncOpResult _accepted(String opId) => SyncOpResult(
      clientOpId: opId,
      verdict: SyncVerdict.accepted,
      serverEntityId: '100001',
      serverEntityUid: '01JBVQ...',
      serverNumber: 'POS-1-20260530-00001',
    );

SyncOpResult _duplicate(String opId) => SyncOpResult(
      clientOpId: opId,
      verdict: SyncVerdict.duplicate,
      serverEntityId: '100001',
      serverEntityUid: '01JBVQ...',
      serverNumber: 'POS-1-20260530-00001',
    );

SyncOpResult _rejected(String opId) => SyncOpResult(
      clientOpId: opId,
      verdict: SyncVerdict.rejected,
      errorCode: 'ITEM_ARCHIVED',
      errorMessage: 'Item is archived',
    );

SyncOpResult _deferred(String opId) => SyncOpResult(
      clientOpId: opId,
      verdict: SyncVerdict.deferred,
    );

void main() {
  setUpAll(() {
    registerFallbackValue(FakeSyncPushRequest());
    registerFallbackValue(FakeTillSessionCloseRequest());
  });

  late PosDatabase db;
  late OutboxRepository outboxRepo;
  late MockSyncApiClient mockApi;
  late MockSyncRepository mockSync;
  late OutboxDispatcher dispatcher;

  setUp(() {
    db = _makeDb();
    outboxRepo = OutboxRepository(db);
    mockApi = MockSyncApiClient();
    mockSync = MockSyncRepository();
    dispatcher = OutboxDispatcher(
      outboxRepo: outboxRepo,
      apiClient: mockApi,
      syncRepo: mockSync,
    );

    // Default: pull is a no-op in unit tests
    when(() => mockSync.pull()).thenAnswer((_) async => false);
  });

  tearDown(() => db.close());

  // ---------------------------------------------------------------------------
  // ACCEPTED / DUPLICATE
  // ---------------------------------------------------------------------------

  group('ACCEPTED verdict', () {
    test('marks outbox row CONFIRMED and stamps server fields', () async {
      String opId = '';
      await db.transaction(() async {
        opId = await outboxRepo.enqueueInTxn(
          db,
          opType: OutboxOpType.posSale,
          payload: {'total': '5000.0000'},
        );
      });

      when(() => mockApi.push(any())).thenAnswer((_) async {
        return _makeResult([_accepted(opId)]);
      });

      await dispatcher.flush();

      final row = await outboxRepo.byClientOpId(opId);
      expect(row!.status, OutboxStatus.confirmed);
      expect(row.serverEntityId, '100001');
      expect(row.serverNumber, 'POS-1-20260530-00001');
    });

    test('flips PosSale.synced=true for POS_SALE ops', () async {
      // Insert a PosSale domain row alongside the outbox op.
      String opId = '';
      await db.transaction(() async {
        opId = await outboxRepo.enqueueInTxn(
          db,
          opType: OutboxOpType.posSale,
          payload: {'total': '7000.0000'},
        );
        await db.into(db.posSales).insert(PosSalesCompanion.insert(
          clientOpId: opId,
          tillSessionId: 1,
          customerId: 1,
          total: 7000.0,
          saleAt: DateTime.now(),
          status: 'POSTED',
        ));
      });

      when(() => mockApi.push(any())).thenAnswer((_) async {
        return _makeResult([_accepted(opId)]);
      });

      await dispatcher.flush();

      final sales = await db.select(db.posSales).get();
      expect(sales[0].synced, isTrue);
    });
  });

  group('DUPLICATE verdict', () {
    test('treated identically to ACCEPTED — row CONFIRMED, no duplicate created', () async {
      String opId = '';
      await db.transaction(() async {
        opId = await outboxRepo.enqueueInTxn(
          db,
          opType: OutboxOpType.posSale,
          payload: {'total': '5000.0000'},
        );
      });

      when(() => mockApi.push(any())).thenAnswer((_) async {
        return _makeResult([_duplicate(opId)]);
      });

      await dispatcher.flush();

      final row = await outboxRepo.byClientOpId(opId);
      expect(row!.status, OutboxStatus.confirmed);
      // Only one row was ever in the DB
      final all = await db.select(db.outbox).get();
      expect(all.length, 1);
    });
  });

  // ---------------------------------------------------------------------------
  // REJECTED
  // ---------------------------------------------------------------------------

  group('REJECTED verdict', () {
    test('moves row to NEEDS_REVIEW and does not retry', () async {
      String opId = '';
      await db.transaction(() async {
        opId = await outboxRepo.enqueueInTxn(
          db,
          opType: OutboxOpType.posSale,
          payload: {'total': '5000.0000'},
        );
      });

      when(() => mockApi.push(any())).thenAnswer((_) async {
        return _makeResult([_rejected(opId)]);
      });

      await dispatcher.flush();

      final row = await outboxRepo.byClientOpId(opId);
      expect(row!.status, OutboxStatus.needsReview);

      // pendingOps should be empty — REJECTED not auto-retried
      expect(await outboxRepo.pendingCount(), 0);
    });

    test('sibling ops in batch are not blocked by a REJECTED op', () async {
      String rejectedId = '';
      String acceptedId = '';
      await db.transaction(() async {
        rejectedId = await outboxRepo.enqueueInTxn(
          db,
          opType: OutboxOpType.posSale,
          payload: {'total': '5000.0000'},
        );
        acceptedId = await outboxRepo.enqueueInTxn(
          db,
          opType: OutboxOpType.cashPickup,
          payload: {'amount': '200.0000'},
        );
      });

      when(() => mockApi.push(any())).thenAnswer((_) async {
        return _makeResult([
          _rejected(rejectedId),
          _accepted(acceptedId),
        ]);
      });

      await dispatcher.flush();

      expect((await outboxRepo.byClientOpId(rejectedId))!.status, OutboxStatus.needsReview);
      expect((await outboxRepo.byClientOpId(acceptedId))!.status, OutboxStatus.confirmed);
    });
  });

  // ---------------------------------------------------------------------------
  // DEFERRED
  // ---------------------------------------------------------------------------

  group('DEFERRED verdict', () {
    test('keeps row in DEFERRED status for retry next cycle', () async {
      String opId = '';
      await db.transaction(() async {
        opId = await outboxRepo.enqueueInTxn(
          db,
          opType: OutboxOpType.posSale,
          payload: {'total': '5000.0000'},
        );
      });

      when(() => mockApi.push(any())).thenAnswer((_) async {
        return _makeResult([_deferred(opId)]);
      });

      await dispatcher.flush();

      final row = await outboxRepo.byClientOpId(opId);
      expect(row!.status, OutboxStatus.deferred);

      // DEFERRED is still returned by pendingOps (will be retried next cycle)
      expect(await outboxRepo.pendingCount(), 1);
    });
  });

  // ---------------------------------------------------------------------------
  // Network error (HTTP 426)
  // ---------------------------------------------------------------------------

  group('CONTRACT_TOO_OLD (426)', () {
    test('halts the dispatcher on 426', () async {
      String opId = '';
      await db.transaction(() async {
        opId = await outboxRepo.enqueueInTxn(
          db,
          opType: OutboxOpType.posSale,
          payload: {'total': '5000.0000'},
        );
      });

      when(() => mockApi.push(any())).thenThrow(
        DioException(
          requestOptions: RequestOptions(path: '/api/v1/sync/push'),
          response: Response(
            requestOptions: RequestOptions(path: '/api/v1/sync/push'),
            statusCode: 426,
          ),
          type: DioExceptionType.badResponse,
        ),
      );

      // Should not throw
      await dispatcher.flush();

      // Op remains pending (dispatcher halted, not processed)
      final row = await outboxRepo.byClientOpId(opId);
      expect(row!.status, OutboxStatus.pending);
    });
  });
}
