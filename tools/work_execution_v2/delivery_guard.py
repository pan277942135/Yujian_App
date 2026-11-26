#!/usr/bin/env python3
"""Fail-closed commit, checkpoint, push, and CI bookkeeping for YuJian Work.

This tool complements (and never replaces) Control Plane Admission and the
existing Work V2 failure gate. It never force-pushes, never stores credentials,
and leaves divergent history for an explicit safe-reconciliation attempt.
"""
from __future__ import annotations

import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
from typing import Any


SHA40 = re.compile(r"^[0-9a-f]{40}$")
SAFE_UNTRACKED_SUFFIXES = {
    ".py", ".kt", ".java", ".kts", ".gradle", ".yaml", ".yml",
    ".json", ".md", ".txt", ".sh", ".xml", ".toml", ".csv",
}
SENSITIVE_PARTS = {
    ".env", ".ssh", "secret", "secrets", "credential", "credentials",
    "private", "tokens", "token-store",
}
SENSITIVE_SUFFIXES = {
    ".apk", ".aab", ".zip", ".onnx", ".tflite", ".task", ".pt",
    ".pth", ".jpg", ".jpeg", ".png", ".webp", ".mp4", ".mov",
    ".pem", ".key", ".p12", ".pfx", ".keystore",
}
MAX_SAFE_UNTRACKED_BYTES = 25 * 1024 * 1024
AUTHORIZED_REPOSITORY = "pan277942135/Yujian_App"
AUTHORIZED_PR_BRANCH = "fix/26kf-path-boundary-and-fish-gate-v1"
SENSITIVE_CONTENT_PATTERNS = (
    re.compile(rb"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
    re.compile(rb"\bgh[pousr]_[A-Za-z0-9_]{20,}\b"),
    re.compile(rb"\bgithub_pat_[A-Za-z0-9_]{20,}\b"),
    re.compile(rb"AKIA[0-9A-Z]{16}"),
)


class GuardError(RuntimeError):
    pass


def _run(root: Path, args: list[str], *, check: bool = True,
         timeout: int = 1200, env: dict[str, str] | None = None) -> subprocess.CompletedProcess[str]:
    result = subprocess.run(
        args, cwd=root, text=True, capture_output=True, timeout=timeout,
        env=env, check=False,
    )
    if check and result.returncode:
        detail = (result.stderr or result.stdout).strip()
        raise GuardError(f"command failed ({result.returncode}): {args[0]} {args[1:]}: {_redact(detail)[-1200:]}")
    return result


def _secret_values() -> list[str]:
    values = []
    for key, value in os.environ.items():
        if value and any(word in key.casefold() for word in ("token", "password", "secret", "credential", "private_key")):
            if len(value) >= 8:
                values.append(value)
    return sorted(set(values), key=len, reverse=True)


def _redact(value: str) -> str:
    for secret in _secret_values():
        value = value.replace(secret, "[REDACTED]")
    value = re.sub(r"\bgh[pousr]_[A-Za-z0-9_]{20,}\b", "[REDACTED_GITHUB_TOKEN]", value)
    value = re.sub(r"\bgithub_pat_[A-Za-z0-9_]{20,}\b", "[REDACTED_GITHUB_TOKEN]", value)
    value = re.sub(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----.*?-----END (?:RSA |EC |OPENSSH )?PRIVATE KEY-----", "[REDACTED_PRIVATE_KEY]", value, flags=re.DOTALL)
    return value


def _contains_sensitive_content(path: Path) -> bool:
    try:
        data = path.read_bytes()
    except OSError:
        return True
    if any(pattern.search(data) for pattern in SENSITIVE_CONTENT_PATTERNS):
        return True
    return any(secret.encode("utf-8", "ignore") in data for secret in _secret_values())


def _assert_no_secret_values(value: Any) -> None:
    encoded = json.dumps(value, ensure_ascii=False, sort_keys=True)
    if any(secret in encoded for secret in _secret_values()) or any(pattern.search(encoded.encode("utf-8")) for pattern in SENSITIVE_CONTENT_PATTERNS):
        raise GuardError("SECRET_VALUE_PRESENT_IN_DELIVERY_METADATA")


def _staged_sensitive_paths(root: Path) -> list[str]:
    paths = _git(root, "diff", "--cached", "--name-only", "-z").split("\0")
    sensitive = []
    for rel in filter(None, paths):
        result = subprocess.run(["git", "show", f":{rel}"], cwd=root, capture_output=True, check=False)
        data = result.stdout
        contains = any(pattern.search(data) for pattern in SENSITIVE_CONTENT_PATTERNS)
        contains = contains or any(secret.encode("utf-8", "ignore") in data for secret in _secret_values())
        if contains:
            sensitive.append(rel)
    return sensitive


def _git(root: Path, *args: str, check: bool = True,
         timeout: int = 1200) -> str:
    result = _run(root, ["git", *args], check=check, timeout=timeout)
    return result.stdout.strip()


def repository_root(start: Path) -> Path:
    try:
        return Path(_git(start, "rev-parse", "--show-toplevel")).resolve()
    except GuardError as exc:
        raise GuardError("not inside a Git worktree") from exc


def common_git_dir(root: Path) -> Path:
    value = Path(_git(root, "rev-parse", "--git-common-dir"))
    return (root / value).resolve() if not value.is_absolute() else value.resolve()


def _hash(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _atomic_json(path: Path, data: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temp_name = tempfile.mkstemp(prefix=".delivery-", dir=path.parent)
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as stream:
            json.dump(data, stream, ensure_ascii=False, sort_keys=True, indent=2)
            stream.write("\n")
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temp_name, path)
    finally:
        if os.path.exists(temp_name):
            os.unlink(temp_name)


@contextmanager
def writer_lock(root: Path, checkpoint_root: Path):
    """Serialize delivery operations and enforce the recorded canonical writer."""
    root = root.resolve()
    lock_record = checkpoint_root / "PRODUCTION_WORKSPACE_LOCK.json"
    if lock_record.is_file():
        record = json.loads(lock_record.read_text(encoding="utf-8"))
        canonical = Path(record.get("active_writer_worktree", "")).resolve()
        if canonical != root:
            raise GuardError(f"NOT_CANONICAL_WORKTREE: expected {canonical}, got {root}")
        expected_branch = record.get("active_local_branch")
        actual_branch = _git(root, "branch", "--show-current")
        if expected_branch and actual_branch != expected_branch:
            raise GuardError(f"WRITER_BRANCH_MISMATCH: expected {expected_branch}, got {actual_branch}")
    checkpoint_root.mkdir(parents=True, exist_ok=True)
    lock_path = checkpoint_root / ".delivery_guard.lock"
    with lock_path.open("a+") as stream:
        fcntl.flock(stream.fileno(), fcntl.LOCK_EX)
        try:
            yield
        finally:
            fcntl.flock(stream.fileno(), fcntl.LOCK_UN)


def _safe_untracked_reason(rel: str, size: int) -> str | None:
    path = Path(rel)
    lowered_parts = {part.casefold() for part in path.parts}
    name = path.name.casefold()
    suffix = path.suffix.casefold()
    if lowered_parts & SENSITIVE_PARTS or any(token in name for token in ("token", "password", "secret", "credential")):
        return "sensitive_path_or_name"
    if suffix in SENSITIVE_SUFFIXES or rel.replace("\\", "/").startswith(("artifacts/", "build/", ".gradle/")):
        return "private_media_model_or_build_artifact"
    if suffix not in SAFE_UNTRACKED_SUFFIXES:
        return "not_a_source_or_report_file"
    if size > MAX_SAFE_UNTRACKED_BYTES:
        return "file_exceeds_safe_copy_limit"
    return None


def _untracked_paths(root: Path) -> list[str]:
    result = _run(root, ["git", "ls-files", "--others", "--exclude-standard", "-z"])
    return [item.decode("utf-8", "surrogateescape") for item in result.stdout.encode().split(b"\0") if item]


def _safe_copy_untracked(root: Path, dest: Path) -> dict[str, Any]:
    copied: list[dict[str, Any]] = []
    skipped: list[dict[str, Any]] = []
    for rel in _untracked_paths(root):
        source = root / rel
        try:
            resolved = source.resolve(strict=True)
            resolved.relative_to(root)
            if source.is_symlink() or not source.is_file():
                skipped.append({"path": rel, "reason": "not_a_regular_file"})
                continue
            size = source.stat().st_size
        except (OSError, ValueError):
            skipped.append({"path": rel, "reason": "missing_or_outside_worktree"})
            continue
        reason = _safe_untracked_reason(rel, size)
        if reason is None and _contains_sensitive_content(source):
            reason = "sensitive_content_detected"
        if reason:
            skipped.append({"path": rel, "reason": reason, "bytes": size})
            continue
        target = dest / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
        copied.append({"path": rel, "bytes": size, "sha256": _hash(target)})
    return {"copied": copied, "skipped": skipped}


def _remote_sha(root: Path, remote: str, branch: str) -> str:
    ref = f"refs/heads/{branch}"
    output = _git(root, "ls-remote", remote, ref)
    if not output:
        raise GuardError(f"REMOTE_BRANCH_MISSING: {ref}")
    sha = output.split()[0]
    if not SHA40.fullmatch(sha):
        raise GuardError("REMOTE_SHA_INVALID")
    return sha


def _is_ancestor(root: Path, ancestor: str, descendant: str) -> bool:
    result = _run(root, ["git", "merge-base", "--is-ancestor", ancestor, descendant], check=False)
    if result.returncode == 0:
        return True
    if result.returncode == 1:
        return False
    raise GuardError("ANCESTRY_CHECK_FAILED")


def _commit_rows(root: Path, base: str, head: str, metadata: dict[str, Any]) -> list[dict[str, str]]:
    output = _git(root, "rev-list", "--reverse", "--topo-order", f"{base}..{head}")
    rows = []
    for sha in output.splitlines():
        meta = _git(root, "show", "-s", "--format=%H%x1f%P%x1f%s", sha).split("\x1f")
        body = _git(root, "show", "-s", "--format=%B", sha)
        trailers = {}
        for line in body.splitlines():
            match = re.match(r"^(Issue|Root-Cause|Source-SHA|Parent-SHA|Tests|Evidence):\s*(.*)$", line)
            if match:
                trailers[match.group(1).casefold().replace("-", "_")] = match.group(2).strip()
        parent = meta[1].split()[0] if len(meta) > 1 and meta[1] else ""
        rows.append({
            "commit_sha": meta[0], "parent_sha": parent,
            "repository": metadata.get("repository", AUTHORIZED_REPOSITORY),
            "issue_id": trailers.get("issue", metadata.get("issue_id", "UNKNOWN")),
            "root_cause": trailers.get("root_cause", metadata.get("root_cause", "NOT_RECORDED")),
            "source_sha": trailers.get("source_sha", metadata.get("source_sha", "NOT_RECORDED")),
            "branch": metadata.get("target_branch", ""),
            "test_result": trailers.get("tests", metadata.get("test_result", "NOT_RECORDED")),
            "admission_result": metadata.get("admission_result", "NOT_RUN"),
            "bundle_location": metadata.get("bundle_location", ""),
            "patch_sha256": "PENDING",
            "push_status": metadata.get("push_status", "PENDING"),
            "ci_status": metadata.get("ci_status", "PENDING"),
            "test_commands": metadata.get("test_commands", []),
            "test_report_location": metadata.get("test_report_location", "NOT_PROVIDED"),
            "evidence_ref": metadata.get("evidence_ref", "NOT_RECORDED"),
            "subject": meta[2] if len(meta) > 2 else "",
        })
    return rows


def _write_checksums(folder: Path) -> None:
    checksum_file = folder / "SHA256SUMS.txt"
    rows = []
    for path in sorted(folder.rglob("*")):
        if not path.is_file() or path == checksum_file:
            continue
        rows.append(f"{_hash(path)}  {path.relative_to(folder).as_posix()}")
    checksum_file.write_text("\n".join(rows) + "\n", encoding="utf-8")


def _update_pending_queue(checkpoint_root: Path, commits: list[dict[str, Any]]) -> None:
    queue_path = checkpoint_root / "PENDING_COMMITS_QUEUE.json"
    queue = json.loads(queue_path.read_text(encoding="utf-8")) if queue_path.is_file() else {"schema_version": "1.0", "commits": []}
    existing = {row.get("commit_sha"): row for row in queue.get("commits", []) if row.get("commit_sha")}
    for row in commits:
        sha = row.get("commit_sha")
        if sha:
            existing[sha] = {**existing.get(sha, {}), **row}
    queue["commits"] = [existing[key] for key in sorted(existing)]
    queue["updated_at_utc"] = datetime.now(timezone.utc).isoformat()
    _atomic_json(queue_path, queue)


def _refresh_pending_queue(checkpoint_root: Path, root: Path, branch: str,
                           remote_sha: str, status: str) -> None:
    queue_path = checkpoint_root / "PENDING_COMMITS_QUEUE.json"
    if not queue_path.is_file():
        return
    queue = json.loads(queue_path.read_text(encoding="utf-8"))
    changed = False
    for row in queue.get("commits", []):
        commit_sha = row.get("commit_sha", "")
        if row.get("branch") == branch and SHA40.fullmatch(commit_sha):
            if _is_ancestor(root, commit_sha, remote_sha):
                row["push_status"] = status
                row["remote_sha"] = remote_sha
                changed = True
    if changed:
        queue["updated_at_utc"] = datetime.now(timezone.utc).isoformat()
        _atomic_json(queue_path, queue)


def snapshot(root: Path, checkpoint_root: Path, metadata: dict[str, Any],
             test_report: Path | None = None, *, reason: str) -> Path:
    """Persist Git objects, binary diffs, safe untracked source, and a queue record."""
    root = repository_root(root)
    _assert_no_secret_values(metadata)
    if test_report is None and metadata.get("test_report"):
        candidate_report = Path(metadata["test_report"])
        if candidate_report.is_file():
            test_report = candidate_report
    checkpoint_root.mkdir(parents=True, exist_ok=True)
    head = _git(root, "rev-parse", "HEAD")
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
    folder = checkpoint_root / f"{stamp}_{head[:8]}"
    folder.mkdir(parents=True, exist_ok=False)

    bundle = folder / "goal_checkpoint.bundle"
    bundle_tmp = folder / ".goal_checkpoint.bundle.tmp"
    _git(root, "bundle", "create", str(bundle_tmp), "--all", "HEAD", timeout=2400)
    _git(root, "bundle", "verify", str(bundle_tmp), timeout=2400)
    os.replace(bundle_tmp, bundle)

    remote = metadata.get("remote", "origin")
    branch = metadata.get("target_branch", "")
    remote_sha = None
    merge_base = None
    if metadata.get("remote_sha_override") and SHA40.fullmatch(metadata["remote_sha_override"]):
        remote_sha = metadata["remote_sha_override"]
        merge_base = _git(root, "merge-base", remote_sha, head, check=False) or None
    elif branch and not metadata.get("skip_remote_lookup"):
        try:
            remote_sha = _remote_sha(root, remote, branch)
            merge_base = _git(root, "merge-base", remote_sha, head)
        except GuardError:
            pass
    base = merge_base
    if not base:
        candidates = (metadata.get("source_sha", ""), metadata.get("parent_sha", ""))
        base = next((candidate for candidate in candidates
                     if SHA40.fullmatch(candidate) and candidate != head and _is_ancestor(root, candidate, head)), None)
        if not base:
            base = _git(root, "rev-list", "--max-parents=0", "HEAD").splitlines()[-1]
    commit_patch = _run(root, ["git", "format-patch", "--stdout", f"{base}..{head}"], check=False, timeout=1800)
    working_diff = _run(root, ["git", "diff", "--binary", "HEAD"], check=False)
    cached_diff = _run(root, ["git", "diff", "--binary", "--cached"], check=False)
    patch = folder / "pending_commits.patch"
    patch.write_bytes(commit_patch.stdout.encode() + cached_diff.stdout.encode() + working_diff.stdout.encode())

    untracked_result = _safe_copy_untracked(root, folder / "untracked")
    commits = _commit_rows(root, base, head, metadata)
    pending = {
        "schema_version": "1.0", "repository": metadata.get("repository", "pan277942135/Yujian_App"),
        "goal_id": metadata.get("goal_id", "YUJIAN_AI_FULL_RECOGNITION_AND_VISUAL_FIDELITY"),
        "issue_id": metadata.get("issue_id", "UNKNOWN"), "branch": metadata.get("target_branch", ""),
        "source_sha": metadata.get("source_sha", head), "head_sha": head,
        "remote_sha": remote_sha, "merge_base": merge_base,
        "test_result": metadata.get("test_result", "NOT_RECORDED"),
        "admission_result": metadata.get("admission_result", "NOT_RUN"),
        "root_cause": metadata.get("root_cause", "NOT_RECORDED"),
        "test_commands": metadata.get("test_commands", []),
        "test_report_location": str(test_report) if test_report else "NOT_PROVIDED",
        "evidence_ref": metadata.get("evidence_ref", "NOT_RECORDED"),
        "bundle_location": str(bundle), "patch_location": str(patch),
        "patch_sha256": _hash(patch), "push_status": reason, "ci_status": "PENDING",
        "commits": commits, "safe_untracked": untracked_result,
        "created_at_utc": datetime.now(timezone.utc).isoformat(),
    }
    if test_report and test_report.is_file():
        shutil.copy2(test_report, folder / "TEST_REPORT.md")
    else:
        summary = metadata.get("test_summary", "No test report was provided.")
        (folder / "TEST_REPORT.md").write_text(
            f"# Delivery checkpoint\n\nStatus: {metadata.get('test_result', 'NOT_RECORDED')}\n\n{summary}\n",
            encoding="utf-8",
        )
    pending["test_report_location"] = str(folder / "TEST_REPORT.md")
    for row in commits:
        row["bundle_location"] = str(bundle)
        row["patch_sha256"] = pending["patch_sha256"]
        row["push_status"] = reason
        row["test_report_location"] = str(folder / "TEST_REPORT.md")
    _atomic_json(folder / "pending_commits.json", pending)
    _atomic_json(folder / "WORK_STATE.json", {
        **pending, "worktree": str(root), "next_action": metadata.get("next_action", "Resolve the recorded blocker and resume from this checkpoint."),
        "failure_classification": metadata.get("failure_classification", reason),
    })
    _write_checksums(folder)
    _update_pending_queue(checkpoint_root, commits)
    return folder


def _run_test_commands(root: Path, commands: list[list[str]], report_path: Path) -> tuple[bool, str]:
    sections = ["# Stage test report", "", f"Started: {datetime.now(timezone.utc).isoformat()}", ""]
    passed = True
    for index, argv in enumerate(commands, 1):
        if not argv or not all(isinstance(x, str) and x for x in argv):
            raise GuardError("TEST_COMMAND_MUST_BE_NONEMPTY_ARGV")
        result = _run(root, argv, check=False, timeout=3600)
        sections += [f"## Command {index}: `{_redact(json.dumps(argv, ensure_ascii=False))}`", "", f"Exit code: `{result.returncode}`", "", "```text", _redact(result.stdout[-20000:]), _redact(result.stderr[-12000:]), "```", ""]
        passed = passed and result.returncode == 0
    sections += ["TESTS_PASS" if passed else "TESTS_FAIL", ""]
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text("\n".join(sections), encoding="utf-8")
    return passed, "\n".join(sections)


def commit_stage(root: Path, checkpoint_root: Path, manifest: dict[str, Any]) -> dict[str, Any]:
    root = repository_root(root)
    _assert_no_secret_values(manifest)
    required = ("issue_id", "root_cause", "source_sha", "subject", "files", "test_commands", "evidence_ref")
    missing = [key for key in required if not manifest.get(key)]
    if missing:
        raise GuardError("MANIFEST_MISSING_" + ",".join(missing))
    head_before = _git(root, "rev-parse", "HEAD")
    if manifest["source_sha"] != head_before:
        raise GuardError(f"SOURCE_SHA_MISMATCH: expected current HEAD {head_before}")
    if not SHA40.fullmatch(manifest["source_sha"]):
        raise GuardError("SOURCE_SHA_INVALID")
    allowed = set()
    before_hashes: dict[str, str | None] = {}
    tracked_paths = set(_git(root, "ls-files").splitlines())
    for rel in manifest["files"]:
        candidate = Path(rel)
        if candidate.is_absolute() or ".." in candidate.parts:
            raise GuardError("STAGE_PATH_OUTSIDE_REPOSITORY")
        candidate_path = root / candidate
        resolved = candidate_path.resolve()
        try:
            resolved.relative_to(root)
        except ValueError as exc:
            raise GuardError("STAGE_PATH_SYMLINK_ESCAPE") from exc
        if candidate_path.is_symlink():
            raise GuardError("STAGE_PATH_SYMLINK_FORBIDDEN: " + rel)
        if candidate_path.is_file():
            before_hashes[candidate.as_posix()] = _hash(candidate_path)
        elif not candidate_path.exists() and candidate.as_posix() in tracked_paths:
            before_hashes[candidate.as_posix()] = None
        else:
            raise GuardError("STAGE_PATH_MISSING_OR_NOT_FILE: " + rel)
        allowed.add(candidate.as_posix())
    tracked_changed = set(_git(root, "diff", "--name-only", "HEAD").splitlines())
    untracked_changed = set(_untracked_paths(root))
    selected_untracked = allowed & untracked_changed
    changed = tracked_changed | selected_untracked
    staged = set(_git(root, "diff", "--cached", "--name-only").splitlines())
    if staged:
        raise GuardError("PREEXISTING_STAGED_CHANGES: commit or unstage them separately")
    unexpected_tracked = tracked_changed - allowed
    missing_selected = allowed - (tracked_changed | untracked_changed)
    if unexpected_tracked or missing_selected or not changed:
        details = []
        if unexpected_tracked:
            details.append("unexpected tracked=" + ", ".join(sorted(unexpected_tracked)))
        if missing_selected:
            details.append("missing selected=" + ", ".join(sorted(missing_selected)))
        raise GuardError("STAGE_SCOPE_MISMATCH: " + "; ".join(details))
    report_path = checkpoint_root / "stage_reports" / f"{head_before[:8]}_{manifest['issue_id']}.md"
    tests_ok, _ = _run_test_commands(root, manifest["test_commands"], report_path)
    if not tests_ok:
        snap = snapshot(root, checkpoint_root, {**manifest, "test_result": "FAIL", "test_summary": "Targeted test command failed; no source commit created."}, report_path, reason="TEST_FAILED")
        return {"status": "TEST_FAILED", "snapshot": str(snap), "test_report": str(report_path)}
    for rel, old_hash in before_hashes.items():
        path = root / rel
        if old_hash is None:
            if path.exists():
                raise GuardError("WORKTREE_CHANGED_DURING_TESTS: " + rel)
        elif not path.is_file() or path.is_symlink() or _hash(path) != old_hash:
            raise GuardError("WORKTREE_CHANGED_DURING_TESTS: " + rel)
    tracked_after_tests = set(_git(root, "diff", "--name-only", "HEAD").splitlines())
    selected_untracked_after_tests = allowed & set(_untracked_paths(root))
    if tracked_after_tests | selected_untracked_after_tests != changed:
        raise GuardError("WORKTREE_PATH_SET_CHANGED_DURING_TESTS")
    _run(root, ["git", "diff", "--check", "HEAD"])
    _run(root, ["git", "add", "--", *sorted(allowed)])
    staged_after = set(_git(root, "diff", "--cached", "--name-only").splitlines())
    if staged_after != changed:
        raise GuardError("STAGED_PATH_SET_MISMATCH")
    sensitive_paths = _staged_sensitive_paths(root)
    if sensitive_paths:
        _run(root, ["git", "reset", "--", *sorted(allowed)], check=False)
        raise GuardError("SECRET_CONTENT_IN_STAGED_PATHS: " + ", ".join(sensitive_paths))
    parent = _git(root, "rev-parse", "HEAD")
    body = "\n".join([
        f"Issue: {manifest['issue_id']}", f"Root-Cause: {manifest['root_cause']}",
        f"Source-SHA: {manifest['source_sha']}", f"Parent-SHA: {parent}",
        "Tests: PASS (see checkpoint stage report)", f"Evidence: {manifest['evidence_ref']}",
    ])
    try:
        _run(root, ["git", "commit", "-m", manifest["subject"], "-m", _redact(body)])
    except GuardError as exc:
        snap = snapshot(root, checkpoint_root, {
            **manifest, "test_result": "PASS", "admission_result": "NOT_RUN",
            "failure_classification": "GIT_COMMIT_FAILED", "test_report": str(report_path),
        }, report_path, reason="GIT_COMMIT_FAILED")
        return {"status": "GIT_COMMIT_FAILED", "snapshot": str(snap), "details": _redact(str(exc))[-2000:]}
    commit_sha = _git(root, "rev-parse", "HEAD")
    receipt = {
        "issue_id": manifest["issue_id"], "source_sha": manifest["source_sha"],
        "parent_sha": parent, "commit_sha": commit_sha, "test_result": "PASS",
        "test_report": str(report_path), "test_report_sha256": _hash(report_path),
        "evidence_ref": manifest["evidence_ref"], "created_at_utc": datetime.now(timezone.utc).isoformat(),
        "admission_result": "REQUIRED_AFTER_STATE_CHECKPOINT",
    }
    receipt_path = checkpoint_root / "stage_receipts" / f"{commit_sha}.json"
    _atomic_json(receipt_path, receipt)
    return {"status": "COMMITTED_PENDING_STATE_ADMISSION_AND_PUSH", "commit_sha": commit_sha, "parent_sha": parent, "receipt": str(receipt_path), "test_report": str(report_path)}


def _admission(root: Path) -> tuple[bool, str]:
    script = root / "scripts/validate_control_plane_admission.py"
    if not script.is_file():
        return False, "ADMISSION_SCRIPT_MISSING"
    result = _run(root, [sys.executable, str(script)], check=False, timeout=1800)
    return result.returncode == 0, result.stdout + result.stderr


def _classify_transport_failure(output: str) -> str:
    low = output.casefold()
    if any(x in low for x in (
        "connect tunnel failed", "could not resolve proxy", "proxy connect",
        "connection timed out", "could not resolve host", "failed to connect",
        "network is unreachable", "operation timed out", "connection reset",
    )):
        return "NETWORK_OR_PROXY_BLOCKED"
    if "permission to " in low and "denied to" in low:
        return "REPOSITORY_PERMISSION_DENIED"
    if "requested url returned error: 403" in low or "http 403" in low:
        return "REPOSITORY_PERMISSION_DENIED"
    if any(x in low for x in ("authentication failed", "invalid username or password", "http 401", "bad credentials")):
        return "AUTH_INVALID"
    if any(x in low for x in ("non-fast-forward", "fetch first", "stale info")):
        return "REMOTE_BRANCH_CONFLICT"
    if any(x in low for x in ("protected branch hook declined", "repository rule violations", "write access to repository not granted")):
        return "REPOSITORY_PERMISSION_DENIED"
    return "PUSH_FAILED_UNCLASSIFIED"


def _reconcile_nonconflicting(root: Path, remote_sha: str, checkpoint_root: Path,
                              metadata: dict[str, Any]) -> dict[str, Any]:
    head = _git(root, "rev-parse", "HEAD")
    branch = _git(root, "branch", "--show-current")
    if not branch:
        return {"status": "REMOTE_BRANCH_CONFLICT", "reason": "detached HEAD cannot be rebased safely"}
    if _git(root, "diff", "--quiet", check=False) != "" or _git(root, "diff", "--cached", "--quiet", check=False) != "":
        # `git diff --quiet` has no stdout even for a dirty tree; inspect status instead.
        dirty = _git(root, "status", "--porcelain=v1")
        tracked_dirty = [line for line in dirty.splitlines() if not line.startswith("??")]
        if tracked_dirty:
            return {"status": "REMOTE_BRANCH_CONFLICT", "reason": "tracked worktree changes must be checkpointed before rebase"}
    base = _git(root, "merge-base", remote_sha, head, check=False)
    if not base:
        return {"status": "REMOTE_BRANCH_CONFLICT", "reason": "no common ancestor"}
    snap = snapshot(root, checkpoint_root, {**metadata, "skip_remote_lookup": True, "remote_sha_override": remote_sha}, reason="REMOTE_BRANCH_CONFLICT_BEFORE_REBASE")
    result = _run(root, ["git", "rebase", "--rebase-merges", "--onto", remote_sha, base, branch], check=False, timeout=3600)
    if result.returncode:
        _run(root, ["git", "rebase", "--abort"], check=False)
        return {"status": "REMOTE_BRANCH_CONFLICT", "reason": "rebase conflicted and was aborted", "snapshot": str(snap), "details": _redact(result.stderr)[-2000:]}
    return {"status": "REBASED_NEEDS_TESTS_AND_ADMISSION", "old_head": head, "new_head": _git(root, "rev-parse", "HEAD"), "snapshot": str(snap), "base": base}


def push_stage(root: Path, checkpoint_root: Path, metadata: dict[str, Any],
               *, resume: bool = False, allow_local_fixture_remote: bool = False) -> dict[str, Any]:
    root = repository_root(root)
    _assert_no_secret_values(metadata)
    remote = metadata.get("remote", "origin")
    branch = metadata.get("target_branch")
    if not branch:
        raise GuardError("TARGET_BRANCH_REQUIRED")
    repository = metadata.get("repository")
    if repository != AUTHORIZED_REPOSITORY and not (allow_local_fixture_remote and repository is None):
        raise GuardError("REPOSITORY_IDENTITY_MISMATCH")
    if branch != AUTHORIZED_PR_BRANCH:
        raise GuardError("UNAUTHORIZED_TARGET_BRANCH")
    remote_url = _git(root, "remote", "get-url", remote)
    if "github.com" in remote_url.casefold():
        if re.match(r"(?:ssh://)?git@github\.com[:/].+", remote_url, re.IGNORECASE):
            raise GuardError("GIT_REMOTE_USES_SSH")
        if "@github.com" in remote_url.casefold() or re.search(r"https://[^/]+@", remote_url, re.IGNORECASE):
            raise GuardError("GIT_REMOTE_CREDENTIALS_IN_URL_FORBIDDEN")
        match = re.match(r"https://github\.com/([^/]+/[^/]+?)(?:\.git)?$", remote_url, re.IGNORECASE)
        if not match or match.group(1).casefold() != AUTHORIZED_REPOSITORY.casefold():
            raise GuardError("GIT_REMOTE_REPOSITORY_MISMATCH")
    elif not allow_local_fixture_remote or not Path(remote_url).exists():
        raise GuardError("GIT_REMOTE_USES_NON_GITHUB_PROTOCOL")
    head = _git(root, "rev-parse", "HEAD")
    if metadata.get("source_sha") and not _is_ancestor(root, metadata["source_sha"], head):
        raise GuardError("SOURCE_SHA_NOT_ANCESTOR_OF_HEAD")
    test_status = str(metadata.get("test_result", "")).upper()
    if not test_status.startswith("PASS"):
        snap = snapshot(root, checkpoint_root, {**metadata, "skip_remote_lookup": True, "test_result": metadata.get("test_result", "NOT_RECORDED")}, reason="TARGETED_TEST_NOT_PASS")
        return {"status": "TARGETED_TEST_NOT_PASS", "snapshot": str(snap)}
    admission_ok, admission_output = _admission(root)
    if not admission_ok:
        snap = snapshot(root, checkpoint_root, {**metadata, "skip_remote_lookup": True, "admission_result": "FAIL", "test_result": metadata.get("test_result", "NOT_RECORDED")}, reason="CONTROL_PLANE_ADMISSION_BLOCKED")
        return {"status": "CONTROL_PLANE_ADMISSION_BLOCKED", "snapshot": str(snap), "admission": admission_output[-4000:]}
    fetched = _run(root, ["git", "fetch", remote, "main", branch], check=False, timeout=1800)
    if fetched.returncode:
        details = fetched.stderr + fetched.stdout
        reason = _classify_transport_failure(details)
        snap = snapshot(root, checkpoint_root, {**metadata, "skip_remote_lookup": True, "failure_classification": reason}, reason=reason)
        return {"status": reason, "snapshot": str(snap), "details": _redact(details)[-2000:]}
    try:
        remote_sha = _remote_sha(root, remote, branch)
    except GuardError as exc:
        reason = _classify_transport_failure(str(exc))
        snap = snapshot(root, checkpoint_root, {**metadata, "skip_remote_lookup": True, "failure_classification": reason}, reason=reason)
        return {"status": reason, "snapshot": str(snap), "details": _redact(str(exc))[-2000:]}
    head = _git(root, "rev-parse", "HEAD")
    if remote_sha == head:
        _refresh_pending_queue(checkpoint_root, root, branch, remote_sha, "ALREADY_AT_REMOTE_HEAD")
        return {"status": "ALREADY_AT_REMOTE_HEAD", "remote_sha": remote_sha}
    if _is_ancestor(root, remote_sha, head):
        relation = "FAST_FORWARD"
    elif _is_ancestor(root, head, remote_sha):
        _refresh_pending_queue(checkpoint_root, root, branch, remote_sha, "ALREADY_INCLUDED_BY_REMOTE")
        return {"status": "ALREADY_INCLUDED_BY_REMOTE", "head_sha": head, "remote_sha": remote_sha}
    elif resume:
        reconciled = _reconcile_nonconflicting(root, remote_sha, checkpoint_root, metadata)
        if reconciled["status"] != "REBASED_NEEDS_TESTS_AND_ADMISSION":
            snap = snapshot(root, checkpoint_root, {**metadata, "skip_remote_lookup": True, "remote_sha_override": remote_sha, "failure_classification": "REMOTE_BRANCH_CONFLICT"}, reason="REMOTE_BRANCH_CONFLICT")
            return {**reconciled, "snapshot_after_reconcile": str(snap)}
        commands = metadata.get("test_commands", [])
        if not commands:
            snap = snapshot(root, checkpoint_root, {**metadata, "skip_remote_lookup": True, "remote_sha_override": remote_sha, "test_result": "NOT_RUN"}, reason="REBASE_TEST_COMMANDS_MISSING")
            return {"status": "REBASE_TEST_COMMANDS_MISSING", "snapshot": str(snap), "new_head": _git(root, "rev-parse", "HEAD")}
        report_path = checkpoint_root / "resume_reports" / f"{reconciled['new_head'][:8]}.md"
        tests_ok, _ = _run_test_commands(root, commands, report_path)
        if not tests_ok:
            snap = snapshot(root, checkpoint_root, {**metadata, "skip_remote_lookup": True, "remote_sha_override": remote_sha, "test_result": "FAIL"}, report_path, reason="REBASE_TEST_FAILED")
            return {"status": "REBASE_TEST_FAILED", "snapshot": str(snap), "new_head": _git(root, "rev-parse", "HEAD")}
        admission_ok, admission_output = _admission(root)
        if not admission_ok:
            snap = snapshot(root, checkpoint_root, {**metadata, "skip_remote_lookup": True, "remote_sha_override": remote_sha, "admission_result": "FAIL"}, report_path, reason="REBASE_ADMISSION_FAILED")
            return {"status": "CONTROL_PLANE_ADMISSION_BLOCKED", "snapshot": str(snap), "admission": admission_output[-4000:]}
        relation = "FAST_FORWARD_AFTER_SAFE_REBASE"
    else:
        snap = snapshot(root, checkpoint_root, {**metadata, "skip_remote_lookup": True, "remote_sha_override": remote_sha, "failure_classification": "REMOTE_BRANCH_CONFLICT"}, reason="REMOTE_BRANCH_CONFLICT")
        return {"status": "REMOTE_BRANCH_CONFLICT", "snapshot": str(snap), "head_sha": head, "remote_sha": remote_sha}

    refspec = f"HEAD:refs/heads/{branch}"
    dry = _run(root, ["git", "push", "--dry-run", remote, refspec], check=False, timeout=1800)
    if dry.returncode:
        reason = _classify_transport_failure(dry.stdout + dry.stderr)
        snap = snapshot(root, checkpoint_root, {**metadata, "skip_remote_lookup": True, "remote_sha_override": remote_sha, "failure_classification": reason}, reason=reason)
        return {"status": reason, "snapshot": str(snap), "details": _redact(dry.stderr or dry.stdout)[-2000:]}
    pushed = _run(root, ["git", "push", remote, refspec], check=False, timeout=1800)
    if pushed.returncode:
        reason = _classify_transport_failure(pushed.stdout + pushed.stderr)
        snap = snapshot(root, checkpoint_root, {**metadata, "skip_remote_lookup": True, "remote_sha_override": remote_sha, "failure_classification": reason}, reason=reason)
        return {"status": reason, "snapshot": str(snap), "details": _redact(pushed.stderr or pushed.stdout)[-2000:]}
    try:
        verified = _remote_sha(root, remote, branch)
    except GuardError as exc:
        snap = snapshot(root, checkpoint_root, {**metadata, "skip_remote_lookup": True, "failure_classification": "REMOTE_SHA_VERIFICATION_BLOCKED"}, reason="REMOTE_SHA_VERIFICATION_BLOCKED")
        return {"status": "REMOTE_SHA_VERIFICATION_BLOCKED", "snapshot": str(snap), "details": _redact(str(exc))[-2000:]}
    if verified != _git(root, "rev-parse", "HEAD"):
        snap = snapshot(root, checkpoint_root, {**metadata, "skip_remote_lookup": True, "remote_sha_override": verified, "failure_classification": "REMOTE_SHA_MISMATCH"}, reason="REMOTE_SHA_MISMATCH")
        return {"status": "REMOTE_SHA_MISMATCH", "snapshot": str(snap), "remote_sha": verified}
    _refresh_pending_queue(checkpoint_root, root, branch, verified, "PUSHED_AND_REMOTE_SHA_VERIFIED")
    return {"status": "PUSHED_AND_REMOTE_SHA_VERIFIED", "push_relation": relation, "remote_sha": verified, "ci_status": "PENDING"}


def record_ci(checkpoint_root: Path, commit_sha: str, status: str,
              run_url: str = "", raw_log: Path | None = None) -> dict[str, Any]:
    if not SHA40.fullmatch(commit_sha):
        raise GuardError("CI_COMMIT_SHA_INVALID")
    if status not in {"PASS", "FAIL", "UNAVAILABLE", "PENDING"}:
        raise GuardError("CI_STATUS_INVALID")
    _assert_no_secret_values({"run_url": run_url})
    record: dict[str, Any] = {
        "commit_sha": commit_sha, "status": status, "run_url": _redact(run_url),
        "recorded_at_utc": datetime.now(timezone.utc).isoformat(),
        "continue_research": status == "FAIL",
    }
    checkpoint_root.mkdir(parents=True, exist_ok=True)
    if raw_log and raw_log.is_file():
        record["raw_log_sha256"] = _hash(raw_log)
        record["raw_log_bytes"] = raw_log.stat().st_size
        record["raw_log_sensitive"] = _contains_sensitive_content(raw_log)
        if record["raw_log_sensitive"]:
            record["raw_log_path"] = "WITHHELD_SENSITIVE_CONTENT"
        else:
            evidence_dir = checkpoint_root / "ci_raw_evidence"
            evidence_dir.mkdir(parents=True, exist_ok=True)
            saved_log = evidence_dir / f"{commit_sha}_{record['raw_log_sha256'][:12]}.log"
            shutil.copy2(raw_log, saved_log)
            record["raw_log_path"] = str(saved_log)
    path = checkpoint_root / "ci_results.jsonl"
    with path.open("a", encoding="utf-8") as stream:
        stream.write(json.dumps(record, ensure_ascii=False, sort_keys=True) + "\n")
    queue_path = checkpoint_root / "PENDING_COMMITS_QUEUE.json"
    if queue_path.is_file():
        queue = json.loads(queue_path.read_text(encoding="utf-8"))
        changed = False
        for row in queue.get("commits", []):
            if row.get("commit_sha") == commit_sha:
                row["ci_status"] = status
                row["ci_run_url"] = record["run_url"]
                if record.get("raw_log_sha256"):
                    row["ci_raw_log_sha256"] = record["raw_log_sha256"]
                    row["ci_raw_log_bytes"] = record["raw_log_bytes"]
                    row["ci_raw_log_sensitive"] = record["raw_log_sensitive"]
                changed = True
        if changed:
            queue["updated_at_utc"] = datetime.now(timezone.utc).isoformat()
            _atomic_json(queue_path, queue)
    return record


def resume_queue(root: Path, checkpoint_root: Path, *, remote: str = "origin",
                 target_branch: str | None = None, allow_local_fixture_remote: bool = False) -> dict[str, Any]:
    """Restore and safely retry queued local commits after remote access returns."""
    root = repository_root(root)
    checkpoint_root = checkpoint_root.resolve()
    queue_path = checkpoint_root / "PENDING_COMMITS_QUEUE.json"
    if not queue_path.is_file():
        return {"status": "PENDING_QUEUE_EMPTY"}
    queue = json.loads(queue_path.read_text(encoding="utf-8"))
    terminal = {"PUSHED_AND_REMOTE_SHA_VERIFIED", "ALREADY_INCLUDED_BY_REMOTE", "ALREADY_AT_REMOTE_HEAD"}
    pending = [row for row in queue.get("commits", [])
               if row.get("push_status") not in terminal
               and (target_branch is None or row.get("branch") == target_branch)]
    if not pending:
        return {"status": "PENDING_QUEUE_EMPTY"}
    branches = {row.get("branch") for row in pending}
    if len(branches) != 1 or not next(iter(branches)):
        return {"status": "PENDING_QUEUE_BRANCH_AMBIGUOUS", "branches": sorted(str(x) for x in branches)}
    branch = next(iter(branches))
    tips = []
    for row in pending:
        sha = row.get("commit_sha", "")
        if not SHA40.fullmatch(sha):
            return {"status": "PENDING_QUEUE_SHA_INVALID", "commit_sha": sha}
        exists = _run(root, ["git", "cat-file", "-e", f"{sha}^{{commit}}"], check=False)
        if exists.returncode:
            bundle = Path(row.get("bundle_location", "")).resolve()
            try:
                bundle.relative_to(checkpoint_root)
            except ValueError:
                return {"status": "PENDING_BUNDLE_OUTSIDE_CHECKPOINT_ROOT", "commit_sha": sha}
            if not bundle.is_file():
                return {"status": "PENDING_BUNDLE_MISSING", "commit_sha": sha}
            verified = _run(root, ["git", "bundle", "verify", str(bundle)], check=False, timeout=2400)
            if verified.returncode:
                return {"status": "PENDING_BUNDLE_INVALID", "commit_sha": sha}
            import_ref = f"refs/codex/delivery-guard/recovered/{sha}"
            fetched = _run(root, ["git", "fetch", str(bundle), f"{sha}:{import_ref}"], check=False, timeout=2400)
            if fetched.returncode:
                return {"status": "PENDING_COMMIT_RESTORE_FAILED", "commit_sha": sha, "details": _redact(fetched.stderr)[-1200:]}
        tips.append((sha, row))
    tip_rows = [(sha, row) for sha, row in tips
                if not any(sha != other and _is_ancestor(root, sha, other) for other, _ in tips)]
    if len(tip_rows) != 1:
        return {"status": "PENDING_QUEUE_COMMIT_GRAPH_AMBIGUOUS", "tips": [sha for sha, _ in tip_rows]}
    tip, metadata_row = tip_rows[0]
    if not all(_is_ancestor(root, sha, tip) for sha, _ in tips):
        return {"status": "PENDING_QUEUE_COMMIT_GRAPH_DISCONNECTED", "tip": tip}

    head = _git(root, "rev-parse", "HEAD")
    if head != tip:
        if _is_ancestor(root, head, tip):
            status = _git(root, "status", "--porcelain=v1")
            if status:
                return {"status": "CANONICAL_WORKTREE_DIRTY", "head_sha": head, "candidate_sha": tip}
            merged = _run(root, ["git", "merge", "--ff-only", tip], check=False)
            if merged.returncode:
                return {"status": "PENDING_COMMIT_FAST_FORWARD_FAILED", "head_sha": head, "candidate_sha": tip, "details": _redact(merged.stderr)[-1200:]}
        elif not _is_ancestor(root, tip, head):
            return {"status": "PENDING_COMMIT_DIVERGED_FROM_WRITER", "head_sha": head, "candidate_sha": tip}

    metadata = {
        "remote": remote, "target_branch": branch,
        "source_sha": metadata_row.get("source_sha"),
        "issue_id": metadata_row.get("issue_id", "UNKNOWN"),
        "root_cause": metadata_row.get("root_cause", "NOT_RECORDED"),
        "test_result": metadata_row.get("test_result", "NOT_RECORDED"),
        "admission_result": metadata_row.get("admission_result", "NOT_RUN"),
        "test_commands": metadata_row.get("test_commands", []),
        "test_report": metadata_row.get("test_report_location"),
        "evidence_ref": metadata_row.get("evidence_ref", "NOT_RECORDED"),
        "repository": metadata_row.get("repository"),
        "next_action": "Complete exact-HEAD CI and record its result before continuing the next repair stage.",
    }
    if not str(metadata["test_result"]).upper().startswith("PASS"):
        return {"status": "PENDING_COMMIT_TESTS_NOT_PASS", "commit_sha": tip,
                "test_result": metadata["test_result"]}
    return push_stage(root, checkpoint_root, metadata, resume=True,
                      allow_local_fixture_remote=allow_local_fixture_remote)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="operation", required=True)
    commit = sub.add_parser("commit-stage")
    commit.add_argument("--repo", default=".")
    commit.add_argument("--checkpoint-root", required=True)
    commit.add_argument("--manifest", required=True)
    push = sub.add_parser("push-stage")
    push.add_argument("--repo", default=".")
    push.add_argument("--checkpoint-root", required=True)
    push.add_argument("--metadata", required=True)
    push.add_argument("--resume", action="store_true", help="allow a safe rebase only after bundling and rerunning tests/Admission")
    ci = sub.add_parser("record-ci")
    ci.add_argument("--checkpoint-root", required=True)
    ci.add_argument("--commit-sha", required=True)
    ci.add_argument("--status", choices=("PASS", "FAIL", "UNAVAILABLE", "PENDING"), required=True)
    ci.add_argument("--run-url", default="")
    ci.add_argument("--raw-log", type=Path)
    resume = sub.add_parser("resume-queue")
    resume.add_argument("--repo", default=".")
    resume.add_argument("--checkpoint-root", required=True)
    resume.add_argument("--remote", default="origin")
    resume.add_argument("--target-branch")
    args = parser.parse_args(argv)
    try:
        if args.operation == "commit-stage":
            root = repository_root(Path(args.repo))
            cp = Path(args.checkpoint_root).resolve()
            with writer_lock(root, cp):
                result = commit_stage(root, cp, json.loads(Path(args.manifest).read_text(encoding="utf-8")))
        elif args.operation == "push-stage":
            root = repository_root(Path(args.repo))
            cp = Path(args.checkpoint_root).resolve()
            metadata = json.loads(Path(args.metadata).read_text(encoding="utf-8"))
            with writer_lock(root, cp):
                result = push_stage(root, cp, metadata, resume=args.resume)
        elif args.operation == "record-ci":
            result = record_ci(Path(args.checkpoint_root).resolve(), args.commit_sha, args.status, args.run_url, args.raw_log)
        else:
            root = repository_root(Path(args.repo))
            cp = Path(args.checkpoint_root).resolve()
            with writer_lock(root, cp):
                result = resume_queue(root, cp, remote=args.remote, target_branch=args.target_branch)
        print(json.dumps(result, ensure_ascii=False, sort_keys=True))
        if args.operation == "record-ci":
            return 0
        return 0 if result.get("status", "").endswith(("PASS", "VERIFIED", "COMMITTED_PENDING_STATE_ADMISSION_AND_PUSH")) or result.get("status") in {"PUSHED_AND_REMOTE_SHA_VERIFIED", "ALREADY_INCLUDED_BY_REMOTE", "ALREADY_AT_REMOTE_HEAD"} else 3
    except (GuardError, OSError, ValueError, json.JSONDecodeError, subprocess.SubprocessError) as exc:
        print("DELIVERY_GUARD_BLOCKED: " + str(exc), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
