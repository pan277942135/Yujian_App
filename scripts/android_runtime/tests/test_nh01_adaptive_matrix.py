from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from aggregate_nh01_adaptive_matrix import validate_case


class AdaptiveMatrixAggregateTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.raw = Path(self.temp.name)
        Image.new("RGB", (320, 640), "#668899").save(self.raw / "M01-adaptive_v1.png")
        self.payload = {
            "status": "PASS",
            "execution_mode": "ANDROID_API28_RUNTIME",
            "layout_variant": "ADAPTIVE_V1",
            "source_commit": "a" * 40,
            "system_font_scale": 1.0,
            "density_dpi": 160,
            "display_resolution_px": [320, 640],
            "bounds_in_window_px": {
                name: {"left": 0, "top": 0, "right": 1, "bottom": 1}
                for name in (
                    "gemini-home-screen", "gemini-home-brand-title", "gemini-home-stats-bar",
                    "gemini-home-section-title", "gemini-home-catch-card-nh01-catch-001",
                    "gemini-home-footer-cta", "gemini-home-cta-label", "gemini-home-camera",
                )
            },
            "metrics": {
                "card_aspect_ratio": 247 / 293,
                "card_track_horizontal_center_error_dp": 0.2,
                "cta_camera_overlap": False,
                "camera_safe_bottom_clearance_dp": 8.0,
            },
            "data": {
                "catch_ids": ["nh01-catch-001", "nh01-catch-002"],
                "unique_catch_count": 2,
                "pager_page_count": 2,
                "same_time_duplicate_ids": False,
                "swipe_reached_second_id": True,
                "required_card_text_accessible": True,
            },
            "screenshots": {"initial": "M01-adaptive_v1.png"},
        }

    def tearDown(self) -> None:
        self.temp.cleanup()

    def test_accepts_actual_runtime_report_with_complete_two_catch_evidence(self) -> None:
        self.assertEqual([], validate_case(
            self.payload, "M01", 320, 640, 1.0, "ADAPTIVE_V1", "a" * 40, self.raw,
        ))

    def test_rejects_wrong_shape_and_duplicate_or_missing_catch_identity(self) -> None:
        self.payload["metrics"]["card_aspect_ratio"] = 0.71
        self.payload["data"]["catch_ids"] = ["nh01-catch-001", "nh01-catch-001"]
        problems = validate_case(
            self.payload, "M01", 320, 640, 1.0, "ADAPTIVE_V1", "a" * 40, self.raw,
        )
        self.assertTrue(any("card ratio" in problem for problem in problems), problems)
        self.assertTrue(any("two-catch IDs/uniqueness" in problem for problem in problems), problems)


if __name__ == "__main__":
    unittest.main()
