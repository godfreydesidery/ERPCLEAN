// Mock data + in-memory state for the POS UI prototype.
//
// [mockItems] and [mockItemGroups] are kept for offline-fallback tests and
// the non-retail panes (pharmacy batch demo, restaurant table demo) that do
// not yet have a live-data implementation.  The LIVE sell path (retail,
// supermarket, wholesale, pharmacy, restaurant) reads from the Drift-backed
// catalog and calls [CartNotifier.addCatalogItem] — never [addItem] with a
// [MockItem] from this file.

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../catalog/catalog_item.dart';

// ---------------------------------------------------------------------------
// Catalog
// ---------------------------------------------------------------------------

@immutable
class MockItem {
  /// Internal catalog item code (short, business-meaningful).
  final String code;
  /// Primary barcode printed on the package (EAN-13). Real systems carry a
  /// list of barcodes per item (supplier + retailer codes); this prototype
  /// keeps a single primary one to demo the scan flow.
  final String barcode;
  final String name;
  final String group;
  final double price;
  final String uom;

  const MockItem({
    required this.code,
    required this.barcode,
    required this.name,
    required this.group,
    required this.price,
    required this.uom,
  });
}

const mockItems = <MockItem>[
  MockItem(code: 'BR-001', barcode: '5901234100013', name: 'White bread loaf 600g',  group: 'Bakery',    price:  2500, uom: 'EA'),
  MockItem(code: 'BR-002', barcode: '5901234100020', name: 'Brown bread loaf 600g',  group: 'Bakery',    price:  2700, uom: 'EA'),
  MockItem(code: 'BR-003', barcode: '5901234100037', name: 'Hot dog buns (6 pack)',  group: 'Bakery',    price:  3500, uom: 'PK'),
  MockItem(code: 'DR-001', barcode: '5901234200013', name: 'Fresh milk 1L',          group: 'Dairy',     price:  3200, uom: 'EA'),
  MockItem(code: 'DR-002', barcode: '5901234200020', name: 'Yoghurt strawberry 500ml', group: 'Dairy',   price:  4500, uom: 'EA'),
  MockItem(code: 'DR-003', barcode: '5901234200037', name: 'Cheddar cheese 250g',    group: 'Dairy',     price: 12000, uom: 'EA'),
  MockItem(code: 'BV-001', barcode: '5449000000996', name: 'Coca-Cola 500ml',        group: 'Beverages', price:  2000, uom: 'EA'),
  MockItem(code: 'BV-002', barcode: '5901234300013', name: 'Mineral water 1.5L',     group: 'Beverages', price:  2500, uom: 'EA'),
  MockItem(code: 'BV-003', barcode: '5901234300020', name: 'Orange juice 1L',        group: 'Beverages', price:  6500, uom: 'EA'),
  MockItem(code: 'BV-004', barcode: '5901234300037', name: 'Tusker lager 500ml',     group: 'Beverages', price:  5000, uom: 'EA'),
  MockItem(code: 'GR-001', barcode: '5901234400013', name: 'Rice 5kg bag',           group: 'Groceries', price: 18000, uom: 'BAG'),
  MockItem(code: 'GR-002', barcode: '5901234400020', name: 'Sugar 2kg',              group: 'Groceries', price:  9500, uom: 'KG'),
  MockItem(code: 'GR-003', barcode: '5901234400037', name: 'Cooking oil 3L',         group: 'Groceries', price: 22000, uom: 'EA'),
  MockItem(code: 'SN-001', barcode: '5000159407236', name: 'Pringles 165g',          group: 'Snacks',    price:  8000, uom: 'EA'),
  MockItem(code: 'SN-002', barcode: '7622210447241', name: 'Cadbury chocolate 90g',  group: 'Snacks',    price:  6500, uom: 'EA'),
  MockItem(code: 'HS-001', barcode: '5901234500013', name: 'Toilet paper (10 roll)', group: 'Household', price: 14000, uom: 'PK'),
  MockItem(code: 'HS-002', barcode: '5901234500020', name: 'Laundry soap 1kg',       group: 'Household', price:  7500, uom: 'EA'),
  MockItem(code: 'HS-003', barcode: '5901234500037', name: 'Dishwashing liquid 750ml', group: 'Household', price: 6000, uom: 'EA'),
];

final mockItemGroups = mockItems.map((i) => i.group).toSet().toList()..sort();

// ---------------------------------------------------------------------------
// POS mode — picked at till-open. Same data, different cashier UX.
// ---------------------------------------------------------------------------

enum PosMode {
  /// Convenience / specialty stores. Search + category chips + tile grid.
  /// Best when active SKUs fit on screen.
  retail,

  /// High-throughput grocery, 5k+ SKUs, barcode-driven. Scan input dominates
  /// the screen; no tile grid.
  supermarket,

  /// Pharmacy. Surfaces batch + expiry per line; Rx flag and customer required
  /// for prescription items; controlled-substance gating.
  pharmacy,

  /// B2B / wholesale. Quantity-first numpad entry, customer required, pricing
  /// tier badges, larger discount approval thresholds.
  wholesale,

  /// Restaurant / cafe. Table-driven workflow: pick a table → add menu items →
  /// "Send to kitchen" instead of immediate payment. Pay when the diner asks
  /// for the bill.
  restaurant,
}

extension PosModeX on PosMode {
  String get label => switch (this) {
        PosMode.retail => 'Retail',
        PosMode.supermarket => 'Supermarket',
        PosMode.pharmacy => 'Pharmacy',
        PosMode.wholesale => 'Wholesale',
        PosMode.restaurant => 'Restaurant',
      };

  String get tagline => switch (this) {
        PosMode.retail => 'Tap-to-add tile grid, ~100 active items',
        PosMode.supermarket => 'Barcode-driven scan, 5k+ SKUs',
        PosMode.pharmacy => 'Rx + batch + expiry, customer required',
        PosMode.wholesale => 'Numpad qty + item, account customers',
        PosMode.restaurant => 'Table-driven, menu + send-to-kitchen',
      };

  IconData get icon => switch (this) {
        PosMode.retail => Icons.storefront_outlined,
        PosMode.supermarket => Icons.shopping_cart_outlined,
        PosMode.pharmacy => Icons.local_pharmacy_outlined,
        PosMode.wholesale => Icons.local_shipping_outlined,
        PosMode.restaurant => Icons.restaurant_menu,
      };
}

final modeProvider = StateProvider<PosMode>((_) => PosMode.retail);

// ---------------------------------------------------------------------------
// Restaurant-specific: tables + selected table
// ---------------------------------------------------------------------------

@immutable
class MockTable {
  final String code;
  final String name;
  final int seats;
  final String area;
  const MockTable({required this.code, required this.name, required this.seats, required this.area});
}

const mockTables = <MockTable>[
  MockTable(code: 'T1',  name: 'Table 1',  seats: 2, area: 'Indoor'),
  MockTable(code: 'T2',  name: 'Table 2',  seats: 4, area: 'Indoor'),
  MockTable(code: 'T3',  name: 'Table 3',  seats: 4, area: 'Indoor'),
  MockTable(code: 'T4',  name: 'Table 4',  seats: 6, area: 'Indoor'),
  MockTable(code: 'T5',  name: 'Table 5',  seats: 2, area: 'Window'),
  MockTable(code: 'T6',  name: 'Table 6',  seats: 2, area: 'Window'),
  MockTable(code: 'P1',  name: 'Patio 1',  seats: 4, area: 'Patio'),
  MockTable(code: 'P2',  name: 'Patio 2',  seats: 4, area: 'Patio'),
  MockTable(code: 'B1',  name: 'Bar 1',    seats: 1, area: 'Bar'),
  MockTable(code: 'B2',  name: 'Bar 2',    seats: 1, area: 'Bar'),
  MockTable(code: 'TKO', name: 'Takeaway', seats: 0, area: 'Takeaway'),
];

final selectedTableProvider = StateProvider<MockTable?>((_) => null);

/// Supermarket numpad — cash tendered by the customer. Set by the right-pane
/// numpad, consumed by the Pay button. Resets to 0 after a completed sale.
final tenderedAmountProvider = StateProvider<double>((_) => 0);

// ---------------------------------------------------------------------------
// Pharmacy-specific mock batch info — each line surfaces a batch when in
// pharmacy mode. Other modes ignore.
// ---------------------------------------------------------------------------

@immutable
class MockBatch {
  final String number;
  final DateTime expiry;
  final int qtyOnHand;
  const MockBatch({required this.number, required this.expiry, required this.qtyOnHand});
}

/// Picks a deterministic mock batch for a given item code so the UI shows
/// consistent batches without a real backend.
MockBatch mockBatchFor(String itemCode) {
  final h = itemCode.codeUnits.fold<int>(0, (a, b) => a + b);
  final now = DateTime.now();
  return MockBatch(
    number: 'B${itemCode.replaceAll('-', '')}-${(h % 9 + 1)}',
    expiry: DateTime(now.year, now.month + (h % 18) + 1, 1),
    qtyOnHand: 20 + (h % 80),
  );
}

// ---------------------------------------------------------------------------
// Cashier / till session
// ---------------------------------------------------------------------------

@immutable
class CashierSession {
  final String cashierName;
  final String tillCode;
  final String tillName;
  final String branchName;
  final DateTime openedAt;
  final double openingFloat;
  final String currency;

  const CashierSession({
    required this.cashierName,
    required this.tillCode,
    required this.tillName,
    required this.branchName,
    required this.openedAt,
    required this.openingFloat,
    required this.currency,
  });
}

class SessionNotifier extends Notifier<CashierSession?> {
  @override
  CashierSession? build() => null;

  void open(CashierSession session) => state = session;
  void close() => state = null;
}

final sessionProvider = NotifierProvider<SessionNotifier, CashierSession?>(SessionNotifier.new);

// ---------------------------------------------------------------------------
// Cart
// ---------------------------------------------------------------------------

@immutable
class CartLine {
  final MockItem item;
  final double qty;
  final double discountPct;

  const CartLine({required this.item, required this.qty, this.discountPct = 0});

  double get gross => item.price * qty;
  double get discount => gross * (discountPct / 100);
  double get net => gross - discount;

  CartLine copyWith({double? qty, double? discountPct}) => CartLine(
        item: item,
        qty: qty ?? this.qty,
        discountPct: discountPct ?? this.discountPct,
      );
}

class CartNotifier extends Notifier<List<CartLine>> {
  @override
  List<CartLine> build() => const [];

  void addItem(MockItem item, {double qty = 1}) {
    final existing = state.indexWhere((l) => l.item.code == item.code);
    if (existing >= 0) {
      final line = state[existing];
      final updated = [...state]..[existing] = line.copyWith(qty: line.qty + qty);
      state = updated;
    } else {
      state = [...state, CartLine(item: item, qty: qty)];
    }
  }

  /// Add an item sourced from the live Drift catalog.
  ///
  /// Converts [CatalogItem] to the cart's internal [MockItem] shape using the
  /// synced price from [PriceRows].  This is the canonical add path for the
  /// live sell screens (retail, supermarket, wholesale, pharmacy, restaurant).
  /// Items with [hasPriceRow] == false are rejected — they are not sellable.
  void addCatalogItem(CatalogItem item, {double qty = 1}) {
    if (!item.hasPriceRow) return; // no synced price — not sellable
    final mockItem = MockItem(
      code: item.code,
      barcode: '', // barcodes live in the Barcodes table; empty here is fine
      name: item.name,
      group: '', // item-group not exposed in CatalogItem v1
      price: item.price, // synced price from PriceRows
      uom: 'EA',         // UOM not in schema v1; default
    );
    addItem(mockItem, qty: qty);
  }

  void setQty(int index, double qty) {
    if (qty <= 0) {
      remove(index);
      return;
    }
    final updated = [...state]..[index] = state[index].copyWith(qty: qty);
    state = updated;
  }

  void setDiscount(int index, double pct) {
    final updated = [...state]..[index] = state[index].copyWith(discountPct: pct);
    state = updated;
  }

  void remove(int index) {
    final updated = [...state]..removeAt(index);
    state = updated;
  }

  void clear() => state = const [];

  double get subtotal => state.fold(0, (sum, l) => sum + l.gross);
  double get discountTotal => state.fold(0, (sum, l) => sum + l.discount);
  double get total => state.fold(0, (sum, l) => sum + l.net);
}

final cartProvider = NotifierProvider<CartNotifier, List<CartLine>>(CartNotifier.new);

/// Derived total for convenience in widgets.
final cartTotalProvider = Provider<double>((ref) {
  final lines = ref.watch(cartProvider);
  return lines.fold<double>(0, (sum, l) => sum + l.net);
});

final cartSubtotalProvider = Provider<double>((ref) {
  final lines = ref.watch(cartProvider);
  return lines.fold<double>(0, (sum, l) => sum + l.gross);
});

final cartDiscountProvider = Provider<double>((ref) {
  final lines = ref.watch(cartProvider);
  return lines.fold<double>(0, (sum, l) => sum + l.discount);
});

// ---------------------------------------------------------------------------
// Payment methods (mock)
// ---------------------------------------------------------------------------

enum PaymentMethod { cash, card, mobileMoney, giftCard, voucher }

extension PaymentMethodX on PaymentMethod {
  String get label => switch (this) {
        PaymentMethod.cash => 'Cash',
        PaymentMethod.card => 'Card',
        PaymentMethod.mobileMoney => 'Mobile money',
        PaymentMethod.giftCard => 'Gift card',
        PaymentMethod.voucher => 'Voucher',
      };

  String get icon => switch (this) {
        PaymentMethod.cash => '💵',
        PaymentMethod.card => '💳',
        PaymentMethod.mobileMoney => '📱',
        PaymentMethod.giftCard => '🎁',
        PaymentMethod.voucher => '🧾',
      };
}

// ---------------------------------------------------------------------------
// Mock customers (for the optional customer-pick on a cart)
// ---------------------------------------------------------------------------

@immutable
class MockCustomer {
  final String code;
  final String name;
  final bool walkIn;
  const MockCustomer({required this.code, required this.name, this.walkIn = false});
}

const mockCustomers = <MockCustomer>[
  MockCustomer(code: 'WALKIN', name: 'Walk-in customer', walkIn: true),
  MockCustomer(code: 'CUST0001', name: 'Sarah Nakato'),
  MockCustomer(code: 'CUST0002', name: 'Joseph Mwangi'),
  MockCustomer(code: 'CUST0003', name: 'Amani Hotels Ltd'),
  MockCustomer(code: 'CUST0004', name: 'Kampala Catering Co.'),
];

final selectedCustomerProvider = StateProvider<MockCustomer>((_) => mockCustomers.first);

// ---------------------------------------------------------------------------
// Held carts — cashier parks an in-progress cart so they can serve the next
// customer, then recalls it later by id.
// ---------------------------------------------------------------------------

@immutable
class HeldCart {
  final String id;
  final List<CartLine> lines;
  final MockCustomer customer;
  final DateTime heldAt;
  final String? note;
  const HeldCart({
    required this.id,
    required this.lines,
    required this.customer,
    required this.heldAt,
    this.note,
  });

  int get itemCount => lines.fold<int>(0, (a, l) => a + l.qty.round());
  double get total => lines.fold<double>(0, (a, l) => a + l.net);
}

class HeldCartsNotifier extends Notifier<List<HeldCart>> {
  int _seq = 1;

  @override
  List<HeldCart> build() => const [];

  String hold(List<CartLine> lines, MockCustomer customer, {String? note}) {
    final id = 'H${_seq.toString().padLeft(3, '0')}';
    _seq++;
    state = [
      ...state,
      HeldCart(id: id, lines: lines, customer: customer, heldAt: DateTime.now(), note: note),
    ];
    return id;
  }

  HeldCart? recall(String id) {
    final found = state.where((h) => h.id == id).firstOrNull;
    if (found != null) state = state.where((h) => h.id != id).toList();
    return found;
  }

  void delete(String id) => state = state.where((h) => h.id != id).toList();
}

final heldCartsProvider = NotifierProvider<HeldCartsNotifier, List<HeldCart>>(HeldCartsNotifier.new);

// ---------------------------------------------------------------------------
// Last completed sale — written by the payment / pay-cash flow, consumed by
// the receipt preview screen so the cashier can reprint / email / start over.
// ---------------------------------------------------------------------------

@immutable
class CompletedSale {
  final String receiptNo;
  final List<CartLine> lines;
  final MockCustomer customer;
  final PaymentMethod method;
  final double subtotal;
  final double discount;
  final double total;
  final double tendered;
  final double change;
  final DateTime completedAt;
  final String tillCode;
  final String cashierName;
  final String branchName;
  const CompletedSale({
    required this.receiptNo,
    required this.lines,
    required this.customer,
    required this.method,
    required this.subtotal,
    required this.discount,
    required this.total,
    required this.tendered,
    required this.change,
    required this.completedAt,
    required this.tillCode,
    required this.cashierName,
    required this.branchName,
  });
}

final lastSaleProvider = StateProvider<CompletedSale?>((_) => null);

/// One-shot helper called by the payment flows so receipt-write logic lives
/// in one place. Returns the receipt number it generated.
String recordSale(WidgetRef ref, {
  required PaymentMethod method,
  required double tendered,
}) {
  final lines = ref.read(cartProvider);
  final customer = ref.read(selectedCustomerProvider);
  final session = ref.read(sessionProvider);
  final subtotal = ref.read(cartSubtotalProvider);
  final discount = ref.read(cartDiscountProvider);
  final total = ref.read(cartTotalProvider);
  final receiptNo = 'POS-${DateTime.now().millisecondsSinceEpoch.toString().substring(7)}';
  ref.read(lastSaleProvider.notifier).state = CompletedSale(
    receiptNo: receiptNo,
    lines: List.unmodifiable(lines),
    customer: customer,
    method: method,
    subtotal: subtotal,
    discount: discount,
    total: total,
    tendered: tendered,
    change: tendered - total,
    completedAt: DateTime.now(),
    tillCode: session?.tillCode ?? 'TILL-?',
    cashierName: session?.cashierName ?? 'Cashier',
    branchName: session?.branchName ?? 'Branch',
  );
  return receiptNo;
}

// ---------------------------------------------------------------------------
// Mock past sales — used by the refund / return picker.
// ---------------------------------------------------------------------------

@immutable
class PastSale {
  final String receiptNo;
  final DateTime completedAt;
  final String tillCode;
  final String cashierName;
  final List<CartLine> lines;
  final double total;
  final PaymentMethod method;
  const PastSale({
    required this.receiptNo,
    required this.completedAt,
    required this.tillCode,
    required this.cashierName,
    required this.lines,
    required this.total,
    required this.method,
  });
}

List<PastSale> get mockPastSales {
  final now = DateTime.now();
  CartLine line(MockItem item, double qty) => CartLine(item: item, qty: qty);
  List<PastSale> build(List<({String r, int minsAgo, String till, String cashier, List<CartLine> lines, PaymentMethod m})> seed) {
    return seed
        .map((s) => PastSale(
              receiptNo: s.r,
              completedAt: now.subtract(Duration(minutes: s.minsAgo)),
              tillCode: s.till,
              cashierName: s.cashier,
              lines: s.lines,
              total: s.lines.fold<double>(0, (a, l) => a + l.net),
              method: s.m,
            ))
        .toList();
  }
  return build([
    (r: 'POS-8472103', minsAgo:   4, till: 'TILL-1', cashier: 'Cashier One', lines: [line(mockItems[0], 2), line(mockItems[6], 3)],  m: PaymentMethod.cash),
    (r: 'POS-8472089', minsAgo:  18, till: 'TILL-1', cashier: 'Cashier One', lines: [line(mockItems[13], 1)],                          m: PaymentMethod.card),
    (r: 'POS-8472051', minsAgo:  42, till: 'TILL-2', cashier: 'Cashier Two', lines: [line(mockItems[10], 1), line(mockItems[11], 2)],  m: PaymentMethod.mobileMoney),
    (r: 'POS-8472012', minsAgo:  93, till: 'TILL-1', cashier: 'Cashier One', lines: [line(mockItems[3], 4), line(mockItems[4], 1), line(mockItems[15], 1)], m: PaymentMethod.cash),
    (r: 'POS-8471870', minsAgo: 217, till: 'TILL-3', cashier: 'Cashier Two', lines: [line(mockItems[12], 1)],                          m: PaymentMethod.card),
  ]);
}

// ---------------------------------------------------------------------------
// Formatting
// ---------------------------------------------------------------------------

String money(double amount, [String currency = 'TZS']) {
  final whole = amount.round();
  final s = whole.toString();
  final buf = StringBuffer();
  for (var i = 0; i < s.length; i++) {
    if (i > 0 && (s.length - i) % 3 == 0) buf.write(',');
    buf.write(s[i]);
  }
  return '$currency ${buf.toString()}';
}
