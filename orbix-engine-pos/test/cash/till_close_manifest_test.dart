// Tests for till-close manifest total computation (US-POS-013/014 fix).
//
// Verifies that closeTillSession() correctly sums cash-pickup and petty-cash
// amounts from the outbox payload (previously hardcoded to 0).
//
// Coverage:
// - cashPickupTotal in manifest reflects actual pickup amounts
// - pettyCashTotal in manifest reflects actual petty-cash amounts
// - manifest totals = 0 when no movements exist (zero state)
// - mixed session: sale + pickup + petty all counted correctly

import 'package:drift/drift.dart' show Value;
import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:orbix_engine_pos/data/local/database.dart';
import 'package:orbix_engine_pos/data/sync/outbox_repository.dart';
import 'package:orbix_engine_pos/data/sync/sync_api_client.dart';
import 'package:orbix_engine_pos/data/sync/sync_models.dart';
import 'package:orbix_engine_pos/data/sync/sync_repository.dart';

class MockSyncApiClient extends Mock implements SyncApiClient {}
class FakeTillSessionCloseRequest extends Fake implements TillSessionCloseRequest {}

PosDatabase _makeDb() => PosDatabase.forTesting(NativeDatabase.memory());

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
  // Helper: build a minimal CLOSED response for the session
  // ---------------------------------------------------------------------------

  TillSessionCloseResult closedResult(String sessionOpId, List<String> allOpIds) =>
      TillSessionCloseResult(
        tillSessionUid: 'SESSION-UID-1',
        status: TillCloseStatus.closed,
        openingFloat: '100000.0000',
        expectedCash: '100000.0000',
        declaredCash: '100000.0000',
        variance: '0.0000',
        confirmedClientOpIds: allOpIds,
        missingClientOpIds: [],
        unexpectedClientOpIds: [],
      );

  group('till-close manifest totals', () {
    test('cashPickupTotal reflects actual pickup amounts (not hardcoded 0)', () async {
      String sessionOpId = '';
      String pickupOpId1 = '';
      String pickupOpId2 = '';

      await db.transaction(() async {
        sessionOpId = await outboxRepo.enqueueInTxn(db,
            opType: OutboxOpType.tillSessionOpen, payload: {});

        pickupOpId1 = await outboxRepo.enqueueInTxn(db,
            opType: OutboxOpType.cashPickup,
            payload: {
              'amount': '30000.0000',
              'authorisedBy': 0,
            },
            dependsOn: sessionOpId);

        pickupOpId2 = await outboxRepo.enqueueInTxn(db,
            opType: OutboxOpType.cashPickup,
            payload: {
              'amount': '20000.0000',
              'authorisedBy': 0,
            },
            dependsOn: sessionOpId);

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

      final allIds = [sessionOpId, pickupOpId1, pickupOpId2];
      TillSessionCloseRequest? capturedRequest;
      when(() => mockApi.closeSession(any())).thenAnswer((invocation) async {
        capturedRequest =
            invocation.positionalArguments.first as TillSessionCloseRequest;
        return closedResult(sessionOpId, allIds);
      });

      await syncRepo.closeTillSession(
        sessionClientOpId: sessionOpId,
        declaredCash: 100000.0,
      );

      expect(capturedRequest, isNotNull);
      // Pickup total must be 50000 (30000 + 20000), not 0
      expect(capturedRequest!.manifest.cashPickupTotal, '50000.0000');
      expect(capturedRequest!.manifest.cashPickupCount, 2);
    });

    test('pettyCashTotal reflects actual petty-cash amounts (not hardcoded 0)', () async {
      String sessionOpId = '';
      String pettyOpId = '';

      await db.transaction(() async {
        sessionOpId = await outboxRepo.enqueueInTxn(db,
            opType: OutboxOpType.tillSessionOpen, payload: {});

        pettyOpId = await outboxRepo.enqueueInTxn(db,
            opType: OutboxOpType.pettyCash,
            payload: {
              'amount': '7500.0000',
              'category': 'TRANSPORT',
              'authorisedBy': 0,
            },
            dependsOn: sessionOpId);

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

      final allIds = [sessionOpId, pettyOpId];
      TillSessionCloseRequest? capturedRequest;
      when(() => mockApi.closeSession(any())).thenAnswer((invocation) async {
        capturedRequest =
            invocation.positionalArguments.first as TillSessionCloseRequest;
        return closedResult(sessionOpId, allIds);
      });

      await syncRepo.closeTillSession(
        sessionClientOpId: sessionOpId,
        declaredCash: 100000.0,
      );

      expect(capturedRequest, isNotNull);
      expect(capturedRequest!.manifest.pettyCashTotal, '7500.0000');
      expect(capturedRequest!.manifest.pettyCashCount, 1);
    });

    test('manifest totals are 0 when no movements exist', () async {
      String sessionOpId = '';
      String saleOpId = '';

      await db.transaction(() async {
        sessionOpId = await outboxRepo.enqueueInTxn(db,
            opType: OutboxOpType.tillSessionOpen, payload: {});

        saleOpId = await outboxRepo.enqueueInTxn(db,
            opType: OutboxOpType.posSale,
            payload: {
              'total': '10000.0000',
              'tillSessionClientOpId': sessionOpId,
            },
            dependsOn: sessionOpId);

        await db.into(db.posSales).insert(PosSalesCompanion.insert(
          clientOpId: saleOpId,
          tillSessionId: 1,
          customerId: 1,
          total: 10000.0,
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

      TillSessionCloseRequest? capturedRequest;
      when(() => mockApi.closeSession(any())).thenAnswer((invocation) async {
        capturedRequest =
            invocation.positionalArguments.first as TillSessionCloseRequest;
        return closedResult(sessionOpId, [sessionOpId, saleOpId]);
      });

      await syncRepo.closeTillSession(
        sessionClientOpId: sessionOpId,
        declaredCash: 100000.0,
      );

      expect(capturedRequest, isNotNull);
      expect(capturedRequest!.manifest.cashPickupTotal, '0.0000');
      expect(capturedRequest!.manifest.cashPickupCount, 0);
      expect(capturedRequest!.manifest.pettyCashTotal, '0.0000');
      expect(capturedRequest!.manifest.pettyCashCount, 0);
    });

    test('mixed session: sale + pickup + petty all counted correctly', () async {
      String sessionOpId = '';
      String saleOpId = '';
      String pickupOpId = '';
      String pettyOpId = '';

      await db.transaction(() async {
        sessionOpId = await outboxRepo.enqueueInTxn(db,
            opType: OutboxOpType.tillSessionOpen, payload: {});

        saleOpId = await outboxRepo.enqueueInTxn(db,
            opType: OutboxOpType.posSale,
            payload: {
              'total': '50000.0000',
              'tillSessionClientOpId': sessionOpId,
            },
            dependsOn: sessionOpId);

        pickupOpId = await outboxRepo.enqueueInTxn(db,
            opType: OutboxOpType.cashPickup,
            payload: {'amount': '25000.0000', 'authorisedBy': 0},
            dependsOn: sessionOpId);

        pettyOpId = await outboxRepo.enqueueInTxn(db,
            opType: OutboxOpType.pettyCash,
            payload: {
              'amount': '3500.0000',
              'category': 'OFFICE',
              'authorisedBy': 0,
            },
            dependsOn: sessionOpId);

        await db.into(db.posSales).insert(PosSalesCompanion.insert(
          clientOpId: saleOpId,
          tillSessionId: 1,
          customerId: 1,
          total: 50000.0,
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

      final allIds = [sessionOpId, saleOpId, pickupOpId, pettyOpId];
      TillSessionCloseRequest? capturedRequest;
      when(() => mockApi.closeSession(any())).thenAnswer((invocation) async {
        capturedRequest =
            invocation.positionalArguments.first as TillSessionCloseRequest;
        return closedResult(sessionOpId, allIds);
      });

      await syncRepo.closeTillSession(
        sessionClientOpId: sessionOpId,
        declaredCash: 100000.0,
      );

      expect(capturedRequest, isNotNull);
      final m = capturedRequest!.manifest;
      expect(m.posSaleCount, 1);
      expect(m.posSaleTotal, '50000.0000');
      expect(m.cashPickupCount, 1);
      expect(m.cashPickupTotal, '25000.0000');
      expect(m.pettyCashCount, 1);
      expect(m.pettyCashTotal, '3500.0000');
    });
  });
}
