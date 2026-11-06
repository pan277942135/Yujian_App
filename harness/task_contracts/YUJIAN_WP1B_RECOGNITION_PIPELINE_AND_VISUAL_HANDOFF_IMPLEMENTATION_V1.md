# Work P0 — 渔见 AI WP1-B 识别业务链路完整修复、视觉生命系统集成与结果交接 V1

**Task ID:** `YUJIAN_WP1B_RECOGNITION_PIPELINE_AND_VISUAL_HANDOFF_IMPLEMENTATION_V1`

**Repository:** `pan277942135/Yujian_App`

**所属阶段:** WP1-B

**优先级:** P0

**执行模式:**

`CONTRACT_REPOSITORY_VERIFICATION → CONTROL_PLANE_ADMISSION → MAIN_BASELINE → TARGETED_IMPLEMENTATION → AUTOMATED_TESTING → DEBUG_APK_DELIVERY`

**执行目标：** 完成识别业务链路从真实照片输入、Detector、Classifier、SlimSAM、可信 Mask、Orb 水光交互、真实鱼种凝字，到 Result 页面完整交接的工程闭环。

---

# 一、最高执行要求

本轮不是继续编写审计报告，也不是重新设计识鱼视觉。

WP0 已完成全局审计，WP1 已完成业务链路审计，产品负责人已经批准并冻结：

`YUJIAN_RECOGNITION_VISUAL_CONTRACT_FREEZE_ADDENDUM_V1`

现在进入正式工程实施。

必须遵守：

1. 先核实全部权威设计合同是否已经正式进入 Git 仓库。
2. 如果合同缺失，先完成原文同步、SHA 核对和独立提交。
3. 从最新 `origin/main` 建立独立工作分支。
4. 遵循 `AGENTS.md` 和 Main 控制面准入，不绕过任何门禁。
5. 以当前 Main 为唯一集成基线，不直接提升 PR #317 的代码为 Main。
6. 不重新设计 Orb，不擅自修改冻结资源和视觉参数。
7. 不增加第二套 SceneClock、状态机、Session 或 Attempt 管理器。
8. 不做鱼尾、尾柄、鱼尾尖端识别。
9. 不使用伪造 Mask、固定坐标、虚假鱼种名称或 BBox 轮廓冒充真实模型结果。
10. 本轮必须实际完成代码开发、自动化测试和 Debug APK 交付，不能以审计报告代替工程完成。

**特别注意：合同核验和代码实施是两个阶段，但属于同一个 WP1-B 总任务。合同核验通过后，不需要再次等待产品负责人授权即可继续实施。**

---

# 二、第一阶段：权威合同正式落库核验

## 2.1 核验基线

首先读取：

- `AGENTS.md`
- Main 控制面和准入规则
- Feature Matrix
- Recognition State Machine
- Unified Lifecycle
- Design Registry
- Asset Registry
- WP0 全局审计报告
- WP1 识别业务链路审计报告
- WP1 P0-P1 修复路线

重新解析：

- `origin/main`
- 当前 Main HEAD
- PR #304
- PR #317
- PR #318

之前核对的 Main SHA：

`74cad95b4f673bd9c8c60d16839f036d5886d933`

该 SHA 仅作为历史核对基线，必须以实际远端 Main 为准。

如果 Main 已发生变化，重新审查差异，不得使用过期基线。

## 2.2 必须核验的权威文件

以下文件是本轮必须核验的正式设计依据：

| 文件 | 权威内容 |
|---|---|
| `AI_ORB_WATERLIGHT_13_STAGE_STATE_MACHINE_V3.md` | 13 阶段状态机 |
| `AI_ORB_WATERLIGHT_UNIFIED_LIFECYCLE_V3.md` | Orb × WaterLight 统一生命系统 |
| `recognition_21_frame_matrix.csv` | 21 帧参数矩阵 |
| `recognition_21_frame_matrix.json` | 21 帧结构化数据 |
| `component_runtime_mapping.md` | 组件与 Android Runtime 映射 |
| `YUJIAN_RECOGNITION_VISUAL_CONTRACT_FREEZE_ADDENDUM_V1` | Stage 05–13 最新补充冻结合同 |

另外核验模型合同：

- DET_FISH v0.3
- MODEL_CROP_M1 v0.2
- SlimSAM 模型来源、权重身份和推理合同。

## 2.3 落库规则

逐项检查：

1. 文件是否存在于 Git 仓库。
2. 文件内容是否与冻结版本一致。
3. 文件 SHA-256 是否一致。
4. Git Blob SHA 是否已记录。
5. 是否存在旧版本覆盖新版本的情况。
6. PR #318 是否有未正式落库的设计内容。
7. Main 中是否存在同名但内容不同的文件。

如果文件已经存在且内容完全一致，不重复复制。

如果缺失：

- 在仓库现有设计文档目录中补齐。
- 保留原始文件内容。
- 不重新生成设计参数。
- 不修改已经冻结的原始文件。
- 新增文件必须纳入 Design Registry 和相关索引。

如果需要更新已有冻结文件，只能通过新增版本或补充合同处理，不得静默修改旧合同。

## 2.4 必须形成核验表

每个文件记录：

- 文件名
- 仓库路径
- 来源文件
- 文件 SHA-256
- Git Blob SHA
- Main 是否存在
- PR 来源
- 内容一致性
- 冻结状态
- 最终 Commit

**退出条件：全部权威文件均有明确的仓库身份和版本证据。**

若某项权威文件缺失且无法恢复原始冻结内容，标记 BLOCKED，不得自行重建或猜测。

## 2.5 独立提交

合同落库完成后：

- 建立独立文档提交。
- 保留原始文件 SHA。
- 更新 Design Registry。
- 更新相关索引。
- 形成合同落库核验报告。
- 提供 Commit SHA。

该提交不得修改产品 Kotlin、模型权重或视觉资源。

---

# 三、第二阶段：控制面准入与统一工程基线

合同落库后立即继续。

## 3.1 Main 准入

严格执行仓库规定的控制面准入流程。

如果准入失败：

- 保存完整错误。
- 不绕过检查。
- 不修改 PASS 状态。
- 不继续执行受准入限制的操作。

## 3.2 Main 全局基线

重新核对：

- 最新 Main HEAD
- 工作区是否干净
- Android Gradle 配置
- 依赖和模型资源
- 当前 Android CI
- PR #317、#318 与 Main 的实际差异
- PR #304 已合并资源的实际身份

建立：

`wp1b/recognition-pipeline-visual-handoff-v1`

不得直接在 Main 开发。

PR #317 和 #318 只作为历史代码和设计参考，逐项挑选可复用内容，禁止整体强行合并。

## 3.3 Main CI 前置阻断

WP0 已发现 Main Android CI 在 Gradle 前被 Empty Home V2.2 资产合同阻断。

必须重新确认当前是否仍然失败。

如果仍然失败：

- 定位真实失败文件和断言。
- 对照已经冻结的 Empty Home V2.2 合同。
- 只修复不一致的资产引用、锚点或图层顺序。
- 不改变已经冻结的首页视觉设计。
- 不删除检查，不跳过检查，不降低阈值。

修复后必须让 Main CI 真正进入 Gradle。

如果该问题与本轮识别链路代码可以独立隔离，应使用独立提交，便于追踪和回滚。

**退出条件：控制面准入通过，Main CI 前置阻断已解除，后续测试不再被预检查拦截。**

---

# 四、第三阶段：WP1-B 识别业务链路完整修复

本阶段是核心开发任务。

必须按依赖关系执行，不允许先做视觉动画，再回头补模型和业务数据。

## P0-1：Detector → Classifier → SlimSAM 真实模型链路

### A. Detector

当前 WP1 审计发现 Main 的 Detector 和 bootstrap 仍声明 v0.1，而冻结合同要求 v0.3。

必须完成：

1. 核实批准的 DET_FISH v0.3 模型文件。
2. 核对实际模型 SHA-256。
3. 核对模型加载、预处理和解码实现。
4. 核对 Detector metadata。
5. 核对 bootstrap 版本。
6. 核对实际运行路径是否加载 v0.3。

冻结参数：

| 项目 | 参数 |
|---|---|
| Input | `[1,3,416,416]` |
| Color | BGR planar |
| Pad | 114 |
| Output | `[1,3549,6]` |
| NMS IoU | 0.45 |

不允许仅修改版本字符串而继续加载旧模型。

如果批准的 v0.3 二进制文件缺失，明确报告 BLOCKED，不得拿 v0.1 冒充 v0.3。

### B. Classifier

核对：

- Detector 输出是否正确生成 Crop。
- BBox 扩展是否符合模型合同。
- Crop 是否与当前 Photo Identity 一致。
- 分类器是否实际读取当前 Attempt 的图片。
- Top1、speciesKey、speciesName、confidence 是否一致。
- 分类结果是否正确写入 SessionStore。

冻结参数：

| 项目 | 参数 |
|---|---|
| Model | MODEL_CROP_M1 v0.2 |
| Input | `[1,3,224,224]` |
| Color | RGB |
| Normalize | ImageNet mean/std |
| Pad RGB | `[124,116,104]` |
| Classes | 15 |

必须使用真实分类结果。

禁止使用固定名称、测试名称或假数据驱动正式识鱼动画。

### C. SlimSAM

必须核对：

1. Encoder 是否实际运行。
2. Embedding 是否来自当前照片。
3. Decoder 是否消费当前 Detector BBox。
4. Mask 是否真实生成。
5. Mask 是否完成质量评估。
6. Mask 是否与 Session、Attempt、Photo 一致。
7. Mask 是否进入 Recognition Runtime。
8. 模型权重 SHA 是否能够复算。

补齐 SlimSAM 的模型来源、权重 SHA、代码来源和许可证记录。

不能仅凭类存在或模型文件存在就标记为已完成。

**退出条件：三类模型真实调用关系明确，模型输入输出正确，模型身份可追溯。**

---

# 五、P0-2：Session / Attempt / Photo Identity / Geometry 统一

这是所有后续视觉功能的基础。

必须统一：

- recognitionSessionId
- recognitionAttemptId
- imageId
- photoSha256
- Photo source dimensions
- EXIF orientation
- ROI
- Detector BBox
- Mask identity/version
- Geometry version
- speciesKey
- speciesName
- model identity

## 5.1 单一身份链

一张照片进入识别后，必须始终使用同一份 Photo Identity。

完整链路：

`SelectedImage → Session → Attempt → Detector → Classifier → SlimSAM → Mask → RecognitionScene → Glyph → Result`

所有异步结果必须校验当前 Attempt。

旧 Attempt 的回调不能覆盖当前照片的模型结果、Mask、轮廓或鱼种名称。

## 5.2 Photo Geometry

统一明确：

- Source Photo 坐标。
- EXIF 归一化坐标。
- Detector ROI 坐标。
- SlimSAM ROI 坐标。
- Canonical Photo 坐标。
- Result Viewport 坐标。

修复 WP1 已发现的问题：

**已经转换为 Canonical Photo 坐标的轮廓，不得再次套用 Detector BBox。**

必须建立唯一转换链，禁止不同 Renderer 分别做重复变换。

## 5.3 Photo SHA 规则

WP1 已发现 JPEG bytes SHA 与 SlimSAM bitmap pixel SHA 存在定义差异。

必须先查明现有权威身份合同，明确：

- 原始照片 SHA。
- 归一化照片 SHA。
- 模型输入 Bitmap 身份。
- Mask 对应照片身份。

不能为了让校验通过而随意改 SHA 算法。

必须保证同一照片在 Detector、Classifier、SlimSAM、Result 中具有可追溯的一致身份。

**退出条件：同一张照片的身份、尺寸和几何映射在整个链路中唯一且可验证。**

---

# 六、P0-3：Mask 可信门控和失败降级

必须修复 WP1 发现的两个问题：

1. 非 HIGH 结果在 Mask 不可信时可能进入无法通过的 CONTOUR_A。
2. SlimSAM 的 catch(Throwable) 可能吞掉 CancellationException。

## 6.1 统一 Mask 状态

至少明确：

- NOT_STARTED
- RUNNING
- TRUSTED
- UNTRUSTED
- FAILED
- TIMED_OUT
- CANCELLED

具体命名可以沿用项目现有定义，不要建立第二套状态机。

## 6.2 降级规则

| Classifier | Mask | 处理 |
|---|---|---|
| 有效 | TRUSTED | 允许完整鱼体轮廓和 Fish Gold |
| 有效 | UNTRUSTED | ORB_ONLY |
| 有效 | FAILED | ORB_ONLY |
| 有效 | TIMED_OUT | ORB_ONLY |
| 有效 | CANCELLED | 正确取消，不伪造结果 |
| 无效 | 任意 | 不生成虚假鱼种名称 |

核心要求：

**Mask 不可信不能阻塞一个已经有效的 Classifier 结果。**

ORB_ONLY 模式下：

- 不创建虚假 P*。
- 不生成虚假 Contour。
- 不生成虚假 Fish Gold。
- 不使用 BBox 冒充 Mask。
- 真实分类名称有效时，仍然可以继续 Orb Return、Glyph 和 Result。

## 6.3 超时

沿用现有共享识别预算和 60 秒超时合同。

Detector、Classifier、SlimSAM 不得各自重新获得一个完整 60 秒。

必须覆盖：

- 模型超时。
- 模型失败。
- 协程取消。
- 页面离开。
- Attempt 替换。
- 迟到回调。
- 资源释放。

CancellationException 必须正确保留为 CANCELLED，不能记录为普通模型失败。

**退出条件：所有模型组合都能够进入明确的终态，任何失败均不会无限等待。**

---

# 七、P0-4：Orb 最近轮廓接触点与 Contour A/B/C

依据冻结合同：

`YUJIAN_RECOGNITION_VISUAL_CONTRACT_FREEZE_ADDENDUM_V1`

## 7.1 最近接触点 P*

不做鱼尾识别。

不找尾柄。

不找尾尖。

唯一算法：

当前 Orb 能量出射锚点 O，与当前 Attempt 的可信 SlimSAM Mask 外轮廓 C，经过同一个 PhotoGeometry 映射到相同 Photo 坐标系。

计算：

`P* = argmin(P ∈ C) ||P - O||₂`

P* 必须同时供以下三个功能使用：

1. PATH_TO_FISH 接触终点。
2. CONTOUR_A 初始接触位置。
3. ORB_RETURN 原路径回流锚点。

三处禁止独立计算不同接触点。

必须共享：

- Photo Identity
- Session ID
- Attempt ID
- Mask Identity
- Geometry Version

排除：

- Mask 内部孔洞。
- 噪声轮廓。
- BBox 边缘。
- 旧 Attempt 的 Mask。
- 其他照片的几何信息。

## 7.2 Contour A/B/C

必须使用同一份真实 Mask Path。

| 阶段 | 表现 | 实际几何 |
|---|---|---|
| Contour A | 局部初显 | 真实 Mask Path 局部显现 |
| Contour B | 沿鱼体轮廓连续延展 | 同一 Mask Path 逐步延展 |
| Contour C | 完整闭合 | 同一 Mask Path 完整显示 |

不是三种算法。

不是三套轮廓。

不是三份独立 BBox。

不能用预设 SVG 代替真实鱼体 Mask。

## 7.3 Fish Gold

三层光效：

- Core
- Soft
- Halo

三层均贴合真实 Mask。

不得各自使用不同的鱼体几何。

无可信 Mask 时全部关闭。

**退出条件：P* 算法通过独立几何测试，Contour A/B/C 和 Fish Gold 使用同一真实 Mask 与 Geometry。**

---

# 八、P0-5：Orb 解构、真实鱼种凝字和 Result 完整交接

这是本轮最重要的用户体验交付。

此前真机测试已经出现：

- Orb 解构没有看到。
- 鱼种凝字没有看到。
- Result 照片出现短暂占位块。
- 结果页交接不完整。

这些问题必须进入实际工程修复，不能再以动态 Path 已存在作为完成依据。

## 8.1 Orb × Glyph 六个连续状态

沿用现有 13 阶段状态机和单一 SceneClock。

必须实现：

| 阶段 | 状态 | 必须看到的实际效果 |
|---|---|---|
| 11.1 | 完整水核 | Orb 保持完整生命体，内部水光清晰 |
| 11.2 | 非线性舒展 | 水核横向舒展，产生连续形变 |
| 11.3 | 可见水光解构 | Orb 真实分解为水光、光丝和能量笔势 |
| 12.1 | 笔势聚合 | 水光向真实文字笔势聚合 |
| 12.2 | Glyph 显现 | 真实鱼种 Glyph Path 逐步显现 |
| 12.3 | 真实名称定形 | 真实 Classifier 名称完整、清晰、稳定 |

要求：

1. 六个状态必须连续。
2. 每一步必须有独立的状态进入和完成事件。
3. 必须有可见的形态变化。
4. 不允许整颗 Orb 直接淡出并替换成文字。
5. 不允许预先显示固定中文名称。
6. Glyph 必须对应真实 Classifier 的 speciesKey 和 speciesName。
7. Glyph Path 必须来自真实文字布局。
8. 名称必须完成布局稳定性检测。

尤其是 11.3：

**必须让用户肉眼看到 Orb 从一颗完整的水光生命体，逐渐解构为可见的光丝和笔势，而不是简单透明度降低。**

## 8.2 NAME_STABLE 门禁

这是页面交接的唯一前置条件。

必须同时满足：

- Classifier 真实名称有效。
- Glyph Path 已经生成。
- 名称文字布局完成。
- 当前 Session 有效。
- 当前 Attempt 有效。
- Photo Identity 一致。
- 名称没有继续变化。
- Result 目标几何已经准备好。

以上条件满足后，才允许进入 13.1。

## 8.3 Result 五个连续阶段

| 阶段 | 名称 | 行为 |
|---|---|---|
| 13.1 | RESULT_NAME_HOLD | 凝字完成后稳定展示真实鱼种名称 |
| 13.2 | PHOTO_REPOSITION | 同一张照片开始向结果页目标位置移动 |
| 13.3 | RESULT_HANDOFF | 识别场景向 Result 场景交接 |
| 13.4 | RESULT_INFO_ARRIVAL | 鱼种真实属性、信息和结果内容进入 |
| 13.5 | RESULT_SCENE_STABLE | 结果页全部元素稳定 |

强制顺序：

`NAME_STABLE → 13.1 → 13.2 → 13.3 → 13.4 → 13.5`

禁止：

- 名称未稳定就移动照片。
- 直接导航到 Result 页面。
- 先显示空结果页，再补充鱼种名称。
- 创建第二套时钟。
- 重新创建一张照片代替原照片。
- 在结果页使用不同的 Photo Identity。

**用户看到的必须是一张照片从识别过程连续进入结果页面，而不是两个独立页面的简单切换。**

## 8.4 Result 占位块修复

修复 WP1 已发现的 FishPhotoDerivativeCache miss 占位问题。

要求：

- Cache 命中：直接展示。
- Cache 未命中：优先显示已有原始照片。
- Derivative 完成后平滑替换。
- 不允许照片区域短暂出现黑块、空白块或错误占位。
- Render 失败时保留可用的原始照片。

不得改变照片身份和结果交接门禁。

**退出条件：Orb 六步、名称稳定、Result 五阶段均有可验证的状态和真实数据链路。**

---

# 九、P0-6：完整识别链路测试

不得只测试单个动画组件。

必须建立端到端链路验证。

## 9.1 核心测试

| 编号 | 测试内容 | 通过条件 |
|---|---|---|
| T01 | Detector v0.3 | 输入输出和 Golden Fixture 一致 |
| T02 | Classifier | Crop、类别和真实名称一致 |
| T03 | SlimSAM | 真实 Mask 正确生成 |
| T04 | Mask 可信门控 | GOOD/WARNING/FAILED 正确分支 |
| T05 | 最近接触点 | P* 与独立几何计算一致 |
| T06 | Contour A/B/C | 同一 Mask 连续形成 |
| T07 | Fish Gold | Core/Soft/Halo 同源 |
| T08 | ORB_ONLY | 不可信 Mask 正确降级 |
| T09 | Orb 解构 | 11.1–11.3 连续 |
| T10 | Glyph | 12.1–12.3 使用真实名称 |
| T11 | NAME_STABLE | 名称稳定前不交接 |
| T12 | Result | 13.1–13.5 正确顺序 |
| T13 | Photo Identity | 全流程照片身份不变 |
| T14 | 超时和失败 | 60 秒共享预算正确收敛 |
| T15 | Attempt 切换 | 旧回调不污染新结果 |
| T16 | 页面重建 | Scene 状态和资源生命周期正确 |

## 9.2 几何测试场景

必须覆盖：

- 横向照片。
- 纵向照片。
- 斜向鱼体。
- EXIF 旋转。
- 镜像照片。
- 边缘 ROI。
- 多外轮廓。
- Mask 内部孔洞。
- Mask 质量不足。
- Orb 出射点变化。

重点检查重复 BBox 映射和跨 Attempt 几何污染。

## 9.3 自动化测试范围

至少执行：

- 相关 Unit Tests。
- Recognition Integration Tests。
- Detector/Classifier/SlimSAM 基础推理测试。
- Scene State Machine Tests。
- Geometry Tests。
- Glyph Tests。
- Result Handoff Tests。
- Cancellation/Timeout Tests。
- APK 编译与资源检查。

不能把测试文件存在写成测试通过。

不能把 Gradle Build 成功写成视觉通过。

不能把模拟器运行写成真机验收。

---

# 十、时序合同保护

本轮特别禁止擅自修改时序。

已有：

- Main 候选：Processing 2800ms + RR09 850ms = 3650ms。
- PR #317：6000ms 时序合同。

两者的批准关系仍需要权威证据。

本轮冻结补充合同明确不解决这一争议。

因此：

1. 不自行决定 3650ms 或 6000ms。
2. 不增加第三个总时长。
3. 不重建第二套 SceneClock。
4. 保留现有主阶段与 21 帧矩阵。
5. 六个 Orb/Glyph 状态及五个 Result 状态作为现有主阶段的连续子状态，不增加新的主阶段。
6. 如果实现依赖于尚未裁定的时长参数，保留现有参数并记录阻断，不自行修改。

**视觉完整性优先通过状态和完成门禁保证，不能通过擅自拉长时间来掩盖业务链路问题。**

---

# 十一、实施提交和 PR 管理

所有代码修改必须基于最新 Main。

建议提交顺序：

1. Contract repository sync。
2. Main CI prerequisite fix。
3. Detector / Classifier / SlimSAM。
4. PhotoIdentity / PhotoGeometry。
5. Mask terminal / fallback。
6. P* / Contour / Fish Gold。
7. Orb deconstruction / Glyph。
8. NAME_STABLE / Result handoff。
9. Tests / diagnostics / APK delivery。

每个提交必须能够说明：

- 修改原因。
- 关联缺陷编号。
- 修改文件。
- 是否改变冻结参数。
- 测试情况。

PR 保持独立工作分支，未经产品负责人验收不得自动合并 Main。

---

# 十二、APK 交付要求

本轮必须生成可供产品负责人真机测试的 Debug APK。

不做 API28 专项验证。

不要求 Work 替代产品负责人进行最终真机视觉验收。

交付前必须核验：

| 项目 | 要求 |
|---|---|
| Repository | pan277942135/Yujian_App |
| Branch | WP1-B 独立分支 |
| Source SHA | APK 对应的精确 Commit |
| Model SHA | 实际模型文件 SHA |
| APK SHA-256 | 完整值 |
| APK Size | 实际字节数 |
| Build | 成功 |
| Unit Tests | 分项记录 |
| Integration Tests | 分项记录 |
| Lint | 记录结果 |
| CI | 记录实际 Run ID |

APK 必须与最终源码 Commit 一致。

如果 APK 构建后又修改了代码，必须重新构建，不能继续交付旧包。

## 交付内容

1. `YuJian-debug.apk`
2. APK SHA-256
3. APK 对应 Source Commit
4. 本轮修改文件清单
5. 测试报告
6. 逐项测试用例
7. 合同落库核验报告
8. 更新后的 WP1 追踪矩阵
9. 未完成事项和阻断清单
10. PR 链接

---

# 十三、最终验收标准

本轮不是要求所有文件都修改了，而是要求实际业务链路完整。

最终按以下独立维度报告：

| 验收维度 | 通过标准 |
|---|---|
| Contract | 权威合同已进入仓库并有 SHA 证据 |
| Build | 精确源码可构建 |
| Unit Test | 相关单测通过 |
| Integration | 真实模型与业务链路通过 |
| Identity | Session/Attempt/Photo 一致 |
| Mask | 真实 Mask 可信门控正确 |
| Geometry | 最近接触点与轮廓正确 |
| Orb | 六步可见连续解构和凝字 |
| Result | 名称稳定后按五步交接 |
| APK | 精确源码 APK 可交付 |
| Device | 等待产品负责人真机测试 |
| Visual | 不提前宣称视觉验收通过 |

禁止用一个总 PASS 掩盖单项失败。

## 本轮最终交付必须回答的六个问题

1. 所有冻结合同是否已经正式落库？对应 Commit 和 SHA 是什么？
2. Detector、Classifier、SlimSAM 是否真实打通，而非仅存在代码？
3. Mask 是否真正进入 Orb 最近接触点、Contour 和 Fish Gold？
4. Orb 是否能够真实解构为笔势并凝结为 Classifier 的鱼种名称？
5. 名称稳定后，是否真正完成同一张照片的 Result 页面交接？
6. APK 是否与最终源码和模型版本严格绑定？

---

# 十四、执行边界

本轮允许：

- 修复识别业务链路。
- 修复必要的 Android Runtime。
- 修复模型加载和身份管理。
- 修复 Mask 和 Geometry。
- 完成 Orb、WaterLight、Glyph、Result 集成。
- 修复阻塞 Main CI 的必要前置问题。
- 补充测试和诊断能力。
- 生成 Debug APK。

本轮禁止：

- 重新设计首页。
- 重做 Empty Home。
- 重新制作 Orb 资源。
- 改动冻结的水光材质和视觉参数。
- 新增鱼尾识别。
- 伪造 Mask 和鱼种名称。
- 擅自解决 3650ms/6000ms 冲突。
- 新建第二套时钟或状态机。
- 跳过仓库准入。
- 直接合并 PR。

**最终目标：让渔见 AI 从真实照片输入开始，经过真实模型识别、可信鱼体轮廓、Orb 能量释放、真实鱼种凝字，最后让同一张照片自然进入完整的识鱼结果页面。**

这是 WP1-B 唯一交付目标。

现在开始执行。不要再提交只有审计、计划或设计说明的阶段性成果；合同核验通过后立即进入实际工程修复。遇到无法解决的硬阻断，报告完整证据和具体阻断项，不得伪造完成状态。