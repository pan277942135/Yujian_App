"""Git integration coverage for Work State commit and recovery semantics."""
from __future__ import annotations

import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

VALIDATOR_SOURCE = Path(__file__).resolve().parents[1] / "scripts/validate_work_execution_state.py"


def git(repo: Path, *args: str) -> str:
    return subprocess.check_output(
        ["git", *args], cwd=repo, text=True, stderr=subprocess.PIPE
    ).strip()


class WorkExecutionStateCheckpointTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory(prefix="work-state-checkpoint-")
        self.repo = Path(self.temp.name)
        git(self.repo, "init", "--initial-branch=main")
        git(self.repo, "config", "user.name", "Work Checkpoint Test")
        git(self.repo, "config", "user.email", "work-checkpoint-test@example.invalid")

        (self.repo / "scripts").mkdir()
        (self.repo / "harness").mkdir()
        (self.repo / "app/src/main").mkdir(parents=True)
        (self.repo / "scripts/validate_work_execution_state.py").write_bytes(
            VALIDATOR_SOURCE.read_bytes()
        )
        (self.repo / "harness/PHASE_TASK_STATE_MACHINE.yaml").write_text(
            "schema_version: '1.0'\ntasks:\n  P0-01:\n    phase: P0\n    depends_on: []\n",
            encoding="utf-8",
        )
        (self.repo / "harness/UNIFIED_CONTRACT_REGISTRY.yaml").write_text(
            "schema_version: '1.0'\nfeature_contracts:\n  test_feature:\n"
            "    contract_id: FC-test_feature\ntask_contract_bindings:\n"
            "  P0-01:\n    contract_refs: [FC-test_feature]\n",
            encoding="utf-8",
        )
        git(self.repo, "add", "scripts", "harness")
        git(self.repo, "commit", "-m", "add test control plane")
        self.main_sha = git(self.repo, "rev-parse", "HEAD")
        git(self.repo, "update-ref", "refs/remotes/origin/main", self.main_sha)
        git(self.repo, "switch", "-c", "work/checkpoint-recovery")

        (self.repo / "app/src/main/Example.kt").write_text(
            "class Example { val value = 1 }\n", encoding="utf-8"
        )
        git(self.repo, "add", "app/src/main/Example.kt")
        git(self.repo, "commit", "-m", "commit tested source")
        self.source_sha = git(self.repo, "rev-parse", "HEAD")

        self.state_path = self.repo / "WORK_EXECUTION_STATE.json"
        self.state = {
            "schema_version": "1.0",
            "work_id": "WORK-CHECKPOINT-001",
            "repository": "pan277942135/Yujian_App",
            "branch": "work/checkpoint-recovery",
            "control_plane_revision": self.main_sha,
            "base_branch": "main",
            "base_sha": self.main_sha,
            "phase": "P0",
            "task_id": "P0-01",
            "stage": "E0",
            "status": "RUNNING",
            "source_sha": self.source_sha,
            "checkpoint_updated_at": "2026-10-16T12:00:00Z",
            "next_action": "Validate the committed source snapshot.",
            "dependencies": [],
            "blockers": [],
            "evidence": {},
        }
        self.write_state()
        self.first_checkpoint = self.commit_state("record initial Work checkpoint")

    def tearDown(self) -> None:
        self.temp.cleanup()

    def write_state(self) -> None:
        self.state_path.write_text(
            json.dumps(self.state, indent=2) + "\n", encoding="utf-8"
        )

    def commit_state(self, message: str) -> str:
        self.write_state()
        git(self.repo, "add", "WORK_EXECUTION_STATE.json")
        git(self.repo, "commit", "-m", message)
        return git(self.repo, "rev-parse", "HEAD")

    def validate(self) -> subprocess.CompletedProcess[str]:
        env = os.environ.copy()
        env.pop("GITHUB_HEAD_REF", None)
        env.pop("GITHUB_REF_NAME", None)
        env["PYTHONDONTWRITEBYTECODE"] = "1"
        return subprocess.run(
            [sys.executable, str(self.repo / "scripts/validate_work_execution_state.py")],
            cwd=self.repo,
            env=env,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )

    def test_state_submission_separates_source_state_and_head_shas(self) -> None:
        result = self.validate()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn(f"source_sha={self.source_sha}", result.stdout)
        self.assertIn(f"state_commit_sha={self.first_checkpoint}", result.stdout)
        self.assertIn(f"current_branch_head_sha={self.first_checkpoint}", result.stdout)
        self.assertIn(f"checkout_sha={self.first_checkpoint}", result.stdout)
        self.assertNotEqual(self.source_sha, self.first_checkpoint)

    def test_two_consecutive_state_updates_advance_only_state_commit(self) -> None:
        self.state["status"] = "VALIDATING"
        self.state["checkpoint_updated_at"] = "2026-10-16T12:01:00Z"
        second = self.commit_state("record validation checkpoint")
        self.state["status"] = "BLOCKED"
        self.state["checkpoint_updated_at"] = "2026-10-16T12:02:00Z"
        third = self.commit_state("record blocker checkpoint")

        result = self.validate()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn(f"source_sha={self.source_sha}", result.stdout)
        self.assertIn(f"state_commit_sha={third}", result.stdout)
        self.assertIn(f"current_branch_head_sha={third}", result.stdout)
        self.assertNotEqual(second, third)

    def test_checkpoint_restore_reuses_restored_state_and_git_head(self) -> None:
        self.state["status"] = "PASS"
        self.state["checkpoint_updated_at"] = "2026-10-16T12:03:00Z"
        self.commit_state("record later checkpoint")
        git(self.repo, "reset", "--hard", self.first_checkpoint)

        result = self.validate()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn(f"source_sha={self.source_sha}", result.stdout)
        self.assertIn(f"state_commit_sha={self.first_checkpoint}", result.stdout)
        self.assertIn(f"current_branch_head_sha={self.first_checkpoint}", result.stdout)

    def test_prior_state_only_commit_cannot_be_reused_as_source_sha(self) -> None:
        self.state["source_sha"] = self.first_checkpoint
        self.state["checkpoint_updated_at"] = "2026-10-16T12:04:00Z"
        self.commit_state("record invalid state-as-source claim")

        result = self.validate()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("source_sha cannot reference a commit that updates WORK_EXECUTION_STATE.json", result.stderr)
        self.assertIn("WORK_EXECUTION_STATE_VALIDATION=FAIL", result.stdout)

    def test_product_change_after_source_requires_revalidation(self) -> None:
        (self.repo / "app/src/main/Example.kt").write_text(
            "class Example { val value = 2 }\n", encoding="utf-8"
        )
        git(self.repo, "add", "app/src/main/Example.kt")
        git(self.repo, "commit", "-m", "change code after tested source")
        self.state["checkpoint_updated_at"] = "2026-10-16T12:05:00Z"
        self.commit_state("record stale source claim")

        result = self.validate()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("code or contract changes after source_sha require revalidation", result.stderr)


if __name__ == "__main__":
    unittest.main()
