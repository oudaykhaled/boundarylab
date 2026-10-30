# BoundaryLab reliability review

Date: 2026-10-30. Reviewer: independent reliability/source review agent. Status: three-round scoped source review complete; all six reported source defects corrected. Runtime validation remains pending where stated; not release approval. Root owns acceptance and board state. No source edits, Gradle runs, device operations, commits, or external writes by this reviewer.

## Scope and method

Read FIELDPACK, PROTOCOL-V1 and BUILD-DECISIONS; examined CoreEngine, Models, PositionalSource and core tests; BoundaryClient and SDK models; native PackAttempt/PackController and lifecycle tests; Flutter plugin, Dart gateway/controller and widget tests. Reviewed serialized admission, fairness, retention, terminal races, cancellation, cooperative deadlines, deduplication, worker/source lifetime, connection generations, disposal and stale async continuations. JNI, caller-identity and transport FD validation belong to the separate security review. This is a working-tree source review, not a PR or remote CI approval.

Applied Blocking/Non-blocking/Nit/Praise taxonomy and correctness, tests, performance, observability and operability review lenses. Security overlap was limited to resource ownership and stale-handle behavior. No remote telemetry/deployment architecture was added to this local product.

## Blocking findings

### REL-01 — valid positive short reads failed (source corrected, execution verified)

Original CoreEngine.kt:239–240 required `read == requested`; a stable three-byte positional source returning one byte per call failed IO_ERROR. ADR-B02 permits positive reads and requires bounded reads, not full-buffer returns. Corrective change consumes the actual count in `1..requested`, updating digest/offset by that count; zero, premature EOF and oversized returns still fail. Worker checkpoints and EOF/metadata verification remain.

Independent controlled probe compiled copied core Kotlin source under `/tmp/boundarylab-reliability-review`, outside Gradle. Source returned one byte of `abc` per call. Observed: `state=SUCCEEDED error=null bytes=3 digest=ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad closes=1`. The fix landed before probe source capture; this is post-fix execution plus an initial-source logical counterexample, not a claimed pre-fix executed failure.

CoreEngineTest now includes `positive partial reads hash complete source and premature EOF still fails`. Read final JUnit XML: 25 engine tests, zero failures/errors, timestamp 2026-10-30T13:21:03.320Z. FieldPack XML: 134 tests, zero failures/errors. `docs/evidence/build/crossplatform-qa-core-jvm-r3.json` has exit 0 and 189 passing tests across its combined suites; the core subset is 159. Its CoreEngine hash matches this review. Two additional engine cases cover 17 physical inputs and 500 seeded state transitions; their source was reviewed.

### REL-02 — native disposal removed the only descriptor cleanup callback (source corrected; runtime regression blocked by fixture crash)

Original PackController.close():242 called `main.removeCallbacksAndMessages(null)`. Counterexample: IO opens descriptor → posts owning delivery → main disposes controller before delivery → removes callback → stale branch that closes descriptor never runs. Fix preserves delivery callbacks; their `current(token)` rejection closes the descriptor. Pending polls already check disposal/revision before IPC.

Added Android test `FieldPackFlowTest.disposalClosesDescriptorAlreadyQueuedForMainDelivery`, with test-only provider and FD-baseline observation. The test blocks main, releases the provider, observes the descriptor, then disposes. It uses a 150ms allowance for IO posting, so actual callback-enqueue ordering is not fully deterministic; source reasoning establishes the failure order independently. QA ran the physical-device suite: five cases passed, then case 6 (this disposal regression) crashed; eight total were intended. Read `android-native-instrumentation-r1.json` and scoped `android-native-provider-crash-log-r1.json`: test-provider `DelayedFixtureProvider.call` throws NoClassDefFoundError for kotlin.jvm.internal.Intrinsics in its separate test-APK process. This is a fixture dependency failure, not a successful disposal check. Native owner is converting the provider to Java without weakening assertions; corrected rerun remains required.

### REL-03 — generation changes silently erased callback completion (source corrected; targeted runtime pending)

Intermediate BoundaryClient.deliverForGeneration():321–324 only invoked callbacks if generation still matched. Counterexample: status IPC throws RemoteException → lose increments generation → request completes with LOST_OR_UNKNOWN → main delivery drops that result. Native waits and Flutter method results could never settle. Fix delivers one typed CLOSED/LOST_OR_UNKNOWN failure when disposed/stale and prevents stale success; completion CAS prevents timeout plus response double delivery.

### REL-04 — SDK ingress and throttle history were unbounded (source corrected; targeted runtime pending)

Original connect/status entered an unbounded ScheduledThreadPoolExecutor before their callback/poll checks. With its single worker blocked in IPC, repeated status calls to the same handle accumulated closures without reaching `polling.add` or the pending cap. `lastPoll` also retained every observed job until reconnect, outliving bounded core retention.

Fix performs admission before enqueue, bounds pending ingress to 32, shares polling exclusion with statusByRequest, and prunes throttle history after the 250ms spacing window. Rejection/shutdown paths release admission and cleanup. Final source reserves the throttle key with `getOrPut` before scheduling, preserving a strict 64-entry history cap. Pending physical operations keep their admission slots even when the caller receives TIMEOUT_UNKNOWN; timeout does not imply cancellation or replay.

### REL-05 — Flutter cancellation/disposal allowed stale follow-on native work (source corrected; test receipts read)

Dart PackController.prepare/generated awaited connect then invoked prepare/generated before checking generation. select similarly awaited clear then opened the picker. Counterexample: prepare gen1 waits for connect → cancel gen2 completes → old connect resolves → old continuation starts native prepare after cancellation. Fix checks `_current(generation)` immediately after clear/connect before the next side effect.

Three Completer-based tests hold connect/clear, cancel or dispose, release, and assert zero downstream prepare/generated/select calls. Read `examples/flutter/evidence/local-checks.json`, `app-tests.log` and `analysis.log`: nine app tests pass, including all three post-await regressions; two plugin tests pass; analyzer reports no issues. This is presentation/gateway evidence, not Binder/device proof.

### REL-06 — recovery could suppress the sole connection-loss event (source corrected in round 3; targeted runtime pending)

BoundaryClient.lose():174–177 posts onLoss but requires the captured loss generation and RECOVERING state at main delivery. Counterexample: main is busy → worker detects loss and posts event → 250ms retry invokes bind and increments generation → main resumes → event is dropped. A previously complete pack may retain export eligibility because its consumer never received invalidation.

Final BoundaryClient.lose():173–184 posts the loss callback with a disposal-only guard before scheduling retry. Main FIFO delivers that loss before subsequent service-connection/READY callbacks. This preserves the sole event even if main delivery is delayed beyond 250ms. Middleware head independently confirmed the original concern. Source-reviewed `SdkLifecycleTest.remoteFailureSettlesOnceAndDelayedMainReceivesLossBeforeRecovery` blocks main for 450ms while recovery proceeds and asserts one LOST_OR_UNKNOWN completion plus loss-before-ready ordering. It is a fake-transport control-flow test hosted in Android instrumentation; execution remains with QA, and it does not prove real Binder process death.

## Non-blocking

Revoked-session contract wording initially required DENIED while core intentionally returns EXPIRED_OR_UNKNOWN for absent sessions. Protocol wording now agrees with implementation and tests; foreign live-session authorization remains DENIED. No revocation tombstones or persistence were introduced.

## Nit

None raised. No style-only changes requested.

## Praise

Core keeps the physical worker and owned source until read/cleanup actually exits, even after cancellation or session revocation. Hashing never holds the state monitor. First committed terminal state is immutable; bounded retention explicitly stops dedup promises after eviction/TTL. Tests use injected monotonic time and latches for terminal/cancellation/resource races. Native and Flutter invalidate export eligibility instead of combining results across attempts.

## Evidence limits and outstanding work

No exactly-once, durable receipt, hard blocked-read deadline, atomic snapshot, process RSS bound, or device performance claim follows from this review. Core metadata accounting excludes runtime object overhead. Security review, final SDK/native instrumentation, Android/Flutter builds, lint/security scan, broader compatibility and installed process-loss evidence remain with their owners. Source corrections alone do not establish those gates.

Round-3 disposition: zero known unresolved source defects among the six reported findings. No approval of the public SDK/native surface until targeted lifecycle/loss/polling instrumentation passes. Root/chief binding semantics were checked against final source: every admitted public callback completes exactly once; disposal maps to CLOSED, generation/loss maps to LOST_OR_UNKNOWN, stale success is suppressed by replacement failure rather than dropped completion. Main-thread five-second completion timers do not wait for blocked physical Binder work; descriptors remain physically owned until that work exits. SDK ownership tests cover closed-client rejection, executor rejection and 30 close/submission races. SdkLifecycleTest additionally covers 101 submitted polls under blocked transport with pending <=32 and >=250ms spacing, delayed-main loss ordering, and TIMEOUT_UNKNOWN followed by original-request lookup with no replay. These were source-reviewed, not executed by this reviewer.

The final hash list identifies the reviewed working tree, not a commit or immutable build artifact. Later edits invalidate the corresponding source review until their diff is checked. Test artifacts can be regenerated by the sole QA runner; hashes identify the exact receipts read here.

## Reviewed SHA-256 snapshot

Captured 2026-10-30T13:26:05.658376+00:00 after round-3 reread.

| File | SHA-256 |
|---|---|
| `docs/FIELDPACK.md` | `ab2a0c1ecc5e7c6fefa3edd096d5ea477ba8df044dd36f98ed4191ced3a60bdd` |
| `contracts/PROTOCOL-V1.md` | `f07bdf8e59de4b4f6fd1167c44c797ffd67c8928cecacc27702d50e5022a2448` |
| `docs/decisions/BUILD-DECISIONS.md` | `be27ac702ce45909aa784d8f57dc7a1a63a5ade6cc56b0bf9fe889000327171f` |
| `core/src/main/kotlin/dev/boundarylab/core/CoreEngine.kt` | `2f5c12615fcfd2b99c777a055ff8d4131e07a4a5d8b7aa3797441aa34f133320` |
| `core/src/main/kotlin/dev/boundarylab/core/Models.kt` | `878da64de8807d9d2fbceec6aa77a4167ec6979acddf82b3ff4acffd67ea93d5` |
| `core/src/main/kotlin/dev/boundarylab/core/PositionalSource.kt` | `29b11796e1df2cbac33bb01a62043d6c6e5ae6495d3319015aece83b431d6c8b` |
| `core/src/test/kotlin/dev/boundarylab/core/CoreEngineTest.kt` | `f725856c10abd858e9ce99c2af32773512f2542aa5b8665c070f0c21ef1ee1f1` |
| `sdk/kotlin/src/main/kotlin/dev/boundarylab/sdk/BoundaryClient.kt` | `5ca1f618334f4bbcaedc4e05776b4e569c49614e04b2ab4d03cd722a9c4b9e92` |
| `sdk/kotlin/src/main/kotlin/dev/boundarylab/sdk/Models.kt` | `3147fd80cfa3a3377d70057a64df39729bec9d5567ef304c45c937d744645639` |
| `examples/android-native/src/main/java/dev/boundarylab/nativeapp/PackAttempt.kt` | `9b3d59b8f4088077012d5c2ec4e3d138d01f7a3db04314209e4452caca070a70` |
| `examples/android-native/src/main/java/dev/boundarylab/nativeapp/PackController.kt` | `4d8ee72287782c67216aabad7f938dfe7aa2306b26d8f85895d388a813d551b5` |
| `examples/android-native/src/androidTest/java/dev/boundarylab/nativeapp/FieldPackFlowTest.kt` | `a35cfe3656567a5e47127f73999e01ef9578cc145d12e29ed5d4748c1abbfb20` |
| `sdk/flutter/android/src/main/kotlin/dev/boundarylab/flutter/BoundaryLabPlugin.kt` | `b182585e3083a1291928f1412f480c3eb98283df57a821a5cfb4d3668e6017fb` |
| `sdk/flutter/lib/boundarylab.dart` | `44b9f0654b34174b121c9885429adb533335aa0e8e31f77f815e7d64d07efdfc` |
| `examples/flutter/lib/pack_controller.dart` | `8237a2875e990630439f7eeb75dd828170fddb30e34d602715f4379224615deb` |
| `examples/flutter/test/widget_test.dart` | `bd517fe61af72f49f7b2a6af3d27eabf479596c989680279c0ac7812085939fc` |
| `android/broker/src/androidTest/kotlin/dev/boundarylab/broker/SdkOwnershipTest.kt` | `1a42d581a94b87a6456a244d41a19160b9af0c77dff4326a07d02583d025e134` |
| `core/build/test-results/test/TEST-dev.boundarylab.core.CoreEngineTest.xml` | `33dc0213e94afad6b3b864c38b274e32aa8bfe47457462461ebfb2b8802a650b` |
| `core/build/test-results/test/TEST-dev.boundarylab.core.FieldPackTest.xml` | `5eb894e8c6669ccf750d21bf158fecc342f67c432eff3203a9fba454cea5e2d7` |
| `examples/flutter/evidence/local-checks.json` | `e7dc81d6cd7ddd84da584f2c1d8a6b2b0e301afb3a1097a824bcbe1bae4bf765` |
| `examples/flutter/evidence/app-tests.log` | `d0d593d6e73511fb4df693c06d4b4e795e20b64e301d290cf20ffbe7fed2758b` |
| `examples/flutter/evidence/plugin-tests.log` | `9c51b27d14df5a6c38ffb21951d44b8fbc445e48d435e1b1bfb30f6e88224b21` |
| `examples/flutter/evidence/analysis.log` | `c56287aca0ca8160d9057a76e9035283cce9eae6dddab6a0fd0fb1f7b55ebe3a` |
| `android/broker/src/androidTest/kotlin/dev/boundarylab/broker/SdkLifecycleTest.kt` | `fed5c874263e51ca03359c55684b9cda9175567371caded887884bd5f8b0253c` |
| `docs/evidence/build/crossplatform-qa-core-jvm-r3.json` | `6bb502cfd8cc7615983e77bafa9fdd4e25205620567a3115bf10909c9213bca4` |

## Post-review runtime handoff

Three source-review rounds are exhausted. The newly observed native fixture crash was escalated to the chief and QA/native owner for correction and rerun. SDK production hash remains unchanged. Additional broker unlinkToDeath cleanup changes are outside this reviewer's primary source scope and remain with middleware/security owners. No failed or incomplete device run is represented as a passing gate.

- `docs/evidence/build/android-native-instrumentation-r1.json`: SHA-256 `e48f5d82713ee96139c74b22b69e0a83a1c487ba8715261c74a57b05e7d5a56d`.
- `docs/evidence/build/android-native-provider-crash-log-r1.json`: SHA-256 `c5ed48a88813ba9b39f42600a9558a62798975514977aef01340977ba518b9ff`.

## Chief-authorized amendment — 2026-10-30

This is a separate, one-pass review of the approved post-freeze changes. The original three-round review and its hashes remain historical; this amendment replaces only the listed slices. Chief explicitly authorized strict generated arguments, export cancellation classification, and the SDK old-generation polling cleanup guard. Middleware confirmed security review does not duplicate the polling-guard review. No production edits, Gradle runs or device tests by this reviewer.

**Blocking:** no new blocking source finding in this amendment. Source disposition is accepted for the narrow changes; final Android/Flutter execution gates remain separate.

**GeneratedArguments:** `decode` accepts an exact two-key map (`seed`, `bytes`), only Int/Long values, signed 64-bit seeds and byte count 0..134217728. Double, Float, Boolean, String, null, BigInteger and unexpected/missing fields reject; no floating coercion survives. Plugin validates before invalidating/admitting work and explicitly retains the 30,000ms deadline. Reviewed the two JVM tests and raw-channel negative instrumentation (Double/NaN/infinity followed by valid generated work). An independent standalone Kotlin probe compiled a copy of the actual decoder source in `/tmp/boundarylab-reliability-amendment` and passed 26 checks, including signed extremes, all listed rejected types, byte overflow/range and extra/missing keys. This is actual JVM decoder evidence, not installed MethodChannel evidence. QA still owns execution of the JVM test task and raw-channel instrumentation on the rebuilt artifact.

**Export cancellation:** `BoundaryLabPlugin.completeExport` now chooses CANCELLED when its attempt/generation has been invalidated, including the exception path after a write. Only a destination failure while that attempt is still current emits EXPORT_FAILED. Dart `PackController.export` preserves ready entries only for EXPORT_FAILED; CANCELLED and other errors go through `_failure`, which clears entries and disables export. The focused regression `export lifecycle cancellation never restores ready pack` asserts `canExport == false` and empty entries. Existing destination-failure retry coverage remains. Read refreshed local-checks.json and logs: 10 app tests, 2 Dart facade tests, analyzer clean; controller/test/plugin hashes match those receipts. These tests establish presentation behavior, not document-provider atomicity or real export failure timing.

**SDK poll cleanup:** `request(... cleanup = { if (expected == generation) polling.remove(requestId) })` prevents delayed old-generation completion from removing a newer generation's outstanding slot with the same request UUID. The guard does not alter descriptor cleanup, request admission decrement or public callback completion. `oldGenerationPollCleanupCannotRemoveNewGenerationSameRequestSlot` deliberately controls executor ordering and installs the replacement generation/slot, then verifies old LOST_OR_UNKNOWN, new duplicate BUSY and no extra status transport call. Source-reviewed at SDK hash b0e29c363c5677bbb8bf88060ff267b66bec4031576fd8ff61c60ab400ec00b8. Latest SDK instrumentation execution was pending at review; fake-transport/reflection evidence must not be called real process reconnect proof.

**Native fixture rerun closes the earlier runtime blocker:** independently read `android-native-disposal-r2.json`: 1/1 passed, zero skipped, suite completed; `android-native-instrumentation-r2.json`: 8/8 passed, zero skipped, suite completed. The Kotlin-to-Java test-provider correction removed the missing-stdlib fixture dependency, and the original disposal assertion remained. These actual device receipts supersede the earlier incomplete r1 result for that tested artifact; they do not automatically certify later SDK APK changes.

**Non-blocking / Nit:** none added in this slice. **Praise:** the fixes preserve typed completion and attempt invalidation rather than silently coercing inputs or reviving stale export state.

### Amendment SHA-256 snapshot

Captured 2026-10-30T13:35:50.689152+00:00. Later changes require a scoped diff check.

| File | SHA-256 |
|---|---|
| `sdk/flutter/android/src/main/kotlin/dev/boundarylab/flutter/GeneratedArguments.kt` | `220a0b90397995338357035c4ce9450138a60099023ed39271cccf908f728e8b` |
| `sdk/flutter/android/src/main/kotlin/dev/boundarylab/flutter/BoundaryLabPlugin.kt` | `eca1a634dd7190e61bea9e7239ab95b93c07a12ca52b9861691649b09861528b` |
| `sdk/flutter/android/src/test/kotlin/dev/boundarylab/flutter/GeneratedArgumentsTest.kt` | `71d743cc0c227ecd38699ea30975396ac6749af8f5cd78bc4e2ff28c4747c79f` |
| `examples/flutter/lib/pack_controller.dart` | `8237a2875e990630439f7eeb75dd828170fddb30e34d602715f4379224615deb` |
| `examples/flutter/test/widget_test.dart` | `9ef3f85b3785921637e394dc91bc97d28bebb8368d3edc85a00ae465e9282a16` |
| `examples/flutter/android/app/src/androidTest/kotlin/dev/boundarylab/fieldpack_flutter/EngineLifecycleTest.kt` | `308f48840e13f8adf1abba5f9b7898c798811a03b7445a6222639973d887c963` |
| `sdk/kotlin/src/main/kotlin/dev/boundarylab/sdk/BoundaryClient.kt` | `b0e29c363c5677bbb8bf88060ff267b66bec4031576fd8ff61c60ab400ec00b8` |
| `android/broker/src/androidTest/kotlin/dev/boundarylab/broker/SdkLifecycleTest.kt` | `5f0fe860ba0fab443f37ffa8f5214f3c107cc2364a391db660cdb314e1e286dc` |
| `examples/flutter/evidence/local-checks.json` | `e0c456d6f246a84e24f54047f774a19fe9a57a2993db5862dd291ee1d6a950ce` |
| `examples/flutter/evidence/app-tests.log` | `b07643845510da5af53d427d5a10397b25225ac21a6432c869446f67f6caa12a` |
| `examples/flutter/evidence/plugin-tests.log` | `9c51b27d14df5a6c38ffb21951d44b8fbc445e48d435e1b1bfb30f6e88224b21` |
| `examples/flutter/evidence/analysis.log` | `c56287aca0ca8160d9057a76e9035283cce9eae6dddab6a0fd0fb1f7b55ebe3a` |
| `docs/evidence/build/android-native-disposal-r2.json` | `faa068b661292673d173deb70e952ee495d06aa46da4799cfd718dce70d10a00` |
| `docs/evidence/build/android-native-instrumentation-r2.json` | `70224a06c9416d264885a5f9fc14a366f974733fd8137692b8bbb20615185533` |
