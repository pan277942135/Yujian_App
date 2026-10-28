#!/usr/bin/env python3
"""Verify the release-facing Empty Home runtime asset contract.

This intentionally uses only the Python standard library so CI can run it
before Gradle dependencies are resolved.
"""

from __future__ import annotations

import hashlib
import json
import struct
import sys
from pathlib import Path


EXPECTED_SOURCE_SHA = (
    "cde41925fa6b9682bfa965981afd0af9c23b8d15d5d3f9ab523b74fcbf0edf61"
)
EXPECTED_MASTER_SHA = (
    "b7e0e8912865a17a2f2a3b386138779bfcb41a4842fd8fd9589e830455756733"
)
ROOT = Path(__file__).resolve().parents[1] / "app/src/main/assets/empty_home_runtime_v1"

REQUIRED = {
    "static_scene_cache.png",
    "dynamic/cloud_layer.png",
    "dynamic/sun_glow.png",
    "dynamic/bobber.png",
    "dynamic/ripple_mask.png",
    "dynamic/sun_beam_mask.png",
    "dynamic/sun_particle_mask.png",
    "camera/camera_button_base.png",
    "camera/camera_gold_rim_mask.png",
    "camera/camera_breath_glow.png",
    "config/runtime_manifest.json",
    "config/layer_transform.json",
    "config/camera_transform.json",
    "config/timeline_config.json",
}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def png_dimensions(path: Path) -> tuple[int, int]:
    with path.open("rb") as stream:
        if stream.read(8) != b"\x89PNG\r\n\x1a\n":
            raise AssertionError(f"not a PNG: {path}")
        length = struct.unpack(">I", stream.read(4))[0]
        chunk = stream.read(4)
        if chunk != b"IHDR" or length < 8:
            raise AssertionError(f"missing IHDR: {path}")
        width, height = struct.unpack(">II", stream.read(8))
        return width, height


def main() -> int:
    if not ROOT.is_dir():
        raise AssertionError(f"missing runtime asset root: {ROOT}")

    actual = {
        path.relative_to(ROOT).as_posix()
        for path in ROOT.rglob("*")
        if path.is_file()
    }
    missing = sorted(REQUIRED - actual)
    if missing:
        raise AssertionError(f"missing required runtime assets: {missing}")

    forbidden = sorted(
        path
        for path in actual
        if path.startswith(("proof/", "preview/", "validation/"))
        or path.endswith((".mp4", ".gif"))
    )
    if forbidden:
        raise AssertionError(f"forbidden release assets present: {forbidden}")

    manifest = json.loads((ROOT / "config/runtime_manifest.json").read_text())
    assert manifest["source_zip_sha256"] == EXPECTED_SOURCE_SHA
    assert manifest["source_master_sha256"] == EXPECTED_MASTER_SHA
    assert manifest["canvas"] == {
        "width": 1080,
        "height": 1920,
        "coordinate_system": "reference_px_top_left",
    }
    assert manifest["ai_generation_used"] is False
    assert set(manifest["systems"]) == {
        "bobber",
        "ripple",
        "cloud",
        "sun_particle_beam",
        "camera_gold_rim",
        "camera_breathe",
    }
    assert png_dimensions(ROOT / "static_scene_cache.png") == (1080, 1920)

    compressed_bytes = sum((ROOT / relative).stat().st_size for relative in actual)
    decoded_bitmap_bytes = 0
    for relative in actual:
        path = ROOT / relative
        if path.suffix.lower() == ".png":
            width, height = png_dimensions(path)
            decoded_bitmap_bytes += width * height * 4

    report = {
        "status": "PASS",
        "file_count": len(actual),
        "compressed_bytes": compressed_bytes,
        "decoded_bitmap_bytes_argb8888": decoded_bitmap_bytes,
        "decoded_bitmap_mib_argb8888": round(decoded_bitmap_bytes / 1024 / 1024, 3),
        "source_zip_sha256": EXPECTED_SOURCE_SHA,
        "source_master_sha256": EXPECTED_MASTER_SHA,
    }
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, KeyError, json.JSONDecodeError) as error:
        print(f"EMPTY_HOME_RUNTIME_ASSET_CONTRACT_FAIL: {error}", file=sys.stderr)
        raise SystemExit(1)
