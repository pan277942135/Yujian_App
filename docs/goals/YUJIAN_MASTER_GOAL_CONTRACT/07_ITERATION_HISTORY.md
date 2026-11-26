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


## 2026-11-26 — Master Goal V2.3 unified baseline and Classifier/API36 closure

- Task: YUJIAN_MASTER_GOAL_V23_UNIFIED_BASELINE_CLASSIFIER_API36_CLOSURE. Production writer remains this locked Codex workspace at /workspace/Yujian_App_PR346; sole branch remains fix/26kf-path-boundary-and-fish-gate-v1; sole PR remains #346. Source lock began at PR/Main/Base/merge-base 2d8bacef09f79d20dde7e2a8587c1caab00432fa / bc89484ca79fe20c63c9c6e65b284f1ec3ac1863 / 6ec43662ef76c6ffee8530148098649496ab7fb1 / 03db3692669847084d7e48fc3128f85cbf832af9. Fresh refs refs/heads/fix/26kf-path-boundary-and-fish-gate-v1 and refs/pull/346/head agreed at 2d8bacef...; PR base stayed at 6ec43662.... No Main, base, Draft, or PR metadata mutation was made.
- Main/PR comparison: Main is two commits ahead (bc89484c Merge PR #347: YuJian Master Goal Contract V1.0; 5d8ef191 docs(goal): establish YuJian master goal contract, frozen source index, and device evidence ledger) and the complete three-dot diff at intake consists of exactly the 12 docs/goals/YUJIAN_MASTER_GOAL_CONTRACT/ files below; each was absent from the PR tree. The eleven non-status documents were copied byte-for-byte from origin/main:bc89484ca79fe20c63c9c6e65b284f1ec3ac1863; the stale Main GOAL_STATUS.json was not copied as current state and was replaced with this branch historical evidence plus pending V2.3 gates. Hashes below are the Main source blob SHA256s before this append/update.

| Master Goal file | Main source SHA256 | Bytes | PR comparison/action |
|---|---|---:|---|
| 00_MASTER_GOAL.md | b55de81d7008fcabcce385fb1942b59160b0c2762c631c1d47cd3bed8d711369 | 3595 | copied byte-for-byte |
| 01_FROZEN_PRODUCT_AND_VISUAL_CONTRACT.md | a25d1aecdc72af0089279964cae66e88ec361b6b965f0dd8a75ac6be53ab7ee1 | 5333 | copied byte-for-byte |
| 02_ARCHITECTURE_AND_TECHNICAL_CONTRACTS.md | 5aa52c676739c2e3f77635174fed0eaeb5f7daca9ddaaacc72a81176a947880e | 4536 | copied byte-for-byte |
| 03_HISTORICAL_ISSUE_LEDGER.md | 8a3da026613ff1cff06b2fa77a37f2f5d7a6ad7e2d6b304c12b9aa4a390fde8d | 4513 | copied byte-for-byte |
| 04_REAL_DEVICE_EVIDENCE_ANALYSIS.md | eedd4175c6a694a232119226a6b6acc3b1ed701b20f34fce5cba27268758b011 | 4344 | copied byte-for-byte |
| 05_ROOT_CAUSE_HYPOTHESIS_REGISTER.md | 0cb3b15f8f50eedfe016778efb0d9ab628449eb162e9504aded5a3515e7f17d5 | 4324 | copied byte-for-byte |
| 06_ACCEPTANCE_AND_METRICS.md | 70b810b340ef9a115e8afcbdffed2f13ebf09eec1ca0ccfa01872586058e6c98 | 5207 | copied byte-for-byte |
| 07_ITERATION_HISTORY.md | 8953d92bde08d46f2653787bceeaa875fd4a517096c4b77af3e7e43ee0fdda36 | 2551 | copied byte-for-byte |
| 08_SOURCE_PROVENANCE_AND_AUTHORITY.md | 1dd0a14687a89d665863146f8553bc34fa67f5133bfe8f43d05207aab94e97c3 | 4631 | copied byte-for-byte |
| 09_AUTONOMOUS_EXECUTION_PROTOCOL.md | cf4a5626471e0805b27b23bf74b143579502e2876d12d82c4285efaa24550cd4 | 4120 | copied byte-for-byte |
| GOAL_STATUS.json | 8f52e5973e628dcb915c8f5a9ae53ccaf6bf7248af5d52966d34b858a828b063 | 2608 | replaced stale dynamic snapshot; historical evidence retained |
| README.md | 4a75f94e1d6cd4216abf679214b24e83cb15c7612d3e8991a67f883f19047bba | 2596 | copied byte-for-byte |

- Contract authority review: Master Goal Contract V1.0 remains an index and status surface, not the canonical Control Plane and not a replacement for the six frozen product contracts. No frozen contract, Gate policy, or Main Control Plane file was edited. Current PR blob SHA256s:

| Frozen contract | SHA256 | Bytes |
|---|---|---:|
| 02_RECOGNITION_RUNTIME_CONTRACT_V1.md | a6b27578a9a6c96b0ba4dd6595eea5faa11602611b55d46d4117d3c5bc97a80e | 8579 |
| 03_RECOGNITION_RESULT_SNAPSHOT_CONTRACT_V1.md | 125ed4f09ef278a45de41f44ddcddd32a235a3d15f0bfb6dfd2fa944a7f83442 | 6640 |
| 04_REVEAL_TIMELINE_CONTRACT_V1.md | 960d90154cd6d1b159582203836cad44fabe19c5ff23559bf6be575683920a87 | 7358 |
| 05_ORB_TO_GLYPH_RENDERING_CONTRACT_V1.md | 84e2a65289b9850423fb3e75edf73266c47f7db0e21ead7aaa3479324467cb50 | 6526 |
| 06_RESULT_HANDOFF_CONTRACT_V1.md | c1f89229b0f523492f2b8446286d3ea84d73aca39d11ce2627617c4f328e9b42 | 6193 |
| 07_RUNTIME_EVIDENCE_CONTRACT_V1.md | 38fbe6b619e270bba9c7b730c1d306446aa8997fb1061fb2c49b06979700025c | 8625 |

- The formal cross-contract review and Gate Deadline Policy were also read and preserved. Current PR SHA256s: 01_CROSS_CONTRACT_REVIEW.md = cd1aa5936b1a2b1ef29036b4967066ef96a7bb240b9a96c6392d78ffc98bdf45; 12_NON_MODEL_GATE_DEADLINE_POLICY_V1.md = 49b3bf8de8244a169d7779647cd24c08063d3427d62de95f5d139fe932b08d93. H01–H22 and R01–R08 remain append-only. H23 and R09 were added with old real-device incident/build identities retained; source fix and metadata evidence are separated from pending current-head runtime acceptance.
- Guard V1 integration review: commits 189f6d85, 2362e097, 4da4693, and cc2cde4 are ancestors of the locked branch. The sole writer lock and canonical worktree are active. Existing Guard code/tests/docs were reused; no second Guard or writer was created. tools/work_execution_v2 suite: 31/31 PASS. Final Admission and the Guard fast-forward push remain to run after the source and existing state checkpoints.
- Classifier work: preserved the strict frozen binding; production FishRecognitionEngine emits its real tensor receipts on actual Interpreter.run() success; pipeline validates model/tensor/photo/attempt identities and emits the accepted receipt event; RecognitionRevealSessionStore rejects missing or stale receipt identity for photo-identity-bound production results. Test A now uses the real night fixture and production Detector→Crop→Classifier. Test B asserts real Top1 plus receipt acceptance and exercises missing/stale Attempt rejection. The committed FlatBuffer gate passed locally. No APK or model asset was modified.
- API36 runner: the existing workflow is reused; Test A and Test B run separately and sequentially, with app force-stop and PID verification between them. Per-case parser checks exact status event sequence, INSTRUMENTATION_CODE=-1, OK (1 test), process/LMK logs, exit status, and one passing JUnit testcase. Runner harness, parser/finalizer tests, workflow YAML parse, Bash syntax, and diff whitespace checks pass. AVD gate checks hw.ramSize, hw.heapSize, hw.cpu.arch, and API36 Google x86_64 image.sysdir.1.
- Verification limits at checkpoint authoring: this cloud workspace has no Android SDK, and GRADLE_USER_HOME=/tmp/yujian-v23-gradle ./gradlew projects --no-daemon could not resolve services.gradle.org due restricted DNS. Therefore current-source JVM regression, AndroidTest compile, Kotlin/Release/Lint, API36 boot, real Interpreter.run(), production yellow_catfish output, memory profile, APK, and GitHub Exact-Head CI remain PENDING. Historical 604/604 and 574/574 results are not promoted to this source.
- H23/R09 acceptance ledger: SOURCE_FIX_PASS at cf746e152e027e945494ddceee9f94ce63c7d8d3; MODEL_METADATA_PASS by the FlatBuffer gate against the unchanged committed model; current-source JVM_REGRESSION, ANDROIDTEST_COMPILE, CLASSIFIER_INTERPRETER, PRODUCTION_TOP1, RECEIPT_IDENTITY on API36, API36_RUNTIME, and EXACT_HEAD_CI remain PENDING. Stage 4/whole-flow and overall Master Goal remain NOT_ACCEPTED.
- Next: finish state reconciliation, commit through Guard, run Admission, ordinary fast-forward push only to PR #346 original branch, verify branch and pull refs, then review exact-head GitHub Actions artifacts before any runtime/build status promotion.

