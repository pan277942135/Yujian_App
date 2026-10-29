#!/usr/bin/env python3
"""Build and verify the reproducible YuJian Core UI V1 source bundle."""

from __future__ import annotations

import argparse
import hashlib
import json
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path
from typing import Iterable


ROOT = Path(__file__).resolve().parents[1]
SYSTEM_DIR = ROOT / "design" / "system" / "core_visual_v1"
REFERENCE_DIR = SYSTEM_DIR / "reference"
VALIDATION_DIR = SYSTEM_DIR / "validation"
PAGES_DIR = ROOT / "design" / "pages"
VERIFIER = ROOT / "scripts" / "verify_core_ui_v1_references.py"
PACKAGE_NAME = "YuJian_Core_UI_V1_Source_Bundle_V1.0_FINAL.zip"
FIXED_ZIP_DATETIME = (1980, 1, 1, 0, 0, 0)


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def git_head() -> str:
    result = subprocess.run(
        ["git", "rev-parse", "HEAD"],
        cwd=ROOT,
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout.strip()


def require_file(path: Path) -> Path:
    if not path.is_file():
        raise FileNotFoundError(f"Required bundle source is missing: {path}")
    return path


def source_files() -> list[tuple[Path, str]]:
    """Return deterministic source-path to archive-path pairs."""
    entries: list[tuple[Path, str]] = [
        (REFERENCE_DIR / "README.md", "reference/README.md"),
        (REFERENCE_DIR / "reference_manifest.json", "reference/reference_manifest.json"),
        (REFERENCE_DIR / "empty_home_v2.png", "reference/empty_home_v2.png"),
        (REFERENCE_DIR / "normal_home_v1.png", "reference/normal_home_v1.png"),
        (REFERENCE_DIR / "recognition_result_v1.png", "reference/recognition_result_v1.png"),
        (REFERENCE_DIR / "fish_record_detail_v2.png", "reference/fish_record_detail_v2.png"),
        (REFERENCE_DIR / "my_catches_v2.png", "reference/my_catches_v2.png"),
        (REFERENCE_DIR / "fish_guide_v2.png", "reference/fish_guide_v2.png"),
        (
            REFERENCE_DIR / "supplemental" / "fish_memory_reference.png",
            "reference/supplemental/fish_memory_reference.png",
        ),
        (
            REFERENCE_DIR / "supplemental" / "fish_guide_unlit_state.png",
            "reference/supplemental/fish_guide_unlit_state.png",
        ),
        (SYSTEM_DIR / "README.md", "system/README.md"),
        (
            SYSTEM_DIR / "YuJian_Core_Visual_System_V1.md",
            "system/YuJian_Core_Visual_System_V1.md",
        ),
        (SYSTEM_DIR / "cross_page_rules.md", "system/cross_page_rules.md"),
        (SYSTEM_DIR / "shared_assets" / "README.md", "shared_assets/README.md"),
        (VERIFIER, "scripts/verify_core_ui_v1_references.py"),
    ]

    for path in sorted((SYSTEM_DIR / "tokens").glob("*.json")):
        entries.append((path, f"system/tokens/{path.name}"))
    for path in sorted((SYSTEM_DIR / "components").glob("*.md")):
        entries.append((path, f"components/{path.name}"))
    for path in sorted(VALIDATION_DIR.iterdir()):
        if path.is_file():
            entries.append((path, f"validation/{path.name}"))

    page_readmes = [
        (PAGES_DIR / "home" / "empty_home" / "README.md", "pages/home/empty_home/README.md"),
        (PAGES_DIR / "home" / "normal_home" / "README.md", "pages/home/normal_home/README.md"),
        (PAGES_DIR / "recognition" / "result" / "README.md", "pages/recognition/result/README.md"),
        (PAGES_DIR / "fish_record" / "detail" / "README.md", "pages/fish_record/detail/README.md"),
        (PAGES_DIR / "fish_records" / "list" / "README.md", "pages/fish_records/list/README.md"),
        (PAGES_DIR / "fish_guide" / "README.md", "pages/fish_guide/README.md"),
    ]
    entries.extend(page_readmes)

    verified_entries = [(require_file(path), archive_path) for path, archive_path in entries]
    archive_paths = [archive_path for _, archive_path in verified_entries]
    if len(archive_paths) != len(set(archive_paths)):
        raise ValueError("Duplicate archive path in source bundle input")
    return sorted(verified_entries, key=lambda item: item[1])


def payload_records(entries: Iterable[tuple[Path, str]]) -> list[dict[str, object]]:
    return [
        {
            "path": archive_path,
            "bytes": source_path.stat().st_size,
            "sha256": sha256_file(source_path),
        }
        for source_path, archive_path in entries
    ]


def payload_digest(records: Iterable[dict[str, object]]) -> str:
    digest = hashlib.sha256()
    for record in sorted(records, key=lambda item: str(item["path"])):
        line = (
            f"{record['path']}\x00{record['bytes']}\x00{record['sha256']}\n"
        ).encode("utf-8")
        digest.update(line)
    return digest.hexdigest()


def make_manifest(entries: list[tuple[Path, str]]) -> bytes:
    records = payload_records(entries)
    canonical_payload_sha = payload_digest(records)
    manifest = {
        "bundle_version": "V1.0_FINAL",
        "creation_source_commit": git_head(),
        "payload_file_count": len(records),
        "archive_file_count": len(records) + 1,
        "files": records,
        "bundle_sha256": {
            "algorithm": "SHA-256",
            "value": canonical_payload_sha,
            "scope": "Canonical source payload record digest",
            "outer_archive_sha256_sidecar": f"{PACKAGE_NAME}.sha256",
            "note": (
                "The final ZIP SHA-256 is written to the adjacent sidecar after the "
                "archive is created. A ZIP cannot embed its own final archive hash "
                "without a self-referential checksum cycle."
            ),
        },
        "archive_reproducibility": {
            "zip_member_order": "lexicographic archive paths; bundle_manifest.json last",
            "zip_member_timestamp": "1980-01-01T00:00:00",
            "compression": "ZIP_DEFLATED",
        },
    }
    return (json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + "\n").encode(
        "utf-8"
    )


def write_member(archive: zipfile.ZipFile, archive_path: str, data: bytes) -> None:
    info = zipfile.ZipInfo(archive_path, date_time=FIXED_ZIP_DATETIME)
    info.compress_type = zipfile.ZIP_DEFLATED
    info.external_attr = 0o100644 << 16
    archive.writestr(info, data)


def build_bundle(output: Path) -> tuple[bytes, list[tuple[Path, str]]]:
    entries = source_files()
    manifest_bytes = make_manifest(entries)
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(
        output,
        mode="w",
        compression=zipfile.ZIP_DEFLATED,
        compresslevel=9,
        strict_timestamps=False,
    ) as archive:
        for source_path, archive_path in entries:
            write_member(archive, archive_path, source_path.read_bytes())
        write_member(archive, "bundle_manifest.json", manifest_bytes)
    return manifest_bytes, entries


def verify_sidecar(output: Path, expected_sha: str) -> None:
    sidecar = output.with_name(f"{output.name}.sha256")
    expected_line = f"{expected_sha}  {output.name}\n"
    if sidecar.read_text(encoding="utf-8") != expected_line:
        raise ValueError("Bundle SHA-256 sidecar does not match the final archive")


def verify_bundle(output: Path) -> tuple[str, int, int]:
    actual_archive_sha = sha256_file(output)
    verify_sidecar(output, actual_archive_sha)

    with zipfile.ZipFile(output) as archive:
        corrupt_member = archive.testzip()
        if corrupt_member is not None:
            raise ValueError(f"ZIP CRC validation failed for {corrupt_member}")
        names = archive.namelist()
        if "bundle_manifest.json" not in names:
            raise ValueError("bundle_manifest.json is missing from the archive")
        manifest = json.loads(archive.read("bundle_manifest.json").decode("utf-8"))
        records = manifest.get("files")
        if not isinstance(records, list):
            raise ValueError("Bundle manifest files list is invalid")
        if manifest.get("payload_file_count") != len(records):
            raise ValueError("Bundle manifest payload_file_count is invalid")
        if manifest.get("archive_file_count") != len(names):
            raise ValueError("Bundle manifest archive_file_count is invalid")
        if len(names) != len(records) + 1:
            raise ValueError("Bundle archive contains unexpected members")
        if names[-1] != "bundle_manifest.json":
            raise ValueError("bundle_manifest.json must be the final archive member")
        if manifest.get("bundle_sha256", {}).get("value") != payload_digest(records):
            raise ValueError("Bundle manifest canonical payload digest is invalid")

        with tempfile.TemporaryDirectory(prefix="yujian-core-ui-v1-") as temporary:
            destination = Path(temporary)
            archive.extractall(destination)
            for record in records:
                if not isinstance(record, dict):
                    raise ValueError("Bundle manifest includes an invalid file record")
                archive_path = record.get("path")
                if not isinstance(archive_path, str) or archive_path.startswith("/"):
                    raise ValueError("Bundle manifest includes an invalid archive path")
                extracted = destination / archive_path
                if not extracted.is_file():
                    raise ValueError(f"Bundle payload is missing {archive_path}")
                if extracted.stat().st_size != record.get("bytes"):
                    raise ValueError(f"Bundle payload byte size mismatch for {archive_path}")
                if sha256_file(extracted) != record.get("sha256"):
                    raise ValueError(f"Bundle payload SHA-256 mismatch for {archive_path}")

            verifier = destination / "scripts" / "verify_core_ui_v1_references.py"
            result = subprocess.run(
                [
                    sys.executable,
                    str(verifier),
                    "--reference-dir",
                    str(destination / "reference"),
                ],
                check=False,
                capture_output=True,
                text=True,
            )
            if result.returncode != 0:
                detail = result.stdout + result.stderr
                raise ValueError(f"Unpacked reference verifier failed:\n{detail}")

    return actual_archive_sha, output.stat().st_size, len(names)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--output",
        type=Path,
        default=ROOT / "build" / "design" / PACKAGE_NAME,
        help="Output ZIP path. The ZIP itself is generated evidence and is not committed.",
    )
    args = parser.parse_args()
    output = args.output if args.output.is_absolute() else ROOT / args.output
    output = output.resolve()

    try:
        build_bundle(output)
        initial_archive_sha = sha256_file(output)
        sidecar = output.with_name(f"{output.name}.sha256")
        sidecar.write_text(
            f"{initial_archive_sha}  {output.name}\n", encoding="utf-8"
        )
        archive_sha, size, file_count = verify_bundle(output)
    except (FileNotFoundError, OSError, ValueError, subprocess.CalledProcessError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        return 1

    print("PASS source bundle payload validation")
    print("PASS unpacked Core UI V1 reference verifier")
    print("PASS source bundle generated")
    print(f"Filename: {output.name}")
    print(f"Size: {size}")
    print(f"SHA-256: {archive_sha}")
    print(f"File count: {file_count}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
