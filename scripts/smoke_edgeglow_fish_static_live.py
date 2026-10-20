#!/usr/bin/env python3
"""Verify the deployed Acceptance Center route and its registered photo bytes."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from urllib.request import Request, urlopen


EXPECTED_PHOTO_SHA = "6e955087108f7463eca2dbac489699a4942028ebe1b3d456ddfca523286385ef"
ROUTE = "design/manager/nh01_acceptance/edgeglow-fish-static/"
PHOTO = "design/system/fish_media_display_v1/assets/real_catch_source.jpg"
OPTICAL_REVIEW = ROUTE + "optical-review/"
STATIC_EVIDENCE = OPTICAL_REVIEW + "evidence/static-v3/"
REQUIRED_STATIC_PNGS = [
    "geometry-diagnostic-390x844.png",
    "static-A-390x844.png",
    "static-B-390x844.png",
    "static-C-390x844.png",
    "receiving-light-only.png",
    "outer-bloom-only.png",
    "mid-glow-only.png",
    "core-only.png",
    "hairline-echoes-only.png",
    "full-composite-B-390x844.png",
    "fish-focus-blocked-fish-located.png",
    "fish-focus-review-proxy-fish-located.png",
    "fish-focus-review-proxy-species-recognizing.png",
]


def fetch(url: str) -> bytes:
    request = Request(url, headers={"User-Agent": "Yujian-EdgeGlow-Preview-Smoke/1.0"})
    with urlopen(request, timeout=20) as response:
        if response.status != 200:
            raise AssertionError(f"{url} returned HTTP {response.status}")
        return response.read()


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", required=True, help="GitHub Pages deployment root URL")
    parser.add_argument("--pr-sha", required=True)
    parser.add_argument("--center-sha", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    base = args.base_url.rstrip("/") + "/"
    route_url = base + ROUTE

    page = fetch(route_url).decode("utf-8")
    build_info = json.loads(fetch(route_url + "build-info.json"))
    fixture = json.loads(fetch(route_url + "fixture.json"))
    photo_bytes = fetch(base + PHOTO)
    actual_photo_sha = sha256(photo_bytes)
    acceptance_page = fetch(base + "design/manager/nh01_acceptance/").decode("utf-8")
    optical_page = fetch(base + OPTICAL_REVIEW).decode("utf-8")
    optical_build = fetch(base + OPTICAL_REVIEW + "build-info.js").decode("utf-8")
    gallery_page = fetch(base + STATIC_EVIDENCE).decode("utf-8")
    static_report_bytes = fetch(base + STATIC_EVIDENCE + "static-closure-v3-report.json")
    static_report = json.loads(static_report_bytes)
    parameter_bytes = fetch(base + STATIC_EVIDENCE + "static-candidate-parameters.json")
    assert sha256(parameter_bytes) == static_report["candidate_parameter_json"]["sha256"], "candidate parameter JSON SHA mismatch"
    artifact_manifest = json.loads(fetch(base + STATIC_EVIDENCE + "static-evidence-artifact-manifest.json"))
    artifact_records = static_report["rendered_png_artifacts"]
    assert {item["file"] for item in artifact_records} >= set(REQUIRED_STATIC_PNGS), "render report is missing required browser PNGs"
    assert artifact_manifest["artifacts"] == artifact_records, "PNG artifact manifest differs from the report"
    image_hashes = {}
    for record in artifact_records:
        image = fetch(base + STATIC_EVIDENCE + record["file"])
        assert image.startswith(b"\x89PNG\r\n\x1a\n"), f"{record['file']} is not a real PNG payload"
        actual_sha = sha256(image)
        assert actual_sha == record["sha256"] and len(image) == record["bytes"], f"{record['file']} does not match browser render evidence SHA/size"
        image_hashes[record["file"]] = actual_sha

    assert "originalCanvas" in page and "contourCanvas" in page and "glowCanvas" in page, "three canvas views missing"
    assert "MANUAL_REFERENCE_CONTOUR" in page and fixture["contour"]["source"] == "MANUAL_REFERENCE_CONTOUR"
    assert actual_photo_sha == EXPECTED_PHOTO_SHA == build_info["photo_sha256"] == fixture["photo"]["sha256"], "deployed photo identity mismatch"
    assert build_info["pr_source_sha"] == args.pr_sha, "deployed PR source SHA mismatch"
    assert build_info["acceptance_center_source_sha"] == args.center_sha, "deployed Acceptance Center SHA mismatch"
    assert 'id="opticalReviewEntry" href="./optical-review/"' in page, "static prototype must preserve its Optical Review route"
    assert "edgeglow-fish-static/" in acceptance_page and 'id="opticalReviewEntry"' in page and 'href="./optical-review/"' in page, "canonical Acceptance Center route linkage missing"
    assert "静态 PNG 证据" in optical_page and "./evidence/static-v3/" in optical_page, "Optical Review must link to the deployed static evidence gallery"
    assert f'"head_sha":"{args.pr_sha}"' in optical_build, "Optical Review build SHA does not match the deployed PR source"
    assert static_report["source_sha"] == args.pr_sha == static_report["build_sha"], "static evidence source/build SHA mismatch"
    assert static_report["deployment_source_sha"] == args.pr_sha, "static evidence expected deploy SHA does not match the PR source"
    assert static_report["page_url"] == base + OPTICAL_REVIEW and static_report["acceptance_url"] == base + ROUTE, "static evidence must link to the deployed canonical routes"
    assert static_report["gates"]["composed_page_build_binding"] == "PASS"
    assert static_report["gates"]["public_https_route_verification"] == "POST_DEPLOY_LIVE_SMOKE_REQUIRED"
    assert static_report["gates"]["source_photo_identity_to_frozen"] == "SOURCE_PHOTO_IDENTITY_UNVERIFIED"
    assert static_report["gates"]["human_static_optical_review"] == "REVIEW_REQUIRED"
    assert static_report["gates"]["fish_focus_production"] == "BLOCKED"
    assert static_report["gates"]["android_runtime"] == "NOT_RUN"
    assert "geometry-diagnostic-390x844.png" in gallery_page and "REVIEW_REQUIRED" in gallery_page

    report = {
        "task_id": "YUJIAN_EDGEGLOW_WEB_STATIC_VISUAL_CLOSURE_V3",
        "task_scope": "WEB_STATIC_ONLY",
        "status": "PASS_LIVE_ROUTE_BUILD_AND_STATIC_EVIDENCE_BYTES",
        "cloud_route_url": route_url,
        "optical_review_url": base + OPTICAL_REVIEW,
        "static_evidence_gallery_url": base + STATIC_EVIDENCE,
        "pr_source_sha": build_info["pr_source_sha"],
        "deployment_source_sha": args.pr_sha,
        "acceptance_center_source_sha": build_info["acceptance_center_source_sha"],
        "source_photo_sha256": actual_photo_sha,
        "source_photo_dimensions": build_info["photo_dimensions"],
        "optical_review_build_sha": args.pr_sha,
        "static_evidence_png_sha256": image_hashes,
        "static_evidence_report_sha256": sha256(static_report_bytes),
        "visual_review": "REVIEW_REQUIRED · live HTTP and PNG integrity do not establish optical quality",
        "public_https_route_verification": "PASS",
        "frozen_source_photo_identity": "SOURCE_PHOTO_IDENTITY_UNVERIFIED",
        "production_fish_focus": "BLOCKED",
        "android_runtime": "NOT_RUN",
        "native_implementation": "NOT_RUN",
        "contour_source": build_info["contour_source"],
        "http_checks": ["acceptance center", "canonical static route", "Optical Review", "review build SHA", "static evidence gallery", f"{len(image_hashes)} browser PNG payloads and SHA-256 matches", "candidate parameter JSON SHA-256", "registered photo bytes and SHA-256"],
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
