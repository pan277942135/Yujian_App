#!/usr/bin/env python3
"""Produce diagnostic Frozen-vs-structured images and component ROI measurements."""

from __future__ import annotations

import argparse
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path

from PIL import Image, ImageChops, ImageStat


ROOT = Path(__file__).resolve().parents[2]
FROZEN = ROOT / "design/system/core_visual_v1/reference/normal_home_v1.png"
EXPECTED_SHA = "6ab9d3348b4a9a7e77ddca3a06235b4991798a309bd3512cc6fb9ea7aeb1d377"
PIXEL_TARGETS = ROOT / "design/system_v3/10_Page_Visual_Acceptance_Contracts/Normal_Home/Normal_Home_Pixel_Targets_V1.json"


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def rect_inclusive(box: list[int]) -> tuple[int, int, int, int]:
    x, y, x2, y2 = box
    return x, y, x2 + 1, y2 + 1


def rect_xywh(box: list[int]) -> tuple[int, int, int, int]:
    x, y, width, height = box
    return x, y, x + width, y + height


def roi_metrics(source: Image.Image, candidate: Image.Image, box: tuple[int, int, int, int], name: str, scope: str, status: str) -> dict:
    left, top, right, bottom = box
    a = source.crop(box).convert("RGB")
    b = candidate.crop(box).convert("RGB")
    diff = ImageChops.difference(a, b)
    stat = ImageStat.Stat(diff)
    values = list(diff.getdata())
    changed = sum(1 for pixel in values if max(pixel) > 8)
    return {
        "component": name,
        "bbox_inclusive": [left, top, right - 1, bottom - 1],
        "scope": scope,
        "status": status,
        "mean_absolute_difference_rgb": [round(v, 4) for v in stat.mean],
        "changed_pixel_fraction_over_8": round(changed / max(1, len(values)), 6),
        "pixel_count": len(values),
        "verdict": "NOT_A_PARITY_THRESHOLD" if status == "REVIEW_REQUIRED" else status,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--structured", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--source-sha", default="unknown")
    args = parser.parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    if sha(FROZEN) != EXPECTED_SHA:
        raise SystemExit("SOURCE_FAIL: Frozen NH01 PNG SHA mismatch")
    with Image.open(FROZEN) as image:
        source = image.convert("RGB")
    with Image.open(args.structured) as image:
        candidate = image.convert("RGB")
    if source.size != (1080, 1920) or candidate.size != source.size:
        raise SystemExit(f"SOURCE_FAIL: expected two unresampled 1080x1920 images, got {source.size} and {candidate.size}")

    diff = ImageChops.difference(source, candidate)
    diff_pixels = diff.load()
    heat = Image.new("RGB", source.size)
    heat_pixels = heat.load()
    for y in range(source.height):
        for x in range(source.width):
            strength = max(diff_pixels[x, y])
            heat_pixels[x, y] = (strength, max(0, 90 - int(strength * .35)), 18)
    overlay = Image.blend(source, candidate, .5)
    heat_path = args.output_dir / "NH01_Diff_Heatmap.png"
    overlay_path = args.output_dir / "NH01_Diff_Overlay.png"
    heat.save(heat_path)
    overlay.save(overlay_path)

    targets = json.loads(PIXEL_TARGETS.read_text(encoding="utf-8"))
    measured = targets["measured_source_targets"]["normal_home_source_masks"]
    anchors = targets["measured_source_targets"]["normal_home_reference_anchors"]
    rois = []
    rois.append(roi_metrics(source, candidate, rect_xywh(anchors["header"]), "Header", "Layout ROI includes unverified Avatar; raw diagnostic only.", "REVIEW_REQUIRED"))
    rois.append(roi_metrics(source, candidate, (250, 320, 851, 402), "Statistics glyph ROI", "Source-defined visible glyph union; HTML font rendering differs from Android.", "REVIEW_REQUIRED"))
    rois.append(roi_metrics(source, candidate, (108, 494, 972, 565), "RecentCatchHeader", "Source-defined region; text glyph raster is diagnostic only.", "REVIEW_REQUIRED"))
    rois.append(roi_metrics(source, candidate, rect_xywh(anchors["hero"]), "Hero Card", "Dynamic hero photo source is blocked; do not use pixel metric as parity verdict.", "REVIEW_REQUIRED"))
    for key, label in (("CTA_NEAR_WHITE", "CTA glyph"), ("CAMERA_CORE_NEAR_WHITE", "Camera core"), ("CAMERA_GOLD", "Camera gold")):
        item = measured[key]
        rois.append(roi_metrics(source, candidate, rect_inclusive(item["bbox"]), label, f"Source mask ROI; threshold contract: {item['threshold']}", "REVIEW_REQUIRED"))

    report = {
        "schema_version": 1,
        "classification": "DIAGNOSTIC_ONLY_REVIEW_REQUIRED",
        "comparison": "Frozen vs Structured Browser Preview",
        "source_sha": args.source_sha,
        "frozen": {"path": "design/system/core_visual_v1/reference/normal_home_v1.png", "sha256": sha(FROZEN), "dimensions": [1080, 1920]},
        "structured_preview": {"path": str(args.structured), "sha256": sha(args.structured), "dimensions": list(candidate.size), "renderer": "Chromium DOM screenshot; not Android Runtime"},
        "global_changed_pixel_fraction_over_8": round(sum(1 for pixel in diff.getdata() if max(pixel) > 8) / (source.width * source.height), 6),
        "global_rgb_mae": [round(v, 4) for v in ImageStat.Stat(diff).mean],
        "rois": rois,
        "threshold_authority": "design/pages/home/normal_home/engineering/Quantitative_Visual_Acceptance_Contract_V1.md",
        "threshold_policy": "No global pixel MAE threshold applied. Geometry/glyph thresholds are not substituted for browser-to-Android acceptance. Dynamic photo and unresolved avatar remain review/blocked.",
        "generated_at_utc": datetime.now(timezone.utc).isoformat(),
    }
    (args.output_dir / "NH01_ROI_Report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"NH01_PREVIEW_DIFF=REVIEW_REQUIRED source_sha={args.source_sha} changed={report['global_changed_pixel_fraction_over_8']}")


if __name__ == "__main__":
    main()
