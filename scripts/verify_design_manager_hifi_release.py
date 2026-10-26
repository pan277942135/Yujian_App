#!/usr/bin/env python3
"""Release gate for Recognition Design Manager hi-fi assets and routes."""

from __future__ import annotations

import argparse
import hashlib
import json
import struct
import sys
import tempfile
from pathlib import Path

GUIDE = Path("pages/recognition/first_use_camera_guide")
ASSETS = Path("system_v3/00_Product_Master_Flow/assets")
EXPECTED_GUIDE = {
    "p01": (941, 1671, "dc731cca5470ef2154bb06172b9836e823ea780b0833f04e34caaf883e465c50"),
    "p02": (941, 1671, "fb092235fd043a42aa8250aba9dfdb91a78e96549dd5a63f8e89cf7433685f8f"),
    "p03": (941, 1672, "1a3008483ac5708a0649501cf2aef6c799d47b95bcc2c1b1272fa64e68f7f9b3"),
}
EXPECTED_OVERVIEW = "2cf0ea161360888248fb42227d2a2511df64aa94a4c4d3af7c7ffd3f06a0aeea"
EXPECTED_NEW_STATES = "8cc7c04a4e15bf35ca0f307d0b95940d05901ead0cf6c66742a9f25d4d2e33e5"
EXPECTED_OVERVIEW_SVG = "fcb7a89215760b08901bdb94c2181dc5f40bc8ff0af0324b8ed0c8b705e0db6b"
EXPECTED_NEW_STATES_SVG = "c47c2fa800d201d331c72c3aa6037404887d2567d7526445ecd6cded9ca1b9de"
NH01_STATIC_ASSETS = [
    ("NH01-M01-320x480dp-STATIC-HIFI-V1", "design/pages/home/normal_home/08_multi_screen_static_hifi/NH01_M01_320x480dp_STATIC_HIFI_V1.png", 1024, 1536, "a3808f2fb93d48494eafbb6ba061dbd9c1dd019348488355780c8f4504636692"),
    ("NH01-M02-360x640dp-STATIC-HIFI-V1", "design/pages/home/normal_home/08_multi_screen_static_hifi/NH01_M02_360x640dp_STATIC_HIFI_V1.png", 941, 1672, "8a6721552cec97113c2a77200a07bb76b954c1a16f4e0f10d8410b9e873a90c0"),
    ("NH01-M03-390x844dp-STATIC-HIFI-V1", "design/pages/home/normal_home/08_multi_screen_static_hifi/NH01_M03_390x844dp_STATIC_HIFI_V1.png", 853, 1844, "6a5e2e9fd18d2b06f9452b6a6c95802e41cfc0450b7ec77cf0d1500c6b3932ff"),
    ("NH01-M04-430x932dp-STATIC-HIFI-V1", "design/pages/home/normal_home/08_multi_screen_static_hifi/NH01_M04_430x932dp_STATIC_HIFI_V1.png", 853, 1844, "07846d3089b7c2a0b0e6c858dbd3f97890121b225092dde7d7622f0a461b9893"),
    ("NH01-M05-480dp-WIDTH-STATIC-HIFI-V1", "design/pages/home/normal_home/08_multi_screen_static_hifi/NH01_M05_480dp_WIDTH_STATIC_HIFI_V1.png", 1024, 1536, "ee2ed666107bd7f4263cf140438a5a1cbb171af0199e57646372e2e1d8cc8de2"),
]
NH01_ORIGINAL_SHA = "6ab9d3348b4a9a7e77ddca3a06235b4991798a309bd3512cc6fb9ea7aeb1d377"


class ValidationError(Exception):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValidationError(message)


def load_json(path: Path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ValidationError(f"cannot read JSON {path}: {exc}") from exc


def check_png(path: Path, expected_sha: str, expected_dimensions: tuple[int, int]) -> dict:
    if not path.is_file():
        raise ValidationError(f"missing frozen PNG: {path}")
    raw = path.read_bytes()
    if raw[:8] != b"\x89PNG\r\n\x1a\n" or len(raw) < 24:
        raise ValidationError(f"invalid PNG header: {path}")
    actual_dimensions = struct.unpack(">II", raw[16:24])
    actual_sha = hashlib.sha256(raw).hexdigest()
    if actual_dimensions != expected_dimensions:
        raise ValidationError(f"PNG dimensions mismatch for {path}: {actual_dimensions}")
    if actual_sha != expected_sha:
        raise ValidationError(f"PNG SHA-256 mismatch for {path}: {actual_sha}")
    return {"path": str(path), "bytes": len(raw), "sha256": actual_sha, "dimensions": actual_dimensions}


def check_sha(path: Path, expected_sha: str) -> dict:
    if not path.is_file():
        raise ValidationError(f"missing authority file: {path}")
    raw = path.read_bytes()
    actual_sha = hashlib.sha256(raw).hexdigest()
    if actual_sha != expected_sha:
        raise ValidationError(f"SHA-256 mismatch for {path}: {actual_sha}")
    return {"path": str(path), "bytes": len(raw), "sha256": actual_sha}


def expect_failure(callable_, label: str) -> None:
    try:
        callable_()
    except ValidationError:
        return
    raise ValidationError(f"negative test did not fail: {label}")


def validate_nh01_multiscreen(root: Path, *, built: bool = False) -> list[str]:
    design = root / "design"
    archive = design / "pages/home/normal_home/08_multi_screen_static_hifi"
    manifest = load_json(archive / "NH01_MULTI_SCREEN_STATIC_HIFI_V1_MANIFEST.json")
    contract = load_json(archive / "NH01_MULTI_SCREEN_RESPONSIVE_CONTRACT_V1.json")
    matrix = load_json(archive / "NH01_STATIC_HIFI_ACCEPTANCE_MATRIX_V1.json")
    require(manifest.get("contract_id") == "NH01_MULTI_SCREEN_STATIC_HIFI_V1", "NH01 multi-screen contract identity drift")
    require(manifest.get("status") == "STATIC_HIFI_FROZEN" and manifest.get("rules_status") == "STATIC_RULES_FROZEN", "NH01 static freeze statuses drift")
    require(manifest.get("android_runtime_status") == "NOT_VERIFIED", "NH01 static archive cannot upgrade Android Runtime")
    registered = manifest.get("assets", [])
    require(len(registered) == len(NH01_STATIC_ASSETS), "NH01 manifest must register exactly M01-M05")
    files = {}
    for asset, expected in zip(registered, NH01_STATIC_ASSETS):
        asset_id, path, width, height, sha = expected
        require(asset.get("asset_id") == asset_id and asset.get("path") == path, f"NH01 asset registration path/ID drift: {asset_id}")
        require(asset.get("status") == "STATIC_HIFI_FROZEN" and asset.get("version") == "V1", f"NH01 freeze version/status drift: {asset_id}")
        require(asset.get("responsive_contract") == "NH01_MULTI_SCREEN_STATIC_HIFI_V1", f"NH01 contract mapping missing: {asset_id}")
        require(asset.get("actual_canvas_px") == {"width": width, "height": height}, f"NH01 manifest canvas mismatch: {asset_id}")
        require(asset.get("size_bytes") == (root / path).stat().st_size, f"NH01 manifest byte count mismatch: {asset_id}")
        files[asset_id] = check_png(root / path, sha, (width, height))
        require(asset.get("sha256") == sha, f"NH01 manifest SHA drift: {asset_id}")
    original = manifest.get("original_reference", {})
    require(original.get("path") == "design/system/core_visual_v1/reference/normal_home_v1.png" and original.get("historical_sha256") == NH01_ORIGINAL_SHA, "original NH01 reference identity drift")
    check_png(root / original["path"], NH01_ORIGINAL_SHA, (1080, 1920))
    require(manifest.get("provenance", {}).get("visual_content_recreated_or_modified") is False, "recovered PNG provenance must state unchanged original bytes")
    require(manifest.get("provenance", {}).get("private_library_file_ids_excluded") is True, "public asset manifest must not expose Library file IDs")

    require(contract.get("contract_id") == manifest["contract_id"] and contract.get("rules_status") == "STATIC_RULES_FROZEN", "NH01 responsive contract identity/status drift")
    require(len(contract.get("viewport_profiles", [])) == 5, "responsive contract must cover five viewport profiles")
    require(contract["viewport_profiles"][-1].get("id") == "M05" and contract["viewport_profiles"][-1].get("height_dp") is None, "M05 must remain width-only without an invented Android height")
    targets = contract.get("spacing", {}).get("title_to_card_targets_dp_at_font_scale_1_0", [])
    require(len(targets) == 5 and targets[-1].get("target") == 24 and targets[-1].get("tolerance") == 2, "480dp 24±2dp static spacing extension is missing")
    require("NEW STATIC WIDTH EXTENSION" in targets[-1].get("authority", "") and targets[-1].get("scope", "").startswith("static design preview only"), "M05 24dp target must declare new static-only authority scope")
    require(len(contract.get("typography", {}).get("roles", [])) == 9, "complete NH01 typography role table is missing")
    require(len(contract.get("spacing", {}).get("required_adjacent_interval_measurements", [])) == 6, "six adjacent-region measurement contracts are missing")
    require(len(contract.get("authority_crosswalk", [])) >= 8, "existing NH01 authority crosswalk is incomplete")
    markdown_path = archive / "NH01_MULTI_SCREEN_RESPONSIVE_CONTRACT_V1.md"
    require(markdown_path.is_file(), "NH01 full responsive Markdown contract is missing")
    markdown = markdown_path.read_text(encoding="utf-8")
    for phrase in ("M05 addition", "Android acceptance status", "six", "UNSUPPORTED_WINDOW", "Morning Lake", "Guest", "Avatar"):
        require(phrase.lower() in markdown.lower(), f"NH01 full responsive contract section missing: {phrase}")
    for ref in contract.get("authority_crosswalk", []):
        # The Pages artifact intentionally contains the public design system, not Android source.
        # Source-tree validation runs before artifact validation and checks these implementation traces.
        if built and Path(ref["path"]).parts[0] == "app":
            continue
        require((root / ref["path"]).is_file(), f"NH01 crosswalk authority missing: {ref['path']}")

    native = matrix.get("android_matrix", {})
    cases = native.get("cases", [])
    require(native.get("required_case_count") == 24 and len(cases) == 24, "NH01 must retain the exact 24-case native matrix")
    require(matrix.get("android_runtime_status") == "NOT_VERIFIED" and native.get("native_status") == "NOT_VERIFIED", "browser/image checks cannot promote Android Runtime")
    require(all(item.get("execution_status") == "NOT_RUN" and item.get("evidence") is None for item in cases), "native NOT_RUN records require real evidence before changing")
    require(set(item.get("viewport_dp") for item in cases) == {"320x480", "360x640", "390x844", "430x932"}, "native matrix viewport set drift")
    require(set(item.get("layout_variant") for item in cases) == {"ADAPTIVE_V2", "V3_BASELINE"}, "native layout variant set drift")
    require(set(item.get("system_font_scale") for item in cases) == {1.0, 1.3, 1.5}, "native font scale set drift")

    registry = load_json(design / "registry/experience_registry_v1.json")
    feature = next((item for item in registry.get("features", []) if item.get("id") == "home_normal_v1"), None)
    view = next((item for item in (feature or {}).get("hifi_views", []) if item.get("id") == "multi_screen_static_hifi"), None)
    require(view is not None and view.get("render_mode") == "nh01_multi_screen_static_hifi", "NH01 Design Manager static workspace renderer is not registered")
    require(view.get("status") == "STATIC_HIFI_FROZEN" and view.get("design_approval_status") == "STATIC_RULES_FROZEN" and view.get("runtime_status") == "NOT_VERIFIED", "NH01 Design Manager statuses drift")
    require(view.get("authority") == "design/pages/home/normal_home/08_multi_screen_static_hifi/NH01_MULTI_SCREEN_RESPONSIVE_CONTRACT_V1.md", "NH01 Design Manager contract path drift")
    nav = load_json(design / "pages/home/normal_home/navigation.json")
    menu = next((item for item in nav.get("level_2", []) if item.get("id") == "multi_screen_static_hifi"), None)
    require(menu and menu.get("route") == "#page/home_normal_v1/hifi/multi_screen_static_hifi", "NH01 direct menu route missing")
    app = (design / "manager/app.js").read_text(encoding="utf-8")
    css = (design / "manager/styles.css").read_text(encoding="utf-8")
    require("nh01ResponsiveRuleCanvas" in app and "nh01-static-asset" in app and "data-testid=\"nh01-multiscreen-workspace\"" in app, "NH01 Design Manager renderer/gallery is missing")
    require(".nh01-static-gallery" in css and "@media(max-width:650px)" in css.replace(" ", ""), "NH01 responsive gallery CSS is missing")
    return ["NH01 M01-M05 source PNG integrity", "NH01 original raster preserved", "NH01 responsive contract and crosswalk", "NH01 24-case native matrix remains NOT_VERIFIED", "NH01 Design Manager registry and navigation"]


def validate_tree(root: Path, *, built: bool = False, expected_build: str | None = None) -> list[str]:
    design = root / "design"
    registry_path = design / "registry/experience_registry_v1.json"
    registry = load_json(registry_path)
    flow = next((f for f in registry.get("features", []) if f.get("id") == "recognition_flow_v1_2"), None)
    require(flow is not None, "recognition_flow_v1_2 missing")
    views = flow.get("hifi_views", [])
    by_view = {v.get("id"): v for v in views}
    overview = by_view.get("main_journey_overview")
    boundary = by_view.get("boundary_state_release_gate")
    guide_view = by_view.get("capture_first_use_guide")
    full_flow = by_view.get("recognition_full_flow_models")
    swos_static = by_view.get("swos_static_visual_v1_1")
    sites_hifi = by_view.get("fishtrace_sites_web_hifi_workspace")
    require(overview and boundary and guide_view and full_flow and swos_static and sites_hifi, "overview/boundary/guide/full-flow/SWOS static/FishTrace Sites Hi-Fi view missing")
    require(views[0].get("id") == "main_journey_overview", "00 journey overview is not the first recognition route")
    view_ids = [view.get("id") for view in views]
    require(
        view_ids.index("boundary_state_release_gate") < view_ids.index("recognition_full_flow_models")
        < view_ids.index("swos_optical_contract") < view_ids.index("processing_result_continuity")
        < view_ids.index("swos_static_visual_v1_1") < view_ids.index("fishtrace_sites_web_hifi_workspace"),
        "08 boundary, 09 full-flow/model, SWOS, continuity, static visual and FishTrace Sites prototype routes must retain their registered order",
    )
    require(full_flow.get("title") == "09 · 识别全流程-和模型", "09 full-flow/model menu title drift")
    require(full_flow.get("status") == "DESIGN_ONLY" and full_flow.get("render_mode") == "recognition_full_flow_models", "09 full-flow/model must remain DESIGN_ONLY with its renderer")
    require(
        swos_static.get("status") == "DESIGN_ONLY"
        and swos_static.get("design_status") == "FROZEN_DESIGN_DIRECTION"
        and swos_static.get("adoption_status") == "MAIN_ADOPTED_DESIGN_ONLY"
        and swos_static.get("adoption_pr") == 190
        and swos_static.get("render_mode") == "swos_static_visual_workspace"
        and swos_static.get("playback_implemented") is False,
        "SWOS V1.1 static route must remain a non-playing dependent design review",
    )
    require(swos_static.get("source_prs") == [186, 187], "SWOS static route must retain its source PR provenance")
    frames = load_json(root / swos_static["machine_authority"])
    transition = load_json(root / swos_static["transition_authority"])
    require(frames.get("frame_count") == 15 and len(frames.get("keyframes", [])) == 15, "SWOS static authority must render exactly 15 frames")
    require(frames.get("total_nominal_duration_ms") == 3650, "SWOS static authority duration must stay at 3650ms")
    require(frames.get("processing", {}).get("frame_count") == 8 and frames.get("rr09", {}).get("frame_count") == 7, "SWOS frame tracks must stay 8 + 7")
    require(transition.get("samples", {}).get("R0.5", {}).get("relative_ms") == 100 if "relative_ms" in transition.get("samples", {}).get("R0.5", {}) else transition.get("samples", {}).get("R0.5", {}).get("global_nominal_ms") == 2900, "R0.5 geometry/time binding missing")
    require(
        full_flow.get("authority") == "design/system_v3/00_Product_Master_Flow/subflows/Recognition_Full_Flow_Models_V1.md"
        and full_flow.get("machine_authority") == "design/system_v3/00_Product_Master_Flow/subflows/Recognition_Full_Flow_Models_V1.json"
        and full_flow.get("image") == "design/system_v3/00_Product_Master_Flow/subflows/Recognition_Full_Flow_Models_V1.svg",
        "09 full-flow/model authority paths drift",
    )
    flow_machine = load_json(root / full_flow["machine_authority"])
    require(flow_machine.get("flow_id") == "recognition_full_flow_models_v1", "full-flow machine authority identity drift")
    require(len(flow_machine.get("stages", [])) == 10, "full-flow authority must contain ten workflow stages")
    require(len(flow_machine.get("model_responsibilities", [])) == 4, "full-flow authority must preserve four responsibility groups")
    require(
        len(full_flow.get("acceptance_scenarios", [])) == len(flow_machine.get("acceptance_scenarios", [])),
        "Design Manager acceptance scenarios must match machine authority",
    )
    require(
        [step.get("title") for step in full_flow.get("workflow_steps", [])]
        == [step.get("title") for step in flow_machine.get("stages", [])],
        "Design Manager workflow stage titles must match machine authority",
    )
    require(
        len(full_flow.get("decision_branches", [])) == len(flow_machine.get("branches", [])),
        "Design Manager decision branches must match machine authority",
    )
    flow_svg_path = root / full_flow["image"]
    require(flow_svg_path.is_file(), "full-flow SVG asset missing")
    flow_svg = flow_svg_path.read_text(encoding="utf-8")
    require("<svg " in flow_svg and "</svg>" in flow_svg and "DET_FISH" in flow_svg and "MODEL_M1" in flow_svg, "full-flow SVG content is incomplete")
    require(guide_view.get("title") == "07 · 首次拍照教学", "legacy 07 guide menu title changed")
    guide_children = {x.get("id"): x for x in guide_view.get("children", [])}
    require(set(guide_children) == set(EXPECTED_GUIDE) | {"resources"}, "P01/P02/P03 and Resources direct children changed")
    resource_view = guide_children["resources"]
    require(resource_view.get("title") == "资源", "guide Resources submenu title changed")
    require(resource_view.get("status") == "DESIGN_ONLY" and resource_view.get("authority_type") == "asset_catalog", "guide Resources must remain a Design Manager-only asset catalog")
    require(resource_view.get("render_mode") == "recognition_guide_resources", "guide Resources renderer is missing")
    require(resource_view.get("resource_ids") == ["p01", "p02", "p03"], "guide Resources must expose the three active Frozen images")
    require(resource_view.get("runtime_visual_authority") is False, "guide Resources cannot claim Android runtime visual authority")

    manifest = load_json(design / GUIDE / "manifest.json")
    nav = load_json(design / GUIDE / "navigation.json")
    require(manifest.get("status") == "FROZEN", "first-use guide manifest is not FROZEN")
    require(manifest.get("runtime_visual_authority") is False, "guide must not claim Android runtime visual authority")
    nav_children = {x.get("id"): x for x in nav.get("children", [])}
    require(nav.get("manager_parent") == "recognition_flow_v1_2", "guide manager parent changed")
    require(set(nav_children) == set(EXPECTED_GUIDE), "navigation.json must retain exactly P01/P02/P03")
    resource_menus = nav.get("resource_menus", [])
    require(len(resource_menus) == 1, "navigation.json must register one separate Design Manager resource menu")
    require(resource_menus[0].get("id") == "resources" and resource_menus[0].get("title") == "资源", "guide resource menu identity changed")
    require(resource_menus[0].get("route") == "#page/recognition_flow_v1_2/hifi/capture_first_use_guide/resources", "guide resource menu route changed")
    require(resource_menus[0].get("manifest") == "manifest.json" and resource_menus[0].get("resource_ids") == ["p01", "p02", "p03"], "guide resource menu must use the existing manifest and frozen pages")
    require(resource_menus[0].get("runtime_visual_authority") is False, "guide resource menu cannot claim runtime visual authority")
    manifest_children = {x.get("id"): x for x in manifest.get("assets", [])}
    require(set(manifest_children) == set(EXPECTED_GUIDE), "guide manifest P01/P02/P03 set changed")
    require(resource_view.get("resource_manifest") == "design/" + (GUIDE / "manifest.json").as_posix(), "guide Resources must use the existing authoritative manifest")

    files: dict[str, dict] = {}
    for asset_id, (width, height, expected_sha) in EXPECTED_GUIDE.items():
        asset = manifest_children[asset_id]
        child = guide_children[asset_id]
        expected_path = f"design/pages/recognition/first_use_camera_guide/frozen/P{asset_id[-2:]}_First_Use_Camera_Guide_Frozen.png"
        require(asset.get("path") == expected_path, f"{asset_id} manifest path drift")
        require(asset.get("sha256") == expected_sha, f"{asset_id} manifest SHA drift")
        require((asset.get("width"), asset.get("height")) == (width, height), f"{asset_id} manifest dimensions drift")
        require(child.get("status") == "FROZEN" and child.get("visual_status") == "FROZEN", f"{asset_id} manager status is not FROZEN")
        require(child.get("visual_authority") == expected_path, f"{asset_id} Registry source path drift")
        require(child.get("visual_authority_sha256") == expected_sha, f"{asset_id} Registry SHA drift")
        require(child.get("page_id") == "recognition.capture.v3_1" and child.get("state_id") == asset_id.upper(), f"{asset_id} Page/State ID missing")
        require(child.get("runtime_status") == "NOT_RUN", f"{asset_id} incorrectly upgrades Runtime status")
        require(nav_children[asset_id].get("visual_authority") == f"frozen/P{asset_id[-2:]}_First_Use_Camera_Guide_Frozen.png", f"{asset_id} navigation source drift")
        files[asset_id] = check_png(root / expected_path, expected_sha, (width, height))

    routes = {
        "00 overview": "#page/recognition_flow_v1_2/hifi/main_journey_overview",
        "guide P01": "#page/recognition_flow_v1_2/hifi/capture_first_use_guide/p01",
        "guide P02": "#page/recognition_flow_v1_2/hifi/capture_first_use_guide/p02",
        "guide P03": "#page/recognition_flow_v1_2/hifi/capture_first_use_guide/p03",
        "guide Resources": "#page/recognition_flow_v1_2/hifi/capture_first_use_guide/resources",
        "master board": "#page/recognition_flow_v1_2/hifi/boundary_state_release_gate/master_board_v1",
        "review state": "#page/recognition_flow_v1_2/hifi/boundary_state_release_gate/new_states_review",
        "capture asset gate": "#page/recognition_flow_v1_2/hifi/boundary_state_release_gate/capture_portrait",
        "permission visual gate": "#page/recognition_flow_v1_2/hifi/boundary_state_release_gate/camera_permission_visual",
        "full flow and model responsibilities": "#page/recognition_flow_v1_2/hifi/recognition_full_flow_models",
        "FishTrace Sites high-fidelity structured prototype": "#page/recognition_flow_v1_2/hifi/fishtrace_sites_web_hifi_workspace",
    }
    for child_id in ("p01", "p02", "p03"):
        child = guide_children[child_id]
        require(child.get("entry_conditions") and child.get("trigger") and child.get("input_contract"), f"{child_id} contract entry fields missing")
        require(child.get("user_actions") and child.get("next_page") and child.get("back_behavior"), f"{child_id} contract actions missing")
        require(child.get("cancel_behavior") and child.get("retake_behavior") and child.get("contract_links"), f"{child_id} cancel/retake/contracts missing")

    capture_manifest = load_json(design / "pages/recognition/capture/HiFi_Freeze_Manifest_V1.json")
    capture_children = {x.get("id"): x for x in boundary.get("children", [])}
    for state in capture_manifest.get("states", []):
        manager_id = "capture_portrait" if state.get("id") == "capture_ready_portrait_horizontal" else "capture_landscape"
        entry = capture_children.get(manager_id)
        require(entry is not None, f"manager capture availability item missing: {manager_id}")
        require(state.get("binary_import_status") == "PENDING", f"unexpected capture import state: {state.get('id')}")
        require(not (root / state["repository_path"]).exists(), f"capture binary path unexpectedly appeared; re-audit before release: {state['repository_path']}")
        require(entry.get("status") == "BLOCKED_INTEGRITY" and entry.get("design_approval_status") == "FROZEN", f"{manager_id} must separate approved freeze and binary integrity")
        require("image" not in entry and "visual_authority" not in entry, f"{manager_id} must not render a fake/missing image")
        require(entry.get("authority_path_available") is False and entry.get("expected_asset_path") == state.get("repository_path"), f"{manager_id} missing path must be plain text")

    permission = capture_children.get("camera_permission_visual")
    require(permission and permission.get("status") == "MISSING", "permission visual must remain MISSING")
    require(not (root / permission.get("expected_asset_path", "")).exists(), "permission PNG source unexpectedly exists; re-audit the correct path before release")
    require("image" not in permission and "visual_authority" not in permission, "permission page must not use a fake image")
    require(any(x.get("path", "").endswith("Camera_Permission_Denied_Visual_Contract_V1.md") for x in permission.get("contract_links", [])), "permission text contract link missing")

    overview_manifest = load_json(design / ASSETS / "Recognition_Main_Journey_Asset_Manifest_V1.json")
    require(overview_manifest.get("status") == "FROZEN_BUSINESS_OVERVIEW", "journey overview approval scope changed")
    require(overview_manifest.get("scope") == "communication_flow_not_runtime_visual_authority", "journey overview scope changed")
    overview_png = overview_manifest["png"]
    overview_path = overview_png["path"]
    files["overview"] = check_png(root / overview_path, EXPECTED_OVERVIEW, (5120, 2880))
    overview_svg = overview_manifest.get("source_svg", {})
    require(overview_svg.get("path", "").endswith("Recognition_Main_Journey_Overview_V1.svg"), "overview SVG path missing from asset manifest")
    files["overview_svg"] = check_sha(root / overview_svg["path"], EXPECTED_OVERVIEW_SVG)
    require(overview.get("status") == "FROZEN" and overview.get("asset_status") == "FROZEN_BUSINESS_OVERVIEW", "overview manager status/scope mismatch")
    require(overview.get("authority_type") == "business_flow_overview" and overview.get("visual_authority") == overview_path, "overview must remain a business overview route")
    require("not a single product page" in overview.get("scope_note", ""), "overview disclaimer missing")
    for link in overview.get("journey_links", []):
        route = link.get("route", "")
        require(route.startswith("#page/"), f"journey link is not a manager Hash route: {route}")
        routes[link.get("label", "journey link")] = route
    require(overview.get("page_id") and overview.get("state_id_scope"), "business overview identity and its non-product State scope must be explicit")
    require(overview.get("visual_authority_sha256") == EXPECTED_OVERVIEW, "overview metadata panel must carry the PNG SHA")

    new_states = load_json(design / ASSETS / "Recognition_New_States_Manifest_V1.json")
    review = capture_children.get("new_states_review")
    require(review and review.get("status") == "PARTIAL", "new states must map review to PARTIAL")
    require(review.get("image") == new_states.get("png", {}).get("path"), "review image path must use the real combined PNG")
    require(review.get("visual_authority_sha256") == EXPECTED_NEW_STATES, "review source SHA-256 drift")
    files["new_states_review"] = check_png(root / review["image"], EXPECTED_NEW_STATES, (2800, 1600))
    review_manifest_svg = new_states.get("svg", {})
    require(review_manifest_svg.get("path", "").endswith("Recognition_New_States_HiFi_V1.svg"), "review SVG path missing from manifest")
    files["new_states_svg"] = check_sha(root / review_manifest_svg["path"], EXPECTED_NEW_STATES_SVG)
    review_states = {x.get("source_status"): x.get("manager_status") for x in review.get("review_states", [])}
    require(review_states.get("SUPERSEDED") == "DEPRECATED", "superseded first-use art must map to DEPRECATED")
    require(review_states.get("ADDITIVE_UI_REVIEW") == "PARTIAL", "additive UI review must map to PARTIAL")
    require(review.get("status") != "FROZEN", "review source must not be promoted to FROZEN")

    board_path = design / ASSETS / "Recognition_Main_Journey_Master_Board_V1.json"
    board = load_json(board_path)
    board_child = capture_children.get("master_board_v1")
    require(board_child and board_child.get("status") == "PARTIAL", "Master Board manager mapping must remain PARTIAL")
    require(board.get("status") == "FROZEN_REVIEW_BOARD" and board.get("standalone_visual_authority") is False, "Master Board source scope changed")
    require(board.get("asset_import_gate", {}).get("status") == "BLOCKED_CONNECTOR_UPLOAD_STEP", "Master Board import gate changed")
    for output in board.get("output_targets", {}).values():
        if isinstance(output, str) and output.startswith("design/"):
            require(not (root / output).exists(), f"Master Board output unexpectedly exists and needs re-review: {output}")
    require("Recognition_Main_Journey_Overview_V1.png" not in json.dumps(board), "overview PNG must not be substituted for the Master Board")

    app = (design / "manager/app.js").read_text(encoding="utf-8")
    css = (design / "manager/styles.css").read_text(encoding="utf-8")
    html = (design / "manager/index.html").read_text(encoding="utf-8")
    guide_workspace_path = design / "manager/first_use_guide_workspace.js"
    require(guide_workspace_path.is_file(), "first-use guide interactive workspace module is missing")
    guide_workspace = guide_workspace_path.read_text(encoding="utf-8")
    swos_js_path = design / "manager/swos_static_visual.js"
    require(swos_js_path.is_file(), "SWOS static visual Design Manager module is missing")
    swos_js = swos_js_path.read_text(encoding="utf-8")
    require("recognitionGuideResourcesCanvas" in app and "FirstUseGuideWorkspace?.mount" in app, "guide Resources workspace hook is missing")
    require("guide-resource-actions" in guide_workspace and "CANDIDATE_NEEDS_APPROVAL" in guide_workspace and "data-guide-layer" in guide_workspace, "guide Resources preview/download and structured layer workspace are missing")
    require("recognitionFullFlowModelsCanvas" in app and 'view.render_mode === "recognition_full_flow_models"' in app, "full-flow/model Design Manager renderer is missing")
    require('view.render_mode === "swos_static_visual_workspace"' in app and "SWOSStaticVisual?.mount" in app, "SWOS static workspace hook is missing")
    sites_workspace_html = design / "manager/fishtrace_sites_web_workspace.html"
    sites_workspace_css = design / "manager/fishtrace_sites_web_workspace.css"
    sites_workspace_js = design / "manager/fishtrace_sites_web_workspace.js"
    require(sites_workspace_html.is_file() and sites_workspace_css.is_file() and sites_workspace_js.is_file(), "FishTrace Sites high-fidelity workspace HTML/CSS/JS is missing")
    require('view.render_mode === "fishtrace_sites_web_hifi_workspace"' in app and "fishtrace_sites_web_workspace.html" in app, "FishTrace Sites prototype iframe renderer is missing")
    require("FISHTRACE-SITES-WEB-HIFI-V1" in sites_workspace_html.read_text(encoding="utf-8") and "fishtrace_sites_web_workspace.css" in sites_workspace_html.read_text(encoding="utf-8") and "fishtrace_sites_web_workspace.js" in sites_workspace_html.read_text(encoding="utf-8"), "FishTrace Sites workspace asset references are missing")
    require("layer_tree_by_state" in sites_workspace_js.read_text(encoding="utf-8") or "FishTrace_Sites_Web_Structured_Prototype_V1.json" in sites_workspace_js.read_text(encoding="utf-8"), "FishTrace Sites structured layer contract is not loaded")

    water_lab_html = sites_workspace_html.read_text(encoding="utf-8")
    water_lab_js_path = design / "manager/fishtrace_canvas_water_lab.js"
    water_lab_css_path = design / "manager/fishtrace_canvas_water_lab.css"
    water_lab_contract_path = design / "pages/recognition/web/candidates/FISHTRACE_SWOS_CANVAS_WATER_LAB_V1_CANDIDATE.json"
    require(water_lab_js_path.is_file() and water_lab_css_path.is_file() and water_lab_contract_path.is_file(), "FishTrace Canvas Water Lab module, styles or candidate contract are missing")
    source_root = Path(__file__).resolve().parents[1] if built else root
    water_lab_browser_test_path = source_root / "scripts/verify_design_manager_browser.mjs"
    require(water_lab_browser_test_path.is_file(), "FishTrace Canvas Water Lab browser verifier is missing")
    water_lab_browser_test = water_lab_browser_test_path.read_text(encoding="utf-8")
    water_lab_js = water_lab_js_path.read_text(encoding="utf-8")
    water_lab_css = water_lab_css_path.read_text(encoding="utf-8")
    water_lab = load_json(water_lab_contract_path)
    require('data-water-view="canvas-water"' in water_lab_html and 'data-testid="canvas-water-lab"' in water_lab_html and 'id="water-overlay"' in water_lab_html, "FishTrace Canvas Water Lab is not registered inside the existing workspace")
    require("fishtrace_canvas_water_lab.js" in water_lab_html and "fishtrace_canvas_water_lab.css" in water_lab_html, "FishTrace Canvas Water Lab assets are not loaded by the existing workspace")
    require("CanvasWaterRenderer" in water_lab_js and "WaterCausticsScene" in water_lab_js and "WaterTimelineController" in water_lab_js, "Canvas Water Lab module boundaries are incomplete")
    require("workspaceViewButton" in sites_workspace_js.read_text(encoding="utf-8") and "clearPhotoTransition()" in sites_workspace_js.read_text(encoding="utf-8"), "Entering Canvas Water Lab must stop the existing photo transition overlay")
    require("requestAnimationFrame" in water_lab_js and "createRadialGradient" in water_lab_js and "devicePixelRatio" in water_lab_js and "objectFit" in water_lab_js, "Canvas Water Lab deterministic 2D renderer geometry/time handling is missing")
    require(water_lab.get("contract_id") == "FISHTRACE_SWOS_CANVAS_WATER_LAB_V1_CANDIDATE" and water_lab.get("status") == "CANDIDATE_NOT_VISUALLY_APPROVED", "Canvas Water Lab must remain an unapproved candidate contract")
    require(water_lab.get("asset",{}).get("sha256") == "6e955087108f7463eca2dbac489699a4942028ebe1b3d456ddfca523286385ef" and [water_lab.get("asset",{}).get("width"),water_lab.get("asset",{}).get("height")] == [1152,1536], "Canvas Water Lab fixed asset identity or dimensions changed")
    require(water_lab.get("task_id") == "FISHTRACE_SWOS_A_LINE_CANVAS_WATER_FIDELITY_V1", "Canvas Water Lab candidate task ID is not the current A-line task")
    require([(frame.get("id"),frame.get("ms"),frame.get("intensity_percent")) for frame in water_lab.get("timeline",{}).get("frames",[])] == [("P0",0,0),("P1",350,8),("P2",950,42),("P3",1200,68),("P4",1550,58),("P5",2050,28),("P6",2600,8),("P7",2800,0)], "Canvas Water Lab must reuse exact A-line P0-P7 times and caustic channel values")
    water_field = water_lab.get("water_field",{})
    regions = water_field.get("regions",{})
    expected_a = [
        {"id":"A1","points":[[0,.66],[0,.95],[.38,.95],[.46,.79],[.33,.60],[.10,.58]]},
        {"id":"A2","points":[[.38,.61],[.57,.57],[.98,.59],[.98,.93],[.52,.93],[.39,.77]]},
    ]
    expected_b = {"id":"B1","points":[[.06,.44],[.24,.40],[.47,.45],[.80,.43],[.95,.50],[.92,.64],[.57,.69],[.23,.65],[.08,.56]]}
    expected_c = {"id":"C1","points":[[.10,.34],[.92,.34],[.92,.47],[.10,.47]]}
    def normalized_polygon_valid(polygon):
        points = polygon.get("points",[])
        return len(points) >= 3 and all(
            isinstance(point,list) and len(point) == 2
            and isinstance(point[0],(int,float)) and isinstance(point[1],(int,float))
            and 0 <= point[0] <= 1 and 0 <= point[1] <= 1
            for point in points
        )
    a_polygons = regions.get("A",{}).get("polygons",[])
    b_polygon = regions.get("B",{}).get("polygon",{})
    c_polygon = regions.get("C",{}).get("polygon",{})
    region_geometry_valid = (
        a_polygons == expected_a and b_polygon == expected_b and c_polygon == expected_c
        and all(normalized_polygon_valid(polygon) for polygon in a_polygons+[b_polygon,c_polygon])
        and regions.get("A",{}).get("strength_coefficient") == 1
        and regions.get("B",{}).get("strength_coefficient") == .48
        and regions.get("C",{}).get("strength_coefficient") == .16
        and regions.get("C",{}).get("clear_caustic_texture") is True
        and regions.get("D",{}).get("no_draw") is True
        and water_field.get("coordinate_system") == "source_image_normalized_top_left_0_1"
        and water_field.get("source_width") == 1152 and water_field.get("source_height") == 1536
        and water_field.get("asset_sha256") == water_lab.get("asset",{}).get("sha256")
        and water_field.get("edge_fade_width_w",0) > 0
    )
    water_rendering = water_lab.get("rendering",{})
    ambient_spec = water_rendering.get("ambient",{})
    large_sample = ambient_spec.get("large_radius_sampling_w",[])
    medium_sample = ambient_spec.get("medium_radius_sampling_w",[])
    large_bounds = ambient_spec.get("large_radius_w",[])
    medium_bounds = ambient_spec.get("medium_radius_w",[])
    water_motion = water_lab.get("motion",{})
    timeline_frames = water_lab.get("timeline",{}).get("frames",[])
    expected_channels = [
        {"ambient":0,"caustic":0,"highlight":0},
        {"ambient":.18,"caustic":.08,"highlight":0},
        {"ambient":.40,"caustic":.42,"highlight":.16},
        {"ambient":.50,"caustic":.68,"highlight":.34},
        {"ambient":.44,"caustic":.58,"highlight":.28},
        {"ambient":.22,"caustic":.28,"highlight":.10},
        {"ambient":.08,"caustic":.08,"highlight":.02},
        {"ambient":0,"caustic":0,"highlight":0},
    ]
    rendering_valid = (
        water_rendering.get("backend") == "Canvas 2D"
        and water_rendering.get("ambient",{}).get("count") == 5
        and water_rendering.get("ambient",{}).get("large_count") == 3
        and water_rendering.get("ambient",{}).get("medium_count") == 2
        and water_rendering.get("ambient",{}).get("field_opacity_response_multiplier") == .68
        and water_rendering.get("ambient",{}).get("large_radius_sampling_w") == [.22,.26]
        and water_rendering.get("ambient",{}).get("medium_radius_sampling_w") == [.12,.14]
        and water_rendering.get("ambient",{}).get("aspect_ratio") == [.68,.83]
        and water_rendering.get("ambient",{}).get("radial_gradient_opacity_stops") == [1,.92,.72]
        and water_rendering.get("ambient",{}).get("p3_single_field_opacity_max") == .10
        and len(large_sample) == len(large_bounds) == 2 and large_bounds[0] <= large_sample[0] <= large_sample[1] <= large_bounds[1]
        and len(medium_sample) == len(medium_bounds) == 2 and medium_bounds[0] <= medium_sample[0] <= medium_sample[1] <= medium_bounds[1]
        and water_rendering.get("caustics",{}).get("geometry_model") == "a_line_organic_face_patches_v2_grouped"
        and water_rendering.get("caustics",{}).get("patch_count") == 18
        and water_rendering.get("caustics",{}).get("groups") == {"A":4,"B":4,"C":4,"D":3,"E":3}
        and water_rendering.get("caustics",{}).get("realized_zone_patch_count") == {"A":13,"B":4,"C":1}
        and water_rendering.get("caustics",{}).get("patch_sampling",{}).get("length_w") == [.16,.18]
        and water_rendering.get("caustics",{}).get("patch_sampling",{}).get("short_axis_w") == [.052,.065]
        and water_rendering.get("caustics",{}).get("patch_sampling",{}).get("placement") == "deterministic_spread_group_anchors_with_seeded_local_offsets"
        and water_rendering.get("caustics",{}).get("patch_sampling",{}).get("group_cluster_radius_w") == [.025,.07]
        and water_rendering.get("caustics",{}).get("field_opacity_response_multiplier") == .42
        and water_rendering.get("caustics",{}).get("gradient_half_extent_multiplier") == .9
        and water_rendering.get("caustics",{}).get("primary_opacity_max_p3") == .22
 