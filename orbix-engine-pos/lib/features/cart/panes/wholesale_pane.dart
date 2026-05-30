import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../features/catalog/catalog_item.dart';
import '../../../features/catalog/catalog_providers.dart';
import '../../_demo/mocks.dart';

/// Wholesale mode — quantity-first numpad on the left, item picker on the
/// right. Catalog sourced from local Drift ([catalogItemsProvider]).
class WholesalePane extends ConsumerStatefulWidget {
  const WholesalePane({super.key});

  @override
  ConsumerState<WholesalePane> createState() => _WholesalePaneState();
}

class _WholesalePaneState extends ConsumerState<WholesalePane> {
  String _qtyText = '1';
  String _query = '';
  final _searchCtrl = TextEditingController();

  double get _qty => double.tryParse(_qtyText) ?? 1;

  void _press(String d) {
    setState(() {
      if (_qtyText == '0' || _qtyText == '1') {
        _qtyText = d;
      } else if (_qtyText.length < 5) {
        _qtyText += d;
      }
    });
  }

  void _back() {
    setState(() {
      _qtyText = _qtyText.length <= 1 ? '0' : _qtyText.substring(0, _qtyText.length - 1);
    });
  }

  void _add(CatalogItem item) {
    if (!item.hasPriceRow) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('${item.name} has no price — cannot sell')),
      );
      return;
    }
    ref.read(cartProvider.notifier).addCatalogItem(item, qty: _qty);
    setState(() => _qtyText = '1');
  }

  @override
  void dispose() {
    _searchCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final catalogAsync = ref.watch(catalogItemsProvider);

    return catalogAsync.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => Center(
        child: Text('Catalog error: $e',
            style: TextStyle(color: theme.colorScheme.error)),
      ),
      data: (items) {
        final filtered = _query.isEmpty
            ? items
            : items
                .where((i) =>
                    i.code.toLowerCase().contains(_query.toLowerCase()) ||
                    i.name.toLowerCase().contains(_query.toLowerCase()))
                .toList();

        return Row(
          children: [
            // ---- Numpad ----
            SizedBox(
              width: 280,
              child: Container(
                margin: const EdgeInsets.all(12),
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: theme.colorScheme.surface,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Text('Quantity', style: theme.textTheme.titleSmall),
                    const SizedBox(height: 8),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 14),
                      decoration: BoxDecoration(
                        color: theme.colorScheme.surfaceContainerHigh,
                        borderRadius: BorderRadius.circular(10),
                      ),
                      alignment: Alignment.centerRight,
                      child: Text(
                        _qtyText,
                        style: theme.textTheme.displaySmall?.copyWith(
                          fontWeight: FontWeight.w700,
                          color: theme.colorScheme.primary,
                        ),
                      ),
                    ),
                    const SizedBox(height: 12),
                    Expanded(
                      child: GridView.count(
                        crossAxisCount: 3,
                        mainAxisSpacing: 8,
                        crossAxisSpacing: 8,
                        childAspectRatio: 1.4,
                        children: [
                          for (final d in ['7','8','9','4','5','6','1','2','3'])
                            _padKey(theme, d, () => _press(d)),
                          _padKey(theme, '.', () => setState(() {
                            if (!_qtyText.contains('.')) _qtyText += '.';
                          })),
                          _padKey(theme, '0', () => _press('0')),
                          _padKey(theme, '⌫', _back),
                        ],
                      ),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      'Type qty, then tap an item.',
                      style: theme.textTheme.bodySmall
                          ?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                      textAlign: TextAlign.center,
                    ),
                  ],
                ),
              ),
            ),

            // ---- Items + search ----
            Expanded(
              child: Column(
                children: [
                  Padding(
                    padding: const EdgeInsets.fromLTRB(0, 12, 12, 8),
                    child: TextField(
                      controller: _searchCtrl,
                      decoration: const InputDecoration(
                        hintText: 'Filter items…',
                        prefixIcon: Icon(Icons.search),
                        border: OutlineInputBorder(),
                        contentPadding:
                            EdgeInsets.symmetric(horizontal: 12, vertical: 12),
                      ),
                      onChanged: (v) => setState(() => _query = v),
                    ),
                  ),
                  if (filtered.isEmpty)
                    Expanded(
                      child: Center(
                        child: Text(
                          items.isEmpty
                              ? 'No catalog yet — sync required'
                              : 'No items match "$_query"',
                          style: theme.textTheme.bodyMedium?.copyWith(
                              color: theme.colorScheme.onSurfaceVariant),
                        ),
                      ),
                    )
                  else
                    Expanded(
                      child: ListView.separated(
                        padding: const EdgeInsets.fromLTRB(0, 0, 12, 12),
                        itemCount: filtered.length,
                        separatorBuilder: (_, __) => const SizedBox(height: 6),
                        itemBuilder: (_, i) {
                          final item = filtered[i];
                          final lineTotal = item.price * _qty;
                          return Material(
                            color: item.hasPriceRow
                                ? theme.colorScheme.surface
                                : theme.colorScheme.surfaceContainerHighest,
                            borderRadius: BorderRadius.circular(10),
                            child: InkWell(
                              borderRadius: BorderRadius.circular(10),
                              onTap: item.hasPriceRow ? () => _add(item) : null,
                              child: Padding(
                                padding: const EdgeInsets.symmetric(
                                    horizontal: 14, vertical: 12),
                                child: Row(
                                  children: [
                                    Container(
                                      padding: const EdgeInsets.symmetric(
                                          horizontal: 8, vertical: 3),
                                      decoration: BoxDecoration(
                                        color: theme.colorScheme.surfaceContainerHigh,
                                        borderRadius: BorderRadius.circular(4),
                                      ),
                                      child: Text(item.code,
                                          style: theme.textTheme.labelSmall
                                              ?.copyWith(fontFamily: 'monospace')),
                                    ),
                                    const SizedBox(width: 10),
                                    Expanded(
                                      child: Column(
                                        crossAxisAlignment: CrossAxisAlignment.start,
                                        children: [
                                          Text(item.name,
                                              style: theme.textTheme.bodyMedium
                                                  ?.copyWith(fontWeight: FontWeight.w600)),
                                          if (item.hasPriceRow)
                                            Text(
                                              '${money(item.price)} / EA',
                                              style: theme.textTheme.labelSmall?.copyWith(
                                                  color: theme.colorScheme.onSurfaceVariant),
                                            )
                                          else
                                            Text('No price',
                                                style: theme.textTheme.labelSmall?.copyWith(
                                                    color: theme.colorScheme.error)),
                                        ],
                                      ),
                                    ),
                                    if (item.hasPriceRow)
                                      Column(
                                        crossAxisAlignment: CrossAxisAlignment.end,
                                        children: [
                                          Text(
                                            '${_qty == _qty.truncate() ? _qty.toInt() : _qty} × ${money(item.price)}',
                                            style: theme.textTheme.labelSmall?.copyWith(
                                                color: theme.colorScheme.onSurfaceVariant),
                                          ),
                                          Text(
                                            money(lineTotal),
                                            style: theme.textTheme.titleSmall?.copyWith(
                                              fontWeight: FontWeight.w700,
                                              color: theme.colorScheme.primary,
                                            ),
                                          ),
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
                ],
              ),
            ),
          ],
        );
      },
    );
  }

  Widget _padKey(ThemeData theme, String label, VoidCallback onTap) {
    return FilledButton.tonal(
      onPressed: onTap,
      style: FilledButton.styleFrom(textStyle: theme.textTheme.titleLarge),
      child: Text(label),
    );
  }
}
