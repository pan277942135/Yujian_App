# 05｜Result Handoff Contract V1

**Contract ID:** `YUJIAN_RESULT_HANDOFF_CONTRACT_V1`\
**Priority:** P0 · 最终交接基础

## 1. 目标

冻结 Recognition Result Snapshot 从 Processing 场景进入 Result Scene 的完整交接，覆盖 ORB_RETURN、ORB_TO_NAME、NAME_STABLE、Photo Settle、Result Geometry、Scene Handoff 和 13.1–13.5。

## 2. 输入

唯一业务数据输入是已提交的 RecognitionResultSnapshot。FULL_REVEAL 还必须引用原始 RecognitionFishPathSnapshot；ORB_ONLY 必须携带明确降级结果。禁止 Result Screen 重新推理、重算 Mask/P*、重建 FishPath ID 或替换照片。

## 3. ORB_RETURN

- FULL_REVEAL 必须消费 Stage05 冻结的原始 FishPathSnapshot。
- RETURN pathId、segments、P*、Mask SHA/version、Geometry Version 必须与原始 PathSnapshot 一致。
- Controller 真实接受 RETURN Event 后才能标记 `orbReturnComplete=true`。
- 禁止使用默认 `fish-to-original-orb` 等新路径 ID 冒充原路径。
- ORB_ONLY 使用显式独立 Orb-only return，Evidence 必须标记降级，不得冒充 FULL_REVEAL。

## 4. ORB_TO_NAME / NAME_STABLE

- ORB_TO_NAME 由 Orb-to-Glyph Contract 驱动。
- Result Handoff 不自行创建 Glyph Path，也不将 speciesName 非空当作 Glyph 完成。
- 13.1 NAME_STABLE 必须消费 Orb-to-Glyph 产生的真实 NameStableReceipt，并核对 Snapshot/Species/Glyph identity。

## 5. Result Geometry

### 5.1 Geometry Receipt 独立于 Snapshot

ResultGeometryReceipt 由 Result Host 实际测量产生，绑定 snapshotId、attemptId、photoId、geometryVersion、windowInsets、density、layoutDirection、rootBounds、heroBounds、titleBounds、infoBounds、coordinateSpace、transform matrix 和 measurement timestamp。

GeometryReceipt 不得写回已提交 RecognitionResultSnapshot。

### 5.2 分离几何与 Raster

必须区分：

- Layout Geometry READY；
- Hero Raster/Bitmap READY；
- GPU Render Geometry/Receipt；
- PixelCopy Screen Geometry。

不得因 Hero Raster 异步加载而错误阻塞已有效的布局几何；也不得把布局 Bounds 当作实际屏幕像素证据。

### 5.3 Host Pre-mount

Result Host 可以在 13.2 Photo Settle 前进行预挂载/测量，以产生 GeometryReceipt；这不等于可见 Scene 所有权已转移。该机制用于消除“必须先完成 Result Handoff 才能测量 Geometry”的循环依赖。

## 6. Photo Settle

13.2 PHOTO_SETTLE 必须使用同一 PhotoIdentity，从 Processing Source Geometry 连续变换到 Result Hero Target Geometry。

记录 source/target transform、crop、bounds、progress、geometryVersion、photoId、canonicalPixelSha256、start/end timestamp 和实际完成回执。

不得只因 progress=1.0 就判定完成；必须核验最终目标几何和真实呈现。禁止换图、重编码替换原图或改变 PhotoIdentity。

## 7. 13.1–13.5 顺序

| 子阶段 | ID | 输入 | 完成条件 | 对应 KF |
|---|---|---|---|---|
| 13.1 | NAME_STABLE | NameStableReceipt | 真实 Glyph/鱼种名称稳定且身份一致 | KF20 |
| 13.2 | PHOTO_SETTLE | ResultGeometryReceipt + PhotoIdentity | 同一照片完成归位，真实目标几何成立 | KF21 |
| 13.3 | RESULT_SCENE_HANDOFF | HandoffEnvelope | Result Host 校验身份并 ACK，Scene Owner 原子转移 | KF22 |
| 13.4 | RESULT_INFO_ARRIVAL | Snapshot Species/Result Data | 真实结果信息完成布局/可见性确认 | KF23 |
| 13.5 | RESULT_SCENE_STABLE | 前四阶段 Receipts + Geometry | 照片、Glyph、信息、布局和场景稳定 | KF24 |

顺序严格为 13.1→13.2→13.3→13.4→13.5。每个子阶段必须有 Producer、Consumer、事件序号、单调时间戳、身份校验、接受/拒绝原因和 Receipt。不得新增独立 Timer 或把五个子阶段扩成 Stage14–18。

## 8. Handoff Envelope / ACK

HandoffEnvelope 至少包含：handoffId、recognitionSessionId、attemptId、photoIdentity、snapshotId、snapshotContentHash、modelBundleIdentity、recognitionOutcome、mask/contour/path refs、speciesResult、nameStableReceiptRef、photoGeometrySource、geometryReceiptRef、timelineStage/substage、eventSequence、createdAt。

Result Consumer 必须校验：

1. Session/Attempt/Photo/Snapshot 一致；
2. Snapshot Hash 有效；
3. Species 与 Glyph 一致；
4. FULL_REVEAL 的 Mask/Contour/FishPath identity 一致；
5. GeometryReceipt 有效且坐标系一致；
6. 事件顺序和幂等键未被消费。

Consumer 真实接受后生成 `ResultHandoffAck`。Navigation Requested、ResultScreen Composed 或 Host Mounted 都不等于 ACK。

## 9. Scene Ownership

- 13.3 之前 Result Host 可以预挂载/测量，但 Processing Scene 仍是可见 Owner。
- 13.3 Consumer ACK 后执行原子可见 Scene Owner 转移。
- 不能同时由 Processing 和 Result 两个 Host 重复绘制同一 Photo/Glyph/Orb 层。
- 转移失败时保留原 Scene 或进入明确错误回退；不得显示静态 Result 后宣称交接成功。

## 10. Result Info 与 Stable

13.4 只展示 Snapshot 中存在的真实数据；长度、重量、地点等缺失字段必须保持缺省，不能编造占位数据冒充识别结果。

13.5 必须满足：13.1–13.4 已接受；同一 Snapshot/Photo/Species/Glyph；Geometry 有效；Result Layout 完成；关键内容真实可见；无未完成 P0 渲染事件；稳定证据可关联。只有 13.5 Consumer 接受后才允许 `resultSceneStable=true` 和 Playback COMPLETED。

## 11. Failure Codes

至少包括：`SNAPSHOT_MISSING`、`HANDOFF_IDENTITY_MISMATCH`、`FISH_PATH_SNAPSHOT_MISSING`、`RETURN_PATH_ID_MISMATCH`、`RETURN_PATH_GEOMETRY_INVALID`、`RESULT_GEOMETRY_PENDING`、`RESULT_GEOMETRY_INVALID`、`HERO_RASTER_PENDING`、`PHOTO_IDENTITY_CHANGED`、`PHOTO_SETTLE_FAILED`、`RESULT_HOST_NOT_READY`、`CONSUMER_ACK_MISSING`、`DUPLICATE_HANDOFF`、`RESULT_INFO_ARRIVAL_MISSING`、`RESULT_SCENE_NOT_STABLE`。

## 12. Acceptance

13.1–13.5 全部按序产生并被真实消费；Snapshot 不变；Result Geometry 独立且有效；Photo Settle 连续；Handoff ACK/Owner 转移真实发生；Result 信息来自真实 Snapshot；KF20–KF24 对应真实证据；静态 fallback 不得标记 PASS。
