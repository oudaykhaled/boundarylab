#!/usr/bin/env python3
"""Bound local/CI checks and retain exit status, logs and honest evidence labels."""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
PROFILES = {
    "jvm": [["./gradlew", "--no-daemon", "--console=plain", "checkJvm"]],
    "android": [["./gradlew", "--no-daemon", "--console=plain", "checkAndroid"]],
    "reference": [
        [sys.executable, "conformance/shared/run_reference.py", "--output", "build/evidence/reference/shared.json"],
        [sys.executable, "conformance/shared/run_reference.py", "--suite", "benchmarks", "--output", "build/evidence/reference/benchmarks.json"],
        [sys.executable, "examples/fieldpack/demo.py"],
    ],
}


def execute(command: list[str], timeout: int, log: Path, cwd: Path = ROOT) -> dict[str, object]:
    started = time.monotonic()
    timed_out = False
    with log.open("w", encoding="utf-8") as output:
        process = subprocess.Popen(command, cwd=cwd, stdout=output, stderr=subprocess.STDOUT,
                                   start_new_session=True)
        try:
            returncode = process.wait(timeout=timeout)
        except (subprocess.TimeoutExpired, KeyboardInterrupt) as error:
            timed_out = isinstance(error, subprocess.TimeoutExpired)
            os.killpg(process.pid, signal.SIGTERM)
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
                process.wait()
            returncode = 124 if timed_out else 130
    sanitized = log.read_text(encoding="utf-8", errors="replace").replace(str(ROOT), "<repository>").replace(str(Path.home()), "<home>")
    log.write_text(sanitized, encoding="utf-8")
    lines = sanitized.splitlines()
    print("\n".join(lines[-40:]), flush=True)
    return {"command": command, "exit_code": returncode, "timed_out": timed_out,
            "duration_seconds": round(time.monotonic() - started, 3),
            "log": str(log.relative_to(ROOT))}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("profile", choices=[*PROFILES, "all"])
    parser.add_argument("--timeout-seconds", type=int, default=900,
                        help="Per-command deadline (1–3600 seconds).")
    args = parser.parse_args()
    if not 1 <= args.timeout_seconds <= 3600:
        parser.error("timeout must be between 1 and 3600 seconds")
    output = ROOT / "build" / "evidence" / args.profile
    output.mkdir(parents=True, exist_ok=True)
    profiles = list(PROFILES) if args.profile == "all" else [args.profile]
    records = []
    result = 0
    try:
        for profile in profiles:
            for index, command in enumerate(PROFILES[profile], 1):
                print(f"Running {profile} check {index}; deadline {args.timeout_seconds}s", flush=True)
                record = execute(command, args.timeout_seconds, output / f"{profile}-{index}.log")
                records.append(record)
                if record["exit_code"]:
                    result = int(record["exit_code"])
                    return result
        return result
    except OSError as error:
        result = 1
        print(f"Could not run check: {error}", file=sys.stderr)
        records.append({"error": str(error), "exit_code": result})
        return result
    finally:
        (output / "summary.json").write_text(json.dumps({
            "evidence_class": "local-build-and-tests", "android_runtime_executed": False,
            "checks": records, "exit_code": result,
        }, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    raise SystemExit(main())
