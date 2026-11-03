from __future__ import annotations

import json
import struct
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from scripts.design import capture_nh01_device_metadata as metadata


class Nh01DeviceMetadataTest(unittest.TestCase):
    def test_physical_values_are_not_replaced_by_overrides(self) -> None:
        self.assertEqual(metadata.parse_physical_size("Physical size: 720x1280\nOverride size: 390x844"), (720, 1280))
        self.assertEqual(metadata.parse_override_size("Physical size: 720x1280\nOverride size: 390x844"), (390, 844))
        self.assertEqual(metadata.parse_physical_density("Physical density: 420\nOverride density: 160"), 420)
        self.assertEqual(metadata.parse_override_density("Physical density: 420\nOverride density: 160"), 160)

    def test_all_frozen_native_profiles_are_explicit(self) -> None:
        self.assertEqual(metadata.PROFILES, {
            "M04": {"width_px": 360, "height_px": 640, "density_dpi": 160},
            "M07": {"width_px": 390, "height_px": 844, "density_dpi": 160},
            "M10": {"width_px": 430, "height_px": 932, "density_dpi": 160},
        })

    def test_metadata_capture_is_read_only_and_records_actual_framebuffer(self) -> None:
        png = b"\x89PNG\r\n\x1a\n" + b"\x00" * 8 + struct.pack(">II", 360, 640)
        commands: list[tuple[str, ...]] = []

        def fake_run(command, **kwargs):
            args = tuple(command[1:])
            commands.append(args)
            text_outputs = {
                ("wait-for-device",): "",
                ("shell", "getprop", "ro.build.version.sdk"): "28\n",
                ("shell", "getprop", "ro.product.model"): "Pixel 2\n",
                ("shell", "getprop", "ro.build.fingerprint"): "test/fingerprint\n",
                ("shell", "wm", "size"): "Physical size: 360x640\n",
                ("shell", "wm", "density"): "Physical density: 160\n",
                ("shell", "settings", "get", "system", "font_scale"): "1.0\n",
                ("shell", "dumpsys", "window", "displays"): "window insets dump\n",
                ("shell", "dumpsys", "display"): "display dump\n",
            }
            if args == ("exec-out", "screencap", "-p"):
                return SimpleNamespace(stdout=png, stderr=b"", returncode=0)
            if args not in text_outputs:
                raise AssertionError(f"unexpected ADB command: {args}")
            return SimpleNamespace(stdout=text_outputs[args], stderr="", returncode=0)

        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            out = root / "metadata.json"
            insets = root / "insets.txt"
            screen = root / "screen.png"
            with patch.object(metadata.shutil, "which", return_value="/usr/bin/adb"), \
                 patch.object(metadata.subprocess, "run", side_effect=fake_run), \
                 patch("sys.argv", ["capture_nh01_device_metadata.py", "--profile", "M04",
                                    "--output", str(out), "--insets-dump", str(insets),
                                    "--screencap-output", str(screen)]):
                self.assertEqual(metadata.main(), 0)

            payload = json.loads(out.read_text(encoding="utf-8"))
            self.assertEqual(payload["wm_physical_size_px"], [360, 640])
            self.assertEqual(payload["screencap_dimensions_px"], [360, 640])
            self.assertEqual(payload["display_validation"]["status"], "PASS")
            self.assertEqual(screen.read_bytes(), png)
            self.assertIn("window insets dump", insets.read_text(encoding="utf-8"))
            self.assertFalse(any("put" in command or ("shell", "wm", "size", "reset") == command for command in commands))
            self.assertFalse(any(command[:3] == ("shell", "wm", "size") and len(command) > 3 for command in commands))
            self.assertFalse(any(command[:3] == ("shell", "wm", "density") and len(command) > 3 for command in commands))

    def test_mismatched_actual_frame_is_reported_as_failure(self) -> None:
        self.assertEqual(metadata.png_dimensions(b"invalid"), None)
        self.assertEqual(metadata.font_scale_value("1.15\n"), 1.15)


    def test_capture_writes_failure_evidence_for_wrong_native_size(self) -> None:
        png = b"\x89PNG\r\n\x1a\n" + b"\x00" * 8 + struct.pack(">II", 720, 1280)
        outputs = {
            ("wait-for-device",): "",
            ("shell", "getprop", "ro.build.version.sdk"): "28\n",
            ("shell", "getprop", "ro.product.model"): "Pixel 2\n",
            ("shell", "getprop", "ro.build.fingerprint"): "test/fingerprint\n",
            ("shell", "wm", "size"): "Physical size: 720x1280\n",
            ("shell", "wm", "density"): "Physical density: 420\n",
            ("shell", "settings", "get", "system", "font_scale"): "1.0\n",
            ("shell", "dumpsys", "window", "displays"): "window insets dump\n",
            ("shell", "dumpsys", "display"): "display dump\n",
        }

        def fake_run(command, **kwargs):
            args = tuple(command[1:])
            if args == ("exec-out", "screencap", "-p"):
                return SimpleNamespace(stdout=png, stderr=b"", returncode=0)
            return SimpleNamespace(stdout=outputs[args], stderr="", returncode=0)

        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            out, insets = root / "metadata.json", root / "insets.txt"
            with patch.object(metadata.shutil, "which", return_value="/usr/bin/adb"), \
                 patch.object(metadata.subprocess, "run", side_effect=fake_run), \
                 patch("sys.argv", ["capture_nh01_device_metadata.py", "--profile", "M04",
                                    "--output", str(out), "--insets-dump", str(insets)]):
                with self.assertRaisesRegex(SystemExit, "PHYSICAL_SIZE_EXPECTED_360x640"):
                    metadata.main()
            payload = json.loads(out.read_text(encoding="utf-8"))
            self.assertEqual(payload["display_validation"]["status"], "FAIL")
            self.assertEqual(payload["wm_physical_size_px"], [720, 1280])


if __name__ == "__main__":
    unittest.main()
