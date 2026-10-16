#!/usr/bin/env python3
"""Validate YuJian Phase / Task State Machine V1.

The validator verifies that the execution controller is structurally consistent
with PROJECT_MASTER_STATE and FEATURE_MATRIX. It does not promote product,
runtime, visual, or release status; those require their own evidence gates.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

try:
    import yaml
except ImportError as exc:
    print("ERROR: PyYAML is required: python -m pip install PyYAML", file=sys.stderr)
    raise SystemExit(2) from exc

ROOT = Path(__file__).resolve().parents[1]
MACHINE_PATH = ROOT / "harness/PHASE_TASK_STATE_MACHINE.yaml"
STATE_PATH = ROOT / "PROJECT_MASTER_STATE.yaml"
MATRIX_PATH = ROOT / "harness/FEATURE_MATRIX.yaml"

PHASES = [f"P{i}" for i in range(10)]
JOURNEYS = [f"J{i:02d}" for i in range(1, 21)]
QUEUE = [f"N{i:03d}" for i in range(1, 11)]
EXPECTED_QUEUE_STATUS = {
    "N001": "READY",
    "N002": "WAITING_FOR_N001",
    "N003": "WAITING_FOR_N001",
    "N004": "PARALLEL",
    "N005": "WAITING",
    "N006": "WAITING",
    "N007": "WAITING",
    "N008": "WAITING",
    "N009": "FINAL_GATE",
    "N010": "BLOCKED_BY_B008",
}
PHASE_NAMES = {
    "P0": "Product Goal / Decisions",
    "P1": "Product Master Flow / State Model",
    "P2": "Product Specifications / Business Contracts",
    "P3": "Design System / Visual Contracts",
    "P4": "Architecture / API / Data Contracts",
    "P5": "Engineering Foundation / CI / Test Harness",
    "P6": "Feature Implementation",
    "P7": "Integration / Full Surface",
    "P8": "QA / Runtime / Evidence",
    "P9": "Release / APK Delivery",
}
TASK_STATES = {
    "READY", "WAITING", "RUNNING", "VALIDATING", "FAIL", "RECOVERING",
    "BLOCKED", "PASS", "CLOSED", "FINAL_GATE", "CANCELLED", "IN_PROGRESS",
}


class UniqueKeyLoader(yaml.SafeLoader):
    pass


def _construct_mapping(loader, node, deep=False):
    mapping = {}
    for key_node, value_node in node.value:
        key = loader.construct_object(key_node, deep=deep)
        if key in mapping:
            raise yaml.constructor.ConstructorError(
                "while constructing a mapping",
                node.start_mark,
                f"duplicate key: {key!r}",
                key_node.start_mark,
            )
        mapping[key] = loader.construct_object(value_node, deep=deep)
    return mapping


UniqueKeyLoader.add_constructor(
    yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG,
    _construct_mapping,
)


def load_yaml(path: Path) -> dict:
    raw = path.read_text(encoding="utf-8")
    if "\t" in raw:
        raise ValueError(f"tabs are not allowed in YAML indentation: {path}")
    value = yaml.load(raw, Loader=UniqueKeyLoader)
    if not isinstance(value, dict):
        raise ValueError(f"root YAML value must be a mapping: {path}")
    return value


class Validation:
    def __init__(self) -> None:
        self.errors: list[str] = []

    def error(self, message: str) -> None:
        self.errors.append(message)

    def require(self, condition: bool, message: str) -> None:
        if not condition:
            self.error(message)


def expand_journeys(refs: list[str]) -> set[str]:
    result: set[str] = set()
    import re
    for ref in refs or []:
        if not isinstance(ref, str):
            continue
        match = re.fullmatch(r"J(\\d{2})(?:-J(\\d{2}))?", ref)
        if not match:
            continue
        start = int(match.group(1))
        end = int(match.group(2) or match.group(1))
        if 1 <= start <= end <= 20:
            result.update(f"J{i:02d}" for i in range(start, end + 1))
    return result


def validate_phase_names_and_statuses(machine: dict, state: dict, v: Validation) -> None:
    phases = machine.get("phases", {})
    master_phases = state.get("phases", {})
    v.require(list(phases.keys()) == PHASES, "machine phases must be exactly P0-P9 in order")
    for phase in PHASES:
        item = phases.get(phase, {})
        master = master_phases.get(phase, {})
        v.require(item.get("name") == PHASE_NAMES[phase], f"{phase}: phase name drift")
        v.require(item.get("status") == master.get("status"), f"{phase}: status drift from PROJECT_MASTER_STATE")
        task_refs = item.get("task_refs", [])
        v.require(task_refs, f"{phase}: task_refs must not be empty")


def validate_tasks(machine: dict, state: dict, matrix: dict, v: Validation) -> None:
    tasks = machine.get("tasks", {})
    features = matrix.get("features", {})
    feature_rollup = machine.get("feature_closure_rollup", {})
    blockers = {
        x.get("id") for x in state.get("blockers", [])
        if isinstance(x, dict) and x.get("id")
    }
    next_actions = {
        x.get("id") for x in state.get("next_actions", [])
        if isinstance(x, dict) and x.get("id")
    }

    required_keys = {
        "phase", "priority", "owner", "status", "queue_status",
        "feature_refs", "journey_refs", "depends_on", "blockers",
        "entry_blockers", "target_blockers", "next_action_refs", "evidence_required", "completion",
        "human_required", "retry_count", "retry_budget",
    }

    for task_id, task in tasks.items():
        missing = required_keys - set(task)
        v.require(not missing, f"{task_id}: missing keys {sorted(missing)}")
        if missing:
            continue

        phase = task.get("phase")
        v.require(phase in PHASES, f"{task_id}: unknown phase {phase!r}")
        v.require(task.get("status") in TASK_STATES, f"{task_id}: invalid status {task.get('status')!r}")
        v.require(isinstance(task.get("retry_count"), int), f"{task_id}: retry_count must be integer")
        v.require(isinstance(task.get("retry_budget"), int), f"{task_id}: retry_budget must be integer")
        v.require(0 <= task.get("retry_count", -1) <= 2, f"{task_id}: retry_count must be 0..2")
        v.require(0 <= task.get("retry_budget", -1) <= 2, f"{task_id}: retry_budget must be 0..2")

        for dep in task.get("depends_on", []) or []:
            v.require(dep in tasks, f"{task_id}: unknown dependency {dep}")
        for fid in task.get("feature_refs", []) or []:
            v.require(fid in features, f"{task_id}: unknown feature {fid}")
        for bid in task.get("blockers", []) or []:
            v.require(bid in blockers, f"{task_id}: unknown blocker {bid}")
        entry = set(task.get("entry_blockers", []) or [])
        target = set(task.get("target_blockers", []) or [])
        v.require(entry.issubset(blockers), f"{task_id}: unknown entry_blocker")
        v.require(target.issubset(blockers), f"{task_id}: unknown target_blocker")
        v.require(entry.isdisjoint(target) or task_id in {"P3-03", "P6-01", "N010"},
                  f"{task_id}: entry/target blocker overlap is only allowed for B008 asset tasks")
        v.require(set(task.get("blockers", []) or []) == entry | target,
                  f"{task_id}: blockers must equal entry_blockers union target_blockers")
        if not entry and target and task.get("status") == "BLOCKED":
            v.error(f"{task_id}: target-only blocker cannot force BLOCKED")
        for aid in task.get("next_action_refs", []) or []:
            v.require(aid in next_actions, f"{task_id}: unknown next_action {aid}")

        if task_id.startswith("N"):
            v.require(task_id in QUEUE, f"{task_id}: task is outside N001-N010 queue")
            v.require(
                task.get("queue_status") == EXPECTED_QUEUE_STATUS[task_id],
                f"{task_id}: queue_status must remain {EXPECTED_QUEUE_STATUS[task_id]!r}",
            )

    v.require(set(tasks) >= set(QUEUE), "tasks must contain all N001-N010")

    # Dependency cycle detection.
    visiting: set[str] = set()
    visited: set[str] = set()

    def dfs(task_id: str) -> None:
        if task_id in visiting:
            v.error(f"{task_id}: dependency cycle detected")
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

    # Exact queue semantics.
    for task_id in QUEUE:
        task = tasks[task_id]
        deps = set(task.get("depends_on", []) or [])
        if task_id in {"N002", "N003"}:
            v.require("N001" in deps, f"{task_id}: must depend on N001")
        if task_id == "N004":
            v.require(task.get("queue_status") == "PARALLEL", "N004 must remain PARALLEL")
        if task_id in {"N005", "N006", "N007", "N008"}:
            v.require(task.get("status") == "WAITING", f"{task_id}: current queue state must remain WAITING")
        if task_id == "N009":
            v.require(task.get("status") == "FINAL_GATE", "N009 must remain FINAL_GATE")
            v.require(task.get("human_required") is True, "N009 must require human authorization")
        if task_id == "N010":
            v.require("B008" in set(task.get("blockers", [])), "N010 must remain blocked by B008")

    # Every feature has exactly one rollup.
    matrix_feature_ids = list(features)
    v.require(
        set(feature_rollup) == set(matrix_feature_ids),
        "feature_closure_rollup must cover exactly the 15 Feature Matrix feature IDs",
    )
    for fid, rollup in feature_rollup.items():
        v.require(rollup.get("task_id") in tasks, f"{fid}: rollup task missing")
        v.require(rollup.get("phase") in PHASES, f"{fid}: rollup phase invalid")
        if rollup.get("task_id") in tasks:
            v.require(
                fid in tasks[rollup["task_id"]].get("feature_refs", []),
                f"{fid}: rollup task must reference the feature",
            )

    # Every journey is covered by at least one executable task.
    covered: set[str] = set()
    for task in tasks.values():
        covered.update(expand_journeys(task.get("journey_refs", []) or []))
    v.require(covered == set(JOURNEYS), "task journey coverage must equal J01-J20")

    # Current execution pointer must match master state and the queue.
    control = machine.get("execution_control", {})
    current_work = state.get("current_work", {})
    v.require(
        control.get("current_task") == "N001",
        "execution_control.current_task must be N001",
    )
    v.require(
        control.get("current_phase") == "P8",
        "execution_control.current_phase must be P8",
    )
    v.require(
        control.get("current_work_id") == current_work.get("id"),
        "execution_control.current_work_id must match PROJECT_MASTER_STATE.current_work.id",
    )
    v.require(
        control.get("current_work_stage") == current_work.get("stage"),
        "execution_control.current_work_stage must match PROJECT_MASTER_STATE.current_work.stage",
    )
    v.require(
        control.get("current_execution_baseline_sha") == state.get("state_control", {}).get("execution_baseline_sha"),
        "execution_control baseline SHA must match PROJECT_MASTER_STATE",
    )

    # Retry and safety controls.
    retry = machine.get("retry_policy", {})
    v.require(retry.get("automatic_repairs") == 2, "automatic repair budget must be 2")
    v.require(retry.get("maximum_attempts") == 3, "maximum attempts must be 3")
    v.require(retry.get("no_unbounded_retry") is True, "unbounded retry must be forbidden")
    safety = machine.get("execution_control", {})
    for key in ("main_merge_allowed", "force_push_allowed", "rebase_allowed", "history_rewrite_allowed"):
        v.require(safety.get(key) is False, f"{key} must remain false")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--machine", type=Path, default=MACHINE_PATH)
    args = parser.parse_args()

    machine_path = args.machine.resolve()
    for required in (machine_path, STATE_PATH, MATRIX_PATH):
        if not required.exists():
            print(f"ERROR: required file missing: {required}", file=sys.stderr)
            return 2

    try:
        machine = load_yaml(machine_path)
        state = load_yaml(STATE_PATH)
        matrix = load_yaml(MATRIX_PATH)
    except (OSError, ValueError, yaml.YAMLError) as exc:
        print(f"ERROR: source parse failed: {exc}", file=sys.stderr)
        return 2

    v = Validation()
    v.require(machine.get("schema_version") == "1.0", "schema_version must be 1.0")
    v.require(machine.get("machine_id") == "yujian-phase-task-state-machine", "machine_id mismatch")
    validate_phase_names_and_statuses(machine, state, v)
    validate_tasks(machine, state, matrix, v)

    if v.errors:
        print("PHASE_TASK_STATE_MACHINE_VALIDATION=FAIL")
        print(f"structural_errors={len(v.errors)}")
        for error in v.errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    print("PHASE_TASK_STATE_MACHINE_VALIDATION=PASS")
    print("phase_count=10")
    print("task_count="+str(len(machine.get("tasks", {}))))
    print("feature_rollup_count="+str(len(machine.get("feature_closure_rollup", {}))))
    print("journey_count=20")
    print("queue=N001-N010")
    print("current_phase=P8")
    print("current_task=N001")
    print("automatic_repairs=2")
    print("maximum_attempts=3")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
