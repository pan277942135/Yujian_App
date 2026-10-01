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
    "dynamic/rod.png", "dynamic/line.png", "dynamic/bobber.png", "dynamic/ripple_mask.png",
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
        fail(errors, "authority manifest must name V2 / V2.2")
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
    if scene_mapping.get("strategy") != "UNIFORM_COVER" or scene_mapping.get("all_scene_locked_elements_share_transform") is not True:
        fail(errors, "scene elements must share one UNIFORM_COVER sceneTransform")
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
        fail(errors, "Hero asset contract is not the current V2.2 authority")
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
    if "MistBlueGray" in scene_renderer:
        fail(errors, "Empty Home Scene may not add an unauthorized blue-gray grade")
    source = load(FEATURE / "source/provenance/source_manifest.json")
    production = load(FEATURE / "intermediate/validation/asset_production_report.json")
    asset_manifest = load(FEATURE / "shared/contracts/asset_manifest.json")

    if runtime.get("design_version") != "Empty_Home_Final_Design_V2":
        fail(errors, "runtime manifest does not name Frozen V2")
    if runtime.get("reference_canvas") != [1080, 1920]:
        fail(errors, "runtime reference canvas must be [1080, 1920]")
    if runtime.get("visual_revision") != "V2.2":
        fail(errors, "runtime visual revision must be V2.2")
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
    if layers.get("order") != ["scene_base", "cloud_atmosphere", "sun_ambient", "rod", "line", "bobber_underwater", "water_contact_occlusion", "ripple", "bobber_above_water", "foreground_occlusion", "native_ui", "capture_action"]:
        fail(errors, "layer order does not preserve the V2.2 water-contact compositing order")
    if layers.get("rules", {}).get("ripple_count") != 1 or layers.get("rules", {}).get("scene_base_has_baked_ripple") is not False:
        fail(errors, "single-ripple/no-baked-ripple rule failed")
    if layers.get("rules", {}).get("scene_transform") != "UNIFORM_COVER" or layers.get("rules", {}).get("bobber_is_split_at_water_contact") is not True:
        fail(errors, "scene transform and bobber water-contact split rules are missing")
    if anchors["ripple"]["center_reference_px"] != anchors["bobber"]["water_contact_reference_px"]:
        fail(errors, "ripple center must equal bobber water contact")
    if anchors["bobber"]["water_contact_reference_px"] != [560, 1320]:
        fail(errors, "V2.2 bobber water contact mismatch")
    if anchors.get("visual_revision") != "V2.2" or anchors.get("authority_status") != "CURRENT":
        fail(errors, "anchor contract is not the current V2.2 authority")
    if anchors.get("hero_title", {}).get("bbox_reference_px") != {"x": 50, "y": 224, "width": 620, "height": 310}:
        fail(errors, "Hero anchor differs from the frozen normalized reference contract")
    if anchors.get("rod", {}).get("bbox_reference_px", {}).get("x") != -96 or anchors.get("rod", {}).get("bbox_reference_px", {}).get("y") != 1172:
        fail(errors, "V2.2 rod origin mismatch")
    if anchors.get("rod", {}).get("tip_reference_px") != [335, 1180]:
        fail(errors, "V2.2 rod tip mismatch")
    if anchors.get("line", {}).get("control_points_reference_px") != [[390, 1265], [470, 1352]]:
        fail(errors, "V2.2 single cubic line control points mismatch")
    if anchors.get("bobber", {}).get("bbox_reference_px") != {"x": 548, "y": 1236, "width": 24, "height": 122}:
        fail(errors, "V2.2 bobber geometry mismatch")
    if anchors.get("cta", {}).get("camera_size_reference_px") != 220:
        fail(errors, "V2.2 Camera size mismatch")
    if anchors["rod"]["tip_reference_px"] != [335, 1180]:
        fail(errors, "V2.2 rod tip mismatch")
    line = anchors.get("line", {})
    if line.get("start_reference_px") != [335, 1180]:
        fail(errors, "V2.2 fishing line must start at rod tip")
    if line.get("control_points_reference_px") != [[390, 1265], [470, 1352]]:
        fail(errors, "V2.2 fishing line slack control points mismatch")
    if line.get("end_reference_px") != [560, 1328]:
        fail(errors, "V2.2 fishing line must terminate below the bobber water seam")
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
