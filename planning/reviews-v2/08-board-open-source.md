# BoundaryLab board revision: accountable delivery and useful adoption

Agent 08 · 30 October 2026 · Recommendation only; no GitHub mutations performed

## Scope and recommendation

Keep issues #1–#16 as implementation epics B01–B16. Amend their bodies to list implementation-sized children and aggregate acceptance, preserving their URLs and open state. Add a distinct planning/reference epic P00 so the current deliverables can complete without implying Binder, Flutter, macOS JVM, or AOSP implementation exists. Use one GitHub ProjectsV2 board, one accountable maintainer, and one root writer. A plan reviewed by ten AI agents is planning review; it is neither ten human contributors nor independent product reproduction.

FieldPack should lead the adopter README: create a SHA-256 manifest for authorized inspection attachments, transport the manifest and files separately, and verify the recipient's local directory. This is integrity checking, not authenticity, malware scanning, or automatic synchronization. The immediately runnable Python reference is a contract example. Its result cannot close the future native product issues.

For actual assignment, root should assign the repository maintainer account **@oudaykhaled** to planning/governance items and implementation epics as the accountable maintainer, with `owner_role` specifying the future executor specialty. Verify the account is assignable and read assignments back; do not leave the current placeholder “implementation owner to be confirmed” as the only ownership. This names accountability, not an assertion that the human has committed to every implementation estimate. Do not assign issues to fictitious agent accounts. Agent roles belong in review provenance. Never grant repository access or invite collaborators as part of assigning existing work.

## ProjectsV2 model

Projects supports custom fields and multiple filtered/grouped views [S1]. Avoid organization-only issue-field assumptions: this is a personal repository; use project custom fields and ordinary labels. Root should record the verified board URL only after creation and membership readback. A local JSON export is a useful recovery artifact, not proof that a board exists.

Recommended compact fields:

| Field | Type | Values / rule |
|---|---|---|
| Status | Single select | Planned, Ready, In progress, In review, Verified, Blocked |
| Priority | Single select | P0 contract/security prerequisite; P1 gate requirement; P2 optional refinement |
| Phase | Single select | Planning/reference, G1 Android, G2 evidence, G3 conditional platform |
| Kind | Single select | Epic, Implementation, Documentation, Verification, Planning |
| Gate | Single select | P0 reference, G1, G2, G3, None |
| Owner role | Text | Maintainer, Android engineer, Flutter engineer, JVM engineer, QA, technical writer, platform engineer |
| Estimate low / Estimate high | Two number fields | Child effort only; leave epic numbers empty to prevent double counting |
| Evidence URL | Text | Link to checked-in evidence index or review record, not a bare green claim |
| Blocker / next step | Text | One concise operational action; detailed reasoning in issue body |

Use native Assignees and linked parent/sub-issue relationships when supported by the available writer. Always retain `Parent: #N (Bxx)` and child task links in issue bodies as portable navigation. Stable keys and dependency links live in bodies plus the local backlog, not a large collection of redundant custom fields. Requirement IDs are body metadata; optional `scope:reference`, `scope:product`, `phase:g1`, etc. labels aid ordinary repository searches. Do not invent deadlines from the old 40/120/240-hour illustrations.

Views:

1. **Delivery board:** product children, grouped by Status, sorted by Priority; hide epics and reference tasks.
2. **Current planning/reference:** only P00 children; shows what this request actually delivered.
3. **Gate ledger:** table with epics and gates, grouped by Phase; status, assignee, evidence, blocker visible.
4. **Ready work:** child rows in Ready; dependencies must already satisfy their specific prerequisites.
5. **Review and blockers:** table of In review/Blocked items; expose exact review scope and next action.
6. **Estimate table:** implementation children only, low/high totals by Phase; epics excluded. No percentage-complete chart that treats documentation as native delivery.

Workflow rules: root alone writes board metadata during this task. Agents supply findings; root reconciles them. Disable an automatic “issue closed → Done/Verified” rule if it would bypass evidence evaluation. Closed-as-not-planned does not mean Verified. Implementation enters In review after an artifact exists; Verified requires expected evidence and explicit declared scope. Merged/tagged remain separate evidence facts, not alternate words for tested. Native runtime tasks require native runtime evidence. A cancelled ticket is closed with a reason and excluded from completed-delivery metrics. Epics become Verified only when all required children and aggregate criteria pass; optional/deferred children must be identified explicitly, never silently omitted.

Root should upsert by stable ticket key, not title; preserve existing issue numbers, reconcile repeated runs, and reread before creating missing children. Save key→issue URL→project-item mappings. After writing, fetch or inspect every membership/field, compare the count to the expected ledger, check unique keys and missing assignees, and record the result. Avoid a second competing board or polling watcher.

## Detailed ticket proposals

These keys are deliberately namespaced `OS-*` to avoid colliding with other agents' proposed children. Root may renumber after consolidation. Estimates are focused hours and exclude waiting for external humans. All start Planned; reference items can become Verified only after actual completion. `P00` below is a new epic titled “Planning revision and FieldPack reference example,” not a new product implementation gate.

### OS-01 — Reconcile existing epics with an acyclic child backlog

- **Parent / type / phase / priority:** P00 / Planning / Planning-reference / P0.
- **Owner role / assignee / effort:** Maintainer / @oudaykhaled / 2–4h.
- **Depends on:** none.
- **User value:** reviewers can find actionable work without duplicate estimates or conflicting scope.
- **Scope:** amend B01–B16 bodies, preserve issue URLs, integrate child definitions and review decisions; update canonical machine-readable backlog.
- **Acceptance:** every child has a unique key, parent, owner role, dependency IDs, low/high estimate, acceptance and evidence; every dependency resolves; graph is acyclic; epics show no summed child-plus-parent hours; B01–B16 remain planned until product evidence exists. Reference completion does not close B02/B07/B10.
- **Negative cases:** duplicate key, unknown dependency, cycle, missing estimate, or “Python verified ⇒ Binder verified” fails the consistency check. Keep superseded ticket history and explicit replacement references rather than silently deleting obligations.
- **Verification / evidence:** run a metadata validator; store expected/actual row counts, issue map, dependency graph, and decision ledger listing accepted/rejected agent findings with reasons.
- **Risks:** many overlapping agent suggestions can duplicate work; root merges by behavior rather than appending everything.
- **Requirements:** R01, R10, R11, R18.

### OS-02 — Create and verify the single delivery board

- **Parent / type / phase / priority:** P00 / Planning / Planning-reference / P0.
- **Owner role / assignee / effort:** Maintainer / @oudaykhaled / 1–3h.
- **Depends on:** OS-01 for final item set; field preparation may proceed independently.
- **User value / scope:** navigable board with truthful status, actual owner assignments, views, and a reproducible reconciliation receipt.
- **Acceptance:** one verified ProjectsV2 URL; all revised epics and children are members exactly once; proposed fields/views exist; all product items Planned; planning/reference items reflect observed results; @oudaykhaled assignment confirmed; sole-writer policy in project README. Readback matches the canonical key map and statuses.
- **Negative/race cases:** rerunning reconciliation creates no duplicates; a concurrent changed field is reread before overwriting; a closed-as-not-planned item is never marked Verified. Access failure leaves an explicit board-publication blocker and no claim of successful creation.
- **Verification / evidence:** board URL, item count, field export or UI verification record, missing-item/duplicate check, and timestamp. No credential or token screenshots.
- **Risks:** current token lacks Projects scope; root handles supported access independently. This report does not request or attempt auth changes.
- **Requirements:** R01, R11, R18.

### OS-03 — Publish a truthful reference quickstart and fixture walkthrough

- **Parent / type / phase / priority:** P00 / Documentation / Planning-reference / P1.
- **Owner role / assignee / effort:** Technical writer accountable to maintainer / @oudaykhaled / 2–4h.
- **Depends on:** reference utility and manifest contract ticket selected by root; dependency keys must be resolved during OS-01, not published as unresolved placeholders.
- **User value:** a new reader can execute FieldPack immediately and understand its limits.
- **Scope:** fresh-clone instructions for Python stdlib reference, project-owned fixtures, caller-specified paths, explicit schema version, manifest export and separate-directory verification. Keep proposed commands below illustrative until matched to actual implemented CLI.
- **Acceptance:** README labels “Runnable Python reference” separately from “Planned native middleware”; commands work from an empty temporary clone with documented Python version, no secrets, no local absolute paths, and no optional packages; fixture output succeeds; modifying one copied attachment produces nonzero mismatch; deleting another produces nonzero missing-file result; original source fixture remains intact. Arbitrary caller files stay out of checked-in evidence.
- **Negative/race cases:** paths with spaces work; output manifest is excluded from its own input set; symlink/path-traversal and malformed manifest errors are explained according to implemented policy; unrecognized schema version fails clearly; interrupted create must not leave a final-looking complete manifest. If reference cannot enforce a case, state the exact limitation instead of documenting imaginary behavior.
- **Verification / evidence:** clean-clone transcript, expected exit codes, fixture digest oracle, commit/hash, and passing meaningful tests. Sanitize local paths in public transcript.
- **Risks:** making a polished reference appear to implement the real service; trust-boundary banner must remain adjacent to commands.
- **Requirements:** R09, R10, R11, R18; illustrates R07 but does not satisfy native reliability.

### OS-04 — Specify the product adopter path and same-publisher signing boundary

- **Parent / type / phase / priority:** B10 / Documentation / G2 / P1.
- **Owner role / assignee / effort:** Technical writer plus Android engineer / @oudaykhaled / 3–5h.
- **Depends on:** B01 contract; drafts need not wait for B09 performance experiments.
- **User value:** readers know whether BoundaryLab fits their deployment before investing in integration.
- **Scope:** own-broker/own-clients adopter model, package artifacts, manifest contract, native/plugin integration guide, support matrix, lifecycle FAQ.
- **Acceptance:** guide states broker and clients must share the permitted publisher signing relationship; an unrelated APK cannot attach to the maintainer's sample broker; adopter builds/signs their own broker and consumers; sample signing is confined to disposable test material. Define authorized file selection, exported alias mapping, recipient directory, LOST/UNKNOWN and epoch reset, explicit resubmission, no sync/background durability promise.
- **Negative cases:** wrong-signature denial, old epoch handle, URI grant loss, unsupported/nonseekable source, and changed input all link to expected contract outcomes. A Flutter engine disposed mid-job is not documented as broker persistence.
- **Verification / evidence:** design review links, supported/unsupported table, sample configuration audited for secret-free reproduction. Execution is deferred to OS-05.
- **Risks:** public SDK language can imply arbitrary third-party broker access; correct that directly.
- **Requirements:** R02, R03, R09, R10, R14, R15, R18.

### OS-05 — Run a fresh external-directory consumer rehearsal

- **Parent / type / phase / priority:** B10 / Verification / G2 / P1.
- **Owner role / assignee / effort:** QA / @oudaykhaled / 4–7h.
- **Depends on:** OS-04, native broker/Kotlin package from B03, Flutter artifacts from B05, macOS JVM CLI from B07; no dependency on B09 benchmark results unless packaging changes.
- **User value:** validates the installation contract rather than relying on monorepo convenience.
- **Scope:** fresh checkout/build staging, separately constructed same-publisher consumer, native/Flutter file submissions, exported manifest and separately supplied PC files.
- **Acceptance:** no source-relative module shortcuts; only documented artifacts/configuration; user selects actual fixture files; both Android consumers create matching manifest content under defined normalization; macOS verifier succeeds then rejects changed/missing files. Author runs are labeled author-only. Credentials/private file contents never appear in receipts.
- **Negative/race cases:** wrong publisher denied; stale handle after broker kill rejected; reconnect does not claim restored execution; cancellation has no complete-looking manifest; unavailable recipient file fails; wrong protocol major rejects cleanly. Retry is explicit new submission, never silent resurrection.
- **Verification / evidence:** fresh-environment recipe, artifact checksums, toolchain/platform details, exact commands, expected/actual exit codes, logs, observed failures and fixes. Record package signing identity only in nonsecret terms.
- **Risks:** developer workstation caches can hide missing dependencies; isolate checkout and document caches used. This is not independent adoption unless another real person performs it.
- **Requirements:** R02, R03, R07, R09, R11, R14, R15, R18, R19.

### OS-06 — Define release identity, licenses, provenance and maintenance ownership

- **Parent / type / phase / priority:** B10 / Documentation / G2 / P1.
- **Owner role / assignee / effort:** Maintainer / @oudaykhaled / 3–5h.
- **Depends on:** OS-04 for declared surfaces; license inventory can start immediately.
- **User value:** consumers can tell what they are installing and which promises are supported.
- **Scope:** preserve actual repository license, dependency inventory, fixture provenance, API/schema version policy, release checklist, MAINTAINERS and best-effort support instructions.
- **Acceptance:** planning snapshot, runnable Python reference, Android preview, and G2 candidate have unambiguous identities; do not tag Python-only work as the native v0.1.0 release. Every fixture is project-owned or correctly attributed; no private input; checksums and source revision accompany artifacts; public API/protocol/schema changes have compatibility rules. @oudaykhaled listed as accountable maintainer only after root writes the actual project records; specialist owner roles remain roles, not invented people. No SLA or customer claim.
- **Negative cases:** absent build lane yields no passing badge; reference-only tests cannot turn native support green; unlicensed fixture blocks publication; embedded absolute path or signing secret fails artifact audit. Missing runtime evidence blocks native release eligibility.
- **Verification / evidence:** license scan/inventory, release-candidate checklist, documented support matrix, reproducibility receipt; publish only artifacts authorized by root's scope.
- **Risks:** this task authorizes board/planning/reference work, not automatic registry publication or product deployment.
- **Requirements:** R09, R10, R11, R18.

### OS-07 — Establish genuine reproduction and contribution evidence policy

- **Parent / type / phase / priority:** B16 / Documentation / G3 / P2.
- **Owner role / assignee / effort:** Maintainer / @oudaykhaled / 2–3h.
- **Depends on:** OS-06; policy writing does not depend on AOSP implementation.
- **User value:** an interested adopter knows how to report results without sharing private inspection files.
- **Scope:** issue templates for sanitized reproduction, defect reports, docs improvements; honest current participation ledger; no outbound contact in this task.
- **Acceptance:** ledger begins with observed participation only; AI-assisted review is labeled; author-run fresh install is distinct from outside reproduction; opt-in report requests version/platform/commands/outcome and synthetic repro, never customer attachments. No geography/nationality inference from usernames. Describe contribution rights and privacy before collecting a report.
- **Negative cases:** anonymous star, unanswered question, agent report, and maintainer second sample are not counted as adoption or partnership; absence of external response leaves adoption Not evidenced, not a fabricated success or blocked product gate.
- **Verification / evidence:** reviewed template, empty-or-truthful ledger, explicit R20/R22 gap statement; actual public report links added only if such reports later exist.
- **Risks:** external participation is not schedulable; do not make compulsory product gates depend on volunteers.
- **Requirements:** R18, R20, R22; policy enables evidence but proves neither R20 nor R22.

### OS-08 — Audit gate claims and demonstrate the real current workflow

- **Parent / type / phase / priority:** P00 / Verification / Planning-reference / P1.
- **Owner role / assignee / effort:** Maintainer / @oudaykhaled / 1–3h.
- **Depends on:** OS-01, OS-02, OS-03.
- **User value:** final handoff makes current usefulness and planned work immediately distinguishable.
- **Scope:** five-minute walkthrough: board, FieldPack reference success, modified/missing failure, native roadmap, gaps.
- **Acceptance:** show verified public board URL and counts; run actual user-specified fixture paths through reference; explain manual separate file transport; show reference test evidence; state Binder/Flutter/macOS JVM/AOSP remain planned. No native performance results, customer names, education, tenure or partnership claims inferred from the example. If board access remains blocked, report exact missing artifact instead of claiming the overall request complete.
- **Negative cases:** static illustration cannot substitute for reference execution; Python execution cannot close G1; accepted agent review cannot imply human independent reproduction.
- **Verification / evidence:** handoff checklist, command receipt, final links, distinction table: planning completed / reference checked / product planned / historical evidence separate.
- **Risks:** final communication compression can erase qualifications; preserve them next to the relevant claim.
- **Requirements:** R01, R09, R11, R18; R13/R21 explicitly outside project proof.

## Reconciliation of B01–B16

- B01 must incorporate FieldPack schema/path privacy and actual-file feasibility, not merely synthetic digest vectors.
- B02–B07 need their domain agents' detailed children for streaming, descriptor ownership, cancellation, broker loss, export and recipient verification. OS-03 is never a substitute for these children.
- B08 retains compatibility ownership; OS-05 consumes its documented contract but can start its fresh-install draft independently.
- B09 owns measurements. Remove artificial dependency “all adopter documentation waits for all performance measurement.” Preserve final G2 gate dependence on both completed measurement and consumer verification.
- B10 gains OS-04/05/06, replacing the old opaque 6–12h range with child totals after cross-agent deduplication; do not add both totals.
- B11's technology comparison can start after B01; the empirical case study waits for an actual investigated result. Split rather than serialize both behind release paperwork.
- B12–B15 remain conditional planned platform work. AOSP prerequisites must never be implied by a working Mac CLI.
- B16 gains OS-07's policy now, but final image reproduction and handoff remain dependent on B15 and actual platform evidence. Do not close B16 merely because CONTRIBUTING exists.

## Fresh consumer instructions: concrete contract for documentation

The reference quickstart must include these steps using the utility's **actual implemented** command names. Suggested shape, not an existing CLI claim:

1. Clone a named public revision into a clean directory and confirm the documented Python version.
2. Copy repository-owned FieldPack fixtures to a disposable input directory whose name contains a space.
3. Run `create --root "inspection input" --manifest manifest.json` with the manifest outside the input root. Explain exported relative names/aliases and no absolute paths.
4. Copy the fixture files to a separate recipient directory and deliberately transport the manifest separately; there is no automatic sync.
5. Run `verify --root "recipient files" --manifest manifest.json`; expect success.
6. Modify one recipient file and rerun; expect mismatch and nonzero exit. Restore, delete a file, rerun; expect missing and nonzero exit.
7. Show malformed/unsupported schema handling and point to the bounded reference's exact resource/path/mutation policy.
8. State that Android provider descriptors and broker epochs are future native integration concerns, even if the reference demonstrates matching manifest semantics.

Root should replace proposed commands with exact commands and check them verbatim before publishing. A “copy this command” example that does not execute fails OS-03. Users may point the command at their own authorized files, but evidence uses only fixtures and does not upload or retain private file names.

## Ticket review checklist and traceability

- Is the parent preserved, with child work replacing rather than duplicating its estimated scope?
- Does each dependency name a concrete deliverable and resolve without cycles? Can draft docs, platform inventory, and protocol work proceed independently?
- Can an executor distinguish success, error, cancellation race, epoch loss, resource limit and unsupported capability?
- Are output and partial-output semantics explicit, including no false complete manifest after failure?
- Are assumptions about signing, same-user desktop trust, URI permission and actual files visible?
- Does verification test behavior rather than echo implementation, and does evidence state source revision/environment/actual outcome?
- Is every native claim backed by native evidence rather than Python or a static frontend?
- Does every ticket have an actual accountable assignee plus specialist role without pretending ten humans are staffed?
- Are completion, review, merge, tag, deployment and independent reproduction separate facts?
- Are licenses, secrets, arbitrary private attachments, public filenames and provenance addressed?
- Are agents' proposed findings recorded as AI-assisted review, with root disposition and no invented human engagement?

Requirement roll-up: this lens directly strengthens R01/R09/R10/R11/R18; guides consumer evidence for R02/R03/R07/R14/R15/R19; requires accurate pointers to engineering artifacts for R04/R05/R06/R08/R12/R16/R17; explicitly preserves unproven R13/R20/R21/R22. Board organization alone proves none of the runtime requirements. No new estimate should retain the old 40/120/240-hour narrative as a guaranteed deadline after actual-file and privacy scope expansion.

## Sources and limits

- [S1 GitHub Projects overview](https://docs.github.com/en/issues/planning-and-tracking-with-projects/learning-about-projects/about-projects): fields, views and project organization; checked 30 October 2026.
- [S2 GitHub issue assignment](https://docs.github.com/en/issues/tracking-your-work-with-issues/using-issues/assigning-issues-and-pull-requests-to-other-github-users): eligible assignees; root must verify actual repository state rather than assuming assignment succeeded.
- [S3 GitHub Projects best practices](https://github.com/github/docs/blob/main/content/issues/planning-and-tracking-with-projects/learning-about-projects/best-practices-for-projects.md): configurable workflows; automatic closure mapping must respect evidence policy.
- Local baseline read: `planning/backlog.json`, `docs/EVIDENCE-POLICY.md`, `docs/ROADMAP.md`, and this task's BRIEF. No peer reports read, no external messages or mutations, and no implementation performed by this agent.

Main unresolved integration facts: exact reference CLI/schema names, consolidated sibling ticket keys, and verified Projects write capability. Root owns their resolution. This report intentionally makes no claim that the board, assignments, reference program, or native product already exists.
