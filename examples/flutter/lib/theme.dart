import 'package:flutter/material.dart';

abstract final class PackTokens {
  static const navy = Color(0xFF102C3A);
  static const emerald = Color(0xFF006B52);
  static const background = Color(0xFFF3F6F7);
  static const text = Color(0xFF173340);
  static const muted = Color(0xFF4D6470);
  static const border = Color(0xFFC6D3D8);
  static const danger = Color(0xFFA52A35);
  static const white = Color(0xFFFFFFFF);
  static const small = 8.0;
  static const medium = 16.0;
  static const page = 24.0;
  static const radius = 12.0;
  static const target = 48.0;
  static const maxWidth = 720.0;
}

ThemeData packTheme(Brightness brightness) {
  final scheme = ColorScheme.fromSeed(
    seedColor: PackTokens.emerald,
    brightness: brightness,
  );
  return ThemeData(
    useMaterial3: true,
    colorScheme: scheme.copyWith(
      primary: brightness == Brightness.light
          ? PackTokens.emerald
          : scheme.primary,
    ),
    scaffoldBackgroundColor: brightness == Brightness.light
        ? PackTokens.background
        : scheme.surface,
    appBarTheme: const AppBarTheme(
      backgroundColor: PackTokens.navy,
      foregroundColor: PackTokens.white,
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        minimumSize: const Size.fromHeight(PackTokens.target),
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        minimumSize: const Size.fromHeight(PackTokens.target),
      ),
    ),
  );
}
