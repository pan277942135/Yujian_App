"""Regression tests for the generic responsive contract boundary."""

from __future__ import annotations

import copy
import json
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "scripts/design"))
import validate_page_responsive_contract as validator  # noqa: E402


MATRIX_PATH = Path(validator.MATRIX_DEFAULT)


class PageResponsiveContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        matrix = json.loads((ROOT / MATRIX_PATH).read_text(encoding="utf-8"))
        cls.cases = {}
        for case in matrix["cases"]:
            envelope = json.loads((ROOT / case["envelope"]).read_text(encoding="utf-8"))
            profile = json.loads((ROOT / case["profile"]).read_text(encoding="utf-8"))
            cls.cases[case["page_id"]] = (envelope, profile, case)

    def validate(self, envelope, profile):
        return validator.validate_contract(
            envelope,
            profile,
            ROOT,
            validate_bound_files=False,
            run_adapter=False,
        )

    def error_codes(self, result):
        self.assertEqual(result["status"], "FAIL", result["errors"])
        return {item["code"] for item in result["errors"]}

    def test_three_distinct_page_shapes_are_schema_valid(self):
        expected = {"home_normal_v1", "home_empty_v2", "auth_login_v2"}
        self.assertEqual(set(self.cases), expected)
        for page_id, (envelope, profile, _case) in self.cases.items():
            with self.subTest(page_id=page_id):
                result = self.validate(envelope, profile)
                self.assertEqual(result["status"], "PASS", result["errors"])
                self.assertEqual(result["formal_acceptance"], "NOT_VERIFIED")
        empty_profile = self.cases["home_empty_v2"][1]
        login_profile = self.cases["auth_login_v2"][1]
        empty_envelope = self.cases["home_empty_v2"][0]
        login_envelope = self.cases["auth_login_v2"][0]
        self.assertNotIn("fishcatch-pager", json.dumps(empty_profile).lower())
        self.assertIn("credential-form", json.dumps(login_profile))
        for draft in (empty_envelope, login_envelope):
            self.assertEqual(draft["acceptance"]["assets"], "NOT_VERIFIED")
            self.assertEqual(draft["acceptance"]["page_state"], "NOT_VERIFIED")

    def test_page_identity_mismatch_fails(self):
        envelope, profile, _ = self.cases["home_normal_v1"]
        profile = copy.deepcopy(profile)
        profile["page_id"] = "auth_login_v2"
        result = self.validate(envelope, profile)
        self.assertIn("PAGE_PROFILE_ID_MISMATCH", self.error_codes(result))

    def test_missing_profile_fails(self):
        envelope, _profile, _ = self.cases["home_empty_v2"]
        result = self.validate(envelope, None)
        self.assertIn("PROFILE_MISSING", self.error_codes(result))

    def test_illegal_status_fails(self):
        envelope, profile, _ = self.cases["home_empty_v2"]
        envelope = copy.deepcopy(envelope)
        envelope["status"] = "FROZEN_CANDIDATE"
        result = self.validate(envelope, profile)
        self.assertIn("INVALID_CONTRACT_STATUS", self.error_codes(result))

    def test_missing_required_envelope_field_fails_schema(self):
        envelope, profile, _ = self.cases["auth_login_v2"]
        envelope = copy.deepcopy(envelope)
        envelope.pop("contract_version")
        result = self.validate(envelope, profile)
        self.assertTrue(
            any("contract_version" in item["message"] for item in result["errors"]),
            result["errors"],
        )

    def test_wrong_schema_reference_fails(self):
        envelope, profile, _ = self.cases["home_empty_v2"]
        envelope = copy.deepcopy(envelope)
        envelope["schema_ref"] = "design/pages/home/normal_home/schema.json"
        result = self.validate(envelope, profile)
        self.assertIn("WRONG_ENVELOPE_SCHEMA_REF", self.error_codes(result))

    def test_wrong_profile_schema_reference_fails(self):
        envelope, profile, _ = self.cases["home_empty_v2"]
        profile = copy.deepcopy(profile)
        profile["schema_ref"] = "design/pages/home/empty_home/profile-schema.json"
        result = self.validate(envelope, profile)
        self.assertIn("WRONG_PROFILE_SCHEMA_REF", self.error_codes(result))

    def test_invalid_coordinate_and_fake_viewport_dimension_fail_schema(self):
        envelope, profile, _ = self.cases["auth_login_v2"]
        envelope = copy.deepcopy(envelope)
        envelope["coordinate_space"]["unit"] = "invented_unit"
        envelope["viewport_inputs"]["width_input"] = -320
        result = self.validate(envelope, profile)
        self.assertTrue(any("coordinate_space.unit" in item["message"] for item in result["errors"]), result["errors"])
        self.assertTrue(any("viewport_inputs.width_input" in item["message"] for item in result["errors"]), result["errors"])

    def test_second_numeric_authority_fails(self):
        envelope, profile, _ = self.cases["home_normal_v1"]
        envelope = copy.deepcopy(envelope)
        current = copy.deepcopy(envelope["authority_refs"][-1])
        current["authority_ref_id"] = "nh01-responsive-shadow"
        current["role"] = "RESPONSIVE_NUMERIC_AUTHORITY"
        envelope["authority_refs"].append(current)
        result = self.validate(envelope, profile)
        self.assertIn("DUPLICATE_NUMERIC_AUTHORITY", self.error_codes(result))

    def test_inline_numeric_value_in_profile_fails(self):
        envelope, profile, _ = self.cases["home_normal_v1"]
        profile = copy.deepcopy(profile)
        profile["conformance"]["responsive_rules"][0]["value_dp"] = 148
        result = self.validate(envelope, profile)
        self.assertIn("DUPLICATE_NUMERIC_AUTHORITY", self.error_codes(result))

    def test_wrong_authority_sha_fails(self):
        envelope, profile, _ = self.cases["home_empty_v2"]
        envelope = copy.deepcopy(envelope)
        envelope["authority_refs"][1]["sha256"] = "0" * 64
        result = validator.validate_contract(
            envelope,
            profile,
            ROOT,
            validate_bound_files=True,
            run_adapter=False,
        )
        self.assertIn("AUTHORITY_SHA_MISMATCH", self.error_codes(result))

    def test_unregistered_authority_reference_fails(self):
        envelope, profile, _ = self.cases["home_empty_v2"]
        envelope = copy.deepcopy(envelope)
        envelope["authority_refs"][1]["path"] = "harness/FEATURE_MATRIX.yaml"
        result = self.validate(envelope, profile)
        self.assertIn("AUTHORITY_NOT_REGISTERED", self.error_codes(result))

    def test_visual_authority_must_match_registered_page_authority(self):
        envelope, profile, _ = self.cases["auth_login_v2"]
        envelope = copy.deepcopy(envelope)
        visual = next(item for item in envelope["authority_refs"] if item["role"] == "PAGE_VISUAL_AUTHORITY")
        visual["path"] = "design/pages/account_privacy/Login/Login_V2_Closure.md"
        result = self.validate(envelope, profile)
        self.assertIn("VISUAL_AUTHORITY_REGISTRY_MISMATCH", self.error_codes(result))

    def test_profile_cannot_reference_another_pages_authority_id(self):
        envelope, profile, _ = self.cases["auth_login_v2"]
        profile = copy.deepcopy(profile)
        profile["conformance"]["responsive_rules"][0]["authority_bindings"][0]["authority_ref_id"] = "missing-authority"
        result = self.validate(envelope, profile)
        self.assertIn("PROFILE_AUTHORITY_BINDING_UNRESOLVED", self.error_codes(result))

    def test_acceptance_pass_requires_exact_sha_run_and_report(self):
        envelope, profile, _ = self.cases["auth_login_v2"]
        envelope = copy.deepcopy(envelope)
        envelope["acceptance"]["browser"] = "PASS"
        result = self.validate(envelope, profile)
        self.assertIn("ACCEPTANCE_EVIDENCE_INCOMPLETE", self.error_codes(result))

    def test_malformed_profile_shape_fails_without_validator_crash(self):
        envelope, _profile, _ = self.cases["home_empty_v2"]
        result = self.validate(envelope, ["not", "an", "object"])
        self.assertIn("PROFILE_INVALID", self.error_codes(result))


if __name__ == "__main__":
    unittest.main()
