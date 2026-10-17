"""Check canonical main PR promotion without weakening real product Work admission."""
import importlib.util
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

SOURCE = Path(__file__).resolve().parents[1] / "scripts/validate_control_plane_admission.py"
spec = importlib.util.spec_from_file_location("yujian_admission", SOURCE)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class MainPromotionAdmissionTest(unittest.TestCase):
    def call(self, *, event, base="main", branch="integration/work-main-closure-20261017",
             static_pass=True, clean=True):
        def static_gate(label, args):
            if label == "FEATURE_MATRIX":
                return static_pass
            return True
        env = {"GITHUB_EVENT_NAME": event, "GITHUB_BASE_REF": base}
        with mock.patch.dict(os.environ, env, clear=True), \
             mock.patch.object(module, "branch_name", return_value=branch), \
             mock.patch.object(module, "run", side_effect=static_gate) as gates, \
             mock.patch.object(module, "validate_canonical_promotion_tree", return_value=clean) as tree, \
             mock.patch.object(sys, "argv", ["validator", "--no-fetch-main"]):
            rc = module.main()
        return rc, [c.args[0] for c in gates.call_args_list], tree.called

    def test_pr_to_main_validates_static_and_canonical_tree(self):
        rc, names, tree_checked = self.call(event="pull_request", base="main")
        self.assertEqual(0, rc)
        self.assertEqual(["PYTHON_SYNTAX", "CONTROL_PLANE_REGISTRY", "FEATURE_MATRIX",
                          "PHASE_TASK_STATE_MACHINE", "UNIFIED_CONTRACT"], names)
        self.assertTrue(tree_checked)

    def test_product_pr_to_nonmain_retains_strict_branch_admission(self):
        rc, names, tree_checked = self.call(
            event="pull_request", base="audit/design-system-v3-1-phase1-master-baseline")
        self.assertEqual(0, rc)
        self.assertIn("CONTROL_PLANE_SYNC", names)
        self.assertIn("PROJECT_MASTER_STATE", names)
        self.assertIn("WORK_EXECUTION_STATE", names)
        self.assertFalse(tree_checked)

    def test_work_branch_push_remains_strict(self):
        rc, names, tree_checked = self.call(event="push", base="", branch="fix/example")
        self.assertEqual(0, rc)
        self.assertIn("PROJECT_MASTER_STATE", names)
        self.assertFalse(tree_checked)

    def test_main_promotion_fails_when_any_static_validator_fails(self):
        rc, names, tree_checked = self.call(event="pull_request", static_pass=False)
        self.assertEqual(1, rc)
        self.assertIn("FEATURE_MATRIX", names)
        self.assertTrue(tree_checked)

    def test_main_promotion_fails_if_branch_local_state_leaks(self):
        rc, _, tree_checked = self.call(event="pull_request", clean=False)
        self.assertEqual(1, rc)
        self.assertTrue(tree_checked)

    def test_real_promotion_guard_rejects_live_state_files(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            with mock.patch.object(module, "ROOT", root):
                self.assertTrue(module.validate_canonical_promotion_tree())
                (root / "PROJECT_MASTER_STATE.yaml").write_text("state: fake")
                self.assertFalse(module.validate_canonical_promotion_tree())
                (root / "PROJECT_MASTER_STATE.yaml").unlink()
                (root / "WORK_EXECUTION_STATE.json").write_text("{}")
                self.assertFalse(module.validate_canonical_promotion_tree())

    def test_main_branch_validates_canonical_tree(self):
        rc, names, checked = self.call(event="push", base="", branch="main")
        self.assertEqual(0, rc)
        self.assertTrue(checked)
        self.assertNotIn("PROJECT_MASTER_STATE", names)


if __name__ == "__main__":
    unittest.main()
