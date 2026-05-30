// Tests for Drift-backed customer provider (US-POS customer picker).
//
// Coverage:
// - PosCustomer.walkIn is the default sentinel
// - customerSearchResultsProvider returns walk-in when table empty
// - customerSearchResultsProvider returns synced customers from Drift
// - customerSearchResultsProvider filters by name substring
// - customerSearchResultsProvider filters by code substring
// - Inactive customers are excluded from results

import 'package:drift/drift.dart' hide isNotNull;
import 'package:drift/native.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:orbix_engine_pos/data/local/database.dart';
import 'package:orbix_engine_pos/data/sync/sync_providers.dart' show posDatabaseProvider;
import 'package:orbix_engine_pos/features/customer/customer_providers.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:orbix_engine_pos/data/core_providers.dart';

ProviderContainer _makeContainer(PosDatabase db) {
  SharedPreferences.setMockInitialValues({});
  return ProviderContainer(
    overrides: [
      posDatabaseProvider.overrideWithValue(db),
      sharedPrefsProvider.overrideWith(
          (ref) => throw UnimplementedError('not needed in test')),
    ],
  );
}

Future<void> _seedCustomer(
  PosDatabase db, {
  required int id,
  required String code,
  required String name,
  bool isWalkIn = false,
  bool isActive = true,
}) async {
  await db.into(db.customers).insert(CustomersCompanion.insert(
    id: Value(id),
    code: code,
    name: name,
    isWalkIn: Value(isWalkIn),
    isActive: Value(isActive),
  ));
}

void main() {
  late PosDatabase db;

  setUp(() => db = PosDatabase.forTesting(NativeDatabase.memory()));
  tearDown(() => db.close());

  group('PosCustomer', () {
    test('walkIn sentinel has id=0 and isWalkIn=true', () {
      expect(PosCustomer.walkIn.id, 0);
      expect(PosCustomer.walkIn.isWalkIn, isTrue);
      expect(PosCustomer.walkIn.code, 'WALKIN');
    });
  });

  group('customerSearchResultsProvider', () {
    test('returns only walk-in when Customers table is empty', () async {
      final container = _makeContainer(db);
      addTearDown(container.dispose);

      container.read(customerSearchQueryProvider.notifier).state = '';

      // Wait for the FutureProvider to resolve.
      final results =
          await container.read(customerSearchResultsProvider.future);

      expect(results, hasLength(1));
      expect(results.first.isWalkIn, isTrue);
    });

    test('returns walk-in plus synced customers', () async {
      await _seedCustomer(db, id: 1, code: 'C001', name: 'Sarah Nakato');
      await _seedCustomer(db, id: 2, code: 'C002', name: 'Joseph Mwangi');

      final container = _makeContainer(db);
      addTearDown(container.dispose);

      final results =
          await container.read(customerSearchResultsProvider.future);

      // Walk-in always first, then alphabetical.
      expect(results.length, greaterThanOrEqualTo(3));
      expect(results.first.isWalkIn, isTrue);
      final names = results.map((c) => c.name).toList();
      expect(names, containsAll(['Sarah Nakato', 'Joseph Mwangi']));
    });

    test('filters by name substring', () async {
      await _seedCustomer(db, id: 3, code: 'C003', name: 'Amani Hotels Ltd');
      await _seedCustomer(db, id: 4, code: 'C004', name: 'John Doe');

      final container = _makeContainer(db);
      addTearDown(container.dispose);

      container.read(customerSearchQueryProvider.notifier).state = 'amani';

      final results =
          await container.read(customerSearchResultsProvider.future);

      final nonWalkIn = results.where((c) => !c.isWalkIn).toList();
      expect(nonWalkIn, hasLength(1));
      expect(nonWalkIn.first.name, 'Amani Hotels Ltd');
    });

    test('filters by code substring', () async {
      await _seedCustomer(db, id: 5, code: 'HOT-001', name: 'Serena Hotel');
      await _seedCustomer(db, id: 6, code: 'RTL-001', name: 'Retail Customer');

      final container = _makeContainer(db);
      addTearDown(container.dispose);

      container.read(customerSearchQueryProvider.notifier).state = 'HOT';

      final results =
          await container.read(customerSearchResultsProvider.future);

      final nonWalkIn = results.where((c) => !c.isWalkIn).toList();
      expect(nonWalkIn, hasLength(1));
      expect(nonWalkIn.first.code, 'HOT-001');
    });

    test('excludes inactive customers', () async {
      await _seedCustomer(db, id: 7, code: 'OLD', name: 'Archived Ltd', isActive: false);
      await _seedCustomer(db, id: 8, code: 'ACT', name: 'Active Customer');

      final container = _makeContainer(db);
      addTearDown(container.dispose);

      final results =
          await container.read(customerSearchResultsProvider.future);

      final nonWalkIn = results.where((c) => !c.isWalkIn).toList();
      expect(nonWalkIn.any((c) => c.code == 'OLD'), isFalse);
      expect(nonWalkIn.any((c) => c.code == 'ACT'), isTrue);
    });
  });
}
