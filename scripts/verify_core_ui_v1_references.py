#!/usr/bin/env python3
"""Verify frozen YuJian Core UI V1 reference PNGs against their manifest."""

from __future__ import annotations

import argparse
import binascii
import hashlib
import json
import struct
import sys
import zlib
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_REFERENCE_DIR = ROOT / "design" / "system" / "core_visual_v1" / "reference"

EXPECTED_CORE = {
    "empty_home_v2": {
        "source_filename": "晨雾湖畔，记录第一条鱼.png",
        "canonical_filename": "empty_home_v2.png",
    },
    "normal_home_v1": {
        "source_filename": "yujian_home_with_catches_full_bleed_refined_v1.png",
        "canonical_filename": "normal_home_v1.png",
    },
    "recognition_result_v1": {
        "source_filename": "湖畔钓获识别记录界面.png",
        "canonical_filename": "recognition_result_v1.png",
    },
    "fish_record_detail_v2": {
        "source_filename": "湖畔鱼获详情界面.png",
        "canonical_filename": "fish_record_detail_v2.png",
    },
    "my_catches_v2": {
        "source_filename": "湖畔晨曦中的鱼获日志(1).png",
        "canonical_filename": "my_catches_v2.png",
    },
    "fish_guide_v2": {
        "source_filename": "晨雾湖畔鱼鉴草鱼卡片.png",
        "canonical_filename": "fish_guide_v2.png",
    },
}

EXPECTED_SUPPLEMENTAL = {
    "fish_memory_reference": {
        "source_filename": "千岛湖晨雾中的草鱼记忆.png",
        "canonical_filename": "supplemental/fish_memory_reference.png",
    },
    "fish_guide_unlit_state": {
        "source_filename": "雾山湖畔的未点亮鱼鉴.png",
        "canonical_filename": "supplemental/fish_guide_unlit_state.png",
    },
}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def decode_png(path: Path) -> tuple[int, int]:
    """Validate PNG chunk CRCs and inflate the supported source image data."""
    data = path.read_bytes()
    signature = b"\x89PNG\r\n\x1a\n"
    if not data.startswith(signature):
        raise ValueError("PNG signature is missing")

    position = len(signature)
    ihdr: tuple[int, int, int, int, int, int, int] | None = None
    idat_parts: list[bytes] = []
    saw_iend = False

    while position < len(data):
        if position + 12 > len(data):
            raise ValueError("truncated PNG chunk")
        length = struct.unpack(">I", data[position : position + 4])[0]
        chunk_type = data[position + 4 : position + 8]
        chunk_end = position + 8 + length
        if chunk_end + 4 > len(data):
            raise ValueError("truncated PNG chunk payload")
        payload = data[position + 8 : chunk_end]
        recorded_crc = struct.unpack(">I", data[chunk_end : chunk_end + 4])[0]
        calculated_crc = binascii.crc32(chunk_type)
        calculated_crc = binascii.crc32(payload, calculated_crc) & 0xFFFFFFFF
        if recorded_crc != calculated_crc:
            raise ValueError(f"CRC mismatch in {chunk_type.decode('ascii', 'replace')}")

        if chunk_type == b"IHDR":
            if ihdr is not None or length != 13:
                raise ValueError("invalid IHDR")
            ihdr = struct.unpack(">IIBBBBB", payload)
        elif chunk_type == b"IDAT":
            if ihdr is None:
                raise ValueError("IDAT appears before IHDR")
            idat_parts.append(payload)
        elif chunk_type == b"IEND":
            if length != 0:
                raise ValueError("invalid IEND")
            saw_iend = True
            position = chunk_end + 4
            break

        position = chunk_end + 4

    if ihdr is None:
        raise ValueError("IHDR is missing")
    if not idat_parts:
        raise ValueError("IDAT is missing")
    if not saw_iend:
        raise ValueError("IEND is missing")
    if position != len(data):
        raise ValueError("unexpected bytes after IEND")

    width, height, bit_depth, color_type, compression, filter_method, interlace = ihdr
    if width <= 0 or height <= 0:
        raise ValueError("invalid image dimensions")
    if bit_depth != 8 or color_type not in {0, 2, 4, 6}:
        raise ValueError("unsupported source PNG color encoding")
    if compression != 0 or filter_method != 0 or interlace != 0:
        raise ValueError("unsupported PNG compression, filter, or interlace mode")

    channels = {0: 1, 2: 3, 4: 2, 6: 4}[color_type]
    try:
        inflated = zlib.decompress(b"".join(idat_parts))
    except zlib.error as error:
        raise ValueError(f"PNG image data cannot be decoded: {error}") from error

    scanline_bytes = width * channels
    expected_length = height * (scanline_bytes + 1)
    if len(inflated) != expected_length:
        raise ValueError(
            f"decoded data length {len(inflated)} does not match expected {expected_length}"
        )
    for row in range(height):
        filter_type = inflated[row * (scanline_bytes + 1)]
        if filter_type > 4:
            raise ValueError(f"invalid PNG filter type {filter_type}")

    return width, height


def add_field_mismatches(
    errors: list[str], item: dict[str, Any], expected: dict[str, str], label: str
) -> None:
    for field, value in expected.items():
        if item.get(field) != value:
            errors.append(
                f"MISMATCH {label}: {field}={item.get(field)!r} expected={value!r}"
            )


def verify_reference(
    reference_dir: Path,
    item: dict[str, Any],
    expected: dict[str, str],
    kind: str,
) -> tuple[list[str], str | None]:
    label = expected["canonical_filename"]
    errors: list[str] = []
    add_field_mismatches(errors, item, expected, label)

    required_fields = ("width", "height", "bytes", "sha256", "source_validation")
    for field in required_fields:
        if field not in item:
            errors.append(f"MISSING {label}: manifest field {field}")

    if kind == "core":
        for field, expected_value in {
            "git_binary_status": "PRESENT_VERIFIED",
            "freeze_status": "FROZEN",
            "source_validation": "PASS",
        }.items():
            if item.get(field) != expected_value:
                errors.append(
                    f"MISMATCH {label}: {field}={item.get(field)!r} "
                    f"expected={expected_value!r}"
                )
    else:
        for field, expected_value in {
            "type": "SUPPLEMENTAL_REFERENCE",
            "source_validation": "PASS",
        }.items():
            if item.get(field) != expected_value:
                errors.append(
                    f"MISMATCH {label}: {field}={item.get(field)!r} "
                    f"expected={expected_value!r}"
                )

    path = reference_dir / label
    if not path.exists():
        errors.append(f"MISSING {label}")
        return errors, None

    try:
        width, height = decode_png(path)
    except (OSError, ValueError) as error:
        errors.append(f"INVALID {label}: {error}")
        return errors, None

    actual = {
        "sha256": sha256(path),
        "bytes": path.stat().st_size,
        "width": width,
        "height": height,
    }
    for field, actual_value in actual.items():
        expected_value = item.get(field)
        if actual_value != expected_value:
            errors.append(
                f"MISMATCH {label}: {field}={actual_value!r} expected={expected_value!r}"
            )

    return errors, actual["sha256"] if not errors else None


def items_by_id(items: Any, collection_name: str, failures: list[str]) -> dict[str, dict[str, Any]]:
    if not isinstance(items, list):
        failures.append(f"MISSING {collection_name}: expected a list")
        return {}
    result: dict[str, dict[str, Any]] = {}
    for item in items:
        if not isinstance(item, dict) or not isinstance(item.get("id"), str):
            failures.append(f"INVALID {collection_name}: item is missing string id")
            continue
        item_id = item["id"]
        if item_id in result:
            failures.append(f"INVALID {collection_name}: duplicate id {item_id}")
            continue
        result[item_id] = item
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--reference-dir",
        type=Path,
        default=DEFAULT_REFERENCE_DIR,
        help="Reference directory; useful for validating an unpacked source bundle.",
    )
    parser.add_argument(
        "--manifest",
        type=Path,
        help="Optional manifest path. Defaults to reference-dir/reference_manifest.json.",
    )
    args = parser.parse_args()

    reference_dir = args.reference_dir.resolve()
    manifest_path = (args.manifest or reference_dir / "reference_manifest.json").resolve()
    failures: list[str] = []

    try:
        data = json.loads(manifest_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        print(f"INVALID manifest: {error}", file=sys.stderr)
        return 1

    if data.get("status") != "FROZEN":
        failures.append(
            f"MISMATCH manifest status={data.get('status')!r} expected='FROZEN'"
        )
    if data.get("core_reference_count") != len(EXPECTED_CORE):
        failures.append("MISMATCH core_reference_count")
    if data.get("supplemental_reference_count") != len(EXPECTED_SUPPLEMENTAL):
        failures.append("MISMATCH supplemental_reference_count")

    freeze_gate = data.get("freeze_gate")
    if not isinstance(freeze_gate, dict):
        failures.append("MISSING freeze_gate")
    else:
        for field in (
            "present",
            "png_validation",
            "dimensions_recorded",
            "sha256_recorded",
            "byte_size_recorded",
        ):
            if freeze_gate.get(field) != "PASS":
                failures.append(f"MISMATCH freeze_gate.{field}")

    core_by_id = items_by_id(data.get("references"), "references", failures)
    supplemental_by_id = items_by_id(
        data.get("supplemental_references"), "supplemental_references", failures
    )
    if set(core_by_id) != set(EXPECTED_CORE):
        failures.append(
            f"MISMATCH core reference ids={sorted(core_by_id)} "
            f"expected={sorted(EXPECTED_CORE)}"
        )
    if set(supplemental_by_id) != set(EXPECTED_SUPPLEMENTAL):
        failures.append(
            f"MISMATCH supplemental reference ids={sorted(supplemental_by_id)} "
            f"expected={sorted(EXPECTED_SUPPLEMENTAL)}"
        )

    passed_core: list[str] = []
    passed_supplemental: list[str] = []
    for item_id, expected in EXPECTED_CORE.items():
        item = core_by_id.get(item_id)
        if item is None:
            continue
        item_failures, actual_sha = verify_reference(reference_dir, item, expected, "core")
        failures.extend(item_failures)
        if actual_sha:
            passed_core.append(expected["canonical_filename"])

    for item_id, expected in EXPECTED_SUPPLEMENTAL.items():
        item = supplemental_by_id.get(item_id)
        if item is None:
            continue
        item_failures, actual_sha = verify_reference(
            reference_dir, item, expected, "supplemental"
        )
        failures.extend(item_failures)
        if actual_sha:
            passed_supplemental.append(expected["canonical_filename"])

    if failures:
        for line in failures:
            print(line, file=sys.stderr)
        print(
            f"FAIL: {len(failures)} Core UI V1 reference verification issue(s)",
            file=sys.stderr,
        )
        return 1

    for filename in passed_core:
        print(f"PASS {filename}")
    for filename in passed_supplemental:
        print(f"PASS {filename}")
    print("PASS: all Core UI V1 reference PNGs are present and exact")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
