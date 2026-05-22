import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../_demo/mocks.dart';

/// Drawer of parked carts. The cashier presses "Hold" on the cart pane,
/// which pushes the current cart here; "Recall" opens this list, lets them
/// pick one, and swaps it back into the active cart.
class HeldCartsScreen extends ConsumerWidget {
  const HeldCartsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final held = ref.watch(heldCartsProvider);
    final currentCart = ref.watch(cartProvider);

    return Scaffold(
      backgroundColor: theme.colorScheme.surfaceContainerLow,
      appBar: AppBar(
        title: const Text('Held carts'),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () => context.pop(),
        ),
      ),
      body: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 760),
          child: held.isEmpty
              ? _empty(context, theme, currentCart.isEmpty)
              : ListView.separated(
                  padding: const EdgeInsets.all(20),
                  itemCount: held.length,
                  separatorBuilder: (_, __) => const SizedBox(height: 10),
                  itemBuilder: (_, i) => _HeldRow(held: held[i]),
                ),
        ),
      ),
    );
  }

  Widget _empty(BuildContext context, ThemeData theme, bool currentEmpty) {
    return Padding(
      padding: const EdgeInsets.all(32),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.pause_circle_outline, size: 72, color: theme.colorScheme.outline),
          const SizedBox(height: 12),
          Text('No held carts', style: theme.textTheme.titleLarge),
          const SizedBox(height: 6),
          Text(
            currentEmpty
                ? 'When you press Hold on a cart, it appears here.'
                : 'Press Hold on the current cart to park it for later.',
            style: theme.textTheme.bodyMedium?.copyWith(color: theme.colorScheme.onSurfaceVariant),
            textAlign: TextAlign.center,
          ),
        ],
      ),
    );
  }
}

class _HeldRow extends ConsumerWidget {
  final HeldCart held;
  const _HeldRow({required this.held});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final age = DateTime.now().difference(held.heldAt);
    final ageText = age.inMinutes < 1
        ? 'just now'
        : age.inHours < 1
            ? '${age.inMinutes} min ago'
            : '${age.inHours}h ${age.inMinutes % 60}m ago';

    return Material(
      color: theme.colorScheme.surface,
      borderRadius: BorderRadius.circular(10),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 12, 12, 12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                    color: theme.colorScheme.primaryContainer,
                    borderRadius: BorderRadius.circular(4),
                  ),
                  child: Text(
                    held.id,
                    style: theme.textTheme.labelLarge?.copyWith(
                      fontFamily: 'monospace',
                      color: theme.colorScheme.onPrimaryContainer,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(held.customer.name,
                          style: theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w600)),
                      Text('Held $ageText  ·  ${held.lines.length} lines  ·  ${held.itemCount} items',
                          style: theme.textTheme.labelSmall?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
                    ],
                  ),
                ),
                Text(
                  money(held.total),
                  style: theme.textTheme.titleMedium?.copyWith(
                    color: theme.colorScheme.primary,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ],
            ),
            if (held.note != null && held.note!.isNotEmpty) ...[
              const SizedBox(height: 8),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                decoration: BoxDecoration(
                  color: theme.colorScheme.tertiaryContainer.withValues(alpha: 0.35),
                  borderRadius: BorderRadius.circular(4),
                ),
                child: Text('Note: ${held.note}',
                    style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onTertiaryContainer)),
              ),
            ],
            const SizedBox(height: 8),
            // Quick line preview
            Wrap(
              spacing: 6, runSpacing: 4,
              children: <Widget>[
                for (final l in held.lines.take(4))
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                    decoration: BoxDecoration(
                      color: theme.colorScheme.surfaceContainerHigh,
                      borderRadius: BorderRadius.circular(4),
                    ),
                    child: Text(
                      '${l.qty == l.qty.truncate() ? l.qty.toInt() : l.qty}× ${l.item.name}',
                      style: theme.textTheme.labelSmall,
                    ),
                  ),
                if (held.lines.length > 4)
                  Text('+${held.lines.length - 4} more',
                      style: theme.textTheme.labelSmall?.copyWith(color: theme.colorScheme.outline)),
              ],
            ),
            const SizedBox(height: 10),
            Row(
              children: [
                TextButton.icon(
                  onPressed: () => _delete(context, ref),
                  icon: Icon(Icons.delete_outline, size: 16, color: theme.colorScheme.error),
                  label: Text('Delete', style: TextStyle(color: theme.colorScheme.error)),
                ),
                const Spacer(),
                FilledButton.icon(
                  onPressed: () => _recall(context, ref),
                  icon: const Icon(Icons.replay, size: 16),
                  label: const Text('Recall to cart'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _recall(BuildContext context, WidgetRef ref) async {
    final currentCart = ref.read(cartProvider);
    if (currentCart.isNotEmpty) {
      final discard = await showDialog<bool>(
        context: context,
        builder: (_) => AlertDialog(
          icon: const Icon(Icons.warning_amber, size: 40, color: Colors.orange),
          title: const Text('Current cart will be replaced'),
          content: Text(
            'The active cart has ${currentCart.length} line${currentCart.length == 1 ? "" : "s"} '
            'that has not been paid yet. Hold the active cart first, or discard it to recall ${held.id}.',
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
            FilledButton(
              onPressed: () => Navigator.pop(context, true),
              style: FilledButton.styleFrom(backgroundColor: Colors.orange),
              child: const Text('Discard & recall'),
            ),
          ],
        ),
      );
      if (discard != true) return;
    }
    final recalled = ref.read(heldCartsProvider.notifier).recall(held.id);
    if (recalled == null) return;
    final cartNotifier = ref.read(cartProvider.notifier);
    cartNotifier.clear();
    for (final line in recalled.lines) {
      cartNotifier.addItem(line.item, qty: line.qty);
    }
    ref.read(selectedCustomerProvider.notifier).state = recalled.customer;
    if (context.mounted) context.go('/cart');
  }

  Future<void> _delete(BuildContext context, WidgetRef ref) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        title: Text('Delete ${held.id}?'),
        content: Text('Cart with ${held.lines.length} line${held.lines.length == 1 ? "" : "s"} '
            '(${money(held.total)}) will be discarded.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            style: FilledButton.styleFrom(backgroundColor: Theme.of(context).colorScheme.error),
            child: const Text('Delete'),
          ),
        ],
      ),
    );
    if (ok == true) {
      ref.read(heldCartsProvider.notifier).delete(held.id);
    }
  }
}
