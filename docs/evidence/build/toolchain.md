# Build toolchain and verification

Evidence date: 2026-10-30. This records build infrastructure and local checks only. It is not Android device, Flutter runtime, two-process desktop or AOSP execution evidence.

## Decision

Context: BoundaryLab needs one reproducible build for a pure JVM core, Android Binder SDK/broker/native client, and desktop host/CLI. The machine has Java 17.0.14 active and Java 21.0.5 available, on macOS arm64. Android SDK platforms 35 and 36 and build-tools 35.0.0 and 36.0.0 are installed.

Decision: pin Gradle 8.13, Android Gradle Plugin 8.11.1, Kotlin Gradle Plugin 2.2.21, compile/target SDK 36, minimum Android API 30, and Java/Kotlin target 17. The API 30 installation floor follows the required `Os.fcntlInt` API; it is not a claim of API 30 runtime coverage. Actual Android execution is limited to the separately recorded API 36 target. Use AGP's default build-tools 35.0.0. Individual module builds select the JVM test engine. Root tasks preserve JUnit XML and HTML reporting.

Consequences: this tuple is inside Kotlin's published fully supported Gradle/AGP ranges, supports the installed API 36 platform, and uses the active JDK. New plugin artifacts may need downloading. Newer AGP major versions are deliberately deferred because they introduce unnecessary migration work for this initial build. Using the cached older Kotlin 2.1.0/AGP 8.7.3 combination would fall outside Kotlin's listed fully supported AGP maximum 8.7.2; Java 21 is unnecessary for the shared target.

Primary sources accessed on the evidence date:

- [AGP 8.11 compatibility](https://developer.android.com/build/releases/agp-8-11-0-release-notes): Gradle 8.13, JDK 17, build-tools 35.0.0 and API 36 support.
- [Kotlin Gradle compatibility table](https://kotlinlang.org/docs/gradle-configure-project.html): Kotlin 2.2.20–2.2.21 supports Gradle 7.6.3–8.14 and AGP 7.3.1–8.11.1.
- [Official Gradle checksums](https://gradle.org/release-checksums/): Gradle 8.13 binary distribution SHA-256 `20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78`; wrapper JAR SHA-256 `81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f`.
- [Official Gradle v8.13.0 source](https://github.com/gradle/gradle/tree/v8.13.0): wrapper launchers and JAR copied directly from the tag. The JAR digest was checked before executing it. The distribution checksum is enforced by wrapper properties.

Context7 documentation lookup was attempted but its monthly quota was exhausted; the official sources above were consulted directly.

Reuse classification: **net-new build wiring**, reusing the official Gradle wrapper and the existing Python reference checks. The domain-atlas and reuse-first instructions were read; the Guardrail Brief scopes this as a standalone public repository and rules out importing unrelated private Olivium infrastructure. No existing Gradle root build or CI workflow was present, and the only existing executable check workflow was the Python example documented in `examples/fieldpack/README.md`.

## Commands

Set `ANDROID_HOME` to an installed Android SDK or provide ignored `local.properties`; use Java 17. No global Gradle installation or configuration change is required.

```sh
python3 scripts/verify-wrapper.py
./gradlew --version
python3 scripts/check.py reference
python3 scripts/check.py jvm
python3 scripts/check.py android
```

Each command executed by `check.py` has a default 900-second deadline, adjustable to at most 3600 seconds, plus a 10-second termination grace period. Logs and exit-code summaries live under ignored `build/evidence/<profile>/`. Timeout returns 124, interruption 130, and test/build failure preserves the command's nonzero status. The runner stops on the first failed command. CI uses independent JVM jobs on Ubuntu 24.04 and macOS 14, plus Android and reference jobs on Ubuntu 24.04. It retains logs, JUnit XML, lint reports and debug APK/AAR outputs for 14 days, including on failure. CI requires no repository secrets and does not deploy or publish.

## Execution record

- `python3 scripts/verify-wrapper.py`: wrapper hash verified locally.
- `./gradlew --version`: exit 0; Gradle 8.13, launcher Java 17.0.14, macOS 26.5.1 arm64. Gradle's embedded Kotlin 2.0.21 displayed here is distinct from the pinned Kotlin project plugin 2.2.21.
- `python3 scripts/check.py reference`: exit 0; all 12 Python reference tests passed and the actual-file demo observed every expected outcome/exit code. Logs are in `build/evidence/reference/`.
- Check-runner failure-path probe: command exit 7 was retained; a 30-second subprocess with a 1-second deadline was terminated and returned 124. Both assertions passed.
- `./gradlew --no-daemon --console=plain projects`: exit 0, `BUILD SUCCESSFUL in 2m 8s`; all six modules configured successfully. Android module `kotlinOptions.jvmTarget` currently produces deprecation warnings; this does not fail configuration.
- `actionlint .github/workflows/ci.yml`: exit 0. YAML parse and Python script compilation also succeeded.
- Full module compile/test checks are owned by cross-platform QA as module implementations arrive. A successful configuration run above is not a module compile/test pass.

This is an uncommitted working-tree record; root will associate the final source commit with its consolidated evidence. No source commit or CI run is invented here.

## Additional build gates

Both hosted JVM jobs explicitly run the pinned `setup-android` action and install API 36 plus build-tools 35.0.0 before `checkJvm`. Root settings include Android projects unconditionally, so JVM task selection is not treated as proof that project configuration needs no Android SDK. This preserves the single project graph and avoids relying on a preinstalled SDK on macOS 14 or Ubuntu 24.04. This change was source-audited and validated with actionlint; no remote CI execution is claimed.

The reference profile now uses the shared machine-readable runner: `conformance/shared/run_reference.py --output build/evidence/reference/shared.json` passed 174/174 tests, and `--suite benchmarks --output build/evidence/reference/benchmarks.json` passed 9/9, with zero skipped. The reference demo also passed. Benchmark tests validate synthetic analysis inputs; they are not physical measurements.

The separate `:android:denied-client` test-only application is wired into settings. Its build requires an explicitly prepared disposable signer outside the repository; CI invokes `scripts/prepare-denied-signing.py` before building its APK and instrumentation APK. A local signer-generation check verified real key creation, outside-repository location, 0700 directory and 0600 key/environment-file modes, then removed that test key. The real differently signed bind test is for runtime QA to execute. No denial runtime pass is claimed here. The normal `checkAndroid` aggregate does not require the denial signer; CI builds the probe as a separate explicit step.

Flutter CI pins Flutter 3.38.9 through SHA-pinned [subosito/flutter-action](https://github.com/subosito/flutter-action/tree/1a449444c387b1966244ae4d4f8c696479add0b2), Java 17, API 36 and NDK 28.2.13676358 (the installed Flutter version's declared NDK). `scripts/check-flutter.py` enforces existing pub lockfiles, analyzes and runs machine-readable tests in both SDK and example, then builds the debug APK. Every command has a 1200-second bound, the CI job has a 45-minute bound, and retained logs replace repository/home paths. Flutter execution remains under the single QA Gradle lease; adding this workflow does not imply a completed CI run or Android runtime journey.

The Flutter CI job now has a 45-minute overall bound and also assembles broker/native debug APKs on that same runner after Flutter succeeds. `scripts/package-matched-apks.py` uses build-tools 35.0.0 `apksigner verify --print-certs`, requires exactly one signer per APK, and fails unless all three certificate SHA-256 digests match. Only after verification does it package the three APKs plus JSON with public certificate/APK digests, source commit/dirty status, and toolchain provenance. Its `matched-3apk-debug-build` artifact carries no keystore or private credentials. Independent Android/JVM/reference job artifacts are named `standalone-build-check`; their APKs must not be mixed into the matched set. Signer validation and compilation are not installed runtime evidence. This workflow/script change passed actionlint, Python compilation and diff checks; no local Gradle or remote CI run was performed for it.
