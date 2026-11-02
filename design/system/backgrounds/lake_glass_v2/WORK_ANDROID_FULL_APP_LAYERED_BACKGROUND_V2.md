# Work P0 — 渔见 Lake Glass V2 全 APP 统一分层背景渲染与 Android 接入

**Task ID:** `YUJIAN_LAKE_GLASS_V2_LAYERED_FULL_APP_BACKGROUND_RUNTIME_P0`  
**Repository:** `pan277942135/Yujian_App`  
**Design source:** `design/system/backgrounds/lake_glass_v2/Lake_Glass_Background_System_V2.md`  
**Asset identity:** Candidate02 1440×3120 PNG, SHA256 `d0bd07ba379491e8b93468c616468fc60d59a2dfebc11061cf4d14e9788c6e12` (chat file; reverify bytes after Work imports).  
**Baseline:** Read live `main` at task start, not the prior reference SHA; inspect Draft PR #302 for current V2 decisions.  
**Priority:** P0. **Mode:** autonomous inventory → asset decomposition → shared renderer → full-route integration → device QA → APK, PR → merge only with verified evidence.

## 一、唯一目标

本次不是单图替换、不是三页面试点，而是：**全 APP 所有主题背景统一升级为 Lake Glass V2，唯一不替换 Empty Home 无数据首页。**

在统一的场景坐标里保持右上自然晨光、透明湖水、水草和水滴的相对位置与形态，前景保留真实功能和高保真几何。不得把真实 CameraX Preview、照片、视频、鱼体 Mask 或 AI Orb/WaterLight 当作背景替换目标。独立渲染新背景的场景材质，不更改识别阶段和场景时钟。

## 二、当前代码审计必须覆盖

最低限度：
```text
app/src/main/java/com/yujian/ai/YujianApp.kt
app/src/main/java/com/yujian/ai/ui/home/GeminiNormalHomeContent.kt
app/src/main/java/com/yujian/ai/ui/home/NormalHomeRuntimeAssets.kt
app/src/main/java/com/yujian/ai/ui/home/EmptyHomeSceneRenderer.kt
app/src/main/java/com/yujian/ai/ui/designsystem/background/YuJianMorningLakeBackground.kt
app/src/main/java/com/yujian/ai/ui/screens/RecognitionResultScreen.kt
app/src/main/java/com/yujian/ai/ui/screens/RecognizingScreen.kt
app/src/main/java/com/yujian/ai/ui/screens/MyScreen.kt
app/src/main/java/com/yujian/ai/ui/recorddetail/FishRecordDetailScreen.kt
app/src/main/java/com/yujian/ai/ui/fishguide/FishGuideBackground.kt
app/src/main/java/com/yujian/ai/ui/auth/AuthV2Components.kt
app/src/main/java/com/yujian/ai/ui/screens/AccountPrivacyScreens.kt
app/build.gradle.kts
app/src/androidTest/java/com/yujian/ai/NormalHomeRuntimeContractTest.kt
scripts/verify_normal_home_runtime_v1.py
```

搜所有旧山水引用、静态背景和隐含子页面（特别是 `BG_CONTENT`, `BG_DATA`, 相关失败页与滤镜遮罩）。依据**运行时实际组件分支与路由状态**列出每个 Screen/Route/State 的背景归属。对 `home` 路由不能用 route 字符串推断是否 Empty Home，必须由**真实 Empty Home 分支状态**隔离，不触碰其 `scene_base.webp` 等既有实现。

## 三、实现分层素材包（不得因格式要求降低自然写实）

### 1. 资源与生成顺序

1. 将本任务附带的 **Candidate02 主 PNG 和 Manifest** 经独立 SHA 校验纳入 V2 资源目录，禁止从聊天界面预览压缩图重新截取。
2. 核查原来生成的独立母版与最初 V1.1 合成参考；基于真实母版**建立 Layer Pack**，每个层独立记录矩形边界、图层顺序、坐标和色彩空间。
3. `LakeBase`：明暗渐变、柔和湖水体积色，用 Canvas/必要的低频纹理实现。若从原始完整 PNG 拆除其他元素时破坏保真，则采用**静态整体 fallback/动态区域 mask**，不得出现补洞涂抹、重影或不可信“原始 SVG”。
4. `Sunlight`：遮罩 PNG + 固定入射方向 + 可选 shader。暖金自然光，绝不冒充 AI 能量。
5. `WaterCaustics`：Alpha/Luma Tile 或 Shader；下部自然不规则焦散，内容区弱化。
6. `AquaticGrass`：左右自然水草透明 PNG/WebP；根部锚定，叶尖可用 SVG/Path/骨架微摆动。不能粗糙描边替代真实植物。
7. `WaterDroplets`：少量玻璃水滴 RGBA 细节，SVG/Path 只可负责遮罩、受光路径；不可叠出两份水滴。
8. 导出 `lake_anchors_v2.json` / `lake_layer_pack_v2.json` / `asset_manifest.json` 和无 UI 静态合成对比图，登记每个原文件/导出文件 SHA256。

### 2. 资源路径

```text
design/system/backgrounds/lake_glass_v2/assets/{master,base,sunlight,caustics,grass,droplets,masks,manifest}
app/src/main/assets/lake_glass_v2/{same runtime derivatives and manifests}
app/src/main/java/com/yujian/ai/ui/designsystem/lakeglass/
```

若素材已存在仓库，优先复用，保留源身份。视觉母版不是最终唯一 runtime 层，但必须可一键切回作为统一静态 fallback。

## 四、共享 Android 引擎与边界

创建或复用：
```text
LakeGlassEnvironmentHost
LakeGlassScene
LakeGlassAssetCache
LakeGlassFrameParams
LakeGlassLayoutTransform
LakeGlassRenderPolicy
LakeGlassMotionPolicy
LakeGlassStaticFallback
```

- Root-level Host 尽可能在 `NavHost` 背后持续绘制；单一 Scene、单一全局设计坐标、统一 Crop Transform；所有页的湖水、水草、水滴位置不能自行漂移。
- 必须避免与各 Screen 旧背景重复叠加造成旧图残留；将所有 Theme Background 的原组件迁移为统一 Host/Wrapper。保留旧 V1 源文件和历史测试快照。
- Compose/Canvas 优先；仅 API33+ 使用 AGSL `RuntimeShader`，其它 Android 使用可控位图/Canvas 降级；不得添加专用 `GLSurfaceView`、无额外独立 EGL 生命周期。
- 部分页面原本是顶部环境片段、底部局部背景或不透明真实媒体。只在对应**环境容器**渲染同一主题场景，避免遮盖原有内容；不把半屏相机预览强制铺满图。
- 稳定无黑闪白闪：初始先显示同步可用的低成本静态缓存或预加载资源；在新图层完全 Ready 后提交可见帧。
- Android 解码缓存必须有释放策略，不为每个页面保留一份 1440×3120 全尺寸 RGBA Bitmap。原尺寸约 17.1MiB。
- 无 UI 的基本 fallback 为 V2 母版，不可回退到历史山水；唯一业务场景例外 Empty Home 仍使用其独立冻结背景。

## 五、动效技术合同

**先静态视觉齐平，再允许极轻微动效。** 所有动画由共享 `LakeGlassMotionPolicy` 管控：

| 内容 | 初始设计参考 | 强制限制 |
|---|---|---|
| 水体光照 | 24–36s 慢变化 | 不让文字亮度波动显著 |
| 水草 | 16–24s 低频叶尖位移 | 根固定，叶尖最大约 2dp，避免整组平移 |
| 焦散 | 小范围低频变化 | 不产生夸张全屏水波 |
| 水滴 | 默认静态 | 不上下漂移、不粒子雨 |
| AI Orb/WaterLight | 完全独立 | 识别统一 SceneClock 不更改 |

在 App 进入后台、禁用动效、录制、真实相机识别繁忙、低端设备等情况下自动稳定成静态 V2 帧。禁止为每片草启动单独时钟。

## 六、全 App 一次性覆盖清单

必须遍历实际 Main 当前所有路由/Screen（不要相信下列举例就是全量）：

- **有数据首页 NH01**：统计无玻璃面板；鱼获卡真实照片/比例/角部/文字、晶环按钮和触感保持。
- **历史/时间流**：My Catches、按日详情、搜索、筛选、空搜索/错误、鱼获详情 A/B、媒体附件/编辑页。
- **识别与知识**：识别流程非照片区域、05 Photo Morph 外围、RR09 结果外围、High/Medium/Low 结果、识别问题、鱼种选择、Fish Guide、鱼种详情。
- **账户/通用**：登录注册、编辑资料、修改密码、数据与隐私、关于、法律协议及其他已有页面。
- **Camera/First Capture**：统一页面环境视觉层；实时预览及取景几何、首次引导内容和导流逻辑不修改。
- **唯一 exclude**：Empty Home **整体**（背景、鱼漂、鱼线、波纹、触感、布局都不得改）。

对于每个 RenderSurface 交付：
`routeId, runtimeState, currentBackgroundOwner, targetLakeGlassHost, contentOpaqueOverride, emptyHomeExcluded, screenshotId, testStatus`。

不允许因复用一个 BG_CONTENT Wrapper 而断言“全页面已覆盖”；必须沿实际导航路径执行完整 route matrix。

## 七、响应式、可读性与性能

- 以 1440×3120 设计坐标；统一 Viewport Transform + Crop + Alignment，源/层统一，不容许各 Layer 单独 `fillBounds` 产生错位。
- 设备矩阵：320×480、360×640、390×844、430×932 dp，fontScale 1.0 / 1.3 / 1.5。
- 审视中短屏底部水草是否被裁切，右上光束是否压文字；只允许调整**全局背景裁切策略与既有页面局部内容对比保护**，不得每页移动场景自然元素。
- 列表滚动背景固定，不让草和水波随时间轴滚动。
- 常规小字目标对比度 ≥ 4.5:1；大字 ≥ 3:1。
- 帧率目标 60Hz 体验；实测性能数据，不虚构“零掉帧”。重点监控 ANR、内存峰值、过度绘制、场景进入首帧和 UI/识别并行。
- Empty Home 前后同一设备对比：背景资源 SHA、鱼漂位置/动作/涟漪/触感无变化。

## 八、已有 V1 资产和自动构建链冲突

`GeminiNormalHomeContent.kt` 的旧 `scene_base.png`、`NormalHomeRuntimeAssets.kt`，`YuJianMorningLakeBackground.kt` 的白雾矩阵，`app/build.gradle.kts` 中旧山水 `account_privacy_morning_lake` 复制，及 V1 固定 SHA 的脚本均可能将新资源覆盖回旧景。处理策略：

1. **不要直接覆盖旧历史图片文件**；V2 资源建立独立路径/资产 ID。
2. 改目标 Runtime 加载指向 V2 Host，而不是把旧文件字节偷偷替换；旧路径留历史追溯。
3. V1 原测试针对旧版本快照保留；新增 V2 验证与按目标版本路由的测试。不能伪造新文件是旧 SHA。
4. 严格审计及清理运行时旧背景的误引用，不要删除历史资源或把旧 V1 规则抹掉。

## 九、验收与交付

必交：
1. 一套 V2 Master + 分层源码/派生资产、资源 manifest、source SHA，静态 composited reference、像素差异/目视差异报告。
2. 最新 Main 全路由背景覆盖矩阵（逐页状态截图），`Empty Home` 为唯一明确原背景例外。
3. 四种典型页（NH01、结果、鱼获详情、My Catches）以及账户/相机环境/鱼鉴的本机截图；证明不改变页面 UI。
4. 320/360/390/430 屏幕 Crop 对比，fontScale 回归。
5. CameraX、05 Photo Morph 700ms、Button Morph 900ms、13 阶段时钟、RR09、鱼漂触感相关回归记录。
6. `compileDebugKotlin`、unit test、相关 AndroidTest/静态资源测试执行结果、失败摘要。
7. Debug APK 可安装下载链接、Git HEAD、APK SHA256、CI Build ID，失败时报告真实未执行项目。
8. PR 提交与合入决策，禁止用设计图或代码合并替代真机验收。

允许 Work 自主解决代码接入和 PR 冲突，但应保持原有静态母版不可变、V1 历史可回滚；若视觉 Layer Pack 不能达到候选母版最低保真要求，则允许先全页部署共享 **STATIC_FALLBACK** APK 供比较，同时继续完成分层渲染，不得把 fallback 冒充正式分层交付。

**完成定义：全 APP 背景接入、唯一 Empty Home 排除、真实设备运行、资料/代码一致、APK 可验证。只完成三个页面 = 未完成。**
