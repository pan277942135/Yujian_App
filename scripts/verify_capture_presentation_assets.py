#!/usr/bin/env python3
"""Decode exact capture/AI Orb/Processing raster bytes, including those inside the APK."""
import argparse
import io
import zipfile
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = {
    "capture_button_main_v1": {"alpha": True, "min_dimension": 192},
    "ai_orb_camera_activated_v1": {"alpha": True, "min_dimension": 256},
    "camera_mint_background_v2": {"alpha": False, "min_dimension": 256},
    "processing_water_core_v1": {"alpha": True, "min_dimension": 256},
    "processing_mint_background_v1": {"alpha": False, "min_dimension": 256},
}


def verify(name, data, requirements):
    with Image.open(io.BytesIO(data)) as image:
        image.verify()
    with Image.open(io.BytesIO(data)) as image:
        image.load()
        if min(image.size) < requirements["min_dimension"]:
            raise ValueError(f"{name}: raster too small: {image.size}")
        alpha_required = requirements["alpha"]
        if alpha_required:
            if "A" not in image.getbands():
                raise ValueError(f"{name}: missing alpha channel")
            alpha = image.getchannel("A")
            for point in [(0, 0), (image.width - 1, 0), (0, image.height - 1), (image.width - 1, image.height - 1)]:
                if alpha.getpixel(point) != 0:
                    raise ValueError(f"{name}: opaque outer corner")
        print(f"DECODE_PASS {name} {image.format} {image.width}x{image.height} alpha={alpha_required}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", type=Path)
    args = parser.parse_args()
    if args.apk:
        with zipfile.ZipFile(args.apk) as archive:
            for stem, requirements in ASSETS.items():
                paths = [p for p in archive.namelist() if p.startswith("res/drawable") and Path(p).stem == stem]
                if len(paths) != 1:
                    raise ValueError(f"{stem}: expected one packaged raster, got {paths}")
                verify(paths[0], archive.read(paths[0]), requirements)
    else:
        for stem, requirements in ASSETS.items():
            paths = list((ROOT / "app/src/main/res/drawable-nodpi").glob(stem + ".*"))
            if len(paths) != 1:
                raise ValueError(f"{stem}: expected one source raster, got {paths}")
            verify(paths[0].name, paths[0].read_bytes(), requirements)
    print("CAPTURE_PRESENTATION_RASTER_DECODE=PASS")


if __name__ == "__main__":
    main()
