import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

class LoginScreen extends StatelessWidget {
  const LoginScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 360),
          child: Card(
            margin: const EdgeInsets.all(24),
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text('Orbix Engine POS', style: Theme.of(context).textTheme.headlineSmall),
                  const SizedBox(height: 24),
                  const TextField(decoration: InputDecoration(labelText: 'Username')),
                  const SizedBox(height: 12),
                  const TextField(obscureText: true, decoration: InputDecoration(labelText: 'Password')),
                  const SizedBox(height: 24),
                  FilledButton(
                    onPressed: () => context.go('/till/open'),
                    child: const Text('Sign in'),
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
