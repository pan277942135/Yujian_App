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
        self.assertTrue(all(row["state_id"] == "EMPTY" and row["result"] == "NOT_RUN" for row in matrix["cases"]))
        self.assertEqual(matrix["state_authority_refs"][0]["locator"], "heading:Scope")
        self.assertEqual(matrix["state_authority_refs"][0]["declared_locator"], "heading:empty state")
        self.assertEqual(matrix["state_authority_refs"][0]["resolution"], "PAGE_SPECIFIC_EXPLICIT_STATE_MAPPING")
        self.assertEqual(matrix["state_authority_refs"][0]["resolved_state_ids"], ["EMPTY"])

    def test_empty_home_unknown_behavior_heading_remains_unresolved(self):
        envelope = runner.read_json(ROOT / "design/system_v3/07_Responsive_System/page_contract_examples/home_empty_v2.envelope.json")
        profile = runner.read_json(ROOT / "design/system_v3/07_Responsive_System/page_contract_examples/home_empty_v2.profile.json")
        envelope["state_scope"]["included_state_refs"][0]["locator"] = "heading:State That Does Not Exist"
        matrix = runner.derive_matrix(ROOT, envelope, profile)
        self.assertEqual(matrix["expected_case_count"], 8)
        self.assertTrue(all(row["state_id"] is None for row in matrix["cases"]))
        self.assertIn("State That Does Not Exist", matrix["cases"][0]["failure_reason"])
        self.assertEqual(matrix["state_authority_refs"][0]["resolved_state_ids"], [])

    def test_empty_home_legacy_behavior_locator_maps_only_to_explicit_scope_rule(self):
        envelope = runner.read_json(ROOT / "design/system_v3/07_Responsive_System/page_contract_examples/home_empty_v2.envelope.json")
        profile = runner.read_json(ROOT / "design/system_v3/07_Responsive_System/page_contract_examples/home_empty_v2.profile.json")
        matrix = runner.derive_matrix(ROOT, envelope, profile)
        ref = matrix["state_authority_refs"][0]
        self.assertEqual(ref["declared_locator"], "heading:empty state")
        self.assertEqual(ref["locator"], "heading:Scope")
        self.assertEqual(ref["resolution"], "PAGE_SPECIFIC_EXPLICIT_STATE_MAPPING")
        self.assertEqual(ref["resolved_state_ids"], ["EMPTY"])

    def test_empty_home_behavior_state_sha_mismatch_stays_unresolved(self):
        envelope = runner.read_json(ROOT / "design/system_v3/07_Responsive_System/page_contract_examples/home_empty_v2.envelope.json")
        profile = runner.read_json(ROOT / "design/system_v3/07_Responsive_System/page_contract_examples/home_empty_v2.profile.json")
        authority = next(row for row in envelope["authority_refs"] if row["authority_ref_id"] == "empty-home-behavior")
        authority["sha256"] = "0" * 64
        matrix = runner.derive_matrix(ROOT, envelope, profile)
        self.assertTrue(all(row["state_id"] is None for row in matrix["cases"]))
        self.assertEqual(matrix["state_authority_refs"][0]["resolution"], "UNRESOLVED")
        self.assertEqual(matrix["state_authority_refs"][0]["resolved_state_ids"], [])

    def make_w5_matrix_report(self, folder: Path, *, source_sha=None, run_id=None, authority_sha=None, authority_id=None,
                              state_id="EMPTY", declared_locator=None, resolved_locator=None, resolution=None,
                              include_screenshots=True):
        envelope = runner.read_json(ROOT / "design/system_v3/07_Responsive_System/page_contract_examples/home_empty_v2.envelope.json")
        profile = runner.read_json(ROOT / "design/system_v3/07_Responsive_System/page_contract_examples/home_empty_v2.profile.json")
        matrix = runner.derive_matrix(ROOT, envelope, profile)
        state_ref = matrix["state_authority_refs"][0]
        folder.mkdir(parents=True, exist_ok=True)
        report_path = folder / "EmptyHome_Structured_HiFi_Evidence_V1.json"
        rows = []
        for index, expected in enumerate(matrix["cases"]):
            width, height = expected["viewport"]["width"], expected["viewport"]["height"]
            font = expected["font_scale"]
            label = f"{font:g}" if isinstance(font, (int, float)) else str(font)
            shot_name = f"case-{index}.png"
            shot_path = folder / shot_name
            if include_screenshots:
                png = bytearray(24)
                png[:8] = b"\x89PNG\r\n\x1a\n"
                png[16:20] = (width * 3).to_bytes(4, "big")
                png[20:24] = (height * 3).to_bytes(4, "big")
                shot_path.write_bytes(png)
            rows.append({
                "case_id": f"EMPTY_HOME_{width}x{height}_FONT_{label}",
                "page_id": "home_empty_v2", "viewport": expected["viewport"],
                "viewport_width": width, "viewport_height": height,
                "font_scale": font, "state_id": state_id, "renderer_state_id": "EMPTY_DEFAULT",
                "matrix_classification": "FORMAL_AUTHORITY_CASE",
                "source_sha": source_sha or self.source_sha, "run_id": run_id or self.run_id,
                "matrix_source_path": matrix["source_path"],
                "matrix_source_sha256": authority_sha or matrix["source_sha256"],
                "result": "PASS", "node_bounds": [{"node_id": "EmptyHomeRoot", "bounds": {"x": 0, "y": 0, "width": width, "height": height}}],
                "screenshot_rendered": {"path": shot_name, "sha256": runner.sha256_file(shot_path) if include_screenshots else "0" * 64,
                                        "pixel_width": width * 3, "pixel_height": height * 3},
            })
        expected_viewports = sorted({(row["viewport"]["width"], row["viewport"]["height"]) for row in matrix["cases"]})
        for index, (width, height) in enumerate(expected_viewports, start=len(rows)):
            shot_name = f"case-{index}.png"
            shot_path = folder / shot_name
            if include_screenshots:
                png = bytearray(24)
                png[:8] = b"\x89PNG\r\n\x1a\n"
                png[16:20] = (width * 3).to_bytes(4, "big")
                png[20:24] = (height * 3).to_bytes(4, "big")
                shot_path.write_bytes(png)
            rows.append({
                "case_id": f"EMPTY_HOME_{width}x{height}_FONT_1.5", "page_id": "home_empty_v2",
                "viewport": {"width": width, "height": height}, "viewport_width": width, "viewport_height": height,
                "font_scale": 1.5, "state_id": state_id, "renderer_state_id": "EMPTY_DEFAULT",
                "matrix_classification": "SUPPLEMENTAL_CANDIDATE_NOT_IN_FROZEN_AUTHORITY",
                "source_sha": source_sha or self.source_sha, "run_id": run_id or self.run_id,
                "matrix_source_path": matrix["source_path"], "matrix_source_sha256": authority_sha or matrix["source_sha256"],
                "result": "PASS", "node_bounds": [{"node_id": "EmptyHomeRoot", "bounds": {"x": 0, "y": 0, "width": width, "height": height}}],
                "screenshot_rendered": {"path": shot_name, "sha256": runner.sha256_file(shot_path) if include_screenshots else "0" * 64,
                                        "pixel_width": width * 3, "pixel_height": height * 3},
            })
        formal_count = len(matrix["cases"])
        supplemental_count = len(expected_viewports)
        report = {
            "schema": "YUJIAN_PAGE_EVIDENCE_MANIFEST_V1", "page_id": "home_empty_v2",
            "source_sha": source_sha or self.source_sha, "candidate_source_sha": source_sha or self.source_sha,
            "github_run_id": run_id or self.run_id, "status": "PASS_STRUCTURAL_MATRIX_VISUAL_REVIEW_PENDING",
            "visual_review_status": "USER_VISUAL_ACCEPTANCE_PENDING",
            "user_acceptance_status": "USER_VISUAL_ACCEPTANCE_PENDING", "native_runtime_status": "NOT_VERIFIED",
            "matrix_authority": {"id": authority_id or matrix["source_authority_ref_id"],
                                 "path": matrix["source_path"], "sha256": authority_sha or matrix["source_sha256"]},
            "matrix_summary": {"expected": formal_count, "executed": formal_count, "pass": formal_count, "fail": 0, "not_run": 0,
                               "formal_expected": formal_count, "formal_executed": formal_count, "formal_pass": formal_count, "formal_fail": 0, "formal_not_run": 0,
                               "supplemental_expected": supplemental_count, "supplemental_executed": supplemental_count,
                               "supplemental_pass": supplemental_count, "supplemental_fail": 0, "supplemental_not_run": 0,
                               "total_expected": formal_count + supplemental_count, "total_executed": formal_count + supplemental_count,
                               "total_pass": formal_count + supplemental_count, "total_fail": 0, "total_not_run": 0,
                               "source_path": matrix["source_path"], "source_sha256": matrix["source_sha256"]},
            "supplemental_matrix_definition": {"font_scale": 1.5, "viewports": [{"width": w, "height": h} for w, h in expected_viewports],
                                               "status": "SUPPLEMENTAL_CANDIDATE_NOT_IN_FROZEN_AUTHORITY",
                                               "source_path": matrix["source_path"], "source_sha256": matrix["source_sha256"]},
            "behavior_state_mapping": {"authority_ref_id": state_ref["authority_ref_id"], "path": state_ref["path"],
                                       "sha256": state_ref["sha256"], "declared_locator": declared_locator or state_ref["declared_locator"],
                                       "locator": resolved_locator or state_ref["locator"], "resolution": resolution or state_ref["resolution"],
                                       "behavior_state_id": state_id, "renderer_state_id": "EMPTY_DEFAULT"},
            "matrix": rows,
            "negative_cases": [{"case": f"negative_{index}", "result": "EXPECTED_FAIL_CLOSED"} for index in range(8)],
        }
        report_path.write_text(json.dumps(report), encoding="utf-8")
        return report_path, matrix

    def test_w4_imports_exact_w5_cases_dom_and_screenshots(self):
        source, matrix = self.make_w5_matrix_report(self.temp_root / "w5-matrix-valid")
        output = self.temp_root / "w4-matrix-evidence-valid"
        errors = runner.import_structured_matrix_report(ROOT, "home_empty_v2", self.source_sha, self.run_id,
                                                         str(source), matrix, output)
        self.assertEqual(errors, [])
        self.assertEqual(matrix["status"], "PASS")
        self.assertEqual(matrix["expected_case_count"], 8)
        self.assertEqual(sum(row["result"] == "PASS" for row in matrix["cases"]), 8)
        self.assertEqual(matrix["supplemental"]["status"], "PASS")
        self.assertEqual(matrix["supplemental"]["expected_case_count"], 4)
        self.assertEqual(matrix["supplemental"]["recorded_case_count"], 4)
        schema = runner.read_json(ROOT / runner.EVIDENCE_SCHEMA)
        matrix_schema = {**schema["properties"]["matrix"], "$defs": schema["$defs"]}
        self.assertEqual(list(runner.Draft202012Validator(matrix_schema).iter_errors(matrix)), [])
        self.assertTrue(all((output / row["evidence_path"]).is_file() and (output / row["screenshot_path"]).is_file()
                            for row in matrix["cases"]))

    def test_w4_rejects_missing_or_misclassified_1_5x_supplemental_case(self):
        mutations = ["missing", "wrong-scale"]
        for mutation in mutations:
            with self.subTest(mutation=mutation):
                source, matrix = self.make_w5_matrix_report(self.temp_root / f"w5-matrix-supplemental-{mutation}")
                report = json.loads(source.read_text(encoding="utf-8"))
                supplemental = [row for row in report["matrix"]
                                if row.get("matrix_classification") == "SUPPLEMENTAL_CANDIDATE_NOT_IN_FROZEN_AUTHORITY"]
                if mutation == "missing":
                    report["matrix"].remove(supplemental[-1])
                else:
                    supplemental[-1]["font_scale"] = 1.3
                source.write_text(json.dumps(report), encoding="utf-8")
                errors = runner.import_structured_matrix_report(ROOT, "home_empty_v2", self.source_sha, self.run_id,
                                                                 str(source), matrix,
                                                                 self.temp_root / f"w4-matrix-supplemental-{mutation}")
                self.assertTrue(errors)
                self.assertEqual(matrix["status"], "FAIL")
                self.assertEqual(matrix["supplemental"]["status"], "FAIL")

    def test_w4_rejects_matrix_wrong_source_run_state_authority_or_missing_screenshot(self):
        mutations = [
            {"source_sha": "0" * 40},
            {"run_id": "987654321"},
            {"authority_sha": "0" * 64},
            {"authority_id": "wrong-authority"},
            {"state_id": "NORMAL"},
            {"declared_locator": "heading:wrong"},
            {"resolved_locator": "heading:wrong"},
            {"include_screenshots": False},
        ]
        for index, mutation in enumerate(mutations):
            with self.subTest(mutation=mutation):
                source, matrix = self.make_w5_matrix_report(self.temp_root / f"w5-matrix-invalid-{index}", **mutation)
                errors = runner.import_structured_matrix_report(ROOT, "home_empty_v2", self.source_sha, self.run_id,
                                                                 str(source), matrix, self.temp_root / f"w4-matrix-invalid-{index}")
                self.assertTrue(errors)
                self.assertEqual(matrix["status"], "FAIL")

    def test_w4_failed_w5_report_with_null_dom_report_fails_closed_without_secondary_error(self):
        source, matrix = self.make_w5_matrix_report(self.temp_root / "w5-matrix-failed-null-dom")
        report = json.loads(source.read_text(encoding="utf-8"))
        report["status"] = "FAIL"
        report["matrix_summary"].update({"executed": 0, "pass": 0, "fail": 1, "not_run": len(report["matrix"])})
        report["dom_report"] = None
        source.write_text(json.dumps(report), encoding="utf-8")

        errors = runner.import_structured_matrix_report(ROOT, "home_empty_v2", self.source_sha, self.run_id,
                                                         str(source), matrix, self.temp_root / "w4-matrix-failed-null-dom")

        self.assertTrue(errors)
        self.assertTrue(any("did not complete its structural run" in error for error in errors))
        self.assertEqual(matrix["status"], "FAIL")

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

    def test_unconnected_reference_image_workspace_stays_not_run(self):
        report_path = self.temp_root / "reference-only-browser-report.json"
        report = {
            "result": "NOT_RUN",
            "reason": "REFERENCE_IMAGE_ONLY_NOT_CONNECTED",
            "page_id": "home_empty_v2",
            "source_sha": self.source_sha,
            "candidate_url": "https://example.invalid/design/manager/#page/home_empty_v2/hifi/structured_workspace",
            "visual_result": "MEASUREMENT_ONLY_NOT_VISUAL_PASS",
            "page_acceptance": "NOT_VERIFIED",
            "workspace": {"workspaceKind": "reference-image-only"},
            "run_id": self.run_id,
        }
        report_path.write_text(json.dumps(report), encoding="utf-8")
        evidence_dir = self.temp_root / "empty-home-evidence"
        evidence_dir.mkdir(parents=True, exist_ok=True)
        status, evidence, error = runner.validate_browser_report(
            ROOT, "home_empty_v2", self.source_sha, self.run_id, str(report_path), evidence_dir
        )
        self.assertEqual(status, "NOT_RUN")
        self.assertEqual(error, "Structured page renderer is not connected; browser page evidence remains NOT_RUN.")
        self.assertEqual(evidence["source_sha"], self.source_sha)
        self.assertEqual(evidence["run_id"], self.run_id)
        manifest = self.make_manifest("home_empty_v2")
        manifest["checks"]["browser_geometry"] = status
        manifest["checks"]["browser_evidence"] = evidence
        self.assertEqual(runner.validate_manifest(ROOT, manifest, self.source_sha, self.run_id, evidence_dir), [])

    def test_structured_renderer_cannot_hide_behind_not_run(self):
        report_path = self.temp_root / "structured-not-run-browser-report.json"
        report_path.write_text(json.dumps({
            "result": "NOT_RUN",
            "reason": "REFERENCE_IMAGE_ONLY_NOT_CONNECTED",
            "page_id": "home_empty_v2",
            "source_sha": self.source_sha,
            "candidate_url": "https://example.invalid/design/manager/",
            "visual_result": "MEASUREMENT_ONLY_NOT_VISUAL_PASS",
            "page_acceptance": "NOT_VERIFIED",
            "workspace": {"workspaceKind": "structured-dom-candidate"},
            "run_id": self.run_id,
        }), encoding="utf-8")
        evidence_dir = self.temp_root / "structured-evidence"
        evidence_dir.mkdir(parents=True, exist_ok=True)
        status, _, error = runner.validate_browser_report(
            ROOT, "home_empty_v2", self.source_sha, self.run_id, str(report_path), evidence_dir
        )
        self.assertEqual(status, "FAIL")
        self.assertIn("NOT_RUN is reserved", error)

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
