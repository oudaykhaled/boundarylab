import 'package:boundarylab/boundarylab.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

/// Instrumentation entrypoint: exercises the production plugin and Binder SDK.
/// It does not select files, forge job results, or bypass export validation.
@pragma('vm:entry-point')
void boundaryEngineProbe() {
  WidgetsFlutterBinding.ensureInitialized();
  final gateway = BoundaryLab();
  const probe = MethodChannel('dev.boundarylab/engine_probe');
  probe.setMethodCallHandler((call) async {
    if (call.method != 'run' && call.method != 'rawGenerated') {
      throw MissingPluginException();
    }
    await gateway.connect();
    if (call.method == 'rawGenerated') {
      return const MethodChannel(
        'dev.boundarylab/client',
      ).invokeMethod<Object?>('generated', call.arguments);
    }
    final bytes = (call.arguments as Map<Object?, Object?>)['bytes']! as int;
    final result = await gateway.generated(bytes: bytes);
    return result.sha256;
  });
  probe.invokeMethod<void>('ready');
}
