> Archived independent proposal, 2026-10-30. This is research input, not the selected scope or completed work. The [synthesis](../DECISION.md) and [roadmap](../../docs/ROADMAP.md) define the final plan. No product implementation or benchmark result is asserted.

# Capability Broker Lab: a hiring-panel evidence proposal

Independent proposal 07 · 30 October 2026 · Planning only

## Recommendation and hiring thesis

Build **Capability Broker Lab**, a small, versioned local job framework with an Android Binder service, native Android client, Flutter client, and Linux desktop implementation. Its one useful capability is computing a digest of a broker-owned fixture in cancellable chunks. The product analogue is a shared device capability used by several application ecosystems: document ingestion, asset verification, or an embedded device’s processing framework. It is deliberately small enough that concurrency, authorization, lifecycle, compatibility and performance remain visible.

A skeptical principal-architect panel should be able to establish three facts: the candidate understands the OS mechanisms beneath an application framework; can turn an architectural decision into working, measurable behavior; and can explain what the evidence does and does not establish. The strongest deliverable is a reproducible failure-and-recovery experiment with an intelligible decision trail. A large feature inventory would dilute this evidence.

The initial claim should be “Android and Linux user-space middleware and client framework,” never “a new OS,” “production proven,” or “kernel development.” The listing’s end client is unknown. Avoid designing to an inferred employer or promising a hiring outcome.

Two rejected alternatives:

1. **A polished multiplatform business application.** Easier to showcase, but screens and HTTP integration primarily establish application competence. They do little to resolve the likely panel doubt about genuine system-framework depth.
2. **A custom Android distribution with several new platform services.** More direct platform experience, but build infrastructure, privileged integration and hardware variation consume the available time. A superficial fork is less persuasive than a comprehensively understood user-space service. Keep one bounded AOSP integration experiment as a later, separately scoped option.

## Architecture and the real OS boundary

Android: independent native and Flutter application packages → client SDK → AIDL/Binder → broker APK in its own process → bounded work queue → chunked fixture digest engine. Flutter reaches the Kotlin SDK through a small plugin. Platform channels are the Flutter/native bridge; Binder is the actual cross-process boundary. AIDL is specifically relevant to cross-application IPC and its threading implications [S1, S2].

Linux: Flutter desktop client → Linux plugin → Unix-domain socket → user-session broker → the same behavioral contract, implemented by a small Linux-specific worker. Prefer familiar language tooling for this adapter after a short feasibility spike; do not force a shared native core merely to advertise more languages. A C++ broker and plugin are a plausible default. The common asset is the specification and conformance suite, not identical transport code. Unix socket peer credentials and filesystem socket permissions support a documented same-user trust model [S6].

The contract exposes `capabilities`, `submit`, `status`, `cancel`, and `subscribe`. Requests carry a client-generated idempotency key, protocol version and fixture identifier. Responses carry a job identifier, service epoch, state and error code. Fixtures avoid arbitrary file access in the first slice. Control messages have explicit size limits. Concurrency is one worker and a bounded queue initially; callbacks coalesce progress instead of accumulating every intermediate event. Jobs are ephemeral: service death invalidates that epoch and clients display interruption. There is no initial exactly-once or durable completion claim.

Android caller authorization is based on the Binder caller identity and a signature permission, not a client-supplied package string. This intentionally restricts the first release to a controlled suite. Public third-party onboarding is a later product decision. Capture identity before dispatching asynchronous work. Do not execute expensive work or call client callbacks while holding global locks. Register remote death handling and remove subscriptions when clients die; Android exposes Binder death notification [S3]. Keep the service bound to visible clients initially, with no promise of unrestricted background execution.

The OS contribution is correct use and explanation of process isolation, IPC, caller identity, scheduling pressure, resource ownership and lifecycle. This does not modify Binder, the kernel scheduler, Android’s framework internals, SELinux policy or Linux drivers. A read-only source walkthrough can supplement this understanding but cannot be counted as implementation.

## Exact minimum vertical slice

On one Android device, install two separately packaged clients and the broker, using a local demo signing identity. The native client submits a 32 MiB fixture digest. The Flutter client submits an independent job and observes status. Both show the broker’s separate PID and the contract version in a developer inspector. Cancel a running job; fill the queue and observe a typed `BUSY` response; kill the broker and show both clients changing to `INTERRUPTED`. Relaunch/rebind, negotiate a fresh epoch, and explicitly resubmit. An unsigned/incorrectly signed test client must fail to bind.

Acceptance is executable: digest correctness against a known fixture; bounded queued jobs; cancellation terminal state; no UI freeze during work; deterministic process-death state; no false successful completion; authorization rejection. Screens need only a submit button, status, cancellation and event timeline. Desktop is excluded from the 40-hour slice and required before a phone-and-PC claim.

## Requirements, evidence and limits

| ID | Evidence and acceptance proposed | Limit or separate career evidence |
|---|---|---|
| R01 | Architecture diagram, three ADRs and dependency-ordered roadmap; reviewers can trace each boundary to a requirement. | One-person design is not proof of owning a commercial architecture organization. |
| R02 | Native Kotlin and Flutter clients pass identical behavior fixtures against Android broker. | Two app ecosystems, not arbitrary runtime support. |
| R03 | Same conformance scenarios pass on Android phone and Linux PC at 120-hour target. | No iOS, Windows or all-device compatibility claim. |
| R04 | Reproducible latency/throughput baseline, one trace-backed optimization and before/after raw runs. | No guaranteed speedup; negative results remain publishable. |
| R05 | Broker memory sampled at idle, steady load and after teardown; queue bounds and subscriber cleanup tested. | Does not establish fleet-wide memory efficiency. |
| R06 | Separate app first-frame, bind/handshake and first-job timings. | App startup is not OS boot latency. |
| R07 | Kill, disconnect, cancellation, overload and malformed-message tests with explicit terminal states. | A finite lab campaign is not a production reliability history. |
| R08 | Load sweep across 1/4/16 clients and documented saturation policy. | Local concurrency, not distributed scale or millions of devices. |
| R09 | Tagged runnable release with reproducible build and product-oriented tradeoff narrative. | Commercial shipping must come from permission-safe career examples. |
| R10 | Contract evolution rules, ADR templates and backward-compatibility fixtures. | A personal standard does not prove organization-wide adoption. |
| R11 | Contribution guide, CI, review checklist and required conformance checks. | Self-review is labeled; do not fabricate other reviewers. |
| R12 | Dated comparison of Binder, in-process library and socket approaches; reevaluation triggers. | One comparison is not evidence of years of technology assessment. |
| R13 | No project substitute. Privately reference the reported relevant degree or equivalent practice with appropriate verification. | Do not publish private CV material or invent qualification evidence. |
| R14 | Kotlin application-framework integration plus independent process/service, lifecycle and IPC implementation. | Meaningful user-space system work; not Android framework or kernel authorship. |
| R15 | Real Flutter plugin and Android/Linux integration with reconnect and error handling. | A tutorial screen alone does not satisfy the acceptance. |
| R16 | Annotated call path through platform bridge, Binder proxy/stub, process boundary and worker. | Source comprehension and OS API use do not establish kernel implementation experience. |
| R17 | A real observed failure gets a diagnosis, competing explanations, fix and regression test. | Planned failures cannot be described as historical achievements. |
| R18 | Five-minute walkthrough, concise RFC and contributor onboarding exercise. | Cross-team leadership requires actual career testimony or real collaboration. |
| R19 | Android depth plus Linux peer-platform implementation. | Preferred examples do not mandate all named platforms; Fuchsia/iOS remain unsupported. |
| R20 | Optional genuine upstream issue or contribution, only after an actual defect or need is found. | Publication alone is not technology-partner work; use consented career examples separately. |
| R21 | None from the repository; private interview evidence can cover reported Netherlands work since 2020. | A project cannot create sustained European professional history. |
| R22 | Public collaboration record only if real participants contribute; otherwise use actual international career examples. | English documentation and invented personas do not prove collaboration. |

Maintain an evidence ledger with `planned`, `implemented`, `measured`, `independently reproduced`, and `career-only` states. Every claim links to a commit, command and result or is explicitly an interview topic. Describe private examples through permission-safe abstractions and distinguish personal contribution from team outcome.

## Benchmark and fault campaign

Register the experiment before tuning: device/OS, hardware, build mode, compiler/runtime versions, compilation state, thermal state, fixture hash, process state and git commit. Use a release-like Android target and Macrobenchmark for application startup; the official tooling separates startup and other application metrics [S4]. Trace broker and SDK events separately so UI startup is not mistaken for IPC readiness.

Proposed lab budgets, to be revised transparently after baseline: metadata round-trip p95 under 10 ms at one client; cancellation observed within 250 ms for chunked work; no more than eight queued jobs; and no sustained memory growth across ten load/teardown cycles. These are aspirations, not measured achievements. Choose memory limits after observing baseline runtime overhead rather than inventing a language-independent RSS target.

Collect at least 30 cold startup samples and 1,000 metadata requests per load cell, repeating runs three times. Publish medians, p95, spread, raw data and exclusions. Randomize baseline/candidate order where practical. Never compare Android PSS directly against Linux RSS as a cross-platform efficiency ranking. Run large-fixture throughput separately from metadata latency to show contention. Report supported device results individually; hosted CI performs functional regression checks, not authoritative performance gating.

Fault matrix: kill client during callback; kill service before/after acceptance; disconnect during subscription; submit duplicate keys; send unsupported versions; exhaust queue; flood progress listeners; cancel at completion boundary; send oversized/malformed Linux frames; attempt unauthorized Android binding. Assert permitted outcomes and cleanup, not just “no crash.” Add a two-hour soak at the extended stage. A recovery trace must identify what was lost and who initiated replay.

## Milestones, effort and scope boundaries

Ranges are planning estimates for one experienced engineer with working Android/Flutter toolchains and a Linux host or VM; elapsed time and device procurement are additional risks. Order is dependency-driven.

| Priority | Milestone | Depends on | Person-hours |
|---|---|---|---:|
| P0 | Evidence ledger, contract, trust model, three core ADRs | None | 4–6 |
| P0 | Android broker, native client, process/lifecycle handling | Contract | 10–14 |
| P0 | Flutter Android plugin and minimal client | Broker | 8–10 |
| P0 | Fault smoke tests, basic measurements, demo README | Clients | 8–10 |
| P1 | Linux broker/plugin and shared conformance fixtures | Proven Android semantics | 24–34 |
| P1 | Version skew, authorization and concurrency hardening | Both adapters | 12–18 |
| P1 | Repeatable performance campaign and packaging | Stable behavior | 12–18 |
| P2 | Deeper contention investigation, optimization and soak | Baseline | 24–36 |
| P2 | Multi-device matrix, expanded fault coverage, contributor exercise | Hardened release | 20–32 |
| P2 | Optional single AOSP service integration spike or external review | Explicit feasibility gate | 24–40 |

**40 hours:** cap scope at the Android vertical slice, ledger and initial demo. If the P0 upper bound overruns, simplify UI, not correctness checks. Not proven: PCs, cross-OS behavior, sustained stability, meaningful optimization, privileged framework development, any career-only requirement.

**120 hours:** target the Android/Linux release, conformance suite, version skew and measured baseline. P0+P1 estimates total 78–110 hours, leaving 10–42 hours for setup and integration contingency. Not proven: broad ecosystem support, production adoption, kernel implementation, sustained reliability, independent collaboration or career credentials.

**240 hours:** target one defensible optimization, deeper fault analysis, multi-device evidence and a reproducible contributor experience. Select the optional AOSP experiment only after the principal deliverables are complete; drop it if tooling threatens the cap. If implemented, distinguish service integration from kernel modification. Not proven: commercial ownership, years of architectural leadership, production SLOs, partner relationships or every OS in the listing.

## Open-source packaging and panel navigation

Use one repository with `/contracts`, `/android`, `/flutter`, `/linux`, `/conformance`, `/benchmarks` and `/docs`. README opens with a 90-second overview, architecture, quick start and a visible limitations box. Provide an Apache-2.0 license subject to dependency/license review, contribution guide, security-reporting instructions, dependency inventory, pinned tool versions and clean-build instructions. Never commit signing secrets, employer materials, personal contacts or private resume documents. Demo keys must be explicitly non-production and generated locally.

Release assets include a source tag, command log, benchmark manifest and short video transcript. Raw failures remain visible. `EVIDENCE.md` maps all requirement IDs; `CLAIMS.md` states what can be reproduced. Public GitHub actions and releases are future implementation tasks, not completed by this planning document.

## Five-minute hiring demo and hard questions

0:00–0:35: State the product problem and show the one-page boundary diagram. Identify the two real process crossings.

0:35–1:35: Submit work from native and Flutter Android clients; show distinct PIDs, bounded queue and cancellation.

1:35–2:25: Kill the broker. Show interruption, new epoch, rebind and explicit resubmission. Explain why no exactly-once guarantee is claimed.

2:25–3:10: Run the Linux client and the same conformance scenario. Explain what differs in identity, lifetime and transport semantics.

3:10–4:20: Open one raw trace and baseline/candidate results. Explain the bottleneck, change and tradeoff; show an unfavorable measurement if relevant.

4:20–5:00: Open the evidence ledger and roadmap. State the strongest demonstrated capability, the largest remaining technical gap and which experience must be established in interview.

Prepare concise answers to: Why does this need a separate process? Why AIDL instead of Messenger? Where is caller identity lost during dispatch? Can a slow callback block other clients? What happens when cancel races completion? How are old clients rejected or supported? What is the difference between transport reconnection and semantic recovery? How would untrusted third-party apps change authorization? Which measurements exclude runtime startup? What evidence would justify a shared C++/Rust core? Which guarantees break under process death? What would integration into `system_server` change about privilege and blast radius?

The panel should be able to select any arrow on the diagram and get an implementation location, ownership rule, failure mode and test. That is the principal-level communication test.

## Major risks and honest gaps

The greatest risk is relabeling normal application integration as OS implementation. The second is overinvesting in desktop ports while Android semantics remain weak. Two implementations can drift; contract fixtures must precede duplication. Self-generated benchmarks can hide mistakes, so reproducibility outranks impressive percentages. A single author cannot demonstrate real cross-team influence through fictional reviews. Obtain independent reproduction only when real people volunteer; never make project completion depend on outside participation.

## Primary sources

Sources consulted 30 October 2026; pin implementation versions when work begins.

- [S1 Android AIDL guide](https://developer.android.com/develop/background-work/services/aidl): IPC and concurrency considerations.
- [S2 AOSP AIDL overview](https://source.android.com/docs/core/architecture/aidl): relationship between AIDL and Binder.
- [S3 Android IBinder reference](https://developer.android.com/reference/android/os/IBinder): remote death notification API.
- [S4 Android Macrobenchmark](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview): application benchmarking workflow.
- [S5 Flutter platform channels](https://docs.flutter.dev/platform-integration/platform-channels) and [desktop support](https://docs.flutter.dev/platform-integration/desktop): native integration and desktop targets.
- [S6 Linux unix(7)](https://man7.org/linux/man-pages/man7/unix.7.html): Unix-domain sockets and peer credentials.
- [Role source](https://nl.linkedin.com/jobs/view/principal-architect-%E2%80%94-os-middleware-system-frameworks-at-sparagus-4476561682): verified requirement interpretation supplied in the brief.

## Scored recommendation

Relevance **28/30**; authentic system depth **19/25**; evidence auditability **19/20**; feasibility **12/15**; reviewer clarity **10/10**: **88/100**. These scores evaluate the proposed 120-hour scope, not completed work. System depth loses points because this is user-space middleware, and feasibility loses points for the second native adapter. Recommend it because it makes the candidate’s strongest plausible architectural evidence easy to inspect while leaving career and kernel-level gaps explicit.
