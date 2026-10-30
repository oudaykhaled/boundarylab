"""Reproduce a manual attachment handoff and failures in a disposable directory."""
import json
from pathlib import Path
import shutil
import tempfile

import fieldpack


def expect(args, expected):
    actual = fieldpack.main(args)
    if actual != expected:
        raise RuntimeError(f"Expected exit {expected}, observed {actual}")


def run():
    fixtures = Path(__file__).parent / "fixtures"
    with tempfile.TemporaryDirectory(prefix="fieldpack-demo-") as temporary:
        workspace = Path(temporary)
        sender, recipient = workspace / "sender", workspace / "recipient"
        shutil.copytree(fixtures, sender)
        shutil.copytree(fixtures, recipient)
        manifest = workspace / "inspection.fieldpack.json"
        names = sorted(item.name for item in fixtures.iterdir())
        args = ["verify", "--root", str(recipient), "--manifest", str(manifest)]
        print("1. Create manifest from actual note, CSV and PNG files")
        expect(["create", "--root", str(sender), "--output", str(manifest), *names], 0)
        print("2. Verify separately copied recipient files")
        expect(args, 0)
        print("3. Change one byte without changing file size")
        note = recipient / "inspection-note.txt"
        content = note.read_bytes()
        note.write_bytes(b"X" + content[1:])
        expect(args, 1)
        print("4. Remove one recipient attachment")
        (recipient / "inspection-schematic.png").unlink()
        expect(args, 1)
        print("5. Reject an unsupported manifest version")
        invalid = json.loads(manifest.read_bytes())
        invalid["version"] = 2
        manifest.write_text(json.dumps(invalid))
        expect(args, 2)
        print("PASS: all expected outcomes and exit codes observed")


if __name__ == "__main__":
    run()
