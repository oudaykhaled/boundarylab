# Detailed implementation tickets

Revised 2026-10-30 by ten independent Astra planning reviews and root synthesis. [Plan v2](../docs/PLAN-V2.md), [selected file contract](../docs/FIELDPACK.md), [review records](reviews-v2/README.md), [machine-readable tickets](tickets-v2.json), [live board](https://github.com/users/oudaykhaled/projects/3).

40 leaves; 17 epics. This table and `tickets-v2.json` preserve the original planning snapshot, when only REF01 was verified. **Current execution, evidence and unmet acceptance are in [EXECUTION-STATUS.md](EXECUTION-STATUS.md) and [execution-status.json](execution-status.json).** Modules and scoped runtime receipts now exist. Estimates remain planning estimates; parent rollups are not additive. Original verification procedures remain labeled proposed rather than being silently relabeled as executed.

| Task | Parent | Stage | Priority | Effort | Prerequisites | State |
|---|---|---|---|---:|---|---|
| [C01](https://github.com/oudaykhaled/boundarylab/issues/18) Prove Android, Flutter and macOS toolchain feasibility | [B01](https://github.com/oudaykhaled/boundarylab/issues/1) | G1 | P0 | 4–8h | None | planned |
| [C02](https://github.com/oudaykhaled/boundarylab/issues/19) Freeze volatile job contract, caps and ownership semantics | [B01](https://github.com/oudaykhaled/boundarylab/issues/1) | G1 | P0 | 6–10h | None | planned |
| [C03](https://github.com/oudaykhaled/boundarylab/issues/22) Implement owner-scoped state machine, quotas and fairness | [B02](https://github.com/oudaykhaled/boundarylab/issues/2) | G1 | P0 | 8–16h | [C02](https://github.com/oudaykhaled/boundarylab/issues/19) | planned |
| [C04](https://github.com/oudaykhaled/boundarylab/issues/25) Implement bounded generated and positional-source digest engine | [B02](https://github.com/oudaykhaled/boundarylab/issues/2) | G1 | P0 | 6–12h | [C03](https://github.com/oudaykhaled/boundarylab/issues/22) | planned |
| [A01](https://github.com/oudaykhaled/boundarylab/issues/26) Implement same-publisher cross-package Binder service | [B03](https://github.com/oudaykhaled/boundarylab/issues/3) | G1 | P0 | 8–16h | [C01](https://github.com/oudaykhaled/boundarylab/issues/18), [C03](https://github.com/oudaykhaled/boundarylab/issues/22) | planned |
| [A02](https://github.com/oudaykhaled/boundarylab/issues/30) Implement Kotlin SDK generation, polling and loss behavior | [B03](https://github.com/oudaykhaled/boundarylab/issues/3) | G1 | P0 | 8–14h | [A01](https://github.com/oudaykhaled/boundarylab/issues/26), [C04](https://github.com/oudaykhaled/boundarylab/issues/25) | planned |
| [A03](https://github.com/oudaykhaled/boundarylab/issues/33) Build native generated-work consumer and denial smoke suite | [B04](https://github.com/oudaykhaled/boundarylab/issues/4) | G1 | P0 | 6–10h | [A02](https://github.com/oudaykhaled/boundarylab/issues/30) | planned |
| [F01](https://github.com/oudaykhaled/boundarylab/issues/34) Implement authored Flutter plugin and generated-work consumer | [B05](https://github.com/oudaykhaled/boundarylab/issues/5) | G1 | P0 | 8–16h | [A02](https://github.com/oudaykhaled/boundarylab/issues/30) | planned |
| [F02](https://github.com/oudaykhaled/boundarylab/issues/39) Verify Flutter engine lifecycle and second-engine isolation | [B05](https://github.com/oudaykhaled/boundarylab/issues/5) | G1 | P0 | 6–12h | [F01](https://github.com/oudaykhaled/boundarylab/issues/34) | planned |
| [Q01](https://github.com/oudaykhaled/boundarylab/issues/35) Create deterministic fault hooks and regression corpus | [B06](https://github.com/oudaykhaled/boundarylab/issues/6) | G2 | P1 | 8–14h | [A02](https://github.com/oudaykhaled/boundarylab/issues/30), [C04](https://github.com/oudaykhaled/boundarylab/issues/25) | planned |
| [Q02](https://github.com/oudaykhaled/boundarylab/issues/42) Execute descriptor cleanup and bounded saturation campaign | [B06](https://github.com/oudaykhaled/boundarylab/issues/6) | G2 | P1 | 8–16h | [Q01](https://github.com/oudaykhaled/boundarylab/issues/35), [FP02](https://github.com/oudaykhaled/boundarylab/issues/38), [F02](https://github.com/oudaykhaled/boundarylab/issues/39) | planned |
| [Q03](https://github.com/oudaykhaled/boundarylab/issues/43) Audit manifest, trust boundaries and public evidence privacy | [B06](https://github.com/oudaykhaled/boundarylab/issues/6) | G2 | P1 | 4–8h | [FP01](https://github.com/oudaykhaled/boundarylab/issues/24), [FP02](https://github.com/oudaykhaled/boundarylab/issues/38), [D01](https://github.com/oudaykhaled/boundarylab/issues/31), [REF01](https://github.com/oudaykhaled/boundarylab/issues/21), [FP05](https://github.com/oudaykhaled/boundarylab/issues/41) | planned |
| [D01](https://github.com/oudaykhaled/boundarylab/issues/31) Implement bounded macOS JVM host and connection-owned sessions | [B07](https://github.com/oudaykhaled/boundarylab/issues/7) | G2 | P1 | 8–16h | [C01](https://github.com/oudaykhaled/boundarylab/issues/18), [C04](https://github.com/oudaykhaled/boundarylab/issues/25) | planned |
| [D02](https://github.com/oudaykhaled/boundarylab/issues/36) Implement macOS CLI and generated-profile parity corpus | [B07](https://github.com/oudaykhaled/boundarylab/issues/7) | G2 | P1 | 6–10h | [D01](https://github.com/oudaykhaled/boundarylab/issues/31), [A02](https://github.com/oudaykhaled/boundarylab/issues/30) | planned |
| [V01](https://github.com/oudaykhaled/boundarylab/issues/44) Retain compiled old clients and test supported version evolution | [B08](https://github.com/oudaykhaled/boundarylab/issues/8) | G2 | P1 | 6–12h | [A02](https://github.com/oudaykhaled/boundarylab/issues/30), [F01](https://github.com/oudaykhaled/boundarylab/issues/34), [D01](https://github.com/oudaykhaled/boundarylab/issues/31), [FP02](https://github.com/oudaykhaled/boundarylab/issues/38), [FP05](https://github.com/oudaykhaled/boundarylab/issues/41) | planned |
| [V02](https://github.com/oudaykhaled/boundarylab/issues/46) Create pinned quality lanes and meaningful regression gates | [B08](https://github.com/oudaykhaled/boundarylab/issues/8) | G2 | P1 | 6–12h | [C04](https://github.com/oudaykhaled/boundarylab/issues/25), [A03](https://github.com/oudaykhaled/boundarylab/issues/33), [F01](https://github.com/oudaykhaled/boundarylab/issues/34), [D02](https://github.com/oudaykhaled/boundarylab/issues/36), [V01](https://github.com/oudaykhaled/boundarylab/issues/44) | planned |
| [M01](https://github.com/oudaykhaled/boundarylab/issues/27) Preregister fair benchmark hypotheses and bounded experiment matrix | [B09](https://github.com/oudaykhaled/boundarylab/issues/9) | G2 | P1 | 4–8h | [C02](https://github.com/oudaykhaled/boundarylab/issues/19), [FP01](https://github.com/oudaykhaled/boundarylab/issues/24) | planned |
| [M02](https://github.com/oudaykhaled/boundarylab/issues/47) Instrument equal baselines and stage timing without private data | [B09](https://github.com/oudaykhaled/boundarylab/issues/9) | G2 | P1 | 8–16h | [M01](https://github.com/oudaykhaled/boundarylab/issues/27), [FP03](https://github.com/oudaykhaled/boundarylab/issues/40), [FP04](https://github.com/oudaykhaled/boundarylab/issues/45) | planned |
| [M03](https://github.com/oudaykhaled/boundarylab/issues/50) Execute physical-device startup, file, load and memory campaign | [B09](https://github.com/oudaykhaled/boundarylab/issues/9) | G2 | P1 | 10–16h | [M02](https://github.com/oudaykhaled/boundarylab/issues/47), [Q02](https://github.com/oudaykhaled/boundarylab/issues/42) | planned |
| [M04](https://github.com/oudaykhaled/boundarylab/issues/54) Publish reproducible analysis with uncertainty and protected metrics | [B09](https://github.com/oudaykhaled/boundarylab/issues/9) | G2 | P1 | 6–10h | [M03](https://github.com/oudaykhaled/boundarylab/issues/50) | planned |
| [L01](https://github.com/oudaykhaled/boundarylab/issues/51) Rehearse fresh same-publisher consumer and release artifacts | [B10](https://github.com/oudaykhaled/boundarylab/issues/10) | G2 | P1 | 6–12h | [V02](https://github.com/oudaykhaled/boundarylab/issues/46), [FP06](https://github.com/oudaykhaled/boundarylab/issues/49), [Q03](https://github.com/oudaykhaled/boundarylab/issues/43) | planned |
| [L02](https://github.com/oudaykhaled/boundarylab/issues/56) Publish auditable requirement ledger and five-minute FieldPack demo | [B10](https://github.com/oudaykhaled/boundarylab/issues/10) | G2 | P1 | 4–8h | [L01](https://github.com/oudaykhaled/boundarylab/issues/51), [M04](https://github.com/oudaykhaled/boundarylab/issues/54), [T02](https://github.com/oudaykhaled/boundarylab/issues/55) | planned |
| [T01](https://github.com/oudaykhaled/boundarylab/issues/28) Assess technology alternatives and architectural revisit triggers | [B11](https://github.com/oudaykhaled/boundarylab/issues/11) | G2 | P1 | 4–8h | [C02](https://github.com/oudaykhaled/boundarylab/issues/19), [FP01](https://github.com/oudaykhaled/boundarylab/issues/24) | planned |
| [T02](https://github.com/oudaykhaled/boundarylab/issues/55) Document one observed difficult defect or bottleneck investigation | [B11](https://github.com/oudaykhaled/boundarylab/issues/11) | G2 | P1 | 4–8h | [M04](https://github.com/oudaykhaled/boundarylab/issues/54), [T01](https://github.com/oudaykhaled/boundarylab/issues/28) | planned |
| [P01](https://github.com/oudaykhaled/boundarylab/issues/20) Audit authorized Linux/AOSP resources and pin baseline target | [B12](https://github.com/oudaykhaled/boundarylab/issues/12) | G3 | P2 | 4–8h | None | planned |
| [P02](https://github.com/oudaykhaled/boundarylab/issues/23) Build and boot unchanged AOSP baseline image | [B12](https://github.com/oudaykhaled/boundarylab/issues/12) | G3 | P2 | 8–16h | [P01](https://github.com/oudaykhaled/boundarylab/issues/20) | planned |
| [P03](https://github.com/oudaykhaled/boundarylab/issues/29) Implement generated-only native AIDL service and core vectors | [B13](https://github.com/oudaykhaled/boundarylab/issues/13) | G3 | P2 | 8–16h | [P02](https://github.com/oudaykhaled/boundarylab/issues/23), [C02](https://github.com/oudaykhaled/boundarylab/issues/19) | planned |
| [P04](https://github.com/oudaykhaled/boundarylab/issues/32) Integrate service boot, supervision and enforcing SELinux policy | [B13](https://github.com/oudaykhaled/boundarylab/issues/13) | G3 | P2 | 8–16h | [P03](https://github.com/oudaykhaled/boundarylab/issues/29) | planned |
| [P05](https://github.com/oudaykhaled/boundarylab/issues/37) Implement platform-built facade and deliberate API client | [B14](https://github.com/oudaykhaled/boundarylab/issues/14) | G3 | P2 | 8–16h | [P04](https://github.com/oudaykhaled/boundarylab/issues/32) | planned |
| [P06](https://github.com/oudaykhaled/boundarylab/issues/48) Execute platform boot, restart and interface-compatibility corpus | [B14](https://github.com/oudaykhaled/boundarylab/issues/14) | G3 | P2 | 6–12h | [P05](https://github.com/oudaykhaled/boundarylab/issues/37), [V01](https://github.com/oudaykhaled/boundarylab/issues/44) | planned |
| [P07](https://github.com/oudaykhaled/boundarylab/issues/52) Investigate platform scheduling or memory with a controlled experiment | [B15](https://github.com/oudaykhaled/boundarylab/issues/15) | G3 | P2 | 8–16h | [P06](https://github.com/oudaykhaled/boundarylab/issues/48), [M01](https://github.com/oudaykhaled/boundarylab/issues/27) | planned |
| [P08](https://github.com/oudaykhaled/boundarylab/issues/53) Run two-hour bounded generated-profile platform soak | [B15](https://github.com/oudaykhaled/boundarylab/issues/15) | G3 | P2 | 4–8h | [P06](https://github.com/oudaykhaled/boundarylab/issues/48) | planned |
| [P09](https://github.com/oudaykhaled/boundarylab/issues/57) Reproduce final image and reconcile platform productization gaps | [B16](https://github.com/oudaykhaled/boundarylab/issues/16) | G3 | P2 | 8–16h | [P07](https://github.com/oudaykhaled/boundarylab/issues/52), [P08](https://github.com/oudaykhaled/boundarylab/issues/53), [L02](https://github.com/oudaykhaled/boundarylab/issues/56) | planned |
| [FP01](https://github.com/oudaykhaled/boundarylab/issues/24) Freeze FieldPack manifest, file profile and shared negative vectors | [B17](https://github.com/oudaykhaled/boundarylab/issues/17) | G1 | P0 | 4–8h | [C02](https://github.com/oudaykhaled/boundarylab/issues/19) | planned |
| [REF01](https://github.com/oudaykhaled/boundarylab/issues/21) Deliver runnable Python FieldPack reference, tests, fixtures and walkthrough | [B17](https://github.com/oudaykhaled/boundarylab/issues/17) | reference | P0 | 6–12h | None | verified |
| [FP02](https://github.com/oudaykhaled/boundarylab/issues/38) Implement Android regular-file capability and descriptor ownership | [B17](https://github.com/oudaykhaled/boundarylab/issues/17) | G2 | P1 | 8–16h | [FP01](https://github.com/oudaykhaled/boundarylab/issues/24), [A02](https://github.com/oudaykhaled/boundarylab/issues/30), [C04](https://github.com/oudaykhaled/boundarylab/issues/25) | planned |
| [FP03](https://github.com/oudaykhaled/boundarylab/issues/40) Implement native FieldPack selection, assembly and explicit export | [B17](https://github.com/oudaykhaled/boundarylab/issues/17) | G2 | P1 | 8–16h | [FP02](https://github.com/oudaykhaled/boundarylab/issues/38), [A03](https://github.com/oudaykhaled/boundarylab/issues/33) | planned |
| [FP04](https://github.com/oudaykhaled/boundarylab/issues/45) Implement Flutter real-file workflow through native broker adapter | [B17](https://github.com/oudaykhaled/boundarylab/issues/17) | G2 | P1 | 8–16h | [FP02](https://github.com/oudaykhaled/boundarylab/issues/38), [F02](https://github.com/oudaykhaled/boundarylab/issues/39) | planned |
| [FP05](https://github.com/oudaykhaled/boundarylab/issues/41) Implement macOS rooted-file host capability and JVM recipient verifier | [B17](https://github.com/oudaykhaled/boundarylab/issues/17) | G2 | P1 | 8–16h | [FP01](https://github.com/oudaykhaled/boundarylab/issues/24), [D02](https://github.com/oudaykhaled/boundarylab/issues/36) | planned |
| [FP06](https://github.com/oudaykhaled/boundarylab/issues/49) Verify manual Android-to-PC FieldPack handoff and failure diagnosis | [B17](https://github.com/oudaykhaled/boundarylab/issues/17) | G2 | P1 | 4–8h | [FP03](https://github.com/oudaykhaled/boundarylab/issues/40), [FP04](https://github.com/oudaykhaled/boundarylab/issues/45), [FP05](https://github.com/oudaykhaled/boundarylab/issues/41), [REF01](https://github.com/oudaykhaled/boundarylab/issues/21) | planned |

## C01 — Prove Android, Flutter and macOS toolchain feasibility

Avoid committing to targets that cannot execute.

[GitHub ticket](https://github.com/oudaykhaled/boundarylab/issues/18) · Proposed role: Android/Flutter/JVM engineer · 4–8h · Requirements R03, R14, R15, R19.

Scope:

- Minimal clean build/install/run probes
- Toolchain and physical measurement-device inventory

Acceptance:

- [ ] Pin actual Android SDK, JDK, Flutter and host versions.
- [ ] Separate broker/client APK probes run with distinct PIDs.
- [ ] Flutter probe executes authored native channel code on Android.
- [ ] Two macOS JVM processes exchange one bounded Unix-socket frame.
- [ ] Record missing physical phone or host capabilities as blockers; tool installation alone is not success.

Verification:

- **Proposed**: Execute clean probe builds and capture install/run logs and process receipts. Expected: All acceptance cases pass; retain actual counts, failures, environment and commit. No skipped native test counts as passing.
- **Proposed**:  Expected: Commands execute the stated acceptance corpus on actual required target and return success; retain complete counts and negative-case assertions, not only build exit status.

Evidence: `docs/evidence/C01/README.md`, `docs/evidence/C01/results.json`.

Risks: Allocated devices may be unavailable; do not substitute emulator results for physical performance.

## C02 — Freeze volatile job contract, caps and ownership semantics

Give clients predictable failure and resource semantics.

[GitHub ticket](https://github.com/oudaykhaled/boundarylab/issues/19) · Proposed role: Middleware architect · 6–10h · Requirements R01, R07, R08, R10, R16.

Scope:

- Protocol 1.0 and generated/file operation IDs
- Versioned errors, epochs, ownership and race vectors

Acceptance:

- [ ] Freeze 4KiB descriptor metadata, 16KiB response/frame, 16 sessions, four outstanding/session, 16 queued plus one running.
- [ ] Freeze 64 terminal entries, 60s TTL and 1MiB encoded retention; caps include rejection behavior and checked arithmetic.
- [ ] Define fresh UUID epoch, owner/session/request dedup and STALE_EPOCH/EXPIRED_OR_UNKNOWN responses.
- [ ] Running cancel reports requested state until first terminal commit wins; late success cannot overwrite terminal state.
- [ ] File duplicate with same operation/metadata returns original retained job and closes new FD; differing metadata conflicts; changed source requires new request ID.
- [ ] Timeout is unknown until queried; broker death is LOST_OR_UNKNOWN; reconnect never restores/replays work.
- [ ] Define monotonic 1–120000ms deadline, default 30000ms, and SDK polling at least 250ms with one outstanding poll.

Verification:

- **Proposed**: Review state/ownership tables using both orders of completion/cancel/deadline races. Contract completion requires frozen documents/expected vectors; downstream native execution remains in implementation tickets. Expected: All acceptance cases pass; retain actual counts, failures, environment and commit. No skipped native test counts as passing.
- **Proposed**: Run contract vectors for cap boundaries, expired dedup and stale epochs once harness exists. Expected: All acceptance cases pass; retain actual counts, failures, environment and commit. No skipped native test counts as passing.

Evidence: `docs/evidence/C02/README.md`, `docs/evidence/C02/results.json`.

Risks: Logical cancellation is not proof a blocked OS read exited. Metadata byte accounting excludes JVM object/runtime overhead.

## C03 — Implement owner-scoped state machine, quotas and fairness

Prevent incorrect states and noisy-client resource exhaustion.

[GitHub ticket](https://github.com/oudaykhaled/boundarylab/issues/22) · Proposed role: Kotlin core engineer · 8–16h · Requirements R05, R07, R08, R10.

Scope:

- Pure Kotlin policy core
- Deterministic scheduler and retention accounting

Acceptance:

- [ ] Core imports no Android/UI/transport APIs.
- [ ] All status/cancel operations require owner, session and epoch match.
- [ ] Queued cancel does zero reads and removes work; running races commit one terminal result.
- [ ] Round robin chooses one queued job per active session turn without preempting running work.
- [ ] Cap−1/cap/cap+1 and checked-overflow cases enforce session, queue, retention bytes/entries and TTL.
- [ ] Expired results never restart work; same-key conflict and bounded dedup follow C02.

Verification:

- **Proposed**: Proposed ./gradlew :core:test with virtual-clock, seeded-event and deterministic barrier tests. Expected: All acceptance cases pass; retain actual counts, failures, environment and commit. No skipped native test counts as passing.
- **Proposed**:  Expected: Commands execute the stated acceptance corpus on actual required target and return success; retain complete counts and negative-case assertions, not only build exit status.

Evidence: `docs/evidence/C03/README.md`, `docs/evidence/C03/results.json`.

Risks: Fairness applies at job boundaries, not a hard latency guarantee for a blocked worker.

## C04 — Implement bounded generated and positional-source digest engine

Hash large inputs without retaining whole payloads.

[GitHub ticket](https://github.com/oudaykhaled/boundarylab/issues/25) · Proposed role: Kotlin core engineer · 6–12h · Requirements R04, R05, R07, R08.

Scope:

- Platform-neutral size/readAt/close source
- Generated-sha256-v1 and digest oracle vectors

Acceptance:

- [ ] One worker uses a reusable 65536-byte buffer; no whole-file allocation/mmap.
- [ ] Zero, one, chunk−1/chunk/chunk+1 and 128MiB vectors match independent SHA-256 oracle.
- [ ] 128MiB+1, negative and overflow lengths fail before unbounded work.
- [ ] Short reads, premature EOF and injected I/O error cannot yield success.
- [ ] Cancel/deadline checkpoints submit terminal requests to state owner; blocked read does not free physical slot.
- [ ] Source closes exactly once after actual worker exit; counters separate queued/running/retained resources.

Verification:

- **Proposed**: Proposed ./gradlew :core:test with counting sources, short-read fixtures, faults and resource assertions. Expected: All acceptance cases pass; retain actual counts, failures, environment and commit. No skipped native test counts as passing.
- **Proposed**:  Expected: Commands execute the stated acceptance corpus on actual required target and return success; retain complete counts and negative-case assertions, not only build exit status.

Evidence: `docs/evidence/C04/README.md`, `docs/evidence/C04/results.json`.

Risks: Fake sources validate policy, not OS cancellation guarantees.

## A01 — Implement same-publisher cross-package Binder service

Expose a real process-isolated service with trustworthy caller identity.

[GitHub ticket](https://github.com/oudaykhaled/boundarylab/issues/26) · Proposed role: Android IPC engineer · 8–16h · Requirements R14, R16, R07.

Scope:

- AIDL service and permission
- Session death token and per-method guards

Acceptance:

- [ ] Broker and native consumer install as distinct packages and PIDs.
- [ ] Wrong-signature client cannot bind; same-signature different UID cannot use another owner session.
- [ ] Capture Binder UID/user synchronously before dispatch; parcel package/PID is not authority.
- [ ] Each operation validates owner/session/epoch; passing another Binder reference does not bypass guards.
- [ ] Ingress performs bounded metadata/admission work; hashing never runs on Binder or UI thread.
- [ ] Client-owned session token death unregisters session; link/death/admission races have deterministic outcomes.

Verification:

- **Proposed**: Proposed ./gradlew :android:broker:connectedCheck using approved and denial packages and real IPC. Expected: All acceptance cases pass; retain actual counts, failures, environment and commit. No skipped native test counts as passing.
- **Proposed**:  Expected: Commands execute the stated acceptance corpus on actual required target and return success; retain complete counts and negative-case assertions, not only build exit status.

Evidence: `docs/evidence/A01/README.md`, `docs/evidence/A01/results.json`.

Risks: In-process mocks are insufficient IPC evidence. No arbitrary third-party onboarding.

## A02 — Implement Kotlin SDK generation, polling and loss behavior

Applications distinguish uncertain/lost work from completed work.

[GitHub ticket](https://github.com/oudaykhaled/boundarylab/issues/30) · Proposed role: Android SDK engineer · 8–14h · Requirements R07, R14, R16.

Scope:

- Typed asynchronous SDK
- Bounded polling and demand-owned reconnect

Acceptance:

- [ ] No blocking IPC on UI thread.
- [ ] Poll interval/request concurrency obey C02 and stop after terminal/session close.
- [ ] Disconnection and death merge into generation-aware recovery without duplicate sessions.
- [ ] Old-generation callback cannot update fresh state; onBindingDied/null binding are explicit.
- [ ] Timeout never auto-resubmits; status-by-request resolves ambiguity while original session lives.
- [ ] Broker kill yields LOST_OR_UNKNOWN, fresh epoch invalidates old handles and resubmission is explicit new work.
- [ ] Retry budget applies only while foreground demand exists.

Verification:

- **Proposed**: Execute instrumented broker kill, lost reply, stale callback and foreground-demand checkpoint cases. Expected: All acceptance cases pass; retain actual counts, failures, environment and commit. No skipped native test counts as passing.
- **Proposed**:  Expected: Commands execute the stated acceptance corpus on actual required target and return success; retain complete counts and negative-case assertions, not only build exit status.

Evidence: `docs/evidence/A02/README.md`, `docs/evidence/A02/results.json`.

Risks: Reconnect availability is not durable job recovery.

## A03 — Build native generated-work consumer and denial smoke suite

Provide an inspectable native baseline and security smoke path.

[GitHub ticket](https://github.com/oudaykhaled/boundarylab/issues/33) · Proposed role: Android application/QA engineer · 6–10h · Requirements R02, R07, R11.

Scope:

- Submit/status/cancel UI
- Independent denial client and release fault-hook inspection

Acceptance:

- [ ] Known generated digest traverses real Binder and matches oracle.
- [ ] Queue saturation presents BUSY without UI freeze.
- [ ] Wrong-signature binding and cross-owner status/cancel fail with expected outcomes.
- [ ] Kill/rebind path shows LOST_OR_UNKNOWN and explicit new request.
- [ ] Recording identifies actual packages/PIDs without private device information.
- [ ] Release package excludes kill/stall hooks and test signing private material.

Verification:

- **Proposed**: Run native smoke scenarios and inspect release APK manifest/classes for debug hooks. Expected: All acceptance cases pass; retain actual counts, failures, environment and commit. No skipped native test counts as passing.
- **Proposed**:  Expected: Commands execute the stated acceptance corpus on actual required target and return success; retain complete counts and negative-case assertions, not only build exit status.

Evidence: `docs/evidence/A03/README.md`, `docs/evidence/A03/results.json`.

Risks: A screenshot is not proof of cross-process execution.

## F01 — Implement authored Flutter plugin and generated-work consumer

Let a second ecosystem use the same broker semantics.

[GitHub ticket](https://github.com/oudaykhaled/boundarylab/issues/34) · Proposed role: Flutter/Android engineer · 8–16h · Requirements R02, R15.

Scope:

- Dart typed facade
- Native adapter to Kotlin SDK

Acceptance:

- [ ] Installed Flutter client calls authored plugin then SDK then separate broker.
- [ ] Dart contains no duplicate scheduler/hash policy and no synthetic success shortcut.
- [ ] Digest and error vectors match native baseline.
- [ ] Cancellation request, timeout and broker loss are distinct user-visible outcomes.
- [ ] Native channel work obeys platform threading rules; heavy calls do not block UI.
- [ ] Status polling respects SDK caps rather than stacking a Dart polling loop.

Verification:

- **Proposed**: Proposed flutter test plus installed-device integration test with process traces. Expected: All acceptance cases pass; retain actual counts, failures, environment and commit. No skipped native test counts as passing.
- **Proposed**:  Expected: Commands execute the stated acceptance corpus on actual required target and return success; retain complete counts and negative-case assertions, not only build exit status.

Evidence: `docs/evidence/F01/README.md`, `docs/evidence/F01/results.json`.

Risks: Mock channel tests alone do not prove Binder execution.

## F02 — Verify Flutter engine lifecycle and second-engine isolation

Prevent leaked sessions and stale UI results during lifecycle changes.

[GitHub ticket](https://github.com/oudaykhaled/boundarylab/issues/39) · Proposed role: Flutter QA engineer · 6–12h · Requirements R07, R15, R18.

Scope:

- Engine-owned plugin state
- Recreation, disposal and concurrent engine tests

Acceptance:

- [ ] Engine disposal stops demand and closes only its own session.
- [ ] Activity recreation does not accidentally create/replay jobs.
- [ ] Old-generation events cannot update disposed/replaced engine.
- [ ] Second engine has independent session and cannot cancel/read first engine jobs.
- [ ] Delayed native completion after disposal is discarded with resources reclaimed according to worker state.
- [ ] Broker death during teardown yields no successful stale completion.

Verification:

- **Proposed**: Execute deterministic engine lifecycle tests plus real two-engine device scenario; retain counters. Expected: All acceptance cases pass; retain actual counts, failures, environment and commit. No skipped native test counts as passing.
- **Proposed**:  Expected: Commands execute the stated acceptance corpus on actual required target and return success; retain complete counts and negative-case assertions, not only build exit status.

Evidence: `docs/evidence/F02/README.md`, `docs/evidence/F02/results.json`.

Risks: Activity and Flutter-engine lifetimes are different.

## Q01 — Create deterministic fault hooks and regression corpus

Make difficult failures reproducible.

[GitHub ticket](https://github.com/oudaykhaled/boundarylab/issues/35) · Proposed role: Reliability QA engineer · 8–14h · Requirements R07, R11, R17.

Scope:

- Test-only barriers around admission/read/commit/reply
- Race and malformed-input corpus

Acceptance:

- [ ] Cover cancel before admission, queued cancel and both running cancel/completion orders.
- [ ] Cover deadline versus completion, session close versus admission and death before/after registration.
- [ ] Cover broker death before/after terminal commit and lost submit response.
- [ ] Malformed metadata, wrong owner and stale epoch never produce success.
- [ ] Blocked-reader test retains occupied worker/FD accounting; no unsafe thread kill or replacement-worker growth.
- [ ] Release configuration cannot enable fault hooks.

Verification:

- **Proposed**: Run barrier-controlled corpus with seed/checkpoint logs; inspect release artifact hook exclusion. Expected: All acceptance cases pass; retain actual counts, failures, environment and commit. No skipped native test counts as passing.
- **Proposed**:  Expected: Commands execute the stated acceptance corpus on actual required target and return success; retain complete counts and negative-case assertions, not only build exit status.

Evidence: `docs/evidence/Q01/README.md`, `docs/evidence/Q01/results.json`.

Risks: Injected reader stalls are labeled simulations and cannot prove provider behavior.

## Q02 — Execute descriptor cleanup and bounded saturation campaign

Establish bounded resource behavior under real contention.

[GitHub ticket](https://github.com/oudaykhaled/boundarylab/issues/42) · Proposed role: Android reliability QA · 8–16h · Requirements R05, R07, R08, R11, R17.

Scope:

- 100 repetitions per selected race cell
- 30-minute capped churn and rejection load

Acceptance:

- [ ] Record planned/actual counts and every failure; incomplete cells remain incomplete.
- [ ] Accepted job descriptors never exceed 17; report transient Binder FD headroom separately.
- [ ] Rejected/duplicate/queued-cancel FDs close; running descriptors close only after worker exits.
- [ ] 1000 rejected submissions and churn do not cause persistent owned-FD/session/retention growth after drain/TTL.
- [ ] Second caller progresses at job boundaries under noisy-caller load.
- [ ] No false success, implicit replay or stale-epoch result occurs across actual process death.
- [ ] Report offered/admitted/rejected/completed/cancelled/failed counts and logical caps.

Verification:

- **Proposed**: Run actual-package race/churn harness with FD/queue/session sampling and sanitized traces. Expected: All acceptance cases pass; retain actual counts, failures, environment and commit. No skipped native test counts as passing.
- **Proposed**:  Expected: Commands execute the stated acceptance corpus on actual required target and return success; retain complete counts and negative-case assertions, not only build exit status.

Evidence: `docs/evidence/Q02/README.md`, `docs/evidence/Q02/results.json`.

Risks: Total process FD count includes transport/runtime baseline; do not promise exactly 17 total FDs.

## Q03 — Audit manifest, trust boundaries and public evidence privacy

Catch unsafe trust claims and disclosure before release.

[GitHub ticket](https://github.com/oudaykhaled/boundarylab/issues/43) · Proposed role: Security reviewer · 4–8h · Requirements R07, R10, R11.

Scope:

- Threat model review
- Negative-control and evidence sanitization review

Acceptance:

- [ ] Wrong signature, cross-owner access and unsupported descriptors have executed denial evidence.
- [ ] Manifest aliases are not original provider names/URIs or absolute paths.
- [ ] Invalid manifest fails before any referenced attachment is opened.
- [ ] Traversal/symlink/nonregular cases cannot become verified matches under supported profile.
- [ ] Public logs use owned fixtures and omit file contents, device serials, credentials and private roots.
- [ ] Same-user filesystem races and mutable-source limitations are explicit; integrity is not authenticity.

Verification:

- **Proposed**: Review source/contracts and execute negative corpus on reference plus available native targets; clearly label unexecuted targets. Expected: All acceptance cases pass; retain actual counts, failures, environment and commit. No skipped native test counts as passing.

Evidence: `docs/evidence/Q03/README.md`, `docs/evidence/Q03/results.json`.

Risks: Author/AI review is not independent human security certification.

## D01 — Implement bounded macOS JVM host and connection-owned sessions

Run the same policy in a real PC process boundary.

[GitHub ticket](https://github.com/oudaykhaled/boundarylab/issues/31) · Proposed role: Kotlin/JVM engineer · 8–16h · Requirements R03, R07, R19.

Scope:

- Private Unix-socket runtime directory
- Framed protocol, deadlines and cleanup

Acceptance:

- [ ] Two actual macOS JVM processes exchange protocol requests.
- [ ] Private runtime-directory ownership and permissions are checked; no network listener/token published.
- [ ] Reject oversized frame length before allocation and truncated/malformed frames explicitly.
- [ ] Read/write deadlines and disconnect close connection-owned sessions.
- [ ] Same-user trust is documented; no unsupported peer-credential assertion.
- [ ] Generated semantic vectors and epoch restart behavior match contract.

Verification:

- **Proposed**: Run macOS subprocess conformance for frames, permissions, disconnect and restart. Expected: All acceptance cases pass; retain actual counts, failures, environment and commit. No skipped native test counts as passing.
- **Proposed**:  Expected: Commands execute the stated acceptance corpus on actual required target and return success; retain complete counts and negative-case assertions, not only build exit status.

Evidence: `docs/evidence/D01/README.md`, `docs/evidence/D01/results.json`.

Risks: JDK socket support is proven on selected host; Android emulator does not satisfy PC profile.

## D02 — Implement macOS CLI and generated-profile parity corpus

Demonstrate matching behavior across phone and PC.

[GitHub ticket](https://github.com/oudaykhaled/boundarylab/issues/36) · Proposed role: Kotlin/JVM engineer · 6–10h · Requirements R03, R10, R19.

Scope:

- CLI submit/status/cancel
- Shared semantic test runner

Acceptance:

- [ ] CLI drives a separate host process, not direct core calls.
- [ ] Same generated vectors pass on named Android and macOS environments.
- [ ] Caps, owner/session isolation, cancellation and stale epoch cases report equivalent semantics.
- [ ] Platform-specific identity/lifetime differences appear in support matrix.
- [ ] Unknown capability/version produces typed rejection rather than silent fallback.
- [ ] Run manifest identifies source commit and actual host/tool versions.

Verification:

- **Proposed**: Execute shared conformance corpus through both real transports and compare result classifications. Expected: All acceptance cases pass; retain actual counts, failures, environment and commit. No skipped native test counts as passing.
- **Proposed**:  Expected: Commands execute the stated acceptance corpus on actual required target and return success; retain complete counts and negative-case assertions, not only build exit status.

Evidence: `docs/evidence/D02/README.md`, `docs/evidence/D02/results.json`.

Risks: Sharing code is not runtime compatibility evidence.

## V01 — Retain compiled old clients and test supported version evolution

Prevent upgrades from silently breaking consumers.

[GitHub ticket](https://github.com/oudaykhaled/boundarylab/issues/44) · Proposed role: SDK compatibility engineer · 6–12h · Requirements R03, R07, R10.

Scope:

- Compiled client fixtures
- Generated/file capability evolution matrix

Acceptance:

- [ ] Old fixture binaries are archived with checksums and compiler versions, never rebuilt against new API during test.
- [ ] Old client/new provider and new client/old provider supported cells pass.
- [ ] Unsupported protocol major is rejected explicitly.
- [ ] Generated-only provider advertises file operation unsupported and rejects before work/FD retention.
- [ ] Additive change preserves agreed ABI; intentional incompatible AIDL change fails test.
- [ ] Unknown error handling does not turn failures into success.

Verification:

- **Proposed**: Run binary compatibility matrix against actual Android provider and macOS host. Expected: All acceptance cases pass; retain actual counts, failures, environment and commit. No skipped native test counts as passing.
- **Proposed**:  Expected: Commands execute the stated acceptance corpus on actual required target and return success; retain complete counts and negative-case assertions, not only build exit status.

Evidence: `docs/evidence/V01/README.md`, `docs/evidence/V01/results.json`.

Risks: A version field cannot repair incompatible AIDL layout.

## V02 — Create pinned quality lanes and meaningful regression gates

Make development standards executable.

[GitHub ticket](https://github.com/oudaykhaled/boundarylab/issues/46) · Proposed role: CI engineer · 6–12h · Requirements R10, R11.

Scope:

- Core/reference/Android/macOS CI lanes
- Artifact inspection and contribution checks

Acceptance:

- [ ] Pin toolchains and document cache versus clean build modes.
- [ ] Core and reference lanes execute semantic/negative tests.
- [ ] Android and macOS runtime lanes show explicit executed/skipped/blocked states.
- [ ] Deliberate contract/authorization regression causes relevant lane to fail.
- [ ] Release inspection rejects test fault controls and secrets.
- [ ] Build success, runtime success and reviewed status remain separate fields.

Verification:

- **Proposed**: Execute workflow lanes and controlled negative mutations; capture run URLs or local equivalent transcripts. Expected: All acceptance cases pass; retain actual counts, failures, environment and commit. No skipped native test counts as passing.
- **Proposed**:  Expected: Commands execute the stated acceptance corpus on actual required target and return success; retain complete counts and negative-case assertions, not only build exit status.

Evidence: `docs/evidence/V02/README.md`, `docs/evidence/V02/results.json`.

Risks: Hosted CI performance is not an authoritative phone benchmark.

## M01 — Preregister fair benchmark hypotheses and bounded experiment matrix

Ensure later measurements answer falsifiable questions.

[GitHub ticket](https://github.com/oudaykhaled/boundarylab/issues/27) · Proposed role: Performance engineer · 4–8h · Requirements R04, R05, R06, R08.

Scope:

- Physical device and baseline design
- Input cells, sample plan and exclusions

Acceptance:

- [ ] Declare physical phone or mark execution blocked; pin release/compilation/profile state.
- [ ] Define no-work shell and same-native-reader/hash in-process baselines for native and Flutter.
- [ ] Include 0,1KiB,1MiB,16MiB,128MiB and boundary-invalid files plus 32-file packs; invalid cells excluded from speed pools.
- [ ] Separate process cold/resident from prewarmed/uncontrolled filesystem cache; restart is not disk-cold.
- [ ] Freeze sample counts, randomized balanced order, thermal rules, protected metrics and hypotheses before confirmation.
- [ ] Calibrate targets after pilot; no universal throughput or measured gain asserted.
- [ ] Document device-hours and additional provider/device matrix as separately estimated scope.

Verification:

- **Proposed**: Review preregistration and run ten-pair pilot after instrumentation is available; no confirmatory conclusions from pilot. Expected: All acceptance cases pass; retain actual counts, failures, environment and commit. No skipped native test counts as passing.

Evidence: `docs/evidence/M01/README.md`, `docs/evidence/M01/results.json`.

Risks: Large matrices require reserved device time; old 120h target is superseded.

## M02 — Instrument equal baselines and stage timing without private data

Separate actual middleware overhead from file and UI delays.

[GitHub ticket](https://github.com/oudaykhaled/boundarylab/issues/47) · Proposed role: Android/Flutter performance engineer · 8–16h · Requirements R04, R05, R06.

Scope:

- Trace points and counters
- Native/Flutter in-process and broker variants

Acceptance:

- [ ] All variants use equal input, reader, chunk size, digest and export behavior.
- [ ] Trace launch/frame, provider open, descriptor handoff, admission, queue, read/hash, terminal, poll and export milestones.
- [ ] Poll observation delay is separate from control-RPC latency.
- [ ] FD handoff is not described as copying all file bytes through Binder.
- [ ] Correlation IDs and monotonic clock domains are documented; overlapping spans are not blindly summed.
- [ ] Trace contains owned aliases only; compare trace-on/off overhead and collect memory separately.
- [ ] Missing/overrun events invalidate attribution instead of becoming zero durations.

Verification:

- **Proposed**: Execute baseline parity check and trace extraction/counter consistency tests. Expected: All acceptance cases pass; retain actual counts, failures, environment and commit. No skipped native test counts as passing.
- **Proposed**:  Expected: Commands execute the stated acceptance corpus on actual required target and return success; retain complete counts and negative-case assertions, not only build exit status.

Evidence: `docs/evidence/M02/README.md`, `docs/evidence/M02/results.json`.

Risks: Instrumentation can perturb performance; low-overhead counters required for confirmation.

## M03 — Execute physical-device startup, file, load and memory campaign

Collect reproducible performance and resource observations.

[GitHub ticket](https://github.com/oudaykhaled/boundarylab/issues/50) · Proposed role: Performance QA engineer · 10–16h · Requirements R04, R05, R06, R07, R08.

Scope:

- Bounded campaign; active estimate excludes 12–24 reserved device-hours
- Separate pilot and confirmation

Acceptance:

- [ ] Record ten paired pilot trials separately.
- [ ] Run three sessions ×30 launches ×four framework/treatment combinations per declared startup state, or report exact incomplete cells.
- [ ] Run native file windows across three sizes and separately declared Flutter confirmation subset.
- [ ] Run three sessions ×five 60s windows ×four concurrency levels per treatment plus fixed-arrival overload.
- [ ] Run three 30-minute memory/churn sessions with all clients+broker PSS and logical counters.
- [ ] Preserve failures, exclusions, thermal/cache notes, actual sample counts and randomized block order.
- [ ] No native/desktop measurement is fabricated when hardware unavailable.

Verification:

- **Proposed**: Execute frozen M01 campaign runner, retain raw JSON/traces/environment and checksums. Expected: All acceptance cases pass; retain actual counts, failures, environment and commit. No skipped native test counts as passing.
- **Proposed**:  Expected: Commands execute the stated acceptance corpus on actual required target and return success; retain complete counts and negative-case assertions, not only build exit status.

Evidence: `docs/evidence/M03/README.md`, `docs/evidence/M03/results.json`.

Risks: Campaign may need re-estimation after pilot; device elapsed hours are additional to active labor.

## M04 — Publish reproducible analysis with uncertainty and protected metrics

Let reviewers regenerate conclusions from raw observations.

[GitHub ticket](https://github.com/oudaykhaled/boundarylab/issues/54) · Proposed role: Performance analyst · 6–10h · Requirements R04, R05, R06, R08, R11.

Scope:

- Analysis scripts and report
- Per-session distributions and resource accounting

Acceptance:

- [ ] Clean analysis command regenerates all tables from immutable raw data.
- [ ] Report counts, failures, medians/tails and clustered uncertainty without unsupported p99 claims.
- [ ] Separate queue, read wait, hash, IPC and polling; PSS and desktop RSS not ranked as equivalent.
- [ ] Logical resource counters return to defined drained baseline; PSS trends reported separately.
- [ ] Any proposed improvement fails acceptance if protected cancellation/memory metric regresses beyond frozen threshold.
- [ ] Negative/inconclusive outcomes remain visible with no claimed speedup.

Verification:

- **Proposed**: Run analysis from fresh environment and compare report checksums/tables to stored expected outputs. Expected: All acceptance cases pass; retain actual counts, failures, environment and commit. No skipped native test counts as passing.
- **Proposed**:  Expected: Commands execute the stated acceptance corpus on actual required target and return success; retain complete counts and negative-case assertions, not only build exit status.

Evidence: `docs/evidence/M04/README.md`, `docs/evidence/M04/results.json`.

Risks: Small independent-session count limits generalization.

## L01 — Rehearse fresh same-publisher consumer and release artifacts

Prove documented integration works outside the development workspace.

[GitHub ticket](https://github.com/oudaykhaled/boundarylab/issues/51) · Proposed role: Release/SDK engineer · 6–12h · Requirements R09, R11.

Scope:

- Clean consumer integration
- Provenance, license inventory and checksums

Acceptance:

- [ ] Fresh clone builds documented artifacts without private maintainer configuration.
- [ ] Separately built same-publisher consumer uses intended SDK and broker successfully.
- [ ] Local demo signing material generated outside repository; no reusable private keys published.
- [ ] License/dependency inventory, checksums, versions and upgrade instructions complete.
- [ ] Support matrix distinguishes reference, app, PC and conditional platform evidence.
- [ ] Release candidate recipe names remaining gates and never claims commercial shipment.

Verification:

- **Proposed**: Execute fresh-workspace recipe and artifact inspection; retain commands and environment. Expected: All acceptance cases pass; retain actual counts, failures, environment and commit. No skipped native test counts as passing.

Evidence: `docs/evidence/L01/README.md`, `docs/evidence/L01/results.json`.

Risks: Same-publisher integration is not third-party adoption or partner work.

## L02 — Publish auditable requirement ledger and five-minute FieldPack demo

Make architectural evidence easy for a skeptical panel to inspect.

[GitHub ticket](https://github.com/oudaykhaled/boundarylab/issues/56) · Proposed role: Architect/documentation writer · 4–8h · Requirements R01, R09, R18.

Scope:

- Demo script/recording
- R01–R22 claim ledger

Acceptance:

- [ ] Show real native/Flutter selection, broker failure, fresh attempt and explicit manifest export.
- [ ] Show recipient verification, changed file and missing file from separately supplied fixture directory.
- [ ] Each implemented claim links commit, command, environment and actual result.
- [ ] Python reference, app Binder, macOS JVM and AOSP evidence never substitute for each other.
- [ ] Mark R13/R20/R21/R22 external; R09 shipping and R18 real cross-team work remain separate career evidence.
- [ ] State no snapshot/authenticity/cloud-sync/exactly-once/kernel claim.
- [ ] Unbuilt optional platform portion stays planned in recording and ledger.

Verification:

- **Proposed**: Execute five-minute script from clean starting state and audit each claim link. Expected: All acceptance cases pass; retain actual counts, failures, environment and commit. No skipped native test counts as passing.

Evidence: `docs/evidence/L02/README.md`, `docs/evidence/L02/results.json`.

Risks: Polished demonstration must not hide failing required cells.

## T01 — Assess technology alternatives and architectural revisit triggers

Explain why selected boundaries fit the actual workflow.

[GitHub ticket](https://github.com/oudaykhaled/boundarylab/issues/28) · Proposed role: Software architect · 4–8h · Requirements R01, R12.

Scope:

- Dated primary-source comparison
- Binder/Messenger, Flutter alternatives, regular-file profile and daemon placement

Acceptance:

- [ ] Compare at least two realistic alternatives for each major selected boundary.
- [ ] Record user value, complexity/security tradeoffs and observable revisit trigger.
- [ ] Distinguish app AIDL from image-internal structured AIDL.
- [ ] Explain regular seekable input restriction and why streaming providers are deferred.
- [ ] Document macOS same-user boundary and why generic JDK FD passing is not assumed.
- [ ] Cite primary sources and dates; research exposure is not hands-on expertise.

Verification:

- **Proposed**: Review ADR against contract and current official platform documentation at implementation kickoff. Expected: All acceptance cases pass; retain actual counts, failures, environment and commit. No skipped native test counts as passing.

Evidence: `docs/evidence/T01/README.md`, `docs/evidence/T01/results.json`.

Risks: Post-hoc decisions must be labeled as reassessments.

## T02 — Document one observed difficult defect or bottleneck investigation

Show evidence-based engineering judgment.

[GitHub ticket](https://github.com/oudaykhaled/boundarylab/issues/55) · Proposed role: Architect/performance engineer · 4–8h · Requirements R04, R12, R17.

Scope:

- Actual failing observation
- Hypothesis, minimal experiment and outcome

Acceptance:

- [ ] Start from an actual recorded defect/bottleneck, not invented incident.
- [ ] List competing explanations and a discriminating test.
- [ ] Link minimal change or justified no-change decision to result.
- [ ] Retain failing evidence and regression/protected-metric check.
- [ ] Publish negative/inconclusive result honestly.
- [ ] Explain impact on roadmap and what remains unresolved.

Verification:

- **Proposed**: Reproduce case from stored artifacts and rerun discriminating/regression test. Expected: All acceptance cases pass; retain actual counts, failures, environment and commit. No skipped native test counts as passing.

Evidence: `docs/evidence/T02/README.md`, `docs/evidence/T02/results.json`.

Risks: No guaranteed optimization; defect absence must not trigger fabrication.

## P01 — Audit authorized Linux/AOSP resources and pin baseline target

Determine whether conditional platform work is feasible.

[GitHub ticket](https://github.com/oudaykhaled/boundarylab/issues/20) · Proposed role: AOSP platform engineer · 4–8h · Requirements R16.

Scope:

- Linux resource inventory
- Pinned manifest/target and build plan

Acceptance:

- [ ] Verify suitable existing authorized Linux host and virtualization resources.
- [ ] Record actual disk/RAM/toolchain prerequisites against selected AOSP documentation.
- [ ] Pin source manifest/target and active versus elapsed build-time budget.
- [ ] No cloud purchase, device flashing or new external deployment.
- [ ] Unavailable resources produce exact blocker/unblock condition; no fake Ready state.
- [ ] Scope explicitly generated-sha256-v1 only; real-file privileged ingestion deferred.

Verification:

- **Proposed**: Run documented resource capability checks and review pinned baseline plan. Expected: All acceptance cases pass; retain actual counts, failures, environment and commit. No skipped native test counts as passing.
- **Proposed**: On the P01-authorized Linux host, checkout the pinned manifest, select the exact P02 build target, build changed modules/image using its documented AOSP build recipe, boot that image in the recorded virtual target, wait for adb readiness, capture ro.build.fingerprint and getenforce, then run the ticket-specific authorized/denied/restart/corpus cases. Record source manifest, build output, boot logs and case results. P01 is an inventory-only exception; P02 exercises baseline only. Expected: Executed target matches pinned image and enforcing policy; source-only compilation never closes image runtime acceptance. P01/P02 prerequisite exclusions apply as specified in scope.

Evidence: `docs/evidence/P01/README.md`, `docs/evidence/P01/results.json`.

Risks: Current workstation readiness is not AOSP host readiness.

## P02 — Build and boot unchanged AOSP baseline image

Establish a known working image before modifications.

[GitHub ticket](https://github.com/oudaykhaled/boundarylab/issues/23) · Proposed role: AOSP platform engineer · 8–16h · Requirements R16, R19.

Scope:

- Clean baseline build/boot spike
- Provenance and test access

Acceptance:

- [ ] Build unmodified pinned manifest/target.
- [ ] Boot image and record fingerprint and host/tool versions.
- [ ] Test runner can reach target and execute baseline smoke.
- [ ] Preserve build/boot logs and artifact checksums.
- [ ] If timebox fails, record failure and re-estimate follow-up; image gate remains open.
- [ ] Do not claim physical phone performance from virtual target.

Verification:

- **Proposed**: Execute pinned AOSP build and virtual-image boot recipe; retain source/build/boot receipts. Expected: All acceptance cases pass; retain actual counts, failures, environment and commit. No skipped native test counts as passing.
- **Proposed**: On the P01-authorized Linux host, checkout the pinned manifest, select the exact P02 build target, build changed modules/image using its documented AOSP build recipe, boot that image in the recorded virtual target, wait for adb readiness, capture ro.build.fingerprint and getenforce, then run the ticket-specific authorized/denied/restart/corpus cases. Record source manifest, build output, boot logs and case results. P01 is an inventory-only exception; P02 exercises baseline only. Expected: Executed target matches pinned image and enforcing policy; source-only compilation never closes image runtime acceptance. P01/P02 prerequisite exclusions apply as specified in scope.

Evidence: `docs/evidence/P02/README.md`, `docs/evidence/P02/results.json`.

Risks: 8–16h active spike excludes long machine build time; success is not guaranteed.

## P03 — Implement generated-only native AIDL service and core vectors

Extend the actual system image with a bounded native capability.

[GitHub ticket](https://github.com/oudaykhaled/boundarylab/issues/29) · Proposed role: Native/platform engineer · 8–16h · Requirements R14, R16.

Scope:

- Dedicated native service
- Generated-sha256-v1 only and tracked internal AIDL

Acceptance:

- [ ] Service implements generated vectors and bounded state/worker policy.
- [ ] Structured AIDL is tracked for selected platform target; no vendor/VINTF promise.
- [ ] File capability is absent and file operation/FD request explicitly rejected.
- [ ] Known digest, byte cap, cancellation and epoch semantics match common corpus.
- [ ] Native tests include malformed/over-limit input and one terminal race winner.
- [ ] No ordinary app hidden-API reflection or system_server placement.

Verification:

- **Proposed**: Run native tests and build service into pinned target; capture test/build logs. Expected: All acceptance cases pass; retain actual counts, failures, environment and commit. No skipped native test counts as passing.
- **Proposed**: On the P01-authorized Linux host, checkout the pinned manifest, select the exact P02 build target, build changed modules/image using its documented AOSP build recipe, boot that image in the recorded virtual target, wait for adb readiness, capture ro.build.fingerprint and getenforce, then run the ticket-specific authorized/denied/restart/corpus cases. Record source manifest, build output, boot logs and case results. P01 is an inventory-only exception; P02 exercises baseline only. Expected: Executed target matches pinned image and enforcing policy; source-only compilation never closes image runtime acceptance. P01/P02 prerequisite exclusions apply as specified in scope.

Evidence: `docs/evidence/P03/README.md`, `docs/evidence/P03/results.json`.

Risks: Do not expand privileged file access to match app FieldPack profile.

## P04 — Integrate service boot, supervision and enforcing SELinux policy

Make platform service lifecycle and isolation real.

[GitHub ticket](https://github.com/oudaykhaled/boundarylab/issues/32) · Proposed role: Platform/security engineer · 8–16h · Requirements R14, R16.

Scope:

- Init/service registration
- Least-privilege permission and SELinux integration
- Own minimal authorized/denied generated-interface platform test clients; P05 later supplies deliberate published facade/client.

Acceptance:

- [ ] Booted image starts dedicated service and advertises intended interface.
- [ ] SELinux remains enforcing with narrow reviewed policy.
- [ ] Authorized platform client succeeds; denied client fails.
- [ ] Restart creates fresh epoch and old handles fail.
- [ ] Crash-loop/supervision policy is documented and exercised without unlimited rapid respawn.
- [ ] Record service/PID/registration evidence; source-only patch cannot pass.

Verification:

- **Proposed**: Execute boot/restart/denial tests on actual custom image and inspect policy denial logs. Expected: All acceptance cases pass; retain actual counts, failures, environment and commit. No skipped native test counts as passing.
- **Proposed**: On the P01-authorized Linux host, checkout the pinned manifest, select the exact P02 build target, build changed modules/image using its documented AOSP build recipe, boot that image in the recorded virtual target, wait for adb readiness, capture ro.build.fingerprint and getenforce, then run the ticket-specific authorized/denied/restart/corpus cases. Record source manifest, build output, boot logs and case results. P01 is an inventory-only exception; P02 exercises baseline only. Expected: Executed target matches pinned image and enforcing policy; source-only compilation never closes image runtime acceptance. P01/P02 prerequisite exclusions apply as specified in scope.

Evidence: `docs/evidence/P04/README.md`, `docs/evidence/P04/results.json`.

Risks: Policy debugging may exceed estimate; split repair work rather than disable enforcement.

## P05 — Implement platform-built facade and deliberate API client

Expose platform capability through an intentional framework surface.

[GitHub ticket](https://github.com/oudaykhaled/boundarylab/issues/37) · Proposed role: Platform framework engineer · 8–16h · Requirements R14, R16, R19.

Scope:

- Platform-built facade/client
- Separate app and platform profiles

Acceptance:

- [ ] Facade is compiled against intended custom platform API.
- [ ] Client executes generated operation through real native service.
- [ ] No reflection into hidden ServiceManager APIs from ordinary downloadable app.
- [ ] Permission denial and unsupported file capability reach typed client outcomes.
- [ ] Lifecycle/epoch changes remain visible to client.
- [ ] Layer diagram and build/run records identify exact framework versus user-space changes.

Verification:

- **Proposed**: Build facade/client with pinned image and execute authorized/denied tests. Expected: All acceptance cases pass; retain actual counts, failures, environment and commit. No skipped native test counts as passing.
- **Proposed**: On the P01-authorized Linux host, checkout the pinned manifest, select the exact P02 build target, build changed modules/image using its documented AOSP build recipe, boot that image in the recorded virtual target, wait for adb readiness, capture ro.build.fingerprint and getenforce, then run the ticket-specific authorized/denied/restart/corpus cases. Record source manifest, build output, boot logs and case results. P01 is an inventory-only exception; P02 exercises baseline only. Expected: Executed target matches pinned image and enforcing policy; source-only compilation never closes image runtime acceptance. P01/P02 prerequisite exclusions apply as specified in scope.

Evidence: `docs/evidence/P05/README.md`, `docs/evidence/P05/results.json`.

Risks: This is system framework integration, not kernel implementation.

## P06 — Execute platform boot, restart and interface-compatibility corpus

Verify image integration beyond a happy-path call.

[GitHub ticket](https://github.com/oudaykhaled/boundarylab/issues/48) · Proposed role: Platform QA engineer · 6–12h · Requirements R07, R14, R16, R19.

Scope:

- Generated-only image conformance
- Boot, restart and supported interface evolution

Acceptance:

- [ ] Each run records image fingerprint/source manifest/enforcing status.
- [ ] Boot registration and authorized/denied client behavior pass.
- [ ] Service kill/restart yields fresh epoch and no old-job restoration.
- [ ] Supported generated-interface evolution passes previously compiled platform consumer fixture.
- [ ] File capability stays unsupported and rejects without receiving/retaining user data.
- [ ] All actual failures/skip reasons retained; app-profile test cannot substitute.

Verification:

- **Proposed**: Run image instrumentation/conformance matrix using generated fixtures only. Expected: All acceptance cases pass; retain actual counts, failures, environment and commit. No skipped native test counts as passing.
- **Proposed**: On the P01-authorized Linux host, checkout the pinned manifest, select the exact P02 build target, build changed modules/image using its documented AOSP build recipe, boot that image in the recorded virtual target, wait for adb readiness, capture ro.build.fingerprint and getenforce, then run the ticket-specific authorized/denied/restart/corpus cases. Record source manifest, build output, boot logs and case results. P01 is an inventory-only exception; P02 exercises baseline only. Expected: Executed target matches pinned image and enforcing policy; source-only compilation never closes image runtime acceptance. P01/P02 prerequisite exclusions apply as specified in scope.

Evidence: `docs/evidence/P06/README.md`, `docs/evidence/P06/results.json`.

Risks: Platform binary compatibility differs from Gradle app AIDL; keep fixtures distinct.

## P07 — Investigate platform scheduling or memory with a controlled experiment

Develop defensible system-layer performance evidence.

[GitHub ticket](https://github.com/oudaykhaled/boundarylab/issues/52) · Proposed role: Platform performance engineer · 8–16h · Requirements R04, R05, R17.

Scope:

- Trace-backed generated-work investigation
- Bounded candidate experiment

Acceptance:

- [ ] Identify actual trace observation and competing explanations.
- [ ] Use matched baseline/candidate image/workload and pinned state.
- [ ] Separate scheduling/IPC/compute effects with declared metrics.
- [ ] Protected cancellation/memory behavior remains within frozen budgets.
- [ ] Retain negative/inconclusive conclusions and raw traces.
- [ ] Limit conclusions to executed virtual/physical target; no phone generalization.

Verification:

- **Proposed**: Run paired generated-work experiment and repeat protected-metric checks. Expected: All acceptance cases pass; retain actual counts, failures, environment and commit. No skipped native test counts as passing.
- **Proposed**: On the P01-authorized Linux host, checkout the pinned manifest, select the exact P02 build target, build changed modules/image using its documented AOSP build recipe, boot that image in the recorded virtual target, wait for adb readiness, capture ro.build.fingerprint and getenforce, then run the ticket-specific authorized/denied/restart/corpus cases. Record source manifest, build output, boot logs and case results. P01 is an inventory-only exception; P02 exercises baseline only. Expected: Executed target matches pinned image and enforcing policy; source-only compilation never closes image runtime acceptance. P01/P02 prerequisite exclusions apply as specified in scope.

Evidence: `docs/evidence/P07/README.md`, `docs/evidence/P07/results.json`.

Risks: No promised speedup or transfer of file-profile claims to platform profile.

## P08 — Run two-hour bounded generated-profile platform soak

Observe longer-running resource and failure behavior.

[GitHub ticket](https://github.com/oudaykhaled/boundarylab/issues/53) · Proposed role: Platform QA engineer · 4–8h · Requirements R05, R07.

Scope:

- Two-hour campaign plus active analysis
- Restart/churn/cap accounting

Acceptance:

- [ ] Complete two-hour declared workload or record exact failure time/incomplete status.
- [ ] Preserve offered/admitted/rejected/completed/lost counts.
- [ ] Track worker/session/retention and actual memory trends.
- [ ] Restart faults never create false success or restore old epoch.
- [ ] Enforcing state remains unchanged throughout.
- [ ] No file descriptors from user attachments are introduced.

Verification:

- **Proposed**: Run timed image campaign and regenerate counter/memory report from logs. Expected: All acceptance cases pass; retain actual counts, failures, environment and commit. No skipped native test counts as passing.
- **Proposed**: On the P01-authorized Linux host, checkout the pinned manifest, select the exact P02 build target, build changed modules/image using its documented AOSP build recipe, boot that image in the recorded virtual target, wait for adb readiness, capture ro.build.fingerprint and getenforce, then run the ticket-specific authorized/denied/restart/corpus cases. Record source manifest, build output, boot logs and case results. P01 is an inventory-only exception; P02 exercises baseline only. Expected: Executed target matches pinned image and enforcing policy; source-only compilation never closes image runtime acceptance. P01/P02 prerequisite exclusions apply as specified in scope.

Evidence: `docs/evidence/P08/README.md`, `docs/evidence/P08/results.json`.

Risks: Finite soak does not establish production SLO; elapsed runtime additional to active labor.

## P09 — Reproduce final image and reconcile platform productization gaps

Make final platform evidence reproducible and bounded.

[GitHub ticket](https://github.com/oudaykhaled/boundarylab/issues/57) · Proposed role: Release architect · 8–16h · Requirements R09, R18.

Scope:

- Fresh source/image recipe
- Final evidence and productization handoff

Acceptance:

- [ ] Recipe reproduces pinned image/client from documented inputs.
- [ ] Final ledger references executed image tests and unresolved failures.
- [ ] Review identity/method is real; absence of independent volunteer explicitly author-only.
- [ ] Commercial rollout, maintenance, security, distribution and career gaps stated.
- [ ] Generated-only platform scope and unsupported file capability remain visible.
- [ ] No deployment or production certification claimed by source publication.

Verification:

- **Proposed**: Execute fresh reproduction where resources permit and audit final claims against evidence. Expected: All acceptance cases pass; retain actual counts, failures, environment and commit. No skipped native test counts as passing.
- **Proposed**: On the P01-authorized Linux host, checkout the pinned manifest, select the exact P02 build target, build changed modules/image using its documented AOSP build recipe, boot that image in the recorded virtual target, wait for adb readiness, capture ro.build.fingerprint and getenforce, then run the ticket-specific authorized/denied/restart/corpus cases. Record source manifest, build output, boot logs and case results. P01 is an inventory-only exception; P02 exercises baseline only. Expected: Executed target matches pinned image and enforcing policy; source-only compilation never closes image runtime acceptance. P01/P02 prerequisite exclusions apply as specified in scope.

Evidence: `docs/evidence/P09/README.md`, `docs/evidence/P09/results.json`.

Risks: Independent reproduction cannot be fabricated or made contingent on invented collaborators.

## FP01 — Freeze FieldPack manifest, file profile and shared negative vectors

Give producer and recipient one precise portable integrity contract.

[GitHub ticket](https://github.com/oudaykhaled/boundarylab/issues/24) · Proposed role: Contract architect · 4–8h · Requirements R01, R03, R07, R10, R11.

Scope:

- Strict manifest v1
- Flat aliases and bounded real-file profile

Acceptance:

- [ ] Top-level fields exactly format/version/algorithm/files; entries exactly name/size/sha256; reject unknown/duplicate keys and wrong types including bool integers.
- [ ] Use 1–64 lowercase ASCII aliases matching [a-z0-9][a-z0-9._-]{0,63}, reject dot/dotdot, separators and trailing dot and reserved basenames con/prn/aux/nul/com1..9/lpt1..9 per docs/FIELDPACK.md; no implicit normalization.
- [ ] Require sorted unique aliases, 1–32 entries, SHA-256 lowercase hex, 128MiB per file AND aggregate, 16KiB UTF-8 manifest, 64KiB chunks.
- [ ] No URI/path/original filename/device identity/timestamp emitted; aliases literal, no percent decoding.
- [ ] Freeze MATCH/MISSING/SIZE_MISMATCH/DIGEST_MISMATCH/UNREADABLE_OR_UNSUPPORTED; exit0 all match, exit1 any non-MATCH entry including unreadable, exit2 invalid manifest/root or operation-level error; interrupt130.
- [ ] Reject malformed manifest before attachment opens; extra directory files ignored and explicitly stated.
- [ ] Android profile accepts read-only regular seekable whole-file descriptors only; AOSP generated-only.
- [ ] Mutation checks describe observed bytes, not immutable snapshot, authenticity or malware safety.

Verification:

- **Proposed**: Review frozen manifest/file specification and executable reference vectors. Native/JVM vector execution is downstream acceptance and does not block completion of the contract document. Expected: All acceptance cases pass; retain actual counts, failures, environment and commit. No skipped native test counts as passing.

Evidence: `docs/evidence/FP01/README.md`, `docs/evidence/FP01/results.json`.

Risks: Portable aliases intentionally restrict names; no broad Unicode/nested-path support. Alias policy must align reference implementation before final acceptance. Reference policy is frozen; native/JVM adoption must run the same versioned vectors.

## REF01 — Deliver runnable Python FieldPack reference, tests, fixtures and walkthrough

Provide a useful bounded real-file example before native middleware exists.

[GitHub ticket](https://github.com/oudaykhaled/boundarylab/issues/21) · Proposed role: Python reference maintainer · 6–12h · Requirements R05, R07, R09, R10, R11, R18.

Scope:

- Python stdlib create/verify CLI
- Owned fixture and happy/changed/missing walkthrough
- Reference file contract already delivered in docs/FIELDPACK.md; native adoption remains FP01/C02 work. Historical planning range 6–12h is not an elapsed-time assertion.

Acceptance:

- [x] Commands accept actual caller-specified supported files/root and output, not hard-coded fixture identities.
- [x] Use bounded reads, strict manifest validation and FP01 caps; symlink/nonregular/unsafe aliases reject under documented POSIX capability profile.
- [x] Create publishes complete output outside input root with explicit no-overwrite behavior; failed creation leaves no success-shaped manifest; verifier never mutates inputs.
- [x] Known empty/binary/multichunk digests match independent oracle; changed and missing files produce distinct nonzero outcomes.
- [x] Test duplicate keys, bool sizes, limits, path escapes, injected read/mutation errors and interruption without claiming all mutation detection.
- [x] Root-relative safe opens and capability limitations documented; diagnostics expose aliases only.
- [x] Clean walkthrough creates/verifies project-owned note, CSV and PNG fixtures, then modifies/removes temporary copies; explicit Python-reference label, no Binder/Flutter/JVM completion.
- [x] Record actual command/interpreter/host/commit/test counts before root changes planned status to verified.

Verification:

- **Executed reference**:  Expected: Receipt records 12 passing tests, both demo modes PASS and all three committed fixture aliases MATCH; no native runtime implication.
- **Executed reference**: Agent10 scoped independent AI review reported pass by root; retain report link and label reviewer as AI. Expected: No implication of independent human/native middleware review.

Evidence: `docs/FIELDPACK.md`, `examples/fieldpack/README.md`, `examples/fieldpack/EVIDENCE.md`, `examples/fieldpack/test_fieldpack.py`, `examples/fieldpack/inspection.fieldpack.json`.

Risks: Reference success does not close native epics. Root is implementing independently; reconcile actual CLI and evidence paths before publication.

## FP02 — Implement Android regular-file capability and descriptor ownership

Hash authorized real files across Binder without broad storage access.

[GitHub ticket](https://github.com/oudaykhaled/boundarylab/issues/38) · Proposed role: Android storage/IPC engineer · 8–16h · Requirements R05, R07, R14, R16, R17.

Scope:

- Client-opened read-only PFD transfer
- Platform-neutral positional reader adapter

Acceptance:

- [ ] Broker receives actual cross-package descriptor; never reopens client URI/path; no file bytes in AIDL arrays.
- [ ] Validate regular/seekable/read-only whole-file descriptor and known 0–128MiB length; reject pipe/socket/directory/closed/writable/subsection inputs without content probing.
- [ ] Use positional reads from offset zero; client cursor changes do not alter digest.
- [ ] Client closes local PFD after synchronous submit returns/errors; broker owns received PFD through rejection/duplicate/terminal/cleanup with exactly one close owner.
- [ ] Same retained request metadata returns old job and closes incoming duplicate FD without reading; conflict closes FD.
- [ ] Before/after same-FD metadata and exact byte count detect chosen changes; premature EOF/growth/read error cannot succeed.
- [ ] One worker stays occupied until blocked read exits; no hard cancellation deadline, unsafe thread kill or replacement pool.
- [ ] 100-cycle rejection/acceptance tests show owned FD baseline recovery.
- [ ] Reject st_nlink != 1 and explicitly unsupported source properties; absence of required validation cannot silently enable file capability.

Verification:

- **Proposed**: Run actual Android provider/regular-FD tests with wrong owner, shared cursor, revoked open, read errors and deterministic mutations. Expected: All acceptance cases pass; retain actual counts, failures, environment and commit. No skipped native test counts as passing.
- **Proposed**:  Expected: Commands execute the stated acceptance corpus on actual required target and return success; retain complete counts and negative-case assertions, not only build exit status.

Evidence: `docs/evidence/FP02/README.md`, `docs/evidence/FP02/results.json`.

Risks: Regular provider-backed reads may still block; provider support is explicit. If implementation exceeds 16h split adapter and transport follow-up before starting.

## FP03 — Implement native FieldPack selection, assembly and explicit export

Let an inspector create a complete attachment-integrity manifest.

[GitHub ticket](https://github.com/oudaykhaled/boundarylab/issues/40) · Proposed role: Android application engineer · 8–16h · Requirements R07, R09, R14, R18.

Scope:

- SAF picker/open lifecycle
- Sequential pack attempt and manifest export

Acceptance:

- [ ] Select 1–32 documents with read-only grants; no all-files permission, URI-to-path conversion or default persisted grant.
- [ ] Open off UI thread with bounded concurrency; null/missing/revoked/provider-cancel/late-open results handled and FDs closed.
- [ ] Assign approved safe aliases; reject collisions and pack aggregate over128MiB before successful export.
- [ ] Sequential jobs share one assembly attempt; any failure/cancel/loss prevents success manifest.
- [ ] New explicit attempt rehashes all inputs; never combine prior epoch partial results silently.
- [ ] Successful export requires confirmed destination write; cancellation/write failure not shown as exported.
- [ ] Explain matching alias filenames must be transferred separately; no source-file copy or cloud transfer implied.

Verification:

- **Proposed**: Use actual picker with owned fixtures and test provider delays/revocation; verify output via REF01 and negative second-file/loss scenarios. Expected: All acceptance cases pass; retain actual counts, failures, environment and commit. No skipped native test counts as passing.
- **Proposed**:  Expected: Commands execute the stated acceptance corpus on actual required target and return success; retain complete counts and negative-case assertions, not only build exit status.

Evidence: `docs/evidence/FP03/README.md`, `docs/evidence/FP03/results.json`.

Risks: Picker can expose cloud providers; app makes no network upload but provider I/O may occur. Android destination providers may not support atomic replacement; guarantee complete serialized content before write, not universal atomic export.

## FP04 — Implement Flutter real-file workflow through native broker adapter

Offer the same real inspection workflow in Flutter.

[GitHub ticket](https://github.com/oudaykhaled/boundarylab/issues/45) · Proposed role: Flutter/Android engineer · 8–16h · Requirements R02, R07, R09, R15.

Scope:

- Picker native bridge
- Dart pack UI and explicit manifest export

Acceptance:

- [ ] Actual selection/open/descriptor submit runs native side; no raw bytes/FD numbers/URIs cross Dart channel.
- [ ] Use safe opaque selection handles and approved aliases; enforce32-file/128MiB pack limits.
- [ ] Real broker produces each digest; Dart cannot hash locally as substitute.
- [ ] All-success active attempt alone enables export; cancel/loss/error disables it.
- [ ] Engine disposal/navigation/late callbacks release handles and cannot publish stale manifest.
- [ ] Native and Flutter equivalent fixtures yield identical sorted manifest content.
- [ ] Destination cancellation/write error visible; instructions distinguish export from separately transferred attachments.

Verification:

- **Proposed**: Execute real-device Flutter selection through Binder, disposal and broker-loss flows; compare manifest to native/reference vectors. Expected: All acceptance cases pass; retain actual counts, failures, environment and commit. No skipped native test counts as passing.
- **Proposed**:  Expected: Commands execute the stated acceptance corpus on actual required target and return success; retain complete counts and negative-case assertions, not only build exit status.

Evidence: `docs/evidence/FP04/README.md`, `docs/evidence/FP04/results.json`.

Risks: Plugin lifecycle/open cancellation adds native work beyond a happy-path channel.

## FP05 — Implement macOS rooted-file host capability and JVM recipient verifier

Verify separately supplied attachments through the real PC host profile.

[GitHub ticket](https://github.com/oudaykhaled/boundarylab/issues/41) · Proposed role: Kotlin/JVM engineer · 8–16h · Requirements R03, R07, R09, R19.

Scope:

- Explicit startup input root
- Alias-only file operation and strict manifest verification

Acceptance:

- [ ] Host opens only validated flat aliases below explicit startup root; no hypothetical JDK FD passing.
- [ ] No-follow regular-file channel, positional reads and same-handle mutation checks follow supported JDK/filesystem capabilities.
- [ ] Pin root identity and detect replacement where possible; document residual same-user TOCTOU, no race-proof sandbox claim.
- [ ] CLI strictly validates manifest before attachment reads and invokes separate host process.
- [ ] MATCH/MISSING/SIZE_MISMATCH/DIGEST_MISMATCH/UNREADABLE_OR_UNSUPPORTED outcomes and aggregate caps match shared corpus.
- [ ] Unknown version, symlink/nonregular/root escape and oversized frames fail safely.
- [ ] Generated operation remains supported; file support advertises independently.
- [ ] Prove nlink==1, seekability, regular type and no-follow/root checks on selected JDK/filesystem; fail capability gate if unavailable. Any JNI helper is separately reviewed and estimated.

Verification:

- **Proposed**: Run macOS host/CLI subprocess tests on real user-specified fixture roots, including tamper/missing/symlink/root-change cases. Include hardlink and nonseekable negatives; record unsupported primitive as a file-capability failure. Expected: All acceptance cases pass; retain actual counts, failures, environment and commit. No skipped native test counts as passing.
- **Proposed**:  Expected: Commands execute the stated acceptance corpus on actual required target and return success; retain complete counts and negative-case assertions, not only build exit status.

Evidence: `docs/evidence/FP05/README.md`, `docs/evidence/FP05/results.json`.

Risks: Java secure-directory support is platform dependent; scope trust rather than claim unsupported guarantees.

## FP06 — Verify manual Android-to-PC FieldPack handoff and failure diagnosis

Show an end-to-end real business example with honest transfer boundaries.

[GitHub ticket](https://github.com/oudaykhaled/boundarylab/issues/49) · Proposed role: Integration QA/documentation engineer · 4–8h · Requirements R02, R03, R09, R18, R19.

Scope:

- Owned inspection note and generated binary fixture
- Manual alias-preserving transfer and recipient checks

Acceptance:

- [ ] Native and Flutter each hash same owned attachments through actual broker and export schema-valid manifests.
- [ ] Operator separately copies fixtures to supplied macOS directory with exact aliases; no automatic synchronization claimed.
- [ ] JVM verifier and Python reference independently agree on good manifest/file set.
- [ ] Changed-byte, missing-file and unsupported-input cases return documented distinct failures.
- [ ] Broker death mid-pack prevents complete manifest; explicit restart attempt rehashes all.
- [ ] Evidence omits private inputs and labels reference versus native versus JVM process proofs.
- [ ] Walkthrough works from documented clean state and describes integrity-not-authenticity limitation.

Verification:

- **Proposed**: Execute entire owned-fixture handoff on declared Android/macOS targets and record sanitized transcript/video. Expected: All acceptance cases pass; retain actual counts, failures, environment and commit. No skipped native test counts as passing.
- **Proposed**:  Expected: Commands execute the stated acceptance corpus on actual required target and return success; retain complete counts and negative-case assertions, not only build exit status.

Evidence: `docs/evidence/FP06/README.md`, `docs/evidence/FP06/results.json`.

Risks: Reference-only walkthrough cannot satisfy this native handoff ticket.
