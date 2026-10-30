# Evidence policy

At initial plan publication the repository contained plans only. Implementation and scoped runtime receipts were added on 2026-10-30; current results and limitations are recorded in the [implementation evidence map](IMPLEMENTATION-STATUS.md) and [execution board](../planning/BUILD-BOARD.md). Controlled benchmark and independent adoption claims remain unavailable.

Every requirement is assigned one or more evidence types: source/design, executable contract, native runtime, controlled experiment, independent adoption or historical career evidence. A design document cannot satisfy a runtime gate. An emulator result cannot silently become a physical-device performance claim. Author-run integration cannot become external adoption.

## Evidence record

Results must name the source commit or exact uncommitted source hashes, artifact hashes, toolchain/dependency pins, OS build/API and architecture, device class (with public identifiers redacted), build mode, scenario and workload seed, timestamps, command/runner, expected outcome, actual outcome, raw-output/trace checksums, exclusions and limitations. Source-hash receipts are associated with the subsequent publication commit; a later edit does not inherit an earlier passing result. A report includes failures, not only successful attempts. Every summary links its underlying record.

Separate statuses: planned → implemented → checked in declared scope → independently reproduced. Merged, tagged, deployed and commercially shipped are separate facts. Do not add passing badges for lanes that do not exist.

## Experiments

Before comparing implementations, freeze a workload and equivalence oracle, baseline revision, target device and build settings, warmup/sample schedule, compilation/cache state, thermal exclusion rule and decision thresholds. Interleave or randomize paired runs, retain all raw observations and state exclusions. Report distributions, uncertainty and memory/CPU tradeoffs. Absolute p95 targets are hypotheses pending a pilot, not market-wide service guarantees. Small startup samples support median/range observations; do not present a precise tail percentile from a handful of runs.

Use separate clocks appropriately: on-device monotonic duration for a same-host request; cross-device timestamps are not interchangeable. Process startup, framework initialization, first UI frame and middleware-ready are different metrics. Report them separately.

## Truthful scope

An ordinary Android bound service using Binder demonstrates application-accessible IPC and middleware design. It does not demonstrate modification of system_server, a HAL, an OS kernel or a shipping OS framework. Platform integration requires a separately built platform artifact and corresponding verification. Read-only architecture analysis is analysis, not implementation. This distinction follows the Android [application AIDL guide](https://developer.android.com/develop/background-work/services/aidl) and [platform AIDL backend restrictions](https://source.android.com/docs/core/architecture/aidl/aidl-backends).

Education, professional tenure, commercial product delivery and genuine collaboration require independently supportable evidence outside a solo code repository. Only consented, non-confidential public references belong here. Do not publish employer source, contact details, CVs, device serials, credentials or private traces.

## External participation

A maintainer-authored second sample demonstrates consumer integration. Independent adoption requires another person to run the documented contract against a released artifact, record their environment and report an actual outcome. An unanswered issue is not a partnership. Contributions and benchmarks produced with AI assistance should disclose that assistance and retain accountable human review.
