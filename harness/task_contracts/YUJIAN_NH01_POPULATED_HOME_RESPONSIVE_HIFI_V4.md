# YUJIAN_NH01_POPULATED_HOME_RESPONSIVE_HIFI_V4

- Priority: P0
- Applies to: `YUJIAN_NH01_POPULATED_HOME_RESPONSIVE_HIFI_V4`
- User authorization: NH01 V4 responsive high fidelity implementation and three-round visual/runtime closure.
- Reference authority: `NH01_V4_VISUAL_BASELINE_MANIFEST.json`, authority `CURRENT_USER_FROZEN_DESIGN`.
- Runtime: reuse the repository's API 28 NH01 matrix workflow and existing run path.
- Exact Android app file set: only the following app files may change for this task.

  - `app/src/androidTest/java/com/yujian/ai/ui/home/GeminiHomeDeviceMatrixTest.kt`
  - `app/src/androidTest/java/com/yujian/ai/ui/home/GeminiNormalHomeResponsiveTest.kt`
  - `app/src/main/java/com/yujian/ai/catches/CatchRepository.kt`
  - `app/src/main/java/com/yujian/ai/ui/designsystem/color/YuJianColors.kt`
  - `app/src/main/java/com/yujian/ai/ui/home/GeminiHomeAdaptiveLayoutPolicy.kt`
  - `app/src/main/java/com/yujian/ai/ui/home/GeminiNormalHomeContent.kt`
  - `app/src/main/java/com/yujian/ai/ui/home/HomeStats.kt`
  - `app/src/main/java/com/yujian/ai/ui/screens/HomeScreen.kt`
  - `app/src/test/java/com/yujian/ai/ui/home/GeminiHomeAdaptiveLayoutPolicyTest.kt`
  - `app/src/androidTest/java/com/yujian/ai/ui/home/HomeStatsSemanticsTest.kt`

- Acceptance remains governed by the existing NH01 V4 layout, runtime, visual, and three-round closure requirements. This task contract records scope and does not alter acceptance thresholds.
