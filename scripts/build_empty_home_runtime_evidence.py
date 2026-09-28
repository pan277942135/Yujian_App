#!/usr/bin/env python3
"""Create Empty Home runtime evidence with full-frame and semantic-region parity."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageStat


ROOT = Path(__file__).resolve().parents[1]
FEATURE = ROOT / "design/pages/home/empty_home"
RUNTIME = ROOT / "app/src/main/assets/empty_home_runtime_v2/config"

REFERENCE_WIDTH = 1080
REFERENCE_HEIGHT = 1920

# Full-frame MAE is background-dominated. These frozen reference-space regions
# make UI/focal fidelity an explicit gate without changing the V2 visual authority.
VISUAL_REGIONS = {
    "hero": {
        "box": [50, 224, 670, 534],
        "threshold": 42.0,
    },
    "camera": {
        "box": [400, 1430, 680, 1740],
        "threshold": 42.0,
    },
    "cta": {
        "box": [300, 1400, 780, 1830],
        "threshold": 38.0,
    },
    "bobber_water": {
        "box": [411, 1080, 650, 1220],
        "threshold": 15.0,
    },
}


def mean_rgb_error(left: Image.Image, right: Image.Image) -> float:
    diff = ImageChops.difference(left.convert("RGB"), right.convert("RGB"))
    return sum(ImageStat.Stat(diff).mean) / 3.0


def runtime_box(reference_box: list[int], width: int, height: int) -> tuple[int, int, int, int]:
    x0, y0, x1, y1 = reference_box
    sx = width / REFERENCE_WIDTH
    sy = height / REFERENCE_HEIGHT
    return (
        int(round(x0 * sx)),
        int(round(y0 * sy)),
        int(round(x1 * sx)),
        int(round(y1 * sy)),
    )


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
    sx, sy = image.width / REFERENCE_WIDTH, image.height / REFERENCE_HEIGHT
    for x, y, colour, label in [
        (*contact, (255, 188, 50, 255), "ripple center / water contact"),
        (*camera, (20, 74, 123, 255), "capture center"),
    ]:
        px, py = x * sx, y * sy
        d.line((px - 22, py, px + 22, py), fill=colour, width=3)
        d.line((px, py - 22, px, py + 22), fill=colour, width=3)
        d.text((px + 26, py - 18), label, fill=colour)
    overlay.save(out / "anchor_overlay.png")

    # The native canonical PNG remains the sole visual authority. Runtime
    # screenshots are resampled only for comparison/output dimensions.
    reference = Image.open(
        ROOT / "design/system/core_visual_v1/reference/empty_home_v2.png"
    ).convert("RGB")
    reference_at_runtime_size = reference.resize(image.size, Image.Resampling.LANCZOS)
    reference_at_runtime_size.save(out / "frozen_reference.png")

    side_by_side = Image.new("RGB", (image.width * 2, image.height))
    side_by_side.paste(reference_at_runtime_size, (0, 0))
    side_by_side.paste(image, (image.width, 0))
    side_by_side.save(out / "side_by_side.png")

    difference = ImageChops.difference(reference_at_runtime_size, image)
    difference.save(out / "pixel_diff_heatmap.png")

    candidate = image.resize((REFERENCE_WIDTH, REFERENCE_HEIGHT), Image.Resampling.LANCZOS)
    reference_for_metric = reference.resize(
        (REFERENCE_WIDTH, REFERENCE_HEIGHT),
        Image.Resampling.LANCZOS,
    )
    full_mae = mean_rgb_error(reference_for_metric, candidate)

    region_results: dict[str, dict[str, object]] = {}
    region_failures: list[str] = []
    for name, spec in VISUAL_REGIONS.items():
        box = runtime_box(spec["box"], image.width, image.height)
        ref_crop = reference_at_runtime_size.crop(box)
        runtime_crop = image.crop(box)
        value = mean_rgb_error(ref_crop, runtime_crop)
        threshold = float(spec["threshold"])
        status = "PASS" if value <= threshold else "FAIL"
        if status != "PASS":
            region_failures.append(name)

        region_results[name] = {
            "reference_box": spec["box"],
            "runtime_box": list(box),
            "metric": "mean_absolute_rgb_error",
            "value": round(value, 4),
            "threshold": threshold,
            "status": status,
        }

        comparison = Image.new(
            "RGB",
            (ref_crop.width * 2, max(ref_crop.height, runtime_crop.height)),
        )
        comparison.paste(ref_crop, (0, 0))
        comparison.paste(runtime_crop, (ref_crop.width, 0))
        comparison.save(out / f"region_{name}_side_by_side.png")

    full_pass = full_mae <= 40.0
    overall_pass = full_pass and not region_failures
    parity = {
        "source": "real Android APK screenshot runtime_static.png",
        "reference": "design/system/core_visual_v1/reference/empty_home_v2.png",
        "metric": "mean_absolute_rgb_error",
        "value": round(full_mae, 4),
        "threshold": 40.0,
        "full_frame_status": "PASS" if full_pass else "FAIL",
        "regions": region_results,
        "region_failures": region_failures,
        "status": "PASS" if overall_pass else "FAIL",
        "note": (
            "Full-frame parity plus frozen focal-region parity. "
            "This prevents the lake background from hiding UI fidelity regressions."
        ),
    }
    (out / "visual_parity_report.json").write_text(
        json.dumps(parity, ensure_ascii=False, indent=2) + "\n"
    )

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
        "visual_regions": VISUAL_REGIONS,
        "fps_summary": {
            "capture": "Android adb screenrecord",
            "duration_s": 15,
            "expected_frame_rate": 30,
        },
        "phase_offsets": {
            "cloud_s": 17.6,
            "sun_particle_s": 2.08,
            "bobber_s": 1.15,
            "ripple_s": 0.42,
            "camera_breath_s": 1.71,
        },
    }
    (out / "runtime_debug.json").write_text(
        json.dumps(debug, ensure_ascii=False, indent=2) + "\n"
    )

    if parity["status"] != "PASS":
        raise SystemExit(
            "visual fidelity gate failed: " + json.dumps(parity, ensure_ascii=False)
        )


if __name__ == "__main__":
    main()
