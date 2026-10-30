# AOSP source security review

Verdict: **PASS for the bounded integration attempt, source scope only.** No remaining blocking source finding was identified after the focused corrections below. This is not a successful Android build, compiled-policy verdict, custom-image boot, Binder runtime result, or completion of P01–P09. Root retains the US$50 total budget and publication decisions. Review date: 2026-10-30.

The reviewed target is Android `android-16.0.0_r4`, lunch `aosp_cf_x86_64_only_phone-bp4a-userdebug`, under [ADR-B14](../../decisions/BUILD-DECISIONS.md). The optional public build `16644040` is only a host/Cuttlefish preflight and cannot substitute for the pinned source-built baseline or custom image. No reviewer cloud action, device command, paid execution, or test rerun was performed.

## Threat model and scope

Assets are session/job isolation, bounded daemon resources, immutable terminal results, reliable revocation, and trustworthy evidence. The security boundary is real Binder caller identity plus enforcing SELinux policy. The authorized fixture is UID 2000 in `boundarylab_probe`; the negative fixture is UID 65534 in distinct `boundarylab_denied`. Both intentionally receive narrowly scoped discovery/call permission to exercise the daemon UID gate. Its typed denial is **not** evidence of SELinux discovery denial. Root, image administrator, trusted build tools, and hostile mutation of operator-owned output directories are outside this demonstration's assurance boundary.

Spoofing is addressed by kernel-supplied caller UID and session ownership rather than request metadata. Tampering and replay are constrained by canonical IDs, exact request deduplication, current process epoch and immutable terminal states. Resource exhaustion is bounded at Parcel admission and in the engine. Death tokens tie session cleanup to the remote process. The generated-only interface carries no content FD, filename, URI or arbitrary blob. Privilege is confined to a dedicated non-root daemon domain; the facade is a platform-built synchronous native Binder client, not an Android framework service or the full application SDK. Evidence remains tied to the executed platform and source hashes.

## Reviewed boundary behavior

- [Service](../../../aosp/service/main.cpp): lines 39–47 reject one-way calls, unauthorized application transactions, Parcels above 4096 bytes and any attached FD before generated argument decoding. Lines 56–98 validate remote death tokens, serialize lease registration/revocation, close failed registrations, and bind removal to the expected death recipient. All engine methods use the actual calling UID. The service keeps no content descriptors.
- [Engine](../../../aosp/engine/Engine.cpp): lines 68–105 enforce 16 sessions, 16 queued jobs, four outstanding jobs per session, canonical UUIDs, 128 MiB maximum input, and deadlines from 1 to 120000 ms. Lines 164–178 bound retained results to 64 entries, 60 seconds and 1 MiB accounted metadata. One physical worker uses a 65536-byte chunk and checks cancellation/deadline between chunks. Hashing occurs outside the state mutex. Lines 121–155 and 169–208 preserve first terminal state, revoke session jobs, and retain worker ownership until cleanup. Epoch/IDs use secure random generation with no predictable fallback.
- [AIDL and build](../../../aosp/Android.bp): structured C++ AIDL has an explicit first-integration update/freeze workflow. No invented frozen hash or `unstable:true` substitutes for the pinned Soong operation. Generated C++ decoding and platform linking still require the real build.
- [Init](../../../aosp/service/boundarylab.rc) starts the daemon as nobody, with `restart_period 10` and no device-critical reboot behavior. [Policy](../../../aosp/sepolicy/private/boundarylab.te) declares exact executable/service labels, dedicated domains, narrow service discovery/call grants, and no permissive declaration, network capability, or reverse method-call grant. Actual policy expansion/neverallow acceptance remain build gates.
- [Runtime harness](../../../aosp/scripts/device-smoke.py) checks real probe UIDs and contexts, global enforcing, daemon context, and the loaded policy's permissive domains using the pinned `sepolicy-analyze`. It exercises death cleanup, empty resource baselines, stale epoch after restart, raw oversized/FD rejection with the actual rejecting boundary recorded, typed negatives, native deterministic tests, and three observed start-to-start restart intervals. Raw command receipts are retained during execution. The native facade documents its synchronous scope and does not claim the application SDK's callback contract.

## Findings resolved before this verdict

| Finding | Severity / exploitability | Correction inspected |
| --- | --- | --- |
| Original denied probe shared the authorized SELinux domain | Blocking evidence/contract gap; no demonstrated UID authorization bypass; CVSS not applicable | Separate binary/init entry and `boundarylab_denied` domain, actual nobody UID, exact context assertion; clarified ADR-B14 denial meaning. |
| Reverse daemon-to-client Binder call permission had no callback use | Least-privilege hardening; no demonstrated exploit; CVSS not assigned | Removed reverse `binder_call`; death notification uses the kernel obituary mechanism, not a client callback method. |
| Global Enforcing and a later process list did not establish exercised clients' domain enforcement | Blocking evidence gap; CVSS not applicable | Probes capture their own contexts; harness checks loaded policy for all three domains and validates daemon label. |
| Runtime cancellation assertion assumed cancellation always beats success | Test correctness; no vulnerability | Accept actual CANCELLED or prior SUCCEEDED, then assert terminal immutability. Deterministic engine tests retain explicit ordering coverage. |

The final FD probe correction accepts only explicit transport `PERMISSION_DENIED` or decoded daemon service-specific `INVALID`, recording which boundary rejected the attachment. Policy rejection does not prove the daemon FD check executed and does not justify adding reverse FD permissions.

No unresolved exploitable finding was identified within this source slice. This bounded review does not promise absence of defects; any build/runtime failure must remain recorded and be fixed in its owning source.

## Downloader amendment

[Downloader](../../../scripts/aosp-fetch-public.py) was additionally reviewed at root/chief request. It pins two named artifacts and build 16644040, limits viewer metadata to 2 MiB, requires HTTPS and exact viewer/storage hosts including redirect checks, and does not use Google credentials. Downloads require exact known byte size and magic, stream with a 1 MiB buffer, compute SHA-256, and publish a private temporary file by no-replace hardlink. Failed partial files are removed. Existing complete files are checked and rehashed. Exception output reports only the exception type and a fixed message; signed URL/query values are not serialized. The elapsed bound is checked between reads, with a 60-second socket timeout, as documented. SHA-256 records received bytes; it is not an upstream signature or independent source-build proof. No blocking finding in this narrow downloader review. Anonymous 206 probe success was reported by the chief/owner; no matching local probe receipt was present at this review, so it is not counted as independently inspected evidence here.

## Evidence actually inspected and remaining gates

The existing local engine JSONL contains **61 passing assertions**, plus PASS summary. Environment records Darwin arm64, Apple clang 21.0.0 and OpenSSL 4.0.3; the host script enables AddressSanitizer and UndefinedBehaviorSanitizer. Existing build and sanitizer output files are empty, with no diagnostic content. These receipts support the local native-engine run only. `aidl-generation.txt` is empty, but the subsequently supplied `aidl-generation.json` records SDK build-tools 35.0.0 structured C++ code generation, exit 0, timestamps, four input hashes and 16 generated-file hashes. The four inputs match the reviewed source. This supports local SDK35 generation only, not pinned Android16 Soong, API freeze, C++ compilation or linking.

At this review, no Android 16 Soong build, frozen API output, compiled SELinux policy, unchanged source baseline boot, custom image boot, real platform Binder run or paid runtime receipt exists in the reviewed evidence. The minimum attempt must preserve those actual outcomes, exact image/source provenance, UID/domain/enforcement checks, observed denial, death/resource recovery and restart intervals. Do not convert source assertions or host tests into runtime passes. Full evolution, performance, soak and fresh-reproduction gates remain open outside the minimum smoke scope. Root owns final publication privacy inspection; local raw receipts are not declared safe for publication by this review.

## Frozen source inventory

This verdict applies to the inventory below, not future edits. SHA-256 of the newline-delimited inventory itself: `0b511c0536200642d0fc13f4378ccc57bfa61c7accd5d7cbe609cb9c5293e3c9`.

```text
f157d45f5cf9b6d04b64bae75d84b586f9fc156cd6cb9af8de2d94ec45cf8618  aosp/.gitignore
d4245ae6393541b83de5426d27ba983d8a8ea6800761972d9884637ba90cef1a  aosp/Android.bp
378a4bb360762017a0f7d9198374f7a2618631bf2191e655cb630bc1f2166056  aosp/BoardConfigBoundaryLab.mk
7e11b175d4e8c9d7506c815c682ebcf5331af13b834a21a84de328166dab8ffc  aosp/PUBLIC-PREFLIGHT.md
3cb4f0496be72d38c8cc1c1146f874bb77248bf2d5525c03f33e1b4ea071d1d9  aosp/README.md
4fc33d1e706495bae0806e32c78639d1f7ccde13e1799b3d083b5877df3cf867  aosp/aidl/dev/boundarylab/platform/Capabilities.aidl
c2850e424ccc54c724d7139f00bdda373e76a4d766ea714f216378308c2e2c7a  aosp/aidl/dev/boundarylab/platform/IBoundaryLab.aidl
e362a699eaf5a0ebb9dad9811f56d18f9d50f08f18bd1bfabf14c606fabcaf62  aosp/aidl/dev/boundarylab/platform/Request.aidl
e4e14f3131665cb2515233cb558b54847f178b6d2f9e2b80de026ec5642951de  aosp/aidl/dev/boundarylab/platform/Result.aidl
e23d6243c599654e24b55346cd9be870c35a3ac80eb71a9ff5bb745d902d492a  aosp/client/Client.cpp
d00e6c0a6dfaed5512bd176baf61736b072166aea4ed8cad9047867d1bfada15  aosp/client/Client.h
3b87335220b68835438baed142591ec791b32ac79b365702c36aec3d69801b6e  aosp/client/probe.cpp
b05dd4c31b08cffe3a48bed3280eb2365e28166acc7f74c2ce9bb3218cb20cc1  aosp/engine/Engine.cpp
3900dbe8968074de7aac66f2dffa3573dfe061cd54ea331436723333878dd790  aosp/engine/Engine.h
6dc19f988be4709ab44001420b155250afa2cd5c650812eea23c097f810db10c  aosp/product.mk
70c171f60e8673587a1da1b32eeff5b2f62ed9c0d55faa9837a8bc0170f4ab6e  aosp/scripts/device-smoke.py
abcf0aacef96e60c63ada03080566b33fe4c8825e0b389d5854cf28c9cfd970e  aosp/scripts/stage-overlay.py
395adb2ce290cc917889dfb0cde59d1acdc42e956eb48bb7a6f30ce36fcb7f31  aosp/scripts/test-host.sh
910968444721c68967443bfc4901aeab236c1ac16b678a12eb9b896ace90db64  aosp/sepolicy/private/boundarylab.te
5185be83d1c73e981c22b07b87d7b01afbe4fd323d2fa04ccfa3b0ca53efd1e0  aosp/sepolicy/private/file_contexts
c5af65af923153597bccdf76a80db3d78d38eda4a21669629e734b7d66ce1c4d  aosp/sepolicy/private/service_contexts
9c2eb2ad0ecd0b5654ec182d02f0678fa5b741f3d87796d151e43ea82f3cd0cd  aosp/service/boundarylab.rc
e3de5457f72dfbfe0c0ba254117dde373020a8a84ba2ba0955ef3161f508d5d5  aosp/service/main.cpp
3f72bb5701081a01f476de1ed57bb62f5f822d3ff27f37a6fc592eebefcfb016  aosp/tests/Vectors.h
6c5cc1fe0f00342b803d9c01dd80132a0b5e54f4fc1b357927027ff2da7d71b0  aosp/tests/engine_test.cpp
5594c544677d38ab98b22af437074f4e157542ef6904311b3aaa7a0b9f3c8c79  scripts/aosp-fetch-public.py
```

Local evidence SHA-256:

```text
807f273f82895598f4e1faa030c02f6672c309e88266d894c461ada1dfc51536  aosp/evidence/local/README.md
090b0f52872b6e50cfdd9d659eac047019333dfbd34cec6cc6453e3b64015e1c  aosp/evidence/local/aidl-generation.json
e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855  aosp/evidence/local/aidl-generation.txt
e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855  aosp/evidence/local/build.txt
66c45888b1d9313434e34ba887a86a468d4b078cd86ab1a521f6f25f1e63a671  aosp/evidence/local/checks.jsonl
eed74abed1f6ec7d1cb2eac73001457b346195ea25d6571a7b1db4fc70e193f4  aosp/evidence/local/engine_test
12c75c9154fc8c91101e262ec3edf2f4722388e60a9c168c7ed1cdf004dbef4b  aosp/evidence/local/environment.txt
d508d8299e8a80a1bba0f795b5bdcd8da462a947aaae215626f1a23321746259  aosp/evidence/local/results.json
e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855  aosp/evidence/local/sanitizer.txt
```

## Minimum-smoke amendment — focused review against `5663baa`

**Verdict: scoped AI source-review PASS.** No blocking assertion or boundary issue was found in the diff limited to `aosp/client/probe.cpp` and `aosp/scripts/device-smoke.py`. This amendment does not change the original inventory or approve unrelated later edits. The reviewed file hashes are:

```text
20d1d50d34a38ff0fee641d6183aa576f06ff01ddbee6a34423ae1ea4c71b235  aosp/client/probe.cpp
1bd25f98d7d8c5d233cb93305c02af4a3d214f184571c30370ec7f0da061f646  aosp/scripts/device-smoke.py
```

- **Two live sessions:** probe lines 35–59 open a second real `Client`, with its own Binder death token, while the owner remains open. The second session supplies its own session/epoch to status and cancel against the owner's known job. Both results must be `DENIED` with all eight remaining fields in the frozen `Result` shape empty/zero/false. The owner result is then compared across all nine fields. Using a completed golden job removes ordinary worker progress as an explanation for differences. This proves the intended same-UID, different-session case if executed successfully; it is not a second cross-UID fixture or proof of foreign cancellation against an actively running job. The existing distinct-UID denial fixture remains separate.
- **Actual identity:** both clients execute inside the probe's checked UID 2000 and exact `boundarylab_probe` context. `resources` and `known` modes pass through the same identity/enforcement and capability checks. Metrics are collected after unrooting; root is used only for process/FD observations. No supplied UID or local engine call substitutes for the Binder request.
- **Recovery after the final restart:** probe lines 99–108 submit one byte with the frozen request's default seed zero, require successful terminal state, exactly one processed byte, no cancellation, and literal SHA-256 `6e340b9cffb37a989ca544e6bb780a2c78901d3fb33738768511a30617afa01d`. The expected digest is independent of the service's hashing implementation. Harness lines 201–206 first require old handles to return `STALE_EPOCH`, then require this new real job to finish, require a changed epoch, and match that epoch to the final quiescent snapshot. Emitting a handle before completion cannot itself pass the check: the probe must subsequently exit successfully after the terminal assertion and explicit close.
- **Resource recovery without a growth allowance:** harness lines 92–156 warm session/worker/crypto paths with a completed known job, require all five core counts to become zero within a five-second observation window, and record daemon FD counts/listings. PID, kernel process start tick and epoch must match before and after the smoke/death cycle; a daemon restart cannot reset counters and silently pass the comparison. FD counts must be exactly equal, and all core counts zero. Timeout, malformed/missing resource records, failed process reads, unexpected identity, or unequal counts fail the run. The final restart gets a fresh independent quiescent snapshot.

These are point-in-time recovery assertions for one minimum target run. Equal FD counts are not continuous FD high-water measurement or proof against every possible balanced descriptor replacement; the recorded listings support interpretation of the actual run. The isolation result checks the completed golden job and makes no claim to exhaust all cancellation races. These limits do not block the specified minimum-smoke scope.

This review read the two-file diff and the existing frozen `Result`/`Request` shapes and native facade only for assertion interpretation. No tests, builds, device commands or cloud operations were performed, and no product source was edited. Prior 61 host assertions do not validate these new device-only checks. At dispatch the unchanged AOSP baseline was reported compiling; no new baseline boot, overlay build, or custom runtime result is inferred here. Overlay remains gated on the root-owned P02 source build and Enforcing baseline boot evidence. Only a real target run at these hashes can turn the new assertions into runtime evidence.
