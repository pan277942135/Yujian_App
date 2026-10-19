from __future__ import annotations

import hashlib
import json
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[3]
SOURCE_SHA = "1234567890abcdef1234567890abcdef12345678"


class Nh01RuntimePackageTest(unittest.TestCase):
    def test_artifact_contains_exact_sha_runtime_evidence_and_recursive_checksums(self) -> None:
        with tempfile.TemporaryDirectory(prefix="nh01-runtime-package-") as temp:
            root = Path(temp)
            evidence_dir = root / "evidence"
            normal_home = evidence_dir / "normal-home-v1"
            normal_home.mkdir(parents=True)
            web_root = root / "web"
            data_dir = web_root / "design/manager/nh01_acceptance/data"
            data_dir.mkdir(parents=True)
            for source in (ROOT / "design/manager/nh01_acceptance/data").glob("*.json"):
                shutil.copy2(source, data_dir / source.name)

            Image.new("RGB", (1080, 1920), (8, 16, 24)).save(
                normal_home / "04_normal_home_runtime_1080x1920.png"
            )
            (normal_home / "Normal_Home_Runtime_Bounds_Report.json").write_text(
                '{"classification":"CAPTURED","build_sha":"' + SOURCE_SHA + '"}\n',
                encoding="utf-8",
            )
            (evidence_dir / "instrumentation.log").write_text(
                "instrumentation exact-source evidence\n", encoding="utf-8"
            )
            (evidence_dir / "runtime_gate_result.json").write_text(
                json.dumps({"classification": "BLOCKED_INFRA", "build_sha": SOURCE_SHA}),
                encoding="utf-8",
            )
            apk = root / "app-debug.apk"
            apk.write_bytes(b"test-apk-payload")
            device = root / "device.json"
            device.write_text('{"profile":"Pixel 2","api_level":28}\n', encoding="utf-8")
            artifact_dir = root / "artifact"

            subprocess.run(
                [
                    sys.executable,
                    str(ROOT / "scripts/design/package_nh01_runtime_evidence.py"),
                    "--evidence-dir", str(evidence_dir),
                    "--source-sha", SOURCE_SHA,
                    "--run-id", "987654321",
                    "--run-url", "https://github.com/example/repo/actions/runs/987654321",
                    "--apk", str(apk),
                    "--device-metadata", str(device),
                    "--artifact-dir", str(artifact_dir),
                    "--web-root", str(web_root),
                ],
                cwd=ROOT,
                check=True,
                capture_output=True,
                text=True,
            )

            expected = {
                "app-debug.apk",
                "device_metadata.json",
                "runtime_gate_result.json",
                f"evidence/{SOURCE_SHA}/NH01_Android_Runtime_1080x1920.png",
                f"evidence/{SOURCE_SHA}/NH01_Android_Bounds.json",
                f"evidence/{SOURCE_SHA}/private/NH01_Instrumentation.log",
            }
            manifest = json.loads((artifact_dir / "NH01_Runtime_Manifest_V1.json").read_text())
            self.assertEqual(manifest["source_sha"], SOURCE_SHA)
            self.assertEqual(manifest["classification"], "BLOCKED_INFRA")
            self.assertIn(f"evidence/{SOURCE_SHA}/NH01_Android_Runtime_1080x1920.png", manifest["evidence_files"].values())
            self.assertIn(f"evidence/{SOURCE_SHA}/private/NH01_Instrumentation.log", manifest["private_evidence_files"].values())
            self.assertFalse((web_root / "design/manager/nh01_acceptance/evidence" / SOURCE_SHA / "NH01_Instrumentation.log").exists())

            checksums = {}
            for line in (artifact_dir / "SHA256SUMS").read_text().splitlines():
                digest, relpath = line.split("  ", 1)
                checksums[relpath] = digest
            self.assertTrue(expected.issubset(checksums.keys()))
            for relpath in expected:
                self.assertEqual(
                    hashlib.sha256((artifact_dir / relpath).read_bytes()).hexdigest(),
                    checksums[relpath],
                    relpath,
                )

    def test_browser_diff_recording_keeps_runtime_and_parity_unpassed(self) -> None:
        with tempfile.TemporaryDirectory(prefix="nh01-browser-record-") as temp:
            root = Path(temp)
            site = root / "site"
            data_dir = site / "design/manager/nh01_acceptance/data"
            data_dir.mkdir(parents=True)
            for source in (ROOT / "design/manager/nh01_acceptance/data").glob("*.json"):
                shutil.copy2(source, data_dir / source.name)
            screenshot = root / "structured.png"
            Image.new("RGB", (1080, 1920), (30, 45, 60)).save(screenshot)
            diff_dir = root / "diff"
            diff_dir.mkdir()
            for filename in ("NH01_Diff_Heatmap.png", "NH01_Diff_Overlay.png"):
                Image.new("RGB", (1080, 1920), (60, 75, 90)).save(diff_dir / filename)
            diff_report = diff_dir / "NH01_ROI_Report.json"
            diff_report.write_text(json.dumps({
                "classification": "DIAGNOSTIC_ONLY_REVIEW_REQUIRED",
                "source_sha": SOURCE_SHA,
                "structured_preview": {"path": str(screenshot), "sha256": "diagnostic-only"},
                "heatmap": "NH01_Diff_Heatmap.png",
                "overlay": "NH01_Diff_Overlay.png",
                "global_changed_pixel_fraction_over_8": 0.25,
                "global_rgb_mae": [10.0, 11.0, 12.0],
                "rois": [{"component": "Header", "status": "REVIEW_REQUIRED", "verdict": "NOT_A_PARITY_THRESHOLD"}],
                "threshold_authority": "existing visual contract",
                "threshold_policy": "diagnostic only",
            }), encoding="utf-8")
            browser_report = root / "browser.json"
            browser_report.write_text(json.dumps({"status": "PASS", "gate": "G2_CLOUD_PREVIEW_VALID", "source_sha": SOURCE_SHA}), encoding="utf-8")

            subprocess.run(
                [
                    sys.executable,
                    str(ROOT / "scripts/design/record_nh01_browser_evidence.py"),
                    "--report", str(browser_report),
                    "--site-root", str(site),
                    "--expected-source-sha", SOURCE_SHA,
                    "--workflow-run-id", "987654321",
                    "--workflow-run-url", "https://github.com/example/repo/actions/runs/987654321",
                    "--preview-diff-report", str(diff_report),
                    "--preview-screenshot", str(screenshot),
                    "--preview-diff-dir", str(diff_dir),
                ],
                cwd=ROOT,
                check=True,
                capture_output=True,
                text=True,
            )

            evidence = json.loads((data_dir / "NH01_Evidence_Provenance_V1.json").read_text())
            diff_doc = json.loads((data_dir / "NH01_Browser_Preview_Diff_V1.json").read_text())
            updated_browser_report = json.loads((data_dir / "NH01_Browser_Viewer_Report_V1.json").read_text())
            self.assertEqual(evidence["gate_status"]["G2_CLOUD_PREVIEW_VALID"], "PASS")
            self.assertEqual(evidence["gate_status"]["G4_RUNTIME_VALID"], "NOT_RUN")
            self.assertEqual(evidence["gate_status"]["G5_VISUAL_PARITY_VALID"], "NOT_RUN")
            self.assertEqual(diff_doc["status"], "REVIEW_REQUIRED")
            self.assertEqual(diff_doc["classification"], "DIAGNOSTIC_ONLY_REVIEW_REQUIRED")
            self.assertTrue((site / "design/manager/nh01_acceptance" / diff_doc["artifacts"]["heatmap"]).is_file())
            self.assertEqual(updated_browser_report["structured_preview_diff"], "DIAGNOSTIC_ONLY_REVIEW_REQUIRED")


if __name__ == "__main__":
    unittest.main()
