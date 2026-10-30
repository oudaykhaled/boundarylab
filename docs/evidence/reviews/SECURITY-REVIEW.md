# BoundaryLab scoped security review

AI-assisted independent source review, 2026-10-30. Reviewer: Principal Security Auditor. Status at source `06b2d4f`: **Scoped source PASS, including authorized narrow amendments. Final broker runtime: 19 passed, 0 failed, 1 skipped; native client 9/9 passed. Hardlink (`nlink=2`) runtime rejection remains unverified on the selected Android target.** Core/manifest and native filesystem slices pass focused tests. This is not whole-product or publication-privacy acceptance; the latest amendment section supersedes earlier pending notes.

## Scope and threat model

Reviewed the approved `FIELDPACK.md`, `BUILD-DECISIONS.md`, guardrail brief, shared core state/resource policy and strict manifest parser, ordinary Android broker/SDK descriptor boundary, and desktop JNI/root adapter and bounded socket framing. Inputs are explicitly authorized stable regular files. Desktop ancestry and same-user process environment are trusted; flat child names are untrusted. No atomic snapshot, hostile same-user isolation, authenticity, persistence, or AOSP file capability is claimed.

Android delegates a read-only opened descriptor across a same-publisher boundary. Both client and broker must establish their peer identity. UID ownership separates sessions; a client string cannot supply authority. Digest success requires exact byte count and final opened-object checks. Same-size concurrent edits can still evade best-effort metadata checks under the documented contract.

## Findings — round 1

### SEC-01: SDK does not authenticate the broker signer

**High; provisional CVSS 3.1 7.1, AV:L/AC:L/PR:N/UI:R/S:U/C:H/I:H/A:N.** Confirmed source defect; installed exploit not executed. Affected initial source: `sdk/kotlin/src/main/kotlin/dev/boundarylab/sdk/BoundaryClient.kt:82` and handshake at line 92.

When the genuine broker is absent, a differently signed application can use the expected application ID and service class, expose compatible AIDL without a signature permission, and return plausible capabilities. The SDK trusts the explicit component name, then submits selected file descriptors. The impostor can read delegated files and forge digest results. Installing the impostor and user file selection are preconditions; an installed genuine package cannot simply be overwritten with a different signer. The genuine broker's caller-signature check does not authenticate a replacement server.

Remediation: resolve the exact component and fail closed unless its signing identity matches the client publisher, before binding/handshake/FD delegation; revalidate the connected component before accepting it. Require regression evidence for wrong-signature rejection and same-signature acceptance. The chief architect agreed this blocks the claimed Android security slice.

Reference: [Android PackageManager signing checks](https://developer.android.com/reference/android/content/pm/PackageManager#checkSignatures(java.lang.String,%20java.lang.String)), [ParcelFileDescriptor delegation](https://developer.android.com/reference/android/os/ParcelFileDescriptor).

### SEC-02: Broker silently rewrites the declared file length

**Low security severity; provisional CVSS 3.1 3.3, AV:L/AC:L/PR:L/UI:N/S:U/C:N/I:L/A:N; blocking contract defect.** Confirmed source defect; no maximum-size bypass claimed. Affected initial source: `android/broker/src/main/kotlin/dev/boundarylab/broker/BoundaryBrokerService.kt:105`, SDK file submission at `BoundaryClient.kt:173`.

The SDK submits zero as `totalBytes`; the broker replaces caller-declared length with `source.size`. Thus a three-byte PFD declared as zero is admitted rather than rejected. It also changes retained request metadata according to a later descriptor instead of preserving the declared request. The 128 MiB actual-size guard still applies, so this is not an unbounded-read issue.

Remediation: SDK captures the known descriptor size and submits that value; broker preserves `totalBytes`, and core/admission rejects a mismatch while closing the received handle. Verify mismatched size, negative/over-limit declared values, duplicate metadata handling and descriptor recovery. The chief architect agreed this blocks the claimed FieldPack contract slice.

Reference: `docs/FIELDPACK.md` descriptor/dedup contract and `docs/decisions/BUILD-DECISIONS.md` ADR-B02.

### SEC-03: SDK shutdown can bypass submitted descriptor cleanup

**Low; provisional CVSS 3.1 2.5, AV:L/AC:H/PR:L/UI:N/S:U/C:N/I:N/A:L.** Confirmed source interleaving; deterministic regression pending. Affected initial source: `BoundaryClient.kt:229-236` and `close()` at lines 281-295.

`request()` checks `disposed` and increments its pending count before calling `worker.execute`. A concurrent `close()` can enqueue and execute shutdown between those operations. The submit enqueue then throws `RejectedExecutionException` outside the task's cleanup block: the SDK has already taken ownership of the PFD, but never closes it or decrements the counter. Repeated lifecycle races can leak the client's descriptors. This requires concurrent callers and precise timing; no broker privilege bypass is claimed.

Remediation: handle executor rejection at the enqueue boundary, close the taken descriptor exactly once, restore the pending count, and deliver a stable failure callback. Make lifecycle enqueue/shutdown semantics explicit for connect/status as well. Require a deterministic rejection test rather than relying on a timing-only stress test.

## Native filesystem slice

No concrete defect found in the approved boundary. `openat` uses read-only/no-follow/nonblocking/close-on-exec; `fstat` checks the opened object's regular type, single link, size and seekability. Native array offset/count validation avoids addition overflow; `pread` uses at most 65,536 stack bytes, does not pin Java arrays across I/O, and preserves the shared cursor. Kotlin owns every successful open; rejects close local handles; close is idempotent at the owner and does not retry POSIX close. Root and alias replacement checks fail success without rebinding reads to a replacement path. Directory snapshots are intentional for the root; content opens remain regular-only.

Independently inspected JUnit XML `desktop/host/build/test-results/test/TEST-dev.boundarylab.desktop.RootedFileProviderTest.xml`: 7 tests, 0 failures/errors/skips, timestamp 2026-10-30T13:13:20.523Z. Desktop owner command: `./gradlew :desktop:host:test --tests dev.boundarylab.desktop.RootedFileProviderTest --console=plain`. Execution environment reported JDK 17.0.14, macOS 26.5.1 arm64; the prerequisite public-JDK capability probe used JDK 21.0.5. Tests cover unsafe types/links, exact cap, mutation/root replacement, bounds, positional reading, output-root rejection and 100 descriptor recovery cycles. Source review and hashes were independently checked; this reviewer did not launch a duplicate global build.

Source SHA-256:

| File | Hash |
|---|---|
| `desktop/host/src/main/c/native_files.c` | `dff7979d32ee3151db416390a11d98b8559e70cad3a6906d6168a5340466f953` |
| `desktop/host/src/main/java/dev/boundarylab/desktop/NativeFiles.java` | `b69bf1707207b19b00e90ecdebb466e13ef25d0897318af35d412886d54170aa` |
| `desktop/host/src/main/kotlin/dev/boundarylab/desktop/RootedFileProvider.kt` | `d937d96368a11570ffc2a46c7276efac1b1b051110107547654bc55e11c59430` |

Initial repository HEAD was `2a49ae3c15dafd0d3070bde74c48d2390f286538`; reviewed new source was still being written, so these hashes, rather than that commit alone, define this native verdict. Any relevant source change requires rereview.

**Follow-up revision accepted:** the desktop owner added a 17-slot semaphore covering transient opens before core admission, with release on failed construction and exactly-once source close. Its source was reread with no new finding. Updated provider SHA-256: `cafa7436b6de35402af501bbe23268a540512322f53d0be3b0a9d6c408f2964d`. Independently inspected updated rooted JUnit XML: **8 tests, 0 failures/errors/skips**, timestamp 2026-10-30T13:18:42.923Z. C and Java remain unchanged. The native filesystem slice passes scoped source and focused-test review at this revision. Broader CLI integration failures are a separate outstanding gate.

## Remaining evidence

Android signer, broker-wire declared-length, and deterministic executor-rejection regression receipts remain outstanding. Core/manifest and native source hashes and passing focused test receipts appear below. Device identity, wrong-owner, broker death, FD transfer and consumer behavior are QA gates and are not inferred from this source review.

## Round 2 source reread

All three reported defects have scoped source corrections: `verifyBrokerIdentity()` checks the resolved service permission/export and UID signer before bind and again before handshake; connected component identity is checked. Broker preserves wire `totalBytes` and SDK obtains `statSize` within its owned request/cleanup scope. Executor rejection now runs cleanup, decrements pending and delivers failure. No new blocking defect found in these corrections. Android now also bounds constructed/admitted input sources with 17 permits; failure and exactly-once close release permits. Binder unmarshalling can create a transient handle before application code runs, so this is not a claim to bound kernel-created ingress handles before dispatch.

The authored `AndroidFileSourceTest.declaredSizeMismatchRejectsAndCloses` exercises the Android adapter plus core directly; it does not by itself regress broker wire metadata rewriting. Requested broker ingress coverage and signer/rejection regression receipts from the middleware/QA owners. Final source hashes and execution receipts will be added after their freeze signal.

Lifecycle review also identified that the follow-up SDK's stale-generation callback suppression conflicted with its exactly-once callback test. The chief architect resolved the contract: admitted per-call callbacks complete once (`CLOSED` for close, `LOST_OR_UNKNOWN` for obsolete generation/loss), independently of potentially blocked IPC cleanup; UI observers suppress stale display updates separately. The round-three source below implements that decision using atomic completion and bounded main-thread timeout delivery; physical cleanup still waits for actual IPC return where necessary.

## Core and manifest scoped result

Final core reread at SHA-256 `2f5c12615fcfd2b99c777a055ff8d4131e07a4a5d8b7aa3797441aa34f133320` found no concrete blocking defect. Positive partial reads now accumulate correctly; failure defaults prevent an unexpected thrown error from committing success. Inspected `CoreEngineTest` JUnit XML: **23 tests, 0 failures/errors/skips**, timestamp 2026-10-30T13:18:31.679Z. Cases exercise ownership, dedup/input closure, queue/session caps, cancellation order, blocked worker retention, deadline, TTL/count/byte eviction and mutation/short-read failure.

Manifest codec SHA-256 `01b3e0c575b04df1f8b89befd3654432ece071460f1e39c1f5f1bbf560d03b90`; inspected `FieldPackTest` XML: **134 tests, 0 failures/errors/skips**, timestamp 2026-10-30T13:18:34.399Z, including the shared corpus. Models SHA-256 `878da64de8807d9d2fbceec6aa77a4167ec6979acddf82b3ff4acffd67ea93d5`; source interface SHA-256 `29b11796e1df2cbac33bb01a62043d6c6e5ae6495d3319015aece83b431d6c8b`. Core and manifest pass scoped source/test review at these revisions. These tests do not establish device transport identity, whole-process RSS, or installed consumer behavior.

Updated core XML at 2026-10-30T13:21:03.320Z contains **25 passing engine tests**, no failures/errors/skips, with the same production engine hash. Together with 134 codec tests this is 159 passing core tests; source acceptance is unchanged.

## Round 3 frozen Android source verdict

Middleware supplied a stable source snapshot and this reviewer independently reread and hashed it. **PASS for scoped source correctness of SEC-01/02/03 fixes; no new source blocker found.** This verdict does not convert unexecuted Android regression cases into passes.

| File | SHA-256 |
|---|---|
| `sdk/kotlin/src/main/kotlin/dev/boundarylab/sdk/BoundaryClient.kt` | `5ca1f618334f4bbcaedc4e05776b4e569c49614e04b2ab4d03cd722a9c4b9e92` |
| `android/broker/src/main/kotlin/dev/boundarylab/broker/BoundaryBrokerService.kt` | `70febf7f639f6e1e780efa554fcb8aba80c12dd81b611f7936b2f766589db44d` |
| `android/broker/src/main/kotlin/dev/boundarylab/broker/AndroidFileSource.kt` | `c148ecbbc8dc83562b6f54ef72761062214cec15a8476bf88f1287dcc35e90b8` |

`SdkOwnershipTest.rejectedExecutorClosesTransferredDescriptorAndSettlesCallback` now deterministically forces executor rejection and asserts descriptor invalidity, one callback and zero pending count. A separate 30-iteration close race is supplementary. `BrokerWireTest` directly submits three actual bytes with declared length two, checks `INVALID` and no admitted request, then checks retained duplicate versus changed-metadata conflict. These are appropriate regression designs but execution was pending at this snapshot. A differently signed caller/cross-owner test does **not** prove SDK rejection of a differently signed broker; that distinct SEC-01 gate must remain explicit until separately exercised.

## Chief-authorized narrow amendment

After the three-round freeze, root and the chief explicitly authorized one targeted amendment review. **Amendment source PASS** at the following independently checked hashes; the earlier frozen verdict is not silently extended to other source changes.

| File | Amended SHA-256 |
|---|---|
| `sdk/kotlin/src/main/kotlin/dev/boundarylab/sdk/BoundaryClient.kt` | `b0e29c363c5677bbb8bf88060ff267b66bec4031576fd8ff61c60ab400ec00b8` |
| `android/broker/src/main/kotlin/dev/boundarylab/broker/BoundaryBrokerService.kt` | `67e6f45995ca93c7019be21297ba3d7e72919771020e4b40b1cce262fca3fec0` |

SDK line 296 now removes a delayed poll's bookkeeping only while its captured generation still matches the current generation. An obsolete completion cannot clear a fresh same-request-ID poll slot, preserving one outstanding poll and spacing policy after reconnect. The request still settles and decrements its pending count through the existing completion path. The targeted lifecycle regression constructs a replacement generation and verifies the occupied fresh slot remains `BUSY` without another transport read.

Broker lines 91, 147 and 157 isolate `unlinkToDeath` failures so an already-dead/unlinked token cannot prevent session or engine cleanup. Session revocation and descriptor cleanup remain under the prior ownership rules; no authorization check or metadata validation was weakened. SEC-01 signer checks, SEC-02 preserved wire length and SEC-03 rejected-enqueue cleanup remain intact on targeted reread. The file-source adapter stays at the previously reviewed hash.

`SdkLifecycleTest.realPackageManagerSignerMismatchPreventsBindingAndDescriptorDelegation` is an appropriate controlled outgoing-guard regression: it first establishes a real installed PackageManager `SIGNATURE_NO_MATCH` result between platform and broker UIDs, uses a ContextWrapper to supply the mismatched client UID, then requires `DENIED`, zero bind attempts and closed submitted PFD. This is controlled client-context evidence, **not installation/execution of a rogue broker APK**. Runtime execution of this amended snapshot and the 16 broker instrumentation cases was pending when this amendment was recorded; earlier test receipts are not evidence for the amended binaries.

## Chief-authorized bounded metadata preflight amendment

**Targeted source PASS** at SDK SHA-256 `56043c3bb681bc37ed036b07c3ee0534e3dbd268bcf830e6cfcdf2a2c6b11c71`; broker remains `67e6f45995ca93c7019be21297ba3d7e72919771020e4b40b1cce262fca3fec0`. This supersedes the earlier SDK hash for current source acceptance only.

The SDK now rejects noncanonical request/handle UUIDs after an exact 36-character bound, bounds aliases before shared `FieldPackCodec.validateAlias`, and checks size/deadline ranges before Binder submission. Request lookup validates its ID before polling bookkeeping. All four handle IDs are checked before status/cancel IPC. File validation stays within the existing request-owned cleanup, so invalid metadata closes its transferred descriptor and releases pending admission. These checks prevent oversized local metadata from becoming a Binder transport exception and unnecessary connection loss. The generation cleanup guard and original three security fixes remain intact in the reviewed methods.

The new controlled-transport regression covers two-million-character IDs/aliases, reserved/traversal/uppercase aliases, bad ranges and each handle ID, and requires `INVALID`, no unintended transport calls, unchanged generation/bind count, closed file descriptors and zero pending count. This is a source-accepted regression design; the current broker suite now has 17 cases and final artifact runtime execution remains pending. No previous runtime pass is attributed to this source hash.

## Final broker runtime evidence received

Independently inspected QA's `docs/evidence/build/android-broker-instrumentation-final.json`: **19 cases, 18 passed, 0 failed, 1 skipped**, completed on physical Samsung SM-A336B / API 36. The test suite grew from 17 after separating unsafe-type fixtures and adding an already-unlinked descriptor case; no additional production change was reported. Runtime receipt SHA-256: `8428e652c9e4555893fcc7eeb9024cd0dd99ffbbb2ba27d8201704763780ae96`.

Passing cases include controlled outgoing signer mismatch (`DENIED`, bind count zero, no FD delegation), deterministic executor rejection and 30 concurrent close/submit cycles, metadata preflight, old-generation polling cleanup, blocked transport admission and spacing, once-only loss/timeout behavior, cross-UID ownership, client token process death, positional shared-cursor preservation, declared-size rejection/closure, duplicate descriptor closure, mutation checks and 100 descriptor-recovery cycles. Thus SEC-01 and SEC-03 regression gates pass in their declared evidence classes; SEC-01 is still not an installed rogue-broker demonstration. SEC-02's adapter/core mismatch check passes here; the separate raw cross-package wire tests passed in `android-native-instrumentation-r2.json` (8/8 overall), with final native rerun pending when this section was recorded.

The one skipped case is `rejectsHardlinkWhenTargetPolicyPermitsCreatingFixture`: creating the `nlink=2` fixture fails with `EACCES` under the target policy. It is **not a pass** and does not demonstrate rejection of a hard-linked Android input. `rejectsAlreadyUnlinkedOpenDescriptor` passes for `nlink=0`; that is useful independent evidence but does not replace the skipped `nlink=2` case. The earlier combined test failure remains in `android-broker-instrumentation-r1.json` (15/16) rather than being erased. Desktop native hardlink rejection has separate passing evidence.

Artifact provenance checked: `android-product-install-final.json` records successful installation of broker APK SHA-256 `53c97d7478aba6fddb84832d81b86e6b43207f6a7685a377e523469cc896d0f9`; `android-broker-test-install-final.json` records broker-test APK `98b1afee824fe349266174536f75f290304e314f89dadd693ed150db1bb555e2`. Build receipts `crossplatform-qa-android-metadata-final.json` and `crossplatform-qa-broker-fixtures-final.json` both exited zero and contain identical before/after production hashes: SDK `56043c3...`, broker `67e6f4...`, file adapter `c148ec...`. These are the amended source hashes reviewed above. This reviewer read existing evidence only and did not operate the device.

## Final authorized old-handle amendment — source 06b2d4f

**Targeted source PASS**, no new boundary or lifetime defect found. Reviewed commit `06b2d4f9d550a7c2b02bcc4d3e8743800ea9eb4c` changes SDK status/cancel to preserve the supplied handle's generation through admission and request execution. Current SDK SHA-256 is `a75d5b0dd9c61c07f4bf4110223883befb8a2d67769fe954676d66bb1a756e51`; broker and Android adapter remain `67e6f4...` and `c148ec...` respectively. Prior SDK hashes are historical review snapshots.

The change checks a lost handle's captured generation before requiring a currently available remote. Status/cancel therefore report `LOST_OR_UNKNOWN` during the post-death/pre-rebind interval instead of incorrectly returning `UNAVAILABLE`. Rejection releases pending admission without issuing IPC; delayed poll cleanup remains generation-bound. Callback completion, closed-client precedence, signer checks, metadata bounds and descriptor ownership remain on their existing paths. Request-ID lookup intentionally defaults to the current generation. The focused regression explicitly constructs the disconnected interval, checks both status and cancel outcomes, and verifies zero pending requests.

Independently inspected final installed receipts:

| Receipt under `docs/evidence/build/` | Result | Receipt SHA-256 |
|---|---|---|
| `android-broker-instrumentation-post-loss-r2.json` | 20 cases: **19 pass, 0 fail, 1 skip** | `29ea87169469c09137f6e87910976bb9d1063d171386a84c645c9cc109da34e8` |
| `android-native-instrumentation-post-loss-r2.json` | **9 pass, 0 fail, 0 skip** | `49591e4e9d127569b25b05a6161dbd817b5ec894f4c028a6935b069bee272779` |

The broker run includes the new old-handle regression and all previously accepted signer, rejection-cleanup and metadata cases. The native run includes raw cross-package declared-length rejection, retained-duplicate/metadata-conflict checks and actual broker death requiring a fresh epoch and explicit new submission. SEC-01/02/03 are corrected and their scoped regression gates now pass; controlled signer evidence still does not claim an installed rogue broker. The same Android `nlink=2` fixture case remains skipped because of `EACCES`, while `nlink=0` rejection passes separately. No complete Android hardlink-rejection claim is made.

`android-install-post-loss-r2.json` (receipt SHA-256 `3ab3b06a9a62529df4196a5fff80caba3618d1f46f685af136fe36b90dae4783`) records successful installs of broker APK `d1fce929990b08880bf27f5cb0d194776c3c6c33a5d4df170535b4f48ad25337`, broker-test APK `21be134a4ab29f183f8165e21022a64c606a64a328317ab84717cb3db48fa14c`, native APK `c81af5811e7a0b2015d3e19c2c3ab5279a7c1493d7e7baf9b8a191db233c0057`, and native-test APK `95472adf7d8000b8200f1cc966a1e968d839600acb325ba938857d02996485c6`.

`crossplatform-qa-post-loss-android-desktop.json` (receipt SHA-256 `c2508e8d37b8fc9c84d1a054c7839d02744a90e0f4cb2208792977ceeb3aec8c`) reports successful compilation/build and 36 passing host tests with identical before/after SDK/broker/adapter source hashes matching this review. Prior failed actual-death receipts remain retained separately; only the passing post-loss receipts above support this current result. Root owns the final publication privacy audit. This security review does **not** attest that every public log/artifact has passed that separate audit, and does not by itself mark full Q03 acceptance complete.

The same commit's native helper delta was inspected: only `_POSIX_C_SOURCE 200809L` was added before headers for portable POSIX declarations; no open/read/validation/close logic changed. Current helper hash `b8136eea7b00feabbfa2d549b1850ca3f7f742969634536b8ba142728cacb64e` appears unchanged before/after in the passing host receipt. The earlier helper hash is historical. Supplemental Flutter receipt `android-flutter-instrumentation-post-loss-r2.json` reports 3/3 pass, no failures/skips (receipt SHA-256 `72f7b25fff66b61d96639168fd76bb88b84618b2aacef0dc3db90af7d26e8b32`); this is QA runtime evidence and not an expansion of this narrow source review to all Flutter changes.
