#!/usr/bin/env python3
"""Render the sourced AOSP replay and static poster; never execute product/cloud tests.

Dependency: Pillow (tested with 12.2.0), plus a local sans and monospace font.
Run from any directory: python3 docs/media/readme/src/proof_replay.py
Use --validate-only to check all selected source hashes and literal excerpt values.
Inputs are immutable repository receipts and data/replay-scenes.json. Output text
is selected, wrapped and typeset; no original evidence or screenshot is edited.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any

from PIL import Image, ImageDraw, ImageFont

MEDIA = Path(__file__).resolve().parents[1]
ROOT = MEDIA.parents[2]
DATA = MEDIA / "data/replay-scenes.json"
WIDTH, HEIGHT = 1440, 810
COLORS = {"bg": "#09111f", "panel": "#101f32", "edge": "#284055", "teal": "#37dfc5", "white": "#e7eef7", "muted": "#a5b7cc", "amber": "#ffcd73"}


def font(size: int, mono: bool = False) -> ImageFont.FreeTypeFont:
    paths = (["/System/Library/Fonts/Menlo.ttc", "/usr/share/fonts/truetype/liberation2/LiberationMono-Regular.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf"] if mono else ["/System/Library/Fonts/Helvetica.ttc", "/usr/share/fonts/truetype/liberation2/LiberationSans-Regular.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"])
    for path in paths:
        if Path(path).is_file():
            return ImageFont.truetype(path, size)
    raise RuntimeError("No supported local font. Install a local sans/mono font or update the font candidates.")


def at_pointer(value: Any, pointer: str) -> Any:
    for part in pointer.strip("/").split("/"):
        part = part.replace("~1", "/").replace("~0", "~")
        value = value[int(part)] if isinstance(value, list) else value[part]
    return value


def validate(data: dict[str, Any]) -> None:
    assert data["permanent_label"] == "Animated evidence replay — not a live recording"
    assert sum(s["duration_ms"] for s in data["scenes"]) == data["duration_ms"]
    for scene in data["scenes"]:
        for source in scene["sources"]:
            path = ROOT / source["path"]
            raw = path.read_bytes()
            actual_hash = hashlib.sha256(raw).hexdigest()
            if actual_hash != source["sha256"]:
                raise ValueError(f"Changed retained source: {source['path']}")
            if source["kind"] in {"lines", "stdout_lines"}:
                text = json.loads(raw)["stdout"] if source["kind"] == "stdout_lines" else raw.decode()
                actual = [text.splitlines()[n - 1] for n in source["lines"]]
                if actual != source["expected"]:
                    raise ValueError(f"Excerpt mismatch: {source['path']}")
            else:
                obj = json.loads(raw)
                for field in source["fields"]:
                    if at_pointer(obj, field["pointer"]) != field["expected"]:
                        raise ValueError(f"JSON pointer mismatch: {source['path']} {field['pointer']}")


def wrap(text: str, face: ImageFont.FreeTypeFont, width: int) -> list[str]:
    """Wrap without dropping characters; long literal lines are visibly wrapped."""
    rows: list[str] = []
    for paragraph in text.split("\n"):
        rest = paragraph
        while face.getlength(rest) > width:
            end = len(rest)
            while face.getlength(rest[:end]) > width:
                end -= 1
            split = rest.rfind(" ", 0, end + 1)
            if split > end // 2:
                end = split + 1
            rows.append(rest[:end].rstrip())
            rest = rest[end:]
        rows.append(rest)
    return rows


def body_rows(scene: dict[str, Any]) -> list[tuple[str, str]]:
    rows: list[tuple[str, str]] = []
    for source in scene["sources"]:
        if source.get("display", True) is False:
            continue
        if source["kind"] in {"lines", "stdout_lines"}:
            for excerpt in source["expected"]:
                rows.extend((line, COLORS["white"]) for line in wrap(excerpt, font(32, True), 1272))
        else:
            for field in source["fields"]:
                value = json.dumps(field["expected"], ensure_ascii=False)
                text = f"{field['label']}: {value}"
                rows.extend((line, COLORS["white"]) for line in wrap(text, font(32, True), 1272))
    if len(rows) > 8:
        raise ValueError(f"Scene {scene['id']} needs {len(rows)} rows; edit layout/excerpts instead of shrinking text.")
    return rows


def draw_scene(data: dict[str, Any], scene: dict[str, Any], reveal: float = 1.0) -> Image.Image:
    im = Image.new("RGB", (WIDTH, HEIGHT), COLORS["bg"])
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((42, 24, 1398, 75), 14, fill=COLORS["panel"])
    d.text((62, 34), data["permanent_label"], font=font(28), fill=COLORS["amber"])
    date = scene["event_date_utc"]
    d.text((1378 - d.textlength(date, font=font(26)), 36), date, font=font(26), fill=COLORS["muted"])
    d.text((56, 99), scene["title"], font=font(52), fill=COLORS["white"])
    subs = wrap(scene["subtitle"], font(32), 1328)
    if len(subs) > 2:
        raise ValueError(f"Subtitle overflow in scene {scene['id']}")
    for i, row in enumerate(subs):
        d.text((58, 171 + i * 36), row, font=font(32), fill=COLORS["teal"])
    d.rounded_rectangle((56, 252, 1384, 574), 18, fill=COLORS["panel"], outline=COLORS["edge"], width=2)
    rows = body_rows(scene)
    count = max(1, round(len(rows) * reveal))
    for i, (row, color) in enumerate(rows[:count]):
        d.text((84, 266 + i * 36), row, font=font(32, True), fill=color)
    note = wrap(scene["note"], font(27), 1328)
    if len(note) > 2:
        raise ValueError(f"Scope note overflow in scene {scene['id']}")
    for i, row in enumerate(note):
        d.text((58, 590 + i * 31), row, font=font(27), fill=COLORS["amber"])
    source_rows: list[str] = []
    for index, source in enumerate(scene["sources"]):
        locator = scene["footer_locators"][index]
        source_rows.extend(wrap(f"{source['path']}  ·  {locator}", font(24), 1328))
    if len(source_rows) > 4:
        raise ValueError(f"Source footer overflow in scene {scene['id']}: {len(source_rows)}")
    for i, row in enumerate(source_rows):
        d.text((58, 658 + i * 28), row, font=font(24), fill=COLORS["muted"])
    # Progress represents presentation position, never test progress or new execution.
    for i in range(len(data["scenes"])):
        x = 56 + i * 167
        d.rounded_rectangle((x, 786, x + 154, 792), 3, fill=COLORS["teal"] if i < scene["id"] else COLORS["edge"])
    return im


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--validate-only", action="store_true")
    args = parser.parse_args()
    data = json.loads(DATA.read_text())
    validate(data)
    if args.validate_only:
        print(f"Validated {len(data['scenes'])} scenes against unchanged retained sources.")
        return
    # A shared palette keeps text consistent and yields compact GIF delta frames.
    completed = [draw_scene(data, scene) for scene in data["scenes"]]
    completed[0].save(MEDIA / "aosp-proof-poster.png", optimize=True)
    palette = Image.new("RGB", (WIDTH * 2, HEIGHT * 4))
    for index, frame in enumerate(completed):
        palette.paste(frame, ((index % 2) * WIDTH, (index // 2) * HEIGHT))
    palette = palette.quantize(colors=128, method=Image.Quantize.MEDIANCUT)
    frames, durations = [], []
    for scene, final in zip(data["scenes"], completed):
        # Whole rows appear before a 3.3-second fully readable hold.
        # The opening scene is complete from the first frame, without a flash.
        ratios = (1.0, 1.0) if scene["id"] == 1 else (0.34, 0.67)
        for ratio in ratios:
            frames.append(draw_scene(data, scene, ratio).quantize(palette=palette, dither=Image.Dither.NONE))
            durations.append(100)
        frames.append(final.quantize(palette=palette, dither=Image.Dither.NONE))
        durations.append(scene["duration_ms"] - 200)
    # Start with the complete meaningful poster, then show each complete evidence scene.
    frames[0] = completed[0].quantize(palette=palette, dither=Image.Dither.NONE)
    frames[0].save(MEDIA / "aosp-proof-replay.gif", save_all=True, append_images=frames[1:], duration=durations, loop=0, optimize=True, disposal=1)
    target = MEDIA / "aosp-proof-replay.gif"
    if target.stat().st_size >= 8_000_000:
        raise RuntimeError("GIF exceeds 8 MB budget; reduce presentation frames, never source readability.")
    with Image.open(target) as replay:
        total_ms = 0
        for index in range(replay.n_frames):
            replay.seek(index)
            total_ms += replay.info["duration"]
        assert replay.size == (WIDTH, HEIGHT) and total_ms == data["duration_ms"]
        print(json.dumps({"scenes": len(data["scenes"]), "frames": replay.n_frames, "duration_ms": total_ms, "dimensions": replay.size, "gif_bytes": target.stat().st_size, "poster_bytes": (MEDIA / "aosp-proof-poster.png").stat().st_size}))


if __name__ == "__main__":
    main()
