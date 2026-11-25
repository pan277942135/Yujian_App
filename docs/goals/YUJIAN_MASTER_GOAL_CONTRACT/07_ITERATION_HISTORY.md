# 07｜保留研发历史；追加而非覆写

| 节点 | SHA / 记录 | 可以确认的事实 | 不能宣称 |
|---|---|---|---|
| PR #346 历史基线 | `babd6b3ad31ee02f70d74738a27d7f14cc6b5e10` | P0-2 代码原始 Parent | 当前生产 HEAD |
| 原始未推送 P0-2 | `759e918332293a4ffaea479a57556386b7c41f1d` | Native 生命周期/内存/测试原始九文件 | 已直接在远端 HEAD |
| State reconciliation | `23aae59ca70b12ace504ccb5920e24820b43f8a9` | PR #346 两阶段快进的独立状态提交 | 模型运行验收 |
| P0-2 集成 | `57ccf1b30dfad4ec4fe049dd972a60f5cbfc64c5` | 9 文件 GitHub 入库；历史 73 单元测试 PASS、CI Run 45567597200 SUCCESS、Debug APK Artifact | API36 Simulator/真机 Smoke PASS |
| 本轮真实设备回归 | 同上 `57ccf1b3` | ZIP `6f3a69…` 8/13 阶段、`9bc218…` 4/13 阶段；两个 Mask READY | 完整 Result / 26KF PASS |
| 后续 PR #346 提交 1–3 | `ad7e7359`、`fe2c1a04`、`13cfe7e8` | 状态/Admission/回归记录提交 | 真机已修好 |
| 后续 PR #346 P0 修复 | `5b1f28c2b0b47c078833b0559d861465e8931448` | Gate/Path Binding/Identity 等生产改动已提交（源码差异可见） | HIGH/STATIC 两类真机证据 PASS |
| 建档最新 PR #346 | `cafc289c27d7b7fc80d8bbc76183e799f5d28ae9` | 以 P0-2 安全修复集成为目标的 Merge Commit；相对 57ccf1b ahead 5 commits | 最新 APK 的真机运行效果已确认 |

其他历史 PR #331 Orb/R06、#344 GPU、#345 资产、#335 AUTO、#336 Glyph/Result 以及 #346 较早多次 CI/Debug 版本均保留原 GitHub 历史，不将早期通过的 CI 计入新 SHA。历史 20s/60s 卡死、旧 ZIP 缺 `original_mask.png`、左下孤立矩形等问题继续列在 [03](03_HISTORICAL_ISSUE_LEDGER.md)。

## 每轮追加模板
```
Date/Goal iteration:
SOURCE_LOCK: exact PR head / parent / control-plane main SHA
ISSUE_IDS:
EVIDENCE_INPUT: zip SHA256 + sample IDs + source build SHA
ROOT_CAUSE: confirmed / hypothesis / rejected
CODE_DIFF: paths / contract authority / consumer & producer
TESTS: exact commands, counts, pass/fail/skip
ANDROID: device/emulator, API, actual runtime outcome
VISUAL: KF status/ROI metrics/known gaps
COMMIT_PUSH: branch, new SHA, parent, PR head, CI run, APK SHA
NEXT_PRIORITY: unresolved issue IDs and prerequisites
```

不允许失败后删除历史记录，也不允许原地重写控制面的静态规则。当前 `PROJECT_MASTER_STATE.yaml / WORK_EXECUTION_STATE.json` 仍按现有分支状态协议维护；这个文件是历史索引而不是与动态状态竞争的权威。
