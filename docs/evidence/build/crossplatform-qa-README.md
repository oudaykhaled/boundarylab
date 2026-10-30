# Cross-platform host verification — 2026-10-30

The final selected host suites passed **392 tests, zero failures, zero skips**.
Coverage was not measured. [Machine-readable index](crossplatform-qa-index.json)
selects nonoverlapping test classes from the relevant receipts; historical
attempts must not be summed. This agent performed no Android device actions.
The parent QA runner owns installed instrumentation and the actual picker,
export, loss and manual handoff journeys.

| Executed suite | Passed | Evidence |
|---|---:|---|
| Existing Python reference plus shared corpus | 174 | [reference-final](crossplatform-qa-reference-final.json) |
| Benchmark analysis/schedule | 9 | [benchmarks-final](crossplatform-qa-benchmarks-final.json) |
| Kotlin core, including strict shared parser corpus | 159 | [core-jvm-r3](crossplatform-qa-core-jvm-r3.json), core classes only |
| Final JVM host/native/process suite | 20 | [host-final-broker-tests](crossplatform-qa-host-final-broker-tests.json) |
| JVM CLI process suite | 11 | [core-jvm-r3](crossplatform-qa-core-jvm-r3.json), CLI class only |
| Native Android app unit suite | 5 | [android-build-r3](crossplatform-qa-android-build-r3.json) |
| Flutter app controller/widget suite | 10 | [flutter-app-final](crossplatform-qa-flutter-app-final.json) |
| Flutter SDK Dart channel suite | 2 | [flutter-sdk](crossplatform-qa-flutter-sdk.json) |
| Flutter native strict-number unit suite | 2 | [flutter-native-tests-final](crossplatform-qa-flutter-native-tests-final.json) |

The [shared corpus](../../../conformance/shared/README.md) contains 127 raw-byte
manifest cases, 10 filesystem recipes and 25 independent digest vectors. The
reference run includes actual 128 MiB files and observes reads bounded to
65,536 bytes. The Kotlin parser consumes the same raw manifest bytes. Native
and JVM ownership, cancellation and process behavior require their own suites;
the corpus alone does not establish those properties.

Final Android SDK/broker/native compilation and lint passed with identical
before/after source hashes in [android-metadata-final](crossplatform-qa-android-metadata-final.json).
The latest [broker test-fixture APK](crossplatform-qa-broker-fixtures-final.json)
adds the split filesystem fixture cases; a device hardlink fixture that cannot
be constructed remains explicitly unavailable, not passing. Final Flutter
[app packaging](crossplatform-qa-flutter-apk-metadata-final.json) and
[test-APK packaging](crossplatform-qa-flutter-test-apk-metadata-final.json)
include that same SDK metadata preflight revision. The earlier
[strict-number unit run](crossplatform-qa-flutter-native-tests-final.json)
remains valid for its unchanged pure parser. The compiled APKs are fingerprinted in
[artifacts](crossplatform-qa-artifacts.json). No APK build is counted as device
test execution. The native release APK separately passed [static fixture
exclusion inspection](crossplatform-qa-release-fixtures.json).

Both Dart packages passed direct `dart analyze`; the final app receipt is
[flutter-analysis-final](crossplatform-qa-flutter-analysis-final.json), with
the SDK receipt in [flutter-sdk-analysis](crossplatform-qa-flutter-sdk-analysis.json).
The installed Flutter wrapper's analyzer snapshot issue was handled using the
working direct Dart analyzer, without changing the SDK installation.
[Toolchain](crossplatform-qa-toolchain.json) records the actual versions.

Every failed attempt remains available in the index. Concrete findings routed
to owners included a CLI test package-shadowing compile error, a JVM CLI entry
point with the wrong return type, late Gradle task registration, Android safety
API/minimum-SDK mismatch and an unavailable test-fixture constant. Subsequent
passing receipts cover those fixes. The final host rerun also caught the
corrected discovery of a test whose expression-body return type had prevented
JUnit discovery; it was not counted as a skipped or passing test beforehand.

The working tree was shared with active implementers. Reports record source
hashes before/after execution, or exact reference/corpus hashes. Changes during
earlier runs triggered focused reruns. CLI's 11 tests passed before the final
host connection cleanup ordering change; the final 20-test host rerun verifies
that change. This evidence does not establish an atomic source snapshot,
provider-read interruption guarantee, physical latency campaign or coverage
percentage.
