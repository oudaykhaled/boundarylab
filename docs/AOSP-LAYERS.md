# Native AOSP profile: layers and ownership

Status: source architecture at `735ebba`. The pinned Android 16 baseline is compiling;
API freeze, custom-image build, enforcing boot and native Binder execution remain
unverified. This diagram describes the implemented integration, not a runtime pass.

```mermaid
flowchart TB
    subgraph clients["New platform-built clients: system_ext user space"]
        authorized["boundarylab_probe<br/>UID 2000; boundarylab_probe domain"]
        denied["boundarylab_denied_probe<br/>UID 65534; boundarylab_denied domain"]
        facade["libboundarylab_platform_client<br/>Native C++ facade + structured CPP AIDL<br/>Intended V1; real freeze pending"]
        authorized --> facade
        denied --> facade
    end

    manager["Existing servicemanager process<br/>New BoundaryLab service-name / SELinux mapping"]
    binder["Existing kernel Binder driver<br/>No kernel or driver source changes"]
    initProc["Existing Android init process<br/>New service definition; restart_period 10"]

    subgraph daemon["New system_ext daemon: user space"]
        ingress["boundarylab_service<br/>UID 65534; boundarylab domain<br/>Transport UID gate before parcel decoding"]
        engine["Native generated-only SHA-256 engine<br/>One worker; 64 KiB work buffer<br/>Bounded sessions, queue and retained results"]
        ingress --> engine
    end

    facade -->|"Service lookup"| manager
    facade -->|"Synchronous Binder RPC"| binder
    binder --> ingress
    initProc -->|"Boot and bounded restart"| ingress
    policy["New system_ext SELinux types and rules<br/>Loaded-policy / Enforcing verification pending"]
    policy -.-> manager
    policy -.-> ingress
```

Both test-client domains deliberately have narrow discovery/call rules. The denied
fixture reaches the independent transport-UID check and must produce typed `DENIED`
through the facade. It is not a SELinux discovery-denial test. UID 2000 is a deliberate
userdebug test principal; this is not a production application authorization scheme.

The facade calls generated platform C++ Binder bindings. It is statically linked into
the deliberate native clients, not exposed through a new Java `framework.jar` API.
The stock-Android downloadable examples continue using the separate app broker.

| Layer | Change in this profile | Evidence needed |
|---|---|---|
| Cuttlefish product/build | Product package/property include and BoardConfig policy include | Overlay before/after hashes, pinned manifest and actual Soong build logs |
| Internal AIDL | Structured `boundarylab-platform` interface and generated CPP bindings; no VINTF declaration | Real update/freeze output, generated API snapshot/hash and committed build definition |
| Native clients | Platform-built facade and authorized/denied probes | Target compile/link and real typed Binder outcomes |
| Native service | Dedicated `/system_ext/bin/boundarylab_service`, generated-only engine | Image placement, automatic boot registration, PID/domain and operation receipts |
| Init and SELinux | New `.rc` definition, file/service contexts and narrow policy rules | Observed restart intervals, reviewed source policy and loaded enforcing policy |
| Java framework / `system_server` | No source extension or daemon placement here | Source integration inspection and observed dedicated process |
| Linux kernel / Binder driver | Existing target kernel and driver; no kernel implementation | Pinned target/build provenance; no custom-kernel claim |

The diagram alone does not satisfy build or runtime acceptance. The current
[P02 record](evidence/P02/README.md) distinguishes the earlier public Android 17
preflight from the pinned Android 16 source baseline. The custom profile still needs
its own source/API/image checksums, build exits, boot fingerprint and native checks.
The [minimum harness](../aosp/scripts/device-smoke.py) captures real identities,
selected vectors, denial, client death, three restart intervals, stale/fresh epochs
and quiescent resource recovery. It does not establish old-client evolution,
peak-resource bounds, performance, soak or fresh-source reproduction.

Source: [build modules](../aosp/Android.bp), [facade](../aosp/client/Client.cpp),
[daemon](../aosp/service/main.cpp), [init definitions](../aosp/service/boundarylab.rc)
and [policy](../aosp/sepolicy/private/boundarylab.te).
