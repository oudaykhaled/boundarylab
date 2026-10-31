#!/usr/bin/env python3
"""Render README charts from receipt-derived data using only Python's stdlib.

Run from any directory: python3 docs/media/readme/src/charts.py
Use --check to verify deterministic output without changing the SVG files.
The source dataset, receipt references, and input hashes are embedded in metadata.
"""

from __future__ import annotations

import argparse
from datetime import datetime
from hashlib import sha256
from html import escape
import json
from pathlib import Path
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[4]
MEDIA = ROOT / "docs/media/readme"
DATA = MEDIA / "data/run-summary.json"
REPOSITORY = "https://github.com/oudaykhaled/boundarylab/blob/main/"
WIDTH = 1400
NAVY = "#09111f"
TEAL = "#37dfc5"
LIGHT = "#e7eef7"
MUTED = "#a5b7cc"
AMBER = "#ffcd73"
PANEL = "#111f32"
LINE = "#2d425b"


def value(metric: dict) -> object:
    return metric["value"]


def text(x: float, y: float, content: object, size: int = 28,
         color: str = LIGHT, weight: int = 400, anchor: str = "start") -> str:
    return (
        f'<text x="{x:g}" y="{y:g}" fill="{color}" font-size="{size}" '
        f'font-weight="{weight}" text-anchor="{anchor}">'
        f'{escape(str(content))}</text>'
    )


def rect(x: float, y: float, width: float, height: float,
         fill: str, radius: int = 16, stroke: str | None = None) -> str:
    outline = f' stroke="{stroke}" stroke-width="2"' if stroke else ""
    return (
        f'<rect x="{x:g}" y="{y:g}" width="{width:g}" height="{height:g}" '
        f'rx="{radius}" fill="{fill}"{outline}/>'
    )


def source_links(node: object) -> list[dict]:
    """Keep each original citation and add a browser-readable source URL."""
    result = []
    if isinstance(node, dict):
        for citation in node.get("sources", []):
            linked = dict(citation)
            linked["url"] = REPOSITORY + citation["path"]
            if "lines" in citation:
                start, end = citation["lines"]
                linked["url"] += f"#L{start}-L{end}"
            if linked not in result:
                result.append(linked)
        for key, item in node.items():
            if key != "sources":
                for citation in source_links(item):
                    if citation not in result:
                        result.append(citation)
    elif isinstance(node, list):
        for item in node:
            for citation in source_links(item):
                if citation not in result:
                    result.append(citation)
    return result


def document(title: str, description: str, height: int, body: list[str],
             data: dict, relevant: object) -> str:
    metadata = {
        "renderer": "docs/media/readme/src/charts.py (Python stdlib SVG)",
        "dataset": "docs/media/readme/data/run-summary.json",
        "dataset_sha256": sha256(DATA.read_bytes()).hexdigest(),
        "source_files": data["sourceFiles"],
        "citations": source_links(relevant),
        "chart_scope": description,
        "transformation": "Static presentation of recorded values; no new execution or measurement.",
    }
    return "\n".join([
        '<?xml version="1.0" encoding="UTF-8"?>',
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{WIDTH}" height="{height}" '
        f'viewBox="0 0 {WIDTH} {height}" role="img" aria-labelledby="title desc">',
        f'<title id="title">{escape(title)}</title>',
        f'<desc id="desc">{escape(description)}</desc>',
        '<metadata>' + escape(json.dumps(metadata, sort_keys=True)) + '</metadata>',
        '<g font-family="ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, '
        'Segoe UI, sans-serif">',
        rect(0, 0, WIDTH, height, NAVY, 24),
        *body,
        '</g>',
        '</svg>',
        '',
    ])


def header(kicker: str, title: str, subtitle: str) -> list[str]:
    return [
        text(48, 48, kicker, 26, TEAL, 700),
        text(48, 105, title, 46, LIGHT, 700),
        text(48, 153, subtitle, 28, MUTED),
    ]


def evidence(data: dict) -> str:
    counts = data["counts"]
    identity = data["identity"]
    native_cases = int(value(counts["nativeAssertions"]))
    if (value(identity["globalSelinuxBefore"]) != "Enforcing"
            or value(identity["globalSelinuxAfter"]) != "Enforcing"
            or len(value(identity["nonpermissiveBoundaryLabDomains"])) != 3
            or "su listed permissive" not in str(value(identity["nativeExecutionIdentity"]))):
        raise ValueError("Revisit the visible execution-scope captions for changed identity data")
    specs = [
        ("operatorPassed", "operatorChecks", "Operator checks",
         "Boot, identity, orchestration", "and cleanup checks."),
        ("harnessPassed", "harnessObservations", "Harness observations",
         "Repeated case names occur.", "These are not unique tests."),
        ("nativePassed", "nativeAssertions", "Native assertions",
         f"{native_cases} unique engine cases.", "One native test execution."),
    ]
    body = header("ACTUAL AOSP R6 RECEIPTS · 30 OCT 2026 UTC",
                  "Three evidence layers. Three separate counts.",
                  "The layers overlap. Their totals must not be added together.")
    card_width = (WIDTH - 96 - 48) / 3
    for index, (passed, total, label, first, second) in enumerate(specs):
        x = 48 + index * (card_width + 24)
        actual, denominator = int(value(counts[passed])), int(value(counts[total]))
        state = "PASSED" if actual == denominator else "PARTIAL"
        body.extend([
            rect(x, 190, card_width, 290, PANEL, 18, LINE),
            text(x + 26, 232, state, 26, TEAL, 700),
            text(x + 26, 315, f"{actual} / {denominator}", 72, LIGHT, 700),
            text(x + 26, 364, label, 30, LIGHT, 650),
            text(x + 26, 414, first, 26, MUTED),
            text(x + 26, 452, second, 26, MUTED),
        ])
    body.extend([
        rect(48, 514, 1304, 140, PANEL, 16),
        text(74, 555, "Guest SELinux: Enforcing", 30, TEAL, 650),
        text(74, 598, "All three BoundaryLab security domains were nonpermissive.", 28),
        text(74, 636, "Native assertions ran under platform su, which was permissive.", 28, AMBER),
        text(48, 700, "Source: recorded r6 operator, harness and native JSONL receipts.", 26, MUTED),
    ])
    description = (
        "Actual AOSP custom-image r6 proof recorded on 30 October 2026 UTC: "
        "34 of 34 operator checks, 236 of 236 harness observations including repeated case names, "
        "and 61 of 61 unique native assertion cases. These evidence layers overlap and must not "
        "be summed. Guest SELinux was Enforcing and the three BoundaryLab domains were "
        "nonpermissive. The native engine test ran once under permissive platform su."
    )
    return document("Actual AOSP evidence: separate recorded counts", description,
                    736, body, data, {"counts": counts, "identity": identity})


def cost(data: dict) -> str:
    costs = data["cost"]
    estimate = float(value(costs["infrastructureEstimateUsd"]))
    reserve = float(value(costs["reservedEstimateUsd"]))
    budget = float(value(costs["budgetUsd"]))
    body = header("BOUNDED CLOUD ATTEMPT · 30 OCT 2026 UTC",
                  f"A ${budget:g} authorization. A ${estimate:.2f} infrastructure estimate.",
                  "Estimate through verified deletion; the settled provider invoice is not available.")
    x_bar, bar_width = 520, 680
    rows = [
        ("Infrastructure estimate", estimate, TEAL, 230),
        ("With conservative reserve", reserve, AMBER, 340),
        ("Authorized total cap", budget, MUTED, 450),
    ]
    for label, amount, color, y in rows:
        body.extend([
            text(48, y + 4, label, 30),
            rect(x_bar, y - 24, bar_width, 34, LINE, 8),
            rect(x_bar, y - 24, bar_width * amount / budget, 34, color, 8),
            text(1352, y + 6, f"${amount:,.2f}", 36, color, 700, "end"),
        ])
    body.extend([
        text(x_bar, 507, "$0", 26, MUTED),
        text(x_bar + bar_width, 507, "$50 USD", 26, MUTED, anchor="end"),
        rect(48, 549, 1304, 104, PANEL, 16),
        text(74, 590, f"${reserve:.2f} includes a $2 egress allowance and 40% tax/FX reserve.", 28),
        text(74, 632, "VM + both disks verified absent at 22:41:46 UTC. Cost clock stopped.", 28, TEAL),
        text(48, 697, "Source: retained cost-estimate.json and provider teardown receipts.", 26, MUTED),
    ])
    description = (
        "Recorded estimate for the 30 October 2026 bounded Google Cloud attempt: "
        f"infrastructure estimate USD {estimate:.4f}; conservative projection USD {reserve:.4f} "
        "including a USD 2 egress allowance and 40 percent tax/FX reserve; authorized cap USD 50. "
        "The amounts are estimates, not a settled provider invoice. VM and both disks were "
        "verified absent at 22:41:46 UTC and the cost clock stopped."
    )
    return document("AOSP cloud attempt: estimated cost against authorized cap", description,
                    736, body, data, {"cost": costs, "teardown": data["teardown"]})


def utc_clock(metric: dict) -> str:
    return datetime.fromisoformat(str(value(metric))).strftime("%H:%M:%S")


def timeline(data: dict) -> str:
    events = data["timeline"]
    height = 220 + len(events) * 142 + 130
    labels = {
        "baseline-build": ("Unchanged AOSP source build", "Resumed r5 using existing outputs; not a clean-build duration."),
        "baseline-boot": ("Unchanged baseline boot + cleanup", "Separate proof before the BoundaryLab overlay."),
        "native-r5": ("Final incremental native build", "Final source revision; native-r5 exited successfully."),
        "image-r4": ("Final incremental image package", "Image-r4 packaged the custom Cuttlefish image."),
        "runtime-r6": ("Custom image boot + runtime proof", "R6 combined proof and guest cleanup passed."),
        "export-verified": ("Evidence export verified locally", "Hash verification completed before deleting the server."),
        "charged-resources-deleted": ("VM + both disks verified absent", "Provider responses confirm attempt resources absent."),
        "teardown-verified": ("Attempt teardown verified", "Final recorded resource lists were empty."),
    }
    body = header("RECORDED EXECUTION TIMELINE · 30 OCT 2026 UTC",
                  "Built on Linux. Executed in Cuttlefish. Cleaned up.",
                  "Actual receipt timestamps, shown as ordered milestones — not performance results.")
    line_end = 218 + (len(events) - 1) * 142 + 38
    body.append(f'<path d="M 82 252 V {line_end}" stroke="{LINE}" stroke-width="4"/>')
    for index, event in enumerate(events):
        y = 210 + index * 142
        label, scope = labels[event["id"]]
        started = event["startedUtc"]
        ended = event["endedUtc"]
        when = f'{utc_clock(started)} → {utc_clock(ended)}' if started else utc_clock(ended)
        is_passed = bool(value(event["passed"]))
        body.extend([
            rect(116, y, 1236, 116, PANEL, 16),
            f'<circle cx="82" cy="{y + 38}" r="22" fill="{NAVY}" stroke="{TEAL}" stroke-width="3"/>',
            text(82, y + 48, str(index + 1), 26, TEAL, 700, "middle"),
            text(144, y + 42, label, 30, LIGHT, 650),
            text(1324, y + 42, when, 30, TEAL if is_passed else AMBER, 650, "end"),
            text(144, y + 87, scope, 27, MUTED),
        ])
    bottom = 210 + len(events) * 142
    body.extend([
        text(48, bottom + 32, "Five failed runtime attempts (r1–r5) are retained beside the passing r6.", 28, AMBER),
        text(48, bottom + 76, "Selected final milestones; intervening revisions and diagnostics remain in the evidence.", 26, MUTED),
    ])
    description = (
        "Selected actual UTC milestones on 30 October 2026 from recorded receipts: unchanged "
        "AOSP source build and baseline boot, final incremental native-r5 and image-r4 builds, "
        "passing r6 custom-image runtime proof, local evidence export verification, verified "
        "VM and disk deletion, and final teardown. Milestones are equally spaced for readability; "
        "positions and elapsed times are not benchmark results. Failed runtime attempts r1 "
        "through r5 are retained. This is not a complete transcript of every revision."
    )
    return document("Actual Linux AOSP build, Cuttlefish proof, export and teardown timeline",
                    description, height, body, data, events)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Verify deterministic SVG bytes without rewriting")
    args = parser.parse_args()
    data = json.loads(DATA.read_text())
    renderers = {
        "evidence-results.svg": evidence,
        "cost.svg": cost,
        "run-timeline.svg": timeline,
    }
    for name, renderer in renderers.items():
        contents = renderer(data)
        ET.fromstring(contents)
        path = MEDIA / name
        if args.check:
            if not path.exists() or path.read_text() != contents:
                raise SystemExit(f"Out of date: {path.relative_to(ROOT)}")
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(contents)
        print(f"{'Verified' if args.check else 'Rendered'} {path.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
