// Tests for barcode lookup via Drift Barcodes table (US-POS-003).
//
// Coverage:
// - findByBarcode returns null when barcode not in DB
// - findByBarcode resolves to correct CatalogItem with price
// - findByBarcode returns null when the linked item is inactive
// - findByBarcode with a price list — uses preferred list

import 'package:drift/drift.dart' hide isNotNull, isNull;
import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:orbix_engine_pos/data/local/database.dart';
import 'package:orbix_engine_pos/features/catalog/catalog_repository.dart';

PosDatabase _makeDb() => PosDatabase.forTesting(NativeDatabase.memory());

Future<void> _seedItem(PosDatabase db,
    {required int id, required String code, bool isActive = true}) async {
  await db.into(db.items).insert(ItemsCompanion.insert(
    id: Value(id),
    code: code,
    name: 'Item $code',
    price: 0.0,
    vatGroup: 'STD',
    itemGroupId: 1,
    isActive: Value(isActive),
  ));
}

Future<void> _seedBarcode(PosDatabase db,
    {required String barcode, required int itemId, double packQty = 1}) async {
  await db.into(db.barcodes).insert(BarcodesCompanion.insert(
    barcode: barcode,
    itemId: itemId,
    packQty: Value(packQty),
  ));
}

Future<void> _seedPrice(PosDatabase db,
    {required int itemId,
    required String priceListCode,
    required double price}) async {
  await db.into(db.priceRows).insert(PriceRowsCompanion.insert(
    itemId: itemId,
    priceListCode: priceListCode,
    price: price,
  ));
}

void main() {
  late PosDatabase db;
  late CatalogRepository repo;

  setUp(() {
    db = _makeDb();
    repo = CatalogRepository(db: db, preferredPriceList: 'RETAIL');
  });

  tearDown(() => db.close());

  group('findByBarcode', () {
    test('returns null when barcode not registered', () async {
      final result = await repo.findByBarcode('5901234100013');
      expect(result, isNull);
    });

    test('resolves barcode to CatalogItem with correct price', () async {
      await _seedItem(db, id: 1, code: 'COKE');
      await _seedBarcode(db, barcode: '5449000000996', itemId: 1);
      await _seedPrice(db, itemId: 1, priceListCode: 'RETAIL', price: 2000);

      final result = await repo.findByBarcode('5449000000996');
      expect(result, isNotNull);
      expect(result!.code, 'COKE');
      expect(result.price, closeTo(2000, 0.01));
      expect(result.hasPriceRow, isTrue);
      expect(result.priceListCode, 'RETAIL');
    });

    test('returns null when linked item is inactive', () async {
      await _seedItem(db, id: 2, code: 'DEAD', isActive: false);
      await _seedBarcode(db, barcode: '9999999999999', itemId: 2);
      await _seedPrice(db, itemId: 2, priceListCode: 'RETAIL', price: 1000);

      final result = await repo.findByBarcode('9999999999999');
      expect(result, isNull);
    });

    test('resolves item with no price row — hasPriceRow is false', () async {
      await _seedItem(db, id: 3, code: 'NOPRICE');
      await _seedBarcode(db, barcode: '1111111111111', itemId: 3);
      // No price row seeded.

      final result = await repo.findByBarcode('1111111111111');
      expect(result, isNotNull);
      expect(result!.hasPriceRow, isFalse);
      expect(result.price, 0.0);
    });

    test('prefers configured price list over DEFAULT when both exist', () async {
      await _seedItem(db, id: 4, code: 'RICE');
      await _seedBarcode(db, barcode: '5901234400013', itemId: 4);
      await _seedPrice(db, itemId: 4, priceListCode: 'DEFAULT', price: 15000);
      await _seedPrice(db, itemId: 4, priceListCode: 'RETAIL', price: 18000);

      final result = await repo.findByBarcode('5901234400013');
      expect(result!.price, closeTo(18000, 0.01));
      expect(result.priceListCode, 'RETAIL');
    });
  });
}
