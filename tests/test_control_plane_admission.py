import contextlib
import io
from pathlib import Path
import tempfile
import unittest

from scripts.validate_control_plane_admission import validate_canonical_promotion_tree


class CanonicalPromotionTreeTests(unittest.TestCase):
    def test_clean_tree_is_allowed(self):
        with tempfile.TemporaryDirectory() as tmp, contextlib.redirect_stdout(io.StringIO()):
            self.assertTrue(validate_canonical_promotion_tree(Path(tmp)))

    def test_branch_local_state_and_logs_are_rejected(self):
        for forbidden in (
            "PROJECT_MASTER_STATE.yaml",
            "WORK_EXECUTION_STATE.json",
            "harness/build-log.md",
        ):
            with self.subTest(path=forbidden), tempfile.TemporaryDirectory() as tmp:
                path = Path(tmp) / forbidden
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text("branch-local state\n", encoding="utf-8")
                with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
                    self.assertFalse(validate_canonical_promotion_tree(Path(tmp)))


if __name__ == "__main__":
    unittest.main()
