#!/usr/bin/env python3
"""Catalog regressions are synthetic and cannot certify actual app execution."""
import copy
import json
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from validate_recognition_test_case_catalog import validate, CATALOG, E2E, INPUTS, USERS

class CatalogRegressionTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.original = tuple(json.loads(path.read_text(encoding="utf-8"))
                             for path in (CATALOG,E2E,INPUTS,USERS))

    def check(self, mutation=None):
        parts = tuple(copy.deepcopy(p) for p in self.original)
        if mutation:
            mutation(*parts)
        return validate(*parts)

    def test_complete_66_case_ten_e2e_four_state_catalog(self):
        self.assertEqual([], self.check())

    def test_missing_case_is_rejected(self):
        self.assertTrue(any("exactly 66" in x for x in self.check(
            lambda c,e,i,u: c["cases"].pop())))

    def test_duplicate_case_id_is_rejected(self):
        def corrupt(c,e,i,u):
            c["cases"][1]["case_id"]=c["cases"][0]["case_id"]
        self.assertTrue(any("duplicate base case" in x for x in self.check(corrupt)))

    def test_primary_user_state_required(self):
        self.assertTrue(any("primary user state" in x for x in self.check(
            lambda c,e,i,u: c["cases"][0].update(initial_state="NONEXISTENT"))))

    def test_broken_reverse_user_index_is_rejected(self):
        self.assertTrue(any("reverse case mapping" in x for x in self.check(
            lambda c,e,i,u: u["states"][0]["test_case_ids"].pop())))

    def test_no_fake_execution_pass_can_live_in_source(self):
        self.assertTrue(any("source catalog must not fake execution PASS" in x for x in self.check(
            lambda c,e,i,u: c["cases"][0].update(execution_status="PASS"))))

    def test_fake_real_model_e2e_from_controlled_response_is_rejected(self):
        self.assertTrue(any("response mock" in x for x in self.check(
            lambda c,e,i,u: e["scenarios"][0]["input_ids"].append("RESP-HIGH"))))

    def test_real_device_e2e_must_have_capture_trace(self):
        def corrupt(c,e,i,u):
            e["scenarios"][0]["evidence_required"].remove("camera_capture_trace")
        self.assertTrue(any("camera proof" in x for x in self.check(corrupt)))

    def test_quality_not_technical_failure(self):
        def corrupt(c,e,i,u):
            e["scenarios"][6]["covers_case_ids"].append("PROC-09")
        self.assertTrue(any("INVALID" in x for x in self.check(corrupt)))

    def test_missing_photo_registry_input_is_rejected(self):
        self.assertTrue(any("unknown input" in x for x in self.check(
            lambda c,e,i,u: c["cases"][0]["input_ids"].append("IMG-99"))))

    def test_approved_photo_requires_evidence(self):
        self.assertTrue(any("provenance/rights" in x for x in self.check(
            lambda c,e,i,u: i["inputs"][0].update(status="APPROVED"))))

    def test_missing_source_authority_is_detected(self):
        self.assertTrue(any("authority missing" in x for x in self.check(
            lambda c,e,i,u: c["cases"][0]["authority_refs"].append("nonexistent/contract.json"))))

    def test_changed_dataset_version_requires_sync(self):
        self.assertTrue(any("versions not synchronized" in x for x in self.check(
            lambda c,e,i,u: u.update(dataset_version="7.7.7"))))

    def test_review_cases_require_reason(self):
        self.assertTrue(any("review case needs reason" in x for x in self.check(
            lambda c,e,i,u: c["cases"][8].update(design_status="REVIEW",review_reason=""))))

if __name__=="__main__":
    unittest.main()
