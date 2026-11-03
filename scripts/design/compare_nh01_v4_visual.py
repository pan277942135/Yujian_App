#!/usr/bin/env python3
"""Project and compare NH01 against the private current-user frozen design.

The original upload is read-only and is resolved only through the V4 manifest.
Responsive projections compose per-component crops at the Android runtime's
measured bounds; the full page is never uniformly stretched to make a match.
Pixel differences remain diagnostic and do not override layout/interaction gates.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any

from PIL import Image, ImageChops, ImageDraw, ImageEnhance, ImageFilter, ImageOps

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_MANIFEST = ROOT / "design/pages/home/normal_home/engineering/NH01_V4_VISUAL_BASELINE_MANIFEST.json"
DEFAULT_SOURCE = Path("visual_refs/临时/有数据首页面/渔见湖光鱼获首页.png")
DESIGN_DP = (360, 640)
# Source rectangles are measured from the registered 941x1672 frozen image.
# They are diagnostic projection regions only and are never exported as app assets.
COMPONENTS = (
    ("brand", (79, 88, 232, 174), "gemini-home-brand-title", "layout_bounds_logical_window_px"),
    ("avatar", (767, 87, 871, 189), "gemini-home-avatar-visual", "layout_bounds_logical_window_px"),
    ("statistics", (170, 250, 784, 373), "gemini-home-stats-bar", "layout_bounds_logical_window_px"),
    ("section_header", (69, 420, 872, 500), "gemini-home-section-header", "layout_bounds_logical_window_px"),
    ("hero", (145, 515, 796, 1280), "gemini-home-catch-card-nh01-catch-001", "layout_bounds_logical_window_px"),
    ("left_peek", (0, 551, 117, 1068), "gemini-home-catch-neighbor-nh01-catch-002", "visible_bounds_clipped_window_px"),
    ("right_peek", (824, 551, 941, 1068), "gemini-home-catch-neighbor-nh01-catch-003", "visible_bounds_clipped_window_px"),
    ("cta", (285, 1304, 660, 1372), "gemini-home-cta-label", "layout_bounds_logical_window_px"),
    ("camera_art", (354, 1362, 588, 1605), "gemini-home-camera-art", "layout_bounds_logical_window_px"),
)


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
        raise SystemExit("REFERENCE_ASSET_BLOCKED: current V4 source image is unavailable; 229571.png is never a fallback")
    actual_sha = sha256(source)
    if actual_sha != manifest.get("sha256"):
        raise SystemExit(f"REFERENCE_ASSET_BLOCKED: V4 source SHA mismatch: {actual_sha} != {manifest.get('sha256')}")
    try:
        with Image.open(source) as image:
            actual_format, actual_size, actual_mode = image.format, image.size, image.mode
            has_icc, has_srgb = bool(image.info.get("icc_profile")), "srgb" in image.info
    except OSError as error:
        raise SystemExit(f"REFERENCE_ASSET_BLOCKED: cannot decode current V4 source: {error}") from error
    expected_size = (manifest.get("width_px"), manifest.get("height_px"))
    if actual_format != manifest.get("image_format") or actual_size != expected_size:
        raise SystemExit(f"REFERENCE_ASSET_BLOCKED: source metadata mismatch: format={actual_format}, dimensions={actual_size}, expected={manifest.get('image_format')}/{expected_size}")
    if actual_mode != manifest.get("color_mode"):
        raise SystemExit(f"REFERENCE_ASSET_BLOCKED: color mode {actual_mode} != {manifest.get('color_mode')}")
    if manifest.get("color_profile") == "UNPROFILED_RGB_NO_EMBEDDED_ICC_OR_SRGB_CHUNK" and (has_icc or has_srgb):
        raise SystemExit("REFERENCE_ASSET_BLOCKED: source now contains an ICC or sRGB profile absent from manifest")
    return manifest, source


def read_runtime(path: Path | None) -> dict[str, Any]:
    if path is None:
        return {}
    try:
        runtime = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise SystemExit(f"RUNTIME_METRICS_BLOCKED: cannot read runtime JSON: {error}") from error
    return runtime


def parse_bounds(value: str) -> tuple[int, int, int, int]:
    try:
        x, y, width, height = (int(part.strip()) for part in value.split(","))
    except (TypeError, ValueError) as error:
        raise argparse.ArgumentTypeError("bounds must be x,y,width,height integers") from error
    if min(width, height) <= 0:
        raise argparse.ArgumentTypeError("bounds width and height must be positive")
    return x, y, width, height


def get_bounds(runtime: dict[str, Any], key: str) -> dict[str, Any]:
    value = runtime.get(key)
    if isinstance(value, dict):
        return value
    metrics = runtime.get("metrics")
    if isinstance(metrics, dict) and isinstance(metrics.get(key), dict):
        return metrics[key]
    return {}


def safe_insets(runtime: dict[str, Any]) -> tuple[int, int, int, int]:
    raw = runtime.get("safe_insets_dp", {})
    density = float(runtime.get("density", 1.0) or 1.0)
    # Compose bounds are captured in the configured logical-pixel space. The
    # matrix pins that space to 160dpi; multiplying inset dp by density matches it.
    top = round(float(raw.get("top", 0)) * density)
    bottom = round(float(raw.get("bottom", 0)) * density)
    start = round(float(raw.get("start", 0)) * density)
    end = round(float(raw.get("end", 0)) * density)
    return start, top, end, bottom


def crop_safe_content(screenshot: Image.Image, runtime: dict[str, Any], override: tuple[int, int, int, int] | None) -> tuple[Image.Image, tuple[int, int, int, int]]:
    image = screenshot.convert("RGB")
    bounds = override
    if bounds is None:
        start, top, end, bottom = safe_insets(runtime)
        bounds = (start, top, image.width - start - end, image.height - top - bottom)
    x, y, width, height = bounds
    if min(width, height) <= 0 or x < 0 or y < 0 or x + width > image.width or y + height > image.height:
        raise SystemExit(f"RUNTIME_METRICS_BLOCKED: safe content bounds {bounds} exceed screenshot {image.size}")
    return image.crop((x, y, x + width, y + height)), bounds


def resized_component(source: Image.Image, crop: tuple[int, int, int, int], size: tuple[int, int], rounded: bool = False) -> Image.Image:
    patch = source.crop(crop).resize(size, Image.Resampling.LANCZOS).convert("RGBA")
    if rounded:
        alpha = Image.new("L", size, 0)
        draw = ImageDraw.Draw(alpha)
        draw.rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), radius=max(1, round(min(size) * 0.055)), fill=255)
        patch.putalpha(alpha)
    return patch


def project(source: Image.Image, size: tuple[int, int], runtime: dict[str, Any], state: str = "initial") -> tuple[Image.Image, dict[str, Any], dict[str, tuple[int, int, int, int]]]:
    if min(size) <= 0:
        raise ValueError(f"Projection dimensions must be positive, got {size}")
    design = source.convert("RGB").resize(DESIGN_DP, Image.Resampling.LANCZOS)
    canvas = ImageOps.fit(design, size, method=Image.Resampling.LANCZOS, centering=(0.5, 0.5)).convert("RGBA")
    # Remove the flattened source components from the background layer by
    # locally blending their areas. Their responsive component projections are
    # overlaid below at the measured API28 window bounds.
    scale = max(size[0] / DESIGN_DP[0], size[1] / DESIGN_DP[1])
    offset_x = (size[0] - DESIGN_DP[0] * scale) / 2
    offset_y = (size[1] - DESIGN_DP[1] * scale) / 2
    blur = canvas.filter(ImageFilter.GaussianBlur(radius=max(16, round(min(size) * 0.075))))
    mask = Image.new("L", size, 0)
    mask_draw = ImageDraw.Draw(mask)
    for _, source_box, _, _ in COMPONENTS:
        x0, y0, x1, y1 = source_box
        dp_box = (x0 * 360 / 941, y0 * 640 / 1672, x1 * 360 / 941, y1 * 640 / 1672)
        box = tuple(round(v * scale + (offset_x if i % 2 == 0 else offset_y)) for i, v in enumerate(dp_box))
        margin = max(2, round(min(size) * 0.004))
        mask_draw.rectangle((box[0] - margin, box[1] - margin, box[2] + margin, box[3] + margin), fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(radius=max(4, round(min(size) * 0.018))))
    canvas.paste(blur, (0, 0), mask)

    layout = get_bounds(runtime, "layout_bounds_logical_window_px")
    visible = get_bounds(runtime, "visible_bounds_clipped_window_px")
    tag_overrides = runtime.get(f"comparison_tags_{state}", {})
    start, top, end, bottom = safe_insets(runtime)
    projected: dict[str, tuple[int, int, int, int]] = {}
    source_w, source_h = source.size
    for name, source_box, default_tag, bounds_kind in COMPONENTS:
        tag = tag_overrides.get(name, default_tag) if isinstance(tag_overrides, dict) else default_tag
        if not isinstance(tag, str) or not tag:
            continue
        bound_map = visible if bounds_kind.startswith("visible") else layout
        bounds = bound_map.get(tag)
        if not isinstance(bounds, dict):
            continue
        try:
            x0 = round(float(bounds["left"]) - start)
            y0 = round(float(bounds["top"]) - top)
            x1 = round(float(bounds["right"]) - start)
            y1 = round(float(bounds["bottom"]) - top)
        except (KeyError, TypeError, ValueError):
            continue
        x0, y0 = max(0, x0), max(0, y0)
        x1, y1 = min(size[0], x1), min(size[1], y1)
        if x1 <= x0 or y1 <= y0:
            continue
        patch = resized_component(source, source_box, (x1 - x0, y1 - y0), rounded=(name == "hero"))
        canvas.alpha_composite(patch, (x0, y0))
        projected[name] = (x0, y0, x1, y1)
    meta = {
        "method": "component_bounds_from_api28_runtime_with_frozen_design_crops",
        "safe_content_size_px": {"width": size[0], "height": size[1]},
        "design_canvas_dp": {"width": 360, "height": 640},
        "component_bounds_count": len(projected),
        "component_bounds_px": {name: list(rect) for name, rect in projected.items()},
        "background_transform": "LakeGlassViewportTransform.centerCrop reference canvas; local flattened components softened before component projection",
        "geometry_source": "runtime component Bounds in the case JSON; component/spacing contracts are checked independently by the Android matrix",
        "photo_note": "Frozen screenshot crops appear only inside this local diagnostic projection; they are not exported or used as production/photo fixture assets.",
    }
    return canvas, meta, projected


def mean_abs_rgb(a: Image.Image, b: Image.Image) -> float:
    diff = ImageChops.difference(a.convert("RGB"), b.convert("RGB"))
    hist = diff.histogram()
    total = max(1, a.width * a.height)
    return sum((i % 256) * count for i, count in enumerate(hist)) / (total * 3)


def compare(reference: Image.Image, screenshot: Image.Image, output: Path,
            bounds: tuple[int, int, int, int] | None, runtime: dict[str, Any], manifest: dict[str, Any],
            state: str = "initial") -> dict[str, Any]:
    actual, safe_bounds = crop_safe_content(screenshot, runtime, bounds)
    projection_runtime = dict(runtime)
    if state == "after_swipe":
        projection_runtime["layout_bounds_logical_window_px"] = runtime.get(
            "layout_bounds_after_swipe_logical_window_px", runtime.get("layout_bounds_logical_window_px", {})
        )
        projection_runtime["visible_bounds_clipped_window_px"] = runtime.get(
            "visible_bounds_after_swipe_clipped_window_px", runtime.get("visible_bounds_clipped_window_px", {})
        )
    target, target_meta, component_bounds = project(reference, actual.size, projection_runtime, state)
    target_rgb = target.convert("RGB")
    target.save(output / "NH01_RESPONSIVE_REFERENCE_PROJECTION.png", optimize=True)
    pair = Image.new("RGB", (actual.width * 2, actual.height), (245, 247, 249))
    pair.paste(target_rgb, (0, 0))
    pair.paste(actual, (actual.width, 0))
    pair.save(output / "NH01_V4_SIDE_BY_SIDE.png", optimize=True)
    raw_diff = ImageChops.difference(target_rgb, actual)
    raw_diff.save(output / "NH01_V4_ABSOLUTE_DIFFERENCE.png", optimize=True)
    Image.blend(target_rgb, actual, 0.5).save(output / "NH01_V4_OVERLAY_50PCT.png", optimize=True)
    gray = ImageOps.grayscale(raw_diff)
    ImageEnhance.Contrast(gray).enhance(3.0).save(output / "NH01_V4_DIFFERENCE_HEATMAP.png", optimize=True)
    component_diffs = {}
    for name, (x0, y0, x1, y1) in component_bounds.items():
        component_diffs[name] = {
            "bounds_px": [x0, y0, x1, y1],
            "mean_absolute_rgb_difference_0_255": round(mean_abs_rgb(target_rgb.crop((x0, y0, x1, y1)), actual.crop((x0, y0, x1, y1))), 4),
        }
    histogram = gray.histogram()
    mean_gray_error = sum(i * count for i, count in enumerate(histogram)) / max(1, actual.width * actual.height)
    return {
        "status": "DIAGNOSTIC_ONLY_VISUAL_REVIEW_REQUIRED",
        "baseline_id": manifest["baseline_id"],
        "baseline_sha256": manifest["sha256"],
        "render_size_px": {"width": actual.width, "height": actual.height},
        "runtime_safe_content_bounds_px": safe_bounds,
        "target_projection": target_meta,
        "component_differences": component_diffs,
        "compared_pixels": actual.width * actual.height,
        "mean_grayscale_absolute_difference_0_255": round(mean_gray_error, 4),
        "visual_acceptance": "NOT_DECIDED_BY_PIXEL_SIMILARITY",
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    parser.add_argument("--baseline", type=Path, default=None, help="Current local original upload; never re-encoded.")
    parser.add_argument("--render", type=Path, required=True, help="Actual emulator screenshot.")
    parser.add_argument("--metrics-json", type=Path, required=True, help="Actual API28 case report with component bounds/insets.")
    parser.add_argument("--content-bounds", type=parse_bounds, default=None,
                        help="Optional x,y,width,height safe-content crop; takes precedence over measured Insets.")
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--profile-id", default="UNKNOWN")
    parser.add_argument("--state", choices=("initial", "after_swipe"), default="initial")
    parser.add_argument("--api-level", type=int, default=None)
    parser.add_argument("--avd-id", default=None)
    args = parser.parse_args()
    manifest, baseline_path = load_baseline(args.manifest.resolve(), args.baseline)
    if not args.render.is_file():
        raise SystemExit(f"RUNTIME_SCREENSHOT_BLOCKED: missing actual render {args.render}")
    args.output_dir.mkdir(parents=True, exist_ok=True)
    runtime = read_runtime(args.metrics_json)
    try:
        with Image.open(baseline_path) as source:
            reference = source.copy()
        with Image.open(args.render) as image:
            screenshot = image.copy()
    except OSError as error:
        raise SystemExit(f"RUNTIME_SCREENSHOT_BLOCKED: cannot decode image: {error}") from error
    record = compare(reference, screenshot, args.output_dir, runtime=runtime, manifest=manifest,
                     bounds=args.content_bounds, state=args.state)
    record.update({
        "render_sha256": sha256(args.render),
        "runtime_metrics_file": str(args.metrics_json),
        "profile_id": args.profile_id,
        "state": args.state,
        "api_level": args.api_level,
        "avd_id": args.avd_id,
        "baseline_source_file": manifest["filename"],
    })
    (args.output_dir / "NH01_V4_VISUAL_DIFF_SUMMARY.json").write_text(
        json.dumps(record, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print(json.dumps(record, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
