# Use BoundaryLab as a library

BoundaryLab lets a JVM application reuse one bounded job engine and FieldPack
codec, and lets Android/Flutter applications delegate supported work to a separate
Android broker process. Consumers resolve versioned artifacts without including
BoundaryLab's Gradle source projects. The current workload is generated SHA-256
or supported whole-file SHA-256 for FieldPack; it is not an arbitrary operation
plugin system. Matching hashes check byte consistency, not document safety or
provenance.

## Choose an integration

| Consumer | Dependency | Work executes in | Additional setup |
|---|---|---|---|
| JVM 17 | `dev.boundarylab:boundarylab-core:0.1.0-SNAPSHOT` JAR | Your JVM, or your separately implemented host | Supply authenticated owner identity at the transport boundary |
| Android API 30+ | `dev.boundarylab:boundarylab-android:0.1.0-SNAPSHOT` AAR | Separately installed application broker | Same signing publisher, exact component, owned signature permission, manifest permission/query |
| Flutter Android | Private `boundarylab` Flutter package plus the Android AAR | Same application broker through the Kotlin SDK | Configure the Maven repository in the consuming Android build; install the matched broker |
| Custom OS native host | Platform source from [`aosp/`](../aosp/README.md) | Native `system_ext` daemon | Custom image build, exact UID and SELinux policy; generated-only profile |

The Android AAR brings the core JAR transitively. It neither installs a broker nor
connects ordinary applications to the native OS daemon. Android API 30 is the
minimum supported installation level, not a claim of runtime coverage on every
later API. See [module contracts](../sdk/kotlin/README.md) and
[recorded QA](../docs/evidence/build/QUALITY.md) for their respective scope.

## Publish a local development repository

Use JDK 17, the committed Gradle 8.13 wrapper, Android SDK 36, AGP 8.11.1 and
Kotlin 2.2.21. The source build pins these versions. From the checkout:

```sh
./gradlew publishLibrariesToLocalRepository -PboundarylabRepository=/absolute/path/to/maven
```

Omitting the repository property writes to `build/local-maven`. The producer
accepts local folders/`file:` URLs only. It writes both libraries, Maven POMs,
Gradle module metadata, source JARs and Dokka HTML API documentation in
`javadoc`-classified JARs. Keep the full Maven folder: distributing an AAR alone
loses its transitive dependencies and metadata.

These coordinates are local/private distribution identifiers. They do not claim
Maven Central availability, and the Flutter package retains `publish_to: none`.
Private registry distribution needs its own publication setup and credentials.
Successful Flutter CI jobs also retain the complete Maven folder as the
`boundarylab-library-maven-<commit>` GitHub Actions artifact for 14 days. Download
and extract that artifact to use it as the consumer's folder repository. It is a
build artifact, not a permanent public package-registry release.
`boundarylabVersion` defaults to `0.1.0-SNAPSHOT`; set it consistently in producer
and consumer if choosing another version. A snapshot can change; retain artifact
hashes or use a unique version for a reproducible private distribution.

In an independent Gradle consumer, add the repository to
`dependencyResolutionManagement.repositories` in `settings.gradle.kts`:

```kotlin
exclusiveContent {
    forRepository {
        maven {
            name = "BoundaryLabLocal"
            url = uri("/absolute/path/to/maven")
        }
    }
    filter { includeGroup("dev.boundarylab") }
}
```

Keep the consumer's usual `google()`/`mavenCentral()` repositories for Android,
Kotlin and other dependencies. The exclusive filter prevents missing BoundaryLab
artifacts falling through to public registries. Add only the dependency you need:

```kotlin
// JVM application:
implementation("dev.boundarylab:boundarylab-core:0.1.0-SNAPSHOT")
// Android application (already includes core transitively):
implementation("dev.boundarylab:boundarylab-android:0.1.0-SNAPSHOT")
```

## Check independent artifact consumption

The [standalone examples](../examples/standalone/README.md) have their own settings
and pinned plugins. Copy them outside this checkout; no source module,
`includeBuild`, repository version catalog or `mavenLocal()` is required. From the
BoundaryLab checkout, after publication:

```sh
mkdir -p /absolute/path/to/consumers
cp -R examples/standalone/jvm examples/standalone/android /absolute/path/to/consumers/
./gradlew -p /absolute/path/to/consumers/jvm -PboundarylabRepository=/absolute/path/to/maven run
./gradlew -p /absolute/path/to/consumers/android -PboundarylabRepository=/absolute/path/to/maven assembleDebug
```

The JVM program runs a 65,536-byte generated SHA-256 job, checks an independently
calculated digest and round-trips FieldPack, then prints `PUBLISHED_CORE_OK`.
The Android build references SDK and transitive core APIs through one AAR
coordinate. Building an APK proves consumption/compilation; running it with a
separate broker is the next check.

For a repeatable build check after publishing to the default folder, run
`python3 scripts/check-library-consumers.py`. It copies both consumers into a
temporary directory outside the checkout, executes the JVM workload, builds the
Android APK, and records results under `build/evidence/library-consumers/`.
The existing Flutter CI lane runs this check; it does not execute an Android device.

## Install and run the Android path

Build and install the default broker, then install and launch the copied consumer:

```sh
./gradlew :android:broker:assembleDebug
adb install -r android/broker/build/outputs/apk/debug/broker-debug.apk
adb install -r /absolute/path/to/consumers/android/build/outputs/apk/debug/BoundaryLabStandaloneAndroid-debug.apk
adb shell am start -n dev.boundarylab.standalone/dev.boundarylab.consumer.MainActivity
```

Use one selected Android device/emulator; add `adb -s <target>` when needed. Both
APK builds must use the same signing certificate. The example uses ordinary local
debug signing; an adopter must configure a matched development or product signing
setup and keep keys private. Press **Run generated SHA-256**. Success shows
`PUBLISHED_ANDROID_OK`, the digest and separate client/broker PIDs; logcat uses
`BoundaryLabConsumer`. The marker follows remote execution, local digest checking
and a FieldPack round trip. To try a file, press **Choose document and hash**, select a supported whole file, then use
**Export FieldPack manifest** after hashing completes. The provider and descriptor restrictions below still apply. It is not produced merely by compiling the application.

For a real document, press **Choose document and hash**, select a supported local
file, then **Export FieldPack manifest**. Send the manifest and the unchanged
attachment together; the [FieldPack recipient example](../examples/fieldpack/README.md)
can verify the received bytes. This sample uses the published SDK for actual
broker work. It does not extract document text, create previews, or transfer files
automatically.

Use `BoundaryClient(applicationContext)` to keep the default broker. The SDK's
callbacks run on the main thread; Binder work runs on one owned worker. Close the
client when foreground demand ends. Whole-file descriptors transfer local
ownership immediately on `submitFile`; never reuse or close the same PFD after
submission. See [Kotlin lifecycle and descriptor rules](../sdk/kotlin/README.md).

## Use your own broker package

The broker's APK ID is configurable while its Kotlin service class stays
`dev.boundarylab.broker.BoundaryBrokerService`. Build it with a permission it owns:

```sh
./gradlew :android:broker:assembleDebug -Pboundarylab.brokerApplicationId=com.example.boundarybroker -Pboundarylab.brokerBindPermission=com.example.boundarybroker.permission.BIND_BROKER
```

Install that APK with the matching signer. In an Android consumer:

```kotlin
val endpoint = BrokerEndpoint(
    ComponentName("com.example.boundarybroker", "dev.boundarylab.broker.BoundaryBrokerService"),
    "com.example.boundarybroker.permission.BIND_BROKER"
)
val client = BoundaryClient(applicationContext, endpoint)
```

Import `android.content.ComponentName` and the SDK types from
`dev.boundarylab.sdk`. Add these entries directly under the consumer's manifest
root:

```xml
<uses-permission android:name="com.example.boundarybroker.permission.BIND_BROKER" />
<queries><package android:name="com.example.boundarybroker" /></queries>
```

The standalone Android template configures all of these using Gradle properties
`boundarylab.brokerApplicationId`, `boundarylab.brokerServiceClass` and
`boundarylab.brokerBindPermission`. General Kotlin consumers pass the typed
endpoint themselves. Default library manifest entries remain compatible and can
coexist with custom entries.

Before binding and before handshake, the SDK verifies the exact enabled/exported
service, its declared permission, ownership of that permission by the selected
broker package, signature-level protection and matching APK signatures. Each
broker method captures the real Binder calling UID and applies session ownership.
Endpoint configuration never substitutes for authorization; independently signed
third-party applications do not gain access by knowing the package name.

## Consume from Flutter

Use Flutter 3.38.9 / Dart 3.10.8 and distribute the `sdk/flutter` package privately
by path or Git. For example, copy that package outside the source checkout and
reference the copy in your application's `pubspec.yaml`:

```yaml
dependencies:
  boundarylab:
    path: /absolute/path/to/copied/boundarylab
```

Configure the folder Maven repository in the consumer's Android build as shown
in the [Flutter README](../sdk/flutter/README.md). The plugin resolves the AAR
coordinate; it does not require sibling `:core`/`:sdk:kotlin` projects or the
BoundaryLab version catalog. Use the same `boundarylabVersion` as the producer.
Install the matching broker separately.

For a custom endpoint set **all three** consumer Android Gradle properties:

```properties
boundarylab.brokerPackage=com.example.boundarybroker
boundarylab.brokerServiceClass=dev.boundarylab.broker.BoundaryBrokerService
boundarylab.brokerBindPermission=com.example.boundarybroker.permission.BIND_BROKER
```

Flutter's package property is `boundarylab.brokerPackage`; the broker APK build
property is `boundarylab.brokerApplicationId`. Supply matching values. Add the
same permission/query entries to the consumer application manifest. Omit all
three properties for the default endpoint. Partial configuration fails the build.
The endpoint is fixed when each engine attaches; every engine keeps its own
client/session. The Dart channel contract is unchanged.

For the checked-in example, after publishing:

```sh
cd examples/flutter
ORG_GRADLE_PROJECT_boundarylabRepository=/absolute/path/to/maven flutter build apk --debug
```

`select`, `prepare`, `generated` and `export` retain their existing lifecycle and
native descriptor behavior; no document bytes cross the Dart channel. See the
[plugin contract and checks](../sdk/flutter/README.md).

## Troubleshoot setup and uncertain work

| Outcome | Check or action |
|---|---|
| Maven cannot resolve `dev.boundarylab` | Publish both artifacts; check folder URI, matching version and repository visibility in the consumer. Keep POM/module metadata. |
| `DENIED` at connect | Confirm installed package/service class, consumer query, requested permission, permission owner/signature protection and APK signing certificates. Missing/hidden broker identity also returns `DENIED`. |
| `UNAVAILABLE` | Identity checks may have passed but binding failed; check service installation/enabled state and current device. |
| `INCOMPATIBLE` | Broker protocol/capabilities do not match the SDK; use matching builds. |
| `TIMEOUT_UNKNOWN` | Keep the request UUID. Resolve with `statusByRequest` in the same live session; do not automatically resubmit ambiguous work. |
| `LOST_OR_UNKNOWN` | Discard old handles. Reconnect establishes a fresh epoch/session; work is volatile and is never replayed automatically. |
| Unsupported provider/file | Use a known-length, read-only, regular, single-link whole file. Pipes, cloud-backed unknown lengths and asset subsections are outside the supported profile. |

Poll no faster than 250 ms and stop on terminal state, loss or disposal. Closing
ends that client's demand; cancellation is cooperative and blocked OS reads have
no invented interruption deadline. See the [protocol](../contracts/PROTOCOL-V1.md)
and [security profile](../SECURITY.md) for bounds and error semantics.

## Recorded library checks

The [31 October library campaign](evidence/library-20261031/README.md) records
local Maven publication, outside-checkout JVM execution, Android and Flutter
artifact consumption, installed API 35 emulator checks and native host tests.
Each receipt states its own execution scope; native host checks do not establish
a new custom-image run.

## Custom OS host and evidence boundaries

The [AOSP guide](../aosp/README.md) owns native facade/host reuse instructions.
That path needs a custom OS image, exact authorized UID and reviewed SELinux
integration. It supports generated SHA-256; file operations remain `UNSUPPORTED`.
It does not expose an ordinary public Android app SDK or arbitrary native jobs.

The retained 30 October Android 16 image receipts describe their recorded source,
userdebug probe identity and policy. New host source and host tests require fresh
matching Soong/image/boot/runtime receipts before claiming execution on a new
image. Local Maven publication, standalone JVM execution, Android app-broker
execution and the historical AOSP proof are separate results. Report each with
its own source, artifact and environment. A substantive new workload remains a
separate capability decision and implementation.
