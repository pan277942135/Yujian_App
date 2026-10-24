#!/usr/bin/env python3
"""Verify restricted, local-only NH01 catch-photo fixtures without publishing them."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any


SCOPE = "NH01_TASK_DESIGN_AND_ACCEPTANCE_ONLY"
EXPECTED = [
    {
        "role": "peek_left",
        "layer_id": "Card-Peek-Left",
        "asset_id": "peek_left_photo",
        "sha256": "d0c400c9854d47fdb699253a78da7b1e3b7f2394d34f0c90d91142ed35d15e88",
        "byte_length": 672273,
        "dimensions_px": [1536, 2048],
    },
    {
        "role": "hero",
        "layer_id": "Card-Main-Active",
        "asset_id": "hero_photo",
        "sha256": "34e6992a594ba0a756eeee3b2784463d3a7224a5815839888d532fa344798f04",
        "byte_length": 730637,
        "dimensions_px": [1440, 1920],
    },
    {
        "role": "peek_right",
        "layer_id": "Card-Peek-Right",
        "asset_id": "peek_right_photo",
        "sha256": "a60db3c12c2c84527af0ea803ac420d152dff3b8e7604559aaf59ffa4edf8ac4",
        "byte_length": 284989,
        "dimensions_px": [1215, 2048],
    },
]


def jpeg_dimensions(data: bytes) -> tuple[int, int]:
    if len(data) < 4 or data[:3] != b"\xff\xd8\xff":
        raise ValueError("file does not have a JPEG SOI signature")
    sof = {0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7, 0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF}
    index = 2
    while index < len(data):
        if data[index] != 0xFF:
            index += 1
            continue
        while index < len(data) and data[index] == 0xFF:
            index += 1
        if index >= len(data):
            break
        marker = data[index]
        index += 1
        if marker in {0xD8, 0xD9, 0x01, *range(0xD0, 0xD8)}:
            if marker == 0xD9:
                break
            continue
        if index + 2 > len(data):
            break
        segment_length = int.from_bytes(data[index : index + 2], "big")
        if segment_length < 2 or index + segment_length > len(data):
            raise ValueError("invalid JPEG segment length")
        if marker in sof:
            if segment_length < 7:
                raise ValueError("invalid JPEG start-of-frame segment")
            height = int.from_bytes(data[index + 3 : index + 5], "big")
            width = int.from_bytes(data[index + 5 : index + 7], "big")
            return width, height
        index += segment_length
    raise ValueError("JPEG dimensions were not found")


def verify(manifest_path: Path, provenance_path: Path) -> dict[str, Any]:
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    provenance = json.loads(provenance_path.read_text(encoding="utf-8"))
    require = lambda condition, message: (_ for _ in ()).throw(ValueError(message)) if not condition else None
    require(manifest.get("schema") == "NH01_LOCAL_PRIVATE_CATCH_FIXTURES_V1", "unexpected local fixture schema")
    require(manifest.get("scope") == SCOPE, "fixture scope is not limited to task design and acceptance")
    require(manifest.get("public_redistribution") == "NOT_CONFIRMED", "public redistribution must remain unconfirmed")
    require(manifest.get("provenance_file") == "./NH01_Private_Photo_Provenance_V1.json", "local manifest provenance link is missing or unexpected")
    require(provenance.get("schema") == "NH01_PRIVATE_PHOTO_PROVENANCE_V1", "unexpected photo provenance schema")
    require(provenance.get("task_id") == "YUJIAN_NH01_THREE_CATCH_IMAGES_AND_RESPONSIVE_V2_CLOSURE_V1", "provenance file belongs to another task")
    require(provenance.get("authorized_usage") == "NH01 task design and acceptance only", "provenance usage authorization mismatch")
    require(provenance.get("public_redistribution") == "NOT_CONFIRMED", "provenance cannot authorize public redistribution")
    records = manifest.get("records")
    require(isinstance(records, list) and len(records) == 3, "manifest must contain exactly three distinct records")
    provenance_records = provenance.get("records")
    require(isinstance(provenance_records, list) and len(provenance_records) == 3, "provenance file must contain three explicit layer mappings")
    provenance_by_asset = {record.get("asset_id"): record for record in provenance_records}
    seen_paths: set[Path] = set()
    seen_hashes: set[str] = set()
    verified = []
    media_root = (manifest_path.parent / "local_media").resolve()
    for record, expected in zip(records, EXPECTED, strict=True):
        for key in ("role", "layer_id", "asset_id", "sha256", "byte_length", "dimensions_px"):
            require(record.get(key) == expected[key], f"{record.get('asset_id', record.get('role'))}: {key} mismatch")
        require(bool(record.get("fixture_id")), f"{expected['asset_id']}: local fixture identity is missing")
        require(bool(record.get("label")), f"{expected['asset_id']}: role label is missing")
        require(record.get("usage_scope") == SCOPE, f"{expected['asset_id']}: usage scope mismatch")
        require(record.get("public_redistribution") == "NOT_CONFIRMED", f"{expected['asset_id']}: public redistribution not blocked")
        require(record.get("mime_type") == "image/jpeg", f"{expected['asset_id']}: declared file type is not JPEG")
        provenance_record = provenance_by_asset.get(record.get("asset_id"))
        require(provenance_record is not None, f"{expected['asset_id']}: provenance mapping is missing")
        for key in ("layer_id", "sha256", "byte_length", "dimensions_px", "fixture_id"):
            require(provenance_record.get(key) == record.get(key), f"{expected['asset_id']}: provenance {key} mismatch")
        relative = Path(str(record.get("local_preview_path", "")))
        require(not relative.is_absolute() and ".." not in relative.parts, f"{expected['asset_id']}: fixture path is not local and relative")
        file_path = (manifest_path.parent / relative).resolve()
        require(file_path.is_relative_to(media_root), f"{expected['asset_id']}: fixture path escaped local_media")
        require(file_path not in seen_paths, f"{expected['asset_id']}: duplicate local asset path")
        seen_paths.add(file_path)
        data = file_path.read_bytes()
        actual_sha = hashlib.sha256(data).hexdigest()
        actual_dimensions = jpeg_dimensions(data)
        require(len(data) == expected["byte_length"], f"{expected['asset_id']}: byte length mismatch")
        require(actual_sha == expected["sha256"], f"{expected['asset_id']}: SHA-256 mismatch")
        require(list(actual_dimensions) == expected["dimensions_px"], f"{expected['asset_id']}: JPEG dimensions mismatch")
        require(actual_sha not in seen_hashes, f"{expected['asset_id']}: source image is not independent")
        seen_hashes.add(actual_sha)
        verified.append({**{key: record[key] for key in ("role", "layer_id", "asset_id", "fixture_id", "label", "local_preview_path", "usage_scope", "public_redistribution")}, "mime_type": "image/jpeg", "sha256": actual_sha, "byte_length": len(data), "dimensions_px": list(actual_dimensions)})
    return {"status": "PASS", "schema": manifest["schema"], "scope": SCOPE, "public_redistribution": "NOT_CONFIRMED", "provenance_file": provenance_path.name, "provenance_file_sha256": hashlib.sha256(provenance_path.read_bytes()).hexdigest(), "records": verified}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--provenance", type=Path)
    args = parser.parse_args()
    try:
        manifest_path = args.manifest.resolve()
        provenance_path = (args.provenance or manifest_path.parent / "NH01_Private_Photo_Provenance_V1.json").resolve()
        result = verify(manifest_path, provenance_path)
    except (OSError, UnicodeDecodeError, json.JSONDecodeError, ValueError) as exc:
        raise SystemExit(f"NH01_LOCAL_PRIVATE_FIXTURES=FAIL: {exc}") from exc
    print(json.dumps(result, indent=2, ensure_ascii=False))
    print("NH01_LOCAL_PRIVATE_FIXTURES=PASS")


if __name__ == "__main__":
    main()
