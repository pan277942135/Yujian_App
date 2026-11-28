#!/usr/bin/env python3
"""Refresh evidence checksums and create a verified API 36 evidence archive."""

from __future__ import annotations

import argparse
import hashlib
import os
import pathlib
import tempfile
import zipfile


def _sha256(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def package_evidence(root: pathlib.Path, output: pathlib.Path) -> tuple[int, str]:
    root = root.resolve()
    output = output.resolve()
    root.mkdir(parents=True, exist_ok=True)
    output.parent.mkdir(parents=True, exist_ok=True)
    if output == root or root in output.parents:
        raise ValueError("evidence archive must be outside its source directory")

    sums_path = root / "evidence-sha256sums.txt"
    evidence_files = sorted(
        path for path in root.rglob("*")
        if path.is_file() and path != sums_path
    )
    rows = [f"{_sha256(path)}  {path.relative_to(root).as_posix()}\n" for path in evidence_files]
    with tempfile.NamedTemporaryFile(
        mode="w", encoding="utf-8", dir=root.parent, prefix="evidence-sha256sums-", suffix=".tmp", delete=False
    ) as temporary:
        temporary.write("".join(rows))
        temporary_path = pathlib.Path(temporary.name)
    os.replace(temporary_path, sums_path)

    for line in sums_path.read_text(encoding="utf-8").splitlines():
        digest, relative = line.split("  ", 1)
        relative_path = pathlib.PurePosixPath(relative)
        if relative_path.is_absolute() or ".." in relative_path.parts:
            raise ValueError(f"unsafe evidence checksum path: {relative}")
        path = root.joinpath(*relative_path.parts)
        if not path.is_file() or _sha256(path) != digest:
            raise ValueError(f"evidence checksum mismatch: {relative}")

    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(root.rglob("*")):
            if path.is_file():
                archive.write(path, path.relative_to(root).as_posix())
    with zipfile.ZipFile(output) as archive:
        if archive.testzip() is not None:
            raise ValueError("evidence ZIP integrity check failed")
        members = set(archive.namelist())
        if "evidence-sha256sums.txt" not in members:
            raise ValueError("evidence ZIP is missing evidence-sha256sums.txt")

    digest = _sha256(output)
    sidecar = output.with_suffix(output.suffix + ".sha256")
    sidecar.write_text(f"{digest}  {output.name}\n", encoding="utf-8")
    if _sha256(output) != digest or sidecar.read_text(encoding="utf-8") != f"{digest}  {output.name}\n":
        raise ValueError("evidence ZIP SHA256 sidecar verification failed")
    return output.stat().st_size, digest


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", required=True, type=pathlib.Path)
    parser.add_argument("--output", required=True, type=pathlib.Path)
    args = parser.parse_args()
    size, digest = package_evidence(args.root, args.output)
    print(f"API36_EVIDENCE_ZIP={args.output} bytes={size} sha256={digest}")
    print(f"API36_EVIDENCE_SHA256_SIDECAR={args.output}.sha256")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
