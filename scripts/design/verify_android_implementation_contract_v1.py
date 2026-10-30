#!/usr/bin/env python3
"""Validate Recognition Result Android implementation-contract references only.

This validator intentionally does not inspect or build the Android project.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path, PurePosixPath
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
BASE = "design/system_v3/09_Android_Implementation_Contracts"
REGISTRY_PATH = f"{BASE}/Android_Implementation_Contract_Registry_V1.json"
STATE_PATH = f"{BASE}/Recognition_Result_State_Binding_V1.json"
MAPPING_PATH = f"{BASE}/Recognition_Result_Component_Mapping_V1.json"
CONTRACT_PATH = f"{BASE}/Recognition_Result_Android_Contract_V1.md"
GATE_PATH = f"{BASE}/Recognition_Result_Runtime_Gate_V1.md"
PAGE_REGISTRY_PATH = "design/system_v3/08_Page_Contracts/Page_Registry_V3_1.json"
LAYOUT_PATH = "design/system_v3/08_Page_Contracts/recognition/result/Layout_Contract_V3_1.json"
PAGE_CONTRACT_PATH = "design/system_v3/08_Page_Contracts/recognition/result/Recognition_Result_Page_Contract_V3_1.md"
PAGE_STATE_PATH = "design/system_v3/08_Page_Contracts/recognition/result/State_Machine_V3_1.json"
COMPONENT_PATH = "design/system_v3/05_Component_System/Component_Registry_V3_FINAL.json"
ASSET_PATH = "design/system_v3/06_Asset_System/Asset_Registry_V3_FINAL.json"
AUTHORITY_PATH = "design/system_v3/11_Freeze_Governance/Effective_Authority_Registry_V3_1.json"
TOKEN_PATHS = {
    "typography": "design/system_v3/04_Design_Tokens/Typography/Typography_Tokens_V3_FINAL.json",
    "color": "design/system_v3/04_Design_Tokens/Color/Color_Tokens_V3_FINAL.json",
    "geometry": "design/system_v3/07_Responsive_System/Geometry_Tokens_V3_FINAL.json",
}
REQUIRED_STATES = {
    "INITIAL", "RESULT_READY", "HIGH_CONFIDENCE", "MEDIUM_CONFIDENCE",
    "LOW_CONFIDENCE", "NO_FISH", "IMAGE_QUALITY_WARNING", "SAVE_LOADING",
    "SAVE_SUCCESS", "SAVE_ERROR",
}
FORBIDDEN_STATES = {"SUCCESS_2", "FAILED_TEMP", "UNKNOWN_RESULT"}
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")


def safe_path(value: Any) -> bool:
    if not isinstance(value, str) or not value:
        return False
    path = PurePosixPath(value)
    return not path.is_absolute() and all(part not in {"", ".", ".."} for part in path.parts)


def read_json(relative: str) -> dict[str, Any]:
    if not safe_path(relative):
        raise ValueError(f"unsafe path: {relative!r}")
    value = json.loads((ROOT / relative).read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"{relative}: JSON root must be an object")
    return value


def require_file(relative: Any, label: str, issues: list[str]) -> None:
    if not safe_path(relative):
        issues.append(f"{label}: unsafe or empty path {relative!r}")
    elif not (ROOT / relative).is_file():
        issues.append(f"{label}: referenced file does not exist: {relative}")


def main() -> int:
    issues: list[str] = []
    registry = read_json(REGISTRY_PATH)
    page_registry = read_json(PAGE_REGISTRY_PATH)
    layout = read_json(LAYOUT_PATH)
    page_state = read_json(PAGE_STATE_PATH)
    binding = read_json(STATE_PATH)
    mapping = read_json(MAPPING_PATH)
    components_doc = read_json(COMPONENT_PATH)
    assets_doc = read_json(ASSET_PATH)
    authorities = read_json(AUTHORITY_PATH)
    token_docs = {name: read_json(path) for name, path in TOKEN_PATHS.items()}

    if registry.get("registry_id") != "android_implementation_contract_registry_v1":
        issues.append("Android Implementation Contract registry_id mismatch")
    if registry.get("version") != "V1" or registry.get("status") != "READY_FOR_IMPLEMENTATION":
        issues.append("Android Implementation Contract registry is not V1 READY_FOR_IMPLEMENTATION")
    if registry.get("design_contract") != "PAGE_CONTRACT_READY":
        issues.append("Android Implementation Contract does not reference PAGE_CONTRACT_READY")

    entries = registry.get("entries")
    if not isinstance(entries, list) or not entries:
        issues.append("registry must contain scoped page entries")
        entries = []
    entry_page_ids = [row.get("page_id") for row in entries if isinstance(row, dict)]
    if len(entry_page_ids) != len(set(entry_page_ids)):
        issues.append("registry contains duplicate page_id values")
    entry = next((row for row in entries if isinstance(row, dict) and row.get("page") == "RecognitionResult"), None)
    if entry is None:
        issues.append("RecognitionResult registry entry is missing")
    else:
        expected_entry = {
            "page_id": "recognition.result.v3_1",
            "contract_version": "V1",
            "status": "READY_FOR_IMPLEMENTATION",
            "implementation_status": "READY_FOR_IMPLEMENTATION",
            "design_contract": "PAGE_CONTRACT_READY",
            "design_contract_status": "FROZEN",
            "authority_source": PAGE_CONTRACT_PATH,
            "implementation_contract": CONTRACT_PATH,
            "state_binding": STATE_PATH,
            "component_mapping": MAPPING_PATH,
            "runtime_gate": GATE_PATH,
            "validation_status": "PASS",
            "runtime_validation_status": "NOT_RUN",
        }
        for key, expected in expected_entry.items():
            if entry.get(key) != expected:
                issues.append(f"RecognitionResult registry {key} must equal {expected!r}")
        for key in ("authority_source", "implementation_contract", "state_binding", "component_mapping", "runtime_gate", "validator"):
            require_file(entry.get(key), f"registry.{key}", issues)

    gate = page_registry.get("freeze_gate", {})
    if gate.get("gate") != "PAGE_CONTRACT_READY" or gate.get("status") != "PASS" or gate.get("frozen_pages") != 3 or gate.get("blocking_findings") != 0:
        issues.append("Phase 3-A Page Contract gate is not PAGE_CONTRACT_READY PASS")
    result_page = next((row for row in page_registry.get("pages", []) if row.get("page_id") == "recognition.result.v3_1"), None)
    if not result_page or result_page.get("status") != "FROZEN" or result_page.get("contract") != PAGE_CONTRACT_PATH:
        issues.append("Recognition Result Page Contract is not present and FROZEN")
    if layout.get("page_id") != "recognition.result.v3_1" or layout.get("status") != "FROZEN":
        issues.append("Recognition Result Layout Contract is not FROZEN")
    if binding.get("page_id") != "recognition.result.v3_1" or binding.get("canonical_state_name") != "RecognitionResultState":
        issues.append("State Binding does not define canonical RecognitionResultState")
    if binding.get("authority_source") != PAGE_CONTRACT_PATH or binding.get("page_state_machine") != PAGE_STATE_PATH:
        issues.append("State Binding authority does not resolve to the frozen Page Contract")

    state_rows = binding.get("states")
    state_ids = [row.get("id") for row in state_rows if isinstance(row, dict)] if isinstance(state_rows, list) else []
    if len(state_ids) != len(set(state_ids)) or set(state_ids) != REQUIRED_STATES:
        issues.append("RecognitionResultState IDs must exactly match the ten approved states")
    if FORBIDDEN_STATES & set(state_ids):
        issues.append(f"prohibited Android-private state(s): {sorted(FORBIDDEN_STATES & set(state_ids))}")
    canonical_ids = {row.get("id") for row in page_state.get("states", []) if isinstance(row, dict)}
    for row in state_rows if isinstance(state_rows, list) else []:
        mapped = row.get("page_state")
        if isinstance(mapped, str) and mapped not in canonical_ids:
            issues.append(f"{row.get('id')}: page_state {mapped} does not resolve")
        if isinstance(mapped, list) and any(item not in canonical_ids for item in mapped):
            issues.append(f"{row.get('id')}: one or more exit page_states do not resolve")
    for transition in binding.get("transitions", []):
        if transition.get("from") not in REQUIRED_STATES or transition.get("to") not in REQUIRED_STATES:
            issues.append("state binding transition references an unknown Android binding state")

    component_rows = components_doc.get("entries", [])
    component_by_id = {row.get("component_id"): row for row in component_rows if isinstance(row, dict)}
    asset_rows = assets_doc.get("entries", [])
    asset_by_id = {row.get("asset_id"): row for row in asset_rows if isinstance(row, dict)}
    tokens: dict[str, str] = {}
    for group, doc in token_docs.items():
        for token in doc.get("tokens", []):
            if isinstance(token, dict) and token.get("id"):
                if token["id"] in tokens:
                    issues.append(f"duplicate token ID across effective registries: {token['id']}")
                tokens[token["id"]] = TOKEN_PATHS[group]

    expected_components = {row.get("component"): row for row in layout.get("component_refs", []) if isinstance(row, dict)}
    mapped_rows = mapping.get("component_mapping")
    actual_components = [row.get("page_contract_component") for row in mapped_rows if isinstance(row, dict)] if isinstance(mapped_rows, list) else []
    if len(actual_components) != len(set(actual_components)) or set(actual_components) != set(expected_components):
        issues.append("Component Mapping must cover each Page Contract component exactly once")
    for row in mapped_rows if isinstance(mapped_rows, list) else []:
        cid = row.get("page_contract_component")
        source = expected_components.get(cid)
        component = component_by_id.get(cid)
        if source is None or component is None:
            issues.append(f"component mapping does not resolve: {cid}")
            continue
        if row.get("component_registry_id") != cid or component.get("status") != "FROZEN":
            issues.append(f"{cid}: registry ID or FROZEN status mismatch")
        if row.get("variant") != source.get("variant") or row.get("component_status") != source.get("component_status"):
            issues.append(f"{cid}: page variant/status mismatch")
        if row.get("layout_authority") != LAYOUT_PATH or row.get("component_authority") != COMPONENT_PATH:
            issues.append(f"{cid}: authority path mismatch")
        if set(row.get("token_refs", [])) != set(source.get("token_refs", [])):
            issues.append(f"{cid}: token refs differ from frozen Layout Contract")
        if set(row.get("asset_refs", [])[i].get("asset_id") for i in range(len(row.get("asset_refs", [])))) != set(source.get("asset_refs", [])):
            issues.append(f"{cid}: asset refs differ from frozen Layout Contract")
        if not row.get("android_implementation_owner"):
            issues.append(f"{cid}: Android owner is missing")
        for token_id in row.get("token_refs", []):
            if token_id not in tokens:
                issues.append(f"{cid}: token reference does not resolve: {token_id}")
        for asset_ref in row.get("asset_refs", []):
            aid = asset_ref.get("asset_id")
            asset = asset_by_id.get(aid)
            if not asset or asset.get("path") != asset_ref.get("path") or asset.get("sha256") != asset_ref.get("sha256") or not SHA256_RE.fullmatch(str(asset_ref.get("sha256", ""))):
                issues.append(f"{cid}: asset identity does not resolve through Asset Registry: {aid}")

    authority_row = next((row for row in authorities.get("entries", []) if row.get("authority_id") == "recognition_result_android_implementation"), None)
    if not authority_row or authority_row.get("canonical_path") != REGISTRY_PATH or authority_row.get("derived_from") != PAGE_CONTRACT_PATH or authority_row.get("implementation_status") != "READY_FOR_IMPLEMENTATION":
        issues.append("derived Android implementation authority is missing or does not point to the frozen Page Contract")
    else:
        for path in [authority_row.get("canonical_path"), *authority_row.get("supporting_paths", [])]:
            require_file(path, "Effective Authority Registry", issues)

    for path in [CONTRACT_PATH, GATE_PATH]:
        require_file(path, "contract document", issues)
    if (ROOT / CONTRACT_PATH).is_file():
        text = (ROOT / CONTRACT_PATH).read_text(encoding="utf-8")
        for heading in ("Authority Chain", "Screen Ownership", "State Binding", "Component Mapping", "Data Binding", "Runtime and Acceptance Boundary"):
            if f"## {heading}" not in text:
                issues.append(f"Android contract document is missing section: {heading}")
    if (ROOT / GATE_PATH).is_file():
        text = (ROOT / GATE_PATH).read_text(encoding="utf-8")
        for required in ("320dp", "360dp", "411dp", "fontScale", "TalkBack", "44×44dp", "Content Description", "Loading", "Error", "Empty", "Long species name", "Missing metadata"):
            if required not in text:
                issues.append(f"Runtime Gate is missing required criterion: {required}")
    if registry.get("validation_status") != "PASS" or (entry and entry.get("validation_status") != "PASS"):
        issues.append("registry validation_status must be PASS after structural validation")
    if registry.get("runtime_validation_status") != "NOT_RUN" or (entry and entry.get("runtime_validation_status") != "NOT_RUN"):
        issues.append("contract must not claim runtime validation")

    if issues:
        print(f"FAIL: {len(issues)} Android Implementation Contract issue(s)")
        for issue in issues:
            print(f" - {issue}")
        return 1
    print("PASS: Recognition Result Android Implementation Contract V1")
    print(f"  Page: recognition.result.v3_1 | Components mapped: {len(actual_components)} | Required states: {len(state_ids)}")
    print("  Authority, token and asset identity references resolve; Android project not inspected.")
    print("  Runtime validation: NOT_RUN")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, json.JSONDecodeError, ValueError) as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        raise SystemExit(1)
