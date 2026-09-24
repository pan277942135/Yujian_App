#!/usr/bin/env python3
"""Verify canonical YuJian Core UI V1 reference PNGs against the manifest."""

from __future__ import annotations
import hashlib
import json
import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REF_DIR = ROOT / "design" / "system" / "core_visual_v1" / "reference"
MANIFEST = REF_DIR / "reference_manifest.json"

def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()

def png_size(path: Path) -> tuple[int, int]:
    with path.open("rb") as f:
        sig = f.read(24)
    if len(sig) < 24 or sig[:8] != b"\x89PNG\r\n\x1a\n" or sig[12:16] != b"IHDR":
        raise ValueError("not a valid PNG")
    return struct.unpack(">II", sig[16:24])

def main() -> int:
    data = json.loads(MANIFEST.read_text(encoding="utf-8"))
    failures = []
    for item in data["references"]:
        path = REF_DIR / item["target_filename"]
        if not path.exists():
            failures.append(f"MISSING {item['target_filename']}")
            continue
        actual_sha = sha256(path)
        actual_size = path.stat().st_size
        try:
            width, height = png_size(path)
        except ValueError as e:
            failures.append(f"INVALID {item['target_filename']}: {e}")
            continue
        checks = {
            "sha256": (actual_sha, item["sha256"]),
            "bytes": (actual_size, item["bytes"]),
            "width": (width, item["width"]),
            "height": (height, item["height"]),
        }
        bad = [f"{k}={a!r} expected={e!r}" for k,(a,e) in checks.items() if a != e]
        if bad:
            failures.append(f"MISMATCH {item['target_filename']}: " + "; ".join(bad))
        else:
            print(f"PASS {item['target_filename']} {actual_sha}")
    if failures:
        for line in failures:
            print(line, file=sys.stderr)
        print(f"FAIL: {len(failures)} reference asset(s) failed verification", file=sys.stderr)
        return 1
    print("PASS: all Core UI V1 reference PNGs are present and exact")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
