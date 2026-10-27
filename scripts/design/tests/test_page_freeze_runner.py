from __future__ import annotations

import hashlib
import json
import sys
import tempfile
import shutil
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "scripts/design"))
import page_freeze_runner as runner  # noqa: E402


class PageFreezeRunnerTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source_sha = runner.current_sha(ROOT)
        cls.temp = tempfile.TemporaryDirectory(prefix="page-freeze-runner-tests-")
        cls.temp_root = Path(cls.temp.name)
        cls.run_id = "123456789"

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def make_manifest(self, page_id="home_normal_v1"):
        refs = runner.resolve_page_case(ROOT, page_id, None, None)
        envelope = runner.read_json(ROOT / refs["contract"])
        profile = runner.read_json(ROOT / refs["profile"])
        report_path = self.temp_root / f"{page_id}-w2-report.json"
        report = {
            "status": "PASS",
            "source_sha": self.source_sha,
            "workflow_run_id": self.run_id,
            "pages": [{
                "page_id": page_id,
                "status": "PASS",
                "publication": {"file_count": 1046, "restricted_bound_resource_count": 2},
            }],
        }
        report_path.write_text(json.dumps(report), encoding="utf-8")
        return {
            "schema_version": "1.0",
            "page_id": page_id,
            "contract_id": envelope["contract_id"],
            "contract_version": envelope["contract_version"],
            "contract_path": refs["contract"],
            "contract_sha256": runner.sha256_file(ROOT / refs["contract"]),
            "profile_id": profile["profile_id"],
            "profile_path": refs["profile"],
            "profile_sha256": runner.sha256_file(ROOT / refs["profile"]),
            "state_id": None,
            "candidate_sha": self.source_sha,
            "build_sha": self.source_sha,
            "deploy_sha": None,
            "viewport": None,
            "fontScale": None,
            "command": ["python", "scripts/design/page_freeze_runner.py", "validate-page", "--page-id", page_id],
            "ci_run_id": self.run_id,
            "artifact_url": f"https://github.com/pan277942135/Yujian_App/actions/runs/{self.run_id}",
            "gate_id": runner.W4_GATE,
            "result": "NOT_RUN",
            "failure_reason": None,
            "photo_publication_provenance": {
                "status": "PASS",
                "source_sha": self.source_sha,
                "run_id": self.run_id,
                "report_path": str(report_path),
                "report_sha256": runner.sha256_file(report_path),
                "file_count": 1046,
                "restricted_resource_count": 2,
            },
            "checks": {
                "contract": "PASS",
                "resources": "PASS",
                "pages_bundle": "PASS",
                "browser_geometry": "NOT_RUN",
                "browser_evidence": {
                    "status": "NOT_RUN", "report_path": None, "report_sha256": None,
                    "screenshot_path": None, "screenshot_sha256": None,
                    "dom_path": None, "dom_sha256": None, "candidate_url": None,
                    "source_sha": None, "run_id": None,
                    "visual_result": "MEASUREMENT_ONLY_NOT_VISUAL_PASS",
                },
                "native_runtime": "NOT_VERIFIED",
            },
            "matrix": runner.derive_matrix(ROOT, envelope, profile),
            "page_acceptance": "NOT_VERIFIED",
            "generated_at": "2026-10-27T07:00:00+00:00",
        }

    def test_nh01_matrix_comes_from_owned_matrix_and_has_expected_case_count(self):
        manifest = self.make_manifest()
        matrix = manifest["matrix"]
        self.assertEqual(matrix["source_path"], "design/pages/home/normal_home/engineering/NH01_GEMINI_ADAPTIVE_V2_ACCEPTANCE_MATRIX_V1.json")
        self.assertEqual(matrix["expected_case_count"], 24)
        self.assertEqual(matrix["recorded_case_count"], 24)
        self.assertTrue(all(row["result"] == "NOT_RUN" for row in matrix["cases"]))

    def test_empty_home_uses_declared_viewports_and_never_fabricates_state_ids(self):
        manifest = self.make_manifest("home_empty_v2")
        matrix = manifest["matrix"]
        self.assertEqual(matrix["expected_case_count"], 8)
        self.assertEqual({row["viewport"]["width"] for row in matrix["cases"]}, {320, 360, 393, 411})
        self.assertTrue(all(row["state_id"] is None and row["result"] == "NOT_RUN" for row in matrix["cases"]))

    def test_login_without_machine_viewports_does_not_invent_a_matrix(self):
        matrix = self.make_manifest("auth_login_v2")["matrix"]
        self.assertEqual(matrix["expected_case_count"], 0)
        self.assertEqual(matrix["recorded_case_count"], 0)
        self.assertIn("No machine-readable viewport authority", matrix["reason"])

    def test_wrong_page_id_contract_binding_is_rejected(self):
        normal = "design/system_v3/07_Responsive_System/page_contract_examples/home_normal_v1.envelope.json"
        normal_profile = "design/system_v3/07_Responsive_System/page_contract_examples/home_normal_v1.profile.json"
        with self.assertRaisesRegex(runner.PipelineError, "wrong page_id"):
            runner.resolve_page_case(ROOT, "home_empty_v2", normal, normal_profile)

    def test_missing_contract_file_is_rejected(self):
        with self.assertRaisesRegex(runner.PipelineError, "repository file is missing"):
            runner.resolve_page_case(ROOT, "home_normal_v1", "design/missing-envelope.json", "design/missing-profile.json")

    def test_stale_candidate_sha_is_rejected(self):
        manifest = self.make_manifest()
        errors = runner.validate_manifest(ROOT, manifest, "0" * 40, self.run_id)
        self.assertTrue(any("candidate_sha" in error for error in errors))

    def test_runner_refuses_a_source_sha_other_than_checked_out_head(self):
        with self.assertRaisesRegex(runner.PipelineError, "must match current exact HEAD"):
            runner.run_validation(ROOT, "home_normal_v1", "0" * 40, None, None,
                                  self.temp_root / "stale-sha", None, None, False)

    def test_pass_without_run_id_is_rejected(self):
        manifest = self.make_manifest()
        manifest["result"] = "PASS"
        manifest["ci_run_id"] = None
        manifest["artifact_url"] = None
        errors = runner.validate_manifest(ROOT, manifest, self.source_sha, None)
        self.assertTrue(any("numeric CI Run ID" in error for error in errors))
        self.assertTrue(any("observed workflow Run ID" in error for error in errors))

    def test_forged_run_id_is_rejected_against_pages_scan(self):
        manifest = self.make_manifest()
        manifest["result"] = "PASS"
        manifest["ci_run_id"] = "987654321"
        manifest["artifact_url"] = "https://github.com/pan277942135/Yujian_App/actions/runs/987654321"
        errors = runner.validate_manifest(ROOT, manifest, self.source_sha, "987654321")
        self.assertTrue(any("same-Run-ID Pages bundle scan" in error or "report contents" in error for error in errors))

    def test_lost_expected_matrix_case_is_rejected(self):
        manifest = self.make_manifest()
        manifest["matrix"]["cases"].pop()
        manifest["matrix"]["recorded_case_count"] -= 1
        errors = runner.validate_manifest(ROOT, manifest, self.source_sha, self.run_id)
        self.assertTrue(any("page-owned expected matrix" in error or "expected_case_count" in error for error in errors))

    def test_matrix_resolution_failure_cannot_pass(self):
        manifest = self.make_manifest()
        manifest["matrix"]["status"] = "FAIL"
        errors = runner.validate_manifest(ROOT, manifest, self.source_sha, self.run_id)
        self.assertTrue(any("failed page-owned matrix cases" in error for error in errors))

    def test_fake_screenshot_cannot_claim_browser_pass(self):
        manifest = self.make_manifest()
        manifest["result"] = "PASS"
        evidence = manifest["checks"]["browser_evidence"]
        manifest["checks"]["browser_geometry"] = "PASS"
        evidence.update({
            "status": "PASS", "report_path": str(self.temp_root / "missing-report.json"),
            "report_sha256": hashlib.sha256(b"fake").hexdigest(),
            "screenshot_path": str(self.temp_root / "missing.png"),
            "screenshot_sha256": hashlib.sha256(b"fake-image").hexdigest(),
            "dom_path": str(self.temp_root / "missing-dom.json"),
            "dom_sha256": hashlib.sha256(b"fake-dom").hexdigest(),
            "candidate_url": "https://example.invalid/design/manager/",
            "source_sha": self.source_sha, "run_id": self.run_id,
        })
        errors = runner.validate_manifest(ROOT, manifest, self.source_sha, self.run_id)
        self.assertTrue(any("missing or its SHA-256" in error or "cannot be verified" in error for error in errors))

    def test_unexecuted_matrix_and_browser_remain_not_run(self):
        manifest = self.make_manifest()
        self.assertEqual(runner.validate_manifest(ROOT, manifest, self.source_sha, self.run_id), [])
        self.assertEqual(manifest["result"], "NOT_RUN")
        self.assertEqual(manifest["page_acceptance"], "NOT_VERIFIED")
        self.assertEqual(manifest["checks"]["native_runtime"], "NOT_VERIFIED")

    def test_manifest_cannot_pass_with_unrun_browser_or_matrix_cases(self):
        manifest = self.make_manifest()
        manifest["result"] = "PASS"
        errors = runner.validate_manifest(ROOT, manifest, self.source_sha, self.run_id)
        self.assertTrue(any("actual browser workspace geometry" in error for error in errors))
        self.assertTrue(any("matrix case NOT_RUN" in error for error in errors))

    def test_unexpected_dirty_source_file_blocks_exact_sha_runner(self):
        path = ROOT / "w4-dirty-check.tmp"
        path.write_text("untracked source", encoding="utf-8")
        try:
            with self.assertRaisesRegex(runner.PipelineError, "source worktree is dirty"):
                runner.require_clean_source_tree(ROOT)
        finally:
            path.unlink(missing_ok=True)

    def test_generated_pages_bundle_with_unicode_paths_is_allowed(self):
        generated_root = ROOT / "_site" / "w4-unicode-path-check"
        generated = generated_root / "设计" / "资源图.png"
        generated.parent.mkdir(parents=True, exist_ok=True)
        generated.write_bytes(b"generated")
        try:
            runner.require_clean_source_tree(ROOT)
        finally:
            shutil.rmtree(generated_root, ignore_errors=True)


if __name__ == "__main__":
    unittest.main()
