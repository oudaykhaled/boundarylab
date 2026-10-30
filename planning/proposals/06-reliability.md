> Archived independent proposal, 2026-10-30. This is research input, not the selected scope or completed work. The [synthesis](../DECISION.md) and [roadmap](../../docs/ROADMAP.md) define the final plan. No product implementation or benchmark result is asserted.

# RelayGuard: recoverable local middleware across Android and Linux

Independent proposal 06 · 30 October 2026 · reliability, test architecture and IPC lens. Planning only: all performance numbers below are proposed budgets or experimental parameters, never measured results.

## Recommended project and rationale

Build **RelayGuard**, an open-source local capability broker that executes bounded deterministic transformations of synthetic device observations. Native Android and Flutter applications submit observations, receive normalized results, cancel work and recover from service disappearance. Linux desktop provides a second transport and operating-system implementation of the same public contract. The practical product analogy is a device middleware SDK shared by several application teams; no employer implementation or proprietary business logic is needed.

The hiring value is in explaining—and demonstrating—what happens between processes: authorization, bounded memory, lifecycle, concurrency, ambiguous completion, native crashes and incompatible clients. A beautiful dashboard is secondary. The broker exposes a deliberately small operation, `normalizeObservation`, whose workload can vary without requiring hardware sensors, cloud services or personal data. CPU-heavy normalization is synthetic and must be labelled accordingly.

Two rejected alternatives:

1. **Cross-platform component gallery:** feasible and visually attractive, but UI reuse does not expose meaningful service lifetime, contention or recovery behavior. It offers weak evidence for system-software architecture.
2. **AOSP system-service fork with a new HAL:** deeper platform integration, but image builds, privileged deployment and device dependencies consume the available budget. A rushed emulator-only fork risks superficial breadth. Keep an AOSP integration RFC as a future assessment, not a claimed implementation.

## Architecture and real OS boundary

```text
Kotlin/Compose client ─ Kotlin SDK ─ AIDL/Binder ─ Android broker process
Flutter Android ─ platform plugin ─ Kotlin SDK ─┘       │ JNI
                                                portable C++ core
Flutter Linux ─ local socket SDK ─ Unix socket ─ Linux broker process
                                                       │
                                                same C++ core
```

Android broker is a separate APK with a bound service in its own process, accessible only to demo clients signed with the development signing identity through a signature permission. Install separate client APKs to demonstrate real application boundaries. Scope broker availability to actively bound clients; do not promise indefinite background execution. Android documents the bound-service lifecycle [S2]. AIDL defines the process contract and requires deliberate threading design [S1]. A native C++ core reached through JNI gives a real native crash boundary, while ordinary service implementation remains Kotlin for feasibility.

The Linux adapter runs the same core in a separate executable, using a Unix-domain stream socket in a user-owned directory, permission checks and peer-credential validation. Explicit length framing handles fragmented reads. Unix sockets provide local IPC and credential mechanisms [S6]. Linux and Android have different identity/lifecycle semantics; a portability matrix records these differences rather than hiding them behind an identical class name.

The portable core owns validation, deterministic transformation and result encoding. Each OS adapter owns identity, transport and lifecycle. One protocol specification defines semantic errors and compatibility; platform transport encodings may differ. Flutter's plugin delegates off its UI thread and respects platform-channel threading rules [S5]. Shared-core code is user-space system software; this is not kernel implementation, an Android framework patch or proof of iOS expertise.

Contract proposal: `hello`, `openSession`, `submit`, `poll`, `cancel`, `closeSession`. Each request carries protocol major/minor, session token, request ID, relative timeout budget and payload. Replies include broker epoch and an explicit state: accepted, running, completed, rejected, cancelled or unknown. Major mismatch rejects before work; minor additions are optional and capability-negotiated. Version checks do not repair incompatible AIDL layouts: preserve existing methods and maintain old compiled client fixtures.

Use synchronous, short admission calls on SDK workers and asynchronous execution; bounded polling avoids callback complexity in the first slice. Binder client tokens permit death observation and cleanup [S3]. Initial proposed limits: 4 KiB payload, eight outstanding requests per session, 32 sessions, 64 queued requests globally, two core workers, 256 cached results globally and 60-second retention. Every cache and queue has a byte ceiling as well as an entry ceiling. Return `RESOURCE_EXHAUSTED` before allocating work past a limit. Never hold a scheduler lock while making an external call.

No durable execution in v1. Accepted work can disappear with broker death; callers see an epoch change and `UNKNOWN`, never fabricated success. Request-ID deduplication applies only within the same session and broker epoch, with an explicit retention limit. Automatic retries are allowed only for this operation's documented idempotent semantics. Cancellation is cooperative and does not imply rollback. Binder transport failure can leave completion ambiguous [S4]; the API makes that ambiguity visible.

## Exact minimum vertical slice

One broker APK, one Kotlin client APK and one Flutter Android client APK. Both clients bind to the same broker, negotiate a version, submit 1 KiB synthetic observations and display deterministic output plus epoch. A test-only JNI abort terminates the broker process after a selected admission checkpoint. Both client UIs remain responsive, surface disconnection, rebind while foregrounded, observe a new epoch and resubmit explicitly. A client killed mid-session releases broker resources through death handling. An incompatible major version is rejected without core execution. Linux is outside the 40-hour slice.

Acceptance requires recordings plus raw assertions: distinct PIDs, process-death evidence, unchanged client PIDs, correct result vectors, bounded live-session counts after death, and no success response for a request whose result is unknown. Fault hooks exist only in a test build variant; release artifacts must exclude them. A release-build inspection is part of the gate.

## Requirement evidence map

| ID | Proposed evidence and acceptance | Limit |
|---|---|---|
| R01 | Architecture RFC, ADRs and ordered roadmap; decisions trace to measured experiments. | One public case study, not organization-wide authority. |
| R02 | Kotlin and Flutter clients execute identical golden vectors through the broker. | Two ecosystems, no React Native claim. |
| R03 | Physical Android phone and Linux PC pass shared conformance suite. | Linux is a PC platform; no Windows/iOS support claim. |
| R04 | Transport/core latency distributions and a trace-supported optimization comparison. | No universal performance ranking. |
| R05 | Queue/cache byte accounting, PSS/RSS and connection-churn results. | Allocator high-water memory may remain resident. |
| R06 | Separate app cold start, broker bind/handshake and first useful result measurements. | Warm and cold measurements are not interchangeable. |
| R07 | Native crash, client death, cancellation and stalled-worker fault suite. | Fault campaign is not a production uptime history. |
| R08 | Offered-load sweeps, saturation curve and fairness checks across sessions. | Local IPC scale only; not distributed scale. |
| R09 | Installable release candidate, versioned SDK and upgrade guide. | Demonstrates delivery discipline, not commercial deployment. |
| R10 | Reviewed protocol, lifecycle and resource-ownership specifications. | Solo review is explicitly labelled self-review. |
| R11 | CI contract, sanitizer, lint, release-build and dependency checks. | Passing CI does not establish safety certification. |
| R12 | Dated technology comparison: Binder/Messenger, socket transports, JNI/FFI and platform restrictions. | Roadmap assessment is not platform implementation. |
| R13 | Link only to separately authorized public qualification evidence. | Repository cannot establish a degree or equivalent career history. |
| R14 | Android service lifecycle, Binder identity/threading and native core integration demonstrated. | No privileged framework or kernel modification. |
| R15 | Authored Flutter plugin, lifecycle recovery and UI-thread responsiveness checks. | A thin wrapper alone is insufficient; exercise failures end to end. |
| R16 | Boundary diagram and trace walkthrough explain scheduling, IPC, memory ownership and process death. | User-space expertise does not imply kernel authorship. |
| R17 | Reproduce and fix at least one measured contention/recovery defect with retained failing evidence. | Do not invent a defect or call injected faults historical incidents. |
| R18 | ADR rationale, issue templates and concise integration guide; independent review if someone actually participates. | Documentation is not evidence of cross-team delivery. |
| R19 | Android implementation and Linux comparison; optional Fuchsia/ChromeOS assessment note. | No additional OS expertise inferred from reading docs. |
| R20 | Public extension proposal and integration contract inviting external feedback. | Only actual third-party participation counts as partner work. |
| R21 | Separately verified, consented public professional history if available. | No code artifact proves years of European work. |
| R22 | Contributor guidance and asynchronous design-review template; record actual contributions if received. | A solo repository cannot prove international collaboration. |

## Measurable benchmark and fault plan

Use three layers: deterministic core tests with seeded inputs; scheduler model tests with virtual time; real OS integration tests using separate processes. Virtual time verifies retry/cancellation state machines, but does not establish real scheduler timing. Test instrumentation provides explicit checkpoints, such as after admission and before result publication, so an external controller triggers faults without sleep-based guessing. Save seed, command, commit, build flags, runtime versions, device model, OS build, timestamps and assertion results with every run.

| Fault or pressure | Injection | Required observable result |
|---|---|---|
| Native boundary crash | Test JNI `abort` after selected checkpoint | Broker dies; clients survive; pending requests become unknown; new epoch on reconnect. |
| Client death | Kill only one client PID while a second remains bound | Dead session work cancelled where possible; handles/cache entries reclaimed; surviving client progresses. |
| Broker death during handshake | Kill after bind, before hello reply | One bounded reconnect loop; no duplicate active sessions. |
| Slow/stalled core | Cooperative delay and separately a non-cooperative worker hang | Deadline reported; queue bounded; hang consumes a worker visibly; watchdog requests process restart. |
| Overload | Fixed-rate arrivals above observed capacity | Explicit rejection, bounded retained bytes, no silent dropping of accepted work. |
| Protocol skew | Previously built v1 client with v2 broker, and reverse | Compatible subset passes; incompatible major rejects; no parser crash. |
| Malformed payload | Size, nesting, invalid enums and truncated socket frames | Bounded validation failure before core execution; stream resynchronization or clean close. |
| Late completion/cancel race | Deterministic competing checkpoints | Exactly one terminal state in an epoch; cancellation cannot overwrite published completion. |
| Slow/dead socket peer | Stop reads or close mid-frame | Write/read deadline releases connection; unrelated clients progress. |

A stalled in-process native worker cannot safely be killed as an ordinary thread. Restarting the entire broker is the honest containment action for the initial architecture; the 240-hour extension can assess separate worker processes if traces justify it. Supervisor crash-loop prevention uses capped exponential backoff, a retry budget and a terminal unavailable state requiring explicit retry. Test with deterministic jitter seeds; production randomness is not the evidence oracle.

Benchmark 1/2/8/32 concurrent sessions with 64 B/1 KiB/4 KiB payloads and cheap/expensive core work. Distinguish closed-loop concurrency tests from fixed-arrival-rate tests that reveal overload. Record offered, admitted, completed, rejected and timed-out rates separately, plus p50/p95/p99 latency and queue wait. Do not report only successful-request latency or equate throughput with goodput. Include scheduled-arrival delay to expose coordinated omission.

Proposed acceptance on named reference hardware: zero correctness violations in 100 repetitions per deterministic fault; all queues remain inside configured bounds; 1,000 connect/disconnect cycles leave zero live session handles; after warm-up, no persistent memory growth trend across repeated cycles. Initial recovery target is p95 under two seconds while a foreground client can legally rebind, with explicit unavailable fallback within ten seconds. Report failures rather than loosening the target silently.

For performance, first establish baseline variance. Consider provisional budgets of p95 below 10 ms for warm 1 KiB cheap requests at two clients, broker handshake p95 below 300 ms, and no more than 10% regression on a stable dedicated runner. These are hypotheses subject to a recorded calibration ADR, not promises. Record at least 30 cold-start samples and ten independent steady-state runs; retain raw samples and uncertainty. Android Macrobenchmark supplies startup results and traces [S7]; broker-specific milestones need custom timestamps. Compare identical cores in-process and over IPC, without calling the in-process mode an equivalent isolation alternative.

## Milestones, dependencies and budget

| Priority / milestone | Depends on | Person-hours | Exit evidence |
|---|---|---:|---|
| P0 Contract, ownership and fault model | None | 6–10 | RFC, golden vectors, resource limits, state transitions. |
| P0 Android broker + Kotlin + Flutter slice | Contract | 18–28 | Three APKs, separate PIDs, first-result path. |
| P0 Crash/rebind and basic evidence | Android slice | 10–16 | Native-crash and client-death assertions, short demo. |
| P1 Admission, compatibility and fault suite | P0 | 20–30 | Bounded overload, old binaries, deterministic races. |
| P1 Linux adapter and Flutter desktop | Contract and stable core | 20–30 | Shared vectors on physical PC. |
| P1 Measurement and release documentation | Working transports | 18–26 | Raw results, scripts, trace analysis, tagged candidate. |
| P2 Broader fault/performance campaign | P1 | 28–44 | Multi-device repetition, sanitizer/fuzz findings. |
| P2 Profiling-driven optimization and hardening | Campaign | 28–42 | Before/after evidence and regression test. |
| P2 External review preparation and assessment | Release candidate | 12–20 | Reviewable RFCs and addressed real feedback if available. |

**40 hours:** timebox the lower-end P0 slice, with simple core and minimal UI. It proves two clients crossing Android IPC and one native recovery path. It does **not** prove PC compatibility, meaningful scale, full fault coverage, sustained memory stability or production readiness. If setup overruns, retain the boundary and cut polish.

**120 hours:** target P0 plus P1 near their lower/middle estimates; prioritize real Linux conformance and evidence over extra features. It can establish repeatable two-OS behavior and bounded-load results. It does **not** prove all hardware families, privileged frameworks, long-duration reliability, Windows/iOS support or commercial operation. Report any deferred P1 item explicitly.

**240 hours:** complete the estimated 160–246-hour program with contingency managed through scope cuts. Add multi-device runs, sanitizer/fuzz campaigns, measured optimization and optional worker-isolation investigation. It still does **not** prove kernel development, external collaboration absent actual contributors, degree/career claims or commercial shipping. Do not fill surplus time with unverified platform logos.

## Open-source packaging and five-minute hiring demo

Use an Apache-2.0 monorepo with `protocol`, `core`, `android`, `linux`, `flutter`, `tests`, `benchmarks` and `docs`. Include LICENSE/NOTICE, contributor guide, security reporting instructions, supported matrix, reproducible build prerequisites, ADRs, version policy and a requirement-to-evidence index. Pin toolchain versions when implementation begins. Release signed checksums, generated SDK artifacts and synthetic fixtures; omit local paths, private resumes, personal contacts and credentials. CI gates portable checks on every change; physical-device results are separately identified, with no emulator throughput masquerading as phone measurements. Publishing occurs only through the authorized parent workflow.

Demo: **0:00–0:45** explain the process diagram and limits; **0:45–1:30** run identical Kotlin/Flutter jobs; **1:30–2:30** abort the native broker and show surviving clients, epoch change and explicit unknown state; **2:30–3:30** overload one client and show bounded queue plus healthy second client; **3:30–4:15** run the same vector on Linux and reject incompatible protocol; **4:15–5:00** open the raw report, one trace and requirement map. At 40 hours label Linux and overload as planned and use the time for client-death evidence instead.

## Risks, honest gaps and primary sources

Main risks are JNI/JVM ownership mistakes, Flutter integration learning, unequal platform semantics, flaky process orchestration and spending too much effort on a generic protocol. Mitigate with one operation, polling first, explicit ownership, stored old-client binaries and deterministic checkpoints. A finite crash campaign cannot establish an availability percentage. Signature-only Android clients limit third-party distribution; that is a documented prototype trust model. Do not imply an unnamed employer endorses the project.

Primary sources verified 30 October 2026:

- [S1 Android AIDL and threading](https://developer.android.com/develop/background-work/services/aidl).
- [S2 Android bound-service lifecycle](https://developer.android.com/develop/background-work/services/bound-services).
- [S3 AOSP Binder death notifications](https://source.android.com/docs/core/architecture/aidl/aidl-backends).
- [S4 Binder transaction limits and partial-failure semantics](https://developer.android.com/reference/android/os/TransactionTooLargeException).
- [S5 Flutter platform channels and threading](https://docs.flutter.dev/platform-integration/platform-channels).
- [S6 Linux Unix-domain sockets](https://man7.org/linux/man-pages/man7/unix.7.html).
- [S7 Android Macrobenchmark](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview).
- [Role source: Sparagus Principal Architect](https://nl.linkedin.com/jobs/view/principal-architect-%E2%80%94-os-middleware-system-frameworks-at-sparagus-4476561682), requirement paraphrases supplied by the verified brief.

## Scored recommendation

| Criterion | Score | Reason |
|---|---:|---|
| Relevance | 28/30 | Direct middleware, Android, Flutter, PC and performance evidence. |
| Authentic system depth | 21/25 | Real IPC, identity, native boundary and lifecycle; no privileged OS internals. |
| Evidence auditability | 19/20 | Reproducible faults, raw distributions and explicit ambiguous states. |
| Feasibility | 11/15 | Strong Android base; Linux plus Flutter plus native testing requires disciplined cuts. |
| Reviewer clarity | 9/10 | One service and a visible failure/recovery narrative. |
| **Total** | **88/100** | Recommend the 120-hour target, with 40-hour boundary proof first. |
