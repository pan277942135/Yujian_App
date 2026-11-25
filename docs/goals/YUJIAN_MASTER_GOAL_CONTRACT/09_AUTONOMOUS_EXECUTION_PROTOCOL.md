# 09｜Codex Goal 执行与恢复合同

## 启动（禁止跳过）
1. 在 `pan277942135/Yujian_App` 当前仓库 checkout；先读 **main 的 `CONTROL_PLANE_REGISTRY.yaml` → `AGENTS.md` → 六层 authority**，根据控制面 main-first/sync 策略执行 `python scripts/validate_control_plane_admission.py`。禁止自创控制面或跳过 Gate。
2. 读取本目录 `README → 00–09` 与 `GOAL_STATUS.json`，随后实际读取六份原始冻结合同、Non-model Gate policy、26KF manifest/missing/conflicts。
3. `git fetch` 后读取当前 PR #346 HEAD，比较 `57ccf1b3` 真机失败基线与当前源码差异，**历史问题不是最新确定性 Bug**。
4. 获得原始私有证据可读取授权后才称已复盘 ZIP/MP4，引用 SHA256；没有权限则仍可完成源码测试，但标记 `NEEDS_DEVICE_EVIDENCE`。
5. 将待修复问题定位在 `03/05`，新问题 append，旧记录不可删除。

## 自主研发循环
`SOURCE_LOCK → EVIDENCE_REPLAY → CAUSAL_ROOT_ANALYSIS → MINIMAL_CODE_FIX → TARGETED_TEST → CROSS_LAYER_TEST → REAL_RENDER/EMULATOR (if available) → DIFF_METRICS → CONTROL_PLANE_ADMISSION → COMMIT_PUSH → EXACT_HEAD_CI → EVIDENCE_REVIEW → NEXT`

- 从真实证据推导事件/版本/Producer/Consumer；优先修复 `STATIC_RESULT` Outcome/Snapshot 合同冲突、`PATH_BOUND` 依赖时序、Stage09 的准确 Gate/GoldReceipt、Result 13.1–13.5 与 Evidence 缺失；新版已有修复先回归，避免重复改。
- 逐项测试高/中低置信度、Mask FAILED、模型 timeout、取消/重试、换图迟到回调、Result commitOnce、UI Navigation、Result Host 几何。
- 视觉修改先获取冻结原图与量化容差，基于真实 GPU/PixelCopy 前后对比；没原图不得宣称已完成高保真。
- 严守有界 Work recovery/failure_gate，不通过创建新工作区掩盖失败 Gate；**自动循环不允许绕过安全/权限/合同失败，也不保证无限持续**。
- 每个已通过审核的独立修复包记录 `parent SHA/new SHA/paths/CI/buildSha/log/frames` 并向原 PR #346 普通快进 Push；禁止 force push、无关新 PR、乱改 main、单测试冒充真机。
- 如果必须修改冻结合同/控制面：先按 main-first 设计/控制变更流程获人审批准，更新相关六层，再同步到 PR；不能把本 Goal 文字当作授权。
- 发生 Run/预算/网络/权限/外部真机证据阻塞时，明确 `BLOCKED_<reason>`，保留已提交源码、差异报告、当前 exact SHA 与下一动作；不要给假成功。

## 固定完成测试
1. 样本 A HIGH 的 FULL Flow，13 Stage、KF适用帧、Gold/Return/Glyph/13.1–13.5 全部真实接收；
2. 样本 B MEDIUM/LOW 正确 STATIC Result，Snap Commit-once、Navigation 与合法状态呈现，不等待 FULL Gate；
3. 原历史问题 H01–H22 已证实消除或有明确不适用/仍未关闭状态，不得无故跳过；
4. PixelCopy 与 Frozen Visual 的 ROI/Geometry/Material/Path/Glyph/Diff 指标有批准容差及证据；
5. API36 Native 堆内存/取消/重试与真实 Renderer Evidence；
6. 可导出的完整独立验证 Evidence ZIP，收据/时间戳/sha 连续；
7. GitHub Exact-HEAD CI、APK 下载及真实用户设备验证。

未取得真实 API36 / 用户华为设备输出时，最终不得标记 COMPLETE，即使云端 unit/cross-layer/CI 全部通过。

## 每轮报告
```text
SOURCE_LOCK = <main control sha> / <PR head>
ISSUE_ID = ...
SOURCE_EVIDENCE = <ZIP SHA; exact JSONL event & uptime>
ROOT_CAUSE = CONFIRMED | PARTIAL | HYPOTHESIS
FIX = <files; why producer/consumer/gate change>
TESTS = targeted/full; count; failed/skipped
ANDROID = environment & actual status
VISUAL = applicable KF; PixelCopy refs; metrics; missing sources
PUSH = branch/commit parent/new SHA/remote HEAD
CI = run SHA, status, APK artifact and APK-file SHA
CURRENT_PROGRESS = first failing gate removed? next failing gate?
NEXT = highest-priority issue; unblock condition
```

Goal 本身不保证 Codex Cloud 自动访问当前 ChatGPT 中的私有文件；当证据不可达时必须报告，不可假装阅读过。
