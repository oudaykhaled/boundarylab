# Implementation backlog

All packages are **planned**. Owners describe the proposed maintainer role; no implementation has started. Estimates include proportionate verification and documentation. Dependencies are package IDs, not evidence that those packages exist in source. Gate timeboxes and contingency are defined in the [roadmap](../docs/ROADMAP.md).

Baseline nominal work = 100h plus 20h reserve. Platform extension nominal work = 100h plus 20h reserve. A range exposes uncertainty; all upper bounds do not fit the nominal cap. [Machine-readable backlog](backlog.json).

| ID | Gate | Work | Nominal (range) hours | Dependencies | Requirements |
|---|---|---|---:|---|---|
| B01 | G1 | [Feasibility, trust model and contract](#b01) | 6 (4–8) | None | R01, R10 |
| B02 | G1 | [Bounded deterministic core and state model](#b02) | 8 (6–10) | B01 | R05, R08 |
| B03 | G1 | [Real Android Binder broker and Kotlin SDK](#b03) | 10 (8–14) | B02 | R14, R16 |
| B04 | G1 | [Native consumer, denial client and fault smoke](#b04) | 6 (4–8) | B03 | R02, R07 |
| B05 | G1 | [Flutter plugin and second installed consumer](#b05) | 10 (8–16) | B04 | R02, R15 |
| B06 | G2 | [Deterministic fault, ownership and overload campaign](#b06) | 10 (8–14) | B05 | R05, R07, R08, R11, R17 |
| B07 | G2 | [macOS JVM host and CLI conformance](#b07) | 14 (10–20) | B05 | R03, R19 |
| B08 | G2 | [Compiled compatibility fixtures and quality lanes](#b08) | 8 (6–10) | B06, B07 | R03, R07, R10, R11 |
| B09 | G2 | [Controlled startup, memory and load experiment](#b09) | 14 (10–20) | B08 | R04, R05, R06, R08 |
| B10 | G2 | [Fresh consumer, release rehearsal and maintainer docs](#b10) | 8 (6–12) | B09 | R09, R11, R18 |
| B11 | G2 | [Technology assessment and evidence case study](#b11) | 6 (4–8) | B10 | R01, R04, R12, R17 |
| B12 | G3 | [Linux/AOSP clean-image feasibility gate](#b12) | 12 (8–20) | B11 | R16 |
| B13 | G3 | [Image-integrated native Binder service](#b13) | 36 (24–52) | B12 | R14, R16 |
| B14 | G3 | [Platform-facing facade and executed integration tests](#b14) | 24 (18–34) | B13 | R14, R16, R19 |
| B15 | G3 | [Deeper measured investigation and bounded soak](#b15) | 16 (10–24) | B14 | R04, R05, R07, R17 |
| B16 | G3 | [Reproduction, productization gaps and final handoff](#b16) | 12 (10–20) | B15 | R09, R18 |

## B01

**Feasibility, trust model and contract** · G1 · Owner: proposed maintainer · Status: planned.

Dependencies: None. Evidence: none; next step is dependency resolution and authorized implementation.

- [ ] Prove clean Android and native Flutter build/run on allocated target; prove macOS JDK socket capability without treating tool presence as readiness.
- [ ] Freeze protocol, caps including retained-byte ceiling, state/lifecycle/identity ADR and golden vectors.
- [ ] Inventory physical measurement device and existing Linux/AOSP resources; record missing prerequisites.

## B02

**Bounded deterministic core and state model** · G1 · Owner: proposed maintainer · Status: planned.

Dependencies: B01. Evidence: none; next step is dependency resolution and authorized implementation.

- [ ] Independent digest oracle matches generated fixtures.
- [ ] One worker, queue/session/retention byte and entry caps hold at boundaries.
- [ ] Owner-scoped states, cancellation race and round-robin admission tests exercise invariants.

## B03

**Real Android Binder broker and Kotlin SDK** · G1 · Owner: proposed maintainer · Status: planned.

Dependencies: B02. Evidence: none; next step is dependency resolution and authorized implementation.

- [ ] Separately installed service and client cross real Binder with captured caller identity.
- [ ] No work on UI/Binder ingress; bounded polling SDK and connection epoch behave as specified.
- [ ] Broker process loss yields explicit lost/unknown; reconnect does not claim job restoration.

## B04

**Native consumer, denial client and fault smoke** · G1 · Owner: proposed maintainer · Status: planned.

Dependencies: B03. Evidence: none; next step is dependency resolution and authorized implementation.

- [ ] Known digest, cancellation and real broker-death/rebind path execute with raw receipts.
- [ ] Wrong-signature client and cross-owner read/cancel are rejected.
- [ ] Trace and process receipts identify actual packages/PIDs; fault hooks isolated from release.

## B05

**Flutter plugin and second installed consumer** · G1 · Owner: proposed maintainer · Status: planned.

Dependencies: B04. Evidence: none; next step is dependency resolution and authorized implementation.

- [ ] Dart calls authored native adapter, Kotlin SDK and separate broker; vector matches native consumer.
- [ ] Cancellation, timeout semantics, broker loss and engine disposal observed end to end.
- [ ] Capture a short G1 demo and disclose any remaining gate failure.

## B06

**Deterministic fault, ownership and overload campaign** · G2 · Owner: proposed maintainer · Status: planned.

Dependencies: B05. Evidence: none; next step is dependency resolution and authorized implementation.

- [ ] Checkpoint-based faults cover death, churn, cancellation, malformed input and denied owners.
- [ ] Required corpus targets 100 repetitions per cell; record actual failures and counts.
- [ ] 30-minute saturation/churn retains bounded state, second-caller progress and no false success.

## B07

**macOS JVM host and CLI conformance** · G2 · Owner: proposed maintainer · Status: planned.

Dependencies: B05. Evidence: none; next step is dependency resolution and authorized implementation.

- [ ] Two actual macOS processes use bounded framed Unix sockets and pure Kotlin core.
- [ ] Private runtime-directory ownership/permissions, framing/deadline failures and same-user trust documented.
- [ ] Same semantic vectors pass on Android and PC; no emulator-as-desktop substitution.

## B08

**Compiled compatibility fixtures and quality lanes** · G2 · Owner: proposed maintainer · Status: planned.

Dependencies: B06, B07. Evidence: none; next step is dependency resolution and authorized implementation.

- [ ] Previously compiled consumers test supported provider evolution and major rejection.
- [ ] Add meaningful core, Android instrumentation and macOS conformance checks with pinned tools.
- [ ] Release inspection excludes kill/stall controls; build results and native results remain distinct.

## B09

**Controlled startup, memory and load experiment** · G2 · Owner: proposed maintainer · Status: planned.

Dependencies: B08. Evidence: none; next step is dependency resolution and authorized implementation.

- [ ] Freeze baselines, reference physical phone, pilot, thermal/compilation rules and decision thresholds.
- [ ] Retain startup/steady-state sessions, all outcomes, raw traces, exclusions and analysis script.
- [ ] Separate framework, IPC, queue, computation, polling and memory accounting; report uncertainty.

## B10

**Fresh consumer, release rehearsal and maintainer docs** · G2 · Owner: proposed maintainer · Status: planned.

Dependencies: B09. Evidence: none; next step is dependency resolution and authorized implementation.

- [ ] Fresh clone/separately built same-publisher consumer uses documented artifacts without secret setup.
- [ ] Provenance, dependency/license inventory, checksums, support limits and upgrade instructions complete.
- [ ] G2 acceptance ledger, tagged candidate recipe and five-minute demonstration reflect actual results.

## B11

**Technology assessment and evidence case study** · G2 · Owner: proposed maintainer · Status: planned.

Dependencies: B10. Evidence: none; next step is dependency resolution and authorized implementation.

- [ ] Dated source-backed Binder/Messenger, framework and deployment comparison records revisit triggers.
- [ ] Document one actually observed defect or bottleneck, competing explanations and regression/measurement.
- [ ] Publish negative or inconclusive finding honestly; do not invent a successful optimization.

## B12

**Linux/AOSP clean-image feasibility gate** · G3 · Owner: proposed maintainer · Status: planned.

Dependencies: B11. Evidence: none; next step is dependency resolution and authorized implementation.

- [ ] Use suitable existing authorized Linux resources; pin manifest and target.
- [ ] Build and boot unmodified baseline image with captured provenance before platform edits.
- [ ] If host/build fails, record infrastructure blocker and keep G3 open; no cloud purchase or device flashing.

## B13

**Image-integrated native Binder service** · G3 · Owner: proposed maintainer · Status: planned.

Dependencies: B12. Evidence: none; next step is dependency resolution and authorized implementation.

- [ ] Dedicated native service builds into pinned image with init registration and narrow tracked AIDL.
- [ ] Known synthetic operation conforms to golden vectors with bounded resources and explicit epochs.
- [ ] Permission and least-privilege SELinux policies work in enforcing mode; no hidden-API workaround.

## B14

**Platform-facing facade and executed integration tests** · G3 · Owner: proposed maintainer · Status: planned.

Dependencies: B13. Evidence: none; next step is dependency resolution and authorized implementation.

- [ ] Platform-built client/facade calls intended API; stock APK profile remains separately labeled.
- [ ] Boot registration, authorized/denied access, restart and interface compatibility pass on actual image.
- [ ] Retain image fingerprint, source/build logs, enforcing status and test records; source-only patch is incomplete.

## B15

**Deeper measured investigation and bounded soak** · G3 · Owner: proposed maintainer · Status: planned.

Dependencies: B14. Evidence: none; next step is dependency resolution and authorized implementation.

- [ ] Investigate real scheduling/memory/startup behavior from executed profiles; test a defensible change.
- [ ] Two-hour bounded campaign and any second-device observations identify precise scope.
- [ ] Publish tradeoff/negative result, failing evidence and protected-metric regression check.

## B16

**Reproduction, productization gaps and final handoff** · G3 · Owner: proposed maintainer · Status: planned.

Dependencies: B15. Evidence: none; next step is dependency resolution and authorized implementation.

- [ ] Fresh source/image reproduction recipe and final requirement ledger match executed artifacts.
- [ ] Record genuine independent reproduction/review only if a real volunteer participates; otherwise label author-only.
- [ ] Publish commercial/productization and remaining career/partner gaps; final demo never claims unbuilt support.
