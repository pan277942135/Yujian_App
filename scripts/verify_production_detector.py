#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
from pathlib import Path

ASSETS = Path("app/src/main/assets")
ONNX = ASSETS / "fish_detector_yolox_nano_v0_1.onnx"
METADATA = ASSETS / "detector_metadata.json"
CONTRACT = ASSETS / "recognition_pipeline_v1.json"

EXPECTED_MODEL = "DET_FISH_v0.1"
EXPECTED_DATASET = "DET_DS_v0.1"
EXPECTED_FAMILY = "YOLOX_NANO"
EXPECTED_CONTRACT = "RECOGNITION_PIPELINE_v1"
EXPECTED_INPUT = 416


def fail(message: str) -> None:
    raise SystemExit(f"PRODUCTION_DETECTOR_VERIFY_FAILED: {message}")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def close(left: float, right: float) -> bool:
    return abs(left - right) <= 1e-8


def main() -> None:
    for path in (ONNX, METADATA, CONTRACT):
        if not path.is_file():
            fail(f"missing {path}")

    metadata = json.loads(METADATA.read_text(encoding="utf-8"))
    contract = json.loads(CONTRACT.read_text(encoding="utf-8"))
    if metadata.get("model_version") != EXPECTED_MODEL:
        fail("model_version mismatch")
    if metadata.get("dataset_version") != EXPECTED_DATASET:
        fail("dataset_version mismatch")
    if metadata.get("model_family") != EXPECTED_FAMILY:
        fail("model_family mismatch")

    actual_sha = sha256(ONNX)
    actual_bytes = ONNX.stat().st_size
    if metadata.get("onnx_sha256") != actual_sha:
        fail("ONNX SHA256 mismatch")
    if int(metadata.get("onnx_bytes") or 0) != actual_bytes:
        fail("ONNX byte-size mismatch")

    if contract.get("contract_version") != EXPECTED_CONTRACT:
        fail("recognition contract mismatch")
    detector = contract.get("detector") or {}
    quality = contract.get("quality_gate") or {}
    crop = contract.get("crop") or {}
    if detector.get("model_family") != EXPECTED_FAMILY or int(detector.get("input_size") or 0) != EXPECTED_INPUT:
        fail("detector contract mismatch")
    expected_values = {
        "strong_confidence": 0.35,
        "weak_confidence": 0.20,
        "nms_iou": 0.45,
    }
    for key, expected in expected_values.items():
        if not close(float(detector.get(key)), expected):
            fail(f"detector {key} mismatch")
    if not close(float(quality.get("min_primary_area_ratio")), 0.08):
        fail("min_primary_area_ratio mismatch")
    if not close(float(quality.get("incomplete_edge_margin_ratio")), 0.015):
        fail("incomplete_edge_margin_ratio mismatch")
    if not close(float(crop.get("expand_ratio")), 0.15):
        fail("crop expand_ratio mismatch")
    if int(crop.get("classifier_size") or 0) != 224 or crop.get("resize_mode") != "letterbox":
        fail("classifier crop contract mismatch")

    onnx_doc = metadata.get("onnx") or {}
    if onnx_doc.get("input_shape") != [1, 3, 416, 416]:
        fail(f"unexpected ONNX input shape {onnx_doc.get('input_shape')}")
    output_shape = onnx_doc.get("output_shape") or []
    if len(output_shape) != 3 or output_shape[0] != 1 or output_shape[-1] != 6:
        fail(f"unexpected ONNX output shape {output_shape}")

    print(
        "PRODUCTION_DETECTOR_VERIFY_PASS "
        f"model={EXPECTED_MODEL} bytes={actual_bytes} sha256={actual_sha} "
        f"input={onnx_doc.get('input_shape')} output={output_shape}"
    )


if __name__ == "__main__":
    main()
