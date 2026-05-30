/// X-report screen — mid-shift read of current session totals.
///
/// Non-resetting (does NOT close the session). Shows:
///   - Gross sales total and count
///   - Tender breakdown (from CompletedSale.tenders in the mock session)
///   - Cash pickups: count + total
///   - Petty cash: count + total
///   - Expected drawer cash = opening float + cash sales - pickups - petty cash
///
/// All data is read from local Drift/outbox so the report works offline.
/// The mock layer provides tenders; the outbox provides pickup/petty totals.
///
/// TODO: once the sell path is fully backed by the DB (not mocks), replace
/// the mock-session tender breakdown with an outbox query.
library;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../_demo/mocks.dart';
import '../cash/cash_movement_providers.dart';
import '../payment/payment_screen.dart' show TenderLine;
import 'till_session_providers.dart' show activeTillSessionProvider, cashierSessionProvider;

class XReportScreen extends ConsumerWidget {
  const XReportScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final session = ref.watch(cashierSessionProvider);

    if (session == null) {
      return Scaffold(
        appBar: AppBar(title: const Text('X-report')),
        body: const Center(child: Text('No active session')),
      );
    }

    return _XReportBody(session: session);
  }
}

class _XReportBody extends ConsumerStatefulWidget {
  final CashierSession session;
  const _XReportBody({required this.session});

  @override
  ConsumerState<_XReportBody> createState() => _XReportBodyState();
}

class _XReportBodyState extends ConsumerState<_XReportBody> {
  // ---------------------------------------------------------------------------
  // State: aggregated from outbox (async load on mount)
  // ---------------------------------------------------------------------------
  bool _loading = true;
  double _pickupTotal = 0;
  int _pickupCount = 0;
  double _pettyTotal = 0;
  int _pettyCount = 0;

  @override
  void initState() {
    super.initState();
    _loadMovements();
  }

  Future<void> _loadMovements() async {
    // Read the active session clientOpId from the Drift-backed provider.
    // Falls back to an empty string when no session is active (no ops found).
    final activeSession = await ref.read(activeTillSessionProvider.future);
    final sessionClientOpId = activeSession?.clientOpId ?? '';
    final repo = ref.read(cashMovementRepositoryProvider);
    final pickupTotal = await repo.cashPickupTotalForSession(sessionClientOpId);
    final pickupCount = await repo.cashPickupCountForSession(sessionClientOpId);
    final pettyTotal = await repo.pettyCashTotalForSession(sessionClientOpId);
    final pettyCount = await repo.pettyCashCountForSession(sessionClientOpId);
    if (mounted) {
      setState(() {
        _pickupTotal = pickupTotal;
        _pickupCount = pickupCount;
        _pettyTotal = pettyTotal;
        _pettyCount = pettyCount;
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final lastSale = ref.watch(lastSaleProvider);

    // Derive session totals from last-sale state (mock layer).
    // In the fully-wired path these come from the DB / outbox.
    final double saleTotal = lastSale?.total ?? 0;
    final int saleCount = lastSale != null ? 1 : 0;

    // Tender breakdown from the most recent sale (mock — real path reads outbox).
    final List<TenderLine> tenders = lastSale?.tenders ?? [];

    // Cash tendered from this session (simplified from mock: last sale cash tender).
    final double cashSales = tenders
        .where((t) => t.method == PaymentMethod.cash)
        .fold(0.0, (s, t) => s + t.amount);

    // Expected drawer = opening float + cash sales - pickups - petty cash.
    final double expectedDrawer = widget.session.openingFloat +
        cashSales -
        _pickupTotal -
        _pettyTotal;

    final now = DateTime.now();

    return Scaffold(
      backgroundColor: theme.colorScheme.surfaceContainerLow,
      appBar: AppBar(
        title: const Text('X-report (mid-shift)'),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () => context.pop(),
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            tooltip: 'Refresh',
            onPressed: () {
              setState(() => _loading = true);
              _loadMovements();
            },
          ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : Center(
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 720),
                child: ListView(
                  padding: const EdgeInsets.all(24),
                  children: [
                    // Header card
                    Card(
                      elevation: 0,
                      child: Padding(
                        padding: const EdgeInsets.all(20),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              children: [
                                Icon(Icons.summarize_outlined,
                                    color: theme.colorScheme.primary),
                                const SizedBox(width: 8),
                                Text('X-Report — mid-shift read',
                                    style: theme.textTheme.titleMedium
                                        ?.copyWith(fontWeight: FontWeight.w600)),
                                const Spacer(),
                                Container(
                                  padding: const EdgeInsets.symmetric(
                                      horizontal: 8, vertical: 4),
                                  decoration: BoxDecoration(
                                    color: theme.colorScheme.primaryContainer,
                                    borderRadius: BorderRadius.circular(6),
                                  ),
                                  child: Text('NON-RESETTING',
                                      style: theme.textTheme.labelSmall?.copyWith(
                                          color: theme.colorScheme.onPrimaryContainer,
                                          fontWeight: FontWeight.w700)),
                                ),
                              ],
                            ),
                            const SizedBox(height: 12),
                            _kv(theme, 'Till', '${widget.session.tillCode} · ${widget.session.tillName}'),
                            _kv(theme, 'Cashier', widget.session.cashierName),
                            _kv(theme, 'Branch', widget.session.branchName),
                            _kv(theme, 'Opened', _fmt(widget.session.openedAt)),
                            _kv(theme, 'Report time', _fmt(now)),
                          ],
                        ),
                      ),
                    ),
                    const SizedBox(height: 16),

                    // Sales totals
                    Card(
                      elevation: 0,
                      child: Padding(
                        padding: const EdgeInsets.all(20),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text('Sales', style: theme.textTheme.titleMedium),
                            const SizedBox(height: 16),
                            _row(theme, 'Transaction count', '$saleCount'),
                            _row(theme, 'Gross sales', money(saleTotal), bold: true),
                          ],
                        ),
                      ),
                    ),
                    const SizedBox(height: 16),

                    // Tender breakdown
                    if (tenders.isNotEmpty)
                      Card(
                        elevation: 0,
                        child: Padding(
                          padding: const EdgeInsets.all(20),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text('Tender breakdown', style: theme.textTheme.titleMedium),
                              const SizedBox(height: 16),
                              ...tenders.map(
                                (t) => _row(theme,
                                    '${t.method.icon}  ${t.method.label}',
                                    money(t.amount)),
                              ),
                              Container(
                                  height: 1,
                                  color: theme.dividerColor,
                                  margin: const EdgeInsets.symmetric(vertical: 8)),
                              _row(theme, 'Total tendered',
                                  money(tenders.fold(0.0, (s, t) => s + t.amount)),
                                  bold: true),
                            ],
                          ),
                        ),
                      )
                    else
                      Card(
                        elevation: 0,
                        child: Padding(
                          padding: const EdgeInsets.all(20),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text('Tender breakdown', style: theme.textTheme.titleMedium),
                              const SizedBox(height: 12),
                              Text('No completed sales this session',
                                  style: theme.textTheme.bodyMedium?.copyWith(
                                      color: theme.colorScheme.onSurfaceVariant)),
                            ],
                          ),
                        ),
                      ),
                    const SizedBox(height: 16),

                    // Cash movements
                    Card(
                      elevation: 0,
                      child: Padding(
                        padding: const EdgeInsets.all(20),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text('Cash movements', style: theme.textTheme.titleMedium),
                            const SizedBox(height: 16),
                            _row(theme, 'Opening float',
                                money(widget.session.openingFloat)),
                            _row(theme, 'Cash sales', money(cashSales)),
                            Container(
                                height: 1,
                                color: theme.dividerColor,
                                margin: const EdgeInsets.symmetric(vertical: 6)),
                            _row(theme,
                                'Cash pickups ($_pickupCount)',
                                _pickupCount > 0
                                    ? '- ${money(_pickupTotal)}'
                                    : money(0),
                                negative: _pickupCount > 0),
                            _row(theme,
                                'Petty cash ($_pettyCount)',
                                _pettyCount > 0
                                    ? '- ${money(_pettyTotal)}'
                                    : money(0),
                                negative: _pettyCount > 0),
                            Container(
                                height: 1,
                                color: theme.dividerColor,
                                margin: const EdgeInsets.symmetric(vertical: 8)),
                            _row(theme, 'Expected drawer cash',
                                money(expectedDrawer),
                                bold: true,
                                highlight: true),
                          ],
                        ),
                      ),
                    ),
                    const SizedBox(height: 24),

                    // Back button
                    OutlinedButton.icon(
                      onPressed: () => context.pop(),
                      style: OutlinedButton.styleFrom(
                          padding: const EdgeInsets.symmetric(vertical: 16)),
                      icon: const Icon(Icons.arrow_back),
                      label: const Text('Back to till'),
                    ),
                  ],
                ),
              ),
            ),
    );
  }

  Widget _kv(ThemeData theme, String k, String v) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 2),
        child: Row(
          children: [
            SizedBox(
              width: 110,
              child: Text(k,
                  style: theme.textTheme.bodySmall
                      ?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
            ),
            Text(v, style: theme.textTheme.bodyMedium),
          ],
        ),
      );

  Widget _row(ThemeData theme, String label, String value,
      {bool bold = false, bool highlight = false, bool negative = false}) {
    final labelStyle = bold
        ? theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700)
        : theme.textTheme.bodyLarge;
    final valueStyle = bold
        ? theme.textTheme.titleSmall?.copyWith(
            fontWeight: FontWeight.w700,
            color: highlight ? theme.colorScheme.primary : null)
        : theme.textTheme.bodyLarge?.copyWith(
            fontWeight: FontWeight.w600,
            color: negative ? theme.colorScheme.error : null);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        children: [
          Expanded(child: Text(label, style: labelStyle)),
          Text(value, style: valueStyle),
        ],
      ),
    );
  }

  String _fmt(DateTime t) {
    String two(int n) => n.toString().padLeft(2, '0');
    return '${t.year}-${two(t.month)}-${two(t.day)} ${two(t.hour)}:${two(t.minute)}';
  }
}
