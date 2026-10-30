import 'package:boundarylab/boundarylab.dart';
import 'package:flutter/material.dart';

import 'engine_probe.dart';
import 'pack_controller.dart';
import 'pack_screen.dart';
import 'theme.dart';

void main() => runApp(const FieldPackApp());

class FieldPackApp extends StatefulWidget {
  const FieldPackApp({super.key, this.gateway});
  final BoundaryGateway? gateway;
  @override
  State<FieldPackApp> createState() => _FieldPackAppState();
}

class _FieldPackAppState extends State<FieldPackApp> {
  late final controller = PackController(widget.gateway ?? BoundaryLab());
  @override
  void dispose() {
    controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => MaterialApp(
    title: 'FieldPack',
    debugShowCheckedModeBanner: false,
    theme: packTheme(Brightness.light),
    darkTheme: packTheme(Brightness.dark),
    home: PackScreen(controller: controller),
  );
}

// Retain a separate real-boundary instrumentation entrypoint in debug builds.
// ignore: unused_element
@pragma('vm:entry-point')
void engineProbe() => boundaryEngineProbe();
