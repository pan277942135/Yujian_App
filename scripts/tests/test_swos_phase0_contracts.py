#!/usr/bin/env python3
"""Regression tests for cross-contract SWOS Phase 0 timing and reveal invariants."""
from __future__ import annotations

import copy
import importlib.util
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("validate_swos_phase0_contracts", ROOT / "scripts/validate_swos_phase0_contracts.py")
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
SPEC.loader.exec_module(MODULE)


class SwosPhase0ContractTests(unittest.TestCase):
    def load(self):
        return {key: MODULE.load_json(ROOT / relative) for key, relative in MODULE.FILES.items()}

    def test_checked_in_contract_set_is_consistent(self):
        self.assertEqual([], MODULE.validate(ROOT))

    def test_extra_resolve_wait_is_rejected(self):
        contracts = self.load()
        contracts["continuity"]["navigation"]["additional_pre_navigation_wait_ms"] = 200
        self.assertTrue(any("pre-navigation wait" in error for error in MODULE.consistency_errors(contracts)))

    def test_rr09_t0_must_be_first_result_frame(self):
        contracts = self.load()
        contracts["continuity"]["rr09"]["t0"] = "PROCESSING_RESOLVE_START"
        self.assertTrue(any("T0" in error for error in MODULE.consistency_errors(contracts)))

    def test_chinese_only_title_is_enforced(self):
        contracts = self.load()
        contracts["rr09"]["title"]["show_english_scientific_name"] = True
        self.assertTrue(any("Chinese-only" in error for error in MODULE.consistency_errors(contracts)))

    def test_processing_release_cannot_become_a_business_state(self):
        contracts = self.load()
        contracts["processing_state"]["states"].append({"id": "RESOLVE"})
        self.assertTrue(any("RESOLVE business state" in error for error in MODULE.consistency_errors(contracts)))

    def load_authority_indexes(self):
        registry = json.loads((ROOT / "design/registry/experience_registry_v1.json").read_text(encoding="utf-8"))
        navigation = json.loads((ROOT / "design/pages/recognition/result/navigation.json").read_text(encoding="utf-8"))
        active_registry = (ROOT / "docs/ACTIVE_SOURCE_REGISTRY.md").read_text(encoding="utf-8")
        return registry, navigation, active_registry

    def test_design_manager_routes_use_swos_and_keep_v2_historical(self):
        registry, navigation, active = self.load_authority_indexes()
        self.assertEqual([], MODULE.authority_reference_errors(registry, navigation, active))

    def test_legacy_edge_field_cannot_remain_processing_motion_authority(self):
        registry, navigation, active = self.load_authority_indexes()
        processing = next(f for f in registry["features"] if f["id"] == "recognition_flow_v1_2")
        motion = next(v for v in processing["hifi_views"] if v["id"] == "motion_transition")
        motion["secondary_authority"] = "design/pages/recognition/processing/motion/AI_Edge_Field_V1_Motion_Contract.md"
        self.assertTrue(any("legacy Edge Field" in error for error in MODULE.authority_reference_errors(registry, navigation, active)))

    def test_rr09_v3_route_cannot_reveal_english_scientific_name(self):
        registry, navigation, active = self.load_authority_indexes()
        result = next(f for f in registry["features"] if f["id"] == "recognition_result_v1")
        rr09 = next(v for v in result["hifi_views"] if v["id"] == "motion_continuation")
        rr09["menu_points"].append("English scientific name reveal")
        self.assertTrue(any("English scientific-name reveal" in error for error in MODULE.authority_reference_errors(registry, navigation, active)))


if __name__ == "__main__":
    unittest.main()
