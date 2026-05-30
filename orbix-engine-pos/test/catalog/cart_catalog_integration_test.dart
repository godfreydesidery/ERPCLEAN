// Tests for CartNotifier.addCatalogItem:
// - addCatalogItem with hasPriceRow=true uses synced price on cart line
// - addCatalogItem with hasPriceRow=false is rejected (no line added)
// - addCatalogItem increments qty when same item added twice
// - cart line total uses synced price (not any hardcoded value)

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:orbix_engine_pos/features/_demo/mocks.dart';
import 'package:orbix_engine_pos/features/catalog/catalog_item.dart';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

CatalogItem _item({
  required String code,
  required String name,
  required double price,
  bool hasPriceRow = true,
}) =>
    CatalogItem(
      itemId: code.hashCode,
      code: code,
      name: name,
      price: price,
      currency: 'TZS',
      hasPriceRow: hasPriceRow,
      priceListCode: hasPriceRow ? 'DEFAULT' : '',
    );

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

void main() {
  late ProviderContainer container;

  setUp(() {
    container = ProviderContainer();
  });

  tearDown(() => container.dispose());

  group('CartNotifier.addCatalogItem', () {
    test('adds item with synced price when hasPriceRow=true', () {
      final cokeItem = _item(code: 'COKE', name: 'Coca-Cola 500ml', price: 2000.0);
      container.read(cartProvider.notifier).addCatalogItem(cokeItem);

      final cart = container.read(cartProvider);
      expect(cart.length, 1);
      expect(cart[0].item.code, 'COKE');
      expect(cart[0].item.price, 2000.0);
      expect(cart[0].qty, 1.0);
      expect(cart[0].gross, 2000.0);
    });

    test('rejects item with hasPriceRow=false — no line added', () {
      final noPriceItem = _item(
        code: 'NOPRICE', name: 'No Price Item', price: 0.0, hasPriceRow: false,
      );
      container.read(cartProvider.notifier).addCatalogItem(noPriceItem);

      final cart = container.read(cartProvider);
      expect(cart, isEmpty);
    });

    test('increments qty when same item code added twice', () {
      final sugarItem = _item(code: 'SUGAR', name: 'Sugar 1kg', price: 4500.0);
      container.read(cartProvider.notifier).addCatalogItem(sugarItem);
      container.read(cartProvider.notifier).addCatalogItem(sugarItem);

      final cart = container.read(cartProvider);
      expect(cart.length, 1);
      expect(cart[0].qty, 2.0);
    });

    test('cart line total uses synced price from PriceRows', () {
      final riceItem = _item(code: 'RICE5KG', name: 'Rice 5kg', price: 18000.0);
      container.read(cartProvider.notifier).addCatalogItem(riceItem, qty: 3);

      final cart = container.read(cartProvider);
      expect(cart[0].gross, 54000.0); // 18000 * 3
      expect(cart[0].net, 54000.0);   // no discount
    });

    test('cartTotalProvider sums across multiple catalog items', () {
      container.read(cartProvider.notifier)
          .addCatalogItem(_item(code: 'A', name: 'Alpha', price: 1000.0));
      container.read(cartProvider.notifier)
          .addCatalogItem(_item(code: 'B', name: 'Beta', price: 2500.0), qty: 2);

      final total = container.read(cartTotalProvider);
      expect(total, 1000.0 + 2500.0 * 2); // 6000
    });
  });
}
