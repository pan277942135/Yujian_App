#!/usr/bin/env python3
"""Verify Main-committed v0.3 Detector and v0.2 Classifier for an Android build."""

from __future__ import annotations

import hashlib
import json
import os
import subprocess
import sys
import time
import zipfile
from pathlib import Path

from production_model_contract import (
    ANDROID_CLASS_MAP_FILE,
    ANDROID_METADATA_FILE,
    ANDROID_MODEL_FILE,
    ANDROID_SNAPSHOT_FILE,
    ANDROID_TENSOR_CONTRACT_FILE,
    ModelContractError,
    snapshot_summary,
)


ROOT = Path(__file__).resolve().parents[1]
MAIN_ASSETS = ROOT / "app" / "src" / "main" / "assets"
DETECTOR_BUNDLE = "det-fish-v03.zip"
DETECTOR_ONNX_SHA256 = "98257b953b0e9f8aa673572fe4a9749a730ed8a2c014a42b7c492a0e3592c9a1"
DETECTOR_MEMBER_MAP = {
    "fish_detector_yolox_nano_v0_1.onnx": "models_DET_FISH_v0.3_fish_detector_yolox_nano_v0_1.onnx",
    "detector_metadata.json": "detector_metadata.json.txt",
    "recognition_pipeline_v1.json": "recognition_pipeline_v1.json.txt",
}

class BootstrapError(RuntimeError):
    pass


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _member_candidates(archive: zipfile.ZipFile, basename: str) -> list[str]:
    return [name for name in archive.namelist() if name == basename or name.endswith(f"/{basename}")]


def install_detector_bundle(bundle: Path) -> None:
    """Validate the committed v0.3 bundle and destination before any replacement."""
    if not bundle.is_file():
        raise BootstrapError(f"detector bundle: missing committed archive {bundle}")
    with zipfile.ZipFile(bundle) as archive:
        candidates = {
            target: _member_candidates(archive, source)
            for target, source in DETECTOR_MEMBER_MAP.items()
        }
        if any(len(names) != 1 for names in candidates.values()):
            raise BootstrapError(f"detector bundle: missing or duplicate v0.3 members: {candidates}")
        payloads = {target: archive.read(names[0]) for target, names in candidates.items()}
    model = payloads["fish_detector_yolox_nano_v0_1.onnx"]
    metadata = json.loads(payloads["detector_metadata.json"])
    contract = json.loads(payloads["recognition_pipeline_v1.json"])
    actual = hashlib.sha256(model).hexdigest()
    if actual != DETECTOR_ONNX_SHA256 or metadata.get("onnx_sha256") != actual:
        raise BootstrapError(f"detector bundle: frozen ONNX SHA mismatch: {actual}")
    if metadata.get("model_version") != "DET_FISH_v0.3" or metadata.get("dataset_version") != "DET_DS_v0.3":
        raise BootstrapError("detector bundle: model/dataset version mismatch")
    if metadata.get("onnx_bytes") != len(model) or metadata.get("model_family") != "YOLOX_NANO":
        raise BootstrapError("detector bundle: model size/family mismatch")
    if (metadata.get("onnx") or {}).get("input_shape") != [1, 3, 416, 416] or (metadata.get("onnx") or {}).get("output_shape") != [1, 3549, 6]:
        raise BootstrapError("detector bundle: tensor shape mismatch")
    if contract.get("contract_version") != "RECOGNITION_PIPELINE_v1" or (contract.get("detector") or {}).get("nms_iou") != 0.45:
        raise BootstrapError("detector bundle: pipeline contract mismatch")
    # Main already contains the production files. Fail closed on drift instead of overwriting it.
    for target, payload in payloads.items():
        destination = MAIN_ASSETS / target
        if destination.is_file() and destination.read_bytes() != payload:
            raise BootstrapError(f"detector bundle: committed asset differs from ZIP: {destination}")
    for target, payload in payloads.items():
        destination = MAIN_ASSETS / target
        if not destination.is_file():
            _write_asset(destination, payload)
    print(f"DETECTOR_MAIN_BUNDLE_PASS zip_sha256={sha256(bundle)} onnx_sha256={actual}")


def run_verifier(script: str) -> None:
    result = subprocess.run([sys.executable, str(ROOT / "scripts" / script)], cwd=ROOT)
    if result.returncode:
        raise BootstrapError(f"{script} failed with exit code {result.returncode}")


def _write_asset(path: Path, payload: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f".{path.name}.install-{time.time_ns()}")
    try:
        temporary.write_bytes(payload)
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


def bootstrap(root: Path = ROOT) -> None:
    global MAIN_ASSETS
    original_main_assets = MAIN_ASSETS
    try:
        MAIN_ASSETS = root / "app" / "src" / "main" / "assets"
        install_detector_bundle(MAIN_ASSETS / DETECTOR_BUNDLE)
        # The committed classifier files are the deterministic source. The verifier
        # writes only a build-scoped provenance snapshot after validating all bytes.
        from production_model_contract import build_local_model_snapshot
        snapshot = build_local_model_snapshot(MAIN_ASSETS, os.environ.get("BUILD_SHA") or os.environ.get("GITHUB_SHA"))
        _write_asset(MAIN_ASSETS / ANDROID_SNAPSHOT_FILE,
                     (json.dumps(snapshot, ensure_ascii=False, sort_keys=True, indent=2) + "\n").encode())
        run_verifier("verify_production_model.py")
        run_verifier("verify_production_detector.py")
        print("BOOTSTRAP_ANDROID_RECOGNITION_ASSETS_PASS")
        print("MODEL_RELEASE_SNAPSHOT=" + json.dumps(snapshot_summary(snapshot), ensure_ascii=False, sort_keys=True))
    finally:
        MAIN_ASSETS = original_main_assets


def main() -> int:
    try:
        bootstrap()
    except (BootstrapError, ModelContractError, OSError, ValueError, zipfile.BadZipFile) as error:
        print(f"BOOTSTRAP_ANDROID_RECOGNITION_ASSETS_FAILED: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
