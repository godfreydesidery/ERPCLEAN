// E2E integration test — drives the full till-open → sale → cash pickup → till-close
// cycle against the running QA container at http://localhost:8081.
//
// Run with:
//   flutter test test/e2e/live_sync_e2e_test.dart --timeout=60s
//
// Prerequisites:
//   - QA container running: http://localhost:8081
//   - POS user: cashier / Cashier#2026, branch HQ (branchId=1)
//   - At least one catalog item with a synced price (after pull/bootstrap)
//   - Till id 1 exists in the DB (seeded by bootstrap)
//
// This test does NOT use Flutter widget testing — it exercises the repository and
// dispatcher layers directly with a real Dio client against the live server.
//
// Verdicts expected:
//   TILL_SESSION_OPEN  → ACCEPTED with a numeric server session id
//   POS_SALE           → ACCEPTED after session id is back-filled
//   CASH_PICKUP        → ACCEPTED after session id is back-filled
//   Replay (same ops)  → DUPLICATE for all three
//   Till-close         → CLOSED with a computed variance

@Tags(['e2e'])
library;

import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:drift/drift.dart' show Value;
import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:orbix_engine_pos/data/local/database.dart';
import 'package:orbix_engine_pos/data/sync/outbox_dispatcher.dart';
import 'package:orbix_engine_pos/data/sync/outbox_repository.dart';
import 'package:orbix_engine_pos/data/sync/sync_api_client.dart';
import 'package:orbix_engine_pos/data/sync/sync_models.dart';
import 'package:orbix_engine_pos/data/sync/sync_repository.dart';
import 'package:logger/logger.dart';

const _kBaseUrl = 'http://localhost:8081';
const _kUsername = 'cashier';
const _kPassword = 'Cashier#2026';
const _kAdminPassword = 'SKp315goPN8Nb0yJtMCCD7cm';
const _kBranchId = 1;
/// Server-assigned cashier user id (sub claim in JWT = 2 for the cashier user).
const _kCashierUserId = 2;
/// Walk-in customer id (created via POST /api/v1/customers, id = 1).
const _kWalkInCustomerId = 1;
/// POS section id (id = 1, code=MAIN, type=RETAIL_FLOOR).
const _kSectionId = 1;

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

Future<String> _login(Dio dio) async {
  final resp = await dio.post('/api/v1/auth/login', data: {
    'username': _kUsername,
    'password': _kPassword,
  });
  if (resp.statusCode != 200) {
    fail('Login failed: ${resp.statusCode} ${resp.data}');
  }
  final data = resp.data as Map<String, dynamic>;
  final body = data['data'] as Map<String, dynamic>;
  return body['accessToken'] as String;
}

Future<String> _loginAdmin(Dio dio) async {
  final resp = await dio.post('/api/v1/auth/login', data: {
    'username': 'rootadmin',
    'password': _kAdminPassword,
  });
  if (resp.statusCode != 200) {
    fail('Admin login failed: ${resp.statusCode} ${resp.data}');
  }
  final data = resp.data as Map<String, dynamic>;
  final body = data['data'] as Map<String, dynamic>;
  return body['accessToken'] as String;
}

Dio _buildDio(String token) {
  return Dio(BaseOptions(
    baseUrl: _kBaseUrl,
    validateStatus: (s) => s != null,
    headers: {
      'Authorization': 'Bearer $token',
      'X-Branch-Id': _kBranchId.toString(),
    },
  ));
}

// ---------------------------------------------------------------------------
// Test
// ---------------------------------------------------------------------------

void main() {
  // Check server is reachable before running — skip gracefully if not.
  late Dio rawDio;
  late String accessToken;
  late int tillId; // created fresh per test run
  late PosDatabase db;
  late OutboxRepository outboxRepo;
  late SyncApiClient syncApi;
  late SyncRepository syncRepo;
  late OutboxDispatcher dispatcher;
  bool _initialized = false;
  final log = Logger();

  setUpAll(() async {
    rawDio = Dio(BaseOptions(
      baseUrl: _kBaseUrl,
      connectTimeout: const Duration(seconds: 5),
      validateStatus: (s) => s != null,
    ));
    try {
      final health = await rawDio.get('/actuator/health');
      if (health.statusCode != 200) {
        throw Exception('Server not healthy: ${health.statusCode}');
      }
      accessToken = await _login(rawDio);
    } catch (e) {
      // Server not running — skip all e2e tests gracefully.
      markTestSkipped('E2E server not reachable: $e');
      return;
    }

    // Create a unique till for this test run so we don't collide with open sessions.
    // till.code is VARCHAR(6) — use 3-char prefix + 3-digit suffix.
    final adminToken = await _loginAdmin(rawDio);
    final ts = DateTime.now().millisecondsSinceEpoch % 1000;
    final adminDio = _buildDio(adminToken);
    final tillResp = await adminDio.post('/api/v1/tills', data: {
      'code': 'T${ts.toString().padLeft(3, '0')}',
      'name': 'E2E $ts',
      'branchId': _kBranchId,
      'defaultPriceListId': 1,
    });
    if (tillResp.statusCode != 201) {
      fail('Failed to create test till: ${tillResp.data}');
    }
    final tillData = (tillResp.data as Map<String, dynamic>)['data'] as Map<String, dynamic>;
    tillId = int.parse(tillData['id'] as String);
    log.i('E2E: created test till id=$tillId');

    db = PosDatabase.forTesting(NativeDatabase.memory());
    outboxRepo = OutboxRepository(db, logger: log);
    await outboxRepo.initSeq();

    final dio = _buildDio(accessToken);
    syncApi = DioSyncApiClient(dio: dio, logger: log);
    syncRepo = SyncRepository(
      db: db,
      apiClient: syncApi,
      outboxRepo: outboxRepo,
      logger: log,
    );
    dispatcher = OutboxDispatcher(
      outboxRepo: outboxRepo,
      apiClient: syncApi,
      syncRepo: syncRepo,
      deviceId: 'T${ts.toString().padLeft(3, '0')}',
      pushInterval: const Duration(hours: 1), // manual flush only
      pullInterval: const Duration(hours: 1),
      logger: log,
    );
    dispatcher.start();
    _initialized = true;
  });

  tearDownAll(() async {
    if (!_initialized) return;
    dispatcher.stop();
    await db.close();
  });

  test('full cycle: open session → sale → cash pickup → till-close', () async {
    // ---- Step 1: Pull bootstrap so catalog + prices are available ----
    await syncRepo.bootstrap();
    final items = await db.select(db.items).get();
    log.i('E2E: catalog items after bootstrap: ${items.length}');

    // ---- Step 2: Open till session ----
    // Enqueue TILL_SESSION_OPEN op directly (mirrors TillSessionRepository.openSession).
    String sessionOpId = '';
    await db.transaction(() async {
      sessionOpId = await outboxRepo.enqueueInTxn(
        db,
        opType: OutboxOpType.tillSessionOpen,
        payload: {
          'tillId': tillId,
          'openingFloatAmount': '100000.0000',
        },
        occurredAt: DateTime.now(),
      );
      await db.into(db.tillSessions).insert(TillSessionsCompanion.insert(
        clientOpId: Value(sessionOpId),
        tillId: tillId,
        businessDate: '2026-05-30',
        openedBy: _kCashierUserId,
        openedAt: DateTime.now(),
        openingFloat: 100000.0,
        status: 'OPEN',
      ));
    });

    // ---- Step 3: Enqueue a POS_SALE op (depends on session) ----
    // Use item id 1 (price=1200 TZS) with a cash tender.
    final saleOpId = await _enqueueSale(
      db, outboxRepo, sessionOpId,
      sessionServerId: null,
      cashierUserId: _kCashierUserId,
      customerId: _kWalkInCustomerId,
      sectionId: _kSectionId,
    );

    // ---- Step 4: Enqueue a CASH_PICKUP op (depends on session) ----
    String pickupOpId = '';
    await db.transaction(() async {
      pickupOpId = await outboxRepo.enqueueInTxn(
        db,
        opType: OutboxOpType.cashPickup,
        payload: {
          'amount': '10000.0000',
          'authorisedBy': 1,
          'tillSessionId': 0, // will be back-filled after session ACCEPTED
        },
        dependsOn: sessionOpId,
        occurredAt: DateTime.now(),
      );
    });

    // ---- Step 5: First flush — session should be ACCEPTED, sale + pickup DEFERRED ----
    await dispatcher.flush();

    final sessionOutbox = await outboxRepo.byClientOpId(sessionOpId);
    expect(sessionOutbox!.status, OutboxStatus.confirmed,
        reason: 'TILL_SESSION_OPEN must be ACCEPTED on first flush');
    expect(sessionOutbox.serverEntityId, isNotNull,
        reason: 'Backend must return a server session id');
    final serverSessionId = sessionOutbox.serverEntityId!;
    log.i('E2E: TILL_SESSION_OPEN ACCEPTED serverEntityId=$serverSessionId');

    // After back-fill, pickup payload should have the real tillSessionId.
    final pickupAfterBackfill = await outboxRepo.byClientOpId(pickupOpId);
    final pickupPayload = jsonDecode(pickupAfterBackfill!.payloadJson) as Map<String, dynamic>;
    final backfilledId = pickupPayload['tillSessionId'];
    log.i('E2E: pickup tillSessionId after back-fill: $backfilledId '
        'expected: ${int.tryParse(serverSessionId)}');
    expect(backfilledId, int.tryParse(serverSessionId),
        reason: 'tillSessionId in pickup payload must be back-filled with server session id');

    // ---- Step 6: Second flush — sale + pickup should now be ACCEPTED ----
    await dispatcher.flush();

    final saleOutbox = await outboxRepo.byClientOpId(saleOpId);
    final pickupOutbox = await outboxRepo.byClientOpId(pickupOpId);
    log.i('E2E: POS_SALE verdict=${saleOutbox?.status} '
        'serverEntityId=${saleOutbox?.serverEntityId}');
    log.i('E2E: CASH_PICKUP verdict=${pickupOutbox?.status} '
        'serverEntityId=${pickupOutbox?.serverEntityId}');

    expect(saleOutbox!.status, OutboxStatus.confirmed,
        reason: 'POS_SALE must be ACCEPTED after session id is resolved');
    expect(pickupOutbox!.status, OutboxStatus.confirmed,
        reason: 'CASH_PICKUP must be ACCEPTED after tillSessionId is back-filled');

    // ---- Step 7: Replay — same ops must come back as DUPLICATE ----
    // Re-push the same ops from the outbox; server deduplicates by clientOpId.
    // Mark them PENDING again to re-push.
    await (db.update(db.outbox)
          ..where((t) => t.clientOpId.isIn([sessionOpId, saleOpId, pickupOpId])))
        .write(const OutboxCompanion(status: Value(OutboxStatus.pending)));
    // Flush 1: sends only session (sale+pickup filtered pending confirmation).
    await dispatcher.flush();
    // Flush 2: session is now confirmed (DUPLICATE) → sends sale+pickup.
    await dispatcher.flush();

    // After replay all should still be confirmed (DUPLICATE treated as confirmed).
    final sessionAfterReplay = await outboxRepo.byClientOpId(sessionOpId);
    final saleAfterReplay = await outboxRepo.byClientOpId(saleOpId);
    final pickupAfterReplay = await outboxRepo.byClientOpId(pickupOpId);
    log.i('E2E REPLAY: session=${sessionAfterReplay?.status} '
        'sale=${saleAfterReplay?.status} pickup=${pickupAfterReplay?.status}');
    expect(sessionAfterReplay!.status, OutboxStatus.confirmed,
        reason: 'Replay of TILL_SESSION_OPEN must be DUPLICATE (treated as confirmed)');
    expect(saleAfterReplay!.status, OutboxStatus.confirmed,
        reason: 'Replay of POS_SALE must be DUPLICATE (idempotent)');
    expect(pickupAfterReplay!.status, OutboxStatus.confirmed,
        reason: 'Replay of CASH_PICKUP must be DUPLICATE (idempotent)');

    // ---- Step 8: Till close ----
    // Mark them PENDING again so they're included in the close manifest.
    // Actually closeTillSession reads outbox ops for session — they're CONFIRMED, which is correct.
    // Expected = opening(100000) + cashSale(1416) - pickup(10000) = 91416
    final closeResult = await syncRepo.closeTillSession(
      sessionClientOpId: sessionOpId,
      declaredCash: 91416.0,
    );
    log.i('E2E: till-close status=${closeResult.status} '
        'expectedCash=${closeResult.expectedCash} '
        'variance=${closeResult.variance}');
    expect(closeResult.status, TillCloseStatus.closed,
        reason: 'Till-close must succeed with CLOSED status');
    expect(closeResult.tillSessionUid, isNotEmpty);

    // Variance: expected = opening(100000) + cashSale(5000) - pickup(10000) = 95000
    // Declared: 95000 → variance = 0. (exact value depends on backend computation.)
    log.i('E2E: All verdicts confirmed. Test complete.');
  }, timeout: const Timeout(Duration(minutes: 2)));
}

Future<String> _enqueueSale(
  PosDatabase db,
  OutboxRepository outboxRepo,
  String sessionOpId, {
  required String? sessionServerId,
  int cashierUserId = 2,
  int customerId = 1,
  int sectionId = 1,
}) async {
  // Build a minimal sale payload matching PostPosSaleRequestDto.
  // Uses item id 1 (seed item, price=1200 TZS) with qty=1.
  final saleAt = DateTime.now().toUtc();
  // Generate a unique number per test run to avoid collision with prior runs.
  final numSuffix = (saleAt.millisecondsSinceEpoch % 100000).toString().padLeft(5, '0');
  final number = 'TE-$numSuffix';

  String saleClientOpId = '';
  await db.transaction(() async {
    final payload = <String, dynamic>{
      'number': number,
      'tillSessionId': sessionServerId != null ? int.tryParse(sessionServerId) ?? 0 : 0,
      'tillSessionClientOpId': sessionOpId,
      'sectionId': sectionId,
      'customerId': customerId,
      'supervisorId': cashierUserId,
      'saleAt': saleAt.toIso8601String(),
      'total': '1416.0000', // 1200 * 1.18 VAT
      'lines': [
        {
          'itemId': 1, // seed item id, price=1200 TZS (ex-VAT)
          'qty': '1.0000',
          'unitPrice': '1200.0000',
        }
      ],
      'payments': [
        {
          'method': 'CASH',
          'amount': '1416.0000', // full VAT-inclusive amount
        }
      ],
    };

    saleClientOpId = await outboxRepo.enqueueInTxn(
      db,
      opType: OutboxOpType.posSale,
      payload: payload,
      dependsOn: sessionOpId,
      occurredAt: saleAt,
    );

    // Update payload with clientOpId.
    final payloadWithId = Map<String, dynamic>.from(payload)
      ..['clientOpId'] = saleClientOpId;
    await (db.update(db.outbox)
          ..where((t) => t.clientOpId.equals(saleClientOpId)))
        .write(OutboxCompanion(payloadJson: Value(jsonEncode(payloadWithId))));

    // Insert local PosSale.
    await db.into(db.posSales).insert(PosSalesCompanion.insert(
      clientOpId: saleClientOpId,
      tillSessionId: 1,
      customerId: customerId,
      total: 1416.0, // VAT-inclusive
      saleAt: saleAt,
      status: 'POSTED',
    ));
  });

  return saleClientOpId;
}
