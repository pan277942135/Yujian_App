#!/usr/bin/env python3
"""Fast, deterministic source and asset audit for the EdgeGlow fish preview."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path


EXPECTED_PHOTO_SHA = "6e955087108f7463eca2dbac489699a4942028ebe1b3d456ddfca523286385ef"
ROUTE = Path("design/manager/nh01_acceptance/edgeglow-fish-static")
PHOTO = Path("design/system/fish_media_display_v1/assets/real_catch_source.jpg")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--site-root", type=Path, required=True)
    parser.add_argument("--pr-sha", required=True)
    parser.add_argument("--center-sha", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    route = args.site_root / ROUTE
    fixture = json.loads((route / "fixture.json").read_text(encoding="utf-8"))
    page = (route / "index.html").read_text(encoding="utf-8")
    renderer = (route / "edgeglow.js").read_text(encoding="utf-8")
    info = json.loads((route / "build-info.json").read_text(encoding="utf-8"))
    photo = args.site_root / PHOTO
    actual_sha = sha256(photo)

    assert fixture["photo"]["sha256"] == EXPECTED_PHOTO_SHA == actual_sha, "registered photo SHA mismatch"
    assert fixture["photo"]["width"] == 1152 and fixture["photo"]["height"] == 1536, "photo dimensions fixture changed"
    assert fixture["contour"]["source"] == "MANUAL_REFERENCE_CONTOUR", "contour provenance changed"
    assert fixture["contour"]["production_mask"] is False, "manual fixture must not masquerade as a production mask"
    assert fixture["contour"]["coordinate_space"] == "original_photo_px_1152x1536", "contour coordinate space changed"
    assert len(fixture["contour"]["path_d"]) > 500 and " C " in fixture["contour"]["path_d"], "expected irregular curved path"
    assert all(f'id="{canvas}"' in page for canvas in ("originalCanvas", "contourCanvas", "glowCanvas")), "three photo views are required"
    assert all(token in renderer for token in ("Path2D", "getTotalLength", "getPointAtLength", "screen", "blur(")), "Path-based layered renderer tokens are missing"
    assert "drawRoundRect" not in renderer and "addRoundRect" not in renderer, "fixed rounded-rectangle rendering returned"
    assert info["pr_source_sha"] == args.pr_sha and info["acceptance_center_source_sha"] == args.center_sha, "build provenance mismatch"

    args.output.parent.mkdir(parents=True, exist_ok=True)
    report = {
        "task_id": "YUJIAN_EDGEGLOW_FISH_STATIC_HIFI_V1",
        "status": "PASS_STATIC_SOURCE_AUDIT",
        "pr_source_sha": args.pr_sha,
        "acceptance_center_source_sha": args.center_sha,
        "source_photo_sha256": actual_sha,
        "source_photo_dimensions": [fixture["photo"]["width"], fixture["photo"]["height"]],
        "contour_source": fixture["contour"]["source"],
        "coordinate_space": fixture["contour"]["coordinate_space"],
        "checks": ["photo identity", "manual contour provenance", "irregular path geometry", "three aligned canvas views", "Path-length comet renderer", "layered glow tokens", "no rounded-rectangle substitute", "source SHA binding"],
    }
    args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
