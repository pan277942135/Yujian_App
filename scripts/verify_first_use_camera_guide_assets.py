#!/usr/bin/env python3
"""Verify active two-page guide PNG bytes and keep the removed vertical page archive-only."""

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
        "path": "design/pages/recognition/first_use_camera_guide/frozen/P02_Landscape_Horizontal_Fish_Frozen.png",
        "source_filename": "湖畔横屏拍摄指南(1).png",
        "width": 941,
        "height": 1672,
        "bytes": 2_001_322,
        "sha256": "1a3008483ac5708a0649501cf2aef6c799d47b95bcc2c1b1272fa64e68f7f9b3",
    },
]
ARCHIVED_VERTICAL = {
    "path": "design/pages/recognition/first_use_camera_guide/archive/P02_Portrait_Vertical_Fish_Superseded_20261016.png",
    "bytes": 2_042_852,
    "sha256": "fb092235fd043a42aa8250aba9dfdb91a78e96549dd5a63f8e89cf7433685f8f",
}


def fail(message: str) -> None:
    print(f"FAIL: {message}", file=sys.stderr)
    raise SystemExit(1)


def load_json(path: Path) -> dict:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        fail(f"cannot load {path.relative_to(ROOT)}: {exc}")


def sha256(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def git_blob_sha(raw: bytes) -> str:
    return hashlib.sha1(b"blob " + str(len(raw)).encode("ascii") + b"\0" + raw).hexdigest()


def main() -> None:
    manifest = load_json(GUIDE / "manifest.json")
    report = load_json(GUIDE / "asset_import_report.json")
    expected_ids = [asset["id"] for asset in EXPECTED]
    expected_paths = [asset["path"] for asset in EXPECTED]

    if manifest.get("active_page_ids") != expected_ids:
        fail("manifest active_page_ids must be exactly p01/p02")
    if manifest.get("repository_binary_import", {}).get("status") != "PASS":
        fail("manifest repository_binary_import.status must be PASS")
    if manifest.get("repository_binary_import", {}).get("required_paths") != expected_paths:
        fail("manifest required_paths must contain only active P01/P02 assets in order")
    if report.get("schema_version") != "1.2" or report.get("task_id") != "RECOGNITION-FIRST-USE-GUIDE-V1.2":
        fail("asset import report schema/task id mismatch")
    if report.get("status") != "PASS" or report.get("binary_import_status") != "PASS":
        fail("asset import report must record source-byte PASS")

    manifest_assets = manifest.get("assets", [])
    report_assets = report.get("assets", [])
    if [item.get("id") for item in manifest_assets] != expected_ids:
        fail("active manifest must list only P01/P02")
    if [item.get("id") for item in report_assets] != expected_ids:
        fail("active asset report must list only P01/P02")
    manifest_by_id = {item["id"]: item for item in manifest_assets}

    for expected, recorded in zip(EXPECTED, report_assets):
        relative = expected["path"]
        path = ROOT / relative
        if not path.is_file():
            fail(f"missing frozen PNG: {relative}")
        raw = path.read_bytes()
        size = len(raw)
        digest = sha256(raw)
        blob_sha = git_blob_sha(raw)
        if raw[:8] != b"\x89PNG\r\n\x1a\n" or raw[12:16] != b"IHDR" or len(raw) < 24:
            fail(f"invalid PNG signature/IHDR: {relative}")
        width, height = struct.unpack(">II", raw[16:24])
        actual = {"width": width, "height": height, "bytes": size, "sha256": digest}
        for key, value in expected.items():
            if key in actual and actual[key] != value:
                fail(f"{relative} {key} mismatch: {actual[key]} != {value}")

        manifest_entry = manifest_by_id[expected["id"]]
        for key, value in expected.items():
            if key == "id":
                continue
            if manifest_entry.get(key) != value:
                fail(f"manifest {expected['id']} {key} mismatch")
        for key, value in {
            "path": relative,
            "filename": path.name,
            "source_filename": expected["source_filename"],
            "width": width,
            "height": height,
            "bytes": size,
            "sha256": digest,
            "git_blob_sha": blob_sha,
        }.items():
            if recorded.get(key) != value:
                fail(f"report {expected['id']} {key} mismatch: {recorded.get(key)!r} != {value!r}")
        if recorded.get("status") != "PASS":
            fail(f"report {expected['id']} must include PASS")

    archive_path = ROOT / ARCHIVED_VERTICAL["path"]
    if not archive_path.is_file():
        fail(f"removed vertical P02 must remain in the provenance archive: {ARCHIVED_VERTICAL['path']}")
    archived = archive_path.read_bytes()
    if len(archived) != ARCHIVED_VERTICAL["bytes"] or sha256(archived) != ARCHIVED_VERTICAL["sha256"]:
        fail("archived former vertical P02 bytes changed")
    for retired_name in ("P02_First_Use_Camera_Guide_Frozen.png", "P03_First_Use_Camera_Guide_Frozen.png"):
        if (GUIDE / "frozen" / retired_name).exists():
            fail(f"retired active filename still exists: {retired_name}")

    print("PASS: two active guide PNGs verified; former vertical P02 is archive-only; retired active paths are absent.")


if __name__ == "__main__":
    main()
