#!/usr/bin/env python3
"""Verify Normal Home V1 independent runtime assets and source wiring."""

from __future__ import annotations
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSET_ROOT = ROOT / "app/src/main/assets/normal_home_runtime_v1"
MANIFEST = ASSET_ROOT / "config/runtime_manifest.json"

EXPECTED = {
    "static/scene_base.webp": "ac57c2067888ef503467b360f411669f6d76d1e8",
    "camera/camera_button_base.png": "7017622172491023276f2092ada4e75e976b1f6b",
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

def main() -> int:
    failures: list[str] = []
    try:
        data = json.loads(MANIFEST.read_text(encoding="utf-8"))
    except Exception as exc:
        print(f"INVALID Normal Home runtime manifest: {exc}", file=sys.stderr)
        return 1

    if data.get("asset_root") != "normal_home_runtime_v1":
        failures.append("asset_root is not normal_home_runtime_v1")
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

    source_files = [
        ROOT / "app/src/main/java/com/yujian/ai/ui/screens/HomeScreen.kt",
        ROOT / "app/src/main/java/com/yujian/ai/ui/home/NormalHomeContent.kt",
        ROOT / "app/src/main/java/com/yujian/ai/ui/home/RecentFishCard.kt",
    ]
    source = "\n".join(path.read_text(encoding="utf-8") for path in source_files)
    for forbidden in ("home_empty_v1_3", "home_normal_v1_2"):
        if forbidden in source:
            failures.append(f"LEGACY_RUNTIME_REFERENCE {forbidden}")
    for required in (
        "normal_home_runtime_v1/static/scene_base.webp",
        "normal_home_runtime_v1/avatar/guest_avatar.png",
        "normal_home_runtime_v1/fish_card",
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
