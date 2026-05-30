import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../data/auth/auth_providers.dart';
import '../../data/auth/auth_repository.dart';

class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  final _username = TextEditingController(text: '');
  final _password = TextEditingController(text: '');
  bool _busy = false;
  String? _errorMessage;

  @override
  void dispose() {
    _username.dispose();
    _password.dispose();
    super.dispose();
  }

  // ---------------------------------------------------------------------------
  // Sign in — real backend call with offline fallback
  // ---------------------------------------------------------------------------

  Future<void> _signIn() async {
    final username = _username.text.trim();
    final password = _password.text;
    if (username.isEmpty || password.isEmpty) {
      setState(() => _errorMessage = 'Username and password are required.');
      return;
    }

    setState(() {
      _busy = true;
      _errorMessage = null;
    });

    final authRepo = ref.read(authRepositoryProvider);
    final tokenStore = ref.read(authTokenStoreProvider);

    try {
      await authRepo.login(username: username, password: password);
      // Persist successful login to session notifier.
      ref.read(sessionProvider.notifier).onLogin();
      if (!mounted) return;
      context.go('/till/open');
    } on AuthException catch (e) {
      if (!mounted) return;
      setState(() {
        _busy = false;
        _errorMessage = e.message;
      });
    } on DioException {
      // Network unreachable — attempt offline login with stored session.
      if (!mounted) return;
      if (tokenStore.hasAnySession) {
        final session = tokenStore.read();
        if (session != null && session.username == username) {
          // Cashier previously logged in on this device — allow offline access.
          ref.read(sessionProvider.notifier).onLogin();
          context.go('/till/open');
          return;
        }
      }
      setState(() {
        _busy = false;
        _errorMessage =
            'Cannot reach server. Connect to the network or use the last logged-in account.';
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _busy = false;
        _errorMessage = 'Unexpected error: $e';
      });
    }
  }

  // ---------------------------------------------------------------------------
  // Biometric (mock — hardware integration is a separate task)
  // ---------------------------------------------------------------------------

  Future<void> _biometric() async {
    setState(() => _busy = true);
    final ok = await showDialog<bool>(
      context: context,
      barrierDismissible: false,
      builder: (dialogCtx) {
        // Biometric dialog auto-dismisses after a simulated scan delay.
        // dialogCtx is captured here and only used synchronously by Navigator
        // which checks mounted internally; this pattern is intentional.
        Future<void>.delayed(const Duration(milliseconds: 900), () {
          // ignore: use_build_context_synchronously
          final nav = Navigator.of(dialogCtx, rootNavigator: true);
          if (nav.canPop()) nav.pop(true);
        });
        return const AlertDialog(
          icon: Icon(Icons.fingerprint, size: 56, color: Colors.indigo),
          title: Text('Touch sensor'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              SizedBox(height: 4),
              Text('Place your finger on the reader…'),
              SizedBox(height: 12),
              LinearProgressIndicator(),
            ],
          ),
        );
      },
    );
    if (!mounted) return;
    if (ok == true) {
      context.go('/till/open');
    } else {
      setState(() => _busy = false);
    }
  }

  // ---------------------------------------------------------------------------
  // Build
  // ---------------------------------------------------------------------------

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      backgroundColor: theme.colorScheme.surfaceContainerLow,
      body: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 420),
          child: Card(
            elevation: 0,
            margin: const EdgeInsets.all(24),
            child: Padding(
              padding: const EdgeInsets.all(32),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  CircleAvatar(
                    radius: 32,
                    backgroundColor: theme.colorScheme.primaryContainer,
                    child: Icon(Icons.point_of_sale,
                        size: 36,
                        color: theme.colorScheme.onPrimaryContainer),
                  ),
                  const SizedBox(height: 16),
                  Text(
                    'Orbix Engine POS',
                    style: theme.textTheme.headlineSmall
                        ?.copyWith(fontWeight: FontWeight.w600),
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 6),
                  Text(
                    'Sign in to open a till',
                    style: theme.textTheme.bodyMedium?.copyWith(
                        color: theme.colorScheme.onSurfaceVariant),
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 28),
                  TextField(
                    controller: _username,
                    autofocus: true,
                    enabled: !_busy,
                    decoration: const InputDecoration(
                      labelText: 'Username',
                      prefixIcon: Icon(Icons.person_outline),
                      border: OutlineInputBorder(),
                    ),
                    onSubmitted: (_) => _signIn(),
                  ),
                  const SizedBox(height: 14),
                  TextField(
                    controller: _password,
                    obscureText: true,
                    enabled: !_busy,
                    decoration: const InputDecoration(
                      labelText: 'Password',
                      prefixIcon: Icon(Icons.lock_outline),
                      border: OutlineInputBorder(),
                    ),
                    onSubmitted: (_) => _signIn(),
                  ),
                  if (_errorMessage != null) ...[
                    const SizedBox(height: 10),
                    Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 12, vertical: 8),
                      decoration: BoxDecoration(
                        color: theme.colorScheme.errorContainer,
                        borderRadius: BorderRadius.circular(6),
                      ),
                      child: Text(
                        _errorMessage!,
                        style: theme.textTheme.bodySmall?.copyWith(
                            color: theme.colorScheme.onErrorContainer),
                      ),
                    ),
                  ],
                  const SizedBox(height: 12),
                  Align(
                    alignment: Alignment.centerRight,
                    child: TextButton.icon(
                      onPressed: _busy ? null : _biometric,
                      icon: const Icon(Icons.fingerprint),
                      label: const Text('Use biometric'),
                    ),
                  ),
                  const SizedBox(height: 8),
                  FilledButton(
                    onPressed: _busy ? null : _signIn,
                    style: FilledButton.styleFrom(
                      padding: const EdgeInsets.symmetric(vertical: 14),
                    ),
                    child: _busy
                        ? const SizedBox(
                            width: 18,
                            height: 18,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Text('Sign in'),
                  ),
                  const SizedBox(height: 16),
                  Text(
                    'Orbix Engine · v0.1 · Branch HQ',
                    style: theme.textTheme.labelSmall?.copyWith(
                        color: theme.colorScheme.onSurfaceVariant),
                    textAlign: TextAlign.center,
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
