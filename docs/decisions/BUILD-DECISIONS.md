# BoundaryLab build decisions

Date: 2026-10-30. Owner: Astra chief architect. Status: binding implementation guidance; execution remains independently evidenced. Root alone changes the board and git state. This document supplements, rather than replaces, `FIELDPACK.md`, `ARCHITECTURE.md`, `PLAN-V2.md` and `planning/tickets-v2.json`.

## ADR-B01 — module and dependency boundaries

**Context.** Android native, Flutter and macOS must use one policy implementation while their transports and file authority differ. Parallel implementation needs stable module names.

**Decision.** Retain these Gradle projects:

| Project | Responsibility | Allowed dependencies |
|---|---|---|
| `:core` | JVM protocol models, strict manifest codec, state/admission/dedup/retention, bounded digest worker | JVM standard library and explicitly pinned codec/test dependencies; no Android |
| `:sdk:kotlin` | Android library containing shared AIDL and typed client SDK | `:core`; Android APIs |
| `:android:broker` | Separate application; service, caller capture, permission, received-file adapter | `:sdk:kotlin`, `:core` |
| `:examples:android-native` | Native FieldPack selection, assembly, export UI | `:sdk:kotlin` |
| `:android:denied-client` | Test-only independently signed denial consumer; quality/build ownership | Shared protocol as needed; never shipped as a production client |
| `:desktop:host` | Separate JVM host, bounded Unix socket frames, rooted file adapter | `:core` |
| `:desktop:cli` | Recipient CLI; only socket calls for execution | `:core` models/codec; no in-process engine execution |

Flutter plugin lives at `sdk/flutter`; consumer lives at `examples/flutter`. Its Android Gradle build includes the shared Kotlin SDK/core projects by explicit project paths. It must not duplicate AIDL, policy, hashing or manifests in Dart. AIDL declarations have one owner in the SDK; the broker consumes that library. Additional test-only modules require a named owner and root build integration, not duplicated production logic.

Use package root `dev.boundarylab`; core uses `dev.boundarylab.core`. Application identifiers should be `dev.boundarylab.broker`, `dev.boundarylab.nativeclient`, `dev.boundarylab.flutterclient`, with any denial fixture independently identified and signed. The middleware head owns final source names and broadcasts unavoidable API amendments before consumers diverge.

**Consequences.** Native and Flutter share Android behavior while the macOS process boundary stays distinct. Shared core code does not itself establish cross-platform execution evidence.

**Alternatives considered.** Duplicate Android/desktop engines make initial scaffolding easier but duplicate policy and drift. A Kotlin Multiplatform rewrite creates extra tooling without a current non-JVM engine requirement. Neither is selected.

## ADR-B02 — core API and ownership freeze

**Context.** Platform adapters need concrete signatures before the core implementation is complete. Use synchronous bounded policy operations; only the owned worker performs hashing. SDKs put synchronous Binder work off the main thread.

**Decision.** The following Kotlin excerpt reflects the implemented integration contract. Authoritative declarations are [Models.kt](../../core/src/main/kotlin/dev/boundarylab/core/Models.kt), [PositionalSource.kt](../../core/src/main/kotlin/dev/boundarylab/core/PositionalSource.kt) and [CoreEngine.kt](../../core/src/main/kotlin/dev/boundarylab/core/CoreEngine.kt). The middleware head may add constructors/testing injection and models without changing these meanings. Numeric types and field names must be communicated to consumers before edits.

```kotlin
package dev.boundarylab.core

data class SessionHandle(val epoch: String, val sessionId: String)

data class JobRequest(
    val requestId: String,
    val operation: String,
    val totalBytes: Long,
    val seed: Long = 0,
    val deadlineMillis: Long = 30_000,
    val alias: String? = null,
    val protocolMajor: Int = 1,
)

enum class JobState { ACCEPTED, RUNNING, SUCCEEDED, FAILED, CANCELLED }

data class JobSnapshot(
    val epoch: String,
    val sessionId: String,
    val jobId: String,
    val requestId: String,
    val operation: String,
    val state: JobState,
    val totalBytes: Long,
    val bytesProcessed: Long,
    val digest: String? = null,
    val errorCode: String? = null,
    val cancelRequested: Boolean = false,
)

interface PositionalSource : AutoCloseable {
    val size: Long
    fun readAt(offset: Long, buffer: ByteArray, bufferOffset: Int, length: Int): Int
    fun verifyUnchanged(): Boolean
    override fun close()
}

// CoreEngine : AutoCloseable implements the following conceptual facade:
// capabilities(): Capabilities
// openSession(owner: String): SessionHandle
// submit(owner: String, sessionId: String, epoch: String,
//        request: JobRequest, source: PositionalSource? = null): JobSnapshot
// status(owner: String, sessionId: String, epoch: String, jobId: String): JobSnapshot
// statusByRequest(owner: String, sessionId: String, epoch: String,
//                 requestId: String): JobSnapshot
// cancel(owner: String, sessionId: String, epoch: String, jobId: String): JobSnapshot
// closeSession(owner: String, sessionId: String, epoch: String)
// close()
```

Policy rejection uses the implemented `BoundaryException(code: ErrorCode)`. Adapters encode stable errors; do not expose exception messages or private paths. Baseline error vocabulary is `INVALID`, `BUSY`, `DENIED`, `INCOMPATIBLE`, `UNSUPPORTED`, `CONFLICT`, `STALE_EPOCH`, `EXPIRED_OR_UNKNOWN`, `DEADLINE_EXCEEDED`, `IO_ERROR`, and `SOURCE_CHANGED`. `LOST_OR_UNKNOWN` is an unresolved client observation after transport loss, not a stored server terminal state. A timeout does not create success or automatic resubmission.

`owner` is an adapter-derived opaque identity. Android captures UID/user on the Binder ingress thread; desktop ties ownership/session to connection lifetime. No wire-supplied owner string grants authority. All UUIDs are canonical lowercase hyphenated strings.

Calling `submit` transfers ownership of any supplied source to the core even if submission rejects or returns a retained duplicate. The core closes it exactly once on all paths. Before transfer, the adapter closes failed construction/validation handles. Android client and received broker descriptors are different handles: client closes its own after synchronous IPC return/error. The worker retains its physical slot and running source until the actual read/cleanup exits. No early replacement worker after cancellation.

`readAt` returns positive bytes, or -1 at EOF; zero progress before expected EOF is a read failure. Reads never request more than 65,536 bytes. `verifyUnchanged` checks the same opened object plus the adapter's available root/alias revalidation; false or an exception prevents success. A job may succeed only after exact byte count and final validation. The adapter validates supported file type/read-only/seekability/link count before admission without reading document contents.

Capabilities advertise protocol major/minor 1/0, fresh epoch, supported operation IDs and the exact limits from `FIELDPACK.md`. Generated jobs have no source; file jobs require one and matching declared length. Dedup compares retained immutable request metadata; it must run before content reads. Null irrelevant fields are normalized consistently. Seed/alias semantics are explicit per operation; adapters cannot silently coerce bad numbers.

**Consequences.** One worker owns bounded work; one serialized state owner decides terminal races. Closing a session revokes access immediately while cooperative cleanup can continue. State retention and scheduler ownership remain centralized.

**Alternatives considered.** Coroutines in the public API add consumer/runtime coupling; callbacks add listener reentrancy. Neither is necessary for the baseline polling contract.

## ADR-B03 — generated stream and manifest parity

The existing documents name a seed but do not define byte generation. Freeze `generated-sha256-v1`: byte at zero-based offset `i` is the low eight bits of `seed + i`, with signed 64-bit seed and wrapping addition. This deliberately simple, deterministic stream is a scheduling/IPC workload, not a randomness primitive. The implementation must not use platform default PRNGs. Independent Python/JVM oracle tests include zero, one, 65,535/65,536/65,537 and 134,217,728 bytes, plus negative/wrapping seeds. A seed=0 one-byte stream contains `00`.

`FIELDPACK.md` remains authoritative for canonical manifest writer order/LF, strict duplicate-key and integer validation, 32 aliases, 16,384 encoded bytes and 134,217,728 aggregate bytes. The shared codec must reject duplicate keys before map conversion. Every UI exports only the complete successful active attempt. Cancel, loss, selection changes and disposal invalidate that attempt; a user initiated retry rehashes all files.

## ADR-B04 — toolchain and support claims

Local inspection observed Flutter 3.38.9 stable / Dart 3.10.8, Android platforms 35/36/36.1/37.0 and build tools through 37.0.0. Root observed JDK 17.0.14 active and JDK 21.0.5 available. Installed does not mean latest or tested. Build ownership must freeze a compatible Gradle wrapper, AGP/Kotlin and compile/target/min SDK tuple using official compatibility evidence and actual clean builds; no floating versions. Prefer JDK 21 for the host runtime and JVM 17 bytecode for shared core/Android, subject to the selected Kotlin toolchain. Do not upgrade the user's global Flutter/JDK installation implicitly.

The current macOS arm64 host with about 39 GiB free cannot satisfy a local AOSP image build. G3 stays explicitly resource-blocked; generated-only AOSP source is not a booted-system result. Emulator tests may establish functional Android behavior, not physical-device performance. Toolchain pins and actual devices belong in build/QA evidence before C01 is marked verified.

**Build decision update.** Quality head reports official compatibility evidence for Gradle 8.13, AGP 8.11.1, Kotlin 2.2.21, Java 17, compile/target SDK 36 and Build Tools 35.0.0. The initial min SDK 26 proposal is superseded by **min SDK 30** under ADR-B09. This tuple is approved pending actual build evidence; Flutter stays at observed 3.38.9/Dart 3.10.8. It is a selected compatible tuple, not a claim of the latest release overall. Quality owns central physical Samsung device scheduling, preventing concurrent installs/tests. Native Android Views are approved instead of Compose; Flutter uses Material 3. Both implement the same original design specification, with installed behavior independently checked.

## ADR-B05 — desktop file capability decision gate

JVM Unix sockets do not imply FD transfer. The host starts with an explicit root; the CLI sends flat aliases only. The desktop head must execute a probe showing descriptor-relative/no-follow opening and same-handle regular/seekable/nlink checks on the chosen JDK/filesystem. A path stat before opening does not prove opened-file link count. If required primitives are unavailable, expose generated support only and classify files as unsupported until a bounded native helper is separately scoped, reviewed and tested. Never silently weaken `FIELDPACK.md`.

A small native helper is preferable to a false containment claim if it is needed to deliver the user-authorized FieldPack product. Its proposal must specify resource ownership, ABI, no-follow/root checks, positional reads, mutation checks, build portability, and negative tests. JNI complexity is accepted only with that concrete evidence. Python remains an independent oracle, not a concealed host execution substitute.

**Probe result.** Desktop head executed JDK 21/macOS probe: directory stream is `sun.nio.fs.UnixDirectoryStream`, not `SecureDirectoryStream`; `unix:nlink` is path-based rather than public same-descriptor inspection. Four-byte big-endian length-prefixed Unix socket frames, capped at 16,384 payload bytes before allocation, are approved. File support follows ADR-B06.

## ADR-B06 — approved bounded macOS JNI file adapter

**Context.** The actual JDK probe could not satisfy the normative descriptor-relative/no-follow and same-FD metadata requirements. A path-only fallback would weaken the public contract and fail the real-file product.

**Decision.** Approve the narrowly bounded helper now implemented in [native_files.c](../../desktop/host/src/main/c/native_files.c), [NativeFiles.java](../../desktop/host/src/main/java/dev/boundarylab/desktop/NativeFiles.java) and [RootedFileProvider.kt](../../desktop/host/src/main/kotlin/dev/boundarylab/desktop/RootedFileProvider.kt). Internal JNI ABI:

```java
static native int openRoot(String root) throws IOException;
static native int openAlias(int rootFd, String alias) throws IOException;
static native long[] snapshot(int fd) throws IOException;
static native int pread(int fd, byte[] buffer, int bufferOffset, int length, long offset) throws IOException;
static native boolean revalidate(int rootFd, String alias, int fd) throws IOException;
static native void close(int fd) throws IOException;
```

`openRoot` uses `O_DIRECTORY|O_NOFOLLOW|O_CLOEXEC`. `openAlias` uses `openat` against the retained root and `O_RDONLY|O_NOFOLLOW|O_NONBLOCK|O_CLOEXEC`. Validate the alias in both managed and native boundaries. Same-FD `fstat` snapshots capture device/inode/link-count/type/size/mtime/ctime; directories are allowed for the pinned-root snapshot only. Content sources must be regular, read-only, seekable, `nlink==1`, and within the file cap. `pread` is bounded to 65,536 bytes with checked array/offset/length; `revalidate` uses `fstatat` with `AT_SYMLINK_NOFOLLOW` and compares opened identity. Java checks root-path identity against the retained root before/after processing, with documented same-user ancestor replacement limitations.

Java/Kotlin retains exact resource ownership and the shared core performs hashing. Native code must not process manifests, transfer descriptors over sockets, run arbitrary operations or allocate whole files. Missing/failed helper disables file capability explicitly while generated work can remain supported. No same-user adversary or transactional snapshot guarantee is added.

**Review gates.** Independent review must cover JNI bounds/error paths, EINTR/short reads, descriptor closure, root and alias races, symlink/hardlink/FIFO/directory rejection, and absence of unsafe fallback. Actual tests must exercise those inputs and changed/missing verification through the real host. Root alone marks verified after review plus execution; implementation/compilation is insufficient.

**Consequences.** Real FieldPack becomes feasible on this macOS/JDK host with a small platform-specific build artifact. The supported host/architecture and native library build must be pinned and reproduced. Other hosts require their own evidence.

**Alternatives considered.** Generated-only desktop is safe but leaves the required recipient workflow incomplete. Python subprocess delegation reuses the reference but would not establish a JVM file adapter. A path-only Java channel implementation cannot prove promised checks. A bounded reviewed JNI shim is selected.

## ADR-B07 — reciprocal Android trust and declared file metadata

**Context.** Independent security source review identified two implementation gaps: the SDK selected the broker by package/component without authenticating its signer, and the broker replaced the caller's declared length with observed descriptor size. A differently signed package-name squatter must not receive delegated file descriptors; a false declaration must not be normalized into accepted work.

**Decision.** Before binding or delegating any source, the SDK verifies that the explicitly resolved broker package has a matching signing identity under Android's supported signature comparison. Missing or nonmatching identity fails closed with a typed denial. The broker still independently enforces signature permission and actual caller UID/session ownership. This reciprocal check preserves the selected same-publisher trust model.

The SDK/native picker obtains actual opened-descriptor length for its declared request. The broker independently validates the received descriptor and compares its observed size with `totalBytes`; mismatch rejects and closes the received handle before admission. It does not overwrite a caller's declaration. Dedup compares the immutable declared metadata rather than silently changing it.

**Consequences.** Native/Flutter SDK integrations must pass real declared length and cannot use a zero placeholder. Review requires wrong-signer and declared-size mismatch regression evidence. These findings block the claimed security/file slice until fixed; source review alone is not installed wrong-signature execution evidence.

**Alternatives considered.** Trusting only package names permits descriptor delegation to an impostor. Deriving size solely at the broker makes a caller's immutable request metadata meaningless. Explicit reciprocal trust plus independent matching metadata is selected.

## ADR-B08 — positive partial reads and exact-length success

**Context.** C04 says “Short reads, premature EOF and injected I/O error cannot yield success.” Operating-system positional reads may legitimately return a positive count smaller than requested even when more bytes remain. The source interface already defines positive byte counts rather than an all-or-error transfer.

**Decision.** Accumulate positive partial reads using their actual count, checking cancellation/deadline between bounded calls. Never pad missing bytes or advance by requested count. Reject zero progress before completion, premature EOF, returned count exceeding requested bounds, and read errors. Success requires the exact declared length, an EOF/growth probe, and final same-source metadata validation. This explicitly clarifies C04: an incomplete/truncated input cannot succeed; a legal series of partial returns that supplies every expected byte can.

**Consequences.** Tests must include successful bounded partial chunks, zero progress, premature EOF, growth and injected errors. Zero-length inputs still require supported-source validation and EOF/mutation checks. This supports real OS behavior without weakening byte-count or resource limits.

**Alternatives considered.** Rejecting every short return is conservative but falsely rejects supported regular-file reads. Treating short return as EOF silently accepts truncation. Actual-count accumulation plus strict completion checks is selected.

## ADR-B09 — Android minimum API 30 after lint evidence

**Context.** Real Android lint found 17 NewApi errors in the file adapter: nanosecond stat fields require API 27, while `Os.fcntlInt` used for descriptor access-mode validation requires API 30. The original min SDK 26 was a proposed tuple, not an executed support promise. The current physical target is API 36.

**Decision.** Raise min SDK to 30 consistently in broker, Kotlin SDK, native client, Flutter Android client/plugin and denial fixture. Keep compile/target SDK 36. Preserve actual read-only mode validation and nanosecond metadata; do not suppress lint or substitute weaker stat/path checks. Quality's build owner coordinates edits and the sole build runner reruns lint/build.

**Consequences.** Android 11/API 30 is the declared installation minimum. Only actually executed API versions may be claimed tested; an API 36 run does not establish range-endpoint coverage. Supporting generated-only work on API 26–29 may be a future independently tested capability profile, outside this chosen initial application profile.

**Alternatives considered.** API-gating file support below 30 retains generated compatibility but introduces a second untested profile. API 27 alone fixes nanosecond fields but not access-mode validation. Suppressing checks or using second-resolution metadata does not fix the API 30 read-only primitive. One safe minimum profile is selected.

## ADR-B10 — per-call completion versus stale UI observations

**Context.** Stress-test expectations and disposal suppression can conflict if a generation guard silently drops a public SDK completion. An active Flutter/native caller must not wait forever after connection loss or SDK close.

**Decision.** Every admitted SDK operation callback completes exactly once. Closing the SDK produces typed `CLOSED`; an old-generation/disconnected request produces `LOST_OR_UNKNOWN`, preserving unknown admission rather than replay. Connection establishment may use its specific denied/incompatible/unavailable result. A late success must never escape after closure/loss. Arrange bounded main-thread failure delivery independently of physical Binder/read cleanup; do not wait indefinitely for blocked I/O or terminate worker threads.

This per-call completion is distinct from state/progress/UI observation. Suppress stale observations and prevent callbacks from updating a replaced/disposed screen. A destroyed Flutter engine may discard a completed native callback after native bookkeeping, because its Dart recipient no longer exists; an active engine's pending call must still settle. Resource cleanup and session revocation remain required even when there is no live UI.

**Consequences.** Close/loss race tests retain exact callback counts and eventual pending-count recovery. Main-thread posting must recheck disposition and return a typed error, not silently drop delivery. A callback timeout stops waiting, not physical execution; owned descriptors still close on their actual safe ownership path.

**Alternatives considered.** Suppress every stale callback to protect UI, which hangs active awaiters; or deliver stale success, which corrupts fresh state. Exactly-once typed completion plus separately guarded UI observation is selected.

## ADR-B11 — CI distributes one interoperable signed APK set

**Context.** Android and Flutter jobs on isolated CI runners generate different default debug keys. Although each APK builds, combining a broker/native pair from one job with Flutter from another violates the application's same-publisher signer contract. Local testing with one developer key does not prove downloaded cross-job artifacts interoperate.

**Decision.** The Flutter packaging job also assembles broker and native client. Publish the three APKs together as one matched set generated under that job's same ephemeral private signing key. Before publishing the artifact, use Android `apksigner` to extract and assert equal SHA-256 signer certificate fingerprints across all three. Publish only APKs, public fingerprints and artifact/source/toolchain provenance. Never upload, transfer between jobs or commit the private keystore. An independently built Android pair remains a separately labeled build-check artifact; instructions must not mix it with the matched Flutter set.

**Consequences.** Repeated CI jobs may produce distinct demonstration signing identities; a user may need to uninstall a previous demonstration set before installing a new one. This is debug demonstration distribution, not a stable publisher-key release or automatic upgrade policy. Workflow source/static validation does not imply a remotely executed CI run.

**Alternatives considered.** Uploading a shared private key from one job to another increases key exposure and is unnecessary. Disabling signer enforcement undermines the trust model. Producing the matched set in a single job with public certificate verification is selected.

## ADR-B12 — SDK validates bounded caller metadata before Binder

**Context.** Broker-side metadata validation cannot prevent a client from first constructing an oversized Binder parcel. Public SDK callers can provide malformed request IDs/aliases or constructed handles; `TransactionTooLarge` must not turn a deterministic argument error into a lost-session/reconnect event.

**Decision.** SDK preflight validates canonical UUIDs, safe aliases, operation ranges and encoded descriptor bounds before issuing Binder calls. Check string length before encoding/allocation. Use the shared grammar and exact 4,096-byte metadata cap. Apply checks to caller-supplied submit/request/handle fields that cross the boundary, inside the existing ownership/cleanup path so an invalid transferred PFD closes exactly once. Failure returns `INVALID`, does not invoke the broker and does not change connection generation/reconnect state. Broker validation remains independent and mandatory.

**Consequences.** Focused regression cases cover oversized ID, invalid alias, handle fields and FD closure, with zero transport invocation, exactly one completion and pending count recovery. This is a required bounded-interface correction before the current SDK slice is accepted, not a broad new API redesign.

**Alternatives considered.** Broker-only checks leave the transport allocation/error-classification gap. Treating every Binder transaction failure as invalid hides real process loss. Validate known local argument faults before IPC and retain true transport-loss handling separately.

## ADR-B13 — conditional online AOSP host, planning only

**Status.** Historical planning recommendation researched 2026-10-30. At its creation no host, account mutation, purchase, remote command, source download or image build had executed. The later explicit US$50 authorization and quota-driven profile in ADR-B14 supersede its pending-resource and storage assumptions. This conditional G3 track does not block Android/JVM publication.

**Context.** AOSP requires x86-64 Linux, at least 64 GB RAM and 400 GB free build/check-out storage. Cuttlefish additionally needs working KVM. The current macOS host cannot supply this profile. These requirements establish suitability, not a successful build or boot. [AOSP requirements](https://source.android.com/docs/setup/start/requirements), [Cuttlefish setup](https://source.android.com/docs/devices/cuttlefish/get-started).

**Decision.** Recommend this initial on-demand Google Compute Engine profile, subject to the selected project's actual quota, zone capacity and authorized budget:

| Setting | Selected planning value |
|---|---|
| Machine | Intel `n2-standard-16`: 16 vCPU, 64 GB RAM |
| Location | Prefer `europe-west4-a`, Netherlands; another N2-capable zone only after checking availability |
| OS | Ubuntu 24.04 LTS x86-64; `ubuntu-os-cloud/ubuntu-2404-lts-amd64`; record resolved immutable image ID |
| Storage | 50 GB boot disk plus 1,000 GB zonal `pd-ssd`, ext4 workspace mounted at `/srv/aosp`; no source mirror |
| Virtualization | `enableNestedVirtualization=true`; Linux KVM, ordinary non-Confidential VM |
| Operation | One full build or one Cuttlefish instance at a time; begin conservatively with 12 build jobs, adjust from memory evidence |
| Guest proof | One pinned x86-64 Cuttlefish userdebug image; record build/manifest and matching host-package revisions |

N2 supplies the stated memory/CPU and supports SSD Persistent Disk. Its listed Netherlands zone is a candidate, not a reservation. Ubuntu 24.04 is an explicit compatible LTS choice, not a claim of the newest OS. [N2 specifications](https://docs.cloud.google.com/compute/docs/general-purpose-machines#n2_series), [zone catalog](https://docs.cloud.google.com/compute/docs/regions-zones), [Ubuntu image catalog](https://docs.cloud.google.com/compute/docs/images/os-details).

Intel N2 avoids excluded E2, Arm and N2D profiles; enable nested virtualization directly rather than creating a legacy licensed custom image. Check the effective `compute.disableNestedVirtualization` policy and granted permissions before any future provisioning. A permission-denied policy read is unresolved, not evidence that nested virtualization is allowed. Check regional N2 CPU, aggregate CPU and SSD-disk quota headroom as well as actual zone capacity; quota alone does not reserve a machine. [Nested restrictions](https://docs.cloud.google.com/compute/docs/instances/nested-virtualization/overview), [enablement](https://docs.cloud.google.com/compute/docs/instances/nested-virtualization/enabling), [effective policy](https://docs.cloud.google.com/compute/docs/instances/nested-virtualization/managing-constraint), [allocation quotas](https://docs.cloud.google.com/compute/resource-usage).

**Reproducible preflight, not executed.** After the user selects an authorized project, these are read-only project checks. Set `AOSP_PROJECT` to that project; no create/enable/policy-update command is included:

```sh
gcloud resource-manager org-policies describe constraints/compute.disableNestedVirtualization --effective --project="$AOSP_PROJECT"
gcloud compute machine-types describe n2-standard-16 --zone=europe-west4-a --project="$AOSP_PROJECT"
gcloud compute regions describe europe-west4 --project="$AOSP_PROJECT" --format='json(quotas)'
gcloud compute project-info describe --project="$AOSP_PROJECT" --format='json(quotas)'
gcloud compute images describe-from-family ubuntu-2404-lts-amd64 --project=ubuntu-os-cloud --format='json(name,id,architecture,creationTimestamp)'
```

On the separately authorized/configured host, run the following as the intended build/Cuttlefish user, after installing the documented host packages and applying their KVM/network/render group membership. It uses no sudo, changes no permissions, creates only a transient empty KVM object and closes it. The memory check allows kernel reservations on a cloud-verified nominal 64 GB machine; it does not authorize selecting a smaller plan. [Linux KVM API](https://docs.kernel.org/virt/kvm/api.html).

```sh
python3 - <<'PY'
import fcntl, json, os, pathlib, platform, shutil, stat

def require(ok, message):
    if not ok:
        raise SystemExit('PREFLIGHT FAILED: ' + message)

require(platform.system() == 'Linux' and platform.machine() == 'x86_64', 'Linux x86_64 required')
require((os.cpu_count() or 0) >= 16, 'expected at least 16 virtual CPUs')
cpu = pathlib.Path('/proc/cpuinfo').read_text().split()
require('vmx' in cpu, 'Intel nested virtualization not exposed')
mem_kib = int(next(x.split()[1] for x in pathlib.Path('/proc/meminfo').read_text().splitlines() if x.startswith('MemTotal:')))
require(mem_kib * 1024 >= 60 * 1024**3, 'insufficient usable memory for nominal 64 GB profile')
root = pathlib.Path('/srv/aosp')
require(root.is_dir(), 'workspace mount missing')
free = shutil.disk_usage(root).free
require(free >= 400_000_000_000, 'less than 400 GB free workspace')
require(stat.S_ISCHR(os.stat('/dev/kvm').st_mode), '/dev/kvm is not a character device')
kvm = os.open('/dev/kvm', os.O_RDWR | os.O_CLOEXEC)
try:
    api = fcntl.ioctl(kvm, 0xAE00, 0)  # Linux x86 KVM_GET_API_VERSION
    require(api == 12, 'unexpected KVM API version')
    vm = fcntl.ioctl(kvm, 0xAE01, 0)   # KVM_CREATE_VM; no guest code runs
    os.close(vm)
finally:
    os.close(kvm)
print(json.dumps({'preflight': 'PASS', 'cpus': os.cpu_count(), 'memoryKiB': mem_kib, 'workspaceFreeBytes': free, 'kvmApi': api}))
PY
```

Record OS/kernel, instance description including enabled nesting, immutable image, disk mount/type/free space, host-package revision and preflight output. Then boot an unmodified `aosp_cf_x86_64_only_phone` userdebug baseline with images and `cvd-host_package.tar.gz` from the **same pinned build**. Require `adb` availability, `sys.boot_completed=1` and `getenforce=Enforcing` before the large source sync/custom-service phase. Device-node existence or KVM object creation alone is not Cuttlefish boot evidence. Pin the source manifest and exact branch-supported lunch target before custom image work. [Cuttlefish baseline procedure](https://source.android.com/docs/devices/cuttlefish/get-started).

**Consequences and guards.** Sixty-four GB is the documented memory floor; concurrent build/guest work may need the separately chosen `n2-standard-32`/128 GB profile. No build-duration or physical-device performance promise is made. Agree a spending ceiling and explicit stop/retention plan before provisioning; stopping compute does not remove retained disk charges. Keep SSH access restricted and reach the Cuttlefish web UI through an authorized tunnel; do not expose ADB or debugging endpoints publicly. The guest service remains generated-only under the existing G3 contract. G3 stays blocked until actual authorized host and enforcing-boot evidence exists.

**Alternatives considered.** Hetzner **bare metal** with at least 64 GB RAM, adequate usable SSD space, enabled VT-x/AMD-V and verified `/dev/kvm` avoids nested-hypervisor dependency but requires more OS/host administration and actual order/hardware validation. Hetzner **Cloud**, including dedicated-vCPU Cloud plans, is unsuitable for this KVM requirement according to its nested-virtualization FAQ. Prebuilt Cuttlefish alone is a cheap feasibility step but does not prove a custom image/service build. [Hetzner bare-metal virtualization](https://docs.hetzner.com/robot/dedicated-server/virtualization/general/), [Hetzner Cloud restriction](https://docs.hetzner.com/cloud/servers/faq/#can-i-run-virtual-machines-on-cloud-servers-or-rather-is-nested-virtualization-possible).

## ADR-B14 — authorized AOSP proof with a total US$50 ceiling

**Authorization and evidence boundary.** The user now authorizes optional Google Cloud AOSP build, minimum necessary tests and contemporaneous evidence, with **US$50 maximum total infrastructure cost**. That explicit instruction supersedes P01's historical “No cloud purchase” constraint for this attempt; frozen P01–P09 acceptance text is retained. Root alone owns cloud/account, git and board mutations. Chief edits this decision record; implementation owns `aosp/`. Repeated confirmation is unnecessary for steps within the authorized ceiling. A missing quota/resource or a projected over-budget attempt produces an explicit blocker, not an invented completion. Android/JVM publication remains independent.

**Pinned build and reuse.** Select Android 16 `android-16.0.0_r4`, manifest tag object `6a0aa432c646cef4c76b276c9a0f38ecaa6e0c59`, peeled manifest commit **`15128c9e27cfa599c48d294babd39286ee8f1426`**, and lunch **`aosp_cf_x86_64_only_phone-bp4a-userdebug`**. Read-only upstream queries verified the product definition, the `aosp_current` alias to `bp4a`, and the `bp4a` release configuration. The actual host must still execute lunch/build successfully. This is a deliberately pinned API-36-era baseline, not a claim of the latest Android/security release. Preserve `repo manifest -r`, upstream cleanliness, toolchain and exact commands before modifying the tree. [Pinned manifest](https://android.googlesource.com/platform/manifest/+/15128c9e27cfa599c48d294babd39286ee8f1426/default.xml), [product definition](https://android.googlesource.com/device/google/cuttlefish/+/refs/tags/android-16.0.0_r4/AndroidProducts.mk), [release mapping](https://android.googlesource.com/platform/build/release/+/refs/tags/android-16.0.0_r4/release_config_map.textproto), [release configuration](https://android.googlesource.com/platform/build/release/+/refs/tags/android-16.0.0_r4/release_configs/bp4a.textproto).

No existing native platform implementation was present at this inspection. Reuse the existing operation IDs, protocol/caps, generated-byte definition and independent conformance vectors; the C++ state engine is an explicit native port, not reuse of the Kotlin binary. Before paid time, prepare/review the daemon, portable engine tests, AIDL, native facade/probes, Soong definitions, init/policy and integration recipe locally. Large AOSP sources stay off the Mac.

**Anonymous prebuilt host preflight.** Retain ADR-B13's early prebuilt boot before the large source sync. Read-only checks on 2026-10-30 verified public build **16644040**, target **`aosp_cf_x86_64_only_phone-userdebug`**, listed by the official `aosp-android-latest-release` status endpoint. Both canonical artifact-viewer pages anonymously supplied a transient download URL, `forceLogin=false` and no identity token. Four-byte HTTP range reads returned 206 with gzip magic for `cvd-host_package.tar.gz` (**898,832,924 bytes**) and ZIP magic for `aosp_cf_x86_64_only_phone-img-16644040.zip` (**1,163,636,385 bytes**). This proves availability of a matching pair, not a successful boot or equivalence to the selected Android 16 source baseline. [Official status](https://ci.android.com/builds/branches/aosp-android-latest-release/status.json), [pinned host archive](https://ci.android.com/builds/submitted/16644040/aosp_cf_x86_64_only_phone-userdebug/latest/cvd-host_package.tar.gz), [pinned image archive](https://ci.android.com/builds/submitted/16644040/aosp_cf_x86_64_only_phone-userdebug/latest/aosp_cf_x86_64_only_phone-img-16644040.zip).

Use those two official archives directly; an unverified `fetch_cvd` executable or a Google Build API credential is unnecessary. Parse the viewer's JSON `JSVariables.artifactUrl`, validate its HTTPS storage host, stream to a bounded temporary file, verify exact length/magic and compute SHA-256 before extraction. Receipts contain canonical viewer URL, pinned build/target, byte count and computed digest; do not persist transient signed query strings. Never copy a Google account credential or grant a VM service account to retrieve these public artifacts. Record download failures accurately. Stop on changed/missing artifacts rather than silently selecting another build. Full artifact hashes remain unmeasured until the remote downloads.

Install **`cuttlefish-base=1.57.0`** from Google's scoped signed Artifact Registry apt repository; record the selected package version and signed repository metadata. This supplies host resources, while the matched build archive supplies launcher binaries. Use the real non-root `aosp` account, whose configured home directory is `/srv/aosp/home`; never repurpose `HOME`. Set `ANDROID_HOST_OUT` and `ANDROID_SOONG_HOST_OUT` to the extracted host package and `ANDROID_PRODUCT_OUT` to the extracted images; select the explicit image directory when launching. Confirm supported launcher flags from that archive before execution. Apply required groups/udev/kernel changes, reboot/fresh-login, and execute the non-root KVM API/create-VM check before downloading. Keep all debug ports private. After a recorded successful prebuilt boot, stop it, retain small evidence/hashes and remove its attempt-owned archives/extracted images before source sync to recover capacity. [Official host packages](https://github.com/google/android-cuttlefish/blob/main/README.md), [matching image/host requirement](https://source.android.com/docs/devices/cuttlefish/get-started).

**Smallest meaningful integration.** Build the unchanged pinned Cuttlefish target from source and boot it first. Then add the native daemon and deliberately platform-built native facade/probes in `system_ext`, incrementally rebuild the affected image/host artifacts in the same output tree, and boot that custom image. Daemon work stays outside `system_server`; use one worker and the published generated-only policy. No user-file operation, FD-bearing API or attachment storage is introduced. Track/freeze internal structured AIDL through real Soong API tasks, with no `unstable:true` or VINTF claim. The native facade is an intentional platform API, explicitly **not** a new Java `framework.jar` manager or kernel feature. [AOSP build process](https://source.android.com/docs/setup/build/building), [internal AIDL tracking](https://source.android.com/docs/core/architecture/aidl/stable-aidl).

**Bounded first-boot diagnosis.** Root reported the first paid-host prebuilt launch failed before ADB: crosvm's mount-namespace `unshare(CLONE_NEWNS)` returned `EPERM`, followed by PCI-proxy connection resets. Non-root KVM API 12 had passed. This alone does not establish an AppArmor cause. Preserve the failed launch logs and inspect kernel AppArmor denials correlated to the executed crosvm path/PID and current sysctls/profile. If those facts establish Ubuntu's unprivileged-user-namespace restriction against an otherwise unconfined crosvm, permit one narrowly scoped recovery: a named AppArmor profile attached only to the exact resolved crosvm executable, using Ubuntu's documented `flags=(unconfined) { userns, }` compatibility pattern. Record executable digest, profile text/load result and resulting effective label. Do not replace an existing restrictive profile, use a path wildcard, change global AppArmor/userns sysctls or disable Cuttlefish's own sandbox. This explicitly permits the known executable's namespace-based sandbox; it does not claim that executable gains full AppArmor confinement. Relaunch once, verify global controls unchanged and Cuttlefish sandbox enabled, and require an actual enforcing guest boot. If the evidence disagrees, a new error appears that cannot be resolved within the remaining preflight allocation, or the scoped change fails, preserve evidence and delete the attempt instead of broadening permissions. The original 30-minute preflight ceiling remains. [Ubuntu 24.04 namespace compatibility guidance](https://github.com/ubuntu/ubuntu-release-notes/blob/main/docs/24.04/index.md), [AppArmor namespace restrictions](https://documentation.ubuntu.com/security/security-features/privilege-restriction/apparmor/).

Root subsequently captured the matching denial at 16:36:27 UTC: crosvm PIDs 2462/2463 transitioned from `unconfined` into `unprivileged_userns`; child PIDs 2474/2479 were denied capability 21 (`sys_admin`). The executable was `/srv/aosp/preflight/cf/bin/x86_64-linux-gnu/crosvm`; both `apparmor_restrict_unprivileged_userns=1` and `unprivileged_userns_clone=1` were recorded. These facts support the scoped namespace-profile recovery. Original exit-255 launch evidence and journal were retained before stopping Cuttlefish. Recovery success remains unproven until the new boot receipt exists.

After that profile loaded, OpenWRT booted but the Android VMM stalled before kernel output. A final bounded read-state diagnostic established a different blocker: main crosvm PID 3324 waited on the GPU child's IPC response; child 3348 was `pcivirtio-gpu`, with worker 3352 polling. `frames.sock` had a listening backlog of one and only `run_cvd` PIDs 3057/3060 holding its listener, while no WebRTC consumer existed and launch explicitly set `--start_webrtc=false`. This is affirmative evidence of an unaccepted graphics connection, not merely slow Android boot. The graphics child retained `CapEff=0`, `Seccomp=2` and the scoped crosvm label. Critical arbitration therefore permits one additional **single-flag** correction, `--start_webrtc=true`, restoring the graphics consumer with identical image/GPU/VMM/sandbox settings and the unchanged private firewall. Preserve both preceding failures; allow at most 180 seconds and never extend the original 16:58 UTC preflight deadline. No GPU/VMM matrix or permission broadening is authorized. Only actual ADB boot completion and guest `Enforcing` can clear this gate. [Official default WebRTC launch](https://source.android.com/docs/devices/cuttlefish/get-started), [streamer socket/consumer separation](https://android.googlesource.com/device/google/cuttlefish/+/refs/heads/main/host/commands/run_cvd/launch/streamer.cpp).

**Executed prebuilt host gate: PASS at 16:54:01 UTC.** Chief independently read receipt `public-boot-verification-and-screenshot-09f86f1c6e6844e8a2539aeabccb4567` and its complete 812-byte stdout. The third launch succeeded; ADB reported the device, `BOOT_COMPLETED=1`, guest `SELINUX=Enforcing`, UID 2000 with `u:r:shell:s0`, `enable_sandbox=true`, and both recorded host namespace sysctls still 1. The fingerprint is **`generic/aosp_cf_x86_64_only_phone/vsoc_x86_64_only:17/CP2A.260605.016/16644040:userdebug/test-keys`**. This is Android 17 public-prebuilt host feasibility only, **not** the Android 16 source baseline or P02 completion. Screenshot SHA-256 is `5f57ccd7d901c9b65d0adeb06cf5f7bc065c3fd1683e514cb3847474cfd0de20`; command stdout SHA-256 is `45f8308a26102e5d18101301265d56dc17e6acb69082039d75e6f9e091c28d0c`. The generic collector's JSON-probe assertion field is not used as a pass claim here; these are explicit checks of actual stdout values.

Proceed with only the already selected source procedure: export receipts/logs/screenshot, stop the prebuilt guest, remove its attempt-owned downloaded/extracted artifacts, then sync the pinned Android 16 manifest under the four-hour ceiling. Retain the post-sync 180-GiB gate, unchanged-source build/boot prerequisite, single output tree, and original cloud lifetime/budget controls. When the source build produces its matching crosvm executable, resolve and hash that artifact and replace the expired preflight-path AppArmor attachment with that exact source-output path using the same documented profile pattern; no wildcard/global exception is introduced. Record the new executable/profile hashes and carry forward `--start_webrtc=true` with the private firewall and enabled sandbox. This routine source-path update needs no repeated approval.

**Source-build host limitation, observed 17:21 UTC.** Root reported that the unchanged Android 16 baseline reached the actual build in attempt r3, with `PLATFORM_VERSION=16`, `REL`, build ID `BP4A.251205.006`, the selected userdebug target and `/srv/aosp/out`. The preceding two failures were preparation defects in our wrapper/host: upstream `envsetup.sh` was incompatible with inherited shell `nounset`, then the root-owned workspace lacked the writable output directory. Preserve their receipts; corrections disabled only wrapper `nounset` before upstream functions and created only the owned output directory, without changing AOSP source.

The actual build emitted upstream warning **“Build sandboxing disabled due to nsjail error.”** Continue the unchanged build in the dedicated disposable VM as unprivileged `aosp`, with no VM service account or account credentials. Record this as a compilation-host limitation; do not claim build sandbox confinement or hermetic execution. Do not chase the warning by disabling host controls, adding a broad namespace exception or changing upstream source during this run. Existing source-integrity, disk, time and cost gates still apply. This decision does **not** waive the separate runtime requirements: the resulting source image must boot using enabled Cuttlefish sandboxing and guest SELinux enforcing before P02 can pass; custom-service domains must also remain enforcing for later acceptance.

**Output-path correction after actual r3 failure.** At 17:22:43 UTC, Soong failed after its 280-step bootstrap while generating `continuous_native_tests`: `Path is outside directory: /srv/aosp/out/host/linux-x86/framework/net-tests-utils-host-common.jar`. This is distinct from the nonfatal nsjail warning. Chief verified the exact pinned source: `ci_tests/ci_test_package_zip.go` line 300 removes the product-output prefix; lines 301–303 skip the remaining path only when it starts with literal `out`; line 319 passes the remaining path into `PathForModuleOut(..., "STAGING", f)`. The host-framework absolute path bypasses that literal-prefix check, and `android/paths.go` lines 2143–2145 rejects its leading slash with the observed error. [Pinned packager](https://android.googlesource.com/platform/build/soong/+/refs/tags/android-16.0.0_r4/ci_tests/ci_test_package_zip.go#300), [pinned validator](https://android.googlesource.com/platform/build/soong/+/refs/tags/android-16.0.0_r4/android/paths.go#2143).

Use standard **`OUT_DIR=out` from `/srv/aosp/source`**, yielding one physical output tree at **`/srv/aosp/source/out`**, superseding the initial absolute output location. This reaches the existing upstream skip logic without modifying AOSP, removing modules or changing the target. Preserve/export the failed r3 log, manifest, receipt and selected partial-output hashes; verify its process group is cleaned up before deleting only the failed attempt-owned `/srv/aosp/out`. Reject symlinks and keep the new directory owned/writable by `aosp`. Update the wrapper and supervisor receipt to report the new actual path. Preserve all source-pin/cleanliness, same-filesystem free-space, `m -j12`, original time and cost guards. The correction is justified by the exact path-handling code; success remains dependent on the next real build and boot.

Use a dedicated daemon SELinux domain, explicit service-manager type and narrow Binder permissions. The proposed authorized probe runs as actual shell UID 2000 in its dedicated domain; the denied probe runs as actual nobody UID in a distinct `boundarylab_denied` domain. Give that negative fixture only the narrow discovery/Binder permission necessary to reach this daemon's UID authorization gate and record typed `DENIED`. This is an actual caller-denial test inside enforcing policy, not proof of a SELinux discovery denial; a separate discovery-denied case may supplement it. Capture identities rather than trusting a supplied UID. Do not let `adb root` accidentally turn the authorized case into UID 0 or a permissive `su` domain. Keep the daemon and both exercised client domains enforcing, not merely global `getenforce=Enforcing`. Init `restart_period 10` bounds respawn rate; do not mark the experimental service device-critical or reboot the OS on its failure. Three observed crash/restart intervals must verify the configured rate. [Pinned init semantics](https://android.googlesource.com/platform/system/core/+/refs/tags/android-16.0.0_r4/init/README.md).

**Quota-adjusted storage.** Root's actual unused regional quotas are N2 CPUs 200, total CPUs 200, standard disk 4,096 GiB and SSD 500 GiB. Use **500 GiB `pd-ssd` workspace plus 50 GiB `pd-standard` boot**, retaining the ADR-B13 N2 16-vCPU/64-GB host and nested KVM. Five hundred GiB exceeds the documented 400-GB checkout/build floor; the exact selected-tag footprint is unmeasured until sync. Use shallow/current-revision checkout, no full mirror/history, no all-tag fetch, no clone bundles, one target and one `OUT_DIR`. Do not remove required manifest projects to force a successful-looking build. Avoid ccache, duplicate baseline/custom outputs and redundant `dist` image archives. Preserve baseline image hashes/build logs and its successful boot before reusing outputs incrementally.

Require at least **180 GiB free after sync** before the full build; warn at 80 GiB free and stop the build in an orderly way before 40 GiB free. If the checkout/output cannot fit, record sizes and stop/delete the attempt rather than silently increasing spend or weakening P02. A no-charge quota request may be investigated before paid start; do not pay for a waiting machine. A 1-TB standard workspace offers capacity but risks much slower I/O within the elapsed-time ceiling; 500-GiB SSD with measured gates is selected. These are engineering reserves, not measured AOSP footprint guarantees. [Official storage requirement](https://source.android.com/docs/setup/start/requirements).

**Budget and time stop.** Root configures one fixed UTC termination time at most 24 hours after creation, **termination action DELETE**, with both attached disks `autoDelete=true`, and verifies the applied API response before workload. Use an absolute deadline, not a run-duration counter reset by restart. This native timer is only one safeguard: Compute Engine clears the effective termination timestamp when the VM stops or suspends, so it does not establish a wall-clock deletion guarantee for retained stopped-instance disks. The independent cleanup task below is also required. No snapshots, reservations, managed groups, retained detached disks or automatic replacement VMs. At the previously researched list rates, the 24-hour VM plus 500-GiB SSD subtotal is approximately **US$23.60** (`24 × (0.855328 + 500 × 0.000256164)`), excluding boot/IP/network/tax. Root must validate the effective quote and include these extras in its conservative cumulative ledger; do not depend on credits, delayed billing export or alerts to enforce the ceiling. [Native timer lifecycle](https://docs.cloud.google.com/compute/docs/instances/limit-vm-runtime), [disk auto-delete](https://docs.cloud.google.com/compute/docs/disks/modify-persistent-disk).

**Independent wall-clock cleanup.** Before paid provisioning, prepare and verify one dedicated Cloud Tasks queue and one scheduled HTTP `DELETE` task for the exact intended instance endpoint, scheduled no later than 23 hours 45 minutes after the attempt's start. Re-read the task/queue before workload: expected URL/method, absolute schedule, OAuth account, queue `RUNNING`, retry settings and no routing/auth override. Retain the native hour-24 DELETE and normal early manual teardown. No recurring scheduler or worker VM is needed. The task survives the guest stopping and does not depend on the guest network, credentials or process. It remains an independent best-effort control, not a provider-enforced dollar ceiling.

Use a dedicated keyless service account in the isolated attempt project, with a custom role containing only **`compute.instances.delete`**, bound within that project, which contains only this attempt's one VM. No VM attachment, downloaded key, creation/update/stop permission or broad Compute Admin role. A project-scoped single-permission binding is selected over an unexercised resource-name condition for this disposable isolated project. The API URI still names only the intended instance; never recreate that name while its deletion task exists. Google's deletion guide distinguishes automatic attached-disk deletion from force-overriding a preserved disk: `compute.disks.delete` is required for that override, not for this plain instance DELETE with both verified `autoDelete=true` disks. Root retains separate operational authority to remediate any failed disk cleanup. [Deletion permissions](https://docs.cloud.google.com/compute/docs/instances/deleting-instance#required_roles), [DELETE API](https://docs.cloud.google.com/compute/docs/reference/rest/v1/instances/delete).

Authenticate with an OAuth **access token** and Compute scope, not an OIDC identity token. Verify the Cloud Tasks service agent's documented token-generation role and the creator's `iam.serviceAccounts.actAs` access to this cleanup account; do not grant those capabilities to the VM. Include one stable nonzero UUID `requestId` in the task URL for retried DELETE deduplication. Configure `maxAttempts=3`, `maxRetryDuration=600s`, `minBackoff=maxBackoff=300s`, and one concurrent dispatch. Actual CLI create/update readback retained `maxDoublings=16` despite requested zero; equal minimum/maximum backoff still fixes the nominal interval at 300 seconds. Record the applied value rather than claim zero. The actual queue uses supported `europe-west1` (Belgium); the VM remains in `europe-west4` (Netherlands). Both retry limits must be satisfied before Cloud Tasks gives up, so this is nominally three attempts spaced five minutes apart, **not** a strict three-attempt or exact delivery-time guarantee. Keep dispatch logs enabled for this tiny queue. [Task authentication](https://docs.cloud.google.com/tasks/docs/creating-http-target-tasks#use_http_target_tasks_with_authentication_tokens), [retry semantics](https://docs.cloud.google.com/tasks/docs/reference/rest/v2/RetryConfig).

A 2xx DELETE response means an asynchronous Compute operation was accepted; it does not prove instance/disk deletion completed. Root must verify the operation result and absence of the instance and both disks, whether deletion is manual, task-triggered or native. After early verified teardown, remove the pending task/queue and temporary cleanup identity/role. Cloud Tasks charges per operation, with the first million monthly account operations free; even if that shared free allowance is already consumed, this bounded task is negligible within the existing contingency. Include it in the ledger rather than assume a guaranteed free tier. [Operation response](https://docs.cloud.google.com/compute/docs/reference/rest/v1/instances/delete), [Cloud Tasks pricing](https://cloud.google.com/tasks/pricing).

Stop adding work when projected all-in cumulative cost reaches **US$40**; reserve the remaining US$10 for evidence transfer, shutdown/deletion latency, tax/rate uncertainty and cleanup. Compare the US$40 trigger to projected actual spend, including tax/FX/egress but excluding the still-unused US$10 contingency. Root's read-only calculator produced US$23.7942 for the 24-hour infrastructure subtotal including boot/IP, US$36.1118 after US$2 egress and 40% tax/FX reserve, and **US$46.1118** including the US$10 contingency. These are estimates, not settled charges. The US$50 ceiling applies across retries, not separately to each VM. No extension/recreation may bypass the cumulative ceiling. Preflight gets at most 30 paid minutes; if sync exceeds four hours or a baseline build is not green by hour 18, stop and retain the failure rather than start a rushed custom-image claim. By hour 20, do not start another image build; by hour 22, stop tests and export final evidence. Delete earlier on completion/failure; the fixed hour-24 deletion remains the backstop. Root verifies that VM and both disks are absent afterwards.

**Minimum tests, with evidence captured during each case.** Run the complete small portable native-engine unit suite locally before paid start, then its Android-native build once on the custom target. It must cover caps/retention/TTL, owner/session isolation, round robin, dedup/conflict, deadline/cancel races and first-terminal-wins with deterministic controls. Do not replace these cheap unit cases with a long soak. The remote acceptance set is:

| ID | Minimum executed proof | Ticket coverage |
|---|---|---|
| A0 | Host architecture/RAM/disk/KVM preflight, selected account/project, immutable image and source manifest/target; applied expiry/delete controls | P01 |
| A1 | Unmodified pinned source build succeeds; boot completes; fingerprint, image checksums, `adb` access and SELinux enforcing recorded | P02 |
| A2 | Custom source image boots; intended service registered automatically, distinct PID/domain/UID and image provenance; no permissive service/client domain | P03/P04 |
| A3 | Authorized native facade calls real Binder; protocol 1.0, generated capability only; independently expected digests for 0, 1, 65,535, 65,536, 65,537 and 134,217,728 bytes, including a negative/wrapping seed | P03/P05 |
| A4 | Unsupported major, negative length, limit+1, malformed metadata and file operation reject; no file capability/FD-bearing entry point or user data retention | P03/P05 |
| A5 | Actual denied caller fails; guessed foreign session/job read/cancel fails; capture caller identity and typed result, not just shell exit status | P04/P05 |
| A6 | Device-native deterministic engine tests pass, plus real Binder duplicate/conflict and cancel/deadline outcomes; no false success or second terminal result | P03 |
| A7 | Client token/process death closes its session; service kill/restart yields fresh epoch, old handles fail, explicit new request succeeds | P04/P05/P06 subset |
| A8 | Three service crash/restart intervals respect the 10-second configured rate, whole image stays available/enforcing; capture actual times | P04 |
| A9 | One end-state resource/descriptor/session accounting snapshot returns to expected baseline and a final known job succeeds; archive failures and final enforcing state | Bounded smoke only |

No expensive repeated full-build, broad device matrix or performance campaign is added to this minimum. Deterministic unit barriers are allowed in the native test binary; production daemon behavior cannot be replaced by a mock. Real Binder cases must run through the built daemon. A screenshot supplements command/results/provenance; it does not prove Binder or enforcing policy.

**Minimum-smoke gap correction while baseline builds.** The implementation owner's read-only review identified three missing exercised checks in the initial probe/harness: live foreign-session read/cancel, completed known work after the final restart, and comparable daemon FD snapshots. These are existing A5/A7/A9 requirements and must be added before the one combined custom-image smoke, rather than silently treated as covered. Authorize changes only to project `aosp/client/probe.cpp` and `aosp/scripts/device-smoke.py`; engine, protocol and SELinux policy remain unchanged. Keep the remote pinned AOSP tree entirely unchanged through the P02 source-baseline build and boot. Review the focused changes and record fresh source/archive hashes before any later overlay integration.

The ownership case uses two simultaneously live session tokens: submit a job in session A, attempt status/cancel with session B, require the existing contract's rejection without result disclosure or mutation, then prove legitimate A access still works. This proves session isolation; the separate real denied-UID case retains its distinct purpose. After the final service restart, require stale-handle failure and an explicit fresh known request completing successfully with an independently expected digest. For FD accounting, capture PID, descriptors and core counters at comparable quiescent points in the same daemon generation, warming lazy resources before the first snapshot; close the client/session and allow only bounded cleanup before the final snapshot. Preserve any unexplained growth or incomplete recovery as a failed/open result. These additions do not authorize benchmarks, a soak, a configuration matrix, extra application tests or an additional custom-image campaign; other unexercised acceptance clauses remain open.

**Evidence and completion.** Stream timestamped command, exit code, counts, stdout/stderr, build/boot logs and case results to a local-on-host evidence directory, then copy compressed receipts after every stage and before teardown. Keep selected service logs/denials, image/source hashes, actual process/domain records and minimal screenshots. Export small proof artifacts, patches/manifests and recipes; no full AOSP checkout or unnecessary multi-GB image copies. Record every failed/aborted run, budget estimate and final resource-deletion receipt. Root alone reconciles statuses from all original acceptance clauses.

| Ticket | Maximum defensible outcome of this attempt |
|---|---|
| P01 | Verified only after actual authorized host checks, pinned manifest and budget receipts; historical cloud restriction explicitly superseded above |
| P02 | Verified only after actual unmodified source build and enforcing baseline boot; prebuilt boot alone is insufficient |
| P03–P05 | May verify individually if all their original clauses and the native port/facade tests pass; compilation or this ADR alone is insufficient |
| P06 | Partial unless a **previously compiled** platform consumer actually passes a supported interface evolution; version number/frozen V1 alone is not that experiment |
| P07 | Remains open: no matched baseline/candidate trace-backed performance investigation in this minimum |
| P08 | Remains open: a short smoke cannot replace the required two-hour declared soak |
| P09 | Evidence/recipe can be prepared, but fresh final-image reproduction and dependency reconciliation are not inferred from the initial baseline/custom build |

**Alternatives considered.** Prebuilt-only Cuttlefish cheaply proves host feasibility but fails P02 source-build acceptance. Moving work into the ordinary APK broker fails the native image-service claim. Broad Java framework/system-server integration expands risk without being required by this native facade scope. The selected source-baseline plus small `system_ext` native service/probe path maximizes meaningful evidence within the capped attempt; any unfinished row remains visibly incomplete.

## Milestones and acceptance authority

1. **M0: integration contract and executable build.** Pins, shared API and process probes are available. A compilation receipt alone does not complete C01.
2. **M1: generated boundary product.** Core tests plus separate Android broker/native/Flutter and separate JVM host/CLI execute golden generated jobs. Capture packages/PIDs, cancel and loss behavior.
3. **M2: real FieldPack product.** Native and Flutter select supported files, hash through Binder and export; JVM host verifies separately supplied aliases. Good/changed/missing outcomes agree with Python. GUI exposes selection, progress, cancellation, results, errors and confirmed export.
4. **M3: acceptance evidence.** Independent code review, unauthorized/cross-owner tests, races/resources, old-consumer compatibility and documented clean reproduction. Unexecuted performance or device cases remain open.

These slices guide implementation order; they do not reduce the published G1/G2 acceptance list. Root alone decides board status from actual reports. Reversible implementation proceeds autonomously; contract weakening, invented evidence, global toolchain changes and unapproved external publishing are not shortcuts.

## ADR-B15 — scoped nsjail recovery after the baseline r4 failure (2026-10-30 UTC)

**Observed failure.** Baseline r4 ended at 20:56:18 with exit 1 at 84% (147,050/174,912 steps), no supervisor stop reason, its process group gone and no overlay. The watcher stopped before preparation or boot. At 20:54:48, the mandatory `trusty_security_vm_x86_64.elf` genrule failed in nsjail at `mount('/', '/', NULL, MS_REC|MS_PRIVATE, NULL)`. The matching kernel audit identifies the exact executable `/srv/aosp/source/prebuilts/build-tools/linux-x86/bin/nsjail`, transition into `unprivileged_userns`, and denied `setgid`, `dac_override` and mount operations. The pinned Trusty defaults explicitly set `use_nsjail: true`, and this target inherits them; the earlier optional Soong sandbox fallback cannot bypass this required genrule. [Pinned Trusty defaults and target](https://android.googlesource.com/trusty/vendor/google/aosp/+/refs/tags/android-16.0.0_r4/scripts/Android.bp#161).

**Decision.** Permit one separate exact-executable AppArmor user-namespace compatibility profile and one incremental baseline resume in the existing output tree. Use Ubuntu's documented `flags=(unconfined)` plus `userns,` pattern, retaining ABI 4.0 and `tunables/global`, with profile name `boundarylab-nsjail` attached only to the exact nsjail path above. Require verified ELF/path/hash and clean pinned binary provenance; reject an unexpected existing profile rather than overwrite it. Keep both global namespace controls at 1 and the crosvm profile unchanged. No root build, `setcap`, global relaxation, disabled sandbox, upstream source edit or removed module is authorized. If the same denial recurs, stop and inspect; do not broaden permissions. [Official Ubuntu 24.04 guidance](https://documentation.ubuntu.com/release-notes/24.04/#unprivileged-user-namespace-restrictions).

**Executed recovery evidence.** Root exported the complete failed log, manifest, source status and result, reporting all four local file hashes verified. Chief independently read successful recovery receipt `scoped-nsjail-userns-recovery-9f0588944b214ac0b19d5c4e8c7270ba`: at 20:59:11 the profile loaded as `boundarylab-nsjail (unconfined)`, both global controls remained 1, the crosvm profile was unchanged and source was unmodified. The binary's locked project commit is `412724a805835d89234d67c363f7ada7f5f8a67f`; binary SHA-256 is `3f97556c3cf8a83d3f5ae854e6dfc2f345355ead547dd661d07a369b6c2ba280`; applied profile SHA-256 is `c4a587b1ff1aa8a1e4a5157fb5ca32763c0b49eeefcefe03afe2d9b87d5ffb5f`.

Root reported r5 started at 20:59:33, retaining existing outputs and the same target, `m -j12`, supervisor, original deadline, single VM and aggregate US$50 ceiling. Give r5 a distinct receipt and exact watcher identity; r4 remains failed. Earlier r4 compilation ran with the optional host sandbox disabled, and that historical limitation remains. This profile is a per-program namespace opt-in, **not full AppArmor confinement**, and does not retroactively establish sandboxed compilation. At this decision, r5 build success, the Android 16 source-baseline boot and custom-service runtime remain unverified.

## ADR-B16 — custom-proof setup corrections and probe enforcing-state access (2026-10-30 UTC)

**Preserved operator failures.** Custom proof r1 booted Android 16 with the expected source fingerprint, `generated-v1`, enabled sandbox and global Enforcing, then shell received permission denied while hashing `/system_ext/bin/boundarylab_service`. Approve moving the existing `adb root`/wait/UID check immediately before artifact inspection, accurately naming it `root_for_artifact_and_test_placement`; retain unrooting and actual UID/domain assertions before authorized Binder tests. This changes inspection order, not image bytes or policy. In r2, all three image binaries matched, but the native test already present in userdata had mode `0644:0:0` and `nativetest_data_file`, with source/device SHA-256 both `aae13062a147b97c9859feb96833459d4b6fdb8087533af29582c0187a8849bd`. Approve only that hash-verified test fixture's `0644` to `0755` correction, recording before/after mode and unchanged content, owner and label. No daemon/probe push, source/policy change or rebuild was justified for these operator corrections. Both attempts failed before harness/native-test execution and verified cleanup; preserve their snapshots and operator hashes before a fresh bounded attempt. Neither is retroactively passed.

**Actual probe failure and cause.** In r3, the probe passed context/domain checks, then its `global_enforcing` assertion failed although shell/global receipts remained Enforcing. Kernel audit 206 identifies denied access to selinuxfs `enforce` by `boundarylab_probe`; the associated `openat` syscall returned `-13` (`EACCES`) under UID/eUID 2000 and `u:r:boundarylab_probe:s0`, with `permissive=0`. Pinned `security_getenforce()` opens that file read-only and returns `-1` on open/read failure. Existing base domain policy already permits selinuxfs directory search and file metadata access. Thus this was a denied self-observation, not evidence of a permissive guest. [Pinned libselinux implementation](https://android.googlesource.com/platform/external/selinux/+/refs/tags/android-16.0.0_r4/libselinux/src/getenforce.c#23), [pinned base policy](https://android.googlesource.com/platform/system/sepolicy/+/refs/tags/android-16.0.0_r4/private/domain.te#269).

**Reviewed decision.** Add only `allow { boundarylab_probe boundarylab_denied } selinuxfs:file { open read };` with explanatory comments. Both probes execute the same enforcing-state check. Preserve the assertion, dedicated enforcing domains and all authorization checks; grant no daemon, write, administration or broader macro permissions. The reviewed policy SHA-256 is `28b01c997f7f7adccd6c091c2d29a2badf5d4edf7741b4c792887f18ee61c831`, committed by root in `0d484a0ec7e3b60ce3394440d436f78108bce379`. Preserve/export r3, capture the new source checkpoint, incrementally build native then image, and run the existing proof once with unchanged single-VM, budget, deadline and cleanup controls. Frozen V1 API remains unchanged.

**Status at 22:17:09 UTC.** Chief independently read native-r3's actual successful receipt: exit 0, no stop reason, no remaining process group, 1,054-byte log SHA-256 `137b28499f4c635f9b4ab2a4e00cba189062c2971a74f1bd019542defebaa576`. Root reported image-r2 running. The rebuilt-policy proof-r4, Binder functional cases and native61 execution remain pending at this decision. Compilation and the earlier boot observations do not close P03–P05 runtime acceptance; every failed attempt remains part of the evidence.

## ADR-B17 — correlate actual FD denial instead of accepting generic Binder failure (2026-10-30 UTC)

**Observed r4 failure.** The rebuilt probe's enforcing-state check passed, followed by real caller identity/domain, discovery/capabilities, six golden vectors through 128 MiB, deduplication/conflict, terminal immutability, foreign-session denial and malformed/unsupported-operation checks. The raw-FD assertion then failed and cleanup was verified. Its old default detail, `KERNEL_PERMISSION_DENIED`, was misleading: the probe did not record its numeric transaction status, which remains **unknown**. Chief independently inspected preserved r4 logcat/kernel records: audit 202 at 22:20:32 correlates probe PID 4280, socket inode 39950, denied FD `use` from `u:r:boundarylab:s0` to `u:r:adbd:s0`, `permissive=0`, and the same audit's UID/eUID 2000/probe-context Binder ioctl. The driver separately reports `BR_FAILED_REPLY { source: EACCES } my_pid:4280`. These are observed denial facts, not a recovered numeric return or a retroactive passing assertion.

**Cause and decision.** Pinned libbinder maps both `BR_FAILED_REPLY` and `BR_FROZEN_REPLY` to `FAILED_TRANSACTION`; accepting that status alone would also accept unrelated failure. Preserve strict decoded service `INVALID` and explicit `PERMISSION_DENIED` branches, and correct only `aosp/client/probe.cpp` and `aosp/scripts/device-smoke.py`. The probe records actual numeric transaction/parse status, decoded exception/error, PID, sent-FD inode and realtime call interval. A `FAILED_TRANSACTION` observation remains pending, with no rejection PASS. The harness must correlate the exact PID/inode, FD-use AVC/domain pair and `permissive=0`, the same audit ID's UID/eUID/executable/context, and a same-PID driver `BR_FAILED_REPLY/EACCES` record within the fixed call interval plus one second on either side. Missing, ambiguous or unexpected evidence fails; capture raw logs even when the probe fails. [Pinned libbinder mapping](https://android.googlesource.com/platform/frameworks/native/+/refs/tags/android-16.0.0_r4/libs/binder/IPCThreadState.cpp#1201).

Root committed the reviewed two-file correction as `308b30744ad56440a73e268cab5c06b0a482f080`; reviewed probe SHA-256 is `cc3c7dadbeecc2ee33b1c791a2a5d466a184aa81720b70aff89c42ddf37fad91` and harness SHA-256 is `bb5b1868eb617043fb1fd32496a36057b3e11f5aa93859da58effc53bd3ee518`. No FD-transfer permission, SELinux policy, interface or engine change is authorized. Preserve r4 and its unknown numeric status; bind a new source checkpoint, incrementally build native/image, then run the existing bounded proof once. At this decision those new builds and proof are pending. Root's five offline parser fixtures used an explicitly synthetic observation/status with preserved audit text; they validate parser behaviour only and are not target execution or reconstruction of r4's missing status. Native61 and remaining runtime acceptance are not inferred.

## ADR-B18 — Android nobody identity and final bounded proof (2026-10-30 UTC)

**Observed r5 failure.** The strict FD correlation passed and execution advanced through generated operations, cancellation, client death and the resource cycle. The denied init fixture then failed its UID assertion: actual PID 4382 ran as Android `nobody`, UID **9999**, in the expected enforcing `boundarylab_denied` domain; the probe incorrectly expected POSIX-style UID 65534. The assertion failed before its `openSession` denial check. Overall r5 remains failed with verified cleanup and no native61/restart proof inferred. Root reported its exported snapshot locally verified across 1,050 archive members and 1,049 payload hashes.

**Decision and scope.** Pinned Android 16 defines `AID_NOBODY` as 9999. Original P04 AC3 requires “Authorized platform client succeeds; denied client fails.” It does not prescribe UID 65534, and ADR-B14 selected actual Android `nobody`. Correct only the probe expectation to 9999 with an `Android AID_NOBODY` comment; keep actual identity/domain/Enforcing assertions and typed `DENIED`. The service's sole authorized UID remains 2000. No init identity, caller allowlist, permission or policy change is authorized. [Pinned Android identity definition](https://android.googlesource.com/platform/system/core/+/refs/tags/android-16.0.0_r4/libcutils/include/private/android_filesystem_config.h#204).

The reviewed one-line correction is root commit `444ae29a31833616c465e155a3dc1335d061cda6`, probe SHA-256 `9a21355ffb8ab48905d490b9421f7fcf0c69f22298fb93d1fae32354f873f3ef`. Historical review/runtime records containing 65534 remain historical and are superseded by this explicit identity amendment; root will correct current explanatory documentation to 9999. Bind the new source inventory and permit **native-r5 → image-r4 → one proof-r6 as the final attempt ceiling** under the existing VM, budget and deadlines. No further phase or retry extension is authorized by this decision. At recording, the pipeline is active and no final target pass exists; if it fails, preserve the exact remaining acceptance gaps rather than infer completion.


## ADR-B19 — supported macOS runner after provider brownout (2026-10-30 UTC)

**Observed infrastructure failure.** PR59 candidate `5cffa2116c1a2ac0a5733a78b5c8f90eee8e2780`, run41141562437, had its macOS14 job cancelled by the provider after zero executed steps. The actual annotation identifies the scheduled October30 brownout and November2 retirement; this is not a product-test failure or a passing lane. [Preserved provider receipt](../evidence/build/ci-macos14-brownout-20261030.json), [official retirement and mitigation](https://github.com/actions/runner-images/issues/13518). Root cancelled the superseded run rather than consume more checks on a candidate unable to pass all five lanes.

**Astra decision.** Chief independently verified and approved only `macos-14` → `macos-15` in the existing JVM matrix. Both standard labels use ARM64; `macos-15-intel` would introduce an unnecessary architecture change. Preserve JDK17, Gradle, action SHAs, all test commands, five lanes and timeouts. Standard GitHub-hosted runners are free for this public repository; no paid runner, cloud resource or AOSP test is introduced. [Official runner architecture and public-repository terms](https://docs.github.com/en/actions/reference/runners/github-hosted-runners).

Require actual five-lane success on the revised candidate and retain its actual runner image/toolchain provenance before merge. This proves the recorded hosted CI environment only; it establishes no physical macOS15 product support, does not repeat the completed cloud proof, and does not close wider roadmap acceptance.

## ADR-B20 — independently consumable libraries and explicit native service configuration (2026-10-31 UTC)

**Status and context.** Accepted for implementation by root after guardrail review; verification remains tied to actual receipts. BoundaryLab already has a reusable Kotlin engine, Android Binder client, and native Soong engine/client libraries, but app consumers depend on repository-local builds and fixed broker identity. The native service implementation is embedded in its executable. A substantive new workload remains undecided; this decision does not select one or authorize a custom-image build.

**Decision.** Publish the existing core JAR and Android SDK AAR/POM into a local filesystem Maven repository and prove an independent consumer resolves their coordinates and transitive dependencies. Flutter consumes the resulting Android artifact without importing sibling source projects. Preserve existing package namespaces, protocol major, real workload code and lifecycle behavior. Add `BrokerEndpoint` configuration without removing the existing `BoundaryClient(context)` constructor. Resolve and validate the exact enabled/exported component, a signature-level permission owned by the expected broker package, and matching app/service signatures before binding and during handshake. Missing identity metadata (`NameNotFoundException`) fails as `DENIED`, consistent with ADR-B07. A configurable endpoint is an address, not permission to bypass these checks. Keep the default broker identity unchanged.

**Native reuse contract.** Retain `libboundarylab_engine` and `libboundarylab_platform_client`; extract the existing Binder service and lifecycle into `libboundarylab_service` with `RunService(const ServiceConfig&)`. `ServiceConfig` contains a service name and explicit `std::vector<uint32_t>` caller UIDs. A shared production configuration helper validates 1–127 ASCII name bytes from `[A-Za-z0-9_.\-/]` and 1–16 distinct UIDs, each at most `INT32_MAX`. These are BoundaryLab configuration bounds, not assertions about Android's universal limits. A copied `CallerPolicy` authorizes only exact listed transport UIDs; invalid or empty configuration denies every caller. `RunService` rejects invalid configuration before Binder setup or registration (exit 2), and returns exit 1 for registration failure or unexpected pool exit. No runtime property, environment variable, implicit UID fallback or caller-supplied identity broadens access.

The legacy `boundarylab_service` executable becomes a thin entry point with the exact existing `dev.boundarylab.platform.IBoundaryLab/default` name and `{2000}` allowlist. Preserve its init/product/SELinux integration. Before generated parcel decoding, the host still enforces caller authorization, rejects one-way transactions, and applies its existing transaction-size/FD restrictions. Preserve native engine/session/death handling without introducing another execution path. Add an explicit-service-name native `Client` constructor while retaining the actual zero-argument constructor and its default behavior; an invalid explicit name remains disconnected and never falls back to the legacy service.

An optional adopter example demonstrates linking the real host library and supplying image-owned identity/policy. It is not installed by the default proof product. Its unchecked/default caller configuration must deny or fail startup, not silently authorize shell/root. An adopter must allocate its actual OS principal and supply matching init, service-context and SELinux rules. This is native image integration scaffolding, not ordinary application authorization. The app SDK and native platform facade remain distinct transports; no hidden-API reflection, application UID guess, broad untrusted-app rule or production app bridge is introduced here.

**Compatibility and tradeoffs.** Frozen native V1 AIDL and hashes remain untouched. Existing generated-only native capabilities, descriptor rejection, uncertain-outcome behavior, bounded retention and no replay remain intact. File/document/AI/hardware operations are not added by packaging or host extraction. The benefit is reuse of real implementations with explicit deployment choices; the cost is keeping two documented transports and requiring adopter-owned image policy. Alternatives rejected for this scope are a generic plugin runtime, a new native protocol, a framework/system-server integration and a production application bridge before its caller/workload requirements are known.

**Confirmation and evidence boundary.** Require artifact/POM inspection and isolated consumer compilation; existing JVM/Android/Flutter checks; real matching-signer and rejection tests where a local device is available; native configuration tests exercising the same production helper; and existing sanitizer-enabled engine tests. Native source extraction must receive a diff review against the old service. Host checks do not compile or execute libbinder/Soong/SELinux integration. The new service library, constructor and optional product integration remain image-unverified until a separately authorized pinned Soong build and enforcing custom-image run produce receipts. Prior custom-image evidence remains attributable only to its recorded historical source. No cloud resources, spending, external publication or additional image campaign is authorized by this decision.
