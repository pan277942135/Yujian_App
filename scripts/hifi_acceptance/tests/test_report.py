"""Evidence integrity and no-false-green regression tests for Android Hi-Fi audit."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import report as hifi


class HiFiEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.old_root = hifi.ROOT
        hifi.ROOT = self.root
        self.addCleanup(setattr, hifi, "ROOT", self.old_root)
        self.sha = "a" * 40
        self.ref = self.root / "frozen.png"
        image = Image.new("RGB", (600, 1000), "#d7e9e2")
        dr = ImageDraw.Draw(image)
        dr.rectangle((65, 70, 530, 240), fill="#224e4a")
        dr.rectangle((125, 500, 495, 910), fill="#6c997e")
        image.save(self.ref)
        self.artifacts = self.root / "artifacts"
        self.gate = self.artifacts / "runtime-gate-home"
        self.gate.mkdir(parents=True)
        self.screen = self.gate / "runtime.png"
        self.output = self.root / "out"
        self.manifest = {
            "package": "com.yujian.ai",
            "required_modules": ["Home"],
            "surfaces": [{
                "id": "home", "module": "Home", "gate": "home",
                "reference": "frozen.png", "screenshot": "runtime.png",
                "mode": "auto",
                "rois": [{"name": "full", "rect": [0, 0, 1, 1], "max_mae": 4}],
            }],
        }

    def gate_result(self, classification="PASS", build_sha=None):
        (self.gate / "runtime_gate_result.json").write_text(json.dumps({
            "gate": "home", "classification": classification,
            "build_sha": build_sha or self.sha, "failure_reason": "ASSERTION_FAILED",
        }), encoding="utf-8")

    def screenshot(self, proof=True, package="com.yujian.ai"):
        self.screen.write_bytes(self.ref.read_bytes())
        if proof:
            self.proof(package=package)

    def proof(self, package="com.yujian.ai"):
        data = {
            "package": package, "build_sha": self.sha,
            "foreground_verified": True,
            "resumed_activity": "com.yujian.ai/com.yujian.ai.MainActivity",
            "capture_method": "adb-exec-out-screencap",
            "screenshot_sha256": hashlib.sha256(self.screen.read_bytes()).hexdigest(),
        }
        (self.gate / "runtime.png.provenance.json").write_text(
            json.dumps(data), encoding="utf-8")

    def status(self):
        return hifi.evaluate(self.manifest, self.artifacts, self.output, self.sha)["surfaces"][0]

    def test_absent_gate_is_blocked(self):
        self.assertEqual(self.status()["reason"], "GATE_NOT_EXECUTED_OR_OTHER_SHA")

    def test_wrong_commit_gate_is_blocked(self):
        self.gate_result(build_sha="b" * 40)
        self.assertEqual(self.status()["status"], "BLOCKED")

    def test_missing_screenshot_cannot_pass(self):
        self.gate_result()
        self.assertEqual(self.status()["reason"], "SCREENSHOT_MISSING")

    def test_unverified_desktop_capture_cannot_pass(self):
        self.gate_result()
        self.screenshot(proof=False)
        e = self.status()
        self.assertEqual(e["status"], "BLOCKED")
        self.assertEqual(e["reason"], "MISSING_CAPTURE_PROVENANCE")
        self.assertTrue((self.output / "home" / "side-by-side.png").is_file())

    def test_wrong_package_proof_cannot_pass(self):
        self.gate_result()
        self.screenshot(package="com.android.launcher")
        self.assertEqual(self.status()["reason"], "INVALID_CAPTURE_PROVENANCE")

    def test_sha_mismatch_proof_cannot_pass(self):
        self.gate_result()
        self.screenshot()
        self.screen.write_bytes(self.ref.read_bytes() + b"x")
        # Broken PNG is separately rejected as invalid evidence.
        self.assertNotEqual(self.status()["status"], "AUTO_CHECK_PASS")

    def test_successful_provenance_and_roi(self):
        self.gate_result()
        self.screenshot()
        self.assertEqual(self.status()["status"], "AUTO_CHECK_PASS")

    def test_photo_surface_requires_user_review_not_fake_pass(self):
        self.gate_result()
        self.screenshot()
        self.manifest["surfaces"][0]["mode"] = "review"
        result = hifi.evaluate(self.manifest, self.artifacts, self.output, self.sha)
        self.assertEqual(result["surfaces"][0]["status"], "REVIEW_REQUIRED")
        self.assertEqual(result["summary"]["status"], "READY_FOR_USER_REVIEW")
        self.assertEqual(result["summary"]["user_physical_acceptance"], "NOT_PERFORMED")

    def test_visual_drift_is_a_failure(self):
        self.gate_result()
        image = Image.open(self.ref).convert("RGB")
        ImageDraw.Draw(image).rectangle((65, 70, 530, 240), fill="red")
        image.save(self.screen)
        self.proof()
        self.assertEqual(self.status()["status"], "FAIL_VISUAL")

    def test_test_failure_cannot_be_masked_by_good_pixels(self):
        self.gate_result("FAIL_TEST")
        self.screenshot()
        self.assertEqual(self.status()["status"], "FAIL_TEST")

    def test_without_design_authority_fails_closed(self):
        self.gate_result()
        self.screenshot()
        self.manifest["surfaces"][0]["reference"] = None
        self.assertEqual(self.status()["status"], "FAIL_AUTHORITY")


if __name__ == "__main__":
    unittest.main()
