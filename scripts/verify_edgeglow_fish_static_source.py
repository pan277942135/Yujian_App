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
OPTICAL_CONFIG = Path("design/system/recognition_optical_v2/RecognitionOpticalConfigV2.json")
ANDROID_EDGE_FIELD = Path("app/src/main/java/com/yujian/ai/ui/recognition/RecognitionAmbientField.kt")
ANDROID_OPTICAL_CONFIG = Path("app/src/main/java/com/yujian/ai/ui/recognition/RecognitionOpticalConfigV2.kt")


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
    assert "edgeglow.js?v=edgeglow-static-r12-corner-rim" in page, "the deployed prototype must load R12 with crisp inset full-length corner arcs and bypass stale cache entries"
    renderer = (route / "edgeglow.js").read_text(encoding="utf-8")
    info = json.loads((route / "build-info.json").read_text(encoding="utf-8"))
    optical_config = json.loads((args.site_root / OPTICAL_CONFIG).read_text(encoding="utf-8"))
    pr_root = Path(__file__).resolve().parents[1]
    android_edge_field = (pr_root / ANDROID_EDGE_FIELD).read_text(encoding="utf-8")
    android_optical_config = (pr_root / ANDROID_OPTICAL_CONFIG).read_text(encoding="utf-8")
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
    assert all(f'id="{control}"' in page for control in ("edgeFieldEnabled", "receivingLightEnabled", "primaryIslandsEnabled", "secondaryFragmentsEnabled", "companionHairlinesEnabled", "edgeFieldIntensity")), "photo-edge light controls are required"
    assert all(token in renderer for token in ("drawReceivingLight", "drawPrimaryEdgeIslands", "drawSecondaryEdgeFragments", "drawCompanionHairlines", "drawStaticEnergyNodes", "edgeFieldPartsAt", 'id: "B1"', 'id: "B3"', 'id: "G1"', 'id: "G3"', 'id: "B2"', 'id: "B4"', 'id: "G2"', 'id: "B1C"', 'id: "B1M"')), "four primary islands, legacy fragments, companion hairlines and path-following local light are required"
    receiving_renderer = renderer.split("function drawReceivingLight", 1)[1].split("function edgeFieldPartsAt", 1)[0]
    perimeter_path_renderer = renderer.split("function drawEdgeFieldPathSet", 1)[1].split("function drawCompanionHairlines", 1)[0]
    assert 'ctx.globalCompositeOperation = "source-over"' in receiving_renderer and 'ctx.globalCompositeOperation = "source-over"' in perimeter_path_renderer, "photo-edge light must preserve blue/gold hue over bright photo pixels"
    assert "ctx.ellipse(" not in receiving_renderer and "createRadialGradient" not in receiving_renderer, "Receiving Light must follow island paths, not render circular corner glows"
    assert receiving_renderer.count("ctx.stroke();") == 3 and "EDGE_RECEIVING_BLUR_DP[0] * photoPxPerDp * EDGE_FIELD_RENDER_SCALE" in receiving_renderer and "EDGE_RECEIVING_BLUR_DP[1] * photoPxPerDp * EDGE_FIELD_RENDER_SCALE" in receiving_renderer and "EDGE_RECEIVING_WIDTH_DP[0] * photoPxPerDp" in receiving_renderer and "EDGE_RECEIVING_WIDTH_DP[1] * photoPxPerDp" in receiving_renderer and "EDGE_RECEIVING_CORE_WIDTH_DP * photoPxPerDp" in receiving_renderer and "receivingGradient(EDGE_RECEIVING_CORE_ALPHA)" in receiving_renderer, "R12 requires two broad path-following blooms plus a crisp third perimeter-ridge pass"
    assert "const EDGE_RECEIVING_VISIBLE_RATIO = 1.0;" in renderer and "const EDGE_RECEIVING_INSET = Object.freeze([0.028, 0.022]);" in renderer and "const EDGE_RECEIVING_MIN_PATH_WEIGHT = 0.92;" in renderer and "const common = intensity * Math.max(EDGE_RECEIVING_MIN_PATH_WEIGHT, geometry.weight);" in receiving_renderer and "[[0, geometry.length]]" in receiving_renderer and "ctx.translate(photo.naturalWidth * insetXRatio, photo.naturalHeight * insetYRatio);" in receiving_renderer and "ctx.scale(1 - 2 * insetXRatio, 1 - 2 * insetYRatio);" in receiving_renderer, "R12 keeps the four inset path arcs full length while adding only a luminous crest; thin source paths retain Quiet Gaps"
    assert "const EDGE_RECEIVING_ALPHA_PER_PASS = Object.freeze([0.78, 0.64]);" in renderer and "const EDGE_RECEIVING_CORE_WIDTH_DP = 1.8;" in renderer and "const EDGE_RECEIVING_CORE_ALPHA = 0.88;" in renderer and "receivingGradient(EDGE_RECEIVING_ALPHA_PER_PASS[0])" in receiving_renderer and "receivingGradient(EDGE_RECEIVING_ALPHA_PER_PASS[1])" in receiving_renderer and "R12" in renderer, "R12 blue/gold corner visibility candidate must remain explicit and distinct from Android runtime"
    assert "const EDGE_FIELD_RENDER_SCALE = 0.5;" in renderer and "function ensurePhotoEdgeLayer()" in renderer and "const inset = ensurePhotoEdgeLayer()" in renderer and "drawImage(edgeLayer, inset, inset, Math.ceil(photo.naturalWidth * EDGE_FIELD_RENDER_SCALE), Math.ceil(photo.naturalHeight * EDGE_FIELD_RENDER_SCALE), 0, 0, photo.naturalWidth, photo.naturalHeight)" in renderer, "half-resolution edge blur must bleed past photo bounds before cropping back to original pixels"
    companion_renderer = renderer.split("function drawCompanionHairlines", 1)[1].split("function drawStaticEnergyNodes", 1)[0]
    node_renderer = renderer.split("function drawStaticEnergyNodes", 1)[1].split("function drawPrimaryEdgeIslands", 1)[0]
    assert all('ctx.globalCompositeOperation = "source-over"' in fn and 'ctx.globalCompositeOperation = "screen"' not in fn for fn in (companion_renderer, node_renderer)), "all perimeter layers must preserve hue on bright pixels"
    assert all(f'id="{control}"' in page for control in ("edgeFieldBlueColor", "edgeFieldGoldColor", "fishGoldColor", "fishCoreColor")), "perimeter and fish palettes must have separate controls"
    assert optical_config["schema_version"] == "2.0.0" and optical_config["task_id"] == "YUJIAN_EDGEGLOW_OPTICAL_ENHANCEMENT_ANDROID_V2", "shared optical config version/task identity mismatch"
    assert set(optical_config["presets"]) == {"current", "enhanced", "frozen_match"}, "Current, Enhanced and Frozen Match presets are required"
    assert optical_config["photo_mapping"]["source_coordinate_space"] == "EXIF_NORMALIZED_PHOTO" and "photoContentRect" in optical_config["photo_mapping"]["overlay_bounds"], "shared contract must bind overlays to the normalized photo content rect"
    assert [island["id"] for island in optical_config["primary_islands"]] == ["G1", "B1", "G3", "B3"], "four independently calibrated corner islands are required"
    assert [island["strength"] for island in optical_config["primary_islands"]] == [0.9, 1.0, 0.88, 0.84], "shared corner strengths must match the Enhanced Android mapping"
    assert optical_config["recognition_animation"]["minimum_visible_duration_ms"] == {"CAPTURED": 220, "DETECTING": 320, "OUTLINE": 350, "CLASSIFYING": 250}, "state-machine minimum visible durations must be versioned"
    assert optical_config["fish_mask_contract"]["static_demo_fixture"] == "MANUAL_REFERENCE_CONTOUR; production_mask=false", "manual demo contour provenance must remain explicit"
    assert optical_config["android_mapping"]["alpha_modifiers"] == {"inner_bloom": 0.78, "outer_bloom": 0.74}, "static and native bloom alpha mappings must agree"
    assert set(optical_config["android_mapping"]["quality_profiles"]) == {"HIGH", "MEDIUM", "LOW"}, "HIGH/MEDIUM/LOW performance profiles are required"
    assert optical_config["android_mapping"]["quality_profiles"]["MEDIUM"]["outer_bloom_radius_multiplier"] == 0.75
    assert optical_config["android_mapping"]["quality_profiles"]["MEDIUM"]["spill_radius_multiplier"] == 0.75
    assert optical_config["android_mapping"]["quality_profiles"]["LOW"]["outer_bloom_radius_multiplier"] == 0.5
    assert optical_config["android_mapping"]["quality_profiles"]["LOW"]["spill_radius_multiplier"] == 0.5
    assert all(f'data-optical-preset="{preset}"' in page for preset in ("current", "enhanced", "frozen_match")), "three live preset controls are required"
    assert all(f'id="{control}"' in page for control in ("opticalCoreIntensity", "opticalCoreWidth", "opticalCoreBlend", "innerBloomRadius", "innerBloomIntensity", "outerBloomRadius", "outerBloomIntensity", "atmosphericSpill", "blueSaturation", "goldWarmth", "backgroundCompensation", "strengthG1", "strengthB1", "strengthG3", "strengthB3")), "the V2 optical parameter controls are incomplete"
    assert all(token in renderer for token in ("OPTICAL_CONFIG_VERSION", "drawEnhancedOpticalIslands", "brightBackgroundGain", 'params.opticalCoreBlend || "source-over"', 'blend: "screen"', '"lighter"', "measureIslandLuminance", "params.backgroundCompensation")), "four-layer optical renderer or local background compensation is missing"
    assert '<option value="soft-light">Soft Light</option>' in page and "SOFT_LIGHT" in optical_config["blend_mode_trials"]["optical_core_candidates"], "Source Over, Screen, Additive and Soft Light must be selectable and declared as browser Core trials"
    assert "RecognitionOpticalConfigV2" in android_optical_config and 'VERSION = "2.0.0"' in android_optical_config, "Android must publish the matching V2 parameter mapping"
    assert all(str(value) in android_optical_config for value in ("0.90f", "1.00f", "0.88f", "0.84f")), "Android island strengths must match the Enhanced shared config"
    assert all(token in android_optical_config for token in ("INNER_BLOOM_ALPHA_MODIFIER = .78f", "OUTER_BLOOM_ALPHA_MODIFIER = .74f", "RecognitionQualityLevel.FULL", "RecognitionQualityLevel.BALANCED", "RecognitionQualityLevel.LITE", "RecognitionQualityLevel.BALANCED -> .75f", "RecognitionQualityLevel.LITE -> .50f", "BRIGHT_LUMINANCE_THRESHOLD = .46f", "BRIGHT_BACKGROUND_MAX_GAIN = .28f")), "Android quality, bloom and background-compensation mapping must follow the shared contract"
    assert "if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)" in android_edge_field and "RecognitionOpticalBlend.SOFT_LIGHT -> PorterDuffXfermode(PorterDuff.Mode.LIGHTEN)" in android_edge_field, "API28 must use the documented Lighten fallback without calling API29 BlendMode methods"
    api28_blend_block = android_edge_field.split("if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)", 1)[1].split("\n        }", 1)[1].split("\n    }", 1)[0]
    assert "blendMode = null" not in api28_blend_block, "API28 fallback must not invoke the API29 Paint.blendMode setter"
    assert all(token in android_edge_field for token in ("RecognitionVisualStateController", "RecognitionImageTransform.photoContentRect")) or "photoContentRect" in android_edge_field, "Android optical geometry must clip to the photo content rect"
    assert optical_config["android_mapping"]["source"].endswith("RecognitionOpticalConfigV2.kt"), "shared config must declare its Android mapping source"
    assert all(asset in page for asset in ("02_AI_Understanding_Frozen.png", "03_Fish_Highlight_Frozen.png", "04_Fish_Identifying_Frozen.png")), "all three frozen optical references must be shown"
    fish_renderer = renderer.split("function createContourGradient", 1)[1].split("function drawReceivingLight", 1)[0]
    assert "params.edgeFieldBlueColor" not in fish_renderer and "params.blueColor" not in fish_renderer, "blue/cyan perimeter color must never enter Fish Focus"
    assert "params.fishGoldColor" in fish_renderer and "params.fishCoreColor" in fish_renderer, "Fish Focus must render a warm-gold palette"
    assert 'ctx.globalCompositeOperation = "screen"' not in fish_renderer and fish_renderer.count('ctx.globalCompositeOperation = "source-over"') == 3, "fish Bloom/Core/Comet must preserve gold hue instead of washing the contour toward white"
    assert 'fish_highlight: { source: fixture.contour.source, composite_mode: "source-over hue-preserving warm-gold composition"' in renderer, "export contract must distinguish warm-gold fish highlight from blue/gold photo perimeter"
    assert "blueColor" not in page and "#58c8f4" not in renderer, "obsolete blue fish-highlight controls/palette must be removed"
    assert 'fishGoldColor: "#ffd887", fishCoreColor: "#ffe7ae"' in renderer, "Fish Focus defaults must use the approved warm-gold family"
    assert 'edgeFieldBlueColor: "#5ab2fc", edgeFieldGoldColor: "#e0b644"' in renderer, "photo perimeter uses the R05 Frozen-raster Web palette candidate"
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
    assert all(token in android_edge_field for token in ("recognitionAmbientPrimaryIslandIds = setOf(\"B1\", \"G1\", \"B3\", \"G3\")", "recognitionAmbientSecondaryFragmentIds = setOf(\"B2\", \"G2\", \"B4\")", "Cubic(.78f, -.02f)", "Cubic(1.02f, .61f)", "Cubic(-.02f, .18f)", "Cubic(-.01f, .74f)", "ATMOSPHERIC_SPILL_INTENSITY * common", "OUTER_BLOOM_INTENSITY *", "INNER_BLOOM_INTENSITY *", "CORE_INTENSITY * islandCommon")), "Android source must keep the existing four primary paths and expose all four V2 optical layers"
    assert "receiving_alpha_per_pass: EDGE_RECEIVING_ALPHA_PER_PASS" in renderer and "receiving_core_width_dp: EDGE_RECEIVING_CORE_WIDTH_DP" in renderer and "receiving_core_alpha: EDGE_RECEIVING_CORE_ALPHA" in renderer and "receiving_inset_fraction: EDGE_RECEIVING_INSET" in renderer and "corner_color_roles: { top_left: \"gold\", top_right: \"blue\", bottom_left: \"gold\", bottom_right: \"blue\" }" in renderer and "receiving_width_dp: EDGE_RECEIVING_WIDTH_DP" in renderer and "receiving_blur_dp: EDGE_RECEIVING_BLUR_DP" in renderer and '"R12 four-corner Web visibility candidate with a crisp perimeter ridge; above Android runtime 0.13 and Frozen Rendering Contract cap 0.07; open RP-A01"' in renderer and 'perimeter_blend_mode: "source-over R02 hue-preserving photo-edge candidate' in renderer, "R12 parameter contract must expose its photo inset, crest values, color roles, Web values and Android/Frozen cap discrepancy"
    assert 'web_palette: { blue: "#5ab2fc", gold: "#e0b644", authority: "sampled from Frozen 02 photo-edge raster; web visual candidate" }' in renderer and 'android_reference_palette: { blue: "#bfeaf2", blue_hot: "#d8f5fa", gold: "#f6d99b", gold_hot: "#ffe7ae" }' in renderer, "Web Frozen-raster palette must remain distinct from Android tokens"
    expected_companion_path_definitions = (
        '{ id: "B1C", parentId: "B1", family: "blue", widthDp: 0.60, phaseDelta: -0.055, visibleRatio: 0.44, weight: 0.30, start: [0.75, -0.01], control1: [0.91, 0.07], control2: [0.99, 0.20], end: [0.94, 0.37] }',
        '{ id: "G1C", parentId: "G1", family: "gold", widthDp: 0.58, phaseDelta: 0.065, visibleRatio: 0.42, weight: 0.30, start: [-0.01, 0.20], control1: [0.04, 0.11], control2: [0.13, 0.04], end: [0.24, 0.00] }',
        '{ id: "G3C", parentId: "G3", family: "gold", widthDp: 0.60, phaseDelta: 0.050, visibleRatio: 0.38, weight: 0.30, start: [0.01, 0.77], control1: [0.09, 0.87], control2: [0.20, 0.94], end: [0.34, 0.99] }',
        '{ id: "B3C", parentId: "B3", family: "blue", widthDp: 0.58, phaseDelta: -0.060, visibleRatio: 0.34, weight: 0.30, start: [0.99, 0.65], control1: [0.91, 0.77], control2: [0.82, 0.90], end: [0.69, 0.99] }',
        '{ id: "B1M", parentId: "B1", family: "blue", widthDp: 0.42, phaseDelta: 0.085, visibleRatio: 0.12, weight: 0.18, start: [0.84, -0.01], control1: [0.94, 0.05], control2: [0.99, 0.13], end: [0.985, 0.24] }',
    )
    assert all(definition in renderer for definition in expected_companion_path_definitions), "companion and B1-only micro geometry must match RecognitionAmbientField.kt"
    assert "drawCompanionHairlines(edgeContext, companionEdgeFieldGeometry)" in renderer and "drawCompanionHairlines(edgeContext, microEdgeFieldGeometry, true)" in renderer, "frozen companion/micro layers must be drawn independently"
    assert info["pr_source_sha"] == args.pr_sha and info["acceptance_center_source_sha"] == args.center_sha, "build provenance mismatch"
    current_light = optical_config["presets"]["current"]["legacy_receiving_light"]
    assert current_light["alpha_per_pass"] == [0.78, 0.64] and current_light["path_coverage_ratio"] == 1.0, "Current preset must reflect the live PR #166 R12 renderer"
    assert current_light["core_width_dp"] == 1.8 and current_light["core_alpha"] == 0.88
    assert current_light["inset_fraction"] == [0.028, 0.022]
    assert optical_config["bright_background_compensation"]["scope"] == "perimeter_light_only", "brightness compensation must leave photo exposure untouched"
    assert optical_config["fish_mask_contract"]["detector_output"].lower().find("bounding box") >= 0, "detector output provenance must distinguish BBox from pixel mask"
    assert "NOT_VALIDATED" in optical_config["fish_mask_contract"]["fish_specific_semantic_accuracy"], "generic subject segmentation must not be presented as fish-specific accuracy"

    args.output.parent.mkdir(parents=True, exist_ok=True)
    report = {
        "task_id": "YUJIAN_EDGEGLOW_OPTICAL_ENHANCEMENT_ANDROID_V2",
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
            "android_git_blob_sha": hashlib.sha1(b"blob " + str(len(android_edge_field.encode("utf-8"))).encode("ascii") + b"\0" + android_edge_field.encode("utf-8")).hexdigest(),
            "primary_path_ids": ["B1", "B3", "G1", "G3"],
            "secondary_path_ids": ["B2", "G2", "B4"],
            "secondary_seed_authority": {"source": "Android runtime", "frozen": False, "tracking": "RP-A03"},
            "companion_path_ids": ["B1C", "G1C", "G3C", "B3C"],
            "micro_path_ids": ["B1M"],
            "receiving_light_renderer": "four disconnected path-following corner arcs with two soft blooms and a crisp luminous ridge; padded offscreen blur bleed; no corner circles",
            "receiving_light_web_passes": 3,
            "receiving_light_bloom_passes": 2,
            "receiving_light_rim_width_dp": current_light["core_width_dp"],
            "receiving_light_rim_alpha": current_light["core_alpha"],
            "receiving_light_alpha_per_pass": current_light["alpha_per_pass"],
            "receiving_light_unweighted_configured_peak_alpha": round(1 - (1 - current_light["alpha_per_pass"][0]) * (1 - current_light["alpha_per_pass"][1]) * (1 - current_light["core_alpha"]), 4),
            "perimeter_web_blend": "source-over; R02 hue-preserving Web review candidate, not Android runtime validation",
            "web_palette": {"blue": "#5ab2fc", "gold": "#e0b644", "authority": "sampled Frozen 02 photo-edge raster; review candidate"},
            "android_reference_palette": {"blue": "#bfeaf2", "blue_hot": "#d8f5fa", "gold": "#f6d99b", "gold_hot": "#ffe7ae"},
            "receiving_light_default_alpha_per_pass": current_light["alpha_per_pass"],
            "receiving_light_path_coverage_ratio": current_light["path_coverage_ratio"],
            "receiving_light_minimum_path_weight": current_light["minimum_path_weight"],
            "receiving_light_width_dp": current_light["width_dp"],
            "receiving_light_blur_dp": current_light["blur_dp"],
            "receiving_light_inset_fraction": current_light["inset_fraction"],
            "receiving_light_corner_color_roles": {"top_left": "gold", "top_right": "blue", "bottom_left": "gold", "bottom_right": "blue"},
            "edge_effect_compositor": "padded transparent offscreen layer; crop back to original photo pixels",
            "edge_effect_padding_px": "responsive; recorded at runtime by the browser lab",
            "bright_background_compensation": optical_config["bright_background_compensation"],
            "web_current_authority": "PR #166 current R12 renderer; separate from the V2 enhanced and Frozen Match presets",
            "alpha_reconciliation": "R12_WEB_CURRENT_IS_NOT_AN_ANDROID_ALPHA_MAPPING",
        },
        "optical_config": {
            "version": optical_config["schema_version"],
            "source": str(OPTICAL_CONFIG),
            "presets": ["current", "enhanced", "frozen_match"],
            "android_mapping": str(ANDROID_OPTICAL_CONFIG),
            "renderer": "four-layer per-island normalized optical field with bounded local luminance compensation",
            "presets_detail": optical_config["presets"],
            "photo_mapping": optical_config["photo_mapping"],
            "recognition_animation": optical_config["recognition_animation"],
            "fish_mask_contract": optical_config["fish_mask_contract"],
            "android_blend_modes": optical_config["android_mapping"]["blend_modes"],
            "android_api28_fallback": optical_config["android_mapping"]["api28_fallback"],
            "android_quality_profiles": optical_config["android_mapping"]["quality_profiles"],
            "optical_core_blend_trials": optical_config["blend_mode_trials"],
        },
        "checks": ["photo identity", "manual contour provenance", "irregular fish geometry", "three aligned canvas views", "three switchable optical presets", "versioned shared parameter config", "Path-length comet renderer", "independent Receiving Light, Primary Island, Secondary Fragment and Companion/Micro layers", "R12 full-length corner arcs with inner inset, two broad blooms and crisp luminous ridge", "four-layer V2 renderer with padding and bounded local luminance compensation", "Frozen four-island color-role mapping", "all seven Android path coordinates and five companion/micro path coordinates", "EXIF/photoContentRect/window coordinate contract", "state timing and quality profiles", "fish segmentation provenance", "secondary seeds marked non-Frozen", "no rounded-rectangle substitute", "source SHA binding"],
    }
    args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
