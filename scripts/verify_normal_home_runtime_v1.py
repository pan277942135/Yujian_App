#!/usr/bin/env python3
"""Verify Normal Home V1 independent runtime assets and source wiring."""

from __future__ import annotations
import json
import hashlib
import subprocess
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSET_ROOT = ROOT / "app/src/main/assets/normal_home_runtime_v1"
MANIFEST = ASSET_ROOT / "config/runtime_manifest.json"
BACKGROUND_SOURCE = ROOT / "design/system/backgrounds/morning_lake_v1/assets/Morning_Lake_Master_V1.png"
EMPTY_SCENE = ROOT / "app/src/main/assets/empty_home_runtime_v2/static/scene_base.webp"
BACKGROUND_SHA256 = "5fba741088ea186e898cd3bee5777e35978436f427492e6e6122528ef6aa91d7"

EXPECTED = {
    "static/scene_base.png": "188593ed13b0fd6b9164aaba8632e783b8534d68",
    "camera/camera_button_base.png": "cc653b4478a1749cfa3fc74eb7b830d183db4ee4",
    "camera/camera_gold_rim_mask.png": "2d10713e4ec0a458e88396fdd162102c87239755",
    "camera/camera_breath_glow.png": "cc8ae4ae614072ed00d035eebbb65a40e8af12cf",
    "avatar/guest_avatar.png": "f58549babb41aaedb5ee2590e6d48bceda19d05a",
    "fish_card/fish_card_gradient.png": "b17776098b76ecc6ce7488fef70c6ad319d58a21",
    "fish_card/fish_card_outline.png": "6ca9464b44b5aadda2819bda74f1dc3244055815",
    "fish_card/fish_card_shadow.png": "2a782f76a7880cf957b71ec718a0d260099e5f65",
}
FROZEN_SHA = "6ab9d3348b4a9a7e77ddca3a06235b4991798a309bd3512cc6fb9ea7aeb1d377"

def git_blob(path: Path) -> str:
    result = subprocess.run(
        ["git", "hash-object", str(path)],
        cwd=ROOT,
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout.strip()

def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main() -> int:
    failures: list[str] = []
    try:
        data = json.loads(MANIFEST.read_text(encoding="utf-8"))
    except Exception as exc:
        print(f"INVALID Normal Home runtime manifest: {exc}", file=sys.stderr)
        return 1

    if data.get("asset_root") != "normal_home_runtime_v1":
        failures.append("asset_root is not normal_home_runtime_v1")
    if data.get("asset_revision") != "NORMAL_HOME_RUNTIME_V1.1":
        failures.append("asset_revision is not NORMAL_HOME_RUNTIME_V1.1")
    if data.get("frozen_authority_sha256") != FROZEN_SHA:
        failures.append("Frozen authority SHA mismatch")

    blobs = data.get("git_blobs", {})
    for rel, expected_blob in EXPECTED.items():
        path = ASSET_ROOT / rel
        if not path.is_file():
            failures.append(f"MISSING {rel}")
            continue
        actual = git_blob(path)
        if actual != expected_blob:
            failures.append(f"BLOB_MISMATCH {rel}: {actual} != {expected_blob}")
        if blobs.get(rel) != expected_blob:
            failures.append(f"MANIFEST_BLOB_MISMATCH {rel}")

    provenance = data.get("background_provenance")
    if not isinstance(provenance, dict):
        failures.append("runtime manifest background_provenance is missing")
        provenance = {}
    expected_provenance = {
        "source_path": "design/system/backgrounds/morning_lake_v1/assets/Morning_Lake_Master_V1.png",
        "source_sha256": BACKGROUND_SHA256,
        "source_dimensions": [941, 1672],
        "runtime_path": "app/src/main/assets/normal_home_runtime_v1/static/scene_base.png",
        "runtime_sha256": BACKGROUND_SHA256,
        "runtime_dimensions": [941, 1672],
        "transform_policy": "byte-identical copy; Android ContentScale.Crop applies centered viewport crop without changing the source bitmap",
    }
    for key, expected in expected_provenance.items():
        if provenance.get(key) != expected:
            failures.append(f"runtime background provenance mismatch: {key}")

    scene_path = ASSET_ROOT / "static/scene_base.png"
    if not BACKGROUND_SOURCE.is_file():
        failures.append("Morning_Lake_Master_V1 source is missing")
    elif sha256(BACKGROUND_SOURCE) != BACKGROUND_SHA256:
        failures.append(f"Morning_Lake_Master_V1 source SHA-256 mismatch: {sha256(BACKGROUND_SOURCE)}")
    elif Image.open(BACKGROUND_SOURCE).size != (941, 1672):
        failures.append(f"Morning_Lake_Master_V1 dimensions mismatch: {Image.open(BACKGROUND_SOURCE).size}")
    if scene_path.is_file() and sha256(scene_path) != BACKGROUND_SHA256:
        failures.append(f"Normal Home runtime background SHA-256 mismatch: {sha256(scene_path)}")
    if scene_path.is_file() and Image.open(scene_path).size != (941, 1672):
        failures.append(f"Normal Home runtime background dimensions mismatch: {Image.open(scene_path).size}")
    try:
        design_assets = json.loads(
            (ROOT / "design/pages/home/normal_home/assets/asset_manifest.json").read_text(encoding="utf-8")
        )
        design_background = design_assets.get("background", {})
        if design_background.get("source") != "design/system/backgrounds/morning_lake_v1/assets/Morning_Lake_Master_V1.png":
            failures.append("Design Package background source is not Morning_Lake_Master_V1")
        if design_background.get("source_sha256") != BACKGROUND_SHA256:
            failures.append("Design Package background source SHA-256 mismatch")
        if design_background.get("runtime_output_sha256") != BACKGROUND_SHA256:
            failures.append("Design Package runtime background provenance SHA-256 mismatch")
    except Exception as exc:
        failures.append(f"Design Package asset manifest is invalid: {exc}")
    if not EMPTY_SCENE.is_file():
        failures.append("Empty Home scene missing; cannot perform negative-control comparison")
    elif scene_path.is_file() and git_blob(scene_path) == git_blob(EMPTY_SCENE):
        failures.append("Normal Home runtime background blob equals Empty Home scene blob")

    source_files = [
        ROOT / "app/src/main/java/com/yujian/ai/ui/screens/HomeScreen.kt",
        ROOT / "app/src/main/java/com/yujian/ai/ui/home/NormalHomeContent.kt",
        ROOT / "app/src/main/java/com/yujian/ai/ui/home/RecentFishCard.kt",
        ROOT / "app/src/main/java/com/yujian/ai/ui/home/NormalHomeRuntimeAssets.kt",
    ]
    source = "\n".join(path.read_text(encoding="utf-8") for path in source_files)
    for forbidden in ("home_empty_v1_3", "home_normal_v1_2"):
        if forbidden in source:
            failures.append(f"LEGACY_RUNTIME_REFERENCE {forbidden}")
    for required in (
        "normal_home_runtime_v1/static/scene_base.png",
        "normal_home_runtime_v1/avatar/guest_avatar.png",
        "fish_card/fish_card_gradient.png",
        "rememberNormalHomeRuntimeAssets",
    ):
        if required not in source:
            failures.append(f"MISSING_RUNTIME_REFERENCE {required}")

    if failures:
        for failure in failures:
            print(failure, file=sys.stderr)
        print(f"FAIL: {len(failures)} Normal Home runtime closure issue(s)", file=sys.stderr)
        return 1
    print("PASS: Normal Home runtime assets are independent and source wiring is closed")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
