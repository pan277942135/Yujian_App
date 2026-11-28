#!/usr/bin/env python3
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

MODULE_PATH = Path(__file__).with_name("verify_orb_to_glyph_video.py")
SPEC = importlib.util.spec_from_file_location("verify_orb_to_glyph_video", MODULE_PATH)
verify = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(verify)


class OrbToGlyphVideoVerifierTest(unittest.TestCase):
    def test_requires_ordered_kf14_to_kf20_markers(self):
        valid = {f"KF{i:02d}": (i - 14 + 1) * 100 for i in range(14, 21)}
        self.assertEqual(7, len(verify._validate_kf_index(valid, 10_000)))
        with self.assertRaisesRegex(verify.VideoValidationError, "KF_SEQUENCE_NOT_STRICTLY_INCREASING"):
            broken = dict(valid, KF18=valid["KF17"])
            verify._validate_kf_index(broken, 10_000)

    def test_rejects_reencoded_or_replaced_video_against_metadata_hash(self):
        with tempfile.TemporaryDirectory() as directory:
            video = Path(directory, "capture.mp4")
            video.write_bytes(b"real-device-capture-placeholder-for-hash-check")
            metadata = {
                "source": "ANDROID_DEVICE_SCREENRECORD",
                "attemptId": "attempt-1",
                "snapshotId": "snapshot-1",
                "videoSha256": verify._sha256(video),
                "videoSizeBytes": video.stat().st_size,
                "kfTimestampsMs": {f"KF{i:02d}": (i - 13) * 100 for i in range(14, 21)},
                "durationMs": 10_000,
                "decodeStatus": "PASS",
                "frameCount": 10,
            }
            verify.verify_metadata(video, metadata, "attempt-1", "snapshot-1")
            video.write_bytes(b"changed")
            with self.assertRaisesRegex(verify.VideoValidationError, "VIDEO_HASH_OR_SIZE_MISMATCH"):
                verify.verify_metadata(video, metadata, "attempt-1", "snapshot-1")

    def test_inspect_decodes_and_records_stream_pts_and_attempt_binding(self):
        with tempfile.TemporaryDirectory() as directory:
            video = Path(directory, "capture.mp4")
            video.write_bytes(b"unit-test-input")
            probe = {
                "streams": [{
                    "width": 1080, "height": 2400, "codec_name": "h264",
                    "avg_frame_rate": "30/1", "r_frame_rate": "30/1",
                    "nb_read_frames": "60", "duration": "2.0",
                }],
                "format": {"duration": "2.0"},
                "frames": [{"best_effort_timestamp_time": str(index / 30)} for index in range(60)],
            }
            kfs = {f"KF{i:02d}": i * 100 for i in range(14, 21)}
            with patch.object(verify, "_run", side_effect=[json.dumps(probe), ""]):
                result = verify.inspect_video(video, "attempt-2", "snapshot-2", kfs)
            self.assertEqual((1080, 2400), (result["width"], result["height"]))
            self.assertEqual("attempt-2", result["attemptId"])
            self.assertEqual("snapshot-2", result["snapshotId"])
            self.assertEqual(60, result["frameCount"])
            self.assertEqual(60, len(result["ptsMs"]))
            self.assertEqual("PASS", result["decodeStatus"])


if __name__ == "__main__":
    unittest.main()
