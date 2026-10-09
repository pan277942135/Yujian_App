#!/usr/bin/env python3
"""Compare Fish Guide screenshots with frozen geometry without resizing full captures."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageStat


REGIONS = {
    "header": (0.02, 0.02, 0.96, 0.18),
    "progress": (0.42, 0.14, 0.54, 0.16),
    "card": (134 / 941, 442 / 1672, 673 / 941, 923 / 1672),
}
THRESHOLDS = {"header": 105.0, "progress": 105.0, "card": 115.0}
EXPECTED_CARD = (134 / 941, 442 / 1672, 673 / 941, 923 / 1672)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def bbox_from_fraction(image: Image.Image, fraction: tuple[float, float, float, float]) -> tuple[int, int, int, int]:
    x, y, width, height = fraction
    return (
        round(image.width * x),
        round(image.height * y),
        round(image.width * (x + width)),
        round(image.height * (y + height)),
    )


def normalized_mae(reference: Image.Image, actual: Image.Image) -> float:
    # Resize only the compared region; native full screenshots remain byte-identical.
    reference_region = reference.resize((256, 256), Image.Resampling.LANCZOS)
    actual_region = actual.resize((256, 256), Image.Resampling.LANCZOS)
    return sum(ImageStat.Stat(ImageChops.difference(reference_region, actual_region)).mean) / 3.0


def geometry_report(metadata: dict) -> dict:
    geometry = metadata.get("geometry_bounds", {})
    card = geometry.get("selected_card_bounds_in_window")
    origin = metadata.get("window_origin_on_screen_px", {})
    native_w = int(metadata.get("screenshot_width_px", 0))
    native_h = int(metadata.get("screenshot_height_px", 0))
    if not card or native_w <= 0 or native_h <= 0:
        return {"status": "MISSING", "reason": "selected card bounds or native dimensions missing"}
    x0 = (float(card["left"]) + float(origin.get("x", 0))) / native_w
    y0 = (float(card["top"]) + float(origin.get("y", 0))) / native_h
    x1 = (float(card["right"]) + float(origin.get("x", 0))) / native_w
    y1 = (float(card["bottom"]) + float(origin.get("y", 0))) / native_h
    actual = (x0, y0, x1 - x0, y1 - y0)
    expected = EXPECTED_CARD
    delta = [abs(actual[i] - expected[i]) for i in range(4)]
    tolerance = [0.035, 0.035, 0.035, 0.035]
    return {
        "status": "PASS" if all(value <= limit for value, limit in zip(delta, tolerance)) else "FAIL",
        "coordinate_space": "normalized native screenshot pixels",
        "expected_xywh": expected,
        "actual_xywh": actual,
        "absolute_delta_xywh": delta,
        "tolerance_xywh": tolerance,
    }


def compare(label: str, reference_path: Path, native_path: Path, surface_path: Path, metadata_path: Path, out: Path) -> dict:
    reference = Image.open(reference_path).convert("RGB")
    native = Image.open(native_path).convert("RGB")
    surface = Image.open(surface_path).convert("RGB")
    metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    observed_native_sha = sha256(native_path)
    observed_surface_sha = sha256(surface_path)

    errors = []
    if [native.width, native.height] != [int(metadata.get("screenshot_width_px", -1)), int(metadata.get("screenshot_height_px", -1))]:
        errors.append("native screenshot dimensions do not match metadata")
    if observed_native_sha != metadata.get("native_screenshot_sha256"):
        errors.append("native screenshot SHA-256 does not match metadata")
    if observed_surface_sha != metadata.get("app_surface_screenshot_sha256"):
        errors.append("app surface SHA-256 does not match metadata")
    apk_sha = str(metadata.get("apk_sha256", ""))
    if len(apk_sha) != 64:
        errors.append("APK SHA-256 missing")

    metrics = {}
    for name, fraction in REGIONS.items():
        reference_crop = reference.crop(bbox_from_fraction(reference, fraction))
        native_crop = native.crop(bbox_from_fraction(native, fraction))
        mapped_native = native_crop.resize(reference_crop.size, Image.Resampling.LANCZOS)
        mae = sum(ImageStat.Stat(ImageChops.difference(reference_crop, mapped_native)).mean) / 3.0
        metrics[name] = {
            "reference_region_px": list(reference_crop.size),
            "native_region_px": list(native_crop.size),
            "coordinate_aligned_pixel_mae": round(mae, 4),
            "normalized_mae_256": round(normalized_mae(reference_crop, native_crop), 4),
            "threshold_mae": THRESHOLDS[name],
        }
        overlay = Image.blend(reference_crop, mapped_native, 0.5)
        diff = ImageChops.difference(reference_crop, mapped_native).point(lambda value: min(255, value * 3))
        overlay.save(out / f"{label}_{name}_overlay.png")
        diff.save(out / f"{label}_{name}_diff.png")

    # Validate that app-surface capture corresponds to the recorded Compose root,
    # while retaining the full native screenshot for independent inspection.
    safe_map = metadata.get("safe_coordinate_map", {})
    expected_surface = (round(float(safe_map.get("width_px", 0))), round(float(safe_map.get("height_px", 0))))
    surface_size_delta = [abs(surface.width - expected_surface[0]), abs(surface.height - expected_surface[1])]
    if any(delta > 2 for delta in surface_size_delta):
        errors.append("app surface dimensions do not match safe coordinate map")

    geometry = geometry_report(metadata)
    asset_status = str(metadata.get("selected_cover_hero_status") or "MISSING").upper()
    asset_version = metadata.get("selected_cover_hero_version_id")
    asset_url = metadata.get("selected_cover_hero_image_url")
    asset_ready = asset_status == "ACTIVE" and asset_version is not None and bool(asset_url)
    visual_pass = all(metrics[name]["coordinate_aligned_pixel_mae"] <= THRESHOLDS[name] for name in THRESHOLDS)
    status = "PASS" if not errors and asset_ready and visual_pass and geometry.get("status") == "PASS" else "FAIL"
    blocker = None
    if not asset_ready:
        blocker = "BLOCKED_MISSING_PUBLISHED_COVER_HERO"
    report = {
        "status": status,
        "blocker": blocker,
        "reference": str(reference_path),
        "native_runtime_screenshot": str(native_path),
        "app_surface_screenshot": str(surface_path),
        "native_runtime_dimensions_px": [native.width, native.height],
        "reference_dimensions_px": [reference.width, reference.height],
        "full_capture_resized": False,
        "native_screenshot_sha256": observed_native_sha,
        "app_surface_screenshot_sha256": observed_surface_sha,
        "apk_sha256": apk_sha,
        "device": {key: metadata.get(key) for key in ("device_model", "sdk", "density", "density_dpi", "font_scale", "activity", "insets_px")},
        "safe_coordinate_map": safe_map,
        "geometry": geometry,
        "cover_hero": {"status": asset_status, "version_id": asset_version, "image_url": asset_url},
        "metrics": metrics,
        "errors": errors,
    }
    (out / f"{label}_visual_parity_report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print("FISH_GUIDE_VISUAL_PARITY", label, json.dumps(report, ensure_ascii=False))
    return report


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--reference-dir", type=Path, required=True)
    parser.add_argument("--evidence-dir", type=Path, required=True)
    args = parser.parse_args()
    out = args.evidence_dir
    reports = []
    cases = [
        ("fish_guide_lit", "fish_guide_v2.png", "fish_guide_lit.png"),
        ("fish_guide_unlit", "supplemental/fish_guide_unlit_state.png", "fish_guide_unlit.png"),
    ]
    for label, reference_name, capture_name in cases:
        native = out / capture_name
        surface = out / capture_name.replace(".png", "_app_surface.png")
        metadata = out / capture_name.replace(".png", "_metadata.json")
        reports.append(compare(
            label,
            args.reference_dir / reference_name,
            native,
            surface,
            metadata,
            out,
        ))
    if any(report["blocker"] for report in reports):
        return 2
    return 0 if all(report["status"] == "PASS" for report in reports) else 1


if __name__ == "__main__":
    raise SystemExit(main())
