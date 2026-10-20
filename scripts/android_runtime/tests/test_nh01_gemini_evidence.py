from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

RUNTIME_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(RUNTIME_DIR))

from validate_nh01_gemini_evidence import EvidenceValidationError, validate  # noqa: E402


class Nh01GeminiEvidenceValidationTest(unittest.TestCase):
    def setUp(self) -> None:
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)

    def write_valid_reports(self) -> None:
        compose_path = self.root / "compose-simulation/gemini_responsive_compose_report.json"
        avatar_path = self.root / "avatar/gemini_avatar_transition_report.json"
        carousel_path = self.root / "compose-simulation/carousel/gemini_catch_carousel_count_report.json"
        compose_path.parent.mkdir(parents=True)
        avatar_path.parent.mkdir(parents=True)
        carousel_path.parent.mkdir(parents=True)
        compose_path.write_text(
            json.dumps({
                "profiles": [
                    {
                        "profile": f"profile-{index}",
                        "status": "PASS",
                        "interactions": {"guest_avatar_login": "PASS"},
                    }
                    for index in range(15)
                ]
            }),
            encoding="utf-8",
        )
        avatar_path.write_text(
            json.dumps({
                "account_switch_clears_previous_avatar": "PASS",
                "logout_clears_previous_avatar": "PASS",
            }),
            encoding="utf-8",
        )
        carousel_path.write_text(
            json.dumps({"count_cases": [{"catch_count": count} for count in (0, 1, 2, 3, 10)]}),
            encoding="utf-8",
        )

    def test_valid_reports_pass_at_the_instrumentation_directory_layout(self) -> None:
        self.write_valid_reports()
        result = validate(self.root)
        self.assertEqual(result["status"], "PASS")
        self.assertEqual(result["compose_profiles"], 15)
        self.assertEqual(result["carousel_count_cases"], [0, 1, 2, 3, 10])

    def test_missing_reports_are_reported_as_missing(self) -> None:
        with self.assertRaisesRegex(EvidenceValidationError, r"MISSING: .*gemini_responsive_compose_report"):
            validate(self.root)

    def test_missing_avatar_report_is_not_treated_as_pass(self) -> None:
        self.write_valid_reports()
        (self.root / "avatar/gemini_avatar_transition_report.json").unlink()
        with self.assertRaisesRegex(EvidenceValidationError, r"MISSING: avatar/gemini_avatar_transition_report"):
            validate(self.root)

    def test_invalid_json_is_reported_with_the_file_path(self) -> None:
        self.write_valid_reports()
        (self.root / "compose-simulation/gemini_responsive_compose_report.json").write_text("{", encoding="utf-8")
        with self.assertRaisesRegex(EvidenceValidationError, r"INVALID_JSON: compose-simulation/gemini_responsive_compose_report"):
            validate(self.root)

    def test_incomplete_or_failed_compose_profiles_are_rejected(self) -> None:
        self.write_valid_reports()
        compose = self.root / "compose-simulation/gemini_responsive_compose_report.json"
        payload = json.loads(compose.read_text(encoding="utf-8"))
        payload["profiles"].pop()
        compose.write_text(json.dumps(payload), encoding="utf-8")
        with self.assertRaisesRegex(EvidenceValidationError, "expected 15 rows"):
            validate(self.root)

        payload["profiles"] = [
            {"profile": f"profile-{index}", "status": "PASS", "interactions": {"guest_avatar_login": "PASS"}}
            for index in range(15)
        ]
        payload["profiles"][4]["status"] = "FAIL"
        compose.write_text(json.dumps(payload), encoding="utf-8")
        with self.assertRaisesRegex(EvidenceValidationError, "did not pass"):
            validate(self.root)

    def test_avatar_transition_failure_is_rejected(self) -> None:
        self.write_valid_reports()
        avatar = self.root / "avatar/gemini_avatar_transition_report.json"
        avatar.write_text(json.dumps({
            "account_switch_clears_previous_avatar": "FAIL",
            "logout_clears_previous_avatar": "PASS",
        }), encoding="utf-8")
        with self.assertRaisesRegex(EvidenceValidationError, "account_switch_clears_previous_avatar"):
            validate(self.root)

    def test_incomplete_carousel_count_matrix_is_rejected(self) -> None:
        self.write_valid_reports()
        carousel = self.root / "compose-simulation/carousel/gemini_catch_carousel_count_report.json"
        carousel.write_text(json.dumps({"count_cases": [{"catch_count": 0}, {"catch_count": 1}]}), encoding="utf-8")
        with self.assertRaisesRegex(EvidenceValidationError, r"expected \[0, 1, 2, 3, 10\]"):
            validate(self.root)

    def test_cli_returns_failure_for_missing_reports(self) -> None:
        script = RUNTIME_DIR / "validate_nh01_gemini_evidence.py"
        completed = subprocess.run(
            [sys.executable, str(script), str(self.root)],
            check=False,
            capture_output=True,
            text=True,
        )
        self.assertEqual(completed.returncode, 1)
        self.assertIn("NH01_GEMINI_EVIDENCE_VALIDATION=FAIL", completed.stderr)
        self.assertIn("MISSING:", completed.stderr)


if __name__ == "__main__":
    unittest.main()
