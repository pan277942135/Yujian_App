# 05｜根因与假设表（不以错误码代替根因）

| ID / 关联问题 | 证据事实 | 根因假设 / 源码交点 | 必须核验的反证或证据 | 现状 |
|---|---|---|---|---|
| R01/H06,H07 STATIC_RESULT | B: 44.94%、`STATIC_RESULT`、26次 COMPLETE_ATTEMPT_UNAVAILABLE | `RecognitionRevealSessionStore.completeAttempt/createAndCommitSnapshot` 可能将非 FULL 成功决策编码成 `RecognitionOutcome.ORB_ONLY`；`RecognitionResultSnapshot.isValid` 又要求 ORB_ONLY 的 Mask **不可信/Contour 不闭合**，但 B 实际可信闭合。此为**源码合同冲突**，是否为本次唯一拒绝原因须取 commitOnce 状态 | 日志补 `INVALID/CONFLICT/STALE_ATTEMPT`、Snapshot 字段/身份；复现可信 Mask+STATIC_RESULT 单测；以 frozen contract 授权兼容分支设计 | SOURCE_CONFLICT_CONFIRMED; RUNTIME_CAUSALITY_PENDING |
| R02/H06 STATIC Stage05 | B: SlimSAM READY 后 Stage05 仍 BLOCKED | Controller PATH_TO_FISH `revealDecisionReady` 仅取 `isReady(FISH_FOCUS_READY/ORB_ONLY_READY)`，`STATIC_RESULT` 为 `isNavigable` 但非动画 Gate 就绪；需要业务完成合法路由，不该向 Full 强行补假事件 | 读取最新 `RecognizingScreen` 的 STATIC 分支/Result Navigation Event；业务导航的实际接受回执 | CONFIRMED_AT_57CC; NEW_HEAD_UNVERIFIED |
| R03/H05 PATH_BOUND 顺序 | A: `GATE_EVENT_ORDER_INVALID PATH_BOUND dependencies=[MASK_CONTOUR_PSTAR]` | Producer/Tracker 依赖投递时序或无原子事件组；新版 `RecognitionPathBindingCommitCoordinator` 可能已修改 | 必须证明 MASK/Contour/P* accepted 先于 PATH_BOUND accepted，含 eventSequence、Photo/Mask/Path 同身份；重复/迟到回调负例 | CONFIRMED_AT_57CC; NEW_HEAD_UNVERIFIED |
| R04/H08,H09 Gold | A: 进入 Stage09 后 `MASK_GEOMETRY_INVALID` | Controller 的 waitingReason 可能把缺 Gold Render Receipt 也统一显示为 geometry 错误；也可能真的是 Photo/Mask GeometryVersion 不相容 | 并行记录 `activeMaskGeometryVersion/activePhotoGeometryVersion/fishGoldComplete` 与 Receipt expected/actual、drawPass/FramePresented；禁止“接受所有 Receipt” | BLOCK_REASON_CONFIRMED; ROOT_CAUSE_PENDING |
| R05/H21 Result 过早 | A: SCREEN 只「识别中」，Result Scene Stable 缺失 | Business `COMPLETE_ATTEMPT` / Result navigation 早于 Stage10–13.5；Result Host 预挂载与**可见 Scene 所有权**混用 | 核对 `YujianApp` 事务及 `RecognitionResultRevealCoordinator/RecognitionResultHandoffMilestoneEffects` 收据顺序、ResultGeometry、ACK、Consumer | SYMPTOM_CONFIRMED; ROOT_CAUSE_PENDING |
| R06/H01,H02 Native | 两次 SlimSAM READY，但以前出现 20s 卡死 / Native Heap 2.47 GiB | ORT 并发与取消后资源、缓存 Lease、内存分配和 Attention 临时张量仍可能存在风险 | 连续同/换照片、真实取消后重试、Android PSS/RSS/NativeHeap、Session poison/cleanup + exact model identity | FIXED_CODE_PENDING_ANDROID |
| R07/H16 ARRIVAL 矩形 | 旧真机左下孤立矩形，路径首尾重合 | `between()` 小于1px退化、Geometry Host 锚点映射/错误重用 | 对同一 PhotoTransform 验证真实路径端点、控制点、闭合/落点及 PixelCopy | HISTORICAL_PENDING_REGRESSION |
| R08/H13,H17 Evidence | 两份 ZIP 缺 `result_handoff.jsonl`、截图覆盖率低 | 可能是事件未产生、Recorder 未订阅、导出遗漏，不能直接判断哪一环 | 三方对照 ResultProducer/Recorder/Exporter、文件存在性/索引、事件数量、SHA/CRC，错误明确记录 | MISSING_FILE_CONFIRMED; CAUSE_PENDING |

| R09/H23 Classifier tensor binding | HUAWEI CMS-AL20/API36 incident and classifier FlatBuffer: logical images/logits versus runtime serving_default_args_0/serving_default_output_0_output; frozen model SHA 29c2f2fd1608178f023b8e35edf632c0dd685df4a7ae1c3562220a7352ccce18 | Production TensorFlow Lite binding is explicit and strict in cf746e152e027e945494ddceee9f94ce63c7d8d3; current task connects real Input/Output Receipts to pipeline and SessionStore consumer | scripts/verify_classifier_tensor_binding.py PASS against committed asset. Exact-head API36 Interpreter.run(), yellow_catfish Top1, confidence >=0.55, old-Attempt rejection, Receipt acceptance and hosted CI remain pending | SOURCE_FIX_PASS_AT_CF746; MODEL_METADATA_GATE_PASS; REAL_INFERENCE_PENDING |

### 必须处理的合同冲突
**STATIC_RESULT vs 冻结 Snapshot Outcome**：当前设计同时存在低置信度业务静态 Result 与仅三个冻结底层 Outcome。任何方案须区分（a）新增非破坏性业务投影层；（b）独立 outcome 枚举的正式修订。不得未经控制面设计审批改变 V1 原始 6 合同、掩盖真实 Mask 质量或通过 ORB_ONLY 伪造静态结果。

### 根因确认模板
`Issue ID → sourceSha → device evidence SHA → exact event/uptime → producer → consumer → expectation → actual → causal fix → targeted test → integrated test → device proof → newSha → status`。

每轮必须记录可能被证据推翻的假设，**禁止从单一报错直接把假设升级 CONFIRMED**。
