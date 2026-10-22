#!/usr/bin/env python3
"""Open and commit the orphaned durable API28 ledger worktree."""
from __future__ import annotations

import argparse
from pathlib import Path
import subprocess
import sys

BRANCH = "api28-batch-ledger"


def git(root: Path, *args: str, check: bool = True):
    return subprocess.run(["git", *args], cwd=root, text=True, capture_output=True, check=check)


def open_worktree(args) -> int:
    repo = args.repository_root.resolve()
    target = args.ledger_dir.resolve()
    if target.exists():
        raise SystemExit(f"ledger worktree path already exists: {target}")
    target.parent.mkdir(parents=True, exist_ok=True)
    exists = git(repo, "ls-remote", "--exit-code", "origin", f"refs/heads/{BRANCH}", check=False).returncode == 0
    if exists:
        fetched = git(repo, "fetch", "origin", f"+refs/heads/{BRANCH}:refs/remotes/origin/{BRANCH}", check=False)
        if fetched.returncode != 0:
            raise SystemExit("cannot refresh durable API28 ledger branch: " + fetched.stderr[-1000:])
    base = f"origin/{BRANCH}" if exists else "HEAD"
    added = git(repo, "worktree", "add", "--detach", str(target), base, check=False)
    if added.returncode != 0:
        raise SystemExit("cannot create ledger worktree: " + added.stderr[-1000:])
    if exists:
        git(target, "checkout", "-B", BRANCH, f"origin/{BRANCH}")
    else:
        git(target, "checkout", "--orphan", BRANCH)
        git(target, "rm", "-rf", "--ignore-unmatch", ".", check=False)
        clean = git(target, "clean", "-fdx", check=False)
        if clean.returncode != 0:
            raise SystemExit("cannot clear initial ledger worktree: " + clean.stderr[-1000:])
    git(target, "config", "user.name", "YuJian API28 Batch")
    git(target, "config", "user.email", "actions@users.noreply.github.com")
    print(f"LEDGER_WORKTREE={target}")
    print(f"LEDGER_BRANCH={BRANCH}")
    return 0


def commit_worktree(args) -> int:
    path = args.ledger_dir.resolve()
    git(path, "add", "-A")
    staged = git(path, "diff", "--cached", "--quiet", check=False)
    if staged.returncode == 0:
        print("LEDGER_COMMIT=NO_CHANGES")
        return 0
    if staged.returncode != 1:
        raise SystemExit("cannot inspect ledger changes")
    git(path, "commit", "-m", args.message)
    pushed = git(path, "push", "origin", f"HEAD:refs/heads/{BRANCH}", check=False)
    if pushed.returncode != 0:
        raise SystemExit("cannot persist API28 ledger: " + pushed.stderr[-1200:])
    print("LEDGER_COMMIT=PUSHED")
    print("LEDGER_BRANCH=api28-batch-ledger")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)
    opening = sub.add_parser("open")
    opening.add_argument("--repository-root", type=Path, required=True)
    opening.add_argument("--ledger-dir", type=Path, required=True)
    opening.set_defaults(run=open_worktree)
    committing = sub.add_parser("commit")
    committing.add_argument("--ledger-dir", type=Path, required=True)
    committing.add_argument("--message", required=True)
    committing.set_defaults(run=commit_worktree)
    args = parser.parse_args()
    return args.run(args)


if __name__ == "__main__":
    raise SystemExit(main())
