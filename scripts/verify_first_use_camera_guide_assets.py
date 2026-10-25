#!/usr/bin/env python3
"""Verify frozen authorities, candidate photo bytes, predev geometry and registry links."""

from __future__ import annotations

import hashlib
import json
import math
import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GUIDE = ROOT / "design/pages/recognition/first_use_camera_guide"
TASK = "YUJIAN_FIRST_USE_GUIDE_PREDEV_CONTRACT_ASSETS_RESPONSIVE_FREEZE_V1"
EXPECTED = {
    "p01": {
        "path": "frozen/P01_First_Use_Camera_Guide_Frozen.png", "source_filename": "湖畔夕照拍照识鱼指南.png",
        "width": 941, "height": 1671, "bytes": 1988159,
        "sha256": "dc731cca5470ef2154bb06172b9836e823ea780b0833f04e34caaf883e465c50",
    },
    "p02": {
        "path": "frozen/P02_First_Use_Camera_Guide_Frozen.png", "source_filename": "湖畔竖拍识鱼指南.png",
        "width": 941, "height": 1671, "bytes": 2042852,
        "sha256": "fb092235fd043a42aa8250aba9dfdb91a78e96549dd5a63f8e89cf7433685f8f",
    },
    "p03": {
        "path": "frozen/P03_First_Use_Camera_Guide_Frozen.png", "source_filename": "湖畔横屏拍摄指南(1).png",
        "width": 941, "height": 1672, "bytes": 2001322,
        "sha256": "1a3008483ac5708a0649501cf2aef6c799d47b95bcc2c1b1272fa64e68f7f9b3",
    },
}
ROLES = {"correct_hero", "fish_head_out_of_frame", "fish_too_small", "excessive_occlusion"}
PAGES = {"p01", "p02", "p03"}


def fail(message: str) -> None:
    print(f"FAIL: {message}", file=sys.stderr)
    raise SystemExit(1)


def load(path: Path) -> dict:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        fail(f"cannot load {path.relative_to(ROOT)}: {exc}")


def image_identity(path: Path) -> dict:
    if not path.is_file():
        fail(f"missing image file: {path.relative_to(ROOT)}")
    raw = path.read_bytes()
    if len(raw) < 24 or raw[:8] != b"\x89PNG\r\n\x1a\n" or raw[12:16] != b"IHDR":
        fail(f"invalid PNG signature/IHDR: {path.relative_to(ROOT)}")
    width, height = struct.unpack(">II", raw[16:24])
    return {"width": width, "height": height, "bytes": len(raw), "sha256": hashlib.sha256(raw).hexdigest()}


def find_resources_node(value):
    if isinstance(value, dict):
        if value.get("id") == "resources" and value.get("render_mode") == "recognition_guide_resources":
            return value
        for child in value.values():
            found = find_resources_node(child)
            if found:
                return found
    elif isinstance(value, list):
        for child in value:
            found = find_resources_node(child)
            if found:
                return found
    return None


def verify_frozen_assets() -> None:
    manifest = load(GUIDE / "manifest.json")
    report = load(GUIDE / "asset_import_report.json")
    navigation = load(GUIDE / "navigation.json")
    import_status = manifest.get("repository_binary_import", {})
    expected_paths = [EXPECTED[page]["path"] for page in ("p01", "p02", "p03")]
    if import_status.get("status") != "PASS":
        fail("frozen page repository binary import status changed")
    if [Path(path).as_posix() for path in import_status.get("required_paths", [])] != [f"design/pages/recognition/first_use_camera_guide/{path}" for path in expected_paths]:
        fail("frozen manifest required_paths changed")
    if report.get("schema_version") != "1.1" or report.get("task_id") != "RECOGNITION-FIRST-USE-GUIDE-V1":
        fail("legacy Frozen image import report schema/task id changed")
    if report.get("status") != "PASS" or report.get("binary_import_status") != "PASS":
        fail("legacy Frozen image import report must remain PASS")
    if [item.get("id") for item in manifest.get("assets", [])] != ["p01", "p02", "p03"]:
        fail("Frozen manifest must retain the P01/P02/P03 order")
    if [item.get("id") for item in navigation.get("children", [])] != ["p01", "p02", "p03"]:
        fail("navigation children must remain the three-page V1 sequence")
    menus = navigation.get("resource_menus", [])
    if len(menus) != 1 or menus[0].get("route") != "#page/recognition_flow_v1_2/hifi/capture_first_use_guide/resources":
        fail("existing Design Manager Resources hash route changed")
    if menus[0].get("resource_ids") != ["p01", "p02", "p03"] or menus[0].get("runtime_visual_authority") is not False:
        fail("Frozen page resources must remain outside Android runtime authority")
    if menus[0].get("frontend_asset_manifest") != "frontend_assets_manifest.json":
        fail("navigation must continue to link the separate front-end asset manifest")
    if menus[0].get("structured_page_manifest") != "structured_pages.json":
        fail("navigation must link the structured page contract")

    report_assets = {item.get("id"): item for item in report.get("assets", [])}
    manifest_assets = {item.get("id"): item for item in manifest.get("assets", [])}
    for page_id, expected in EXPECTED.items():
        path = GUIDE / expected["path"]
        actual = image_identity(path)
        for key in ("width", "height", "bytes", "sha256"):
            if actual[key] != expected[key]:
                fail(f"Frozen {page_id} {key} changed: {actual[key]!r} != {expected[key]!r}")
            if manifest_assets.get(page_id, {}).get(key) != expected[key]:
                fail(f"Frozen manifest {page_id} {key} changed")
            if report_assets.get(page_id, {}).get(key) != expected[key]:
                fail(f"Frozen import report {page_id} {key} changed")
        if manifest_assets[page_id].get("path") != f"design/pages/recognition/first_use_camera_guide/{expected['path']}":
            fail(f"Frozen manifest {page_id} source path changed")
    print("PASS: the three Frozen page images preserve exact registered byte lengths, dimensions, hashes, sequence, and hash route.")


def verify_candidate_manifest() -> dict:
    manifest = load(GUIDE / "frontend_assets_manifest.json")
    if str(manifest.get("schema_version")) not in {"1.1", "1.0"} or manifest.get("task_id") != TASK:
        fail("front-end asset manifest schema/task ID mismatch")
    if manifest.get("status") != "GENERATED_CANDIDATES_AVAILABLE; ORIGINAL_PHOTOS_AND_APPROVAL_BLOCKED":
        fail("asset manifest must clearly state generated candidate and missing originals")
    assets = manifest.get("assets", [])
    expected_slots = {f"{page}_{role}" for page in PAGES for role in ROLES}
    if len(assets) != 12 or {item.get("id") for item in assets} != expected_slots:
        fail("manifest must enumerate exactly 12 page/role bindings")
    if len({item.get("id") for item in assets}) != 12:
        fail("asset slot IDs are not unique")

    by_slot = {}
    by_path = {}
    for asset in assets:
        slot_id = asset["id"]
        page_id, role = slot_id.split("_", 1)
        if page_id not in PAGES or role not in ROLES or asset.get("role") != role:
            fail(f"invalid page/role mapping: {slot_id}")
        expected_orientation = "vertical" if page_id == "p02" else "horizontal"
        if asset.get("fish_orientation") != expected_orientation:
            fail(f"wrong fish orientation for {slot_id}: {asset.get('fish_orientation')}")
        pages = ["p01", "p03"] if expected_orientation == "horizontal" else ["p02"]
        if asset.get("page_refs") != pages:
            fail(f"{slot_id} page_refs must reflect shared physical file bindings {pages}")
        if asset.get("authority_state") != "CANDIDATE_NEEDS_APPROVAL" or asset.get("candidate_status") != "CANDIDATE_NEEDS_APPROVAL":
            fail(f"generated candidate incorrectly promoted: {slot_id}")
        if asset.get("original_source_status") != "ASSET_SOURCE_BLOCKED" or asset.get("original_source_found") is not False:
            fail(f"original source state must remain blocked for {slot_id}")
        if asset.get("approved_for_frontend") is not False or asset.get("approved_for_runtime") is not False:
            fail(f"unapproved photo was marked as a production/runtime asset: {slot_id}")
        if asset.get("source_type") != "GENERATED_CANDIDATE" or asset.get("candidate_type") != "GENERATED_CANDIDATE":
            fail(f"candidate provenance missing for {slot_id}")
        if "NOT_CONFIRMED" not in asset.get("license_status", ""):
            fail(f"unresolved source license status is missing for {slot_id}")
        if not asset.get("privacy_status") or not asset.get("semantic_crop") or not asset.get("focal_point_normalized"):
            fail(f"privacy/crop/focal-point metadata missing for {slot_id}")
        if asset.get("fit") != "contain":
            fail(f"photo binding must preserve the complete asset with contain: {slot_id}")
        path = asset.get("source_path", "")
        if not path.startswith("design/pages/recognition/first_use_camera_guide/assets/candidates/"):
            fail(f"candidate file outside independent candidate library: {slot_id}")
        actual = image_identity(ROOT / path)
        for key in ("width", "height", "bytes", "sha256"):
            if asset.get(key) != actual[key]:
                fail(f"candidate source identity mismatch for {slot_id}:{key}")
        if asset.get("mime_type") != "image/png" or len(asset.get("sha256", "")) != 64:
            fail(f"candidate MIME/SHA metadata invalid for {slot_id}")
        required_dimensions = (1086, 1448) if expected_orientation == "vertical" else (1448, 1086)
        if (actual["width"], actual["height"]) != required_dimensions:
            fail(f"candidate dimensions/orientation mismatch for {slot_id}: {(actual['width'], actual['height'])}")
        by_slot[slot_id] = asset
        by_path[path] = asset

    if len(by_path) != 8:
        fail(f"12 slots must reference eight unique candidate source files, found {len(by_path)}")
    for role in ROLES:
        if by_slot[f"p01_{role}"]["source_path"] != by_slot[f"p03_{role}"]["source_path"]:
            fail(f"P01/P03 must explicitly share the same horizontal candidate for {role}")
        if by_slot[f"p02_{role}"]["source_path"] == by_slot[f"p01_{role}"]["source_path"]:
            fail(f"P02 vertical example must not reuse a horizontal source for {role}")
    reference_count = 0
    for asset in assets:
        reference = asset.get("reference_package_asset")
        if asset["role"] == "correct_hero":
            if reference:
                fail(f"correct hero cannot inherit an error-example archive crop: {asset['id']}")
        else:
            if not reference or reference.get("authority_state") != "REFERENCE_ONLY":
                fail(f"existing archive crop must remain REFERENCE_ONLY: {asset['id']}")
            if reference.get("extraction_status") != "ARCHIVE_PACKAGE_ONLY_NOT_MATERIALIZED_AS_STANDALONE_REPO_FILE":
                fail(f"archive reference file was falsely presented as independently available: {asset['id']}")
            reference_count += 1
    counts = manifest.get("counts", {})
    if counts.get("required_slots") != 12 or counts.get("candidate_slot_mappings") != 12:
        fail("asset manifest counts do not reflect all 12 candidate bindings")
    if counts.get("approved_independent_original_slots") != 0 or counts.get("blocked_original_source_slots") != 12:
        fail("source original count must remain zero approved / 12 blocked")
    if counts.get("unique_candidate_files") != 8 or counts.get("reference_only_archived_error_crops") != 9 or reference_count != 9:
        fail("resource counts must remain eight generated files and nine archive-only crops")
    if "Figma account connection confirmed" not in manifest.get("source_search", {}).get("figma_originals", ""):
        fail("Figma inventory note must accurately describe the connected account/current-file limitation")
    return manifest


def verify_geometry_and_structure() -> None:
    geometry = load(GUIDE / "geometry_measurements.json")
    structure = load(GUIDE / "structured_pages.json")
    if geometry.get("task_id") != TASK or set(geometry.get("pages", {})) != PAGES:
        fail("geometry report must be keyed by the three stable page IDs")
    if structure.get("task_id") != TASK or [page.get("id") for page in structure.get("pages", [])] != ["p01", "p02", "p03"]:
        fail("structured page order/task ID changed")
    shared_ids = [item.get("id") for item in structure.get("shared_components", [])]
    if len(shared_ids) != len(set(shared_ids)):
        fail("shared component IDs must be unique")
    for page in structure["pages"]:
        page_id = page["id"]
        measurements = geometry["pages"][page_id]
        if measurements.get("source_sha256") != EXPECTED[page_id]["sha256"]:
            fail(f"geometry measurements do not bind to the Frozen {page_id} source SHA")
        width = measurements["source_dimensions_px"]["width"]
        height = measurements["source_dimensions_px"]["height"]
        if page.get("outer_page_orientation") != "responsive; portrait frozen reference artboard":
            fail(f"{page_id} outer page orientation must remain viewport-responsive")
        if page.get("outer_viewport_rotation") != "not prescribed by frozen V1; P03 must not imply landscape outer UI":
            fail(f"{page_id} must not claim a frozen outer viewport rotation")
        if page.get("capture_phone_orientation") != ("landscape" if page_id == "p03" else "portrait"):
            fail(f"{page_id} capture direction is inconsistent with the V1 guide contract")
        if len(page.get("nodes", [])) != 25:
            fail(f"{page_id} must keep 25 stable semantic layers")
        nodes = page["nodes"]
        by_id = {node.get("id"): node for node in nodes}
        if len(by_id) != len(nodes):
            fail(f"duplicate layer ID on {page_id}")
        root_id = f"guide.page.{page_id}"
        if root_id not in by_id:
            fail(f"missing page root layer on {page_id}")
        for node in nodes:
            node_id = node["id"]
            parent_id = node.get("parent_id")
            if parent_id and parent_id not in by_id:
                fail(f"unknown parent {parent_id} for {page_id}:{node_id}")
            for child_id in node.get("children", []):
                if child_id not in by_id or by_id[child_id].get("parent_id") != node_id:
                    fail(f"parent/child relationship mismatch at {page_id}:{node_id}->{child_id}")
            if node.get("z_index_status") != "CANDIDATE_FROM_FLATTENED_VISUAL_ORDER; ORIGINAL_DOM_Z_INDEX_NOT_RECOVERABLE":
                fail(f"original z-index must not be fabricated: {page_id}:{node_id}")
            if node_id == root_id:
                expected_box = {"x": 0, "y": 0, "width": width, "height": height}
                actual_box = node.get("geometry_px")
            else:
                record = measurements.get("components", {}).get(node_id)
                if record is None:
                    fail(f"missing pixel measurement record for {page_id}:{node_id}")
                expected_box = record.get("bbox_px")
                actual_box = node.get("geometry_px")
                if node.get("geometry_status") != record.get("measurement_status"):
                    fail(f"measurement status mismatch for {page_id}:{node_id}")
            if actual_box != expected_box:
                fail(f"measured xywh differs from geometry evidence for {page_id}:{node_id}: {actual_box} != {expected_box}")
            normalized = node.get("geometry_normalized")
            if actual_box:
                expected_normalized = {key: round(actual_box[key] / span, 6) for key, span in (("x", width), ("width", width), ("y", height), ("height", height))}
                if normalized != expected_normalized:
                    fail(f"normalized geometry mismatch for {page_id}:{node_id}")
                if actual_box["x"] < 0 or actual_box["y"] < 0 or actual_box["x"] + actual_box["width"] > width or actual_box["y"] + actual_box["height"] > height:
                    fail(f"measured bounds leave the source artboard for {page_id}:{node_id}")
            elif node.get("geometry_status") != "NOT_VISIBLE_ON_THIS_PAGE":
                fail(f"unmeasured visible layer needs an explicit non-visible/NOT_RUN reason: {page_id}:{node_id}")
            if node_id in {"guide.background", "guide.overlay", "guide.hero"} and node.get("layer_separation_status") != "COMPOSITED_ONLY_SOURCE_LAYER_NOT_RECOVERABLE":
                fail(f"flattened photographic layer must be explicitly COMPOSITED_ONLY: {page_id}:{node_id}")
        if by_id.get("guide.previous_action", {}).get("visible_condition") != "P02_OR_P03_ONLY":
            fail(f"previous-action visibility changed on {page_id}")
        if page_id == "p03" and page.get("capture_phone_orientation") == "landscape" and page.get("outer_page_orientation").startswith("landscape"):
            fail("P03 capture-phone orientation must not force the outer UI to landscape")
    if structure.get("component_contract", {}).get("exact_pixel_geometry_status") != "MEASURED_FROM_FROZEN_RASTER":
        fail("pixel geometry must be tied to raster evidence")
    print("PASS: 75 semantic layers and exact/native normalized geometry match the registered Frozen pixels; composited source style and interaction limits stay explicit.")


def verify_responsive_and_readiness() -> None:
    responsive = load(GUIDE / "responsive_contract.json")
    readiness = load(GUIDE / "PREDEV_READINESS_MATRIX.json")
    freeze = (GUIDE / "Freeze_Decision.md").read_text(encoding="utf-8")
    if responsive.get("task_id") != TASK or readiness.get("task_id") != TASK:
        fail("responsive/readiness contracts task ID mismatch")
    allowed = set(readiness.get("status_vocabulary", []))
    rows = readiness.get("rows", [])
    if not rows or any(row.get("status") not in allowed for row in rows):
        fail("readiness rows must use the required explicit state vocabulary")
    required_states = {
        "approved_independent_original_photos": "BLOCKED",
        "generated_independent_candidate_photos": "CANDIDATE_PENDING_REVIEW",
        "flattened_source_style_and_hit_targets": "BLOCKED",
        "android_runtime_device_acceptance": "NOT_RUN",
    }
    row_map = {row.get("id"): row for row in rows}
    for row_id, state in required_states.items():
        if row_map.get(row_id, {}).get("status") != state:
            fail(f"readiness status mismatch for {row_id}: expected {state}")
    if readiness.get("overall_status") != "PARTIAL_READY; ORIGINAL_PHOTO_APPROVAL_AND_NATIVE_RUNTIME_BLOCKED":
        fail("full readiness cannot be claimed while source photo approval is blocked")
    profiles = responsive.get("viewport_matrix", [])
    required_profiles = {(320,480),(360,640),(390,844),(412,915),(430,932),(480,960),(640,360),(844,390),(915,412)}
    if {(item.get("width_dp"),item.get("height_dp")) for item in profiles} != required_profiles or len(profiles) != 9:
        fail("responsive viewport matrix must contain all nine portrait/landscape/480dp-width profiles")
    if responsive.get("font_scales") != [1.0, 1.3]:
        fail("responsive font scale matrix must include 1.0x and 1.3x")
    tuning = responsive.get("candidate_tuning_values", {})
    if tuning.get("status") != "CANDIDATE_PENDING_REVIEW":
        fail("new visual tuning values must remain pending review")
    if tuning.get("three_column_min_safe_width_dp") != 384 or tuning.get("three_column_min_content_width_dp") != 352:
        fail("responsive 3-column breakpoint must be derived from 3×112dp cards + 2×8dp gaps + 2×16dp padding")
    if responsive.get("composition", {}).get("hero", {}).get("fit") != "contain" or responsive.get("composition", {}).get("actions", {}).get("target_min_dp") != 48:
        fail("responsive hero containment/48dp action invariants missing")
    if "landscape capture-phone" not in responsive.get("coordinate_and_unit_contract", {}).get("p03_capture_direction", "") or "no page-specific forced rotation" not in responsive.get("coordinate_and_unit_contract", {}).get("outer_viewport_orientation", ""):
        fail("P03 outer viewport vs camera capture direction must be explicitly separated")
    if "ALL_ASSETS_FROZEN" not in freeze or "are **not** PASS" not in freeze or "CANDIDATE_NEEDS_APPROVAL" not in freeze:
        fail("Freeze Decision must explicitly state photo candidates and prevent full-ready false PASS")
    print("PASS: responsive matrix, viewport/inset/font contract, explicit candidate tuning, readiness states and partial-freeze boundary are consistent.")


def verify_registry_and_links(candidate_manifest: dict) -> None:
    registry = load(ROOT / "design/registry/experience_registry_v1.json")
    resource = find_resources_node(registry)
    if not resource:
        fail("existing Design Manager Resources entry was not found")
    expected_route = "#page/recognition_flow_v1_2/hifi/capture_first_use_guide/resources"
    if resource.get("route") != expected_route and resource.get("source_view_id") != "capture_first_use_guide":
        fail("Design Manager must keep the original Resources route/source view")
    if resource.get("render_mode") != "recognition_guide_resources":
        fail("Design Manager resource renderer ID changed")
    required_tabs = {"structure", "responsive", "resources"}
    if {tab.get("id") for tab in resource.get("workspace_tabs", [])} != required_tabs:
        fail("resource entry must publish the three interactive workspace tabs")
    expected_manifest = "design/pages/recognition/first_use_camera_guide/frontend_assets_manifest.json"
    if resource.get("frontend_asset_manifest") != expected_manifest:
        fail("Design Manager registry must point to the canonical independent asset manifest")
    if "frontend_asset_slots" in resource:
        fail("Design Manager registry must not duplicate the canonical manifest slot data")
    if len(candidate_manifest.get("assets", [])) != 12:
        fail("canonical asset manifest must own all 12 page-role bindings")
    for key in ("geometry_measurement_manifest", "responsive_contract_manifest", "readiness_matrix", "freeze_decision", "workspace_module"):
        target = resource.get(key)
        if not target or not (ROOT / target).is_file():
            fail(f"registry workspace artifact is not available: {key}={target}")
    print("PASS: the existing Design Manager Resources route, three workspaces, registry and asset manifest agree.")


def main() -> None:
    verify_frozen_assets()
    candidates = verify_candidate_manifest()
    verify_geometry_and_structure()
    verify_responsive_and_readiness()
    verify_registry_and_links(candidates)
    print("PASS: 12 candidate bindings → 8 unique independent PNG files; all remain non-frozen and unapproved.")
    print("PASS: nine archived composite example crops remain REFERENCE_ONLY; no full-page PNG is used as a photo slot.")


if __name__ == "__main__":
    main()
