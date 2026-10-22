from __future__ import annotations

import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

import yaml

from scripts import api28_batch_monitor as batch_monitor
from scripts import create_api28_integration_candidate as composer
from scripts.work_lifecycle import (
    assert_public_ledger_paths,
    candidate_eligibility,
    candidate_set_key,
    check_admission_identity,
    integration_outcome,
    integration_plan,
    reconcile_queue,
    validate_delivery,
    validate_feedback,
)

ROOT = Path(__file__).resolve().parents[1]
SHA_A = "a" * 40
SHA_B = "b" * 40
SHA_C = "c" * 40
APK_SHA = "d" * 64


def git(root: Path, *args: str, env: dict | None = None) -> str:
    result = subprocess.run(["git", *args], cwd=root, text=True, capture_output=True, env=env, check=True)
    return result.stdout.strip()


class WorkLifecycleGovernanceTests(unittest.TestCase):
    def test_freeze_candidate_and_production_verification_are_separate(self):
        policy = yaml.safe_load((ROOT / "harness/ANDROID_BATCH_ACCEPTANCE_POLICY_V1_1.yaml").read_text(encoding="utf-8"))
        model = policy["state_model"]
        freeze = model["freeze_candidate"]
        self.assertEqual(freeze["status"], "FROZEN_CANDIDATE")
        self.assertIn("PRODUCTION_VERIFIED", freeze["does_not_imply"])
        self.assertIn("FORMAL_ACCEPTANCE", freeze["does_not_imply"])
        self.assertIn("RELEASE_AUTHORIZATION", freeze["does_not_imply"])
        self.assertEqual(model["integration"]["blocked_status"], "BLOCKED")
        self.assertTrue(model["integration"]["preserve_candidate_for_retry"])
        evidence = model["evidence_scope"]
        self.assertTrue(evidence["exact_sha_required"])
        self.assertEqual(evidence["queued_or_skipped_job_result"], "NOT_RUN")
        self.assertEqual(evidence["maximum_queue_wait_minutes"], policy["batch"]["maximum_queue_wait_minutes"])
        self.assertEqual(evidence["exceeded_queue_wait_status"], "BLOCKED_INFRA")
        production = model["production_verification"]
        self.assertEqual(production["status"], "PRODUCTION_VERIFIED")
        self.assertFalse(production["pages_only_is_sufficient"])
        self.assertFalse(production["queued_or_skipped_api28_is_sufficient"])
        self.assertFalse(production["release_authorization_implied"])

    def test_admission_start_and_recovery_require_current_canonical_sha(self):
        self.assertEqual(check_admission_identity(registry_sha=SHA_A, canonical_sha=SHA_A, static_valid=True, control_change=False), [])
        self.assertEqual(check_admission_identity(registry_sha=SHA_A, canonical_sha=SHA_A, static_valid=True, control_change=False), [])
        self.assertTrue(check_admission_identity(registry_sha=None, canonical_sha=SHA_A, static_valid=True, control_change=False))
        self.assertTrue(check_admission_identity(registry_sha=SHA_B, canonical_sha=SHA_A, static_valid=True, control_change=False))
        self.assertTrue(check_admission_identity(registry_sha=SHA_A, canonical_sha=SHA_A, static_valid=False, control_change=False))

    def test_candidate_requires_trusted_labeler_admission_metadata_and_exact_checks(self):
        candidate = {
            "state": "open", "draft": False, "base_branch": "main",
            "head_repo": "pan277942135/Yujian_App", "base_repo": "pan277942135/Yujian_App",
            "author_association": "MEMBER", "labels": ["api28-ready"],
            "label_authorized": True, "task_id": "YUJIAN_WORK_X", "head_sha": SHA_A,
            "required_gates": ["normal-home-v1"], "depends_on_prs": [],
            "quick_check_results": {"control-plane-admission": "PASS", "Android CI build": "PASS"},
        }
        ok, reason = candidate_eligibility(candidate, approval_label="api28-ready", allowed_associations={"OWNER", "MEMBER", "COLLABORATOR"}, quick_checks={"control-plane-admission", "Android CI build"}, supported_gates={"normal-home-v1"})
        self.assertTrue(ok, reason)
        changed = {**candidate, "label_authorized": False}
        self.assertFalse(candidate_eligibility(changed, approval_label="api28-ready", allowed_associations={"MEMBER"}, quick_checks={"control-plane-admission"}, supported_gates={"normal-home-v1"})[0])
        fork = {**candidate, "head_repo": "someone/Yujian_App"}
        self.assertEqual(candidate_eligibility(fork, approval_label="api28-ready", allowed_associations={"MEMBER"}, quick_checks={"control-plane-admission"}, supported_gates={"normal-home-v1"})[1], "FORK_SOURCE_NOT_ALLOWED")

    def test_candidate_set_and_dependency_order_are_deterministic(self):
        rows = [
            {"pr_number": 14, "head_sha": SHA_B, "task_id": "TASK-B", "required_gates": ["normal-home-v1"], "depends_on_prs": [13]},
            {"pr_number": 13, "head_sha": SHA_A, "task_id": "TASK-A", "required_gates": ["normal-home-v1"], "depends_on_prs": []},
        ]
        plan = integration_plan(rows, scope_gates={"normal-home-v1"}, merged_prs=set())
        self.assertEqual([item["pr_number"] for item in plan["integrated"]], [13, 14])
        self.assertEqual(candidate_set_key(SHA_C, "CORE", rows), candidate_set_key(SHA_C, "CORE", list(reversed(rows))))
        changed = [{**rows[0], "depends_on_prs": []}, rows[1]]
        self.assertNotEqual(candidate_set_key(SHA_C, "CORE", rows), candidate_set_key(SHA_C, "CORE", changed))

    def test_incompatible_dependency_is_explicitly_blocked(self):
        rows = [
            {"pr_number": 14, "head_sha": SHA_B, "required_gates": ["normal-home-v1"], "depends_on_prs": [13]},
        ]
        plan = integration_plan(rows, scope_gates={"normal-home-v1"}, merged_prs=set())
        self.assertEqual(plan["integrated"], [])
        self.assertEqual(plan["blocked"][0]["status"], "BLOCKED_DEPENDENCY")
        scoped = integration_plan(rows, scope_gates={"camera-runtime-v1"}, merged_prs=set())
        self.assertEqual(scoped["deferred"][0]["status"], "DEFERRED_SCOPE")

    def test_composer_builds_same_integration_sha_for_same_candidates(self):
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            bare = base / "origin.git"
            seed = base / "seed"
            workspace = base / "workspace"
            bare.mkdir()
            seed.mkdir()
            git(bare, "init", "--bare", "--initial-branch=main")
            git(seed, "init", "--initial-branch=main")
            git(seed, "config", "user.name", "Governance Test")
            git(seed, "config", "user.email", "governance@example.invalid")
            (seed / "shared.txt").write_text("base\n", encoding="utf-8")
            git(seed, "add", "shared.txt")
            git(seed, "commit", "-m", "base")
            base_sha = git(seed, "rev-parse", "HEAD")
            git(seed, "remote", "add", "origin", str(bare))
            git(seed, "push", "origin", "main")
            for number, filename in ((13, "a.txt"), (14, "b.txt")):
                git(seed, "checkout", "-b", f"pr-{number}", base_sha)
                (seed / filename).write_text(f"PR {number}\n", encoding="utf-8")
                git(seed, "add", filename)
                git(seed, "commit", "-m", f"PR {number}")
                git(seed, "push", "origin", f"HEAD:refs/pull/{number}/head")
            git(seed, "checkout", "main")
            git(seed, "clone", str(bare), str(workspace))
            git(workspace, "config", "user.name", "Governance Test")
            git(workspace, "config", "user.email", "governance@example.invalid")
            git(workspace, "fetch", "origin", "main")
            candidates = [
                {"pr_number": 14, "head_sha": git(seed, "rev-parse", "pr-14"), "required_gates": ["normal-home-v1"], "depends_on_prs": [13]},
                {"pr_number": 13, "head_sha": git(seed, "rev-parse", "pr-13"), "required_gates": ["normal-home-v1"], "depends_on_prs": []},
            ]
            candidate_path = base / "candidates.json"
            candidate_path.write_text(json.dumps(candidates), encoding="utf-8")
            output_a, output_b = base / "a.json", base / "b.json"
            original_root = composer.ROOT
            original_argv = sys.argv
            try:
                composer.ROOT = workspace
                sys.argv = ["create_api28_integration_candidate.py", "--base-sha", base_sha, "--batch-id", "one", "--scope", "CORE", "--candidates", str(candidate_path), "--scope-gates", "normal-home-v1", "--output", str(output_a)]
                self.assertEqual(composer.main(), 0)
                sys.argv = ["create_api28_integration_candidate.py", "--base-sha", base_sha, "--batch-id", "two", "--scope", "CORE", "--candidates", str(candidate_path), "--scope-gates", "normal-home-v1", "--output", str(output_b)]
                self.assertEqual(composer.main(), 0)
            finally:
                composer.ROOT = original_root
                sys.argv = original_argv
            result_a = json.loads(output_a.read_text())
            result_b = json.loads(output_b.read_text())
            self.assertEqual(result_a["integration_sha"], result_b["integration_sha"])
            self.assertEqual([row["pr_number"] for row in result_a["integrated"]], [13, 14])
            self.assertEqual(result_a["base_sha"], base_sha)

    def test_ledger_cli_persists_superseded_and_final_candidate_records(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            ledger = root / "ledger"
            ledger.mkdir()
            candidates_dir = root / "inputs"
            candidates_dir.mkdir()
            observed_path = candidates_dir / "observed.json"
            observed_path.write_text("[17]")
            current_path = candidates_dir / "current.json"
            eligible_path = candidates_dir / "eligible.json"
            batch_path = candidates_dir / "batch.json"
            batch_output = candidates_dir / "batch-output.json"
            queue_output = candidates_dir / "queue-output.json"

            old_candidate = {
                "pr_number": 17, "head_sha": SHA_A, "task_id": "TASK-17",
                "required_gates": ["normal-home-v1"], "depends_on_prs": [], "initial_status": "QUEUED",
            }
            current_path.write_text(json.dumps([old_candidate]))
            eligible_path.write_text(json.dumps([old_candidate]))
            batch_path.write_text(json.dumps({
                "batch_id": "batch-old", "base_sha": SHA_C, "scope": "CORE",
                "queue_started_at": "2026-10-22T00:00:00Z", "eligible_candidates": [old_candidate],
                "required_gates": ["normal-home-v1"],
            }))
            command = [sys.executable, str(ROOT / "scripts/api28_batch_ledger.py"), "prepare",
                       "--ledger-dir", str(ledger), "--batch-input", str(batch_path),
                       "--current-input", str(current_path), "--observed-prs", str(observed_path),
                       "--batch-output", str(batch_output), "--queue-output", str(queue_output)]
            subprocess.run(command, cwd=ROOT, check=True, capture_output=True, text=True)

            new_candidate = {**old_candidate, "head_sha": SHA_B}
            current_path.write_text(json.dumps([new_candidate]))
            eligible_path.write_text(json.dumps([new_candidate]))
            batch_path.write_text(json.dumps({
                "batch_id": "batch-new", "base_sha": SHA_C, "scope": "CORE",
                "queue_started_at": "2026-10-22T01:00:00Z", "eligible_candidates": [new_candidate],
                "required_gates": ["normal-home-v1"],
            }))
            subprocess.run(command, cwd=ROOT, check=True, capture_output=True, text=True)
            queue = json.loads((ledger / "ledger/queue.json").read_text())
            old_row = next(row for row in queue["records"] if row["head_sha"] == SHA_A)
            new_row = next(row for row in queue["records"] if row["head_sha"] == SHA_B)
            self.assertEqual(old_row["status"], "CANCELLED_SUPERSEDED")
            self.assertEqual(old_row["superseded_by_sha"], SHA_B)
            self.assertEqual(new_row["status"], "QUEUED")

            final_path = candidates_dir / "final.json"
            final_path.write_text(json.dumps({
                "batch_status": "BLOCKED_INFRA", "api28_status": "BLOCKED_INFRA",
                "reason": "runner unavailable", "integration_sha": SHA_C,
                "integrated": [{"pr_number": 17, "head_sha": SHA_B}], "blocked": [], "deferred": [],
            }))
            finalize_command = [sys.executable, str(ROOT / "scripts/api28_batch_ledger.py"), "finalize",
                                "--ledger-dir", str(ledger), "--batch-id", "batch-new", "--final-input", str(final_path)]
            subprocess.run(finalize_command, cwd=ROOT, check=True, capture_output=True, text=True)
            queue = json.loads((ledger / "ledger/queue.json").read_text())
            updated = next(row for row in queue["records"] if row["head_sha"] == SHA_B)
            self.assertEqual(updated["status"], "BLOCKED_INFRA")
            self.assertEqual(updated["independent_pr_result"], "NOT_TESTED")
            self.assertEqual(assert_public_ledger_paths([
                path.relative_to(ledger).as_posix() for path in (ledger / "ledger").rglob("*") if path.is_file()
            ]), [])

    def test_superseded_head_is_retained_with_reason(self):
        old = [{"pr_number": 10, "head_sha": SHA_A, "status": "QUEUED", "first_seen_at": "t0"}]
        current = [{"pr_number": 10, "head_sha": SHA_B, "task_id": "TASK", "required_gates": ["normal-home-v1"], "initial_status": "QUEUED"}]
        result = reconcile_queue(old, current, now="t1", observed_prs={10})
        self.assertEqual(len(result), 2)
        self.assertEqual(result[0]["status"], "CANCELLED_SUPERSEDED")
        self.assertEqual(result[0]["superseded_by_sha"], SHA_B)
        self.assertEqual(result[1]["status"], "QUEUED")

    def test_withdrawn_candidate_is_recorded(self):
        old = [{"pr_number": 10, "head_sha": SHA_A, "status": "QUEUED"}]
        result = reconcile_queue(old, [], now="t2", observed_prs={10})
        self.assertEqual(result[0]["status"], "CANCELLED_WITHDRAWN")

    def test_runner_unavailable_is_blocked_infra_not_pass(self):
        outcome = integration_outcome(required_gates=["normal-home-v1"], gate_results={"normal-home-v1": "NOT_RUN"}, runner_available=False, integrated_prs=[{"pr_number": 10, "head_sha": SHA_A}], integration_sha=SHA_B)
        self.assertEqual(outcome["batch_status"], "BLOCKED_INFRA")
        self.assertEqual(outcome["component_results"][0]["result"], "NOT_INDEPENDENTLY_TESTED")

    def test_live_queue_timeout_persists_blocked_infra_and_retains_candidate(self):
        from datetime import datetime, timedelta, timezone
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            batch_path = root / "prepared.json"
            record = {
                "batch_id": "api28-core-777", "integration_sha": SHA_A, "base_sha": SHA_B,
                "tree_sha": SHA_C, "required_gates": ["normal-home-v1"],
                "integrated": [{"pr_number": 77, "head_sha": SHA_C}],
                "blocked": [], "deferred": [],
            }
            batch_path.write_text(json.dumps(record))
            ledger_dir = root / "ledger-worktree"
            ledger_dir.mkdir()
            old_queue_time = (datetime.now(timezone.utc) - timedelta(minutes=20)).isoformat().replace("+00:00", "Z")
            queued_job = {
                "name": "android-runtime (normal-home-v1)", "labels": ["self-hosted", "yujian-android", "api28"],
                "status": "queued", "check_run_url": "https://api.github.com/repos/o/r/check-runs/123",
            }
            args = ["api28_batch_monitor.py", "--repository", "o/r", "--run-id", "777",
                    "--batch-id", "api28-core-777", "--batch-record", str(batch_path),
                    "--ledger-dir", str(ledger_dir), "--token", "token", "--max-wait-minutes", "1",
                    "--observe-minutes", "1", "--poll-seconds", "0"]
            with patch.object(sys, "argv", args), \
                 patch.object(batch_monitor, "workflow_jobs", return_value=[queued_job]), \
                 patch.object(batch_monitor, "check_created_at", return_value=old_queue_time), \
                 patch.object(batch_monitor, "summarize_gate_artifacts", return_value=({"normal-home-v1": "NOT_RUN"}, None)), \
                 patch.object(batch_monitor, "api", return_value={}), \
                 patch.object(batch_monitor.subprocess, "run"):
                self.assertEqual(batch_monitor.main(), 0)
            result = json.loads((ledger_dir / "monitor-final.json").read_text())
            self.assertEqual(result["batch_status"], "BLOCKED_INFRA")
            self.assertEqual(result["api28_status"], "BLOCKED_INFRA")
            self.assertEqual(result["gate_results"]["normal-home-v1"], "NOT_RUN")
            self.assertEqual(result["component_results"][0]["result"], "NOT_INDEPENDENTLY_TESTED")

    def test_user_preview_not_promotable_to_formal_pass(self):
        preview = {"artifact_class": "USER_PREVIEW", "source_sha": SHA_A, "source_branch": "feature/x", "api28_status": "NOT_RUN", "formal_status": "NOT_ELIGIBLE", "release_status": "NOT_ELIGIBLE"}
        self.assertEqual(validate_delivery(preview), [])
        invalid = {**preview, "api28_status": "PASS", "formal_status": "PASS"}
        self.assertTrue(validate_delivery(invalid))
        integration = {"artifact_class": "API28_INTEGRATION_CANDIDATE", "source_sha": SHA_A, "source_branch": "api28-candidate/x", "api28_status": "PASS", "formal_status": "PENDING"}
        self.assertTrue(validate_delivery(integration))

    def test_formal_pass_requires_exact_integration_sha_and_every_gate(self):
        delivery = {
            "artifact_class": "FORMAL_ACCEPTANCE", "source_sha": SHA_A, "source_branch": "api28-candidate/x",
            "api28_status": "PASS", "formal_status": "PASS", "release_status": "NOT_ELIGIBLE",
            "integration_sha": SHA_A, "tested_source_sha": SHA_A, "batch_id": "batch-1", "workflow_run_id": 55,
            "apk_sha256": APK_SHA, "required_gates": ["normal-home-v1"], "gate_results": {"normal-home-v1": "PASS"},
            "evidence_links": ["https://example.invalid/evidence"], "component_prs": [{"pr_number": 10, "head_sha": SHA_B, "result": "NOT_INDEPENDENTLY_TESTED"}],
        }
        self.assertEqual(validate_delivery(delivery), [])
        invalid = {**delivery, "tested_source_sha": SHA_B}
        self.assertTrue(validate_delivery(invalid))
        invalid_component = {**delivery, "component_prs": [{"pr_number": 10, "head_sha": SHA_B, "result": "PASS"}]}
        self.assertTrue(validate_delivery(invalid_component))

    def test_feedback_requires_fix_replacement_apk_and_user_retest_to_close(self):
        feedback = {
            "feedback_id": "YF-ANDROID-1", "task_id": "TASK-01", "pr_number": 10,
            "preview_source_sha": SHA_A, "preview_apk_sha256": APK_SHA,
            "device_profile": {"device": "Pixel 2", "api_level": 28, "screen_px": "1080x1920", "font_scale": 1.0},
            "observed_behavior": "Observed", "expected_behavior": "Expected",
            "reproduction_steps": ["Open app"], "evidence_links": ["https://example.invalid/evidence"],
            "severity": "P1", "fix_source_sha": None, "replacement_apk_sha256": None,
            "user_retest_status": "NOT_RUN", "feedback_status": "OPEN",
        }
        self.assertEqual(validate_feedback(feedback), [])
        closed_early = {**feedback, "feedback_status": "CLOSED"}
        self.assertTrue(validate_feedback(closed_early))
        fixed_not_retested = {**feedback, "fix_source_sha": SHA_C, "replacement_apk_sha256": APK_SHA, "feedback_status": "FIXED_AWAITING_RETEST"}
        self.assertEqual(validate_feedback(fixed_not_retested), [])
        invalid_fix = {**fixed_not_retested, "feedback_status": "CLOSED", "user_retest_status": "NOT_RUN"}
        self.assertTrue(validate_feedback(invalid_fix))

    def test_dynamic_live_state_files_are_forbidden_from_public_ledger(self):
        self.assertEqual(assert_public_ledger_paths(["ledger/queue.json", "ledger/batches/run.json"]), [])
        self.assertTrue(assert_public_ledger_paths(["PROJECT_MASTER_STATE.yaml", "WORK_EXECUTION_STATE.json", "harness/build-log.md"]))

    def test_preview_provenance_writer_sets_not_run_and_exact_hash(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            apk = directory / "preview.apk"
            test_apk = directory / "preview-androidTest.apk"
            apk.write_bytes(b"sample APK bytes")
            test_apk.write_bytes(b"sample Android test APK bytes")
            output = directory / "provenance.json"
            command = [sys.executable, str(ROOT / "scripts/write_apk_provenance.py"),
                       "--apk", str(apk), "--test-apk", str(test_apk), "--output", str(output), "--source-sha", SHA_A,
                       "--source-branch", "feature/x", "--artifact-class", "USER_PREVIEW",
                       "--run-id", "123", "--artifact-name", "preview",
                       "--quick-check", "asset-contracts=PASS", "--quick-check", "unit-tests=PASS",
                       "--quick-check", "android-test-compile=PASS", "--quick-check", "lint=PASS"]
            subprocess.run(command, cwd=ROOT, check=True, capture_output=True, text=True)
            result = json.loads(output.read_text())
            self.assertEqual(result["artifact_class"], "USER_PREVIEW")
            self.assertEqual(result["api28_status"], "NOT_RUN")
            self.assertEqual(result["formal_status"], "NOT_ELIGIBLE")
            import hashlib
            self.assertEqual(result["android_test_apk_sha256"], hashlib.sha256(test_apk.read_bytes()).hexdigest())

    def test_batch_finalizer_requires_exact_sha_and_the_same_apk_pair_for_every_gate(self):
        import hashlib
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            artifacts = root / "artifacts"
            apk_dir = artifacts / "apk"
            apk_dir.mkdir(parents=True)
            apk = apk_dir / "app-debug.apk"
            test_apk = apk_dir / "app-debug-androidTest.apk"
            apk.write_bytes(b"one integration APK")
            test_apk.write_bytes(b"one integration test APK")
            app_sha = hashlib.sha256(apk.read_bytes()).hexdigest()
            test_sha = hashlib.sha256(test_apk.read_bytes()).hexdigest()
            (apk_dir / "apk_provenance.json").write_text(json.dumps({
                "source_sha": SHA_A, "artifact_class": "API28_INTEGRATION_CANDIDATE",
                "apk_sha256": app_sha, "android_test_apk_sha256": test_sha,
            }))
            gates_dir = artifacts / "gates"
            gate_names = ["normal-home-v1", "recognition-frozen", "camera-runtime-v1", "empty-home-v2"]
            for index, gate in enumerate(gate_names):
                gate_dir = gates_dir / f"runtime-gate-{index}"
                gate_dir.mkdir(parents=True)
                (gate_dir / "runtime_gate_result.json").write_text(json.dumps({
                    "gate": gate, "build_sha": SHA_A, "api_level": 28,
                    "app_apk_sha256": app_sha, "test_apk_sha256": test_sha,
                    "classification": "PASS",
                }))
            batch = {
                "batch_id": "api28-core-123", "mode": "CORE", "scope": "CORE",
                "integration_sha": SHA_A, "base_sha": SHA_B,
                "integrated": [{"pr_number": 17, "head_sha": SHA_C}],
                "repository": "pan277942135/Yujian_App",
            }
            batch_path = root / "batch.json"
            batch_path.write_text(json.dumps(batch))
            metadata_path = root / "metadata.json"
            metadata_path.write_text(json.dumps({"workflow_run_id": 123, "api28_queue_started_at": "2026-10-22T12:00:00Z"}))
            output_path = root / "final.json"
            command = [sys.executable, str(ROOT / "scripts/finalize_api28_batch.py"),
                       "--batch", str(batch_path), "--artifacts", str(artifacts),
                       "--run-metadata", str(metadata_path), "--android-outcome", "success",
                       "--output", str(output_path)]
            subprocess.run(command, cwd=ROOT, check=True, capture_output=True, text=True)
            result = json.loads(output_path.read_text())
            self.assertEqual(result["batch_status"], "CORE_PASS")
            self.assertEqual(result["android_test_apk_sha256"], test_sha)
            self.assertEqual(result["queue_started_at"], "2026-10-22T12:00:00Z")

            failing_gate = gates_dir / "runtime-gate-0" / "runtime_gate_result.json"
            gate_record = json.loads(failing_gate.read_text())
            gate_record["classification"] = "FAIL_TEST"
            failing_gate.write_text(json.dumps(gate_record))
            subprocess.run(command, cwd=ROOT, check=True, capture_output=True, text=True)
            self.assertEqual(json.loads(output_path.read_text())["batch_status"], "FAIL")

            gate_record["classification"] = "PASS"
            gate_record["build_sha"] = SHA_B
            failing_gate.write_text(json.dumps(gate_record))
            subprocess.run(command, cwd=ROOT, check=True, capture_output=True, text=True)
            rejected = json.loads(output_path.read_text())
            self.assertEqual(rejected["batch_status"], "BLOCKED_INFRA")
            self.assertTrue(rejected["gate_evidence_errors"])

    def test_control_changes_do_not_auto_start_api28_and_all_entries_share_mutex(self):
        workflow_text = (ROOT / ".github/workflows/android.yml").read_text(encoding="utf-8")
        self.assertIn("api28-batch-acceptance.yml@refs/heads/main", workflow_text)
        self.assertIn("yujian-api28-global", workflow_text)
        self.assertIn("queue: max", workflow_text)
        self.assertIn("max-parallel: 1", workflow_text)
        for path in ("camera-capture-p0-v2.yml", "camera-runtime-harness.yml", "nh01-cloud-runtime.yml"):
            text = (ROOT / ".github/workflows" / path).read_text(encoding="utf-8")
            self.assertNotRegex(text, r"(?m)^  (push|pull_request):")
            self.assertIn("yujian-api28-global", text)
        central = (ROOT / ".github/workflows/api28-batch-acceptance.yml").read_text(encoding="utf-8")
        self.assertIn("cancel-in-progress: false", central)
        self.assertIn("queue: max", central)
        self.assertIn("github.ref == 'refs/heads/main'", central)
        self.assertIn("needs.prepare.outputs.duplicate_of == ''", central)

    def test_repeated_successful_candidate_set_is_recorded_and_skips_runtime(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            ledger = root / "ledger"
            batch_dir = ledger / "ledger" / "batches"
            batch_dir.mkdir(parents=True)
            candidate = {
                "pr_number": 23, "head_sha": SHA_A, "task_id": "TASK-23",
                "required_gates": ["normal-home-v1"], "depends_on_prs": [], "initial_status": "QUEUED",
            }
            key = candidate_set_key(SHA_C, "CORE", [candidate])
            (batch_dir / "prior.json").write_text(json.dumps({
                "batch_id": "prior-batch", "candidate_set_key": key, "batch_status": "CORE_PASS",
            }))
            inputs = root / "inputs"
            inputs.mkdir()
            current = inputs / "current.json"
            current.write_text(json.dumps([candidate]))
            observed = inputs / "observed.json"
            observed.write_text("[23]")
            batch_input = inputs / "batch.json"
            batch_input.write_text(json.dumps({
                "batch_id": "duplicate-batch", "base_sha": SHA_C, "scope": "CORE",
                "eligible_candidates": [candidate],
            }))
            batch_output = inputs / "batch-output.json"
            queue_output = inputs / "queue-output.json"
            command = [sys.executable, str(ROOT / "scripts/api28_batch_ledger.py"), "prepare",
                       "--ledger-dir", str(ledger), "--batch-input", str(batch_input),
                       "--current-input", str(current), "--observed-prs", str(observed),
                       "--batch-output", str(batch_output), "--queue-output", str(queue_output)]
            subprocess.run(command, cwd=ROOT, check=True, capture_output=True, text=True)

            batch = json.loads(batch_output.read_text())
            persisted = json.loads((batch_dir / "duplicate-batch.json").read_text())
            self.assertEqual(batch["duplicate_of"], "prior-batch")
            self.assertEqual(batch["batch_status"], "DUPLICATE_SKIPPED")
            self.assertEqual(persisted["duplicate_of"], "prior-batch")
            self.assertEqual(persisted["candidate_set_key"], key)


if __name__ == "__main__":
    unittest.main()
