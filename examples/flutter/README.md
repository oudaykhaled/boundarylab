# FieldPack Flutter consumer

This standalone public sample uses the authored `boundarylab` plugin, Kotlin SDK,
and separately installed Android broker. The application ID is
`dev.boundarylab.flutterclient`; it requires a matching-signed broker APK.

Run with Flutter 3.38.9 / Dart 3.10.8, JDK 17 and Android SDK 36:

```sh
flutter pub get
flutter test
dart analyze
flutter build apk --debug
```

From `android/`, `./gradlew :app:assembleDebugAndroidTest` builds the real
`EngineLifecycleTest` harness. Install the app and test APK alongside the broker
and run `dev.boundarylab.flutterclient.test/androidx.test.runner.AndroidJUnitRunner`.
Tests create two real Flutter engines and use the actual plugin/SDK/Binder path;
mock widget tests do not establish this runtime evidence.

Select stable local files, prepare every attachment, then explicitly export the
complete manifest. The displayed source-name → alias mapping is local only.
Transfer the original attachments separately under the public aliases. Checksums
compare bytes and do not authenticate the sender. The generated workload lives in
a separate diagnostics panel and never enables file-pack export.

## Architecture decision

Context: one screen and one volatile asynchronous workflow in an independent
public sample. Decision: use Flutter SDK `ChangeNotifier` with an injectable typed
`BoundaryGateway`; keep URI access, descriptor ownership, sequential SDK polling
and shared manifest encoding native. Reuse Material 3 components and the local
`design/DESIGN.md` semantic palette. Private OMDS dependencies do not apply to this
standalone repository.

Consequences: no third-party state-management/runtime dependency or duplicated
hash/scheduler policy. Controller generation checks precede every post-await
side effect and reject late completion. More workflows would justify Riverpod;
BLoC would add event/state boilerplate without a current shared event requirement.
Default launch is lazy: it neither binds the broker nor admits work. The widget
test asserts no connect, prepare, or generated call before a user action. Measure
explicit connect/prepare and submit-to-visible-result latency separately; this
sample does not imply a startup IPC delta.

History is memory-only and cannot export previous attempts. The sample is English;
layout uses logical positioning, scalable text, semantics and light/dark themes.

## Evidence boundaries

Widget and channel tests are local unit evidence. APK compilation is separate
from installed execution. QA owns Android installations and records broker/process,
provider, cancellation, lifecycle and handoff evidence at repository level. The
installed Flutter tool currently lacks the non-AOT analysis snapshot requested by
`flutter analyze`; `dart analyze` uses the available AOT analyzer. Do not replace
or modify the global SDK to conceal that tool limitation.
