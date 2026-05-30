import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../data/sync/sync_models.dart' show TillCloseStatus;
import '../../data/sync/sync_providers.dart';
import '../_demo/mocks.dart';
import 'till_session_providers.dart';

class TillCloseScreen extends ConsumerStatefulWidget {
  const TillCloseScreen({super.key});

  @override
  ConsumerState<TillCloseScreen> createState() => _TillCloseScreenState();
}

class _TillCloseScreenState extends ConsumerState<TillCloseScreen> {
  final _declaredCash = TextEditingController(text: '0');
  bool _closing = false;
  String? _error;

  @override
  void dispose() {
    _declaredCash.dispose();
    super.dispose();
  }

  Future<void> _close() async {
    final activeSession = await ref.read(activeTillSessionProvider.future);
    if (activeSession == null) {
      setState(() => _error = 'No active session found');
      return;
    }

    final declared = double.tryParse(_declaredCash.text.replaceAll(',', '')) ?? 0;

    setState(() {
      _closing = true;
      _error = null;
    });

    try {
      final syncRepo = ref.read(syncRepositoryProvider);
      final result = await syncRepo.closeTillSession(
        sessionClientOpId: activeSession.clientOpId,
        declaredCash: declared,
      );

      if (!mounted) return;

      if (result.status == TillCloseStatus.closed) {
        await showDialog<void>(
          context: context,
          barrierDismissible: false,
          builder: (_) => AlertDialog(
            icon: const Icon(Icons.lock, size: 48, color: Colors.green),
            title: const Text('Till closed'),
            content: Text(
              'Z-report generated.\n'
              'Expected cash: ${money(double.tryParse(result.expectedCash) ?? 0)}\n'
              'Declared cash: ${money(double.tryParse(result.declaredCash) ?? 0)}\n'
              'Variance: ${money(double.tryParse(result.variance) ?? 0)}',
            ),
            actions: [
              TextButton(
                onPressed: () {
                  Navigator.pop(context);
                  // Clear the mock session provider so the cart gate redirects to till-open.
                  ref.read(cashierSessionProvider.notifier).state = null;
                  ref.read(cartProvider.notifier).clear();
                  context.go('/login');
                },
                child: const Text('Sign out'),
              ),
            ],
          ),
        );
      } else {
        // RECONCILE_INCOMPLETE — show mismatched op ids.
        setState(() {
          _error = 'Reconcile incomplete. '
              'Missing: ${result.missingClientOpIds.length}, '
              'Unexpected: ${result.unexpectedClientOpIds.length}. '
              'Sync all ops and retry.';
        });
      }
    } catch (e) {
      if (mounted) setState(() => _error = 'Close failed: $e');
    } finally {
      if (mounted) setState(() => _closing = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final session = ref.watch(cashierSessionProvider);

    return Scaffold(
      backgroundColor: theme.colorScheme.surfaceContainerLow,
      appBar: AppBar(
        title: const Text('Close till'),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () => context.go('/cart'),
        ),
      ),
      body: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 720),
          child: ListView(
            padding: const EdgeInsets.all(24),
            children: [
              // Session header card
              Card(
                elevation: 0,
                child: Padding(
                  padding: const EdgeInsets.all(20),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Icon(Icons.point_of_sale, color: theme.colorScheme.primary),
                          const SizedBox(width: 8),
                          Text(
                            '${session?.tillCode ?? "—"} · ${session?.tillName ?? ""}',
                            style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w600),
                          ),
                        ],
                      ),
                      const SizedBox(height: 12),
                      _kv(theme, 'Cashier', session?.cashierName ?? '—'),
                      _kv(theme, 'Branch', session?.branchName ?? '—'),
                      _kv(theme, 'Opened', _fmt(session?.openedAt ?? DateTime.now())),
                      _kv(theme, 'Closing', _fmt(DateTime.now())),
                      _kv(theme, 'Opening float',
                          money(session?.openingFloat ?? 0)),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 16),

              // Cash declaration
              Card(
                elevation: 0,
                child: Padding(
                  padding: const EdgeInsets.all(20),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      Text('Cash declaration', style: theme.textTheme.titleMedium),
                      const SizedBox(height: 6),
                      Text(
                        'Count the till drawer and declare what is physically in it.',
                        style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                      ),
                      const SizedBox(height: 16),
                      TextField(
                        controller: _declaredCash,
                        keyboardType: TextInputType.number,
                        style: theme.textTheme.headlineSmall,
                        decoration: InputDecoration(
                          labelText: 'Declared cash (TZS)',
                          prefixIcon: const Icon(Icons.payments_outlined),
                          border: const OutlineInputBorder(),
                          errorText: _error,
                        ),
                        onChanged: (_) => setState(() => _error = null),
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 24),

              // Action row
              Row(
                children: [
                  Expanded(
                    child: OutlinedButton.icon(
                      onPressed: _closing ? null : () => context.go('/cart'),
                      style: OutlinedButton.styleFrom(padding: const EdgeInsets.symmetric(vertical: 16)),
                      icon: const Icon(Icons.arrow_back),
                      label: const Text('Back to till'),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    flex: 2,
                    child: FilledButton.icon(
                      onPressed: _closing ? null : _close,
                      style: FilledButton.styleFrom(padding: const EdgeInsets.symmetric(vertical: 16)),
                      icon: _closing
                          ? const SizedBox(
                              width: 18,
                              height: 18,
                              child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                            )
                          : const Icon(Icons.lock),
                      label: Text(_closing ? 'Closing...' : 'Close till & sign out'),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _kv(ThemeData theme, String k, String v) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 2),
      child: Row(
        children: [
          SizedBox(
            width: 120,
            child: Text(k, style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
          ),
          Text(v, style: theme.textTheme.bodyMedium),
        ],
      ),
    );
  }

  String _fmt(DateTime t) {
    String two(int n) => n.toString().padLeft(2, '0');
    return '${t.year}-${two(t.month)}-${two(t.day)} ${two(t.hour)}:${two(t.minute)}';
  }
}
