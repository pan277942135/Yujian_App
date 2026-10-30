#!/usr/bin/env python3
"""Validate page-neutral responsive envelopes and reference-only page profiles."""

from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import re
import subprocess
import sys
from pathlib import Path
from typing import Any

try:
    import yaml
    from jsonschema import Draft202012Validator
except ImportError as exc:  # pragma: no cover - exercised by CLI environment check
    print("ERROR: install PyYAML and jsonschema before running this validator", file=sys.stderr)
    raise SystemExit(2) from exc


ROOT = Path(__file__).resolve().parents[2]
SYSTEM_DIR = "design/system_v3/07_Responsive_System"
ENVELOPE_SCHEMA_REL = f"{SYSTEM_DIR}/Page_Responsive_Envelope_Schema_V1.json"
PROFILE_SCHEMA_REL = f"{SYSTEM_DIR}/Page_Conformance_Profile_Schema_V1.json"
EXPERIENCE_REGISTRY_REL = "design/registry/experience_registry_v1.json"
FEATURE_MATRIX_REL = "harness/FEATURE_MATRIX.yaml"
UCR_REL = "harness/UNIFIED_CONTRACT_REGISTRY.yaml"
MATRIX_DEFAULT = f"{SYSTEM_DIR}/Page_Responsive_Contract_Schema_Validation_Matrix_V1.json"
ADAPTERS = {
    "nh01_responsive_v2": "scripts/design/page_contract_adapters/nh01_responsive_v2.py",
    "schema_only_draft": "scripts/design/page_contract_adapters/schema_only_draft.py",
}
HEX_SHA256 = re.compile(r"^[a-f0-9]{64}$")


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def as_dict(value: Any) -> dict:
    return value if isinstance(value, dict) else {}


def safe_repo_file(root: Path, relative: str) -> Path | None:
    if not isinstance(relative, str) or not relative:
        return None
    candidate = (root / relative).resolve()
    try:
        candidate.relative_to(root.resolve())
    except ValueError:
        return None
    return candidate if candidate.is_file() else None


def load_json(root: Path, relative: str) -> tuple[Any | None, str | None]:
    path = safe_repo_file(root, relative)
    if path is None:
        return None, f"missing or unsafe JSON path: {relative}"
    try:
        return json.loads(path.read_text(encoding="utf-8")), None
    except (OSError, json.JSONDecodeError) as exc:
        return None, f"cannot read JSON {relative}: {exc}"


def load_yaml(root: Path, relative: str) -> tuple[Any | None, str | None]:
    path = safe_repo_file(root, relative)
    if path is None:
        return None, f"missing or unsafe YAML path: {relative}"
    try:
        return yaml.safe_load(path.read_text(encoding="utf-8")), None
    except (OSError, yaml.YAMLError) as exc:
        return None, f"cannot read YAML {relative}: {exc}"


def json_pointer_errors(schema: dict, instance: Any, prefix: str) -> list[dict[str, str]]:
    try:
        Draft202012Validator.check_schema(schema)
    except Exception as exc:
        return [{"code": "SCHEMA_DEFINITION_INVALID", "message": f"{prefix}: {exc}"}]
    errors = []
    for error in Draft202012Validator(schema).iter_errors(instance):
        path = ".".join(str(part) for part in error.absolute_path) or "<root>"
        errors.append({"code": "SCHEMA_VALIDATION_FAILED", "message": f"{prefix} at {path}: {error.message}"})
    return errors


def flatten_strings(value: Any) -> set[str]:
    found: set[str] = set()
    if isinstance(value, str):
        found.add(value)
    elif isinstance(value, dict):
        for child in value.values():
            found.update(flatten_strings(child))
    elif isinstance(value, list):
        for child in value:
            found.update(flatten_strings(child))
    return found


def feature_record(root: Path, page_id: str) -> tuple[dict | None, dict | None, dict | None, list[str]]:
    errors: list[str] = []
    experience, error = load_json(root, EXPERIENCE_REGISTRY_REL)
    if error:
        errors.append(error)
    feature_matrix, error = load_yaml(root, FEATURE_MATRIX_REL)
    if error:
        errors.append(error)
    ucr, error = load_yaml(root, UCR_REL)
    if error:
        errors.append(error)
    if errors:
        return None, None, None, errors
    registry_feature = next((item for item in experience.get("features", []) if item.get("id") == page_id), None)
    matrix_feature = feature_matrix.get("features", {}).get(page_id)
    if registry_feature is None or matrix_feature is None:
        return registry_feature, matrix_feature, ucr, [f"page id is not registered in both Experience Registry and Feature Matrix: {page_id}"]
    return registry_feature, matrix_feature, ucr, []


def registry_authorities(root: Path, page_id: str, registry_feature: dict, matrix_feature: dict, ucr: dict) -> set[str]:
    shared_contract_paths = flatten_strings(ucr.get("shared_contracts", {}))
    authorized = set(shared_contract_paths)
    authorized.update(flatten_strings(registry_feature))
    authorized.update(flatten_strings(matrix_feature))
    page_set = ucr.get("page_contract_sets", {}).get(page_id, {})
    authorized.update(flatten_strings(page_set))
    owner = registry_feature.get("owner_path", "")
    direct_page_authorities = {
        modality.get("authority")
        for modality in registry_feature.get("modalities", {}).values()
        if isinstance(modality, dict) and isinstance(modality.get("authority"), str)
    }
    for version in registry_feature.get("design_versions", []):
        if isinstance(version, dict):
            for key in ("visual_authority", "spec_authority"):
                if isinstance(version.get(key), str):
                    direct_page_authorities.add(version[key])
    for modality in registry_feature.get("modalities", {}).values():
        manifest_rel = modality.get("authority") if isinstance(modality, dict) else None
        manifest = safe_repo_file(root, manifest_rel) if isinstance(manifest_rel, str) else None
        if manifest is None or manifest.suffix.lower() != ".json":
            continue
        try:
            manifest_data = json.loads(manifest.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            continue
        base = Path(manifest_rel).parent.as_posix()
        for name in manifest_data.get("current_contracts", []):
            if isinstance(name, str):
                authorized.add(f"{base}/{name}")
        for name in manifest_data.get("contract_sha256", {}):
            if isinstance(name, str):
                authorized.add(f"{base}/{name}")
    return {
        item
        for item in authorized
        if item.startswith(f"{owner}/") or item in shared_contract_paths or item in direct_page_authorities
    }


def check_reference_bindings(envelope: dict, profile: dict, errors: list[dict[str, str]]) -> None:
    envelope_authorities = envelope.get("authority_refs", [])
    authority_ids = {
        item.get("authority_ref_id")
        for item in (envelope_authorities if isinstance(envelope_authorities, list) else [])
        if isinstance(item, dict)
    }
    profile_authorities = profile.get("authority_bindings", [])
    for item in profile_authorities if isinstance(profile_authorities, list) else []:
        if not isinstance(item, dict):
            continue
        if item.get("authority_ref_id") not in authority_ids:
            errors.append({"code": "PROFILE_AUTHORITY_BINDING_UNRESOLVED", "message": f"profile authority binding is absent from envelope: {item.get('authority_ref_id')}"})
    for section, entries in as_dict(profile.get("conformance")).items():
        for index, entry in enumerate(entries if isinstance(entries, list) else []):
            if not isinstance(entry, dict):
                continue
            entry_bindings = entry.get("authority_bindings", [])
            for binding in entry_bindings if isinstance(entry_bindings, list) else []:
                if not isinstance(binding, dict):
                    continue
                if binding.get("authority_ref_id") not in authority_ids:
                    errors.append({"code": "PROFILE_AUTHORITY_BINDING_UNRESOLVED", "message": f"conformance binding {section}[{index}] is absent from envelope: {binding.get('authority_ref_id')}"})
    safe_ref = as_dict(as_dict(envelope.get("safe_area")).get("policy_ref")).get("authority_ref_id")
    if safe_ref not in authority_ids:
        errors.append({"code": "SAFE_AREA_AUTHORITY_UNRESOLVED", "message": f"safe-area policy authority is absent from envelope: {safe_ref}"})
    viewport_ref = as_dict(envelope.get("viewport_inputs")).get("viewport_authority_ref_id")
    if viewport_ref not in authority_ids:
        errors.append({"code": "VIEWPORT_AUTHORITY_UNRESOLVED", "message": f"viewport authority is absent from envelope: {viewport_ref}"})
    state_scope = as_dict(envelope.get("state_scope"))
    for field in ("included_state_refs", "excluded_scope_refs"):
        bindings = state_scope.get(field, [])
        for index, binding in enumerate(bindings if isinstance(bindings, list) else []):
            if isinstance(binding, dict) and binding.get("authority_ref_id") not in authority_ids:
                errors.append({"code": "STATE_SCOPE_AUTHORITY_UNRESOLVED", "message": f"state_scope.{field}[{index}] authority is absent from envelope: {binding.get('authority_ref_id')}"})


def numeric_values(value: Any, location: str = "profile") -> list[str]:
    found: list[str] = []
    if isinstance(value, bool) or value is None:
        return found
    if isinstance(value, (int, float)):
        found.append(location)
    elif isinstance(value, dict):
        for key, child in value.items():
            found.extend(numeric_values(child, f"{location}.{key}"))
    elif isinstance(value, list):
        for index, child in enumerate(value):
            found.extend(numeric_values(child, f"{location}[{index}]"))
    return found


def validate_contract(
    envelope: dict | None,
    profile: dict | None,
    root: Path = ROOT,
    *,
    validate_bound_files: bool = True,
    run_adapter: bool = True,
    profile_source_path: str | None = None,
) -> dict:
    errors: list[dict[str, str]] = []
    adapter_result: dict | None = None
    if not isinstance(envelope, dict):
        return {"status": "FAIL", "errors": [{"code": "ENVELOPE_MISSING", "message": "envelope document is missing or not an object"}], "adapter": None, "formal_acceptance": "NOT_VERIFIED"}
    if profile is not None and not isinstance(profile, dict):
        errors.append({"code": "PROFILE_INVALID", "message": "profile document must be a JSON object"})
        profile = None

    if envelope.get("status") not in {"DRAFT", "PROPOSED_FOR_CANONICAL_MAIN_REVIEW", "SPEC_FROZEN", "DEPRECATED"}:
        errors.append({"code": "INVALID_CONTRACT_STATUS", "message": f"illegal envelope status: {envelope.get('status')}"})
    if profile is None:
        errors.append({"code": "PROFILE_MISSING", "message": "referenced page conformance profile was not supplied or could not be loaded"})

    envelope_schema, schema_error = load_json(root, ENVELOPE_SCHEMA_REL)
    if schema_error:
        errors.append({"code": "ENVELOPE_SCHEMA_UNAVAILABLE", "message": schema_error})
    profile_schema, schema_error = load_json(root, PROFILE_SCHEMA_REL)
    if schema_error:
        errors.append({"code": "PROFILE_SCHEMA_UNAVAILABLE", "message": schema_error})
    if envelope_schema is not None:
        errors.extend(json_pointer_errors(envelope_schema, envelope, "envelope"))

    if envelope.get("schema_ref") != ENVELOPE_SCHEMA_REL:
        errors.append({"code": "WRONG_ENVELOPE_SCHEMA_REF", "message": f"schema_ref must equal {ENVELOPE_SCHEMA_REL}"})

    declared_authorities = envelope.get("authority_refs", [])
    declared_authorities = declared_authorities if isinstance(declared_authorities, list) else []
    visual_authorities = [item for item in declared_authorities if isinstance(item, dict) and item.get("role") == "PAGE_VISUAL_AUTHORITY"]
    if len(visual_authorities) != 1:
        errors.append({"code": "VISUAL_AUTHORITY_BINDING_INVALID", "message": "envelope must declare exactly one registered visual authority path and SHA-256"})

    validation_bindings = as_dict(envelope.get("validation_bindings"))
    expected_validation_bindings = {
        "envelope_schema": ENVELOPE_SCHEMA_REL,
        "profile_schema": PROFILE_SCHEMA_REL,
        "generic_validator": "scripts/design/validate_page_responsive_contract.py",
        "gate_id": "G-PAGE-FREEZE-PIPELINE-W1-CONTRACT",
    }
    for key, expected in expected_validation_bindings.items():
        if validation_bindings.get(key) != expected:
            errors.append({"code": "VALIDATION_BINDING_MISMATCH", "message": f"validation_bindings.{key} must equal {expected}"})

    if profile is not None:
        if profile_schema is not None:
            errors.extend(json_pointer_errors(profile_schema, profile, "profile"))
        if profile.get("schema_ref") != PROFILE_SCHEMA_REL:
            errors.append({"code": "WRONG_PROFILE_SCHEMA_REF", "message": f"profile schema_ref must equal {PROFILE_SCHEMA_REL}"})
        page = as_dict(envelope.get("page"))
        if profile.get("page_id") != page.get("page_id"):
            errors.append({"code": "PAGE_PROFILE_ID_MISMATCH", "message": "envelope page.page_id and profile.page_id differ"})
        profile_ref = as_dict(envelope.get("conformance_profile_ref"))
        if profile.get("profile_id") != profile_ref.get("profile_id"):
            errors.append({"code": "PROFILE_ID_MISMATCH", "message": "envelope profile reference and profile.profile_id differ"})
        if profile_source_path is not None and profile_ref.get("path") != profile_source_path:
            errors.append({"code": "PROFILE_PATH_MISMATCH", "message": f"loaded profile path {profile_source_path} differs from envelope binding {profile_ref.get('path')}"})
        if profile.get("status") == "DRAFT" and envelope.get("status") != "DRAFT":
            errors.append({"code": "PROFILE_STATUS_MISMATCH", "message": "draft profile cannot be bound to a non-draft envelope"})
        if envelope.get("status") == "DRAFT" and any(value == "PASS" for value in as_dict(envelope.get("acceptance")).values()):
            errors.append({"code": "DRAFT_ACCEPTANCE_CANNOT_PASS", "message": "draft envelope cannot claim acceptance PASS"})
        evidence_refs = envelope.get("evidence_refs", [])
        evidence_refs = evidence_refs if isinstance(evidence_refs, list) else []
        for evidence in evidence_refs:
            if not isinstance(evidence, dict):
                continue
            if evidence.get("status") == "PASS":
                source_sha = evidence.get("source_sha")
                if not isinstance(source_sha, str) or not re.fullmatch(r"[a-f0-9]{40}", source_sha) or not evidence.get("run_id") or not evidence.get("path"):
                    errors.append({"code": "EVIDENCE_BINDING_INCOMPLETE", "message": f"PASS evidence {evidence.get('evidence_id')} must bind a 40-character source SHA, Run ID, and report path"})
        acceptance = as_dict(envelope.get("acceptance"))
        for domain, status in acceptance.items():
            if status != "PASS":
                continue
            matching = [item for item in evidence_refs if isinstance(item, dict) and item.get("evidence_type") == domain and item.get("status") == "PASS"]
            if not matching or any(not re.fullmatch(r"[a-f0-9]{40}", str(item.get("source_sha", ""))) or not item.get("run_id") or not item.get("path") for item in matching):
                errors.append({"code": "ACCEPTANCE_EVIDENCE_INCOMPLETE", "message": f"acceptance domain {domain} cannot PASS without matching exact-SHA, Run ID, and report-path evidence"})

        numeric = numeric_values(profile)
        if numeric:
            errors.append({"code": "DUPLICATE_NUMERIC_AUTHORITY", "message": "page profile contains executable numeric values: " + ", ".join(numeric)})

        numeric_authority = as_dict(profile.get("numeric_authority"))
        authority_refs = envelope.get("authority_refs", [])
        numeric_refs = [item for item in authority_refs if isinstance(item, dict) and item.get("role") == "RESPONSIVE_NUMERIC_AUTHORITY"] if isinstance(authority_refs, list) else []
        if numeric_authority.get("status") == "BOUND_SINGLE":
            if len(numeric_refs) != 1 or numeric_refs[0].get("authority_ref_id") != numeric_authority.get("authority_ref_id"):
                errors.append({"code": "DUPLICATE_NUMERIC_AUTHORITY", "message": "bound profile must identify exactly one matching responsive numeric authority"})
        elif numeric_authority.get("status") == "UNBOUND_DRAFT":
            if numeric_refs or numeric_authority.get("authority_ref_id") is not None:
                errors.append({"code": "DUPLICATE_NUMERIC_AUTHORITY", "message": "unbound draft profile cannot declare a numeric authority reference"})
        if profile.get("status") == "FROZEN_BINDING" and numeric_authority.get("status") != "BOUND_SINGLE":
            errors.append({"code": "NUMERIC_AUTHORITY_REQUIRED", "message": "frozen profile must bind one page-owned numeric authority"})

        authority_ids_in_envelope = [item.get("authority_ref_id") for item in envelope.get("authority_refs", []) if isinstance(item, dict)]
        if len(authority_ids_in_envelope) != len(set(authority_ids_in_envelope)):
            errors.append({"code": "DUPLICATE_AUTHORITY_ID", "message": "envelope authority_ref_id values must be unique"})

        check_reference_bindings(envelope, profile, errors)

    page = as_dict(envelope.get("page"))
    page_id = page.get("page_id")
    if isinstance(page_id, str) and page_id:
        registry_feature, matrix_feature, ucr, registration_errors = feature_record(root, page_id)
        for message in registration_errors:
            errors.append({"code": "PAGE_REGISTRATION_INVALID", "message": message})
        if registry_feature is not None and matrix_feature is not None and ucr is not None:
            registered_visual = as_dict(as_dict(registry_feature.get("modalities")).get("visual")).get("authority")
            if len(visual_authorities) == 1 and visual_authorities[0].get("path") != registered_visual:
                errors.append({"code": "VISUAL_AUTHORITY_REGISTRY_MISMATCH", "message": f"visual authority path differs from Experience Registry: {visual_authorities[0].get('path')} != {registered_visual}"})
            route = page.get("route")
            route_record = {}
            route_owner, route_error = load_yaml(root, FEATURE_MATRIX_REL)
            if route_error:
                errors.append({"code": "ROUTE_REGISTRY_UNAVAILABLE", "message": route_error})
            else:
                route_record = route_owner.get("routes", {}).get(route, {})
                owners = route_record.get("owner_features", [])
                if page_id not in owners:
                    errors.append({"code": "ROUTE_OWNER_MISMATCH", "message": f"route {route!r} is not registered to page {page_id!r}"})
            if page.get("owner_path") != registry_feature.get("owner_path"):
                errors.append({"code": "PAGE_OWNER_MISMATCH", "message": "envelope owner_path differs from Experience Registry"})
            allowed_entrypoints = {
                entry
                for entry in [
                    matrix_feature.get("implementation", {}).get("runtime_authority"),
                    matrix_feature.get("implementation", {}).get("layout_policy"),
                    route_record.get("source"),
                    *[modality.get("authority") for modality in matrix_feature.get("registry_modalities", {}).values()],
                ]
                if isinstance(entry, str)
            }
            entrypoints = page.get("implementation_entrypoints", [])
            for entry in entrypoints if isinstance(entrypoints, list) else []:
                if not isinstance(entry, dict):
                    continue
                if entry.get("path") not in allowed_entrypoints:
                    errors.append({"code": "IMPLEMENTATION_ENTRYPOINT_UNREGISTERED", "message": f"entrypoint is not declared by the page registry: {entry.get('path')}"})
                elif safe_repo_file(root, entry.get("path")) is None:
                    errors.append({"code": "IMPLEMENTATION_FILE_MISSING", "message": f"registered implementation entrypoint is missing or unsafe: {entry.get('path')}"})

            allowed_authorities = registry_authorities(root, page_id, registry_feature, matrix_feature, ucr)
            authority_refs = envelope.get("authority_refs", [])
            for authority in authority_refs if isinstance(authority_refs, list) else []:
                if not isinstance(authority, dict):
                    continue
                path_text = authority.get("path")
                if path_text not in allowed_authorities:
                    errors.append({"code": "AUTHORITY_NOT_REGISTERED", "message": f"authority is not present in the page or shared authority registry: {path_text}"})
                    continue
                if validate_bound_files:
                    path = safe_repo_file(root, path_text)
                    if path is None:
                        errors.append({"code": "AUTHORITY_FILE_MISSING", "message": f"authority file is missing or unsafe: {path_text}"})
                    elif not HEX_SHA256.fullmatch(str(authority.get("sha256", ""))) or sha256_file(path) != authority.get("sha256"):
                        errors.append({"code": "AUTHORITY_SHA_MISMATCH", "message": f"authority SHA-256 does not match bytes: {path_text}"})

    profile_ref = as_dict(envelope.get("conformance_profile_ref"))
    if validate_bound_files:
        profile_path_text = profile_ref.get("path")
        profile_path = safe_repo_file(root, profile_path_text) if isinstance(profile_path_text, str) else None
        if profile_path is None:
            errors.append({"code": "PROFILE_FILE_MISSING", "message": f"profile file is missing or unsafe: {profile_path_text}"})
        elif not HEX_SHA256.fullmatch(str(profile_ref.get("sha256", ""))) or sha256_file(profile_path) != profile_ref.get("sha256"):
            errors.append({"code": "PROFILE_SHA_MISMATCH", "message": f"profile SHA-256 does not match bytes: {profile_path_text}"})
        elif profile is not None:
            try:
                bound_profile = json.loads(profile_path.read_text(encoding="utf-8"))
            except (OSError, json.JSONDecodeError) as exc:
                errors.append({"code": "PROFILE_FILE_INVALID", "message": f"cannot load bound profile {profile_path_text}: {exc}"})
            else:
                if bound_profile != profile:
                    errors.append({"code": "PROFILE_CONTENT_BINDING_MISMATCH", "message": f"loaded profile content differs from the bytes bound by the envelope: {profile_path_text}"})

    resource_refs = envelope.get("resource_binding_refs", [])
    if isinstance(resource_refs, list) and any("sha256" in item or "publication_permission" in item for item in resource_refs if isinstance(item, dict)):
        errors.append({"code": "W2_VALIDATION_BOUNDARY_VIOLATION", "message": "resource SHA and publication permission validation belongs to W2"})

    if not errors and run_adapter:
        binding = as_dict(envelope.get("validator_binding"))
        adapter_id = binding.get("adapter_id")
        expected_path = ADAPTERS.get(adapter_id)
        if expected_path is None or binding.get("adapter_path") != expected_path:
            errors.append({"code": "VALIDATOR_ADAPTER_UNREGISTERED", "message": f"validator adapter is not in the allowlist: {adapter_id} / {binding.get('adapter_path')}"})
        else:
            adapter_path = safe_repo_file(root, expected_path)
            if adapter_path is None:
                errors.append({"code": "VALIDATOR_ADAPTER_MISSING", "message": f"validator adapter file is missing: {expected_path}"})
            else:
                spec = importlib.util.spec_from_file_location(f"page_adapter_{adapter_id}", adapter_path)
                if spec is None or spec.loader is None:
                    errors.append({"code": "VALIDATOR_ADAPTER_LOAD_FAILED", "message": f"cannot load adapter module: {expected_path}"})
                else:
                    module = importlib.util.module_from_spec(spec)
                    try:
                        spec.loader.exec_module(module)
                        adapter_result = module.validate(envelope, profile or {}, root)
                    except Exception as exc:  # adapter failures are evidence, never a pass
                        adapter_result = {"status": "FAIL", "formal_acceptance": "NOT_VERIFIED", "errors": [f"adapter exception: {exc}"]}
                    if not isinstance(adapter_result, dict):
                        adapter_result = {"status": "FAIL", "formal_acceptance": "NOT_VERIFIED", "errors": ["adapter result must be an object"]}
                    if adapter_result.get("status") == "FAIL":
                        errors.append({"code": "PAGE_ADAPTER_FAILED", "message": json.dumps(adapter_result, ensure_ascii=False, sort_keys=True)})

    return {
        "status": "FAIL" if errors else "PASS",
        "errors": errors,
        "adapter": adapter_result,
        "formal_acceptance": "NOT_VERIFIED",
        "resource_validation_owner": "W2",
    }


def current_source_sha(root: Path) -> str | None:
    try:
        result = subprocess.run(["git", "rev-parse", "HEAD"], cwd=root, check=True, capture_output=True, text=True)
    except (OSError, subprocess.CalledProcessError):
        return None
    return result.stdout.strip()


def run_matrix(root: Path, matrix_path: str, expected_source_sha: str | None) -> dict:
    matrix, error = load_json(root, matrix_path)
    if error:
        return {"status": "FAIL", "errors": [{"code": "MATRIX_UNAVAILABLE", "message": error}], "cases": []}
    source_sha = current_source_sha(root)
    errors: list[dict[str, str]] = []
    if expected_source_sha and source_sha != expected_source_sha:
        errors.append({"code": "SOURCE_SHA_MISMATCH", "message": f"expected {expected_source_sha}, checked out {source_sha}"})
    cases = []
    for case in matrix.get("cases", []):
        envelope, envelope_error = load_json(root, case.get("envelope", ""))
        profile, profile_error = load_json(root, case.get("profile", ""))
        if envelope_error or profile_error:
            result = {
                "status": "FAIL",
                "errors": [{"code": "FIXTURE_UNAVAILABLE", "message": msg} for msg in (envelope_error, profile_error) if msg],
                "adapter": None,
                "formal_acceptance": "NOT_VERIFIED",
            }
        else:
            result = validate_contract(
                envelope,
                profile,
                root,
                validate_bound_files=True,
                run_adapter=True,
                profile_source_path=case.get("profile"),
            )
        case_result = {
            "case_id": case.get("case_id"),
            "expected_page_id": case.get("page_id"),
            "actual_page_id": (envelope or {}).get("page", {}).get("page_id"),
            **result,
        }
        if case_result["actual_page_id"] != case_result["expected_page_id"]:
            case_result["status"] = "FAIL"
            case_result["errors"].append({"code": "FIXTURE_ID_MISMATCH", "message": "matrix page id does not match envelope page id"})
        cases.append(case_result)
        if case_result["status"] != "PASS":
            errors.extend({"code": err["code"], "message": f"{case_result['case_id']}: {err['message']}"} for err in case_result["errors"])
    return {
        "status": "FAIL" if errors else "PASS",
        "gate_id": "G-PAGE-FREEZE-PIPELINE-W1-CONTRACT",
        "source_sha": source_sha,
        "expected_source_sha": expected_source_sha,
        "workflow_run_id": __import__("os").environ.get("GITHUB_RUN_ID"),
        "cases": cases,
        "errors": errors,
        "page_acceptance": "NOT_VERIFIED",
        "interpretation": "PASS means envelope/profile schema and registered adapter checks passed; it is not browser, human, or native page acceptance.",
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--matrix", default=MATRIX_DEFAULT)
    parser.add_argument("--expected-source-sha")
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    root = args.root.resolve()
    report = run_matrix(root, args.matrix, args.expected_source_sha)
    encoded = json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    if args.report:
        report_path = args.report if args.report.is_absolute() else root / args.report
        report_path.parent.mkdir(parents=True, exist_ok=True)
        report_path.write_text(encoded, encoding="utf-8")
    print(encoded, end="")
    return 0 if report["status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
