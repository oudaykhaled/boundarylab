#!/usr/bin/env python3
"""Render the evidence-backed server/guest diagram using only Python's stdlib.

Run from any directory: python3 docs/media/readme/src/server_pipeline.py
The source receipts are read, never changed. This is an explanatory diagram,
not a screenshot or a claim of an independently attested execution.
"""

from __future__ import annotations

import hashlib
import json
import re
from datetime import datetime
from html import escape
from pathlib import Path


ROOT = Path(__file__).resolve().parents[4]
OUTPUT = Path(__file__).resolve().parents[1] / "server-to-guest-pipeline.svg"
RECEIPTS = {
    "host": "docs/evidence/aosp/attempt-20261030/host-kvm.json",
    "cloud": "docs/evidence/aosp/attempt-20261030/cloud-controls.json",
    "baseline": "docs/evidence/P02/r5-source-baseline/build-result.json",
    "baseline_boot": "docs/evidence/P02/r5-source-baseline/boot/result.json",
    "native_log": "docs/evidence/aosp/custom-20261030/build/native-r5/build.log",
    "custom_image": "docs/evidence/aosp/custom-20261030/build/image-r4/build-result.json",
    "runtime": "docs/evidence/aosp/custom-20261030/actual-results.json",
    "teardown": "docs/evidence/aosp/teardown-20261030/teardown.json",
}


def read_json(name: str) -> dict:
    return json.loads((ROOT / RECEIPTS[name]).read_text())


def utc_time(timestamp: str) -> str:
    return datetime.fromisoformat(timestamp).strftime("%H:%M:%S UTC")


def main() -> None:
    host, cloud = read_json("host"), read_json("cloud")
    baseline, boot = read_json("baseline"), read_json("baseline_boot")
    image, runtime = read_json("custom_image"), read_json("runtime")
    teardown = read_json("teardown")
    native_log = (ROOT / RECEIPTS["native_log"]).read_text()
    ubuntu_match = re.search(r"HOST_OS_EXTRA=.*Ubuntu-(\d+\.\d+\.\d+)-LTS", native_log)
    if ubuntu_match is None:
        raise ValueError("Actual native build log does not identify the Ubuntu release")
    assert "HOST_OS=linux" in native_log
    assert host["system"] == "Linux" and host["architecture"] == "x86_64"
    assert host["transientVmCreatedAndClosed"] is True
    assert cloud["machine_type"] == "n2-standard-16"
    assert baseline["exit_code"] == 0 and baseline["overlay_present"] is False
    assert boot["passed"] and all(check["passed"] for check in boot["checks"])
    assert image["exit_code"] == 0 and image["overlay_present"] is True
    assert runtime["passed"] and runtime["cleanup_verified"]
    assert runtime["policy"]["permissive_domains"] == ["su"]
    assert len(runtime["policy"]["boundarylab_domains_checked_nonpermissive"]) == 3
    assert teardown["instance_absent"] and teardown["both_disks_absent"]
    assert teardown["evidence_verified_before_deletion"]
    assert all(not resources for resources in teardown["final_resource_lists"].values())

    evidence = {
        "kind": "explanatory_diagram_from_retained_receipts",
        "run_date": "2026-10-30",
        "source_commit": runtime["source_commit"],
        "facts": {
            "linux_ubuntu_lts": ubuntu_match.group(1),
            "baseline_build_end_utc": baseline["ended_utc"],
            "custom_image_build_end_utc": image["ended_utc"],
            "runtime_end_utc": runtime["ended_utc"],
            "charged_resources_absent_since_utc": teardown["charged_resources_absent_since_utc"],
        },
        "sources": {
            key: {
                "path": path,
                "sha256": hashlib.sha256((ROOT / path).read_bytes()).hexdigest(),
            }
            for key, path in RECEIPTS.items()
        },
        "limits": [
            "Native unit binary ran as su, which is a permissive domain.",
            "236 harness observations include repeated case names; counts are not added together.",
            "Baseline and custom builds reused source/output state; no fresh full reproduction claim.",
            "The diagram explains retained operator receipts, not independent execution attestation.",
        ],
    }
    elements: list[str] = []

    def add(markup: str) -> None:
        elements.append(markup)

    def rect(x: int, y: int, width: int, height: int, fill: str, stroke: str, radius: int = 20) -> None:
        add(f'<rect x="{x}" y="{y}" width="{width}" height="{height}" rx="{radius}" fill="{fill}" stroke="{stroke}" stroke-width="2"/>')

    def text(x: int, y: int, label: str, size: int = 24, fill: str = "#e7eef7", weight: int = 400) -> None:
        add(f'<text x="{x}" y="{y}" font-size="{size}" font-weight="{weight}" fill="{fill}">{escape(label)}</text>')

    add('<svg xmlns="http://www.w3.org/2000/svg" width="1500" height="880" viewBox="0 0 1500 880" role="img" aria-labelledby="title description">')
    add('<title id="title">BoundaryLab: built on a Linux server, tested inside a real Android 16 Cuttlefish guest</title>')
    add('<desc id="description">Explanatory diagram from retained 30 October 2026 receipts. A Google Cloud Ubuntu 24.04.5 x86_64 n2-standard-16 host with 16 virtual CPUs, 64 GiB nominal memory and KVM compiled the pinned Android 16 r4 unchanged baseline, then a custom system_ext native-service image. The nested Cuttlefish Android 16 API 36 guest executed authorized and denied Binder calls, 61 unique native assertion cases under the separate permissive su domain, 236 harness observations including repeats, and 34 operator checks. Evidence was exported and locally hash verified before the VM and both disks were deleted.</desc>')
    add(f'<metadata>{escape(json.dumps(evidence, sort_keys=True, indent=2))}</metadata>')
    add('<g font-family="system-ui, -apple-system, Segoe UI, sans-serif">')
    add('<rect width="1500" height="880" fill="#09111f"/>')
    text(40, 65, "Built on Linux. Tested inside Android.", 52, weight=700)
    text(42, 109, "Executed 30 Oct 2026 · explanatory diagram from retained receipts", 26, "#a5b7cc")

    # The outer boundary is the actual cloud Linux host; Android is nested inside it.
    rect(40, 142, 1420, 510, "#101d30", "#2b425b", 24)
    rect(69, 165, 44, 35, "#15333b", "#37dfc5", 7)
    add('<path d="M77 177h28M77 189h28" fill="none" stroke="#37dfc5" stroke-width="3"/>')
    text(130, 192, "GOOGLE CLOUD / LINUX HOST", 32, "#37dfc5", 700)
    text(70, 230, f"Ubuntu {ubuntu_match.group(1)} LTS · x86_64 · n2-standard-16 · 16 vCPU / 64 GiB nominal · KVM API {host['kvmApi']}", 26, "#a5b7cc")
    add('<path d="M70 249H1430" fill="none" stroke="#2b425b" stroke-width="2"/>')

    rect(70, 268, 570, 348, "#0b1524", "#2b425b")
    text(94, 309, "1 / SOONG SOURCE BUILDS", 30, weight=700)
    text(94, 343, baseline["source_manifest"], 26, "#37dfc5", 600)
    rect(94, 362, 522, 87, "#142337", "#294157", 12)
    text(115, 397, "Unchanged source baseline", 30, weight=650)
    text(115, 430, f"Built + booted before overlay · {len(boot['checks'])} checks", 24, "#a5b7cc")
    rect(94, 465, 522, 126, "#142337", "#294157", 12)
    text(115, 502, "Custom system_ext image", 30, weight=650)
    text(115, 538, "Native service + stable AIDL V1", 24, "#a5b7cc")
    text(115, 571, "init service + SELinux policy", 24, "#a5b7cc")

    add('<path d="M656 435H715" fill="none" stroke="#37dfc5" stroke-width="3"/>')
    add('<path d="M709 428L720 435L709 442" fill="none" stroke="#37dfc5" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>')
    text(656, 413, "boot", 24, "#37dfc5", 600)
    rect(738, 268, 692, 348, "#102a31", "#37dfc5")
    text(765, 309, "2 / CUTTLEFISH ANDROID GUEST", 30, "#37dfc5", 700)
    text(765, 343, "Android 16 · API 36 · actual built images", 26, "#e7eef7")
    add('<path d="M766 362H1402" fill="none" stroke="#285051" stroke-width="2"/>')
    text(765, 401, "Real Binder calls", 30, weight=650)
    text(765, 438, f"UID {runtime['identities']['authorized_binder_uid']} → SUCCEEDED", 24, "#37dfc5", 600)
    text(1120, 438, f"UID {runtime['identities']['denied_fixture_uid']} → DENIED", 24, "#ffcd73", 600)
    text(765, 480, f"{runtime['native_assertions']['unique_cases']} native assertion cases passed", 30, weight=650)
    text(765, 512, "Unit binary: su domain (permissive).", 24, "#ffcd73")
    text(765, 546, f"{runtime['harness_observations']['count']} harness observations, including repeats", 24, "#a5b7cc")
    text(765, 578, "Guest Enforcing; BoundaryLab domains nonpermissive.", 24, "#a5b7cc")
    text(765, 606, f"{runtime['operator_checks']['count']} operator checks · cleanup verified", 24, "#37dfc5", 600)

    # Evidence leaves the deleted host; it is retained in the public repository.
    add('<path d="M1084 618V666H385V680" fill="none" stroke="#37dfc5" stroke-width="2.5" stroke-linejoin="round"/>')
    add('<path d="M378 675L385 685L392 675" fill="none" stroke="#37dfc5" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>')
    rect(40, 696, 690, 138, "#102331", "#285051")
    text(70, 736, "3 / EXPORT & VERIFY", 30, "#37dfc5", 700)
    text(70, 773, "SHA-256 archive + raw receipts + original PNGs", 24)
    text(70, 808, f"Final runtime passed {utc_time(runtime['ended_utc'])}", 24, "#a5b7cc")
    add('<path d="M738 763H756M750 757L759 763L750 769" fill="none" stroke="#37dfc5" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>')
    rect(770, 696, 690, 138, "#152730", "#52715d")
    text(800, 736, "4 / TEARDOWN VERIFIED", 30, "#37dfc5", 700)
    text(800, 773, f"VM + both disks absent at {utc_time(teardown['charged_resources_absent_since_utc'])}", 24)
    text(800, 808, "No retained VM, disks, snapshots or addresses", 24, "#a5b7cc")
    text(41, 867, "Operator-recorded evidence + local hash integrity; no independent execution attestation.", 24, "#a5b7cc")
    add('</g></svg>')
    OUTPUT.write_text("\n".join(elements) + "\n")
    print(OUTPUT.relative_to(ROOT))


if __name__ == "__main__":
    main()
