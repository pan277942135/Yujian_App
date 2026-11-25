# 08｜权威分层、来源指针、冻结冲突与 Source Migration

## 首要规则：GitHub 为信息入口；原始权威不被摘要覆盖
1. **流程/控制面最高优先**：`main:CONTROL_PLANE_REGISTRY.yaml`、`AGENTS.md` 及其六层权威、Admission；任何 Goal 内容不得成为第二 Work 入口。
2. **冻结识鱼合同**：PR #346 的 `docs/contracts/yujian-ai-v2/` 六份冻结合同、C01–C24 审议和辅助 Gate policy。建档时该目录在 PR 分支，**在 main 不存在**；需依 main-first 流程同步或正式纳入 main，不能伪称 main 已含原文。
3. **冻结高保真素材**：真实 Library 图片/上传的 26KF 素材包；仓库中的 `artifacts/26kf_visual_reference_v1/` 是已审计 SHA、映射、ROI、缺失/冲突索引，**不是所有原始 PNG 本体**。
4. **真实运行证据**：`292159.mp4`、`292161.mp4` 与两份 ZIP；必须绑定 `57ccf1b3`，不得套用到新 SHA。
5. **动态研发状态**：PR #346 当前分支的 `PROJECT_MASTER_STATE.yaml / WORK_EXECUTION_STATE.json` 与 GitHub CI；不应从聊天猜测当前运行状态。

## 固定来源清单
- GitHub Repo：[Yujian_App](https://github.com/pan277942135/Yujian_App)，当前开发 PR：[PR #346](https://github.com/pan277942135/Yujian_App/pull/346)。
- 最近已知 PR HEAD：`cafc289c27d7b7fc80d8bbc76183e799f5d28ae9`；读者需要每次自行刷新。
- [六合同包原文（固定 Commit）](https://github.com/pan277942135/Yujian_App/tree/cafc289c27d7b7fc80d8bbc76183e799f5d28ae9/docs/contracts/yujian-ai-v2)，包含 `02–07`、`12_NON_MODEL_GATE_DEADLINE_POLICY_V1.md`、`CONTRACT_MANIFEST.json`、`CONTRACT_FREEZE_REPORT.md`。
- [26KF 资产映射与冲突（固定 Commit）](https://github.com/pan277942135/Yujian_App/tree/cafc289c27d7b7fc80d8bbc76183e799f5d28ae9/artifacts/26kf_visual_reference_v1)，母版 SHA256 `173b1314bcfaebbf0510b71585a929eb67be962806b225b7660cedcaf115abac`。
- Library：`/冻结高保/13阶段高保真/AI_26KF_HighRes_Overview.png`（原始对照）；历史独立图片同目录，13 阶段清单在 `/冻结高保/AI Orb × WaterLight 13阶段冻结/YUJIAN_AI_ORB_WATERLIGHT_13_STAGE_FREEZE_MANIFEST.md`。
- Library 与本仓库不自动同步；需读取文件内容和哈希，不可仅据文件名。
- 设备 ZIP A（`6f3a69…`）：`6c0c296c7d2ed35eaa6e42cc89d3dd5cd2426540c551a359590956d374c51f94`；ZIP B（`9bc218…`）：`0e06c3eac636fa0e4b1f1e54167ba925ecd3391338bc9ed0d05230f9cb8861a5`。

## 已知冻结冲突，必须保留并按权威层级解释
| 项目 | 观察到的冲突 | 处理 |
|---|---|---|
| Runtime 时长 | 早期 Library 13-stage manifest 3650ms vs 后续 V2 6000ms | Android 13 Stage 以 V2 6000ms；3650ms 留作历史视觉参考 |
| 母版尺寸 | 旧 P0 Token 2048×1327 RGBA vs 59-item archive 8677×5626 RGB | 以真实包校验值 8677×5626 RGB 量测，禁止静默缩放为真源尺寸 |
| 文件名 | 某工作簿称 `YuJian_AI_26KF_HighRes_Overview_V2.png`，该确切文件不存在 | 不创造 V2 文件；使用真实 `AI_26KF_HighRes_Overview.png` |
| KF00/KF01 | 仅总览格，无独立完整高保原图 | 标记 STRONG / SOURCE_GAP |
| KF14 | 动态 Fish→Orb Return 方向证据不足 | 不把单帧当连续动画证明 |
| KF15 | 独立图与 6 帧板起始形态描述冲突 | 按现有 reference_conflicts.json 决议；冲突可审计 |
| KF25 | 未见单独“保存成功”帧 | 用户没保存时 NOT_APPLICABLE；不可伪造成功 |
| Component Tokens | 缺真实组件轮廓、Task Energy 路径、部分量化容差 | 禁止生成伪阈值和“高保通过”报告 |
| STATIC_RESULT | 新业务决策与旧底层 Outcome enum 语义错位 | 正式登记差异及兼容层，冻结原合同需审批后才能变更 |

## SOURCE_MIGRATION_PENDING
**当前这次上传只包括 Master Goal 的可版本化文字、哈希和索引；不包括私有视频/ZIP，亦未宣称所有 Library 原图已安全迁移 GitHub。** GitHub Repo 是 public，原始照片/视频可能含个人与位置数据。需要用户明确授权公开原件，或将视觉原图/证据存储到有访问控制的私有证据库，再写 GitHub manifest + SHA256 + 可用的私有引用。未完成之前，真实源图/实际运行帧验收标记 `SOURCE_NOT_FULLY_REHYDRATED`。

## 每轮校验
- 读取 main canonical registry，执行 admission；
- 确认 PR 最新 SHA、六合同实际所在分支和原始 Blob SHA； 
- 对比 Library/素材包/设备 ZIP SHA256； 
- 若任一来源缺失，明确列出精确路径、owner、影响 Gate 与可恢复路线，不能用历史聊天代替。
