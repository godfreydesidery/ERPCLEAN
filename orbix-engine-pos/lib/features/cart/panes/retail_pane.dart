import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../_demo/mocks.dart';

/// Retail mode — search + category chips + tile grid. The default mode,
/// suitable for stores with ~100 active SKUs that fit on screen.
class RetailPane extends ConsumerStatefulWidget {
  const RetailPane({super.key});

  @override
  ConsumerState<RetailPane> createState() => _RetailPaneState();
}

class _RetailPaneState extends ConsumerState<RetailPane> {
  String _query = '';
  String? _group;
  final _ctrl = TextEditingController();

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  List<MockItem> get _filtered {
    final q = _query.trim().toLowerCase();
    return mockItems.where((i) {
      if (_group != null && i.group != _group) return false;
      if (q.isEmpty) return true;
      return i.code.toLowerCase().contains(q) || i.name.toLowerCase().contains(q);
    }).toList();
  }

  void _scan(String value) {
    final hit = mockItems.firstWhere(
      (i) => i.code.toLowerCase() == value.trim().toLowerCase(),
      orElse: () => mockItems.first,
    );
    ref.read(cartProvider.notifier).addItem(hit);
    _ctrl.clear();
    setState(() => _query = '');
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
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
                  onSubmitted: _scan,
                ),
              ),
              const SizedBox(width: 8),
              IconButton.filledTonal(
                tooltip: 'Hold this cart and start a new one.',
                onPressed: () {},
                icon: const Icon(Icons.pause_circle_outline),
              ),
              IconButton.filledTonal(
                tooltip: 'Recall a previously held cart.',
                onPressed: () {},
                icon: const Icon(Icons.history),
              ),
            ],
          ),
        ),

        // Group chips
        SizedBox(
          height: 44,
          child: ListView(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 12),
            children: [
              FilterChip(
                label: const Text('All'),
                selected: _group == null,
                onSelected: (_) => setState(() => _group = null),
              ),
              const SizedBox(width: 6),
              for (final g in mockItemGroups) ...[
                FilterChip(
                  label: Text(g),
                  selected: _group == g,
                  onSelected: (_) => setState(() => _group = _group == g ? null : g),
                ),
                const SizedBox(width: 6),
              ],
            ],
          ),
        ),

        // Item tiles
        Expanded(
          child: _filtered.isEmpty
              ? _emptyState(theme)
              : GridView.builder(
                  padding: const EdgeInsets.all(12),
                  gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
                    maxCrossAxisExtent: 220,
                    childAspectRatio: 1.4,
                    crossAxisSpacing: 10,
                    mainAxisSpacing: 10,
                  ),
                  itemCount: _filtered.length,
                  itemBuilder: (_, i) => _ItemTile(
                    item: _filtered[i],
                    onTap: () => ref.read(cartProvider.notifier).addItem(_filtered[i]),
                  ),
                ),
        ),
      ],
    );
  }

  Widget _emptyState(ThemeData theme) {
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
  final MockItem item;
  final VoidCallback onTap;
  const _ItemTile({required this.item, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Material(
      color: theme.colorScheme.surface,
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
                  style: theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w600),
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              const SizedBox(height: 4),
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
                  Text('/ ${item.uom}',
                      style: theme.textTheme.labelSmall?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
