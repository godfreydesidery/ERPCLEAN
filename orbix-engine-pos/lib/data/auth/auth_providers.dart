/// Riverpod providers for auth layer: token store, repository, session state.
///
/// Imports only from core_providers — no circular dependency with sync_providers.
library;

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core_providers.dart';
import 'auth_repository.dart';
import 'auth_token_store.dart';

// ---------------------------------------------------------------------------
// Token store
// ---------------------------------------------------------------------------

final authTokenStoreProvider = Provider<AuthTokenStore>((ref) {
  return AuthTokenStore(ref.watch(sharedPrefsProvider));
});

// ---------------------------------------------------------------------------
// Auth repository (uses the BASE Dio — no interceptor to avoid re-entry)
// ---------------------------------------------------------------------------

final authRepositoryProvider = Provider<AuthRepository>((ref) {
  return AuthRepository(
    dio: ref.watch(baseDioProvider),
    tokenStore: ref.watch(authTokenStoreProvider),
    logger: ref.watch(loggerProvider),
  );
});

// ---------------------------------------------------------------------------
// Session state — reactive signal for login screen + router guard
// ---------------------------------------------------------------------------

class SessionNotifier extends StateNotifier<StoredSession?> {
  SessionNotifier(this._store) : super(_store.read());

  final AuthTokenStore _store;

  /// Called by LoginScreen after a successful login.
  void onLogin() {
    state = _store.read();
  }

  /// Called on explicit logout or when refresh fails.
  void onLogout() {
    state = null;
  }
}

final sessionProvider =
    StateNotifierProvider<SessionNotifier, StoredSession?>((ref) {
  final store = ref.watch(authTokenStoreProvider);
  return SessionNotifier(store);
});

/// True when there is a session with a valid (non-expired) access token.
final isAuthenticatedProvider = Provider<bool>((ref) {
  final session = ref.watch(sessionProvider);
  return session != null && session.accessTokenValid;
});
