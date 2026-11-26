"""Local Git fixture coverage for the delivery guard; no GitHub credentials/network."""
from __future__ import annotations

import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from io import StringIO
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent))
import delivery_guard as guard  # noqa: E402


def run(root: Path, *args: str, check: bool = True) -> str:
    result = subprocess.run(["git", *args], cwd=root, capture_output=True, text=True, check=False)
    if check and result.returncode:
        raise AssertionError(result.stderr)
    return result.stdout.strip()


def init_repo(root: Path, *, bare: bool = False) -> None:
    root.mkdir(parents=True, exist_ok=True)
    run(root, "init", "--bare" if bare else "--initial-branch=main")
    if not bare:
        run(root, "config", "user.name", "Delivery Guard Test")
        run(root, "config", "user.email", "delivery-guard@example.invalid")


def commit_file(root: Path, name: str, contents: str, subject: str) -> str:
    path = root / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(contents, encoding="utf-8")
    run(root, "add", "--", name)
    run(root, "commit", "-m", subject)
    return run(root, "rev-parse", "HEAD")


class SnapshotTests(unittest.TestCase):
    def test_snapshot_has_verified_bundle_queue_manifest_and_sensitive_skip(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            base = Path(td)
            repo = base / "repo"
            init_repo(repo)
            commit_file(repo, "tracked.txt", "base\n", "base")
            base_sha = run(repo, "rev-parse", "HEAD")
            commit_file(repo, "tracked.txt", "changed\n", "change")
            (repo / "safe.md").write_text("safe report\n", encoding="utf-8")
            (repo / "token.txt").write_text("credential placeholder must remain private\n", encoding="utf-8")
            cp = base / "checkpoints"

            folder = guard.snapshot(repo, cp, {
                "target_branch": guard.AUTHORIZED_PR_BRANCH, "issue_id": "H22",
                "source_sha": base_sha, "test_result": "PASS",
                "admission_result": "PASS", "root_cause": "push path unavailable",
            }, reason="AUTH_INVALID")

            required = {
                "goal_checkpoint.bundle", "pending_commits.patch", "pending_commits.json",
                "WORK_STATE.json", "TEST_REPORT.md", "SHA256SUMS.txt",
            }
            self.assertTrue(required.issubset({p.name for p in folder.iterdir()}))
            run(repo, "bundle", "verify", str(folder / "goal_checkpoint.bundle"))
            self.assertTrue((folder / "untracked/safe.md").is_file())
            self.assertFalse((folder / "untracked/token.txt").exists())
            pending = json.loads((folder / "pending_commits.json").read_text())
            self.assertEqual(pending["push_status"], "AUTH_INVALID")
            self.assertEqual(pending["commits"][-1]["issue_id"], "H22")
            skipped = pending["safe_untracked"]["skipped"]
            self.assertEqual(skipped[0]["reason"], "sensitive_path_or_name")
            queue = json.loads((cp / "PENDING_COMMITS_QUEUE.json").read_text())
            self.assertEqual(queue["commits"][0]["push_status"], "AUTH_INVALID")
            sums = (folder / "SHA256SUMS.txt").read_text(encoding="utf-8")
            for row in sums.splitlines():
                digest, rel = row.split("  ", 1)
                self.assertEqual(guard._hash(folder / rel), digest)

    def test_writer_lock_rejects_noncanonical_worktree(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            base = Path(td)
            repo = base / "repo"
            init_repo(repo)
            commit_file(repo, "file.txt", "x", "base")
            cp = base / "checkpoints"
            cp.mkdir()
            (cp / "PRODUCTION_WORKSPACE_LOCK.json").write_text(json.dumps({
                "active_writer_worktree": str(repo), "active_local_branch": "main",
            }))
            with guard.writer_lock(repo, cp):
                pass
            other = base / "other"
            init_repo(other)
            with self.assertRaisesRegex(guard.GuardError, "NOT_CANONICAL_WORKTREE"):
                with guard.writer_lock(other, cp):
                    self.fail("noncanonical writer acquired the lock")


class CommitTests(unittest.TestCase):
    def test_commit_stage_runs_tests_and_keeps_commit_scope_exact(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            base = Path(td)
            repo = base / "repo"
            init_repo(repo)
            source = commit_file(repo, "guard.py", "before\n", "base")
            (repo / "guard.py").write_text("after\n", encoding="utf-8")
            (repo / "test_guard.py").write_text("assert 1 + 1 == 2\n", encoding="utf-8")
            unrelated = repo / "unrelated.cpython-312.pyc"
            unrelated.write_bytes(b"preexisting untracked cache")
            result = guard.commit_stage(repo, base / "checkpoint", {
                "issue_id": "H22", "root_cause": "missing durable push failure checkpoint",
                "source_sha": source, "subject": "fix(ci): add delivery checkpoint guard",
                "files": ["guard.py", "test_guard.py"],
                "test_commands": [[sys.executable, "test_guard.py"]],
                "evidence_ref": "local fixture tests in test_delivery_guard.py",
            })
            self.assertEqual(result["status"], "COMMITTED_PENDING_STATE_ADMISSION_AND_PUSH")
            self.assertEqual(run(repo, "diff", "--name-only"), "")
            self.assertEqual(run(repo, "show", "-s", "--format=%P", "HEAD"), source)
            body = run(repo, "show", "-s", "--format=%B", "HEAD")
            for fragment in ("Issue: H22", "Root-Cause:", f"Source-SHA: {source}", f"Parent-SHA: {source}", "Tests: PASS", "Evidence:"):
                self.assertIn(fragment, body)
            report = Path(result["test_report"])
            self.assertIn("TESTS_PASS", report.read_text(encoding="utf-8"))
            self.assertEqual(unrelated.read_bytes(), b"preexisting untracked cache")
            self.assertIn("?? unrelated.cpython-312.pyc", run(repo, "status", "--short"))

    def test_failed_targeted_test_makes_checkpoint_without_commit(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            base = Path(td)
            repo = base / "repo"
            init_repo(repo)
            source = commit_file(repo, "guard.py", "before\n", "base")
            (repo / "guard.py").write_text("after\n", encoding="utf-8")
            result = guard.commit_stage(repo, base / "checkpoint", {
                "issue_id": "H22", "root_cause": "fixture failure", "source_sha": source,
                "subject": "fix(ci): guarded stage", "files": ["guard.py"],
                "test_commands": [[sys.executable, "-c", "raise SystemExit(7)"]],
                "evidence_ref": "deterministic failing fixture",
            })
            self.assertEqual(result["status"], "TEST_FAILED")
            self.assertEqual(run(repo, "rev-parse", "HEAD"), source)
            self.assertIn("TESTS_FAIL", Path(result["test_report"]).read_text())
            run(repo, "bundle", "verify", str(Path(result["snapshot"]) / "goal_checkpoint.bundle"))

    def test_credential_in_source_is_rejected_without_losing_worktree_file(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            base = Path(td)
            repo = base / "repo"
            init_repo(repo)
            source = commit_file(repo, "guard.py", "before\n", "base")
            (repo / "guard.py").write_text("after\n", encoding="utf-8")
            secret = "test-secret-value-never-to-be-recorded"
            with mock.patch.dict(os.environ, {"CDD_TEST_TOKEN": secret}):
                (repo / "notes.txt").write_text("accidental " + secret + "\n", encoding="utf-8")
                with self.assertRaisesRegex(guard.GuardError, "SECRET_CONTENT_IN_STAGED_PATHS"):
                    guard.commit_stage(repo, base / "checkpoint", {
                        "issue_id": "H22", "root_cause": "fixture", "source_sha": source,
                        "subject": "fix(ci): do not commit credentials",
                        "files": ["guard.py", "notes.txt"],
                        "test_commands": [[sys.executable, "-c", "assert True"]],
                        "evidence_ref": "local secret-scan regression",
                    })
            self.assertEqual(run(repo, "rev-parse", "HEAD"), source)
            self.assertIn(secret, (repo / "notes.txt").read_text(encoding="utf-8"))
            self.assertEqual(run(repo, "diff", "--cached", "--name-only"), "")


class PushTests(unittest.TestCase):
    def make_remote(self, base: Path) -> tuple[Path, Path, str]:
        remote = base / "remote.git"
        seed = base / "seed"
        work = base / "work"
        init_repo(remote, bare=True)
        init_repo(seed)
        base_sha = commit_file(seed, "base.txt", "base\n", "base")
        run(seed, "remote", "add", "origin", str(remote))
        run(seed, "push", "origin", "main:main")
        run(seed, "branch", guard.AUTHORIZED_PR_BRANCH, base_sha)
        run(seed, "push", "origin", f"{guard.AUTHORIZED_PR_BRANCH}:refs/heads/{guard.AUTHORIZED_PR_BRANCH}")
        run(remote, "symbolic-ref", "HEAD", "refs/heads/main")
        run(base, "clone", str(remote), str(work))
        run(work, "config", "user.name", "Delivery Guard Test")
        run(work, "config", "user.email", "delivery-guard@example.invalid")
        run(work, "checkout", "-b", "codex/stage")
        return remote, work, base_sha

    def meta(self, base_sha: str) -> dict[str, object]:
        return {
            "remote": "origin", "target_branch": guard.AUTHORIZED_PR_BRANCH, "source_sha": base_sha,
            "issue_id": "H22", "root_cause": "fixture", "test_result": "PASS",
            "admission_result": "PASS", "test_commands": [[sys.executable, "-c", "assert True"]],
        }

    def test_fast_forward_push_verifies_remote_sha(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            base = Path(td)
            remote, work, base_sha = self.make_remote(base)
            local_sha = commit_file(work, "fix.txt", "fixed\n", "guarded fix")
            with mock.patch.object(guard, "_admission", return_value=(True, "CONTROL_PLANE_ADMISSION=PASS")):
                result = guard.push_stage(work, base / "checkpoints", self.meta(base_sha), allow_local_fixture_remote=True)
            self.assertEqual(result["status"], "PUSHED_AND_REMOTE_SHA_VERIFIED")
            self.assertEqual(result["remote_sha"], run(work, "rev-parse", "HEAD"))
            self.assertEqual(guard._remote_sha(work, "origin", guard.AUTHORIZED_PR_BRANCH), result["remote_sha"])

    def test_denied_actual_push_preserves_bundle_and_queue(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            base = Path(td)
            remote, work, base_sha = self.make_remote(base)
            local_sha = commit_file(work, "fix.txt", "fixed\n", "guarded fix")
            hook = remote / "hooks/pre-receive"
            hook.write_text("#!/bin/sh\necho 'simulated write denial' >&2\nexit 1\n", encoding="utf-8")
            hook.chmod(0o755)
            cp = base / "checkpoints"
            with mock.patch.object(guard, "_admission", return_value=(True, "CONTROL_PLANE_ADMISSION=PASS")):
                result = guard.push_stage(work, cp, self.meta(base_sha), allow_local_fixture_remote=True)
            self.assertEqual(result["status"], "PUSH_FAILED_UNCLASSIFIED")
            folder = Path(result["snapshot"])
            for name in ("goal_checkpoint.bundle", "pending_commits.patch", "pending_commits.json", "WORK_STATE.json", "TEST_REPORT.md", "SHA256SUMS.txt"):
                self.assertTrue((folder / name).is_file(), name)
            run(work, "bundle", "verify", str(folder / "goal_checkpoint.bundle"))
            queue = json.loads((cp / "PENDING_COMMITS_QUEUE.json").read_text())
            self.assertEqual(queue["commits"][0]["push_status"], "PUSH_FAILED_UNCLASSIFIED")
            self.assertEqual(guard._remote_sha(work, "origin", guard.AUTHORIZED_PR_BRANCH), base_sha)
            hook.unlink()
            with mock.patch.object(guard, "_admission", return_value=(True, "CONTROL_PLANE_ADMISSION=PASS")):
                resumed = guard.resume_queue(work, cp, allow_local_fixture_remote=True)
            self.assertEqual(resumed["status"], "PUSHED_AND_REMOTE_SHA_VERIFIED")
            updated_queue = json.loads((cp / "PENDING_COMMITS_QUEUE.json").read_text())
            self.assertEqual(updated_queue["commits"][0]["push_status"], "PUSHED_AND_REMOTE_SHA_VERIFIED")
            raw_log = base / "ci.log"
            raw_log.write_text("required check failed; continue local diagnosis\n", encoding="utf-8")
            ci = guard.record_ci(cp, local_sha, "FAIL", "https://github.com/example/actions/runs/123", raw_log)
            self.assertTrue(ci["continue_research"])
            self.assertTrue(Path(ci["raw_log_path"]).is_file())
            updated_queue = json.loads((cp / "PENDING_COMMITS_QUEUE.json").read_text())
            self.assertEqual(updated_queue["commits"][0]["ci_status"], "FAIL")

    def test_resume_restores_a_missing_commit_from_bundle_before_push(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            base = Path(td)
            remote, original_work, base_sha = self.make_remote(base)
            local_sha = commit_file(original_work, "recovered.txt", "durable\n", "recoverable change")
            cp = base / "checkpoints"
            denied = remote / "hooks/pre-receive"
            denied.write_text("#!/bin/sh\nexit 1\n", encoding="utf-8")
            denied.chmod(0o755)
            with mock.patch.object(guard, "_admission", return_value=(True, "CONTROL_PLANE_ADMISSION=PASS")):
                result = guard.push_stage(original_work, cp, self.meta(base_sha), allow_local_fixture_remote=True)
            self.assertEqual(result["status"], "PUSH_FAILED_UNCLASSIFIED")

            denied.unlink()
            restored_work = base / "restored-work"
            run(base, "clone", "--branch", "main", str(remote), str(restored_work))
            run(restored_work, "config", "user.name", "Delivery Guard Test")
            run(restored_work, "config", "user.email", "delivery-guard@example.invalid")
            run(restored_work, "checkout", "-b", "codex/stage")
            missing_commit = subprocess.run(["git", "cat-file", "-e", f"{local_sha}^{{commit}}"], cwd=restored_work, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False)
            self.assertNotEqual(missing_commit.returncode, 0)
            with mock.patch.object(guard, "_admission", return_value=(True, "CONTROL_PLANE_ADMISSION=PASS")):
                resumed = guard.resume_queue(restored_work, cp, allow_local_fixture_remote=True)
            self.assertEqual(resumed["status"], "PUSHED_AND_REMOTE_SHA_VERIFIED")
            self.assertEqual(resumed["remote_sha"], local_sha)
            self.assertEqual(run(restored_work, "rev-parse", "HEAD"), local_sha)

    def test_divergence_is_held_without_push_or_rewrite(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            base = Path(td)
            remote, work, base_sha = self.make_remote(base)
            commit_file(work, "local.txt", "local\n", "local change")
            remote_clone = base / "remote-clone"
            run(base, "clone", str(remote), str(remote_clone))
            run(remote_clone, "config", "user.name", "Delivery Guard Test")
            run(remote_clone, "config", "user.email", "delivery-guard@example.invalid")
            run(remote_clone, "checkout", "-b", guard.AUTHORIZED_PR_BRANCH, f"origin/{guard.AUTHORIZED_PR_BRANCH}")
            remote_sha = commit_file(remote_clone, "remote.txt", "remote\n", "remote change")
            run(remote_clone, "push", "origin", f"HEAD:refs/heads/{guard.AUTHORIZED_PR_BRANCH}")
            cp = base / "checkpoints"
            with mock.patch.object(guard, "_admission", return_value=(True, "CONTROL_PLANE_ADMISSION=PASS")):
                result = guard.push_stage(work, cp, self.meta(base_sha), allow_local_fixture_remote=True)
            self.assertEqual(result["status"], "REMOTE_BRANCH_CONFLICT")
            self.assertEqual(run(work, "rev-parse", "HEAD"), run(work, "rev-parse", "codex/stage"))
            self.assertEqual(guard._remote_sha(work, "origin", guard.AUTHORIZED_PR_BRANCH), remote_sha)
            run(work, "bundle", "verify", str(Path(result["snapshot"]) / "goal_checkpoint.bundle"))

    def test_resume_rebases_nonconflicting_change_runs_tests_then_pushes(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            base = Path(td)
            remote, work, base_sha = self.make_remote(base)
            local_sha = commit_file(work, "local.txt", "local\n", "local change")
            remote_clone = base / "remote-clone"
            run(base, "clone", str(remote), str(remote_clone))
            run(remote_clone, "config", "user.name", "Delivery Guard Test")
            run(remote_clone, "config", "user.email", "delivery-guard@example.invalid")
            run(remote_clone, "checkout", "-b", guard.AUTHORIZED_PR_BRANCH, f"origin/{guard.AUTHORIZED_PR_BRANCH}")
            remote_sha = commit_file(remote_clone, "remote.txt", "remote\n", "remote change")
            run(remote_clone, "push", "origin", f"HEAD:refs/heads/{guard.AUTHORIZED_PR_BRANCH}")
            meta = self.meta(base_sha)
            cp = base / "checkpoints"
            with mock.patch.object(guard, "_admission", return_value=(True, "CONTROL_PLANE_ADMISSION=PASS")):
                result = guard.push_stage(work, cp, meta, resume=True, allow_local_fixture_remote=True)
            self.assertEqual(result["status"], "PUSHED_AND_REMOTE_SHA_VERIFIED")
            self.assertEqual(result["push_relation"], "FAST_FORWARD_AFTER_SAFE_REBASE")
            self.assertNotEqual(run(work, "rev-parse", "HEAD"), local_sha)
            self.assertTrue(guard._is_ancestor(work, remote_sha, run(work, "rev-parse", "HEAD")))
            self.assertEqual(guard._remote_sha(work, "origin", guard.AUTHORIZED_PR_BRANCH), run(work, "rev-parse", "HEAD"))

    def test_github_ssh_remote_is_rejected_before_auth_or_push(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            repo = Path(td) / "repo"
            init_repo(repo)
            head = commit_file(repo, "source.py", "x = 1\n", "base")
            run(repo, "remote", "add", "origin", "git@github.com:pan277942135/Yujian_App.git")
            metadata = {
                "repository": guard.AUTHORIZED_REPOSITORY,
                "target_branch": guard.AUTHORIZED_PR_BRANCH,
                "source_sha": head,
                "test_result": "PASS",
            }
            with self.assertRaisesRegex(guard.GuardError, "GIT_REMOTE_USES_SSH"):
                guard.push_stage(repo, Path(td) / "checkpoints", metadata)


class ClassificationTests(unittest.TestCase):
    def test_transport_failure_classes_are_distinct(self) -> None:
        self.assertEqual(guard._classify_transport_failure("HTTP 401 authentication failed"), "AUTH_INVALID")
        self.assertEqual(guard._classify_transport_failure("CONNECT tunnel failed, response 403"), "NETWORK_OR_PROXY_BLOCKED")
        self.assertEqual(guard._classify_transport_failure("permission to repo denied to user"), "REPOSITORY_PERMISSION_DENIED")
        self.assertEqual(guard._classify_transport_failure("non-fast-forward"), "REMOTE_BRANCH_CONFLICT")

    def test_ci_failure_recording_returns_success_and_keeps_continue_flag(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            output = StringIO()
            commit_sha = "a" * 40
            with redirect_stdout(output):
                rc = guard.main([
                    "record-ci", "--checkpoint-root", td, "--commit-sha", commit_sha,
                    "--status", "FAIL", "--run-url", "https://github.com/example/actions/runs/4",
                ])
            self.assertEqual(rc, 0)
            record = json.loads(output.getvalue())
            self.assertTrue(record["continue_research"])


if __name__ == "__main__":
    unittest.main()
