#!/usr/bin/env python3
"""Regression test: unresolved V3.1/Resolve authority must block M1 release."""

from __future__ import annotations

import hashlib
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
FINALIZER = ROOT / "scripts/finalize_recognition_m1_v3_1_runtime_evidence.py"
CONTRACT = ROOT / "docs/qa/recognition_main_journey/m1_recognition_runtime_gate_contract_v3_1.json"


class FinalizerAuthorityCollisionTest(unittest.TestCase):
    def test_unresolved_collision_rejects_even_pass_runtime_result(self) -> None:
        with tempfile.TemporaryDirectory(prefix="m1-v3-1-finalizer-") as temporary:
            evidence_dir = Path(temporary) / "evidence"
            evidence_dir.mkdir()
            manifest = {
                "contract_id": "M1_RECOGNITION_RUNTIME_GATE_V3_1",
                "authority": {"contract_sha256": hashlib.sha256(CONTRACT.read_bytes()).hexdigest()},
                "motion_authority_collision": {"status": "REVIEW_REQUIRED"},
                "release_readiness": "REVIEW_REQUIRED",
            }
            (evidence_dir / "recognition_runtime_evidence_manifest_m1_v3_1.json").write_text(
                json.dumps(manifest), encoding="utf-8"
            )
            runtime_result = Path(temporary) / "runtime_gate_result.json"
            runtime_result.write_text(
                json.dumps(
                    {
                        "gate": "recognition-frozen",
                        "build_sha": "0" * 40,
                        "api_level": 28,
                        "classification": "PASS",
                        "preflight": "PASS",
                        "install": "PASS",
                        "instrumentation": "PASS",
                        "evidence": "PASS",
                    }
                ),
                encoding="utf-8",
            )
            result = subprocess.run(
                [
                    sys.executable,
                    str(FINALIZER),
                    "--evidence-dir",
                    str(evidence_dir),
                    "--runtime-result",
                    str(runtime_result),
                    "--workflow-run-id",
                    "123456789",
                ],
                check=False,
                capture_output=True,
                text=True,
            )
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("release remains REVIEW_REQUIRED", result.stderr + result.stdout)


if __name__ == "__main__":
    unittest.main()
