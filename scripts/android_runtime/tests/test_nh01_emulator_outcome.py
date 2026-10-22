from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from nh01_emulator_outcome import classify_emulator_outcome


class Nh01EmulatorOutcomeTest(unittest.TestCase):
    def test_cancelled_step_is_not_reported_as_an_emulator_boot_failure(self) -> None:
        result = classify_emulator_outcome(True, "cancelled")
        self.assertEqual("CANCELLED", result.classification)
        self.assertEqual("WORKFLOW_CANCELLATION", result.failure_phase)
        self.assertEqual("ANDROID_EMULATOR_STEP_CANCELLED_REASON_UNAVAILABLE", result.failure_reason)

    def test_failed_step_without_result_is_a_blocked_runner_failure(self) -> None:
        result = classify_emulator_outcome(True, "failure")
        self.assertEqual("BLOCKED_INFRA", result.classification)
        self.assertEqual("EMULATOR_RUNNER", result.failure_phase)
        self.assertEqual("ANDROID_EMULATOR_STEP_FAILED_BEFORE_RUNTIME_RESULT", result.failure_reason)

    def test_unavailable_hosted_runner_is_reported_separately(self) -> None:
        result = classify_emulator_outcome(False, "skipped")
        self.assertEqual("BLOCKED_INFRA", result.classification)
        self.assertEqual("STANDARD_INTEL_MACOS_EMULATOR_RUNNER_UNAVAILABLE", result.failure_reason)

    def test_success_without_runtime_json_is_an_evidence_failure(self) -> None:
        result = classify_emulator_outcome(True, "success")
        self.assertEqual("FAIL_EVIDENCE", result.classification)
        self.assertEqual("EVIDENCE", result.failure_phase)
        self.assertEqual("ANDROID_EMULATOR_STEP_SUCCEEDED_WITHOUT_RUNTIME_RESULT", result.failure_reason)


if __name__ == "__main__":
    unittest.main()
