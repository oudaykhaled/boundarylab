# BoundaryLab native AOSP profile

Status: implementation prepared; **no Android 16 Soong build, frozen API, policy compilation,
custom-image boot or Binder runtime result is claimed here**. Actual local host evidence is
under `evidence/local/`. This is the generated-only native platform integration in P03–P06,
not the app broker and not a `framework.jar` or `SystemServiceManager` extension.

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

Structured `boundarylab-platform` AIDL uses libbinder CPP. Its first V1 API must be frozen
by the pinned Soong tools; it has no VINTF or vendor stability declaration. The native
`libboundarylab_platform_client` facade deliberately compiles with platform Binder APIs.
It maps security denial to `DENIED` and transport loss to `LOST_OR_UNKNOWN`, does no replay,
and preserves epoch/session handles. Calls are synchronous; an eventual UI consumer must
call from its worker executor. This CLI facade does not claim the application SDK's callback
and five-second observation-timeout contract.

The service authenticates **exact transport UID 2000**, before generated parcel decoding,
and service discovery/calls additionally require the dedicated probe SELinux domain.
UID 0 is not authorized. This is a narrowly scoped userdebug test principal, not a production
app signing/permission scheme. The denied init probe uses UID 65534 in a separate `boundarylab_denied`
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

In the AOSP tree with the same pinned environment and existing output directory:

```sh
source build/envsetup.sh
lunch aosp_cf_x86_64_only_phone-bp4a-userdebug
m boundarylab-platform-update-api
m boundarylab-platform-freeze-api
m -j12 boundarylab_service boundarylab_probe boundarylab_engine_test
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
