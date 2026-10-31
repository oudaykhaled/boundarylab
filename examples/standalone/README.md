# Standalone consumers of local BoundaryLab artifacts

These two Gradle projects intentionally have no dependency on BoundaryLab source
paths, composite builds, version catalogs, or project modules. Copy them anywhere
outside the checkout to establish consumption of the published artifacts.
The `dev.boundarylab` coordinates below are local/private distribution identifiers;
this repository does not claim that they exist on Maven Central or another public registry.

## Publish once

From the BoundaryLab checkout, with JDK 17 and Android SDK 36 available:

```sh
./gradlew publishLibrariesToLocalRepository -PboundarylabRepository=/absolute/path/to/local-maven
```

The default destination is `build/local-maven`. Only local folder paths / `file:`
URLs are accepted. Both publications contain Maven POMs, Gradle module metadata,
sources, and real Dokka HTML API reference in a `javadoc`-classified JAR. The SDK
AAR carries a transitive dependency on `boundarylab-core`; do not distribute a
bare AAR while dropping its metadata and transitive JAR.

The version defaults to `0.1.0-SNAPSHOT`. To choose a private release version, pass
`-PboundarylabVersion=<version>` consistently to the producer and every consumer.
Snapshot builds can change; use a unique version when retaining a release.
The library folder repository is explicitly restricted to the `dev.boundarylab`
group, with no fallback to public registries for these coordinates.

## Run the JVM consumer

Use Gradle 8.13 (or the producer's wrapper with `-p` pointing to the copied project):

```sh
/path/to/BoundaryLab/gradlew -p /absolute/path/to/copied/jvm -PboundarylabRepository=/absolute/path/to/local-maven run
```

Expected output starts with `PUBLISHED_CORE_OK`. The program runs a real 65,536-byte
generated SHA-256 job, compares its digest against an independent calculation,
and round-trips a FieldPack manifest. It uses no broker or Android classes.

## Build and run the Android consumer

```sh
/path/to/BoundaryLab/gradlew -p /absolute/path/to/copied/android -PboundarylabRepository=/absolute/path/to/local-maven assembleDebug
adb install -r /absolute/path/to/copied/android/build/outputs/apk/debug/BoundaryLabStandaloneAndroid-debug.apk
adb shell am start -n dev.boundarylab.standalone/dev.boundarylab.consumer.MainActivity
```

Install the standard broker separately with the same signing certificate as the
consumer. Press **Choose document and hash** to select a local document through
Android's Storage Access Framework. The SDK transfers ownership of the read-only
descriptor to the broker, which hashes it and returns the SHA-256 digest.
**Export FieldPack manifest** writes the bounded manifest through the system save
picker; it does not copy attachments. Only whole, regular, read-only, single-link
files are supported; cloud/pipe providers, writable or changing files, and
subsection descriptors are rejected. No text extraction or document preview is
implemented.

Leaving the activity or losing the connection invalidates pending document-preparation
and polling results. Export is disabled while work is in progress. A manifest export
already writing to a provider can finish after the activity stops; provider calls
already in progress have no forced interruption guarantee. Its completion message
is discarded once the activity generation changes.

**Run generated SHA-256** retains a deterministic generated workload probe. `BoundaryClient(context)` is used for
the default endpoint. The UI/logcat tag `BoundaryLabConsumer` reports the broker
and client PIDs, and `PUBLISHED_ANDROID_OK` only after successful remote execution,
local digest verification, and a FieldPack round trip through the transitive core
API. Compilation alone is not runtime evidence. The sample closes its session
when the activity stops; it never replays an ambiguous submission.

For a custom broker identity, build both broker and consumer with the same values:

```sh
/path/to/BoundaryLab/gradlew -p /absolute/path/to/copied/android -PboundarylabRepository=/absolute/path/to/local-maven -Pboundarylab.brokerApplicationId=dev.boundarylab.qa.broker -Pboundarylab.brokerBindPermission=dev.boundarylab.qa.permission.BIND_BROKER assembleDebug
```

The service class defaults to `dev.boundarylab.broker.BoundaryBrokerService` and
can be overridden using `-Pboundarylab.brokerServiceClass=<qualified-class>`.
These properties configure both the SDK's `BrokerEndpoint` and the consumer's
manifest permission/package visibility declarations. They do not disable signing
or permission checks. A custom component must be exported and protected by the
configured signature-level permission, with the same publisher signing identity.
SDK manifest entries for the default endpoint can remain alongside these extra
consumer entries; declaring them grants no additional broker authority.

The sample uses the default local Android debug signing key. To run it against a
broker built elsewhere, configure both builds with the same development signing
key, kept outside version control. Never weaken signature checks to make a demo
connect. The consumer does not install/start a broker itself.
