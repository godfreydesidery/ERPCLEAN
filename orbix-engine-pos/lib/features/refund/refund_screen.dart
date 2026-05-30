/// Refund / return screen (US-POS-012).
///
/// Searches local Drift [PosSales] (+ lines) rather than the mock list.
/// Cashier picks a past sale, selects line(s) + qty to refund, enters a
/// reason, then processes after supervisor PIN.
///
/// The refund is enqueued as a POS_SALE outbox op with kind=REFUND — matching
/// the same dispatcher/backend contract as a regular sale.
library;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../data/core_providers.dart' show deviceIdProvider;
import '../_demo/mocks.dart' show money;
import '../payment/pos_sale_providers.dart';
import '../payment/pos_sale_repository.dart';
import '../till_session/till_session_providers.dart' show activeTillSessionProvider;

class RefundScreen extends ConsumerStatefulWidget {
  const RefundScreen({super.key});

  @override
  ConsumerState<RefundScreen> createState() => _RefundScreenState();
}

class _RefundScreenState extends ConsumerState<RefundScreen> {
  LocalSale? _picked;
  final _searchCtrl = TextEditingController();
  String _query = '';
  /// Refund qty per line index of _picked.
  final Map<int, double> _refundQty = {};
  final _reasonCtrl = TextEditingController();
  bool _processing = false;
  String? _processError;

  @override
  void dispose() {
    _searchCtrl.dispose();
    _reasonCtrl.dispose();
    super.dispose();
  }

  void _pick(LocalSale s) {
    setState(() {
      _picked = s;
      _refundQty.clear();
    });
  }

  double _refundTotal() {
    if (_picked == null) return 0;
    double t = 0;
    _refundQty.forEach((idx, qty) {
      final line = _picked!.lines[idx];
      t += line.unitPrice * qty;
    });
    return t;
  }

  Future<void> _process() async {
    final pinOk = await Navigator.push<bool>(
      context,
      MaterialPageRoute(builder: (_) => const _InlinePinPrompt()),
    );
    if (pinOk != true || !mounted) return;

    setState(() {
      _processing = true;
      _processError = null;
    });

    try {
      final saleRepo = ref.read(posSaleRepositoryProvider);
      final activeSession = await ref.read(activeTillSessionProvider.future);
      final deviceId = ref.read(deviceIdProvider);

      if (activeSession == null || activeSession.clientOpId.isEmpty) {
        setState(() => _processError = 'No open till session — open the till first.');
        return;
      }

      final refundLines = <RefundLine>[];
      _refundQty.forEach((idx, qty) {
        final l = _picked!.lines[idx];
        refundLines.add(RefundLine(
          itemId: l.itemId,
          qty: qty,
          unitPrice: l.unitPrice,
          lineTotal: l.unitPrice * qty,
        ));
      });

      final now = DateTime.now();
      final businessDate =
          '${now.year}-${now.month.toString().padLeft(2, '0')}-${now.day.toString().padLeft(2, '0')}';

      await saleRepo.recordRefund(
        sessionLocalId: activeSession.localId,
        sessionClientOpId: activeSession.clientOpId,
        sessionServerId: activeSession.serverEntityId,
        originalSaleClientOpId: _picked!.clientOpId,
        originalSaleServerUid: _picked!.serverEntityUid,
        customerId: 1, // walk-in default; actual customer not stored on local sale yet
        userId: activeSession.openedBy,
        lines: refundLines,
        reason: _reasonCtrl.text.trim(),
        deviceId: deviceId,
        businessDate: businessDate,
      );

      if (!mounted) return;
      await showDialog<void>(
        context: context,
        builder: (_) => AlertDialog(
          icon: const Icon(Icons.check_circle, size: 48, color: Colors.green),
          title: const Text('Refund processed'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text('Refunded ${money(_refundTotal())}'),
              const SizedBox(height: 6),
              Text(
                'Original: ${_picked!.receiptNo}',
                style: const TextStyle(
                    fontFamily: 'monospace', color: Colors.grey),
              ),
              const SizedBox(height: 6),
              const Text(
                'Refund queued in outbox — will sync when online.',
                style: TextStyle(fontSize: 12, color: Colors.grey),
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () {
                Navigator.pop(context);
                context.go('/cart');
              },
              child: const Text('Done'),
            ),
          ],
        ),
      );
    } catch (e) {
      if (mounted) setState(() => _processError = 'Failed: $e');
    } finally {
      if (mounted) setState(() => _processing = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    // Load recent sales asynchronously from Drift.
    final salesAsync = ref.watch(_recentSalesProvider);

    return Scaffold(
      backgroundColor: theme.colorScheme.surfaceContainerLow,
      appBar: AppBar(
        title: const Text('Refund / return'),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () => context.pop(),
        ),
      ),
      body: Row(
        children: [
          // Left: past-sales picker
          SizedBox(
            width: 360,
            child: Container(
              margin: const EdgeInsets.fromLTRB(16, 16, 8, 16),
              decoration: BoxDecoration(
                color: theme.colorScheme.surface,
                borderRadius: BorderRadius.circular(12),
              ),
              child: Column(
                children: [
                  Padding(
                    padding: const EdgeInsets.all(12),
                    child: TextField(
                      controller: _searchCtrl,
                      decoration: const InputDecoration(
                        hintText: 'Find receipt number or date…',
                        prefixIcon: Icon(Icons.search),
                        border: OutlineInputBorder(),
                        isDense: true,
                      ),
                      onChanged: (v) => setState(() => _query = v),
                    ),
                  ),
                  Divider(height: 1, color: theme.dividerColor),
                  Expanded(
                    child: salesAsync.when(
                      loading: () => const Center(
                          child: CircularProgressIndicator()),
                      error: (e, _) => Center(
                        child: Text('Error loading sales: $e',
                            style: TextStyle(
                                color: theme.colorScheme.error)),
                      ),
                      data: (sales) {
                        final filtered = _filterSales(sales);
                        if (filtered.isEmpty) {
                          return Center(
                            child: Text(
                              _query.isEmpty
                                  ? 'No recent sales on this device'
                                  : 'No sales match "$_query"',
                              style: theme.textTheme.bodyMedium
                                  ?.copyWith(
                                      color: theme
                                          .colorScheme.outline),
                            ),
                          );
                        }
                        return ListView.separated(
                          itemCount: filtered.length,
                          separatorBuilder: (_, __) => Divider(
                              height: 1,
                              color: theme.dividerColor
                                  .withValues(alpha: 0.4)),
                          itemBuilder: (_, i) {
                            final s = filtered[i];
                            final selected =
                                _picked?.id == s.id;
                            return InkWell(
                              onTap: () => _pick(s),
                              child: Container(
                                color: selected
                                    ? theme
                                        .colorScheme
                                        .primaryContainer
                                        .withValues(alpha: 0.4)
                                    : null,
                                padding:
                                    const EdgeInsets.symmetric(
                                        horizontal: 14,
                                        vertical: 10),
                                child: Column(
                                  crossAxisAlignment:
                                      CrossAxisAlignment.start,
                                  children: [
                                    Row(
                                      children: [
                                        Expanded(
                                          child: Text(
                                            s.receiptNo,
                                            style: theme
                                                .textTheme
                                                .titleSmall
                                                ?.copyWith(
                                              fontFamily: 'monospace',
                                              fontWeight: FontWeight.w700,
                                            ),
                                            overflow: TextOverflow.ellipsis,
                                          ),
                                        ),
                                        Text(
                                          money(s.total),
                                          style: theme
                                              .textTheme.titleSmall
                                              ?.copyWith(
                                            color: theme
                                                .colorScheme.primary,
                                            fontWeight: FontWeight.w700,
                                          ),
                                        ),
                                      ],
                                    ),
                                    const SizedBox(height: 2),
                                    Text(
                                      '${_ago(s.saleAt)}  ·  '
                                      '${s.lines.length} line${s.lines.length == 1 ? "" : "s"}',
                                      style: theme
                                          .textTheme.labelSmall
                                          ?.copyWith(
                                              color: theme.colorScheme
                                                  .onSurfaceVariant),
                                    ),
                                    if (!s.synced)
                                      Text(
                                        'Not yet synced',
                                        style: theme
                                            .textTheme.labelSmall
                                            ?.copyWith(
                                                color: theme
                                                    .colorScheme.tertiary),
                                      ),
                                  ],
                                ),
                              ),
                            );
                          },
                        );
                      },
                    ),
                  ),
                ],
              ),
            ),
          ),

          // Right: line picker + reason + process
          Expanded(
            child: Container(
              margin: const EdgeInsets.fromLTRB(8, 16, 16, 16),
              decoration: BoxDecoration(
                color: theme.colorScheme.surface,
                borderRadius: BorderRadius.circular(12),
              ),
              padding: const EdgeInsets.all(16),
              child: _picked == null
                  ? _emptyRight(theme)
                  : _detail(theme),
            ),
          ),
        ],
      ),
    );
  }

  List<LocalSale> _filterSales(List<LocalSale> sales) {
    if (_query.isEmpty) return sales;
    final q = _query.toLowerCase();
    return sales
        .where((s) =>
            s.receiptNo.toLowerCase().contains(q) ||
            s.clientOpId.toLowerCase().contains(q))
        .toList();
  }

  Widget _emptyRight(ThemeData theme) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.receipt_long,
              size: 72, color: theme.colorScheme.outline),
          const SizedBox(height: 12),
          Text('Pick a receipt to refund',
              style: theme.textTheme.titleMedium),
          const SizedBox(height: 4),
          Text(
            'Select from the list on the left, then tick the line(s) — full or partial qty.',
            style: theme.textTheme.bodySmall
                ?.copyWith(color: theme.colorScheme.onSurfaceVariant),
            textAlign: TextAlign.center,
          ),
        ],
      ),
    );
  }

  Widget _detail(ThemeData theme) {
    final s = _picked!;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Row(
          children: [
            Expanded(
              child: Text(
                s.receiptNo,
                style: theme.textTheme.titleLarge?.copyWith(
                    fontFamily: 'monospace',
                    fontWeight: FontWeight.w700),
                overflow: TextOverflow.ellipsis,
              ),
            ),
            const SizedBox(width: 12),
            Text('Original ${money(s.total)}',
                style: theme.textTheme.bodyMedium
                    ?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
          ],
        ),
        const SizedBox(height: 14),
        Expanded(
          child: s.lines.isEmpty
              ? Center(
                  child: Text('No line detail available',
                      style: theme.textTheme.bodyMedium),
                )
              : ListView.separated(
                  itemCount: s.lines.length,
                  separatorBuilder: (_, __) => Divider(
                      height: 1,
                      color: theme.dividerColor.withValues(alpha: 0.4)),
                  itemBuilder: (_, i) {
                    final line = s.lines[i];
                    final picked = _refundQty[i] ?? 0;
                    return Padding(
                      padding: const EdgeInsets.symmetric(vertical: 8),
                      child: Row(
                        children: [
                          Checkbox(
                            value: picked > 0,
                            onChanged: (v) => setState(() {
                              if (v == true) {
                                _refundQty[i] = line.qty;
                              } else {
                                _refundQty.remove(i);
                              }
                            }),
                          ),
                          Expanded(
                            child: Column(
                              crossAxisAlignment:
                                  CrossAxisAlignment.start,
                              children: [
                                Text(line.itemName,
                                    style:
                                        theme.textTheme.bodyMedium?.copyWith(
                                            fontWeight:
                                                FontWeight.w600)),
                                Text(
                                  '${line.itemCode}  ·  '
                                  '${line.qty.toInt()} sold @ ${money(line.unitPrice)}',
                                  style: theme.textTheme.labelSmall
                                      ?.copyWith(
                                          color: theme.colorScheme
                                              .onSurfaceVariant),
                                ),
                              ],
                            ),
                          ),
                          SizedBox(
                            width: 130,
                            child: Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                IconButton(
                                  visualDensity:
                                      VisualDensity.compact,
                                  onPressed: picked <= 0
                                      ? null
                                      : () => setState(() {
                                            final next = picked - 1;
                                            if (next <= 0) {
                                              _refundQty.remove(i);
                                            } else {
                                              _refundQty[i] = next;
                                            }
                                          }),
                                  icon: const Icon(
                                      Icons.remove_circle_outline,
                                      size: 18),
                                ),
                                SizedBox(
                                  width: 40,
                                  child: Text(
                                    '${picked.toInt()}',
                                    textAlign: TextAlign.center,
                                    style: theme.textTheme.titleSmall
                                        ?.copyWith(
                                      fontWeight: FontWeight.w600,
                                      color: picked > 0
                                          ? theme.colorScheme.primary
                                          : theme.colorScheme.outline,
                                    ),
                                  ),
                                ),
                                IconButton(
                                  visualDensity:
                                      VisualDensity.compact,
                                  onPressed: picked >= line.qty
                                      ? null
                                      : () => setState(
                                          () => _refundQty[i] = picked + 1),
                                  icon: const Icon(
                                      Icons.add_circle_outline,
                                      size: 18),
                                ),
                              ],
                            ),
                          ),
                          SizedBox(
                            width: 100,
                            child: Text(
                              money(line.unitPrice * picked),
                              textAlign: TextAlign.right,
                              style: theme.textTheme.bodyMedium
                                  ?.copyWith(
                                fontWeight: FontWeight.w700,
                                color: picked > 0
                                    ? theme.colorScheme.primary
                                    : theme.colorScheme.outline,
                              ),
                            ),
                          ),
                        ],
                      ),
                    );
                  },
                ),
        ),
        Divider(color: theme.dividerColor),
        const SizedBox(height: 8),
        TextField(
          controller: _reasonCtrl,
          decoration: const InputDecoration(
            labelText: 'Reason for refund',
            hintText:
                'Damaged on opening / wrong item / change of mind…',
            border: OutlineInputBorder(),
            isDense: true,
          ),
          maxLines: 2,
        ),
        if (_processError != null) ...[
          const SizedBox(height: 6),
          Text(_processError!,
              style: TextStyle(color: Theme.of(context).colorScheme.error)),
        ],
        const SizedBox(height: 12),
        Row(
          children: [
            Text('Refund total', style: theme.textTheme.titleMedium),
            const Spacer(),
            Text(
              money(_refundTotal()),
              style: theme.textTheme.headlineSmall?.copyWith(
                color: theme.colorScheme.primary,
                fontWeight: FontWeight.w700,
              ),
            ),
          ],
        ),
        const SizedBox(height: 14),
        Row(
          children: [
            Expanded(
              child: OutlinedButton(
                onPressed: () => context.pop(),
                style: OutlinedButton.styleFrom(
                    padding: const EdgeInsets.symmetric(vertical: 14)),
                child: const Text('Cancel'),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              flex: 2,
              child: FilledButton.icon(
                onPressed: (_refundTotal() > 0 &&
                        _reasonCtrl.text.trim().isNotEmpty &&
                        !_processing)
                    ? _process
                    : null,
                style: FilledButton.styleFrom(
                    padding: const EdgeInsets.symmetric(vertical: 14)),
                icon: _processing
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(
                            strokeWidth: 2,
                            color: Colors.white),
                      )
                    : const Icon(Icons.shield_outlined, size: 18),
                label: Text(_processing
                    ? 'Processing…'
                    : 'Process refund (PIN required)'),
              ),
            ),
          ],
        ),
      ],
    );
  }

  String _ago(DateTime t) {
    final d = DateTime.now().difference(t);
    if (d.inMinutes < 1) return 'just now';
    if (d.inHours < 1) return '${d.inMinutes} min ago';
    if (d.inDays < 1) {
      return '${d.inHours}h ${d.inMinutes % 60}m ago';
    }
    return '${d.inDays}d ago';
  }
}

// ---------------------------------------------------------------------------
// Provider — watches recent sales from Drift (rebuilds on DB change)
// ---------------------------------------------------------------------------

/// One-shot async provider for recent sales.  Use FutureProvider.autoDispose
/// so the list refreshes each time the screen is visited.
final _recentSalesProvider = FutureProvider.autoDispose<List<LocalSale>>((ref) {
  final repo = ref.watch(posSaleRepositoryProvider);
  return repo.recentSales(limit: 60);
});

// ---------------------------------------------------------------------------
// Inline PIN prompt (supervisor auth)
// ---------------------------------------------------------------------------

class _InlinePinPrompt extends StatefulWidget {
  const _InlinePinPrompt();

  @override
  State<_InlinePinPrompt> createState() => _InlinePinPromptState();
}

class _InlinePinPromptState extends State<_InlinePinPrompt> {
  String _pin = '';
  String? _error;

  void _press(String d) {
    if (_pin.length >= 4) return;
    setState(() {
      _pin += d;
      _error = null;
    });
    if (_pin.length == 4) {
      Future<void>.delayed(const Duration(milliseconds: 200), () {
        if (!mounted) return;
        if (_pin == '1234') {
          Navigator.pop(context, true);
        } else {
          setState(() {
            _error = 'Wrong PIN';
            _pin = '';
          });
        }
      });
    }
  }

  void _back() {
    if (_pin.isEmpty) return;
    setState(() {
      _pin = _pin.substring(0, _pin.length - 1);
      _error = null;
    });
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      backgroundColor: theme.colorScheme.surfaceContainerLow,
      appBar: AppBar(
        title: const Text('Supervisor authorisation'),
        leading: IconButton(
            icon: const Icon(Icons.close),
            onPressed: () => Navigator.pop(context, false)),
      ),
      body: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 360),
          child: Card(
            elevation: 0,
            margin: const EdgeInsets.all(20),
            child: Padding(
              padding: const EdgeInsets.all(20),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(Icons.shield_outlined,
                      size: 44, color: theme.colorScheme.primary),
                  const SizedBox(height: 8),
                  Text('Enter PIN to authorise refund',
                      style: theme.textTheme.titleMedium),
                  Text('Hint: 1234 (mock)',
                      style: theme.textTheme.bodySmall
                          ?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
                  const SizedBox(height: 18),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: List.generate(4, (i) {
                      final filled = i < _pin.length;
                      return Container(
                        width: 16,
                        height: 16,
                        margin: const EdgeInsets.symmetric(horizontal: 5),
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          color: filled
                              ? theme.colorScheme.primary
                              : Colors.transparent,
                          border:
                              Border.all(color: theme.colorScheme.outline),
                        ),
                      );
                    }),
                  ),
                  if (_error != null) ...[
                    const SizedBox(height: 8),
                    Text(_error!,
                        style:
                            TextStyle(color: theme.colorScheme.error)),
                  ],
                  const SizedBox(height: 16),
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    alignment: WrapAlignment.center,
                    children: [
                      for (final d in [
                        '1', '2', '3', '4', '5', '6', '7', '8', '9'
                      ])
                        SizedBox(
                          width: 70,
                          height: 56,
                          child: FilledButton.tonal(
                            onPressed: () => _press(d),
                            child: Text(d,
                                style: const TextStyle(fontSize: 22)),
                          ),
                        ),
                      const SizedBox(width: 70, height: 56),
                      SizedBox(
                        width: 70,
                        height: 56,
                        child: FilledButton.tonal(
                          onPressed: () => _press('0'),
                          child: const Text('0',
                              style: TextStyle(fontSize: 22)),
                        ),
                      ),
                      SizedBox(
                        width: 70,
                        height: 56,
                        child: FilledButton.tonal(
                          onPressed: _back,
                          child: const Text('⌫',
                              style: TextStyle(fontSize: 22)),
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
    );
  }
}
