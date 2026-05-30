/// Dio interceptor: attaches JWT + branch header, handles 401 refresh→retry.
///
/// Because the shared Dio is configured with `validateStatus: (s) => s != null`
/// (all status codes treated as successful responses, not errors), 401 arrives
/// in [onResponse], not [onError]. The interceptor handles it there.
///
/// Behaviour (mirrors orbix-engine-web/src/app/core/auth/auth.interceptor.ts):
///   1. Every outgoing request gets `Authorization: Bearer <accessToken>`.
///   2. If a branch override is set, `X-Branch-Id: <branchId>` is added.
///   3. On 401 response (not an auth endpoint, not already a retry):
///        a. Call POST /api/v1/auth/refresh with the stored refresh token.
///        b. If successful, replace stored tokens atomically, retry the
///           original request exactly once with the new token.
///        c. If refresh fails, surface the 401 so the dispatcher / UI can
///           prompt re-login. Does NOT loop.
///   4. If there is no token (offline pre-login) the request is sent without
///      an Authorization header. Server 401s propagate normally; the outbox
///      keeps growing without crashing the dispatcher.
///
/// Thread safety: a single-flight latch prevents concurrent 401s from each
/// attempting a separate refresh. The second caller awaits the first.
library;

import 'dart:async';

import 'package:dio/dio.dart';
import 'package:logger/logger.dart';

import 'auth_repository.dart';
import 'auth_token_store.dart';

const _anonymousPaths = ['/api/v1/auth/login', '/api/v1/auth/refresh'];

bool _isAnonymous(RequestOptions opts) =>
    _anonymousPaths.any((p) => opts.path.contains(p));

/// Header injected on retried requests so the interceptor doesn't loop.
const _kRetryHeader = 'X-Orbix-Auth-Retry';

class AuthInterceptor extends Interceptor {
  AuthInterceptor({
    required Dio dio,
    required AuthTokenStore tokenStore,
    required AuthRepository authRepository,
    Logger? logger,
  })  : _dio = dio,
        _store = tokenStore,
        _auth = authRepository,
        _log = logger ?? Logger();

  final Dio _dio;
  final AuthTokenStore _store;
  final AuthRepository _auth;
  final Logger _log;

  /// In-flight refresh latch — a Future shared by concurrent 401 callers.
  Future<void>? _inFlightRefresh;

  // ---------------------------------------------------------------------------
  // 1. Attach auth headers on every outgoing request
  // ---------------------------------------------------------------------------

  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) {
    if (!_isAnonymous(options)) {
      final token = _store.accessToken;
      if (token != null) {
        options.headers['Authorization'] = 'Bearer $token';
      }

      final branchId = _store.read()?.effectiveBranchId;
      if (branchId != null) {
        options.headers['X-Branch-Id'] = branchId.toString();
      }
    }
    handler.next(options);
  }

  // ---------------------------------------------------------------------------
  // 2. Intercept 401 in onResponse (validateStatus allows all codes through)
  // ---------------------------------------------------------------------------

  @override
  void onResponse(
    Response<dynamic> response,
    ResponseInterceptorHandler handler,
  ) async {
    final isRetry =
        response.requestOptions.headers.containsKey(_kRetryHeader);

    if (response.statusCode != 401 ||
        _isAnonymous(response.requestOptions) ||
        isRetry) {
      handler.next(response);
      return;
    }

    _log.d('AuthInterceptor: 401 on ${response.requestOptions.path} — attempting refresh');

    try {
      await _ensureRefreshed();
    } on RefreshException catch (e) {
      _log.w('AuthInterceptor: refresh failed — re-login required: $e');
      handler.next(response); // propagate the original 401
      return;
    } catch (e, st) {
      _log.w('AuthInterceptor: unexpected refresh error', error: e, stackTrace: st);
      handler.next(response);
      return;
    }

    // Retry the original request with the fresh token.
    try {
      final retried = await _retryRequest(response.requestOptions);
      handler.resolve(retried);
    } on DioException catch (retryErr) {
      // Should not happen (validateStatus accepts all codes), but guard anyway.
      _log.w('AuthInterceptor: retry threw DioException: ${retryErr.message}');
      handler.next(response);
    }
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /// Single-flight refresh: if a refresh is already in progress, await it.
  /// If this is the first, run it and cache the Future for concurrent callers.
  /// The in-flight Future is cleared after the refresh settles regardless of
  /// success or failure, so subsequent 401s get a fresh attempt.
  Future<void> _ensureRefreshed() {
    if (_inFlightRefresh != null) return _inFlightRefresh!;
    _inFlightRefresh = _auth.refresh().whenComplete(() {
      _inFlightRefresh = null;
    });
    return _inFlightRefresh!;
  }

  Future<Response<dynamic>> _retryRequest(RequestOptions original) {
    final token = _store.accessToken;
    final newHeaders = Map<String, dynamic>.from(original.headers);
    if (token != null) newHeaders['Authorization'] = 'Bearer $token';
    // Stamp the retry header so we don't loop on a persistent 401.
    newHeaders[_kRetryHeader] = '1';

    return _dio.request<dynamic>(
      original.path,
      data: original.data,
      queryParameters: original.queryParameters,
      options: Options(
        method: original.method,
        headers: newHeaders,
        validateStatus: original.validateStatus,
        sendTimeout: original.sendTimeout,
        receiveTimeout: original.receiveTimeout,
      ),
    );
  }
}
