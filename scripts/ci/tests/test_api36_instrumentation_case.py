import importlib.util
from pathlib import Path
import unittest


SCRIPT = Path(__file__).resolve().parents[1] / "api36_instrumentation_case.py"
SPEC = importlib.util.spec_from_file_location("api36_instrumentation_case", SCRIPT)
CASE = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
SPEC.loader.exec_module(CASE)


CLASS_NAME = "com.yujian.ai.ClassifierRealTfliteTensorBindingTest"
METHOD_NAME = "packagedClassifierRunsWithFrozenTensorBinding"


def passing_log(class_name=CLASS_NAME, method_name=METHOD_NAME):
    return "\n".join(
        (
            f"INSTRUMENTATION_STATUS: class={class_name}",
            "INSTRUMENTATION_STATUS: current=1",
            "INSTRUMENTATION_STATUS: numtests=1",
            f"INSTRUMENTATION_STATUS: test={method_name}",
            "INSTRUMENTATION_STATUS_CODE: 1",
            f"INSTRUMENTATION_STATUS: class={class_name}",
            f"INSTRUMENTATION_STATUS: test={method_name}",
            "INSTRUMENTATION_STATUS_CODE: 0",
            "INSTRUMENTATION_RESULT: stream=\n",
            "OK (1 test)",
            "INSTRUMENTATION_CODE: -1",
        )
    )


class Api36InstrumentationCaseTests(unittest.TestCase):
    def evaluate(self, raw=None, **kwargs):
        return CASE.evaluate_case(
            class_name=CLASS_NAME,
            method_name=METHOD_NAME,
            raw_log=passing_log() if raw is None else raw,
            command_exit_code=0,
            **kwargs,
        )

    def test_exact_single_test_passes(self):
        result = self.evaluate()
        self.assertTrue(result["passed"])
        self.assertEqual([-1], result["instrumentation_codes"])
        self.assertEqual([1, 0], result["instrumentation_status_codes"])

    def test_shell_zero_with_instrumentation_code_zero_fails(self):
        raw = passing_log().replace("INSTRUMENTATION_CODE: -1", "INSTRUMENTATION_RESULT: shortMsg=Process crashed\nINSTRUMENTATION_CODE: 0")
        result = self.evaluate(raw)
        self.assertFalse(result["passed"])
        self.assertEqual("PROCESS_DEATH_OR_LOW_MEMORY_MANAGER_EVENT", result["reason"])

    def test_lmk_event_fails_even_when_instrumentation_claims_success(self):
        result = self.evaluate(auxiliary_log="lowmemorykiller: Kill 'com.yujian.ai.uiv2' (pid 124) rss=670552kB")
        self.assertFalse(result["passed"])
        self.assertTrue(result["lmk_or_process_death_observed"])
        self.assertEqual("PROCESS_DEATH_OR_LOW_MEMORY_MANAGER_EVENT", result["reason"])

    def test_wrong_test_name_fails(self):
        result = self.evaluate(passing_log(method_name="someOtherTest"))
        self.assertFalse(result["passed"])
        self.assertFalse(result["started"])

    def test_extra_completed_test_fails(self):
        raw = passing_log().replace("INSTRUMENTATION_STATUS_CODE: 0", "INSTRUMENTATION_STATUS_CODE: 0\nINSTRUMENTATION_STATUS_CODE: 0")
        result = self.evaluate(raw)
        self.assertFalse(result["passed"])
        self.assertEqual("EXPECTED_ONE_COMPLETED_TEST", result["reason"])

    def test_other_method_cannot_supply_the_only_completion(self):
        raw = passing_log().replace(
            "INSTRUMENTATION_STATUS: test=packagedClassifierRunsWithFrozenTensorBinding\nINSTRUMENTATION_STATUS_CODE: 0",
            "INSTRUMENTATION_STATUS: test=someOtherTest\nINSTRUMENTATION_STATUS_CODE: 0",
        )
        result = self.evaluate(raw)
        self.assertFalse(result["passed"])

    def test_another_test_event_in_same_command_fails(self):
        raw = passing_log().replace(
            "INSTRUMENTATION_RESULT: stream=",
            "INSTRUMENTATION_STATUS: class=com.yujian.ai.OtherTest\n"
            "INSTRUMENTATION_STATUS: test=other\n"
            "INSTRUMENTATION_STATUS_CODE: 1\n"
            "INSTRUMENTATION_STATUS: class=com.yujian.ai.OtherTest\n"
            "INSTRUMENTATION_STATUS: test=other\n"
            "INSTRUMENTATION_STATUS_CODE: 0\n"
            "INSTRUMENTATION_RESULT: stream=",
        )
        result = self.evaluate(raw)
        self.assertFalse(result["passed"])

    def test_nonzero_shell_exit_fails(self):
        result = CASE.evaluate_case(
            class_name=CLASS_NAME,
            method_name=METHOD_NAME,
            raw_log=passing_log(),
            command_exit_code=1,
        )
        self.assertFalse(result["passed"])
        self.assertEqual("INSTRUMENTATION_COMMAND_EXIT_1", result["reason"])

    def test_timeout_fails(self):
        result = CASE.evaluate_case(
            class_name=CLASS_NAME,
            method_name=METHOD_NAME,
            raw_log=passing_log(),
            command_exit_code=0,
            timed_out=True,
        )
        self.assertFalse(result["passed"])
        self.assertEqual("INSTRUMENTATION_TIMEOUT", result["reason"])

    def test_no_normal_summary_fails(self):
        result = self.evaluate(passing_log().replace("OK (1 test)", ""))
        self.assertFalse(result["passed"])
        self.assertEqual("NORMAL_ONE_TEST_SUMMARY_MISSING", result["reason"])


if __name__ == "__main__":
    unittest.main()
