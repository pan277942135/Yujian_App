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
            detector = archive.read("assets/fish_detector_yolox_nano_v0_1.onnx")
            detector_metadata_bytes = archive.read("assets/detector_metadata.json")
            slim_sam_encoder = archive.read("assets/slimsam/vision_encoder_quantized.onnx")
            slim_sam_decoder = archive.read("assets/slimsam/prompt_encoder_mask_decoder_quantized.onnx")
        snapshot = json.loads(snapshot_bytes.decode("utf-8"))
        detector_metadata = json.loads(detector_metadata_bytes.decode("utf-8"))
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
    detector_sha = sha256_bytes(detector)
    if detector_metadata.get("model_version") != "DET_FISH_v0.1" or detector_metadata.get("onnx_sha256") != detector_sha:
        raise SystemExit("APK_MODEL_TRACE_FAILED: packaged Detector metadata does not match the APK ONNX bytes")
    if int(detector_metadata.get("onnx_bytes") or 0) != len(detector):
        raise SystemExit("APK_MODEL_TRACE_FAILED: packaged Detector byte count does not match the APK ONNX bytes")
    args.output_dir.mkdir(parents=True, exist_ok=True)
    (args.output_dir / ANDROID_SNAPSHOT_FILE).write_bytes(snapshot_bytes)
    trace = {
        "build_sha": args.build_sha,
        "apk_sha256": sha256_bytes(args.apk.read_bytes()),
        "apk_bytes": args.apk.stat().st_size,
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
        "detector": {
            "model_id": detector_metadata["model_version"],
            "sha256": detector_sha,
            "bytes": len(detector),
            "input_shape": (detector_metadata.get("onnx") or {}).get("input_shape"),
            "output_shape": (detector_metadata.get("onnx") or {}).get("output_shape"),
            "metadata_sha256": sha256_bytes(detector_metadata_bytes),
        },
        "slim_sam": {
            "model_id": "SlimSAM quantized ONNX",
            "encoder": {
                "asset": "assets/slimsam/vision_encoder_quantized.onnx",
                "sha256": sha256_bytes(slim_sam_encoder),
                "bytes": len(slim_sam_encoder),
            },
            "decoder": {
                "asset": "assets/slimsam/prompt_encoder_mask_decoder_quantized.onnx",
                "sha256": sha256_bytes(slim_sam_decoder),
                "bytes": len(slim_sam_decoder),
            },
        },
    }
    trace_path = args.output_dir / "apk_model_trace.json"
    trace_path.write_text(json.dumps(trace, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print("APK_MODEL_TRACE_PASS")
    print(json.dumps(trace, ensure_ascii=False, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    raise SystemExit(main())
