#!/usr/bin/env python3
"""Create exact-pixel Android/Frozen diagnostics without inventing parity thresholds."""

from __future__ import annotations

import argparse
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path

from PIL import Image, ImageChops, ImageStat

ROOT = Path(__file__).resolve().parents[2]
FROZEN = ROOT / "design/system/core_visual_v1/reference/normal_home_v1.png"
FROZEN_SHA = "6ab9d3348b4a9a7e77ddca3a06235b4991798a309bd3512cc6fb9ea7aeb1d377"


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def roi(left: Image.Image, right: Image.Image, box: tuple[int, int, int, int], name: str, note: str) -> dict:
    diff = ImageChops.difference(left.crop(box), right.crop(box))
    values = list(diff.getdata())
    changed = sum(max(pixel) > 8 for pixel in values)
    return {
        "name": name,
        "bbox_xyxy": list(box),
        "mean_absolute_difference_rgb": [round(value, 4) for value in ImageStat.Stat(diff).mean],
        "changed_pixel_fraction_over_8": round(changed / max(1, len(values)), 6),
        "pixel_count": len(values),
        "note": note,
        "status": "DIAGNOSTIC_ONLY_REVIEW_REQUIRED",
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--screenshot", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--source-sha", required=True)
    parser.add_argument("--apk-sha256", required=True)
    parser.add_argument("--run-id", required=True)
    args = parser.parse_args()
    if sha(FROZEN) != FROZEN_SHA:
        raise SystemExit("Frozen NH01 source hash mismatch")
    with Image.open(FROZEN) as image:
        frozen = image.convert("RGB")
    with Image.open(args.screenshot) as image:
        android = image.convert("RGB")
    if frozen.size != (1080, 1920) or android.size != frozen.size:
        raise SystemExit(f"Expected unresampled 1080x1920 inputs; got Frozen={frozen.size} Android={android.size}")

    args.output_dir.mkdir(parents=True, exist_ok=True)
    diff = ImageChops.difference(frozen, android)
    source_pixels = diff.load()
    heat = Image.new("RGB", frozen.size)
    heat_pixels = heat.load()
    for y in range(frozen.height):
        for x in range(frozen.width):
            strength = max(source_pixels[x, y])
            heat_pixels[x, y] = (strength, 255 - strength, min(255, int(strength * 0.8)))
    heat_path = args.output_dir / "NH01_Android_Pixel_Heatmap.png"
    overlay_path = args.output_dir / "NH01_Android_Overlay.png"
    heat.save(heat_path)
    Image.blend(frozen, android, 0.5).save(overlay_path)

    rois = [
        roi(frozen, android, (88, 104, 992, 208), "Header", "Avatar authority unresolved; diagnostic only."),
        roi(frozen, android, (150, 304, 930, 420), "Statistics", "Text raster and contract source reviewed separately."),
        roi(frozen, android, (108, 494, 972, 564), "Recent Catch Header", "Typography and registered chevron authority reviewed separately."),
        roi(frozen, android, (170, 596, 910, 1476), "Hero Card", "Dynamic photo region is excluded from parity acceptance while photo rights are unresolved."),
        roi(frozen, android, (433, 1527, 647, 1559), "CTA glyph", "Existing source mask ROI; contract threshold is applied by Normal_Home_Pixel_Diff_Report.json."),
        roi(frozen, android, (450, 1590, 629, 1774), "Camera core", "Existing source mask ROI; contract threshold is applied by Normal_Home_Pixel_Diff_Report.json."),
    ]
    changed = sum(max(pixel) > 8 for pixel in diff.getdata())
    report = {
        "schema_version": 1,
        "classification": "DIAGNOSTIC_ONLY_REVIEW_REQUIRED",
        "comparison": "Frozen NH01 vs API28 Android Runtime",
        "source_sha": args.source_sha,
        "workflow_run_id": args.run_id,
        "apk_sha256": args.apk_sha256,
        "frozen": {"path": "design/system/core_visual_v1/reference/normal_home_v1.png", "sha256": sha(FROZEN), "dimensions": [1080, 1920]},
        "android": {"path": args.screenshot.name, "sha256": sha(args.screenshot), "dimensions": list(android.size), "capture": "native adb screencap"},
        "heatmap": heat_path.name,
        "overlay": overlay_path.name,
        "global_changed_pixel_fraction_over_8": round(changed / (1080 * 1920), 6),
        "global_rgb_mae": [round(value, 4) for value in ImageStat.Stat(diff).mean],
        "rois": rois,
        "threshold_authority": "design/pages/home/normal_home/engineering/Quantitative_Visual_Acceptance_Contract_V1.md",
        "threshold_policy": "No new global acceptance threshold. Full image metrics are diagnostics; existing ROI contracts and exact runtime gate remain authoritative.",
        "generated_at_utc": datetime.now(timezone.utc).isoformat(),
    }
    (args.output_dir / "NH01_Android_Pixel_Heatmap_Report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"NH01_ANDROID_PIXEL_DIFF=DIAGNOSTIC_ONLY_REVIEW_REQUIRED source_sha={args.source_sha} run_id={args.run_id} changed={report['global_changed_pixel_fraction_over_8']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
