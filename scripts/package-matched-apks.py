#!/usr/bin/env python3
"""Verify one signer across broker/native/Flutter debug APKs and package public provenance."""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "build/evidence/matched-client-suite"
APKS = {
    "broker": "android/broker/build/outputs/apk/debug/broker-debug.apk",
    "native-client": "examples/android-native/build/outputs/apk/debug/android-native-debug.apk",
    "flutter-client": "examples/flutter/build/app/outputs/flutter-apk/app-debug.apk",
}


def command(args: list[str]) -> str:
    result = subprocess.run(args, cwd=ROOT, capture_output=True, text=True, timeout=60)
    if result.returncode:
        raise RuntimeError(f"{Path(args[0]).name} failed (exit {result.returncode}); package not published")
    return result.stdout


def certificate(apksigner: Path, apk: Path) -> str:
    output = command([str(apksigner), "verify", "--verbose", "--print-certs", str(apk)])
    digests = re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})$", output, re.MULTILINE)
    if len(digests) != 1:
        raise RuntimeError("Expected exactly one verified APK signer")
    return digests[0].lower()


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(65536), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> None:
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk:
        raise RuntimeError("ANDROID_HOME or ANDROID_SDK_ROOT is required")
    apksigner = Path(sdk) / "build-tools/35.0.0/apksigner"
    if OUTPUT.exists():
        raise RuntimeError("Matched package output already exists; use a fresh build workspace")
    fingerprints = {name: certificate(apksigner, ROOT / relative) for name, relative in APKS.items()}
    if len(set(fingerprints.values())) != 1:
        raise RuntimeError("APK signing certificates differ; refusing a matched-client artifact")
    flutter = json.loads(command([os.environ.get("FLUTTER_BIN", "flutter"), "--version", "--machine"]))
    java = subprocess.run(["java", "-version"], capture_output=True, text=True, check=True, timeout=30)
    catalog = (ROOT / "gradle/libs.versions.toml").read_text()
    versions = dict(re.findall(r'^([\w-]+)\s*=\s*"([^"]+)"\s*$', catalog, re.MULTILINE))
    wrapper = (ROOT / "gradle/wrapper/gradle-wrapper.properties").read_text()
    gradle_version = re.search(r"gradle-([\d.]+)-bin.zip", wrapper)
    if not gradle_version:
        raise RuntimeError("Cannot identify pinned Gradle version")
    provenance = {
        "schemaVersion": 1,
        "evidenceClass": "same-runner-debug-build-signer-verification",
        "androidRuntimeExecuted": False,
        "sourceCommit": command(["git", "rev-parse", "HEAD"]).strip(),
        "trackedSourceDirty": bool(command(["git", "diff", "--name-only", "HEAD"]).strip()),
        "signingCertificateSha256": next(iter(fingerprints.values())),
        "signingProfile": "one ephemeral runner default debug signer; private key excluded",
        "toolchain": {
            "gradle": gradle_version.group(1), "agp": versions["agp"], "kotlin": versions["kotlin"],
            "compileSdk": versions["compile-sdk"], "minSdk": versions["min-sdk"], "buildTools": "35.0.0",
            "java": (java.stderr or java.stdout).splitlines()[0],
            "flutter": {key: flutter[key] for key in ("frameworkVersion", "frameworkRevision", "engineRevision", "dartSdkVersion")},
            "os": platform.system(), "architecture": platform.machine(),
        },
        "apks": [],
        "limitations": ["Debug build set only; no installed journey or production release claim.",
                        "Do not combine with standalone-build-check artifacts from other jobs."],
    }
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    staging = Path(tempfile.mkdtemp(prefix="matched-apks-", dir=OUTPUT.parent))
    try:
        for name, relative in APKS.items():
            destination = staging / f"{name}-debug.apk"
            shutil.copyfile(ROOT / relative, destination)
            provenance["apks"].append({"role": name, "file": destination.name, "sha256": sha256(destination),
                                       "signingCertificateSha256": fingerprints[name]})
        (staging / "provenance.json").write_text(json.dumps(provenance, indent=2) + "\n")
        staging.rename(OUTPUT)
    finally:
        if staging.exists():
            shutil.rmtree(staging)
    print(json.dumps({"result": "MATCHED_3_APK_BUILD_SET", "signingCertificateSha256": provenance["signingCertificateSha256"],
                      "sourceCommit": provenance["sourceCommit"], "apkCount": 3}))


if __name__ == "__main__":
    main()
