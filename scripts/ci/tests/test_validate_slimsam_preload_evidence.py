import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from validate_slimsam_preload_evidence import (  # noqa: E402
    DECODER_SHA256,
    INITIALIZATION_EVENTS,
    VISION_SHA256,
    validate,
)


class SlimSamPreloadEvidenceValidationTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        self.workload = [
            {
                "sequence": index,
                "photo_sha256": f"{index:064x}",
                "recognition_ready": True,
                "vision_encoder_count": 1,
                "mask_decoder_count": 1,
                "vision_session_count": 1,
                "decoder_session_count": 1,
                "cached_photo_count": 1,
                "encoder_detector_overlap_ms": 4.5,
                "encoder_classifier_overlap_ms": 2.5,
            }
            for index in range(1, 6)
        ]
        runtime = {
            "status": "READY",
            "visionSessionReady": True,
            "decoderSessionReady": True,
            "visionModelSha256": VISION_SHA256,
            "decoderModelSha256": DECODER_SHA256,
            "visionSessionCount": 1,
            "decoderSessionCount": 1,
            "failure": None,
            "events": [{"eventName": name} for name in INITIALIZATION_EVENTS],
        }
        timing = {
            "startup_timing": {"preload_completed": True, "app_first_frame_duration_ms": 280.0,
                               "runtime_ready_uptime_ns": 900000000},
            "samePhotoRepeatTimingsMs": [0.5] * 5,
            "samePhotoCacheHitCount": 6,
            "visionEncoderExecutionCount": 2,
            "decoderExecutionCount": 1,
            "distinctPhotoWorkload": self.workload,
            "cancelledPhotoReplacement": {
                "cancellation_requested_after_encoder_start": True,
                "cancelled_task_observed": True,
                "replacement_embedding_ready": True,
                "active_operation_count_after_replace": 0,
                "cached_photo_count_after_replace": 1,
            },
        }
        stages = {"encoderDetectorOverlapMs": 30.0, "encoderClassifierOverlapMs": 20.0}
        samples = [{
            "java_heap_used_bytes": 100,
            "native_heap_allocated_bytes": 200,
            "total_pss_kb": 300,
            "embedding_buffer_bytes": 8 * 1024 * 1024,
            "vision_session_count": 1,
            "decoder_session_count": 1,
            "cached_photo_count": 1,
            "active_operation_count": 0,
        } for _ in range(5)]
        memory = {"samples": samples, "five_photo_growth_delta": {"sample_count": 5}}
        for name, payload in (
            ("model_runtime_state.json", runtime),
            ("slimsam_timing.json", timing),
            ("recognition_stage_timing.json", stages),
            ("memory_profile.json", memory),
        ):
            (self.root / name).write_text(json.dumps(payload), encoding="utf-8")
        (self.root / "slimsam_execution_trace.csv").write_text("eventName\n", encoding="utf-8")

    def tearDown(self):
        self.temp_dir.cleanup()

    def test_complete_startup_parallelism_cache_and_memory_evidence_passes(self):
        result = validate(self.root)
        self.assertTrue(result["passed"], result)
        self.assertEqual("NONE", result["reason"])
        self.assertEqual(5, result["distinct_photo_count"])

    def test_missing_session_initialization_event_fails_closed(self):
        path = self.root / "model_runtime_state.json"
        payload = json.loads(path.read_text(encoding="utf-8"))
        payload["events"] = [event for event in payload["events"] if event["eventName"] != "DECODER_SESSION_CREATE_END"]
        path.write_text(json.dumps(payload), encoding="utf-8")

        result = validate(self.root)

        self.assertFalse(result["passed"])
        self.assertEqual("STARTUP_EVENT_MISSING:DECODER_SESSION_CREATE_END", result["reason"])

    def test_repeated_photo_hash_does_not_count_as_five_distinct_photos(self):
        path = self.root / "slimsam_timing.json"
        payload = json.loads(path.read_text(encoding="utf-8"))
        payload["distinctPhotoWorkload"][4]["photo_sha256"] = payload["distinctPhotoWorkload"][0]["photo_sha256"]
        path.write_text(json.dumps(payload), encoding="utf-8")

        result = validate(self.root)

        self.assertFalse(result["passed"])
        self.assertEqual("DISTINCT_PHOTO_IDENTITIES_DUPLICATED_OR_MISSING", result["reason"])


if __name__ == "__main__":
    unittest.main()
