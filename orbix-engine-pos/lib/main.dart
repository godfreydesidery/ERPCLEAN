// Orbix Engine — Point of Sale (Flutter Desktop entry point).
// See ARCHITECTURE.md §3.1 for the offline-first design.

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app/app.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const ProviderScope(child: OrbixPosApp()));
}
