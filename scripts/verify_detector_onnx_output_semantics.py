#!/usr/bin/env python3
"""Inspect and run the approved DET_FISH v0.3 ONNX graph on CPU.

This is deliberately independent of detector metadata declarations. It records
the actual graph path feeding the output and a deterministic reference tensor
for the Android instrumentation test. A graph-semantic verdict remains
REVIEW_REQUIRED until the exported graph structure and numerical output have
been reviewed against the frozen YOLOX decode contract.
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import json
from collections import Counter, deque
from pathlib import Path
from typing import Any

import numpy as np
import onnx
import onnxruntime as ort
from PIL import Image

from det_fish_v03_contract import (
    ANDROID_DETECTOR_MODEL_FILE,
    DETECTOR_INPUT_SHAPE,
    DETECTOR_MODEL_VERSION,
    DETECTOR_ONNX_SHA256,
    DETECTOR_OUTPUT_SHAPE,
)

ROOT = Path(__file__).resolve().parents[1]
MODEL_PATH = ROOT / "app" / "src" / "main" / "assets" / ANDROID_DETECTOR_MODEL_FILE
FIXTURE_PATH = ROOT / "app" / "src" / "androidTest" / "assets" / "detector_case_b_night_flash.jpg"
REFERENCE_PATH = ROOT / "app" / "src" / "androidTest" / "assets" / "detector_case_b_v03_reference.json"
REPORT_PATH = ROOT / "build" / "model-evidence" / "detector_onnx_output_semantics.json"
FIXTURE_SHA256 = "a7ed3bb0410c191364f6d853b4078aebdc2f3b9baa4da34b7b3f6e8e0e93a56b"
INPUT_SIZE = 416
PAD_VALUE = 114
NMS_IOU = 0.45


def sha256(payload: bytes) -> str:
    return hashlib.sha256(payload).hexdigest()


def shape_map(model: onnx.ModelProto) -> dict[str, list[int | str]]:
    inferred = onnx.shape_inference.infer_shapes(model)
    values = list(inferred.graph.input) + list(inferred.graph.value_info) + list(inferred.graph.output)
    result: dict[str, list[int | str]] = {}
    for value in values:
        if not value.type.HasField("tensor_type"):
            continue
        dims: list[int | str] = []
        for dim in value.type.tensor_type.shape.dim:
            if dim.dim_value > 0:
                dims.append(int(dim.dim_value))
            elif dim.dim_param:
                dims.append(str(dim.dim_param))
            else:
                dims.append("?")
        result[value.name] = dims
    return result


def node_attribute(node: onnx.NodeProto, name: str, default: Any = None) -> Any:
    for attribute in node.attribute:
        if attribute.name == name:
            return onnx.helper.get_attribute_value(attribute)
    return default


def inspect_graph(model: onnx.ModelProto) -> dict[str, Any]:
    graph = model.graph
    shapes = shape_map(model)
    producers = {output: node for node in graph.node for output in node.output if output}
    output_name = graph.output[0].name

    visited: set[str] = set()
    ordered_nodes: list[onnx.NodeProto] = []
    queue: deque[str] = deque([output_name])
    while queue:
        tensor_name = queue.popleft()
        if tensor_name in visited:
            continue
        visited.add(tensor_name)
        producer = producers.get(tensor_name)
        if producer is None:
            continue
        ordered_nodes.append(producer)
        queue.extend(name for name in producer.input if name and name not in visited)

    counts = Counter(node.op_type for node in ordered_nodes)
    relevant = {
        "Add", "Concat", "Div", "Exp", "Gather", "Mul", "Pow", "Reshape",
        "Sigmoid", "Slice", "Split", "Sub", "Transpose", "Unsqueeze",
    }
    node_rows = []
    for index, node in enumerate(ordered_nodes):
        if node.op_type not in relevant:
            continue
        node_rows.append({
            "op_type": node.op_type,
            "name": node.name,
            "inputs": [
                {"name": name, "shape": shapes.get(name)} for name in node.input if name
            ],
            "outputs": [
                {"name": name, "shape": shapes.get(name)} for name in node.output if name
            ],
            "attributes": {
                attribute.name: (
                    onnx.helper.get_attribute_value(attribute).decode("utf-8")
                    if isinstance(onnx.helper.get_attribute_value(attribute), bytes)
                    else onnx.helper.get_attribute_value(attribute).tolist()
                    if hasattr(onnx.helper.get_attribute_value(attribute), "tolist")
                    else onnx.helper.get_attribute_value(attribute)
                )
                for attribute in node.attribute
                if attribute.name in {"axis", "perm", "keepdims", "to"}
            },
        })

    output_producer = producers.get(output_name)
    concat_rows = []
    for node in ordered_nodes:
        if node.op_type != "Concat":
            continue
        axis = int(node_attribute(node, "axis", -1))
        output_shape = shapes.get(node.output[0]) if node.output else None
        if output_shape == DETECTOR_OUTPUT_SHAPE or (axis in (2, -1) and output_shape and output_shape[-1] == 6):
            concat_rows.append({
                "name": node.name,
                "axis": axis,
                "output": node.output[0] if node.output else None,
                "output_shape": output_shape,
                "inputs": [
                    {
                        "name": name,
                        "shape": shapes.get(name),
                        "producer_op": producers[name].op_type if name in producers else None,
                    }
                    for name in node.input
                ],
            })

    return {
        "input": {
            "name": graph.input[0].name if graph.input else None,
            "shape": shapes.get(graph.input[0].name) if graph.input else None,
            "dtype": "float32" if graph.input and graph.input[0].type.tensor_type.elem_type == onnx.TensorProto.FLOAT else "non-float32",
        },
        "output": {
            "name": output_name,
            "shape": shapes.get(output_name),
            "dtype": "float32" if graph.output[0].type.tensor_type.elem_type == onnx.TensorProto.FLOAT else "non-float32",
            "producer_op": output_producer.op_type if output_producer else None,
            "producer_name": output_producer.name if output_producer else None,
        },
        "ancestor_node_count": len(ordered_nodes),
        "ancestor_operator_counts": dict(sorted(counts.items())),
        "sigmoid_ancestor_count": counts.get("Sigmoid", 0),
        "exp_ancestor_count": counts.get("Exp", 0),
        "add_ancestor_count": counts.get("Add", 0),
        "mul_ancestor_count": counts.get("Mul", 0),
        "decoded_output_concat_candidates": concat_rows,
        "relevant_output_ancestor_nodes": node_rows,
        "graph_semantics_verdict": "REVIEW_REQUIRED",
    }


def preprocess_rgb_to_bgr_planar(image_path: Path) -> tuple[np.ndarray, dict[str, Any]]:
    with Image.open(image_path) as opened:
        image = opened.convert("RGB")
    source_width, source_height = image.size
    scale = min(INPUT_SIZE / float(source_width), INPUT_SIZE / float(source_height))
    draw_width = max(1, int(source_width * scale))
    draw_height = max(1, int(source_height * scale))
    resized = image.resize((draw_width, draw_height), Image.Resampling.BILINEAR)
    canvas = np.full((INPUT_SIZE, INPUT_SIZE, 3), PAD_VALUE, dtype=np.uint8)
    canvas[:draw_height, :draw_width] = np.asarray(resized, dtype=np.uint8)
    tensor = np.ascontiguousarray(canvas[:, :, ::-1].transpose(2, 0, 1), dtype=np.float32)[None, ...]
    metadata = {
        "source_width": source_width,
        "source_height": source_height,
        "scale": scale,
        "draw_width": draw_width,
        "draw_height": draw_height,
        "input_shape": list(tensor.shape),
        "input_dtype": str(tensor.dtype),
        "input_layout": "BGR_PLANAR",
        "pad_value": PAD_VALUE,
        "input_min": float(tensor.min()),
        "input_max": float(tensor.max()),
        "input_sha256": sha256(tensor.astype("<f4", copy=False).tobytes()),
    }
    return tensor, metadata


def run_cpu_inference(model_path: Path, fixture_path: Path) -> tuple[np.ndarray, dict[str, Any], dict[str, Any]]:
    model_bytes = model_path.read_bytes()
    model_sha = sha256(model_bytes)
    if model_sha != DETECTOR_ONNX_SHA256:
        raise SystemExit(f"ONNX SHA mismatch: {model_sha} != {DETECTOR_ONNX_SHA256}")
    fixture_sha = sha256(fixture_path.read_bytes())
    if fixture_sha != FIXTURE_SHA256:
        raise SystemExit(f"fixture SHA mismatch: {fixture_sha} != {FIXTURE_SHA256}")

    graph_model = onnx.load_model_from_string(model_bytes)
    onnx.checker.check_model(graph_model)
    graph_evidence = inspect_graph(graph_model)

    input_tensor, preprocess = preprocess_rgb_to_bgr_planar(fixture_path)
    if list(input_tensor.shape) != DETECTOR_INPUT_SHAPE:
        raise SystemExit(f"preprocessed input shape mismatch: {input_tensor.shape}")
    if input_tensor.dtype != np.float32:
        raise SystemExit(f"preprocessed input dtype mismatch: {input_tensor.dtype}")

    session = ort.InferenceSession(model_bytes, providers=["CPUExecutionProvider"])
    inputs = session.get_inputs()
    outputs = session.get_outputs()
    if len(inputs) != 1 or len(outputs) != 1:
        raise SystemExit(f"ONNX Runtime expected one input/output; got {len(inputs)}/{len(outputs)}")
    if inputs[0].name != graph_evidence["input"]["name"] or outputs[0].name != graph_evidence["output"]["name"]:
        raise SystemExit("ONNX Runtime tensor names disagree with the parsed graph")
    raw = session.run([outputs[0].name], {inputs[0].name: input_tensor})[0]
    if list(raw.shape) != DETECTOR_OUTPUT_SHAPE or raw.dtype != np.float32:
        raise SystemExit(f"ONNX Runtime output contract mismatch: {raw.shape} {raw.dtype}")
    if not np.isfinite(raw).all():
        raise SystemExit("ONNX Runtime output contains NaN/Inf")

    rows = raw[0]
    confidence = rows[:, 4] * rows[:, 5]
    order = np.argsort(confidence)[::-1][:10]
    top_rows = []
    for index in order:
        cx, cy, width, height, objectness, fish_probability = (float(v) for v in rows[index, :6])
        left = (cx - width / 2.0) / preprocess["scale"] / preprocess["source_width"]
        top = (cy - height / 2.0) / preprocess["scale"] / preprocess["source_height"]
        right = (cx + width / 2.0) / preprocess["scale"] / preprocess["source_width"]
        bottom = (cy + height / 2.0) / preprocess["scale"] / preprocess["source_height"]
        top_rows.append({
            "row": int(index),
            "cx": cx, "cy": cy, "width": width, "height": height,
            "objectness": objectness, "fish_probability": fish_probability,
            "confidence_objectness_times_class": objectness * fish_probability,
            "bbox_normalized_unclipped": [left, top, right, bottom],
        })

    columns = {
        str(index): {
            "min": float(np.min(rows[:, index])),
            "max": float(np.max(rows[:, index])),
            "p50": float(np.percentile(rows[:, index], 50)),
            "p90": float(np.percentile(rows[:, index], 90)),
            "p99": float(np.percentile(rows[:, index], 99)),
        }
        for index in range(6)
    }
    probability_range = bool(
        np.all(rows[:, 4] >= -1e-6) and np.all(rows[:, 4] <= 1.0 + 1e-6)
        and np.all(rows[:, 5] >= -1e-6) and np.all(rows[:, 5] <= 1.0 + 1e-6)
    )
    positive_wh_ratio = float(np.mean((rows[:, 2] > 0) & (rows[:, 3] > 0)))
    center_in_frame_ratio = float(np.mean(
        (rows[:, 0] >= 0) & (rows[:, 0] <= INPUT_SIZE)
        & (rows[:, 1] >= 0) & (rows[:, 1] <= INPUT_SIZE)
    ))
    tensor_bytes = raw.astype("<f4", copy=False).tobytes()
    inference = {
        "provider": "CPUExecutionProvider",
        "input_name": inputs[0].name,
        "output_name": outputs[0].name,
        "output_shape": list(raw.shape),
        "output_dtype": str(raw.dtype),
        "output_tensor_sha256": sha256(tensor_bytes),
        "output_columns": columns,
        "objectness_and_class_probability_in_unit_interval": probability_range,
        "positive_width_height_ratio": positive_wh_ratio,
        "center_coordinates_inside_416_frame_ratio": center_in_frame_ratio,
        "top10": top_rows,
        "output_semantics_candidate": (
            "decoded_xywh_objectness_class_probability"
            if probability_range and positive_wh_ratio > 0.99 and center_in_frame_ratio > 0.5
            else "REVIEW_REQUIRED"
        ),
    }
    reference = {
        "schema_version": 1,
        "model_version": DETECTOR_MODEL_VERSION,
        "onnx_sha256": model_sha,
        "fixture_path": str(fixture_path.relative_to(ROOT)),
        "fixture_sha256": fixture_sha,
        "preprocess": preprocess,
        "output_shape": list(raw.shape),
        "output_dtype": str(raw.dtype),
        "output_tensor_sha256": inference["output_tensor_sha256"],
        "output_tensor_f32_le_base64": base64.b64encode(tensor_bytes).decode("ascii"),
        "top10": top_rows,
    }
    return raw, {"graph": graph_evidence, "preprocess": preprocess, "inference": inference}, reference


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", type=Path, default=MODEL_PATH)
    parser.add_argument("--fixture", type=Path, default=FIXTURE_PATH)
    parser.add_argument("--write-android-reference", action="store_true")
    args = parser.parse_args()

    raw, report, reference = run_cpu_inference(args.model, args.fixture)
    report["model_version"] = DETECTOR_MODEL_VERSION
    report["onnx_sha256"] = DETECTOR_ONNX_SHA256
    report["onnxruntime_version"] = ort.__version__
    report["onnx_version"] = onnx.__version__
    REPORT_PATH.parent.mkdir(parents=True, exist_ok=True)
    REPORT_PATH.write_text(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    if args.write_android_reference:
        REFERENCE_PATH.parent.mkdir(parents=True, exist_ok=True)
        REFERENCE_PATH.write_text(json.dumps(reference, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")

    print("DET_FISH_V03_ONNX_CPU_INFERENCE_PASS")
    print("ONNX_GRAPH_EVIDENCE=" + json.dumps(report["graph"], ensure_ascii=False, sort_keys=True))
    print("ONNX_CPU_INFERENCE=" + json.dumps(report["inference"], ensure_ascii=False, sort_keys=True))
    print("PREPROCESS_EVIDENCE=" + json.dumps(report["preprocess"], ensure_ascii=False, sort_keys=True))
    print(f"ANDROID_REFERENCE_PATH={REFERENCE_PATH if args.write_android_reference else 'NOT_WRITTEN'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
