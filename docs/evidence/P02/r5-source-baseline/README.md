# r5 — unchanged Android 16 source baseline

The recorded incremental source build completed successfully at **2026-10-30 21:36:20 UTC**. Its matching Cuttlefish image booted Android 16 / API 36 with **18/18 checks passing**, and the boot runner verified cleanup at **21:37:50 UTC**. This directory curates those supplied execution receipts; no new build or device test was run during curation.

| Evidence | Recorded result |
| --- | --- |
| [Build supervisor](build-result.json) and [full Ninja log](build.log.gz) | Exit 0; no stop reason; no surviving build process group; no BoundaryLab overlay |
| [Locked project manifest](manifest.xml) and [source status](source-status-before.txt) | `android-16.0.0_r4`; manifest HEAD `15128c9e27cfa599c48d294babd39286ee8f1426`; clean source worktrees |
| [Image and host binary inventory](boot-preparation.json) | 25 size/SHA-256 records, including built images, source-built adb and exact crosvm ELF |
| [Boot checks](boot/checks.json), [result](boot/result.json), [command receipts](boot/commands.json) | Android 16, SDK 36, build ID `BP4A.251205.006`; fingerprint equals built system; profile marker absent; shell identity/domain and activity service recorded |
| [Effective CVD configuration](boot/effective-config.json) | Runtime sandbox and WebRTC enabled; source-built boot/super images and matching host crosvm paths |
| [Boot and cleanup aggregate](boot-run-result.json), [stop process receipt](source-stop-processes.json) | Boot passed and cleanup verified; no matching CVD processes remain |
| [Actual screenshot](boot/boot.png) | Android lock screen; original PNG retained without resizing or editing |
| [Pinned checkout tool inventory](toolchains.json) | 13 executable/version-command records, source selection configuration and executable SHA-256 values; scope explained below |

The exact fingerprint is `generic/aosp_cf_x86_64_only_phone/vsoc_x86_64_only:16/BP4A.251205.006/eng.aosp:userdebug/test-keys`. The target is `aosp_cf_x86_64_only_phone-bp4a-userdebug`. Output paths are `/srv/aosp/source/out/target/product/vsoc_x86_64_only` and `/srv/aosp/source/out/host/linux-x86`. Guest SELinux was **Enforcing**. The absence of `ro.boundarylab.profile` is expected for this unchanged baseline.

The `source-*-command.json` files retain launch, verification, stop and adb-stop command arguments, timestamps and exit status; their paired stdout/stderr logs are retained, including empty streams. `boot/commands.json` retains the actual adb commands and outputs. `boot-window.json` records the 600-second bound. Build provenance comprises the full log, pinned manifest and supervisor receipt; this archive does not contain a separate shell-trace receipt for every build subprocess.

## Provenance and publication coverage

The supplied private archive `baseline-final.tar.gz` is **2,659,393 bytes**, SHA-256 **`cf78a18d8a4eea1f11654187136aa2b03edcde2c826df7b6c8b512a2a0fe6688`**. Its 39 inventory-listed files were checked locally against both byte counts and SHA-256 values; its inventory is the additional 40th archive member. [Provenance](provenance.json) accounts for all 40 members, with original hashes and sizes, selected public hashes and sizes, transformations, and reasons for every omission. [The original export inventory](export-inventory.json) and [local integrity receipt](local-export-integrity.json) are retained byte for byte.

We selected **29 archive members**. The 11 omissions are the nested archive of the earlier prebuilt state and 10 internal stopped-runtime files (duplicate configuration, verbose logcat/kernel/launcher/auxiliary logs and modem state). These remain hash-inventoried in provenance; primary build, image, boot, configuration, command, process and cleanup evidence is included. The original archive itself and large image binaries are not duplicated here. Hash consistency links the supplied evidence; it is **not independent authenticity verification, signed attestation, or a fresh reproduction**.

`build.log.gz` is a lossless gzip of the **entire** raw Ninja log, not a head/tail excerpt. Decompression yields **3,937,178 bytes**, SHA-256 **`4d5fc02309b039e810f5f33038c060c064d0931e6662f92653d0012d47bc10f3`**. Compression used level 9 with mtime 0; no log bytes were edited. All other selected archive members and the local integrity receipt are byte-identical. [SHA256SUMS](SHA256SUMS) covers every public file except itself.

Selected text was scanned in full for private-key material, credential/token patterns, signed URL parameters, account email candidates, Mac home paths and physical-device identifiers, with credential-keyword review. No publication-sensitive matches were found. The log's 1,534 email-like matches (335 unique) are Android module/APEX/JAR names. Generic `aosp` paths/account, virtual CVD address `0.0.0.0:6520`, synthetic Cuttlefish identifiers and test-key fingerprint are retained; no text redaction was applied. This pattern review is not a guarantee against every possible secret format.

The PNG was visually reviewed during curation and shows only an Android virtual-device lock screen, with no account or personal content. Its SHA-256 is `e0afeb9a7c7cd1e98efc09c71d49e40c442ece7f481b2c4b8ed9d47d22ec6be5`. The earlier local integrity receipt's `screenshot_visual_review_pending: true` is preserved as recorded; visual review occurred afterward. The preparatory/build receipts likewise retain their then-correct `guest_boot_proven: false`; later boot receipts provide that evidence.

## Limits retained from the actual run

- **Incremental resume:** r5 reused the r4 outputs after the [required Trusty nsjail failure](../r4-nsjail-failure/README.md). It does not satisfy P09 fresh-clone/fresh-output reproduction.
- **Build sandbox history:** earlier compilation emitted `Build sandboxing disabled due to nsjail error`. Recovery used a separate exact-program userns compatibility profile for pinned nsjail, without an upstream source edit. Earlier unsandboxed compilation remains a limitation. The retained [crosvm profile](apparmor-profile-applied.txt) is an exact-ELF `flags=(unconfined)` userns compatibility profile, not a claim of full AppArmor confinement. Runtime sandbox=true and guest Enforcing do not retroactively prove build sandboxing.
- **Tool availability versus use:** the inventory includes four Clang binaries (20/21), Rust 1.88.0-dev, Java/javac 8/21/25, Ninja 1.9.0.git and nsjail. Twelve version commands returned 0. `nsjail --version` returned **255** because that option is unsupported; its error and usage are retained as metadata, not classified as a build/runtime failure. Source defaults reference Clang r563880c and Rust 1.88.0; the inventory alone does not establish that all 13 executables were selected or used.
- **Baseline only:** these receipts prove the recorded unchanged-source baseline build/boot/cleanup scope. They provide no BoundaryLab custom-service, generated workload, API-freeze, compatibility, performance or soak acceptance. No custom overlay or service runtime is claimed.
