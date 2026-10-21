#!/usr/bin/env python3
"""Validate and package the real Android NH01 adaptive-layout matrix."""
from __future__ import annotations

import argparse
import json
import math
from pathlib import Path
import shutil
from typing import Any

from PIL import Image, ImageDraw, ImageFont


CASES = (
    ("M01", 320, 480, 1.0), ("M02", 320, 480, 1.3), ("M03", 320, 480, 1.5),
    ("M04", 360, 640, 1.0), ("M05", 360, 640, 1.3), ("M06", 360, 640, 1.5),
    ("M07", 390, 844, 1.0), ("M08", 390, 844, 1.3), ("M09", 390, 844, 1.5),
    ("M10", 430, 932, 1.0), ("M11", 430, 932, 1.3), ("M12", 430, 932, 1.5),
)
VARIANTS = {"ADAPTIVE_V2": "adaptive_v2", "V3_BASELINE": "v3_baseline"}


def read_case(raw: Path, profile: str, variant: str) -> dict[str, Any] | None:
    path = raw / f"{profile}-{variant}.json"
    if not path.is_file():
        return None
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return None


def read_execution(execution_dir: Path, profile: str, variant: str) -> dict[str, Any] | None:
    path = execution_dir / f"{profile}-{VARIANTS[variant]}.json"
    if not path.is_file():
        return None
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {"status": "FAIL", "reason": "EXECUTION_RECORD_JSON_INVALID", "exit_code": None}


def classify_execution(record: dict[str, Any] | None) -> tuple[str, str]:
    """Keep a failed execution distinct from a case which never started."""
    if record is None:
        return "MISSING", "execution record missing; the case did not start or its evidence was not collected"
    status = str(record.get("status", "INVALID")).upper()
    reason = str(record.get("reason") or "")
    if status in {"MISSING", "PENDING", "NOT_RUN"}:
        return "MISSING", reason or f"case was not executed (record status={status})"
    if status == "PASS":
        failures = []
        if record.get("exit_code") != 0:
            failures.append(f"runner exit code={record.get('exit_code')}")
        if record.get("device_report_present") is not True:
            failures.append("device report missing")
        if record.get("required_screenshots_present") is not True:
            failures.append("required initial/after-swipe screenshots missing")
        if record.get("profile_settings_match") is not True:
            failures.append("requested device settings did not apply")
        if failures:
            return "FAIL", "; ".join(failures)
        return "PASS", "instrumentation and host evidence completed"
    if status == "RUNNING":
        return "FAIL", reason or "instrumentation started but did not record a terminal result"
    if status == "FAIL":
        return "FAIL", reason or f"instrumentation failed (exit code={record.get('exit_code')})"
    return "FAIL", reason or f"unrecognized execution record status={status}"


def report_status(raw: Path, profile: str, variant_key: str, payload: dict[str, Any] | None) -> str:
    if payload is not None:
        return str(payload.get("status", "INVALID")).upper()
    path = raw / f"{profile}-{variant_key}.json"
    return "INVALID" if path.is_file() else "MISSING"


def close(a: float, b: float, tolerance: float = 0.02) -> bool:
    return math.isfinite(a) and abs(a - b) <= tolerance


def validate_case(payload: dict[str, Any], profile: str, width: int, height: int, font: float,
                  variant: str, source_sha: str, raw: Path) -> list[str]:
    problems: list[str] = []
    if payload.get("status") != "PASS":
        problems.append(f"runtime status={payload.get('status')}")
    if payload.get("execution_mode") != "ANDROID_API28_RUNTIME":
        problems.append("capture is not API 28 Android runtime")
    if payload.get("layout_variant") != variant:
        problems.append(f"layout variant={payload.get('layout_variant')}")
    if payload.get("source_commit") != source_sha:
        problems.append(f"source SHA={payload.get('source_commit')}")
    if not close(float(payload.get("system_font_scale", -1)), font, 0.02):
        problems.append(f"font scale={payload.get('system_font_scale')} expected {font}")
    if payload.get("display_resolution_px") != [width, height]:
        problems.append(f"display resolution={payload.get('display_resolution_px')} expected {[width, height]}")
    if payload.get("density_dpi") != 160:
        problems.append(f"display density={payload.get('density_dpi')} dpi expected 160")
    bounds = payload.get("bounds_in_window_px", {})
    required_bounds = (
        "gemini-home-screen", "gemini-home-brand-title", "gemini-home-stats-bar",
        "gemini-home-section-title", "gemini-home-catch-card-nh01-catch-001",
        "gemini-home-footer-cta", "gemini-home-cta-label", "gemini-home-camera",
    )
    missing = [tag for tag in required_bounds if tag not in bounds]
    if missing:
        problems.append("missing bounds: " + ", ".join(missing))
    metrics = payload.get("metrics", {})
    ratio = float(metrics.get("card_aspect_ratio", 0))
    if abs(ratio / (247 / 293) - 1) > 0.01:
        problems.append(f"card ratio={ratio:.5f} exceeds 1% error")
    if float(metrics.get("card_track_horizontal_center_error_dp", 999)) > 2:
        problems.append("card is more than 2dp off center")
    if float(metrics.get("card_actual_width_dp", 0)) > float(metrics.get("card_target_width_dp", 0)) + 1:
        problems.append("card width exceeds its responsive policy target")
    if metrics.get("cta_camera_overlap") is not False:
        problems.append("CTA and camera overlap or were not measured")
    if float(metrics.get("camera_safe_bottom_clearance_dp", -999)) < -1:
        problems.append("camera touches or overlaps the safe bottom inset")
    data = payload.get("data", {})
    ids = data.get("catch_ids", [])
    if ids != ["nh01-catch-001", "nh01-catch-002"] or data.get("unique_catch_count") != 2:
        problems.append(f"two-catch IDs/uniqueness failed: {ids}")
    if data.get("pager_page_count") != 2 or data.get("same_time_duplicate_ids") is not False:
        problems.append("finite two-page uniqueness assertion failed")
    if data.get("swipe_reached_second_id") is not True:
        problems.append("real-ID horizontal swipe was not verified")
    if data.get("required_card_text_accessible") is not True:
        problems.append("species, measurement, and metadata accessibility assertion failed")
    screenshot_name = payload.get("screenshots", {}).get("initial")
    image_path = raw / str(screenshot_name)
    if not image_path.is_file():
        problems.append(f"screenshot missing: {screenshot_name}")
    else:
        try:
            with Image.open(image_path) as image:
                if image.size != (width, height):
                    problems.append(f"screenshot pixels={image.size} expected {(width, height)}")
        except OSError as error:
            problems.append(f"screenshot cannot be decoded: {error}")
    return problems


def copy_screenshot(raw: Path, payload: dict[str, Any], destination: Path, name: str) -> bool:
    source = raw / str(payload.get("screenshots", {}).get("initial", ""))
    if not source.is_file():
        return False
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, destination / name)
    return True


def make_pair(raw: Path, baseline: dict[str, Any], adaptive: dict[str, Any], target: Path, profile: str) -> None:
    before_path = raw / str(baseline["screenshots"]["initial"])
    after_path = raw / str(adaptive["screenshots"]["initial"])
    with Image.open(before_path) as before_source, Image.open(after_path) as after_source:
        before, after = before_source.convert("RGB"), after_source.convert("RGB")
    scale = 0.78
    width = max(1, round(before.width * scale))
    height = max(1, round(before.height * scale))
    before = before.resize((width, height), Image.Resampling.LANCZOS)
    after = after.resize((width, height), Image.Resampling.LANCZOS)
    label_height, gutter = 26, 12
    sheet = Image.new("RGB", (width * 2 + gutter, height + label_height), "#ffffff")
    draw = ImageDraw.Draw(sheet)
    draw.text((0, 4), f"{profile} | V3 baseline", fill="#15202b")
    draw.text((width + gutter, 4), f"{profile} | Adaptive V1", fill="#15202b")
    sheet.paste(before, (0, label_height))
    sheet.paste(after, (width + gutter, label_height))
    target.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(target, format="PNG", optimize=True)


def make_contact_sheet(pairs: list[tuple[str, Path, int, int, float]], output: Path) -> None:
    if len(pairs) != len(CASES):
        return
    cell_width = 512
    gutter, caption = 16, 28
    previews: list[tuple[str, Image.Image]] = []
    for profile, path, _, _, font_scale in pairs:
        with Image.open(path) as source:
            image = source.convert("RGB")
        scaled_height = round(image.height * cell_width / max(image.width, 1))
        preview = image.resize((cell_width, scaled_height), Image.Resampling.LANCZOS)
        previews.append((f"{profile} | font {font_scale:.1f}x", preview))
    row_heights = [max(previews[index][1].height for index in row) + caption
                   for row in (range(0, 3), range(3, 6), range(6, 9))]
    sheet = Image.new("RGB", (cell_width * 3, sum(row_heights) + 4 * gutter), "#eef2f4")
    draw = ImageDraw.Draw(sheet)
    y = gutter
    for row in range(3):
        row_height = row_heights[row]
        for col in range(3):
            index = row * 3 + col
            title, preview = previews[index]
            x = col * cell_width + gutter // 2
            draw.text((x, y), title, fill="#10202c")
            sheet.paste(preview, (x, y + caption))
        y += row_height + gutter
    output.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(output, format="PNG", optimize=True)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--raw-dir", type=Path, required=True)
    parser.add_argument("--execution-dir", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--source-sha", required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--run-url", required=True)
    parser.add_argument("--contract", type=Path, required=True)
    args = parser.parse_args()
    raw = args.raw_dir.resolve()
    execution_dir = args.execution_dir.resolve()
    output = args.output_dir.resolve()
    matrix_dir = output / "NH01_RUNTIME_MATRIX_V1"
    matrix_dir.mkdir(parents=True, exist_ok=True)
    shutil.copy2(args.contract, output / "NH01_ADAPTIVE_VISUAL_BALANCE_V2.md")

    report_rows: list[dict[str, Any]] = []
    bound_rows: list[dict[str, Any]] = []
    metric_rows: list[dict[str, Any]] = []
    regression_rows: list[dict[str, Any]] = []
    pairs: list[tuple[str, Path, int, int, float]] = []
    all_problems: list[str] = []
    for profile, width, height, font in CASES:
        candidate = read_case(raw, profile, "adaptive_v2")
        baseline = read_case(raw, profile, "v3_baseline")
        candidate_execution = read_execution(execution_dir, profile, "ADAPTIVE_V2")
        baseline_execution = read_execution(execution_dir, profile, "V3_BASELINE")
        candidate_execution_status, candidate_execution_reason = classify_execution(candidate_execution)
        baseline_execution_status, baseline_execution_reason = classify_execution(baseline_execution)
        candidate_report_status = report_status(raw, profile, "adaptive_v2", candidate)
        baseline_report_status = report_status(raw, profile, "v3_baseline", baseline)

        candidate_validation = [] if candidate is None else validate_case(
            candidate, profile, width, height, font, "ADAPTIVE_V2", args.source_sha, raw,
        )
        baseline_validation = [] if baseline is None else validate_case(
            baseline, profile, width, height, font, "V3_BASELINE", args.source_sha, raw,
        )
        candidate_problems: list[str] = []
        baseline_problems: list[str] = []
        if candidate_execution_status != "PASS":
            candidate_problems.append(f"execution {candidate_execution_status}: {candidate_execution_reason}")
        if candidate_report_status != "PASS":
            candidate_problems.append(f"device report {candidate_report_status.lower()}")
        candidate_problems.extend(candidate_validation)
        if baseline_execution_status != "PASS":
            baseline_problems.append(f"execution {baseline_execution_status}: {baseline_execution_reason}")
        if baseline_report_status != "PASS":
            baseline_problems.append(f"device report {baseline_report_status.lower()}")
        baseline_problems.extend(baseline_validation)

        candidate_status = "PASS" if not candidate_problems else (
            "MISSING" if candidate_execution_status == "MISSING" and candidate_report_status == "MISSING" else "FAIL"
        )
        baseline_status = "PASS" if not baseline_problems else (
            "MISSING" if baseline_execution_status == "MISSING" and baseline_report_status == "MISSING" else "FAIL"
        )
        row_problems = candidate_problems + [f"baseline: {problem}" for problem in baseline_problems]
        result_status = "PASS" if candidate_status == baseline_status == "PASS" else (
            "FAIL" if "FAIL" in {candidate_status, baseline_status} else "MISSING"
        )
        report_rows.append({"profile": profile, "size_dp": [width, height], "font_scale": font,
                            "candidate_status": candidate_status, "baseline_status": baseline_status,
                            "candidate_report_status": candidate_report_status,
                            "baseline_report_status": baseline_report_status,
                            "candidate_execution": candidate_execution,
                            "baseline_execution": baseline_execution,
                            "status": result_status, "problems": row_problems})
        all_problems.extend(f"{profile}: {problem}" for problem in row_problems)

        if candidate is not None:
            copy_screenshot(raw, candidate, matrix_dir, f"{profile}.png")
            bound_rows.append({"profile": profile, "layout_variant": "ADAPTIVE_V2",
                               "system_font_scale": candidate.get("system_font_scale"),
                               "display_resolution_px": candidate.get("display_resolution_px"),
                               "configuration_screen_dp": candidate.get("configuration_screen_dp"),
                               "window_bounds_dp": candidate.get("window_bounds_dp"),
                               "safe_insets_dp": candidate.get("safe_insets_dp"),
                               "bounds_in_window_px": candidate.get("bounds_in_window_px"),
                               "screenshot": candidate.get("screenshots", {}).get("initial")})
            metric_rows.append({"profile": profile, **candidate.get("metrics", {}),
                                "card_width_fraction": 0.70, "card_aspect_ratio_target": 247 / 293,
                                "status": candidate_status})
            regression_rows.append({"profile": profile, **candidate.get("data", {}),
                                    "visible_card_ids_unique": len(set(candidate.get("data", {}).get("initial_visible_card_ids", []))) == 2,
                                    "status": candidate_status})
        if baseline is not None and candidate is not None:
            pair_file = matrix_dir / "COMPARISON" / f"{profile}_V3_vs_Adaptive.png"
            before = raw / str(baseline.get("screenshots", {}).get("initial", ""))
            after = raw / str(candidate.get("screenshots", {}).get("initial", ""))
            if before.is_file() and after.is_file():
                make_pair(raw, baseline, candidate, pair_file, profile)
                pairs.append((profile, pair_file, width, height, font))

    (output / "NH01_BOUNDS_MATRIX_V1.json").write_text(json.dumps({
        "schema": "NH01_BOUNDS_MATRIX_V1", "source_sha": args.source_sha,
        "workflow_run_id": args.run_id, "workflow_run_url": args.run_url,
        "execution_mode": "ANDROID_API28_EMULATOR_RUNTIME", "profiles": bound_rows,
    }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (output / "NH01_LAYOUT_METRICS_V1.json").write_text(json.dumps({
        "schema": "NH01_LAYOUT_METRICS_V1", "source_sha": args.source_sha,
        "candidate_parameters": {"width_fraction": 0.76, "max_width_dp": 360, "aspect_ratio": "247:293",
                                 "compact_if_width_lt_dp": 360, "compact_if_height_lt_dp": 700},
        "profiles": metric_rows,
    }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (output / "NH01_TWO_CATCH_REGRESSION_V1.json").write_text(json.dumps({
        "schema": "NH01_TWO_CATCH_REGRESSION_V1", "source_sha": args.source_sha,
        "test_fixture_ids": ["nh01-catch-001", "nh01-catch-002"],
        "profiles": regression_rows,
    }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    contact = output / "NH01_V3_VS_CANDIDATE_COMPARISON_V1.png"
    make_contact_sheet(pairs, contact)
    lines = [
        "# NH01 Visual Acceptance Report V1", "",
        f"- Source SHA: `{args.source_sha}`",
        f"- Android runtime: [API 28 emulator workflow run {args.run_id}]({args.run_url})",
        "- Physical Android handset: `NOT_RUN`; the CI API 28 Pixel 2 emulator is reported separately.",
        "- Candidate uses the production `HomeScreen`; exactly two stable catch IDs are injected per case.",
        "- Full-resolution Android screenshots: `NH01_RUNTIME_MATRIX_V1/`; paired same-runtime V3 screenshots are in `NH01_RUNTIME_MATRIX_V1/COMPARISON/`.",
        "", "| Case | Display | System font | Adaptive V2 | V3 baseline | Result | Details |",
        "|---|---:|---:|---|---|---|---|",
    ]
    for row in report_rows:
        problems = "; ".join(row["problems"]) or "all asserted bounds and interactions passed"
        lines.append(f"| {row['profile']} | {row['size_dp'][0]}×{row['size_dp'][1]}dp | {row['font_scale']:.1f}× | "
                     f"{row['candidate_status']} | {row['baseline_status']} | {row['status']} | {problems} |")
    final_status = "PASS" if len(report_rows) == 12 and all(row["status"] == "PASS" for row in report_rows) else (
        "FAIL" if any(row["status"] == "FAIL" for row in report_rows) else "MISSING"
    )
    (output / "NH01_ADAPTIVE_MATRIX_SUMMARY_V1.json").write_text(json.dumps({
        "schema": "NH01_ADAPTIVE_MATRIX_SUMMARY_V1", "status": final_status,
        "source_sha": args.source_sha, "workflow_run_id": args.run_id,
        "workflow_run_url": args.run_url, "execution_mode": "ANDROID_API28_EMULATOR_RUNTIME",
        "physical_handset_status": "NOT_RUN",
        "profile_count": len(report_rows), "passed_profiles": sum(row["status"] == "PASS" for row in report_rows),
        "cases": report_rows,
    }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    lines.extend([
        "", f"**Matrix result: {final_status}.** This report is generated from the actual device test outputs; missing or failed cases remain explicit.",
        "", "## Candidate policy notes", "",
        "Card shape remains 247:293. The candidate width target is 70% of the measured carousel track, capped at 337.2dp; any reduction to keep a compact viewport usable is proportional. Actual card width and title/card/footer gaps are recorded per case in `NH01_LAYOUT_METRICS_V1.json`.",
        "", "The side-by-side image is a contact sheet of same-profile full-window Android captures. The raw case PNGs remain the visual source of record.",
    ])
    (output / "NH01_VISUAL_ACCEPTANCE_REPORT_V1.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"NH01_ADAPTIVE_MATRIX={final_status} profiles={sum(row['status'] == 'PASS' for row in report_rows)}/12 source_sha={args.source_sha}")
    if all_problems:
        for problem in all_problems:
            print(f"FAIL {problem}")
    return 0 if final_status == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
