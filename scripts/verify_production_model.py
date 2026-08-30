#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODEL = ROOT / "app/src/main/assets/fish_classifier.tflite"
EXPECTED_SIZE = 2_077_712
EXPECTED_SHA256 = "5bb77f0bea96be2c6d2ace8a0fea36e8907bc9e4076beac05e0c82f44c345459"


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

    print("PRODUCTION_MODEL_VERIFY_OK")
    print(f"path={MODEL.relative_to(ROOT)}")
    print(f"size={size}")
    print(f"sha256={digest}")


if __name__ == "__main__":
    main()
