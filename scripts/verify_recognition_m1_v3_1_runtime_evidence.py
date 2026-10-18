#!/usr/bin/env python3
"""Validate M1 Recognition V3.1 runtime evidence and exact-source provenance."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import struct
from pathlib import Path


REQUIRED = [
    "01_image_recognizing_early.png",
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
    "recognition_processing_m1_v3_1.mp4",
    "recognition_processing_timing.txt",
    "recognition_production_flow_trace.txt",
    "recognition_motion_trace_m1_v3_1.json",
    "quality_full.png",
    "quality_balanced.png",
    "quality_lite.png",
    "reduce_motion_static.png",
    "degradation_d0_d4_contact_sheet.png",
    "recognition_accessibility_trace_v3_1.json",
    "recognition_visual_qa_v1_3.json",
    "recognition_production_flow_trace_m1_v3_1.json",
    "fish_focus_bbox_mapping.json",
    "recognition_visual_parity_v1_2.json",
    "recognition_visual_parity_contact_sheet_v1_2.png",
]
MANIFEST = "recognition_runtime_evidence_manifest_m1_v3_1.json"
PHASES = ["CAPTURED", "DETECTING", "OUTLINE", "CLASSIFYING"]
PRESENTATION = PHASES + ["RESULT_READY"]
MINIMUM_MS = [220, 320, 350, 250]
PIPELINE_PHASES = PHASES + ["RESULT"]
TIMING_AUTHORITY_PATH = "design/system_v3/08_Page_Contracts/recognition/processing/State_Machine_V3_1.json"
TIMING_AUTHORITY_SHA256 = "fa4f9f7ea371dd46f8a92be8fc0398bee465c05356a8d1d5623a46120d07de02"
MOTION_AUTHORITY_PATH = "design/pages/recognition/processing/motion/Recognition_Processing_Motion_Spec_V1_2.md"
MOTION_AUTHORITY_SHA256 = "00df6a7c28a76182a44f8f2008918dc533f5640114496b132a759d72e61ddc7c"
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


def read_key_values(path: Path) -> dict[str, str]:
    fields: dict[str, str] = {}
    for line_number, raw_line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        key, separator, value = line.partition("=")
        if not separator or not key or key in fields:
            fail(f"timing evidence must have unique KEY=value rows: line {line_number}")
        fields[key.strip()] = value.strip()
    return fields


def verify_apk_pair(app_apk: Path, test_apk: Path, sums_path: Path) -> dict[str, str]:
    if not app_apk.is_file() or not test_apk.is_file() or not sums_path.is_file():
        fail("exact APK pair or SHA256SUMS is missing")
    entries: dict[str, str] = {}
    for line in sums_path.read_text(encoding="utf-8").splitlines():
        match = re.fullmatch(r"([0-9a-f]{64})\s+\*?(.+)", line.strip())
        if not match:
            fail("malformed APK SHA256SUMS entry")
        entries[Path(match.group(2)).name] = match.group(1)
    app_sha = sha256(app_apk)
    test_sha = sha256(test_apk)
    if entries.get(app_apk.name) != app_sha or entries.get(test_apk.name) != test_sha:
        fail("APK SHA256SUMS do not bind the exact host app and instrumentation APK pair")
    return {"app_apk_sha256": app_sha, "test_apk_sha256": test_sha}


def validate_profile(profile: dict, contract: dict) -> dict:
    expected = contract["exact_source_provenance"]
    actual = {
        "device_model": str(profile.get("device_model", "")).strip(),
        "avd_name": str(profile.get("avd_name", "")).strip(),
        "display_size": str(profile.get("display_size", "")).strip(),
        "display_density": int(profile.get("display_density", -1)),
        "font_scale": float(profile.get("font_scale", -1)),
    }
    if actual["device_model"] != expected["device_model"]:
        fail(f"device model mismatch: {actual['device_model']!r}")
    if actual["avd_name"] != expected["avd_name"]:
        fail(f"AVD mismatch: {actual['avd_name']!r}")
    if actual["display_size"] != expected["display_size"]:
        fail(f"display size mismatch: {actual['display_size']!r}")
    if actual["display_density"] != expected["display_density"]:
        fail(f"display density mismatch: {actual['display_density']}")
    if abs(actual["font_scale"] - float(expected["font_scale"])) > 0.0001:
        fail(f"font scale mismatch: {actual['font_scale']}")
    return actual


def validate_catalog(path: Path, contract: dict) -> str:
    catalog = read_json(path)
    binding = contract["catalog_binding"]
    if catalog.get("catalog_version") != binding["version"]:
        fail("QA catalog version does not match the M1 evidence contract")
    if catalog.get("case_count") != 66 or len(catalog.get("cases", [])) != 66:
        fail("QA catalog base-case count changed")
    cases = {str(case.get("case_id")): case for case in catalog["cases"]}
    if not set(binding["case_ids"]).issubset(cases):
        fail("M1 processing timing cases are missing from the approved catalog")
    proc07 = cases.get("PROC-07", {})
    if proc07.get("acceptance_readiness") != "BLOCKED_PARAMETER":
        fail("PROC-07 must remain BLOCKED_PARAMETER until an approved timeout exists")
    if any(case.get("execution_status") != "NOT_RUN" for case in catalog["cases"]):
        fail("static M1 catalog validation cannot claim execution results")
    return sha256(path)


def validate_design_authority_matrix(path: Path, repository_root: Path, runtime_targets: set[str]) -> dict:
    """Bind visual comparisons to Design System sources; never promote a map to a visual PASS."""
    if not path.is_file():
        return {
            "path": str(path.relative_to(repository_root)),
            "sha256": None,
            "status": "BLOCKED_SOURCE_MATRIX_MISSING",
            "runtime_target_gaps": sorted(runtime_targets),
            "rows": [],
        }

    matrix = read_json(path)
    rows = matrix.get("authority_rows")
    if not isinstance(rows, list) or not rows:
        return {
            "path": str(path.relative_to(repository_root)),
            "sha256": sha256(path),
            "status": "BLOCKED_SOURCE_MATRIX_INVALID",
            "runtime_target_gaps": sorted(runtime_targets),
            "rows": [],
        }

    def repo_file(value: str, label: str) -> Path:
        candidate = (repository_root / value).resolve()
        if repository_root.resolve() not in candidate.parents:
            fail(f"design authority {label} escapes the repository: {value}")
        return candidate

    mapped_targets: set[str] = set()
    row_summaries: list[dict] = []
    visual_gaps: set[str] = set()
    seen: set[tuple[str, str]] = set()
    for index, row in enumerate(rows):
        if not isinstance(row, dict):
            fail(f"design authority row {index} must be an object")
        required = {
            "page_id", "state_id", "component_id", "version", "manager_route", "effective_authority_path",
            "frozen_asset_paths", "asset_sha256", "contract_path", "shared_foundation_paths",
            "source_status", "expected_runtime_target", "known_gap",
        }
        if not required.issubset(row):
            fail(f"design authority row {index} is missing fields: {sorted(required - set(row))}")
        if not isinstance(row["component_id"], str):
            fail(f"design authority component_id must be a string (empty for page/state rows): row {index}")
        key = (str(row["page_id"]), str(row["state_id"]), row["component_id"])
        if key in seen:
            fail(f"duplicate design authority page/state row: {key}")
        seen.add(key)
        target_value = row["expected_runtime_target"]
        targets = [target_value] if isinstance(target_value, str) else target_value
        if not isinstance(targets, list) or any(not isinstance(item, str) for item in targets):
            fail(f"design authority expected_runtime_target must be a string or string list: {key}")
        mapped_targets.update(targets)
        assets = row["frozen_asset_paths"]
        if not isinstance(assets, list) or any(not isinstance(item, str) for item in assets):
            fail(f"design authority frozen_asset_paths must be a string list: {key}")
        hashes = row["asset_sha256"]
        if isinstance(hashes, str):
            if len(assets) != 1:
                fail(f"single asset_sha256 requires exactly one frozen asset path: {key}")
            hashes = {assets[0]: hashes}
        if not isinstance(hashes, dict) or set(hashes) != set(assets):
            fail(f"asset_sha256 must map every frozen asset path exactly: {key}")
        for asset in assets:
            asset_path = repo_file(asset, "frozen asset")
            if not asset_path.is_file():
                visual_gaps.update(targets)
                continue
            expected_hash = hashes[asset]
            if not isinstance(expected_hash, str) or not re.fullmatch(r"[0-9a-f]{64}", expected_hash):
                fail(f"invalid frozen asset SHA-256 for {asset}: {key}")
            if sha256(asset_path) != expected_hash:
                fail(f"frozen asset SHA-256 mismatch for {asset}: {key}")
        for field in ("effective_authority_path", "contract_path"):
            source = repo_file(str(row[field]), field)
            if not source.is_file():
                visual_gaps.update(targets)
        foundations = row["shared_foundation_paths"]
        if not isinstance(foundations, list) or any(not isinstance(item, str) for item in foundations):
            fail(f"shared_foundation_paths must be a string list: {key}")
        for foundation in foundations:
            if not repo_file(foundation, "shared foundation").is_file():
                visual_gaps.update(targets)
        known_gap = row["known_gap"]
        known_gap_text = str(known_gap).strip().lower()
        if known_gap not in (None, "", [], {}) and known_gap_text not in {"none", "n/a", "no known gap"}:
            visual_gaps.update(targets)
        status = str(row["source_status"])
        if not status.strip() or any(marker in status.upper() for marker in ("MISSING", "BLOCKED", "REVIEW", "UNRESOLVED", "PENDING", "UNKNOWN", "UNAPPROVED")):
            visual_gaps.update(targets)
        row_summaries.append({
            "page_id": key[0],
            "state_id": key[1],
            "component_id": key[2],
            "version": str(row["version"]),
            "source_status": status,
            "expected_runtime_target": targets,
            "known_gap": known_gap,
            "asset_paths": assets,
        })

    target_gaps = sorted(runtime_targets - mapped_targets)
    visual_gaps.update(target_gaps)
    status = "REVIEW_REQUIRED" if visual_gaps else "SOURCE_MAP_VALIDATED"
    return {
        "path": str(path.relative_to(repository_root)),
        "sha256": sha256(path),
        "status": status,
        "runtime_target_gaps": target_gaps,
        "review_required_runtime_targets": sorted(visual_gaps),
        "rows": row_summaries,
    }


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
    parser.add_argument("--contract", type=Path, required=True)
    parser.add_argument("--catalog", type=Path, required=True)
    parser.add_argument("--guide-evidence-dir", type=Path, required=True)
    parser.add_argument("--device-profile", type=Path, required=True)
    parser.add_argument("--source-sha", required=True)
    parser.add_argument("--workflow-run-id", required=True)
    parser.add_argument("--api-level", type=int, required=True)
    parser.add_argument("--app-apk", type=Path, required=True)
    parser.add_argument("--test-apk", type=Path, required=True)
    parser.add_argument("--sha256sums", type=Path, required=True)
    parser.add_argument("--runner-pool", required=True)
    args = parser.parse_args()
    root = args.evidence_dir

    contract = read_json(args.contract)
    if contract.get("contract_id") != "M1_RECOGNITION_RUNTIME_GATE_V3_1":
        fail("unexpected M1 runtime gate contract")
    authority = contract.get("authority", {})
    authority_path = args.contract.resolve().parents[3] / authority.get("path", "")
    if (
        authority.get("version") != "recognition_processing_state_machine_v3_1"
        or authority.get("path") != TIMING_AUTHORITY_PATH
        or authority.get("sha256") != TIMING_AUTHORITY_SHA256
        or not authority_path.is_file()
        or sha256(authority_path) != authority.get("sha256")
    ):
        fail("M1 timing authority path or SHA-256 mismatch")
    contract_timing = contract.get("timing_ms", {})
    if contract_timing != {
        "CAPTURED_min": 220,
        "DETECTING_min": 320,
        "OUTLINE_min": 350,
        "CLASSIFYING_min": 250,
        "RESULT_READY": 0,
    }:
        fail("M1 runtime contract timing thresholds do not match the V3.1 state machine")
    if contract.get("presentation_states") != PRESENTATION or contract.get("pipeline_events") != PIPELINE_PHASES:
        fail("M1 runtime contract state or real Pipeline event sequence differs from V3.1")
    if contract.get("required_m1_processing_artifacts") != [
        "recognition_processing_m1_v3_1.mp4",
        "recognition_processing_timing.txt",
        "recognition_production_flow_trace.txt",
        "recognition_production_flow_trace_m1_v3_1.json",
        "recognition_motion_trace_m1_v3_1.json",
    ]:
        fail("M1 processing artifact contract is incomplete or stale")
    collision = contract.get("motion_authority_collision", {})
    motion_authority_path = args.contract.resolve().parents[3] / collision.get("path", "")
    if (
        collision.get("version") != "Recognition_Processing_Motion_Spec_V1_2"
        or collision.get("path") != MOTION_AUTHORITY_PATH
        or collision.get("sha256") != MOTION_AUTHORITY_SHA256
        or collision.get("status") not in {"REVIEW_REQUIRED", "RESOLVED"}
        or not motion_authority_path.is_file()
        or sha256(motion_authority_path) != collision.get("sha256")
    ):
        fail("M1 Resolve authority collision record or source SHA is invalid")
    if collision.get("status") == "RESOLVED" and not str(collision.get("resolution", "")).strip():
        fail("resolved M1 Resolve authority collision must include its approved resolution")
    if contract.get("manifest") != MANIFEST:
        fail("M1 evidence manifest name does not match the V3.1 contract")
    if contract.get("proc_07_timeout", {}).get("acceptance_readiness") != "BLOCKED_PARAMETER" or contract.get("proc_07_timeout", {}).get("timeout_ms") is not None:
        fail("PROC-07 must remain BLOCKED_PARAMETER with no unapproved timeout value")
    if not re.fullmatch(r"[0-9a-f]{40}", args.source_sha):
        fail("source SHA must be a full 40-character commit SHA")
    if not re.fullmatch(r"[0-9]+", args.workflow_run_id):
        fail("workflow run ID must be a numeric GitHub Actions run ID")
    if args.api_level != int(contract["exact_source_provenance"]["api_level"]):
        fail(f"API level mismatch: {args.api_level}")
    required_runner_pool = "/".join(contract["exact_source_provenance"]["workflow_runner_labels"])
    if args.runner_pool != required_runner_pool:
        fail(f"runtime runner pool mismatch: {args.runner_pool!r}")
    catalog_sha = validate_catalog(args.catalog, contract)
    profile = validate_profile(read_json(args.device_profile), contract)
    apk_hashes = verify_apk_pair(args.app_apk, args.test_apk, args.sha256sums)

    missing = [name for name in REQUIRED if not (root / name).is_file() or (root / name).stat().st_size == 0]
    if missing:
        fail("missing runtime evidence: " + ", ".join(missing))
    for name in REQUIRED:
        if name.endswith(".png"):
            check_png(root / name)

    motion = read_json(root / "recognition_motion_trace_m1_v3_1.json")
    if motion.get("contract_version") != "recognition_processing_state_machine_v3_1":
        fail("motion trace uses the wrong M1 contract version")
    if motion.get("motion_authority") != MOTION_AUTHORITY_PATH or motion.get("motion_authority_sha256") != MOTION_AUTHORITY_SHA256:
        fail("motion trace does not bind the recorded V1.2 diagnostic authority and SHA")
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
            resolve_strength = float(sample["resolve_strength"])
            phase = str(sample["phase"])
            contour_core_target = sample.get("contour_core_alpha_target")
            contour_core_target = None if contour_core_target is None else float(contour_core_target)
        except (KeyError, TypeError, ValueError) as error:
            fail(f"malformed motion sample: {error}")
        if timestamp < previous_time:
            fail("motion trace timestamps moved backwards")
        if not (0.0 <= offset < 1.0 and 0.0 <= speed <= 1.0 and 0.0 <= strength <= 1.0 and 0.0 <= resolve_strength <= 1.0):
            fail("motion trace contains an out-of-range value")
        if phase not in PHASES:
            fail(f"motion trace contains an unknown V3.1 phase: {phase}")
        if contour_core_target is not None and not 0.0 <= contour_core_target <= 1.0:
            fail("motion trace contains an out-of-range contour alpha diagnostic")
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

    flow = read_json(root / "recognition_production_flow_trace_m1_v3_1.json")
    if flow.get("contract_version") != "recognition_processing_state_machine_v3_1":
        fail("production flow uses the wrong M1 timing contract version")
    if flow.get("timing_authority") != TIMING_AUTHORITY_PATH or flow.get("timing_authority_sha256") != TIMING_AUTHORITY_SHA256:
        fail("production flow does not bind the V3.1 timing authority path and SHA")
    if flow.get("motion_authority") != MOTION_AUTHORITY_PATH or flow.get("motion_authority_sha256") != MOTION_AUTHORITY_SHA256:
        fail("production flow does not bind the separate V1.2 motion diagnostic authority and SHA")
    if flow.get("visual_state_sequence") != PRESENTATION:
        fail(f"production visual-state sequence mismatch: {flow.get('visual_state_sequence')}")
    if flow.get("pipeline_phases") != PIPELINE_PHASES:
        fail(f"production pipeline trace mismatch: {flow.get('pipeline_phases')}")
    if flow.get("minimum_durations_ms") != {
        "CAPTURED": 220,
        "DETECTING": 320,
        "OUTLINE": 350,
        "CLASSIFYING": 250,
        "RESULT_READY": 0,
    }:
        fail("production flow does not declare the exact V3.1 minimum visual beats")
    if any(field in flow for field in ("nominal_total_ms", "fast_result_min_ms", "fast_result_max_ms", "final_fish_focus_stable_ms")):
        fail("production flow contains superseded V1.1 total, fast-result, or focus-stability fields")
    events = flow.get("presentation_events", [])
    if [item.get("state") for item in events] != PRESENTATION:
        fail("production presentation trace does not match the V3.1 five-state sequence")
    try:
        event_times = [int(item.get("at_ms", -1)) for item in events]
    except (TypeError, ValueError) as error:
        fail(f"malformed production presentation event: {error}")
    if any(now < 0 for now in event_times) or event_times != sorted(event_times):
        fail("production presentation timestamps are invalid")
    pipeline_events = flow.get("pipeline_events", [])
    if [item.get("phase") for item in pipeline_events] != PIPELINE_PHASES:
        fail("production pipeline event trace does not contain each real phase exactly once and in order")
    pipeline_times = []
    for item in pipeline_events:
        try:
            pipeline_times.append(int(item["at_ms"]))
        except (KeyError, TypeError, ValueError) as error:
            fail(f"malformed production pipeline event: {error}")
    if any(now < 0 for now in pipeline_times) or pipeline_times != sorted(pipeline_times):
        fail("production pipeline timestamps are invalid")
    if any(pipeline_at > presented_at for pipeline_at, presented_at in zip(pipeline_times, event_times)):
        fail("a presented state precedes its corresponding real pipeline event/focus/result")
    runtime_intervals = [later - earlier for earlier, later in zip(event_times, event_times[1:])]
    for observed, minimum in zip(runtime_intervals, MINIMUM_MS):
        if observed < minimum:
            fail(f"runtime presentation beat below V3.1 minimum: {observed}ms < {minimum}ms")
    total_ms = event_times[-1] - event_times[0]
    timing = read_key_values(root / "recognition_processing_timing.txt")
    if timing.get("contract_version") != "recognition_processing_state_machine_v3_1":
        fail("timing evidence uses the wrong M1 contract version")
    if timing.get("timing_authority") != TIMING_AUTHORITY_PATH or timing.get("timing_authority_sha256") != TIMING_AUTHORITY_SHA256:
        fail("timing text does not bind the V3.1 timing authority path and SHA")
    if timing.get("resolve_motion_authority") != MOTION_AUTHORITY_PATH or timing.get("resolve_motion_authority_sha256") != MOTION_AUTHORITY_SHA256:
        fail("timing text does not identify the separate Resolve motion authority and SHA")
    declared_timing = {
        "captured_min_ms": 220,
        "detecting_min_ms": 320,
        "outline_min_ms": 350,
        "classifying_min_ms": 250,
    }
    for field, expected in declared_timing.items():
        try:
            observed = int(timing[field])
        except (KeyError, TypeError, ValueError) as error:
            fail(f"timing evidence has invalid declared {field}: {error}")
        if observed != expected:
            fail(f"timing evidence declaration {field} differs from approved V3.1 minimum {expected}ms")
    if any(field in timing for field in ("nominal_total_ms", "fast_result_min_ms", "fast_result_max_ms", "final_fish_focus_stable_ms")):
        fail("timing evidence contains a superseded V1.1 total, fast-result, or focus-stability field")
    timing_values: dict[str, int | None] = {}
    for field in (
        "captured_ms", "detecting_ms", "outline_ms", "classifying_ms", "total_ms", "result_ready_at_ms",
        "resolve_fade_ms", "classifying_breath_period_ms",
    ):
        try:
            timing_values[field] = int(timing[field])
        except (KeyError, TypeError, ValueError) as error:
            fail(f"timing evidence has invalid {field}: {error}")
    for field in ("resolve_fade_started_at_ms", "resolve_fade_first_visible_at_ms", "resolve_fade_observed_ms"):
        raw_value = timing.get(field)
        if raw_value is None or raw_value.lower() == "null":
            timing_values[field] = None
        else:
            try:
                timing_values[field] = int(raw_value)
            except (TypeError, ValueError) as error:
                fail(f"timing evidence has invalid optional diagnostic {field}: {error}")
    for field, minimum in zip(("captured_ms", "detecting_ms", "outline_ms", "classifying_ms"), MINIMUM_MS):
        actual = timing_values[field]
        if actual < minimum:
            fail(f"M1 timing {field} below V3.1 minimum: {actual}ms < {minimum}ms")
    if timing_values["total_ms"] != total_ms:
        fail("diagnostic timing text total does not match production presentation event trace")
    if timing_values["total_ms"] != sum(runtime_intervals):
        fail("diagnostic total does not equal the measured phase durations")
    actual_phase_durations = {
        "captured_ms": runtime_intervals[0],
        "detecting_ms": runtime_intervals[1],
        "outline_ms": runtime_intervals[2],
        "classifying_ms": runtime_intervals[3],
    }
    if any(timing_values[field] != measured for field, measured in actual_phase_durations.items()):
        fail("timing text phase durations do not match exact presentation event deltas")
    flow_timing_fields = (
        "captured_ms", "detecting_ms", "outline_ms", "classifying_ms", "total_ms",
        "resolve_fade_ms", "classifying_breath_period_ms",
    )
    for field in flow_timing_fields:
        try:
            flow_value = int(flow[field])
        except (KeyError, TypeError, ValueError) as error:
            fail(f"production flow trace has invalid {field}: {error}")
        if flow_value != timing_values[field]:
            fail(f"production flow trace {field} does not match canonical timing text")
    for field in ("resolve_fade_started_at_ms", "resolve_fade_first_visible_at_ms", "resolve_fade_observed_ms"):
        flow_value = flow.get(field)
        if flow_value is not None:
            try:
                flow_value = int(flow_value)
            except (TypeError, ValueError) as error:
                fail(f"production flow trace has invalid optional diagnostic {field}: {error}")
        if flow_value != timing_values[field]:
            fail(f"production flow trace {field} does not match canonical timing text")
    if timing_values["result_ready_at_ms"] != event_times[-1] or int(flow.get("result_ready_at_ms", -1)) != event_times[-1]:
        fail("production flow result_ready_at_ms does not match the RESULT_READY event")
    if "resolve_fade_observed_meets_motion_min" not in timing or timing["resolve_fade_observed_meets_motion_min"].lower() not in {"true", "false"}:
        fail("Resolve timing must be present as a separate diagnostic boolean")
    if flow.get("resolve_fade_observed_meets_motion_min") != (timing["resolve_fade_observed_meets_motion_min"].lower() == "true"):
        fail("Resolve diagnostic boolean does not match between timing text and production flow")
    # V3.1 requires immediate RESULT_READY after both the CLASSIFYING beat and
    # real RESULT event. Resolve remains a separate V1.2 diagnostic and is not
    # interpreted as a V3.1 state or as a timing acceptance condition here.
    if any(value is not None and value < 0 for value in (
        timing_values["resolve_fade_started_at_ms"],
        timing_values["resolve_fade_first_visible_at_ms"],
        timing_values["resolve_fade_observed_ms"],
    )):
        fail("Resolve diagnostic timestamps/duration cannot be negative")
    if flow.get("result_ready") is not True:
        fail("production flow did not reach a ready Result")
    source = flow.get("source", {})
    if int(source.get("width", 0)) <= 0 or int(source.get("height", 0)) <= 0:
        fail("production flow is missing source dimensions")
    if flow.get("quality_gate", {}).get("classifier_eligible") is not True:
        fail("production flow quality gate did not admit the fixture")
    if flow.get("focus", {}).get("level") != "A":
        fail("production flow did not record Level A focus")

    repo_root = args.contract.resolve().parents[3]
    matrix_path = repo_root / contract.get("visual_authority_matrix", {}).get("path", "")
    visual_targets = {
        "01_image_recognizing_early.png", "02_image_recognizing_late.png", "03_fish_located.png",
        "04_species_recognizing.png", "05_result_high.png", "06_result_medium.png",
        "07_result_low.png", "08_error_no_fish.png", "09_error_image_quality.png",
        "P01_compose_component.png", "P02_compose_component.png",
    }
    design_authority = validate_design_authority_matrix(matrix_path, repo_root, visual_targets)
    guide_artifacts = []
    for guide_name in ("P01_compose_component.png", "P02_compose_component.png"):
        guide_path = args.guide_evidence_dir / guide_name
        if not guide_path.is_file() or guide_path.stat().st_size == 0:
            design_authority["review_required_runtime_targets"] = sorted(set(design_authority.get("review_required_runtime_targets", [])) | {guide_name})
            design_authority["status"] = "REVIEW_REQUIRED"
            continue
        check_png(guide_path)
        guide_artifacts.append({"path": guide_name, "bytes": guide_path.stat().st_size, "sha256": sha256(guide_path)})

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
    if parity.get("classification") != "PASS" or len(parity.get("states", [])) != 9:
        fail("frozen visual parity did not pass all nine reference states")
    authority_rows = design_authority.get("rows", [])
    matrix_targets_missing: set[str] = set()
    for state in parity.get("states", []):
        target = state.get("runtime")
        frozen_name = state.get("frozen")
        matches = [
            row for row in authority_rows
            if target in row.get("expected_runtime_target", [])
        ]
        if not matches or not any(Path(asset).name == frozen_name for row in matches for asset in row.get("asset_paths", [])):
            matrix_targets_missing.add(str(target))
    for guide_name in ("P01_compose_component.png", "P02_compose_component.png"):
        matches = [row for row in authority_rows if guide_name in row.get("expected_runtime_target", [])]
        if not matches or not any(row.get("asset_paths") for row in matches):
            matrix_targets_missing.add(guide_name)
    if matrix_targets_missing:
        design_authority["review_required_runtime_targets"] = sorted(
            set(design_authority.get("review_required_runtime_targets", [])) | matrix_targets_missing
        )
        design_authority["status"] = "REVIEW_REQUIRED"

    artifacts = [
        {"path": name, "bytes": (root / name).stat().st_size, "sha256": sha256(root / name)}
        for name in REQUIRED
    ]
    manifest = {
        "contract_id": contract["contract_id"],
        "contract_version": "Recognition Runtime Evidence M1 V3.1",
        "validation_status": "PASS",
        "runtime_acceptance": "NOT_CLAIMED_BY_STATIC_VALIDATOR",
        "release_readiness": "REVIEW_REQUIRED" if collision["status"] != "RESOLVED" else "M1_RELEASE_ELIGIBLE",
        "release_blockers": [] if collision["status"] == "RESOLVED" else [
            "V3_1_IMMEDIATE_RESULT_READY_VS_V1_2_RESOLVE_HANDOFF_AUTHORITY_COLLISION"
        ],
        "authority": {
            "version": authority["version"],
            "path": authority["path"],
            "sha256": authority["sha256"],
            "contract_sha256": sha256(args.contract),
        },
        "motion_authority_collision": {
            "version": collision["version"],
            "path": collision["path"],
            "sha256": collision["sha256"],
            "status": collision["status"],
            "resolution": collision.get("resolution"),
            "conflict": collision["conflict"],
            "resolve_values_are_diagnostic_only": True,
        },
        "source_commit_sha": args.source_sha,
        "workflow_run_id": args.workflow_run_id,
        "gate": "recognition-frozen",
        "api_level": args.api_level,
        "runner_pool": args.runner_pool,
        "device_profile": profile,
        "apk_provenance": {
            **apk_hashes,
            "sha256sums_file_sha256": sha256(args.sha256sums),
            "sha256sums_verified": True,
        },
        "catalog": {
            "path": contract["catalog_binding"]["path"],
            "version": contract["catalog_binding"]["version"],
            "sha256": catalog_sha,
            "execution_statuses": "NOT_RUN",
            "proc_07_timeout_status": "BLOCKED_PARAMETER",
        },
        "visual_authority": design_authority,
        "visual_parity": {
            "classification": parity.get("classification"),
            "state_count": len(parity.get("states", [])),
            "source_map_sha256": design_authority.get("sha256"),
        },
        "guide_artifacts": guide_artifacts,
        "timing_ms": timing_values,
        "artifacts": artifacts,
        "runtime_gate_result": contract["runtime_gate_result"],
    }
    (root / MANIFEST).write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    if design_authority["status"] in {"REVIEW_REQUIRED", "BLOCKED_SOURCE_MATRIX_MISSING", "BLOCKED_SOURCE_MATRIX_INVALID"}:
        print(f"Recognition M1 V3.1 evidence collection: REVIEW_REQUIRED ({design_authority['status']}); runtime remains unclaimed")
        raise SystemExit(50)
    if collision["status"] == "RESOLVED":
        print(f"Recognition M1 V3.1 evidence collection: artifacts valid ({len(artifacts)}); exact runtime gate result still required for release")
    else:
        print(f"Recognition M1 V3.1 evidence collection: artifacts valid ({len(artifacts)}); release remains REVIEW_REQUIRED for the Resolve authority collision")


if __name__ == "__main__":
    main()
