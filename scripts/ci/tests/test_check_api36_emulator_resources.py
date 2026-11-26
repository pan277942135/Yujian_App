import os
from unittest import TestCase, mock

from scripts.ci.check_api36_emulator_resources import resolve_serial


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
