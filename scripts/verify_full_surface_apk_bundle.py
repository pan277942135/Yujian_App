#!/usr/bin/env python3
"""Verify a build-once Full-Surface APK plus its exact source/model provenance."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--bundle-dir", required=True, type=Path)
    parser.add_argument("--build-sha", required=True)
    parser.add_argument("--model-id", required=True)
    args = parser.parse_args()

    bundle = args.bundle_dir
    apk_files = list(bundle.glob("*.apk"))
    if len(apk_files) != 1:
        raise SystemExit(f"FULL_SURFACE_APK_BUNDLE_FAILED: expected one APK, found {len(apk_files)}")
    apk = apk_files[0]
    if not apk.is_file() or apk.stat().st_size == 0:
        raise SystemExit("FULL_SURFACE_APK_BUNDLE_FAILED: APK is empty")

    expected_name = f"YuJian_Full_Surface_Android_{args.build_sha[:12]}_{args.model_id}.apk"
    if apk.name != expected_name:
        raise SystemExit(f"FULL_SURFACE_APK_BUNDLE_FAILED: unexpected filename {apk.name!r}")

    contract_path = bundle / "model_release_contract.json"
    trace_path = bundle / "apk_model_trace.json"
    sums_path = bundle / "SHA256SUMS"
    for required in (contract_path, trace_path, sums_path):
        if not required.is_file() or required.stat().st_size == 0:
            raise SystemExit(f"FULL_SURFACE_APK_BUNDLE_FAILED: missing {required.name}")

    contract = json.loads(contract_path.read_text(encoding="utf-8"))
    trace = json.loads(trace_path.read_text(encoding="utf-8"))
    if contract.get("model_id") != args.model_id or trace.get("model_id") != args.model_id:
        raise SystemExit("FULL_SURFACE_APK_BUNDLE_FAILED: model id does not match the bundle name")
    if trace.get("build_sha") != args.build_sha:
        raise SystemExit("FULL_SURFACE_APK_BUNDLE_FAILED: APK trace belongs to a different source commit")
    if trace.get("apk_sha256") != sha256(apk):
        raise SystemExit("FULL_SURFACE_APK_BUNDLE_FAILED: APK digest does not match its embedded trace")

    expected_hashes = {
        apk.name: sha256(apk),
        contract_path.name: sha256(contract_path),
        trace_path.name: sha256(trace_path),
    }
    listed_hashes = {}
    for line in sums_path.read_text(encoding="utf-8").splitlines():
        fields = line.split(maxsplit=1)
        if len(fields) != 2:
            raise SystemExit("FULL_SURFACE_APK_BUNDLE_FAILED: malformed SHA256SUMS entry")
        listed_hashes[fields[1].lstrip("* ")] = fields[0]
    if listed_hashes != expected_hashes:
        raise SystemExit("FULL_SURFACE_APK_BUNDLE_FAILED: SHA256SUMS does not match the bundle contents")

    expected_files = set(expected_hashes) | {sums_path.name}
    actual_files = {path.name for path in bundle.iterdir() if path.is_file()}
    if actual_files != expected_files:
        raise SystemExit("FULL_SURFACE_APK_BUNDLE_FAILED: bundle contains unexpected or missing files")

    print("FULL_SURFACE_APK_BUNDLE_PASS")
    print(json.dumps({
        "build_sha": args.build_sha,
        "apk": apk.name,
        "apk_sha256": expected_hashes[apk.name],
        "model_id": args.model_id,
    }, ensure_ascii=False, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
