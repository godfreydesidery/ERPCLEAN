import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../_demo/mocks.dart';

/// Universal cart pane — appears on the right in every mode. Restaurant mode
/// swaps the "Pay" button for "Send to kitchen" and shows the table.
class CartPane extends ConsumerWidget {
  const CartPane({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final cart = ref.watch(cartProvider);
    final mode = ref.watch(modeProvider);
    final customer = ref.watch(selectedCustomerProvider);
    final subtotal = ref.watch(cartSubtotalProvider);
    final discount = ref.watch(cartDiscountProvider);
    final total = ref.watch(cartTotalProvider);
    final table = ref.watch(selectedTableProvider);

    final customerRequired =
        mode == PosMode.pharmacy || mode == PosMode.wholesale;
    final missingCustomer = customerRequired && customer.walkIn;

    return Container(
      color: theme.colorScheme.surface,
      child: Column(
        children: [
          // Customer or table header
          if (mode == PosMode.restaurant)
            _tableHeader(context, theme, table)
          else
            _customerHeader(context, ref, theme, customer, missingCustomer),

          Divider(height: 1, color: theme.dividerColor),

          // Lines
          Expanded(
            child: cart.isEmpty
                ? _emptyState(theme, mode)
                : ListView.separated(
                    padding: const EdgeInsets.symmetric(vertical: 4),
                    itemCount: cart.length,
                    separatorBuilder: (_, __) =>
                        Divider(height: 1, color: theme.dividerColor.withValues(alpha: 0.4)),
                    itemBuilder: (_, i) => _CartLineRow(index: i, line: cart[i], mode: mode),
                  ),
          ),

          // Totals + action
          Container(
            decoration: BoxDecoration(
              color: theme.colorScheme.surfaceContainerLow,
              border: Border(top: BorderSide(color: theme.dividerColor)),
            ),
            padding: const EdgeInsets.all(16),
            child: Column(
              children: [
                _totalRow(theme, 'Subtotal', money(subtotal)),
                if (discount > 0)
                  _totalRow(theme, 'Discount', '− ${money(discount)}',
                      color: theme.colorScheme.tertiary),
                const SizedBox(height: 6),
                Container(height: 1, color: theme.dividerColor),
                const SizedBox(height: 12),
                Row(
                  children: [
                    Text('Total', style: theme.textTheme.titleMedium),
                    const Spacer(),
                    Text(
                      money(total),
                      style: theme.textTheme.headlineSmall?.copyWith(
                        fontWeight: FontWeight.w700,
                        color: theme.colorScheme.primary,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 16),
                _actionRow(context, ref, theme, cart, mode, missingCustomer, table),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _customerHeader(BuildContext context, WidgetRef ref, ThemeData theme,
      MockCustomer customer, bool missing) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
      child: InkWell(
        borderRadius: BorderRadius.circular(8),
        onTap: () => _pickCustomer(context, ref),
        child: Padding(
          padding: const EdgeInsets.all(8),
          child: Row(
            children: [
              CircleAvatar(
                radius: 16,
                backgroundColor: missing
                    ? theme.colorScheme.errorContainer
                    : theme.colorScheme.primaryContainer,
                child: Icon(
                  customer.walkIn ? Icons.person_outline : Icons.person,
                  size: 18,
                  color: missing
                      ? theme.colorScheme.onErrorContainer
                      : theme.colorScheme.onPrimaryContainer,
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(customer.name,
                        style: theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w600)),
                    Text(
                      missing ? 'Customer required for this mode — tap to pick' : customer.code,
                      style: theme.textTheme.labelSmall?.copyWith(
                        color: missing ? theme.colorScheme.error : theme.colorScheme.onSurfaceVariant,
                        fontFamily: missing ? null : 'monospace',
                      ),
                    ),
                  ],
                ),
              ),
              Icon(Icons.swap_horiz, size: 18, color: theme.colorScheme.onSurfaceVariant),
            ],
          ),
        ),
      ),
    );
  }

  Widget _tableHeader(BuildContext context, ThemeData theme, MockTable? table) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
      child: Container(
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: theme.colorScheme.surfaceContainerHigh,
          borderRadius: BorderRadius.circular(8),
        ),
        child: Row(
          children: [
            Icon(Icons.restaurant_menu, size: 18, color: theme.colorScheme.primary),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    table == null ? 'No table selected' : '${table.name} · ${table.area}',
                    style: theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w600),
                  ),
                  Text(
                    table == null ? 'Pick a table from the left panel' : 'Seats ${table.seats}',
                    style: theme.textTheme.labelSmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _emptyState(ThemeData theme, PosMode mode) {
    final hint = switch (mode) {
      PosMode.retail => 'Tap or search an item to add it',
      PosMode.supermarket => 'Scan a barcode or type item code',
      PosMode.pharmacy => 'Search and add prescriptions or OTC items',
      PosMode.wholesale => 'Enter quantity, then pick an item',
      PosMode.restaurant => 'Pick a table, then tap menu items',
    };
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(
            mode == PosMode.restaurant ? Icons.receipt_long : Icons.shopping_basket_outlined,
            size: 56,
            color: theme.colorScheme.outline,
          ),
          const SizedBox(height: 12),
          Text(
            mode == PosMode.restaurant ? 'No ticket yet' : 'Cart is empty',
            style: theme.textTheme.titleMedium?.copyWith(color: theme.colorScheme.onSurfaceVariant),
          ),
          const SizedBox(height: 4),
          Text(hint, style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.outline)),
        ],
      ),
    );
  }

  Widget _actionRow(
    BuildContext context,
    WidgetRef ref,
    ThemeData theme,
    List<CartLine> cart,
    PosMode mode,
    bool missingCustomer,
    MockTable? table,
  ) {
    if (mode == PosMode.restaurant) {
      final missingTable = table == null;
      return Row(
        children: [
          Expanded(
            child: OutlinedButton.icon(
              onPressed: cart.isEmpty ? null : () => ref.read(cartProvider.notifier).clear(),
              style: OutlinedButton.styleFrom(
                padding: const EdgeInsets.symmetric(vertical: 14),
                foregroundColor: theme.colorScheme.error,
              ),
              icon: const Icon(Icons.delete_outline),
              label: const Text('Void'),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: OutlinedButton.icon(
              onPressed: (cart.isEmpty || missingTable) ? null : () => _sendToKitchen(context),
              style: OutlinedButton.styleFrom(padding: const EdgeInsets.symmetric(vertical: 14)),
              icon: const Icon(Icons.send),
              label: const Text('Send'),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            flex: 2,
            child: FilledButton.icon(
              onPressed: (cart.isEmpty || missingTable) ? null : () => context.push('/payment'),
              style: FilledButton.styleFrom(padding: const EdgeInsets.symmetric(vertical: 14)),
              icon: const Icon(Icons.receipt),
              label: const Text('Bill'),
            ),
          ),
        ],
      );
    }
    return Row(
      children: [
        Expanded(
          child: OutlinedButton.icon(
            onPressed: cart.isEmpty ? null : () => ref.read(cartProvider.notifier).clear(),
            style: OutlinedButton.styleFrom(
              padding: const EdgeInsets.symmetric(vertical: 14),
              foregroundColor: theme.colorScheme.error,
            ),
            icon: const Icon(Icons.delete_outline),
            label: const Text('Void'),
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          flex: 2,
          child: FilledButton.icon(
            onPressed: (cart.isEmpty || missingCustomer) ? null : () => context.push('/payment'),
            style: FilledButton.styleFrom(padding: const EdgeInsets.symmetric(vertical: 14)),
            icon: const Icon(Icons.payments_outlined),
            label: const Text('Pay'),
          ),
        ),
      ],
    );
  }

  void _sendToKitchen(BuildContext context) {
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Row(
          children: [
            Icon(Icons.check_circle, color: Colors.green),
            SizedBox(width: 12),
            Text('Order sent to kitchen — KOT printed'),
          ],
        ),
      ),
    );
  }

  Widget _totalRow(ThemeData theme, String label, String value, {Color? color}) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 2),
      child: Row(
        children: [
          Text(label, style: theme.textTheme.bodyMedium?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
          const Spacer(),
          Text(value, style: theme.textTheme.bodyMedium?.copyWith(color: color)),
        ],
      ),
    );
  }

  Future<void> _pickCustomer(BuildContext context, WidgetRef ref) async {
    final picked = await showDialog<MockCustomer>(
      context: context,
      builder: (_) => SimpleDialog(
        title: const Text('Pick customer'),
        children: [
          for (final c in mockCustomers)
            SimpleDialogOption(
              onPressed: () => Navigator.pop(context, c),
              child: Row(
                children: [
                  Icon(c.walkIn ? Icons.person_outline : Icons.person, size: 18),
                  const SizedBox(width: 8),
                  Text(c.name),
                  const Spacer(),
                  Text(c.code,
                      style: const TextStyle(
                          fontFamily: 'monospace', fontSize: 12, color: Colors.grey)),
                ],
              ),
            ),
        ],
      ),
    );
    if (picked != null) ref.read(selectedCustomerProvider.notifier).state = picked;
  }
}

class _CartLineRow extends ConsumerWidget {
  final int index;
  final CartLine line;
  final PosMode mode;
  const _CartLineRow({required this.index, required this.line, required this.mode});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final batch = mode == PosMode.pharmacy ? mockBatchFor(line.item.code) : null;
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(line.item.name,
                        style: theme.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.w600)),
                    Text(
                      '${line.item.code} · ${money(line.item.price)} / ${line.item.uom}',
                      style: theme.textTheme.labelSmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                    ),
                    if (line.discountPct > 0)
                      Padding(
                        padding: const EdgeInsets.only(top: 4),
                        child: Container(
                          padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                          decoration: BoxDecoration(
                            color: theme.colorScheme.tertiaryContainer,
                            borderRadius: BorderRadius.circular(4),
                          ),
                          child: Text(
                            '${line.discountPct.toStringAsFixed(0)}% off  ·  −${money(line.discount)}',
                            style: theme.textTheme.labelSmall?.copyWith(
                              color: theme.colorScheme.onTertiaryContainer,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ),
                      ),
                    if (batch != null)
                      Padding(
                        padding: const EdgeInsets.only(top: 4),
                        child: Row(
                          children: [
                            Icon(Icons.label_outline, size: 12, color: theme.colorScheme.tertiary),
                            const SizedBox(width: 4),
                            Text(
                              '${batch.number}  exp ${batch.expiry.year}-${batch.expiry.month.toString().padLeft(2, '0')}',
                              style: theme.textTheme.labelSmall?.copyWith(
                                color: theme.colorScheme.tertiary,
                                fontFamily: 'monospace',
                              ),
                            ),
                          ],
                        ),
                      ),
                  ],
                ),
              ),
              IconButton(
                tooltip: 'Apply / change discount on this line',
                visualDensity: VisualDensity.compact,
                onPressed: () => _showDiscountDialog(context, ref, index, line),
                icon: const Icon(Icons.percent, size: 18),
              ),
              IconButton(
                tooltip: 'Remove',
                visualDensity: VisualDensity.compact,
                onPressed: () => ref.read(cartProvider.notifier).remove(index),
                icon: const Icon(Icons.close, size: 18),
              ),
            ],
          ),
          const SizedBox(height: 4),
          Row(
            children: [
              _QtyStepper(index: index, line: line),
              const Spacer(),
              Text(
                money(line.net),
                style: theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

Future<void> _showDiscountDialog(BuildContext context, WidgetRef ref, int index, CartLine line) async {
  final ctrl = TextEditingController(text: line.discountPct == 0 ? '' : line.discountPct.toStringAsFixed(0));
  final preset = [0.0, 5.0, 10.0, 15.0, 20.0];
  double current = line.discountPct;
  final ok = await showDialog<double>(
    context: context,
    builder: (_) => StatefulBuilder(builder: (ctx, setLocal) {
      final theme = Theme.of(ctx);
      final newNet = line.gross * (1 - current / 100);
      return AlertDialog(
        icon: const Icon(Icons.percent, size: 36),
        title: Text('Discount — ${line.item.name}'),
        content: SizedBox(
          width: 360,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text('Line total before discount: ${money(line.gross)}',
                  style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
              const SizedBox(height: 14),
              TextField(
                controller: ctrl,
                keyboardType: TextInputType.number,
                autofocus: true,
                decoration: const InputDecoration(
                  labelText: 'Discount %',
                  suffixText: '%',
                  border: OutlineInputBorder(),
                  isDense: true,
                ),
                onChanged: (v) {
                  final n = double.tryParse(v) ?? 0;
                  setLocal(() => current = n.clamp(0, 100).toDouble());
                },
              ),
              const SizedBox(height: 10),
              Wrap(
                spacing: 6,
                children: preset.map((p) => ChoiceChip(
                  label: Text(p == 0 ? 'None' : '${p.toStringAsFixed(0)}%'),
                  selected: current == p,
                  onSelected: (_) {
                    ctrl.text = p == 0 ? '' : p.toStringAsFixed(0);
                    setLocal(() => current = p);
                  },
                )).toList(),
              ),
              const SizedBox(height: 14),
              Container(
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: theme.colorScheme.surfaceContainerHigh,
                  borderRadius: BorderRadius.circular(6),
                ),
                child: Row(
                  children: [
                    Text('New line total', style: theme.textTheme.bodyMedium),
                    const Spacer(),
                    Text(money(newNet),
                        style: theme.textTheme.titleMedium?.copyWith(
                          color: theme.colorScheme.primary,
                          fontWeight: FontWeight.w700,
                        )),
                  ],
                ),
              ),
              if (current >= 15) ...[
                const SizedBox(height: 10),
                Row(
                  children: [
                    Icon(Icons.shield_outlined, size: 14, color: theme.colorScheme.error),
                    const SizedBox(width: 6),
                    Expanded(
                      child: Text(
                        'Discounts ≥ 15% require supervisor authorisation in production.',
                        style: theme.textTheme.labelSmall?.copyWith(color: theme.colorScheme.error),
                      ),
                    ),
                  ],
                ),
              ],
            ],
          ),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(ctx, current), child: const Text('Apply')),
        ],
      );
    }),
  );
  if (ok != null) {
    ref.read(cartProvider.notifier).setDiscount(index, ok);
  }
}

class _QtyStepper extends ConsumerWidget {
  final int index;
  final CartLine line;
  const _QtyStepper({required this.index, required this.line});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    return Container(
      decoration: BoxDecoration(
        border: Border.all(color: theme.dividerColor),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        children: [
          InkWell(
            borderRadius: const BorderRadius.horizontal(left: Radius.circular(8)),
            onTap: () => ref.read(cartProvider.notifier).setQty(index, line.qty - 1),
            child: const Padding(padding: EdgeInsets.all(6), child: Icon(Icons.remove, size: 16)),
          ),
          Container(
            width: 44,
            alignment: Alignment.center,
            child: Text(
              line.qty == line.qty.truncate() ? line.qty.toInt().toString() : line.qty.toString(),
              style: theme.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.w600),
            ),
          ),
          InkWell(
            borderRadius: const BorderRadius.horizontal(right: Radius.circular(8)),
            onTap: () => ref.read(cartProvider.notifier).setQty(index, line.qty + 1),
            child: const Padding(padding: EdgeInsets.all(6), child: Icon(Icons.add, size: 16)),
          ),
        ],
      ),
    );
  }
}
