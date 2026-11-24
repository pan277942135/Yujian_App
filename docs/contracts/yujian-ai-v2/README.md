# YuJian AI V2｜六大核心合同冻结包 V1.0

**Contract Set ID:** `YUJIAN_AI_V2_SIX_CORE_CONTRACTS`\
**Version:** `1.0.0`\
**Effective date:** 2026-11-24\
**Status:** `FROZEN_SPECIFICATION`\
**Repository:** `pan277942135/Yujian_App`\
**Target PR:** `#346`（执行时必须重新核验）

## 本包用途

本包是 YuJian AI V2 六大核心合同的统一规范版本，解决 Recognition Runtime、Recognition Result Snapshot、Reveal Timeline、Orb-to-Glyph、Result Handoff 和 Runtime Evidence 之间的接口、身份、时钟、事件及验收冲突。

本包冻结的是**产品与工程规范**，不代表当前源码已符合合同，也不代表真机全流程已经通过。

## 文件清单

| 文件 | 内容 |
|---|---|
| `01_CROSS_CONTRACT_REVIEW.md` | 交叉审查结果、冲突清单、裁决和适用来源 |
| `02_RECOGNITION_RUNTIME_CONTRACT_V1.md` | 模型运行、Native 生命周期、Session、Embedding、并发和取消 |
| `03_RECOGNITION_RESULT_SNAPSHOT_CONTRACT_V1.md` | 统一识别数据身份、Mask/Contour/Fish Path 和不可变 Snapshot |
| `04_REVEAL_TIMELINE_CONTRACT_V1.md` | 13 Stage、26KF、6000ms、三时钟及阶段门控 |
| `05_ORB_TO_GLYPH_RENDERING_CONTRACT_V1.md` | Orb 解体、Resident、粒子迁移、真实 Glyph 和 NAME_STABLE |
| `06_RESULT_HANDOFF_CONTRACT_V1.md` | ORB_RETURN、Geometry、Photo Settle、13.1–13.5 和 Result 接管 |
| `07_RUNTIME_EVIDENCE_CONTRACT_V1.md` | Native Profile、模型日志、PixelCopy、视频、ZIP 和 AUTO |
| `08_CODEX_CONTRACT_FREEZE_WORK_V1.md` | 可直接交给 CodeX 的合同入库/冻结 Work 指令 |
| `09_CROSS_CONTRACT_ACCEPTANCE_MATRIX.csv` | 合同间接口和验收矩阵 |
| `10_CONTRACT_REGISTRY.json` | 机器可读合同注册表 |
| `CONTRACT_MANIFEST.json` | 文件清单、版本、范围和逐文件 SHA256 |
| `11_CHANGELOG.md` | 冲突裁决和版本变更记录 |
| `CONTRACT_SOURCE_INVENTORY.md` | 归档源、远端既有规范/实现参考及 legacy 状态清单 |
| `CONTRACT_FREEZE_REPORT.md` | 本次仓库落盘、静态校验与 Exact-HEAD 提交记录 |
| `validate_contract_package.py` | 可重复执行的合同包静态校验器 |
| `SHA256SUMS` | 仓库规范目录内文件 SHA-256 |

## 规范状态边界

- `CROSS_CONTRACT_REVIEW`: PASS（规范层级与接口一致性审查通过）
- `SOURCE_IMPLEMENTATION_CONFORMANCE`: NOT_ASSESSED（尚未完成当前 HEAD 的逐条源码映射）
- `UNIT_TEST_STATUS`: NOT_RUN（本次不修改生产代码）
- `DEVICE_RUNTIME_STATUS`: NOT_RUN
- `VISUAL_ACCEPTANCE_STATUS`: NOT_RUN
- `APK_DELIVERY_STATUS`: NOT_IN_SCOPE

## 唯一关键规则

1. 13 个识鱼阶段从 `PHOTO_STABLE` 开始，视觉活动时长合同为 6000ms；HOME/IDENTIFY 是前置入口。
2. 模型等待最多 60s，与 6000ms 视觉时钟分离；等待期间 MotionClock 继续驱动 Orb/WaterLight。
3. 26 个检查点编号统一为 `KF00–KF25`。
4. `KF20` 对应 NAME_STABLE；`KF21–KF24` 对应 13.2–13.5；`KF25` 是用户触发的保存动作。
5. Stage13 的 13.1–13.5 是有序子阶段，不新增五个顶层 Stage，也不另开五套 Timer。
6. RecognitionResultSnapshot 一旦提交不可变；Result Geometry、Render Receipt、Handoff ACK 均为独立回执。
7. `onGlyphAvailable`、Draw Submission、Navigation Requested 均不等于真实完成。
8. Evidence 只观察、不控制业务状态；没有证据不得 PASS。

## 冻结方式

本包冻结为一套规范基线。仓库中的正式冻结仍须由 CodeX 按 `08_CODEX_CONTRACT_FREEZE_WORK_V1.md` 执行：写入 canonical path、校验 Schema、生成 Manifest/Registry/SHA256，并提交到现有 PR #346 分支。不得因此改动生产 Runtime、Renderer、模型或业务状态机。


## Repository integration

本目录是 PR #346 对应分支中的 canonical V2 core-contract authority。
`CONTRACT_SOURCE_INVENTORY.md` 记录既有文档和实现引用；历史设计文档不会被删除或静默覆盖。
`CONTRACT_FREEZE_REPORT.md` 记录归档包来源、静态检查边界及提交基线。
