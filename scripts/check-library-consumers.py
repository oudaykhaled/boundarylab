#!/usr/bin/env python3
"""Build independent consumers of already-published local BoundaryLab artifacts."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import shutil
import tempfile

from check import ROOT, execute


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", type=Path, default=ROOT / "build/local-maven")
    args = parser.parse_args()
    repository = args.repository.resolve()
    if not (repository / "dev/boundarylab/boundarylab-android").is_dir():
        parser.error("Publish libraries first with publishLibrariesToLocalRepository")
    output = ROOT / "build/evidence/library-consumers"
    output.mkdir(parents=True, exist_ok=True)
    records: list[dict[str, object]] = []
    result = 1
    try:
        with tempfile.TemporaryDirectory(prefix="boundarylab-consumers-") as temporary:
            consumers = Path(temporary).resolve()
            if consumers.is_relative_to(ROOT):
                raise ValueError("The consumer fixture must be outside the source checkout")
            for profile in ("jvm", "android"):
                shutil.copytree(ROOT / "examples/standalone" / profile, consumers / profile,
                                ignore=shutil.ignore_patterns("build", ".gradle", "local.properties"))
                task = "run" if profile == "jvm" else "assembleDebug"
                command = [str(ROOT / "gradlew"), "--no-daemon", "--console=plain",
                           "-p", str(consumers / profile),
                           f"-PboundarylabRepository={repository}", task]
                record = execute(command, 600, output / f"{profile}.log")
                records.append(dict(record, profile=profile, source_projects_included=False))
                result = int(record["exit_code"])
                if result:
                    return result
                if profile == "jvm" and "PUBLISHED_CORE_OK" not in (output / "jvm.log").read_text():
                    result = 1
                    records.append({"error": "JVM workload success marker missing", "exit_code": result})
                    return result
            result = 0
            return result
    except (OSError, ValueError) as error:
        result = 1
        records.append({"error": str(error), "exit_code": result})
        return result
    finally:
        (output / "summary.json").write_text(json.dumps({
            "evidence_class": "published-artifact-independent-consumers",
            "android_runtime_executed": False,
            "checks": records,
            "exit_code": result,
        }, indent=2) + "\n")


if __name__ == "__main__":
    raise SystemExit(main())
