# 04｜Orb-to-Glyph Rendering Contract V1

**Contract ID:** `YUJIAN_ORB_TO_GLYPH_RENDERING_CONTRACT_V1`\
**Priority:** P0 · 核心视觉基础

## 1. 目标

冻结从原始 AI Orb 到真实中文鱼种名称的连续渲染算法。必须发生可见的 Core 形变、Resident 能量释放、粒子迁移、真实 Glyph 几何形成和名称稳定；不得以 Orb 隐藏后 Text 淡入替代。

## 2. 输入与身份

只消费当前 Attempt 的不可变 RecognitionResultSnapshot、PhotoIdentity、ModelIdentity、SpeciesResult、GlyphPathSnapshot 和 Timeline SceneClock/MotionClock。

必须保持：`recognitionSessionId + attemptId + photoId + canonicalPixelSha256 + snapshotId + speciesKey + speciesName + glyphPathId + geometryVersion` 一致。禁止从固定字符串、历史 Attempt 或静态图片读取鱼种名称。

## 3. GlyphPathProvider

### 3.1 消除 Result UI 循环依赖

Glyph Path 必须由共享 `GlyphPathProvider` 产生，使用真实字体文件/字体身份和 Result typography token；不得要求 ResultScreen 已完成导航才能准备 Glyph Path。Processing 与 Result 必须消费同一 GlyphPathSnapshot。

可以使用真实文本布局引擎/字体 Path API（例如 TextLayout Path 或等效字体轮廓接口），但必须记录实际字体、字重、字号、字距、LayoutDirection、字符序列和坐标变换。不能用 TextView/Compose 普通文字淡入冒充 Path。

### 3.2 GlyphPathSnapshot

包含：glyphPathId、snapshotId、speciesKey、speciesName、fontIdentity/hash、typographyTokenVersion、characterCount、pathHash、glyphBounds、coordinateSpace、per-character contour/stroke metadata、createdAt。SpeciesResult 改变必须生成新 Attempt/Snapshot，不允许原地改 Glyph。

## 4. Orb 生命周期与分层解体

状态顺序：

`ORB_ACTIVE → ORB_RETURN_RECEIVED → CORE_STRETCH → HORIZONTAL_STRETCH → LAYER_DECONSTRUCTION → ENERGY_TRANSFER → GLYPH_EMERGENCE → GLYPH_AGGREGATION → NAME_STABLE`。

- ORB_RETURN 未真实完成，不得提前解体。
- Orb 必须保持原有体积、材质、核心结构和空间连续性；不是简单整体 Alpha 变 0。
- 核心、玻璃壳、内部水体、Resident、外围光效分层处理，层间有连续几何/材质变换。
- Orb 的原始中心、尺寸、Transform、MotionClock phase 和 Renderer Identity 必须跨 KF 连续。
- 禁止解体时突然换成另一颗独立 Orb 或静态文字贴图。

## 5. Resident 与 Task Energy

- G01/G02/A01/A02/C01 是五条 Orb Resident 常驻光丝，是 Orb 生命体的一部分。
- Resident 与 Photo Release、Perimeter、Fish Seek 等 Task Energy Path 严格分离。
- Task Energy 数量不冻结为五条，按视觉母版和真实任务决定。
- Task Energy 分流不得错误减少、替换或重编号 Resident。
- 在 Orb 解体阶段，Resident 可按冻结能量映射逐步释放/转化为粒子，但每条 Resident 的身份和能量来源必须可追踪；不得把 Resident ID 直接改成 Task Path ID。

## 6. 粒子源点与目标点

### 6.1 Source Points

Source Point 必须来自真实 Orb/Resident/能量结构的可见几何采样。每个 Source Group 记录 sourceId、residentId/energyLayerId、sourceGeometryVersion、sourceCoordinateSpace、pointSetHash 和采样方法。

禁止使用屏幕固定点、随机全屏散点或无 Orb 来源的粒子。

### 6.2 Target Points

Target Point 必须来自当前真实 GlyphPathSnapshot 的笔画/轮廓采样。记录 glyphPathId、characterIndex、contourIndex、strokeIndex、targetPointSetHash、coordinateSpace 和路径采样版本。

禁止固定中文、预设鱼名、静态 PNG、硬编码目标点阵。

### 6.3 Particle Assignment

每个粒子组必须有稳定 assignment：source group → target stroke/contour。允许以分组/批次记录而非逐粒子无限日志，但必须能复核数量、分配比例、路径、进度和 Hash。

## 7. Energy Migration

粒子迁移必须是连续、可见、时间驱动的空间运动：

- Source 到 Target 有明确路径和方向。
- 速度曲线连续，不突然跳点。
- 粒子带有尺寸/Alpha/能量衰减和局部轨迹。
- 迁移过程中 Orb Core 真实收缩/解构，Glyph 笔画同步聚合。
- 所有 Renderer 消费同一 SceneClock/MotionClock 和同一 SceneSnapshot。
- 不得在不同 Renderer 中各自重置动画时间。

必须记录 source/target identity、assignment、migration progress、renderer receipt、frame sequence 和失败原因。

## 8. 渲染状态与 Receipt

必须区分：

- `SPECIES_RESULT_READY`
- `GLYPH_PATH_PREPARED`
- `ORB_DECONSTRUCTION_STARTED`
- `ORB_DECONSTRUCTION_RENDERED`
- `ENERGY_MIGRATION_RENDERED`
- `GLYPH_REVEAL_RENDERED`
- `NAME_STABLE`

`onGlyphAvailable()` 或 speciesName 非空最多表示 SPECIES_RESULT_READY，不得直接置 Glyph READY。

Renderer Receipt 必须在真实绘制和有效 Frame Presented 后产生，包含 attemptId、snapshotId、rendererId/version、frameSequence、sceneClockMs、geometryVersion、glyphPathId、renderStatus、visibleBounds/coverage summary。Draw Submission 不能代替 Frame Presented。

## 9. NAME_STABLE

只有同时满足以下条件才可产生 NAME_STABLE Receipt：

1. SpeciesResult 来自当前 Snapshot 的真实 Classifier Top1。
2. GlyphPathSnapshot 的 speciesKey/name 与 Snapshot 完全一致。
3. Orb 解体/能量迁移/字形聚合均完成。
4. 真实 Glyph 已绘制并可见。
5. 名称布局和几何稳定，未再处于路径变形或字符生成状态。
6. 当前 Attempt/Photo/Snapshot/Geometry 身份一致。
7. 对应 KF20 画面证据可关联。

NAME_STABLE Receipt 是 Result Handoff 13.1 的输入，不是 Result Handoff 的替代。

## 10. 降级

- ORB_ONLY：无可信鱼体 Mask 时可使用受限 Orb→Glyph，但只能在真实 SpeciesResult 有效时显示真实鱼名；不得绘制虚假鱼体轮廓。
- CLASSIFICATION_FAILED：不生成任何鱼种名称 Glyph；显示明确失败/未知状态。
- GlyphPath 生成失败：记录 GLYPH_PATH_INVALID，禁止以普通文字淡入冒充成功。

## 11. Acceptance

- KF14 RETURN 完成后才开始解体。
- KF15–KF17 有连续真实 Core 形变和分层解体。
- Resident 能量来源可追踪且不与 Task Energy 混淆。
- 粒子 Source/Target 均有真实几何身份。
- KF18–KF19 真实 Glyph 逐步形成。
- KF20 NAME_STABLE 有真实 Frame/Pixel Evidence。
- 没有固定鱼名、静态替换、假路径或普通 Text 淡入。
