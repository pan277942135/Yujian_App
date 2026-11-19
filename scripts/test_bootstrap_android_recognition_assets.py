#!/usr/bin/env python3
"""Committed model bytes are the deterministic build input."""
import io
import json
import shutil
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import bootstrap_android_recognition_assets as bootstrap
from production_model_contract import ModelContractError, build_local_model_snapshot


class MainAssetBootstrapTest(unittest.TestCase):
    def test_real_main_bundle_is_v03_and_never_downloads_legacy_release(self):
        bootstrap.install_detector_bundle(bootstrap.MAIN_ASSETS / bootstrap.DETECTOR_BUNDLE)
        self.assertEqual(bootstrap.sha256(bootstrap.MAIN_ASSETS / "fish_detector_yolox_nano_v0_1.onnx"),
                         bootstrap.DETECTOR_ONNX_SHA256)
        self.assertFalse(hasattr(bootstrap, "DETECTOR_BUNDLE_ASSET_ID"))

    def test_corrupt_bundle_does_not_overwrite_committed_assets(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            old_assets = bootstrap.MAIN_ASSETS
            try:
                bootstrap.MAIN_ASSETS = root
                (root / "fish_detector_yolox_nano_v0_1.onnx").write_bytes(b"keep-original")
                bad = root / "bad.zip"
                with zipfile.ZipFile(bad, "w") as archive:
                    archive.writestr("detector_metadata.json.txt", b"{}")
                with self.assertRaises(bootstrap.BootstrapError):
                    bootstrap.install_detector_bundle(bad)
                self.assertEqual((root / "fish_detector_yolox_nano_v0_1.onnx").read_bytes(), b"keep-original")
            finally:
                bootstrap.MAIN_ASSETS = old_assets

    def test_classifier_local_snapshot_validates_real_model_and_order(self):
        snapshot = build_local_model_snapshot(bootstrap.MAIN_ASSETS, "test-head")
        self.assertEqual(snapshot["source"], "MAIN_COMMITTED_ASSETS")
        self.assertEqual(snapshot["input_shape"], [1, 3, 224, 224])
        self.assertEqual(snapshot["output_shape"], [1, 15])
        self.assertEqual(len(snapshot["class_order"]), 15)


if __name__ == "__main__":
    unittest.main()
