> Archived independent proposal, 2026-10-30. This is research input, not the selected scope or completed work. The [synthesis](../DECISION.md) and [roadmap](../../docs/ROADMAP.md) define the final plan. No product implementation or benchmark result is asserted.

# Worklane: inspectable local-job middleware for Android and Linux

Independent proposal 08 · Open-source product and adoption lens · 30 October 2026

This is a proposed project, not an implementation or measured result. The target is the Sparagus Principal Architect — OS Middleware & System Frameworks listing identified in the brief; the end client remains unknown. No degree, employment history, adopter, partner relationship, benchmark result, or hiring outcome is implied by publishing this project.

## Recommended project and why

Build **Worklane**, a small embeddable middleware library and conformance suite for running cancellable, bounded file-processing jobs outside an application's UI process. Its initial useful workload is producing SHA-256 integrity manifests for user-selected files. Native Android and Flutter consumers use the same job contract; a Linux desktop host exercises a second operating-system boundary. Offline document tools, archival utilities, and inspection applications could reuse its lifecycle handling, progress events, resource limits, and failure diagnostics without adopting a complete application framework.

The product hypothesis is precise: developers can write the processing algorithm once and obtain consistent job outcomes across native and cross-platform applications, while retaining truthful platform-specific lifecycle behavior. A digest manifest is independently useful and easy to validate with existing hashing tools. Worklane is not a file-sync platform, universal background scheduler, or arbitrary remote-code runner. Its API supports registered built-in operations only. A future consumer may embed another operation, but runtime plugin loading is excluded.

The adoption differentiator should be its executable failure contract: what happens when the consumer disconnects, the worker dies, cancellation races completion, or the event consumer becomes slow? An external developer must be able to download the conformance suite and test their adapter without reading the candidate's biography. Keep the career-oriented requirement matrix in an evidence appendix; lead the public README with the developer problem and working integration.

Two alternatives rejected:

1. **Cross-platform device-information dashboard.** Quick to demonstrate but usually little more than wrappers around platform getters. It has weak sustained utility, limited fault semantics, and little reason for another maintainer to contribute.
2. **New mobile operating system or general-purpose middleware bus.** Potentially deeper but too broad for 240 hours, difficult to validate on real devices, and likely to produce diagrams instead of usable software. Worklane selects a narrow process-lifecycle problem while leaving the OS in charge of scheduling and access control.

## Architecture and the real OS boundary

Use a Rust processing core for deterministic streaming hashing, bounded buffering, cancellation checkpoints, and job-state transitions. Android has a Kotlin host service in a separate application process, reached through AIDL. Its small JNI boundary invokes the core and owns native-resource lifetime explicitly. A native Kotlin sample and a Flutter plugin consume the service. Flutter platform channels bridge Dart to the Android client, not directly around the process boundary. Android's AIDL documentation describes real IPC and concurrency considerations; the design must test them rather than assume UI-thread serialization [1,2].

The Linux PC adapter hosts the same core in a child process over a filesystem Unix-domain socket, with a private user-owned directory, bounded length-prefixed messages, negotiated protocol version, and a request identifier. Unix sockets provide a genuine local IPC mechanism [3]. The Linux reference CLI is sufficient initially; a Flutter Linux UI is a later integration, not a prerequisite for proving a PC target.

Public contract: `submit`, `watch`, `cancel`, `query`, and `capabilities`. States are accepted, running, succeeded, cancelled, failed, and interrupted. Each submission has a unique ID; replay is not silently deduplicated or described as exactly-once execution. Events carry monotonically increasing per-job sequence numbers. A slow subscriber receives coalesced progress plus the current authoritative state, never an unbounded progress backlog. Result delivery has a queryable terminal state within the current host session. Host death invalidates that session and produces interrupted status on reconnection; durable resumption is expressly absent in version 0.1.

Android uses a user-selected descriptor passed to the worker, avoiding invented global file access. The host remains within the application's UID and uses a non-exported service; separate process does not imply a hostile-code security sandbox. Linux initially accepts only files staged in a designated user-owned workspace; descriptor passing is a later hardening option. No network listener, administrator permission, private framework API, or root access is required.

The minimum runtime promise is foreground, user-initiated work while a client is bound. Android can stop application processes; Worklane reports interruption and does not promise indefinite background execution. Deferred work integration would need a separate platform-policy design. This is user-space systems and application-framework engineering: IPC, process lifetime, handles, concurrency, and memory ownership. It is not kernel implementation, a device driver, or an AOSP platform service.

## Exact minimum vertical slice

On one physical Android phone, a native Kotlin app selects a synthetic 64 MiB file, binds the separate-process worker, submits one hashing job, observes bounded progress, and receives a digest verified independently. A second run cancels and releases the descriptor. A test-only harness kills the owned worker process during execution; the UI shows interrupted, reconnects, and successfully starts a new job. Record separate client and service PIDs, trace the binder interaction, and include the raw test output.

Ship source, reproducible fixture generation, a short integration example, and the failing-then-passing regression for one lifecycle bug. Exclude Flutter, Linux, persistent queues, and public package-registry publication from this minimum. This makes 40 hours a credible exploration, not a claim to meet every advertised requirement.

## Requirement coverage and acceptance

All acceptance criteria below are proposed. “Evidence” means a linked artifact at a specific commit and environment, not a checklist tick.

| ID | Evidence and acceptance | Limit |
|---|---|---|
| R01 | Component map, ownership model, sequenced roadmap, and ADR explaining the lifecycle contract; roadmap changes tied to issues. | Small project architecture, not enterprise portfolio ownership. |
| R02 | Native Android and Flutter Android execute identical conformance vectors. | Two application ecosystems; no React Native claim. |
| R03 | Physical Android phone and Linux PC pass shared job-state tests; publish device matrix. | No iOS, Windows, or universal compatibility claim. |
| R04 | Reproducible throughput and IPC-latency comparison against an in-process baseline. | Measurements apply only to recorded workloads/devices. |
| R05 | Memory and descriptor traces show bounded growth as input size increases. | Does not establish memory behavior of arbitrary extensions. |
| R06 | Separate UI startup, first bind, process start, and time-to-first-progress measurements. | No undocumented “instant startup” claim. |
| R07 | Worker kill, client loss, cancellation races, malformed input, and repeat-run recovery tests. | Cannot promise OS uptime or automatic durable recovery. |
| R08 | Sweep 1/2/4/8 clients with bounded queue and explicit overload rejection. | Local concurrency, not distributed/cloud scale. |
| R09 | Released artifact, consuming sample, installation test, and tracked integration feedback. | Product relevance does not equal commercial shipment. |
| R10 | Published protocol/state machine, compatibility policy, ADRs, and adapter conformance suite. | Project standards, not industry-standard ownership. |
| R11 | Required build/test/lint/license checks; review template targets process lifetime and resources. | Solo author review cannot become independent review by relabeling it. |
| R12 | Dated quarterly assessment comparing this adapter approach with platform scheduling and plugin alternatives. | One research note does not establish sustained technology leadership. |
| R13 | Optional separate, consented credential reference or discussion of equivalent practice. | Software cannot prove a degree; publish no private resume. |
| R14 | Android service lifecycle, binder threading, resource ownership, and crash analysis documented with real traces. | Application framework plus user-space systems depth, not kernel work. |
| R15 | Maintained Flutter plugin and consuming sample pass native parity tests. | Deferred until 120h scope; running a template alone is insufficient. |
| R16 | Annotated process/UID/descriptor diagram and measured lifecycle explanation. | Demonstrates understanding of relevant mechanisms only. |
| R17 | Reproduction, competing hypotheses, trace, minimal fix, and regression for a difficult observed defect. | Never manufacture a production incident. |
| R18 | Clear RFC, integration guide, and attributable review discussion if another person participates. | Docs show communication; solo work cannot prove cross-team history. |
| R19 | Android implementation and Linux peer-platform comparison. | Preferred examples do not require Fuchsia or ChromeOS; no claimed exposure from a paper sketch. |
| R20 | Consent-based pilot report or linked upstream interaction when actually completed. | Maintainer contact is not a technology partnership. |
| R21 | Separate verified professional-history evidence when appropriate. | Repository cannot manufacture sustained European experience. |
| R22 | Attributable cross-border technical collaboration only if it occurs. | English documentation and remote-accessible issues are enabling conditions, not proof. |

## Measurement and fault plan

Use fixed generated inputs of 1, 64, and 512 MiB; publish generator seed, file digest, filesystem, phone build, CPU, RAM, toolchains, commit, build flags, thermal state, and power mode. Baselines are the same hashing core in-process and the full IPC path. Do not confuse digest throughput with bridge latency: measure empty control requests independently.

Run at least 30 cold host starts and 100 warm control requests per configuration, with multiple independently repeated sessions. Report median, p95, sample count, dispersion, raw observations, and exclusions. Keep cache state explicit; “cold process” does not mean “cold filesystem.” Record Android total proportional memory across client and service and Linux process RSS separately rather than pretending those measures are identical.

Initial engineering targets, subject to baseline revision: metadata messages below 16 KiB; four active jobs maximum; queue depth 16; explicit rejection beyond capacity; additional worker memory grows by less than 10 MiB between 64 and 512 MiB inputs; cancellation observed within 250 ms at p95 on the recorded device. These are provisional gates, not claims. Benchmark overhead matters: compare tracing enabled and disabled. Avoid CI timing gates on heterogeneous hosted machines; use CI for functional invariants and stable dedicated devices for performance regressions.

Fault cases include worker termination, client termination, unreadable input, descriptor closure, input mutation during reading, truncated/oversized Linux frames, unsupported protocol version, slow subscribers, and repeated cancellation. Define snapshot semantics honestly: a file modified during hashing can produce a non-snapshot digest; detect observable metadata changes and fail where possible, but do not promise perfect mutation detection. Require no false success, no retained descriptors after 100 submit/cancel cycles, and recovery for the next valid job. Linux harness tests protocol fuzzing; Android tests exercise binder clients and genuine process death.

## Priorities, dependencies, and effort

Estimates are focused person-hours for an experienced Android engineer learning the selected Rust integration surface, excluding waiting for external reviews. Each budget is a scope cap, not an additive promise.

| Stage | Dependencies | Hours | Exit |
|---|---|---:|---|
| A: contract, ownership, fixture, threat notes | None | 6–10 | State model and acceptance vectors agreed locally. |
| B: Android native vertical slice | A | 22–30 | Real IPC hashing, cancellation, kill/reconnect test. |
| C: packaging and first measurements | B | 6–10 | Honest preview with raw evidence. |
| D: Flutter Android integration | B | 16–24 | Native/Flutter parity and plugin consumer. |
| E: Linux host and CLI | A, core from B | 22–32 | PC conformance and private socket boundary. |
| F: fault matrix and release automation | C–E | 14–22 | Repeatable source/build/release provenance. |
| G: adopter usability and hardening | F | 45–70 | Fresh-machine integration, reviewed fixes, maintainable docs. |
| H: compatibility rehearsal and deeper profiling | G | 25–40 | Old-client/new-host checks and published performance investigation. |

**40h:** target A–C at their lower/middle bounds; drop polish if necessary. Not proven: Flutter, PC compatibility, ecosystem adoption, sustained maintenance, durable recovery, cross-team work, or all requirements.

**120h:** target A–F within a roughly 86–128h band; hard-cap by reducing UI polish and optional platform combinations, never by omitting failure honesty. Not proven: production reliability, actual external adoption, commercial shipping, partnerships, kernel engineering, credentials, or European career history.

**240h:** complete G–H with the remaining budget for observed integration issues, a second Android device/API configuration, and documented maintenance. Do not assume all upper bounds fit. Not proven: organic community growth, externally accepted changes, long-term sustainability, or personal-history requirements. External review response time is not under project control.

## Open-source packaging and sustainable contribution

Proposed repository: `worklane`, with `core/`, `hosts/android/`, `hosts/linux/`, `clients/kotlin/`, `clients/flutter/`, `examples/`, `protocol/`, `conformance/`, `benchmarks/`, `docs/adr/`, and `evidence/`. README offers a ten-minute integration, current support table, limitations, and one honest result chart. Separate tutorial, reference, architecture explanation, and troubleshooting pages. Include CONTRIBUTING, CODE_OF_CONDUCT, SECURITY, GOVERNANCE, MAINTAINERS, CHANGELOG, and an issue template requesting a minimal reproducer and sanitized environment.

Use Apache-2.0 for original code and documentation; include the actual license and required third-party notices [4]. Maintain SPDX file annotations and a machine-readable dependency inventory following REUSE guidance [5]. Prefer generated synthetic fixtures; record upstream URL, revision, license, and modifications for reused examples. No employer code, copied private SDK contracts, CV, credentials, contacts, or machine paths enter artifacts. Use DCO sign-off for rights declarations, explaining its permanent public record before contributions [6]. DCO is not a substitute for checking provenance.

Version 0.1 is an experimental Android preview; 0.2 adds Flutter/Linux and explicit compatibility tests; 0.3 incorporates observed integration findings. Define public API surfaces before 1.0: client signatures, error codes, protocol, state transitions, and documented configuration. Follow semantic versioning and maintain a version handshake independent of package version [7]. Release only tested platform artifacts, source archive, checksums, SBOM, and build provenance. GitHub artifact attestations describe where/how builds occurred; they do not establish correctness or independently guarantee reproducible binaries [8]. Pin dependencies and CI actions; release automation gets narrow permissions.

Begin with one accountable maintainer, a small supported matrix, best-effort issue triage, and no promised SLA. Publish a support/deprecation window only after maintenance capacity is known. Prefer fewer useful releases over a cadence manufactured for hiring.

Genuine external evidence is optional and recorded only after it exists: an independently reproduced issue, another developer's integration PR, a substantive upstream bug report, or a consented pilot write-up with reproducible setup. The current evidence count is **zero**. Do not create sock-puppet contributions, count agents as collaborators, seed fake discussions, or treat stars as technical validation. After a usable release and separately authorized outreach, seek feedback from relevant maintainers without promotional issue spam. If nobody responds, publish the solo integration test and say so. A concrete documentation fix arising from real external feedback is stronger than a fabricated “partner ecosystem.”

## Five-minute hiring demo

0:00–0:40: explain the consumer problem, supported scope, and OS process diagram. 0:40–1:40: run the native Android manifest operation and verify its digest. 1:40–2:30: kill the worker, show interrupted state, reconnect, and run again. 2:30–3:20: run the same vectors from Flutter and the Linux CLI if implemented. 3:20–4:20: inspect one raw benchmark trace, resource graph, and linked regression. 4:20–5:00: show the version policy, independent-install instructions, actual contribution record, and explicit gaps. Use prerecorded fallback evidence labeled with date and commit; do not present it as live.

## Major risks and recommendation

Three-language integration can consume the entire budget; prove ownership and cancellation before adding features. Hashing alone is commodity functionality; adoption depends on whether lifecycle handling saves consumers enough work to justify another dependency. Validate that hypothesis through actual integration experience, and stop expanding if the bridge adds complexity without benefit. Android background restrictions, JNI resource errors, benchmark noise, and overstated portability are material technical risks. No external engagement exists yet, and no repository can repair missing career credentials.

Score, as a planning judgment: relevance **27/30**, authentic system depth **20/25**, evidence auditability **19/20**, feasibility **11/15**, reviewer clarity **9/10**; total **86/100**. Recommend the 120h bounded product with a 40h go/no-go checkpoint. Its strongest value is usable, auditable failure handling across real OS process boundaries, with external adoption treated as an outcome to earn rather than a claim to stage.

## Primary sources

Sources checked 30 October 2026; interpretations above distinguish platform facts from proposed design.

1. [Android AIDL](https://developer.android.com/develop/background-work/services/aidl) and [bound services](https://developer.android.com/develop/background-work/services/bound-services).
2. [Flutter platform channels](https://docs.flutter.dev/platform-integration/platform-channels).
3. [Linux unix(7)](https://man7.org/linux/man-pages/man7/unix.7.html).
4. [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).
5. [REUSE specification](https://reuse.software/spec/).
6. [Developer Certificate of Origin 1.1](https://developercertificate.org/).
7. [Semantic Versioning 2.0.0](https://semver.org/).
8. [GitHub artifact attestations](https://docs.github.com/en/actions/how-tos/secure-your-work/use-artifact-attestations/use-artifact-attestations).
9. [Sparagus listing supplied in brief](https://nl.linkedin.com/jobs/view/principal-architect-%E2%80%94-os-middleware-system-frameworks-at-sparagus-4476561682).
