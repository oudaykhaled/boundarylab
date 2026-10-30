import 'dart:async';

import 'package:boundarylab/boundarylab.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

enum PackPhase {
  idle,
  connecting,
  selecting,
  preparing,
  ready,
  exporting,
  exported,
  cancelling,
  cancelled,
  failed,
  lost,
}

/// Presentation state only. Native owns selection authority, jobs and export.
final class PackController extends ChangeNotifier {
  PackController(this.gateway) {
    _loss = gateway.losses.listen((_) => _lost());
    _progress = gateway.progress.listen(_updateProgress);
  }
  final BoundaryGateway gateway;
  late final StreamSubscription<void> _loss;
  late final StreamSubscription<Map<Object?, Object?>> _progress;
  String progressLabel = "";
  List<Selection> selections = const [];
  List<DigestEntry> entries = const [];
  PackPhase phase = PackPhase.idle;
  String message = 'Choose attachments to start a new pack.';
  String? generatedDigest;
  final List<String> history = [];
  String? diagnosticCode;
  int _generation = 0;
  bool _disposed = false;
  bool get busy => const {
    PackPhase.connecting,
    PackPhase.selecting,
    PackPhase.preparing,
    PackPhase.exporting,
    PackPhase.cancelling,
  }.contains(phase);
  bool get canExport =>
      phase == PackPhase.ready &&
      entries.length == selections.length &&
      entries.isNotEmpty;

  Future<void> select() async {
    final generation = _begin(
      PackPhase.selecting,
      'Choose up to 32 local attachments.',
    );
    try {
      await gateway.clear();
      if (!_current(generation)) return;
      final value = await gateway.select();
      if (!_current(generation)) return;
      selections = value;
      _set(
        PackPhase.idle,
        value.isEmpty
            ? 'Selection cancelled.'
            : '${value.length} attachments selected. Ready to prepare.',
      );
    } catch (error) {
      _failure(generation, error);
    }
  }

  Future<void> prepare() async {
    if (selections.isEmpty || busy) return;
    final generation = _begin(PackPhase.preparing, 'Checking attachments…');
    try {
      await gateway.connect();
      if (!_current(generation)) return;
      final value = await gateway.prepare(selections);
      if (!_current(generation)) return;
      entries = value;
      _set(
        PackPhase.ready,
        'Pack ready. All attachments succeeded in this attempt.',
      );
    } catch (error) {
      _failure(generation, error);
    }
  }

  Future<void> generated() async {
    if (busy) return;
    final generation = _begin(
      PackPhase.preparing,
      'Running generated workload through the broker…',
    );
    try {
      await gateway.connect();
      if (!_current(generation)) return;
      final result = await gateway.generated();
      if (!_current(generation)) return;
      generatedDigest = result.sha256;
      _set(
        PackPhase.idle,
        'Generated workload succeeded. This is not a file pack.',
      );
    } catch (error) {
      _failure(generation, error);
    }
  }

  Future<void> export() async {
    if (!canExport) return;
    final generation = _generation;
    _set(PackPhase.exporting, 'Choose where to save the complete manifest.');
    try {
      final saved = await gateway.export();
      if (!_current(generation)) return;
      _set(
        saved ? PackPhase.exported : PackPhase.ready,
        saved
            ? 'Manifest exported. Transfer attachments separately using the aliases below.'
            : 'Export cancelled. The complete pack is still ready.',
      );
    } catch (error) {
      if (error is! PlatformException || error.code != 'EXPORT_FAILED') {
        _failure(generation, error);
        return;
      }
      if (_current(generation)) {
        _set(
          PackPhase.ready,
          'Could not save the manifest. Choose another destination and retry export.',
        );
      }
    }
  }

  Future<void> cancel() async {
    final generation = _begin(
      PackPhase.cancelling,
      'Waiting for the current read to finish. This can take time.',
    );
    try {
      await gateway.cancel();
      if (_current(generation)) {
        _set(
          PackPhase.cancelled,
          'Attempt cancelled. Prepare again to recheck every attachment.',
        );
      }
    } catch (error) {
      _failure(generation, error);
    }
  }

  int _begin(PackPhase value, String text) {
    _generation++;
    entries = const [];
    progressLabel = '';
    generatedDigest = null;
    _set(value, text);
    return _generation;
  }

  bool _current(int generation) => !_disposed && generation == _generation;
  void _set(PackPhase value, String text) {
    if (_disposed) return;
    phase = value;
    if (const {
      PackPhase.exported,
      PackPhase.cancelled,
      PackPhase.failed,
      PackPhase.lost,
    }.contains(value)) {
      history.insert(0, text);
      if (history.length > 8) history.removeLast();
    }
    message = text;
    notifyListeners();
  }

  void _failure(int generation, Object error) {
    if (!_current(generation)) return;
    entries = const [];
    final code = error is PlatformException ? error.code : 'UNAVAILABLE';
    diagnosticCode = code;
    _set(
      code == 'LOST_OR_UNKNOWN' ? PackPhase.lost : PackPhase.failed,
      _recovery(code),
    );
  }

  String _recovery(String code) => switch (code) {
    'INVALID' =>
      'This selection is too large or unsupported. Choose up to 32 files and 128 MiB total.',
    'UNSUPPORTED' =>
      'This file source is unsupported. Choose stable local files from another provider.',
    'IO_ERROR' =>
      'A selected file could not be read. Check access and select it again.',
    'SOURCE_CHANGED' =>
      'An attachment changed while being checked. Wait for editing to finish and prepare again.',
    'DEADLINE_EXCEEDED' =>
      'The check timed out. Try fewer or smaller files in a new attempt.',
    'TIMEOUT_UNKNOWN' =>
      'The request timed out and its outcome is unknown. Start a new attempt to retry; nothing is retried automatically.',
    'BUSY' => 'The service is still busy. Wait for cleanup, then try again.',
    'DENIED' =>
      'This app is not authorized to use the installed service. Install the matching signed apps.',
    'CANCELLED' =>
      'The attempt was cancelled. Prepare again to recheck every attachment.',
    'LOST_OR_UNKNOWN' =>
      'Connection lost. Start a new attempt to recheck every attachment.',
    _ =>
      'The check could not finish. Check the service is available and start a new attempt.',
  };

  void _updateProgress(Map<Object?, Object?> value) {
    if (_disposed || phase != PackPhase.preparing) return;
    final alias = value['alias'];
    if (alias != null) {
      progressLabel = "${value['index']}/${value['total']} · $alias";
    }
    if (value['bytes'] != null) {
      message = 'Checking attachments · ${value['bytes']} bytes checked';
    }
    notifyListeners();
  }

  void _lost() => _begin(
    PackPhase.lost,
    'Connection lost. Results are unknown. Start a new attempt to recheck every attachment.',
  );
  @override
  void dispose() {
    _disposed = true;
    _generation++;
    unawaited(_loss.cancel());
    unawaited(_progress.cancel());
    unawaited(gateway.dispose());
    super.dispose();
  }
}
