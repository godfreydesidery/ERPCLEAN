import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

class SupervisorPinScreen extends StatefulWidget {
  const SupervisorPinScreen({super.key});

  @override
  State<SupervisorPinScreen> createState() => _SupervisorPinScreenState();
}

class _SupervisorPinScreenState extends State<SupervisorPinScreen> {
  String _pin = '';
  String? _error;

  void _press(String d) {
    if (_pin.length >= 6) return;
    setState(() {
      _pin += d;
      _error = null;
    });
    if (_pin.length == 4) _verify();
  }

  void _backspace() {
    if (_pin.isEmpty) return;
    setState(() {
      _pin = _pin.substring(0, _pin.length - 1);
      _error = null;
    });
  }

  void _verify() {
    // Mock: PIN 1234 authorises, anything else fails.
    if (_pin == '1234') {
      showDialog<void>(
        context: context,
        builder: (_) => AlertDialog(
          icon: const Icon(Icons.verified_user, size: 48, color: Colors.green),
          title: const Text('Authorisation granted'),
          content: const Text('You may now perform a void, discount, or refund on this sale.'),
          actions: [
            TextButton(
              onPressed: () {
                Navigator.pop(context);
                context.pop();
              },
              child: const Text('Back to cart'),
            ),
          ],
        ),
      );
    } else {
      setState(() {
        _error = 'Wrong PIN — try again or call a manager.';
        _pin = '';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      backgroundColor: theme.colorScheme.surfaceContainerLow,
      appBar: AppBar(
        title: const Text('Supervisor authorisation'),
        leading: IconButton(icon: const Icon(Icons.arrow_back), onPressed: () => context.pop()),
      ),
      body: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 380),
          child: Card(
            elevation: 0,
            margin: const EdgeInsets.all(24),
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(Icons.shield_outlined, size: 48, color: theme.colorScheme.primary),
                  const SizedBox(height: 12),
                  Text('Enter supervisor PIN', style: theme.textTheme.titleLarge),
                  const SizedBox(height: 4),
                  Text('Hint: 1234 (mock)', style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
                  const SizedBox(height: 20),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: List.generate(4, (i) {
                      final filled = i < _pin.length;
                      return Container(
                        width: 18, height: 18,
                        margin: const EdgeInsets.symmetric(horizontal: 6),
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          color: filled ? theme.colorScheme.primary : Colors.transparent,
                          border: Border.all(color: theme.colorScheme.outline),
                        ),
                      );
                    }),
                  ),
                  if (_error != null) ...[
                    const SizedBox(height: 12),
                    Text(_error!, style: TextStyle(color: theme.colorScheme.error)),
                  ],
                  const SizedBox(height: 20),
                  _Keypad(onDigit: _press, onBackspace: _backspace),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _Keypad extends StatelessWidget {
  final void Function(String) onDigit;
  final VoidCallback onBackspace;
  const _Keypad({required this.onDigit, required this.onBackspace});

  @override
  Widget build(BuildContext context) {
    return Wrap(
      spacing: 8, runSpacing: 8,
      alignment: WrapAlignment.center,
      children: [
        for (final d in ['1','2','3','4','5','6','7','8','9'])
          _key(context, d, () => onDigit(d)),
        _key(context, '', null),
        _key(context, '0', () => onDigit('0')),
        _key(context, '⌫', onBackspace),
      ],
    );
  }

  Widget _key(BuildContext context, String label, VoidCallback? onTap) {
    return SizedBox(
      width: 80, height: 64,
      child: FilledButton.tonal(
        onPressed: onTap,
        style: FilledButton.styleFrom(
          textStyle: Theme.of(context).textTheme.headlineSmall,
        ),
        child: Text(label),
      ),
    );
  }
}
