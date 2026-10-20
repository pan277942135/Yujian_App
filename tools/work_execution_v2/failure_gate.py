#!/usr/bin/env python3
"""Fail-closed execution guard backed by the existing Work execution state.

This is an opt-in pilot. It cannot bypass the Control Plane admission and never
promotes a runtime/release gate to PASS. State must stay in an approved storage
location: do not publish sensitive dynamic Work state to a public repository.
"""
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import tempfile

from work_control import MAX_ATTEMPTS, REQUIRED, failure_id


def _load(path):
    path = Path(path)
    if not path.is_file():
        raise ValueError("WORK_EXECUTION_STATE_MISSING: " + str(path))
    state = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(state, dict) or state.get("schema_version") != "1.0":
        raise ValueError("WORK_EXECUTION_STATE_INVALID_SCHEMA")
    for name in ("task_id", "source_sha", "status", "next_action"):
        if not isinstance(state.get(name), str) or not state[name].strip():
            raise ValueError("WORK_EXECUTION_STATE_MISSING_" + name.upper())
    if not isinstance(state.get("execution_v2", {}), dict):
        raise ValueError("INVALID_EXECUTION_V2")
    return path, state


def _check_event(state, event):
    for field in REQUIRED:
        if not isinstance(event.get(field), str) or not event[field].strip():
            raise ValueError("MISSING_EVENT_FIELD_" + field.upper())
    if event["task_id"] != state["task_id"]:
        raise ValueError("TASK_MISMATCH")
    if event["source_sha"] != state["source_sha"]:
        raise ValueError("STALE_SOURCE_SHA")
    if state["status"] in {"PASS", "CLOSED", "CANCELLED", "FINAL_GATE"}:
        raise ValueError("NOT_AN_ACTIVE_WORK_STATE")


def decision(state, event):
    _check_event(state, event)
    issues = state.get("execution_v2", {}).get("issues", {})
    issue = issues.get(failure_id(event))
    if issue is None:
        return {"allowed": True, "decision": "ALLOW_FIRST_ATTEMPT", "failed_attempts": 0}
    if issue.get("closed"):
        return {"allowed": False, "decision": "ISSUE_CLOSED", "failed_attempts": len(issue.get("failures", []))}
    failures = issue.get("failures", [])
    count = len(failures)
    if count >= MAX_ATTEMPTS:
        return {"allowed": False, "decision": "STOP_AND_ESCALATE", "failed_attempts": count}
    if count >= 2 and failures[-1]["hypothesis"].strip().lower() == event["hypothesis"].strip().lower():
        return {"allowed": False, "decision": "NEW_HYPOTHESIS_REQUIRED", "failed_attempts": count}
    return {"allowed": True, "decision": "ALLOW_REPAIR", "failed_attempts": count}


def _atomic_json(path, state):
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temp = tempfile.mkstemp(prefix=".work-v2-", dir=str(path.parent))
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as out:
            json.dump(state, out, ensure_ascii=False, sort_keys=True, indent=2)
            out.write("\n")
            out.flush()
            os.fsync(out.fileno())
        os.replace(temp, path)
    finally:
        if os.path.exists(temp):
            os.unlink(temp)


def record(state_path, event):
    path, state = _load(state_path)
    _check_event(state, event)
    if event.get("result") not in {"FAIL", "FIX_VERIFIED"}:
        raise ValueError("INVALID_RESULT")
    container = state.setdefault("execution_v2", {"schema_version": "1.0", "issues": {}})
    issues = container.setdefault("issues", {})
    key = failure_id(event)
    issue = issues.setdefault(key, {"task_id": event["task_id"], "gate_id": event["gate_id"], "symptom_key": event["symptom_key"], "failures": [], "closed": False})
    failures = issue["failures"]
    if issue["closed"]:
        raise ValueError("ISSUE_ALREADY_CLOSED")
    if event["result"] == "FIX_VERIFIED":
        # This is not the authoritative Evidence Gate PASS.
        if not failures:
            raise ValueError("NO_FAILURE_TO_VERIFY")
        issue["closed"] = True
        issue["fix_evidence"] = {"source_sha": event["source_sha"], "evidence": event["evidence"]}
        action = "FIX_RECORDED_NOT_GATE_PASS"
    else:
        # Re-observing a failed SHA must not inflate the correction budget.
        if any(f["source_sha"] == event["source_sha"] for f in failures):
            return {"failure_id": key, "decision": "DUPLICATE_SHA_OBSERVATION", "failed_attempts": len(failures), "allowed": len(failures) < MAX_ATTEMPTS}
        verdict = decision(state, event)
        if not verdict["allowed"]:
            raise ValueError(verdict["decision"])
        failures.append({"source_sha": event["source_sha"], "hypothesis": event["hypothesis"], "evidence": event["evidence"]})
        count = len(failures)
        action = ("LOCALIZE_AND_REPRODUCE", "RETHINK_ROOT_CAUSE", "STOP_AND_ESCALATE")[count - 1]
        if count >= MAX_ATTEMPTS:
            state["status"] = "BLOCKED"
            state["next_action"] = "Stop same-failure repairs; escalate " + key + " with recorded evidence and a new root-cause plan."
            state["checkpoint_updated_at"] = datetime.now(timezone.utc).isoformat()
    _atomic_json(path, state)
    return {"failure_id": key, "decision": action, "failed_attempts": len(failures), "allowed": action != "STOP_AND_ESCALATE", "state_path": str(path)}



def validate_recorded_failures(state):
    """Return durable-state ledger errors; callable by the existing validator."""
    root = state.get("execution_v2")
    if root is None:
        return []
    if not isinstance(root, dict) or root.get("schema_version") != "1.0":
        return ["invalid execution_v2 schema"]
    issues = root.get("issues")
    if not isinstance(issues, dict):
        return ["execution_v2.issues must be an object"]
    errors = []
    for key, issue in issues.items():
        if not isinstance(issue, dict):
            errors.append("invalid failure issue: " + str(key))
            continue
        try:
            fingerprint = failure_id(issue)
        except (KeyError, TypeError, ValueError):
            errors.append("invalid failure issue identity: " + str(key))
            continue
        if key != fingerprint:
            errors.append("failure ID does not match issue identity: " + str(key))
        failures = issue.get("failures")
        if not isinstance(failures, list) or len(failures) > MAX_ATTEMPTS:
            errors.append("invalid failure retry budget: " + str(key))
            continue
        sources = set()
        for item in failures:
            if not isinstance(item, dict) or any(not isinstance(item.get(k), str) or not item[k].strip() for k in ("source_sha", "hypothesis", "evidence")):
                errors.append("invalid failure evidence: " + str(key))
                continue
            if item["source_sha"] in sources:
                errors.append("duplicate failed source SHA: " + str(key))
            sources.add(item["source_sha"])
        if len(failures) >= MAX_ATTEMPTS and not issue.get("closed", False) and state.get("status") != "BLOCKED":
            errors.append("exhausted failure must be BLOCKED: " + str(key))
    return errors


def main():
    parser = argparse.ArgumentParser(description="Work V2 failure guard for existing branch-local state")
    parser.add_argument("operation", choices=("check", "record"))
    parser.add_argument("--state", required=True, help="Approved existing WORK_EXECUTION_STATE.json")
    parser.add_argument("--event", required=True, help="JSON failure event with current source SHA")
    args = parser.parse_args()
    try:
        payload = json.loads(Path(args.event).read_text(encoding="utf-8"))
        if args.operation == "check":
            _, state = _load(args.state)
            result = decision(state, payload)
        else:
            result = record(args.state, payload)
        print(json.dumps(result, ensure_ascii=False, sort_keys=True))
        return 0 if result["allowed"] else 3
    except (ValueError, OSError, json.JSONDecodeError) as exc:
        print(json.dumps({"decision": "DENIED", "reason": str(exc)}, ensure_ascii=False))
        return 3


if __name__ == "__main__":
    raise SystemExit(main())
