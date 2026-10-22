#!/usr/bin/env python3
"""Regression tests for Android foreground verification."""

from pathlib import Path
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import capture_verified_surface


class ForegroundPackageBoundaryTests(unittest.TestCase):
    def test_accepts_application_id_suffix_with_base_package_activity_class(self):
        outputs = iter([
            "mResumedActivity: ActivityRecord{u0 com.yujian.ai.uiv2/com.yujian.ai.MainActivity}",
            "mCurrentFocus=Window{u0 com.yujian.ai.uiv2/com.yujian.ai.MainActivity}",
            "3085",
        ])
        with patch.object(capture_verified_surface, "run", side_effect=lambda *args, **kwargs: next(outputs)):
            self.assertEqual(
                capture_verified_surface.foreground("adb", "com.yujian.ai.uiv2"),
                "com.yujian.ai.uiv2/com.yujian.ai.MainActivity",
            )

    def test_rejects_a_different_foreground_package(self):
        outputs = iter([
            "mResumedActivity: ActivityRecord{u0 com.yujian.ai.other/com.yujian.ai.MainActivity}",
            "mCurrentFocus=Window{u0 com.yujian.ai.other/com.yujian.ai.MainActivity}",
        ])
        with patch.object(capture_verified_surface, "run", side_effect=lambda *args, **kwargs: next(outputs)):
            with self.assertRaisesRegex(RuntimeError, "WRONG_FOREGROUND_APP"):
                capture_verified_surface.foreground("adb", "com.yujian.ai.uiv2")


if __name__ == "__main__":
    unittest.main()
