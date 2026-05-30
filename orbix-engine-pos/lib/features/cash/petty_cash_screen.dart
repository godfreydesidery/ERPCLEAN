/// Petty cash payout screen — records a small cash payment from the till
/// drawer (deliveries, office supplies, etc.). Records a PETTY_CASH outbox op
/// so the amount is deducted from the expected drawer balance at till close
/// (US-POS-014).
///
/// Offline-capable: enqueue + confirm dialog works with no network.
library;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../_demo/mocks.dart';
import '../till_session/till_session_providers.dart' show activeTillSessionProvider;
import 'cash_movement_providers.dart';
import 'cash_movement_repository.dart';

class PettyCashScreen extends ConsumerStatefulWidget {
  const PettyCashScreen({super.key});

  @override
  ConsumerState<PettyCashScreen> createState() => _PettyCashScreenState();
}

class _PettyCashScreenState extends ConsumerState<PettyCashScreen> {
  final _amountCtrl = TextEditingController();
  final _paidToCtrl = TextEditingController();
  final _descriptionCtrl = TextEditingController();
  PettyCashCategory _category = PettyCashCategory.other;
  bool _submitting = false;
  String? _error;

  @override
  void dispose() {
    _amountCtrl.dispose();
    _paidToCtrl.dispose();
    _descriptionCtrl.dispose();
    super.dispose();
  }

  double? get _parsedAmount {
    final raw = _amountCtrl.text.replaceAll(',', '').trim();
    if (raw.isEmpty) return null;
    return double.tryParse(raw);
  }

  bool get _canSubmit => _parsedAmount != null && _parsedAmount! > 0 && !_submitting;

  Future<void> _submit() async {
    final amount = _parsedAmount;
    if (amount == null || amount <= 0) {
      setState(() => _error = 'Enter a valid amount greater than 0');
      return;
    }

    setState(() {
      _submitting = true;
      _error = null;
    });

    // Read the active session clientOpId from the Drift-backed provider.
    final activeSession = await ref.read(activeTillSessionProvider.future);
    final sessionClientOpId = activeSession?.clientOpId ?? '';
    if (sessionClientOpId.isEmpty) {
      if (mounted) setState(() => _error = 'No active till session. Open the till first.');
      if (mounted) setState(() => _submitting = false);
      return;
    }
    final sessionServerId = activeSession?.serverEntityId;

    try {
      final repo = ref.read(cashMovementRepositoryProvider);
      await repo.recordPettyCash(
        tillSessionClientOpId: sessionClientOpId,
        tillSessionServerId: sessionServerId,
        amount: amount,
        category: _category,
        paidTo: _paidToCtrl.text.trim().isEmpty ? null : _paidToCtrl.text.trim(),
        description: _descriptionCtrl.text.trim().isEmpty
            ? null
            : _descriptionCtrl.text.trim(),
      );

      if (!mounted) return;
      await _showSuccessDialog(amount);
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = 'Failed to record payout: $e');
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  Future<void> _showSuccessDialog(double amount) async {
    await showDialog<void>(
      context: context,
      barrierDismissible: false,
      builder: (_) => AlertDialog(
        icon: const Icon(Icons.receipt_long, color: Colors.green, size: 48),
        title: const Text('Petty cash recorded'),
        content: Text(
          '${money(amount)} payout has been recorded.\n\n'
          'The amount will sync to the server and will be deducted from '
          'the expected drawer balance at till close.',
        ),
        actions: [
          FilledButton(
            onPressed: () {
              Navigator.pop(context);
              context.pop();
            },
            child: const Text('Done'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      backgroundColor: theme.colorScheme.surfaceContainerLow,
      appBar: AppBar(
        title: const Text('Petty cash payout'),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () => context.pop(),
        ),
      ),
      body: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 560),
          child: ListView(
            padding: const EdgeInsets.all(24),
            children: [
              // Info banner
              Container(
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: theme.colorScheme.secondaryContainer.withValues(alpha: 0.5),
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Icon(Icons.info_outline, color: theme.colorScheme.secondary, size: 20),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Text(
                        'Record a small cash payment made from the till drawer. '
                        'This reduces the expected drawer balance at till close.',
                        style: theme.textTheme.bodySmall,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 24),

              Card(
                elevation: 0,
                child: Padding(
                  padding: const EdgeInsets.all(24),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      Text('Payout details', style: theme.textTheme.titleMedium),
                      const SizedBox(height: 20),

                      // Category
                      DropdownButtonFormField<PettyCashCategory>(
                        value: _category,
                        decoration: const InputDecoration(
                          labelText: 'Category',
                          prefixIcon: Icon(Icons.category_outlined),
                          border: OutlineInputBorder(),
                        ),
                        items: PettyCashCategory.values
                            .map((c) => DropdownMenuItem(
                                  value: c,
                                  child: Text(c.label),
                                ))
                            .toList(),
                        onChanged: (v) {
                          if (v != null) setState(() => _category = v);
                        },
                      ),
                      const SizedBox(height: 16),

                      // Amount
                      TextField(
                        controller: _amountCtrl,
                        keyboardType: TextInputType.number,
                        style: theme.textTheme.headlineSmall,
                        decoration: InputDecoration(
                          labelText: 'Amount paid (TZS)',
                          prefixIcon: const Icon(Icons.payments_outlined),
                          border: const OutlineInputBorder(),
                          errorText: _error,
                          hintText: '0',
                        ),
                        onChanged: (_) => setState(() => _error = null),
                      ),
                      const SizedBox(height: 16),

                      // Paid to
                      TextField(
                        controller: _paidToCtrl,
                        textCapitalization: TextCapitalization.words,
                        decoration: const InputDecoration(
                          labelText: 'Paid to (optional)',
                          prefixIcon: Icon(Icons.person_outline),
                          border: OutlineInputBorder(),
                          hintText: 'e.g. Delivery driver, supplier name',
                        ),
                      ),
                      const SizedBox(height: 16),

                      // Description
                      TextField(
                        controller: _descriptionCtrl,
                        maxLines: 2,
                        textCapitalization: TextCapitalization.sentences,
                        decoration: const InputDecoration(
                          labelText: 'Description (optional)',
                          prefixIcon: Icon(Icons.notes_outlined),
                          border: OutlineInputBorder(),
                          hintText: 'e.g. Bought printer paper, ref INV-0042',
                        ),
                      ),
                      const SizedBox(height: 24),

                      // Submit
                      FilledButton.icon(
                        onPressed: _canSubmit ? _submit : null,
                        style: FilledButton.styleFrom(
                          padding: const EdgeInsets.symmetric(vertical: 16),
                        ),
                        icon: _submitting
                            ? const SizedBox(
                                width: 18,
                                height: 18,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                  color: Colors.white,
                                ),
                              )
                            : const Icon(Icons.check),
                        label: const Text('Record payout'),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
