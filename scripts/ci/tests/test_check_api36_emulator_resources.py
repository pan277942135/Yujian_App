import os
from pathlib import Path
import tempfile
from unittest import TestCase, mock

from scripts.ci.check_api36_emulator_resources import main, resolve_serial


class Api36EmulatorSerialTest(TestCase):
    def test_discovers_only_online_emulator_when_runner_does_not_set_serial(self):
        with mock.patch.dict(os.environ, {"ANDROID_SERIAL": ""}), mock.patch(
            "scripts.ci.check_api36_emulator_resources.subprocess.run",
            return_value=mock.Mock(stdout="List of devices attached\nemulator-5554\tdevice\n"),
        ):
            self.assertEqual("emulator-5554", resolve_serial())

    def test_rejects_ambiguous_adb_target(self):
        with mock.patch.dict(os.environ, {"ANDROID_SERIAL": ""}), mock.patch(
            "scripts.ci.check_api36_emulator_resources.subprocess.run",
            return_value=mock.Mock(stdout="List of devices attached\nemulator-5554\tdevice\nemulator-5556\tdevice\n"),
        ):
            with self.assertRaisesRegex(ValueError, "expected one online emulator"):
                resolve_serial()

    def test_runtime_resource_gate_uses_guest_cpu_and_heap(self):
        with tempfile.TemporaryDirectory() as root:
            avd = Path(root) / "test.avd"
            avd.mkdir()
            (avd / "config.ini").write_text(
                "hw.cpu.ncore=4\nhw.ramSize=4096\nhw.heapSize=512\nvm.heapSize=512\n")
            (avd / "hardware-qemu.ini").write_text("hw.ramSize=4096\n")
            before = os.getcwd()
            try:
                os.chdir(root)
                with mock.patch.dict(os.environ, {"ANDROID_SERIAL": "emulator-5554", "ANDROID_AVD_HOME": root}), mock.patch(
                    "scripts.ci.check_api36_emulator_resources.subprocess.run",
                    return_value=mock.Mock(stdout="4\nMemTotal: 4000000 kB\n512m\n"),
                ):
                    self.assertEqual(0, main())
                    report = Path("evidence/runtime/recognition-result-api36/emulator-resources.txt").read_text()
                    self.assertIn("API36_EMULATOR_RESOURCES_PASS", report)
            finally:
                os.chdir(before)

    def test_two_online_cores_fail_even_with_four_core_config(self):
        with tempfile.TemporaryDirectory() as root:
            avd = Path(root) / "test.avd"
            avd.mkdir()
            (avd / "config.ini").write_text(
                "hw.cpu.ncore=4\nhw.ramSize=4096\nhw.heapSize=512\nvm.heapSize=512\n")
            (avd / "hardware-qemu.ini").write_text("hw.ramSize=4096\n")
            before = os.getcwd()
            try:
                os.chdir(root)
                with mock.patch.dict(os.environ, {"ANDROID_SERIAL": "emulator-5554", "ANDROID_AVD_HOME": root}), mock.patch(
                    "scripts.ci.check_api36_emulator_resources.subprocess.run",
                    return_value=mock.Mock(stdout="2\nMemTotal: 4000000 kB\n512m\n"),
                ):
                    self.assertEqual(1, main())
            finally:
                os.chdir(before)
