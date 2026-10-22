#!/usr/bin/env python3
"""Regression gate for YuJian Design Manager navigation and Pages artifact."""

from __future__ import annotations

import argparse
import json
import hashlib
import struct
from pathlib import Path
import sys

EXPECTED_NORMAL_HOME = ["NH01", "NH02", "NH03", "NH04", "NH05", "NH06", "NH07"]
EXPECTED_RECOGNITION_FLOW = [
    "main_journey_overview", "state_timeline", "visual_states",
    "layer_component_ownership", "motion_transition", "degradation_accessibility",
    "runtime_evidence", "capture_first_use_guide", "boundary_state_release_gate",
    "recognition_full_flow_models",
]
EXPECTED_RECOGNITION_RESULT = [
    "overview", "high", "medium", "low", "no_fish", "image_quality",
    "content_edit", "decision_logic", "visual_acceptance", "motion_continuation",
]


def fail(message: str) -> None:
    print(f"FAIL: {message}", file=sys.stderr)
    raise SystemExit(1)


def load_json(path: Path):
    if not path.exists():
        fail(f"missing file: {path}")
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        fail(f"invalid json {path}: {exc}")


def validate_tree(root: Path, *, built: bool = False, expected_build: str | None = None) -> None:
    design = root / "design"
    registry_path = design / "registry" / "experience_registry_v1.json"
    nav_path = design / "pages" / "home" / "normal_home" / "navigation.json"
    app_path = design / "manager" / "app.js"
    index_path = design / "manager" / "index.html"

    registry = load_json(registry_path)
    nav = load_json(nav_path)

    feature = next((x for x in registry.get("features", []) if x.get("id") == "home_normal_v1"), None)
    if not feature:
        fail("home_normal_v1 missing from experience registry")

    hifi_ids = [x.get("id") for x in feature.get("hifi_views", [])]
    if hifi_ids != EXPECTED_NORMAL_HOME:
        fail(f"home_normal_v1 hifi_views changed: {hifi_ids} != {EXPECTED_NORMAL_HOME}")

    nav_ids = [x.get("id") for x in nav.get("level_2", [])]
    if nav_ids != EXPECTED_NORMAL_HOME:
        fail(f"Normal Home navigation changed: {nav_ids} != {EXPECTED_NORMAL_HOME}")

    # Design Manager workspaces are an independent navigation contract. They must never
    # become NH page-state entries or mutate the frozen NH01–NH07 sequence.
    workspace_registry_path = design / "manager" / "workspace_registry.json"
    workspaces = load_json(workspace_registry_path)
    expected_workspaces = [
        ("structured_hifi", "结构化高保真", "#page/home_normal_v1/workspace/structured_hifi", "gemini_native_structured_embed", "structured_hifi"),
        ("responsive_design", "响应式设计", "#page/home_normal_v1/workspace/responsive_design", "gemini_native_responsive_embed", "responsive_design"),
    ]
    if workspaces.get("schema") != "YUJIAN_DESIGN_MANAGER_WORKSPACE_REGISTRY_V1":
        fail("Design Manager workspace registry schema missing")
    if workspaces.get("parent_page_id") != "home_normal_v1" or workspaces.get("navigation_group") != "设计工作台":
        fail("NH01 Design Manager workbench must be a separate home_normal_v1 navigation group")
    registered_workspaces = workspaces.get("workspaces", [])
    actual_workspaces = [
        (item.get("id"), item.get("title"), item.get("route"), item.get("render_mode"),
         item.get("preview_entry", "").split("embed=")[-1])
        for item in registered_workspaces
    ]
    if actual_workspaces != expected_workspaces:
        fail(f"Design Manager workspace IDs/routes/render modes changed: {actual_workspaces}")
    if set(item[0] for item in expected_workspaces) & set(hifi_ids):
        fail("Design Manager workspace IDs must not be counted as NH01–NH07 page states")
    app_source = app_path.read_text(encoding="utf-8") if app_path.exists() else ""
    if "WORKSPACE_REGISTRY_URL" not in app_source or "selectWorkspace" not in app_source:
        fail("Design Manager must load the separate workspace registry and render internal workspace routes")
    gemini_entry = design / "manager" / "nh01_acceptance" / "gemini-native-preview.html"
    if not gemini_entry.is_file():
        fail("Gemini Native V3 preview entry is missing")
    manifest_path = root / registered_workspaces[0].get("asset_manifest", "")
    manifest = load_json(manifest_path)
    if manifest.get("schema") != "NH01_GEMINI_NATIVE_PREVIEW_ASSET_BINDING_V1":
        fail("Gemini Native asset Manifest schema mismatch")
    authority = manifest.get("structure_authority", {})
    if (
        authority.get("alignment_snapshot") != registered_workspaces[0].get("structure_snapshot")
        or authority.get("native_implementation_contract") != registered_workspaces[0].get("implementation_contract")
    ):
        fail("Workspace registry and Gemini asset Manifest disagree on authoritative sources")
    for item in registered_workspaces:
        if item.get("asset_manifest") != registered_workspaces[0].get("asset_manifest"):
            fail("Both workspaces must share the same Gemini asset Manifest")
        if item.get("structure_snapshot") != registered_workspaces[0].get("structure_snapshot") or item.get("implementation_contract") != registered_workspaces[0].get("implementation_contract"):
            fail("Both workspaces must share the same structure snapshot and implementation contract")
        preview_name, _, query = item.get("preview_entry", "").partition("?")
        if preview_name != "nh01_acceptance/gemini-native-preview.html" or f"embed={item['id']}" not in query:
            fail(f"Workspace preview entry must use the shared Gemini renderer: {item.get('id')}")
        if not (design / "manager" / preview_name).is_file():
            fail(f"Workspace Gemini preview route missing: {preview_name}")
    for source_path, label in [
        (registered_workspaces[0]["structure_snapshot"], "Gemini structure snapshot"),
        (registered_workspaces[0]["implementation_contract"], "Gemini implementation contract"),
    ]:
        if not (root / source_path).is_file():
            fail(f"{label} source is missing: {source_path}")
    if "embed-structured" not in gemini_entry.read_text(encoding="utf-8") or "embed-responsive" not in gemini_entry.read_text(encoding="utf-8"):
        fail("Shared Gemini preview must provide both internal Design Manager embed modes")
    asset_rows = manifest.get("assets", [])
    expected_public_assets = {"background", "brand", "avatar", "camera"}
    public_assets = {item.get("id"): item for item in asset_rows if item.get("public_preview")}
    if set(public_assets) != expected_public_assets:
        fail(f"Gemini preview public asset allowlist changed: {sorted(public_assets)}")
    preview_dir = (design / "manager" / "nh01_acceptance").resolve()
    for asset_id, asset in public_assets.items():
        preview_path = (preview_dir / asset.get("preview_path", "")).resolve()
        if not preview_path.is_file() and not built and asset_id == "avatar":
            # The original app asset is intentionally materialized into the preview bundle by Pages.
            preview_path = (root / asset.get("source_path", "")).resolve()
        if root.resolve() not in preview_path.parents or not preview_path.is_file():
            fail(f"Gemini public preview asset missing or outside site root: {asset_id} {preview_path}")
        asset_bytes = preview_path.read_bytes()
        if hashlib.sha256(asset_bytes).hexdigest() != asset.get("sha256"):
            fail(f"Gemini Manifest SHA-256 mismatch for {asset_id}")
        if asset_bytes[:8] != b"\x89PNG\r\n\x1a\n" or len(asset_bytes) < 24:
            fail(f"Gemini preview asset is not a valid PNG: {asset_id}")
        actual_dim = struct.unpack(">II", asset_bytes[16:24])
        if list(actual_dim) != asset.get("dimensions_px"):
            fail(f"Gemini Manifest dimension mismatch for {asset_id}: {actual_dim}")
    blocked = {item.get("id"): item for item in asset_rows if not item.get("public_preview")}
    if blocked.get("hero_photo", {}).get("status") != "BLOCKED_ASSET_SOURCE" or blocked.get("neighbor_photos", {}).get("status") != "BLOCKED_MISSING_APPROVED_INDEPENDENT_ORIGINALS":
        fail("Unapproved Hero/neighbor photos must remain explicit blocked placeholders")

    recognition = next((x for x in registry.get("features", []) if x.get("id") == "recognition_flow_v1_2"), None)
    if not recognition:
        fail("recognition_flow_v1_2 missing from experience registry")
    recognition_ids = [x.get("id") for x in recognition.get("hifi_views", [])]
    if recognition_ids != EXPECTED_RECOGNITION_FLOW:
        fail(f"Recognition main journey navigation changed: {recognition_ids} != {EXPECTED_RECOGNITION_FLOW}")
    flow_models = next((x for x in recognition["hifi_views"] if x.get("id") == "recognition_full_flow_models"), None)
    if (
        flow_models is None
        or flow_models.get("title") != "09 · 识别全流程-和模型"
        or flow_models.get("status") != "DESIGN_ONLY"
        or flow_models.get("render_mode") != "recognition_full_flow_models"
    ):
        fail("09 · 识别全流程-和模型 must remain a registered DESIGN_ONLY submenu")
    guide = next(x for x in recognition["hifi_views"] if x.get("id") == "capture_first_use_guide")
    guide_children = [x.get("id") for x in guide.get("children", [])]
    if guide.get("title") != "07 · 首次拍照教学" or guide_children != ["p01", "p02", "p03", "resources"]:
        fail("07 guide must retain P01/P02/P03 and expose the Resources submenu")
    resources = guide["children"][-1]
    if (
        resources.get("title") != "资源"
        or resources.get("status") != "DESIGN_ONLY"
        or resources.get("authority_type") != "asset_catalog"
        or resources.get("render_mode") != "recognition_guide_resources"
        or resources.get("resource_ids") != ["p01", "p02", "p03"]
        or resources.get("runtime_visual_authority") is not False
    ):
        fail("guide Resources submenu must expose only its design-source P01/P02/P03 catalog")
    guide_manifest = load_json(root / resources["resource_manifest"])
    guide_navigation = load_json(root / "design/pages/recognition/first_use_camera_guide/navigation.json")
    resource_menus = guide_navigation.get("resource_menus", [])
    if (
        len(resource_menus) != 1
        or resource_menus[0].get("id") != "resources"
        or resource_menus[0].get("route") != "#page/recognition_flow_v1_2/hifi/capture_first_use_guide/resources"
        or resource_menus[0].get("resource_ids") != ["p01", "p02", "p03"]
        or resource_menus[0].get("runtime_visual_authority") is not False
    ):
        fail("guide navigation manifest must register the separate Design Manager Resources submenu")
    manifest_assets = {item.get("id"): item for item in guide_manifest.get("assets", [])}
    frozen_children = {item.get("id"): item for item in guide.get("children", []) if item.get("id") in {"p01", "p02", "p03"}}
    for asset_id in ("p01", "p02", "p03"):
        child = frozen_children.get(asset_id, {})
        asset = manifest_assets.get(asset_id, {})
        if child.get("visual_authority") != asset.get("path") or child.get("visual_authority_sha256") != asset.get("sha256"):
            fail(f"Resources submenu source identity drift for {asset_id.upper()}")
    journey = next((x for x in registry["features"] if x.get("id") == "recognition_result_v1"), None)
    if not journey or [x.get("id") for x in journey.get("hifi_views", [])] != EXPECTED_RECOGNITION_RESULT:
        fail("Recognition Result hifi views must preserve RR00–RR07 and append RR08/RR09")
    result_views = {x.get("id"): x for x in journey.get("hifi_views", [])}
    rr08 = result_views.get("visual_acceptance", {})
    rr09 = result_views.get("motion_continuation", {})
    if rr08.get("status") != "FROZEN" or rr08.get("readiness_status") != "READY_FOR_VISUAL_IMPLEMENTATION":
        fail("RR08 visual acceptance index must be frozen while preserving its separate implementation-readiness status")
    if rr09.get("status") != "FROZEN" or rr09.get("behavior_authority") != "design/pages/recognition/result/motion/Recognition_Result_Motion_Spec_V2.md":
        fail("RR09 motion continuation must resolve to the frozen Motion V2 authority")
    if rr09.get("machine_authority") != "design/pages/recognition/result/motion/recognition_result_motion_contract_v2.json":
        fail("RR09 machine-readable Motion V2 contract is not registered")
    result_nav = load_json(design / "pages/recognition/result/navigation.json")
    rr09_nav = next((x for x in result_nav.get("level_2", []) if x.get("id") == "RR09"), None)
    if not rr09_nav or rr09_nav.get("order") != 10 or rr09_nav.get("status") != "FROZEN":
        fail("Recognition Result RR09 · 动效接续 menu contract missing or out of order")

    # NH07 is a design-only asset workspace: gate its source identity, not Android runtime.
    if not built:
        nh07 = feature["hifi_views"][-1]
        if nh07.get("id") != "NH07" or nh07.get("render_mode") != "nh07_avatar_states":
            fail("NH07 must be registered as its own avatar state workspace")
        manifest_path = root / "design/pages/home/normal_home/07_avatar_states/assets/avatar_asset_manifest_v1.json"
        manifest = load_json(manifest_path)
        expected = [(64, "1x"), (128, "2x"), (192, "3x")]
        exports = manifest.get("exports", [])
        if len(exports) != 3:
            fail("NH07 requires exactly three registered avatar density exports")
        if nh07.get("image") != exports[-1].get("path"):
            fail("NH07 registry preview must use the registered 3x avatar")
        for asset, (side, scale) in zip(exports, expected):
            if asset.get("scale") != scale or asset.get("width") != side or asset.get("height") != side:
                fail(f"NH07 density manifest ordering/dimensions incorrect for {scale}")
            file_path = root / asset["path"]
            if not file_path.exists():
                fail(f"NH07 asset missing: {file_path}")
            raw = file_path.read_bytes()
            if len(raw) != asset["bytes"] or hashlib.sha256(raw).hexdigest() != asset["sha256"]:
                fail(f"NH07 PNG bytes/hash mismatch: {file_path}")
            if raw[:8] != b"\x89PNG\r\n\x1a\n" or len(raw) < 24:
                fail(f"NH07 invalid PNG header: {file_path}")
            width, height = struct.unpack(">II", raw[16:24])
            if (width, height) != (side, side):
                fail(f"NH07 actual PNG dimensions mismatch: {file_path}")
        # The source is a real 1254px original, never an enlarged 192px export.
        master = manifest.get("high_resolution_master", {})
        source = manifest.get("source", {})
        master_path = master.get("path")
        expected_master_sha = "12ec5fc8c1a08723ea461edf327870df19b7dfe309587b18b6de28c9f701b643"
        if not master_path or source.get("path") != master_path or master.get("sha256") != expected_master_sha:
            fail("NH07 mother path/identity must be recorded consistently in source/high_resolution_master")
        if nh07.get("master_source") != master_path or nh07.get("master_sha256") != expected_master_sha:
            fail("NH07 registry must expose exact high-resolution mother")
        if (master.get("width"), master.get("height"), master.get("bytes")) != (1254, 1254, 1255352):
            fail("NH07 mother metadata must match the approved source bytes")
        master_file = root / master_path
        if not master_file.exists():
            fail(f"NH07 mother original missing: {master_path}")
        master_raw = master_file.read_bytes()
        if len(master_raw) != 1255352 or hashlib.sha256(master_raw).hexdigest() != expected_master_sha:
            fail("NH07 original mother SHA/byte count mismatch")
        if master_raw[:8] != b"\x89PNG\r\n\x1a\n" or struct.unpack(">II", master_raw[16:24]) != (1254, 1254):
            fail("NH07 mother file is not an original 1254px PNG")
        guest = manifest.get("states", {}).get("GUEST", "")
        if not guest or not (root / guest).exists():
            fail("NH07 Guest must resolve to separate registered source")

    # Record Date V2 originals and user-approved design layout are FROZEN; runtime remains unverified.
    record = next((f for f in registry.get("features", []) if f.get("id") == "record_date_v2"), None)
    if record is None or record.get("display_name") != "记录日期":
        fail("Record Date V2 top-level navigation missing")
    if [v.get("id") for v in record.get("hifi_views", [])] != ["month_v2", "year_v2", "interaction"]:
        fail("Record Date V2 must register month/year/interaction direct submenus")
    record_manifest = load_json(design / "pages/record_date/v2/assets/asset_manifest.json")
    for view_id, entry_key, dim in [
        ("month_v2", "month", (853, 1844)),
        ("year_v2", "year", (935, 1683)),
    ]:
        entry = record_manifest[entry_key]
        view = next(v for v in record["hifi_views"] if v["id"] == view_id)
        if view.get("status") != "FROZEN" or view.get("image") != entry["path"] or entry.get("status") != "FROZEN":
            fail(f"Record Date {view_id} must render registered frozen original PNG")
        data_path = root / entry["path"]
        if not data_path.is_file():
            fail(f"Record Date V2 source PNG absent: {entry['path']}")
        data = data_path.read_bytes()
        if data[:8] != b"\x89PNG\r\n\x1a\n" or len(data) != entry["bytes"]:
            fail(f"Record Date PNG bytes/header mismatch: {view_id}")
        width, height = struct.unpack(">II", data[16:24])
        if (width, height) != dim or hashlib.sha256(data).hexdigest() != entry["sha256"]:
            fail(f"Record Date image SHA256/dimensions mismatch: {view_id}")
    if record.get("design_overall") != "FROZEN":
        fail("Record Date images must remain approved FROZEN")

    if record_manifest.get("status") != "FROZEN":
        fail("Record Date PNG manifest is not frozen")
    record_status = load_json(design / "pages/record_date/v2/status.json")
    record_nav = load_json(design / "pages/record_date/v2/navigation.json")
    if record_status.get("design_overall") != "FROZEN" or not record_status.get("frozen"):
        fail("Record Date feature status does not record approved freeze")
    if record_nav.get("status") != "FROZEN" or [v.get("status") for v in record_nav.get("level_2", [])] != ["FROZEN"] * 3:
        fail("Record Date navigation freeze status inconsistent")
    if any(v.get("status") != "FROZEN" for v in record.get("hifi_views", [])):
        fail("Record Date month/year/interaction must be frozen")
    if record.get("modalities", {}).get("runtime", {}).get("status") != "MISSING":
        fail("Record Date Android runtime cannot become validated as a side effect of design freeze")
    decision_path = design / "pages/record_date/v2/review/Record_Date_V2_Freeze_Decision_20261010.md"
    if not decision_path.is_file():
        fail("Record Date V2 freeze decision missing")
    decision = decision_path.read_text(encoding="utf-8")
    if not all(token in decision for token in ("68", "59", "BG_DATA", "FishRecord")):
        fail("Record Date known data/background exceptions must be documented")

    app = app_source
    required_logic = '(selectedKey === key || selectedKey.startsWith(key + "/"))'
    if required_logic not in app:
        fail("selected page no longer auto-expands its submenu")

    if "nh07-master-open" not in app or "openNh07MasterLightbox" not in app or "dialog.showModal" not in app:
        fail("NH07 must provide a clickable modal preview of its verified mother image")
    if "recognitionGuideResourcesCanvas" not in app or "guide-resource-actions" not in app:
        fail("guide Resources submenu must render original-file previews and download links")

    route_logic = (
        'window.addEventListener("popstate", selectFromHash)',
        "renderInvalidManagerRoute",
        "selectShared(id, variantId, false)",
        "selectPage(id, null, hifiId, childId, false)",
        "openHifiImageLightbox",
        'data-zoom="fit"',
        'hifiViewContractDetails'
    )
    if any(token not in app and token not in index_path.read_text(encoding="utf-8") for token in route_logic):
        fail("Design Manager direct Hash / history / Hi-Fi preview routes regressed")

    index = index_path.read_text(encoding="utf-8") if index_path.exists() else ""
    if built:
        if "__DESIGN_MANAGER_BUILD__" in index or "__DESIGN_MANAGER_BUILD_SHORT__" in index:
            fail("Pages artifact still contains unresolved build placeholders")
        if expected_build and expected_build not in index:
            fail(f"Pages artifact does not identify expected build {expected_build}")
        if "app.js?v=" not in index or "styles.css?v=" not in index:
            fail("Pages artifact lost cache-busted JS/CSS URLs")
    else:
        if 'app.js?v=__DESIGN_MANAGER_BUILD__' not in index:
            fail("source index lost app.js build cache-busting placeholder")
        if 'styles.css?v=__DESIGN_MANAGER_BUILD__' not in index:
            fail("source index lost styles.css build cache-busting placeholder")
        if 'name="design-manager-build" content="__DESIGN_MANAGER_BUILD__"' not in index:
            fail("source index lost deployment build marker")

    print(
        "PASS: Design Manager navigation guard | "
        f"Normal Home={','.join(hifi_ids)} | Recognition Flow={','.join(recognition_ids)} | built={built}"
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--site", type=Path, default=None)
    parser.add_argument("--expected-build", default=None)
    args = parser.parse_args()

    repo_root = Path(__file__).resolve().parents[1]
    validate_tree(repo_root)

    if args.site is not None:
        validate_tree(args.site.resolve(), built=True, expected_build=args.expected_build)


if __name__ == "__main__":
    main()
