#!/usr/bin/env python3
"""Derive the Empty Home rod, bobber and reflection from frozen V2.2 pixels.

This deliberately updates only the fishing layers which are independently
rendered by Android. Coordinates below are the measured V2.2 reference-space
boxes; source RGB pixels come from the committed 941x1672 authority.
"""

from __future__ import annotations

import hashlib
from pathlib import Path

from PIL import Image, ImageFilter


ROOT = Path(__file__).resolve().parents[1]
AUTHORITY = ROOT / "design/pages/home/empty_home/source/frozen/Empty_Home_Frozen_Visual_V2_2.png"
EXPECTED_SHA = "3071481ed7e58106381cdd5321267792491c21fd1a357e4362db1dad8e08e7ec"
DESIGN = ROOT / "design/pages/home/empty_home"
RUNTIME = ROOT / "app/src/main/assets/empty_home_runtime_v2/dynamic"


def normalized_authority() -> Image.Image:
    data = AUTHORITY.read_bytes()
    if hashlib.sha256(data).hexdigest() != EXPECTED_SHA:
        raise SystemExit("V2.2 authority SHA mismatch; no fishing assets were written")
    source = Image.open(AUTHORITY).convert("RGB")
    if source.size != (941, 1672):
        raise SystemExit(f"unexpected V2.2 authority dimensions: {source.size}")
    height = round(source.height * 1080 / source.width)
    resized = source.resize((1080, height), Image.Resampling.LANCZOS)
    canvas = Image.new("RGB", (1080, 1920))
    canvas.paste(resized, (0, 0))
    if height < 1920:
        canvas.paste(resized.crop((0, height - 1, 1080, height)).resize((1080, 1920-height)), (0, height))
    return canvas


def pixel_delta(rgb: tuple[int, int, int], background: tuple[int, int, int]) -> int:
    return max(abs(rgb[channel] - background[channel]) for channel in range(3))


def alpha_from_delta(delta: int, threshold: int, gain: float) -> int:
    return max(0, min(255, round((delta - threshold) * gain)))


def make_rod(source: Image.Image) -> Image.Image:
    # Measured tip and lower-left entry in reference pixels. The corridor follows
    # the straight tapered dark shaft; dark pixels outside it are excluded.
    box = (0, 1180, 340, 1386)
    crop = source.crop(box).convert("RGBA")
    smooth = source.filter(ImageFilter.GaussianBlur(4.0))
    mask = Image.new("L", crop.size, 0)
    source_pixels = source.load()
    smooth_pixels = smooth.load()
    mask_pixels = mask.load()
    for y in range(crop.height):
        absolute_y = y + box[1]
        for x in range(crop.width):
            absolute_x = x + box[0]
            center_y = 1380.0 - (196.0 * absolute_x / 337.0)
            half_width = 4.3 - 2.2 * absolute_x / 337.0
            distance = abs(absolute_y - center_y)
            if distance > half_width:
                continue
            rgb = source_pixels[absolute_x, absolute_y]
            backdrop = smooth_pixels[absolute_x, absolute_y]
            delta = pixel_delta(rgb, backdrop)
            luminance = (rgb[0] * 299 + rgb[1] * 587 + rgb[2] * 114) // 1000
            darkness = max(0.0, min(1.0, (150.0 - luminance) / 75.0))
            edge = max(0.0, min(1.0, (half_width + 0.65 - distance) / 1.3))
            mask_pixels[x, y] = round(alpha_from_delta(delta, 7, 7.0) * darkness * edge)
    crop.putalpha(mask.filter(ImageFilter.GaussianBlur(0.28)))
    return crop


def make_bobber_body(source: Image.Image) -> Image.Image:
    # One extracted float body. The polygon bounds the measured signal stem and
    # bulb; local contrast supplies antialiased edges without lake pixels.
    box = (550, 1250, 574, 1328)
    crop = source.crop(box).convert("RGBA")
    factor = 4
    draw_mask = Image.new("L", (crop.width * factor, crop.height * factor), 0)
    from PIL import ImageDraw

    draw = ImageDraw.Draw(draw_mask)
    draw.polygon(
        [
            (8*factor, 1*factor), (16*factor, 1*factor), (16*factor, 47*factor),
            (19*factor, 51*factor), (22*factor, 57*factor), (22*factor, 63*factor),
            (19*factor, 70*factor), (16*factor, 75*factor), (12*factor, 77*factor),
            (8*factor, 74*factor), (4*factor, 69*factor), (2*factor, 62*factor),
            (2*factor, 57*factor), (5*factor, 51*factor), (8*factor, 47*factor),
        ],
        fill=255,
    )
    mask = draw_mask.resize(crop.size, Image.Resampling.LANCZOS)
    mask = mask.point(lambda value: 0 if value < 20 else value)
    crop.putalpha(mask)
    return crop


def make_reflection(source: Image.Image) -> Image.Image:
    # Separate, low-opacity V2.2 continuation. Depth fade and blur are optical
    # treatment only; source colour pixels are retained from the frozen raster.
    box = (556, 1322, 568, 1374)
    crop = source.crop(box).convert("RGBA")
    smooth = source.filter(ImageFilter.GaussianBlur(4.0))
    mask = Image.new("L", crop.size, 0)
    source_pixels = source.load()
    smooth_pixels = smooth.load()
    mask_pixels = mask.load()
    for y in range(crop.height):
        absolute_y = y + box[1]
        depth = y / max(1, crop.height - 1)
        fade = (1.0 - depth) ** 0.9
        for x in range(crop.width):
            absolute_x = x + box[0]
            # Keep only the narrow float-colour continuation, not nearby lake
            # glints. It narrows gently below the water plane.
            center = 562.0 + 0.03 * y
            half_width = max(1.7, 4.4 - 2.2 * depth)
            distance = abs(absolute_x - center)
            if distance > half_width:
                continue
            rgb = source_pixels[absolute_x, absolute_y]
            backdrop = smooth_pixels[absolute_x, absolute_y]
            delta = pixel_delta(rgb, backdrop)
            contrast = alpha_from_delta(delta, 8, 3.2)
            edge = max(0.0, min(1.0, (half_width + 0.5 - distance) / 1.0))
            saturation = max(rgb) - min(rgb)
            signal = max(contrast, 54 if saturation >= 40 else 0)
            mask_pixels[x, y] = round(min(120, signal) * fade * edge)
    crop.putalpha(mask.filter(ImageFilter.GaussianBlur(0.65)))
    return crop


def write_pair(image: Image.Image, master: Path, runtime: Path) -> None:
    master.parent.mkdir(parents=True, exist_ok=True)
    runtime.parent.mkdir(parents=True, exist_ok=True)
    image.save(master, "PNG", optimize=True)
    image.save(runtime, "PNG", optimize=True)


def main() -> None:
    source = normalized_authority()
    write_pair(
        make_rod(source),
        DESIGN / "intermediate/extracted/rod_master.png",
        RUNTIME / "rod.png",
    )
    write_pair(
        make_bobber_body(source),
        DESIGN / "intermediate/extracted/bobber_master.png",
        RUNTIME / "bobber.png",
    )
    write_pair(
        make_reflection(source),
        DESIGN / "intermediate/extracted/bobber_reflection_master.png",
        RUNTIME / "bobber_reflection.png",
    )
    # Runtime-facing shared masters mirror the extracted sources exactly.
    for name in ("rod", "bobber"):
        (DESIGN / f"shared/assets/fishing/{name}_master.png").write_bytes(
            (DESIGN / f"intermediate/extracted/{name}_master.png").read_bytes()
        )
    (DESIGN / "shared/assets/fishing/bobber_reflection_master.png").write_bytes(
        (DESIGN / "intermediate/extracted/bobber_reflection_master.png").read_bytes()
    )


if __name__ == "__main__":
    main()
