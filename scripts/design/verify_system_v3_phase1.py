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
DEFAULT_ASSET_REGISTRY = "design/system_v3/06_Asset_System/Asset_Registry_V3_FINAL.json"
EXPECTED_BASELINE_FILES = 658
EXPECTED_APPROVED_DERIVATIONS = 11
EXPECTED_BRAND_DERIVATIONS = 8
CAMERA_DERIVED_ROOT = "design/system_v3/06_Asset_System/derived/normal_home_camera_primary_v1/"
CAMERA_DERIVED_PATHS = {CAMERA_DERIVED_ROOT + name for name in ("normal_home_camera_primary_v1.png", "manifest.json", "source_audit.json")}
DERIVED_ASSET_ROOT = "design/system_v3/06_Asset_System/derived/brand_title_primary/"


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


def verify_register(root: Path, register_path: Path, asset_registry_path: Path) -> int:
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

    try:
        asset_registry = json.loads(asset_registry_path.read_text(encoding="utf-8"))
        asset_rows = asset_registry.get("entries")
        if not isinstance(asset_rows, list):
            raise ValueError("Asset Registry entries must be a list")
    except (OSError, json.JSONDecodeError, ValueError) as exc:
        print(f"FAIL: cannot read effective Asset Registry {asset_registry_path}: {exc}", file=sys.stderr)
        return 1
    asset_rows_by_path = {row.get("path"): row for row in asset_rows if isinstance(row, dict)}
    approved_derivations = {
        row.get("path"): row for row in asset_rows
        if isinstance(row, dict) and isinstance(row.get("post_baseline_addition"), dict)
    }
    if len(entries) != EXPECTED_BASELINE_FILES + EXPECTED_APPROVED_DERIVATIONS:
        print(
            f"FAIL: expected {EXPECTED_BASELINE_FILES} immutable baseline rows plus "
            f"{EXPECTED_APPROVED_DERIVATIONS} registered derivation rows; found {len(entries)}",
            file=sys.stderr,
        )
        return 1
    brand_paths = {path for path in approved_derivations if isinstance(path, str) and path.startswith(DERIVED_ASSET_ROOT)}
    camera_paths = set(approved_derivations) - brand_paths
    if (len(brand_paths) != EXPECTED_BRAND_DERIVATIONS
            or camera_paths != CAMERA_DERIVED_PATHS
            or len(approved_derivations) != EXPECTED_APPROVED_DERIVATIONS):
        print("FAIL: additions must be the 8 brand derivations plus the exact 3 authorized Camera files", file=sys.stderr)
        return 1
    if recorded_count != len(entries):
        print("FAIL: integrity register current entry count is inconsistent", file=sys.stderr)
        return 1

    paths: set[str] = set()
    issues: list[str] = []
    verified = 0
    recorded_sha256_checked = 0
    declared_sha256_checked = 0
    formerly_blocked_recomputed = 0
    allowlisted_worktree_changes = 0
    verified_derivations = 0

    try:
        ensure_baseline_commit(root, str(baseline or ""))
    except (ValueError, RuntimeError) as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 1

    def source_is_registered_and_verified(source_path: str, source_sha: str, trail: set[str] | None = None) -> bool:
        trail = set() if trail is None else trail
        if source_path in trail:
            return False
        source_row = asset_rows_by_path.get(source_path)
        if not isinstance(source_row, dict) or source_row.get("sha256") != source_sha:
            return False
        baseline_blob = run_git(root, ["cat-file", "-e", f"{baseline}:{source_path}"], check=False)
        if baseline_blob.returncode == 0:
            try:
                return sha256_hex(read_baseline_blob(root, str(baseline), source_path)) == source_sha
            except (ValueError, RuntimeError):
                return False
        source_addition = source_row.get("post_baseline_addition")
        source_file = root.joinpath(*PurePosixPath(source_path).parts)
        if not isinstance(source_addition, dict) or source_file.is_symlink() or not source_file.is_file():
            return False
        if sha256_hex(source_file.read_bytes()) != source_sha:
            return False
        trail.add(source_path)
        upstream_path = source_addition.get("source_path")
        upstream_sha = source_addition.get("source_sha256")
        return isinstance(upstream_path, str) and isinstance(upstream_sha, str) and source_is_registered_and_verified(
            upstream_path, upstream_sha, trail
        )

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

        derivation = approved_derivations.get(asset_path)
        if derivation is not None:
            addition = derivation.get("post_baseline_addition") or {}
            if derivation.get("sha256") != row.get("actual_sha256") or derivation.get("git_blob_sha") != expected_git_sha:
                issues.append(f"{asset_path}: Phase 1 register and effective Asset Registry identities differ")
                continue
            if addition.get("artifact_type") not in {"DERIVED_FROM_FROZEN_RASTER", "BRAND_TITLE_EXTRACTION_EVIDENCE"}:
                issues.append(f"{asset_path}: unapproved post-baseline artifact type")
                continue
            if addition.get("approval_status") not in {"APPROVED_VISUALLY", "APPROVED_AUDIT_EVIDENCE"}:
                issues.append(f"{asset_path}: post-baseline artifact is not explicitly approved")
                continue
            source_path = addition.get("source_path")
            source_sha = addition.get("source_sha256")
            if not isinstance(source_path, str) or not isinstance(source_sha, str) or not source_is_registered_and_verified(source_path, source_sha):
                issues.append(f"{asset_path}: approved source chain does not resolve to registered baseline bytes")
                continue
            current_file = root.joinpath(*PurePosixPath(asset_path).parts)
            if current_file.is_symlink() or not current_file.is_file():
                issues.append(f"{asset_path}: registered derivative is missing or is a symlink")
                continue
            data = current_file.read_bytes()
            verified_derivations += 1
        else:
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
    print(f"  immutable baseline design files checked: {verified - verified_derivations}")
    print(f"  registered post-baseline derivations checked: {verified_derivations}")
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
        "--asset-registry",
        default=DEFAULT_ASSET_REGISTRY,
        help=f"effective Asset Registry path (default: {DEFAULT_ASSET_REGISTRY})",
    )
    parser.add_argument(
        "--self-test",
        action="store_true",
        help="run SHA and path helper self-tests without reading the repository",
    )
    args = parser.parse_args()
    if args.self_test:
        return run_self_test()
    root = Path(args.repo_root).resolve()
    return verify_register(root, root / args.asset_register, root / args.asset_registry)


if __name__ == "__main__":
    raise SystemExit(main())
