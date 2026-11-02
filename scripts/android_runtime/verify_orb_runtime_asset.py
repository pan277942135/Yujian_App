#!/usr/bin/env python3
"""Verify the immutable R00 WebP source and its packaged APK copy."""

from __future__ import annotations

import argparse
import hashlib
import io
import json
from pathlib import Path
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
RESOURCE = ROOT / "app/src/main/res/drawable-nodpi/processing_water_core_v1.webp"
CONTRACT = ROOT / "design/system/components/ai_orb_core/profiles/R04_two_state_final_contract.json"
DERIVED = ROOT / "design/system/components/ai_orb_core/assets/derived/processing_water_core_v1.webp"


def summarize(data: bytes, origin: str, expected_sha: str) -> dict:
    digest = hashlib.sha256(data).hexdigest()
    if digest != expected_sha:
        raise ValueError(f"{origin} SHA-256 mismatch: {digest}")
    try:
        from PIL import Image
    except ImportError as error:
        raise RuntimeError("Pillow is required to decode the Orb WebP alpha channel") from error
    image = Image.open(io.BytesIO(data))
    image.load()
    if image.format != "WEBP" or image.mode not in ("RGBA", "LA"):
        raise ValueError(f"{origin} must remain a transparent RGBA WebP; got {image.format}/{image.mode}")
    alpha = image.getchannel("A")
    low, high = alpha.getextrema()
    if low != 0 or high != 255:
        raise ValueError(f"{origin} alpha range must retain transparent edge and opaque center; got {low}..{high}")
    if image.size != (384, 384):
        raise ValueError(f"{origin} dimensions changed: {image.size}")
    return {"origin": origin, "sha256": digest, "width": image.width, "height": image.height,
            "mode": image.mode, "alpha_min": low, "alpha_max": high}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", type=Path)
    args = parser.parse_args()
    contract = json.loads(CONTRACT.read_text(encoding="utf-8"))
    expected_sha = contract["core_asset"]["sha256"]
    runtime = RESOURCE.read_bytes()
    derived = DERIVED.read_bytes()
    if runtime != derived:
        raise SystemExit("R00 runtime WebP differs byte-for-byte from the PR #304 derived asset")
    records = [summarize(runtime, str(RESOURCE.relative_to(ROOT)), expected_sha),
               summarize(derived, str(DERIVED.relative_to(ROOT)), expected_sha)]
    if args.apk:
        with zipfile.ZipFile(args.apk) as archive:
            names = [name for name in archive.namelist() if name.endswith("/processing_water_core_v1.webp")]
            if not names:
                raise SystemExit("APK does not contain processing_water_core_v1.webp")
            data = archive.read(names[0])
            records.append(summarize(data, f"{args.apk}:{names[0]}", expected_sha))
    print("ORB_R00_ASSET=PASS " + json.dumps(records, sort_keys=True))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, RuntimeError, zipfile.BadZipFile) as error:
        print(f"ORB_R00_ASSET=FAIL {error}", file=sys.stderr)
        raise SystemExit(1)
