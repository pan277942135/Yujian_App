#!/usr/bin/env python3
"""Static parity checks for the frozen shared Android component contracts."""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def load(relative: str) -> dict:
    return json.loads(read(relative))


def require(condition: bool, label: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL {label}")
    print(f"PASS {label}")


action = load("design/system/components/action_button/visual_contract.json")
icon_action = load("design/system/components/icon_action/visual_contract.json")
text_action = load("design/system/components/text_action/visual_contract.json")
capture_motion = load("design/system/components/primary_capture_button/motion_contract.json")
capture_haptic = load("design/system/components/primary_capture_button/haptic_contract.json")
backgrounds = load("design/system/backgrounds/morning_lake_v1/treatment_contract.json")

spacing_source = read("app/src/main/java/com/yujian/ai/ui/designsystem/spacing/YuJianSpacing.kt")
button_source = read("app/src/main/java/com/yujian/ai/ui/designsystem/components/YuJianPrimaryButton.kt")
icon_source = read("app/src/main/java/com/yujian/ai/ui/designsystem/components/YuJianIconAction.kt")
text_source = read("app/src/main/java/com/yujian/ai/ui/designsystem/components/YuJianTextAction.kt")
capture_source = read("app/src/main/java/com/yujian/ai/ui/designsystem/components/YuJianCaptureButton.kt")
glass_source = read("app/src/main/java/com/yujian/ai/ui/designsystem/components/YuJianGlassCard.kt")
motion_source = read("app/src/main/java/com/yujian/ai/ui/designsystem/motion/YuJianMotion.kt")
background_source = read("app/src/main/java/com/yujian/ai/ui/designsystem/background/YuJianMorningLakeBackground.kt")
result_source = read("app/src/main/java/com/yujian/ai/ui/screens/RecognitionResultScreen.kt")
guide_source = read("app/src/main/java/com/yujian/ai/ui/fishguide/FishGuideBackground.kt")
my_catches_source = read("app/src/main/java/com/yujian/ai/ui/screens/MyScreen.kt")
runtime_test = read("app/src/androidTest/java/com/yujian/ai/ui/designsystem/DesignSystemComponentPreviewTest.kt")
gate_source = read("scripts/android_runtime/gates/login_v2.sh")

require(action["geometry"]["height_dp"] == 56, "action button frozen height")
require(action["geometry"]["radius_dp"] == 28, "action button frozen radius")
require("val actionButtonHeight = 56.dp" in spacing_source, "shared action height token")
require("val minimumTouchTarget = 44.dp" in spacing_source, "44dp shared touch target token")
require("val captureTouchTarget = 64.dp" in spacing_source, "64dp capture touch target token")
require("val labelColor = if (!enabled && !loading) disabledContent else content" in button_source,
        "disabled action label uses frozen disabled color")
require("stateDescription = \"正在加载\"" in button_source, "loading action exposes state description")
require("Modifier.border(2.dp, YuJianColors.ActionPrimary, shape).padding(4.dp)" in button_source,
        "action button focus ring includes external gap")
require(icon_action["geometry"]["min_touch_target_dp"] == 44, "icon action frozen target")
require(".size(YuJianSpacing.minimumTouchTarget)" in icon_source, "icon action enforces frozen target")
require("role = Role.Button" in icon_source and "Modifier.border(2.dp" in icon_source,
        "icon action role and focus outline")
require(text_action["geometry"]["min_touch_width_dp"] == 44 and text_action["geometry"]["min_touch_height_dp"] == 44,
        "text action frozen target")
require("minWidth = YuJianSpacing.minimumTouchTarget" in text_source and "minHeight = YuJianSpacing.minimumTouchTarget" in text_source,
        "text action enforces frozen target")
require(capture_motion["breath"]["duration_ms"] == 5000 and capture_motion["gold_rim"]["duration_ms"] == 1400,
        "capture motion contract remains frozen")
require("effectiveMotionEnabled && rasterMotion == null" in capture_source,
        "capture skips local idle animation when motion is disabled or scene-clocked")
require("YuJianSpacing.captureTouchTarget" in capture_source, "capture uses dedicated touch target")
require(capture_haptic["camera_tap"]["semantic"] == "light_impact" and "Feedback.LightImpact" in capture_source,
        "capture camera haptic matches frozen semantic")
require("role = Role.Button" in glass_source and "rememberYuJianReduceMotion()" in glass_source,
        "interactive glass card exposes button role and honors reduced motion")
require(backgrounds["variants"]["BG_CONTENT"]["mist_white_veil_alpha"] == 0.15,
        "BG_CONTENT frozen veil")
require(backgrounds["variants"]["BG_DATA"]["mist_white_veil_alpha"] == 0.3,
        "BG_DATA frozen veil")
require("BgContentColorMatrix" in background_source and "BgDataColorMatrix" in background_source,
        "both frozen background matrices share one implementation")
require("ColorMatrix" not in result_source and "ColorMatrix" not in guide_source and "ColorMatrix" not in my_catches_source,
        "page backgrounds no longer duplicate color matrices")
require("Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)" in motion_source,
        "reduced motion responds to system setting changes")
require("fontScale = 1.6f" in runtime_test and "Modifier.width(320.dp)" in runtime_test,
        "compact width and large-font runtime case authored")
require("DesignSystemComponentPreviewTest" in gate_source and
        "shared_components_accessibility.png" in gate_source and
        "shared_components_compact_large_font.png" in gate_source,
        "shared-component tests and evidence are collected by existing login gate")

print("SHARED_COMPONENT_PARITY=PASS")
