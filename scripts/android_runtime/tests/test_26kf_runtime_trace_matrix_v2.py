import json
import unittest
from pathlib import Path


MATRIX = Path(__file__).resolve().parents[3] / "docs/visual/v3/26kf/26KF_RUNTIME_TRACE_MATRIX_V2.json"
REQUIRED_FIELDS = {
    "KF_ID",
    "Visual State",
    "Runtime Stage",
    "Stage Local Progress",
    "Producer",
    "Consumer",
    "Photo Identity",
    "Mask Identity",
    "Geometry Version",
    "Path ID",
    "Required Event",
    "Render Receipt",
    "Completion Condition",
    "Fallback Branch",
    "Evidence Capture Key",
    "Test Evidence",
    "Source Status",
    "Test Status",
}


class RuntimeTraceMatrixV2Test(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.document = json.loads(MATRIX.read_text(encoding="utf-8"))
        cls.checkpoints = cls.document["checkpoints"]
        cls.by_id = {row["KF_ID"]: row for row in cls.checkpoints}

    def test_exact_frozen_26_checkpoints_and_required_trace_fields(self):
        self.assertEqual([f"KF{i:02d}" for i in range(26)], [row["KF_ID"] for row in self.checkpoints])
        self.assertEqual(26, len(self.by_id))
        for row in self.checkpoints:
            self.assertTrue(REQUIRED_FIELDS.issubset(row), row["KF_ID"])
            for field in REQUIRED_FIELDS - {"Test Status", "Source Status"}:
                self.assertTrue(str(row[field]).strip(), f"{row['KF_ID']} missing {field}")
            self.assertEqual("SOURCE_IMPLEMENTED", row["Source Status"], row["KF_ID"])
        capture_keys = [row["Evidence Capture Key"] for row in self.checkpoints]
        self.assertEqual(len(capture_keys), len(set(capture_keys)))

    def test_visual_checkpoints_do_not_create_a_second_stage_machine(self):
        self.assertIn("outside recognition animation", self.by_id["KF00"]["Runtime Stage"])
        self.assertIn("outside recognition animation", self.by_id["KF01"]["Runtime Stage"])
        self.assertIn("not a fourteenth Stage", self.by_id["KF04"]["Runtime Stage"])
        self.assertIn("internal energy state", self.by_id["KF07"]["Runtime Stage"])
        self.assertIn("internal energy state", self.by_id["KF08"]["Runtime Stage"])
        for kf_id in ("KF15", "KF16", "KF17"):
            self.assertIn("Stage11", self.by_id[kf_id]["Runtime Stage"])
        for kf_id in ("KF18", "KF19"):
            self.assertIn("Stage12", self.by_id[kf_id]["Runtime Stage"])
        self.assertIn("not an added frame", self.by_id["KF19"]["Runtime Stage"])

    def test_name_stability_is_accepted_at_stage12_and_save_is_not_a_fake_success_event(self):
        self.assertIn("Stage12 completion boundary", self.by_id["KF20"]["Runtime Stage"])
        self.assertIn("never duplicated", self.by_id["KF20"]["Runtime Stage"])
        self.assertIn("Save", self.by_id["KF25"]["Visual State"])
        self.assertIn("save", self.by_id["KF25"]["Completion Condition"].lower())
        self.assertNotIn("button highlight", self.by_id["KF25"]["Completion Condition"].lower())

    def test_every_checkpoint_has_a_non_fabricated_event_or_ui_receipt(self):
        for row in self.checkpoints:
            if row["Required Event"] == "NONE":
                self.assertTrue(
                    "UI" in row["Render Receipt"] or "UI" in row["Completion Condition"],
                    row["KF_ID"],
                )
            self.assertNotIn("FIXED_SEQUENCE", row["Render Receipt"])
            self.assertNotIn("SYNTHETIC_SUCCESS", row["Completion Condition"])


if __name__ == "__main__":
    unittest.main()
