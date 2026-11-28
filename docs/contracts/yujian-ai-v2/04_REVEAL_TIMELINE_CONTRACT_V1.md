# 03｜Reveal Timeline Contract V1

**Contract ID:** `YUJIAN_REVEAL_TIMELINE_CONTRACT_V1`\
**Priority:** P0 · 视觉时间基础

## 1. 目标

冻结识鱼 13 个顶层 Stage、26 个视觉检查点、统一视觉时间、模型依赖等待、Stage/KF 事件和超时/降级语义。Timeline 负责调度与可观测性，不负责产生模型结果、Mask、Glyph 或 Result 数据。

## 2. 唯一编号与边界

- KF 编号唯一采用 `KF00–KF25`，不得再出现 KF01–KF26 的 1-based 版本。
- HOME 和 IDENTIFY 是前置入口，对应 KF00、KF01，不计入 13 个识鱼 Stage 的 6000ms。
- 13 Stage 从 KF02 `PHOTO_STABLE` 开始。
- KF25 `SAVE_CURRENT_CATCH` 是用户触发的保存动作，不计入 6000ms 自动识别视觉时间。

## 3. 13 Stage 与时长

| Stage | Stage ID | 最短主动呈现时长 | KF |
|---:|---|---:|---|
| 01 | PHOTO_STABLE | 0ms | KF02 |
| 02 | ORB_WAKE | 350ms | KF03 |
| 03 | ORB_RELEASE | 600ms | KF04–KF05 |
| 04 | PERIMETER_ARRIVAL | 125ms | KF06–KF07 |
| 05 | PATH_TO_FISH | 125ms | KF08–KF09 |
| 06 | CONTOUR_A | 450ms | KF10 |
| 07 | CONTOUR_B | 450ms | KF11 |
| 08 | CONTOUR_C | 450ms | KF12 |
| 09 | FISH_GOLD_PEAK | 1000ms | KF13 |
| 10 | ORB_RETURN | 350ms | KF14 |
| 11 | ORB_TO_NAME | 850ms | KF15–KF17 |
| 12 | GLYPH_REVEAL | 800ms | KF18–KF19 |
| 13 | RESIDUE_SETTLE / RESULT_HANDOFF | 450ms | KF20–KF24 |
| | **总计** | **6000ms** | |

PHOTO_STABLE 的 0ms 是时钟起点，不是无效阶段。上述时长是冻结的主动呈现预算；不得为了掩盖模型慢而压缩、跳过或重排。

## 4. 26KF 唯一语义

| KF | 语义 | 约束 |
|---|---|---|
| KF00 | HOME | 真实首页 UI |
| KF01 | IDENTIFY | 真实拍照/取景入口 |
| KF02 | PHOTO_STABLE | 同一 PhotoIdentity 的首个稳定呈现帧 |
| KF03 | ORB_WAKE | Orb 真实唤醒 |
| KF04 | ORB_RELEASE 起始 | 能量从 Orb 真实边缘释放 |
| KF05 | ORB_RELEASE 传播 | 能量传播连续 |
| KF06 | PERIMETER_ARRIVAL | 到达真实 Photo Perimeter |
| KF07 | PERIMETER_COVERAGE | 外围水光覆盖/流动检查点 |
| KF08 | PATH_TO_FISH 起始 | 真实 FishPath 目标 |
| KF09 | PATH_TO_FISH 抵达 | 抵达可信 P* |
| KF10 | CONTOUR_A | 真实 Mask 轮廓初现 |
| KF11 | CONTOUR_B | 连续延展 |
| KF12 | CONTOUR_C | 闭合完成 |
| KF13 | FISH_GOLD_PEAK | 金光沿真实轮廓峰值 |
| KF14 | ORB_RETURN | 原路径回流 |
| KF15 | WATER_CORE_STRETCH | 水核形变开始 |
| KF16 | HORIZONTAL_STRETCH | 横向舒展 |
| KF17 | WATERLIGHT_DECONSTRUCTION | Orb 分层解体/能量转化 |
| KF18 | GLYPH_EMERGENCE | 真实字形开始显现 |
| KF19 | SPECIES_NAME_FINAL | 真实鱼种名称完整显现 |
| KF20 | NAME_STABLE | 名称稳定回执；Stage13.1 |
| KF21 | PHOTO_SETTLE | Photo 归位；Stage13.2 |
| KF22 | RESULT_SCENE_HANDOFF | Result Scene 接管；Stage13.3 |
| KF23 | RESULT_INFO_ARRIVAL | 真实结果信息抵达；Stage13.4 |
| KF24 | RESULT_SCENE_STABLE | 最终场景稳定；Stage13.5 |
| KF25 | SAVE_CURRENT_CATCH | 用户真实触发保存后才适用 |

KF 是视觉/业务检查点，不要求每个 KF 都成为独立状态机。每次运行必须为 26 个 ID 输出状态：`CAPTURED`、`CAPTURE_FAILED`、`NOT_REACHED`、`NOT_APPLICABLE`。KF25 未触发保存时必须 NOT_APPLICABLE。

## 5. 三时钟统一合同

### 5.1 ModelClock

每个模型 Run 独立记录真实推理时间；不驱动 Stage 进度，不计入 6000ms。

### 5.2 SceneClock

唯一统一的 Stage/KF 呈现时钟，起点为 `PHOTO_FIRST_STABLE_FRAME_PRESENTED`。所有 Stage Controller、Orb、WaterLight、Glyph 和 Result 动画消费同一 SceneClock，不得自行启动局部 Timer。

- 6000ms 是主动视觉呈现预算。
- 在等待必要模型终态、可信 Mask/Path、Result Geometry 或 Handoff Receipt 的明确 Gate 上，SceneClock 可进入 WAITING/PAUSED；等待时长单独记录，不计入主动呈现预算。
- Gate 解除后继续剩余主动预算；不得把等待时间伪装成 Stage 已完成。

### 5.3 MotionClock

统一连续单调运动时钟，Orb 和 WaterLight 在模型/几何等待期间仍保持合理运动。所有 Renderer 共用同一 MotionClock；不得各自重置。MotionClock 与 SceneClock 同源或可建立单调相关关系，但语义不同。

### 5.4 60s 模型等待

单次 Attempt 的必要模型依赖等待上限为 60,000ms。到达 deadline 后请求取消仍运行的模型，等待 Native 实际返回或记录 NATIVE_CALL_STUCK；依据 Snapshot Contract 选择合法失败/降级。60s 不是视觉时间，也不是正常推理目标。

## 6. Stage 状态机

每个 Stage 必须经历：`NOT_REACHED → READY → ENTERED → ACTIVE/WAITING → COMPLETED`，或 `FAILED/TIMED_OUT/DEGRADED/BLOCKED_BY_UPSTREAM_FAILURE`。

每个 Stage 必须有：

- Enter Condition；
- Required Input Identity；
- Producer Event；
- Consumer Acceptance；
- Minimum Active Duration；
- Complete Condition；
- Timeout/Failure/Degrade Reason；
- KF references；
- Render/Pixel evidence reference。

控制器退出、进度达到 1.0、Compose 重组、Renderer READY、Draw Submission 均不等于 Stage 完成。

## 7. 关键 Stage Gate

- Stage05 PATH_TO_FISH：只有可信 Mask/Contour/P* 和真实 FishPath 才可 FULL_REVEAL；FishPathSnapshot 在 PATH_BOUND 被 Controller 和 Store 接受后冻结。
- Stage06–09：依赖同一 MaskIdentity/ContourIdentity；不可信 Mask 不得画真实鱼体轮廓或金光。
- Stage10 ORB_RETURN：FULL_REVEAL 消费原始 FishPathSnapshot；ORB_ONLY 使用显式独立 Orb-only return，不伪装鱼体回流。
- Stage11 ORB_TO_NAME：要求真实 SpeciesResult 和真实 GlyphPathSnapshot 已准备；不要求 Result 页面已经接管。
- Stage12 GLYPH_REVEAL：必须有实际 Orb/Glyph Render Receipt；KF20 NAME_STABLE 才进入 Stage13.1。
- Stage13：按 13.1–13.5 顺序完成 Result Handoff；Geometry 可预先解析，但可见 Scene 所有权只能在 13.3 Consumer ACK 后转移。

## 8. 13.1–13.5 与 Stage13 的关系

13.1–13.5 是 Stage13 的有序子阶段，不是新增顶层 Stage，不拥有独立时钟，不增加五段额外固定时长：

1. 13.1 NAME_STABLE（KF20）
2. 13.2 PHOTO_SETTLE（KF21）
3. 13.3 RESULT_SCENE_HANDOFF（KF22）
4. 13.4 RESULT_INFO_ARRIVAL（KF23）
5. 13.5 RESULT_SCENE_STABLE（KF24）

Stage13 只有在五个子阶段均有真实完成 Receipt、Consumer Acceptance 和身份校验后才能完成。未满足条件时停留在明确 WAITING/BLOCKED 状态，不得强制完成或增加 Stage 数量。

## 9. 降级与失败

- `FULL_REVEAL`：真实分类 + TRUSTED Mask + 有效 Contour/P*/FishPath。
- `ORB_ONLY`：Classifier 可接受且 Mask 已终态但不可信；不画鱼体轮廓/金光，不伪造 FishPath。
- `CLASSIFICATION_FAILED`：不显示固定鱼名、不生成虚假 Glyph。
- `TIMEOUT/CANCELLED`：停止当前 Attempt 的结果提交；迟到结果隔离。

## 10. Timeline Acceptance

- 13 Stage 有序、无跳跃、无重复完成。
- KF00–KF25 有完整状态矩阵。
- 6000ms 主动视觉预算和所有等待时长分别可核验。
- 所有 Renderer 共用统一 SceneClock/MotionClock。
- 13.1–13.5 顺序完整且归属于 Stage13。
- 无模型终态不得进入 FULL_REVEAL。
- KF25 只在真实保存操作后 CAPTURED。
