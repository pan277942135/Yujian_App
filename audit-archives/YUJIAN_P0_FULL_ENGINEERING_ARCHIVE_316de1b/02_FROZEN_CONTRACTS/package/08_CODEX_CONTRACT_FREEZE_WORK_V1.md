# CodeX Work｜YuJian AI V2 六大核心合同交叉审查收敛 × 规范冻结 × Registry/SHA 归档 V1

**Task ID:** `YUJIAN_AI_V2_SIX_CORE_CONTRACTS_CROSS_REVIEW_AND_FREEZE_V1`\
**Repository:** `pan277942135/Yujian_App`\
**Target PR:** `#346`（必须执行时重新查询）\
**Priority:** P0\
**Work Type:** CONTRACT / DOCUMENTATION ONLY\
**Contract Set:** `YUJIAN_AI_V2_SIX_CORE_CONTRACTS@1.0.0`

## 一、本轮唯一目标

将本 Work Package 中的六份核心合同作为唯一规范输入，完成仓库内现有合同/架构文档的去重与交叉映射，形成 canonical contract set，完成机器可读 Registry、Manifest、SHA256 和静态一致性校验，并将文档增量提交到现有 PR #346 分支。

本轮**只冻结规范，不实施生产代码修复**。

## 二、必须遵守的边界

### 2.1 禁止修改生产实现

不得修改：

- Detector / Classifier / Vision Encoder / Mask Decoder 生产推理逻辑；
- Native Runtime、ORT Session、Attention、Allocator、Embedding Cache；
- Recognition State Machine / SessionStore / Snapshot Producer；
- RecognitionSceneAnimationController / SceneClock / WaterLight Engine；
- Orb Renderer / Glyph Renderer / Result Screen / Navigation；
- PixelCopy、Video Recorder、Evidence Recorder 生产逻辑；
- 模型文件、视觉资产、APK 构建配置。

不得运行完整 APK Build、Instrumentation、Emulator、真机 Smoke 或视觉验收来冒充本轮合同冻结结果。本轮只允许运行文档/JSON Schema/Registry/一致性静态校验。

### 2.2 保留现有工作

1. 首先读取当前分支、远端 HEAD、PR #346、工作区状态、未提交文件、`artifacts/`、状态文件和现有合同目录。
2. 不得 `reset --hard`、`clean -fd`、覆盖/删除用户已有文件、重建分支或强制推送。
3. 保留已有状态文件改动和 `artifacts/`；只增加本 Work 明确要求的文档文件。
4. 不得沿用历史 SHA 作为当前 HEAD。已知历史检查点 `0acfa98afe96bb04768c8304b81cd5e1456b63d9` 仅作检索线索，必须重新查询远端。
5. PR #346 保持 Open/Draft；不得擅自合并或改为 Ready。

## 三、规范输入与优先级

使用本 Work Package 的：

- `01_CROSS_CONTRACT_REVIEW.md`
- `02_RECOGNITION_RUNTIME_CONTRACT_V1.md`
- `03_RECOGNITION_RESULT_SNAPSHOT_CONTRACT_V1.md`
- `04_REVEAL_TIMELINE_CONTRACT_V1.md`
- `05_ORB_TO_GLYPH_RENDERING_CONTRACT_V1.md`
- `06_RESULT_HANDOFF_CONTRACT_V1.md`
- `07_RUNTIME_EVIDENCE_CONTRACT_V1.md`
- `09_CROSS_CONTRACT_ACCEPTANCE_MATRIX.csv`
- `10_CONTRACT_REGISTRY.json`

规范优先级：

1. 用户最新明确冻结决策；
2. 本 Work Package 六份合同及 Cross Review 裁决；
3. `AI_26KF_P0_Visual_Contract_V1.md` 的 KF00–KF25 视觉语义；
4. `YuJian_AI_Unified_Architecture_and_Motion_Spec_V2.xlsx` 的架构/时钟/模型分支规则（与最新明确决策冲突时按最新决策）；
5. 真机审计表、历史 Work 和旧报告只作为历史证据，不得覆盖本规范。

如果仓库存在同名旧合同，保留历史内容和来源，建立 superseded/legacy 标记；不得静默覆盖后丢失历史差异。

## 四、Canonical Repository Layout

在审计现有目录后，优先采用：

```text
docs/contracts/yujian-ai-v2/
├── README.md
├── 01_CROSS_CONTRACT_REVIEW.md
├── 02_RECOGNITION_RUNTIME_CONTRACT_V1.md
├── 03_RECOGNITION_RESULT_SNAPSHOT_CONTRACT_V1.md
├── 04_REVEAL_TIMELINE_CONTRACT_V1.md
├── 05_ORB_TO_GLYPH_RENDERING_CONTRACT_V1.md
├── 06_RESULT_HANDOFF_CONTRACT_V1.md
├── 07_RUNTIME_EVIDENCE_CONTRACT_V1.md
├── 08_CODEX_CONTRACT_FREEZE_WORK_V1.md
├── 09_CROSS_CONTRACT_ACCEPTANCE_MATRIX.csv
├── 10_CONTRACT_REGISTRY.json
├── 11_CHANGELOG.md
├── CONTRACT_MANIFEST.json
└── SHA256SUMS
```

如果仓库已有等价 canonical path，复用现有路径并在报告中说明；禁止创建第二个平行合同目录。

## 五、执行阶段

### WP0｜只读基线核验

- 核验 PR #346 当前 head/base/state/draft。
- 核验目标分支与最新远端 HEAD。
- 核验工作区未提交改动和 artifacts。
- 搜索现有六合同、Recognition Runtime、Snapshot、Timeline、Orb/Glyph、Handoff、Evidence、Registry。
- 输出 `CONTRACT_SOURCE_INVENTORY.md`：每个来源文件、版本、SHA、状态、是否重复、是否被 supersede。

WP0 未完成前不得写入。

### WP1｜逐条 Contract-to-Contract Mapping

把每份合同的所有 MUST/SHALL 映射到：

- 唯一 Owner Contract；
- Producer；
- Consumer；
- Shared Identity；
- Event/Receipt；
- Failure Code；
- Acceptance Gate。

每个条款必须且只能有一个权威 Owner；其他合同只能引用，不得复制定义并形成不同语义。

必须核验：

- Identity 字段名和含义；
- Photo/Model/Mask/Contour/FishPath/Snapshot/Handoff Identity；
- 三时钟；
- 13 Stage/26KF/6000ms/60s；
- Stage13 与 13.1–13.5；
- FULL_REVEAL/ORB_ONLY/CLASSIFICATION_FAILED；
- Glyph Path 与 Result UI 依赖；
- Geometry 与 Snapshot 边界；
- Native cancellation/return/release；
- Evidence 只读和 AUTO Gate。

### WP2｜冲突裁决和文档落盘

严格采用 `01_CROSS_CONTRACT_REVIEW.md` 的 C01–C24 裁决。不得自行改变 KF 编号、Stage 数量、时长、Resident 数量、60s 上限或 Handoff 子阶段顺序。

将六份合同写入 canonical path。若现有合同包含额外规则，先判断其是否与本规范冲突；冲突必须写入 changelog，不得静默丢弃。

### WP3｜机器可读 Registry/Manifest

生成：

- `10_CONTRACT_REGISTRY.json`；
- `CONTRACT_MANIFEST.json`；
- `SHA256SUMS`；
- `CONTRACT_SOURCE_INVENTORY.md`；
- `CONTRACT_FREEZE_REPORT.md`。

Registry 必须记录 Contract ID、版本、文件路径、Owner Domain、依赖合同、状态、内容 SHA256。Manifest 必须记录全包版本、Git HEAD、PR、文件清单、文件 Hash、冻结状态。

### WP4｜静态校验

必须执行并记录：

1. 所有 Markdown 文件非空且链接目标存在；
2. 所有 JSON 可解析；
3. Registry 六个 Contract ID 唯一且全部存在；
4. 所有合同版本一致为 V1；
5. KF ID 恰好为 KF00–KF25；
6. Stage 数量恰好为 13；
7. Stage duration 合计恰好 6000ms；
8. 60,000ms 模型等待与 6000ms Scene Presentation 不混用；
9. 13.1–13.5 顺序正确且归属 Stage13；
10. `FULL_REVEAL` / `ORB_ONLY` 条件不冲突；
11. Snapshot immutable，ResultGeometry 不写回 Snapshot；
12. Resident 五条与 Task Energy 独立；
13. Evidence 不得作为业务 Producer；
14. 所有文件 SHA256 与 Manifest 一致。

若发现冲突，修正文档后重新运行静态校验。不得通过删掉断言、弱化检查或写 `|| true` 使校验变绿。

### WP5｜提交到现有 PR #346

- 只提交合同/Registry/Manifest/校验脚本/报告。
- 不包含生产代码、模型、视觉资产、APK 或运行产物。
- 保留 PR #346 当前 Draft 状态。
- 提交前检查 `git diff --check` 和 staged file list。
- Commit message：`docs(contracts): freeze YuJian AI V2 six core contracts v1`
- 推送到现有 PR #346 原分支；不得创建新 PR、不得 force push、不得合并。
- 如果远端认证或网络阻塞，保留本地提交和完整报告，明确 `PUSH_BLOCKED`，不得宣称已推送。

### WP6｜交付报告

输出：

- Contract Set Version；
- Cross Review Status；
- Source Inventory；
- C01–C24 裁决摘要；
- Canonical Path；
- Registry/Manifest/SHA；
- 静态校验逐项结果；
- Commit SHA；
- PR #346 最新状态；
- 未解决项；
- 明确声明本轮没有修改生产代码、没有构建 APK、没有运行设备验收。

## 六、停止条件

出现以下情况立即停止写入并报告：

- 无法确认当前 PR 分支或远端 HEAD；
- 工作区状态不清楚且可能覆盖用户改动；
- 现有 canonical contract 与本包存在未裁决的产品级冲突；
- 不能确认文件来源或 SHA；
- 静态校验失败且无法定位。

不得为了完成任务而猜测、覆盖、清理或绕过。

## 七、最终状态

只有 WP0–WP5 全部完成、静态校验通过、文档 Commit 已关联 PR #346，才可报告：

`CONTRACT_FREEZE_PASS`

若文档已生成但未推送：`CONTRACT_PACKAGE_READY_PUSH_BLOCKED`。\
若合同冲突未裁决：`CONTRACT_FREEZE_BLOCKED`。

**本 Work 的 PASS 只代表六份规范已统一并冻结，不代表生产代码符合合同，不代表 13 Stage/26KF、Orb-to-Glyph、Result Handoff 或真机 APK 已经验收通过。**
