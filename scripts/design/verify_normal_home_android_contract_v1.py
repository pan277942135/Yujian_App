#!/usr/bin/env python3
"""Validate Normal Home Android implementation-contract references only.

This validator intentionally does not inspect or build the Android project.
"""
from __future__ import annotations

import hashlib
import json
import re
import sys
from pathlib import Path, PurePosixPath
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
PAGE_REGISTRY_PATH = "design/system_v3/08_Page_Contracts/Page_Registry_V3_1.json"
ANDROID_REGISTRY_PATH = "design/system_v3/09_Android_Implementation_Contracts/Android_Implementation_Contract_Registry_V1.json"
AUTHORITY_PATH = "design/system_v3/11_Freeze_Governance/Effective_Authority_Registry_V3_1.json"
VISUAL_DIR = "design/system_v3/10_Page_Visual_Acceptance_Contracts/Normal_Home"
VISUAL_CONTRACT = f"{VISUAL_DIR}/Normal_Home_Visual_Contract_V1.md"
VISUAL_REGISTRY = f"{VISUAL_DIR}/Normal_Home_Visual_Contract_Registry_V1.json"
STATE_MATRIX = f"{VISUAL_DIR}/Normal_Home_State_Visual_Matrix_V1.json"
ANCHOR_MAP = f"{VISUAL_DIR}/Normal_Home_Component_Anchor_Map_V1.json"
BASE = "design/system_v3/09_Android_Implementation_Contracts/Normal_Home"
CONTRACT = f"{BASE}/Normal_Home_Android_Contract_V1.md"
MAPPING = f"{BASE}/Normal_Home_Android_Component_Mapping_V1.json"
STATE_BINDING = f"{BASE}/Normal_Home_State_Binding_V1.json"
COMPOSE = f"{BASE}/Normal_Home_Compose_Guideline_V1.md"
INSETS = f"{BASE}/Normal_Home_Window_Insets_Contract_V1.md"
RESOURCES = f"{BASE}/Normal_Home_Resource_Binding_V1.json"
MOTION = f"{BASE}/Normal_Home_Motion_Runtime_Binding_V1.md"
RUNTIME = f"{BASE}/Normal_Home_Runtime_Gate_V1.md"
README = f"{BASE}/README.md"
COMPONENTS = "design/system_v3/05_Component_System/Component_Registry_V3_FINAL.json"
ASSETS = "design/system_v3/06_Asset_System/Asset_Registry_V3_FINAL.json"
BACKGROUNDS = "design/system_v3/08_Background_System/Background_Registry_V3.json"
ICONS = "design/system_v3/09_Icon_System/Icon_Registry_V3.json"
AVATAR_MANIFEST = "design/pages/home/normal_home/07_avatar_states/assets/avatar_asset_manifest_v1.json"
TOKEN_PATHS = {
    "typography": "design/system_v3/04_Design_Tokens/Typography/Typography_Tokens_V3_FINAL.json",
    "color": "design/system_v3/04_Design_Tokens/Color/Color_Tokens_V3_FINAL.json",
    "geometry": "design/system_v3/07_Responsive_System/Geometry_Tokens_V3_FINAL.json",
}
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")
REQUIRED_OUTPUTS = [CONTRACT, MAPPING, STATE_BINDING, COMPOSE, INSETS, RESOURCES, MOTION, RUNTIME, README]
REQUIRED_CONTRACT_HEADINGS = [
    "Authority Chain", "Screen Ownership", "Component Mapping", "State Binding",
    "Compose Guideline", "Window Insets Contract", "Resource and Motion Binding",
    "Runtime and Acceptance Boundary",
]


def safe_path(value: Any) -> bool:
    if not isinstance(value, str) or not value:
        return False
    p = PurePosixPath(value)
    return not p.is_absolute() and all(part not in {"", ".", ".."} for part in p.parts)


def read_json(path: str) -> dict[str, Any]:
    if not safe_path(path):
        raise ValueError(f"unsafe path: {path!r}")
    value = json.loads((ROOT / path).read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"{path}: JSON root must be an object")
    return value


def require_file(path: Any, label: str, issues: list[str]) -> None:
    if not safe_path(path) or not (ROOT / str(path)).is_file():
        issues.append(f"{label}: missing/unsafe file {path!r}")


def main() -> int:
    issues: list[str] = []
    warnings: list[str] = []
    for path in REQUIRED_OUTPUTS + [
        PAGE_REGISTRY_PATH, ANDROID_REGISTRY_PATH, AUTHORITY_PATH, VISUAL_CONTRACT,
        VISUAL_REGISTRY, STATE_MATRIX, ANCHOR_MAP, COMPONENTS, ASSETS, BACKGROUNDS,
        ICONS, AVATAR_MANIFEST, *TOKEN_PATHS.values(),
    ]:
        require_file(path, "required contract dependency", issues)
    if issues:
        print(f"FAIL: {len(issues)} Normal Home Android contract file/dependency issue(s)")
        for issue in issues:
            print(f" - {issue}")
        return 1

    page_registry = read_json(PAGE_REGISTRY_PATH)
    android_registry = read_json(ANDROID_REGISTRY_PATH)
    authorities = read_json(AUTHORITY_PATH)
    visual_registry = read_json(VISUAL_REGISTRY)
    state_matrix = read_json(STATE_MATRIX)
    anchors_doc = read_json(ANCHOR_MAP)
    mapping = read_json(MAPPING)
    binding = read_json(STATE_BINDING)
    resource_doc = read_json(RESOURCES)
    component_doc = read_json(COMPONENTS)
    asset_doc = read_json(ASSETS)
    background_doc = read_json(BACKGROUNDS)
    icon_doc = read_json(ICONS)
    avatar_manifest = read_json(AVATAR_MANIFEST)
    token_docs = {name: read_json(path) for name, path in TOKEN_PATHS.items()}

    contract_text = (ROOT / CONTRACT).read_text(encoding="utf-8")
    runtime_text = (ROOT / RUNTIME).read_text(encoding="utf-8")
    manager_text = (ROOT / "design/manager/index.html").read_text(encoding="utf-8")
    for heading in REQUIRED_CONTRACT_HEADINGS:
        if f"## {heading}" not in contract_text:
            issues.append(f"Android contract is missing section: {heading}")
    runtime_text_folded = runtime_text.casefold()
    for required in ("320", "360", "411", "480", "fontScale", "TalkBack", "48×48dp",
                     "APK SHA-256", "Device/API", "Screenshot SHA-256",
                     "PASS", "FAIL", "BLOCKED_INFRA", "REVIEW_REQUIRED", "NOT_RUN"):
        if required.casefold() not in runtime_text_folded:
            issues.append(f"Runtime Gate is missing criterion: {required}")
    for required in ("Normal Home Android Contract V1", "Component Mapping",
                     "State Binding", "Runtime Gate", "READY_FOR_IMPLEMENTATION"):
        if required not in manager_text:
            issues.append(f"Design Manager is missing Normal Home implementation navigation: {required}")

    if visual_registry.get("status") != "READY_FOR_VISUAL_IMPLEMENTATION":
        issues.append("Normal Home Visual Contract is not registered READY_FOR_VISUAL_IMPLEMENTATION")
    visual_gate = visual_registry.get("visual_gate", {})
    if visual_gate.get("gate") != "NORMAL_HOME_VISUAL_CONTRACT_READY" or visual_gate.get("status") != "PASS":
        issues.append("Normal Home Visual Contract gate is not PASS")

    # Preserve the Phase 3-A frozen-page gate; Home is a separate implementation binding.
    phase3a_pages = page_registry.get("pages", [])
    phase3a_ids = [row.get("page_id") for row in phase3a_pages if isinstance(row, dict)]
    freeze_gate = page_registry.get("freeze_gate", {})
    if len(phase3a_pages) != 3 or set(phase3a_ids) != {
        "recognition.capture.v3_1", "recognition.processing.v3_1", "recognition.result.v3_1"
    }:
        issues.append("Page Contract Registry Phase 3-A frozen-page scope changed unexpectedly")
    if freeze_gate.get("gate") != "PAGE_CONTRACT_READY" or freeze_gate.get("status") != "PASS" or freeze_gate.get("frozen_pages") != 3:
        issues.append("Phase 3-A PAGE_CONTRACT_READY gate no longer matches its frozen 3-page scope")
    bindings = page_registry.get("implementation_contract_bindings", [])
    page_binding = next((row for row in bindings if isinstance(row, dict) and row.get("id") == "normal_home_v1"), None)
    expected_page_binding = {
        "id": "normal_home_v1",
        "visual_contract": VISUAL_CONTRACT,
        "android_contract": CONTRACT,
        "status": "READY_FOR_IMPLEMENTATION",
    }
    if page_binding is None:
        issues.append("Page Registry Normal Home implementation binding is missing")
    else:
        for key, expected in expected_page_binding.items():
            if page_binding.get(key) != expected:
                issues.append(f"Page Registry Normal Home {key} must equal {expected!r}")
        if page_binding.get("page_contract_status") != "NOT_STARTED_PHASE_3B":
            issues.append("Page Registry must keep the distinct Home Page Contract status explicit")

    entries = android_registry.get("entries", [])
    normal_entry = next((row for row in entries if isinstance(row, dict) and row.get("page_id") == "normal_home_v1"), None)
    if android_registry.get("registry_id") != "android_implementation_contract_registry_v1" or normal_entry is None:
        issues.append("Android Implementation Contract Registry is missing Normal Home")
    if android_registry.get("validation_status") != "PASS":
        issues.append("Android Implementation Contract Registry validation_status must be PASS")
    home_gate = android_registry.get("normal_home_gate", {})
    if home_gate.get("gate") != "NORMAL_HOME_ANDROID_CONTRACT_READY" or home_gate.get("status") != "PASS":
        issues.append("NORMAL_HOME_ANDROID_CONTRACT_READY must be PASS in the Android Registry")
    if normal_entry:
        expected = {
            "page": "NormalHome",
            "contract_version": "V1",
            "status": "READY_FOR_IMPLEMENTATION",
            "implementation_status": "READY_FOR_IMPLEMENTATION",
            "design_contract": "NORMAL_HOME_VISUAL_CONTRACT_READY",
            "design_contract_status": "READY_FOR_VISUAL_IMPLEMENTATION",
            "authority_source": VISUAL_CONTRACT,
            "implementation_contract": CONTRACT,
            "state_binding": STATE_BINDING,
            "component_mapping": MAPPING,
            "resource_binding": RESOURCES,
            "compose_guideline": COMPOSE,
            "window_insets_contract": INSETS,
            "motion_runtime_binding": MOTION,
            "runtime_gate": RUNTIME,
            "validation_status": "PASS",
            "runtime_validation_status": "NOT_RUN",
            "authority_id": "normal_home_android_implementation",
        }
        for key, expected_value in expected.items():
            if normal_entry.get(key) != expected_value:
                issues.append(f"Android Registry Normal Home {key} must equal {expected_value!r}")
    page_ids = [row.get("page_id") for row in entries if isinstance(row, dict)]
    if len(page_ids) != len(set(page_ids)):
        issues.append("Android Implementation Contract Registry contains duplicate page_id values")

    authority_entry = next((row for row in authorities.get("entries", [])
                            if isinstance(row, dict) and row.get("authority_id") == "normal_home_android_implementation"), None)
    if authority_entry is None:
        issues.append("Effective Authority Registry Normal Home Android authority is missing")
    else:
        if authority_entry.get("canonical_path") != ANDROID_REGISTRY_PATH:
            issues.append("Effective Authority Registry Normal Home canonical path mismatch")
        if authority_entry.get("derived_from") != VISUAL_CONTRACT:
            issues.append("Normal Home Android authority must derive from the Visual Contract")
        if authority_entry.get("status") != "READY_FOR_IMPLEMENTATION":
            issues.append("Normal Home Android authority is not READY_FOR_IMPLEMENTATION")
        for path in [authority_entry.get("canonical_path"), *authority_entry.get("supporting_paths", [])]:
            require_file(path, "Effective Authority Registry reference", issues)

    # Resolve shared tokens from the three effective authorities.
    token_ids: set[str] = set()
    for doc in token_docs.values():
        for row in doc.get("tokens", []):
            if isinstance(row, dict) and isinstance(row.get("id"), str):
                if row["id"] in token_ids:
                    issues.append(f"duplicate effective token ID: {row['id']}")
                token_ids.add(row["id"])

    component_rows = component_doc.get("entries", [])
    components = {row.get("component_id"): row for row in component_rows if isinstance(row, dict)}
    asset_rows = asset_doc.get("entries", [])
    assets_by_id = {row.get("asset_id"): row for row in asset_rows if isinstance(row, dict)}
    assets_by_path = {row.get("path"): row for row in asset_rows if isinstance(row, dict)}
    visual_legacy_ids = {
        row.get("component_id") for row in visual_registry.get("component_refs", [])
        if isinstance(row, dict) and row.get("component_id")
    }
    legacy_paths = {
        row.get("component_id"): row.get("registry_source")
        for row in mapping.get("legacy_shared_bindings", []) if isinstance(row, dict)
    }
    anchor_ids = {row.get("anchor_id") for row in anchors_doc.get("anchors", []) if isinstance(row, dict)}
    resource_rows = resource_doc.get("resources", [])
    resource_by_id = {row.get("asset_id"): row for row in resource_rows if isinstance(row, dict)}

    shared_ids = mapping.get("shared_component_bindings", [])
    if not shared_ids:
        issues.append("Component Mapping has no shared component bindings")
    for cid in shared_ids:
        component = components.get(cid)
        if component is None or component.get("status") != "FROZEN":
            issues.append(f"shared component binding does not resolve to a FROZEN registry row: {cid}")

    mapping_rows = mapping.get("component_mappings", [])
    if not mapping_rows:
        issues.append("Component Mapping has no component_mappings")
    layers: dict[tuple[str, str], set[str]] = {}
    for row in mapping_rows:
        if not isinstance(row, dict):
            issues.append("Component Mapping contains a malformed row")
            continue
        if not row.get("design_component") or not row.get("android_owner"):
            issues.append("Component Mapping row is missing design_component/android_owner")
        if row.get("source_anchor_id") not in anchor_ids:
            issues.append(f"Component Mapping anchor does not resolve: {row.get('source_anchor_id')}")
        cid = row.get("component_registry_id")
        legacy_id = row.get("legacy_component_id")
        if cid:
            component = components.get(cid)
            if component is None or component.get("status") != "FROZEN":
                issues.append(f"component ID is not a FROZEN Component Registry entry: {cid}")
            elif cid not in shared_ids:
                issues.append(f"mapped shared component is absent from shared_component_bindings: {cid}")
        if legacy_id:
            legacy_path = legacy_paths.get(legacy_id)
            if legacy_id not in visual_legacy_ids or not safe_path(legacy_path) or not (ROOT / str(legacy_path)).is_file():
                issues.append(f"legacy shared component is not resolvable through Visual Contract and its manifest: {legacy_id}")
        if not cid and not legacy_id and row.get("mapping_kind") not in {"page_visual_layer", "registered_text_primitive"}:
            issues.append(f"mapping row has no registered component or allowed page-layer/primitive type: {row.get('design_component')}")
        for token_id in row.get("token_refs", []):
            if token_id not in token_ids:
                issues.append(f"{row.get('design_component')}: unresolved token {token_id}")
        for aid in row.get("asset_refs", []):
            if aid not in resource_by_id or aid not in assets_by_id:
                issues.append(f"{row.get('design_component')}: unresolved Asset Registry resource {aid}")
        if row.get("layer"):
            layers.setdefault((row.get("design_area"), row.get("design_component")), set()).add(row["layer"])
    for area in ("NH01", "NH05"):
        found = any(key[0] == area and key[1] == "Camera CTA" and value == {"VISUAL", "LAYOUT", "TOUCH"}
                    for key, value in layers.items())
        if not found:
            issues.append(f"{area} Camera CTA must have separate VISUAL/LAYOUT/TOUCH mappings")
    statistic_variant = mapping.get("nh04_statistic_variant", {})
    if statistic_variant != {
        "component": "data.statistic_card",
        "source": COMPONENTS,
        "variant": "NH04",
    }:
        issues.append("NH04 statistic variant must use the registered data.statistic_card component")
    aliases = {"guest": "avatar_guest_default", "default": "avatar_default",
               "loading": "avatar_loading", "error": "avatar_error", "remote": "avatar_remote"}
    if mapping.get("nh07_avatar_asset_aliases") != aliases or binding.get("avatar_state_aliases") != aliases:
        issues.append("NH07 avatar asset aliases are incomplete or inconsistent")
    for row in mapping.get("legacy_shared_bindings", []):
        if isinstance(row, dict):
            require_file(row.get("registry_source"), "Legacy shared component registry", issues)

    # Resolve component token/asset dependencies to prevent hidden unregistered inputs.
    for cid in shared_ids:
        component = components.get(cid, {})
        for token_id in component.get("token_dependency", []):
            if token_id not in token_ids:
                issues.append(f"{cid}: Component Registry token dependency is unresolved: {token_id}")
        mapped_rows_for_component = [row for row in mapping_rows if isinstance(row, dict) and row.get("component_registry_id") == cid]
        mapped_tokens = {token for row in mapped_rows_for_component for token in row.get("token_refs", [])}
        missing_tokens = set(component.get("token_dependency", [])) - mapped_tokens
        if missing_tokens:
            issues.append(f"{cid}: Component Mapping omits registered token dependencies: {sorted(missing_tokens)}")
        mapped_assets = {aid for row in mapped_rows_for_component for aid in row.get("asset_refs", [])}
        for aid in component.get("asset_dependency", []):
            asset = assets_by_id.get(aid)
            if asset is None:
                issues.append(f"{cid}: Component Registry asset dependency is unresolved: {aid}")
            elif aid not in mapped_assets:
                issues.append(f"{cid}: Component Mapping omits registered asset dependency: {aid}")
            elif asset.get("path") != next((r.get("path") for r in resource_rows if r.get("asset_id") == aid), asset.get("path")):
                issues.append(f"{cid}: Component Registry asset/resource path mismatch for {aid}")

    # Check state coverage and exact catch/avatar vocabularies.
    visual_state_ids = {row.get("state") for row in state_matrix.get("states", []) if isinstance(row, dict)}
    expected_coverage = {"NH01", "NH02", "NH03", "NH04", "NH05", "NH06", "NH07"}
    if set(state_matrix.get("required_coverage_area_ids", [])) != expected_coverage:
        issues.append("Visual Contract NH01-NH07 coverage is incomplete")
    states = {row.get("state"): row for row in binding.get("states", []) if isinstance(row, dict)}
    if not {"EMPTY_HOME", "DATA_HOME"}.issubset(states):
        issues.append("State Binding must include EMPTY_HOME and DATA_HOME")
    if states.get("EMPTY_HOME", {}).get("visual_contract") != "NH01":
        issues.append("EMPTY_HOME must bind to visual contract NH01")
    if states.get("DATA_HOME", {}).get("visual_contract") != "NH02":
        issues.append("DATA_HOME must bind to visual contract NH02")
    catch_ids = {row.get("state") for row in binding.get("catch_states", []) if isinstance(row, dict)}
    if catch_ids != {"NO_CATCH", "HAS_CATCH", "MULTIPLE_CATCH"}:
        issues.append("Catch state binding must exactly cover NO_CATCH/HAS_CATCH/MULTIPLE_CATCH")
    avatar_ids = {row.get("state") for row in binding.get("avatar_states", []) if isinstance(row, dict)}
    if avatar_ids != {"GUEST", "DEFAULT", "LOADING", "ERROR", "REMOTE"}:
        issues.append("Avatar state binding must exactly cover GUEST/DEFAULT/LOADING/ERROR/REMOTE")
    visual_asset_ids = {row.get("asset_id") for row in visual_registry.get("assets", []) if isinstance(row, dict)}
    for avatar in binding.get("avatar_states", []):
        if isinstance(avatar, dict) and avatar.get("asset_id") and avatar.get("asset_id") not in visual_asset_ids:
            issues.append(f"Avatar state references an unknown Visual Contract asset ID: {avatar.get('asset_id')}")
    for mapped_source in ("HOME_EMPTY", "HOME_RESOLVING", "HOME_DATA_SINGLE", "HOME_DATA_MULTIPLE",
                          "HOME_REFRESH_PENDING_OR_ERROR_WITH_SNAPSHOT", "HOME_MEDIA_UNAVAILABLE_OR_RECOVERED"):
        if mapped_source not in visual_state_ids:
            issues.append(f"State Binding references missing Visual Contract state: {mapped_source}")
    for row in binding.get("states", []):
        if isinstance(row, dict):
            for source_state in row.get("source_states", [row.get("source_state")]):
                if source_state and source_state not in visual_state_ids:
                    issues.append(f"State Binding source state does not resolve: {source_state}")

    # Resolve every static asset by ID, path, declared SHA and actual bytes. Preserve warnings.
    for row in resource_rows:
        if not isinstance(row, dict):
            issues.append("Resource Binding contains a malformed resource row")
            continue
        aid, path, digest, status = row.get("asset_id"), row.get("path"), row.get("sha256"), row.get("status")
        if status == "RUNTIME_USER_MEDIA_NOT_STATIC":
            if path or digest:
                issues.append(f"{aid}: runtime media must not claim a static path or SHA")
            continue
        if not path and status == "RUNTIME_DATA":
            if digest:
                issues.append(f"{aid}: runtime data must not claim a static SHA")
            continue
        registry_row = assets_by_id.get(aid)
        if registry_row is None:
            if aid == "normal_home_guest_avatar" and status == "WARNING_NOT_REGISTERED_NON_BLOCKING":
                if not safe_path(path) or not (ROOT / str(path)).is_file():
                    issues.append("Guest avatar warning binding points to a missing file")
                elif not SHA256_RE.fullmatch(str(digest or "")) or hashlib.sha256((ROOT / path).read_bytes()).hexdigest() != digest:
                    issues.append("Guest avatar warning binding SHA-256 does not match actual bytes")
                else:
                    warnings.append("Guest avatar has an exact path/SHA but no V3 Asset Registry row.")
                continue
            issues.append(f"Resource asset ID is absent from Asset Registry: {aid}")
            continue
        if registry_row.get("path") != path or registry_row.get("sha256") != digest:
            issues.append(f"Resource path/SHA does not match Asset Registry for {aid}")
            continue
        file_path = ROOT / str(path)
        if file_path.is_symlink() or not file_path.is_file():
            issues.append(f"Resource file is missing or a symlink: {path}")
            continue
        actual = hashlib.sha256(file_path.read_bytes()).hexdigest()
        if actual != digest:
            issues.append(f"Resource raw-byte SHA mismatch: {path}")
        expected_registry_status = row.get("asset_registry_status", status)
        if registry_row.get("status") != expected_registry_status:
            issues.append(f"Resource Asset Registry status mismatch for {aid}")
        if registry_row.get("status") == "REVIEW":
            warnings.append(f"Resource keeps inherited REVIEW status: {aid}")

    for reference in resource_doc.get("frozen_visual_references", []):
        if not isinstance(reference, dict):
            issues.append("Frozen visual reference is malformed")
            continue
        row = assets_by_path.get(reference.get("path"))
        if row is None or row.get("sha256") != reference.get("sha256"):
            issues.append(f"Frozen visual reference path/SHA does not resolve through Asset Registry: {reference.get('asset_id')}")
        elif row.get("status") != reference.get("status"):
            issues.append(f"Frozen visual reference status differs from Asset Registry: {reference.get('asset_id')}")
        else:
            file_path = ROOT / reference["path"]
            if not file_path.is_file() or hashlib.sha256(file_path.read_bytes()).hexdigest() != reference["sha256"]:
                issues.append(f"Frozen visual reference SHA mismatch: {reference['path']}")

    # Check background role and icon identities against their own registries.
    background_rows = {row.get("background_id"): row for row in background_doc.get("backgrounds", []) if isinstance(row, dict)}
    bg = background_rows.get("BG_ENV_HERO")
    if not bg or bg.get("status") != "FROZEN":
        issues.append("BG_ENV_HERO is missing or not FROZEN")
    elif bg.get("master_path") != "design/system/backgrounds/morning_lake_v1/assets/Morning_Lake_Master_V1.png":
        issues.append("BG_ENV_HERO Normal Home master mapping is incorrect")
    page_override = bg.get("page_override", {}) if bg else {}
    if page_override.get("master_path") != "design/system/backgrounds/morning_lake_v1/assets/Morning_Lake_Sunrise_Hero_V1.png":
        issues.append("BG_ENV_HERO Empty Home sunrise override is unresolved")
    icon_rows = {row.get("icon_id"): row for row in icon_doc.get("entries", []) if isinstance(row, dict)}
    for icon_id in resource_doc.get("icon_ids", []):
        icon = icon_rows.get(icon_id)
        if not icon or icon.get("status") != "FROZEN":
            issues.append(f"Icon Registry entry is missing or not FROZEN: {icon_id}")
            continue
        resource = next((row for row in resource_rows if row.get("icon_id") == icon_id), None)
        if not resource or resource.get("path") != icon.get("file") or resource.get("sha256") != icon.get("sha256"):
            issues.append(f"Icon Resource Binding does not match Icon Registry: {icon_id}")
        if not SHA256_RE.fullmatch(str(icon.get("sha256", ""))):
            issues.append(f"Icon SHA-256 is invalid: {icon_id}")

    # NH07 exact alias/state map: the Guest omission is a warning, not a silent asset invention.
    if avatar_manifest.get("id") != "normal_home_avatar_default_nh07_v1":
        issues.append("NH07 avatar manifest identity does not resolve")
    if avatar_manifest.get("states", {}).get("GUEST") != "app/src/main/assets/normal_home_runtime_v1/avatar/guest_avatar.png":
        issues.append("NH07 Guest source path mismatch")
    guest = next((row for row in binding.get("avatar_states", []) if row.get("state") == "GUEST"), None)
    if not guest or guest.get("registry_status") != "WARNING_NOT_REGISTERED_NON_BLOCKING":
        issues.append("NH07 Guest missing registry row must be explicitly WARNING / NOT BLOCKING")
    if binding.get("runtime_status") != "NOT_RUN":
        issues.append("State Binding must not claim runtime validation")

    for required_path in REQUIRED_OUTPUTS:
        if not (ROOT / required_path).is_file():
            issues.append(f"contract output missing: {required_path}")
    if page_registry.get("implementation_contract_bindings") is None:
        issues.append("Page Registry implementation bindings are missing")
    if resource_doc.get("registries", {}).get("asset_registry") != "design/system_v3/06_Asset_System/Asset_Registry_V3_FINAL.json":
        issues.append("Resource Binding must name the effective Asset Registry")
    if not manager_text.find("Normal Home Android Contract V1") >= 0:
        issues.append("Design Manager does not expose Normal Home Android Contract V1")

    if issues:
        print(f"FAIL: {len(issues)} Normal Home Android contract issue(s)")
        for issue in issues:
            print(f" - {issue}")
        return 1
    print("PASS: Normal Home Android Implementation Contract V1")
    print(f"  Shared V3 components: {len(shared_ids)} | mapping rows: {len(mapping_rows)}")
    print(f"  Coverage areas: NH01-NH07 | catch states: {len(catch_ids)} | avatar states: {len(avatar_ids)}")
    print(f"  Static Asset Registry identities: {sum(1 for row in resource_rows if row.get('asset_id') in assets_by_id)}")
    print(f"  Warnings: {len(set(warnings))}")
    for warning in sorted(set(warnings)):
        print(f"  WARNING: {warning}")
    print("  Runtime validation: NOT_RUN; Android project not inspected.")
    print("  Scoped gate: NORMAL_HOME_ANDROID_CONTRACT_READY = PASS")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, json.JSONDecodeError, ValueError, TypeError) as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        raise SystemExit(1)
