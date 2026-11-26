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
        self.assertEqual("PROCESS_CRASH", result["reason"])

    def test_lmk_event_fails_even_when_instrumentation_claims_success(self):
        result = self.evaluate(auxiliary_log="lowmemorykiller: Kill 'com.yujian.ai.uiv2' (pid 124) rss=670552kB")
        self.assertFalse(result["passed"])
        self.assertTrue(result["lmk_or_process_death_observed"])
        self.assertEqual("LMK_CONFIRMED", result["reason"])

    def test_finished_instrumentation_cleanup_is_not_lmk(self):
        result = self.evaluate(auxiliary_log="am_kill: stop com.yujian.ai.uiv2 due to finished inst")
        self.assertTrue(result["passed"])
        self.assertTrue(result["normal_process_cleanup"])
        self.assertFalse(result["lmk_confirmed"])

    def test_unexplained_process_death_is_distinct_from_lmk(self):
        result = self.evaluate(auxiliary_log="am_proc_died: com.yujian.ai.uiv2 pid=1234")
        self.assertFalse(result["passed"])
        self.assertEqual("PROCESS_DEATH_UNKNOWN", result["reason"])

    def test_assertion_failure_is_instrumentation_failure(self):
        result = self.evaluate(passing_log().replace("INSTRUMENTATION_CODE: -1", "FAILURES!!!\nINSTRUMENTATION_CODE: 0"))
        self.assertFalse(result["passed"])
        self.assertEqual("INSTRUMENTATION_FAILURE", result["reason"])

    def test_cleanup_does_not_hide_prior_assertion_failure(self):
        raw = passing_log().replace("INSTRUMENTATION_CODE: -1", "FAILURES!!!\nINSTRUMENTATION_CODE: 0")
        result = self.evaluate(raw, auxiliary_log="am_kill: stop com.yujian.ai.uiv2 due to finished inst")
        self.assertFalse(result["passed"])
        self.assertEqual("INSTRUMENTATION_FAILURE", result["reason"])

    def test_matching_pid_and_time_confirm_lmk(self):
        result = self.evaluate(
            auxiliary_log="11-26 09:00:20.000  100  100 I lmkd: Kill com.yujian.ai.uiv2 (pid 333)",
            app_pids=("333",), start_time_utc="2026-11-26T09:00:00Z", end_time_utc="2026-11-26T09:01:00Z",
        )
        self.assertEqual("LMK_CONFIRMED", result["reason"])

    def test_other_process_lmk_does_not_contaminate_case(self):
        result = self.evaluate(
            auxiliary_log="11-26 09:00:20.000  100  100 I lmkd: Kill com.yujian.ai.uiv2 (pid 444)",
            app_pids=("333",), start_time_utc="2026-11-26T09:00:00Z", end_time_utc="2026-11-26T09:01:00Z",
        )
        self.assertTrue(result["passed"])
        self.assertFalse(result["lmk_confirmed"])

    def test_event_outside_case_window_does_not_contaminate_case(self):
        result = self.evaluate(
            auxiliary_log="11-26 09:02:20.000  100  100 I lmkd: Kill com.yujian.ai.uiv2 (pid 333)",
            app_pids=("333",), start_time_utc="2026-11-26T09:00:00Z", end_time_utc="2026-11-26T09:01:00Z",
        )
        self.assertTrue(result["passed"])

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
