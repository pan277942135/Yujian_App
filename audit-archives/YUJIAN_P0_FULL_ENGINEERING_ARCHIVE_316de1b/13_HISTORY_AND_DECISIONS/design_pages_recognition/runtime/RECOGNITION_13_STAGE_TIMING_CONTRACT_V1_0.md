# 渔见 AI 13 阶段识鱼 Runtime 时间合同 V1.0

**Contract ID:** `YUJIAN_RECOGNITION_13_STAGE_TIMING_6000MS_V1`  
**Product decision:** `FROZEN`  
**Decision date:** 2026-11-05  
**Applies to:** Android Recognition Runtime · 13-stage FULL path  
**Machine-readable authority:** [recognition_13_stage_timing_contract_v1_0.json](./recognition_13_stage_timing_contract_v1_0.json)  
**Implementation status at freeze:** `PENDING_RUNTIME_IMPLEMENTATION`

## 1. 冻结决定

正常 FULL 识鱼流程的名义最短视觉时长冻结为 **6000ms（6.0s）**。

本合同冻结的是 13 个主阶段的最短可见时间分配，不是强制 6000ms 截止计时器。全部阶段最短时间合计 6000ms；如果动画实际完成、模型门控或页面几何晚于最短时间，流程必须继续等待，不得压缩后续高光时刻。

### 时间语义

- 唯一时钟：现有共享 `SceneClock`，不得建立第二套时钟。
- T0：当前识别 Session 的照片稳定首帧（`PHOTO_STABLE first presented frame`）实际呈现时。
- 01 `PHOTO_STABLE` 是 T0 锚点，生产路径不额外增加阶段时长（0ms）；Debug MANUAL 仍可单步预览 3000ms。
- 6 秒是模型及时就绪时 FULL 路径的名义最短视觉序列。
- Classifier / SlimSAM 等真实模型门控未满足时，停留在合法等待状态；等待时间不计入 6000ms 阶段最短时间总和，也不能通过压缩后续阶段追回。
- 既有 60,000ms 模型看门狗保持不变；它是模型超时处理，不是动画总时长。
- 13.5 到达稳定结果页后持续展示，不在 6000ms 时自动消失或跳转。

## 2. 13 阶段冻结时间表

时间区间采用左闭右开 `[start_ms, end_ms)`。Stage 01 是时钟锚点；Stage 02 从 T0 开始计时。

| 阶段 | Frame ID | 状态 | 最短显示时间 | 起始时间 | 结束时间 | 累计结束 | 主要视觉重点 |
|---|---|---|---:|---:|---:|---:|---|
| 01 | 01 | PHOTO_STABLE | 0ms（T0 锚点） | 0 | 0 | 0 | 照片稳定，Orb 准备苏醒 |
| 02 | 02 | ORB_WAKE | 350ms | 0 | 350 | 350 | 水核、内部水体、焦散、水滴与水草苏醒 |
| 03 | 03 | ORB_RELEASE | 600ms | 350 | 950 | 950 | 五条 R06 光丝舒展及局部回收 |
| 04 | 04 | PERIMETER_FORM | 125ms | 950 | 1075 | 1075 | 照片四周水光形成，不提前覆盖整张照片 |
| 05 | 05 | FISH_SEEK | 125ms | 1075 | 1200 | 1200 | 1–2 条断续细金丝寻向真实鱼尾接触点 |
| 06 | 06 | CONTOUR_A | 450ms | 1200 | 1650 | 1650 | 从可信鱼尾锚点开始绘制轮廓 |
| 07 | 07 | CONTOUR_B | 450ms | 1650 | 2100 | 2100 | 蓝金交替渐变，沿真实鱼体边缘延伸 |
| 08 | 08 | CONTOUR_C | 450ms | 2100 | 2550 | 2550 | 真实轮廓闭合，水滴与线宽稳定 |
| 09 | 09 | FISH_GOLD_PEAK | 1000ms | 2550 | 3550 | 3550 | Core / Soft / Halo 三层金光形成、峰值保持与交接 |
| 10 | 10 | ORB_RETURN | 350ms | 3550 | 3900 | 3900 | 能量沿有效路径回到同一颗 Orb |
| 11 | 11.1–11.3 | ORB_TO_NAME | 850ms | 3900 | 4750 | 4750 | 同一水核舒展、液态转化为笔势 |
| 12 | 12.1–12.3 | SPECIES_NAME_REVEAL | 800ms | 4750 | 5550 | 5550 | 笔势聚合、真实中文鱼种名称形成并稳定 |
| 13 | 13.1–13.5 | RESIDUE_SETTLE | 450ms | 5550 | 6000 | 6000 | 名称保持、照片归位、结果信息交接、场景稳定 |
| | | **FULL 合计** | **6000ms** | **0** | **6000** | **6000** | **6.0 秒** |

## 3. 三个高光时刻的内部时间合同

### 3.1 Stage 09 — 鱼体金光（1000ms）

| 内部子阶段 | 时长 | Stage 09 相对区间 | 冻结要求 |
|---|---:|---:|---|
| 金光形成 | 300ms | 0–300ms | Core / Soft / Halo 从低强度自然增强 |
| 峰值保持 | **500ms** | 300–800ms | 三层金光完整、清晰、连续稳定，不闪烁、不提前退场 |
| 能量交接 | 200ms | 800–1000ms | 保留必要鱼体金光，平滑交给 Stage 10 Orb Return |
| **合计** | **1000ms** | **0–1000ms** | |

### 3.2 Stage 11 — Orb 舒展凝字（850ms）

| 子帧 | 时长 | Stage 11 相对区间 | 冻结要求 |
|---|---:|---:|---|
| 11.1 ORB_CORE_HOLD | 100ms | 0–100ms | 同一颗完整水核保持身份 |
| 11.2 ORB_HORIZONTAL_STRETCH | 350ms | 100–450ms | 水核自然横向舒展，保留液态玻璃质感 |
| 11.3 LIQUID_BRUSH_TRANSITION | 400ms | 450–850ms | 水核连续转化为流动笔势，不得突然消失或直接切换文字 |
| **合计** | **850ms** | **0–850ms** | |

### 3.3 Stage 12 — 鱼种名称揭晓（800ms）

| 子帧 | 时长 | Stage 12 相对区间 | 冻结要求 |
|---|---:|---:|---|
| 12.1 STROKE_AGGREGATION | 150ms | 0–150ms | 分散笔势聚合 |
| 12.2 GLYPH_EMERGENCE | 200ms | 150–350ms | 中文字形逐渐可辨认 |
| 12.3 SPECIES_NAME_FINAL | **450ms** | 350–800ms | 完整真实鱼种名称稳定展示，不得一闪而过 |
| **合计** | **800ms** | **0–800ms** | |

### 3.4 Stage 13 子帧

13.1–13.5 共用 Stage 13 的 450ms 父阶段时间。当前不冻结五个子帧各自的独立毫秒数；其局部进度由照片几何交接、结果数据到达和场景稳定事件驱动。不得擅自平均拆分 450ms。

## 4. 时间轴与入口动画

- 原有照片归位 `Photo Morph 700ms` 与 Camera Orb 入场 `900ms` 保持并行关系；不得串行相加为 1600ms。
- 两者属于拍摄页到 PHOTO_STABLE 的入口交接，不额外叠加到 6000ms。
- 识鱼 SceneClock 仍只从当前 PhotoSession 的照片稳定首帧实际呈现时启动。
- 不得因切换页面、暂停/恢复、窗口尺寸变化或 Debug 模式切换而创建第二时钟或重置识别 Session。

## 5. AUTO / MANUAL / 模型门控

### Release / AUTO

进入下一阶段必须同时满足：

1. 当前阶段最短可见时间已达到；
2. 当前阶段动画完成条件已满足；
3. 对应真实业务事件、模型终态及几何门控已满足。

阶段最短时间不是动画强制结束时间。实际动画较慢时允许延长；不允许为了赶 6000ms 压缩轮廓绘制、金光保持、凝字或名称揭晓。

### Debug / MANUAL

- 默认 MANUAL。
- 每个主阶段单步预览 3000ms。
- 单步结束自动暂停，等待用户明确点击下一步。
- 支持上一步、下一步、暂停和继续。
- MANUAL 的 3000ms 是诊断预览时间，不覆盖生产 6000ms 合同，也不计入 Release 时间。
- 切换 AUTO / MANUAL 不重置 PhotoSession、模型结果、Mask、Glyph 或共享 SceneClock。

### 模型等待与超时

- Classifier 与 SlimSAM 独立运行并按真实终态汇合。
- 模型未完成时保持当前合法等待阶段的低强度环境动效；不得伪造 Mask、鱼种名称或识别成功。
- 等待时间使实际墙钟耗时超过 6 秒是允许的；不得压缩后续阶段追回时间。
- 保留 60 秒看门狗及既有失败/降级处理。
- 旧 Attempt 的迟到结果不得污染当前 PhotoSession。

## 6. FULL 与 ORB_ONLY 分支

- **FULL：** 可信 Mask 可用，执行 01–13 全部阶段，名义最短视觉时长 6000ms。
- **ORB_ONLY：** Mask 失败或不可信时，不执行 06–09 的鱼体轮廓与金光；不伪造鱼体视觉，也不通过无意义等待补足 6000ms。继续复用 Orb Return、真实 Classifier 名称、Glyph 和 Result 路径。
- 两条路径共享唯一 Orb、WaterLight、SceneClock、Session 身份、SpeciesNameReveal 和 Result 页面。

## 7. 对既有 3650ms 合同的兼容说明

仓库原有 SWOS/RR09 15 帧设计合同（Processing 2800ms + RR09 850ms = 3650ms）保留为其原有 P0–P7 / R0–R5 静态光学样本、照片几何和 Web 历史验收参考。

**对当前 Android 13 阶段 Recognition Runtime 的端到端总时长，该 3650ms 不再具有时间权威。** 当前 Runtime 唯一时间权威是本合同的 6000ms 13 阶段表。不得把旧 15 帧样本时间直接叠加到 6000ms，也不得用旧 3650ms 覆盖本合同。

本说明不自动改写旧 Web 静态验收脚本及其 15 帧历史样本；如后续需要 Web 播放器也采用 6000ms，应另行明确迁移范围并同步其播放器合同与测试。

## 8. 参数变更记录与实施状态

| 项目 | 原 Runtime 最短值 | 新冻结值 |
|---|---:|---:|
| PHOTO_STABLE | 120ms | 0ms（T0 锚点） |
| ORB_WAKE | 350ms | 350ms |
| ORB_RELEASE | 600ms | 600ms |
| PERIMETER_ARRIVAL | 125ms | 125ms |
| PATH_TO_FISH | 125ms | 125ms |
| CONTOUR_A/B/C | 各 250ms | 各 450ms |
| FISH_GOLD_PEAK | 850ms | 1000ms |
| ORB_RETURN | 380ms | 350ms |
| ORB_TO_NAME | 170ms | 850ms |
| SPECIES_NAME_REVEAL | 130ms | 800ms |
| RESIDUE_SETTLE | 170ms | 450ms |

本合同已冻结产品时间分配，但**不代表 Android Runtime 已经同步修改或完成验收**。在本次合同登记时，现有 CP05 源码仍保留旧常量；后续 Work 必须按本合同更新唯一 SceneClock 的阶段最短时间和对应测试，并在报告中给出实际源码 SHA、测试结果与 APK 证据。

## 9. 验收要求

- 13 阶段时间总和严格等于 6000ms。
- Stage 09 的 500ms 峰值保持真实连续可见。
- Stage 11 的 850ms 形态转换连续。
- Stage 12 的最终中文名称至少稳定显示 450ms。
- Stage 13 的 450ms 交接结束后，结果页持续稳定。
- AUTO 不早于阶段最短时间，不绕过业务事件。
- MANUAL 每个主阶段 3000ms 后暂停，不自动推进。
- 模型等待不伪造完成、不压缩后续时间。
- FULL / ORB_ONLY 分支符合本合同。
- Pause/Resume、GeometryChanged、模式切换不重置 Session 或创建第二时钟。

**合同状态：FROZEN_PRODUCT_DECISION**  
**Runtime 实施状态：PENDING_RUNTIME_IMPLEMENTATION**  
**产品真机视觉验收：PENDING**
