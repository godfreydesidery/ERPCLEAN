import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../_demo/mocks.dart';

/// Supermarket-mode right pane. The cart lines already live in the
/// spreadsheet on the left, so this pane is dedicated to a compact numeric
/// pad that enters the customer's cash tendered, shows the change due, and
/// completes the sale.
class SupermarketRightPane extends ConsumerStatefulWidget {
  const SupermarketRightPane({super.key});

  @override
  ConsumerState<SupermarketRightPane> createState() => _SupermarketRightPaneState();
}

class _SupermarketRightPaneState extends ConsumerState<SupermarketRightPane> {
  /// Live keypad buffer — the source of truth while the cashier is typing.
  /// Commits to the provider on every change so the rest of the pane
  /// recomputes change in real time.
  String _buf = '0';

  void _commit() {
    final v = double.tryParse(_buf) ?? 0;
    ref.read(tenderedAmountProvider.notifier).state = v;
  }

  void _press(String d) {
    setState(() => _buf = (_buf == '0') ? d : (_buf.length < 9 ? _buf + d : _buf));
    _commit();
  }

  void _doublePress() {
    setState(() => _buf = (_buf == '0') ? '0' : (_buf.length < 8 ? '${_buf}00' : _buf));
    _commit();
  }

  void _back() {
    setState(() => _buf = _buf.length <= 1 ? '0' : _buf.substring(0, _buf.length - 1));
    _commit();
  }

  void _dot() {
    if (_buf.contains('.')) return;
    setState(() => _buf = '$_buf.');
    _commit();
  }

  void _clear() {
    setState(() => _buf = '0');
    _commit();
  }

  void _quickFill(double amount) {
    final v = amount.toStringAsFixed(0);
    setState(() => _buf = v);
    _commit();
  }

  double _roundUp(double total, double bucket) => ((total / bucket).ceil() * bucket).toDouble();

  Future<void> _payCash(double total, double tendered) async {
    final change = tendered - total;
    await showDialog<void>(
      context: context,
      builder: (_) => AlertDialog(
        icon: const Icon(Icons.check_circle, size: 48, color: Colors.green),
        title: const Text('Payment complete'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text('Paid ${money(tendered)} in cash.'),
            if (change > 0) ...[
              const SizedBox(height: 8),
              Text('Change: ${money(change)}',
                  style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w700)),
            ],
            const SizedBox(height: 12),
            Text(
              'Receipt #POS-${DateTime.now().millisecondsSinceEpoch.toString().substring(7)}',
              style: const TextStyle(color: Colors.grey, fontFamily: 'monospace'),
            ),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text('Next sale')),
        ],
      ),
    );
    ref.read(cartProvider.notifier).clear();
    ref.read(tenderedAmountProvider.notifier).state = 0;
    if (mounted) setState(() => _buf = '0');
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final cart = ref.watch(cartProvider);
    final customer = ref.watch(selectedCustomerProvider);
    final total = ref.watch(cartTotalProvider);
    final tendered = ref.watch(tenderedAmountProvider);
    final change = tendered - total;

    final hasCart = cart.isNotEmpty;
    final cashReady = hasCart && tendered >= total && tendered > 0;

    return Container(
      color: theme.colorScheme.surface,
      child: Column(
        children: [
          _customerHeader(theme, customer),
          Divider(height: 1, color: theme.dividerColor),
          _totalsStrip(theme, total, tendered, change),
          Divider(height: 1, color: theme.dividerColor),
          _quickFillRow(theme, total, hasCart),
          Expanded(child: _numpad(theme)),
          _actionRow(theme, total, tendered, cashReady, hasCart),
        ],
      ),
    );
  }

  // ---------------------------------------------------------------------
  Widget _customerHeader(ThemeData theme, MockCustomer customer) {
    return InkWell(
      onTap: () => _pickCustomer(context, ref),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(12, 8, 12, 8),
        child: Row(
          children: [
            CircleAvatar(
              radius: 11,
              backgroundColor: theme.colorScheme.primaryContainer,
              child: Icon(
                customer.walkIn ? Icons.person_outline : Icons.person,
                size: 13,
                color: theme.colorScheme.onPrimaryContainer,
              ),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: Text(customer.name,
                  style: theme.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.w600),
                  overflow: TextOverflow.ellipsis),
            ),
            Icon(Icons.swap_horiz, size: 14, color: theme.colorScheme.onSurfaceVariant),
          ],
        ),
      ),
    );
  }

  // ---------------------------------------------------------------------
  Widget _totalsStrip(ThemeData theme, double total, double tendered, double change) {
    return Container(
      color: theme.colorScheme.surfaceContainerLow,
      padding: const EdgeInsets.fromLTRB(14, 10, 14, 12),
      child: Column(
        children: [
          _money(theme, 'Total', total,
              valueStyle: theme.textTheme.titleLarge?.copyWith(
                fontWeight: FontWeight.w700,
                color: theme.colorScheme.primary,
              )),
          const SizedBox(height: 4),
          _money(theme, 'Tendered', tendered,
              valueColor: theme.colorScheme.onSurface,
              labelColor: theme.colorScheme.onSurfaceVariant),
          const SizedBox(height: 4),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
            decoration: BoxDecoration(
              color: change >= 0
                  ? theme.colorScheme.surfaceContainerHigh
                  : theme.colorScheme.errorContainer,
              borderRadius: BorderRadius.circular(6),
            ),
            child: Row(
              children: [
                Icon(
                  change >= 0 ? Icons.payments_outlined : Icons.error_outline,
                  size: 14,
                  color: change >= 0 ? theme.colorScheme.primary : theme.colorScheme.error,
                ),
                const SizedBox(width: 6),
                Text(
                  change >= 0 ? 'Change due' : 'Short by',
                  style: theme.textTheme.labelSmall?.copyWith(
                    color: change >= 0 ? theme.colorScheme.primary : theme.colorScheme.error,
                    fontWeight: FontWeight.w600,
                    letterSpacing: 0.4,
                  ),
                ),
                const Spacer(),
                Text(
                  money(change.abs()),
                  style: theme.textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.w700,
                    color: change >= 0 ? theme.colorScheme.primary : theme.colorScheme.error,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _money(ThemeData theme, String label, double amount,
      {TextStyle? valueStyle, Color? valueColor, Color? labelColor}) {
    return Row(
      children: [
        Text(label,
            style: theme.textTheme.bodySmall?.copyWith(color: labelColor ?? theme.colorScheme.onSurfaceVariant)),
        const Spacer(),
        Text(
          money(amount),
          style: valueStyle ?? theme.textTheme.bodyMedium?.copyWith(color: valueColor),
        ),
      ],
    );
  }

  // ---------------------------------------------------------------------
  Widget _quickFillRow(ThemeData theme, double total, bool hasCart) {
    final amounts = <double>{
      if (total > 0) total,
      if (total > 0) _roundUp(total, 5000),
      if (total > 0) _roundUp(total, 10000),
    }.toList();
    if (amounts.isEmpty) return const SizedBox(height: 8);
    return Container(
      padding: const EdgeInsets.fromLTRB(10, 8, 10, 6),
      child: Row(
        children: amounts
            .map(
              (a) => Expanded(
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 3),
                  child: OutlinedButton(
                    onPressed: hasCart ? () => _quickFill(a) : null,
                    style: OutlinedButton.styleFrom(
                      padding: const EdgeInsets.symmetric(vertical: 6),
                      textStyle: const TextStyle(fontSize: 11, fontWeight: FontWeight.w600),
                      minimumSize: const Size(0, 32),
                    ),
                    child: Text(money(a), overflow: TextOverflow.ellipsis),
                  ),
                ),
              ),
            )
            .toList(),
      ),
    );
  }

  // ---------------------------------------------------------------------
  // Comfortable-size pad, fixed width, horizontally centred at the top of
  // the slot. The empty space below is intentional — reserved for future
  // controls.
  Widget _numpad(ThemeData theme) {
    return Align(
      alignment: Alignment.topCenter,
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 240),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 10),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              // Inline mini-controls for ops outside the 3×4 grid.
              Row(
                children: [
                  TextButton.icon(
                    onPressed: _clear,
                    icon: Icon(Icons.close, size: 13, color: theme.colorScheme.error),
                    label: Text('Clear',
                        style: TextStyle(fontSize: 11, color: theme.colorScheme.error)),
                    style: TextButton.styleFrom(
                      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                      minimumSize: const Size(0, 24),
                      visualDensity: VisualDensity.compact,
                    ),
                  ),
                  const Spacer(),
                  TextButton(
                    onPressed: _dot,
                    style: TextButton.styleFrom(
                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 2),
                      minimumSize: const Size(0, 24),
                      visualDensity: VisualDensity.compact,
                    ),
                    child: const Text('.',
                        style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
                  ),
                ],
              ),
              const SizedBox(height: 4),
              GridView.count(
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                crossAxisCount: 3,
                crossAxisSpacing: 5,
                mainAxisSpacing: 5,
                childAspectRatio: 1.4,
                padding: EdgeInsets.zero,
                children: [
                  _key(theme, '7', () => _press('7')),
                  _key(theme, '8', () => _press('8')),
                  _key(theme, '9', () => _press('9')),
                  _key(theme, '4', () => _press('4')),
                  _key(theme, '5', () => _press('5')),
                  _key(theme, '6', () => _press('6')),
                  _key(theme, '1', () => _press('1')),
                  _key(theme, '2', () => _press('2')),
                  _key(theme, '3', () => _press('3')),
                  _key(theme, '00', _doublePress),
                  _key(theme, '0', () => _press('0')),
                  _key(theme, '⌫', _back, tone: _KeyTone.warn),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _key(ThemeData theme, String label, VoidCallback onTap, {_KeyTone tone = _KeyTone.normal}) {
    final fg = tone == _KeyTone.warn ? theme.colorScheme.error : null;
    return FilledButton.tonal(
      onPressed: onTap,
      style: FilledButton.styleFrom(
        padding: EdgeInsets.zero,
        foregroundColor: fg,
        textStyle: const TextStyle(fontSize: 17, fontWeight: FontWeight.w600),
        minimumSize: const Size(0, 0),
      ),
      child: Text(label),
    );
  }

  // ---------------------------------------------------------------------
  Widget _actionRow(
      ThemeData theme, double total, double tendered, bool cashReady, bool hasCart) {
    return Container(
      decoration: BoxDecoration(
        color: theme.colorScheme.surfaceContainerLow,
        border: Border(top: BorderSide(color: theme.dividerColor)),
      ),
      padding: const EdgeInsets.fromLTRB(12, 8, 12, 12),
      child: Row(
        children: [
          Expanded(
            child: OutlinedButton.icon(
              onPressed: hasCart
                  ? () {
                      ref.read(cartProvider.notifier).clear();
                      ref.read(tenderedAmountProvider.notifier).state = 0;
                      setState(() => _buf = '0');
                    }
                  : null,
              style: OutlinedButton.styleFrom(
                padding: const EdgeInsets.symmetric(vertical: 10),
                foregroundColor: theme.colorScheme.error,
              ),
              icon: const Icon(Icons.delete_outline, size: 16),
              label: const Text('Void'),
            ),
          ),
          const SizedBox(width: 6),
          Expanded(
            child: OutlinedButton.icon(
              onPressed: hasCart ? () => context.push('/payment') : null,
              style: OutlinedButton.styleFrom(padding: const EdgeInsets.symmetric(vertical: 10)),
              icon: const Icon(Icons.credit_card, size: 16),
              label: const Text('Other'),
            ),
          ),
          const SizedBox(width: 6),
          Expanded(
            flex: 2,
            child: FilledButton.icon(
              onPressed: cashReady ? () => _payCash(total, tendered) : null,
              style: FilledButton.styleFrom(padding: const EdgeInsets.symmetric(vertical: 10)),
              icon: const Icon(Icons.payments, size: 18),
              label: const Text('Pay cash'),
            ),
          ),
        ],
      ),
    );
  }

  // ---------------------------------------------------------------------
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

enum _KeyTone { normal, warn }
