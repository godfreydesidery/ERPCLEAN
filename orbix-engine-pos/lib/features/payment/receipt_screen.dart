/// Receipt screen — shown after a successful payment.
///
/// Left panel: paper-tape receipt with real sale data (shop header, line items,
/// mixed-tender breakdown, VAT info, fiscal block).
/// Right panel: action buttons (reprint / email / next sale).
///
/// Data source: [lastSaleProvider] — populated by [PaymentScreen._recordSaleReal]
/// after writing to Drift + outbox.
///
/// Fiscal block: if [CompletedSale.fiscalStatus] is non-null and the sale has
/// been fiscalised, shows FISCALIZED + QR from [fiscalQrPayload].
/// If fiscalStatus is absent (regime=NONE) the fiscal block is omitted.
library;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../_demo/mocks.dart';
import '../customer/customer_providers.dart';

/// Fiscal status values mirrored from the server PosReceiptFiscalStatus enum.
/// The server updates the POS_SALE outbox response on sync-back; for now the
/// status may be null (offline) or 'NONE' (no fiscal regime).
enum _FiscalStatus { none, provisional, fiscalized }

_FiscalStatus _parseFiscal(String? raw) => switch (raw?.toUpperCase()) {
      'FISCALIZED' => _FiscalStatus.fiscalized,
      'PROVISIONAL' => _FiscalStatus.provisional,
      _ => _FiscalStatus.none,
    };

class ReceiptScreen extends ConsumerWidget {
  const ReceiptScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final sale = ref.watch(lastSaleProvider);
    if (sale == null) {
      WidgetsBinding.instance
          .addPostFrameCallback((_) => context.go('/cart'));
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

// ---------------------------------------------------------------------------
// Paper receipt
// ---------------------------------------------------------------------------

class _PaperReceipt extends StatelessWidget {
  final CompletedSale sale;
  const _PaperReceipt({required this.sale});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final fiscal = _parseFiscal(sale.fiscalStatus);

    return Container(
      decoration: BoxDecoration(
        color: const Color(0xFFFFFDF7),
        borderRadius: BorderRadius.circular(8),
        boxShadow: const [
          BoxShadow(color: Colors.black12, blurRadius: 12, offset: Offset(0, 6))
        ],
      ),
      padding: const EdgeInsets.fromLTRB(24, 28, 24, 28),
      child: DefaultTextStyle(
        style: const TextStyle(
          fontFamily: 'monospace',
          fontSize: 12.5,
          color: Colors.black87,
          height: 1.4,
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // ---- Header ----
            Center(
              child: Column(
                children: [
                  Text(
                    'ORBIX ENGINE',
                    style: theme.textTheme.titleMedium?.copyWith(
                      fontFamily: 'monospace',
                      color: Colors.black,
                      fontWeight: FontWeight.w800,
                      letterSpacing: 2,
                    ),
                  ),
                  Text(sale.branchName),
                  Text('${sale.tillCode}  ·  ${sale.cashierName}'),
                ],
              ),
            ),
            const SizedBox(height: 10),
            _divider(),

            // ---- Receipt metadata ----
            Text('Receipt #${sale.receiptNo}'),
            Text(_fmt(sale.completedAt)),
            Text('Customer: ${sale.customer.name}'),
            _divider(),
            const SizedBox(height: 4),

            // ---- Line items ----
            ...sale.lines.map((l) => Padding(
                  padding: const EdgeInsets.symmetric(vertical: 2),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(l.item.name),
                      Row(
                        children: [
                          Text(
                            '  ${l.qty == l.qty.truncate() ? l.qty.toInt() : l.qty}'
                            ' x ${_amt(l.item.price)}',
                          ),
                          const Spacer(),
                          Text(_amt(l.net)),
                        ],
                      ),
                      if (l.discount > 0)
                        Row(
                          children: [
                            Text('  disc ${l.discountPct.toStringAsFixed(0)}%'),
                            const Spacer(),
                            Text('-${_amt(l.discount)}'),
                          ],
                        ),
                    ],
                  ),
                )),

            const SizedBox(height: 4),
            _divider(),

            // ---- Totals ----
            _line('Subtotal', _amt(sale.subtotal)),
            if (sale.discount > 0)
              _line('Discount', '-${_amt(sale.discount)}'),
            _line('TOTAL', _amt(sale.total), bold: true),
            const SizedBox(height: 8),

            // ---- Tender breakdown ----
            if (sale.tenders.isNotEmpty) ...[
              ...sale.tenders.map((t) => _line(
                    '${t.method.label} tendered',
                    _amt(t.amount),
                  )),
            ] else
              _line('Tendered', _amt(sale.tendered)),

            if (sale.change > 0) _line('Change', _amt(sale.change)),
            _divider(),
            const SizedBox(height: 6),

            // ---- VAT notice ----
            const Center(child: Text('Incl. VAT  ·  TIN 100-000-000')),
            const SizedBox(height: 4),
            const Center(child: Text('Thank you — please come again!')),

            // ---- Fiscal block (shown only when regime is active) ----
            if (fiscal != _FiscalStatus.none) ...[
              const SizedBox(height: 10),
              _divider(),
              const SizedBox(height: 6),
              if (fiscal == _FiscalStatus.fiscalized) ...[
                const Center(
                  child: Text(
                    'FISCALIZED',
                    style: TextStyle(
                      fontWeight: FontWeight.w900,
                      letterSpacing: 1.5,
                    ),
                  ),
                ),
                if (sale.fiscalVerificationCode != null) ...[
                  const SizedBox(height: 4),
                  Center(
                    child: Text(
                      'Verification: ${sale.fiscalVerificationCode}',
                      style: const TextStyle(fontSize: 11),
                    ),
                  ),
                ],
                if (sale.fiscalQrPayload != null) ...[
                  const SizedBox(height: 8),
                  Center(
                    child: _FiscalQrPlaceholder(
                        payload: sale.fiscalQrPayload!),
                  ),
                ],
              ] else ...[
                const Center(
                  child: Text(
                    'PROVISIONAL (pending fiscalisation)',
                    style: TextStyle(
                      fontStyle: FontStyle.italic,
                      fontSize: 11,
                    ),
                  ),
                ),
              ],
            ],
          ],
        ),
      ),
    );
  }

  Widget _divider() => const Text('--------------------------------');

  Widget _line(String label, String value, {bool bold = false}) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 1),
      child: Row(
        children: [
          Text(label,
              style: TextStyle(
                  fontWeight:
                      bold ? FontWeight.w900 : FontWeight.normal)),
          const Spacer(),
          Text(value,
              style: TextStyle(
                  fontWeight:
                      bold ? FontWeight.w900 : FontWeight.normal)),
        ],
      ),
    );
  }

  /// Format amount without currency prefix (currency shown in header).
  String _amt(double v) => money(v).replaceFirst('TZS ', '');

  String _fmt(DateTime t) {
    String two(int n) => n.toString().padLeft(2, '0');
    return '${t.year}-${two(t.month)}-${two(t.day)} '
        '${two(t.hour)}:${two(t.minute)}:${two(t.second)}';
  }
}

/// Placeholder for a fiscal QR code.
/// Replace with `qr_flutter` when the fiscal device plugin is integrated.
class _FiscalQrPlaceholder extends StatelessWidget {
  final String payload;
  const _FiscalQrPlaceholder({required this.payload});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 100,
      height: 100,
      decoration: BoxDecoration(
        border: Border.all(color: Colors.black54),
        borderRadius: BorderRadius.circular(4),
      ),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(Icons.qr_code, size: 48, color: Colors.black54),
          const SizedBox(height: 4),
          Text(
            'QR code\n(${payload.length > 12 ? "${payload.substring(0, 12)}…" : payload})',
            textAlign: TextAlign.center,
            style: const TextStyle(fontSize: 9),
          ),
        ],
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Action panel
// ---------------------------------------------------------------------------

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
          Icon(Icons.check_circle,
              size: 56, color: Colors.green.shade600),
          const SizedBox(height: 8),
          Center(
              child: Text('Payment complete',
                  style: theme.textTheme.titleLarge)),
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
                padding: const EdgeInsets.symmetric(
                    horizontal: 12, vertical: 6),
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
            onPressed: () => _snack(context,
                'Printer integration pending — receipt shown on screen'),
            icon: const Icon(Icons.print_outlined),
            label: const Text('Reprint receipt'),
            style: OutlinedButton.styleFrom(
                padding: const EdgeInsets.symmetric(vertical: 12)),
          ),
          const SizedBox(height: 8),
          OutlinedButton.icon(
            onPressed: () => _emailDialog(context, sale.customer),
            icon: const Icon(Icons.email_outlined),
            label: const Text('Email receipt'),
            style: OutlinedButton.styleFrom(
                padding: const EdgeInsets.symmetric(vertical: 12)),
          ),
          const SizedBox(height: 8),
          OutlinedButton.icon(
            onPressed: () =>
                _snack(context, 'Drawer opened — kick code sent'),
            icon: const Icon(Icons.inbox_outlined),
            label: const Text('Open drawer'),
            style: OutlinedButton.styleFrom(
                padding: const EdgeInsets.symmetric(vertical: 12)),
          ),
          const SizedBox(height: 16),
          FilledButton.icon(
            onPressed: () {
              ref.read(cartProvider.notifier).clear();
              ref.read(tenderedAmountProvider.notifier).state = 0;
              // Reset customer to walk-in for next sale.
              ref.read(selectedPosCustomerProvider.notifier).state =
                  PosCustomer.walkIn;
              context.go('/cart');
            },
            icon: const Icon(Icons.add_shopping_cart),
            label: const Text('Start next sale'),
            style: FilledButton.styleFrom(
                padding: const EdgeInsets.symmetric(vertical: 14)),
          ),
        ],
      ),
    );
  }

  void _snack(BuildContext context, String msg) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
          content: Text(msg),
          duration: const Duration(seconds: 2)),
    );
  }

  Future<void> _emailDialog(
      BuildContext context, MockCustomer customer) async {
    final ctrl = TextEditingController(
      text: customer.walkIn
          ? ''
          : '${customer.code.toLowerCase()}@example.com',
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
          TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Cancel')),
          FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('Send')),
        ],
      ),
    );
    if (sent == true && context.mounted) {
      _snack(context, 'Receipt emailed to ${ctrl.text}');
    }
  }
}
