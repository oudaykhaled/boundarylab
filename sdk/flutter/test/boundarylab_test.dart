import 'package:boundarylab/boundarylab.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('dev.boundarylab/test');
  final calls = <MethodCall>[];
  setUp(() {
    calls.clear();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
          calls.add(call);
          return switch (call.method) {
            'select' => [
              {'handle': 'opaque-token', 'alias': 'abc.txt'},
            ],
            'prepare' => [
              {'name': 'abc.txt', 'size': 3, 'sha256': 'digest'},
            ],
            'export' => true,
            _ => null,
          };
        });
  });
  test('Dart transmits opaque selection tokens only', () async {
    final client = BoundaryLab(channel: channel);
    final selected = await client.select();
    final entries = await client.prepare(selected);
    expect(entries.single.alias, 'abc.txt');
    expect(calls.last.arguments, {
      'handles': ['opaque-token'],
    });
    expect(await client.export(), true);
    await client.dispose();
  });
  test('disposal calls native cleanup once', () async {
    final client = BoundaryLab(channel: channel);
    await client.dispose();
    await client.dispose();
    expect(calls.where((call) => call.method == 'dispose').length, 1);
  });
}
