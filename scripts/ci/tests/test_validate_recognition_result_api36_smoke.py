import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from validate_recognition_result_api36_smoke import (  # noqa: E402
    REQUIRED_ARTIFACTS,
    REQUIRED_EVENTS,
    REQUIRED_SUBSTAGES,
    REQUIRED_STAGE_SEQUENCE,
    REQUIRED_STAGE_DURATIONS_MS,
    TARGET_CLASS,
    TARGET_METHOD,
    validate_run,
)


SOURCE_SHA = "a" * 40


def instrumentation_case(test, method):
    return {
        "test": f"{test}#{method}",
        "test_method": method,
        "started": True,
        "completed": True,
        "instrumentation_status_codes": [1, 0],
        "instrumentation_codes": [-1],
        "command_exit_code": 0,
        "timed_out": False,
        "lmk_or_process_death_observed": False,
        "normal_summary": True,
        "passed": True,
        "reason": "NONE",
        "app_pids": ["1142"],
        "instrumentation_pids": ["1142"],
        "start_time_utc": "2026-11-26T12:00:00Z",
        "end_time_utc": "2026-11-26T12:00:01Z",
    }


def instrumentation_log(include_finish=True, crashed=False, final_code=-1):
    rows = [
        "INSTRUMENTATION_STATUS: class=" + TARGET_CLASS,
        "INSTRUMENTATION_STATUS: test=" + TARGET_METHOD,
        "INSTRUMENTATION_STATUS_CODE: 1",
    ]
    if include_finish:
        rows.extend([
            "INSTRUMENTATION_STATUS: class=" + TARGET_CLASS,
            "INSTRUMENTATION_STATUS: test=" + TARGET_METHOD,
            "INSTRUMENTATION_STATUS_CODE: 0",
        ])
    rows.append("INSTRUMENTATION_RESULT: stream=\n")
    if crashed:
        rows.append("INSTRUMENTATION_RESULT: shortMsg=Process crashed.")
    rows.append(f"INSTRUMENTATION_CODE: {final_code}")
    return "\n".join(rows) + "\n"


def business_report():
    facts = {
        "slimsam_terminal": {"status": "READY", "quality": "GOOD", "contour_segments": 4, "timestamp_uptime_ms": 100},
        "path_bound": {"binding_state": "BOUND", "path_id": "path-1", "segment_count": 3, "timestamp_uptime_ms": 200},
        "stage10": {"navigation_status": "NAVIGATION_REQUESTED", "navigation_requests": 1, "timestamp_uptime_ms": 300},
        "result_route_attached": {"status": "RESULT_ATTACHED", "result_entries": 1, "timestamp_uptime_ms": 400},
        "return_event": {"accepted": True, "feedback": "RETURN", "source": "FISH_CONTOUR", "render_canvas_receipt_ready": True, "segment_count": 3, "path_id": "path-1", "timestamp_uptime_ms": 500},
        "orb_to_name": {"stage": "ORB_TO_NAME", "timestamp_uptime_ms": 600},
        "glyph": {"glyph_path_ready": True, "glyph_reveal_rendered": True, "name_stable": True, "rendered_progress": 1.0, "fill_path_drawn": True, "contour_path_drawn": True, "draw_pass_sequence": 9, "timestamp_uptime_ms": 700},
        "name_stable": {"accepted": True, "event_sequence": 1, "timestamp_uptime_ms": 700},
        "geometry": {"status": "RESOLVED", "geometry_version": 9, "return_path_segment_count": 3},
        "result_scene": {"accepted": True, "result_scene_stable": True, "all_stages_visited": True, "stage_count": 13, "completed_handoff_substages": REQUIRED_SUBSTAGES, "timestamp_uptime_ms": 800},
    }
    fact_names = [
        "slimsam_terminal", "path_bound", "stage10", "result_route_attached", "return_event",
        "orb_to_name", "glyph", "name_stable", "result_scene",
    ]
    return {
        "status": "PASS",
        "test": f"{TARGET_CLASS}#{TARGET_METHOD}",
        "source_build_sha": SOURCE_SHA,
        "model_contract_build_sha": SOURCE_SHA,
        "event_chain": [
            {"name": event, "timestamp_uptime_ms": facts[fact]["timestamp_uptime_ms"]}
            for event, fact in zip(REQUIRED_EVENTS, fact_names)
        ],
        "facts": facts,
        "evidence_files": REQUIRED_ARTIFACTS,
    }


class Api36SmokeValidationTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.evidence_dir = Path(self.temp_dir.name)
        facts = business_report()["facts"]
        stage_starts = {"ORB_TO_NAME": facts["orb_to_name"]["timestamp_uptime_ms"]}
        stage_rows = []
        current_start = 1
        for stage, duration in zip(REQUIRED_STAGE_SEQUENCE, REQUIRED_STAGE_DURATIONS_MS):
            start = stage_starts.get(stage, current_start)
            stage_rows.append({
                "stage": stage,
                "stage_start_timestamp_ms": start,
                "stage_end_timestamp_ms": start + duration,
                "stage_duration_ms": duration,
                "required_duration_ms": duration,
            })
            current_start = start + duration + 1
        artifacts = {
            "Stage_Timeline.json": {"status": "PASS", "stages": stage_rows},
            "Result_Navigation_Trace.json": {"status": "PASS", "transitions": [
                {"status": status, "monotonicTimestampMs": timestamp} for status, timestamp in [
                    ("CREATED", 1), ("PROCESSING_COMPLETE", 2), ("HANDOFF_READY", 3),
                    ("NAVIGATION_REQUESTED", 300), ("RESULT_ATTACHED", 400), ("HANDOFF_COMPLETED", 800),
                ]
            ]},
            "Result_Geometry.json": {"status": "RESOLVED", "geometry_version": 9, "return_path_segment_count": 3, "return_path_id": "path-1"},
            "Return_Event.json": {"accepted": True, "feedback": "RETURN", "source": "FISH_CONTOUR", "render_canvas_receipt_ready": True, "segment_count": 3, "path_id": "path-1", "timestamp_uptime_ms": 500},
            "Result_Handoff_Trace.json": {
                "transactionStatus": "HANDOFF_COMPLETED",
                "playbackStatus": "COMPLETED",
                "resultStableTimestampMs": 800,
                "geometryReleasedTimestampMs": 900,
                "resultSceneStable": True,
                "events": [
                    {"substage": name, "accepted": True, "glyphPathReady": True, "glyphRevealRendered": True,
                     "glyphNameStable": True, "glyphDrawPassSequence": 1, "glyphDrawProgress": 1.0,
                     "timestamp": 700 if name == "NAME_STABLE" else 800 if name == "RESULT_SCENE_STABLE" else 650 + index}
                    for index, name in enumerate(REQUIRED_SUBSTAGES)
                ],
            },
        }
        for name, payload in artifacts.items():
            (self.evidence_dir / name).write_text(json.dumps(payload), encoding="utf-8")

    def tearDown(self):
        self.temp_dir.cleanup()

    def test_normal_success_is_pass(self):
        result = validate_run(instrumentation_log(), 0, business_report(), SOURCE_SHA,
                              business_evidence_dir=self.evidence_dir)
        self.assertTrue(result.passed, result.reason)

    def test_process_crashed_with_zero_exit_is_rejected(self):
        result = validate_run(instrumentation_log(crashed=True, final_code=0), 0, business_report(), SOURCE_SHA,
                              business_evidence_dir=self.evidence_dir)
        self.assertFalse(result.passed)
        self.assertEqual("INSTRUMENTATION_ABORT_OR_TEST_FAILURE", result.reason)

    def test_missing_test_completion_marker_is_rejected(self):
        result = validate_run(instrumentation_log(include_finish=False), 0, business_report(), SOURCE_SHA,
                              business_evidence_dir=self.evidence_dir)
        self.assertFalse(result.passed)
        self.assertEqual("TARGET_TEST_COMPLETION_MISSING_OR_DUPLICATED", result.reason)


class Api36SmokeSummaryFinalizerTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        self.evidence_dir = self.root / "evidence"
        self.evidence_dir.mkdir()
        self.apk_dir = self.root / "apks"
        self.apk_dir.mkdir()
        self.result_path = self.evidence_dir / "runtime-result.json"
        self.summary_path = self.root / "step-summary.md"
        self.result_path.write_text(json.dumps({
            "source_sha": SOURCE_SHA,
            "classification": "AUTO_SMOKE_PASS",
            "failure_reason": "NONE",
            "validation_reason": "NONE",
            "instrumentation_started": True,
            "instrumentation_attempted": True,
            "instrumentation_command_exit_code": 0,
            "instrumentation_timed_out": False,
            "emulator_boot_pass": True,
            "EMULATOR_BOOT_PASS": True,
            "BUILD_PASS": True,
            "KVM_READY": True,
            "AUTO_SMOKE_PASS": True,
            "instrumentation_cases": {
                "classifier_tensor_binding": instrumentation_case(
                    "com.yujian.ai.ClassifierRealTfliteTensorBindingTest",
                    "packagedClassifierRunsWithFrozenTensorBinding",
                ),
                "production_night_catch_reveal": instrumentation_case(
                    "com.yujian.ai.RecognitionRevealProductionRuntimeTest",
                    "realNightCatchHandoffDrivesTheNativeResultReveal",
                ),
                "production_home_picker_auto": instrumentation_case(
                    "com.yujian.ai.RecognitionResultHandoffProductionNavigationTest",
                    "hostedApi36HomePickerAutoRecognitionCompletesResultHandoff",
                ),
            },
            "CLASSIFIER_TFLITE_RUNTIME_PASS": True,
            "PRODUCTION_REVEAL_INSTRUMENTATION_PASS": True,
            "AUTO_PRODUCTION_SMOKE_PASS": True,
            "API36_INSTRUMENTATION_PASS": True,
            "APP_FORCE_STOP_VERIFIED_BETWEEN_TESTS": True,
            "APP_FORCE_STOP_VERIFIED_BETWEEN_REVEAL_AND_AUTO": True,
            "DEVICE_QA_PENDING": True,
        }), encoding="utf-8")
        (self.evidence_dir / "host-memory-preflight.json").write_text(json.dumps({
            "mem_total_kb": 16000000,
            "mem_available_kb": 12000000,
            "swap_total_kb": 0,
            "swap_free_kb": 0,
        }), encoding="utf-8")
        (self.evidence_dir / "avd-config-before-launch.txt").write_text(
            "hw.ramSize=4096M\nhw.heapSize=512M\n", encoding="utf-8"
        )
        (self.apk_dir / "app-debug.apk").write_bytes(b"app-apk-fixture")
        (self.apk_dir / "app-debug-androidTest.apk").write_bytes(b"android-test-apk-fixture")

    def tearDown(self):
        self.temp_dir.cleanup()

    def finalize(self, smoke_result="success", corrupt_host_json=False):
        if corrupt_host_json:
            (self.evidence_dir / "host-memory-preflight.json").write_text("{bad", encoding="utf-8")
        script = Path(__file__).resolve().parents[1] / "finalize_recognition_result_api36_smoke.py"
        return subprocess.run([
            sys.executable,
            str(script),
            "--result", str(self.result_path),
            "--evidence-dir", str(self.evidence_dir),
            "--summary", str(self.summary_path),
            "--apk-dir", str(self.apk_dir),
            "--build-result", "success",
            "--smoke-result", smoke_result,
            "--source-sha", SOURCE_SHA,
            "--kvm-ready", "true",
        ], capture_output=True, text=True, check=False)

    def configure_e2e_result(self, classification, single_status, five_status):
        payload = json.loads(self.result_path.read_text(encoding="utf-8"))
        payload.update({
            "e2e_acceptance_mode": "API36_SINGLE_AND_FIVE_PHOTO",
            "single_photo_e2e_status": single_status,
            "five_photo_e2e_status": five_status,
            "API36_SINGLE_PHOTO_E2E_PASS": single_status == "PASS",
            "API36_FIVE_PHOTO_E2E_PASS": five_status == "PASS",
            "API36_E2E_ACCEPTANCE_PASS": single_status == "PASS" and five_status == "PASS",
            "classification": classification,
            "failure_reason": "fixture reason",
        })
        self.result_path.write_text(json.dumps(payload), encoding="utf-8")

    def test_single_photo_pass_with_missing_five_photo_set_does_not_claim_full_acceptance(self):
        self.configure_e2e_result(
            "SINGLE_PHOTO_PASS_FIVE_PHOTO_FIXTURE_SET_BLOCKED",
            "PASS",
            "NOT_RUN_FIVE_PHOTO_FIXTURE_SET_UNDEFINED",
        )
        process = self.finalize(smoke_result="failure")
        payload = json.loads(self.result_path.read_text(encoding="utf-8"))
        self.assertEqual(0, process.returncode, process.stderr)
        self.assertIs(payload["API36_SINGLE_PHOTO_E2E_PASS"], True)
        self.assertIs(payload["API36_FIVE_PHOTO_E2E_PASS"], False)
        self.assertIs(payload["API36_E2E_ACCEPTANCE_PASS"], False)
        self.assertEqual("SINGLE_PHOTO_PASS_FIVE_PHOTO_FIXTURE_SET_BLOCKED", payload["classification"])

    def test_stale_e2e_success_classification_is_downgraded(self):
        self.configure_e2e_result(
            "YUJIAN_P0_API36_E2E_ACCEPTANCE_PASS",
            "FAIL",
            "NOT_RUN_SINGLE_PHOTO_GATE_BLOCKED",
        )
        process = self.finalize(smoke_result="failure")
        payload = json.loads(self.result_path.read_text(encoding="utf-8"))
        self.assertEqual(0, process.returncode, process.stderr)
        self.assertIs(payload["API36_E2E_ACCEPTANCE_PASS"], False)
        self.assertEqual("INFRA_BLOCKED", payload["classification"])
        self.assertIn("API36_E2E_ACCEPTANCE_GATE_FAILED", payload["failure_reason"])

    def test_valid_exact_head_auto_pass_is_preserved(self):
        process = self.finalize()
        payload = json.loads(self.result_path.read_text(encoding="utf-8"))
        self.assertEqual(0, process.returncode, process.stderr)
        self.assertEqual("AUTO_SMOKE_PASS", payload["classification"])
        self.assertIs(payload["AUTO_SMOKE_PASS"], True)
        self.assertEqual(4096, payload["avd_ram_mb"])
        self.assertEqual(512, payload["avd_vm_heap_mb"])
        self.assertTrue(payload["app_apk_sha256"])
        self.assertIn("runtime-result.json", (self.evidence_dir / "evidence-sha256sums.txt").read_text())
        self.assertFalse((self.evidence_dir / "summary-finalization-error.txt").exists())

    def test_runner_failure_downgrades_a_prior_auto_flag(self):
        process = self.finalize(smoke_result="failure")
        payload = json.loads(self.result_path.read_text(encoding="utf-8"))
        self.assertEqual(0, process.returncode, process.stderr)
        self.assertEqual("INFRA_BLOCKED", payload["classification"])
        self.assertEqual("AUTO_PASS_GATE_CONTRADICTION:SMOKE_RUNNER_STEP", payload["failure_reason"])
        self.assertIs(payload["AUTO_SMOKE_PASS"], False)

    def test_missing_runtime_source_sha_cannot_be_filled_from_expected_sha(self):
        payload = json.loads(self.result_path.read_text(encoding="utf-8"))
        payload.pop("source_sha")
        self.result_path.write_text(json.dumps(payload), encoding="utf-8")
        process = self.finalize()
        payload = json.loads(self.result_path.read_text(encoding="utf-8"))
        self.assertEqual(0, process.returncode, process.stderr)
        self.assertIsNone(payload.get("source_sha"))
        self.assertEqual("INFRA_BLOCKED", payload["classification"])
        self.assertEqual("RUNTIME_RESULT_SOURCE_SHA_MISSING", payload["failure_reason"])
        self.assertIs(payload["AUTO_SMOKE_PASS"], False)

    def test_boot_failure_cannot_keep_auto_pass(self):
        payload = json.loads(self.result_path.read_text(encoding="utf-8"))
        payload["EMULATOR_BOOT_PASS"] = False
        self.result_path.write_text(json.dumps(payload), encoding="utf-8")
        process = self.finalize()
        payload = json.loads(self.result_path.read_text(encoding="utf-8"))
        self.assertEqual(0, process.returncode, process.stderr)
        self.assertEqual("INFRA_BLOCKED", payload["classification"])
        self.assertEqual("AUTO_PASS_GATE_CONTRADICTION:EMULATOR_BOOT_PASS", payload["failure_reason"])
        self.assertIs(payload["AUTO_SMOKE_PASS"], False)

    def test_missing_classifier_instrumentation_cannot_keep_auto_pass(self):
        payload = json.loads(self.result_path.read_text(encoding="utf-8"))
        payload["instrumentation_cases"].pop("classifier_tensor_binding")
        payload["CLASSIFIER_TFLITE_RUNTIME_PASS"] = False
        payload["API36_INSTRUMENTATION_PASS"] = False
        self.result_path.write_text(json.dumps(payload), encoding="utf-8")
        process = self.finalize()
        payload = json.loads(self.result_path.read_text(encoding="utf-8"))
        self.assertEqual(0, process.returncode, process.stderr)
        self.assertEqual("INFRA_BLOCKED", payload["classification"])
        self.assertIn("CLASSIFIER_TFLITE_RUNTIME_PASS", payload["failure_reason"])
        self.assertIs(payload["AUTO_SMOKE_PASS"], False)

    def test_missing_real_home_picker_auto_cannot_keep_auto_pass(self):
        payload = json.loads(self.result_path.read_text(encoding="utf-8"))
        payload["instrumentation_cases"].pop("production_home_picker_auto")
        self.result_path.write_text(json.dumps(payload), encoding="utf-8")
        process = self.finalize()
        payload = json.loads(self.result_path.read_text(encoding="utf-8"))
        self.assertEqual(0, process.returncode, process.stderr)
        self.assertEqual("INFRA_BLOCKED", payload["classification"])
        self.assertIn("AUTO_PRODUCTION_SMOKE_PASS", payload["failure_reason"])
        self.assertIs(payload["AUTO_SMOKE_PASS"], False)

    def test_lmk_marked_classifier_case_cannot_keep_auto_pass(self):
        payload = json.loads(self.result_path.read_text(encoding="utf-8"))
        payload["instrumentation_cases"]["classifier_tensor_binding"]["lmk_or_process_death_observed"] = True
        self.result_path.write_text(json.dumps(payload), encoding="utf-8")
        process = self.finalize()
        payload = json.loads(self.result_path.read_text(encoding="utf-8"))
        self.assertEqual(0, process.returncode, process.stderr)
        self.assertEqual("INFRA_BLOCKED", payload["classification"])
        self.assertIn("CLASSIFIER_TFLITE_RUNTIME_PASS", payload["failure_reason"])
        self.assertIs(payload["AUTO_SMOKE_PASS"], False)

    def test_failed_case_diagnostics_are_visible_in_summary_and_annotations(self):
        payload = json.loads(self.result_path.read_text(encoding="utf-8"))
        failed_case = payload["instrumentation_cases"]["production_home_picker_auto"]
        failed_case.update({
            "passed": False,
            "failure_phase": "SLIMSAM_VALIDATION",
            "failure_reason": "SLIMSAM_RUNTIME_EVIDENCE_INVALID:ENCODER_DEADLINE_EXCEEDED",
            "reason": "SLIMSAM_RUNTIME_EVIDENCE_INVALID:ENCODER_DEADLINE_EXCEEDED",
            "root_cause": "SLIMSAM_TIMEOUT",
            "exception": "EncoderInferenceTimeoutException",
            "command_exit_code": 1,
            "timed_out": True,
            "relevant_log_excerpt": "encoder stage exceeded shared model deadline; attempt=abc123",
        })
        payload["classification"] = "BUSINESS_LOGIC_FAILURE"
        payload["failure_reason"] = "SLIMSAM_RUNTIME_EVIDENCE_INVALID:ENCODER_DEADLINE_EXCEEDED"
        payload["AUTO_SMOKE_PASS"] = False
        payload["AUTO_PRODUCTION_SMOKE_PASS"] = False
        self.result_path.write_text(json.dumps(payload), encoding="utf-8")

        process = self.finalize(smoke_result="failure")

        summary = self.summary_path.read_text(encoding="utf-8")
        self.assertEqual(0, process.returncode, process.stderr)
        self.assertIn("Test C — Home picker AUTO", summary)
        self.assertIn("SLIMSAM_VALIDATION", summary)
        self.assertIn("ENCODER_DEADLINE_EXCEEDED", summary)
        self.assertIn("SLIMSAM_TIMEOUT", summary)
        self.assertIn("EncoderInferenceTimeoutException", summary)
        self.assertIn("encoder stage exceeded shared model deadline", summary)
        self.assertIn("::error title=API36 Test C — Home picker AUTO failed::", process.stdout)
        self.assertIn("root_cause=SLIMSAM_TIMEOUT", process.stdout)
        self.assertIn("ENCODER_DEADLINE_EXCEEDED", process.stdout)

    def test_bad_memory_input_is_reported_and_fails_closed(self):
        process = self.finalize(corrupt_host_json=True)
        payload = json.loads(self.result_path.read_text(encoding="utf-8"))
        self.assertEqual(0, process.returncode, process.stderr)
        self.assertEqual("INFRA_BLOCKED", payload["classification"])
        self.assertIs(payload["AUTO_SMOKE_PASS"], False)
        self.assertTrue(payload["failure_reason"].startswith("SMOKE_RESULT_FINALIZATION_FAILED:"))
        self.assertTrue((self.evidence_dir / "summary-finalization-error.txt").is_file())
        self.assertIn("INFRA_BLOCKED", self.summary_path.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
