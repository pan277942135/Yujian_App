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
SOURCE_SHA = "a" * 40
NATIVE_SHA = "b" * 40


def evidence(source_sha: str, result: str = "PASS") -> dict:
    return {
        "source_sha": source_sha,
        "workflow_or_command": "pytest-freeze-checklist",
        "run_id": None,
        "result": result,
        "report_url": "https://example.invalid/evidence-fixture",
        "artifact_urls": [],
    }


def freeze_ready_template() -> dict:
    data = copy.deepcopy(TEMPLATE)
    data["page"].update({
        "page_id": "sample_page_v1",
        "route": "sample_route",
        "implementation_entrypoints": ["design/pages/sample/renderer.js"],
        "included_states": ["default"],
        "contract_path": "design/pages/sample/responsive/contract.json",
        "contract_version": "1.0",
        "exact_source_sha": SOURCE_SHA,
    })
    data["authority_audit"].update({
        "visual_master": {"path": "design/pages/sample/master.png", "sha256": "c" * 64},
        "visual_anchors": [{
            "id": "sample-anchor",
            "path": "design/pages/sample/anchor.png",
            "sha256": "d" * 64,
            "status": "FROZEN",
        }],
        "result": "PASS",
        "evidence": [evidence(SOURCE_SHA)],
    })
    data["test_matrix"].update({
        "anchor_viewports": ["360x800"],
        "interpolation_viewports": ["375x812"],
        "threshold_boundary_probes": ["safeHeight 500/501"],
        "required_states": ["default"],
        "page_specific_assertions": ["sample-specific composition remains in bounds"],
        "generic_assertions": ["safe area consumed once", "text and touch bounds are valid"],
    })
    for gate in data["gates"][:7]:
        gate["result"] = "PASS"
        gate["evidence"] = [evidence(SOURCE_SHA)]
    data["product_owner_acceptance"].update({
        "decision": "ACCEPT",
        "reviewer": "Product Owner",
        "reviewed_at": "2026-10-26T15:00:00Z",
        "source_sha": SOURCE_SHA,
        "evidence_url": "https://example.invalid/owner-acceptance-fixture",
    })
    data["canonical_freeze"].update({
        "status": "SPEC_FROZEN",
        "main_sha": "e" * 40,
        "merge_commit_sha": "f" * 40,
    })
    return data


TEMPLATE = json.loads(TEMPLATE_PATH.read_text(encoding="utf-8"))


class ResponsivePageFreezeChecklistTests(unittest.TestCase):
    def test_reusable_template_is_structurally_valid(self):
        self.assertEqual(validate_checklist(TEMPLATE, "template"), [])

    def test_pass_gate_requires_evidence(self):
        data = copy.deepcopy(TEMPLATE)
        data["gates"][0]["result"] = "PASS"
        errors = validate_checklist(data, "template")
        self.assertTrue(any("requires at least one evidence record" in item for item in errors))

    def test_pass_gate_requires_exact_sha_match(self):
        data = copy.deepcopy(TEMPLATE)
        data["page"]["exact_source_sha"] = SOURCE_SHA
        data["gates"][0]["result"] = "PASS"
        data["gates"][0]["evidence"] = [evidence("f" * 40)]
        errors = validate_checklist(data, "template")
        self.assertTrue(any("exact target source SHA" in item for item in errors))

    def test_duplicate_gate_ids_are_rejected(self):
        data = copy.deepcopy(TEMPLATE)
        data["gates"].append(copy.deepcopy(data["gates"][0]))
        errors = validate_checklist(data, "template")
        self.assertTrue(any("duplicate gate id" in item for item in errors))

    def test_required_not_applicable_gate_is_rejected(self):
        data = copy.deepcopy(TEMPLATE)
        data["gates"][0]["result"] = "NOT_APPLICABLE"
        data["gates"][0]["reason"] = "not required for this page"
        errors = validate_checklist(data, "template")
        self.assertTrue(any("required gate cannot be NOT_APPLICABLE" in item for item in errors))

    def test_later_stage_cannot_pass_before_failed_prerequisite(self):
        data = freeze_ready_template()
        data["gates"][4]["result"] = "FAIL"
        data["gates"][4]["evidence"] = [evidence(SOURCE_SHA, "FAIL")]
        errors = validate_checklist(data, "template")
        self.assertTrue(any("cannot PASS before prerequisite G4_BROWSER_GEOMETRY_MATRIX=PASS" in item for item in errors))

    def test_freeze_mode_rejects_unfrozen_template(self):
        errors = validate_checklist(TEMPLATE, "freeze")
        self.assertTrue(any("product_owner_acceptance.decision ACCEPT" in item for item in errors))
        self.assertTrue(any("canonical_freeze.status SPEC_FROZEN" in item for item in errors))

    def test_freeze_pass_does_not_require_native_runtime_pass(self):
        data = freeze_ready_template()
        self.assertEqual(data["gates"][8]["result"], "NOT_VERIFIED")
        self.assertEqual(data["native_runtime"]["status"], "NOT_VERIFIED")
        self.assertEqual(validate_checklist(data, "freeze"), [])

    def test_freeze_requires_main_and_merge_sha(self):
        data = freeze_ready_template()
        data["canonical_freeze"]["merge_commit_sha"] = None
        errors = validate_checklist(data, "freeze")
        self.assertTrue(any("merge_commit_sha" in item for item in errors))

    def test_runtime_mode_rejects_unverified_runtime(self):
        errors = validate_checklist(TEMPLATE, "runtime")
        self.assertTrue(any("native_runtime.status PASS" in item for item in errors))
        self.assertTrue(any("native_runtime.apk_sha256" in item for item in errors))

    def test_runtime_requires_all_stages_and_native_sha_evidence(self):
        data = freeze_ready_template()
        data["native_runtime"].update({
            "status": "PASS",
            "source_sha": NATIVE_SHA,
            "build_run_id": "unit-test-fixture-run",
            "apk_sha256": "9" * 64,
            "artifact_url": "https://example.invalid/native-artifact-fixture",
        })
        for gate in data["gates"][7:]:
            gate["result"] = "PASS"
            gate["evidence"] = [evidence(NATIVE_SHA)]
        self.assertEqual(validate_checklist(data, "runtime"), [])

        data["gates"][7]["evidence"] = [evidence("8" * 40)]
        errors = validate_checklist(data, "runtime")
        self.assertTrue(any("exact target source SHA" in item for item in errors))


if __name__ == "__main__":
    unittest.main()
