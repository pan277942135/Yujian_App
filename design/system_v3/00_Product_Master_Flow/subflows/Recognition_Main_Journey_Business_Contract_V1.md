# 渔见 · 拍照识鱼主链路业务合同 V1.0

**状态：FROZEN（业务流程与数据提交规则）**
**版本日期：2026-10-15**
**适用范围：Empty Home → Capture → Recognition Processing → Result / Recovery → FishRecord**

本合同统一规定拍照识鱼的业务触发、页面路由、结果分支、保存时点和异常恢复。既有 V3.1 Capture、Processing、Result 页面合同继续负责页面布局、状态视觉和组件；本合同不覆盖已有 Frozen PNG，不改变模型、裁剪、阈值、处理动画或页面视觉。

## 1. 不可变业务原则

> **一次拍照识鱼旅程是临时 Recognition Session。只有用户明确发起保存，并且应用核验到持久化成功，才创建正式 FishRecord。**

- 打开相机、选择照片、识别完成、确认鱼种、编辑 metadata/story、点击保存但未成功，都不会增加鱼获记录数。
- 一次 Session 可以包含多次 Capture/Gallery Attempt；新 Attempt 必须使用新图片和新识别任务。
- Save Catch 与 Continue Memory 是两种保存目的地，但都必须先成功创建同一类型的正式 FishRecord。
- No Fish、Image Quality、Technical Failure、取消、返回、重拍、未保存草稿和确定失败均创建 0 条 FishRecord。
- 游客可以完成拍照识鱼和本地保存；登录态与 Home 的 EMPTY / NORMAL 状态相互独立。

## 2. 实体与标识

| 实体 / 标识 | 生命周期与约束 |
|---|---|
| `recognitionSessionId` | 用户从 Home 发起的一次完整识鱼旅程。返回 Home 且未保存、或保存成功后结束。 |
| `recognitionAttemptId` | 同一 Session 中一次 Capture 或 Gallery 图片及其推理。每次取得新图生成新 ID。取消或替换图片时，旧 ID 立即失效。 |
| `imageId` | 已归一化照片的标识。一个 Attempt 只交付一张可读图片。 |
| `RecognitionDraft` | 识别结果、用户确认鱼种、长度、重量、地点、故事和保存目的地的临时状态；不是鱼获记录。 |
| `clientRecordId` | 对一次正式创建操作的幂等键。保存重试及不确定结果核验沿用原键；不得因双击或重试生成第二条记录。 |
| `FishRecord` | 仅在用户明确保存意图且后端或本地仓储已确认持久化后存在。创建成功后 Session 结束。 |

不得让过期的 `recognitionAttemptId` 回调覆盖新照片的结果或改变当前导航。

## 3. 入口、权限与首次引导

### 3.1 入口与权限

1. 用户在 Home 点击拍照后进入既有 Capture / Identify 页面，并创建 Recognition Session。不得在 Empty Home 预先请求相机、定位或麦克风权限。
2. 只有相机路径在用户发起后才请求 Camera 权限。相机 Provider 与 Preview 真正 Ready 后，快门才可用。
3. Camera 权限拒绝或永久拒绝时，相机不启动；提供系统设置恢复、从相册选择和返回。Photo Picker 独立于 Camera 权限。
4. 直接选择相册不触发 Camera 权限或首次相机指引。Picker 取消返回原 Capture 上下文，不启动识别、不闪现旧照片。
5. Provider、拍照或图片归一化失败时留在可恢复 Capture 错误状态；不得创建识别结果或 FishRecord。

### 3.2 首次拍照引导

- The first-use guide is a two-page additive Capture Overlay: P01 `竖屏拍摄示意` (portrait phone + horizontal fish), P02 `横屏拍摄示意` (landscape phone + horizontal fish).
- P01 uses `下一张`; P02 uses `开始拍摄` and `上一张`.
- Each page uses one correct Hero example plus three lower error examples: `鱼头不在画面` / `鱼体过小` / `遮挡过多`.
- P01 and P02 error examples both use horizontal fish. The former portrait-phone + vertical-fish page is archived, not active.
- `跳过` and the final `开始拍摄` action only dismiss the guide; neither action automatically triggers the shutter.
- The frozen visual authority is `design/pages/recognition/first_use_camera_guide/`.

- 仅当 `capture_guide_v1_completed=false`、当前入口为相机、Camera 权限已授权且 Provider + Preview Ready 时，在 Capture 内展示一次轻量 Overlay。
- 引导文案回答：这是拍照识鱼；尽量让鱼体轮廓清晰，手持鱼获也可以拍；拍摄后自动识别，结果可确认或修改。
- “跳过”和“开始拍照”都只关闭引导并持久化完成标识；**都不自动触发快门**。关闭后等待用户主动拍摄。
- 标识按应用安装实例持久化，与登录账号和是否保存过鱼获无关；退出登录不重复展示。卸载重装或清除应用数据后可再次展示。
- 未授权、预览未 Ready、相册入口或引导未完成即退出/进程结束时不标记完成。
- Overlay 显示期间不得有快门触控穿透；遵守安全区、无障碍语义、大字体和减少动态效果设置。
- 引导不得要求鱼体必须完整入镜。手持、草地、水桶、夜晚和部分入画仍可拍摄；是否可分类由真实质量门决定。

## 4. Capture 与 Recognition Processing

1. Capture 或 Gallery Picker 仅交付一张可读取、方向正确并完成归一化的图片；未保存前不创建 FishRecord。
2. 同一 Attempt 不允许并发快门或并发推理。
3. Processing 仅按 V3.1 的真实事件与最短展示时长推进：`CAPTURED ≥220ms → DETECTING ≥320ms → OUTLINE ≥350ms → CLASSIFYING ≥250ms → RESULT_READY`。不以计时器代替真实 pipeline event，不伪造检测框、鱼种或图像质量原因。
4. Processing 的系统/页面返回取消当前 Attempt。离开页面后旧任务结果不得再触发结果页导航。
5. Capture、Processing、Result 的视觉、状态和图像 Fit/Fill 遵守各自 V3.1 页面合同；流程总图中的小图仅是已冻结页面缩略参考，不是新增 Runtime 截图。

## 5. Result：三类鱼种结果、两类图片恢复与技术异常

| 结果 | 鱼种状态 | 保存规则 | 恢复 / 视觉权威 |
|---|---|---|---|
| `HIGH` | Top-1 是可编辑的默认推荐，不等于事实保证。 | 可直接发起保存。首次进入 CTA 为 Idle；只有用户点击后进入 Saving。 | Result High Frozen。 |
| `MEDIUM` | Top-K 仅为候选建议；必须由用户主动确认一个候选或在共享 Species Selector 中选择其他鱼种。关闭选择器不等于确认。 | 未确认不得创建 FishRecord。 | Result Medium Frozen；行为以 V3.1 状态机为准。 |
| `LOW` | 没有确认鱼种；允许暂存 metadata/story。用户必须手动选择鱼种。 | 未手动确认不得保存未知、空值或“待确认”鱼种。选鱼后沿用已请求的保存目的地。 | Result Low Frozen 与共享 Species Selector。 |
| `NO_FISH` | 没有符合条件的鱼体证据。 | 禁止保存。 | RR04 原 Frozen 文案与 Evidence Fit；重新拍摄或从相册选择。 |
| `IMAGE_QUALITY` | 真实 Quality Gate 为 `INVALID`，且不是 `NO_FISH` 优先分支。`WARNING` 不单独触发该状态。 | 禁止保存。不得将分类器低置信度冒充图片质量问题。 | RR05 原 Frozen 文案与 Evidence Fit；重新拍摄或从相册选择。不得编造模糊、曝光或角度诊断。 |
| `TECHNICAL_FAILURE` | 推理、模型、裁剪或依赖的真实技术错误；不属于 3+2。 | 禁止保存，不创建 FishRecord。 | 独立技术恢复文案：“识别没有完成 / 请重新拍摄或选择照片。”不得向用户显示堆栈/错误码或归咎照片质量。 |

现有阈值与状态映射继续由已冻结的 Recognition pipeline / Result contracts 决定；本合同不重开模型、阈值或 3+2 语义。尤其保持 Quality Gate 顺序：`NO_FISH` 优先；只有真实 `INVALID` 才能进入 Image Quality；`WARNING` 不等于 Image Quality。

## 6. 返回、重拍、草稿与保存

### 6.1 路由矩阵

| 当前状态 | 用户动作 | 下一状态 | 数据行为 |
|---|---|---|---|
| Empty / Normal Home | 点击拍照 | Capture | 新建 Session；记录数不变。 |
| Capture | 页面 / 系统返回 | Session 来源 Home | 结束未保存 Session；0 条新增。 |
| Capture | 打开相册；Picker 取消 | 原 Capture | 保留可恢复上下文，不推理、不闪旧图。 |
| Capture | 拍摄成功 / 选中有效新图 | Processing | 新 imageId 与 attemptId，只有一个图片输入。 |
| Processing | 返回 | Capture | 取消 Attempt；丢弃旧回调；0 条新增。 |
| Result / Issue | 返回 | Capture | 不保存；旧结果保留为本次 Session 的历史数据，但不作为下一次推理输入。 |
| Result / Issue | “重新拍摄” | Capture | 先清除当前有效识别结果，再要求获取新图片、新 Attempt；不得直接重跑旧图。 |
| Result | 修改鱼种 | Species Selector | 草稿保留；提交选择后返回 Result；未提交返回不改鱼种。 |
| Result | 编辑长度 / 重量 / 地点 / 故事 | Editor → Result | 只有确实提交了值变化才标记 dirty。打开后取消、不变更不算编辑。 |
| Result | 返回或重拍，且有实质未保存修改 / 保存待核验 | 确认框 | “继续编辑”留在当前 Result；“放弃编辑”去 Capture，并按动作清除旧识别结果。 |
| Result | 返回或重拍，无实质编辑且没有待核验保存 | Capture | 直接离开，不弹草稿确认。 |
| Result | Save Catch / Continue Memory | `SAVE_IN_PROGRESS` | 冻结当前 `clientRecordId` 和目标；阻止重复提交及不安全返回。 |
| Result | 明确保存失败 | Result / `SAVE_ERROR` | 保留照片、已确认鱼种、字段、故事、目标与幂等键；允许重试。 |
| Result | 保存结果超时/未知 | 查询幂等键 | 先核验是否已创建，不能盲目重复新增。 |
| Save Catch | 已确认持久化成功 | Home / NH02 | 创建且只创建 1 条记录；清除识别页面历史栈；刷新 Home。 |
| Continue Memory | 已确认持久化成功 | 对应 FishRecordDetail(memory) | 创建且只创建 1 条记录；清除识别页面历史栈；Detail 返回 Home 或明确父级上下文。 |
| FishRecordDetail | 系统返回 | Home / 明确父级 | 不能回到本次旧 Result、Processing 或 Capture。 |

### 6.2 Dirty 草稿与系统 Back

- 实质变化：鱼种确认/修改、长度、重量、地点或故事内容变化，以及仍待核验的保存请求。
- 打开 Editor / Selector 但取消、不提交变化，不算 dirty。
- 编辑弹层、Species Selector、输入法优先执行自身关闭/隐藏规则；仅在没有这些上层输入状态后处理 Result 返回。
- 保存进行中禁用返回、再次提交和重拍；不要并发写入。
- 草稿保护文案建议：“放弃本次编辑？”、“你修改的鱼种和鱼获信息还没有保存，离开后将无法恢复这些修改。” 按钮：“继续编辑”“放弃编辑”。正式样式复用共享确认组件。
- 配置变化/旋转时保留原图与已提交草稿；进程被系统结束不得把临时 Session 自动升级为 FishRecord。恢复保存状态时先查询幂等键。

### 6.3 持久化确认

1. Save Catch 或 Continue Memory 是唯一的记录创建入口，且必须来自有效用户点击和可核验的保存成功结果。
2. 网络失败、图片上传失败、无效字段或身份过期时保留 Result 草稿。身份过期走合法登录恢复，并返回到可恢复草稿；不得先清空 Session。
3. 后端响应丢失或超时时，使用同一个 `clientRecordId` 查询是否已创建。找到记录时验证 ID、字段和图片关联后按成功导航；确认未创建前不发送新的逻辑创建请求。
4. 游客本地仓储也必须按稳定 `clientRecordId` 幂等写入，并在同步持久化成功前不报告保存成功。
5. 一个 Session 最多产生一条 FishRecord。只有持久化核验完成后才结束 Session、刷新 Home 或打开 Detail。

## 7. 页面合同和新增设计边界

- 已冻结权威：Capture V3.1、Processing V3.1、Result V3.1，以及 `design/pages/recognition/design/05–09` 五张 Frozen Result PNG。不得在本次流程任务里修改这些字节。
- 系统权限提示与 Photo Picker 属于操作系统 UI，不制作伪造的应用系统对话框。
- 首次拍照指引 Overlay、脏草稿确认框为本版本新增应用 UI；其状态与文案已有独立高保真评审稿。本次 Android 使用共享 Material 3 AlertDialog 形态，运行时字号、Insets、TalkBack、字体缩放与系统 Back 仍需通过 Runtime 验收。
- Capture Preview 是相机实时运行面，不拿静态图冒充冻结相机页面。
- 总览 SVG/PNG 是业务沟通图；其中已有 Frozen 页面缩略图仅供识别权威来源。总图不是运行时视觉验收目标。

## 8. 冻结引用

- 机器流程：[Recognition_Main_Journey_Flow_V1.json](Recognition_Main_Journey_Flow_V1.json)
- 页面路由与决策表：[Recognition_Main_Journey_Route_Matrix_V1.md](Recognition_Main_Journey_Route_Matrix_V1.md)
- 自动化验收矩阵：[Recognition_Main_Journey_Acceptance_Matrix_V1.json](Recognition_Main_Journey_Acceptance_Matrix_V1.json)
- 人读验收表：[Recognition_Main_Journey_Acceptance_Matrix_V1.md](Recognition_Main_Journey_Acceptance_Matrix_V1.md)
- 高分辨率横向总图：[SVG 源文件](assets/Recognition_Main_Journey_Overview_V1.svg) · [5120×2880 PNG](assets/Recognition_Main_Journey_Overview_V1.png)
- 新增状态高保真评审：[SVG 源文件](assets/Recognition_New_States_HiFi_V1.svg) · [PNG 预览](assets/Recognition_New_States_HiFi_V1.png) · [资产清单](assets/Recognition_New_States_Manifest_V1.json)
- Capture / Processing / Result 页面合同均列于 `design/system_v3/08_Page_Contracts/recognition/`；V3.1 的 `Effective_Authority_Registry_V3_1.json` 仍是共享与页面 authority 索引。

**冻结范围说明：**本文件冻结业务触发、导航、会话/记录边界和 J01–J20 的预期行为。代码实现和自动化/运行时结果仍由 Stage 3–7 的 exact-head 证据决定；冻结合同不代表实现门禁已经 PASS。
