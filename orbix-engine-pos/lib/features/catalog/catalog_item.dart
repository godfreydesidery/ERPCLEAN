/// CatalogItem — the view model the sell-screen consumes.
///
/// Produced by [CatalogRepository] as a JOIN of the local [Items] +
/// [PriceRows] Drift tables.  All prices are from the synced price rows;
/// [hasPriceRow] is false when no row exists yet (item shown as non-sellable).
library;

import 'package:flutter/foundation.dart';

@immutable
class CatalogItem {
  const CatalogItem({
    required this.itemId,
    this.uid,
    required this.code,
    required this.name,
    this.shortName,
    required this.price,
    required this.currency,
    required this.hasPriceRow,
    required this.priceListCode,
  });

  final int itemId;
  final String? uid;
  final String code;
  final String name;
  final String? shortName;

  /// Sell price from [PriceRows].  Zero when [hasPriceRow] is false.
  final double price;

  /// ISO currency code — 'TZS' by default.
  final String currency;

  /// False → item exists in catalog but has no synced price yet; treat as
  /// not-sellable in the UI rather than using a zero / hardcoded price.
  final bool hasPriceRow;

  /// The price-list code that produced [price].
  final String priceListCode;

  /// Display name prefers shortName when available.
  String get displayName => shortName?.isNotEmpty == true ? shortName! : name;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is CatalogItem &&
          runtimeType == other.runtimeType &&
          itemId == other.itemId &&
          priceListCode == other.priceListCode;

  @override
  int get hashCode => Object.hash(itemId, priceListCode);
}
