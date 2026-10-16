from __future__ import annotations

import importlib.util
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MODULE_PATH = ROOT / "scripts" / "validate_control_plane_sync.py"
SPEC = importlib.util.spec_from_file_location("validate_control_plane_sync", MODULE_PATH)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


def test_canonical_exception_registry_contains_only_non_frozen_design_paths():
    exceptions = MODULE.load_worktree_exceptions()

    assert exceptions["design/pages/home/empty_home/shared/contracts/responsive_mapping_contract.json"] == (
        "4eaf3b7c17b8c4d93503bda5ade4537ac1326855"
    )
    assert exceptions["design/pages/home/empty_home/shared/contracts/runtime_manifest.json"] == (
        "1d4a386373a080fc73fea071331e19fd11d968ee"
    )
    assert len(exceptions) == 9


def test_exception_requires_exact_content_addressed_blob():
    exceptions = MODULE.load_worktree_exceptions()
    path = "design/pages/home/empty_home/shared/contracts/runtime_manifest.json"
    expected = exceptions[path]

    assert MODULE.is_approved_worktree_exception(path, expected, exceptions)
    assert not MODULE.is_approved_worktree_exception(path, "0" * 40, exceptions)
    assert not MODULE.is_approved_worktree_exception("design/unknown.txt", expected, exceptions)
