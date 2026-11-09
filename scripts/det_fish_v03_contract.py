#!/usr/bin/env python3
"""Immutable DET_FISH v0.3 release, ONNX graph and Android package contract."""

from __future__ import annotations

import hashlib
import io
import json
import os
import shutil
import time
import urllib.request
import zipfile
from pathlib import Path
from typing import Any

DETECTOR_RELEASE_REPOSITORY = "pan277942135/Yujian_Model_Assets"
DETECTOR_RELEASE_ID = 422778098
DETECTOR_RELEASE_TAG = "model-assets-v1"
DETECTOR_RELEASE_NAME = "YuJian Model Assets v1"
DETECTOR_RELEASE_URL = f"https://github.com/{DETECTOR_RELEASE_REPOSITORY}/releases/tag/{DETECTOR_RELEASE_TAG}"
DETECTOR_ASSET_ID = 672467369
DETECTOR_ASSET_NAME = "det-fish-v03.zip"
DETECTOR_ZIP_BYTES = 10_525_017
DETECTOR_ZIP_SHA256 = "4a3dd5a55f254e6a813d7f029e23b8fe00caf7c616422be51510b70a3ba495ca"
DETECTOR_ONNX_SHA256 = "98257b953b0e9f8aa673572fe4a9749a730ed8a2c014a42b7c492a0e3592c9a1"
DETECTOR_ONNX_BYTES = 3_753_998
DETECTOR_METADATA_BYTES = 3_255_235
DETECTOR_PIPELINE_BYTES = 733
DETECTOR_CHECKPOINT_SHA256 = "3e2b4d835de65bf408f565f5104c9993d3c558527690782e75a4415e3c67ca89"
DETECTOR_CHECKPOINT_BYTES = 7_581_047
DETECTOR_MODEL_VERSION = "DET_FISH_v0.3"
DETECTOR_DATASET_VERSION = "DET_DS_v0.3"
DETECTOR_MODEL_FAMILY = "YOLOX_NANO"
DETECTOR_INPUT_NAME = "images"
DETECTOR_INPUT_SHAPE = [1, 3, 416, 416]
DETECTOR_OUTPUT_SHAPE = [1, 3549, 6]
DETECTOR_OUTPUT_CONTRACT = "decoded_xywh_objectness_class_probability"
DETECTOR_OPSET_VERSION = 17
DETECTOR_INPUT_LAYOUT = "BGR_PLANAR"
DETECTOR_PAD_VALUE = 114
DETECTOR_NMS_IOU = 0.45

DETECTOR_ONNX_MEMBER = "DEF_FISH/models_DET_FISH_v0.3_fish_detector_yolox_nano_v0_1.onnx"
DETECTOR_METADATA_MEMBER = "DEF_FISH/detector_metadata.json.txt"
DETECTOR_PIPELINE_MEMBER = "DEF_FISH/recognition_pipeline_v1.json.txt"
DETECTOR_CHECKPOINT_MEMBER = "DEF_FISH/models_DET_FISH_v0.3_best_ckpt.pth"

ANDROID_DETECTOR_MODEL_FILE = "det_fish_v0_3.onnx"
ANDROID_DETECTOR_METADATA_FILE = "detector_metadata.json"
ANDROID_DETECTOR_PIPELINE_FILE = "recognition_pipeline_v1.json"
ANDROID_DETECTOR_SNAPSHOT_FILE = "detector_model_release_snapshot.json"
ANDROID_CLASSIFIER_PARITY_FILE = "classifier_bundle_parity.json"
ANDROID_MODEL_PACKAGE_MANIFEST_FILE = "android_model_package_manifest.json"

CLASSIFIER_BUNDLE_ASSET_ID = 672468391
CLASSIFIER_BUNDLE_ASSET_NAME = "classifier-crop-m1-v02.zip"
CLASSIFIER_BUNDLE_BYTES = 19_082_024
CLASSIFIER_BUNDLE_SHA256 = "aef10982824426052c07907093b11cb1d6448ff5f6c52027f266ed95f87c640a"
CLASSIFIER_BUNDLE_TFLITE_MEMBER = "MODEL_CROP_M1_v0.2/models_MODEL_CROP_M1_v0.2_export_MODEL_CROP_M1_v0.2.tflite"
CLASSIFIER_BUNDLE_METADATA_MEMBER = "MODEL_CROP_M1_v0.2/fish_classifier_v0_2.metadata.json.txt"
CLASSIFIER_BUNDLE_CLASS_MAP_MEMBER = "MODEL_CROP_M1_v0.2/class_map.json.txt"
CLASSIFIER_BUNDLE_MANIFEST_MEMBER = "MODEL_CROP_M1_v0.2/publish_manifest.json.txt"
CLASSIFIER_MODEL_ID = "MODEL_CROP_M1_v0.2"
CLASSIFIER_DATASET_ID = "DS_M1_v1.1"
CLASSIFIER_TFLITE_SHA256 = "29c2f2fd1608178f023b8e35edf632c0dd685df4a7ae1c3562220a7352ccce18"
CLASSIFIER_INPUT_SHAPE = [1, 3, 224, 224]
CLASSIFIER_OUTPUT_SHAPE = [1, 15]
CLASSIFIER_CLASS_COUNT = 15
CLASSIFIER_PRODUCTION_RELEASE_ID = 379310749
CLASSIFIER_PRODUCTION_RELEASE_TAG = "mobile-model-v0.2"
CLASSIFIER_PRODUCTION_TFLITE_ASSET_ID = 598521217

MODEL_ASSETS_EXPECTED_ASSETS = {
    DETECTOR_ASSET_ID: {
        "name": DETECTOR_ASSET_NAME,
        "size": DETECTOR_ZIP_BYTES,
        "digest": f"sha256:{DETECTOR_ZIP_SHA256}",
    },
    CLASSIFIER_BUNDLE_ASSET_ID: {
        "name": CLASSIFIER_BUNDLE_ASSET_NAME,
        "size": CLASSIFIER_BUNDLE_BYTES,
        "digest": f"sha256:{CLASSIFIER_BUNDLE_SHA256}",
    },
}


class DetectorContractError(RuntimeError):
    pass


def sha256_bytes(payload: bytes) -> str:
    return hashlib.sha256(payload).hexdigest()


def read_json_object(payload: bytes, label: str) -> dict[str, Any]:
    try:
        value = json.loads(payload.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise DetectorContractError(f"{label} is not valid UTF-8 JSON: {error}") from error
    if not isinstance(value, dict):
        raise DetectorContractError(f"{label} must be a JSON object")
    return value


def _asset_records(release: dict[str, Any]) -> dict[int, dict[str, Any]]:
    if release.get("id") != DETECTOR_RELEASE_ID:
        raise DetectorContractError(f"unexpected model-assets Release id: {release.get('id')!r}")
    if release.get("tag_name") != DETECTOR_RELEASE_TAG:
        raise DetectorContractError(f"unexpected model-assets Release tag: {release.get('tag_name')!r}")
    if release.get("name") != DETECTOR_RELEASE_NAME:
        raise DetectorContractError(f"unexpected model-assets Release name: {release.get('name')!r}")
    if release.get("draft") is not False:
        raise DetectorContractError("model-assets Release must be published before Android bootstrap")
    assets = release.get("assets")
    if not isinstance(assets, list) or len(assets) != 2:
        raise DetectorContractError("model-assets Release must contain exactly the two approved ZIP assets")
    by_id: dict[int, dict[str, Any]] = {}
    for asset in assets:
        if not isinstance(asset, dict):
            raise DetectorContractError("invalid model-assets Release asset record")
        asset_id = asset.get("id")
        if asset_id not in MODEL_ASSETS_EXPECTED_ASSETS or asset_id in by_id:
            raise DetectorContractError(f"unexpected or duplicate model-assets Release asset id: {asset_id!r}")
        expected = MODEL_ASSETS_EXPECTED_ASSETS[asset_id]
        for key in ("name", "size", "digest"):
            if asset.get(key) != expected[key]:
                raise DetectorContractError(
                    f"model-assets Release asset {asset_id} {key} mismatch: "
                    f"{asset.get(key)!r} != {expected[key]!r}"
                )
        if asset.get("state") not in (None, "uploaded"):
            raise DetectorContractError(f"model-assets Release asset is not uploaded: {asset_id}")
        url = asset.get("url")
        expected_prefix = f"https://api.github.com/repos/{DETECTOR_RELEASE_REPOSITORY}/releases/assets/"
        if not isinstance(url, str) or not url.startswith(expected_prefix):
            raise DetectorContractError(f"invalid model-assets Release asset API URL: {url!r}")
        by_id[asset_id] = asset
    if set(by_id) != set(MODEL_ASSETS_EXPECTED_ASSETS):
        raise DetectorContractError("model-assets Release asset id set differs from the approved two-asset set")
    return by_id


def fetch_model_assets_release() -> dict[str, Any]:
    url = f"https://api.github.com/repos/{DETECTOR_RELEASE_REPOSITORY}/releases/{DETECTOR_RELEASE_ID}"
    request = urllib.request.Request(
        url,
        headers={
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "YuJian-Android-Model-Bootstrap/1",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            release = json.loads(response.read().decode("utf-8"))
    except Exception as error:
        raise DetectorContractError(f"cannot read published model-assets Release {DETECTOR_RELEASE_ID}: {error}") from error
    if not isinstance(release, dict):
        raise DetectorContractError("model-assets Release API did not return an object")
    _asset_records(release)
    return release


def download_release_asset(asset: dict[str, Any], destination: Path) -> bytes:
    url = asset.get("url")
    prefix = f"https://api.github.com/repos/{DETECTOR_RELEASE_REPOSITORY}/releases/assets/"
    if not isinstance(url, str) or not url.startswith(prefix):
        raise DetectorContractError(f"invalid model-assets API asset URL for {asset.get('name')!r}")
    request = urllib.request.Request(
        url,
        headers={
            "Accept": "application/octet-stream",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "YuJian-Android-Model-Bootstrap/1",
        },
    )
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_name(f".{destination.name}.download-{time.time_ns()}")
    try:
        with urllib.request.urlopen(request, timeout=180) as response, temporary.open("wb") as output:
            shutil.copyfileobj(response, output)
        payload = temporary.read_bytes()
        expected = MODEL_ASSETS_EXPECTED_ASSETS[asset["id"]]
        if len(payload) != expected["size"]:
            raise DetectorContractError(
                f"{asset['name']} byte count mismatch: {len(payload)} != {expected['size']}"
            )
        actual = sha256_bytes(payload)
        if actual != expected["digest"].removeprefix("sha256:"):
            raise DetectorContractError(f"{asset['name']} SHA-256 mismatch: {actual}")
        os.replace(temporary, destination)
        return payload
    except Exception:
        temporary.unlink(missing_ok=True)
        raise


def cached_release_asset(release: dict[str, Any], asset_id: int, cache: Path) -> tuple[Path, bytes]:
    assets = _asset_records(release)
    asset = assets[asset_id]
    destination = cache / asset["name"]
    expected = MODEL_ASSETS_EXPECTED_ASSETS[asset_id]
    if destination.is_file():
        payload = destination.read_bytes()
        if len(payload) == expected["size"] and sha256_bytes(payload) == expected["digest"].removeprefix("sha256:"):
            return destination, payload
        destination.unlink(missing_ok=True)
    payload = download_release_asset(asset, destination)
    return destination, payload


def _safe_member_name(name: str) -> bool:
    path = Path(name)
    return (
        bool(name)
        and not path.is_absolute()
        and "\\" not in name
        and ".." not in path.parts
        and not name.startswith("/")
    )


def _read_required_member(archive: zipfile.ZipFile, name: str) -> bytes:
    if name not in archive.namelist():
        raise DetectorContractError(f"model ZIP is missing required member: {name}")
    info = archive.getinfo(name)
    if not _safe_member_name(info.filename):
        raise DetectorContractError(f"unsafe model ZIP member path: {info.filename}")
    mode = (info.external_attr >> 16) & 0o170000
    if mode == 0o120000:
        raise DetectorContractError(f"symbolic links are not permitted in model ZIP: {name}")
    return archive.read(name)


def verify_onnx_graph(onnx_bytes: bytes) -> dict[str, Any]:
    try:
        import onnx
        from onnx import TensorProto
    except ImportError as error:
        raise DetectorContractError("onnx==1.17.0 is required to inspect the DET_FISH v0.3 graph") from error
    try:
        model = onnx.load_model_from_string(onnx_bytes)
        onnx.checker.check_model(model)
    except Exception as error:
        raise DetectorContractError(f"DET_FISH v0.3 ONNX graph is invalid: {error}") from error

    graph = model.graph
    initializer_names = {item.name for item in graph.initializer}
    inputs = [item for item in graph.input if item.name not in initializer_names]
    outputs = list(graph.output)
    if len(inputs) != 1 or len(outputs) != 1:
        raise DetectorContractError(
            f"DET_FISH v0.3 must expose one graph input and one output; got {len(inputs)} and {len(outputs)}"
        )

    def tensor_contract(value_info: Any, label: str) -> tuple[str, list[int]]:
        if not value_info.type.HasField("tensor_type"):
            raise DetectorContractError(f"{label} is not a tensor")
        tensor = value_info.type.tensor_type
        if tensor.elem_type != TensorProto.FLOAT:
            raise DetectorContractError(f"{label} must be float32, got ONNX element type {tensor.elem_type}")
        shape = []
        for dim in tensor.shape.dim:
            if dim.dim_value <= 0:
                raise DetectorContractError(f"{label} contains a dynamic or invalid dimension")
            shape.append(int(dim.dim_value))
        return value_info.name, shape

    input_name, input_shape = tensor_contract(inputs[0], "DET_FISH v0.3 input")
    output_name, output_shape = tensor_contract(outputs[0], "DET_FISH v0.3 output")
    if input_name != DETECTOR_INPUT_NAME or input_shape != DETECTOR_INPUT_SHAPE:
        raise DetectorContractError(
            f"DET_FISH v0.3 graph input mismatch: {input_name} {input_shape}"
        )
    if not output_name or output_shape != DETECTOR_OUTPUT_SHAPE:
        raise DetectorContractError(
            f"DET_FISH v0.3 graph output mismatch: {output_name!r} {output_shape}"
        )
    opsets = {entry.domain: int(entry.version) for entry in model.opset_import}
    if opsets.get("", 0) != DETECTOR_OPSET_VERSION:
        raise DetectorContractError(f"DET_FISH v0.3 ONNX opset mismatch: {opsets.get('', 0)}")
    return {
        "input_name": input_name,
        "input_shape": input_shape,
        "input_dtype": "float32",
        "output_name": output_name,
        "output_shape": output_shape,
        "output_dtype": "float32",
        "opset_version": opsets[""],
        "node_count": len(graph.node),
    }


def verify_detector_metadata_and_pipeline(
    metadata_bytes: bytes,
    pipeline_bytes: bytes,
    onnx_bytes: bytes,
) -> tuple[dict[str, Any], dict[str, Any], dict[str, Any]]:
    metadata = read_json_object(metadata_bytes, "detector_metadata.json")
    pipeline = read_json_object(pipeline_bytes, "recognition_pipeline_v1.json")
    if metadata.get("model_version") != DETECTOR_MODEL_VERSION:
        raise DetectorContractError(f"unexpected detector model_version: {metadata.get('model_version')!r}")
    if metadata.get("dataset_version") != DETECTOR_DATASET_VERSION:
        raise DetectorContractError(f"unexpected detector dataset_version: {metadata.get('dataset_version')!r}")
    if metadata.get("model_family") != DETECTOR_MODEL_FAMILY:
        raise DetectorContractError(f"unexpected detector model_family: {metadata.get('model_family')!r}")
    onnx_meta = metadata.get("onnx")
    if not isinstance(onnx_meta, dict):
        raise DetectorContractError("detector metadata is missing the ONNX contract object")
    if metadata.get("onnx_sha256") != DETECTOR_ONNX_SHA256:
        raise DetectorContractError("detector metadata ONNX SHA does not match the approved v0.3 SHA")
    if metadata.get("onnx_bytes") != DETECTOR_ONNX_BYTES or len(onnx_bytes) != DETECTOR_ONNX_BYTES:
        raise DetectorContractError("detector metadata ONNX byte count does not match the approved v0.3 model")
    if onnx_meta.get("input_name") != DETECTOR_INPUT_NAME:
        raise DetectorContractError("detector metadata input name mismatch")
    if onnx_meta.get("input_shape") != DETECTOR_INPUT_SHAPE:
        raise DetectorContractError("detector metadata input shape mismatch")
    if onnx_meta.get("output_shape") != DETECTOR_OUTPUT_SHAPE:
        raise DetectorContractError("detector metadata output shape mismatch")
    if onnx_meta.get("output_contract") != DETECTOR_OUTPUT_CONTRACT:
        raise DetectorContractError("detector metadata output semantics are not the v0.3 decoded XYWH contract")
    if onnx_meta.get("onnx_opset") != DETECTOR_OPSET_VERSION:
        raise DetectorContractError("detector metadata ONNX opset mismatch")
    graph = verify_onnx_graph(onnx_bytes)

    if pipeline.get("contract_version") != "RECOGNITION_PIPELINE_v1":
        raise DetectorContractError("recognition pipeline contract version mismatch")
    detector = pipeline.get("detector")
    quality = pipeline.get("quality_gate")
    crop = pipeline.get("crop")
    if not isinstance(detector, dict) or not isinstance(quality, dict) or not isinstance(crop, dict):
        raise DetectorContractError("recognition pipeline is missing detector, quality_gate or crop objects")
    expected_detector = {
        "model_family": DETECTOR_MODEL_FAMILY,
        "class_name": "fish",
        "input_size": 416,
        "strong_confidence": 0.35,
        "weak_confidence": 0.2,
        "nms_iou": DETECTOR_NMS_IOU,
    }
    for key, expected in expected_detector.items():
        if detector.get(key) != expected:
            raise DetectorContractError(f"recognition pipeline detector.{key} mismatch: {detector.get(key)!r}")
    expected_quality = {
        "min_primary_area_ratio": 0.08,
        "incomplete_edge_margin_ratio": 0.015,
    }
    for key, expected in expected_quality.items():
        if quality.get(key) != expected:
            raise DetectorContractError(f"recognition pipeline quality_gate.{key} mismatch: {quality.get(key)!r}")
    if (
        crop.get("expand_ratio") != 0.15
        or crop.get("classifier_size") != 224
        or crop.get("padding_rgb") != [124, 116, 104]
        or crop.get("normalization_mean") != [0.485, 0.456, 0.406]
        or crop.get("normalization_std") != [0.229, 0.224, 0.225]
    ):
        raise DetectorContractError("recognition pipeline classifier crop/normalization contract mismatch")
    if metadata.get("pipeline_contract") != pipeline:
        raise DetectorContractError("detector metadata embedded pipeline contract differs from recognition_pipeline_v1.json")
    if metadata.get("checkpoint_sha256") != DETECTOR_CHECKPOINT_SHA256:
        raise DetectorContractError("detector metadata checkpoint SHA does not match the approved v0.3 checkpoint")
    return metadata, pipeline, graph


def verify_detector_zip_bytes(payload: bytes) -> dict[str, Any]:
    if len(payload) != DETECTOR_ZIP_BYTES:
        raise DetectorContractError(f"detector ZIP size mismatch: {len(payload)} != {DETECTOR_ZIP_BYTES}")
    actual_zip_sha = sha256_bytes(payload)
    if actual_zip_sha != DETECTOR_ZIP_SHA256:
        raise DetectorContractError(f"detector ZIP SHA-256 mismatch: {actual_zip_sha}")
    try:
        archive = zipfile.ZipFile(io.BytesIO(payload))
    except zipfile.BadZipFile as error:
        raise DetectorContractError(f"invalid DET_FISH v0.3 ZIP: {error}") from error
    with archive:
        for info in archive.infolist():
            if info.is_dir():
                continue
            if not _safe_member_name(info.filename):
                raise DetectorContractError(f"unsafe detector ZIP member: {info.filename}")
            lowered = info.filename.lower()
            if "sam" in lowered or "slim" in lowered:
                raise DetectorContractError(f"SAM/SlimSAM weights are not permitted in the detector Release ZIP: {info.filename}")
        onnx_bytes = _read_required_member(archive, DETECTOR_ONNX_MEMBER)
        metadata_bytes = _read_required_member(archive, DETECTOR_METADATA_MEMBER)
        pipeline_bytes = _read_required_member(archive, DETECTOR_PIPELINE_MEMBER)
        checkpoint_bytes = _read_required_member(archive, DETECTOR_CHECKPOINT_MEMBER)
    if len(metadata_bytes) != DETECTOR_METADATA_BYTES:
        raise DetectorContractError("DET_FISH v0.3 metadata byte count mismatch")
    if len(pipeline_bytes) != DETECTOR_PIPELINE_BYTES:
        raise DetectorContractError("DET_FISH v0.3 pipeline contract byte count mismatch")
    onnx_sha = sha256_bytes(onnx_bytes)
    checkpoint_sha = sha256_bytes(checkpoint_bytes)
    if onnx_sha != DETECTOR_ONNX_SHA256:
        raise DetectorContractError(f"DET_FISH v0.3 ONNX SHA-256 mismatch: {onnx_sha}")
    if len(onnx_bytes) != DETECTOR_ONNX_BYTES:
        raise DetectorContractError("DET_FISH v0.3 ONNX size mismatch")
    if checkpoint_sha != DETECTOR_CHECKPOINT_SHA256:
        raise DetectorContractError(f"DET_FISH v0.3 checkpoint SHA-256 mismatch: {checkpoint_sha}")
    if len(checkpoint_bytes) != DETECTOR_CHECKPOINT_BYTES:
        raise DetectorContractError("DET_FISH v0.3 checkpoint size mismatch")
    metadata, pipeline, graph = verify_detector_metadata_and_pipeline(metadata_bytes, pipeline_bytes, onnx_bytes)
    return {
        "zip_sha256": actual_zip_sha,
        "zip_bytes": len(payload),
        "onnx_sha256": onnx_sha,
        "onnx_bytes": len(onnx_bytes),
        "checkpoint_sha256": checkpoint_sha,
        "checkpoint_bytes": len(checkpoint_bytes),
        "model_version": metadata["model_version"],
        "dataset_version": metadata["dataset_version"],
        "model_family": metadata["model_family"],
        "input_name": graph["input_name"],
        "input_shape": graph["input_shape"],
        "input_dtype": graph["input_dtype"],
        "output_name": graph["output_name"],
        "output_shape": graph["output_shape"],
        "output_dtype": graph["output_dtype"],
        "output_contract": DETECTOR_OUTPUT_CONTRACT,
        "opset_version": graph["opset_version"],
        "input_layout": DETECTOR_INPUT_LAYOUT,
        "pad_value": DETECTOR_PAD_VALUE,
        "nms_iou": DETECTOR_NMS_IOU,
        "metadata_sha256": sha256_bytes(metadata_bytes),
        "metadata_bytes": len(metadata_bytes),
        "pipeline_sha256": sha256_bytes(pipeline_bytes),
        "pipeline_bytes": len(pipeline_bytes),
        "pipeline_contract_version": pipeline["contract_version"],
    }


def build_detector_snapshot(
    *,
    release: dict[str, Any],
    evidence: dict[str, Any],
    build_sha: str | None = None,
) -> dict[str, Any]:
    assets = _asset_records(release)
    asset = assets[DETECTOR_ASSET_ID]
    return {
        "schema_version": 1,
        "model_version": DETECTOR_MODEL_VERSION,
        "dataset_version": DETECTOR_DATASET_VERSION,
        "model_family": DETECTOR_MODEL_FAMILY,
        "release_id": DETECTOR_RELEASE_ID,
        "release_tag": DETECTOR_RELEASE_TAG,
        "release_url": DETECTOR_RELEASE_URL,
        "release_published_at": release.get("published_at"),
        "asset_id": DETECTOR_ASSET_ID,
        "asset_name": DETECTOR_ASSET_NAME,
        "asset_size": asset["size"],
        "zip_sha256": evidence["zip_sha256"],
        "onnx_sha256": evidence["onnx_sha256"],
        "onnx_bytes": evidence["onnx_bytes"],
        "metadata_sha256": evidence["metadata_sha256"],
        "metadata_bytes": evidence["metadata_bytes"],
        "pipeline_sha256": evidence["pipeline_sha256"],
        "pipeline_bytes": evidence["pipeline_bytes"],
        "checkpoint_sha256": evidence["checkpoint_sha256"],
        "checkpoint_bytes": evidence["checkpoint_bytes"],
        "input_name": evidence["input_name"],
        "input_shape": evidence["input_shape"],
        "input_dtype": evidence["input_dtype"],
        "output_name": evidence["output_name"],
        "output_shape": evidence["output_shape"],
        "output_dtype": evidence["output_dtype"],
        "output_contract": evidence["output_contract"],
        "opset_version": evidence["opset_version"],
        "input_layout": evidence["input_layout"],
        "pad_value": evidence["pad_value"],
        "nms_iou": evidence["nms_iou"],
        "pipeline_contract_version": evidence["pipeline_contract_version"],
        "build_sha": build_sha or None,
    }


def write_atomic(path: Path, payload: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f".{path.name}.install-{time.time_ns()}")
    try:
        temporary.write_bytes(payload)
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


def install_detector_zip(zip_payload: bytes, assets_dir: Path, release: dict[str, Any], build_sha: str | None = None) -> dict[str, Any]:
    evidence = verify_detector_zip_bytes(zip_payload)
    with zipfile.ZipFile(io.BytesIO(zip_payload)) as archive:
        onnx_bytes = _read_required_member(archive, DETECTOR_ONNX_MEMBER)
        metadata_bytes = _read_required_member(archive, DETECTOR_METADATA_MEMBER)
        pipeline_bytes = _read_required_member(archive, DETECTOR_PIPELINE_MEMBER)
    snapshot = build_detector_snapshot(release=release, evidence=evidence, build_sha=build_sha)
    write_atomic(assets_dir / ANDROID_DETECTOR_MODEL_FILE, onnx_bytes)
    write_atomic(assets_dir / ANDROID_DETECTOR_METADATA_FILE, metadata_bytes)
    write_atomic(assets_dir / ANDROID_DETECTOR_PIPELINE_FILE, pipeline_bytes)
    write_atomic(
        assets_dir / ANDROID_DETECTOR_SNAPSHOT_FILE,
        json.dumps(snapshot, ensure_ascii=False, indent=2, sort_keys=True).encode("utf-8") + b"\n",
    )
    return snapshot


def verify_detector_payloads(
    *,
    onnx_bytes: bytes,
    metadata_bytes: bytes,
    pipeline_bytes: bytes,
    snapshot_bytes: bytes,
) -> dict[str, Any]:
    snapshot = read_json_object(snapshot_bytes, ANDROID_DETECTOR_SNAPSHOT_FILE)
    if snapshot.get("schema_version") != 1:
        raise DetectorContractError("unsupported detector model snapshot schema")
    if snapshot.get("release_id") != DETECTOR_RELEASE_ID or snapshot.get("asset_id") != DETECTOR_ASSET_ID:
        raise DetectorContractError("detector model snapshot release/asset identity mismatch")
    if snapshot.get("zip_sha256") != DETECTOR_ZIP_SHA256:
        raise DetectorContractError("detector model snapshot ZIP SHA mismatch")
    if snapshot.get("checkpoint_sha256") != DETECTOR_CHECKPOINT_SHA256:
        raise DetectorContractError("detector model snapshot checkpoint SHA mismatch")
    if sha256_bytes(onnx_bytes) != DETECTOR_ONNX_SHA256:
        raise DetectorContractError("packaged detector ONNX SHA mismatch")
    if len(onnx_bytes) != DETECTOR_ONNX_BYTES:
        raise DetectorContractError("packaged detector ONNX size mismatch")
    metadata, pipeline, graph = verify_detector_metadata_and_pipeline(metadata_bytes, pipeline_bytes, onnx_bytes)
    expected_snapshot = {
        "model_version": metadata["model_version"],
        "dataset_version": metadata["dataset_version"],
        "model_family": metadata["model_family"],
        "onnx_sha256": DETECTOR_ONNX_SHA256,
        "onnx_bytes": DETECTOR_ONNX_BYTES,
        "metadata_sha256": sha256_bytes(metadata_bytes),
        "metadata_bytes": len(metadata_bytes),
        "pipeline_sha256": sha256_bytes(pipeline_bytes),
        "pipeline_bytes": len(pipeline_bytes),
        "input_name": graph["input_name"],
        "input_shape": graph["input_shape"],
        "input_dtype": graph["input_dtype"],
        "output_name": graph["output_name"],
        "output_shape": graph["output_shape"],
        "output_dtype": graph["output_dtype"],
        "output_contract": DETECTOR_OUTPUT_CONTRACT,
        "opset_version": graph["opset_version"],
        "input_layout": DETECTOR_INPUT_LAYOUT,
        "pad_value": DETECTOR_PAD_VALUE,
        "nms_iou": DETECTOR_NMS_IOU,
        "pipeline_contract_version": pipeline["contract_version"],
    }
    for key, expected in expected_snapshot.items():
        if snapshot.get(key) != expected:
            raise DetectorContractError(f"detector snapshot {key} mismatch: {snapshot.get(key)!r} != {expected!r}")
    return snapshot


def verify_detector_asset_directory(assets_dir: Path) -> dict[str, Any]:
    paths = {
        "onnx": assets_dir / ANDROID_DETECTOR_MODEL_FILE,
        "metadata": assets_dir / ANDROID_DETECTOR_METADATA_FILE,
        "pipeline": assets_dir / ANDROID_DETECTOR_PIPELINE_FILE,
        "snapshot": assets_dir / ANDROID_DETECTOR_SNAPSHOT_FILE,
    }
    for label, path in paths.items():
        if not path.is_file() or path.stat().st_size == 0:
            raise DetectorContractError(f"missing or empty packaged detector {label}: {path}")
    return verify_detector_payloads(
        onnx_bytes=paths["onnx"].read_bytes(),
        metadata_bytes=paths["metadata"].read_bytes(),
        pipeline_bytes=paths["pipeline"].read_bytes(),
        snapshot_bytes=paths["snapshot"].read_bytes(),
    )


def _normalized_class_map(class_map: dict[str, Any]) -> list[dict[str, Any]]:
    rows = class_map.get("classes")
    if not isinstance(rows, list) or not rows:
        raise DetectorContractError("classifier ZIP class_map.classes must be a non-empty array")
    try:
        normalized = [
            {
                "class_index": int(row["class_index"]),
                "species_key": str(row["species_key"]).strip(),
                "display_name": str(row.get("common_name_zh") or row.get("display_name") or row.get("species_key") or "").strip(),
            }
            for row in rows
        ]
        normalized.sort(key=lambda row: row["class_index"])
    except (KeyError, TypeError, ValueError) as error:
        raise DetectorContractError(f"invalid classifier ZIP class_map entry: {error}") from error
    if [row["class_index"] for row in normalized] != list(range(len(normalized))):
        raise DetectorContractError("classifier ZIP class_map indices must be contiguous from zero")
    if len({row["species_key"] for row in normalized}) != len(normalized):
        raise DetectorContractError("classifier ZIP class_map has duplicate species keys")
    return normalized


def verify_classifier_bundle_parity(
    zip_payload: bytes,
    production_snapshot: dict[str, Any],
    release: dict[str, Any],
) -> dict[str, Any]:
    assets = _asset_records(release)
    asset = assets[CLASSIFIER_BUNDLE_ASSET_ID]
    if len(zip_payload) != CLASSIFIER_BUNDLE_BYTES or sha256_bytes(zip_payload) != CLASSIFIER_BUNDLE_SHA256:
        raise DetectorContractError("classifier source ZIP size/SHA does not match the approved model-assets Release asset")
    try:
        with zipfile.ZipFile(io.BytesIO(zip_payload)) as archive:
            tflite = _read_required_member(archive, CLASSIFIER_BUNDLE_TFLITE_MEMBER)
            metadata_bytes = _read_required_member(archive, CLASSIFIER_BUNDLE_METADATA_MEMBER)
            class_map_bytes = _read_required_member(archive, CLASSIFIER_BUNDLE_CLASS_MAP_MEMBER)
            manifest_bytes = _read_required_member(archive, CLASSIFIER_BUNDLE_MANIFEST_MEMBER)
    except zipfile.BadZipFile as error:
        raise DetectorContractError(f"invalid classifier source ZIP: {error}") from error

    metadata = read_json_object(metadata_bytes, "classifier ZIP metadata")
    class_map = read_json_object(class_map_bytes, "classifier ZIP class_map")
    manifest = read_json_object(manifest_bytes, "classifier ZIP publish_manifest")
    tflite_sha = sha256_bytes(tflite)
    if tflite_sha != CLASSIFIER_TFLITE_SHA256 or tflite_sha != production_snapshot.get("sha256"):
        raise DetectorContractError("classifier ZIP TFLite SHA does not match the current production Release")
    if tflite[4:8] != b"TFL3":
        raise DetectorContractError("classifier ZIP TFLite is not a valid TFL3 FlatBuffer")
    if metadata.get("model_id") != CLASSIFIER_MODEL_ID or metadata.get("dataset_id") != CLASSIFIER_DATASET_ID:
        raise DetectorContractError("classifier ZIP model/dataset identity mismatch")
    if metadata.get("sha256") != tflite_sha or metadata.get("input_size") != 224 or metadata.get("num_classes") != CLASSIFIER_CLASS_COUNT:
        raise DetectorContractError("classifier ZIP metadata does not match the approved v0.2 contract")
    if metadata.get("class_names") != production_snapshot.get("class_order"):
        raise DetectorContractError("classifier ZIP metadata class_names do not match production class order")

    zip_classes = _normalized_class_map(class_map)
    production_classes = production_snapshot.get("classes")
    if zip_classes != production_classes:
        raise DetectorContractError("classifier ZIP class_map semantic mapping differs from production Release class_map")
    if manifest.get("model_id") not in (None, CLASSIFIER_MODEL_ID):
        raise DetectorContractError("classifier ZIP publish_manifest model_id mismatch")
    if manifest.get("input_shape") != CLASSIFIER_INPUT_SHAPE or manifest.get("output_shape") != CLASSIFIER_OUTPUT_SHAPE:
        raise DetectorContractError("classifier ZIP publish_manifest tensor shape mismatch")
    if manifest.get("num_classes") not in (None, CLASSIFIER_CLASS_COUNT):
        raise DetectorContractError("classifier ZIP publish_manifest class count mismatch")

    production_assets = {row["name"]: row for row in production_snapshot.get("release_assets", [])}
    production_tflite_asset = production_assets.get("fish_classifier_v0_2.tflite")
    if not production_tflite_asset or production_tflite_asset.get("id") != CLASSIFIER_PRODUCTION_TFLITE_ASSET_ID:
        raise DetectorContractError("current classifier production Release TFLite Asset ID mismatch")
    if production_snapshot.get("release_id") != CLASSIFIER_PRODUCTION_RELEASE_ID or production_snapshot.get("release_tag") != CLASSIFIER_PRODUCTION_RELEASE_TAG:
        raise DetectorContractError("current classifier production Release identity mismatch")

    return {
        "schema_version": 1,
        "source_release_id": DETECTOR_RELEASE_ID,
        "source_release_tag": DETECTOR_RELEASE_TAG,
        "source_release_url": DETECTOR_RELEASE_URL,
        "source_asset_id": CLASSIFIER_BUNDLE_ASSET_ID,
        "source_asset_name": CLASSIFIER_BUNDLE_ASSET_NAME,
        "source_zip_bytes": len(zip_payload),
        "source_zip_sha256": CLASSIFIER_BUNDLE_SHA256,
        "source_tflite_member": CLASSIFIER_BUNDLE_TFLITE_MEMBER,
        "source_tflite_sha256": tflite_sha,
        "source_metadata_sha256": sha256_bytes(metadata_bytes),
        "source_class_map_sha256": sha256_bytes(class_map_bytes),
        "source_manifest_sha256": sha256_bytes(manifest_bytes),
        "production_release_id": CLASSIFIER_PRODUCTION_RELEASE_ID,
        "production_release_tag": CLASSIFIER_PRODUCTION_RELEASE_TAG,
        "production_tflite_asset_id": CLASSIFIER_PRODUCTION_TFLITE_ASSET_ID,
        "production_tflite_sha256": production_snapshot["sha256"],
        "production_class_map_sha256": production_snapshot["class_map_sha256"],
        "production_tensor_contract_sha256": production_snapshot["tensor_contract_sha256"],
        "class_map_semantic_match": True,
        "raw_class_map_sha256_match": sha256_bytes(class_map_bytes) == production_snapshot["class_map_sha256"],
        "model_id": CLASSIFIER_MODEL_ID,
        "dataset_id": CLASSIFIER_DATASET_ID,
        "input_shape": CLASSIFIER_INPUT_SHAPE,
        "output_shape": CLASSIFIER_OUTPUT_SHAPE,
        "class_count": CLASSIFIER_CLASS_COUNT,
        "class_order": production_snapshot["class_order"],
        "tensor_contract_verified": True,
    }


def verify_classifier_bundle_parity_record(
    parity: dict[str, Any],
    production_snapshot: dict[str, Any],
) -> dict[str, Any]:
    expected = {
        "schema_version": 1,
        "source_release_id": DETECTOR_RELEASE_ID,
        "source_asset_id": CLASSIFIER_BUNDLE_ASSET_ID,
        "source_zip_sha256": CLASSIFIER_BUNDLE_SHA256,
        "source_tflite_sha256": CLASSIFIER_TFLITE_SHA256,
        "production_release_id": CLASSIFIER_PRODUCTION_RELEASE_ID,
        "production_tflite_asset_id": CLASSIFIER_PRODUCTION_TFLITE_ASSET_ID,
        "production_tflite_sha256": production_snapshot.get("sha256"),
        "production_class_map_sha256": production_snapshot.get("class_map_sha256"),
        "production_tensor_contract_sha256": production_snapshot.get("tensor_contract_sha256"),
        "class_map_semantic_match": True,
        "tensor_contract_verified": True,
        "model_id": CLASSIFIER_MODEL_ID,
        "dataset_id": CLASSIFIER_DATASET_ID,
        "input_shape": CLASSIFIER_INPUT_SHAPE,
        "output_shape": CLASSIFIER_OUTPUT_SHAPE,
        "class_count": CLASSIFIER_CLASS_COUNT,
        "class_order": production_snapshot.get("class_order"),
    }
    for key, value in expected.items():
        if parity.get(key) != value:
            raise DetectorContractError(f"classifier bundle parity {key} mismatch: {parity.get(key)!r} != {value!r}")
    return parity


def build_android_model_package_manifest(
    detector_snapshot: dict[str, Any],
    classifier_snapshot: dict[str, Any],
    classifier_parity: dict[str, Any],
    build_sha: str | None = None,
) -> dict[str, Any]:
    verify_classifier_bundle_parity_record(classifier_parity, classifier_snapshot)
    return {
        "schema_version": 1,
        "build_sha": build_sha or None,
        "detector": {
            "model_version": detector_snapshot["model_version"],
            "release_id": detector_snapshot["release_id"],
            "asset_id": detector_snapshot["asset_id"],
            "zip_sha256": detector_snapshot["zip_sha256"],
            "onnx_sha256": detector_snapshot["onnx_sha256"],
            "checkpoint_sha256": detector_snapshot["checkpoint_sha256"],
            "input_shape": detector_snapshot["input_shape"],
            "output_shape": detector_snapshot["output_shape"],
            "output_contract": detector_snapshot["output_contract"],
        },
        "classifier": {
            "model_id": classifier_snapshot["model_id"],
            "release_id": classifier_snapshot["release_id"],
            "tflite_sha256": classifier_snapshot["sha256"],
            "source_zip_sha256": classifier_parity["source_zip_sha256"],
            "source_asset_id": classifier_parity["source_asset_id"],
            "input_shape": classifier_snapshot["input_shape"],
            "output_shape": classifier_snapshot["output_shape"],
            "class_count": classifier_snapshot["class_count"],
            "class_map_semantic_match": classifier_parity["class_map_semantic_match"],
            "tensor_contract_sha256": classifier_snapshot["tensor_contract_sha256"],
        },
    }


def verify_android_model_package_manifest(
    manifest: dict[str, Any],
    detector_snapshot: dict[str, Any],
    classifier_snapshot: dict[str, Any],
    classifier_parity: dict[str, Any],
) -> dict[str, Any]:
    expected = build_android_model_package_manifest(
        detector_snapshot, classifier_snapshot, classifier_parity, manifest.get("build_sha")
    )
    if manifest != expected:
        raise DetectorContractError("android model package manifest does not match packaged detector/classifier snapshots")
    return expected


def detector_snapshot_summary(snapshot: dict[str, Any]) -> dict[str, Any]:
    return {
        key: snapshot[key]
        for key in (
            "model_version", "dataset_version", "release_id", "asset_id",
            "zip_sha256", "onnx_sha256", "metadata_sha256", "pipeline_sha256", "checkpoint_sha256",
            "input_shape", "output_shape", "output_contract",
        )
    }
