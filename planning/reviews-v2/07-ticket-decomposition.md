# Ticket decomposition and hiring-evidence review

Reviewed baseline: planning/backlog.json, all sixteen issue bodies in ISSUES-BEFORE.json, architecture, requirements and evidence sources. No implementation or public edits performed. All assignments below are proposed roles, not real assignees. All middleware work remains planned.

## Recommendation

Retain #1–#16 as epics and replace their effort rollups with child-only totals. Add **B17 FieldPack: local attachment integrity** as a product epic. Keep the synthetic digest workload for repeatable measurements; introduce actual files through an explicit new contract profile. Existing architecture currently excludes real files and describes seed/byte-count inputs: those statements must be amended before marking any real-file integration Ready.

The existing chain unnecessarily serializes independent work. Flutter can start after the SDK contract and broker baseline, without waiting for every native denial test. macOS can start after the pure core/contract, without Flutter. Benchmark design can start once the contract exists; execution waits for the selected release build. Technology comparison precedes architecture choices; its measured case study follows actual observations. Linux/AOSP feasibility depends on authorized resources and contract, not on completing the whole G2 release.

Treat documentation, Python reference execution, native source, native compilation, device execution and independently reproduced evidence as different evidence classes. The reference utility is useful now but cannot close B02–B09 or prove Binder, Flutter, macOS JVM IPC or AOSP work.

## Ticket conventions and status rules

All rows below are implementation-sized proposals, generally 4–16 focused hours (0.5–2 eight-hour days). Larger uncertain activities have a feasibility ticket followed by a new estimate. `parent` means the named existing umbrella or B17; it is not a dependency. `depends_on` lists only necessary predecessor children. All rows default to type `task`, priority P1, status `Planned`, assignee empty. G1 essentials and reference-example tasks are P0; conditional G3 tasks are P2. Roles indicate who should implement, not who has accepted ownership.

Every issue body must include user value, bounded scope, acceptance checkboxes, exact verification command to be finalized at kickoff, expected evidence paths, risks, dependencies and requirement IDs. Commands that do not exist yet must be labeled proposed. Avoid empty “tests pass” criteria: state cases, expected result and execution environment. Each acceptance statement below supplies an outcome; verification specifies how to observe it; evidence is the retained result.

**Ready:** inputs/contracts are approved by the actual implementer, predecessor evidence is accepted, required toolchain/device/resources are available, estimate fits 4–16h, and verification is executable or a concrete implementation deliverable. No automatic Ready state solely because an issue has text. **Blocked:** record exact missing dependency/resource/decision, unblock condition and next action; do not fabricate an owner or date. **In progress:** actual implementer has started. **Review:** linked diff plus tests; implementation complete does not imply runtime verification. **Done:** all scoped acceptance and verification pass, evidence links identify commit/environment/command/result, and an identified reviewer or explicitly labeled author-review has checked them. A skipped native test cannot count as passing. A knowingly failed optional experiment can close a research task with a negative conclusion, never the associated implementation gate. **Epic Done:** required children Done and gate acceptance reconciled; optional deferred children clearly excluded. Track review method separately; ten AI planning reviews do not become ten human implementation reviews.

## Concrete child tickets

Compact row format: key/title; parent/phase; role; hours; dependencies; value/scope; acceptance; verification/evidence; risk; requirements. These are intended as direct issue-body material. Expanded commands and final numeric caps belong in the contract, avoiding contradictory constants in every child.

### Contracts and core

**C01 — Prove toolchain and target feasibility** · B01/G1 · Android/Flutter engineer · 4–8h · deps none · Value: avoid committing to unbuildable targets. Build and run minimal separately packaged Android and Flutter smoke targets; run two-process macOS JVM local-socket spike. Record tool versions and allocated physical measurement device separately. Pass only targets actually executed; unavailable targets are explicit blockers. Verification: clean build/install/run logs and process receipts. Evidence: feasibility matrix with commit, host/device and exit status. Risk: tool presence mistaken for working runtime. R03,R14,R15,R19.

**C02 — Freeze state, trust and bounded contract** · B01/G1 · middleware architect · 6–12h · deps none · Value: consistent implementations. Define version/epoch, owner-bound sessions, timeout ambiguity, cancellation winner, dedup retention and all byte/entry caps. Acceptance: every transition has an allowed outcome; wrong owner cannot read/cancel; retention ceiling is numeric; lost broker never implies success. Verify table-driven specification vectors including cap−1/cap/cap+1, race outcomes and unsupported major. Evidence: ADR and reviewed vector corpus. Risk: counting metadata entries while leaving bytes unbounded. R01,R07,R10,R16.

**C03 — Implement deterministic admission and state policy** · B02/G1 · Kotlin engineer · 8–16h · deps C02 · Value: shared predictable policy. Pure JVM state machine with bounded queues, owner/session scoping and serialized terminal commits. Acceptance: queued cancellation never executes; completion/cancel ordering allows exactly one terminal result; second caller progresses under flood; same key/different input conflicts. Verify deterministic scheduler tests, not timing sleeps. Evidence: machine-readable tests and invariant counters. Risk: coupling core to Android identity. R05,R07,R08.

**C04 — Implement bounded streaming work and retention** · B02/G1 · Kotlin engineer · 6–12h · deps C03 · Value: correct work without whole-input retention. Synthetic baseline plus a stream abstraction usable by future file adapter. Acceptance: independent digest vectors match; buffer/retention caps hold; expired records return UNKNOWN/EXPIRED; worker exceptions release resources. Verify chunk boundary fixtures and fake stream close/error counters. Evidence: tests, resource budget table. Risk: stream abstraction cannot itself guarantee interruptible I/O. R04,R05,R08.

### Android and consumers

**A01 — Implement permission-protected Binder service** · B03/G1 · Android middleware engineer · 8–16h · deps C01,C03 · Value: real isolated capability. Separate broker package, AIDL boundary, signature permission, captured caller identity and session token. Acceptance: different PIDs; unauthorized signer denied; forged owner/session rejected; expensive work off Binder ingress. Verify installed package instrumentation and process/identity traces. Evidence: install/run receipts, authorization tests. Risk: trusting parcel package names or identity after async dispatch. R14,R16.

**A02 — Implement Kotlin connection and loss semantics** · B03/G1 · Android engineer · 8–16h · deps A01,C04 · Value: clients see truthful outcomes. Bounded polling, generation-aware reconnect, foreground demand and explicit resubmission. Acceptance: kill yields LOST/UNKNOWN; stale response cannot change a new generation; timeout is not automatic retry; no rebinding after demand closes. Verify checkpoint-driven death/timeouts and callback interleavings. Evidence: state trace and instrumentation report. Risk: conflating reconnect with restored jobs. R07,R14,R16.

**A03 — Implement native sample and denial harness** · B04/G1 · Android engineer · 6–12h · deps A02 · Value: inspect real service usage. Minimal submit/status/cancel UI plus separately signed test client. Acceptance: known vector, queue rejection, wrong signature, cross-owner cancellation denial and kill/rebind all execute; fault controls absent from release artifact. Verify end-to-end instrumented scenarios and artifact inspection. Evidence: short recording, raw test receipts and package list. Risk: screenshots without real IPC. R02,R07,R11.

**F01 — Implement Flutter plugin and consumer** · B05/G1 · Flutter/native engineer · 8–16h · deps A02 · Value: second ecosystem uses same broker. Dart typed facade through authored native adapter and existing Kotlin SDK. Acceptance: same vectors/states as Kotlin, UI stays responsive, timeout/cancel distinct, no duplicate policy engine in Dart. Verify installed Flutter app against separate broker. Evidence: native trace, result comparison and build log. Risk: replacing native integration with mocks. R02,R15.

**F02 — Verify Flutter engine lifecycle and G1 walkthrough** · B05/G1 · Flutter QA engineer · 6–12h · deps F01,A03 · Value: integration survives disposal. Acceptance: engine disposal closes session; recreation cannot reuse stale observations; second engine isolated; broker loss explicit. Verify repeated engine teardown and deterministic generation tests, then five-minute G1 recording. Evidence: tests, disposal counters, limitations ledger. Risk: confusing Activity recreation with engine destruction. R07,R15,R18.

### Faults, desktop and compatibility

**Q01 — Build deterministic fault orchestration** · B06/G2 · QA/middleware engineer · 8–16h · deps A02,C04 · Value: reproducible hard failures. Add test-only acceptance/start/completion checkpoints. Acceptance: process/client loss at each checkpoint, cancel/deadline race, malformed input and unauthorized owner have declared outcomes; release excludes controls. Verify checkpoint matrix with actual counts and failure outputs. Evidence: seed/checkpoint/commit manifests. Risk: test hooks changing production behavior. R07,R11,R17.

**Q02 — Execute saturation and cleanup campaign** · B06/G2 · QA engineer · 6–12h · deps Q01,F02 · Value: establish bounded stability. Acceptance: planned 100 repetitions/cell and 30-minute churn are executed or explicitly incomplete; report accepted/rejected/completed counts, descriptors, retained bytes and second-caller progress; zero false success. Verify counter assertions and process sampling. Evidence: raw campaign logs and failure ledger. Risk: treating observed duration as production reliability. R05,R07,R08.

**D01 — Implement macOS JVM host framing/security** · B07/G2 · Kotlin/JVM engineer · 8–16h · deps C01,C04 · Value: real PC profile sharing policy. Private runtime directory, bounded frames, deadlines and connection-owned sessions. Acceptance: two real JVM processes; malformed/truncated/oversized frames rejected before oversized allocation; inaccessible runtime path rejected; disconnected sessions cleaned. Verify macOS integration tests and filesystem permissions. Evidence: host/client logs and framing tests. Risk: assuming Android UID semantics on PC. R03,R07,R19.

**D02 — Implement desktop CLI and semantic parity** · B07/G2 · Kotlin/JVM engineer · 6–12h · deps D01,A02 · Value: demonstrate phone/PC behavior parity. Acceptance: shared corpus matches on both hosts, including versions, caps, cancellation and epoch reset; platform-specific differences documented. Verify actual macOS and Android run manifests. Evidence: comparative conformance report. Risk: identical code treated as proof without execution. R03,R10,R19.

**V01 — Preserve compiled consumers and test version skew** · B08/G2 · SDK engineer · 6–12h · deps A02,D01,F01 · Value: upgrades avoid silent breakage. Acceptance: archived compiled old clients exercise supported additive provider version; unsupported major returns typed rejection; ABI-incompatible change is detected. Verify old binary/new provider and new client/old provider matrix. Evidence: fixture checksums, compiler versions and test results. Risk: recompiling “old” client against new definitions. R03,R07,R10.

**V02 — Add quality lanes and release artifact checks** · B08/G2 · CI engineer · 6–12h · deps Q01,D02,V01 · Value: executable standards. Acceptance: core, Android and macOS lanes clearly report executed/skipped status; intentional contract/denial mutation fails correct lane; release inspection rejects test fault hooks. Verify workflow runs and negative-control branch locally or in authorized CI. Evidence: pinned workflow config, pass/fail links. Risk: green docs-only CI implying native validation. R10,R11.

### Measurement, release and architecture narrative

**M01 — Preregister performance and startup experiment** · B09/G2 · performance engineer · 4–8h · deps C02 · Value: credible measurement. Acceptance: baseline variants, metrics, physical device, thermal/compilation rules, sample sizes, exclusion policy and protected metrics frozen before tuning; separate startup/bind/queue/compute/polling. Verify pilot establishes feasible collection, not claimed speedup. Evidence: versioned protocol and pilot raw logs. Risk: unsupported fixed latency promise. R04,R05,R06,R08.

**M02 — Execute baseline and resource/load study** · B09/G2 · performance engineer · 8–16h · deps M01,V02,Q02 · Value: actual capacity evidence. Acceptance: required sessions run; all outcomes and exclusions retained; PSS/RSS labeled, uncertainty shown; comparisons use equivalent workload. Verify analysis rerun reproduces tables from raw records. Evidence: scripts, trace files, environment manifest. Risk: desktop/phone resource numbers compared without accounting. R04,R05,R06,R08.

**L01 — Rehearse fresh consumer and release package** · B10/G2 · release/SDK engineer · 6–12h · deps V02,D02,F02 · Value: usability outside source tree. Acceptance: fresh clone and separately built same-publisher client work from documented artifacts; license/dependency inventory and checksums included; no secrets required from maintainer. Verify documented recipe in clean workspace. Evidence: command transcript and artifact manifest. Risk: author’s cached configuration masking omissions. R09,R11.

**L02 — Produce evidence ledger and five-minute demo** · B10/G2 · technical writer/architect · 4–8h · deps L01,M02,E04 · Value: panel can audit claims quickly. Acceptance: every demonstrated claim links commit/command/result; real-file example and loss scenario shown; Python/native/AOSP evidence labels distinct. Verify scripted demo from documented starting state and claim-link audit. Evidence: recording/transcript, R01–R22 ledger. Risk: implying integration completed because reference tool runs. R09,R18.

**T01 — Assess technology choices and revisit triggers** · B11/G2 · architect · 4–8h · deps C02 · Value: defensible direction. Acceptance: dated primary-source comparison of Binder/Messenger, cross-platform framework choice and daemon placement; each decision names revisit trigger. Verify decisions align with actual scope and sources. Evidence: compact ADR. Risk: retrospective rationale presented as preimplementation decision. R01,R12.

**T02 — Write observed defect/performance case study** · B11/G2 · architect/performance engineer · 4–8h · deps M02,T01 · Value: show difficult problem resolution. Acceptance: actual failing observation, alternatives, test, minimal change or justified no-change, and regression evidence; negative outcome acceptable. Verify claims against raw observation. Evidence: case study with linked artifact history. Risk: manufactured incident or promised speedup. R04,R12,R17.

### Conditional platform track

**P01 — Audit authorized Linux/AOSP resources and pin target** · B12/G3 · platform engineer · 4–8h · deps C02 · Acceptance: resource inventory, version/target and build budget exist; unavailable infrastructure means Blocked, no purchases. Verify available host evidence without secrets. Evidence: feasibility record. Risk: macOS tooling mistaken for supported platform build host. R16.

**P02 — Build and boot unchanged baseline image** · B12/G3 · platform engineer · 8–16h active work · deps P01 · Acceptance: image actually boots, fingerprint/provenance/test access retained. Verify build log and boot smoke. Evidence: pinned manifest and logs. Risk: machine elapsed time can exceed active-hour estimate; split further on new blockers. R16,R19.

**P03 — Implement narrow native service and vectors** · B13/G3 · native/platform engineer · 8–16h · deps P02,C02 · Acceptance: native digest/state implementation passes independent vectors and cap/race tests; profile is explicitly separate from ordinary APK broker. Verify native tests. Evidence: results and interface definition. Risk: expanding this track to real-file ingestion; synthetic operation remains valid here. R14,R16.

**P04 — Integrate image service startup and SELinux** · B13/G3 · platform/security engineer · 8–16h · deps P03 · Acceptance: service starts on boot in separate process, restart epoch changes, authorized access works and denied access fails with enforcing policy; no permissive workaround. Verify booted-image security/restart tests. Evidence: policy diff, process/service receipts, enforcing status. Risk: SELinux policy debugging may need a separately re-estimated child. R14,R16.

**P05 — Implement platform client/facade** · B14/G3 · platform engineer · 8–16h · deps P04 · Acceptance: platform-built facade uses deliberate API; ordinary SDK profile stays separate; no hidden-API reflection. Verify authorized/denied client calls on image. Evidence: build/run log and layer diagram. Risk: source-only facade misreported as implemented runtime. R14,R16,R19.

**P06 — Execute image lifecycle and compatibility corpus** · B14/G3 · platform QA · 6–12h · deps P05,V01 · Acceptance: boot registration, restart/loss, denied access and supported interface evolution execute; image fingerprint attached per run. Verify image tests. Evidence: result matrix and logs. Risk: app-profile results substituted for image results. R07,R14,R16,R19.

**P07 — Investigate platform trace and test bounded change** · B15/G3 · performance engineer · 8–16h · deps P06,M01 · Acceptance: trace-backed hypothesis tested with protected metrics; negative/inconclusive conclusions retained. Verify repeat experiment. Evidence: raw trace, patch if justified, comparison. Risk: assuming emulator speed establishes phone performance. R04,R05,R17.

**P08 — Execute two-hour platform soak** · B15/G3 · platform QA · 4–8h active · deps P06 · Acceptance: two hours complete or failure time reported; counts, loss outcomes and memory/descriptor trends retained; failures produce separate repair tasks. Verify campaign and counters. Evidence: soak report. Risk: finite soak overstated as SLO proof. R05,R07.

**P09 — Reproduce image and publish productization gaps** · B16/G3 · release architect · 8–16h · deps P07,P08,L02 · Acceptance: fresh reproduction recipe matches image evidence; review author identified; absent independent reproducer labeled; commercial/security/distribution gaps explicit. Verify recipe and final claims audit. Evidence: reproduction log and final ledger. Risk: “published” confused with production deployment. R09,R18.

### B17: FieldPack real example

**E01 — Freeze manifest and real-file safety profile** · B17/G1-example · architect · 4–8h · deps C02 · Value: recipient can compare attachments safely. Define UTF-8 JSON version, SHA-256 algorithm, logical relative path, byte count and digest; no source URI, absolute path, username or original root directory. Acceptance: duplicate logical paths, absolute paths, `..`, unsupported major/algorithm and excessive entry/path/manifest limits rejected; deterministic ordering; integrity explicitly not authenticity. Specify files observed as bytes, not immutable acquisition snapshots. Verify positive/negative golden manifests. Evidence: schema/spec/vector corpus. Risk: filename privacy still matters; public evidence uses owned fixtures only. R01,R09,R10.

**E02 — Build bounded Python reference generator/verifier** · B17/reference · Python engineer · 6–12h · deps E01 · Value: runnable workflow now. User supplies local root/files and output manifest; verifier separately receives root and manifest. Acceptance: streaming hashes actual supplied regular files; exact byte count; clear match/changed/missing results and nonzero failure exit; reject traversal/symlinks and unsupported special files under documented local threat model; no uploads; bounded input/manifest/output; fail without complete-success manifest on error. Verify standard-library tests with temporary directories, empty/binary/multichunk files and out-of-root names. Evidence: commands, tests and generated fixture manifest. Risk: lexical containment alone is insufficient; security claims must match actual file-open handling. R09,R10,R11; does not satisfy native R14/R15/R16.

**E03 — Verify mutation/error/cancellation edge cases** · B17/reference · Python QA · 4–8h · deps E02 · Value: trust honest results. Acceptance: controlled mutation/read error/oversize/interrupt cannot produce claimed complete validated pack; no partial final manifest; malformed/duplicate manifests rejected; before/after metadata check documented as best-effort, not immutable snapshot guarantee. Verify controlled hooks and negative tests; no arbitrary user files in evidence. Evidence: results plus limitations. Risk: same-size concurrent edits may evade metadata checks; captured digest describes read bytes only. R07,R11,R17.

**E04 — Publish safe FieldPack fixture and walkthrough** · B17/reference · technical writer · 4–8h · deps E03 · Value: understandable business example. Own fixture `inspection-note.txt` plus project-generated binary attachment; generate manifest, verify good directory, modify attachment, remove attachment, observe precise failures. Acceptance: instructions run from clean checkout; clearly labeled Python reference, not future middleware; filenames harmless and logs sanitized. Verify exact README commands. Evidence: transcript, fixture provenance, result samples. Risk: misleading green native gate. R09,R18.

**E05 — Design Android file grant and descriptor ownership** · B17/G2 · Android architect · 4–8h · deps E01,A01 · Value: real Android user-selected inputs. Acceptance: ownership transfer/duplication, close responsibility and cancellation/error cleanup each specified; grant lifetime/provider mutability/unknown length/nonseekable policy explicit; staged versus direct-stream mode decided; numeric disk/read/descriptor limits selected if staging. Verify descriptor lifecycle table and provider test design. Evidence: ADR. Risk: blocking provider reads cannot inherit CPU-job cancellation SLA; do not claim support before tests. R05,R07,R14,R16.

**E06 — Implement native FieldPack selection and broker hashing** · B17/G2 · Android engineer · 8–16h · deps E05,A03,C04 · Value: actual selected attachments processed via Binder. Acceptance: chosen supported provider/file profile works; buffers stay bounded; grants/FDs closed exactly once; revoked access, read failure, cap excess and cancel return explicit errors; process loss produces LOST/UNKNOWN with no auto-resume. Verify provider fault fixtures and actual selected files plus descriptor counters. Evidence: device logs, manifest parity with reference. Risk: staging may require additional ticket after E05; split rather than pretend within estimate. R05,R07,R09,R14.

**E07 — Wire Flutter FieldPack and export manifest** · B17/G2 · Flutter engineer · 8–16h · deps E06,F02 · Value: second ecosystem executes same inspection flow. Acceptance: real user selection routes through native adapter and broker; manifest matches reference; export is explicit user action; cancellation/disposal/loss discard incomplete result; no arbitrary path display in public evidence. Verify native/Flutter same fixtures and engine disposal. Evidence: end-to-end device recording and receipt. Risk: Flutter independently hashes files and bypasses broker. R02,R09,R15.

**E08 — Implement JVM recipient verifier and end-to-end handoff** · B17/G2 · JVM engineer · 8–16h · deps E01,D02,E06 · Value: recipient checks separately supplied local attachments on PC. Acceptance: macOS CLI consumes exported versioned manifest; match/changed/missing and hostile paths produce specified outcomes; no automatic transfer/sync; same vectors as reference. Verify manual transfer of owned fixtures and Android-produced manifest, then mutation/deletion. Evidence: macOS command log and cross-implementation comparison. Risk: verifier process alone does not replace planned host/CLI IPC conformance. R03,R09,R19.

## Dependencies and estimate accounting

Graph above is acyclic: foundational C tickets → platform adapters → conformance → execution/release. E01 feeds the reference immediately; E05 waits for Android transport; E08 waits for manifest producer and desktop. T01 and M01 run early; P01/P02 can run independently if authorized resources exist. Q01 does not wait for Flutter; Q02 adds cross-client campaign after Flutter is ready. Do not retain obsolete umbrella “B05 before B07” dependency when child dependencies replace it.

Existing #1–#16 nominal/range estimates must become `rollup only; superseded by children`. B17 adds genuine scope and cannot be hidden inside old 40/120/240-hour promises. Sum each unique child once. Reference tasks E01–E04 total 18–36h; reference implementation may already be partially delivered by root, so report actual verification state before scheduling remaining work. Native real-file work E05–E08 adds 28–56h before unforeseen staging/provider issues. These are estimates, not proof of elapsed effort or completion. Rebaseline all milestone ranges from the chosen child set; conditional G3 must be separate, not a prerequisite for the reference release.

## Requirement coverage and honest exclusions

| Requirement | Concrete evidence tickets | Remaining limit |
|---|---|---|
| R01 | C02,T01,E01 | Solo architecture is not organization-wide ownership. |
| R02 | A03,F01,F02,E07 | Two ecosystems only. |
| R03 | D01,D02,V01,E08 | Actual macOS required; no automatic synchronization. |
| R04 | M01,M02,T02,P07 | No results or speedup before execution. |
| R05 | C03,C04,Q02,M02,E05,E06,P08 | Bounded workload evidence, not whole-device guarantees. |
| R06 | M01,M02 | App/SDK/broker startup, not OS boot improvement. |
| R07 | A02,Q01,Q02,E03,E06,P06,P08 | Volatile work and finite experiments, not SLO history. |
| R08 | C03,Q02,M02 | Local load, not distributed fleet scale. |
| R09 | L01,L02,E02,E04,E06,E08,P09 | Commercial shipping stays career evidence. |
| R10 | C02,V01,V02,E01 | Repository standards, not enterprise adoption. |
| R11 | Q01,V02,L01,E03 | Runtime coverage must be separately recorded. |
| R12 | T01,T02 | Assessment is not hands-on experience with every alternative. |
| R13 | External evidence register only | Degree/equivalent practice privately substantiated. |
| R14 | A01,A02,E06,P03–P06 | App Binder versus image-integrated system work distinguished. |
| R15 | F01,F02,E07 | Native plugin execution required. |
| R16 | C02,A01,A02,E05,P01–P06 | No kernel implementation claim. |
| R17 | Q01,T02,E03,P07 | Actual observed problem required, not invented incident. |
| R18 | F02,L02,E04,P09 + career register | Solo/AI-reviewed docs do not prove real cross-team leadership. |
| R19 | C01,D01,D02,P02,P05,P06,E08 | Executed Android/macOS and conditional AOSP only. |
| R20 | External evidence register only | Same-publisher sample is not partner collaboration. |
| R21 | External evidence register only | Repository cannot establish European professional tenure. |
| R22 | External evidence register only | English documentation is not international collaboration. |

Add a non-implementation tracking issue for the external evidence register, with no fabricated assignee or confidential uploads: mark these items `external/not established by repository`, not Done. Do not make degree/partner/history verification a dependency of technical implementation.

## Primary references and review cautions

The ticket contract is a proposed project design, not a claim of current platform execution. Relevant primary documentation already cited in the repository: [Android AIDL](https://developer.android.com/develop/background-work/services/aidl), [bound services](https://developer.android.com/develop/background-work/services/bound-services), [Flutter platform channels](https://docs.flutter.dev/platform-integration/platform-channels), [Java Unix-domain socket address](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/UnixDomainSocketAddress.html), [Macrobenchmark](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview), [AOSP build requirements](https://source.android.com/docs/setup/start/requirements), and [AIDL backends](https://source.android.com/docs/core/architecture/aidl/aidl-backends). E05 should verify the selected Android storage/provider APIs against official documentation at implementation kickoff; do not assert broad provider support from these general IPC sources.

Highest review priorities: remove synthetic-only contradictions; make FD ownership and blocked-I/O limits explicit; prevent path traversal and privacy leakage; do not promise snapshot consistency; preserve volatile epoch semantics; and make reference-versus-native evidence unmistakable. A hiring panel should see a real workflow and a correct architecture boundary, not a board whose green cards merely mean documents exist.
