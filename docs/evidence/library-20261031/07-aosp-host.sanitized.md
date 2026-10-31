# AOSP reusable service-host slice — 2026-10-31

Implemented and architect-reviewed; locally verified for the pure production helper and existing Engine only. No Soong/Binder/image execution or paid build occurred. Historical receipts and frozen V1 were left untouched.

## Reusable behavior

- `libboundarylab_service` exposes `boundarylab::RunService(const ServiceConfig&)` in `aosp/service/ServiceHost.h`, reusing the original Engine and Binder/session/death lifecycle.
- `libboundarylab_service_config` contains the production `ServiceConfig`/`CallerPolicy` implementation used by service startup, the client name validator, and host tests.
- Config bounds: service name 1–127 bytes, ASCII `[A-Za-z0-9_.\-/]`; explicit caller list 1–16 distinct `uint32_t` UIDs each `<= INT32_MAX`. Invalid/empty config denies every UID. Policy privately snapshots the validated list.
- `RunService` rejects invalid config with exit 2 before Binder setup/Engine construction/registration; registration failure/pool exit return 1. The transaction gate uses real Binder caller UID before generated parcel unmarshalling; existing FD/4096-byte/oneway limits remain.
- Legacy `boundarylab_service` main supplies exactly `dev.boundarylab.platform.IBoundaryLab/default` plus `{2000}`. Existing no-arg `Client()` preserves that identity. `Client(explicitServiceName)` performs an exact lookup; invalid names remain disconnected without lookup/fallback.
- Binary name, active init/product/SELinux policy, workload operation and frozen V1 files remain unchanged. No workload plugin or general production application auth scheme is introduced.
- `aosp/examples/image-host` has a compilable adopter main and inert `*.example` Soong/product/init/SELinux templates. Empty caller list deliberately rejects startup; the image owner must configure real native caller UID(s) and domains. Nothing installs by default.

## Verification

Executed from `/workspace/BoundaryLab`:

```sh
./aosp/scripts/test-host.sh /workspace/outputs/boundarylab-library-20261031/aosp-host
clang++ -std=c++17 -Wall -Wextra -Werror -I aosp/service -fsyntax-only aosp/service/main.cpp aosp/examples/image-host/main.cpp
```

First command exit 0: 61 unchanged Engine checks plus 20 checks compiling actual production `ServiceConfig.cpp`, under AddressSanitizer and UndefinedBehaviorSanitizer; both sanitizer logs empty. Second command exit 0, no diagnostics: syntax-checks thin legacy/adopter entry points and public C++ API, with no Binder stubs or Android claim.

Host policy checks cover name format/bounds/NUL/non-ASCII, default/invalid deny-all, exact legacy/configured UID lists, no implicit root/shell, UID count/range/duplicates and immutable snapshots after source config mutation. They do not cover Binder registration/lookup/dispatch, generated Android compilation, policy compilation, SELinux or image execution. Those require the pinned AOSP integration environment. Earlier image evidence continues to apply only to its recorded source revision.

Receipts: `aosp-host/environment.txt`, `build.txt`, `checks.jsonl`, `sanitizer.txt`, `service-config-build.txt`, `service-config-checks.jsonl`, `service-config-sanitizer.txt`, `entrypoint-syntax.json`, `results.json`, `source-sha256.json` under this output directory. The source manifest records the final exact changed-source SHA-256 values.

Architect review found no blocking issue in extraction/default parity, the predecode UID gate, helper/client validation, Soong linkage intent or the inactive/default-deny integration example. Soong linkage remains source review only until actually built.

## Exact changed/new files

- `aosp/.gitignore`
- `aosp/Android.bp`
- `aosp/README.md`
- `aosp/client/Client.cpp`
- `aosp/client/Client.h`
- `aosp/scripts/test-host.sh`
- `aosp/service/main.cpp`
- `aosp/service/ServiceConfig.cpp`
- `aosp/service/ServiceConfig.h`
- `aosp/service/ServiceHost.cpp`
- `aosp/service/ServiceHost.h`
- `aosp/tests/service_config_test.cpp`
- `aosp/examples/image-host/Android.bp.example`
- `aosp/examples/image-host/README.md`
- `aosp/examples/image-host/example_generated_hash.rc.example`
- `aosp/examples/image-host/example_generated_hash.te.example`
- `aosp/examples/image-host/file_contexts.example`
- `aosp/examples/image-host/main.cpp`
- `aosp/examples/image-host/product.mk.example`
- `aosp/examples/image-host/service_contexts.example`
