#!/usr/bin/env python3
"""Run bounded Flutter analysis, widget/channel tests and a debug APK build."""
import json
import os
from check import ROOT, execute


def main() -> int:
    output = ROOT / "build/evidence/flutter"
    output.mkdir(parents=True, exist_ok=True)
    flutter = os.environ.get("FLUTTER_BIN", "flutter")
    commands = []
    for directory in ("sdk/flutter", "examples/flutter"):
        for args in (["pub", "get", "--enforce-lockfile"], ["analyze", "--no-pub"], ["test", "--no-pub", "--machine"]):
            commands.append((directory, [flutter, *args]))
    commands.append(("examples/flutter", [flutter, "build", "apk", "--debug", "--no-pub"]))
    records, result = [], 0
    try:
        for index, (directory, command) in enumerate(commands, 1):
            print(f"Running Flutter check {index} in {directory}", flush=True)
            record = execute(command, 1200, output / f"flutter-{index}.log", ROOT / directory)
            records.append(dict(record, cwd=directory))
            if record["exit_code"]:
                result = int(record["exit_code"])
                return result
        return 0
    except OSError as error:
        result = 1
        records.append({"error": str(error), "exit_code": result})
        return result
    finally:
        (output / "summary.json").write_text(json.dumps({
            "evidence_class": "flutter-analysis-widget-tests-and-build",
            "android_runtime_executed": False, "checks": records, "exit_code": result,
        }, indent=2) + "\n")


if __name__ == "__main__":
    raise SystemExit(main())
