#!/usr/bin/env python3
"""Bind an API28 Normal Home runtime run to its exact source SHA and viewer bundle."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
from datetime import datetime, timezone
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
VIEWER_DATA = ROOT / "design/manager/nh01_acceptance/data"
VALIDATION_APK_NAME = "NH01_VALIDATION_DEBUG.apk"


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read_json(path: Path, default: dict | None = None) -> dict:
    if not path.is_file():
        return default or {}
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path: Path, payload: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def copy_if_present(source: Path, destination: Path) -> bool:
    if not source.is_file() or source.stat().st_size == 0:
        return False
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, destination)
    return True


def artifact_link(value: str | None) -> str | None:
    return value if value and value.startswith("https://") else None


def logged_rect(path: Path, name: str) -> list[float] | None:
    if not path.is_file():
        return None
    pattern = re.compile(
        rf"(?:^|\s){re.escape(name)}=(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?),"
        rf"(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)(?=\s|$)"
    )
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        if "NORMAL_HOME_HIERARCHY_EVIDENCE" not in line:
            continue
        match = pattern.search(line)
        if match:
            return [float(value) for value in match.groups()]
    return None


def rect_contains(outer: list[float] | None, inner: list[float] | None) -> bool:
    return bool(
        outer and inner
        and inner[0] >= outer[0]
        and inner[1] >= outer[1]
        and inner[2] <= outer[2]
        and inner[3] <= outer[3]
    )


def rects_separate(first: list[float] | None, second: list[float] | None) -> bool:
    return bool(
        first and second
        and (first[3] <= second[1] or second[3] <= first[1])
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--evidence-dir", type=Path, required=True)
    parser.add_argument("--source-sha", required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--run-url", required=True)
    parser.add_argument("--artifact-url", default="")
    parser.add_argument("--pr-url", default="https://github.com/pan277942135/Yujian_App/pulls")
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--test-apk", type=Path)
    parser.add_argument("--model-contract", type=Path)
    parser.add_argument("--model-trace", type=Path)
    parser.add_argument("--device-metadata", type=Path, required=True)
    parser.add_argument("--artifact-dir", type=Path)
    parser.add_argument("--web-root", type=Path, required=True)
    args = parser.parse_args()

    evidence_dir = args.evidence_dir.resolve()
    web_root = args.web_root.resolve()
    if len(args.source_sha) != 40 or any(char not in "0123456789abcdef" for char in args.source_sha.lower()):
        raise SystemExit("Source SHA must be a full 40-character commit ID")
    if not args.apk.is_file() or args.apk.stat().st_size == 0:
        raise SystemExit("Build APK is missing; runtime package cannot claim delivery")
    device = read_json(args.device_metadata)
    runtime_result = read_json(evidence_dir / "runtime_gate_result.json")
    closure = read_json(evidence_dir / "normal-home-v1/Normal_Home_Runtime_Closure_Result.json")
    pixel = read_json(evidence_dir / "normal-home-v1/Normal_Home_Pixel_Diff_Report.json")
    responsive = read_json(evidence_dir / "normal-home-v1/Normal_Home_Responsive_Runtime_Report.json")
    bounds = read_json(evidence_dir / "normal-home-v1/Normal_Home_Runtime_Bounds_Report.json")
    classification = runtime_result.get("classification", "BLOCKED_INFRA")
    if runtime_result and runtime_result.get("build_sha") != args.source_sha:
        raise SystemExit("Runtime Harness build_sha does not match the checked-out source SHA")

    evidence_root = web_root / "design/manager/nh01_acceptance/evidence" / args.source_sha
    selected_files = [
        ("infra/hosted_runner_kvm_diagnostic_reference.log", "NH01_KVM_Diagnostic_Reference.log"),
        ("infra/window_insets_dump.txt", "NH01_Window_Insets_Dump.txt"),
        ("normal-home-v1/01_normal_home_first_card.png", "NH01_Android_FirstCard.png"),
        ("normal-home-v1/04_normal_home_runtime_1080x1920.png", "NH01_Android_Runtime_1080x1920.png"),
        ("normal-home-v1/normal_home_runtime_1080x1920_fs1_0.png", "NH01_Android_Runtime_1080x1920_FontScale1_0.png"),
        ("normal-home-v1/normal_home_runtime_1080x1920_fs1_3.png", "NH01_Android_Runtime_1080x1920_FontScale1_3.png"),
        ("normal-home-v1/normal_home_runtime_1080x1920_fs1_5.png", "NH01_Android_Runtime_1080x1920_FontScale1_5.png"),
        ("normal-home-v1/normal_home_runtime_1080x2340_fs1_0.png", "NH01_Android_Runtime_1080x2340_FontScale1_0.png"),
        ("normal-home-v1/normal_home_profile_1080x1920_fs1_0.json", "NH01_Android_Profile_FontScale1_0.json"),
        ("normal-home-v1/normal_home_profile_1080x1920_fs1_0_metrics.log", "NH01_Android_Metrics_FontScale1_0.log"),
        ("normal-home-v1/normal_home_profile_1080x1920_fs1_0_insets_instrumentation.log", "NH01_Android_Insets_FontScale1_0.log"),
        ("normal-home-v1/normal_home_profile_1080x1920_fs1_3.json", "NH01_Android_Profile_FontScale1_3.json"),
        ("normal-home-v1/normal_home_profile_1080x1920_fs1_3_metrics.log", "NH01_Android_Metrics_FontScale1_3.log"),
        ("normal-home-v1/normal_home_profile_1080x1920_fs1_3_insets_instrumentation.log", "NH01_Android_Insets_FontScale1_3.log"),
        ("normal-home-v1/normal_home_profile_1080x2340_fs1_0.json", "NH01_Android_Profile_1080x2340.json"),
        ("normal-home-v1/normal_home_profile_1080x2340_fs1_0_metrics.log", "NH01_Android_Metrics_1080x2340.log"),
        ("normal-home-v1/normal_home_profile_1080x2340_fs1_0_insets_instrumentation.log", "NH01_Android_Insets_1080x2340.log"),
        ("normal-home-v1/03_normal_home_frozen_1080x1920.png", "NH01_Frozen_1080x1920.png"),
        ("normal-home-v1/05_normal_home_frozen_runtime_side_by_side.png", "NH01_Frozen_Android_SideBySide.png"),
        ("normal-home-v1/11_Normal_Home_Runtime_18s.mp4", "NH01_Android_Runtime_Motion.mp4"),
        ("normal-home-v1/Normal_Home_Runtime_Bounds_Report.json", "NH01_Android_Bounds.json"),
        ("normal-home-v1/Normal_Home_Pixel_Diff_Report.json", "NH01_Android_Pixel_Diff.json"),
        ("normal-home-v1/NH01_Android_Pixel_Heatmap.png", "NH01_Android_Pixel_Heatmap.png"),
        ("normal-home-v1/NH01_Android_Overlay.png", "NH01_Android_Overlay.png"),
        ("normal-home-v1/NH01_Android_Pixel_Heatmap_Report.json", "NH01_Android_Pixel_Heatmap_Report.json"),
        ("normal-home-v1/Normal_Home_Responsive_Runtime_Report.json", "NH01_Android_Responsive.json"),
        ("normal-home-v1/Normal_Home_Runtime_Closure_Result.json", "NH01_Android_Closure.json"),
        ("normal-home-v1/Normal_Home_Runtime_Visual_Audit_Report.md", "NH01_Android_Visual_QA.md"),
        ("normal-home-v1/Normal_Home_Hierarchy_Report.json", "NH01_Android_Hierarchy.json"),
        ("normal-home-v1/Normal_Home_Hero_EvidenceFit_Report.json", "NH01_Android_Hero_EvidenceFit.json"),
        ("normal-home-v1/Normal_Home_Typography_Runtime_Report.json", "NH01_Android_Typography.json"),
        ("normal-home-v1/Normal_Home_Motion_Runtime_Report.json", "NH01_Android_Motion.json"),
        ("normal-home-v1/Hero_Fixture_Preflight_Report.json", "NH01_Hero_Fixture_Preflight.json"),
        ("runtime_gate_result.json", "NH01_Runtime_Gate_Result.json"),
    ]
    private_files = [
        ("instrumentation.log", "NH01_Instrumentation.log"),
        ("normal-home-v1/normal-home-motion-instrumentation.log", "NH01_Motion_Instrumentation.log"),
    ]
    copied: dict[str, str] = {}
    for source_rel, target_name in selected_files:
        if copy_if_present(evidence_dir / source_rel, evidence_root / target_name):
            copied[target_name] = f"evidence/{args.source_sha}/{target_name}"

    screenshot_path = evidence_root / "NH01_Android_Runtime_1080x1920.png"
    screenshot_ok = screenshot_path.is_file()
    if screenshot_ok:
        with Image.open(screenshot_path) as image:
            screenshot_ok = image.size == (1080, 1920)
    if screenshot_ok and not device:
        raise SystemExit("Runtime screenshot exists but device metadata is missing")

    apk_sha = sha256(args.apk)
    artifact_dir = args.artifact_dir.resolve() if args.artifact_dir else None
    artifact_manifest = None
    if artifact_dir:
        artifact_dir.mkdir(parents=True, exist_ok=True)
        # Preserve the long-standing filename for existing consumers and add
        # the explicitly requested installable validation APK name.
        shutil.copy2(args.apk, artifact_dir / "app-debug.apk")
        shutil.copy2(args.apk, artifact_dir / VALIDATION_APK_NAME)
        if args.test_apk and args.test_apk.is_file():
            shutil.copy2(args.test_apk, artifact_dir / "app-debug-androidTest.apk")
        if args.model_contract and args.model_contract.is_file():
            shutil.copy2(args.model_contract, artifact_dir / "model_release_contract.json")
        if args.model_trace and args.model_trace.is_file():
            shutil.copy2(args.model_trace, artifact_dir / "apk_model_trace.json")
        if args.device_metadata.is_file():
            shutil.copy2(args.device_metadata, artifact_dir / "device_metadata.json")
        for target_name, site_relative_path in copied.items():
            site_path = web_root / "design/manager/nh01_acceptance" / site_relative_path
            artifact_path = artifact_dir / site_relative_path
            if site_path.is_file():
                artifact_path.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(site_path, artifact_path)
        private_copied: dict[str, str] = {}
        for source_rel, target_name in private_files:
            source_path = evidence_dir / source_rel
            relative_path = f"evidence/{args.source_sha}/private/{target_name}"
            if copy_if_present(source_path, artifact_dir / relative_path):
                private_copied[target_name] = relative_path
        (artifact_dir / "runtime_gate_result.json").write_text(
            json.dumps(runtime_result or {"classification": classification}, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        artifact_manifest = {
            "schema_version": 1,
            "source_sha": args.source_sha,
            "workflow_run_id": args.run_id,
            "workflow_run_url": args.run_url,
            "app_apk_filename": VALIDATION_APK_NAME,
            "app_apk_sha256": apk_sha,
            "app_apk_size_bytes": args.apk.stat().st_size,
            "android_test_apk_sha256": sha256(args.test_apk) if args.test_apk and args.test_apk.is_file() else None,
            "classification": classification,
            "device": device,
            "validation_report": "NH01_Android_Validation_Report.md",
            "evidence_files": copied,
            "private_evidence_files": private_copied,
        }
        write_json(artifact_dir / "NH01_Runtime_Manifest_V1.json", artifact_manifest)

        density = float(device.get("density_scale") or (float(device.get("density_dpi") or 480) / 160.0))
        profile_rows: list[dict[str, object]] = []
        for profile_id, expected_scale, screenshot_name in (
            ("1080x1920_fs1_0", 1.0, "NH01_Android_Runtime_1080x1920_FontScale1_0.png"),
            ("1080x1920_fs1_3", 1.3, "NH01_Android_Runtime_1080x1920_FontScale1_3.png"),
            ("1080x2340_fs1_0", 1.0, "NH01_Android_Runtime_1080x2340_FontScale1_0.png"),
        ):
            source_profile = evidence_dir / "normal-home-v1" / f"normal_home_profile_{profile_id}.json"
            profile = read_json(source_profile)
            metrics = evidence_dir / "normal-home-v1" / f"normal_home_profile_{profile_id}_metrics.log"
            root_rect = logged_rect(metrics, "root")
            cta_rect = logged_rect(metrics, "cta_layout")
            cta_text_region_rect = logged_rect(metrics, "cta_text_region")
            cta_text_rect = logged_rect(metrics, "cta_text")
            camera_rect = logged_rect(metrics, "camera_touch")
            screenshot = evidence_dir / "normal-home-v1" / f"normal_home_runtime_{profile_id}.png"
            screenshot_copy = artifact_dir / f"evidence/{args.source_sha}/{screenshot_name}"
            screenshot_dimensions: list[int] | None = None
            if screenshot_copy.is_file():
                try:
                    with Image.open(screenshot_copy) as image:
                        screenshot_dimensions = [image.width, image.height]
                except OSError:
                    screenshot_dimensions = None
            width_dp = ((camera_rect[2] - camera_rect[0]) / density) if camera_rect else None
            height_dp = ((camera_rect[3] - camera_rect[1]) / density) if camera_rect else None
            geometry_ok = bool(
                root_rect and cta_rect and cta_text_region_rect and cta_text_rect and camera_rect
                and width_dp is not None and height_dp is not None
                and width_dp >= 48 and height_dp >= 48
                and rect_contains(root_rect, camera_rect)
                and rect_contains(cta_text_region_rect, cta_text_rect)
                and rects_separate(cta_rect, camera_rect)
                and rects_separate(cta_text_rect, camera_rect)
            )
            if cta_rect and root_rect:
                reference_scale = (root_rect[2] - root_rect[0]) / 1080.0
                geometry_ok = bool(
                    geometry_ok
                    and abs((cta_rect[2] - cta_rect[0]) - 420 * reference_scale) <= 1.5
                    and abs((cta_rect[3] - cta_rect[1]) - 58 * reference_scale) <= 1.5
                )
            profile_rows.append({
                "id": profile_id,
                "expected_font_scale": expected_scale,
                "actual_font_scale": profile.get("font_scale"),
                "status": profile.get("status", "NOT_RUN"),
                "screenshot": screenshot_name if screenshot_copy.is_file() else None,
                "screenshot_dimensions_px": screenshot_dimensions,
                "cta_layout_boundsInWindow_px": cta_rect,
                "cta_text_region_boundsInWindow_px": cta_text_region_rect,
                "cta_text_boundsInWindow_px": cta_text_rect,
                "camera_touch_boundsInWindow_px": camera_rect,
                "camera_touch_size_dp": [round(width_dp, 2), round(height_dp, 2)] if width_dp is not None and height_dp is not None else None,
                "geometry_checks": "PASS" if geometry_ok else ("FAIL" if camera_rect else "NOT_RUN"),
            })

        fs1_0 = next(row for row in profile_rows if row["id"] == "1080x1920_fs1_0")
        fs1_3 = next(row for row in profile_rows if row["id"] == "1080x1920_fs1_3")
        required_profiles = [row for row in profile_rows if row["id"] in {"1080x1920_fs1_0", "1080x1920_fs1_3"}]
        profile_matrix_pass = all(row["status"] == "CAPTURED" and row["geometry_checks"] == "PASS" for row in required_profiles)
        touch_target_status = "PASS" if profile_matrix_pass else (
            "FAIL" if any(row["geometry_checks"] == "FAIL" for row in required_profiles) else "NOT_RUN"
        )
        hero_fixture = read_json(evidence_dir / "normal-home-v1/Hero_Fixture_Preflight_Report.json")
        hero_status = hero_fixture.get("status") or hero_fixture.get("classification") or "NOT_RUN"
        validation_delivered = bool(
            args.apk.is_file()
            and runtime_result.get("install") == "PASS"
            and runtime_result.get("instrumentation") == "PASS"
            and screenshot_ok
        )
        validation_report_lines = [
            "# NH01 Android Validation Debug APK — Build and Test Report",
            "",
            f"- Source commit: `{args.source_sha}`",
            f"- API28 Runtime workflow: [{args.run_id}]({args.run_url})",
            f"- APK: `{VALIDATION_APK_NAME}` ({args.apk.stat().st_size} bytes)",
            f"- APK SHA-256: `{apk_sha}`",
            f"- Android test APK SHA-256: `{sha256(args.test_apk) if args.test_apk and args.test_apk.is_file() else 'NOT_AVAILABLE'}`",
            f"- Device: {device.get('profile', 'Pixel 2')} / API {device.get('api_level', 28)} / {device.get('resolution_px', [1080, 1920])} / {device.get('density_dpi', 480)} dpi",
            "",
            "## Status",
            "",
            f"- `VALIDATION_APK_DELIVERED`: {'PASS' if validation_delivered else 'NOT_PASS'} (requires exact-source APK, API28 install, instrumentation, and native Home screenshot).",
            f"- `ANDROID_BUILD`: {'PASS' if args.apk.is_file() and args.apk.stat().st_size > 0 else 'FAIL'}; APK hash is recorded above.",
            f"- `BASIC_TESTS`: {'PASS' if args.test_apk and args.test_apk.is_file() else 'NOT_PASS'}; the exact-source Gradle test and APK assembly step must complete before the test APK is packaged.",
            f"- `API28_RUNTIME`: `{classification}`; preflight `{runtime_result.get('preflight', 'NOT_RUN')}`, install `{runtime_result.get('install', 'NOT_RUN')}`, instrumentation `{runtime_result.get('instrumentation', 'NOT_RUN')}`, evidence `{runtime_result.get('evidence', 'NOT_RUN')}`.",
            f"- `TOUCH_TARGET`: `{touch_target_status}`; actual `boundsInWindow` rectangles and recomputable geometry are included for fontScale 1.0 and 1.3 below.",
            f"- `FONT_SCALE_1_3`: `{fs1_3['status']}`; native screenshot and profile JSON are packaged when captured.",
            f"- `HERO_FIXTURE_MATRIX`: `{hero_status}`; see `evidence/{args.source_sha}/NH01_Hero_Fixture_Preflight.json`.",
            "- `FIGMA_VISUAL_PARITY`: `NOT_RUN` by explicit authorization for this validation APK; no Figma or full-page visual PASS is claimed.",
            "",
            "## Runtime bounds and screenshots",
            "",
            "The rectangles below are copied from API28 instrumentation `boundsInWindow` logs. Touch size is converted using the measured density; CTA container and camera touch target remain separate measurements.",
            "",
            "| Profile | Status | CTA container bounds (px) | CTA text region (px) | Camera touch bounds (px) | Camera target (dp) | Screenshot |",
            "|---|---|---|---|---:|---|",
        ]
        for row in profile_rows:
            cta_bounds = row["cta_layout_boundsInWindow_px"] or "NOT_RUN"
            text_region_bounds = row["cta_text_region_boundsInWindow_px"] or "NOT_RUN"
            camera_bounds = row["camera_touch_boundsInWindow_px"] or "NOT_RUN"
            camera_dp = "NOT_RUN" if row["camera_touch_size_dp"] is None else " × ".join(str(v) for v in row["camera_touch_size_dp"])
            screenshot_rel = row["screenshot"] or "NOT_RUN"
            validation_report_lines.append(
                f"| {row['id']} | {row['status']} / {row['geometry_checks']} | `{cta_bounds}` | `{text_region_bounds}` | `{camera_bounds}` | {camera_dp} | `{screenshot_rel}` |"
            )
        validation_report_lines.extend([
            "",
            "## Evidence files",
            "",
            "- `Normal_Home_Runtime_Bounds_Report.json` — full API28 hierarchy, geometry checks, source and screenshot provenance.",
            "- `Normal_Home_Runtime_Closure_Result.json` — separate visual/runtime closure status.",
            "- `NH01_Android_Runtime_1080x1920.png` — canonical runtime screenshot; profile screenshots and actual metrics logs are listed above and in the evidence directory.",
            "- `SHA256SUMS` — recursively verifies the APK, test APK, screenshots, and report files.",
            "",
            "A failed Hero fixture or visual-parity gate does not change the build/test status above. This APK is a development validation build and does not represent a Release or full visual acceptance PASS.",
        ])
        (artifact_dir / "NH01_Android_Validation_Report.md").write_text(
            "\n".join(validation_report_lines) + "\n", encoding="utf-8"
        )
        sums = artifact_dir / "SHA256SUMS"
        files = sorted(
            path for path in artifact_dir.rglob("*")
            if path.is_file() and path.name != "SHA256SUMS"
        )
        sums.write_text(
            "".join(f"{sha256(path)}  {path.relative_to(artifact_dir).as_posix()}\n" for path in files),
            encoding="utf-8",
        )
    runtime = {
        "schema_version": 1,
        "document_type": "android_runtime",
        "status": classification,
        "source": {
            "expected_source_sha": args.source_sha,
            "workflow_run_id": args.run_id,
            "workflow_run_url": args.run_url,
            "artifact_url": artifact_link(args.artifact_url),
            "artifact_sha256": None,
            "captured_at_utc": datetime.now(timezone.utc).isoformat(),
        },
        "device": {
            "profile": device.get("profile", "Pixel 2"),
            "api_level": device.get("api_level", 28),
            "resolution_px": device.get("resolution_px", [1080, 1920]),
            "density_dpi": device.get("density_dpi"),
            "density_scale": device.get("density_scale"),
            "font_scale": device.get("font_scale"),
            "insets_px": device.get("insets_px"),
            "build_fingerprint": device.get("build_fingerprint"),
            "runner": device.get("runner"),
            "emulator_acceleration": device.get("emulator_acceleration"),
            "kvm_preflight": device.get("kvm_preflight"),
            "software_tcg_attempt": device.get("software_tcg_attempt"),
        },
        "artifacts": {
            "screenshot": copied.get("NH01_Android_Runtime_1080x1920.png"),
            "frozen": copied.get("NH01_Frozen_1080x1920.png"),
            "side_by_side": copied.get("NH01_Frozen_Android_SideBySide.png"),
            "bounds": copied.get("NH01_Android_Bounds.json"),
            "pixel_diff": copied.get("NH01_Android_Pixel_Diff.json"),
            "pixel_heatmap": copied.get("NH01_Android_Pixel_Heatmap.png"),
            "pixel_overlay": copied.get("NH01_Android_Overlay.png"),
            "pixel_heatmap_report": copied.get("NH01_Android_Pixel_Heatmap_Report.json"),
            "responsive": copied.get("NH01_Android_Responsive.json"),
            "closure": copied.get("NH01_Android_Closure.json"),
            "motion_mp4": copied.get("NH01_Android_Runtime_Motion.mp4"),
            "visual_qa_report": copied.get("NH01_Android_Visual_QA.md"),
            "apk": None,
            "apk_sha256": apk_sha,
            "apk_artifact_url": artifact_link(args.artifact_url),
            "runtime_gate_result": copied.get("NH01_Runtime_Gate_Result.json"),
        },
        "gate_classification": classification,
        "runtime_harness_result": runtime_result,
        "pixel_diff_summary": pixel,
        "bounds_summary": bounds,
        "responsive_summary": responsive,
    }
    runtime_doc = web_root / "design/manager/nh01_acceptance/data/NH01_Android_Runtime_V1.json"
    write_json(runtime_doc, runtime)

    evidence_path = web_root / "design/manager/nh01_acceptance/data/NH01_Evidence_Provenance_V1.json"
    evidence = read_json(evidence_path)
    evidence["android"] = {
        "runtime": classification,
        "bounds_in_window": "CAPTURED" if copied.get("NH01_Android_Bounds.json") else "NOT_RUN",
        "native_screenshot": "CAPTURED" if screenshot_ok else "NOT_RUN",
        "apk_sha256": apk_sha,
        "source_sha": args.source_sha,
        "workflow_run_id": args.run_id,
        "workflow_run_url": args.run_url,
        "pixel_diff": pixel.get("status", "NOT_RUN"),
        "responsive": responsive.get("status", "NOT_RUN"),
    }
    gates = evidence.setdefault("gate_status", {})
    gates["G4_RUNTIME_VALID"] = "PASS" if classification == "PASS" and screenshot_ok else classification
    gates["G5_VISUAL_PARITY_VALID"] = "PASS" if closure.get("status") == "PASS" else ("REVIEW_REQUIRED" if pixel else "NOT_RUN")
    gates["G6_RESPONSIVE_VALID"] = responsive.get("status", "NOT_RUN")
    delivery_ready = bool(copied.get("NH01_Runtime_Gate_Result.json") and args.run_url and args.artifact_url)
    gates["G7_DELIVERY_VALID"] = "PASS" if delivery_ready else "REVIEW_REQUIRED"
    gates["G8_USER_ACCEPTANCE"] = "PENDING_REVIEW"
    gates["FINAL"] = "REVIEW_REQUIRED"
    evidence_path.write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    browser_report = read_json(web_root / "design/manager/nh01_acceptance/data/NH01_Browser_Viewer_Report_V1.json")
    source_map = read_json(VIEWER_DATA / "source/M1_NH01_FIGMA_NODE_MAPPING_V2.json")
    components = structure = read_json(VIEWER_DATA / "NH01_Structure_V1.json")
    verified_components = sum(item.get("status") == "PASS" for item in components.get("components", []))
    unresolved = len(source_map.get("conflicts", []))
    preview_url = "https://pan277942135.github.io/Yujian_App/design/manager/nh01_acceptance/"
    lines = [
        "# NH01 Cloud High-Fidelity Final Report",
        "",
        f"- Branch: design/nh01-cloud-structured-parity-v1",
        f"- Draft PR: {args.pr_url}",
        f"- Exact runtime source SHA: {args.source_sha}",
        f"- Android Runtime workflow: {args.run_url} (Run ID {args.run_id})",
        f"- Frozen SHA-256: {structure['frozen_authority']['sha256']}",
        f"- Structured components: {len(components.get('components', []))}",
        f"- Source-verified mapped components: {verified_components}/{len(components.get('components', []))}",
        f"- Unresolved PR #164 conflicts: {unresolved}",
        f"- Browser Viewer Gate G2: {browser_report.get('status', 'NOT_RUN')} (separate Pages/browser run)",
        f"- Android build: PASS (APK SHA-256 {apk_sha})",
        f"- Source / structure Gate G0: {gates.get('G0_SOURCE_AUTHORITY', 'NOT_RUN')} / G1: {gates.get('G1_STRUCTURE_VALID', 'NOT_RUN')}",
        f"- Compose mapping Gate G3: {gates.get('G3_COMPOSE_MAPPING_VALID', 'NOT_RUN')}",
        f"- Runtime Gate G4: {gates['G4_RUNTIME_VALID']}",
        f"- Visual parity Gate G5: {gates['G5_VISUAL_PARITY_VALID']}",
        f"- Responsive Gate G6: {gates['G6_RESPONSIVE_VALID']}",
        f"- Delivery Gate G7: {gates['G7_DELIVERY_VALID']}",
        f"- Viewer: {preview_url}",
        f"- APK / runtime artifact: {args.artifact_url or 'NOT_AVAILABLE'}",
        "- User acceptance G8: PENDING_REVIEW",
        f"- Final Gate: {gates['FINAL']}",
        "",
        "## Remaining issues",
        "",
        "- Figma Desktop readback is separate and remains outside this cloud Viewer gate.",
        "- Hero photo rights/provenance remain unverified; runtime screenshots are diagnostic evidence, not approval of the fixture as a product asset.",
        "- Avatar source authority and the Camera artwork/touch geometry conflict remain under review.",
        "- Any responsive profile reported REVIEW_REQUIRED or BLOCKED_INFRA remains open.",
        "",
        "This report is generated from the current exact-source Android run. The APK is built from the same checkout and is hashed separately; no older SHA evidence is reused.",
    ]
    final_path = web_root / "design/cloud_hifi/NH01/NH01_CLOUD_HIFI_FINAL_REPORT.md"
    final_path.parent.mkdir(parents=True, exist_ok=True)
    final_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"NH01_RUNTIME_PACKAGE status={classification} source_sha={args.source_sha} apk_sha256={apk_sha} screenshot={'CAPTURED' if screenshot_ok else 'NOT_RUN'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
