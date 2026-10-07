#!/usr/bin/env python3
import io
import os
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent))
import bootstrap_android_recognition_assets as bootstrap


class BootstrapContractTest(unittest.TestCase):
    def test_production_release_api_requests_use_workflow_token(self) -> None:
        with mock.patch.dict(os.environ, {"YUJIAN_GITHUB_TOKEN": "workflow-token"}):
            with mock.patch.object(bootstrap, "urlopen", return_value=io.BytesIO(b"{}")) as urlopen_mock:
                bootstrap.fetch_production_release()
            release_request = urlopen_mock.call_args.args[0]
            self.assertEqual(release_request.get_header("Authorization"), "Bearer workflow-token")

            with tempfile.TemporaryDirectory() as directory:
                payload = b"release-asset"
                with mock.patch.object(bootstrap, "urlopen", return_value=io.BytesIO(payload)) as urlopen_mock:
                    saved = bootstrap.download_release_asset(
                        {
                            "url": f"https://api.github.com/repos/{bootstrap.RELEASE_REPOSITORY}/releases/assets/1",
                            "name": "fixture.json",
                        },
                        Path(directory) / "fixture.json",
                    )
                asset_request = urlopen_mock.call_args.args[0]
                self.assertEqual(asset_request.get_header("Authorization"), "Bearer workflow-token")
                self.assertEqual(saved, payload)

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
