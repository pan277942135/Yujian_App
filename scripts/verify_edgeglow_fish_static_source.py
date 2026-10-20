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
    optical_route = route / "optical-review"
    optical_page = (optical_route / "index.html").read_text(encoding="utf-8")
    optical_build_info = (optical_route / "build-info.js").read_text(encoding="utf-8")
    bbox_record = json.loads((optical_route / "review_v1/fish_focus_case_b_bbox.json").read_text(encoding="utf-8"))
    review_alpha = optical_route / "review_v1/fish_focus_review_only_alpha.png"
    case_b_photo = args.site_root / "app/src/androidTest/assets/detector_case_b_night_flash.jpg"
    assert "edgeglow.js?v=edgeglow-r13-shared-receiving-field" in page, "the deployed prototype must use an R13 cache-busted shared Receiving Light compositor"
    renderer = (route / "edgeglow.js").read_text(encoding="utf-8")
    info = json.loads((route / "build-info.json").read_text(encoding="utf-8"))
    optical_config = json.loads((args.site_root / OPTICAL_CONFIG).read_text(encoding="utf-8"))
    processing_config_path = args.site_root / "design/pages/recognition/processing/handoff/Processing_Engineering_Import_Config_V1.json"
    processing_config = json.loads(processing_config_path.read_text(encoding="utf-8"))
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
    assert "const EDGE_RECEIVING_COVERAGE_BY_ISLAND = Object.freeze({ B1: 1.0, G1: 0.84, G3: 0.60, B3: 0.38 });" in renderer and "const EDGE_RECEIVING_INSET = Object.freeze([0.028, 0.022]);" in renderer and "const EDGE_RECEIVING_MIN_PATH_WEIGHT = 0.54;" in renderer and "const common = intensity * Math.max(EDGE_RECEIVING_MIN_PATH_WEIGHT, geometry.weight);" in receiving_renderer and "const coverage = EDGE_RECEIVING_COVERAGE_BY_ISLAND[geometry.id] ?? geometry.visibleRatio;" in receiving_renderer and "[[0, geometry.length]]" in receiving_renderer and "ctx.translate(photo.naturalWidth * insetXRatio, photo.naturalHeight * insetYRatio);" in receiving_renderer and "ctx.scale(1 - 2 * insetXRatio, 1 - 2 * insetYRatio);" in receiving_renderer, "R13 keeps four inset path arcs deliberately unequal: B1 100%, G1 84%, G3 60%, B3 38%"
    assert "const EDGE_RECEIVING_ALPHA_PER_PASS = Object.freeze([0.78, 0.64]);" in renderer and "const EDGE_RECEIVING_CORE_WIDTH_DP = 1.8;" in renderer and "const EDGE_RECEIVING_CORE_ALPHA = 0.88;" in renderer and "receivingGradient(EDGE_RECEIVING_ALPHA_PER_PASS[0])" in receiving_renderer and "receivingGradient(EDGE_RECEIVING_ALPHA_PER_PASS[1])" in receiving_renderer and "R13" in renderer, "R13 blue/gold corner visibility candidate must remain explicit and distinct from Android runtime"
    render_buffer = renderer.split("function renderGlowBuffer()", 1)[1].split("function drawGlowComparison()", 1)[0]
    enhanced_render_branch = render_buffer.split("// Receiving Light is the broad perimeter base", 1)[1]
    assert render_buffer.count("drawReceivingLight(edgeContext);") == 2 and "drawReceivingLight(edgeContext);" in enhanced_render_branch and enhanced_render_branch.index("drawReceivingLight(edgeContext);") < enhanced_render_branch.index("for (const layerId of [\"atmospheric_spill\""), "Current and default Enhanced/Frozen Match must render Receiving Light before preset-specific optical detail layers"
    assert "const EDGE_FIELD_RENDER_SCALE = 0.5;" in renderer and "function ensurePhotoEdgeLayer()" in renderer and "const inset = ensurePhotoEdgeLayer()" in renderer and "drawImage(edgeLayer, inset, inset, Math.ceil(photo.naturalWidth * EDGE_FIELD_RENDER_SCALE), Math.ceil(photo.naturalHeight * EDGE_FIELD_RENDER_SCALE), 0, 0, photo.naturalWidth, photo.naturalHeight)" in renderer, "half-resolution edge blur must bleed past photo bounds before cropping back to original pixels"
    companion_renderer = renderer.split("function drawCompanionHairlines", 1)[1].split("function drawStaticEnergyNodes", 1)[0]
    node_renderer = renderer.split("function drawStaticEnergyNodes", 1)[1].split("function drawPrimaryEdgeIslands", 1)[0]
    assert all('ctx.globalCompositeOperation = "source-over"' in fn and 'ctx.globalCompositeOperation = "screen"' not in fn for fn in (companion_renderer, node_renderer)), "all perimeter layers must preserve hue on bright pixels"
    assert all(f'id="{control}"' in page for control in ("edgeFieldBlueColor", "edgeFieldGoldColor", "fishGoldColor", "fishCoreColor")), "perimeter and fish palettes must have separate controls"
    assert 'id="opticalReviewEntry" href="./optical-review/"' in page, "the existing EdgeGlow page must link to the integrated static/dynamic review"
    assert "DYNAMIC_REVIEW_ENABLED=true" in optical_page and "DYNAMIC_CANDIDATE_B" in optical_page, "the composed review route must include the enabled dynamic candidates"
    assert all(f'id="{control}"' in optical_page for control in ("previewModeButtons", "dynamicCandidate", "playPause", "motionSpeed", "focusSource", "fieldLayerView")), "the composed optical review must expose mode, candidates, playback, states and Fish Focus source controls"
    assert f'<script src="./build-info.js?source={args.pr_sha}"></script>' in optical_page, "nested review build provenance must use a source-versioned URL to avoid serving a stale cached SHA"
    assert "REVIEW_PROXY_NOT_PRODUCTION" in optical_page and "Production: BLOCKED" in optical_page, "review-only and production-blocked provenance must stay explicit"
    assert f'"head_sha":"{args.pr_sha}"' in optical_build_info, "nested review route must publish the exact PR source SHA"
    assert sha256(case_b_photo) == bbox_record["fixture_sha256"] == "a7ed3bb0410c191364f6d853b4078aebdc2f3b9baa4da34b7b3f6e8e0e93a56b", "the Case B detector fixture must remain byte-identical"
    assert sha256(review_alpha) == bbox_record["alpha_sha256"] == "ebcf100e49934065f318d5a37f9f1465601838d6af12dd48312045bbb776ae42", "Review-only alpha must remain byte-identical and provenance-bound"
    assert bbox_record["source_mode"] == "REVIEW_PROXY_NOT_PRODUCTION" and "manual" in bbox_record["alpha_provenance"].lower(), "annotated alpha must not be presented as a production Mask"
    assert optical_config["schema_version"] == "2.0.0" and optical_config["task_id"] == "YUJIAN_EDGEGLOW_OPTICAL_ENHANCEMENT_ANDROID_V2", "shared optical config version/task identity mismatch"
    assert processing_config["artifact_id"] == "recognition.processing.engineering_handoff" and len(processing_config["sources"]) == 27, "the composed Optical Review must include PR #166's existing P1-P5 source contract"
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
    assert all(token in renderer for token in ("OPTICAL_CONFIG_VERSION", "drawEnhancedOpticalIslands", "brightBackgroundGain", 'String(params.opticalCoreBlend || "source-over")', "opticalConfig.web_mapping.canvas_blend_mode_names", "measureIslandLuminance", "params.backgroundCompensation")), "four-layer optical renderer or local background compensation is missing"
    assert optical_config["web_mapping"]["renderer"] == "Canvas2D" and optical_config["web_mapping"]["blend_modes"] == {"optical_core": "SOURCE_OVER", "inner_bloom": "SCREEN", "outer_bloom": "SCREEN", "atmospheric_spill": "SOFT_LIGHT"}, "versioned Web layer blend roles are required"
    assert 'function opticalPhotoBlend(layerId)' in renderer and 'opticalConfig.web_mapping.blend_modes[layerId]' in renderer and 'bufferContext.globalCompositeOperation = blend' in renderer and 'compositePhotoEdge(bufferContext, inset, opticalPhotoBlend(layerId))' in renderer, "enhanced optical layers must composite directly onto the photo using the shared versioned blend modes"
    assert optical_config["android_mapping"]["blend_modes"] == {"optical_core": "SOURCE_OVER", "inner_bloom": "SCREEN", "outer_bloom": "SCREEN", "atmospheric_spill": "SOFT_LIGHT"}, "Android and Web layer blend roles must stay aligned"
    assert "blend = RecognitionOpticalBlend.SOFT_LIGHT" in android_edge_field, "Android atmospheric spill must use Soft Light with its declared API 28 fallback"
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
        '{ id: "B4", family: "blue", widthDp: 0.8, seed: 0.11, visibleRatio: 0.14, weight: 0.22, seedAuthority: "ANDROID_RUNTIME_RP_A03_NOT_FROZEN", start: [0.22, 1