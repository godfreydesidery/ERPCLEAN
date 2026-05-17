import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../_demo/mocks.dart';

class TillCloseScreen extends ConsumerStatefulWidget {
  const TillCloseScreen({super.key});

  @override
  ConsumerState<TillCloseScreen> createState() => _TillCloseScreenState();
}

class _TillCloseScreenState extends ConsumerState<TillCloseScreen> {
  // Mock totals for the closed shift
  static const _expectedCash = 482500.0;
  static const _expectedCard = 215000.0;
  static const _expectedMobileMoney = 91500.0;
  static const _salesCount = 47;

  final _declaredCash = TextEditingController(text: '482500');

  @override
  void dispose() {
    _declaredCash.dispose();
    super.dispose();
  }

  void _close() {
    showDialog<void>(
      context: context,
      builder: (_) => AlertDialog(
        icon: const Icon(Icons.lock, size: 48, color: Colors.green),
        title: const Text('Till closed'),
        content: const Text(
          'Z-report printed. The till session is now closed and posted. You will be signed out.',
        ),
        actions: [
          TextButton(
            onPressed: () {
              Navigator.pop(context);
              ref.read(sessionProvider.notifier).close();
              ref.read(cartProvider.notifier).clear();
              context.go('/login');
            },
            child: const Text('Sign out'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final session = ref.watch(sessionProvider);
    final declared = double.tryParse(_declaredCash.text.replaceAll(',', '')) ?? 0;
    final variance = declared - _expectedCash;

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
                      _kv(theme, 'Cashier', session?.cashierName ?? 'Cashier One'),
                      _kv(theme, 'Branch', session?.branchName ?? 'Branch HQ'),
                      _kv(theme, 'Opened', _fmt(session?.openedAt ?? DateTime.now().subtract(const Duration(hours: 8)))),
                      _kv(theme, 'Closing', _fmt(DateTime.now())),
                      _kv(theme, 'Sales count', '$_salesCount'),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 16),

              // Expected breakdown
              Card(
                elevation: 0,
                child: Padding(
                  padding: const EdgeInsets.all(20),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Expected per tender', style: theme.textTheme.titleMedium),
                      const SizedBox(height: 16),
                      _tenderRow(theme, '💵 Cash', _expectedCash, isPrimary: true),
                      _tenderRow(theme, '💳 Card', _expectedCard),
                      _tenderRow(theme, '📱 Mobile money', _expectedMobileMoney),
                      Container(height: 1, color: theme.dividerColor, margin: const EdgeInsets.symmetric(vertical: 10)),
                      _tenderRow(theme, 'Total takings', _expectedCash + _expectedCard + _expectedMobileMoney, bold: true),
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
                        decoration: const InputDecoration(
                          labelText: 'Declared cash (TZS)',
                          prefixIcon: Icon(Icons.payments_outlined),
                          border: OutlineInputBorder(),
                        ),
                        onChanged: (_) => setState(() {}),
                      ),
                      const SizedBox(height: 14),
                      Container(
                        padding: const EdgeInsets.all(14),
                        decoration: BoxDecoration(
                          color: variance == 0
                              ? theme.colorScheme.surfaceContainerHigh
                              : (variance > 0
                                  ? Colors.green.withValues(alpha: 0.12)
                                  : theme.colorScheme.errorContainer),
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: Row(
                          children: [
                            Icon(
                              variance == 0
                                  ? Icons.check_circle
                                  : (variance > 0 ? Icons.trending_up : Icons.trending_down),
                              color: variance == 0
                                  ? theme.colorScheme.primary
                                  : (variance > 0 ? Colors.green : theme.colorScheme.error),
                            ),
                            const SizedBox(width: 10),
                            Expanded(
                              child: Text(
                                variance == 0
                                    ? 'Balanced — declared matches expected'
                                    : (variance > 0
                                        ? 'Over by ${money(variance)} — review and recount if unexpected'
                                        : 'Short by ${money(variance.abs())} — supervisor approval required'),
                                style: theme.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.w500),
                              ),
                            ),
                          ],
                        ),
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
                      onPressed: () => context.go('/cart'),
                      style: OutlinedButton.styleFrom(padding: const EdgeInsets.symmetric(vertical: 16)),
                      icon: const Icon(Icons.arrow_back),
                      label: const Text('Back to till'),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    flex: 2,
                    child: FilledButton.icon(
                      onPressed: _close,
                      style: FilledButton.styleFrom(padding: const EdgeInsets.symmetric(vertical: 16)),
                      icon: const Icon(Icons.lock),
                      label: const Text('Close till & sign out'),
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
            width: 110,
            child: Text(k, style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
          ),
          Text(v, style: theme.textTheme.bodyMedium),
        ],
      ),
    );
  }

  Widget _tenderRow(ThemeData theme, String label, double amount, {bool isPrimary = false, bool bold = false}) {
    final style = bold
        ? theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700)
        : theme.textTheme.bodyLarge;
    final amountStyle = bold
        ? theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700, color: theme.colorScheme.primary)
        : style?.copyWith(color: isPrimary ? theme.colorScheme.primary : null, fontWeight: FontWeight.w600);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        children: [
          Expanded(child: Text(label, style: style)),
          Text(money(amount), style: amountStyle),
        ],
      ),
    );
  }

  String _fmt(DateTime t) {
    final two = (int n) => n.toString().padLeft(2, '0');
    return '${t.year}-${two(t.month)}-${two(t.day)} ${two(t.hour)}:${two(t.minute)}';
  }
}
