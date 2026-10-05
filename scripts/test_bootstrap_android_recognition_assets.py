#!/usr/bin/env python3
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import bootstrap_android_recognition_assets as bootstrap


class BootstrapContractTest(unittest.TestCase):
    def test_missing_model_fails(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(bootstrap.BootstrapError):
                bootstrap.verify_file_hash(Path(directory) / "missing.tflite", "0" * 64, "classifier")

    def test_bad_sha_fails(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "model.tflite"
            path.write_bytes(b"not a production model")
            with self.assertRaises(bootstrap.BootstrapError):
                bootstrap.verify_file_hash(path, "0" * 64, "classifier")

    def test_missing_detector_bundle_member_fails(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            archive_path = Path(directory) / "bundle.zip"
            with zipfile.ZipFile(archive_path, "w") as archive:
                for member in bootstrap.REQUIRED_DETECTOR_MEMBERS[:-1]:
                    archive.writestr(member, b"fixture")
                archive.writestr("golden/ready.jpg", b"fixture")
            with zipfile.ZipFile(archive_path) as archive:
                with self.assertRaises(bootstrap.BootstrapError):
                    bootstrap.find_required_detector_members(archive)


if __name__ == "__main__":
    unittest.main()
