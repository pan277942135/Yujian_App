# 15 · 首次拍照教学：设计资产到 Android Runtime 可执行交付合同

**合同 ID：** RFG-ANDROID-ASSET-DELIVERY-V1  
**状态：** STANDARD_DEFINED / ASSET_SOURCE_BLOCKED  
**适用页面：** P01 / P02 / P03 · Recognition First-use Camera Guide  
**规范阶段限制：** 本文件定义交付合同；不导入图片、不改 Android 源码、不实现校验器、不运行 Runtime。

## 1. 目标与非目标

将已冻结的三张整页高保真参考图，转化为 Android Compose 可消费、可追溯、可校验的独立教学图片资源，并规定图片缺失时必须 fail closed。

本合同不允许把 Frozen 整页 PNG 直接用作运行时整页背景，也不允许从整页 PNG 截图裁出 Hero/错误示例图。Android 继续使用 CameraX 实时预览作为背景，以 Compose 构建文字、布局、Pager 与操作；12 个示范图位置必须使用各自获批的独立图片资源。

## 2. 权威与资产分层

按以下顺序解释资源，不得混用：

1. **页面视觉权威**：design/pages/recognition/first_use_camera_guide/frozen/ 下的 P01/P02/P03 Frozen PNG。只用于页面结构、文字、层级、间距、色彩及示范区域的视觉对照；不是 Android Runtime 图片资源。
2. **页面行为合同**：First_Use_Camera_Guide_Spec_V1.md 与该目录的冻结规则。决定页序、手机/鱼体方向、正确示范与错误示范语义。
3. **独立 Runtime 图片登记主源**：design/pages/recognition/first_use_camera_guide/runtime_asset_manifest_v1.json。这是 12 个槽位的唯一机器可读绑定登记；本合同不另建第二份资源清单。
4. **获批原图**：存放于 design/pages/recognition/first_use_camera_guide/runtime_assets/v1/，保留原始字节和来源记录。
5. **Android Runtime 资源**：存放于 app/src/main/res/drawable-nodpi/，按 Manifest 指定的资源名加载。V1 默认要求与获批原图逐字节一致。
6. **Android 实现绑定**：每个槽位必须从 Manifest 中的 android_resource_name 映射到实际 R.drawable，再由对应 Compose Image 展示；不允许将 null、空容器、纯文字、实时 CameraX 背景或通用占位图当作示范图。

若上层合同与 Manifest 冲突，不能自行选取对开发最方便的值；登记 REVIEW_REQUIRED，由 Design/Asset owner 修订权威源。

## 3. 十二个必需视觉槽位

每页必须具备一个正确示范 Hero 和三个错误示范，共 12 个必需槽位。槽位数不等于唯一图片数；复用图片必须逐槽位明确登记并通过语义审核。

| Slot ID | 页面 / 手机方向 | 鱼体方向 | 必须呈现的语义 |
|---|---|---|---|
| P01_HERO | P01 / 竖屏 | 横向 | 正确示范；鱼头与鱼尾均在原图画面内，鱼体清楚、占比适当、遮挡不过量 |
| P01_ERROR_HEAD_OUT | P01 / 竖屏 | 横向 | 错误示范；鱼头确实超出原始照片边界，不能仅靠 Android Image 裁剪制造 |
| P01_ERROR_TOO_SMALL | P01 / 竖屏 | 横向 | 错误示范；鱼体在原始照片中明显过小，须记录鱼体框/尺度注释 |
| P01_ERROR_OCCLUDED | P01 / 竖屏 | 横向 | 错误示范；鱼体存在明显遮挡，须记录遮挡范围与原因 |
| P02_HERO | P02 / 竖屏 | 竖直 | 正确示范；鱼头与鱼尾完整入画，鱼体清楚、占比适当 |
| P02_ERROR_HEAD_OUT | P02 / 竖屏 | 竖直 | 鱼头确实超出原始照片边界 |
| P02_ERROR_TOO_SMALL | P02 / 竖屏 | 竖直 | 原图中的竖向鱼体明显过小，附鱼体框/尺度注释 |
| P02_ERROR_OCCLUDED | P02 / 竖屏 | 竖直 | 原图中的竖向鱼体存在明显遮挡，附遮挡注释 |
| P03_HERO | P03 / 横屏 | 横向 | 正确示范；鱼头与鱼尾完整入画，鱼体清楚、占比适当 |
| P03_ERROR_HEAD_OUT | P03 / 横屏 | 横向 | 鱼头确实超出原始照片边界 |
| P03_ERROR_TOO_SMALL | P03 / 横屏 | 横向 | 原图中的横向鱼体明显过小，附鱼体框/尺度注释 |
| P03_ERROR_OCCLUDED | P03 / 横屏 | 横向 | 原图中的横向鱼体存在明显遮挡，附遮挡注释 |

页面 P03 表示向用户教学“手机横屏拍摄”，不表示 Guide Compose 页面必须旋转为 Android landscape。页面的响应式排版仍按 Capture 页面合同处理。

## 4. 文件路径与绑定命名

### 4.1 获批源文件

每个获批独立源图登记在：

design/pages/recognition/first_use_camera_guide/runtime_assets/v1/

文件名使用稳定 Slot ID 对应的 Android 资源基名，例如：

- recognition_guide_p01_hero.jpg
- recognition_guide_p01_error_head_out.jpg
- recognition_guide_p01_error_too_small.jpg
- recognition_guide_p01_error_occluded.jpg

P02/P03 同规则。实际扩展名可为经批准的 PNG/JPEG/WebP；不得仅为迎合文件名而转码。

### 4.2 Android 资源

Manifest 中的 Android 目标路径必须位于：

app/src/main/res/drawable-nodpi/

并使用 Android 合法资源名，例如：

app/src/main/res/drawable-nodpi/recognition_guide_p01_hero.jpg

android_resource_name 对应 R.drawable.recognition_guide_p01_hero。源文件与 Android 资源文件在 V1 默认必须逐字节一致：source_sha256 == runtime_sha256。Compose 的显示缩放不改变源文件字节。

如果未来确实需要派生格式、裁剪或压缩，必须先升级合同并新增可审计的派生来源字段、变换参数和独立批准；不得静默替换原图，也不得在本规范阶段做任何转码。

## 5. 来源、授权与版本字段

每个槽位必须记录以下信息；缺一不可进入 APPROVED：

- 稳定 slot_id、页面、角色和目标鱼体方向。
- 原始来源 URL、内部素材档案 ID 或可追溯的来源记录。
- 权利人/来源主体、许可依据、允许的用途及是否允许提交公开 GitHub、打入 App 与用于 CI。
- 涉及可识别人物时的肖像/隐私审核结果；未经核准的人像或水印素材不得直接升格。
- 原始文件路径、格式、字节数、像素宽高、原始字节 SHA-256、EXIF Orientation（如适用）。
- Android 目标资源路径、资源名、Android 文件 SHA-256。
- 页面语义注释、审核状态、审核者和审核时间。
- 如复用图片：allow_reuse=true、reuse_group、每个槽位独立的语义适配审核结论。

Git Blob SHA 与文件 SHA-256 是不同概念，必须分别记录，不能互相替代。候选文件、已存在的设计素材、模型 Golden 图片都不自动构成来源授权。

## 6. 图像语义和显示合同

- CORRECT_HERO 必须是照片本身正确示范；源图中鱼头、鱼尾完整可见。不得依赖运行时遮罩隐藏鱼体缺陷。
- ERROR_HEAD_OUT_OF_FRAME 必须在原始照片边界处截断鱼头。不得用 Android ContentScale.Crop 临时切掉鱼头来伪造错误示范。
- ERROR_FISH_TOO_SMALL 必须在源图中展示明显过小的鱼体；需记录归一化鱼体框和长轴占比，供设计/资产审核复核。
- ERROR_EXCESSIVE_OCCLUSION 必须在源图中真实体现遮挡；需记录遮挡对象/区域及估计遮挡比例或明确的审核注释。
- P01/P03 目标鱼体为横向，P02 为竖直；方向与语义必须由人工审核并记录，不得仅由文件名推断。
- 默认使用保持宽高比且不裁切语义内容的 ContentScale.Fit。若冻结视觉合同明确要求其他显示模式，必须在 Manifest 记录 fit_mode、焦点区域/裁切参数及其审核依据。
- 源图像素尺寸必须足以支持最大批准设备 profile 的实际渲染尺寸；不足时标记 REVIEW_REQUIRED，不能无记录地放大或重采样。
- 每张图都必须可解码、方向正确、色彩可读，且在真实屏幕上能够辨认目标语义。

## 7. 交付状态机

每个槽位采用以下状态：

MISSING → CANDIDATE → SOURCE_APPROVED → ANDROID_BOUND → VERIFIED → APPROVED

- MISSING：没有可用源图或必要登记字段。
- CANDIDATE：找到了候选文件，但来源/语义/授权至少一项未核准。
- SOURCE_APPROVED：来源授权与语义已核准，原始 SHA/尺寸已记录。
- ANDROID_BOUND：Android 资源路径和资源名存在，文件 SHA 与来源满足合同。
- VERIFIED：文件解码、方向、布局绑定和真实 Runtime 采集均有证据。
- APPROVED：以上全部满足，Asset owner 与 QA Gate 责任人完成签核。

不得越级将 CANDIDATE 或 MISSING 标记为 APPROVED。设计 Frozen 状态与 Runtime Asset 状态是两个独立维度。

## 8. 与开发及验收的接口

Android 开发实施时必须：

1. 读取 Manifest，而非自行猜测资源名。
2. 为每个槽位建立可追踪的 Compose Image 绑定及独立 testTag，标签至少包含 slot_id。
3. 通过静态检查确认 12 个槽位均有非空资源引用；禁止 heroAsset = null、errorAssets = listOf(null, ...) 等空绑定。
4. 不提供“图片缺失时隐藏 Image，只显示文字”的生产 fallback。
5. 不以实时 CameraX 背景、整页 Frozen PNG、截图裁切、Compose Preview 或 Mock 图替代独立示范图片。
6. 输出资源绑定清单：Slot ID、R.drawable 名、源 SHA-256、Android 文件 SHA-256、运行时截图证据。
7. 资源未批准时保留可诊断的阻断状态；不得将此页面标记为高保真完成。

## 9. 版本和变更管理

- Manifest 的 schema/version 必须固定；源图内容变化、替换、授权变化、鱼体方向变化、裁切策略变化都必须更新资源版本并重新审核。
- 更新源图时生成新 SHA，不覆盖历史验收报告引用的文件。
- PR 必须说明新增/替换槽位、授权证据位置、源/运行时哈希、视觉差异和受影响用例。
- 冻结整页 PNG 不因 Runtime 资产更新而重新编码或覆盖。
- 本阶段仅定义合同；当前 12 个槽位均为缺图/未绑定状态，不能声称 ASSET_READY 或 FIRST_USE_GUIDE_VISUAL=PASS。

## 10. 唯一机器可读主源

- Manifest：design/pages/recognition/first_use_camera_guide/runtime_asset_manifest_v1.json
- Schema：design/pages/recognition/first_use_camera_guide/runtime_asset_manifest_v1.schema.json
- 缺图 Fail-closed Gate：docs/qa/recognition_main_journey/spec_v1/16_MISSING_ASSET_FAIL_CLOSED_GATE.md

以上三个文件与 manifest.json 中的 Frozen 整页设计资源登记用途不同。manifest.json 继续登记 Frozen 页面参考图；新 Manifest 只登记独立 Runtime 示例图槽位。
