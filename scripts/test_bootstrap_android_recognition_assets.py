#!/usr/bin/env python3
"""Unit tests for pinned detector release, ONNX graph and classifier ZIP parity."""

from __future__ import annotations

import hashlib
import io
import json
import unittest
import zipfile
from unittest.mock import patch

import onnx
from onnx import TensorProto, helper

import det_fish_v03_contract as contract


def _onnx_model(output_shape: list[int] | None = None) -> bytes:
    output_shape = output_shape or contract.DETECTOR_OUTPUT_SHAPE
    input_info = helper.make_tensor_value_info(
        contract.DETECTOR_INPUT_NAME, TensorProto.FLOAT, contract.DETECTOR_INPUT_SHAPE
    )
    output_info = helper.make_tensor_value_info(
        "output", TensorProto.FLOAT, output_shape
    )
    tensor = helper.make_tensor(
        "constant_output",
        TensorProto.FLOAT,
        output_shape,
        [0.0] * int(__import__("math").prod(output_shape)),
    )
    node = helper.make_node("Constant", inputs=[], outputs=["output"], value=tensor)
    graph = helper.make_graph([node], "det-fish-v03-contract-test", [input_info], [output_info])
    model = helper.make_model(graph, opset_imports=[helper.make_opsetid("", contract.DETECTOR_OPSET_VERSION)])
    model.ir_version = 9
    return model.SerializeToString()


def _pipeline() -> dict:
    return {
        "contract_version": "RECOGNITION_PIPELINE_v1",
        "detector": {
            "model_family": "YOLOX_NANO",
            "class_name": "fish",
            "input_size": 416,
            "strong_confidence": 0.35,
            "weak_confidence": 0.2,
            "nms_iou": 0.45,
        },
        "quality_gate": {
            "min_primary_area_ratio": 0.08,
            "incomplete_edge_margin_ratio": 0.015,
        },
        "crop": {
            "expand_ratio": 0.15,
            "classifier_size": 224,
            "resize_mode": "letterbox",
            "padding_rgb": [124, 116, 104],
            "normalization_mean": [0.485, 0.456, 0.406],
            "normalization_std": [0.229, 0.224, 0.225],
        },
    }


def _detector_metadata(onnx_bytes: bytes, checkpoint_sha: str) -> dict:
    pipeline = _pipeline()
    return {
        "model_version": contract.DETECTOR_MODEL_VERSION,
        "dataset_version": contract.DETECTOR_DATASET_VERSION,
        "model_family": contract.DETECTOR_MODEL_FAMILY,
        "onnx_sha256": hashlib.sha256(onnx_bytes).hexdigest(),
        "onnx_bytes": len(onnx_bytes),
        "checkpoint_sha256": checkpoint_sha,
        "onnx": {
            "input_name": contract.DETECTOR_INPUT_NAME,
            "input_shape": contract.DETECTOR_INPUT_SHAPE,
            "output_shape": contract.DETECTOR_OUTPUT_SHAPE,
            "output_contract": contract.DETECTOR_OUTPUT_CONTRACT,
            "onnx_opset": contract.DETECTOR_OPSET_VERSION,
        },
        "pipeline_contract": pipeline,
    }


def _detector_zip(onnx_bytes: bytes, checkpoint: bytes) -> tuple[bytes, bytes, bytes]:
    pipeline = _pipeline()
    pipeline_bytes = json.dumps(pipeline, separators=(",", ":")).encode("utf-8")
    metadata = _detector_metadata(onnx_bytes, hashlib.sha256(checkpoint).hexdigest())
    metadata_bytes = json.dumps(metadata, separators=(",", ":")).encode("utf-8")
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        archive.writestr(contract.DETECTOR_ONNX_MEMBER, onnx_bytes)
        archive.writestr(contract.DETECTOR_METADATA_MEMBER, metadata_bytes)
        archive.writestr(contract.DETECTOR_PIPELINE_MEMBER, pipeline_bytes)
        archive.writestr(contract.DETECTOR_CHECKPOINT_MEMBER, checkpoint)
    return stream.getvalue(), metadata_bytes, pipeline_bytes


class DetectorReleaseContractTests(unittest.TestCase):
    def test_release_requires_exact_published_release_and_asset_ids(self):
        release = {
            "id": contract.DETECTOR_RELEASE_ID,
            "tag_name": contract.DETECTOR_RELEASE_TAG,
            "name": contract.DETECTOR_RELEASE_NAME,
            "draft": False,
            "assets": [
                {
                    "id": asset_id,
                    "name": expected["name"],
                    "size": expected["size"],
                    "digest": expected["digest"],
                    "url": f"https://api.github.com/repos/{contract.DETECTOR_RELEASE_REPOSITORY}/releases/assets/{asset_id}",
                }
                for asset_id, expected in contract.MODEL_ASSETS_EXPECTED_ASSETS.items()
            ],
        }
        indexed = contract._asset_records(release)
        self.assertEqual(set(indexed), set(contract.MODEL_ASSETS_EXPECTED_ASSETS))

    def test_release_rejects_draft_or_extra_asset(self):
        release = {
            "id": contract.DETECTOR_RELEASE_ID,
            "tag_name": contract.DETECTOR_RELEASE_TAG,
            "name": contract.DETECTOR_RELEASE_NAME,
            "draft": True,
            "assets": [],
        }
        with self.assertRaises(contract.DetectorContractError):
            contract._asset_records(release)


class DetectorGraphAndBundleTests(unittest.TestCase):
    def test_v03_onnx_graph_has_exact_input_and_output_tensor_contract(self):
        graph = contract.verify_onnx_graph(_onnx_model())
        self.assertEqual(graph["input_name"], "images")
        self.assertEqual(graph["input_shape"], [1, 3, 416, 416])
        self.assertEqual(graph["output_shape"], [1, 3549, 6])
        self.assertEqual(graph["input_dtype"], "float32")
        self.assertEqual(graph["output_dtype"], "float32")
        self.assertEqual(graph["opset_version"], 17)

    def test_wrong_output_tensor_shape_fails(self):
        with self.assertRaises(contract.DetectorContractError):
            contract.verify_onnx_graph(_onnx_model([1, 3549, 5]))

    def test_wrong_detector_onnx_sha_fails(self):
        onnx_bytes = _onnx_model()
        checkpoint = b"approved-checkpoint-fixture"
        zip_bytes, metadata_bytes, pipeline_bytes = _detector_zip(onnx_bytes, checkpoint)
        metadata = json.loads(metadata_bytes)
        metadata["onnx_sha256"] = "0" * 64
        with patch.object(contract, "DETECTOR_ONNX_SHA256", "1" * 64):
            with self.assertRaises(contract.DetectorContractError):
                contract.verify_detector_metadata_and_pipeline(
                    json.dumps(metadata).encode(), pipeline_bytes, onnx_bytes
                )

    def test_wrong_output_semantics_fail(self):
        onnx_bytes = _onnx_model()
        checkpoint = b"approved-checkpoint-fixture"
        _, metadata_bytes, pipeline_bytes = _detector_zip(onnx_bytes, checkpoint)
        metadata = json.loads(metadata_bytes)
        metadata["onnx"]["output_contract"] = "raw_yolox_grid_logits"
        with patch.object(contract, "DETECTOR_ONNX_SHA256", hashlib.sha256(onnx_bytes).hexdigest()), \
             patch.object(contract, "DETECTOR_ONNX_BYTES", len(onnx_bytes)), \
             patch.object(contract, "DETECTOR_CHECKPOINT_SHA256", hashlib.sha256(checkpoint).hexdigest()):
            with self.assertRaises(contract.DetectorContractError):
                contract.verify_detector_metadata_and_pipeline(
                    json.dumps(metadata).encode(), pipeline_bytes, onnx_bytes
                )

    def test_zip_hash_mismatch_fails_before_extraction(self):
        with self.assertRaises(contract.DetectorContractError):
            contract.verify_detector_zip_bytes(b"not-the-approved-zip")

    def test_v03_contract_does_not_accept_unsafe_member_paths(self):
        with self.assertRaises(contract.DetectorContractError):
            contract._read_required_member(
                zipfile.ZipFile(io.BytesIO(_zip_with_unsafe_path())),
                "../outside.onnx",
            )


def _zip_with_unsafe_path() -> bytes:
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, "w") as archive:
        archive.writestr("../outside.onnx", b"not-safe")
    return stream.getvalue()


class ClassifierBundleParityTests(unittest.TestCase):
    def _fixture(self):
        class_order = [f"species_{i:02d}" for i in range(contract.CLASSIFIER_CLASS_COUNT)]
        classes = [
            {"class_index": i, "species_key": key, "display_name": f"鱼种{i:02d}"}
            for i, key in enumerate(class_order)
        ]
        tflite = b"\\x00\\x00\\x00\\x00TFL3" + b"test-model-payload"
        metadata = {
            "model_id": contract.CLASSIFIER_MODEL_ID,
            "dataset_id": contract.CLASSIFIER_DATASET_ID,
            "sha256": hashlib.sha256(tflite).hexdigest(),
            "input_size": 224,
            "num_classes": contract.CLASSIFIER_CLASS_COUNT,
            "class_names": class_order,
        }
        class_map = {
            "classes": [
                {"class_index": i, "species_key": key, "common_name_zh": f"鱼种{i:02d}"}
                for i, key in enumerate(class_order)
            ]
        }
        manifest = {
            "model_id": contract.CLASSIFIER_MODEL_ID,
            "input_shape": contract.CLASSIFIER_INPUT_SHAPE,
            "output_shape": contract.CLASSIFIER_OUTPUT_SHAPE,
            "num_classes": contract.CLASSIFIER_CLASS_COUNT,
        }
        stream = io.BytesIO()
        with zipfile.ZipFile(stream, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            archive.writestr(contract.CLASSIFIER_BUNDLE_TFLITE_MEMBER, tflite)
            archive.writestr(contract.CLASSIFIER_BUNDLE_METADATA_MEMBER, json.dumps(metadata))
            archive.writestr(contract.CLASSIFIER_BUNDLE_CLASS_MAP_MEMBER, json.dumps(class_map))
            archive.writestr(contract.CLASSIFIER_BUNDLE_MANIFEST_MEMBER, json.dumps(manifest))
        payload = stream.getvalue()
        snapshot = {
            "model_id": contract.CLASSIFIER_MODEL_ID,
            "dataset_id": contract.CLASSIFIER_DATASET_ID,
            "sha256": hashlib.sha256(tflite).hexdigest(),
            "class_map_sha256": "a" * 64,
            "tensor_contract_sha256": "b" * 64,
            "input_shape": contract.CLASSIFIER_INPUT_SHAPE,
            "output_shape": contract.CLASSIFIER_OUTPUT_SHAPE,
            "class_count": contract.CLASSIFIER_CLASS_COUNT,
            "class_order": class_order,
            "classes": classes,
            "release_id": contract.CLASSIFIER_PRODUCTION_RELEASE_ID,
            "release_tag": contract.CLASSIFIER_PRODUCTION_RELEASE_TAG,
            "release_assets": [
                {"id": contract.CLASSIFIER_PRODUCTION_TFLITE_ASSET_ID, "name": "fish_classifier_v0_2.tflite"}
            ],
        }
        return payload, snapshot

    def test_classifier_zip_tflite_and_semantic_class_map_match_production(self):
        payload, snapshot = self._fixture()
        digest = hashlib.sha256(payload).hexdigest()
        expected_assets = {
            contract.DETECTOR_ASSET_ID: {
                "name": contract.DETECTOR_ASSET_NAME,
                "size": contract.DETECTOR_ZIP_BYTES,
                "digest": f"sha256:{contract.DETECTOR_ZIP_SHA256}",
            },
            contract.CLASSIFIER_BUNDLE_ASSET_ID: {
                "name": contract.CLASSIFIER_BUNDLE_ASSET_NAME,
                "size": len(payload),
                "digest": f"sha256:{digest}",
            },
        }
        release = {
            "id": contract.DETECTOR_RELEASE_ID,
            "tag_name": contract.DETECTOR_RELEASE_TAG,
            "name": contract.DETECTOR_RELEASE_NAME,
            "draft": False,
            "assets": [
                {
                    "id": asset_id,
                    "name": expected["name"],
                    "size": expected["size"],
                    "digest": expected["digest"],
                    "url": f"https://api.github.com/repos/{contract.DETECTOR_RELEASE_REPOSITORY}/releases/assets/{asset_id}",
                }
                for asset_id, expected in expected_assets.items()
            ],
        }
        with patch.object(contract, "MODEL_ASSETS_EXPECTED_ASSETS", expected_assets), \
             patch.object(contract, "CLASSIFIER_BUNDLE_BYTES", len(payload)), \
             patch.object(contract, "CLASSIFIER_BUNDLE_SHA256", digest), \
             patch.object(contract, "CLASSIFIER_TFLITE_SHA256", snapshot["sha256"]):
            parity = contract.verify_classifier_bundle_parity(payload, snapshot, release)
        self.assertTrue(parity["class_map_semantic_match"])
        self.assertTrue(parity["tensor_contract_verified"])
        self.assertEqual(parity["source_tflite_sha256"], snapshot["sha256"])

    def test_classifier_zip_rejects_tflite_sha_mismatch(self):
        payload, snapshot = self._fixture()
        digest = hashlib.sha256(payload).hexdigest()
        expected_assets = {
            contract.DETECTOR_ASSET_ID: {
                "name": contract.DETECTOR_ASSET_NAME,
                "size": contract.DETECTOR_ZIP_BYTES,
                "digest": f"sha256:{contract.DETECTOR_ZIP_SHA256}",
            },
            contract.CLASSIFIER_BUNDLE_ASSET_ID: {
                "name": contract.CLASSIFIER_BUNDLE_ASSET_NAME,
                "size": len(payload),
                "digest": f"sha256:{digest}",
            },
        }
        release = {
            "id": contract.DETECTOR_RELEASE_ID,
            "tag_name": contract.DETECTOR_RELEASE_TAG,
            "name": contract.DETECTOR_RELEASE_NAME,
            "draft": False,
            "assets": [
                {
                    "id": asset_id,
                    "name": expected["name"],
                    "size": expected["size"],
                    "digest": expected["digest"],
                    "url": f"https://api.github.com/repos/{contract.DETECTOR_RELEASE_REPOSITORY}/releases/assets/{asset_id}",
                }
                for asset_id, expected in expected_assets.items()
            ],
        }
        with patch.object(contract, "MODEL_ASSETS_EXPECTED_ASSETS", expected_assets), \
             patch.object(contract, "CLASSIFIER_BUNDLE_BYTES", len(payload)), \
             patch.object(contract, "CLASSIFIER_BUNDLE_SHA256", digest), \
             patch.object(contract, "CLASSIFIER_TFLITE_SHA256", "0" * 64):
            with self.assertRaises(contract.DetectorContractError):
                contract.verify_classifier_bundle_parity(payload, snapshot, release)


if __name__ == "__main__":
    unittest.main()
