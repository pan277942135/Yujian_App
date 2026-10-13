#!/usr/bin/env python3
"""Deterministically derive the Normal Home brand wordmark from its frozen raster.

This is an alpha-matted raster derivative, not recovery of an original vector or
transparent master. The approved source image is read-only and SHA-256 pinned.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import shutil
from pathlib import Path
from statistics import median

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
SOURCE_REL = "design/system/core_visual_v1/reference/normal_home_v1.png"
SOURCE_SHA256 = "6ab9d3348b4a9a7e77ddca3a06235b4991798a309bd3512cc6fb9ea7aeb1d377"
SOURCE_SIZE = (1080, 1920)
# Safe ROI: x=95..250, y=116..196; contains the visible wordmark and transparent margin.
ROI = (95, 116, 250, 196)
VISIBLE_INK_EXPECTED = (103, 125, 242, 188)
TOP_SAMPLE = (116, 124)
BOTTOM_SAMPLE = (190, 198)
ALGORITHM = "normal-home-brand-title-local-median-linear-matte-v1"
DEFAULT_OUTPUT_REL = "design/system_v3/06_Asset_System/derived/brand_title_primary"
DEFAULT_RUNTIME_REL = "app/src/main/res/drawable-nodpi/brand_title_primary.png"


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def json_dump(path: Path, value: object) -> None:
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def median_band(image: Image.Image, y0: int, y1: int, x: int) -> tuple[float, float, float]:
    pixels = image.load()
    samples = [pixels[sx, sy] for sy in range(y0, y1) for sx in range(max(0, x - 4), min(image.width, x + 5))]
    return tuple(float(median(pixel[channel] for pixel in samples)) for channel in range(3))


def make_checkerboard(size: tuple[int, int]) -> Image.Image:
    canvas = Image.new("RGB", size, (236, 236, 236))
    draw = ImageDraw.Draw(canvas)
    cell = 8
    for y in range(0, size[1], cell):
        for x in range(0, size[0], cell):
            if (x // cell + y // cell) % 2:
                draw.rectangle((x, y, x + cell - 1, y + cell - 1), fill=(196, 196, 196))
    return canvas


def bounds_for(mask: list[list[bool]], left: int, top: int) -> list[int] | None:
    points = [(x, y) for y, row in enumerate(mask) for x, value in enumerate(row) if value]
    if not points:
        return None
    xs = [point[0] for point in points]
    ys = [point[1] for point in points]
    return [left + min(xs), top + min(ys), left + max(xs) + 1, top + max(ys) + 1]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=ROOT / SOURCE_REL)
    parser.add_argument("--output-dir", type=Path, default=ROOT / DEFAULT_OUTPUT_REL)
    parser.add_argument("--runtime-output", type=Path, default=ROOT / DEFAULT_RUNTIME_REL)
    parser.add_argument(
        "--review-status",
        choices=("REVIEW_REQUIRED", "APPROVED_VISUALLY"),
        default="REVIEW_REQUIRED",
        help="Explicit reviewer decision; extraction never self-approves.",
    )
    args = parser.parse_args()

    source_path = args.source.resolve()
    output_dir = args.output_dir.resolve()
    runtime_path = args.runtime_output.resolve()
    source_sha = sha256(source_path)
    if source_sha != SOURCE_SHA256:
        raise SystemExit(f"Frozen source SHA mismatch: {source_sha}")
    source = Image.open(source_path).convert("RGB")
    if source.size != SOURCE_SIZE:
        raise SystemExit(f"Frozen source dimensions mismatch: {source.size}")
    left, top, right, bottom = ROI
    if not (0 <= left < right <= source.width and 0 <= top < bottom <= source.height):
        raise SystemExit("Extraction ROI is outside source bounds")

    source_px = source.load()
    width, height = right - left, bottom - top
    patch = [[source_px[left + x, top + y] for x in range(width)] for y in range(height)]

    # Estimate the absent backdrop from nearby pixels in the same raster. For
    # each source column, local 9x8 medians suppress texture and glyph leakage;
    # a linear interpolation joins the top and bottom background bands.
    top_bg = [median_band(source, TOP_SAMPLE[0], TOP_SAMPLE[1], x) for x in range(left, right)]
    bottom_bg = [median_band(source, BOTTOM_SAMPLE[0], BOTTOM_SAMPLE[1], x) for x in range(left, right)]
    bg: list[list[tuple[float, float, float]]] = []
    for y in range(top, bottom):
        fraction = min(1.0, max(0.0, (y - 119.5) / (193.5 - 119.5)))
        bg.append([
            tuple(top_bg[x][c] * (1.0 - fraction) + bottom_bg[x][c] * fraction for c in range(3))
            for x in range(width)
        ])

    ink_samples = [
        patch[y - top][x - left]
        for y in range(VISIBLE_INK_EXPECTED[1], VISIBLE_INK_EXPECTED[3])
        for x in range(VISIBLE_INK_EXPECTED[0], VISIBLE_INK_EXPECTED[2])
        if max(patch[y - top][x - left]) < 75
    ]
    if len(ink_samples) < 500:
        raise SystemExit(f"Insufficient opaque ink pixels for deterministic matting: {len(ink_samples)}")
    foreground = tuple(float(median(pixel[c] for pixel in ink_samples)) for c in range(3))

    alpha_rows: list[list[float]] = []
    rgb_rows: list[list[tuple[int, int, int]]] = []
    source_visible_mask = [[False] * width for _ in range(height)]
    alpha_mask = [[False] * width for _ in range(height)]
    diff_channels = [[], [], []]
    alpha_values: list[float] = []
    composite_rows: list[list[tuple[int, int, int]]] = []
    matte_cut = 0.018
    backdrop_delta_cut = 4.0
    for y in range(height):
        alpha_row: list[float] = []
        rgb_row: list[tuple[int, int, int]] = []
        composite_row: list[tuple[int, int, int]] = []
        for x in range(width):
            pixel = patch[y][x]
            backdrop = bg[y][x]
            source_visible_mask[y][x] = max(pixel) < 150
            direction = tuple(backdrop[c] - foreground[c] for c in range(3))
            denominator = max(sum(value * value for value in direction), 1.0)
            delta = tuple(backdrop[c] - pixel[c] for c in range(3))
            opacity = min(1.0, max(0.0, sum(delta[c] * direction[c] for c in range(3)) / denominator))
            distance = math.sqrt(sum(value * value for value in delta))
            if opacity < matte_cut or distance < backdrop_delta_cut:
                opacity = 0.0
            alpha_row.append(opacity)
            alpha_values.append(opacity)
            alpha_mask[y][x] = opacity > 0.0
            if opacity >= 0.18:
                color = tuple(
                    int(round(min(255.0, max(0.0, (pixel[c] - (1.0 - opacity) * backdrop[c]) / opacity))))
                    for c in range(3)
                )
            elif opacity >= 0.06:
                color = tuple(
                    int(round(min(255.0, max(0.0, (pixel[c] - (1.0 - opacity) * backdrop[c]) / opacity))))
                    for c in range(3)
                )
            else:
                color = tuple(int(round(value)) for value in foreground)
            rgb_row.append(color)
            recomposed = tuple(
                int(round(min(255.0, max(0.0, opacity * color[c] + (1.0 - opacity) * backdrop[c]))))
                for c in range(3)
            )
            composite_row.append(recomposed)
            for channel in range(3):
                diff_channels[channel].append(abs(recomposed[channel] - pixel[channel]))
        alpha_rows.append(alpha_row)
        rgb_rows.append(rgb_row)
        composite_rows.append(composite_row)

    visible_bbox = bounds_for(source_visible_mask, left, top)
    matte_bbox = bounds_for(alpha_mask, left, top)
    if visible_bbox != list(VISIBLE_INK_EXPECTED):
        raise SystemExit(f"Measured visible ink bbox changed: {visible_bbox}")

    rgba = Image.new("RGBA", (width, height))
    rgba_pixels = rgba.load()
    for y in range(height):
        for x in range(width):
            rgba_pixels[x, y] = (*rgb_rows[y][x], int(round(alpha_rows[y][x] * 255.0)))

    output_dir.mkdir(parents=True, exist_ok=True)
    runtime_path.parent.mkdir(parents=True, exist_ok=True)
    asset_path = output_dir / "brand_title_primary.png"
    rgba.save(asset_path, format="PNG", optimize=False, compress_level=9)
    shutil.copyfile(asset_path, runtime_path)
    asset_sha = sha256(asset_path)

    checker = make_checkerboard(rgba.size).convert("RGBA")
    preview = Image.alpha_composite(checker, rgba).convert("RGB")
    preview.resize((width * 4, height * 4), Image.Resampling.NEAREST).save(
        output_dir / "brand_title_extraction_preview.png", format="PNG", optimize=False, compress_level=9
    )

    original_crop = source.crop(ROI)
    backdrop_crop = Image.new("RGB", (width, height))
    backdrop_pixels = backdrop_crop.load()
    for y in range(height):
        for x in range(width):
            backdrop_pixels[x, y] = tuple(int(round(value)) for value in bg[y][x])
    recomposed_crop = Image.alpha_composite(backdrop_crop.convert("RGBA"), rgba).convert("RGB")
    overlay = Image.blend(original_crop, recomposed_crop, 0.5)
    comparison = Image.new("RGB", (width * 3, height), (240, 240, 240))
    comparison.paste(original_crop, (0, 0))
    comparison.paste(recomposed_crop, (width, 0))
    comparison.paste(overlay, (width * 2, 0))
    comparison.resize((width * 3 * 3, height * 3), Image.Resampling.NEAREST).save(
        output_dir / "brand_title_overlay_comparison.png", format="PNG", optimize=False, compress_level=9
    )

    diff = Image.new("RGB", (width, height))
    diff_pixels = diff.load()
    for y in range(height):
        for x in range(width):
            diff_pixels[x, y] = tuple(min(255, abs(recomposed_crop.getpixel((x, y))[c] - original_crop.getpixel((x, y))[c]) * 4) for c in range(3))
    diff.resize((width * 3, height * 3), Image.Resampling.NEAREST).save(
        output_dir / "brand_title_difference_visual.png", format="PNG", optimize=False, compress_level=9
    )

    def percentile(values: list[int], q: float) -> int:
        ordered = sorted(values)
        return ordered[min(len(ordered) - 1, int(round((len(ordered) - 1) * q)))]

    review_status = args.review_status
    manifest = {
        "asset_id": "brand_title_primary",
        "type": "brand_artwork",
        "source_type": "DERIVED_FROM_FROZEN_RASTER",
        "status": "FROZEN" if review_status == "APPROVED_VISUALLY" else "REVIEW",
        "approval_status": review_status,
        "format": "png",
        "mode": "RGBA",
        "background": "transparent",
        "usage": ["normal_home"],
        "source_path": SOURCE_REL,
        "source_sha256": source_sha,
        "derived_path": (Path(DEFAULT_OUTPUT_REL) / "brand_title_primary.png").as_posix(),
        "android_resource_path": DEFAULT_RUNTIME_REL,
        "sha256": asset_sha,
        "width": width,
        "height": height,
        "visible_ink_bounds_in_source": visible_bbox,
        "extraction_roi_in_source": list(ROI),
        "algorithm": ALGORITHM,
        "review_note": "Raster-derived alpha matte; no original transparent/vector master is claimed. Visual review compares source and recomposition; no visual threshold was changed.",
    }
    json_dump(output_dir / "brand_title_primary_manifest.json", manifest)
    json_dump(output_dir / "Brand_Title_Source_Extraction_Audit.json", {
        "source_path": SOURCE_REL,
        "source_sha256": source_sha,
        "source_resolution": list(SOURCE_SIZE),
        "source_mode": "RGB",
        "color_space": "RGB; no embedded ICC profile; treated as sRGB without profile conversion",
        "extraction_roi_bounds_xyxy": list(ROI),
        "roi_dimensions": [width, height],
        "visible_ink_bounds_xyxy": visible_bbox,
        "visible_ink_detection": "max(R,G,B) < 150 within expected Normal Home wordmark region",
        "visible_ink_expected_bbox": list(VISIBLE_INK_EXPECTED),
        "matte_bounds_xyxy": matte_bbox,
        "background_sample_policy": {
            "top_band_y": list(TOP_SAMPLE),
            "bottom_band_y": list(BOTTOM_SAMPLE),
            "horizontal_neighborhood_radius_px": 4,
            "per_channel_statistic": "median",
            "interpolation": "linear by source y between sample band centers 119.5 and 193.5",
        },
        "foreground_anchor_rgb": [int(round(value)) for value in foreground],
        "alpha_matte": {
            "algorithm_version": ALGORITHM,
            "method": "least-squares projection between local estimated background and opaque ink anchor; stable unmatting for semi-transparent edge pixels",
            "opacity_floor": matte_cut,
            "background_delta_floor": backdrop_delta_cut,
            "non_unique_alpha_note": "The frozen page is pre-composited. The matte is an explicit deterministic approximation, not recovered original alpha or vector source.",
        },
        "asset_sha256": asset_sha,
        "asset_dimensions": list(rgba.size),
        "alpha_pixel_counts": {
            "transparent": sum(1 for row in alpha_rows for value in row if value == 0),
            "partial": sum(1 for row in alpha_rows for value in row if 0 < value < 1),
            "opaque": sum(1 for row in alpha_rows for value in row if value == 1),
        },
        "review_status": review_status,
    })
    report = {
        "source_sha256": source_sha,
        "asset_sha256": asset_sha,
        "roi": list(ROI),
        "visible_ink_bounds": visible_bbox,
        "matte_bounds": matte_bbox,
        "comparison_dimensions_px": [width, height],
        "recomposition": {
            "comparison": "derived matte composited over the estimated text-free local backdrop",
            "difference_visual_scale": 4,
            "absolute_channel_residual": {
                "mean_rgb": [round(sum(channel) / len(channel), 3) for channel in diff_channels],
                "p50_rgb": [percentile(channel, 0.50) for channel in diff_channels],
                "p95_rgb": [percentile(channel, 0.95) for channel in diff_channels],
                "max_rgb": [max(channel) for channel in diff_channels],
            },
            "interpretation": "Descriptive diagnostics only. Existing page contracts do not define a brand-extraction MAE acceptance threshold; none is introduced here.",
        },
        "review_status": review_status,
    }
    json_dump(output_dir / "brand_title_difference_report.json", report)
    (output_dir / "brand_title_primary_sha256.txt").write_text(f"{asset_sha}  brand_title_primary.png\n", encoding="ascii")
    print(json.dumps({
        "source_sha256": source_sha,
        "asset_sha256": asset_sha,
        "dimensions": list(rgba.size),
        "roi": list(ROI),
        "visible_ink_bounds": visible_bbox,
        "matte_bounds": matte_bbox,
        "review_status": review_status,
        "output_dir": str(output_dir),
        "runtime_resource": str(runtime_path),
    }, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
