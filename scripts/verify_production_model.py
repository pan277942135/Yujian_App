#!/usr/bin/env python3
"""Strictly verify the production Release assets packaged for this Android build."""

from __future__ import annotations

import json
import sys
from pathlib import Path

from production_model_contract import (
    ANDROID_CLASS_MAP_FILE,
    ANDROID_METADATA_FILE,
    ANDROID_MODEL_FILE,
    ANDROID_SNAPSHOT_FILE,
    ANDROID_TENSOR_CONTRACT_FILE,
    ModelContractError,
    snapshot_summary,
    verify_model_snapshot,
)

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets"


def verify_asset_directory(assets: Path = ASSETS) -> dict:
    paths = {
        "model": assets / ANDROID_MODEL_FILE,
        "metadata": assets / ANDROID_METADATA_FILE,
        "class_map": assets / ANDROID_CLASS_MAP_FILE,
        "tensor_contract": assets / ANDROID_TENSOR_CONTRACT_FILE,
        "snapshot": assets / ANDROID_SNAPSHOT_FILE,
    }
    for label, path in paths.items():
        if not path.is_file() or path.stat().st_size == 0:
            raise ModelContractError(f"missing or empty packaged {label}: {path}")
    try:
        snapshot = json.loads(paths["snapshot"].read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise ModelContractError(f"invalid model_release_contract.json: {error}") from error
    if not isinstance(snapshot, dict):
        raise ModelContractError("model_release_contract.json must contain a JSON object")
    return verify_model_snapshot(
        model=paths["model"].read_bytes(),
        metadata_bytes=paths["metadata"].read_bytes(),
        class_map_bytes=paths["class_map"].read_bytes(),
        tensor_contract_bytes=paths["tensor_contract"].read_bytes(),
        snapshot=snapshot,
    )


def main() -> int:
    try:
        snapshot = verify_asset_directory()
    except (OSError, ModelContractError) as error:
        print(f"PRODUCTION_MODEL_VERIFY_FAILED: {error}", file=sys.stderr)
        return 1

    summary = snapshot_summary(snapshot)
    print("PRODUCTION_MODEL_VERIFY_OK")
    for key, value in summary.items():
        print(f"{key}={json.dumps(value, ensure_ascii=False) if isinstance(value, (list, dict)) else value}")
    print(f"model_snapshot={ANDROID_SNAPSHOT_FILE}")
    print(f"class_order={','.join(snapshot['class_order'])}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
