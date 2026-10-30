# Primary sources

Reviewed 2026-10-30. Toolchain versions and API availability must be pinned again at implementation kickoff; these references do not certify a working build.

| Source | What it supports | Consequence for the plan |
|---|---|---|
| [Target LinkedIn posting](https://www.linkedin.com/jobs/view/4476561682/) | Role requirements; Sparagus listing; end client unnamed | Requirement IDs describe the actual posting; do not infer the end client. |
| [Android AIDL](https://developer.android.com/develop/background-work/services/aidl) | IPC interfaces, concurrency and compatibility responsibilities | Real remote process tests, bounded worker scheduling and version-skew fixtures. |
| [Android bound services](https://developer.android.com/develop/background-work/services/bound-services) | Binding and service lifetime | Recovery tests must tolerate service disappearance; no perpetual service assumption. |
| [AOSP architecture](https://source.android.com/docs/core/architecture) | Distinct application/framework/system components | Name the implementation layer and do not imply kernel work. |
| [AIDL backends](https://source.android.com/docs/core/architecture/aidl/aidl-backends) | App SDK versus internal platform interfaces | Ordinary app route and platform route have separate prerequisites and acceptance. |
| [Macrobenchmark](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview) | External benchmark module; release-like target; result JSON and traces | Retain machine-readable observations alongside reproducible traces. |
| [Flutter platform channels](https://docs.flutter.dev/platform-integration/platform-channels) | Dart/native messaging and threading mechanisms | Specify native lifecycle/thread ownership and test bridge failure semantics. |
| [Flutter desktop support](https://docs.flutter.dev/platform-integration/desktop) | Desktop hosts and plugin support | A phone/PC claim needs a real supported desktop adapter; mobile execution is insufficient. |
| [Java Unix-domain socket address](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/UnixDomainSocketAddress.html) | JDK local-socket address API | A real chosen-host socket spike still gates the PC profile. |
| [AOSP host requirements](https://source.android.com/docs/setup/start/requirements) | Supported build host and resource requirements | Mac PC tooling is separate from Linux platform-build readiness. |
| [Cuttlefish](https://source.android.com/docs/devices/cuttlefish) | Virtual platform-development target | Boot/test an actual custom image; do not infer physical-phone speed. |
| [Stable AIDL](https://source.android.com/docs/core/architecture/aidl/stable-aidl) | Structured, build-tracked interface evolution | Do not label ordinary Gradle AIDL as Stable AIDL. |
| [Perfetto documentation](https://perfetto.dev/docs/) | On-device tracing and analysis | Publish bounded, synthetic scenario traces and explain the bottleneck. |

Further platform-specific sources are linked from the architecture and research decision documents when used. No complete vacancy text is redistributed. Proposed budgets, protocol choices and schedules are project design decisions, not claims made by these sources.
