import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../features/auth/login_screen.dart';
import '../features/route/route_screen.dart';
import '../features/visit/visit_screen.dart';
import '../features/sales_sheet/sales_sheet_screen.dart';

final _routerProvider = Provider<GoRouter>((ref) {
  return GoRouter(
    initialLocation: '/login',
    routes: [
      GoRoute(path: '/login', builder: (_, __) => const LoginScreen()),
      GoRoute(path: '/route', builder: (_, __) => const RouteScreen()),
      GoRoute(path: '/visit', builder: (_, __) => const VisitScreen()),
      GoRoute(path: '/sheet', builder: (_, __) => const SalesSheetScreen()),
    ],
  );
});

class OrbixWmsApp extends ConsumerWidget {
  const OrbixWmsApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final router = ref.watch(_routerProvider);
    return MaterialApp.router(
      title: 'Orbix Engine WMS',
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF1A4FB5)),
      ),
      routerConfig: router,
      debugShowCheckedModeBanner: false,
    );
  }
}
