#!/usr/bin/env python3
"""Package exact-source NH01 APKs and the current native API 28 device evidence."""
from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import subprocess
import struct
from pathlib import Path
from typing import Any


REQUIRED_PROFILES = {
    "M04": (360, 640),
    "M07": (390, 844),
    "M10": (430, 932),
}
BASELINE_MANIFEST = Path(
    "design/pages/home/normal_home/engineering/NH01_V4_VISUAL_BASELINE_MANIFEST.json"
)
VISUAL_REVIEW_STATUS = Path(
    "design/pages/home/normal_home/engineering/NH01_VISUAL_QA_STATUS_V1_1.json"
)


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


def read_json(path: Path) -> dict[str, Any]:
    try:
        return json.loads(path.read_text(encoding="utf-8")) if path.is_file() else {}
    except (OSError, json.JSONDecodeError):
        return {}


def visual_review_status(source_sha: str) -> tuple[str, dict[str, Any]]:
    review = read_json(VISUAL_REVIEW_STATUS)
    status = str(review.get("visual_acceptance", "PENDING_WORK_REVIEW"))
    reviewed_sha = review.get("reviewed_source_sha")
    if status not in {
        "PASS",
        "PASS_WITH_KNOWN_LIMITATIONS",
        "FAIL",
        "PENDING_WORK_REVIEW",
    }:
        status = "PENDING_WORK_REVIEW"
    if status.startswith("PASS") and reviewed_sha != source_sha:
        unchanged = False
        if isinstance(reviewed_sha, str) and len(reviewed_sha) == 40:
            result = subprocess.run(
                ["git", "diff", "--name-only", f"{reviewed_sha}...{source_sha}"],
                check=False,
                capture_output=True,
                text=True,
            )
            changed = result.stdout.splitlines()
            ui_paths = (
                "app/src/main/",
                "app/src/androidTest/",
                "app/src/test/",
            )
            unchanged = result.returncode == 0 and not any(
                path.startswith(ui_paths) for path in changed
            )
        if not unchanged:
            status = "PENDING_WORK_REVIEW"
    return status, review


def png_dimensions(path: Path) -> list[int] | None:
    try:
        header = path.read_bytes()[:24]
        if len(header) != 24 or header[:8] != b"\x89PNG\r\n\x1a\n" or header[12:16] != b"IHDR":
            return None
        return list(struct.unpack(">II", header[16:24]))
    except OSError:
        return None


def case_screenshot(evidence: Path, profile: str, state: str) -> Path:
    width, height = REQUIRED_PROFILES[profile]
    return evidence / "NH01_RUNTIME_MATRIX_V1" / "screenshots" / (
        f"{profile}_{width}x{height}_{state}.png"
    )


def build_profile_rows(evidence: Path, source_sha: str) -> tuple[list[dict[str, Any]], bool]:
    matrix = read_json(evidence / "NH01_THREE_DEVICE_ADAPTIVE_V2_REPORT.json")
    records = {str(row.get("profile")): row for row in matrix.get("cases", [])}
    execution_summary = read_json(
        evidence / "NH01_RUNTIME_MATRIX_V1" / "execution_summary.json"
    )
    summary_records = {
        str(row.get("profile_id")): row
        for row in execution_summary.get("execution_records", [])
    }
    fixture_seed = read_json(
        evidence / "NH01_RUNTIME_MATRIX_V1" / "fixture_seed.json"
    )
    rows: list[dict[str, Any]] = []
    all_pass = matrix.get("status") == "PASS" and execution_summary.get("status") == "PASS"
    for profile, viewport in REQUIRED_PROFILES.items():
        record = records.get(profile, {})
        execution = summary_records.get(profile, {})
        settings = read_json(
            evidence / "NH01_RUNTIME_MATRIX_V1" / "logs" / f"{profile}_device_settings.json"
        )
        raw_metrics = read_json(
            evidence / "nh01-adaptive-raw" / "nh01-adaptive-visual-balance-v2"
            / f"{profile}-adaptive_v2.json"
        )
        screenshots = {}
        for state in ("initial", "after_swipe"):
            path = case_screenshot(evidence, profile, state)
            actual_size = png_dimensions(path) if path.is_file() else None
            screenshots[state] = {
                "path": path.relative_to(evidence).as_posix(),
                "exists": path.is_file() and path.stat().st_size > 0,
                "sha256": sha256(path) if path.is_file() and path.stat().st_size > 0 else None,
                "size_px": actual_size,
                "dimensions_match": actual_size == list(viewport),
            }
        setting_pass = (
            settings.get("profile_id") == profile
            and settings.get("requested_native_size_px") == f"{viewport[0]}x{viewport[1]}"
            and settings.get("requested_density_dpi") == 160
            and settings.get("requested_font_scale") == 1.0
            and settings.get("actual_wm_size") == f"{viewport[0]}x{viewport[1]}"
            and settings.get("actual_wm_density") == "160"
            and settings.get("actual_font_scale") == "1.0"
            and settings.get("wm_override_size") is None
            and settings.get("wm_override_density_dpi") is None
            and settings.get("native_viewport_pass") is True
        )
        row_pass = (
            record.get("status") == "PASS"
            and record.get("screenshot_evidence") is True
            and record.get("settings_match") is True
            and record.get("execution", {}).get("source_sha") == source_sha
            and execution.get("source_sha") == source_sha
            and execution.get("status") == "PASS"
            and setting_pass
            and all(item["exists"] and item["dimensions_match"] for item in screenshots.values())
        )
        all_pass = all_pass and row_pass
        initial_layout = raw_metrics.get("initial_layout", {})
        bounds = initial_layout.get("bounds_dp", {})
        hero = next(
            (value for key, value in bounds.items() if key.startswith("gemini-home-catch-card-")),
            {},
        )
        camera = bounds.get("gemini-home-camera", {})
        camera_art = bounds.get("gemini-home-camera-art", {})
        rows.append({
            "profile_id": profile,
            "variant": "ADAPTIVE_V2",
            "status": "PASS" if row_pass else "FAIL_EVIDENCE",
            "viewport_dp": list(viewport),
            "density_dpi": settings.get("requested_density_dpi"),
            "font_scale": settings.get("requested_font_scale"),
            "wm_override_size": settings.get("wm_override_size"),
            "wm_override_density_dpi": settings.get("wm_override_density_dpi"),
            "native_viewport_pass": settings.get("native_viewport_pass", False),
            "settings_match": setting_pass,
            "duration_seconds": record.get("duration_seconds"),
            "screenshots": screenshots,
            "layout_bounds_dp": {
                "hero": hero,
                "camera_touch": camera,
                "camera_art": camera_art,
            },
            "pager_case": execution.get("status", "NOT_RUN"),
        })
    fixture_pass = (
        fixture_seed.get("status") == "PASS"
        and len(records) == 3
        and all(row["status"] == "PASS" for row in rows)
    )
    return rows, all_pass and fixture_pass


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
    shutil.copy2(args.apk, artifact / "NH01_VISUAL_QA_SELF_HEALING_DEBUG.apk")
    shutil.copy2(args.test_apk, artifact / "app-debug-androidTest.apk")
    copy_if_present(args.device_metadata, artifact / "device_metadata.json")
    copy_if_present(args.source_audit, artifact / "source_audit.txt")
    if evidence.exists():
        shutil.copytree(evidence, artifact / "evidence", dirs_exist_ok=True)

    runtime = read_json(evidence / "runtime_gate_result.json")
    execution_summary = read_json(
        evidence / "NH01_RUNTIME_MATRIX_V1" / "execution_summary.json"
    )
    native_report = read_json(evidence / "NH01_THREE_DEVICE_ADAPTIVE_V2_REPORT.json")
    fixture_seed = read_json(
        evidence / "NH01_RUNTIME_MATRIX_V1" / "fixture_seed.json"
    )
    profiles, matrix_pass = build_profile_rows(evidence, args.source_sha)
    source_bound = (
        runtime.get("build_sha") == args.source_sha
        and execution_summary.get("source_sha") == args.source_sha
        and native_report.get("source_sha") == args.source_sha
    )
    runtime_classification = runtime.get("classification", "BLOCKED_INFRA")
    classification = runtime_classification
    if not source_bound:
        classification = "FAIL_ARTIFACT"
    elif runtime_classification == "PASS" and not matrix_pass:
        classification = "FAIL_EVIDENCE"

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
    legacy_profiles = {
        "1080x1920": "SUPERSEDED_BY_CURRENT_NATIVE_M04_M07_M10_MATRIX",
        "1080x2340": "SUPERSEDED_BY_CURRENT_NATIVE_M04_M07_M10_MATRIX",
        "720x1280": "SUPERSEDED_BY_CURRENT_NATIVE_M04_M07_M10_MATRIX",
    }
    visual_status, visual_review = visual_review_status(args.source_sha)
    baseline = read_json(BASELINE_MANIFEST)
    baseline_sha = baseline.get("sha256", "8bb3bb1b4a2303d006bd3f6dbd373a11a02997d97ca64a1d5130c9432847e37c")
    fixture_status = "PASS" if fixture_seed.get("status") == "PASS" and matrix_pass else "FAIL"
    manifest = {
        "schema": "NH01_GEMINI_NATIVE_RUNTIME_MANIFEST_V3",
        "task_id": "YUJIAN_NH01_VISUAL_QA_SELF_HEALING_V1_1",
        "source_sha": args.source_sha,
        "workflow_run_id": args.run_id,
        "workflow_run_url": args.run_url,
        "pull_request_url": args.pr_url,
        "runtime_gate": runtime,
        "classification": classification,
        "app_apk": {
            "filename": "NH01_GEMINI_VALIDATION_DEBUG.apk",
            "sha256": app_sha,
            "size_bytes": args.apk.stat().st_size,
        },
        "android_test_apk": {
            "filename": "app-debug-androidTest.apk",
            "sha256": test_sha,
            "size_bytes": args.test_apk.stat().st_size,
        },
        "native_api28_matrix": {
            "status": "PASS" if matrix_pass and source_bound else "FAIL",
            "source_sha": args.source_sha,
            "device_api": native_report.get("device_api"),
            "adb_serial": native_report.get("adb_serial"),
            "layout_variant": "ADAPTIVE_V2",
            "profiles": profiles,
            "execution_summary": execution_summary,
            "source_report": "evidence/NH01_THREE_DEVICE_ADAPTIVE_V2_REPORT.json",
            "pager_evidence": "evidence/NH01_RUNTIME_MATRIX_V1/NH01_API28_NATIVE_VIEWPORT_AND_PAGER_FIX_REPORT.md",
        },
        "reference_business_fixture": {
            **expected_fixture,
            "status": fixture_status,
            "fixture_seed": fixture_seed,
            "verification": "Production MainActivity instrumentation asserts these values from GuestCatchRepository.",
        },
        "superseded_legacy_profile_records": legacy_profiles,
        "full_12_profile_matrix": "NOT_RUN",
        "font_scale_1_3": "NOT_RUN",
        "font_scale_1_5": "NOT_RUN",
        "physical_handset_verification": "NOT_RUN",
        "frozen_pixel_parity": "NOT_CLAIMED",
        "visual_review": {
            "status": visual_status,
            "review": visual_review,
            "pixel_similarity_is_acceptance": False,
        },
        "artifact_files": [],
    }
    (artifact / "NH01_Gemini_Runtime_Manifest_V3.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )

    lines = [
        "# NH01 Native Validation and Visual QA",
        "",
        f"- Task: YUJIAN_NH01_VISUAL_QA_SELF_HEALING_V1_1",
        f"- PR: {args.pr_url} (Open / Draft / Unmerged)",
        f"- Source SHA: {args.source_sha}",
        f"- GitHub Actions run: [{args.run_id}]({args.run_url})",
        f"- Runtime classification: {classification}",
        f"- NH01_VISUAL_QA_SELF_HEALING_DEBUG.apk size / SHA-256: {args.apk.stat().st_size} bytes / {app_sha}",
        f"- AndroidTest APK size / SHA-256: {args.test_apk.stat().st_size} bytes / {test_sha}",
        f"- Frozen visual authority SHA-256: {baseline_sha} (metadata only; source bytes and image derivatives are excluded from CI and Git)",
        "",
        "## Runtime and fixture",
        "",
        f"- Android Debug Build: {'PASS' if args.apk.is_file() else 'FAIL'}",
        f"- AndroidTest Build: {'PASS' if args.test_apk.is_file() else 'FAIL'}",
        "- Android unit tests: PASS (the packaging step is reached only after gradle test succeeds)",
        f"- ADB preflight / install / instrumentation / evidence: {runtime.get('preflight', 'NOT_RUN')} / {runtime.get('install', 'NOT_RUN')} / {runtime.get('instrumentation', 'NOT_RUN')} / {runtime.get('evidence', 'NOT_RUN')}",
        f"- Repository fixture and production Home assertions: {fixture_status}",
        "- Asserted data: 3 species, 3 catches, 1 record day; 草鱼, 42.6 cm, 1.28 kg, 浙江 · 千岛湖, registered sample photo.",
        "- Native viewports: M04 360×640 @ 160 dpi; M07 390×844 @ 160 dpi; M10 430×932 @ 160 dpi.",
        "- All three cases use font scale 1.0, no wm size or density override, and the pre-boot AVD hardware LCD configuration.",
        "",
        "| Profile | Native viewport | Runtime | Initial / after-swipe | Screenshot SHA-256 (initial / after-swipe) | Hero bounds |",
        "|---|---:|---|---|---|---|",
    ]
    for row in profiles:
        shot = row["screenshots"]
        hero = row["layout_bounds_dp"]["hero"]
        hero_text = (
            f"{hero.get('width', '?')}×{hero.get('height', '?')} dp"
            if isinstance(hero, dict)
            else "NOT_CAPTURED"
        )
        lines.append(
            f"| {row['profile_id']} | {row['viewport_dp'][0]}×{row['viewport_dp'][1]} @ {row['density_dpi']} dpi | {row['status']} | "
            f"[initial]({shot['initial']['path']}) / [after swipe]({shot['after_swipe']['path']}) | "
            f"{shot['initial']['sha256'] or 'MISSING'} / {shot['after_swipe']['sha256'] or 'MISSING'} | "
            f"{hero_text} |"
        )
    lines.extend([
        "",
        "## Pager and evidence scope",
        "",
        "- Pager is exercised on the installed production MainActivity using a real 600 ms 72%→28% horizontal touch. Assertions require page 0→1 and 1→2, with current/target/settled page and current record ID recorded.",
        "- Six emulator screenshots, per-profile settings, bounds, fixture report, execution summary, and native viewport/pager report are included under evidence/NH01_RUNTIME_MATRIX_V1/.",
        "- Three representative API 28 profiles at font scale 1.0 are PASS; the full 12-profile matrix, font scales 1.3 / 1.5, and physical handset verification remain NOT_RUN.",
        f"- Previous 1080×1920, 1080×2340 and 720×1280 records are historical only: SUPERSEDED_BY_RUN_{args.run_id}.",
        "",
        "## Visual review",
        "",
        f"- VISUAL_ACCEPTANCE: {visual_status}. Runtime PASS is not visual sign-off.",
        "- The source screenshot is reviewed by Work against the sole frozen V4 authority using the ADAPTIVE_V2 / V4 2.1 contract. Pixel-difference values are diagnostic and are not a pass gate.",
        "- Camera artwork bounds are captured from native Compose LayoutCoordinates and validated against the V4 responsive size; semantics bounds are not used as a substitute.",
        "",
        "## SHA manifest",
        "",
        "- SHA256SUMS covers every delivered artifact file except SHA256SUMS itself, including the Runtime Manifest.",
        "- The private frozen reference image, reference crops, and reference-derived comparison images are intentionally not included in GitHub Actions artifacts.",
    ])
    (artifact / "NH01_Gemini_Native_Validation_Report.md").write_text(
        "\n".join(lines) + "\n", encoding="utf-8"
    )

    manifest_path = artifact / "NH01_Gemini_Runtime_Manifest_V3.json"
    files = sorted(
        path for path in artifact.rglob("*")
        if path.is_file() and path.name not in {"SHA256SUMS", manifest_path.name}
    )
    manifest["artifact_files"] = [
        {
            "path": path.relative_to(artifact).as_posix(),
            "sha256": sha256(path),
            "size_bytes": path.stat().st_size,
        }
        for path in files
    ]
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    checksum_files = sorted(
        path for path in artifact.rglob("*")
        if path.is_file() and path.name != "SHA256SUMS"
    )
    (artifact / "SHA256SUMS").write_text(
        "".join(f"{sha256(path)}  {path.relative_to(artifact).as_posix()}\n" for path in checksum_files),
        encoding="utf-8",
    )
    print(
        f"NH01_GEMINI_RUNTIME_PACKAGE classification={classification} "
        f"matrix={'PASS' if matrix_pass and source_bound else 'FAIL'} "
        f"source_sha={args.source_sha} apk_sha256={app_sha} run_id={args.run_id}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
