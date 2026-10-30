#!/usr/bin/env python3
"""Execute the existing reference suite and shared corpus; emit per-test JSON."""
import argparse
import base64
import datetime
import hashlib
import io
import json
import os
from pathlib import Path
import platform
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import patch

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(ROOT / "examples" / "fieldpack"))
import fieldpack as fp


def corpus(name):
    return json.loads((HERE / name).read_text())["cases"]


class SharedManifestTests(unittest.TestCase):
    pass


def manifest_test(case):
    def run(self):
        raw = base64.b64decode(case["rawBase64"], validate=True)
        if case["valid"]:
            actual = fp.validate_manifest(raw)
            expected = json.loads(base64.b64decode(case["canonicalBase64"]))
            self.assertEqual(actual, expected)
        else:
            with patch.object(fp, "open_root", side_effect=AssertionError("invalid manifest opened root")), self.assertRaises(fp.PackError):
                fp.validate_manifest(raw)
    return run


class SharedFileTests(unittest.TestCase):
    pass


def file_test(case):
    def run(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            target = root / case["entry"]["name"]
            source = case["source"]
            content = base64.b64decode(source["contentBase64"])
            kind = source["kind"]
            if kind == "regular":
                target.write_bytes(content)
            elif kind == "directory":
                target.mkdir()
            elif kind in ("symlink", "hardlink"):
                backing = root / "backing.bin"
                backing.write_bytes(content)
                if kind == "symlink":
                    target.symlink_to(backing)
                else:
                    os.link(backing, target)
            elif kind == "fifo":
                os.mkfifo(target)
            elif kind != "missing":
                self.fail("Unknown recipe " + kind)
            data = {"format": "fieldpack", "version": 1, "algorithm": "sha256", "files": [case["entry"]]}
            self.assertEqual(fp.verify(root, data), [{"name": target.name, "outcome": case["outcome"]}])
    return run


class SharedDigestTests(unittest.TestCase):
    pass


def digest_test(case):
    def run(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            target = root / "subject.bin"
            generator = case["generator"]
            with target.open("wb") as output:
                if generator["kind"] == "base64":
                    output.write(base64.b64decode(generator["data"]))
                else:
                    chunk = bytes((generator["seed"] + i) & 255 for i in range(256)) * 256
                    remaining = case["size"]
                    while remaining:
                        part = chunk[:min(remaining, len(chunk))]
                        output.write(part)
                        remaining -= len(part)
            read_sizes = []
            original_read = os.read

            def counted(fd, count):
                read_sizes.append(count)
                return original_read(fd, count)

            with patch.object(fp.os, "read", side_effect=counted):
                raw = fp.create(root, [target.name])
            self.assertEqual(json.loads(raw)["files"], [{"name": target.name, "size": case["size"], "sha256": case["sha256"]}])
            expected = {"format": "fieldpack", "version": 1, "algorithm": "sha256", "files": [{"name": target.name, "size": case["size"], "sha256": case["sha256"]}]}
            self.assertEqual(raw, (json.dumps(expected, separators=(",", ":")) + "\n").encode())
            self.assertTrue(read_sizes)
            self.assertLessEqual(max(read_sizes), 65536)
    return run


for filename, klass, factory in (("manifest-v1.json", SharedManifestTests, manifest_test), ("file-v1.json", SharedFileTests, file_test), ("digest-v1.json", SharedDigestTests, digest_test)):
    for vector in corpus(filename):
        setattr(klass, "test_" + vector["id"].replace("-", "_"), factory(vector))


class JsonResult(unittest.TextTestResult):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.records = []
        self.statuses = {}

    def startTest(self, test):
        self.started = time.monotonic()
        self.statuses[test.id()] = "passed"
        super().startTest(test)

    def addFailure(self, test, err):
        self.statuses[test.id()] = "failed"
        super().addFailure(test, err)

    def addError(self, test, err):
        self.statuses[test.id()] = "error"
        super().addError(test, err)

    def addSkip(self, test, reason):
        self.statuses[test.id()] = "skipped"
        super().addSkip(test, reason)

    def addSubTest(self, test, subtest, err):
        if err is not None:
            self.statuses[test.id()] = "failed"
        super().addSubTest(test, subtest, err)

    def stopTest(self, test):
        self.records.append({"id": test.id(), "status": self.statuses[test.id()], "durationSeconds": round(time.monotonic() - self.started, 6)})
        super().stopTest(test)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--suite", choices=("reference", "benchmarks"), default="reference")
    args = parser.parse_args()
    if args.suite == "reference":
        suite = unittest.defaultTestLoader.loadTestsFromName("test_fieldpack")
        for klass in (SharedManifestTests, SharedFileTests, SharedDigestTests):
            suite.addTests(unittest.defaultTestLoader.loadTestsFromTestCase(klass))
        sources = [ROOT / "examples/fieldpack/fieldpack.py", ROOT / "examples/fieldpack/test_fieldpack.py", Path(__file__), *sorted(HERE.glob("*-v1.json"))]
    else:
        suite = unittest.defaultTestLoader.discover(str(ROOT / "benchmarks"), pattern="test_*.py")
        sources = [Path(__file__), *sorted((ROOT / "benchmarks").glob("*.py"))]
    stream = io.StringIO()
    result = unittest.TextTestRunner(stream=stream, resultclass=JsonResult, verbosity=2).run(suite)
    counts = {status: sum(r["status"] == status for r in result.records) for status in ("passed", "failed", "error", "skipped")}
    report = {
        "schemaVersion": 1, "evidenceClass": "python-reference-local-filesystem" if args.suite == "reference" else "benchmark-analysis-unit-tests",
        "timestampUtc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "baseCommit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
        "sourceState": "working-tree; source SHA-256 recorded below",
        "command": "python3 conformance/shared/run_reference.py --suite " + args.suite + " --output " + str(args.output),
        "environment": {"python": platform.python_version(), "os": platform.system(), "osRelease": platform.release(), "machine": platform.machine()},
        "sourceSha256": {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources},
        "tests_run": result.testsRun, "tests_passed": counts["passed"], "tests_failed": counts["failed"] + counts["error"], "tests_skipped": counts["skipped"],
        "coverage": None, "coverageReason": "No coverage instrumentation run; test counts are not code coverage.",
        "cases": result.records,
        "failures": [{"id": t.id(), "detail": detail.replace(str(ROOT), "<repository>")} for t, detail in result.failures + result.errors],
        "limitations": ["Reference local POSIX filesystem only; no Android/Flutter/Binder/JVM boundary execution.", "Reference fault tests inject selected failures; no blocked provider or adversarial snapshot guarantee."] if args.suite == "reference" else ["Analysis/schedule code tested with synthetic samples; no latency campaign or performance claim."]}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps({key: report[key] for key in ("tests_run", "tests_passed", "tests_failed", "tests_skipped", "coverage")}))
    if not result.wasSuccessful():
        print(stream.getvalue().replace(str(ROOT), "<repository>"), file=sys.stderr)
    return int(not result.wasSuccessful())


if __name__ == "__main__":
    raise SystemExit(main())
