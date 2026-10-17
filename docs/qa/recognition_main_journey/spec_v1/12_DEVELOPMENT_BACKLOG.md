# 12 · 后续开发任务清单（仅规格，不实施）

每项任务只在本规范 PR 审查冻结后另行授权实现。范围引用当前只读源码审计和正式业务合同，不表示代码已修复。RFX.1–RFX.6 是现有识别动效工作流/证据文档中的顺序条目；本规范不重复实现它们，也不将未审的历史工作说明当作本次运行证明。

## 开发前置

| 顺序 | ID | 工作 | 依赖 | 关闭条件 | 状态 |
|---:|---|---|---|---|---|
| 0 | DEV-00 | 审批并冻结本标准；关闭阻止实现的业务/资源决策 | SPEC report, open decisions | Product/Android/QA owners 认可合同版本和仍受阻部分 | PENDING REVIEW |

## RFX.1 → RFX.6 识别动效序列

| 顺序 | ID | 合同对应 / 任务 | 源码候选/参考文件 | Gate / 验收输出 | 依赖/目前说明 |
|---:|---|---|---|---|---|
| 1 | RFX.1 | 共享原图与光场坐标变换；FIT letterbox 排除、CROP 按可视 viewport、authored overshoot 仅在照片边界裁切 | Processing scene、photo transform 与鱼体 focus 几何 | G05/G07；FIT/CROP/overshoot 同图坐标证据 | 参考 harness/evidence/recognition-rfx/RFX.1.md；进实施前核对目标 branch source SHA |
| 2 | RFX.2 | Optical edge 真实曲线段测量、路径终点 wrap、保持冻结路径/fragment topology | edge field renderer / PathMeasure | G07/G10；七条冻结路径 ID 和局部 segment 帧证据 | 参考 RFX.2.md；不得更改 Frozen path 几何 |
| 3 | RFX.3 | 从真实 subject alpha mask 追踪连续轮廓；仅 Level A；Level B/C 保留其 box 语义 | subject mask/render boundary | G06/G07；mask contour 证据、无 bbox-angle 假轮廓 | 参考 RFX.3.md；不改变 detector/threshold |
| 4 | RFX.4 | V3.1 CAPTURED/DETECTING/OUTLINE/CLASSIFYING 独立状态卡与能量环；真实事件门控及 220/320/350/250ms 最短节拍 | Processing state/scene | G07；phase trace、event ordering、reduce-motion 审查 | 参考 RFX.4.md；各最短值不能充当 model timeout |
| 5 | RFX.5 | 单调共享 frame clock、缓存 viewport Bezier/PathMeasure 与绘制几何、状态切换不重置相位 | Processing frame callbacks/renderer caches | G07；同一视觉时钟 trace、phase continuity、缓存边界证据 | 参考 RFX.5.md；测量设备和 build SHA 绑定 |
| 6 | RFX.6 | FULL/LITE live optical fidelity 采样和 V3.1 证据校验；保留未人工审的视觉结论为 UNREVIEWED | Runtime evidence & validators | G07/G12/G15；单 run 证据、refresh sample summary、人工 visual review | 参考 RFX.6.md；现有报告明确没有质量 PASS 阈值，不可自行增加 |

执行规则：RFX.1 → RFX.2 → RFX.3 → RFX.4 → RFX.5 → RFX.6 顺序；每一项的源码改动和测试需在后来授权的产品分支/PR 进行，不向此规格分支 cherry-pick 产品代码。复用已有实现时先按最新目标 SHA 对应源码/测试报告做审计，避免重复开发；未有 exact-SHA runtime artifact 时维持 NOT_RUN。

## 主流程四项差距与支撑开发

| 顺序 | ID | 核心实现 | 推荐文件 | 关联 Case / Gate | 阻塞/关闭条件 |
|---:|---|---|---|---|---|
| 7 | DEV-01 | 拆分 Result Back / System Back / Retake；记录 source Home；统一 discard/save-in-flight policy；清旧 session/ID | YujianApp.kt, RecognitionResultScreen.kt；必要时新增导航 policy | NAV-04/05/06, E2E-01/02/10；G01/G09 | 目标行为已批准；修改后 exact API28 nav/record-count |
| 8 | DEV-02 | 相机权限状态 reducer/Host-observed history；Settings Intent + resume recheck；去重 request；保留 Gallery 独立 | IdentifyScreen.kt；必要时新 permission policy 与 test-host probe | PER-01…07, E2E-03；G02/G04 | requestability UNKNOWN 兜底与提示文案 owner review |
| 9 | DEV-03 | 单一 Attempt coordinator、单调总 deadline/阶段事件、terminal-once、迟到回调过滤、资源 release、新 ID Retry | FishRecognitionPipeline.kt, RecognitionStateMachine.kt, RecognizingScreen.kt；新增 coordinator/config | PROC-06/07/09, E2E-08；G06/G07/G08/G09 | 先由 Model/Product owner 批准超时配置；无测量前不得落数值 |
| 10 | DEV-04 | Attempt 生命周期 owner、Activity/进程重建恢复资格、image lease/hash、无 owner 时显式恢复/重拍 | YujianApp.kt, RecognitionImageStore.kt, RecognizingScreen.kt；新增 coordinator | NAV-03/07, E2E-09/10；G01/G06/G09 | API28 生命周期 owner trace 和图像恢复策略审核 |
| 11 | DEV-05 | Entry/Guide/Capture 页面与 Camera Ready 顺序的合同一致性整改；只修正经 Gate 证实的差距 | YujianApp.kt, IdentifyScreen.kt, Guide overlay | EN-01…06, GUIDE-01…11, CAP-01…09；G01/G03/G04 | CameraX Ready/guide/persistence/capture trace 满足 Frozen contract |
| 12 | DEV-06 | 仅在 G10/G11 发现合同偏差后修正短长屏、安全区、字体缩放、TalkBack 语义/焦点；不更改 Frozen 视觉行为 | Capture/Guide/Processing/Result Compose 屏幕及 semantics | SYS-02…06；G10/G11 | 每个经审核的 profile、TalkBack 和字体比例证据通过 |

## 实施管理要求

- 完成状态只依各项验收证据更新；不以代码提交或文档存在宣称 PASS。
- DEV-01…04 会改变产品行为/导航/生命周期，逐项绑定本报告的唯一状态机和 Gate，禁止仅改截图与 OCR 文案绕过状态合同。
- 任何 timeout 参数、模糊阈值、Camera permission requestability 推断或 Mock DTO 字段未定时先关闭决策，不在代码里用常数/猜测消除 BLOCKED。
- 若依赖项另有 PR，记录 PR URL 和 exact commit SHA；此文档中的既有 RFX note 不是该 PR 当前状态的证据。
