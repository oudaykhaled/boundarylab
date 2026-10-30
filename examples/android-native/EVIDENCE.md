# Native client verification status

The product and tests were implemented in the working tree. QA owns serialized
Gradle builds and the allocated physical Android device. These are separate
classes of evidence; no ticket/gate completion is inferred from compilation.

| Check | Observed result | Receipt |
|---|---|---|
| PackAttempt JVM tests | 5 passed | [Android build r3](../../docs/evidence/build/crossplatform-qa-android-build-r3.json) |
| Pre-status-bar-fix debug/test/release APKs and lint | Passed; native before/after source hashes stable | [Native build r4](../../docs/evidence/build/crossplatform-qa-native-fixture-r4.json) |
| Release fixture exclusion | OwnerFixtureService and DelayedFixtureProvider absent from manifest and all DEX bytes | [Release inspection](../../docs/evidence/build/crossplatform-qa-release-fixtures.json) |
| First native physical instrumentation run | 5 passed, then test-provider process crashed before case 6 completed; suite incomplete | [Device run r1](../../docs/evidence/build/android-native-instrumentation-r1.json) |
| Repaired disposal regression on physical device | 1 passed, 0 skipped | [Disposal r2](../../docs/evidence/build/android-native-disposal-r2.json) |
| Full native physical instrumentation rerun | 8 passed, 0 skipped | [Device run r2](../../docs/evidence/build/android-native-instrumentation-r2.json) |
| Actual DocumentsUI selection and destination export | Two owned files selected; explicit fresh attempt succeeded; destination write confirmed and manifest pulled | [SAF journey r1](../../docs/evidence/build/android-native-saf-attempt-r1.json) |
| Status-bar correction and latest shared SDK debug/test builds, lint | Passed; final style resource hash stable | [Android final build](../../docs/evidence/build/crossplatform-qa-android-metadata-final.json) |
| Added isolated broker-process death/reconnect regression | Failed: old handle returned `UNAVAILABLE` after observed loss instead of required `LOST_OR_UNKNOWN`; SDK owner fixing, assertion unchanged | [Broker death regression](../../docs/evidence/build/android-native-broker-death-final.json) |

The first run's five completed assertions covered raw Binder metadata dedup,
declared-size rejection, cancellation export gating, real `abc`/empty file
manifest assembly, and generated digest agreement with an independent oracle
through a separate broker PID. These tests alone do not establish picker/export
behavior or broker restart recovery. The separate SAF journey used DocumentsUI
Downloads with owned note/CSV fixtures: its first attempt failed with an observed
change and correctly blocked export (exact cause unproven); a new explicit
attempt processed both stable inputs and confirmed the destination write.

The case-6 crash was a harness packaging error: Kotlin's runtime was deduplicated
into the target app and unavailable in the standalone instrumentation-provider
process. [Provider crash evidence](../../docs/evidence/build/android-native-provider-crash-log-r1.json)
identifies `NoClassDefFoundError: kotlin.jvm.internal.Intrinsics`. The fixture was
converted to Java using Android/JDK classes only. The disposal race assertions
remain unchanged; build r4 contains that fix. The isolated regression then passed
1/1 and the complete native suite passed 8/8 with no skipped tests on the allocated
physical Samsung API 36 device. This also executes the second-file-failure/fresh
rehash and same-UID/different-session handle rejection cases.

The source fix under test preserves queued descriptor-delivery callbacks after
controller disposal. Their stale-generation branch closes the just-opened file.
Removing all main-handler callbacks would discard that cleanup and leak the FD.

The observed DocumentsUI Downloads profile does not establish support for other
providers. Broker-process recovery, visual/accessibility checks and physical
measurements remain separate evidence items. The root-owned board decides acceptance from the full
required corpus.

## Installed visual correction verified

The [API 36 ready screenshot](../../docs/evidence/build/native-ready-api36.png)
records the pre-fix app: white system status icons lost contrast against the
light top inset under enforced edge-to-edge rendering. Independent design review
confirmed the defect. The two theme-resource corrections set `windowLightStatusBar=true` to request
dark status icons and align `statusBarColor` with the existing light page
background (`#F3F6F7`). The companion color preserves contrast on API 30–34,
where the opaque status-bar color is still honored; those API levels have not
been device-tested here. Neither correction changes layout or processing flow.
The earlier build/runtime receipts above predate this resource change. The final
Android build passed with the final style resource hash unchanged before/after;
the [final installed API 36 screenshot](../../docs/evidence/build/native-launch-final-api36.png)
confirms legible dark status icons on the light inset. The experience head,
designer and native implementer reviewed this capture. This is scoped visual
verification, not a complete accessibility audit or older-API runtime claim.
