# A lost job reported as unavailable

Date: 2026-10-30. Evidence class: observed prototype defect, source diagnosis, physical-device regression and AI review. This was found during BoundaryLab development, not a customer incident. The correction is in [source commit 06b2d4f](https://github.com/oudaykhaled/boundarylab/commit/06b2d4f9d550a7c2b02bcc4d3e8743800ea9eb4c).

## Observed failure

The installed native client submitted a generated job, observed real broker process loss, and then queried its old handle. The contract requires `LOST_OR_UNKNOWN`: the previous process cannot establish that job's outcome, and a new broker epoch cannot restore it. The SDK instead returned `UNAVAILABLE`.

The [unchanged failing instrumentation receipt](../evidence/build/android-native-broker-death-final.json) records one executed test, zero passes and one assertion failure: expected `LOST_OR_UNKNOWN`, received `UNAVAILABLE`. The target was an allocated physical Samsung SM-A336B, API36, arm64. The ADB command itself exited zero; the test-result parser correctly treated the assertion failure as a failed gate. A shell exit code alone was insufficient evidence.

## Competing explanations

| Explanation | Prediction | Discriminating check |
|---|---|---|
| Broker process never actually died | The old connection/epoch remains usable, or no loss callback occurs | The actual process-kill test waits for loss and requires a fresh epoch on explicit reconnect |
| UI/test callback ordering incorrectly reused a fresh request | The queried handle or assertion would refer to the new session | Inspect the retained old handle and the unchanged test's status-before-reconnect sequence |
| SDK checks connection availability before the old handle's generation | A null remote returns generic unavailability even though generation already establishes loss | Trace `status`/`cancel` into the shared request/poll helpers; preserve the handle generation at helper entry and rerun the same assertion |

Source inspection supported the third explanation. `status` entered the polling helper using the current client generation; `checkHandle` ran only inside the remote action. After loss, the remote was absent, so the action and handle check were never reached. `cancel` had the equivalent ordering issue. The lower-level helper already knew how to reject a mismatched expected generation correctly.

## Smallest correction

[`BoundaryClient.kt`](../../sdk/kotlin/src/main/kotlin/dev/boundarylab/sdk/BoundaryClient.kt) now passes `handle.generation` into `status` polling and `cancel` request handling. The polling helper accepts that expected generation while ordinary request lookup retains its current-generation default. This reuses the existing loss guard; it does not reconnect, replay, restore jobs or invent a successful outcome.

The changed SDK source SHA-256 is `a75d5b0dd9c61c07f4bf4110223883befb8a2d67769fe954676d66bb1a756e51`. The native failing assertion was preserved. A focused broker/SDK regression additionally checks old-handle status and cancellation without conflating a fresh unconnected request with a lost handle.

## Executed result and protected behavior

- The [same physical broker-kill test](../evidence/build/android-native-broker-death-post-loss-r2.json) passed 1/1 after rebuilding/installing the corrected source.
- The [full native suite](../evidence/build/android-native-instrumentation-post-loss-r2.json) passed 9/9, including actual loss, fresh epoch and explicit new submission.
- The [broker/SDK suite](../evidence/build/android-broker-instrumentation-post-loss-r2.json) passed 19 cases, with zero failures and one unavailable hardlink fixture. Ownership, process death, bounded polling and descriptor checks remained in the protected suite.
- The [installed Flutter suite](../evidence/build/android-flutter-instrumentation-post-loss-r2.json) passed 3/3 against the corrected SDK. A [runtime index](../evidence/build/android-runtime-index.json) ties receipts to installed artifacts and source snapshots.

The previous failure remains published beside the later success. These results establish the tested loss-ordering correction; they do not prove every before/after-terminal race, every Flutter teardown interleaving or physical performance. No speedup was asserted or measured.

## Product and roadmap impact

The distinction matters because users must not mistake an unknown result for work that never started. Recovery remains explicit: reconnect into a fresh epoch and choose a new attempt. File preparation must recheck all attachments rather than reuse uncertain earlier results. This defect blocked the affected native gate until the real test passed; wider fault, compatibility, provider and measurement campaigns remain separately open in the [execution status](../../planning/EXECUTION-STATUS.md).
