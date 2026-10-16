#!/usr/bin/env python3
"""Verify byte-identical frozen guide PNG imports and their provenance report."""

from __future__ import annotations

import hashlib
import json
import struct
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
GUIDE = ROOT / "design/pages/recognition/first_use_camera_guide"
EXPECTED = [
    {
        "id": "p01",
        "path": "design/pages/recognition/first_use_camera_guide/frozen/P01_First_Use_Camera_Guide_Frozen.png",
        "source_filename": "湖畔夕照拍照识鱼指南.png",
        "width": 941,
        "height": 1671,
        "bytes": 1_988_159,
        "sha256": "dc731cca5470ef2154bb06172b9836e823ea780b0833f04e34caaf883e465c50",
    },
    {
        "id": "p02",
        "path": "design/pages/recognition/first_use_camera_guide/frozen/P02_First_Use_Camera_Guide_Frozen.png",
        "source_filename": "湖畔横屏拍摄指南(1).png",
        "width": 941,
        "height": 1672,
        "bytes": 2_001_322,
        "sha256": "1a3008483ac5708a0649501cf2aef6c799d47b95bcc2c1b1272fa64e68f7f9b3",
    },
]


def fail(message: str) -> None:
    print(f"FAIL: {message}", file=sys.stderr)
    raise SystemExit(1)


def load_json(path: Path) -> dict:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        fail(f"cannot load {path.relative_to(ROOT)}: {exc}")


def main() -> None:
    manifest_path = GUIDE / "manifest.json"
    report_path = GUIDE / "asset_import_report.json"
    manifest = load_json(manifest_path)
    report = load_json(report_path)

    import_status = manifest.get("repository_binary_import", {})
    expected_paths = [asset["path"] for asset in EXPECTED]
    if import_status.get("status") != "PASS":
        fail("manifest repository_binary_import.status must be PASS")
    if import_status.get("required_paths") != expected_paths:
        fail("manifest repository_binary_import.required_paths order mismatch")
    if report.get("schema_version") != "1.1" or report.get("task_id") != "RECOGNITION-FIRST-USE-GUIDE-V1":
        fail("asset import report schema/task id mismatch")
    if report.get("status") != "PASS" or report.get("binary_import_status") != "PASS":
        fail("asset import report must record PASS")

    manifest_assets = {item.get("id"): item for item in manifest.get("assets", [])}
    report_assets = report.get("assets", [])
    if [item.get("id") for item in report_assets] != [asset["id"] for asset in EXPECTED]:
        fail("asset import report must list P01/P02 in order")

    for expected, recorded in zip(EXPECTED, report_assets):
        relative = expected["path"]
        path = ROOT / relative
        if not path.is_file():
            fail(f"missing frozen PNG: {relative}")
        raw = path.read_bytes()
        size = len(raw)
        digest = hashlib.sha256(raw).hexdigest()
        blob_sha = hashlib.sha1(b"blob " + str(size).encode("ascii") + b"\0" + raw).hexdigest()
        if raw[:8] != b"\x89PNG\r\n\x1a\n" or raw[12:16] != b"IHDR" or len(raw) < 24:
            fail(f"invalid PNG signature/IHDR: {relative}")
        width, height = struct.unpack(">II", raw[16:24])
        actual = {"width": width, "height": height, "bytes": size, "sha256": digest}
        for key, value in expected.items():
            if key in actual and actual[key] != value:
                fail(f"{relative} {key} mismatch: {actual[key]} != {value}")

        manifest_entry = manifest_assets.get(expected["id"], {})
        for key in ("path", "source_filename", "width", "height", "bytes", "sha256"):
            if manifest_entry.get(key) != expected[key]:
                fail(f"manifest {expected['id']} {key} mismatch")
        for key in ("path", "filename", "source_filename", "width", "height", "bytes", "sha256", "git_blob_sha"):
            wanted = path.name if key == "filename" else blob_sha if key == "git_blob_sha" else expected.get(key)
            if recorded.get(key) != wanted:
                fail(f"report {expected['id']} {key} mismatch: {recorded.get(key)!r} != {wanted!r}")
        if recorded.get("status") != "PASS" or not recorded.get("import_timestamp"):
            fail(f"report {expected['id']} must include PASS and import_timestamp")

    print("PASS: P01/P02 frozen guide PNG bytes, SHA-256, dimensions, and Git blob SHAs match.")


if __name__ == "__main__":
    main()
