#!/usr/bin/env python3
"""Mutate synthetic copies of the manifest; never modify production/real accounts."""
import copy
import json
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from validate_recognition_qa_test_users import DEFAULT, validate


class RecognitionQaTestUsers(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.original = json.loads(DEFAULT.read_text(encoding="utf-8"))

    def test_committed_manifest_matches_runtime_contract(self):
        self.assertEqual([], validate(self.original), "Current manifest must pass")

    def test_stable_id_cannot_change(self):
        data = copy.deepcopy(self.original)
        data["identity"]["user_id"] = "anyone"
        self.assertTrue(any("identity id drift" in e for e in validate(data, Path("synthetic"))))

    def test_no_real_backend_account_or_credentials(self):
        data = copy.deepcopy(self.original)
        data["identity"]["is_real_backend_account"] = True
        data["identity"]["password"] = "fake-test-value"
        errors = validate(data, Path("synthetic"))
        self.assertTrue(any("is_real_backend_account" in e for e in errors))
        self.assertTrue(any("secret-bearing key" in e for e in errors))

    def test_guide_done_variants_are_stable(self):
        data = copy.deepcopy(self.original)
        data["states"][0]["guide_completed"] = True
        self.assertTrue(any("FIRST_USE: guide persistence mismatch" in e
                            for e in validate(data, Path("synthetic"))))

    def test_permission_cannot_be_seeded_from_manifest(self):
        data = copy.deepcopy(self.original)
        data["states"][0]["camera_permission"] = "GRANTED_BY_APP"
        self.assertTrue(any("host permission mismatch" in e
                            for e in validate(data, Path("synthetic"))))

    def test_reset_cannot_expand_to_backend_or_auth(self):
        data = copy.deepcopy(self.original)
        data["storage_contract"]["allowed_mutations"].append("backend_accounts")
        self.assertTrue(any("reset scope must be allowlisted exactly" in e
                            for e in validate(data, Path("synthetic"))))

    def test_duplicate_state_ids_fail(self):
        data = copy.deepcopy(self.original)
        data["states"][1]["id"] = data["states"][0]["id"]
        self.assertTrue(any("state roster" in e for e in validate(data, Path("synthetic"))))

    def test_nonzero_fish_records_block_empty_home_contract(self):
        data = copy.deepcopy(self.original)
        data["invariant"]["initial_fish_records"] = 1
        self.assertTrue(any("zero catches" in e for e in validate(data, Path("synthetic"))))


if __name__ == "__main__":
    unittest.main()
