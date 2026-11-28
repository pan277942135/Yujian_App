# YuJian AI V2｜六大核心合同交叉一致性审查 V1.0

**Review ID:** `YUJIAN_AI_V2_SIX_CONTRACTS_CROSS_REVIEW_V1`\
**Review date:** 2026-11-24\
**Result:** `CROSS_CONTRACT_REVIEW_PASS`\
**Scope:** 规范、数据接口、事件语义、时钟、生命周期、渲染所有权、证据与验收

## 1. 审查范围与证据边界

本次审查使用：

1. 用户在本轮对六份合同明确的职责和核心问题范围。
2. 本对话中完整可见的 Runtime Evidence Contract 候选稿。
3. Library 中的 `AI_26KF_P0_Visual_Contract_V1.md`，作为 KF 语义和 13 阶段产品输入依据。
4. `YuJian_AI_Unified_Architecture_and_Motion_Spec_V2.xlsx`，作为分层架构、三时钟、模型分支、资源所有权和事件协议的既有架构依据。
5. `YuJian_26KF_真机逐帧对照审计_V1.xlsx`，作为历史真机运行证据，不作为覆盖冻结产品合同的规范来源。
6. 既有 Result Handoff Work、Orb/Glyph Work、Stage10–13.5 修复 Work 和 WP1 Identity Consistency Matrix，作为历史实现路径与已知断链证据。

**边界说明：** Library 中未找到六份以本次六个 Contract ID 分别独立归档的完整原始合同文件。因此，本次不是对六份独立原稿逐段逐字的 redline 审查；本次对用户明确的六个合同范围、当前可恢复的规范正文、既有架构表和工程 Work 进行规则级交叉审查，并形成一套统一、可替代此前候选稿的规范 V1.0。仓库源码符合性仍须在 CodeX Work 中按最新 HEAD 逐条映射。

## 2. 总体审查结论

六个合同可以形成一致架构，但必须采用本报告的统一裁决。裁决后，合同职责不重叠、核心身份不冲突、时间与模型等待隔离、Orb-to-Glyph 与 Result Handoff 不形成循环依赖，Evidence 不反向控制业务。

**通过范围：** 规范一致性与接口定义。\
**未通过/未执行范围：** 当前源码符合性、单元测试、设备运行、视觉验收、APK 交付。

## 3. 冲突与裁决记录

| ID | 原有冲突/风险 | 统一裁决 | 影响合同 |
|---|---|---|---|
| C01 | KF01–KF26 与 KF00–KF25 两套编号 | 唯一编号为 KF00–KF25；不再使用 1-based KF 编号 | Timeline / Evidence |
| C02 | HOME/IDENTIFY 被混入 13 个 6000ms 阶段 | HOME、IDENTIFY 是前置入口；13 阶段从 PHOTO_STABLE 开始 | Timeline |
| C03 | 13.1–13.5 被误认为新增五个顶层阶段 | 13.1–13.5 是 Stage13 的五个有序子阶段；不得增加 Stage14–18 | Timeline / Handoff |
| C04 | 6000ms 与 60s 模型等待混用 | 6000ms 是主动视觉呈现时长预算；模型等待单独计时，最多 60s | Runtime / Timeline |
| C05 | 单一 Scene Clock 与旧三时钟规范冲突 | 使用 ModelClock、统一 SceneClock、统一 MotionClock；所有 Renderer 共用同一 SceneClock/MotionClock，不得各自计时 | Runtime / Timeline / Orb-Glyph |
| C06 | Result Snapshot 与 Fish Path Snapshot 混为一体 | 两者是不同不可变对象；FishPath 在 Stage05 PATH_BOUND 后冻结，ResultSnapshot 在模型分支和 Path 可用性确定后提交 | Snapshot / Handoff |
| C07 | Stage10 才导出 FishPath，导致 RETURN 无快照 | FishPathSnapshot 必须在 Stage05 Controller/Store 均接受 PATH_BOUND 后产生；Stage10 只消费，不再创建 | Snapshot / Timeline / Handoff |
| C08 | `sessionId` 同时指业务 Session、ORT Session、Evidence Session | 分别命名 recognitionSessionId、attemptId、modelSessionId、inferenceId、evidenceSessionId | 全部 |
| C09 | imageId/photoId、JPEG 文件 SHA 与 Bitmap Pixel SHA 不一致 | PhotoIdentity 同时保存 sourceBytesSha256 与 canonicalPixelSha256；模型、Mask、几何绑定 canonicalPixelSha256 | Runtime / Snapshot / Evidence |
| C10 | 缺少稳定 maskId，使用 Bitmap generationId 充当身份 | MaskIdentity 由原始 Mask SHA、PhotoIdentity、ModelIdentity、预处理/几何版本组成；generationId 只能作缓存提示 | Snapshot / Evidence |
| C11 | Glyph Path 依赖 ResultScreen onTextLayout，可能形成 Result 导航循环 | 使用共享 GlyphPathProvider，基于真实字体和当前分类名称预先生成 GlyphGeometry；Processing 和 Result 消费同一 GlyphPathSnapshot | Orb-Glyph / Handoff |
| C12 | `onGlyphAvailable` 或非空 speciesName 被当作 Glyph READY | 分离 SPECIES_RESULT_READY、GLYPH_PATH_PREPARED、ORB_TO_GLYPH_RENDERED、GLYPH_REVEAL_RENDERED、NAME_STABLE | Snapshot / Orb-Glyph / Handoff |
| C13 | Result Geometry 被写入/等待在识别 Snapshot | Snapshot 只保存源照片/模型/Mask/Path 身份；ResultGeometryReceipt 在 Result Host 预挂载和真实布局后独立生成 | Snapshot / Handoff |
| C14 | Result Host 准备与 Result Scene 接管混为一谈 | Result Host 可提前挂载测量 Geometry，但只有 13.3 ACK 后才转移可见 Scene 所有权 | Handoff / Timeline |
| C15 | Resident 五条与 Task Energy 数量混淆 | G01/G02/A01/A02/C01 是五条 Orb Resident；Task Energy 是独立路径集合，数量由冻结视觉资产和真实业务任务决定 | Orb-Glyph / Timeline |
| C16 | Native terminate 请求被当作推理终止 | 必须分别记录 CANCEL_REQUESTED、TERMINATION_REQUESTED、NATIVE_CALL_RETURNED、SESSION_CLOSED、RESOURCE_RELEASED | Runtime / Evidence |
| C17 | 同进程无法安全强杀卡住的 Native 算子 | 取消是协作式；未返回时标记 NATIVE_CALL_STUCK、隔离 Session/Attempt，不得提前释放仍被 Native 使用的 Buffer；硬终止需进程隔离或进程重启策略 | Runtime / Evidence |
| C18 | ORT Operator Profile 被当成 Native Allocation 归因 | Operator 耗时与 Native Allocation 是两种证据；只有带 allocator/call-stack 关联才可标注 operator allocation attribution | Runtime / Evidence |
| C19 | Draw Submission 被当作画面完成 | 分离 DRAW_SUBMITTED、FRAME_PRESENTED、PIXELCOPY_CAPTURED、VISUAL_VALIDATED | Timeline / Orb-Glyph / Evidence |
| C20 | PixelCopy/视频缺失时仍可能报告视觉 PASS | 缺失证据标记 INCOMPLETE/UNAVAILABLE；不得用前一帧、静态图、Preview 或 Demo 补位 | Evidence |
| C21 | 26KF 每次都必须强制 26 张截图，与 KF25 条件触发冲突 | 始终输出 26 行状态矩阵；适用帧须真实采集；KF25 未触发时 NOT_APPLICABLE，未到达时 NOT_REACHED | Timeline / Evidence |
| C22 | AUTO Smoke、视觉验收、Full Flow 合并为一个 PASS | 分开 BUILD、UNIT、MODEL、AUTO_SMOKE、PIXELCOPY、VIDEO、VISUAL_AUTO、EVIDENCE_ARCHIVE、FULL_FLOW | Evidence |
| C23 | Evidence Recorder 可能推动业务状态或补齐事件 | Evidence 只读订阅生产事件；不得调用业务 transition、补造 ACK、修改 Snapshot | Evidence / 全部 |
| C24 | 失败日志只报最后失败，不报首个失败 | 以 FIRST_FAILURE 为主根因；下游标记 BLOCKED_BY_UPSTREAM_FAILURE | Evidence / 全部 |

## 4. 统一身份模型

禁止继续用含义不明的 `sessionId` 作为所有系统的通用 ID。

| 名称 | 生命周期/含义 | 创建者 |
|---|---|---|
| `recognitionSessionId` | 一次用户识鱼流程会话 | App Recognition Coordinator |
| `attemptId` | 一次具体照片识别尝试；重试必须新建 | Recognition Attempt Coordinator |
| `photoId` | 当前标准化照片对象身份 | Photo Store |
| `modelSessionId` | ORT/TFLite Session 实例身份，可跨 Attempt 复用 | Model Runtime Manager |
| `inferenceId` | 一次模型 Run | Model Runtime |
| `maskId` | 一份确定来源和内容的 Mask 身份 | Mask Result Producer |
| `contourId` | 从 Mask 派生的确定轮廓身份 | Contour Extractor |
| `fishPathSnapshotId` | 冻结的 Fish Path 证据身份 | Path Ledger/Controller |
| `snapshotId` | 不可变 RecognitionResultSnapshot 身份 | Snapshot Committer |
| `handoffId` | Processing→Result 一次交接事务 | Handoff Producer |
| `evidenceSessionId` | 一次诊断采集会话 | Evidence Coordinator |

所有跨层事件必须携带 `recognitionSessionId + attemptId + photoId`；涉及模型、Mask、路径、Snapshot、Handoff 时必须再携带对应身份。

## 5. 统一 Photo / Model / Mask 身份

### PhotoIdentity

```json
{
  "photoId": "uuid",
  "sourceBytesSha256": "hex",
  "canonicalPixelSha256": "hex",
  "width": 0,
  "height": 0,
  "sourceOrientation": "EXIF_OR_CAMERA_NORMALIZED",
  "normalizationVersion": "string",
  "photoContentVersion": 1
}
```

- `sourceBytesSha256` 追踪原始文件字节。
- `canonicalPixelSha256` 标识 EXIF/方向归一化后的模型输入像素内容。
- 模型、Mask、Contour、P* 和 FishPath 必须绑定 canonicalPixelSha256。
- 不允许一个模块用 JPEG 文件 SHA、另一个模块用 Bitmap Pixel SHA，却都写成同一个 `photoSha256` 字段。

### ModelIdentity

使用 ModelBundleIdentity + 每个模型独立 ModelIdentity。至少包含 modelId、version、SHA256、input/output schema hash、preprocessVersion、runtime/provider/version。Detector、Classifier、Vision Encoder、Mask Decoder 分别记录，禁止用一个 `modelVersion` 代替四个模型身份。

### MaskIdentity

Mask 必须绑定 photoId、canonicalPixelSha256、model identity、prompt/input identity、maskSha256、mask dimensions、coordinateSpace、maskVersion、geometryVersion。Bitmap generationId 仅可用于本地缓存失效，不是持久身份。

## 6. 唯一职责与依赖方向

```text
Recognition Runtime
        ↓ produces
Recognition Result Snapshot + FishPathSnapshot
        ↓ consumed by
Reveal Timeline / Orb-to-Glyph / Result Handoff
        ↓ produce receipts
Result Handoff Commit

Runtime Evidence ──read-only observes all layers──► Independent Validator
```

禁止反向依赖：

- Runtime 不依赖 Renderer、Result UI 或 Evidence。
- Snapshot 不依赖 Timeline、Renderer、Result Geometry 或 Evidence。
- Timeline 不负责模型推理、Mask 生成或页面导航。
- Orb-to-Glyph 不负责决定鱼种；只消费 Snapshot 中的真实 SpeciesResult。
- Result Handoff 不重算 Model/Mask/FishPath；只验证和交接。
- Evidence 不得成为任何业务 Gate 的 Producer。

## 7. 最终统一验收语义

`FULL_FLOW_ACCEPTED` 必须建立在同一 APK/Model/Photo/Attempt/Snapshot 身份上，并完成真实识别、13 Stage、适用 KF、Orb-to-Glyph、13.1–13.5、Result Stable 和独立证据验证。BUILD PASS、Unit PASS、CI PASS、Renderer READY 或 Navigation Requested 均不能单独推出 FULL_FLOW_ACCEPTED。

## 8. 冻结判定

本次规范层交叉审查通过。上述裁决已写入六份合同和统一 Registry。源码映射与实现验收是下一阶段独立 Work，不属于本次规范冻结 PASS 的含义。
