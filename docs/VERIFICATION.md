# Proposed verification and experiment protocol

No tests or benchmarks described here have been executed for BoundaryLab. This is the acceptance design for future implementation, governed by the [evidence policy](EVIDENCE-POLICY.md).

## Correctness oracles

Check deterministic job digests against an independent reference implementation and published fixture vectors. Pure-core model tests cover allowed state transitions, fair admission, owner isolation, cancellation races and fixed queue/retention bounds. Native integration tests must cross the real process boundary; mock transports cannot satisfy IPC acceptance.

Record exact commit/artifact hashes, dependency/toolchain pins, OS build/API/ABI, device class, build mode, compilation/profile settings, input seed/size, scenario, runner, expected/actual outcome and raw-output/trace checksum. Redact serials, private paths and personal data. Only generated content enters traces. Keep failed and invalid runs with their reasons.

## Required fault matrix

| Fault | Controlled trigger | Acceptance and evidence |
|---|---|---|
| Broker dies before/after admission | Debug-only checkpoint and external process termination | Clients survive, no false success, ambiguous work visible, new epoch on reconnect |
| Client dies while another remains | Kill one task-owned client process | Dead session reclaimed; surviving client progresses; no stale owner records |
| Cancel/completion race | Barriers immediately before terminal commit | One authoritative terminal state; cancellation-first cannot later become success |
| Timeout without cancellation | Observe waiting deadline, then explicit SDK cancellation | Distinguish UI timeout from actual stopped work; count active workers |
| Flutter engine destruction/recreation | Dispose engine during active request | No calls to disposed engine, retained Activity or leaked session; new engine isolated |
| Connection churn | 1,000 bind/session/close cycles | No live-session/descriptor growth; no duplicate reconnect loops |
| Noisy caller and queue overflow | Fixed arrival rate beyond admitted capacity | Explicit rejection, bounded bytes/entries, observable progress for another admitted caller |
| Unauthorized/other-owner operations | Wrong signing identity; guessed session/job handles | Denied before work or result access; no cross-owner status/cancel |
| Malformed/oversized request | Invalid sizes/enum/version and truncated PC frame corpus | Bounded failure or clean close, no crash, unrelated client unaffected |
| Major/minor skew | Old compiled consumer against new provider and reverse | Supported subset works, incompatible major fails before admission |
| Desktop slow/disconnected peer | Stop reading or close mid-frame | Read/write deadline cleans connection; host remains responsive |
| Platform service restart and denied policy | Dedicated AOSP image service restart; untrusted client | Init lifecycle and epoch visible, SELinux enforcing, allowed/denied test results retained |

For deterministic fault cells, target 100 seeded repetitions per required scenario. Record the actual count; a failed required case blocks its gate. The 120h candidate includes a 30-minute bounded saturation/churn observation; the platform extension targets a two-hour campaign where resources allow. Neither establishes a production availability percentage. A package force-stop, process kill, OS low-memory kill and reboot are distinct experiments; label what actually occurred.

Fault controls exist only in dedicated test variants. Inspect the release artifact to ensure kill, stall and deliberately unsafe handlers are absent. Act only on the task-owned process/target. An OS-wide restart test belongs in a disposable platform image, never the host workstation.

## Falsifiable questions

H1: larger cooperative work chunks improve throughput but worsen cancellation latency. Compare fixed workload and scheduler with chunk sizes 16/64/256 KiB. Keep correctness and memory limits equal. Reject a chosen chunk size if it breaches the declared cancellation budget even when throughput improves.

H2: lazy connection improves first frame while delaying first useful middleware action. Measure both, not just the favorable metric. A shell startup win is not an overall user benefit if the first action becomes unacceptable.

H3: bounded admission prevents retained-job memory from increasing under overload. Sweep offered load past saturation, record retained bytes/jobs and settle after drain. Reject the design if the internal cap or cleanup invariant fails. Runtime allocator high-water behavior is reported separately from logical leaks.

H4, conditional platform investigation: dedicated-process placement contains worker failure at an IPC/startup cost. Compare the actual platform and app profiles only under clearly described deployment differences; do not attribute the whole cost difference to Binder alone. No successful optimization is required: a well-supported negative result can close the investigation.

## Comparable baselines

Use native empty shell, native same-core in-process work, native broker work, Flutter empty shell and Flutter broker work. Compare middleware deltas within each framework. Do not call the entire Flutter/native startup difference plugin overhead, or claim in-process execution gives equivalent isolation.

Separate: first UI frame, fully useful UI milestone, SDK initialization, cold/warm service binding, admission acknowledgement, queue wait, execution, terminal observation, cancellation-to-quiescence, throughput/goodput, rejection and timeout rates. With polling, observation delay is part of user latency and must be reported separately from computation completion. Log polling interval and count.

Measure native/Flutter client and broker memory separately, plus aggregate Android PSS where appropriate. macOS JVM RSS is a different metric and is not ranked against Android PSS as though equivalent. Allocation profiling and periodic memory sampling can perturb timing; use separate diagnosis runs and quantify trace overhead. [Macrobenchmark](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview) and [Perfetto](https://perfetto.dev/docs/) support the proposed Android measurement approach.

## Environment and sampling

Use release-like non-debuggable/profileable Android builds for performance, with identical compiler/compilation/Baseline Profile conditions per comparison. Keep debug fault results separate. Pin reference hardware after inventory; use one physical phone for the initial controlled study. An emulator is valid for scoped correctness, not a silent substitute for physical-phone performance. The desktop is its own experiment, not pooled phone data.

Define process-cold versus service-resident conditions; process-cold does not imply filesystem-cache-cold. Record thermal/power/storage conditions and a predeclared invalid-run rule. Pilot ten paired trials to validate instrumentation and estimate variability; exclude the pilot from confirmatory results. Then use three independently initialized sessions with 30 launches per treatment, balanced/randomized in blocks. Report per-session median/range and a pooled median with session-aware uncertainty; optional p90/p95 is exploratory at this sample size, and no stable startup p99 claim is made.

For steady state, use five 60-second windows in each of three sessions at 1/2/4/8 concurrent clients, with explicit warmup and cheap/long job mixes. A fixed-arrival-rate overload run supplements closed-loop concurrency. Record scheduled-arrival delay, admitted/completed/rejected/timed-out counts and all outcomes so success-only latency cannot hide overload. Report p50/p95 with sample/window counts and uncertainty clustered by session/window. Preserve analysis script and random seed. Reduce the matrix explicitly if the timebox cannot support valid thermal resets.

## Proposed budgets, calibrated before confirmation

Hard functional gates: zero cross-owner access, correct digest, no false success, caps respected, no UI-thread wait on job execution and no contradictory terminal transition. These are mandatory independently of hardware speed.

Provisional usefulness targets on named reference hardware: cooperative cancellation-to-quiescence p95 ≤250 ms; foreground reconnect resolves to ready or explicit unavailable within 10 seconds; middleware first-use median overhead ≤100 ms against the same-framework control. Warm control-request p95 target is ≤10 ms at one client, excluding deliberate polling sleep. These are design hypotheses, not achieved or vacancy-specified numbers.

Measure baseline runtime footprint before freezing a steady-state memory budget. Record its decision and rationale before confirmatory comparisons; hard metadata/buffer bounds remain fixed regardless. A 10% repeated performance shift triggers investigation, not an automatic claim of statistical significance. A claimed improvement needs an uncertainty interval, protected-metric tradeoff and raw baseline/candidate data. Inconclusive stays inconclusive. Do not silently change thresholds after seeing the result.

## Future release evidence bundle

Produce a manifest, exact commands, fixture hashes, raw observations, exclusions, report, trace configurations/files, symbols/mappings and a known-limitations ledger. Use release assets with SHA-256 manifests for large sanitized traces; do not commit system images or private logs to source history. A fresh clone consumer must not depend on undocumented local caches or secret profiles. B16 seeks independent reproduction if a real volunteer is available, and labels author-only reproduction otherwise.
