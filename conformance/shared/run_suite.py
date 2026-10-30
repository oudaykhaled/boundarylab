#!/usr/bin/env python3
"""Bound a real suite invocation and preserve its machine-readable evidence."""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import platform
import signal
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]


def source_hashes():
    result = {}
    for folder in ("core", "desktop", "android", "sdk", "examples/android-native", "examples/flutter", "conformance/shared"):
        for path in (ROOT / folder).rglob("*"):
            if any(p in ("build", ".gradle", ".dart_tool") for p in path.parts) or not path.is_file():
                continue
            if path.name == "local.properties":
                continue
            if path.suffix in (".kt", ".java", ".c", ".h", ".aidl", ".dart", ".kts", ".gradle", ".json", ".py", ".xml", ".yaml", ".toml", ".properties"):
                result[str(path.relative_to(ROOT))] = hashlib.sha256(path.read_bytes()).hexdigest()
    for name in ("build.gradle.kts", "settings.gradle.kts", "gradle.properties", "gradle/libs.versions.toml", "gradle/wrapper/gradle-wrapper.properties"):
        path = ROOT / name
        if path.is_file():
            result[name] = hashlib.sha256(path.read_bytes()).hexdigest()
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--cwd", default=".")
    parser.add_argument("--timeout", type=int, default=600)
    parser.add_argument("--junit", action="append", default=[])
    parser.add_argument("--flutter", action="store_true")
    parser.add_argument("--build-only", action="store_true")
    parser.add_argument("command", nargs=argparse.REMAINDER)
    args = parser.parse_args()
    command = args.command[1:] if args.command[:1] == ["--"] else args.command
    if not command or not 1 <= args.timeout <= 900 or not args.name.replace("-", "").isalnum():
        parser.error("command, alphanumeric/hyphen name and timeout 1..900 required")
    output = ROOT / "docs/evidence/build"
    output.mkdir(parents=True, exist_ok=True)
    stem = "crossplatform-qa-" + args.name
    log = output / (stem + ".log")
    start = time.time()
    before = source_hashes()
    timeout = False
    with log.open("w") as stream:
        process = subprocess.Popen(command, cwd=ROOT / args.cwd, stdout=stream, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            code = process.wait(timeout=args.timeout)
        except (subprocess.TimeoutExpired, KeyboardInterrupt) as exc:
            timeout = isinstance(exc, subprocess.TimeoutExpired)
            os.killpg(process.pid, signal.SIGTERM)
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
                process.wait()
            code = 124 if timeout else 130
    raw = log.read_text(errors="replace")
    sanitized = raw.replace(str(ROOT), "<repository>").replace(str(Path.home()), "<home>")
    log.write_text(sanitized)
    cases, artifacts = [], []
    for pattern in args.junit:
        for path in sorted(ROOT.glob(pattern)):
            if path.stat().st_mtime < start:
                continue  # An earlier successful run must not mask this run.
            tree = ET.parse(path)
            artifacts.append({"path": str(path.relative_to(ROOT)), "sha256": hashlib.sha256(path.read_bytes()).hexdigest()})
            for case in tree.iter("testcase"):
                status = "failed" if case.find("failure") is not None or case.find("error") is not None else "skipped" if case.find("skipped") is not None else "passed"
                cases.append({"id": case.get("classname", "") + "." + case.get("name", ""), "status": status, "durationSeconds": case.get("time")})
    if args.flutter:
        tests, events = {}, []
        for line in raw.splitlines():
            try:
                event = json.loads(line)
            except ValueError:
                continue
            if isinstance(event, dict):
                events.append(event)
                if event.get("type") == "testStart":
                    tests[event["test"]["id"]] = event["test"]
        for event in events:
            if event.get("type") != "testDone" or event.get("hidden"):
                continue
            test = tests.get(event["testID"], {})
            cases.append({"id": test.get("name", str(event["testID"])), "status": "skipped" if event.get("skipped") else "passed" if event.get("result") == "success" else "failed"})
    report = {
        "schemaVersion": 1, "evidenceClass": "compilation-static-analysis" if args.build_only else "host-unit-integration-tests", "androidRuntimeExecuted": False,
        "timestampUtc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "baseCommit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
        "sourceState": "working tree; compare before/after source hashes for concurrent edits",
        "sourceSha256Before": before, "sourceSha256After": source_hashes(),
        "command": command, "cwd": args.cwd, "exitCode": code, "timedOut": timeout,
        "durationSeconds": round(time.time() - start, 3),
        "environment": {"os": platform.system(), "osRelease": platform.release(), "machine": platform.machine()},
        "tests_run": len(cases), "tests_passed": sum(c["status"] == "passed" for c in cases),
        "tests_failed": sum(c["status"] == "failed" for c in cases), "tests_skipped": sum(c["status"] == "skipped" for c in cases),
        "coverage": None, "coverageReason": "No coverage instrumentation configured by this runner.",
        "cases": cases, "junitArtifacts": artifacts, "log": str(log.relative_to(ROOT)),
        "suiteCompleted": None if args.build_only else code == 0 and bool(cases),
        "buildCompleted": code == 0,
    }
    (output / (stem + ".json")).write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps({k: report[k] for k in ("command", "exitCode", "tests_run", "tests_passed", "tests_failed", "tests_skipped", "suiteCompleted")}))
    print("\n".join(sanitized.splitlines()[-25:]))
    return code if code else 0 if cases or args.build_only else 3


if __name__ == "__main__":
    raise SystemExit(main())
