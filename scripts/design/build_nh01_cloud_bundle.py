#!/usr/bin/env python3
"""Build the NH01 cloud handoff bundle from the PR #164 mapping and frozen contracts."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[2]
VIEWER = ROOT / "design/manager/nh01_acceptance"
DATA = VIEWER / "data"
CLOUD_HIFI = ROOT / "design/cloud_hifi/NH01"
PR164_HEAD = "77a9aabc2648bda2942575d6600b74c9fe7d3ad7"
MAPPING_PATH = DATA / "source/M1_NH01_FIGMA_NODE_MAPPING_V2.json"
FROZEN_PATH = "design/system/core_visual_v1/reference/normal_home_v1.png"
FROZEN_SHA = "6ab9d3348b4a9a7e77ddca3a06235b4991798a309bd3512cc6fb9ea7aeb1d377"
BACKGROUND_PATH = "design/system/backgrounds/morning_lake_v1/assets/Morning_Lake_Master_V1.png"
BACKGROUND_SHA = "5fba741088ea186e898cd3bee5777e35978436f427492e6e6122528ef6aa91d7"
BRAND_PATH = "design/system_v3/06_Asset_System/derived/brand_title_primary/brand_title_primary.png"
BRAND_SHA = "2dd41f5138e88e3bd48178dd26a15dfbb29c434536e4e3cc887fe2db13bdfddc"
CAMERA_PATH = "design/system_v3/06_Asset_System/derived/normal_home_camera_primary_v1/normal_home_camera_primary_v1.png"
CAMERA_SHA = "fb6417e372d08328ac8559d004d1c6f5b03fc17551646be46031df641660e8f2"
CHEVRON_PATH = "design/system/icon_library_v0_1/assets/ic_chevron_right_v1.svg"
CHEVRON_SHA = "3d8c0bf43500c40b636a08f50d6fedf4d5f0ae2fd984a7e6c16cd03f4130d4a9"
ANCHOR_PATH = "design/system_v3/10_Page_Visual_Acceptance_Contracts/Normal_Home/Normal_Home_Component_Anchor_Map_V1.json"
PIXEL_PATH = "design/system_v3/10_Page_Visual_Acceptance_Contracts/Normal_Home/Normal_Home_Pixel_Targets_V1.json"
RESOURCE_PATH = "design/system_v3/09_Android_Implementation_Contracts/Normal_Home/Normal_Home_Resource_Binding_V1.json"
RESPONSIVE_PATH = "design/pages/home/normal_home/engineering/Layout_Responsive_Contract_V1.md"
VISUAL_CONTRACT_PATH = "design/pages/home/normal_home/engineering/Quantitative_Visual_Acceptance_Contract_V1.md"
HERO_CONTRACT_PATH = "design/system_v3/10_Page_Visual_Acceptance_Contracts/Normal_Home/Normal_Home_Hero_EvidenceFit_Contract_V1.md"
PHOTO_REGISTER_PATH = "design/pages/home/normal_home/engineering/hero_real_photo_fixture_register_v1.json"


def read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def file_ref(repo_path: str, status: str = "SOURCE_VERIFIED") -> dict[str, Any]:
    path = ROOT / repo_path
    return {
        "repo_path": repo_path,
        "sha256": sha256(path) if path.is_file() else None,
        "expected_sha256": None,
        "status": status if path.is_file() else "BLOCKED_SOURCE_MISSING",
    }


def write_json(name: str, payload: dict[str, Any]) -> None:
    out = DATA / name
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


COMPOSE_MAP = {
    "41:30": (["app/src/main/java/com/yujian/ai/ui/home/NormalHomeContent.kt"], ["NormalHomeContent"]),
    "41:33": (["app/src/main/java/com/yujian/ai/ui/home/NormalHomeContent.kt"], ["NormalHomeHeader", "NormalHomeDefaultAvatar"]),
    "41:37": (["app/src/main/java/com/yujian/ai/ui/home/HomeStats.kt"], ["HomeStats"]),
    "41:49": (["app/src/main/java/com/yujian/ai/ui/home/NormalHomeContent.kt"], ["RecentCatchSectionHeader"]),
    "41:56": (["app/src/main/java/com/yujian/ai/ui/home/NormalHomeContent.kt", "app/src/main/java/com/yujian/ai/ui/home/RecentFishCard.kt", "app/src/main/java/com/yujian/ai/ui/designsystem/components/YuJianHeroCard.kt"], ["RecentCatchPager", "RecentFishCard", "YuJianHeroCard"]),
    "41:67": (["app/src/main/java/com/yujian/ai/ui/home/RecentFishCard.kt", "app/src/main/java/com/yujian/ai/ui/designsystem/components/YuJianHeroCard.kt"], ["RecentFishCard", "YuJianHeroCard"]),
    "41:58": (["app/src/main/java/com/yujian/ai/ui/home/NormalHomeContent.kt", "app/src/main/java/com/yujian/ai/ui/home/RecentFishCard.kt"], ["RecentCatchPager", "RecentFishCard"]),
    "41:76": (["app/src/main/java/com/yujian/ai/ui/home/NormalHomeContent.kt", "app/src/main/java/com/yujian/ai/ui/home/RecentFishCard.kt"], ["RecentCatchPager", "RecentFishCard"]),
    "41:85": (["app/src/main/java/com/yujian/ai/ui/home/NormalHomeContent.kt"], ["NormalHomeContent"]),
    "41:86": (["app/src/main/java/com/yujian/ai/ui/home/HomeCameraButton.kt", "app/src/main/java/com/yujian/ai/ui/designsystem/components/YuJianCaptureButton.kt"], ["HomeCameraButton", "YuJianCaptureButton"]),
    "41:32": (["app/src/main/java/com/yujian/ai/ui/home/NormalHomeContent.kt"], ["NormalHomeContent"]),
    "41:34": (["app/src/main/java/com/yujian/ai/ui/home/NormalHomeContent.kt"], ["NormalHomeHeader"]),
    "41:35": (["app/src/main/java/com/yujian/ai/ui/home/NormalHomeContent.kt"], ["NormalHomeDefaultAvatar"]),
}


def compose_mapping_evidence(figma_node: str) -> dict[str, Any]:
    paths, symbols = COMPOSE_MAP.get(figma_node, ([], []))
    source_files = [ROOT / path for path in paths]
    files_exist = bool(source_files) and all(path.is_file() for path in source_files)
    symbols_exist = files_exist
    for symbol in symbols:
        declaration = re.compile(rf"\b(?:fun|class|object|interface|val|var)\s+{re.escape(symbol)}\b")
        if not any(declaration.search(path.read_text(encoding="utf-8")) for path in source_files):
            symbols_exist = False
            break
    return {
        "source_files": paths,
        "source_symbols": symbols,
        "source_path_exists": files_exist,
        "source_symbol_readback": "PASS" if symbols_exist else "REVIEW_REQUIRED",
    }


def main() -> None:
    global ROOT, VIEWER, DATA, MAPPING_PATH, CLOUD_HIFI
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=ROOT)
    args = parser.parse_args()
    ROOT = args.root.resolve()
    VIEWER = ROOT / "design/manager/nh01_acceptance"
    DATA = VIEWER / "data"
    CLOUD_HIFI = ROOT / "design/cloud_hifi/NH01"
    MAPPING_PATH = DATA / "source/M1_NH01_FIGMA_NODE_MAPPING_V2.json"

    mapping = read_json(MAPPING_PATH)
    if mapping.get("schema") != "M1_NH01_FIGMA_NODE_MAPPING_V2":
        raise SystemExit("Unexpected PR #164 mapping schema")
    if mapping.get("status") != "REVIEW_REQUIRED":
        raise SystemExit("Source mapping status changed; review before promoting any acceptance state")
    if sha256(MAPPING_PATH) != "0a9880d1250969d85104641686efe1b1f85c965b8d9b2f7954d387d3b72c0b6a":
        raise SystemExit("PR #164 mapping snapshot SHA changed")

    anchor_map = read_json(ROOT / ANCHOR_PATH)
    pixel_targets = read_json(ROOT / PIXEL_PATH)
    resource_binding = read_json(ROOT / RESOURCE_PATH)
    photo_register = read_json(ROOT / PHOTO_REGISTER_PATH)

    mapping_sha = sha256(MAPPING_PATH)
    source_manifest = {
        "schema_version": 1,
        "document_type": "source_manifest",
        "status": "PASS",
        "source": {"canonical_main_sha_at_handoff": mapping.get("canonicalSourceSha"), "pr164_head_sha": PR164_HEAD},
        "pr164": {
            "number": 164,
            "source_branch": "design/m1-nh01-figma-structural-handoff-v2",
            "source_head_sha": PR164_HEAD,
            "mapping_repo_path": "design/manager/nh01_acceptance/data/source/M1_NH01_FIGMA_NODE_MAPPING_V2.json",
            "mapping_sha256": mapping_sha,
            "importer_report_url": f"https://github.com/pan277942135/Yujian_App/blob/{PR164_HEAD}/release/design_manager/YUJIAN_FIGMA_NATIVE_IMPORTER_V1_REPORT.md",
            "importer_zip_url": f"https://github.com/pan277942135/Yujian_App/raw/{PR164_HEAD}/release/design_manager/YUJIAN_FIGMA_NATIVE_IMPORTER_V1.zip",
            "importer_scope": "Reused as handoff evidence; not rebuilt or claimed as real Figma visual acceptance.",
        },
        "frozen_authority": {
            "repo_path": FROZEN_PATH,
            "width": 1080,
            "height": 1920,
            "sha256": sha256(ROOT / FROZEN_PATH),
            "expected_sha256": FROZEN_SHA,
            "status": "PASS" if sha256(ROOT / FROZEN_PATH) == FROZEN_SHA else "SOURCE_FAIL",
            "use": "Read-only reference image displayed separately; never used as the structured preview layer.",
        },
        "source_contracts": [
            {"repo_path": p, "sha256": sha256(ROOT / p), "role": role}
            for p, role in [
                (ANCHOR_PATH, "component anchor authority"),
                (PIXEL_PATH, "pixel/ROI thresholds and responsive profile authority"),
                (RESOURCE_PATH, "Android resource binding authority"),
                (RESPONSIVE_PATH, "responsive geometry authority"),
                (VISUAL_CONTRACT_PATH, "visual comparison tolerance authority"),
                (HERO_CONTRACT_PATH, "hero photo crop and evidence-fit authority"),
                (PHOTO_REGISTER_PATH, "photo source and usage-rights status"),
            ]
        ],
        "public_asset_review": {
            "repo_is_public": True,
            "source_assets_are_in_public_repository": True,
            "frozen_full_reference_already_registered_in_design_manager_pages_copy": True,
            "hero_fixture_usage_rights": "NOT_VERIFIED; do not expose fixture photo as an approved preview asset.",
            "sensitive_credentials_or_internal_runner_logs_in_viewer": False,
        },
    }
    write_json("NH01_Source_Manifest_V1.json", source_manifest)

    anchors = {a["anchor_id"]: a for a in anchor_map.get("anchors", [])}
    geometry = {
        "environment": {"x": 0, "y": 0, "width": 1080, "height": 1920, "evidence": "full-window background contract"},
        "header": {"x": 88, "y": 104, "width": 904, "height": 104, "evidence": "home.header"},
        "statistics": {"x": 150, "y": 304, "width": 780, "height": 116, "evidence": "home.statistics; Figma map additionally records rowHeight=116"},
        "recent_header": {"x": 108, "y": 494, "width": 864, "height": 70, "evidence": "home.recent_header"},
        "hero_selected": {"x": 170, "y": 596, "width": 740, "height": 880, "radius": 32, "evidence": "home.hero.frame"},
        "hero_neighbor_left": {"x": -586, "y": 596, "width": 740, "height": 880, "radius": 32, "evidence": "DERIVED_FROM_PAGER_FIXED_PAGE_SIZE_AND_16PX_GAP; visual transform is a separate runtime state"},
        "hero_neighbor_right": {"x": 926, "y": 596, "width": 740, "height": 880, "radius": 32, "evidence": "DERIVED_FROM_PAGER_FIXED_PAGE_SIZE_AND_16PX_GAP; visual transform is a separate runtime state"},
        "cta": {"x": 330, "y": 1495, "width": 420, "height": 58, "evidence": "current Compose container fact; not visible glyph bounds"},
        "camera_touch": {"x": 430.5, "y": 1564, "width": 216, "height": 216, "evidence": "responsive layout contract outer touch frame; conflicts with current Compose runtime y"},
        "camera_compose": {"x": 435.5, "y": 1583, "width": 208, "height": 208, "evidence": "current Compose artwork frame (source mapping); REVIEW_REQUIRED against contract"},
    }
    source_sha = mapping.get("canonicalSourceSha")
    component_data = [
        {"id": "environment", "name": "00 / Environment", "figma_node_id": "41:31", "parent_id": "NH01_Normal_Home_1080x1920", "kind": "frame", "rect": geometry["environment"], "compose": "NormalHomeContent background layer", "status": "PASS", "evidence": "Figma structure readback at PR #164 source head; source background hash checked from repository."},
        {"id": "header", "name": "01 / Header", "figma_node_id": "41:33", "parent_id": "NH01_Normal_Home_1080x1920", "kind": "frame", "rect": geometry["header"], "compose": "NormalHomeHeader in NormalHomeContent.kt", "status": "PASS", "evidence": "Figma structural readback and Layout contract."},
        {"id": "statistics", "name": "02 / Statistics", "figma_node_id": "41:37", "parent_id": "NH01_Normal_Home_1080x1920", "kind": "frame", "rect": geometry["statistics"], "compose": "HomeStats.kt", "status": "PASS", "evidence": "Figma Auto Layout readback and source anchor contract."},
        {"id": "recent_header", "name": "03 / RecentCatchHeader", "figma_node_id": "41:49", "parent_id": "NH01_Normal_Home_1080x1920", "kind": "frame", "rect": geometry["recent_header"], "compose": "RecentCatchSectionHeader in NormalHomeContent.kt", "status": "PASS", "evidence": "Figma structure readback and source anchor contract."},
        {"id": "fish_pager", "name": "04 / FishCatchPager", "figma_node_id": "41:56", "parent_id": "NH01_Normal_Home_1080x1920", "kind": "frame", "rect": {"x": 0, "y": 596, "width": 1080, "height": 880, "evidence": "runtime pager viewport in Compose; page instance positions follow fixed 740px card + 16px gap"}, "compose": "RecentCatchPager + RecentFishCard + YuJianHeroCard", "status": "REVIEW_REQUIRED", "evidence": "Figma reports three clipped instances; real photo fixture provenance and rights remain NOT_VERIFIED."},
        {"id": "hero_selected", "name": "Selected Hero Card", "figma_node_id": "41:67", "parent_id": "41:56", "kind": "component_instance", "rect": geometry["hero_selected"], "compose": "RecentFishCard → YuJianHeroCard(variant=HOME)", "component_node_id": "38:24", "status": "REVIEW_REQUIRED", "evidence": "740×880/32px geometry confirmed; dynamic photo comparison blocked on fixture rights."},
        {"id": "hero_neighbor_left", "name": "Neighbor Hero Card · previous", "figma_node_id": "41:58", "parent_id": "41:56", "kind": "component_instance", "rect": geometry["hero_neighbor_left"], "compose": "RecentCatchPager neighbor page; runtime transform varies with pager offset", "component_node_id": "38:24", "status": "REVIEW_REQUIRED", "evidence": "Base page position derived from fixed pager width and 16px spacing; runtime visual transform has not been captured."},
        {"id": "hero_neighbor_right", "name": "Neighbor Hero Card · next", "figma_node_id": "41:76", "parent_id": "41:56", "kind": "component_instance", "rect": geometry["hero_neighbor_right"], "compose": "RecentCatchPager neighbor page; runtime transform varies with pager offset", "component_node_id": "38:24", "status": "REVIEW_REQUIRED", "evidence": "Base page position derived from fixed pager width and 16px spacing; runtime visual transform has not been captured."},
        {"id": "capture_action_group", "name": "05 / CaptureActionGroup", "figma_node_id": "41:85", "parent_id": "NH01_Normal_Home_1080x1920", "kind": "component_instance", "rect": {"x": 330, "y": 1495, "width": 420, "height": 296, "evidence": "aggregate from CTA container and current camera action frames; camera authority conflict remains"}, "compose": "CTA in NormalHomeContent.kt + HomeCameraButton.kt + YuJianCaptureButton.kt", "component_node_id": "38:28", "status": "REVIEW_REQUIRED", "evidence": "CTA/container exists; camera source anchor y=1564 conflicts with Compose y=1583."},
        {"id": "cta", "name": "Capture CTA", "figma_node_id": "41:85", "parent_id": "41:85", "kind": "text_action", "rect": geometry["cta"], "compose": "NormalHomeContent.kt / normal-home-capture-cta", "status": "REVIEW_REQUIRED", "evidence": "Container geometry is implementation fact; Frozen glyph ROI is x433..646/y1527..1558."},
        {"id": "camera", "name": "Camera artwork + touch target", "figma_node_id": "41:86", "parent_id": "41:85", "kind": "button", "rect": geometry["camera_compose"], "compose": "HomeCameraButton.kt → YuJianCaptureButton.kt", "status": "REVIEW_REQUIRED", "evidence": "Compose y=1583 vs Layout touch y=1564; runtime bounds not measured."},
    ]
    structure = {
        "schema_version": 1,
        "document_type": "structure",
        "page_id": "normal_home_v1",
        "state_id": "NH01",
        "authority_version": "V1",
        "status": "REVIEW_REQUIRED",
        "source": {"pr164_head_sha": PR164_HEAD, "mapping_sha256": mapping_sha, "canonical_main_sha_at_handoff": source_sha},
        "frame": {"id": "41:30", "name": "NH01_Normal_Home_1080x1920", "width": 1080, "height": 1920, "coordinate_space": "Frozen Pixel Space", "status": "PASS"},
        "frozen_authority": {"repo_path": FROZEN_PATH, "sha256": sha256(ROOT / FROZEN_PATH), "expected_sha256": FROZEN_SHA, "width": 1080, "height": 1920, "status": "PASS"},
        "source_contracts": {"anchor_map": ANCHOR_PATH, "pixel_targets": PIXEL_PATH, "resource_binding": RESOURCE_PATH, "responsive": RESPONSIVE_PATH, "visual_acceptance": VISUAL_CONTRACT_PATH},
        "components": component_data,
        "preview_layout": [
            {"id": "background", "component_id": "environment", "parent": "41:30", "kind": "image", "rect": {"x": 0, "y": 0, "width": 1080, "height": 1920}, "asset_id": "morning_lake_background", "evidence_status": "SOURCE_AUTHORITY_VERIFIED_FIGMA_FILL_BLOCKED"},
            {"id": "brand", "component_id": "header", "parent": "41:33", "kind": "image", "rect": {"x": 95, "y": 116, "width": 155, "height": 80}, "asset_id": "brand_title", "evidence_status": "COMPOSE_ARTWORK_FRAME_FROM_NORMALHOMECONTENT_KT"},
            {"id": "avatar", "component_id": "header", "parent": "41:33", "kind": "diagnostic_placeholder", "rect": {"x": 900, "y": 110, "width": 92, "height": 92}, "asset_id": "avatar_default_nh07", "evidence_status": "AUTHORITY_CONFLICT_NOT_RENDERED"},
            {"id": "statistics", "component_id": "statistics", "parent": "41:30", "kind": "editable_text_group", "rect": {"x": 150, "y": 304, "width": 780, "height": 116}, "token_role": "Statistics Value 36/42 + Statistics Label 28/32", "evidence_status": "FROZEN_CONTRACT"},
            {"id": "recent_header", "component_id": "recent_header", "parent": "41:30", "kind": "editable_text_group", "rect": {"x": 108, "y": 494, "width": 864, "height": 70}, "token_role": "Recent Title 48/52 + Recent Action 40/44", "evidence_status": "FROZEN_CONTRACT"},
            {"id": "pager", "component_id": "fish_pager", "parent": "41:30", "kind": "clipped_pager", "rect": {"x": 0, "y": 596, "width": 1080, "height": 880}, "evidence_status": "COMPOSE_VIEWPORT"},
            {"id": "hero_neighbor_left", "component_id": "hero_neighbor_left", "parent": "pager", "kind": "component_instance", "rect": {"x": -565.65, "y": 28.2, "width": 699.3, "height": 831.6}, "asset_id": "hero_photo_fixture", "evidence_status": "VISUAL_TRANSFORM_DERIVED_FROM_CURRENT_COMPOSE_PAGER; MEDIA_BLOCKED"},
            {"id": "hero_selected", "component_id": "hero_selected", "parent": "pager", "kind": "component_instance", "rect": {"x": 170, "y": 0, "width": 740, "height": 880}, "radius": 32, "asset_id": "hero_photo_fixture", "evidence_status": "FROZEN_FRAME; MEDIA_BLOCKED"},
            {"id": "hero_neighbor_right", "component_id": "hero_neighbor_right", "parent": "pager", "kind": "component_instance", "rect": {"x": 946.35, "y": 28.2, "width": 699.3, "height": 831.6}, "asset_id": "hero_photo_fixture", "evidence_status": "VISUAL_TRANSFORM_DERIVED_FROM_CURRENT_COMPOSE_PAGER; MEDIA_BLOCKED"},
            {"id": "cta", "component_id": "cta", "parent": "41:85", "kind": "editable_text", "rect": {"x": 330, "y": 1495, "width": 420, "height": 58}, "token_role": "CTA 40/44 frozen visual role; current Compose 36/40", "evidence_status": "RUNTIME_BOUNDS_NOT_RUN"},
            {"id": "camera", "component_id": "camera", "parent": "41:85", "kind": "image_plus_touch_target", "rect": {"x": 435.5, "y": 1583, "width": 208, "height": 208}, "touch_rect": {"x": 430.5, "y": 1564, "width": 216, "height": 216}, "asset_id": "camera_artwork", "evidence_status": "COMPOSE_Y_CONFLICTS_WITH_LAYOUT_CONTRACT"}
        ],
        "reference_typography_px": {"brand": [72, 80], "statistics_value": [36, 42], "statistics_label": [28, 32], "recent_title": [48, 52], "recent_action": [40, 44], "hero_species": [56, 64], "hero_measurement": [46, 52], "hero_context": [36, 42], "cta": [40, 44]},
        "preview_fixture": {
            "statistics": [{"value": "12", "label": "鱼种"}, {"value": "38", "label": "鱼获"}, {"value": "7", "label": "记录天数"}],
            "sample_copy_status": "SOURCE_SAMPLE_TEXT_NOT_LIVE_PRODUCT_DATA",
            "hero": {"species": "草鱼", "measurement": "42.6 cm · 1.28 kg", "context": "昨天 18:20 · 浙江 · 千岛湖", "photo_status": "BLOCKED_ASSET_SOURCE", "photo_placeholder": "Hero photo provenance and usage rights are NOT_VERIFIED."},
            "avatar_status": "REVIEW_REQUIRED_AUTHORITY_CONFLICT",
            "font_status": mapping.get("tokens", {}).get("fontStatus", "REVIEW_REQUIRED"),
        },
        "figma_readback": {
            "source_head_sha": PR164_HEAD,
            "overall": mapping.get("overall"),
            "master_board_untouched": mapping.get("structuralAudit", {}).get("masterBoard", {}).get("untouched"),
            "brand_node_41_34": "IMAGE_FILL_READBACK_CONFIRMED_VISUAL_EXPORT_NOT_RUN",
            "background_node_41_32": "BLOCKED_ENV_NOT_IMPORTED",
            "visual_export": "NOT_RUN",
            "runtime_import_acceptance": "NOT_RUN",
        },
    }
    write_json("NH01_Structure_V1.json", structure)

    tokens = {
        "schema_version": 1,
        "document_type": "tokens",
        "status": "REVIEW_REQUIRED",
        "source": {"pr164_head_sha": PR164_HEAD, "mapping_sha256": mapping_sha},
        "figma_collection": {"id": mapping["tokens"]["collectionId"], "name": mapping["tokens"]["collectionName"], "mode": mapping["tokens"]["mode"], "variable_count": mapping["tokens"]["variableCount"], "collection_status": "RECORDED_IN_PR164_MAPPING_NOT_CURRENTLY_RE_READ"},
        "colors": [{**item, "evidence_status": "BOUND_COLOR_RECORDED_IN_PR164_MAPPING"} for item in mapping["tokens"]["verifiedBoundColors"]],
        "text_styles": [{"name": item["name"], "id": item["id"], "status": "RECORDED_IN_PR164_MAPPING"} for item in mapping["tokens"]["textStyles"]],
        "font": {"candidate": mapping["tokens"]["fontCandidate"], "status": mapping["tokens"]["fontStatus"], "note": "No font substitution is promoted to verified."},
    }
    write_json("NH01_Tokens_V1.json", tokens)

    assets = {
        "schema_version": 1,
        "document_type": "assets",
        "status": "REVIEW_REQUIRED",
        "source": {"pr164_head_sha": PR164_HEAD, "mapping_sha256": mapping_sha},
        "assets": [
            {"id": "frozen_full_reference", "name": "Frozen NH01 full reference", "repo_path": FROZEN_PATH, "sha256": sha256(ROOT / FROZEN_PATH), "expected_sha256": FROZEN_SHA, "figma_node_id": None, "role": "Read-only comparison input; never a structured preview layer.", "status": "PASS"},
            {"id": "morning_lake_background", "name": "Morning Lake background", "repo_path": BACKGROUND_PATH, "sha256": sha256(ROOT / BACKGROUND_PATH), "expected_sha256": BACKGROUND_SHA, "figma_node_id": "41:32", "role": "NH01 full-window background; centered Crop contract.", "status": "BLOCKED", "detail_status": "BLOCKED_ENV_NOT_IMPORTED", "figma_fill": "NOT_READ_BACK"},
            {"id": "brand_title", "name": "Brand title artwork", "repo_path": BRAND_PATH, "sha256": sha256(ROOT / BRAND_PATH), "expected_sha256": BRAND_SHA, "figma_node_id": "41:34", "role": "Separate image asset, FIT.", "status": "REVIEW_REQUIRED", "detail_status": "IMAGE_FILL_READBACK_CONFIRMED_VISUAL_EXPORT_NOT_RUN"},
            {"id": "avatar_default_nh07", "name": "NH07 default avatar master", "repo_path": "design/pages/home/normal_home/07_avatar_states/assets/nh07_default_avatar_master_1254.png", "sha256": sha256(ROOT / "design/pages/home/normal_home/07_avatar_states/assets/nh07_default_avatar_master_1254.png"), "expected_sha256": "12ec5fc8c1a08723ea461edf327870df19b7dfe309587b18b6de28c9f701b643", "figma_node_id": "41:35", "role": "Avatar fallback only after authority conflict is closed.", "status": "REVIEW_REQUIRED", "detail_status": "REVIEW_REQUIRED_AUTHORITY_CONFLICT_NOT_IMPORTED"},
            {"id": "camera_artwork", "name": "Normal Home camera artwork", "repo_path": CAMERA_PATH, "sha256": sha256(ROOT / CAMERA_PATH), "expected_sha256": CAMERA_SHA, "figma_node_id": "41:86", "role": "208x208 visible artwork; distinct from 216x216 touch target.", "status": "PASS"},
            {"id": "chevron", "name": "Recent Catch chevron", "repo_path": CHEVRON_PATH, "sha256": sha256(ROOT / CHEVRON_PATH), "expected_sha256": CHEVRON_SHA, "figma_node_id": "41:53", "role": "Registered vector icon; authority mapping remains under review.", "status": "REVIEW_REQUIRED"},
            {"id": "hero_photo_fixture", "name": "Hero fish photo fixture set", "repo_path": PHOTO_REGISTER_PATH, "sha256": sha256(ROOT / PHOTO_REGISTER_PATH), "expected_sha256": None, "figma_node_id": "41:67", "role": "Dynamic record media; no candidate photo is rendered in structured preview until provenance and rights are verified.", "status": "BLOCKED", "detail_status": photo_register.get("status", "BLOCKED_ASSET_SOURCE")},
        ],
    }
    write_json("NH01_Assets_V1.json", assets)

    tree = {
        "schema_version": 1,
        "document_type": "component_tree",
        "status": "REVIEW_REQUIRED",
        "source": {"pr164_head_sha": PR164_HEAD, "mapping_sha256": mapping_sha},
        "root": {"id": "41:30", "name": "NH01_Normal_Home_1080x1920", "type": "FRAME", "width": 1080, "height": 1920, "children": [
            {"id": "41:31", "name": "00 / Environment", "type": "FRAME", "children": [{"id": "41:32", "name": "Morning Lake Background", "type": "IMAGE_LAYER", "status": "BLOCKED", "asset_id": "morning_lake_background"}]},
            {"id": "41:33", "name": "01 / Header", "type": "FRAME", "children": [{"id": "41:34", "name": "Brand Artwork", "type": "IMAGE_LAYER", "status": "REVIEW_REQUIRED", "asset_id": "brand_title"}, {"id": "41:35", "name": "Avatar", "type": "IMAGE_LAYER", "status": "REVIEW_REQUIRED", "asset_id": "avatar_default_nh07"}]},
            {"id": "41:37", "name": "02 / Statistics", "type": "FRAME", "component_id": "38:3", "children": [{"id": None, "name": "Species Statistic", "type": "TEXT_GROUP", "node_status": "NOT_IN_MAPPING_SNAPSHOT"}, {"id": None, "name": "Catch Statistic", "type": "TEXT_GROUP", "node_status": "NOT_IN_MAPPING_SNAPSHOT"}, {"id": None, "name": "Record Days Statistic", "type": "TEXT_GROUP", "node_status": "NOT_IN_MAPPING_SNAPSHOT"}]},
            {"id": "41:49", "name": "03 / RecentCatchHeader", "type": "FRAME", "children": [{"id": None, "name": "Title / 最近鱼获", "type": "TEXT", "node_status": "NATIVE_TEXT_COUNT_CONFIRMED; INDIVIDUAL_ID_NOT_IN_MAPPING_SNAPSHOT"}, {"id": None, "name": "All action", "type": "TEXT_ACTION", "node_status": "REVIEW_REQUIRED"}, {"id": "41:53", "name": "Chevron", "type": "VECTOR", "status": "REVIEW_REQUIRED", "asset_id": "chevron"}]},
            {"id": "41:56", "name": "04 / FishCatchPager", "type": "FRAME", "clips_content": True, "children": [{"id": "41:58", "name": "Neighbor / previous", "type": "INSTANCE", "component_id": "38:24", "status": "REVIEW_REQUIRED"}, {"id": "41:67", "name": "Selected Hero", "type": "INSTANCE", "component_id": "38:24", "status": "REVIEW_REQUIRED"}, {"id": "41:76", "name": "Neighbor / next", "type": "INSTANCE", "component_id": "38:24", "status": "REVIEW_REQUIRED"}]},
            {"id": "41:85", "name": "05 / CaptureActionGroup", "type": "COMPONENT_INSTANCE", "component_id": "38:28", "children": [{"id": "41:86", "name": "Capture button", "type": "COMPONENT_INSTANCE", "component_id": "38:25", "status": "REVIEW_REQUIRED"}]},
        ]},
        "mapping_completeness": "PR164 snapshot records 18 native text nodes; several individual text node IDs are not present in that mapping and are intentionally left null.",
    }
    write_json("NH01_Component_Tree_V1.json", tree)

    compose = []
    for item in mapping["nodeToCompose"]:
        source_evidence = compose_mapping_evidence(item["figmaNode"])
        compose.append({
            "figma_node": item["figmaNode"],
            "figma_component_node": item.get("componentNode"),
            "design_contract": item.get("contract"),
            "compose_symbol_and_path": item.get("compose"),
            **source_evidence,
            "runtime_bounds": "NOT_RUN",
            "status": "PASS" if source_evidence["source_symbol_readback"] == "PASS" else "REVIEW_REQUIRED",
            "source_status": item.get("sourceStatus", "MAPPED_IN_PR164"),
        })
    bindings = {
        "schema_version": 1,
        "document_type": "data_bindings",
        "status": "PASS" if all(row["status"] == "PASS" for row in compose) else "REVIEW_REQUIRED",
        "source": {"pr164_head_sha": PR164_HEAD, "mapping_sha256": mapping_sha, "android_source_baseline_sha": source_sha},
        "bindings": compose,
        "runtime_bounds_contract": "boundsInWindow and accessibility semantics must be captured on Android API28; NOT_RUN until exact-SHA Runtime run.",
        "business_binding_summary": [
            {"component": "Statistics", "data": ["CatchStatistics.speciesCount", "CatchStatistics.totalCatchCount", "CatchStatistics.recordDays"], "interaction": "species and catch totals navigate; recordDays is read-only", "source": "HomeStats.kt / State Interaction Contract", "status": "MAPPED"},
            {"component": "Recent Catch Hero", "data": ["RemoteCatch.id", "RemoteCatch.imageUrl", "species", "length", "weight", "timestamp", "location"], "interaction": "manual pager; tap routes by stable recordId", "source": "RecentCatchPager + RecentFishCard", "status": "MAPPED"},
            {"component": "Capture action", "data": [], "interaction": "onIdentify; camera target >=48dp", "source": "NormalHomeContent.kt + HomeCameraButton.kt", "status": "MAPPED"},
        ],
    }
    write_json("NH01_Data_Bindings_V1.json", bindings)

    responsive_contract = (ROOT / RESPONSIVE_PATH).read_text(encoding="utf-8")
    responsive = {
        "schema_version": 1,
        "document_type": "responsive",
        "status": "NOT_RUN",
        "source": {"pr164_head_sha": PR164_HEAD, "contract_path": RESPONSIVE_PATH, "contract_sha256": sha256(ROOT / RESPONSIVE_PATH), "pixel_targets_path": PIXEL_PATH},
        "reference": {"width_px": 1080, "height_px": 1920, "density_scale": pixel_targets["reference_device"]["density_scale"], "density_dpi": pixel_targets["reference_device"]["density_dpi"], "viewport_dp": pixel_targets["reference_device"]["viewport_dp"], "font_scale": 1.0},
        "formula": {"Wsafe": "max(0,W-L-R)", "Hsafe": "max(0,H-T-B)", "S": "Wsafe/1080", "E": "max(0,Hsafe-1920*S)", "delta": "min(0.36*E,180*S)", "xWindow": "L+xFrozen*S", "yWindow": "yFrozen*S+delta; do not add T", "ComposeDp": "physicalPx/d"},
        "modes": ["NORMAL_FIXED", "SAFE_OVERFLOW", "UNSUPPORTED_WINDOW"],
        "required_profiles": pixel_targets.get("responsive_profiles", []),
        "contract_has_expected_mapping_rules": "Wsafe" in responsive_contract and "SAFE_OVERFLOW" in responsive_contract,
        "runtime_profile_status": "NOT_RUN",
        "prohibited": ["global nonuniform scaling", "frozen image resampling", "assuming frozen px equals Compose dp", "claiming unrun profile PASS"],
    }
    write_json("NH01_Responsive_V1.json", responsive)

    evidence = {
        "schema_version": 1,
        "document_type": "evidence_provenance",
        "status": "REVIEW_REQUIRED",
        "source": {"pr164_head_sha": PR164_HEAD, "mapping_sha256": mapping_sha, "canonical_main_sha_at_handoff": source_sha},
        "figma": {"overall": mapping.get("overall"), "page_id": mapping["figma"]["page"]["id"], "main_frame_id": mapping["figma"]["mainFrame"]["id"], "master_board_untouched": mapping["structuralAudit"]["masterBoard"]["untouched"], "brand_image_fill": "CONFIRMED_ON_PR164_MAPPING_HEAD; visual export NOT_RUN", "background_image_fill": "BLOCKED_ENV_NOT_IMPORTED", "current_visual_export": "NOT_RUN", "pixel_diff": "NOT_RUN", "motion": "NOT_RUN", "independent_qa": "NOT_RUN"},
        "android": {"runtime": "NOT_RUN", "bounds_in_window": "NOT_RUN", "native_screenshot": "NOT_RUN", "apk_sha256": None, "source_sha": None, "workflow_run_id": None},
        "design_assets": {"frozen_sha_match": sha256(ROOT / FROZEN_PATH) == FROZEN_SHA, "background_sha_match": sha256(ROOT / BACKGROUND_PATH) == BACKGROUND_SHA, "brand_sha_match": sha256(ROOT / BRAND_PATH) == BRAND_SHA, "hero_fixture_rights": photo_register.get("status", "NOT_VERIFIED")},
        "open_issues": mapping.get("conflicts", []),
        "gate_status": {
            "G0_SOURCE_AUTHORITY": "PASS" if source_manifest["frozen_authority"]["status"] == "PASS" else "BLOCKED",
            "G1_STRUCTURE_VALID": "NOT_RUN",
            "G2_CLOUD_PREVIEW_VALID": "NOT_RUN",
            "G3_COMPOSE_MAPPING_VALID": "PASS" if all(row["status"] == "PASS" for row in compose) else "REVIEW_REQUIRED",
            "G4_RUNTIME_VALID": "NOT_RUN",
            "G5_VISUAL_PARITY_VALID": "NOT_RUN",
            "G6_RESPONSIVE_VALID": "NOT_RUN",
            "G7_DELIVERY_VALID": "NOT_RUN",
            "G8_USER_ACCEPTANCE": "PENDING_REVIEW",
            "FINAL": "IN_PROGRESS",
        },
    }
    write_json("NH01_Evidence_Provenance_V1.json", evidence)
    write_json("NH01_Android_Runtime_V1.json", {
        "schema_version": 1,
        "document_type": "android_runtime",
        "status": "NOT_RUN",
        "source": {"expected_source_sha": None, "workflow_run_id": None},
        "device": {"profile": "Pixel 2", "api_level": 28, "resolution_px": [1080, 1920], "density_dpi": None, "density_scale": None, "insets_px": None},
        "artifacts": {"screenshot": None, "bounds": None, "metadata": None, "runtime_log": None, "motion_mp4": None, "apk": None, "apk_sha256": None},
        "gate_classification": "NOT_RUN",
    })
    write_json("NH01_Browser_Preview_Diff_V1.json", {
        "schema_version": 1,
        "document_type": "browser_preview_diff",
        "status": "NOT_RUN",
        "source": {"source_sha": None, "renderer": "Chromium DOM screenshot; not Android Runtime"},
        "classification": "NOT_RUN",
        "artifacts": {"structured_preview": None, "heatmap": None, "overlay": None, "roi_report": None},
        "metrics": {},
        "rois": [],
    })

    print(f"NH01 bundle generated from mapping sha256={mapping_sha}")


if __name__ == "__main__":
    main()
