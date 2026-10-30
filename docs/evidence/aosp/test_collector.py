"""Synthetic collector integrity checks, not platform implementation tests."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest

SCRIPT = Path(__file__).resolve().parents[3] / "scripts/aosp-evidence.py"


class CollectorIntegrity(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix="boundarylab-collector-")
        self.base = Path(self.tmp.name)
        self.bundle = self.base / "bundle"
        self.call("init", "--attempt-id", "synthetic", "--source-commit", "synthetic-not-a-platform-build", "--build-target", "none")

    def tearDown(self):
        self.tmp.cleanup()

    def call(self, action, *args, expected=0):
        result = subprocess.run([sys.executable, str(SCRIPT), action, "--bundle", str(self.bundle), *args], capture_output=True, text=True, timeout=15)
        self.assertEqual(expected, result.returncode, result.stdout + result.stderr)
        return json.loads(result.stdout)

    def run_command(self, code, *options, expected=0):
        return self.call("run", "--label", "integrity", "--scope", "collector-self-test", "--timeout", "2", *options, "--", sys.executable, "-c", code, expected=expected)

    def test_failed_receipt_is_preserved_and_logs_bounded_redacted(self):
        first = self.run_command("import sys;print('PRIVATE_MARKER' + 'x'*10000);sys.exit(7)", "--max-log-bytes", "100", "--redact", "PRIVATE_MARKER", expected=1)
        path = self.bundle / first["receipt"]
        original = path.read_bytes()
        self.run_command("print('next execution')")
        self.assertEqual(original, path.read_bytes())
        receipt = json.loads(original)
        output = receipt["outputs"]["stdout"]
        self.assertEqual(7, receipt["exit_code"])
        self.assertTrue(output["truncated"])
        self.assertNotIn("PRIVATE_MARKER", (self.bundle / output["path"]).read_text())
        self.assertFalse(receipt["probe_assertions_passed"])
        self.assertTrue(self.call("verify")["integrity_passed"])

    def test_timeout_kills_owned_descendant_before_delayed_write(self):
        marker = self.base / "must-not-exist"
        child = "import time;from pathlib import Path;time.sleep(4);Path(" + repr(str(marker)) + ").write_text('leak')"
        code = "import subprocess,sys,time;subprocess.Popen([sys.executable,'-c'," + repr(child) + "]);time.sleep(10)"
        result = self.run_command(code, expected=1)
        self.assertTrue(result["timed_out"])
        # Waiting is confined to this synthetic cleanup assertion, not a platform test.
        subprocess.run([sys.executable, "-c", "import time;time.sleep(3)"], timeout=5, check=True)
        self.assertFalse(marker.exists())

    def test_export_verification_detects_corrupted_artifact(self):
        source = self.base / "manifest.xml"
        source.write_text("<manifest revision='synthetic'/>")
        result = self.call("artifact", "--label", "manifest", "--scope", "collector-self-test", "--file", str(source))
        self.assertEqual(hashlib.sha256(source.read_bytes()).hexdigest(), result["sha256"])
        self.assertTrue(self.call("verify")["integrity_passed"])
        (self.bundle / result["path"]).write_text("corrupted")
        self.assertFalse(self.call("verify", expected=1)["integrity_passed"])

    def test_large_output_preserves_trailing_failure_and_full_stream_hash(self):
        payload = b"BEGIN\n" + b"x" * (2 * 1024 * 1024) + b"\nUNIQUE_TRAILING_SOONG_FAILURE\n"
        code = "import sys;sys.stdout.buffer.write(b'BEGIN\\n'+b'x'*(2*1024*1024)+b'\\nUNIQUE_TRAILING_SOONG_FAILURE\\n');sys.exit(42)"
        result = self.run_command(code, "--max-tail-bytes", "128", expected=1)
        receipt = json.loads((self.bundle / result["receipt"]).read_text())
        output = receipt["outputs"]["stdout"]
        self.assertEqual(42, receipt["exit_code"])
        self.assertEqual(hashlib.sha256(payload).hexdigest(), output["captured_stream_sha256"])
        self.assertEqual(len(payload), output["observed_bytes"])
        self.assertTrue(output["truncated"])
        self.assertEqual(128, output["tail"]["retained_input_bytes"])
        tail = self.bundle / output["tail"]["path"]
        self.assertIn("UNIQUE_TRAILING_SOONG_FAILURE", tail.read_text())
        self.assertNotIn("UNIQUE_TRAILING_SOONG_FAILURE", (self.bundle / output["path"]).read_text())
        self.assertTrue(self.call("verify")["integrity_passed"])
        tail.write_text("lost trailing failure")
        self.assertFalse(self.call("verify", expected=1)["integrity_passed"])

    def test_empty_or_permissive_claims_cannot_pass(self):
        context = {k: "synthetic" for k in ("fingerprint", "service", "service_pid", "build_target", "source_manifest_sha256")}
        context["source_manifest_sha256"] = hashlib.sha256(b"synthetic manifest").hexdigest()
        for state, cases in [("Enforcing", []), ("Permissive", [{"probe": "authorized", "status": "passed", "expected": "synthetic", "actual": "synthetic"}])]:
            payload = {"schema_version": 1, "context": dict(context, selinux=state), "cases": cases}
            result = self.run_command("print(" + repr(json.dumps(payload)) + ")", "--probe-json", expected=1)
            self.assertFalse(result["probe_assertions_passed"])

    def test_skipped_case_does_not_become_a_complete_probe_pass(self):
        payload = {"schema_version": 1, "context": {
            "fingerprint": "synthetic", "selinux": "Enforcing", "service": "synthetic",
            "service_pid": "123", "build_target": "synthetic", "source_manifest_sha256": hashlib.sha256(b"synthetic").hexdigest()
        }, "cases": [{"probe": "authorized", "status": "passed", "expected": "synthetic", "actual": "synthetic"},
                      {"probe": "denied", "status": "skipped", "expected": "synthetic", "actual": "not executed"}]}
        result = self.run_command("print(" + repr(json.dumps(payload)) + ")", "--probe-json", expected=1)
        self.assertFalse(result["probe_assertions_passed"])
        receipt = json.loads((self.bundle / result["receipt"]).read_text())
        self.assertIsNone(receipt["probe_parse_error"])
        self.assertEqual("skipped", receipt["probe_result"]["cases"][1]["status"])

    def test_partial_evidence_is_exportable_before_command_finishes(self):
        command = [sys.executable, str(SCRIPT), "run", "--bundle", str(self.bundle), "--label", "live",
                   "--scope", "collector-self-test", "--timeout", "5", "--", sys.executable, "-c",
                   "import time;print('live-marker',flush=True);time.sleep(2)"]
        proc = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        try:
            end = time.monotonic() + 2
            observed = False
            while time.monotonic() < end:
                logs = list((self.bundle / "artifacts").glob("*.stdout.log"))
                if logs and "live-marker" in logs[0].read_text():
                    observed = True
                    break
                time.sleep(0.02)
            self.assertTrue(observed, "No partial log was persisted during command")
            self.assertIsNone(proc.poll())
            self.assertEqual(1, len(self.call("verify")["unfinished_commands"]))
            out, err = proc.communicate(timeout=6)
            self.assertEqual(0, proc.returncode, out + err)
            self.assertEqual([], self.call("verify")["unfinished_commands"])
        finally:
            if proc.poll() is None:
                proc.kill()
                proc.wait(timeout=6)


if __name__ == "__main__":
    unittest.main()
