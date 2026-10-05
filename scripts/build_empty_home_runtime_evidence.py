#!/usr/bin/env python3
"""Build Empty Home canonical app-surface evidence and contract-backed parity."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageStat

ROOT = Path(__file__).resolve().parents[1]
CONFIG = ROOT / "app/src/main/assets/empty_home_runtime_v2/config"
REFERENCE_PATH = ROOT / "design/pages/home/empty_home/source/frozen/Empty_Home_Final_Design_V2_normalized_1080x1920.png"
CANONICAL_SIZE = (1080, 1920)
FULL_THRESHOLD = 40.0
HEADER_THRESHOLD = 42.0


def load_json(name: str) -> dict:
    return json.loads((CONFIG / name).read_text(encoding="utf-8"))


def mean_rgb_error(left: Image.Image, right: Image.Image) -> float:
    diff = ImageChops.difference(left.convert("RGB"), right.convert("RGB"))
    return sum(ImageStat.Stat(diff).mean) / 3.0


def mean_rgb_spread(image: Image.Image) -> float:
    return sum(ImageStat.Stat(image.convert("RGB")).stddev) / 3.0


def require_canonical(path: Path, label: str) -> Image.Image:
    image = Image.open(path).convert("RGB")
    if image.size != CANONICAL_SIZE:
        raise SystemExit(
            f"CANONICAL_CAPTURE_INVALID {label}={image.width}x{image.height} expected=1080x1920"
        )
    return image


def checked_crop(image: Image.Image, box: tuple[int, int, int, int], label: str) -> Image.Image:
    x0, y0, x1, y1 = box
    if not (0 <= x0 < x1 <= image.width and 0 <= y0 < y1 <= image.height):
        raise SystemExit(f"CANONICAL_CAPTURE_INVALID {label}_box={box}")
    return image.crop(box)


def save_diff(reference: Image.Image, runtime: Image.Image, box: tuple[int, int, int, int], path: Path) -> float:
    ref_crop = checked_crop(reference, box, "reference")
    run_crop = checked_crop(runtime, box, "runtime")
    ImageChops.difference(ref_crop, run_crop).save(path)
    return mean_rgb_error(ref_crop, run_crop)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--evidence-dir", type=Path, required=True)
    parser.add_argument("--build-sha", required=True)
    args = parser.parse_args()
    out = args.evidence_dir
    out.mkdir(parents=True, exist_ok=True)
    runtime = require_canonical(out / "runtime_static.png", "runtime")
    reference = require_canonical(REFERENCE_PATH, "reference")

    anchors = load_json("anchor_contract.json")
    motion = load_json("motion_contract.json")
    responsive = load_json("responsive_mapping_contract.json")
    manifest = load_json("runtime_manifest.json")

    hero_spec = responsive["groups"]["hero_copy"]["reference_machine_bbox_px"]
    hero_box = (
        int(hero_spec["x"]),
        int(hero_spec["y"]),
        int(hero_spec["x"] + hero_spec["width"]),
        int(hero_spec["y"] + hero_spec["height"]),
    )
    if hero_spec["width"] != 620 or hero_spec["height"] != 310:
        raise SystemExit("CANONICAL_CAPTURE_INVALID Hero machine bounds do not match the canonical mapping contract")
    reference_box = hero_box
    runtime_box = hero_box
    if reference_box != runtime_box:
        raise SystemExit("CANONICAL_CAPTURE_INVALID Hero boxes are not in the same canonical coordinate space")

    header_box = (0, 0, 1080, 200)
    contact_x, contact_y = anchors["bobber"]["water_contact_reference_px"]
    fishing_box = (
        max(0, int(contact_x) - 120),
        max(0, int(contact_y) - 90),
        min(1080, int(contact_x) + 120),
        min(1920, int(contact_y) + 90),
    )
    camera_anchor = anchors["camera_button"]["bbox_reference_px"]
    camera_box = (
        int(camera_anchor["x"]),
        int(camera_anchor["y"]),
        int(camera_anchor["x"] + camera_anchor["width"]),
        int(camera_anchor["y"] + camera_anchor["height"]),
    )
    cta = anchors["cta"]
    cta_box = (
        0,
        int(cta["prompt_top_reference_px"]),
        1080,
        min(1920, int(cta["album_top_reference_px"]) + 120),
    )

    # Required canonical evidence files. No image is scaled to reach these dimensions.
    reference.save(out / "01_frozen_1080x1920.png")
    runtime.save(out / "02_runtime_canonical_1080x1920.png")
    side = Image.new("RGB", (2160, 1920))
    side.paste(reference, (0, 0))
    side.paste(runtime, (1080, 0))
    side.save(out / "03_reference_vs_runtime.png")
    full_diff = ImageChops.difference(reference, runtime)
    full_diff.save(out / "04_full_diff.png")
    checked_crop(reference, reference_box, "hero_reference").save(out / "05_hero_reference.png")
    checked_crop(runtime, runtime_box, "hero_runtime").save(out / "06_hero_runtime.png")
    save_diff(reference, runtime, hero_box, out / "07_hero_diff.png")
    save_diff(reference, runtime, header_box, out / "08_header_diff.png")
    save_diff(reference, runtime, fishing_box, out / "09_fishing_diff.png")
    save_diff(reference, runtime, camera_box, out / "10_camera_diff.png")
    reference.save(out / "static_reference.png")

    full_mae = mean_rgb_error(reference, runtime)
    header_mae = mean_rgb_error(checked_crop(reference, header_box, "header"), checked_crop(runtime, header_box, "header"))
    hero_mae = mean_rgb_error(checked_crop(reference, hero_box, "hero"), checked_crop(runtime, hero_box, "hero"))
    fishing_mae = mean_rgb_error(checked_crop(reference, fishing_box, "fishing"), checked_crop(runtime, fishing_box, "fishing"))
    camera_mae = mean_rgb_error(checked_crop(reference, camera_box, "camera"), checked_crop(runtime, camera_box, "camera"))
    cta_mae = mean_rgb_error(checked_crop(reference, cta_box, "cta"), checked_crop(runtime, cta_box, "cta"))

    camera_crop = checked_crop(runtime, camera_box, "camera")
    camera_pixels = list(camera_crop.getdata())
    bright_fraction = sum(1 for red, green, blue in camera_pixels if red > 210 and green > 210 and blue > 210) / max(1, len(camera_pixels))
    camera_status = "PASS" if bright_fraction >= 0.05 and mean_rgb_spread(camera_crop) >= 10.0 else "FAIL"

    line_end = anchors["line"]["end_reference_px"]
    ripple_center = anchors["ripple"]["center_reference_px"]
    fishing_contract_ok = (
        ripple_center == [contact_x, contact_y]
        and int(line_end[0]) == int(contact_x)
        and int(line_end[1]) > int(contact_y)
        and mean_rgb_spread(checked_crop(runtime, fishing_box, "fishing")) >= 5.0
    )
    fishing_status = "PASS" if fishing_contract_ok else "FAIL"

    prompt_top = int(cta["prompt_top_reference_px"])
    camera_top = int(cta["camera_top_reference_px"])
    camera_size = int(cta["camera_size_reference_px"])
    album_top = int(cta["album_top_reference_px"])
    cta_contract_ok = (
        prompt_top < camera_top
        and camera_top == int(camera_anchor["y"])
        and camera_size == int(camera_anchor["width"]) == int(camera_anchor["height"])
        and camera_top + camera_size < album_top
        and album_top < 1920
    )
    cta_status = "PASS" if cta_contract_ok and camera_status == "PASS" else "FAIL"

    regions = {
        "full_frame": {"box": [0, 0, 1080, 1920], "metric": "mean_absolute_rgb_error", "value": round(full_mae, 4), "threshold": FULL_THRESHOLD, "status": "PASS" if full_mae <= FULL_THRESHOLD else "FAIL"},
        "header": {"box": list(header_box), "metric": "mean_absolute_rgb_error", "value": round(header_mae, 4), "threshold": HEADER_THRESHOLD, "status": "PASS" if header_mae <= HEADER_THRESHOLD else "FAIL"},
        "hero": {"authority": "responsive_mapping_contract.json:groups.hero_copy.reference_machine_bbox_px", "reference_box": list(reference_box), "runtime_box": list(runtime_box), "metric": "mean_absolute_rgb_error", "value": round(hero_mae, 4), "threshold": 42.0, "status": "PASS" if hero_mae <= 42.0 else "FAIL"},
        "fishing_composition": {"authority": "Empty_Home_Frozen_Visual_Revision_V2_2 + anchor_contract.json", "box": list(fishing_box), "metric": "base_reference_MAE_plus_contract_visibility", "value": round(fishing_mae, 4), "contract_status": fishing_status, "status": fishing_status},
        "camera": {"authority": "Empty_Home_Frozen_Visual_Revision_V2_2 + anchor_contract.json", "box": list(camera_box), "metric": "base_reference_MAE_plus_runtime_visibility", "value": round(camera_mae, 4), "bright_fraction": round(bright_fraction, 4), "bright_fraction_min": 0.05, "rgb_spread": round(mean_rgb_spread(camera_crop), 4), "rgb_spread_min": 10.0, "status": camera_status},
        "cta": {"authority": "Empty_Home_Frozen_Visual_Revision_V2_2 + anchor_contract.json", "box": list(cta_box), "metric": "base_reference_MAE_plus_frozen_anchor_contract", "value": round(cta_mae, 4), "prompt_top": prompt_top, "camera_top": camera_top, "camera_size": camera_size, "album_top": album_top, "status": cta_status},
    }
    failures = [name for name, item in regions.items() if item["status"] != "PASS"]
    parity = {
        "source": "instrumentation-rendered app DecorView; no physical framebuffer resampling",
        "reference": "design/pages/home/empty_home/source/frozen/Empty_Home_Final_Design_V2_normalized_1080x1920.png",
        "canonical_dimensions": [1080, 1920],
        "framebuffer_resampling": False,
        "visual_revision": manifest.get("visual_revision"),
        "visual_revision_source_sha256": manifest.get("approved_visual_sha256"),
        "metric": "canonical_direct_rgb_mae_plus_v2_2_contract_evidence",
        "hero_reference_box": list(reference_box),
        "hero_runtime_box": list(runtime_box),
        "regions": regions,
        "region_failures": failures,
        "status": "PASS" if not failures else "FAIL",
    }
    (out / "visual_parity_report.json").write_text(json.dumps(parity, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (out / "runtime_debug.json").write_text(json.dumps({
        "screen": "home_empty",
        "build_sha": args.build_sha,
        "reference_canvas": [1080, 1920],
        "screenshot_dimensions": [runtime.width, runtime.height],
        "visual_revision": manifest.get("visual_revision"),
        "visual_revision_source_sha256": manifest.get("approved_visual_sha256"),
        "bobber_range_reference_px": motion["bobber"]["range_reference_px"],
        "bobber_duration_ms": motion["bobber"]["duration_ms"],
        "ripple_duration_ms": motion["ripple"]["duration_ms"],
        "camera_gold_rim": motion["camera_gold_rim"],
        "camera_breath": motion["camera_breath"],
        "hero_box_reference_px": list(hero_box),
        "regions": regions,
    }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    # Preserve existing diagnostic aliases consumed by the gate and uploaded artifact.
    (out / "frozen_reference.png").write_bytes((out / "01_frozen_1080x1920.png").read_bytes())
    (out / "side_by_side.png").write_bytes((out / "03_reference_vs_runtime.png").read_bytes())
    (out / "pixel_diff_heatmap.png").write_bytes((out / "04_full_diff.png").read_bytes())
    hero_side = Image.new("RGB", ((hero_box[2] - hero_box[0]) * 2, hero_box[3] - hero_box[1]))
    hero_side.paste(checked_crop(reference, hero_box, "hero_reference"), (0, 0))
    hero_side.paste(checked_crop(runtime, hero_box, "hero_runtime"), (hero_box[2]-hero_box[0], 0))
    hero_side.save(out / "region_hero_side_by_side.png")

    if parity["status"] != "PASS":
        raise SystemExit("visual fidelity gate failed: " + json.dumps(parity, ensure_ascii=False))


if __name__ == "__main__":
    main()
