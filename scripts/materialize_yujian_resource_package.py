#!/usr/bin/env python3
"""Reassemble the verified NH01/P01-P03 resource ZIP from its numbered parts."""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

from verify_yujian_resource_package import EXPECTED_ARCHIVE_SHA256, digest, read_archive_bytes


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--output",
        type=Path,
        required=True,
        help="destination path for the reconstructed ZIP",
    )
    args = parser.parse_args()
    try:
        archive = read_archive_bytes()
    except (OSError, ValueError) as exc:
        print("RESOURCE_PACKAGE_MATERIALIZATION=FAIL: " + str(exc), file=sys.stderr)
        return 1
    if digest(archive) != EXPECTED_ARCHIVE_SHA256:
        print("RESOURCE_PACKAGE_MATERIALIZATION=FAIL: archive SHA-256 mismatch", file=sys.stderr)
        return 1
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_bytes(archive)
    print("RESOURCE_PACKAGE_MATERIALIZATION=PASS")
    print("output=" + str(args.output.resolve()))
    print("sha256=" + EXPECTED_ARCHIVE_SHA256)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
