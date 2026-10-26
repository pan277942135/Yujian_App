#!/usr/bin/env python3
"""Validate the reusable responsive-page freeze checklist and its evidence gates.

This validates process/evidence completeness. It does NOT replace a page-specific
responsive contract validator or prove that rendered geometry is correct.
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
SHA40 = re.compile(r"^[0-9a-f]{40}$")
SHA64 = re.compile(r"^[0-9a-f]{64}$")


def _is_text(value: Any) -> bool:
    return isinstance(value, str) and bool(value.strip())


def _valid_sha(value: Any, pattern: re.Pattern[str]) -> bool:
    return isinstance(value, str) and pattern.fullmatch(value) is not None


def validate_checklist(data: Any, mode: str = "template") -> list[str]:
    errors: list[str] = []
    if mode not in {"template", "freeze", "runtime"}:
        return [f"unsupported mode: {mode}"]
    if not isinstance(data, dict):
        return ["checklist root must be an object"]

    for key in (
        "template_id", "template_version", "status", "page", "authority_audit",
        "gates", "test_matrix", "evidence_record_template", "freeze_rules",
        "product_owner_acceptance", "canonical_freeze", "native_runtime",
    ):
        if key not in data:
            errors.append(f"missing required top-level key: {key}")

    page = data.get("page")
    if not isinstance(page, dict):
        errors.append("page must be an object")
        page = {}
    for key in ("page_id", "route", "implementation_entrypoints", "contract_path", "contract_version", "exact_source_sha"):
        if key not in page:
            errors.append(f"page.{key} is required")

    authority = data.get("authority_audit")
    if not isinstance(authority, dict):
        errors.append("authority_audit must be an object")
        authority = {}
    if authority.get("result", "NOT_RUN") not in RESULTS:
        errors.append("authority_audit.result is not a valid lifecycle result")
    if not isinstance(authority.get("evidence", []), list):
        errors.append("authority_audit.evidence must be an array")

    gates = data.get("gates")
    gate_map: dict[str, dict[str, Any]] = {}
    if not isinstance(gates, list):
        errors.append("gates must be an array")
        gates = []
    for index, gate in enumerate(gates):
        if not isinstance(gate, dict):
            errors.append(f"gates[{index}] must be an object")
            continue
        gate_id = gate.get("id")
        if not _is_text(gate_id):
            errors.append(f"gates[{index}].id is required")
            continue
        if gate_id in gate_map:
            errors.append(f"duplicate gate id: {gate_id}")
        gate_map[gate_id] = gate
        result = gate.get("result")
        if result not in RESULTS:
            errors.append(f"{gate_id}: invalid result {result!r}")
        if not isinstance(gate.get("criteria"), list):
            errors.append(f"{gate_id}: criteria must be an array")
        evidence = gate.get("evidence", [])
        if not isinstance(evidence, list):
            errors.append(f"{gate_id}: evidence must be an array")
            continue
        if result == "NOT_APPLICABLE" and not _is_text(gate.get("reason")):
            errors.append(f"{gate_id}: NOT_APPLICABLE requires a reason")
        if result == "PASS":
            if not evidence:
                errors.append(f"{gate_id}: PASS requires at least one evidence record")
            for evidence_index, record in enumerate(evidence):
                prefix = f"{gate_id}.evidence[{evidence_index}]"
                if not isinstance(record, dict):
                    errors.append(f"{prefix} must be an object")
                    continue
                if not _valid_sha(record.get("source_sha"), SHA40):
                    errors.append(f"{prefix}.source_sha must be a full 40-character commit SHA")
                if not (_is_text(record.get("run_id")) or _is_text(record.get("workflow_or_command"))):
                    errors.append(f"{prefix} needs run_id or workflow_or_command")
                if not (_is_text(record.get("report_url")) or record.get("artifact_urls")):
                    errors.append(f"{prefix} needs report_url or at least one artifact_url")
                if record.get("result", "PASS") != "PASS":
                    errors.append(f"{prefix}.result must be PASS for a passing gate")

    missing = [gate_id for gate_id in EXPECTED_GATES if gate_id not in gate_map]
    if missing:
        errors.append("missing required gates: " + ", ".join(missing))

    matrix = data.get("test_matrix")
    if not isinstance(matrix, dict):
        errors.append("test_matrix must be an object")
        matrix = {}
    for key in ("anchor_viewports", "interpolation_viewports", "threshold_boundary_probes", "font_scales", "required_states", "page_specific_assertions", "generic_assertions"):
        if key not in matrix:
            errors.append(f"test_matrix.{key} is required")
    font_scales = matrix.get("font_scales")
    if not isinstance(font_scales, list) or not font_scales or any(not isinstance(v, (int, float)) or v <= 0 for v in font_scales):
        errors.append("test_matrix.font_scales must be a non-empty array of positive numbers")

    owner_acceptance = data.get("product_owner_acceptance")
    if not isinstance(owner_acceptance, dict):
        errors.append("product_owner_acceptance must be an object")
        owner_acceptance = {}
    canonical_freeze = data.get("canonical_freeze")
    if not isinstance(canonical_freeze, dict):
        errors.append("canonical_freeze must be an object")
        canonical_freeze = {}
    native_runtime = data.get("native_runtime")
    if not isinstance(native_runtime, dict):
        errors.append("native_runtime must be an object")
        native_runtime = {}

    if mode in {"freeze", "runtime"}:
        for key in ("page_id", "route", "contract_path", "contract_version"):
            value = page.get(key)
            if not _is_text(value) or value.startswith("REPLACE_WITH_"):
                errors.append(f"freeze mode requires a real page.{key}")
        if not _valid_sha(page.get("exact_source_sha"), SHA40):
            errors.append("freeze mode requires page.exact_source_sha to be a full 40-character SHA")
        if authority.get("result") != "PASS":
            errors.append("freeze mode requires authority_audit.result PASS")
        if owner_acceptance.get("decision") != "ACCEPT":
            errors.append("freeze mode requires product_owner_acceptance.decision ACCEPT")
        if not _is_text(owner_acceptance.get("reviewer")) or not _is_text(owner_acceptance.get("reviewed_at")):
            errors.append("freeze mode requires product-owner reviewer and reviewed_at")
        for gate_id in EXPECTED_GATES[:7]:
            gate = gate_map.get(gate_id)
            if gate and gate.get("result") != "PASS":
                errors.append(f"freeze mode requires {gate_id}=PASS")
        if canonical_freeze.get("status") != "SPEC_FROZEN":
            errors.append("freeze mode requires canonical_freeze.status SPEC_FROZEN")
        if not _valid_sha(canonical_freeze.get("main_sha"), SHA40):
            errors.append("freeze mode requires canonical_freeze.main_sha to be a full 40-character SHA")

    if mode == "runtime":
        for gate_id in EXPECTED_GATES:
            gate = gate_map.get(gate_id)
            if gate and gate.get("result") != "PASS":
                errors.append(f"runtime mode requires {gate_id}=PASS")
        if native_runtime.get("status") != "PASS":
            errors.append("runtime mode requires native_runtime.status PASS")
        if not _valid_sha(native_runtime.get("source_sha"), SHA40):
            errors.append("runtime mode requires native_runtime.source_sha to be a full 40-character SHA")
        if not _is_text(native_runtime.get("build_run_id")):
            errors.append("runtime mode requires native_runtime.build_run_id")
        if not _valid_sha(native_runtime.get("apk_sha256"), SHA64):
            errors.append("runtime mode requires native_runtime.apk_sha256 to be a full 64-character SHA-256")
        if not _is_text(native_runtime.get("artifact_url")):
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
