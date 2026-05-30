// Tests for price-list selection in CatalogRepository (US-CAT-008).
//
// Coverage:
// - Default repo (RETAIL preferred) picks RETAIL over DEFAULT
// - Repo with preferred=DEFAULT picks DEFAULT over WHOLESALE
// - Falls back to DEFAULT when preferred list not present
// - Falls back to any row when neither preferred nor DEFAULT present
// - watchCatalog reflects price change when preferred list changes
//   (via a new repository instance)

import 'package:drift/drift.dart' hide isNotNull;
import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:orbix_engine_pos/data/local/database.dart';
import 'package:orbix_engine_pos/features/catalog/catalog_repository.dart';

PosDatabase _makeDb() => PosDatabase.forTesting(NativeDatabase.memory());

Future<void> _seedItem(PosDatabase db, {required int id, required String code}) async {
  await db.into(db.items).insert(ItemsCompanion.insert(
    id: Value(id),
    code: code,
    name: 'Item $code',
    price: 0.0,
    vatGroup: 'STD',
    itemGroupId: 1,
  ));
}

Future<void> _seedPrice(PosDatabase db,
    {required int itemId, required String priceListCode, required double price}) async {
  await db.into(db.priceRows).insert(PriceRowsCompanion.insert(
    itemId: itemId,
    priceListCode: priceListCode,
    price: price,
  ));
}

void main() {
  late PosDatabase db;

  setUp(() => db = _makeDb());
  tearDown(() => db.close());

  group('price-list selection', () {
    test('preferred=RETAIL picks RETAIL over DEFAULT', () async {
      await _seedItem(db, id: 1, code: 'A');
      await _seedPrice(db, itemId: 1, priceListCode: 'DEFAULT', price: 10000);
      await _seedPrice(db, itemId: 1, priceListCode: 'RETAIL', price: 12000);

      final repo = CatalogRepository(db: db, preferredPriceList: 'RETAIL');
      final items = await repo.watchCatalog().first;
      expect(items.first.price, closeTo(12000, 0.01));
      expect(items.first.priceListCode, 'RETAIL');
    });

    test('preferred=DEFAULT picks DEFAULT over WHOLESALE', () async {
      await _seedItem(db, id: 2, code: 'B');
      await _seedPrice(db, itemId: 2, priceListCode: 'WHOLESALE', price: 8000);
      await _seedPrice(db, itemId: 2, priceListCode: 'DEFAULT', price: 10000);

      final repo = CatalogRepository(db: db, preferredPriceList: 'DEFAULT');
      final items = await repo.watchCatalog().first;
      expect(items.first.price, closeTo(10000, 0.01));
      expect(items.first.priceListCode, 'DEFAULT');
    });

    test('falls back to DEFAULT when preferred list absent', () async {
      await _seedItem(db, id: 3, code: 'C');
      await _seedPrice(db, itemId: 3, priceListCode: 'DEFAULT', price: 9000);
      // No RETAIL row.

      final repo = CatalogRepository(db: db, preferredPriceList: 'RETAIL');
      final items = await repo.watchCatalog().first;
      expect(items.first.price, closeTo(9000, 0.01));
      expect(items.first.priceListCode, 'DEFAULT');
    });

    test('falls back to any row when neither preferred nor DEFAULT present', () async {
      await _seedItem(db, id: 4, code: 'D');
      await _seedPrice(db, itemId: 4, priceListCode: 'WHOLESALE', price: 7000);
      // No RETAIL or DEFAULT rows.

      final repo = CatalogRepository(db: db, preferredPriceList: 'RETAIL');
      final items = await repo.watchCatalog().first;
      expect(items.first.hasPriceRow, isTrue);
      expect(items.first.price, closeTo(7000, 0.01));
    });

    test('switching preferred list (new repo instance) changes prices', () async {
      await _seedItem(db, id: 5, code: 'E');
      await _seedPrice(db, itemId: 5, priceListCode: 'DEFAULT', price: 10000);
      await _seedPrice(db, itemId: 5, priceListCode: 'WHOLESALE', price: 6000);

      final retailRepo =
          CatalogRepository(db: db, preferredPriceList: 'DEFAULT');
      final wholesaleRepo =
          CatalogRepository(db: db, preferredPriceList: 'WHOLESALE');

      final retailItems = await retailRepo.watchCatalog().first;
      final wholesaleItems = await wholesaleRepo.watchCatalog().first;

      expect(retailItems.first.price, closeTo(10000, 0.01));
      expect(wholesaleItems.first.price, closeTo(6000, 0.01));
    });
  });
}
