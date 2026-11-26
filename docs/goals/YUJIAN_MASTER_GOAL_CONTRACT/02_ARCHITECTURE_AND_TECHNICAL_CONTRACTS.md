# 02｜六类冻结合同与现有架构（不得重造）

所有合同**在 PR #346 工作分支**可直接查阅，但是否已合并 main 必须独立核验。合同原文优先于本摘要。

| 责任域 | 原始冻结/辅助权威 | 禁止回退的核心不变量 |
|---|---|---|
| 1 运行时 | [Recognition Runtime Contract](https://github.com/pan277942135/Yujian_App/blob/cafc289c27d7b7fc80d8bbc76183e799f5d28ae9/docs/contracts/yujian-ai-v2/02_RECOGNITION_RUNTIME_CONTRACT_V1.md) | 四模型独立 Identity/IO Receipt；Application 级 ORT Session；取消 ≠ Native 返回；Lease 安全释放；Embedding single-flight；独立 ModelClock |
| 2 结果数据 | [Recognition Result Snapshot Contract](https://github.com/pan277942135/Yujian_App/blob/cafc289c27d7b7fc80d8bbc76183e799f5d28ae9/docs/contracts/yujian-ai-v2/03_RECOGNITION_RESULT_SNAPSHOT_CONTRACT_V1.md) | PhotoIdentity / Mask / Contour / P* / Path 同一 Attempt；Controller + Store 双端 PATH_BOUND；Snapshot Commit-once；Result Geometry 非 Snapshot |
| 3 时间线 | [Reveal Timeline Contract](https://github.com/pan277942135/Yujian_App/blob/cafc289c27d7b7fc80d8bbc76183e799f5d28ae9/docs/contracts/yujian-ai-v2/04_REVEAL_TIMELINE_CONTRACT_V1.md) | 13 Stage、6000ms 主动预算、26KF、真实事件 Gate、等待独立计时 |
| 4 Orb/Glyph | [Orb-to-Glyph Rendering Contract](https://github.com/pan277942135/Yujian_App/blob/cafc289c27d7b7fc80d8bbc76183e799f5d28ae9/docs/contracts/yujian-ai-v2/05_ORB_TO_GLYPH_RENDERING_CONTRACT_V1.md) | 真实 Core 解体、Resident ID、Source→Target 粒子迁移、真实字体 Glyph Path、Frame Presented Receipt |
| 5 Result Handoff | [Result Handoff Contract](https://github.com/pan277942135/Yujian_App/blob/cafc289c27d7b7fc80d8bbc76183e799f5d28ae9/docs/contracts/yujian-ai-v2/06_RESULT_HANDOFF_CONTRACT_V1.md) | RETURN 三段真实路径；NAME_STABLE→PHOTO_SETTLE→RESULT_SCENE_HANDOFF→RESULT_INFO_ARRIVAL→RESULT_SCENE_STABLE；Host 可预挂载但不能假 ACK |
| 6 Evidence | [Runtime Evidence Contract](https://github.com/pan277942135/Yujian_App/blob/cafc289c27d7b7fc80d8bbc76183e799f5d28ae9/docs/contracts/yujian-ai-v2/07_RUNTIME_EVIDENCE_CONTRACT_V1.md) | 证据只读；真实 Frame Presented/PixelCopy、Native Memory、model trace、Result events、独立 ZIP 校验，不得补造业务事件 |
| 辅助 | [Non-model Gate Deadline](https://github.com/pan277942135/Yujian_App/blob/cafc289c27d7b7fc80d8bbc76183e799f5d28ae9/docs/contracts/yujian-ai-v2/12_NON_MODEL_GATE_DEADLINE_POLICY_V1.md) | 23 个 Gate、固定期限/重试、10s AUTO 阻塞并集、单例调度器 |
| 冲突审议 | [Cross-contract Review](https://github.com/pan277942135/Yujian_App/blob/cafc289c27d7b7fc80d8bbc76183e799f5d28ae9/docs/contracts/yujian-ai-v2/01_CROSS_CONTRACT_REVIEW.md) | 冻结 C01–C24 处理结果，新增差异另立变更记录 |

## 实现所有权（定位入口）
- `application-core`：`FishRecognitionEngine/FishDetectorEngine`、`SlimSamRuntimeManager/Coordinator/Segmenter/NativeRunLifecycle`、真实模型 IO。
- `recognition-runtime`：`RecognitionSceneAnimationController`、`RecognitionGateDeadlineTracker/Policy`、`RecognitionPathBindingCommitCoordinator`、WaterLightPathLedger。
- `recognition-ui`：`RecognizingScreen`、`RecognitionRevealDecisionGate`、`RecognitionRevealSessionStore`、回调/生成与收据。
- `waterlight-renderer`：生产 GPU 水光、路径、Gold 与 Orb 相关 Renderer。
- `recognition-result-components` + `recognition-result-ui`：Result Handoff Milestones、Reveal Coordinator、Result Host、渲染 Receipt。
- `app/src/main/java/com/yujian/ai/YujianApp.kt`：Attempt/Navigation/SessionStore 接缝、Result Snapshot 消费。

这是**排查地图而非精确文件清单冻结**；每轮在最新 HEAD 上真实读取代码与调用链，不能将函数名或旧源码行号当作未变事实。

## Native 现存进度
P0-2 原始 `759e9183` → PR #346 集成 `57ccf1b3` 已含独立 RunOptions、终止控制、资源门控、逐行预处理、内存遥测等 9 个生产/测试文件；历史 73 个单元测试及 Exact-HEAD CI 成功**不等于** API36 Native 内存与取消/重试实测通过。

## 硬性保护
不得建立第二套 SceneClock/Controller、假的 Mask/Contour/Stage Complete/Render Receipt；不得以 Compose 变化、Stage EXITED、进度=1 代替真实 Render Receipt；不得抹除上游错误、延长冻结 Gate deadline 或给模型补造 READY。真实分类名称不可写死“黑鱼/草鱼/鳜鱼”。