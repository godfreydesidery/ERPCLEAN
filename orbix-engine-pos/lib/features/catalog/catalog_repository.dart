/// CatalogRepository — streams active catalog items joined to their sell price.
///
/// Reads entirely from local Drift tables ([Items] + [PriceRows]).  No network
/// calls here — the sync pipeline populates both tables; this just queries.
///
/// Price-list selection priority (no server config knob wired yet):
///   1. Rows whose [priceListCode] == 'DEFAULT'  (the canonical retail list).
///   2. Any other row for that item (first by ascending id).
/// If no price row exists at all, [CatalogItem.hasPriceRow] is false and the
/// item is shown as non-sellable.  This is a hard offline-safe invariant.
library;

import 'package:drift/drift.dart';
import 'package:logger/logger.dart';

import '../../data/local/database.dart';
import 'catalog_item.dart';

/// Preferred price-list code.  When the backend ships a config endpoint we
/// can replace this constant; for now 'DEFAULT' is the agreed-upon retail list.
const _preferredPriceList = 'DEFAULT';

class CatalogRepository {
  CatalogRepository({required PosDatabase db, Logger? logger})
      : _db = db,
        _log = logger ?? Logger();

  final PosDatabase _db;
  final Logger _log;

  /// Stream of all ACTIVE catalog items joined to their best price.
  ///
  /// Emits a new list whenever [Items] or [PriceRows] change in Drift.
  /// The list is sorted: sellable items (hasPriceRow=true) first, then
  /// alphabetically by name within each group.
  Stream<List<CatalogItem>> watchCatalog() {
    // Drift watch on Items; requery PriceRows on every emission.
    // We use a custom SELECT rather than the generated join helpers because
    // we need LEFT JOIN semantics (items with no price row must appear).
    final itemsQuery = _db.select(_db.items)
      ..where((t) => t.isActive.equals(true))
      ..orderBy([(t) => OrderingTerm.asc(t.name)]);

    return itemsQuery.watch().asyncMap((items) async {
      if (items.isEmpty) return const <CatalogItem>[];
      return _joinPrices(items);
    });
  }

  /// One-shot fetch — used by the CartNotifier when it needs a single item
  /// (e.g. scan lookup by code or barcode).
  Future<CatalogItem?> findByCode(String code) async {
    final rows = await (_db.select(_db.items)
          ..where((t) => t.code.equals(code) & t.isActive.equals(true))
          ..limit(1))
        .get();
    if (rows.isEmpty) return null;
    final joined = await _joinPrices(rows);
    return joined.firstOrNull;
  }

  /// Lookup by barcode — scans the [Barcodes] table first, then joins.
  Future<CatalogItem?> findByBarcode(String barcode) async {
    final barcodeRows = await (_db.select(_db.barcodes)
          ..where((t) => t.barcode.equals(barcode))
          ..limit(1))
        .get();
    if (barcodeRows.isEmpty) return null;
    final itemId = barcodeRows.first.itemId;
    final itemRows = await (_db.select(_db.items)
          ..where((t) => t.id.equals(itemId) & t.isActive.equals(true))
          ..limit(1))
        .get();
    if (itemRows.isEmpty) return null;
    final joined = await _joinPrices(itemRows);
    return joined.firstOrNull;
  }

  // ---------------------------------------------------------------------------
  // Internals
  // ---------------------------------------------------------------------------

  Future<List<CatalogItem>> _joinPrices(List<Item> items) async {
    if (items.isEmpty) return const [];

    final itemIds = items.map((i) => i.id).toList();

    // Fetch all price rows for these items in one query.
    final priceRows = await (_db.select(_db.priceRows)
          ..where((t) => t.itemId.isIn(itemIds)))
        .get();

    // Build a map: itemId → best PriceRow.
    // Priority: DEFAULT list first, else lowest id row.
    final bestPrice = <int, PriceRow>{};
    for (final row in priceRows) {
      final existing = bestPrice[row.itemId];
      if (existing == null) {
        bestPrice[row.itemId] = row;
      } else if (row.priceListCode == _preferredPriceList &&
          existing.priceListCode != _preferredPriceList) {
        bestPrice[row.itemId] = row;
      } else if (row.priceListCode != _preferredPriceList &&
          existing.priceListCode != _preferredPriceList &&
          row.id < existing.id) {
        bestPrice[row.itemId] = row;
      }
    }

    final result = <CatalogItem>[];
    for (final item in items) {
      final pr = bestPrice[item.id];
      result.add(CatalogItem(
        itemId: item.id,
        uid: item.uid,
        code: item.code,
        name: item.name,
        shortName: item.shortName,
        price: pr?.price ?? 0.0,
        currency: pr?.currency ?? 'TZS',
        hasPriceRow: pr != null,
        priceListCode: pr?.priceListCode ?? '',
      ));
    }

    _log.d('CatalogRepository._joinPrices items=${result.length} '
        'withPrice=${result.where((i) => i.hasPriceRow).length}');

    // Sellable items first, then by name.
    result.sort((a, b) {
      if (a.hasPriceRow != b.hasPriceRow) {
        return a.hasPriceRow ? -1 : 1;
      }
      return a.name.compareTo(b.name);
    });
    return result;
  }
}
