#!/usr/bin/env python3
"""Enforce the reviewed M1 C1 file whitelist and protected-source rules."""

from __future__ import annotations

import argparse
import fnmatch
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "docs/qa/recognition_main_journey/M1_SCOPE_MANIFEST.json"
PRIVATE_DYNAMIC_STATE = {"PROJECT_MASTER_STATE.yaml", "WORK_EXECUTION_STATE.json"}


def is_protected(path: str, patterns: list[str]) -> bool:
    normalized = path.replace("\\", "/")
    lowered = normalized.lower()
    if any(fnmatch.fnmatchcase(normalized, pattern) for pattern in patterns):
        return True
    suffix = Path(normalized).suffix.lower()
    is_design_image = normalized.startswith("design/") and suffix in {".png", ".svg"}
    if is_design_image and ("/frozen/" in lowered or "frozen" in Path(normalized).name.lower()):
        return True
    return False


def changed_paths(base_sha: str) -> list[str]:
    merge_base = subprocess.check_output(
        ["git", "merge-base", base_sha, "HEAD"], cwd=ROOT, text=True
    ).strip()
    tracked = subprocess.check_output(
        ["git", "diff", "--name-only", "-z", merge_base, "--"], cwd=ROOT
    )
    paths = {item.decode("utf-8") for item in tracked.split(b"\0") if item}
    untracked = subprocess.check_output(
        ["git", "ls-files", "--others", "--exclude-standard", "-z"], cwd=ROOT
    )
    for item in untracked.split(b"\0"):
        if not item:
            continue
        path = item.decode("utf-8")
        if path in PRIVATE_DYNAMIC_STATE:
            tracked_check = subprocess.run(
                ["git", "ls-files", "--error-unmatch", path],
                cwd=ROOT,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
            if tracked_check.returncode == 0:
                paths.add(path)
            continue
        paths.add(path)
    return sorted(paths)


def validate(base_sha: str) -> tuple[list[str], list[str], list[str]]:
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    if base_sha != manifest["canonical_base_sha"]:
        raise ValueError(
            "PR base SHA changed from the declared canonical baseline; refresh authority and scope before continuing"
        )
    subprocess.run(
        ["git", "cat-file", "-e", f"{base_sha}^{{commit}}"], cwd=ROOT, check=True
    )
    allowed = set(manifest["allowed_paths"])
    paths = changed_paths(base_sha)
    unexpected = [path for path in paths if path not in allowed]
    protected = [
        path
        for path in paths
        if is_protected(path, manifest["protected_patterns"])
    ]
    return paths, unexpected, protected


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-sha", required=True)
    args = parser.parse_args()
    try:
        paths, unexpected, protected = validate(args.base_sha)
    except (OSError, subprocess.CalledProcessError, ValueError, json.JSONDecodeError) as error:
        print(f"M1_SCOPE_BLOCKED: {error}", file=sys.stderr)
        return 2
    print(
        json.dumps(
            {
                "manifest_id": "M1_SCOPE_MANIFEST_V1",
                "changed_path_count": len(paths),
                "unexpected_paths": unexpected,
                "protected_paths": protected,
                "classification": "PASS" if not unexpected and not protected else "FAIL",
            },
            sort_keys=True,
        )
    )
    return 0 if not unexpected and not protected else 1


if __name__ == "__main__":
    raise SystemExit(main())
