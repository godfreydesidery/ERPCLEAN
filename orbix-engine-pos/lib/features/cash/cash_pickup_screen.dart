/// Cash pickup screen — mid-shift removal of cash from the till drawer
/// to a back-office safe or bank. Records a CASH_PICKUP outbox op so the
/// amount is included in the till-close manifest (US-POS-013).
///
/// Offline-capable: enqueue + confirm dialog works with no network.
/// Sync happens on the next dispatcher cycle.
library;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../_demo/mocks.dart';
import '../till_session/till_session_providers.dart' show activeTillSessionProvider;
import 'cash_movement_providers.dart';

class CashPickupScreen extends ConsumerStatefulWidget {
  const CashPickupScreen({super.key});

  @override
  ConsumerState<CashPickupScreen> createState() => _CashPickupScreenState();
}

class _CashPickupScreenState extends ConsumerState<CashPickupScreen> {
  final _amountCtrl = TextEditingController();
  final _noteCtrl = TextEditingController();
  bool _submitting = false;
  String? _error;

  @override
  void dispose() {
    _amountCtrl.dispose();
    _noteCtrl.dispose();
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
    // Server-side session id for the tillSessionId payload field (may be null
    // if the session has not yet synced; back-filled by dispatcher on ACCEPTED).
    final sessionServerId = activeSession?.serverEntityId;

    try {
      final repo = ref.read(cashMovementRepositoryProvider);
      await repo.recordCashPickup(
        tillSessionClientOpId: sessionClientOpId,
        tillSessionServerId: sessionServerId,
        amount: amount,
        note: _noteCtrl.text.trim().isEmpty ? null : _noteCtrl.text.trim(),
      );

      if (!mounted) return;
      await _showSuccessDialog(amount);
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = 'Failed to record pickup: $e');
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  Future<void> _showSuccessDialog(double amount) async {
    await showDialog<void>(
      context: context,
      barrierDismissible: false,
      builder: (_) => AlertDialog(
        icon: const Icon(Icons.check_circle, color: Colors.green, size: 48),
        title: const Text('Cash pickup recorded'),
        content: Text(
          '${money(amount)} has been recorded as removed from the till.\n\n'
          'The amount will sync to the server and appear in the till-close manifest.',
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
        title: const Text('Cash pickup'),
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
                  color: theme.colorScheme.primaryContainer.withValues(alpha: 0.5),
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Icon(Icons.info_outline, color: theme.colorScheme.primary, size: 20),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Text(
                        'Record cash removed from the till drawer for safe-keeping or bank deposit. '
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
                      Text('Pickup details', style: theme.textTheme.titleMedium),
                      const SizedBox(height: 20),

                      // Amount
                      TextField(
                        controller: _amountCtrl,
                        keyboardType: TextInputType.number,
                        autofocus: true,
                        style: theme.textTheme.headlineSmall,
                        decoration: InputDecoration(
                          labelText: 'Amount removed (TZS)',
                          prefixIcon: const Icon(Icons.payments_outlined),
                          border: const OutlineInputBorder(),
                          errorText: _error,
                          hintText: '0',
                        ),
                        onChanged: (_) => setState(() => _error = null),
                      ),
                      const SizedBox(height: 16),

                      // Note / reference
                      TextField(
                        controller: _noteCtrl,
                        maxLines: 2,
                        textCapitalization: TextCapitalization.sentences,
                        decoration: const InputDecoration(
                          labelText: 'Note / reference (optional)',
                          prefixIcon: Icon(Icons.notes_outlined),
                          border: OutlineInputBorder(),
                          hintText: 'e.g. Moved to safe, ref SAFE-001',
                        ),
                      ),
                      const SizedBox(height: 24),

                      // Quick amounts
                      Text('Quick amounts', style: theme.textTheme.labelLarge),
                      const SizedBox(height: 8),
                      Wrap(
                        spacing: 8,
                        runSpacing: 8,
                        children: [50000, 100000, 200000, 500000]
                            .map((amt) => OutlinedButton(
                                  onPressed: () {
                                    _amountCtrl.text = amt.toString();
                                    setState(() => _error = null);
                                  },
                                  child: Text(money(amt.toDouble())),
                                ))
                            .toList(),
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
                        label: const Text('Record pickup'),
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
