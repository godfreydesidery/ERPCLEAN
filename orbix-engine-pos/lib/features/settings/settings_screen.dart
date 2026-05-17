import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
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
                      width: 10, height: 10,
                      decoration: const BoxDecoration(color: Colors.green, shape: BoxShape.circle),
                    ),
                  ),
                  _SettingsTile(
                    icon: Icons.qr_code_scanner,
                    title: 'Barcode scanner',
                    subtitle: 'Honeywell Voyager 1450g (keyboard wedge) — connected',
                    trailing: Container(
                      width: 10, height: 10,
                      decoration: const BoxDecoration(color: Colors.green, shape: BoxShape.circle),
                    ),
                  ),
                  _SettingsTile(
                    icon: Icons.inbox_outlined,
                    title: 'Cash drawer',
                    subtitle: 'Opens via printer kick — tested',
                    trailing: TextButton(onPressed: () {}, child: const Text('Test')),
                  ),
                  _SettingsTile(
                    icon: Icons.receipt_long,
                    title: 'Fiscal device',
                    subtitle: 'Not configured for this branch',
                    trailing: const Chip(label: Text('Off'), visualDensity: VisualDensity.compact),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              _SectionCard(
                title: 'Connection',
                children: [
                  _SettingsTile(
                    icon: Icons.cloud_outlined,
                    title: 'Backend API',
                    subtitle: 'https://qa.orbix.example.com/api/v1',
                  ),
                  _SettingsTile(
                    icon: Icons.sync,
                    title: 'Sync status',
                    subtitle: '12 ops in outbox · last push 2 min ago',
                    trailing: TextButton.icon(
                      onPressed: () {},
                      icon: const Icon(Icons.refresh, size: 16),
                      label: const Text('Push now'),
                    ),
                  ),
                  _SettingsTile(
                    icon: Icons.wifi,
                    title: 'Network',
                    subtitle: 'Online · 47 ms to backend',
                    trailing: Container(
                      width: 10, height: 10,
                      decoration: const BoxDecoration(color: Colors.green, shape: BoxShape.circle),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              _SectionCard(
                title: 'About',
                children: const [
                  _SettingsTile(icon: Icons.info_outline,    title: 'App version',     subtitle: 'Orbix Engine POS · 0.1.0'),
                  _SettingsTile(icon: Icons.storage,         title: 'Local database',  subtitle: 'SQLite · 4.2 MB'),
                  _SettingsTile(icon: Icons.business,        title: 'Branch',          subtitle: 'Branch HQ · TILL-1 · Front counter'),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

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
  const _SettingsTile({required this.icon, required this.title, required this.subtitle, this.trailing});

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
