> Archived independent proposal, 2026-10-30. This is research input, not the selected scope or completed work. The [synthesis](../DECISION.md) and [roadmap](../../docs/ROADMAP.md) define the final plan. No product implementation or benchmark result is asserted.

# CapabilityBridge: an auditable middleware performance laboratory

Independent proposal 05 — performance, startup, memory and experimental validity. Planning only, 30 October 2026. All numerical targets below are proposed acceptance criteria, not measurements or promises.

## Recommended project and why

Build **CapabilityBridge**, a small user-space middleware broker for bounded local document processing, consumed by native Android and Flutter applications and, in the extended scope, a Linux desktop client. Its demonstration operation computes a deterministic digest over a synthetic document in bounded chunks, reporting progress and cancellation. The product story is an offline document utility shared across application ecosystems; the architectural subject is the contract, lifecycle, resource ownership, compatibility and cost of crossing process boundaries.

The distinguishing artifact is an executable experiment pack answering: what does isolation cost, when does batching help, what delays first useful work, and does retained memory remain bounded after cancellation and service death? An honest negative result is valuable: if an optimization does not help, publish the evidence and retain the simpler design. This directly serves the position's performance, memory and startup requirements without claiming a toy application is an operating system.

Use Android first because it aligns with the supplied, self-reported experience and allows substantial system-framework work inside a realistic budget. Kotlin service and native client; Flutter plugin for the second ecosystem. A Linux broker is a later portability test, not a claim that Android lifecycle semantics exist unchanged on desktop. No private employer artifacts enter the repository.

Two alternatives rejected:

1. **Full custom AOSP framework service plus ROM:** greater privileged-system depth but device flashing, builds and integration dominate the budget; a credible two-ecosystem product and rigorous measurements become unlikely. Keep an AOSP integration RFC as an explicitly unimplemented extension.
2. **A polished Flutter productivity app with performance charts:** accessible demo, but framework startup and widget work swamp the middleware question; it offers little evidence of IPC ownership, fault containment or application-framework architecture.

## Architecture and real OS boundary

The native and Flutter Android apps bind to a separate broker APK using a versioned AIDL contract. Different applications and concurrent clients justify the added AIDL complexity. Incoming Binder calls validate permissions, caller identity, protocol version and payload bounds, then enqueue bounded work; computation does not occupy the UI thread or indefinitely block Binder ingress. Android documents both cross-process AIDL marshalling and thread-safety obligations [1].

The API is deliberately narrow: `capabilities`, `submit`, `cancel`, and completion/progress callbacks. Requests carry a request ID, protocol version, deadline, digest algorithm identifier and bounded payload chunk. Results carry explicit success, busy, unsupported, cancelled or failed status. The first slice permits at most 16 KiB per chunk, eight active requests and a fixed queue capacity. These are design choices to validate, not platform limits. Per-caller quotas prevent a noisy client monopolizing the broker.

A Kotlin contract adapter translates the Flutter platform channel into the same Android client library; platform channels are a documented mechanism for invoking platform-specific code [2]. The native client remains essential for separating bridge overhead from broker overhead. Core algorithms and workload fixtures stay deterministic and simple. Introducing a Rust/C++ core before measurements justify it would add a language boundary and obscure attribution.

Linux extension: a user-session daemon and a Unix-domain-socket transport with equivalent wire semantics, size limits and cancellation behavior. A Dart desktop adapter and command-line reference client test compatibility. Android and Linux implementation duplication is accepted initially to investigate semantic portability; share schemas, vectors and conformance tests, not unjustified platform abstractions. Linux peer credentials and Android caller UID/signature permission require separate threat-model decisions.

Actual OS evidence includes processes, Binder scheduling/dispatch, service binding and death, memory accounting, permission enforcement and desktop socket lifecycle. This is user-space system software using existing OS facilities. It does **not** implement a scheduler, kernel driver, Binder driver, HAL or privileged Android framework service.

## Exact minimum vertical slice

On one physical Android phone, install broker, native client and Flutter client signed with the test key. Both clients bind, negotiate protocol v1 and digest an identical 1 MiB generated fixture through bounded chunks; compare against a checked-in known digest. Display progress, cancel a request and show terminal status. Stop the broker during a request; surface a typed failure, rebind on explicit retry and complete a new request without a duplicate terminal callback. Record first useful result timing and a trace with client submit, service admission, execution and callback spans.

Ship a command that builds release-like benchmark artifacts, installs the trio, runs the correctness scenario and produces a machine-readable run manifest. The initial baseline is a direct in-process invocation of the same digest and chunking logic in the native client, followed by equivalent calls through the broker. This slice establishes measurement plumbing before claiming optimization or desktop support.

## Requirement-to-evidence and limits

| ID | Proposed evidence and acceptance | Explicit limit |
|---|---|---|
| R01 | Context/container diagrams, ownership map, three-stage roadmap and two decision records; reviewer can trace API decisions to requirements. | One person's proposal is not organizational architecture authority. |
| R02 | Native Android and Flutter clients pass identical golden vectors and lifecycle cases. | Two ecosystems, not universal compatibility. |
| R03 | Extended release passes the shared suite on Android phone and Linux PC; publish tested OS/device matrix. | Android emulator on PC alone does not prove desktop portability. |
| R04 | Reproducible comparative latency/throughput study with raw observations and confidence intervals. | No speed claim until measured. |
| R05 | Process and aggregate memory curves, bounded-queue tests, repeated cancellation soak. | Allocation samples do not equal total memory. |
| R06 | Separate shell launch, bind-ready and first useful result milestones for cold/warm states. | App launch is not device boot. |
| R07 | Death, disconnect, cancellation and overload tests terminate within defined deadlines. | Fault exercises cannot establish production availability. |
| R08 | Concurrency sweep with accepted/completed/rejected counts and saturation curve. | Single-device capacity, not fleet/cloud scale. |
| R09 | Tagged runnable vertical slice, release notes, support/upgrade story and cost budget. | Product relevance, not commercial shipment. |
| R10 | Versioning policy, architecture decisions, permission boundary and dependency rules. | Repository conventions, not adopted industry standards. |
| R11 | Lint, unit/contract/integration gates, dependency/license checks and review template. | Solo CI is not proof of leading a large team. |
| R12 | Dated comparison of AIDL/Messenger/in-process choices and Flutter bridge options, with reassessment triggers. | Technology assessment is time-bound. |
| R13 | Optional separate candidate statement about degree/equivalent practice. | Repository cannot prove education; no CV publication. |
| R14 | Android Binder service, lifecycle, concurrency and permission tests plus traces. | Demonstrates framework/system-service use, not kernel implementation. |
| R15 | Working Flutter plugin, errors, cancellation and Android integration tests. | A sample is not years of Flutter expertise. |
| R16 | Annotated sequence showing process boundary, scheduling, memory ownership and service death. | Diagrams require executable corroboration. |
| R17 | One measured bottleneck investigation, competing hypotheses, controlled experiment and recorded decision. | Do not fabricate a difficult incident or successful optimization. |
| R18 | Clear RFC, reviewer guide, change rationale and onboarding task. | Actual cross-team communication requires real participants. |
| R19 | Android first; Linux comparison later; document iOS constraints as research only. | No implied Fuchsia, ChromeOS or iOS implementation. |
| R20 | Prepare upstream-quality minimal reproducer if a real external issue emerges. | No fictional partner work, unsolicited outreach or promised upstream acceptance. |
| R21 | Private candidate context may support a separate experience discussion. | Project cannot manufacture European professional tenure. |
| R22 | Open contribution guide and asynchronous review protocol. | International collaboration needs actual attributable contributions. |

## Measurable benchmark and fault plan

### Questions, baselines and falsification

**H1:** batching callbacks reduces IPC cost under sustained work. Compare per-chunk versus batched progress for the same payload, digest, completion semantics and cancellation deadline. Reject the optimization if end-to-end latency does not improve or cancellation worsens beyond budget. **H2:** lazy broker initialization reduces first-frame time but may delay first useful work. Measure both; a shell-only win fails the intended user objective. **H3:** fixed queue and buffer limits prevent increasing retained memory under overload. Reject if memory continues growing after accepted work reaches its bound.

Baselines: native empty shell; native in-process workload; native broker workload; Flutter empty shell; Flutter broker workload. Compare native and Flutter middleware deltas within their own shells. Do not attribute the entire Flutter/native startup difference to the plugin. Use identical deterministic inputs: 1 KiB control requests, 1 MiB standard workload and 16 MiB stress workload, with recorded seeds and checksums. Report callback policy and transferred bytes. No contrived slow baseline and no unequal debug/release comparison.

### Environment and sampling

Choose one named primary physical phone after inventory, then a second physical device with different memory/SoC and one desktop only in the extended scope. Record build fingerprint, OS/security update, ABI, RAM, storage free space, battery/charging condition, thermal state, refresh rate, app versions, toolchain, commit and artifact hashes. Fix brightness and network state, disable incidental sync where possible, and stabilize temperature before each block. Do not conceal normal platform scheduling variability by silently rooting or changing governors. Emulators supply correctness feedback, not flagship performance evidence.

Use non-debuggable release-like targets and explicit compilation modes; Macrobenchmark provides startup experiments and compilation control [3]. Freeze one compilation condition per comparison. Report cold client/cold service, cold client/already-bound service where feasible, and warm client/service separately. Process-cold is not filesystem-cache-cold; reboot tests, if any, form another named condition. Label Baseline Profile treatment explicitly rather than mixing it into an IPC comparison.

Pilot ten paired trials to estimate variability and verify instrumentation; exclude pilot from confirmatory analysis. Then use three independently initialized sessions, each with 30 launches per treatment, randomized in short balanced blocks. Report per-session distributions and pooled medians/p90 with session-aware uncertainty. Ninety startup observations do not justify a stable p99 claim. For steady-state load, use three sessions with five 60-second windows at concurrency 1, 2, 4, 8 and 16, after a declared warmup; report distributions per window and avoid treating correlated requests as independent samples. Bootstrap at session/window level, record seed and publish the analysis script. Retry only invalid runs under predefined conditions, such as disconnection or documented thermal throttling; retain invalid-run records and reasons. Never delete slow valid observations.

### Metrics and proposed gates

Measure TTID, fully useful UI milestone, bind-ready time, submit-to-terminal latency, queue wait, service execution time, bridge time, throughput, CPU time, accepted/rejected counts, cancellation latency, RSS/PSS and peak/settled memory for **all** participating processes. Monotonic timestamps and correlation IDs support same-device attribution; do not subtract unsynchronized clocks across machines. Periodic memory accounting can perturb execution; run memory attribution separately from the main latency study. Native allocation profiling is sampled and has overhead, so use Perfetto heapprofd for diagnosis rather than equating it to PSS or uninstrumented latency [4].

Provisional engineering budgets, frozen before confirmatory measurements: middleware adds at most 100 ms median first-use delay versus its same-framework baseline; standard-load native IPC adds at most 10 ms p95 control-request latency; cancellation receives a terminal response within 250 ms p95; retained broker PSS after a 30-minute cycle settles within 10 MiB of its post-warmup starting point; no growing queue beyond configured bounds. Thresholds are hypotheses about usefulness, not job requirements. A failed budget triggers documented scope/architecture reassessment, not retrospective threshold editing. For claimed optimization, require at least 10% median improvement with a 95% interval excluding zero and no >5% regression in the paired protected metric; inconclusive results stay inconclusive.

Fault suite: kill service before admission, during compute and before callback; kill client; flood queue; send invalid version/oversized input; race cancellation with completion; disconnect desktop socket. For 100 seeded repetitions per fault, require no crash in surviving client, at most one terminal callback, no unauthorized operation, no orphaned work after its deadline, and successful new request after reconnect. A documented deadline resolves ambiguous in-flight work; exactly-once processing across process death is not promised. Add soak and low-memory observations at extended scope; do not simulate a real OS low-memory kill merely by throwing an exception.

Publish raw Macrobenchmark output, Perfetto traces, trace configuration, symbols/mappings, workload fixtures, SQL extraction queries, CSV observations, run manifest, exclusion log and analysis version. Keep large traces in versioned release assets with SHA-256 manifest; redact device identifiers and use synthetic data. Trace loss/overrun is an explicit validity field.

## Milestones, dependencies and budget

| Priority | Deliverable | Depends on | Person-hours |
|---|---|---|---:|
| P0 | Contract, hypotheses, threat model, measurement definitions | None | 6–10 |
| P1 | Native broker/client and death/cancel behavior | P0 | 16–24 |
| P2 | Flutter adapter and identical vectors | P1 | 10–16 |
| P3 | Benchmark harness, baselines and raw artifact pipeline | P1 | 16–24 |
| P4 | Controlled study, bounded-memory/fault evidence and investigation | P2/P3 | 20–30 |
| P5 | Linux daemon/client conformance | P0/P4 | 30–45 |
| P6 | Second-device replication, soak, packaging and review material | P4/P5 | 28–45 |

**40 hours:** prioritize P0/P1, a minimal Flutter path and smoke measurement. NOT proven: statistically defensible performance superiority, PC compatibility, broad device support, stability at scale, external collaboration.

**120 hours:** complete Android native/Flutter evidence, controlled single-phone study, fault suite, polished reproducibility package; attempt Linux only after Android acceptance. NOT proven: desktop implementation if deferred, fleet reliability, privileged framework expertise, commercial deployment or genuine partner work.

**240 hours:** target Android plus Linux, second-phone replication, 30-minute/longer soaks, compatibility evolution test and a documented real bottleneck investigation; reserve remaining hours for tooling/device friction and independent review if available. NOT proven: all-platform support, kernel development, production SLOs, commercial scale, credentials or career history. Hardware access and human reviewers are external dependencies; do not assume budget authorizes purchases.

## Open-source/GitHub packaging

Propose Apache-2.0 with dependency-license review, README that leads with measured status, architecture/contract/benchmark/fault folders, contribution and security guidance, tested-platform table, release manifest and requirement matrix. Separate `planned`, `implemented`, `measured`, `reviewed` and `released` evidence. CI runs deterministic correctness; a manually controlled physical-device lane produces performance evidence without treating shared hosted runners as calibrated hardware. Pin toolchains and retain baseline commits. Never include resumes, personal contact details, employer code or absolute local paths.

## Five-minute hiring demo

0:00–0:45: show architecture, real OS boundary and stated limitations. 0:45–1:45: native and Flutter submit identical work. 1:45–2:30: kill the broker, observe typed failure, retry. 2:30–3:30: open one trace and identify queue versus execution versus callback delay. 3:30–4:30: show distributions, baseline comparison, raw-manifest link and one falsified hypothesis if available. 4:30–5:00: demonstrate desktop only if implemented, then explain the next roadmap decision. Keep a prerecorded fallback clearly labeled; never substitute it for a claimed live result.

## Risks, honest gaps and recommendation

The largest risk is spending the budget on four runtimes rather than one strong architectural experiment. Native Android plus Flutter is the minimum; desktop waits for evidence quality. Binder lifecycle, callback races, memory instrumentation effects and thermal drift can invalidate superficial results. Narrow workloads limit generalization: hashing does not represent graphics, multimedia or every platform capability. The supplied experience may complement the repository privately, but the repository itself cannot verify education, years worked, partner relationships or commercial success.

Proposed score: relevance **28/30**, authentic system depth **20/25**, evidence auditability **20/20**, feasibility **12/15**, reviewer clarity **9/10**; total **89/100**. This is a planning judgment, not measured implementation quality. Recommend the 120-hour Android evidence-first release and a conditional 240-hour desktop extension.

## Primary sources

1. [Android AIDL](https://developer.android.com/develop/background-work/services/aidl), checked 2026-10-30: cross-process contract and threading semantics.
2. [Flutter platform channels](https://docs.flutter.dev/platform-integration/platform-channels), checked 2026-10-30: platform integration and threading guidance.
3. [Android Macrobenchmark](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview), checked 2026-10-30: realistic app benchmarking and compilation setup.
4. [Perfetto native heap profiling](https://perfetto.dev/docs/data-sources/native-heap-profiler), checked 2026-10-30: sampled allocation attribution and profiling constraints.
5. [Sparagus vacancy](https://nl.linkedin.com/jobs/view/principal-architect-%E2%80%94-os-middleware-system-frameworks-at-sparagus-4476561682), requirements supplied in the verified brief; client identity remains unknown.
