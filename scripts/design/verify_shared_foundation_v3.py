#!/usr/bin/env python3
"""Validate YuJian Design System V3.1 shared foundation registries and byte identities."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import sys
import zlib
from collections import Counter
from pathlib import Path, PurePosixPath
from typing import Any

TYPE_PATH = "design/system_v3/04_Design_Tokens/Typography/Typography_Tokens_V3_FINAL.json"
COLOR_PATH = "design/system_v3/04_Design_Tokens/Color/Color_Tokens_V3_FINAL.json"
GEOMETRY_PATH = "design/system_v3/07_Responsive_System/Geometry_Tokens_V3_FINAL.json"
COMPONENT_PATH = "design/system_v3/05_Component_System/Component_Registry_V3_FINAL.json"
ASSET_PATH = "design/system_v3/06_Asset_System/Asset_Registry_V3_FINAL.json"
ICON_PATH = "design/system_v3/09_Icon_System/Icon_Registry_V3.json"
BACKGROUND_PATH = "design/system_v3/08_Background_System/Background_Registry_V3.json"
FREEZE_PATH = "design/system_v3/11_Freeze_Governance/Shared_Foundation_Freeze_Status_V3_1.json"
PAGE_MAP_PATH = "design/system_v3/01_Page_Registry/page_state_authority_map_v3_1.json"
AUTHORITY_PATH = "design/system_v3/11_Freeze_Governance/Effective_Authority_Registry_V3_1.json"
FLOW_PATH = "design/system_v3/00_Product_Master_Flow/master_flow_v3_1.json"
ASSET_STATUSES = {"FROZEN", "APPROVED", "REVIEW", "MISSING", "DEPRECATED"}
SHA1_RE = re.compile(r"^[0-9a-f]{40}$")
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")
HEX_RE = re.compile(r"(?<![0-9A-Za-z])#(?:[0-9a-fA-F]{8}|[0-9a-fA-F]{6}|[0-9a-fA-F]{4}|[0-9a-fA-F]{3})(?![0-9A-Za-z])")
RGB_RE = re.compile(r"(?i)\brgba?\(\s*[^)]*?\s*\)")
PNG_SIG = b"\x89PNG\r\n\x1a\n"


def load_json(root: Path, relative: str) -> dict[str, Any]:
    path = root / relative
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ValueError(f"{relative}: cannot parse JSON: {exc}") from exc
    if not isinstance(value, dict):
        raise ValueError(f"{relative}: root must be an object")
    return value


def unique_ids(rows: Any, field: str, label: str, issues: list[str]) -> set[str]:
    if not isinstance(rows, list):
        issues.append(f"{label}: expected a list")
        return set()
    seen: set[str] = set()
    for row in rows:
        if not isinstance(row, dict):
            issues.append(f"{label}: row is not an object")
            continue
        value = row.get(field)
        if not isinstance(value, str) or not value:
            issues.append(f"{label}: missing {field}")
            continue
        if value in seen:
            issues.append(f"{label}: duplicate {field} {value}")
        seen.add(value)
    return seen


def safe_relative(value: Any, roots: set[str]) -> bool:
    if not isinstance(value, str):
        return False
    path = PurePosixPath(value)
    return (
        not path.is_absolute()
        and bool(path.parts)
        and path.parts[0] in roots
        and all(part not in {"", ".", ".."} for part in path.parts)
    )


def run_git(root: Path, args: list[str], *, check: bool = True) -> subprocess.CompletedProcess[bytes]:
    result = subprocess.run(["git", *args], cwd=root, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)
    if check and result.returncode != 0:
        message = result.stderr.decode("utf-8", errors="replace").strip()
        raise RuntimeError(f"git {' '.join(args)} failed: {message}")
    return result


def ensure_commit(root: Path, commit: str) -> None:
    if not SHA1_RE.fullmatch(commit):
        raise ValueError(f"invalid baseline commit SHA: {commit!r}")
    probe = run_git(root, ["cat-file", "-e", f"{commit}^{{commit}}"], check=False)
    if probe.returncode == 0:
        return
    fetched = run_git(root, ["fetch", "--no-tags", "--depth=1", "origin", commit], check=False)
    if fetched.returncode != 0:
        message = fetched.stderr.decode("utf-8", errors="replace").strip()
        raise RuntimeError(f"baseline commit unavailable: {message}")
    if run_git(root, ["cat-file", "-e", f"{commit}^{{commit}}"], check=False).returncode != 0:
        raise RuntimeError(f"baseline commit unavailable after fetch: {commit}")


def git_show(root: Path, commit: str, relative: str) -> bytes:
    result = run_git(root, ["show", f"{commit}:{relative}"], check=False)
    if result.returncode != 0:
        message = result.stderr.decode("utf-8", errors="replace").strip()
        raise RuntimeError(f"cannot read baseline file {relative}: {message}")
    return result.stdout


def git_blob_sha1(data: bytes) -> str:
    header = b"blob " + str(len(data)).encode("ascii") + b"\0"
    return hashlib.sha1(header + data).hexdigest()


def canonical_color(raw: str) -> str | None:
    value = raw.strip().lower()
    if value.startswith("#"):
        digits = value[1:]
        if len(digits) == 3:
            digits = "".join(ch * 2 for ch in digits) + "ff"
        elif len(digits) == 4:
            digits = "".join(ch * 2 for ch in digits)
        elif len(digits) == 6:
            digits += "ff"
        if len(digits) == 8:
            return "#" + digits
        return None
    match = re.fullmatch(
        r"rgba?\(\s*([+-]?[\d.]+)%?\s*,\s*([+-]?[\d.]+)%?\s*,\s*([+-]?[\d.]+)%?(?:\s*,\s*([+-]?[\d.]+)%?)?\s*\)",
        value,
        re.IGNORECASE,
    )
    if not match:
        return None
    channels = []
    for raw_channel in match.groups()[:3]:
        channel = float(raw_channel)
        if "%" in value:
            channel *= 2.55
        channels.append(max(0, min(255, int(round(channel)))))
    alpha_raw = match.groups()[3]
    if alpha_raw is None:
        alpha = 255
    else:
        alpha = float(alpha_raw)
        if re.search(r",\s*[+-]?[\d.]+%\s*\)$", value):
            alpha *= 2.55
        else:
            alpha *= 255
        alpha = max(0, min(255, int(round(alpha))))
    return "#" + "".join(f"{channel:02x}" for channel in channels) + f"{alpha:02x}"


def extract_color_literals(text: str) -> list[str]:
    values = [match.group(0) for match in HEX_RE.finditer(text)]
    values.extend(match.group(0) for match in RGB_RE.finditer(text))
    return values


def png_metadata_bytes(data: bytes) -> bytes:
    if not data.startswith(PNG_SIG):
        return b""
    result = bytearray()
    offset = len(PNG_SIG)
    while offset + 12 <= len(data):
        length = int.from_bytes(data[offset:offset + 4], "big")
        kind = data[offset + 4:offset + 8]
        start = offset + 8
        end = start + length
        if end + 4 > len(data):
            break
        payload = data[start:end]
        if kind == b"tEXt":
            result.extend(payload.replace(b"\0", b" "))
        elif kind == b"iTXt":
            result.extend(payload.replace(b"\0", b" "))
        elif kind == b"zTXt":
            separator = payload.find(b"\0")
            if separator >= 0 and separator + 2 <= len(payload):
                try:
                    result.extend(zlib.decompress(payload[separator + 2:]))
                except zlib.error:
                    pass
        offset = end + 4
        if kind == b"IEND":
            break
    return bytes(result)


def scan_colors(root: Path, token_doc: dict[str, Any]) -> tuple[int, list[str], int]:
    allowed: set[str] = set()
    for token in token_doc.get("tokens", []):
        if not isinstance(token, dict):
            continue
        for key in ("hex", "rgba"):
            value = token.get(key)
            if isinstance(value, str):
                canonical = canonical_color(value)
                if canonical:
                    allowed.add(canonical)

    source_roots = [
        "design/pages",
        "design/system/components",
        "design/system/core_visual_v1",
        "design/system/backgrounds",
        "design/system/icon_library_v0_1",
        "design/system/fish_media_display_v1",
        "design/registry",
        "design/system_v3/04_Design_Tokens",
        "design/system_v3/05_Component_System",
        "design/system_v3/08_Background_System",
        "design/system_v3/09_Icon_System",
    ]
    text_ext = {".md", ".json", ".svg", ".css", ".html", ".xml", ".js", ".txt"}
    raster_ext = {".png"}
    found: set[str] = set()
    png_metadata_count = 0
    scanned = 0
    for source_root in source_roots:
        directory = root / source_root
        if not directory.exists():
            continue
        for path in directory.rglob("*"):
            if not path.is_file() or path.is_symlink():
                continue
            suffix = path.suffix.lower()
            try:
                if suffix in text_ext:
                    text = path.read_text(encoding="utf-8")
                    scanned += 1
                elif suffix in raster_ext:
                    metadata = png_metadata_bytes(path.read_bytes()).decode("utf-8", errors="ignore")
                    scanned += 1
                    text = metadata
                else:
                    continue
            except (OSError, UnicodeDecodeError):
                continue
            for raw in extract_color_literals(text):
                canonical = canonical_color(raw)
                if not canonical:
                    continue
                if suffix == ".png":
                    png_metadata_count += 1
                if canonical not in allowed:
                    found.add(f"{canonical} in {path.relative_to(root).as_posix()}")
    return scanned, sorted(found), png_metadata_count


def verify_assets(root: Path, registry: dict[str, Any], issues: list[str]) -> tuple[int, int]:
    rows = registry.get("entries")
    asset_ids = unique_ids(rows, "asset_id", "assets", issues)
    rows = rows if isinstance(rows, list) else []
    rows_by_id = {row.get("asset_id"): row for row in rows if isinstance(row, dict)}
    paths: set[str] = set()
    baseline = registry.get("baseline_commit_sha")
    try:
        ensure_commit(root, str(baseline or ""))
    except (RuntimeError, ValueError) as exc:
        issues.append(f"asset baseline: {exc}")
        return len(asset_ids), 0
    verified = 0
    for row in rows:
        if not isinstance(row, dict):
            continue
        relative = row.get("path")
        if not safe_relative(relative, {"design"}):
            issues.append(f"asset has unsafe path: {relative!r}")
            continue
        if relative in paths:
            issues.append(f"duplicate asset path: {relative}")
        paths.add(relative)
        if row.get("status") not in ASSET_STATUSES:
            issues.append(f"{relative}: invalid asset status {row.get('status')!r}")
        if not isinstance(row.get("type"), str) or not row["type"]:
            issues.append(f"{relative}: missing type")
        if not isinstance(row.get("size"), int) or row["size"] < 0:
            issues.append(f"{relative}: invalid byte size")
        if not isinstance(row.get("dimensions"), str) or not row["dimensions"]:
            issues.append(f"{relative}: missing dimensions field")
        if not isinstance(row.get("source"), (str, list)) or not row.get("source"):
            issues.append(f"{relative}: missing source")
        if not isinstance(row.get("used_by"), list):
            issues.append(f"{relative}: used_by must be a list")
        recorded_sha256 = row.get("sha256")
        if not isinstance(recorded_sha256, str) or not SHA256_RE.fullmatch(recorded_sha256):
            issues.append(f"{relative}: missing/invalid SHA-256")
            continue
        worktree_file = root.joinpath(*PurePosixPath(relative).parts)
        if worktree_file.is_symlink() or not worktree_file.is_file():
            issues.append(f"{relative}: missing from current checkout or is a symlink")
            continue

        baseline_path = f"{baseline}:{relative}"
        in_baseline = run_git(root, ["cat-file", "-e", baseline_path], check=False).returncode == 0
        if in_baseline:
            try:
                data = git_show(root, str(baseline), relative)
            except RuntimeError as exc:
                issues.append(str(exc))
                continue
        else:
            addition = row.get("post_baseline_addition")
            if not isinstance(addition, dict):
                issues.append(f"{relative}: absent from baseline without explicit post_baseline_addition provenance")
                continue
            source_id = addition.get("source_asset_id")
            source_path = addition.get("source_path")
            source_sha = addition.get("source_sha256")
            source_row = rows_by_id.get(source_id)
            if not isinstance(source_row, dict):
                issues.append(f"{relative}: post-baseline source asset is not registered: {source_id!r}")
                continue
            if source_row.get("path") != source_path or source_row.get("sha256") != source_sha:
                issues.append(f"{relative}: post-baseline source path/SHA does not match its registry row")
                continue
            if addition.get("artifact_type") not in {"DERIVED_FROM_FROZEN_RASTER", "BRAND_TITLE_EXTRACTION_EVIDENCE"}:
                issues.append(f"{relative}: unrecognized post-baseline addition type")
                continue
            try:
                if run_git(root, ["cat-file", "-e", f"{baseline}:{source_path}"], check=False).returncode == 0:
                    source_data = git_show(root, str(baseline), str(source_path))
                else:
                    source_file = root.joinpath(*PurePosixPath(str(source_path)).parts)
                    if source_file.is_symlink() or not source_file.is_file():
                        raise RuntimeError("source file is missing or is a symlink")
                    source_data = source_file.read_bytes()
            except RuntimeError as exc:
                issues.append(f"{relative}: cannot verify post-baseline source: {exc}")
                continue
            if hashlib.sha256(source_data).hexdigest() != source_sha:
                issues.append(f"{relative}: post-baseline source raw-byte SHA-256 mismatch")
                continue
            if addition.get("approval_status") not in {"APPROVED_VISUALLY", "APPROVED_AUDIT_EVIDENCE"}:
                issues.append(f"{relative}: post-baseline addition is not approved")
                continue
            data = worktree_file.read_bytes()

        actual_sha256 = hashlib.sha256(data).hexdigest()
        if actual_sha256 != recorded_sha256:
            issues.append(f"{relative}: raw-byte SHA-256 mismatch")
        if len(data) != row.get("size"):
            issues.append(f"{relative}: raw-byte size mismatch")
        recorded_git_sha = row.get("git_blob_sha")
        if not isinstance(recorded_git_sha, str) or not SHA1_RE.fullmatch(recorded_git_sha):
            issues.append(f"{relative}: invalid separate Git blob identity")
        elif git_blob_sha1(data) != recorded_git_sha:
            issues.append(f"{relative}: Git blob identity mismatch")
        if row.get("status") == "FROZEN":
            current_data = worktree_file.read_bytes()
            if in_baseline and current_data != data:
                issues.append(f"{relative}: Frozen baseline asset content changed")
            if not in_baseline and current_data != data:
                issues.append(f"{relative}: Frozen derived asset differs from its verified worktree bytes")
            if not in_baseline and row.get("post_baseline_addition", {}).get("approval_status") != "APPROVED_VISUALLY":
                issues.append(f"{relative}: Frozen derived asset lacks visual approval")
        verified += 1

    summary = registry.get("summary", {})
    identity = registry.get("identity_verification", {})
    expected_total = len(rows)
    if summary.get("total_files") != expected_total:
        issues.append("asset summary total_files does not match entry count")
    if len(asset_ids) != expected_total or len(paths) != expected_total:
        issues.append(f"asset identities are not unique: ids={len(asset_ids)}, paths={len(paths)}, expected={expected_total}")
    if verified != expected_total:
        issues.append(f"raw-byte asset verification expected {expected_total}; verified {verified}")
    for field in ("total_files", "verified_sha256", "size_matches"):
        if identity.get(field) != expected_total:
            issues.append(f"asset identity_verification.{field} does not match {expected_total} entries")
    if identity.get("source_commit_sha") != baseline:
        issues.append("asset identity_verification source commit does not match baseline_commit_sha")
    return len(asset_ids), verified


def run(root: Path, *, strict_ready: bool = False) -> int:
    issues: list[str] = []
    try:
        typography = load_json(root, TYPE_PATH)
        color = load_json(root, COLOR_PATH)
        geometry = load_json(root, GEOMETRY_PATH)
        components = load_json(root, COMPONENT_PATH)
        assets = load_json(root, ASSET_PATH)
        icons = load_json(root, ICON_PATH)
        backgrounds = load_json(root, BACKGROUND_PATH)
        freeze = load_json(root, FREEZE_PATH)
        authorities = load_json(root, AUTHORITY_PATH)
        product_flow = load_json(root, FLOW_PATH)
    except ValueError as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 1

    type_ids = unique_ids(typography.get("tokens"), "id", "typography tokens", issues)
    color_ids = unique_ids(color.get("tokens"), "id", "color tokens", issues)
    geometry_ids = unique_ids(geometry.get("tokens"), "id", "geometry tokens", issues)
    component_ids = unique_ids(components.get("entries"), "component_id", "components", issues)
    asset_ids, verified_assets = verify_assets(root, assets, issues)
    icon_ids = unique_ids(icons.get("entries"), "icon_id", "icons", issues)
    background_ids = unique_ids(backgrounds.get("backgrounds"), "background_id", "backgrounds", issues)

    declared_component_fields = components.get("required_fields")
    if not isinstance(declared_component_fields, list) or not declared_component_fields:
        issues.append("components: required_fields must be a non-empty list")

    for token in typography.get("tokens", []):
        if isinstance(token, dict) and token.get("color") not in color_ids:
            issues.append(f"{token.get('id')}: unknown color token {token.get('color')!r}")

    component_rows = components.get("entries", [])
    token_ids = type_ids | color_ids | geometry_ids
    registered_asset_ids = {
        row.get("asset_id") for row in assets.get("entries", []) if isinstance(row, dict)
    }
    external_ids = {
        row.get("id") for row in components.get("external_asset_dependencies", []) if isinstance(row, dict)
    }
    registry_ids = {"icon_registry_v3", "background_registry_v3"}
    # Final-freeze semantics: registry completeness and the approved brand artwork contract.
    type_fields = {"id", "name", "family", "size", "weight", "lineHeight", "letterSpacing", "color", "usage"}
    for token in typography.get("tokens", []):
        if not isinstance(token, dict):
            continue
        missing = sorted(field for field in type_fields if token.get(field) in (None, ""))
        if missing:
            issues.append(f"{token.get('id', '<unknown>')}: incomplete typography token fields {missing}")
        if token.get("family") not in {row.get("id") for row in typography.get("font_families", []) if isinstance(row, dict)}:
            issues.append(f"{token.get('id')}: family is not registered")
    brand = typography.get("brand_asset_contract")
    if not isinstance(brand, dict):
        issues.append("Typography: missing brand_asset_contract")
    else:
        if brand.get("type") != "FROZEN_ARTWORK_ASSET" or brand.get("dynamic_text") is not False or brand.get("font_dependency") != "none":
            issues.append("Typography: Brand Typography must be FROZEN_ARTWORK_ASSET, dynamic_text=false, font_dependency=none")
        brand_row = next((row for row in assets.get("entries", []) if isinstance(row, dict) and row.get("asset_id") == brand.get("asset_id")), None)
        if not brand_row or brand_row.get("path") != brand.get("source_asset") or brand_row.get("status") != "FROZEN" or brand_row.get("sha256") != brand.get("sha256"):
            issues.append("Typography: brand artwork asset identity is missing, non-FROZEN, or SHA-mismatched")
    allowed_families = {row.get("id") for row in typography.get("font_families", []) if isinstance(row, dict)}
    for override in typography.get("page_overrides", []):
        if not isinstance(override, dict):
            issues.append("Typography: page override is not an object")
            continue
        family = override.get("family") or override.get("font_family")
        if family and family not in allowed_families:
            issues.append(f"Typography: illegal page font override {family!r} on {override.get('page')}")
        if "brand" in str(override.get("page", "")).lower() and override.get("status") not in {"FROZEN_ARTWORK_ASSET", "FROZEN_PAGE_OVERRIDE"}:
            issues.append(f"Typography: Brand page override is not artwork-backed: {override.get('page')}")

    body_small = next((row for row in typography.get("tokens", []) if isinstance(row, dict) and row.get("id") == "type.body_small"), None)
    if not body_small or body_small.get("size") != "14sp" or body_small.get("weight") != "Regular (400)" or body_small.get("color") != "color.mist_blue_gray":
        issues.append("Typography: Recognition Processing body_small must be 14sp Regular secondary")
    processing_override = next((row for row in typography.get("page_overrides", []) if isinstance(row, dict) and row.get("page") == "Recognition Processing" and row.get("role") == "processing.subtitle" and row.get("token") == "type.body_small" and row.get("status") == "FROZEN_PAGE_OVERRIDE"), None)
    if not processing_override:
        issues.append("Typography: processing.subtitle has no effective page override to type.body_small")

    # Color semantic roles must be fully tokenized; Danger has a separate page-only scope.
    color_fields = {"id", "hex", "rgba", "opacity", "usage", "forbidden_usage", "source_paths"}
    for token in color.get("tokens", []):
        if not isinstance(token, dict):
            continue
        missing = sorted(field for field in color_fields if token.get(field) in (None, "", []))
        if missing:
            issues.append(f"{token.get('id', '<unknown>')}: incomplete color token fields {missing}")
    semantic = color.get("semantic_role_mapping", {})
    brand_roles = semantic.get("Brand", {}) if isinstance(semantic, dict) else {}
    for role in ("Primary", "Secondary"):
        row = brand_roles.get(role, {})
        if row.get("status") != "READY" or row.get("token_ref") not in color_ids:
            issues.append(f"Color: Brand {role} has no effective token mapping")
    status_roles = semantic.get("Status", []) if isinstance(semantic, dict) else []
    status_by_role = {row.get("role"): row for row in status_roles if isinstance(row, dict)}
    for role in ("Success", "Warning", "Error", "Info"):
        row = status_by_role.get(role, {})
        if row.get("status") != "READY":
            issues.append(f"Color: {role} semantic role is not READY")
        for ref_field in ("foreground_token_ref", "surface_token_ref"):
            if row.get(ref_field) not in color_ids:
                issues.append(f"Color: {role} {ref_field} is missing or unresolved")
    danger = semantic.get("Danger", {}) if isinstance(semantic, dict) else {}
    if danger.get("scope") != "PAGE_SCOPED" or danger.get("forbidden_global_promotion") is not True:
        issues.append("Color: Danger must remain explicitly Page Scoped")
    if danger.get("token_ref") in {status_by_role.get("Error", {}).get("foreground_token_ref"), status_by_role.get("Error", {}).get("surface_token_ref")} :
        issues.append("Color: page Danger token has been promoted to global Error")
    if color.get("orphan_scan", {}).get("unregistered_after_compatibility") != 0:
        issues.append("Color: reported orphan literal count is not zero")

    # Geometry baseline and shared video aspect behavior are part of the frozen contract.
    if geometry.get("baseline_widths_dp") != [360, 393, 412, 480]:
        issues.append("Geometry: reference widths must be [360, 393, 412, 480]dp")
    if "geometry.video.aspect_policy" not in geometry_ids:
        issues.append("Geometry: shared video aspect policy token is missing")

    if len(component_rows) != 18:
        issues.append(f"Components: expected 18 public component contracts; found {len(component_rows)}")
    for component in component_rows if isinstance(component_rows, list) else []:
        if isinstance(component, dict) and component.get("status") != "FROZEN":
            issues.append(f"{component.get('component_id')}: component must be FROZEN for READY")
        if isinstance(component, dict):
            contract = component.get("component_contract", {})
            if contract.get("unresolved_blockers") != []:
                issues.append(f"{component.get('component_id')}: unresolved component blockers remain")
    camera_preview = next((row for row in component_rows if isinstance(row, dict) and row.get("component_id") == "media.camera_preview"), {})
    capture_surface = next((row for row in component_rows if isinstance(row, dict) and row.get("component_id") == "camera.capture_surface"), {})
    if camera_preview.get("status") != "FROZEN" or camera_preview.get("type") != "runtime_surface":
        issues.append("Components: media.camera_preview must be a FROZEN runtime_surface")
    if capture_surface.get("status") != "FROZEN" or capture_surface.get("type") != "runtime_surface":
        issues.append("Components: camera.capture_surface must be a FROZEN runtime_surface")
    for item in (camera_preview, capture_surface):
        if item.get("component_id") and item.get("component_contract", {}).get("unresolved_blockers") != []:
            issues.append(f"Components: {item.get('component_id')} has unresolved blockers")

    selector = next((row for row in component_rows if isinstance(row, dict) and row.get("component_id") == "input.selector"), {})
    required_selector_states = {"CLOSED", "OPEN", "QUERY_INPUT", "SEARCHING", "RESULTS", "NO_RESULTS", "EMPTY_DATA", "ERROR", "SELECTED", "MULTI_SELECTED", "COMMITTED", "RETURNED", "CANCELLED"}
    if not required_selector_states.issubset(set(selector.get("state", []))):
        issues.append("Components: Selector Core state contract is incomplete")
    video = next((row for row in component_rows if isinstance(row, dict) and row.get("component_id") == "media.video"), {})
    if not {"IDLE", "LOADING", "PLAYING", "PAUSED", "ERROR", "UNAVAILABLE"}.issubset(set(video.get("state", []))):
        issues.append("Components: Video Surface state contract is incomplete")

    for component in component_rows if isinstance(component_rows, list) else []:
        if not isinstance(component, dict):
            continue
        cid = component.get("component_id", "<unknown>")
        for field in declared_component_fields if isinstance(declared_component_fields, list) else []:
            if field not in component:
                issues.append(f"{cid}: missing declared required field {field}")
        for dependency in component.get("token_dependency", []):
            if dependency not in token_ids:
                issues.append(f"{cid}: orphan token reference {dependency!r}")
        for dependency in component.get("component_dependency", []):
            if dependency not in component_ids:
                issues.append(f"{cid}: unknown component dependency {dependency!r}")
        for dependency in component.get("asset_dependency", []):
            if dependency not in registered_asset_ids:
                issues.append(f"{cid}: unknown asset dependency {dependency!r}")
        for dependency in component.get("external_asset_dependency", []):
            if dependency not in external_ids:
                issues.append(f"{cid}: unknown external asset dependency {dependency!r}")
        for dependency in component.get("registry_dependency", []):
            if dependency not in registry_ids:
                issues.append(f"{cid}: unknown registry dependency {dependency!r}")
        for field in ("purpose", "visual_rule", "geometry", "state", "interaction", "responsive_rule", "accessibility_rule", "acceptance_rule"):
            value = component.get(field)
            if not value:
                issues.append(f"{cid}: missing component contract field {field}")
        contract = component.get("component_contract")
        if not isinstance(contract, dict):
            issues.append(f"{cid}: component_contract must be an object")
        else:
            for field in ("shared_visual_core", "shared_geometry_boundary", "state_model", "page_variants", "responsive_rule", "accessibility_rule", "asset_rule", "acceptance_checks", "unresolved_blockers"):
                if field not in contract or contract[field] is None or contract[field] == "":
                    issues.append(f"{cid}: missing component_contract.{field}")
            if not isinstance(contract.get("acceptance_checks"), list) or not contract.get("acceptance_checks"):
                issues.append(f"{cid}: component_contract.acceptance_checks must be a non-empty list")
            if not isinstance(contract.get("unresolved_blockers"), list):
                issues.append(f"{cid}: component_contract.unresolved_blockers must be a list")
            variants = contract.get("page_variants", [])
            if not isinstance(variants, list) or not variants:
                issues.append(f"{cid}: component_contract.page_variants must be a non-empty list")
            else:
                variant_ids = [variant.get("page_id") for variant in variants if isinstance(variant, dict)]
                if sorted(variant_ids) != sorted(component.get("page_references", [])):
                    issues.append(f"{cid}: page_variants do not match page_references")
                for variant in variants:
                    if not isinstance(variant, dict):
                        issues.append(f"{cid}: page variant is not an object")
                        continue
                    paths = variant.get("authority_paths")
                    if not isinstance(paths, list) or not paths:
                        issues.append(f"{cid}: page variant {variant.get('page_id')} has no authority_paths")
                        continue
                    for path in paths:
                        if not safe_relative(path, {"design", "app"}) or not (root / path).is_file():
                            issues.append(f"{cid}: page variant authority missing or unsafe: {path!r}")
        if not isinstance(component.get("authority_dependencies"), list) or not component["authority_dependencies"]:
            issues.append(f"{cid}: no authority_dependencies")
        else:
            for authority in component["authority_dependencies"]:
                if not safe_relative(authority, {"design", "app"}):
                    issues.append(f"{cid}: unsafe authority path {authority!r}")
                elif not (root / authority).is_file():
                    issues.append(f"{cid}: authority path not present {authority}")
        if not isinstance(component.get("page_references"), list) or not component["page_references"]:
            issues.append(f"{cid}: no explicit page_references")
        if not isinstance(component.get("token_gap"), list):
            issues.append(f"{cid}: token_gap must be a list")

    # Effective authority registry: every domain has one explicit canonical source;
    # review-only candidates stay separately typed and all paths resolve in-repo.
    authority_rows = authorities.get("entries")
    authority_ids = unique_ids(authority_rows, "authority_id", "effective authorities", issues)
    seen_domains: set[str] = set()
    for row in authority_rows if isinstance(authority_rows, list) else []:
        if not isinstance(row, dict):
            continue
        domain = row.get("domain")
        if not isinstance(domain, str) or not domain:
            issues.append(f"{row.get('authority_id')}: missing authority domain")
        elif domain in seen_domains:
            issues.append(f"effective authorities: duplicate domain {domain!r}")
        else:
            seen_domains.add(domain)
        paths = [row.get("canonical_path"), *row.get("supporting_paths", []), *row.get("review_only_paths", [])]
        for path in paths:
            if not safe_relative(path, {"design", "app"}) or not (root / path).is_file():
                issues.append(f"{row.get('authority_id')}: authority path missing or unsafe: {path!r}")

    shared_system_names = {"Typography", "Color", "Geometry", "Background", "Icon", "Component", "Asset"}
    system_rows = [row for row in authority_rows if isinstance(row, dict) and row.get("system") in shared_system_names] if isinstance(authority_rows, list) else []
    systems_seen = [row.get("system") for row in system_rows]
    if set(systems_seen) != shared_system_names or len(systems_seen) != len(shared_system_names):
        issues.append("Effective Authority Registry must contain exactly one entry per shared system")
    effective_paths: set[str] = set()
    for row in system_rows:
        for field in ("effective_authority_path", "version", "status", "supersedes", "consumer_reference"):
            if field not in row or row[field] in (None, "", []):
                issues.append(f"{row.get('system')}: authority missing {field}")
        path = row.get("effective_authority_path")
        if path in effective_paths:
            issues.append(f"Effective Authority Registry has duplicate effective path {path}")
        effective_paths.add(path)
        if path != row.get("canonical_path") or not isinstance(path, str) or not (root / path).is_file():
            issues.append(f"{row.get('system')}: effective authority path is not the unique present canonical path")
        if row.get("status") != "READY":
            issues.append(f"{row.get('system')}: effective authority status is not READY")
    expected_type_path = TYPE_PATH
    type_auth = next((row for row in system_rows if row.get("system") == "Typography"), {})
    if type_auth.get("effective_authority_path") != expected_type_path:
        issues.append("Typography authority does not uniquely point to Typography_Tokens_V3_FINAL.json")
    old_type_path = root / "design/system_v3/04_Design_Tokens/Typography/Typography_Tokens_V3.json"
    try:
        old_type_doc = json.loads(old_type_path.read_text(encoding="utf-8"))
        if old_type_doc.get("status") != "DEPRECATED_NON_EFFECTIVE" or old_type_doc.get("superseded_by") != expected_type_path:
            issues.append("Non-final Typography V3 token file is not marked deprecated/non-effective")
    except (OSError, json.JSONDecodeError) as exc:
        issues.append(f"Non-final Typography token registry cannot be checked: {exc}")

    # Product-flow count fields are derived from the canonical arrays; audit indexes must agree.
    flow_nodes = product_flow.get("nodes", [])
    flow_edges = product_flow.get("edges", [])
    flow_features = {row.get("feature_id") for row in flow_nodes if isinstance(row, dict) and row.get("feature_id")}
    expected_flow_counts = {"nodes": len(flow_nodes), "edges": len(flow_edges), "registry_features": len(flow_features)}
    if product_flow.get("counts") != expected_flow_counts:
        issues.append(f"Master Flow counts do not match canonical arrays/features: expected {expected_flow_counts}")
    readiness_path = root / "design/system_v3/11_Freeze_Governance/audits/Design_Readiness_Baseline_V3_1.json"
    dependency_path = root / "design/system_v3/11_Freeze_Governance/audits/Preliminary_Dependency_Map_V3_1.json"
    completeness_path = root / "design/system_v3/11_Freeze_Governance/audits/Contract_Completeness_Matrix_V3_1.json"
    try:
        readiness_counts = json.loads(readiness_path.read_text(encoding="utf-8")).get("summary", {})
        dependency_counts = json.loads(dependency_path.read_text(encoding="utf-8")).get("counts", {})
        completeness = json.loads(completeness_path.read_text(encoding="utf-8"))
        if readiness_counts.get("product_flow_nodes") != len(flow_nodes) or readiness_counts.get("product_flow_edges") != len(flow_edges):
            issues.append("Design Readiness Baseline flow counts do not match Master Flow")
        if dependency_counts.get("product_flow_nodes") != len(flow_nodes) or dependency_counts.get("product_flow_edges") != len(flow_edges):
            issues.append("Preliminary Dependency Map flow counts do not match Master Flow")
        flow_row = next((row for row in completeness.get("contracts", []) if row.get("contract") == "Product / canonical flow"), None)
        if not flow_row or f"{len(flow_nodes)} nodes, {len(flow_edges)} edges" not in flow_row.get("evidence", ""):
            issues.append("Contract Completeness Matrix flow count does not match Master Flow")
        if flow_row and "Save Catch target conflicts" in flow_row.get("gap", ""):
            issues.append("Contract Completeness Matrix still lists the resolved Save Catch conflict")
    except (OSError, json.JSONDecodeError) as exc:
        issues.append(f"flow audit index cannot be read: {exc}")

    asset_by_path = {
        row.get("path"): row for row in assets.get("entries", []) if isinstance(row, dict)
    }
    for icon in icons.get("entries", []):
        if not isinstance(icon, dict):
            continue
        asset = asset_by_path.get(icon.get("file"))
        if not asset or asset.get("sha256") != icon.get("sha256"):
            issues.append(f"icon {icon.get('icon_id')}: Asset Registry path/SHA mismatch")
    if len(icon_ids) != 43:
        issues.append(f"Icon Registry expected 43 entries; found {len(icon_ids)}")

    for background in backgrounds.get("backgrounds", []):
        if not isinstance(background, dict):
            continue
        for reference in [background, background.get("page_override", {})]:
            path = reference.get("master_path")
            digest = reference.get("sha256")
            if path is None:
                continue
            asset = asset_by_path.get(path)
            if not asset or asset.get("sha256") != digest:
                issues.append(f"background {background.get('background_id')}: Asset Registry path/SHA mismatch for {path}")
    if len(background_ids) != 5:
        issues.append(f"Background Registry expected 5 rows; found {len(background_ids)}")

    literal_scanned, color_orphans, png_metadata_colors = scan_colors(root, color)
    if color_orphans:
        for item in color_orphans[:100]:
            issues.append(f"unregistered color literal: {item}")
        if len(color_orphans) > 100:
            issues.append(f"... {len(color_orphans) - 100} additional unregistered colors")

    gate = freeze.get("shared_foundation", {})
    systems = gate.get("systems", [])
    statuses = {row.get("id"): row.get("status") for row in systems if isinstance(row, dict)}
    required_systems = {"Typography", "Color", "Geometry", "Background", "Icon", "Component", "Asset"}
    if set(statuses) != required_systems:
        issues.append("freeze status must contain exactly the seven shared systems")
    ready_count = sum(statuses.get(name) == "READY" for name in required_systems)
    expected_state = "SHARED_FOUNDATION_READY" if ready_count == 7 else "NOT_READY"
    if gate.get("state") != expected_state:
        issues.append(f"freeze state {gate.get('state')!r} does not match system statuses ({ready_count}/7)")
    if gate.get("phase3_allowed") is not (ready_count == 7):
        issues.append("Phase 3 entry flag does not match seven-system readiness")
    readiness_path = root / "design/system_v3/11_Freeze_Governance/audits/Design_Readiness_Baseline_V3_1.json"
    try:
        readiness = json.loads(readiness_path.read_text(encoding="utf-8"))
        readiness_gate = readiness.get("shared_foundation_gate", {})
        if readiness_gate.get("current_state") != expected_state:
            issues.append("Design Readiness Baseline gate state does not match Freeze Status")
        if readiness_gate.get("phase3_entry_allowed") is not (ready_count == 7):
            issues.append("Design Readiness Baseline Phase 3 flag does not match Freeze Status")
        certification = readiness_gate.get("certification", {})
        if ready_count == 7 and (certification.get("gate") != "SHARED_FOUNDATION_READY" or certification.get("status") != "PASS"):
            issues.append("Design Readiness Baseline is missing the SHARED_FOUNDATION_READY PASS certificate")
        report_path = root / "design/system_v3/11_Freeze_Governance/Shared_Foundation_Readiness_Report_V3_1.md"
        report_text = report_path.read_text(encoding="utf-8")
        if ready_count == 7 and '"gate":"SHARED_FOUNDATION_READY","status":"PASS"' not in report_text:
            issues.append("Shared Foundation Readiness Report is missing the SHARED_FOUNDATION_READY PASS record")
    except (OSError, json.JSONDecodeError) as exc:
        issues.append(f"Design Readiness Baseline cannot be read: {exc}")

    if strict_ready and ready_count != 7:
        issues.append(f"strict readiness requested but only {ready_count}/7 systems are READY")

    if issues:
        print(f"FAIL: {len(issues)} shared foundation issue(s)")
        for issue in issues[:150]:
            print(f" - {issue}")
        if len(issues) > 150:
            print(f" - ... {len(issues) - 150} more")
        return 1

    print("PASS: Shared Foundation V3.1 structural validation")
    print(f"  Typography tokens: {len(type_ids)}")
    print(f"  Color tokens: {len(color_ids)}; unregistered color literals: {len(color_orphans)}")
    print(f"  Geometry tokens: {len(geometry_ids)}")
    print(f"  Components: {len(component_ids)}")
    print(f"  Effective authorities: {len(authority_ids)}")
    print(f"  Product flow: {len(flow_nodes)} nodes, {len(flow_edges)} edges, {len(flow_features)} registered features")
    print(f"  Assets: {asset_ids}; raw-byte SHA-256 verified: {verified_assets}")
    print(f"  Icons: {len(icon_ids)}; Backgrounds: {len(background_ids)}")
    print(f"  Color-scan sources: {literal_scanned}; PNG metadata color literals: {png_metadata_colors}")
    print(f"  Shared Foundation gate: {gate.get('state')} ({ready_count}/7 READY)")
    return 0


def self_test() -> int:
    cases = {
        "#ABC": "#aabbccff",
        "#abcd": "#aabbccdd",
        "#AABBCC": "#aabbccff",
        "#AABBCC80": "#aabbcc80",
        "rgba(255, 255, 255, 0.76)": "#ffffffc2",
        "rgb(15, 122, 120)": "#0f7a78ff",
    }
    for raw, expected in cases.items():
        if canonical_color(raw) != expected:
            print(f"FAIL: canonical color {raw}: got {canonical_color(raw)} expected {expected}", file=sys.stderr)
            return 1
    if not safe_relative("design/pages/example.png", {"design"}):
        print("FAIL: relative path self-test", file=sys.stderr)
        return 1
    if safe_relative("design/../outside", {"design"}) or safe_relative("/design/file", {"design"}):
        print("FAIL: unsafe path self-test", file=sys.stderr)
        return 1
    sample = b"abc"
    if hashlib.sha256(sample).hexdigest() != "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad":
        print("FAIL: SHA-256 self-test", file=sys.stderr)
        return 1
    print("PASS: color normalization, safe paths, and SHA-256 self-tests")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo-root", default=".", help="Repository checkout root.")
    parser.add_argument("--strict-ready", action="store_true", help="Fail unless all seven systems are READY.")
    parser.add_argument("--self-test", action="store_true", help="Run helper checks without repository data.")
    args = parser.parse_args()
    if args.self_test:
        return self_test()
    return run(Path(args.repo_root).resolve(), strict_ready=args.strict_ready)


if __name__ == "__main__":
    raise SystemExit(main())
