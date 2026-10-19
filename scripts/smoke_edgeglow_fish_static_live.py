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

    assert "originalCanvas" in page and "contourCanvas" in page and "glowCanvas" in page, "three canvas views missing"
    assert "MANUAL_REFERENCE_CONTOUR" in page and fixture["contour"]["source"] == "MANUAL_REFERENCE_CONTOUR"
    assert actual_photo_sha == EXPECTED_PHOTO_SHA == build_info["photo_sha256"] == fixture["photo"]["sha256"], "deployed photo identity mismatch"
    assert build_info["pr_source_sha"] == args.pr_sha, "deployed PR source SHA mismatch"
    assert build_info["acceptance_center_source_sha"] == args.center_sha, "deployed Acceptance Center SHA mismatch"

    report = {
        "task_id": "YUJIAN_EDGEGLOW_FISH_STATIC_HIFI_V1",
        "status": "PASS_LIVE_ROUTE_AND_ASSET_IDENTITY",
        "cloud_route_url": route_url,
        "pr_source_sha": build_info["pr_source_sha"],
        "acceptance_center_source_sha": build_info["acceptance_center_source_sha"],
        "source_photo_sha256": actual_photo_sha,
        "source_photo_dimensions": build_info["photo_dimensions"],
        "contour_source": build_info["contour_source"],
        "http_checks": ["route HTML", "build-info.json", "fixture.json", "photo bytes and SHA-256"],
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
