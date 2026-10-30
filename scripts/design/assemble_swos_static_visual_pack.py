#!/usr/bin/env python3
"""Assemble private SWOS frame exports, contact sheet, geometry compare and provenance."""
from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import zipfile
from datetime import datetime, timezone
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
FRAME_IDS = ["P0", "P1", "P2", "P3", "P4", "P5", "P6", "P7", "R0", "R0.5", "R1", "R2", "R3", "R4", "R5"]
SHA = lambda raw: hashlib.sha256(raw).hexdigest()


def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("--frames-dir", required=True, type=Path)
    parser.add_argument("--photo", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    return parser.parse_args()


def frame_filename(frame_id: str) -> str:
    return frame_id.replace(".", "_") + ".png"


def load_font(size: int):
    candidates = [
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/liberation2/LiberationSans-Regular.ttf",
    ]
    for path in candidates:
        if Path(path).is_file():
            return ImageFont.truetype(path, size=size)
    return ImageFont.load_default()


def main():
    args = parse_args()
    frames_dir = args.frames_dir.resolve()
    photo = args.photo.resolve()
    out = args.output_dir.resolve()
    out.mkdir(parents=True, exist_ok=True)
    photo_bytes = photo.read_bytes()
    photo_sha = SHA(photo_bytes)
    expected = "ab7227cf7205ee2ac342b6cd75cd899747879506c95c33b888b6adf0acb47987"
    if photo_sha != expected:
        raise SystemExit(f"Source SHA mismatch: {photo_sha} != {expected}")
    browser_report_path = frames_dir / "SWOS_Static_Visual_Browser_Report.json"
    browser_report = json.loads(browser_report_path.read_text(encoding="utf-8"))
    if browser_report.get("source_photo_sha256") != photo_sha or browser_report.get("frame_count") != 15:
        raise SystemExit("Browser export report does not match the 15-frame photo fixture")
    files = {}
    images = {}
    for frame_id in FRAME_IDS:
        path = frames_dir / frame_filename(frame_id)
        raw = path.read_bytes()
        image = Image.open(path).convert("RGB")
        if image.size != (1080, 1920):
            raise SystemExit(f"{frame_id} dimensions are {image.size}, expected 1080×1920")
        destination = out / path.name
        shutil.copyfile(path, destination)
        files[frame_id] = {"filename": destination.name, "width": 1080, "height": 1920, "sha256": SHA(raw)}
        images[frame_id] = image

    # 75% contact sheet leaves each frozen screen legible while fitting 8 + 7 frames.
    tile_w, tile_h, gap, label_h = 810, 1440, 20, 40
    sheet_w = 8 * tile_w + 7 * gap
    sheet_h = 2 * (tile_h + label_h) + 3 * gap
    sheet = Image.new("RGB", (sheet_w, sheet_h), "#e8efec")
    draw = ImageDraw.Draw(sheet)
    font = load_font(24)
    for index, frame_id in enumerate(FRAME_IDS):
        row, column = divmod(index, 8)
        x = column * (tile_w + gap)
        y = gap + row * (tile_h + label_h + gap)
        draw.rounded_rectangle((x, y, x + tile_w, y + label_h - 5), radius=8, fill="#244a4b")
        draw.text((x + 14, y + 7), frame_id, fill="#ffffff", font=font)
        frame = images[frame_id].resize((tile_w, tile_h), Image.Resampling.LANCZOS)
        sheet.paste(frame, (x, y + label_h))
    contact_path = out / "SWOS_15_Frame_Contact_Sheet.png"
    sheet.save(contact_path, optimize=True)

    geometry_ids = ["P7", "R0", "R0.5", "R1"]
    geom_w, geom_h, geom_gap, geom_label_h = 1080, 1920, 18, 48
    geometry = Image.new("RGB", (4 * geom_w + 3 * geom_gap, geom_h + geom_label_h), "#e8efec")
    geom_draw = ImageDraw.Draw(geometry)
    for index, frame_id in enumerate(geometry_ids):
        x = index * (geom_w + geom_gap)
        geom_draw.rounded_rectangle((x, 0, x + geom_w, geom_label_h - 4), radius=8, fill="#244a4b")
        geom_draw.text((x + 14, 8), frame_id, fill="#ffffff", font=load_font(26))
        geometry.paste(images[frame_id], (x, geom_label_h))
    geometry_path = out / "SWOS_P7_R0_R0_5_R1_Geometry_Continuity.png"
    geometry.save(geometry_path, optimize=True)

    source_copy = out / "source_photo_208758.jpg"
    shutil.copyfile(photo, source_copy)
    manifest = {
        "artifact": "SWOS_STATIC_VISUAL_V1_1_PRIVATE_REVIEW_PACK",
        "created_at_utc": datetime.now(timezone.utc).isoformat(),
        "source_photo": {"filename": source_copy.name, "sha256": photo_sha, "width": 1152, "height": 1536, "orientation": 1, "publication_permission": "NOT_CONFIRMED; PRIVATE_REVIEW_ONLY"},
        "historical_direction_reference": {"sha256": "ea0cf2e5fa84aad64195487cbedaab07b1727e96e87c9b4e661dc3d0fa252c1c", "version": "SWOS 14-frame V0.3", "role": "APPROVED_DIRECTION_REFERENCE_NOT_PIXEL_FROZEN"},
        "processing_authorities": ["design/pages/recognition/design/02_AI_Understanding_Frozen.png", "design/pages/recognition/design/03_Fish_Highlight_Frozen.png", "design/pages/recognition/design/04_Fish_Identifying_Frozen.png"],
        "result_authority": "design/pages/recognition/design/05_Result_High_Frozen.png",
        "frames": files,
        "contact_sheet": {"filename": contact_path.name, "width": sheet.width, "height": sheet.height, "sha256": SHA(contact_path.read_bytes())},
        "geometry_continuity": {"filename": geometry_path.name, "width": geometry.width, "height": geometry.height, "sha256": SHA(geometry_path.read_bytes()), "frames": geometry_ids},
        "fish_geometry": "MANUAL_REFERENCE_ONLY_NOT_PRODUCTION_MASK",
        "browser_report": browser_report,
        "public_repository_included": False,
        "dynamic_playback": False,
        "android_runtime": "NOT_RUN"
    }
    manifest_path = out / "SWOS_Static_Visual_Private_Asset_Manifest.json"
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    report_path = out / "SWOS_Static_Visual_Private_Review_Report.md"
    report_path.write_text(
        "# SWOS Static Visual V1.1 — Private Review Pack\n\n"
        f"- Frames: 15 exports at 1080×1920.\n- Contact sheet: `{contact_path.name}` ({sheet.width}×{sheet.height}).\n"
        f"- Geometry continuity: `{geometry_path.name}` ({geometry.width}×{geometry.height}).\n"
        f"- Source SHA-256: `{photo_sha}`.\n- Fish geometry: `MANUAL_REFERENCE_ONLY_NOT_PRODUCTION_MASK`.\n"
        "- Photo publication permission: `NOT_CONFIRMED`; keep this package private.\n"
        "- Static only: no playable animation, no Android Runtime.\n",
        encoding="utf-8"
    )
    archive = out / "SWOS_Static_Visual_V1_1_Private_Review_Pack.zip"
    with zipfile.ZipFile(archive, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=7) as bundle:
        for path in sorted(out.iterdir()):
            if path.is_file() and path != archive:
                bundle.write(path, arcname=path.name)
    print(f"frames={len(files)} contact={sheet.size} geometry={geometry.size}")
    print(f"archive={archive} sha256={SHA(archive.read_bytes())}")


if __name__ == "__main__":
    main()
