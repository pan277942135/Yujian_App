#!/usr/bin/env python3
"""Verify the deployed R01 Receiving Light page and its four browser PNGs."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from urllib.request import Request, urlopen


ROUTE = "design/manager/nh01_acceptance/edgeglow-fish-static/"
OPTICAL_REVIEW = ROUTE + "optical-review/"
STATIC_EVIDENCE = OPTICAL_REVIEW + "evidence/static-recovery-r01/"
EXPECTED_PNGS = [
    "receiving-light-only-B.png",
    "receiving-light-over-photo-B.png",
    "static-B-390x844.png",
    "geometry-diagnostic-390x844.png",
]
BASELINE_SHA = "76512ea4c889e4963cc8ed42662f2b0d344b835b"
BASELINE_RUN = 39453185868


def fetch(url: str) -> bytes:
    request = Request(url, headers={"User-Agent": "Yujian-EdgeGlow-R01-Live-Smoke/1.0"})
    with urlopen(request, timeout=25) as response:
        if response.status != 200:
            raise AssertionError(f"{url} returned HTTP {response.status}")
        return response.read()


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--pr-sha", required=True)
    parser.add_argument("--center-sha", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    base = args.base_url.rstrip("/") + "/"
    route_url = base + ROUTE
    page = fetch(route_url).decode("utf-8")
    acceptance_page = fetch(base + "design/manager/nh01_acceptance/").decode("utf-8")
    optical_page = fetch(base + OPTICAL_REVIEW).decode("utf-8")
    optical_build = fetch(base + OPTICAL_REVIEW + "build-info.js").decode("utf-8")
    build_info = json.loads(fetch(route_url + "build-info.json"))
    evidence_url = base + STATIC_EVIDENCE
    evidence_page = fetch(evidence_url).decode("utf-8")
    report_bytes = fetch(evidence_url + "receiving-light-recovery-r01-test-report.json")
    params_bytes = fetch(evidence_url + "receiving-light-recovery-r01-parameters.json")
    report = json.loads(report_bytes)
    params = json.loads(params_bytes)

    assert build_info["pr_source_sha"] == args.pr_sha, "deployed acceptance build SHA mismatch"
    assert build_info["acceptance_center_source_sha"] == args.center_sha, "Acceptance Center SHA mismatch"
    assert report["source_sha"] == args.pr_sha == report["build_sha"] == report["deployment_source_sha"]
    assert report["acceptance_center_source_sha"] == args.center_sha
    assert report["browser_render_status"] == "PASS_TARGETED_BROWSER_RENDER_ONLY"
    assert report["visual_status"].startswith("REVIEW_REQUIRED")
    assert report["viewport"] == [390, 844] and report["state"] == "IMAGE_RECOGNIZING"
    assert report["inherited_geometry_evidence"]["workflow_run"] == BASELINE_RUN
    assert report["inherited_geometry_evidence"]["source_sha"] == BASELINE_SHA
    assert report["inherited_geometry_evidence"]["status"] == "REUSED_PARENT_EVIDENCE_NO_REPEAT"
    assert params["source_sha"] == args.pr_sha and params["inherited_baseline_sha"] == BASELINE_SHA
    assert params["viewport"] == [390, 844]
    assert set(record["file"] for record in report["rendered_pngs"]) == set(EXPECTED_PNGS)
    assert len(report["rendered_pngs"]) == 4, "R01 report must contain exactly four PNG records"
    png_hashes = {}
    for record in report["rendered_pngs"]:
        image = fetch(evidence_url + record["file"])
        assert image.startswith(b"\x89PNG\r\n\x1a\n"), f"{record['file']} is not a PNG"
        actual_sha = sha256(image)
        assert actual_sha == record["sha256"] and len(image) == record["bytes"], f"{record['file']} differs from the captured browser payload"
        png_hashes[record["file"]] = actual_sha
    assert sha256(params_bytes) == report["parameters_sha256"], "R01 parameter JSON SHA mismatch"
    assert 'id="opticalReviewEntry"' in page and 'href="./optical-review/"' in page
    assert "./evidence/static-recovery-r01/" in optical_page, "Optical Review must link to the R01 evidence page"
    assert f'"head_sha":"{args.pr_sha}"' in optical_build, "Optical Review source build SHA mismatch"
    assert "REVIEW_REQUIRED" in evidence_page and all(name in evidence_page for name in EXPECTED_PNGS)
    assert params["candidate"]["composite"] == "source-over"
    assert params["candidate"]["opacity_cap"] == 0.14
    assert params["candidate"]["base_alpha_range"] == [0.03, 0.07]
    assert report["constraints"]["p1_base_alpha"] == "UNCHANGED"
    assert report["constraints"]["seven_paths"] == "UNCHANGED"

    result = {
        "task_id": "YUJIAN_EDGEFIELD_STATIC_RECOVERY_R01",
        "status": "PASS_LIVE_ROUTE_AND_TARGETED_EVIDENCE_BYTE_MATCH",
        "scope": "WEB_STATIC_RECEIVING_LIGHT_ONLY",
        "acceptance_url": route_url,
        "optical_review_url": base + OPTICAL_REVIEW,
        "evidence_url": evidence_url,
        "source_sha": args.pr_sha,
        "deployment_source_sha": report["deployment_source_sha"],
        "acceptance_center_source_sha": args.center_sha,
        "targeted_png_sha256": png_hashes,
        "parameters_sha256": sha256(params_bytes),
        "inherited_photo_and_geometry_evidence": {
            "baseline_run": BASELINE_RUN,
            "baseline_sha": BASELINE_SHA,
            "status": "REUSED_PARENT_EVIDENCE_NO_REPEAT"
        },
        "visual_review": "REVIEW_REQUIRED · live route and PNG byte integrity do not establish optical quality",
        "android_runtime": "NOT_RUN",
        "native_implementation": "NOT_RUN",
        "dynamic_preview_changes": "NONE"
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
