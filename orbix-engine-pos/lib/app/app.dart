import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'router.dart';
import 'theme.dart';

class OrbixPosApp extends ConsumerWidget {
  const OrbixPosApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final router = ref.watch(routerProvider);
    return MaterialApp.router(
      title: 'Orbix Engine POS',
      theme: orbixLightTheme(),
      darkTheme: orbixDarkTheme(),
      routerConfig: router,
      debugShowCheckedModeBanner: false,
    );
  }
}
