#!/usr/bin/env python3
"""Decode exact capture/AI Orb/Processing raster bytes, including those inside the APK."""
import argparse
import io
import hashlib
import zipfile
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
CANONICAL_CAPTURE_SOURCE = ROOT / "design/system/components/primary_capture_button/assets/capture_button_main_v1.png"
CANONICAL_CAPTURE_SHA256 = "501abcc58a65263dd879ad198a0a033630ab4bdcaf945eb02599e4f1249ca327"
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


def verify_canonical_capture_identity(runtime_bytes, packaged=False):
    source_bytes = CANONICAL_CAPTURE_SOURCE.read_bytes()
    source_sha = hashlib.sha256(source_bytes).hexdigest()
    if source_sha != CANONICAL_CAPTURE_SHA256:
        raise ValueError(f"Frozen Primary Capture Button source SHA mismatch: {source_sha}")
    runtime_sha = hashlib.sha256(runtime_bytes).hexdigest()
    if packaged:
        with Image.open(io.BytesIO(source_bytes)) as source_image, Image.open(io.BytesIO(runtime_bytes)) as runtime_image:
            source_image.load()
            runtime_image.load()
            if source_image.size != runtime_image.size or source_image.convert("RGBA").tobytes() != runtime_image.convert("RGBA").tobytes():
                raise ValueError(f"Packaged Primary Capture Button pixels differ from frozen source: {runtime_sha}")
        print(f"CANONICAL_CAPTURE_PIXEL_IDENTITY=PASS source_sha256={source_sha} packaged_sha256={runtime_sha}")
    else:
        if runtime_bytes != source_bytes:
            raise ValueError(f"Runtime Primary Capture Button is not byte-identical to frozen source: {runtime_sha}")
        print(f"CANONICAL_CAPTURE_BYTE_IDENTITY=PASS sha256={runtime_sha}")


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
                data = archive.read(paths[0])
                verify(paths[0], data, requirements)
                if stem == "capture_button_main_v1":
                    verify_canonical_capture_identity(data, packaged=True)
    else:
        for stem, requirements in ASSETS.items():
            paths = list((ROOT / "app/src/main/res/drawable-nodpi").glob(stem + ".*"))
            if len(paths) != 1:
                raise ValueError(f"{stem}: expected one source raster, got {paths}")
            data = paths[0].read_bytes()
            verify(paths[0].name, data, requirements)
            if stem == "capture_button_main_v1":
                verify_canonical_capture_identity(data)
    print("CAPTURE_PRESENTATION_RASTER_DECODE=PASS")


if __name__ == "__main__":
    main()
