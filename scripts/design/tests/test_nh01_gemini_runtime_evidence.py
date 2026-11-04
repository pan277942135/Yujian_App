from __future__ import annotations

import hashlib
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[3]
SOURCE_SHA = "a" * 40
PACKAGE_SCRIPT = ROOT / "scripts/design/package_nh01_gemini_runtime_evidence.py"


class Nh01GeminiRuntimeEvidencePackageTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory(prefix="nh01-gemini-package-")
        self.root = Path(self.temporary.name)
        self.evidence = self.root / "evidence"
        self.matrix_dir = self.evidence / "NH01_RUNTIME_MATRIX_V1"
        self.screenshots = self.matrix_dir / "screenshots"
        self.settings_dir = self.matrix_dir / "logs"
        self.raw_dir = self.evidence / "nh01-adaptive-raw/nh01-adaptive-visual-balance-v2"
        self.screenshots.mkdir(parents=True)
        self.settings_dir.mkdir(parents=True)
        self.raw_dir.mkdir(parents=True)
        self.profile_sizes = {
            "M04": (360, 640),
            "M07": (390, 844),
            "M10": (430, 932),
        }
        cases = []
        executions = []
        for profile, size in self.profile_sizes.items():
            case_exec = {
                "profile_id": profile,
                "layout_variant": "adaptive_v2",
                "source_sha": SOURCE_SHA,
                "status": "PASS",
                "duration_seconds": 12.0,
            }
            cases.append({
                "profile": profile,
                "variant": "adaptive_v2",
                "status": "PASS",
                "duration_seconds": 12.0,
                "screenshot_evidence": True,
                "settings_match": True,
                "execution": case_exec,
            })
            executions.append(case_exec)
            (self.settings_dir / f"{profile}_device_settings.json").write_text(
                json.dumps({
                    "profile_id": profile,
                    "requested_native_size_px": f"{size[0]}x{size[1]}",
                    "requested_density_dpi": 160,
                    "requested_font_scale": 1.0,
                    "actual_wm_size": f"{size[0]}x{size[1]}",
                    "actual_wm_density": "160",
                    "actual_font_scale": "1.0",
                    "wm_override_size": None,
                    "wm_override_density_dpi": None,
                    "native_viewport_pass": True,
                }),
                encoding="utf-8",
            )
            for state in ("initial", "after_swipe"):
                Image.new("RGB", size, (80, 180, 200)).save(
                    self.screenshots / f"{profile}_{size[0]}x{size[1]}_{state}.png"
                )
            (self.raw_dir / f"{profile}-adaptive_v2.json").write_text(
                json.dumps({
                    "initial_layout": {
                        "bounds_dp": {
                            "gemini-home-catch-card-fixture": {
                                "left": 55, "top": 190, "right": 305, "bottom": 486,
                                "width": 250, "height": 296,
                            },
                            "gemini-home-camera": {
                                "left": 142, "top": 510, "right": 218, "bottom": 586,
                                "width": 76, "height": 76,
                            },
                            "gemini-home-camera-art": {
                                "left": 146, "top": 514, "right": 214, "bottom": 582,
                                "width": 68, "height": 68,
                            },
                        },
                    },
                }),
                encoding="utf-8",
            )
        (self.evidence / "NH01_THREE_DEVICE_ADAPTIVE_V2_REPORT.json").write_text(
            json.dumps({
                "status": "PASS",
                "source_sha": SOURCE_SHA,
                "device_api": "28",
                "cases": cases,
            }),
            encoding="utf-8",
        )
        (self.matrix_dir / "execution_summary.json").write_text(
            json.dumps({
                "status": "PASS",
                "source_sha": SOURCE_SHA,
                "device_api": "28",
                "execution_records": executions,
            }),
            encoding="utf-8",
        )
        (self.matrix_dir / "fixture_seed.json").write_text(
            json.dumps({"status": "PASS"}),
            encoding="utf-8",
        )
        (self.evidence / "runtime_gate_result.json").write_text(
            json.dumps({
                "classification": "PASS",
                "build_sha": SOURCE_SHA,
                "preflight": "PASS",
                "install": "PASS",
                "instrumentation": "PASS",
                "evidence": "PASS",
            }),
            encoding="utf-8",
        )
        (self.evidence / "NH01_RUNTIME_MATRIX_V1").mkdir(exist_ok=True)
        self.apk = self.root / "app-debug.apk"
        self.test_apk = self.root / "app-debug-androidTest.apk"
        self.apk.write_bytes(b"apk-payload")
        self.test_apk.write_bytes(b"android-test-payload")
        self.source_audit = self.root / "source_audit.txt"
        self.source_audit.write_text("source audit pass\n", encoding="utf-8")
        self.device_metadata = self.root / "device_metadata.json"
        self.device_metadata.write_text('{"profile":"Pixel 2"}\n', encoding="utf-8")

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def run_package(self, artifact: Path) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [
                sys.executable,
                str(PACKAGE_SCRIPT),
                "--evidence-dir", str(self.evidence),
                "--source-sha", SOURCE_SHA,
                "--run-id", "123456789",
                "--run-url", "https://github.com/example/repo/actions/runs/123456789",
                "--pr-url", "https://github.com/example/repo/pull/314",
                "--apk", str(self.apk),
                "--test-apk", str(self.test_apk),
                "--source-audit", str(self.source_audit),
                "--device-metadata", str(self.device_metadata),
                "--artifact-dir", str(artifact),
            ],
            cwd=ROOT,
            check=True,
            capture_output=True,
            text=True,
        )

    def test_current_native_matrix_replaces_stale_legacy_rows_and_hashes_all_deliverables(self) -> None:
        artifact = self.root / "artifact"
        self.run_package(artifact)
        manifest = json.loads((artifact / "NH01_Gemini_Runtime_Manifest_V3.json").read_text())
        report = (artifact / "NH01_Gemini_Native_Validation_Report.md").read_text()
        self.assertEqual("PASS", manifest["classification"])
        self.assertEqual(
            ["M04", "M07", "M10"],
            [row["profile_id"] for row in manifest["native_api28_matrix"]["profiles"]],
        )
        self.assertEqual("PASS", manifest["reference_business_fixture"]["status"])
        self.assertIn("VISUAL_ACCEPTANCE: PENDING_WORK_REVIEW", report)
        self.assertNotIn("| NOT_RUN |", report)
        self.assertIn("SUPERSEDED_BY_RUN_123456789", report)
        sums = {}
        for line in (artifact / "SHA256SUMS").read_text().splitlines():
            digest, filename = line.split("  ", 1)
            sums[filename] = digest
        self.assertIn("NH01_Gemini_Runtime_Manifest_V3.json", sums)
        self.assertEqual(
            sums["NH01_Gemini_Runtime_Manifest_V3.json"],
            hashlib.sha256((artifact / "NH01_Gemini_Runtime_Manifest_V3.json").read_bytes()).hexdigest(),
        )
        self.assertEqual(
            sums["NH01_Gemini_Native_Validation_Report.md"],
            hashlib.sha256((artifact / "NH01_Gemini_Native_Validation_Report.md").read_bytes()).hexdigest(),
        )

    def test_wrong_screenshot_dimensions_fail_native_evidence_gate(self) -> None:
        Image.new("RGB", (320, 480), (1, 2, 3)).save(
            self.screenshots / "M04_360x640_initial.png"
        )
        artifact = self.root / "artifact-bad-size"
        self.run_package(artifact)
        manifest = json.loads((artifact / "NH01_Gemini_Runtime_Manifest_V3.json").read_text())
        self.assertEqual("FAIL_EVIDENCE", manifest["classification"])
        self.assertEqual("FAIL", manifest["native_api28_matrix"]["status"])
        m04 = manifest["native_api28_matrix"]["profiles"][0]
        self.assertEqual([320, 480], m04["screenshots"]["initial"]["size_px"])
        self.assertFalse(m04["screenshots"]["initial"]["dimensions_match"])


if __name__ == "__main__":
    unittest.main()
