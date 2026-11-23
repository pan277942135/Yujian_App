import json
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


if __name__ == "__main__":
    unittest.main()
