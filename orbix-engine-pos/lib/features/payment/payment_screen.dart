import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../_demo/mocks.dart';

class PaymentScreen extends ConsumerStatefulWidget {
  const PaymentScreen({super.key});

  @override
  ConsumerState<PaymentScreen> createState() => _PaymentScreenState();
}

class _PaymentScreenState extends ConsumerState<PaymentScreen> {
  PaymentMethod _method = PaymentMethod.cash;
  final _tendered = TextEditingController();

  @override
  void dispose() {
    _tendered.dispose();
    super.dispose();
  }

  void _quickFill(double amount) {
    _tendered.text = amount.toStringAsFixed(0);
    setState(() {});
  }

  void _complete() {
    final total = ref.read(cartTotalProvider);
    final tendered = _method == PaymentMethod.cash
        ? (double.tryParse(_tendered.text.replaceAll(',', '')) ?? total)
        : total;
    recordSale(ref, method: _method, tendered: tendered);
    context.go('/receipt');
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final total = ref.watch(cartTotalProvider);
    final tenderedAmount = double.tryParse(_tendered.text.replaceAll(',', '')) ?? 0;
    final change = tenderedAmount - total;
    final canComplete = _method != PaymentMethod.cash || tenderedAmount >= total;

    return Scaffold(
      backgroundColor: theme.colorScheme.surfaceContainerLow,
      appBar: AppBar(
        title: const Text('Payment'),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () => context.pop(),
        ),
      ),
      body: Row(
        children: [
          // Left: order summary
          Expanded(
            flex: 1,
            child: Container(
              margin: const EdgeInsets.all(16),
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                color: theme.colorScheme.surface,
                borderRadius: BorderRadius.circular(12),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text('Order summary', style: theme.textTheme.titleMedium),
                  const SizedBox(height: 16),
                  Expanded(
                    child: ListView(
                      children: ref.watch(cartProvider).map((l) {
                        return Padding(
                          padding: const EdgeInsets.symmetric(vertical: 6),
                          child: Row(
                            children: [
                              Expanded(
                                child: Text('${l.qty.toInt()} × ${l.item.name}'),
                              ),
                              Text(money(l.net),
                                  style: const TextStyle(fontWeight: FontWeight.w600)),
                            ],
                          ),
                        );
                      }).toList(),
                    ),
                  ),
                  Container(height: 1, color: theme.dividerColor),
                  const SizedBox(height: 12),
                  Row(
                    children: [
                      Text('Amount due', style: theme.textTheme.titleMedium),
                      const Spacer(),
                      Text(
                        money(total),
                        style: theme.textTheme.headlineMedium?.copyWith(
                          color: theme.colorScheme.primary,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),

          // Right: tender selection + amount tendered
          Expanded(
            flex: 1,
            child: Container(
              margin: const EdgeInsets.fromLTRB(0, 16, 16, 16),
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                color: theme.colorScheme.surface,
                borderRadius: BorderRadius.circular(12),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text('Tender', style: theme.textTheme.titleMedium),
                  const SizedBox(height: 12),
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: PaymentMethod.values.map((m) {
                      final selected = _method == m;
                      return ChoiceChip(
                        label: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Text(m.icon, style: const TextStyle(fontSize: 16)),
                            const SizedBox(width: 6),
                            Text(m.label),
                          ],
                        ),
                        selected: selected,
                        onSelected: (_) => setState(() => _method = m),
                        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                      );
                    }).toList(),
                  ),
                  const SizedBox(height: 20),
                  if (_method == PaymentMethod.cash) ...[
                    TextField(
                      controller: _tendered,
                      keyboardType: TextInputType.number,
                      autofocus: true,
                      style: theme.textTheme.headlineSmall,
                      decoration: InputDecoration(
                        labelText: 'Amount tendered (TZS)',
                        hintText: total.toStringAsFixed(0),
                        border: const OutlineInputBorder(),
                      ),
                      onChanged: (_) => setState(() {}),
                    ),
                    const SizedBox(height: 12),
                    Wrap(
                      spacing: 8,
                      runSpacing: 8,
                      children: [total, _roundUp(total, 5000), _roundUp(total, 10000), _roundUp(total, 50000)]
                          .toSet()
                          .map((amt) => OutlinedButton(
                                onPressed: () => _quickFill(amt),
                                child: Text(money(amt)),
                              ))
                          .toList(),
                    ),
                    const SizedBox(height: 20),
                    Container(
                      padding: const EdgeInsets.all(14),
                      decoration: BoxDecoration(
                        color: change >= 0
                            ? theme.colorScheme.surfaceContainerHigh
                            : theme.colorScheme.errorContainer,
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Row(
                        children: [
                          Text(
                            change >= 0 ? 'Change due' : 'Short by',
                            style: theme.textTheme.titleMedium,
                          ),
                          const Spacer(),
                          Text(
                            money(change.abs()),
                            style: theme.textTheme.headlineSmall?.copyWith(
                              color: change >= 0 ? theme.colorScheme.primary : theme.colorScheme.error,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ] else
                    Container(
                      padding: const EdgeInsets.all(16),
                      decoration: BoxDecoration(
                        color: theme.colorScheme.surfaceContainerHigh,
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Column(
                        children: [
                          Icon(Icons.tap_and_play, size: 48, color: theme.colorScheme.primary),
                          const SizedBox(height: 12),
                          Text('Awaiting ${_method.label.toLowerCase()} payment…',
                              style: theme.textTheme.titleMedium),
                          const SizedBox(height: 6),
                          Text(
                            _method == PaymentMethod.card
                                ? 'Present card to the PIN-pad'
                                : 'Customer to confirm on their device',
                            style: theme.textTheme.bodyMedium?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                          ),
                        ],
                      ),
                    ),
                  const Spacer(),
                  Row(
                    children: [
                      Expanded(
                        child: OutlinedButton(
                          onPressed: () => context.pop(),
                          style: OutlinedButton.styleFrom(padding: const EdgeInsets.symmetric(vertical: 16)),
                          child: const Text('Cancel'),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        flex: 2,
                        child: FilledButton.icon(
                          onPressed: canComplete ? _complete : null,
                          style: FilledButton.styleFrom(padding: const EdgeInsets.symmetric(vertical: 16)),
                          icon: const Icon(Icons.check),
                          label: const Text('Complete payment'),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  double _roundUp(double total, double bucket) {
    return ((total / bucket).ceil() * bucket).toDouble();
  }
}
