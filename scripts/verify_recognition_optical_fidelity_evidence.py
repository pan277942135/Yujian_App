#!/usr/bin/env python3
"""Check measured optical-fidelity evidence; do not impose an unfrozen budget."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import statistics
import struct
from pathlib import Path


METRICS = "recognition_optical_fidelity_v3_1.json"
SCREENSHOTS = ["optical_fidelity_full.png", "optical_fidelity_lite.png"]
MIN_SAMPLES = 90
POLICY = "MEASURED_ONLY_NO_FROZEN_THRESHOLD"


def fail(message: str) -> None:
    raise SystemExit(message)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def check_png(path: Path) -> None:
    header = path.read_bytes()[:24]
    if len(header) != 24 or header[:8] != b"\x89PNG\r\n\x1a\n":
        fail(f"invalid screenshot: {path.name}")
    width, height = struct.unpack(">II", header[16:24])
    if width < 320 or height < 640:
        fail(f"screenshot dimensions are implausible: {path.name} ({width}x{height})")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--evidence-dir", type=Path, required=True)
    root = parser.parse_args().evidence_dir
    required = [METRICS, *SCREENSHOTS]
    missing = [name for name in required if not (root / name).is_file() or (root / name).stat().st_size == 0]
    if missing:
        fail("missing optical-fidelity evidence: " + ", ".join(missing))
    for name in SCREENSHOTS:
        check_png(root / name)

    try:
        evidence = json.loads((root / METRICS).read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        fail(f"invalid optical-fidelity metrics: {error}")
    if evidence.get("contract_version") != "recognition_processing_state_machine_v3_1":
        fail("optical-fidelity evidence uses the wrong processing contract")
    if evidence.get("phase") != "CLASSIFYING":
        fail("optical-fidelity evidence was not captured in CLASSIFYING")
    if evidence.get("budget_policy") != POLICY:
        fail("optical-fidelity report must explicitly avoid an unfrozen threshold")
    if evidence.get("result_ready") is not False:
        fail("optical-fidelity scene must remain live during measurement")
    method = str(evidence.get("measurement_method", ""))
    if "Choreographer.FrameCallback" not in method or "RecognitionProcessingScene" not in method:
        fail("frame timing must come from a live production scene and Choreographer callbacks")
    if "CLASSIFYING" not in evidence.get("presented_phases", []):
        fail("the production scene did not present CLASSIFYING during the measurement")
    profiles = evidence.get("profiles")
    if not isinstance(profiles, list) or [row.get("profile") for row in profiles] != ["FULL", "LITE"]:
        fail("optical-fidelity evidence must contain FULL then LITE measurements")

    for row in profiles:
        if row.get("phase") != "CLASSIFYING" or row.get("budget_policy") != POLICY:
            fail(f"{row.get('profile')} measurement metadata is inconsistent")
        intervals = row.get("samples_ms")
        if not isinstance(intervals, list) or len(intervals) < MIN_SAMPLES:
            fail(f"{row.get('profile')} needs at least {MIN_SAMPLES} raw frame intervals")
        if row.get("sample_count") != len(intervals):
            fail(f"{row.get('profile')} frame sample count does not match raw intervals")
        if row.get("motion_sample_count", 0) < 20:
            fail(f"{row.get('profile')} production motion clock did not emit enough samples")
        if not math.isfinite(float(row.get("refresh_rate_hz", 0))) or row["refresh_rate_hz"] <= 0:
            fail(f"{row.get('profile')} refresh rate is invalid")
        clean = [float(value) for value in intervals]
        if any(not math.isfinite(value) or value <= 0 for value in clean):
            fail(f"{row.get('profile')} has invalid frame intervals")
        summary = row.get("interval_ms", {})
        ordered = sorted(clean)
        expected = {
            "mean": statistics.fmean(clean),
            "median": statistics.median(clean),
            "p95": ordered[math.ceil(len(ordered) * .95) - 1],
            "max": ordered[-1],
        }
        for key, computed in expected.items():
            value = float(summary.get(key, float("nan")))
            if not math.isfinite(value) or abs(value - computed) > .01:
                fail(f"{row.get('profile')} {key} summary does not match raw intervals")
        if summary.get("frames_over_32ms") != sum(value > 32.0 for value in clean):
            fail(f"{row.get('profile')} over-32ms count is inconsistent")

    artifacts = [
        {"path": name, "bytes": (root / name).stat().st_size, "sha256": sha256(root / name)}
        for name in required
    ]
    report = {
        "contract_version": "Recognition Optical Fidelity Evidence V3.1",
        "classification": "MEASURED_NO_FROZEN_THRESHOLD",
        "budget_policy": POLICY,
        "artifacts": artifacts,
    }
    (root / "recognition_optical_fidelity_manifest_v3_1.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print("Recognition optical-fidelity evidence: RECORDED (no frozen frame-time threshold)")


if __name__ == "__main__":
    main()
