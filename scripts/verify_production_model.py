#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODEL = ROOT / "app/src/main/assets/fish_classifier.tflite"
CONTRACT = ROOT / "app/src/main/assets/model_tensor_contract.json"
CLASS_MAP = ROOT / "app/src/main/assets/model_class_map.json"
MANIFEST = ROOT / "app/src/main/assets/model_release_manifest.json"
EXPECTED_RELEASE_REPOSITORY = "pan277942135/Yujian"
EXPECTED_RELEASE_TAG = "mobile-model-v0.2"
EXPECTED_SCHEMA = "YUJIAN_ANDROID_MODEL_RELEASE_v1"
EXPECTED_INPUT_SHAPE = [1, 3, 224, 224]
EXPECTED_DTYPE = "<class 'numpy.float32'>"


def fail(message: str) -> None:
    print(f"PRODUCTION_MODEL_VERIFY_FAILED: {message}", file=sys.stderr)
    raise SystemExit(1)


def read_json(path: Path, label: str) -> dict:
    if not path.is_file():
        fail(f"missing {path.relative_to(ROOT)}")
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        fail(f"invalid {label}: {exc}")
    if not isinstance(value, dict):
        fail(f"invalid {label}: expected JSON object")
    return value


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> None:
    manifest = read_json(MANIFEST, "model release manifest")
    if manifest.get("schema_version") != EXPECTED_SCHEMA:
        fail(f"manifest schema mismatch: {manifest.get('schema_version')}")
    if manifest.get("release_repository") != EXPECTED_RELEASE_REPOSITORY:
        fail(f"release repository mismatch: {manifest.get('release_repository')}")
    if manifest.get("release_tag") != EXPECTED_RELEASE_TAG:
        fail(f"release tag mismatch: {manifest.get('release_tag')}")

    model_version = str(manifest.get("model_version") or "").strip()
    expected_sha = str(manifest.get("model_sha256") or "").lower()
    expected_size = int(manifest.get("model_bytes") or 0)
    expected_tensor_sha = str(manifest.get("tensor_contract_sha256") or "").lower()
    expected_class_map_sha = str(manifest.get("class_map_sha256") or "").lower()
    expected_class_count = int(manifest.get("class_count") or 0)
    if not model_version:
        fail("manifest model_version is required")
    if not re.fullmatch(r"[0-9a-f]{64}", expected_sha):
        fail(f"invalid manifest model_sha256: {expected_sha}")
    if expected_size <= 0:
        fail(f"invalid manifest model_bytes: {expected_size}")
    if not re.fullmatch(r"[0-9a-f]{64}", expected_tensor_sha):
        fail(f"invalid manifest tensor_contract_sha256: {expected_tensor_sha}")
    if not re.fullmatch(r"[0-9a-f]{64}", expected_class_map_sha):
        fail(f"invalid manifest class_map_sha256: {expected_class_map_sha}")
    if expected_class_count <= 0:
        fail(f"invalid manifest class_count: {expected_class_count}")

    if not MODEL.is_file():
        fail(f"missing {MODEL.relative_to(ROOT)}")
    size = MODEL.stat().st_size
    if size != expected_size:
        fail(f"size mismatch: expected {expected_size}, got {size}")
    digest = sha256(MODEL)
    if digest != expected_sha:
        fail(f"sha256 mismatch: expected {expected_sha}, got {digest}")

    with MODEL.open("rb") as fh:
        header = fh.read(8)
    if len(header) < 8 or header[4:8] != b"TFL3":
        fail(f"invalid TFLite flatbuffer identifier: {header!r}")

    contract = read_json(CONTRACT, "tensor contract")
    if sha256(CONTRACT) != expected_tensor_sha:
        fail("tensor contract SHA-256 does not match model release manifest")

    class_map = read_json(CLASS_MAP, "class map")
    if sha256(CLASS_MAP) != expected_class_map_sha:
        fail("class map SHA-256 does not match model release manifest")
    classes = class_map.get("classes")
    if not isinstance(classes, list) or not classes:
        fail("class map classes must be a non-empty list")
    try:
        ordered_classes = sorted(classes, key=lambda item: int(item.get("class_index", -1)))
        indexes = [int(item.get("class_index", -1)) for item in ordered_classes]
    except (TypeError, ValueError, AttributeError) as exc:
        fail(f"invalid class map: {exc}")
    if indexes != list(range(len(ordered_classes))):
        fail(f"class indexes must be contiguous from zero, got {indexes}")
    class_keys = [str(item.get("species_key") or "").strip() for item in ordered_classes]
    if any(not key for key in class_keys) or len(set(class_keys)) != len(class_keys):
        fail("class map species_key values must be non-empty and unique")
    if len(ordered_classes) != expected_class_count:
        fail(
            f"class count mismatch: manifest={expected_class_count}, class_map={len(ordered_classes)}"
        )

    try:
        input_contract = contract["inputs"][0]
        output_contract = contract["outputs"][0]
    except (KeyError, IndexError, TypeError) as exc:
        fail(f"invalid tensor contract: {exc}")

    if input_contract.get("shape") != EXPECTED_INPUT_SHAPE:
        fail(f"input shape mismatch: expected {EXPECTED_INPUT_SHAPE}, got {input_contract.get('shape')}")
    if input_contract.get("dtype") != EXPECTED_DTYPE:
        fail(f"input dtype mismatch: expected {EXPECTED_DTYPE}, got {input_contract.get('dtype')}")
    output_shape = output_contract.get("shape")
    if not isinstance(output_shape, list) or not output_shape or output_shape[-1] != expected_class_count:
        fail(
            f"output class dimension mismatch: expected {expected_class_count}, got {output_shape}"
        )
    if output_contract.get("dtype") != EXPECTED_DTYPE:
        fail(f"output dtype mismatch: expected {EXPECTED_DTYPE}, got {output_contract.get('dtype')}")

    print("PRODUCTION_MODEL_VERIFY_OK")
    print(f"release={EXPECTED_RELEASE_REPOSITORY}@{EXPECTED_RELEASE_TAG}")
    print(f"model_version={model_version}")
    print(f"dataset_id={manifest.get('dataset_id') or '-'}")
    print(f"class_count={expected_class_count}")
    print("classes=" + ",".join(class_keys))
    print(f"path={MODEL.relative_to(ROOT)}")
    print(f"size={size}")
    print(f"sha256={digest}")


if __name__ == "__main__":
    main()
