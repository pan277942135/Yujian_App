#!/usr/bin/env python3
"""Extract verified production-model provenance from the actual packaged APK."""

from __future__ import annotations

import argparse
import json
import sys
import zipfile
from pathlib import Path

from production_model_contract import (
    ANDROID_CLASS_MAP_FILE,
    ANDROID_METADATA_FILE,
    ANDROID_MODEL_FILE,
    ANDROID_SNAPSHOT_FILE,
    ANDROID_TENSOR_CONTRACT_FILE,
    ModelContractError,
    sha256_bytes,
    snapshot_summary,
    verify_model_snapshot,
)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--build-sha", required=True)
    args = parser.parse_args()
    if not args.apk.is_file() or args.apk.stat().st_size == 0:
        raise SystemExit(f"APK_MODEL_TRACE_FAILED: missing APK {args.apk}")

    try:
        with zipfile.ZipFile(args.apk) as archive:
            model = archive.read(f"assets/{ANDROID_MODEL_FILE}")
            metadata = archive.read(f"assets/{ANDROID_METADATA_FILE}")
            class_map = archive.read(f"assets/{ANDROID_CLASS_MAP_FILE}")
            tensor_contract = archive.read(f"assets/{ANDROID_TENSOR_CONTRACT_FILE}")
            snapshot_bytes = archive.read(f"assets/{ANDROID_SNAPSHOT_FILE}")
        snapshot = json.loads(snapshot_bytes.decode("utf-8"))
        verified = verify_model_snapshot(
            model=model,
            metadata_bytes=metadata,
            class_map_bytes=class_map,
            tensor_contract_bytes=tensor_contract,
            snapshot=snapshot,
        )
    except (OSError, KeyError, zipfile.BadZipFile, UnicodeDecodeError, json.JSONDecodeError, ModelContractError) as error:
        raise SystemExit(f"APK_MODEL_TRACE_FAILED: {error}") from error

    if verified.get("build_sha") and verified["build_sha"] != args.build_sha:
        raise SystemExit("APK_MODEL_TRACE_FAILED: packaged model snapshot belongs to a different app commit")
    args.output_dir.mkdir(parents=True, exist_ok=True)
    (args.output_dir / ANDROID_SNAPSHOT_FILE).write_bytes(snapshot_bytes)
    trace = {
        "build_sha": args.build_sha,
        "apk_sha256": sha256_bytes(args.apk.read_bytes()),
        **snapshot_summary(verified),
        "MODEL_RELEASE_TAG": verified["release_tag"],
        "MODEL_ID": verified["model_id"],
        "DATASET_ID": verified["dataset_id"],
        "MODEL_SHA256": verified["sha256"],
        "MODEL_BYTES": verified["bytes"],
        "MODEL_CLASS_COUNT": verified["class_count"],
        "CLASS_MAP_SHA256": verified["class_map_sha256"],
        "TENSOR_CONTRACT_SHA256": verified["tensor_contract_sha256"],
        "model_release_contract_sha256": sha256_bytes(snapshot_bytes),
    }
    trace_path = args.output_dir / "apk_model_trace.json"
    trace_path.write_text(json.dumps(trace, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print("APK_MODEL_TRACE_PASS")
    print(json.dumps(trace, ensure_ascii=False, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    raise SystemExit(main())
