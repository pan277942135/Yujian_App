# 渔见 YuJian｜Lake Glass Background System V2

> Version: **BACKGROUND_SYSTEM_V2** · Visual reference: **NH01 Lake Glass V1.1** · Decision: 2026-11-02

**Governance status:** `VISUAL_DIRECTION_FROZEN / SOURCE_MASTER_PENDING / ANDROID_NOT_STARTED`.

## 1. 版本治理：新增 V2，V1 归入历史

- 本文件为新的 V2 规范，不重写、不删除、不重新标记原有 `design/system/backgrounds/morning_lake_v1/` 任一 V1 源文件或历史 SHA。
- **V2 是本轮适用页面的未来背景权威**；Morning Lake V1 作为上述页面的历史版本与回退参考。生效前还需独立纯背景 Master、SHA 和新版资源映射。
- **Empty Home 例外：** `home_empty_v2` 保留既有独立 Sunrise/Empty Home 背景、鱼漂、鱼线、涟漪、触感和动效。本次**不得修改**其任何代码或资源；V1 在该例外中继续提供历史源依据。
- `BG_CAPTURE`、Camera Preview、05 Photo Morph、13 阶段 Processing、AI Orb × WaterLight 是独立视觉/时序系统，非本轮替换对象。
- 视觉稿版本“Lake Glass V1.1”和背景系统规范版本“V2”是**不同的版本号**，不得混用。

## 2. 核心设计思想

**水滴 × 水草 × 阳光**构成自然、清澈、克制、安静有生命感的湖水环境；鱼获真实照片、用户信息、数据、内容、交互作为独立前景，不烘焙在背景里。自然阳光不等于 Orb 能量；不引入 HUD、雷达、夸张霓虹。

跨页面**同一张独立 Master**，不重新生成、重画、重新布局水草/水滴/光源，不通过页面成品截图去除 UI 反向提取背景。背景在同视口/同裁切参数下须具有相同像素空间。

## 3. 目标视觉与约束

| 元素 | 规范 |
|---|---|
| 水体色调 | 雾白浅湖青 → 湖水青 → 通透青绿湖水；深湖蓝文字；不可强 HDR/高饱和蓝 |
| 阳光 | 柔和晨金从右上方入射，非强聚光；不穿透人像/鱼体/重要数字 |
| 水草 | 左右底部真实叶片、疏密不对称；中央操作区保留空间 |
| 水滴 | 主要 2–3 颗，靠边少量点缀，折射清透；内容保护区不穿过 |
| 焦散 | 主要在下部水体，阅读区弱纹理，避免高对比水波 |
| 玻璃 | 页面内容如原有玻璃卡可保留；**不得给首页统计区额外增加整块玻璃面板** |
| 焦点 | 首先是鱼获真实照片，其次数据及操作；背景不可抢主角 |

示意 Token：MistWhite `#EAF7F7`，LakeMist `#C6EAE5`，LakeWater `#8CCFC4`，Depth `#65BFB5`，Ink `#153B50`，MorningGold `#D8B36A`。这些仅为方向色彩，**最终像素由独立冻结背景源图决定**，不是代码重新绘景的授权。

## 4. 适用页面 / 不适用页面

| 旧映射 | 新映射 | 页面 |
|---|---|---|
| BG_ENV_HERO | BG_LG_HERO | 有数据首页 `home_normal_v1` |
| BG_CONTENT | BG_LG_CONTENT | 识别结果、鱼获详情 A/B |
| BG_DATA | BG_LG_DATA | 我的鱼获历史、鱼类指南 |
| BG_CONTENT | BG_LG_CONTENT（需单页视觉校验） | 账户、登录、注册、个人资料等使用旧 Morning Lake 的页面 |
| 原 Empty Home 独立背景 | **KEEP_EXISTING** | 无数据首页 |
| BG_CAPTURE/Processing | **KEEP_EXISTING** | 相机、真实取景、识别动画与 05 Photo Morph |

其他页面须凭 `usage_map_v2.json` 显式加入，不允许全局查找替换而误改无数据首页。登录页当前背景仅用于顶部环境区域，迁移时不得强制改为全屏背景。

## 5. 前景冻结保护

- NH01：Logo/头像/三项统计直接悬浮，三等分细线保持，**不加统计玻璃面板**；Hero 鱼获照片不加独立玻璃底座，保留现有细边框、轻阴影与底部渐变信息；晶环按钮位置、材质、光丝、点击/触感全部不变。
- Detail：真实照片与 A/B 鱼体、导航、说明、媒体网格、翻面、编辑交互全部保留。
- My Catches：时间轴、鱼获缩略图、成长标记、过滤、搜索、滚动、拍照入口全部保留。
- 识别结果：不修改照片几何/裁切、识别管线、RR09、标题凝字和 WaterLight。
- 其他页：逐页尊重既有页面冻结规范；可读性修饰仅可落于**局部前景容器**，不能整体漂白、调色或改动 V2 Master。

## 6. 独立资源与工程实现门槛

**目前已有页面融合视觉与规范板，不等于已有能打包的纯净背景母版。** 必须先搜寻既有独立背景源；若找不到，则依据 V1.1 方向单独补齐并再次确认。不能从整页生成图裁切、修复文字区域拼出资源。

正式 Master 资源登记 `Lake_Glass_Master_V2` 必须包含：实际像素尺寸、文件格式、色域 sRGB、路径、SHA-256、来源/使用权说明、独立无 UI 预览、多设备 Crop Safe 区及 Android 派生版本校验。**缺失时 SHA、尺寸记 `PENDING`，禁止编造。**

Android 技术建议：单张高清 PNG 作为不可变源图；Runtime 可使用质量等同的 PNG 或无损 WebP，保留源/派生 SHA 和像素差异记录；静态底图一次加载并缓存，无逐帧解码/大量 shader。所有目标页采用统一的 `ContentScale.Crop` 与统一 Alignment（当前候选 `TopCenter`，需经多尺寸视觉核验方可冻结），仅前景随页面滚动，不让背景滑动、平铺、拉伸、重新生成或视差变位。

### 当前需处理的真实 Android 接线

- `GeminiNormalHomeContent.kt` 当前通过 `normal_home_runtime_v1/static/scene_base.png` 绘制 NH01。
- `NormalHomeRuntimeAssets.kt` 加载同一路径；已有静态验证、manifest、SHA 和 V1 指定资源测试。
- `YuJianMorningLakeBackground.kt` 当前的 CONTENT/DATA 使用 `account_privacy_morning_lake` 加旧版白雾/ColorMatrix；`MyScreen.kt`、`FishGuideBackground.kt`、`RecognitionResultScreen.kt`、`FishRecordDetailScreen.kt`复用现有通道。
- `app/build.gradle.kts` 当前会从 V1 Morning Lake Master 生成 `account_privacy_morning_lake`；切换时必须处理旧自动复制链，防止把旧山水重新打包。
- `AuthV2Components.kt` 直接在顶部引用旧 drawable，保持原布局，仅替换对应环境素材。
- **勿伪造 V1 测试通过：** 原 V1 固定 SHA/布局验证保留为历史回归，新增 V2 manifest/asset/source/runtime 测试与页面对比证据。

## 7. 验收标准

1. Master 真实存在、独立无 UI、路径/SHA/尺寸/权属登记一致；各页面引用同一 Master。
2. 在完全相同设备视口下各目标页面背景坐标一致；无位图错位、黑边、拉伸、滚动位移、重复铺图。
3. 文字可读，常规文本对比度建议 ≥4.5:1；水滴/水草/光束避让内容保护区。
4. NH01 统计仍**无玻璃面板**；照片/鱼获卡/按钮保持原结构和几何。
5. 冷启动、页面返回、结果揭晓无黑闪白闪；背景不因首次异步解码出现明显跳变。
6. Android 多尺寸（320×480、360×640、390×844、430×932 dp）及系统 fontScale 1.0/1.3/1.5 完成原生截图核验。
7. Empty Home 资源 SHA、屏幕表现、鱼漂/鱼线/涟漪/触感全部无变化。
8. V1 全部历史文件字节不变；V2 独立登记，不在旧文件中强行改历史 SHA 或修图。

## 8. 当前仍需补齐，才能交 Work 执行

`P0` 独立湖水玻璃背景 Master 与 SHA；`P0` 背景真实尺寸裁切安全区；`P0` 以同一母版与既有前景做非生成式四页融合对照；`P1` 登录/鱼鉴等补充页面的 V2 视觉验证；`P1` 对照运行时旧 V1 来源和断言清单。

**本 PR 仅登记规范，不替换当前 Android 背景、不修改旧 V1、不进入生产。这些缺口补齐后，再发布 Work 可执行指令和完整 Runtime 资源迁移任务。**
