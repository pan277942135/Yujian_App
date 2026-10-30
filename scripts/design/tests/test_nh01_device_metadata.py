from __future__ import annotations

import unittest

from scripts.design.capture_nh01_device_metadata import parse_wm_density, parse_wm_size


class Nh01DeviceMetadataTest(unittest.TestCase):
    def test_density_override_takes_precedence_over_physical_density(self) -> None:
        self.assertEqual(
            parse_wm_density("Physical density: 420\nOverride density: 480"),
            480,
        )

    def test_physical_density_is_used_when_no_override_exists(self) -> None:
        self.assertEqual(parse_wm_density("Physical density: 480"), 480)

    def test_size_override_takes_precedence_over_physical_size(self) -> None:
        self.assertEqual(
            parse_wm_size("Physical size: 720x1280\nOverride size: 1080x1920"),
            (1080, 1920),
        )

    def test_physical_size_is_used_when_no_override_exists(self) -> None:
        self.assertEqual(parse_wm_size("Physical size: 1080x1920"), (1080, 1920))


if __name__ == "__main__":
    unittest.main()
