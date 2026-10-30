# FieldPack reference implementation — independent AI review

Date: 2026-10-30. Reviewer: separate Astra agent; implementation and original tests authored by root. This is independent AI source review and locally executed reference-tool verification, not independent human adoption, native middleware validation or security certification. Only this report was edited by the reviewer.

## Verdict

**Scoped PASS after fixes: no unresolved P1/P2 finding in the reviewed regular-file reference.** Two initial contract discrepancies were corrected by root during this review and their source changes were confirmed. The intended trusted-directory-ancestry / untrusted-flat-child boundary is explicit and proportionate; no arbitrary provider, Windows or malicious same-user protection is claimed.

## Resolved P2: essential demo calls disappeared under Python optimization

**Location:** `examples/fieldpack/demo.py:21`, `:23`, `:28`, `:31`, `:36`.

Each `fieldpack.main(...)` call is embedded in an `assert`. Python optimization removes these expressions entirely. Reproduced with `python3 -O examples/fieldpack/demo.py`: creation and verification never run, and the demo fails at line 33 when reading the manifest that was never created. `PYTHONOPTIMIZE` causes the same class of failure. Normal execution succeeds, so this is a walkthrough robustness issue, not an observed normal-mode false PASS.

**Fix:** invoke `main` unconditionally through a helper that compares its returned code with the expected code and raises an explicit exception on mismatch. Run the demonstration once normally and once with `-O`; both must execute all five operations and report the expected outcomes. No product/native claim should be added.

## Initial findings resolved during review

1. **Compact writer contract:** initial `fieldpack.py` used `indent=2`, inconsistent with the normative compact writer format. Current `create()` line 145 uses `separators=(",", ":")` and trailing LF, matching the contract.
2. **Alias replacement detection:** initial `hash_file()` checked only the opened descriptor. Current lines 122–124 re-stat the alias relative to the pinned root without following symlinks and compare identity. A reviewer-created deterministic probe replaced the alias immediately after the final descriptor metadata observation; the patched implementation rejected it as `INPUT_CHANGED`. This remains best-effort detection, not an atomic input snapshot. Current line 109 also explicitly seeks to offset zero, rejecting unsupported seek behavior.

Root was notified of all findings promptly. Live files were changing during review; the resolved findings above reflect the later re-read, not an assertion that the initial revision already satisfied them.

## Executed evidence

- Existing unittest suite: **11 tests passed** at the initial review run, including known digests, schema negatives, bounded chunk reads, link/FIFO rejection, mutation/error closure, no-overwrite output, interruption and exit codes.
- Normal `demo.py`: **passed** with actual owned text/CSV/PNG files; successful handoff, same-size changed bytes, missing image and unsupported manifest version produced the expected outcomes and exit codes.
- Optimized `demo.py`: **failed as described above**, providing a concrete regression target.
- Independent reviewer probes: reduced aggregate budget with two real files consumed exactly the admitted total; oversized recipient was rejected from metadata without reading its large body; an output-parent symlink resolving inside the input root was rejected; source bytes remained unchanged.
- Final-alias replacement probe against updated code: rejected `INPUT_CHANGED`.

These observations are local Python-reference evidence only. The original suite's root authorship remains disclosed; reviewer probes were independently designed and do not alter committed tests.

## Safety and contract assessment

**Bounded reads:** creation subtracts each completed entry from a shared remaining budget. Verification validates the sum of expected sizes first, then bounds each read by that entry's expected size. Growth detection can consume one extra sentinel byte per attempted file, so the total verification consumption is bounded by the manifest total plus at most 32 bytes; this is a finite detection overhead, not an unbounded aggregate-read flaw. Reads are capped at 64 KiB. No whole attachment is loaded into memory.

**Parser:** manifest input is capped before JSON decoding; duplicate keys, invalid UTF-8/BOM, nonfinite constants, unknown fields, booleans masquerading as integers, invalid aliases/digests, unsorted/duplicate entries and count/size limits are rejected. Public in-memory verification revalidates its schema. The CLI processes an explicitly capped file, so arbitrary Python caller allocations are outside its untrusted-input boundary.

**Filesystem:** the root is pinned; flat names disallow traversal and separators; selected children use no-follow, nonblocking opening and descriptor type/link validation. FIFO open cannot block before rejection. Hardlinked inputs are deliberately unsupported. Directory ancestry is explicitly operator-controlled; the documentation does not claim to prevent hostile ancestor substitution.

**Publication/input preservation:** output must resolve outside the input root. Same-parent temporary output is flushed, then hard-linked into place without overwriting an existing target. Existing files/symlinks are not replaced. Failed hashing never reaches publication. The input root is only read; mutation in tests/demo occurs exclusively in disposable copies. Complete output may legitimately remain if interruption occurs after publication, as the contract explains; no crash-durability guarantee is asserted.

**Claims:** README correctly distinguishes single-process Python reference from future Android Binder, Flutter, JVM host and AOSP work. Integrity/authenticity, provider support, manual transfer, non-atomic snapshots, controlled ancestors, extra-files scope and missing native evidence are stated. No credential, user data or external transfer was required for this review.

## Follow-up gate

Root replaced side-effect assertions with an unconditional expectation helper and added the final-alias replacement regression. Reviewer rerun: **12 tests passed; normal demo passed; optimized (`python3 -O`) demo passed**, with all five operations and expected outcomes executed. The original P2 is closed. Preserve planned status on native-product tickets; this scoped reference result proves none of their execution gates.
