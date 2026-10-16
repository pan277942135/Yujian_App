#!/usr/bin/env python3
"""Guard the two-page guide authority across its static owners and assets."""
from __future__ import annotations

import hashlib
import json
import unittest
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
GUIDE = ROOT / "design/pages/recognition/first_use_camera_guide"
MASTER = ROOT / "design/system_v3/00_Product_Master_Flow/assets"
P01_SHA = "dc731cca5470ef2154bb06172b9836e823ea780b0833f04e34caaf883e465c50"
P02_SHA = "1a3008483ac5708a0649501cf2aef6c799d47b95bcc2c1b1272fa64e68f7f9b3"
OLD_VERTICAL_SHA = "fb092235fd043a42aa8250aba9dfdb91a78e96549dd5a63f8e89cf7433685f8f"
MB05_SHA = "6d67d03045f5f3dfb66adad10921d7456a2c82620b7b68f156cb98be74f1908c"


def read_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def read_yaml(path: Path):
    return yaml.safe_load(path.read_text(encoding="utf-8"))


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


class FirstUseGuideAuthorityTests(unittest.TestCase):
    def test_active_design_authorities_all_resolve_to_p01_p02(self) -> None:
        matrix = read_yaml(ROOT / "harness/FEATURE_MATRIX.yaml")
        machine = read_yaml(ROOT / "harness/PHASE_TASK_STATE_MACHINE.yaml")
        contracts = read_yaml(ROOT / "harness/UNIFIED_CONTRACT_REGISTRY.yaml")
        feature = matrix["features"]["recognition_flow_v1_2"]
        self.assertIn("p01", feature["design_children"])
        self.assertIn("p02", feature["design_children"])
        self.assertNotIn("p03", feature["design_children"])
        self.assertNotIn("P03", str(machine["tasks"]["N010"]))
        self.assertEqual(contracts["feature_contracts"]["recognition_flow_v1_2"]["contract_extensions"]["first_use_guide"]["active_pages_by_design_authority"], ["p01", "p02"])
        guide_contract = contracts["feature_contracts"]["recognition_flow_v1_2"]["contract_extensions"]["first_use_guide"]
        self.assertEqual(guide_contract["active_pages_by_state_machine"], ["p01", "p02"])
        self.assertEqual(guide_contract["page_orientation"]["p02"], "LANDSCAPE + HORIZONTAL_FISH")
        self.assertEqual(guide_contract["asset_sha256"], {"p01": P01_SHA, "p02": P02_SHA})
        gate = contracts["gate_contracts"]["G-FIRST-USE-GUIDE-ASSET"]
        self.assertEqual(set(gate["required_assets"]), {"p01", "p02"})
        self.assertEqual(gate["page_set_resolution"], "USER_AUTHORIZED_TWO_PAGE_V1_2")
        self.assertNotIn("blocker_id", gate)
        self.assertEqual(machine["tasks"]["N010"]["gate_id"], "G-FIRST-USE-GUIDE-ASSET")
        self.assertEqual(set(machine["tasks"]["N010"]["evidence_required"]), {"P01_png_sha256_bytes_dimensions", "P02_png_sha256_bytes_dimensions", "Design_Governance_PASS"})
        self.assertNotIn("B008", machine["blocker_semantics"]["n010_target_and_entry"])
        conflict = contracts["contract_conflicts"]["C-FIRST-USE-GUIDE-PAGE-COUNT"]
        self.assertEqual(conflict["status"], "RESOLVED")
        self.assertIn("two active pages", conflict["resolution"])

    def test_guide_spec_manifest_navigation_and_registry_match(self) -> None:
        manifest = read_json(GUIDE / "manifest.json")
        navigation = read_json(GUIDE / "navigation.json")
        registry = read_json(ROOT / "design/registry/experience_registry_v1.json")
        status = read_json(ROOT / "design/pages/recognition/status.json")
        self.assertEqual(manifest["active_page_ids"], ["p01", "p02"])
        self.assertEqual(navigation["active_sequence"], ["p01", "p02"])
        self.assertEqual([x["id"] for x in manifest["assets"]], ["p01", "p02"])
        self.assertEqual(manifest["active_page_ids"], ["p01", "p02"])
        self.assertEqual(manifest["assets"][1]["sha256"], P02_SHA)
        self.assertIn("P02_Landscape_Horizontal_Fish_Frozen.png", manifest["assets"][1]["path"])
        feature = next(x for x in registry["features"] if x["id"] == "recognition_flow_v1_2")
        self.assertEqual(feature["first_use_camera_guide"]["active_page_ids"], ["p01", "p02"])
        stack = [registry]
        guide_entries = []
        while stack:
            value = stack.pop()
            if isinstance(value, dict):
                if value.get("id") == "capture_first_use_guide":
                    guide_entries.append(value)
                stack.extend(value.values())
            elif isinstance(value, list):
                stack.extend(value)
        self.assertEqual(len(guide_entries), 1)
        self.assertEqual([x["id"] for x in guide_entries[0]["children"]], ["p01", "p02"])
        self.assertEqual(guide_entries[0]["page_count"], 2)
        self.assertEqual(status["first_use_camera_guide"]["active_pages"], ["p01", "p02"])

    def test_exact_original_bytes_and_superseded_vertical_archive(self) -> None:
        self.assertEqual(sha256(GUIDE / "frozen/P01_First_Use_Camera_Guide_Frozen.png"), P01_SHA)
        self.assertEqual(sha256(GUIDE / "frozen/P02_Landscape_Horizontal_Fish_Frozen.png"), P02_SHA)
        self.assertEqual(sha256(GUIDE / "archive/P02_Portrait_Vertical_Fish_Superseded_20261016.png"), OLD_VERTICAL_SHA)
        self.assertFalse((GUIDE / "frozen/P02_First_Use_Camera_Guide_Frozen.png").exists())
        self.assertFalse((GUIDE / "frozen/P03_First_Use_Camera_Guide_Frozen.png").exists())

    def test_master_board_has_15_primary_8_recovery_and_keeps_mb05_missing(self) -> None:
        board = read_json(MASTER / "Recognition_Main_Journey_Master_Board_V1.json")
        assets = read_json(MASTER / "Recognition_Main_Journey_Asset_Manifest_V1.json")
        capture = read_json(ROOT / "design/pages/recognition/capture/HiFi_Freeze_Manifest_V1.json")
        self.assertEqual([x["board_id"] for x in board["primary_sequence"]], [f"MB-{i:02d}" for i in range(1, 16)])
        self.assertEqual(len(board["recovery_sequence"]), 8)
        guide_pages = [x.get("guide_page_id") for x in board["primary_sequence"] if x.get("guide_page_id")]
        self.assertEqual(guide_pages, ["p01", "p02"])
        self.assertEqual(board["status"], "PARTIAL")
        self.assertEqual(board["source_missing"]["board_id"], "MB-05")
        self.assertEqual(board["source_missing"]["expected_sha256"], MB05_SHA)
        self.assertFalse((ROOT / "design/pages/recognition/capture/frozen/Recognition_Capture_Ready_Portrait_Horizontal_Fish_Frozen.png").exists())
        mb05 = next(x for x in assets["primary_assets"] if x["tile_id"] == "MB-05")
        self.assertEqual(mb05["source_status"], "SOURCE_MISSING")
        self.assertEqual(mb05["expected_sha256"], MB05_SHA)
        capture_mb05 = next(x for x in capture["states"] if x["board_id"] == "MB-05")
        self.assertEqual(capture_mb05["source_status"], "SOURCE_MISSING")
        self.assertEqual(capture_mb05["source_sha256"], MB05_SHA)


if __name__ == "__main__":
    unittest.main()
