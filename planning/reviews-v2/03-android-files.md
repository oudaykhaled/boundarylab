# Android FieldPack file boundary — proposed tickets

Status: planning only, 2026-10-30. No native implementation or device evidence is claimed. Inspected baseline backlog and architecture/requirements documents. Root owns integration and publication. These children replace the relevant portions of umbrella estimates; do not add their hours to unchanged parent estimates.

## Recommendation and contract decisions

Deliver an honest **regular-file SAF profile** first. The native/Flutter user chooses documents through Android's system picker; the client opens each authorized content URI read-only and passes a ParcelFileDescriptor through real cross-package Binder. The broker streams SHA-256 using one fixed buffer. Do not resolve a content URI to a filesystem path, request all-files permission, serialize the file into AIDL/Dart, or assume every document provider exposes a seekable file. Unsupported streaming providers receive a typed outcome with a local-download/reselection instruction; supporting them later requires its own bounded-I/O design.

The workflow selects up to 16 attachments, hashes them sequentially, assigns user-approved manifest-relative aliases, and exports a manifest only when all chosen attachments have succeeded. The export contains aliases, observed lengths and checksums, not source URIs/provider names/device paths. It does not transfer attachments to the PC. The recipient independently supplies matching files to the macOS verifier. Individual failure does not become a success-shaped partial manifest. The UI may retain partial results in memory for explanation, but restarting the broker or client does not restore work.

SAF grants authorize the **client**, whereas the transmitted open descriptor provides the broker with file access; no grant forwarding or reopening by URI is needed. An open descriptor is not evidence the user selected that file: authorized same-publisher clients can pass other descriptors they possess. The trust claim is UID authorization plus explicit capability transfer, not proof of UI consent at the broker.

Use ACTION_OPEN_DOCUMENT, CATEGORY_OPENABLE, read grants, and optional multi-selection. Current SAF documentation describes picker-based access and persisted grants; virtual/non-openable documents are excluded. Avoid storing persisted grants by default because jobs are volatile. An optional “remember selection” future feature must explicitly retain only granted read permission, handle revocation/removal, and release grants when forgotten. A persistable grant would not preserve a broker job. [SAF guidance](https://developer.android.com/training/data-storage/shared/documents-files).

`openFileDescriptor(uri, "r", cancellationSignal)` runs off the UI thread. Null, SecurityException, FileNotFoundException and cancellation are explicit failures. Providers can return pipes/sockets even for read mode; raw FD opening also excludes subsection-only providers. Do not ask for write access merely to force seekability. These limitations are documented by [ContentResolver](https://developer.android.com/reference/android/content/ContentResolver).

### Ingress, ownership and resource contract

Add a separately versioned file operation with one input PFD and bounded scalar metadata. Keep synthetic work for deterministic performance comparison. Server identity remains `(captured calling UID including Android user, session token, epoch)`; capture caller UID and authorize synchronously before dispatch. Each status/cancel/session call repeats owner checks. A supplied package or owner field is never authoritative, and PID is diagnostic only. [Binder identity reference](https://developer.android.com/reference/android/os/Binder).

The remote Stub takes ownership of its unmarshalled input PFD and either transfers that object to a tracked job or closes it on rejection. Client closes its own PFD in `finally` after the synchronous submit attempt, including response failure. SDK does not expose an ambiguous “callee closes my Java object” API; local same-process calls are outside this cross-package contract and must have a separately tested wrapper if introduced. Do not call detachFd. Native cleanup paths have one resource owner; close is idempotently guarded. AIDL metadata carries neither bytes nor filename paths. [ParcelFileDescriptor](https://developer.android.com/reference/android/os/ParcelFileDescriptor) documents descriptor ownership operations and shared seek state on duplication.

Do lightweight permission/version/size checks, quota reservation and handle registration on Binder ingress; defer fstat/read/provider-related work to worker execution. No file-content I/O on Binder threads. Admission checks run atomically with session/death state. Fixed proposal: 64 KiB metadata ceiling is **not** needed; preserve existing 4 KiB descriptor-metadata ceiling, one worker, 16 queued jobs globally, four outstanding jobs/session, 16 sessions and 1 MiB retained metadata. Add maximum 17 retained input PFDs (one running plus 16 queued), plus bounded transient transport handling tied to configured Binder concurrency. No unbounded asynchronous intake queue. A file consumes at most 128 MiB; a pack permits at most 16 files and 128 MiB aggregate observed bytes. Reject oversize metadata before allocation. Freeze Binder handler concurrency and descriptor headroom at implementation spike rather than promise exactly 17 total process FDs.

The worker accepts only `S_ISREG(fstat(fd).st_mode)` and readable descriptors. It records initial size and rejects values above the remaining declared byte budget. Use `Os.pread` with explicit offsets to avoid shared-descriptor-position races. Empty regular files are valid. Fixed-length initial-size hashing plus a final stat/size check prevents unbounded append workloads; short reads before initial length, size change or observed metadata change fail with `SOURCE_CHANGED`. A one-byte boundary probe can detect EOF discrepancies but never makes mutability detection complete. Android exposes `fstat` and positional reads in [android.system.Os](https://developer.android.com/reference/android/system/Os).

**Critical limit:** even matching before/after size and timestamps cannot prove immutability. Another writer can change bytes during hashing; a digest describes the bytes actually observed, potentially spanning mutations. Manifest semantics must say `observationMode: streamed` / equivalent, not “atomic snapshot.” Guaranteeing a consistent point-in-time snapshot requires provider cooperation or a separately designed staging flow; even copying mutable input does not prove that source was coherent during the copy. Tests must demonstrate detectable mutation failure and document undetectable races. Do not claim “detects every modification while hashing.”

Session death/close requests cancellation and immediately releases queued descriptors. For running work, mark terminal cancellation according to the serialized existing state rule and suppress any late success; the worker owns and closes its FD in finally after I/O returns. Do not close/reuse a raw FD concurrently with a running read or assume Thread.interrupt reliably interrupts every filesystem/provider operation. Deadline expiry likewise does not free a busy physical worker slot early. If a read hangs, stop admission when capacity is exhausted, expose unavailable/stalled state, and require explicit broker restart for recovery in the lab. A restart invalidates *all* epoch handles and yields LOST/UNKNOWN; it never means successful cancellation of every external side effect. This operation is read-only. No unsafe thread kill or unbounded replacement-worker pool.

## Tickets

All tickets are `type: implementation`, status Planned. Dependencies below refer to child work; B01/B02/B03 references mean the existing frozen contract/core/broker prerequisites, not completed work. Implement subcontracts before dependent integrations. Release acceptance remains blocked until the corresponding executed evidence exists.

### AF01 — Freeze file capability, budget and observation contract

- parent: B01; phase: G1; priority: P0; owner_role: Android middleware architect; depends_on: []; estimate_hours_range: [4, 7].
- user_value: Consumers know which real documents can be safely submitted and what their manifest proves.
- scope: File operation/version; errors; descriptor ledger; limits; observation semantics; contract delta replacing the old real-files exclusion. Preserve pure JVM core by passing a platform-neutral bounded chunk reader/result interface, not Android types into core.
- acceptance: Define `ACCESS_DENIED`, `OPEN_FAILED`, `PROVIDER_UNAVAILABLE`, `UNSUPPORTED_DESCRIPTOR`, `SOURCE_CHANGED`, `TOO_LARGE`, `IO_ERROR`, `BUSY`, and existing cancellation/deadline/LOST outcomes. Freeze EOF and zero-length rules, resource reclamation, manifest gating, and all caps. Specify duplicate request IDs for this operation return `DUPLICATE_REQUEST` within retention, closing incoming PFD, rather than pretending descriptor content equality; status-by-request is the uncertain-submit recovery route. After eviction, no duplicate guarantee.
- verification: Tabletop state transitions for timeout-after-admission, duplicate-ID/different-FD, client death at admission, mutation and stale epoch. Contract golden vectors reviewed before coding.
- evidence: ADR, API/error table, ownership sequence diagram, platform/core interface specification and negative-vector inventory.
- risks: Changing operation semantics may need protocol-major or separate-operation negotiation. Align with compiled old consumers in B08.
- requirement_ids: [R01,R05,R07,R08,R10,R12,R16].

### AF02 — Implement native SAF selection and scoped open lifecycle

- parent: B04; phase: G1; priority: P0; owner_role: Android application engineer; depends_on: [AF01]; estimate_hours_range: [6, 10].
- user_value: A user can select actual authorized attachments without broad storage permission.
- scope: Picker, bounded result count, selection aliases, provider metadata as untrusted hints, cancellable worker-thread opens, no persisted URI grants in baseline.
- acceptance: Picker cancellation creates no job. Excess selections rejected before opening any FD. Denied/revoked URI, missing document, null provider result, virtual document, subsection-only provider, unexpected non-content scheme and user navigation away each produce typed UI results and release resources. Display name/size can be null or misleading; neither controls allocation. No URI-to-path conversion; no MANAGE_EXTERNAL_STORAGE; no read/write open. Activity recreation cannot accidentally submit twice. A late successful open after user cancellation closes its descriptor without submission.
- verification: Instrumented test DocumentsProvider and actual system picker with project-owned files; permission revocation before open; delayed open/cancel barrier; resource counts return to baseline after 100 cycles.
- evidence: Native test report, manifest-permission inspection, redacted state receipts and one short picker recording with fixture aliases only.
- risks: Provider implementations can ignore cancellation; cap open concurrency and avoid unbounded retries. Grant removal does not imply already-open FDs are instantly revoked.
- requirement_ids: [R07,R09,R11,R14,R17].

### AF03 — Transfer PFDs through owner-checked cross-package Binder

- parent: B03; phase: G1; priority: P0; owner_role: Android IPC engineer; depends_on: [AF01,B03]; estimate_hours_range: [8, 13].
- user_value: Both permitted clients use the real broker without sharing file paths or granting it broad storage access.
- scope: Separate file-submit AIDL operation, synchronous bounded admission, immutable caller capture, per-job descriptor ledger, SDK uncertainty handling.
- acceptance: Separately installed client opens a fixture URI the broker cannot reopen; broker hashes received descriptor successfully. Wrong-signature binding and forged cross-UID session/status/cancel fail. Passing a third-party Binder reference does not bypass method authorization. UID captured before dispatch remains correct after thread hopping. Client closes its PFD after submit while accepted broker job remains valid. Null/closed/writable-only descriptor, rejected admission, duplicate request, transaction exception and client death during registration produce neither leak nor accidental success. A reply lost after acceptance is UNKNOWN to caller until queried, never automatically replayed.
- verification: Two approved packages, one differently signed denial client, separate UIDs, barrier-controlled worker tests and real Binder instrumentation. Verify service permission plus every method guard.
- evidence: Package/PID/UID receipt (no personal file details), authorization matrix, FD ledger deltas and failed/successful transaction traces.
- risks: Avoid fake in-process Binder tests as sole proof. OS transport creates transient FDs even when application admission rejects; bound accepted retention and verify headroom under configured concurrency.
- requirement_ids: [R02,R05,R07,R10,R14,R16].

### AF04 — Stream bounded regular-file hashing with mutation outcomes

- parent: B02; phase: G1; priority: P0; owner_role: Kotlin core and Android storage engineer; depends_on: [AF01,B02]; estimate_hours_range: [8, 14].
- user_value: Large real attachments receive verifiable checksums without whole-file memory.
- scope: Android regular-file reader, fixed chunk digest adapter to pure JVM core, explicit byte counters, observation metadata and stable error mapping.
- acceptance: Independent SHA-256 oracle agrees for empty, 1-byte, buffer−1/buffer/buffer+1, cap−1/cap and multi-chunk files. cap+1 rejected. Pipe/socket/directory/nonseekable descriptors fail before content reading; no blocking pipe read to “test” support. Advancing client-side shared position does not change positional result. Negative/overflow sizes, unreadable descriptors, premature EOF and append/truncate between barriers cannot produce success. Metadata equal before/after is described as best-effort checking, not immutable snapshot verification. No byte array or retained buffer scales with payload size. Metadata changes after success are outside the completed observation.
- verification: Pure-core golden vectors plus Android FD tests; mutable controlled fixture; heap allocation recording at 1 MiB versus 128 MiB; short-read/error injection at reader interface plus separately labelled real regular-file tests.
- evidence: Oracle comparisons, byte-count assertions, allocation report and tested descriptor support matrix.
- risks: fstat/reads can stall on provider-backed files even when regular. Extra bytes/read probes count toward I/O accounting and must remain bounded.
- requirement_ids: [R04,R05,R07,R08,R11,R14,R17].

### AF05 — Prove descriptor cleanup, death and cancellation races

- parent: B06; phase: G2; priority: P0; owner_role: Android reliability QA; depends_on: [AF03,AF04]; estimate_hours_range: [8, 14].
- user_value: Cancelling or losing the connection does not leak file access or fabricate completed integrity results.
- scope: Deterministic checkpoint corpus, descriptor accounting and real package death/rebind runs.
- acceptance: Exercise cancel-before-admission, queued cancellation, running cancellation, complete-versus-cancel both orders, closeSession-versus-admission, death-before-link/after-link, broker death before/after completion response and old-epoch status. At most one terminal outcome wins. No late success overwrites cancellation/deadline. Queued descriptors close promptly; active descriptor and worker capacity are reclaimed only when worker exits. No replacement-worker growth for stalled I/O. Every surviving client marks old jobs LOST/UNKNOWN after broker death. Client retries require new explicit work; no silent restore. Repeated denied/overloaded calls leave retained FD count flat.
- verification: 100 iterations per selected deterministic race cell, with exact actual counts and failures recorded; 30-minute capped churn; a test-only blocked reader models noninterruptible I/O separately from native descriptor tests. Confirm release artifacts exclude stall/kill hooks.
- evidence: Race matrix, terminal-state logs, queue/FD/worker counters, PSS context and native process-death receipts.
- risks: Never infer OS-level blocked-I/O guarantees from a fake reader. Leaked resources after failed tests must be visible in the report rather than removed from statistics.
- requirement_ids: [R05,R07,R08,R11,R16,R17].

### AF06 — Integrate real-file FieldPack native and Flutter acceptance flow

- parent: B05; phase: G1 integration/G2 validation; priority: P1; owner_role: Android/Flutter integration engineer; depends_on: [AF02,AF03,AF04]; estimate_hours_range: [8, 14].
- user_value: Native and Flutter consumers produce the same portable inspection manifest from real user-selected files.
- scope: SDK calls from authored Flutter native plugin; aliases and progress; aggregate limits; manifest schema owned by root's separate manifest workstream. No FD numbers or entire bytes cross Dart channels.
- acceptance: Each installed client selects the same fixtures, calls the broker and emits matching digest/length entries using the approved versioned schema. Source URIs, display names without approval, absolute paths and raw bytes never enter evidence or manifest. Alias collisions are rejected before export. One failed/cancelled attachment blocks a complete manifest. An exact aggregate budget passes; adding one byte fails without overflow or partial-success export. Flutter Activity recreation and engine disposal close pending opens/session demand; stale callbacks do not publish a manifest. Export destination failure is surfaced and not called successful export.
- verification: Native and Flutter physical-device flow, source-fixture changes, second engine disposal tests, exported JSON schema validation and separately supplied fixture directory verification by reference utility. Required dependency: manifest-schema freeze from root's manifest owner, keyed during integration; no reliance on another agent's unpublished plan.
- evidence: Five-minute recording, exact fixture generator/version, redacted manifests and consumer/broker receipts; Python verification explicitly labelled reference-utility evidence, not native proof.
- risks: Manifest/export work is a separate slice; coordinate schema dependency without adding whole umbrella estimate twice. Flutter plugin needs lifecycle handling beyond a happy-path MethodChannel call.
- requirement_ids: [R02,R03,R09,R11,R15,R18].

## Integration amendments, sequencing and estimation

B01 freezes AF01 and adds FD caps/support profile to capabilities. B02 gains AF04 while retaining deterministic synthetic workload. B03 gains AF03 without sacrificing per-method UID checks. B04 gains AF02 and actual authorized-file workflow. B05 gains AF06; it need not wait for the entire B04 test campaign once shared SDK contracts and SAF component are ready. B06 gains AF05. B08 adds old-client behavior when file capability absent, and unknown new error handling. B09 separates provider-open, Binder transfer, queue, file I/O and digest CPU costs; never compare real-file time to generated-byte compute as equivalent throughput. B10 includes honest provider support/export instructions. B11 records the streaming-versus-regular-file decision and revisit trigger. B12–B16 stay conditional; real-file support does not automatically extend into the AOSP daemon.

AF01 precedes independent AF02/AF03/AF04 work. AF05 and AF06 can then proceed independently. Estimated Android file work: **42–72 person-hours**, replacing overlapping synthetic-only integration effort where appropriate. Manifest-schema/export implementation and desktop verifier are not included in this subtotal. The new scope requires re-estimating the old 40/120/240h gates; it must not be squeezed in by deleting negative tests or silently claiming generic SAF support.

## Remaining unknowns, exclusions and traceability

Confirm target Android API endpoints, actual picker/provider behavior, reliable-error FD behavior and FD accounting headroom on declared devices. No device claim exists yet. No iOS, uploads, scanning, authenticity/signatures, automatic file copying/sync, persisted jobs, exact cancellation latency, general streaming-provider support or malicious-same-UID protection is promised. The ordinary broker remains app-accessible userspace middleware, not kernel work.

Direct tickets cover R01–R05, R07–R12, R14–R18. R06 remains B09 startup evidence; measure file adapter registration/open separately from startup. R19 receives Android framework exposure only; PC evidence remains the separate host track. R13 qualification, R20 partner work, R21 European tenure and R22 international collaboration remain external and cannot be established by this workflow. R09 commercial relevance does not mean commercial shipping; R18 documentation does not establish actual cross-team collaboration. All sources linked above are primary Android documentation, checked on the planning date. Freeze exact SDK versions and execute tests before declaring supported versions.
