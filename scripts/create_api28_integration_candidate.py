#!/usr/bin/env python3
"""Create/push a deterministic, hosted-runner API28 integration source commit."""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import subprocess
import sys

try:
    from .work_lifecycle import integration_plan
except ImportError:  # direct script execution
    from work_lifecycle import integration_plan

ROOT = Path(os.environ.get("YUJIAN_COMPOSER_ROOT", Path(__file__).resolve().parents[1])).resolve()
SHA = re.compile(r"^[0-9a-f]{40}$")


def git(*args: str, env: dict | None = None, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(["git", *args], cwd=ROOT, text=True, capture_output=True, env=env, check=check)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-sha", required=True)
    parser.add_argument("--batch-id", required=True)
    parser.add_argument("--scope", choices=["CORE", "FULL"], required=True)
    parser.add_argument("--candidates", type=Path, required=True)
    parser.add_argument("--scope-gates", required=True, help="comma-separated accepted gates")
    parser.add_argument("--merged-prs", default="", help="comma-separated dependency PRs already on main")
    parser.add_argument("--dry-run", action="store_true", help="compose locally but do not publish a candidate branch")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if not SHA.fullmatch(args.base_sha):
        parser.error("base-sha must be a 40-char SHA")

    candidates = json.loads(args.candidates.read_text(encoding="utf-8"))
    if not isinstance(candidates, list):
        raise SystemExit("candidate input must be an array")
    clean = git("status", "--porcelain").stdout.strip()
    if clean:
        raise SystemExit("integration workspace must be clean")
    scope_gates = set(filter(None, args.scope_gates.split(",")))
    merged_prs = {int(value) for value in args.merged_prs.split(",") if value.strip()}
    plan = integration_plan(candidates, scope_gates=scope_gates, merged_prs=merged_prs)
    if not plan["integrated"]:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps({"batch_id": args.batch_id, "scope": args.scope, "base_sha": args.base_sha, **plan, "integration_sha": None, "tree_sha": None}, indent=2) + "\n", encoding="utf-8")
        return 0

    remote_main = git("rev-parse", "origin/main").stdout.strip()
    if remote_main != args.base_sha:
        raise SystemExit(f"base SHA is stale: expected origin/main {remote_main}, got {args.base_sha}")
    base_time = git("show", "-s", "--format=%cI", args.base_sha).stdout.strip()
    git("checkout", "--detach", args.base_sha)
    included: list[dict] = []
    blocked = list(plan["blocked"])
    blocked_prs = {int(row["pr_number"]) for row in blocked}
    for candidate in plan["integrated"]:
        pr_number = int(candidate["pr_number"])
        sha = candidate["head_sha"]
        if not SHA.fullmatch(sha):
            blocked.append({"pr_number": pr_number, "head_sha": sha, "status": "BLOCKED_STALE_SHA", "reason": "invalid candidate SHA"})
            blocked_prs.add(pr_number)
            continue
        fetched = git("fetch", "--no-tags", "origin", f"refs/pull/{pr_number}/head", check=False)
        if fetched.returncode != 0:
            blocked.append({"pr_number": pr_number, "head_sha": sha, "status": "BLOCKED_STALE_SHA", "reason": "unable to fetch PR head"})
            blocked_prs.add(pr_number)
            continue
        current_sha = git("rev-parse", "FETCH_HEAD").stdout.strip()
        if current_sha != sha:
            blocked.append({"pr_number": pr_number, "head_sha": sha, "status": "BLOCKED_STALE_SHA", "reason": f"live PR head changed to {current_sha}"})
            blocked_prs.add(pr_number)
            continue
        deps = {int(value) for value in next((row.get("depends_on_prs", []) for row in candidates if int(row["pr_number"]) == pr_number), [])}
        if deps & blocked_prs:
            blocked.append({"pr_number": pr_number, "head_sha": sha, "status": "BLOCKED_DEPENDENCY", "depends_on_prs": sorted(deps & blocked_prs)})
            blocked_prs.add(pr_number)
            continue
        merged = git("merge", "--no-commit", "--no-ff", "--no-gpg-sign", sha, check=False)
        if merged.returncode != 0:
            git("merge", "--abort", check=False)
            blocked.append({"pr_number": pr_number, "head_sha": sha, "status": "BLOCKED_CONFLICT", "reason": merged.stderr[-1200:]})
            blocked_prs.add(pr_number)
            continue
        merge_head = git("rev-parse", "--verify", "MERGE_HEAD", check=False)
        staged_changes = git("diff", "--cached", "--quiet", check=False)
        if merge_head.returncode != 0 or staged_changes.returncode == 0:
            if merge_head.returncode == 0:
                git("merge", "--abort", check=False)
            blocked.append({"pr_number": pr_number, "head_sha": sha, "status": "BLOCKED_NO_CHANGE", "reason": "PR head adds no new tree change to the current Integration SHA"})
            blocked_prs.add(pr_number)
            continue
        env = os.environ.copy()
        env.update({
            "GIT_AUTHOR_NAME": "YuJian API28 Batch",
            "GIT_AUTHOR_EMAIL": "actions@users.noreply.github.com",
            "GIT_COMMITTER_NAME": "YuJian API28 Batch",
            "GIT_COMMITTER_EMAIL": "actions@users.noreply.github.com",
            "GIT_AUTHOR_DATE": base_time,
            "GIT_COMMITTER_DATE": base_time,
        })
        git("commit", "--no-gpg-sign", "-m", f"API28 integration: PR #{pr_number} @ {sha}", env=env)
        included.append(candidate)

    if not included:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps({"batch_id": args.batch_id, "scope": args.scope, "base_sha": args.base_sha, "integrated": [], "blocked": blocked, "deferred": plan["deferred"], "integration_sha": None, "tree_sha": None}, indent=2) + "\n", encoding="utf-8")
        return 0

    integration_sha = git("rev-parse", "HEAD").stdout.strip()
    tree_sha = git("rev-parse", "HEAD^{tree}").stdout.strip()
    branch = f"api28-candidate/{args.batch_id}"
    if not args.dry_run:
        pushed = git("push", "origin", f"HEAD:refs/heads/{branch}", check=False)
        if pushed.returncode != 0:
            raise SystemExit("failed to publish Integration SHA branch: " + pushed.stderr[-1200:])
    result = {
        "batch_id": args.batch_id,
        "scope": args.scope,
        "base_sha": args.base_sha,
        "integration_sha": integration_sha,
        "tree_sha": tree_sha,
        "integration_branch": None if args.dry_run else branch,
        "dry_run": args.dry_run,
        "integrated": included,
        "blocked": blocked,
        "deferred": plan["deferred"],
        "integration_identity": "deterministic for fixed base SHA, ordered PR/head SHAs, merge trees, and fixed commit metadata",
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"INTEGRATION_SHA={integration_sha}")
    print(f"INTEGRATION_TREE_SHA={tree_sha}")
    print(f"INTEGRATION_BRANCH={None if args.dry_run else branch}")
    print(f"INTEGRATED_PR_COUNT={len(included)}")
    print(f"BLOCKED_PR_COUNT={len(blocked)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
