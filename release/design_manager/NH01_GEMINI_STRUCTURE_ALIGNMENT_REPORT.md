# NH01 Gemini 结构映射与交付审计报告

**Task ID:** `YUJIAN_NH01_GEMINI_FIGMA_ANDROID_CLOSURE_V2`  
**仓库：** `pan277942135/Yujian_App`  
**报告状态：** `BLOCKED_FIGMA_WRITE_PERMISSION`  
**本次处理范围：** 原始 HTML 审计、结构原型净化、增量映射、既有仓库与运行证据核查。

## 1. 结论

Figma 原生编辑是进入 Android 生产修改的强制门禁。本次 Figma MCP 在 Starter 计划上返回调用额度错误，连目标文件元数据和节点读取都无法完成，因此不能做最小写入验证，也不能证明文件的实际编辑权限。没有编辑产品页面、Frozen 资产或 Android 生产代码；没有声称 Figma 已更新。

按任务规格将阶段 A 标记为 `BLOCKED_FIGMA_WRITE_PERMISSION`，阻塞根因记录为 `STARTER_FIGMA_MCP_CALL_LIMIT`。此前 Node Mapping 中的页、Frame 和节点 ID 都只作为候选值保留；本次映射的 `figma_node_id` 与 `parent_node_id` 均为 `null`，等待恢复实时读取后确认。

已完成不依赖 Figma 写入的工作：核验原始 HTML 字节和 SHA、审计 43 个 HTML 图层、制作 38 图层的结构参考原型、记录真实动态字段与交互、核查冻结资产和现有 Android 运行证据。原型明确标注为结构参考，不是 Figma 对齐或视觉验收结果。

## 2. 输入、基线与提交范围

| 对象 | 当前值 | 状态 |
|---|---|---|
| 原始 Gemini HTML | `粘贴的文本 (1)(6).txt`，18,331 bytes，SHA-256 `1988a9343a6e911c87f93871279ba6d5e161e2ef0651ecd0d783526c5bceb714` | 原件保留，未修改 |
| Frozen NH01 全屏参考 | `design/system/core_visual_v1/reference/normal_home_v1.png`，1080×1920，SHA-256 `6ab9d3348b4a9a7e77ddca3a06235b4991798a309bd3512cc6fb9ea7aeb1d377` | `PASS`；未修改 |
| `main` | `0d3238e8a41b87363e9a17b04c9cc45fca4c426d` | 基线 |
| PR #164 | `design/m1-nh01-figma-structural-handoff-v2`，HEAD `77a9aabc2648bda2942575d6600b74c9fe7d3ad7` | Draft / Open / Unmerged；本次结构交接承接分支 |
| PR #167 | `design/nh01-cloud-structured-parity-v1`，HEAD `8481cecebd057f84858e5548163fbb61bb4d50e7` | Draft / Open / Unmerged；只读取现有 Viewer 与运行证据 |
| 既有 Node Mapping V2 | `release/design_manager/M1_NH01_FIGMA_NODE_MAPPING_V2.json`，Git blob `606bf5470181a0201f0723adbc24ca27c5a50401` | `REVIEW_REQUIRED`；ID 仅为候选锚点 |

本次产物拟放在 PR #164，不改写旧 Node Mapping V2，不修改 PR #167 的 Android 或 Viewer 源码，也不合并 `main`。

## 3. Figma 门禁

- Figma 身份读取显示账户为 Starter 计划、Full Seat、admin；身份信息不等于文件编辑权限证明。
- 后续 Figma metadata、design context 和节点读取均被工具调用额度错误阻止：`You've reached the Figma MCP tool call limit on the Starter plan...`。
- `figma_node_id`、`parent_node_id`、目标页导出、图片填充回读、节点写入/删除测试均为 `NOT_RUN`。本次没有留下测试节点，也没有升级计划或调用付费服务。
- 既有映射记录的候选值：page `25:2`、主 Frame `41:30`（1080×1920）、响应验证 Frame `41:91`（1080×2340）；本次未能实时复核。
- 增量映射 JSON 为全部 43 个源图层保留候选 ID 字段；没有把候选值冒充为实时 Figma 读取结果。

恢复条件：Figma MCP 调用额度可用后，先重新读取当前文件/页面/节点，再完成隔离节点的写入、回读和删除验证；只有通过后才替换正式背景、品牌和共享相机组件，并导出当前页面做检查。

## 4. Gemini HTML 结构审计

源 HTML 有 43 个 `data-layer` 图层，发现 5 个 Unsplash 图片引用、1 个 `onclick="return false"` 占位处理，以及固定演示值：统计 `12 / 38 / 7`、示例草鱼、`42.6 cm`、`1.28 kg`、昨天 18:20、浙江·千岛湖。它还包含未登记的氛围渐变和芦苇前景，以及 HTML 自绘相机内圈与 SVG。

结构原型移除了上述外部图片和非正式装饰，将图片改成有明确来源要求的动态槽位，将品牌、背景和相机改成正式资源/共享组件槽位；移除测试设备控制栏；把静态统计和鱼获信息改为动态字段；把 `return false` 替换为已有业务动作映射。动态字段为：

`species_count`、`catch_count`、`record_days`、`catch_image`、`species_name`、`length_cm`、`weight_kg`、`captured_at`、`location`、`catch_id`。

原型校验结果：38 个 `data-layer`；0 个 Unsplash 引用；0 个 `<img src>` 外链；0 个 `return false`；上述 10 个动态字段全部存在。HTML 仍保留原有 Tailwind/字体外部样式加载，且资源槽位没有真实图片，所以它不用于视觉对比或产品截图。

| 产物 | SHA-256 | 状态 |
|---|---|---|
| `NH01_GEMINI_STRUCTURE_ALIGNMENT_V1.json` | `cc314d2af418c804a2e84cac07e7308db6910e6c2a33b85783c1cab6658dae04` | 43 行结构映射；候选 ID 与实时 ID 分离 |
| `NH01_GEMINI_REFERENCE_PROTOTYPE_NOT_VISUAL_PASS.html` | `2982b40f8def980d0d77c6b1ac264442a9b0fa1e11124e4e41b4963f24123b3e` | `PASS`：结构/字段审计；不是视觉验收 |

## 5. 正式资源与设计合同核对

| 资产/规则 | 正式值 | 本次状态 |
|---|---|---|
| 背景 | `design/system/backgrounds/morning_lake_v1/assets/Morning_Lake_Master_V1.png`，941×1672，SHA-256 `5fba741088ea186e898cd3bee5777e35978436f427492e6e6122528ef6aa91d7`；Android 目标 `.../static/scene_base.png` | 仓库运行时资源路径可复用；Figma 41:32 的 IMAGE/Crop 回读 `NOT_RUN` |
| 品牌 | `brand_title_primary.png`，SHA-256 `2dd41f5138e88e3bd48178dd26a15dfbb29c434536e4e3cc887fe2db13bdfddc` | 既有记录曾回读 IMAGE/FIT；本次未刷新节点与画布视觉 |
| 相机 | `normal_home_camera_primary_v1.png`，SHA-256 `fb6417e372d08328ac8559d004d1c6f5b03fc17551646be46031df641660e8f2` | 正式共享组件槽位已映射；本次 Figma 节点回读 `NOT_RUN` |
| 头像 | NH07 登录头像、默认头像、加载/失败和 Guest 状态必须分离 | `REVIEW_REQUIRED`：全屏复核与默认/Guest 权威仍待确认 |
| 鱼获照片夹具 | F01–F08 批准原图、来源/授权、构建追溯和首页/详情运行证据 | `BLOCKED_ASSET_SOURCE`：当前没有完整合格矩阵 |

`State_Interaction_Contract_V1.md` 对首页状态选择和统计数值来源描述的是不同层次：页面状态依据有效鱼获记录；统计优先用正数服务端总量，无可用总量时用有效本地记录回退，记录天数按清洗后的日期去重。PR #167 的 `HomeStats.kt` 与此规则一致，本次没有发现这两项之间的合同冲突。

仍需按同一对象类型消解的几何/字体项：冻结 CTA 文本 A1 为 40/44，旧 Node Mapping 记录为 36/40；冻结 CTA 容器为 420×58，而既有 API28 运行记录为 420×75；触控区域、可见文字墨迹、相机视觉 Mask 和窗口坐标必须分别比较。没有通过任选一个数字或扩大容差来掩盖差异。

## 6. 现有 PR 与 API 28 证据

PR #164 当前 HEAD 的 control-plane、Native Importer Validation 和 Android CI 三项均成功（Runs `39177894762`、`39177894709`、`39177894769`）。Importer Validation 的绿色结果不证明本次 Figma 文件已写入。

PR #167 当前 HEAD 的 Android CI、control-plane、Design Governance、Design Manager Pages 均成功（Runs `39219487032`、`39219487054`、`39219487061`、`39219487087`）。现有 API28 运行 [Run 39219487046](https://github.com/pan277942135/Yujian_App/actions/runs/39219487046) 已构建、安装并运行仪器测试，但 `Enforce real Android Runtime gate` 失败，最终状态为 `FAIL_EVIDENCE`：

- API 28 Pixel 2 模拟器，1080×1920，480 dpi，fontScale 1.0；截图 SHA-256 `ebbdf77ad2f027207db081d1450eb1501bc8eccfcac7289040aa05687c8ab545`。
- 运行 APK 的 SHA-256 为 `fc9050706a3d803f204190284bf2dd1ef8892f9a9ebddf265bcf14db349032fc`。这是 PR #167 既有运行产物，不是本次 Figma 门禁后的 Android 交付 APK。
- Frozen 关键 CTA/相机像素 Mask、排版、响应式和动效子门禁为 `PASS`；Hero 夹具为 `BLOCKED`，层级/几何为 `FAIL`。全屏像素差异报告仅诊断，不构成整页视觉 PASS。
- P0 `HIERARCHY_OR_GEOMETRY`：相机触控 Bounds 为 216×121 px；480 dpi 下 48dp 最小要求是 144×144 px。CTA 页面本地布局 Bounds 为 `[330,1495,750,1570]`，合同目标为 `[330,1495,750,1553]`。
- P1 `HERO_APPROVED_FIXTURE_MATRIX`：F01–F08 原始照片来源/授权或构建与运行证据不全。夹具预检状态为 `BLOCKED`，Hero 运行矩阵未通过。

这些是当前 PR #167 的旧分支证据。本次没有改动该分支、没有重跑 Android，也没有把这个 APK 宣称为本任务已验收的最终包。

## 7. 本次执行状态

| 工作项 | 状态 | 证据/说明 |
|---|---|---|
| 原始 HTML 字节与 SHA | `PASS` | 18,331 bytes；原件未改 |
| 43 图层 HTML → Figma → Compose 候选映射 | `REVIEW_REQUIRED` | 映射已写；Node ID 均未实时确认 |
| 结构参考原型与动态字段清理 | `PASS` | 明确标记 `NOT_VISUAL_PASS` |
| Figma 原生写入/回读/导出 | `BLOCKED_FIGMA_WRITE_PERMISSION` | Starter MCP 调用额度错误 |
| 固定资源的 Figma 画布核验 | `NOT_RUN` | 没有 IMAGE Fill、引用或页面导出回读 |
| 头像最终资源权威 | `REVIEW_REQUIRED` | NH07 状态语义保留，首页视觉复核未完成 |
| Hero 照片区域验收 | `BLOCKED_ASSET_SOURCE` | 8 个批准来源夹具未齐 |
| 本任务 Android 生产修改/构建/APK | `NOT_RUN` | Stage A Figma 门禁未通过，按规格没有开始 |
| 既有 PR #167 API28 精确 HEAD 运行 | `FAIL` | Run `39219487046`；P0 触控几何与 P1 照片夹具阻塞 |

## 8. 后续恢复步骤

1. Figma MCP 调用额度可用后，实时读取目标文件、页和 Frame；用隔离节点完成写入/回读/删除检查。
2. 在原生节点上复用现有结构，导入登记背景并核验居中 Crop、品牌 Artwork 和共享 Capture 组件；读取真实字段、父子关系、组件和字体，再导出页面证据。
3. 复核头像默认/Guest 资产、Chevron 登记、CTA 字体角色和触控 Bounds。对鱼获照片缺口保持 `BLOCKED_ASSET_SOURCE`，不造图或使用未经授权图片。
4. 只有阶段 A 的结构、固定资源、关键布局和业务合同满足门禁后，才在承接 Android 的分支修复明确的合同差异、执行测试并跑准确 HEAD 的 API28 验收，附 APK、SHA、截图和 Bounds。

截至本报告，PR #164/#167 均保持 Draft/Open/Unmerged，`main` 未改动。
