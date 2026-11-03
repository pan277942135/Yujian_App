#!/usr/bin/env python3
"""Compare an actual NH01 V4 runtime capture to the current user-frozen baseline.

The baseline is resolved only through the V4 manifest. This tool creates diagnostic
projections and never issues a visual PASS. It opens the original reference read-only;
all outputs go to the caller-selected local evidence directory.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any

from PIL import Image, ImageChops, ImageEnhance, ImageOps

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_MANIFEST = ROOT / "design/pages/home/normal_home/engineering/NH01_V4_VISUAL_BASELINE_MANIFEST.json"
DEFAULT_SOURCE = Path("visual_refs/临时/有数据首页面/渔见湖光鱼获首页.png")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def load_baseline(manifest_path: Path, source_override: Path | None) -> tuple[dict[str, Any], Path]:
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise SystemExit(f"REFERENCE_ASSET_BLOCKED: cannot read V4 manifest: {error}") from error
    if manifest.get("authority") != "CURRENT_USER_FROZEN_DESIGN":
        raise SystemExit("REFERENCE_ASSET_BLOCKED: manifest authority is not CURRENT_USER_FROZEN_DESIGN")

    source = source_override or DEFAULT_SOURCE
    if not source.is_absolute():
        source = Path.cwd() / source
    if not source.is_file():
        raise SystemExit(
            "REFERENCE_ASSET_BLOCKED: current V4 source image is unavailable; "
            "the historical 229571.png is never a fallback"
        )
    actual_sha = sha256(source)
    if actual_sha != manifest.get("sha256"):
        raise SystemExit(
            f"REFERENCE_ASSET_BLOCKED: V4 source SHA mismatch: {actual_sha} != {manifest.get('sha256')}"
        )
    try:
        with Image.open(source) as image:
            actual_format, actual_size, actual_mode = image.format, image.size, image.mode
            has_icc = bool(image.info.get("icc_profile"))
            has_srgb = "srgb" in image.info
    except OSError as error:
        raise SystemExit(f"REFERENCE_ASSET_BLOCKED: cannot decode current V4 source: {error}") from error
    expected_size = (manifest.get("width_px"), manifest.get("height_px"))
    if actual_format != manifest.get("image_format") or actual_size != expected_size:
        raise SystemExit(
            f"REFERENCE_ASSET_BLOCKED: source metadata mismatch: "
            f"format={actual_format}, dimensions={actual_size}, expected={manifest.get('image_format')}/{expected_size}"
        )
    if actual_mode != manifest.get("color_mode"):
        raise SystemExit(f"REFERENCE_ASSET_BLOCKED: color mode {actual_mode} != {manifest.get('color_mode')}")
    if manifest.get("color_profile") == "UNPROFILED_RGB_NO_EMBEDDED_ICC_OR_SRGB_CHUNK" and (has_icc or has_srgb):
        raise SystemExit("REFERENCE_ASSET_BLOCKED: source now contains an ICC or sRGB profile absent from manifest")
    return manifest, source


def parse_bounds(value: str) -> tuple[int, int, int, int]:
    try:
        x, y, width, height = (int(part.strip()) for part in value.split(","))
    except (TypeError, ValueError) as error:
        raise argparse.ArgumentTypeError("bounds must be x,y,width,height integers") from error
    if min(width, height) <= 0:
        raise argparse.ArgumentTypeError("bounds width and height must be positive")
    return x, y, width, height


def read_metrics_bounds(metrics_path: Path | None) -> tuple[int, int, int, int] | None:
    if metrics_path is None:
        return None
    try:
        metrics = json.loads(metrics_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise SystemExit(f"RUNTIME_METRICS_BLOCKED: cannot read runtime JSON: {error}") from error
    for key in ("screenshot_content_bounds_px", "content_bounds_px", "window_bounds_px"):
        value = metrics.get(key)
        if isinstance(value, dict):
            try:
                return (int(value.get("x", 0)), int(value.get("y", 0)),
                        int(value["width"]), int(value["height"]))
            except (KeyError, TypeError, ValueError):
                continue
    return None


def project(source: Image.Image, width: int, height: int) -> tuple[Image.Image, dict[str, Any]]:
    if width <= 0 or height <= 0:
        raise ValueError(f"Projection dimensions must be positive, got {width}x{height}")
    fitted = ImageOps.contain(source.convert("RGBA"), (width, height), method=Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    x, y = (width - fitted.width) // 2, (height - fitted.height) // 2
    canvas.alpha_composite(fitted, (x, y))
    return canvas, {
        "width_px": fitted.width, "height_px": fitted.height,
        "offset_x_px": x, "offset_y_px": y, "distortion": "none",
        "fit_policy": "uniform_scale_centered_letterbox"
    }


def compare(reference: Image.Image, screenshot: Image.Image, output: Path,
            bounds: tuple[int, int, int, int] | None, manifest: dict[str, Any]) -> dict[str, Any]:
    actual = screenshot.convert("RGB")
    if bounds:
        x, y, width, height = bounds
        if x < 0 or y < 0 or x + width > actual.width or y + height > actual.height:
            raise SystemExit(f"RUNTIME_METRICS_BLOCKED: content bounds {bounds} exceed screenshot {actual.size}")
        actual = actual.crop((x, y, x + width, y + height))

    target, target_meta = project(reference, actual.width, actual.height)
    target.save(output / "NH01_V4_TARGET_PROJECTION.png", optimize=True)
    composite = Image.new("RGB", actual.size, (232, 237, 240))
    composite.paste(target.convert("RGB"), mask=target.getchannel("A"))
    pair = Image.new("RGB", (actual.width * 2, actual.height), (245, 247, 249))
    pair.paste(composite, (0, 0))
    pair.paste(actual, (actual.width, 0))
    pair.save(output / "NH01_V4_SIDE_BY_SIDE.png", optimize=True)

    raw_diff = ImageChops.difference(target.convert("RGB"), actual)
    mask = target.getchannel("A")
    diff = Image.new("RGB", actual.size, (0, 0, 0))
    diff.paste(raw_diff, mask=mask)
    diff.save(output / "NH01_V4_ABSOLUTE_DIFFERENCE.png", optimize=True)
    Image.blend(composite, actual, 0.5).save(output / "NH01_V4_OVERLAY_50PCT.png", optimize=True)
    gray = ImageOps.grayscale(diff)
    ImageEnhance.Contrast(gray).enhance(3.0).save(output / "NH01_V4_DIFFERENCE_HEATMAP.png", optimize=True)

    histogram = gray.histogram()
    comparison_pixels = max(1, mask.histogram()[255])
    mean_gray_error = sum(i * count for i, count in enumerate(histogram)) / comparison_pixels
    return {
        "status": "DIAGNOSTIC_ONLY_VISUAL_REVIEW_REQUIRED",
        "baseline_id": manifest["baseline_id"],
        "baseline_sha256": manifest["sha256"],
        "render_size_px": {"width": actual.width, "height": actual.height},
        "runtime_content_bounds_px": bounds,
        "target_projection": target_meta,
        "compared_pixels": comparison_pixels,
        "mean_grayscale_absolute_difference_0_255": round(mean_gray_error, 4),
        "reference_fit_note": "The projection preserves aspect ratio and letterboxes. It is diagnostic only and does not implement responsive layout or replace contract/component checks."
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    parser.add_argument("--baseline", type=Path, default=None, help="Current local original upload; never re-encoded.")
    parser.add_argument("--render", type=Path, required=True, help="Actual emulator screenshot.")
    parser.add_argument("--metrics-json", type=Path, default=None)
    parser.add_argument("--content-bounds", type=parse_bounds, default=None,
                        help="Optional x,y,width,height crop; takes precedence over metrics JSON.")
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--profile-id", default="UNKNOWN")
    parser.add_argument("--api-level", type=int, default=None)
    parser.add_argument("--avd-id", default=None)
    args = parser.parse_args()

    manifest, baseline_path = load_baseline(args.manifest.resolve(), args.baseline)
    if not args.render.is_file():
        raise SystemExit(f"RUNTIME_SCREENSHOT_BLOCKED: missing actual render {args.render}")
    args.output_dir.mkdir(parents=True, exist_ok=True)
    try:
        with Image.open(baseline_path) as source:
            reference = source.copy()
        with Image.open(args.render) as image:
            screenshot = image.copy()
    except OSError as error:
        raise SystemExit(f"RUNTIME_SCREENSHOT_BLOCKED: cannot decode image: {error}") from error

    bounds = args.content_bounds or read_metrics_bounds(args.metrics_json)
    record = compare(reference, screenshot, args.output_dir, bounds, manifest)
    record.update({
        "render_sha256": sha256(args.render),
        "profile_id": args.profile_id,
        "api_level": args.api_level,
        "avd_id": args.avd_id,
        "baseline_source_file": manifest["filename"],
        "visual_acceptance": "NOT_DECIDED_BY_PIXEL_SIMILARITY"
    })
    (args.output_dir / "NH01_V4_VISUAL_DIFF_SUMMARY.json").write_text(
        json.dumps(record, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print(json.dumps(record, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
