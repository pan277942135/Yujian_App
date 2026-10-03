#!/usr/bin/env python3
"""Android-only P0 verifier for Empty Home V2.  V1 verifier is intentionally untouched."""

from __future__ import annotations

import hashlib
import json
import re
import struct
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
FEATURE = ROOT / "design/pages/home/empty_home"
CANONICAL = FEATURE / "shared/contracts"
RUNTIME = ROOT / "app/src/main/assets/empty_home_runtime_v2"
REQUIRED = {
    "static/scene_base.webp",
    "dynamic/cloud.png", "dynamic/sun_beam_mask.png", "dynamic/particle_mask.png",
    "dynamic/rod.png", "dynamic/line.png", "dynamic/bobber.png", "dynamic/bobber_reflection.png", "dynamic/ripple_mask.png",
    "camera/camera_button_base.png", "camera/camera_gold_rim_mask.png", "camera/camera_breath_glow.png",
    "config/runtime_manifest.json", "config/authority_manifest.json", "config/layer_contract.json",
    "config/anchor_contract.json", "config/responsive_mapping_contract.json",
    "config/motion_contract.json", "config/haptic_contract.json", "config/hero_asset_contract.json",
}
FORBIDDEN = ("proof", "preview", "validation", ".mp4", ".gif", "frozen", "source")


def load(path: Path) -> dict:
    return json.loads(path.read_text())


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def png_dimensions(path: Path) -> tuple[int, int]:
    with path.open("rb") as stream:
        if stream.read(8) != b"\x89PNG\r\n\x1a\n":
            raise AssertionError(f"not PNG: {path}")
        length = struct.unpack(">I", stream.read(4))[0]
        if stream.read(4) != b"IHDR" or length < 8:
            raise AssertionError(f"invalid PNG header: {path}")
        return struct.unpack(">II", stream.read(8))


def fail(errors: list[str], message: str) -> None:
    errors.append(message)


def main() -> int:
    errors: list[str] = []
    report: dict[str, object] = {"gate": "EMPTY_HOME_RUNTIME_V2", "errors": errors}
    missing = sorted(item for item in REQUIRED if not (RUNTIME / item).is_file())
    if missing:
        fail(errors, "missing runtime assets: " + ", ".join(missing))
    forbidden = sorted(str(path.relative_to(RUNTIME)) for path in RUNTIME.rglob("*") if path.is_file() and any(token in path.name.lower() for token in FORBIDDEN))
    if forbidden:
        fail(errors, "forbidden runtime payloads: " + ", ".join(forbidden))
    if errors:
        print(json.dumps(report, indent=2))
        return 1

    runtime = load(RUNTIME / "config/runtime_manifest.json")
    design_runtime = load(CANONICAL / "runtime_manifest.json")
    anchors = load(RUNTIME / "config/anchor_contract.json")
    motion = load(RUNTIME / "config/motion_contract.json")
    layers = load(RUNTIME / "config/layer_contract.json")
    authority = load(CANONICAL / "authority_manifest.json")
    responsive = load(CANONICAL / "responsive_mapping_contract.json")

    # The design-side machine contracts are the sole visual authority. Packaged
    # Runtime copies must remain byte-semantically equal, including responsive
    # mapping, so local APK assets cannot drift into a second active authority.
    for name in ("anchor_contract.json", "responsive_mapping_contract.json", "layer_contract.json", "motion_contract.json", "haptic_contract.json", "hero_asset_contract.json"):
        canonical_contract = load(CANONICAL / name)
        packaged_contract = load(RUNTIME / "config" / name)
        if canonical_contract != packaged_contract:
            fail(errors, f"design and packaged Runtime contract diverge: {name}")

    if authority.get("authority_status") != "CURRENT":
        fail(errors, "authority manifest is not marked CURRENT")
    if authority.get("design_version") != "Empty_Home_Final_Design_V2" or authority.get("visual_revision") != "V2.2":
        fail(errors, "authority manifest must name the measured V2.2 Fishing Composition")
    if authority.get("contracts", {}).get("responsive_mapping") != "responsive_mapping_contract.json":
        fail(errors, "authority manifest does not register the responsive mapping contract")
    if authority.get("contracts", {}).get("hero_asset") != "hero_asset_contract.json":
        fail(errors, "authority manifest does not register the frozen Hero asset contract")
    if design_runtime != runtime:
        fail(errors, "design and packaged Runtime manifests diverge")
    if responsive.get("design_version") != "Empty_Home_Final_Design_V2" or responsive.get("visual_revision") != "V2.2":
        fail(errors, "responsive mapping contract must name V2 / V2.2")
    if responsive.get("reference_canvas") != [1080, 1920]:
        fail(errors, "responsive mapping reference canvas must be [1080, 1920]")
    groups = responsive.get("groups", {})
    header_mapping = groups.get("safe_top_ui", {})
    if header_mapping.get("top_margin_after_safe_inset_dp") != 16 or header_mapping.get("maximum_content_width_dp") != 430 or header_mapping.get("minimum_touch_target_dp") != 48:
        fail(errors, "safe-top header geometry/target contract is incomplete")
    scene_mapping = groups.get("scene_space", {})
    if scene_mapping.get("strategy") != "UNIFORM_COVER_COMPOSITION_AWARE" or scene_mapping.get("all_scene_locked_elements_share_transform") is not True:
        fail(errors, "scene elements must share one composition-aware sceneTransform")
    expected_scene_targets = [
        {"width": 1080, "height": 1920}, {"width": 1080, "height": 2160},
        {"width": 1080, "height": 2340}, {"width": 1080, "height": 2400},
        {"width": 720, "height": 1600},
        {"width": 320, "height": 640, "validation": "STRUCTURAL_ONLY"},
    ]
    if responsive.get("responsive_scene_capture_targets_px") != expected_scene_targets:
        fail(errors, "responsive scene targets must cover the six approved aspect profiles")
    protected_bounds = scene_mapping.get("fishing_protected_bounds_reference_px", {})
    if (protected_bounds.get("left"), protected_bounds.get("right")) != (0, 660):
        fail(errors, "fishing protected horizontal bounds must preserve rod through ripple")
    if groups.get("hero_copy", {}).get("reference_machine_bbox_px") != {"x": 50, "y": 224, "width": 620, "height": 310}:
        fail(errors, "hero responsive mapping differs from the frozen machine bbox")
    if groups.get("hero_copy", {}).get("fill_bounds") is not False or groups.get("hero_copy", {}).get("stretch") is not False:
        fail(errors, "hero may not use FillBounds or anisotropic stretching")
    if [item.get("width_dp") for item in responsive.get("responsive_profiles_to_verify", [])] != [320, 360, 393, 411]:
        fail(errors, "responsive mapping must cover all four required width profiles")

    hero_contract = load(CANONICAL / "hero_asset_contract.json")
    hero_source = ROOT / hero_contract["source"]["path"]
    hero_design_asset = ROOT / hero_contract["asset"]["design_path"]
    hero_runtime_asset = ROOT / hero_contract["asset"]["runtime_resource_path"]
    if hero_contract.get("authority_status") != "CURRENT" or hero_contract.get("visual_revision") != "V2.2":
        fail(errors, "Hero asset contract is not the current V2.3 authority")
    if not hero_source.is_file() or digest(hero_source) != hero_contract["source"]["sha256"]:
        fail(errors, "Hero derivation source SHA does not match the frozen normalized V2 reference")
    for candidate in (hero_design_asset, hero_runtime_asset):
        if not candidate.is_file() or digest(candidate) != hero_contract["asset"]["sha256"]:
            fail(errors, "canonical design Hero and Android resource must match the frozen derived SHA")
    if hero_design_asset.read_bytes() != hero_runtime_asset.read_bytes():
        fail(errors, "Android Hero resource is not an exact copy of the canonical design asset")
    try:
        with hero_design_asset.open("rb") as png:
            header = png.read(29)
        width, height = struct.unpack(">II", header[16:24])
        color_type = header[25]
        expected_size = tuple(hero_contract["asset"]["dimensions_px"])
        if (width, height) != expected_size or color_type != 6:
            fail(errors, "canonical Hero asset must be the declared RGBA PNG")
    except (OSError, struct.error):
        fail(errors, "canonical Hero asset PNG header is invalid")
    hero_ratio = hero_contract["asset"]["dimensions_px"][0] / hero_contract["asset"]["dimensions_px"][1]
    layout_box = hero_contract["layout_reference_bbox_px"]
    fit_scale = min(layout_box["width"] / hero_contract["asset"]["dimensions_px"][0], layout_box["height"] / hero_contract["asset"]["dimensions_px"][1])
    rendered_ratio = (
        hero_contract["asset"]["dimensions_px"][0] * fit_scale
        / (hero_contract["asset"]["dimensions_px"][1] * fit_scale)
    )
    if hero_contract["asset"].get("render_content_scale") != "FIT" or abs(rendered_ratio / hero_ratio - 1.0) > 0.01:
        fail(errors, "Hero FIT rendering must preserve the source aspect ratio within one percent")

    layout_source = (ROOT / "app/src/main/java/com/yujian/ai/ui/home/EmptyHomeLayoutMapping.kt").read_text()
    home_screen = (ROOT / "app/src/main/java/com/yujian/ai/ui/screens/HomeScreen.kt").read_text()
    scene_entry = (ROOT / "app/src/main/java/com/yujian/ai/ui/home/HomeEmptyScene.kt").read_text()
    scene_renderer = (ROOT / "app/src/main/java/com/yujian/ai/ui/home/EmptyHomeSceneRenderer.kt").read_text()
    motion_source = (ROOT / "app/src/main/java/com/yujian/ai/ui/home/EmptyHomeMotion.kt").read_text()
    geometry_constants = {
        "REFERENCE_WIDTH_PX": anchors["reference_canvas"]["width"],
        "REFERENCE_HEIGHT_PX": anchors["reference_canvas"]["height"],
        "HERO_X_PX": anchors["hero_title"]["bbox_reference_px"]["x"],
        "HERO_Y_PX": anchors["hero_title"]["bbox_reference_px"]["y"],
        "HERO_WIDTH_PX": anchors["hero_title"]["bbox_reference_px"]["width"],
        "HERO_HEIGHT_PX": anchors["hero_title"]["bbox_reference_px"]["height"],
        "PROMPT_Y_PX": anchors["cta"]["prompt_top_reference_px"],
        "CAMERA_X_PX": anchors["camera_button"]["bbox_reference_px"]["x"],
        "CAMERA_Y_PX": anchors["camera_button"]["bbox_reference_px"]["y"],
        "CAMERA_SIZE_PX": anchors["cta"]["camera_size_reference_px"],
        "ALBUM_Y_PX": anchors["cta"]["album_top_reference_px"],
        "PROMPT_CAMERA_GAP_MIN_PX": responsive["groups"]["capture_cta_group"]["reference_geometry_px"]["prompt_to_camera_clear_gap_minimum"],
        "CAMERA_ALBUM_GAP_MIN_PX": responsive["groups"]["capture_cta_group"]["reference_geometry_px"]["camera_to_album_clear_gap_minimum"],
        "SAFE_HEADER_INSET_DP": responsive["groups"]["safe_top_ui"]["top_margin_after_safe_inset_dp"],
        "HEADER_MAX_WIDTH_DP": responsive["groups"]["safe_top_ui"]["maximum_content_width_dp"],
        "HEADER_MIN_TOUCH_TARGET_DP": responsive["groups"]["safe_top_ui"]["minimum_touch_target_dp"],
        "HERO_ASSET_WIDTH_PX": hero_contract["asset"]["dimensions_px"][0],
        "HERO_ASSET_HEIGHT_PX": hero_contract["asset"]["dimensions_px"][1],
    }
    for name, expected in geometry_constants.items():
        match = re.search(rf"const val {name} = ([0-9.]+)f", layout_source)
        if match is None or float(match.group(1)) != float(expected):
            fail(errors, f"EmptyHomeLayoutMapping.{name} diverges from the canonical machine contract")
    if re.search(r"\bscaleX\b|\bscaleY\b|\brefX\s*\(|\brefY\s*\(", home_screen):
        fail(errors, "HomeScreen still contains independent X/Y reference scaling")
    if "ContentScale.FillBounds" in home_screen:
        fail(errors, "Empty Home Hero may not use ContentScale.FillBounds")
    if "ContentScale.Fit" not in home_screen or "painterResource(R.drawable.empty_home_title_v2)" not in home_screen:
        fail(errors, "HomeScreen must render the canonical Hero resource with ContentScale.Fit")
    gradle = (ROOT / "app/build.gradle.kts").read_text()
    if "generateEmptyHomeFrozenHero" in gradle or "emptyHomeGeneratedResDir" in gradle or "emptyHomeFrozenHeroSource" in gradle:
        fail(errors, "Gradle may not regenerate the frozen Empty Home Hero at build time")
    if "sceneTransform = layoutMapping.sceneTransform" not in scene_entry:
        fail(errors, "HomeEmptyScene does not pass its single sceneTransform to the renderer")
    if "bitmap = assets.sceneBase" not in scene_renderer or "transform = transform" not in scene_renderer:
        fail(errors, "scene base and scene-locked overlays must share sceneTransform")
    if "protectedMaxOffsetX" not in motion_source or "coerceIn(protectedMinOffsetX, protectedMaxOffsetX)" not in motion_source:
        fail(errors, "scene transform does not clamp Cover alignment to the Fishing Protected Bounds")
    if "offsetX = (containerWidthPx - REFERENCE_SCENE_WIDTH * scale) / 2f" in motion_source:
        fail(errors, "centered Cover may crop the left-side rod and is not permitted")
    if "MistBlueGray" in scene_renderer:
        fail(errors, "Empty Home Scene may not add an unauthorized blue-gray grade")
    source = load(FEATURE / "source/provenance/source_manifest.json")
    production = load(FEATURE / "intermediate/validation/asset_production_report.json")
    asset_manifest = load(FEATURE / "shared/contracts/asset_manifest.json")
    v22_authority = FEATURE / "source/frozen/Empty_Home_Frozen_Visual_V2_2.png"
    if not v22_authority.is_file() or digest(v22_authority) != "3071481ed7e58106381cdd5321267792491c21fd1a357e4362db1dad8e08e7ec":
        fail(errors, "canonical V2.2 Fishing Composition authority SHA mismatch")
    if authority.get("reference_canvas", {}).get("approved_delta_artwork", {}).get("repository_copy") != "design/pages/home/empty_home/source/frozen/Empty_Home_Frozen_Visual_V2_2.png":
        fail(errors, "authority manifest must use the committed V2.2 visual file")

    if runtime.get("design_version") != "Empty_Home_Final_Design_V2":
        fail(errors, "runtime manifest does not name Frozen V2")
    if runtime.get("reference_canvas") != [1080, 1920]:
        fail(errors, "runtime reference canvas must be [1080, 1920]")
    if runtime.get("visual_revision") != "V2.2":
        fail(errors, "runtime visual revision must be the measured V2.2 Fishing Composition")
    contract_hashes = runtime.get("contract_sha256", {})
    for name in runtime.get("current_contracts", []):
        packaged = RUNTIME / "config" / name
        if contract_hashes.get(name) != digest(packaged):
            fail(errors, "runtime manifest contract SHA256 mismatch: " + name)
    if runtime.get("approved_visual_sha256") != "3071481ed7e58106381cdd5321267792491c21fd1a357e4362db1dad8e08e7ec":
        fail(errors, "runtime approved visual SHA mismatch")
    for relative_path, expected_sha in runtime.get("sha256", {}).items():
        candidate = RUNTIME / relative_path
        if not candidate.is_file() or digest(candidate) != expected_sha:
            fail(errors, "runtime asset SHA256 mismatch: " + relative_path)
    if layers.get("order") != ["scene_base", "cloud_atmosphere", "sun_ambient", "rod", "line", "bobber_reflection", "water_contact_occlusion", "ripple", "bobber_body", "foreground_occlusion", "native_ui", "capture_action"]:
        fail(errors, "layer order does not preserve V2.2 fishing registration and separate reflection")
    if layers.get("rules", {}).get("ripple_count") != 1 or layers.get("rules", {}).get("scene_base_has_baked_ripple") is not False:
        fail(errors, "single-ripple/no-baked-ripple rule failed")
    if layers.get("rules", {}).get("scene_transform") != "UNIFORM_COVER_COMPOSITION_AWARE" or layers.get("rules", {}).get("bobber_is_split_at_water_contact") is not True:
        fail(errors, "scene transform and bobber water-contact split rules are missing")
    if anchors["bobber"]["water_contact_reference_px"] != [561, 1323]:
        fail(errors, "bobber contact must match the measured V2.2 authority")
    if anchors["ripple"]["center_reference_px"] != [562, 1320]:
        fail(errors, "ripple center must match the measured V2.2 authority")
    if anchors["bobber"]["bbox_reference_px"] != {"x": 550, "y": 1250, "width": 24, "height": 78}:
        fail(errors, "V2.2 bobber body origin/size mismatch")
    if layers.get("rules", {}).get("bobber_water_treatment") != "single_body_plus_separate_faded_reflection":
        fail(errors, "separate V2.2 reflection treatment contract missing")
    if anchors.get("visual_revision") != "V2.2" or anchors.get("authority_status") != "CURRENT":
        fail(errors, "anchor contract is not the current measured V2.2 authority")
    if anchors.get("hero_title", {}).get("bbox_reference_px") != {"x": 50, "y": 224, "width": 620, "height": 310}:
        fail(errors, "Hero anchor differs from the frozen normalized reference contract")
    if anchors.get("rod", {}).get("asset_origin_reference_px") != [0, 1180]:
        fail(errors, "V2.2 rod asset origin mismatch")
    if anchors.get("rod", {}).get("tip_reference_px") != [337, 1184]:
        fail(errors, "V2.2 rod tip mismatch")
    if anchors.get("line", {}).get("control_points_reference_px") != [[389, 1257], [471, 1312]]:
        fail(errors, "V2.2 measured line fit control points mismatch")
    line = anchors.get("line", {})
    anchor_values = {
        "EMPTY_HOME_V2_ROD_X": anchors["rod"]["bbox_reference_px"]["x"],
        "EMPTY_HOME_V2_ROD_Y": anchors["rod"]["bbox_reference_px"]["y"],
        "EMPTY_HOME_V2_ROD_TIP_X": anchors["rod"]["tip_reference_px"][0],
        "EMPTY_HOME_V2_ROD_TIP_Y": anchors["rod"]["tip_reference_px"][1],
        "EMPTY_HOME_V2_LINE_START_X": line["start_reference_px"][0],
        "EMPTY_HOME_V2_LINE_START_Y": line["start_reference_px"][1],
        "EMPTY_HOME_V2_LINE_C1_X": line["control_points_reference_px"][0][0],
        "EMPTY_HOME_V2_LINE_C1_Y": line["control_points_reference_px"][0][1],
        "EMPTY_HOME_V2_LINE_C2_X": line["control_points_reference_px"][1][0],
        "EMPTY_HOME_V2_LINE_C2_Y": line["control_points_reference_px"][1][1],
        "EMPTY_HOME_V2_LINE_END_X": line["end_reference_px"][0],
        "EMPTY_HOME_V2_LINE_END_Y": line["end_reference_px"][1],
        "EMPTY_HOME_V2_BOBBER_X": anchors["bobber"]["bbox_reference_px"]["x"],
        "EMPTY_HOME_V2_BOBBER_Y": anchors["bobber"]["bbox_reference_px"]["y"],
        "EMPTY_HOME_V2_RIPPLE_X": anchors["ripple"]["bbox_reference_px"]["x"],
        "EMPTY_HOME_V2_RIPPLE_Y": anchors["ripple"]["bbox_reference_px"]["y"],
        "EMPTY_HOME_V2_WATER_CONTACT_X": anchors["bobber"]["water_contact_reference_px"][0],
        "EMPTY_HOME_V2_WATER_CONTACT_Y": anchors["bobber"]["water_contact_reference_px"][1],
    }
    for name, expected in anchor_values.items():
        match = re.search(rf"const val {name} = (-?[0-9.]+)f", motion_source)
        if match is None or float(match.group(1)) != float(expected):
            fail(errors, f"EmptyHomeMotion.{name} diverges from the canonical V2.2 measured anchor contract")
    if anchors.get("cta", {}).get("camera_size_reference_px") != 220:
        fail(errors, "V2.2 Camera size mismatch")
    if anchors["rod"]["tip_reference_px"] != [337, 1184]:
        fail(errors, "V2.2 rod tip mismatch")
    if line.get("start_reference_px") != anchors["rod"]["tip_reference_px"]:
        fail(errors, "V2.2 fishing line must start at rod tip")
    if line.get("control_points_reference_px") != [[389, 1257], [471, 1312]]:
        fail(errors, "V2.2 fishing line fit control points mismatch")
    if line.get("end_reference_px") != [560, 1326]:
        fail(errors, "V2.2 fishing line endpoint must enter the bobber contact occlusion")
    contact = anchors["bobber"]["water_contact_reference_px"]
    bobber_bounds = anchors["bobber"]["bbox_reference_px"]
    if not (bobber_bounds["x"] <= line["end_reference_px"][0] <= bobber_bounds["x"] + bobber_bounds["width"] and contact[1] <= line["end_reference_px"][1] <= contact[1] + 4):
        fail(errors, "line endpoint is not inside the bobber water-contact occlusion region")
    if scene_renderer.count("cubicTo(") != 1:
        fail(errors, "fishing line renderer must contain exactly one cubic Bézier")
    fishing_layers = scene_renderer[scene_renderer.index("drawFrozenFishingLine(transform)"):]
    layer_markers = (
        "bitmap = assets.bobberReflection",
        "bitmap = assets.ripple",
        "source = Rect(0, 0, assets.bobber.width, bobberSplit.splitY)",
    )
    layer_positions = [fishing_layers.find(marker) for marker in layer_markers]
    if any(position < 0 for position in layer_positions) or layer_positions != sorted(layer_positions):
        fail(errors, "bobber/reflection/contact/ripple ordering does not match V2.2 compositing")
    if scene_renderer.count("bitmap = assets.bobber,") != 1 or "bitmap = assets.bobberReflection" not in scene_renderer:
        fail(errors, "bobber body must render once and reflection must use its own asset")
    if "source = Rect(0, bobberSplit.splitY" in scene_renderer or "underwaterHeight.toFloat()" in scene_renderer:
        fail(errors, "Runtime must not render a second submerged bobber silhouette")
    if layers.get("rules", {}).get("bobber_underwater_visible_height_reference_px") != 0 or layers.get("rules", {}).get("bobber_underwater_alpha") != 0:
        fail(errors, "V2.2 reflection must remain separate from the single bobber body")
    if "waterContactY - bobberTopY" not in scene_renderer:
        fail(errors, "bobber source split must be derived from the fixed water-contact coordinate")
    line_renderer = scene_renderer[scene_renderer.index("private fun DrawScope.drawFrozenFishingLine"):scene_renderer.index("private fun DrawScope.drawSunParticles")]
    if line_renderer.count("drawPath(") != 1 or "DeepInk" in line_renderer or "Stroke(width = 2.25f" in line_renderer:
        fail(errors, "Fishing line must use one authority-matched pale stroke without a dark outline")
    reflection_asset = RUNTIME / "dynamic/bobber_reflection.png"
    body_asset = RUNTIME / "dynamic/bobber.png"
    if reflection_asset.read_bytes() == body_asset.read_bytes():
        fail(errors, "bobber reflection must be a distinct optical asset")
    for asset_path, expected in ((RUNTIME / "dynamic/rod.png", (340, 206)), (body_asset, (24, 78)), (reflection_asset, (12, 52))):
        try:
            if png_dimensions(asset_path) != expected:
                fail(errors, "fishing layer source dimensions mismatch: " + str(asset_path.relative_to(RUNTIME)))
        except (OSError, AssertionError):
            fail(errors, "invalid fishing layer PNG: " + str(asset_path.relative_to(RUNTIME)))
    cta = anchors.get("cta", {})
    if (
        cta.get("prompt_top_reference_px"),
        cta.get("camera_top_reference_px"),
        cta.get("camera_size_reference_px"),
        cta.get("album_top_reference_px"),
    ) != (1448, 1537, 220, 1780):
        fail(errors, "V2.2 CTA layout/spacing contract mismatch")
    bobber = motion.get("bobber", {})
    if (bobber.get("axis"), bobber.get("range_reference_px"), bobber.get("duration_ms"), bobber.get("rotation_deg"), bobber.get("scale_animation")) != ("y", 3, 4600, 0, False):
        fail(errors, "bobber Motion V2 contract failed")
    if bobber.get("keyframes") != [[0, 0], [1150, -3], [2300, 0], [3450, 3], [4600, 0]]:
        fail(errors, "bobber keyframes differ from V2")
    ripple = motion.get("ripple", {})
    if (ripple.get("count"), ripple.get("scale_from"), ripple.get("scale_to"), ripple.get("alpha_from"), ripple.get("alpha_to"), ripple.get("duration_ms")) != (1, 1.0, 1.22, 0.30, 0.0, 3200):
        fail(errors, "ripple Motion V2 contract failed")
    if motion.get("cloud", {}).get("speed_reference_px_per_s") != 0.2:
        fail(errors, "cloud speed must be 0.2 reference px/s")
    beam = motion.get("sun_particle_beam", {})
    if beam.get("duration_ms") != 4800 or beam.get("max_alpha", 1) > .18 or beam.get("particle_count_range") != [6, 12]:
        fail(errors, "sun particle beam contract failed")
    rim = motion.get("camera_gold_rim", {})
    if (rim.get("first_delay_ms"), rim.get("duration_ms"), rim.get("repeat_interval_ms")) != (3000, 1400, 9000):
        fail(errors, "camera gold rim contract failed")
    if motion.get("camera_breath", {}).get("max_scale", 2) > 1.015:
        fail(errors, "camera breath max scale exceeds V2")
    if source["frozen_input"]["sha256"] != digest(FEATURE / "source/frozen/Empty_Home_Final_Design_V2.png"):
        fail(errors, "frozen source SHA256 mismatch")
    visual_revision = source.get("active_visual_revision", {})
    if visual_revision.get("revision") != "V2.2" or visual_revision.get("status") != "APPROVED_FROZEN":
        fail(errors, "V2.2 approved visual revision metadata missing")
    if visual_revision.get("sha256") != "3071481ed7e58106381cdd5321267792491c21fd1a357e4362db1dad8e08e7ec":
        fail(errors, "V2.2 approved visual source SHA mismatch")
    if production.get("status") != "PASS":
        fail(errors, "asset production/static recompose gate is not PASS")
    for asset in asset_manifest.get("assets", []):
        asset_path = ROOT / asset["path"]
        if not asset_path.is_file() or asset.get("sha256") != digest(asset_path):
            fail(errors, "shared asset SHA256 mismatch: " + asset.get("asset_id", "unknown"))

    report.update({
        "status": "PASS" if not errors else "FAIL",
        "design_version": runtime.get("design_version"),
        "visual_revision": runtime.get("visual_revision"),
        "reference_canvas": runtime.get("reference_canvas"),
        "asset_count": len(asset_manifest.get("assets", [])),
        "runtime_file_count": len([path for path in RUNTIME.rglob("*") if path.is_file()]),
        "asset_production": production.get("status"),
    })
    print(json.dumps(report, indent=2, ensure_ascii=False))
    return 0 if not errors else 1


if __name__ == "__main__":
    sys.exit(main())
