from __future__ import annotations

import importlib.util
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MODULE_PATH = ROOT / "scripts" / "validate_control_plane_sync.py"
SPEC = importlib.util.spec_from_file_location("validate_control_plane_sync", MODULE_PATH)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class WorktreeExceptionTests(unittest.TestCase):
    def test_canonical_exception_registry_contains_only_non_frozen_design_paths(self):
        exceptions = MODULE.load_worktree_exceptions()

        self.assertEqual(
            exceptions["design/pages/home/empty_home/shared/contracts/responsive_mapping_contract.json"],
            "4eaf3b7c17b8c4d93503bda5ade4537ac1326855",
        )
        self.assertEqual(
            exceptions["design/pages/home/empty_home/shared/contracts/runtime_manifest.json"],
            "1d4a386373a080fc73fea071331e19fd11d968ee",
        )
        self.assertEqual(
            exceptions["design/pages/account_privacy/Login/structured_candidate/component_mapping_v1.json"],
            "75c615d20be011b16e4e6843b41dabd0479a8d63",
        )
        self.assertEqual(len(exceptions), 10)

    def test_exception_requires_exact_content_addressed_blob(self):
        exceptions = MODULE.load_worktree_exceptions()
        path = "design/pages/home/empty_home/shared/contracts/runtime_manifest.json"
        expected = exceptions[path]

        self.assertTrue(MODULE.is_approved_worktree_exception(path, expected, exceptions))
        self.assertFalse(MODULE.is_approved_worktree_exception(path, "0" * 40, exceptions))
        self.assertFalse(MODULE.is_approved_worktree_exception("design/unknown.txt", expected, exceptions))

    def test_extra_candidate_path_requires_exact_blob(self):
        path = "design/pages/account_privacy/Login/structured_candidate/component_mapping_v1.json"
        exceptions = {path: ""}
        with tempfile.TemporaryDirectory() as directory:
            candidate = Path(directory) / "component_mapping_v1.json"
            candidate.write_text("candidate mapping", encoding="utf-8")
            expected = MODULE.local_blob(candidate)
            exceptions[path] = expected

            self.assertTrue(MODULE.is_approved_extra_worktree_file(path, candidate, exceptions))
            candidate.write_text("changed mapping", encoding="utf-8")
            self.assertFalse(MODULE.is_approved_extra_worktree_file(path, candidate, exceptions))
            self.assertFalse(MODULE.is_approved_extra_worktree_file("design/unknown.txt", candidate, exceptions))


if __name__ == "__main__":
    unittest.main()
