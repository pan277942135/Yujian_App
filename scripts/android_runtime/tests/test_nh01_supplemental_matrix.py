from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from validate_nh01_supplemental_matrix import (  # noqa: E402
    BOUNDARY_HEIGHTS,
    FONT_CASES,
    MATRIX_CASES,
    STATE_CASES,
    validate_continuity,
)


class Nh01SupplementalMatrixTest(unittest.TestCase):
    def test_contract_adds_33_v2_cases_and_four_scoped_state_runs(self) -> None:
        self.assertEqual(33, len(MATRIX_CASES))
        self.assertEqual(4, len(STATE_CASES))
        self.assertEqual({1.0, 1.3, 1.5}, {case["font_scale"] for case in MATRIX_CASES})
        self.assertEqual({459, 460, 499, 500, 699, 700, 839, 840}, set(BOUNDARY_HEIGHTS))
        self.assertEqual(57, 24 + len(MATRIX_CASES))
        self.assertEqual(61, 24 + len(MATRIX_CASES) + len(STATE_CASES))

    def test_every_expected_profile_is_registered_in_the_runtime_runner(self) -> None:
        runner = Path(__file__).resolve().parents[1] / "gates" / "run_nh01_adaptive_device_matrix.sh"
        source = runner.read_text(encoding="utf-8")
        for case in MATRIX_CASES:
            with self.subTest(profile=case["profile_id"]):
                self.assertIn(case["profile_id"], source)
        for profile, _scenario in STATE_CASES:
            with self.subTest(profile=profile):
                self.assertIn(profile, source)

    def test_api28_normal_home_gate_executes_and_enforces_the_matrix(self) -> None:
        normal_home = Path(__file__).resolve().parents[1] / "gates" / "normal_home_v1.sh"
        runtime = Path(__file__).resolve().parents[2] / "run_android_runtime_gate.sh"
        normal_home_source = normal_home.read_text(encoding="utf-8")
        runtime_source = runtime.read_text(encoding="utf-8")
        self.assertIn("run_nh01_adaptive_device_matrix.sh", normal_home_source)
        self.assertIn("NH01_ADAPTIVE_V2_DEVICE_MATRIX_FAILED", normal_home_source)
        self.assertIn("after_instrumentation_rc=$?", runtime_source)
        self.assertIn("instrumentation_rc=$after_instrumentation_rc", runtime_source)

    def test_continuity_gate_rejects_a_runtime_discontinuity(self) -> None:
        rows = {}
        for lower, upper in zip(BOUNDARY_HEIGHTS[::2], BOUNDARY_HEIGHTS[1::2]):
            for font_name, _scale in FONT_CASES:
                for height in (lower, upper):
                    rows[f"BOUNDARY-SAFE{height}-{font_name}"] = {
                        "status": "PASS",
                        "metrics": {
                            "card_actual_width_dp": 170.0,
                            "section_title_to_card_gap_dp": 12.0,
                            "stats_to_title_gap_dp": 14.0,
                            "card_to_footer_gap_dp": 8.0,
                        },
                    }
        self.assertEqual([], validate_continuity(rows))
        rows["BOUNDARY-SAFE460-F10"]["metrics"]["card_actual_width_dp"] = 174.0
        problems = validate_continuity(rows)
        self.assertTrue(any("459/460" in problem and "card_actual_width_dp" in problem for problem in problems), problems)


if __name__ == "__main__":
    unittest.main()
