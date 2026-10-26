#!/usr/bin/env python3
"""Regression gate for YuJian Design Manager navigation and Pages artifact."""

from __future__ import annotations

import argparse
import json
import hashlib
import struct
from pathlib import Path
import sys

EXPECTED_NORMAL_HOME = ["NH01", "NH02", "NH03", "NH04", "NH05", "NH06", "NH07", "multi_screen_static_hifi"]
EXPECTED_RECOGNITION_FLOW = [
    "main_journey_overview", "state_timeline", "visual_states",
    "layer_component_ownership", "motion_transition", "degradation_accessibility",
    "runtime_evidence", "capture_first_use_guide", "boundary_state_release_gate",
    "recognition_full_flow_models", "swos_optical_contract", "processing_result_continuity",
    "swos_static_visual_v1_1",
    "web_hifi_contract_v2", "swos_web_hifi_motion_contract_v1_0",
    "swos_rr09_optical_v1_2", "fishtrace_sites_web_hifi_workspace",
]
EXPECTED_RECOGNITION_RESULT = [
    "overview", "high", "medium", "low", "no_fish", "image_quality",
    "content_edit", "decision_logic", "visual_acceptance", "motion_continuation",
    "motion_continuation_v2_history",
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
    index = index_path.read_text(encoding="utf-8") if index_path.exists() else ""

    feature = next((x for x in registry.get("features", []) if x.get("id") == "home_normal_v1"), None)
    if not feature:
        fail("home_normal_v1 missing from experience registry")

    hifi_ids = [x.get("id") for x in feature.get("hifi_views", [])]
    if hifi_ids != EXPECTED_NORMAL_HOME:
        fail(f"home_normal_v1 hifi_views changed: {hifi_ids} != {EXPECTED_NORMAL_HOME}")

    nav_ids = [x.get("id") for x in nav.get("level_2", [])]
    if nav_ids != EXPECTED_NORMAL_HOME:
        fail(f"Normal Home navigation changed: {nav_ids} != {EXPECTED_NORMAL_HOME}")

    multiscreen = feature["hifi_views"][-1]
    expected_route = "#page/home_normal_v1/hifi/multi_screen_static_hifi"
    if (
        multiscreen.get("id") != "multi_screen_static_hifi"
        or multiscreen.get("title") != "结构化高保真 · 多屏静态与响应式规范"
        or multiscreen.get("render_mode") != "nh01_multi_screen_static_hifi"
        or multiscreen.get("status") != "STATIC_HIFI_FROZEN"
        or multiscreen.get("design_approval_status") != "STATIC_RULES_FROZEN"
        or multiscreen.get("runtime_status") != "NOT_VERIFIED"
    ):
        fail("NH01 multi-screen static hi-fi route must preserve frozen design and NOT_VERIFIED runtime statuses")
    nav_view = nav["level_2"][-1]
    if nav_view.get("id") != "multi_screen_static_hifi" or nav_view.get("route") != expected_route or nav_view.get("label_zh") != multiscreen.get("title"):
        fail("NH01 multi-screen workspace must be the final direct Hash navigation entry")
    module_path = design / "manager" / "nh01_structured_workspace.js"
    module_css = design / "manager" / "nh01_structured_workspace.css"
    app_source = app_path.read_text(encoding="utf-8")
    if not module_path.is_file() or not module_css.is_file():
        fail("NH01 structured workspace JS/CSS module is missing")
    if "window.NH01StructuredWorkspace?.mount(" in app_source:
        fail("NH01 structured mount must report missing module/mount prerequisites explicitly")
    for marker in (
        'data-resource-id="nh01-workspace-js"',
        'data-resource-id="nh01-workspace-css"',
        "createResourceLoader",
        'data-testid="nh01-resource-loader"',
        "AbortController",
        "RETRYING",
        "typeof window.NH01StructuredWorkspace?.mount",
    ):
        if marker not in index + app_source + module_path.read_text(encoding="utf-8"):
            fail(f"NH01 load/mount failure path marker is missing: {marker}")
    for key in ("authority", "machine_authority", "visual_authority_manifest", "acceptance_matrix", "native_acceptance_authority"):
        target = multiscreen.get(key)
        if not target or not (root / target).is_file():
            fail(f"NH01 multi-screen registration has missing {key}: {target}")

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
    swos_optical = next((x for x in recognition["hifi_views"] if x.get("id") == "swos_optical_contract"), None)
    continuity = next((x for x in recognition["hifi_views"] if x.get("id") == "processing_result_continuity"), None)
    swos_static = next((x for x in recognition["hifi_views"] if x.get("id") == "swos_static_visual_v1_1"), None)
    if (
        swos_optical is None or swos_optical.get("status") != "ACTIVE_CLOSURE"
        or swos_optical.get("authority") != "design/pages/recognition/processing/optics/SWOS_Optical_Contract_V1.md"
        or swos_optical.get("machine_authority") != "design/pages/recognition/processing/optics/SWOS_Optical_Contract_V1.json"
    ):
        fail("SWOS Optical Contract V1 must be a registered Processing submenu")
    if (
        continuity is None or continuity.get("status") != "ACTIVE_CLOSURE"
        or continuity.get("authority") != "design/pages/recognition/continuity/Processing_Result_Optical_Continuity_Contract_V1.md"
        or continuity.get("machine_authority") != "design/pages/recognition/continuity/processing_result_optical_continuity_v1.json"
    ):
        fail("Processing → Result Optical Continuity Contract V1 must be registered")
    if not (root / swos_optical["authority"]).is_file() or not (root / continuity["authority"]).is_file():
        fail("SWOS/continuity Design Manager authority source is missing")
    if (
        swos_static is None or swos_static.get("title") != "12 · SWOS · 静态视觉"
        or swos_static.get("status") != "DESIGN_ONLY"
        or swos_static.get("design_status") != "FROZEN_DESIGN_DIRECTION"
        or swos_static.get("adoption_status") != "MAIN_ADOPTED_DESIGN_ONLY"
        or swos_static.get("adoption_pr") != 190
        or swos_static.get("render_mode") != "swos_static_visual_workspace"
        or swos_static.get("playback_implemented") is not False
    ):
        fail("SWOS V1.1 must remain a main-adopted, static-only Design Manager submenu")
    for field in ("authority", "machine_authority", "transition_authority", "asset_manifest"):
        path = swos_static.get(field)
        if not path or not (root / path).is_file():
            fail(f"SWOS static Design Manager {field} is missing: {path}")
    if not any(x.get("route") == "#page/recognition_result_v1/hifi/motion_continuation" for x in swos_static.get("related_routes", [])):
        fail("SWOS static submenu must link to RR09 V3")
    if not any(x.get("route") == "#page/recognition_flow_v1_2/hifi/swos_web_hifi_motion_contract_v1_0" for x in swos_static.get("related_routes", [])):
        fail("SWOS static submenu must link to the Web motion technical contract")
    static_frames = load_json(root / swos_static["machine_authority"])
    if static_frames.get("frame_count") != 15 or len(static_frames.get("keyframes", [])) != 15:
        fail("SWOS keyframe authority must contain exactly 15 frames")
    if static_frames.get("total_nominal_duration_ms") != 3650 or static_frames.get("processing", {}).get("duration_ms") != 2800 or static_frames.get("rr09", {}).get("duration_ms") != 850:
        fail("SWOS keyframe duration contract drift")
    if not any(x.get("id") == "R0.5" and x.get("relative_ms") == 100 and x.get("global_nominal_ms") == 2900 for x in static_frames["keyframes"]):
        fail("SWOS static keyframe contract must include R0.5 at 100ms / 2900ms")
    result_feature = next((x for x in registry.get("features", []) if x.get("id") == "recognition_result_v1"), None)
    result_rr09 = next((x for x in (result_feature or {}).get("hifi_views", []) if x.get("id") == "motion_continuation"), None)
    if not result_rr09 or not any(x.get("route") == "#page/recognition_flow_v1_2/hifi/swos_static_visual_v1_1" for x in result_rr09.get("related_routes", [])):
        fail("RR09 V3 must link back to the SWOS static visual workspace")
    result_navigation = load_json(root / "design/pages/recognition/result/navigation.json")
    rr09_navigation = next((x for x in result_navigation.get("level_2", []) if x.get("id") == "RR09"), None)
    if not rr09_navigation or not any(x.get("route") == "#page/recognition_flow_v1_2/hifi/swos_static_visual_v1_1" for x in rr09_navigation.get("related_manager_routes", [])):
        fail("RR09 page navigation manifest must link to SWOS static review")
    if (root / "design/manager/swos_static_visual.js").is_file() is False:
        fail("SWOS static visual workspace renderer is missing")
    if not any("swos_static_visual.js" in line for line in (root / "design/manager/index.html").read_text(encoding="utf-8").splitlines()):
        fail("Design Manager HTML does not load the SWOS static visual renderer")
    swos_web_motion = next((x for x in recognition["hifi_views"] if x.get("id") == "swos_web_hifi_motion_contract_v1_0"), None)
    if (
        swos_web_motion is None
        or swos_web_motion.get("title") != "14 · SWOS Web 高保真动效技术合同 V1.0"
        or swos_web_motion.get("status") != "DESIGN_ONLY"
        or swos_web_motion.get("render_mode") != "authority_index"
        or swos_web_motion.get("authority") != "design/pages/recognition/web/SWOS_WEB_HIFI_MOTION_TECHNICAL_CONTRACT_V1_0.md"
        or swos_web_motion.get("machine_authority") != "design/pages/recognition/web/contracts/SWOS_WEB_HIFI_MOTION_TECHNICAL_CONTRACT_V1_0.json"
        or swos_web_motion.get("runtime_status") != "NOT_VERIFIED"
    ):
        fail("SWOS Web motion technical contract must be a registered design-only authority route")
    for field in ("authority", "machine_authority"):
        target = swos_web_motion.get(field)
        if not target or not (root / target).is_file():
            fail(f"SWOS Web motion technical contract is missing {field}: {target}")
    if not any(link.get("route") == "#page/recognition_flow_v1_2/hifi/swos_static_visual_v1_1" for link in swos_web_motion.get("related_routes", [])):
        fail("SWOS Web motion contract must link back to the 15-frame static review route")
    sites_hifi = next((x for x in recognition["hifi_views"] if x.get("id") == "fishtrace_sites_web_hifi_workspace"), None)
    if (
        sites_hifi is None
        or sites_hifi.get("title") != "16 · FishTrace Sites Web 高保真与结构化原型 V1"
        or sites_hifi.get("status") != "DESIGN_ONLY"
        or sites_hifi.get("render_mode") != "fishtrace_sites_web_hifi_workspace"
        or sites_hifi.get("runtime_status") != "NOT_VERIFIED"
        or sites_hifi.get("runtime_visual_verified") is not False
        or sites_hifi.get("production_inference_connected") is not False
    ):
        fail("FishTrace Sites Web high-fidelity prototype must remain a design-only, non-runtime route")
    for field in ("authority", "machine_authority", "acceptance_matrix"):
        target = sites_hifi.get(field)
        if not target or not (root / target).is_file():
            fail(f"FishTrace Sites Web prototype is missing {field}: {target}")
    prototype = load_json(root / sites_hifi["machine_authority"])
    if prototype.get("prototype_id") != "FISHTRACE-SITES-WEB-STRUCTURED-PROTOTYPE-V1":
        fail("FishTrace Sites structured prototype machine ID mismatch")
    if prototype.get("production_inference_connected") is not False or prototype.get("sites_source_modified") is not False:
        fail("FishTrace Sites design prototype must not claim production inference or Sites source changes")
    for rel in (
        "design/manager/fishtrace_sites_web_workspace.html",
        "design/manager/fishtrace_sites_web_workspace.css",
        "design/manager/fishtrace_sites_web_workspace.js",
    ):
        if not (root / rel).is_file():
            fail(f"FishTrace Sites interactive prototype resource missing: {rel}")
    if "fishtrace_sites_web_hifi_workspace" not in app_source or "fishtrace_sites_web_workspace.html" not in app_source:
        fail("Design Manager FishTrace Sites prototype iframe renderer is missing")
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
    rr09_v2_history = result_views.get("motion_continuation_v2_history", {})
    if rr08.get("status") != "FROZEN" or rr08.get("readiness_status") != "READY_FOR_VISUAL_IMPLEMENTATION":
        fail("RR08 visual acceptance index must be frozen while preserving its separate implementation-readiness status")
    if rr09.get("status") != "ACTIVE_CLOSURE" or rr09.get("render_mode") != "authority_index" or rr09.get("authority") != "design/pages/recognition/result/motion/Recognition_Result_Motion_Spec_V3.md":
        fail("Current RR09 must resolve to the main-adopted design-only Motion V3 authority index")
    if rr09.get("machine_authority") != "design/pages/recognition/result/motion/recognition_result_motion_contract_v3.json":
        fail("RR09 machine-readable Motion V3 contract is not registered")
    if rr09_v2_history.get("status") != "DEPRECATED" or rr09_v2_history.get("render_mode") != "recognition_result_motion_playable":
        fail("RR09 V2 must remain explicitly reachable only as a deprecated historical prototype")
    if rr09_v2_history.get("authority") != "design/pages/recognition/result/motion/Recognition_Result_Motion_Spec_V2.md" or rr09_v2_history.get("machine_authority") != "design/pages/recognition/result/motion/recognition_result_motion_contract_v2.json":
        fail("RR09 V2 historical menu must preserve its original spec and machine contract")
    result_nav = load_json(design / "pages/recognition/result/navigation.json")
    rr09_nav = next((x for x in result_nav.get("level_2", []) if x.get("id") == "RR09"), None)
    rr09_v2_nav = next((x for x in result_nav.get("level_2", []) if x.get("id") == "RR09_V2_HISTORY"), None)
    if not rr09_nav or rr09_nav.get("order") != 10 or rr09_nav.get("status") != "ACTIVE_CLOSURE" or rr09_nav.get("contract") != "design/pages/recognition/result/motion/Recognition_Result_Motion_Spec_V3.md":
        fail("Recognition Result current RR09 V3 menu contract missing or out of order")
    if not rr09_v2_nav or rr09_v2_nav.get("order") != 11 or rr09_v2_nav.get("status") != "DEPRECATED":
        fail("RR09 V2 must remain visible as an explicitly historical menu entry")

    # NH07 is a design-only asset workspace: gate its source identity, not Android runtime.
    if not built:
        nh07 = next((item for item in feature["hifi_views"] if item.get("id") == "NH07"), None)
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

    app = app_path.read_text(encoding="utf-8") if app_path.exists() else ""
    guide_workspace_path = root / "design/manager/first_use_guide_workspace.js"
    guide_workspace = guide_workspace_path.read_text(encoding="utf-8") if guide_workspace_path.exists() else ""
    manager_index = (root / "design/manager/index.html").read_text(encoding="utf-8")
    required_logic = '(selectedKey === key || selectedKey.startsWith(key + "/"))'
    if required_logic not in app:
        fail("selected page no longer auto-expands its submenu")

    if "nh07-master-open" not in app or "openNh07MasterLightbox" not in app or "dialog.showModal" not in app:
        fail("NH07 must provide a clickable modal preview of its verified mother image")
    if "recognitionGuideResourcesCanvas" not in app or "FirstUseGuideWorkspace?.mount" not in app:
        fail("guide Resources submenu must mount the structured pre-development workspaces")
    if "first_use_guide_workspace.js" not in manager_index or "guide-resource-actions" not in guide_workspace or "data-guide-layer" not in guide_workspace:
        fail("guide workspaces must include interactive layers and direct original-file links")

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
        if f'nh01_structured_workspace.js?v={expected_build}' not in index or f'nh01_structured_workspace.css?v={expected_build}' not in index:
            fail("Pages artifact NH01 module JS/CSS cache version does not match its source SHA")
    else:
        if 'app.js?v=__DESIGN_MANAGER_BUILD__' not in index:
            fail("source index lost app.js build cache-busting placeholder")
        if 'styles.css?v=__DESIGN_MANAGER_BUILD__' not in index:
            fail("source index lost styles.css build cache-busting placeholder")
        if 'nh01_structured_workspace.js?v=__DESIGN_MANAGER_BUILD__' not in index or 'nh01_structured_workspace.css?v=__DESIGN_MANAGER_BUILD__' not in index:
            fail("source index lost the NH01 JS/CSS build cache-busting placeholders")
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
