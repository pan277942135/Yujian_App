from __future__ import annotations

import os
import subprocess
import sys
import tempfile
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
        self.assertIn('instrumentation_rc="$EXIT_FAIL_TEST"', normal_home_source)
        self.assertIn("if (( instrumentation_rc != EXIT_PASS )); then", runtime_source)

    def test_matrix_failure_survives_diagnostic_only_post_hook(self) -> None:
        gate = Path(__file__).resolve().parents[1] / "gates" / "normal_home_v1.sh"
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            adb = root / "fake-adb"
            adb.write_text("#!/usr/bin/env bash\nexit 0\n", encoding="utf-8")
            adb.chmod(0o755)
            runtime = root / "runtime"
            (runtime / "gates").mkdir(parents=True)
            matrix = runtime / "gates" / "run_nh01_adaptive_device_matrix.sh"
            matrix.write_text("#!/usr/bin/env bash\nexit \"${NH01_MATRIX_EXIT:-0}\"\n", encoding="utf-8")
            matrix.chmod(0o755)

            harness = """\
runtime_set_failure() { :; }
source "$NORMAL_HOME_GATE"
instrumentation_rc=0
gate_after_instrumentation || true
printf '%s\\n' "$instrumentation_rc"
"""
            environment = {
                **os.environ,
                "NORMAL_HOME_GATE": str(gate),
                "YUJIAN_ADB_BIN": str(adb),
                "YUJIAN_RUNTIME_DIR": str(runtime),
                "YUJIAN_EVIDENCE_DIR": str(root / "evidence"),
                "YUJIAN_APP_PACKAGE": "com.yujian.ai",
                "YUJIAN_INSTRUMENTATION_TARGET": "com.yujian.ai.test/androidx.test.runner.AndroidJUnitRunner",
                "YUJIAN_BUILD_SHA": "test-sha",
                "APP_APK": "app-debug.apk",
                "TEST_APK": "app-debug-androidTest.apk",
                "EXIT_FAIL_TEST": "30",
            }
            passed = subprocess.run(
                ["bash", "-c", harness], env={**environment, "NH01_MATRIX_EXIT": "0"},
                check=False, capture_output=True, text=True,
            )
            failed = subprocess.run(
                ["bash", "-c", harness], env={**environment, "NH01_MATRIX_EXIT": "1"},
                check=False, capture_output=True, text=True,
            )
            self.assertEqual(0, passed.returncode, passed.stderr)
            self.assertEqual("0", passed.stdout.strip().splitlines()[-1])
            self.assertEqual(0, failed.returncode, failed.stderr)
            self.assertEqual("30", failed.stdout.strip().splitlines()[-1])

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
