/// Dart models for the sync wire contract (US-POS-017/018).
/// These match the OpenAPI schemas under the `Sync` tag in
/// orbix-engine-contracts/openapi/orbix-engine.yaml exactly.
///
/// NOT generated — the contract is frozen. Do not deviate from field names.
/// ApiResponse<T> envelope is handled by SyncApiClient; these classes are T.
library;

// ---------------------------------------------------------------------------
// Push request
// ---------------------------------------------------------------------------

class SyncOp {
  final String clientOpId;
  final String opType;
  final int seq;
  final DateTime occurredAt;
  final String? dependsOn;
  final Map<String, dynamic> payload;

  const SyncOp({
    required this.clientOpId,
    required this.opType,
    required this.seq,
    required this.occurredAt,
    this.dependsOn,
    required this.payload,
  });

  Map<String, dynamic> toJson() => {
        'clientOpId': clientOpId,
        'opType': opType,
        'seq': seq,
        'occurredAt': occurredAt.toUtc().toIso8601String(),
        if (dependsOn != null) 'dependsOn': dependsOn,
        'payload': payload,
      };
}

class SyncPushRequest {
  final String deviceId;
  final int clientContractVersion;
  final List<SyncOp> ops;

  const SyncPushRequest({
    required this.deviceId,
    required this.clientContractVersion,
    required this.ops,
  });

  Map<String, dynamic> toJson() => {
        'deviceId': deviceId,
        'clientContractVersion': clientContractVersion,
        'ops': ops.map((o) => o.toJson()).toList(),
      };
}

// ---------------------------------------------------------------------------
// Push result
// ---------------------------------------------------------------------------

/// Per-op verdict values.
enum SyncVerdict { accepted, duplicate, rejected, deferred }

SyncVerdict _verdictFromString(String v) => switch (v.toUpperCase()) {
      'ACCEPTED' => SyncVerdict.accepted,
      'DUPLICATE' => SyncVerdict.duplicate,
      'REJECTED' => SyncVerdict.rejected,
      'DEFERRED' => SyncVerdict.deferred,
      _ => SyncVerdict.rejected,
    };

class SyncOpResult {
  final String clientOpId;
  final SyncVerdict verdict;
  final String? serverEntityId;
  final String? serverEntityUid;
  final String? serverNumber;
  final String? errorCode;
  final String? errorMessage;

  const SyncOpResult({
    required this.clientOpId,
    required this.verdict,
    this.serverEntityId,
    this.serverEntityUid,
    this.serverNumber,
    this.errorCode,
    this.errorMessage,
  });

  factory SyncOpResult.fromJson(Map<String, dynamic> json) => SyncOpResult(
        clientOpId: json['clientOpId'] as String,
        verdict: _verdictFromString(json['verdict'] as String),
        serverEntityId: json['serverEntityId'] as String?,
        serverEntityUid: json['serverEntityUid'] as String?,
        serverNumber: json['serverNumber'] as String?,
        errorCode: json['errorCode'] as String?,
        errorMessage: json['errorMessage'] as String?,
      );
}

class SyncPushResult {
  final int batchAcceptedCount;
  final int batchRejectedCount;
  final DateTime serverReceivedAt;
  final bool resyncRequired;
  final List<SyncOpResult> results;

  const SyncPushResult({
    required this.batchAcceptedCount,
    required this.batchRejectedCount,
    required this.serverReceivedAt,
    required this.resyncRequired,
    required this.results,
  });

  factory SyncPushResult.fromJson(Map<String, dynamic> json) => SyncPushResult(
        batchAcceptedCount: json['batchAcceptedCount'] as int,
        batchRejectedCount: json['batchRejectedCount'] as int,
        serverReceivedAt: DateTime.parse(json['serverReceivedAt'] as String),
        resyncRequired: (json['resyncRequired'] as bool?) ?? false,
        results: (json['results'] as List<dynamic>)
            .map((e) => SyncOpResult.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
}

// ---------------------------------------------------------------------------
// Pull result
// ---------------------------------------------------------------------------

class SyncDataset {
  final List<Map<String, dynamic>> upserts;
  final List<String> deletes;

  const SyncDataset({required this.upserts, required this.deletes});

  factory SyncDataset.fromJson(Map<String, dynamic> json) => SyncDataset(
        upserts: (json['upserts'] as List<dynamic>)
            .map((e) => Map<String, dynamic>.from(e as Map))
            .toList(),
        deletes: (json['deletes'] as List<dynamic>).cast<String>(),
      );
}

class SyncPullResult {
  final DateTime serverTime;
  final String nextCursor;
  final bool hasMore;
  final bool resyncRequired;
  final Map<String, SyncDataset> datasets;

  const SyncPullResult({
    required this.serverTime,
    required this.nextCursor,
    required this.hasMore,
    required this.resyncRequired,
    required this.datasets,
  });

  factory SyncPullResult.fromJson(Map<String, dynamic> json) => SyncPullResult(
        serverTime: DateTime.parse(json['serverTime'] as String),
        nextCursor: json['nextCursor'] as String,
        hasMore: json['hasMore'] as bool,
        resyncRequired: (json['resyncRequired'] as bool?) ?? false,
        datasets: (json['datasets'] as Map<String, dynamic>).map(
          (k, v) => MapEntry(k, SyncDataset.fromJson(v as Map<String, dynamic>)),
        ),
      );
}

// ---------------------------------------------------------------------------
// Till-session close
// ---------------------------------------------------------------------------

class TillCloseManifest {
  final int posSaleCount;
  final String posSaleTotal;
  final int cashPickupCount;
  final String cashPickupTotal;
  final int pettyCashCount;
  final String pettyCashTotal;
  final List<String> clientOpIds;

  const TillCloseManifest({
    required this.posSaleCount,
    required this.posSaleTotal,
    required this.cashPickupCount,
    required this.cashPickupTotal,
    required this.pettyCashCount,
    required this.pettyCashTotal,
    required this.clientOpIds,
  });

  Map<String, dynamic> toJson() => {
        'posSaleCount': posSaleCount,
        'posSaleTotal': posSaleTotal,
        'cashPickupCount': cashPickupCount,
        'cashPickupTotal': cashPickupTotal,
        'pettyCashCount': pettyCashCount,
        'pettyCashTotal': pettyCashTotal,
        'clientOpIds': clientOpIds,
      };
}

class TillSessionCloseRequest {
  final String tillSessionClientOpId;
  final String declaredCash;
  final TillCloseManifest manifest;

  const TillSessionCloseRequest({
    required this.tillSessionClientOpId,
    required this.declaredCash,
    required this.manifest,
  });

  Map<String, dynamic> toJson() => {
        'tillSessionClientOpId': tillSessionClientOpId,
        'declaredCash': declaredCash,
        'manifest': manifest.toJson(),
      };
}

enum TillCloseStatus { closed, reconcileIncomplete }

TillCloseStatus _statusFromString(String s) => switch (s.toUpperCase()) {
      'CLOSED' => TillCloseStatus.closed,
      'RECONCILE_INCOMPLETE' => TillCloseStatus.reconcileIncomplete,
      _ => TillCloseStatus.reconcileIncomplete,
    };

/// Convert a JSON money value to a canonical decimal string.
/// The server returns BigDecimal values as JSON numbers; test mocks use strings.
/// Both are accepted — callers always get a 4dp decimal string.
String _toMoneyStr(dynamic value) {
  if (value == null) return '0.0000';
  if (value is String) return value;
  if (value is num) return value.toStringAsFixed(4);
  return value.toString();
}

class TillSessionCloseResult {
  final String tillSessionUid;
  final TillCloseStatus status;
  final String openingFloat;
  final String expectedCash;
  final String declaredCash;
  final String variance;
  final List<String> confirmedClientOpIds;
  final List<String> missingClientOpIds;
  final List<String> unexpectedClientOpIds;
  final String? zReportObjectKey;

  const TillSessionCloseResult({
    required this.tillSessionUid,
    required this.status,
    required this.openingFloat,
    required this.expectedCash,
    required this.declaredCash,
    required this.variance,
    required this.confirmedClientOpIds,
    required this.missingClientOpIds,
    required this.unexpectedClientOpIds,
    this.zReportObjectKey,
  });

  factory TillSessionCloseResult.fromJson(Map<String, dynamic> json) =>
      TillSessionCloseResult(
        tillSessionUid: json['tillSessionUid'] as String,
        status: _statusFromString(json['status'] as String),
        openingFloat: _toMoneyStr(json['openingFloat']),
        expectedCash: _toMoneyStr(json['expectedCash']),
        declaredCash: _toMoneyStr(json['declaredCash']),
        variance: _toMoneyStr(json['variance']),
        confirmedClientOpIds:
            (json['confirmedClientOpIds'] as List<dynamic>).cast<String>(),
        missingClientOpIds:
            (json['missingClientOpIds'] as List<dynamic>?)?.cast<String>() ?? [],
        unexpectedClientOpIds:
            (json['unexpectedClientOpIds'] as List<dynamic>?)?.cast<String>() ?? [],
        zReportObjectKey: json['zReportObjectKey'] as String?,
      );
}
