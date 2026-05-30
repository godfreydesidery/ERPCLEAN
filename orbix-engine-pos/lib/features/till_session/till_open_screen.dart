import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../data/auth/auth_providers.dart' show sessionProvider;
import '../../data/sync/sync_providers.dart';
import '../_demo/mocks.dart' hide sessionProvider;
import 'till_session_providers.dart';

class TillOpenScreen extends ConsumerStatefulWidget {
  const TillOpenScreen({super.key});

  @override
  ConsumerState<TillOpenScreen> createState() => _TillOpenScreenState();
}

class _TillOpenScreenState extends ConsumerState<TillOpenScreen> {
  final _floatCtrl = TextEditingController(text: '100000');
  String _tillCode = 'TILL-1';
  bool _opening = false;
  String? _error;

  // Till configuration: code → (server till id, display name).
  // tillId must match the server-side till.id value. In v1 HQ has:
  //   TILL-1 = id 1 (Front counter), TILL-2 = id 2 (Express), TILL-3 = id 3 (Bakery)
  // These are seeded by the bootstrap / admin setup; match what the backend has.
  static const _tills = [
    (code: 'TILL-1', serverId: 1, name: 'Front counter'),
    (code: 'TILL-2', serverId: 2, name: 'Express lane'),
    (code: 'TILL-3', serverId: 3, name: 'Bakery counter'),
  ];

  @override
  void dispose() {
    _floatCtrl.dispose();
    super.dispose();
  }

  Future<void> _openTill() async {
    final till = _tills.firstWhere((t) => t.code == _tillCode);
    final amount = double.tryParse(_floatCtrl.text.replaceAll(',', '')) ?? 0;

    if (amount < 0) {
      setState(() => _error = 'Opening float cannot be negative');
      return;
    }

    setState(() {
      _opening = true;
      _error = null;
    });

    try {
      final sessionRepo = ref.read(tillSessionRepositoryProvider);
      // Pull the logged-in user identity from the auth session.
      // Falls back to userId=1 when not available (should never happen in prod).
      final authSession = ref.read(sessionProvider);
      final userId = authSession?.userId ?? 1;
      final cashierName = authSession?.displayName ?? 'Cashier';
      // Resolve branch name from the JWT session. The branch id is stored in
      // activeBranchId/defaultBranchId; the display name is not in the JWT
      // payload itself (the server doesn't include it). Use a config-derivable
      // label: "Branch <id>" until a branch-name pull is added.
      final branchId = authSession?.effectiveBranchId;
      final branchName = branchId != null ? 'Branch $branchId' : 'Branch';

      final businessDate = _todayDate();

      final result = await sessionRepo.openSession(
        tillId: till.serverId,
        tillCode: till.code,
        tillName: till.name,
        openedBy: userId,
        cashierName: cashierName,
        branchName: branchName,
        openingFloat: amount,
        businessDate: businessDate,
      );

      // Populate the legacy mock sessionProvider so existing screens (cart header,
      // X-report, till-close) that still watch it get a real session.
      // cashierSessionProvider carries the real display data.
      ref.read(cashierSessionProvider.notifier).state = CashierSession(
        cashierName: cashierName,
        tillCode: till.code,
        tillName: till.name,
        branchName: branchName,
        openedAt: result.session.openedAt,
        openingFloat: amount,
        currency: 'TZS',
      );

      // Kick off a sync immediately so the TILL_SESSION_OPEN op is pushed
      // and we get the server session id back as fast as possible.
      ref.read(outboxDispatcherProvider).flush().ignore();

      if (!mounted) return;
      context.go('/cart');
    } catch (e) {
      if (mounted) setState(() => _error = 'Failed to open till: $e');
    } finally {
      if (mounted) setState(() => _opening = false);
    }
  }

  String _todayDate() {
    final now = DateTime.now();
    final mm = now.month.toString().padLeft(2, '0');
    final dd = now.day.toString().padLeft(2, '0');
    return '${now.year}-$mm-$dd';
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
                      'Pick a till, choose a mode, declare your opening float.',
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
                                value: t.code,
                                child: Text('${t.code} · ${t.name}'),
                              ))
                          .toList(),
                      onChanged: (v) => setState(() => _tillCode = v ?? _tillCode),
                    ),
                    const SizedBox(height: 16),

                    // ---- Opening float ----
                    TextField(
                      controller: _floatCtrl,
                      keyboardType: TextInputType.number,
                      decoration: InputDecoration(
                        labelText: 'Opening cash float (TZS)',
                        prefixIcon: const Icon(Icons.payments_outlined),
                        border: const OutlineInputBorder(),
                        hintText: 'e.g. 100000',
                        errorText: _error,
                      ),
                      onChanged: (_) => setState(() => _error = null),
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
                            onPressed: _opening ? null : () => context.go('/login'),
                            style: OutlinedButton.styleFrom(padding: const EdgeInsets.symmetric(vertical: 14)),
                            child: const Text('Sign out'),
                          ),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          flex: 2,
                          child: FilledButton.icon(
                            onPressed: _opening ? null : _openTill,
                            style: FilledButton.styleFrom(padding: const EdgeInsets.symmetric(vertical: 14)),
                            icon: _opening
                                ? const SizedBox(
                                    width: 18,
                                    height: 18,
                                    child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                                  )
                                : const Icon(Icons.lock_open),
                            label: Text(_opening ? 'Opening...' : 'Open till'),
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
