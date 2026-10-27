#!/usr/bin/env python3
"""Validate the 33 supplemental V2 runtime cases and three scoped NH01 states."""
from __future__ import annotations

import argparse
import json
import math
from pathlib import Path
from typing import Any


FONT_CASES = (("F10", 1.0), ("F13", 1.3), ("F15", 1.5))
NAMED_VIEWPORTS = (
    ("SUP-W480-H840", 480, 840),
    ("SUP-W412-H915", 412, 915),
    ("SUP-W375-H720", 375, 720),
)
BOUNDARY_HEIGHTS = (459, 460, 499, 500, 699, 700, 839, 840)
MATRIX_CASES: tuple[dict[str, Any], ...] = tuple(
    {
        "profile_id": f"{prefix}-{font_name}",
        "width_dp": width,
        "display_height_dp": height,
        "safe_height_dp": None,
        "font_scale": scale,
    }
    for prefix, width, height in NAMED_VIEWPORTS
    for font_name, scale in FONT_CASES
) + tuple(
    {
        "profile_id": f"BOUNDARY-SAFE{height}-{font_name}",
        "width_dp": 360,
        "display_height_dp": None,
        "safe_height_dp": height,
        "font_scale": scale,
    }
    for height in BOUNDARY_HEIGHTS
    for font_name, scale in FONT_CASES
)
STATE_CASES = (
    ("SUP-SINGLE-M01", "single_record"),
    ("SUP-SINGLE-M03", "single_record"),
    ("SUP-IMAGEFAIL-M01", "image_failure"),
    ("SUP-THREE-M03", "three_unique_catches"),
)
REQUIRED_BOUNDS = (
    "gemini-home-screen",
    "gemini-home-top-section",
    "gemini-home-middle-section",
    "gemini-home-section-title",
    "gemini-home-carousel-track",
    "gemini-home-catch-card-nh01-catch-001",
    "gemini-home-footer-cta",
    "gemini-home-cta-label",
    "gemini-home-avatar-button",
    "gemini-home-camera",
    "gemini-home-catch-species-nh01-catch-001",
    "gemini-home-catch-measurement-nh01-catch-001",
    "gemini-home-catch-metadata-nh01-catch-001",
)
REQUIRED_TEXT_TAGS = (
    "gemini-home-catch-species-nh01-catch-001",
    "gemini-home-catch-measurement-nh01-catch-001",
    "gemini-home-catch-metadata-nh01-catch-001",
    "gemini-home-cta-label",
)


def read_json(path: Path) -> dict[str, Any] | None:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return None
    return value if isinstance(value, dict) else None


def exists_screenshot(raw_dir: Path, payload: dict[str, Any], key: str) -> bool:
    name = payload.get("screenshots", {}).get(key)
    return isinstance(name, str) and (raw_dir / name).is_file()


def validate_matrix_case(
    raw_dir: Path,
    execution_dir: Path,
    case: dict[str, Any],
    source_sha: str,
) -> tuple[dict[str, Any], list[str]]:
    profile = str(case["profile_id"])
    report_path = raw_dir / f"{profile}-adaptive_v2.json"
    execution_path = execution_dir / f"{profile}-adaptive_v2.json"
    report = read_json(report_path)
    execution = read_json(execution_path)
    errors: list[str] = []
    if execution is None:
        errors.append("execution record missing or invalid")
    else:
        for key, expected in (
            ("source_sha", source_sha),
            ("status", "PASS"),
            ("layout_variant", "adaptive_v2"),
        ):
            if execution.get(key) != expected:
                errors.append(f"execution {key}={execution.get(key)!r}, expected {expected!r}")
        for key in ("device_report_present", "required_screenshots_present", "profile_settings_match"):
            if execution.get(key) is not True:
                errors.append(f"execution {key} is not true")
    if report is None:
        errors.append("device report missing or invalid")
        return {"profile_id": profile, "status": "FAIL", "errors": errors}, errors
    for key, expected in (
        ("status", "PASS"),
        ("execution_mode", "ANDROID_API28_RUNTIME"),
        ("layout_variant", "ADAPTIVE_V2"),
        ("source_commit", source_sha),
    ):
        if report.get(key) != expected:
            errors.append(f"report {key}={report.get(key)!r}, expected {expected!r}")
    if not math.isclose(float(report.get("system_font_scale", -1)), float(case["font_scale"]), abs_tol=0.02):
        errors.append("system font scale does not match the requested case")
    if report.get("density_dpi") != 160:
        errors.append(f"density_dpi={report.get('density_dpi')!r}, expected 160")
    resolution = report.get("display_resolution_px")
    if not isinstance(resolution, list) or len(resolution) != 2 or resolution[0] != case["width_dp"]:
        errors.append(f"display resolution does not use the requested width: {resolution!r}")
    elif case["display_height_dp"] is not None and resolution[1] != case["display_height_dp"]:
        errors.append(f"display height={resolution[1]!r}, expected {case['display_height_dp']!r}")

    safe_insets = report.get("safe_insets_dp", {})
    window = report.get("window_bounds_dp", {})
    measured_safe_height = float(report.get("data", {}).get("safe_height_dp", -1))
    if case["safe_height_dp"] is not None and not math.isclose(
        measured_safe_height, float(case["safe_height_dp"]), abs_tol=0.25
    ):
        errors.append(f"measured safe height={measured_safe_height}, expected {case['safe_height_dp']}")
    if not safe_insets or not isinstance(window, dict):
        errors.append("actual window bounds and safe insets were not recorded")

    bounds = report.get("bounds_in_window_px", {})
    missing_bounds = [tag for tag in REQUIRED_BOUNDS if tag not in bounds]
    if missing_bounds:
        errors.append(f"required bounds missing: {missing_bounds}")
    if report.get("data", {}).get("footer_outside_middle") is not True:
        errors.append("Footer is not proven outside Middle")
    metrics = report.get("metrics", {})
    for key in ("card_actual_width_dp", "card_aspect_ratio", "stats_to_title_gap_dp", "card_to_footer_gap_dp"):
        if key not in metrics:
            errors.append(f"measured metric missing: {key}")
    camera = metrics.get("camera_bounds_dp", {})
    if float(camera.get("width", 0)) < 72 or float(camera.get("height", 0)) < 72:
        errors.append("camera touch target is below 72dp")
    action = metrics.get("section_action_bounds_dp", {})
    if float(action.get("width", 0)) < 48 or float(action.get("height", 0)) < 48:
        errors.append("section action touch target is below 48dp")
    avatar = metrics.get("avatar_touch_bounds_dp", {})
    if float(avatar.get("width", 0)) < 48 or float(avatar.get("height", 0)) < 48:
        errors.append("avatar touch target is below 48dp")
    if metrics.get("cta_camera_overlap") is not False:
        errors.append("CTA/camera non-overlap is not proven")
    text_layouts = metrics.get("text_layouts", {})
    for tag in REQUIRED_TEXT_TAGS:
        text = text_layouts.get(tag, {})
        if not isinstance(text.get("line_ranges"), str) or not text.get("line_ranges"):
            errors.append(f"actual TextLayoutResult line range missing for {tag}")
        if text.get("visual_overflow") is not False:
            errors.append(f"required text overflowed or was not disproven for {tag}")
    after_swipe_text_layouts = metrics.get("text_layouts_after_swipe", {})
    for tag in (
        "gemini-home-catch-species-nh01-catch-002",
        "gemini-home-catch-measurement-nh01-catch-002",
        "gemini-home-catch-metadata-nh01-catch-002",
    ):
        text = after_swipe_text_layouts.get(tag, {})
        if not isinstance(text.get("line_ranges"), str) or not text.get("line_ranges"):
            errors.append(f"post-swipe TextLayoutResult line range missing for {tag}")
        if text.get("visual_overflow") is not False:
            errors.append(f"post-swipe required text overflowed or was not disproven for {tag}")
    if not exists_screenshot(raw_dir, report, "initial"):
        errors.append("full-window initial screenshot missing")
    if not exists_screenshot(raw_dir, report, "after_swipe"):
        errors.append("full-window post-swipe screenshot missing")

    row = {
        "profile_id": profile,
        "requested_width_dp": case["width_dp"],
        "requested_display_height_dp": case["display_height_dp"],
        "requested_safe_height_dp": case["safe_height_dp"],
        "measured_safe_height_dp": measured_safe_height,
        "font_scale": case["font_scale"],
        "display_resolution_px": resolution,
        "safe_insets_dp": safe_insets,
        "bounds_in_window_px": bounds,
        "metrics": metrics,
        "screenshots": report.get("screenshots", {}),
        "report": str(report_path.relative_to(raw_dir.parent)),
        "execution": str(execution_path.relative_to(execution_dir.parent)),
        "status": "PASS" if not errors else "FAIL",
        "errors": errors,
    }
    return row, errors


def validate_state_case(
    raw_dir: Path,
    execution_dir: Path,
    profile: str,
    expected_scenario: str,
    source_sha: str,
) -> tuple[dict[str, Any], list[str]]:
    report_path = raw_dir / f"{profile}-adaptive_v2.json"
    execution_path = execution_dir / f"{profile}-adaptive_v2.json"
    report = read_json(report_path)
    execution = read_json(execution_path)
    errors: list[str] = []
    if report is None or execution is None:
        errors.append("scenario report or execution record missing or invalid")
        return {"profile_id": profile, "scenario": expected_scenario, "status": "FAIL", "errors": errors}, errors
    for payload_name, payload in (("report", report), ("execution", execution)):
        if payload.get("status") != "PASS":
            errors.append(f"{payload_name} status={payload.get('status')!r}")
        if payload.get("source_commit", payload.get("source_sha")) != source_sha:
            errors.append(f"{payload_name} source SHA mismatch")
    if report.get("scenario") != expected_scenario:
        errors.append(f"scenario={report.get('scenario')!r}, expected {expected_scenario!r}")
    data = report.get("data", {})
    metrics = report.get("metrics", {})
    if expected_scenario == "single_record":
        if data.get("single_record_state") != "PASS_ONE_CENTERED_HERO_NO_NEIGHBOR":
            errors.append("single-record centered Hero/no-neighbor assertion missing")
        if data.get("pager_page_count") != 1 or data.get("unique_catch_count") != 1:
            errors.append("single-record pager count or stable record count is incorrect")
        if not math.isclose(float(metrics.get("card_aspect_ratio", 0)), 247 / 293, abs_tol=0.01):
            errors.append("single-record Hero ratio is outside 247:293 tolerance")
        avatar = metrics.get("avatar_touch_bounds_dp", {})
        camera = metrics.get("camera_touch_bounds_dp", {})
        if float(avatar.get("width", 0)) < 48 or float(avatar.get("height", 0)) < 48:
            errors.append("single-record avatar touch target is below 48dp")
        if float(camera.get("width", 0)) < 72 or float(camera.get("height", 0)) < 72:
            errors.append("single-record camera touch target is below 72dp")
        if data.get("middle_scroll_enabled") is not False:
            errors.append("single-record middle scrolling is not proven disabled")
    elif expected_scenario == "image_failure":
        if data.get("image_failure_fallback") != "PASS_FALLBACK_RENDERED":
            errors.append("image failure fallback was not rendered")
        if data.get("fallback_tag_status") != "FALLBACK" or data.get("rendered_image_nodes_after_failure") != 0:
            errors.append("image failure result does not identify the visible fallback state")
        if metrics.get("geometry_unchanged_after_image_failure") is not True:
            errors.append("layout geometry stability was not asserted")
        before = metrics.get("before_failure_bounds_dp", {})
        after = metrics.get("after_failure_bounds_dp", {})
        if not before or before.keys() != after.keys():
            errors.append("before/after image-failure component bounds are incomplete")
        else:
            for tag in before:
                if any(abs(float(before[tag][edge]) - float(after[tag][edge])) > 1 for edge in ("left", "top", "right", "bottom")):
                    errors.append(f"{tag} changed by more than 1dp after image failure")
    else:
        if data.get("catch_ids") != ["nh01-catch-001", "nh01-catch-002", "nh01-catch-003"]:
            errors.append("three-record case does not contain three unique stable record IDs in order")
        if data.get("unique_catch_count") != 3 or data.get("pager_page_count") != 3:
            errors.append("three-record pager count or stable record count is incorrect")
        if data.get("swipe_reached_second_id") is not True or data.get("selected_record_after_swipe") != "nh01-catch-002":
            errors.append("three-record case did not select its real center record")
        if data.get("left_neighbor_after_swipe") != "nh01-catch-001" or data.get("right_neighbor_after_swipe") != "nh01-catch-003":
            errors.append("three-record center page does not bind distinct real left/right neighbors")
        if data.get("both_real_neighbors_after_swipe") is not True:
            errors.append("three-record center state does not prove both real neighbors are visible")
        if metrics.get("hero_geometry_unchanged_after_swipe") is not True:
            errors.append("three-record Hero geometry changed when selecting another real record")
        if float(metrics.get("left_peek_after_swipe_overlap_dp", 999)) > 1:
            errors.append("three-record left neighbor overlaps the selected Hero by more than 1dp")
        if float(metrics.get("right_peek_after_swipe_overlap_dp", 999)) > 1:
            errors.append("three-record right neighbor overlaps the selected Hero by more than 1dp")
        if float(metrics.get("left_peek_after_swipe_visible_width_dp", 0)) <= 0:
            errors.append("three-record left neighbor is not visible inside the track")
        if float(metrics.get("right_peek_after_swipe_visible_width_dp", 0)) <= 0:
            errors.append("three-record right neighbor is not visible inside the track")
        if not math.isclose(float(metrics.get("card_aspect_ratio", 0)), 247 / 293, abs_tol=0.01):
            errors.append("three-record Hero ratio is outside 247:293 tolerance")
        after_swipe_text = metrics.get("text_layouts_after_swipe", {})
        for tag in (
            "gemini-home-catch-species-nh01-catch-002",
            "gemini-home-catch-measurement-nh01-catch-002",
            "gemini-home-catch-metadata-nh01-catch-002",
        ):
            text = after_swipe_text.get(tag, {})
            if not isinstance(text.get("line_ranges"), str) or not text.get("line_ranges"):
                errors.append(f"three-record selected text line ranges missing for {tag}")
            if text.get("visual_overflow") is not False:
                errors.append(f"three-record selected text overflowed or was not disproven for {tag}")
    if expected_scenario == "single_record":
        screenshot_keys = ("initial",)
    elif expected_scenario == "image_failure":
        screenshot_keys = ("initial", "image_failure_fallback")
    else:
        screenshot_keys = ("initial", "after_swipe")
        if not exists_screenshot(raw_dir, report, screenshot_key):
            errors.append(f"scenario screenshot missing: {screenshot_key}")
    row = {
        "profile_id": profile,
        "scenario": expected_scenario,
        "source_sha": source_sha,
        "screenshots": report.get("screenshots", {}),
        "report": str(report_path.relative_to(raw_dir.parent)),
        "status": "PASS" if not errors else "FAIL",
        "errors": errors,
        "data": data,
        "metrics": metrics,
    }
    return row, errors


def validate_continuity(rows: dict[str, dict[str, Any]]) -> list[str]:
    errors: list[str] = []
    for lower_height, upper_height in zip(BOUNDARY_HEIGHTS[::2], BOUNDARY_HEIGHTS[1::2]):
        for font_name, scale in FONT_CASES:
            lower = rows.get(f"BOUNDARY-SAFE{lower_height}-{font_name}")
            upper = rows.get(f"BOUNDARY-SAFE{upper_height}-{font_name}")
            if not lower or not upper or lower.get("status") != "PASS" or upper.get("status") != "PASS":
                errors.append(f"continuity pair {lower_height}/{upper_height} at {scale}x is incomplete")
                continue
            lower_metrics = lower["metrics"]
            upper_metrics = upper["metrics"]
            for metric in (
                "card_actual_width_dp",
                "section_title_to_card_gap_dp",
                "stats_to_title_gap_dp",
                "card_to_footer_gap_dp",
            ):
                delta = abs(float(lower_metrics.get(metric, 0)) - float(upper_metrics.get(metric, 0)))
                if delta > 2.5:
                    errors.append(
                        f"curve discontinuity {lower_height}/{upper_height} at {scale}x: {metric} delta={delta:.3f}dp"
                    )
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--raw-dir", type=Path, required=True)
    parser.add_argument("--execution-dir", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--source-sha", required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--run-url", required=True)
    args = parser.parse_args()
    raw_dir = args.raw_dir.resolve()
    execution_dir = args.execution_dir.resolve()
    output_dir = args.output_dir.resolve()
    output_dir.mkdir(parents=True, exist_ok=True)
    rows: list[dict[str, Any]] = []
    rows_by_id: dict[str, dict[str, Any]] = {}
    errors: list[str] = []
    for case in MATRIX_CASES:
        row, row_errors = validate_matrix_case(raw_dir, execution_dir, case, args.source_sha)
        rows.append(row)
        rows_by_id[row["profile_id"]] = row
        errors.extend(f"{row['profile_id']}: {error}" for error in row_errors)
    continuity_errors = validate_continuity(rows_by_id)
    errors.extend(continuity_errors)
    state_rows: list[dict[str, Any]] = []
    for profile, scenario in STATE_CASES:
        row, row_errors = validate_state_case(raw_dir, execution_dir, profile, scenario, args.source_sha)
        state_rows.append(row)
        errors.extend(f"{profile}: {error}" for error in row_errors)
    status = "PASS" if len(rows) == 33 and len(state_rows) == 4 and not errors else "FAIL"
    report = {
        "schema": "NH01_ADAPTIVE_SUPPLEMENTAL_MATRIX_V1",
        "status": status,
        "source_sha": args.source_sha,
        "workflow_run_id": args.run_id,
        "workflow_run_url": args.run_url,
        "execution_mode": "ANDROID_API28_RUNTIME",
        "baseline_24_executions": "SEE_NH01_ADAPTIVE_MATRIX_SUMMARY_V1.json",
        "supplemental_matrix_case_count": len(rows),
        "supplemental_state_case_count": len(state_rows),
        "total_expected_api28_executions": 24 + len(rows),
        "total_including_scoped_state_cases": 24 + len(rows) + len(state_rows),
        "matrix_cases": rows,
        "state_cases": state_rows,
        "continuity": {"status": "PASS" if not continuity_errors else "FAIL", "errors": continuity_errors},
        "errors": errors,
    }
    (output_dir / "NH01_ADAPTIVE_SUPPLEMENTAL_MATRIX_V1.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    lines = [
        "# NH01 Adaptive V2 supplemental Android runtime matrix",
        "",
        f"- Status: **{status}**",
        f"- Exact source SHA: `{args.source_sha}`",
        f"- API 28 workflow: [{args.run_id}]({args.run_url})",
        "- The original 24-run matrix remains unchanged.",
        "- Supplemental matrix: 33 V2 runs (explicit 480×840, 412×915, 375×720 pairs; eight measured safe-height boundaries at three font scales).",
        "- Scoped runtime states: single record on M01 and M03; image-load failure on M01; and three records with the center page selected on M03 (390×844dp). M03 single/two/three-record runs share the same viewport and font scale.",
        "",
        "| Profile | Width | Display height | Measured safe height | Font scale | Result |",
        "|---|---:|---:|---:|---:|---|",
    ]
    for row in rows:
        lines.append(
            f"| {row['profile_id']} | {row.get('requested_width_dp')}dp | "
            f"{row.get('requested_display_height_dp') or 'Inset-calibrated'} | "
            f"{row.get('measured_safe_height_dp')}dp | {row.get('font_scale')}× | {row['status']} |"
        )
    lines.extend(["", "## Scoped state runs", "", "| Profile | Scenario | Result |", "|---|---|---|"])
    lines.extend(f"| {row['profile_id']} | {row['scenario']} | {row['status']} |" for row in state_rows)
    if errors:
        lines.extend(["", "## Failures", "", *[f"- {error}" for error in errors]])
    (output_dir / "NH01_ADAPTIVE_SUPPLEMENTAL_MATRIX_V1.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(
        f"NH01_ADAPTIVE_SUPPLEMENTAL_MATRIX={status} cases={sum(r['status'] == 'PASS' for r in rows)}/33 "
        f"scenarios={sum(r['status'] == 'PASS' for r in state_rows)}/3 total=60 source_sha={args.source_sha}"
    )
    for error in errors:
        print(f"FAIL {error}")
    return 0 if status == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
