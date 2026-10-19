#!/usr/bin/env python3
"""Bind an API28 Normal Home runtime run to its exact source SHA and viewer bundle."""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
from datetime import datetime, timezone
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
VIEWER_DATA = ROOT / "design/manager/nh01_acceptance/data"


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
        ("normal-home-v1/01_normal_home_first_card.png", "NH01_Android_FirstCard.png"),
        ("normal-home-v1/04_normal_home_runtime_1080x1920.png", "NH01_Android_Runtime_1080x1920.png"),
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
        shutil.copy2(args.apk, artifact_dir / "app-debug.apk")
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
            "app_apk_sha256": apk_sha,
            "android_test_apk_sha256": sha256(args.test_apk) if args.test_apk and args.test_apk.is_file() else None,
            "classification": classification,
            "device": device,
            "evidence_files": copied,
            "private_evidence_files": private_copied,
        }
        write_json(artifact_dir / "NH01_Runtime_Manifest_V1.json", artifact_manifest)
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
