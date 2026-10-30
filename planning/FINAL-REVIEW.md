> Archived review of the consolidated draft. Both findings were addressed before the final planning snapshot; see [disposition](DECISION.md#final-review-and-disposition). This is AI document review, not product verification.

# BoundaryLab consolidated-plan review

Reviewed 2026-10-30 by the request-scoped skeptical architecture reviewer. This is a second-phase review of the synthesis, separate from the frozen independent proposal. Scope: README, consolidated docs, top-level planning Markdown/JSON, security/contribution policy; archived proposal bodies were not substantively reviewed. No public files or board were modified.

**Verdict: no publication blockers found.** The repository is sufficiently concrete as a public project plan and consistently distinguishes planning from implementation. Two small contract clarifications should be resolved before B02/B03 implementation; correcting them in the plan now would make the implementation handoff stronger. Neither requires an approval process or additional agent treatment.

## Actionable findings

### Medium — queued cancellation is absent from the declared state graph

**File:** `docs/ARCHITECTURE.md:40` (related cancellation semantics at lines 44–46).

The only illustrated path is `ACCEPTED → RUNNING → SUCCEEDED | FAILED | CANCELLED`. The same contract offers cancellation, deadlines and session-close cancellation for admitted jobs, including queued ones. Read literally, the graph requires a queued job to enter RUNNING before cancellation, and does not define queued deadline expiry. Implementers could produce inconsistent adapters while both believe they conform.

**Fix:** add explicit `ACCEPTED → CANCELLED` and the selected queued-deadline terminal outcome; specify whether deadline expiry is `FAILED` with a stable reason or a distinct terminal state. State that no execution is required for a queued cancellation. Add a deterministic test where one long job occupies the worker and a second queued job is cancelled/expired without executing.

### Medium — dead-client session reclamation lacks a chosen detection mechanism

**File:** `docs/ARCHITECTURE.md:36` and `docs/ARCHITECTURE.md:46`; acceptance in `docs/VERIFICATION.md`, “Client dies while another remains.”

The proposed interface returns an opaque session and uses polling; the text promises that losing a session cancels work and reclaims records. Broker-death handling is well specified, but client-death detection is not. An owner UID and opaque token do not alone tell the Android broker which session died, particularly while another bound client keeps the broker alive. Platform and socket profiles also need an explicit session-to-lifetime association.

**Fix:** choose the smallest mechanism in B01: for Android, pass a client-owned Binder lifetime token when opening a session and register death handling; no progress callback API is required. For desktop, bind sessions to the connection and reclaim on disconnect/deadline. Document explicit close versus process-death behavior, and test that client A's death cancels only A's sessions while B continues. A bounded lease would also be valid if its expiry semantics and maximum reclamation delay are explicit.

## Checks and findings that passed

- **All R01–R22 are present exactly once** in `planning/requirements.json`, with corresponding readable coverage in `docs/REQUIREMENTS.md`. The plan does not manufacture education, commercial shipment, European tenure or partnership/collaboration evidence.
- **Timebox arithmetic reconciles:** B01–B05 = 40 nominal hours, B06–B11 = 60, B12–B16 = 100. Adding the two 20-hour reserves yields cumulative gates of 40/120/240 hours. Baseline ranges total 74–140 hours, platform range 70–150, matching the roadmap. Upper-bound overrun and elapsed build/wait time are disclosed.
- **No material platform contradiction:** the final plan consistently chooses macOS JVM host/CLI for the baseline PC port and separately requires suitable Linux resources for AOSP. Archived alternatives explicitly do not override the synthesis. The JDK/socket feasibility spike and custom platform API build prerequisite prevent an untested transport or hidden-API assumption being treated as proven.
- **OS-depth claims are controlled:** application Binder IPC, booted image-integrated user-space service and kernel work are distinguished. A source-only AOSP patch cannot close the platform gate; enforcing policy and executed tests are required.
- **Performance evidence is credible as a proposal:** comparison controls, physical-device conditions, uncertainty, rejection/timeout accounting and planned-versus-achieved targets are explicit. No benchmark execution or improvement is claimed.
- **Privacy inspection:** targeted scanning of Markdown/JSON found no private workstation paths, named private employer context, personal email pattern or obvious credential marker. The one public portfolio URL encountered is a public project reference, not leaked private research. This is a scoped textual inspection, not a claim of exhaustive secret-scanner coverage.
- **Deliverable is concrete:** ordered work packages, dependencies, acceptance assertions, limits, synthetic workload, protocol operations, failure semantics and five-minute reviewer route provide a usable implementation starting point. Toolchain pins and exact AOSP target appropriately remain kickoff feasibility decisions.
- **Status is truthful:** public documents say planning only; implementation items have empty evidence, planned status and false implemented/verified fields. Board planning completion is distinguished from product verification. No external adoption or independent human review is inferred from the ten AI proposals.

## Review limit

This review establishes document consistency and identifies design clarifications. It does not validate executable code, build an AOSP image, run native tests, certify security, or attest the runtime identity of the proposing models. Public publication can proceed as a plan; B01 should settle the two contract details before source implementation.
