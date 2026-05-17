import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../_demo/mocks.dart';

/// Pharmacy mode — search + tiles tagged with batch + expiry. Adds a "Rx
/// required" prompt when the cashier picks a prescription item (mock: items
/// whose code starts with 'DR-' are treated as Rx-requiring for demo).
class PharmacyPane extends ConsumerStatefulWidget {
  const PharmacyPane({super.key});

  @override
  ConsumerState<PharmacyPane> createState() => _PharmacyPaneState();
}

class _PharmacyPaneState extends ConsumerState<PharmacyPane> {
  String _query = '';
  final _ctrl = TextEditingController();

  bool _isRx(MockItem i) => i.code.startsWith('DR-'); // mock: dairy stands in as Rx

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  List<MockItem> get _filtered {
    final q = _query.trim().toLowerCase();
    if (q.isEmpty) return mockItems;
    return mockItems
        .where((i) => i.code.toLowerCase().contains(q) || i.name.toLowerCase().contains(q))
        .toList();
  }

  Future<void> _addItem(MockItem item) async {
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
    ref.read(cartProvider.notifier).addItem(item);
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
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
                  style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onTertiaryContainer),
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 8),

        // Tiles
        Expanded(
          child: GridView.builder(
            padding: const EdgeInsets.all(12),
            gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
              maxCrossAxisExtent: 240,
              childAspectRatio: 1.25,
              crossAxisSpacing: 10,
              mainAxisSpacing: 10,
            ),
            itemCount: _filtered.length,
            itemBuilder: (_, i) {
              final item = _filtered[i];
              final batch = mockBatchFor(item.code);
              final rx = _isRx(item);
              return Material(
                color: theme.colorScheme.surface,
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
                                  style: theme.textTheme.labelSmall?.copyWith(fontFamily: 'monospace')),
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
                                        fontSize: 10, fontWeight: FontWeight.w700, color: Colors.deepPurple)),
                              ),
                          ],
                        ),
                        const SizedBox(height: 6),
                        Expanded(
                          child: Text(item.name,
                              style: theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w600),
                              maxLines: 2,
                              overflow: TextOverflow.ellipsis),
                        ),
                        const SizedBox(height: 4),
                        Text(money(item.price),
                            style: theme.textTheme.titleMedium?.copyWith(
                              color: theme.colorScheme.primary,
                              fontWeight: FontWeight.w700,
                            )),
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
          ),
        ),
      ],
    );
  }
}
