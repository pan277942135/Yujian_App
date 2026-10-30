#!/usr/bin/env python3
"""Validate the Normal Home V3.1 visual acceptance contract and frozen references.

This is a static design-contract check. It never inspects/builds Android code or
claims runtime screenshot, device, accessibility, or photo-fixture acceptance.
"""
from __future__ import annotations

import hashlib
import json
import re
import struct
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
CONTRACT_DIR = ROOT / "design/system_v3/10_Page_Visual_Acceptance_Contracts/Normal_Home"
REGISTRY_PATH = CONTRACT_DIR / "Normal_Home_Visual_Contract_Registry_V1.json"

errors: list[str] = []
warnings: list[str] = []
checks = 0


def check(condition: bool, label: str, detail: str = "") -> None:
    global checks
    checks += 1
    if condition:
        print(f"PASS {label}")
    else:
        message = f"{label}: {detail}" if detail else label
        errors.append(message)
        print(f"FAIL {message}")


def read_json(path: Path, label: str) -> dict[str, Any] | list[Any] | None:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
        check(True, f"JSON parses: {label}")
        return value
    except Exception as exc:  # report parse issue without aborting other checks
        check(False, f"JSON parses: {label}", str(exc))
        return None


def repo_file(relative: str) -> Path:
    return ROOT / relative


def png_dimensions(data: bytes) -> tuple[int, int] | None:
    if len(data) < 24 or data[:8] != b"\x89PNG\r\n\x1a\n":
        return None
    return struct.unpack(">II", data[16:24])


def token_ids(path: Path) -> set[str]:
    data = read_json(path, str(path.relative_to(ROOT)))
    if not isinstance(data, dict):
        return set()
    return {
        item["id"] for item in data.get("tokens", [])
        if isinstance(item, dict) and isinstance(item.get("id"), str)
    }


def main() -> int:
    reg = read_json(REGISTRY_PATH, str(REGISTRY_PATH.relative_to(ROOT)))
    if not isinstance(reg, dict):
        print(f"NORMAL_HOME_VISUAL_CONTRACT_GATE FAIL checks={checks}")
        return 1

    check(reg.get("page_id") == "normal_home_v1", "Normal Home page identity")
    gate = reg.get("visual_gate", {})
    check(gate.get("gate") == "NORMAL_HOME_VISUAL_CONTRACT_READY" and gate.get("status") == "PASS",
          "Static visual contract gate identity/status")
    check(reg.get("runtime_status") == "NOT_RUN", "Runtime status remains NOT_RUN")
    check(reg.get("aggregate_implementation_readiness", {}).get("status") == "NOT_READY",
          "Android aggregate is not falsely marked ready")
    check(reg.get("page_registry_status") == "HOME_DEFERRED_NOT_REGISTERED_IN_PAGE_REGISTRY_V3_1",
          "Normal Home Page Contract status is explicit")
    android_registry = read_json(repo_file("design/system_v3/09_Android_Implementation_Contracts/Android_Implementation_Contract_Registry_V1.json"),
                                "Android_Implementation_Contract_Registry_V1.json")
    android_entry = next((row for row in android_registry.get("entries", [])
                          if isinstance(row, dict) and row.get("page_id") == "normal_home_v1"), None) if isinstance(android_registry, dict) else None
    check(reg.get("android_implementation_registry_status") == "NORMAL_HOME_ENTRY_REGISTERED"
          and android_entry is not None and android_entry.get("status") == "READY_FOR_IMPLEMENTATION",
          "Normal Home Android Contract is registered without altering visual rules")

    required_docs = {
        "visual", "state_visual_matrix", "pixel_targets", "component_anchor_map",
        "responsive", "hero_evidence_fit", "motion", "accessibility", "runtime_gate", "readme"
    }
    contracts = reg.get("contracts", {})
    check(required_docs <= set(contracts), "All required visual contract files are registered",
          f"missing={sorted(required_docs - set(contracts))}")
    for key, relative in contracts.items():
        check(repo_file(relative).is_file(), f"Contract file exists: {key}", relative)

    authorities = reg.get("authority_source", {})
    for key, relative in authorities.items():
        check(repo_file(relative).is_file(), f"Authority exists: {key}", relative)

    areas = reg.get("coverage_areas", [])
    area_ids = [item.get("area_id") for item in areas if isinstance(item, dict)]
    required_areas = {"NH01", "NH02", "NH03", "NH04", "NH05", "NH06", "NH07"}
    check(len(area_ids) == len(set(area_ids)), "Coverage area IDs are unique")
    check(set(area_ids) == required_areas, "NH01-NH07 coverage is complete",
          f"found={sorted(set(area_ids))}")

    assets = reg.get("assets", [])
    asset_ids = [item.get("asset_id") for item in assets if isinstance(item, dict)]
    check(len(asset_ids) == len(set(asset_ids)), "Visual asset IDs are unique")
    asset_by_id = {item.get("asset_id"): item for item in assets if isinstance(item, dict)}
    asset_registry = read_json(repo_file("design/system_v3/06_Asset_System/Asset_Registry_V3_FINAL.json"),
                               "Asset_Registry_V3_FINAL.json")
    global_asset_rows = {}
    if isinstance(asset_registry, dict):
        global_asset_rows = {x.get("path"): x for x in asset_registry.get("entries", []) if isinstance(x, dict)}

    for asset in assets:
        path = asset.get("path", "")
        file_path = repo_file(path)
        check(file_path.is_file(), f"Asset exists: {asset.get('asset_id')}", path)
        if not file_path.is_file():
            continue
        raw = file_path.read_bytes()
        actual_sha = hashlib.sha256(raw).hexdigest()
        check(actual_sha == asset.get("sha256"), f"Asset SHA-256: {asset.get('asset_id')}",
              f"expected={asset.get('sha256')} actual={actual_sha}")
        check(len(raw) == asset.get("size_bytes"), f"Asset byte size: {asset.get('asset_id')}",
              f"expected={asset.get('size_bytes')} actual={len(raw)}")
        if asset.get("type") == "PNG":
            dimensions = png_dimensions(raw)
            expected = tuple(int(part) for part in asset.get("dimensions", "0x0").split("x"))
            check(dimensions == expected, f"PNG dimensions: {asset.get('asset_id')}",
                  f"expected={expected} actual={dimensions}")
        global_row = global_asset_rows.get(path)
        if global_row:
            check(global_row.get("sha256") == asset.get("sha256"),
                  f"Shared Asset Registry identity: {asset.get('asset_id')}", path)
            check(asset.get("asset_registry_status") == global_row.get("status"),
                  f"Asset status preserves V3 registry: {asset.get('asset_id')}",
                  f"contract={asset.get('asset_registry_status')} registry={global_row.get('status')}")
            if global_row.get("status") == "REVIEW":
                check(asset.get("page_visual_authority_status") == "FROZEN",
                      f"Page/asset review distinction: {asset.get('asset_id')}")
        else:
            check(asset.get("asset_registry_status") == "NOT_REGISTERED",
                  f"Unregistered asset is identified honestly: {asset.get('asset_id')}")
            warnings.append(f"No V3 Asset Registry row for {path}; source identity is still SHA-verified in this page contract.")

    for area in areas:
        refs = area.get("authority_asset_ids", [])
        check(bool(refs) and all(ref in asset_by_id for ref in refs),
              f"Area assets resolve: {area.get('area_id')}", str(refs))

    component_registry_path = repo_file("design/system_v3/05_Component_System/Component_Registry_V3_FINAL.json")
    component_registry = read_json(component_registry_path, "Component_Registry_V3_FINAL.json")
    component_ids = set()
    if isinstance(component_registry, dict):
        component_ids = {x.get("component_id") for x in component_registry.get("entries", []) if isinstance(x, dict)}
    component_refs = reg.get("component_refs", [])
    allowed_component_ids: set[str] = set()
    legacy_component_ids: set[str] = set()
    for item in component_refs:
        cid = item.get("component_id")
        authority_path = item.get("registry", "")
        if authority_path == "design/system_v3/05_Component_System/Component_Registry_V3_FINAL.json":
            check(cid in component_ids, f"Shared component resolves: {cid}")
            allowed_component_ids.add(cid)
        else:
            authority = read_json(repo_file(authority_path), authority_path)
            if isinstance(authority, dict):
                is_approved = authority.get("id") == cid or (
                    cid == "primary_capture_button" and authority.get("status") == "FROZEN"
                )
                check(is_approved, f"Frozen legacy component authority resolves: {cid}", authority_path)
                if is_approved:
                    legacy_component_ids.add(cid)
            else:
                check(False, f"Frozen legacy component authority resolves: {cid}", authority_path)
    allowed_component_ids |= legacy_component_ids

    typography_path = repo_file(reg.get("token_refs", {}).get("typography", ""))
    color_path = repo_file(reg.get("token_refs", {}).get("color", ""))
    geometry_path = repo_file(reg.get("token_refs", {}).get("geometry", ""))
    typography_ids = token_ids(typography_path)
    color_ids = token_ids(color_path)
    geometry_ids = token_ids(geometry_path)
    refs = reg.get("token_refs", {})
    for field, known in (("typography_ids", typography_ids), ("color_ids", color_ids), ("geometry_ids", geometry_ids)):
        ids = refs.get(field, [])
        check(len(ids) == len(set(ids)), f"{field} are unique")
        missing = set(ids) - known
        check(not missing, f"{field} resolve in the effective registry", str(sorted(missing)))

    matrix = read_json(repo_file(contracts.get("state_visual_matrix", "")), "Normal Home state matrix")
    check(isinstance(matrix, dict), "State visual matrix is an object")
    if isinstance(matrix, dict):
        states = matrix.get("states", [])
        state_ids = [s.get("state") for s in states if isinstance(s, dict)]
        check(len(state_ids) == len(set(state_ids)), "State IDs are unique")
        represented = {area for state in states for area in state.get("coverage_area_ids", [])}
        check(represented == required_areas, "State matrix covers all seven acceptance areas",
              f"found={sorted(represented)}")
        for state in states:
            missing_components = set(state.get("components", [])) - allowed_component_ids
            check(not missing_components, f"State components resolve: {state.get('state')}",
                  str(sorted(missing_components)))
            missing_assets = set(state.get("authority_asset_ids", [])) - set(asset_by_id)
            check(not missing_assets, f"State assets resolve: {state.get('state')}",
                  str(sorted(missing_assets)))
            missing_tokens = (set(state.get("typography_tokens", [])) - typography_ids) | \
                             (set(state.get("color_tokens", [])) - color_ids)
            check(not missing_tokens, f"State token references resolve: {state.get('state')}",
                  str(sorted(missing_tokens)))
        runtime_verdicts = matrix.get("runtime_verdicts", {})
        check(bool(runtime_verdicts) and all(x == "NOT_RUN" for x in runtime_verdicts.values()),
              "No runtime verdict is represented as PASS")

    anchors = read_json(repo_file(contracts.get("component_anchor_map", "")), "Component anchor map")
    check(isinstance(anchors, dict), "Component anchor map is an object")
    if isinstance(anchors, dict):
        anchor_items = anchors.get("anchors", [])
        anchor_ids = [x.get("anchor_id") for x in anchor_items if isinstance(x, dict)]
        check(len(anchor_ids) == len(set(anchor_ids)), "Anchor IDs are unique")
        anchored_areas = {x.get("area_id") for x in anchor_items if isinstance(x, dict)}
        check(required_areas <= anchored_areas, "Anchor map covers NH01-NH07",
              f"missing={sorted(required_areas - anchored_areas)}")
        for anchor in anchor_items:
            for field in ("visual_bounds", "layout_bounds", "touch_bounds", "source", "tolerance"):
                check(bool(anchor.get(field)), f"Anchor {anchor.get('anchor_id')} has {field}")
            cid = anchor.get("component_id")
            if cid:
                check(cid in allowed_component_ids, f"Anchor component resolves: {anchor.get('anchor_id')}", str(cid))
            else:
                check(bool(anchor.get("target_role")), f"Page role is explicit: {anchor.get('anchor_id')}")
            aid = anchor.get("authority_asset_id")
            check(aid in asset_by_id, f"Anchor source asset resolves: {anchor.get('anchor_id')}", str(aid))

    pixel = read_json(repo_file(contracts.get("pixel_targets", "")), "Pixel Targets")
    check(isinstance(pixel, dict), "Pixel targets are an object")
    if isinstance(pixel, dict):
        profile = pixel.get("reference_device", {})
        check((profile.get("width_px"), profile.get("height_px")) == (1080, 1920), "1080x1920 reference device")
        check(pixel.get("runtime_status") == "NOT_RUN", "Pixel target runtime status remains NOT_RUN")
        colors = pixel.get("source_color_policy", {})
        check(colors.get("source_color_space") == "UNVERIFIED_NOT_RECORDED",
              "Source color space is not guessed")

    hero_contract = repo_file(contracts.get("hero_evidence_fit", ""))
    hero_text = hero_contract.read_text(encoding="utf-8") if hero_contract.is_file() else ""
    for required in ("4:3 landscape", "16:9 landscape", "3:4 portrait", "9:16 portrait", "edge", "black", "bbox"):
        check(required.lower() in hero_text.lower(), f"Hero evidence case covered: {required}")
    check("AI" not in hero_text or "AI-generated" in hero_text or "generated" in hero_text.lower(),
          "Hero contract prohibits fabricated photo content")

    manager_page = CONTRACT_DIR / "index.html"
    check(manager_page.is_file(), "Normal Home Design Manager page exists")
    if manager_page.is_file():
        html = manager_page.read_text(encoding="utf-8")
        for href in re.findall(r"(?:href|src)=\"([^\"]+)\"", html):
            if href.startswith(("http://", "https://", "#", "data:")):
                continue
            target = (manager_page.parent / href.split("#", 1)[0]).resolve()
            check(target.exists(), f"Design Manager link resolves: {href}")
    manager_index = repo_file("design/manager/index.html")
    check(manager_index.is_file() and "10_Page_Visual_Acceptance_Contracts/Normal_Home/index.html" in manager_index.read_text(encoding="utf-8"),
          "Design Manager retains menus and links Normal Home Visual Acceptance")

    print(f"NORMAL_HOME_VISUAL_CONTRACT_GATE checks={checks} failed={len(errors)} warnings={len(warnings)}")
    for warning in warnings:
        print(f"WARN {warning}")
    if errors:
        print("NORMAL_HOME_VISUAL_CONTRACT_GATE FAIL")
        return 1
    print("NORMAL_HOME_VISUAL_CONTRACT_GATE PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
