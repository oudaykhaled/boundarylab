# README visuals and evidence provenance

These assets present the **recorded 30 October 2026 Linux/KVM Android16 run**. They were produced offline from existing receipts; creating them did not provision a server, execute AOSP or rerun product tests. Original execution artifacts and screenshots are unchanged.

[Project README](../../../README.md) · [Original screenshot gallery](../../SHOWCASE.md) · [Actual custom proof](../../evidence/aosp/custom-20261030/README.md) · [Original Linux compiler log](../../evidence/aosp/custom-20261030/build/native-r5/build.log#L9-L10)

## What each visual means

| Asset | Classification | Purpose / source |
|---|---|---|
| [Hero](hero.svg) | Architecture illustration, not a capture | Linux/KVM host → Android guest → native Binder; separate receipt totals |
| [Server-to-guest pipeline](server-to-guest-pipeline.svg) | Explanatory diagram | Actual host inventory, source builds, guest checks, export and teardown; receipt hashes embedded in SVG metadata |
| [Component architecture](architecture.svg) | Explanatory diagram | Application/desktop profiles and separate generated-only native AOSP profile; [layer ownership](../../AOSP-LAYERS.md) |
| [Results](evidence-results.svg) | Receipt-derived chart | 34 operator checks /236 repeated harness observations /61 unique native assertions; overlapping layers, never summed |
| [Cost](cost.svg) | Receipt-derived estimate chart | US$6.1723 infrastructure; US$11.4413 with allowance/reserve; US$50 authorization cap, not a settled invoice |
| [Timeline](run-timeline.svg) | Recorded milestone chart | Actual UTC events; final/resumed builds use existing outputs, not a fresh-rebuild benchmark |
| [Animated replay](aosp-proof-replay.gif) | **Animated evidence replay — not a live recording** | Eight scenes,28seconds, actual literal excerpts or explicitly labeled selected JSON fields |
| [Static poster](aosp-proof-poster.png) | Typeset first replay scene | Same original Linux compiler excerpt; accessible without animation |

The displayed baseline and custom guest PNGs in the README are **original lock-screen captures**. Command receipts establish the service semantics. The physical Android/Flutter screenshots belong to the separate application profile; the native AOSP service has no graphical UI. [Original captures and captions](../../SHOWCASE.md).

The three BoundaryLab domains were checked nonpermissive and the guest was globally Enforcing. The61 native assertions ran separately under permissive `su`; this distinction appears in the affected graphics and README. Five failed runtime attempts remain available. Compatibility evolution, controlled performance, two-hour soak and fresh final-image reproduction remain open.

## Animated replay transcript

Every frame permanently says **“Animated evidence replay — not a live recording.”** Typesetting, line wrapping, selected fields and omitted/nonadjacent lines are identified. There are no invented prompts, new execution, reconstructed missing outputs or altered original screenshots. The static poster is shown by default in the README; the looping GIF is inside an expandable section so readers can stop it by collapsing that section.

| Scene | Retained source and displayed scope |
|---|---|
| 1 · Built on a real Linux cloud host | [build.log](../../../docs/evidence/aosp/custom-20261030/build/native-r5/build.log). Literal compiler excerpts · nonadjacent lines · final incremental native build |
| 2 · Android 16 booted the custom image | [run-result.json](../../../docs/evidence/aosp/custom-20261030/runtime/r6/run-result.json). Selected receipt fields · global Enforcing before and after · BoundaryLab profile generated-v1 |
| 3 · An authorized Binder job succeeded | [012-command.json](../../../docs/evidence/aosp/custom-20261030/runtime/r6/smoke/012-command.json). Literal decoded stdout excerpts · known 1-byte input SHA-256 · long lines wrapped |
| 4 · A different identity was denied | [057-command.json](../../../docs/evidence/aosp/custom-20261030/runtime/r6/smoke/057-command.json). Literal decoded stdout excerpts · typed DENIED response · omissions between lines |
| 5 · 61 unique native assertions passed | [native61.jsonl](../../../docs/evidence/aosp/custom-20261030/runtime/r6/native61.jsonl) · [actual-results.json](../../../docs/evidence/aosp/custom-20261030/actual-results.json). Native unit binary ran under permissive su; the three BoundaryLab domains were nonpermissive. |
| 6 · Cleanup and restart recovery were observed | [checks.json](../../../docs/evidence/aosp/custom-20261030/runtime/r6/smoke/checks.json). Selected same-generation FD fields · restart PIDs · this is not a performance or soak test |
| 7 · Evidence was exported and verified | [r6-local-verification.json](../../../docs/evidence/aosp/custom-20261030/archives/r6-local-verification.json). Selected export fields · payload hashes establish local consistency, not independent attestation |
| 8 · The paid server and disks were deleted | [teardown.json](../../../docs/evidence/aosp/teardown-20261030/teardown.json) · [cost-estimate.json](../../../docs/evidence/aosp/teardown-20261030/cost-estimate.json). Infrastructure estimate US$6.1723 · final invoice pending · compatibility/performance/soak remain open |

[Exact literal excerpts, one-based line locators, JSON pointers and source SHA-256 values](data/replay-scenes.json). The scene data is validated before rendering. [Full metrics, UTC timestamps and source hashes](data/run-summary.json) bind every chart value to a retained receipt. Hash matching establishes retained-byte integrity, not independent execution attestation.

## Reproduce or validate the media

The diagrams, dataset and SVG charts use Python3 standard library only. GIF rendering additionally needs **Pillow12.2.0** and local Helvetica/Menlo or Liberation/DejaVu fonts. Font rasterization can vary by host; the published asset bytes are identified in the [asset manifest](manifest.json) and [checksums](SHA256SUMS).

Run from the repository root:

```sh
python3 docs/media/readme/src/extract_run_summary.py --check
python3 docs/media/readme/src/charts.py --check
python3 docs/media/readme/src/proof_replay.py --validate-only
```

To regenerate assets, omit the validation flags for the three commands above, then run the independent SVG generators:

```sh
python3 docs/media/readme/src/hero.py
python3 docs/media/readme/src/architecture.py
python3 docs/media/readme/src/server_pipeline.py
```

These commands read retained local files and render documentation assets. They do not run the middleware, a device, a benchmark or a cloud command. Check new claims against their sources, update the asset manifest/checksums when changing media, and leave original evidence immutable. [Evidence policy](../../EVIDENCE-POLICY.md).

## Review method

Exactly18 specialist AI agents contributed scoped source audits, authored assets and reviews; root integrated and published, with Astra arbitrating critical editorial decisions. The work was checked for receipt accuracy, privacy, readability, static fallback, GitHub-compatible rendering and unchanged original evidence. This is scoped AI-assisted review; it is not independent external execution or a human review claim.
