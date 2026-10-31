# BoundaryLab native AOSP profile

The 2026-10-31 reusable-host extraction below is **source-only for Binder/Soong/image
integration**. Its production config/policy helper and unchanged Engine pass local native
sanitizer checks. The historical custom-image result immediately below applies only to
its recorded source revision; it does not certify the new extraction or adopter template.

Status: **the bounded Android 16 source-build and custom native-service proof passed** at
source `444ae29a31833616c465e155a3dc1335d061cda6`. The completed run records 236 harness
checks, 61 unique native assertions and 34 operator checks, with guest SELinux Enforcing,
all three security domains checked against loaded policy, matching image binaries and
verified Cuttlefish cleanup. [Actual receipts and preserved failures](../docs/evidence/aosp/custom-20261030/README.md)
are separate from earlier host-only evidence in `evidence/local/`.

This is the generated-only native platform integration in P03–P05. Compatibility
evolution, performance, soak and fresh final-image reproduction remain open. The facade
is a native platform library; the Java framework, `system_server` and kernel are unchanged.

## Reuse from an image-owned native service

The existing Soong `libboundarylab_engine` and `libboundarylab_platform_client` modules
are joined by `libboundarylab_service`. Link that static library and call the public
[`boundarylab::RunService(const ServiceConfig&)`](service/ServiceHost.h) from your own
native daemon's main. It owns the existing V1 Binder dispatch, exact caller gate,
client-death session leases, bounded Engine, registration and thread pool. It continues
to serve only the generated SHA-256 operation; no workload extension API is introduced.
A consumer also links the frozen `boundarylab-platform-V1-cpp`, `libbinder`, `libutils`
and `libcrypto` dependencies as shown in the [inert image integration template](examples/image-host/README.md).

[`ServiceConfig`](service/ServiceConfig.h) takes an explicit registry name and exact
`std::vector<uint32_t>` caller UID list. The shared production
`libboundarylab_service_config` helper enforces these library-specific limits:

- Registry name: 1–127 bytes, only ASCII `A–Z a–z 0–9 _ . - /`; embedded NUL,
  whitespace and non-ASCII are rejected. This is not an Android platform maximum.
- Caller list: 1–16 distinct UIDs, each at most `INT32_MAX`; empty, duplicate or
  out-of-range values invalidate the entire configuration.
- `CallerPolicy` snapshots validated values. Invalid/default policy denies every
  caller. `RunService` returns 2 before Binder setup/registration for invalid config;
  registration failure or unexpected pool exit returns 1.

The Binder transport supplies the UID; the policy checks it before generated parcel
unmarshalling, with the existing FD/4096-byte/oneway restrictions retained. There is no
implicit root or shell entry. UID allowlisting is appropriate only where the image owner
controls the actual native caller identity and narrow SELinux domains. It is not a
general production application authentication or signing/permission scheme.

The legacy `boundarylab_service` is now a thin main supplying exactly
`dev.boundarylab.platform.IBoundaryLab/default` and `{2000}`. Its binary name, init rc,
product installation and SELinux policy remain unchanged, as do all AIDL and frozen V1
files. Existing `Client()` keeps that name; new
`Client("org.example.generated_hash/default")` performs an exact nonblocking lookup.
An invalid name leaves the client disconnected without lookup or fallback. Both clients
retain existing transport mapping, session/epoch handling and no-replay semantics.

The [adopter main and product/policy templates](examples/image-host/README.md) are not
included by the default product or Soong graph. Their empty caller list deliberately
rejects startup until the image owner supplies its actual allocation. Their integration
requires a real pinned Soong build and image authorization/lifecycle checks; neither was
executed for this extraction on the Mac. Historical image receipts remain untouched.

The host script now builds the actual production `ServiceConfig.cpp` with the Engine
checks. `service-config-checks.jsonl` covers valid names/UIDs, name/UID boundaries,
invalid/default deny-all, exact configured caller decisions and immutable snapshots.
These helper checks do not exercise Binder lookup/registration/dispatch or SELinux.

## Fixed source target

- AOSP manifest tag: `android-16.0.0_r4`.
- Manifest peeled commit: `15128c9e27cfa599c48d294babd39286ee8f1426` (chief's upstream audit).
- Lunch: `aosp_cf_x86_64_only_phone-bp4a-userdebug` (must resolve in actual envsetup).
- One source/output tree: unchanged baseline build/boot first, then overlay and incremental custom build.
- Root owns cloud resources, budget, manifest capture, Git and board. This overlay performs none of those actions.

## Implementation decision

Context: P03 requires a dedicated native service, while the shared policy is Kotlin/JVM.
Decision: port the generated-only policy to a small C++17 engine, checked against independent
Python digest vectors and the existing core semantics. `boundarylab_service` runs from
`system_ext`, as UID nobody, in its own enforcing SELinux domain. Init starts it in class
main and applies `restart_period 10` (minimum start-to-start interval; no critical reboot).
One physical worker uses a 65,536-byte content buffer. One mutex owns policy decisions;
no content hashing holds it. Sessions/queue/outstanding/terminal storage retain the frozen
16/16/4/64/60,000 ms/1,048,576-byte bounds. Cancellation and deadline decisions cannot
replace a committed terminal state. Session death revokes access immediately.

Structured `boundarylab-platform` AIDL uses libbinder CPP. Pinned Soong update/freeze
produced the committed V1 API, hash `6a6258db03acac4a2fa4bd1abecbcf528d7348ea`; it has
no VINTF or vendor stability declaration. The native
`libboundarylab_platform_client` facade deliberately compiles with platform Binder APIs.
It maps security denial to `DENIED` and transport loss to `LOST_OR_UNKNOWN`, does no replay,
and preserves epoch/session handles. Calls are synchronous; an eventual UI consumer must
call from its worker executor. This CLI facade does not claim the application SDK's callback
and five-second observation-timeout contract.

The service authenticates **exact transport UID 2000**, before generated parcel decoding,
and service discovery/calls additionally require the dedicated probe SELinux domain.
UID 0 is not authorized. This is a narrowly scoped userdebug test principal, not a production
app signing/permission scheme. The denied init probe uses UID 9999 (Android `AID_NOBODY`) in a separate `boundarylab_denied`
domain with narrow find/call permission, so its explicit `DENIED` demonstrates the independent UID gate. Untrusted apps
have no service find/call rule. A production caller scheme needs separate design and review.

File operation returns `UNSUPPORTED`; no FD, byte-array, path or URI exists in the AIDL.
Any raw transaction containing an FD, or exceeding the 4096-byte complete transaction bound,
is rejected before generated argument decoding. `raw_fd_rejected` sends only an existing
stdout descriptor, never an attachment. It records either kernel permission denial of
the transfer or service `INVALID`; neither path succeeds or reads an attachment. This wire bound is stricter than a payload-only
metadata allowance and leaves all valid generated requests ample room. Responses have fixed
fields bounded well below 16,384 bytes. Random UUID generation fails closed if secure random
bytes are unavailable. Native engine crypto is AOSP BoringSSL (`libcrypto`); the host-only
sanitizer test uses the installed OpenSSL through pkg-config.

Alternatives considered: embedding the Kotlin broker would violate the native-service
acceptance; adding a system_server service would widen the process blast radius and violate
P03. Neither is selected. Porting policy adds maintenance cost; shared golden vectors and
matching edge tests guard drift, but do not prove the implementations equivalent in all cases.

## Source/build integration (operator commands, not execution receipts)

Host provisioning, dependencies, KVM/Cuttlefish installation and source sync are owned by
root's `scripts/aosp-host-bootstrap.sh`. No huge download should happen on the Mac. The
AOSP source already supplies libbinder, libutils, liblog and BoringSSL; no additional external
library or generated binary is copied into the image.

Record the baseline manifest, build/boot logs, fingerprint and Enforcing state before this step.
Then, from BoundaryLab (replace paths with the authorized host locations):

```sh
python3 aosp/scripts/stage-overlay.py /srv/aosp --baseline-receipt /srv/evidence/baseline-boot.json
```

The staging tool copies only source into `vendor/boundarylab`, appends the product include
and BoardConfig policy include, and records before/after hashes. It refuses an existing
integration; inspect an interrupted attempt before retrying. `SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS`
is set from BoardConfig, not through product inheritance.

The checkout already contains frozen V1. The update/freeze commands below record the
first integration sequence; future version evolution remains P06. The actual build used
the same pinned AOSP environment and existing output directory:

```sh
source build/envsetup.sh
lunch aosp_cf_x86_64_only_phone-bp4a-userdebug
m boundarylab-platform-update-api
m boundarylab-platform-freeze-api
m -j12 boundarylab_service boundarylab_probe boundarylab_denied_probe boundarylab_engine_test sepolicy-analyze
m -j12
```

Retain generated `vendor/boundarylab/aidl_api/`, the Soong-updated `Android.bp`, the exact
frozen hash/version, full build exit/log and final manifest. Copy that real API freeze back
into this source package via the root writer. **Do not manufacture `.hash` files or mark the
API frozen before these commands succeed.** The reference to `boundarylab-platform-V1-cpp`
is intentional: after the first freeze, both service and facade link V1. AIDL evolution and
an old compiled consumer remain separate P06 work.

Stop the baseline Cuttlefish instance and boot the rebuilt image using the matching host
package/output. Preserve the baseline evidence before reusing disk artifacts. Do not `adb
install` the app broker or hot-push just a daemon to claim custom image integration. The
custom product sets `ro.boundarylab.profile=generated-v1`; that marker is not itself build proof.

## Minimal verification and receipts

On the developer host:

```sh
./aosp/scripts/test-host.sh
```

The script builds with `-Wall -Wextra -Werror` plus address/undefined sanitizers and records
environment, build, sanitizer output and JSONL checks alongside execution. Its ignored native
binary is not a deliverable. These checks cover six digest vectors including signed seed
wrapping and 128 MiB, malformed/unsupported requests, UID/session/epoch authorization,
dedup/conflict, admission caps, queued cancel/deadline zero work, retention caps/TTL, and
barrier-controlled running cancellation/deadline versus success. `beforeChunk` is an injected
engine-test hook; the daemon never supplies it and there is no test-control Binder API.

On the authorized custom Cuttlefish image, with adb and the root QA collector available:

```sh
python3 aosp/scripts/device-smoke.py --serial localhost:6520 --out /srv/evidence/aosp-smoke --sepolicy-analyze /srv/aosp/out/host/linux-x86/bin/sepolicy-analyze
```

Use the actual serial from the selected instance. The script refuses an existing output
directory and writes each command's argv/timestamps/exit/stdout/stderr and SHA before parsing.
It checks the custom marker and Enforcing state, unroots adb for the real UID 2000 probe,
runs Binder vectors/negative/FD/bounds/cancel/deadline/session checks, captures each client context, exercises abrupt client
death, then uses root only for the UID-nobody init probe and real service kill/restart. It
runs the native deterministic policy corpus from the image, records service registration/PIDs,
and measures three successive init start intervals from `/proc/PID/stat`. It reconnects as
UID 2000, requires old handles to fail `STALE_EPOCH`, requires resource counters back at zero,
pulls the loaded policy and checks all three domains against its permissive list,
and rechecks Enforcing. It never sets permissive mode or installs an ordinary Android app.

Expected positive evidence is JSONL `passed:true` from the **actual** native probe, paired
with complete raw command receipts. A failed/timeout command remains failed. Exit zero
alone never closes P01–P09. The device corpus depends on the image installing the native
cc_test at `/data/nativetest64/boundarylab_engine_test/boundarylab_engine_test`; if the selected
build doesn't install it, root must explicitly push the source-built test executable and
record its artifact hash/path, not silently skip it.

Deterministic queue/outstanding/running-race tests execute the same native engine on the
image; the production Binder surface intentionally exposes no scheduler-freeze control.
The live Binder cancel check records CANCELLED or a prior SUCCEEDED and verifies that
terminal state remains immutable; a prior success is not evidence that cancellation won. The
deadline check requires an actual DEADLINE_EXCEEDED and retains a failure if completion wins.
No successful race outcome is invented.
Minimum smoke omits P06 old-consumer evolution, P07 paired performance, P08 two-hour soak,
and P09 fresh-source reproduction. Those original acceptance items remain open.

## Rollback and primary references

Rollback: stop the custom Cuttlefish instance; restore the baseline source revisions for the
two modified Cuttlefish makefiles and remove only `vendor/boundarylab`; rebuild/boot baseline
from the recorded manifest. Preserve receipts first. Do not touch unrelated changes or cloud
disks. Root controls all resource deletion under the total $50 cap.

- [AIDL backends and structured types](https://source.android.com/docs/core/architecture/aidl/aidl-backends)
- [Stable AIDL version management](https://source.android.com/docs/core/architecture/aidl/stable-aidl)
- [Pinned Cuttlefish product](https://android.googlesource.com/device/google/cuttlefish/+/refs/tags/android-16.0.0_r4/vsoc_x86_64_only/phone/aosp_cf.mk)
- [Pinned init service lifecycle reference](https://android.googlesource.com/platform/system/core/+/refs/tags/android-16.0.0_r4/init/README.md)
- [SELinux customization](https://source.android.com/docs/security/features/selinux/customize)
