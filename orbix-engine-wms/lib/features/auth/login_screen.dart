import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

class LoginScreen extends StatelessWidget {
  const LoginScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const SizedBox(height: 40),
              Text('Orbix Engine WMS', style: Theme.of(context).textTheme.headlineSmall),
              const SizedBox(height: 32),
              const TextField(decoration: InputDecoration(labelText: 'Username')),
              const SizedBox(height: 12),
              const TextField(obscureText: true, decoration: InputDecoration(labelText: 'Password')),
              const SizedBox(height: 24),
              FilledButton(
                onPressed: () => context.go('/route'),
                child: const Text('Sign in'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
