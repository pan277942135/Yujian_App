#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
from pathlib import Path

ASSETS = Path("app/src/main/assets")
TEST_ASSETS = Path("app/src/androidTest/assets/detector")
ONNX = ASSETS / "fish_detector_yolox_nano_v0_1.onnx"
METADATA = ASSETS / "detector_metadata.json"
CONTRACT = ASSETS / "recognition_pipeline_v1.json"
GOLDEN_MANIFEST = TEST_ASSETS / "golden_cases.json"
GOLDEN_DIR = TEST_ASSETS / "golden"

EXPECTED_MODEL = "DET_FISH_v0.1"
EXPECTED_DATASET = "DET_DS_v0.1"
EXPECTED_FAMILY = "YOLOX_NANO"
EXPECTED_CONTRACT = "RECOGNITION_PIPELINE_v1"
EXPECTED_INPUT = 416
EXPECTED_GOLDEN_SCHEMA = "DET_FISH_GOLDEN_CASES_v1"
EXPECTED_CASE_IDS = {"ready", "no_fish", "incomplete_fish", "fish_too_small", "multiple_fish"}


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


def verify_golden_assets(actual_onnx_sha: str) -> None:
    if not GOLDEN_MANIFEST.is_file():
        fail(f"missing {GOLDEN_MANIFEST}")
    manifest = json.loads(GOLDEN_MANIFEST.read_text(encoding="utf-8"))
    if manifest.get("schema_version") != EXPECTED_GOLDEN_SCHEMA:
        fail("golden schema mismatch")
    if manifest.get("model_version") != EXPECTED_MODEL:
        fail("golden model_version mismatch")
    if manifest.get("dataset_version") != EXPECTED_DATASET:
        fail("golden dataset_version mismatch")
    if manifest.get("onnx_sha256") != actual_onnx_sha:
        fail("golden ONNX SHA256 mismatch")
    if int(manifest.get("crop_pixel_tolerance", -1)) != 0:
        fail("golden crop_pixel_tolerance must be zero")
    bbox_tolerance = manifest.get("bbox_tolerance")
    if not isinstance(bbox_tolerance, (int, float)) or float(bbox_tolerance) <= 0.0:
        fail("golden bbox_tolerance invalid")

    cases = manifest.get("cases") or []
    if len(cases) != 5:
        fail(f"golden case count mismatch: {len(cases)}")
    seen: set[str] = set()
    for case in cases:
        case_id = str(case.get("id") or "")
        if not case_id or case_id in seen:
            fail(f"invalid or duplicate golden case id: {case_id!r}")
        seen.add(case_id)
        source_uri = str(case.get("golden_gcs_uri") or "")
        suffix = Path(source_uri.rsplit("/", 1)[-1]).suffix.lower()
        if suffix not in {".jpg", ".jpeg", ".png", ".webp"}:
            fail(f"unsupported golden image suffix for {case_id}: {suffix}")
        fixture = GOLDEN_DIR / f"{case_id}{suffix}"
        if not fixture.is_file():
            fail(f"missing golden fixture {fixture}")
        expected_sha = str(case.get("source_sha256") or "")
        if not expected_sha or sha256(fixture) != expected_sha:
            fail(f"golden fixture SHA256 mismatch for {case_id}")

    if seen != EXPECTED_CASE_IDS:
        fail(f"golden case coverage mismatch: {sorted(seen)}")


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

    verify_golden_assets(actual_sha)

    print(
        "PRODUCTION_DETECTOR_VERIFY_PASS "
        f"model={EXPECTED_MODEL} bytes={actual_bytes} sha256={actual_sha} "
        f"input={onnx_doc.get('input_shape')} output={output_shape} "
        f"golden_cases={len(EXPECTED_CASE_IDS)}"
    )


if __name__ == "__main__":
    main()
