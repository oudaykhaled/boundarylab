# AOSP resource audit — 2026-10-30

P01 remains blocked. The accessible workstation is Darwin 25.5.0 arm64, with 32 GiB RAM and 39,862,181,888 bytes (~37.1 GiB) free at the probe. `/dev/kvm` is absent. Docker CLI is installed but its daemon is unreachable. No authorized remote Linux resource has been identified. The connected Samsung is an app test target, not an allocated flashing target.

The current [AOSP requirements](https://source.android.com/docs/setup/start/requirements) specify a 64-bit x86 Linux host, glibc 2.17+, at least 400 GB free disk and 64 GB RAM. Modern AOSP development on macOS is unsupported. [Cuttlefish setup](https://source.android.com/docs/devices/cuttlefish/get-started) requires usable KVM and matching host/image artifacts. Both pages were checked on 2026-10-30.

Unblock condition: supply an existing authorized suitable Linux host and demonstrate KVM access. Then pin immutable source revisions and an exact compatible Cuttlefish target before spending the P02 baseline build timebox. No untested manifest/target is recorded as selected. Reserve the 8–16 active-hour P02 feasibility spike separately from unattended build elapsed time. Record real build duration, fingerprint, boot readiness and `getenforce` before changing any service source. Failed baseline work needs a new estimate; it does not close the gate.

Scope after that gate remains `generated-sha256-v1` only. No AOSP source was fetched or written, cloud resource purchased, external deployment created, or device flashed. Native Android app execution cannot satisfy this image gate.

Sanitized machine-readable observations are in [aosp-resource-audit.json](aosp-resource-audit.json). Local probes used Python `platform`, `shutil.disk_usage`, `sysctl -n hw.memsize`, existence of `/dev/kvm`, and `docker info`; device inventory used `adb devices` and non-identifying build properties. Private serials and host paths are omitted.
