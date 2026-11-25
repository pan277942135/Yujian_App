# 06｜量化目标、26KF 与 Android 真机验收矩阵

## 已冻结的硬数值（不是这次新发明）
| 指标 | 已确认数值/状态 | 权威 |
|---|---|---|
| FULL 主 Stage 数 | 13 | Reveal Timeline V1 |
| 主动视觉时长 | 6000ms，逐阶段 0/350/600/125/125/450/450/450/1000/350/850/800/450 | Reveal Timeline V1 |
| 必需模型等待上限 | 60000ms / Attempt | Runtime + Timeline |
| 非模型单 Gate 最大期限 | 3000ms（个别 Gate 更短） | Non-model Gate Policy |
| AUTO 非模型阻塞预算 | 10000ms，重叠阻塞只按并集计时 | Non-model Gate Policy |
| PixelCopy 截图期限 | 1500ms，Evidence clock，与业务时钟独立 | Non-model Gate Policy |
| Gate 个数 | 23 个独立 Gate，失败不能绕过父依赖 | Gate Policy + Control Plane |
| KF 状态矩阵 | KF00–KF25：每次 26 行；KF25 保存未触发则 NOT_APPLICABLE | Reveal Timeline + Evidence |
| Resident 独立身份 | G01/G02/A01/A02/C01 五条，**不代表 Task Energy 只有五条** | Orb-to-Glyph Contract |
| Result substage | Stage13.1–13.5 5 条顺序回执、同一 SceneClock | Result Handoff Contract |

## 必须分层判定，不能一个 PASS 代替全部
- `MODEL_PASS`：4 个模型的真实输入输出、模型 SHA、DONE/CANCELLED/FAILED 和时钟可复核。
- `PATH_PASS`：真实 Attempt Mask，Contour 闭合，P* 与 Controller/Store 双端绑定，Path Ledger、Return segments 身份一致。
- `STAGE_PASS`：13 Stage 的真实 Event/Gate/Duration/Render receipts + 停顿时间独立记账。
- `GLYPH_PASS`：Orb 真实分层形变、可见能量转移、真实中文字体 Path 和 `NAME_STABLE` Receipt。
- `RESULT_PASS`：Result Snapshot Commit-once，Result Geometry 独立，`NAME_STABLE → PHOTO_SETTLE → RESULT_SCENE_HANDOFF → RESULT_INFO_ARRIVAL → RESULT_SCENE_STABLE` 按序消费，真实可见。
- `EVIDENCE_PASS`：独立校验 ZIP、PixelCopy/index、可解码 Orb→Glyph 视频、hash/identity/monotonic receipts、缺失声明；未跑不得填 PASS。
- `VISUAL_FIDELITY_PASS`：真实 PixelCopy 帧和冻结原图对齐后 ROI/overlay/diff 指标达冻结容差，主观审视无结构回退，所有 PASS 保留 ref/actual/diff/hash。
- `ANDROID_RUNTIME_PASS`：真实 Android API36 模型成功+取消/重试/NativeRun Return/Lease/Heap/PSS/RSS；无法做算子级归因则标记 INSTRUMENTATION_UNAVAILABLE，不能伪 PASS。
- `EXACT_HEAD_CI_PASS`：源码 SHA、Run SHA、APK 内 build SHA、APK 单文件哈希一致。
- `REAL_DEVICE_ACCEPTED`：用户 HUAWEI 真实设备的 HIGH/STATIC/降级/负例回归 + Evidence，未上机不能记通过。

## 两类固定回归的差异化预期
**HIGH（6f3a69…）**：Mask READY/TRUSTED、P* BOUND → Stage09 真实 Gold Receipt → Stage10 RETURN → Stage11 Orb 解体 → Stage12 Glyph → 13.1–13.5 → stable Result。要证实上次 H05/H08/H21 不再发生。

**MEDIUM/LOW（9bc218…）**：Classifier Ready/44.94% → STATIC_RESULT 合法数据提交 → Result 导航与可见信息完整；FULL 专属 Stage/KF 可以 NOT_APPLICABLE，但**不能因等待 Full Gate 卡 50 秒或伪造完整动画**。两类 Result 都要有 Snapshot 及来源一致性证据。

## 26KF 视觉量化方法（冻结源缺口显式处理）
1. 从冻结 **`AI_26KF_HighRes_Overview.png`** 与独立阶段图、`26kf_reference_mapping.json`、`frozen_visual_tokens.json` 读取真实像素/ROI/色阶/空间结构；不使用未经核验的 V2 文件名或旧 2048x1327 RGBA 伪数据。
2. 每个 KF 按 STATUS = EXACT / STRONG / MISSING / CANDIDATE 标注源精度，不允许独立图缺失时伪造；优先引用真实 8677x5626 RGB 母版及经 SHA 校验 59 项资产。
3. 生成每帧 `reference.png, pixelcopy.png, overlay.png, diff.png, metrics.json`；对齐照片尺寸、裁剪、屏幕 Insets、Orientation、GeometryVersion 和采集 FrameSequence。
4. 量化 Orb 的实际中心、直径、材质前后遮挡、内部动态；Resident 身份/位置/运动；Task Energy 分支/覆盖；真实鱼体 Mask/Contour 与 Gold 路径几何；Orb→Glyph 的粒子轨迹、笔画匹配与最终 Name；Result Hero/文字/布局。
5. `metrics.json` 每项须包含 `value, unit, sourceAssetSha, extractionMethod, tolerance, toleranceAuthority, pass, screenshotSha, buildSha`。**若冻结资产没有数值容差，先量测并给出候选范围供批准，不可由 Codex 在失败后自行放宽或把候选数字写成 FROZEN。**
6. 演示用“黑鱼/草鱼”等文字绝不可成为生产识别鱼种。缺原始组件轮廓、动态回流方向证据时标 `REFERENCE_GAP`。

## 严格验收
- 26/26 **状态行必须完整**，不代表所有分支要 26/26 CAPTURED；FULL 应采集所有 **适用**关键帧（如果某帧无法捕获，明确 FAILED，而非 PASS）；KF25 仅保存后 CAPTURED。
- 真实 Stage/KF Receipt 必须有 Producer、Consumer、事件序号、单调时间、模型/Photo/Mask/Path/Snapshot 身份和 GPU/PixelCopy 证据。Draw Submission/UNIT PASS/MANUAL 不能替代。
- 任何未实现的高保真容差、缺少源素材、无真机环境、无 Memory Profiler 都只能是 PENDING/BLOCKED，不是产品 COMPLETE。
