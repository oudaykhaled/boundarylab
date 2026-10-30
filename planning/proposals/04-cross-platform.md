> Archived independent proposal, 2026-10-30. This is research input, not the selected scope or completed work. The [synthesis](../DECISION.md) and [roadmap](../../docs/ROADMAP.md) define the final plan. No product implementation or benchmark result is asserted.

# SessionBridge: a portable, cancellable job SDK across application runtimes

Independent proposal 04 — Flutter/React Native ecosystems and native bridges. Planning snapshot: 30 October 2026. All performance thresholds, milestones, and demonstrations below are proposed acceptance criteria, not measured achievements.

## Recommended project and why

Build **SessionBridge**, an open-source middleware SDK that lets native Android and Flutter applications submit bounded local jobs, receive progress, cancel work, and recover predictably when a provider process or application runtime disappears. A Linux desktop Flutter client exercises the same public semantics over a Unix-domain socket. A later React Native TurboModule provides a third application ecosystem without duplicating the Android provider.

The showcase workload generates and hashes deterministic synthetic chunks. It has configurable duration and payload sizes, no personal data, no cloud dependency, and repeatable correctness vectors. Its commercial analogy is an SDK that mediates expensive local operations for banking, inspection, or device-management applications. The project does not claim to process payments or have production customers.

This is deliberately a small framework with demanding lifecycle semantics. It makes the candidate's Android background useful while supplying direct Flutter evidence and visible system-software work: IPC, concurrent admission, identity, death notification, resource ownership, protocol compatibility, and bounded recovery. The interesting artifact is the SDK contract and its verified behavior, not a dashboard.

## Framework choices and two alternatives rejected

| Choice | Value | Cost and decision |
|---|---|---|
| Flutter federated plugin + Kotlin Android SDK | One Dart interface, explicit platform implementations, first-party desktop path; typed Pigeon Android boundary | Recommended first cross-platform consumer. Plugin lifetimes and message serialization remain real responsibilities. Flutter documents federated packages and engine-specific instances [S1]. |
| React Native TurboModule | Typed TypeScript/Flow specification with generated native interfaces [S2]; attractive to web-oriented adopters | Add after contract stabilizes. Use current TurboModules, not an obsolete legacy-bridge demo. Desktop requires separate platform projects [S3], so do not add RN desktop initially. |
| Kotlin Multiplatform-first SDK | Reuses the candidate's Kotlin skills and could share policy | Rejected as primary project: less direct evidence of learning Flutter/RN and a risk of spending the project on shared-code plumbing. Can be reassessed after profiling duplication. |

**Alternative project 1: a universal C++/Rust engine with Flutter FFI and RN JSI wrappers.** Reject for initial delivery. Ownership across runtimes, native packaging, sanitizers, and ABI policy could consume the budget while leaving little OS interaction. Reconsider only if measurements show serialization dominates realistic work.

**Alternative project 2: one Flutter application wrapping ordinary platform APIs.** Reject because it can look portable while hiding the hard middleware problems. It lacks independent SDK adopters, provider failure, and meaningful cross-process compatibility. SessionBridge instead requires independently built consumers and a service process.

## Architecture and real OS boundary

Proposed paths:

```text
Compose sample ─ Kotlin SDK ─────────────┐
Flutter sample ─ Pigeon ─ Kotlin SDK ────┼─ AIDL/Binder ─ Android provider APK
RN sample ─ TurboModule ─ Kotlin SDK ───┘                 bounded worker pool
                                                        session registry
Flutter Linux ─ Dart socket adapter ─ Unix socket ─ Dart CLI provider process
```

The Android provider is a separately installed, explicitly bound service, protected by a signature permission. Demo clients share a development signing identity; unrelated-signature access is a negative test. This is an intentional deployment constraint, not an SDK for arbitrary third-party apps. AIDL is justified by concurrent clients from separate applications; incoming remote calls can execute on Binder pool threads and require thread-safe handling [S4]. Dispatch commands quickly into an owned scheduler; do not hash inside a Binder entry point.

The desktop provider is initially a Dart command-line process to keep delivery feasible. Its socket lives in a private user directory with restrictive permissions and a per-launch handshake token, excluded from logs. Dart supports Unix socket addresses [S5]. Separate processes and socket framing are real OS boundaries, but a Dart daemon does not establish native Linux framework expertise. Android is the deep system implementation; Linux demonstrates portable contract behavior.

The shared contract defines `capabilities`, `openSession`, `submit`, `cancel`, `closeSession`, and ordered events. Requests contain session generation, job ID, deadline duration, bounded workload configuration, and protocol version. Local monotonic clocks measure durations; avoid comparing unrelated process clock epochs. Capabilities explicitly announce limits and optional features. Reject unsupported major versions before accepting work.

Use one serialized state owner per session, a fixed worker pool, and bounded queue. Suggested starting limits are 4 workers, 32 queued jobs, and 64 KiB inline payloads. Reject overflow with `BUSY`; never silently accumulate. Progress is coalescible; terminal events are preserved. No automatic job replay after process death: expose `PROVIDER_LOST` and let the adopter resubmit an idempotent job deliberately.

A job ends in exactly one terminal SDK state: `SUCCEEDED`, `FAILED`, `CANCELLED`, or `PROVIDER_LOST`. This is a local state-machine guarantee, not exactly-once distributed delivery. Completion racing with cancellation may win; `cancel` reports `accepted`, `alreadyTerminal`, or `unknownJob`. Engine disposal invalidates callbacks and cleans registrations. Cancellation must reach native/provider work; wrapping a Dart Future in a timeout only stops waiting and does not stop the source Future [S6].

## Exact minimum vertical slice

The minimum executable slice is Android only: install one provider APK plus Compose and Flutter sample APKs; negotiate capabilities; submit the same seeded workload; display identical digest; cancel a long job; terminate the provider through a debug-only test control; show a terminal failure and reconnect on explicit retry. Record process IDs to prove work crosses Binder. This fits the 40-hour discovery tier, with modest UI and one device.

The recommended 120-hour target adds Linux desktop, lifecycle race tests, reproducible benchmarks, public SDK packaging, and the adoption walkthrough. It is only called cross-device verified after both phone and PC tests actually run. iOS, Windows, Fuchsia and ChromeOS remain explicit unsupported/unverified rows, not inferred from framework marketing.

## Exact bridge, lifecycle, thread and cancellation demonstrations

| Experiment | Controlled failure | Required corrected behavior and evidence |
|---|---|---|
| Bridge overhead | Send 1/16/64 KiB chunks via Flutter/Pigeon; compare equivalent native SDK calls | Record serialized bytes, end-to-end latency and allocations; distinguish runtime crossing from Binder costs. No zero-copy claim. |
| Main-thread stall | Debug variant executes a 200 ms blocking operation in platform handler | Trace shows stall; corrected variant uses background handler/worker and correct callback dispatch. Flutter documents TaskQueue and platform-thread requirements [S7]. |
| Activity recreation | Recreate activity 50 times during active jobs | No retained destroyed Activity; session policy survives recreation when engine survives. Separate engine destruction must cancel/close. ActivityAware distinguishes detach for configuration change from final detach [S8]. |
| Engine isolation | Start two Flutter engines, both using job ID 1; destroy one | Generation + session identity prevents cross-delivery; surviving engine completes. Plugin-instance state must not be global [S1]. |
| Cancellation race | Test barrier pauses worker just before result commit; alternate cancel-before-release and release-before-cancel for 1,000 trials | One terminal outcome; accepted cancellation prevents later success; completion-first reports already terminal. Worker counters return to zero. |
| Timeout leak | Set Dart timeout to 50 ms on 5 s workload | First demonstrate worker remains active; SDK deadline then sends cancel and reclaims worker. Measure actual cleanup latency, not only disappearing spinner [S6]. |
| Provider death | Kill provider with jobs active, then explicitly rebind | Every pending job terminates once; no stale callback accepted after new generation. Use Binder death callback and trace cause [S9]. |
| RN runtime disposal, later tier | Destroy/recreate React Native instance during job | Invalidate subscriptions and sessions; no call into disposed runtime. Native module lifecycle guidance covers recreation and cleanup [S10]. |
| Backpressure | One slow subscriber, two active clients, 100 submit attempts | Bounded queue, explicit rejection counts, terminal preservation, and no starvation of second client's admitted work. |

Fault switches ship only in dedicated demonstration/test builds. Production SDK artifacts must contain neither kill controls nor deliberately blocking handlers.

## SDK adopter story

An adopter begins in a fresh sample repository with a tagged package and documented provider installation, rather than source-relative dependencies. In under 30 minutes, the adopter should obtain capabilities, start a session, render progress, cancel, handle `BUSY`/`PROVIDER_LOST`, and dispose the session. Count steps and record actual completion time when tested; 30 minutes is a target.

Ship a Kotlin artifact, Dart facade/platform interface/Android and Linux implementations, and later an npm TurboModule package. Explain signature trust and provider availability before the quickstart. Provide a capability matrix, migration example from v1 to additive v1.1, error recovery table, and runnable contract fixtures. A separately built consumer must pass against the previous compatible provider version. Seek real voluntary adopter feedback later; self-authored sample apps are not independent partner validation.

## R01–R22 evidence and limits

| ID | Proposed evidence and acceptance | Limit |
|---|---|---|
| R01 | Architecture RFC, three-stage roadmap, explicit ownership and evolution ADR | Project-level architecture only |
| R02 | Compose and Flutter independently consume provider; RN later | Samples are not ecosystem adoption |
| R03 | Same fixture results on physical Android phone and Linux PC | No untested OS claims |
| R04 | Native/Flutter transport and workload latency reports | Device/workload-specific |
| R05 | Heap/PSS plus queue/worker counters, lifecycle soak | No universal footprint guarantee |
| R06 | Cold app startup and separate first-provider-ready metric | Framework startup not wholly SDK-controlled |
| R07 | Race, engine disposal, process-death and reconnect suite | Bounded experimental reliability |
| R08 | 1/2/4 clients, bounded queue, fairness/rejection report | Local scale, not cloud fleet scale |
| R09 | Tagged SDK, independent consumer build, adopter walkthrough | Commercial relevance; no shipping claim |
| R10 | Versioning, ownership, threading, errors and capability ADRs | Standards within project |
| R11 | Contribution guide, CI contract tests, review checklist | Solo reviews labeled self-review |
| R12 | Dated Flutter/RN/KMP assessment with upgrade experiment | Not continuous industry coverage |
| R13 | Architecture rationale demonstrates practice | Degree must be evidenced separately |
| R14 | Android lifecycle/service integration and real IPC provider | Framework/system middleware, not kernel work |
| R15 | Authored Flutter plugin and consumer with lifecycle tests | Framework depth bounded by scope |
| R16 | Binder thread/process/death trace and privilege explanation | No modified Android kernel or HAL |
| R17 | Reproducible cancellation/stale-callback defect and fix | Controlled bug, not production incident |
| R18 | RFC, diagram, issue template, recorded technical walkthrough | Cross-team work requires real participants |
| R19 | Android depth; Linux peer-platform sample | Fuchsia/iOS/ChromeOS not required or proven |
| R20 | Public adopter guide and future upstream contribution candidate | Partner work only after actual engagement |
| R21 | No project substitute; candidate may supply verified work history separately | European tenure cannot be manufactured |
| R22 | Accessible English docs and collaboration process | International collaboration needs real records |

## Measurable benchmark and fault plan

Pin toolchains and record OS build, device model, CPU architecture, thermal state, power mode, commit, build mode and raw outputs. Freeze a tested version rather than promising compatibility with whatever is newest. Run release/profile-appropriate builds; keep diagnostic instrumentation overhead separately measured.

For startup, collect 30 cold and 30 warm launches, and separately time SDK initialization/provider binding. Android Macrobenchmark provides the app-level measurement harness [S11]. For calls, collect 1,000 samples after warm-up at each payload/concurrency point; report p50/p95/p99 with sample counts and run-to-run variation across three runs. Time from SDK entry to terminal callback, with spans for bridge, IPC queue and computation. Compare framework overhead against the native client using identical service configuration, never different algorithm implementations.

Initial targets: no main-thread job processing; cancellation cleanup p95 below 250 ms for cooperative workload; all admitted jobs terminal within 2 s of observed provider death; 100% digest agreement; zero duplicate terminal events; queue never above configured capacity; no retained destroyed Activity after a 100-cycle soak. Measure PSS separately for client/provider and report idle versus loaded deltas. Establish memory/startup baselines before setting regression gates; proposed gate is ≤10% regression on controlled hardware, reviewed with variance rather than blindly enforced on shared CI.

## Prioritized milestones, dependencies and time tiers

| Milestone | Dependency | Person-hours | Exit |
|---|---|---:|---|
| M0 contract, platform spike, threat/lifecycle ADR | None | 6–10 | Generation/cancel model reviewed against tests |
| M1 Android provider + Kotlin consumer | M0 | 12–18 | Process boundary and digest/death demonstrated |
| M2 Flutter bridge + sample | M1 | 12–18 | Slice works, disposal and cancellation documented |
| M3 fault harness + benchmark baseline | M2 | 18–26 | Reproducible evidence bundle |
| M4 Linux socket adapter/provider | M0; integration after M2 | 18–28 | Same contract fixtures on PC |
| M5 packaging, compatibility and adopter demo | M3/M4 | 12–20 | Fresh consumer works from tag |
| M6 React Native Android TurboModule | M5 | 24–36 | Same Android provider, runtime disposal tests |
| M7 deeper scheduling/security/soak + independent feedback | M6 | 30–50 | Auditable failures, fixes and limitations |

**40 hours:** M0–M2 lean slice, one device and basic trace. NOT proven: PC compatibility, broad lifecycle reliability, performance improvement, RN expertise or production readiness.

**120 hours:** target M0–M5, whose estimate spans 78–120 hours. Prioritize correctness over polish and reserve unused lower-range hours for integration. NOT proven: RN implementation, iOS/Windows support, kernel development, commercial deployment or external collaboration.

**240 hours:** M0–M7 spans 132–206 hours; remaining 34–108 hours covers difficult integration, second Android device, longer soak, onboarding repairs and release hardening. NOT proven: universal OS support, industrial certification, employment history, external adoption unless obtained, or kernel authorship. Do not expand to another OS simply to consume budget.

## Open-source/GitHub packaging

Propose Apache-2.0 licensing after dependency/license review. Repository areas: contracts, android-provider, sdk-kotlin, sdk-flutter, desktop-provider, examples, benchmarks, evidence, docs/adr; RN is added only when implemented. README leads with a 90-second preview, install steps, architecture, measured/unmeasured badge table and limitations. Publish source, fixtures, benchmark scripts, sanitized raw measurements and trace capture instructions; large traces can be release attachments.

CI builds supported targets, validates generated bindings, runs contract/race tests and checks API compatibility. Hardware performance reports remain separately labeled. Release checklist records exact supported versions, provider trust model, changelog and verification SHA. Public artifacts exclude local paths, resumes, contacts, employer details and credentials. Creating resources/publishing packages belongs to a later authorized implementation phase.

## Five-minute hiring demo

0:00–0:40: show contract and actual client/provider PIDs. 0:40–1:30: Compose and Flutter submit identical vectors. 1:30–2:30: cancel at the result barrier and show one terminal outcome plus zero workers. 2:30–3:15: destroy engine or kill provider and expose recovery policy. 3:15–4:00: Linux PC runs the same fixture, highlighting transport differences. 4:00–4:40: inspect trace and native-versus-Flutter benchmark with hardware labels. 4:40–5:00: show fresh adopter instructions, one rejected architecture decision, and explicit unproven requirements.

## Major risks and honest gaps

Three runtimes can turn a focused SDK into packaging work; defer RN until the Android/Flutter contract is sound. Flutter-generated typing does not solve cancellation, lifetime or version negotiation. Two independently implemented providers can drift; shared fixtures, not assumed code similarity, control that risk. Debug fault variants can bias results; publish build identities and repeat corrected-path measurements. Signature trust simplifies the demo but constrains commercial distribution. A pure user-space project supports OS reasoning without proving kernel implementation. No portfolio can establish degrees, years of European work, paid commercial shipping, genuine partnerships or hiring suitability by itself.

## Primary sources

Verified official pages during this planning pass; Context7 lookup was attempted but quota-blocked. Current documentation is moving documentation, so implementation must pin and recheck versions.

- [S1 Flutter packages, federation and multiple engines](https://docs.flutter.dev/packages-and-plugins/developing-packages)
- [S2 React Native Turbo Native Modules](https://reactnative.dev/docs/turbo-native-modules-introduction)
- [S3 React Native out-of-tree platforms](https://reactnative.dev/docs/out-of-tree-platforms)
- [S4 Android AIDL and threading](https://developer.android.com/develop/background-work/services/aidl)
- [S5 Dart Unix-domain InternetAddress](https://api.dart.dev/dart-io/InternetAddress/InternetAddress.html)
- [S6 Dart Future.timeout semantics](https://api.dart.dev/dart-async/Future/timeout.html)
- [S7 Flutter platform channels and threading](https://docs.flutter.dev/platform-integration/platform-channels)
- [S8 Flutter ActivityAware lifecycle](https://api.flutter.dev/javadoc/io/flutter/embedding/engine/plugins/activity/ActivityAware.html)
- [S9 Android Binder death notification](https://developer.android.com/reference/android/os/IBinder.DeathRecipient)
- [S10 React Native module lifecycle](https://reactnative.dev/docs/the-new-architecture/native-modules-lifecycle)
- [S11 Android Macrobenchmark](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview)
- [S12 Android bound services](https://developer.android.com/develop/background-work/services/bound-services)

## Scored recommendation

| Criterion | Score | Rationale |
|---|---:|---|
| Role relevance | 27/30 | Direct middleware, framework integration, lifecycle and compatibility evidence |
| Authentic system depth | 20/25 | Real Binder/socket processes and ownership; no OS-source modification |
| Evidence auditability | 19/20 | Deterministic vectors, fault barriers, traceable builds and raw data |
| Feasibility | 12/15 | 120-hour target plausible but desktop and packaging remain integration risks |
| Reviewer clarity | 9/10 | Small SDK story with observable before/after behavior |
| **Total** | **87/100** | Recommend 120-hour Flutter + Android + Linux release, RN as 240-hour extension |
