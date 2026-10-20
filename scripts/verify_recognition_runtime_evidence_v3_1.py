#!/usr/bin/env python3
"""Validate Recognition V3.1 runtime evidence without inventing performance limits."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import struct
from pathlib import Path


REQUIRED = [
    "02_image_recognizing_late.png",
    "03_fish_located.png",
    "04_species_recognizing.png",
    "05_result_high.png",
    "06_result_medium.png",
    "07_result_low.png",
    "08_error_no_fish.png",
    "09_error_image_quality.png",
    "10_issue_technical_failure.png",
    "08_issue_no_fish.png",
    "09_issue_image_quality.png",
    "06_edge_field_crop.png",
    "07_fish_focus_crop.png",
    "08_contour_closeup.png",
    "level_a_real_contour.png",
    "recognition_processing_v3_1.mp4",
    "recognition_processing_timing_v3_1.txt",
    "recognition_motion_trace_v3_1.json",
    "quality_full.png",
    "quality_balanced.png",
    "quality_lite.png",
    "reduce_motion_static.png",
    "degradation_d0_d4_contact_sheet.png",
    "recognition_accessibility_trace_v3_1.json",
    "recognition_visual_qa_v1_3.json",
    "recognition_production_flow_trace_v3_1.json",
    "fish_focus_bbox_mapping.json",
    "recognition_visual_parity_v1_2.json",
    "recognition_visual_parity_contact_sheet_v1_2.png",
]
MANIFEST = "runtime_evidence_manifest_v3_1.json"
PHASES = ["CAPTURED", "DETECTING", "OUTLINE", "CLASSIFYING"]
PRESENTATION = PHASES + ["RESULT_READY"]
MINIMUM_MS = [220, 320, 350, 250]


def fail(message: str) -> None:
    raise SystemExit(message)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def read_json(path: Path) -> dict:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        fail(f"invalid JSON evidence {path.name}: {error}")
    if not isinstance(value, dict):
        fail(f"JSON evidence root must be an object: {path.name}")
    return value


def check_png(path: Path) -> None:
    with path.open("rb") as handle:
        header = handle.read(24)
    if len(header) != 24 or header[:8] != b"\x89PNG\r\n\x1a\n":
        fail(f"invalid PNG evidence: {path.name}")
    width, height = struct.unpack(">II", header[16:24])
    if width < 64 or height < 64:
        fail(f"PNG dimensions are implausible: {path.name} ({width}x{height})")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--evidence-dir", type=Path, required=True)
    root = parser.parse_args().evidence_dir

    missing = [name for name in REQUIRED if not (root / name).is_file() or (root / name).stat().st_size == 0]
    if missing:
        fail("missing runtime evidence: " + ", ".join(missing))
    for name in REQUIRED:
        if name.endswith(".png"):
            check_png(root / name)

    motion = read_json(root / "recognition_motion_trace_v3_1.json")
    samples = motion.get("samples")
    if not isinstance(samples, list) or len(samples) < 10:
        fail("motion trace needs at least 10 runtime samples")
    previous_time = -1
    observed_phases: set[str] = set()
    for sample in samples:
        try:
            timestamp = int(sample["uptime_ms"])
            detail_time = int(sample["detail_motion_time_ms"])
            offset = float(sample["segment_offset"])
            speed = float(sample["segment_speed"])
            strength = float(sample["state_strength"])
            phase = str(sample["phase"])
        except (KeyError, TypeError, ValueError) as error:
            fail(f"malformed motion sample: {error}")
        if timestamp < previous_time:
            fail("motion trace timestamps moved backwards")
        if not (0.0 <= offset < 1.0 and 0.0 <= speed <= 1.0 and 0.0 <= strength <= 1.0):
            fail("motion trace contains an out-of-range value")
        if detail_time != timestamp:
            fail("ambient and detail motion do not share the live uptime clock")
        if sample.get("reduce_motion") and speed > 0.001:
            fail("segment travel continued under Reduce Motion")
        if sample.get("quality") not in {"FULL", "BALANCED", "LITE"}:
            fail("motion trace contains an unknown quality profile")
        observed_phases.add(phase)
        previous_time = timestamp
    if not set(PHASES).issubset(observed_phases):
        fail(f"motion trace misses a live phase: {sorted(observed_phases)}")

    flow = read_json(root / "recognition_production_flow_trace_v3_1.json")
    expected_pipeline = PHASES + ["RESULT"]
    if flow.get("contract_version") != "recognition_processing_state_machine_v3_1":
        fail("production flow uses the wrong contract version")
    if flow.get("visual_state_sequence") != PRESENTATION:
        fail(f"production visual-state sequence mismatch: {flow.get('visual_state_sequence')}")
    if flow.get("pipeline_phases") != expected_pipeline:
        fail(f"production pipeline trace mismatch: {flow.get('pipeline_phases')}")
    events = flow.get("presentation_events", [])
    if [item.get("state") for item in events] != PRESENTATION:
        fail("production presentation trace is not the V3.1 five-state sequence")
    event_times = [int(item.get("at_ms", -1)) for item in events]
    if any(now < 0 for now in event_times) or event_times != sorted(event_times):
        fail("production presentation timestamps are invalid")
    runtime_intervals = [later - earlier for earlier, later in zip(event_times, event_times[1:])]
    for observed, minimum in zip(runtime_intervals, MINIMUM_MS):
        # Compose presentation markers are emitted in adjacent effects. Keep the
        # existing 50 ms observation tolerance; unit tests assert exact gates.
        if observed < minimum - 50:
            fail(f"runtime presentation beat below V3.1 minimum: {observed}ms < {minimum}ms")
    if flow.get("result_ready") is not True:
        fail("production flow did not reach a ready Result")
    source = flow.get("source", {})
    if int(source.get("width", 0)) <= 0 or int(source.get("height", 0)) <= 0:
        fail("production flow is missing source dimensions")
    if flow.get("quality_gate", {}).get("classifier_eligible") is not True:
        fail("production flow quality gate did not admit the fixture")
    if flow.get("focus", {}).get("level") != "A":
        fail("production flow did not record Level A focus")

    timing = (root / "recognition_processing_timing_v3_1.txt").read_text(encoding="utf-8")
    if "RESULT_READY=immediate" not in timing or re.search(r"\bRESOLVE\b|\b200\s*ms\b", timing, re.IGNORECASE):
        fail("timing evidence contains a legacy Resolve delay")
    for label, minimum in zip(PHASES, MINIMUM_MS):
        if f"{label}>={minimum}ms" not in timing:
            fail(f"timing contract does not declare {label}>={minimum}ms")

    accessibility = read_json(root / "recognition_accessibility_trace_v3_1.json")
    expected_degradation = {
        "D0": "FULL+A",
        "D1": "BALANCED+A",
        "D2": "LITE+A",
        "D3": "LITE+B",
        "D4": "LITE+C",
    }
    if accessibility.get("degradation") != expected_degradation:
        fail("degradation trace does not match the D0-D4 V3.1 mapping")

    visual_qa = read_json(root / "recognition_visual_qa_v1_3.json")
    expected_findings = {"F01", "F02", "F03", "F04", "F05", "F06"}
    findings = visual_qa.get("findings", {})
    if set(findings) != expected_findings:
        fail("visual QA evidence must include the complete F01-F06 taxonomy")
    for code, finding in findings.items():
        if finding.get("status") not in {"UNREVIEWED", "PASS", "FAIL"}:
            fail(f"invalid visual QA status for {code}")
        if finding.get("status") == "FAIL" and not finding.get("evidence"):
            fail(f"visual QA failure {code} has no evidence reference")

    bbox = read_json(root / "fish_focus_bbox_mapping.json")
    box = bbox.get("detector_bbox_normalized", {})
    try:
        x1, y1, x2, y2 = (float(box[key]) for key in ("x1", "y1", "x2", "y2"))
    except (KeyError, TypeError, ValueError) as error:
        fail(f"bbox mapping is malformed: {error}")
    if not (0 <= x1 < x2 <= 1 and 0 <= y1 < y2 <= 1):
        fail("bbox mapping has invalid normalized coordinates")

    parity = read_json(root / "recognition_visual_parity_v1_2.json")
    if parity.get("classification") != "PASS" or len(parity.get("states", [])) != 8:
        fail("frozen visual parity did not pass all eight active reference states")

    artifacts = [
        {"path": name, "bytes": (root / name).stat().st_size, "sha256": sha256(root / name)}
        for name in REQUIRED
    ]
    manifest = {
        "contract_version": "Recognition Runtime Evidence V3.1",
        "classification": "PASS",
        "artifacts": artifacts,
        "runtime_gate_result": "runtime_gate_result_v1_2.json",
    }
    (root / MANIFEST).write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"Recognition V3.1 runtime evidence: PASS ({len(artifacts)} artifacts)")


if __name__ == "__main__":
    main()
