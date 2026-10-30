#!/usr/bin/env python3
"""Create a disposable denial-probe signer outside the repository; print only its env-file path."""
from __future__ import annotations

import argparse
import os
from pathlib import Path
import secrets
import shlex
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--directory", type=Path, help="New private directory outside the repository.")
    parser.add_argument("--github-env", type=Path, help="Append variables to the CI runner's environment file.")
    args = parser.parse_args()
    keytool = shutil.which("keytool")
    if not keytool:
        parser.error("keytool from JDK 17 is required")
    if args.directory:
        directory = args.directory.resolve()
        if directory.is_relative_to(ROOT):
            parser.error("signing directory must be outside the repository")
        directory.mkdir(mode=0o700, parents=False, exist_ok=False)
    else:
        directory = Path(tempfile.mkdtemp(prefix="boundarylab-denied-signing-")).resolve()
        if directory.is_relative_to(ROOT):
            directory.rmdir()
            parser.error("temporary directory must be outside the repository")
    store = directory / "denied-probe.p12"
    password = secrets.token_hex(32)
    environment = dict(os.environ, BOUNDARYLAB_DENIED_PASSWORD=password)
    result = subprocess.run([
        keytool, "-genkeypair", "-alias", "denial-probe", "-keyalg", "RSA", "-keysize", "2048",
        "-validity", "7", "-dname", "CN=BoundaryLab Disposable Denial Probe",
        "-storetype", "PKCS12", "-keystore", str(store),
        "-storepass:env", "BOUNDARYLAB_DENIED_PASSWORD", "-keypass:env", "BOUNDARYLAB_DENIED_PASSWORD",
        "-noprompt",
    ], env=environment, capture_output=True, timeout=30)
    if result.returncode:
        raise SystemExit(f"keytool generation failed (exit {result.returncode}); private output suppressed")
    store.chmod(0o600)
    values = {"BOUNDARYLAB_DENIED_KEYSTORE": str(store), "BOUNDARYLAB_DENIED_PASSWORD": password}
    environment_file = directory / "environment.sh"
    with environment_file.open("x", encoding="utf-8") as stream:
        environment_file.chmod(0o600)
        stream.write("".join(f"export {name}={shlex.quote(value)}\n" for name, value in values.items()))
    if args.github_env:
        with args.github_env.open("a", encoding="utf-8") as stream:
            stream.write("".join(f"{name}={value}\n" for name, value in values.items()))
    print(environment_file)


if __name__ == "__main__":
    main()
