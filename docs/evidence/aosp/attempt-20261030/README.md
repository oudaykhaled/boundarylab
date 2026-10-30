# Authorized AOSP attempt — 2026-10-30

Status at17:55UTC: **public prebuilt host feasibility passed; pinned Android16 source checkout completed; unchanged source baseline compiling**. This is not an AOSP source-build/custom-service result. Total infrastructure authorization isUS$50, with one VM, fixed deletion safeguards and early teardown on failure/completion.

| Check | Observed result | Evidence |
|---|---|---|
| Non-root Linux/KVM | API12 and real transient VM creation/close;16CPU/64GiB host | [host-kvm.json](host-kvm.json) |
| First public launch | Failed255: Ubuntu namespace denial; retained | [failure](first-launch-failure.json), [diagnosis](first-failure-and-controls.txt) |
| Narrow recovery | Exact executable AppArmor userns opt-in; global restrictions unchanged | [profile](scoped-host-profile.txt) |
| Graphics startup stall | GPU helper waited; frames socket backlog1, no consumer | [helper state](graphics-helper-state.txt) |
| Single causal correction | Required private frontend enabled; launch exited0 within180s | [final-launch.json](final-launch.json) |
| Actual prebuilt boot | boot_completed1, Enforcing, shellUID2000/context, CVDsandboxtrue | [stdout](prebuilt-boot.txt), [screenshot](prebuilt-boot.png) |
| Cloud safeguards | Applied native DELETE, independent deadline task, both disks auto-delete, no VM service account | [curated controls](cloud-controls.json) |

The public image is Android17 build16644040, downloaded with its exact matching host package from official public CI. The selected **source** baseline remains Android16 `android-16.0.0_r4`, manifest15128c9e27cfa599c48d294babd39286ee8f1426, target `aosp_cf_x86_64_only_phone-bp4a-userdebug`. Success on the public image only establishes host feasibility; P02 requires its own unchanged source build and enforcing boot before the overlay.

The first scoped relaunch's local SSH wrapper timed out at240seconds and did not write its intended exit receipt; a later operator note preserves this collection limitation. The contemporaneous process, socket and zero-byte guest logs are retained. Final launch and explicit boot evidence are separate.

Evidence was collected during execution. [Provenance](provenance.json) records original private receipt/log hashes and the small redactions in these public extracts. The screenshot contains only the owned clean virtual guest. Raw cloud identities, keys, transient download URLs and uncurated runtime archive remain private. Passing command exit is distinct from a semantic assertion; these boot facts were checked explicitly from stdout, not inferred from the collector's optional JSON probe field.

The existing hosted CI run for source-preparation commit5663baa [passed](https://github.com/oudaykhaled/boundarylab/actions/runs/41090093247); it covers the existing app/desktop profiles, not the new AOSP target. No manual rerun is needed for that unchanged source.

Collection classification correction: the first source-sync/progress commands were accidentally tagged `prebuilt-baseline` in immutable collector metadata. Their command labels/content are source preparation, not a boot result. Original receipts remain intact; future source build/probe commands use `source-baseline`. No test/download is repeated merely to change a metadata tag.

No completed pinned-source build/boot, custom service image, compatibility, performance, two-hour soak or independent reproduction result is claimed here yet. The original source build attempts and current progress are retained separately under [P02](../../P02/README.md).
