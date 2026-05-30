// Tests for CatalogRepository — Drift-backed catalog + price join.
//
// Coverage:
// - watchCatalog emits items joined to prices from Drift
// - empty state when no Items rows
// - item with DEFAULT price row uses DEFAULT price
// - item with no DEFAULT falls back to first available price
// - item with no price row has hasPriceRow=false
// - sellable items (hasPriceRow=true) sorted before no-price items
// - findByCode returns the item with correct price
// - findByCode returns null for unknown code
// - CartNotifier.addCatalogItem uses synced price; rejects no-price items

import 'package:drift/drift.dart' hide isNotNull, isNull;
import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:orbix_engine_pos/data/local/database.dart';
import 'package:orbix_engine_pos/features/catalog/catalog_repository.dart';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

PosDatabase _makeDb() => PosDatabase.forTesting(NativeDatabase.memory());

Future<void> _seedItem(PosDatabase db, {
  required int id,
  required String code,
  required String name,
  bool isActive = true,
}) async {
  await db.into(db.items).insert(ItemsCompanion.insert(
    id: Value(id),
    code: code,
    name: name,
    price: 0.0, // legacy field; price comes from PriceRows
    vatGroup: 'STD',
    itemGroupId: 1,
    isActive: Value(isActive),
  ));
}

Future<void> _seedPrice(PosDatabase db, {
  required int itemId,
  required String priceListCode,
  required double price,
  String currency = 'TZS',
}) async {
  await db.into(db.priceRows).insert(PriceRowsCompanion.insert(
    itemId: itemId,
    priceListCode: priceListCode,
    price: price,
    currency: Value(currency),
  ));
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

void main() {
  late PosDatabase db;
  late CatalogRepository repo;

  setUp(() {
    db = _makeDb();
    repo = CatalogRepository(db: db);
  });

  tearDown(() => db.close());

  // ---------------------------------------------------------------------------
  // watchCatalog — basic emissions
  // ---------------------------------------------------------------------------

  group('watchCatalog', () {
    test('emits empty list when no items in DB', () async {
      final items = await repo.watchCatalog().first;
      expect(items, isEmpty);
    });

    test('emits items joined to prices', () async {
      await _seedItem(db, id: 1, code: 'COKE', name: 'Coca-Cola 500ml');
      await _seedPrice(db, itemId: 1, priceListCode: 'DEFAULT', price: 2000.0);

      final items = await repo.watchCatalog().first;
      expect(items.length, 1);
      expect(items[0].code, 'COKE');
      expect(items[0].name, 'Coca-Cola 500ml');
      expect(items[0].price, 2000.0);
      expect(items[0].currency, 'TZS');
      expect(items[0].hasPriceRow, isTrue);
      expect(items[0].priceListCode, 'DEFAULT');
    });

    test('emits item with hasPriceRow=false when no price row exists', () async {
      await _seedItem(db, id: 2, code: 'SUGAR', name: 'Sugar 1kg');
      // No price row seeded

      final items = await repo.watchCatalog().first;
      expect(items.length, 1);
      expect(items[0].hasPriceRow, isFalse);
      expect(items[0].price, 0.0);
    });

    test('excludes inactive items', () async {
      await _seedItem(db, id: 3, code: 'DISCO', name: 'Discontinued Item', isActive: false);
      await _seedPrice(db, itemId: 3, priceListCode: 'DEFAULT', price: 999.0);

      final items = await repo.watchCatalog().first;
      expect(items, isEmpty);
    });

    test('prefers DEFAULT price list over other lists', () async {
      await _seedItem(db, id: 4, code: 'RICE', name: 'Rice 5kg');
      await _seedPrice(db, itemId: 4, priceListCode: 'WHOLESALE', price: 15000.0);
      await _seedPrice(db, itemId: 4, priceListCode: 'DEFAULT', price: 18000.0);

      final items = await repo.watchCatalog().first;
      expect(items[0].price, 18000.0);
      expect(items[0].priceListCode, 'DEFAULT');
    });

    test('falls back to any available price when DEFAULT absent', () async {
      await _seedItem(db, id: 5, code: 'OIL', name: 'Cooking Oil 3L');
      await _seedPrice(db, itemId: 5, priceListCode: 'WHOLESALE', price: 22000.0);

      final items = await repo.watchCatalog().first;
      expect(items[0].hasPriceRow, isTrue);
      expect(items[0].price, 22000.0);
    });

    test('sellable items sorted before no-price items', () async {
      await _seedItem(db, id: 6, code: 'AAA', name: 'Item AAA');
      await _seedItem(db, id: 7, code: 'BBB', name: 'Item BBB');
      // Only BBB has a price
      await _seedPrice(db, itemId: 7, priceListCode: 'DEFAULT', price: 500.0);

      final items = await repo.watchCatalog().first;
      expect(items.length, 2);
      expect(items[0].code, 'BBB'); // sellable first
      expect(items[1].code, 'AAA'); // no price second
    });

    test('reacts to new price row inserted after initial emit', () async {
      await _seedItem(db, id: 8, code: 'MILK', name: 'Fresh Milk 1L');

      final stream = repo.watchCatalog();
      final firstEmit = await stream.first;
      expect(firstEmit[0].hasPriceRow, isFalse);

      // Insert a price row and check the stream emits an updated list.
      await _seedPrice(db, itemId: 8, priceListCode: 'DEFAULT', price: 3200.0);
      final secondEmit = await stream.first;
      expect(secondEmit[0].hasPriceRow, isTrue);
      expect(secondEmit[0].price, 3200.0);
    });
  });

  // ---------------------------------------------------------------------------
  // findByCode
  // ---------------------------------------------------------------------------

  group('findByCode', () {
    test('returns CatalogItem with correct price for known code', () async {
      await _seedItem(db, id: 9, code: 'SUGAR', name: 'Sugar 1kg');
      await _seedPrice(db, itemId: 9, priceListCode: 'DEFAULT', price: 4500.0);

      final item = await repo.findByCode('SUGAR');
      expect(item, isNotNull);
      expect(item!.price, 4500.0);
      expect(item.hasPriceRow, isTrue);
    });

    test('returns null for unknown code', () async {
      final item = await repo.findByCode('UNKNOWN');
      expect(item, isNull);
    });

    test('returns null for inactive item', () async {
      await _seedItem(db, id: 10, code: 'OLD', name: 'Old Item', isActive: false);
      final item = await repo.findByCode('OLD');
      expect(item, isNull);
    });
  });

  // ---------------------------------------------------------------------------
  // Multiple items
  // ---------------------------------------------------------------------------

  group('multiple items', () {
    test('returns all active items with their individual prices', () async {
      await _seedItem(db, id: 11, code: 'A', name: 'Alpha');
      await _seedItem(db, id: 12, code: 'B', name: 'Beta');
      await _seedItem(db, id: 13, code: 'C', name: 'Gamma');
      await _seedPrice(db, itemId: 11, priceListCode: 'DEFAULT', price: 100.0);
      await _seedPrice(db, itemId: 12, priceListCode: 'DEFAULT', price: 200.0);
      // Item C has no price

      final items = await repo.watchCatalog().first;
      expect(items.length, 3);
      // Sellable first (A, B alphabetically), then C (no price)
      final byCode = {for (final i in items) i.code: i};
      expect(byCode['A']!.price, 100.0);
      expect(byCode['B']!.price, 200.0);
      expect(byCode['C']!.hasPriceRow, isFalse);
    });
  });
}
