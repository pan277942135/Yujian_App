#!/usr/bin/env python3
"""Shared strict validation for the mutable Android production model release."""

from __future__ import annotations

import hashlib
import json
import re
from typing import Any


RELEASE_REPOSITORY = "pan277942135/Yujian"
RELEASE_TAG = "mobile-model-v0.2"
RELEASE_SOURCE = f"https://github.com/{RELEASE_REPOSITORY}/releases/tag/{RELEASE_TAG}"
REQUIRED_ASSETS = (
    "fish_classifier_v0_2.tflite",
    "fish_classifier_v0_2.metadata.json",
    "class_map.json",
    "tensor_contract.json",
)
ANDROID_MODEL_FILE = "fish_classifier.tflite"
ANDROID_METADATA_FILE = "fish_classifier_v0_2.metadata.json"
ANDROID_CLASS_MAP_FILE = "class_map.json"
ANDROID_TENSOR_CONTRACT_FILE = "tensor_contract.json"
ANDROID_SNAPSHOT_FILE = "model_release_contract.json"


class ModelContractError(ValueError):
    pass


def sha256_bytes(payload: bytes) -> str:
    return hashlib.sha256(payload).hexdigest()


def _json_object(payload: bytes, label: str) -> dict[str, Any]:
    try:
        value = json.loads(payload.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise ModelContractError(f"{label} is not valid UTF-8 JSON: {error}") from error
    if not isinstance(value, dict):
        raise ModelContractError(f"{label} must be a JSON object")
    return value


def _shape(value: Any, label: str) -> list[int]:
    if not isinstance(value, list) or not value:
        raise ModelContractError(f"{label} must be a non-empty shape array")
    if any(not isinstance(item, int) or isinstance(item, bool) or item <= 0 for item in value):
        raise ModelContractError(f"{label} must contain positive integer dimensions: {value!r}")
    return value


def _check_tflite_header(model: bytes) -> None:
    if len(model) < 8 or model[4:8] != b"TFL3":
        raise ModelContractError(f"invalid TFLite FlatBuffer identifier: {model[:8]!r}")
    root_offset = int.from_bytes(model[:4], "little", signed=False)
    if root_offset < 8 or root_offset >= len(model):
        raise ModelContractError(f"invalid TFLite root table offset: {root_offset} for {len(model)} bytes")


def _release_asset_records(release: dict[str, Any]) -> dict[str, dict[str, Any]]:
    release_id = release.get("id")
    if not isinstance(release_id, int) or release_id <= 0:
        raise ModelContractError("production release has no valid release id")
    if release.get("tag_name") != RELEASE_TAG:
        raise ModelContractError(f"unexpected production release tag: {release.get('tag_name')!r}")
    raw_assets = release.get("assets")
    if not isinstance(raw_assets, list):
        raise ModelContractError("production release has no asset list")
    by_name: dict[str, dict[str, Any]] = {}
    for asset in raw_assets:
        if not isinstance(asset, dict):
            continue
        name = asset.get("name")
        if name in REQUIRED_ASSETS:
            if name in by_name:
                raise ModelContractError(f"duplicate production release asset: {name}")
            asset_id = asset.get("id")
            size = asset.get("size")
            url = asset.get("url")
            if not isinstance(asset_id, int) or asset_id <= 0:
                raise ModelContractError(f"invalid asset id for {name}")
            if not isinstance(size, int) or size <= 0:
                raise ModelContractError(f"invalid asset size for {name}")
            if not isinstance(url, str) or not url.startswith("https://api.github.com/repos/"):
                raise ModelContractError(f"invalid API download URL for {name}")
            if asset.get("state") not in (None, "uploaded"):
                raise ModelContractError(f"production asset is not uploaded: {name}")
            by_name[name] = {
                "name": name,
                "id": asset_id,
                "size": size,
                "digest": asset.get("digest"),
                "updated_at": asset.get("updated_at"),
                "state": asset.get("state") or "uploaded",
                "api_url": url,
                "release_id": release_id,
            }
    missing = sorted(set(REQUIRED_ASSETS) - set(by_name))
    if missing:
        raise ModelContractError(f"production release missing required assets: {missing}")
    return by_name


def _verify_release_asset_bytes(name: str, payload: bytes, record: dict[str, Any]) -> None:
    if len(payload) != record["size"]:
        raise ModelContractError(
            f"{name} byte count differs from the Release asset record: {len(payload)} != {record['size']}"
        )
    digest = record.get("digest")
    if digest:
        if not isinstance(digest, str) or not digest.startswith("sha256:"):
            raise ModelContractError(f"unsupported GitHub Release digest for {name}: {digest!r}")
        actual = sha256_bytes(payload)
        if actual != digest.removeprefix("sha256:"):
            raise ModelContractError(f"GitHub Release digest mismatch for {name}: {actual} != {digest}")


def build_model_snapshot(
    *,
    model: bytes,
    metadata_bytes: bytes,
    class_map_bytes: bytes,
    tensor_contract_bytes: bytes,
    release: dict[str, Any],
    build_sha: str | None = None,
) -> dict[str, Any]:
    """Validate all four official assets and return the exact packaged trace."""
    _check_tflite_header(model)
    metadata = _json_object(metadata_bytes, "model metadata")
    class_map = _json_object(class_map_bytes, "class_map.json")
    tensor_contract = _json_object(tensor_contract_bytes, "tensor_contract.json")
    release_assets = _release_asset_records(release)

    for name, payload in (
        (REQUIRED_ASSETS[0], model),
        (REQUIRED_ASSETS[1], metadata_bytes),
        (REQUIRED_ASSETS[2], class_map_bytes),
        (REQUIRED_ASSETS[3], tensor_contract_bytes),
    ):
        _verify_release_asset_bytes(name, payload, release_assets[name])

    actual_model_sha = sha256_bytes(model)
    expected_model_sha = metadata.get("sha256")
    if not isinstance(expected_model_sha, str) or not re.fullmatch(r"[0-9a-f]{64}", expected_model_sha):
        raise ModelContractError("model metadata sha256 is missing or invalid")
    if actual_model_sha != expected_model_sha:
        raise ModelContractError(f"metadata/model SHA mismatch: {expected_model_sha} != {actual_model_sha}")

    classes_raw = class_map.get("classes")
    if not isinstance(classes_raw, list) or not classes_raw:
        raise ModelContractError("class_map.classes must be a non-empty array")
    try:
        classes = sorted(classes_raw, key=lambda row: int(row["class_index"]))
        normalized_classes = [
            {
                "class_index": int(row["class_index"]),
                "species_key": str(row["species_key"]).strip(),
                "display_name": str(row.get("common_name_zh") or row.get("species_key") or "").strip(),
            }
            for row in classes
        ]
    except (KeyError, TypeError, ValueError) as error:
        raise ModelContractError(f"invalid class map entry: {error}") from error
    count = len(normalized_classes)
    if [row["class_index"] for row in normalized_classes] != list(range(count)):
        raise ModelContractError("class_map class_index values must be contiguous from zero")
    if any(not row["species_key"] or not row["display_name"] for row in normalized_classes):
        raise ModelContractError("class_map entries require species_key and Chinese display name")
    class_order = [row["species_key"] for row in normalized_classes]
    if len(set(class_order)) != count:
        raise ModelContractError("class_map contains duplicate species_key values")

    model_id = metadata.get("model_id")
    dataset_id = metadata.get("dataset_id")
    if not isinstance(model_id, str) or not model_id.strip():
        raise ModelContractError("model metadata model_id is required")
    if not isinstance(dataset_id, str) or not dataset_id.strip():
        raise ModelContractError("model metadata dataset_id is required")
    if metadata.get("published_filename") != REQUIRED_ASSETS[0]:
        raise ModelContractError("model metadata published_filename does not name the packaged TFLite asset")
    metadata_count = metadata.get("num_classes")
    if not isinstance(metadata_count, int) or metadata_count != count:
        raise ModelContractError(f"metadata/class_map class count mismatch: {metadata_count!r} != {count}")
    if metadata.get("class_names") != class_order:
        raise ModelContractError("metadata class_names order does not match class_map class_index order")

    try:
        input_contracts = tensor_contract["inputs"]
        output_contracts = tensor_contract["outputs"]
        if not isinstance(input_contracts, list) or len(input_contracts) != 1:
            raise ModelContractError("tensor_contract must describe exactly one input")
        if not isinstance(output_contracts, list) or len(output_contracts) != 1:
            raise ModelContractError("tensor_contract must describe exactly one output")
        input_contract = input_contracts[0]
        output_contract = output_contracts[0]
        input_shape = _shape(input_contract.get("shape"), "tensor input shape")
        output_shape = _shape(output_contract.get("shape"), "tensor output shape")
    except (KeyError, TypeError, IndexError) as error:
        raise ModelContractError(f"invalid tensor_contract.json: {error}") from error
    if len(input_shape) != 4 or input_shape[0] != 1:
        raise ModelContractError(f"classifier input must be a batch-one 4D tensor: {input_shape}")
    if not (input_shape[1] == 3 or input_shape[-1] == 3):
        raise ModelContractError(f"classifier input must contain three RGB channels: {input_shape}")
    if len(output_shape) < 1 or output_shape[-1] != count:
        raise ModelContractError(f"tensor output/class_map class count mismatch: {output_shape} != {count}")
    if "float32" not in str(input_contract.get("dtype", "")).lower():
        raise ModelContractError(f"unsupported classifier input dtype: {input_contract.get('dtype')!r}")
    if "float32" not in str(output_contract.get("dtype", "")).lower():
        raise ModelContractError(f"unsupported classifier output dtype: {output_contract.get('dtype')!r}")
    input_size = metadata.get("input_size")
    spatial_dims = input_shape[2:4] if input_shape[1] == 3 else input_shape[1:3]
    if not isinstance(input_size, int) or spatial_dims != [input_size, input_size]:
        raise ModelContractError(f"metadata input_size/tensor shape mismatch: {input_size!r} != {input_shape}")

    all_asset_ids = [record["id"] for record in release_assets.values()]
    if len(set(all_asset_ids)) != len(REQUIRED_ASSETS):
        raise ModelContractError("required production assets do not have distinct Release asset ids")
    if {record["release_id"] for record in release_assets.values()} != {release["id"]}:
        raise ModelContractError("production asset records do not share one Release id")

    return {
        "schema_version": 1,
        "model_id": model_id,
        "dataset_id": dataset_id,
        "release_tag": RELEASE_TAG,
        "release_source": RELEASE_SOURCE,
        "release_id": release["id"],
        "release_created_at": release.get("created_at"),
        "release_published_at": release.get("published_at"),
        "release_updated_at": release.get("updated_at"),
        "published_at": metadata.get("created_at"),
        "build_sha": build_sha or None,
        "sha256": actual_model_sha,
        "bytes": len(model),
        "class_count": count,
        "class_order": class_order,
        "classes": normalized_classes,
        "input_shape": input_shape,
        "input_dtype": input_contract["dtype"],
        "output_shape": output_shape,
        "output_dtype": output_contract["dtype"],
        "class_map_sha256": sha256_bytes(class_map_bytes),
        "tensor_contract_sha256": sha256_bytes(tensor_contract_bytes),
        "metadata_sha256": sha256_bytes(metadata_bytes),
        "release_assets": [
            {
                key: release_assets[name][key]
                for key in ("name", "id", "size", "digest", "updated_at", "state", "release_id")
            }
            for name in REQUIRED_ASSETS
        ],
    }


def verify_model_snapshot(
    *,
    model: bytes,
    metadata_bytes: bytes,
    class_map_bytes: bytes,
    tensor_contract_bytes: bytes,
    snapshot: dict[str, Any],
) -> dict[str, Any]:
    """Rebuild the snapshot from packaged assets and reject any drift."""
    if snapshot.get("schema_version") != 1:
        raise ModelContractError(f"unsupported model release snapshot schema: {snapshot.get('schema_version')!r}")
    asset_rows = snapshot.get("release_assets")
    if not isinstance(asset_rows, list):
        raise ModelContractError("model release snapshot is missing release_assets")
    assets = []
    for row in asset_rows:
        if not isinstance(row, dict):
            raise ModelContractError("invalid release asset record in model snapshot")
        asset_id = row.get("id")
        assets.append(
            {
                **row,
                "url": f"https://api.github.com/repos/{RELEASE_REPOSITORY}/releases/assets/{asset_id}",
            }
        )
    release = {
        "id": snapshot.get("release_id"),
        "tag_name": snapshot.get("release_tag"),
        "created_at": snapshot.get("release_created_at"),
        "published_at": snapshot.get("release_published_at"),
        "updated_at": snapshot.get("release_updated_at"),
        "assets": assets,
    }
    rebuilt = build_model_snapshot(
        model=model,
        metadata_bytes=metadata_bytes,
        class_map_bytes=class_map_bytes,
        tensor_contract_bytes=tensor_contract_bytes,
        release=release,
        build_sha=snapshot.get("build_sha"),
    )
    if rebuilt != snapshot:
        differing = sorted(key for key in set(rebuilt) | set(snapshot) if rebuilt.get(key) != snapshot.get(key))
        raise ModelContractError(f"packaged model snapshot mismatch in fields: {differing}")
    return rebuilt


def snapshot_summary(snapshot: dict[str, Any]) -> dict[str, Any]:
    """Fields suitable for CI output/evidence without dumping asset URLs."""
    return {
        key: snapshot[key]
        for key in (
            "model_id", "dataset_id", "release_tag", "sha256", "bytes",
            "class_count", "class_order", "input_shape", "output_shape",
            "class_map_sha256", "tensor_contract_sha256",
        )
    }
