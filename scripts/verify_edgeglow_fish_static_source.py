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
ANDROID_EDGE_FIELD = Path("app/src/main/java/com/yujian/ai/ui/recognition/RecognitionAmbientField.kt")


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
    pr_root = Path(__file__).resolve().parents[1]
    android_edge_field = (pr_root / ANDROID_EDGE_FIELD).read_text(encoding="utf-8")
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
    assert all(f'id="{control}"' in page for control in ("edgeFieldEnabled", "receivingLightEnabled", "primaryIslandsEnabled", "secondaryFragmentsEnabled", "edgeFieldIntensity")), "photo-edge light controls are required"
    assert all(token in renderer for token in ("drawReceivingLight", "drawPrimaryEdgeIslands", "drawSecondaryEdgeFragments", "edgeFieldParts", 'id: "B1"', 'id: "B3"', 'id: "G1"', 'id: "G3"', 'id: "B2"', 'id: "B4"', 'id: "G2"')), "four primary paths, three secondary fragments and local receiving light are required"
    assert all(f'id="{control}"' in page for control in ("edgeFieldBlueColor", "edgeFieldGoldColor", "fishGoldColor", "fishCoreColor")), "perimeter and fish palettes must have separate controls"
    fish_renderer = renderer.split("function createContourGradient", 1)[1].split("function drawReceivingLight", 1)[0]
    assert "params.edgeFieldBlueColor" not in fish_renderer and "params.blueColor" not in fish_renderer, "blue/cyan perimeter color must never enter Fish Focus"
    assert "params.fishGoldColor" in fish_renderer and "params.fishCoreColor" in fish_renderer, "Fish Focus must render a warm-gold palette"
    assert "blueColor" not in page and "#58c8f4" not in renderer, "obsolete blue fish-highlight controls/palette must be removed"
    assert 'fishGoldColor: "#ffd887", fishCoreColor: "#ffe7ae"' in renderer, "Fish Focus defaults must use the approved warm-gold family"
    assert 'edgeFieldBlueColor: "#bfeaf2", edgeFieldGoldColor: "#f6d99b"' in renderer, "photo perimeter retains its separate blue/cyan and gold palette"
    expected_primary_path_definitions = (
        '{ id: "B1", family: "blue", widthDp: 1.2, seed: 0.00, visibleRatio: 0.30, weight: 1.00, start: [0.78, -0.02], control1: [0.94, 0.06], control2: [1.02, 0.22], end: [0.96, 0.42] }',
        '{ id: "B3", family: "blue", widthDp: 1.2, seed: 0.47, visibleRatio: 0.20, weight: 0.54, start: [1.02, 0.61], control1: [0.92, 0.75], control2: [0.84, 0.89], end: [0.66, 1.02] }',
        '{ id: "G1", family: "gold", widthDp: 1.1, seed: 0.12, visibleRatio: 0.26, weight: 0.78, start: [-0.02, 0.18], control1: [0.03, 0.08], control2: [0.12, 0.02], end: [0.26, -0.02] }',
        '{ id: "G3", family: "gold", widthDp: 1.2, seed: 0.64, visibleRatio: 0.22, weight: 0.63, start: [-0.01, 0.74], control1: [0.08, 0.86], control2: [0.20, 0.95], end: [0.38, 1.02] }',
    )
    assert all(definition in renderer for definition in expected_primary_path_definitions), "static Web path values must match the four Android primary values"
    expected_secondary_path_definitions = (
        '{ id: "B2", family: "blue", widthDp: 1.0, seed: 0.07, visibleRatio: 0.18, weight: 0.30, seedAuthority: "ANDROID_RUNTIME_RP_A03_NOT_FROZEN", start: [1.01, 0.18], control1: [0.92, 0.31], control2: [0.95, 0.52], end: [1.02, 0.68] }',
        '{ id: "B4", family: "blue", widthDp: 0.8, seed: 0.11, visibleRatio: 0.14, weight: 0.22, seedAuthority: "ANDROID_RUNTIME_RP_A03_NOT_FROZEN", start: [0.22, 1.02], control1: [0.38, 0.95], control2: [0.52, 0.95], end: [0.66, 1.01] }',
        '{ id: "G2", family: "gold", widthDp: 0.9, seed: 0.09, visibleRatio: 0.16, weight: 0.26, seedAuthority: "ANDROID_RUNTIME_RP_A03_NOT_FROZEN", start: [-0.02, 0.33], control1: [0.04, 0.47], control2: [0.02, 0.62], end: [-0.01, 0.78] }',
    )
    assert all(definition in renderer for definition in expected_secondary_path_definitions), "secondary path geometry and runtime seed authority must match the existing P1/Android source"
    assert all(token in android_edge_field for token in ("recognitionAmbientPrimaryIslandIds = setOf(\"B1\", \"G1\", \"B3\", \"G3\")", "recognitionAmbientSecondaryFragmentIds = setOf(\"B2\", \"G2\", \"B4\")", "Cubic(.78f, -.02f)", "Cubic(1.02f, .61f)", "Cubic(-.02f, .18f)", "Cubic(-.01f, .74f)", "alpha = (.13f * common * weight)")), "Android source binding changed; review the Web adapter and the tracked Receiving Light alpha discrepancy"
    assert "receiving_alpha_default: 0.07" in renderer and "const alpha = 0.07 * intensity * island.weight" in renderer and "tracked as RP-A01" in renderer, "web receiving light must use a visible in-contract value while exposing the Android alpha discrepancy"
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
        "edge_field": {
            "status": "PASS_STATIC_ADAPTER_SOURCE_CHECK",
            "android_source": str(ANDROID_EDGE_FIELD),
            "android_git_blob_sha": "1969409c2f903a16ea38ed2f73b705daa424c050",
            "primary_path_ids": ["B1", "B3", "G1", "G3"],
            "secondary_path_ids": ["B2", "G2", "B4"],
            "secondary_seed_authority": {"source": "Android runtime", "frozen": False, "tracking": "RP-A03"},
            "receiving_light_default_alpha": 0.07,
            "android_receiving_light_alpha": 0.13,
            "alpha_reconciliation": "OPEN_RP_A01",
        },
        "checks": ["photo identity", "manual contour provenance", "irregular fish geometry", "three aligned canvas views", "Path-length comet renderer", "independent Receiving Light, Primary Island and Secondary Fragment layers", "all seven Android path coordinates", "secondary seeds marked non-Frozen", "no rounded-rectangle substitute", "source SHA binding"],
    }
    args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
