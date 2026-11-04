from __future__ import annotations

import unittest

from scripts.android_runtime.nh01_matrix_classification import classify_matrix_result


class Nh01MatrixClassificationTest(unittest.TestCase):
    def test_all_three_native_profiles_pass(self) -> None:
        report = {"cases": [{"execution": {"status": "PASS"}} for _ in range(3)]}
        self.assertEqual(("PASS", []), classify_matrix_result(0, report))

    def test_visual_contract_assertion_is_a_product_failure(self) -> None:
        report = {"cases": [{"execution": {
            "status": "FAIL",
            "reason": "failure_class=java.lang.AssertionError; failure_message="
                      "camera artwork frame follows the V4 responsive visual size expected:<96> but was:<88>",
        }}]}
        classification, _ = classify_matrix_result(30, report)
        self.assertEqual("FAIL_PRODUCT", classification)

    def test_capture_pipeline_failure_is_an_evidence_failure(self) -> None:
        report = {"cases": [{"execution": {"status": "FAIL", "reason": "FAIL_CAPTURE_PIPELINE"}}]}
        classification, _ = classify_matrix_result(40, report)
        self.assertEqual("FAIL_EVIDENCE", classification)

    def test_generic_instrumentation_assertion_is_a_test_failure(self) -> None:
        report = {"cases": [{"execution": {
            "status": "FAIL", "reason": "failure_class=java.lang.AssertionError; failure_message=test setup failed",
        }}]}
        classification, _ = classify_matrix_result(30, report)
        self.assertEqual("FAIL_TEST", classification)

    def test_missing_profile_is_blocked_infrastructure(self) -> None:
        report = {"cases": [{"execution": {"status": "PASS"}}, {"execution": {"status": "MISSING"}}]}
        classification, _ = classify_matrix_result(10, report)
        self.assertEqual("BLOCKED_INFRA", classification)


if __name__ == "__main__":
    unittest.main()
