#!/usr/bin/env python3
"""Validate runtime-generated Recognition V1.2 evidence and write its manifest."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

REQUIRED = [
    "01_image_recognizing_early.png",
    "02_image_recognizing_late.png",
    "03_fish_located.png",
    "04_species_recognizing.png",
    "05_result_high.png",
    "06_result_medium.png",
    "07_result_low.png",
    "08_issue_no_fish.png",
    "09_issue_image_quality.png",
    "10_issue_technical_failure.png",
    "recognition_processing_v1_2.mp4",
    "recognition_processing_timing_v1_2.txt",
    "recognition_motion_trace_v1_2.json",
    "quality_full.png",
    "quality_balanced.png",
    "quality_lite.png",
    "reduce_motion_static.png",
    "degradation_d0_d4_contact_sheet.png",
    "recognition_accessibility_trace_v1_2.json",
    "recognition_visual_qa_v1_3.json",
    "recognition_production_flow_trace_v1_2.json",
    "level_a_real_contour.png",
    "fish_focus_bbox_mapping.json",
]
MANIFEST = "runtime_evidence_manifest_v1_2.json"


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--evidence-dir", type=Path, required=True)
    args = parser.parse_args()
    root = args.evidence_dir

    missing = [name for name in REQUIRED if not (root / name).is_file() or (root / name).stat().st_size == 0]
    if missing:
        raise SystemExit("missing runtime evidence: " + ", ".join(missing))

    motion = json.loads((root / "recognition_motion_trace_v1_2.json").read_text(encoding="utf-8"))
    samples = motion.get("samples")
    if not isinstance(samples, list) or len(samples) < 10:
        raise SystemExit("motion trace needs at least 10 runtime samples")
    previous_time = -1
    observed_phases: set[str] = set()
    resolve_samples: list[dict[str, object]] = []
    for sample in samples:
        timestamp = int(sample["uptime_ms"])
        offset = float(sample["segment_offset"])
        speed = float(sample["segment_speed"])
        strength = float(sample["state_strength"])
        resolve = float(sample["resolve_strength"])
        if timestamp < previous_time:
            raise SystemExit("motion trace timestamps moved backwards")
        if not (
            0.0 <= offset < 1.0
            and 0.0 <= speed <= 1.0
            and 0.0 <= strength <= 1.0
            and 0.0 <= resolve <= 1.0
        ):
            raise SystemExit("motion trace contains an out-of-range value")
        if (sample.get("reduce_motion") or resolve < 1.0) and speed > 0.001:
            raise SystemExit("SegmentSpeed did not freeze for Reduce Motion or Resolve")
        if resolve < 1.0:
            resolve_samples.append(sample)
        observed_phases.add(sample["phase"])
        previous_time = timestamp
    if not {"CAPTURED", "OUTLINE", "CLASSIFYING"}.issubset(observed_phases):
        raise SystemExit(f"motion trace misses a presented phase: {sorted(observed_phases)}")
    if not resolve_samples:
        raise SystemExit("motion trace misses the Resolve fade")
    resolve_motion_times = {int(sample["detail_motion_time_ms"]) for sample in resolve_samples}
    if len(resolve_motion_times) != 1:
        raise SystemExit("nodes or particles continued moving during Resolve")

    flow = json.loads((root / "recognition_production_flow_trace_v1_2.json").read_text(encoding="utf-8"))
    expected_pipeline = ["CAPTURED", "DETECTING", "OUTLINE", "CLASSIFYING", "RESULT"]
    if flow.get("pipeline_phases") != expected_pipeline:
        raise SystemExit(f"production pipeline trace mismatch: {flow.get('pipeline_phases')}")
    events = flow.get("presentation_events", [])
    if [item.get("state") for item in events] != [
        "IMAGE_RECOGNIZING", "FISH_LOCATED", "SPECIES_RECOGNIZING", "RESULT"
    ]:
        raise SystemExit("presentation trace is not the three-state product flow")
    if flow.get("result_ready") is not True:
        raise SystemExit("production flow did not reach a ready Result")

    accessibility = json.loads(
        (root / "recognition_accessibility_trace_v1_2.json").read_text(encoding="utf-8")
    )
    if accessibility.get("degradation", {}).get("D2") != "LITE+A":
        raise SystemExit("degradation trace does not preserve Fish Focus A at D2")
    if accessibility.get("degradation", {}).get("D3") != "LITE+B":
        raise SystemExit("degradation trace does not map D3 to Fish Focus B")
    if accessibility.get("degradation", {}).get("D4") != "LITE+C":
        raise SystemExit("degradation trace does not map D4 to Fish Focus C")

    visual_qa = json.loads(
        (root / "recognition_visual_qa_v1_3.json").read_text(encoding="utf-8")
    )
    expected_findings = {"F01", "F02", "F03", "F04", "F05", "F06"}
    if set(visual_qa.get("findings", {})) != expected_findings:
        raise SystemExit("visual QA evidence must include the complete F01-F06 taxonomy")
    for code, finding in visual_qa["findings"].items():
        if finding.get("status") not in {"UNREVIEWED", "PASS", "FAIL"}:
            raise SystemExit(f"invalid visual QA status for {code}")
        if finding.get("status") == "FAIL" and not finding.get("evidence"):
            raise SystemExit(f"visual QA failure {code} has no evidence reference")

    bbox = json.loads((root / "fish_focus_bbox_mapping.json").read_text(encoding="utf-8"))
    box = bbox.get("detector_bbox_normalized", {})
    if not (0 <= box.get("x1", -1) < box.get("x2", 2) <= 1):
        raise SystemExit("bbox mapping has invalid horizontal coordinates")
    if not (0 <= box.get("y1", -1) < box.get("y2", 2) <= 1):
        raise SystemExit("bbox mapping has invalid vertical coordinates")

    artifacts = [
        {"path": name, "bytes": (root / name).stat().st_size, "sha256": sha256(root / name)}
        for name in REQUIRED
    ]
    manifest = {
        "contract_version": "Recognition Runtime Evidence V1.2",
        "classification": "PASS",
        "artifacts": artifacts,
        "runtime_gate_result": "runtime_gate_result_v1_2.json",
    }
    (root / MANIFEST).write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"Recognition V1.2 runtime evidence: PASS ({len(artifacts)} artifacts)")


if __name__ == "__main__":
    main()
