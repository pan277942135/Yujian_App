#!/usr/bin/env python3
"""Compose the current NH01 Pages source with PR #166's additive fish prototype."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import subprocess
from pathlib import Path


CENTER_BRANCH = "main"
PHOTO_PATH = "design/system/fish_media_display_v1/assets/real_catch_source.jpg"
EXPECTED_PHOTO_SHA = "6e955087108f7463eca2dbac489699a4942028ebe1b3d456ddfca523286385ef"
OPTICAL_CONFIG = Path("design/system/recognition_optical_v2/RecognitionOpticalConfigV2.json")
ROUTE_PATH = Path("design/manager/nh01_acceptance/edgeglow-fish-static")
OPTICAL_REVIEW_SOURCE = Path("design/manager/optical-lab")
OPTICAL_REVIEW_ROUTE = ROUTE_PATH / "optical-review"
CASE_B_FIXTURE = Path("app/src/androidTest/assets/detector_case_b_night_flash.jpg")
EVIDENCE_GALLERY_URL = "https://recognition-optical-lab-a1-20261019.pan277942135.chatgpt.site/design/manager/optical-lab/review_v1/evidence/"
ENTRY = (
    '<a class="mode-button edgeglow-entry" data-edgeglow-entry="recognition-processing" '
    'href="./edgeglow-fish-static/" aria-label="Recognition Processing → EdgeGlow Fish Static + Dynamic Review">'
    'Recognition Processing → EdgeGlow Fish Static Prototype · Static + Dynamic Review</a>'
)


def checked_sha(repo: Path) -> str:
    return subprocess.check_output(["git", "-C", str(repo), "rev-parse", "HEAD"], text=True).strip()


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--center-root", type=Path, required=True)
    parser.add_argument("--pr-root", type=Path, required=True)
    parser.add_argument("--output-root", type=Path, required=True)
    parser.add_argument("--center-sha", required=True)
    parser.add_argument("--pr-sha", required=True)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if not re.fullmatch(r"[0-9a-f]{40}", args.center_sha) or not re.fullmatch(r"[0-9a-f]{40}", args.pr_sha):
        raise SystemExit("Expected full 40-character source commits.")
    actual_center_sha = checked_sha(args.center_root)
    actual_pr_sha = checked_sha(args.pr_root)
    if actual_center_sha != args.center_sha:
        raise SystemExit(f"Acceptance Center SHA mismatch: expected {args.center_sha}, got {actual_center_sha}")
    if actual_pr_sha != args.pr_sha:
        raise SystemExit(f"PR #166 SHA mismatch: expected {args.pr_sha}, got {actual_pr_sha}")

    registry_path = args.center_root / "design/system/fish_media_display_v1/real_photo_cases.json"
    source_photo = args.center_root / PHOTO_PATH
    fixture_path = args.pr_root / ROUTE_PATH / "fixture.json"
    config_path = args.pr_root / OPTICAL_CONFIG
    optical_page = args.pr_root / OPTICAL_REVIEW_SOURCE / "index.html"
    case_b_photo = args.pr_root / CASE_B_FIXTURE
    review_alpha = args.pr_root / OPTICAL_REVIEW_SOURCE / "review_v1/fish_focus_review_only_alpha.png"
    bbox_record = args.pr_root / OPTICAL_REVIEW_SOURCE / "review_v1/fish_focus_case_b_bbox.json"
    if not all(path.is_file() for path in (registry_path, source_photo, fixture_path, config_path, optical_page, case_b_photo, review_alpha, bbox_record)):
        raise SystemExit("Expected photo registry, source image, static fixture, optical review, Case B fixture and provenance files are required.")
    registry = json.loads(registry_path.read_text(encoding="utf-8"))
    registered = registry["fixture"]
    fixture = json.loads(fixture_path.read_text(encoding="utf-8"))
    optical_config = json.loads(config_path.read_text(encoding="utf-8"))
    if optical_config.get("schema_version") != "2.0.0" or set(optical_config.get("presets", {})) != {"current", "enhanced", "frozen_match"}:
        raise SystemExit("Optical config must declare V2 and the Current/Enhanced/Frozen Match presets.")
    photo_sha = sha256(source_photo)
    if registered["path"] != PHOTO_PATH or registered["sha256"] != EXPECTED_PHOTO_SHA:
        raise SystemExit("The checked Acceptance Center source registry no longer matches this photo fixture.")
    if photo_sha != EXPECTED_PHOTO_SHA or fixture["photo"]["sha256"] != photo_sha:
        raise SystemExit(f"Photo source identity mismatch: {photo_sha}")
    if registered["fish_bbox_available"] is not False:
        raise SystemExit("This fixture is deliberately manual; replace only after the registered source contract is reviewed.")

    if args.output_root.exists() and any(args.output_root.iterdir()):
        raise SystemExit(f"Refusing to overwrite non-empty output directory: {args.output_root}")
    args.output_root.mkdir(parents=True, exist_ok=True)
    shutil.copytree(args.center_root / "design", args.output_root / "design")

    center_page = args.output_root / "design/manager/nh01_acceptance/index.html"
    html = center_page.read_text(encoding="utf-8")
    if html.count('data-edgeglow-entry="recognition-processing"') > 1:
        raise SystemExit("Duplicate EdgeGlow entry found in Acceptance Center source.")
    if 'data-edgeglow-entry="recognition-processing"' not in html:
        anchor = re.search(r'<nav class="mode-nav"[^>]*>[\s\S]*?</nav>', html)
        if not anchor:
            raise SystemExit("Could not locate the existing Acceptance Center mode navigation.")
        updated_nav = anchor.group(0).replace("</nav>", f"{ENTRY}\n    </nav>")
        html = html[: anchor.start()] + updated_nav + html[anchor.end() :]
    if html.count('data-edgeglow-entry="recognition-processing"') != 1:
        raise SystemExit("Acceptance Center entry is not unique after integration.")
    center_page.write_text(html, encoding="utf-8")

    prototype_source = args.pr_root / ROUTE_PATH
    prototype_output = args.output_root / ROUTE_PATH
    shutil.copytree(prototype_source, prototype_output)
    optical_output = args.output_root / OPTICAL_REVIEW_ROUTE
    shutil.copytree(args.pr_root / OPTICAL_REVIEW_SOURCE, optical_output)
    review_build_info = {
        "pull_request": 166,
        "head_sha": args.pr_sha,
        "workflow": "edgeglow-fish-static-pages-composition",
        "acceptance_center_source_sha": args.center_sha,
    }
    (optical_output / "build-info.js").write_text(
        "window.OPTICAL_LAB_BUILD_INFO__ = " + json.dumps(review_build_info, separators=(",", ":")) + ";\n",
        encoding="utf-8",
    )
    case_b_output = args.output_root / CASE_B_FIXTURE
    case_b_output.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(case_b_photo, case_b_output)
    shared_config_output = args.output_root / OPTICAL_CONFIG
    shared_config_output.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(config_path, shared_config_output)
    build_info = {
        "task_id": "YUJIAN_EDGEGLOW_OPTICAL_ENHANCEMENT_ANDROID_V2",
        "static_demo_task_id": "YUJIAN_EDGEGLOW_FISH_STATIC_HIFI_V1",
        "status": "CLOUD_PREVIEW_COMPOSED",
        "pr_number": 166,
        "pr_source_sha": args.pr_sha,
        "acceptance_center_source_sha": args.center_sha,
        "acceptance_center_source_branch": CENTER_BRANCH,
        "photo_sha256": photo_sha,
        "photo_dimensions": [fixture["photo"]["width"], fixture["photo"]["height"]],
        "optical_config_version": optical_config["schema_version"],
        "optical_config_sha256": sha256(config_path),
        "optical_review_route": "/design/manager/nh01_acceptance/edgeglow-fish-static/optical-review/",
        "evidence_gallery_url": EVIDENCE_GALLERY_URL,
        "contour_source": fixture["contour"]["source"],
        "android_runtime": "NOT_RUN",
        "visual_quality": "REVIEW_REQUIRED",
    }
    (prototype_output / "build-info.json").write_text(json.dumps(build_info, indent=2) + "\n", encoding="utf-8")

    manager_index = args.output_root / "design/manager/index.html"
    manager_html = manager_index.read_text(encoding="utf-8")
    manager_html = manager_html.replace("__DESIGN_MANAGER_BUILD__", args.pr_sha)
    manager_html = manager_html.replace("__DESIGN_MANAGER_BUILD_SHORT__", args.pr_sha[:12])
    manager_index.write_text(manager_html, encoding="utf-8")
    center_html = center_page.read_text(encoding="utf-8")
    center_html = center_html.replace("__NH01_SOURCE_SHA__", args.center_sha)
    center_html = center_html.replace("__NH01_VIEWER_BUILD__", args.center_sha)
    center_page.write_text(center_html, encoding="utf-8")
    (args.output_root / "design/manager/edgeglow-composed-build.json").write_text(
        json.dumps(build_info, indent=2) + "\n", encoding="utf-8"
    )

    (args.output_root / "index.html").write_text(
        '<!doctype html><html lang="zh-CN"><meta charset="utf-8">'
        '<meta http-equiv="refresh" content="0; url=./design/manager/">'
        '<title>YuJian Design Manager</title><a href="./design/manager/">Open Design Manager</a></html>\n',
        encoding="utf-8",
    )
    (args.output_root / ".nojekyll").touch()

    if not (prototype_output / "index.html").is_file() or not (prototype_output / "edgeglow.js").is_file():
        raise SystemExit("Prototype route is incomplete after composition.")
    if not all(path.is_file() for path in (optical_output / "index.html", optical_output / "review_v1/fish_focus_case_b_bbox.json", optical_output / "review_v1/fish_focus_review_only_alpha.png", case_b_output)):
        raise SystemExit("Static/dynamic Optical Review route or Fish Focus provenance inputs are incomplete after composition.")
    print(json.dumps({"status": "PASS", "pr_source_sha": args.pr_sha, "center_source_sha": args.center_sha,
                      "photo_sha256": photo_sha, "route": "/design/manager/nh01_acceptance/edgeglow-fish-static/",
                      "optical_review_route": "/design/manager/nh01_acceptance/edgeglow-fish-static/optical-review/",
                      "entry_count": html.count('data-edgeglow-entry="recognition-processing"')}, indent=2))


if __name__ == "__main__":
    main()
