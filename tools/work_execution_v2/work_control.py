#!/usr/bin/env python3
"""Small, dependency-free pilot for bounded Work execution and routing.

No automatic merges, pushes, deployments, GPU starts, or status promotion.
The failure ledger MUST be stored durably in the owning Work's branch or
approved evidence store; this script never substitutes for the Evidence Gate.
"""
import argparse
import fnmatch
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile

MAX_ATTEMPTS = 3
REQUIRED = ("task_id", "gate_id", "symptom_key", "source_sha", "hypothesis", "evidence")


def failure_id(event):
    # Deliberately excludes source_sha and run_id: fixes must share one budget.
    basis = "|".join(
        re.sub(r"\s+", " ", str(event[k]).strip().lower())
        for k in ("task_id", "gate_id", "symptom_key")
    )
    return hashlib.sha256(basis.encode("utf-8")).hexdigest()[:20]


def record_event(path, event):
    for field in REQUIRED:
        if not isinstance(event.get(field), str) or not event[field].strip():
            raise ValueError("missing required field: " + field)
    if event.get("result") not in ("FAIL", "FIX_VERIFIED"):
        raise ValueError("result must be FAIL or FIX_VERIFIED")
    path = Path(path)
    ledger = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {"schema": 1, "issues": {}}
    if ledger.get("schema") != 1 or not isinstance(ledger.get("issues"), dict):
        raise ValueError("unsupported ledger schema")
    key = failure_id(event)
    issue = ledger["issues"].setdefault(key, {"task_id": event["task_id"], "gate_id": event["gate_id"], "symptom_key": event["symptom_key"], "events": []})
    if issue.get("closed"):
        raise ValueError("issue already closed; reopen requires explicit new diagnosis")
    previous = [e for e in issue["events"] if e["result"] == "FAIL"]
    if event["result"] == "FAIL":
        attempt = len(previous) + 1
        if attempt == 1:
            action = "LOCALIZE_AND_REPRODUCE"
        elif attempt == 2:
            action = "RETHINK_ROOT_CAUSE"
        else:
            action = "STOP_AND_ESCALATE"
        if attempt >= 2 and previous[-1]["hypothesis"] == event["hypothesis"]:
            action = "RETHINK_ROOT_CAUSE" if attempt == 2 else "STOP_AND_ESCALATE"
        issue["events"].append({**event, "attempt": attempt, "decision": action})
    else:
        action = "FIX_EVIDENCE_RECORDED_NOT_GATE_PASS"
        issue["closed"] = True
        issue["events"].append({**event, "attempt": len(previous), "decision": action})
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temp_name = tempfile.mkstemp(prefix=".failure-ledger-", dir=str(path.parent))
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            json.dump(ledger, f, ensure_ascii=False, indent=2, sort_keys=True)
            f.write("\n")
        os.replace(temp_name, path)
    finally:
        if os.path.exists(temp_name):
            os.unlink(temp_name)
    return {"failure_id": key, "failed_attempts": len(previous) + (event["result"] == "FAIL"), "decision": action, "ledger": str(path)}


def extract_pr_paths(workflow_path):
    """Read only the subset of Actions YAML required for pull_request.paths."""
    lines = Path(workflow_path).read_text(encoding="utf-8").splitlines()
    in_pr = False
    in_paths = False
    result = []
    for line in lines:
        if line == "  pull_request:":
            in_pr, in_paths = True, False
            continue
        if in_pr and re.match(r"^  [A-Za-z_][\w-]*:", line):
            break
        if not in_pr:
            continue
        if line == "    paths:":
            in_paths = True
            continue
        if in_paths and re.match(r"^    [A-Za-z_][\w-]*:", line):
            break
        if in_paths:
            match = re.match(r"^      - ['\"]?([^'\"]+)['\"]?\s*$", line)
            if match:
                result.append(match.group(1))
    if not result:
        raise ValueError("No explicit pull_request.paths found in " + str(workflow_path))
    return result


def route_files(root, changed):
    root = Path(root)
    paths = [x.strip().replace("\\", "/") for x in changed if x.strip()]
    def matches(workflow):
        patterns = extract_pr_paths(root / ".github" / "workflows" / workflow)
        return any(fnmatch.fnmatchcase(p, pat) for p in paths for pat in patterns)
    optical = any(
        p.startswith("design/manager/optical-lab/")
        or p.startswith("design/manager/nh01_acceptance/edgeglow-fish-static/")
        or p.startswith("scripts/capture_optical_lab_")
        for p in paths
    )
    return {
        "changed_paths": paths,
        "android_ci": matches("android.yml"),
        "nh01_runtime": matches("nh01-cloud-runtime.yml"),
        "optical_browser": optical,
        "warning": "Decision preview only; GitHub event filters remain authoritative."
    }


def preflight(profile):
    def present(name):
        return bool(shutil.which(name))
    if profile == "android":
        checks = {"java": present("java"), "adb": present("adb"), "gradle": present("gradle"), "android_sdk": bool(os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT"))}
        status = "LOCAL_CAPABLE" if all(checks.values()) else "ROUTE_TO_ANDROID_CI"
    elif profile == "optical":
        checks = {"node": present("node"), "browser": any(present(x) for x in ("chromium", "chromium-browser", "google-chrome", "chrome"))}
        status = "LOCAL_CAPABLE" if all(checks.values()) else "ROUTE_TO_BROWSER_CI"
    else:
        checks = {"gcloud": present("gcloud"), "gcloud_authenticated": False}
        if checks["gcloud"]:
            try:
                r = subprocess.run(
                    ["gcloud", "auth", "list", "--filter=status:ACTIVE", "--format=value(account)"],
                    capture_output=True, text=True, timeout=8, check=False,
                )
                checks["gcloud_authenticated"] = r.returncode == 0 and bool(r.stdout.strip())
            except (subprocess.SubprocessError, OSError):
                pass
        status = "DATA_ACCESS_PROBE_REQUIRED" if all(checks.values()) else "ROUTE_TO_AUTHORIZED_CLOUD_SHELL"
    return {"profile": profile, "checks": checks, "decision": status, "note": "Capabilities do not prove runtime PASS or authorize resource creation."}


def main(argv=None):
    ap = argparse.ArgumentParser(description="YuJian Work execution V2 pilot")
    sub = ap.add_subparsers(dest="cmd", required=True)
    p = sub.add_parser("failure")
    p.add_argument("--ledger", required=True)
    p.add_argument("--event", required=True, help="Path to JSON failure event")
    p = sub.add_parser("route")
    p.add_argument("--repo-root", default=".")
    p.add_argument("--changed-file", required=True, help="newline-delimited changed paths")
    p = sub.add_parser("preflight")
    p.add_argument("--profile", choices=("android", "optical", "detector"), required=True)
    args = ap.parse_args(argv)
    try:
        if args.cmd == "failure":
            result = record_event(args.ledger, json.loads(Path(args.event).read_text(encoding="utf-8")))
        elif args.cmd == "route":
            result = route_files(args.repo_root, Path(args.changed_file).read_text(encoding="utf-8").splitlines())
        else:
            result = preflight(args.profile)
        print(json.dumps(result, ensure_ascii=False, sort_keys=True))
        return 0
    except (ValueError, OSError, json.JSONDecodeError) as exc:
        print("WORK_EXECUTION_V2_ERROR: " + str(exc), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
