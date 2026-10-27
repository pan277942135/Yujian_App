# 渔见设计管理

Design Manager V1 采用两层结构：

## 第一层：公共设计系统

跨页面复用、由页面引用：

- 背景系统
- 主拍摄按钮
- 顶部导航
- 雾面玻璃
- 颜色与字体
- 间距与圆角

数据来源：

`design/registry/shared_design_system_v1.json`

页面不能复制公共元素后私自修改。需要变化时，应升级公共系统版本或登记明确的页面例外。

## 第二层：页面模块

页面自己的设计状态、视觉权威、版本与设计维度：

`design/registry/experience_registry_v1.json`

每个页面通过 `shared_system_refs` 声明使用哪些公共系统和变体。

例如：

```text
空首页
├── 背景系统 / BG_ENV_HERO
├── 主拍摄按钮 / home_primary_capture
├── 颜色与字体
└── 间距与圆角
```

## 顶部导航结构

顶部导航采用 **父级总览 + 3 个直接子菜单**：

```text
顶部导航
├── 01 · 标题
├── 02 · 返回 + 标题
└── 03 · 返回 + 标题 + 工具动作
```

规则：

- 一级「顶部导航」默认收起；
- 点击父级只显示 Top Navigation 总览；
- 点击子菜单直接进入对应组合工作区；
- 右侧不重复生成 Variant 卡片索引；
- 旧 `search_filter` Variant 已移除；
- Search / Filter 属于页面内容工具或输入系统，不再作为 Top Navigation 结构；
- 子菜单内部 ID 保留为 `TITLE_ONLY / BACK_TITLE / BACK_TITLE_ACTIONS`，UI 仅显示中文编号名称。

## 一级菜单折叠规则

Design Manager 左侧导航的一级菜单统一支持折叠，并且**默认收起**。

适用：

- 背景系统；
- 按钮规范；
- 拥有高保真子菜单的页面模块。

规则：

- 默认不展开子菜单；
- 点击一级菜单：进入一级总览，并切换展开 / 收起；
- 点击子菜单：自动保持对应一级菜单展开；
- 当前 URL / Hash 指向某个子项时，对应一级菜单自动展开；
- 搜索状态下自动展开匹配分支，避免匹配结果被折叠隐藏；
- 没有子菜单的一级项不显示展开箭头，也不增加无意义折叠行为。

## 背景系统导航结构

背景系统采用 **父级总览 + 子菜单直达工作区**，不在右侧重复生成一套 Variant 子页面。

```text
背景系统
├── BG_ENV_HERO
├── BG_CONTENT
├── BG_DATA
├── BG_CAPTURE
└── BG_SOLID_FALLBACK
```

交互规则：

- 点击「背景系统」：只显示系统总览、两张 Canonical Master、全局 Authority 与使用范围；
- 点击任一 BG 子菜单：右侧直接进入该背景类型工作区；
- 子菜单工作区只显示该类型的预览、参数合同、使用页面/场景和 Authority；
- 不再显示重复的「5 种背景类型」卡片索引；
- 子菜单页不混入父级总览和全局 Authority/Usage。

## 当前公共系统状态

背景系统 V1 已 **冻结**：

- Morning_Lake_Sunrise_Hero_V1：仅空首页；
- Morning_Lake_Master_V1：其余湖景页面统一母版；
- BG_ENV_HERO / BG_CONTENT / BG_DATA：只通过雾化、饱和度、对比度、亮度区分；
- BG_CAPTURE：用户当前照片；
- BG_SOLID_FALLBACK：固定浅色 / 深色兜底。

跨页面有内容验证板已归档到：
`design/system/backgrounds/morning_lake_v1/validation/Morning_Lake_Content_Adaptation_Approved_V1.png`。

主拍摄按钮已经拥有独立视觉、动效和震动合同，可作为公共组件管理。

## 本地打开

```bash
python3 scripts/design_manager.py serve
```

浏览器打开：

`http://127.0.0.1:8765/design/manager/`

## 校验

```bash
python3 scripts/design_manager.py validate
```

同时检查：

- 公共设计系统 Registry；
- 页面 Registry；
- Authority 路径；
- 公共系统 ID；
- 公共变体 ID；
- 页面 → 公共系统引用关系；
- 每个页面只有一个当前版本。

## 汇总

```bash
python3 scripts/design_manager.py summary
```

输出两段：

1. 公共设计系统；
2. 页面模块。

## 范围

V1 仍然只管理设计：

- 行为
- 视觉
- 动效
- 震动
- 声音
- 资产
- 公共系统
- 页面版本
- 权威与冻结状态

Runtime、Evidence 和 Work 交接暂不进入当前 Design Manager。


## Recognition Capture · 首次拍照教学

新增 Recognition Capture 的三页首次进入教学子菜单：

```text
识别过程
└── 07 · 首次拍照教学
    ├── P01 · 竖屏拍摄示意
    ├── P02 · 竖拍鱼类拍摄示意
    ├── P03 · 横屏拍摄示意
    └── 资源
```

注册源：`design/registry/experience_registry_v1.json` → `recognition_flow_v1_2.hifi_views.capture_first_use_guide`。

- Parent：`FROZEN` + `authority_gallery`，展示三张 Frozen Visual Authority。
- P01/P02/P03：`FROZEN` + `repo_image`，点击后直接预览对应 PNG，不生成模拟 UI。
- P01/P02 →「下一张」；P03 →「开始拍摄」。
- Hero 为唯一正确示范；下方三个均为错误示范。
- P02 的三个错误示例保持竖直鱼体；P03 的三个错误示例保持横向鱼体。
- `跳过/开始拍摄` 均不自动触发快门。
- P01/P02/P03 PNG 按原字节导入；Design Manager 导航、Manifest 与 SHA 校验分别验收。
- 「资源」子菜单直接预览和下载这三张已上传的 Frozen 原始 PNG，并显示仓库路径、尺寸、字节数和 SHA-256；来源为 `design/pages/recognition/first_use_camera_guide/manifest.json`，不复制文件，也不把设计稿映射为 Android 运行时资源。

## Normal Home · NH07 头像状态

新增 `#page/home_normal_v1/hifi/NH07` 二级菜单，由 `design/registry/experience_registry_v1.json` 的 `home_normal_v1.hifi_views` 注册。五态独立预览：真实头像、默认头像、加载、失败、游客。读取 `07_avatar_states/README.md` 与 PNG SHA Manifest；Guest 资源和登录 fallback 分离。本工作区属于设计稿与规范展示，不包含 Android 代码改动。NH01 Frozen PNG 不重写。新菜单进入部署后才会出现在公开的 GitHub Pages 网站。

## 鱼获图片自适应显示设计 V1.1（公共设计系统）

当前冻结权威：`design/system/fish_media_display_v1/Fish_Media_Display_Spec_V1_1.md`。V1 原文作为历史记录保留，V1.1 在响应式容器几何、BBox 裁切顺序和背景回退方面取代冲突规则。

- 公共入口：`#shared/fish_media_display_v1`，默认收起。
- 组件宽度随可用屏幕内容宽度响应；同一组件比例保持稳定，不因照片横拍/纵拍切换卡片比例。
- 固定处理顺序：BBox 锚定扩展 → 单侧边界补偿 → 留白 10%/12%、8%/8%、4%/4%、0%/0% 逐级缩小 → 仍无安全窗口时同源模糊背景填充。
- BBox 缺失或不可信时不进行激进中心裁切；使用完整原图前景 + 同源背景填充。
- 禁止非等比拉伸、生成式扩图、inpainting 或伪造鱼体；原始媒体字节保持不变。
- 规则适用 HOME 最近鱼获/轮播、识别结果、详情/编辑 Hero 和我的鱼获列表缩略图；各组件保留其现有参考比例和页面状态几何。
- 真实照片演示见 `design/system/fish_media_display_v1/real_photo_cases.json`。演示只是 CSS 容器映射，不是真机截图，也不是没有真实 BBox 时的安全裁切证明。
- 设计冻结、代码实现、APK 构建、模拟器测试和真机验收分别记录，不能互相代替。

GitHub Pages 仅复制 `design/`。合并到 main 并完成 Pages 部署后，上述规则才会出现在公开 Design Manager 页面。

## Structured Page Workspace Adapter

跨页面工作区入口使用现有 Design Manager 路由：

```text
#page/{page_id}/hifi/structured_workspace
```

页面身份从 Experience Registry 解析；W1 Envelope/Profile 路径从 W1 validation matrix 解析；资源身份、SHA、尺寸、MIME 和发布权限从页面自己的 W2 `Page_Resource_Binding_V1.json` 解析。不会创建第二份页面注册表或共享页面视觉素材。

`window.PageWorkspaceAdapters` 提供统一接口：

| 方法 | 输入 | 输出 / 失败语义 |
|---|---|---|
| `getRegisteredPage(pageId)` | Registry `page_id` | Experience Registry 页面描述；缺失时 `PAGE_NOT_REGISTERED`。 |
| `loadAuthority(sourceSha)` | 完整页面构建 SHA | 本页 Envelope、Profile、W2 binding 和视觉资源 descriptor；SHA、身份、来源、权限或字节校验失败时拒绝加载。 |
| `loadComponentTree()` | 无 | 页面拥有的组件树。NH01 使用已存在的冻结组件树；当前静态视觉引用 Adapter 只登记一个真实图片 DOM 节点，并明确标出未映射的 Profile 组件。 |
| `renderPreview(viewport, fontScale, state)` | 调用方参数 | 重新渲染页面拥有的预览；不支持或未解析的状态返回明确错误/`NOT_RUN`。 |
| `measureElements()` | 无 | 从当前 DOM 读取节点 bounds；缺少映射时 `COMPONENT_NODE_NOT_MAPPED`。 |
| `selectLayer(nodeId)` / `getInspector(nodeId)` | 稳定节点 ID | 选择实际渲染节点并返回 Inspector；未注册、无 DOM 映射或选中不一致时失败。 |
| `getResponsiveDiagnostics()` | 无 | 当前 Profile、页面布局和 Native Runtime 状态；浏览器检查不提升页面或原生验收状态。 |
| `dispose()` | 无 | 终止当前页面请求、移除页面事件监听器并释放预览 URL。 |

NH01 继续通过原有 `multi_screen_static_hifi` 入口使用自己的冻结合同、组件树和资源；原有直接 Hash 保持有效。Empty Home 与 Login 从各自 W2 视觉权威创建只读参考图 Adapter。参考图的子组件 DOM、Login 输入/键盘行为及未机器化状态不会由像素反推；这些检查保持 `NOT_RUN` / `NOT_VERIFIED`，不代表页面正式验收。

## NH07 高清母图

「有数据首页」→「NH07 · 头像状态」现提供已 SHA-256 校验的 **1254×1254 原始 RGBA PNG** 预览：点击可在 Design Manager 中放大，Esc / 关闭按钮返回；原图链接保留原始分辨率。该资产是 Design Source，不改 Android 运行时资源。请以 `design/pages/home/normal_home/07_avatar_states/assets/avatar_asset_manifest_v1.json` 的 SHA 为准。

## 图标系统 V0.1 · 全局小图标盘点
- 公共一级菜单：`#shared/icon_library_v0_1`，默认收起。
- 7 个入口：00 全局清单、01 鱼获字段、02 照片与视频、03 导航与层级、04 页面工具、05 账户与输入、06 状态与反馈。
- 43 个候选小图标语义，详见 `design/system/icon_library_v0_1/icon_inventory_v0_1.json`。
- V0.1 仅为资产盘点 / PARTIAL，不代表新 icon SVG、Android XML、冻结高保真素材已经完成。现有 Icon Action V1 继续冻结。

## 图标 V1 首批资产
已上传 6 个经过确认的 SVG：长度、重量、地点、日期、时间、天气；原样冻结并登记 SHA-256。访问 `#shared/icon_library_v0_1` →「01 · 鱼获字段」或「07 · 已冻结 SVG」，点击 SVG 放大。其余 37 个尚在盘点设计阶段；Android 未修改。

## 图标库 V1 · 43/43 全量冻结
公共设计系统「图标」六类全部具备 SVG V1 冻结母版；「07 · 全部已冻结 SVG」支持 43 张预览、点击 SVG 打开原图放大，并记录每份资源 SHA-256。仅设计素材冻结；Android 尚未接入。

## 拍照识别主链路 · 高保真发布视图

`recognition_flow_v1_2` 保留既有 `07 · 首次拍照教学` 菜单与 P01/P02/P03 Hash。新增 `00 · 全流程总览` 和 `08 · 边界状态与验收`：总览 PNG 标明为业务流程图，不冒充单页 Frozen Hi-Fi；边界视图按真实 Master Board JSON 展示状态映射，并将未导入的 PNG/SVG、缺失的 Capture 图和权限图标记为 `PARTIAL`、`BLOCKED_INTEGRITY` 或 `MISSING`。

Frozen 图片按原始比例完整展示。点击可打开支持放大、缩小、适配窗口和查看原文件的弹层；页面与状态合同显示 Authority Path / SHA、进入条件、触发、操作、下一状态、返回 / 取消 / 重拍和独立 Runtime 状态。未提供的原图不生成模拟 UI 或可点击假路径；浏览器前进 / 后退、刷新和无效 Hash 均经过回归验证。

展示与路由合同见 [`RECOGNITION_HIFI_PRESENTATION_CONTRACT_V1.md`](RECOGNITION_HIFI_PRESENTATION_CONTRACT_V1.md)。本管理站的响应式验证只证明 Design Manager；Page Contract 的 320dp / 360dp、Insets、字号、TalkBack 和 Runtime 项仍由各自产品合同与验收状态控制。

## Recognition · 识别全流程-和模型

在「识别过程」下新增 `09 · 识别全流程-和模型`，展示完整业务流程图、十个处理阶段、DET_FISH / MODEL_M1 / 质量尺寸规则 / 结果融合的职责边界、关键决策分支与端到端验收场景。

- 详细规范：`design/system_v3/00_Product_Master_Flow/subflows/Recognition_Full_Flow_Models_V1.md`
- 机器可读定义：`design/system_v3/00_Product_Master_Flow/subflows/Recognition_Full_Flow_Models_V1.json`
- SVG 流程图：`design/system_v3/00_Product_Master_Flow/subflows/Recognition_Full_Flow_Models_V1.svg`

此入口属于 DESIGN_ONLY 业务/技术说明，不增加 Android 产品状态、不修改冻结动效，也不声明 DET_FISH_v0.3 已成为生产默认模型。设计站的流程图预览与运行时/模型验收分别记录。

## Page Workspace structured renderer interface

The shared `PageWorkspaceAdapters.mount(root, options)` keeps the current W1/W2 reference view when no `renderer` is supplied. A registered page adapter may supply a page-owned `componentTree`, `renderer`, viewport/font/state profiles, and the default authority resolver; the shared shell then provides the same component tree, central DOM canvas, selection, measurement, and Inspector. The default resolver continues to read the Experience Registry, W1 Envelope/Profile, and W2 binding. It does not create another page or asset registry.

Each structured tree node carries `nodeId`, `pageId`, `parentId`, `componentType`, `renderedDomId`, `authorityRef`, `resourceRef`, `layoutRuleRef`, `state`, `visible`, `selectable`, and `measurable`. A visible measurable node must map to exactly one actual DOM element marked with `data-workspace-rendered-node`; `measureElements()` reads browser `getBoundingClientRect()` values in CSS px, while the input viewport unit remains separately reported. Missing or ambiguous mappings fail the mount. The Inspector reports expected bounds only when the page authority supplies them, plus actual DOM bounds and measured deviation.

The test-only non-NH01 fixture is `scripts/design/tests/fixtures/non_nh01_workspace_fixture.html`; it validates the shared adapter and is not a Home Empty or Login renderer. Gate A evidence is generated by `scripts/design/tests/verify_non_nh01_workspace_fixture.mjs`. Its synthetic authority and intentionally missing image remain confined to the fixture and do not enter the Design Manager registry or publication package.


### 首次拍照教学 · 结构化资源工作区

入口：`#page/recognition_flow_v1_2/hifi/capture_first_use_guide/resources`

资源页分为「页面高保真资源」和「前端独立素材」两组，并在直接打开时默认显示资源工作区；结构化高保真与响应式工作区可通过页签切换，选择会保存在 `guide_workspace` 查询参数中。前端独立素材通过 `design/pages/recognition/first_use_camera_guide/frontend_assets_manifest.json` 登记每个页面的正确主图与三个错误示范，并链接 `structured_pages.json` 的组件结构合同。独立照片未核验时显示明确候选/阻塞状态；整页 PNG 不可代替独立照片，也不映射为 Android 运行时背景。
