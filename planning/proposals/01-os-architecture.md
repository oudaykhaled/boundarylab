> Archived independent proposal, 2026-10-30. This is research input, not the selected scope or completed work. The [synthesis](../DECISION.md) and [roadmap](../../docs/ROADMAP.md) define the final plan. No product implementation or benchmark result is asserted.

# SessionBuffer: auditable system middleware across application ecosystems

Independent proposal 01 · OS architecture and primary-source lens · 30 October 2026

This is a proposed open-source portfolio project, not implemented software or measured results. The target is Sparagus’s Principal Architect — OS Middleware & System Frameworks listing; the end client is unknown. No project can guarantee hiring or manufacture professional experience.

## Recommended project and why

Build **SessionBuffer**, a bounded, permission-controlled snapshot broker integrated into an AOSP image. Native Android and Flutter applications obtain the same immutable, versioned data snapshots through a framework API. A later Linux desktop service exposes equivalent semantics to a Flutter desktop client. The example workload is a synthetic device-session catalogue, useful for discussing device dashboards and multi-application platforms without copying employer software or handling personal information.

The architectural problem is concrete: several independent application processes need a consistent shared snapshot without each maintaining a producer, allocating duplicate datasets, or trusting other applications. The broker owns publication, caller authorization, generation identifiers, quotas, and lifecycle cleanup. Clients own rendering. Compare ordinary serialized IPC with shared-memory handles using identical payloads; allow the measurements to show where shared memory loses for small payloads.

This narrow capability gives an interviewer reviewable evidence of framework integration, Binder semantics, process boundaries, API evolution, memory lifetime, fault containment, startup sequencing, and Flutter interoperability. It is deliberately less feature-rich than a consumer application. Its commercial relevance is an architectural pattern and operational discipline, not an assertion that the project shipped commercially.

## Two rejected alternatives

1. **Flutter device-information SDK with native platform plugins.** Fast and visually convincing, but a plugin principally connects Dart to native code in its host application. It does not by itself demonstrate implementation of an OS service or framework lifecycle. Flutter documents this boundary explicitly in its [architectural overview](https://docs.flutter.dev/resources/architectural-overview).
2. **A new cross-platform runtime or mini operating system.** Broad relevance on paper, but boot, graphics, runtime, security, packaging, and hardware support overwhelm a 240-hour portfolio budget. The likely result is many abstractions and little independently reproducible system evidence. Use an existing OS and make one bounded integration excellent.

## Architecture and real OS boundary

```text
Native Compose client            Flutter Android client
        |                         Dart -> plugin -> Kotlin SDK
        +---------------------------+
                                    |
                         SessionBufferManager
                                    | Binder / versioned AIDL
                                    v
                       AOSP SessionBufferService
                      registered during framework boot
                     permission + UID quota + generations
                                    |
                     immutable SharedMemory snapshots
                                    |
                    existing Android/Linux primitives

Flutter Linux client -> native adapter -> local IPC -> Linux broker
                      same semantics, different transport
```

**Android implementation:** a small Java framework service hosted in `system_server`, a manager facade, a structured AIDL interface, build integration, service registration, and narrowly scoped SELinux/service visibility changes where the selected branch requires them. Android clients use a compile-time SDK artifact produced from this custom platform; there is no reflective hidden-API workaround. The image grants a project-defined signature permission only to the demonstration clients. Every Binder entry point validates the caller independently of client-side checks. Caller UID comes from Binder, never from a caller-supplied field. Two separately installed applications establish the ecosystem boundary.

The system-service choice is an explicit tradeoff: it gives authentic framework and boot integration but increases blast radius. Keep the service small; no network, arbitrary file reads, unbounded allocation, callbacks while holding locks, or application-controlled parsing in a critical thread. A dedicated handler performs bounded publication work. A subsequent ADR can evaluate moving the producer into a separate process; do not silently claim service-local restart when the implementation lives in `system_server`.

**Data model:** a schema-versioned header, generation, declared byte length, and immutable records. Publish a fresh snapshot, then reduce allowed mapping protection to read-only before distributing its handle. Android’s [SharedMemory API](https://developer.android.com/reference/android/os/SharedMemory) supplies mapping and protection primitives; detailed protection behavior must be tested on the pinned image. Avoid a writable ring buffer and its concurrency complexity in the first version.

A client lease is an accounting and notification relationship, not a promise to revoke an already delivered mapping. A hostile client can retain a descriptor. Consequently the broker limits allocations and outstanding generations globally, refuses further publication when its policy budget is exhausted, and documents that reference tracking cannot force another process to release memory. Client death releases broker bookkeeping; it does not justify an unsupported universal reclamation claim.

**Desktop implementation:** a Linux user service with a local authenticated IPC endpoint and a second implementation of the contract. Start with copied snapshots; shared-memory file-descriptor transport is optional. Report the desktop path’s actual behavior rather than claiming zero-copy parity. A user service demonstrates user-space OS integration; it is a weaker boundary than the AOSP framework service. Use a dedicated test account or VM for installation. [systemd’s execution documentation](https://www.freedesktop.org/software/systemd/man/latest/systemd.exec.html) provides the basis for service isolation choices.

**What is genuinely system-level:** modifying and building framework code, registering a system service, enforcing platform permissions, integrating boot lifecycle, and testing cross-process resource semantics. AIDL is Android IPC infrastructure, as explained in the [Binder overview](https://source.android.com/docs/core/architecture/ipc/binder-overview). **What is not proven:** writing the Binder driver, kernel scheduling, device drivers, HAL implementation, vendor compatibility certification, or a portable OS. Framework development is not kernel implementation.

## Exact minimum vertical slice

Pin one AOSP release commit and boot a custom Cuttlefish image. Register SessionBufferService. A signed native Android client requests one deterministic 64 KiB snapshot containing a generation and checksum, maps it read-only, displays the checksum, and closes it. An unsigned negative-test client receives a permission failure. Kill the authorized client and demonstrate cleanup of broker accounting. Repeat 100 times without accumulating broker leases or open descriptors. Archive build identity, commands, test results, and one trace.

This slice contains no Flutter, desktop, live telemetry, remote service, or UI polish. It is the first acceptance gate because a normal APK implementing an exported bound service cannot substitute for its framework evidence. [Cuttlefish](https://source.android.com/docs/devices/cuttlefish) provides a configurable virtual Android device; it is not proof of physical-phone performance.

## Requirements-to-evidence contract

All acceptance statements below are proposed. A traceability document links each eventual claim to a commit and evidence artifact.

| ID | Evidence and acceptance | Honest limit |
|---|---|---|
| R01 | Context/container diagrams, ownership ADR, sequenced roadmap; implementation matches boundaries. | One bounded service, not a company platform strategy. |
| R02 | Compose and Flutter applications independently consume the broker; shared contract tests pass. | Two application ecosystems, not arbitrary runtimes. |
| R03 | Cuttlefish phone configuration plus Linux PC client run the same semantic fixtures; later physical phone SDK test. | Custom-image Android and Linux only; virtual phone is labelled. |
| R04 | Copied versus shared-memory experiment with raw latency/CPU traces and reproducible analysis. | No speed claim before measurement. |
| R05 | PSS, descriptor and generation accounting across fan-out; retained-handle adversary test. | Shared pages cannot be counted by simply summing RSS. |
| R06 | Separate service registration, first request, and app startup timings; cold/warm distributions. | Emulator wall time is not a phone startup SLA. |
| R07 | Death, malformed metadata, denied caller, resource exhaustion, and reboot tests with expected outcomes. | No fleet reliability claim. |
| R08 | 1/4/16/32 client load sweep; bounded queues and explicit overload errors. | Local fan-out, not distributed scalability. |
| R09 | Reviewed design, functioning vertical slice, tagged release, operator instructions, product-context ADR. | Commercial relevance is demonstrated; commercial deployment is not. |
| R10 | ADRs for API compatibility, trust boundary, lifetime, and service placement; review checklist. | Project standards, not enterprise adoption. |
| R11 | Formatting, static checks, unit/IPC integration tests, contract compatibility gate and reproducible builds. | Passing CI does not imply Android certification. |
| R12 | Dated comparison of Binder/AIDL, Flutter channel choices and Linux transport; one upgrade experiment. | Technology assessment is scoped, not exhaustive. |
| R13 | Public contribution history demonstrates practice; privately supplied degree evidence remains separate. | Repository cannot prove a degree. |
| R14 | Custom AOSP service, manager, boot registration, permissions and negative tests. | Android satisfies the alternative; iOS expertise is not claimed. |
| R15 | Real Flutter plugin, lifecycle/disconnect handling, UI integration and plugin tests. | Cross-platform skill only after runnable delivery. |
| R16 | Annotated process/UID/thread diagram and real boot/IPC trace tied to code. | Existing kernel mechanisms are used, not implemented. |
| R17 | One measured investigation: descriptor retention, callback starvation or startup contention; hypothesis and before/after evidence. | Do not fabricate an incident or optimize a contrived defect solely for a story. |
| R18 | Clear RFC, issue decisions and reproducible reviewer guide; invite actual review. | Solo authored documents do not prove cross-team collaboration. |
| R19 | Deep Android exposure plus Linux desktop comparison. | No requirement to implement every example OS; no Fuchsia/ChromeOS/iOS claim. |
| R20 | Optional substantive upstream issue/patch arising from observed behavior, with authentic response history. | Publishing an issue does not establish partnership. |
| R21 | Keep verified employment history outside technical project claims. | A repository cannot manufacture sustained European work experience. |
| R22 | Contribution guide and accessible technical discussion enable international participation. | Count collaboration only when independent people actually participate. |

## Benchmark and fault plan

Use fixed seeds and payload sizes of 1 KiB, 64 KiB and 256 KiB; larger payloads wait until platform transaction limits and baseline behavior are understood. Compare copying with shared-memory delivery using equivalent validation and consumption. Record commit, AOSP fingerprint, architecture, CPU/RAM allocation, thermal state where available, release/debug mode, compilation state, and tracing configuration.

For steady-state experiments, propose 10 independent runs of 1,000 operations after explicit warm-up. Report median, p95, throughput, CPU time, allocations and run-to-run variation; do not promote tiny-sample p99 numbers. For startup use 30 separately reset launches and distinguish app process cold start from device boot and service readiness. [Macrobenchmark](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview) measures application scenarios; explicit trace markers cover service registration and IPC. Use [Perfetto](https://perfetto.dev/docs/) for correlated scheduling and memory analysis.

Provisional acceptance budgets: no unbounded allocation under 32 clients; no broker-owned descriptor growth after 1,000 connect/disconnect cycles; no deadlock or framework crash in a one-hour bounded stress run; denied callers receive no payload handles; old clients remain usable after an additive contract change. Performance improvement is a hypothesis, not a release requirement. Set numerical latency budgets after baseline capture and explain product relevance before freezing them.

Inject client death during delivery, slow callbacks, bad lengths/versions, duplicate close, resource saturation and boot-order changes. Kill/restart `system_server` only inside a disposable image and characterize device/framework recovery honestly. On Linux test service-process termination and reconnection separately. Keep privileged fault scripts off ordinary user machines. Verify SELinux enforcing mode; never make permissive mode the acceptance configuration. Follow AOSP’s [SELinux customization guidance](https://source.android.com/docs/security/features/selinux/customize).

## Prioritized milestones and scope budgets

Person-hours are planning ranges, excluding passive downloads/build queues and unavailable hardware. They are not delivery promises. One engineer owns implementation; external review availability is separate.

| Milestone | Dependency | Hours | Exit evidence |
|---|---|---:|---|
| M0 Host feasibility, pinned manifest, trust/lifetime ADR | None | 8–16 | Linux/KVM feasibility and bootable baseline |
| M1 Exact AOSP vertical slice | M0 | 24–44 | Boot registration, authorized/denied calls, lifecycle test |
| M2 Flutter Android consumer and versioned SDK | M1 | 20–32 | Two independent client applications |
| M3 Benchmark and fault harness | M1; final comparison after M2 | 24–40 | Raw artifacts, analysis and bounded-resource tests |
| M4 Linux desktop semantic adapter | M2 contract stabilized | 28–44 | Flutter desktop plus conformance results |
| M5 Hardening, compatibility, release/demo | M3, M4 | 24–40 | Reviewer reproduction, upgrade test, release candidate |

Total anticipated hands-on work: **128–216 hours**, leaving up to 24 hours of a 240-hour budget for integration uncertainty at the upper estimate. Obtain host feasibility before committing to calendar dates: current [AOSP requirements](https://source.android.com/docs/setup/start/requirements) require substantial build resources, and Cuttlefish development targets Linux; a Mac workspace is not evidence that a suitable builder exists. No cloud spending is assumed.

- **40 hours:** feasibility plus a reduced M1 at favorable estimates. Deliver the exact slice only if it passes; otherwise publish an honest partial build investigation. **Not proven:** Flutter, PC compatibility, optimization, sustained reliability, full requirements coverage.
- **120 hours:** aim for M0–M3 and a polished Android evidence package, cutting scope rather than weakening the system-service gate. **Not proven:** dependable desktop implementation, physical-device system integration, production durability, real partnership, or professional-history facts.
- **240 hours:** M0–M5, Linux compatibility and reviewable evidence, with optional physical-phone client validation if hardware exists. **Not proven:** stock-phone installation of the custom service, commercial shipment, certification, kernel implementation, iOS/Fuchsia/ChromeOS expertise, or real external collaboration without actual participants.

## Open-source/GitHub packaging

Proposed repository: `sessionbuffer-framework`. Publish original code under Apache-2.0 after dependency/license review; preserve upstream notices and store AOSP patches/manifests rather than vendoring an OS tree. Include `README`, `ARCHITECTURE`, `ROADMAP`, `CONTRIBUTING`, `SECURITY`, ADRs, API schemas, AOSP integration, Android SDK, Flutter plugin, sample clients, Linux service, conformance fixtures, and evidence index.

README begins with a short demonstration and a clear supported/experimental/not-implemented matrix. Every benchmark chart links raw data and the exact command. Separate ordinary SDK checks from opt-in heavy image builds; hosted CI does not pretend to validate an image it never boots. Release only license-cleared artifacts with checksums and provenance. Publish no CV, contacts, credentials, private company material, absolute workstation paths or employer code. Issues and a milestone board distinguish proposed, implemented, reviewed and verified work. Publication of this plan does not authorize implementing or releasing the product.

## Five-minute hiring demonstration

1. **0:00–0:45:** show the trust/process diagram and custom image fingerprint; locate the framework service source and registration.
2. **0:45–1:45:** native and Flutter Android clients read the same generation/checksum; show the unauthorized client failing.
3. **1:45–2:45:** kill a client, show resource accounting, then trigger quota exhaustion and predictable recovery.
4. **2:45–3:45:** open one trace and the copied/shared-memory results, explaining any crossover or negative result.
5. **3:45–4:30:** Linux desktop consumes the same fixture; explicitly identify different transport and security semantics.
6. **4:30–5:00:** show compatibility evidence and the honest gaps table, then discuss the `system_server` blast-radius tradeoff.

## Major risks and honest gaps

AOSP host provisioning is the first blocker candidate. Do not quietly replace the framework service with an APK and retain the claim. Buffer retention and read-only protection semantics are the principal correctness risks; solve them with bounded allocation, immutable generations and explicit non-revocability. A synthetic workload may seem academic, so explain its multi-application reuse scenario and quantify tradeoffs without claiming a real customer. Linux duplication can consume the budget; ship the Android core first. Security isolation, release engineering and testing support commercial relevance but cannot substitute for production experience or independent review.

## Primary sources and scored recommendation

The linked official AOSP, Android Developers, Flutter, Perfetto and systemd pages above were checked on 30 October 2026. [Stable AIDL](https://source.android.com/docs/core/architecture/aidl/stable-aidl) additionally grounds interface tracking and compatibility; use stable versioning where appropriate to the selected partition boundary, without implying that every internal interface must be a VINTF HAL. Pin source revisions rather than relying on moving documentation labels. The job source is the [Sparagus listing](https://nl.linkedin.com/jobs/view/principal-architect-%E2%80%94-os-middleware-system-frameworks-at-sparagus-4476561682), paraphrased in the supplied brief.

| Criterion | Score | Reason |
|---|---:|---|
| Relevance | 28/30 | Direct framework, middleware, memory, performance and ecosystem evidence |
| Authentic system depth | 23/25 | Real AOSP integration; intentionally no kernel implementation |
| Evidence auditability | 19/20 | Commit-linked image, traces, negative tests and reproducible fixtures |
| Feasibility | 10/15 | AOSP host and dual-platform work are material uncertainties |
| Reviewer clarity | 9/10 | One capability, visible boundary and concise demo |
| **Total** | **89/100** | **Recommend the 120-hour Android core, with a gated extension to 240 hours.** |
