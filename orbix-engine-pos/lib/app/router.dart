import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../features/auth/login_screen.dart';
import '../features/till_session/till_open_screen.dart';
import '../features/cart/cart_screen.dart';
import '../features/till_session/till_close_screen.dart';

final routerProvider = Provider<GoRouter>((ref) {
  return GoRouter(
    initialLocation: '/login',
    routes: [
      GoRoute(path: '/login', builder: (_, __) => const LoginScreen()),
      GoRoute(path: '/till/open', builder: (_, __) => const TillOpenScreen()),
      GoRoute(path: '/cart', builder: (_, __) => const CartScreen()),
      GoRoute(path: '/till/close', builder: (_, __) => const TillCloseScreen()),
    ],
  );
});
