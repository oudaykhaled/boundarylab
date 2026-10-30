# Custom Android 16 proof — minimum runtime passed

**Final r6 passed at 2026-10-30 22:39:22 UTC with cleanup verified.** Source **`444ae29a31833616c465e155a3dc1335d061cda6`** completed **34/34 operator checks**, **236/236 harness observations**, and **61 unique native assertions plus the PASS/61 summary**. The 236 observations include repeated identity/case checks; these counts are separate layers, not a sum of unique tests. [Actual results](actual-results.json) provides a concise machine-readable summary; [the original final aggregate](runtime/r6/run-result.json) and [combined harness receipts](runtime/r6/smoke/checks.json) remain authoritative.

All five earlier failed runtime attempts and the first failed native compilation remain unchanged below. This curation covers actual exports **r1–r6**; it does not declare every original P03–P05 acceptance clause complete. [The independent acceptance map](../../../evidence/reviews/AOSP-ACCEPTANCE-MAP.md) assesses that broader scope. No build, device test or cloud action ran during curation.

## Final r6 evidence and identity boundaries

- [Final checkpoint](runtime/r6/source-checkpoint.json), [source/output artifact inventory](source/r6-source-and-artifacts.json), [image preparation inventory](runtime/r6/preparation.json), and [snapshot operator scripts](runtime/r6/operators) link source444, built images/binaries, actual commands and configuration.
- [Original native execution](runtime/r6/smoke/058-command.json) SHA-256 `f24623a09fbb1332b6d6afbc9d7dbb5e06f1a7e0d01be106d5a40beceb052e47`; [extracted stdout JSONL](runtime/r6/native61.jsonl). Curation independently parsed exactly **61 distinct case names, all true, and one `{summary:PASS,checks:61}`**. The reviewed test source hash is `6c5cc1fe0f00342b803d9c01dd80132a0b5e54f4fc1b357927027ff2da7d71b0`.
- [Denied fixture receipt](runtime/r6/smoke/057-command.json) proves actual UID **9999**, domain `boundarylab_denied`, discovery and typed **DENIED**. [The final assertion-only correction](source/444ae29-android-nobody-expectation.diff) changes the expected Android nobody UID; no caller-authorization policy is broadened.
- [Loaded guest policy](runtime/r6/smoke/loaded-policy.bin), [policy analyzer result](runtime/r6/smoke/permissive-domains.json), and [process/domain observation](runtime/r6/smoke/059-command.json) retain the actual policy evidence. Guest global SELinux is Enforcing before and after. `boundarylab`, `boundarylab_probe`, and `boundarylab_denied` are absent from the permissive-domain list; that list contains **`su`**.
- Authorized Binder work uses **UID 2000 / `u:r:boundarylab_probe:s0`**. Root/su is used separately for protected artifact inspection, fixture setup, native unit execution, policy/process observation and init lifecycle controls. **Native61 ran under su, which is permissive; no su-domain-enforcement claim is made.**
- The [harness](runtime/r6/smoke/checks.json) records exact fresh FD/AVC correlation, generated goldens, completed-job session isolation, cancellation/deadline and immutable terminal outcomes, client-death cleanup, resource recovery, three actual init restart start intervals (**33.23, 10.01, 10.00 seconds**), stale-epoch rejection and an explicit fresh successful job. The resource-cycle FD delta is **0** in the same generation; it is a quiescent observation.
- [Cleanup process receipt](runtime/r6/cleanup-processes.json) and original stop/adb-stop command receipts support the final cleanup result. Actual [boot PNG](runtime/r6/custom-boot.png) and [post-smoke PNG](runtime/r6/custom-after-smoke.png) are unchanged and visually show the virtual Android lock screen without personal content. Screenshots do not independently prove service semantics.

## Build and frozen-source linkage

| Actual phase | End UTC | Recorded exit | Evidence |
| --- | --- | --- | --- |
| Native r1 | 21:55:50 | **1** | [Failed result](build/native-r1/build-result.json), [complete raw log](build/native-r1/build.log) |
| Native r2 | 22:00:19 | **0** | [Result](build/native-r2/build-result.json), [complete raw log](build/native-r2/build.log) |
| Image r1 | 22:02:36 | **0** | [Result](build/image-r1/build-result.json), [complete raw log](build/image-r1/build.log) |
| Native r3 | 22:17:09 | **0** | [Result](build/native-r3/build-result.json), [complete raw log](build/native-r3/build.log) |
| Image r2 | 22:19:17 | **0** | [Result](build/image-r2/build-result.json), [complete raw log](build/image-r2/build.log) |
| Native r4 | 22:30:10 | **0** | [Result](build/native-r4/build-result.json), [complete raw log](build/native-r4/build.log) |
| Image r3 | 22:30:31 | **0** | [Result](build/image-r3/build-result.json), [complete raw log](build/image-r3/build.log) |
| Native r5 | 22:37:01 | **0** | [Result](build/native-r5/build-result.json), [complete raw log](build/native-r5/build.log) |
| Image r4 | 22:37:20 | **0** | [Result](build/image-r4/build-result.json), [complete raw log](build/image-r4/build.log) |

All nine supervisor receipts record no stop reason and no surviving build process group. Native r1 failed because the probe called a non-static generated C++ interface-descriptor method without an object. The [recorded probe correction](source/df4f293-probe-descriptor-fix.diff) preceded native r2. Compilation of a test binary is not execution of its assertions.

The [original checkpoint at 90a479d](source/source-checkpoint-90a479d.json) captures 33 overlay-file hashes before those builds. Runtime r1–r3 use source **`df4f293b611ccc60f685e08e9a6a5b873860872e`**, with [r1 checkpoint](runtime/r1/source-checkpoint.json) binding native-r2 and image-r1 receipts. Runtime r4 uses **`0d484a0ec7e3b60ce3394440d436f78108bce379`**, with [r4 checkpoint](runtime/r4/source-checkpoint.json) binding native-r3 and image-r2. The latter revision contains the [probe SELinux-enforcement-read policy correction](source/0d484a0-probe-enforcing-read.diff) after r3's actual read denial.

R5 uses source **`308b30744ad56440a73e268cab5c06b0a482f080`**, with its [checkpoint](runtime/r5/source-checkpoint.json) binding native-r4 and image-r3 and its [exact FD-correlation correction](source/308b307-exact-fd-correlation.diff) preserved separately.

Per-snapshot [r1](source/r1-source-and-artifacts.json), [r2](source/r2-source-and-artifacts.json), [r3](source/r3-source-and-artifacts.json) [r4](source/r4-source-and-artifacts.json) [r5](source/r5-source-and-artifacts.json) and [r6](source/r6-source-and-artifacts.json) metadata retain actual overlay hashes, selected source-output artifact hashes, process checks and source identity. These are explicitly documented field selections from the original export metadata. The actual [API-freeze receipts and Cuttlefish integration diff](../../P03/api-freeze/README.md) remain separate, unchanged evidence. The [unchanged baseline](../../P02/r5-source-baseline/README.md) is likewise separate; earlier build-sandbox limitations still apply.

## Runtime attempts and exact failures

| Attempt | Source | Aggregate and cause | Cleanup |
| --- | --- | --- | --- |
| r1 | df4f293 | [FAILED](runtime/r1/run-result.json): shell could not hash protected `/system_ext/bin/boundarylab_service`; [original command](runtime/r1/proof-command-012-command.json), [stderr](runtime/r1/proof-command-012.stderr.log). Functional harness not reached. | [Verified](runtime/r1/cleanup-processes.json) |
| r2 | df4f293 | [FAILED](runtime/r2/run-result.json): actual native test file matched its source hash but mode was `644:0:0`, failing required executable identity. Functional harness not reached. | [Verified](runtime/r2/cleanup-processes.json) |
| r3 | df4f293 | [FAILED](runtime/r3/run-result.json): probe's `global_enforcing` check failed after authorized-domain entry; actual SELinux denial prevented reading `selinuxfs/enforce`. [Smoke checks](runtime/r3/smoke/checks.json), [raw probe receipt](runtime/r3/smoke/011-command.json). | [Verified](runtime/r3/cleanup-processes.json) |
| r4 | 0d484a0 | [FAILED](runtime/r4/run-result.json): raw-FD classification check failed after a passing subset. [Smoke checks](runtime/r4/smoke/checks.json), [raw smoke command and JSONL output](runtime/r4/smoke/028-command.json). | [Verified](runtime/r4/cleanup-processes.json) |
| r5 | 308b307 | [FAILED](runtime/r5/run-result.json): strict FD correlation and smoke/lifecycle subset passed; denied fixture observed UID 9999 against the old 65534 expectation before calling openSession. [Smoke checks](runtime/r5/smoke/checks.json), [denied fixture raw log receipt](runtime/r5/smoke/057-command.json). | [Verified](runtime/r5/cleanup-processes.json) |
| r6 | 444ae29 | [PASSED](runtime/r6/run-result.json): complete minimum combined proof; 34 operator checks, 236 harness observations, native61 and summary. [All smoke receipts](runtime/r6/smoke/checks.json). | [Verified](runtime/r6/cleanup-processes.json) |

Each runtime directory retains its bound source checkpoint, preparation/window/configuration, original command receipts and stdout/stderr, final aggregate, cleanup evidence, and **the four operator scripts from that exact snapshot**. Successful boot checks record Android 16 / SDK 36, build ID `BP4A.251205.006`, fingerprint matching the built system, profile `generated-v1`, guest Enforcing, runtime sandbox=true, and matching source-built host/product paths. This does not turn a later failed proof into a success.

After r1, the operator moved the already-planned root phase before protected artifact inspection for the subsequent attempt; functional probes still use an explicitly verified shell UID. R2's installed test identity failure remains visible rather than being counted as native test execution. R3's subsequent policy change was source-built into image-r2; r4's probe successfully observed global enforcement. Original command receipts and snapshot scripts establish the precise root/unroot and test-placement operations; these are not interchangeable across attempts.

## R4: passing subset and failed raw-FD classification

The [actual raw smoke receipt](runtime/r4/smoke/028-command.json) contains successful per-case JSON lines for generated golden vectors at **0, 1, 65,535, 65,536, 65,537 and 134,217,728 bytes**, deduplication/conflicts, major-version and size rejection, typed `UNSUPPORTED` file operation, and same-UID session ownership checks. Two sessions had live tokens while a foreign **completed** job was checked: read and cancel returned DENIED with empty fields, and the owner's successful result remained unchanged. This is **not RUNNING foreign-cancel evidence**. Terminal success winning a later cancel is also distinct from cancellation of live work.

The same command exits **1** and its final line is `raw_fd_rejected` with `passed:false`. Its old ternary detail text, `KERNEL_PERMISSION_DENIED`, is insufficient to establish the numeric Binder status: **do not infer numeric PERMISSION_DENIED from that label**. Separately, the [kernel excerpt](runtime/r4/diagnostics/kernel.log-excerpt.log) and [logcat excerpt](runtime/r4/diagnostics/logcat-excerpt.log) correlate **PID 4280 / audit 202** with SELinux denial of FD use and the Binder driver's `BR_FAILED_REPLY { source: EACCES }`. Those observed driver facts explain the need for an exact status-classification correction; they do not retroactively pass the failed assertion.

The [r3 kernel](runtime/r3/diagnostics/kernel.log-excerpt.log) and [r3 logcat](runtime/r3/diagnostics/logcat-excerpt.log) preserve the earlier `selinuxfs/enforce` read denial. Every excerpt consists only of exact original complete lines, concatenated in order. [Provenance](provenance.json) records the original full-log SHA-256/size, one-based line numbers and exact byte ranges. Full kernel/logcat streams are intentionally not published here. Final r6 likewise retains exact focused excerpts and complete harness correlation receipts.

## R5: exact FD correlation and later UID-fixture failure

The [raw smoke command](runtime/r5/smoke/029-command.json) exits 0. Its numeric FD record reports status/parsedStatus **-2147483646**, `replyDecoded:false`, PID **4283** and stdout socket inode **37391**. The [harness checks](runtime/r5/smoke/checks.json) require the fresh observation, identity, time bounds and unique denial audit **198**, then classify `CORRELATED_SELINUX_FD_USE_DENIAL` from matching AVC and Binder-driver EACCES records. This passes the narrow rejection observation; it does not reinterpret r4's old label or claim a decoded service response. [Kernel](runtime/r5/diagnostics/kernel.log-excerpt.log) and [logcat](runtime/r5/diagnostics/logcat-excerpt.log) excerpts retain exact original bytes.

The same run records a CANCELLED terminal outcome remaining immutable, DEADLINE_EXCEEDED, session-close revocation, the 16-session bound and recovery, actual client-process death followed by quiescence and expired-handle observation. The before/after resource cycle keeps service PID **921**, process start ticks **1814**, and the same epoch, returning all listed counters to zero with **7 → 7 FDs (delta 0)**. This is a short quiescent cycle, not peak measurement or soak.

The separately init-started denied fixture enters `u:r:boundarylab_denied:s0`, observes global Enforcing and discovers the service, then fails `denied_real_uid`: its actual Android UID is **9999**, while this revision expected POSIX nobody **65534**. [The original log receipt](runtime/r5/smoke/057-command.json) records that failure. It stops before the service open call, so **typed DENIED remains unrun**. The final complete proof remains false and cleanup verified.

Authorized Binder probes run as **UID 2000** in their recorded domain. Root is used separately for protected artifact/fixture inspection, process/FD observations and init lifecycle controls; those root observations are not evidence of authorization under UID 2000. Native61 has still not executed. Guest global Enforcing and observed checks for the relevant service/probe/denied security domains must not be expanded into a claim that every target or native-unit domain was independently tested.

## Historical unrun boundary at r5

At the end of r5, typed denied-UID response, native61, init restart/new-epoch assertions and complete harness acceptance were **UNRUN**. R6 now supplies those receipts above; the historical r5 failure is unchanged. Compatibility evolution, performance, peak resources, soak and fresh reproduction remain outside this minimum run.

## Historical unrun boundary at r4 (not the final r6 result)

- Actual execution and independent parsing of the **61 native assertions**.
- The different-UID denied probe, init restart intervals/new-epoch checks, and final combined-harness completion.
- Remaining downstream harness lifecycle/cancellation/resource checks. The pre-smoke FD snapshot is quiescent, not peak or soak evidence.
- Old-consumer evolution, incompatible-contract controls, performance, long-duration soak and fresh reproduction.

No P03–P05 completion, blanket security signoff, vendor/VINTF compatibility or final service acceptance follows from this partial runtime subset.

## Integrity, selection and privacy

The six outer archive hashes and **4,312 cumulative-export payload hashes** were verified locally: r1 136 payloads, r2 294, r3 498, r4 746, r5 1,049, r6 1,589. Each archive contains one additional inventory file. [Archives](archives) retains the exact original inventories and local verification receipts. [Provenance](provenance.json) accounts for every payload, every selected public file, each transformation and every omission, including previous runtime trees repeated inside later cumulative exports. It also records full archive hashes and sizes. [SHA256SUMS](SHA256SUMS) covers this directory's files except itself.

Complete native/image raw logs, runtime/command/smoke receipts, and operator scripts are byte-preserved. Exceptions are explicitly identified metadata field selections, exact-line diagnostic excerpts, and scoped source diffs obtained from the named local commits without author metadata. No log text was silently redacted or rewritten. Image/ELF binaries, intermediate r1–r5 boot PNGs, repetitive preparation/check files, whole stopped-runtime logs, and redundant earlier-runtime copies are omitted with reasons. Existing P03 API-freeze evidence is linked instead of duplicated.

Selected text was scanned for private-key material, account email candidates, common credentials/tokens, signed URLs, Mac home roots and known physical-device identifiers. Email-like build-log matches are Android module/APEX/JAR names; all newly appended text was screened and no sensitive matches were found. Virtual CVD identifiers, ephemeral guest PIDs/audit IDs, request/session UUIDs and generic `/srv/aosp` paths are retained for evidence correlation. Pattern screening is not a guarantee against every secret format.

Local hash checks establish consistency of the supplied archive, snapshots and selected public bytes. They are **not independent execution-authenticity verification**, signed attestation or a fresh reproduction. Failure preservation and remaining unexecuted acceptance work remain part of the result. Native61, typed denial and minimum restart/harness completion now have final r6 evidence; earlier unrun descriptions above are explicitly historical.
