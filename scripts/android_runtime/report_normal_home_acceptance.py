#!/usr/bin/env python3
"""Build a conservative 46-case result from one Normal Home validation run."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path


def read_json(path: Path) -> dict:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {}


def status_entry(status: str, reason: str, refs: list[str]) -> dict:
    return {"status": status, "reason": reason, "evidence_refs": refs}


def main() -> int:
    evidence_root, repo_root, build_sha = Path(sys.argv[1]), Path(sys.argv[2]), sys.argv[3]
    matrix_path = repo_root / "design/pages/home/normal_home/engineering/normal_home_acceptance_matrix_v1.json"
    photo_path = repo_root / "design/pages/home/normal_home/engineering/hero_real_photo_fixture_register_v1.json"
    matrix = json.loads(matrix_path.read_text(encoding="utf-8"))
    photo_register = read_json(photo_path)
    result = read_json(evidence_root / "normal_home_brand_title_result.json")
    device = read_json(evidence_root / "device_metrics.json")
    native_screenshot = read_json(evidence_root / "runtime_screenshot.json")
    visual = read_json(evidence_root / "normal-home-v1/09_normal_home_visual_parity_report.json")
    optical = read_json(evidence_root / "normal-home-v1/10_normal_home_optical_background_report.json")
    optical_phase = read_json(evidence_root / "normal-home-v1/optical_frame_phase.json")
    evidence_dir = evidence_root / "normal-home-v1"

    suite_pass = result.get("normal_home_suite_exit_code") == 0
    targeted_pass = result.get("targeted_exit_code") == 0
    safe_paths = [
        evidence_dir / "normal_home_safe_overflow_320x480_start.png",
        evidence_dir / "normal_home_safe_overflow_320x480_hero_reached.png",
        evidence_dir / "normal_home_safe_overflow_320x480.json",
    ]
    safe_pass = suite_pass and all(p.is_file() and p.stat().st_size > 0 for p in safe_paths)
    optical_status = optical.get("optical_masks", {}).get("status", "NOT_RUN")
    if optical_status not in {"PASS", "FAIL"}:
        optical_status = "NOT_RUN"
    if optical_phase.get("neutral_frame_verified") is not True or optical_phase.get("settings_value_after_launch") != 0:
        optical_status = "NOT_RUN"
    physical_size_match = re.search(r"Physical size:\s*(\d+)x(\d+)", device.get("original_wm_size", ""))
    physical_size = [int(physical_size_match.group(1)), int(physical_size_match.group(2))] if physical_size_match else None

    status_by_id: dict[str, dict] = {}

    def add(ids: list[str], status: str, reason: str, refs: list[str]) -> None:
        entry = status_entry(status, reason, refs)
        for case_id in ids:
            status_by_id[case_id] = entry

    add(
        ["G01", "G04"],
        "PASS" if suite_pass else "NOT_RUN",
        "JVM NormalHomeSafeOverflowContractTest checks the 1080x1920 frozen identity and zero-inset 1080x2340 geometry; the Android build job must pass before this runner job starts.",
        ["normal_home_suite_instrumentation.log", "Build CI unit-test job"],
    )
    g02_status = "PASS" if suite_pass and physical_size == [1080, 1920] else "BLOCKED_INFRA"
    add(
        ["G02"],
        g02_status,
        "Edge-to-edge insets and one window origin are measured by instrumentation. PASS requires the runner's original physical size to be 1080x1920; otherwise the gate's wm override cannot be presented as a real 9:16 profile.",
        ["device_metrics.json", "normal-home-measurement-logcat.log", "normal_home_suite_instrumentation.log"],
    )
    add(
        ["G03"],
        "BLOCKED_INFRA",
        "The API28 job reports one original physical panel size; it does not provide separate physical 19.5:9, 20:9, and 21:9 devices. The 1080x2340 arithmetic test is not physical-profile evidence.",
        ["device_metrics.json", "normal_home_v1.sh"],
    )
    add(
        ["G06", "G07"],
        "NOT_RUN",
        "This run does not exercise the complete width/density matrix or all fontScale 1.0/1.15/1.3 profiles.",
        ["device_metrics.json", "normal_home_v1.sh"],
    )
    add(
        ["G05"],
        "PASS" if suite_pass else "NOT_RUN",
        "API28 Compose instrumentation uses RTL with asymmetric 34dp/12dp logical insets and verifies guest, CTA, and camera bounds remain inside the safe width.",
        ["normal_home_suite_instrumentation.log"],
    )
    add(
        ["G08"],
        "PASS" if safe_pass else ("FAIL" if suite_pass else "NOT_RUN"),
        "API28 instrumentation measures a 320x480dp Compose viewport at fontScale 1.3, verifies at least 160dp of scroll viewport and pinned actions, and saves before/after PNGs. A companion Compose matrix rechecks density 1/3 and fontScale 1.0/1.3. These are Compose viewport fixtures, not a physical 320x480 device profile.",
        ["normal_home_suite_instrumentation.log", "normal_home_safe_overflow_320x480.json", "normal_home_safe_overflow_320x480_start.png", "normal_home_safe_overflow_320x480_hero_reached.png"],
    )
    add(
        ["G09"],
        "PASS" if suite_pass else "NOT_RUN",
        "Instrumentation verifies guest avatar, signed-in profile fallback, all-catches action, CTA container, and camera target dimensions at or above 48dp.",
        ["normal_home_suite_instrumentation.log"],
    )
    add(
        ["V01", "V02", "V03", "V04"],
        "NOT_RUN",
        "Screenshots and structural checks exist, but the independent Frozen hierarchy, single-record/error-state and metadata visual review is incomplete.",
        ["01_normal_home_first_card.png", "02_normal_home_second_card.png", "09_normal_home_visual_parity_report.json"],
    )
    add(
        ["V05"],
        "REVIEW_REQUIRED",
        "Morning Lake source/runtime byte identity is verified, while the flattened Frozen page and the registered background scene remain distinct visual authorities.",
        ["08_normal_home_background_parity_report.json", "10_normal_home_optical_background_report.json"],
    )
    add(
        ["V06"],
        optical_status,
        "The unresized 1080x1920 Android runner capture is classified with the pixel-target JSON RGB thresholds and source ROIs. Optical captures run with animator_duration_scale=0 before process relaunch; HomeMotionState fixes camera sceneTime at zero (breath scale 1, sweep alpha 0). Inclusive bbox edge tolerance is max(3px, 0.003*Wsafe). The gate applies a wm size override, so this is not evidence of a physical 1080x1920 panel.",
        ["10_normal_home_optical_background_report.json", "optical_frame_phase.json", "runtime_screenshot.json"],
    )
    add(
        ["S01"],
        "PASS" if suite_pass else "NOT_RUN",
        "The unresolved archive state keeps statistics and capture controls without inventing recent-catch content.",
        ["normal_home_suite_instrumentation.log"],
    )
    add(
        ["S02", "S03", "S04", "S05", "S06", "S07", "S08", "S09", "S10"],
        "NOT_RUN",
        "The requested state-transition evidence is not covered by this targeted run.",
        ["normal_home_acceptance_matrix_v1.json"],
    )
    add(
        ["S11", "S12", "S13", "S14"],
        "PASS" if suite_pass and targeted_pass else "NOT_RUN",
        "Instrumentation verifies that a Home card click delivers the selected FishRecord ID to its caller, plus single-record centering, manual multi-record paging, and long metadata rendering.",
        ["targeted-instrumentation.log", "normal_home_suite_instrumentation.log"],
    )
    add(
        ["S15"],
        "NOT_RUN",
        "Guest and signed-in V2 fallback, profile route, and local URL success/failure rendering are covered; an authorized real remote-avatar source is not present.",
        ["normal_home_suite_instrumentation.log", "NormalHomeAvatarContractTest"],
    )
    add(
        ["M01", "M02", "M03", "M04", "M05", "M06", "M07", "M08"],
        "NOT_RUN",
        "A 16-second runtime video and Reduce Motion/lifecycle screenshots are captured, but exact animation keyframes, sweep cadence, lifecycle pause/resume, haptic and sound acceptance are not instrumented.",
        ["11_Normal_Home_Runtime_16s.mp4", "12_normal_home_reduce_motion.png", "13_normal_home_lifecycle_resumed.png"],
    )

    for case_id in ["F01", "F02", "F03", "F04", "F05", "F06", "F07", "F08"]:
        status_by_id[case_id] = {
            **status_entry(
                "NOT_RUN",
                "A source URL/license, original provenance, or case-matched real-photo fixture is unavailable; synthetic test images are not counted as real-photo evidence.",
                ["hero_real_photo_fixture_register_v1.json"],
            ),
            "fixture_register_status": "PROVENANCE_PENDING",
        }

    cases = []
    for row in matrix["cases"]:
        status = status_by_id.get(row["id"], status_entry("NOT_RUN", "No executable evidence in this run.", []))
        cases.append({**row, "acceptance_status": status["status"], "acceptance_reason": status["reason"], "evidence_refs": status["evidence_refs"], **({"fixture_register_status": status["fixture_register_status"]} if "fixture_register_status" in status else {})})

    apk_info: dict[str, str] = {}
    provenance_path = evidence_root / "apk_provenance.txt"
    if provenance_path.exists():
        for line in provenance_path.read_text(encoding="utf-8").splitlines():
            if "=" in line:
                key, value = line.split("=", 1)
                apk_info[key] = value
    metric_log = evidence_root / "normal-home-measurement-logcat.log"
    metric_text = metric_log.read_text(encoding="utf-8", errors="replace") if metric_log.exists() else ""
    metric_match = re.search(r"density=([^ ]+) fontScale=([^ ]+) safeInsetsPhysicalPx=([^ ]+)", metric_text)
    inset_fields: dict[str, object] = {"logical_start_top_end_bottom_px": None, "physical_left_top_right_bottom_px": None}
    geometry_fields: dict[str, object] = {}
    if metric_match:
        inset_values = [float(v) for v in metric_match.group(3).split(",") if v]
        if len(inset_values) == 4:
            inset_fields = {
                "physical_left_top_right_bottom_px": inset_values,
            }
        direction = re.search(r"layoutDirection=([^ ]+)", metric_text)
        logical = re.search(r"safeInsetsLogicalPx=([^ ]+)", metric_text)
        scale = re.search(r"scalePhysical=([^ ]+)", metric_text)
        vertical_delta = re.search(r"verticalDeltaPx=([^ ]+)", metric_text)
        window_px = re.search(r"windowPx=([0-9.]+)x([0-9.]+)", metric_text)
        geometry_fields = {
            "layout_direction": direction.group(1) if direction else None,
            "logical_start_top_end_bottom_px": [float(v) for v in logical.group(1).split(",")] if logical else None,
            "scale_physical": float(scale.group(1)) if scale else None,
            "vertical_delta_px": float(vertical_delta.group(1)) if vertical_delta else None,
            "compose_window_width_px": float(window_px.group(1)) if window_px else None,
            "compose_window_height_px": float(window_px.group(2)) if window_px else None,
        }

    screenshot_dimensions = native_screenshot.get("dimensions")
    payload = {
        "schema_version": 1,
        "matrix_source": "design/pages/home/normal_home/engineering/normal_home_acceptance_matrix_v1.json",
        "build_commit_sha": build_sha,
        "workflow_run_id": __import__("os").environ.get("GITHUB_RUN_ID"),
        "app_apk_sha256": apk_info.get("APP_APK_SHA256"),
        "app_apk_bytes": int(apk_info["APP_APK_BYTES"]) if apk_info.get("APP_APK_BYTES", "").isdigit() else None,
        "test_apk_sha256": apk_info.get("TEST_APK_SHA256"),
        "test_apk_bytes": int(apk_info["TEST_APK_BYTES"]) if apk_info.get("TEST_APK_BYTES", "").isdigit() else None,
        "device": {
            "api_level": device.get("api_level"),
            "wm_size_at_capture": device.get("wm_size"),
            "wm_size_before_gate_override": device.get("original_wm_size"),
            "physical_size_px": physical_size,
            "density": device.get("wm_density"),
            "font_scale": device.get("font_scale"),
            "window_width_px": screenshot_dimensions[0] if screenshot_dimensions else None,
            "window_height_px": screenshot_dimensions[1] if screenshot_dimensions else None,
            "insets_px": inset_fields,
            "conversion_and_origin": geometry_fields,
            "screenshot_sha256": native_screenshot.get("sha256"),
            "capture_resized": False,
            "physical_profile_verified": False,
            "mode": "NORMAL_FIXED",
        },
        "frozen_source_sha256": "6ab9d3348b4a9a7e77ddca3a06235b4991798a309bd3512cc6fb9ea7aeb1d377",
        "overall_product_visual_acceptance": "REVIEW_REQUIRED",
        "photo_fixture_register_status": photo_register.get("status"),
        "targeted_test_exit_codes": {
            "targeted": result.get("targeted_exit_code"),
            "normal_home_suite": result.get("normal_home_suite_exit_code"),
            "visual_evidence": result.get("visual_evidence_exit_code"),
        },
        "visual_report_status": visual.get("status"),
        "optical_frame_phase": optical_phase,
        "status_counts": {status: sum(row["acceptance_status"] == status for row in cases) for status in ["PASS", "FAIL", "BLOCKED_INFRA", "NOT_RUN", "REVIEW_REQUIRED"]},
        "cases": cases,
    }
    output = evidence_root / "normal_home_acceptance_report_v1.json"
    output.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"acceptance_report": str(output), "status_counts": payload["status_counts"]}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
