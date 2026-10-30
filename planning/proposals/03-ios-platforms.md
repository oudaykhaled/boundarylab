> Archived independent proposal, 2026-10-30. This is research input, not the selected scope or completed work. The [synthesis](../DECISION.md) and [roadmap](../../docs/ROADMAP.md) define the final plan. No product implementation or benchmark result is asserted.

# RelayKit: capability-aware durable work middleware for phones and PCs

Independent proposal 03 — iOS and cross-platform compatibility lens. Planning date: 2026-10-30. All milestones, budgets, and performance targets below are proposed, not implemented or measured. The target role is Sparagus's Principal Architect — OS Middleware & System Frameworks; its client is unnamed.

## Recommended project and why

Build **RelayKit**, an open-source application framework that accepts a durable local operation, exposes its lifecycle consistently to native Android, native iOS, and Flutter applications, and delegates execution opportunities to each operating system. Demonstrate a synthetic inspection-record outbox: create a record offline, survive process loss, retry an interrupted upload, and show one server-side result. Extend the same contract to a macOS PC application with an isolated XPC worker.

The architectural claim is deliberately precise: cross-platform compatibility means consistent observable data and failure semantics, not identical privileges, timers, processes, or background availability. The application displays both operation state and platform capability. An iPhone may say “persisted; next execution opportunity depends on the OS”; the desktop may show an on-demand helper process. The difference is part of the successful demonstration.

This is more credible than another complete consumer app. It makes scheduling, durable state, API evolution, resource ownership, cancellation, IPC, and recovery reviewable. It also connects the candidate's stated Android experience to an explicit new iOS/desktop learning boundary. It cannot manufacture employment history, partner relationships, or commercial deployment.

Two alternatives rejected:

1. **A universal mobile daemon or plugin host:** unrealistic for ordinary iOS applications, and invites misleading claims about arbitrary code loading and uninterrupted execution. Its apparent parity would hide the most interesting compatibility constraints.
2. **A custom kernel or full OS distribution:** deeper kernel work but poor feasibility within 240 hours, weak immediate application-framework evidence, and a distraction from a credible Android/iOS/Flutter contract. An emulator-only kernel exercise would not demonstrate phone/PC product integration.

## Architecture and real OS boundary

Use a portable Rust state-machine library with a narrow C ABI, a SQLite journal, thin Swift/Kotlin adapters, and a federated Flutter plugin. A generated binding approach may replace hand-written ABI wrappers after a time-boxed spike; the ABI must have explicit ownership, opaque handles, length limits, error codes, and cancellation rules. Platform adapters own HTTP, lifecycle integration, filesystem locations, and scheduler registration. Rust owns validated operation transitions, bounded queue selection, retry metadata, and journal transactions. Keep network credentials and URLs out of core logs.

The core exposes `submit`, `inspect`, `cancel`, `claimBatch`, `recordOutcome`, and `capabilities`. Submission returns only after persistence commits. States include queued, executing, retryable, succeeded, permanentlyFailed, and cancelled; a separate eligibility field captures foreground-only, OS-deferred, and currently unavailable execution. The contract promises durable intent under documented storage assumptions, at-least-once execution attempts, and idempotent test-server effects. It never promises exactly-once delivery. Expired claims recover after restart; cancellation racing a completed upload may yield “too late.”

The fixture server implements an idempotency key and records response-loss scenarios. It is a test dependency, not the product's main architecture. No private finance or employer SDK code is needed.

**iOS:** ship as an embedded framework inside the host application's sandbox. Third-party access beyond that container requires OS-provided services; this is not a system-wide broker. [Apple platform security](https://support.apple.com/en-gb/guide/security/sec15bfe098e/web) describes that security boundary. Ordinary deferred work uses BackgroundTasks, with foreground resume as a dependable opportunity when the user opens the app. OS scheduling is discretionary and expiration must leave recoverable state. No periodic deadline or always-running promise is offered. Apple's [background strategy guide](https://developer.apple.com/documentation/backgroundtasks/choosing-background-strategies-for-your-app) documents OS-selected scheduling and limited execution windows. File-transfer specialization using background URLSession is an optional later adapter, not silently assumed for arbitrary computation. Newer background APIs require a separate availability and policy assessment before adoption.

**Android:** Kotlin integrates the core with lifecycle events and WorkManager. Android recommends WorkManager for persistent work; its scheduling model still does not confer an exact execution deadline. See [Android task scheduling](https://developer.android.com/develop/background-work/background-tasks/persistent). The first prototype runs within the application process. Do not call this Binder service implementation or claim system-server modification.

**macOS:** an application-bundled XPC service owns the journal and executes work on demand. The UI never opens its database concurrently. XPC supplies a real process boundary and launchd-managed helper lifecycle; implement disconnect/reconnect and input validation. [Apple XPC documentation](https://developer.apple.com/documentation/xpc) describes these mechanisms. This desktop-only arrangement is not transplanted onto iOS. App Sandbox is enabled and entitlements documented, following [Apple's sandbox guide](https://developer.apple.com/documentation/security/protecting-user-data-with-app-sandbox).

**Flutter:** a small federated plugin delegates to native adapters through typed channels. UI interoperability is not runtime or binary interoperability. [Flutter channels](https://docs.flutter.dev/platform-integration/platform-channels) define this native integration route; avoid doing storage or network work on the UI thread. Native Swift and Kotlin clients remain first-class independent consumers.

### Support and compatibility policy

| Tier | Targets | Release meaning |
|---|---|---|
| A at 120h candidate | Android physical phone; iOS physical phone; macOS PC foreground host | Named hardware and exact OS/toolchain versions; contract tests and real lifecycle evidence. Promotion requires passing evidence, not elapsed time. |
| A extension at 240h | macOS XPC mode; Android/iOS Flutter clients | Recovery and boundary tests pass; simulator results remain separately labelled. |
| B exploratory | Linux core CLI; Windows cross-compile or runner if available | Core/API compatibility only; no mobile scheduler or desktop shipping claim. |
| C documented | ChromeOS, Fuchsia, web, other architectures | Not implemented or supported. A written port assessment is not platform exposure. |

Choose minimum OS versions only after checking current pinned SDK/framework support and the candidate's actual devices. Test oldest declared supported version plus a current version where hardware exists. If only one version is available, declare that limitation. Flutter itself supports several target platforms, but that does not imply this plugin does: [Flutter platform integration](https://docs.flutter.dev/platform-integration).

Compatibility has four independent axes: API/schema, behavior, lifecycle, and packaging. Capability snapshots include adapter version, contract version, available scheduling modes, and restrictions. Unsupported operations return typed errors, never fabricated success. An older client with a newer journal needs an explicit supported migration path or a safe refusal. No implicit shared database across apps, devices, or OS accounts.

## Exact minimum vertical slice

On one real Android phone and one real iPhone, submit a 1 KiB synthetic record with a unique idempotency key, receive persisted acknowledgement, interrupt the process, reopen the app, reconnect to the fixture server, and obtain one committed server record. Show native Kotlin and Swift screens plus the same core state-transition test vectors. The iPhone test demonstrates recovery after relaunch; it does not claim the OS woke the app on a deadline. A macOS command-line host exercises the same journal contract, establishing a limited PC baseline.

Include a capability screen, ten deterministic fault scenarios, one raw benchmark bundle, and one architecture decision record rejecting uniform background scheduling. Flutter integration and XPC are the next funded increments. Until those land, R15 and process-isolation evidence remain open rather than hidden behind a screenshot.

## Requirement evidence and acceptance

| ID | Proposed evidence and acceptance | Limit |
|---|---|---|
| R01 | Architecture, prioritized roadmap, two versioned ADRs; reviewer can trace each milestone to a tradeoff. | A portfolio roadmap is not company architecture ownership. |
| R02 | Native Kotlin, native Swift, and Flutter consume one tested contract. | Distinct API consumers, not execution of arbitrary ecosystems. |
| R03 | Physical phone results plus named macOS PC run and capability differences. | No Windows/Linux GUI parity until tested. |
| R04 | Baseline and optimized run with raw latency/throughput distributions. | No performance claim before measurement. |
| R05 | Queue-scale memory profile; bounded batches and payload limits demonstrated. | Host RSS includes framework overhead. |
| R06 | Separate app first-frame, library init, journal ready, and first acknowledgement timings. | Device caches and debug builds distort comparisons. |
| R07 | Crash/restart, response-loss, storage failure, and cancellation test ledger passes. | Process termination is not power-loss testing. |
| R08 | 100/10k/100k-entry queue tests with explicit capacity rejection. | Local workload scalability, not millions of users. |
| R09 | Release candidate, executable acceptance scenario, migration guide, support policy. | Commercial relevance; no invented shipped product. |
| R10 | ADRs for state model, ABI ownership, scheduling, and schema evolution. | Standards are project-scoped. |
| R11 | Contribution guide, automated contract checks, formatting and static checks, release checklist. | Coverage percentage alone is insufficient. |
| R12 | Dated technology assessment comparing OS API capabilities and revisit triggers. | Study is not hands-on experience. |
| R13 | Private interview mapping to self-reported BSc/equivalent practice; user verifies separately. | Project cannot prove education. |
| R14 | Android framework integration plus Swift lifecycle adapter, journal and IPC work. | User-space system software; no kernel authorship. |
| R15 | Working federated Flutter plugin and fault demo on both phones. | Open at minimum-slice stage. |
| R16 | Annotated thread/process/container diagrams matched to traces. | Does not prove OS implementation expertise across all kernels. |
| R17 | One documented failure, competing diagnosis, minimal fix, repeated measurement. | Never prewrite a fictional incident. |
| R18 | Design RFC, reviewer guide and response log if real review occurs. | Solo documentation is communication evidence, not cross-team work. |
| R19 | Android/iOS/macOS evidence with unsupported-platform matrix. | Fuchsia/ChromeOS are optional, not claimed. |
| R20 | Reproducible upstream issue or patch only if an actual defect merits it. | No manufactured partner collaboration or unsolicited outreach. |
| R21 | Candidate's self-reported Netherlands history discussed privately if authorized. | Repository cannot prove sustained European employment. |
| R22 | Accessible English docs and contribution workflow; real contributors credited with consent. | International collaboration remains unproven without real participation. |

## Benchmarking and fault plan

Pin commit, release profile, toolchain, OS build, hardware, storage settings, dataset and temperature/power conditions. Run 30 cold-launch and 30 warm-launch trials per supported physical device; retain all samples and define “cold” operationally. Report median/p95 and spread, not cross-device league tables. Measure core initialization, acknowledgement latency, queue-drain throughput, peak host memory, and Flutter first-frame overhead separately. Compare identical native baselines with and without the middleware; instrument bridge serialization separately from journal I/O.

Provisional engineering budgets: p95 1 KiB acknowledgement under 50 ms, middleware initialization under 100 ms, and additional steady-state native memory under 20 MiB for 10k queued metadata entries on declared devices. These are negotiation targets, not expected facts. Run 100/10k/100k queue sizes, bounded batches of 32, payload references rather than unlimited in-memory blobs, and a fixed 100 MiB storage quota. Record battery/thermal observations without claiming battery-life improvement from a short experiment.

Faults: terminate before/after commit; lose upload response; duplicate retry; deny connectivity; exhaust quota; corrupt a disposable journal copy; expire a background task; revoke relevant permissions; crash the XPC helper; migrate an older schema. Test logical state invariants deterministically and repeat selected native lifecycle runs. Success means no silently lost acknowledged intent, no duplicate fixture-server effect, bounded resource growth, and an explainable terminal or recoverable state. SQLite guarantees depend on its journal and storage assumptions; document chosen settings instead of claiming immunity to all hardware failures. See [SQLite atomic commit](https://www2.sqlite.org/atomiccommit.html).

## Prioritized milestones and budgets

Person-hour ranges assume one experienced Android developer learning Swift/Rust integration; hardware access and signing setup are prerequisites. Select capped scope, because maximum estimates cannot all fit their nominal budget.

| Milestone | Dependency | Hours |
|---|---|---:|
| M0 contract, device inventory, ABI/build spike, ADR | None | 8–12 |
| M1 journal state machine, CLI, fixture server, deterministic faults | M0 | 20–30 |
| M2 Android and iOS thin native adapters with relaunch recovery | M1 | 28–40 |
| M3 Flutter plugin and native parity scenarios | M2 | 18–28 |
| M4 measured benchmark bundle and macOS foreground harness | M2 | 14–22 |
| M5 XPC isolation, security review, migration and extended fault tests | M4 | 35–55 |
| M6 documentation, release packaging and sustained device runs | M3–M5 | 25–40 |

**40h:** prioritize M0/M1 and a single Android demonstration; Swift build spike only if spare time. Does not prove iOS execution, Flutter, cross-device parity, XPC, broad stability, or any biographical requirement. Publish an explicitly incomplete prototype plan rather than inflate scope.

**120h:** target M0–M4 plus lean documentation, approximately 88–132h; cap features by deferring richer UI and optional optimizations. Native iOS/Android, Flutter and macOS foreground results must each earn status independently. Does not prove desktop isolation, long-term reliability, public package quality, Windows/Fuchsia/ChromeOS support, or commercial/partner history.

**240h:** fund M5/M6 and roughly 20–35h contingency, giving a plausible 168–262h envelope. Trim exploratory ports first. Target a reviewable release candidate, XPC fault recovery and repeated physical-device evidence. Still does not prove kernel implementation, App Store acceptance, indefinite background execution, production scale, degree, employment geography or international collaboration.

## Open-source packaging and five-minute demo

Propose a single GitHub repository with core, adapters, Flutter plugin, examples, fixtures, benchmarks, ADRs, compatibility matrix and evidence manifest. Use Apache-2.0 subject to dependency-license review, a security reporting policy, contribution guide, CI matrix, pinned dependencies, and release tags. Every evidence row links a commit, test command, raw output and device metadata. Do not publish provisioning credentials, CV contacts, employer code or absolute local paths. Publishing occurs only within the user's authorization; no package-store or app-store release is assumed.

Demo: 0:00–0:45 show boundary diagram and capability contrast; 0:45–1:45 persist offline work on both phones; 1:45–2:45 terminate/relaunch and lose a response while proving server idempotency; 2:45–3:30 show Flutter consuming the same states; 3:30–4:15 crash/recover the macOS helper when implemented; 4:15–5:00 open raw performance evidence and explain one regression and one unsupported capability. Label prerecorded OS-deferred execution separately from live deterministic tests.

## Major risks, gaps and scored recommendation

Swift/Rust packaging and physical-device signing may consume the first sprint; make the build spike the first gate. Flutter plus three native hosts can create packaging work without architectural depth; favor shared fault vectors and small UIs. Deferred iOS scheduling cannot be made deterministic by test mocks; distinguish adapter logic tests from actual scheduler observation. A broad platform checklist must never replace a reliable two-phone slice. The largest remaining hiring gaps are commercial adoption and real collaboration; list them plainly.

Primary sources are linked beside the relevant claims above. Source review occurred on the planning date; exact API availability must be rechecked at implementation freeze.

Scored recommendation: relevance **28/30**, authentic system depth **20/25**, evidence auditability **19/20**, feasibility **11/15**, reviewer clarity **9/10**; **87/100**. This is a planning judgment, not a measured outcome. Recommend the 120h milestone as the first review point and authorize a 240h extension only if the portable contract and two physical-phone recovery cases are already convincing.
