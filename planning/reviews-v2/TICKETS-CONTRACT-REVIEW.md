# Final backlog contract review

Verdict: **publish after the small corrections below**. Reviewed the 40-leaf `tickets-draft.json`, published `docs/FIELDPACK.md`, and Python reference source. No native execution or independent rerun of the already reviewed reference tests was performed here. Root's 12-test and normal/optimized demo receipts remain the execution authority.

## Verified structure and scope

- Parsed all 40 leaves and traversed every dependency: all referenced keys exist, graph is acyclic, and every epic B01–B17 has at least one child.
- Numeric global caps agree with the normative contract: 32 entries; 128 MiB per file and pack; 64 KiB chunks; 16 KiB manifest/frame; 4 KiB descriptor; 16 sessions; four outstanding/session; 16 queued plus one worker; 64 terminal entries/60 seconds/1 MiB accounted retention. Metadata accounting is not misrepresented as total heap.
- AOSP P01–P09 consistently remain generated-only. No file/URI/descriptor privileged ingestion should be added.
- Blocked I/O retains the worker slot, first terminal commit wins, no forced thread interruption or extra worker pool is promised, and loss is not restoration. Mutation checks are explicitly best effort, not an atomic snapshot.
- All draft statuses remain planned. REF01 alone can become verified after root links its actual receipts; this must not complete B17 or native epics. Reference code does implement actual caller-supplied files and bounded reads, not a fixture-only shortcut.

## Essential corrections before publication

1. **FP01 exit statuses conflict with the normative/reference behavior.** Replace `1 mismatch/missing,2 invalid/inaccessible` with: `0 all entries match; 1 valid verification completed with any non-MATCH entry, including UNREADABLE_OR_UNSUPPORTED; 2 invalid arguments/manifest, invalid or inaccessible root, failed creation/publication or operation-level error; interruption may return130.` Inaccessible individual attachments are entry results, not automatically exit2. Reference `main` already returns 1 for all non-MATCH entries.

2. **FP05 names an undefined `UNREADABLE` outcome.** Use exact `UNREADABLE_OR_UNSUPPORTED`. Its acceptance must explicitly adopt `docs/FIELDPACK.md`'s single-link (`nlink == 1`), seekability and no-follow requirements and fail the file-capability gate if the JDK/filesystem cannot establish them. Retaining disclosed same-user races does not permit silently dropping mandatory input checks. JNI remains separately reviewed additional work, not an implicit prerequisite or fictional existing capability. Add hardlink and nonseekable negatives to FP05 verification. FP02 should likewise explicitly mention rejecting `nlink != 1`, already required by the normative Android profile.

3. **V01's file capability matrix lacks the implementations it claims to verify.** It currently depends only on A02/F01/D01, all generated-profile foundations. Add FP02 and FP05 to V01 dependencies if its acceptance continues to cover real file-capability evolution across Android/macOS. This introduces no cycle: FP05 → D02 → A02/D01. Alternative: narrow V01 to generated ABI and absent-file rejection, then put the file-enabled compatibility matrix in FP06; do not leave it implied and unowned. Adding dependencies is the smaller correction.

4. **Release readiness can bypass the security/privacy audit.** Q03 has no path into L01/L02, so release rehearsal/final handoff can reach completion while its audit remains planned. Add Q03 to L01 prerequisites. Since Q03 currently depends on D01 rather than the actual desktop file capability, add FP05 to Q03 if its audit is meant to cover desktop file access; this is necessary for the advertised all-profile release review. No cycle results. Do not require Q03 to block early native development or benchmark preparation.

5. **FP01's filename policy is still worded as unreconciled.** Replace vague `unsafe portable reserved names` with a direct normative-contract reference, including trailing-dot rejection and `con/prn/aux/nul/com1..9/lpt1..9` basename rejection. The root reference already implements this; remove draft note/risk language that implies the reference's policy is still undecided. Preserve the alias/original-name distinction: public explicit reference aliases may equal local fixture filenames; the prohibited disclosure is private original names different from assigned aliases, paths and URIs.

## Small dependency clarifications, no extra scope needed

- P04 uses an authorized/denied platform client before P05's published facade exists. State that P04 owns minimal platform test clients using the generated interface; P05 delivers the deliberate user-facing facade/client. Do not add a reverse P05 dependency and create a cycle.
- C02/FP01 contract acceptance can finish with documents and frozen expected vectors. Their wording about running native vectors “once available” is downstream validation, not an implicit requirement to finish C03/FP02 before those prerequisite contracts can complete.
- C01 is a combined feasibility ticket, so Android work waits for the macOS spike. This is conservative but not a correctness blocker; no need to split the backlog immediately. Native and Flutter FieldPack implementations are otherwise appropriately parallel after shared SDK/FD foundations.

## Reference observations

Source review confirms strict duplicate-key/type/schema checking, fixed cap constants, sorted entries, DOS-reserved/trailing-dot rules, descriptor-relative no-follow input opens, regular/single-link checks, bounded streaming, metadata comparison and output no-overwrite via hard-link publication. Creation rejects output within the root. Verification returns explicit per-alias outcomes and does not write inputs. The manifest loader is bounded. No source path is copied into the manifest.

The reference's conventional interrupt status130 is compatible with the normative exception. Publication can leave a complete output if interrupted after atomic linking; that is not a partial manifest and the contract explicitly permits it. A missing alias discovered on final revalidation may appear as MISSING rather than generic mutation; this does not create false success and is not a publication blocker. No additional product scope or broad test expansion is required by this review.

## Traceability and honesty

The engineering leaves cover the implementable R01–R12/R14–R19 evidence. R13/R20/R21/R22 remain external-career evidence; R09 commercial shipping and R18 actual cross-team practice are not manufactured by author/AI review. Proposed verification commands are labeled as proposed. Root should retain that labeling while only marking the delivered reference evidence verified.

## Coordinator resolution

All five essential corrections and the P04/contract-completion clarifications were applied to planning/tickets-v2.json before publication. Q03 includes FP05; L01 includes Q03; V01 includes FP02/FP05. REF01 alone is verified from execution receipts. The final graph is validated again after changes.
