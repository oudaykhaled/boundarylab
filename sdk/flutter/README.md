# BoundaryLab Flutter plugin

Use the [library guide](../../docs/LIBRARY-GUIDE.md) for the full adopter setup,
including the separate broker APK and JVM/Android consumer checks.

Android implementation for Flutter 3.38.9 / Dart 3.10.8, JDK 17 and Android
SDK 36. Minimum installed Android API: 30. `BoundaryLab` owns one
native client per Flutter engine. The native adapter uses the Kotlin SDK and the
separate signature-protected `dev.boundarylab.broker` application. It never hashes
in Dart or inside the plugin.

The Android dependency is `dev.boundarylab:boundarylab-android:0.1.0-SNAPSHOT`.
Its Maven metadata supplies the transitive `boundarylab-core` dependency. The
consumer does not include sibling Gradle projects or a repository version catalog.
`publish_to: none` remains intentional: distribute this Flutter package privately
by filesystem or Git, and provide the matching Maven artifacts separately. These
coordinates do not claim availability on Maven Central or pub.dev.

Publish the libraries from the BoundaryLab source checkout first:

```sh
./gradlew publishLibrariesToLocalRepository -PboundarylabRepository=/absolute/path/to/maven
```

Add the folder repository to the consuming Android root `build.gradle.kts`:

```kotlin
allprojects {
    repositories {
        exclusiveContent {
            forRepository {
                maven {
                    name = "BoundaryLabLocal"
                    url = uri("/absolute/path/to/maven")
                }
            }
            filter { includeGroup("dev.boundarylab") }
        }
        google()
        mavenCentral()
    }
}
```

The exclusive group filter prevents an absent local BoundaryLab artifact from
falling through to public registries. A private Maven repository may replace the
folder in an adopter's build. Keep its credentials outside source control. Use
`boundarylabVersion` to select the matching published SDK version; the default is
`0.1.0-SNAPSHOT`.

The checked-in example accepts an explicit folder via Gradle property. From
`examples/flutter`, build it with:

```sh
ORG_GRADLE_PROJECT_boundarylabRepository=/absolute/path/to/maven flutter build apk --debug
```

Both client and the separately installed broker must have matching signing
identity. No signing material belongs in this repository.

For an adopter-owned broker, set all three properties in the consumer's
`android/gradle.properties` (or pass them as Gradle project properties):

```properties
boundarylab.brokerPackage=com.example.boundarybroker
boundarylab.brokerServiceClass=dev.boundarylab.broker.BoundaryBrokerService
boundarylab.brokerBindPermission=com.example.boundarybroker.permission.BIND_BROKER
```

The broker's application ID can differ from its service's Kotlin package: use
the actual fully qualified service class. Declare the custom package visibility
and signature permission request in the **consumer application** manifest:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="com.example.boundarybroker.permission.BIND_BROKER" />
    <queries><package android:name="com.example.boundarybroker" /></queries>
    <application ... />
</manifest>
```

These values construct the SDK's typed `BrokerEndpoint` when the engine attaches.
Omitting all three preserves the exact original broker defaults; partial
configuration fails the build. The endpoint is fixed for that engine's lifetime,
so there is no runtime session migration or changed channel contract. SDK and
broker signing/permission checks remain mandatory. The library manifests retain
their default permission and package query for compatibility; the custom entries
are additive. This configures the ordinary Android application broker transport;
it does not select or integrate the separate native AOSP service.

`select()` presents Android's document picker. Native code retains URIs; Dart
receives engine-scoped opaque selection tokens, safe public aliases, and a bounded
provider display name for local source-to-alias mapping. Display names never enter
the manifest. `prepare()` opens each selected URI natively with read authorization,
passes the descriptor to the SDK, and polls sequential jobs at 250 ms or slower.
The broker validates supported descriptors. Providers returning pipes or unknown
lengths are unsupported. `generated()` uses the same SDK and separate broker.

`export()` offers a new SAF document only after the current complete attempt
succeeds. Native shared `FieldPackCodec` encodes the sorted manifest. Success is
reported after destination write, flush and close. A failed write attempts to
remove the newly created document; provider cleanup may fail. SAF providers do
not offer the POSIX atomic no-replace publication guarantee. No crash durability
is claimed. Destination cancellation keeps the complete attempt available.

Cancellation invalidates export immediately and waits for pending provider open
or broker work to reach its observed terminal outcome. Reads may block; there is
no invented hard cleanup deadline. Broker loss, engine disposal, selection changes
and new attempts discard old results. Activity recreation never resubmits a job.
Engine disposal closes only its own SDK session and native selections.

Validation: run `flutter test` and `dart analyze` here. These mock-channel tests
verify facade behavior only. Installed multi-engine and real Binder evidence is
produced separately from the sample Android instrumentation tests.
