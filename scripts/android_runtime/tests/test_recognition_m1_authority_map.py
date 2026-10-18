#!/usr/bin/env python3
"""Unit checks for M1 Design System authority-map validation."""

from __future__ import annotations

import hashlib
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "scripts"))
from verify_recognition_m1_v3_1_runtime_evidence import validate_design_authority_matrix  # noqa: E402


class AuthorityMapTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        self.matrix_path = self.root / "docs/qa/M1_DESIGN_AUTHORITY_MATRIX.json"
        self.matrix_path.parent.mkdir(parents=True)
        for relative in (
            "design/authority.md",
            "design/contract.md",
            "design/foundation.md",
            "design/p01.png",
            "design/p01_save.png",
        ):
            path = self.root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(relative.encode("utf-8"))

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def row(self, component_id: str, target: str | list[str], asset: str) -> dict:
        return {
            "page_id": "P01",
            "state_id": "ready",
            "component_id": component_id,
            "version": "1.0",
            "manager_route": "Design System / Recognition / P01",
            "effective_authority_path": "design/authority.md",
            "frozen_asset_paths": [asset],
            "asset_sha256": {
                asset: hashlib.sha256((self.root / asset).read_bytes()).hexdigest(),
            },
            "contract_path": "design/contract.md",
            "shared_foundation_paths": ["design/foundation.md"],
            "source_status": "FROZEN",
            "expected_runtime_target": target,
            "known_gap": None,
        }

    def write_matrix(self, rows: list[dict]) -> None:
        self.matrix_path.write_text(
            json.dumps({"authority_rows": rows}, indent=2) + "\n",
            encoding="utf-8",
        )

    def test_component_rows_and_string_or_list_targets_are_supported(self) -> None:
        rows = [
            self.row("", "P01_runtime.png", "design/p01.png"),
            self.row("save_button", ["P01_save_runtime.png"], "design/p01_save.png"),
        ]
        self.write_matrix(rows)
        result = validate_design_authority_matrix(
            self.matrix_path,
            self.root,
            {"P01_runtime.png", "P01_save_runtime.png"},
        )
        self.assertEqual(result["status"], "SOURCE_MAP_VALIDATED")
        self.assertEqual(result["runtime_target_gaps"], [])
        self.assertEqual(len(result["rows"]), 2)

    def test_duplicate_page_state_component_is_rejected(self) -> None:
        row = self.row("save_button", "P01_save_runtime.png", "design/p01_save.png")
        self.write_matrix([row, row])
        with self.assertRaises(SystemExit):
            validate_design_authority_matrix(self.matrix_path, self.root, {"P01_save_runtime.png"})

    def test_known_gap_keeps_mapped_target_in_review(self) -> None:
        row = self.row("", "P01_runtime.png", "design/p01.png")
        row["known_gap"] = "Runtime shadow not yet reviewed"
        self.write_matrix([row])
        result = validate_design_authority_matrix(self.matrix_path, self.root, {"P01_runtime.png"})
        self.assertEqual(result["status"], "REVIEW_REQUIRED")
        self.assertEqual(result["review_required_runtime_targets"], ["P01_runtime.png"])

    def test_missing_matrix_is_explicitly_blocked(self) -> None:
        result = validate_design_authority_matrix(
            self.matrix_path,
            self.root,
            {"P01_runtime.png"},
        )
        self.assertEqual(result["status"], "BLOCKED_SOURCE_MATRIX_MISSING")


if __name__ == "__main__":
    unittest.main()
