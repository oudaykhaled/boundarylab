import 'dart:async';
import 'package:boundarylab/boundarylab.dart';
import 'package:fieldpack_flutter/main.dart';
import 'package:fieldpack_flutter/pack_controller.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

class FakeGateway implements BoundaryGateway {
  final lossEvents = StreamController<void>.broadcast(sync: true);
  final progressEvents = StreamController<Map<Object?, Object?>>.broadcast(
    sync: true,
  );
  Completer<List<DigestEntry>>? pending;
  bool writeFails = false;
  String writeError = 'EXPORT_FAILED';
  bool disposed = false;
  int prepares = 0;
  int connects = 0;
  int generatedCalls = 0;
  int selections = 0;
  Completer<void>? connectGate;
  Completer<void>? clearGate;
  static const selected = Selection(handle: 'opaque', alias: 'abc.txt');
  static const digest = DigestEntry(
    alias: 'abc.txt',
    size: 3,
    sha256: 'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad',
  );
  @override
  Stream<void> get losses => lossEvents.stream;
  @override
  Stream<Map<Object?, Object?>> get progress => progressEvents.stream;
  @override
  Future<void> connect() async {
    connects++;
    await connectGate?.future;
  }

  @override
  Future<List<Selection>> select() async {
    selections++;
    return [selected];
  }

  @override
  Future<List<DigestEntry>> prepare(List<Selection> selections) async {
    prepares++;
    return pending == null ? [digest] : pending!.future;
  }

  @override
  Future<DigestEntry> generated({int seed = 0, int bytes = 3}) async {
    generatedCalls++;
    return digest;
  }

  @override
  Future<bool> export() async {
    if (writeFails) throw PlatformException(code: writeError);
    return true;
  }

  @override
  Future<void> clear() async {
    await clearGate?.future;
  }

  @override
  Future<void> cancel() async {}
  @override
  Future<void> dispose() async {
    disposed = true;
  }
}

void main() {
  test('all-success attempt enables export; new attempt rehashes', () async {
    final gateway = FakeGateway();
    final controller = PackController(gateway);
    await controller.select();
    expect(controller.canExport, false);
    await controller.prepare();
    expect(controller.canExport, true);
    await controller.prepare();
    expect(gateway.prepares, 2);
    await controller.export();
    expect(controller.phase, PackPhase.exported);
    controller.dispose();
  });
  test('late prepare success after cancel cannot enable export', () async {
    final gateway = FakeGateway()..pending = Completer<List<DigestEntry>>();
    final controller = PackController(gateway);
    await controller.select();
    final preparing = controller.prepare();
    await Future<void>.delayed(Duration.zero);
    await controller.cancel();
    gateway.pending!.complete([FakeGateway.digest]);
    await preparing;
    expect(controller.canExport, false);
    expect(controller.phase, PackPhase.cancelled);
    expect(controller.entries, isEmpty);
    controller.dispose();
  });
  test('broker loss clears successful pack', () async {
    final gateway = FakeGateway();
    final controller = PackController(gateway);
    await controller.select();
    await controller.prepare();
    gateway.lossEvents.add(null);
    expect(controller.canExport, false);
    expect(controller.phase, PackPhase.lost);
    controller.dispose();
  });
  test('write failure retains complete pack for explicit retry', () async {
    final gateway = FakeGateway()..writeFails = true;
    final controller = PackController(gateway);
    await controller.select();
    await controller.prepare();
    await controller.export();
    expect(controller.canExport, true);
    expect(controller.message, contains('Could not save'));
    gateway.writeFails = false;
    await controller.export();
    expect(controller.phase, PackPhase.exported);
    controller.dispose();
  });
  test('export lifecycle cancellation never restores ready pack', () async {
    final gateway = FakeGateway()
      ..writeFails = true
      ..writeError = 'CANCELLED';
    final controller = PackController(gateway);
    await controller.select();
    await controller.prepare();
    await controller.export();
    expect(controller.canExport, false);
    expect(controller.entries, isEmpty);
    controller.dispose();
  });
  test('dispose discards delayed completion and closes gateway', () async {
    final gateway = FakeGateway()..pending = Completer<List<DigestEntry>>();
    final controller = PackController(gateway);
    await controller.select();
    final preparing = controller.prepare();
    await Future<void>.delayed(Duration.zero);
    controller.dispose();
    gateway.pending!.complete([FakeGateway.digest]);
    await preparing;
    expect(controller.entries, isEmpty);
    expect(gateway.disposed, true);
  });
  test('cancel while connect pending never admits file work', () async {
    final gateway = FakeGateway();
    final controller = PackController(gateway);
    await controller.select();
    gateway.connectGate = Completer<void>();
    final pending = controller.prepare();
    await controller.cancel();
    gateway.connectGate!.complete();
    await pending;
    expect(gateway.prepares, 0);
    controller.dispose();
  });
  test('dispose while connect pending never admits generated work', () async {
    final gateway = FakeGateway()..connectGate = Completer<void>();
    final controller = PackController(gateway);
    final pending = controller.generated();
    controller.dispose();
    gateway.connectGate!.complete();
    await pending;
    expect(gateway.generatedCalls, 0);
  });
  test('dispose while clear pending never opens selection', () async {
    final gateway = FakeGateway()..clearGate = Completer<void>();
    final controller = PackController(gateway);
    final pending = controller.select();
    controller.dispose();
    gateway.clearGate!.complete();
    await pending;
    expect(gateway.selections, 0);
  });
  testWidgets('actions gated and transfer guidance visible', (tester) async {
    final gateway = FakeGateway();
    await tester.pumpWidget(FieldPackApp(gateway: gateway));
    expect(gateway.connects, 0);
    expect(gateway.generatedCalls, 0);
    expect(gateway.prepares, 0);
    expect(
      tester
          .widget<FilledButton>(find.byKey(const Key('export_manifest')))
          .onPressed,
      isNull,
    );
    await tester.tap(find.byKey(const Key('select_attachments')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('prepare_pack')));
    await tester.pumpAndSettle();
    expect(
      tester
          .widget<FilledButton>(find.byKey(const Key('export_manifest')))
          .onPressed,
      isNotNull,
    );
    await tester.drag(find.byType(ListView).first, const Offset(0, -400));
    await tester.pumpAndSettle();
    expect(find.textContaining('Transfer files separately'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
