import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../_demo/mocks.dart';

/// Receipt preview shown after a successful payment. Paper-tape look on the
/// left, action panel (reprint / email / next sale) on the right. Reads the
/// just-recorded sale from [lastSaleProvider].
class ReceiptScreen extends ConsumerWidget {
  const ReceiptScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final sale = ref.watch(lastSaleProvider);
    if (sale == null) {
      // No sale in memory — sent here directly without paying. Bounce back.
      WidgetsBinding.instance.addPostFrameCallback((_) => context.go('/cart'));
      return const Scaffold(body: SizedBox.shrink());
    }

    return Scaffold(
      backgroundColor: theme.colorScheme.surfaceContainerLow,
      appBar: AppBar(
        title: const Text('Receipt'),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () => context.go('/cart'),
        ),
      ),
      body: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 900),
          child: Padding(
            padding: const EdgeInsets.all(20),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(flex: 4, child: _PaperReceipt(sale: sale)),
                const SizedBox(width: 20),
                Expanded(flex: 3, child: _ActionPanel(sale: sale)),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _PaperReceipt extends StatelessWidget {
  final CompletedSale sale;
  const _PaperReceipt({required this.sale});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      decoration: BoxDecoration(
        color: const Color(0xFFFFFDF7),
        borderRadius: BorderRadius.circular(8),
        boxShadow: const [BoxShadow(color: Colors.black12, blurRadius: 12, offset: Offset(0, 6))],
      ),
      padding: const EdgeInsets.fromLTRB(24, 28, 24, 28),
      child: DefaultTextStyle(
        style: const TextStyle(fontFamily: 'monospace', fontSize: 12.5, color: Colors.black87, height: 1.4),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Center(
              child: Column(children: [
                Text('ORBIX ENGINE',
                    style: theme.textTheme.titleMedium?.copyWith(
                      fontFamily: 'monospace',
                      color: Colors.black,
                      fontWeight: FontWeight.w800,
                      letterSpacing: 2,
                    )),
                Text(sale.branchName),
                Text('${sale.tillCode}  ·  ${sale.cashierName}'),
              ]),
            ),
            const SizedBox(height: 10),
            const Text('--------------------------------'),
            Text('Receipt #${sale.receiptNo}'),
            Text(_fmt(sale.completedAt)),
            Text('Customer: ${sale.customer.name}'),
            const Text('--------------------------------'),
            const SizedBox(height: 4),
            ...sale.lines.map((l) => Padding(
                  padding: const EdgeInsets.symmetric(vertical: 2),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(l.item.name),
                      Row(
                        children: [
                          Text('  ${l.qty == l.qty.truncate() ? l.qty.toInt() : l.qty} '
                              '× ${money(l.item.price).replaceAll("TZS ", "")}'),
                          const Spacer(),
                          Text(money(l.net).replaceAll('TZS ', '')),
                        ],
                      ),
                      if (l.discount > 0)
                        Row(
                          children: [
                            Text('  disc ${l.discountPct.toStringAsFixed(0)}%'),
                            const Spacer(),
                            Text('-${money(l.discount).replaceAll("TZS ", "")}'),
                          ],
                        ),
                    ],
                  ),
                )),
            const SizedBox(height: 4),
            const Text('--------------------------------'),
            _line('Subtotal', money(sale.subtotal).replaceAll('TZS ', '')),
            if (sale.discount > 0) _line('Discount', '-${money(sale.discount).replaceAll("TZS ", "")}'),
            _line('TOTAL', money(sale.total).replaceAll('TZS ', ''), bold: true),
            const SizedBox(height: 8),
            _line('${_methodLabel(sale.method)} tendered', money(sale.tendered).replaceAll('TZS ', '')),
            if (sale.change > 0) _line('Change', money(sale.change).replaceAll('TZS ', '')),
            const Text('--------------------------------'),
            const SizedBox(height: 8),
            const Center(child: Text('Thank you — please come again!')),
            const SizedBox(height: 4),
            const Center(child: Text('VAT registered  ·  TIN 100-000-000')),
          ],
        ),
      ),
    );
  }

  Widget _line(String label, String value, {bool bold = false}) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 1),
      child: Row(
        children: [
          Text(label, style: TextStyle(fontWeight: bold ? FontWeight.w900 : FontWeight.normal)),
          const Spacer(),
          Text(value, style: TextStyle(fontWeight: bold ? FontWeight.w900 : FontWeight.normal)),
        ],
      ),
    );
  }

  String _fmt(DateTime t) {
    String two(int n) => n.toString().padLeft(2, '0');
    return '${t.year}-${two(t.month)}-${two(t.day)} ${two(t.hour)}:${two(t.minute)}:${two(t.second)}';
  }

  String _methodLabel(PaymentMethod m) => switch (m) {
        PaymentMethod.cash => 'Cash',
        PaymentMethod.card => 'Card',
        PaymentMethod.mobileMoney => 'Mobile money',
        PaymentMethod.giftCard => 'Gift card',
        PaymentMethod.voucher => 'Voucher',
      };
}

class _ActionPanel extends ConsumerWidget {
  final CompletedSale sale;
  const _ActionPanel({required this.sale});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    return Container(
      decoration: BoxDecoration(
        color: theme.colorScheme.surface,
        borderRadius: BorderRadius.circular(12),
      ),
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.check_circle, size: 56, color: Colors.green.shade600),
          const SizedBox(height: 8),
          Center(child: Text('Payment complete', style: theme.textTheme.titleLarge)),
          const SizedBox(height: 4),
          Center(
            child: Text(
              money(sale.total),
              style: theme.textTheme.headlineMedium?.copyWith(
                color: theme.colorScheme.primary,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
          if (sale.change > 0) ...[
            const SizedBox(height: 6),
            Center(
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                decoration: BoxDecoration(
                  color: theme.colorScheme.tertiaryContainer,
                  borderRadius: BorderRadius.circular(20),
                ),
                child: Text(
                  'Change due ${money(sale.change)}',
                  style: theme.textTheme.titleSmall?.copyWith(
                    color: theme.colorScheme.onTertiaryContainer,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
            ),
          ],
          const SizedBox(height: 20),
          OutlinedButton.icon(
            onPressed: () => _snack(context, 'Receipt reprinted on Star TSP143'),
            icon: const Icon(Icons.print_outlined),
            label: const Text('Reprint receipt'),
            style: OutlinedButton.styleFrom(padding: const EdgeInsets.symmetric(vertical: 12)),
          ),
          const SizedBox(height: 8),
          OutlinedButton.icon(
            onPressed: () => _emailDialog(context, sale.customer),
            icon: const Icon(Icons.email_outlined),
            label: const Text('Email receipt'),
            style: OutlinedButton.styleFrom(padding: const EdgeInsets.symmetric(vertical: 12)),
          ),
          const SizedBox(height: 8),
          OutlinedButton.icon(
            onPressed: () => _snack(context, 'Drawer opened — kick code sent'),
            icon: const Icon(Icons.inbox_outlined),
            label: const Text('Open drawer'),
            style: OutlinedButton.styleFrom(padding: const EdgeInsets.symmetric(vertical: 12)),
          ),
          const SizedBox(height: 16),
          FilledButton.icon(
            onPressed: () {
              ref.read(cartProvider.notifier).clear();
              ref.read(tenderedAmountProvider.notifier).state = 0;
              ref.read(selectedCustomerProvider.notifier).state = mockCustomers.first;
              context.go('/cart');
            },
            icon: const Icon(Icons.add_shopping_cart),
            label: const Text('Start next sale'),
            style: FilledButton.styleFrom(padding: const EdgeInsets.symmetric(vertical: 14)),
          ),
        ],
      ),
    );
  }

  void _snack(BuildContext context, String msg) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(msg), duration: const Duration(seconds: 2)),
    );
  }

  Future<void> _emailDialog(BuildContext context, MockCustomer customer) async {
    final ctrl = TextEditingController(
      text: customer.walkIn ? '' : '${customer.code.toLowerCase()}@example.com',
    );
    final sent = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('Email receipt'),
        content: TextField(
          controller: ctrl,
          decoration: const InputDecoration(
            labelText: 'Email address',
            border: OutlineInputBorder(),
          ),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Send')),
        ],
      ),
    );
    if (sent == true && context.mounted) {
      _snack(context, 'Receipt emailed to ${ctrl.text}');
    }
  }
}
