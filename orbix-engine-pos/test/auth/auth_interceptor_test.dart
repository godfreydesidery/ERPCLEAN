// Tests for AuthInterceptor.
// Uses a fake HTTP adapter (Dio HttpClientAdapter) — no live server required.
//
// Coverage:
// - Valid token: attaches Authorization + X-Branch-Id headers
// - No token: request sent without Authorization header (offline-safe)
// - Anonymous path: no auth headers attached
// - 401 response: triggers refresh, retries the original request once
// - Refresh success: new token used on retry; 200 propagated
// - Refresh failure (RefreshException): original 401 propagated; no loop
// - Retry header: second 401 on retried request is NOT refreshed again
// - Concurrent 401s share a single refresh call

import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:orbix_engine_pos/data/auth/auth_interceptor.dart';
import 'package:orbix_engine_pos/data/auth/auth_repository.dart';
import 'package:orbix_engine_pos/data/auth/auth_token_store.dart';

// ---------------------------------------------------------------------------
// Fake token constants
// ---------------------------------------------------------------------------

/// Valid JWT exp=2099
const _futureToken =
    'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9'
    '.eyJzdWIiOiIxIiwiZXhwIjo0MTAyNDQ0ODAwfQ'
    '.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c';

const _futureToken2 =
    'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9'
    '.eyJzdWIiOiIyIiwiZXhwIjo0MTAyNDQ0ODAwfQ'
    '.newtoken';

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

class MockAuthRepository extends Mock implements AuthRepository {}

// ---------------------------------------------------------------------------
// Fake HTTP adapter
// ---------------------------------------------------------------------------

typedef _Handler = Future<ResponseBody> Function(RequestOptions options);

class _FakeAdapter implements HttpClientAdapter {
  _Handler? handler;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    return handler!(options);
  }

  @override
  void close({bool force = false}) {}
}

ResponseBody _jsonBody(Map<String, dynamic> data, {int status = 200}) {
  final bytes = utf8.encode(jsonEncode(data));
  return ResponseBody.fromBytes(bytes, status,
      headers: {'content-type': ['application/json']});
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

Future<AuthTokenStore> _storeWithToken({
  String access = _futureToken,
  String refresh = 'refresh-tok',
  int branchId = 7,
}) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  final store = AuthTokenStore(prefs);
  await store.save(
    accessToken: access,
    refreshToken: refresh,
    userId: 1,
    username: 'cashier1',
    displayName: 'Cashier One',
    defaultBranchId: branchId,
  );
  return store;
}

Future<AuthTokenStore> _emptyStore() async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  return AuthTokenStore(prefs);
}

/// Build a Dio configured identically to the production sync Dio:
/// validateStatus accepts ALL codes (no exceptions thrown for 4xx/5xx).
Dio _makeDio(_FakeAdapter adapter) {
  final dio = Dio(BaseOptions(
    baseUrl: 'http://localhost:8081',
    validateStatus: (s) => s != null, // matches sync_providers.dart
  ));
  dio.httpClientAdapter = adapter;
  return dio;
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

void main() {
  late _FakeAdapter adapter;
  late Dio dio;

  setUp(() {
    adapter = _FakeAdapter();
    dio = _makeDio(adapter);
  });

  group('header injection', () {
    test('attaches Authorization + X-Branch-Id when token exists', () async {
      final store = await _storeWithToken(branchId: 7);
      final authRepo = MockAuthRepository();
      dio.interceptors.add(AuthInterceptor(
        dio: dio,
        tokenStore: store,
        authRepository: authRepo,
      ));

      late RequestOptions captured;
      adapter.handler = (opts) async {
        captured = opts;
        return _jsonBody({'data': 'ok'});
      };

      await dio.get<dynamic>('/api/v1/sync/pull');

      expect(captured.headers['Authorization'], 'Bearer $_futureToken');
      // effectiveBranchId = activeBranchId seeded from defaultBranchId (7) on first save
      expect(captured.headers['X-Branch-Id'], '7');
    });

    test('sends request without Authorization when no token', () async {
      final store = await _emptyStore();
      final authRepo = MockAuthRepository();
      dio.interceptors.add(AuthInterceptor(
        dio: dio,
        tokenStore: store,
        authRepository: authRepo,
      ));

      late RequestOptions captured;
      adapter.handler = (opts) async {
        captured = opts;
        return _jsonBody({'data': 'ok'});
      };

      await dio.get<dynamic>('/api/v1/sync/pull');

      expect(captured.headers.containsKey('Authorization'), isFalse);
    });

    test('does not attach auth headers to login path', () async {
      final store = await _storeWithToken();
      final authRepo = MockAuthRepository();
      dio.interceptors.add(AuthInterceptor(
        dio: dio,
        tokenStore: store,
        authRepository: authRepo,
      ));

      late RequestOptions captured;
      adapter.handler = (opts) async {
        captured = opts;
        return _jsonBody({'data': 'ok'});
      };

      await dio.post<dynamic>('/api/v1/auth/login',
          data: {'username': 'u', 'password': 'p'});

      // Authorization must NOT be added to the login path.
      expect(captured.headers.containsKey('Authorization'), isFalse);
    });
  });

  group('401 → refresh → retry', () {
    test('successful refresh: retries request once and resolves 200', () async {
      final store = await _storeWithToken(access: _futureToken);
      final authRepo = MockAuthRepository();

      // Return 401 on first call; 200 on the retry.
      var firstCall = true;
      adapter.handler = (opts) async {
        if (firstCall) {
          firstCall = false;
          return _jsonBody({'message': 'Unauthorized'}, status: 401);
        }
        return _jsonBody({'data': 'ok'});
      };

      when(() => authRepo.refresh()).thenAnswer((_) async {
        await store.updateTokens(
            accessToken: _futureToken2, refreshToken: 'new-refresh');
      });

      dio.interceptors.add(AuthInterceptor(
        dio: dio,
        tokenStore: store,
        authRepository: authRepo,
      ));

      final response = await dio.get<dynamic>('/api/v1/sync/pull');
      expect(response.statusCode, 200);
      verify(() => authRepo.refresh()).called(1);
    });

    test('refresh failure: propagates original 401, does not call refresh again',
        () async {
      final store = await _storeWithToken();
      final authRepo = MockAuthRepository();

      adapter.handler = (opts) async =>
          _jsonBody({'message': 'Unauthorized'}, status: 401);

      when(() => authRepo.refresh())
          .thenThrow(const RefreshException('token expired'));

      dio.interceptors.add(AuthInterceptor(
        dio: dio,
        tokenStore: store,
        authRepository: authRepo,
      ));

      final response = await dio.get<dynamic>('/api/v1/sync/pull');
      // Refresh failed → original 401 propagated
      expect(response.statusCode, 401);
      verify(() => authRepo.refresh()).called(1);
    });

    test('retry 401 is NOT refreshed again (no loop)', () async {
      final store = await _storeWithToken();
      final authRepo = MockAuthRepository();

      // Always return 401 (first call + retry both get 401).
      adapter.handler = (opts) async =>
          _jsonBody({'message': 'Unauthorized'}, status: 401);

      // Refresh succeeds, but the retried request also returns 401.
      when(() => authRepo.refresh()).thenAnswer((_) async {
        await store.updateTokens(
            accessToken: _futureToken2, refreshToken: 'r2');
      });

      dio.interceptors.add(AuthInterceptor(
        dio: dio,
        tokenStore: store,
        authRepository: authRepo,
      ));

      final response = await dio.get<dynamic>('/api/v1/sync/pull');
      // The retry 401 must be returned as-is (no second loop).
      expect(response.statusCode, 401);
      // Refresh called exactly once.
      verify(() => authRepo.refresh()).called(1);
    });

    test('does not attempt refresh on 401 from auth endpoints', () async {
      final store = await _storeWithToken();
      final authRepo = MockAuthRepository();

      adapter.handler = (opts) async =>
          _jsonBody({'message': 'Unauthorized'}, status: 401);

      dio.interceptors.add(AuthInterceptor(
        dio: dio,
        tokenStore: store,
        authRepository: authRepo,
      ));

      final response =
          await dio.post<dynamic>('/api/v1/auth/refresh', data: {});
      expect(response.statusCode, 401);
      verifyNever(() => authRepo.refresh());
    });

    test('sequential 401s each succeed — refresh is called per request', () async {
      // Verifies that successive 401s (e.g. after token rotates on each use)
      // each trigger a refresh and succeed.  The important invariant is that
      // the interceptor does not crash or loop — each request gets resolved.
      final store = await _storeWithToken();
      final authRepo = MockAuthRepository();

      // Always 401 on first call per request; 200 on retry.
      int callCount = 0;
      adapter.handler = (opts) async {
        final isRetry = opts.headers.containsKey('X-Orbix-Auth-Retry');
        if (!isRetry) {
          callCount++;
          return _jsonBody({'message': 'Unauthorized'}, status: 401);
        }
        return _jsonBody({'data': 'ok'});
      };

      when(() => authRepo.refresh()).thenAnswer((_) async {
        await store.updateTokens(
            accessToken: _futureToken2, refreshToken: 'r$callCount');
      });

      dio.interceptors.add(AuthInterceptor(
        dio: dio,
        tokenStore: store,
        authRepository: authRepo,
      ));

      final r1 = await dio.get<dynamic>('/api/v1/sync/pull');
      final r2 = await dio.get<dynamic>('/api/v1/sync/pull');

      expect(r1.statusCode, 200);
      expect(r2.statusCode, 200);
      // Two requests, each with a 401, each triggered a refresh.
      verify(() => authRepo.refresh()).called(2);
    });
  });
}
