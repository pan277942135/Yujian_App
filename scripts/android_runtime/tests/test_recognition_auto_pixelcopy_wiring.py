from __future__ import annotations

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]


class RecognitionAutoPixelCopyWiringTest(unittest.TestCase):
    def _assert_auto_window_capture_wiring(self, relative_path: str, window_expression: str) -> None:
        source = (ROOT / relative_path).read_text(encoding="utf-8")
        draw_start = source.index(".drawWithContent {")
        draw_body = source[draw_start:draw_start + 2_000]
        self.assertIn("drawContent()", draw_body)
        self.assertIn("recognitionEvidenceRecorder?.onFramePresented(", draw_body)
        self.assertIn(f"window = {window_expression}", draw_body)
        self.assertIn("allowPixelCopyCapture = BuildConfig.DEBUG && recognitionEvidenceRecorder != null", draw_body)

    def test_processing_auto_uses_post_draw_real_activity_window(self) -> None:
        self._assert_auto_window_capture_wiring(
            "app/src/main/java/com/yujian/ai/ui/screens/RecognizingScreen.kt",
            "evidenceWindow",
        )

    def test_result_auto_uses_post_draw_real_activity_window(self) -> None:
        self._assert_auto_window_capture_wiring(
            "app/src/main/java/com/yujian/ai/ui/screens/RecognitionResultScreen.kt",
            "recognitionEvidenceWindow(context)",
        )

    def test_pixelcopy_requests_after_frame_commit_and_records_timeout(self) -> None:
        source = (ROOT / "app/src/main/java/com/yujian/ai/ui/recognition/diagnostics/RecognitionPixelCopyCapture.kt").read_text(
            encoding="utf-8"
        )
        self.assertIn("PixelCopy.request(window, bitmap", source)
        self.assertIn("registerFrameCommitCallback", source)
        self.assertIn("POST_COMMIT_FALLBACK_DELAY_MS", source)
        self.assertIn('status = "PIXELCOPY_TIMEOUT"', source)

    def test_recorder_rejects_manual_frames_for_automatic_pixelcopy_evidence(self) -> None:
        source = (ROOT / "app/src/main/java/com/yujian/ai/ui/recognition/diagnostics/RecognitionSessionEvidenceRecorder.kt").read_text(
            encoding="utf-8"
        )
        self.assertIn("frame.playbackMode != ScenePlaybackMode.AUTO", source)
        self.assertIn('"PIXELCOPY_SUPPRESSED_NON_AUTO_FRAME"', source)
        self.assertIn('"sourceDomain" to frame.playbackMode.name', source)


if __name__ == "__main__":
    unittest.main()
