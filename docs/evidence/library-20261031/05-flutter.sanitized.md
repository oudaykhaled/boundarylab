# Flutter reusable-package implementation — 2026-10-31

Owner: library_flutter. State: implemented and locally checked; external copied-consumer debug APK build passed. No Flutter Android runtime executed.

## Changes and reuse

Extends the existing `sdk/flutter` adapter and uses the existing `BoundaryClient`/typed `BrokerEndpoint`. No new state-management package, Dart API, wire method, hashing implementation, session migration or AOSP integration is introduced.

- `sdk/flutter/android/build.gradle`: depends on `dev.boundarylab:boundarylab-android:0.1.0-SNAPSHOT` (override `boundarylabVersion`) with transitive core supplied by publication metadata. Three optional compile-time properties (`boundarylab.brokerPackage`, `boundarylab.brokerServiceClass`, `boundarylab.brokerBindPermission`) are all-or-none; generated BuildConfig supplies the explicit endpoint.
- `sdk/flutter/android/src/main/kotlin/dev/boundarylab/flutter/BoundaryLabPlugin.kt`: constructs the typed custom endpoint once on engine attachment; otherwise uses `BrokerEndpoint.DEFAULT`. Existing engine/session/selection ownership stays intact.
- `examples/flutter/android/settings.gradle.kts`: removes source SDK/core includes and root version-catalog path. The remaining Flutter toolchain `includeBuild` is standard Flutter plugin loading, not a BoundaryLab source dependency.
- `examples/flutter/android/build.gradle.kts`: explicit local `boundarylabRepository` folder, supplied as Gradle property or `ORG_GRADLE_PROJECT_boundarylabRepository`; exclusive `dev.boundarylab` repository content prevents remote fallback for these coordinates.
- `scripts/check-flutter.py`: keeps analysis/tests and adds bounded local Maven publication before the debug APK build, with explicit repository env injection. Summary labels distinguish publication and APK compilation; `android_runtime_executed` remains false.
- `sdk/flutter/README.md`: package consumption/custom endpoint instructions supplied; documentation owner now maintains final wording/links.

`publish_to: none` is unchanged. Maven coordinates are local/private distribution identifiers, with no public registry availability claimed. A custom consumer must declare its broker package query and requested signature permission. Signer and permission validation remain SDK/broker responsibilities.

## Executed source and facade checks

| Command/check | Result | Evidence |
|---|---|---|
| `flutter analyze` in `sdk/flutter` | Tooling failure, exit 1 | `flutter-logs/plugin-analyze.log`, `flutter-logs/flutter_01.log` |
| `dart analyze` in `sdk/flutter` | Pass, no issues | `flutter-logs/plugin-dart-analyze.log` |
| `flutter test` in `sdk/flutter` | Pass, 2 tests | `flutter-logs/plugin-test.log` |
| `dart analyze` in `examples/flutter` | Pass, no issues | `flutter-logs/example-dart-analyze.log` |
| `flutter test` in `examples/flutter` | Pass, 11 tests | `flutter-logs/example-test.log` |
| `git diff --check` on owned paths | Pass | command exit 0 |
| source link scan | Only standard Flutter SDK toolchain includeBuild remains; no BoundaryLab project/projectDir/catalog links | `rg` source inspection |
| Python CI-script orchestration check | Pass: publication precedes APK with explicit folder; publication failure prevents APK and preserves exit/summary | `flutter-logs/script-path-check.json` |

The CI-script check replaces process execution with a recorder and is explicitly mocked orchestration evidence, not publication/build evidence. The actual publication and copied-consumer build are separate.

Flutter analysis tooling failure: installed Flutter 3.38.9 references `bin/cache/dart-sdk/bin/snapshots/analysis_server.dart.snapshot`; installed Dart 3.10.8 contains `analysis_server_aot.dart.snapshot` instead. Direct `dart analyze` succeeds with the same SDK. No global SDK/cache edit or upgrade was performed. Crash file moved outside repository to the evidence folder.

## Independent consumption fixture and build

Copied plugin and example to `consumers/flutter/{plugin,app}` outside the repository, excluding build/cache/generated metadata; changed only copied Dart path dependency to `../plugin`. Its Gradle configuration has no BoundaryLab sibling module or root catalog access. The package owner verified local core/SDK publication and standalone native/JVM consumers. The copied Flutter consumer then ran:

```sh
ORG_GRADLE_PROJECT_boundarylabRepository=/workspace/outputs/boundarylab-library-20261031/maven flutter build apk --debug --no-pub
```

Exit 0; Gradle assembleDebug completed in 33.2 seconds. Exact build log: `flutter-logs/copied-debug-apk.log`.

Build tail:

```text
Running Gradle task 'assembleDebug'...                             33.2s
✓ Built build/app/outputs/flutter-apk/app-debug.apk
```

APK: `consumers/flutter/app/build/app/outputs/flutter-apk/app-debug.apk`.
SHA-256: `3ea8bf861fe337558bbae6bca2a391c4d34e1bbc9ef50993ff6d5e424d8590c2`; 147,377,126 bytes. Machine-readable metadata: `flutter-logs/apk.json`.

The relocated fixture's initial `flutter pub get --offline --enforce-lockfile` correctly rejected the intentionally changed path dependency (`../../sdk/flutter` → `../plugin`); normal offline `flutter pub get` updated that one copied lockfile path and succeeded. Repository lockfiles were not edited. Logs retain both invocations.

Focused configuration checks, without another full build:

- Default generated BuildConfig has `CUSTOM_BROKER_ENDPOINT = false`; native code therefore uses `BrokerEndpoint.DEFAULT`. Saved `flutter-logs/default-BuildConfig.java`.
- `./gradlew --console=plain :boundarylab:generateDebugBuildConfig` with explicit repository and all three custom endpoint properties exits 0. Generated package, service and bind-permission values exactly match input, and custom flag is true. Saved `flutter-logs/custom-BuildConfig.java` and `custom-endpoint-config.log`.
- The same task with only `boundarylab.brokerPackage` exits 1 as expected with the required all-three-or-none message. Saved `flutter-logs/partial-endpoint-config.log`.

Machine-readable config evidence: `flutter-logs/endpoint-config-checks.json`. These establish Gradle configuration, not installed custom broker execution.

## Evidence boundary

Facade unit/widget tests establish Dart behavior only. An APK build establishes artifact consumption and Android compilation only. No installed custom/default broker runtime, matching-signature bind, descriptor transfer, multi-engine test, native AOSP service or OS bridge acceptance is inferred.
