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

# V2.2 is a delta freeze over the V2 base scene. Pixel comparisons against
# empty_home_v2.png are valid only for unchanged areas. Revised CTA/fishing
# composition is checked against the explicit V2.2 runtime contracts plus
# real-screenshot visibility, so an older base PNG cannot reject approved deltas.
BASE_VISUAL_REGIONS = {
    "hero": {
        "box": [50, 224, 670, 534],
        "threshold": 42.0,
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

    # Unchanged V2 areas continue to use direct pixel parity.
    for name, spec in BASE_VISUAL_REGIONS.items():
        box = runtime_box(spec["box"], image.width, image.height)
        ref_crop = reference_at_runtime_size.crop(box)
        runtime_crop = image.crop(box)
        value = mean_rgb_error(ref_crop, runtime_crop)
        threshold = float(spec["threshold"])
        status = "PASS" if value <= threshold else "FAIL"
        if status != "PASS":
            region_failures.append(name)

        region_results[name] = {
            "authority": "Empty_Home_Final_Design_V2",
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

    # Camera / CTA are V2.2-approved deltas. Validate their frozen geometry and
    # that the real APK screenshot actually contains the bright capture control
    # at the contracted location instead of comparing against stale V2 pixels.
    camera_anchor = anchors["camera_button"]["bbox_reference_px"]
    camera_reference_box = [
        int(camera_anchor["x"]),
        int(camera_anchor["y"]),
        int(camera_anchor["x"] + camera_anchor["width"]),
        int(camera_anchor["y"] + camera_anchor["height"]),
    ]
    camera_box = runtime_box(camera_reference_box, image.width, image.height)
    camera_crop = image.crop(camera_box)
    camera_pixels = list(camera_crop.getdata())
    camera_bright_fraction = (
        sum(1 for r, g, b in camera_pixels if r > 210 and g > 210 and b > 210)
        / max(1, len(camera_pixels))
    )
    camera_spread = sum(ImageStat.Stat(camera_crop).stddev) / 3.0
    camera_in_bounds = (
        0 <= camera_box[0] < camera_box[2] <= image.width
        and 0 <= camera_box[1] < camera_box[3] <= image.height
    )
    camera_status = (
        "PASS"
        if camera_in_bounds and camera_bright_fraction >= 0.05 and camera_spread >= 10.0
        else "FAIL"
    )
    if camera_status != "PASS":
        region_failures.append("camera")
    region_results["camera"] = {
        "authority": "Empty_Home_Frozen_Visual_Revision_V2_2",
        "reference_box": camera_reference_box,
        "runtime_box": list(camera_box),
        "metric": "contract_geometry+runtime_visibility",
        "bright_fraction": round(camera_bright_fraction, 4),
        "bright_fraction_min": 0.05,
        "rgb_stddev_mean": round(camera_spread, 4),
        "rgb_stddev_min": 10.0,
        "status": camera_status,
    }
    camera_crop.save(out / "region_camera_runtime.png")

    cta = anchors["cta"]
    prompt_top = int(cta["prompt_top_reference_px"])
    camera_top = int(cta["camera_top_reference_px"])
    camera_size = int(cta["camera_size_reference_px"])
    album_top = int(cta["album_top_reference_px"])
    cta_geometry_ok = (
        prompt_top < camera_top
        and camera_top == int(camera_anchor["y"])
        and camera_size == int(camera_anchor["height"])
        and camera_size == int(camera_anchor["width"])
        and camera_top + camera_size < album_top
        and album_top < REFERENCE_HEIGHT
    )
    cta_status = "PASS" if cta_geometry_ok and camera_status == "PASS" else "FAIL"
    if cta_status != "PASS":
        region_failures.append("cta")
    region_results["cta"] = {
        "authority": "Empty_Home_Frozen_Visual_Revision_V2_2",
        "metric": "frozen_anchor_contract",
        "prompt_top_reference_px": prompt_top,
        "camera_top_reference_px": camera_top,
        "camera_size_reference_px": camera_size,
        "album_top_reference_px": album_top,
        "status": cta_status,
    }

    # Fishing composition is also a V2.2 delta. Validate the frozen line/contact
    # relationship and require non-blank runtime pixels around the new contact.
    contact_x, contact_y = anchors["bobber"]["water_contact_reference_px"]
    bobber_reference_box = [
        max(0, int(contact_x) - 120),
        max(0, int(contact_y) - 90),
        min(REFERENCE_WIDTH, int(contact_x) + 120),
        min(REFERENCE_HEIGHT, int(contact_y) + 90),
    ]
    bobber_box = runtime_box(bobber_reference_box, image.width, image.height)
    bobber_crop = image.crop(bobber_box)
    bobber_spread = sum(ImageStat.Stat(bobber_crop).stddev) / 3.0
    line_end = anchors["line"]["end_reference_px"]
    ripple_center = anchors["ripple"]["center_reference_px"]
    bobber_contract_ok = (
        ripple_center == [contact_x, contact_y]
        and int(line_end[0]) == int(contact_x)
        and int(line_end[1]) > int(contact_y)
        and bobber_spread >= 5.0
    )
    bobber_status = "PASS" if bobber_contract_ok else "FAIL"
    if bobber_status != "PASS":
        region_failures.append("bobber_water")
    region_results["bobber_water"] = {
        "authority": "Empty_Home_Frozen_Visual_Revision_V2_2",
        "reference_box": bobber_reference_box,
        "runtime_box": list(bobber_box),
        "metric": "frozen_anchor_contract+runtime_variance",
        "rgb_stddev_mean": round(bobber_spread, 4),
        "rgb_stddev_min": 5.0,
        "water_contact_reference_px": [contact_x, contact_y],
        "line_end_reference_px": line_end,
        "status": bobber_status,
    }
    bobber_crop.save(out / "region_bobber_water_runtime.png")

    full_pass = full_mae <= 40.0
    overall_pass = full_pass and not region_failures
    parity = {
        "source": "real Android APK screenshot runtime_static.png",
        "reference": "design/system/core_visual_v1/reference/empty_home_v2.png",
        "visual_revision": runtime.get("visual_revision"),
        "visual_revision_source_sha256": runtime.get("approved_visual_sha256"),
        "metric": "hybrid_base_pixel_parity+v2_2_contract_evidence",
        "value": round(full_mae, 4),
        "threshold": 40.0,
        "full_frame_status": "PASS" if full_pass else "FAIL",
        "regions": region_results,
        "region_failures": region_failures,
        "status": "PASS" if overall_pass else "FAIL",
        "note": (
            "V2 base-scene/full-frame and unchanged hero use pixel parity. "
            "V2.2-approved CTA and fishing-composition deltas use frozen geometry "
            "contracts plus real-runtime visibility so stale V2 pixels are not "
            "treated as authority for revised regions."
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
        "base_visual_regions": BASE_VISUAL_REGIONS,
        "visual_revision": runtime.get("visual_revision"),
        "visual_revision_source_sha256": runtime.get("approved_visual_sha256"),
        "delta_region_results": {
            name: region_results[name]
            for name in ("camera", "cta", "bobber_water")
        },
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
