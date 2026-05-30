/// HTTP client for the four sync endpoints.
///
/// All responses are wrapped in ApiResponse<T> by the server's
/// ApiResponseBodyAdvice. This client unwraps to T before returning,
/// matching the pattern used in the backend/web Angular layer.
///
/// Sync contract version is sent as X-Orbix-Contract-Version header on every
/// request. Server returns 426 (CONTRACT_TOO_OLD) or 409 (CONTRACT_TOO_NEW)
/// when the major is outside the supported range.
library;

import 'package:dio/dio.dart';
import 'package:logger/logger.dart';

import 'sync_models.dart';

/// Current sync contract major. Bump when the wire shape breaks compat.
const kSyncContractVersion = 1;

/// Datasets the POS requests on pull/bootstrap.
const kPosPullDatasets = 'catalog,price,customer,balance';

abstract class SyncApiClient {
  Future<SyncPushResult> push(SyncPushRequest request);
  Future<SyncPullResult> pull({String? cursor, String datasets = kPosPullDatasets});
  Future<SyncPullResult> bootstrap({String datasets = kPosPullDatasets});
  Future<TillSessionCloseResult> closeSession(TillSessionCloseRequest request);
}

class DioSyncApiClient implements SyncApiClient {
  DioSyncApiClient({required Dio dio, Logger? logger})
      : _dio = dio,
        _log = logger ?? Logger();

  final Dio _dio;
  final Logger _log;

  static const _contractHeader = 'X-Orbix-Contract-Version';

  Options get _opts => Options(headers: {
        _contractHeader: kSyncContractVersion,
        'Content-Type': 'application/json',
      });

  /// Unwrap ApiResponse<T>.data; throw if status is not 2xx.
  dynamic _unwrap(Response<dynamic> response) {
    final body = response.data as Map<String, dynamic>;
    // Backend wraps in { status, statusCode, responseCode, message, errors, data }
    if (body.containsKey('data')) {
      return body['data'];
    }
    return body;
  }

  @override
  Future<SyncPushResult> push(SyncPushRequest request) async {
    _log.d('SyncApiClient.push ops=${request.ops.length}');
    final response = await _dio.post<dynamic>(
      '/api/v1/sync/push',
      data: request.toJson(),
      options: _opts,
    );
    return SyncPushResult.fromJson(_unwrap(response) as Map<String, dynamic>);
  }

  @override
  Future<SyncPullResult> pull({
    String? cursor,
    String datasets = kPosPullDatasets,
  }) async {
    _log.d('SyncApiClient.pull cursor=$cursor datasets=$datasets');
    final params = <String, dynamic>{'datasets': datasets};
    if (cursor != null) params['cursor'] = cursor;
    final response = await _dio.get<dynamic>(
      '/api/v1/sync/pull',
      queryParameters: params,
      options: _opts,
    );
    return SyncPullResult.fromJson(_unwrap(response) as Map<String, dynamic>);
  }

  @override
  Future<SyncPullResult> bootstrap({String datasets = kPosPullDatasets}) async {
    _log.d('SyncApiClient.bootstrap datasets=$datasets');
    final response = await _dio.get<dynamic>(
      '/api/v1/sync/bootstrap',
      queryParameters: {'datasets': datasets},
      options: _opts,
    );
    return SyncPullResult.fromJson(_unwrap(response) as Map<String, dynamic>);
  }

  @override
  Future<TillSessionCloseResult> closeSession(
      TillSessionCloseRequest request) async {
    _log.d('SyncApiClient.closeSession session=${request.tillSessionClientOpId}');
    final response = await _dio.post<dynamic>(
      '/api/v1/sync/till-session/close',
      data: request.toJson(),
      options: _opts,
    );
    return TillSessionCloseResult.fromJson(
        _unwrap(response) as Map<String, dynamic>);
  }
}
