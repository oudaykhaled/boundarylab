import contextlib
import hashlib
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import fieldpack as fp


class FieldPackTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)
        self.root = self.base / "attachments"
        self.root.mkdir()
        (self.root / "a.txt").write_bytes(b"abc")
        (self.root / "empty.bin").write_bytes(b"")
        self.output = self.base / "manifest.json"

    def manifest(self):
        return json.loads(fp.create(self.root, ["empty.bin", "a.txt"]))

    def test_known_vectors_and_deterministic_order(self):
        data = self.manifest()
        self.assertEqual([x["name"] for x in data["files"]], ["a.txt", "empty.bin"])
        self.assertEqual(data["files"][0]["sha256"],
                         "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
        self.assertEqual(data["files"][1]["sha256"],
                         "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")

    def test_match_tamper_missing_and_extra_scope(self):
        data = self.manifest()
        (self.root / "extra.txt").write_text("outside manifest scope")
        self.assertEqual([x["outcome"] for x in fp.verify(self.root, data)], ["MATCH", "MATCH"])
        (self.root / "a.txt").write_bytes(b"abd")
        self.assertEqual(fp.verify(self.root, data)[0]["outcome"], "DIGEST_MISMATCH")
        (self.root / "a.txt").write_bytes(b"longer")
        self.assertEqual(fp.verify(self.root, data)[0]["outcome"], "SIZE_MISMATCH")
        (self.root / "a.txt").unlink()
        self.assertEqual(fp.verify(self.root, data)[0]["outcome"], "MISSING")

    def test_real_binary_multichunk_and_bounded_reads(self):
        raw = bytes(range(256)) * 600
        (self.root / "binary.dat").write_bytes(raw)
        original = os.read
        sizes = []
        def observed(fd, count):
            sizes.append(count)
            return original(fd, count)
        with patch.object(fp.os, "read", side_effect=observed):
            data = json.loads(fp.create(self.root, ["binary.dat"]))
        self.assertEqual(data["files"][0]["sha256"], hashlib.sha256(raw).hexdigest())
        self.assertLessEqual(max(sizes), fp.CHUNK)
        self.assertGreater(len(sizes), 2)

    def test_name_policy(self):
        for name in ["../a.txt", "/a.txt", "a/b", "a\\b", ".", "..", "a.", "CON", "con.txt",
                     "lpt1.bin", "Upper.txt", "é.txt", "a" * 65, "", 42]:
            with self.subTest(name=name), self.assertRaises(fp.PackError):
                fp.check_name(name)

    def test_strict_manifest_negatives(self):
        data = self.manifest()
        mutations = [
            {**data, "version": True}, {**data, "version": 2},
            {**data, "algorithm": "md5"}, {**data, "unknown": 1},
            {**data, "files": []}, {**data, "files": data["files"] * 2},
            {**data, "files": list(reversed(data["files"]))},
        ]
        for field, value in [("size", True), ("size", -1), ("size", 1.5),
                             ("size", fp.MAX_BYTES + 1), ("name", "../escape"),
                             ("sha256", "A" * 64), ("extra", "bad")]:
            entry = {**data["files"][0], field: value}
            mutations.append({**data, "files": [entry]})
        for candidate in mutations:
            with self.subTest(candidate=candidate), self.assertRaises(fp.PackError):
                fp.validate_manifest(json.dumps(candidate).encode())
        for raw in [b'{"version":1,"version":1}', b'NaN', b'\xef\xbb\xbf{}', b'\xff', b'[' * 2000,
                    b' ' * (fp.MAX_MANIFEST + 1)]:
            with self.subTest(raw=raw[:30]), self.assertRaises(fp.PackError):
                fp.validate_manifest(raw)

    def test_count_and_total_caps(self):
        for names in [[], ["a.txt"] * 2, [f"f{i}" for i in range(33)]]:
            with self.assertRaises(fp.PackError):
                fp.create(self.root, names)
        with patch.object(fp, "MAX_BYTES", 2), self.assertRaises(fp.PackError):
            fp.create(self.root, ["a.txt"])
        data = self.manifest()
        for entry in data["files"]:
            entry["size"] = fp.MAX_BYTES
        with self.assertRaises(fp.PackError):
            fp.validate_manifest(json.dumps(data).encode())

    def test_symlink_hardlink_fifo_and_root_symlink(self):
        (self.root / "link.txt").symlink_to(self.root / "a.txt")
        os.link(self.root / "a.txt", self.root / "hard.txt")
        os.mkfifo(self.root / "pipe")
        for name in ["link.txt", "hard.txt", "a.txt", "pipe"]:
            with self.subTest(name=name), self.assertRaises((OSError, fp.PackError)):
                fp.create(self.root, [name])
        alias = self.base / "alias"
        alias.symlink_to(self.root)
        with self.assertRaises(OSError):
            fp.create(alias, ["empty.bin"])

    def test_read_error_and_mutation_close_descriptors(self):
        original_close = os.close
        closed = []
        def closed_fd(fd):
            closed.append(fd)
            original_close(fd)
        with patch.object(fp.os, "read", side_effect=OSError("controlled")), \
                patch.object(fp.os, "close", side_effect=closed_fd), self.assertRaises(OSError):
            fp.create(self.root, ["a.txt"])
        self.assertEqual(len(closed), 2)  # File and pinned root on failure.
        original_read = os.read
        changed = False
        def mutate(fd, count):
            nonlocal changed
            result = original_read(fd, count)
            if result and not changed:
                changed = True
                (self.root / "a.txt").write_bytes(b"changed")
            return result
        with patch.object(fp.os, "read", side_effect=mutate), self.assertRaises(fp.PackError):
            fp.create(self.root, ["a.txt"])

    def test_output_is_external_atomic_and_does_not_overwrite(self):
        raw = fp.create(self.root, ["a.txt"])
        with self.assertRaises(fp.PackError):
            fp.write_manifest(self.root, self.root / "manifest.json", raw)
        fp.write_manifest(self.root, self.output, raw)
        with self.assertRaises(FileExistsError):
            fp.write_manifest(self.root, self.output, b"replacement")
        self.assertEqual(self.output.read_bytes(), raw)
        self.assertEqual(list(self.base.glob(".fieldpack-*")), [])

    def test_replacement_after_read_is_detected(self):
        original_stat = os.fstat
        calls = 0
        def replace_after_final_fstat(fd):
            nonlocal calls
            info = original_stat(fd)
            calls += 1
            if calls == 2:
                replacement = self.root / "replacement.txt"
                replacement.write_bytes(b"abc")
                replacement.replace(self.root / "a.txt")
            return info
        with patch.object(fp.os, "fstat", side_effect=replace_after_final_fstat), \
                self.assertRaisesRegex(fp.PackError, "INPUT_CHANGED"):
            fp.create(self.root, ["a.txt"])

    def test_failed_create_and_interrupt_leave_no_manifest(self):
        with contextlib.redirect_stdout(io.StringIO()) as result:
            code = fp.main(["create", "--root", str(self.root), "--output", str(self.output), "missing"])
        self.assertEqual(code, 2)
        self.assertFalse(self.output.exists())
        self.assertNotIn(str(self.root), result.getvalue())
        with patch.object(fp, "create", side_effect=KeyboardInterrupt), \
                contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(fp.main(["create", "--root", str(self.root), "--output", str(self.output), "a.txt"]), 130)
        self.assertFalse(self.output.exists())

    def test_cli_exit_codes(self):
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(fp.main(["create", "--root", str(self.root), "--output", str(self.output), "a.txt"]), 0)
            self.assertEqual(fp.main(["verify", "--root", str(self.root), "--manifest", str(self.output)]), 0)
            (self.root / "a.txt").unlink()
            self.assertEqual(fp.main(["verify", "--root", str(self.root), "--manifest", str(self.output)]), 1)
            self.output.write_text("invalid")
            self.assertEqual(fp.main(["verify", "--root", str(self.root), "--manifest", str(self.output)]), 2)


if __name__ == "__main__":
    unittest.main()
