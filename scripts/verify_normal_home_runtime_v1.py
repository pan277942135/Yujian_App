#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSET_ROOT = ROOT / "app/src/main/assets/home_normal_v1"
MANIFEST = ASSET_ROOT / "manifest.json"
REFERENCE_MANIFEST = ROOT / "design/system/core_visual_v1/reference/reference_manifest.json"
REQUIRED = [
    "assets/background/normal_home_bg.webp",
    "assets/avatar/guest_avatar.png",
    "assets/fish_card/fish_card_gradient.png",
    "assets/fish_card/fish_card_mask.png",
    "assets/fish_card/fish_card_outline.png",
    "assets/fish_card/fish_card_shadow.png",
]
BYTE_PRESERVED = {
    "assets/background/normal_home_bg.webp": "app/src/main/assets/home_empty_v1_3/assets/background/home_empty_bg_no_bobber.webp",
    "assets/avatar/guest_avatar.png": "app/src/main/assets/home_normal_v1_2/assets/avatar/guest_avatar.png",
    "assets/fish_card/fish_card_gradient.png": "app/src/main/assets/home_normal_v1_2/assets/fish_card/fish_card_gradient.png",
    "assets/fish_card/fish_card_mask.png": "app/src/main/assets/home_normal_v1_2/assets/fish_card/fish_card_mask.png",
    "assets/fish_card/fish_card_outline.png": "app/src/main/assets/home_normal_v1_2/assets/fish_card/fish_card_outline.png",
    "assets/fish_card/fish_card_shadow.png": "app/src/main/assets/home_normal_v1_2/assets/fish_card/fish_card_shadow.png",
}

def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()

def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)

manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
reference_manifest = json.loads(REFERENCE_MANIFEST.read_text(encoding="utf-8"))
normal_ref = next(item for item in reference_manifest["references"] if item["id"] == "normal_home_v1")
require(normal_ref["width"] == 1080 and normal_ref["height"] == 1920, "NORMAL_HOME_FROZEN_DIMENSIONS_INVALID")
require(normal_ref["sha256"] == manifest["frozen_authority"]["sha256"], "NORMAL_HOME_FROZEN_SHA_MISMATCH")
asset_hashes = {}
for relative in REQUIRED:
    target = ASSET_ROOT / relative
    require(target.is_file() and target.stat().st_size > 0, f"NORMAL_HOME_ASSET_MISSING:{relative}")
    source = ROOT / BYTE_PRESERVED[relative]
    require(source.is_file(), f"NORMAL_HOME_MIGRATION_SOURCE_MISSING:{source}")
    require(target.read_bytes() == source.read_bytes(), f"NORMAL_HOME_ASSET_REENCODED:{relative}")
    asset_hashes[relative] = sha256(target)

source_paths = [
    ROOT / "app/src/main/java/com/yujian/ai/ui/screens/HomeScreen.kt",
    ROOT / "app/src/main/java/com/yujian/ai/ui/home/NormalHomeContent.kt",
    ROOT / "app/src/main/java/com/yujian/ai/ui/home/RecentFishCard.kt",
]
for source_path in source_paths:
    source_text = source_path.read_text(encoding="utf-8")
    require("home_empty_v1_3" not in source_text, f"NORMAL_HOME_LEGACY_EMPTY_DEPENDENCY:{source_path}")
    require("home_normal_v1_2" not in source_text, f"NORMAL_HOME_LEGACY_ADDON_DEPENDENCY:{source_path}")
home = source_paths[0].read_text(encoding="utf-8")
require("home_normal_v1/assets/background/normal_home_bg.webp" in home, "NORMAL_HOME_BACKGROUND_NOT_INDEPENDENT")
content = source_paths[1].read_text(encoding="utf-8")
body = content[content.index("internal fun NormalHomeContent"):content.index("@Composable\nprivate fun NormalHomeHeader")]
require(body.index("RecentCatchPager(") < body.index("HomeStats("), "NORMAL_HOME_HERO_MUST_PRECEDE_STATS")
require("runtimeAssets = runtimeAssets" in body, "NORMAL_HOME_SHARED_CAPTURE_VISUAL_NOT_WIRED")
print(json.dumps({
    "gate": "NORMAL_HOME_RUNTIME_V1",
    "status": "PASS",
    "frozen_sha256": normal_ref["sha256"],
    "assets": asset_hashes,
    "legacy_runtime_dependencies": "REMOVED",
    "hierarchy": "REAL_FISH_BEFORE_DATA",
}, ensure_ascii=False, indent=2))
