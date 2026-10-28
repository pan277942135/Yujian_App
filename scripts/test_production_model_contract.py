#!/usr/bin/env python3
import hashlib
import json
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from production_model_contract import (  # noqa: E402
    RELEASE_TAG,
    REQUIRED_ASSETS,
    ModelContractError,
    build_model_snapshot,
    verify_model_snapshot,
)


def make_release(model: bytes, metadata: bytes, class_map: bytes, tensor: bytes) -> dict:
    payloads = [model, metadata, class_map, tensor]
    assets = []
    for index, (name, payload) in enumerate(zip(REQUIRED_ASSETS, payloads), start=1):
        assets.append({
            "id": index,
            "name": name,
            "size": len(payload),
            "digest": "sha256:" + hashlib.sha256(payload).hexdigest(),
            "updated_at": "2026-09-30T00:00:00Z",
            "state": "uploaded",
            "url": f"https://api.github.com/repos/pan277942135/Yujian/releases/assets/{index}",
        })
    return {
        "id": 987654,
        "tag_name": RELEASE_TAG,
        "created_at": "2026-08-30T00:00:00Z",
        "published_at": "2026-08-30T00:00:00Z",
        "updated_at": "2026-09-30T00:00:00Z",
        "assets": assets,
    }


def fixture(class_count: int = 15):
    model = b"\x08\x00\x00\x00TFL3" + b"\x00" * 24
    classes = [
        {"class_index": index, "species_key": f"species_{index}", "common_name_zh": f"鱼种{index}"}
        for index in range(class_count)
    ]
    class_map = json.dumps({"classes": list(reversed(classes))}, ensure_ascii=False).encode("utf-8")
    metadata = json.dumps({
        "model_id": "MODEL_CROP_M1_v0.2",
        "dataset_id": "DS_M1_v1.1",
        "input_size": 224,
        "num_classes": class_count,
        "class_names": [row["species_key"] for row in classes],
        "published_filename": REQUIRED_ASSETS[0],
        "sha256": hashlib.sha256(model).hexdigest(),
        "created_at": "2026-09-30T00:00:00Z",
    }, ensure_ascii=False).encode("utf-8")
    tensor = json.dumps({
        "inputs": [{"name": "input", "shape": [1, 3, 224, 224], "dtype": "<class 'numpy.float32'>"}],
        "outputs": [{"name": "output", "shape": [1, class_count], "dtype": "<class 'numpy.float32'>"}],
    }).encode("utf-8")
    return model, metadata, class_map, tensor


class ProductionModelContractTest(unittest.TestCase):
    def test_dynamic_fifteen_class_release_builds_and_round_trips_snapshot(self):
        model, metadata, class_map, tensor = fixture(15)
        release = make_release(model, metadata, class_map, tensor)
        snapshot = build_model_snapshot(
            model=model,
            metadata_bytes=metadata,
            class_map_bytes=class_map,
            tensor_contract_bytes=tensor,
            release=release,
            build_sha="abc123",
        )
        self.assertEqual(15, snapshot["class_count"])
        self.assertEqual("MODEL_CROP_M1_v0.2", snapshot["model_id"])
        self.assertEqual("DS_M1_v1.1", snapshot["dataset_id"])
        self.assertEqual([f"species_{index}" for index in range(15)], snapshot["class_order"])
        self.assertEqual(snapshot, verify_model_snapshot(
            model=model,
            metadata_bytes=metadata,
            class_map_bytes=class_map,
            tensor_contract_bytes=tensor,
            snapshot=snapshot,
        ))

    def test_apk_trace_records_the_exact_packaged_model_identity(self):
        model, metadata, class_map, tensor = fixture(15)
        snapshot = build_model_snapshot(
            model=model,
            metadata_bytes=metadata,
            class_map_bytes=class_map,
            tensor_contract_bytes=tensor,
            release=make_release(model, metadata, class_map, tensor),
            build_sha="abc123",
        )
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            apk = root / "app-debug.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("assets/fish_classifier.tflite", model)
                archive.writestr("assets/fish_classifier_v0_2.metadata.json", metadata)
                archive.writestr("assets/class_map.json", class_map)
                archive.writestr("assets/tensor_contract.json", tensor)
                archive.writestr("assets/model_release_contract.json", json.dumps(snapshot).encode())
            output_dir = root / "trace"
            subprocess.run([
                sys.executable,
                str(Path(__file__).resolve().parent / "export_apk_model_trace.py"),
                "--apk", str(apk),
                "--output-dir", str(output_dir),
                "--build-sha", "abc123",
            ], check=True, capture_output=True, text=True)
            trace = json.loads((output_dir / "apk_model_trace.json").read_text(encoding="utf-8"))
            self.assertEqual("MODEL_CROP_M1_v0.2", trace["MODEL_ID"])
            self.assertEqual("DS_M1_v1.1", trace["DATASET_ID"])
            self.assertEqual(15, trace["MODEL_CLASS_COUNT"])
            self.assertEqual(snapshot["sha256"], trace["MODEL_SHA256"])
            self.assertEqual(snapshot["class_order"], trace["class_order"])

    def test_rejects_metadata_sha_mismatch(self):
        model, metadata, class_map, tensor = fixture()
        changed = json.loads(metadata)
        changed["sha256"] = "0" * 64
        metadata = json.dumps(changed).encode()
        with self.assertRaisesRegex(ModelContractError, "metadata/model SHA mismatch"):
            build_model_snapshot(
                model=model, metadata_bytes=metadata, class_map_bytes=class_map,
                tensor_contract_bytes=tensor, release=make_release(model, metadata, class_map, tensor),
            )

    def test_rejects_metadata_class_count_mismatch(self):
        model, metadata, class_map, tensor = fixture()
        changed = json.loads(metadata)
        changed["num_classes"] = 16
        metadata = json.dumps(changed).encode()
        with self.assertRaisesRegex(ModelContractError, "class count mismatch"):
            build_model_snapshot(
                model=model, metadata_bytes=metadata, class_map_bytes=class_map,
                tensor_contract_bytes=tensor, release=make_release(model, metadata, class_map, tensor),
            )

    def test_rejects_tensor_output_class_count_mismatch(self):
        model, metadata, class_map, tensor = fixture()
        changed = json.loads(tensor)
        changed["outputs"][0]["shape"] = [1, 16]
        tensor = json.dumps(changed).encode()
        with self.assertRaisesRegex(ModelContractError, "tensor output/class_map class count mismatch"):
            build_model_snapshot(
                model=model, metadata_bytes=metadata, class_map_bytes=class_map,
                tensor_contract_bytes=tensor, release=make_release(model, metadata, class_map, tensor),
            )

    def test_rejects_noncontiguous_class_indices_and_invalid_tflite_header(self):
        model, metadata, class_map, tensor = fixture()
        changed = json.loads(class_map)
        changed["classes"][0]["class_index"] = 17
        class_map = json.dumps(changed).encode()
        with self.assertRaisesRegex(ModelContractError, "contiguous"):
            build_model_snapshot(
                model=model, metadata_bytes=metadata, class_map_bytes=class_map,
                tensor_contract_bytes=tensor, release=make_release(model, metadata, class_map, tensor),
            )
        with self.assertRaisesRegex(ModelContractError, "TFLite FlatBuffer"):
            build_model_snapshot(
                model=b"not tflite", metadata_bytes=metadata, class_map_bytes=class_map,
                tensor_contract_bytes=tensor, release=make_release(b"not tflite", metadata, class_map, tensor),
            )

    def test_rejects_release_digest_mismatch(self):
        model, metadata, class_map, tensor = fixture()
        release = make_release(model, metadata, class_map, tensor)
        release["assets"][0]["digest"] = "sha256:" + "0" * 64
        with self.assertRaisesRegex(ModelContractError, "GitHub Release digest mismatch"):
            build_model_snapshot(
                model=model, metadata_bytes=metadata, class_map_bytes=class_map,
                tensor_contract_bytes=tensor, release=release,
            )


if __name__ == "__main__":
    unittest.main()
