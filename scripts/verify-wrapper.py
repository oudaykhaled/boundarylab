#!/usr/bin/env python3
"""Check the trusted Gradle 8.13 wrapper JAR before executing it."""
import hashlib
from pathlib import Path

root = Path(__file__).resolve().parents[1]
expected = "81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f"
actual = hashlib.sha256((root / "gradle/wrapper/gradle-wrapper.jar").read_bytes()).hexdigest()
if actual != expected:
    raise SystemExit(f"Gradle wrapper checksum mismatch: {actual}")
print(f"Gradle 8.13 wrapper SHA-256 verified: {actual}")
