# Flutter SDK and FieldPack consumer: proposed ticket revision

Status: planning only. No native module, FieldPack UI, device execution or Projects board is claimed. This report owns the Flutter-facing contract and real workflow; root integrates tickets and cross-agent dependencies. Sources reviewed: current README, architecture, backlog, verification/evidence policy, existing issue snapshot and official platform references below.

## Recommendation and scope amendment

Make FieldPack a real **attachment integrity handoff**, with Flutter Android as the second independently installed consumer. An inspector selects authorized local files, reviews their intended manifest names, hashes them through the broker, exports a versioned JSON manifest, and separately supplies attachments to a recipient. A recipient verifies that manifest against selected files on Android or a local directory using the planned macOS JVM CLI. Changed, missing, unreadable and unsupported inputs remain distinguishable from a successful match. Integrity does not imply authenticity, malware detection, source identity or inspection approval.

Keep the deterministic generated-byte operation as the small baseline/conformance fixture. Add FieldPack as a newly estimated file capability, not an unnoticed expansion of B05's old 8–16 hours. B01 must amend the current explicit exclusion of real user files and freeze file limits, ownership and manifest semantics before real-file integration is accepted. B03 needs descriptor transfer and ownership support; B02 needs streaming hashing that accepts bounded reads instead of only a deterministic generator. The macOS first-PC choice stays unchanged; no Flutter desktop or Linux GUI is required.

Recommended first supported input profile: selected local, finite, regular, seekable files, opened read-only. Reject virtual/transcoded/nonseekable/unknown-size sources explicitly in v0.1; do not quietly copy them into memory or fetch them. Android's document picker can expose cloud providers [S1], so requesting local-only selection is a preference, not a guarantee that every provider is truly offline. Acceptance must document tested local providers; the application itself adds no network client or upload. Provider access failures remain visible.

Files remain caller-controlled and may mutate. Record byte count actually read and digest of bytes observed. Size/metadata-before/after checks detect some changes, but are not a snapshot guarantee and cannot detect all same-size mutations. Surface “changed during read” where detectable; never advertise immutable capture. A stronger snapshot/copy profile would need separate disk quotas and tickets. Descriptor counts, bytes and deadlines require admission limits independent of queue length.

## Actual screens and observable states

1. **Home:** Create manifest / Verify manifest. “Files stay in your chosen storage. No upload.” Broker unavailable, incompatible and denied setup states have distinct actions.
2. **Choose attachments:** system picker; removable rows with on-screen display name, size or “unknown”, readiness/error and manifest alias. No broad storage permission prompt. Cancel picker leaves prior selection unchanged. Duplicate selections are identified without claiming two URIs always identify the same file.
3. **Review manifest names:** show exactly what will be exported. Default safe aliases such as `attachment-001.bin` avoid exporting provider URI, absolute path or inspection/customer metadata. User may choose safe names explicitly. Names are single relative leaf names, unique after the contract's portability normalization; no separators, traversal or control characters. Explain that recipient files must use these names or be mapped explicitly. No silent renaming of original attachments.
4. **Create:** queued/running/cancelling/completed/failed/lost states; measured bytes and completed-file count from bounded status polling. Percent only if denominator is known and trustworthy; elapsed UI time is not fake work progress. Cancel remains available until terminal state is committed. Changing selection is disabled while its descriptors belong to an active job.
5. **Review/export:** per-file digest, byte count, manifest alias, warnings and version. Export is enabled only for a complete successful manifest; failures/cancellation never produce a normal completion manifest. Save invokes system create-document UI. “Saved manifest” is distinct from “files supplied”: attachments are not embedded or uploaded. Save failure or cancellation does not discard the still-valid in-memory completed result; result expiry must be handled if not yet copied locally.
6. **Verify:** select a bounded manifest; parse and validate before opening inputs. Map each manifest entry to a user-selected file; display Matched / Changed / Missing / Unreadable / Unsupported / Not checked. Overall “All files match” requires every declared entry to match. Selectively unmatched or cancelled verification cannot display overall success. A valid digest does not authenticate the manifest's author.

UI strings for critical cases: `BUSY`: “No work accepted. Try again when capacity is available.” `DENIED`: “This app is not authorized to use this broker.” `LOST/UNKNOWN`: “The broker stopped. The previous outcome is unknown. Starting again creates new work.” `submit timeout`: “Checking whether the request was accepted”; query same session/request while valid, never auto-resubmit. `cancelling`: “Stopping work”; a stopped spinner is not proof that work stopped. Terminal completion may legitimately beat cancellation.

## Native bridge, lifetime and privacy contract

Use generated Pigeon types [S2] for capabilities, opaque selection IDs, safe UI metadata, job handles, typed status/error and export/verification results. File bytes, Android URI strings, platform file-descriptor integers and unrestricted native exceptions do not cross into Dart. Native selection registry owns URI/grant/descriptor state; Kotlin SDK transfers a duplicated read-only descriptor through Binder under the separately owned transport contract. Binder validates caller/session and owns/cleans its received descriptor. Flutter channels and Binder remain two different boundaries.

One plugin instance per Flutter engine owns its registry, native coroutine scope, polling and connection. No process-global Activity, selection map or callback state. Dispose cancels work best-effort, closes session and local descriptors, stops polling, unregisters handlers, and prevents completion against a dead engine. Broker-side client death/token cleanup handles abrupt process termination. Flutter explicitly documents independent engine lifetimes [S3].

Activity recreation preserves engine-owned work, clears old Activity/binding references and attaches result listeners only to the replacement Activity. Picker/export launches require an attached resumed Activity; otherwise return a typed unavailable state. Engine destruction is not activity recreation. Flutter's ActivityAware API requires clearing detached bindings [S4]. Only one picker/export launch is outstanding per engine; result tokens reject stale results. UI navigation away follows an explicit “leave and cancel” action; opening the system picker is not mistaken for abandoning the job. No perpetual background service or durable resume.

Native I/O and Binder calls run off UI threads. Route channel replies using the framework's documented threading rules. Poll with one in-flight status request and a frozen interval/budget; stop polling when the engine is gone, terminal state is known or foreground demand expires. A Dart timeout only stops waiting [S5]; cancellation must reach native work, whose status is authoritative.

Private names may be displayed to the local user but are never added to logs, analytics, public screenshots or trace labels. Log synthetic operation IDs, counts and typed errors only. No original names in default manifest. Even hashes can identify known content: export is user-directed and the manifest is sensitive metadata, not anonymized evidence. Demonstrations and committed evidence use project-owned fixtures exclusively. Retain no grants across app restarts in this baseline; reacquire selection after process loss rather than imply persistent access.

## Detailed proposed child tickets

Dependency aliases below are **capability contracts for root to map to actual new ticket IDs**, not already-created tickets: `FP-SCHEMA` = versioned manifest + cross-language fixtures; `FP-CORE` = bounded real-file hashing and mutation policy; `FP-ANDROID` = authorized descriptor Binder transport + ownership; `FP-DESKTOP` = macOS host/CLI manifest verification. Do not make an epic depend on its own child. B05 should depend on contract/Kotlin SDK prerequisites, not completion of every native UI B04 task.

### FL01 — Freeze FieldPack Flutter state and typed boundary

- **parent/type/phase/priority:** B05 / design+implementation / real-example foundation / P0.
- **owner_role:** Flutter engineer with Android API reviewer.
- **depends_on:** B01 contract subtask; FP-SCHEMA. **estimate_hours_range:** 4–7.
- **user_value/scope:** Explicit, portable outcomes instead of raw platform errors; Pigeon schema and UI state reducer, capability detection, no file data bridge.
- **acceptance:** Generated Dart/Kotlin types build from pinned tool versions. Define every screen state above and allowed transition. Unknown error code maps to safe unsupported/failure, never success. Unknown enum/version and late old-generation status are tested. No SDK or UI silently retries submit.
- **verification/evidence:** Pure reducer/codec tests, schema generation check, contract table and generated-artifact commit. These tests do not satisfy native IPC evidence.
- **risks:** Generated API typing is not compatibility across arbitrary plugin versions. **requirement_ids:** R02,R10,R11,R15.

### FL02 — Implement bounded native file selection registry

- **parent/type/phase/priority:** B05 / implementation / FieldPack input / P0.
- **owner_role:** Android/Flutter plugin engineer.
- **depends_on:** FL01; B01 input-profile/caps decision. **estimate_hours_range:** 8–12.
- **user_value/scope:** Pick actual authorized attachments without broad filesystem access; review aliases without exposing URI to Dart.
- **acceptance:** Picker cancellation, missing Activity, revoked permission, removed file, duplicate selection, size/count cap and unsupported provider return distinct outcomes. Reject nonregular/nonseekable/unknown-size source under the selected profile before job admission. Opening at submit revalidates selection. Concurrent picker launch returns typed busy. Selection removal closes native resources; stale token/other-engine token cannot open a file. Client does not read entire files into a Dart list.
- **verification/evidence:** Instrumentation with controlled local document provider covering permission/metadata changes and pipe/virtual sources; actual picker run on allocated Android target; descriptor-counter before/after repeated select/remove. Commit only fixture names and sanitized screenshots.
- **risks:** Provider locality/metadata trust; native descriptor cap must include pre-admission selections. **requirement_ids:** R05,R07,R14,R15,R17.

### FL03 — Bind Flutter jobs to real Kotlin/Binder file execution

- **parent/type/phase/priority:** B05 / implementation / FieldPack execution / P0.
- **owner_role:** Flutter plugin engineer.
- **depends_on:** FL01,FL02,FP-CORE,FP-ANDROID. **estimate_hours_range:** 8–14.
- **user_value/scope:** Selected files execute in separate broker, with typed polling and progress.
- **acceptance:** Native/Flutter same fixture yields identical byte counts/digests. Broker/package PID evidence establishes process boundary. Polling has one in-flight request; UI remains usable while a controlled read stalls. Capture ownership before async dispatch in broker dependency. Full queue is BUSY without accepted job/retained descriptor. Wrong-signature app is DENIED before work. Deadline/timeout do not fabricate terminal state. Duplicate submit timeout is resolved by original request identity within valid epoch.
- **verification/evidence:** Actual installed Flutter client, Kotlin SDK and broker run; sanitized process receipt, thread trace, cap/status tests; no fake transport can pass.
- **risks:** Blocking provider reads require tested cancellation/deadline strategy in FP-ANDROID; do not claim cooperative CPU cancellation solves blocking I/O. **requirement_ids:** R02,R04,R05,R07,R08,R14,R15,R16.

### FL04 — Prove engine, Activity and cancellation ownership

- **parent/type/phase/priority:** B06 / integration tests+fixes / FieldPack reliability / P0.
- **owner_role:** Flutter/native QA engineer.
- **depends_on:** FL03. **estimate_hours_range:** 8–14.
- **user_value/scope:** No leaked work or stale UI after rotation, disposal and broker loss.
- **acceptance:** Recreate Activity during picker and active read; old binding is released and result delivered once to correct live owner or explicit cancelled result. Two engines may reuse request ID but cannot access each other's selection/job. Destroy one engine; other completes. Inject cancel before/after result commit; preserve exactly one authoritative terminal state. Broker dies after admission: UI becomes LOST/UNKNOWN; new epoch rejects old handles; explicit new submit uses new job ID. Client death closes broker resources via token cleanup. No endless foregroundless rebind. Record actual repetition counts.
- **verification/evidence:** Device integration run, deterministic fault barriers, memory/descriptor/session counter before/after 100 lifecycle cycles, sanitized loss video and failed cases. Release variant lacks fault controls.
- **risks:** Activity result delivery differs across engine host arrangements; test selected host rather than claim universal embedding support. **requirement_ids:** R05,R07,R11,R14,R15,R17.

### FL05 — Build selection, review and execution screens

- **parent/type/phase/priority:** B05 / implementation / FieldPack UX / P1.
- **owner_role:** Flutter UI engineer.
- **depends_on:** FL01,FL02; final runtime acceptance requires FL03. **estimate_hours_range:** 7–11.
- **user_value/scope:** Inspector can understand selected input, export naming and live operation state.
- **acceptance:** Empty selection disables Create. All rows expose name/size or explicit uncertainty, alias, remove and actionable failure. Invalid/colliding alias blocks submission. Run buttons do not permit duplicate submit. Back navigation warns about active work; explicit leave cancels/releases. Progress is based on observed counters; zero-byte files do not divide by zero. BUSY/DENIED/LOST map to appropriate actions, no generic Retry that automatically replays unknown work. Screen reader labels, 200% text scaling and small-device layout remain operable.
- **verification/evidence:** Reducer/widget tests for combinations plus real selection→broker→result UI run; screenshots only of owned fixtures; semantics-tree audit.
- **risks:** Mock-only UI completion cannot close ticket runtime AC. **requirement_ids:** R02,R09,R11,R15.

### FL06 — Export versioned manifest safely

- **parent/type/phase/priority:** B05 / implementation / FieldPack handoff / P0.
- **owner_role:** Flutter/Android engineer.
- **depends_on:** FL03,FL05,FP-SCHEMA. **estimate_hours_range:** 6–10.
- **user_value/scope:** Inspector saves actual manifest without implying attachments were exported.
- **acceptance:** Schema/digest/byte count match independent fixture oracle. Default manifest excludes URI, full path, original private name and inspection identity. Review shows all exported fields. Save uses system create-document action [S1]; cancel does not say saved. Disk/provider error including short write yields failure and incomplete destination warning; attempt deletion only for newly created app-owned export URI when supported, otherwise identify incomplete file without claiming rollback. No cross-provider atomic-write promise. Broker loss before complete result prevents export; after a complete validated local result, broker loss need not destroy that snapshot. Save screen clearly says only manifest is saved and attachments are supplied separately using reviewed aliases.
- **verification/evidence:** Real exported JSON reopened by independent parser; faulting output provider; export cancel/retry; privacy assertion; schema test.
- **risks:** Document-provider writes can fail after partial bytes; finalized manifest vs partial file must not be ambiguous. **requirement_ids:** R07,R09,R10,R14,R15.

### FL07 — Verify real files and present per-entry outcomes

- **parent/type/phase/priority:** B05 / implementation / FieldPack verification / P1.
- **owner_role:** Flutter engineer with contract reviewer.
- **depends_on:** FL03,FL05,FP-SCHEMA. **estimate_hours_range:** 8–13.
- **user_value/scope:** Recipient checks a bounded manifest against individually authorized local selections; no directory crawling required on Android.
- **acceptance:** Parse size/count/version/algorithm/path limits before opening files. Reject duplicate entry IDs, malformed digest, traversal/absolute names and unsupported schema. Explicit mapping prevents same-name silent overwrite. All-match, one changed byte, missing, empty, unreadable and unsupported source produce separate row states. Cancellation marks remaining entries Not checked; overall success requires every declared entry matched. Never use digest match as authenticity approval. Result remains scoped to bytes observed, not immutable original-file proof.
- **verification/evidence:** Independent manifest fixtures and real read flow; negative corpus, changed-byte test, partially mapped manifest and cancel race; screenshots show final reasons.
- **risks:** Same-size mutation cannot be generally proven absent; UI must preserve scope warning. **requirement_ids:** R03,R07,R09,R10,R15,R17.

### FL08 — Demonstrate reproducible phone-to-recipient handoff

- **parent/type/phase/priority:** B10 / integration+documentation / FieldPack evidence / P1.
- **owner_role:** Mobile QA/documentation engineer.
- **depends_on:** FL04,FL06,FL07,FP-DESKTOP. **estimate_hours_range:** 5–9.
- **user_value/scope:** Fresh adopter can reproduce the actual workflow with safe fixtures and optional own local files.
- **acceptance:** Fresh same-publisher Flutter consumer runs documented setup. Select project-owned attachments on Android; export manifest; manually supply copies to macOS directory with documented alias mapping; real JVM CLI reports match, changed and missing cases. Distinguish Python reference execution from future Kotlin/macOS and Binder integration. Record actual OS/build/toolchain/artifact and command outputs; no passing status before native execution. A device demonstration uses fixture-only media and redacted identifiers. Explain no upload, no automatic sync, no trust signature on manifest.
- **verification/evidence:** Five-minute walkthrough, local fixture hashes, manifest, CLI receipt and per-stage acceptance ledger; author-run only unless another person actually reproduces.
- **risks:** Manual file arrangement is part of baseline, not hidden automation. **requirement_ids:** R02,R03,R09,R11,R18,R19.

## Dependency and estimate review

FL01 unlocks selection/UX; FL03 waits on real transport/core; FL04 can run alongside UI/export work once bridge exists; export and verify are siblings; final handoff joins mobile reliability, export, verification and desktop. Avoid B04→B05 and B05→B07 artificial whole-epic serialization; depend on shared contract/transport capabilities instead. Unit-level UI development may use fixtures, but runtime gates remain blocked until FL03.

Flutter-lens child total is **54–90 focused hours**, including input/export/verify work and lifecycle evidence. This replaces—not adds to—the overlapping B05 portions and parts of B06/B10. Core, Android descriptor transport, schema and desktop work are external estimates and must not be double counted. Original cumulative 40/120/240-hour labels need revised scope decisions. A useful cut is Android verification UI deferred while retaining native export→macOS verify; that saves FL07 8–13 hours but must be stated as a product scope change, not marked complete.

## Coverage and limits

Direct: R02,R03,R05,R07–R11,R14–R19 as mapped. R04/R06 require separate actual timing/memory/startup study, not responsive UI impression. R01/R12 receive supporting contract and framework assessment artifacts only. R13,R20–R22 remain external education/partner/professional-history/international-collaboration evidence; no ticket can manufacture them. Same-publisher examples are not arbitrary third-party onboarding. No Flutter iOS/desktop, React Native, cloud, background transfer, persistent job recovery, manifest signing, archive packaging, malware scanning or kernel implementation is included.

## Primary sources and interpretation

- [S1 Android Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files): user-selected document access, create/open actions and provider behavior. Baseline exclusions and privacy policy are project decisions, not universal SAF guarantees.
- [S2 Flutter platform channels and Pigeon](https://docs.flutter.dev/platform-integration/platform-channels): generated typed platform interfaces and threading guidance; neither implements Binder ownership or cancellation automatically.
- [S3 Flutter plugin lifecycle/multiple engines](https://docs.flutter.dev/packages-and-plugins/developing-packages): engine-specific instance/lifetime guidance.
- [S4 Flutter ActivityAware](https://api.flutter.dev/javadoc/io/flutter/embedding/engine/plugins/activity/ActivityAware.html): binding invalidation and configuration reattachment.
- [S5 Dart Future.timeout](https://api.dart.dev/dart-async/Future/timeout.html): timeout does not cancel source execution.
- [S6 Android ContentResolver](https://developer.android.com/reference/android/content/ContentResolver): descriptor open/cancellation APIs; provider exceptions and supported descriptor shapes require actual spike validation.

Sources verified during planning; exact framework/tool versions remain kickoff pins. No external writes, auth changes, public edits, product implementation or presentation changes were performed for this report.
