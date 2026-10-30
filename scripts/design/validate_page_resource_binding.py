#!/usr/bin/env python3
"""Resolve and verify page-owned visual resource bindings without copying assets."""

from __future__ import annotations

import argparse
import hashlib
import json
import mimetypes
import re
import subprocess
import sys
from pathlib import Path, PurePosixPath
from typing import Any

from jsonschema import Draft202012Validator
from PIL import Image


ROOT = Path(__file__).resolve().parents[2]
SCHEMA_PATH = ROOT / "design/system_v3/06_Asset_System/Page_Resource_Binding_Schema_V1.json"
EXPERIENCE_REGISTRY_PATH = "design/registry/experience_registry_v1.json"
PAGES_WORKFLOW_PATH = ".github/workflows/design-manager-pages.yml"
PRIVATE_REFERENCE_MANIFEST_PATH = "design/pages/recognition/web/VISUAL_REFERENCE_MANIFEST_V1.json"
PRIVATE_INPUT_MANIFEST_PATH = "design/pages/recognition/web/implementation_input_pack/SWOS_WEB_IMPLEMENTATION_INPUT_MANIFEST_V1.json"
PAGES_SCOPE_MARKER = "cp -R design _site/design"
DEFAULT_REQUIRED_PAGE_IDS = ("home_normal_v1", "home_empty_v2", "auth_login_v2")


class BindingError(Exception):
    pass


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_json(path: Path) -> Any:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise BindingError(f"cannot read JSON {path}: {exc}") from exc


def safe_repo_path(root: Path, value: str) -> Path:
    posix = PurePosixPath(value)
    if posix.is_absolute() or ".." in posix.parts or "\\" in value:
        raise BindingError(f"unsafe repository path: {value}")
    target = root.joinpath(*posix.parts)
    try:
        target.resolve(strict=False).relative_to(root.resolve())
    except ValueError as exc:
        raise BindingError(f"path escapes repository root: {value}") from exc
    return target


def json_pointer(value: Any, pointer: str) -> Any:
    if pointer == "":
        return value
    if not pointer.startswith("/"):
        raise BindingError(f"invalid JSON Pointer: {pointer}")
    current = value
    for raw_part in pointer[1:].split("/"):
        part = raw_part.replace("~1", "/").replace("~0", "~")
        if isinstance(current, list):
            try:
                current = current[int(part)]
            except (ValueError, IndexError) as exc:
                raise BindingError(f"JSON Pointer target missing: {pointer}") from exc
        elif isinstance(current, dict) and part in current:
            current = current[part]
        else:
            raise BindingError(f"JSON Pointer target missing: {pointer}")
    return current


def nested_value(value: Any, dotted_path: str) -> Any:
    if dotted_path == "":
        return value
    current = value
    for part in dotted_path.split("."):
        if isinstance(current, dict) and part in current:
            current = current[part]
        else:
            raise BindingError(f"binding field missing: {dotted_path}")
    return current


def detect_media_type(path: Path, data: bytes) -> str:
    suffix = path.suffix.lower()
    if data.startswith(b"\x89PNG\r\n\x1a\n"):
        return "image/png"
    if data.startswith(b"\xff\xd8\xff"):
        return "image/jpeg"
    if len(data) >= 12 and data[:4] == b"RIFF" and data[8:12] == b"WEBP":
        return "image/webp"
    if data.lstrip().startswith(b"<svg") or (b"<svg" in data[:512] and suffix == ".svg"):
        return "image/svg+xml"
    if suffix == ".kt":
        return "text/x-kotlin"
    if suffix == ".json":
        return "application/json"
    if suffix == ".md":
        return "text/markdown"
    return mimetypes.guess_type(path.name)[0] or "application/octet-stream"


def image_dimensions(path: Path, media_type: str) -> dict[str, int] | None:
    if not media_type.startswith("image/") or media_type == "image/svg+xml":
        return None
    try:
        with Image.open(path) as image:
            return {"width": int(image.width), "height": int(image.height)}
    except Exception as exc:  # Pillow raises format-specific exceptions.
        raise BindingError(f"image decode failed for {path}: {exc}") from exc


def experience_pages(root: Path) -> tuple[dict[str, dict[str, Any]], dict[str, Any]]:
    registry = load_json(safe_repo_path(root, EXPERIENCE_REGISTRY_PATH))
    features = registry.get("features")
    if not isinstance(features, list):
        raise BindingError("Experience Registry features list is missing")
    by_id = {row.get("id"): row for row in features if isinstance(row, dict) and row.get("id")}
    return by_id, registry


def binding_path_for(page: dict[str, Any]) -> str:
    owner_path = page.get("owner_path")
    if not isinstance(owner_path, str) or not owner_path:
        raise BindingError("registered page owner_path is missing")
    return f"{owner_path}/Page_Resource_Binding_V1.json"


def page_binding_candidates(root: Path) -> list[tuple[str, dict[str, Any], Path]]:
    pages, _ = experience_pages(root)
    result = []
    for page_id, page in pages.items():
        binding_path = safe_repo_path(root, binding_path_for(page))
        if binding_path.is_file():
            result.append((page_id, page, binding_path))
    return result


def find_binding(root: Path, requested_page_id: str) -> tuple[dict[str, Any], Path, dict[str, Any]]:
    pages, _ = experience_pages(root)
    for canonical_id, page in pages.items():
        path = safe_repo_path(root, binding_path_for(page))
        if not path.is_file():
            continue
        binding = load_json(path)
        if requested_page_id in [canonical_id, *binding.get("page_id_aliases", [])]:
            return binding, path, page
    raise BindingError(f"no page binding registered for page_id={requested_page_id}")


def schema_errors(root: Path, binding: dict[str, Any]) -> list[str]:
    schema = load_json(safe_repo_path(root, "design/system_v3/06_Asset_System/Page_Resource_Binding_Schema_V1.json"))
    validator = Draft202012Validator(schema)
    errors = []
    for error in sorted(validator.iter_errors(binding), key=lambda item: list(map(str, item.absolute_path))):
        path = "/".join(str(item) for item in error.absolute_path) or "$"
        errors.append(f"SCHEMA_INVALID at {path}: {error.message}")
    return errors


def validate_page_identity(root: Path, binding: dict[str, Any], binding_path: Path, page: dict[str, Any]) -> list[str]:
    errors = []
    canonical_id = page.get("id")
    owner_path = page.get("owner_path")
    if binding.get("page_id") != canonical_id:
        errors.append(f"PAGE_ID_MISMATCH: binding={binding.get('page_id')} registry={canonical_id}")
    if binding.get("owner_path") != owner_path:
        errors.append(f"OWNER_PATH_MISMATCH: binding={binding.get('owner_path')} registry={owner_path}")
    try:
        expected_path = safe_repo_path(root, binding_path_for(page)).resolve()
        if binding_path.resolve() != expected_path:
            errors.append(f"BINDING_PATH_MISMATCH: {binding_path}")
    except BindingError as exc:
        errors.append(f"PAGE_REGISTRY_INVALID: {exc}")
    return errors


def validate_authorities(root: Path, binding: dict[str, Any]) -> tuple[dict[str, dict[str, Any]], list[str]]:
    errors: list[str] = []
    authorities: dict[str, dict[str, Any]] = {}
    for authority in binding.get("source_authorities", []):
        authority_id = authority.get("authority_id")
        if authority_id in authorities:
            errors.append(f"DUPLICATE_AUTHORITY_ID: {authority_id}")
            continue
        authorities[authority_id] = authority
        try:
            path = safe_repo_path(root, authority["path"])
        except (KeyError, BindingError) as exc:
            errors.append(f"SOURCE_AUTHORITY_PATH_INVALID: {authority_id}: {exc}")
            continue
        if not path.is_file():
            errors.append(f"SOURCE_AUTHORITY_MISSING: {authority.get('path')}")
            continue
        actual_sha = sha256_file(path)
        if actual_sha != authority.get("sha256"):
            errors.append(f"SOURCE_AUTHORITY_SHA_MISMATCH: {authority.get('path')}: expected {authority.get('sha256')} got {actual_sha}")
    return authorities, errors


def validate_field_reference(
    root: Path, authorities: dict[str, dict[str, Any]], reference: dict[str, Any], expected: Any
) -> str | None:
    authority = authorities.get(reference.get("authority_id"))
    if authority is None:
        return f"SOURCE_AUTHORITY_UNKNOWN: {reference.get('authority_id')}"
    path = safe_repo_path(root, authority["path"])
    if not path.is_file():
        return f"SOURCE_AUTHORITY_MISSING: {authority['path']}"
    try:
        record = json_pointer(load_json(path), reference.get("json_pointer", ""))
        actual = json_pointer(record, reference.get("field_pointer", ""))
    except BindingError as exc:
        return f"SOURCE_FIELD_UNRESOLVED: {exc}"
    values_match = actual == expected or (isinstance(expected, str) and isinstance(actual, (int, float)) and str(actual) == expected)
    if not values_match:
        return f"SOURCE_FIELD_MISMATCH: {reference.get('authority_id')} {reference.get('json_pointer')}{reference.get('field_pointer')}: expected {expected!r} got {actual!r}"
    return None


def validate_identity_and_version(root: Path, binding: dict[str, Any], authorities: dict[str, dict[str, Any]], page: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    identity = binding.get("page_identity_authority", {})
    expected_page_id = binding.get("page_id")
    expected_owner = binding.get("owner_path")
    for field_pointer, expected in (("/id", expected_page_id), ("/owner_path", expected_owner)):
        error = validate_field_reference(root, authorities, {**identity, "field_pointer": field_pointer}, expected)
        if error:
            errors.append(error)
    version = binding.get("authority_version_source", {})
    version_error = validate_field_reference(root, authorities, version, binding.get("authority_version"))
    if version_error:
        errors.append(version_error)
    for alias_source in binding.get("page_id_alias_sources", []):
        alias = alias_source.get("expected_value")
        error = validate_field_reference(root, authorities, alias_source, alias)
        if error:
            errors.append(error)
        if alias not in binding.get("page_id_aliases", []):
            errors.append(f"UNDECLARED_PAGE_ID_ALIAS: {alias}")
    declared = set(binding.get("page_id_aliases", []))
    evidenced = {row.get("expected_value") for row in binding.get("page_id_alias_sources", [])}
    if declared != evidenced:
        errors.append(f"PAGE_ID_ALIAS_EVIDENCE_MISMATCH: declared={sorted(declared)} evidenced={sorted(evidenced)}")
    if page.get("owner_path") != expected_owner:
        errors.append(f"PAGE_OWNER_AUTHORITY_MISMATCH: {expected_owner}")
    return errors


def validate_permission_authorities(
    root: Path, binding: dict[str, Any], authorities: dict[str, dict[str, Any]]
) -> list[str]:
    errors: list[str] = []
    policy = binding.get("publication_policy", {})
    policy_authority_id = policy.get("authority_id")
    policy_authority = authorities.get(policy_authority_id)
    if policy_authority is None:
        errors.append(f"PAGES_PUBLICATION_POLICY_AUTHORITY_MISSING: {policy_authority_id}")
    else:
        try:
            if not safe_repo_path(root, policy_authority["path"]).is_file():
                errors.append(f"PAGES_PUBLICATION_POLICY_SOURCE_MISSING: {policy_authority['path']}")
        except BindingError as exc:
            errors.append(f"PAGES_PUBLICATION_POLICY_PATH_INVALID: {exc}")
    for asset in binding.get("asset_bindings", []):
        permission = asset.get("publication_permission", {})
        authority_id = permission.get("authority_id")
        if authority_id not in authorities:
            errors.append(f"PUBLICATION_PERMISSION_AUTHORITY_UNKNOWN: {asset.get('asset_id')} -> {authority_id}")
            continue
        if permission.get("state") == "PUBLIC_BY_EXISTING_PAGES_RULE":
            if authority_id != policy_authority_id:
                errors.append(f"PUBLICATION_PERMISSION_AUTHORITY_MISMATCH: {asset.get('asset_id')} -> {authority_id}")
            marker = permission.get("policy_marker") or policy.get("public_scope_marker")
            authority = authorities[authority_id]
            try:
                source_path = safe_repo_path(root, authority["path"])
                source_exists = source_path.is_file()
                source_text = source_path.read_text(encoding="utf-8") if source_exists else ""
            except (BindingError, OSError) as exc:
                errors.append(f"PUBLICATION_PERMISSION_SOURCE_INVALID: {asset.get('asset_id')}: {exc}")
                continue
            if not marker or not source_exists or marker not in source_text:
                errors.append(f"PUBLICATION_PERMISSION_NOT_EXPLICIT: {asset.get('asset_id')} marker={marker}")
    return errors


def validate_asset_source_fields(root: Path, asset: dict[str, Any], authorities: dict[str, dict[str, Any]]) -> list[str]:
    errors: list[str] = []
    for source in asset.get("source_authorities", []):
        authority = authorities.get(source.get("authority_id"))
        if authority is None:
            errors.append(f"SOURCE_AUTHORITY_UNKNOWN: {asset.get('asset_id')} -> {source.get('authority_id')}")
            continue
        try:
            source_file = safe_repo_path(root, authority["path"])
            record = json_pointer(load_json(source_file), source.get("json_pointer", ""))
            for binding_field, source_pointer in source.get("field_map", {}).items():
                expected = nested_value(asset, binding_field)
                actual = json_pointer(record, source_pointer)
                if actual != expected:
                    errors.append(
                        f"SOURCE_RECORD_MISMATCH: {asset.get('asset_id')} field={binding_field} "
                        f"authority={source.get('authority_id')} expected={expected!r} got={actual!r}"
                    )
        except (BindingError, OSError, KeyError) as exc:
            errors.append(f"SOURCE_RECORD_UNRESOLVED: {asset.get('asset_id')}: {exc}")
    return errors


def validate_asset_file(root: Path, asset: dict[str, Any]) -> tuple[dict[str, Any], list[str], list[str]]:
    errors: list[str] = []
    warnings: list[str] = []
    result = {
        "asset_id": asset.get("asset_id"),
        "asset_role": asset.get("asset_role"),
        "source_path": asset.get("source_path"),
        "lifecycle_status": asset.get("lifecycle_status"),
        "asset_designation": asset.get("asset_designation"),
        "publication_permission": asset.get("publication_permission", {}).get("state"),
        "requiredness": asset.get("requiredness"),
        "validation_status": "NOT_VERIFIED",
        "resource_status": "NOT_VERIFIED",
        "actual": {},
        "degradation": None,
    }
    if asset.get("source_path") is None:
        if asset.get("requiredness") == "REQUIRED":
            errors.append(f"REQUIRED_RESOURCE_MISSING: {asset.get('asset_id')}: {asset.get('missing_reason')}")
            result["resource_status"] = "MISSING"
            result["validation_status"] = "FAIL"
        elif asset.get("requiredness") == "OPTIONAL":
            result["resource_status"] = "MISSING"
            result["degradation"] = asset.get("degradation_policy") or "OPTIONAL_RESOURCE_MISSING"
            warnings.append(f"OPTIONAL_RESOURCE_MISSING: {asset.get('asset_id')}: {asset.get('missing_reason')}")
        else:
            result["resource_status"] = "NOT_VERIFIED"
            warnings.append(f"RESOURCE_PATH_NOT_VERIFIED: {asset.get('asset_id')}: {asset.get('missing_reason')}")
        return result, errors, warnings
    try:
        path = safe_repo_path(root, asset["source_path"])
    except BindingError as exc:
        errors.append(f"RESOURCE_PATH_INVALID: {asset.get('asset_id')}: {exc}")
        result["resource_status"] = "BLOCKED_INTEGRITY"
        result["validation_status"] = "FAIL"
        return result, errors, warnings
    if not path.is_file():
        if asset.get("requiredness") == "OPTIONAL":
            result["resource_status"] = "MISSING"
            result["degradation"] = asset.get("degradation_policy") or "OPTIONAL_RESOURCE_MISSING"
            warnings.append(f"OPTIONAL_RESOURCE_MISSING: {asset.get('asset_id')} path={asset['source_path']}")
        else:
            status = "REQUIRED_RESOURCE_MISSING" if asset.get("requiredness") == "REQUIRED" else "RESOURCE_PATH_NOT_VERIFIED"
            errors.append(f"{status}: {asset.get('asset_id')} path={asset['source_path']}")
            result["resource_status"] = "MISSING" if asset.get("requiredness") == "REQUIRED" else "NOT_VERIFIED"
            result["validation_status"] = "FAIL"
        return result, errors, warnings
    raw = path.read_bytes()
    actual_sha = sha256_bytes(raw)
    actual_media_type = detect_media_type(path, raw)
    actual_dimensions = image_dimensions(path, actual_media_type)
    result["actual"] = {"bytes": len(raw), "sha256": actual_sha, "media_type": actual_media_type, "pixel_dimensions": actual_dimensions}
    if asset.get("bytes") is not None and asset.get("bytes") != len(raw):
        errors.append(f"BYTE_COUNT_MISMATCH: {asset.get('asset_id')} expected={asset.get('bytes')} actual={len(raw)}")
    if asset.get("sha256") is not None and asset.get("sha256") != actual_sha:
        errors.append(f"SHA256_MISMATCH: {asset.get('asset_id')} expected={asset.get('sha256')} actual={actual_sha}")
    if asset.get("media_type") is not None and asset.get("media_type") != actual_media_type:
        errors.append(f"MEDIA_TYPE_MISMATCH: {asset.get('asset_id')} expected={asset.get('media_type')} actual={actual_media_type}")
    expected_dimensions = asset.get("pixel_dimensions")
    if expected_dimensions is not None and actual_dimensions != expected_dimensions:
        errors.append(f"DIMENSIONS_MISMATCH: {asset.get('asset_id')} expected={expected_dimensions} actual={actual_dimensions}")
    if actual_media_type.startswith("image/") and actual_dimensions is None and expected_dimensions is not None:
        errors.append(f"IMAGE_DIMENSIONS_UNVERIFIABLE: {asset.get('asset_id')}")
    if actual_media_type.startswith("image/") and expected_dimensions is None:
        warnings.append(f"IMAGE_DIMENSIONS_NOT_VERIFIED: {asset.get('asset_id')}")
    if errors:
        result["resource_status"] = "BLOCKED_INTEGRITY"
        result["validation_status"] = "FAIL"
    else:
        result["validation_status"] = "PASS"
        result["resource_status"] = "ILLUSTRATIVE_ONLY" if asset.get("asset_designation") == "ILLUSTRATIVE_ONLY" else asset.get("lifecycle_status", "NOT_VERIFIED")
    return result, errors, warnings


def validate_illustrative_designation(root: Path, asset: dict[str, Any], authorities: dict[str, dict[str, Any]]) -> list[str]:
    errors = []
    for source in asset.get("source_authorities", []):
        authority = authorities.get(source.get("authority_id"))
        if authority is None:
            continue
        try:
            record = json_pointer(load_json(safe_repo_path(root, authority["path"])), source.get("json_pointer", ""))
        except BindingError:
            continue
        status_values = [str(v).upper() for v in record.values() if isinstance(v, (str, int, float))]
        explicit_illustrative = any("ILLUSTRATIVE" in v or "DEMO_ONLY" in v or "SAMPLE_ONLY" in v for v in status_values)
        if explicit_illustrative and asset.get("asset_designation") not in {"ILLUSTRATIVE_ONLY", "REFERENCE_ONLY"}:
            errors.append(f"ILLUSTRATIVE_PROMOTION_BLOCKED: {asset.get('asset_id')} source marks this resource illustrative")
    return errors


def site_file_index(site_root: Path) -> tuple[dict[str, Path], dict[str, list[str]]]:
    paths: dict[str, Path] = {}
    hashes: dict[str, list[str]] = {}
    if site_root.is_symlink():
        raise BindingError(f"PAGES_SYMLINK_NOT_ALLOWED: site root is a symbolic link: {site_root}")
    if not site_root.is_dir():
        raise BindingError(f"published Pages root does not exist: {site_root}")
    for path in site_root.rglob("*"):
        if path.is_symlink():
            relative = path.relative_to(site_root).as_posix()
            raise BindingError(f"PAGES_SYMLINK_NOT_ALLOWED: symbolic link in published Pages bundle: {relative}")
        if not path.is_file():
            continue
        rel = path.relative_to(site_root).as_posix()
        paths[rel] = path
        hashes.setdefault(sha256_file(path), []).append(rel)
    return paths, hashes


def private_reference_denylist(root: Path) -> tuple[set[str], set[str]]:
    manifest_path = safe_repo_path(root, PRIVATE_REFERENCE_MANIFEST_PATH)
    if not manifest_path.is_file():
        raise BindingError(f"private reference manifest is missing: {PRIVATE_REFERENCE_MANIFEST_PATH}")
    manifest = load_json(manifest_path)
    names: set[str] = set()
    hashes: set[str] = set()
    for item in manifest.get("references", []):
        if item.get("public_publication_allowed") is False or item.get("public_permission") == "NOT_CONFIRMED":
            if item.get("filename"):
                names.add(Path(item["filename"]).name)
            if re.fullmatch(r"[0-9a-f]{64}", str(item.get("sha256", ""))):
                hashes.add(item["sha256"])
    # The older private SWOS photo is registered in the same source set, but is
    # intentionally not a top-level visual reference. Keep its known identity
    # in the publication scan as well.
    audit_photo = manifest.get("audit_snapshot", {}).get("source_photo_208758", {})
    if re.fullmatch(r"[0-9a-f]{64}", str(audit_photo.get("sha256", ""))):
        hashes.add(audit_photo["sha256"])
    names.add("208758.jpg")
    names.add("YuJian_Web_SWOS_Visual_References_Private_Handoff.zip")
    mapping_path = safe_repo_path(root, "design/pages/recognition/web/contracts/Work_B_Implementation_Mapping_V2.json")
    if mapping_path.is_file():
        mapping = load_json(mapping_path)
        asset_gate = mapping.get("asset_gate", {})
        if (
            asset_gate.get("public_repo_commit_allowed") is False
            or asset_gate.get("public_ci_artifact_allowed") is False
            or asset_gate.get("public_pages_upload_allowed") is False
        ):
            private_sha = asset_gate.get("verified_private_photo_sha256")
            if re.fullmatch(r"[0-9a-f]{64}", str(private_sha or "")):
                hashes.add(private_sha)
    input_manifest_path = safe_repo_path(root, PRIVATE_INPUT_MANIFEST_PATH)
    if input_manifest_path.is_file():
        input_manifest = load_json(input_manifest_path)
        for key in ("private_handoff_bundle", "private_source_assets_bundle"):
            row = input_manifest.get(key, {})
            filename = row.get("filename")
            digest = row.get("sha256")
            if filename:
                names.add(Path(filename).name)
            if re.fullmatch(r"[0-9a-f]{64}", str(digest or "")):
                hashes.add(digest)
        review_proxy = input_manifest.get("frame_reference_assets", {}).get("review_proxy_bundle", {})
        if review_proxy.get("filename"):
            names.add(Path(review_proxy["filename"]).name)
        if re.fullmatch(r"[0-9a-f]{64}", str(review_proxy.get("sha256", ""))):
            hashes.add(review_proxy["sha256"])
        baseline = input_manifest.get("asset_authorization_and_freeze", {}).get("pixel_baseline", {})
        if re.fullmatch(r"[0-9a-f]{64}", str(baseline.get("sha256", ""))):
            hashes.add(baseline["sha256"])
    return names, hashes


def validate_publication(root: Path, binding: dict[str, Any], authorities: dict[str, dict[str, Any]], site_root: Path) -> tuple[list[str], list[str], dict[str, Any]]:
    errors: list[str] = []
    warnings: list[str] = []
    try:
        site_paths, site_hashes = site_file_index(site_root)
    except BindingError as exc:
        return [str(exc)], warnings, {
            "site_root": str(site_root),
            "file_count": 0,
            "public_bound_resource_count": 0,
            "restricted_bound_resource_count": 0,
            "private_reference_name_count": 0,
            "private_reference_sha_count": 0,
        }
    policy = binding.get("publication_policy", {})
    policy_authority = authorities.get(policy.get("authority_id"))
    marker = policy.get("public_scope_marker")
    if policy_authority is None:
        errors.append("PAGES_PUBLICATION_POLICY_AUTHORITY_MISSING")
    else:
        workflow_path = safe_repo_path(root, policy_authority["path"])
        if not workflow_path.is_file():
            errors.append(f"PAGES_PUBLICATION_POLICY_SOURCE_MISSING: {policy_authority['path']}")
        elif marker not in workflow_path.read_text(encoding="utf-8"):
            errors.append(f"PAGES_PUBLICATION_POLICY_CHANGED: expected marker not found: {marker}")
    public_count = 0
    restricted_count = 0
    for asset in binding.get("asset_bindings", []):
        permission = asset.get("publication_permission", {}).get("state")
        source_path = asset.get("source_path")
        expected_sha = asset.get("sha256") or asset.get("declared_source_sha256")
        if permission == "PUBLIC_BY_EXISTING_PAGES_RULE":
            public_count += 1
            if not source_path:
                errors.append(f"PUBLIC_SCOPE_PATH_INVALID: {asset.get('asset_id')} path={source_path}")
                continue
            asset_marker = asset.get("publication_permission", {}).get("policy_marker") or marker
            workflow_path = safe_repo_path(root, policy_authority["path"]) if policy_authority else None
            if workflow_path is None or not workflow_path.is_file() or asset_marker not in workflow_path.read_text(encoding="utf-8"):
                errors.append(f"PAGES_ASSET_SCOPE_NOT_AUTHORIZED: {asset.get('asset_id')} marker={asset_marker}")
                continue
            site_path = site_paths.get(source_path)
            if site_path is None:
                if asset.get("requiredness") == "OPTIONAL" and asset.get("degradation_policy"):
                    warnings.append(f"OPTIONAL_PUBLISHED_RESOURCE_MISSING: {asset.get('asset_id')} path={source_path}")
                else:
                    errors.append(f"PUBLISHED_RESOURCE_MISSING: {asset.get('asset_id')} path={source_path}")
                continue
            actual_sha = sha256_file(site_path)
            if expected_sha is not None and actual_sha != expected_sha:
                errors.append(f"PUBLISHED_RESOURCE_SHA_MISMATCH: {asset.get('asset_id')} expected={expected_sha} actual={actual_sha}")
        else:
            restricted_count += 1
            if source_path and source_path in site_paths:
                errors.append(f"UNAUTHORIZED_RESOURCE_PATH_IN_PAGES: {asset.get('asset_id')} path={source_path} permission={permission}")
            if expected_sha and expected_sha in site_hashes:
                errors.append(f"UNAUTHORIZED_RESOURCE_BYTES_IN_PAGES: {asset.get('asset_id')} permission={permission} paths={site_hashes[expected_sha]}")
            if permission == "NOT_CONFIRMED" and not source_path and not expected_sha:
                warnings.append(f"PUBLICATION_PERMISSION_NOT_CONFIRMED_WITHOUT_BYTES: {asset.get('asset_id')}")
    denied_names, denied_hashes = private_reference_denylist(root)
    for rel in site_paths:
        if Path(rel).name in denied_names:
            errors.append(f"PRIVATE_REFERENCE_NAME_IN_PAGES: {rel}")
    # Hash every published file. A private image or bundle can be renamed or
    # assigned an unrelated extension, so suffix-based filtering is unsafe.
    for digest in denied_hashes.intersection(site_hashes):
        for rel in site_hashes[digest]:
            errors.append(f"PRIVATE_REFERENCE_BYTES_IN_PAGES: {rel}")
    return errors, warnings, {
        "site_root": str(site_root),
        "file_count": len(site_paths),
        "public_bound_resource_count": public_count,
        "restricted_bound_resource_count": restricted_count,
        "private_reference_name_count": len(denied_names),
        "private_reference_sha_count": len(denied_hashes),
    }


def validate_binding(root: Path, binding_path: Path, page: dict[str, Any], site_root: Path | None = None) -> dict[str, Any]:
    binding = load_json(binding_path)
    errors = schema_errors(root, binding)
    errors.extend(validate_page_identity(root, binding, binding_path, page))
    authorities, authority_errors = validate_authorities(root, binding)
    errors.extend(authority_errors)
    permission_authority_errors = validate_permission_authorities(root, binding, authorities)
    errors.extend(permission_authority_errors)
    if not authority_errors:
        errors.extend(validate_identity_and_version(root, binding, authorities, page))
    results = []
    warnings = []
    seen_asset_ids: set[str] = set()
    owner_path = binding.get("owner_path", "").rstrip("/")
    for asset in binding.get("asset_bindings", []):
        asset_id = asset.get("asset_id")
        asset_errors = []
        if asset.get("page_id") != binding.get("page_id"):
            asset_errors.append(
                f"RESOURCE_PAGE_ID_MISMATCH: {asset_id} asset={asset.get('page_id')} binding={binding.get('page_id')}"
            )
        if asset_id in seen_asset_ids:
            asset_errors.append(f"DUPLICATE_ASSET_ID: {asset_id}")
        seen_asset_ids.add(asset_id)
        source_path = asset.get("source_path")
        if isinstance(source_path, str) and source_path.startswith("design/pages/") and not source_path.startswith(owner_path + "/"):
            asset_errors.append(f"CROSS_PAGE_ASSET_COPY_FORBIDDEN: {asset_id} path={source_path} owner={owner_path}")
        if not authority_errors:
            asset_errors.extend(validate_asset_source_fields(root, asset, authorities))
        asset_errors.extend(validate_illustrative_designation(root, asset, authorities))
        asset_result, file_errors, asset_warnings = validate_asset_file(root, asset)
        asset_errors.extend(file_errors)
        warnings.extend(asset_warnings)
        if asset_errors:
            asset_result["validation_status"] = "FAIL"
            if any("ILLUSTRATIVE_PROMOTION_BLOCKED" in message for message in asset_errors):
                asset_result["resource_status"] = "BLOCKED_INTEGRITY"
        errors.extend(asset_errors)
        results.append(asset_result)
    publication_result = None
    if site_root is not None and not authority_errors and not permission_authority_errors:
        publication_errors, publication_warnings, publication_result = validate_publication(root, binding, authorities, site_root)
        errors.extend(publication_errors)
        warnings.extend(publication_warnings)
    return {
        "gate_id": "G-PAGE-FREEZE-PIPELINE-W2-RESOURCES",
        "binding_id": binding.get("binding_id"),
        "page_id": binding.get("page_id"),
        "authority_version": binding.get("authority_version"),
        "binding_path": binding_path.relative_to(root).as_posix(),
        "binding_sha256": sha256_file(binding_path),
        "status": "FAIL" if errors else "PASS",
        "asset_count": len(results),
        "asset_results": results,
        "publication": publication_result,
        "errors": errors,
        "warnings": warnings,
    }


def validate_all(
    root: Path,
    required_page_ids: tuple[str, ...] = (),
    site_root: Path | None = None,
    source_sha: str | None = None,
    run_id: str | None = None,
) -> dict[str, Any]:
    pages, _ = experience_pages(root)
    candidates = page_binding_candidates(root)
    reports = [validate_binding(root, path, page, site_root) for _, page, path in candidates]
    by_id = {report["page_id"]: report for report in reports}
    missing = sorted(set(required_page_ids) - set(by_id))
    errors = [error for report in reports for error in report["errors"]]
    warnings = [warning for report in reports for warning in report["warnings"]]
    if missing:
        errors.append("REQUIRED_PAGE_BINDING_MISSING: " + ", ".join(missing))
    if source_sha is not None:
        if not re.fullmatch(r"[0-9a-f]{40}", source_sha):
            errors.append(f"EXACT_SOURCE_SHA_INVALID: {source_sha}")
        else:
            try:
                checked_out_sha = subprocess.run(
                    ["git", "rev-parse", "HEAD"], cwd=root, check=True, capture_output=True, text=True
                ).stdout.strip()
                if checked_out_sha != source_sha:
                    errors.append(f"EXACT_SOURCE_SHA_MISMATCH: report={source_sha} checkout={checked_out_sha}")
                dirty = subprocess.run(["git", "diff", "--quiet", "HEAD", "--"], cwd=root, check=False)
                if dirty.returncode != 0:
                    errors.append("EXACT_SOURCE_WORKTREE_DIRTY: Pages bundle must be built from the reported committed SHA")
            except (OSError, subprocess.CalledProcessError) as exc:
                errors.append(f"EXACT_SOURCE_SHA_UNVERIFIABLE: {exc}")
    return {
        "gate_id": "G-PAGE-FREEZE-PIPELINE-W2-RESOURCES",
        "source_sha": source_sha,
        "workflow_run_id": run_id,
        "status": "FAIL" if errors else "PASS",
        "pages_checked": len(reports),
        "required_page_ids": list(required_page_ids),
        "errors": errors,
        "warnings": warnings,
        "pages": reports,
    }


def resolve_page_asset(
    root: Path,
    page_id: str,
    asset_role: str,
    authority_version: str,
    source_sha: str | None = None,
) -> dict[str, Any]:
    binding, binding_path, page = find_binding(root, page_id)
    report = validate_binding(root, binding_path, page)
    if binding.get("authority_version") != authority_version:
        return {
            "page_id": binding.get("page_id"),
            "input_page_id": page_id,
            "asset_role": asset_role,
            "authority_version": authority_version,
            "status": "NOT_VERIFIED",
            "reason": f"AUTHORITY_VERSION_MISMATCH: registered={binding.get('authority_version')}",
            "resources": [],
        }
    role_matches = [row for row in binding.get("asset_bindings", []) if row.get("asset_role") == asset_role]
    matches = role_matches
    if source_sha is not None:
        matches = [row for row in matches if row.get("sha256") == source_sha or row.get("declared_source_sha256") == source_sha]
        if role_matches and not matches:
            return {
                "page_id": binding.get("page_id"),
                "input_page_id": page_id,
                "asset_role": asset_role,
                "authority_version": authority_version,
                "binding_path": binding_path.relative_to(root).as_posix(),
                "binding_sha256": sha256_file(binding_path),
                "status": "BLOCKED_INTEGRITY",
                "reason": {
                    "code": "SOURCE_SHA_MISMATCH",
                    "supplied": source_sha,
                    "registered": [row.get("sha256") or row.get("declared_source_sha256") for row in role_matches],
                },
                "resources": [],
            }
    if not matches:
        return {
            "page_id": binding.get("page_id"),
            "input_page_id": page_id,
            "asset_role": asset_role,
            "authority_version": authority_version,
            "status": "MISSING",
            "reason": "NO_BINDING_MATCH",
            "resources": [],
        }
    report_by_asset = {item["asset_id"]: item for item in report["asset_results"]}
    resources = []
    for asset in matches:
        checked = report_by_asset.get(asset["asset_id"], {})
        descriptor = {
            **asset,
            "verification": checked,
        }
        if checked.get("resource_status") == "MISSING":
            descriptor["resolution_reason"] = asset.get("missing_reason") or (
                f"RESOURCE_FILE_MISSING: {asset.get('source_path')}"
                if asset.get("source_path")
                else "RESOURCE_PATH_NOT_REGISTERED"
            )
        elif checked.get("resource_status") == "NOT_VERIFIED":
            descriptor["resolution_reason"] = asset.get("missing_reason") or "RESOURCE_NOT_VERIFIED"
        resources.append(descriptor)
    state_values = {row.get("resource_status") for row in resources}
    failed_assets = [row for row in resources if row.get("verification", {}).get("validation_status") == "FAIL"]
    if report["errors"] or failed_assets or "BLOCKED_INTEGRITY" in state_values:
        status = "BLOCKED_INTEGRITY"
        reason = report["errors"] or [f"RESOURCE_VALIDATION_FAILED: {row.get('asset_id')}" for row in failed_assets]
    elif all(row.get("resource_status") == "MISSING" for row in resources):
        if all(row.get("requiredness") == "OPTIONAL" for row in resources):
            status = "DEGRADED"
            reason = [row.get("resolution_reason") for row in resources if row.get("resolution_reason")]
        else:
            status = "MISSING"
            reason = [row.get("resolution_reason") for row in resources if row.get("resolution_reason")]
    elif "NOT_VERIFIED" in state_values:
        status = "NOT_VERIFIED"
        reason = [row.get("resolution_reason") for row in resources if row.get("resolution_reason")]
    elif "MISSING" in state_values:
        status = "DEGRADED"
        reason = [row.get("resolution_reason") for row in resources if row.get("resolution_reason")]
    else:
        status = "RESOLVED"
        reason = None
    return {
        "page_id": binding.get("page_id"),
        "input_page_id": page_id,
        "asset_role": asset_role,
        "authority_version": authority_version,
        "binding_path": binding_path.relative_to(root).as_posix(),
        "binding_sha256": sha256_file(binding_path),
        "status": status,
        "reason": reason,
        "resources": resources,
    }


def resolvePageAsset(
    page_id: str,
    asset_role: str,
    authority_version: str,
    source_sha: str | None = None,
    root: Path = ROOT,
) -> dict[str, Any]:
    """Camel-case compatibility spelling for Design Manager adapters."""
    return resolve_page_asset(root, page_id, asset_role, authority_version, source_sha)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--all-pages", action="store_true")
    parser.add_argument("--required-page-id", action="append", default=[])
    parser.add_argument("--page-id")
    parser.add_argument("--asset-role")
    parser.add_argument("--authority-version")
    parser.add_argument("--source-sha")
    parser.add_argument("--site-root", type=Path)
    parser.add_argument("--run-id")
    parser.add_argument("--report", type=Path)
    args = parser.parse_args(argv)
    root = args.root.resolve()
    try:
        if args.page_id or args.asset_role or args.authority_version:
            if not (args.page_id and args.asset_role and args.authority_version):
                raise BindingError("resolver requires --page-id, --asset-role and --authority-version together")
            result = resolve_page_asset(root, args.page_id, args.asset_role, args.authority_version, args.source_sha)
            status = 1 if result["status"] in {"BLOCKED_INTEGRITY", "MISSING"} else 0
        else:
            if not args.all_pages:
                raise BindingError("choose --all-pages or the resolver inputs")
            result = validate_all(
                root,
                tuple(args.required_page_id),
                args.site_root.absolute() if args.site_root else None,
                args.source_sha,
                args.run_id,
            )
            status = 1 if result["status"] != "PASS" else 0
        output = json.dumps(result, ensure_ascii=False, indent=2) + "\n"
        print(output, end="")
        if args.report:
            args.report.parent.mkdir(parents=True, exist_ok=True)
            args.report.write_text(output, encoding="utf-8")
        if not args.page_id:
            print(f"{result['gate_id']}={result['status']}", file=sys.stderr)
        return status
    except BindingError as exc:
        print(json.dumps({"status": "FAIL", "error": str(exc)}, ensure_ascii=False), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
