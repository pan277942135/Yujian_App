from __future__ import annotations

import unittest
from pathlib import Path
import sys

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from compare_nh01_workspace_visual_v1 import normalize_rendered_image


class Nh01ScreenshotNormalizationTest(unittest.TestCase):
    @staticmethod
    def dom(width=320, height=480):
        return {"viewport": {"width": width, "height": height}}

    def test_exact_size_uses_identity_comparison_copy(self):
        raw = Image.new("RGB", (320, 480), (12, 34, 56))
        normalized, details = normalize_rendered_image(raw, self.dom())

        self.assertEqual(normalized.size, (320, 480))
        self.assertEqual(normalized.tobytes(), raw.tobytes())
        self.assertIsNot(normalized, raw)
        self.assertEqual(details["method"], "identity")
        self.assertEqual(details["raw_size_px"], {"width": 320, "height": 480})
        self.assertEqual(details["normalized_size_px"], {"width": 320, "height": 480})

    def test_one_extra_bottom_row_is_cropped_only_from_comparison_copy(self):
        raw = Image.new("RGB", (320, 481), (12, 34, 56))
        raw.putpixel((10, 480), (255, 0, 0))
        normalized, details = normalize_rendered_image(raw, self.dom())

        self.assertEqual(normalized.size, (320, 480))
        self.assertEqual(normalized.getpixel((10, 479)), (12, 34, 56))
        self.assertEqual(raw.size, (320, 481))
        self.assertEqual(raw.getpixel((10, 480)), (255, 0, 0))
        self.assertEqual(details["method"], "crop_bottom_1px")
        self.assertEqual(details["raw_size_px"], {"width": 320, "height": 481})
        self.assertEqual(details["target_size_css_px"], {"width": 320, "height": 480})

    def test_width_one_pixel_too_large_fails(self):
        with self.assertRaisesRegex(ValueError, "width must exactly match"):
            normalize_rendered_image(Image.new("RGB", (321, 480)), self.dom())

    def test_height_two_pixels_too_large_fails(self):
        with self.assertRaisesRegex(ValueError, "height must exactly match"):
            normalize_rendered_image(Image.new("RGB", (320, 482)), self.dom())

    def test_any_dimension_smaller_than_target_fails(self):
        for size in ((319, 480), (320, 479)):
            with self.subTest(size=size), self.assertRaises(ValueError):
                normalize_rendered_image(Image.new("RGB", size), self.dom())

    def test_missing_or_invalid_dom_viewport_dimensions_fail(self):
        invalid_dom_documents = (
            None,
            {},
            {"viewport": None},
            {"viewport": {"height": 480}},
            {"viewport": {"width": 320, "height": 0}},
            {"viewport": {"width": True, "height": 480}},
            {"viewport": {"width": 320.0, "height": 480}},
        )
        for dom in invalid_dom_documents:
            with self.subTest(dom=dom), self.assertRaises(ValueError):
                normalize_rendered_image(Image.new("RGB", (320, 480)), dom)


if __name__ == "__main__":
    unittest.main()
