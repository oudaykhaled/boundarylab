# FieldPack reliability/security contract and actionable ticket review

Planning report, 30 October 2026. Owns no public changes. Baseline read: `planning/backlog.json`, B01–B16. Existing issues are planned; the following tickets do not imply native implementation. Estimates replace relevant umbrella portions and must not be added to umbrella totals.

## Recommendation: a deliberately narrow but useful integrity tool

Implement the authorized Python stdlib reference utility for **explicitly selected, regular local files in a stable, user-controlled directory**. Generate a versioned manifest of logical names, observed byte counts and SHA-256 digests; verify against a separately supplied directory. It must operate on arbitrary supported user-specified files, not recognize a hard-coded fixture. It neither copies attachments nor uploads anything. A digest establishes observed-byte integrity relative to a supplied manifest, not authenticity, malware safety, provenance or a snapshot of concurrently changing files.

Keep the reference implementation flat-directory-only for its first release. This substantially reduces path traversal and intermediate-component races while supporting a real folder of inspection photos, notes and PDFs. A logical name is one portable ASCII filename, 1–120 characters, matching `[A-Za-z0-9][A-Za-z0-9._-]*`; reject `.`/`..`, slash, backslash, colon, controls and absolute/drive/UNC forms. Reject names ending in a dot and case-fold collisions; document unsupported Unicode and nested folders rather than silently rewriting names. Reject Windows device names if describing names as portable. Do not accept display names returned by Android providers as safe filesystem paths. Provide an explicit local mapping to safe logical names in native clients later.

Reference defaults: 100 files, 256 MiB/file, 1 GiB aggregate observed bytes, 1 MiB manifest, 64 KiB read buffer, one active input FD. Configuration overrides require explicit bounded validation, not unlimited mode. Count bytes actually read, not solely stat-reported sizes. Files above caps fail; no partial success manifest. Strict manifest parser rejects duplicate JSON keys, duplicate/case-fold-colliding names, unsupported versions/algorithms, negative or boolean sizes, wrong digest syntax and more than the entry cap. Read the manifest with a cap before JSON parsing. Unknown fields should reject in v1 to avoid accidental semantic ambiguity; evolution requires an explicit schema decision.

Verification ignores unrelated directory entries and explicitly says so. Each listed file is required. Missing, changed, unreadable or unsupported files produce a nonzero result; malformed/unsafe input is distinguished from content mismatch. Never write into the verification directory. Print sanitized logical names and error codes, not source absolute paths, provider URIs, stack traces or file contents. File names and hashes may still reveal information; public evidence uses project-owned fixtures only.

### Filesystem boundaries and TOCTOU

For supported POSIX hosts, open the selected directory once and perform file opens relative to its directory descriptor, with no-follow behavior for the leaf; then `fstat` the opened FD and require a regular file. Check required runtime capabilities and fail clearly when absent. Opening a user-selected root may resolve ancestors under the trusted local-directory assumption; pinning the root FD afterward prevents later root-path substitution from redirecting leaf operations. This is not a universal sandbox against hostile namespace/mount changes.

Avoid `resolve()` followed by ordinary `open()` as the only protection: checking and opening separate pathnames leaves a race. With a flat-name contract, descriptor-relative opens remove intermediate user-controlled components. For FIFO protection, use nonblocking open where supported before rejecting nonregular FDs; otherwise merely opening a FIFO can hang before type inspection. Do not open devices supplied by an attacker: the reference tool assumes a user-controlled tree and is not a privilege boundary. Reject all symlinks. Recommend rejecting `st_nlink != 1` and duplicate `(device,inode)` identities as conservative hard-link policy, but state that this does not prove no link existed earlier or prevent a same-user writer from modifying a file.

Before and after streaming, compare same-FD identity, size, mtime_ns and ctime_ns and require observed byte count to match expected size. If changed, return `SOURCE_CHANGED` and publish no manifest. These checks detect common edits; unchanged metadata cannot prove immutability or a coherent point-in-time snapshot. An opened file can be renamed and remain readable; document that the manifest describes the opened object and observed bytes. A later verification against the same name detects changed content where it differs. Full hostile-writer snapshot guarantees are excluded.

Output should be bounded JSON to stdout after successful completion, avoiding tool-managed overwrite semantics in the first version. Document that shell redirection can truncate a file before the program runs; examples write outside the input folder. If an explicit output-path feature is added, it needs its own exclusive temporary-file/publication contract and output-vs-input alias tests. Do not sneak it into this scope.

## Android-specific safety decision

Android's `ContentResolver.openFileDescriptor(...,"r",...)` can return pipes/sockets and a null result after provider crash; callers own returned descriptors. Asset descriptors may represent subsections. These are documented behaviors, not hypothetical ordinary-file assumptions [S2]. Proposed first native profile accepts only read-only, regular, seekable whole-file descriptors; reject pipes, sockets, unknown subsections and unsupported sources with a recoverable explanation. No eager copying to bypass restrictions. Supporting arbitrary provider streams is a later product decision requiring blocking-I/O containment and source-consistency semantics.

Open authorized content in the consumer off UI threads, with cancellation support, then submit a descriptor under a reserved broker capacity slot. Capture caller identity at Binder ingress. Broker owns its received descriptor and closes it on every rejection/terminal path; consumer closes its copy after submission completes. Duplication is not a new independent snapshot and may share offset state; use explicit positioned reads or define/test exclusive offset ownership. Never derive a filesystem path from a content URI. Record absolute deadlines using a broker monotonic clock when admitting a relative timeout. Expiry checks between chunks do not promise interruption of a kernel/provider read already blocked.

A blocked read must not cause unbounded replacement threads or descriptors. Logical timeout can complete while a worker remains occupied; report this condition and stop admitting work if capacity cannot be reclaimed. Process restart is a possible last-resort recovery, invalidating all broker jobs; it must be explicitly designed and tested, not advertised as per-job cancellation. The reference Python utility excludes hostile/blocking providers and hard real-time cancellation guarantees.

## Detailed proposed tickets

Common fields: `type=implementation` unless stated; `status=planned`; priorities P0 necessary contract/reference safety, P1 native implementation, P2 extended campaign. The keys below form an acyclic local dependency graph. Bxx is a parent mapping, not an additional dependency unless explicitly listed. Root should reconcile these with sibling agent tickets rather than creating duplicate issues.

### FS01 — Freeze safe file and manifest profile

- **parent:** B01; **type:** design; **phase:** reference; **priority:** P0; **owner_role:** Senior Software Architect; **depends_on:** []; **estimate_hours_range:** [3,5].
- **user_value:** A recipient knows exactly what a successful verification establishes.
- **scope:** Flat-name grammar, schema/version/error rules, caps, source-mutability disclaimer, no authenticity claim, supported platform capability check, stdout output and privacy policy.
- **acceptance:** Golden valid manifest includes empty and binary files; every field has type/range; duplicate JSON keys and logical names reject; unsupported sources/nesting are explicit; outcomes distinguish mismatch, invalid input, I/O and source change; no absolute path/URI is serialized.
- **verification:** Table-driven schema/name cases; manually review success wording against snapshot/authenticity exclusions.
- **evidence:** Versioned schema, contract ADR, valid/invalid fixture inventory.
- **risks:** A narrow filename grammar limits international filenames; document rather than silently normalize.
- **requirement_ids:** [R01,R03,R07,R10,R11,R18].

### FS02 — Descriptor-safe streaming reference create/verify

- **parent:** new FieldPack reference epic; **phase:** reference; **priority:** P0; **owner_role:** Principal Python Backend Engineer; **depends_on:** [FS01]; **estimate_hours_range:** [5,9].
- **user_value:** Generate and verify real authorized attachments with bounded memory.
- **scope:** POSIX capability gating, root-FD-relative flat opens, regular-file/no-symlink/no-hard-link policy, incremental SHA-256, observed-byte caps, before/after FD checks, bounded strict JSON parsing.
- **acceptance:** Different arbitrary fixture directories work; exact cap succeeds and cap+1 rejects; zero-byte/binary/large multi-chunk data hashes match independent known vectors; missing or mismatching file causes nonzero status; no JSON success output on any creation failure; every opened FD closes after success, rejection and I/O exception; verification never mutates input tree.
- **verification:** Python stdlib tests with temporary directories; compare known SHA-256 vectors and independently generated fixture expectations; exception injection after first chunk; repeated FD-accounting smoke on supported host.
- **evidence:** Commands, environment, test report and redacted sample manifest; source diff visibly has bounded reads.
- **risks:** `fstat` comparison is detection, not an immutable snapshot; regular network files can still block.
- **requirement_ids:** [R05,R07,R09,R10,R11,R17].

### FS03 — Adversarial reference-tool regression suite

- **parent:** new FieldPack reference epic; **phase:** reference; **priority:** P0; **owner_role:** Principal Backend QA Lead; **depends_on:** [FS01,FS02]; **estimate_hours_range:** [4,7].
- **user_value:** Unsafe manifests and changing sources fail predictably.
- **scope:** Matrix below, deterministic read/open hooks where needed, real filesystem cases and explicit platform skips.
- **acceptance:** Traversal/absolute/UNC/drive/backslash cases reject before input open; leaf symlink and hard link reject; FIFO test cannot hang suite; same-length overwrite, append, truncation and unlink/replacement checkpoints yield documented results; malformed manifest never opens referenced files; cap breaches leave no partial manifest; source/fixture metadata remains unmodified by verifier.
- **verification:** No race tests based solely on sleeps; use events/checkpoints; separate injected read failures from actual OS cases. Fresh-clone command exits zero for passing test suite and nonzero for deliberately changed sample.
- **evidence:** Case IDs, expected/actual outcome table, capability-based skip reasons and run command.
- **risks:** A deterministic replacement experiment validates selected races, not every scheduler interleaving.
- **requirement_ids:** [R07,R11,R17].

### FS04 — Model owner-scoped job lifecycle and cancellation

- **parent:** B02; **phase:** G1; **priority:** P1; **owner_role:** Principal Kotlin/JVM Engineer; **depends_on:** [FS01]; **estimate_hours_range:** [6,10].
- **user_value:** Clients never receive success after cancelled/lost work or access another owner's result.
- **scope:** Pure Kotlin state machine; enqueue/admit/start/complete/cancel/expire; owner+session+epoch handles; result publication linearization; reservation lifetime.
- **acceptance:** Cancellation before worker start performs zero reads and releases slot/FD; deadline expired before admission rejects without retaining FD; simultaneous completion/cancel has exactly one winning terminal transition; post-completion cancel returns already-terminal, not false cancellation; owner B cannot poll/cancel A even with guessed valid ID; new epoch invalidates all old handles; stale completion cannot publish after owner death/cancel/expiry; no automatic restoration/resubmission.
- **verification:** Virtual clock and enumerated checkpoint interleavings; invariant assertions over seeded event sequences.
- **evidence:** Transition table, failing counterexample format, test report.
- **risks:** Logical terminal state does not imply blocked I/O resource is reclaimed.
- **requirement_ids:** [R05,R07,R08,R10,R17].

### FS05 — Broker descriptor admission and source profile

- **parent:** B03; **phase:** G1; **priority:** P1; **owner_role:** Principal Android/Kotlin Engineer; **depends_on:** [FS01,FS04]; **estimate_hours_range:** [8,14].
- **user_value:** Authorized files cross the real Binder boundary without URI/path leakage or resource leaks.
- **scope:** Permission checks, ingress identity, pre-reservation, received-FD ownership ledger, read-only regular source validation, positioned-read/offset contract, complete-file semantics.
- **acceptance:** Wrong signature rejects; same-signature distinct owner rejected for other's jobs; rejected received FD closes; expired reservation closes resources; pipe/socket/subsection profile rejects clearly; submitting client closes original without corrupting broker's valid work; source mutation/read failure produce non-success; no digest reads occur on Binder or UI thread.
- **verification:** Actual separate APKs and test provider; FD counters before/after each path; real file golden vectors; offset-sharing regression and revoked-permission/open-failure cases.
- **evidence:** Package/PID receipts, provider mode, FD lifecycle trace, executed instrumentation report.
- **risks:** Provider metadata can be missing or misleading; reject unsupported cases instead of inferring stability.
- **requirement_ids:** [R05,R07,R14,R16].

### FS06 — Bound descriptors, retention and unfair callers

- **parent:** B06; **phase:** G2; **priority:** P1; **owner_role:** Principal Backend QA Lead with Kotlin owner; **depends_on:** [FS04,FS05]; **estimate_hours_range:** [6,10].
- **user_value:** A busy client cannot exhaust broker resources indefinitely.
- **scope:** Explicit active/queued/reservation FD caps, per-owner/global count and byte caps, retention TTL, fairness and admission overhead limits.
- **acceptance:** Initial proposal one worker, eight queued jobs total, four outstanding per owner, sixteen total accepted/reserved job FDs; caps reconcile including transient ingress descriptors. Reject at cap+1 and close its FD; no file contents retained; 128 terminal records/256 KiB metadata/60-second TTL ceilings all enforced; owner/session removal releases records; 1,000 rejected submissions and churn produce no growing FD count; quiet owner progresses under noisy owner's offered load; retained expiry yields UNKNOWN/EXPIRED, never rerun.
- **verification:** Boundary tests and 30-minute actual saturation/churn with raw admitted/completed/rejected/timeout counts; process FD sampling distinguishes fixed baseline from owned job descriptors.
- **evidence:** Resource ledger, bound assertions, observed maxima and workload config.
- **risks:** App-owned caps do not control all Binder/kernel transient resource allocation; avoid denial-of-service-proof claims against malicious same-signature apps.
- **requirement_ids:** [R04,R05,R07,R08,R11].

### FS07 — Real death, blocking-read and publication fault campaign

- **parent:** B06; **phase:** G2; **priority:** P1; **owner_role:** Principal Android QA Engineer; **depends_on:** [FS05,FS06]; **estimate_hours_range:** [8,14].
- **user_value:** Inspection workflows recover honestly when file access or the broker fails.
- **scope:** Checkpoint-controlled client/broker death and file-provider behavior; no native-crash claim for a pure Kotlin broker.
- **acceptance:** Kill client before admission, queued and mid-read; remaining client survives and resources converge to declared bounds; kill broker before/after result publication, obtain LOST/UNKNOWN and new epoch; consumer does not restore a job; deliberately blocked source shows logical timeout and bounded occupied worker without replacement-thread growth; forced recovery is documented as broker-wide loss; stale result cannot leak after deadline; 100 repetitions per required deterministic cell records actual pass/failure counts, no fictional target completion.
- **verification:** Actual kill receipts plus checkpoint events; test-only blocking read abstraction for regular-file profile and separately a rejected nonregular provider case; cannot count abstract fake-provider runs as real provider cancellation proof.
- **evidence:** Case matrix, failure logs, process epochs, resource recovery times and unsupported-cell explanations.
- **risks:** OS-level blocked reads may resist timely interruption; provider-open blocking in the client needs separate evidence from broker-read blocking.
- **requirement_ids:** [R07,R11,R14,R16,R17].

### FS08 — Cross-runtime integrity and security conformance

- **parent:** B07/B08 (choose B08 as owning parent); **phase:** G2; **priority:** P1; **owner_role:** Principal Backend QA Lead; **depends_on:** [FS01,FS03,FS05]; **estimate_hours_range:** [5,9].
- **user_value:** A phone-produced manifest can be meaningfully verified on a PC.
- **scope:** Share vectors between reference Python, planned Kotlin core, Android adapters and macOS JVM CLI; old manifest fixtures and explicit major rejection.
- **acceptance:** Identical bytes/names produce identical digest and count semantics; Android safe logical names map explicitly to recipient files; unknown versions reject before reads; macOS case-insensitivity cannot accept two logical aliases; changed/missing/unreadable files fail consistently; artifact states reference-tool tested vs native/desktop planned independently. JVM implementation cannot inherit Python descriptor-safety claims without its own secure-open design/evidence.
- **verification:** Golden corpus and separately compiled old native consumer once it exists; current reference report cannot mark native checks done.
- **evidence:** Per-runtime conformance matrix with actual toolchain/OS and pending cells.
- **risks:** Java path APIs require separate capability assessment; no false automatic security portability.
- **requirement_ids:** [R02,R03,R07,R10,R11,R19].

## Minimum fault matrix to attach to issues

| Category | Cases | Expected assertion |
|---|---|---|
| Names | `../x`, `/x`, `C:x`, UNC, backslash, dot segments, control, case duplicate, duplicate JSON key | Invalid before opening any referenced file. |
| Types | Symlink, hard link, FIFO, socket, directory, device | Unsupported/rejected, bounded termination on supported test host. |
| Read | Empty, partial reads, exception at chunk 2, append/truncate, same-size edit | Correct digest or explicit failure; never partial manifest success. |
| Namespace | Replace leaf before open, rename root after FD pin, unlink after open | Never follow leaf symlink; report documented opened-object semantics. |
| Limits | Exact/max+1 count, size, aggregate, JSON bytes, retained bytes/FDs | Deterministic admission/error and resource closure. |
| Lifecycle | Prestart cancel, read cancel, complete/cancel race, deadline, client/broker death | One terminal transition; no stale publication; new epoch invalidates handles. |
| Isolation | Wrong signature, other owner, guessed ID, stale session | Uniform denial without content/result disclosure. |
| Compatibility | Old schema, future major, algorithm mismatch, unknown fields | Defined rejection; no silent interpretation. |

## Baseline issue amendments and sequencing

B01 must add real-file trust and source profile; B02 adds byte/FD budgets and no-snapshot semantics; B03 adds descriptor ownership; B04 adds real-file and denial fixtures; B05 adds picker cancellation/engine disposal with explicit logical-name mapping; B06 replaces generic malformed payload tests with the matrix; B07 states macOS secure-open limits; B08 includes manifest evolution; B09 separates storage throughput from IPC/core overhead; B10 includes integrity-vs-authenticity wording and safe evidence; B11 records provider/secure-file tradeoffs. B12–B16 remain conditional and must adopt the same semantics if implemented; do not imply their existing synthetic-service scope already handles files.

Remove unnecessary whole-epic serialization when refining dependencies: Flutter adapter can begin against a stable SDK contract while the full B04 campaign continues; macOS design/implementation can begin after stable core/manifest contracts without waiting for Flutter or the Android campaign. Integration gates still require all evidence before closure. FS03 and FS04 can run independently after FS02/FS01 respectively. Reference tickets total 12–21 implementation/QA hours plus 3–5 contract hours; native additions require fresh integrated estimates and are not justified by the old 40-hour gate.

## Traceability and limitations

Direct coverage: R01 architecture decision, R02 multi-consumer conformance, R03 cross-platform naming/semantics, R04 bounded workload measurement, R05 memory/FD bounds, R07 fault behavior, R08 admission/fairness, R09 usable reference workflow, R10 standards, R11 tests, R14 Android boundary, R16 OS ownership/lifecycle, R17 difficult races, R18 clear documentation, R19 macOS/Android exposure. R06 startup must remain measured in B09, not inferred from streaming memory. R12 is supported by the provider/secure-open ADR and later comparison. R13 education, R20 actual ecosystem partnerships, R21 European work and R22 real international collaboration are explicitly not established by these tickets. R09 commercial shipping and R18 actual cross-team work also remain separate evidence.

No privileged operation, network transfer, production workload, durable execution, malware screening, trusted timestamp, immutable snapshot or automatically synchronized attachment bundle. Existing accepted same-publisher trust does not grant authority to read arbitrary paths on behalf of a caller. Platform permission and descriptor authorization remain mandatory.

## Primary sources

- [Python os interfaces](https://docs.python.org/3/library/os.html): descriptor-relative operations and platform-dependent flags; feature-detect against the pinned implementation runtime, not the moving documentation version.
- [Python JSON](https://docs.python.org/3/library/json.html): duplicate-key parsing policy must be implemented explicitly; do not treat default parsing as schema validation.
- [Android ContentResolver](https://developer.android.com/reference/android/content/ContentResolver): read-only descriptor APIs, cancellation, provider failures and nonregular streams.
- [Android ParcelFileDescriptor](https://developer.android.com/reference/android/os/ParcelFileDescriptor): ownership/closing and descriptor operations; reference this when freezing the actual offset/duplication contract.
- [Android Binder/AIDL](https://developer.android.com/develop/background-work/services/aidl): real process/thread boundary; Java/Kotlin service code is not native service/kernel implementation.

Source pages inspected for the key platform claims on 30 October 2026. Proposed safety choices are project policy, not promises made by these APIs.
