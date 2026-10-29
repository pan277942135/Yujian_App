#!/usr/bin/env python3
"""Validate Phase 0 SWOS, Processing timing, continuity and RR09 V3 consistency."""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FILES = {
    "swos": "design/pages/recognition/processing/optics/SWOS_Optical_Contract_V1.json",
    "processing_motion": "design/pages/recognition/processing/motion/recognition_processing_motion_contract_v1_3.json",
    "processing_state": "design/system_v3/08_Page_Contracts/recognition/processing/State_Machine_V3_2.json",
    "processing_acceptance": "design/system_v3/08_Page_Contracts/recognition/processing/Acceptance_Contract_V3_2.json",
    "continuity": "design/pages/recognition/continuity/processing_result_optical_continuity_v1.json",
    "android_transition": "design/pages/recognition/continuity/android_rr09_shared_photo_transition_final_v1.json",
    "rr09": "design/pages/recognition/result/motion/recognition_result_motion_contract_v3.json",
}


def load_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def consistency_errors(contracts: dict) -> list[str]:
    errors: list[str] = []
    swos = contracts["swos"]
    motion = contracts["processing_motion"]
    state = contracts["processing_state"]
    acceptance = contracts["processing_acceptance"]
    continuity = contracts["continuity"]
    android_transition = contracts["android_transition"]
    rr09 = contracts["rr09"]

    expected_durations = [350, 600, 600, 1250]
    expected_boundaries = [0, 350, 950, 1550, 2800]
    expected_phases = ["CAPTURED", "DETECTING", "OUTLINE", "CLASSIFYING"]
    phases = motion.get("phases", [])
    if [p.get("duration_ms") for p in phases] != expected_durations:
        errors.append("Processing motion durations must be 350/600/600/1250ms")
    if motion.get("phase_boundaries_ms") != expected_boundaries:
        errors.append("Processing motion boundaries must be 0/350/950/1550/2800ms")
    if [p.get("id") for p in phases] != expected_phases:
        errors.append("Processing internal phases must remain CAPTURED/DETECTING/OUTLINE/CLASSIFYING")
    if motion.get("visual_total_minimum_ms") != 2800:
        errors.append("Processing minimum visual duration must be 2800ms")
    if motion.get("processing_release_subphase") != {
        "start_ms": 2600, "end_ms": 2800, "business_state_added": False,
        "peripheral_water_end_strength": 0, "trusted_fish_gold_trace_retained": True,
    }:
        errors.append("Processing release must remain 2600–2800ms inside CLASSIFYING")

    states = state.get("states", [])
    phase_states = [s for s in states if s.get("id") != "RESULT_READY"]
    if [s.get("id") for s in phase_states] != expected_phases:
        errors.append("Page State Machine V3.2 phase list does not match motion authority")
    if [s.get("duration_ms") for s in phase_states] != expected_durations:
        errors.append("Page State Machine V3.2 durations drift from Processing motion authority")
    if state.get("minimum_visual_timeline", {}).get("boundaries_ms") != expected_boundaries:
        errors.append("Page State Machine V3.2 boundaries drift from Processing motion authority")
    if any(s.get("id") == "RESOLVE" for s in states):
        errors.append("Processing must not add a RESOLVE business state")

    if continuity.get("processing_minimum_ms") != 2800 or continuity.get("result_reveal_ms") != 850:
        errors.append("Continuity durations must be Processing 2800ms plus RR09 850ms")
    if continuity.get("nominal_combined_visual_ms") != 3650:
        errors.append("Nominal combined visual duration must be 3650ms")
    if continuity.get("nominal_is_inference_deadline") is not False:
        errors.append("3650ms must not become an inference deadline")
    nav = continuity.get("navigation", {})
    if nav.get("additional_pre_navigation_wait_ms") != 0:
        errors.append("Processing-to-Result navigation must not add a pre-navigation wait")
    reveal = continuity.get("rr09", {})
    if reveal.get("t0") != "FIRST_ACTUALLY_PRESENTED_RESULT_FRAME":
        errors.append("RR09 T0 must be the first actually presented Result frame")
    if reveal.get("photo_continuity_ms") != [0, 200] or reveal.get("photo_continuity_is_pre_navigation_wait") is not False:
        errors.append("RR09 0–200ms must be post-navigation photo continuity")

    expected_rr09 = [
        (0, 200, 200, "PHOTO_CONTINUITY"),
        (200, 380, 180, "GOLD_TRANSFER_TO_CHINESE_TITLE"),
        (380, 550, 170, "CHINESE_GLYPH_FORMATION"),
        (550, 680, 130, "CHINESE_NAME_REVEAL"),
        (680, 850, 170, "AFTERGLOW_FADE_TO_STATIC_RESULT"),
    ]
    actual_rr09 = [
        (p.get("start_ms"), p.get("end_ms"), p.get("duration_ms"), p.get("phase"))
        for p in rr09.get("timeline", [])
    ]
    if actual_rr09 != expected_rr09 or rr09.get("duration_ms") != 850:
        errors.append("RR09 V3 timeline must match the five approved boundaries and 850ms total")
    title = rr09.get("title", {})
    if title.get("species_title_language") != "CHINESE_ONLY" or title.get("show_english_scientific_name") is not False or title.get("reserve_empty_english_row") is not False:
        errors.append("RR09 V3 title must be Chinese-only with no English row")
    if rr09.get("eligibility", {}).get("result_state") != "RESULT_HIGH":
        errors.append("RR09 V3 must remain HIGH-only")
    interaction = rr09.get("interaction", {})
    if any(interaction.get(key) is not False for key in ("cta_blocked", "save_blocked", "edit_blocked", "back_blocked")):
        errors.append("RR09 V3 motion must not block Result actions")

    # Android shared-photo implementation binding: preserve the existing V1 cross-page design authority,
    # while making both Hero placement modes and the midpoint rule machine-checkable.
    if android_transition.get("contract_id") != "YUJIAN-ANDROID-RR09-SHARED-PHOTO-TRANSITION-V1":
        errors.append("Android RR09 shared-photo contract identity drift")
    if android_transition.get("status") != "FROZEN_DESIGN_DIRECTION" or android_transition.get("runtime_status") != "NOT_RUN":
        errors.append("Android RR09 contract must remain design-only until exact-head runtime evidence exists")
    if android_transition.get("canonicality") != "MAIN_AUTHORITY_ONLY_AFTER_MERGE":
        errors.append("Android RR09 contract must not claim canonicality before Main adoption")
    android_timeline = android_transition.get("timeline", {})
    if android_timeline.get("processing_minimum_ms") != 2800 or android_timeline.get("rr09_duration_ms") != 850 or android_timeline.get("nominal_combined_visual_ms") != 3650:
        errors.append("Android RR09 combined timeline must remain 2800ms + 850ms = 3650ms")
    if android_timeline.get("nominal_combined_visual_is_inference_deadline") is not False:
        errors.append("Android RR09 nominal visual duration must not become an inference deadline")
    photo = android_timeline.get("photo_continuity", {})
    interpolation = photo.get("interpolation", {})
    if photo.get("start_ms") != 0 or photo.get("end_ms") != 200 or photo.get("duration_ms") != 200:
        errors.append("Android photo continuity must remain the post-navigation 0–200ms segment")
    if interpolation.get("formula") != "q(u) = u*u*(3-2*u)" or interpolation.get("exact_midpoint_required") is not True:
        errors.append("Android photo interpolation must use symmetric smoothstep with exact R0.5 midpoint")
    if interpolation.get("samples") != [{"elapsed_ms": 0, "q": 0}, {"elapsed_ms": 100, "q": 0.5}, {"elapsed_ms": 200, "q": 1}]:
        errors.append("Android photo interpolation samples must be q(0)=0, q(100)=0.5 and q(200)=1")
    modes = android_transition.get("photo_placement", {}).get("modes", {})
    if set(modes) != {"BBOX_ANCHORED_SAFE_CROP", "FULL_SOURCE_FIT_SAME_SOURCE_AMBIENT"}:
        errors.append("Android Hero placement must expose exactly the two approved photo modes")
    safe_crop = modes.get("BBOX_ANCHORED_SAFE_CROP", {})
    expected_profiles = [{"horizontal_fraction": 0.1, "vertical_fraction": 0.12}, {"horizontal_fraction": 0.08, "vertical_fraction": 0.08}, {"horizontal_fraction": 0.04, "vertical_fraction": 0.04}, {"horizontal_fraction": 0, "vertical_fraction": 0}]
    if safe_crop.get("padding_profiles") != expected_profiles or safe_crop.get("crop_at_r1_matches_hero") is not True:
        errors.append("BBox safe-crop profiles or R1 Hero transform parity drifted")
    fallback = modes.get("FULL_SOURCE_FIT_SAME_SOURCE_AMBIENT", {})
    eligibility = android_transition.get("reveal_eligibility", {})
    if fallback.get("source_rect") != [0, 0, 1, 1] or fallback.get("foreground_fit") != "CONTAIN" or fallback.get("preserve_source_aspect_ratio") is not True or fallback.get("generated_fill") is not False:
        errors.append("Full-source fallback must preserve the complete oriented source with FIT and no generated fill")
    samples = fallback.get("reference_samples", {})
    if samples.get("R0", {}).get("outer_rect") != {"x": 0, "y": 0, "width": 360, "height": 640} or samples.get("R0", {}).get("foreground_rect") != {"x": 0, "y": 80, "width": 360, "height": 480}:
        errors.append("Mode B R0 start geometry must match the 360x640 Processing source sample")
    if samples.get("R0_5", {}).get("elapsed_ms") != 100 or samples.get("R0_5", {}).get("outer_rect") != {"x": 9.5, "y": 26.5, "width": 341, "height": 425} or samples.get("R0_5", {}).get("foreground_rect") != {"x": 50.625, "y": 66.5, "width": 258.75, "height": 345}:
        errors.append("Mode B R0.5 reference geometry must remain the exact 360x640 midpoint sample")
    if samples.get("R1", {}).get("outer_rect") != {"x": 19, "y": 53, "width": 322, "height": 210} or samples.get("R1", {}).get("foreground_rect") != {"x": 101.25, "y": 53, "width": 157.5, "height": 210}:
        errors.append("Mode B R1 geometry must match the actual 360x640 Hero target sample")
    phases = android_timeline.get("rr09_phases", [])
    if [(p.get("start_ms"), p.get("end_ms")) for p in phases] != [(0, 200), (200, 380), (380, 550), (550, 680), (680, 850)]:
        errors.append("Android RR09 phase boundaries must match Motion V3")
    if eligibility.get("invalid_photo_geometry", {}).get("transition") != "STATIC_RESULT" or eligibility.get("invalid_photo_geometry", {}).get("rr09_timeline") is not False:
        errors.append("Invalid photo identity/transform must fail closed to static Result without RR09")
    if android_transition.get("result_entry", {}).get("english_scientific_name_visible") is not False or android_transition.get("result_entry", {}).get("empty_english_title_row_reserved") is not False:
        errors.append("Android RR09 title must remain Chinese-only with no English row")
    if android_transition.get("result_entry", {}).get("non_title_content_entry") != "EXISTING_STATIC_RESULT_LAYOUT_AT_FINAL_BOUNDS_NO_ADDITIONAL_ALPHA_POSITION_OR_SCALE_ANIMATION":
        errors.append("Android RR09 must preserve the existing static Result content layout without extra entry motion")
    if android_transition.get("result_entry", {}).get("controls_enabled_from_t0") is not True or android_transition.get("invariants", {}).get("additional_pre_navigation_wait_ms") != 0:
        errors.append("Android transition must keep Result actions active and add no pre-navigation wait")
    if photo.get("eligible_result_states") != ["RESULT_HIGH"] or android_transition.get("invariants", {}).get("rr09_animation_high_only") is not True:
        errors.append("RR09 animation including photo continuity must be eligible only for new HIGH results with valid identity")
    for state_key in ("result_medium_or_low", "no_fish_or_image_quality"):
        if eligibility.get(state_key, {}).get("photo_transition") != "NONE_STATIC_RESULT":
            errors.append(f"RR09 {state_key} must use static Result presentation without a photo transition")
    if android_transition.get("invariants", {}).get("source_sha256_explicit_and_propagated") is not True:
        errors.append("Android transition must explicitly propagate normalized source SHA-256 through the handoff")
    if "photo_sha256_propagated_through_session_handoff" not in android_transition.get("acceptance", {}).get("required_runtime_evidence", []):
        errors.append("Android transition runtime evidence must prove source SHA-256 continuity")
    gap_ids = [item.get("id") for item in android_transition.get("implementation_gap_register", [])]
    if gap_ids != [f"G{i:02d}" for i in range(1, 12)]:
        errors.append("Android transition implementation gap register must contain G01–G11 in order")

    if swos.get("contract_id") != "YUJIAN-SWOS-OPTICAL-V1":
        errors.append("SWOS optical contract identity drift")
    layer_ids = [layer.get("id") for layer in swos.get("layers", [])]
    if layer_ids != ["photo_base", "ambient_water", "caustic_field", "ripple_field", "peripheral_refraction", "fish_focus", "residual_gold_trace"]:
        errors.append("SWOS optical layer responsibilities are missing or out of order")
    if swos.get("legacy_edge_field", {}).get("seven_path_geometry_is_swos_basis") is not False:
        errors.append("Legacy seven-path Edge Field must not be identified as the SWOS basis")
    if swos.get("coordinate_contract", {}).get("clip_rect") != "photoContentRect":
        errors.append("SWOS layers must use photoContentRect")
    if acceptance.get("stage1_android_api28_gate") != "NOT_RUN" or acceptance.get("stage1_browser_visual_gate") != "NOT_RUN":
        errors.append("Phase 0 acceptance must not claim Stage 1 runtime or visual gates")
    return errors


def authority_reference_errors(registry: dict, result_navigation: dict, active_registry: str) -> list[str]:
    """Keep Design Manager's main-adopted design routes aligned with their authorities."""
    errors: list[str] = []

    def find_hifi_view(feature_id: str, view_id: str):
        feature = next((item for item in registry.get("features", []) if item.get("id") == feature_id), None)
        if feature is None:
            return None
        return next((item for item in feature.get("hifi_views", []) if item.get("id") == view_id), None)

    motion = find_hifi_view("recognition_flow_v1_2", "motion_transition")
    if motion is None:
        errors.append("Design Manager must expose the Processing motion authority route")
    else:
        if motion.get("authority") != "design/pages/recognition/processing/motion/Recognition_Processing_Motion_Spec_V1_3.md":
            errors.append("Processing motion route must point to main-adopted Motion V1.3")
        if motion.get("secondary_authority") != "design/pages/recognition/processing/optics/SWOS_Optical_Contract_V1.md":
            errors.append("Processing motion route must use SWOS V1, not the legacy Edge Field motion contract")
        model = motion.get("motion_model", {})
        if model.get("phase_boundaries_ms") != [0, 350, 950, 1550, 2800]:
            errors.append("Design Manager Processing motion boundaries must match 0/350/950/1550/2800ms")
        if model.get("minimum_is_inference_deadline") is not False:
            errors.append("Design Manager must not represent the 2800ms visual minimum as an inference deadline")
        points = " ".join(motion.get("menu_points", []))
        if "900ms" in points or "RESOLVE 200ms" in points or "Segment Speed" in points:
            errors.append("Current SWOS Processing menu must not retain V1.2 Edge Field timing/parameter copy")
        legacy_child = next((item for item in motion.get("children", []) if item.get("id") == "ai_edge_field_v1_motion_contract"), None)
        if legacy_child is None or "historical" not in legacy_child.get("summary", "").lower() or legacy_child.get("scope") != "HISTORICAL_EDGE_FIELD_ONLY_NOT_SWOS":
            errors.append("Legacy Edge Field motion contract must be explicitly scoped as historical, outside SWOS")

    rr09 = find_hifi_view("recognition_result_v1", "motion_continuation")
    rr09_history = find_hifi_view("recognition_result_v1", "motion_continuation_v2_history")
    if rr09 is None or rr09.get("authority") != "design/pages/recognition/result/motion/Recognition_Result_Motion_Spec_V3.md":
        errors.append("Current RR09 menu route must point to V3")
    else:
        if rr09.get("secondary_authority") != "design/system_v3/10_Page_Visual_Acceptance_Contracts/Recognition_Result/Recognition_Result_Motion_Contract_V3.md":
            errors.append("Current RR09 route must link to the V3 system contract")
        reveal_points = " ".join(rr09.get("menu_points", []))
        if "英文名紧随" in reveal_points or "English scientific name reveal" in reveal_points:
            errors.append("Current RR09 V3 menu must not promise an English scientific-name reveal")
    if rr09_history is None or rr09_history.get("status") != "DEPRECATED" or rr09_history.get("authority") != "design/pages/recognition/result/motion/Recognition_Result_Motion_Spec_V2.md":
        errors.append("RR09 V2 must remain separately reachable as a deprecated historical baseline")

    rr09_entries = [item for item in result_navigation.get("level_2", []) if item.get("id") in {"RR09", "RR09_V2_HISTORY"}]
    rr09_current = next((item for item in rr09_entries if item.get("id") == "RR09"), None)
    rr09_v2 = next((item for item in rr09_entries if item.get("id") == "RR09_V2_HISTORY"), None)
    if rr09_current is None or rr09_current.get("contract") != "design/pages/recognition/result/motion/Recognition_Result_Motion_Spec_V3.md":
        errors.append("Result navigation must identify RR09 V3 as the main-adopted design-only contract")
    if rr09_v2 is None or rr09_v2.get("status") != "DEPRECATED" or rr09_v2.get("contract") != "design/pages/recognition/result/motion/Recognition_Result_Motion_Spec_V2.md":
        errors.append("Result navigation must retain RR09 V2 as a deprecated historical contract")

    if "Main-adopted Phase 0 design authority set — PR #190" not in active_registry or "0 / 350 / 950 / 1550 / 2800ms" not in active_registry:
        errors.append("Active Source Registry must identify the main-adopted SWOS design authority set")
    return errors


def validate(root: Path) -> list[str]:
    contracts = {}
    errors = []
    for key, relative in FILES.items():
        path = root / relative
        if not path.is_file():
            errors.append(f"missing {key} contract: {relative}")
            continue
        try:
            contracts[key] = load_json(path)
        except (OSError, json.JSONDecodeError) as exc:
            errors.append(f"invalid {key} JSON {relative}: {exc}")
    if not errors:
        errors.extend(consistency_errors(contracts))
    try:
        registry = load_json(root / "design/registry/experience_registry_v1.json")
        result_navigation = load_json(root / "design/pages/recognition/result/navigation.json")
        active_registry = (root / "docs/ACTIVE_SOURCE_REGISTRY.md").read_text(encoding="utf-8")
        errors.extend(authority_reference_errors(registry, result_navigation, active_registry))
    except (OSError, json.JSONDecodeError) as exc:
        errors.append(f"invalid Design Manager authority index or result navigation: {exc}")
    for relative in (
        "design/pages/recognition/processing/optics/SWOS_Optical_Contract_V1.md",
        "design/pages/recognition/processing/motion/Recognition_Processing_Motion_Spec_V1_3.md",
        "design/pages/recognition/continuity/Processing_Result_Optical_Continuity_Contract_V1.md",
        "design/pages/recognition/continuity/Android_RR09_Shared_Photo_Transition_Final_Contract_V1.md",
        "design/pages/recognition/result/motion/Recognition_Result_Motion_Spec_V3.md",
        "design/system_v3/10_Page_Visual_Acceptance_Contracts/Recognition_Result/Recognition_Result_Motion_Contract_V3.md",
        "design/system_v3/08_Page_Contracts/recognition/processing/Recognition_Processing_Page_Contract_V3_2.md",
    ):
        if not (root / relative).is_file():
            errors.append(f"missing human-readable authority: {relative}")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    args = parser.parse_args()
    errors = validate(args.root.resolve())
    print("SWOS_PHASE0_CONTRACT_VALIDATION=" + ("FAIL" if errors else "PASS"))
    for error in errors:
        print("- " + error, file=sys.stderr)
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
