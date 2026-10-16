#!/usr/bin/env python3
"""Validate YuJian's canonical static Phase / Task State Machine."""
from __future__ import annotations
import argparse
import re
import sys
from pathlib import Path
try:
    import yaml
except ImportError as exc:
    print("ERROR: PyYAML is required", file=sys.stderr)
    raise SystemExit(2) from exc

ROOT = Path(__file__).resolve().parents[1]
MACHINE_PATH = ROOT / "harness/PHASE_TASK_STATE_MACHINE.yaml"
MATRIX_PATH = ROOT / "harness/FEATURE_MATRIX.yaml"
PHASES = [f"P{i}" for i in range(10)]
JOURNEYS = [f"J{i:02d}" for i in range(1, 21)]
QUEUE = [f"N{i:03d}" for i in range(1, 11)]
PHASE_NAMES = {
    "P0":"Product Goal / Decisions","P1":"Product Master Flow / State Model",
    "P2":"Product Specifications / Business Contracts","P3":"Design System / Visual Contracts",
    "P4":"Architecture / API / Data Contracts","P5":"Engineering Foundation / CI / Test Harness",
    "P6":"Feature Implementation","P7":"Integration / Full Surface",
    "P8":"QA / Runtime / Evidence","P9":"Release / APK Delivery",
}
DYNAMIC_KEYS = {
    "status","queue_status","current_phase","current_task","current_work_id",
    "current_work_stage","current_work_status","current_execution_baseline_sha",
    "current_task_status","retry_count","retry_budget","last_attempt",
    "failure_class","recovery_action","evidence_observed","next_action_refs",
}

def load(path: Path):
    raw = path.read_text(encoding="utf-8")
    if "\t" in raw:
        raise ValueError("tabs are not allowed in YAML")
    value = yaml.safe_load(raw)
    if not isinstance(value, dict):
        raise ValueError("root must be a mapping")
    return value

def expand_journeys(refs):
    result = set()
    for ref in refs or []:
        if not isinstance(ref, str):
            continue
        match = re.fullmatch(r"J(\d{2})(?:-J(\d{2}))?", ref)
        if match:
            start = int(match.group(1))
            end = int(match.group(2) or match.group(1))
            if 1 <= start <= end <= 20:
                result.update(f"J{i:02d}" for i in range(start, end + 1))
    return result

def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--machine", type=Path, default=MACHINE_PATH)
    args = ap.parse_args()
    try:
        machine = load(args.machine.resolve())
        matrix = load(MATRIX_PATH)
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2

    errors = []
    if machine.get("schema_version") != "1.0":
        errors.append("schema_version must be 1.0")
    if machine.get("machine_id") != "yujian-phase-task-state-machine":
        errors.append("machine_id mismatch")

    project = machine.get("project", {})
    if project.get("repository") != "pan277942135/Yujian_App":
        errors.append("project.repository mismatch")
    if project.get("control_branch") != "main":
        errors.append("project.control_branch must be main")
    for key in ("execution_baseline_sha","current_pr","base_branch","base_sha","current_phase","current_task","current_work_id"):
        if key in project:
            errors.append(f"project.{key} is forbidden in static machine")

    phases = machine.get("phases", {})
    if list(phases) != PHASES:
        errors.append("phases must be exactly P0-P9 in order")
    for pid in PHASES:
        item = phases.get(pid, {})
        if item.get("name") != PHASE_NAMES[pid]:
            errors.append(f"{pid}: phase name drift")
        if "status" in item:
            errors.append(f"{pid}: live status must not be stored")
        if not item.get("task_refs"):
            errors.append(f"{pid}: task_refs missing")

    tasks = machine.get("tasks", {})
    features = matrix.get("features", {})
    known_blockers = set(machine.get("known_blocker_ids", []))
    required = {
        "phase","priority","owner","feature_refs","journey_refs","depends_on",
        "entry_blockers","target_blockers","evidence_required",
        "completion","human_required",
    }
    for task_id, task in tasks.items():
        if not isinstance(task, dict):
            errors.append(f"{task_id}: task must be mapping")
            continue
        dynamic = DYNAMIC_KEYS & set(task)
        if dynamic:
            errors.append(f"{task_id}: dynamic keys present: {sorted(dynamic)}")
        missing = required - set(task)
        if missing:
            errors.append(f"{task_id}: missing static keys: {sorted(missing)}")
        if task.get("phase") not in PHASES:
            errors.append(f"{task_id}: invalid phase")
        for dep in task.get("depends_on", []) or []:
            if dep not in tasks:
                errors.append(f"{task_id}: unknown dependency {dep}")
        for fid in task.get("feature_refs", []) or []:
            if fid not in features:
                errors.append(f"{task_id}: unknown feature {fid}")
        for bid in (task.get("entry_blockers", []) or []) + (task.get("target_blockers", []) or []):
            if bid not in known_blockers:
                errors.append(f"{task_id}: unknown blocker {bid}")

    if not set(tasks) >= set(QUEUE):
        errors.append("tasks must contain N001-N010")

    required_dependencies = {
        "N002":{"N001"}, "N003":{"N001","N010"}, "N004":set(),
        "N005":{"N002","N003","N004"}, "N006":{"N005"},
        "N007":{"N002"}, "N008":{"N005","N006","N007"},
        "N009":{"P9-01"}, "N010":{"P3-01"},
    }
    for task_id, deps in required_dependencies.items():
        if task_id in tasks:
            actual = set(tasks[task_id].get("depends_on", []) or [])
            missing = deps - actual
            if missing:
                errors.append(f"{task_id}: missing required dependencies {sorted(missing)}")
    if "N003" in tasks and set(tasks["N003"].get("depends_on", [])) != {"N001","N010"}:
        errors.append("N003 must wait for N001 and N010")

    visiting, visited = set(), set()
    def dfs(task_id):
        if task_id in visiting:
            errors.append(f"{task_id}: dependency cycle detected")
            return
        if task_id in visited:
            return
        visiting.add(task_id)
        for dep in tasks.get(task_id, {}).get("depends_on", []) or []:
            if dep in tasks:
                dfs(dep)
        visiting.remove(task_id)
        visited.add(task_id)
    for task_id in tasks:
        dfs(task_id)

    rollup = machine.get("feature_closure_rollup", {})
    if set(rollup) != set(features):
        errors.append("feature_closure_rollup must cover exactly the Feature Matrix features")
    for fid, item in rollup.items():
        if "status" in item or "blockers" in item:
            errors.append(f"{fid}: rollup contains live state")
        task_id = item.get("task_id")
        if task_id not in tasks:
            errors.append(f"{fid}: rollup task missing")
        elif fid not in tasks[task_id].get("feature_refs", []):
            errors.append(f"{fid}: rollup task does not reference feature")

    covered = set()
    for task in tasks.values():
        covered |= expand_journeys(task.get("journey_refs", []) or [])
    if covered != set(JOURNEYS):
        errors.append("task journey coverage must equal J01-J20")

    queue = machine.get("next_action_queue", {})
    if queue.get("exact_order") != QUEUE:
        errors.append("next_action_queue.exact_order must be N001-N010")
    if "required_queue_status" in queue:
        errors.append("next_action_queue must not store live queue status")
    if "machine_completion" in machine:
        errors.append("machine_completion live summary must not exist")

    policy = machine.get("execution_policy", {})
    if policy.get("auto_recovery_enabled") is not True:\n        errors.append("execution_policy.auto_recovery_enabled must be true")\n    if policy.get("max_automatic_repairs_per_failing_gate") != 2:
        errors.append("execution_policy.automatic_repairs must be 2")
    if policy.get("total_attempts_per_gate") != 3:
        errors.append("execution_policy.maximum_attempts must be 3")
    if policy.get("no_unbounded_retry") is not True:
        errors.append("execution_policy.no_unbounded_retry must be true")
    for key in ("main_merge_allowed","force_push_allowed","rebase_allowed","history_rewrite_allowed"):
        if policy.get(key) is not False:
            errors.append(f"execution_policy.{key} must be false")

    print("PHASE_TASK_STATE_MACHINE_VALIDATION=" + ("FAIL" if errors else "PASS"))
    print(f"phase_count={len(phases)}")
    print(f"task_count={len(tasks)}")
    print("journey_count=20")
    print("queue=N001-N010")
    for error in errors:
        print("- " + error, file=sys.stderr)
    return 1 if errors else 0

if __name__ == "__main__":
    raise SystemExit(main())
