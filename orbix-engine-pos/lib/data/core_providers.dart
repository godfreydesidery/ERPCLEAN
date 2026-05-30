/// Root infrastructure providers — logger, SharedPreferences, base Dio.
///
/// These sit at the bottom of the dependency graph: auth + sync + config all
/// import from here; this file imports from none of them.
///
/// [sharedPrefsProvider] must be overridden before the widget tree builds:
///
///   final prefs = await SharedPreferences.getInstance();
///   runApp(ProviderScope(
///     overrides: [sharedPrefsProvider.overrideWithValue(prefs)],
///     child: OrbixPosApp(),
///   ));
library;

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:logger/logger.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'config/pos_config_store.dart';

// ---------------------------------------------------------------------------
// Logger
// ---------------------------------------------------------------------------

final loggerProvider = Provider<Logger>((ref) => Logger());

// ---------------------------------------------------------------------------
// SharedPreferences — overridden in main() after async init
// ---------------------------------------------------------------------------

final sharedPrefsProvider = Provider<SharedPreferences>((ref) {
  throw StateError(
    'sharedPrefsProvider not initialised. '
    'Override it in ProviderScope before the widget tree builds.',
  );
});

// ---------------------------------------------------------------------------
// Config store (reads from SharedPreferences)
// ---------------------------------------------------------------------------

final posConfigStoreProvider = Provider<PosConfigStore>((ref) {
  return PosConfigStore(ref.watch(sharedPrefsProvider));
});

final apiBaseUrlProvider = Provider<String>((ref) {
  return ref.watch(posConfigStoreProvider).apiBaseUrl;
});

final deviceIdProvider = Provider<String>((ref) {
  return ref.watch(posConfigStoreProvider).deviceId;
});

// ---------------------------------------------------------------------------
// Base (un-intercepted) Dio — used by AuthRepository only.
// Do NOT add the AuthInterceptor here; that would cause re-entrant 401 loops.
// ---------------------------------------------------------------------------

final baseDioProvider = Provider<Dio>((ref) {
  final baseUrl = ref.watch(apiBaseUrlProvider);
  return Dio(BaseOptions(
    baseUrl: baseUrl,
    connectTimeout: const Duration(seconds: 10),
    receiveTimeout: const Duration(seconds: 30),
    validateStatus: (status) => status != null,
  ));
});
