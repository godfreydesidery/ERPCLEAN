/// Riverpod providers for the sync spine.
///
/// Mirrors the provider pattern used elsewhere in the POS app.
/// The dispatcher is started when the database provider is first read
/// (via the appStartupProvider) so it begins draining on boot.
library;

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../auth/auth_interceptor.dart';
import '../auth/auth_providers.dart';
import '../core_providers.dart';
import '../local/database.dart';
import 'outbox_dispatcher.dart';
import 'outbox_repository.dart';
import 'sync_api_client.dart';
import 'sync_repository.dart';

// ---------------------------------------------------------------------------
// Intercepted Dio — used by the sync API client and all server calls.
// Attaches Authorization + X-Branch-Id; handles 401 → refresh → retry.
// ---------------------------------------------------------------------------

final dioProvider = Provider<Dio>((ref) {
  final baseUrl = ref.watch(apiBaseUrlProvider);
  final tokenStore = ref.watch(authTokenStoreProvider);
  final authRepo = ref.watch(authRepositoryProvider);
  final logger = ref.watch(loggerProvider);

  final dio = Dio(BaseOptions(
    baseUrl: baseUrl,
    connectTimeout: const Duration(seconds: 10),
    receiveTimeout: const Duration(seconds: 30),
    // Don't throw on 4xx/5xx — sync reads status codes directly.
    validateStatus: (status) => status != null,
  ));

  dio.interceptors.add(AuthInterceptor(
    dio: dio,
    tokenStore: tokenStore,
    authRepository: authRepo,
    logger: logger,
  ));

  return dio;
});

// ---------------------------------------------------------------------------
// Database
// ---------------------------------------------------------------------------

final posDatabaseProvider = Provider<PosDatabase>((ref) {
  final db = PosDatabase();
  ref.onDispose(db.close);
  return db;
});

// ---------------------------------------------------------------------------
// Sync layer
// ---------------------------------------------------------------------------

final syncApiClientProvider = Provider<SyncApiClient>((ref) {
  return DioSyncApiClient(
    dio: ref.watch(dioProvider),
    logger: ref.watch(loggerProvider),
  );
});

final outboxRepositoryProvider = Provider<OutboxRepository>((ref) {
  return OutboxRepository(
    ref.watch(posDatabaseProvider),
    logger: ref.watch(loggerProvider),
  );
});

final syncRepositoryProvider = Provider<SyncRepository>((ref) {
  return SyncRepository(
    db: ref.watch(posDatabaseProvider),
    apiClient: ref.watch(syncApiClientProvider),
    outboxRepo: ref.watch(outboxRepositoryProvider),
    logger: ref.watch(loggerProvider),
  );
});

final outboxDispatcherProvider = Provider<OutboxDispatcher>((ref) {
  final dispatcher = OutboxDispatcher(
    outboxRepo: ref.watch(outboxRepositoryProvider),
    apiClient: ref.watch(syncApiClientProvider),
    syncRepo: ref.watch(syncRepositoryProvider),
    deviceId: ref.watch(deviceIdProvider),
    logger: ref.watch(loggerProvider),
  );
  ref.onDispose(dispatcher.stop);
  return dispatcher;
});

// ---------------------------------------------------------------------------
// Derived state — UI reads these
// ---------------------------------------------------------------------------

/// Count of ops awaiting sync (PENDING + DEFERRED).
/// Shows in the cart header / status bar so cashiers know the queue depth.
final pendingOutboxCountProvider = FutureProvider<int>((ref) async {
  final repo = ref.watch(outboxRepositoryProvider);
  return repo.pendingCount();
});

/// Stream of NEEDS_REVIEW ops — cashier "needs attention" list.
final needsReviewProvider = StreamProvider<List<OutboxData>>((ref) {
  final repo = ref.watch(outboxRepositoryProvider);
  return repo.watchNeedsReview();
});

// ---------------------------------------------------------------------------
// App startup: initialise seq counter + start dispatcher
// ---------------------------------------------------------------------------

/// Call ref.read(appStartupProvider) once at app boot (e.g. from main() or
/// the root widget's initState) to kick off the sync loop.
final appStartupProvider = Provider<void>((ref) {
  final outbox = ref.watch(outboxRepositoryProvider);
  final dispatcher = ref.watch(outboxDispatcherProvider);
  // initialise seq from DB then start dispatcher
  outbox.initSeq().then((_) => dispatcher.start());
});
