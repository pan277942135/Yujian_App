# Gate Deadline Policy V1 验收报告

Policy ID：YUJIAN_NON_MODEL_GATE_DEADLINE_POLICY_V1  
版本：1.0.0  状态：FROZEN_SPECIFICATION  
规范文件 SHA256：49b3bf8de8244a169d7779647cd24c08063d3427d62de95f5d139fe932b08d93

## 全局预算

| 参数 | 冻结值 |
|---|---:|
| ACTIVE_PRESENTATION_BUDGET_MS | 6,000 |
| MODEL_ATTEMPT_DEADLINE_MS | 60,000 |
| MAX_SINGLE_NON_MODEL_GATE_MS | 3,000 |
| MAX_NON_MODEL_BLOCKED_WAIT_PER_ATTEMPT_MS | 10,000 |
| PIXELCOPY_CAPTURE_TIMEOUT_MS | 1,500 |

## Gate 表

Max Retries 不包括首次请求。累计阻塞上限与单 Gate 超时相同；PixelCopy 的业务累计为 0，且不计入 Attempt 预算。

| Gate ID | Timeout ms | Max Retries | 单 Gate 累计 ms | 起始事件 | 计入 Attempt |
|---|---:|---:|---:|---|---|
| PHOTO_FIRST_FRAME | 3000 | 2 | 3000 | PHOTO_STABLE 首帧请求 | Yes |
| PROCESSING_GEOMETRY | 2000 | 1 | 2000 | 照片归一化完成并请求处理区几何 | Yes |
| ORB_ANCHOR | 1500 | 1 | 1500 | ORB_WAKE 进入 | Yes |
| RELEASE_RENDER | 1500 | 2 | 1500 | ORB_RELEASE 进入 | Yes |
| PERIMETER_ARRIVAL_RENDER | 1500 | 2 | 1500 | PERIMETER_ARRIVAL 进入 | Yes |
| REVEAL_DECISION | 1000 | 0 | 1000 | 所有必要模型结果终态 | Yes |
| MASK_CONTOUR_PSTAR | 2000 | 0 | 2000 | Mask 回调终态 | Yes |
| PATH_BOUND | 2000 | 1 | 2000 | FishPath 双端绑定请求 | Yes |
| ORB_ONLY_RETURN_PATH | 1500 | 1 | 1500 | ORB_ONLY 分支确认 | Yes |
| CONTOUR_RENDER | 1500 | 2 | 1500 | KF10/KF11/KF12 渲染请求 | Yes |
| FISH_GOLD_RENDER | 1500 | 2 | 1500 | KF13 进入 | Yes |
| RETURN_RENDER | 2000 | 2 | 2000 | KF14 / ORB_RETURN 进入 | Yes |
| GLYPH_PATH | 2500 | 1 | 2500 | 有效 SpeciesResult 后请求 GlyphPath | Yes |
| ORB_TO_GLYPH_RENDER | 2000 | 2 | 2000 | KF15 进入 | Yes |
| GLYPH_REVEAL_NAME_STABLE | 2000 | 2 | 2000 | KF18 进入 | Yes |
| RESULT_HOST_GEOMETRY | 3000 | 2 | 3000 | Result Host 预挂载/几何请求 | Yes |
| PHOTO_SETTLE | 2000 | 1 | 2000 | Stage13.2 进入 | Yes |
| HANDOFF_ACK | 1000 | 0 | 1000 | HandoffEnvelope 发出 | Yes |
| SCENE_OWNER_TRANSFER | 1000 | 0 | 1000 | Consumer ACK 后请求 Owner 转移 | Yes |
| RESULT_INFO_ARRIVAL | 1500 | 1 | 1500 | Stage13.4 进入 | Yes |
| RESULT_SCENE_STABLE | 2000 | 1 | 2000 | Stage13.5 进入 | Yes |
| RESIDUE_SETTLE_RENDER | 1500 | 2 | 1500 | Stage13 余辉稳定请求 | Yes |
| PIXELCOPY_CAPTURE | 1500 | 0 | 0 | PixelCopy 请求实际提交 | No |

## 独立时钟和重试规则

- SceneClock、共享 Model Deadline、各 Gate 原始 Deadline、Attempt 阻塞预算、Evidence Clock 独立管理。Compose 重组、Gate 重试、Stage 切换和 Result Navigation 不得重置这些时钟。
- 多个并行 Gate 只按实际阻塞时间区间的并集计费；模型仍 RUNNING 时，非模型等待不消耗 10,000 ms 预算。
- 10,000 ms 达到后产生 NON_MODEL_GATE_WAIT_BUDGET_EXCEEDED，保存首个失败并停止无限等待。
- Retry 保留 Gate Instance、Deadline、Attempt 和累计预算；成功 Receipt 不得重复接受。身份/hash/几何/事件序列不一致、FishPath 无效、Mask 不可信和上游终态失败均不重试。
- PATH_BOUND 超时不得自动选择 ORB_ONLY。Result Host Geometry 可异步准备，但不得冻结 Stage10。PixelCopy、视频和 ZIP 导出仅属 Evidence。

## 实现核对

RecognitionGateDeadlinePolicyV1 和 RecognitionGateDeadlineTracker 已加入；常量、Retry、并发阻塞区间、模型等待排除、PixelCopy 排除、Attempt 隔离、FIRST_FAILURE 与下游失败行为的单测通过。运行时统一调度器在 YujianApp 根组合中创建，但生产调用点尚未覆盖全部 23 个 Gate。这里的 PASS 仅指数值/Tracker 单测，不能代表 23 条生产链全部完成。
