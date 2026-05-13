import 'package:flutter/material.dart';

ThemeData orbixLightTheme() => ThemeData(
      useMaterial3: true,
      colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF1A4FB5)),
      visualDensity: VisualDensity.compact,
    );

ThemeData orbixDarkTheme() => ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      colorScheme: ColorScheme.fromSeed(
        seedColor: const Color(0xFF1A4FB5),
        brightness: Brightness.dark,
      ),
      visualDensity: VisualDensity.compact,
    );
