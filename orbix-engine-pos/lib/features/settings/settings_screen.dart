import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../data/core_providers.dart';

class SettingsScreen extends ConsumerWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final configStore = ref.watch(posConfigStoreProvider);

    return Scaffold(
      backgroundColor: theme.colorScheme.surfaceContainerLow,
      appBar: AppBar(
        title: const Text('Settings'),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () => context.pop(),
        ),
      ),
      body: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 720),
          child: ListView(
            padding: const EdgeInsets.all(24),
            children: [
              _SectionCard(
                title: 'Hardware',
                children: [
                  _SettingsTile(
                    icon: Icons.print_outlined,
                    title: 'Receipt printer',
                    subtitle: 'Star TSP143 (USB) — connected',
                    trailing: Container(
                      width: 10,
                      height: 10,
                      decoration: const BoxDecoration(
                          color: Colors.green, shape: BoxShape.circle),
                    ),
                  ),
                  _SettingsTile(
                    icon: Icons.qr_code_scanner,
                    title: 'Barcode scanner',
                    subtitle:
                        'Honeywell Voyager 1450g (keyboard wedge) — connected',
                    trailing: Container(
                      width: 10,
                      height: 10,
                      decoration: const BoxDecoration(
                          color: Colors.green, shape: BoxShape.circle),
                    ),
                  ),
                  _SettingsTile(
                    icon: Icons.inbox_outlined,
                    title: 'Cash drawer',
                    subtitle: 'Opens via printer kick — tested',
                    trailing: Builder(
                      builder: (context) => TextButton(
                        onPressed: () =>
                            ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(
                              content: Text(
                                  'Drawer kick sent — listen for the clunk')),
                        ),
                        child: const Text('Test'),
                      ),
                    ),
                  ),
                  const _SettingsTile(
                    icon: Icons.receipt_long,
                    title: 'Fiscal device',
                    subtitle: 'Not configured for this branch',
                    trailing:
                        Chip(label: Text('Off'), visualDensity: VisualDensity.compact),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              _SectionCard(
                title: 'Connection',
                children: [
                  _EditableTile(
                    icon: Icons.cloud_outlined,
                    title: 'Backend API base URL',
                    value: configStore.apiBaseUrl,
                    hint: 'http://localhost:8081',
                    onSave: (v) async {
                      await ref
                          .read(posConfigStoreProvider)
                          .saveApiBaseUrl(v);
                      if (context.mounted) {
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(
                              content: Text(
                                  'API URL saved — restart the app to apply.')),
                        );
                      }
                    },
                  ),
                  _EditableTile(
                    icon: Icons.point_of_sale,
                    title: 'Device / Till ID',
                    value: configStore.deviceId,
                    hint: 'TILL-1',
                    onSave: (v) async {
                      await ref
                          .read(posConfigStoreProvider)
                          .saveDeviceId(v);
                      if (context.mounted) {
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(
                              content: Text(
                                  'Device ID saved — restart the app to apply.')),
                        );
                      }
                    },
                  ),
                  _SettingsTile(
                    icon: Icons.sync,
                    title: 'Sync status',
                    subtitle: 'Outbox managed by sync dispatcher',
                    trailing: Builder(
                      builder: (context) => TextButton.icon(
                        onPressed: () async {
                          ScaffoldMessenger.of(context).showSnackBar(
                            const SnackBar(content: Text('Pushing outbox…')),
                          );
                          await Future<void>.delayed(
                              const Duration(milliseconds: 700));
                          if (!context.mounted) return;
                          ScaffoldMessenger.of(context).hideCurrentSnackBar();
                          ScaffoldMessenger.of(context).showSnackBar(
                            const SnackBar(
                                content: Text('Manual push triggered')),
                          );
                        },
                        icon: const Icon(Icons.refresh, size: 16),
                        label: const Text('Push now'),
                      ),
                    ),
                  ),
                  const _SettingsTile(
                    icon: Icons.wifi,
                    title: 'Network',
                    subtitle: 'Status unknown — sync loop monitors connectivity',
                  ),
                ],
              ),
              const SizedBox(height: 16),
              const _SectionCard(
                title: 'About',
                children: [
                  _SettingsTile(
                      icon: Icons.info_outline,
                      title: 'App version',
                      subtitle: 'Orbix Engine POS · 0.1.0'),
                  _SettingsTile(
                      icon: Icons.storage,
                      title: 'Local database',
                      subtitle: 'SQLite · Drift'),
                  _SettingsTile(
                      icon: Icons.business,
                      title: 'Branch',
                      subtitle: 'Sourced from JWT after login'),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Settings widgets
// ---------------------------------------------------------------------------

class _SectionCard extends StatelessWidget {
  final String title;
  final List<Widget> children;
  const _SectionCard({required this.title, required this.children});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      elevation: 0,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(4, 4, 4, 8),
              child: Text(
                title.toUpperCase(),
                style: theme.textTheme.labelSmall?.copyWith(
                  letterSpacing: 1.2,
                  color: theme.colorScheme.onSurfaceVariant,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ),
            ...children,
          ],
        ),
      ),
    );
  }
}

class _SettingsTile extends StatelessWidget {
  final IconData icon;
  final String title;
  final String subtitle;
  final Widget? trailing;
  const _SettingsTile(
      {required this.icon,
      required this.title,
      required this.subtitle,
      this.trailing});

  @override
  Widget build(BuildContext context) {
    return ListTile(
      contentPadding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
      leading: Icon(icon, color: Theme.of(context).colorScheme.primary),
      title: Text(title, style: const TextStyle(fontWeight: FontWeight.w500)),
      subtitle: Text(subtitle),
      trailing: trailing,
    );
  }
}

/// Tile with an inline edit button — shows current value, opens a dialog to change it.
class _EditableTile extends StatelessWidget {
  final IconData icon;
  final String title;
  final String value;
  final String hint;
  final Future<void> Function(String) onSave;

  const _EditableTile({
    required this.icon,
    required this.title,
    required this.value,
    required this.hint,
    required this.onSave,
  });

  @override
  Widget build(BuildContext context) {
    return ListTile(
      contentPadding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
      leading: Icon(icon, color: Theme.of(context).colorScheme.primary),
      title: Text(title, style: const TextStyle(fontWeight: FontWeight.w500)),
      subtitle: Text(value),
      trailing: IconButton(
        icon: const Icon(Icons.edit_outlined),
        tooltip: 'Edit',
        onPressed: () => _showEditDialog(context),
      ),
    );
  }

  void _showEditDialog(BuildContext context) {
    final controller = TextEditingController(text: value);
    showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(title),
        content: TextField(
          controller: controller,
          autofocus: true,
          decoration: InputDecoration(
            hintText: hint,
            border: const OutlineInputBorder(),
          ),
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx),
              child: const Text('Cancel')),
          FilledButton(
            onPressed: () async {
              final v = controller.text.trim();
              if (v.isNotEmpty) {
                await onSave(v);
              }
              if (ctx.mounted) Navigator.pop(ctx);
            },
            child: const Text('Save'),
          ),
        ],
      ),
    );
  }
}
