# 04｜2026-11-25 两份 Android 16 真机 Evidence · 保留原始事实

**Device**：HUAWEI CMS-AL20 / Android 16 (API 36)  
**Build commit**：`57ccf1b30dfad4ec4fe049dd972a60f5cbfc64c5`  
**PR 新 HEAD**：`cafc289c27d7b7fc80d8bbc76183e799f5d28ae9`（5 个新提交，**未用这两份 ZIP 验证**）

**隐私**：原始视频与 ZIP 是用户私有测试文件；本公开仓库仅存安全摘要和 SHA256，不自动发布原照片或原始日志。新 Codex 线程需通过获授权的私有方式获取原件，不能只依靠摘要声称完成源文件审计。

## 样本 A：悬挂鱼 · HIGH → FULL_REVEAL 预期
- 视频：`292161.mp4`（约 21.4s）
- ZIP：`YuJian_Recognition_Evidence_6f3a69a93779.zip`
- ZIP SHA256：`6c0c296c7d2ed35eaa6e42cc89d3dd5cd2426540c551a359590956d374c51f94`
- Detector READY；Classifier READY：鳜鱼，`0.8288033`；SlimSAM READY；Mask READY/TRUSTED，可信闭合轮廓（历史诊断：789 点）。
- 内部 `session/stage_timeline.json`：PHOTO_STABLE 0–22ms，ORB_WAKE 22–921ms，ORB_RELEASE 921–1540ms，PERIMETER_ARRIVAL 1540–1697ms，PATH_TO_FISH 1697–12819ms，CONTOUR_A 12819–13279ms，CONTOUR_B 13279–13747ms，CONTOUR_C 13747–14209ms，FISH_GOLD_PEAK 14209ms 进入后 BLOCKED（`MASK_GEOMETRY_INVALID`），ORB_RETURN 及后续 NOT_REACHED。
- `completedStageCount=8/13`，`checkpointCount=26`、`checkpointCapturedCount=2`、`checkpointMissingCount=9`、`checkpointNotReachedCount=13`。这不等于 8 帧视觉 PASS。
- 真机事件：`GATE_EVENT_ORDER_INVALID`，`PATH_BOUND` 先于 `MASK_CONTOUR_PSTAR` 满足；后续 P* Path 已绑定 / `FISH_FOCUS_READY`，不能误判为“完全没有路径”。
- `MASK_GEOMETRY_INVALID` 在 Controller 存在多分支笼统返回；GeometryVersion 1437474289190970691 与 8532425843219830317 的差异是**待核实关系**，不等于证明非法。Gold Render Receipt 的真实接受状态必须另行核查。
- Result 有业务导航/界面出现，但未完成 `RESULT_SCENE_STABLE`；不可以业务 COMPLETE 替代视觉 COMPLETE。

## 样本 B：手持大鱼 · MEDIUM/LOW → STATIC_RESULT
- 视频：`292159.mp4`（约 51.1s）
- ZIP：`YuJian_Recognition_Evidence_9bc218e51c16(1).zip`
- ZIP SHA256：`0e06c3eac636fa0e4b1f1e54167ba925ecd3391338bc9ed0d05230f9cb8861a5`
- Detector READY；Classifier READY：鳜鱼，`0.44935718`；SlimSAM READY；Mask READY/TRUSTED，可信闭合轮廓（历史诊断：1630 点）。
- 内部 `session/stage_timeline.json`：PHOTO_STABLE 0–38ms，ORB_WAKE 38–1025ms，ORB_RELEASE 1025–1656ms，PERIMETER_ARRIVAL 1656–1819ms，PATH_TO_FISH 1819ms 进入后 BLOCKED，其余 8 Stage NOT_REACHED；`completedStageCount=4/13`。
- 内部 26KF 摘要：`checkpointCapturedCount=2`、`checkpointMissingCount=6`、`checkpointNotReachedCount=17`。
- `REVEAL_DECISION=STATIC_RESULT`；Stage05 只接受 `FISH_FOCUS_READY / ORB_ONLY_READY`，静态业务导航与控制器视觉 Gate 发生合同分歧。
- 日志中 `COMPLETE_ATTEMPT_UNAVAILABLE` 26 次以及 `NAVIGATION_NOT_ACCEPTED`；伴随 `MASK_CONTOUR_PSTAR_TIMEOUT`。不能把这些拒绝推断为 Native 没有 READY。
- 来源为真实 ZIP，不得把 B 案改造成强制 13 Stage FULL 才算通过。

## 两份证据共同缺口
- 两份 ZIP 中均**不存在** `logs/result_handoff.jsonl`，且 session summary 均有 `RESULT_EVIDENCE_NOT_AVAILABLE`。
- `reports/model_summary.json` 中 Detector / Classifier / SlimSAM 是 READY，但实际 Tensor I/O 仍存在 `NOT_RECORDED` 字段。Native Heap 2.47 GiB 原因及取消→Native 返回→释放顺序，未被此次正常成功推理证明解决。
- 实际 PixelCopy 极不完整；Stage 进入/退出不是视觉 Receipt；必须补齐捕获/索引、身份、Hash、Frame Presented、失败分类。
- **当前 Gate 因果、Gold Receipt、commitOnce 精确拒绝分支、13.1–13.5 等待条件还需要在 `cafc289c` 或其后最新 HEAD 上重新验证。**

## 来源清单（私有文件仅用 SHA 引用）
`session/session_summary.json`、`session/stage_timeline.json`、`reports/model_summary.json`、`logs/recognition_events.jsonl`、`logs/mask_runtime.jsonl`、`logs/model_inference.jsonl`、`manifest.json` 以及两段录屏。新测试应以同一 Evidence schema 输出上述文件，并实质生成 Result Handoff 事件日志。
