"""Pure lifecycle, provenance, queue, and feedback rules for YuJian Work."""
from __future__ import annotations

from copy import deepcopy
import hashlib
import json
import re

SHA40 = re.compile(r"^[0-9a-f]{40}$")
SHA256 = re.compile(r"^[0-9a-f]{64}$")
ACTIVE_QUEUE_STATES = {
    "READY", "QUEUED", "RUNNING", "BLOCKED_INFRA", "BLOCKED_DEPENDENCY",
    "BLOCKED_CONFLICT", "BLOCKED_ADMISSION", "BLOCKED_QUICK_CHECKS",
    "BLOCKED_PERMISSION_VERIFICATION", "DEFERRED_SCOPE", "BLOCKED_STALE_SHA",
    "BLOCKED_NO_CHANGE", "CORE_PASS_UNMERGED", "INTEGRATION_PASS_UNMERGED",
}
TERMINAL_QUEUE_STATES = {
    "PASS", "FAIL", "CORE_PASS_UNMERGED", "INTEGRATION_PASS_UNMERGED",
    "CANCELLED_SUPERSEDED", "CANCELLED_WITHDRAWN",
}


def check_admission_identity(*, registry_sha: str | None, canonical_sha: str | None,
                             static_valid: bool, control_change: bool) -> list[str]:
    """Fail closed on missing/stale authority; proposals may differ by design."""
    errors: list[str] = []
    if not registry_sha or not SHA40.fullmatch(registry_sha):
        errors.append("canonical registry SHA is missing or invalid")
    if not canonical_sha or not SHA40.fullmatch(canonical_sha):
        errors.append("origin/main SHA is missing or invalid")
    if not static_valid:
        errors.append("static control-plane validation did not pass")
    if not control_change and registry_sha != canonical_sha:
        errors.append("local control plane is stale relative to refreshed origin/main")
    return errors


def validate_delivery(delivery: object) -> list[str]:
    if not isinstance(delivery, dict):
        return ["delivery must be an object"]
    errors: list[str] = []
    required = {"artifact_class", "source_sha", "source_branch", "api28_status", "formal_status"}
    missing = sorted(required - set(delivery))
    if missing:
        errors.append("delivery missing required fields: " + ", ".join(missing))
        return errors

    artifact_class = delivery.get("artifact_class")
    api28_status = delivery.get("api28_status")
    formal_status = delivery.get("formal_status")
    source_sha = delivery.get("source_sha")
    if artifact_class not in {"USER_PREVIEW", "API28_INTEGRATION_CANDIDATE", "FORMAL_ACCEPTANCE", "RELEASE_CANDIDATE"}:
        errors.append("delivery.artifact_class is unsupported")
    if not isinstance(source_sha, str) or not SHA40.fullmatch(source_sha):
        errors.append("delivery.source_sha must be a 40-char SHA")
    if api28_status not in {"NOT_RUN", "QUEUED", "RUNNING", "CORE_PASS", "PASS", "FAIL", "BLOCKED_INFRA"}:
        errors.append("delivery.api28_status is unsupported")
    if formal_status not in {"NOT_ELIGIBLE", "PENDING", "CORE_PASS", "PASS", "FAIL", "BLOCKED_INFRA"}:
        errors.append("delivery.formal_status is unsupported")

    if artifact_class == "USER_PREVIEW":
        if api28_status != "NOT_RUN":
            errors.append("USER_PREVIEW must keep api28_status=NOT_RUN")
        if formal_status != "NOT_ELIGIBLE":
            errors.append("USER_PREVIEW cannot be promoted to formal acceptance")
        if delivery.get("release_status", "NOT_ELIGIBLE") != "NOT_ELIGIBLE":
            errors.append("USER_PREVIEW cannot carry a release PASS")

    if api28_status == "PASS" and artifact_class != "FORMAL_ACCEPTANCE":
        errors.append("API28 PASS requires artifact_class=FORMAL_ACCEPTANCE")
    if api28_status == "CORE_PASS" and formal_status == "PASS":
        errors.append("CORE_PASS is not full formal acceptance")
    if formal_status == "CORE_PASS" and api28_status != "CORE_PASS":
        errors.append("formal CORE_PASS requires api28_status=CORE_PASS")

    if formal_status == "PASS":
        if artifact_class != "FORMAL_ACCEPTANCE":
            errors.append("formal PASS requires artifact_class=FORMAL_ACCEPTANCE")
        if api28_status != "PASS":
            errors.append("formal PASS requires api28_status=PASS")
        integration_sha = delivery.get("integration_sha")
        tested_sha = delivery.get("tested_source_sha")
        if not isinstance(integration_sha, str) or not SHA40.fullmatch(integration_sha):
            errors.append("formal PASS requires a valid integration_sha")
        if tested_sha != integration_sha or source_sha != integration_sha:
            errors.append("formal PASS must bind source_sha and tested_source_sha to integration_sha")
        if not delivery.get("batch_id") or not delivery.get("workflow_run_id"):
            errors.append("formal PASS requires batch_id and workflow_run_id")
        if not isinstance(delivery.get("apk_sha256"), str) or not SHA256.fullmatch(delivery["apk_sha256"]):
            errors.append("formal PASS requires a verified APK SHA-256")
        required_gates = delivery.get("required_gates")
        gate_results = delivery.get("gate_results")
        if not isinstance(required_gates, list) or not required_gates:
            errors.append("formal PASS requires required_gates")
        elif not isinstance(gate_results, dict) or any(gate_results.get(gate) != "PASS" for gate in required_gates):
            errors.append("formal PASS requires PASS for every declared gate")
        if not delivery.get("evidence_links"):
            errors.append("formal PASS requires evidence_links")

    if artifact_class in {"API28_INTEGRATION_CANDIDATE", "FORMAL_ACCEPTANCE"}:
        if delivery.get("integration_sha") is not None and not SHA40.fullmatch(str(delivery.get("integration_sha"))):
            errors.append("delivery.integration_sha must be a 40-char SHA")
        components = delivery.get("component_prs", [])
        if not isinstance(components, list):
            errors.append("delivery.component_prs must be an array")
        elif formal_status == "PASS" and any(item.get("result") == "PASS" for item in components if isinstance(item, dict)):
            errors.append("integration PASS cannot be copied onto a component PR result")
    return errors


def validate_feedback(record: object) -> list[str]:
    if not isinstance(record, dict):
        return ["feedback record must be an object"]
    required = {
        "feedback_id", "task_id", "pr_number", "preview_source_sha", "preview_apk_sha256",
        "device_profile", "observed_behavior", "expected_behavior", "reproduction_steps",
        "evidence_links", "severity", "fix_source_sha", "replacement_apk_sha256",
        "user_retest_status", "feedback_status",
    }
    errors = [f"feedback missing required field: {key}" for key in sorted(required - set(record))]
    if errors:
        return errors
    if not isinstance(record.get("feedback_id"), str) or not record["feedback_id"].strip():
        errors.append("feedback_id must be a non-empty string")
    if not isinstance(record.get("task_id"), str) or not record["task_id"].strip():
        errors.append("task_id must be a non-empty string")
    if not isinstance(record.get("pr_number"), int) or record["pr_number"] <= 0:
        errors.append("pr_number must be a positive integer")
    if not isinstance(record.get("preview_source_sha"), str) or not SHA40.fullmatch(record["preview_source_sha"]):
        errors.append("preview_source_sha must be a 40-char SHA")
    if not isinstance(record.get("preview_apk_sha256"), str) or not SHA256.fullmatch(record["preview_apk_sha256"]):
        errors.append("preview_apk_sha256 must be a 64-char SHA-256")
    for field in ("observed_behavior", "expected_behavior"):
        if not isinstance(record.get(field), str) or not record[field].strip():
            errors.append(f"{field} must be a non-empty string")
    device = record.get("device_profile")
    if not isinstance(device, dict):
        errors.append("device_profile must be an object")
    else:
        for field in ("device", "api_level", "screen_px", "font_scale"):
            if field not in device:
                errors.append(f"device_profile missing required field: {field}")
        if not isinstance(device.get("device"), str) or not device["device"].strip():
            errors.append("device_profile.device must be a non-empty string")
        if not isinstance(device.get("api_level"), int) or device["api_level"] < 1:
            errors.append("device_profile.api_level must be a positive integer")
        if not isinstance(device.get("screen_px"), str) or not re.fullmatch(r"[0-9]+x[0-9]+", device["screen_px"]):
            errors.append("device_profile.screen_px must use WIDTHxHEIGHT")
        if not isinstance(device.get("font_scale"), (int, float)) or device["font_scale"] <= 0:
            errors.append("device_profile.font_scale must be positive")
    if record.get("severity") not in {"P0", "P1", "P2", "P3"}:
        errors.append("severity is invalid")
    for field in ("reproduction_steps", "evidence_links"):
        if not isinstance(record.get(field), list):
            errors.append(f"{field} must be an array")
    if isinstance(record.get("evidence_links"), list) and any(not isinstance(value, str) or not value.strip() for value in record["evidence_links"]):
        errors.append("evidence_links entries must be non-empty strings")
    if record.get("feedback_status") not in {"OPEN", "FIXED_AWAITING_RETEST", "CLOSED"}:
        errors.append("feedback_status is invalid")
    if record.get("user_retest_status") not in {"NOT_RUN", "PASS", "FAIL"}:
        errors.append("user_retest_status is invalid")
    if record.get("feedback_status") == "CLOSED" and record.get("user_retest_status") != "PASS":
        errors.append("feedback cannot close before user retest passes")
    fix_sha = record.get("fix_source_sha")
    replacement_sha = record.get("replacement_apk_sha256")
    if fix_sha is not None:
        if not isinstance(fix_sha, str) or not SHA40.fullmatch(fix_sha):
            errors.append("fix_source_sha must be null or a 40-char SHA")
        if not isinstance(replacement_sha, str) or not SHA256.fullmatch(replacement_sha):
            errors.append("a fix requires replacement_apk_sha256")
        if record.get("feedback_status") not in {"FIXED_AWAITING_RETEST", "CLOSED"}:
            errors.append("fixed feedback must remain awaiting retest or be closed after retest")
    if record.get("user_retest_status") == "PASS" and fix_sha is None:
        errors.append("user retest PASS requires fix_source_sha")
    if fix_sha is None and replacement_sha is not None:
        errors.append("replacement_apk_sha256 must be null until a fix is supplied")
    return errors


def candidate_eligibility(candidate: dict, *, approval_label: str, allowed_associations: set[str],
                          quick_checks: set[str], supported_gates: set[str]) -> tuple[bool, str | None]:
    if candidate.get("state") != "open":
        return False, "PR_NOT_OPEN"
    if candidate.get("draft") is True:
        return False, "PR_DRAFT"
    if candidate.get("base_branch") != "main":
        return False, "BASE_NOT_MAIN"
    if candidate.get("head_repo") != candidate.get("base_repo"):
        return False, "FORK_SOURCE_NOT_ALLOWED"
    if candidate.get("author_association") not in allowed_associations:
        return False, "AUTHOR_NOT_TRUSTED"
    if approval_label not in set(candidate.get("labels", [])):
        return False, "MISSING_API28_READY_LABEL"
    if candidate.get("label_authorized") is not True:
        return False, "BLOCKED_PERMISSION_VERIFICATION:api28-ready label author is not a confirmed maintainer"
    if not candidate.get("task_id"):
        return False, "MISSING_TASK_ID"
    gates = candidate.get("required_gates")
    if not isinstance(gates, list) or not gates:
        return False, "MISSING_REQUIRED_GATES"
    dependencies = candidate.get("depends_on_prs")
    if not isinstance(dependencies, list) or any(not isinstance(item, int) or item <= 0 for item in dependencies):
        return False, "MISSING_OR_INVALID_DEPENDENCY_DECLARATION"
    unknown = sorted(set(gates) - supported_gates)
    if unknown:
        return False, "UNSUPPORTED_GATES:" + ",".join(unknown)
    checks = candidate.get("quick_check_results", {})
    missing = sorted(name for name in quick_checks if checks.get(name) != "PASS")
    if missing:
        return False, "QUICK_CHECKS_NOT_PASS:" + ",".join(missing)
    if not SHA40.fullmatch(str(candidate.get("head_sha", ""))):
        return False, "INVALID_HEAD_SHA"
    return True, None


def candidate_set_key(base_sha: str, scope: str, candidates: list[dict]) -> str:
    ordered = sorted(candidates, key=lambda item: (int(item["pr_number"]), item["head_sha"]))
    payload = {"base_sha": base_sha, "scope": scope, "candidates": [
        {
            "pr_number": int(item["pr_number"]),
            "head_sha": item["head_sha"],
            "task_id": item.get("task_id"),
            "required_gates": sorted(set(item.get("required_gates", []))),
            "depends_on_prs": sorted({int(dep) for dep in item.get("depends_on_prs", [])}),
        }
        for item in ordered
    ]}
    data = json.dumps(payload, sort_keys=True, separators=(",", ":")).encode()
    return hashlib.sha256(data).hexdigest()


def integration_plan(candidates: list[dict], *, scope_gates: set[str], merged_prs: set[int],
                     conflict_prs: set[int] | None = None) -> dict:
    """Topologically order compatible candidates with stable PR-number ties."""
    conflict_prs = conflict_prs or set()
    by_pr = {int(row["pr_number"]): row for row in candidates}
    deferred: list[dict] = []
    blocked: list[dict] = []
    pending: dict[int, dict] = {}
    for pr_number, row in by_pr.items():
        if not set(row.get("required_gates", [])) <= scope_gates:
            deferred.append({"pr_number": pr_number, "head_sha": row.get("head_sha"), "status": "DEFERRED_SCOPE"})
        else:
            pending[pr_number] = row

    integrated: list[dict] = []
    completed = set(merged_prs)
    while pending:
        ready: list[int] = []
        unresolved: dict[int, list[int]] = {}
        for pr_number, row in pending.items():
            deps = {int(dep) for dep in row.get("depends_on_prs", [])}
            missing_external = {dep for dep in deps if dep not in completed and dep not in pending}
            blocked_dep = {dep for dep in deps if dep in blocked_numbers(blocked)}
            if missing_external or blocked_dep:
                unresolved[pr_number] = sorted(missing_external | blocked_dep)
            elif deps <= completed:
                ready.append(pr_number)
        if not ready:
            for pr_number in sorted(pending):
                row = pending[pr_number]
                unresolved_deps = unresolved.get(pr_number)
                blocked.append({"pr_number": pr_number, "head_sha": row.get("head_sha"), "status": "BLOCKED_DEPENDENCY", "depends_on_prs": unresolved_deps or sorted(set(row.get("depends_on_prs", [])))})
            break
        pr_number = min(ready)
        row = pending.pop(pr_number)
        if pr_number in conflict_prs:
            blocked.append({"pr_number": pr_number, "head_sha": row.get("head_sha"), "status": "BLOCKED_CONFLICT", "reason": "merge conflict"})
            continue
        integrated.append({"pr_number": pr_number, "head_sha": row["head_sha"], "required_gates": sorted(set(row.get("required_gates", [])))})
        completed.add(pr_number)
    return {"integrated": integrated, "blocked": blocked, "deferred": deferred}


def blocked_numbers(rows: list[dict]) -> set[int]:
    return {int(row["pr_number"]) for row in rows if row.get("status", "").startswith("BLOCKED_")}


def reconcile_queue(existing: list[dict], current: list[dict], *, now: str,
                    observed_prs: set[int] | None = None) -> list[dict]:
    """Append current heads; preserve superseded/withdrawn records and refresh active metadata."""
    rows = deepcopy(existing)
    current_by_pr = {int(item["pr_number"]): item for item in current}
    for row in rows:
        pr_number = int(row.get("pr_number", 0))
        replacement = current_by_pr.get(pr_number)
        if replacement and row.get("head_sha") != replacement.get("head_sha") and row.get("status") in ACTIVE_QUEUE_STATES:
            row["status"] = "CANCELLED_SUPERSEDED"
            row["updated_at"] = now
            row["superseded_by_sha"] = replacement["head_sha"]
            row["reason"] = "new PR head replaced queued source SHA"
        elif replacement and row.get("head_sha") == replacement.get("head_sha") and row.get("status") in ACTIVE_QUEUE_STATES:
            row["required_gates"] = list(replacement.get("required_gates", []))
            row["task_id"] = replacement.get("task_id")
            row["reason"] = replacement.get("reason")
            if row.get("status") not in {"INTEGRATION_PASS_UNMERGED", "CORE_PASS_UNMERGED"}:
                row["status"] = replacement.get("initial_status", row.get("status", "QUEUED"))
            row["updated_at"] = now
        elif observed_prs is not None and pr_number in observed_prs and replacement is None and row.get("status") in ACTIVE_QUEUE_STATES:
            row["status"] = "CANCELLED_WITHDRAWN"
            row["updated_at"] = now
            row["reason"] = "PR closed or api28-ready label removed"
    for item in sorted(current, key=lambda value: int(value["pr_number"])):
        same = [row for row in rows if int(row.get("pr_number", 0)) == int(item["pr_number"]) and row.get("head_sha") == item["head_sha"] and row.get("status") not in {"CANCELLED_SUPERSEDED", "CANCELLED_WITHDRAWN"}]
        if same:
            continue
        rows.append({
            "pr_number": int(item["pr_number"]),
            "head_sha": item["head_sha"],
            "task_id": item.get("task_id"),
            "required_gates": list(item.get("required_gates", [])),
            "status": item.get("initial_status", "QUEUED"),
            "first_seen_at": now,
            "updated_at": now,
            "reason": item.get("reason"),
            "independent_pr_result": "NOT_TESTED",
        })
    return rows


def integration_outcome(*, required_gates: list[str], gate_results: dict[str, str],
                        runner_available: bool, integrated_prs: list[dict], integration_sha: str | None) -> dict:
    if not runner_available:
        return {"batch_status": "BLOCKED_INFRA", "api28_status": "BLOCKED_INFRA", "component_results": [
            {"pr_number": row["pr_number"], "head_sha": row["head_sha"], "result": "NOT_INDEPENDENTLY_TESTED", "batch_result": "BLOCKED_INFRA"}
            for row in integrated_prs
        ]}
    if not integration_sha or not SHA40.fullmatch(integration_sha):
        return {"batch_status": "BLOCKED_INFRA", "api28_status": "BLOCKED_INFRA", "reason": "missing Integration SHA", "component_results": []}
    if any(gate_results.get(gate) != "PASS" for gate in required_gates):
        status = "FAIL" if any(gate_results.get(gate) == "FAIL" for gate in required_gates) else "BLOCKED_INFRA"
    else:
        status = "PASS"
    return {
        "batch_status": status,
        "api28_status": status,
        "integration_sha": integration_sha,
        "component_results": [
            {"pr_number": row["pr_number"], "head_sha": row["head_sha"], "result": "NOT_INDEPENDENTLY_TESTED", "batch_result": "TESTED_INTEGRATED"}
            for row in integrated_prs
        ],
    }


def assert_public_ledger_paths(paths: list[str]) -> list[str]:
    forbidden = {"PROJECT_MASTER_STATE.yaml", "WORK_EXECUTION_STATE.json", "harness/build-log.md"}
    return sorted(forbidden.intersection(paths))
