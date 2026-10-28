#!/usr/bin/env python3
"""Verify the delivered NH01/P01-P03 extraction package and runtime boundary."""
from __future__ import annotations

import hashlib
import io
import json
import math
import re
import struct
import sys
import zipfile
from collections import Counter
from pathlib import Path, PurePosixPath

ROOT = Path(__file__).resolve().parents[1]
PACKAGE_DIR = ROOT / "design/resource_packages"
ARCHIVE = PACKAGE_DIR / "Yujian_NH01_P01-P03_Extracted_Resources_V1.zip"
PARTS_DIR = PACKAGE_DIR / "Yujian_NH01_P01-P03_Extracted_Resources_V1.zip.parts"
ARCHIVE_BYTES = 20558110
PART_BYTES = 511998
PART_COUNT = math.ceil(ARCHIVE_BYTES / PART_BYTES)
EXPECTED_ARCHIVE_SHA256 = "45b7d796e9eb8e9162eae111212afbd02d7e8395b9507965b49bcb3a04387689"
SOURCE_PATHS = {
    "home": ROOT / "design/system/core_visual_v1/reference/normal_home_v1.png",
    "P01": ROOT / "design/pages/recognition/first_use_camera_guide/frozen/P01_First_Use_Camera_Guide_Frozen.png",
    "P02": ROOT / "design/pages/recognition/first_use_camera_guide/frozen/P02_First_Use_Camera_Guide_Frozen.png",
    "P03": ROOT / "design/pages/recognition/first_use_camera_guide/frozen/P03_First_Use_Camera_Guide_Frozen.png",
}
KNOWN_STATUSES = {
    "REFERENCE_ONLY",
    "VECTOR_STRUCTURE_NOT_FROZEN",
    "REVIEW_ONLY",
    "VECTOR_COMPONENT",
}
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")
TEXT_SUFFIXES = {".kt", ".java", ".xml", ".gradle", ".kts", ".json", ".properties"}


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def safe_package_path(name: str) -> bool:
    path = PurePosixPath(name)
    return not path.is_absolute() and ".." not in path.parts and bool(path.parts)


def parse_sums(raw: str) -> dict[str, str]:
    result: dict[str, str] = {}
    for line_number, line in enumerate(raw.splitlines(), start=1):
        if not line.strip():
            continue
        parts = line.split("  ", 1)
        if len(parts) != 2 or not SHA256_RE.fullmatch(parts[0]):
            raise ValueError("invalid SHA256SUMS.txt line " + str(line_number))
        name = parts[1]
        if not safe_package_path(name):
            raise ValueError("unsafe path in SHA256SUMS.txt: " + name)
        if name in result:
            raise ValueError("duplicate SHA256SUMS.txt path: " + name)
        result[name] = parts[0]
    return result


def read_archive_bytes() -> bytes:
    """Read the exact archive from its source file or numbered binary parts."""
    part_paths = sorted(PARTS_DIR.glob("*.part")) if PARTS_DIR.exists() else []
    if part_paths:
        expected_names = [format(index, "04d") + ".part" for index in range(PART_COUNT)]
        actual_names = [path.name for path in part_paths]
        if actual_names != expected_names:
            raise ValueError("resource package parts are missing, duplicated, or out of order")
        parts = []
        for index, path in enumerate(part_paths):
            expected_size = min(PART_BYTES, ARCHIVE_BYTES - index * PART_BYTES)
            data = path.read_bytes()
            if len(data) != expected_size:
                raise ValueError("resource package part has the wrong byte count: " + path.name)
            parts.append(data)
        assembled = b"".join(parts)
        if len(assembled) != ARCHIVE_BYTES:
            raise ValueError("assembled resource package has the wrong byte count")
        if ARCHIVE.is_file() and ARCHIVE.read_bytes() != assembled:
            raise ValueError("resource package file differs from its numbered parts")
        return assembled
    if ARCHIVE.is_file():
        return ARCHIVE.read_bytes()
    raise FileNotFoundError("resource package ZIP or numbered parts are missing")


def verify_runtime_boundary(assets: list[dict], errors: list[str]) -> None:
    blocked = [asset for asset in assets if asset.get("status") != "PRODUCTION_READY"]
    resource_root = ROOT / "app/src/main/res"
    source_root = ROOT / "app/src/main"
    resource_files = [path for path in resource_root.rglob("*") if path.is_file()] if resource_root.exists() else []
    text_sources = [
        path for path in source_root.rglob("*")
        if path.is_file() and path.suffix.lower() in TEXT_SUFFIXES
    ] if source_root.exists() else []
    source_text = []
    for path in text_sources:
        try:
            source_text.append((path, path.read_text(encoding="utf-8", errors="ignore").lower()))
        except OSError as exc:
            errors.append("cannot read Android source " + str(path.relative_to(ROOT)) + ": " + str(exc))

    blocked_digests = set()
    for asset in blocked:
        path = asset.get("path")
        if not isinstance(path, str) or not safe_package_path(path):
            continue
        basename = PurePosixPath(path).name.lower()
        stem = PurePosixPath(path).stem.lower()
        for resource in resource_files:
            if resource.name.lower() == basename or resource.stem.lower() == stem:
                errors.append(
                    "non-production extraction asset is wired under app/src/main/res: "
                    + str(resource.relative_to(ROOT))
                )
        blocked_digests.add(asset.get("sha256"))
        for source, content in source_text:
            if basename in content:
                errors.append(
                    "Android source references non-production extraction asset "
                    + basename + " in " + str(source.relative_to(ROOT))
                )

    for resource in resource_files:
        try:
            if digest(resource.read_bytes()) in blocked_digests:
                errors.append(
                    "byte-identical non-production extraction asset is wired under app/src/main/res: "
                    + str(resource.relative_to(ROOT))
                )
        except OSError as exc:
            errors.append("cannot verify Android resource " + str(resource.relative_to(ROOT)) + ": " + str(exc))


def main() -> int:
    errors: list[str] = []
    try:
        archive_bytes = read_archive_bytes()
    except (OSError, ValueError) as exc:
        print("YUJIAN_RESOURCE_PACKAGE_VALIDATION=FAIL")
        print("cannot read package archive: " + str(exc), file=sys.stderr)
        return 1

    archive_sha = digest(archive_bytes)
    if archive_sha != EXPECTED_ARCHIVE_SHA256:
        errors.append("archive SHA-256 mismatch: " + archive_sha)

    assets: list[dict] = []
    try:
        with zipfile.ZipFile(io.BytesIO(archive_bytes)) as archive:
            bad_member = archive.testzip()
            if bad_member:
                errors.append("ZIP CRC check failed for " + bad_member)
            names = [name for name in archive.namelist() if not name.endswith("/")]
            if len(names) != len(set(names)):
                errors.append("ZIP contains duplicate file paths")
            for name in names:
                if not safe_package_path(name):
                    errors.append("unsafe ZIP path: " + name)
            embedded_manifest = archive.read("MANIFEST.json")
            embedded_sums = archive.read("SHA256SUMS.txt")
            if (PACKAGE_DIR / "MANIFEST.json").read_bytes() != embedded_manifest:
                errors.append("sidecar MANIFEST.json differs from the embedded manifest")
            if (PACKAGE_DIR / "SHA256SUMS.txt").read_bytes() != embedded_sums:
                errors.append("sidecar SHA256SUMS.txt differs from the embedded checksums")

            manifest = json.loads(embedded_manifest)
            sums = parse_sums(embedded_sums.decode("utf-8"))
            archive_files = set(names) - {"SHA256SUMS.txt"}
            if set(sums) != archive_files:
                errors.append("SHA256SUMS.txt entries do not match ZIP file entries")
            for name, expected in sums.items():
                if name not in names:
                    errors.append("checksummed file missing from ZIP: " + name)
                    continue
                if digest(archive.read(name)) != expected:
                    errors.append("checksummed file digest mismatch: " + name)

            raw_assets = manifest.get("assets")
            if not isinstance(raw_assets, list):
                errors.append("manifest assets must be an array")
                raw_assets = []
            seen_assets = set()
            for asset in raw_assets:
                if not isinstance(asset, dict):
                    errors.append("manifest asset entry must be an object")
                    continue
                path = asset.get("path")
                if not isinstance(path, str) or not safe_package_path(path):
                    errors.append("manifest contains an unsafe asset path")
                    continue
                if path in seen_assets:
                    errors.append("duplicate manifest asset path: " + path)
                seen_assets.add(path)
                if asset.get("status") not in KNOWN_STATUSES | {"PRODUCTION_READY"}:
                    errors.append("unknown asset status for " + path + ": " + str(asset.get("status")))
                if path not in sums or asset.get("sha256") != sums.get(path):
                    errors.append("manifest/SHA256SUMS mismatch for asset: " + path)
                if path in names and digest(archive.read(path)) != asset.get("sha256"):
                    errors.append("manifest asset digest mismatch: " + path)
                if path in names and len(archive.read(path)) != asset.get("bytes"):
                    errors.append("manifest asset byte count mismatch: " + path)
                assets.append(asset)

            source_files = manifest.get("source_files")
            if not isinstance(source_files, dict) or set(source_files) != set(SOURCE_PATHS):
                errors.append("manifest source_files must bind home and P01-P03")
                source_files = {}
            for source_id, repository_path in SOURCE_PATHS.items():
                record = source_files.get(source_id)
                if not isinstance(record, dict):
                    errors.append("missing frozen source record: " + source_id)
                    continue
                authority_path = record.get("authority")
                if not isinstance(authority_path, str) or authority_path not in names:
                    errors.append("source authority missing from ZIP: " + source_id)
                    continue
                source_bytes = archive.read(authority_path)
                actual_sha = digest(source_bytes)
                if actual_sha != record.get("sha256") or actual_sha != sums.get(authority_path):
                    errors.append("frozen source digest mismatch: " + source_id)
                if len(source_bytes) != record.get("bytes"):
                    errors.append("frozen source byte count mismatch: " + source_id)
                if len(source_bytes) < 24 or source_bytes[:8] != b"\x89PNG\r\n\x1a\n":
                    errors.append("frozen source is not a PNG: " + source_id)
                else:
                    dimensions = list(struct.unpack(">II", source_bytes[16:24]))
                    if dimensions != record.get("dimensions"):
                        errors.append("frozen source dimensions mismatch: " + source_id)
                if not repository_path.is_file():
                    errors.append("repository source PNG missing: " + str(repository_path.relative_to(ROOT)))
                elif digest(repository_path.read_bytes()) != actual_sha:
                    errors.append("repository frozen source differs from package: " + source_id)
    except (OSError, KeyError, ValueError, zipfile.BadZipFile) as exc:
        errors.append("package inspection failed: " + str(exc))

    verify_runtime_boundary(assets, errors)
    if errors:
        print("YUJIAN_RESOURCE_PACKAGE_VALIDATION=FAIL")
        for error in errors:
            print("- " + error, file=sys.stderr)
        return 1

    statuses = Counter(asset.get("status") for asset in assets)
    print("YUJIAN_RESOURCE_PACKAGE_VALIDATION=PASS")
    print("archive_sha256=" + archive_sha)
    print("asset_count=" + str(len(assets)))
    print("asset_status_counts=" + ", ".join(key + ":" + str(value) for key, value in sorted(statuses.items())))
    print("frozen_source_count=" + str(len(SOURCE_PATHS)))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
