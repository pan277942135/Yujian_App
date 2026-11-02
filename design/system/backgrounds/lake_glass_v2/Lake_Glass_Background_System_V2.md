# 渔见 YuJian｜Lake Glass Background System V2

> Version: **BACKGROUND_SYSTEM_V2** (supersedes Morning Lake V1 as the active *theme-background* contract)  
> Visual: **NH01 Lake Glass V1.1** · Master candidate: **Candidate 02** (2026-11-02)  
> Status: **VISUAL_LANGUAGE_FROZEN / LAYER_EXPORT_PENDING / ANDROID_NOT_IMPLEMENTED**

## 0. 最新范围确认（2026-11-02 用户决策）

1. **全 APP 所有使用主题环境背景的页面，统一迁移至 V2；唯一明确不替换的是无数据首页 Empty Home**。
2. V2 **不是**“把单张 PNG 替换旧山水图”，而是共享、统一、可检验的**分层渲染系统**。
3. 原始用户照片、相机 CameraX 取景流、鱼获媒体、视频和鱼体 Mask 是**前景内容**而非待替换的主题背景；它们仍使用原像素和现有几何。相机/识别过程的**外围主题环境**可以使用 V2，但不得破坏 Preview、05 Photo Morph、统一时钟、13 阶段 AI Orb × WaterLight。
4. **V1 作为历史版本**。保持 `design/system/backgrounds/morning_lake_v1/` 下历史规范与原始资产字节不变；不删除历史 SHA。
5. 静态 Master 候选作为**视觉权威基准与降级图**，并非长期唯一渲染层。禁止将四个页面效果图作为背景素材或从页面截图抠背景。

## 1. 设计核心与不变量

背景主题固定为清透天然湖水玻璃：**水滴、水草、阳光**三类不可缺失元素。固定自然光从**右上**入射；水草在**左右底部**；水滴少量分布屏幕外围；中部大面积低对比阅读保护区；焦散主要位于下部水体。整体克制，不增加光柱、HUD、雷达或满屏粒子。

**V2 同一母版 / 同一场景坐标 / 同一材质 Token / 同一渲染组件。** 不允许页面独立生成背景、移动水草、重构阳光、改变水滴关系或重新配色。页面差异仅允许在明确登记的交互与文字可读性保护层，不修改背景源像素或场景构图。

色彩 Token（校色参考，不替代 Mother PNG）：`#EAF7F7`、`#C6EAE5`、`#8CCFC4`、`#65BFB5`、`#153B50`（文字）、`#D8B36A`（晨光）。

### 优先级
用户真实鱼获照片 > 内容/关键交互 > 低纹理自然环境 > 微小装饰动画。首页三项统计**没有玻璃面板**；Hero 照片无额外玻璃底座，沿用边框/阴影/底部渐变文字；晶环按钮/水光系统不重做。

## 2. 渲染架构：PNG 母版 + 分层素材 + Compose/Shader

**不是三张页面图片。** 统一结构：

```text
YuJianApp
└── LakeGlassEnvironmentHost
    ├── routeBackgroundPolicy: EMPTY_HOME_LEGACY / LAKE_GLASS / CAPTURE_OPAQUE_FOREGROUND
    ├── LakeGlassScene (全局固定设计坐标、唯一渲染实例)
    │   ├── 01 LakeBaseLayer         Compose Canvas：雾白→湖青渐变 + 低频水体色层
    │   ├── 02 SunlightLayer         光源自右上；静态光照纹理 + 可选 AGSL 微动
    │   ├── 03 WaterCausticsLayer    无缝焦散贴图／遮罩 + Canvas/Shader
    │   ├── 04 AquaticGrassLayer    左/右多片 RGBA 自然植物素材；剪裁 + 极低幅摆动
    │   └── 05 WaterDropletsLayer   2–3 个 RGBA 水滴/折射高光，固定锚点
    ├── EnvironmentForegroundProtectionLayer（仅当现有页面确实需要对比度保护）
    └── NavHost ExistingScreenForeground（原 UI、Mask、照片、CameraX、Orb 等）
```

- **静态 Master：** Candidate 02 1440×3120 PNG，SHA-256 `d0bd07ba379491e8b93468c616468fc60d59a2dfebc11061cf4d14e9788c6e12`，位于本次对话产物；**仍为候选，待以二进制提交仓库并重新读取 SHA**。可作为不可变静态 fallback 和全场景对照 reference。
- **正式 Runtime：** 以独立图层合成，复用同一组素材/参数，不允许对 Master 上已经存在的水滴/水草/光束再叠一层同样的素材，造成双影。优先制作可独立合成的 Layer Pack；若某一层尚未能安全拆出，则那一层暂保持静态参考，不强行抠图、反向填充/涂改导致画面变化。
- 不能靠“从栅格 PNG 一键转 SVG”准确恢复真实水草细节。**SVG / VectorDrawable** 用于规则边缘、曲线路径、遮罩、受光/摆动骨架；真实水草叶脉、水滴折射、非规则焦散使用透明 **PNG / WebP** 或相应纹理；SVG 预览文件如有保留源 SVG 供设计审计，Android 可用 VectorDrawable / Canvas Path 等渲染。
- Kotlin Compose Canvas 是默认通路；AGSL RuntimeShader 仅在支持的 Android API 使用；低系统版本或低性能模式采用静态合成，不影响 UI；**不要额外创建 GLSurfaceView**，避免与识别管线 EGL/CameraX 合成冲突。
- 图片解码/缓存一次、后台加载，缩放按设备需要选取，不反复逐帧解码全尺寸 PNG。静态母版 ARGB_8888 原尺寸约 17.1MiB，若多页面每屏各留一个会推高内存。
- 背景宿主共享，页面导航时保持连续，无旧山水闪现、空白或黑块。对图片/相机 Preview 等完全不透明的内容层，**不穿透显示主题背景**。

## 3. 资源包（必须在 Work 中完成，不要求用户再补绘）

```text
design/system/backgrounds/lake_glass_v2/
  Lake_Glass_Background_System_V2.md
  usage_map_v2.json
  assets/
    Lake_Glass_Master_Candidate_02_1440x3120.png     # 独立母版；原样存档
    lake_base_v2.webp                                 # 不含重复前景的基础层
    lake_sunlight_mask_v2.png                         # 光场遮罩（可为低位深）
    lake_caustics_tile_v2.webp                        # 焦散纹理
    grass_left_group_v2.png                           # 左自然叶片 RGBA
    grass_right_group_v2.png                          # 右自然叶片 RGBA
    grass_single_v2.png                               # 非规则单叶备用
    droplet_01_v2.png / 02 / 03                        # 透明水滴 RGBA
    lake_anchors_v2.json                              # 锚点、bbox、排除区
    lake_layer_pack_v2.json                           # 色彩空间、叠放、alpha、混合模式
  reference/
    candidate02_preview.jpg
    composited_static_reference.png                    # 非 UI 合成图
  evidence/
    ...                                               # 多页截图与差异分析
```

> 只允许使用已经确认可合法使用的来源；如果无法从现有母版忠实导出透明水草，不要伪称为原生分层源。保留母版为视觉锚定，进行**受控局部重建**并逐层对照，以屏幕真实效果验收，不得改变整体构图。先以真实素材为主，避免为了 SVG 格式牺牲自然质感。

Manifest 必须含：`sourceFile, sourceSha256, format, width, height, origin/permission, layerId, pixelBounds, anchor, blendMode, alpha, targetProfiles, derivedFiles/sha256`。没有原始向量源时将 `svg_source: null`，而不是编造。

## 4. 全 APP 覆盖：不是只换三个页面

**唯一例外：** `home_empty_v2` = `KEEP_EXISTING`，代码、资源、鱼漂/鱼线/涟漪/触觉及其 SHA 都不动。包括首次启动跳转/空态等真实 Empty Home 路径，不可只依据 path string 猜是否空首页。

其他页面按现有 NavGraph / Composable 背景入口登记并**一次迁移所有 theme-background owner**：

- **首页/列表：** NH01 有数据首页、我的鱼获、日期/时间线、鱼种指南/鱼种详情。
- **鱼获内容：** 鱼获详情 A/B、记忆/媒体附属页面、识别结果各状态、识别问题/选鱼种等辅助页。
- **登录与账户：** 登录、注册、我的账户/资料编辑、账户与隐私、密码、关于、隐私政策、用户协议等。
- **拍摄与识别流程：** Camera/First Capture Guide/Processing/05 Photo Morph/RR09 等*页面的主题环境层*统一调用 V2；**真实取景/照片/分割及独立 AI Orb × WaterLight 都不是主题背景，不替换，且不改变时序**。原视觉强背景若与水光成分绑定，必须先给出逐层剥离的证据，不得用另一张全屏图盖住原水光。
- 其他在当前 Main 中存在的页面、Dialog、Sheet、Error/Empty/Loading、离线状态、全屏预览等按照**视觉宿主与内容语义**判定，不遗漏有主题环境的页面；不强迫系统弹窗、系统媒体选择器或原本全屏不透明媒体使用主题背景。

**强约束：任何页面禁止自己挑选/加载旧 Morning Lake 原图。** 从统一 Host 或共享 Wrapper 获取 V2；保留旧文件只为历史/旧版测试，不成为新 Runtime fallback（Empty Home 的独立背景例外）。

### 路由覆盖审计
Work 必须解析最新 `YujianApp.kt` 导航表，并搜寻：
`Morning_Lake_Master_V1`, `scene_base.png`, `account_privacy_morning_lake`,
`BgContentSurface`, `YuJianMorningLakeBackground`,
`background(WarmBackground)`, 旧 Background System Wrapper,
以及 Camera/Processing 各自环境底层。
交付 `route_background_coverage_v2.json`：route、state、old owner/path、new host、protection rule、runtime evidence、exceptions。唯一业务级 `excludedPage = home_empty_v2`，其它不透明内容例外必须写清楚**内容层原因**。

## 5. 响应式与背景几何

- 以 1440×3120 母版为固定归一化坐标，`fit=CROP`、统一确定锚点；保持各层锚点、光源、水滴、植物同一矩阵缩放与裁切，禁止单独移动水草或水滴。
- 比较 `Center` 与 `TopCenter` 在 320×480、360×640、390×844、430×932dp；确认裁切安全区后冻结统一 `cropAnchor`。必要时使用设计归一坐标保护边缘；绝不将图片拉伸至不同宽高比。
- 每页背景独立于内容 Scroll、列表 LazyColumn；固定在 viewport 下，滚动不平铺、不抖动、无视差。
- 文字保护首先使用现有卡片/标题局部材质；不得全面给 V2 Master 叠加旧 V1 的高不透明白幕/ColorMatrix。WCAG 常规文字建议 ≥4.5:1，较大文字建议 ≥3:1。

## 6. 微动策略（代码负责）

默认静态基线先达到可验收；动态属于同一 V2 引擎，不得另做一套 Demo：

- 水体散射：周期建议 24–36 s，局部亮度变化极小，不形成水流纹理大片移动。
- 水草：16–24 s 低频柔性摆动，叶尖偏移 ≤2dp；根部固定，叶片/骨架不整体左右平移。
- 光照/焦散：局部缓慢变化；不新增第二太阳，不新增光束。
- 水滴：位置固定，仅可进行极弱折射高光微变，不持续上下漂浮或复制粒子雨。
- Activity 停止或 Reduce Motion 时冻结为合格静态合成帧。
- 相机预览、连续识别、快速滚动及低性能模式自动静态降级，绝不能降低 CameraX/模型 FPS 或抢占统一识别 SceneClock。
- 所有动效参数集中在 `LakeGlassMotionPolicy`，由场景统一时间源驱动，不为每片水草另建循环协程。

## 7. 当前 Main 已核查的具体入口

- `app/src/main/java/com/yujian/ai/ui/home/GeminiNormalHomeContent.kt`：`normal_home_runtime_v1/static/scene_base.png` 当前旧山水，`NormalHomeRuntimeAssets.kt` 异步加载。
- `app/src/main/java/com/yujian/ai/ui/designsystem/background/YuJianMorningLakeBackground.kt`：CONTENT/DATA 使用 `account_privacy_morning_lake`、ColorMatrix + White Veil。
- `MyScreen.kt`、`FishGuideBackground.kt`、`RecognitionResultScreen.kt`、`FishRecordDetailScreen.kt` 使用共享 CONTENT/DATA。
- `AuthV2Components.kt`：直接加载旧 drawable 上部环境图。
- `app/build.gradle.kts`：从 V1 Master 生成旧 drawable。
- `RecognizingScreen.kt`：独立 `processing_mint_background_v1`，与 Processing 水光/照片叠放关系须保持；只迁移真实环境底层。
- `EmptyHomeSceneRenderer.kt`：独立背景 `empty_home_runtime_v2/static/scene_base.webp` **禁改**。
- `NormalHomeRuntimeContractTest.kt`、`verify_normal_home_runtime_v1.py` 等原 V1 固定 SHA/尺寸检测需版本化处理：旧测试留档，新增 V2 检测，不通过“伪造旧 SHA”消除报错。

## 8. 验收要求（静态/全局/性能）

- 一张 Candidate02 母版、各分层导出、同一组统一 Anchors/Token，代码静态合成与母版视觉对照，并提供差异热图/透明度及视觉说明。不可出现重复水滴、水草“双影”。
- 每条 NavGraph route 与各主状态均有“V2 覆盖 / Empty Home 原样 / 不透明媒体内容保护”的真实清单，主路径不可漏页。
- NH01 三项统计无玻璃面板；Hero 照片、侧卡、晶环及点击触觉保持冻结；鱼获详情/历史/账户前景布局不变。
- 识别 13 阶段、照片700ms归位、按钮900ms变形、结果揭晓过程无时序/颜色/首帧身份回归；CameraX Preview 不被背景覆盖。
- 无黑边、白闪、空白、层序错误、滚动抖动、明显 GPU 回退；在常规测试设备 60Hz UI 目标下无明显可归因于背景的新掉帧；提交真实帧耗时/内存监测，不编造性能数据。
- Native 检查：320×480、360×640、390×844、430×932dp；fontScale 1.0/1.3/1.5；前景关键锚点 delta = 0dp（冻结几何允许误差遵循页面合同）。
- Empty Home 完全原样：图片 SHA、坐标、鱼漂事件、涟漪与触感无差异。
- 旧 Morning Lake V1 保留历史文件和 SHA，不在新 APK 主线路由中复活旧山水。

## 9. 执行阶段（允许一次全范围 Work 开发）

1. **A0 全入口审计 + Master 字节登记**：读取实时 main、PR #302、Candidate02 PNG/Manifest，完成路由与素材资产矩阵。
2. **A1 分层素材工程**：尽可能从已确认母版保真拆分水草、水滴、自然阳光与焦散，制作 SVG/Path 骨架与 RGBA 细节，输出 Layer Pack/锚点/验证合成。难以保真者静态冻结并记录，不能为了拆而降质。
3. **A2 共享 LakeGlassEnvironmentHost**：Compose Canvas / Shader / Android 版本静态 fallback，页面环境策略、生命周期、缓存与统一变换。
4. **A3 全 APP 同批接入**：所有使用主题背景的页面一次完成，从 NH01 到 My Catches、Detail、Guide、Auth、Processing 环境及各辅助路由；**唯独 Empty Home** 不参与。
5. **A4 完整原生 QA 和 Debug APK**：多视口屏幕证据、全路由覆盖表、性能/内存、识别回归、旧资源残留审计、完整源 SHA 与 APK SHA。

允许在一个 Work 任务中分阶段提交和验收，但**不允许“只做三个页面便宣称完成全局替换”**。如某页面不适合显示主题背景，必须证明其内容是原生不透明取景/媒体/系统层，仍在路由覆盖清单中，不能漏查。

## 10. 资源状态与注意事项

**当前可用：** Candidate02 独立高清 PNG（1440×3120）及生成 Manifest，先前 V1/V2 文案和四页演示效果。  
**待 Work 完成：** Layer Pack 的透明 PNG/WebP、SVG/Path/遮罩（按可行性）、静态合成验证图、Crop-safe 锚点矩阵、真实 APK/真机证据。  
**状态不能伪造：** 仅确认“设计方向与母版候选可用于开发”，不能称“正式 layered master 已冻结”“安卓已完成”或“PR 已合入 Main”。
