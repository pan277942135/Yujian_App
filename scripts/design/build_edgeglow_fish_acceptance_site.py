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


CENTER_BRANCH = "design/nh01-cloud-structured-parity-v1"
PHOTO_PATH = "design/system/fish_media_display_v1/assets/real_catch_source.jpg"
EXPECTED_PHOTO_SHA = "6e955087108f7463eca2dbac489699a4942028ebe1b3d456ddfca523286385ef"
ROUTE_PATH = Path("design/manager/nh01_acceptance/edgeglow-fish-static")
ENTRY = (
    '<a class="mode-button edgeglow-entry" data-edgeglow-entry="recognition-processing" '
    'href="./edgeglow-fish-static/" aria-label="Recognition Processing → EdgeGlowView Fish Static Prototype">'
    'Recognition Processing → EdgeGlowView Fish Static Prototype</a>'
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
    if not all(path.is_file() for path in (registry_path, source_photo, fixture_path)):
        raise SystemExit("Expected photo registry, source image and prototype fixture are required.")
    registry = json.loads(registry_path.read_text(encoding="utf-8"))
    registered = registry["fixture"]
    fixture = json.loads(fixture_path.read_text(encoding="utf-8"))
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
    build_info = {
        "task_id": "YUJIAN_EDGEGLOW_FISH_STATIC_HIFI_V1",
        "status": "CLOUD_PREVIEW_COMPOSED",
        "pr_number": 166,
        "pr_source_sha": args.pr_sha,
        "acceptance_center_source_sha": args.center_sha,
        "acceptance_center_source_branch": CENTER_BRANCH,
        "photo_sha256": photo_sha,
        "photo_dimensions": [fixture["photo"]["width"], fixture["photo"]["height"]],
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
    print(json.dumps({"status": "PASS", "pr_source_sha": args.pr_sha, "center_source_sha": args.center_sha,
                      "photo_sha256": photo_sha, "route": "/design/manager/nh01_acceptance/edgeglow-fish-static/",
                      "entry_count": html.count('data-edgeglow-entry="recognition-processing"')}, indent=2))


if __name__ == "__main__":
    main()
