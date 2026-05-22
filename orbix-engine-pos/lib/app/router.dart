import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../features/auth/login_screen.dart';
import '../features/cart/cart_screen.dart';
import '../features/cart/held_carts_screen.dart';
import '../features/payment/payment_screen.dart';
import '../features/payment/receipt_screen.dart';
import '../features/refund/refund_screen.dart';
import '../features/settings/settings_screen.dart';
import '../features/supervisor/supervisor_pin_screen.dart';
import '../features/till_session/till_close_screen.dart';
import '../features/till_session/till_open_screen.dart';

final routerProvider = Provider<GoRouter>((ref) {
  return GoRouter(
    initialLocation: '/login',
    routes: [
      GoRoute(path: '/login',      builder: (_, __) => const LoginScreen()),
      GoRoute(path: '/till/open',  builder: (_, __) => const TillOpenScreen()),
      GoRoute(path: '/cart',       builder: (_, __) => const CartScreen()),
      GoRoute(path: '/held',       builder: (_, __) => const HeldCartsScreen()),
      GoRoute(path: '/payment',    builder: (_, __) => const PaymentScreen()),
      GoRoute(path: '/receipt',    builder: (_, __) => const ReceiptScreen()),
      GoRoute(path: '/refund',     builder: (_, __) => const RefundScreen()),
      GoRoute(path: '/supervisor', builder: (_, __) => const SupervisorPinScreen()),
      GoRoute(path: '/settings',   builder: (_, __) => const SettingsScreen()),
      GoRoute(path: '/till/close', builder: (_, __) => const TillCloseScreen()),
    ],
  );
});
