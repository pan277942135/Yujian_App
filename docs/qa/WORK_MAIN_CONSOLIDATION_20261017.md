# Work 合并至 main · 集成候选（2026-10-17）

**状态：CANDIDATE / NOT_RELEASE_READY；源项目尚有 N001=BLOCKED。**

## 集成范围
- PR #145 @ cc642f198b6641108589076eb70de600f0e47d3a：产品、设计系统与 Android Runtime 合同。
- PR #157 @ 67dea2e76c9d86272816ff6c8e8c4d7089386c66：RFX.1–RFX.6 识别动效。
- PR #158 @ c3840ea0b83e80d2d4360eec621764720d28055f：Home/Camera 证据判定闭环逻辑。
- PR #159 @ d881191edcbd285041b93597e89012a166d1849e：QA_RECOGNITION_001、66 条用例与七项用户决策。
- Canonical main 起点：b201d2b6229b629bcbcb9b4a3458a307099d2cfe。

## 冲突及排除
- **不引入**产品分支实时快照 `PROJECT_MASTER_STATE.yaml`、`WORK_EXECUTION_STATE.json` 及它们专用的 `project-master-state.yml`。依 `CONTROL_PLANE_REGISTRY.yaml`，这些属于 work branch 状态，不能写为 main 的实时控制面。
- **不引入**分支执行日志 `harness/build-log.md`；保留在 PR #145 分支历史和相应 Workflow 证据中。
- **暂不删除** `scripts/verify_recognition_runtime_evidence_v1_2.py`，避免旧工具依赖不经审查即被移除；新的 RFX V3.1 校验器仍按集成分支评估。
- #153 只有两页教学，与已决定的 P01/P02/P03 三页合同冲突，不纳入。
- 历史的 #120–#124、#84 等冲突或陈旧 PR 不直接覆写最新产品实现，须单独核对是否已被后续工作取代。

## 这不是 Release PASS
- 既有 API28 证据中 Normal Home 与 Camera 均为 FAIL_EVIDENCE；即使构建成功也不改变这一事实。
- N001/J01–J20/发布门禁保持 BLOCKED/NOT_RUN；不得移除现有失败证据或篡改 Frozen PNG/模型阈值。
- 本分支需运行代码构建、静态 Contract 和 QA 目录校验；如 branch-affinity 导致控制面状态检查失效，应报告原因，不得伪造 PASS。
- 合入 main 后需在新的 main HEAD 重跑正式 API28 Gate 与取证，方可再次判断发布资格。

## 操作记录
- 使用源 Git 原始 blob SHA 与文件 mode 原字节合成，未重编码资产；版本通过多父提交可追溯。
- 不强推 main 或改写已有工作分支；合并需经 GitHub PR 状态检查和仓库规则。 
