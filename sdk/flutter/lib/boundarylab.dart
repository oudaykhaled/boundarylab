import 'dart:async';

import 'package:flutter/services.dart';

/// A public alias and an engine-scoped native token; never a URI or descriptor.
final class Selection {
  const Selection({
    required this.handle,
    required this.alias,
    this.displayName,
  });
  final String handle;
  final String? displayName;
  final String alias;

  factory Selection.fromMap(Map<Object?, Object?> map) => Selection(
    handle: map['handle']! as String,
    displayName: map['displayName'] as String?,
    alias: map['alias']! as String,
  );
}

final class DigestEntry {
  const DigestEntry({
    required this.alias,
    required this.size,
    required this.sha256,
  });
  final String alias;
  final int size;
  final String sha256;

  factory DigestEntry.fromMap(Map<Object?, Object?> map) => DigestEntry(
    alias: map['name']! as String,
    size: map['size']! as int,
    sha256: map['sha256']! as String,
  );
}

/// Injectable boundary for widget tests. Only the Android implementation
/// creates exportable manifests; tests cannot establish Binder evidence.
abstract interface class BoundaryGateway {
  Stream<void> get losses;
  Stream<Map<Object?, Object?>> get progress;
  Future<void> connect();
  Future<List<Selection>> select();
  Future<List<DigestEntry>> prepare(List<Selection> selections);
  Future<DigestEntry> generated({int seed = 0, int bytes = 3});
  Future<void> cancel();
  Future<bool> export();
  Future<void> clear();
  Future<void> dispose();
}

/// One gateway per Flutter engine. Native code owns the session and polling.
final class BoundaryLab implements BoundaryGateway {
  BoundaryLab({MethodChannel? channel})
    : _channel = channel ?? const MethodChannel('dev.boundarylab/client') {
    _channel.setMethodCallHandler(_onNativeCall);
  }
  final MethodChannel _channel;
  final _losses = StreamController<void>.broadcast();
  final _progress = StreamController<Map<Object?, Object?>>.broadcast();
  bool _disposed = false;

  Future<void> _onNativeCall(MethodCall call) async {
    if (_disposed) return;
    if (call.method == 'lost') _losses.add(null);
    if (call.method == 'progress') {
      _progress.add(call.arguments as Map<Object?, Object?>);
    }
  }

  @override
  Stream<void> get losses => _losses.stream;
  @override
  Stream<Map<Object?, Object?>> get progress => _progress.stream;
  @override
  Future<void> connect() => _channel.invokeMethod<void>('connect');
  @override
  Future<List<Selection>> select() async {
    final values = await _channel.invokeListMethod<Object?>('select') ?? [];
    return values
        .map((v) => Selection.fromMap(v! as Map<Object?, Object?>))
        .toList();
  }

  @override
  Future<List<DigestEntry>> prepare(List<Selection> selections) async {
    final values = await _channel.invokeListMethod<Object?>('prepare', {
      'handles': selections.map((s) => s.handle).toList(),
    });
    return (values ?? [])
        .map((v) => DigestEntry.fromMap(v! as Map<Object?, Object?>))
        .toList();
  }

  @override
  Future<DigestEntry> generated({int seed = 0, int bytes = 3}) async {
    final value = await _channel.invokeMapMethod<Object?, Object?>(
      'generated',
      {'seed': seed, 'bytes': bytes},
    );
    return DigestEntry.fromMap(value!);
  }

  @override
  Future<void> cancel() => _channel.invokeMethod<void>('cancel');
  @override
  Future<bool> export() async =>
      await _channel.invokeMethod<bool>('export') ?? false;
  @override
  Future<void> clear() => _channel.invokeMethod<void>('clear');
  @override
  Future<void> dispose() async {
    if (_disposed) return;
    _disposed = true;
    _channel.setMethodCallHandler(null);
    await _losses.close();
    await _progress.close();
    await _channel.invokeMethod<void>('dispose');
  }
}
