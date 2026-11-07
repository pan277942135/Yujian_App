#!/usr/bin/env python3
"""Validate SWOS V1.1 static contracts, manager registration, privacy and scope."""
from __future__ import annotations

import hashlib
import json
import os
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FRAME_PATH = ROOT / "design/pages/recognition/processing/optics/SWOS_15_Frame_Keyframe_Contract_V1_1.json"
TRANSITION_PATH = ROOT / "design/pages/recognition/continuity/SWOS_Shared_Photo_Transition_Contract_V1.json"
MANIFEST_PATH = ROOT / "design/pages/recognition/processing/optics/SWOS_Static_Asset_Manifest_V1_1.json"
TECH_CONTRACT_PATH = ROOT / "design/pages/recognition/web/SWOS_WEB_HIFI_MOTION_TECHNICAL_CONTRACT_V1_0.md"
REGISTRY_PATH = ROOT / "design/registry/experience_registry_v1.json"
EXPECTED_SOURCE_SHA = "ab7227cf7205ee2ac342b6cd75cd899747879506c95c33b888b6adf0acb47987"


def fail(message: str) -> None:
    print(f"FAIL: {message}", file=sys.stderr)
    raise SystemExit(1)


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def read_json(path: Path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        fail(f"invalid/missing JSON {path.relative_to(ROOT)}: {exc}")


def rect_mid(a: dict, b: dict) -> dict:
    keys = ("x", "y", "width", "height")
    return {key: (a[key] + b[key]) / 2 for key in keys}


def same_rect(a: dict, b: dict) -> bool:
    return all(abs(float(a[key]) - float(b[key])) < 1e-7 for key in ("x", "y", "width", "height"))


frames = read_json(FRAME_PATH)
transition = read_json(TRANSITION_PATH)
manifest = read_json(MANIFEST_PATH)
registry = read_json(REGISTRY_PATH)
require(TECH_CONTRACT_PATH.is_file(), "SWOS Web hi-fi motion technical contract markdown is missing")
tech_contract_text = TECH_CONTRACT_PATH.read_text(encoding="utf-8")
WEB_VISUAL_BASELINE_PATH = ROOT / "design/pages/recognition/web/VISUAL_REFERENCE_MANIFEST_V1.json"
web_visual_manifest = read_json(WEB_VISUAL_BASELINE_PATH)
baseline = web_visual_manifest.get("pixel_baseline", {})
require(web_visual_manifest.get("pixel_frozen_source") == "FROZEN_PRIVATE_SHA_BOUND", "Web pixel baseline must be frozen by exact private SHA")
require(baseline.get("baseline_id") == "YUJIAN_SWOS_WEB_PIXEL_BASELINE_V1" and baseline.get("reference_id") == "web-three-stage" and baseline.get("sha256") == "27c34ee5d3cf90921df433cb43c671e42b8ffa80b8f5cebfe9b397da2ee853e2", "Web baseline identity/SHA mismatch")
require(baseline.get("dimensions_px") == [1536, 1024] and baseline.get("size_bytes") == 1804497, "Web baseline dimensions/byte count mismatch")
require(baseline.get("source_bytes_reverified_this_execution") is False, "do not claim the private original was reverified in this execution")
require(web_visual_manifest.get("public_publication_allowed") is False and web_visual_manifest.get("public_repo_commit_allowed") is False and web_visual_manifest.get("public_ci_artifact_allowed") is False and web_visual_manifest.get("public_pages_upload_allowed") is False, "private Web baseline must remain excluded from public destinations")
keys = frames.get("keyframes", [])
require(frames.get("frame_count") == 15 and len(keys) == 15, "must contain exactly 15 keyframes")
processing = [frame for frame in keys if frame.get("track") == "PROCESSING"]
rr09 = [frame for frame in keys if frame.get("track") == "RR09"]
require(len(processing) == 8 and [f["id"] for f in processing] == [f"P{i}" for i in range(8)], "Processing must be P0–P7, exactly 8 frames")
require(len(rr09) == 7 and [f["id"] for f in rr09] == ["R0", "R0.5", "R1", "R2", "R3", "R4", "R5"], "RR09 must be R0–R5 with R0.5, exactly 7 frames")
require([f["relative_ms"] for f in processing] == [0, 350, 950, 1200, 1550, 2050, 2600, 2800], "Processing time samples drifted")
require([f["relative_ms"] for f in rr09] == [0, 100, 200, 380, 550, 680, 850], "RR09 relative samples drifted")
require([f["global_nominal_ms"] for f in rr09] == [2800, 2900, 3000, 3180, 3350, 3480, 3650], "RR09 global samples drifted")
require(frames.get("processing", {}).get("duration_ms") == 2800 and frames.get("rr09", {}).get("duration_ms") == 850 and frames.get("total_nominal_duration_ms") == 3650, "duration must remain 2800 + 850 = 3650ms")
require(frames.get("playback_implemented") is False and frames.get("android_runtime") == "NOT_RUN", "static contract must not claim playback or Android runtime")
require(all(frame.get("business_state_change") is False for frame in keys), "a static sample may not add business states")
require(all(frame.get("species_name_reveal") == "HIDDEN" for frame in processing), "Processing may not reveal a species name")
require(processing[-1].get("ambient_water_strength") == 0 and processing[-1].get("fish_gold_strength") == 100, "P7 must release ambient water and preserve fish gold")
require([f["ambient_water_strength"] for f in processing] == [0, 10, 45, 75, 60, 35, 10, 0], "ambient water strength curve drifted")
require([f["fish_gold_strength"] for f in processing] == [0, 0, 15, 45, 100, 100, 100, 100], "fish gold strength curve drifted")
require([f["fish_gold_strength"] for f in processing[-4:]] == [100, 100, 100, 100], "fish gold must hold 1550–2800ms")
require(rr09[1]["id"] == "R0.5" and rr09[1]["relative_ms"] == 100 and rr09[1]["global_nominal_ms"] == 2900, "R0.5 must be at RR09 +100ms / global 2900ms")
require(rr09[2]["id"] == "R1" and rr09[2]["relative_ms"] == 200, "R1 must remain at RR09 +200ms")
samples = transition["samples"]
require(same_rect(samples["R0"]["foreground_rect"], transition["processing_photo_content_rect"]), "R0 must retain the Processing source foreground rect")
require(rr09[2]["gold_transfer_progress"] == 0 and rr09[3]["gold_transfer_progress"] == 100, "gold transfer must begin after the photo lands at R1")
require(rr09[-1]["ambient_water_strength"] == 0 and rr09[-1]["fish_gold_strength"] == 0, "R5 must have no continuing glow")

expected_foreground_mid = rect_mid(transition["processing_photo_content_rect"], transition["result_hero_photo_content_rect"])
expected_outer_mid = rect_mid(samples["R0"]["outer_rect"], samples["R1"]["outer_rect"])
require(same_rect(samples["R0.5"]["foreground_rect"], expected_foreground_mid), "R0.5 foreground must be the exact midpoint of P7 and R1")
require(same_rect(samples["R0.5"]["outer_rect"], expected_outer_mid), "R0.5 outer rect must be the exact midpoint of P7 and R1")
require(transition["sample_source"]["source_photo_sha256"] == EXPECTED_SOURCE_SHA, "sample photo identity SHA drifted")
require(transition["golden_trace_state"]["transform"] == "same foreground transform used by the photo", "gold geometry must share the photo transform")
require(transition["fish_geometry"]["production_mask"] is False and transition["fish_geometry"]["trust_state"] == "MANUAL_REFERENCE_ONLY_NOT_PRODUCTION_MASK", "manual contour must not be treated as production geometry")
require(transition["transform"]["stretch"] is False and transition["transform"]["generated_fill"] is False and transition["transform"]["photo_swap"] is False, "forbidden photo transform behavior found")

photo = manifest["source_photo"]
require(photo["sha256"] == EXPECTED_SOURCE_SHA and photo["repository_included"] is False, "source photo must be identified but excluded from this public repository")
require(photo["public_permission"] == "NOT_CONFIRMED", "photo publication gate must remain explicit")
require(manifest["derived_frames"]["repository_included"] is False and manifest["derived_frames"]["frame_count"] == 15, "private frame exports must not be added to public repository")
require(not any(ROOT.rglob("208758.jpg")), "private photo 208758.jpg leaked into the public repository tree")

flow = next(feature for feature in registry["features"] if feature.get("id") == "recognition_flow_v1_2")
static_view = next((view for view in flow.get("hifi_views", []) if view.get("id") == "swos_static_visual_v1_1"), None)
require(static_view is not None and static_view.get("render_mode") == "swos_static_visual_workspace", "Design Manager route/renderer registration missing")
require(static_view.get("status") == "DESIGN_ONLY" and static_view.get("adoption_status") == "MAIN_ADOPTED_DESIGN_ONLY" and static_view.get("adoption_pr") == 190, "static contract must be main-adopted design-only")
require("YUJIAN-SWOS-WEB-HIFI-MOTION-TECHNICAL-V1_0" in tech_contract_text and "**Version:** 1.0" in tech_contract_text, "SWOS Web motion Markdown authority identity/version drift")
require("3650ms" in tech_contract_text and "2800ms" in tech_contract_text and "850ms" in tech_contract_text and "R0.5" in tech_contract_text, "SWOS Web motion Markdown authority timing/R0.5 drift")
require("同源照片" in tech_contract_text and "单一变换" in tech_contract_text and "中文名" in tech_contract_text, "SWOS Web motion contract must preserve shared-photo transform and Chinese-only reveal")
require("SAM ViT-B" in tech_contract_text and "NOT_QUALIFIED" in tech_contract_text, "SWOS Web must reuse the existing SAM experiment without qualifying production Mask")
require("NOT_VERIFIED" in tech_contract_text, "contract must not claim browser playback/runtime acceptance")
tech_view = next((view for view in flow.get("hifi_views", []) if view.get("id") == "swos_web_hifi_motion_contract_v1_0"), None)
require(tech_view is not None and tech_view.get("authority") == "design/pages/recognition/web/SWOS_WEB_HIFI_MOTION_TECHNICAL_CONTRACT_V1_0.md" and tech_view.get("machine_authority") in (None, ""), "SWOS Web motion technical contract must use its Markdown authority without a retired duplicate JSON")
for link in tech_view.get("contract_links", []):
    require((ROOT / link.get("path", "")).is_file(), f"SWOS Web motion contract link is missing: {link.get('path')}")

EMPTY_HOME_V22_RIPPLE_HAPTIC_AMENDMENT_PATHS = {
    "app/src/androidTest/java/com/yujian/ai/EmptyHomeV2RuntimeContractTest.kt",
    "app/src/main/assets/empty_home_runtime_v2/config/haptic_contract.json",
    "app/src/main/assets/empty_home_runtime_v2/config/layer_contract.json",
    "app/src/main/assets/empty_home_runtime_v2/config/motion_contract.json",
    "app/src/main/assets/empty_home_runtime_v2/config/runtime_manifest.json",
    "app/src/main/java/com/yujian/ai/ui/home/CaptureHaptics.kt",
    "app/src/main/java/com/yujian/ai/ui/home/EmptyHomeMotion.kt",
    "app/src/main/java/com/yujian/ai/ui/home/EmptyHomeSceneRenderer.kt",
    "app/src/test/java/com/yujian/ai/ui/home/EmptyHomeMotionTest.kt",
}
EMPTY_HOME_V22_FROZEN_COMPOSITION_REPAIR_TASK_ID = "YUJIAN_EMPTY_HOME_V2_2_FROZEN_COMPOSITION_REPAIR_V1"
EMPTY_HOME_V22_FROZEN_COMPOSITION_REPAIR_TASK_CONTRACT_PATH = (
    "harness/task_contracts/YUJIAN_EMPTY_HOME_V2_2_FROZEN_COMPOSITION_REPAIR_V1.md"
)
EMPTY_HOME_V22_FROZEN_COMPOSITION_REPAIR_APP_PATHS = {
    "app/src/main/java/com/yujian/ai/ui/home/EmptyHomeMotion.kt",
    "app/src/main/java/com/yujian/ai/ui/home/EmptyHomeSceneRenderer.kt",
}
MAIN_BASELINE_RECOVERY_BOOTSTRAP_TASK_ID = "YUJIAN_MAIN_BASELINE_RECOVERY_BOOTSTRAP_V1"
MAIN_BASELINE_RECOVERY_BOOTSTRAP_TASK_CONTRACT_PATH = (
    "harness/task_contracts/YUJIAN_MAIN_BASELINE_RECOVERY_BOOTSTRAP_V1.md"
)
MAIN_BASELINE_RECOVERY_BOOTSTRAP_APP_PATHS = {
    "app/src/main/java/com/yujian/ai/ui/home/EmptyHomeMotion.kt",
    "app/src/main/java/com/yujian/ai/ui/home/EmptyHomeSceneRenderer.kt",
    "app/src/test/java/com/yujian/ai/ui/home/EmptyHomeMotionTest.kt",
}
AISTUDIO_FULL_RESTORE_TASK_ID = "YUJIAN_ANDROID_AISTUDIO_FULL_PAGE_RESTORE_AND_MAIN_INTEGRATION_V1"
AISTUDIO_FULL_RESTORE_TASK_CONTRACT_PATH = "harness/task_contracts/YUJIAN_ANDROID_AISTUDIO_FULL_PAGE_RESTORE_AND_MAIN_INTEGRATION_V1.md"
AISTUDIO_FULL_RESTORE_APP_PATHS = {
    "app/src/androidTest/java/com/yujian/ai/EmptyHomeV2RuntimeContractTest.kt",
    "app/src/main/assets/empty_home_runtime_v2/config/haptic_contract.json",
    "app/src/main/assets/empty_home_runtime_v2/config/hero_asset_contract.json",
    "app/src/main/assets/empty_home_runtime_v2/config/layer_contract.json",
    "app/src/main/assets/empty_home_runtime_v2/config/motion_contract.json",
    "app/src/main/assets/empty_home_runtime_v2/config/runtime_manifest.json",
    "app/src/main/assets/empty_home_runtime_v2/config/w5_runtime_candidate_geometry.json",
    "app/src/main/java/com/yujian/ai/auth/AuthRepository.kt",
    "app/src/main/java/com/yujian/ai/catches/CatchRepository.kt",
    "app/src/main/java/com/yujian/ai/feedback/FeedbackRepository.kt",
    "app/src/main/java/com/yujian/ai/knowledge/FishKnowledgeRepository.kt",
    "app/src/main/java/com/yujian/ai/network/HttpBaseUrl.kt",
    "app/src/main/java/com/yujian/ai/ui/home/EmptyHomeMotion.kt",
    "app/src/main/java/com/yujian/ai/ui/home/EmptyHomeSceneRenderer.kt",
    "app/src/main/java/com/yujian/ai/ui/identify/RecognitionFirstCaptureGuide.kt",
    "app/src/main/res/drawable-nodpi/empty_home_title_v2.png",
    "app/src/test/java/com/yujian/ai/network/HttpBaseUrlTest.kt",
    "app/src/test/java/com/yujian/ai/ui/home/EmptyHomeMotionTest.kt",
}
LAKE_GLASS_TASK_ID = "YUJIAN_LAKE_GLASS_V2_ASSET_PACK_ANDROID_RUNTIME_INTEGRATION_V1"
LAKE_GLASS_TASK_CONTRACT_PATH = "harness/task_contracts/LAKE_GLASS_V2_ASSET_PACK_ANDROID_RUNTIME_INTEGRATION_V1.md"
LAKE_GLASS_ANDROID_APP_PATHS = {
    "app/src/androidTest/java/com/yujian/ai/ui/designsystem/lakeglass/LakeGlassAgslRuntimeTest.kt",
    "app/src/androidTest/java/com/yujian/ai/ui/designsystem/lakeglass/LakeGlassStaticRuntimeTest.kt",
    "app/src/debug/AndroidManifest.xml",
    "app/src/debug/java/com/yujian/ai/ui/designsystem/lakeglass/LakeGlassShaderLabActivity.kt",
    "app/src/debug/java/com/yujian/ai/ui/designsystem/lakeglass/LakeGlassShaderLabScreen.kt",
    "app/src/main/assets/lake_glass_v2/assets/android_runtime/api24_static/lake_glass_master_fallback.png",
    "app/src/main/assets/lake_glass_v2/assets/android_runtime/api24_static/lake_glass_static_layered_v3.png",
    "app/src/main/assets/lake_glass_v2/assets/android_runtime/layer_manifest_v1.json",
    "app/src/main/assets/lake_glass_v2/assets/android_runtime/runtime_optical_masks_v1.png",
    "app/src/main/assets/lake_glass_v2/shaders/lake_glass_v2.agsl",
    "app/src/main/java/com/yujian/ai/YujianApplication.kt",
    "app/src/main/java/com/yujian/ai/ui/auth/AuthV2Components.kt",
    "app/src/main/java/com/yujian/ai/ui/designsystem/background/YuJianMorningLakeBackground.kt",
    "app/src/main/java/com/yujian/ai/ui/designsystem/lakeglass/AmbientController.kt",
    "app/src/main/java/com/yujian/ai/ui/designsystem/lakeglass/LakeGlassAgslProgram.kt",
    "app/src/main/java/com/yujian/ai/ui/designsystem/lakeglass/LakeGlassAssetRepository.kt",
    "app/src/main/java/com/yujian/ai/ui/designsystem/lakeglass/LakeGlassBackgroundHost.kt",
    "app/src/main/java/com/yujian/ai/ui/designsystem/lakeglass/LakeGlassRenderPolicy.kt",
    "app/src/main/java/com/yujian/ai/ui/designsystem/lakeglass/LakeGlassRenderer.kt",
    "app/src/main/java/com/yujian/ai/ui/designsystem/lakeglass/LakeGlassSceneManifest.kt",
    "app/src/main/java/com/yujian/ai/ui/designsystem/lakeglass/LakeGlassViewportTransform.kt",
    "app/src/main/java/com/yujian/ai/ui/home/GeminiNormalHomeContent.kt",
    "app/src/main/java/com/yujian/ai/ui/home/NormalHomeRuntimeAssets.kt",
    "app/src/main/java/com/yujian/ai/ui/screens/CaptureFrostedBackground.kt",
    "app/src/main/java/com/yujian/ai/ui/screens/RecognizingScreen.kt",
    "app/src/test/java/com/yujian/ai/ui/designsystem/lakeglass/LakeGlassRuntimeContractTest.kt",
}

NH01_V4_TASK_ID = "YUJIAN_NH01_POPULATED_HOME_RESPONSIVE_HIFI_V4"
NH01_V4_TASK_CONTRACT_PATH = "harness/task_contracts/YUJIAN_NH01_POPULATED_HOME_RESPONSIVE_HIFI_V4.md"
NH01_V4_ANDROID_APP_PATHS = {
    "app/src/androidTest/java/com/yujian/ai/ui/home/GeminiHomeDeviceMatrixTest.kt",
    "app/src/main/java/com/yujian/ai/catches/CatchRepository.kt",
    "app/src/main/java/com/yujian/ai/ui/designsystem/color/YuJianColors.kt",
    "app/src/main/java/com/yujian/ai/ui/home/GeminiHomeAdaptiveLayoutPolicy.kt",
    "app/src/main/java/com/yujian/ai/ui/home/GeminiNormalHomeContent.kt",
    "app/src/main/java/com/yujian/ai/ui/home/HomeStats.kt",
    "app/src/main/java/com/yujian/ai/ui/screens/HomeScreen.kt",
    "app/src/test/java/com/yujian/ai/ui/home/GeminiHomeAdaptiveLayoutPolicyTest.kt",
    "app/src/androidTest/java/com/yujian/ai/ui/home/HomeStatsSemanticsTest.kt",
}

base_branch = os.environ.get("GITHUB_BASE_REF")
if base_branch:
    base_ref = f"origin/{base_branch}"
    verified_base = subprocess.run(["git", "rev-parse", "--verify", base_ref], cwd=ROOT, capture_output=True, text=True)
    if verified_base.returncode != 0:
        fail(f"control-scope base ref is unavailable: {base_ref}")
    changed = subprocess.run(["git", "diff", "--name-only", base_ref, "HEAD"], cwd=ROOT, check=True, capture_output=True, text=True).stdout.splitlines()
    changed_app_paths = {path for path in changed if path.startswith("app/")}
    # This owner-authorized baseline PR integrates several independently reviewed Work
    # scopes; the single-task allowlist below is not a valid comparison for that PR.
    # Scope widening is limited to this named integration branch and provenance record.
    baseline_record = ROOT / "harness/reports/YUJIAN_20261103_UNIFIED_MAIN_BASELINE_V1.md"
    baseline_text = baseline_record.read_text(encoding="utf-8") if baseline_record.is_file() else ""
    is_consolidated_baseline_scope = (
        os.environ.get("GITHUB_HEAD_REF") == "baseline/yujian-20261103-unified-qa-v1"
        and "2c09172cdfd9244abc86b25b51492ccf7e51db24" in baseline_text
        and "2026-11-03" in baseline_text
    )
    if changed_app_paths:
        task_contract_path = ROOT / AISTUDIO_FULL_RESTORE_TASK_CONTRACT_PATH
        task_contract_text = task_contract_path.read_text(encoding="utf-8") if task_contract_path.is_file() else ""
        is_exact_full_restore_scope = (
            changed_app_paths == AISTUDIO_FULL_RESTORE_APP_PATHS
            and AISTUDIO_FULL_RESTORE_TASK_CONTRACT_PATH not in changed
            and f"`{AISTUDIO_FULL_RESTORE_TASK_ID}`" in task_contract_text
            and "本次不是 Empty Home 专项修复" in task_contract_text
            and "首次拍照引导页面" in task_contract_text
        )
        frozen_composition_contract_path = ROOT / EMPTY_HOME_V22_FROZEN_COMPOSITION_REPAIR_TASK_CONTRACT_PATH
        frozen_composition_contract_text = (
            frozen_composition_contract_path.read_text(encoding="utf-8")
            if frozen_composition_contract_path.is_file()
            else ""
        )
        is_exact_frozen_composition_scope = (
            changed_app_paths == EMPTY_HOME_V22_FROZEN_COMPOSITION_REPAIR_APP_PATHS
            and EMPTY_HOME_V22_FROZEN_COMPOSITION_REPAIR_TASK_CONTRACT_PATH not in changed
            and f"`{EMPTY_HOME_V22_FROZEN_COMPOSITION_REPAIR_TASK_ID}`" in frozen_composition_contract_text
            and "must not change the frozen V2.2 product contracts" in frozen_composition_contract_text
            and "must not promote the W5 candidate geometry" in frozen_composition_contract_text
        )
        baseline_recovery_contract_path = ROOT / MAIN_BASELINE_RECOVERY_BOOTSTRAP_TASK_CONTRACT_PATH
        baseline_recovery_contract_text = (
            baseline_recovery_contract_path.read_text(encoding="utf-8")
            if baseline_recovery_contract_path.is_file()
            else ""
        )
        is_main_baseline_recovery_bootstrap_scope = (
            os.environ.get("GITHUB_HEAD_REF") == "recovery/main-baseline-v1"
            and changed_app_paths == MAIN_BASELINE_RECOVERY_BOOTSTRAP_APP_PATHS
            and MAIN_BASELINE_RECOVERY_BOOTSTRAP_TASK_CONTRACT_PATH in changed
            and f"`{MAIN_BASELINE_RECOVERY_BOOTSTRAP_TASK_ID}`" in baseline_recovery_contract_text
            and "one-time bootstrap path" in baseline_recovery_contract_text
            and "It must not change the frozen V2.2 product contracts." in baseline_recovery_contract_text
            and "It must not promote the W5 candidate geometry." in baseline_recovery_contract_text
        )
        nh01_v4_contract_path = ROOT / NH01_V4_TASK_CONTRACT_PATH
        nh01_v4_contract_text = nh01_v4_contract_path.read_text(encoding="utf-8") if nh01_v4_contract_path.is_file() else ""
        is_exact_nh01_v4_scope = (
            changed_app_paths == NH01_V4_ANDROID_APP_PATHS
            and NH01_V4_TASK_CONTRACT_PATH in changed
            and f"`{NH01_V4_TASK_ID}`" in nh01_v4_contract_text
            and "Priority: P0" in nh01_v4_contract_text
            and "Exact Android app file set" in nh01_v4_contract_text
        )
        lake_glass_contract_path = ROOT / LAKE_GLASS_TASK_CONTRACT_PATH
        lake_glass_contract_text = lake_glass_contract_path.read_text(encoding="utf-8") if lake_glass_contract_path.is_file() else ""
        is_exact_lake_glass_scope = (
            changed_app_paths == LAKE_GLASS_ANDROID_APP_PATHS
            and f"`{LAKE_GLASS_TASK_ID}`" in lake_glass_contract_text
            and "Keep the existing Empty Home composition" in lake_glass_contract_text
            and "Preserve Morning Lake V1 history byte-for-byte" in lake_glass_contract_text
            and "Do not add a `GLSurfaceView`" in lake_glass_contract_text
        )
        nh01_contract_id_ok = f"`{NH01_V4_TASK_ID}`" in nh01_v4_contract_text
        nh01_contract_priority_ok = "Priority: P0" in nh01_v4_contract_text
        nh01_contract_file_set_ok = "Exact Android app file set" in nh01_v4_contract_text
        scope_diagnostic = (
            f"NH01 app_paths_exact={changed_app_paths == NH01_V4_ANDROID_APP_PATHS} "
            f"contract_changed={NH01_V4_TASK_CONTRACT_PATH in changed} "
            f"contract_id={nh01_contract_id_ok} "
            f"priority={nh01_contract_priority_ok} "
            f"exact_set_marker={nh01_contract_file_set_ok}; "
            f"changed_app_paths={sorted(changed_app_paths)}"
        )
        require(
            changed_app_paths == EMPTY_HOME_V22_RIPPLE_HAPTIC_AMENDMENT_PATHS or is_exact_frozen_composition_scope or is_main_baseline_recovery_bootstrap_scope or is_exact_full_restore_scope or is_exact_lake_glass_scope or is_exact_nh01_v4_scope or is_consolidated_baseline_scope,
            "scope boundary violated by app changes outside an exact approved task file set; " + scope_diagnostic,
        )
        amendment_spec = (ROOT / "design/pages/home/empty_home/spec/Empty_Home_Frozen_Visual_Revision_V2_2.md").read_text(encoding="utf-8")
        motion_contract = read_json(ROOT / "app/src/main/assets/empty_home_runtime_v2/config/motion_contract.json")
        layer_contract = read_json(ROOT / "app/src/main/assets/empty_home_runtime_v2/config/layer_contract.json")
        haptic_contract = read_json(ROOT / "app/src/main/assets/empty_home_runtime_v2/config/haptic_contract.json")
        automatic_haptic = haptic_contract.get("automatic_bobber_nibble", {})
        ripple = motion_contract.get("ripple", {})
        require(
            "Approved motion / haptic amendment — 2026-11-01" in amendment_spec
            and "APPROVED BY PRODUCT OWNER" in amendment_spec,
            "Empty Home app-scope exception requires the explicit V2.2 product-owner amendment record",
        )
        if is_exact_full_restore_scope:
            expected_offsets = [1500, 6800, 13600, 22400, 28200]
            require(
                motion_contract.get("contract_revision") == "EMPTY_HOME_MOTION_V2_2_RIPPLE_HAPTIC_AMENDMENT_02"
                and ripple.get("count") == 3
                and ripple.get("duration_ms") == 3600
                and ripple.get("event_cycle_duration_ms") == 36000
                and ripple.get("event_start_offsets_ms") == expected_offsets
                and len(ripple.get("wave_layers", [])) == 3,
                "AI Studio full restore requires the approved _02 three-wave 3.6s motion contract",
            )
            layer_rules = layer_contract.get("rules", {})
            require(
                layer_contract.get("contract_revision") == "EMPTY_HOME_LAYERS_V2_2_RIPPLE_HAPTIC_AMENDMENT_02"
                and layer_rules.get("ripple_group_count") == 1
                and layer_rules.get("ripple_burst_duration_ms") == 3600
                and layer_rules.get("ripple_burst_trigger") == "periodic_bobber_motion_event",
                "AI Studio full restore requires the _02 single-group motion-event ripple layer",
            )
            require(
                haptic_contract.get("contract_revision") == "EMPTY_HOME_HAPTIC_V1_3_MOTION_EVENT_START_GUARD"
                and automatic_haptic.get("enabled") is True
                and automatic_haptic.get("trigger") == "periodic_bobber_motion_event_start"
                and automatic_haptic.get("event_cycle_duration_ms") == 36000
                and automatic_haptic.get("event_start_offsets_ms") == expected_offsets
                and automatic_haptic.get("dedupe") == "one_per_motion_event_id"
                and automatic_haptic.get("max_start_latency_ms") == 120,
                "AI Studio full restore requires the event-start-guarded V1.3 haptic contract",
            )
        elif not is_main_baseline_recovery_bootstrap_scope and not is_exact_lake_glass_scope and not is_exact_nh01_v4_scope and not is_consolidated_baseline_scope:
            require(
                motion_contract.get("contract_revision") == "EMPTY_HOME_MOTION_V2_2_RIPPLE_HAPTIC_AMENDMENT_01"
                and ripple.get("count") == 3
                and ripple.get("duration_ms") == 3600
                and len(ripple.get("wave_layers", [])) == 3,
                "Empty Home app-scope exception requires the exact approved three-wave 3.6s motion contract",
            )
            require(
                layer_contract.get("contract_revision") == "EMPTY_HOME_LAYERS_V2_2_RIPPLE_HAPTIC_AMENDMENT_01"
                and layer_contract.get("rules", {}).get("ripple_group_count") == 1,
                "Empty Home app-scope exception requires the one-group layer contract",
            )
            require(
                haptic_contract.get("contract_revision") == "EMPTY_HOME_HAPTIC_V1_1_APPROVED_BOBBER_EVENT"
                and automatic_haptic.get("enabled") is True
                and automatic_haptic.get("trigger") == "periodic_bobber_ripple_burst_start",
                "Empty Home app-scope exception requires the approved event-gated haptic contract",
            )
        if is_consolidated_baseline_scope:
            require(
                ripple.get("count") == 3 and ripple.get("duration_ms") == 3600
                and len(ripple.get("wave_layers", [])) == 3
                and automatic_haptic.get("enabled") is True,
                "unified baseline must preserve approved Empty Home ripple/haptic invariants",
            )
            print("UNIFIED_QA_BASELINE_SCOPE=PASS owner_authorized_exact_branch_and_source_provenance")
        elif is_exact_frozen_composition_scope:
            print("EMPTY_HOME_V22_FROZEN_COMPOSITION_SCOPE=PASS exact_app_file_set_and_frozen_contract_guard")
        elif is_main_baseline_recovery_bootstrap_scope:
            print("MAIN_BASELINE_RECOVERY_BOOTSTRAP_SCOPE=PASS exact_branch_task_contract_and_app_file_set")
        elif is_exact_full_restore_scope:
            print("AISTUDIO_FULL_PAGE_RESTORE_SCOPE_EXCEPTION=PASS exact_Main_task_contract_and_app_file_set")
        elif is_exact_lake_glass_scope:
            print("LAKE_GLASS_V2_ANDROID_SCOPE=PASS exact_task_contract_and_app_file_set")
        elif is_exact_nh01_v4_scope:
            print("NH01_V4_ANDROID_SCOPE=PASS exact_task_contract_and_app_file_set")
        else:
            print("EMPTY_HOME_V22_RIPPLE_HAPTIC_SCOPE_EXCEPTION=PASS exact_file_set_and_approved_contracts")
    allowed_app_paths = EMPTY_HOME_V22_RIPPLE_HAPTIC_AMENDMENT_PATHS | EMPTY_HOME_V22_FROZEN_COMPOSITION_REPAIR_APP_PATHS | MAIN_BASELINE_RECOVERY_BOOTSTRAP_APP_PATHS | AISTUDIO_FULL_RESTORE_APP_PATHS | LAKE_GLASS_ANDROID_APP_PATHS | NH01_V4_ANDROID_APP_PATHS
    for path in changed:
        require(not path.startswith("android/") and not path.startswith("design/manager/rr09_motion/"), f"scope boundary violated by {path}")
        if is_consolidated_baseline_scope:
            require(
                not path.startswith("app/") or path == "app/build.gradle.kts" or path.startswith("app/src/"),
                f"unified baseline app-scope violation: {path}",
            )
        else:
            require(not path.startswith("app/") or path in allowed_app_paths, f"scope boundary violated by {path}")
    print(f"CONTROL_SCOPE=PASS base_ref={base_ref} changed_files={len(changed)}")
else:
    # The allowlist compares a PR head with its fetched PR base. Push/dispatch
    # builds have no GITHUB_BASE_REF, so there is no meaningful scope diff.
    print("CONTROL_SCOPE=SKIP reason=no_pull_request_base")

print("SWOS_STATIC_VISUAL_V1_1=CONTRACT_PASS")
print("FRAME_COUNTS=PROCESSING:8 RR09:7 TOTAL:15")
print("DURATION_MS=2800+850=3650; R0.5=100ms/2900ms")
print("R0_R0_5_R1_GEOMETRY=SHARED_MIDPOINT_PASS")
print("WEB_PIXEL_BASELINE=FROZEN_PRIVATE_SHA_BOUND; PHOTO_PUBLICATION=NOT_CONFIRMED; SOURCE_AND_DERIVATIVES_EXCLUDED")
print("ANDROID_NOT_RUN; DYNAMIC_DEMO_NOT_RUN")
