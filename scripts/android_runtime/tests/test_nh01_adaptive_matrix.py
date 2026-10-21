from __future__ import annotations

import io
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path
from unittest.mock import patch

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from aggregate_nh01_adaptive_matrix import classify_execution, main, read_execution, validate_case


class AdaptiveMatrixAggregateTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.raw = Path(self.temp.name)
        Image.new("RGB", (320, 480), "#668899").save(self.raw / "M01-adaptive_v2.png")
        self.payload = {
            "status": "PASS",
            "execution_mode": "ANDROID_API28_RUNTIME",
            "layout_variant": "ADAPTIVE_V2",
            "source_commit": "a" * 40,
            "system_font_scale": 1.0,
            "density_dpi": 160,
            "display_resolution_px": [320, 480],
            "bounds_in_window_px": {
                name: {"left": 0, "top": 0, "right": 1, "bottom": 1}
                for name in (
                    "gemini-home-screen", "gemini-home-brand-title", "gemini-home-stats-bar",
                    "gemini-home-section-title", "gemini-home-catch-card-nh01-catch-001",
                    "gemini-home-footer-cta", "gemini-home-cta-label", "gemini-home-camera",
                )
            },
            "metrics": {
                "card_aspect_ratio": 247 / 293,
                "card_track_horizontal_center_error_dp": 0.2,
                "cta_camera_overlap": False,
                "camera_safe_bottom_clearance_dp": 8.0,
            },
            "data": {
                "catch_ids": ["nh01-catch-001", "nh01-catch-002"],
                "unique_catch_count": 2,
                "pager_page_count": 2,
                "same_time_duplicate_ids": False,
                "swipe_reached_second_id": True,
                "required_card_text_accessible": True,
            },
            "screenshots": {"initial": "M01-adaptive_v2.png"},
        }

    def tearDown(self) -> None:
        self.temp.cleanup()

    def test_accepts_actual_runtime_report_with_complete_two_catch_evidence(self) -> None:
        self.assertEqual([], validate_case(
            self.payload, "M01", 320, 480, 1.0, "ADAPTIVE_V2", "a" * 40, self.raw,
        ))

    def test_rejects_wrong_shape_and_duplicate_or_missing_catch_identity(self) -> None:
        self.payload["metrics"]["card_aspect_ratio"] = 0.71
        self.payload["data"]["catch_ids"] = ["nh01-catch-001", "nh01-catch-001"]
        problems = validate_case(
            self.payload, "M01", 320, 640, 1.0, "ADAPTIVE_V2", "a" * 40, self.raw,
        )
        self.assertTrue(any("card ratio" in problem for problem in problems), problems)
        self.assertTrue(any("two-catch IDs/uniqueness" in problem for problem in problems), problems)

    def test_execution_status_preserves_assertion_crash_and_evidence_failures(self) -> None:
        status, reason = classify_execution(None)
        self.assertEqual("MISSING", status)
        self.assertIn("execution record missing", reason)

        status, reason = classify_execution({"status": "RUNNING", "reason": "WAIT_FOR_MAIN_CATCH_CARD"})
        self.assertEqual("FAIL", status)
        self.assertEqual("WAIT_FOR_MAIN_CATCH_CARD", reason)

        status, reason = classify_execution({
            "status": "FAIL", "exit_code": 1, "reason": "TEST_ASSERTION_FAILED: card bounds",
        })
        self.assertEqual("FAIL", status)
        self.assertIn("TEST_ASSERTION_FAILED", reason)

        status, reason = classify_execution({
            "status": "FAIL", "exit_code": 139, "reason": "INSTRUMENTATION_EXIT_139 (process crash)",
        })
        self.assertEqual("FAIL", status)
        self.assertIn("process crash", reason)

    def test_pass_requires_device_report_both_screenshots_and_applied_settings(self) -> None:
        complete = {
            "status": "PASS", "exit_code": 0, "device_report_present": True,
            "required_screenshots_present": True, "profile_settings_match": True,
        }
        self.assertEqual("PASS", classify_execution(complete)[0])

        for field, value, expected_problem in (
            ("device_report_present", False, "device report missing"),
            ("required_screenshots_present", False, "screenshots missing"),
            ("profile_settings_match", False, "settings did not apply"),
        ):
            with self.subTest(field=field):
                incomplete = dict(complete)
                incomplete[field] = value
                status, reason = classify_execution(incomplete)
                self.assertEqual("FAIL", status)
                self.assertIn(expected_problem, reason)

    def test_invalid_execution_json_is_reported_as_failure(self) -> None:
        execution_dir = self.raw / "executions"
        execution_dir.mkdir()
        (execution_dir / "M01-adaptive_v2.json").write_text("{bad json", encoding="utf-8")
        record = read_execution(execution_dir, "M01", "ADAPTIVE_V2")
        self.assertEqual("FAIL", classify_execution(record)[0])
        self.assertEqual("EXECUTION_RECORD_JSON_INVALID", classify_execution(record)[1])

    def test_aggregator_keeps_test_failure_distinct_from_missing_report(self) -> None:
        raw = self.raw / "raw"
        execution = self.raw / "execution"
        output = self.raw / "output"
        raw.mkdir()
        execution.mkdir()
        (raw / "M01-adaptive_v2.json").write_text(
            '{"status":"FAIL","failure_stage":"WAIT_FOR_MAIN_CATCH_CARD"}', encoding="utf-8",
        )
        (execution / "M01-adaptive_v2.json").write_text(
            '{"status":"FAIL","exit_code":1,"reason":"TEST_ASSERTION_FAILED: WAIT_FOR_MAIN_CATCH_CARD"}',
            encoding="utf-8",
        )
        contract = Path(__file__).resolve().parents[3] / "design/qa/NH01_ADAPTIVE_VISUAL_BALANCE_V2.md"
        arguments = [
            "aggregate_nh01_adaptive_matrix.py", "--raw-dir", str(raw),
            "--execution-dir", str(execution), "--output-dir", str(output),
            "--source-sha", "a" * 40, "--run-id", "test-run", "--run-url", "https://example.invalid/run",
            "--contract", str(contract),
        ]
        with patch.object(sys, "argv", arguments):
            with redirect_stdout(io.StringIO()):
                self.assertEqual(1, main())

        import json
        summary = json.loads((output / "NH01_ADAPTIVE_MATRIX_SUMMARY_V1.json").read_text(encoding="utf-8"))
        first = summary["cases"][0]
        self.assertEqual("FAIL", first["candidate_status"])
        self.assertEqual("MISSING", first["baseline_status"])
        self.assertTrue(any("TEST_ASSERTION_FAILED" in item for item in first["problems"]))


if __name__ == "__main__":
    unittest.main()
