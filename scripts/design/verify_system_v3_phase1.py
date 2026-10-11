#!/usr/bin/env python3
"""Verify the Phase 1 design inventory against its recorded baseline Git objects."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import sys
from pathlib import Path, PurePosixPath
from typing import Any

SHA1_RE = re.compile(r"^[0-9a-f]{40}$")
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")
DEFAULT_REGISTER = "design/system_v3/06_Asset_System/Asset_Integrity_Register_V3_1.json"


def is_design_path(value: str) -> bool:
    path = PurePosixPath(value)
    return (
        not path.is_absolute()
        and len(path.parts) > 1
        and path.parts[0] == "design"
        and all(part not in {"", ".", ".."} for part in path.parts)
    )


def sha256_hex(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def git_blob_sha1(data: bytes) -> str:
    header = b"blob " + str(len(data)).encode("ascii") + b"\0"
    return hashlib.sha1(header + data).hexdigest()


def run_git(root: Path, args: list[str], *, check: bool = True) -> subprocess.CompletedProcess[bytes]:
    result = subprocess.run(
        ["git", *args],
        cwd=root,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if check and result.returncode != 0:
        message = result.stderr.decode("utf-8", errors="replace").strip()
        raise RuntimeError(f"git {' '.join(args)} failed: {message}")
    return result


def ensure_baseline_commit(root: Path, commit_sha: str) -> None:
    if not SHA1_RE.fullmatch(commit_sha):
        raise ValueError(f"invalid baseline commit SHA: {commit_sha!r}")
    probe = run_git(root, ["cat-file", "-e", f"{commit_sha}^{{commit}}"], check=False)
    if probe.returncode == 0:
        return
    fetched = run_git(root, ["fetch", "--no-tags", "--depth=1", "origin", commit_sha], check=False)
    if fetched.returncode != 0:
        message = fetched.stderr.decode("utf-8", errors="replace").strip()
        raise RuntimeError(f"could not fetch baseline commit {commit_sha}: {message}")
    probe = run_git(root, ["cat-file", "-e", f"{commit_sha}^{{commit}}"], check=False)
    if probe.returncode != 0:
        raise RuntimeError(f"baseline commit {commit_sha} is still unavailable after fetch")


def read_baseline_blob(root: Path, commit_sha: str, asset_path: str) -> bytes:
    if not is_design_path(asset_path):
        raise ValueError(f"asset path is outside design/**: {asset_path!r}")
    result = run_git(root, ["show", f"{commit_sha}:{asset_path}"], check=False)
    if result.returncode != 0:
        message = result.stderr.decode("utf-8", errors="replace").strip()
        raise RuntimeError(f"cannot read {asset_path} at {commit_sha}: {message}")
    return result.stdout


def verify_register(root: Path, register_path: Path) -> int:
    try:
        document = json.loads(register_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        print(f"FAIL: cannot read asset register {register_path}: {exc}", file=sys.stderr)
        return 1

    baseline = document.get("baseline_commit_sha")
    entries = document.get("assets")
    if not isinstance(entries, list):
        print("FAIL: register field 'assets' must be a list", file=sys.stderr)
        return 1

    exception_rows = document.get("worktree_exceptions") or []
    if not isinstance(exception_rows, list):
        print("FAIL: register field 'worktree_exceptions' must be a list", file=sys.stderr)
        return 1
    exceptions: dict[str, dict[str, Any]] = {}
    for exception in exception_rows:
        if not isinstance(exception, dict):
            print("FAIL: worktree exception must be an object", file=sys.stderr)
            return 1
        exception_path = exception.get("path")
        expected_current_sha = exception.get("expected_current_git_blob_sha")
        if (
            not isinstance(exception_path, str)
            or not is_design_path(exception_path)
            or not isinstance(expected_current_sha, str)
            or not SHA1_RE.fullmatch(expected_current_sha)
        ):
            print(f"FAIL: invalid worktree exception: {exception!r}", file=sys.stderr)
            return 1
        if exception_path in exceptions:
            print(f"FAIL: duplicate worktree exception: {exception_path}", file=sys.stderr)
            return 1
        exceptions[exception_path] = exception

    recorded_count = (document.get("counts") or {}).get("total_design_files")
    if recorded_count is not None and recorded_count != len(entries):
        print(
            f"FAIL: register count says {recorded_count}; found {len(entries)} rows",
            file=sys.stderr,
        )
        return 1

    if len(entries) != 658:
        print(f"FAIL: expected 658 baseline design files; found {len(entries)}", file=sys.stderr)
        return 1

    paths: set[str] = set()
    issues: list[str] = []
    verified = 0
    recorded_sha256_checked = 0
    declared_sha256_checked = 0
    formerly_blocked_recomputed = 0
    allowlisted_worktree_changes = 0

    try:
        ensure_baseline_commit(root, str(baseline or ""))
    except (ValueError, RuntimeError) as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 1

    for row in entries:
        if not isinstance(row, dict):
            issues.append("asset row is not an object")
            continue
        asset_path = row.get("repository_path")
        if not isinstance(asset_path, str) or not is_design_path(asset_path):
            issues.append(f"invalid repository_path: {asset_path!r}")
            continue
        if asset_path in paths:
            issues.append(f"duplicate repository_path: {asset_path}")
            continue
        paths.add(asset_path)

        expected_git_sha = row.get("git_blob_sha")
        if not isinstance(expected_git_sha, str) or not SHA1_RE.fullmatch(expected_git_sha):
            issues.append(f"{asset_path}: missing or invalid git_blob_sha")
            continue

        try:
            data = read_baseline_blob(root, str(baseline), asset_path)
        except (ValueError, RuntimeError) as exc:
            issues.append(str(exc))
            continue

        actual_size = len(data)
        expected_size = row.get("size_bytes")
        if not isinstance(expected_size, int) or expected_size != actual_size:
            issues.append(f"{asset_path}: size {actual_size} != recorded {expected_size!r}")

        actual_git_sha = git_blob_sha1(data)
        if actual_git_sha != expected_git_sha:
            issues.append(f"{asset_path}: Git blob SHA {actual_git_sha} != recorded {expected_git_sha}")

        current_file = root.joinpath(*PurePosixPath(asset_path).parts)
        exception = exceptions.get(asset_path)
        if current_file.is_symlink() or not current_file.is_file():
            issues.append(f"{asset_path}: current checkout file is missing or is a symlink")
        else:
            current_data = current_file.read_bytes()
            current_git_sha = git_blob_sha1(current_data)
            if current_git_sha != actual_git_sha:
                if exception is None:
                    issues.append(f"{asset_path}: current checkout differs from the baseline")
                elif row.get("is_frozen_reference") is True:
                    issues.append(f"{asset_path}: frozen reference cannot be allowlisted as a worktree change")
                elif current_git_sha != exception["expected_current_git_blob_sha"]:
                    issues.append(
                        f"{asset_path}: current Git blob SHA {current_git_sha} "
                        f"!= allowlisted {exception['expected_current_git_blob_sha']}"
                    )
                else:
                    allowlisted_worktree_changes += 1
            elif exception is not None:
                issues.append(f"{asset_path}: expected allowlisted worktree change is not present")

        actual_sha256 = sha256_hex(data)
        recorded_sha256 = row.get("actual_sha256")
        if recorded_sha256 is not None:
            recorded_sha256_checked += 1
            if not isinstance(recorded_sha256, str) or not SHA256_RE.fullmatch(recorded_sha256):
                issues.append(f"{asset_path}: invalid recorded actual_sha256")
            elif actual_sha256 != recorded_sha256:
                issues.append(f"{asset_path}: SHA-256 {actual_sha256} != recorded {recorded_sha256}")

        declared_sha256 = row.get("declared_sha256")
        if declared_sha256 is not None:
            declared_sha256_checked += 1
            if not isinstance(declared_sha256, str) or not SHA256_RE.fullmatch(declared_sha256):
                issues.append(f"{asset_path}: invalid declared_sha256")
            elif actual_sha256 != declared_sha256:
                issues.append(f"{asset_path}: SHA-256 {actual_sha256} != declared {declared_sha256}")

        evidence = row.get("declared_sha256_evidence") or []
        evidence_hashes = {
            item.get("sha256")
            for item in evidence
            if isinstance(item, dict) and isinstance(item.get("sha256"), str)
        }
        if len(evidence_hashes) > 1:
            issues.append(f"{asset_path}: conflicting current SHA-256 declarations: {sorted(evidence_hashes)}")
        for expected in evidence_hashes:
            if not SHA256_RE.fullmatch(expected):
                issues.append(f"{asset_path}: invalid SHA-256 evidence value {expected!r}")
            elif actual_sha256 != expected:
                issues.append(f"{asset_path}: SHA-256 {actual_sha256} != source declaration {expected}")

        if row.get("raw_bytes_read") is False:
            formerly_blocked_recomputed += 1
        verified += 1

    if len(paths) != len(entries):
        issues.append(f"unique paths {len(paths)} != register rows {len(entries)}")
    for exception_path in exceptions:
        if exception_path not in paths:
            issues.append(f"allowlisted worktree path is not in the baseline register: {exception_path}")

    if issues:
        print(f"FAIL: {len(issues)} Phase 1 asset integrity issue(s)")
        for issue in issues[:100]:
            print(f" - {issue}")
        if len(issues) > 100:
            print(f" - ... {len(issues) - 100} more")
        return 1

    print("PASS: Phase 1 baseline asset integrity")
    print(f"  baseline commit: {baseline}")
    print(f"  design files checked: {verified}")
    print(f"  Git blob SHA-1 and size matched: {verified}")
    print(f"  recorded raw SHA-256 values matched: {recorded_sha256_checked}")
    print(f"  direct declared SHA-256 values matched: {declared_sha256_checked}")
    print(f"  formerly API-blocked rows recomputed from Git blobs: {formerly_blocked_recomputed}")
    print(f"  allowlisted non-frozen worktree changes matched: {allowlisted_worktree_changes}")
    print("  Any other tracked baseline change, including a frozen asset change, fails this check.")
    return 0


def run_self_test() -> int:
    sample = b"abc"
    expected_sha256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
    expected_git_sha = "f2ba8f84ab5c1bce84a7b441cb1959cfc7093b7f"
    if sha256_hex(sample) != expected_sha256:
        print("FAIL: SHA-256 self-test", file=sys.stderr)
        return 1
    if git_blob_sha1(sample) != expected_git_sha:
        print("FAIL: Git blob SHA-1 self-test", file=sys.stderr)
        return 1
    if not is_design_path("design/system/example.json") or is_design_path("design/../outside"):
        print("FAIL: repository path self-test", file=sys.stderr)
        return 1
    print("PASS: SHA-256, Git blob SHA-1, and repository-path self-tests")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--asset-register",
        default=DEFAULT_REGISTER,
        help=f"asset integrity register path (default: {DEFAULT_REGISTER})",
    )
    parser.add_argument(
        "--repo-root",
        default=".",
        help="repository checkout root (default: current directory)",
    )
    parser.add_argument(
        "--self-test",
        action="store_true",
        help="run SHA and path helper self-tests without reading the repository",
    )
    args = parser.parse_args()
    if args.self_test:
        return run_self_test()
    return verify_register(Path(args.repo_root).resolve(), Path(args.repo_root).resolve() / args.asset_register)


if __name__ == "__main__":
    raise SystemExit(main())
