"""Focused regressions for Work execution V2; no secrets, network or emulator."""
import json
from pathlib import Path
import tempfile
import unittest

from work_control import failure_id, record_event, route_files, preflight, extract_pr_paths


ROOT = Path(__file__).resolve().parents[2]


def event(sha, hypothesis="photo rendered too late", result="FAIL"):
    return {
        "task_id": "NH01-M08",
        "gate_id": "API28",
        "symptom_key": "WAIT_FOR_FISH_PHOTO_RENDER",
        "source_sha": sha,
        "hypothesis": hypothesis,
        "evidence": "https://github.com/pan277942135/Yujian_App/actions/runs/1",
        "result": result,
    }


class FailureLedgerTest(unittest.TestCase):
    def test_cross_sha_retry_budget(self):
        with tempfile.TemporaryDirectory() as td:
            ledger = Path(td) / "failed.json"
            a = record_event(ledger, event("sha-1"))
            b = record_event(ledger, event("sha-2"))
            c = record_event(ledger, event("sha-3", "semantics identifier mismatch"))
            self.assertEqual(a["failure_id"], b["failure_id"])
            self.assertEqual(a["failure_id"], c["failure_id"])
            self.assertEqual([a["decision"], b["decision"], c["decision"]], [
                "LOCALIZE_AND_REPRODUCE", "RETHINK_ROOT_CAUSE", "STOP_AND_ESCALATE"
            ])
            self.assertEqual(len(json.loads(ledger.read_text())["issues"][a["failure_id"]]["events"]), 3)

    def test_no_empty_evidence_or_invented_pass(self):
        with tempfile.TemporaryDirectory() as td:
            ledger = Path(td) / "failed.json"
            bad = event("sha-1")
            bad["evidence"] = ""
            with self.assertRaises(ValueError):
                record_event(ledger, bad)
            good = record_event(ledger, event("sha-1"))
            fixed = record_event(ledger, event("sha-2", result="FIX_VERIFIED"))
            self.assertEqual(good["failure_id"], fixed["failure_id"])
            self.assertEqual(fixed["decision"], "FIX_EVIDENCE_RECORDED_NOT_GATE_PASS")
            with self.assertRaises(ValueError):
                record_event(ledger, event("sha-3"))

    def test_fingerprint_ignores_sha_and_whitespace(self):
        x = event("a")
        y = event("b")
        y["symptom_key"] = " WAIT_FOR_FISH_PHOTO_RENDER   "
        self.assertEqual(failure_id(x), failure_id(y))


class RoutingTest(unittest.TestCase):
    def test_optical_only_does_not_trigger_android(self):
        paths = [
            "design/manager/optical-lab/index.html",
            "design/manager/nh01_acceptance/edgeglow-fish-static/index.html",
            "scripts/capture_optical_lab_static_closure_v3.cjs",
        ]
        r = route_files(ROOT, paths)
        self.assertFalse(r["android_ci"], r)
        self.assertFalse(r["nh01_runtime"], r)
        self.assertTrue(r["optical_browser"], r)

    def test_home_android_change_preserves_required_gates(self):
        r = route_files(ROOT, ["app/src/main/java/com/yujian/ai/ui/home/GeminiHome.kt"])
        self.assertTrue(r["android_ci"], r)
        self.assertTrue(r["nh01_runtime"], r)

    def test_frozen_foundation_still_triggers_android(self):
        r = route_files(ROOT, ["design/system_v3/06_Asset_System/derived/brand.png"])
        self.assertTrue(r["android_ci"], r)
        self.assertTrue(r["nh01_runtime"], r)

    def test_nh01_viewer_and_no_unbounded_design_trigger(self):
        a = route_files(ROOT, ["design/manager/nh01_acceptance/viewer.js"])
        self.assertTrue(a["nh01_runtime"])
        b = route_files(ROOT, ["design/pages/recognition/processing/foo.md"])
        self.assertFalse(b["nh01_runtime"])
        self.assertFalse(b["android_ci"])

    def test_explicit_path_allowlist_is_mandatory(self):
        for workflow in ("android.yml", "nh01-cloud-runtime.yml"):
            paths = extract_pr_paths(ROOT / ".github" / "workflows" / workflow)
            self.assertTrue(paths)
            self.assertNotIn("design/**", paths)


class PreflightTest(unittest.TestCase):
    def test_preflight_never_claims_runtime_pass(self):
        for profile in ("android", "optical", "detector"):
            x = preflight(profile)
            self.assertIn("decision", x)
            self.assertNotEqual(x["decision"], "PASS")
            self.assertIn("checks", x)


if __name__ == "__main__":
    unittest.main()
