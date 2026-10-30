# Local design prototype validation

Executed 2026-10-30 against the local source in this directory, before repository publication. Environment: macOS ARM64, Node v26.3.1, Playwright 1.64.1, cached Chromium headless. This is browser prototype evidence, not installed Android/Flutter parity, native accessibility, broker execution or independent human review.

Commands:

```sh
node --check BoundaryLab/design/prototype.js
python3 BoundaryLab/design/check-contrast.py
NODE_PATH=/tmp/boundarylab-design-check/node_modules node BoundaryLab/design/check-prototype.cjs
```

Playwright was installed in `/tmp/boundarylab-design-check`, outside the product repository. For reproduction, provide Playwright on Node's module path and install its matching Chromium if absent. Open `index.html` directly for manual exploration; no server is required.

Browser results: **PASS** for deselect-all blocking; selected files; full success; cancellation requested then terminal cancellation; failed and lost attempts blocking export; export cancel and destination failure; real browser sample download; downloaded sample sizes/digests matched all three repository fixture bytes; four session-history rows; dialog Escape/focus restoration; reload clears history; no local/session storage; RTL toggle; 320px reflow; 200% text at 320px including internal header/stage clipping checks; zero page errors.

Visual review found clipped header/stage controls at 200% text despite no document overflow. Added wrapping and a specific internal-overflow check; rerun passed. [Initial view](preview.png) and [320px with 200% text](reflow-320-text-200.png) are captured from the corrected source. At narrow widths the prototype controls stack above the product preview.

Measured sRGB contrast (WCAG relative luminance, all ≥4.5:1):

| Foreground / background | Ratio |
|---|---:|
| Text / white | 13.26:1 |
| Muted / white | 6.23:1 |
| Muted / background | 5.73:1 |
| White / navy | 14.55:1 |
| White / emerald | 6.51:1 |
| Emerald / success surface | 5.67:1 |
| Danger / danger surface | 6.36:1 |
| Warning / warning surface | 6.28:1 |
| Focus / white | 5.99:1 |

Remaining platform checks: real TalkBack/VoiceOver traversal, Arabic translation and installed RTL layout, font scaling on native/Flutter, destination-write integration, actual lifecycle/FD/process failure behavior and screenshot parity. These remain separate gates. The simulation does not measure accessibility compliance for those clients.
