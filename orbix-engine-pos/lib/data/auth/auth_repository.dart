/// Calls the backend auth endpoints and persists the resulting tokens.
///
/// Endpoints:
///   POST /api/v1/auth/login   — body: {username, password}
///   POST /api/v1/auth/refresh — body: {refreshToken}
///
/// Both return ApiResponse<LoginResponseDto> which the server wraps in:
///   {status, statusCode, responseCode, message, errors, data}
///
/// Tokens: access=PT15M (15 min), refresh=P30D (30 days, single-use/rotated).
library;

import 'package:dio/dio.dart';
import 'package:logger/logger.dart';

import 'auth_token_store.dart';

/// Thrown when the backend returns 401 or 423 (wrong password / locked).
class AuthException implements Exception {
  const AuthException(this.message, {this.statusCode});
  final String message;
  final int? statusCode;

  @override
  String toString() => 'AuthException($statusCode): $message';
}

/// Thrown when the stored refresh token is invalid or expired.
class RefreshException implements Exception {
  const RefreshException(this.message);
  final String message;

  @override
  String toString() => 'RefreshException: $message';
}

class AuthRepository {
  AuthRepository({
    required Dio dio,
    required AuthTokenStore tokenStore,
    Logger? logger,
  })  : _dio = dio,
        _store = tokenStore,
        _log = logger ?? Logger();

  final Dio _dio;
  final AuthTokenStore _store;
  final Logger _log;

  // ---------------------------------------------------------------------------
  // Login
  // ---------------------------------------------------------------------------

  /// Authenticates with the server and persists the session.
  ///
  /// Throws [AuthException] on bad credentials or account lock.
  /// Does NOT throw on network error — callers that want offline fallback should
  /// catch [DioException] separately.
  Future<void> login({
    required String username,
    required String password,
  }) async {
    _log.d('AuthRepository.login username=$username');

    final Response<dynamic> response;
    try {
      response = await _dio.post<dynamic>(
        '/api/v1/auth/login',
        data: {'username': username, 'password': password},
        options: Options(
          headers: {'Content-Type': 'application/json'},
          // Don't throw on 4xx — we read the status ourselves.
          validateStatus: (s) => s != null,
          sendTimeout: const Duration(seconds: 10),
          receiveTimeout: const Duration(seconds: 10),
        ),
      );
    } on DioException {
      rethrow; // network error — caller decides offline fallback
    }

    final status = response.statusCode ?? 0;
    if (status == 401 || status == 423) {
      final msg = _extractMessage(response) ?? 'Invalid credentials';
      throw AuthException(msg, statusCode: status);
    }
    if (status < 200 || status >= 300) {
      final msg = _extractMessage(response) ?? 'Login failed ($status)';
      throw AuthException(msg, statusCode: status);
    }

    final data = _unwrap(response);
    await _store.save(
      accessToken: data['accessToken'] as String,
      refreshToken: data['refreshToken'] as String,
      userId: _parseInt(data['user']['id']),
      username: data['user']['username'] as String,
      displayName: data['user']['displayName'] as String? ?? username,
      defaultCompanyId: _parseIntNullable(data['user']['defaultCompanyId']),
      defaultBranchId: _parseIntNullable(data['user']['defaultBranchId']),
    );
    _log.i('AuthRepository.login success username=$username');
  }

  // ---------------------------------------------------------------------------
  // Refresh
  // ---------------------------------------------------------------------------

  /// Exchanges the stored refresh token for a new access+refresh pair.
  ///
  /// Tokens are rotated atomically — the old refresh token is invalidated by the
  /// server on use (single-use semantics from orbix.jwt.refresh-ttl=P30D).
  ///
  /// Throws [RefreshException] if the refresh token is absent, invalid, or expired.
  Future<void> refresh() async {
    final storedRefresh = _store.refreshToken;
    if (storedRefresh == null) {
      throw const RefreshException('No refresh token stored');
    }

    _log.d('AuthRepository.refresh');

    final Response<dynamic> response;
    try {
      response = await _dio.post<dynamic>(
        '/api/v1/auth/refresh',
        data: {'refreshToken': storedRefresh},
        options: Options(
          headers: {'Content-Type': 'application/json'},
          validateStatus: (s) => s != null,
          sendTimeout: const Duration(seconds: 10),
          receiveTimeout: const Duration(seconds: 10),
        ),
      );
    } on DioException catch (e) {
      throw RefreshException('Network error during refresh: ${e.message}');
    }

    final status = response.statusCode ?? 0;
    if (status == 401) {
      throw const RefreshException('Refresh token invalid or expired');
    }
    if (status < 200 || status >= 300) {
      throw RefreshException('Refresh failed ($status)');
    }

    final data = _unwrap(response);
    await _store.updateTokens(
      accessToken: data['accessToken'] as String,
      refreshToken: data['refreshToken'] as String,
    );
    _log.i('AuthRepository.refresh success');
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  dynamic _unwrap(Response<dynamic> response) {
    final body = response.data;
    if (body is Map<String, dynamic> && body.containsKey('data')) {
      return body['data'];
    }
    return body;
  }

  String? _extractMessage(Response<dynamic> response) {
    try {
      final body = response.data as Map<String, dynamic>?;
      return body?['message'] as String?;
    } catch (_) {
      return null;
    }
  }

  int _parseInt(dynamic v) {
    if (v is int) return v;
    if (v is String) return int.parse(v);
    if (v is double) return v.toInt();
    throw FormatException('Cannot parse int from $v');
  }

  int? _parseIntNullable(dynamic v) {
    if (v == null) return null;
    return _parseInt(v);
  }
}
