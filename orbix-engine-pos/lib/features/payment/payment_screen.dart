/// Payment screen — mixed-tender sale completion.
///
/// Allows splitting a sale across multiple tender types (cash + mobile money,
/// etc.). Tenders must sum to at least the sale total before completion is
/// permitted. Change is calculated only on the cash tender line.
///
/// Records the sale via [recordSale] in mocks.dart (existing pattern); the
/// tender breakdown is carried in CompletedSale.tenders so the receipt and
/// X-report can show it.
///
/// Offline-capable: the sale op is enqueued in the outbox with the full
/// tender breakdown in the payload; no network required.
library;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../_demo/mocks.dart';

/// A single tender line added by the cashier.
class TenderLine {
  final PaymentMethod method;
  final double amount;

  const TenderLine({required this.method, required this.amount});

  @override
  String toString() => '${method.label}: $amount';
}

class PaymentScreen extends ConsumerStatefulWidget {
  const PaymentScreen({super.key});

  @override
  ConsumerState<PaymentScreen> createState() => _PaymentScreenState();
}

class _PaymentScreenState extends ConsumerState<PaymentScreen> {
  /// Committed tender lines (each method can appear at most once).
  final List<TenderLine> _tenders = [];

  /// The method currently being added.
  PaymentMethod _addingMethod = PaymentMethod.cash;

  /// The amount being typed for the current method.
  final _addAmountCtrl = TextEditingController();

  @override
  void dispose() {
    _addAmountCtrl.dispose();
    super.dispose();
  }

  double get _tenderedTotal =>
      _tenders.fold(0.0, (sum, t) => sum + t.amount);

  /// Remaining amount not yet covered by committed tenders.
  double _remaining(double total) => (total - _tenderedTotal).clamp(0, double.infinity);

  bool _canComplete(double total) =>
      _tenders.isNotEmpty && _tenderedTotal >= total;

  void _addTender(double total) {
    final rawText = _addAmountCtrl.text.replaceAll(',', '').trim();
    double amount;
    if (rawText.isEmpty) {
      // default to remaining balance
      amount = _remaining(total);
    } else {
      amount = double.tryParse(rawText) ?? 0;
    }
    if (amount <= 0) return;

    // Clamp to remaining so we don't over-tender (except cash where change is OK).
    if (_addingMethod != PaymentMethod.cash) {
      amount = amount.clamp(0, _remaining(total));
      if (amount <= 0) return;
    }

    setState(() {
      // Remove any existing line for this method (replace it).
      _tenders.removeWhere((t) => t.method == _addingMethod);
      _tenders.add(TenderLine(method: _addingMethod, amount: amount));
      _addAmountCtrl.clear();
      // Advance to next available method if balance remaining.
      if (_tenderedTotal < total) {
        final used = _tenders.map((t) => t.method).toSet();
        final next = PaymentMethod.values.firstWhere(
          (m) => !used.contains(m),
          orElse: () => PaymentMethod.cash,
        );
        _addingMethod = next;
      }
    });
  }

  void _removeTender(int index) {
    setState(() => _tenders.removeAt(index));
  }

  void _complete(double total) {
    if (!_canComplete(total)) return;
    recordSale(
      ref,
      method: _tenders.length == 1 ? _tenders.first.method : PaymentMethod.cash,
      tendered: _tenderedTotal,
      tenders: List.unmodifiable(_tenders),
    );
    context.go('/receipt');
  }

  void _quickFill(double amount) {
    _addAmountCtrl.text = amount.toStringAsFixed(0);
    setState(() {});
  }

  double _roundUp(double total, double bucket) =>
      ((total / bucket).ceil() * bucket).toDouble();

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final total = ref.watch(cartTotalProvider);
    final remaining = _remaining(total);
    // Change = total tendered - total (if positive, cashier gives change).
    final changeAmount = _tenderedTotal - total;

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
          // Left: order summary + committed tenders
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
                              Expanded(child: Text('${l.qty.toInt()} x ${l.item.name}')),
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

                  // Committed tenders list
                  if (_tenders.isNotEmpty) ...[
                    const SizedBox(height: 16),
                    Container(height: 1, color: theme.dividerColor),
                    const SizedBox(height: 12),
                    Text('Tenders', style: theme.textTheme.titleSmall),
                    const SizedBox(height: 8),
                    ..._tenders.asMap().entries.map((e) {
                      final i = e.key;
                      final t = e.value;
                      return Padding(
                        padding: const EdgeInsets.symmetric(vertical: 4),
                        child: Row(
                          children: [
                            Text(t.method.icon,
                                style: const TextStyle(fontSize: 16)),
                            const SizedBox(width: 8),
                            Expanded(
                              child: Text(t.method.label,
                                  style: theme.textTheme.bodyMedium),
                            ),
                            Text(money(t.amount),
                                style: const TextStyle(fontWeight: FontWeight.w600)),
                            const SizedBox(width: 4),
                            IconButton(
                              icon: const Icon(Icons.close, size: 16),
                              visualDensity: VisualDensity.compact,
                              onPressed: () => _removeTender(i),
                            ),
                          ],
                        ),
                      );
                    }),
                    const SizedBox(height: 8),
                    Container(height: 1, color: theme.dividerColor),
                    const SizedBox(height: 8),
                    Row(
                      children: [
                        const Expanded(child: Text('Total tendered')),
                        Text(money(_tenderedTotal),
                            style: theme.textTheme.bodyMedium?.copyWith(
                              fontWeight: FontWeight.w700,
                              color: _tenderedTotal >= total
                                  ? theme.colorScheme.primary
                                  : theme.colorScheme.error,
                            )),
                      ],
                    ),
                    if (remaining > 0) ...[
                      const SizedBox(height: 4),
                      Row(
                        children: [
                          Expanded(
                              child: Text(
                            'Still owed',
                            style: theme.textTheme.bodySmall?.copyWith(
                                color: theme.colorScheme.error),
                          )),
                          Text(money(remaining),
                              style: theme.textTheme.bodySmall?.copyWith(
                                  color: theme.colorScheme.error,
                                  fontWeight: FontWeight.w600)),
                        ],
                      ),
                    ],
                    if (changeAmount > 0) ...[
                      const SizedBox(height: 4),
                      Row(
                        children: [
                          Expanded(
                              child: Text(
                            'Change due',
                            style: theme.textTheme.bodySmall?.copyWith(
                                color: Colors.green[700]),
                          )),
                          Text(money(changeAmount),
                              style: theme.textTheme.bodySmall?.copyWith(
                                  color: Colors.green[700],
                                  fontWeight: FontWeight.w600)),
                        ],
                      ),
                    ],
                  ],
                ],
              ),
            ),
          ),

          // Right: add tender panel
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
                  Text(
                    remaining > 0
                        ? 'Add tender  (${money(remaining)} remaining)'
                        : 'Tender complete',
                    style: theme.textTheme.titleMedium,
                  ),
                  const SizedBox(height: 12),

                  // Method chips
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: PaymentMethod.values
                        .where((m) => !_tenders.any((t) => t.method == m))
                        .map((m) {
                      final selected = _addingMethod == m;
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
                        onSelected: remaining > 0
                            ? (_) => setState(() {
                                  _addingMethod = m;
                                  _addAmountCtrl.clear();
                                })
                            : null,
                        padding: const EdgeInsets.symmetric(
                            horizontal: 14, vertical: 10),
                      );
                    }).toList(),
                  ),
                  const SizedBox(height: 20),

                  if (remaining > 0) ...[
                    // Amount input
                    TextField(
                      controller: _addAmountCtrl,
                      keyboardType: TextInputType.number,
                      autofocus: true,
                      style: theme.textTheme.headlineSmall,
                      decoration: InputDecoration(
                        labelText: '${_addingMethod.label} amount (TZS)',
                        hintText: remaining.toStringAsFixed(0),
                        border: const OutlineInputBorder(),
                        helperText: 'Leave blank to add exact remaining balance',
                      ),
                      onChanged: (_) => setState(() {}),
                      onSubmitted: (_) => _addTender(total),
                    ),
                    const SizedBox(height: 12),

                    // Cash quick-fill buttons
                    if (_addingMethod == PaymentMethod.cash)
                      Wrap(
                        spacing: 8,
                        runSpacing: 8,
                        children: <double>{
                          remaining,
                          _roundUp(remaining, 5000),
                          _roundUp(remaining, 10000),
                          _roundUp(remaining, 50000),
                        }
                            .map((amt) => OutlinedButton(
                                  onPressed: () => _quickFill(amt),
                                  child: Text(money(amt)),
                                ))
                            .toList(),
                      ),
                    const SizedBox(height: 16),

                    FilledButton.tonal(
                      onPressed: () => _addTender(total),
                      style: FilledButton.styleFrom(
                          padding: const EdgeInsets.symmetric(vertical: 14)),
                      child: Text(
                        _addAmountCtrl.text.isEmpty
                            ? 'Add ${_addingMethod.label} (full remaining)'
                            : 'Add ${_addingMethod.label}',
                      ),
                    ),
                  ] else
                    Container(
                      padding: const EdgeInsets.all(16),
                      decoration: BoxDecoration(
                        color: theme.colorScheme.primaryContainer,
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Row(
                        children: [
                          Icon(Icons.check_circle, color: theme.colorScheme.primary),
                          const SizedBox(width: 10),
                          Text('All tenders added',
                              style: theme.textTheme.titleSmall),
                        ],
                      ),
                    ),

                  const Spacer(),

                  // Action row
                  Row(
                    children: [
                      Expanded(
                        child: OutlinedButton(
                          onPressed: () => context.pop(),
                          style: OutlinedButton.styleFrom(
                              padding:
                                  const EdgeInsets.symmetric(vertical: 16)),
                          child: const Text('Cancel'),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        flex: 2,
                        child: FilledButton.icon(
                          onPressed: _canComplete(total)
                              ? () => _complete(total)
                              : null,
                          style: FilledButton.styleFrom(
                              padding:
                                  const EdgeInsets.symmetric(vertical: 16)),
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
}
