# FieldPack product workflow and ticket recommendations

Status: proposed plan revision. Only the small reference utility may be implemented in the currently authorized task; Android, Flutter, JVM broker and platform work remain future implementation. Personas below are hypotheses, not customers or validated demand.

## Recommendation and business rationale

FieldPack is an **inspection attachment integrity check**: create a manifest for selected local files; separately hand the files and manifest to another person; verify the received files against the manifest. It answers “Do these bytes match the supplied inventory?” It does not answer who created the inventory, whether the inspection happened, whether the content is accurate, or whether a file is malicious. An attacker replacing both files and manifest defeats this unsigned integrity check.

Hypothetical personas:

- **Field inspector:** selects authorized inspection notes and photos and wants a clear attachment inventory before handoff. No capture app, editing workflow, customer database or network connection is required.
- **Office reviewer:** already has a separately supplied directory and wants changed/missing attachments identified before manually reviewing content. The initial PC experience is a macOS CLI, not a GUI.
- **Same-publisher application maintainer:** supports a native Android inspection client and a Flutter companion, and wants one ownership, cancellation, bounded-execution and process-loss contract.

The broker is useful for the third persona: it centralizes quotas, fair admission, lifecycle and diagnostic semantics across independently running same-publisher applications. It does not inherently make SHA-256 faster, provide durable execution or turn one user's selected URI into universal authorization. For a single CLI or app, an in-process library is often preferable. Keep the Python reference intentionally in-process: it proves the manifest/file journey, not a need for IPC. Later compare the integration burden and isolation cost with an equivalent in-process baseline before claiming utility.

This changes the real product surface. Generated-byte hashing remains a deterministic control fixture, while actual streams add descriptor ownership, permission loss, source mutability, unknown lengths, blocked reads, privacy, and export errors. Preserve old effort gates as historical planning estimates and re-estimate the changed scope.

## Exact creation, export and verification journey

1. **Prepare/select:** inspector chooses one or more authorized local files. Native Android and Flutter eventually use the system document picker; a selected document is not necessarily local or immutable. The first supported Android profile should require a readable, seekable regular-file descriptor and reject unsupported provider-backed streams with an actionable message. Do not promise reliable local-provider classification merely from a URI. Python accepts explicitly supplied regular files in a declared input directory; no broad home-directory discovery.
2. **Review names:** assign portable, relative attachment names. MVP uses a flat namespace, no directory hierarchy; reject absolute paths, `/`, `\\`, `.`/`..`, control characters, platform-reserved names, trailing dots/spaces and collisions under the declared case-folding rule. Prefer user-approved neutral names such as `attachment-001.txt`. Names may themselves reveal information, so preview the exact exported manifest. Aliases require the separately transferred files to use matching names; do not secretly rename source files.
3. **Create:** request bounded streaming SHA-256, recording bytes actually read. Proposed manifest top-level fields: `schema_version: 1`, `algorithm: "sha256"`, `entries`. Each entry contains `name`, `size_bytes`, `sha256` (64 lowercase hex digits). Entries sorted by name for predictable output. Omit device identifiers, absolute paths, provider URIs, contacts, timestamps and inspection metadata by default. Reject empty or duplicate-name manifests rather than showing a misleading all-clear.
4. **Success boundary:** show a complete manifest only after every requested file succeeds and all limits pass. On cancellation, denied access, read error, detected mutation or broker loss, produce no successful manifest. Preserve any pre-existing output. A digest reflects bytes observed, not an atomic point-in-time snapshot; compare before/after metadata as a best-effort mutation signal, explicitly disclosing that unchanged metadata cannot prove immutability. Do not claim all concurrent edits are detectable.
5. **Export:** user explicitly saves manifest JSON to a chosen destination. The reference writes a temporary file in the destination directory and publishes only after completion, with no overwrite by default. Android document-provider output can fail partially and has no blanket atomicity guarantee; label failed exports and offer explicit retry, never a success toast. Copying/exporting attachments is outside MVP; explain that files must be transferred separately with their manifest names.
6. **Verify:** reviewer explicitly supplies the manifest and a separate local directory. Validate schema, byte limits, count limits, digest syntax, names and duplicates before opening attachments. Read only named files under that root. Return per-entry `MATCH`, `CHANGED`, `MISSING` or `UNREADABLE`; malformed/unsupported manifest is a command-level error. Extra files are unlisted, not verified; either report a bounded count or state this limitation clearly. No match badge for the whole directory merely because named entries match.
7. **Respond:** reviewer examines changed/missing files and asks the sender for a fresh handoff using their existing workflow. No email integration or notification is included. Verification never modifies source files. Invalid paths, symlinks and path-replacement attempts must not escape the chosen root. A same-user adversary and directory race model require explicit engineering review; string-prefix validation alone is insufficient.

Suggested reference command shapes (proposed until implemented): `fieldpack create --root fixtures --file attachment-001.txt --file attachment-002.txt --output manifest.json`; `fieldpack verify --manifest manifest.json --root received`. Exit contract: 0 = every listed entry matches; 1 = verification mismatch/missing/unreadable; 2 = invalid command, unsupported manifest or creation failure. Cancellation should use a documented nonzero result and never leave a success artifact.

## Five-minute real example demo

This demo can run using only project-owned safe files and the authorized Python reference. Label it **reference file/manifest behavior; no Binder or Flutter implementation**.

- **0:00–0:40:** show two tiny fixtures as actual readable files, the user problem and manifest fields; explain integrity versus authenticity.
- **0:40–1:30:** create a manifest from those actual file paths; inspect that only relative names, lengths and hashes appear. Check that inputs are unchanged.
- **1:30–2:10:** manually copy the fixture files to a separate recipient directory, then verify all listed entries match. Explain this manual handoff is not phone–PC synchronization.
- **2:10–3:00:** edit one recipient file and remove another; rerun and show changed/missing outcomes and nonzero exit code.
- **3:00–3:40:** supply a traversal-bearing malformed manifest and show rejection before any out-of-root file read. Use an isolated test directory, not a real sensitive path.
- **3:40–4:20:** show an executed cap/cancellation test and preservation of an existing output. If interactive cancellation is not implemented, explicitly show the real test instead of acting it out.
- **4:20–5:00:** show planned native + Flutter → Binder and macOS JVM host integration tickets, their open status, and the limitations. Only actual recorded test evidence may be labelled passed.

## Proposed implementation-sized tickets

These are product-facing slices to integrate with specialist tickets, not duplicate work packages. B01–B16 are umbrella estimates; do not sum parent and child estimates. All new tickets start `planned`. FP keys are proposed stable work keys, not existing GitHub numbers. Dependencies below are acyclic; platform implementations depend on contract decisions, not on unrelated Flutter delivery.

### FP01

- **key:** FP01
- **title:** Freeze FieldPack manifest, names, outcomes and privacy contract
- **parent:** B01
- **type:** specification
- **phase:** reference/G1 prerequisite
- **priority:** P0
- **owner_role:** product owner + API architect (one accountable owner)
- **depends_on:** []
- **estimate_hours_range:** [3, 5]
- **user_value:** Sender and recipient share an unambiguous inventory and failure vocabulary.
- **scope:** JSON v1, flat portable names, finite count/byte caps, support profile, no-overwrite export, verification exit/status model and integrity wording. Proposed initial reference caps: 32 files, 128 MiB total bytes read, 1 MiB manifest, 64 KiB read buffer; freeze exact behavior for each boundary.
- **acceptance:** [Document valid example and empty/duplicate/absolute/traversal/reserved-name examples; reject unknown schema versions and duplicate JSON keys; specify integer/length/digest validation; count actual bytes against caps even when reported length is absent or wrong; define extra-file limitation; document source mutability and export limitations; distinguish broker-loss UNKNOWN from a successful manifest.]
- **verification:** [Review against valid and negative fixture table; trace each UI/CLI state to one contract outcome.]
- **evidence:** [Versioned schema/contract, fixture table, decision log.]
- **risks:** [Cross-platform name collisions; spec implying authentication; inconsistent cap units.]
- **requirement_ids:** [R01, R03, R07, R10, R11, R18]

### FP02

- **key:** FP02
- **title:** Create manifests from real files with a bounded Python reference
- **parent:** new epic FP — FieldPack reference workflow
- **type:** implementation
- **phase:** authorized reference
- **priority:** P0
- **owner_role:** Python engineer
- **depends_on:** [FP01]
- **estimate_hours_range:** [5, 9]
- **user_value:** Users can create a real inventory without waiting for native middleware.
- **scope:** Stdlib CLI, selected regular files, finite buffer, SHA-256, actual byte counters, safe output, project-owned fixtures. No IPC or UI.
- **acceptance:** [Known independent digest and size match for empty/small/multichunk inputs; user-supplied paths work beyond bundled fixtures; reject unsupported files/symlinks and unsafe names; detect tested before/after mutation signals without claiming full detection; file/count/aggregate cap violations fail without a manifest; interrupt/read failure leaves prior destination unchanged and removes staging artifacts; destination collision requires explicit action.]
- **verification:** [Temporary-directory unit/integration tests; injected read failure and mutation; cap edges; real CLI round trip; input-content comparison before/after.]
- **evidence:** [Commands, exit codes, test output and fixture hashes; no private filenames or bytes.]
- **risks:** [Race-safe open semantics vary; metadata checks are not snapshots; accidental output overwrite.]
- **requirement_ids:** [R05, R07, R09, R11, R17]

### FP03

- **key:** FP03
- **title:** Verify a manifest against a separately supplied local directory
- **parent:** new epic FP — FieldPack reference workflow
- **type:** implementation
- **phase:** authorized reference
- **priority:** P0
- **owner_role:** Python engineer
- **depends_on:** [FP01]
- **estimate_hours_range:** [4, 8]
- **user_value:** Reviewer can distinguish matching, changed, missing and unreadable attachments.
- **scope:** Strict parser, safe root-relative lookup, bounded streaming verification, structured/human outcomes and exit codes. Can develop independently of FP02 using hand-authored golden manifests.
- **acceptance:** [Valid fixture verifies; changed bytes and same-size changes produce CHANGED; absent file produces MISSING; read denial produces UNREADABLE; malformed manifest exits before file reads; traversal/absolute/duplicate/case-colliding names and symlinks are rejected; unlisted files are never claimed verified; verifier changes no input; oversized manifest and attachment reads terminate at caps.]
- **verification:** [Golden manifests independent from creator; directory-escape and symlink negative tests; injected permission/read failure for portable tests; CLI exit assertions.]
- **evidence:** [Per-entry real output, regression results, safe-path threat-model note.]
- **risks:** [Both creator/verifier sharing a bug; platform differences; misleading overall “all files valid” language.]
- **requirement_ids:** [R03, R07, R09, R10, R11, R17]

### FP04

- **key:** FP04
- **title:** Specify native and Flutter FieldPack selection-to-export acceptance journeys
- **parent:** B04, with linked B05 consumer implementation
- **type:** product specification / acceptance fixtures
- **phase:** G1 real-files revision
- **priority:** P0
- **owner_role:** UI/UX engineer + Android engineer (one accountable owner)
- **depends_on:** [FP01]
- **estimate_hours_range:** [3, 6]
- **user_value:** Both application ecosystems expose clear consent, progress, failure and export behavior.
- **scope:** Screen/state map and executable scenario definitions for future Android descriptor and Flutter integration tickets; no app implementation in this ticket.
- **acceptance:** [Cover picker dismissal, permission loss, unsupported/nonseekable source, duplicate aliases, source mutation, cancel during queued/running work, export failure and retry; broker death shows LOST/UNKNOWN and blocks export; new epoch offers explicit new submission, with permissions reopened/revalidated; completion/cancel race displays the actual committed state; visible export preview contains no source URI or absolute path.]
- **verification:** [Walk each scenario against core lifecycle contract; identify descriptor owner and close point in each transition; review wording for no recovery/authenticity claims.]
- **evidence:** [State map, acceptance scenario files, copy deck and linked engineering tickets.]
- **risks:** [Product flow masking platform limitations; Flutter engine teardown versus screen recreation; partial destination writes.]
- **requirement_ids:** [R02, R07, R09, R14, R15, R18]

### FP05

- **key:** FP05
- **title:** Publish an executed five-minute FieldPack reference demo and scope ledger
- **parent:** B10
- **type:** verification/documentation
- **phase:** authorized reference, updated again at G2
- **priority:** P1
- **owner_role:** QA engineer / documentation owner
- **depends_on:** [FP02, FP03]
- **estimate_hours_range:** [2, 4]
- **user_value:** A reviewer can reproduce the actual workflow and distinguish shipped reference behavior from planned middleware.
- **scope:** Safe fixture demo, README commands verified against implementation, test receipts, supported/unsupported matrix and evidence status.
- **acceptance:** [Fresh temporary directory reproduces create/match/change/missing/invalid-manifest sequence; documented commands and exit codes agree; cleanup touches only generated demo directory; evidence names commit/toolchain and excludes user data; clearly states Python reference is not macOS JVM host, Android Binder or Flutter; no invented performance/adoption claims.]
- **verification:** [Run documented sequence exactly; inspect manifest/output for sensitive paths; check links and status ledger.]
- **evidence:** [Reproduction transcript, test report, current scope ledger; optional real recording labelled by commit/date.]
- **risks:** [Demo accidentally used to mark native umbrellas complete; stale command examples.]
- **requirement_ids:** [R09, R11, R18]

## Required umbrella amendments and integration notes

- **B01:** add FP01 and platform file-support policy. New actual-file descriptor caps coexist with queue/session caps; a queued request can hold OS handles even before worker admission.
- **B02:** add bounded stream operation while retaining generated vectors for controlled comparisons. Must cap live descriptors, bytes read and retained manifest/result bytes, not just computation size.
- **B03:** add read-only descriptor transfer and ownership/close/error contract; captured caller identity remains mandatory. No URI-string trust shortcut. Scoped grants are not broader permissions.
- **B04/B05:** implement FP04 journeys using actual files, not just generated seeds. Neither should depend on the other's UI completion once the common SDK contract is stable.
- **B06:** test provider/source mutation, close/cancel races, queued descriptor pressure, export interruption and permission loss in addition to existing death/overload cases.
- **B07:** actual macOS JVM host/CLI should verify the FieldPack schema with independent conformance fixtures; Python is an oracle/reference, not a substitute for two JVM processes.
- **B08:** distinguish manifest schema compatibility from AIDL binary compatibility; retain old manifest fixtures and old compiled consumers separately.
- **B09:** separate cached/local-file I/O, digest compute and broker overhead. Never claim speed improvement by comparing different byte sources or warmed versus cold files.
- **B10/B11:** retain real workflow evidence and investigate whether broker complexity is justified; inconclusive business value is a legitimate finding.
- **B12–B16:** do not automatically broaden platform file access. Select a sandboxed fixture/descriptor profile and demonstrate its security boundaries before claiming FieldPack parity. AOSP remains conditional and needs Linux.

The five product/reference tickets total **17–32 hours** before future native integration. This replaces overlapping product/docs work within affected umbrellas; it is not automatically additive to every previous estimate. Native descriptor engineering and provider fault tests require separately estimated specialist tickets. The former 40-hour generated-hash gate cannot honestly promise all the new functionality without a revised estimate.

## Requirement coverage and honest limits

Direct product/reference contributions: R01 architecture/roadmap, R02 ecosystem acceptance design, R03 portable manifest semantics, R05 bounded reads, R07 clear failures, R09 concrete product journey, R10 contracts, R11 tests, R17 actual fault investigation, R18 clear handoff documentation. R04/R06/R08 need future controlled performance/startup/concurrency evidence; the Python example alone proves none. R12 benefits from a dated assessment but needs an explicit technology decision. R14/R15/R16/R19 remain dependent on real Android/Flutter/PC/AOSP implementation, not a storyboard or reference CLI. R13/R20/R21/R22 remain external qualification, real partnership, tenure and genuine collaboration facts; the example cannot manufacture them.

## Primary sources and limits

- [Android Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files): user selection and document access, including providers that may represent cloud storage. Selection alone does not prove locally resident or immutable bytes.
- [ContentResolver API](https://developer.android.com/reference/android/content/ContentResolver): opening descriptors and cancellation/provider behaviors; use the tested provider profile rather than promising every document works.
- [Python hashlib](https://docs.python.org/3/library/hashlib.html): incremental digest updates and SHA-256 support; streaming is an implementation mechanism, not proof of authenticity.

Sources checked 30 October 2026. Exact platform versions and capabilities require implementation-time verification. No public edits, GitHub board mutations, credential inspection or native implementation were performed for this report.
