import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../_demo/mocks.dart';
import '../catalog/catalog_providers.dart';
import '../till_session/till_session_providers.dart' show cashierSessionProvider;
import 'panes/cart_pane.dart';
import 'panes/pharmacy_pane.dart';
import 'panes/restaurant_pane.dart';
import 'panes/retail_pane.dart';
import 'panes/supermarket_pane.dart';
import 'panes/supermarket_right_pane.dart';
import 'panes/wholesale_pane.dart';

/// Shell for the cashier workspace. Topbar + per-mode left pane + cart pane.
/// All modes share the same cart state — only the input UX changes.
class CartScreen extends ConsumerStatefulWidget {
  const CartScreen({super.key});

  @override
  ConsumerState<CartScreen> createState() => _CartScreenState();
}

class _CartScreenState extends ConsumerState<CartScreen> {
  @override
  void initState() {
    super.initState();
    // Trigger a catalog pull when entering the sell screen.  This fires after
    // the first frame so the ProviderScope tree is fully initialised.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      triggerCatalogSync(ref);
    });
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final session = ref.watch(cashierSessionProvider);
    final mode = ref.watch(modeProvider);

    if (session == null) {
      WidgetsBinding.instance.addPostFrameCallback((_) => context.go('/till/open'));
      return const Scaffold(body: SizedBox.shrink());
    }

    return Scaffold(
      backgroundColor: theme.colorScheme.surfaceContainerLow,
      appBar: _TopBar(session: session, mode: mode),
      body: Row(
        children: [
          // Left pane — mode-specific (item input UX)
          Expanded(flex: 7, child: _leftPane(mode)),
          // Divider
          Container(width: 1, color: theme.dividerColor),
          // Right pane — universal cart for most modes; supermarket gets a
          // numpad instead (its cart lines already live in the left table)
          // and uses a thinner pane to leave the spreadsheet more space.
          SizedBox(width: _rightPaneWidth(mode), child: _rightPane(mode)),
        ],
      ),
    );
  }

  Widget _leftPane(PosMode mode) {
    return switch (mode) {
      PosMode.retail => const RetailPane(),
      PosMode.supermarket => const SupermarketPane(),
      PosMode.pharmacy => const PharmacyPane(),
      PosMode.wholesale => const WholesalePane(),
      PosMode.restaurant => const RestaurantPane(),
    };
  }

  Widget _rightPane(PosMode mode) {
    return switch (mode) {
      PosMode.supermarket => const SupermarketRightPane(),
      _ => const CartPane(),
    };
  }

  double _rightPaneWidth(PosMode mode) {
    return switch (mode) {
      PosMode.supermarket => 260, // just enough for the 3-col tendered numpad
      _ => 420,
    };
  }
}

class _TopBar extends StatelessWidget implements PreferredSizeWidget {
  final CashierSession session;
  final PosMode mode;
  const _TopBar({required this.session, required this.mode});

  @override
  Size get preferredSize => const Size.fromHeight(57);

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return AppBar(
      backgroundColor: theme.colorScheme.surface,
      surfaceTintColor: Colors.transparent,
      titleSpacing: 16,
      title: Row(
        children: [
          Icon(Icons.point_of_sale, size: 22, color: theme.colorScheme.primary),
          const SizedBox(width: 8),
          Text('${session.tillCode} · ${session.tillName}',
              style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w600)),
          const SizedBox(width: 12),
          Chip(
            label: Text(session.branchName),
            visualDensity: VisualDensity.compact,
            backgroundColor: theme.colorScheme.surfaceContainerHigh,
            side: BorderSide.none,
          ),
          const SizedBox(width: 8),
          // Mode badge — distinct colour so cashier always knows the mode
          Tooltip(
            message: '${mode.label} mode — ${mode.tagline}',
            child: Chip(
              avatar: Icon(mode.icon, size: 16, color: theme.colorScheme.onPrimaryContainer),
              label: Text(mode.label,
                  style: TextStyle(
                    color: theme.colorScheme.onPrimaryContainer,
                    fontWeight: FontWeight.w600,
                  )),
              backgroundColor: theme.colorScheme.primaryContainer,
              side: BorderSide.none,
              visualDensity: VisualDensity.compact,
            ),
          ),
        ],
      ),
      actions: [
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 4),
          child: Row(children: [
            const Icon(Icons.person_outline, size: 18),
            const SizedBox(width: 6),
            Text(session.cashierName),
          ]),
        ),
        const SizedBox(width: 8),
        Tooltip(
          message: 'Refund a previous sale — pick the receipt, then the lines / qty to return.',
          child: TextButton.icon(
            onPressed: () => context.push('/refund'),
            icon: const Icon(Icons.assignment_return_outlined, size: 18),
            label: const Text('Refund'),
          ),
        ),
        Tooltip(
          message: 'Authorise a void, discount, or refund (requires supervisor PIN).',
          child: TextButton.icon(
            onPressed: () => context.push('/supervisor'),
            icon: const Icon(Icons.shield_outlined, size: 18),
            label: const Text('Supervisor'),
          ),
        ),
        Tooltip(
          message: 'Hardware, printer, scanner, drawer.',
          child: TextButton.icon(
            onPressed: () => context.push('/settings'),
            icon: const Icon(Icons.settings_outlined, size: 18),
            label: const Text('Settings'),
          ),
        ),
        Tooltip(
          message: 'Mid-shift read — view session totals without closing.',
          child: TextButton.icon(
            onPressed: () => context.push('/till/x-report'),
            icon: const Icon(Icons.bar_chart_outlined, size: 18),
            label: const Text('X-report'),
          ),
        ),
        PopupMenuButton<String>(
          tooltip: 'Cash movements',
          icon: const Icon(Icons.payments_outlined),
          onSelected: (route) => context.push(route),
          itemBuilder: (_) => const [
            PopupMenuItem(
              value: '/cash/pickup',
              child: ListTile(
                leading: Icon(Icons.outbox_outlined),
                title: Text('Cash pickup'),
                subtitle: Text('Move cash to safe/bank'),
                contentPadding: EdgeInsets.zero,
              ),
            ),
            PopupMenuItem(
              value: '/cash/petty',
              child: ListTile(
                leading: Icon(Icons.receipt_long_outlined),
                title: Text('Petty cash'),
                subtitle: Text('Small payout from drawer'),
                contentPadding: EdgeInsets.zero,
              ),
            ),
          ],
        ),
        Tooltip(
          message: 'End the shift — declare cash, generate Z-report, sign out.',
          child: TextButton.icon(
            onPressed: () => context.go('/till/close'),
            icon: const Icon(Icons.lock_outline, size: 18),
            label: const Text('Close till'),
          ),
        ),
        const SizedBox(width: 12),
      ],
      bottom: PreferredSize(
        preferredSize: const Size.fromHeight(1),
        child: Container(height: 1, color: theme.dividerColor),
      ),
    );
  }
}
