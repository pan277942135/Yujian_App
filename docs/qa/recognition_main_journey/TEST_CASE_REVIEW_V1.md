# 渔见拍照识别主流程 · 66 条用例 + 10 条组合场景 V1

**Status: REVIEW · Never claim executed PASS from catalog content.**

## 统一边界与数据纪律

- 主线仅 Empty Home → Permission → First-use P01/P02/P03 → CameraX → Detector/Crop/Classifier → Result → Return。不含 Normal Home Hero F01–F08、鱼获详情/B 面、云同步或模型训练。
- 所有 66 条基础用例各绑定一个 `QA_RECOGNITION_001` 初始状态，并由 `test_users.json` 反向索引。4 个状态是 **同一个本地游客** 的预设，而非 4 个线上用户。
- **设计状态** (`DRAFT`/`REVIEW`/`FROZEN`) 与 **执行状态** (`NOT_RUN`/`PASS`/`FAIL`/`BLOCKED`) 严格分离。当前全部 `NOT_RUN`；未来运行输出应在带 SHA 的独立执行报告，不能回写设计目录伪造 PASS。
- 控制 Outcome 的 UI/状态机测试不得证明真实模型识别；真实端到端须保留 CameraX 原始 Trace、Pipeline 事件、设备信息、APK/source SHA、原生截图。
- 真实图片 `IMG-01/IMG-04` 只是仓库候选原图，来源和权限未核准。其它照片尚缺失或仅有测试规范，不能冒充真实验收资源。
- 系统权限历史、相机设备、字体比例与旋转由 Host/ADB 处理；Seed 不修改系统权限。测试用例每次执行前应重置目标本地用户状态，禁止并发运行 Seed 与运行时测试。

## 基础用例覆盖

| 模块 | 用例数 | P0 | P1 | 主初始用户状态 |
|---|---:|---:|---:|---|
| EN | 6 | 5 | 1 | FIRST_USE、RETURNING |
| PER | 7 | 4 | 3 | FIRST_USE、PERMISSION_DENIED、RETURNING |
| GUIDE | 11 | 9 | 2 | GUIDE_PENDING、RETURNING |
| CAP | 9 | 6 | 3 | RETURNING |
| PROC | 9 | 5 | 4 | RETURNING |
| RES | 10 | 7 | 3 | RETURNING |
| NAV | 7 | 4 | 3 | RETURNING、GUIDE_PENDING |
| SYS | 7 | 1 | 6 | RETURNING、GUIDE_PENDING |

### 66 条逐项审查矩阵

| ID | 用例 | 优先级 | 初始状态 | 层级 | 设计状态 |
|---|---|---|---|---|---|
| EN-01 | 首次安装展示空首页 | P0 | FIRST_USE | L3 | DRAFT |
| EN-02 | 点击拍照入口 | P0 | RETURNING | L3 | DRAFT |
| EN-03 | 快速重复点击入口 | P0 | RETURNING | L2 | DRAFT |
| EN-04 | 相机初始化期间返回 | P0 | RETURNING | L3 | DRAFT |
| EN-05 | 已授权且已完成指引再进入 | P0 | RETURNING | L3 | DRAFT |
| EN-06 | 相机初始化失败后重试 | P1 | RETURNING | L2 | DRAFT |
| PER-01 | 首次请求相机权限 | P0 | FIRST_USE | L3 | DRAFT |
| PER-02 | 首次授权成功 | P0 | FIRST_USE | L3 | DRAFT |
| PER-03 | 首次拒绝授权 | P0 | FIRST_USE | L3 | DRAFT |
| PER-04 | 拒绝后再次进入 | P0 | PERMISSION_DENIED | L3 | FROZEN |
| PER-05 | 不再询问或受策略限制 | P1 | PERMISSION_DENIED | L3 | FROZEN |
| PER-06 | 从系统设置恢复授权 | P1 | PERMISSION_DENIED | L3 | DRAFT |
| PER-07 | 运行时权限被撤销 | P1 | RETURNING | L3 | DRAFT |
| GUIDE-01 | 首次显示的触发条件 | P0 | GUIDE_PENDING | L3 | DRAFT |
| GUIDE-02 | 默认 P01 | P0 | GUIDE_PENDING | L2 | DRAFT |
| GUIDE-03 | P01 到 P02 | P0 | GUIDE_PENDING | L2 | DRAFT |
| GUIDE-04 | P02 到 P03 | P0 | GUIDE_PENDING | L2 | DRAFT |
| GUIDE-05 | 上一步回退 | P1 | GUIDE_PENDING | L2 | DRAFT |
| GUIDE-06 | Pager 左右滑动 | P1 | GUIDE_PENDING | L2 | DRAFT |
| GUIDE-07 | P03 开始拍摄 | P0 | GUIDE_PENDING | L2 | DRAFT |
| GUIDE-08 | 跳过首用指引 | P0 | GUIDE_PENDING | L2 | DRAFT |
| GUIDE-09 | Back 不误标完成 | P0 | GUIDE_PENDING | L2 | DRAFT |
| GUIDE-10 | 底层快门/相册不可穿透 | P0 | GUIDE_PENDING | L2 | DRAFT |
| GUIDE-11 | 引导完成持久化与数据重置 | P0 | RETURNING | L3 | DRAFT |
| CAP-01 | CameraX 预览 Ready | P0 | RETURNING | L3 | DRAFT |
| CAP-02 | Ready 前禁止点击拍照 | P0 | RETURNING | L3 | DRAFT |
| CAP-03 | 单次快门产生单次照片 | P0 | RETURNING | L3 | DRAFT |
| CAP-04 | 双击快门去重 | P0 | RETURNING | L3 | DRAFT |
| CAP-05 | 横向鱼体正常化 | P0 | RETURNING | L3 | DRAFT |
| CAP-06 | 长竖鱼与不完整入框 | P1 | RETURNING | L3 | DRAFT |
| CAP-07 | 夜拍与复杂背景 | P1 | RETURNING | L3 | DRAFT |
| CAP-08 | 拍照失败或图像不可解码 | P0 | RETURNING | L3 | DRAFT |
| CAP-09 | 相册打开与取消不污染相机 | P1 | RETURNING | L3 | DRAFT |
| PROC-01 | 进入真实 Processing | P0 | RETURNING | L3 | DRAFT |
| PROC-02 | CAPTURED 最短视觉时长 | P1 | RETURNING | L2 | DRAFT |
| PROC-03 | DETECTING 最短视觉时长 | P1 | RETURNING | L2 | DRAFT |
| PROC-04 | OUTLINE 使用真实目标几何 | P1 | RETURNING | L3 | DRAFT |
| PROC-05 | CLASSIFYING 最短视觉时长 | P1 | RETURNING | L2 | DRAFT |
| PROC-06 | 模型早于最短节拍完成 | P0 | RETURNING | L1 | DRAFT |
| PROC-07 | 模型延迟返回 | P0 | RETURNING | L1 | FROZEN |
| PROC-08 | 真实无鱼分支 | P0 | RETURNING | L3 | DRAFT |
| PROC-09 | 模型或裁切错误 | P0 | RETURNING | L2 | DRAFT |
| RES-01 | 高置信结果 UI | P0 | RETURNING | L2 | DRAFT |
| RES-02 | 中置信度候选 UI | P0 | RETURNING | L2 | DRAFT |
| RES-03 | 低置信度人工确认 UI | P0 | RETURNING | L2 | DRAFT |
| RES-04 | 无鱼结果 UI | P0 | RETURNING | L2 | DRAFT |
| RES-05 | 严重图像质量异常 | P0 | RETURNING | L2 | DRAFT |
| RES-06 | 可处理的质量警告 | P1 | RETURNING | L2 | DRAFT |
| RES-07 | Result 原图完整且方向正确 | P0 | RETURNING | L3 | DRAFT |
| RES-08 | Result 首入 CTA 不 Loading | P0 | RETURNING | L2 | DRAFT |
| RES-09 | 缺失可选元数据 | P1 | RETURNING | L2 | DRAFT |
| RES-10 | 重组不重复识别 | P1 | RETURNING | L2 | DRAFT |
| NAV-01 | Camera 返回空首页 | P0 | RETURNING | L3 | DRAFT |
| NAV-02 | Guide 退出状态保持 | P0 | GUIDE_PENDING | L2 | DRAFT |
| NAV-03 | Processing 中断/返回策略 | P1 | RETURNING | L3 | FROZEN |
| NAV-04 | Result 顶部返回目的地 | P0 | RETURNING | L3 | FROZEN |
| NAV-05 | Result 系统 Back 处理 | P1 | RETURNING | L3 | FROZEN |
| NAV-06 | 返回后第二次识别 | P0 | RETURNING | L3 | DRAFT |
| NAV-07 | Activity 重建/后台恢复 | P1 | RETURNING | L3 | FROZEN |
| SYS-01 | 基准 API28 主链路 | P0 | RETURNING | L3 | DRAFT |
| SYS-02 | 长屏边界 | P1 | RETURNING | L3 | DRAFT |
| SYS-03 | 短屏边界 | P1 | RETURNING | L3 | DRAFT |
| SYS-04 | 字体缩放 | P1 | GUIDE_PENDING | L3 | DRAFT |
| SYS-05 | 旋转与 Insets | P1 | GUIDE_PENDING | L3 | DRAFT |
| SYS-06 | TalkBack 语义 | P1 | GUIDE_PENDING | L2 | DRAFT |
| SYS-07 | 视觉与 Motion 合同 | P1 | RETURNING | L3 | DRAFT |

完整每条 `steps`、`assertions`、`input_ids`、`authority_refs`、`evidence_required` 与状态字段见 [`test_case_catalog_v1.json`](test_case_catalog_v1.json)。

## 十条组合场景（不重复计入 66 条）

| ID | 场景 | 初始状态 | 执行模式 | 设计状态 |
|---|---|---|---|---|
| E2E-01 | 首次安装→权限→P01/P02/P03→真实拍照→真实结果→返回 | FIRST_USE | REAL_DEVICE | DRAFT |
| E2E-02 | 返回用户不重复引导的完整复访流程 | RETURNING | REAL_DEVICE | DRAFT |
| E2E-03 | 拒绝权限→设置恢复→拍照 | FIRST_USE | REAL_DEVICE | DRAFT |
| E2E-04 | 首次引导跳过并拍照 | GUIDE_PENDING | REAL_DEVICE | DRAFT |
| E2E-05 | Medium/Low 结果受控 UI 分支 | RETURNING | CONTROLLED_UI | DRAFT |
| E2E-06 | 真实无鱼图识别 | RETURNING | REAL_DEVICE | REVIEW |
| E2E-07 | 模糊图质量异常 | RETURNING | REAL_DEVICE | REVIEW |
| E2E-08 | 识别故障注入后恢复 | RETURNING | CONTROLLED_FAILURE | DRAFT |
| E2E-09 | 处理中返回/重新进入 | RETURNING | REAL_DEVICE | DRAFT |
| E2E-10 | 同用户连续识别两次 | RETURNING | REAL_DEVICE | DRAFT |

每条场景以 `covers_case_ids` 绑定基础用例；`E2E-05` 和 `E2E-08` 是受控注入场景，不可以宣传成真实模型全链路验收。

## 2026-10-17 七项业务决定已冻结

根据用户明确批准，PER-04、PER-05、PROC-07、NAV-03、NAV-04、NAV-05、NAV-07 的行为验收合同已经 FROZEN。参见 [七项批准决定](SEVEN_DECISIONS_APPROVED_V1.md) 和 [机器可读决策源](seven_decisions_v1.json)。

**执行结果仍为 66/66 NOT_RUN；当前整体 QA 用例目录仍为 REVIEW，不宣称 E2E 或 API28 通过。**

### 仍需量化与真实设备验证

- PROC-07：超时行为已确定为「等真实推理 → 达正式配置超时 → TECHNICAL_FAILURE → 用户主动重试」，但实际超时毫秒数尚未获授权；对应 BLOCKED_PARAMETER，不能用动效时长代替。
- NAV-07：后台返回/重建时若同一 attempt 能安全恢复就复用，否则明确 Retry Recognition / Retake。是否成功重绑必须运行时验证，不能通过静态断言冒充。
- PER-05：权限恢复 CTA 已批准；文案/视觉按照既有页面权威进行设备审核。
- E2E-06：无鱼实际照片及模型稳定输出尚待建立。
- E2E-07：质量 INVALID 实际输入、来源及模型质量门证据尚待建立。

### 已消除的决策歧义

- NAV-03 主动返回 Capture；迟到模型结果失效。
- NAV-04/05 顶部返回和系统 Back 一致，进入来源 Home；当前测试用户回 Empty Home，未保存不新增 FishRecord。
- PER-04/05 依据平台是否还能请求授权选择显式再次请求或系统设置路径，禁止自动弹窗循环。

### 核查过的冻结边界

- Capture 权限状态 `CAPTURE_PERMISSION_REQUIRED` / `CAPTURE_PERMISSION_DENIED` / `CAPTURE_READY` / `CAPTURE_ERROR`；相册入口不依赖相机权限。
- First-use Guide：仅 Skip/P03 Start 写 completed=true；System Back 和其他 dismiss 不应误标完成。
- Processing：CAPTURED≥220ms、DETECTING≥320ms、OUTLINE≥350ms、CLASSIFYING≥250ms；同时等待真实 Pipeline 事件，不能靠定时器伪造结果。
- Result：HIGH/MEDIUM/LOW 与 NO_FISH/IMAGE_QUALITY 分别验收；严重质量问题 INVALID 不等于技术故障 TECHNICAL_FAILURE。

## 可持续更新规范

1. `test_case_catalog_v1.json` 是 66 条基础用例的唯一机器可读来源；`e2e_scenarios_v1.json` 是 10 条组合场景的来源；`test_inputs_v1.json` 只登记照片/受控输入/Host 配置规格。
2. 新增用例或变更断言需维护 ID 连续性、版本号及 ChangeLog；在 PR 内说明是否需要新的用户 Seed 状态。不得默默重用已经发布的 ID 或用一张图片伪装多个独立样本。
3. 每个新 case 必须明确单一 `initial_state`，同时更新 `test_users.json` 反向索引；未来如一用例需多个初始状态，应加变体用例，而不是在同一条用例里隐式更换状态。
4. 如业务规则未决保持 REVIEW；冻结后只变 `design_status`，**不改变 `execution_status`**。真实 PASS 必须来自当次 APK/SHA 和证据链。
5. 校验命令：`python3 scripts/validate_recognition_qa_test_users.py`、`python3 scripts/validate_recognition_test_case_catalog.py`、`python3 -m unittest discover -s tests -p 'test_recognition_*' -v`。

## 一次运行应产生的结果数据（未来）

至少包含 `test_case_id`, `test_user_id`, `user_dataset_version`, `manifest_sha256`, `test_input_id+sha`, `source_commit_sha`, `apk_sha256`, `api_level`, `device_profile`, `started_at`, `ended_at`, `result`, `assertion_log`, `screenshots`, `pipeline_events`。模拟的预期分支必须单独标注 `controlled=true`。

