import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../features/catalog/catalog_item.dart';
import '../../../features/catalog/catalog_providers.dart';
import '../../_demo/mocks.dart';

/// Pharmacy mode — search + tiles tagged with batch + expiry. Adds a "Rx
/// required" prompt when the cashier picks a prescription item.
///
/// Catalog sourced from local Drift ([catalogItemsProvider]).
class PharmacyPane extends ConsumerStatefulWidget {
  const PharmacyPane({super.key});

  @override
  ConsumerState<PharmacyPane> createState() => _PharmacyPaneState();
}

class _PharmacyPaneState extends ConsumerState<PharmacyPane> {
  String _query = '';
  final _ctrl = TextEditingController();

  // Mock Rx gating — in production this comes from an item attribute.
  bool _isRx(CatalogItem i) => i.code.startsWith('DR-');

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  List<CatalogItem> _filtered(List<CatalogItem> items) {
    final q = _query.trim().toLowerCase();
    if (q.isEmpty) return items;
    return items
        .where((i) => i.code.toLowerCase().contains(q) || i.name.toLowerCase().contains(q))
        .toList();
  }

  Future<void> _addItem(CatalogItem item) async {
    if (!item.hasPriceRow) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('${item.name} has no price — cannot sell')),
      );
      return;
    }
    if (_isRx(item)) {
      final ok = await showDialog<bool>(
        context: context,
        builder: (_) => AlertDialog(
          icon: const Icon(Icons.medical_information, size: 40, color: Colors.deepPurple),
          title: const Text('Prescription required'),
          content: Text(
            '${item.name} requires an Rx number on file. '
            'Confirm you have verified the patient prescription.',
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
            FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Add')),
          ],
        ),
      );
      if (ok != true) return;
    }
    ref.read(cartProvider.notifier).addCatalogItem(item);
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final catalogAsync = ref.watch(catalogItemsProvider);

    return Column(
      children: [
        // Search
        Padding(
          padding: const EdgeInsets.all(12),
          child: TextField(
            controller: _ctrl,
            autofocus: true,
            decoration: InputDecoration(
              hintText: 'Search drug, OTC, or scan barcode…',
              prefixIcon: const Icon(Icons.search),
              border: const OutlineInputBorder(),
              contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 14),
              suffixIcon: _query.isEmpty
                  ? null
                  : IconButton(
                      icon: const Icon(Icons.close),
                      onPressed: () {
                        _ctrl.clear();
                        setState(() => _query = '');
                      },
                    ),
            ),
            onChanged: (v) => setState(() => _query = v),
          ),
        ),

        // Compliance banner
        Container(
          margin: const EdgeInsets.symmetric(horizontal: 12),
          padding: const EdgeInsets.all(10),
          decoration: BoxDecoration(
            color: theme.colorScheme.tertiaryContainer,
            borderRadius: BorderRadius.circular(8),
          ),
          child: Row(
            children: [
              Icon(Icons.health_and_safety, size: 18, color: theme.colorScheme.onTertiaryContainer),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  'Customer required. Rx items prompt for verification. Batch + expiry shown on every line.',
                  style: theme.textTheme.bodySmall
                      ?.copyWith(color: theme.colorScheme.onTertiaryContainer),
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 8),

        // Tiles
        Expanded(
          child: catalogAsync.when(
            loading: () => const Center(child: CircularProgressIndicator()),
            error: (e, _) => Center(
              child: Text('Catalog error: $e',
                  style: TextStyle(color: theme.colorScheme.error)),
            ),
            data: (items) {
              final filtered = _filtered(items);
              if (filtered.isEmpty) {
                return Center(
                  child: Text(
                    items.isEmpty ? 'No catalog — sync required' : 'No items match "$_query"',
                    style: theme.textTheme.bodyMedium
                        ?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                  ),
                );
              }
              return GridView.builder(
                padding: const EdgeInsets.all(12),
                gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
                  maxCrossAxisExtent: 240,
                  childAspectRatio: 1.25,
                  crossAxisSpacing: 10,
                  mainAxisSpacing: 10,
                ),
                itemCount: filtered.length,
                itemBuilder: (_, i) {
                  final item = filtered[i];
                  final batch = mockBatchFor(item.code);
                  final rx = _isRx(item);
                  return Material(
                    color: item.hasPriceRow
                        ? theme.colorScheme.surface
                        : theme.colorScheme.surfaceContainerHighest,
                    borderRadius: BorderRadius.circular(12),
                    child: InkWell(
                      borderRadius: BorderRadius.circular(12),
                      onTap: () => _addItem(item),
                      child: Padding(
                        padding: const EdgeInsets.all(12),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              children: [
                                Container(
                                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                  decoration: BoxDecoration(
                                    color: theme.colorScheme.surfaceContainerHigh,
                                    borderRadius: BorderRadius.circular(4),
                                  ),
                                  child: Text(item.code,
                                      style: theme.textTheme.labelSmall
                                          ?.copyWith(fontFamily: 'monospace')),
                                ),
                                const Spacer(),
                                if (rx)
                                  Container(
                                    padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                    decoration: BoxDecoration(
                                      color: Colors.deepPurple.shade50,
                                      borderRadius: BorderRadius.circular(4),
                                    ),
                                    child: const Text('Rx',
                                        style: TextStyle(
                                            fontSize: 10,
                                            fontWeight: FontWeight.w700,
                                            color: Colors.deepPurple)),
                                  ),
                              ],
                            ),
                            const SizedBox(height: 6),
                            Expanded(
                              child: Text(item.name,
                                  style: theme.textTheme.titleSmall
                                      ?.copyWith(fontWeight: FontWeight.w600),
                                  maxLines: 2,
                                  overflow: TextOverflow.ellipsis),
                            ),
                            const SizedBox(height: 4),
                            if (item.hasPriceRow)
                              Text(money(item.price),
                                  style: theme.textTheme.titleMedium?.copyWith(
                                    color: theme.colorScheme.primary,
                                    fontWeight: FontWeight.w700,
                                  ))
                            else
                              Text('No price',
                                  style: theme.textTheme.labelSmall
                                      ?.copyWith(color: theme.colorScheme.error)),
                            const SizedBox(height: 6),
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 3),
                              decoration: BoxDecoration(
                                color: theme.colorScheme.tertiaryContainer.withValues(alpha: 0.45),
                                borderRadius: BorderRadius.circular(4),
                              ),
                              child: Text(
                                '${batch.number}  exp ${batch.expiry.year}-${batch.expiry.month.toString().padLeft(2, '0')}  ·  ${batch.qtyOnHand} on hand',
                                style: theme.textTheme.labelSmall?.copyWith(
                                  fontFamily: 'monospace',
                                  color: theme.colorScheme.onTertiaryContainer,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  );
                },
              );
            },
          ),
        ),
      ],
    );
  }
}
