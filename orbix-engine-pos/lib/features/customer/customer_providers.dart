/// Customer providers — Drift-backed customer selection for the cart.
///
/// Replaces the mock [selectedCustomerProvider] from mocks.dart.
/// The [selectedPosCustomerProvider] is the single source of truth for
/// which customer is attached to the current cart / sale.
///
/// Defaults to walk-in (id=0, isWalkIn=true).  When the cashier picks a
/// named customer the id is resolved to the Drift Customers row so the
/// POS_SALE payload carries the correct server-side customerId.
///
/// The Drift Customers table is populated by the sync pull.  If no customers
/// have been pulled yet the picker shows only Walk-in, which is always safe.
library;

import 'package:drift/drift.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/core_providers.dart';
import '../../data/local/database.dart';
import '../../data/sync/sync_providers.dart' show posDatabaseProvider;

// ---------------------------------------------------------------------------
// PosCustomer — view model for customer picker and sale payload
// ---------------------------------------------------------------------------

@immutable
class PosCustomer {
  const PosCustomer({
    required this.id,
    required this.code,
    required this.name,
    required this.isWalkIn,
  });

  /// Server-side customer id (from Drift Customers.id).  0 = walk-in / unresolved.
  final int id;
  final String code;
  final String name;
  final bool isWalkIn;

  /// Walk-in sentinel — used as default when no customer has been selected.
  static const walkIn = PosCustomer(
    id: 0,
    code: 'WALKIN',
    name: 'Walk-in customer',
    isWalkIn: true,
  );
}

// ---------------------------------------------------------------------------
// Selected customer state
// ---------------------------------------------------------------------------

final selectedPosCustomerProvider =
    StateProvider<PosCustomer>((_) => PosCustomer.walkIn);

// ---------------------------------------------------------------------------
// Customer search — queries Drift Customers by name/code substring
// ---------------------------------------------------------------------------

/// Searches local Drift Customers for rows matching [query].
/// Returns at most 20 results.  Always prepends Walk-in.
final customerSearchQueryProvider = StateProvider<String>((_) => '');

final customerSearchResultsProvider =
    FutureProvider.autoDispose<List<PosCustomer>>((ref) async {
  final db = ref.watch(posDatabaseProvider);
  final query = ref.watch(customerSearchQueryProvider).trim().toLowerCase();

  final dbQuery = db.select(db.customers)
    ..where((t) => t.isActive.equals(true))
    ..orderBy([(t) => OrderingTerm.asc(t.name)])
    ..limit(20);

  final rows = await dbQuery.get();

  final filtered = query.isEmpty
      ? rows
      : rows
          .where((r) =>
              r.name.toLowerCase().contains(query) ||
              r.code.toLowerCase().contains(query))
          .toList();

  return [
    PosCustomer.walkIn,
    ...filtered.map((r) => PosCustomer(
          id: r.id,
          code: r.code,
          name: r.name,
          isWalkIn: r.isWalkIn,
        )),
  ];
});
