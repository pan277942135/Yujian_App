# Work P0 — AI Studio ZIP 全量页面恢复、W5 设计对齐、Android Main 功能完整集成 V1

**Task ID：** `YUJIAN_ANDROID_AISTUDIO_FULL_PAGE_RESTORE_AND_MAIN_INTEGRATION_V1`

**Repository：** `pan277942135/Yujian_App`

**AI Studio 原始文件：** `渔见.zip`（286,588,012 bytes）


**AI Studio ZIP 审计报告：** `渔见_AI_Studio_ZIP_审计报告_2026-11-01.md`

**当前 Main SHA：** `afd9b5814da327a84cd41181acc08624fb5583ad`

**W5 设计来源：** PR #234，集成记录 PR #292

**并行识别开发：** PR #294，启动时重新读取实时 HEAD

**优先级：** P0

**执行模式：** 完整审计 → 全量页面差异恢复 → 冻结合同核对 → 分模块集成 → Android 测试 → 多页面 Runtime 验收 → APK 交付 → 满足条件后合入 Main。

---

# 第一章｜唯一目标

本次不是 Empty Home 专项修复。

必须对 AI Studio ZIP 中所有实际发生变化的 Android 产品页面、共享组件、交互、动效和资源进行完整恢复。

至少重点覆盖：

1. Empty Home 空首页。
2. 首次拍照引导页面（P01/P02/P03）。
3. 高置信度识别结果页面（HIGH）。
4. ZIP 中实际存在差异的其他识别页面及共享组件。

不允许只恢复 Empty Home 就宣布任务完成。

最终目标：

**将 AI Studio 已经完成的有效产品实现完整迁移到当前 Android Main，同时保留 Main 中已经实现的更先进业务逻辑、模型能力、页面状态和识别动效。**

这里的完整恢复不是用 ZIP 覆盖整个 Main。

而是：

- ZIP 提供实际 UI 实现和新增功能。
- W5 提供最新 Empty Home 设计几何。
- 当前 Main 提供业务和工程权威。
- 已合入 Main 的功能必须保留。
- 当前开放 PR 中已经完成的有效实现必须保护。
- 存在合同冲突时先登记，不能静默覆盖。

# 第二章｜必须恢复的页面范围

## A. Empty Home 空首页

重点恢复：

- 鱼漂居中位置。
- 鱼线自然弧度和垂直连接。
- 鱼漂间歇性运动。
- 鱼漂运动期间的同步触感。
- 三层水面涟漪。
- 3.6 秒涟漪扩散。
- 鱼漂、水面接触点、倒影和涟漪的统一几何。
- 首页标题资源。
- 环境动效及其他 ZIP 中实际修改的 Empty Home 功能。

### A1. 设计位置以 W5 为准

W5 已经完成的鱼漂位置调整必须保留。

1080×1920 参考画布：

- 鱼漂中心目标：`(531, 1161)`。
- 鱼线自然弧度。
- 鱼漂连接段自然垂直。
- 鱼漂接触点、倒影、涟漪保持同一构图关系。

注意：

AI Studio ZIP 的 `EmptyHomeMotion.kt` 使用过：

- Bobber Top Y = 1073。
- Water Contact Y = 1146。

这组坐标不能直接覆盖 W5 的 `(531,1161)` 鱼漂中心目标。

必须根据实际鱼漂资源尺寸、中心锚点和接触点重新换算。

不要把中心坐标误当成顶部坐标。

### A2. 鱼漂间歇运动

必须恢复 ZIP 中的事件驱动运动实现：

`calculatePeriodicBobberMotion`

不再以持续正弦上下摆动作为唯一运动方式。

必须具备：

1. 安静等待状态。
2. 间隔触发运动。
3. 鱼漂轻微下沉或响应。
4. 运动自然回弹。
5. 进入下一次等待。
6. 运动事件可重复、可确定性计算。

使用 ZIP 原有周期、持续时间和运动曲线作为优先参考。

不得擅自把间歇运动改成持续循环动画。

### A3. 鱼漂触感

保留 ZIP 的：

`bobberNibbleHaptic`

但必须修复事件绑定：

- 鱼漂运动开始时触发触感。
- 同一运动事件只触发一次。
- 不得仅依赖 Ripple 事件间接触发。
- 不得出现鱼漂没动但手机振动。
- 不得出现鱼漂已经运动但没有触感。
- 页面初次进入不自动振动。
- 页面后台不振动。
- Reduce Motion 和系统触感关闭时遵守系统策略。

如果 ZIP 中是双脉冲，优先保留其原有脉冲结构。

必须验证真实设备触感，不得只凭代码存在就宣布通过。

### A4. 三层涟漪

用户已经明确批准 ZIP 中的多层涟漪（3.6 秒）和鱼漂自动触感。

因此：

**保留已经合入 Main 的多层涟漪和触感实现，不得回退为旧单层涟漪。**

保留：

- 主波。
- 次波。
- 第三波。
- 水面接触环。
- 点击响应波。

使用 ZIP 已有 3.6 秒运动曲线和尺寸参数。

涟漪中心必须与鱼漂真实水面接触点一致。

### A5. 统一 Fishing Composition

以下组件必须作为一个几何整体：

```text
FishingComposition
├── Rod
├── FishingLine
├── Bobber
│   ├── BobberArtwork
│   ├── IntermittentMotion
│   └── MotionHapticEvent
├── WaterContact
├── Reflection
└── Ripple
    ├── PrimaryWave
    ├── SecondaryWave
    └── TertiaryWave
```

不能只移动鱼漂图片，而不移动对应的鱼线连接、倒影和水波。

必须保留响应式变换。

### A6. 标题资源

保留已经核验的 Empty Home 标题资源：

SHA-256：

`36b7ad90c6b685d286ba54ef92659f58bfb5e28b29824848c6cfded06ecf4cf5`

不得重新生成、压缩替换或使用不同版本覆盖。

---

## B. 首次拍照引导页面

本次必须完整恢复 AI Studio ZIP 对首次拍照引导页面的有效改进。

涉及：

- `RecognitionFirstCaptureGuide.kt`
- `RecognitionFirstCaptureGuidePolicy.kt`
- `RecognitionFirstCaptureGuideStore.kt`
- `RecognitionFirstCaptureGuideReferenceSpace.kt`
- 相关资源和页面布局。

### B1. 页面数量和业务顺序

当前有效权威是三页：

`P01 → P02 → P03`

必须保留三页。

ZIP 将页面改成两页的修改，不能直接覆盖现有三页业务合同。

P03 横屏横向鱼教学不能因为 ZIP 没有对应资源而删除。

### B2. 恢复 ZIP 中的布局改进

允许优先恢复：

- 中间内容区域垂直居中。
- 底部渐变遮罩更通透。
- 主操作按钮右侧对齐。
- 窄屏布局。
- 大字体堆叠。
- 页面间一致的导航。
- 内容区与底部操作区的合理空间分配。

底部渐变、垂直居中和按钮布局必须从 ZIP 提取真实参数，不得凭主观重新设计。

### B3. 保留当前业务门禁

首次拍照引导只能在以下条件全部满足时出现：

1. 首次进入 Capture。
2. Camera 权限已经允许。
3. Camera Provider 已就绪。
4. Preview 已经真正 Ready。
5. `capture_guide_v1_completed=false`。

必须保留：

- 跳过。
- 开始拍照。
- 不自动触发快门。
- 本地一次性完成状态。
- 相册入口不显示相机教学。
- 权限拒绝不提前标记完成。
- Camera 未就绪不显示引导。

不得为了恢复 ZIP 的页面效果而重建 CameraX 或改变拍摄业务流程。

### B4. 图片资源审核

ZIP 新增以下候选资源：

```text
first_use_guide_bg_p01.jpg
first_use_guide_bg_p02.jpg

first_use_guide_p01_head_out.png
first_use_guide_p01_occlusion.png
first_use_guide_p01_too_small.png

first_use_guide_p02_head_out.png
first_use_guide_p02_occlusion.png
first_use_guide_p02_too_small.png
```

这些资源在当前资源清单中仍然是：

`CANDIDATE_NEEDS_APPROVAL`

不能因为 ZIP 代码已经将状态设为 AVAILABLE，就直接进入正式 Runtime。

Work 必须：

1. 提取 8 个资源。
2. 计算 SHA-256。
3. 检查尺寸、来源、透明通道。
4. 生成资源对照表和缩略图联系表。
5. 对照现有 Frozen P01/P02/P03。
6. 检查是否存在版权、隐私和素材授权问题。
7. 提交给产品负责人确认。

未获批准前：

- 不得把候选图片作为正式运行时素材。
- 不得将历史扁平截图裁切冒充独立原始照片。
- 不得把 P01/P02 素材错误映射到 P03。
- 不得把资源状态标成 APPROVED。

布局和组件集成可以继续，不因素材审批阻塞其他页面工作。

---

## C. 高置信度识别结果页面 HIGH

本次明确纳入高置信度结果页的恢复和核对。

当前 Android 文件：

`app/src/main/java/com/yujian/ai/ui/screens/RecognitionResultScreen.kt`

当前 Main 文件 SHA（启动时重新核验）：

`5f1f0e67420303535a8c41fee11681fbe524d5cc`

设计权威：

`05_Result_High_Frozen.png`

同时检查：

- Recognition Result V1 3+2。
- Result HIGH 页面结构。
- Result Hero Media Contract。
- RR07。
- RR08。
- RR09。
- 当前共享 Typography / Color / Geometry / Component 合同。

### C1. ZIP 差异必须完整核查

首先检查 AI Studio ZIP 是否修改了：

- `RecognitionResultScreen.kt`
- HIGH 页面相关组件。
- Result Hero 图片展示。
- 结果标题和鱼种名称。
- 置信度展示。
- CTA。
- 信息区域。
- 图片与内容的布局关系。
- 页面资源和对应测试。

不能只看 Manifest。

如果 ZIP 存在实际 HIGH 页面变更，必须逐项恢复有效差异。

如果 ZIP 没有相关实际代码或资源差异，必须如实报告，不得编造 AI Studio 已实现的功能。

### C2. 必须保留的业务语义

High 结果：

- 使用本次真实识别的 Top-1。
- 真实鱼种中文名称。
- 真实置信度。
- 当前照片与识别结果绑定。
- 用户可以修改鱼种。
- 正式保存逻辑保持现有实现。
- 首次展示不能预先进入 Loading。
- 只有用户触发对应操作才进入 Loading。

不得引入：

- 固定鱼种名称。
- 固定置信度。
- Mock 识别结果。
- 假的照片资源。
- 虚构鱼种数据。

### C3. 视觉恢复

以当前冻结 HIGH 设计为权威。

优先恢复 ZIP 中已经完成且不违反 Frozen Authority 的：

- 页面布局。
- 内容排布。
- 字体和层级。
- 照片展示。
- 鱼种名称表现。
- 置信度信息。
- CTA 样式。
- 响应式布局。
- 组件间距。

如果 ZIP 的 HIGH 页面设计与 `05_Result_High_Frozen.png` 存在明显视觉冲突：

不要直接覆盖 Frozen。

先生成：

`AI_STUDIO_HIGH_RESULT_RESTORE_COMPARISON.md`

并提供：

- Frozen 原图。
- ZIP 候选效果。
- 当前 Main 实际效果。
- 差异说明。
- 推荐恢复方案。

只对存在真实权威冲突的视觉决策请求产品负责人确认。

### C4. 保护 AI Orb / RR09

当前开放 PR #294 已修改：

- `RecognizingScreen.kt`
- `RecognitionResultScreen.kt`
- 13 阶段诊断 UI。
- Orb / WaterLight 结果揭晓门禁。
- `realResultReady` 条件。

PR #294 当前为 Draft。

恢复 HIGH 页面时必须读取其精确 HEAD 和完整差异。

不得用 AI Studio ZIP 中较旧的结果页代码覆盖 PR #294 的有效实现。

必须保留：

- 真实 Result Ready。
- Photo Handoff。
- Orb 原始身份。
- RR09 时间关系。
- Fish Focus。
- 13 阶段诊断 UI。
- 真实鱼种名称揭晓。

将 ZIP 的有效页面布局与这些现有能力整合，而不是二选一。

---

# 第三章｜全量 ZIP 审计：不能只恢复三个页面

ZIP 有 1,559 个条目。

当前审计已经发现 Manifest 存在遗漏。

因此 Work 必须对整个 ZIP 做完整文件级差异审计。

## 3.1 建立唯一文件清单

生成：

`AI_STUDIO_ZIP_FULL_RESTORE_INVENTORY.json`

和：

`AI_STUDIO_ZIP_FULL_RESTORE_INVENTORY.md`

每个文件必须包含：

- ZIP 内路径。
- 文件类型。
- 文件大小。
- SHA-256。
- Main 对应路径。
- Main 当前 SHA。
- ZIP 与 Main 是否相同。
- 是否在 CHANGESET_MANIFEST。
- 所属页面或模块。
- 依赖组件。
- 业务影响。
- 资源授权状态。
- 恢复建议。
- 最终处理结果。

## 3.2 文件分类

必须将所有差异分类：

| 分类 | 处理 |
|---|---|
| UI 页面 | 恢复有效布局和交互 |
| 页面状态 | 对齐当前业务合同 |
| 动效 | 保留有效实现并统一时序 |
| 资源 | 核验 SHA、来源和授权 |
| 共享组件 | 合并，不重复创建 |
| 测试 | 迁移有效测试并补齐 |
| 设计合同 | 与现有权威核对 |
| 模型 / 推理 | 不擅自替换 |
| 后端接口 | 不擅自改变 |
| 构建与 CI | 只在确有必要且经过验证时修改 |
| 临时文件 | 不进入正式产品 |

## 3.3 全量页面范围

除了前三个重点页面，ZIP 中其他实际修改的页面也必须检查。

不能根据本指令中的页面清单推断 ZIP 只有这些变化。

所有实际页面差异必须进入最终恢复报告。

如果发现 Normal Home、Capture、Processing、其他 Result 状态或共享组件有真实变化，也必须分类并核对。

与当前权威冲突的部分单独登记。

---

# 第四章｜三方恢复规则

必须建立：

`AI_STUDIO_W5_MAIN_THREE_WAY_RESTORE_MATRIX.md`

| 来源 | 权威职责 |
|---|---|
| AI Studio ZIP | 已完成的页面实现、动效、布局、组件和资源来源 |
| W5 设计候选 | Empty Home 最新视觉位置与构图 |
| Android Main | 业务状态、正式代码、识别链路和工程运行权威 |

执行规则：

1. ZIP 不能直接覆盖 Main。
2. W5 不能只作为截图参考，必须提取有效几何合同。
3. Main 中已有的功能不能因为 ZIP 代码较旧而被删除。
4. 同一文件有多方改动时，必须逐段合并。
5. 不允许使用整目录覆盖。
6. 不允许遗漏 Manifest 之外的差异。
7. 不允许将 AI Studio 的自报 PASS 当作 Android Runtime PASS。

## 4.1 当前 Main 的保护范围

必须重点保护：

- AI Orb × WaterLight A02。
- PR #294 的 13 阶段实现。
- RecognitionVisualStateController。
- RecognitionRevealClock。
- RecognitionRevealHandoff。
- RecognitionRevealOpticalRenderer。
- SpeciesNameReveal。
- 真实 Detector / Classifier。
- SlimSAM Mask。
- Capture CameraX。
- PhotoSession。
- RR09。
- 当前结果保存业务。
- 当前已经合入的三层涟漪与触感。

## 4.2 并行 PR 冲突

启动时重新检查：

- PR #294。
- PR #254。
- PR #249。
- PR #208。
- 其他涉及同一页面和共享组件的开放 PR。

特别是 PR #294：

不得覆盖其 13 阶段和 Result 揭晓修复。

如果两个 Work 同时修改相同文件：

1. 明确文件 Owner。
2. 读取双方最新 HEAD。
3. 合并功能而不是覆盖。
4. 更新相关测试。
5. 记录合并来源和最终 SHA。

不能为了赶进度直接使用旧版本替换新版本。

---

# 第五章｜不确定事项与产品负责人确认机制

本任务允许 Work 自主完成确定性工作。

但不允许擅自替产品负责人决定存在冲突的视觉和资源事项。

建立：

`AI_STUDIO_RESTORE_DECISION_REGISTER.md`

每个问题必须包含：

- 问题编号。
- 对应页面。
- ZIP 原始实现。
- 当前 Main 合同。
- 冲突截图或证据。
- 两种可行方案。
- 对其他功能的影响。
- Work 推荐方案。
- 等待确认的具体问题。

### 必须主动提请确认的事项

**D01：鱼漂是否采用 ZIP 的倾斜 / 旋转。**

已经明确批准：

- 间歇运动。
- 运动期间自动触感。
- 3.6 秒多层涟漪。

但 ZIP 还增加了 dip / tilt / rotation，和原有 Y-only 运动合同存在冲突。

Work 必须先恢复间歇运动的确定性部分，并提供：

- 仅 Y 轴间歇运动。
- ZIP 完整 dip / tilt 运动。

两种运行效果对比。

未经确认，不擅自启用额外旋转。

**D02：首次拍照引导的 8 个候选图片资源。**

必须提供缩略图联系表、来源和授权信息，请产品负责人确认是否允许进入正式 Runtime。

未经确认，不得标记为已批准。

**D03：HIGH 结果页与 Frozen 视觉权威冲突。**

如果 ZIP 只是优化布局且与 Frozen 一致，直接恢复。

如果需要改变已冻结的视觉结构，先提交对比，询问产品负责人。

### 执行原则

不要因为上述少量事项暂停全部开发。

Work 可以继续完成：

- 文件审计。
- 代码整合。
- 非争议布局。
- 几何修复。
- 测试。
- 已批准资源。
- 不影响争议部分的页面功能。

仅暂停需要用户决策的具体子项。

---

# 第六章｜正式实施顺序

## Phase 0：全量审计

- 获取当前 Main 最新 SHA。
- 获取 ZIP。
- 计算 ZIP 文件 SHA-256。
- 解压并核验全部条目。
- 重新生成完整 Manifest。
- 核对 W5 设计来源。
- 核对全部相关 PR。
- 建立三方差异矩阵。

完成后提交第一阶段审计报告。

## Phase 1：Empty Home 恢复

- W5 鱼漂居中。
- 鱼线自然连接。
- 间歇运动。
- 自动触感。
- 三层涟漪。
- 3.6 秒动画。
- 响应式。
- 资源 SHA 校验。

更新 Motion、Haptic、Anchor、Layer 合同及测试。

## Phase 2：首次拍照引导恢复

- 保留 P01/P02/P03。
- 恢复 ZIP 布局。
- 保留现有相机 Ready 门禁。
- 保留首次使用状态。
- 资源候选隔离。
- 完成响应式和字体测试。

## Phase 3：HIGH 结果页恢复

- 核对 ZIP 实际改动。
- 与 Frozen HIGH 对照。
- 恢复有效页面结构。
- 保留真实数据。
- 保留 AI Orb / WaterLight / RR09。
- 修复与 PR #294 的代码冲突。
- 测试 CTA 和保存业务。

## Phase 4：其他页面与共享组件

逐一处理全量 ZIP 差异中其他已确认的页面。

不得遗漏实际变更。

不得重复开发已经存在的共享组件。

## Phase 5：跨页面一致性

重点检查：

- Empty Home → Capture。
- Capture → First Capture Guide。
- Guide → 正常拍摄。
- Capture → Processing。
- Processing → HIGH Result。
- HIGH Result → 保存。
- 保存 → 首页。

页面间保持统一的品牌视觉、按钮、字体、背景、照片连续性和状态反馈。

## Phase 6：测试和 Runtime

必须进行：

### Empty Home
- 静态布局。
- 鱼漂位置。
- 间歇运动。
- 同步触感。
- 三层涟漪。
- 至少 36 秒完整事件周期。
- 后台恢复。

### First Capture Guide
- P01/P02/P03。
- 首次显示。
- 跳过。
- 开始拍照。
- 相机未就绪。
- 权限拒绝。
- 相册入口。
- 大字体。
- 窄屏。

### HIGH Result
- 真实照片。
- 真实识别名称。
- 真实置信度。
- 初始 CTA 非 Loading。
- 点击后 Loading。
- 修改鱼种。
- RR09 连续交接。
- 保存和返回。

### 全局
- API28。
- 至少一个当前主流 Android Runtime。
- 320dp、360dp、390dp、430dp 等适用宽度。
- 字体缩放 1.0、1.3、1.5。
- 生命周期。
- 页面返回。
- 动效暂停与恢复。

不能用静态预览冒充 Runtime。

## Phase 7：APK 和 Main 收口

最终交付：

- 可安装 Debug APK。
- APK SHA-256。
- 对应 Git SHA。
- PR 链接。
- Main 合并状态。
- 页面级运行截图。
- 关键动效证据。
- 完整差异清单。
- 未解决决策清单。

满足仓库规定的检查后，可以自主合入 Main。

不允许未经检查直接覆盖 Main。

---

# 第七章｜最终交付报告

必须生成：

`AI_STUDIO_FULL_PAGE_RESTORE_COMPLETION_REPORT_V1.md`

至少包含：

1. 原始 ZIP SHA-256。
2. 原始 ZIP 完整文件清单。
3. Manifest 遗漏项。
4. 实际修改文件数量。
5. Empty Home 恢复情况。
6. First Capture Guide 恢复情况。
7. HIGH Result 恢复情况。
8. 其他页面恢复情况。
9. 资源授权状态。
10. 设计合同更新。
11. 与 PR #294 的整合情况。
12. 自动化测试。
13. API28 Runtime。
14. 真机运行证据。
15. APK 下载链接及 SHA-256。
16. 精确 Main SHA。
17. PR 状态。
18. 用户待确认事项。

每项只能标记：

- PASS
- PARTIAL
- BLOCKED
- NOT_RUN

禁止把代码合并等同于页面验收通过。

---

# 最后执行要求

本次任务的核心是：

**把 AI Studio 已经完成的有效产品成果完整恢复到当前渔见 AI Android APP，而不是重新开发一遍，也不是选择性只合入部分代码。**

必须确保：

1. Empty Home 不再丢失鱼漂居中、间歇运动和同步触感。
2. 首次拍照引导保留完整三页。
3. 高置信度结果页完整核对并恢复有效改动。
4. ZIP 中其他实际修改页面不遗漏。
5. 已经合入 Main 的三层涟漪、AI Orb、WaterLight、RR09 等能力不被覆盖。
6. 所有资源和代码均有来源、SHA 和恢复记录。
7. 不确定事项及时提交产品负责人确认。
8. 不因局部待确认事项停止其他模块。
9. 最终交付真实可安装 APK 和运行证据。

**Work 自主负责全量审计、代码整合、冲突解决、测试、提交、PR 更新、符合条件后的 Main 合并和 APK 交付。**

不要只给计划，不要只给审计报告，必须实际完成代码恢复。

从 Phase 0 开始，逐阶段提交并持续推进，直到形成完整交付闭环。