// Tests for sync wire-model serialisation/deserialisation.
// Pins the exact JSON field names against the OpenAPI contract.

import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:orbix_engine_pos/data/sync/sync_models.dart';

void main() {
  group('SyncOp.toJson', () {
    test('includes all required fields', () {
      final op = SyncOp(
        clientOpId: '01JBVQXKWZP0000000000000AB',
        opType: 'POS_SALE',
        seq: 42,
        occurredAt: DateTime.utc(2026, 5, 30, 8, 14, 3, 221),
        dependsOn: '01JBVQXKWZP0000000000000AA',
        payload: {'total': '12500.0000', 'customerId': '1'},
      );

      final json = op.toJson();
      expect(json['clientOpId'], '01JBVQXKWZP0000000000000AB');
      expect(json['opType'], 'POS_SALE');
      expect(json['seq'], 42);
      expect(json['occurredAt'], '2026-05-30T08:14:03.221Z');
      expect(json['dependsOn'], '01JBVQXKWZP0000000000000AA');
      expect((json['payload'] as Map)['total'], '12500.0000');
    });

    test('omits dependsOn when null', () {
      final op = SyncOp(
        clientOpId: '01JBVQXKWZP0000000000000AB',
        opType: 'TILL_SESSION_OPEN',
        seq: 1,
        occurredAt: DateTime.utc(2026, 5, 30, 8, 0, 0),
        payload: {'openingFloat': '100000.0000'},
      );
      final json = op.toJson();
      expect(json.containsKey('dependsOn'), isFalse);
    });
  });

  group('SyncPushResult.fromJson', () {
    const raw = '''
    {
      "batchAcceptedCount": 2,
      "batchRejectedCount": 0,
      "serverReceivedAt": "2026-05-30T08:14:05.880Z",
      "resyncRequired": false,
      "results": [
        {
          "clientOpId": "01JBVQXKWZP0000000000000AB",
          "verdict": "ACCEPTED",
          "serverEntityId": "100482",
          "serverEntityUid": "01JA9Z...",
          "serverNumber": "POS-1-20260530-00027",
          "errorCode": null,
          "errorMessage": null
        },
        {
          "clientOpId": "01JBVQXKWZP0000000000000AC",
          "verdict": "DUPLICATE",
          "serverEntityId": "100481",
          "serverEntityUid": "01JA9Y...",
          "serverNumber": "POS-1-20260530-00026",
          "errorCode": null,
          "errorMessage": null
        }
      ]
    }
    ''';

    test('parses all fields correctly', () {
      final result = SyncPushResult.fromJson(jsonDecode(raw) as Map<String, dynamic>);
      expect(result.batchAcceptedCount, 2);
      expect(result.batchRejectedCount, 0);
      expect(result.resyncRequired, isFalse);
      expect(result.results.length, 2);
      expect(result.serverReceivedAt, DateTime.utc(2026, 5, 30, 8, 14, 5, 880));
    });

    test('ACCEPTED verdict parses correctly', () {
      final result = SyncPushResult.fromJson(jsonDecode(raw) as Map<String, dynamic>);
      final accepted = result.results[0];
      expect(accepted.verdict, SyncVerdict.accepted);
      expect(accepted.serverEntityId, '100482');
      expect(accepted.serverNumber, 'POS-1-20260530-00027');
      expect(accepted.errorCode, isNull);
    });

    test('DUPLICATE verdict parses correctly', () {
      final result = SyncPushResult.fromJson(jsonDecode(raw) as Map<String, dynamic>);
      final duplicate = result.results[1];
      expect(duplicate.verdict, SyncVerdict.duplicate);
      expect(duplicate.serverEntityId, '100481');
    });
  });

  group('SyncPushResult — REJECTED and DEFERRED verdicts', () {
    const raw = '''
    {
      "batchAcceptedCount": 0,
      "batchRejectedCount": 1,
      "serverReceivedAt": "2026-05-30T08:14:05.880Z",
      "resyncRequired": false,
      "results": [
        {
          "clientOpId": "01JBVQXKWZP0000000000000AD",
          "verdict": "REJECTED",
          "serverEntityId": null,
          "serverEntityUid": null,
          "serverNumber": null,
          "errorCode": "ITEM_ARCHIVED",
          "errorMessage": "Item BR-001 is archived"
        },
        {
          "clientOpId": "01JBVQXKWZP0000000000000AE",
          "verdict": "DEFERRED",
          "serverEntityId": null,
          "serverEntityUid": null,
          "serverNumber": null,
          "errorCode": "DEFERRED",
          "errorMessage": "dependsOn op not yet applied; re-push next cycle"
        }
      ]
    }
    ''';

    test('REJECTED verdict parses with error fields', () {
      final result = SyncPushResult.fromJson(jsonDecode(raw) as Map<String, dynamic>);
      final rejected = result.results[0];
      expect(rejected.verdict, SyncVerdict.rejected);
      expect(rejected.errorCode, 'ITEM_ARCHIVED');
      expect(rejected.errorMessage, 'Item BR-001 is archived');
    });

    test('DEFERRED verdict parses correctly', () {
      final result = SyncPushResult.fromJson(jsonDecode(raw) as Map<String, dynamic>);
      final deferred = result.results[1];
      expect(deferred.verdict, SyncVerdict.deferred);
    });
  });

  group('SyncPullResult.fromJson', () {
    const raw = '''
    {
      "serverTime": "2026-05-30T08:14:05.880Z",
      "nextCursor": "eyJ2IjoxLCJzZXEiOjk5ODg3N30=",
      "hasMore": false,
      "resyncRequired": false,
      "datasets": {
        "catalog": {
          "upserts": [{"id": 1, "code": "BR-001", "name": "White bread"}],
          "deletes": ["42", "43"]
        },
        "price": {
          "upserts": [],
          "deletes": []
        }
      }
    }
    ''';

    test('parses cursor and hasMore', () {
      final result = SyncPullResult.fromJson(jsonDecode(raw) as Map<String, dynamic>);
      expect(result.nextCursor, 'eyJ2IjoxLCJzZXEiOjk5ODg3N30=');
      expect(result.hasMore, isFalse);
      expect(result.resyncRequired, isFalse);
    });

    test('parses dataset upserts and deletes', () {
      final result = SyncPullResult.fromJson(jsonDecode(raw) as Map<String, dynamic>);
      final catalog = result.datasets['catalog']!;
      expect(catalog.upserts.length, 1);
      expect(catalog.upserts[0]['code'], 'BR-001');
      expect(catalog.deletes, ['42', '43']);
    });

    test('unknown dataset key is preserved (forward compat)', () {
      final result = SyncPullResult.fromJson(jsonDecode(raw) as Map<String, dynamic>);
      expect(result.datasets.containsKey('price'), isTrue);
    });
  });

  group('TillSessionCloseResult.fromJson', () {
    const closedRaw = '''
    {
      "tillSessionUid": "01JA9Z...",
      "status": "CLOSED",
      "openingFloat": "100000.0000",
      "expectedCash": "1450000.0000",
      "declaredCash": "1448000.0000",
      "variance": "-2000.0000",
      "confirmedClientOpIds": ["01JBVQ1", "01JBVQ2"],
      "missingClientOpIds": [],
      "unexpectedClientOpIds": [],
      "zReportObjectKey": null
    }
    ''';

    test('CLOSED status parses correctly', () {
      final result = TillSessionCloseResult.fromJson(
          jsonDecode(closedRaw) as Map<String, dynamic>);
      expect(result.status, TillCloseStatus.closed);
      expect(result.confirmedClientOpIds, ['01JBVQ1', '01JBVQ2']);
      expect(result.variance, '-2000.0000');
      expect(result.zReportObjectKey, isNull);
    });

    const incompleteRaw = '''
    {
      "tillSessionUid": "01JA9Z...",
      "status": "RECONCILE_INCOMPLETE",
      "openingFloat": "100000.0000",
      "expectedCash": "1450000.0000",
      "declaredCash": "1448000.0000",
      "variance": "0.0000",
      "confirmedClientOpIds": ["01JBVQ1"],
      "missingClientOpIds": ["01JBVQ3"],
      "unexpectedClientOpIds": [],
      "zReportObjectKey": null
    }
    ''';

    test('RECONCILE_INCOMPLETE status parses correctly', () {
      final result = TillSessionCloseResult.fromJson(
          jsonDecode(incompleteRaw) as Map<String, dynamic>);
      expect(result.status, TillCloseStatus.reconcileIncomplete);
      expect(result.missingClientOpIds, ['01JBVQ3']);
      expect(result.unexpectedClientOpIds, isEmpty);
    });
  });
}
