from __future__ import annotations

import copy
import json
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "scripts" / "design"))
from validate_responsive_page_freeze_checklist import validate_checklist  # noqa: E402


TEMPLATE_PATH = ROOT / "design/system_v3/07_Responsive_System/Responsive_Page_Freeze_Checklist_Template_V1.json"


class ResponsivePageFreezeChecklistTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.template = json.loads(TEMPLATE_PATH.read_text(encoding="utf-8"))

    def test_reusable_template_is_structurally_valid(self):
        self.assertEqual(validate_checklist(self.template, "template"), [])

    def test_pass_gate_requires_exact_sha_run_and_artifact_evidence(self):
        data = copy.deepcopy(self.template)
        data["gates"][0]["result"] = "PASS"
        errors = validate_checklist(data, "template")
        self.assertTrue(any("PASS requires at least one evidence record" in e for e in errors))

    def test_duplicate_gate_ids_are_rejected(self):
        data = copy.deepcopy(self.template)
        data["gates"].append(copy.deepcopy(data["gates"][0]))
        errors = validate_checklist(data, "template")
        self.assertTrue(any("duplicate gate id" in e for e in errors))

    def test_not_applicable_requires_reason(self):
        data = copy.deepcopy(self.template)
        data["gates"][0]["result"] = "NOT_APPLICABLE"
        errors = validate_checklist(data, "template")
        self.assertTrue(any("NOT_APPLICABLE requires a reason" in e for e in errors))

    def test_freeze_mode_rejects_unfrozen_template(self):
        errors = validate_checklist(self.template, "freeze")
        self.assertTrue(any("product_owner_acceptance.decision ACCEPT" in e for e in errors))
        self.assertTrue(any("canonical_freeze.status SPEC_FROZEN" in e for e in errors))

    def test_runtime_mode_rejects_unverified_runtime(self):
        errors = validate_checklist(self.template, "runtime")
        self.assertTrue(any("native_runtime.status PASS" in e for e in errors))
        self.assertTrue(any("native_runtime.apk_sha256" in e for e in errors))


if __name__ == "__main__":
    unittest.main()
