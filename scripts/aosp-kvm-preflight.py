#!/usr/bin/env python3
"""Actual non-root Linux/KVM suitability check; no guest code is executed."""
import fcntl
import json
import os
from pathlib import Path
import platform
import shutil
import stat


def require(condition, message):
    if not condition:
        raise SystemExit("PREFLIGHT FAILED: " + message)


require(os.geteuid() != 0, "run as the intended non-root build user")
require(platform.system() == "Linux" and platform.machine() == "x86_64", "Linux x86_64 required")
require((os.cpu_count() or 0) >= 16, "expected 16 virtual CPUs")
require("vmx" in Path("/proc/cpuinfo").read_text().split(), "nested Intel virtualization missing")
mem_kib = int(next(x.split()[1] for x in Path("/proc/meminfo").read_text().splitlines()
                   if x.startswith("MemTotal:")))
require(mem_kib * 1024 >= 60 * 1024**3, "nominal 64GB machine memory floor not met")
workspace = Path("/srv/aosp")
free = shutil.disk_usage(workspace).free
require(free >= 400_000_000_000, "less than 400GB workspace free")
require(stat.S_ISCHR(os.stat("/dev/kvm").st_mode), "KVM character device missing")
kvm = os.open("/dev/kvm", os.O_RDWR | os.O_CLOEXEC)
try:
    api = fcntl.ioctl(kvm, 0xAE00, 0)
    require(api == 12, "unexpected KVM API")
    vm = fcntl.ioctl(kvm, 0xAE01, 0)
    os.close(vm)
finally:
    os.close(kvm)
print(json.dumps({"preflight": "PASS", "system": platform.system(),
                  "architecture": platform.machine(), "kernel": platform.release(),
                  "cpus": os.cpu_count(), "memoryKiB": mem_kib,
                  "workspaceFreeBytes": free, "kvmApi": api,
                  "transientVmCreatedAndClosed": True, "guestBootProven": False}))
