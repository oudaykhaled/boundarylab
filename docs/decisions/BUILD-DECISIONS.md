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

**Status.** Recommendation researched 2026-10-30; no host, account mutation, purchase, remote command, source download or image build executed. User resource choice remains pending. This conditional G3 track does not block Android/JVM publication.

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

## Milestones and acceptance authority

1. **M0: integration contract and executable build.** Pins, shared API and process probes are available. A compilation receipt alone does not complete C01.
2. **M1: generated boundary product.** Core tests plus separate Android broker/native/Flutter and separate JVM host/CLI execute golden generated jobs. Capture packages/PIDs, cancel and loss behavior.
3. **M2: real FieldPack product.** Native and Flutter select supported files, hash through Binder and export; JVM host verifies separately supplied aliases. Good/changed/missing outcomes agree with Python. GUI exposes selection, progress, cancellation, results, errors and confirmed export.
4. **M3: acceptance evidence.** Independent code review, unauthorized/cross-owner tests, races/resources, old-consumer compatibility and documented clean reproduction. Unexecuted performance or device cases remain open.

These slices guide implementation order; they do not reduce the published G1/G2 acceptance list. Root alone decides board status from actual reports. Reversible implementation proceeds autonomously; contract weakening, invented evidence, global toolchain changes and unapproved external publishing are not shortcuts.
