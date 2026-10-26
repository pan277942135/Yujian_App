#!/usr/bin/env python3
"""Create evidence-only screenshot comparisons for NH01 Design Manager canvases.

The M03 PNG is a flattened screenshot and has no per-layer annotation. The
color masks below therefore provide diagnostic screenshot bounds only; they
are explicitly not frozen component geometry or a visual PASS gate.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Callable

from PIL import Image, ImageChops, ImageEnhance, ImageOps


RGBPredicate = Callable[[int, int, int], bool]


def connected_components(image: Image.Image, roi: tuple[int, int, int, int], predicate: RGBPredicate) -> list[dict]:
    left, top, right, bottom = roi
    crop = image.crop(roi).convert("RGB")
    width, height = crop.size
    pixels = crop.load()
    mask = bytearray(width * height)
    for y in range(height):
        offset = y * width
        for x in range(width):
            r, g, b = pixels[x, y]
            if predicate(r, g, b):
                mask[offset + x] = 1

    components: list[dict] = []
    for start, active in enumerate(mask):
        if not active:
            continue
        mask[start] = 0
        stack = [start]
        min_x = max_x = start % width
        min_y = max_y = start // width
        area = 0
        while stack:
            index = stack.pop()
            x, y = index % width, index // width
            area += 1
            min_x, max_x = min(min_x, x), max(max_x, x)
            min_y, max_y = min(min_y, y), max(max_y, y)
            for dy in (-1, 0, 1):
                ny = y + dy
                if ny < 0 or ny >= height:
                    continue
                for dx in (-1, 0, 1):
                    nx = x + dx
                    if dx == 0 and dy == 0 or nx < 0 or nx >= width:
                        continue
                    neighbor = ny * width + nx
                    if mask[neighbor]:
                        mask[neighbor] = 0
                        stack.append(neighbor)
        if area >= 3:
            components.append({
                "area_px": area,
                "bbox_css": {
                    "x": left + min_x,
                    "y": top + min_y,
                    "width": max_x - min_x + 1,
                    "height": max_y - min_y + 1,
                },
            })
    return sorted(components, key=lambda item: item["area_px"], reverse=True)


def compare_bbox(reference: dict | None, rendered: dict | None) -> dict | None:
    if not reference or not rendered:
        return None
    return {key: rendered[key] - reference[key] for key in ("x", "y", "width", "height")}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--render", required=True, type=Path)
    parser.add_argument("--reference", required=True, type=Path)
    parser.add_argument("--dom-json", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--profile-prefix", default="NH01_M03")
    args = parser.parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    prefix = args.profile_prefix

    rendered = Image.open(args.render).convert("RGB")
    source = Image.open(args.reference).convert("RGB")
    dom = json.loads(args.dom_json.read_text(encoding="utf-8"))
    viewport = dom.get("viewport", {})
    expected_size = (int(viewport.get("width", 0)), int(viewport.get("height", 0)))
    if rendered.size != expected_size:
        raise SystemExit(f"Expected a {expected_size[0]}x{expected_size[1]} CSS canvas screenshot; got {rendered.size}")
    reference = source.resize(rendered.size, Image.Resampling.LANCZOS)

    side = Image.new("RGB", (rendered.width * 2, rendered.height), "white")
    side.paste(reference, (0, 0))
    side.paste(rendered, (rendered.width, 0))
    side.save(args.output_dir / f"{prefix}_Frozen_Left_Render_Right.png", optimize=True)
    Image.blend(reference, rendered, 0.5).save(args.output_dir / f"{prefix}_Overlay_50pct.png", optimize=True)

    difference = ImageChops.difference(reference, rendered).convert("L")
    difference = ImageEnhance.Contrast(difference).enhance(3.0)
    ImageOps.colorize(difference, black="#101a1f", white="#ff3b30").save(
        args.output_dir / f"{prefix}_Pixel_Difference_3x.png", optimize=True
    )

    diff_pixels = ImageChops.difference(reference, rendered)
    diff_bytes = diff_pixels.tobytes()
    diff_values = list(zip(diff_bytes[0::3], diff_bytes[1::3], diff_bytes[2::3]))
    pixel_count = rendered.width * rendered.height
    mean_abs_error = sum(sum(px) / 3 for px in diff_values) / pixel_count
    threshold_counts = {
        str(threshold): sum(1 for px in diff_values if max(px) >= threshold)
        for threshold in (8, 24, 64)
    }
    base_rois = {
        "brand-dark-ink": ((10, 35, 125, 112), lambda r, g, b: r < 78 and g < 105 and b < 145 and b - r > 8),
        "avatar-light-rim": ((296, 48, 375, 116), lambda r, g, b: min(r, g, b) >= 222 and max(r, g, b) - min(r, g, b) <= 36),
        "statistics-dark-ink": ((42, 122, 355, 196), lambda r, g, b: r < 75 and g < 100 and b < 138 and b - r > 8),
        "recent-title-light-ink": ((22, 216, 192, 266), lambda r, g, b: min(r, g, b) >= 222 and max(r, g, b) - min(r, g, b) <= 36),
        "recent-action-light-ink": ((285, 216, 386, 266), lambda r, g, b: min(r, g, b) >= 222 and max(r, g, b) - min(r, g, b) <= 36),
        "hero-card-light-outline": ((48, 264, 345, 655), lambda r, g, b: min(r, g, b) >= 222 and max(r, g, b) - min(r, g, b) <= 30),
        "peek-left-light-outline": ((0, 280, 66, 620), lambda r, g, b: min(r, g, b) >= 222 and max(r, g, b) - min(r, g, b) <= 30),
        "peek-right-light-outline": ((324, 280, 390, 620), lambda r, g, b: min(r, g, b) >= 222 and max(r, g, b) - min(r, g, b) <= 30),
        "cta-light-ink": ((96, 632, 294, 692), lambda r, g, b: min(r, g, b) >= 222 and max(r, g, b) - min(r, g, b) <= 36),
        "camera-gold-rim": ((128, 676, 262, 815), lambda r, g, b: r >= 125 and g >= 105 and r - g >= 18 and g - b >= 5),
    }
    scale_x, scale_y = rendered.width / 390, rendered.height / 844
    rois = {
        name: (
            (
                max(0, round(roi[0] * scale_x)),
                max(0, round(roi[1] * scale_y)),
                min(rendered.width, round(roi[2] * scale_x)),
                min(rendered.height, round(roi[3] * scale_y)),
            ),
            predicate,
        )
        for name, (roi, predicate) in base_rois.items()
    }
    mask_report = {}
    for name, (roi, predicate) in rois.items():
        reference_components = connected_components(reference, roi, predicate)
        rendered_components = connected_components(rendered, roi, predicate)
        ref_bbox = reference_components[0]["bbox_css"] if reference_components else None
        render_bbox = rendered_components[0]["bbox_css"] if rendered_components else None
        mask_report[name] = {
            "roi_css": list(roi),
            "reference_largest_components": reference_components[:5],
            "rendered_largest_components": rendered_components[:5],
            "largest_component_bbox_delta_render_minus_reference_css": compare_bbox(ref_bbox, render_bbox),
            "interpretation": "diagnostic screenshot mask only; not a frozen per-layer geometry target",
        }

    reference_sha256 = hashlib.sha256(args.reference.read_bytes()).hexdigest()
    render_sha256 = hashlib.sha256(args.render.read_bytes()).hexdigest()
    if reference_sha256 != dom["frozen_reference"]["sha256"]:
        raise SystemExit(f"Frozen M03 SHA mismatch: {reference_sha256} != {dom['frozen_reference']['sha256']}")
    report = {
        "result": "MEASUREMENT_ONLY_NOT_VISUAL_PASS",
        "profile_prefix": prefix,
        "reference": str(args.reference),
        "reference_sha256": reference_sha256,
        "render": str(args.render),
        "render_sha256": render_sha256,
        "build_sha": dom.get("build_sha"),
        "viewport_css_px": {"width": rendered.width, "height": rendered.height},
        "comparison": {
            "reference_resize": f"Pillow LANCZOS to {rendered.width}x{rendered.height} CSS pixels; reference originals remain unchanged",
            "mean_absolute_rgb_error_0_255": round(mean_abs_error, 4),
            "changed_pixel_count_max_channel_threshold": threshold_counts,
            "changed_pixel_ratio": {key: round(value / pixel_count, 6) for key, value in threshold_counts.items()},
        },
        "measured_dom_layers": dom["dom"],
        "source_screenshot_mask_bounds": mask_report,
        "limitations": [
            "M03 is a flattened composite screenshot and has no layer-ID annotations; color-mask bounds are diagnostic, thresholded measurements only.",
            "The approved NH01 fallback avatar differs from the illustrative personal photo in the static screenshot; no user photo source is present in this repository contract.",
            "Hero and Peek independent photo originals remain blocked. The screenshots themselves are not cropped and reused as photo assets.",
            "Differences are expected while those distinct photo sources are unavailable; this report does not certify visual parity.",
        ],
        "artifacts": [
            f"{prefix}_Frozen_Left_Render_Right.png",
            f"{prefix}_Overlay_50pct.png",
            f"{prefix}_Pixel_Difference_3x.png",
        ],
    }
    (args.output_dir / f"{prefix}_Visual_Comparison_Report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print(json.dumps({"result": report["result"], "mean_absolute_rgb_error_0_255": report["comparison"]["mean_absolute_rgb_error_0_255"], "artifacts": report["artifacts"]}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
