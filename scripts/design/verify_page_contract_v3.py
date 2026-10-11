#!/usr/bin/env python3
"""Validate the YuJian V3.1 Page Contract registry and declared shared dependencies."""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path, PurePosixPath
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
BASE = "design/system_v3/08_Page_Contracts"
REGISTRY = f"{BASE}/Page_Registry_V3_1.json"
TYPE_PATH = "design/system_v3/04_Design_Tokens/Typography/Typography_Tokens_V3_FINAL.json"
COLOR_PATH = "design/system_v3/04_Design_Tokens/Color/Color_Tokens_V3_FINAL.json"
GEOMETRY_PATH = "design/system_v3/07_Responsive_System/Geometry_Tokens_V3_FINAL.json"
COMPONENT_PATH = "design/system_v3/05_Component_System/Component_Registry_V3_FINAL.json"
ASSET_PATH = "design/system_v3/06_Asset_System/Asset_Registry_V3_FINAL.json"
BACKGROUND_PATH = "design/system_v3/08_Background_System/Background_Registry_V3.json"
ALLOWED_STATUS = {"DRAFT", "REVIEW", "FROZEN", "DEPRECATED"}
REQUIRED_PAGE_FIELDS = {"page_id", "name", "status", "contract", "state_machine", "data_contract", "acceptance", "authority"}
REQUIRED_SECTIONS = [
    "Page Purpose", "User Flow", "Layout Contract", "State Machine Contract",
    "Data Contract", "Engineering Contract", "Acceptance Contract", "Conflicts and Freeze Gate"
]
ENGINEERING_SECTIONS = [
    "Page structure", "Scroll, fixed and flexible", "Insets and system bars", "Keyboard",
    "Media Fit", "Shared component use", "Animation and interaction", "Error and recovery"
]
DATA_COVERAGE = {"empty", "loading", "error", "partial", "network_failure"}


def load_json(path: str) -> dict[str, Any]:
    result = json.loads((ROOT / path).read_text(encoding="utf-8"))
    if not isinstance(result, dict):
        raise ValueError(f"{path}: root must be an object")
    return result


def safe_path(value: Any) -> bool:
    if not isinstance(value, str) or not value:
        return False
    path = PurePosixPath(value)
    return not path.is_absolute() and all(part not in {"", ".", ".."} for part in path.parts)


def text_file(path: str) -> str:
    if not safe_path(path):
        raise ValueError(f"unsafe path: {path!r}")
    return (ROOT / path).read_text(encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--strict-frozen", action="store_true", help="fail unless every registered page is FROZEN")
    args = parser.parse_args()
    issues: list[str] = []
    registry = load_json(REGISTRY)
    typography = load_json(TYPE_PATH)
    color = load_json(COLOR_PATH)
    geometry = load_json(GEOMETRY_PATH)
    components_doc = load_json(COMPONENT_PATH)
    assets_doc = load_json(ASSET_PATH)
    backgrounds_doc = load_json(BACKGROUND_PATH)

    tokens: dict[str, tuple[str, dict[str, Any]]] = {}
    for path, doc in [(TYPE_PATH, typography), (COLOR_PATH, color), (GEOMETRY_PATH, geometry)]:
        for row in doc.get("tokens", []):
            tid = row.get("id") if isinstance(row, dict) else None
            if not isinstance(tid, str) or not tid:
                issues.append(f"{path}: token row is missing id")
            elif tid in tokens:
                issues.append(f"duplicate shared token id: {tid}")
            else:
                tokens[tid] = (path, row)

    components = {row.get("component_id"): row for row in components_doc.get("entries", []) if isinstance(row, dict)}
    assets = {row.get("asset_id"): row for row in assets_doc.get("entries", []) if isinstance(row, dict)}
    assets_by_path = {row.get("path"): row for row in assets_doc.get("entries", []) if isinstance(row, dict)}
    backgrounds = {row.get("background_id"): row for row in backgrounds_doc.get("backgrounds", []) if isinstance(row, dict)}
    if len(components) != 16:
        issues.append(f"effective Component Registry expected 16 components, found {len(components)}")
    if len(assets) != 658:
        issues.append(f"effective Asset Registry expected 658 assets, found {len(assets)}")

    pages = registry.get("pages")
    if not isinstance(pages, list) or not pages:
        issues.append("Page Registry pages must be a non-empty list")
        pages = []
    page_ids: set[str] = set()
    counts = {status: 0 for status in ALLOWED_STATUS}
    blocker_count = 0

    def validate_asset_ref(reference: Any, label: str) -> None:
        if not isinstance(reference, dict):
            issues.append(f"{label}: asset reference is not an object")
            return
        aid, path, digest = reference.get("asset_id"), reference.get("path"), reference.get("sha256")
        row = assets.get(aid)
        if row is None:
            issues.append(f"{label}: unknown asset_id {aid!r}")
            return
        if row.get("path") != path or row.get("sha256") != digest:
            issues.append(f"{label}: asset path/SHA does not match Asset Registry for {aid}")
            return
        file_path = ROOT / str(path)
        if file_path.is_symlink() or not file_path.is_file():
            issues.append(f"{label}: referenced asset is missing or a symlink: {path}")
            return
        actual = hashlib.sha256(file_path.read_bytes()).hexdigest()
        if actual != digest:
            issues.append(f"{label}: raw-byte SHA mismatch for {path}")

    def validate_component(cid: str, page_id: str, seen: set[str]) -> None:
        if cid in seen:
            return
        seen.add(cid)
        component = components.get(cid)
        if component is None:
            issues.append(f"{page_id}: unknown shared component {cid!r}")
            return
        if component.get("status") != "FROZEN":
            issues.append(f"{page_id}: component {cid} is not FROZEN")
        for token_id in component.get("token_dependency", []):
            if token_id not in tokens:
                issues.append(f"{page_id}: component {cid} has unresolved token {token_id}")
        for aid in component.get("asset_dependency", []):
            asset_row = assets.get(aid)
            if asset_row is None:
                issues.append(f"{page_id}: component {cid} has unresolved asset {aid}")
            else:
                validate_asset_ref({"asset_id": aid, "path": asset_row.get("path"), "sha256": asset_row.get("sha256")}, f"{page_id}/{cid}")
        for child in component.get("component_dependency", []):
            validate_component(child, page_id, seen)

    for page in pages:
        if not isinstance(page, dict):
            issues.append("Page Registry row is not an object")
            continue
        missing = REQUIRED_PAGE_FIELDS - set(page)
        if missing:
            issues.append(f"page row missing fields: {sorted(missing)}")
        page_id = page.get("page_id")
        if not isinstance(page_id, str) or not page_id:
            issues.append("page row has empty page_id")
            continue
        if page_id in page_ids:
            issues.append(f"duplicate page_id: {page_id}")
        page_ids.add(page_id)
        status = page.get("status")
        if status not in ALLOWED_STATUS:
            issues.append(f"{page_id}: invalid status {status!r}")
        else:
            counts[status] += 1

        required_files = [
            "contract", "layout_contract", "state_machine", "data_contract",
            "engineering_contract", "acceptance", "authority"
        ]
        for field in required_files:
            path = page.get(field)
            if not safe_path(path) or not (ROOT / path).is_file():
                issues.append(f"{page_id}: missing or unsafe {field} path {path!r}")
        if not all(safe_path(page.get(field)) and (ROOT / page[field]).is_file() for field in required_files):
            continue

        contract_text = text_file(page["contract"])
        for required in REQUIRED_SECTIONS:
            if f"## {required}" not in contract_text:
                issues.append(f"{page_id}: contract missing section {required!r}")
        for marker, expected in [("PAGE_ID:", page_id), ("PAGE_NAME:", page.get("name")), ("STATUS:", status), ("AUTHORITY_SOURCE:", page.get("authority"))]:
            if f"{marker} {expected}" not in contract_text:
                issues.append(f"{page_id}: contract metadata does not match registry field {marker}")
        engineering_text = text_file(page["engineering_contract"])
        for required in ENGINEERING_SECTIONS:
            if f"## {required}" not in engineering_text:
                issues.append(f"{page_id}: Engineering Contract missing section {required!r}")

        state_doc = load_json(page["state_machine"])
        states = state_doc.get("states", [])
        state_ids = [row.get("id") for row in states if isinstance(row, dict)]
        if not state_ids or len(state_ids) != len(set(state_ids)):
            issues.append(f"{page_id}: state IDs are empty or duplicated")
        state_set = set(state_ids)
        for transition in state_doc.get("transitions", []):
            if not isinstance(transition, dict):
                issues.append(f"{page_id}: malformed state transition")
                continue
            for field in ("current_state", "action", "next_state"):
                if not transition.get(field):
                    issues.append(f"{page_id}: state transition missing {field}")
            if transition.get("current_state") not in state_set or transition.get("next_state") not in state_set:
                issues.append(f"{page_id}: transition references unknown state {transition.get('current_state')} -> {transition.get('next_state')}")
        if state_doc.get("entry_state") not in state_set:
            issues.append(f"{page_id}: entry_state does not exist")

        data_doc = load_json(page["data_contract"])
        fields = data_doc.get("fields", [])
        if not isinstance(fields, list) or not fields:
            issues.append(f"{page_id}: Data Contract has no fields")
        else:
            seen_fields: set[str] = set()
            for field in fields:
                if not isinstance(field, dict):
                    issues.append(f"{page_id}: malformed data field")
                    continue
                key = field.get("field")
                if not isinstance(key, str) or not key or key in seen_fields:
                    issues.append(f"{page_id}: empty or duplicate data field {key!r}")
                seen_fields.add(key)
                for required in ("source", "required", "fallback", "error_behavior"):
                    if field.get(required) in (None, "", []):
                        issues.append(f"{page_id}/{key}: missing {required}")
        coverage = data_doc.get("coverage", {})
        if not isinstance(coverage, dict) or DATA_COVERAGE - set(coverage):
            issues.append(f"{page_id}: Data Contract must cover {sorted(DATA_COVERAGE)}")
        elif any(not isinstance(coverage[k], str) or not coverage[k].strip() for k in DATA_COVERAGE):
            issues.append(f"{page_id}: Data Contract has an empty coverage rule")

        acceptance = load_json(page["acceptance"])
        blocked_checks = []
        for category in ("visual", "interaction", "accessibility", "error"):
            checks = acceptance.get(category)
            if not isinstance(checks, list) or not checks:
                issues.append(f"{page_id}: Acceptance Contract has no {category} checks")
                continue
            for check in checks:
                if not isinstance(check, dict) or not all(check.get(k) for k in ("id", "criterion", "method", "pass_condition")):
                    issues.append(f"{page_id}: malformed {category} acceptance check")
                    continue
                if check.get("status") not in {"READY", "BLOCKED"}:
                    issues.append(f"{page_id}/{check.get('id')}: status must be READY or BLOCKED")
                if check.get("status") == "BLOCKED":
                    blocked_checks.append(check.get("id"))
        blocker_ids = {
            row.get("id") for row in page.get("known_blockers", [])
            if isinstance(row, dict) and row.get("id")
        }
        unresolved = load_json(page["layout_contract"]).get("unresolved_dependencies", [])
        for item in unresolved:
            if item.get("id") not in blocker_ids:
                issues.append(f"{page_id}: unresolved dependency {item.get('id')} is not registered as a known blocker")
        for check_list in [acceptance.get(k, []) for k in ("visual", "interaction", "accessibility", "error")]:
            for check in check_list:
                if isinstance(check, dict):
                    for bid in check.get("blocked_by", []):
                        if bid not in blocker_ids:
                            issues.append(f"{page_id}/{check.get('id')}: unknown blocker {bid}")
        blocking = [row for row in page.get("known_blockers", []) if isinstance(row, dict) and row.get("blocks_freeze") is True]
        blocker_count += len(blocking)
        if status == "FROZEN" and (blocking or blocked_checks or unresolved):
            issues.append(f"{page_id}: cannot be FROZEN while blocking issues/checks remain")

        layout = load_json(page["layout_contract"])
        if layout.get("page_id") != page_id:
            issues.append(f"{page_id}: Layout Contract page_id mismatch")
        seen_components: set[str] = set()
        for reference in layout.get("component_refs", []):
            if not isinstance(reference, dict) or not reference.get("component"):
                issues.append(f"{page_id}: malformed component reference")
                continue
            cid = reference["component"]
            validate_component(cid, page_id, seen_components)
            component = components.get(cid)
            if component:
                if reference.get("authority") != COMPONENT_PATH:
                    issues.append(f"{page_id}/{cid}: component authority is not the effective registry")
                if not set(component.get("token_dependency", [])).issubset(set(reference.get("token_refs", []))):
                    issues.append(f"{page_id}/{cid}: layout omits a component token dependency")
                if not set(component.get("asset_dependency", [])).issubset(set(reference.get("asset_refs", []))):
                    issues.append(f"{page_id}/{cid}: layout omits a component asset dependency")
        for reference in layout.get("token_refs", []):
            if not isinstance(reference, dict):
                issues.append(f"{page_id}: malformed token reference")
                continue
            tid = reference.get("token_id")
            expected = tokens.get(tid)
            if expected is None:
                issues.append(f"{page_id}: unknown token {tid!r}")
            elif reference.get("authority") != expected[0]:
                issues.append(f"{page_id}/{tid}: token authority is not effective")
        for reference in layout.get("asset_refs", []):
            validate_asset_ref(reference, page_id)
        for reference in layout.get("background_refs", []):
            bid = reference.get("background_id") if isinstance(reference, dict) else None
            background = backgrounds.get(bid)
            if background is None:
                issues.append(f"{page_id}: unknown background {bid!r}")
                continue
            if background.get("status") != "FROZEN" or reference.get("authority") != BACKGROUND_PATH:
                issues.append(f"{page_id}/{bid}: background authority/status is not effective/FROZEN")
            if reference.get("master_path") != background.get("master_path") or reference.get("sha256") != background.get("sha256"):
                issues.append(f"{page_id}/{bid}: background identity does not match registry")
            if background.get("master_path"):
                bg_asset = assets_by_path.get(background["master_path"])
                if not bg_asset or bg_asset.get("sha256") != background.get("sha256"):
                    issues.append(f"{page_id}/{bid}: master path/SHA does not resolve through Asset Registry")
            elif background.get("sha256_status") != "NOT_APPLICABLE_DYNAMIC_SOURCE":
                issues.append(f"{page_id}/{bid}: dynamic background has no documented SHA rule")
        for authority in layout.get("page_scoped_authorities", []):
            if not safe_path(authority) or not (ROOT / authority).is_file():
                issues.append(f"{page_id}: missing page authority {authority!r}")
        if layout.get("rules", {}).get("page_private_tokens") is True or layout.get("rules", {}).get("no_page_private_tokens") is True:
            pass
        elif layout.get("rules", {}).get("page_private_tokens") not in (False, None):
            issues.append(f"{page_id}: page-private token policy is malformed")

    if args.strict_frozen and any(p.get("status") != "FROZEN" for p in pages if isinstance(p, dict)):
        issues.append("strict page freeze requested but one or more registered pages are not FROZEN")

    if issues:
        print(f"FAIL: {len(issues)} Page Contract issue(s)")
        for issue in issues:
            print(f" - {issue}")
        return 1

    print("PASS: Page Contract V3.1 structural validation")
    print(f"  Registered: {len(pages)} | Frozen: {counts['FROZEN']} | Review: {counts['REVIEW']} | Draft: {counts['DRAFT']} | Deprecated: {counts['DEPRECATED']}")
    print(f"  Freeze blockers: {blocker_count} | Component Registry references resolve: yes | Token/Asset/Background references resolve: yes")
    if args.strict_frozen:
        print("  Page freeze gate: PASS")
    else:
        print("  Page freeze gate: REVIEW (registered blockers remain)")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, json.JSONDecodeError, ValueError) as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        raise SystemExit(1)
