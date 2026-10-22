#!/usr/bin/env python3
"""Derive batch result from actual APK hashes, Harness artifacts, and Actions jobs."""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[1]


def read_json(path: Path, default=None):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return default


def iso_now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--batch", type=Path, required=True)
    parser.add_argument("--artifacts", type=Path, required=True)
    parser.add_argument("--run-metadata", type=Path, required=True)
    parser.add_argument("--android-outcome", choices=["success", "failure", "cancelled", "skipped"], required=True)
    parser.add_argument("--gemini-outcome", choices=["success", "failure", "cancelled", "skipped"], default="skipped")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    batch = read_json(args.batch, {})
    metadata = read_json(args.run_metadata, {})
    policy = yaml.safe_load((ROOT / "harness/ANDROID_BATCH_ACCEPTANCE_POLICY_V1_1.yaml").read_text(encoding="utf-8"))
    scope = batch.get("scope")
    required_gates = list(policy["core_gates"] if scope == "CORE" else policy["full_gates"])
    run_id = str(metadata.get("workflow_run_id") or batch.get("workflow_run_id") or "")
    repo = batch.get("repository", "pan277942135/Yujian_App")
    run_url = f"https://github.com/{repo}/actions/runs/{run_id}" if run_id else ""
    artifact_url = f"https://github.com/{repo}/actions/runs/{run_id}/artifacts" if run_id else ""
    links = [url for url in (run_url, artifact_url) if url]

    apk_sha = None
    test_apk_sha = None
    apk_path = next((path for path in args.artifacts.rglob("app-debug.apk") if path.is_file()), None)
    test_apk_path = next((path for path in args.artifacts.rglob("app-debug-androidTest.apk") if path.is_file()), None)
    provenance_path = next((path for path in args.artifacts.rglob("apk_provenance.json") if path.is_file()), None)
    provenance = read_json(provenance_path, {}) if provenance_path else {}
    if apk_path:
        actual_sha = hashlib.sha256(apk_path.read_bytes()).hexdigest()
        if provenance.get("apk_sha256") == actual_sha:
            apk_sha = actual_sha
    if test_apk_path:
        actual_test_sha = hashlib.sha256(test_apk_path.read_bytes()).hexdigest()
        if provenance.get("android_test_apk_sha256") == actual_test_sha:
            test_apk_sha = actual_test_sha
    provenance_valid = bool(
        apk_sha
        and test_apk_sha
        and provenance.get("source_sha") == batch.get("integration_sha")
        and provenance.get("artifact_class") == "API28_INTEGRATION_CANDIDATE"
    )

    gate_results = {gate: "NOT_RUN" for gate in required_gates}
    gate_evidence_errors: list[dict] = []
    runtime_files = list(args.artifacts.rglob("runtime_gate_result.json"))
    for path in runtime_files:
        result = read_json(path, {})
        gate = result.get("gate")
        if gate not in gate_results:
            continue
        classification = result.get("classification")
        if result.get("build_sha") != batch.get("integration_sha"):
            gate_evidence_errors.append({"gate": gate, "reason": "runtime evidence build_sha does not match Integration SHA", "path": str(path)})
            gate_results[gate] = "BLOCKED_INFRA"
            continue
        if result.get("api_level") != 28:
            gate_evidence_errors.append({"gate": gate, "reason": "runtime evidence is not from API 28", "path": str(path)})
            gate_results[gate] = "BLOCKED_INFRA"
            continue
        if not apk_sha or result.get("app_apk_sha256") != apk_sha:
            gate_evidence_errors.append({"gate": gate, "reason": "runtime evidence APK SHA-256 does not match the batch APK", "path": str(path)})
            gate_results[gate] = "BLOCKED_INFRA"
            continue
        if not test_apk_sha or result.get("test_apk_sha256") != test_apk_sha:
            gate_evidence_errors.append({"gate": gate, "reason": "runtime evidence Android test APK SHA-256 does not match the batch APK pair", "path": str(path)})
            gate_results[gate] = "BLOCKED_INFRA"
            continue
        if classification == "PASS":
            gate_results[gate] = "PASS"
        elif classification in {"FAIL", "FAIL_TEST", "FAIL_ARTIFACT", "FAIL_EVIDENCE"}:
            gate_results[gate] = "FAIL"
        elif classification in {"BLOCKED_INFRA", "CANCELLED"}:
            gate_results[gate] = "BLOCKED_INFRA"

    status = "NOT_RUN"
    api28_status = "NOT_RUN"
    reason = None
    if batch.get("mode") == "DRY_RUN":
        status = "DRY_RUN"
    elif batch.get("duplicate_of"):
        status = "DUPLICATE_SKIPPED"
    elif not batch.get("integration_sha"):
        blockers = batch.get("blocked", [])
        status = "BLOCKED_CONFLICT" if blockers else (batch.get("batch_status") or "NO_CANDIDATES")
        reason = "no candidate set could produce an Integration SHA"
    else:
        values = [gate_results[gate] for gate in required_gates]
        if gate_evidence_errors:
            status, api28_status = "BLOCKED_INFRA", "BLOCKED_INFRA"
            reason = "one or more required runtime evidence records failed exact-source or APK-pair verification"
        elif any(value == "FAIL" for value in values):
            status, api28_status = "FAIL", "FAIL"
        elif any(value == "BLOCKED_INFRA" for value in values):
            status, api28_status = "BLOCKED_INFRA", "BLOCKED_INFRA"
        elif any(value != "PASS" for value in values):
            if args.android_outcome == "failure" and all(value == "NOT_RUN" for value in values):
                status = "FAIL"
                reason = "build or quick checks failed before the API28 Harness was launched"
            else:
                status, api28_status = "BLOCKED_INFRA", "BLOCKED_INFRA"
                reason = "one or more required API28 gates did not produce a PASS result"
        elif not provenance_valid:
            status, api28_status = "BLOCKED_INFRA", "BLOCKED_INFRA"
            reason = "the APK SHA-256 or exact Integration SHA provenance was unavailable"
        elif args.android_outcome != "success" or (scope == "FULL" and args.gemini_outcome != "success"):
            status, api28_status = "BLOCKED_INFRA", "BLOCKED_INFRA"
            reason = "a required batch workflow failed after gate evidence was collected"
        elif scope == "CORE":
            status, api28_status = "CORE_PASS", "CORE_PASS"
        else:
            status, api28_status = "PASS", "PASS"

    result = {
        "batch_id": batch.get("batch_id"),
        "batch_status": status,
        "api28_status": api28_status,
        "reason": reason,
        "scope": scope,
        "required_gates": required_gates,
        "gate_results": gate_results,
        "integration_sha": batch.get("integration_sha"),
        "tree_sha": batch.get("tree_sha"),
        "base_sha": batch.get("base_sha"),
        "integrated": batch.get("integrated", []),
        "blocked": batch.get("blocked", []),
        "deferred": batch.get("deferred", []),
        "apk_sha256": apk_sha,
        "android_test_apk_sha256": test_apk_sha,
        "gate_evidence_errors": gate_evidence_errors,
        "artifact_class": provenance.get("artifact_class"),
        "apk_artifact_name": provenance.get("artifact_name"),
        "apk_download_page_url": provenance.get("github_actions_artifacts_url"),
        "apk_build_run_id": provenance.get("github_actions_run_id"),
        "apk_build_run_url": provenance.get("github_actions_run_url"),
        "source_sha": provenance.get("source_sha"),
        "source_branch": provenance.get("source_branch"),
        "apk_provenance_valid": provenance_valid,
        "workflow_run_id": run_id,
        "build_workflow_run_id": provenance.get("github_actions_run_id") or run_id,
        "workflow_run_url": run_url,
        "artifact_url": artifact_url,
        "queue_started_at": metadata.get("api28_queue_started_at") or batch.get("queue_started_at"),
        "runner_started_at": metadata.get("runner_started_at"),
        "finished_at": metadata.get("finished_at") or iso_now(),
        "actual_queue_wait_seconds": metadata.get("api28_queue_wait_seconds"),
        "job_outcomes": metadata.get("jobs", []),
        "evidence_links": links,
        "component_results": [
            {"pr_number": item.get("pr_number"), "head_sha": item.get("head_sha"),
             "result": "NOT_INDEPENDENTLY_TESTED", "batch_result": status,
             "integration_sha": batch.get("integration_sha")}
            for item in batch.get("integrated", [])
        ],
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"BATCH_STATUS={status}")
    print(f"API28_STATUS={api28_status}")
    print(f"APK_SHA256={apk_sha or 'NOT_RUN'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
