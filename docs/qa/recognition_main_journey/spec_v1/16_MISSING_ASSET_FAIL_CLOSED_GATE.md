# 16 · 首次拍照教学缺图 Fail-closed 验收门禁

**Gate ID：** G03-ASSET-FAIL-CLOSED-V1  
**适用范围：** 首次拍照教学 P01/P02/P03 的所有运行时示范图片  
**当前判定：** BLOCKED_ASSET_SOURCE  
**本阶段限制：** 本文定义门禁，不实现 Validator、不修改 Android 源码、不运行 Android/Runtime 测试。

## 1. 强制规则

**任何必需示范图缺失、未经授权、未绑定、哈希不匹配或语义不符，均不得放行首次拍照教学的视觉验收。**

P01/P02/P03 每页 1 个正确 Hero + 3 个错误示范，共 12 个必需槽位。12 个槽位必须全部通过资产完整性、来源授权、Android 绑定、语义审核和真实 Runtime 显示核验。

下列状态不能被降级为“非阻断警告”：

- ASSET_SOURCE_BLOCKED
- ASSET_MISSING
- ASSET_UNBOUND
- ASSET_HASH_MISMATCH
- ASSET_DECODE_FAILED
- ASSET_SEMANTIC_MISMATCH
- ASSET_RUNTIME_NOT_VERIFIED

缺图时不能以空白 Box、仅有标题/说明文字、实时 CameraX 预览、通用图片、模型 Golden、整页 Frozen PNG 或从 Frozen PNG 裁切出的图片作为替代。

## 2. 子 Gate

| 子 Gate | 检查对象 | PASS 条件 | 缺失/失败行为 |
|---|---|---|---|
| G03A_SOURCE_AUTHORITY | 原始来源与授权 | 12 个槽位都有来源记录；用途允许提交仓库、打入 App 和用于 CI；隐私/肖像审核完成 | 缺来源或授权未决 → BLOCKED |
| G03B_SLOT_COMPLETENESS | Runtime Asset Manifest | Schema 有效；12 个指定 Slot ID 恰好各出现一次；没有未知槽位；每个槽位有语义合同 | 槽位缺失/重复/结构不一致 → FAIL；资源本身未找到 → BLOCKED |
| G03C_ANDROID_BINDING_AND_HASH | 源图与 Android 资源 | 源路径和 Android 资源路径存在；资源名可解析；文件可解码；V1 源/运行时 SHA-256 一致；资源绑定到对应 Slot ID | 声称已绑定但文件不存在、引用不存在或 SHA 不一致 → FAIL；尚无获批源 → BLOCKED |
| G03D_SEMANTIC_REVIEW | 鱼体方向及正确/错误语义 | 人工签核鱼体方向、完整性、鱼体占比、头部截断或遮挡注释；槽位语义与源图一致 | 未审核 → BLOCKED；实际语义与标签不符 → FAIL |
| G03E_RUNTIME_VISUAL | 真实 Android 运行时 | 在批准的真实 Runtime profile 截图中 12 个 Slot 均可见、非空、位置/比例符合冻结合同；无裁切破坏语义；证据绑定准确 SHA | 尚未运行 → NOT_RUN；实际资源缺失/错位/不可见 → FAIL；设备/源资产未准备 → BLOCKED |

## 3. 机器判定规则

最终状态按以下规则确定，不能用平均分或多数通过放行：

1. 若 Manifest 结构、Slot ID 数量、绑定路径或已声明资源存在性不符合合同，返回 FAIL。
2. 若任一必需源图缺失、来源/授权未批准或语义审核尚未完成，返回 BLOCKED。
3. 若源图和 Android 文件 SHA 不一致，或资源不可解码、方向错误、绑定到错误槽位，返回 FAIL。
4. 若所有资产 Gate 通过但真实 Android Runtime 尚未执行，返回 NOT_RUN，不得预先 PASS。
5. 只有 G03A–G03E 全部 PASS，且证据属于同一 source SHA、APK SHA、Manifest SHA、device profile 与 run ID，才允许 G03-ASSET-FAIL-CLOSED-V1=PASS。
6. 任一槽位失败/阻塞，整体 Guide Visual Gate 不得 PASS；不得以其他页面通过、平均 Pixel Diff 或其它槽位复用成功抵消。

状态含义：

- FAIL：已有实际校验或运行证据证明合同被违反。
- BLOCKED：必需来源、授权、资源、参数或环境尚未准备好，无法进行完整验收。
- NOT_RUN：前提已满足或尚未判定，但所需检查没有实际执行。
- PASS：所有必需断言及证据齐全并通过。

## 4. 下游传播

| 下游 Gate / 状态 | 当 G03 资产门禁非 PASS 时的规则 |
|---|---|
| FIRST_USE_GUIDE_VISUAL | 必须 BLOCKED（资产缺失/授权未决）或 FAIL（已验证实现错误）；不能 PASS |
| G13_BASE_CASES | 依赖首次指引视觉资源的基础用例标记 BLOCKED，不得记为 PASS；无关用例仍可独立执行 |
| G14_E2E | 包含 P01/P02/P03 的完整首次路径必须 BLOCKED；其他不依赖首次指引的路径可独立报告 |
| RECOGNITION_MAIN_FLOW_ACCEPTANCE | 不得 PASS |
| ANDROID_RELEASE_ACCEPTANCE / FULL_APP_RELEASE | 本 Gate 不能单独判定整个 Release；但如果 Release 合同要求此主流程通过，则必须阻断该 Release Gate |

禁止将“图片缺失”简单标记为 SKIPPED，因为这会丢失它对整体验收的阻断关系。逐条用例需记录 blocked_by=G03-ASSET-FAIL-CLOSED-V1。

## 5. Runtime 证据要求

资产前置条件满足后，未来验收至少采集：

- P01/P02/P03 各一张真实 Android Runtime 截图；不得用设计 PNG、Preview 或测试组件截图替代。
- 12 个槽位的运行时 testTag/语义树或 bounds 报告，证明 Image 实际挂载且可见。
- 对应源图和 Android 文件 SHA-256 清单。
- source_sha、APK SHA-256、AndroidTest APK SHA-256、模型版本/哈希、QA 用户 Manifest SHA、Fixture Manifest SHA、device/API/density/fontScale、workflow run ID。
- 参考图映射、关键区域 bounds、人工语义审查记录和必要的局部 Pixel Diff。
- 缺失资源、解码失败、绑定失败时的原始 Validator 输出；不允许吞掉异常或仅展示警告后继续。

Frozen 整页 PNG 是几何/排版参考，不是 Runtime 背景像素目标。CameraX 背景在真实设备上允许不同；比较时要验证标题、示范图槽位、文字层级、间距、安全区、按钮和指示器，不得要求动态相机背景像素与 Frozen 照片一致。

## 6. 后续 Validator 的最低合同

本阶段不实现脚本。后续实施时，Validator 必须以 runtime_asset_manifest_v1.json 为唯一输入主源，并至少做到：

1. 按 Schema 校验 Manifest。
2. 强制校验 12 个必需 Slot ID 的完整性和唯一性。
3. 校验每个获批源文件与 Android 资源路径存在、可解码、尺寸/方向字段完整。
4. 重新计算 SHA-256，并与 Manifest 中记录值比较；V1 默认要求源/Android 资源字节一致。
5. 拒绝 MISSING、CANDIDATE、NOT_REVIEWED、未授权、空路径、空资源名、null 绑定和未批准复用。
6. 输出逐槽位机器报告：slot_id / source_status / authorization_status / android_binding_status / hash_status / semantic_review_status / runtime_evidence_status / final_status / reason。
7. 任一必需槽位不通过时以非零退出码结束，并输出可追溯错误 ID。
8. 报告写入本次 RUN 的证据目录；不得覆盖旧运行证据。

建议未来的脚本名称：scripts/validate_recognition_guide_runtime_assets.py。该脚本当前不存在；本文件只定义其行为合同。

## 7. 当前真实状态

当前仓库有三张 Frozen 整页 PNG，SHA/尺寸已登记；但它们不是运行时独立示范照片。现有 Android 页面合同中 heroAsset = null，errorAssets = listOf(null, null, null)，并显式标记 ASSET_SOURCE_BLOCKED。因此当前状态应为：

- FROZEN_PAGE_REFERENCE=PASS
- RUNTIME_ASSET_MANIFEST=DEFINED
- RUNTIME_ASSET_SOURCES=BLOCKED
- ANDROID_ASSET_BINDING=NOT_READY
- FIRST_USE_GUIDE_VISUAL=BLOCKED
- ANDROID_RUNTIME_VISUAL_EVIDENCE=NOT_RUN

这些状态不得合并成一个笼统的“页面完成”。只有在后续获批独立图片、绑定和真实运行验收完成后，才可更新对应状态。


## 8. Existing test and validator interpretation (verified on main@0d3238e8)

The current `scripts/verify_first_use_camera_guide_assets.py` validates only the three Frozen full-page PNGs and their import report. It is not the 12-slot runtime asset validator required by this Gate.

The current Android test `RecognitionFirstCaptureGuideRuntimeTest#capturesComponentEvidenceAndReportsTheIndependentPhotoSourceBlocker` is a Compose component harness. Its evidence explicitly records `ASSET_SOURCE_BLOCKED`, `COMPONENT_INSTRUMENTATION_ONLY`, and `runtime_route_evidence=NOT_RUN`; it intentionally asserts that the blocker is reported. A green test result or `fish-guide-v1` CI job must not be interpreted as Guide Visual PASS. G03E remains NOT_RUN/BLOCKED until real IdentifyScreen + CameraX route evidence exists and all 12 images are approved and visible.
