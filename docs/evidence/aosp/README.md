# AOSP attempt evidence

Prepared for the authorized **US$50 total** attempt. Root owns cloud resources, budget/deadline enforcement, actual execution, export and teardown. This directory initially contains collection tooling and a plan, **no AOSP build/boot/probe result**. P01–P09 acceptance remains unchanged. A shortened smoke run cannot close compatibility, controlled measurement, two-hour soak or independent reproduction requirements.

## Minimum sequence

1. Record the authorized attempt ledger, exact source commit, target, current image/host-package immutable IDs, quota/KVM checks and deadline. Root runs all commands; the collector does not provision anything.
2. Collect matched prebuilt baseline boot, fingerprint, enforcing state and host identity as `prebuilt-baseline`. This proves boot feasibility only. Record an unchanged **source** build/boot separately as `source-baseline`, including `repo manifest -r` and build outputs.
3. For `custom-image`, capture the exact source manifest, local overlay/source hashes, build target, build command/result and image hashes. Capture failed build/boot attempts too. Large images may be hash-only; the receipt explicitly says the bytes were not exported.
4. Execute the implementation owner's **single combined** `aosp/scripts/device-smoke.py` harness on the explicit Cuttlefish target. Capture enforcing state before/after, fingerprint, service registration/PID/domain and authorized vector, denied caller, malformed/over-cap, unsupported file, cancellation/terminal winner, restart/fresh epoch/stale handle and supervision observations. Capture harness `checks.json`, individual command receipts and relevant bounded logs. No phone discovery or implicit ADB target.
5. Export the growing bundle during execution, then verify the local copy before deleting attempt-owned resources. Keep failed and skipped cases visible. Root records teardown inventory and estimated versus settled cost separately.

No compatibility pass without a previously compiled consumer and discriminating incompatibility control. No physical performance inference from Cuttlefish. No permissive-domain workaround, privileged file hashing or repeated build/debug loop beyond the remaining budget/deadline.

## Collector interface

`scripts/aosp-evidence.py` uses Python 3.9+ standard library on Linux/macOS. Commands are argument arrays; it never invokes a shell implicitly. A command runs in its own process group, has an explicit deadline, and its remaining descendants are killed when collection ends. **Do not use it to launch a persistent VM/service**; use it for finite probes/builds and capture the independently supervised service state.

```sh
python3 scripts/aosp-evidence.py init --bundle /tmp/boundarylab-aosp-evidence \
  --attempt-id ATTEMPT_ID --source-commit COMMIT --build-target PINNED_TARGET

# Root supplies the actual explicit serial and directory. No credentials in argv.
python3 scripts/aosp-evidence.py run --bundle /tmp/boundarylab-aosp-evidence \
  --label custom-smoke --scope custom-image --timeout 300 \
  --redact EXPLICIT_SERIAL --redact PRIVATE_WORKSPACE \
  -- python3 aosp/scripts/device-smoke.py --serial EXPLICIT_SERIAL --out PRIVATE_WORKSPACE

python3 scripts/aosp-evidence.py artifact --bundle /tmp/boundarylab-aosp-evidence \
  --label smoke-checks --scope custom-image --file SANITIZED_CHECKS_JSON
python3 scripts/aosp-evidence.py artifact --bundle /tmp/boundarylab-aosp-evidence \
  --label pinned-source-manifest --scope custom-image --file SANITIZED_MANIFEST_XML

python3 scripts/aosp-evidence.py verify --bundle /tmp/boundarylab-aosp-evidence \
  > /tmp/boundarylab-aosp-integrity.json
```

Copy the bundle plus the separately saved verification inventory to the local evidence destination after each milestone. Run `verify` again on the destination and compare `files_sha256` maps (timestamps may differ). A `command-start` receipt and atomic bounded log snapshots are written during execution, allowing incremental backup before a long build ends. `verify` reports `unfinished_commands`; their partial logs are not completed test results. Take a stable final copy after each command completes; a copy racing a running writer is only a partial backup. The collector deliberately has no network/export credentials or automatic uploader. Root uses its existing authorized transport. Preserve the original command exit when a pipeline/export operation follows it.

Each invocation adds UUID-named receipts rather than overwriting a previous attempt. `run` returns 1 for unexpected exit, timeout or failed optional probe assertions, 2 for collector configuration/I/O errors. `--expect-exit` supports an intentionally failing command; this proves only its expected exit, not a semantic denial. Output logs retain the first 1 MiB plus a separate rolling last 64 KiB per stream by default (`--max-log-bytes` and `--max-tail-bytes`). Both head and tail have atomic live snapshots and final hashes; the tail records its input-byte offset. The full observed-stream hash still includes all bytes, including the discarded middle. Receipt includes byte counts and truncation. A timeout marks stream completeness false. Truncated JSON cannot pass optional probe validation: the collector requires complete stdout in the head buffer and never reconstructs probe JSON from head/tail fragments. Both stdout and stderr are retained, including failed runs. Artifact copies default to 64 MiB and reject symlinks; `--hash-only --max-bytes N` can record larger immutable images without copying them. Hash-only evidence is explicitly not locally reproducible image bytes.

`--probe-json` is optional for a harness emitting exactly one [probe-result object](probe-result.schema.json) on stdout. The existing implementation harness may instead write its own `checks.json`; collect that file as an artifact and let root reconcile its actual assertions. Do not rerun a test merely to convert JSON formats. `command_expectation_met` and `probe_assertions_passed` are separate; absent assertions are never passed. Even valid passing JSON is runner-reported evidence, not independent verification or a blanket ticket approval.

Logs replace the current home/work directory and explicit `--redact` literals. **This is not a general secret scanner.** Never put credentials in commands, environment dumps or captured files. Artifacts are copied byte-for-byte; root must sanitize manifests, harness receipts, logs and screenshots **before** adding them, preserving the original private receipt and recording any redaction. No serials, private keys, account IDs or private directories in public evidence. Screenshots must show only the owned test image; screenshots are optional when a headless result has sufficient command evidence.

## Integrity checks

`python3 -m unittest discover -s docs/evidence/aosp -p 'test_collector.py'` exercises local synthetic commands only: immutable failed/success receipts, bounded redaction, timeout descendant cleanup, copied-artifact corruption detection rejection of empty/permissive passing assertions, and a unique trailing failure surviving more than 2 MiB of output with full-stream hash and tail-corruption detection. Their results are collector evidence, never Android/AOSP runtime evidence. No app/desktop suite is rerun.
