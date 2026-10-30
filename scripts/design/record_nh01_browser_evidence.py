#!/usr/bin/env python3
"""Bind a successful browser acceptance run to the static Pages build output."""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
from pathlib import Path


def load(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--site-root", type=Path, required=True)
    parser.add_argument("--expected-source-sha", required=True)
    parser.add_argument("--workflow-run-id", required=True)
    parser.add_argument("--workflow-run-url", required=True)
    parser.add_argument("--preview-diff-report", type=Path)
    parser.add_argument("--preview-screenshot", type=Path)
    parser.add_argument("--preview-diff-dir", type=Path)
    args = parser.parse_args()
    report = load(args.report)
    if report.get("status") != "PASS" or report.get("gate") != "G2_CLOUD_PREVIEW_VALID":
        raise SystemExit("Browser evidence is not a PASS for G2_CLOUD_PREVIEW_VALID")
    if report.get("source_sha") != args.expected_source_sha:
        raise SystemExit("Browser evidence source SHA does not match the deployed source")

    data = args.site_root / "design/manager/nh01_acceptance/data"
    evidence_path = data / "NH01_Evidence_Provenance_V1.json"
    evidence = load(evidence_path)
    evidence["browser"] = {
        "status": "PASS",
        "source_sha": args.expected_source_sha,
        "workflow_run_id": args.workflow_run_id,
        "workflow_run_url": args.workflow_run_url,
        "report": "NH01_Browser_Viewer_Report_V1.json",
        "checked_at_utc": report.get("checked_at_utc"),
    }
    evidence["gate_status"]["G2_CLOUD_PREVIEW_VALID"] = "PASS"
    preview_inputs = [args.preview_diff_report, args.preview_screenshot, args.preview_diff_dir]
    if any(preview_inputs) and not all(preview_inputs):
        raise SystemExit("Preview diff recording requires report, screenshot, and diff directory together")
    if all(preview_inputs):
        diff_report = load(args.preview_diff_report)
        if diff_report.get("source_sha") != args.expected_source_sha:
            raise SystemExit("Structured preview diff belongs to a different source SHA")
        if diff_report.get("classification") != "DIAGNOSTIC_ONLY_REVIEW_REQUIRED":
            raise SystemExit("Structured preview diff must remain diagnostic-only")
        preview_dir = args.site_root / "design/manager/nh01_acceptance/evidence" / args.expected_source_sha / "preview"
        preview_dir.mkdir(parents=True, exist_ok=True)
        screenshot_target = preview_dir / "NH01_Structured_Preview_1080x1920.png"
        shutil.copy2(args.preview_screenshot, screenshot_target)
        artifact_paths = {"structured_preview": str(screenshot_target.relative_to(args.site_root / "design/manager/nh01_acceptance"))}
        for key, name in (("heatmap", "NH01_Diff_Heatmap.png"), ("overlay", "NH01_Diff_Overlay.png")):
            source = args.preview_diff_dir / name
            if not source.is_file():
                raise SystemExit(f"Missing structured preview comparison artifact: {source}")
            target = preview_dir / name
            shutil.copy2(source, target)
            artifact_paths[key] = str(target.relative_to(args.site_root / "design/manager/nh01_acceptance"))
        report_target = preview_dir / "NH01_ROI_Report.json"
        diff_report["structured_preview"]["path"] = artifact_paths["structured_preview"]
        diff_report["heatmap"] = artifact_paths["heatmap"]
        diff_report["overlay"] = artifact_paths["overlay"]
        report_target.write_text(json.dumps(diff_report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        artifact_paths["roi_report"] = str(report_target.relative_to(args.site_root / "design/manager/nh01_acceptance"))
        browser_diff = {
            "schema_version": 1,
            "document_type": "browser_preview_diff",
            "status": "REVIEW_REQUIRED",
            "source": {"source_sha": args.expected_source_sha, "renderer": "Chromium DOM screenshot; not Android Runtime"},
            "classification": diff_report["classification"],
            "artifacts": artifact_paths,
            "metrics": {
                "global_changed_pixel_fraction_over_8": diff_report.get("global_changed_pixel_fraction_over_8"),
                "global_rgb_mae": diff_report.get("global_rgb_mae"),
            },
            "rois": diff_report.get("rois", []),
            "threshold_authority": diff_report.get("threshold_authority"),
            "threshold_policy": diff_report.get("threshold_policy"),
        }
        (data / "NH01_Browser_Preview_Diff_V1.json").write_text(
            json.dumps(browser_diff, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        evidence["browser_preview_diff"] = {
            "status": "DIAGNOSTIC_ONLY_REVIEW_REQUIRED",
            "source_sha": args.expected_source_sha,
            "artifacts": artifact_paths,
        }
        report["structured_preview_diff"] = "DIAGNOSTIC_ONLY_REVIEW_REQUIRED"
    evidence_path.write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    report.update({
        "workflow_run_id": args.workflow_run_id,
        "workflow_run_url": args.workflow_run_url,
    })
    (data / "NH01_Browser_Viewer_Report_V1.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print(f"NH01_BROWSER_EVIDENCE_RECORDED=PASS source_sha={args.expected_source_sha} run_id={args.workflow_run_id}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
