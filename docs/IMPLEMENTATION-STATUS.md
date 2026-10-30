# Implementation evidence map

Updated 2026-10-30. This is the current evidence map for the original [22 requirement targets](REQUIREMENTS.md). Source was first published on `build/fieldpack-native-20261030` at `adc83a1`; final amendments and hosted verification are tracked in [PR #58](https://github.com/oudaykhaled/boundarylab/pull/58). This is an engineering prototype, with AI-authored code and AI reviews disclosed. It is not a production release or proof of every career qualification.

| Requirement | Current evidence | Remaining acceptance |
|---|---|---|
| R01 Middleware direction | [Astra decisions](decisions/BUILD-DECISIONS.md), shared bounded core, concrete Binder/JVM adapters and [execution board](../planning/BUILD-BOARD.md) | Explain personal decisions and tradeoffs in interview; company-wide architecture history is separate |
| R02 Multiple application ecosystems | Kotlin native and authored Flutter plugin both execute through the separately installed broker; real SAF exports match; final SDK native9/broker19/Flutter3 cases pass | Full shared client error/lifecycle corpus and broader device support remain scoped |
| R03 Phone/PC compatibility | Physical API36 Android execution, macOS separate JVM host/CLI and shared manifest/digest corpus; Linux/macOS hosted host checks pass | Full generated/lifecycle parity through both actual transports; no phone/PC synchronization claim |
| R04 Performance | [Benchmark harness and preregistered design](../benchmarks/README.md), 9 analysis-tool tests | Execute paired campaign and retain traces, uncertainty and negative outcomes |
| R05 Memory | Bounded core admission/retention; source/FD ownership tests, actual Android 100-FD cycle probe | Controlled PSS/RSS and churn campaign; logical caps do not measure JVM/OS overhead |
| R06 Startup | Functional [lazy-bind observation](evidence/build/android-default-launch-no-work.json): default client launches do not start broker work | Controlled cold/resident launch and readiness measurements; no timing improvement claimed |
| R07 Reliability | Deterministic core races, separate desktop process kill/cleanup, installed Android lifecycle and Flutter disposal tests; actual broker-death defect [fixed and verified](case-studies/broker-loss.md) | Full fault-ordering/soak campaign; no durable recovery or fleet SLO |
| R08 Scale | Fixed sessions, outstanding jobs, worker, queue and retention; deterministic quota/fairness tests | Arrival-rate/saturation curve and resource campaign |
| R09 Implementation and commercial relevance | Native/Flutter FieldPack export, actual separate-host recipient CLI, [desktop walkthrough](../desktop/README.md), isolated fresh-checkout desktop consumer and five hosted lanes passed | Fresh Android-consumer/release rehearsal and remaining acceptance; genuine commercial shipment requires separate history |
| R10 Architecture standards | Protocol/error/ownership/version contracts and source conformance fixtures | Compiled old-consumer compatibility fixture and version-upgrade rehearsal |
| R11 Guidelines and quality mechanisms | Pinned five-lane hosted CI passes, executable shared corpus, [QA charter](evidence/build/QA-CHARTER.md), security/reliability review with failing receipts retained | Deliberate regression/secrets gate proof, coverage enforcement and CI history; AI review is not human review |
| R12 Technology assessment | Dated tradeoffs for Binder, Flutter, rooted JVM/POSIX and optional AOSP in [decisions](decisions/BUILD-DECISIONS.md) | Executed dependency/platform upgrade rehearsal |
| R13 Qualification/equivalent practice | Project can demonstrate current practice | Separately supportable credentials or practical employment history |
| R14 Android framework and system software | Real bound-service Binder IPC, Android SDK/PFD/identity/lifecycle integration | Conditional built/booted AOSP system service and enforcing tests; app middleware does not establish OS-image changes |
| R15 Cross-platform framework practice | Authored typed Flutter facade/native channel uses real SDK/Binder; 11 app,2 facade and3 installed-engine tests, linked to corrected SDK | Broader installed cancel/loss/recreation/provider runtime cases |
| R16 OS implementation principles | Observable process, owner/session, descriptor, thread and loss boundaries; rooted POSIX helper | Booted AOSP registration/supervision/isolation proof; no kernel implementation claim |
| R17 Difficult problem resolution | Preserved failures, competing explanations, minimal correction and protected physical regressions in the [broker-loss case study](case-studies/broker-loss.md) | Final case-study/dependency review; no manufactured customer incident or speedup |
| R18 Communication and cross-team work | Contracts, ADRs, walkthroughs and explicit agent/team ownership | Genuine human collaboration requires attributable career or external evidence |
| R19 Preferred OS exposure | Executed Android and macOS profiles plus passing Linux hosted host checks, with explicit limitations | Optional AOSP image and wider runtime support; no iOS/Fuchsia/ChromeOS implementation claimed |
| R20 External ecosystem partners | Open-source repository and authored integrations | Actual attributable partner/upstream collaboration; none is invented |
| R21 Sustained European work | No project artifact establishes tenure | Separate private professional history |
| R22 International collaboration | English public documentation supports future participation | Actual consented cross-border technical collaboration |

## Reproduce and inspect

[Root quickstart](../README.md), [desktop installed smoke](../conformance/desktop/installed-smoke.json), [Android broker runtime receipt](evidence/build/android-broker-instrumentation-post-loss-r2.json), [Flutter installed-engine receipt](evidence/build/android-flutter-instrumentation-post-loss-r2.json), [real Flutter SAF/export receipt](evidence/build/android-flutter-saf-final.json), [actual native manifest](evidence/build/native-exported.fieldpack.json), [actual Flutter manifest](evidence/build/flutter-exported.fieldpack.json), and [visual implementation review](../design/IMPLEMENTATION-REVIEW.md).

The retained Android hardlink=2 case is **skipped**, because the test target denied creation of that fixture. Other descriptor tests do not turn that missing observation into a pass. Actual-device results apply to the recorded API36 target; minimum installation API30 is not a claim of testing every supported release. Performance tooling tests do not establish performance results.
