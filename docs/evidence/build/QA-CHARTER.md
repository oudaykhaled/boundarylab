# BoundaryLab QA charter — 2026-10-30

Owner: Staff QA Lead agent (`quality_head`); architecture decisions: Astra Chief; product acceptance and board/git state: root. Scope is this standalone public prototype, with core JVM, Android Binder SDK/broker, native and Flutter clients, desktop Unix-socket host and CLI. AOSP is a separate conditional gate. No paid production SLO is asserted.

| Stage | Required evidence | Owner |
|---|---|---|
| PR | JVM compilation/unit tests; strict shared contract corpus; Android compile/lint; Flutter analyze/test; sanitized artifact inspection | Build engineer + cross-platform QA |
| Merge candidate | Real desktop two-process and file-negative tests; Android boundary/lifecycle instrumentation on allocated target | Desktop owner + middleware owner, QA executes |
| Release candidate | Installed native/Flutter broker journeys; manual file handoff; clean-consumer rehearsal; compatibility/fault corpus; preregistered performance and security evidence | QA head reconciles scoped evidence; root decides |

Tests touching changed behavior accompany implementation. Tests use owned synthetic fixtures and disposable directories; Android actions stay within BoundaryLab packages. The single allocated physical test target is serialized by QA. Never clear unrelated application state or expose serials/private attachments. Failed tests block the affected gate; skipped/unavailable cases remain incomplete. JUnit XML or equivalent JSON reports are required beside human-readable evidence. A passing build is not an installed runtime result.

Target test mix is unit/integration/contract/e2e **70/20/8/2**, assessed by cases rather than file count. Current ratio/coverage are unmeasured until suites exist; no numeric coverage claim is made. A 70% core line-coverage target is planned, not an active enforced gate until measurement is wired. Safety boundary negatives take precedence over gaming a ratio. CI wiring and local execution are distinct evidence. The implementation phase does not authorize a production release.

Flake budget is **1% over a rolling 30-day window**, measured as unchanged-revision intermittent failures divided by executions. Current historical baseline is unavailable. Preserve first-run failures and retry evidence. Above budget freeze new non-remediation tests, quarantine with explicit owner/expiry, and repair determinism before expanding. Do not retry to silently green a gate.

Report real tests run/passed/failed/skipped, coverage when measured, CI p95, infra recovery time and defect escapes when enough history exists. Labels for findings: severity `P0/P1/P2`, reproduction `confirmed/intermittent/unconfirmed`, evidence class `unit/contract/integration/device/physical/image`. All test commands have bounded execution and retained outputs; private signing keys stay outside version control.

Known open gates: AOSP Linux/KVM resource allocation; controlled physical campaign baselines/instrumentation; independent human review/external adoption are not created by agents. Exact current results are in the build evidence files and root-owned progress board.
