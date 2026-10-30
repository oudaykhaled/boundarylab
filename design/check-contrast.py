"""Check normal-text WCAG contrast from the prototype's semantic CSS tokens."""
import re
from pathlib import Path

COLORS = dict(re.findall(r"--([a-z-]+):(#\w{6}|#fff);", Path(__file__).with_name("styles.css").read_text()))


def luminance(value):
    value = value.lstrip("#")
    if len(value) == 3:
        value = "".join(channel * 2 for channel in value)
    channels = [int(value[index:index + 2], 16) / 255 for index in (0, 2, 4)]
    channels = [x / 12.92 if x <= 0.04045 else ((x + 0.055) / 1.055) ** 2.4 for x in channels]
    return sum(x * y for x, y in zip(channels, (0.2126, 0.7152, 0.0722)))


pairs = [
    ("text", "surface"), ("muted", "surface"), ("muted", "bg"),
    ("surface", "navy"), ("surface", "accent"), ("accent", "success-bg"),
    ("danger", "danger-bg"), ("warning", "warning-bg"), ("focus", "surface"),
]
failed = False
for foreground, background in pairs:
    dark, light = sorted((luminance(COLORS[foreground]), luminance(COLORS[background])))
    ratio = (light + 0.05) / (dark + 0.05)
    print(f"{foreground}/{background}: {ratio:.2f}:1 {'PASS' if ratio >= 4.5 else 'FAIL'}")
    failed = failed or ratio < 4.5
raise SystemExit(1 if failed else 0)
