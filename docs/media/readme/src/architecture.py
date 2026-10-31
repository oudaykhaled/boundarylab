#!/usr/bin/env python3
"""Generate the explanatory BoundaryLab architecture SVG without dependencies.

The diagram describes code ownership; it is not a runtime screenshot.
Sources (relative to the repository root):
  docs/ARCHITECTURE.md; docs/AOSP-LAYERS.md; aosp/README.md
  android/broker/src/main/kotlin/dev/boundarylab/broker/BoundaryBrokerService.kt
  sdk/flutter/android/src/main/kotlin/dev/boundarylab/flutter/BoundaryLabPlugin.kt
  desktop/host/src/main/kotlin/dev/boundarylab/desktop/Host.kt
  aosp/client/Client.cpp; aosp/service/main.cpp; aosp/service/boundarylab.rc
  aosp/sepolicy/private/boundarylab.te

Run from any directory: python3 docs/media/readme/src/architecture.py
"""

from html import escape
from pathlib import Path


OUT = Path(__file__).resolve().parents[1] / "architecture.svg"
W, H = 1400, 880
NAVY = "#09111f"
TEAL = "#37dfc5"
LIGHT = "#e7eef7"
MUTED = "#a5b7cc"
AMBER = "#ffcd73"
PARTS = []


def rect(x, y, w, h, fill="#111f32", stroke="#30465f", radius=18):
    PARTS.append(
        f'<rect x="{x}" y="{y}" width="{w}" height="{h}" '
        f'rx="{radius}" fill="{fill}" stroke="{stroke}" stroke-width="2"/>'
    )


def text(x, y, label, size=24, color=LIGHT, weight=400, anchor="start"):
    PARTS.append(
        f'<text x="{x}" y="{y}" font-size="{size}" fill="{color}" '
        f'font-weight="{weight}" text-anchor="{anchor}">{escape(label)}</text>'
    )


def line(x1, y1, x2, y2, color=TEAL, arrow=False, dashed=False):
    dash = ' stroke-dasharray="6 7"' if dashed else ""
    tip = ' marker-end="url(#arrow)"' if arrow else ""
    PARTS.append(
        f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" '
        f'stroke="{color}" stroke-width="3"{dash}{tip}/>'
    )


PARTS.append(f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}" role="img" aria-labelledby="title desc">
<title id="title">BoundaryLab architecture: application, desktop and native AOSP profiles</title>
<desc id="desc">Explanatory architecture, not a runtime screenshot. Android native and Flutter clients use the Kotlin SDK or Flutter bridge to call an isolated app Binder broker with its own Kotlin/JVM core. A separate macOS CLI calls a per-user JVM host through a same-user Unix-domain socket. The Android 16 custom AOSP image has a separate native C++ facade with frozen V1 CPP AIDL, using the existing Binder driver to call a UID 9999 daemon and generated-only C++ engine. The authorized native probe has UID 2000; the denied probe has UID 9999. Init supervises the daemon, and all three BoundaryLab SELinux domains are enforcing. The Java framework, system_server and kernel are unchanged.</desc>
<defs><marker id="arrow" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto" markerUnits="userSpaceOnUse"><path d="M0 0L8 4L0 8Z" fill="{TEAL}"/></marker></defs>
<g font-family="Arial, Helvetica, sans-serif">
<rect width="{W}" height="{H}" fill="{NAVY}"/>
''')

text(48, 65, "Architecture with explicit boundaries", 44, LIGHT, 700)
text(48, 108, "Explanatory diagram · distinct application and custom AOSP integrations", 26, MUTED)

# Application profile: client and broker are separate Android processes.
rect(42, 145, 1316, 294, "#0d1829", "#263b54", 24)
text(64, 187, "APPLICATION PROFILE", 28, TEAL, 700)
text(1121, 187, "macOS", 26, TEAL, 700, "middle")

rect(64, 220, 256, 130)
text(192, 264, "Android native", 30, LIGHT, 700, "middle")
text(192, 300, "Flutter client", 30, LIGHT, 700, "middle")
text(192, 332, "Separate client apps", 24, MUTED, 400, "middle")

rect(360, 220, 210, 130)
text(465, 264, "Kotlin SDK", 30, LIGHT, 700, "middle")
text(465, 300, "Flutter bridge", 24, MUTED, 400, "middle")
text(465, 332, "Worker IPC", 24, MUTED, 400, "middle")

rect(612, 220, 270, 130, "#102b31", "#37dfc5")
text(747, 264, "App broker", 30, LIGHT, 700, "middle")
text(747, 300, "Kotlin/JVM core", 27, LIGHT, 400, "middle")
text(747, 332, "Own process", 24, MUTED, 400, "middle")
line(323, 285, 352, 285, arrow=True)
line(573, 285, 604, 285, arrow=True)
line(591, 212, 591, 358, AMBER, dashed=True)
text(64, 397, "Signature permission + Binder UID → owner-bound session", 26, MUTED)

# No transport arrow joins desktop and Android: only source policy is reused.
rect(916, 209, 410, 202, "#111f32", "#30465f")
text(1121, 250, "Desktop CLI", 30, LIGHT, 700, "middle")
line(1121, 266, 1121, 289, arrow=True)
text(1121, 325, "Per-user JVM host", 30, LIGHT, 700, "middle")
text(1121, 360, "Own Kotlin/JVM core", 26, LIGHT, 400, "middle")
text(1121, 394, "Same-user Unix socket", 24, MUTED, 400, "middle")

# Custom AOSP profile: separately ported native implementation, not the app broker.
rect(42, 467, 1316, 320, "#0d1829", "#263b54", 24)
text(64, 509, "AOSP 16 CUSTOM IMAGE", 30, TEAL, 700)
text(640, 509, "Native platform profile · system_ext", 26, MUTED)

rect(64, 548, 248, 130)
text(188, 588, "Native probes", 30, LIGHT, 700, "middle")
text(188, 623, "UID 2000: allowed", 24, TEAL, 400, "middle")
text(188, 657, "UID 9999: DENIED", 24, AMBER, 400, "middle")

rect(356, 548, 266, 130)
text(489, 588, "C++ facade", 30, LIGHT, 700, "middle")
text(489, 623, "Frozen V1 CPP AIDL", 24, LIGHT, 400, "middle")
text(489, 657, "Platform-built clients", 24, MUTED, 400, "middle")

rect(666, 548, 176, 130)
text(754, 588, "Binder", 30, LIGHT, 700, "middle")
text(754, 623, "Existing", 24, MUTED, 400, "middle")
text(754, 657, "kernel driver", 24, MUTED, 400, "middle")

rect(886, 548, 440, 130, "#102b31", "#37dfc5")
text(1106, 588, "Native Binder daemon", 30, LIGHT, 700, "middle")
text(1106, 623, "UID 9999 · dedicated domain", 24, LIGHT, 400, "middle")
text(1106, 657, "C++ engine · generated-only", 26, LIGHT, 400, "middle")
line(315, 613, 348, 613, arrow=True)
line(625, 613, 658, 613, arrow=True)
line(845, 613, 878, 613, arrow=True)
line(864, 538, 864, 686, AMBER, dashed=True)

text(64, 724, "UID gate + owner/session checks", 26, MUTED)
text(64, 760, "Three BoundaryLab domains: Enforcing", 26, MUTED)
text(834, 724, "Init boot + bounded restart", 26, MUTED)
text(834, 760, "1 worker · 64 KiB buffer", 26, MUTED)

line(48, 815, 1352, 815, "#263b54")
text(48, 852, "Java framework, system_server and kernel unchanged · See docs/AOSP-LAYERS.md", 26, MUTED)
PARTS.append("</g></svg>\n")
OUT.write_text("\n".join(PARTS), encoding="utf-8")
print(f"Wrote {OUT} ({W}×{H})")
