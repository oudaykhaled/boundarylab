> Archived independent proposal, 2026-10-30. This is research input, not the selected scope or completed work. The [synthesis](../DECISION.md) and [roadmap](../../docs/ROADMAP.md) define the final plan. No product implementation or benchmark result is asserted.

# RelayFrame: a recoverable device-session framework

Independent proposal 02 — Android framework and Binder/system-software lens. Planning only, 30 October 2026. Every milestone, budget and result below is proposed. The target is the Sparagus Principal Architect — OS Middleware & System Frameworks role described in the supplied brief; the end client is unknown.

## Recommended project and why

Build **RelayFrame**, an open-source middleware framework that lets native Android, Flutter Android, and a Linux PC application submit and observe bounded, resumable device jobs through one semantic contract. The concrete job is a synthetic document-inspection batch: process deterministic generated records, report progress, persist a checkpoint, and reconnect after process death. No private documents, employer code, payments or proprietary SDKs are needed.

The product story is credible: a device vendor or enterprise application platform needs multiple application ecosystems to share execution policy and recoverable state. Its difficult problems are IPC, service ownership, caller isolation, protocol evolution, overload, startup and lifecycle. A dashboard is merely the observation surface. The distinctive deliverable is two honest deployment profiles: a downloadable SDK-based broker demonstrating production application-framework engineering, and a small AOSP framework extension proving actual platform integration. Keep these labels visible throughout the repository.

Prefer a complete 120-hour application framework followed by a gated 120-hour platform extension. Do not advertise OS implementation before the latter runs on a built image. This creates reviewable value early while preserving a path beyond an app wrapper.

## Two alternatives rejected

1. **Universal cross-device notification bridge.** Attractive demo, but permission asymmetries dominate, Android notification access differs from iOS, and a desktop relay can conceal shallow platform work. It proves integration more readily than recoverable middleware.
2. **New HAL or miniature kernel.** Greater apparent systems depth, but invented hardware adds little commercial relevance and a kernel experiment poorly covers Flutter, application ecosystems or delivery. Binder client work is not kernel work; an unnecessary HAL would obscure that distinction.

## Architecture and the real OS boundary

### SDK profile: stock-device executable baseline

Three independently installed packages are involved: broker, native Compose client and Flutter client. Broker exports an explicitly addressed bound Android `Service`; AIDL defines commands and callbacks. The clients and broker use a project signature permission and matching development certificates. A negative-test client has a different certificate. This is a deliberately same-publisher ecosystem; arbitrary third-party onboarding is outside v1.

The service checks calling UID at every entry point, associates jobs with the calling UID/user and never trusts a client-supplied package name as identity. Capture identity before handing work to another thread. Authorization cannot be delegated to an asynchronous thread that no longer has the incoming Binder identity. Avoid `clearCallingIdentity` unless a documented internal operation actually requires it, and restore identity in `finally` if used. Store application data in broker-private storage. Per-user package installations remain isolated; cross-user access is unsupported.

A thin Kotlin client library owns binding, dispatch and recovery. Flutter's platform channel invokes that same library; it is an in-process Dart/native bridge followed by Binder IPC, not a replacement Binder transport. Linux uses a per-user Kotlin/JVM daemon over a Unix-domain socket with owner-only filesystem permissions. Reuse a JVM policy/job core while keeping AIDL, socket framing, identity and scheduling in separate adapters. Flutter Linux reaches that daemon through a small Linux plugin. Android and Linux run separate job stores: v1 establishes semantic compatibility, not phone-to-PC synchronization. [Flutter platform-channel documentation](https://docs.flutter.dev/platform-integration/platform-channels) supports the bridge choice.

The broker is ordinary app software. It cannot register an OS-wide framework service, bypass process limits, change another app's scheduling, read arbitrary process memory or supply an always-running system daemon. Binding lifetime follows Android's service model; a purely bound service normally ends after the last client unbinds. Persist checkpoints before acknowledging durable acceptance; disconnected jobs pause until a client rebinds. Do not use a foreground service or battery exemption to pretend unrestricted background execution. [Android bound-service documentation](https://developer.android.com/develop/background-work/services/bound-services) defines this lifecycle.

### IPC and recovery contract

Expose `getCapabilities`, `submit`, `getSnapshot`, `subscribe` and `cancel`. Submission returns a small acknowledgement after a bounded durable transaction. Processing and callbacks occur outside Binder entry points. Use request UUID plus owner identity as a deduplication key, immutable job IDs, monotonic event sequence numbers, a service generation ID and explicit terminal states. Bound input to 16 KiB and 256 synthetic records; large arbitrary payload transport is deferred. Queue capacity is 64 jobs, eight per caller; excess receives `BUSY` rather than unbounded allocation. A separate serial job executor establishes deterministic ordering.

Remote AIDL calls can arrive concurrently on Binder pool threads. No UI-thread waits, no callback while holding core locks, no nested synchronous client call. A callback sends a compact change notification; clients retrieve the current snapshot. Slow callbacks are coalesced with a bounded pending set. Cancellation is cooperative and can lose a race to completion; the response reports the actual terminal state. [Android AIDL documentation](https://developer.android.com/develop/background-work/services/aidl) establishes threading and compatibility constraints.

Client states are `UNBOUND → BINDING → READY → RECOVERING`, plus terminal `DENIED` and `INCOMPATIBLE`. Use `ServiceConnection` for binding state and `linkToDeath` for the current remote binder. Route both death signals through one generation-aware transition so they cannot create duplicate reconnect loops. Ordinary service disconnect can recover through the existing binding; `onBindingDied` requires unbind/rebind. Treat `onNullBinding` as a terminal unavailable configuration and clean up the binding. Apply capped backoff only while a visible client owns demand. On reconnect, negotiate capability/version, resubscribe and fetch snapshots; discard callbacks from older generations. A timeout means unknown outcome, so query by idempotency key before retrying submission. Client death removes subscriptions through callback death recipients. [ServiceConnection](https://developer.android.com/reference/android/content/ServiceConnection) and [IBinder](https://developer.android.com/reference/android/os/IBinder) define these distinct mechanisms.

### AOSP profile: explicit platform work

After baseline acceptance, add `RelayPolicyService` to `system_server` on one pinned AOSP branch and expose a narrow `RelayManager` platform API to a platform-built test client. It returns a per-caller admission budget and a boot-generation value; the application broker consults it before accepting work. Policy state is ephemeral, query operations are bounded, and no job processing, database or callbacks run in `system_server`. This keeps the experiment focused on service publication, boot phase, permission checks, calling identity, lifecycle and API compatibility.

The platform patch includes service registration, build definitions, explicit permission declaration, API surface tracking, service-manager SELinux labeling/access policy and denial tests with SELinux enforcing. The regular downloadable clients continue using the broker; they do not reflect into hidden `ServiceManager` APIs. Platform-built components use the intentionally defined platform API. A platform test app and ordinary untrusted app must demonstrate allowed and denied access. Privileged installation alone is not blanket authorization; any privileged platform permission needs its proper image configuration. [AOSP permission allowlisting](https://source.android.com/docs/core/permissions/perms-allowlist) explains that boundary.

Use a frozen structured AIDL interface under the AOSP build with compatibility checking; this is not automatically provided by an ordinary Gradle app AIDL file. Do not claim VINTF stability: no vendor/system interface is involved. [Stable AIDL](https://source.android.com/docs/core/architecture/aidl/stable-aidl) distinguishes build-tracked versioning from VINTF commitments. Validate on Linux-hosted [Cuttlefish](https://source.android.com/docs/devices/cuttlefish), which supports framework development. This is framework/system-server engineering, not a custom kernel, driver or HAL.

## Exact minimum vertical slice

Within the first 40 hours: install broker plus native and Flutter Android clients on one emulator; both bind and submit the same 64-record deterministic batch; display owner-scoped progress; kill the debug broker process during execution; rebind, reconcile persisted state and finish without duplicate result records. A differently signed client is rejected. Check in the contract, lifecycle diagram, smoke script and raw timing output. The debug fault hook exists only in debug builds and terminates the actual process. A recorded demo must show distinct package/process identities, not in-process mocks.

## Requirement coverage and honest limits

| ID | Evidence and proposed acceptance | Limit |
|---|---|---|
| R01 | Roadmap, component model, three ADRs with ownership and extension gates | A small project's architecture, not organizational authority |
| R02 | Native Android and Flutter independently use the broker | Same publisher; no unrestricted plugin marketplace |
| R03 | Android phone/emulator and Linux PC pass identical semantic fixtures | No claim for Windows, macOS or iOS |
| R04 | Trace-backed dispatch/callback/load benchmark and one measured improvement | Results tied to device and workload |
| R05 | Idle/loaded/post-recovery PSS and queue-limit tests | No whole-device memory optimization claim |
| R06 | Cold app startup, bind readiness and first accepted job separately measured | No boot improvement claim |
| R07 | Process death, client death, retry and persistent-state recovery suite | Does not establish production availability |
| R08 | 1/4/16 simulated clients, fairness and bounded overload evidence | Local broker scaling only |
| R09 | Tagged executable release, migration example and commercial-use scenario | No paid deployment or commercial shipping evidence |
| R10 | Versioning, identity and concurrency ADRs plus compatibility fixtures | Project-level standards |
| R11 | Contribution guide, CI build/test gates, release checklist | Solo author cannot simulate a real review team |
| R12 | Dated comparison of Binder, socket transport and platform integration | Assessment document, not broad platform mastery |
| R13 | Separate candidate-approved qualification statement if desired | Repository cannot prove degree or equivalent history |
| R14 | SDK Binder framework plus runnable AOSP service integration | SDK profile alone has weaker OS-system evidence; no iOS claim |
| R15 | Flutter plugin and real recovery UI on Android/Linux | No React Native expertise claimed |
| R16 | Boot/service/permission/SELinux walkthrough with executed denial tests | Not kernel implementation |
| R17 | One reproducible stale-callback or duplicate-submission defect and trace-backed fix | Avoid artificial claims about commercial incidents |
| R18 | Design RFC, integration guide and review discussion if actual reviewers participate | Documents alone do not prove teamwork |
| R19 | Deep Android and Linux exposure with platform limitations documented | Fuchsia/ChromeOS/iOS remain unimplemented |
| R20 | Public interoperability issue or external contribution only when actually performed | Cannot manufacture partner work |
| R21 | Candidate may separately supply approved professional-history evidence | Project proves no European work duration |
| R22 | Open contribution process, actual contributor history if it develops | No invented international collaboration |

## Benchmark and fault plan

Pin source SHA, OS image, SDK/tool versions, build type, CPU/RAM, device model and thermal state. Measure release/profileable binaries, not debug timings. Separate emulator correctness from physical-phone performance and Linux-host performance. Use [Macrobenchmark](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview) for app startup and Perfetto traces for IPC/job stages. Record raw samples and analysis scripts.

Proposed gates: 30 cold launches per client and 1,000 warm small requests; report median/p95, sample count and spread. Initial targets are warm acknowledgement p95 below 20 ms, cold bind-to-ready p95 below 500 ms on the selected phone, and recovery below three seconds after service availability. These are hypotheses subject to baseline revision, not promised results. Separate persistence time from transport time. Compare direct-core and Binder paths with identical work, never call the whole difference pure Binder cost.

Run 100 broker-death cycles and 100 client-death cycles; require no duplicate committed outputs, leaked subscriptions or cross-owner reads. Inject death before durable commit, after commit before reply, and during callback delivery. Test disk-full/write failure, malformed bounded payload, cancellation races, delayed callback, stale protocol, package update and queue saturation. Package force-stop is a separate case with explicit user relaunch, not the same as recoverable process death. Test 30-minute saturation and post-drain memory recovery; publish PSS including runtime overhead and report unexplained growth rather than hiding it behind a generous threshold. AOSP gates add boot success, authorized/denied queries, API compatibility checks and no new SELinux denials during the scenario.

## Milestones, dependencies and budget

| Priority | Milestone | Depends on | Person-hours |
|---|---|---|---:|
| P0 | Contract, threat/lifecycle model and deterministic job core | None | 6–10 |
| P0 | Broker, persistence, Binder SDK and two Android clients | Contract | 18–24 |
| P0 | Death/reconnect tests and minimum demo | Broker | 8–12 |
| P1 | Linux daemon, Flutter Linux adapter, parity fixtures | Stable core | 18–26 |
| P1 | Compatibility, overload, memory and startup evidence | Minimum slice | 18–26 |
| P1 | Documentation, reproducible release and defect case study | Evidence | 10–16 |
| P2 | Linux build-host/Cuttlefish feasibility gate | Baseline acceptance | 8–16 |
| P2 | AOSP manager/service/permission/policy patch | Feasible image build | 30–44 |
| P2 | Platform tests, enforcing-policy verification and integration | Platform patch | 20–30 |
| P2 | Platform reproducibility, comparison demo and contingency | All previous | 12–24 |

**40 hours:** tight minimum slice; cut polish first. Not proven: PC compatibility, platform implementation, robust scale or production reliability.

**120 hours:** target the complete SDK/PC evidence package, allowing roughly 20 hours contingency against the lower milestone estimates. Not proven: AOSP service integration, kernel work, broad OEM compatibility, commercial adoption or professional-history criteria.

**240 hours:** target the AOSP extension after a successful feasibility gate. Upper estimates can exceed the budget; prioritize one image and bounded policy service, defer extra devices and plugin extensibility. Not proven: shipping a commercial OS, hardware-driver engineering, vendor certification, all-platform support or employment/partner history. If Linux virtualization/build resources are unavailable, publish the limitation and stop claiming platform completion; a patch that never booted is insufficient.

## Open-source/GitHub packaging

Suggested monorepo: `contracts/`, `core/`, `android-broker/`, `android-sdk/`, `examples/`, `flutter-plugin/`, `linux-daemon/`, `aosp/`, `benchmarks/`, `docs/` and `evidence/`. Apache-2.0 for original work, third-party notices and clear provenance for AOSP patches. Pin the exact AOSP revision and provide a patch application/build guide rather than redistributing unexplained images. Publish source-only planning first; implementation and release publication require subsequent authorized work.

README leads with a 90-second clip, deployment-profile table and evidence index linking each claim to a commit, command and result. Include ADRs, SECURITY, CONTRIBUTING, compatibility policy, roadmap and known limitations. CI runs core/contract tests, Android smoke tests and Linux parity tests; expensive AOSP validation is a documented separate job with attributable logs. No private CV or contact details. Distinguish planned, implemented, measured and externally reviewed badges.

## Five-minute hiring demo

0:00–0:45: show architecture and the SDK/AOSP boundary. 0:45–1:45: native and Flutter clients submit jobs; unauthorized client fails. 1:45–2:45: kill broker mid-acknowledgement, reconnect and show one committed result plus cleaned subscriptions. 2:45–3:30: run identical Linux contract fixture and explain independent stores. 3:30–4:15: display startup/memory traces and a reproducible optimization. 4:15–5:00: on the 240-hour track, query the booted platform service and demonstrate permission denial; otherwise label this milestone unimplemented. Finish on the requirement evidence matrix, including unprovable career criteria.

## Major risks and recommendation score

Main risks are AOSP setup overruns, turning a small broker into a distributed platform, mishandling Binder races, and presenting planned evidence as completed work. Control these with the 120-hour gate, one workload, bounded transport, explicit ownership and raw reproducible artifacts. Real reviewers and external adopters must arrive organically. Nothing here guarantees hiring.

| Criterion | Score | Rationale |
|---|---:|---|
| Relevance | 28/30 | Direct framework/middleware fit and two app ecosystems |
| Authentic system depth | 23/25 | Strong only when the AOSP profile actually boots and passes tests |
| Evidence auditability | 19/20 | Versioned traces, fixtures and denial/recovery scenarios |
| Feasibility | 11/15 | 120-hour baseline reasonable; platform toolchain is a material risk |
| Reviewer clarity | 9/10 | One job and explicit privilege boundary |
| **Total** | **90/100** | Recommend staged execution; reassess after the first evidence gate |

Primary technical sources are linked beside their claims above, verified 30 October 2026. Requirement source: [Sparagus listing](https://nl.linkedin.com/jobs/view/principal-architect-%E2%80%94-os-middleware-system-frameworks-at-sparagus-4476561682), as verified in the supplied brief. No listing text is reproduced.
