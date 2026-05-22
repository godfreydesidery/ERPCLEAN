import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../_demo/mocks.dart';

class TillOpenScreen extends ConsumerStatefulWidget {
  const TillOpenScreen({super.key});

  @override
  ConsumerState<TillOpenScreen> createState() => _TillOpenScreenState();
}

class _TillOpenScreenState extends ConsumerState<TillOpenScreen> {
  final _floatCtrl = TextEditingController(text: '100000');
  String _tillCode = 'TILL-1';

  static const _tills = [
    ('TILL-1', 'Front counter'),
    ('TILL-2', 'Express lane'),
    ('TILL-3', 'Bakery counter'),
  ];

  @override
  void dispose() {
    _floatCtrl.dispose();
    super.dispose();
  }

  void _openTill() {
    final tillName = _tills.firstWhere((t) => t.$1 == _tillCode).$2;
    final amount = double.tryParse(_floatCtrl.text.replaceAll(',', '')) ?? 0;
    ref.read(sessionProvider.notifier).open(CashierSession(
          cashierName: 'Cashier One',
          tillCode: _tillCode,
          tillName: tillName,
          branchName: 'Branch HQ',
          openedAt: DateTime.now(),
          openingFloat: amount,
          currency: 'TZS',
        ));
    context.go('/cart');
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final mode = ref.watch(modeProvider);
    return Scaffold(
      backgroundColor: theme.colorScheme.surfaceContainerLow,
      body: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 720),
          child: SingleChildScrollView(
            padding: const EdgeInsets.symmetric(vertical: 24),
            child: Card(
              elevation: 0,
              margin: const EdgeInsets.symmetric(horizontal: 24),
              child: Padding(
                padding: const EdgeInsets.all(28),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Row(
                      children: [
                        Icon(Icons.point_of_sale_outlined, size: 28, color: theme.colorScheme.primary),
                        const SizedBox(width: 12),
                        Text(
                          'Open till session',
                          style: theme.textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w600),
                        ),
                      ],
                    ),
                    const SizedBox(height: 6),
                    Text(
                      'Welcome, Cashier One — pick a till, choose a mode, declare your opening float.',
                      style: theme.textTheme.bodyMedium?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                    ),
                    const SizedBox(height: 24),

                    // ---- Mode picker ----
                    Text('Mode', style: theme.textTheme.titleSmall),
                    const SizedBox(height: 8),
                    Column(
                      children: PosMode.values.map((m) {
                        final selected = mode == m;
                        return Padding(
                          padding: const EdgeInsets.only(bottom: 8),
                          child: InkWell(
                            borderRadius: BorderRadius.circular(10),
                            onTap: () => ref.read(modeProvider.notifier).state = m,
                            child: Container(
                              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                              decoration: BoxDecoration(
                                color: selected
                                    ? theme.colorScheme.primaryContainer
                                    : theme.colorScheme.surfaceContainerLow,
                                border: Border.all(
                                  color: selected ? theme.colorScheme.primary : theme.dividerColor,
                                  width: selected ? 1.5 : 1,
                                ),
                                borderRadius: BorderRadius.circular(10),
                              ),
                              child: Row(
                                children: [
                                  Icon(
                                    m.icon,
                                    size: 22,
                                    color: selected
                                        ? theme.colorScheme.onPrimaryContainer
                                        : theme.colorScheme.onSurfaceVariant,
                                  ),
                                  const SizedBox(width: 12),
                                  Expanded(
                                    child: Column(
                                      crossAxisAlignment: CrossAxisAlignment.start,
                                      children: [
                                        Text(
                                          m.label,
                                          style: theme.textTheme.titleSmall?.copyWith(
                                            fontWeight: FontWeight.w600,
                                            color: selected ? theme.colorScheme.onPrimaryContainer : null,
                                          ),
                                        ),
                                        Text(
                                          m.tagline,
                                          style: theme.textTheme.bodySmall?.copyWith(
                                            color: selected
                                                ? theme.colorScheme.onPrimaryContainer.withValues(alpha: 0.85)
                                                : theme.colorScheme.onSurfaceVariant,
                                          ),
                                        ),
                                      ],
                                    ),
                                  ),
                                  if (selected)
                                    Icon(Icons.check_circle, color: theme.colorScheme.primary, size: 20),
                                ],
                              ),
                            ),
                          ),
                        );
                      }).toList(),
                    ),
                    const SizedBox(height: 16),

                    // ---- Till ----
                    DropdownButtonFormField<String>(
                      initialValue: _tillCode,
                      decoration: const InputDecoration(
                        labelText: 'Till',
                        prefixIcon: Icon(Icons.point_of_sale),
                        border: OutlineInputBorder(),
                      ),
                      items: _tills
                          .map((t) => DropdownMenuItem(
                                value: t.$1,
                                child: Text('${t.$1} · ${t.$2}'),
                              ))
                          .toList(),
                      onChanged: (v) => setState(() => _tillCode = v ?? _tillCode),
                    ),
                    const SizedBox(height: 16),

                    // ---- Opening float ----
                    TextField(
                      controller: _floatCtrl,
                      keyboardType: TextInputType.number,
                      decoration: const InputDecoration(
                        labelText: 'Opening cash float (TZS)',
                        prefixIcon: Icon(Icons.payments_outlined),
                        border: OutlineInputBorder(),
                        hintText: 'e.g. 100000',
                      ),
                    ),
                    const SizedBox(height: 12),
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: theme.colorScheme.surfaceContainerHigh,
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Row(
                        children: [
                          Icon(Icons.info_outline, size: 18, color: theme.colorScheme.onSurfaceVariant),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              'Foreign currencies (USD, EUR, KES…) are added at close-till declaration time.',
                              style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 24),

                    Row(
                      children: [
                        Expanded(
                          child: OutlinedButton(
                            onPressed: () => context.go('/login'),
                            style: OutlinedButton.styleFrom(padding: const EdgeInsets.symmetric(vertical: 14)),
                            child: const Text('Sign out'),
                          ),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          flex: 2,
                          child: FilledButton.icon(
                            onPressed: _openTill,
                            style: FilledButton.styleFrom(padding: const EdgeInsets.symmetric(vertical: 14)),
                            icon: const Icon(Icons.lock_open),
                            label: const Text('Open till'),
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
