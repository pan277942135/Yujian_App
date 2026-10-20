#!/usr/bin/env python3
"""Package exact-SHA NH01 Gemini V3 APK, device captures, and Compose profile evidence."""
from __future__ import annotations

import argparse
import hashlib
import json
import shutil
from pathlib import Path


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def copy_if_present(source: Path, destination: Path) -> bool:
    if not source.is_file() or source.stat().st_size == 0:
        return False
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, destination)
    return True


def read_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8")) if path.is_file() else {}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--evidence-dir", type=Path, required=True)
    parser.add_argument("--source-sha", required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--run-url", required=True)
    parser.add_argument("--pr-url", required=True)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--test-apk", type=Path, required=True)
    parser.add_argument("--source-audit", type=Path, required=True)
    parser.add_argument("--device-metadata", type=Path, required=True)
    parser.add_argument("--artifact-dir", type=Path, required=True)
    args = parser.parse_args()

    if len(args.source_sha) != 40 or any(char not in "0123456789abcdef" for char in args.source_sha.lower()):
        raise SystemExit("Source SHA must be a full 40-character commit ID")
    for path in (args.apk, args.test_apk):
        if not path.is_file() or path.stat().st_size == 0:
            raise SystemExit(f"Required exact-source Android APK missing: {path}")

    evidence = args.evidence_dir.resolve()
    artifact = args.artifact_dir.resolve()
    artifact.mkdir(parents=True, exist_ok=True)
    app_sha = sha256(args.apk)
    test_sha = sha256(args.test_apk)
    shutil.copy2(args.apk, artifact / "app-debug.apk")
    shutil.copy2(args.apk, artifact / "NH01_GEMINI_VALIDATION_DEBUG.apk")
    shutil.copy2(args.test_apk, artifact / "app-debug-androidTest.apk")
    if args.device_metadata.is_file():
        copy_if_present(args.device_metadata, artifact / "device_metadata.json")
    if args.source_audit.is_file():
        copy_if_present(args.source_audit, artifact / "source_audit.txt")
    evidence_target = artifact / "evidence"
    if evidence.exists():
        shutil.copytree(evidence, evidence_target, dirs_exist_ok=True)

    runtime = read_json(evidence / "runtime_gate_result.json")
    device_1920 = read_json(evidence / "android-device/api28_1080x1920.json")
    device_2340 = read_json(evidence / "android-device/api28_1080x2340.json")
    device_720 = read_json(evidence / "android-device/api28_720x1280.json")
    compose_report = read_json(evidence / "compose-simulation/compose-simulation/gemini_responsive_compose_report.json")
    avatar_report = read_json(evidence / "compose-simulation/avatar/gemini_avatar_transition_report.json")
    carousel_report = read_json(evidence / "compose-simulation/compose-simulation/carousel/gemini_catch_carousel_count_report.json")
    responsive_report = read_json(evidence / "Gemini_NH01_Responsive_Runtime_Report_V3.json")
    device_bounds_1920 = read_json(evidence / "android-device/api28_1080x1920_bounds.json")
    device_bounds_2340 = read_json(evidence / "android-device/api28_1080x2340_bounds.json")
    device_bounds_720 = read_json(evidence / "android-device/api28_720x1280_bounds.json")
    classification = runtime.get("classification", "BLOCKED_INFRA")
    profiles = compose_report.get("profiles", [])
    fixture = compose_report.get("business_fixture", {})
    expected_fixture = {
        "species_count": 3,
        "catch_count": 3,
        "record_days": 1,
        "first_catch": "草鱼",
        "length_cm": 42.6,
        "weight_kg": 1.28,
        "location": "浙江 · 千岛湖",
        "fish_photo_source": "home_normal/fish_record/sample_recent_catch.jpg",
    }
    compose_fixture_match = all(fixture.get(key) == value for key, value in expected_fixture.items())
    physical_fixture = {
        "api28_1080x1920": device_bounds_1920.get("business_fixture", {"verification": "NOT_RUN"}),
        "api28_1080x2340": device_bounds_2340.get("business_fixture", {"verification": "NOT_RUN"}),
        "api28_720x1280": device_bounds_720.get("business_fixture", {"verification": "NOT_RUN"}),
    }

    manifest = {
        "schema": "NH01_GEMINI_NATIVE_RUNTIME_MANIFEST_V3",
        "task_id": "YUJIAN_NH01_GEMINI_NATIVE_REBUILD_AVATAR_FIX_V3",
        "source_sha": args.source_sha,
        "workflow_run_id": args.run_id,
        "workflow_run_url": args.run_url,
        "pull_request_url": args.pr_url,
        "runtime_gate": runtime,
        "classification": classification,
        "app_apk": {"filename": "NH01_GEMINI_VALIDATION_DEBUG.apk", "sha256": app_sha, "size_bytes": args.apk.stat().st_size},
        "android_test_apk": {"filename": "app-debug-androidTest.apk", "sha256": test_sha, "size_bytes": args.test_apk.stat().st_size},
        "device_profiles": [device_1920, device_2340, device_720],
        "reference_business_fixture": expected_fixture,
        "compose_fixture_match": compose_fixture_match,
        "physical_business_fixture_checks": physical_fixture,
        "responsive_runtime_report": responsive_report,
        "compose_profile_count": len(profiles),
        "compose_profiles": profiles,
        "avatar_transition_report": avatar_report,
        "carousel_count_report": carousel_report,
        "frozen_pixel_parity": "NOT_CLAIMED_INTENTIONAL_GEMINI_V3_LAYOUT",
        "visual_review": "PENDING_SEPARATE_REVIEW",
        "artifact_files": [],
    }
    (artifact / "NH01_Gemini_Runtime_Manifest_V3.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )

    lines = [
        "# NH01 Gemini V3 Native Validation APK",
        "",
        f"- Task: `YUJIAN_NH01_GEMINI_NATIVE_REBUILD_AVATAR_FIX_V3`",
        f"- PR: {args.pr_url} (kept Draft / Open / Unmerged)",
        f"- Source HEAD: `{args.source_sha}`",
        f"- CI Run ID: [{args.run_id}]({args.run_url})",
        f"- API 28 runtime classification: `{classification}`",
        f"- APK: `NH01_GEMINI_VALIDATION_DEBUG.apk` ({args.apk.stat().st_size} bytes)",
        f"- APK SHA-256: `{app_sha}`",
        f"- Android test APK SHA-256: `{test_sha}`",
        "",
        "## Build and runtime",
        "",
        f"- Android debug build: `{'PASS' if args.apk.is_file() else 'FAIL'}`",
        f"- Android test APK: `{'PASS' if args.test_apk.is_file() else 'FAIL'}`",
        f"- Device preflight/install/instrumentation/evidence: `{runtime.get('preflight', 'NOT_RUN')}` / `{runtime.get('install', 'NOT_RUN')}` / `{runtime.get('instrumentation', 'NOT_RUN')}` / `{runtime.get('evidence', 'NOT_RUN')}`",
        "- Frozen pixel parity: `NOT_CLAIMED`; this is an intentional Gemini V3 container rebuild.",
        "- Separate visual review: `PENDING`; the runtime pass does not claim visual sign-off.",
        "",
        "## Android device captures",
        "",
        "All three emulator profiles are required to expose the same business data as the PR #167 baseline screenshot: 3 species, 3 catches, 1 record day, the 草鱼 card at 42.6 cm / 1.28 kg, 浙江 · 千岛湖, and the registered sample fish photo. The screenshot is guest-mode so the V3 validation page also exercises its approved V2 avatar fallback; the signed-in fallback and remote-avatar transitions are tested separately in Compose.",
        "",
        "| Profile | Mode | Status | Screenshot | Node bounds |",
        "|---|---|---|---|---|",
    ]
    for device, screenshot in (
        (device_1920, "evidence/android-device/gemini_home_1080x1920.png"),
        (device_2340, "evidence/android-device/gemini_home_1080x2340.png"),
        (device_720, "evidence/android-device/gemini_home_720x1280.png"),
    ):
        image = device.get("screenshot", {}).get("path", "NOT_RUN")
        profile = device.get("profile_id", "NOT_RUN")
        mode = device.get("execution_mode", "NOT_RUN")
        bounds = device.get("node_bounds", {}).get("status", "NOT_RUN")
        lines.append(
            f"| {profile} | {mode} | {device.get('status', 'NOT_RUN')} | `{screenshot if image != 'NOT_RUN' else 'NOT_RUN'}` | `{bounds}` |"
        )

    lines.extend([
        "",
        "## Compose constraint simulation",
        "",
        f"Reference business fixture match: `{'PASS' if compose_fixture_match else 'FAIL_OR_NOT_RUN'}` ({fixture or 'no Compose fixture report'}).",
        "",
        "These rows are test-window simulations, not physical-device captures. Each executed row includes its screenshot, section/action Bounds, CTA/camera spacing check, Hero metadata visibility/scroll result, and callback results in `evidence/compose-simulation/compose-simulation/gemini_responsive_compose_report.json`. The finite 0/1/2/3/10 carousel count, stable selection, click ID, boundary, insert, and delete cases are in `evidence/compose-simulation/compose-simulation/carousel/gemini_catch_carousel_count_report.json`.",
        "",
        "| Window | Font scale | Status | Screenshot | Hero footer | Actions |",
        "|---|---:|---|---|---|---|",
    ])
    for row in profiles:
        lines.append(
            f"| {row.get('profile', 'NOT_RUN')} | {row.get('font_scale', 'NOT_RUN')} | {row.get('status', 'NOT_RUN')} | `{row.get('screenshot', 'NOT_RUN')}` | `initial={row.get('hero_metadata_initially_visible', 'NOT_RUN')}; scroll={row.get('hero_metadata_visible_after_scroll', 'NOT_RUN')}` | `{'PASS' if all(value == 'PASS' for value in row.get('interactions', {}).values()) else 'NOT_PASS'}` |"
        )
    if not profiles:
        lines.append("| All requested profiles | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |")

    lines.extend([
        "",
        "## Avatar P0 evidence",
        "",
        f"- Approved Home V2 source SHA-256: `{avatar_report.get('avatar_asset_sha256', 'NOT_RUN')}`",
        f"- User avatar load: `{avatar_report.get('user_avatar_load', 'NOT_RUN')}`; failed load fallback: `{avatar_report.get('user_avatar_failure_fallback', 'NOT_RUN')}`",
        f"- Account switch/logout stale-avatar checks: `{avatar_report.get('account_switch_clears_previous_avatar', 'NOT_RUN')}` / `{avatar_report.get('logout_clears_previous_avatar', 'NOT_RUN')}`",
        f"- Guest Login vs signed-in Profile actions: `{avatar_report.get('guest_click', 'NOT_RUN')}` / `{avatar_report.get('signed_in_click', 'NOT_RUN')}`",
        f"- Avatar crop/circle/target screenshot: `evidence/compose-simulation/avatar/{avatar_report.get('avatar_screenshot', 'NOT_RUN')}`",
        "",
        "## Runtime limitations",
        "",
        "The API 28 screenshots are real Android emulator screencaps. The 1080×2340 profile uses the emulator's `wm size` display override; the 720×1280 profile uses `wm size` with 320 dpi to represent a 360×640dp viewport. Both overrides are identified in profile JSON. The requested 320–480dp windows and font scales are Compose constraint simulations. A missing physical node dump is recorded as `NOT_RUN` in its device profile; it is not backfilled from the Compose simulation.",
        "",
        "The APK is a validation build. A `PASS` runtime classification means build, install, launch, required Compose interaction tests, and captured device evidence passed; it does not certify Frozen pixel parity or replace separate visual review.",
    ])
    (artifact / "NH01_Gemini_Native_Validation_Report.md").write_text("\n".join(lines) + "\n", encoding="utf-8")

    manifest_path = artifact / "NH01_Gemini_Runtime_Manifest_V3.json"
    files = sorted(
        path for path in artifact.rglob("*")
        if path.is_file() and path.name != "SHA256SUMS" and path != manifest_path
    )
    manifest["artifact_files"] = [
        {"path": path.relative_to(artifact).as_posix(), "sha256": sha256(path), "size_bytes": path.stat().st_size}
        for path in files
    ]
    (artifact / "NH01_Gemini_Runtime_Manifest_V3.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    files = sorted(
        path for path in artifact.rglob("*")
        if path.is_file() and path.name != "SHA256SUMS" and path != manifest_path
    )
    (artifact / "SHA256SUMS").write_text(
        "".join(f"{sha256(path)}  {path.relative_to(artifact).as_posix()}\n" for path in files), encoding="utf-8"
    )
    print(f"NH01_GEMINI_RUNTIME_PACKAGE classification={classification} source_sha={args.source_sha} apk_sha256={app_sha} run_id={args.run_id}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
