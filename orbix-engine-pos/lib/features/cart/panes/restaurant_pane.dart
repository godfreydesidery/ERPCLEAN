import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../features/catalog/catalog_item.dart';
import '../../../features/catalog/catalog_providers.dart';
import '../../_demo/mocks.dart';

/// Restaurant mode — table picker strip across the top, menu grid below.
/// Menu items sourced from local Drift ([catalogItemsProvider]).
class RestaurantPane extends ConsumerStatefulWidget {
  const RestaurantPane({super.key});

  @override
  ConsumerState<RestaurantPane> createState() => _RestaurantPaneState();
}

class _RestaurantPaneState extends ConsumerState<RestaurantPane> {
  String? _area;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final table = ref.watch(selectedTableProvider);
    final catalogAsync = ref.watch(catalogItemsProvider);

    final areas = mockTables.map((t) => t.area).toSet().toList();
    final filteredTables = _area == null
        ? mockTables
        : mockTables.where((t) => t.area == _area).toList();

    return Column(
      children: [
        // Area filter chips (using mock tables — restaurant table management
        // is out of scope for US-POS-017/018)
        Padding(
          padding: const EdgeInsets.fromLTRB(12, 12, 12, 4),
          child: Wrap(
            spacing: 6,
            children: [
              FilterChip(
                label: const Text('All areas'),
                selected: _area == null,
                onSelected: (_) => setState(() => _area = null),
              ),
              for (final a in areas)
                FilterChip(
                  label: Text(a),
                  selected: _area == a,
                  onSelected: (_) => setState(() => _area = _area == a ? null : a),
                ),
            ],
          ),
        ),

        // Tables strip (still mock — table entities not yet in sync schema)
        SizedBox(
          height: 92,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            itemCount: filteredTables.length,
            separatorBuilder: (_, __) => const SizedBox(width: 8),
            itemBuilder: (_, i) {
              final t = filteredTables[i];
              final selected = table?.code == t.code;
              return Material(
                color: selected ? theme.colorScheme.primary : theme.colorScheme.surface,
                borderRadius: BorderRadius.circular(10),
                child: InkWell(
                  borderRadius: BorderRadius.circular(10),
                  onTap: () => ref.read(selectedTableProvider.notifier).state = t,
                  child: Container(
                    width: 96,
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(
                      border: Border.all(
                        color: selected ? theme.colorScheme.primary : theme.dividerColor,
                      ),
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          t.name,
                          style: theme.textTheme.titleSmall?.copyWith(
                            fontWeight: FontWeight.w700,
                            color: selected ? theme.colorScheme.onPrimary : null,
                          ),
                        ),
                        const Spacer(),
                        Row(
                          children: [
                            Icon(Icons.chair_outlined,
                                size: 14,
                                color: selected
                                    ? theme.colorScheme.onPrimary
                                    : theme.colorScheme.onSurfaceVariant),
                            const SizedBox(width: 4),
                            Text(
                              '${t.seats}',
                              style: theme.textTheme.bodySmall?.copyWith(
                                color: selected
                                    ? theme.colorScheme.onPrimary
                                    : theme.colorScheme.onSurfaceVariant,
                              ),
                            ),
                            const Spacer(),
                            if (selected)
                              Icon(Icons.check, size: 16, color: theme.colorScheme.onPrimary),
                          ],
                        ),
                      ],
                    ),
                  ),
                ),
              );
            },
          ),
        ),

        Divider(height: 1, color: theme.dividerColor),

        // Menu grid — live catalog from Drift
        Expanded(
          child: table == null
              ? Center(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(Icons.touch_app, size: 64, color: theme.colorScheme.outline),
                      const SizedBox(height: 12),
                      Text('Pick a table to start a ticket',
                          style: theme.textTheme.titleMedium
                              ?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
                    ],
                  ),
                )
              : catalogAsync.when(
                  loading: () => const Center(child: CircularProgressIndicator()),
                  error: (e, _) => Center(
                    child: Text('Catalog error: $e',
                        style: TextStyle(color: theme.colorScheme.error)),
                  ),
                  data: (items) {
                    if (items.isEmpty) {
                      return Center(
                        child: Text(
                          'No menu items yet — sync required',
                          style: theme.textTheme.bodyMedium?.copyWith(
                              color: theme.colorScheme.onSurfaceVariant),
                        ),
                      );
                    }
                    return GridView.builder(
                      padding: const EdgeInsets.all(12),
                      gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
                        maxCrossAxisExtent: 220,
                        childAspectRatio: 1.5,
                        crossAxisSpacing: 10,
                        mainAxisSpacing: 10,
                      ),
                      itemCount: items.length,
                      itemBuilder: (_, i) {
                        final m = items[i];
                        return Material(
                          color: m.hasPriceRow
                              ? theme.colorScheme.surface
                              : theme.colorScheme.surfaceContainerHighest,
                          borderRadius: BorderRadius.circular(12),
                          child: InkWell(
                            borderRadius: BorderRadius.circular(12),
                            onTap: m.hasPriceRow
                                ? () => ref.read(cartProvider.notifier).addCatalogItem(m)
                                : null,
                            child: Padding(
                              padding: const EdgeInsets.all(12),
                              child: _MenuTile(item: m, theme: theme),
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

class _MenuTile extends StatelessWidget {
  final CatalogItem item;
  final ThemeData theme;
  const _MenuTile({required this.item, required this.theme});

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          item.code,
          style: theme.textTheme.labelSmall
              ?.copyWith(color: theme.colorScheme.onSurfaceVariant, letterSpacing: 0.7),
        ),
        const SizedBox(height: 4),
        Expanded(
          child: Text(
            item.name,
            style: theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w600),
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
          ),
        ),
        if (item.hasPriceRow)
          Text(money(item.price),
              style: theme.textTheme.titleMedium?.copyWith(
                color: theme.colorScheme.primary,
                fontWeight: FontWeight.w700,
              ))
        else
          Text('No price',
              style: theme.textTheme.labelSmall?.copyWith(color: theme.colorScheme.error)),
      ],
    );
  }
}
