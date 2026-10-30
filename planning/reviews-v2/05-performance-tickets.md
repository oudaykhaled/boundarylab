# FieldPack performance and QA ticket amendments

Status: proposed, unimplemented. Independent planning report 05, 2026-10-30. No benchmark results, native build results or GitHub board changes are claimed.

## Recommendation and contract decisions

Replace the generated-byte performance story with a two-layer experiment: (1) controlled transport/policy measurements, retaining the synthetic operation as a diagnostic control; (2) actual file-stream hashing and exported-manifest verification. The real workflow is selecting authorized inspection attachments, producing SHA-256/observed-length records, then checking a separately supplied directory using the macOS verifier. The Python reference can demonstrate manifest correctness now but cannot supply Binder, Flutter, Kotlin memory or Android startup evidence.

Keep the baseline one worker, bounded queue and polling. File bytes must not become one Dart byte array, one Binder parcel or a queued in-memory copy. Android transports an opened read-only descriptor with explicitly transferred ownership; the broker streams through a fixed buffer. Flutter transports selection identifiers/control metadata through its native adapter. Desktop file streaming uses independently supplied local files; no phone-to-PC transfer is implied.

Recommended initial support: authorized local regular files whose opened descriptor passes a documented capability check. Support arbitrary unknown-length/non-seekable provider streams only after P05 below establishes safe liveness, or return explicit `UNSUPPORTED_SOURCE` before admission. Android read-only provider opens may yield pipes/socket pairs and metadata size may be unknown [S1,S2]. “Local picker selection” alone cannot establish seekability, immutability or lack of provider delay. Do not silently copy to staging: that changes storage cost, privacy and quotas and needs its own explicit design.

Hash observed bytes, not an asserted immutable snapshot. Record actual length; a changed known size fails the declared stability check. Same-size modifications cannot generally be detected by stat-before/after. A successful manifest proves a digest of the bytes read; it does not establish that those bytes existed simultaneously, are authentic, safe or unmodified during reading. Preserve this limitation in UI/docs. Independent recipient verification detects divergence from the recorded digest at verification time.

Freeze proposed caps in B01: 64 KiB work buffer; 128 MiB per input stream; 32 files and 512 MiB actual hashed bytes per pack; one worker; existing 16-job global queue; one owned source FD per queued file job and bounded sessions/results. Submit files incrementally rather than retaining 32 descriptors per pack. Explicitly bound encoded names, manifest bytes, open descriptors, pending provider-open requests and partial-result retention. These proposed values are reviewable defaults, not measured limits.

Unknown-size policy if streaming is enabled: metadata never reserves unlimited work; account bytes read cumulatively, including failed attempts. At the cap, inspect at most one extra byte to distinguish exact-limit EOF from oversized input, count that probe separately, then fail without hashing/exporting excess bytes. A probe can block too; P05 must cover it. Pack quota rejection must not label an incomplete pack verified. Success entries require EOF/error checks and final race arbitration.

## Amend the umbrella issues

- **B01:** freeze supported descriptor types, acquisition/ownership, observed-byte semantics, byte/FD/name/manifest quotas and blocking-I/O cancellation policy. Add a measured-data privacy policy.
- **B02:** replace generated-byte-only oracle with actual stream oracle; preserve synthetic control for attribution. Cap actual reads and partial state, not just declared length.
- **B03/B05:** prove native FD handoff and Flutter control-only bridge; forbid whole-file buffering. Report acquisition error separately from job failure.
- **B06:** add provider stall/death, mutation, descriptor leak, exact-limit EOF/probe, unknown length and cancellation while read is blocked. Keep deterministic checkpoint orchestration.
- **B09:** child tickets P01/P02/P06/P07/P08 below replace the 10–20h monolithic estimate. B09 need not wait for desktop B07 or all compatibility B08 to begin Android harness work. Execution depends on working native/file and Flutter paths.
- **B10:** demo real fixture-backed creation, changed-file and missing-file verification, with exact artifact links. Native and Python-reference status remain separate.
- **B11:** case study must distinguish provider/storage wait, hashing CPU, IPC and polling delay before suggesting optimization.
- **B15:** reuse this campaign with a separately labeled AOSP profile; do not pool virtual-device platform measurements with physical-phone data.

Parents are roll-ups; never add parent estimates to child estimates. Existing epic dependencies should reference concrete deliverables rather than force all work into one serial chain.

## Implementation-sized tickets

Keys below are proposal identifiers, not existing GitHub issue numbers. All tickets start Planned. No ticket depends on its own parent. “B03 file path” means completion of its amended real-file transport acceptance, not merely the old synthetic implementation.

### P01 — Freeze FieldPack benchmark specification and input matrix

- **Parent/type/phase/priority:** B09 / design+QA / G1 / P0.
- **Owner:** Principal Frontend Performance Engineer with Android implementer review.
- **Depends on:** B01 amended file contract.
- **Estimate:** 4–6 person-hours.
- **User value/scope:** Make performance claims interpretable for real inspection attachments; predeclare comparisons and measured milestones.
- **Acceptance:** (1) Commit hypotheses, baselines, metrics, sample counts, supported input classes, hard caps and invalid-run rules before confirmatory execution. (2) Enumerate zero-byte, 1 KiB, 1 MiB, 16 MiB, exact 128 MiB, limit+1 byte, 32-small-file and mixed-pack cells. Keep adversarial cells separate from speed comparisons. (3) Name physical phone and release/compilation mode or mark execution blocked. (4) Specify prewarmed-page-cache and uncontrolled-cache treatments; never label process restart as disk-cold. (5) Define no-work shell and same-reader/same-hash in-process baseline per framework; Flutter uses the native reader in both treatments.
- **Verification/evidence:** Reviewable protocol JSON/Markdown, device inventory without serials, frozen commit, matrix with planned run count and predicted wall time.
- **Risks:** Hardware not available; provider behavior differs; target revisions require a documented pre-confirmation amendment.
- **Requirements:** R01,R04,R05,R06,R08,R10,R11.

### P02 — Build real-file corpus and controllable provider test fixture

- **Parent/type/phase/priority:** B06 / test infrastructure / G1 / P0.
- **Owner:** Principal Mobile QA Lead / Android engineer.
- **Depends on:** B01 amended file contract; may proceed alongside P01.
- **Estimate:** 6–10 person-hours.
- **User value/scope:** Reproduce field-file failures without disclosing actual inspection data.
- **Acceptance:** (1) Deterministic project-owned text, binary and image-like fixture files have independent SHA-256 and exact byte counts; large fixtures generated on demand. (2) Test provider exposes regular descriptor, unknown-size sequential source, offset/length asset or explicit rejection, delayed open, short read, midstream failure, stalled stream and controlled mutation. (3) Barriers reproduce truncate, append and same-size rewrite during hashing; distinguish detectable and inherently undetectable mutation. (4) Corpus generator fails cleanly on insufficient space and cleans only its own files. (5) Fixture-only provider/fault hooks absent from release packages.
- **Verification/evidence:** Corpus manifest/generator seed, independent oracle outputs, instrumented scenario tests, release inspection receipt.
- **Risks:** Test provider is a fault model, not proof every third-party provider behaves identically. Raw offset-bearing assets need correct length/offset handling or explicit unsupported status [S1].
- **Requirements:** R05,R07,R11,R14,R17.

### P03 — Enforce stream byte, buffer, descriptor and pack caps

- **Parent/type/phase/priority:** B02 / implementation+unit/integration tests / G1 / P0.
- **Owner:** Android/Kotlin implementer, with QA review of resource invariants.
- **Depends on:** B01 amended file contract; fixture-assisted verification depends on P02.
- **Estimate:** 6–10 person-hours.
- **User value/scope:** Large or misleading files cannot create unbounded memory/resource use.
- **Acceptance:** (1) Reads use fixed 64 KiB storage, never allocate from provider-reported size; observed bytes, not declarations, enforce caps. (2) 0/cap−1/cap/cap+1 and unknown/misreported-size streams match contract; cap+1 yields resource-limit failure, never success. (3) At most one probe byte beyond cap if supported, with counter/test. (4) Submit rejection, cancellation, timeout, completed result, client loss and broker loss each release the receiver-owned descriptor exactly once; sender ownership tests prove no premature close. (5) FD/session/result/buffer caps hold under 100 rejected submissions and 1,000 open/close cycles; terminal result TTL returns logical counters to baseline. (6) Partial failures consume configured pack resource budget and cannot evade it through retries; no full-file copies in Dart/plugin/core.
- **Verification/evidence:** Boundary/property tests, real-process FD ownership tests, counter snapshots, allocation trace, lifecycle table.
- **Risks:** Metadata can lie; OS allocator footprint is not identical to logical retained bytes. Duplication creates ownership complexity.
- **Requirements:** R05,R07,R08,R14,R16.

### P04 — Real-file lifecycle, race and verifier QA matrix

- **Parent/type/phase/priority:** B06 / integration QA / G2 / P0.
- **Owner:** Principal Backend QA Lead with Principal Mobile QA Lead.
- **Depends on:** P02,P03,B03 file path; Flutter-specific cells also B05.
- **Estimate:** 8–14 person-hours, excluding final campaign execution in P07.
- **User value/scope:** Avoid successful-looking manifests after incomplete or ambiguous work.
- **Acceptance:** (1) Open denied/revoked, missing, short-read/error and detected size-change cases produce explicit failure and no success entry. (2) Cancel-before-read, cancel-before-terminal, completion-before-cancel and disconnect-after-admission use barriers; exactly one authoritative terminal state persists. (3) Broker death yields LOST/UNKNOWN; reconnect invalidates handles and explicit resubmission has new epoch identity. (4) A partial pack cannot export an all-files-success status. (5) Verifier distinguishes changed/missing/read-error files with nonzero exit, rejects malformed/oversized/path-traversal manifests, and handles zero-byte files. (6) Native and Flutter vector outcomes match; Python tests count only as reference evidence. (7) One run per case in PR smoke; release campaign defines 100 repeats for each required deterministic race/fault cell, with exact count reported.
- **Verification/evidence:** Test IDs tied to contract clauses, seed/barrier logs, epoch and outcome receipts, failed-case artifacts.
- **Risks:** “At most one terminal state” is not exactly-once execution; manifest atomicity policy must be decided separately.
- **Requirements:** R02,R03,R07,R10,R11,R15,R17.

### P05 — Prove blocked-read cancellation or explicitly restrict stream support

- **Parent/type/phase/priority:** B03 / feasibility+QA / G1 / P0.
- **Owner:** Principal Android/framework engineer.
- **Depends on:** B01 amended descriptor/cancellation decision,P02; does not wait for full B05.
- **Estimate:** 6–12 person-hours.
- **User value/scope:** A bad provider must not silently occupy the only worker forever.
- **Acceptance:** (1) Stall before first byte, mid-read and at cap EOF probe; test cancellation, client death and deadline separately. (2) Record cancellation-request acknowledgement, authoritative terminal state and actual I/O/worker quiescence as distinct events. (3) Demonstrate a bounded cleanup mechanism on supported source types; thread interruption or descriptor-close assumptions alone are not evidence. (4) Required regular-file cooperative cancellation target is ≤250 ms p95 on the named reference phone; any blocked-I/O guarantee is separately established with a hard 5 s diagnostic deadline. (5) If cleanup cannot be proven, reject that source class before admission and publish the restriction; never report cancellation while orphaned work consumes the only worker. (6) A second authorized client progresses after cleanup; no hidden process restart destroys unrelated jobs while pretending successful cancellation.
- **Verification/evidence:** Real stalled provider integration runs, worker/FD snapshots before and after, timed trace, explicit support matrix and failed-spike ADR if applicable.
- **Risks:** Arbitrary regular-file I/O can also stall; guarantees must be scoped to tested mechanisms. Provider cancellation signals do not alone prove every later read is interruptible. A worker-process isolation redesign changes scope and estimates.
- **Requirements:** R05,R07,R08,R14,R16,R17.

### P06 — Instrument fair baselines and file-stage timing

- **Parent/type/phase/priority:** B09 / benchmark infrastructure / G2 / P0.
- **Owner:** Principal Frontend Performance Engineer + Android engineer.
- **Depends on:** P01,B03 file path; Flutter treatment depends on B05.
- **Estimate:** 8–12 person-hours.
- **User value/scope:** Locate first-use and slow-file delays without blaming transport for storage costs.
- **Acceptance:** (1) Native/Flutter no-work shell, native/Flutter same-native-reader in-process control, and corresponding broker treatment use equal corpus, chunk size, algorithm, export behavior and polling schedule. (2) Record launch/frame, picker result, provider-open start/end, descriptor handoff, admission, first read, read wait, hash CPU, queue wait, EOF, terminal commit, poll request/response, user observation and manifest-write milestones. (3) Report terminal-observation minus terminal-commit separately; polling delay is not Binder latency. (4) Control RPC round trip is measured independently of file reads and polling sleep; FD handoff is not described as sending all file bytes through Binder. (5) No private names/URI/path/content in events; IDs map only to public fixtures. (6) Compare trace-on/off pilot overhead and use low-overhead counters for confirmatory timing; memory sampling occurs in a separate run. (7) Desktop socket framing/copy costs are separately described, not assumed equivalent to Android FD handoff.
- **Verification/evidence:** Trace schema, monotonic-clock/correlation-ID description, SQL extraction queries, sample raw trace, counter consistency test, baseline parity checklist.
- **Risks:** Stage totals may overlap; do not sum concurrent spans as elapsed latency. Missing trace events/overrun invalidate stage attribution, not silently become zero duration.
- **Requirements:** R04,R05,R06,R08,R11,R16.

### P07 — Execute bounded physical-device startup/file/load/memory campaign

- **Parent/type/phase/priority:** B09 / experiment execution / G2 / P1.
- **Owner:** Principal Mobile QA Lead with performance engineer.
- **Depends on:** P03,P04,P05,P06; desktop extension separately depends on B07.
- **Estimate:** 10–18 active person-hours plus approximately 12–24 reserved device-hours; estimate after ten-pair pilot.
- **User value/scope:** Deliver measured evidence for the supported input classes with explicitly bounded uncertainty.
- **Acceptance:** (1) Ten paired pilot trials are separate from confirmation. (2) Startup: three independent sessions ×30 launches ×four core native/Flutter in-process/broker treatments =360 launches per declared process/service state; additional states multiply the budget explicitly. Shell controls may use a smaller labeled diagnostic sample and cannot be pooled. (3) File speed: three sessions ×five windows ×two treatments ×three selected sizes =90 windows for native; Flutter gets a separately declared confirmation subset. (4) Load: three sessions ×five 60 s windows ×four concurrency levels =60 min/treatment plus warmups; supplement with one fixed-arrival overload condition, preserve offered/admitted/rejected/completed counts. (5) Three 30-minute memory/churn sessions record all client+broker PSS and logical byte/FD/session counters; desktop RSS reported independently. (6) Randomized balanced blocks, identical compilation/profile state, environment manifests, thermal stabilization, actual cache condition and invalid-run log accompany every cell. (7) Exact completed/planned cells and all failures are reported; no reduced matrix masquerades as the full campaign.
- **Verification/evidence:** Run manifest, raw observations, sanitized traces, checksums, physical-device receipt, cold/service-resident labels, CPU/storage/battery/thermal notes.
- **Risks:** Minimum startup and load matrix already exceeds a casual one-hour run. Provider variants and extra devices require explicit extra campaigns; no full Cartesian product is implied. Diagnostic provider delay must not be pooled with local-file throughput.
- **Requirements:** R04,R05,R06,R07,R08,R11.

### P08 — Analyze falsifiable results and publish reproducibility bundle

- **Parent/type/phase/priority:** B09/B11 (choose B09 as sole parent) / analysis+docs / G2 / P1.
- **Owner:** Principal Frontend Performance Engineer, reviewed by Staff Documentation Writer.
- **Depends on:** P07; analysis script scaffolding may start after P01.
- **Estimate:** 6–10 person-hours.
- **User value/scope:** Allow a reviewer to reproduce conclusions and recognize limits.
- **Acceptance:** (1) Separate storage/provider wait, hashing, IPC and observation; output throughput and byte counts for successful and unsuccessful work. (2) Report distributions per session/window, clustered uncertainty, sample counts and all failure rates; 90 startup samples/treatment do not justify a stable p99. (3) H1 chunk-size gain is rejected if cancellation or memory gate fails; H2 lazy initialization fails user-value claim if first useful manifest becomes slower beyond frozen budget; H3 memory-boundedness fails on cap breach or persistent logical resource growth. (4) Proposed first-use overhead target is ≤100 ms median versus equal framework/input baseline; control RPC p95 target ≤10 ms at one client; no universal file-MiB/s claim. Calibrate and freeze hardware-dependent targets before confirmation. (5) After TTL/drain, logical FD/session/result counters equal baseline; three memory sessions must report PSS trend/settled footprint separately from hard logical caps. Freeze a PSS budget from pilot before confirmation. (6) Any improvement claim needs paired raw data, interval and protected metrics; inconclusive/negative remains such. (7) Bundle tools/commit/artifact hashes, fixture oracle, analysis code, trace config, exclusion log, limitations, and requirements ledger; clean rerun regenerates figures.
- **Verification/evidence:** Reproduction command and receipt, report with no asserted unexecuted cells, source manifest, baseline/candidate comparisons if a real change was tested.
- **Risks:** Three sessions support limited generalization; confidence interval computation cannot manufacture independent replicates. Author-only reproduction is labeled as such.
- **Requirements:** R01,R04,R05,R06,R09,R11,R12,R17,R18.

## Campaign controls and failure decisions

Storage is an experimental factor. Reopening a file or restarting an app does not evict page cache. Prewarm both paired treatments identically; use fresh generated files only under the explicit label “uncontrolled cache,” not “cold disk.” Do not flush global caches or perturb the user's workstation. Record free storage, file-system/provider identity and storage location; document provider open/metadata costs separately. A local provider can still produce delayed streams. Randomizing order reduces systematic warm-cache advantage but does not guarantee cache equivalence.

A failed hard gate blocks the relevant implementation ticket. A performance target miss is a measured finding requiring an architectural decision, not grounds to rewrite raw data or silently relax criteria. B09 closes only after the report distinguishes achieved targets, misses and unexecuted cells. No quota/security/ownership failure becomes acceptable because throughput improved.

For staged delivery, first run one smoke per supported file/provider cell and native-only pilot. Complete native evidence before multiplying Flutter/provider/device combinations. Estimated child work above is **54–92 active hours** for this performance/QA stream, not the entire FieldPack implementation; reserve device time separately. The previous B09 10–20h estimate cannot absorb real-file harness, provider faults and a defensible campaign. Avoid summing the replaced B06/B09 parent estimates again. AOSP-specific profiling and a second-device replication are excluded from these totals.

## Traceability and scope exclusions

Primary coverage: R04/R05/R06 performance-memory-startup; R07/R08 fault containment and bounded load; R11 auditable QA; R14/R16 real Android file/Binder behavior; R17 actual difficult-case investigation. Supporting artifacts cover R01 roadmap, R02 native/Flutter parity, R03 independent macOS verification, R09 runnable release relevance, R10 contracts, R12 measured technology choices, R15 Flutter control bridge, R18 clear communication and R19 Android/macOS exposure. R13 degree/equivalence, R20 genuine partner work, R21 European tenure and R22 international collaboration are not created by benchmark tickets. An authored review guide does not prove real cross-team review.

Exclude arbitrary provider support until liveness is established, virtual/transformed documents unless explicitly modeled, unbounded files, network upload, authenticity claims, immutable snapshots, atomic cross-file snapshots, automatic synchronization, persistent/exactly-once jobs, universal storage benchmarks, production SLOs and kernel claims. No user-file payload/name should enter public benchmark evidence.

## Primary sources checked 2026-10-30

- **S1:** [Android ContentResolver](https://developer.android.com/reference/android/content/ContentResolver): read-only descriptors may be pipes/socket pairs; raw descriptor APIs have subsection limitations. Ground source-capability decisions in actual supported APIs.
- **S2:** [Android OpenableColumns](https://developer.android.com/reference/android/provider/OpenableColumns): SIZE can be null when unknown; quotas cannot rely on metadata availability.
- **S3:** [Android ParcelFileDescriptor](https://developer.android.com/reference/android/os/ParcelFileDescriptor): descriptor ownership/close and reliable-pipe error detection APIs; error detection is capability-dependent, not universal EOF correctness.
- **S4:** [Macrobenchmark metrics](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics): Android startup metric definitions and measurement instrumentation; project-specific useful-file milestones remain additional events.
- **S5:** [Perfetto native heap profiling](https://perfetto.dev/docs/data-sources/native-heap-profiler): use sampled allocation attribution as a diagnostic separate from process memory accounting and ordinary latency runs.
