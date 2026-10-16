#!/usr/bin/env python3
"""Validate YuJian's repository-native project master state.

This validator checks state semantics and provenance. It does not replace
product, Android, API28, visual, or end-to-end tests.
"""

from __future__ import annotations

import argparse
import hashlib
import os
import re
import subprocess
import sys
from pathlib import Path

try:
    import yaml
except ImportError as exc:
    print("ERROR: PyYAML is required: python -m pip install PyYAML", file=sys.stderr)
    raise SystemExit(2) from exc


ROOT = Path(__file__).resolve().parents[1]
STATE_PATH = ROOT / "PROJECT_MASTER_STATE.yaml"

RE_SHA40 = re.compile(r"^[0-9a-f]{40}$")
RE_SHA256 = re.compile(r"^[0-9a-f]{64}$")

REQUIRED_TOP_LEVEL = {
    "schema_version",
    "state_id",
    "project",
    "state_control",
    "source_of_truth",
    "repositories",
    "pull_requests",
    "phases",
    "recognition_main_journey",
    "runtime_gates",
    "apk_and_evidence",
    "current_work",
    "blockers",
    "protected_invariants",
    "next_actions",
    "completion_rules",
    "state_update",
    "agent_recovery",
}

REQUIRED_PHASES = [f"P{i}" for i in range(10)]
ALLOWED_STATUSES = {
    "PASS", "FAIL", "BLOCKED", "NOT_RUN", "NOT_CONFIRMED", "IN_PROGRESS",
    "READY", "HISTORICAL", "NOT_STARTED", "PARTIAL", "FROZEN", "DEFINED",
    "PENDING", "OPEN", "WAITING", "PARALLEL", "FINAL_GATE",
    "NOT_FULLY_CLOSED", "NOT_ACCEPTED", "NOT_CLOSED", "NOT_AVAILABLE",
    "GENERATED", "SKIPPED", "READY_FOR_WORK", "FIX_READY_FOR_EXACT_HEAD_CI",
    "PROTECTED", "UNCHANGED", "APPROVED", "ACTIVE", "AVAILABLE", "CURRENT",
    "DIAGNOSTIC_ONLY", "HISTORICAL_ONLY", "NOT_VERIFIED_FROM_BINARY",
    "DRAFT_OPEN_UNMERGED", "READY_FOR_IMPLEMENTATION",
}


class UniqueKeyLoader(yaml.SafeLoader):
    """PyYAML loader that rejects duplicate mapping keys."""


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


class Validation:
    def __init__(self) -> None:
        self.errors: list[str] = []
        self.warnings: list[str] = []

    def error(self, message: str) -> None:
        self.errors.append(message)

    def warning(self, message: str) -> None:
        self.warnings.append(message)

    def require(self, condition: bool, message: str) -> None:
        if not condition:
            self.error(message)


def git_output(*args: str) -> str | None:
    try:
        return subprocess.check_output(
            ["git", *args],
            cwd=ROOT,
            text=True,
            stderr=subprocess.DEVNULL,
        ).strip()
    except (OSError, subprocess.CalledProcessError):
        return None


def is_ancestor(ancestor: str, descendant: str) -> bool | None:
    try:
        subprocess.check_call(
            ["git", "merge-base", "--is-ancestor", ancestor, descendant],
            cwd=ROOT,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        return True
    except subprocess.CalledProcessError as exc:
        return False if exc.returncode == 1 else None
    except OSError:
        return None


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def validate_state(state: dict, v: Validation) -> None:
    v.require(state.get("schema_version") == "1.0", "schema_version must be 1.0")
    missing = REQUIRED_TOP_LEVEL - set(state)
    v.require(not missing, f"missing top-level keys: {sorted(missing)}")

    control = state.get("state_control", {})
    current_head = control.get("execution_baseline_sha") or control.get("current_head")
    v.require(isinstance(current_head, str) and RE_SHA40.fullmatch(current_head), "state_control.execution_baseline_sha must be a 40-character SHA")
    v.require(bool(control.get("current_branch")), "state_control.current_branch is required")
    if control.get("base_sha"):
        v.require(RE_SHA40.fullmatch(str(control["base_sha"])), "state_control.base_sha must be a 40-character SHA")

    current_pr = control.get("current_pr")
    if current_pr is not None:
        v.require(isinstance(current_pr, int) and current_pr > 0, "state_control.current_pr must be positive")
        pr = state.get("pull_requests", {}).get(f"PR{current_pr}", {})
        v.require(pr.get("number") == current_pr, "current PR snapshot missing")
        v.require(pr.get("branch") == control.get("current_branch"), "current PR branch drift")
        v.require(pr.get("base_sha") == control.get("base_sha"), "current PR base SHA drift")

    phases = state.get("phases", {})
    for phase in REQUIRED_PHASES:
        v.require(phase in phases, f"missing phase {phase}")
        if phase in phases:
            v.require(phases[phase].get("status") in ALLOWED_STATUSES, f"{phase}.status unsupported")

    journey = state.get("recognition_main_journey", {})
    route = journey.get("route", [])
    ids = [x.split(" ", 1)[0] for x in route if isinstance(x, str)]
    v.require(ids == [f"J{i:02d}" for i in range(1, 21)], "recognition route must contain J01-J20 in order")
    v.require(journey.get("contract_status") == "FROZEN", "recognition_main_journey.contract_status must remain FROZEN")

    runtime = state.get("runtime_gates", {})
    exact = runtime.get("exact_head", {})
    v.require(exact.get("sha") == current_head, "runtime exact-head SHA drift")

    work = state.get("current_work", {})
    v.require(work.get("exact_head") == current_head, "current_work.exact_head drift")
    v.require(bool(work.get("id")), "current_work.id is required")
    v.require(bool(work.get("phase")), "current_work.phase is required")
    v.require(bool(work.get("task_id")), "current_work.task_id is required")

    source = state.get("source_of_truth", {})
    work_path = source.get("current_work_state")
    v.require(bool(work_path), "source_of_truth.current_work_state is required")
    if work_path:
        v.require((ROOT / work_path).exists(), f"current Work state missing: {ROOT / work_path}")

    blockers = state.get("blockers", [])
    blocker_ids = [x.get("id") for x in blockers if isinstance(x, dict)]
    v.require(len(blocker_ids) == len(set(blocker_ids)), "blocker IDs must be unique")

    actions = state.get("next_actions", [])
    action_ids = [x.get("id") for x in actions if isinstance(x, dict)]
    v.require(len(action_ids) == len(set(action_ids)), "next_action IDs must be unique")
    v.require(any(isinstance(a, dict) and a.get("status") == "READY" for a in actions), "at least one next_action must be READY")

    invariants = state.get("protected_invariants", {})
    v.require(bool(invariants) and all(isinstance(x, list) and x for x in invariants.values()), "protected_invariants groups must be non-empty")

    historical = runtime.get("historical_runtime_run", {})
    v.require(historical.get("rule") == "Historical results cannot promote current exact HEAD to PASS.", "historical runtime protection rule missing")

    env_branch = os.environ.get("GITHUB_HEAD_REF") or os.environ.get("GITHUB_REF_NAME")
    if env_branch and control.get("current_branch") and env_branch not in {control["current_branch"], "HEAD"}:
        v.error(f"workflow branch {env_branch!r} != state branch {control['current_branch']!r}")

    git_head = git_output("rev-parse", "HEAD")
    if git_head:
        relation = is_ancestor(current_head, git_head)
        if relation is False:
            v.error(f"tracked execution baseline is not an ancestor of repository HEAD: {current_head} -> {git_head}")
        elif relation is None:
            v.warning("could not verify Git ancestry")

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--state",
        type=Path,
        default=STATE_PATH,
        help="Path to PROJECT_MASTER_STATE.yaml",
    )
    args = parser.parse_args()

    path = args.state.resolve()
    if not path.exists():
        print(f"ERROR: state file not found: {path}", file=sys.stderr)
        return 2

    raw = path.read_text(encoding="utf-8")
    if "\t" in raw:
        print("ERROR: tabs are not allowed in YAML indentation", file=sys.stderr)
        return 2

    try:
        state = yaml.load(raw, Loader=UniqueKeyLoader)
    except yaml.YAMLError as exc:
        print(f"ERROR: YAML parse failed: {exc}", file=sys.stderr)
        return 2

    if not isinstance(state, dict):
        print("ERROR: root YAML value must be a mapping", file=sys.stderr)
        return 2

    v = Validation()
    validate_state(state, v)

    for warning in v.warnings:
        print(f"WARNING: {warning}")

    if v.errors:
        for error in v.errors:
            print(f"ERROR: {error}", file=sys.stderr)
        print(
            f"PROJECT_MASTER_STATE_VALIDATION=FAIL ({len(v.errors)} errors)",
            file=sys.stderr,
        )
        return 1

    print("PROJECT_MASTER_STATE_VALIDATION=PASS")
    print(f"state_file={path}")
    print(f"tracked_execution_baseline={state['state_control']['execution_baseline_sha']}")
    print(f"current_pr=PR#{state['state_control'].get('current_pr')}")
    print(f"open_blockers={len(state['blockers'])}")
    print(f"next_actions={len(state['next_actions'])}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
