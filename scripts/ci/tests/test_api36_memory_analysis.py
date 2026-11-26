import json
from pathlib import Path
import sys
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch

with patch.object(sys, "path", [str(Path(__file__).resolve().parents[1]), *sys.path]):
    from api36_memory_analysis import analyze


class Api36MemoryAnalysisTests(unittest.TestCase):
    def setUp(self):
        temp = TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)

    def write(self, name, text):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")

    def sample(self, uptime, available, swap_free=200000):
        return (
            f"===== EMULATOR_MEMORY_SNAPSHOT sample-{uptime} =====\n"
            f"MemAvailable: {available} kB\nSwapTotal: 200000 kB\nSwapFree: {swap_free} kB\n"
            f"{uptime:.2f} 100.00\nVmRSS: 150000 kB\n TOTAL PSS: 120000 kB\n"
        )

    def test_background_lmk_and_emulator_pressure_coexist(self):
        kill = "11-26 10:00:01.000 100 101 I lowmemorykiller: Kill 'com.android.settings' (pid 50), uid 1000\n"
        self.write("logcat-live.txt", kill)
        self.write("logcat.txt", kill)
        self.write("emulator-memory-snapshots.txt", self.sample(6, 475816, 100000))
        result = analyze(self.root)
        self.assertEqual("BACKGROUND_LMK_PRESSURE", result["resource_classification"])
        self.assertEqual(["BACKGROUND_LMK_PRESSURE", "EMULATOR_MEMORY_PRESSURE"],
                         result["resource_classifications"])
        self.assertEqual(1, len(result["background_lmk_events"]))
        self.assertFalse(result["lowmemorykiller_app_kill_observed"])

    def test_meaningful_swap_detected_with_ample_available_memory(self):
        self.write("emulator-memory-snapshots.txt", self.sample(6, 3000000, 100000))
        self.assertEqual("EMULATOR_MEMORY_PRESSURE", analyze(self.root)["resource_classification"])

    def test_configured_unused_swap_does_not_indicate_pressure(self):
        self.write("emulator-memory-snapshots.txt", self.sample(6, 3000000))
        self.assertEqual("NO_MEMORY_PRESSURE", analyze(self.root)["resource_classification"])

    def test_app_lmk_and_normal_cleanup_are_distinguished(self):
        for field, classification in (
            ("lmk_confirmed", "APP_LMK_CONFIRMED"),
            ("normal_process_cleanup", "NORMAL_PROCESS_CLEANUP"),
        ):
            with self.subTest(field=field):
                self.write("test-cases/production-auto/result.json", json.dumps({field: True}))
                self.assertEqual(classification, analyze(self.root)["resource_classification"])

    def test_each_terminal_retains_only_in_window_rss_and_pss(self):
        self.write("emulator-memory-snapshots.txt", "".join(
            self.sample(second, 3000000) for second in (4, 6, 9, 10)
        ))
        for marker, terminal in (
            ("SLIMSAM_END uptime_ms=9000 status=READY", "SLIMSAM_END"),
            ("ATTEMPT_DEADLINE_EXCEEDED stage=SLIMSAM attempt=one", "ATTEMPT_DEADLINE_EXCEEDED"),
            ("event=SLIMSAM_RESULT_FAILED attempt=one", "SLIMSAM_RESULT_FAILED"),
            ("event=NATIVE_RUN_RETURNED attemptId=one", "NATIVE_RUN_RETURNED"),
        ):
            with self.subTest(terminal=terminal):
                self.write("test-cases/production-auto/logcat.txt",
                           "11-26 10:00:00.000 42 42 I Recognizing: SLIMSAM_START attempt=one uptime_ms=5000\n"
                           "11-26 10:00:02.000 99 99 E Recognizing: ATTEMPT_DEADLINE_EXCEEDED stage=SLIMSAM\n"
                           f"11-26 10:00:04.000 42 42 I Recognizing: {marker}\n")
                result = analyze(self.root)
                self.assertEqual([{"start_uptime_ms": 5000, "end_uptime_ms": 9000,
                                   "end_event": terminal}], result["slimsam_memory_windows"])
                samples = result["slimsam_memory_window_samples"]
                self.assertEqual([6000, 9000], [sample["uptime_ms"] for sample in samples])
                self.assertEqual(150000, result["app_rss_peak_in_slimsam_window_kb"])
                self.assertEqual([120000, 120000], [sample["app_total_pss_kb"] for sample in samples])
