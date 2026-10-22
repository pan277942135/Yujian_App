#!/usr/bin/env python3
"""Apply the V1.1 candidate admission policy to current labeled PR metadata."""
from __future__ import annotations

import argparse
import json
from pathlib import Path

import yaml

try:
    from .work_lifecycle import candidate_eligibility
except ImportError:  # direct script execution
    from work_lifecycle import candidate_eligibility

ROOT = Path(__file__).resolve().parents[1]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--current-output", type=Path, required=True)
    parser.add_argument("--eligible-output", type=Path, required=True)
    parser.add_argument("--rejected-output", type=Path, required=True)
    args = parser.parse_args()
    raw = json.loads(args.input.read_text(encoding="utf-8"))
    if not isinstance(raw, list):
        raise SystemExit("candidate metadata must be an array")
    policy = yaml.safe_load((ROOT / "harness/ANDROID_BATCH_ACCEPTANCE_POLICY_V1_1.yaml").read_text(encoding="utf-8"))
    supported = set(policy["core_gates"]) | set(policy["full_gates"])
    required_checks = set(policy["candidate"]["required_quick_checks"])
    current: list[dict] = []
    eligible: list[dict] = []
    rejected: list[dict] = []
    for candidate in raw:
        if not isinstance(candidate, dict):
            continue
        ok, reason = candidate_eligibility(
            candidate,
            approval_label=policy["candidate"]["approval_label"],
            allowed_associations=set(policy["candidate"]["allowed_author_associations"]),
            quick_checks=required_checks,
            supported_gates=supported,
        )
        if ok:
            candidate["initial_status"] = "QUEUED"
            candidate["reason"] = None
            eligible.append(candidate)
        else:
            candidate["initial_status"] = (
                "BLOCKED_QUICK_CHECKS" if reason and reason.startswith("QUICK_CHECKS_NOT_PASS")
                else "BLOCKED_PERMISSION_VERIFICATION" if reason and reason.startswith("BLOCKED_PERMISSION_VERIFICATION")
                else "BLOCKED_ADMISSION"
            )
            candidate["reason"] = reason
            rejected.append({
                "pr_number": candidate.get("pr_number"),
                "head_sha": candidate.get("head_sha"),
                "task_id": candidate.get("task_id"),
                "required_gates": candidate.get("required_gates", []),
                "status": candidate["initial_status"],
                "reason": reason,
            })
        current.append(candidate)
    for path, value in ((args.current_output, current), (args.eligible_output, eligible), (args.rejected_output, rejected)):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"API28_CANDIDATES_LABELLED={len(current)}")
    print(f"API28_CANDIDATES_ELIGIBLE={len(eligible)}")
    print(f"API28_CANDIDATES_BLOCKED={len(rejected)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
