import hashlib
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from package_api36_evidence import package_evidence  # noqa: E402


class PackageApi36EvidenceTest(unittest.TestCase):
    def test_refreshes_stale_manifest_and_creates_verified_zip_and_sidecar(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "evidence" / "recognition-result-api36"
            (root / "runtime_trace").mkdir(parents=True)
            (root / "runtime_trace" / "events.jsonl").write_text('{"event":"TEST_STARTED"}\n')
            (root / "runtime-result.json").write_text('{"source_sha":"exact-head"}\n')
            (root / "evidence-sha256sums.txt").write_text("0" * 64 + "  missing-old-file.txt\n")
            archive_path = Path(directory) / "evidence" / "YuJian-evidence.zip"

            size, digest = package_evidence(root, archive_path)

            self.assertGreater(size, 0)
            self.assertEqual(digest, hashlib.sha256(archive_path.read_bytes()).hexdigest())
            self.assertEqual(
                f"{digest}  {archive_path.name}\n",
                archive_path.with_suffix(".zip.sha256").read_text(),
            )
            with zipfile.ZipFile(archive_path) as zipped:
                self.assertIsNone(zipped.testzip())
                self.assertEqual(
                    {
                        "runtime-result.json",
                        "runtime_trace/events.jsonl",
                        "evidence-sha256sums.txt",
                    },
                    set(zipped.namelist()),
                )
            checksum_rows = (root / "evidence-sha256sums.txt").read_text().splitlines()
            self.assertEqual(2, len(checksum_rows))
            for row in checksum_rows:
                expected, relative = row.split("  ", 1)
                self.assertEqual(expected, hashlib.sha256((root / relative).read_bytes()).hexdigest())

    def test_rejects_archive_inside_source_tree(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "evidence"
            with self.assertRaisesRegex(ValueError, "outside its source directory"):
                package_evidence(root, root / "archive.zip")


if __name__ == "__main__":
    unittest.main()
