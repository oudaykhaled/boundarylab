# Future five-minute reviewer demonstration

> Initial-plan document. [Plan v2](PLAN-V2.md) and the [FieldPack contract](FIELDPACK.md) supersede scope, estimates and real-file semantics. Native commands remain proposed.

Status: script for a future working artifact. Nothing in this document is a recorded pass or an instruction to claim unfinished work.

| Time | Demonstration | Evidence to open |
|---|---|---|
| 0:00–0:40 | Explain the developer problem and identify Dart/native, Binder and process boundaries | Architecture, exact commit, current support table |
| 0:40–1:30 | Native and Flutter clients submit the same synthetic vector to a separate broker and obtain the known digest | Distinct package/process receipts and conformance result |
| 1:30–2:15 | Deny an unauthorized client; saturate the bounded queue and show a second caller's progress | Permission test, admitted/rejected counts, resource counters |
| 2:15–3:00 | Kill broker during work; show honest unknown/lost state, new epoch and explicit resubmission | Death/rebind trace, client survival, one authoritative state |
| 3:00–3:35 | macOS CLI runs the same semantic fixture against its own host | Actual PC conformance manifest; explain same-user trust |
| 3:35–4:20 | Explain one trace and controlled baseline comparison, including a tradeoff or failed hypothesis | Raw observations, experiment definition, analysis |
| 4:20–5:00 | At G3, show booted AOSP service, enforcing policy and denied call; otherwise show its open gate | Image fingerprint, source/build/test receipts, requirement limits |

At G1, PC and rigorous performance/platform evidence remain absent; spend that time explaining the existing fault evidence and open gates. At G2, AOSP remains a plan. Label fallback recordings by date/commit and as recordings. Never present a prerecorded result as live execution.

Prepare answers to: Why separate processes? Why AIDL rather than Messenger? Where is Binder caller identity captured? What exactly survives broker death? How is a timeout different from cancellation? Can a caller starve others? What is the deduplication window? What changes when Flutter's engine dies? What costs are hidden by polling? How does macOS authorization differ? Which interface changes break old binaries? What evidence makes the AOSP profile system software? What has not been built or proven?

For any arrow on the architecture diagram, be ready to identify source location, identity, thread/process ownership, resource limit, failure behavior and test. Explain personal contribution separately from team or AI assistance. Keep qualification, commercial-delivery and collaboration examples truthful and permission-safe; the [requirements matrix](REQUIREMENTS.md) identifies where those separate examples are needed.
