# P03 — actual Soong API update and V1 freeze

The pinned Android 16 source host successfully executed the second API-update attempt and the first API-freeze attempt on **2026-10-30**. The first update failed and is retained. This evidence establishes actual API metadata generation/freezing; **native compilation, image assembly and custom-service runtime remain unverified by these receipts**.

| Phase | UTC start → end | Exit | Receipt and complete raw log |
| --- | --- | --- | --- |
| Update r1 — failed | 21:40:54 → 21:42:11 | **1** | [Result](update-api-r1/build-result.json), [log](update-api-r1/build.log) |
| Update r2 — passed | 21:45:48 → 21:47:41 | **0** | [Result](update-api-r2/build-result.json), [log](update-api-r2/build.log) |
| Freeze r1 — passed | 21:47:44 → 21:49:23 | **0** | [Result](freeze-api-r1/build-result.json), [log](freeze-api-r1/build.log) |

All three supervisor receipts record no stop reason and no surviving process group. Their full timestamps, log byte counts and SHA-256 values are retained. The source is `android-16.0.0_r4`, target `aosp_cf_x86_64_only_phone-bp4a-userdebug`, with relative `OUT_DIR=out` at `/srv/aosp/source/out`. Each phase reports the overlay present and `guest_boot_proven: false`. The [locked manifest](manifest.xml), [Cuttlefish integration diff](integration-cuttlefish.diff), and [source status after overlay](source-status-after-overlay.txt) preserve the recorded integration context. The status file reports the two tracked Cuttlefish changes; it is not a complete inventory of the separate overlay tree.

## Failed attempt and narrow correction

Update r1 failed while Soong generated its graph because `system/lib64/boundarylab-platform-V1-cpp.so` violated the generic-system artifact-path requirement. No API-update success is inferred from that failed attempt. The [one-line source correction](aidl-partition-fix.diff), commit `e50a8e24dc218790e80e84c80677addb16a4b033`, sets `system_ext_specific: true` on the `aidl_interface` so its generated C++ library follows the intended system_ext partition.

The rationale is grounded in the exact pinned implementation: the [AIDL backend creates the generated C++ module](https://android.googlesource.com/platform/system/tools/aidl/+/refs/tags/android-16.0.0_r4/build/aidl_interface_backends.go#150); [Soong module creation inherits common properties](https://android.googlesource.com/platform/build/soong/+/refs/tags/android-16.0.0_r4/android/hooks.go#150); and [system_ext placement reads the common System_ext_specific property](https://android.googlesource.com/platform/build/soong/+/refs/tags/android-16.0.0_r4/android/module.go#1624). The subsequent successful API update is the observed repair result. No artifact-path allowlist, enforcement bypass or partition-rule relaxation was introduced. This evidence does not independently establish final installed library placement, which requires the later image build.

## Actual generated files

The successful update log records the real structured AIDL dump/hash-generation commands. The freeze log records freezing `boundarylab-platform-V1`. Ten files copied from that export match the repository worktree and commit **`90a479d1e44f7367e693ae5e16f3a153fdda367a` byte for byte**:

- [Android.bp](../../../../aosp/Android.bp): the partition property plus actual Soong formatting, `frozen: true`, and version `1` with empty imports.
- [Frozen V1 API](../../../../aosp/aidl_api/boundarylab-platform/1): four AIDL files and the generated `.hash`.
- [Current API](../../../../aosp/aidl_api/boundarylab-platform/current): four AIDL files.

The actual V1 `.hash` contents are **`6a6258db03acac4a2fa4bd1abecbcf528d7348ea` followed by a newline**. This AIDL version hash is distinct from the SHA-256 used to inventory the `.hash` file itself (`5af33e491e67fce275b3ba9d42257d4074e175cf698c0f5e196c2e3d0afa5312`). Generated files are linked instead of duplicated here; [provenance](provenance.json) records each exact repository path, original/export hash, public/source hash and pinned commit. Future source edits must not be mistaken for the pinned bytes described by this receipt.

## Export integrity and publication scope

The supplied `api-freeze-export.tar.gz` is **48,246 bytes**, SHA-256 **`a920dd1d8a9648ead13271791593bb59c54e814e95deb251461c88cee39bf758`**. Curation independently checked that archive hash and all **19 inventory-listed payloads**. The original [inventory](inventory.json) is the additional twentieth archive member. Ten members are copied here byte for byte, and ten generated/build-definition members are linked to their exact committed source; no archive member is omitted without a source link. The [local export integrity receipt](local-export-integrity.json) is also retained unchanged.

All three raw logs are complete, uncompressed and unredacted. The only derived source artifact is the one-file partition diff, extracted without commit author/committer metadata. [Provenance](provenance.json) lists all original and public sizes/hashes and every transformation. [SHA256SUMS](SHA256SUMS) covers all files in this directory except itself.

Full archive text was scanned for private-key material, common credential/token formats, account emails, signed URL parameters, Mac home roots and physical-device identifiers; no publication-sensitive matches were found. Generic source/output paths remain. Pattern scanning is not a guarantee against every possible secret format.

Hashes and matching source bytes demonstrate local integrity and linkage of the supplied metadata. They **do not independently authenticate execution**, constitute signed attestation, or establish reproducibility. No native/image/runtime, vendor/VINTF, old-consumer compatibility, performance or soak result is claimed. No tests, builds or cloud actions were run during this curation.
