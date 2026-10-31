# BoundaryLab packaging investigation — 2026-10-31

Status: packaging implemented and verified locally. Core/SDK folder publication, separate-directory JVM execution, default Android build/dependency resolution, and custom-endpoint app/instrumentation APK compilation all passed. Android runtime acceptance is owned by the QA agent and reported separately. No public publication, cloud operation, CI dispatch, or Git mutation performed.

## Baseline observations before packaging

- Root `build.gradle.kts` assigns `dev.boundarylab:0.1.0-SNAPSHOT`; no publication plugins or repositories exist.
- `core` is Kotlin/JVM with JVM 17, no Android dependencies, and an implicit Kotlin stdlib dependency. It can be a normal standalone JAR.
- `sdk/kotlin` is an Android library (AGP 8.11.1, Kotlin 2.2.21, compile SDK 36, min SDK 30, bytecode 17), exposing `api(project(":core"))`. It includes the Binder AIDL and manifest permission/package visibility requirements. It can be a release AAR with a transitive core dependency in its POM and Gradle module metadata.
- Flutter plugin Android build has `implementation project(':sdk:kotlin')`. The Flutter example settings also includes `:core`, `:sdk:kotlin`, and the root version catalog through `../../../` paths. This prevents independently checking out/installing the plugin.
- Flutter package declares `publish_to: none`; retain it. Private filesystem/Git distribution is sufficient to validate a consumable Flutter package without claiming pub.dev availability.
- Broker remains a separately installed, same-publisher signed application. Shipping an AAR does not itself deploy that broker or remove that trust contract.

## Local toolchain confirmed

`./gradlew --version`: Gradle 8.13, launcher/daemon JDK 17.0.14, macOS arm64. Wrapper URL checksum is already pinned.

`flutter --version`: Flutter 3.38.9, Dart 3.10.8.

`ANDROID_HOME=/home/USER/Library/Android/sdk`; Android 36 platform and build-tools 36.0.0 are installed. Flutter binary is `/home/USER/development/flutter/bin/flutter`. Existing example local.properties points to those paths. No missing local prerequisite identified.

## Implemented packaging design

1. Reuse existing coordinates and toolchain. Add `maven-publish` to core and SDK. Publish core as `dev.boundarylab:boundarylab-core:0.1.0-SNAPSHOT` and SDK as `dev.boundarylab:boundarylab-android:0.1.0-SNAPSHOT`, explicitly documented as local/private coordinates, without claiming namespace ownership on a public service.
2. Add shared POM metadata (name, description, project/source URL, Apache-2.0 license), a folder Maven repository named `BoundaryLabLocal`, defaulting to root `build/local-maven`. Permit an explicit `-PboundarylabRepository=/absolute/folder` and version override for local/private distribution. Enforce `file:` repository URLs so this task cannot publish remotely.
3. Core: `java.withSourcesJar()`; create publication from `components["java"]`. Android: `android.publishing.singleVariant("release") { withSourcesJar() }`; create publication from `components["release"]` after AGP exposes it. Publish proper Gradle module metadata and POM rather than handing out a bare AAR. Gradle resolves the project dependency to the core publication coordinate.
4. Generate Kotlin API reference using Dokka and attach it under the conventional `javadoc` classifier for each publication. Ordinary Javadoc alone does not document Kotlin source. Dokka 2.2.0 is the currently documented plugin; pin it without updating Kotlin/AGP/Gradle. HTML documentation in a `javadoc`-classified archive avoids falsely claiming empty Java API docs are useful. Include source JARs and license in distribution.
5. Root aggregate `publishLibrariesToLocalRepository` depends on both local publish tasks. Keep existing product test/check tasks unchanged.
6. Add standalone consumer templates with their own settings and explicit plugin versions: a JVM application using core by coordinate and an Android app using only the SDK coordinate, importing a core API to prove transitivity. Copy templates into output directories outside the repository; run the existing wrapper with `-p` pointing at these separate roots. No `includeBuild`, `projectDir`, source set linking, root version catalog, or `mavenLocal()` fallback. Use an exclusive repository content filter for `dev.boundarylab` so unavailable local artifacts cannot fall through to remote registries.
7. Coordinate Flutter edits with its owner: replace plugin project dependency with the Android Maven coordinate and use a consumer-defined local/private Maven repository; remove both source module includes and root catalog linking from the example settings. Do not duplicate native Kotlin sources into Flutter or vendor an AAR without its transitive metadata.

Owned implementation files: root `build.gradle.kts`, publication-relevant `settings.gradle.kts` if needed, `core/build.gradle.kts`, `sdk/kotlin/build.gradle.kts`, shared `gradle` publication configuration, standalone consumer templates. Flutter build/settings remain coordinated with its owner. Publication/consumption checks completed below; root/QA own runtime and product suites.

## Executed publication and isolated-consumer commands

Commands ran from `/workspace/BoundaryLab`:

```sh
./gradlew publishLibrariesToLocalRepository -PboundarylabRepository=/workspace/outputs/boundarylab-library-20261031/maven
```

Inspect generated `.pom` and `.module` files plus archives to confirm core/stdlib dependency coordinates, AIDL classes in the AAR, nonempty source docs, and checksums. Capture artifact inventory/digests in the output report.

Standalone consumer templates were copied to `/workspace/outputs/boundarylab-library-20261031/consumers/` (explicit destination is outside repository). The default Android assemble and dependency report ran in the same Gradle invocation:

```sh
./gradlew -p /workspace/outputs/boundarylab-library-20261031/consumers/jvm -PboundarylabRepository=/workspace/outputs/boundarylab-library-20261031/maven run
./gradlew -p /workspace/outputs/boundarylab-library-20261031/consumers/android -PboundarylabRepository=/workspace/outputs/boundarylab-library-20261031/maven assembleDebug
./gradlew -p /workspace/outputs/boundarylab-library-20261031/consumers/android -PboundarylabRepository=/workspace/outputs/boundarylab-library-20261031/maven dependencies --configuration debugRuntimeClasspath
```

JVM consumer performs deterministic FieldPack codec encoding/decoding through the published core JAR. Android consumer compilation references `BoundaryClient` and a transitive core type while declaring only `boundarylab-android`. These are package-consumption checks, not a new runtime capability or process-boundary acceptance claim.

## Decision and alternatives

Decision: portable local Maven folder with proper component publications. It provides reproducible dependency resolution without public credentials, mutable `~/.m2` state, or source-tree access. It is directly transferable to a private registry later once credentials and namespace ownership are verified.

Alternative: `publishToMavenLocal` is fewer repository setup lines but pollutes user-global state and can hide missing artifacts through prior cache contents. Bare AAR/JAR copying is simple but loses transitive metadata and is not an adequate integration contract. Public Maven Central/pub.dev publication requires scope and verified access not supplied here.

Primary references: [Android publication variants](https://developer.android.com/build/publish-library/configure-pub-variants), [Android Maven publishing](https://developer.android.com/build/publish-library/upload-library), [Dokka Gradle documentation/archive tasks](https://kotlinlang.org/docs/dokka-gradle.html), [Dokka Javadoc format](https://kotlinlang.org/docs/dokka-javadoc.html). These describe supported APIs; local execution will establish compatibility with this repository's pinned versions.

## Final consumption results

| Check | Outcome | Evidence |
| --- | --- | --- |
| Core JAR + Android release AAR folder publication | PASS, 53s | `publication.log` |
| Independent JVM generated job + digest + FieldPack | PASS, 11s | `consumer-jvm.log` |
| Independent default Android consumer compilation + dependency report | PASS, 22s | `consumer-android.log` |
| Independent custom Android app + instrumentation APK compilation | PASS, 5s | `consumer-android-custom.log` |
| POM/module transitive metadata and archive contents | PASS | `artifact-inventory.json` |

External consumer roots: `outputs/boundarylab-library-20261031/consumers/jvm` and `consumers/android`, outside the BoundaryLab checkout. They use Maven coordinates with an exclusive local group repository; no Gradle project/composite/source path links or Maven-local fallback.

JVM result: `PUBLISHED_CORE_OK bytes=65536 sha256=0bf0c2fa457e649fae80dcdd53fc222a4784d7696cd286895ac7d9d3993d2dec manifestBytes=180`.

Core publication has 4 Kotlin source files and 125 generated HTML API documentation pages. Android publication has 3 Kotlin source files and 58 generated HTML API documentation pages. Android AAR `classes.jar` contains `BoundaryClient`, `BrokerEndpoint`, and the generated `IBoundaryBroker` classes. SDK POM and Gradle module metadata both point to `dev.boundarylab:boundarylab-core:0.1.0-SNAPSHOT`; POM scope is compile. Both publications carry Kotlin stdlib 2.2.21 metadata.

The final custom endpoint build command was:

```sh
./gradlew -p /workspace/outputs/boundarylab-library-20261031/consumers/android -PboundarylabRepository=/workspace/outputs/boundarylab-library-20261031/maven -Pboundarylab.brokerApplicationId=dev.boundarylab.qa.broker -Pboundarylab.brokerBindPermission=dev.boundarylab.qa.permission.BIND_BROKER assembleDebug assembleDebugAndroidTest
```

Android sample now provides SAF document selection → SDK `submitFile` → SHA-256 result → FieldPack JSON manifest export, alongside a deterministic generated-workload probe. Document-provider query/open/export run on one owned background worker with queue capacity 2; generation/disposal guards discard stale results and close only descriptors not yet transferred to the SDK. Activity destruction shuts down queued work; it does not claim a hard interruption deadline for an operating-system/provider call already blocked. The scrollable UI applies system-bar/cutout insets and labels/IDs for all controls. File selection retains existing whole/regular/read-only/single-link constraints and does not add text extraction/preview.

Durable Android instrumentation source is owned by QA; compilation was performed here. Runtime tests require installed default and custom same-signer brokers and are not claimed by this packaging report. The explicit Gradle slot was released to QA/Flutter after these builds.

Reuse classification: wrap. Reuse citations: existing BoundaryLab `core`, `sdk/kotlin`, generated Binder AIDL, `CoreEngine` deterministic generated workload, FieldPack codec, and SDK file-descriptor ownership contract. No new runtime implementation or service boundary was introduced.

### publication.log — last 20 lines

```text
w: file:///workspace/BoundaryLab/sdk/kotlin/src/main/kotlin/dev/boundarylab/sdk/BoundaryClient.kt:168:29 'field protectionLevel: Int' is deprecated. Deprecated in Java.
w: file:///workspace/BoundaryLab/sdk/kotlin/src/main/kotlin/dev/boundarylab/sdk/BoundaryClient.kt:168:64 'static field PROTECTION_MASK_BASE: Int' is deprecated. Deprecated in Java.

> Task :sdk:kotlin:extractReleaseAnnotations
> Task :sdk:kotlin:compileReleaseJavaWithJavac
> Task :sdk:kotlin:mergeReleaseGeneratedProguardFiles
> Task :sdk:kotlin:mergeReleaseConsumerProguardFiles
> Task :sdk:kotlin:processReleaseJavaRes
> Task :sdk:kotlin:mergeReleaseJavaResource
> Task :sdk:kotlin:syncReleaseLibJars
> Task :sdk:kotlin:bundleReleaseAar
> Task :sdk:kotlin:generateMetadataFileForLibraryPublication
> Task :sdk:kotlin:dokkaGeneratePublicationHtml
> Task :sdk:kotlin:logLinkDokkaGeneratePublicationHtml SKIPPED
> Task :sdk:kotlin:apiDocsJar
> Task :sdk:kotlin:publishLibraryPublicationToBoundaryLabLocalRepository
> Task :publishLibrariesToLocalRepository

BUILD SUCCESSFUL in 53s
39 actionable tasks: 39 executed
```

### consumer-jvm.log — last 20 lines

```text
Starting a Gradle Daemon, 1 incompatible Daemon could not be reused, use --status for details
> Task :checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :processResources NO-SOURCE
> Task :compileKotlin
> Task :compileJava NO-SOURCE
> Task :classes UP-TO-DATE

> Task :run
PUBLISHED_CORE_OK bytes=65536 sha256=0bf0c2fa457e649fae80dcdd53fc222a4784d7696cd286895ac7d9d3993d2dec manifestBytes=180

BUILD SUCCESSFUL in 11s
2 actionable tasks: 2 executed
```

### consumer-android.log — last 20 lines

```text
     |    \--- org.jetbrains.kotlin:kotlin-stdlib:2.2.21 (*)
     \--- org.jetbrains.kotlin:kotlin-stdlib:2.2.21 (*)

(*) - Indicates repeated occurrences of a transitive dependency subtree. Gradle expands transitive dependency subtrees only once per project; repeat occurrences only display the root of the subtree, followed by this annotation.

A web-based, searchable dependency report is available by adding the --scan option.

> Task :compileDebugKotlin
> Task :compileDebugJavaWithJavac
> Task :dexBuilderDebug
> Task :mergeDebugGlobalSynthetics
> Task :processDebugJavaRes
> Task :mergeProjectDexDebug
> Task :mergeDebugJavaResource
> Task :packageDebug
> Task :createDebugApkListingFileRedirect
> Task :assembleDebug

BUILD SUCCESSFUL in 22s
36 actionable tasks: 36 executed
```

### consumer-android-custom.log — last 20 lines

```text
> Task :assembleDebug
> Task :mergeLibDexDebugAndroidTest
> Task :mergeDebugAndroidTestJniLibFolders
> Task :mergeDebugAndroidTestNativeLibs NO-SOURCE
> Task :stripDebugAndroidTestDebugSymbols NO-SOURCE
> Task :validateSigningDebugAndroidTest
> Task :writeDebugAndroidTestSigningConfigVersions
> Task :compileDebugAndroidTestKotlin
> Task :compileDebugAndroidTestJavaWithJavac
> Task :dexBuilderDebugAndroidTest
> Task :mergeDebugAndroidTestGlobalSynthetics
> Task :processDebugAndroidTestJavaRes
> Task :mergeProjectDexDebugAndroidTest
> Task :mergeDebugAndroidTestJavaResource
> Task :packageDebugAndroidTest
> Task :createDebugAndroidTestApkListingFileRedirect
> Task :assembleDebugAndroidTest

BUILD SUCCESSFUL in 5s
64 actionable tasks: 40 executed, 24 up-to-date
```

## Final sample review corrections

Applied after architect review: `finishAttempt` increments the activity generation before reporting/closing, so late document-provider results cannot overwrite connection-loss/terminal results. `setBusy` disables manifest export for every active operation and restores export only when an accepted manifest exists. Successful manifest bytes remain available after completion. The already-started provider-export semantics are documented: a write already in progress may finish after activity stop, while stale completion messages are discarded. Updated source was copied into the outside-repository Android consumer; QA owns the final sample rebuild/runtime. No library republish or independent Gradle command was run for these UI-only corrections.
