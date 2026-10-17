#!/usr/bin/env python3
"""Synthetic fixtures ONLY test validator logic; they never satisfy product QA."""
import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from PIL import Image

import verify_runtime_evidence_source_closure as closure


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


class ConditionalVerdictTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.evidence = self.root / "evidence"
        self.evidence.mkdir()
        self.source_sha = "a" * 40
        self.apk_sha = "b" * 64

    def picture(self, path, rgb, dims=(1080, 1920)):
        path.parent.mkdir(parents=True, exist_ok=True)
        Image.new("RGB", dims, rgb).save(path)
        return sha(path)

    def review(self, name):
        path = self.evidence / (name + ".txt")
        path.write_text("Synthetic review artifact. Unit test only.\n")
        return {"decision": "APPROVED", "reviewer": "unit-test-only",
                "artifact": path.name, "artifact_sha256": sha(path)}

    def home_setup(self):
        fixture_items = [{"id": fid, "test_case": fid.lower(),
                          "source_sha256": "%064x" % index}
                         for index, fid in enumerate(closure.CASES, 1)]
        captures = []
        for index, item in enumerate(fixture_items):
            fid = item["id"]
            rid = "test-record-" + fid
            record = {"fixture_id": fid, "fish_record_id": rid,
                      "source_sha256_before": item["source_sha256"],
                      "source_sha256_after": item["source_sha256"],
                      "source_commit": self.source_sha, "apk_sha256": self.apk_sha,
                      "render_mode": "EVIDENCE_FIT", "fish_visibility": "VERIFIED",
                      "visual_review": self.review(fid)}
            for n, surface in enumerate(("HOME", "DETAIL")):
                name = fid + "_" + surface + ".png"
                record[surface] = {
                    "capture_origin": "ANDROID_API28_INSTRUMENTATION",
                    "api_level": 28, "source_commit": self.source_sha,
                    "apk_sha256": self.apk_sha, "fish_record_id": rid,
                    "path": name,
                    "sha256": self.picture(self.evidence / name,
                                           (10 + index * 15, n * 20, index * 11))}
            captures.append(record)
        (self.evidence / "hero_runtime_matrix.json").write_text(
            json.dumps({"cases": captures}))
        return {"fixtures": fixture_items}, {"status": "SOURCE_READY", "cases": []}

    def test_home_missing_sources_and_matrix_remain_blocked(self):
        got = closure.evaluate_home(
            {"fixtures": []}, {"status": "BLOCKED"}, self.evidence,
            self.source_sha, self.apk_sha)
        self.assertEqual("BLOCKED", got["status"])
        self.assertIn("F01_F08_APPROVED_ORIGINALS_PROVENANCE_INCOMPLETE", got["blocking_reason"])

    def test_home_full_source_backed_matrix_has_reachable_pass(self):
        fixtures, preflight = self.home_setup()
        got = closure.evaluate_home(fixtures, preflight, self.evidence, self.source_sha, self.apk_sha)
        self.assertEqual("PASS", got["status"], got["blocking_reason"])

    def test_home_tampered_home_screenshot_fails_closed(self):
        fixtures, preflight = self.home_setup()
        (self.evidence / "F01_HOME.png").write_bytes(b"altered")
        got = closure.evaluate_home(fixtures, preflight, self.evidence, self.source_sha, self.apk_sha)
        self.assertEqual("BLOCKED", got["status"])
        self.assertIn("F01:HOME_NATIVE_CAPTURE_INVALID", got["blocking_reason"])

    def guide_setup(self):
        screen_dir = self.root / "screenshots"
        screen_dir.mkdir()
        photos_dir = self.root / "photos"
        photos_dir.mkdir()
        guide_evidence = screen_dir / "guide"
        guide_evidence.mkdir()
        saved_root = closure.ROOT
        closure.ROOT = self.root
        self.addCleanup(setattr, closure, "ROOT", saved_root)
        records = []
        for i, page in enumerate(closure.PAGES):
            sources = []
            for j in range(4):
                photo = photos_dir / ("%s_%d.png" % (page, j))
                h = self.picture(photo, (i * 30 + j, j, i), (16, 16))
                sources.append({
                    "source_path": str(photo.relative_to(self.root)), "sha256": h,
                    "photo_provenance": "VERIFIED",
                    "source_usage_rights": "APPROVED",
                    "source_evidence": "unit-source-evidence", "usage_evidence": "unit-rights-evidence",
                })
            screen_name = page + "_runtime.png"
            screenshot_sha = self.picture(screen_dir / screen_name, (i * 30, i, 40))
            review = screen_dir / "guide" / (page + ".txt")
            review.write_text("Synthetic approved unit test only")
            records.append({
                "page": page, "runtime_photo_assets": {
                    "status": "SOURCE_READY", "hero": sources[0], "errors": sources[1:]},
                "visual_status": "APPROVED",
                "visual_review": {"decision": "APPROVED", "reviewer": "unit-test-only",
                                  "artifact": review.name, "artifact_sha256": sha(review)},
                "screenshot": screen_name, "screenshot_sha256": screenshot_sha,
            })
        report = {"runtime_route_evidence": "PASS",
                  "runtime_evidence_status": "PRODUCTION_CAMERAX_INSTRUMENTATION",
                  "runtime_profile_validation": {"status": "PASS"},
                  "apk_sha256_match": True}
        return report, records, guide_evidence

    def test_guide_component_only_never_promotes_pass(self):
        report, records, ev = self.guide_setup()
        report["runtime_route_evidence"] = "NOT_RUN"
        report["runtime_evidence_status"] = "COMPONENT_INSTRUMENTATION_ONLY"
        result = closure.evaluate_guide(report, records, ev)
        self.assertFalse(result["pass"])
        self.assertIn("PRODUCTION_CAMERAX_ROUTE_NOT_VERIFIED", result["blockers"])

    def test_guide_production_evidence_has_reachable_pass(self):
        report, records, ev = self.guide_setup()
        result = closure.evaluate_guide(report, records, ev)
        self.assertTrue(result["pass"], result["blockers"])

    def test_guide_unapproved_source_fails_closed(self):
        report, records, ev = self.guide_setup()
        records[0]["runtime_photo_assets"]["hero"]["source_usage_rights"] = "NOT_VERIFIED"
        result = closure.evaluate_guide(report, records, ev)
        self.assertFalse(result["pass"])
        self.assertIn("P01:SOURCE_0_INVALID_OR_NOT_APPROVED", result["blockers"])


if __name__ == "__main__":
    unittest.main()
