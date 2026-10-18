#!/usr/bin/env python3
"""Bind a validated M1 evidence manifest to its exact runtime gate result."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--evidence-dir", type=Path, required=True)
    parser.add_argument("--runtime-result", type=Path, required=True)
    parser.add_argument("--workflow-run-id", required=True)
    args = parser.parse_args()

    manifest_path = args.evidence_dir / "recognition_runtime_evidence_manifest_m1_v3_1.json"
    if not manifest_path.is_file() or not args.runtime_result.is_file():
        raise SystemExit("M1 evidence manifest or runtime gate result is missing")

    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    result = json.loads(args.runtime_result.read_text(encoding="utf-8"))
    if manifest.get("contract_id") != "M1_RECOGNITION_RUNTIME_GATE_V3_1":
        raise SystemExit("M1 evidence manifest has an unexpected contract ID")
    repository_root = Path(__file__).resolve().parents[1]
    contract_path = repository_root / "docs/qa/recognition_main_journey/m1_recognition_runtime_gate_contract_v3_1.json"
    contract = json.loads(contract_path.read_text(encoding="utf-8"))
    if manifest.get("authority", {}).get("contract_sha256") != sha256(contract_path):
        raise SystemExit("M1 evidence contract SHA does not match the current V3.1 gate contract")
    collision = manifest.get("motion_authority_collision", {})
    active_collision = contract.get("motion_authority_collision", {})
    if (
        collision.get("status") != "RESOLVED"
        or active_collision.get("status") != "RESOLVED"
        or manifest.get("release_readiness") != "M1_RELEASE_ELIGIBLE"
    ):
        raise SystemExit(
            "M1 release remains REVIEW_REQUIRED: V3.1 immediate RESULT_READY and V1.2 Resolve handoff authority collision is unresolved"
        )
    if result.get("gate") != "recognition-frozen":
        raise SystemExit("runtime result is not the recognition-frozen gate")
    if result.get("build_sha") != manifest.get("source_commit_sha"):
        raise SystemExit("runtime gate result SHA does not match M1 evidence source SHA")
    if result.get("api_level") != manifest.get("api_level"):
        raise SystemExit("runtime gate result API level does not match M1 evidence")
    if result.get("classification") != "PASS":
        raise SystemExit("M1 evidence manifest can only bind to a PASS runtime gate")
    if any(result.get(key) != "PASS" for key in ("preflight", "install", "instrumentation", "evidence")):
        raise SystemExit("runtime gate result is missing a required PASS classification")
    if manifest.get("catalog", {}).get("proc_07_timeout_status") != "BLOCKED_PARAMETER":
        raise SystemExit("PROC-07 must remain BLOCKED_PARAMETER in the M1 evidence manifest")
    if manifest.get("validation_status") != "PASS":
        raise SystemExit("M1 evidence contract validation did not pass")
    if not re.fullmatch(r"[0-9]+", args.workflow_run_id):
        raise SystemExit("exact GitHub Actions workflow run ID is required")
    if manifest.get("workflow_run_id") != args.workflow_run_id:
        raise SystemExit("runtime evidence was collected under a different workflow run ID")
    if manifest.get("visual_authority", {}).get("status") != "SOURCE_MAP_VALIDATED":
        raise SystemExit("Design System authority map is incomplete or has review-required visual targets")
    if manifest.get("visual_authority", {}).get("review_required_runtime_targets"):
        raise SystemExit("one or more visual runtime targets remain REVIEW_REQUIRED")
    visual_parity = manifest.get("visual_parity", {})
    if visual_parity.get("classification") != "PASS" or visual_parity.get("state_count") != 9:
        raise SystemExit("nine-state frozen/runtime pixel-diff gate did not pass")

    manifest["runtime_gate"] = {
        "classification": result["classification"],
        "result_path": args.runtime_result.name,
        "result_sha256": sha256(args.runtime_result),
        "workflow_run_id": args.workflow_run_id,
        "source_sha_matches": True,
        "api_level_matches": True,
    }
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("M1 evidence manifest bound to exact runtime result SHA")


if __name__ == "__main__":
    main()
