import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../features/catalog/catalog_item.dart';
import '../../../features/catalog/catalog_providers.dart';
import '../../_demo/mocks.dart';

/// Retail mode — search + category chips + tile grid. The default mode,
/// suitable for stores with ~100 active SKUs that fit on screen.
///
/// Catalog is read from local Drift ([catalogItemsProvider]).  Mock data is
/// NOT used for the live sell path.
class RetailPane extends ConsumerStatefulWidget {
  const RetailPane({super.key});

  @override
  ConsumerState<RetailPane> createState() => _RetailPaneState();
}

class _RetailPaneState extends ConsumerState<RetailPane> {
  String _query = '';
  final _ctrl = TextEditingController();

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  List<CatalogItem> _filtered(List<CatalogItem> items) {
    final q = _query.trim().toLowerCase();
    return items.where((i) {
      if (q.isNotEmpty) {
        if (!i.code.toLowerCase().contains(q) &&
            !i.name.toLowerCase().contains(q)) {
          return false;
        }
      }
      return true;
    }).toList();
  }

  void _scan(String value, List<CatalogItem> items) {
    final v = value.trim().toLowerCase();
    final hit = items.firstWhere(
      (i) => i.code.toLowerCase() == v,
      orElse: () => items.isNotEmpty ? items.first : _noItem,
    );
    if (hit == _noItem) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Item not found: $value')),
      );
    } else {
      ref.read(cartProvider.notifier).addCatalogItem(hit);
    }
    _ctrl.clear();
    setState(() => _query = '');
  }

  // Sentinel — avoids nullable gymnastics in _scan.
  static const _noItem = CatalogItem(
    itemId: -1, code: '', name: '', price: 0, currency: 'TZS',
    hasPriceRow: false, priceListCode: '',
  );

  Future<void> _hold(BuildContext context) async {
    final cart = ref.read(cartProvider);
    if (cart.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Cart is empty — nothing to hold')),
      );
      return;
    }
    final noteCtrl = TextEditingController();
    final ok = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        icon: const Icon(Icons.pause_circle_outline, size: 40),
        title: const Text('Hold cart'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text('Park the current ${cart.length} line${cart.length == 1 ? "" : "s"} '
                'so you can serve the next customer.'),
            const SizedBox(height: 12),
            TextField(
              controller: noteCtrl,
              decoration: const InputDecoration(
                labelText: 'Note (optional)',
                hintText: 'e.g. waiting for ID, gone to fetch wallet',
                border: OutlineInputBorder(),
                isDense: true,
              ),
            ),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Hold')),
        ],
      ),
    );
    if (ok != true) return;
    final id = ref.read(heldCartsProvider.notifier).hold(
          cart,
          ref.read(selectedCustomerProvider),
          note: noteCtrl.text.trim().isEmpty ? null : noteCtrl.text.trim(),
        );
    ref.read(cartProvider.notifier).clear();
    ref.read(selectedCustomerProvider.notifier).state = mockCustomers.first;
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Cart held as $id — recall from the history button')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final catalogAsync = ref.watch(catalogItemsProvider);
    final syncState = ref.watch(catalogSyncStateProvider);

    return Column(
      children: [
        // Search + hold/recall
        Padding(
          padding: const EdgeInsets.all(12),
          child: Row(
            children: [
              Expanded(
                child: TextField(
                  controller: _ctrl,
                  autofocus: true,
                  decoration: InputDecoration(
                    hintText: 'Scan barcode or type item code / name…',
                    prefixIcon: const Icon(Icons.search),
                    suffixIcon: _query.isEmpty
                        ? null
                        : IconButton(
                            icon: const Icon(Icons.close),
                            onPressed: () {
                              _ctrl.clear();
                              setState(() => _query = '');
                            },
                          ),
                    border: const OutlineInputBorder(),
                    contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 14),
                  ),
                  onChanged: (v) => setState(() => _query = v),
                  onSubmitted: (v) => catalogAsync.whenData(
                    (items) => _scan(v, items),
                  ),
                ),
              ),
              const SizedBox(width: 8),
              IconButton.filledTonal(
                tooltip: 'Hold this cart and start a new one.',
                onPressed: () => _hold(context),
                icon: const Icon(Icons.pause_circle_outline),
              ),
              IconButton.filledTonal(
                tooltip: 'Recall a previously held cart.',
                onPressed: () => context.push('/held'),
                icon: const Icon(Icons.history),
              ),
            ],
          ),
        ),

        // Sync loading / error strip
        if (syncState.isLoading)
          Container(
            width: double.infinity,
            color: theme.colorScheme.primaryContainer.withValues(alpha: 0.35),
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
            child: Row(
              children: [
                const SizedBox(
                  width: 14, height: 14,
                  child: CircularProgressIndicator(strokeWidth: 2),
                ),
                const SizedBox(width: 10),
                Text('Syncing catalog…', style: theme.textTheme.bodySmall),
              ],
            ),
          ),
        if (syncState.hasError)
          Container(
            width: double.infinity,
            color: theme.colorScheme.errorContainer.withValues(alpha: 0.45),
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
            child: Row(
              children: [
                Icon(Icons.wifi_off, size: 16, color: theme.colorScheme.error),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    'Catalog sync failed — showing cached data',
                    style: theme.textTheme.bodySmall?.copyWith(
                        color: theme.colorScheme.onErrorContainer),
                  ),
                ),
              ],
            ),
          ),

        // Main content
        catalogAsync.when(
          loading: () => const Expanded(
            child: Center(child: CircularProgressIndicator()),
          ),
          error: (e, _) => Expanded(
            child: Center(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Icon(Icons.error_outline, size: 48, color: Colors.red),
                  const SizedBox(height: 8),
                  Text('Error loading catalog: $e'),
                ],
              ),
            ),
          ),
          data: (items) {
            final filtered = _filtered(items);
            final sellable = filtered.where((i) => i.hasPriceRow).toList();
            final noPrice = filtered.where((i) => !i.hasPriceRow).toList();
            final allShown = [...sellable, ...noPrice];

            return Expanded(
              child: Column(
                children: [
                  // No items at all — first sync not done
                  if (items.isEmpty) ...[
                    Expanded(child: _firstSyncState(theme, syncState.isLoading)),
                  ] else ...[
                    // Item tiles
                    Expanded(
                      child: allShown.isEmpty
                          ? _emptySearchState(theme)
                          : GridView.builder(
                              padding: const EdgeInsets.all(12),
                              gridDelegate:
                                  const SliverGridDelegateWithMaxCrossAxisExtent(
                                maxCrossAxisExtent: 220,
                                childAspectRatio: 1.4,
                                crossAxisSpacing: 10,
                                mainAxisSpacing: 10,
                              ),
                              itemCount: allShown.length,
                              itemBuilder: (_, i) => _ItemTile(
                                item: allShown[i],
                                onTap: allShown[i].hasPriceRow
                                    ? () => ref
                                        .read(cartProvider.notifier)
                                        .addCatalogItem(allShown[i])
                                    : null,
                              ),
                            ),
                    ),
                  ],
                ],
              ),
            );
          },
        ),
      ],
    );
  }

  Widget _firstSyncState(ThemeData theme, bool syncing) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (syncing) ...[
            const CircularProgressIndicator(),
            const SizedBox(height: 16),
            Text('Downloading catalog…', style: theme.textTheme.bodyLarge),
            const SizedBox(height: 8),
            Text('This may take a moment on first run.',
                style: theme.textTheme.bodySmall
                    ?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
          ] else ...[
            const Icon(Icons.inventory_2_outlined, size: 64, color: Colors.grey),
            const SizedBox(height: 12),
            Text('No catalog yet', style: theme.textTheme.titleMedium),
            const SizedBox(height: 6),
            Text(
              'Connect to the network — the catalog will download automatically.',
              style: theme.textTheme.bodySmall
                  ?.copyWith(color: theme.colorScheme.onSurfaceVariant),
              textAlign: TextAlign.center,
            ),
          ],
        ],
      ),
    );
  }

  Widget _emptySearchState(ThemeData theme) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.search_off, size: 64, color: Colors.grey),
          const SizedBox(height: 12),
          Text('No items match "$_query"', style: theme.textTheme.bodyLarge),
        ],
      ),
    );
  }
}

class _ItemTile extends StatelessWidget {
  final CatalogItem item;
  final VoidCallback? onTap;
  const _ItemTile({required this.item, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final sellable = onTap != null;
    return Material(
      color: sellable
          ? theme.colorScheme.surface
          : theme.colorScheme.surfaceContainerHighest,
      borderRadius: BorderRadius.circular(12),
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                decoration: BoxDecoration(
                  color: theme.colorScheme.surfaceContainerHigh,
                  borderRadius: BorderRadius.circular(4),
                ),
                child: Text(item.code,
                    style: theme.textTheme.labelSmall?.copyWith(fontFamily: 'monospace')),
              ),
              const SizedBox(height: 6),
              Expanded(
                child: Text(
                  item.name,
                  style: theme.textTheme.titleSmall?.copyWith(
                    fontWeight: FontWeight.w600,
                    color: sellable ? null : theme.colorScheme.onSurfaceVariant,
                  ),
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              const SizedBox(height: 4),
              if (sellable)
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      money(item.price),
                      style: theme.textTheme.titleMedium?.copyWith(
                        color: theme.colorScheme.primary,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    Text('TZS',
                        style: theme.textTheme.labelSmall?.copyWith(
                            color: theme.colorScheme.onSurfaceVariant)),
                  ],
                )
              else
                Text(
                  'No price',
                  style: theme.textTheme.labelSmall?.copyWith(
                      color: theme.colorScheme.error),
                ),
            ],
          ),
        ),
      ),
    );
  }
}
