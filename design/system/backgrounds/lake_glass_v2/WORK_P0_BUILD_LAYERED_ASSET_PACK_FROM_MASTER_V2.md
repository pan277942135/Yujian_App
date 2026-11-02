# Work P0 — 从 Lake Glass V2 高清 Master 构建完整工程分层资源包

**Task ID:** YUJIAN_LAKE_GLASS_V2_MASTER_TO_LAYERED_ASSET_PACK_P0  
**Repository:** pan277942135/Yujian_App  
**规范：** design/system/backgrounds/lake_glass_v2/Lake_Glass_Background_System_V2.md  
**页面映射：** design/system/backgrounds/lake_glass_v2/usage_map_v2.json  
**本任务边界：只完成 Master 审计、图层拆解/受控重建、资源导出、静态合成验证和工程资源包登记；暂不开始全 APP 背景替换，不修改 Empty Home，不修改 V1 历史资产。**

---

## 0. 唯一目标与完成定义

将已确认的 Lake Glass V2 高清 Master 候选，制作成可被 Android Compose / Canvas / Shader 统一渲染系统直接使用的、可追溯、可验证的工程分层资源包。

交付不是“若干切图”，而是完整闭环：

1. 原始 Master 与来源文件不可变归档、SHA 校验。
2. 明确哪些视觉元素是真正独立图层，哪些仍然烘焙在底图中。
3. 对可以安全分离的元素，输出透明 RGBA 图层、SVG/Vector Path、Mask、纹理等适合的工程格式。
4. 对无法无损分离的部分，保留在原始 Master/静态底层，不伪造可编辑源文件。
5. 使用统一坐标、统一锚点和统一合成规则重组，生成与 Master 对齐的合成图及差异报告。
6. 形成完整 Manifest、Layer Pack Schema、Anchors、Crop-safe Map、资源预览和自动校验脚本。
7. 让下一阶段 Android Work 能直接读取资源包，不需要再次猜测图层位置、格式、尺寸、透明度、锚点或叠放顺序。

**只有静态重新合成达到视觉验收要求后，才允许标记 ASSET_PACK_READY_FOR_ANDROID。**

---

## 1. 输入文件、来源和完整性校验

### 1.1 指定输入

本任务输入为对话/任务附件中的以下实际文件：

| 角色 | 文件名 | 尺寸 | 预期 SHA-256 |
|---|---|---:|---|
| Master（主权威图） | YuJian_Lake_Glass_V2_Master_Candidate_02_1440x3120.png | 1440×3120 | d0bd07ba379491e8b93468c616468fc60d59a2dfebc11061cf4d14e9788c6e12 |
| 原始源图（溯源） | 宁静湖光玻璃水境背景.png | 852×1846 | f3dc1549919f48f939df5a6cf5baf18fb6846164c6f857df8e20cfe247b495ba |
| Master Manifest | YuJian_Lake_Glass_V2_Master_Candidate_02_manifest.json | JSON | 623b978487ef294b7261c45e8ec1612ce372df61bbdca3859f5a9faa31af661b |

以上 SHA 是输入预期值，Work 必须对实际收到的二进制重新计算。任何不一致立即停止后续处理并报告文件名、实际 SHA、尺寸和来源，不允许静默替换、重命名冒充或从聊天预览截图重建。

### 1.2 必须如实记录的来源事实

Candidate 02 是从 852×1846 源图做细节保留放大并进行少量局部纹理处理形成的 1440×3120 RGB PNG，不是原生 1440×3120 多图层设计源，也不能凭该 PNG 推导出原始 SVG/PSD。

Manifest 需保留：
- source_file、source_sha256、source_dimensions
- output_file、output_sha256、output_dimensions
- format、color_profile、generation/refinement_note
- status = CANDIDATE_VISUAL_NOT_FROZEN（在产品负责人正式冻结前不可擅自改为 FROZEN）
- layer_source_type：原生源文件 / 从栅格提取 / 受控重建 / 程序生成，逐层注明

### 1.3 原件保护

把原件放入只读逻辑目录：
design/system/backgrounds/lake_glass_v2/assets/source_master/

原件按字节归档。任何图像优化、裁切、调色、压缩、抠图、锐化均输出为新文件，不允许覆盖 Master。

---

## 2. 第一步：视觉资产审计与场景坐标标定

先不切图、不写 Android。对 1440×3120 Master 建立一张标注审计板。

### 2.1 固定坐标系统

- 设计坐标：W=1440, H=3120。
- 原点：左上角 (0,0)。
- X 向右，Y 向下。
- 所有 bbox 使用像素坐标 [x,y,width,height]。
- 所有锚点同时保存像素坐标和归一化坐标 u=x/1440, v=y/3120。
- 图层的裁切、缩放和锚点都必须以 Master 坐标系为准；严禁各图层各自拉伸至屏幕。

### 2.2 必须识别的场景元素

至少识别并标注：

1. Lake Base：水体主体、雾白至湖水青的渐变、远近层次和环境散射。
2. Sunlight：右上入射的晨光、亮度分布、柔光边缘、光照遮罩。
3. Water Caustics：主要分布于底部的水面焦散/折射纹理。
4. Aquatic Grass Left：左下水草群，包含每组根部、叶尖、前后层次。
5. Aquatic Grass Right：右下水草群，同上。
6. Water Droplets：每一颗可辨认的水滴分别编号；标注主体、透明边缘、内部折射和高光。
7. Quiet/Content Safe Zone：中央阅读保护区，明确允许/不允许的纹理强度。
8. Crop Safe Zone：多屏裁切时必须保护的水滴、水草和光照关键区域。
9. Occlusion/Protected Zone：为未来页面 UI 留出的内容避让区域。

### 2.3 审计输出

输出：
- reference/master_annotated.png：带编号、bbox、锚点和安全区的审计图。
- reference/master_region_map.json：各区域坐标、语义、视觉层级。
- reference/master_visual_inventory.md：逐元素审计、分离难度和风险。
- reference/master_color_samples.json：主色、阴影色、晨金、焦散亮度样本。
- reference/master_edges_and_masks_preview.png：边缘/候选遮罩预览。

必须人工审查分割结果，不能只交自动分割模型的输出。

---

## 3. 第二步：建立“可分层性”判定，不得强行抠图

这是整个任务最重要的质量门槛。当前输入为已扁平化 RGB 图，因此像素中可能已经混合了水草、光照、背景、折射。视觉上分得开，不代表像素上可以无损拆开。

每个元素必须标注以下一种状态：

| 状态 | 定义 | 允许的实现 |
|---|---|---|
| A — NATIVE_LAYER | 找到真实可用的原始独立图层/矢量源 | 原样复用并登记来源 |
| B — CLEAN_EXTRACTION | 有明确 Alpha 边界，可从 Master 提取且无明显背景污染 | 输出 RGBA + 边缘检查 |
| C — CONTROLLED_RECONSTRUCTION | 元素可提取，但其下方背景被遮挡，需有限区域重建 clean plate | 输出重建 mask、方法、风险和差异图，人工验收 |
| D — BAKED_STATIC | 元素与背景光照/折射高度融合，无法可靠分离 | 保留在 Master/静态 Base 中，不独立重复渲染 |
| E — PROCEDURAL_MASK | 适合由代码控制的低频亮度/遮罩/路径，不需要抠出实体 | 输出 mask/参数/路径，不重复画原有实体 |

### 强制规则

- 不得把一张完整 PNG 自动矢量化后宣称获得真实 SVG 源。
- 不得把半透明水滴粗暴抠成硬边 PNG。
- 不得用 AI Generative Fill 大面积补出原图中不存在的水体或植物。
- Clean Plate 仅可在元素原本覆盖区域做受控修复；必须记录 mask、修复算法/软件、人工修正范围。
- 如果 clean plate 无法可信恢复，则该元素标记为 BAKED_STATIC，保留在底图里；动态代码只能在不重复绘制该元素的区域施加轻微光照/遮罩变化。
- 同一水滴/同一片水草/同一束光在最终合成画面中只允许出现一次，禁止底图和透明图层双重叠加。
- 不得为满足“SVG 要求”而把自然叶片变成粗糙、规则、塑料感矢量图。

输出：layer_feasibility_matrix.csv + layer_feasibility_review.md，每层均有判定、原因、置信度、是否可动、是否需要 clean plate。

---

## 4. 第三步：逐层制作工程资产

### 4.1 01 Lake Base

目标：承载水体主体、空间深度和不应拆出的自然环境细节。

优先顺序：
1. 如果有可靠的原始干净底图，复用原图。
2. 如果可通过小范围 clean plate 恢复，则只对遮挡区做受控修复。
3. 若无法可信恢复，允许 lake_base_static_master.png 直接保留完整 Master，后续动态效果仅作用于独立 mask 指定的区域，不额外叠加已烘焙的水草/水滴/光线。

要求：
- 不得把背景大面积变平或变成纯色渐变。
- 保留原图湖水色相、纵深、明暗层次。
- 不能因拆层让中部出现色块、模糊补丁或重复纹理。
- 导出 RGB PNG；若确有透明通道需求则 RGBA PNG。

### 4.2 02 Sunlight

需要分清“真实晨光”和“代码可控制的光场参数”。

交付：
- sunlight_luma_mask.png：灰度光强遮罩。
- sunlight_soft_mask.png：柔和边缘/散射遮罩。
- sunlight_anchor.json：光源中心、主方向、扩散范围、保护区。
- sunlight_vector_paths.svg（若有明确可表达的路径/遮罩形状）。

建议属性：
- 光源方向：右上 → 左下/中下区域自然散射。
- 亮度必须来自 Master 取样，不可任意增加曝光。
- Mask 只用于控制已有光场的极弱变化，不得在静态合成时重复叠加一束“新太阳”。
- 光场若已经完整烘焙在 Base，则将其登记为 BAKED_STATIC，只导出分析用 mask，不进入默认可见图层栈。

### 4.3 03 Water Caustics

交付：
- caustics_luma.png 或 caustics_alpha.png。
- caustics_tile.webp（仅在可无缝平铺且不改变原构图时提供）。
- caustics_region_mask.png。
- caustics_params.json：强度、频率、坐标范围、混合模式、动效许可。

要求：
- 保留自然、不规则、大小不一的焦散结构。
- 不允许简单平铺产生明显重复花纹。
- 中央阅读区为低强度；下部可见但不刺眼。
- 默认静态合成必须与 Master 对齐。
- 若原焦散和底图不能分开，保留 baked 状态，输出 mask 仅供未来局部 shader 调制。

### 4.4 04 Aquatic Grass

左右水草必须分组，但不得为了拆分牺牲自然细节。

每侧交付：
- grass_left_rgba.png
- grass_right_rgba.png
- 如有多前后景层，另交付 grass_left_fg.png、grass_left_bg.png 等并登记真实来源。
- grass_left_skeleton.svg / grass_right_skeleton.svg：仅用于叶片主脉/运动骨架/锚点路径，不用少数几条粗曲线代替整株真实水草。
- grass_motion_bones.json：根部固定点、叶片分段、最大摆动范围、柔性权重。
- grass_edge_review.png：黑/白/棋盘格三种背景的 Alpha 边缘审查图。

要求：
- 保留叶片自然弯曲、宽窄变化、叶脉纹理、透明边缘、前后景深。
- 根部锚定在原 Master 对应位置。
- 不允许硬抠边、黑边、白边、绿色溢色和叶片断裂。
- 不允许将左右水草镜像复制成完全对称。
- 如果水草已经与水面折射/阳光紧密融合，先标注融合边界；无法分离的部分保留在 Base。
- SVG 只承担可控骨架/路径，RGBA 图承担写实细节。

### 4.5 05 Water Droplets

每颗水滴分别编号，不能把所有水滴合成一个透明大图层。

每颗交付：
- droplet_01_rgba.png、droplet_02_rgba.png、droplet_03_rgba.png。
- droplet_XX_shape.svg：若可可靠提取，仅存轮廓/遮罩，不用矢量模拟内部真实折射。
- droplet_XX_highlight_mask.png：高光/折射控制遮罩（可选）。
- droplets_anchors.json：每颗水滴的中心、bbox、尺寸、层级、原始位置。

要求：
- Alpha 透明边缘平滑，无黑底/白底污染。
- 保留内部折射与高光，不得改成普通圆形气泡。
- 水滴在静态合成时回到 Master 原始像素位置。
- 不允许重复渲染 Master 已经包含的同一颗水滴。

### 4.6 06 Scene Masks / Safe Zones

交付：
- content_quiet_zone.png：中央低纹理区域 mask。
- crop_safe_zone.json：各设备比例安全区。
- ui_protection_zones.json：通用标题、正文、照片、操作区域避让参考；只是约束数据，不在背景中画 UI。
- layer_exclusion_masks/：明确各图层在哪些区域禁止绘制。

---

## 5. 第四步：SVG / Raster / Shader 的格式决策

不要求所有资源都 SVG。按视觉真实性和运行时效率选择格式。

| 资产 | 首选格式 | 理由 |
|---|---|---|
| 水草写实叶片 | 透明 PNG / WebP lossless | 保留叶脉、边缘、细节 |
| 水草运动骨架 | SVG / JSON Path | 根固定、叶尖局部形变 |
| 水滴本体 | RGBA PNG / WebP lossless | 保留真实折射与高光 |
| 水滴轮廓/遮罩 | SVG Path / Alpha Mask | 供裁剪与高光参数使用 |
| 阳光光场 | 灰度 PNG Mask + JSON 参数 | 保留自然渐变 |
| 水面焦散 | 灰度/Alpha PNG、WebP lossless 或 Shader 输入 | 可控亮度/局部运动 |
| 基础水体 | PNG/WebP lossless + Compose Canvas | 保留摄影质感与空间层次 |
| 全局锚点 | JSON | 统一坐标，不依赖图像尺寸猜测 |

SVG 文件必须是可编辑源，含 viewBox、路径语义和注释。不得把 PNG Base64 嵌入 SVG 伪装成矢量。

所有 RGBA 图片：
- Straight Alpha 作为交换源；如 Android 上传/合成需要 Premultiplied Alpha，转换只发生在运行时并写明。
- 8-bit sRGB 默认；如源文件确有 16-bit/宽色域信息，保留源并说明转换。
- 不使用有损 JPEG 存透明图层。
- 对 runtime 派生文件记录源文件 SHA 和转换参数。

---

## 6. 第五步：统一图层坐标、锚点与参数

建立：
- lake_layer_pack_v2.json
- lake_anchors_v2.json
- lake_material_tokens_v2.json
- asset_manifest_v2.json

每个 Layer 至少包含：
- layerId、semantic、status
- sourceFile、sourceSha256、assetFile、assetSha256
- format、canvasSize、pixelBounds
- anchorPx、anchorNormalized
- zIndex、blendMode、alpha、scalePolicy
- motionEligible、motionProfile
- reconstruction、notes

紧裁切图层必须记录 pixelBounds 与原 Master 画布偏移，不能只记录裁切后图片宽高。图层顺序必须确定且唯一。所有 Layer 使用相同设计坐标与全局 transform。BAKED_STATIC 元素不进入可见独立叠加栈，避免双影。

Manifest 要记录授权/来源，不得编造版权声明；未知时标记 PENDING_REVIEW。

---

## 7. 第六步：静态重组与像素级验收

这是必须完成的验收，不是可选项。

### 7.1 重组方式

用与 Android 预期一致的顺序和混合模式，输出：
- reference/recomposed_static_1440x3120.png
- reference/master_vs_recomposed_overlay.png
- reference/master_vs_recomposed_difference.png
- reference/master_vs_recomposed_heatmap.png
- reference/edge_halo_check_black.png
- reference/edge_halo_check_white.png
- reference/edge_halo_check_checker.png

### 7.2 量化验收

对比原始 Master 与静态重组图：
- 相同尺寸、相同 sRGB、相同 alpha 处理。
- 计算 MAE、RMSE、PSNR、SSIM、差异像素占比。
- 对未编辑/未重建区域，要求像素完全一致。
- 对分离边缘和有限 clean plate 区域，建议 PSNR ≥ 45 dB、SSIM ≥ 0.995；任何达不到的层必须解释原因并人工确认，不能仅靠全图平均指标掩盖局部破损。
- 对水滴边缘、草叶边缘、晨光边缘分别输出 ROI 局部差异。
- 不允许黑边、白边、色边、叶片断裂、折射丢失、焦散重复、底图补洞痕迹。
- 必须提供 100% / 200% 局部放大审查图。

如果为了分层导致合成结果明显偏离 Master，必须回退该层，降级为 BAKED_STATIC；不能为了“图层数量好看”牺牲画面。

### 7.3 三态验收

必须有：
1. STATIC_MASTER：原图逐像素显示，作为绝对基准。
2. STATIC_LAYERED：Layer Pack 静态合成。
3. LAYER_INSPECTOR：每个图层单独开关、半透明叠加和 bbox 可视化。

STATIC_LAYERED 必须在相同视口下视觉接近 STATIC_MASTER。Inspector 不得成为产品 UI。

---

## 8. 第七步：响应式裁切安全区与 Android 预检

本任务不改产品页面，但资源包必须准备好下一阶段使用。

以 1440×3120 为唯一设计坐标，生成：
- 320×480 dp
- 360×640 dp
- 390×844 dp
- 430×932 dp

分别模拟 CenterCrop、TopCenter 与最终候选统一 Alignment。所有层必须应用同一矩阵，不允许水草和水滴各自单独适配。

输出：
- reference/crop_matrix_contact_sheet.png
- crop_safe_zone.json
- crop_policy_recommendation.md

如果不同屏幕比例无法同时完整保留所有边缘元素，优先保护中央内容可读性和主要水草/光照构图，允许自然裁切，不允许拉伸或按页面单独挪动元素。

---

## 9. 第八步：极轻动效可行性验证（只验证资源，不开发全 APP）

本阶段只定义和验证动效资源，不做全页面 Runtime 接入。

可动层必须具备：
- 水草：根部固定、叶片分段、叶尖最大偏移建议 ≤2dp；16–24s 低频循环参考。
- 阳光：24–36s 低频极小变化，不改变光源方向。
- 焦散：低频、局部、无明显平铺接缝。
- 水滴：默认静态；最多极弱高光变化，不整体上下漂浮。

输出 lake_glass_motion_profiles_v2.json。如果某元素属于 BAKED_STATIC，则 motionEligible=false，不可为了演示动画从底图上重复绘制它。

---

## 10. 最终资源包目录（必须完整）

design/system/backgrounds/lake_glass_v2/
- assets/source_master/：Master、原始源图、Manifest
- assets/base/：Base / clean plate
- assets/sunlight/：光照 mask 与路径
- assets/caustics/：焦散纹理与 mask
- assets/grass/：左右 RGBA 水草与骨架
- assets/droplets/：逐颗水滴素材
- assets/masks/：内容保护区、裁切区、图层排除区
- assets/vector/：可编辑 SVG / Path 源
- assets/metadata/：Manifest、Layer Pack、Anchors、Tokens、Motion Profiles、Crop Safe、可分层矩阵
- assets/reference/：标注图、重组图、差异图、裁切板、Inspector

必须另交 scripts/verify_lake_glass_layer_pack_v2.py。

如果某类层判定为 BAKED_STATIC，目录中不必伪造透明空图；Manifest 中明确 assetFile=null 与原因。

---

## 11. 自动校验脚本与 CI

实现 scripts/verify_lake_glass_layer_pack_v2.py，至少校验：

1. 所有输入源 Master 的 SHA 与登记值一致。
2. 所有引用文件存在、大小/尺寸/格式与 Manifest 一致。
3. 所有 JSON Schema 合法，所有 Layer ID 唯一。
4. 所有 bbox 不越界，锚点在有效范围内。
5. 透明图层确实有 Alpha，SVG 有有效 viewBox/path。
6. 图层排序唯一，BAKED_STATIC 不进入可见合成列表。
7. 重组图尺寸正确，差异报告存在，阈值结果可追溯。
8. Empty Home/V1 历史路径没有被本任务修改。
9. Manifest 的 SHA 可由脚本重新计算验证。
10. 报告层缺失、无来源、待授权、无法分离和不可动的原因。

脚本必须有明确 PASS/FAIL 和机器可读 JSON 输出；不得以“文件存在”代替图层有效性验证。

---

## 12. GitHub 提交与任务执行纪律

- 启动先读实时 Main、PR #302 和当前 V2 规范，建立独立 Work 分支。
- 不覆盖、不删除 V1 历史目录，不修改 Empty Home 任何文件。
- 资源包先提交到设计源目录；Android runtime 目录只放经验证的派生资源，不提前改页面背景。
- 任何重建区域必须有源图、Mask、重建方式、责任记录和差异图。
- 每个阶段形成 checkpoint 和 commit SHA，失败保留失败证据。
- 不得只写一份“拆解完成”的报告而没有真实二进制图层、Manifest、重组图和校验脚本。
- 若素材授权不明，标记待审，不要编造授权；禁止把网络抓取的相似水草/水滴素材混入冻结母版。
- Work 可自主使用图像处理、脚本、矢量工具完成实际操作，不要求用户逐张确认每个技术步骤；但不得擅自改变整体视觉方向。

---

## 13. 阶段门禁与交付清单

| 阶段 | 必须交付 | 通过条件 |
|---|---|---|
| P0-1 输入归档 | Master、源图、Manifest、SHA 报告 | 哈希/尺寸完全一致 |
| P0-2 视觉审计 | 标注图、坐标图、颜色样本、可分层矩阵 | 所有元素逐一登记 |
| P0-3 Layer Pack | Base、Sunlight、Caustics、Grass、Droplets、Masks、Vector | 真实文件与来源完整 |
| P0-4 坐标/Manifest | Layer Pack JSON、Anchors、Tokens、Motion Profiles | 可解析、无坐标冲突 |
| P0-5 静态重组 | 1440×3120 合成图、差异图、局部边缘图 | 通过像素/视觉门槛 |
| P0-6 多屏预检 | Crop Sheet、Safe Zone、裁切建议 | 同一 transform 逻辑 |
| P0-7 自动验证 | Python 校验脚本、JSON 报告 | 全项 PASS 或列出有理由的例外 |
| P0-8 PR 交付 | 分层资源包、审计报告、精确 SHA | 可供 Android 阶段直接接入 |

最终状态只允许：
- ASSET_PACK_READY_FOR_ANDROID
- ASSET_PACK_PARTIAL_WITH_BAKED_LAYERS（有明确说明和用户视觉验收要求）
- BLOCKED_SOURCE_OR_FIDELITY

不允许把“Master PNG 已导入”或“做了几张水草透明图”标记为资源包完成。

---

## 14. 本阶段明确不做

- 不做全 APP 背景替换与路由接入。
- 不修改 Empty Home、Empty Home 鱼漂/鱼线/涟漪/触感。
- 不修改 V1 历史规范、历史图片、历史 SHA。
- 不修改 AI Orb、WaterLight、13 阶段时钟、照片/按钮时序、CameraX Preview。
- 不把四页 UI 融合效果图作为背景层。
- 不重新生成 Master，不改变当前冻结构图、光源方向、水滴和水草位置。
- 不为 SVG 化牺牲自然细节。
- 不把不可分离的元素伪装成独立动态层。

**交付资源包通过后，再启动下一任务：LakeGlassEnvironmentHost Android 统一渲染与全 APP（Empty Home 除外）接入。**
