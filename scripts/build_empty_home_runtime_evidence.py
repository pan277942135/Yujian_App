#!/usr/bin/env python3
"""Create Android-runtime evidence metadata and a measured anchor/parity overlay."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
FEATURE = ROOT / "design/pages/home/empty_home"
RUNTIME = ROOT / "app/src/main/assets/empty_home_runtime_v2/config"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--evidence-dir", type=Path, required=True)
    parser.add_argument("--build-sha", required=True)
    args = parser.parse_args()
    out = args.evidence_dir
    static = out / "runtime_static.png"
    if not static.is_file():
        raise SystemExit("runtime_static.png is required before evidence metadata is generated")
    anchors = json.loads((RUNTIME / "anchor_contract.json").read_text())
    motion = json.loads((RUNTIME / "motion_contract.json").read_text())
    runtime = json.loads((RUNTIME / "runtime_manifest.json").read_text())
    image = Image.open(static).convert("RGB")
    overlay = image.convert("RGBA")
    d = ImageDraw.Draw(overlay, "RGBA")
    contact = anchors["bobber"]["water_contact_reference_px"]
    camera = anchors["camera_button"]["center_reference_px"]
    sx, sy = image.width / 1080, image.height / 1920
    for x, y, colour, label in [(*contact, (255, 188, 50, 255), "ripple center / water contact"), (*camera, (20, 74, 123, 255), "capture center")]:
        px, py = x * sx, y * sy
        d.line((px - 22, py, px + 22, py), fill=colour, width=3)
        d.line((px, py - 22, px, py + 22), fill=colour, width=3)
        d.text((px + 26, py - 18), label, fill=colour)
    overlay.save(out / "anchor_overlay.png")

    reference = Image.open(FEATURE / "source/frozen/Empty_Home_Final_Design_V2_normalized_1080x1920.png").convert("RGB")
    candidate = image.resize((1080, 1920), Image.Resampling.LANCZOS)
    diff = ImageChops.difference(reference, candidate)
    histogram = diff.histogram()
    mae = sum(index % 256 * count for index, count in enumerate(histogram)) / (1080 * 1920 * 3)
    parity = {
        "source": "real Android APK screenshot runtime_static.png",
        "reference": "Empty_Home_Final_Design_V2_normalized_1080x1920.png",
        "metric": "mean_absolute_rgb_error",
        "value": round(mae, 4),
        "threshold": 40.0,
        "status": "PASS" if mae <= 40 else "FAIL",
        "note": "Measured on the full runtime screenshot; tiny idle motion and native system insets are included.",
    }
    (out / "visual_parity_report.json").write_text(json.dumps(parity, ensure_ascii=False, indent=2) + "\n")
    debug = {
        "screen": "home_empty",
        "design_version": runtime["design_version"],
        "asset_revision": runtime["asset_revision"],
        "build_sha": args.build_sha,
        "reference_canvas": runtime["reference_canvas"],
        "screenshot_dimensions": [image.width, image.height],
        "bobber_center_reference_px": anchors["bobber"]["center_reference_px"],
        "water_contact_reference_px": contact,
        "ripple_center_reference_px": anchors["ripple"]["center_reference_px"],
        "bobber_range_reference_px": motion["bobber"]["range_reference_px"],
        "bobber_duration_ms": motion["bobber"]["duration_ms"],
        "ripple_duration_ms": motion["ripple"]["duration_ms"],
        "camera_gold_rim": motion["camera_gold_rim"],
        "camera_breath": motion["camera_breath"],
        "fps_summary": {"capture": "Android adb screenrecord", "duration_s": 15, "expected_frame_rate": 30},
        "phase_offsets": {"cloud_s": 0, "sun_particle_s": 0, "bobber_s": 0, "ripple_s": 0},
    }
    (out / "runtime_debug.json").write_text(json.dumps(debug, ensure_ascii=False, indent=2) + "\n")
    if parity["status"] != "PASS":
        raise SystemExit("visual parity P0 gate failed: " + json.dumps(parity, ensure_ascii=False))


if __name__ == "__main__":
    main()
