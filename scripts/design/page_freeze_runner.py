#!/usr/bin/env python3
"""Run W1 contract and W2 resource checks for one registered page and emit W4 evidence."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath
from typing import Any
from urllib.parse import urlparse

from jsonschema import Draft202012Validator, FormatChecker


ROOT = Path(__file__).resolve().parents[2]
SYSTEM = "design/system_v3/07_Responsive_System"
W1_MATRIX = f"{SYSTEM}/Page_Responsive_Contract_Schema_Validation_Matrix_V1.json"
W1_VALIDATOR = "scripts/design/validate_page_responsive_contract.py"
W2_VALIDATOR = "scripts/design/validate_page_resource_binding.py"
EVIDENCE_SCHEMA = "scripts/design/schemas/Page_Evidence_Manifest_Schema_V1.json"
W4_GATE = "G-PAGE-FREEZE-PIPELINE-W4-TEST-EVIDENCE"
SHA1 = re.compile(r"^[0-9a-f]{40}$")
SHA256 = re.compile(r"^[0-9a-f]{64}$")


class PipelineError(Exception):
    pass


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def safe_repo_path(root: Path, value: str) -> Path:
    posix = PurePosixPath(value)
    if not value or posix.is_absolute() or ".." in posix.parts or "\\" in value:
        raise PipelineError(f"unsafe repository path: {value!r}")
    path = root.joinpath(*posix.parts).resolve()
    try:
        path.relative_to(root.resolve())
    except ValueError as exc:
        raise PipelineError(f"repository path escapes root: {value!r}") from exc
    if not path.is_file():
        raise PipelineError(f"repository file is missing: {value}")
    return path


def read_json(path: Path) -> Any:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise PipelineError(f"cannot read JSON {path}: {exc}") from exc


def current_sha(root: Path) -> str:
    result = subprocess.run(["git", "rev-parse", "HEAD"], cwd=root, capture_output=True, text=True)
    if result.returncode:
        raise PipelineError(f"cannot resolve repository HEAD: {result.stderr.strip()}")
    sha = result.stdout.strip()
    if not SHA1.fullmatch(sha):
        raise PipelineError("repository HEAD is not a full commit SHA")
    return sha


def require_clean_source_tree(root: Path) -> None:
    """Bind reports to a clean source tree, allowing only known generated runtime output."""
    result = subprocess.run(
        ["git", "status", "--porcelain=v1", "--untracked-files=all", "-z"],
        cwd=root, capture_output=True, text=True,
    )
    if result.returncode:
        raise PipelineError(f"cannot inspect source worktree: {result.stderr.strip()}")
    unexpected: list[str] = []
    records = result.stdout.split("\0")
    index = 0
    while index < len(records):
        row = records[index]
        index += 1
        if not row:
            continue
        if len(row) < 4:
            unexpected.append(row)
            continue
        status, path = row[:2], row[3:]
        paths = [path]
        # NUL-delimited rename/copy records carry a second path. Treat either
        # side as dirty so a rename into an allowed generated directory cannot
        # conceal a source-tree change.
        if "R" in status or "C" in status:
            if index < len(records):
                paths.append(records[index])
                index += 1
        for candidate in paths:
            candidate = candidate.replace("\\", "/")
            if (candidate in {"PROJECT_MASTER_STATE.yaml", "WORK_EXECUTION_STATE.json"}
                    or candidate == "_site" or candidate.startswith("_site/")
                    or "__pycache__/" in candidate or candidate.endswith("/__pycache__")
                    or candidate.endswith((".pyc", ".pyo"))):
                continue
            unexpected.append(candidate)
    if unexpected:
        raise PipelineError("source worktree is dirty; exact-SHA evidence requires a clean checkout: "
                            + ", ".join(sorted(set(unexpected))))


def resolve_page_case(root: Path, page_id: str, contract: str | None, profile: str | None) -> dict[str, str]:
    matrix = read_json(safe_repo_path(root, W1_MATRIX))
    matches = [case for case in matrix.get("cases", []) if isinstance(case, dict) and case.get("page_id") == page_id]
    if len(matches) != 1:
        raise PipelineError(f"W1 matrix must contain exactly one registered case for {page_id}; found {len(matches)}")
    case = matches[0]
    if (contract is None) != (profile is None):
        raise PipelineError("--contract and --profile must be supplied together")
    contract_path = contract or case.get("envelope")
    profile_path = profile or case.get("profile")
    if not isinstance(contract_path, str) or not isinstance(profile_path, str):
        raise PipelineError(f"W1 contract/profile references are incomplete for {page_id}")
    safe_repo_path(root, contract_path)
    safe_repo_path(root, profile_path)
    envelope = read_json(root / contract_path)
    profile_data = read_json(root / profile_path)
    if envelope.get("page", {}).get("page_id") != page_id or profile_data.get("page_id") != page_id:
        raise PipelineError(f"wrong page_id in contract/profile for {page_id}")
    return {"contract": contract_path, "profile": profile_path, "case_id": str(case.get("case_id", page_id))}


def run_json_command(argv: list[str], root: Path, label: str) -> tuple[int, dict[str, Any], str, str]:
    result = subprocess.run(argv, cwd=root, capture_output=True, text=True)
    try:
        data = json.loads(result.stdout) if result.stdout.strip() else {}
    except json.JSONDecodeError as exc:
        raise PipelineError(f"{label} did not emit JSON: {exc}; stderr={result.stderr[-1200:]}") from exc
    if not isinstance(data, dict):
        raise PipelineError(f"{label} result must be a JSON object")
    return result.returncode, data, result.stdout, result.stderr


def resolve_numeric_authority(root: Path, envelope: dict[str, Any], profile: dict[str, Any]) -> tuple[str | None, dict[str, Any] | None]:
    viewport_inputs = envelope.get("viewport_inputs") if isinstance(envelope.get("viewport_inputs"), dict) else {}
    authority_id = viewport_inputs.get("viewport_authority_ref_id") or profile.get("numeric_authority", {}).get("authority_ref_id")
    if not isinstance(authority_id, str):
        return None, None
    refs = [row for row in envelope.get("authority_refs", []) if isinstance(row, dict) and row.get("authority_ref_id") == authority_id]
    if len(refs) != 1:
        return None, None
    path = refs[0].get("path")
    if not isinstance(path, str) or not path.lower().endswith(".json"):
        return path if isinstance(path, str) else None, None
    try:
        return path, read_json(safe_repo_path(root, path))
    except PipelineError:
        return path, None


def _explicit_matrix(authority: dict[str, Any]) -> tuple[str | None, list[dict[str, Any]]]:
    acceptance = authority.get("native_acceptance")
    if not isinstance(acceptance, dict):
        return None, []
    matrix_path = acceptance.get("matrix_path")
    return (matrix_path if isinstance(matrix_path, str) else None), []


def _state_values(root: Path, envelope: dict[str, Any], authority_by_id: dict[str, Any]) -> tuple[list[str], list[str], list[dict[str, Any]]]:
    resolved: list[str] = []
    unresolved: list[str] = []
    resolved_refs: list[dict[str, Any]] = []
    refs = envelope.get("state_scope", {}).get("included_state_refs", [])
    page_id = envelope.get("page", {}).get("page_id")
    authority_rows = envelope.get("authority_refs", [])
    for ref in refs if isinstance(refs, list) else []:
        if not isinstance(ref, dict):
            continue
        authority_id = ref.get("authority_ref_id")
        authority = authority_by_id.get(authority_id)
        authority_ref = next((row for row in authority_rows
                              if isinstance(row, dict) and row.get("authority_ref_id") == authority_id), {})
        path_text = authority_ref.get("path")
        locator = ref.get("locator")
        value: Any = None
        resolved_locator = locator
        resolution = "EXACT_LOCATOR"
        if isinstance(authority, dict) and isinstance(locator, str) and locator.startswith("/"):
            current: Any = authority
            for part in locator[1:].split("/"):
                token = part.replace("~1", "/").replace("~0", "~")
                if isinstance(current, dict):
                    current = current.get(token)
                elif isinstance(current, list) and token.isdigit() and int(token) < len(current):
                    current = current[int(token)]
                else:
                    current = None
                    break
            value = current
        elif isinstance(authority, dict) and isinstance(locator, str) and locator.startswith("heading:"):
            if isinstance(path_text, str) and path_text.lower().endswith((".md", ".markdown")):
                try:
                    path = safe_repo_path(root, path_text)
                    expected_digest = authority_ref.get("sha256")
                    if not isinstance(expected_digest, str) or sha256_file(path) != expected_digest:
                        raise PipelineError(f"state authority SHA-256 mismatch or missing pin: {path_text}")
                    lines = path.read_text(encoding="utf-8").splitlines()

                    def section_for(heading: str) -> str | None:
                        heading_re = re.compile(r"^(#{1,6})[ \t]+" + re.escape(heading) + r"[ \t]*#*[ \t]*$")
                        matches = [(i, match) for i, line in enumerate(lines)
                                   if (match := heading_re.fullmatch(line))]
                        if len(matches) != 1:
                            return None
                        start_index, heading_match = matches[0]
                        level = len(heading_match.group(1))
                        end_index = next((i for i in range(start_index + 1, len(lines))
                                          if re.match(r"^#{1," + str(level) + r"}[ \t]+", lines[i])), len(lines))
                        return "\n".join(lines[start_index + 1:end_index])

                    heading = locator[len("heading:"):].strip()
                    section_text = section_for(heading)
                    if section_text is None and (
                        page_id == "home_empty_v2"
                        and authority_id == "empty-home-behavior"
                        and path_text == "design/pages/home/empty_home/spec/Empty_Home_Feature_Spec_V2.md"
                        and locator == "heading:empty state"
                    ):
                        # The legacy locator is absent. Resolve only this page's
                        # explicit behavior alias against the pinned Scope section.
                        candidate = section_for("Scope")
                        selected = re.findall(r"\bselects[ \t]+`?([A-Z][A-Z0-9_]*)`?", candidate or "")
                        if (candidate is not None
                                and re.search(r"`?fishRecords\.isEmpty\(\)`?\s+selects\s+`?EMPTY`?", candidate)
                                and selected == ["EMPTY"]):
                            section_text = candidate
                            resolved_locator = "heading:Scope"
                            resolution = "PAGE_SPECIFIC_EXPLICIT_STATE_MAPPING"
                    if section_text is not None:
                        value = re.findall(r"\bselects[ \t]+`?([A-Z][A-Z0-9_]*)`?", section_text)
                except (OSError, UnicodeDecodeError, PipelineError):
                    value = None
        if isinstance(value, list) and value and all(isinstance(item, str) and item.strip() for item in value):
            state_ids = list(dict.fromkeys(value))
            resolved.extend(state_ids)
        elif isinstance(value, str) and value.strip():
            state_ids = [value]
            resolved.append(value)
        else:
            state_ids = []
            unresolved.append(f"{authority_id}::{locator}")
            resolution = "UNRESOLVED"
        resolved_refs.append({
            "authority_ref_id": authority_id,
            "path": path_text,
            "sha256": authority_ref.get("sha256"),
            "declared_locator": locator,
            "locator": resolved_locator,
            "resolution": resolution,
            "resolved_state_ids": state_ids,
        })
    return list(dict.fromkeys(resolved)), unresolved, resolved_refs

def _valid_viewport(row: Any) -> dict[str, Any] | None:
    if not isinstance(row, dict):
        return None
    width, height = row.get("width_dp"), row.get("height_dp")
    if isinstance(width, (int, float)) and not isinstance(width, bool) and width > 0 and isinstance(height, (int, float)) and not isinstance(height, bool) and height > 0:
        return {"width": width, "height": height, "unit": "dp"}
    return None


def derive_matrix(root: Path, envelope: dict[str, Any], profile: dict[str, Any]) -> dict[str, Any]:
    """Derive a test plan only from page-owned machine authority; never invent missing inputs."""
    authority_path, numeric_authority = resolve_numeric_authority(root, envelope, profile)
    viewport_inputs = envelope.get("viewport_inputs") if isinstance(envelope.get("viewport_inputs"), dict) else {}
    source_authority_ref_id = (viewport_inputs.get("viewport_authority_ref_id")
                               or profile.get("numeric_authority", {}).get("authority_ref_id"))
    if not isinstance(source_authority_ref_id, str):
        source_authority_ref_id = None
    authority_by_id: dict[str, Any] = {}
    for ref in envelope.get("authority_refs", []):
        if not isinstance(ref, dict) or not isinstance(ref.get("path"), str):
            continue
        try:
            candidate = safe_repo_path(root, ref["path"])
            if candidate.suffix.lower() == ".json":
                authority_by_id[ref.get("authority_ref_id")] = read_json(candidate)
            elif candidate.suffix.lower() in {".md", ".markdown"}:
                authority_by_id[ref.get("authority_ref_id")] = {"__markdown_authority__": True}
        except PipelineError:
            continue

    if not isinstance(numeric_authority, dict):
        return {"status": "NOT_RUN", "source_path": authority_path, "expected_case_count": 0, "recorded_case_count": 0,
                "cases": [], "reason": "No machine-readable viewport authority is bound to this page profile.",
                "source_sha256": None, "source_authority_ref_id": source_authority_ref_id,
                "state_authority_refs": []}

    matrix_path, _ = _explicit_matrix(numeric_authority)
    if matrix_path:
        try:
            matrix_doc = read_json(safe_repo_path(root, matrix_path))
        except PipelineError as exc:
            return {"status": "FAIL", "source_path": matrix_path, "source_sha256": None,
                    "source_authority_ref_id": source_authority_ref_id,
                    "expected_case_count": 0, "recorded_case_count": 0, "state_authority_refs": [],
                    "cases": [], "reason": f"Required page matrix is unavailable: {exc}"}
        raw_profiles = matrix_doc.get("profiles")
        raw_variants = matrix_doc.get("variants")
        expected_count = matrix_doc.get("expected_native_executions")
        if not isinstance(raw_profiles, list) or not isinstance(raw_variants, list) or not isinstance(expected_count, int):
            return {"status": "FAIL", "source_path": matrix_path, "source_sha256": None,
                    "source_authority_ref_id": source_authority_ref_id,
                    "expected_case_count": 0, "recorded_case_count": 0, "state_authority_refs": [],
                    "recorded_case_count": 0, "cases": [], "reason": "Page-owned matrix is missing profiles, variants, or expected_native_executions."}
        cases = []
        for profile_row in raw_profiles:
            viewport = _valid_viewport(profile_row)
            if viewport is None:
                return {"status": "FAIL", "source_path": matrix_path, "source_sha256": None,
                        "source_authority_ref_id": source_authority_ref_id,
                    "expected_case_count": expected_count, "recorded_case_count": 0, "state_authority_refs": [],
                        "recorded_case_count": len(cases), "cases": [], "reason": "Page-owned matrix contains a profile without a fixed width/height viewport."}
            font = profile_row.get("font_scale")
            if not isinstance(font, (int, float)) or isinstance(font, bool) or font <= 0:
                return {"status": "FAIL", "source_path": matrix_path, "source_sha256": None,
                        "source_authority_ref_id": source_authority_ref_id,
                        "expected_case_count": expected_count, "state_authority_refs": [],
                        "recorded_case_count": len(cases), "cases": [], "reason": "Page-owned matrix contains a profile without a valid font scale."}
            profile_id = profile_row.get("id") or profile_row.get("profile_id")
            if not isinstance(profile_id, str) or not profile_id:
                return {"status": "FAIL", "source_path": matrix_path, "source_sha256": None,
                        "source_authority_ref_id": source_authority_ref_id,
                        "expected_case_count": expected_count, "state_authority_refs": [],
                        "recorded_case_count": len(cases), "cases": [], "reason": "Page-owned matrix profile is missing its id."}
            for variant in raw_variants:
                variant_id = variant.get("id") if isinstance(variant, dict) else variant
                if not isinstance(variant_id, str) or not variant_id:
                    return {"status": "FAIL", "source_path": matrix_path, "source_sha256": None,
                            "source_authority_ref_id": source_authority_ref_id,
                            "expected_case_count": expected_count, "state_authority_refs": [],
                            "recorded_case_count": len(cases), "cases": [], "reason": "Page-owned matrix variant is missing its id."}
                cases.append({"case_id": f"{profile_id}-{variant_id}", "viewport": viewport,
                              "font_scale": font, "state_id": variant_id, "result": "NOT_RUN",
                              "failure_reason": "The page-owned matrix is planned here; its owning browser/native gate has not been executed by this command."})
        if len(cases) != expected_count:
            return {"status": "FAIL", "source_path": matrix_path, "source_sha256": sha256_file(safe_repo_path(root, matrix_path)),
                    "source_authority_ref_id": source_authority_ref_id,
                    "state_authority_refs": [],
                    "recorded_case_count": len(cases), "cases": cases,
                    "reason": "Page-owned profile × variant case count does not match expected_native_executions."}
        return {"status": "NOT_RUN", "source_path": matrix_path,
                "source_sha256": sha256_file(safe_repo_path(root, matrix_path)),
                "source_authority_ref_id": source_authority_ref_id, "state_authority_refs": [],
                "expected_case_count": expected_count,
                "recorded_case_count": len(cases), "cases": cases, "reason": "Page-owned matrix cases are recorded as NOT_RUN until the corresponding exact-SHA runner executes."}

    viewport_rows: list[Any] = []
    for key in ("responsive_profiles_to_verify",):
        value = numeric_authority.get(key)
        if isinstance(value, list):
            viewport_rows.extend(value)
    preview = numeric_authority.get("preview_profiles")
    if isinstance(preview, list):
        viewport_rows.extend(row for row in preview if isinstance(row, dict) and row.get("preview_enabled") is True)
    anchors = numeric_authority.get("visual_anchors")
    if isinstance(anchors, list):
        viewport_rows.extend(row.get("logical_viewport_dp") for row in anchors if isinstance(row, dict))
    viewports: list[dict[str, Any]] = []
    for row in viewport_rows:
        normalized = _valid_viewport(row)
        if normalized and normalized not in viewports:
            viewports.append(normalized)

    font_values = numeric_authority.get("font_scale_profiles")
    if not isinstance(font_values, list):
        font_values = numeric_authority.get("native_acceptance", {}).get("font_scales", [])
    fonts = list(dict.fromkeys(value for value in font_values if isinstance(value, (int, float)) and not isinstance(value, bool) and value > 0)) if isinstance(font_values, list) else []
    states, unresolved_refs, state_authority_refs = _state_values(root, envelope, authority_by_id)
    state_slots: list[tuple[str | None, str | None]] = [(state, None) for state in states]
    if unresolved_refs:
        state_slots = [(None, ", ".join(unresolved_refs))]

    cases = []
    for viewport_index, viewport in enumerate(viewports):
        for font in fonts:
            for state, state_ref in state_slots:
                state_label = state or "unresolved-state-reference"
                cases.append({"case_id": f"{viewport['width']}x{viewport['height']}dp-font-{font}-state-{state_label}",
                              "viewport": viewport, "font_scale": font, "state_id": state,
                              "result": "NOT_RUN",
                              "failure_reason": f"Matrix is derived from {authority_path}; run is pending." if not state_ref else f"State identifiers are unresolved from the referenced authority: {state_ref}."})
    reason = None
    if not viewports:
        reason = "No fixed viewport anchors are present in the bound numeric authority; no viewport was invented."
    elif not fonts:
        reason = "No numeric font-scale set is declared by the bound authority; no scale was invented."
    elif not state_slots:
        reason = "No included page states can be resolved from the bound page authorities."
    return {"status": "NOT_RUN", "source_path": authority_path,
            "source_sha256": sha256_file(safe_repo_path(root, authority_path)) if authority_path else None,
            "source_authority_ref_id": source_authority_ref_id, "state_authority_refs": state_authority_refs,
            "expected_case_count": len(cases),
            "recorded_case_count": len(cases), "cases": cases,
            "reason": reason or "Cases are page-authority-derived and remain NOT_RUN until a browser/native runner executes."}


def import_structured_matrix_report(root: Path, page_id: str, source_sha: str, run_id: str | None,
                                    report_path: str, matrix: dict[str, Any], evidence_dir: Path) -> list[str]:
    """Attach exact-run W5 Chromium results and copied DOM/screenshot evidence to W4."""
    errors: list[str] = []
    try:
        source_path = Path(report_path).resolve()
        report = read_json(source_path)
    except (OSError, PipelineError) as exc:
        matrix["status"] = "FAIL"
        matrix["reason"] = f"W5 matrix report could not be read: {exc}"
        return [matrix["reason"]]

    matrix["execution_source_sha"] = report.get("candidate_source_sha") or report.get("source_sha")
    matrix["execution_run_id"] = str(report.get("github_run_id")) if report.get("github_run_id") is not None else None
    matrix["execution_report_path"] = None
    matrix["execution_report_sha256"] = None
    if page_id != "home_empty_v2":
        errors.append("W5 structured matrix report may only be bound to home_empty_v2")
    if report.get("schema") != "YUJIAN_PAGE_EVIDENCE_MANIFEST_V1" or report.get("page_id") != page_id:
        errors.append("W5 matrix report schema or page identity does not match the W4 page")
    if report.get("status") != "PASS_STRUCTURAL_MATRIX_VISUAL_REVIEW_PENDING":
        errors.append("W5 matrix report did not complete its structural run while preserving pending user visual review")
    if report.get("source_sha") != source_sha or report.get("candidate_source_sha") != source_sha:
        errors.append("W5 matrix report source SHA does not match this exact W4 candidate SHA")
    if not run_id or not str(run_id).isdigit() or str(report.get("github_run_id")) != str(run_id):
        errors.append("W5 matrix report Run ID does not match the observed W4 workflow Run ID")
    summary = report.get("matrix_summary") if isinstance(report.get("matrix_summary"), dict) else {}
    authority = report.get("matrix_authority") if isinstance(report.get("matrix_authority"), dict) else {}
    if (authority.get("id") != matrix.get("source_authority_ref_id")
            or authority.get("path") != matrix.get("source_path")
            or authority.get("sha256") != matrix.get("source_sha256")):
        errors.append("W5 matrix authority path/SHA does not match the W4-derived page authority")
    if summary.get("source_path") != matrix.get("source_path") or summary.get("source_sha256") != matrix.get("source_sha256"):
        errors.append("W5 matrix summary authority path/SHA does not match the W4-derived page authority")
    expected_count = matrix.get("expected_case_count")
    rows = report.get("matrix") if isinstance(report.get("matrix"), list) else []
    formal_rows = [row for row in rows if isinstance(row, dict) and row.get("matrix_classification") == "FORMAL_AUTHORITY_CASE"]
    supplemental_rows = [row for row in rows if isinstance(row, dict)
                          and row.get("matrix_classification") == "SUPPLEMENTAL_CANDIDATE_NOT_IN_FROZEN_AUTHORITY"]
    expected_viewports = {
        (row.get("viewport", {}).get("width"), row.get("viewport", {}).get("height"))
        for row in matrix.get("cases", []) if isinstance(row, dict) and isinstance(row.get("viewport"), dict)
    }
    supplemental_definition = report.get("supplemental_matrix_definition")
    supplemental_scale = supplemental_definition.get("font_scale") if isinstance(supplemental_definition, dict) else None
    expected_supplemental_count = len(expected_viewports)
    if (summary.get("expected") != expected_count or summary.get("executed") != expected_count
            or summary.get("pass") != expected_count or summary.get("fail") != 0 or summary.get("not_run") != 0
            or summary.get("formal_expected") != expected_count or summary.get("formal_executed") != expected_count
            or summary.get("formal_pass") != expected_count or summary.get("formal_fail") != 0
            or summary.get("formal_not_run") != 0 or len(formal_rows) != expected_count):
        errors.append("W5 formal EXPECTED_CASES and EXECUTED_CASES do not match the W4 page-specific matrix")
    if (not isinstance(supplemental_definition, dict)
            or supplemental_definition.get("status") != "SUPPLEMENTAL_CANDIDATE_NOT_IN_FROZEN_AUTHORITY"
            or supplemental_scale != 1.5
            or supplemental_definition.get("source_path") != matrix.get("source_path")
            or supplemental_definition.get("source_sha256") != matrix.get("source_sha256")
            or {(row.get("width"), row.get("height")) for row in supplemental_definition.get("viewports", [])
                if isinstance(row, dict)} != expected_viewports
            or len(supplemental_rows) != expected_supplemental_count
            or summary.get("supplemental_expected") != expected_supplemental_count
            or summary.get("supplemental_executed") != expected_supplemental_count
            or summary.get("supplemental_pass") != expected_supplemental_count
            or summary.get("supplemental_fail") != 0 or summary.get("supplemental_not_run") != 0
            or summary.get("total_expected") != expected_count + expected_supplemental_count
            or summary.get("total_executed") != expected_count + expected_supplemental_count
            or summary.get("total_pass") != expected_count + expected_supplemental_count
            or summary.get("total_fail") != 0 or summary.get("total_not_run") != 0
            or len(rows) != expected_count + expected_supplemental_count):
        errors.append("W5 1.5x supplemental cases do not exactly cover the W4 viewport set as separate candidate evidence")
    state_mapping = report.get("behavior_state_mapping") if isinstance(report.get("behavior_state_mapping"), dict) else {}
    expected_states = {row.get("state_id") for row in matrix.get("cases", []) if isinstance(row, dict)}
    mapped_state = state_mapping.get("behavior_state_id")
    if not mapped_state or expected_states != {mapped_state}:
        errors.append("W5 behavior state mapping does not resolve the state declared by W4 authority")
    expected_state_ref = next((row for row in matrix.get("state_authority_refs", [])
                               if isinstance(row, dict) and row.get("resolved_state_ids")), None)
    if (not isinstance(expected_state_ref, dict)
            or state_mapping.get("authority_ref_id") != expected_state_ref.get("authority_ref_id")
            or state_mapping.get("path") != expected_state_ref.get("path")
            or state_mapping.get("sha256") != expected_state_ref.get("sha256")
            or state_mapping.get("declared_locator") != expected_state_ref.get("declared_locator")
            or state_mapping.get("locator") != expected_state_ref.get("locator")
            or state_mapping.get("resolution") != expected_state_ref.get("resolution")):
        errors.append("W5 behavior state locator/source does not match the W4 state authority reference")
    negative_cases = report.get("negative_cases") if isinstance(report.get("negative_cases"), list) else []
    if len(negative_cases) != 8 or any(not isinstance(row, dict) or row.get("result") != "EXPECTED_FAIL_CLOSED" for row in negative_cases):
        errors.append("W5 negative cases are missing or do not all report expected fail-closed outcomes")
    if len(negative_cases) != 8:
        errors.append("W5 negative-case count is not the expected 8")

    copied_dir = evidence_dir / "empty-home-matrix"
    copied_dir.mkdir(parents=True, exist_ok=True)
    if source_path.is_file():
        shutil.copyfile(source_path, copied_dir / source_path.name)
    referenced_files: set[Path] = set()
    dom_report_value = report.get("dom_report")
    dom_report_relative = Path(dom_report_value) if isinstance(dom_report_value, str) and dom_report_value else None
    if dom_report_relative is not None:
        referenced_files.add(dom_report_relative)
    def collect_local_paths(value: Any) -> None:
        if isinstance(value, dict):
            for key, child in value.items():
                if key == "path" and isinstance(child, str):
                    referenced_files.add(Path(child))
                else:
                    collect_local_paths(child)
        elif isinstance(value, list):
            for child in value:
                collect_local_paths(child)
    collect_local_paths(report)
    for relative in referenced_files:
        if relative.is_absolute() or ".." in relative.parts:
            errors.append(f"W5 matrix report contains an unsafe evidence file reference: {relative}")
            continue
        item = source_path.parent / relative
        if not item.is_file() or item.is_symlink():
            # Paths in authority/resource references are not runner outputs; only
            # missing explicit output references are errors.
            if (dom_report_relative is not None and relative == dom_report_relative) or any(
                isinstance(row, dict) and isinstance(row.get("screenshot_rendered"), dict)
                and row["screenshot_rendered"].get("path") == relative.as_posix()
                for row in rows
            ):
                errors.append(f"W5 matrix report references a missing evidence file: {relative}")
            continue
        target = copied_dir / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(item, target)

    def copy_reported_screenshot(actual: dict[str, Any], case_id: str) -> dict[str, str] | None:
        screenshot = actual.get("screenshot_rendered") if isinstance(actual.get("screenshot_rendered"), dict) else {}
        screenshot_value = screenshot.get("path")
        if not isinstance(screenshot_value, str) or not screenshot_value or Path(screenshot_value).is_absolute() or ".." in Path(screenshot_value).parts:
            errors.append(f"W5 case {case_id} has an unsafe/missing rendered screenshot path")
            return None
        source_screenshot = source_path.parent / screenshot_value
        copied_screenshot = copied_dir / screenshot_value
        if (not source_screenshot.is_file() or source_screenshot.is_symlink()
                or sha256_file(source_screenshot) != screenshot.get("sha256")):
            errors.append(f"W5 case {case_id} rendered screenshot is missing or hash-mismatched")
            return None
        png = source_screenshot.read_bytes()
        if len(png) < 24 or png[:8] != b"\x89PNG\r\n\x1a\n":
            errors.append(f"W5 case {case_id} rendered screenshot is not a PNG")
            return None
        if int.from_bytes(png[16:20], "big") != screenshot.get("pixel_width") or int.from_bytes(png[20:24], "big") != screenshot.get("pixel_height"):
            errors.append(f"W5 case {case_id} rendered screenshot dimensions do not match its report")
            return None
        copied_screenshot.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source_screenshot, copied_screenshot)
        return {"path":(Path("empty-home-matrix") / screenshot_value).as_posix(),
                "sha256":sha256_file(copied_screenshot)}
    copied_report = copied_dir / source_path.name
    if copied_report.is_file():
        matrix["execution_report_path"] = copied_report.relative_to(evidence_dir).as_posix()
        matrix["execution_report_sha256"] = sha256_file(copied_report)

    row_by_key: dict[tuple[Any, Any, Any, Any], list[dict[str, Any]]] = {}
    for row in rows:
        if not isinstance(row, dict):
            continue
        viewport = row.get("viewport") if isinstance(row.get("viewport"), dict) else {}
        width = row.get("viewport_width", viewport.get("width"))
        height = row.get("viewport_height", viewport.get("height"))
        key = (width, height, row.get("font_scale"), row.get("state_id"))
        row_by_key.setdefault(key, []).append(row)

    for expected in matrix.get("cases", []):
        if not isinstance(expected, dict):
            continue
        viewport = expected.get("viewport") if isinstance(expected.get("viewport"), dict) else {}
        key = (viewport.get("width"), viewport.get("height"), expected.get("font_scale"), expected.get("state_id"))
        matches = row_by_key.get(key, [])
        actual = matches[0] if len(matches) == 1 else None
        if actual is None:
            errors.append(f"W5 matrix is missing or duplicates expected case {expected.get('case_id')}")
            continue
        font_label = format(key[2], "g") if isinstance(key[2], (int, float)) else str(key[2])
        if (actual.get("result") != "PASS" or actual.get("page_id") != page_id
                or actual.get("case_id") != f"EMPTY_HOME_{key[0]}x{key[1]}_FONT_{font_label}"
                or actual.get("source_sha") != source_sha or str(actual.get("run_id")) != str(run_id)
                or actual.get("matrix_source_path") != matrix.get("source_path")
                or actual.get("matrix_source_sha256") != matrix.get("source_sha256")
                or actual.get("state_id") != mapped_state
                or actual.get("renderer_state_id") != state_mapping.get("renderer_state_id")
                or not isinstance(actual.get("node_bounds"), list) or not actual.get("node_bounds")):
            errors.append(f"W5 case {expected.get('case_id')} does not bind exact source, run, authority, state, and actual DOM bounds")
            continue
        screenshot_evidence = copy_reported_screenshot(actual, str(expected.get("case_id")))
        if screenshot_evidence is None:
            continue
        screenshot_value = actual["screenshot_rendered"]["path"]
        case_payload = {
            "schema": "YUJIAN_W4_PAGE_MATRIX_CASE_EVIDENCE_V1",
            "page_id": page_id,
            "case_id": expected.get("case_id"),
            "source_sha": source_sha,
            "run_id": str(run_id),
            "matrix_source_path": matrix.get("source_path"),
            "matrix_source_sha256": matrix.get("source_sha256"),
            "matrix_source_authority_ref_id": matrix.get("source_authority_ref_id"),
            "behavior_state_id": mapped_state,
            "renderer_state_id": state_mapping.get("renderer_state_id"),
            "expected": expected,
            "execution": actual,
            "screenshot_path": screenshot_evidence["path"],
            "screenshot_sha256": screenshot_evidence["sha256"],
        }
        case_path = evidence_dir / "matrix-cases" / f"{expected.get('case_id')}.json"
        case_path.parent.mkdir(parents=True, exist_ok=True)
        case_path.write_text(json.dumps(case_payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        expected.update({
            "renderer_state_id": state_mapping.get("renderer_state_id"),
            "matrix_source_authority_ref_id": matrix.get("source_authority_ref_id"),
            "result": "PASS",
            "failure_reason": None,
            "evidence_path": case_path.relative_to(evidence_dir).as_posix(),
            "evidence_sha256": sha256_file(case_path),
            "screenshot_path": case_payload["screenshot_path"],
            "screenshot_sha256": case_payload["screenshot_sha256"],
            "source_sha": source_sha,
            "run_id": str(run_id),
        })

    supplemental_cases: list[dict[str, Any]] = []
    supplemental_by_key: dict[tuple[Any, Any, Any, Any], list[dict[str, Any]]] = {}
    for row in supplemental_rows:
        viewport = row.get("viewport") if isinstance(row.get("viewport"), dict) else {}
        key = (row.get("viewport_width", viewport.get("width")), row.get("viewport_height", viewport.get("height")),
               row.get("font_scale"), row.get("state_id"))
        supplemental_by_key.setdefault(key, []).append(row)
    for width, height in sorted(expected_viewports):
        key = (width, height, 1.5, mapped_state)
        matches = supplemental_by_key.get(key, [])
        actual = matches[0] if len(matches) == 1 else None
        case_id = f"EMPTY_HOME_{width}x{height}_FONT_1.5"
        if actual is None:
            errors.append(f"W5 is missing or duplicates required supplemental case {case_id}")
            supplemental_cases.append({"case_id":case_id,"viewport":{"width":width,"height":height},"font_scale":1.5,
                                      "state_id":mapped_state,"result":"NOT_RUN","failure_reason":"missing or duplicated W5 case"})
            continue
        if (actual.get("result") != "PASS" or actual.get("page_id") != page_id or actual.get("case_id") != case_id
                or actual.get("source_sha") != source_sha or str(actual.get("run_id")) != str(run_id)
                or actual.get("matrix_source_path") != matrix.get("source_path")
                or actual.get("matrix_source_sha256") != matrix.get("source_sha256")
                or actual.get("state_id") != mapped_state
                or actual.get("renderer_state_id") != state_mapping.get("renderer_state_id")
                or not isinstance(actual.get("node_bounds"), list) or not actual.get("node_bounds")):
            errors.append(f"W5 supplemental case {case_id} does not bind exact source, run, authority, state, and actual DOM bounds")
            result = "FAIL"
        else:
            result = "PASS"
        screenshot_evidence = copy_reported_screenshot(actual, case_id) if result == "PASS" else None
        if result == "PASS" and screenshot_evidence is None:
            result = "FAIL"
        supplemental_case = {
            "case_id":case_id,"viewport":{"width":width,"height":height},"font_scale":1.5,"state_id":mapped_state,
            "renderer_state_id":state_mapping.get("renderer_state_id"),"result":result,
            "failure_reason":None if result == "PASS" else "W5 supplemental evidence failed exact-source, geometry, or screenshot validation.",
            "source_sha":source_sha,"run_id":str(run_id),"matrix_source_path":matrix.get("source_path"),
            "matrix_source_sha256":matrix.get("source_sha256"),"matrix_source_authority_ref_id":matrix.get("source_authority_ref_id"),
            "evidence_path":None,"evidence_sha256":None,
            "screenshot_path":screenshot_evidence["path"] if screenshot_evidence else None,
            "screenshot_sha256":screenshot_evidence["sha256"] if screenshot_evidence else None,
        }
        if result == "PASS" and screenshot_evidence:
            payload = {"schema":"YUJIAN_W4_PAGE_MATRIX_CASE_EVIDENCE_V1","case_classification":"SUPPLEMENTAL_CANDIDATE_NOT_IN_FROZEN_AUTHORITY",
                       "page_id":page_id,"case_id":case_id,"source_sha":source_sha,"run_id":str(run_id),
                       "matrix_source_path":matrix.get("source_path"),"matrix_source_sha256":matrix.get("source_sha256"),
                       "matrix_source_authority_ref_id":matrix.get("source_authority_ref_id"),"behavior_state_id":mapped_state,
                       "renderer_state_id":state_mapping.get("renderer_state_id"),"execution":actual,
                       "screenshot_path":screenshot_evidence["path"],"screenshot_sha256":screenshot_evidence["sha256"]}
            evidence_path=evidence_dir/"matrix-cases"/"supplemental"/f"{case_id}.json"
            evidence_path.parent.mkdir(parents=True,exist_ok=True)
            evidence_path.write_text(json.dumps(payload,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
            supplemental_case["evidence_path"]=evidence_path.relative_to(evidence_dir).as_posix()
            supplemental_case["evidence_sha256"]=sha256_file(evidence_path)
        supplemental_cases.append(supplemental_case)
    supplemental_pass = len(supplemental_cases) == expected_supplemental_count and all(row.get("result") == "PASS" for row in supplemental_cases)
    matrix["supplemental"] = {
        "status":"PASS" if supplemental_pass else "FAIL","classification":"SUPPLEMENTAL_CANDIDATE_NOT_IN_FROZEN_AUTHORITY",
        "font_scale":1.5,"source_path":matrix.get("source_path"),"source_sha256":matrix.get("source_sha256"),
        "expected_case_count":expected_supplemental_count,"recorded_case_count":len(supplemental_cases),
        "cases":supplemental_cases,
        "reason":"All supplemental 1.5x candidates were independently executed and hash-bound; they remain outside the frozen formal matrix." if supplemental_pass
                 else "One or more supplemental 1.5x candidates were missing or failed validation."
    }

    if errors or not supplemental_pass:
        matrix["status"] = "FAIL"
        matrix["reason"] = "; ".join(errors)
    else:
        matrix["status"] = "PASS"
        matrix["reason"] = "All page-owned cases were executed by the same-Run-ID W5 Chromium runner; user visual review remains pending."
    return errors


def matrix_errors(matrix: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    cases = matrix.get("cases")
    if not isinstance(cases, list):
        return ["matrix.cases must be an array"]
    if matrix.get("recorded_case_count") != len(cases):
        errors.append("matrix recorded_case_count does not match cases length")
    if matrix.get("expected_case_count") != len(cases):
        errors.append("matrix has missing or extra cases relative to expected_case_count")
    ids = [row.get("case_id") for row in cases if isinstance(row, dict)]
    if len(ids) != len(cases) or len(ids) != len(set(ids)):
        errors.append("matrix case IDs must be present and unique")
    for row in cases:
        if not isinstance(row, dict) or row.get("result") not in {"NOT_RUN", "PASS", "FAIL", "BLOCKED"}:
            errors.append("matrix case has an invalid result")
    return errors


def _page_binding_path(root: Path, page_id: str) -> tuple[str, dict[str, Any]]:
    registry_path = safe_repo_path(root, "design/registry/experience_registry_v1.json")
    registry = read_json(registry_path)
    features = [row for row in registry.get("features", []) if isinstance(row, dict) and row.get("id") == page_id]
    if len(features) != 1:
        raise PipelineError(f"Experience Registry must contain exactly one page {page_id}; found {len(features)}")
    owner = features[0].get("owner_path")
    if not isinstance(owner, str):
        raise PipelineError(f"Experience Registry owner_path is missing for {page_id}")
    binding_rel = f"{owner}/Page_Resource_Binding_V1.json"
    binding = read_json(safe_repo_path(root, binding_rel))
    if binding.get("page_id") != page_id:
        raise PipelineError(f"resource binding page_id does not match Experience Registry for {page_id}")
    return binding_rel, binding


def publication_scan_status(root: Path, page_id: str, expected_sha: str, run_id: str | None, report_path: str | None) -> tuple[str, dict[str, Any], str | None]:
    if not report_path:
        return "NOT_RUN", {"status": "NOT_RUN", "source_sha": None, "run_id": None, "report_path": None,
                           "report_sha256": None, "file_count": None, "restricted_resource_count": None}, "Actual Pages bundle scan report was not supplied."
    try:
        path = Path(report_path)
        report = read_json(path if path.is_absolute() else safe_repo_path(root, report_path))
    except PipelineError as exc:
        return "FAIL", {"status": "FAIL", "source_sha": None, "run_id": None, "report_path": report_path,
                         "report_sha256": None, "file_count": None, "restricted_resource_count": None}, str(exc)
    row = next((item for item in report.get("pages", []) if isinstance(item, dict) and item.get("page_id") == page_id), None)
    publication = row.get("publication") if isinstance(row, dict) else None
    valid = (report.get("status") == "PASS" and report.get("source_sha") == expected_sha
             and run_id and str(report.get("workflow_run_id")) == str(run_id)
             and isinstance(row, dict) and row.get("status") == "PASS" and isinstance(publication, dict)
             and isinstance(publication.get("file_count"), int) and publication.get("file_count") > 0)
    if not valid:
        reason = "Pages scan must be PASS for this page and bind the same candidate SHA and CI Run ID."
        return "FAIL", {"status": "FAIL", "source_sha": report.get("source_sha"), "run_id": str(report.get("workflow_run_id")) if report.get("workflow_run_id") is not None else None,
                         "report_path": report_path, "report_sha256": sha256_file(path if path.is_absolute() else safe_repo_path(root, report_path)),
                         "file_count": publication.get("file_count") if isinstance(publication, dict) else None,
                         "restricted_resource_count": publication.get("restricted_bound_resource_count") if isinstance(publication, dict) else None}, reason
    return "PASS", {"status": "PASS", "source_sha": expected_sha, "run_id": str(run_id), "report_path": report_path,
                     "report_sha256": sha256_file(path if path.is_absolute() else safe_repo_path(root, report_path)),
                     "file_count": publication["file_count"], "restricted_resource_count": publication.get("restricted_bound_resource_count", 0)}, None


def _resolve_evidence_file(root: Path, value: str, evidence_dir: Path | None = None) -> Path:
    path = Path(value)
    if path.is_absolute():
        return path
    if evidence_dir is not None and (evidence_dir / path).is_file():
        resolved = (evidence_dir / path).resolve()
        try:
            resolved.relative_to(evidence_dir.resolve())
        except ValueError as exc:
            raise PipelineError(f"evidence path escapes its artifact directory: {value}") from exc
        return resolved
    return safe_repo_path(root, value)


def validate_manifest(root: Path, manifest: dict[str, Any], expected_source_sha: str, expected_run_id: str | None = None,
                      evidence_dir: Path | None = None) -> list[str]:
    errors: list[str] = []
    matrix = manifest.get("matrix", {})
    try:
        schema = read_json(safe_repo_path(root, EVIDENCE_SCHEMA))
        schema_errors = list(Draft202012Validator(schema, format_checker=FormatChecker()).iter_errors(manifest))
        for error in schema_errors:
            pointer = "/".join(str(part) for part in error.absolute_path) or "$"
            errors.append(f"evidence schema invalid at {pointer}: {error.message}")
    except PipelineError as exc:
        return [str(exc)]
    if manifest.get("candidate_sha") != expected_source_sha:
        errors.append("candidate_sha does not match the expected exact source SHA")
    try:
        envelope_path = safe_repo_path(root, manifest.get("contract_path", ""))
        profile_path = safe_repo_path(root, manifest.get("profile_path", ""))
        envelope = read_json(envelope_path)
        profile = read_json(profile_path)
        if sha256_file(envelope_path) != manifest.get("contract_sha256"):
            errors.append("contract_sha256 does not match the bound envelope bytes")
        if sha256_file(profile_path) != manifest.get("profile_sha256"):
            errors.append("profile_sha256 does not match the bound profile bytes")
        if envelope.get("page", {}).get("page_id") != manifest.get("page_id") or profile.get("page_id") != manifest.get("page_id"):
            errors.append("manifest page_id does not match its W1 envelope and profile")
        if envelope.get("contract_id") != manifest.get("contract_id") or str(envelope.get("contract_version")) != str(manifest.get("contract_version")):
            errors.append("manifest contract identity does not match its W1 envelope")
        if profile.get("profile_id") != manifest.get("profile_id"):
            errors.append("manifest profile_id does not match its W1 profile")
        expected_matrix = derive_matrix(root, envelope, profile)
        actual_case_ids = [row.get("case_id") for row in manifest.get("matrix", {}).get("cases", []) if isinstance(row, dict)]
        expected_case_ids = [row.get("case_id") for row in expected_matrix.get("cases", [])]
        if actual_case_ids != expected_case_ids:
            errors.append("manifest matrix cases do not match the page-owned expected matrix")
        if matrix.get("state_authority_refs") != expected_matrix.get("state_authority_refs"):
            errors.append("manifest state authority references do not match the declared W1 state scope")
    except (PipelineError, AttributeError, TypeError) as exc:
        errors.append(f"manifest authority binding cannot be resolved: {exc}")
    if manifest.get("result") == "PASS":
        run_id = manifest.get("ci_run_id")
        if not isinstance(run_id, str) or not run_id.isdigit():
            errors.append("PASS evidence requires a numeric CI Run ID")
        if expected_run_id is None or not str(expected_run_id).isdigit() or str(run_id) != str(expected_run_id):
            errors.append("PASS evidence Run ID is missing or does not match the observed workflow Run ID")
        artifact_url = manifest.get("artifact_url")
        if not isinstance(artifact_url, str) or not run_id or f"/actions/runs/{run_id}" not in artifact_url:
            errors.append("PASS evidence artifact_url must resolve to the same GitHub Actions Run ID")
        if manifest.get("photo_publication_provenance", {}).get("status") != "PASS":
            errors.append("PASS evidence requires an exact-SHA, same-Run-ID Pages bundle scan")
        checks = manifest.get("checks", {})
        if checks.get("contract") != "PASS" or checks.get("resources") != "PASS" or checks.get("pages_bundle") != "PASS":
            errors.append("PASS evidence requires contract, resource, and Pages-bundle checks to PASS")
        if checks.get("browser_geometry") != "PASS":
            errors.append("PASS evidence requires actual browser workspace geometry evidence")
        if matrix.get("expected_case_count", 0) > 0 and any(
            not isinstance(row, dict) or row.get("result") != "PASS"
            for row in matrix.get("cases", [])
        ):
            errors.append("PASS evidence cannot omit or leave any page-owned matrix case NOT_RUN")
    publication = manifest.get("photo_publication_provenance", {})
    if publication.get("status") == "PASS":
        if publication.get("source_sha") != expected_source_sha or str(publication.get("run_id")) != str(expected_run_id):
            errors.append("Pages bundle scan must bind the expected source SHA and observed workflow Run ID")
        publication_path = publication.get("report_path")
        try:
            if not isinstance(publication_path, str) or not publication_path:
                raise PipelineError("Pages scan report path is missing")
            path = _resolve_evidence_file(root, publication_path, evidence_dir)
            if not path.is_file() or sha256_file(path) != publication.get("report_sha256"):
                raise PipelineError("Pages scan report is missing or its SHA-256 does not match")
            publication_report = read_json(path)
            page_row = next((row for row in publication_report.get("pages", []) if isinstance(row, dict) and row.get("page_id") == manifest.get("page_id")), None)
            if (publication_report.get("status") != "PASS" or publication_report.get("source_sha") != expected_source_sha
                    or str(publication_report.get("workflow_run_id")) != str(expected_run_id)
                    or not isinstance(page_row, dict) or page_row.get("status") != "PASS"
                    or not isinstance(page_row.get("publication"), dict)
                    or page_row["publication"].get("file_count") != publication.get("file_count")
                    or page_row["publication"].get("restricted_bound_resource_count") != publication.get("restricted_resource_count")):
                raise PipelineError("Pages scan report contents do not support this page manifest")
        except (OSError, PipelineError, json.JSONDecodeError) as exc:
            errors.append(f"Pages scan evidence cannot be verified: {exc}")
    if manifest.get("page_acceptance") != "NOT_VERIFIED":
        errors.append("generic runner cannot promote page acceptance")
    errors.extend(matrix_errors(matrix))
    case_results = [row.get("result") for row in matrix.get("cases", []) if isinstance(row, dict)]
    if matrix.get("status") == "FAIL" or "FAIL" in case_results:
        if manifest.get("result") != "FAIL":
            errors.append("failed page-owned matrix cases must make the manifest result FAIL")
    if matrix.get("status") == "PASS" and (
        not case_results or len(case_results) != matrix.get("expected_case_count") or any(value != "PASS" for value in case_results)
    ):
        errors.append("matrix PASS requires every expected case to PASS")
    if "PASS" in case_results and matrix.get("status") != "PASS":
        errors.append("matrix status must be PASS when all per-case exact-SHA evidence passes")
    if matrix.get("status") == "PASS":
        report_value = matrix.get("execution_report_path")
        if (not isinstance(report_value, str) or not report_value
                or matrix.get("execution_source_sha") != expected_source_sha
                or str(matrix.get("execution_run_id")) != str(expected_run_id)):
            errors.append("matrix PASS requires an exact-source, same-Run-ID execution report")
        else:
            try:
                report_file = _resolve_evidence_file(root, report_value, evidence_dir)
                if not report_file.is_file() or sha256_file(report_file) != matrix.get("execution_report_sha256"):
                    raise PipelineError("execution report is missing or its SHA-256 does not match")
                execution_report = read_json(report_file)
                summary = execution_report.get("matrix_summary", {})
                report_authority = execution_report.get("matrix_authority", {})
                state_mapping = execution_report.get("behavior_state_mapping", {})
                state_authority = next((row for row in matrix.get("state_authority_refs", [])
                                        if isinstance(row, dict) and row.get("resolved_state_ids")), {})
                negative_cases = execution_report.get("negative_cases", [])
                expected_count = matrix.get("expected_case_count")
                if (execution_report.get("status") != "PASS_STRUCTURAL_MATRIX_VISUAL_REVIEW_PENDING"
                        or execution_report.get("page_id") != manifest.get("page_id")
                        or execution_report.get("source_sha") != expected_source_sha
                        or execution_report.get("candidate_source_sha") != expected_source_sha
                        or str(execution_report.get("github_run_id")) != str(expected_run_id)
                        or report_authority.get("id") != matrix.get("source_authority_ref_id")
                        or report_authority.get("path") != matrix.get("source_path")
                        or report_authority.get("sha256") != matrix.get("source_sha256")
                        or state_mapping.get("authority_ref_id") != state_authority.get("authority_ref_id")
                        or state_mapping.get("path") != state_authority.get("path")
                        or state_mapping.get("sha256") != state_authority.get("sha256")
                        or state_mapping.get("declared_locator") != state_authority.get("declared_locator")
                        or state_mapping.get("locator") != state_authority.get("locator")
                        or state_mapping.get("resolution") != state_authority.get("resolution")
                        or state_mapping.get("behavior_state_id") not in state_authority.get("resolved_state_ids", [])
                        or len(negative_cases) != 8
                        or any(not isinstance(row, dict) or row.get("result") != "EXPECTED_FAIL_CLOSED" for row in negative_cases)
                        or summary.get("expected") != expected_count
                        or summary.get("executed") != expected_count
                        or summary.get("pass") != expected_count
                        or summary.get("fail") != 0 or summary.get("not_run") != 0):
                    raise PipelineError("execution report contents do not support matrix PASS")
                if (execution_report.get("visual_review_status") != "USER_VISUAL_ACCEPTANCE_PENDING"
                        or execution_report.get("user_acceptance_status") != "USER_VISUAL_ACCEPTANCE_PENDING"
                        or execution_report.get("native_runtime_status") != "NOT_VERIFIED"):
                    raise PipelineError("matrix report attempted to promote visual acceptance or Native runtime")
            except (OSError, PipelineError, json.JSONDecodeError) as exc:
                errors.append(f"matrix execution report cannot be verified: {exc}")
    checks = manifest.get("checks", {})
    browser = checks.get("browser_evidence", {})
    if checks.get("browser_geometry") != browser.get("status"):
        errors.append("browser_geometry result must match the bound browser_evidence status")
    if browser.get("status") == "PASS":
        if browser.get("source_sha") != expected_source_sha or str(browser.get("run_id")) != str(expected_run_id):
            errors.append("browser evidence must bind the exact source SHA and observed Run ID")
        for key, digest_key in (("report_path", "report_sha256"), ("screenshot_path", "screenshot_sha256"), ("dom_path", "dom_sha256")):
            value = browser.get(key)
            if not isinstance(value, str) or not value:
                errors.append(f"browser PASS requires {key}")
                continue
            try:
                resolved = _resolve_evidence_file(root, value, evidence_dir)
                if not resolved.is_file() or sha256_file(resolved) != browser.get(digest_key):
                    errors.append(f"browser PASS {key} is missing or its SHA-256 does not match")
            except (OSError, PipelineError):
                errors.append(f"browser PASS {key} is missing or unsafe")
        report_path = browser.get("report_path")
        if isinstance(report_path, str) and report_path:
            try:
                browser_report = read_json(_resolve_evidence_file(root, report_path, evidence_dir))
                if browser_report.get("result") != "PASS" or browser_report.get("page_id") != manifest.get("page_id") or browser_report.get("source_sha") != expected_source_sha:
                    errors.append("browser report identity/result does not support browser PASS")
                if browser_report.get("visual_result") != "MEASUREMENT_ONLY_NOT_VISUAL_PASS":
                    errors.append("browser report must remain measurement-only")
            except PipelineError as exc:
                errors.append(f"browser report cannot be verified: {exc}")
    elif browser.get("status") == "NOT_RUN" and browser.get("report_path"):
        try:
            report_path = browser.get("report_path")
            report_file = _resolve_evidence_file(root, report_path, evidence_dir)
            if not report_file.is_file() or sha256_file(report_file) != browser.get("report_sha256"):
                raise PipelineError("NOT_RUN browser report is missing or its SHA-256 does not match")
            not_run_report = read_json(report_file)
            workspace = not_run_report.get("workspace", {})
            if (not_run_report.get("result") != "NOT_RUN"
                    or not_run_report.get("reason") != "REFERENCE_IMAGE_ONLY_NOT_CONNECTED"
                    or workspace.get("workspaceKind") != "reference-image-only"
                    or not_run_report.get("page_id") != manifest.get("page_id")
                    or not_run_report.get("source_sha") != expected_source_sha):
                errors.append("browser NOT_RUN is allowed only for an exact-SHA reference-image-only, unconnected workspace")
            if str(not_run_report.get("run_id")) != str(expected_run_id):
                errors.append("browser NOT_RUN report Run ID does not match the observed workflow Run ID")
        except (OSError, PipelineError, json.JSONDecodeError) as exc:
            errors.append(f"browser NOT_RUN report cannot be verified: {exc}")
    for case in matrix.get("cases", []) if isinstance(matrix.get("cases"), list) else []:
        if isinstance(case, dict) and case.get("result") == "PASS":
            path_text = case.get("evidence_path")
            if not isinstance(path_text, str) or not path_text or case.get("source_sha") != expected_source_sha or str(case.get("run_id")) != str(expected_run_id):
                errors.append(f"matrix case {case.get('case_id')} PASS lacks exact-SHA evidence and Run ID")
            else:
                try:
                    evidence_path = _resolve_evidence_file(root, path_text, evidence_dir)
                    if not evidence_path.is_file() or sha256_file(evidence_path) != case.get("evidence_sha256"):
                        errors.append(f"matrix case {case.get('case_id')} evidence is missing or has the wrong hash")
                    else:
                        case_data = read_json(evidence_path)
                        if (case_data.get("source_sha") != expected_source_sha
                                or str(case_data.get("run_id")) != str(expected_run_id)
                                or case_data.get("case_id") != case.get("case_id")
                                or case_data.get("matrix_source_path") != matrix.get("source_path")
                                or case_data.get("matrix_source_sha256") != matrix.get("source_sha256")
                                or case_data.get("matrix_source_authority_ref_id") != matrix.get("source_authority_ref_id")
                                or case_data.get("matrix_source_authority_ref_id") != matrix.get("source_authority_ref_id")
                                or case_data.get("behavior_state_id") != case.get("state_id")
                                or case_data.get("renderer_state_id") != case.get("renderer_state_id")):
                            errors.append(f"matrix case {case.get('case_id')} content does not match its manifest identity/authority")
                        screenshot_path = case_data.get("screenshot_path")
                        if not isinstance(screenshot_path, str) or not screenshot_path:
                            errors.append(f"matrix case {case.get('case_id')} is missing its rendered screenshot")
                        else:
                            screenshot_file = _resolve_evidence_file(root, screenshot_path, evidence_dir)
                            if not screenshot_file.is_file() or sha256_file(screenshot_file) != case_data.get("screenshot_sha256"):
                                errors.append(f"matrix case {case.get('case_id')} screenshot is missing or has the wrong hash")
                    screenshot_path = case.get("screenshot_path")
                    if isinstance(screenshot_path, str) and screenshot_path:
                        screenshot_file = _resolve_evidence_file(root, screenshot_path, evidence_dir)
                        if not screenshot_file.is_file() or sha256_file(screenshot_file) != case.get("screenshot_sha256"):
                            errors.append(f"matrix case {case.get('case_id')} screenshot evidence is missing or has the wrong hash")
                except (OSError, PipelineError):
                    errors.append(f"matrix case {case.get('case_id')} evidence path is unsafe")
    return errors


def validate_browser_report(root: Path, page_id: str, source_sha: str, run_id: str | None,
                            report_path: str | None, evidence_dir: Path) -> tuple[str, dict[str, Any], str | None]:
    empty = {"status": "NOT_RUN", "report_path": None, "report_sha256": None,
             "screenshot_path": None, "screenshot_sha256": None, "dom_path": None, "dom_sha256": None,
             "candidate_url": None, "source_sha": None, "run_id": None,
             "visual_result": "MEASUREMENT_ONLY_NOT_VISUAL_PASS"}
    if not report_path:
        return "NOT_RUN", empty, None
    try:
        source_report_path = Path(report_path)
        source_report_path = source_report_path if source_report_path.is_absolute() else safe_repo_path(root, report_path)
        browser_report = read_json(source_report_path)
    except (OSError, PipelineError) as exc:
        return "FAIL", {**empty, "status": "FAIL"}, f"browser report cannot be read: {exc}"
    if browser_report.get("page_id") != page_id or browser_report.get("source_sha") != source_sha:
        return "FAIL", {**empty, "status": "FAIL"}, "browser report page_id or source SHA does not match this invocation"
    if browser_report.get("visual_result") != "MEASUREMENT_ONLY_NOT_VISUAL_PASS" or browser_report.get("page_acceptance") != "NOT_VERIFIED":
        return "FAIL", {**empty, "status": "FAIL"}, "browser report attempted to promote visual/page acceptance"
    if browser_report.get("result") in {"BLOCKED", "BLOCKED_INFRA"}:
        return (
            "BLOCKED",
            {**empty, "status": "BLOCKED", "source_sha": source_sha,
             "candidate_url": browser_report.get("candidate_url"),
             "run_id": str(browser_report.get("run_id")) if browser_report.get("run_id") is not None else None},
            "browser workspace verification is BLOCKED_INFRA",
        )
    if browser_report.get("result") == "NOT_RUN":
        workspace = browser_report.get("workspace", {})
        if (browser_report.get("reason") != "REFERENCE_IMAGE_ONLY_NOT_CONNECTED"
                or workspace.get("workspaceKind") != "reference-image-only"):
            return "FAIL", {**empty, "status": "FAIL"}, "NOT_RUN is reserved for a declared reference-image-only, unconnected page"
        if str(browser_report.get("run_id")) != str(run_id):
            return "FAIL", {**empty, "status": "FAIL"}, "browser NOT_RUN report Run ID does not match the current CI Run ID"
        target_report = evidence_dir / "workspace-browser-report.json"
        shutil.copyfile(source_report_path, target_report)
        return "NOT_RUN", {
            **empty, "status": "NOT_RUN", "report_path": target_report.name,
            "report_sha256": sha256_file(target_report),
            "candidate_url": browser_report.get("candidate_url"), "source_sha": source_sha,
            "run_id": str(run_id) if run_id is not None else None
        }, "Structured page renderer is not connected; browser page evidence remains NOT_RUN."
    if browser_report.get("result") != "PASS":
        return (
            "FAIL",
            {**empty, "status": "FAIL", "source_sha": source_sha,
             "candidate_url": browser_report.get("candidate_url"),
             "run_id": str(browser_report.get("run_id")) if browser_report.get("run_id") is not None else None},
            "browser workspace verification did not PASS",
        )
    if not run_id:
        return (
            "NOT_RUN",
            {**empty, "source_sha": source_sha, "candidate_url": browser_report.get("candidate_url"),
             "report_path": str(source_report_path), "report_sha256": sha256_file(source_report_path)},
            "Local browser report has no CI Run ID; it is diagnostic only.",
        )
    if str(browser_report.get("run_id")) != str(run_id):
        return "FAIL", {**empty, "status": "FAIL"}, "browser report Run ID does not match the current CI Run ID"
    artifacts = {}
    for key, digest_key, target_name in (("screenshot_path", "screenshot_sha256", "workspace.png"),
                                         ("dom_path", "dom_sha256", "workspace-dom.json")):
        value = browser_report.get(key)
        if not isinstance(value, str) or not value:
            return "FAIL", {**empty, "status": "FAIL"}, f"browser report is missing {key}"
        artifact_path = Path(value)
        if not artifact_path.is_absolute():
            artifact_path = source_report_path.parent / artifact_path
        if not artifact_path.is_file() or sha256_file(artifact_path) != browser_report.get(digest_key):
            return "FAIL", {**empty, "status": "FAIL"}, f"browser report {key} is missing or SHA-256 mismatched"
        target = evidence_dir / target_name
        shutil.copyfile(artifact_path, target)
        artifacts[key] = target.name
        artifacts[digest_key] = sha256_file(target)
    target_report = evidence_dir / "workspace-browser-report.json"
    shutil.copyfile(source_report_path, target_report)
    return "PASS", {"status": "PASS", "report_path": target_report.name,
                    "report_sha256": sha256_file(target_report),
                    "screenshot_path": artifacts["screenshot_path"], "screenshot_sha256": artifacts["screenshot_sha256"],
                    "dom_path": artifacts["dom_path"], "dom_sha256": artifacts["dom_sha256"],
                    "candidate_url": browser_report.get("candidate_url"), "source_sha": source_sha,
                    "run_id": str(run_id), "visual_result": "MEASUREMENT_ONLY_NOT_VISUAL_PASS"}, None


def run_validation(root: Path, page_id: str, source_sha: str, contract: str | None, profile: str | None,
                   output_dir: Path, publication_report: str | None, browser_report_path: str | None,
                   require_evidence: bool, matrix_report_path: str | None = None) -> dict[str, Any]:
    head = current_sha(root)
    if not SHA1.fullmatch(source_sha) or source_sha != head:
        raise PipelineError(f"--source-sha must match current exact HEAD {head}; received {source_sha!r}")
    require_clean_source_tree(root)
    refs = resolve_page_case(root, page_id, contract, profile)
    envelope = read_json(safe_repo_path(root, refs["contract"]))
    profile_data = read_json(safe_repo_path(root, refs["profile"]))
    _, binding_data = _page_binding_path(root, page_id)

    output_dir.mkdir(parents=True, exist_ok=True)
    output_dir = output_dir.resolve()
    with tempfile.TemporaryDirectory(prefix=".page-freeze-w4-", dir=root) as temp_name:
        temp_root = Path(temp_name)
        matrix_rel = (temp_root / "single-page-matrix.json").relative_to(root).as_posix()
        (root / matrix_rel).write_text(json.dumps({"schema_version": "1.0", "matrix_id": f"W4_{page_id}", "cases": [
            {"case_id": refs["case_id"], "page_id": page_id, "envelope": refs["contract"], "profile": refs["profile"]}
        ]}, indent=2) + "\n", encoding="utf-8")
        w1_report_path = output_dir / "w1-contract-report.json"
        w1_code, w1_report, _, w1_stderr = run_json_command([
            sys.executable, str(root / W1_VALIDATOR), "--root", str(root), "--matrix", matrix_rel,
            "--expected-source-sha", source_sha, "--report", str(w1_report_path)
        ], root, "W1 contract validator")
        w2_report_path = output_dir / "w2-resource-report.json"
        w2_code, w2_report, _, w2_stderr = run_json_command([
            sys.executable, str(root / W2_VALIDATOR), "--root", str(root), "--all-pages",
            "--required-page-id", page_id, "--source-sha", source_sha, "--report", str(w2_report_path)
        ], root, "W2 resource validator")

    w1_ok = w1_code == 0 and w1_report.get("status") == "PASS" and all(
        row.get("expected_page_id") == page_id and row.get("actual_page_id") == page_id and row.get("status") == "PASS"
        for row in w1_report.get("cases", [])
    ) and len(w1_report.get("cases", [])) == 1
    w2_page = next((row for row in w2_report.get("pages", []) if isinstance(row, dict) and row.get("page_id") == page_id), None)
    w2_ok = w2_code == 0 and w2_report.get("status") == "PASS" and isinstance(w2_page, dict) and w2_page.get("status") == "PASS"
    pages_status, photo_provenance, publication_error = publication_scan_status(root, page_id, source_sha, os.environ.get("GITHUB_RUN_ID"), publication_report)
    if pages_status == "PASS" and publication_report:
        source_report_path = Path(publication_report)
        if not source_report_path.is_absolute():
            source_report_path = safe_repo_path(root, publication_report)
        bundled_report_path = output_dir / "pages-resource-binding-report.json"
        shutil.copyfile(source_report_path, bundled_report_path)
        photo_provenance["report_path"] = bundled_report_path.name
    browser_status, browser_evidence, browser_error = validate_browser_report(
        root, page_id, source_sha, os.environ.get("GITHUB_RUN_ID"), browser_report_path, output_dir
    )
    matrix = derive_matrix(root, envelope, profile_data)
    run_id = os.environ.get("GITHUB_RUN_ID")
    matrix_report_errors = import_structured_matrix_report(
        root, page_id, source_sha, run_id, matrix_report_path, matrix, output_dir
    ) if matrix_report_path else []
    matrix_validation_errors = matrix_errors(matrix)
    if matrix_validation_errors or matrix_report_errors:
        matrix["status"] = "FAIL"
        matrix["reason"] = "; ".join([*matrix_validation_errors, *matrix_report_errors])

    if not isinstance(run_id, str) or not run_id.isdigit():
        run_id = None
    repository = os.environ.get("GITHUB_REPOSITORY", "pan277942135/Yujian_App")
    server = os.environ.get("GITHUB_SERVER_URL", "https://github.com").rstrip("/")
    artifact_url = f"{server}/{repository}/actions/runs/{run_id}" if run_id else None
    structural_ok = w1_ok and w2_ok and not matrix_validation_errors and matrix.get("status") != "FAIL"
    matrix_case_results = [row.get("result") for row in matrix.get("cases", []) if isinstance(row, dict)]
    matrix_complete = matrix.get("expected_case_count", 0) == 0 or (
        len(matrix_case_results) == matrix.get("expected_case_count") and all(value == "PASS" for value in matrix_case_results)
    )
    evidence_failed = browser_status == "FAIL" or pages_status == "FAIL" or matrix.get("status") == "FAIL" or "FAIL" in matrix_case_results
    if evidence_failed or not structural_ok:
        result = "FAIL"
    elif (pages_status == "PASS" and run_id and browser_status == "PASS" and matrix_complete
          and all(value == "PASS" for value in matrix_case_results)):
        result = "PASS"
    else:
        result = "BLOCKED" if browser_status == "BLOCKED" or matrix.get("status") == "BLOCKED" or "BLOCKED" in matrix_case_results else "NOT_RUN"
    validation_ok = structural_ok and not evidence_failed
    failure_parts = []
    if not w1_ok:
        failure_parts.append("W1 contract validation failed: " + "; ".join(str(x.get("message")) if isinstance(x, dict) else str(x) for x in w1_report.get("errors", [])))
        if w1_stderr.strip(): failure_parts.append(w1_stderr.strip()[-1000:])
    if not w2_ok:
        failure_parts.append("W2 resource validation failed: " + "; ".join(str(x.get("message")) if isinstance(x, dict) else str(x) for x in w2_report.get("errors", [])))
        if w2_stderr.strip(): failure_parts.append(w2_stderr.strip()[-1000:])
    if publication_error:
        failure_parts.append(publication_error)
    if browser_error:
        failure_parts.append(browser_error)
    elif browser_status == "NOT_RUN":
        failure_parts.append("Actual staged-page browser workspace verification is NOT_RUN.")
    if not matrix_complete:
        missing_matrix = sum(value != "PASS" for value in matrix_case_results)
        failure_parts.append(f"{missing_matrix} of {matrix.get('expected_case_count', 0)} page-owned matrix cases remain NOT_RUN or unpassed.")
    if not run_id and validation_ok:
        failure_parts.append("No CI Run ID is available; local validation is recorded as NOT_RUN evidence.")
    manifest = {
        "schema_version": "1.0", "page_id": page_id,
        "contract_id": str(envelope.get("contract_id", "")), "contract_version": str(envelope.get("contract_version", "")),
        "contract_path": refs["contract"], "contract_sha256": sha256_file(safe_repo_path(root, refs["contract"])),
        "profile_id": str(profile_data.get("profile_id", "")), "profile_path": refs["profile"],
        "profile_sha256": sha256_file(safe_repo_path(root, refs["profile"])),
        "state_id": None, "candidate_sha": source_sha,
        "build_sha": os.environ.get("SITE_BUILD_SHA") if isinstance(os.environ.get("SITE_BUILD_SHA"), str) and SHA1.fullmatch(os.environ["SITE_BUILD_SHA"]) else None,
        "deploy_sha": None, "viewport": None, "fontScale": None,
        "command": ["python", W1_VALIDATOR, "--page-id", page_id, "--source-sha", source_sha],
        "ci_run_id": run_id, "artifact_url": artifact_url, "gate_id": W4_GATE, "result": result,
        "failure_reason": "; ".join(failure_parts) if failure_parts else None,
        "photo_publication_provenance": photo_provenance,
        "checks": {"contract": "PASS" if w1_ok else "FAIL", "resources": "PASS" if w2_ok else "FAIL",
                    "pages_bundle": pages_status, "browser_geometry": browser_status,
                    "browser_evidence": browser_evidence,
                    "native_runtime": "NOT_VERIFIED"},
        "matrix": matrix, "page_acceptance": "NOT_VERIFIED", "generated_at": datetime.now(timezone.utc).isoformat()
    }
    evidence_errors = validate_manifest(root, manifest, source_sha, run_id, output_dir)
    if evidence_errors:
        manifest["result"] = "FAIL"
        manifest["failure_reason"] = "; ".join(evidence_errors)
    elif result == "NOT_RUN":
        manifest["failure_reason"] = "; ".join(failure_parts) if failure_parts else "Evidence is NOT_RUN until a same-Run-ID Pages bundle scan is attached."
    validation_ok = validation_ok and not evidence_errors

    (output_dir / "evidence-manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    report = {"status": manifest["result"], "structural_status": "PASS" if structural_ok else "FAIL",
              "page_id": page_id, "source_sha": source_sha,
              "w1_status": w1_report.get("status"), "w1_report": str(w1_report_path),
              "w2_status": w2_report.get("status"), "w2_report": str(w2_report_path),
              "pages_bundle_status": pages_status, "evidence_status": manifest["result"],
              "evidence_manifest": str(output_dir / "evidence-manifest.json"), "matrix": matrix,
              "page_acceptance": "NOT_VERIFIED", "errors": evidence_errors if manifest["result"] == "FAIL" else []}
    (output_dir / "page-validation-report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if require_evidence and (manifest["result"] != "PASS" or evidence_errors):
        raise PipelineError("W4 evidence gate is not PASS: " + (manifest.get("failure_reason") or "; ".join(evidence_errors)))
    if not validation_ok:
        raise PipelineError("page validation failed; inspect " + str(output_dir / "page-validation-report.json"))
    return report


def verify_workspace(args: argparse.Namespace) -> int:
    head = current_sha(args.root)
    if args.source_sha != head:
        raise PipelineError(f"--source-sha must match current exact HEAD {head}")
    require_clean_source_tree(args.root)
    if not args.candidate_url:
        raise PipelineError("--candidate-url is required for verify-page-workspace")
    script = args.root / "scripts/design/verify_page_workspace.mjs"
    if not script.is_file():
        raise PipelineError("generic browser workspace verifier is missing")
    command = ["node", str(script), "--page-id", args.page_id, "--candidate-url", args.candidate_url,
               "--source-sha", args.source_sha, "--output-dir", str(args.output_dir.resolve()),
               "--unconnected-policy", args.unconnected_policy]
    try:
        result = subprocess.run(command, cwd=args.root)
    except OSError as exc:
        raise PipelineError(f"cannot launch the generic browser verifier: {exc}") from exc
    return result.returncode


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    validate = sub.add_parser("validate-page", help="run W1/W2 validation and emit one page evidence manifest")
    validate.add_argument("--root", type=Path, default=ROOT)
    validate.add_argument("--page-id", required=True)
    validate.add_argument("--contract", help="optional page-owned W1 envelope path")
    validate.add_argument("--profile", help="optional page-owned W1 profile path")
    validate.add_argument("--source-sha", required=True)
    validate.add_argument("--publication-report", help="W2 report from the actual staged Pages bundle scan")
    validate.add_argument("--browser-report", help="report produced by verify-page-workspace")
    validate.add_argument("--matrix-report", help="exact-SHA same-Run-ID page matrix report with DOM/screenshot evidence")
    validate.add_argument("--output-dir", type=Path, default=Path("/tmp/page-freeze-w4-evidence"))
    validate.add_argument("--evidence-name", help="optional output directory label; must be a safe filename stem")
    validate.add_argument("--require-evidence", action="store_true", help="fail unless same-Run-ID Pages evidence makes the manifest PASS")
    workspace = sub.add_parser("verify-page-workspace", help="run Playwright workspace route/DOM inspection using W3 adapter markers")
    workspace.add_argument("--root", type=Path, default=ROOT)
    workspace.add_argument("--page-id", required=True)
    workspace.add_argument("--candidate-url", required=True)
    workspace.add_argument("--source-sha", required=True)
    workspace.add_argument("--output-dir", type=Path, default=Path("/tmp/page-freeze-w4-workspace"))
    workspace.add_argument("--unconnected-policy", choices=("fail", "not-run"), default="fail",
                           help="not-run is only for the core-tools job; page acceptance remains fail-closed")
    manifest = sub.add_parser("validate-manifest", help="fail-closed exact-SHA and Run-ID validation")
    manifest.add_argument("--root", type=Path, default=ROOT)
    manifest.add_argument("--manifest", type=Path, required=True)
    manifest.add_argument("--expected-source-sha", required=True)
    manifest.add_argument("--expected-run-id", required=True)
    manifest.add_argument("--require-pass", action="store_true", help="also require the evidence result itself to be PASS")
    args = parser.parse_args()
    args.root = args.root.resolve()
    try:
        if args.command == "validate-page":
            evidence_name = args.evidence_name or args.page_id
            if not re.fullmatch(r"[A-Za-z0-9_-]+", evidence_name):
                raise PipelineError("--evidence-name must contain only letters, digits, underscore, or hyphen")
            page_output = args.output_dir / f"{evidence_name}_Evidence"
            report = run_validation(args.root, args.page_id, args.source_sha, args.contract, args.profile,
                                    page_output, args.publication_report, args.browser_report, args.require_evidence,
                                    args.matrix_report)
            print(json.dumps(report, ensure_ascii=False, indent=2))
            return 0
        if args.command == "verify-page-workspace":
            return verify_workspace(args)
        if args.command == "validate-manifest":
            manifest_path = args.manifest if args.manifest.is_absolute() else args.root / args.manifest
            data = read_json(manifest_path)
            errors = validate_manifest(args.root, data, args.expected_source_sha, args.expected_run_id, manifest_path.parent)
            if errors:
                for error in errors: print(f"ERROR: {error}", file=sys.stderr)
                print("PAGE_EVIDENCE_MANIFEST_VALIDATION=FAIL", file=sys.stderr)
                return 1
            if args.require_pass and data.get("result") != "PASS":
                print("ERROR: manifest result is not PASS", file=sys.stderr)
                print("PAGE_EVIDENCE_MANIFEST_VALIDATION=FAIL", file=sys.stderr)
                return 1
            print(f"PAGE_EVIDENCE_MANIFEST_VALIDATION=VALID result={data.get('result')}")
            return 0
    except PipelineError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        print("PAGE_FREEZE_PIPELINE=FAIL", file=sys.stderr)
        return 1
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
