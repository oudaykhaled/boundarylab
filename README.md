# BoundaryLab

**Bounded local middleware for Android, Flutter and a desktop JVM—with a real attachment-integrity example.**

BoundaryLab shares admission, ownership, cancellation, deadlines and process-loss behavior across clients. Android apps call a separately installed Binder broker; the desktop CLI calls a separate JVM host over a local Unix socket. Jobs and sessions are volatile.

**FieldPack** is the example: select inspection attachments, hash them through the broker and explicitly export a SHA-256 manifest. A recipient obtains the attachments separately and checks a local directory. Changed and missing files are reported. Matching bytes establishes consistency, not sender authenticity.

## Build status

Engineering prototype: core, desktop host/CLI, Android broker/SDK, native client and authored Flutter plugin/client are implemented. At source `06b2d4f`, 393 selected host test cases passed; final physical-device suites passed 31 cases with one explicitly unavailable hardlink fixture. Native and Flutter each exported a real manifest, and separate JVM/Python recipients agreed on all five good/changed/missing/unsupported outcomes. An isolated fresh-checkout desktop consumer and all five [hosted CI lanes](https://github.com/oudaykhaled/boundarylab/actions/runs/41068567469) passed. See the [quality report](docs/evidence/build/QUALITY.md), [current ticket status](planning/EXECUTION-STATUS.md) and [requirement evidence map](docs/IMPLEMENTATION-STATUS.md). The pinned Android16 baseline and custom native-service image also built and booted; the [minimum AOSP proof](docs/evidence/aosp/custom-20261030/README.md) passed 236 harness observations and 61 native assertions at `444ae29`. Compatibility evolution, performance/soak and fresh final-image reproduction remain open.

No production release, commercial shipment, external adopter or human implementation review is claimed. [Astra architecture decisions](docs/decisions/BUILD-DECISIONS.md), [security review](docs/evidence/reviews/SECURITY-REVIEW.md) and [reliability review](docs/evidence/reviews/RELIABILITY-REVIEW.md) document the actual AI review scope.

Actual installed-device captures below show explicit successful exports. These are runtime screenshots; the separate animated presentation illustrates the architecture proposal.

<p>
  <img src="docs/evidence/build/native-exported-post-loss-api36.png" width="300" alt="Native FieldPack client after real manifest export" />
  <img src="docs/evidence/build/flutter-exported-final-api36.png" width="300" alt="Flutter FieldPack client after real manifest export" />
</p>

[Real mobile-to-desktop results](conformance/desktop/mobile-handoff.json) · [Observed defect and verified fix](docs/case-studies/broker-loss.md) · [AOSP proof](docs/evidence/aosp/custom-20261030/README.md) · [US$6.17 estimated infrastructure and verified teardown](docs/evidence/aosp/teardown-20261030/README.md)

## Run the reference now

Python3.10+ on macOS or Linux/POSIX, standard library only:

```sh
python3 -m unittest discover -s examples/fieldpack -v
python3 examples/fieldpack/demo.py
```

The [reference walkthrough](examples/fieldpack/README.md) uses actual owned note/CSV/PNG files, successful verification, tampering, removal and invalid input. It executes in one process and is independently useful for checking native output.

## Build the implementations

Use JDK17, Android SDK36/build-tools35.0.0, Flutter3.38.9/Dart3.10.8, and a trusted local C compiler for desktop file support. Gradle8.13/AGP8.11.1/Kotlin2.2.21 are pinned. Android installation minimum is API30 (Android11); actual local device evidence is API36. Broader runtime support is untested. No global SDK upgrade is required.

```sh
# Root checks are intentionally serialized. These do not install or test a device.
python3 scripts/verify-wrapper.py
python3 scripts/check.py reference
python3 scripts/check.py jvm
python3 scripts/check.py android

# Flutter package/widget checks and debug APK build
python3 scripts/check-flutter.py
```

The installed local Flutter SDK's wrapper analyzer is incomplete; local receipts identify the equivalent direct Dart analyzer used. CI uses a complete pinned SDK and retains each command's actual result.

- [Desktop host/CLI quickstart](desktop/README.md): exact tested installDist commands, private socket directory, real files, separate recipient, changed/missing results.
- [Flutter consumer](examples/flutter/README.md): typed native plugin, real file picker, session lifecycle and developer diagnostics.
- [Original design](design/DESIGN.md) and [interactive prototype](design/index.html): visual/state source; prototype execution is explicitly simulated.
- [File/job contract](docs/FIELDPACK.md) and [wire contract](contracts/PROTOCOL-V1.md): strict limits and failure semantics.

Native debug APKs are built at `android/broker/build/outputs/apk/debug/` and `examples/android-native/build/outputs/apk/debug/`; Flutter at `examples/flutter/build/app/outputs/flutter-apk/app-debug.apk`. Broker and both consumers **must have matching signing certificates**. Build them on the same local signing setup or use the matched three-APK CI bundle. A separately signed denial fixture is intentionally rejected. Private signing keys are never published.

## What is bounded

32 flat aliases,128MiB per file **and pack**,64KiB reads,16KiB manifest, one worker, fixed queue/session/retention caps. Android accepts the supported read-only regular seekable file profile; arbitrary/cloud-backed providers are not promised. The desktop JNI helper supplies descriptor-relative no-follow opening and same-handle validation that the tested JDK does not expose. Generated work remains available when native file support is unavailable.

Cancellation is cooperative, including storage reads. Broker loss means lost/unknown, a fresh epoch and explicit resubmission. No persistence, atomic multi-file snapshot, automatic transfer/sync, authenticity or exactly-once claim. See [security scope](SECURITY.md).

## Follow the work

[GitHub delivery board](https://github.com/users/oudaykhaled/projects/3) · [FieldPack view](https://github.com/users/oudaykhaled/projects/3/views/2) · [40 detailed tasks](planning/TICKETS.md) · [Planv2](docs/PLAN-V2.md) · [22-item job evidence matrix](docs/REQUIREMENTS.md). The original40/120/240h hypotheses were superseded by the leaf plan; agent concurrency does not substitute for acceptance evidence.

The [animated presentation](presentation/README.md) explains the architecture proposal; it is an illustration rather than recorded middleware execution. Original [research proposals](planning/DECISION.md) and [ten planning reviews](planning/reviews-v2/README.md) remain inspectable.

Prepared for the Principal Architect—OS Middleware & System Frameworks role. Career experience, genuine partners, sustained European work and international collaboration require authentic external evidence; software cannot manufacture them. [Contributing](CONTRIBUTING.md) · [evidence policy](docs/EVIDENCE-POLICY.md) · [Apache-2.0 license](LICENSE).
