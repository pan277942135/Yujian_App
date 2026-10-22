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
    require(overview and boundary and guide_view and full_flow, "overview/boundary/guide/full-flow view missing")
    require(views[0].get("id") == "main_journey_overview", "00 journey overview is not the first recognition route")
    require(
        len(views) >= 2 and views[-2].get("id") == "boundary_state_release_gate"
        and views[-1].get("id") == "recognition_full_flow_models",
        "08 boundary gate must remain in place and 09 full-flow/model route must be appended",
    )
    require(full_flow.get("title") == "09 · 识别全流程-和模型", "09 full-flow/model menu title drift")
    require(full_flow.get("status") == "DESIGN_ONLY" and full_flow.get("render_mode") == "recognition_full_flow_models", "09 full-flow/model must remain DESIGN_ONLY with its renderer")
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
    require("recognitionGuideResourcesCanvas" in app and "guide-resource-actions" in app, "guide Resources preview/download renderer is missing")
    require("recognitionFullFlowModelsCanvas" in app and 'view.render_mode === "recognition_full_flow_models"' in app, "full-flow/model Design Manager renderer is missing")
    require("guide-resource-grid" in css and "guide-resource-metadata" in css, "guide Resources responsive gallery styles are missing")
    for token in (
        "history.pushState", 'window.addEventListener("popstate", selectFromHash)',
        "renderInvalidManagerRoute", "openHifiImageLightbox", '[data-zoom="fit"]',
        "selectPage(id, null, hifiId, childId, false)", "selectShared(id, variantId, false)",
        "hifiContractMetadataHtml(child || view, view)"
    ):
        require(token in app, f"manager implementation missing: {token}")
    require('id="hifiViewContractDetails"' in html, "Page/State contract metadata mount missing")
    require("item.visual_authority_sha256 || item.sha256" in app, "metadata panel must display the overview asset SHA")
    require("hifi-lightbox-stage img" in css and "object-fit:contain" in css, "Hi-Fi image must preserve its ratio in the lightbox")
    for breakpoint in ("@media(max-width:980px)", "@media(max-width:760px)", "@media(max-width:420px)"):
        require(breakpoint in css.replace(" ", ""), f"responsive viewer breakpoint missing: {breakpoint}")

    for view in views:
        link_groups = [view.get("contract_links", [])]
        link_groups.extend(child.get("contract_links", []) for child in view.get("children", []))
        for link in (item for group in link_groups for item in group):
            link_path = link.get("path")
            if link_path and link_path.startswith("design/"):
                require((root / link_path).is_file(), f"registered design contract link target missing: {link_path}")

    page_registry = load_json(design / "system_v3/08_Page_Contracts/Page_Registry_V3_1.json")
    page_by_id = {p.get("page_id"): p for p in page_registry.get("pages", [])}
    processing = page_by_id.get("recognition.processing.v3_1")
    require(processing and processing.get("status") == "FROZEN", "Processing Page Contract V3.1 not registered FROZEN")
    state_path = processing.get("state_machine")
    state_machine = load_json(root / state_path)
    states = state_machine.get("states", [])
    require([s.get("id") for s in states] == ["CAPTURED", "DETECTING", "OUTLINE", "CLASSIFYING", "RESULT_READY"], "effective V3.1 five-state sequence changed")
    require("sole effective Recognition Processing timeline/state authority" in (root / processing["contract"]).read_text(encoding="utf-8"), "Page Contract must identify the canonical state timing authority")
    v12_motion = design / "pages/recognition/processing/motion/Recognition_Processing_Motion_Spec_V1_2.md"
    motion_text = v12_motion.read_text(encoding="utf-8")
    manager_contract = (design / "manager/RECOGNITION_HIFI_PRESENTATION_CONTRACT_V1.md").read_text(encoding="utf-8")
    require("900ms" in motion_text and "600ms" in motion_text and "1250ms" in motion_text, "unchanged V1.2 motion source cannot be confirmed")
    require(all(x in manager_contract for x in ("220ms", "320ms", "350ms", "250ms", "900 / 600 / 1250 / 200ms", "sole effective timeline")), "motion authority resolution must be traceable without editing frozen thresholds")

    if built:
        build_path = design / "manager/build.json"
        build = load_json(build_path)
        require(expected_build is None or build.get("sha") == expected_build, f"artifact build SHA mismatch: {build.get('sha')}")
        require("__DESIGN_MANAGER_BUILD__" not in html and "__DESIGN_MANAGER_BUILD_SHORT__" not in html, "Pages artifact has unresolved build markers")
        for file in ("manager/app.js", "manager/styles.css", "registry/experience_registry_v1.json"):
            require((design / file).is_file(), f"Pages artifact missing {file}")

    # Negative tests exercise the same byte gate used for actual assets.
    p01_info = files["p01"]
    source_path = root / p01_info["path"]
    source_bytes = source_path.read_bytes()
    with tempfile.TemporaryDirectory(prefix="dm-hifi-negative-") as tmp:
        temp_png = Path(tmp) / "p01.png"
        temp_png.write_bytes(source_bytes)
        check_png(temp_png, EXPECTED_GUIDE["p01"][2], (941, 1671))
        temp_png.unlink()
        expect_failure(lambda: check_png(temp_png, EXPECTED_GUIDE["p01"][2], (941, 1671)), "missing Frozen PNG")
        temp_png.write_bytes(source_bytes)
        changed = bytearray(source_bytes)
        changed[-1] ^= 1
        temp_png.write_bytes(changed)
        expect_failure(lambda: check_png(temp_png, EXPECTED_GUIDE["p01"][2], (941, 1671)), "modified Frozen PNG SHA")

    return [
        "DM-T01 P01-P03 SHA/dimensions",
        "DM-T01A overview and review SVG SHA",
        "DM-T02 Registry and guide navigation",
        "DM-T03 Frozen asset presence and negative integrity cases",
        "DM-T04 Menu and stable existing child IDs",
        "DM-T05 Direct Hash routes and invalid route guard",
        "DM-T06 Original-ratio preview, zoom, Fit Window, original link",
        "DM-T07 REVIEW/MISSING/BLOCKED status semantics",
        "DM-T08 responsive CSS width coverage",
        "DM-T09 legacy routes and existing registry items retained",
        "DM-T10 Pages artifact resource completeness" if built else "DM-T10 source resource completeness",
        "DM-T12 authority and governance references"
    ]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--site", type=Path, default=None)
    parser.add_argument("--expected-build", default=None)
    args = parser.parse_args()
    repo_root = Path(__file__).resolve().parents[1]
    try:
        results = validate_tree(repo_root)
        print("PASS: " + " | ".join(results))
        if args.site is not None:
            results = validate_tree(args.site.resolve(), built=True, expected_build=args.expected_build)
            print("PASS: Pages artifact | " + " | ".join(results))
    except ValidationError as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
