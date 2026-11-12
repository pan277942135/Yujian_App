#!/usr/bin/env python3
"""Verify the packaged DET_FISH v0.3 graph, release snapshot and combined model manifest."""

from __future__ import annotations

import json
import sys
from pathlib import Path

from det_fish_v03_contract import (
    ANDROID_CLASSIFIER_PARITY_FILE,
    ANDROID_DETECTOR_MODEL_FILE,
    ANDROID_DETECTOR_SNAPSHOT_FILE,
    ANDROID_MODEL_PACKAGE_MANIFEST_FILE,
    DETECTOR_MODEL_VERSION,
    DetectorContractError,
    detector_snapshot_summary,
    verify_android_model_package_manifest,
    verify_classifier_bundle_parity_record,
    verify_detector_asset_directory,
)
from production_model_contract import ANDROID_SNAPSHOT_FILE

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app" / "src" / "main" / "assets"
LEGACY_PRODUCTION_DETECTOR_FILES = (
    "fish_detector_yolox_nano_v0_1.onnx",
    "det_fish_v0_1.onnx",
    "detector_v0_1.onnx",
)


def main() -> int:
    try:
        for legacy in LEGACY_PRODUCTION_DETECTOR_FILES:
            if (ASSETS / legacy).exists():
                raise DetectorContractError(f"obsolete DET_FISH v0.1 production asset remains packaged: {legacy}")

        detector_snapshot = verify_detector_asset_directory(ASSETS)
        classifier_snapshot = json.loads((ASSETS / ANDROID_SNAPSHOT_FILE).read_text(encoding="utf-8"))
        classifier_parity = json.loads((ASSETS / ANDROID_CLASSIFIER_PARITY_FILE).read_text(encoding="utf-8"))
        package_manifest = json.loads((ASSETS / ANDROID_MODEL_PACKAGE_MANIFEST_FILE).read_text(encoding="utf-8"))

        if detector_snapshot.get("model_version") != DETECTOR_MODEL_VERSION:
            raise DetectorContractError("packaged detector model version is not DET_FISH_v0.3")
        verify_classifier_bundle_parity_record(classifier_parity, classifier_snapshot)
        verify_android_model_package_manifest(
            package_manifest, detector_snapshot, classifier_snapshot, classifier_parity
        )

        # The checkpoint is verified from the approved source ZIP but is never
        # copied into Android assets. Only the ONNX runtime model is packaged.
        if any(path.suffix.lower() in {".pth", ".pt", ".ckpt"} for path in ASSETS.rglob("*") if path.is_file()):
            raise DetectorContractError("training checkpoint must not be packaged in the Android APK assets")
        if not (ASSETS / ANDROID_DETECTOR_MODEL_FILE).is_file():
            raise DetectorContractError("DET_FISH v0.3 ONNX runtime asset is missing")
        if not (ASSETS / ANDROID_DETECTOR_SNAPSHOT_FILE).is_file():
            raise DetectorContractError("DET_FISH v0.3 release snapshot is missing")

        print("DET_FISH_V03_VERIFY_OK")
        print("DETECTOR_SNAPSHOT=" + json.dumps(detector_snapshot_summary(detector_snapshot), ensure_ascii=False, sort_keys=True))
        print("CLASSIFIER_BUNDLE_PARITY_OK")
        print("ANDROID_MODEL_PACKAGE_MANIFEST_OK")
        print("PRODUCTION_DETECTOR_V0_1_ABSENT")
        print("TRAINING_CHECKPOINT_NOT_PACKAGED")
        return 0
    except (DetectorContractError, OSError, ValueError, KeyError, json.JSONDecodeError) as error:
        print(f"VERIFY_PRODUCTION_DETECTOR_FAILED: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
