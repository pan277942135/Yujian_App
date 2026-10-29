#!/usr/bin/env python3
"""Create the one-time, frozen transparent Hero derivative from the V2 authority."""

from __future__ import annotations

import hashlib
import math
import shutil
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "design/pages/home/empty_home/source/frozen/Empty_Home_Final_Design_V2_normalized_1080x1920.png"
DESIGN_OUTPUT = ROOT / "design/pages/home/empty_home/shared/assets/hero/empty_home_hero_v2_2.png"
RUNTIME_OUTPUT = ROOT / "app/src/main/res/drawable-nodpi/empty_home_title_v2.png"
SOURCE_SHA256 = "ebf96d310678b5fd47149cd8dadf3dd24cb00b2b83253876aa4cf9e89f954b77"
EXPECTED_DERIVED_SHA256 = "5561e3b577234ceba41487dd3705e82fa910e96cc6a6cc2d89942f13a629c9ff"
CROP = (50, 224, 675, 535)  # Includes the frozen visible-ink overhang at right/bottom.


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def derive() -> Image.Image:
    if sha256(SOURCE) != SOURCE_SHA256:
        raise SystemExit("Frozen normalized V2 reference SHA does not match the authority")
    with Image.open(SOURCE) as source:
        if source.size != (1080, 1920):
            raise SystemExit(f"Unexpected normalized reference size: {source.size}")
        image = source.crop(CROP).convert("RGBA")

    pixels = image.load()
    for y in range(image.height):
        for x in range(image.width):
            red, green, blue, _ = pixels[x, y]
            navy_distance = math.sqrt((red - 18) ** 2 + (green - 48) ** 2 + (blue - 72) ** 2)
            gold_distance = math.sqrt((red - 218) ** 2 + (green - 160) ** 2 + (blue - 45) ** 2)
            navy_alpha = int(max(0.0, min(255.0, (105.0 - navy_distance) * 4.5)))
            gold_alpha = int(max(0.0, min(255.0, (115.0 - gold_distance) * 4.0)))
            valid_navy = red < 115 and green < 135 and blue < 150
            valid_gold = red > 120 and green > 70 and blue < 125 and red - blue > 55
            alpha = max(navy_alpha, gold_alpha) if valid_navy or valid_gold else 0
            pixels[x, y] = (red, green, blue, alpha) if alpha else (0, 0, 0, 0)
    return image


def main() -> None:
    image = derive()
    DESIGN_OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    RUNTIME_OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    image.save(DESIGN_OUTPUT, format="PNG", optimize=False, compress_level=9)
    shutil.copyfile(DESIGN_OUTPUT, RUNTIME_OUTPUT)
    if sha256(DESIGN_OUTPUT) != EXPECTED_DERIVED_SHA256:
        raise SystemExit("Derived Hero SHA does not match the frozen V2.2 asset")
    print(f"size={image.width}x{image.height}")
    print(f"design_sha256={sha256(DESIGN_OUTPUT)}")
    print(f"runtime_sha256={sha256(RUNTIME_OUTPUT)}")


if __name__ == "__main__":
    main()
