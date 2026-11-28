# 渔见 AI｜Master Goal Contract V1.0（统一研发信息入口）

**建立日期**：2026-11-25 · **项目**：pan277942135/Yujian_App · **状态**：PROPOSED_CONTROL_DOCUMENT（待合并 main 才成为正式统一索引）

本目录是供人和 Codex 读取的**唯一项目目标、历史问题和证据汇总入口**，不是独立于既有控制面的新执行权威。**每次 Work 的唯一正式控制面入口仍是 `CONTROL_PLANE_REGISTRY.yaml`（main），并须遵守 `AGENTS.md`、六层注册表、Admission 与 main-first 规则。** 遇到内容冲突，以被引用的冻结原合同和 main 控制面为准，不能凭本摘要修改冻结决策。

## 阅读顺序
1. [00 主目标](00_MASTER_GOAL.md)
2. [01 产品与视觉冻结](01_FROZEN_PRODUCT_AND_VISUAL_CONTRACT.md)
3. [02 架构与六类核心合同](02_ARCHITECTURE_AND_TECHNICAL_CONTRACTS.md)
4. [03 历史问题账本](03_HISTORICAL_ISSUE_LEDGER.md)
5. [04 两份真机 Evidence 审计](04_REAL_DEVICE_EVIDENCE_ANALYSIS.md)
6. [05 根因与假设登记](05_ROOT_CAUSE_HYPOTHESIS_REGISTER.md)
7. [06 量化与视觉验收](06_ACCEPTANCE_AND_METRICS.md)
8. [07 提交和迭代历史](07_ITERATION_HISTORY.md)
9. [08 来源、优先级与未迁移资产](08_SOURCE_PROVENANCE_AND_AUTHORITY.md)
10. [09 Codex 持续执行协议](09_AUTONOMOUS_EXECUTION_PROTOCOL.md)
11. [机器可读状态](GOAL_STATUS.json)

## 核心原则
- **冻结不删除**：老合同保持原路径/Hash；新摘要只索引和说明优先级，不改写原件。
- **事实不等于假设**：来源、适用 Commit、证据链与置信等级必须可追踪。
- **历史问题不等于最新失败**：2026-11-25 的两份设备证据来自 `57ccf1b30dfad4ec4fe049dd972a60f5cbfc64c5`；PR #346 在建档时已经更新到 `cafc289c27d7b7fc80d8bbc76183e799f5d28ae9`，后者尚无等价新真机证据。
- **单一项目仓库**：AI-Orb-Demo 是独立仓库，不得混用视觉资产、PR 或运行证据。
- **源数据隔离**：当前 GitHub 仓库为公开仓库。本目录只入库必要的哈希、诊断摘要和审计引用，不自动上传用户真实照片、视频、完整诊断 ZIP、密钥或个人信息。

## 不能虚称完全迁移
部分原始高保真图片保存在 ChatGPT Library/历史素材包，尚未全部作为仓库可读取的原件入库。因此**“GitHub 唯一信息入口”已建立，但“全部原始视觉与真机文件已经物理集中到 GitHub”尚未完成**。见 [08](08_SOURCE_PROVENANCE_AND_AUTHORITY.md) 的 SOURCE_MIGRATION_PENDING；不得据此给视觉保真打 PASS。
