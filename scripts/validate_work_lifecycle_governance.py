#!/usr/bin/env python3
"""Static cross-checks for the V1.1 Work lifecycle and API28 batch policy."""
from __future__ import annotations

import json
from pathlib import Path
import re
import sys

import yaml

ROOT = Path(__file__).resolve().parents[1]
EXPECTED_GLOBAL_MUTEX = "yujian-api28-global"
FORBIDDEN_PUBLIC_STATE = {
    "PROJECT_MASTER_STATE.yaml", "WORK_EXECUTION_STATE.json", "harness/build-log.md"
}


def load_yaml(path: Path):
    value = yaml.safe_load(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"{path.relative_to(ROOT)} must contain a YAML object")
    return value


def triggers(workflow: dict):
    return workflow.get("on", workflow.get(True, {})) or {}


def main() -> int:
    errors: list[str] = []
    registry_path = ROOT / "CONTROL_PLANE_REGISTRY.yaml"
    policy_path = ROOT / "harness/ANDROID_BATCH_ACCEPTANCE_POLICY_V1_1.yaml"
    feedback_path = ROOT / "harness/USER_FEEDBACK_SCHEMA_V1.json"
    state_schema_path = ROOT / "harness/WORK_EXECUTION_STATE_SCHEMA_V1.json"
    registry = load_yaml(registry_path)
    policy = load_yaml(policy_path)
    feedback = json.loads(feedback_path.read_text(encoding="utf-8"))
    state_schema = json.loads(state_schema_path.read_text(encoding="utf-8"))

    required_authorities = {
        "harness/WORK_INSTRUCTION_PROTOCOL_V1_1.md",
        "harness/ANDROID_BATCH_ACCEPTANCE_POLICY_V1_1.yaml",
        "harness/USER_FEEDBACK_SCHEMA_V1.json",
        "harness/WORK_INSTRUCTION_TEMPLATE_V1_1.md",
        "scripts/work_lifecycle.py",
        "scripts/api28_batch_ledger.py",
        "scripts/api28_batch_git.py",
        "scripts/api28_batch_monitor.py",
        "scripts/finalize_api28_batch.py",
        "scripts/filter_api28_candidates.py",
        "scripts/create_api28_integration_candidate.py",
        "scripts/write_apk_provenance.py",
        "scripts/validate_work_lifecycle_governance.py",
        "tests/test_work_lifecycle_governance.py",
        ".github/workflows/api28-batch-acceptance.yml",
    }
    indexed = set(registry.get("canonical_static_control_plane", {}).get("files", []))
    for path in sorted(required_authorities - indexed):
        errors.append(f"registry canonical_static_control_plane.files does not index {path}")
    for path in sorted(required_authorities):
        if not (ROOT / path).is_file():
            errors.append(f"required lifecycle authority is missing: {path}")

    if policy.get("timezone") != "Asia/Shanghai":
        errors.append("API28 policy timezone must be Asia/Shanghai")

    state_model = policy.get("state_model", {})
    freeze_model = state_model.get("freeze_candidate", {})
    required_freeze_evidence = {
        "audit_window_utc", "snapshot_timestamp_utc", "main_sha", "freeze_ref", "audit_pack_sha256"
    }
    if freeze_model.get("status") != "FROZEN_CANDIDATE":
        errors.append("freeze state must be named FROZEN_CANDIDATE")
    if not required_freeze_evidence <= set(freeze_model.get("required_evidence", [])):
        errors.append("FROZEN_CANDIDATE must bind exact SHA, UTC audit window, snapshot, freeze ref, and audit-pack SHA-256")
    if not {"API28_PASS", "FORMAL_ACCEPTANCE", "PRODUCTION_VERIFIED", "RELEASE_AUTHORIZATION"} <= set(freeze_model.get("does_not_imply", [])):
        errors.append("FROZEN_CANDIDATE must not imply API28, formal acceptance, production verification, or release authorization")

    integration_model = state_model.get("integration", {})
    if integration_model.get("blocked_status") != "BLOCKED" or integration_model.get("preserve_candidate_for_retry") is not True:
        errors.append("blocked integration must preserve its candidate for retry")
    if integration_model.get("blocked_or_unrun_gates_are_pass") is not False:
        errors.append("blocked or unrun gates must never count as PASS")

    evidence_scope = state_model.get("evidence_scope", {})
    if evidence_scope.get("exact_sha_required") is not True:
        errors.append("verification evidence must be bound to the exact tested SHA")
    if evidence_scope.get("queued_or_skipped_job_result") != "NOT_RUN":
        errors.append("queued or skipped jobs must be classified NOT_RUN")
    if evidence_scope.get("maximum_queue_wait_minutes") != policy.get("batch", {}).get("maximum_queue_wait_minutes"):
        errors.append("status model queue timeout must match the API28 batch policy")
    if evidence_scope.get("exceeded_queue_wait_status") != "BLOCKED_INFRA":
        errors.append("API28 queue timeout must resolve to BLOCKED_INFRA")
    if evidence_scope.get("runner_unavailable_status") != "BLOCKED_INFRA":
        errors.append("runner unavailability must resolve to BLOCKED_INFRA")

    production_model = state_model.get("production_verification", {})
    required_production_evidence = {
        "current_main_sha_verified", "control_plane_admission_pass", "android_ci_pass_on_exact_main_sha",
        "api28_full_batch_required_gates_pass", "camera_runtime_pass_on_exact_main_sha",
        "required_gate_matrix_all_pass", "apk_sha256_and_provenance_verified", "pages_deployed_sha_verified",
    }
    if production_model.get("status") != "PRODUCTION_VERIFIED":
        errors.append("production verification state must be named PRODUCTION_VERIFIED")
    if not required_production_evidence <= set(production_model.get("required_evidence", [])):
        errors.append("PRODUCTION_VERIFIED is missing one or more required exact-SHA gates/evidence")
    if production_model.get("pages_only_is_sufficient") is not False:
        errors.append("Pages-only verification must not imply application PRODUCTION_VERIFIED")
    if production_model.get("queued_or_skipped_api28_is_sufficient") is not False:
        errors.append("queued/skipped API28 jobs must not imply PRODUCTION_VERIFIED")
    if production_model.get("release_authorization_implied") is not False:
        errors.append("PRODUCTION_VERIFIED must not imply release authorization")

    protocol_text = (ROOT / "harness/WORK_INSTRUCTION_PROTOCOL_V1_1.md").read_text(encoding="utf-8")
    for required_phrase in (
        "Freeze, verification scope, and production closure",
        "`FROZEN_CANDIDATE`",
        "`PRODUCTION_VERIFIED`",
        "Pages-only success",
        "BLOCKED_INFRA",
    ):
        if required_phrase not in protocol_text:
            errors.append(f"Work protocol is missing freeze/verification rule: {required_phrase}")
    schedule = policy.get("schedule", {})
    if schedule.get("midday", {}).get("local_time") != "12:30" or schedule.get("midday", {}).get("cron_utc") != "30 4 * * *":
        errors.append("midday schedule must be 12:30 Asia/Shanghai (30 4 * * *)")
    if schedule.get("evening", {}).get("local_time") != "21:00" or schedule.get("evening", {}).get("cron_utc") != "0 13 * * *":
        errors.append("evening schedule must be 21:00 Asia/Shanghai (0 13 * * *)")
    if policy.get("batch", {}).get("api28_mutex", {}).get("group") != EXPECTED_GLOBAL_MUTEX:
        errors.append("policy and workflows must use the single global API28 mutex")
    if policy.get("batch", {}).get("queue_storage", {}).get("branch") != "api28-batch-ledger":
        errors.append("candidate queue must persist on api28-batch-ledger")
    if policy.get("batch", {}).get("queue_storage", {}).get("kind") != "dedicated_git_branch":
        errors.append("GitHub concurrency pending state cannot be the candidate queue")
    mutex_policy = policy.get("batch", {}).get("api28_mutex", {})
    if mutex_policy.get("pending_execution_queue_is_task_database") is not False:
        errors.append("GitHub concurrency queue must not become the candidate task database")
    if mutex_policy.get("authoritative_task_queue") != "api28-batch-ledger":
        errors.append("durable ledger branch must remain the task queue authority")
    if policy.get("runner_security", {}).get("candidate_host_shell_executes_on_self_hosted_api28") is not False:
        errors.append("self-hosted host-side scripts must come from the trusted scheduler SHA")
    if policy.get("runner_security", {}).get("candidate_apk_executes_on_api28_emulator") is not True:
        errors.append("policy must disclose that candidate APK/instrumentation code executes in the emulator")
    if policy.get("runner_security", {}).get("isolated_runner_or_emulator_boundary_required") is not True:
        errors.append("policy must require an isolated API28 runner boundary")

    feedback_required = {
        "feedback_id", "task_id", "pr_number", "preview_source_sha", "preview_apk_sha256",
        "device_profile", "observed_behavior", "expected_behavior", "reproduction_steps",
        "evidence_links", "severity", "fix_source_sha", "replacement_apk_sha256",
        "user_retest_status", "feedback_status",
    }
    if not feedback_required <= set(feedback.get("required", [])):
        errors.append("feedback schema is missing one or more required feedback fields")
    state_properties = state_schema.get("properties", {})
    if "delivery" not in state_properties or "user_feedback" not in state_properties:
        errors.append("Work execution state schema must carry delivery and user_feedback")
    elif state_properties["user_feedback"].get("items", {}).get("$ref") != "./USER_FEEDBACK_SCHEMA_V1.json":
        errors.append("Work execution state must reference the canonical feedback schema")

    workflow_paths = [
        ".github/workflows/android.yml",
        ".github/workflows/camera-capture-p0-v2.yml",
        ".github/workflows/camera-runtime-harness.yml",
        ".github/workflows/nh01-cloud-runtime.yml",
        ".github/workflows/api28-batch-acceptance.yml",
    ]
    workflows = {path: load_yaml(ROOT / path) for path in workflow_paths}
    central = workflows[".github/workflows/api28-batch-acceptance.yml"]
    central_on = triggers(central)
    central_concurrency = central.get("concurrency", {})
    central_crons = {row.get("cron") for row in central_on.get("schedule", []) if isinstance(row, dict)}
    if central_crons != {"30 4 * * *", "0 13 * * *"}:
        errors.append("central workflow must contain both scheduled cron entries")
    if "workflow_dispatch" not in central_on:
        errors.append("central workflow must allow controlled workflow_dispatch")
    if "github.ref == 'refs/heads/main'" not in str(central.get("jobs", {})):
        errors.append("central workflow jobs must fail closed outside refs/heads/main")
    ledger_script = (ROOT / "scripts/api28_batch_git.py").read_text(encoding="utf-8")
    if "api28_batch_git.py" not in (ROOT / workflow_paths[-1]).read_text(encoding="utf-8") or 'BRANCH = "api28-batch-ledger"' not in ledger_script:
        errors.append("central workflow must persist the queue to the dedicated api28-batch-ledger branch")
    if "cancel-in-progress: false" not in (ROOT / workflow_paths[-1]).read_text(encoding="utf-8"):
        errors.append("central workflow concurrency must retain active batches")
    if not central_concurrency:
        errors.append("central scheduler must serialize duplicate orchestration triggers")
    elif central_concurrency.get("queue") != "max" or central_concurrency.get("cancel-in-progress") is not False:
        errors.append("central scheduler must retain queued triggers without canceling the active batch")

    android_path = ".github/workflows/android.yml"
    android = workflows[android_path]
    android_jobs = android.get("jobs", {})
    runtime = android_jobs.get("android-runtime", {})
    runtime_mutex = runtime.get("concurrency", {})
    if EXPECTED_GLOBAL_MUTEX not in str(runtime_mutex):
        errors.append("android-runtime must acquire the global API28 mutex")
    if runtime_mutex.get("queue") != "max" or runtime_mutex.get("cancel-in-progress") is not False:
        errors.append("android-runtime must keep FIFO pending work without replacing queued API28 jobs")
    runtime_if = str(runtime.get("if", ""))
    if "api28-batch-acceptance.yml@refs/heads/main" not in runtime_if or "refs/heads/main" not in runtime_if:
        errors.append("android-runtime central-call guard must require the trusted central main workflow")
    android_on = triggers(android)
    if "push" not in android_on or "pull_request" not in android_on:
        errors.append("ordinary Android push/PR build triggers must remain enabled for previews")
    if "workflow_call" not in android_on:
        errors.append("Android reusable workflow_call entry is missing")
    if "API28_INTEGRATION_CANDIDATE" in str(android_on.get("workflow_dispatch", {})):
        errors.append("manual Android dispatch must not mint an API28 integration artifact")

    for path in workflow_paths[1:4]:
        workflow = workflows[path]
        event_names = set(triggers(workflow))
        if event_names.intersection({"push", "pull_request"}):
            errors.append(f"legacy API28 workflow still auto-triggers on push/PR: {path}")
        for job_name, job in workflow.get("jobs", {}).items():
            runner = job.get("runs-on")
            job_text = json.dumps(job, ensure_ascii=False)
            is_api28 = "api28" in str(runner).lower() or re.search(r"api-level['\"]?\s*:\s*28", job_text) is not None
            if is_api28:
                mutex = job.get("concurrency", {})
                if EXPECTED_GLOBAL_MUTEX not in str(mutex):
                    errors.append(f"API28 job {path}:{job_name} lacks the global mutex")
                if mutex.get("queue") != "max" or mutex.get("cancel-in-progress") is not False:
                    errors.append(f"API28 job {path}:{job_name} can replace older pending work")
                if "refs/heads/main" not in str(job.get("if", "")):
                    errors.append(f"legacy API28 job {path}:{job_name} is not main-only")

    for path in workflow_paths:
        workflow = workflows[path]
        text = (ROOT / path).read_text(encoding="utf-8")
        if "api28" in text.lower() or "API 28" in text:
            jobs = workflow.get("jobs", {})
            for job_name, job in jobs.items():
                runner = job.get("runs-on")
                job_text = json.dumps(job, ensure_ascii=False)
                is_api28 = "api28" in str(runner).lower() or re.search(r"api-level['\"]?\s*:\s*28", job_text) is not None
                if is_api28:
                    mutex = job.get("concurrency", {})
                    if EXPECTED_GLOBAL_MUTEX not in str(mutex):
                        errors.append(f"detected API28 job lacks shared mutex: {path}:{job_name}")
                    if mutex.get("queue") != "max" or mutex.get("cancel-in-progress") is not False:
                        errors.append(f"detected API28 job can replace older pending work: {path}:{job_name}")

    changed = set()
    import subprocess
    for args in (["git", "diff", "--name-only"], ["git", "ls-files", "--others", "--exclude-standard"]):
        result = subprocess.run(args, cwd=ROOT, text=True, capture_output=True, check=False)
        changed.update(line for line in result.stdout.splitlines() if line)
    leaked = sorted(path for path in changed if path in FORBIDDEN_PUBLIC_STATE)
    if leaked:
        errors.append("branch-specific live state must not be promoted to main: " + ", ".join(leaked))

    if errors:
        print("WORK_LIFECYCLE_GOVERNANCE=FAIL")
        for error in errors:
            print(f"- {error}")
        return 1
    print("WORK_LIFECYCLE_GOVERNANCE=PASS")
    print("WORK_LIFECYCLE_AUTHORITIES=4")
    print("API28_AUTOTRIGGERS=NONE_OUTSIDE_CENTRAL_OR_MAIN_DIAGNOSTIC")
    print(f"API28_GLOBAL_MUTEX={EXPECTED_GLOBAL_MUTEX}")
    print("DYNAMIC_STATE_PUBLICATION=PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
