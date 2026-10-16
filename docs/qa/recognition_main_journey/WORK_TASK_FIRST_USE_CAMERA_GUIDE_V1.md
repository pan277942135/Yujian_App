# Work Task · Recognition Capture / 07 · 首次拍照教学 V1

Task ID: RECOGNITION-FIRST-USE-GUIDE-V1
Repository: pan277942135/Yujian_App
PR: #145
Branch: audit/design-system-v3-1-phase1-master-baseline
Status: IN_PROGRESS
Scope: Android runtime implementation + tests + exact Frozen PNG binary import
Main merge: FORBIDDEN
Design redesign: FORBIDDEN

> 本任务是当前 PR #145 的增量实现任务。执行前必须重新读取远端 branch HEAD；不得覆盖已有提交、不得 rebase main、不得等待旧 CI 矩阵。

## 1. 唯一目标

把「识别过程 → 07 · 首次拍照教学 → P01/P02」从当前“单个 AlertDialog 占位实现”升级为真正的两页首次拍照教学 Overlay，并完成：

1. Android Compose 结构化实现；
2. 首次触发/完成持久化；
3. 左右滑动 + 下一张/上一张；
4. 跳过/开始拍摄边界；
5. 遮罩不可穿透到快门；
6. 权限/预览/Gallery 边界；
7. 视觉 Runtime Evidence；
8. P01/P02 Frozen PNG 二进制原字节导入 Git；
9. PNG SHA-256 / bytes / dimensions Gate；
10. Design Manager 实际预览最终验收；
11. APK + SHA-256 + 测试报告。

## 2. 权威优先级

### L0 · 当前冻结高保真

design/pages/recognition/first_use_camera_guide/frozen/

- P01: P01_First_Use_Camera_Guide_Frozen.png — 941×1671 — 1,988,159 bytes — SHA-256 dc731cca5470ef2154bb06172b9836e823ea780b0833f04e34caaf883e465c50
- P02: P02_First_Use_Camera_Guide_Frozen.png — 941×1671 — 2,042,852 bytes — SHA-256 fb092235fd043a42aa8250aba9dfdb91a78e96549dd5a63f8e89cf7433685f8f
- P03: ARCHIVED_P03_Landscape_Horizontal_Fish_Superseded_20261016.png — 941×1672 — 2,001,322 bytes — SHA-256 1a3008483ac5708a0649501cf2aef6c799d47b95bcc2c1b1272fa64e68f7f9b3

这些 PNG 是 Design Manager 的视觉权威，不是 Android Runtime 的整页图片资源。

### L1 · 页面合同

design/pages/recognition/first_use_camera_guide/First_Use_Camera_Guide_Spec_V1.md

### L2 · Recognition Capture V3.1

- design/system_v3/08_Page_Contracts/recognition/capture/Acceptance_Contract_V3_1.json
- design/system_v3/08_Page_Contracts/recognition/capture/Data_Contract_V3_1.json
- design/system_v3/08_Page_Contracts/recognition/capture/Layout_Contract_V3_1.json
- design/system_v3/08_Page_Contracts/recognition/capture/State_Machine_V3_1.json
- design/system_v3/08_Page_Contracts/recognition/capture/Engineering_Contract_V3_1.md
- design/system_v3/08_Page_Contracts/recognition/capture/Capture_Accessibility_Responsive_Matrix.md

### L3 · V3.1 Shared Foundation

只复用已有 Typography / Color / Geometry / Background / Icon / Component tokens；不得创建页面私有设计 Token。

### L4 · 历史 P02 FirstUse 方案

只用于参考历史模板、Pager、首次触发、状态持久化和静态资源拆分；页面数量、方向与交互以两页 V1 冻结合同为准。

## 3. 当前代码问题：必须先修

当前 IdentifyScreen.kt 已有 RecognitionFirstCaptureGuidePolicy.kt 和 shouldShowRecognitionFirstCaptureGuide(...); 这部分逻辑可以复用。

但当前 UI 仍是 AlertDialog，只有标题、说明、跳过、开始拍照。这不是冻结的 P01/P02。

### 当前 P0 缺陷

当前 AlertDialog 的 onDismissRequest 会调用 dismissGuide，而 dismissGuide 会直接写 capture_guide_v1_completed=true。因此系统 Back / Dialog dismiss / 外部 dismiss 可能把未完成教学误标记为完成。必须修复。

## 4. Android Runtime 结构

### 4.1 不新增 Camera Route

继续使用 identify。不要新增 first_use_guide 路由。

原因：首次教学是 Recognition Capture 的 Additive Overlay。CameraX 仍由现有 IdentifyScreen 拥有。

### 4.2 推荐组件结构

新增 app/src/main/java/com/yujian/ai/ui/identify/RecognitionFirstCaptureGuide.kt。

结构：

IdentifyScreen
└── Box / CameraX Preview
    ├── Existing Capture controls
    └── RecognitionFirstCaptureGuideOverlay
        ├── GuideTopBar
        ├── GuidePager
        │   ├── GuidePage P01
        │   ├── GuidePage P02
        │   └── GuidePage P03
        ├── GuideHero
        ├── GuideErrorExampleRow
        │   ├── FishHeadOutOfFrame
        │   ├── FishTooSmall
        │   └── ExcessiveOcclusion
        ├── GuideMessage
        ├── PagerIndicator
        └── GuideActionBar
            ├── Skip
            ├── Previous
            └── Next / Start

不要把 Frozen P01/P02 PNG 当成运行时整页背景。

运行时必须是 CameraX Preview + 独立照片/示例资源 + Compose 文本 + Compose 布局 + Pager + 代码实现的交互。

## 5. 两页状态合同

建立 RecognitionFirstCaptureGuidePage：P01 / P02 / P03。

建立 RecognitionFirstCaptureGuideState，至少包含 currentPage / visible / completed。

不要把页面内容写成三套重复 Composable。采用参数化 GuidePageSpec：page、title、subtitle、heroAsset、errorAssets、orientation、primaryAction。

P01：手机教学方向竖屏；鱼体横向；三个错误示例也全部横向鱼体。
P02：横屏教学方向；鱼体横向；三个错误示例也全部横向鱼体。
Former P03：historical/compatibility only。

注意：P03 是“教学用户横屏拍摄”，不是把整个 Guide UI 强制旋转成 Android landscape。P01/P02 冻结 PNG 为 941×1671；P03 为 941×1672。

## 6. 页面内容合同

每页固定：

Hero = 唯一正确示范：鱼体完整、构图清晰、鱼体占比合适，不再增加第二个“正确”卡。

三个错误示范固定顺序：
1. 鱼头不在画面；
2. 鱼体过小；
3. 遮挡过多。

P01：横鱼 / 横鱼 / 横鱼。
P02：横鱼 / 横鱼 / 横鱼。
Former P03：historical/compatibility only。

## 7. Interaction Contract

P01：下一张 → P02。
P02：上一张 → P01；开始拍摄 → 关闭 Guide → Capture Ready。

开始拍摄绝对不能自动调用快门。

## 8. Skip / Back / Close / Outside-touch

### 明确完成动作

只有：跳过；P02 开始拍摄。才写 capture_guide_v1_completed=true。

### 非完成退出

以下动作不得写 completed：Android System Back；顶部 Back；顶部 Close（如果冻结视觉中存在）；遮罩点击；Activity 暂停/恢复；进程被系统杀死；Camera provider 失败；Permission denial；Gallery 进入；Gallery cancel。

这些行为只能关闭/退出当前 Guide 或保持当前 Guide 状态，不得误判为“教学已完成”。

Guide 可见期间 Capture Button 不得收到 touch-through。

测试：Guide visible → tap shutter → TAKE_PICTURE_REQUESTS = 0。

## 9. 首次触发逻辑

复用 shouldShowRecognitionFirstCaptureGuide。

触发条件：cameraPermissionGranted=true；previewReady=true；galleryPickerPending=false；guideCompleted=false。

即 Permission Granted → CameraX Ready → Guide，而不是 Identify route entered → Guide。

如果用户从 Home 点击从相册选择：Home → identify?openGallery=true → Photo Picker。此流程不得弹首次教学。

## 10. Persistence / Backend 边界

本功能没有后端 API 依赖。不要新增 API endpoint、数据库字段、server preference、user profile 字段、remote config 或登录同步字段。

当前合同使用 device-local SharedPreferences：文件 yujian_capture_guide_v1；key capture_guide_v1_completed。

App 首次安装/清除数据 → false → 显示；Skip / P03 Start → true；后续 Capture → 不显示；登录/游客身份不改变此标识；多设备不要求同步。

## 11. Motion

只实现必要动效，不重新设计：首次进入轻量 fade/slide，遵循既有 FirstUse 动效合同；Pager 正常 horizontal pager transition；Pager indicator 明确当前页。

示例内容不做扫描/HUD/雷达动画；不增加 neon / glow / AI scanner；默认无声音；默认无额外触觉；不影响 CameraX Preview 生命周期。

## 12. Responsive / Insets

Frozen PNG：P01 941×1671；active P02 target canvas 941×1672，source pending。Android Runtime canonical device：API 28 / 1080×1920。

严禁要求 Frozen PNG 与 Runtime Screenshot 像素完全一致。验收必须保持 Frozen 原始尺寸和 SHA；Runtime 使用 reference-space mapping；统一缩放；不得独立 X/Y 拉伸；分别报告 Frozen coordinates 与 Runtime coordinates。

对 941→1080 使用统一 scale = runtimeWidth / 941。P03 相对 P01/P02 高 1px 属于原始画布差异，不得修改 Frozen PNG 补像素。

## 13. Accessibility

每个操作必须有 contentDescription/semantics、Role.Button、selected/current state、Pager page state，最小 44×44dp touch target。

至少覆盖：返回、跳过、上一张、下一张、开始拍摄。

TalkBack 顺序：Top navigation → page title/content → Hero → three examples → page indicator → Skip → Previous → Next/Start。

fontScale 1.0 / 1.3 / 2.0 均不得裁切关键操作；不得通过缩小字体获得“塞得下”。

## 14. Asset / PNG 二进制导入任务

Work 必须找到原始 Frozen PNG。不能截图、JPEG 转 PNG、PNG 重编码、resize、crop、重新生成、用 Design Manager screenshot 替代。

目标目录：design/pages/recognition/first_use_camera_guide/frozen/

导入前对每个文件执行 file、sha256sum、wc -c、Pillow Image.open，并核对以下精确值：

| Asset | Size | Bytes | SHA-256 |
|---|---:|---:|---|
| P01 | 941×1671 | 1,988,159 | dc731cca5470ef2154bb06172b9836e823ea780b0833f04e34caaf883e465c50 |
| P02 | 941×1671 | 2,042,852 | fb092235fd043a42aa8250aba9dfdb91a78e96549dd5a63f8e89cf7433685f8f |
| P03 | 941×1672 | 2,001,322 | 1a3008483ac5708a0649501cf2aef6c799d47b95bcc2c1b1272fa64e68f7f9b3 |

任何 mismatch = ASSET_IMPORT_FAIL，禁止自行修图。

使用 Git 原字节导入，不得经过文本接口或任何重新编码流程。导入后再次执行 sha256sum / wc -c / git hash-object。

生成 design/pages/recognition/first_use_camera_guide/asset_import_report.json，至少包含 path、filename、width、height、bytes、sha256、git_blob_sha、source_filename、import_timestamp、status。

## 15. Runtime Asset 规则

Android Runtime 不得引用上述整页 Frozen PNG。

如果运行时缺少 Hero / error example 的独立照片源：优先在历史 FirstUse Asset Package、当前仓库和 Work workspace 查找原始独立照片；找到后登记 page-specific asset、记录 source SHA、绑定 Android resource、用 Compose 重建页面。

禁止从 Frozen P01/P02 整页截图裁 UI 或照片作为 Runtime Shortcut。

若原始独立照片源确实不存在，标记 ASSET_SOURCE_BLOCKED；必须明确报告缺失源，不得用重新生成图片冒充源资产。

## 16. Design Manager

继续使用 design/registry/experience_registry_v1.json。

结构：
识别过程
└── 07 · 首次拍照教学
    ├── P01 · 竖屏拍摄示意
    ├── Former P02 · 竖拍鱼类拍摄示意 (removed)
    └── Former P03 · 横屏拍摄示意 (historical/compatibility only)

父级 = authority_gallery；子项 = repo_image。

导入 PNG 后：Manifest status=FROZEN；binary_import=PASS；Design Manager P01/P02 均须真实加载；Hash deep-link 保持可用。

执行 python3 scripts/verify_design_manager_navigation.py。

如果 Pages artifact 可构建，再执行 python3 scripts/verify_design_manager_navigation.py --site _site --expected-build "$GITHUB_SHA"。

## 17. 必须新增/更新测试

### JVM Unit

扩展 RecognitionFirstCaptureGuidePolicyTest。

Trigger：permission=false → false；preview=false → false；gallery pending=true → false；completed=true → false；all ready + incomplete → true。

Navigation：P01 next → P02；P02 previous → P01；P02 next → P03；P03 previous → P02；P03 start → dismiss + complete；P01/P02 next 不 complete；Skip → complete。

Completion：Back → not complete；Close → not complete；outside dismiss → not complete；Skip → complete；P03 Start → complete。

### Compose / Android Instrumentation

新增 RecognitionFirstCaptureGuideRuntimeTest。

J01 首次权限完成后：permission granted → camera provider ready → guide visible。
J02 首次 Guide 默认 P01。
J03 P01 下一张 → P02。
J04 P02 上一张 → P01。
J05 P02 下一张 → P03。
J06 P03 上一张 → P02。
J07 P03 开始拍摄：Guide hidden、Capture Ready、shutter invocation count=0。
J08 Skip：Guide hidden、completed=true、shutter count=0。
J09 重新进入 Camera：completed=true、Guide 不出现。
J10 清除 app data：Guide 再次出现。
J11 Gallery entry：Guide 不出现。
J12 Permission denied：Guide 不出现。
J13 Provider not ready：Guide 不出现。
J14 Guide visible 点击 shutter：TAKE_PICTURE_REQUESTS=0。
J15 Guide visible 点击 Gallery：不穿透、不触发 Camera capture。
J16 左右 swipe：P01 → P02 → P03；P03 → P02 → P01。
J17 System Back：不写 completed、不触发 shutter。
J18 rotation / Activity recreation：未完成 Guide 不得被错误标记完成；已完成 Guide 不再次出现。
J19 TalkBack / semantics：所有 actions 有 role/name；page indicator 有 current state。
J20 fontScale 1.0 / 1.3 / 2.0：不裁切关键操作。

## 18. Visual Evidence

Canonical device：API 28 / 1080×1920。

每页输出 P01_runtime.png、P02_runtime.png、P03_runtime.png；对应 Frozen reference；Overlay；Diff。

生成 first_use_camera_guide_runtime_report.json，包含 APK SHA、source SHA、device、API、viewport、density、fontScale、safeInsets、page、boundsInWindow、reference coordinates、runtime coordinates、visual observations、pass/fail。

比较必须使用 reference-space、uniform scale；不得改变 Frozen source；不得把 941×1671/1672 当成 1080×1920 physical pixel target。

## 19. APK / CI

当前仓库使用 JDK 17、Gradle 8.9、API 28 self-hosted runner、Pixel_2 / 1080×1920 runtime profile。

标准构建：

gradle -Pandroid.aapt2FromMavenOverride="$YUJIAN_AAPT2" test --stacktrace --no-daemon --max-workers=2
gradle -Pandroid.aapt2FromMavenOverride="$YUJIAN_AAPT2" :app:assembleDebug --stacktrace --no-daemon --max-workers=2
gradle -Pandroid.aapt2FromMavenOverride="$YUJIAN_AAPT2" :app:assembleDebugAndroidTest --stacktrace --no-daemon --max-workers=2
gradle -Pandroid.aapt2FromMavenOverride="$YUJIAN_AAPT2" :app:lintDebug --stacktrace --no-daemon --max-workers=2

Android Runtime 必须使用当前 SHA 构建出的唯一 APK pair。不要安装旧 APK。

## 20. Git 提交流程

严格：读取远端 HEAD → 实现第一阶段 → Unit Test → commit → push → 远端确认 → 第二阶段 → AndroidTest → commit → push → 远端确认 → PNG binary import → asset gate → commit → push → runtime evidence → final report。

不得一次做完所有东西后才提交。

建议阶段 Commit：
- feat(recognition): implement first-use camera guide pager
- test(recognition): add first-use camera guide runtime coverage
- assets(recognition): import frozen first-use guide pngs byte-identically
- qa(recognition): close first-use camera guide runtime evidence

## 21. 最终 PASS 条件

FIRST_USE_GUIDE_POLICY=PASS
FIRST_USE_GUIDE_UI=PASS
FIRST_USE_GUIDE_NAVIGATION=PASS
FIRST_USE_GUIDE_PERSISTENCE=PASS
FIRST_USE_GUIDE_TOUCH_ISOLATION=PASS
FIRST_USE_GUIDE_ACCESSIBILITY=PASS
FIRST_USE_GUIDE_RESPONSIVE=PASS
P01_BINARY=PASS
P02_BINARY=PASS
P03_BINARY=PASS
DESIGN_MANAGER_NAVIGATION=PASS
DESIGN_MANAGER_PREVIEW=PASS
ANDROID_BUILD=PASS
UNIT_TEST=PASS
ANDROID_TEST=PASS
LINT=PASS
RUNTIME_VISUAL_EVIDENCE=PASS
APK_SHA=RECORDED

最终报告必须明确 FROZEN_VISUAL 与 RUNTIME_IMPLEMENTATION 是两个不同层次。不得用 Frozen PNG、Design Manager screenshot、mock UI 或静态 Compose Preview 冒充 Android Runtime Evidence。

## 22. 失败分类

PRODUCT_FAILURE = 代码/行为错误。
DESIGN_PARITY_FAILURE = Runtime 与 Frozen/Contract 不一致。
ASSET_SOURCE_BLOCKED = 找不到原始独立 Runtime photo asset。
ASSET_BINARY_FAILURE = PNG bytes/SHA/dimensions mismatch。
CI_FAILURE = Workflow / build / test failure。
INFRA_BLOCKED = 真实 Android runner / ADB / emulator 不可用。

Infra blocked 时必须保留已完成的静态/Unit/Design Manager 结果，并继续不依赖该阻塞的阶段；禁止伪造 Runtime PASS。

## 23. 最终交付物

1. GitHub commits + SHA；
2. PR #145 最新 HEAD；
3. P01/P02 PNG binary import report；
4. Design Manager navigation PASS；
5. Design Manager preview PASS；
6. Unit test report；
7. Android instrumentation report；
8. 1080×1920 runtime screenshots；
9. boundsInWindow；
10. Frozen/Runtime/Overlay/Diff；
11. APK；
12. APK SHA-256；
13. 最终 QA report；
14. 未完成项 / blocker 分类。

完成后保持 PR #145 Draft / Open / Unmerged，不得 merge main。