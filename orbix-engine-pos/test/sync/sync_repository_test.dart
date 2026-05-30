// Tests for SyncRepository — pull, cursor advance, paging, till-close.
// Uses an in-memory DB and a mock SyncApiClient — no live server required.
//
// Coverage:
// - pull advances the cursor and upserts catalog items
// - pull follows hasMore=true paging until hasMore=false
// - pull with resyncRequired=true falls back to bootstrap
// - pull with no cursor falls back to bootstrap
// - bootstrap clears the cursor and applies a full snapshot
// - Till-close CLOSED: confirms ops + flips synced on PosSales
// - Till-close RECONCILE_INCOMPLETE: returns result without confirming ops

import 'package:drift/drift.dart';
import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:orbix_engine_pos/data/local/database.dart';
import 'package:orbix_engine_pos/data/sync/outbox_repository.dart';
import 'package:orbix_engine_pos/data/sync/sync_api_client.dart';
import 'package:orbix_engine_pos/data/sync/sync_models.dart';
import 'package:orbix_engine_pos/data/sync/sync_repository.dart';

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

class MockSyncApiClient extends Mock implements SyncApiClient {}
class FakeTillSessionCloseRequest extends Fake implements TillSessionCloseRequest {}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

PosDatabase _makeDb() => PosDatabase.forTesting(NativeDatabase.memory());

SyncPullResult _pullResult({
  String nextCursor = 'CURSOR_2',
  bool hasMore = false,
  bool resyncRequired = false,
  List<Map<String, dynamic>> catalogUpserts = const [],
  List<String> catalogDeletes = const [],
}) =>
    SyncPullResult(
      serverTime: DateTime.utc(2026, 5, 30, 8, 14, 5),
      nextCursor: nextCursor,
      hasMore: hasMore,
      resyncRequired: resyncRequired,
      datasets: {
        'catalog': SyncDataset(upserts: catalogUpserts, deletes: catalogDeletes),
        'price': const SyncDataset(upserts: [], deletes: []),
        'customer': const SyncDataset(upserts: [], deletes: []),
        'balance': const SyncDataset(upserts: [], deletes: []),
      },
    );

void main() {
  setUpAll(() {
    registerFallbackValue(FakeTillSessionCloseRequest());
  });

  late PosDatabase db;
  late OutboxRepository outboxRepo;
  late MockSyncApiClient mockApi;
  late SyncRepository syncRepo;

  setUp(() {
    db = _makeDb();
    outboxRepo = OutboxRepository(db);
    mockApi = MockSyncApiClient();
    syncRepo = SyncRepository(
      db: db,
      apiClient: mockApi,
      outboxRepo: outboxRepo,
    );
  });

  tearDown(() => db.close());

  // ---------------------------------------------------------------------------
  // Pull — cursor management
  // ---------------------------------------------------------------------------

  group('pull', () {
    test('falls back to bootstrap when no cursor stored', () async {
      when(() => mockApi.bootstrap(datasets: any(named: 'datasets')))
          .thenAnswer((_) async => _pullResult(nextCursor: 'CURSOR_1'));

      await syncRepo.pull();

      // Cursor should now be stored
      final cursor = await (db.select(db.syncCursors)
            ..where((t) => t.dataset.equals('global')))
          .getSingleOrNull();
      expect(cursor?.token, 'CURSOR_1');
      verify(() => mockApi.bootstrap(datasets: any(named: 'datasets'))).called(1);
    });

    test('advances cursor after each pull page', () async {
      // Seed an existing cursor
      await db.into(db.syncCursors).insert(SyncCursorsCompanion.insert(
        dataset: 'global',
        token: 'CURSOR_1',
        updatedAt: DateTime.utc(2026, 5, 30, 8, 0, 0),
      ));

      when(() => mockApi.pull(
            cursor: any(named: 'cursor'),
            datasets: any(named: 'datasets'),
          )).thenAnswer((_) async => _pullResult(nextCursor: 'CURSOR_2'));

      await syncRepo.pull();

      final cursor = await (db.select(db.syncCursors)
            ..where((t) => t.dataset.equals('global')))
          .getSingleOrNull();
      expect(cursor?.token, 'CURSOR_2');
    });

    test('follows hasMore=true paging until hasMore=false', () async {
      await db.into(db.syncCursors).insert(SyncCursorsCompanion.insert(
        dataset: 'global',
        token: 'CURSOR_1',
        updatedAt: DateTime.utc(2026, 5, 30, 8, 0, 0),
      ));

      var callCount = 0;
      when(() => mockApi.pull(
            cursor: any(named: 'cursor'),
            datasets: any(named: 'datasets'),
          )).thenAnswer((_) async {
        callCount++;
        return _pullResult(
          nextCursor: 'CURSOR_${callCount + 1}',
          hasMore: callCount < 3, // pages 1 and 2 have more; page 3 is last
        );
      });

      await syncRepo.pull();

      expect(callCount, 3);
      final cursor = await (db.select(db.syncCursors)
            ..where((t) => t.dataset.equals('global')))
          .getSingleOrNull();
      expect(cursor?.token, 'CURSOR_4');
    });

    test('upserts catalog items from pull result', () async {
      await db.into(db.syncCursors).insert(SyncCursorsCompanion.insert(
        dataset: 'global',
        token: 'CURSOR_1',
        updatedAt: DateTime.now(),
      ));

      when(() => mockApi.pull(
            cursor: any(named: 'cursor'),
            datasets: any(named: 'datasets'),
          )).thenAnswer((_) async => _pullResult(
            nextCursor: 'CURSOR_2',
            catalogUpserts: [
              {
                'id': 101,
                'code': 'BR-001',
                'name': 'White bread 600g',
                'price': 2500.0,
                'vatGroup': 'STD',
                'itemGroupId': 1,
                'status': 'ACTIVE',
              }
            ],
          ));

      await syncRepo.pull();

      final items = await db.select(db.items).get();
      expect(items.length, 1);
      expect(items[0].code, 'BR-001');
      expect(items[0].price, 2500.0);
    });

    test('deletes archived items from catalog', () async {
      // Pre-seed an item
      await db.into(db.items).insert(ItemsCompanion.insert(
        id: const Value(101),
        code: 'BR-001',
        name: 'White bread',
        price: 2500.0,
        vatGroup: 'STD',
        itemGroupId: 1,
      ));
      await db.into(db.syncCursors).insert(SyncCursorsCompanion.insert(
        dataset: 'global',
        token: 'CURSOR_1',
        updatedAt: DateTime.now(),
      ));

      when(() => mockApi.pull(
            cursor: any(named: 'cursor'),
            datasets: any(named: 'datasets'),
          )).thenAnswer((_) async => _pullResult(
            nextCursor: 'CURSOR_2',
            catalogDeletes: ['101'],
          ));

      await syncRepo.pull();

      final items = await db.select(db.items).get();
      expect(items, isEmpty);
    });

    test('resyncRequired=true triggers bootstrap and clears cursor', () async {
      await db.into(db.syncCursors).insert(SyncCursorsCompanion.insert(
        dataset: 'global',
        token: 'CURSOR_STALE',
        updatedAt: DateTime.now(),
      ));

      when(() => mockApi.pull(
            cursor: any(named: 'cursor'),
            datasets: any(named: 'datasets'),
          )).thenAnswer((_) async => _pullResult(
            nextCursor: 'CURSOR_NEW',
            resyncRequired: true,
          ));

      when(() => mockApi.bootstrap(datasets: any(named: 'datasets')))
          .thenAnswer((_) async => _pullResult(nextCursor: 'CURSOR_BOOTSTRAP'));

      final didResync = await syncRepo.pull();
      expect(didResync, isTrue);
      verify(() => mockApi.bootstrap(datasets: any(named: 'datasets'))).called(1);
    });
  });

  // ---------------------------------------------------------------------------
  // Bootstrap
  // ---------------------------------------------------------------------------

  group('bootstrap', () {
    test('clears existing cursor and applies snapshot', () async {
      // Pre-seed a cursor
      await db.into(db.syncCursors).insert(SyncCursorsCompanion.insert(
        dataset: 'global',
        token: 'OLD_CURSOR',
        updatedAt: DateTime.now(),
      ));

      when(() => mockApi.bootstrap(datasets: any(named: 'datasets')))
          .thenAnswer((_) async => _pullResult(nextCursor: 'BOOTSTRAP_CURSOR_1'));

      await syncRepo.bootstrap();

      final cursor = await (db.select(db.syncCursors)
            ..where((t) => t.dataset.equals('global')))
          .getSingleOrNull();
      expect(cursor?.token, 'BOOTSTRAP_CURSOR_1');
    });

    test('follows hasMore paging during bootstrap', () async {
      var callCount = 0;
      when(() => mockApi.bootstrap(datasets: any(named: 'datasets')))
          .thenAnswer((_) async {
        callCount++;
        return _pullResult(
          nextCursor: 'CURSOR_$callCount',
          hasMore: callCount == 1, // only first call has more
        );
      });
      when(() => mockApi.pull(
            cursor: any(named: 'cursor'),
            datasets: any(named: 'datasets'),
          )).thenAnswer((_) async => _pullResult(nextCursor: 'CURSOR_FINAL'));

      await syncRepo.bootstrap();

      verify(() => mockApi.bootstrap(datasets: any(named: 'datasets'))).called(1);
      verify(() => mockApi.pull(
            cursor: any(named: 'cursor'),
            datasets: any(named: 'datasets'),
          )).called(1);
    });
  });

  // ---------------------------------------------------------------------------
  // Till-close reconciliation
  // ---------------------------------------------------------------------------

  group('closeTillSession', () {
    test('CLOSED: confirms ops and flips PosSales.synced', () async {
      String sessionOpId = '';
      String saleOpId = '';

      // Create domain rows + outbox ops
      await db.transaction(() async {
        sessionOpId = await outboxRepo.enqueueInTxn(
          db,
          opType: OutboxOpType.tillSessionOpen,
          payload: {},
        );
        saleOpId = await outboxRepo.enqueueInTxn(
          db,
          opType: OutboxOpType.posSale,
          payload: {'total': '5000.0000', 'tillSessionClientOpId': sessionOpId},
          dependsOn: sessionOpId,
        );
        await db.into(db.posSales).insert(PosSalesCompanion.insert(
          clientOpId: saleOpId,
          tillSessionId: 1,
          customerId: 1,
          total: 5000.0,
          saleAt: DateTime.now(),
          status: 'POSTED',
        ));
        await db.into(db.tillSessions).insert(TillSessionsCompanion.insert(
          clientOpId: Value(sessionOpId),
          tillId: 1,
          businessDate: '2026-05-30',
          openedBy: 1,
          openedAt: DateTime.now(),
          openingFloat: 100000.0,
          status: 'OPEN',
        ));
      });

      when(() => mockApi.closeSession(any())).thenAnswer((_) async =>
          TillSessionCloseResult(
            tillSessionUid: '01JA9Z...',
            status: TillCloseStatus.closed,
            openingFloat: '100000.0000',
            expectedCash: '105000.0000',
            declaredCash: '105000.0000',
            variance: '0.0000',
            confirmedClientOpIds: [sessionOpId, saleOpId],
            missingClientOpIds: [],
            unexpectedClientOpIds: [],
          ));

      final result = await syncRepo.closeTillSession(
        sessionClientOpId: sessionOpId,
        declaredCash: 105000.0,
      );

      expect(result.status, TillCloseStatus.closed);

      // Outbox rows should be confirmed
      expect(
        (await outboxRepo.byClientOpId(sessionOpId))!.status,
        OutboxStatus.confirmed,
      );
      expect(
        (await outboxRepo.byClientOpId(saleOpId))!.status,
        OutboxStatus.confirmed,
      );

      // PosSale should be synced
      final sales = await db.select(db.posSales).get();
      expect(sales[0].synced, isTrue);

      // TillSession should be CLOSED
      final sessions = await db.select(db.tillSessions).get();
      expect(sessions[0].status, 'CLOSED');
    });

    test('RECONCILE_INCOMPLETE: returns result without confirming ops', () async {
      String sessionOpId = '';
      String saleOpId = '';

      await db.transaction(() async {
        sessionOpId = await outboxRepo.enqueueInTxn(
          db,
          opType: OutboxOpType.tillSessionOpen,
          payload: {},
        );
        saleOpId = await outboxRepo.enqueueInTxn(
          db,
          opType: OutboxOpType.posSale,
          payload: {'total': '5000.0000', 'tillSessionClientOpId': sessionOpId},
          dependsOn: sessionOpId,
        );
      });

      when(() => mockApi.closeSession(any())).thenAnswer((_) async =>
          TillSessionCloseResult(
            tillSessionUid: '01JA9Z...',
            status: TillCloseStatus.reconcileIncomplete,
            openingFloat: '100000.0000',
            expectedCash: '105000.0000',
            declaredCash: '105000.0000',
            variance: '0.0000',
            confirmedClientOpIds: [sessionOpId],
            missingClientOpIds: [saleOpId],
            unexpectedClientOpIds: [],
          ));

      final result = await syncRepo.closeTillSession(
        sessionClientOpId: sessionOpId,
        declaredCash: 105000.0,
      );

      expect(result.status, TillCloseStatus.reconcileIncomplete);
      expect(result.missingClientOpIds, [saleOpId]);

      // Ops should NOT be marked confirmed (close blocked)
      expect(
        (await outboxRepo.byClientOpId(saleOpId))!.status,
        OutboxStatus.pending,
      );
    });
  });
}
