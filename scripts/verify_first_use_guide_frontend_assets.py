#!/usr/bin/env python3
"""Validate structured first-use guide assets without promoting missing sources."""

from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GUIDE = ROOT / "design/pages/recognition/first_use_camera_guide"
MANIFEST_PATH = GUIDE / "frontend_assets_manifest.json"
STRUCTURE_PATH = GUIDE / "page_structure.json"
FROZEN_PATH = GUIDE / "manifest.json"
REFERENCE_PATH = ROOT / "design/resource_packages/MANIFEST.json"

EXPECTED_PAGES = {
    "p01": {"fish_orientation": "horizontal", "width": 941, "height": 1671},
    "p02": {"fish_orientation": "vertical", "width": 941, "height": 1671},
    "p03": {"fish_orientation": "horizontal", "width": 941, "height": 1672},
}
EXPECTED_ASSETS = {
    "hero_horizontal": ("correct_hero", "horizontal", {"p01", "p03"}),
    "error_head_out_horizontal": ("fish_head_out_of_frame", "horizontal", {"p01", "p03"}),
    "error_too_small_horizontal": ("fish_too_small", "horizontal", {"p01", "p03"}),
    "error_occluded_horizontal": ("excessive_occlusion", "horizontal", {"p01", "p03"}),
    "hero_vertical": ("correct_hero", "vertical", {"p02"}),
    "error_head_out_vertical": ("fish_head_out_of_frame", "vertical", {"p02"}),
    "error_too_small_vertical": ("fish_too_small", "vertical", {"p02"}),
    "error_occluded_vertical": ("excessive_occlusion", "vertical", {"p02"}),
}


def fail(message: str) -> None:
    print(f"FAIL: {message}", file=sys.stderr)
    raise SystemExit(1)


def load(path: Path):
    if not path.is_file():
        fail(f"missing file: {path.relative_to(ROOT)}")
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        fail(f"invalid JSON {path.relative_to(ROOT)}: {exc}")


def main() -> int:
    manifest = load(MANIFEST_PATH)
    structure = load(STRUCTURE_PATH)
    frozen = load(FROZEN_PATH)
    reference = load(REFERENCE_PATH)
    registry = load(ROOT / "design/registry/experience_registry_v1.json")
    navigation = load(GUIDE / "navigation.json")

    if manifest.get("schema_version") != "1.0" or manifest.get("catalog_id") != "recognition_first_use_camera_guide_frontend_assets_v1":
        fail("frontend asset manifest identity/schema drift")
    if manifest.get("status") != "PARTIAL_SOURCE_BLOCKED":
        fail("manifest must truthfully remain PARTIAL_SOURCE_BLOCKED while original photos are missing")
    if manifest.get("source_inventory", {}).get("found_independent_original_count") != 0:
        fail("source inventory must not claim independent originals were found")
    if manifest.get("authority_boundary", {}).get("runtime_visual_authority") is not False:
        fail("front-end resource manifest cannot become Android runtime visual authority")
    if manifest.get("asset_policy", {}).get("do_not_crop_frozen_composites", True) is not True:
        fail("production asset policy must forbid cropping Frozen composites")
    if structure.get("contract_id") != "FIRST_USE_CAMERA_GUIDE_PAGE_STRUCTURE_V1" or structure.get("status") != "STRUCTURE_ONLY_CANDIDATE":
        fail("page structure contract identity/status drift")
    if structure.get("visual_authority") is not False or structure.get("runtime_visual_authority") is not False:
        fail("structure-only candidate must not claim visual/runtime authority")
    if structure.get("geometry_policy", {}).get("exact_component_bounds_measured") is not False:
        fail("schematic structure cannot claim unmeasured exact geometry")

    feature = next((item for item in registry.get("features", []) if item.get("id") == "recognition_flow_v1_2"), None)
    if not feature:
        fail("recognition_flow_v1_2 missing from experience registry")
    guide = next((item for item in feature.get("hifi_views", []) if item.get("id") == "capture_first_use_guide"), None)
    resource = next((item for item in (guide or {}).get("children", []) if item.get("id") == "resources"), None)
    if not resource:
        fail("Resources submenu missing from registered guide")
    if resource.get("frontend_assets_manifest") != "design/pages/recognition/first_use_camera_guide/frontend_assets_manifest.json":
        fail("registry frontend_assets_manifest path drift")
    if resource.get("page_structure_contract") != "design/pages/recognition/first_use_camera_guide/page_structure.json":
        fail("registry page_structure_contract path drift")
    if resource.get("reference_only_package_manifest") != "design/resource_packages/MANIFEST.json":
        fail("registry reference-only manifest path drift")
    if resource.get("asset_catalog_status") != "PARTIAL_SOURCE_BLOCKED" or resource.get("independent_photo_sources_found") != 0:
        fail("registry must preserve explicit source-blocked state")
    menus = navigation.get("resource_menus", [])
    if len(menus) != 1 or menus[0].get("id") != "resources" or menus[0].get("route") != "#page/recognition_flow_v1_2/hifi/capture_first_use_guide/resources":
        fail("guide Resources navigation route drift")
    if menus[0].get("frontend_assets_manifest") != "frontend_assets_manifest.json" or menus[0].get("page_structure_contract") != "page_structure.json":
        fail("navigation does not register both new resource contracts")
    if menus[0].get("reference_only_package_manifest") != "design/resource_packages/MANIFEST.json":
        fail("navigation reference-only package path drift")
    if menus[0].get("asset_catalog_status") != "PARTIAL_SOURCE_BLOCKED":
        fail("navigation must not claim missing photo assets are complete")

    assets = {item.get("id"): item for item in manifest.get("assets", [])}
    if set(assets) != set(EXPECTED_ASSETS):
        fail(f"expected exactly 8 unique source asset definitions; got {sorted(assets)}")
    for asset_id, (role, orientation, shared_by) in EXPECTED_ASSETS.items():
        asset = assets[asset_id]
        if asset.get("role") != role or asset.get("orientation") != orientation:
            fail(f"asset role/orientation drift: {asset_id}")
        if set(asset.get("sharedBy", [])) != shared_by:
            fail(f"asset sharing contract drift: {asset_id}")
        if asset.get("status") != "ASSET_SOURCE_BLOCKED":
            fail(f"unverified photo asset must stay ASSET_SOURCE_BLOCKED: {asset_id}")
        for field in ("path", "source_filename", "mime_type", "width", "height", "bytes", "sha256"):
            if asset.get(field) is not None:
                fail(f"missing photo source must not invent {field}: {asset_id}")
        if asset.get("approved_for_frontend_consumption") is not False or asset.get("runtime_mapping") is not False:
            fail(f"missing photo source cannot be approved for frontend/runtime: {asset_id}")
        if asset.get("license_status") != "NOT_CONFIRMED":
            fail(f"source/licence status must remain NOT_CONFIRMED: {asset_id}")

    bindings = manifest.get("bindings", [])
    if len(bindings) != 12:
        fail(f"expected 12 page/slot bindings, got {len(bindings)}")
    binding_ids = [item.get("binding_id") for item in bindings]
    if len(set(binding_ids)) != len(binding_ids):
        fail("duplicate page slot binding IDs")
    for page_id, expected in EXPECTED_PAGES.items():
        page_bindings = [item for item in bindings if item.get("page_id") == page_id]
        if len(page_bindings) != 4:
            fail(f"{page_id} must bind one hero plus three error examples")
        if any(item.get("status") != "ASSET_SOURCE_BLOCKED" for item in page_bindings):
            fail(f"{page_id} must keep all missing photo bindings blocked")
        if any(item.get("required_orientation") != expected["fish_orientation"] for item in page_bindings):
            fail(f"{page_id} fish orientation binding drift")
        if any(item.get("asset_id") not in assets for item in page_bindings):
            fail(f"{page_id} binding references an unknown asset")

    pages = {item.get("id"): item for item in structure.get("pages", [])}
    if set(pages) != set(EXPECTED_PAGES) or structure.get("page_count") != 3:
        fail(f"page structure must preserve exactly P01/P02/P03, got {sorted(pages)}")
    frozen_assets = {str(item.get("id", "")).lower(): item for item in frozen.get("assets", [])}
    for page_id, expected in EXPECTED_PAGES.items():
        page = pages[page_id]
        frozen_asset = frozen_assets.get(page_id)
        if not frozen_asset:
            fail(f"frozen visual authority missing for {page_id}")
        if page.get("source_page_png") != frozen_asset.get("path") or page.get("source_page_sha256") != frozen_asset.get("sha256"):
            fail(f"structured page authority binding drift: {page_id}")
        if page.get("source_page_dimensions_px") != {"width": expected["width"], "height": expected["height"]}:
            fail(f"frozen source dimensions drift: {page_id}")
        if page.get("fish_orientation") != expected["fish_orientation"]:
            fail(f"page fish orientation drift: {page_id}")
        if page.get("structure_status") != "STRUCTURE_ONLY_CANDIDATE" or page.get("runtime_visual_authority") is not False:
            fail(f"structure preview must remain candidate-only: {page_id}")
        if page.get("geometry_status") != "SEMANTIC_TREE_ONLY_BOUNDS_NOT_MEASURED":
            fail(f"unmeasured page geometry must be explicit: {page_id}")
        preview = ROOT / str(page.get("structure_preview_path", ""))
        if not preview.is_file():
            fail(f"missing structure preview: {page.get('structure_preview_path')}")
        svg = preview.read_bytes()
        svg_text = svg.decode("utf-8")
        if "<svg" not in svg_text or "STRUCTURE_ONLY_CANDIDATE" not in svg_text or "独立原图未找到" not in svg_text:
            fail(f"structure preview missing explicit candidate/missing-source semantics: {preview.relative_to(ROOT)}")
        layers = page.get("layers", [])
        layer_ids = [layer.get("id") for layer in layers]
        if not layers or len(layer_ids) != len(set(layer_ids)):
            fail(f"{page_id} has no layers or duplicate layer IDs")
        known_ids = set(layer_ids)
        for layer in layers:
            parent = layer.get("parent_id")
            if parent is not None and parent not in known_ids:
                fail(f"{page_id} layer has unknown parent: {layer.get('id')} -> {parent}")
            if layer.get("asset_binding") and layer["asset_binding"] not in binding_ids:
                fail(f"{page_id} layer has unknown asset binding: {layer.get('asset_binding')}")
        print(json.dumps({
            "structure_asset": page.get("structure_preview_path"),
            "bytes": len(svg),
            "sha256": hashlib.sha256(svg).hexdigest(),
            "status": "STRUCTURE_ONLY_CANDIDATE"
        }, ensure_ascii=False))

    reference_assets = {item.get("path"): item for item in reference.get("assets", [])}
    for asset in assets.values():
        for entry in asset.get("reference_only_package_entries", []):
            path = entry.get("package_path")
            reference_entry = reference_assets.get(path)
            if not reference_entry or reference_entry.get("status") != "REFERENCE_ONLY":
                fail(f"legacy reference crop must be registered REFERENCE_ONLY: {path}")
            if entry.get("status") != "REFERENCE_ONLY" or entry.get("direct_repo_file") is not False:
                fail(f"legacy crop must not be presented as a direct production image: {path}")
    notes = " ".join(reference.get("notes", []))
    if "flattened composited screenshots" not in notes:
        fail("reference package provenance no longer documents flattened screenshot crops")

    if len({item.get("binding_id") for item in bindings if item.get("page_id") == "p01"}) != 4:
        fail("P01 binding count incorrect")
    if {item.get("asset_id") for item in bindings if item.get("page_id") == "p01"} != {item.get("asset_id") for item in bindings if item.get("page_id") == "p03"}:
        fail("P01 and P03 must share the same four horizontal asset keys")
    if {item.get("asset_id") for item in bindings if item.get("page_id") == "p02"} & {item.get("asset_id") for item in bindings if item.get("page_id") == "p01"}:
        fail("P02 vertical assets must not be mixed with P01/P03 horizontal assets")

    print("PASS: first-use guide structure/asset catalog integrity | pages=3 | unique_source_assets=8 | bindings=12 | independent_originals=0 | status=PARTIAL_SOURCE_BLOCKED")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
