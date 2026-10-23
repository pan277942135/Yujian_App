#!/usr/bin/env python3
from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from resolve_nh01_preview_release_gate import GateError, resolve_release_mode


SHA = "a" * 40
HTML = """<!doctype html>
<html><body>
<span class="status-badge">PREVIEW ONLY · NOT VISUAL PASS</span>
<article id="leftCatchCard" hidden></article>
<article id="rightCatchCard" hidden></article>
<div id="heroPlaceholder" data-status="BLOCKED_ASSET_SOURCE">照片素材待授权</div>
</body></html>"""


def fixture(contract_status="PROPOSED_FOR_CANONICAL_MAIN_REVIEW", p3_status="BLOCKED"):
    hero_public = p3_status == "PASS"
    neighbors_public = p3_status == "PASS"
    contract = {
        "contract_id": "NH01_RESPONSIVE_LAYOUT_V2",
        "contract_version": "2.0",
        "status": contract_status,
    }
    blockers = []
    if p3_status == "BLOCKED":
        blockers = [
            {"id": "hero_photo", "status": "BLOCKED_PUBLIC_REDISTRIBUTION"},
            {"id": "neighbor_photos", "status": "BLOCKED_PUBLIC_REDISTRIBUTION"},
        ]
    structure = {
        "heroPlaceholder": p3_status == "BLOCKED",
        "heroPhotoRendered": p3_status == "PASS",
        "hiddenNeighbors": p3_status == "BLOCKED",
        "visibleNeighborCount": 0 if p3_status == "BLOCKED" else 2,
        "neighborPhotoCount": 0 if p3_status == "BLOCKED" else 2,
    }
    report = {
        "status": "PASS",
        "nh01_preview_v2_status": p3_status,
        "nh01_preview_v2_blockers": blockers,
        "checks": {
            "contract": {
                "id": contract["contract_id"],
                "version": contract["contract_version"],
                "status": contract_status,
                "source_sha": SHA,
                "deployment_sha": SHA,
                "visible_structure": structure,
                "registered_assets": [
                    {"asset": "background", "src": "/design/background.png"},
                    {"asset": "avatar", "src": "/assets/avatar.png"},
                ],
            },
            "nh01_preview_v2": {"media_blockers": blockers},
        },
    }
    manifest = {
        "assets": [
            {"id": "background", "public_preview": True, "source_path": "design/background.png"},
            {"id": "avatar", "public_preview": True, "source_path": "assets/avatar.png"},
            {"id": "hero_photo", "public_preview": hero_public, "source_path": "app/src/main/assets/home_normal/fish_record/sample_recent_catch.jpg"},
            {"id": "neighbor_photos", "public_preview": neighbors_public, "source_path": None},
        ]
    }
    html = HTML if p3_status == "BLOCKED" else """<!doctype html>
<html><body>
<span class="status-badge">PREVIEW ONLY · NOT VISUAL PASS</span>
<article id="leftCatchCard"><img data-asset-id="neighbor_photos" src="/approved/left.jpg"></article>
<article id="rightCatchCard"><img data-asset-id="neighbor_photos" src="/approved/right.jpg"></article>
<article id="mainCatchCard"><img data-asset-id="hero_photo" src="/approved/hero.jpg"></article>
</body></html>"""
    return report, contract, manifest, html


class PreviewReleaseGateTests(unittest.TestCase):
    def test_proposed_contract_with_explicit_media_blockers_can_publish_preview_only(self):
        report, contract, manifest, html = fixture()
        result = resolve_release_mode(report, contract, manifest, html, SHA)
        self.assertTrue(result["publish"])
        self.assertEqual(result["preview_mode"], "PREVIEW_ONLY")
        self.assertEqual(result["release_gate_effect"], "NONE")
        self.assertEqual(result["acceptance_status"], "NOT_CLAIMED")

    def test_frozen_contract_requires_actual_nh01_preview_v2_pass(self):
        report, contract, manifest, html = fixture(contract_status="SPEC_FROZEN", p3_status="BLOCKED")
        with self.assertRaises(GateError):
            resolve_release_mode(report, contract, manifest, html, SHA)

    def test_unknown_media_blocker_fails_closed(self):
        report, contract, manifest, html = fixture()
        report["checks"]["nh01_preview_v2"]["media_blockers"].append({"id": "secret_asset", "status": "BLOCKED_PUBLIC_REDISTRIBUTION"})
        report["nh01_preview_v2_blockers"] = report["checks"]["nh01_preview_v2"]["media_blockers"]
        with self.assertRaises(GateError):
            resolve_release_mode(report, contract, manifest, html, SHA)

    def test_source_sha_mismatch_fails_closed(self):
        report, contract, manifest, html = fixture()
        with self.assertRaises(GateError):
            resolve_release_mode(report, contract, manifest, html, "b" * 40)

    def test_local_photo_success_does_not_grant_public_redistribution(self):
        report, contract, manifest, html = fixture()
        report["checks"]["private_media"] = {"status": "PASS", "public_redistribution": "NOT_CONFIRMED"}
        result = resolve_release_mode(report, contract, manifest, html, SHA)
        self.assertEqual(result["preview_mode"], "PREVIEW_ONLY")
        self.assertEqual(result["nh01_preview_v2_status"], "BLOCKED")
        self.assertIn("public photo placeholders", result["reason"])

    def test_candidate_must_keep_unapproved_media_hidden(self):
        report, contract, manifest, html = fixture()
        report["checks"]["contract"]["visible_structure"]["hiddenNeighbors"] = False
        with self.assertRaises(GateError):
            resolve_release_mode(report, contract, manifest, html, SHA)

    def test_frozen_contract_with_approved_rendered_media_can_use_frozen_acceptance_mode(self):
        report, contract, manifest, html = fixture(contract_status="SPEC_FROZEN", p3_status="PASS")
        result = resolve_release_mode(report, contract, manifest, html, SHA)
        self.assertTrue(result["publish"])
        self.assertEqual(result["preview_mode"], "FROZEN_ACCEPTANCE")
        self.assertEqual(result["release_gate_effect"], "G-NH01-RESPONSIVE-PREVIEW-V2_PASS_REQUIRED")

    def test_nh01_preview_v2_pass_rejects_placeholder_or_missing_photo_evidence(self):
        report, contract, manifest, html = fixture(contract_status="SPEC_FROZEN", p3_status="PASS")
        report["checks"]["contract"]["visible_structure"]["heroPhotoRendered"] = False
        with self.assertRaises(GateError):
            resolve_release_mode(report, contract, manifest, html, SHA)


if __name__ == "__main__":
    unittest.main()
