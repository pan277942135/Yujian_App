# Android 开发实现合同 · Recognition Main Flow V1

**状态：** `REVIEW_REQUIRED`（规范-only；本文件未修改任何 Android 源码）  
**适用：** `YujianApp.kt`、Capture/Processing/Result Compose 页面、识别任务 owner 与 Android navigation。

## DEV-01 · Result 返回导航（NAV-04 / NAV-05）

### 现有代码事实

- `app/src/main/java/com/yujian/ai/YujianApp.kt` 在 Result 路由将 `onBack` 和 `onRetry` 都连接到 `startCapture(preserveSession = true)`；因此当前顶部 Back 和 System Back 的目标是 Capture，而不是来源 Home。
- `RecognitionResultScreen.kt` 的 `requestExit` 会在 `saving || saveRequested` 时不执行退出；顶部 Back 同时 disabled，System Back 被消费。编辑状态通过 `recognitionRequiresDiscardConfirmation(...)` 决定是否弹放弃确认。
- 当前 `startCapture` 清除活动 Attempt、图像、结果并重置 Identify 状态；但 Result Back 仍复用拍摄路径，与已批准合同冲突。

### 与冻结行为的差距

已批准的 NAV-04/05 要求 Result 顶部 Back 和 System Back 都回到该识别会话的来源 Home。固定 QA 用户的来源是 Empty Home。当前两种 Back 都回 Capture，且未建模 Top/System Back 与显式“重新拍摄”的差异。

### 正式目标行为

| 当前状态 | 事件 | Guard | 目标行为 |
|---|---|---|---|
| Result，未保存且无编辑 | 顶部 Back/System Back | 无 save in-flight | 幂等地清理本次识别会话并返回保存的 `source_home_route`；本测试用户显示 Empty Home |
| Result，未保存且有鱼种更正/Metadata/Story 草稿 | 顶部 Back/System Back | `needsDiscardConfirmation=true` | 显示既有确认对话框；“继续编辑”保留所有草稿并留在 Result；“放弃”清理并回来源 Home |
| Result，编辑字段弹层/IME/Selector 打开 | Back | 上层编辑 UI 可处理 Back | 先关闭 Selector、IME 或字段 Sheet；下一次 Back 才离开 Result，按页面级键盘合同执行 |
| Result，保存已提交或正在持久化 | 顶部/System Back | 保存尚未 terminal | 顶部 Back 禁用；System Back 被消费，不改变路由，不创建第二次保存 |
| Result，保存失败 | Back | 无 save in-flight；草稿保留 | 留在 Result 展示错误；Back 仍遵守 dirty-draft 确认 |
| Result，保存成功 | 保存结果已确认 | 有且仅有一个持久化记录 ID | 按既有 Save Catch/Continue Memory 目的地离开；本主流程不验收保存后详情页 |
| 任一 Result 分支 | 明确点击“重新拍摄” | 无 save in-flight；dirty draft 已确认放弃 | 清理当前 Outcome/Image/Attempt 后进入 Capture；创建新 image/Attempt 身份；不沿用 Back 语义 |

### 接口与状态标准

- Capture/Home 进入时在 `RecognitionSession` 保存 `session_id` 与 `source_home_route`；当前 QA 用户值为 `EMPTY_HOME`。不从 Result 当前路由推测来源。
- 统一出口 `ExitRecognition(reason, discardConfirmed)`：`RESULT_BACK`、`RESULT_SYSTEM_BACK`、`RESULT_RETAKE`、`PROCESSING_BACK` 为不同事件；Back 导航和显式 Retake 不可共用回调。
- `RESULT_BACK` 和 `RESULT_SYSTEM_BACK` 共享同一 guard、同一 discard policy、同一 `source_home_route`；未保存离开时 `fish_record_count_after == before`。
- 会话结束的清理要幂等：`activeAttemptId=null`、Result/Prediction/Failure 失效、旧图像 lease 释放、相机重新进入时复核 CameraX 状态；迟到 callback 因 Attempt ID 不匹配而忽略。
- 新识别必须拥有新的 capture image ID 与 Attempt ID；禁止复用上次 Result payload 或旧请求。

### 建议改动范围

- 修改 `app/src/main/java/com/yujian/ai/YujianApp.kt`：保存来源 Home route，拆开 Result Back 与 Retake 回调，定义单一清理/导航出口。
- 修改 `app/src/main/java/com/yujian/ai/ui/screens/RecognitionResultScreen.kt`：显式映射 Top/System Back、保存中 Back、编辑器 Back 和 dirty-draft 确认。
- 若现有导航状态不足，新增纯策略文件 `app/src/main/java/com/yujian/ai/ui/recognition/RecognitionNavigationPolicy.kt`；不得将业务逻辑留在一个模糊的 `onBack` lambda 中。
- 测试规划：`RecognitionResultNavigationRuntimeTest#resultTopBackReturnsToOriginHomeWithoutRecord`、`#resultSystemBackMatchesTopBack`、`#dirtyDraftBackRequiresConfirmation`、`#saveInFlightConsumesBack`、`#retakeCreatesFreshAttempt`、`#newAttemptDoesNotReuseOldResult`。

### Gate 与验收断言

- Gate：G01、G09、G13；绑定 `NAV-04/NAV-05/NAV-06/RES-10`。
- 顶部与系统 Back 从同一状态得到相同 route、相同记录数、相同清理结果。
- 未保存 Back 后来源 Home 为 Empty Home，新增 FishRecord 数为 0；从 Home 再进入识别，旧 imageId/result/attemptId 均不可达。
- 保存中触发任意 Back 不改变路由；保存失败后草稿完整、记录数不变；成功保存恰有 1 条记录并按用户选择的已有目的地退出。

## DEV-02 · 权限拒绝与恢复（PER-04 / PER-05）

### 现有代码事实

- `IdentifyScreen.kt` 通过 `ContextCompat.checkSelfPermission(..., CAMERA)` 初始化 `hasCameraPermission`，并以 `RequestPermission()` 回调更新本地状态。
- 首次 Camera route 进入且未授权时由 Effect 请求；未授权 UI 也可显式请求。源码未维护系统拒绝原因与“系统已不再显示弹窗”的独立状态；未见 App Settings 恢复 Intent 或 `ON_RESUME` 统一复核。
- Photo Picker 使用 `PickVisualMedia()`，Gallery 取消不需 Camera 权限。

### 与冻结行为的差距

PER-04/05 已批准：无自动重复弹窗；仅用户明确点击时再次请求，Gallery/Back 始终可用；当请求不能继续时提供当前 App 设置入口。当前 `permissionRequested` 是 Compose `remember` 状态，不能作为跨重组/Activity 的可靠拒绝历史；`shouldShowRequestPermissionRationale=false` 也不能单独等同“永久拒绝”。

### 状态判断合同

| `checkSelfPermission(CAMERA)` | App 请求历史 | `shouldShowRequestPermissionRationale` | 标准状态与动作 |
|---|---|---|---|
| GRANTED | 任意 | 任意 | 仅在 Camera route 校验 Provider；授予本身不等于 CameraX Ready |
| DENIED | 无已记录请求 | false/未知 | `PERMISSION_REQUIRED/FIRST_REQUEST`; 用户进入 Camera 路由后只发起首次平台请求 |
| DENIED | 曾请求 | true | `PERMISSION_DENIED/REQUESTABLE_HINT`; 先展示解释与显式“再次请求”动作；不自动发起 |
| DENIED | 曾请求 | false | `PERMISSION_DENIED/REQUESTABILITY_UNKNOWN`; false 不能单独证明永久拒绝。保留显式重试和 Settings 两条恢复路径 |
| DENIED | Host 可观测到 USER_FIXED 或 policy-fixed；或显式重试后系统未授予 | 任意 | `PERMISSION_DENIED/NOT_REQUESTABLE_OR_RESTRICTED`; 不循环请求，显示 Settings/Gallery/Back |
| Settings 返回 | 任意 | 任意 | Activity `ON_RESUME` 后重新调用 `checkSelfPermission`; 只有新读到 GRANTED 才初始化 CameraX |
| 用户关闭/拒绝平台弹窗 | 任意 | 任意 | 视为未授予；不启动相机；记录一次请求结果，不自动再弹 |

Android 没有对任意拒绝状态提供一个可由 `shouldShowRequestPermissionRationale` 单独得出的通用 `canRequestNow` 布尔值。产品代码只能将该 API 当作是否显示 rationale 的提示。测试 Host 可用 `adb shell dumpsys package <debug.package>` 观察 `USER_SET`/`USER_FIXED` 等权限标记；这些是测试证据，不作为 Release UI 的隐藏依赖。

### 触发、设置入口与用户提示

- 首次请求：用户主动进入 Camera route 时允许发起一次；不得在 Empty Home 首屏自动请求，也不得在重组/返回前台/进入 Gallery 时重复请求。
- 拒绝后的显式重试：用户点“再次请求相机权限”才调用 `RequestPermission`；每个点击最多有一个 in-flight request。
- 建议提示文案（非 Frozen，需页面 copy owner 冻结）：**“需要相机权限才能拍照识鱼。你可以再次请求相机权限，或前往系统设置开启；也可以从相册选择照片。”** 操作：“再次请求”“前往设置”“从相册选择”“返回”。无权限时不得说“已永久拒绝”。
- Settings Intent：`Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.fromParts("package", context.packageName, null))`；先 `resolveActivity`，失败时保持页面、显示可恢复错误；从 Settings 返回必须重新读取系统授权并重试 CameraX 初始化。
- Photo Picker 不请求 Camera 权限；取消不修改图片/Attempt/Permission 状态。
- Activity 重新进入 Camera 时根据当前系统授权重新计算，不相信旧 Compose Boolean；运行中权限撤销时停止捕获、unbind/release CameraX 并切到 Permission recovery。

### 建议改动范围、测试与 Gate

- 修改 `IdentifyScreen.kt`，新增小型 `RecognitionCameraPermissionPolicy.kt`（若复用已有 policy 更好）；不要修改 Gradle 或 manifest。
- 测试规划：首次 grant/deny、显式重试和 request count、`shouldShow...` false 但不误判、USER_FIXED/管理策略恢复入口、Settings 返回后 grant/no grant、运行时撤权、Gallery permission-independent、Back。
- 代码范围：Capture permission state/presentation, activity resume observer, Settings Intent, test-only Host permission probe；不更改用户账户/相册权限。
- Gate：G02、G04、G12、G13；PASS 需要 API28 平台权限 trace、request count、当前授权状态、设置 Intent target、Gallery/Back 可用断言。

### 未冻结细节

CAP-003 的拒绝文案/视觉仍需 Capture copy owner 批准。Host 可观察的系统 flags 是验收手段；应用内永久拒绝检测策略须按上表保留 `UNKNOWN` 兜底，待实现评审确认。责任：Recognition Android owner；关闭条件：权限状态矩阵在 API28 真机/runner 上得到可复现 request/dialog/settings trace。

## DEV-03 · 模型超时与重试（PROC-07）

### 现有代码事实

- `FishRecognitionPipeline.recognize()` 顺序运行 Detector → quality gate → Crop → Classifier；Crop 失败和 Classifier 异常被分类为技术失败，取消会向上传播。
- `RecognizingScreen.kt` 记录 Processing 视觉状态与 `SystemClock.uptimeMillis()`，按真实 pipeline phase 及冻结最短展示节拍驱动画面。流水线没有找到总推理 deadline 配置；仓库现有资料没有可引用的 Pixel_2/API28 Detector/Crop/Classifier 性能分位数。
- `PROC-07` 行为已批准，数值仍标记 `BLOCKED_PARAMETER`。不得把 220/320/350/250ms 动效时间当 inference timeout。

### Recognition Attempt Contract

1. `Attempt ID` 在一份归一化图像被用户确认用于一次识别时生成；同一图像显式重试生成新 ID。一个活跃 session 只允许一个活动 Attempt。
2. Attempt 生命周期：`CREATED → RUNNING_DETECTOR → RUNNING_CROP → RUNNING_CLASSIFIER → SUCCEEDED | INPUT_BRANCH | TECHNICAL_FAILURE | CANCELLED | TIMED_OUT`。每个 pipeline event 必须携带 `attempt_id`、`image_id`、单调时间戳、阶段名和实际 model artifact/version；结束事件只能一次。
3. 总推理计时从 owner 接受 Attempt、紧邻 Detector dispatch 之前开始；终点为真实成功结果、真实 no-fish/invalid 输入分支、错误回调、用户取消或 timeout terminalization。相机预览、图像归一化与 Processing 纯视觉最短 beat 不计入模型推理时间，但完整用户感知耗时另行记录。
4. 计时必须使用单调时钟（建议 Android `SystemClock.elapsedRealtime()`；同一次 run 禁止混用 wall clock 或跨时钟相减）。现有 Processing visual clock 仍按其冻结实现使用，不将两种时钟混为一个字段。
5. 总 timeout `TOTAL_INFERENCE_TIMEOUT_MS` 是必要配置；Detector、Crop、Classifier 阶段 deadline 仅在阶段可单独挂起/失去响应时配置。总 timeout 是最终上限，阶段 timeout 不得使整段执行超过总上限。同步 Crop 的异常应立即失败，不能为其捏造 timeout。
6. 到期后先原子地把 Attempt 标记为 `TIMED_OUT` 并取消可取消任务，再发 `TECHNICAL_FAILURE`；旧 classifier/detector callback 即使迟到也只能被计为 `STALE_CALLBACK_IGNORED`。Timeout 不是 `NO_FISH` 或 `INVALID`。
7. 用户点击 Retry 才能重试。源归一化文件仍存在且 SHA 一致时允许复用同一不可变图像，生成新 Attempt ID/新 deadline；源缺失或 SHA 不符时只能 Retake/Gallery。旧 Attempt 的 Result 和缓存不得复用。
8. 每个 terminal path 都在 `finally` 释放 Detector/Crop/Classifier 临时资源和回调引用；关闭/释放异常单独记入失败分类，不覆盖原始结果。

### 数值建议与依据

目前仓库命中的素材只有最短动效断言和相机/Runner 等待上限，未发现识别模型冷/热启动、阶段延迟分布或目标 SLA 的认可测量报告。故本阶段将：

```text
TOTAL_INFERENCE_TIMEOUT_MS = PARAMETER_REVIEW_REQUIRED
DETECTOR_TIMEOUT_MS        = PARAMETER_REVIEW_REQUIRED (若异步/可挂起)
CROP_TIMEOUT_MS            = NOT_APPLICABLE_TO_SYNCHRONOUS_CPU_CROP unless profiling shows a bounded async stage
CLASSIFIER_TIMEOUT_MS      = PARAMETER_REVIEW_REQUIRED (若异步/可挂起)
```

建议在实际 API28 Pixel_2 runner 上采集冷/热模型、每阶段、全程 latency 分布和失败样本，再由 Recognition Product Owner 与 Model Owner 审批 P95/P99 目标、余量和功耗/体验折中；发布配置同时绑定 model SHA、runner profile、测量窗口和配置版本。**不建议在没有这些测量时填入 5 秒、10 秒或任何固定值。** 责任：Model Owner + Product Owner；关闭 Gate：G06/G07、PROC-07。

### 建议改动范围、测试与 Gate

- 后续实现文件候选：`ai/FishRecognitionPipeline.kt`、`ai/RecognitionStateMachine.kt`、新增 `RecognitionAttemptCoordinator`/timeout 配置来源、`ui/screens/RecognizingScreen.kt` 的事件绑定。不得此阶段改源码或阈值。
- 测试规划：deadline 前成功；跨 deadline 等待；Detector/Crop/Classifier 各阶段失败；取消与 retry；迟到回调；重复 terminal 回调；同图重试新 ID；release 资源；timeout 不进 NO_FISH/INVALID。
- Gate：G06/G07/G08/G09/G12/G13；PROC-07 的事件/ID合同可冻结，超时数值与运行性能 Gate 仍阻塞。

## DEV-04 · 生命周期恢复（NAV-03 / NAV-07）

### 现有代码事实

- `YujianApp.kt` 中 Recognition Session/Attempt/Image/Result 状态由 `remember { mutableStateOf(...) }` 管理，不是持久的 `SavedStateHandle` / ViewModel owner。
- `RecognitionImageStore` 将 Camera/Gallery 归一化图像写入 app-private cache；这为文件复用提供候选，但当前没有与 Attempt owner 绑定的恢复资格合同。
- `RecognizingScreen` 按 `imageId` 建立其本地 Pipeline/visual state；Activity/进程重建后的单次推理续接没有已确认的 Runtime 证据。当前导航在识别 route 上检查 Attempt ID，Result callback 会被 route/ID 条件保护。

### 目标生命周期矩阵

| 生命周期事件 | Attempt owner | 图片 lease | 行为 |
|---|---|---|---|
| Processing 中用户 Back | owner 存活 | 文件/Bitmap 可读 | 取消或立刻使 ID 失效，返回 Capture（NAV-03）；禁止 Result/记录 |
| 后台短暂停留，原 ViewModel/Job 仍活 | 同一 owner 活 | 文件与 hash 有效 | 恢复观察同一个 Attempt，不再调用 Detector；背景停留时间不用于资格判断 |
| Activity 重建，ViewModel/job owner 仍可重绑 | 同一 owner 活且同一 ID | 文件/lease 有效 | 重新连接观察者，仅一次 inference invocation；恢复当前 state |
| Activity 重建但 owner 已丢失 | 不存在或无法证明 | 文件有效 | 使旧 ID terminal/stale；显示明确 Retry Recognition，用户点后同图新 Attempt ID |
| 进程终止后重开 | 旧 owner 已结束 | 文件/metadata 有效时可复用 | 禁止后台任务假恢复；明确 Retry（新 ID）或 Retake；不可自动开始新推理 |
| 任意恢复时图片文件不存在/不可解码/SHA mismatch | owner 任意 | 无效 | `IMAGE_LOST`，清理错误引用，只给 Retake/Gallery；不得用旧 Result 或冻结截图重建 |
| 老模型回调在取消/重建/重试后到达 | ID 不匹配或 terminal | 任意 | 记录 `stale_callback_ignored=true`，不导航、不落盘、不改当前 Attempt |
| 多次 Activity recreation/foreground cycles | owner 一致 | 文件有效 | 同一 Attempt 只能绑定一个 Pipeline dispatch 和一个 terminal event；清理与导航均幂等 |

### 唯一所有者与持久化标准

- 建议唯一运行所有者是与 Recognition nav graph 绑定的 `RecognitionAttemptCoordinator`/ViewModel。CameraX provider 仍由 Capture lifecycle owner 单独管理。
- ViewModel 持有 live Job、模型管线、Attempt ID、terminal flag、当前 Pipeline stage、observer token 和图片 lease；Activity 仅订阅，不拥有第二份推理任务。
- `SavedStateHandle` 可记录 `session_id`、`attempt_id`、`image_id`、source Home route、当前 status 和 normalized file path/hash；不能持久化 `Bitmap`、模型句柄或正在运行的 coroutine。
- 原图仅留在 app-private 文件并由 lease 管理；terminal cleanup/主动离开后释放 Bitmap、删除不再可重试的临时文件；具体 cache TTL 不用任意秒数，按 owner/lease 是否存在与用户恢复动作清理。
- 安全续接 guard = same owner alive + same attempt ID + exactly one live invocation + source file exists + SHA matches + no terminal/cancel flag。任何一项不满足都不能宣称可恢复。
- “主动回相机”与“超时/错误后显示技术恢复页”是两个不同路径：NAV-03 用户 Back 回 Capture；技术错误回到恢复 UI 后仅在用户点 Retry/Retake 时导航。

### 建议改动范围、测试与 Gate

- 后续实现文件候选：`YujianApp.kt`、新建 `RecognitionAttemptCoordinator.kt`、`RecognitionImageStore.kt` 的 lease/完整性接口、`RecognizingScreen.kt` 的 owner 订阅、navigation route policy。
- 测试规划：后台恢复同 ID/单 invocation、Activity recreation 重绑、Owner loss + image valid explicit retry、image loss retake、late stale callback、multi-recreation idempotence、processing Back to Camera。
- Gate：G01/G06/G09/G12/G13/G14；NAV-07 已批准行为可冻结，只有 exact API28 runtime evidence 可关闭 `DEVICE_VALIDATION_REQUIRED`。

## 四项合同共同验收口径

| ID | 现状 → 差距 | 目标来源 | 计划用例 | Gate | 决策/负责人 |
|---|---|---|---|---|---|
| DEV-01 | Result Back 目前回 Capture，与 NAV-04/05 回来源 Home 冲突 | `seven_decisions_v1.json`, Result contracts | NAV-04/05/06, E2E-01/02/10 | G01/G09/G13/G14 | Android Navigation owner；不需重审已批准返回行为 |
| DEV-02 | 权限路径缺少可靠 requestability/Settings resume 合同 | Capture V3.1, PER-04/05 | PER-01…07, E2E-03 | G02/G04/G12/G13/G14 | Android Permission owner；拒绝文案与 requestability `UNKNOWN` UX 待审 |
| DEV-03 | Pipeline 无总 inference timeout；性能分布与数值无批准依据 | PROC-07 + Processing V3.1 | PROC-06/07/09, E2E-08 | G06/G07/G08/G12/G13 | Model Owner + Product Owner；数值 `PARAMETER_REVIEW_REQUIRED` |
| DEV-04 | Compose remember 状态/缓存文件无 durable attempt owner/rebind 证明 | NAV-03/07 + Image Store | NAV-03/07/06, E2E-09/10 | G01/G06/G09/G12/G13/G14 | Android Lifecycle owner；必须实机验证 owner 复用 |

本合同没有授权任何实现修改。未决项在 `14_OPEN_DECISIONS_AND_BLOCKERS.md`；待实施阶段按表更新代码文件与验证证据。
