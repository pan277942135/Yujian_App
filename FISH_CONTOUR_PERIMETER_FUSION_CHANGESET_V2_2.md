# V2.2 Changeset

## 目标

在 PR #316 / `feat/p0-ai-orb-external-optics-v2` 增量集成真实 SlimSAM 鱼体轮廓光学、Effect 02 照片外围 WaterLight，并沿用 AI Orb 和 13 阶段共享生命周期。

## 代码改动

- `RecognitionMaskContourExtractor.kt`: 闭环加权平滑、闭环 RDP、二次平滑、安全检查及逐级回退；保留 APP Marching Squares 和独立轮廓。
- `RecognitionFishFocus.kt`: L0–L3 渐变与巡航、完整 Path 缓存身份和共享 Scene Frame 相位。
- `RecognitionPerimeterOpticalProfileV1.kt`: Effect 02 默认、Effect 01 内部回归参数。
- `RecognitionPerimeterShaderMath.kt` / `RecognitionPerimeterShaderOverlay.kt`: 共用时钟、1.5%/12%几何、完整底部焦散及现有单 TextureView GLES2 合成。
- Reveal handoff / geometry / optical renderer: 传 Mask version；沿用原识别身份、profile 与 Scene Frame 连续承接。
- `RecognizingScreen.kt`: 去除照片外围重复 CPU Caustics Bitmap 与 Ambient edge overlay；保留非重复 WaterLight 功能。
- Unit tests: 增加闭环尖鳍/尾部回归、缓存身份各字段失效、Effect 01/02 与 perimeter stage/time/coordinates 测试。

## 未修改的合同

- RecognitionSceneAnimationController、13 阶段枚举和顺序、阶段时长、AUTO/MANUAL、暂停恢复、真实模型门禁及共享 60 秒 attempt deadline 未更改。
- 未添加独立动画 Timer、ValueAnimator、新 Shader 宿主、新 PR、新分支或 Demo 媒体二进制。
- PR #316 保持 Open / Draft / Unmerged。

## 验证

- `git diff --check`: PASS
- Admission gate: `CONTROL_PLANE_SYNC=PASS`、项目/执行状态校验 PASS、静态检查 PASS（动态状态文件仅本地，不提交）
- 本地 Gradle unit/lint/APK: BLOCKED（没有 Wrapper、Gradle 安装、Android SDK）
- exact-head Unit Test / Lint / Assemble Debug: 由 PR #316 的 CI 检查记录
- Android Shader runtime compile / device / visual capture: NOT RUN

## 精确产物身份

最终提交 SHA、exact-head CI run、APK 大小和 SHA-256 作为 post-build 结果保存在 PR #316 构建记录中。此源码 changeset 不预写还未生成的二进制摘要。
