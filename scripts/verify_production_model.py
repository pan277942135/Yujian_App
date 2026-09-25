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
LABELS_SOURCE = ROOT / "app/src/main/java/com/yujian/ai/ai/FishRecognitionEngine.kt"
EXPECTED_SIZE = 6_249_008
EXPECTED_SHA256 = "b77ea78e7f8554078ea3a79051039af1ace04f0ac4e2604da57d1dd8f0b010e7"
EXPECTED_MODEL_VERSION = "MODEL_M1_v0.6"
EXPECTED_INPUT_SHAPE = [1, 3, 224, 224]
EXPECTED_OUTPUT_SHAPE = [1, 16]
EXPECTED_DTYPE = "<class 'numpy.float32'>"
EXPECTED_CLASS_KEYS = [
    "bighead_carp",
    "black_carp",
    "blunt_snout_bream",
    "chinese_catfish",
    "common_carp",
    "crucian_carp",
    "grass_carp",
    "largemouth_bass",
    "mandarin_fish",
    "other_freshwater_fish",
    "sharpbelly",
    "silver_carp",
    "snakehead",
    "tilapia",
    "topmouth_culter",
    "yellow_catfish",
]


def fail(message: str) -> None:
    print(f"PRODUCTION_MODEL_VERIFY_FAILED: {message}", file=sys.stderr)
    raise SystemExit(1)


def main() -> None:
    if not MODEL.is_file():
        fail(f"missing {MODEL.relative_to(ROOT)}")

    size = MODEL.stat().st_size
    if size != EXPECTED_SIZE:
        fail(f"size mismatch: expected {EXPECTED_SIZE}, got {size}")

    digest = hashlib.sha256(MODEL.read_bytes()).hexdigest()
    if digest != EXPECTED_SHA256:
        fail(f"sha256 mismatch: expected {EXPECTED_SHA256}, got {digest}")

    with MODEL.open("rb") as fh:
        header = fh.read(8)
    if len(header) < 8 or header[4:8] != b"TFL3":
        fail(f"invalid TFLite flatbuffer identifier: {header!r}")

    if not CONTRACT.is_file():
        fail(f"missing {CONTRACT.relative_to(ROOT)}")
    try:
        contract = json.loads(CONTRACT.read_text(encoding="utf-8"))
        input_contract = contract["inputs"][0]
        output_contract = contract["outputs"][0]
    except (OSError, json.JSONDecodeError, KeyError, IndexError, TypeError) as exc:
        fail(f"invalid tensor contract: {exc}")

    if input_contract.get("shape") != EXPECTED_INPUT_SHAPE:
        fail(f"input shape mismatch: expected {EXPECTED_INPUT_SHAPE}, got {input_contract.get('shape')}")
    if input_contract.get("dtype") != EXPECTED_DTYPE:
        fail(f"input dtype mismatch: expected {EXPECTED_DTYPE}, got {input_contract.get('dtype')}")
    if output_contract.get("shape") != EXPECTED_OUTPUT_SHAPE:
        fail(f"output shape mismatch: expected {EXPECTED_OUTPUT_SHAPE}, got {output_contract.get('shape')}")
    if output_contract.get("dtype") != EXPECTED_DTYPE:
        fail(f"output dtype mismatch: expected {EXPECTED_DTYPE}, got {output_contract.get('dtype')}")

    source = LABELS_SOURCE.read_text(encoding="utf-8")
    class_keys = re.findall(r'^\s+"([a-z0-9_]+)" to "[^"]+",?$', source, re.MULTILINE)
    if class_keys != EXPECTED_CLASS_KEYS:
        fail(f"Android label order mismatch: expected {EXPECTED_CLASS_KEYS}, got {class_keys}")

    print("PRODUCTION_MODEL_VERIFY_OK")
    print(f"model_version={EXPECTED_MODEL_VERSION}")
    print(f"class_count={len(EXPECTED_CLASS_KEYS)}")
    print("classes=" + ",".join(EXPECTED_CLASS_KEYS))
    print(f"path={MODEL.relative_to(ROOT)}")
    print(f"size={size}")
    print(f"sha256={digest}")


if __name__ == "__main__":
    main()
