#!/usr/bin/env python3
"""Fail-closed validation for page-freeze stages and exact-SHA evidence.

This checks checklist/evidence completeness. It does not prove page geometry,
asset bytes, visual fidelity, browser behavior, or native runtime behavior.
Those remain the responsibility of the named page and workflow validators.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any

RESULTS = {"NOT_RUN", "RUNNING", "PASS", "FAIL", "BLOCKED", "NOT_VERIFIED", "NOT_APPLICABLE"}
EXPECTED_GATES = [
    "G0_ADMISSION_AND_SCOPE",
    "G1_AUTHORITY_AND_PROVENANCE",
    "G2_CONTRACT_SCHEMA_AND_CONSTRAINTS",
    "G3_EXISTING_PREVIEW_INTEGRATION",
    "G4_BROWSER_GEOMETRY_MATRIX",
    "G5_CANDIDATE_DEPLOY_AND_HUMAN_ACCEPTANCE",
    "G6_CANONICAL_FREEZE",
    "G7_NATIVE_IMPLEMENTATION",
    "G8_NATIVE_RUNTIME_ACCEPTANCE",
]
GATE_DEPENDENCIES = {
    EXPECTED_GATES[0]: [],
    EXPECTED_GATES[1]: [EXPECTED_GATES[0]],
    EXPECTED_GATES[2]: [EXPECTED_GATES[1]],
    EXPECTED_GATES[3]: [EXPECTED_GATES[2]],
    EXPECTED_GATES[4]: [EXPECTED_GATES[3]],
    EXPECTED_GATES[5]: [EXPECTED_GATES[4]],
    EXPECTED_GATES[6]: [EXPECTED_GATES[5]],
    EXPECTED_GATES[7]: [EXPECTED_GATES[6]],
    EXPECTED_GATES[8]: [EXPECTED_GATES[7]],
}
SHA40 = re.compile(r"^[0-9a-f]{40}$")
SHA64 = re.compile(r"^[0-9a-f]{64}$")


def _is_text(value: Any) -> bool:
    return isinstance(value, str) and bool(value.strip())


def _valid_sha(value: Any, pattern: re.Pattern[str]) -> bool:
    return isinstance(value, str) and pattern.fullmatch(value) is not None


def _evidence_errors(scope: str, records: Any, expected_sha: Any, require_pass: bool) -> list[str]:
    errors: list[str] = []
    if not isinstance(records, list):
        return [f"{scope} must be an array"]
    if require_pass and not records:
        errors.append(f"{scope} requires at least one evidence record")
    matching_pass = False
    for index, record in enumerate(records):
        prefix = f"{scope}[{index}]"
        if not isinstance(record, dict):
            errors.append(f"{prefix} must be an object")
            continue
        source_sha = record.get("source_sha")
        if not _valid_sha(source_sha, SHA40):
            errors.append(f"{prefix}.source_sha must be a full 40-character commit SHA")
        result = record.get("result", "PASS")
        if result not in RESULTS:
            errors.append(f"{prefix}.result is not a valid lifecycle result")
        if not (_is_text(record.get("run_id")) or _is_text(record.get("workflow_or_command"))):
            errors.append(f"{prefix} needs run_id or workflow_or_command")
        artifact_urls = record.get("artifact_urls", [])
        if not isinstance(artifact_urls, list) or any(not _is_text(url) for url in artifact_urls):
            errors.append(f"{prefix}.artifact_urls must be an array of non-empty URLs")
            artifact_urls = []
        if not (_is_text(record.get("report_url")) or artifact_urls):
            errors.append(f"{prefix} needs report_url or at least one artifact_url")
        if result == "PASS" and _valid_sha(source_sha, SHA40) and source_sha == expected_sha:
            matching_pass = True
    if require_pass:
        if not _valid_sha(expected_sha, SHA40):
            errors.append(f"{scope} cannot PASS without an exact target source SHA")
        elif not matching_pass:
            errors.append(f"{scope} needs a PASS evidence record bound to the exact target source SHA")
    return errors


def validate_checklist(data: Any, mode: str = "template") -> list[str]:
    errors: list[str] = []
    if mode not in {"template", "freeze", "runtime"}:
        return [f"unsupported mode: {mode}"]
    if not isinstance(data, dict):
        return ["checklist root must be an object"]

    required_top = (
        "template_id", "template_version", "status", "status_semantics",
        "execution_order", "page", "authority_audit", "gates", "test_matrix",
        "evidence_record_template", "freeze_rules", "product_owner_acceptance",
        "canonical_freeze", "native_runtime",
    )
    for key in required_top:
        if key not in data:
            errors.append(f"missing required top-level key: {key}")
    if data.get("template_id") != "RESPONSIVE_PAGE_FREEZE_CHECKLIST_V1":
        errors.append("template_id mismatch")
    if data.get("status") != "TEMPLATE_NOT_A_PAGE_CONTRACT":
        errors.append("status must remain TEMPLATE_NOT_A_PAGE_CONTRACT")
    semantics = data.get("status_semantics")
    if not isinstance(semantics, dict):
        errors.append("status_semantics must be an object")
        semantics = {}
    if "G6" not in str(semantics.get("rule", "")) or "G7/G8" not in str(semantics.get("rule", "")):
        errors.append("status_semantics must distinguish canonical freeze from native G7/G8")
    lifecycle_values = data.get("lifecycle_status_values")
    if not isinstance(lifecycle_values, list) or set(lifecycle_values) != RESULTS:
        errors.append("lifecycle_status_values must enumerate the complete gate result vocabulary")
    elif semantics.get("gate_result_values") != lifecycle_values:
        errors.append("status_semantics.gate_result_values must match lifecycle_status_values")
    expected_execution_order = [
        {"id": gate_id, "depends_on": GATE_DEPENDENCIES[gate_id]}
        for gate_id in EXPECTED_GATES
    ]
    if data.get("execution_order") != expected_execution_order:
        errors.append("execution_order must match the canonical G0-G8 dependency chain")

    page = data.get("page")
    if not isinstance(page, dict):
        errors.append("page must be an object")
        page = {}
    page_fields = (
        "page_id", "route", "implementation_entrypoints", "included_states",
        "excluded_states", "contract_path", "contract_version", "exact_source_sha",
    )
    for key in page_fields:
        if key not in page:
            errors.append(f"page.{key} is required")
    for key in ("page_id", "route", "contract_path", "contract_version"):
        value = page.get(key)
        if value is not None and not isinstance(value, str):
            errors.append(f"page.{key} must be a string")
    for key in ("implementation_entrypoints", "included_states", "excluded_states"):
        if not isinstance(page.get(key), list):
            errors.append(f"page.{key} must be an array")
    page_sha = page.get("exact_source_sha")
    if page_sha is not None and not _valid_sha(page_sha, SHA40):
        errors.append("page.exact_source_sha must be null or a full 40-character SHA")

    authority = data.get("authority_audit")
    if not isinstance(authority, dict):
        errors.append("authority_audit must be an object")
        authority = {}
    if authority.get("result", "NOT_RUN") not in RESULTS:
        errors.append("authority_audit.result is not a valid lifecycle result")
    authority_evidence = authority.get("evidence", [])
    if not isinstance(authority_evidence, list):
        errors.append("authority_audit.evidence must be an array")
    elif authority.get("result") == "PASS":
        errors.extend(_evidence_errors("authority_audit.evidence", authority_evidence, page_sha, True))

    gates = data.get("gates")
    if not isinstance(gates, list):
        errors.append("gates must be an array")
        gates = []
    gate_map: dict[str, dict[str, Any]] = {}
    observed_order: list[str] = []
    for index, gate in enumerate(gates):
        if not isinstance(gate, dict):
            errors.append(f"gates[{index}] must be an object")
            continue
        gate_id = gate.get("id")
        if not _is_text(gate_id):
            errors.append(f"gates[{index}].id is required")
            continue
        observed_order.append(gate_id)
        if gate_id in gate_map:
            errors.append(f"duplicate gate id: {gate_id}")
        gate_map[gate_id] = gate
        result = gate.get("result")
        if result not in RESULTS:
            errors.append(f"{gate_id}: invalid result {result!r}")
        if not isinstance(gate.get("required"), bool):
            errors.append(f"{gate_id}: required must be boolean")
        elif gate_id in EXPECTED_GATES and gate.get("required") is not True:
            errors.append(f"{gate_id}: canonical G0-G8 stages are required")
        criteria = gate.get("criteria")
        if not isinstance(criteria, list) or not criteria or any(not _is_text(item) for item in criteria):
            errors.append(f"{gate_id}: criteria must be a non-empty array of text")
        dependencies = gate.get("depends_on")
        if not isinstance(dependencies, list):
            errors.append(f"{gate_id}: depends_on must be an array")
        elif dependencies != GATE_DEPENDENCIES.get(gate_id, []):
            errors.append(f"{gate_id}: depends_on does not match the canonical G0-G8 order")
        evidence = gate.get("evidence", [])
        if not isinstance(evidence, list):
            errors.append(f"{gate_id}: evidence must be an array")
            continue
        if result == "NOT_APPLICABLE":
            if not _is_text(gate.get("reason")):
                errors.append(f"{gate_id}: NOT_APPLICABLE requires a reason")
            if gate.get("required") is True:
                errors.append(f"{gate_id}: a required gate cannot be NOT_APPLICABLE")
        if result == "PASS":
            if gate_id not in EXPECTED_GATES:
                errors.append(f"{gate_id}: unexpected gate cannot PASS")
            expected_sha = page_sha if gate_id in EXPECTED_GATES[:7] else data.get("native_runtime", {}).get("source_sha")
            errors.extend(_evidence_errors(f"{gate_id}.evidence", evidence, expected_sha, True))
        elif result in {"FAIL", "BLOCKED"} and mode != "template" and not evidence:
            errors.append(f"{gate_id}: failed/blocked stage requires evidence")
        if result == "PASS":
            for dependency_id in GATE_DEPENDENCIES.get(gate_id, []):
                dependency = gate_map.get(dependency_id)
                if not dependency or dependency.get("result") != "PASS":
                    errors.append(f"{gate_id}: cannot PASS before prerequisite {dependency_id}=PASS")

    if observed_order != EXPECTED_GATES:
        errors.append("gates must contain exactly the canonical G0-G8 gate IDs in order")
    missing = [gate_id for gate_id in EXPECTED_GATES if gate_id not in gate_map]
    if missing:
        errors.append("missing required gates: " + ", ".join(missing))
    unexpected = [gate_id for gate_id in observed_order if gate_id not in EXPECTED_GATES]
    if unexpected:
        errors.append("unexpected gates: " + ", ".join(unexpected))

    matrix = data.get("test_matrix")
    if not isinstance(matrix, dict):
        errors.append("test_matrix must be an object")
        matrix = {}
    matrix_fields = (
        "anchor_viewports", "interpolation_viewports", "threshold_boundary_probes",
        "font_scales", "required_states", "page_specific_assertions", "generic_assertions",
    )
    for key in matrix_fields:
        if key not in matrix:
            errors.append(f"test_matrix.{key} is required")
    for key in ("anchor_viewports", "interpolation_viewports", "threshold_boundary_probes", "required_states", "page_specific_assertions", "generic_assertions"):
        if key in matrix and (not isinstance(matrix[key], list) or any(not _is_text(item) and not isinstance(item, dict) for item in matrix[key])):
            errors.append(f"test_matrix.{key} must be an array of text or object entries")
    font_scales = matrix.get("font_scales")
    if not isinstance(font_scales, list) or not font_scales or any(not isinstance(value, (int, float)) or isinstance(value, bool) or value <= 0 for value in font_scales):
        errors.append("test_matrix.font_scales must be a non-empty array of positive numbers")

    owner = data.get("product_owner_acceptance")
    if not isinstance(owner, dict):
        errors.append("product_owner_acceptance must be an object")
        owner = {}
    canonical = data.get("canonical_freeze")
    if not isinstance(canonical, dict):
        errors.append("canonical_freeze must be an object")
        canonical = {}
    native = data.get("native_runtime")
    if not isinstance(native, dict):
        errors.append("native_runtime must be an object")
        native = {}

    if mode in {"freeze", "runtime"}:
        for key in ("page_id", "route", "contract_path", "contract_version"):
            value = page.get(key)
            if not _is_text(value) or value.startswith("REPLACE_WITH_"):
                errors.append(f"{mode} mode requires a real page.{key}")
        for key in ("implementation_entrypoints", "included_states"):
            if not page.get(key):
                errors.append(f"{mode} mode requires page.{key}")
        if not _valid_sha(page_sha, SHA40):
            errors.append(f"{mode} mode requires page.exact_source_sha to be a full 40-character SHA")
        for key in ("global_standard_path", "shared_schema_path"):
            if not _is_text(authority.get(key)):
                errors.append(f"{mode} mode requires authority_audit.{key}")
        master = authority.get("visual_master")
        if not isinstance(master, dict) or not _is_text(master.get("path")) or not _valid_sha(master.get("sha256"), SHA64):
            errors.append(f"{mode} mode requires authority_audit.visual_master path and SHA-256")
        anchors = authority.get("visual_anchors")
        if not isinstance(anchors, list) or not anchors:
            errors.append(f"{mode} mode requires at least one authority_audit.visual_anchors entry")
        else:
            for index, anchor in enumerate(anchors):
                prefix = f"authority_audit.visual_anchors[{index}]"
                if not isinstance(anchor, dict):
                    errors.append(f"{prefix} must be an object")
                    continue
                if not _is_text(anchor.get("id")) or not _is_text(anchor.get("path")):
                    errors.append(f"{prefix} requires id and path")
                if not _valid_sha(anchor.get("sha256"), SHA64):
                    errors.append(f"{prefix}.sha256 must be a full 64-character SHA-256")
                if anchor.get("status") != "FROZEN":
                    errors.append(f"{prefix}.status must be FROZEN")
        if authority.get("result") != "PASS":
            errors.append(f"{mode} mode requires authority_audit.result PASS")
        if owner.get("decision") != "ACCEPT":
            errors.append(f"{mode} mode requires product_owner_acceptance.decision ACCEPT")
        if not _is_text(owner.get("reviewer")) or not _is_text(owner.get("reviewed_at")):
            errors.append(f"{mode} mode requires product-owner reviewer and reviewed_at")
        if owner.get("source_sha") != page_sha:
            errors.append(f"{mode} mode requires product-owner acceptance bound to page.exact_source_sha")
        if not _is_text(owner.get("evidence_url")):
            errors.append(f"{mode} mode requires product_owner_acceptance.evidence_url")
        for key in ("anchor_viewports", "interpolation_viewports", "threshold_boundary_probes", "required_states", "page_specific_assertions", "generic_assertions"):
            if not matrix.get(key):
                errors.append(f"{mode} mode requires test_matrix.{key}")
        for gate_id in EXPECTED_GATES[:7]:
            gate = gate_map.get(gate_id)
            if gate and gate.get("result") != "PASS":
                errors.append(f"{mode} mode requires {gate_id}=PASS")
        if canonical.get("status") != "SPEC_FROZEN":
            errors.append(f"{mode} mode requires canonical_freeze.status SPEC_FROZEN")
        if not _valid_sha(canonical.get("main_sha"), SHA40):
            errors.append(f"{mode} mode requires canonical_freeze.main_sha to be a full 40-character SHA")
        if not _valid_sha(canonical.get("merge_commit_sha"), SHA40):
            errors.append(f"{mode} mode requires canonical_freeze.merge_commit_sha to be a full 40-character SHA")

    if mode == "runtime":
        for gate_id in EXPECTED_GATES:
            gate = gate_map.get(gate_id)
            if gate and gate.get("result") != "PASS":
                errors.append(f"runtime mode requires {gate_id}=PASS")
        if native.get("status") != "PASS":
            errors.append("runtime mode requires native_runtime.status PASS")
        if not _valid_sha(native.get("source_sha"), SHA40):
            errors.append("runtime mode requires native_runtime.source_sha to be a full 40-character SHA")
        if not _is_text(native.get("build_run_id")):
            errors.append("runtime mode requires native_runtime.build_run_id")
        if not _valid_sha(native.get("apk_sha256"), SHA64):
            errors.append("runtime mode requires native_runtime.apk_sha256 to be a full 64-character SHA-256")
        if not _is_text(native.get("artifact_url")):
            errors.append("runtime mode requires native_runtime.artifact_url")

    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--checklist", required=True, help="Path to a responsive page freeze checklist JSON")
    parser.add_argument("--mode", choices=("template", "freeze", "runtime"), default="template")
    args = parser.parse_args()
    path = Path(args.checklist)
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        print(f"FAIL: cannot read checklist JSON: {exc}", file=sys.stderr)
        return 2
    errors = validate_checklist(data, args.mode)
    if errors:
        print(f"FAIL: {len(errors)} checklist issue(s)")
        for error in errors:
            print(f"- {error}")
        return 1
    print(f"PASS: checklist structure/evidence rules valid (mode={args.mode}, file={path})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
