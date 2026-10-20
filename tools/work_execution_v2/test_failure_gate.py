"""Regression contract for canonical-state-backed Work failure guard."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from failure_gate import record, decision, _load


CURRENT = Path(__file__).resolve().parent
SHA1 = "a" * 40
SHA2 = "b" * 40
SHA3 = "c" * 40
SHA4 = "d" * 40


def state(sha=SHA1):
    return {
        "schema_version": "1.0", "work_id": "fixture", "repository": "pan277942135/Yujian_App",
        "branch": "work/example", "task_id": "N001", "source_sha": sha,
        "status": "RUNNING", "next_action": "Run targeted check", "evidence": {}
    }


def event(sha=SHA1, hypothesis="render timeout", result="FAIL"):
    return {
        "task_id": "N001", "gate_id": "G-RUNTIME", "symptom_key": "M08_RENDER_TIMEOUT",
        "source_sha": sha, "hypothesis": hypothesis,
        "evidence": "run-id:123", "result": result,
    }


class CanonicalStateGuardTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.path = Path(self.temp.name) / "WORK_EXECUTION_STATE.json"
        self.path.write_text(json.dumps(state()), encoding="utf-8")

    def advance_source(self, sha):
        s = json.loads(self.path.read_text(encoding="utf-8"))
        s["source_sha"] = sha
        self.path.write_text(json.dumps(s), encoding="utf-8")

    def test_cross_sha_three_attempts_hard_stop_fourth_denied(self):
        first = record(self.path, event(SHA1))
        self.advance_source(SHA2)
        second = record(self.path, event(SHA2))
        self.advance_source(SHA3)
        third = record(self.path, event(SHA3, "new geometry hypothesis"))
        self.assertEqual(first["failure_id"], second["failure_id"])
        self.assertEqual(second["failure_id"], third["failure_id"])
        self.assertEqual([first["decision"], second["decision"], third["decision"]],
                         ["LOCALIZE_AND_REPRODUCE", "RETHINK_ROOT_CAUSE", "STOP_AND_ESCALATE"])
        saved = json.loads(self.path.read_text(encoding="utf-8"))
        self.assertEqual(saved["status"], "BLOCKED")
        self.assertIn("Stop same-failure", saved["next_action"])
        self.advance_source(SHA4)
        _, saved = _load(self.path)
        self.assertFalse(decision(saved, event(SHA4, "another hypothesis"))["allowed"])
        with self.assertRaisesRegex(ValueError, "STOP_AND_ESCALATE"):
            record(self.path, event(SHA4, "another hypothesis"))
        self.assertEqual(len(json.loads(self.path.read_text())["execution_v2"]["issues"][first["failure_id"]]["failures"]), 3)

    def test_after_two_failures_require_new_hypothesis(self):
        record(self.path, event(SHA1))
        self.advance_source(SHA2)
        record(self.path, event(SHA2))
        self.advance_source(SHA3)
        _, s = _load(self.path)
        self.assertEqual(decision(s, event(SHA3))["decision"], "NEW_HYPOTHESIS_REQUIRED")
        with self.assertRaisesRegex(ValueError, "NEW_HYPOTHESIS_REQUIRED"):
            record(self.path, event(SHA3))

    def test_same_sha_observation_not_counted_twice(self):
        a = record(self.path, event(SHA1))
        b = record(self.path, event(SHA1, "another error log"))
        self.assertEqual(b["decision"], "DUPLICATE_SHA_OBSERVATION")
        self.assertEqual(b["failed_attempts"], 1)
        self.assertEqual(a["failure_id"], b["failure_id"])

    def test_cannot_create_missing_state_or_accept_stale_sha(self):
        with self.assertRaisesRegex(ValueError, "WORK_EXECUTION_STATE_MISSING"):
            record(Path(self.temp.name) / "missing.json", event(SHA1))
        with self.assertRaisesRegex(ValueError, "STALE_SOURCE_SHA"):
            record(self.path, event(SHA2))
        wrong = event(SHA1)
        wrong["task_id"] = "N002"
        with self.assertRaisesRegex(ValueError, "TASK_MISMATCH"):
            record(self.path, wrong)

    def test_fix_does_not_claim_runtime_pass(self):
        record(self.path, event(SHA1))
        self.advance_source(SHA2)
        fixed = record(self.path, event(SHA2, result="FIX_VERIFIED"))
        self.assertEqual(fixed["decision"], "FIX_RECORDED_NOT_GATE_PASS")
        saved = json.loads(self.path.read_text(encoding="utf-8"))
        self.assertEqual(saved["status"], "RUNNING")
        self.assertEqual(saved["next_action"], "Run targeted check")
        with self.assertRaisesRegex(ValueError, "ISSUE_ALREADY_CLOSED"):
            record(self.path, event(SHA2))

    def test_validator_detects_invalid_status_and_fingerprint(self):
        from failure_gate import validate_recorded_failures
        saved = json.loads(self.path.read_text(encoding="utf-8"))
        self.assertEqual(validate_recorded_failures(saved), [])
        record(self.path, event(SHA1))
        self.advance_source(SHA2)
        record(self.path, event(SHA2))
        self.advance_source(SHA3)
        record(self.path, event(SHA3, "distinct diagnosis"))
        saved = json.loads(self.path.read_text(encoding="utf-8"))
        self.assertEqual(validate_recorded_failures(saved), [])
        saved["status"] = "RUNNING"
        self.assertTrue(any("must be BLOCKED" in e for e in validate_recorded_failures(saved)))
        saved["status"] = "BLOCKED"
        key = next(iter(saved["execution_v2"]["issues"]))
        saved["execution_v2"]["issues"]["forged-id"] = saved["execution_v2"]["issues"].pop(key)
        self.assertTrue(any("does not match" in e for e in validate_recorded_failures(saved)))

    def test_cli_returns_nonzero_after_third_failure(self):
        script = CURRENT / "failure_gate.py"
        for i, sha in enumerate((SHA1, SHA2, SHA3), 1):
            self.advance_source(sha)
            path = Path(self.temp.name) / ("event-" + str(i) + ".json")
            path.write_text(json.dumps(event(sha, "different path" if i == 3 else "render timeout")))
            got = subprocess.run(
                [sys.executable, str(script), "record", "--state", str(self.path), "--event", str(path)],
                capture_output=True, text=True, check=False,
            )
            self.assertEqual(got.returncode, 3 if i == 3 else 0, got.stdout + got.stderr)
        self.advance_source(SHA4)
        fourth = Path(self.temp.name) / "event-4.json"
        fourth.write_text(json.dumps(event(SHA4, "other hypothesis")))
        got = subprocess.run(
            [sys.executable, str(script), "check", "--state", str(self.path), "--event", str(fourth)],
            capture_output=True, text=True, check=False,
        )
        self.assertEqual(got.returncode, 3)


if __name__ == "__main__":
    unittest.main()
