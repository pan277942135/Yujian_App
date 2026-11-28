# 02｜Recognition Result Snapshot Contract V1

**Contract ID:** `YUJIAN_RECOGNITION_RESULT_SNAPSHOT_CONTRACT_V1`\
**Priority:** P0 · 全局数据基础

## 1. 目标

冻结一次 Recognition Attempt 的唯一可信结果数据对象，使模型、Mask、Contour、Fish Path、Species、Glyph 和 Result 页面只能消费同一份已验证数据身份。Snapshot 是数据事实，不是 UI 状态容器，不随 Compose 重组或页面导航变化。

## 2. 身份对象

### 2.1 RecognitionSession / Attempt

- `recognitionSessionId`：一次用户识鱼流程。
- `attemptId`：一次具体识别尝试；重新识别/重试必须生成新 ID。
- `photoId`：标准化照片对象 ID。
- `snapshotId`：不可变结果快照 ID。
- `handoffId`：一次 Processing→Result 交接事务。

所有异步回调在写入前必须验证 session、attempt、photo 三重身份。旧 Attempt 的迟到回调只能被记录和丢弃，不得写入当前状态。

### 2.2 PhotoIdentity

必须同时保存：

- `sourceBytesSha256`：原始照片文件字节 SHA。
- `canonicalPixelSha256`：EXIF/方向归一化后的规范像素 SHA。
- `width/height`、方向归一化版本、photoContentVersion。

Detector、Classifier、Encoder、Mask、Contour、P*、FishPath 必须绑定同一个 canonicalPixelSha256。不得把文件 SHA 和 Bitmap 像素 SHA 都命名为 `photoSha256`。

### 2.3 ModelIdentity

Snapshot 保存 ModelBundleIdentity，并分别保存 Detector、Classifier、Vision Encoder、Mask Decoder 的 modelId/version/SHA/schema/preprocess identity。缺失模型身份不得以默认值填充。

### 2.4 MaskIdentity

```text
MaskIdentity = maskId + maskSha256 + photoId + canonicalPixelSha256
             + modelBundleIdentity + promptIdentity + maskVersion
             + geometryVersion + coordinateSpace + dimensions
```

maskId 必须稳定可复核；不得使用 Bitmap generationId 作为唯一持久身份。

### 2.5 ContourIdentity

包含 contourId、maskId、extractorVersion、coordinateSpace、geometryVersion、pointCount、closed、area/quality summary 和 contourHash。Contour 必须由当前 Attempt 的可信 Mask 派生。

## 3. FishPathSnapshot 与 ResultSnapshot 分离

### 3.1 RecognitionFishPathSnapshot

FishPathSnapshot 是几何/路径层不可变对象，包含：

- recognitionSessionId / attemptId / photoId / canonicalPixelSha256；
- maskId / maskSha256 / maskVersion；
- geometryVersion / coordinateSpace；
- P* source coordinate；
- fishPathId、path segments、perimeter bridge；
- returnPathId、returnPathSegments；
- path algorithm/version、创建事件和序号。

**创建时机：** Stage05 `PATH_TO_FISH` 的真实 Fish Path 已注册，Controller 接受 PATH_BOUND，SessionStore 也接受相同路径身份后，立即导出并冻结。Stage10 ORB_RETURN 只能消费该快照，禁止到 Stage10 才临时生成或重新计算 Path ID。

### 3.2 RecognitionResultSnapshot

ResultSnapshot 是一次 Attempt 的最终识别数据事实，包含：

- snapshotId、recognitionSessionId、attemptId；
- PhotoIdentity；
- ModelBundleIdentity；
- DetectorResult；
- Classifier SpeciesResult（speciesKey、speciesName、confidence、source model identity）；
- MaskOutcome 和可选 MaskIdentity；
- ContourOutcome 和可选 ContourIdentity；
- PStar/Geometry identity；
- 可选 FishPathSnapshotRef；
- recognitionOutcome；
- committedAtMonotonicNanos、schemaVersion、contentHash。

ResultSnapshot 不包含 Result 页面布局坐标、Hero Raster、PixelCopy、Glyph 动画进度或 Evidence 状态。上述内容属于后续独立 Receipt。

## 4. Snapshot 提交门控

Snapshot 只能在必要模型分支进入终态、分支策略确定、路径可用性确定后提交。

- `FULL_REVEAL`：Classifier 结果可接受；Mask 已终态且 TRUSTED；Contour 有效闭合；P* 有效；FishPathSnapshot 已被 Controller 和 Store 双方接受。
- `ORB_ONLY`：Classifier 结果可接受；Mask 已终态但 LOW/FAILED/TIMED_OUT/REJECTED；不得携带伪造的可信 Mask/Contour/FishPath；可携带明确的 Mask failure receipt 和独立 Orb-only path policy。
- `CLASSIFICATION_FAILED`：无有效 SpeciesResult；不得生成真实鱼种 Glyph 或显示固定名称。
- `FAILED/TIMED_OUT/CANCELLED`：记录终态与失败原因；不得构造看似成功的 ResultSnapshot。
- `MASK_PENDING` 不是终态，禁止提前判为 ORB_ONLY。

FishPathSnapshot 与 ResultSnapshot 的创建必须属于同一 Attempt。若分支是 ORB_ONLY，ResultSnapshot 中 FishPathRef 明确为空并带 `pathAvailability=NOT_APPLICABLE` 和原因。

## 5. 不可变性

- Snapshot Commit 后不可原地更新任何字段。
- 后续 UI 状态、Result Geometry、Glyph Receipt、Handoff ACK、PixelCopy 和 Evidence 通过 Snapshot ID 关联的独立对象记录。
- 如果模型结果被重新计算、Mask 被替换或照片内容变化，必须创建新的 Attempt 和 Snapshot ID。
- SessionStore 可管理 Snapshot 生命周期和读取索引，但不能在提交后修改 Snapshot 内容。
- JSON 序列化、哈希计算和读取必须保留 schemaVersion；不兼容升级不得静默改变字段语义。

## 6. 统一消费规则

- Timeline 读取 Snapshot 的 readiness/outcome，不修改 Snapshot。
- Orb-to-Glyph 只消费当前 Snapshot 的真实 SpeciesResult、PhotoIdentity 和 Geometry/Path refs。
- Result Handoff 传递同一个 Snapshot ID 和内容 Hash。
- Result Screen 不得重新识别、重建 Mask、重新生成 P* 或生成新的 FishPath ID。
- Evidence 只读取 Snapshot 和 receipts，记录哈希，不向 Snapshot 写入诊断状态。

## 7. 拒绝原因

至少包括：`SESSION_MISMATCH`、`ATTEMPT_MISMATCH`、`PHOTO_ID_MISMATCH`、`SOURCE_SHA_MISMATCH`、`CANONICAL_PIXEL_SHA_MISMATCH`、`MODEL_IDENTITY_MISMATCH`、`MASK_IDENTITY_MISMATCH`、`MASK_NOT_TERMINAL`、`MASK_NOT_TRUSTED`、`CONTOUR_INVALID`、`GEOMETRY_VERSION_MISMATCH`、`PSTAR_INVALID`、`PATH_ID_MISMATCH`、`RETURN_SEGMENT_INVALID`、`SNAPSHOT_ALREADY_COMMITTED`、`STALE_CALLBACK`。

拒绝必须记录 expected/actual identity 的安全摘要，不记录敏感大 Tensor。

## 8. Snapshot Acceptance

- 每个 Attempt 最多提交一个权威 ResultSnapshot。
- FULL_REVEAL 的 Mask→Contour→P*→FishPath 身份连续一致。
- ORB_ONLY 只有在 Mask 终态后才可确定。
- Stage05 已冻结的 FishPath 在 Stage10/Result 被原样消费。
- Result Geometry 不污染 Snapshot。
- 新照片/重试不会接收旧回调。
- Result 页面读取的 Species/Photo/Mask/Path 与 Snapshot 内容 Hash 一致。
