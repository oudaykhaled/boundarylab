# Contributing

BoundaryLab contains an executable Kotlin core, Android broker and SDK, Flutter
plugin, desktop host/CLI, and a separate native AOSP profile. Start with the
[README](README.md), [library guide](docs/LIBRARY-GUIDE.md),
[protocol](contracts/PROTOCOL-V1.md) and [FieldPack contract](docs/FIELDPACK.md).
Choose a focused change and describe its behavior and evidence before updating
its status. Current work is tracked in [the local board](planning/BUILD-BOARD.md)
and [execution status](planning/EXECUTION-STATUS.md).

## Build and check your change

Use JDK 17, Gradle 8.13, AGP 8.11.1, Kotlin 2.2.21, Android SDK 36 and build-tools
35.0.0. Flutter uses Flutter 3.38.9 / Dart 3.10.8. Android consumers require API 30
or later. Python reference checks need Python 3.10+. Desktop file support also
needs a trusted local C compiler. Use the committed Gradle wrapper.

Run checks appropriate to the files you changed, from the repository root:

```sh
python3 scripts/verify-wrapper.py
python3 scripts/check.py reference
./gradlew checkJvm checkAndroid
python3 scripts/check-flutter.py
```

`checkJvm` executes core and desktop tests. `checkAndroid` builds, runs local unit
tests and lints; it does not install or execute on Android. For installed tests,
coordinate one device/Gradle owner and use the commands in the
[broker README](android/broker/README.md) and
[native example README](examples/android-native/README.md). Record the actual
Android API, source and APK identities; API 30 as a minimum is not evidence that
every API level was exercised. AOSP source changes need the separate
[AOSP workflow](aosp/README.md); a Mac host check is not a Soong or image result.

For packaging changes, publish to an explicit local folder and copy the
[standalone consumers](examples/standalone/README.md) outside the checkout.
Verify both dependency resolution and actual operation. Do not use sibling source
projects or a warmed global Maven cache to claim independent artifact consumption.
Do not upload to Maven Central, pub.dev or another registry as part of local checks.

## Preserve the contract

Keep changes small and reviewable. Preserve V1 wire methods, bounded resources,
descriptor ownership, UID/session isolation and reciprocal same-publisher signing
checks. Retain existing default endpoints when adding configuration. Explain any
behavior change and its migration or rejection path in an
[architecture decision](docs/decisions/BUILD-DECISIONS.md). Do not edit a frozen
AIDL snapshot or a golden fixture merely to make a failure pass.

Add meaningful regression coverage for changed behavior, especially denial,
loss/timeout, cancellation and ownership. A mock verifies its modeled behavior;
it does not replace a real cross-package or process-boundary test. Document any
unsupported outcome rather than bypassing validation. Preserve third-party
notices, commit toolchain pins, and use synthetic fixtures. Do not add employer
code, private data, signing keys or selected document contents/URIs to logs.

## Report reproducible evidence

Follow the [evidence policy](docs/EVIDENCE-POLICY.md). Record exact source/artifact
hashes, commands, environment, exit status and expected markers. Distinguish code
implemented, artifact built, installed execution and custom-image execution.
Preserve historical receipts and failed attempts. New source does not inherit a
pass from the recorded 30 October AOSP image, and host tests do not establish
native execution, performance or soak. Remove private device identifiers before
sharing evidence without changing the technical result.

Update the relevant module README and guide when setup or public behavior changes.
Keep recorded showcase assets and evidence immutable; add a new receipt for a new
run. The maintainer remains responsible for decisions, claims, review and release
quality.
