# 结构化交接 QA

## 状态

`DESIGN_HANDOFF_PARTIAL / PRODUCT_VISUAL_FREEZE_NOT_APPROVED`。六组对照与 21 帧语义矩阵可供工程评估，**不能**报告 `READY_FOR_ANDROID_RUNTIME_IMPLEMENTATION` 无条件通过。独立设计分支与 Draft PR 仅用于审查。

| 检查 | 结果 | 证据 / 限制 |
| --- | --- | --- |
| 六组视觉对照 | PASS（参考板） | G01–G06 PNG 齐全；G04 的 V3 Polygon、G05 的静态凝字有醒目非验证标注；G06 为原 2172×724 KF 合图的对照裁切。 |
| 21 帧与 13 主阶段 | PASS（语义） | CSV/JSON 21 唯一 ID；11/12/13 子帧 3/3/5，映射正式 enum，不创建额外时钟。 |
| 入/出条件 | PASS（设计合同） | 每行含真实事件、模型门控、会话身份；与 `RecognitionSceneAnimationController.kt` 当前 `canAdvance/next` 逐阶段比对。子帧内部完成事件仍属设计提案。 |
| 组件映射 | PASS（只读定位） | 17 个核心组件、路径、输入输出、依赖与失败处理；不代表具体运行效果通过。 |
| R01B / Core | PASS（源身份） | 源 Core SHA `2773e462…`；R01B 派生 JSON SHA `f16a55c9…`；manifest 逐文件记录。 |
| R06 精确身份 | FAIL | V1.1 JSON 所列几何 `a37d947d…`、Profile `075e8390…`、SVG `66966e0b…`；当前 Main 和旧 R06 补充包对应文件分别为 `1dfc85e6…`、`8c7f998b…`、`d321c2bd…`。语义五路径可用，不能冒充二进制匹配。 |
| 原始高清照片 | PENDING_SOURCE_ASSET | 仅有 1173×1536 相册截图；其人物和鱼与 KF 原合图不同，合图只约束布局。 |
| 真实鱼体 Mask | FAIL（静态示例） | V3 Polygon 不可信；离线 SlimSAM 三候选混入天空/手且遗漏边缘。Runtime 必须由同一 attempt 的可信真实 Mask 驱动 FULL，否则 ORB_ONLY。 |
| Glyph S3 六态 | PENDING_PRODUCT_REVIEW | V1.1 S1/S2 不覆盖 S3；目前没有被审视的连续可编辑水核→真实中文路径规格。禁止固定“黑鱼”图。 |
| 13.1–13.5 结果交接 | PENDING_PRODUCT_REVIEW | 以 KF06–KF10 构图为依据，需核实目标照片几何、字号及字段进入状态；V3.1 试排未通过。 |
| Android 变更 / APK | PASS（边界） | 本分支仅 `design/recognition_structured_handoff_v1/`；未触碰 Kotlin、Shader、模型、SceneClock、Main。 |

## 视觉连续性逐项

01→02、02→03：同 Core、R06 局部生命变化可由冻结合同描述；03→04、04→05：独立 WaterLight 宿主与真实锚点；05→06、06→09：只在可信 Mask 下执行；09→10：全局回馈事件；10→11、11→12：同 Orb 到实时 Glyph，形态仍待审；12→13：同照片与名称，结果页目标几何待审。

## V1.1 三轮 QA 记录

| 轮次 | 检查与实际结果 | 修正 / 保留问题 |
| --- | --- | --- |
| Round 1 完整性 | 6 张对照 + 1 张总览；21 帧唯一 ID，13 个正式 Stage；CSV/JSON 同源生成；18 个组件映射、四类参数、Manifest 与 SHA256SUMS 均存在。 | 矩阵新增逐帧 `Visual Delta from Previous`、真实输入、Manual Access/Real Data Rendering/Auto Advance；补充 V3 源包未追踪远端状态。 |
| Round 2 审美 | G01–G06 的背景 `#EAF7F8`、卡片白底、青蓝边框、同一标题/标注字体和对照卡间距一致；G01 候选 Orb 可视直径 224px / 卡宽 745px ≈30.1%，其余 Processing 对照沿用同一 V3 构图。G06 保留原五屏布局的裁切证据，画中人物与确认照片不同。 | G04 的粗略 Polygon、G05 的静态凝字与 G06 的不同照片均在板头标记 `REFERENCE` 或 `NOT VERIFIED`；不冒充连续 Runtime。照片与水光细节未做生成式改画。 |
| Round 3 工程 | 已读取 `RecognitionSceneFrame.kt` 的 13 enum、`RecognitionSceneAnimationController.kt` 的 `canAdvance/next`；核对 R06 renderer 两模式、`ProcessingWaterOrb.kt` 的候选模式入口、真实 Glyph Path 绘制、Fish Focus Core/Soft/Halo、60s 看门狗、Result 信息组件。 | 组件状态区分 `EXISTS/PARTIAL`；R06 三文件二进制不匹配 V1.1 仍 `RESOURCE_IDENTITY_PENDING`；Mask/S3/Result 几何仍待运行证据或产品审视。 |

对照板是已有素材的审美统一排版，不能证明 11–13 的连续动态已经通过产品验收。三轮 QA 均以可查文件和源码证据为依据，不以一张拼图推断 Runtime 行为。

## 交接阻塞与修正顺序

1. 核清 V1.1 对应 R06 三文件的准确二进制版本；保留当前 Main 文件原状，不能静默替换或把不同 SHA 标为冻结。
2. 在 Runtime 工作开始前对 S3 可替换 Glyph 与 KF06–KF10 结果页动态几何做产品审视；只读映射不等于视觉冻结。
3. 真照片源继续 `PENDING_SOURCE_ASSET`，不会阻塞纯组件和门控设计，但不能宣称全视觉高保真已验收。
4. FULL 只用 Runtime 当前照片的可信 Mask；无可信 Mask 自动走 ORB_ONLY。不能把三张离线失败候选作为测试真值。

## PR #317

Open / Draft / Unmerged。01 的 Core alpha/RenderEffect、Host 诊断与专项测试属于其现有改动；真机全屏效果仍未通过产品验收。本设计 PR 不合并它，也不从其 HEAD 复制未验收视觉结论。
