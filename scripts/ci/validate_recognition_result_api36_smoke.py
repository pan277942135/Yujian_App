#!/usr/bin/env python3
"""Fail-closed API36 AUTO instrumentation and business-evidence validator."""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any


TARGET_CLASS = "com.yujian.ai.RecognitionRevealProductionRuntimeTest"
TARGET_METHOD = "realNightCatchHandoffDrivesTheNativeResultReveal"
TARGET_NAME = f"{TARGET_CLASS}#{TARGET_METHOD}"
REQUIRED_EVENTS = [
    "SLIMSAM_TERMINAL",
    "PATH_BOUND",
    "STAGE10",
    "RESULT_ROUTE_ATTACHED",
    "REAL_3_SEGMENT_RETURN_ACCEPTED",
    "ORB_TO_NAME",
    "GLYPH_RENDERED",
    "NAME_STABLE",
    "RESULT_SCENE_STABLE",
]
REQUIRED_SUBSTAGES = [
    "NAME_STABLE",
    "PHOTO_SETTLE",
    "RESULT_SCENE_HANDOFF",
    "RESULT_INFO_ARRIVAL",
    "RESULT_SCENE_STABLE",
]
REQUIRED_STAGE_SEQUENCE = [
    "PHOTO_STABLE",
    "ORB_WAKE",
    "ORB_RELEASE",
    "PERIMETER_ARRIVAL",
    "PATH_TO_FISH",
    "CONTOUR_A",
    "CONTOUR_B",
    "CONTOUR_C",
    "FISH_GOLD_PEAK",
    "ORB_RETURN",
    "ORB_TO_NAME",
    "SPECIES_NAME_REVEAL",
    "RESIDUE_SETTLE",
]
REQUIRED_STAGE_DURATIONS_MS = [0, 350, 600, 125, 125, 450, 450, 450, 1000, 350, 850, 800, 450]
REQUIRED_ARTIFACTS = {
    "stage_timeline": "Stage_Timeline.json",
    "navigation": "Result_Navigation_Trace.json",
    "geometry": "Result_Geometry.json",
    "return_event": "Return_Event.json",
    "handoff_trace": "Result_Handoff_Trace.json",
}


@dataclass(frozen=True)
class Validation:
    passed: bool
    reason: str


def _fail(reason: str) -> Validation:
    return Validation(False, reason)


def _json_object(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"{path.name} must contain a JSON object")
    return value


def _instrumentation_blocks(log_text: str) -> tuple[list[dict[str, str]], list[int]]:
    blocks: list[dict[str, str]] = []
    codes: list[int] = []
    current: dict[str, str] = {}
    for line in log_text.splitlines():
        status = re.match(r"^INSTRUMENTATION_STATUS: ([^=]+)=(.*)$", line)
        if status:
            current[status.group(1).strip()] = status.group(2).strip()
            continue
        code = re.match(r"^INSTRUMENTATION_STATUS_CODE: (-?\d+)\s*$", line)
        if code:
            current["__code__"] = code.group(1)
            blocks.append(current)
            codes.append(int(code.group(1)))
            current = {}
    return blocks, codes


def _validate_instrumentation(log_text: str, command_exit_code: int, timed_out: bool) -> Validation:
    if timed_out or command_exit_code == 124:
        return _fail("INSTRUMENTATION_TIMEOUT")
    if command_exit_code != 0:
        return _fail(f"INSTRUMENTATION_COMMAND_EXIT_{command_exit_code}")

    rejected = re.search(
        r"process crashed|shortMsg\s*=|INSTRUMENTATION_FAILED|FAILURES!!!|"
        r"AssertionError|java\.lang\.(?:Exception|Error)|FATAL EXCEPTION|"
        r"test run failed|instrumentation aborted|unable to find instrumentation|"
        r"^INSTRUMENTATION_STATUS: skipped=(?:true|1)\s*$",
        log_text,
        re.IGNORECASE | re.MULTILINE,
    )
    if rejected:
        return _fail("INSTRUMENTATION_ABORT_OR_TEST_FAILURE")

    blocks, status_codes = _instrumentation_blocks(log_text)
    if any(code not in (0, 1) for code in status_codes):
        return _fail("INSTRUMENTATION_TEST_ERROR_STATUS")
    target_blocks = [
        (index, block)
        for index, block in enumerate(blocks)
        if block.get("class") == TARGET_CLASS and block.get("test") == TARGET_METHOD
    ]
    starts = [(index, block) for index, block in target_blocks if block.get("__code__") == "1"]
    finishes = [(index, block) for index, block in target_blocks if block.get("__code__") == "0"]
    if len(starts) != 1:
        return _fail("TARGET_TEST_START_MISSING_OR_DUPLICATED")
    if len(finishes) != 1:
        return _fail("TARGET_TEST_COMPLETION_MISSING_OR_DUPLICATED")
    if finishes[0][0] <= starts[0][0]:
        return _fail("TARGET_TEST_COMPLETION_PRECEDES_START")

    if not re.search(r"^INSTRUMENTATION_RESULT:", log_text, re.MULTILINE):
        return _fail("INSTRUMENTATION_RESULT_TERMINATOR_MISSING")
    final_codes = re.findall(r"^INSTRUMENTATION_CODE:\s*(-?\d+)\s*$", log_text, re.MULTILINE)
    if final_codes != ["-1"]:
        return _fail("INSTRUMENTATION_FINAL_CODE_NOT_SUCCESS_MINUS_ONE")
    if log_text.index("INSTRUMENTATION_RESULT:") > log_text.rindex("INSTRUMENTATION_CODE:"):
        return _fail("INSTRUMENTATION_RESULT_AFTER_FINAL_CODE")
    return Validation(True, "NONE")


def _positive_int(value: Any) -> bool:
    return isinstance(value, int) and not isinstance(value, bool) and value > 0


def _validate_business_report(report: dict[str, Any], expected_source_sha: str) -> Validation:
    if report.get("status") != "PASS":
        return _fail("BUSINESS_CONTRACT_NOT_PASS")
    if report.get("test") != TARGET_NAME:
        return _fail("BUSINESS_CONTRACT_TEST_IDENTITY_MISMATCH")
    if report.get("source_build_sha") != expected_source_sha or not re.fullmatch(r"[0-9a-f]{40}", expected_source_sha):
        return _fail("BUSINESS_CONTRACT_SOURCE_SHA_MISMATCH")
    if report.get("model_contract_build_sha") != expected_source_sha:
        return _fail("MODEL_CONTRACT_SOURCE_SHA_MISMATCH")

    chain = report.get("event_chain")
    if (not isinstance(chain, list) or len(chain) != len(REQUIRED_EVENTS) or
            not all(isinstance(item, dict) for item in chain) or
            [item.get("name") for item in chain] != REQUIRED_EVENTS):
        return _fail("BUSINESS_EVENT_CHAIN_INCOMPLETE_OR_OUT_OF_ORDER")
    timestamps = [item.get("timestamp_uptime_ms") for item in chain]
    if any(not _positive_int(value) for value in timestamps):
        return _fail("BUSINESS_EVENT_TIMESTAMP_MISSING")
    if any(right < left for left, right in zip(timestamps, timestamps[1:])):
        return _fail("BUSINESS_EVENT_CHAIN_TIMESTAMP_ORDER_INVALID")

    facts = report.get("facts")
    if not isinstance(facts, dict):
        return _fail("BUSINESS_FACTS_MISSING")
    expected_fact_for_event = {
        "SLIMSAM_TERMINAL": ("slimsam_terminal", "timestamp_uptime_ms"),
        "PATH_BOUND": ("path_bound", "timestamp_uptime_ms"),
        "STAGE10": ("stage10", "timestamp_uptime_ms"),
        "RESULT_ROUTE_ATTACHED": ("result_route_attached", "timestamp_uptime_ms"),
        "REAL_3_SEGMENT_RETURN_ACCEPTED": ("return_event", "timestamp_uptime_ms"),
        "ORB_TO_NAME": ("orb_to_name", "timestamp_uptime_ms"),
        "GLYPH_RENDERED": ("glyph", "timestamp_uptime_ms"),
        "NAME_STABLE": ("name_stable", "timestamp_uptime_ms"),
        "RESULT_SCENE_STABLE": ("result_scene", "timestamp_uptime_ms"),
    }
    for event, event_time in zip(chain, timestamps):
        fact_name, time_key = expected_fact_for_event[event["name"]]
        fact = facts.get(fact_name)
        if not isinstance(fact, dict) or fact.get(time_key) != event_time:
            return _fail(f"BUSINESS_EVENT_FACT_MISMATCH_{event['name']}")

    def fact(name: str) -> dict[str, Any]:
        value = facts.get(name)
        return value if isinstance(value, dict) else {}

    slim = fact("slimsam_terminal")
    if slim.get("status") != "READY" or slim.get("quality") != "GOOD" or not _positive_int(slim.get("contour_segments")):
        return _fail("SLIMSAM_TERMINAL_NOT_READY_AND_TRUSTED")
    path = fact("path_bound")
    if path.get("binding_state") != "BOUND" or not path.get("path_id") or path.get("segment_count") != 3:
        return _fail("PROCESSING_PATH_NOT_BOUND_WITH_THREE_SEGMENTS")
    stage10 = fact("stage10")
    if stage10.get("navigation_status") != "NAVIGATION_REQUESTED" or stage10.get("navigation_requests") != 1:
        return _fail("STAGE10_NAVIGATION_NOT_REQUESTED_EXACTLY_ONCE")
    route = fact("result_route_attached")
    if route.get("status") != "RESULT_ATTACHED" or route.get("result_entries") != 1:
        return _fail("RESULT_ROUTE_NOT_ATTACHED_EXACTLY_ONCE")
    geometry = fact("geometry")
    if geometry.get("status") != "RESOLVED" or not _positive_int(geometry.get("geometry_version")):
        return _fail("RESULT_GEOMETRY_NOT_RESOLVED")
    returned = fact("return_event")
    if (returned.get("accepted") is not True or returned.get("feedback") != "RETURN" or
            returned.get("source") != "FISH_CONTOUR" or returned.get("render_canvas_receipt_ready") is not True or
            returned.get("segment_count") != 3 or returned.get("path_id") != path.get("path_id")):
        return _fail("THREE_SEGMENT_RETURN_NOT_ACCEPTED_AFTER_CANVAS_RECEIPT")
    orb = fact("orb_to_name")
    if orb.get("stage") != "ORB_TO_NAME":
        return _fail("ORB_TO_NAME_STAGE_NOT_OBSERVED")
    glyph = fact("glyph")
    rendered_progress = glyph.get("rendered_progress")
    if not isinstance(rendered_progress, (int, float)) or isinstance(rendered_progress, bool):
        rendered_progress = 0
    if (glyph.get("glyph_path_ready") is not True or glyph.get("glyph_reveal_rendered") is not True or
            glyph.get("name_stable") is not True or glyph.get("fill_path_drawn") is not True or
            glyph.get("contour_path_drawn") is not True or rendered_progress < 0.999 or
            not _positive_int(glyph.get("draw_pass_sequence"))):
        return _fail("ACTUAL_GLYPH_DRAW_RECEIPT_NOT_VALID")
    name = fact("name_stable")
    if name.get("accepted") is not True or not _positive_int(name.get("event_sequence")):
        return _fail("NAME_STABLE_HANDOFF_NOT_ACCEPTED")
    scene = fact("result_scene")
    if (scene.get("accepted") is not True or scene.get("result_scene_stable") is not True or
            scene.get("all_stages_visited") is not True or scene.get("stage_count") != 13 or
            scene.get("completed_handoff_substages") != REQUIRED_SUBSTAGES):
        return _fail("STAGE11_TO_13_5_OR_FINAL_RESULT_NOT_COMPLETE")

    files = report.get("evidence_files")
    if files != REQUIRED_ARTIFACTS:
        return _fail("BUSINESS_EVIDENCE_FILE_LIST_INVALID")
    return Validation(True, "NONE")


def _validate_business_artifacts(report: dict[str, Any], evidence_dir: Path) -> Validation:
    files = report.get("evidence_files", {})
    payloads: dict[str, dict[str, Any]] = {}
    resolved_root = evidence_dir.resolve()
    for key, required_name in REQUIRED_ARTIFACTS.items():
        if files.get(key) != required_name:
            return _fail(f"BUSINESS_EVIDENCE_REFERENCE_INVALID_{key.upper()}")
        path = (evidence_dir / required_name).resolve()
        if path.parent != resolved_root:
            return _fail(f"BUSINESS_EVIDENCE_PATH_OUTSIDE_DIRECTORY_{key.upper()}")
        if not path.is_file() or path.stat().st_size == 0:
            return _fail(f"BUSINESS_EVIDENCE_ARTIFACT_MISSING_{key.upper()}")
        try:
            payloads[key] = _json_object(path)
        except (OSError, ValueError, json.JSONDecodeError) as exc:
            return _fail(f"BUSINESS_EVIDENCE_ARTIFACT_INVALID_{key.upper()}:{exc}")

    stage_rows = payloads["stage_timeline"].get("stages")
    stage_names = [row.get("stage") for row in stage_rows if isinstance(row, dict)] if isinstance(stage_rows, list) else []
    if payloads["stage_timeline"].get("status") != "PASS" or stage_names != REQUIRED_STAGE_SEQUENCE:
        return _fail("STAGE_TIMELINE_DOES_NOT_CONTAIN_ALL_13_STAGES")
    stage_contract = list(zip(stage_rows, REQUIRED_STAGE_DURATIONS_MS))
    for row, expected_duration in stage_contract:
        required = row.get("required_duration_ms")
        duration = row.get("stage_duration_ms")
        if required != expected_duration or not isinstance(duration, int) or duration < expected_duration:
            return _fail("STAGE_TIMELINE_DURATION_CONTRACT_FAILED")
    if sum(row.get("required_duration_ms", 0) for row in stage_rows if isinstance(row, dict)) != 6000:
        return _fail("STAGE_TIMELINE_REQUIRED_BUDGET_NOT_6000_MS")
    facts = report["facts"]
    orb_timestamp = facts["orb_to_name"]["timestamp_uptime_ms"]
    orb_row = next(row for row in stage_rows if row.get("stage") == "ORB_TO_NAME")
    if orb_row.get("stage_start_timestamp_ms") != orb_timestamp:
        return _fail("STAGE_TIMELINE_ORB_TO_NAME_TIMESTAMP_MISMATCH")
    navigation = payloads["navigation"].get("transitions")
    nav_statuses = [row.get("status") for row in navigation if isinstance(row, dict)] if isinstance(navigation, list) else []
    if (payloads["navigation"].get("status") != "PASS" or nav_statuses != [
            "CREATED", "PROCESSING_COMPLETE", "HANDOFF_READY", "NAVIGATION_REQUESTED", "RESULT_ATTACHED", "HANDOFF_COMPLETED"]):
        return _fail("RESULT_NAVIGATION_TRACE_INCOMPLETE")
    nav_by_status = {row.get("status"): row for row in navigation if isinstance(row, dict)}
    if (nav_by_status["NAVIGATION_REQUESTED"].get("monotonicTimestampMs") != facts["stage10"]["timestamp_uptime_ms"] or
            nav_by_status["RESULT_ATTACHED"].get("monotonicTimestampMs") != facts["result_route_attached"]["timestamp_uptime_ms"] or
            nav_by_status["HANDOFF_COMPLETED"].get("monotonicTimestampMs") != facts["result_scene"]["timestamp_uptime_ms"]):
        return _fail("RESULT_NAVIGATION_FACT_TIMESTAMP_MISMATCH")
    geometry = payloads["geometry"]
    if geometry.get("status") != "RESOLVED" or geometry.get("return_path_segment_count") != 3:
        return _fail("RESULT_GEOMETRY_EVIDENCE_INVALID")
    if (geometry.get("geometry_version") != facts["geometry"]["geometry_version"] or
            geometry.get("return_path_id") != facts["path_bound"]["path_id"]):
        return _fail("RESULT_GEOMETRY_IDENTITY_MISMATCH")
    returned = payloads["return_event"]
    if (returned.get("accepted") is not True or returned.get("feedback") != "RETURN" or returned.get("source") != "FISH_CONTOUR" or
            returned.get("render_canvas_receipt_ready") is not True or returned.get("segment_count") != 3 or
            returned.get("path_id") != facts["path_bound"]["path_id"] or
            returned.get("timestamp_uptime_ms") != facts["return_event"]["timestamp_uptime_ms"]):
        return _fail("RETURN_EVENT_EVIDENCE_INVALID")
    handoff = payloads["handoff_trace"]
    handoff_events = handoff.get("events")
    stable_timestamp = handoff.get("resultStableTimestampMs")
    geometry_released_timestamp = handoff.get("geometryReleasedTimestampMs")
    if (handoff.get("resultSceneStable") is not True or handoff.get("transactionStatus") != "HANDOFF_COMPLETED" or
            handoff.get("playbackStatus") != "COMPLETED" or not _positive_int(stable_timestamp) or
            not _positive_int(geometry_released_timestamp) or geometry_released_timestamp < stable_timestamp or
            not isinstance(handoff_events, list) or len(handoff_events) != len(REQUIRED_SUBSTAGES)):
        return _fail("RESULT_HANDOFF_TRACE_NOT_STABLE")
    if not all(row.get("accepted") is True for row in handoff_events if isinstance(row, dict)):
        return _fail("RESULT_HANDOFF_TRACE_CONTAINS_REJECTED_EVENT")
    accepted = [row.get("substage") for row in handoff_events if isinstance(row, dict) and row.get("accepted") is True]
    if accepted != REQUIRED_SUBSTAGES:
        return _fail("RESULT_HANDOFF_TRACE_SUBSTAGES_INCOMPLETE")
    glyph = next((row for row in handoff_events if isinstance(row, dict) and row.get("substage") == "NAME_STABLE"), None)
    glyph_progress = glyph.get("glyphDrawProgress") if glyph else None
    if (not glyph or glyph.get("glyphPathReady") is not True or glyph.get("glyphRevealRendered") is not True or
            glyph.get("glyphNameStable") is not True or not _positive_int(glyph.get("glyphDrawPassSequence")) or
            not isinstance(glyph_progress, (int, float)) or isinstance(glyph_progress, bool) or glyph_progress < 0.999):
        return _fail("RESULT_HANDOFF_TRACE_GLYPH_RECEIPT_INVALID")
    if glyph.get("timestamp") != facts["name_stable"]["timestamp_uptime_ms"]:
        return _fail("RESULT_HANDOFF_TRACE_GLYPH_TIMESTAMP_MISMATCH")
    stable = next((row for row in handoff_events if isinstance(row, dict) and row.get("substage") == "RESULT_SCENE_STABLE"), None)
    if not stable or stable.get("timestamp") != facts["result_scene"]["timestamp_uptime_ms"]:
        return _fail("RESULT_HANDOFF_TRACE_STABLE_TIMESTAMP_MISMATCH")
    return Validation(True, "NONE")


def validate_run(
    instrumentation_text: str,
    command_exit_code: int,
    business_report: dict[str, Any] | None,
    expected_source_sha: str,
    timed_out: bool = False,
    business_evidence_dir: Path | None = None,
) -> Validation:
    instrumentation = _validate_instrumentation(instrumentation_text, command_exit_code, timed_out)
    if not instrumentation.passed:
        return instrumentation
    if business_report is None:
        return _fail("BUSINESS_CONTRACT_FILE_MISSING")
    report = _validate_business_report(business_report, expected_source_sha)
    if not report.passed:
        return report
    if business_evidence_dir is None:
        return _fail("BUSINESS_EVIDENCE_DIRECTORY_MISSING")
    return _validate_business_artifacts(business_report, business_evidence_dir)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--instrumentation-log", required=True, type=Path)
    parser.add_argument("--business-report", required=True, type=Path)
    parser.add_argument("--business-evidence-dir", required=True, type=Path)
    parser.add_argument("--source-sha", required=True)
    parser.add_argument("--command-exit-code", required=True, type=int)
    parser.add_argument("--timed-out", action="store_true")
    args = parser.parse_args()
    try:
        report = _json_object(args.business_report)
        result = validate_run(
            args.instrumentation_log.read_text(encoding="utf-8", errors="replace"),
            args.command_exit_code,
            report,
            args.source_sha,
            args.timed_out,
            args.business_evidence_dir,
        )
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        result = _fail(f"VALIDATION_INPUT_INVALID:{exc}")
    print(json.dumps({"classification": "AUTO_SMOKE_PASS" if result.passed else "AUTO_SMOKE_FAIL", "reason": result.reason}))
    return 0 if result.passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
