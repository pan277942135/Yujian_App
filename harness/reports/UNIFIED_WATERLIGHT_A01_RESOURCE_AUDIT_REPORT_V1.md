# 统一水光生命系统 A01｜资源与 Android 渲染管线技术审计 V1

Task ID：`YUJIAN_UNIFIED_WATERLIGHT_A01_RESOURCE_AUDIT_V1`  
审计记录时间：2026-10-31T15:47:04+08:00。  
性质：只读源码审计与静态模型接口检查；未运行 Android、未修改产品代码、未生成视觉资源、未合并 PR。  
结论状态：**源码审计完成；真实 Mask 坐标视觉验证为 BLOCKED_BY_MASK_DATA；性能与真机合成未验证。**

## 1. 一页执行结论

1. **AI Orb 可保留整体，抽离实时光带。**当前正式 Android Orb 是 `ProcessingWaterOrb`：按钮贴图溶解为 `processing_water_core_v1.webp`，内部渐变与四条曲线实时绘制，外围金/蓝/绿三层开口光带实时绘制。不是完整实时水体，也没有可直接拿出的透明折射水滴系统。六项能力中：WaterCore/GoldFilament/GreenFilament/WaterHalo/MotionController 有现成基础，WaterDroplet 缺独立实现；不能将此计作“完整视觉资产复用率 5/6”。
2. **照片外围可以成为 WaterLight 的一个宿主，不能原封不动成为六模式通用引擎。**已有 CPU 路径采样、16 个亮点、上下扩散进度和透明合成代码。主光晕仍是专用圆角矩形距离场 GLES Shader；绿色只是混色，亮点只是光盘，不等于独立水草丝和透明水滴。几何/时间/材质可抽象，圆角框 Shader 保留为 Perimeter 后端。
3. **焦散优先迁移 upstream 的 `sampleCaustic`＋低分辨率 Canvas 采样。**数学场可结构对应迁到 Kotlin，像素缓冲换 IntArray/Bitmap，现有页面 Compose Canvas 绘制；复用页面帧时钟，不再开第二套 Choreographer 循环。AGSL 仅可选增强。不要把现有三块上行渐变光斑当作已经移植了焦散场。
4. **统一坐标已有基础，缺少显式预处理元数据。**`RecognitionImageTransform` 已统一照片、ROI 轮廓和动画变换；RR09 已在原图空间建立 Path。应补充实际 ROI 像素矩形、模型 resize/pad、输出栅格与像素中心约定，由 MaskAdapter 一次转换为归一化照片像素空间。当前页面重新计算 `.12` 扩框，应消除这处重复权威。
5. **正式识别页面支持低成本渐进集成。**照片、外围 GLES、环境 Canvas、鱼体 Canvas 已分层，Orb 同在 RecognizingScreen；无需重建页面。最低成本是先统一接口和帧数据、保留现有后端，然后接入 CPU 焦散；不是先替换所有渲染器。

**实际影响开发的缺口：**EXIF 镜像未处理；Perimeter Shader margin `.060` 与亮点路径 `.015` 不同；AI Studio 最新默认配置已变，Android 仍标历史来源 SHA；Mask 结果未携带精确 ROI/预处理元数据；透明水滴无现成独立模块；缺当前真实 Mask＋原照片＋运行矩阵配套证据。

## 2. 精确基线与证据口径

| 对象 | 本轮读取的精确源码版本 | 说明 |
|---|---|---|
| Android Main | `15319ba417827a0b33b0fbe0839f44fac761b7f2` | 浅克隆 main，随后 ls-remote 再核对一致 |
| AI Studio Main | `4078addf9dbca4732a676f8a689800261955b987` | 当前源码；不是历史 794e7f |
| water-caustics 默认分支 master | `de856eec62819b7c4176366db73b8fd37a1b1455` | 算法与 LICENSE 实际读取 |
| Android Shader 标记的来源 | `794e7f2698525761bbb2f3e5d775b5dae8d7a25a` | 只是类内 SOURCE_SHA，不代表当前源库最新 |

已读取 Main `AGENTS.md`、`CONTROL_PLANE_REGISTRY.yaml`。本轮只读取与分析，不执行实现、设计变更、测试或发布；未为了只读审计改动控制面或初始化产品工作状态。没有声称 Product Admission 或 Android Runtime PASS。

报告的 `[Axx] / [Wxx] / [Cxx]` 为附录精确源码证据，包含路径、函数、行段、源码版本与文件 SHA-256。建议方案均标为“建议”，不代表已有实现。开放 PR 的实时 HEAD 附后；PR 正文中的历史 SHA 不作为实际 HEAD。未合并 PR 不当作 Main 能力。

## 3. A01｜Orb 资源复用矩阵

A=直接复用；B=保留算法、接口封装；C=局部修改；D=替换或当前缺失时最小新增。工作量为相对量级。

| 模块 | 实际实现 | 决策 | 工作量 | 最小建议与证据 |
|---|---|---|---|---|
| AI Orb 宿主 | Compose Box/Image/Canvas，前后光带夹水核，呼吸与完成收缩 | A | XS | 保留 `ProcessingWaterOrb` 宿主布局与外观，不改尺寸与入场，A01 |
| WaterCore | WebP 静态水核＋实时 radial glint＋2/4条内部 cubic 曲线 | A | XS | 贴图与内部遮罩独立于通用宿主，不宣传为物理水体，A01/A02 |
| GoldFilament | `OrbRibbons` layer 0，5.5s 周期，双层 stroke＋细亮芯 | B | S | 提取曲线点列和 stroke 绘制；默认参数逐值保留，A01 |
| GreenFilament | layer 2，6.5s 周期，绿线较粗且 alpha 减弱 | C | S | 可先复用绿色光带；真正水草弯曲、尖端/宽度变化目前没有，需局部造型适配，A01 |
| WaterDroplet | Orb 中未找到独立透明水滴对象、折射采样或滴形绘制 | D | M | 最小新增透明滴形绘制器；不能把球核或 perimeter 光盘冒充水滴，A01/W01 |
| WaterHalo | 光带较宽低 alpha stroke；非独立通用 halo 类 | B | S | 提取多层 stroke 材质策略；保留前后 alpha，A01 |
| MotionController | Orb 直接消费 elapsedMs、entranceElapsedMs、completion | B | S | 保留调用方时间，增加 FrameInput 包装；Orb 不创建自己的循环，A01/A03 |

水核贴图 `processing_water_core_v1.webp` SHA-256：`daa85a1fc13fac4a7ca562959c2d19172ee0f1e94277c3eeb32ef1f09a1a7b09`。

### 抽离边界与保真

可抽离：OrbRibbons 的颜色/周期配置、曲线采样、前后段选择结果、细芯/柔光 stroke 组合。球体投影、前后遮挡、圆形内部 clip、按钮溶解、整体呼吸/收缩继续由 Orb 宿主拥有。材料可共享，球体几何不要强塞进照片矩形几何。

建议先让新接口输出与旧算法相同的点、alpha 和线宽，再更换调用。默认 seed、相位、周期、笔触、图层顺序均保留；统一抽象不等于统一所有宿主的光带数量和密度。此处是后续保真方法，本轮未运行图像回归。

**能否共用金丝、水草、水滴？**金丝与绿色光带可以共用曲线笔触和时间输入；水滴需新增后才能共用。目前两者并非已有同一套元素系统。

**参考源库的限制：**`demo--Image-light-effect` 当前 `WaterStage.tsx` 是水光场景宿主，调用 fish-catch、submerged mesh、standard 三种路径；检索并实际读取该路径未找到完整品牌 Orb 组件。它能提供外围与焦散算法来源，不能充当完整 Orb 母版源码证明。[W01–W04]

## 4. A02｜外围效果组件化

### 4.1 真实实现与源库差异

Android `RecognitionPerimeterShaderOverlay` 以 AndroidView 嵌入透明 GLSurfaceView；CPU 计算 perimeter 亮点位置，fragment shader 计算圆角框距离、宽/中/细光晕、底部向上 reveal mask，并采样照片来计算 Overlay 对应的正向增亮差值。[A04/A05]

- GLES 输出预乘透明，`GL_ONE / GL_ONE_MINUS_SRC_ALPHA`；最终 `max(outputColor, base)` 禁止变暗。不是任意 CSS Overlay 的完全等价实现。
- 只在 photoBounds 内输出，矩形外透明；Compose 照片有圆角 clip，但 GL fragment 的边界判断为矩形。最终圆角边缘是否有光泄露需要实机核验。
- `.060` 为 Shader 距离场边距，CPU `RecognitionPerimeterShaderMath.MARGIN_RATIO=.015`。这是实际几何不一致，抽象时必须给二者一个有依据的宿主几何来源，不能随意挑一个覆盖。
- 正式调用未传 `referenceMetrics`，默认 identity mapping 且 `isMeasured=false`。当前可用不等于 AI Studio CSS/DPR/backing 映射已测量一致。
- Android 三色已是深孔雀青/水绿/冰白，强度 1.50、blend 0.75；源库最新默认 Effect02 为强度 1.80、margin .015、glow45、颜色 #4d9b91/#d89913/#719fbc、blend .80。不能盲目同步最新源库覆盖已批准 Android 配色。
- 源库 `createFishCatchOverlay` 先调用 getGl，再绘 accents；WebGL 失败只剩 accents，未在这个函数中接上 sampleCaustic CPU 场。`WaterStage` 的“Canvas 2D”注释不能证明实际强制 Canvas 2D。[W01/W02]
- Android Shader 没有源库完整 bottom caustic field/lightSpots 代码；现有上行效果位于 RecognitionAmbientField，是三个不规则渐变 Path。[A06/W03]

### 4.2 能力映射

| 现有效果 | WaterLight 目标 | 决策 | 限制 |
|---|---|---|---|
| 路径采样/16亮点 | 通用 PathSampler＋粒子运动 | B | 路径固定圆角矩形，需抽参数；亮点不是滴形 |
| 圆角框多层光晕 | SoftGlowLayer 的 Perimeter 后端 | B | SDF 与矩形强耦合，不应用于 Mask 路径 |
| 绿色通道混色 | GreenFilamentLayer | C | 不是独立水草几何；优先复用 Orb 绿色线笔触 |
| 金色光丝 | GoldFilamentRenderer | B | 优先抽 Orb 与现有鱼体/RR09 笔触，不从已改青色的 perimeter 伪造现成金丝 |
| sparkle disks | WaterDropletLayer | D | 可复用运动/seed；透明材质与滴形需新增 |
| 上行渐变光斑 | WaterCausticsLayer | C | 保留现有时序包络；新增原算法后端，不把光斑当算法移植完成 |
| elapsed/progress | MotionController | B | 消费现有自适应阶段和 reference time，不改时序 |

定性复用：**外围宿主本身复用程度高，六模式通用几何程度低，材料与帧接口复用潜力高。**没有可信依据给出“80%”等百分比。

### 4.3 六宿主可行性

| 模式 | 现有可用基础 | 最小适配 | 当前状态 |
|---|---|---|---|
| Orb | OrbRibbons 前后投影 | GeometryAdapter 返回 front/back 点列 | 有专用实现，未通用化 |
| Perimeter | ShaderMath 路径＋GLES 光晕 | 统一边距/坐标单位/MaterialProfile | 已有专用实现 |
| Path | AmbientField 缓存贝塞尔/PathMeasure；RR09 transferCurve | 动态端点、采样函数，跨容器转 root px | 有构件，无 Orb→鱼体通用路径宿主 |
| Contour | extractContour＋RecognitionFishFocus | 原图 Path 缓存、共用笔触 | 已有真实 Mask 驱动代码 |
| Residue | FishFocus gold halo＋完成态包络 | 由既有阶段传稳定余辉强度，禁止新时序 | 部分基础；未发现独立 ResidueRenderer |
| Handoff | RevealGeometry＋OpticalOverlay 的转译曲线/亮点/照片矩阵 | 复用既有 RR09，替换材质入口 | 已有专用实现 |

无需重复写曲线采样。材质/采样函数共用；粒子连续传播可用同一 session 下稳定 ID、seed、归一化弧长状态。不能把同一个可变粒子列表同时交给多个线程/宿主修改。宿主切换先在同一 root px 空间匹配锚点与切线，再改变 geometry；Contour 保留多个闭合子路径，禁止把不连通轮廓串成直线。

## 5. A03｜焦散算法与原生移植

### 5.1 已实际读到的算法

upstream `caustic-field.ts` 的 rotate→octave→field→sampleCaustic 全链已读。[C01]

- 每个 octave 两轮 sin/cos domain warp，计算两个方向正弦乘积 ridge，再以 `0.165/(abs(ridge)*5.6+.22)` 形成窄亮脊。
- 三个不同频率与旋转的 octave 以 max/加权合并，clamp 后 pow(1.45)；不是外部噪声贴图，也不是流体求解器。
- sampleCaustic 以高度归一化坐标、scale 和 time*.36 采样，经 veins/haze 平滑阈值生成 RGB 与 alpha，颜色来自 highlight/tint 插值。
- 原计算没有 DOM/WebGL 依赖；renderer 有 Canvas/Document/ImageData/performance；mount 层有 DOM/ResizeObserver/RAF/WebGL 优先逻辑。
- `canvas2d.ts` 限宽 220，按高宽比计算高度，每33ms最多计算一次，采样 y 为 `bh-y-.5`，保留 WebGL 底左方向。**220 限的是宽，不是最长边或总像素数**；竖图高度可能更大。
- 算法文件在当前 AI Studio 和 upstream 的 SHA-256 完全相同：`a8aa60ac69692246ff15aa36a38763c8097295c1e37e6ce4122a45825f5c5578`。

### 5.2 两方案比较（工程推断，未测帧率）

| 指标 | Canvas/Bitmap 基础 | AGSL 可选增强 |
|---|---|---|
| 迁移难度 | 低至中；数学结构直接对应，数组与绘制 API 改写 | 中；复用 field 数学，改 main/uniform/坐标/预乘输出 |
| 设备兼容 | 适合项目 minSdk24；具体 blend API 分版本适配 | RuntimeShader API33+，需运行时判断 |
| 实时性能 | CPU逐像素三角函数成本高；低分辨率、33ms节流、后台计算 | GPU逐像素，通常更适合连续场；全分辨率/overdraw仍有成本 |
| 视觉还原 | 数学一致，低分辨率插值可能丢细线 | 可保留更细纹理；精度/色彩/采样仍需比对 |
| 内存 | 两张ARGB缓存＋IntArray约12wh字节，不含其他临时对象 | 无CPU纹理循环；仍有GPU render target成本，不能称零内存 |
| 维护 | 易诊断；避免每像素 Tuple/Pair 和每帧分配 | 增加第二后端维护与设备验证 |

API33 要求见 Android 官方：https://developer.android.com/develop/ui/views/graphics/agsl 。本项目 `minSdk=24/compileSdk=35` [A15]，因此不能只交付 AGSL。

### 5.3 实际迁移映射

| Web实现 | Android目标 | 改写范围 |
|---|---|---|
| rotate/octave/field/smoothstep | Kotlin CausticFieldMath | 数学表达式/常量可逐段保留；消除数组返回分配，Double基准后再评估Float |
| sampleCaustic 返回RGBA | 填复用 IntArray | RGB/alpha clamp、四舍五入与Uint8ClampedArray规则需对应；保持y翻转 |
| Uint8ClampedArray/ImageData | IntArray＋ARGB_8888 Bitmap.setPixels | 数据结构替换；注意straight alpha→Bitmap预乘过程，避免重复预乘 |
| putImageData/drawImage | Canvas.drawBitmap 或 Compose drawImage | API改写，FILTER_BITMAP_FLAG 对应 smoothing；scale到照片矩形 |
| performance.now/33ms | 传入同一 monotonic frame time＋lastComputedAt | 不新建独立时钟；不在draw阶段执行整个像素计算 |
| RAF | 现有withFrameNanos（底层帧调度） | 退出/后台不消费；自定义View场景才使用单一Choreographer适配 |
| CSS mix-blend-mode | 独立 Canvas 合成层＋Paint/BlendMode | 在照片同一合成目标上定义；旧API适配PorterDuff或已验证等价策略 |
| ResizeObserver/DOM bounds | onSizeChanged/现有 transform | 不引入WebView，不初始化WebGL |
| dispose | 生命周期取消job、释放缓存 | 后台停止计算；session更换丢弃旧帧 |

推荐初始保留 upstream 宽220与33ms基准；另增总像素上限作为可配置性能策略，不能默默改变算法后声称逐像素一致。缓存采用前后双缓冲，后台任务完成后原子提交，同一Bitmap不能一边setPixels一边绘制。以 generation/session＋geometryVersion 丢弃过期结果。下到上的空间遮罩作为独立 envelope，在现有 opticalReferenceElapsedMs 上求值，避免再开时序。

MIT：保留 upstream 完整 LICENSE 原文及 `Copyright (c) 2026 water-caustics contributors`；移植文件头标来源仓库、精确SHA与改动说明，随 App 第三方声明/分发材料一并交付。不要仅写“MIT”两个字。[C03]

## 6. A04｜正式 Android 渲染管线

正式入口：`YujianApp.kt` 在识别页面注入 `subjectPreviewEngine.generate(selected.bitmap, box)`；应用级 remember 引擎在 app dispose 时 close。[A16]不是仅存在未接线的 demo。

| 检查项 | 读到的事实 | 集成影响 |
|---|---|---|
| UI框架 | RecognizingScreen Compose；PhotoStage 原生 Compose Canvas＋AndroidView GL | 原页面插入适配器即可 |
| 照片组件 | PhotoStage 的 Canvas.drawImage，remember(bitmap).asImageBitmap | 本页面不是RecognitionSourcePhoto composable在绘制，后者只是已有通用组件 |
| 解码 | ImageStore bounds读取＋一次像素解码，降采样到最大维不超过2048；Processing重用SelectedImage.bitmap | 未发现Processing每帧重解码；Mask PNG另解码一次，不能说全App永不重复解码 |
| 存储/像素 | persistNormalized保存JPEG92并计算其SHA，但SelectedImage.bitmap保留压缩前像素 | 内存Bitmap与持久化JPEG字节不同；恢复后图像微差需明确，SHA当前是文件身份 |
| 显示几何 | RecognitionPhotoFlowGeometry生成并插值RecognitionImageTransform | WaterLight消费同一对象/版本；A19 |
| 圆角 | PhotoStage随entryProgress递增16dp圆角clip | 外围/轮廓应消费同一clip，不能各算一个 |
| 图层顺序 | photo→GLES perimeter→AmbientField→FishFocus | 保留现有顺序；GL跨surface叠放需实机验证 |
| 微震 | 照片＋光效共享graphicsLayer变换 | 新layer放入同组；不要再单独应用一次微震 |
| Orb | 位于同一RecognizingScreen的下方区域，独立尺寸 | 跨Orb/鱼体路径需root坐标适配，不能直接用两容器local坐标 |
| 计时 | 主视觉withFrameNanos；入场900ms独立loop；photo entry独立loop；完成Animatable | 有多条受控动画，不等于多套粒子模拟；统一FrameInput不改阶段时间 |
| GLES循环 | RENDERMODE_WHEN_DIRTY，submit→requestRender，无自身动画时钟 | 直接沿用帧提交方式 |
| 暂停释放 | GL显式ON_RESUME/PAUSE/STOP；dispose release，context不保留；subjectBitmap dispose recycle | 主Compose帧循环未看到显式Lifecycle暂停条件；后续统一生命周期消费需补齐，不宣称已验证暂停安全 |

**当前接入成本判断：低至中。**Canvas曲线和Mask几何复用成本低；要让所有效果完全迁离GLES且视觉不变是另一项更大任务，不应作为第一步。当前新焦散不得初始化WebGL/WebView；已有原生GLES perimeter不等于WebGL，无需本轮审计提出立即删除。

## 7. A05｜真实 SlimSAM 与统一坐标

### 7.1 模型资产与真实接口

对仓库 ONNX 二进制的 protobuf GraphProto input/output 元数据做只读解析，不依赖文件名或注释；没有执行模型推理。

| 模型 | 字节数 | SHA-256 |
|---|---:|---|
| vision_encoder_quantized.onnx | 8,882,165 | cce23c7b2e5d4f330932738fb67ba518e04b0d99ccdd1cccd22a7da4e01f2971 |
| prompt_encoder_mask_decoder_quantized.onnx | 4,903,810 | cb90b279f549d2cab7fd6e20c38522438c65d84bdcca3d2a764cff7d857fdce2 |

Encoder：FLOAT pixel_values `[batch,3,1024,1024]`→FLOAT image_embeddings 与 image_positional_embeddings 均 `[batch,256,64,64]`。Decoder：FLOAT input_points `[batch,point_batch,nb_points,2]`、INT64 input_labels `[batch,point_batch,nb_points]`、两个embedding→FLOAT iou_scores `[batch,point_batch,3]`、pred_masks `[batch,point_batch,3,256,256]`。调用代码固定batch=point_batch=1，与资产形状一致。[A07]

真实引擎为 onnxruntime-android 1.29.0 [A15]。Encoder按Bitmap generationId/宽高缓存一次embedding；不是按session/SHA缓存。两个session懒加载，intra-op线程2。原始输入张量约12MiB、两份embedding约8MiB，不含ORT模型/算子内存、像素和Mask；这是静态尺寸计算，不是峰值实测。

### 7.2 实际数据链路

1. ImageStore读取EXIF rotationDegrees，降采样、旋转；相机可按previewAspectRatio中心裁剪，选图不做该裁剪。**镜像未处理。**
2. Detector以缩放后内容贴左上、右/下padding114填满输入；不是居中letterbox。解码BBox除scale，方向重试另有回映逻辑。[A08]
3. SlimSAM处理整张SelectedImage.bitmap，不是先把ROI送Encoder。scale=1024/max(W,H)，targetW/H round；归一化ImageNet，右/下padding为归一化后的0。[A07]
4. BBox用于生成前景点（竖鱼还加上方负点）；**当前不是直接传SAM box-corner labels2/3。**不可将此描述为已有标准BBox prompt编码。
5. Decoder三个候选以IoU*.7＋正logit面积*.3排序；IoU记录但不当作校准可信概率。
6. 源图ROI为detectorBox.expand(.12)经cropBoxPixels取整。ROI每个像素回采256mask，当前使用 `origX*scale/4`、`origY*scale/4` 双线性再sigmoid。
7. 4邻域最大连通域按confidence>=.5保留；质量门检查后，ROI原图RGB＋软alpha输出PNG。
8. 页面重建相同ROI并检查尺寸；alpha>=36提取轮廓、stride2、8邻域清理小岛、边界栅格追踪、去共线/贝塞尔圆滑。这个算法不是OpenCV，也不是标准插值Marching Squares。[A09]
9. FishFocus将ROI normalized坐标加回原图normalized，再经RecognitionImageTransform；RR09提前换回原图px Path并应用photoMatrix。[A10/A11]

### 7.3 必须补齐的坐标合同（建议接口，当前不存在）

PhotoSpace 定义为**预处理完成后的 canonical Bitmap 像素空间**，左上原点，W/H明确。当前相机已经裁过且可能降采样，不能把它叫未裁切传感器全分辨率原图；如需追溯，另存raw→canonical变换。

建议 `MaskGeometryMetadata` 最小字段：sessionId、attemptId、imageId、photoSha256、canonicalWidth/Height、roiPx(left/top/right/bottom)、modelInputSize、resizedWidth/Height、padLeft/Top/Right/Bottom、maskGridSize、modelToPhotoTransform及pixelCenterConvention、threshold/contourVersion、modelSHA。

```text
ROI contour(u,v) → PhotoPoint=(roiLeft+u*roiWidth, roiTop+v*roiHeight)
PhotoPoint → LocalPx=PhotoDisplayMatrix × PhotoPoint
LocalPx → RootPx=HostToRootMatrix × LocalPx
```

`PhotoDisplayMatrix=[s,0,tx;0,s,ty;0,0,1]`沿用现有RecognitionImageTransform；照片微震共用外层graphicsLayer，所以不在轮廓内重复叠加。RR09继续使用现有sourceMatrix/root转换。模型输入反变换在MaskAdapter中完成一次；绝不拿256栅格直接贴屏幕。

当前scale以最长边理论比例，实际targetW/H是整数round，因此`scaleX=targetW/W`、`scaleY=targetH/H`可能略不同。当前 `orig*scale/4` 还需核对 resize的半像素中心约定与256网格对齐；不能宣称数学链路已有像素级实证。未来显式保存实际resize/pad与栅格变换，不在绘制端猜测。

### 7.4 十二项坐标风险核对

| 风险 | 源码结论 | 建议/证据 |
|---|---|---|
| EXIF统一 | 旋转已做；镜像缺失 | 复用已有EvidenceBitmap八方向矩阵算法，迁入正式输入另案实现；A12/A13 |
| Detector letterbox | 左上放置，右下padding | 逆变换padLeft/top=0，不引入居中减padding；A08 |
| padding反移 | Detector decode除scale符合左上padding；SAM同右下 | 保存实际参数，验证旋转重试；A07/A08 |
| SAM独立resize | 1024最长边、整数round | metadata记录实际targetW/H；A07 |
| Mask回原图 | ROI逐像素回采已有 | 半像素/rounding待证；A07 |
| Crop偏移 | 页面重建ROI，轮廓映射加回 | FishSubjectResult补roiPx，移除重算；A09/A14 |
| Bitmap/Mask尺寸 | 运行码有roi dimensions与maskSize检查 | 通过尺寸不证明语义边界准确；A09 |
| Fit/Crop | transform提供min/max scale与translation；Processing走FIT | 外围shader当前按visible rect采整图，在CROP时有采样错位风险；A04/A17 |
| dp/px | geometry px，边距dp显式density转换 | 新接口全px，仅笔触profile定义dp并转换一次；A09/A17 |
| 缩放同步 | 同一transform与父graphicsLayer | 静态结构支持，真机证据未执行；A09/A17 |
| 旧坐标帧 | sceneKey＋LaunchedEffect取消、ensureActive；RR09快照 | 新CPU纹理需geometryVersion，避免异步提交过期帧；A03/A09 |
| 跨session覆盖 | geometryTrust核对image/session/attempt/SHA；缓存embedding只看generationId/尺寸 | 保留门控，embedding建议绑定身份，快速切图runtime待测；A07/A18 |

另外：`FishSubjectResult`无roiLeft/top，且expandRatio可配置；页面写死.12。这是可维护性与未来错位风险，当前默认值一致时不能直接断言已经错位。[A07/A09/A14]

### 7.5 Mask→Contour 路线决策

| 选项 | 当前适配成本 | 判断 |
|---|---|---|
| 已有栅格边界追踪＋贝塞尔 | 最小；调用已接线 | 首选复用，保留多闭合路径，核验细鳍/尾部与alpha阈值 |
| Marching Squares | 中；补插值与鞍点规则 | 如真实证据显示栅格误差再做局部替换，无需先实现 |
| OpenCV findContours | 增加依赖、打包与坐标适配 | 本任务暂缓，无理由仅为了轮廓提取引入大型库 |

Mask质量阈值.5与轮廓alpha36/255不同：LCC保留区内软alpha可能低于.5，轮廓可能沿更外缘。不是未经验证就统一阈值；需记录两种语义与真实边缘偏差。stride2与贝塞尔平滑同样可能改变细鳍/尾尖，不能凭注释宣称已保真。另外 FishFocus 在渐进分支仅对PathMeasure当前子路径getSegment，未遍历nextContour；多闭合子路径时可能在进度到1后突然出现其余轮廓，公共采样器需保留子路径累计弧长语义。

### 7.6 真实坐标证据状态

**BLOCKED_BY_MASK_DATA。**仓库有真实鱼获照片和模型资产，但本轮没有取得与某张照片绑定的真实输出Mask、推理预处理日志、显示矩阵与缩放帧。没有执行Android推理；不拿fixture轮廓或人工鱼形代替。

| 所需证据 | 当前可证明 | 本轮状态 |
|---|---|---|
| 原照片W/H、EXIF、SHA | 有实际照片文件；没有配套本次Mask运行关联 | 配套记录缺失 |
| 模型输入/输出尺寸 | ONNX元数据已解析 | 静态已验证 |
| resize/padding | 实现已读，参数公式明确 | 实际单样本数值未取得 |
| ROI与mask输出 | 运行码和尺寸门控存在 | 真实输出未取得 |
| PhotoDisplayMatrix | 算法与动画共用点已读 | 指定运行帧数值未取得 |
| 鱼头/鱼尾/鱼鳍/身体边缘 | 需要原图＋真实Mask叠加 | NOT_RUN |
| 缩放同步/快速切图 | 需要运行帧和身份日志 | NOT_RUN |

下一步在既有识别页导出真实诊断包即可补证，无需新Demo或要求用户补资源。必须由实际推理生成mask与metadata，分别记录入场、稳定、RR09中点的矩阵及像素偏差；本轮不跨入运行开发。

## 8. 推荐架构：共享算法与接口，保留宿主

| 目标模块 | 复用来源 | 最小新增内容 |
|---|---|---|
| UnifiedWaterLightEngine | 现有页面分层与session | 轻量调度门面，不做新页面/独立时钟 |
| MaterialProfile | OrbRibbons、FishFocus、Reveal、perimeter颜色/笔触 | 不可变材质参数；各宿主默认profile保持原值 |
| MotionController | RecognitionVisualStateController＋现有elapsed/reference/Animatable | FrameInput适配，阶段仍由既有controller管理 |
| GoldFilamentRenderer | OrbRibbons＋FishFocus＋RR09笔触 | 公共path stroke入口；球投影留Orb |
| GreenFilamentRenderer | Orb绿色光带 | 同一曲线笔触，水草造型参数局部新增 |
| WaterDropletRenderer | perimeter亮点运动 | 真正滴形/透明材质最小新增；不声称已有折射 |
| WaterCausticsRenderer | upstream sampleCaustic/canvas2d | Kotlin数学采样＋Bitmap缓冲；AGSL后置 |
| GeometryAdapter | RecognitionImageTransform、ShaderMath、RevealGeometry | Path采样、host→root、clip与geometryVersion |
| MaskAdapter | SlimSamSegmenter、extractContour、sourceContourPath | 显式ROI/模型变换metadata，统一PhotoSpace输出 |
| HostRenderer | Orb/Perimeter/FishFocus/Reveal各专用实现 | 六mode选择与参数适配；Residue/Path补最小连接 |

建议接口概念（不代表代码已新增）：

- `WaterLightFrame(sessionIdentity, elapsedMs, opticalReferenceMs, phase, completion, reducedMotion, geometryVersion)`。
- `HostGeometry(photoDisplayTransform, hostToRoot, clip, curveSampler, contourSubpaths, boundsPx)`。
- `MaterialProfile(coreColor, haloColor, widthsDp, opacity, blendPolicy, seed)`。
- `MaskGeometry(metadata, pathsInPhotoPx)`。

Renderer仅消费frame与geometry，不能反向推进phase或异步修改旧session。时间、材质可共用；不同宿主几何和后端允许独立。

## 9. 最短开发路径与停止边界

| 类别 | 工作 | 相对量 |
|---|---|---|
| 直接复用 | ProcessingWaterOrb宿主/水核、现有PhotoStage、FishFocus、Reveal、轮廓追踪 | XS/S |
| 需要适配 | FrameInput/MaterialProfile/GeometryAdapter、perimeter路径锚点与单位统一 | S/M |
| 需要适配 | Mask结果携带精确ROI与预处理metadata，图像八方向EXIF | S/M |
| 需要新增 | upstream焦散Kotlin/Bitmap后端；独立滴形材质 | M |
| 可以暂缓 | AGSL、OpenCV、新页面、新模型训练、完整球内物理水体 | 不进入首轮 |

推荐实施顺序：

1. 先让旧Orb、perimeter、轮廓/RR09读统一FrameInput和几何适配，不改默认外观及冻结时间轴。
2. 返回Mask metadata，消除页面ROI重算，修复完整EXIF；用既有真实识别页完成一张真实照片的头/尾/鳍/缩放配套证据。
3. 迁移sampleCaustic到低分辨率Bitmap，接入同一照片Canvas与已有时序包络，不改P0/P2/P3/Handoff/RR09。
4. 抽离金/绿曲线笔触，接入六宿主适配；新增滴形只在视觉批准范围内推进。
5. 只有基础效果和成本可测后，再评估AGSL提升。

**本轮到此停止，不自动进入实现。**这份报告支持选择复用边界与下一轮任务；不构成视觉验收、运行通过或合并声明。


## 附录 A｜精确源码证据索引

所有路径相对对应仓库根目录。每项链接固定commit；行段用于定位，文件SHA-256绑定实际读取字节。

### A01｜ProcessingWaterOrb / OrbRibbons

- 路径：`app/src/main/java/com/yujian/ai/ui/recognition/ProcessingWaterOrb.kt`
- [固定版本源码](https://github.com/pan277942135/Yujian_App/blob/15319ba417827a0b33b0fbe0839f44fac761b7f2/app/src/main/java/com/yujian/ai/ui/recognition/ProcessingWaterOrb.kt#L29)；commit：`15319ba417827a0b33b0fbe0839f44fac761b7f2`；关键行：`29-124`。
- 文件 SHA-256：`b18b25a25682d375b797970b3e503fea23af121441e7045afe874fc81f63c757`。

### A02｜静态水核资源

- 路径：`app/src/main/res/drawable-nodpi/processing_water_core_v1.webp`
- [固定版本源码](https://github.com/pan277942135/Yujian_App/blob/15319ba417827a0b33b0fbe0839f44fac761b7f2/app/src/main/res/drawable-nodpi/processing_water_core_v1.webp)；commit：`15319ba417827a0b33b0fbe0839f44fac761b7f2`；关键行：`binary`。
- 文件 SHA-256：`daa85a1fc13fac4a7ca562959c2d19172ee0f1e94277c3eeb32ef1f09a1a7b09`。

### A03｜sceneKey / visual loop / entrance clock

- 路径：`app/src/main/java/com/yujian/ai/ui/screens/RecognizingScreen.kt`
- [固定版本源码](https://github.com/pan277942135/Yujian_App/blob/15319ba417827a0b33b0fbe0839f44fac761b7f2/app/src/main/java/com/yujian/ai/ui/screens/RecognizingScreen.kt#L190)；commit：`15319ba417827a0b33b0fbe0839f44fac761b7f2`；关键行：`190-240;560-640;775-795`。
- 文件 SHA-256：`78ce4b1d385f629d035185671ee297d408d9f04378b9cf4b5924489ea8c74b2c`。

### A04｜RecognitionPerimeterShaderOverlay / PerimeterGlSurfaceView / fragment shader

- 路径：`app/src/main/java/com/yujian/ai/ui/recognition/RecognitionPerimeterShaderOverlay.kt`
- [固定版本源码](https://github.com/pan277942135/Yujian_App/blob/15319ba417827a0b33b0fbe0839f44fac761b7f2/app/src/main/java/com/yujian/ai/ui/recognition/RecognitionPerimeterShaderOverlay.kt#L39)；commit：`15319ba417827a0b33b0fbe0839f44fac761b7f2`；关键行：`39-155;179-250;481-515;560-725`。
- 文件 SHA-256：`af390fc89e957e5c5b2bd0ba54534b4bb1ca26281c33f4ba3e64ddb1ee2f406c`。

### A05｜coordinateScale / generatePerimeterPath / sparkles

- 路径：`app/src/main/java/com/yujian/ai/ui/recognition/RecognitionPerimeterShaderMath.kt`
- [固定版本源码](https://github.com/pan277942135/Yujian_App/blob/15319ba417827a0b33b0fbe0839f44fac761b7f2/app/src/main/java/com/yujian/ai/ui/recognition/RecognitionPerimeterShaderMath.kt#L58)；commit：`15319ba417827a0b33b0fbe0839f44fac761b7f2`；关键行：`58-257`。
- 文件 SHA-256：`655654d11fa76aef03c6560aaf859fe53ffc3ff6f341328599748ce7372fe205`。

### A06｜RecognitionAmbientField / drawParticles / buildOpticalPathCache / drawUpwardCausticSurge

- 路径：`app/src/main/java/com/yujian/ai/ui/recognition/RecognitionAmbientField.kt`
- [固定版本源码](https://github.com/pan277942135/Yujian_App/blob/15319ba417827a0b33b0fbe0839f44fac761b7f2/app/src/main/java/com/yujian/ai/ui/recognition/RecognitionAmbientField.kt#L273)；commit：`15319ba417827a0b33b0fbe0839f44fac761b7f2`；关键行：`273-465;533-710;730-755;891-1012`。
- 文件 SHA-256：`e5b4bed78178ff1aa27a7606df31700396e9ff6c65b9793060569ddef1dd7403`。

### A07｜segment / getOrComputeImageEmbedding / generateBoxPoints / runMaskDecoder / LCC

- 路径：`app/src/main/java/com/yujian/ai/ai/subject/SlimSamSegmenter.kt`
- [固定版本源码](https://github.com/pan277942135/Yujian_App/blob/15319ba417827a0b33b0fbe0839f44fac761b7f2/app/src/main/java/com/yujian/ai/ai/subject/SlimSamSegmenter.kt#L41)；commit：`15319ba417827a0b33b0fbe0839f44fac761b7f2`；关键行：`41-203;217-348;360-485;510-588`。
- 文件 SHA-256：`3c4cafb3e0df55c003bef79e750d80ec46dd293ec8503e2f5f4ec60cbbc49014`。

### A08｜prepareInput / decode

- 路径：`app/src/main/java/com/yujian/ai/ai/FishDetectorEngine.kt`
- [固定版本源码](https://github.com/pan277942135/Yujian_App/blob/15319ba417827a0b33b0fbe0839f44fac761b7f2/app/src/main/java/com/yujian/ai/ai/FishDetectorEngine.kt#L270)；commit：`15319ba417827a0b33b0fbe0839f44fac761b7f2`；关键行：`270-293;437-465`。
- 文件 SHA-256：`ba0d264d985f15065bf63c15dea979b45d570715511bd750fac25e652fc32f65`。

### A09｜Mask acceptance / PhotoStage / extractContour / traceGridContours

- 路径：`app/src/main/java/com/yujian/ai/ui/screens/RecognizingScreen.kt`
- [固定版本源码](https://github.com/pan277942135/Yujian_App/blob/15319ba417827a0b33b0fbe0839f44fac761b7f2/app/src/main/java/com/yujian/ai/ui/screens/RecognizingScreen.kt#L242)；commit：`15319ba417827a0b33b0fbe0839f44fac761b7f2`；关键行：`242-401;953-1145;1171-1349`。
- 文件 SHA-256：`78ce4b1d385f629d035185671ee297d408d9f04378b9cf4b5924489ea8c74b2c`。

### A10｜buildRecognitionContourPath / RecognitionFishFocus

- 路径：`app/src/main/java/com/yujian/ai/ui/recognition/RecognitionFishFocus.kt`
- [固定版本源码](https://github.com/pan277942135/Yujian_App/blob/15319ba417827a0b33b0fbe0839f44fac761b7f2/app/src/main/java/com/yujian/ai/ui/recognition/RecognitionFishFocus.kt#L107)；commit：`15319ba417827a0b33b0fbe0839f44fac761b7f2`；关键行：`107-137;155-255`。
- 文件 SHA-256：`a3c087e24946710493ca6a6b23f5d4315e853a90e40278d7e8853afc7eea2374`。

### A11｜sourceMatrix / sourceContourPath / transferCurve

- 路径：`app/src/main/java/com/yujian/ai/ui/recognition/reveal/RecognitionRevealGeometry.kt`
- [固定版本源码](https://github.com/pan277942135/Yujian_App/blob/15319ba417827a0b33b0fbe0839f44fac761b7f2/app/src/main/java/com/yujian/ai/ui/recognition/reveal/RecognitionRevealGeometry.kt#L280)；commit：`15319ba417827a0b33b0fbe0839f44fac761b7f2`；关键行：`280-330`。
- 文件 SHA-256：`163006a9bb8787a0a8c84e27766183d49a159cce4730094ae5afdace9a7ffaf4`。

### A12｜normalizeLocalFile / persistNormalized / normalizeCameraFileWithDetails

- 路径：`app/src/main/java/com/yujian/ai/media/RecognitionImageStore.kt`
- [固定版本源码](https://github.com/pan277942135/Yujian_App/blob/15319ba417827a0b33b0fbe0839f44fac761b7f2/app/src/main/java/com/yujian/ai/media/RecognitionImageStore.kt#L65)；commit：`15319ba417827a0b33b0fbe0839f44fac761b7f2`；关键行：`65-166`。
- 文件 SHA-256：`96295a5c9cd18c367f3052d4272209d86c22dbcab73131b1c84857c388f3cc98`。

### A13｜EXIF direction helpers

- 路径：`app/src/main/java/com/yujian/ai/ui/components/EvidenceBitmap.kt`
- [固定版本源码](https://github.com/pan277942135/Yujian_App/blob/15319ba417827a0b33b0fbe0839f44fac761b7f2/app/src/main/java/com/yujian/ai/ui/components/EvidenceBitmap.kt#L20)；commit：`15319ba417827a0b33b0fbe0839f44fac761b7f2`；关键行：`20-60`。
- 文件 SHA-256：`7cc80617783336304549f530636ff0005e656f920b5b1568c33b7b7f1b506e20`。

### A14｜FishSubjectResult

- 路径：`app/src/main/java/com/yujian/ai/ai/subject/FishSubjectResult.kt`
- [固定版本源码](https://github.com/pan277942135/Yujian_App/blob/15319ba417827a0b33b0fbe0839f44fac761b7f2/app/src/main/java/com/yujian/ai/ai/subject/FishSubjectResult.kt#L7)；commit：`15319ba417827a0b33b0fbe0839f44fac761b7f2`；关键行：`7-26`。
- 文件 SHA-256：`59fe268f2645d3de36b207be83d529dc2ea4a85a55d0208a893678b7b9968e4e`。

### A15｜android/defaultConfig / dependencies

- 路径：`app/build.gradle.kts`
- [固定版本源码](https://github.com/pan277942135/Yujian_App/blob/15319ba417827a0b33b0fbe0839f44fac761b7f2/app/build.gradle.kts#L48)；commit：`15319ba417827a0b33b0fbe0839f44fac761b7f2`；关键行：`48-58;115-132`。
- 文件 SHA-256：`5c429e5d6db02a14f7b190ba4f9270445462a3ceac9c1d9fd243b339765ae145`。

### A16｜subjectPreviewEngine / production recognition injection / dispose

- 路径：`app/src/main/java/com/yujian/ai/YujianApp.kt`
- [固定版本源码](https://github.com/pan277942135/Yujian_App/blob/15319ba417827a0b33b0fbe0839f44fac761b7f2/app/src/main/java/com/yujian/ai/YujianApp.kt#L137)；commit：`15319ba417827a0b33b0fbe0839f44fac761b7f2`；关键行：`137;313;675-702`。
- 文件 SHA-256：`026f32ac4bf8166dbdd96db9377220c0ee374d667e7e9466b1f37c29afb47f87`。

### A17｜RecognitionImageTransform / calculateRecognitionImageTransform

- 路径：`app/src/main/java/com/yujian/ai/ui/identify/RecognitionImageTransform.kt`
- [固定版本源码](https://github.com/pan277942135/Yujian_App/blob/15319ba417827a0b33b0fbe0839f44fac761b7f2/app/src/main/java/com/yujian/ai/ui/identify/RecognitionImageTransform.kt#L33)；commit：`15319ba417827a0b33b0fbe0839f44fac761b7f2`；关键行：`33-154`。
- 文件 SHA-256：`df90a82d220d9fdebfc40268b58d3be373ea6ed30b4fa6cc6c071ad6e28559e6`。

### A18｜geometryTrust

- 路径：`app/src/main/java/com/yujian/ai/ui/recognition/reveal/RecognitionRevealPolicy.kt`
- [固定版本源码](https://github.com/pan277942135/Yujian_App/blob/15319ba417827a0b33b0fbe0839f44fac761b7f2/app/src/main/java/com/yujian/ai/ui/recognition/reveal/RecognitionRevealPolicy.kt#L30)；commit：`15319ba417827a0b33b0fbe0839f44fac761b7f2`；关键行：`30-67`。
- 文件 SHA-256：`9be54d846f130b5d952f503b4a1ab33aef4b526dd5dbff3a857a9fe03cd80361`。

### A19｜planRecognitionPhotoFlow / interpolateRecognitionPhotoTransform

- 路径：`app/src/main/java/com/yujian/ai/ui/recognition/RecognitionPhotoFlowGeometry.kt`
- [固定版本源码](https://github.com/pan277942135/Yujian_App/blob/15319ba417827a0b33b0fbe0839f44fac761b7f2/app/src/main/java/com/yujian/ai/ui/recognition/RecognitionPhotoFlowGeometry.kt#L20)；commit：`15319ba417827a0b33b0fbe0839f44fac761b7f2`；关键行：`20-155`。
- 文件 SHA-256：`cf4f4fea1b7a745949d5cc09266fc0e55411d5705d7f2f9c6043b850c7ae45d3`。

### A20｜RecognitionRevealOpticalOverlay

- 路径：`app/src/main/java/com/yujian/ai/ui/recognition/reveal/RecognitionRevealOpticalRenderer.kt`
- [固定版本源码](https://github.com/pan277942135/Yujian_App/blob/15319ba417827a0b33b0fbe0839f44fac761b7f2/app/src/main/java/com/yujian/ai/ui/recognition/reveal/RecognitionRevealOpticalRenderer.kt#L20)；commit：`15319ba417827a0b33b0fbe0839f44fac761b7f2`；关键行：`20-175`。
- 文件 SHA-256：`5d91db01ffacc7697d1f8bee0c471c98c857f675239b6d35fd5055d749721f7c`。

### A21｜SelectedImage

- 路径：`app/src/main/java/com/yujian/ai/model/Models.kt`
- [固定版本源码](https://github.com/pan277942135/Yujian_App/blob/15319ba417827a0b33b0fbe0839f44fac761b7f2/app/src/main/java/com/yujian/ai/model/Models.kt#L20)；commit：`15319ba417827a0b33b0fbe0839f44fac761b7f2`；关键行：`20-33`。
- 文件 SHA-256：`568dc6821243a1576c4e105b643cba2429f83f35f2c7e671858c0d118805d1e7`。

### A22｜MASK_THRESHOLD / assess

- 路径：`app/src/main/java/com/yujian/ai/ai/subject/FishSubjectQualityGate.kt`
- [固定版本源码](https://github.com/pan277942135/Yujian_App/blob/15319ba417827a0b33b0fbe0839f44fac761b7f2/app/src/main/java/com/yujian/ai/ai/subject/FishSubjectQualityGate.kt#L1)；commit：`15319ba417827a0b33b0fbe0839f44fac761b7f2`；关键行：`1-70`。
- 文件 SHA-256：`0dae9d72f08bf3585fd4ad553194153cf24d54c52e64cc8eeb88d3715c8d8043`。

### W01｜scene mounting

- 路径：`src/components/WaterStage.tsx`
- [固定版本源码](https://github.com/pan277942135/demo--Image-light-effect/blob/4078addf9dbca4732a676f8a689800261955b987/src/components/WaterStage.tsx#L110)；commit：`4078addf9dbca4732a676f8a689800261955b987`；关键行：`110-194`。
- 文件 SHA-256：`6cb2a6b61b26b00d1a0a68a5b4410405885cdaae0f96955021b166f899ca16a8`。

### W02｜FROZEN_EFFECT configs / createFishCatchOverlay / renderAccents / loop

- 路径：`src/water-caustics/fish-catch-caustics.ts`
- [固定版本源码](https://github.com/pan277942135/demo--Image-light-effect/blob/4078addf9dbca4732a676f8a689800261955b987/src/water-caustics/fish-catch-caustics.ts#L42)；commit：`4078addf9dbca4732a676f8a689800261955b987`；关键行：`42-94;278-654`。
- 文件 SHA-256：`3e7bc4e184da5c4233adeb00037636b274bec939e9374788b476b169f78e1b73`。

### W03｜FISH_CATCH_FRAG_SRC

- 路径：`src/water-caustics/shaders.ts`
- [固定版本源码](https://github.com/pan277942135/demo--Image-light-effect/blob/4078addf9dbca4732a676f8a689800261955b987/src/water-caustics/shaders.ts#L180)；commit：`4078addf9dbca4732a676f8a689800261955b987`；关键行：`180-280`。
- 文件 SHA-256：`e4ebbc171658f8e3453d683a83917968537119c618da8a120decf82cdfce8b2a`。

### W04｜sampleCaustic

- 路径：`src/water-caustics/caustic-field.ts`
- [固定版本源码](https://github.com/pan277942135/demo--Image-light-effect/blob/4078addf9dbca4732a676f8a689800261955b987/src/water-caustics/caustic-field.ts#L1)；commit：`4078addf9dbca4732a676f8a689800261955b987`；关键行：`1-97`。
- 文件 SHA-256：`a8aa60ac69692246ff15aa36a38763c8097295c1e37e6ce4122a45825f5c5578`。

### C01｜rotate / octave / field / sampleCaustic

- 路径：`src/caustic-field.ts`
- [固定版本源码](https://github.com/ethanfox/water-caustics/blob/de856eec62819b7c4176366db73b8fd37a1b1455/src/caustic-field.ts#L1)；commit：`de856eec62819b7c4176366db73b8fd37a1b1455`；关键行：`1-97`。
- 文件 SHA-256：`a8aa60ac69692246ff15aa36a38763c8097295c1e37e6ce4122a45825f5c5578`。

### C02｜createCanvas2DRenderer / frame

- 路径：`src/canvas2d.ts`
- [固定版本源码](https://github.com/ethanfox/water-caustics/blob/de856eec62819b7c4176366db73b8fd37a1b1455/src/canvas2d.ts#L1)；commit：`de856eec62819b7c4176366db73b8fd37a1b1455`；关键行：`1-90`。
- 文件 SHA-256：`1b5dc30fb725b1721d42a144ac6b03037daac6841e10ff85554641d16c25f806`。

### C03｜MIT License

- 路径：`LICENSE`
- [固定版本源码](https://github.com/ethanfox/water-caustics/blob/de856eec62819b7c4176366db73b8fd37a1b1455/LICENSE#L1)；commit：`de856eec62819b7c4176366db73b8fd37a1b1455`；关键行：`1-21`。
- 文件 SHA-256：`2e269665687929e4300af3ecf879eae0534e90b71b94c7c30c09797852031fac`。

### C04｜mountWaterCaustics

- 路径：`src/mount.ts`
- [固定版本源码](https://github.com/ethanfox/water-caustics/blob/de856eec62819b7c4176366db73b8fd37a1b1455/src/mount.ts#L1)；commit：`de856eec62819b7c4176366db73b8fd37a1b1455`；关键行：`1-110`。
- 文件 SHA-256：`27906d4165d34cb2d3f5df63ded44be3815adf4174e207f4ec0900ee506869cb`。

## 附录 B｜开放 PR 实时快照

HEAD由本轮GitHub PR metadata读取；这是时间点快照，不保证后续不变。除源码索引所列Main之外，未把这些分支计入现有正式能力。

| PR | HEAD | 状态 |
|---|---|---|
| [#282](https://github.com/pan277942135/Yujian_App/pull/282) | `f3f9913d5d89e86d7e14ed00f61e86d981f86906` | open |
| [#254](https://github.com/pan277942135/Yujian_App/pull/254) | `c1b2a47b55188f3a80c8833b737a93634f19dd4a` | open |
| [#249](https://github.com/pan277942135/Yujian_App/pull/249) | `15ccbcf17750ab3e799729afd18283156ba78ccc` | open |
| [#248](https://github.com/pan277942135/Yujian_App/pull/248) | `154145fcaf13b9b96b101104bce4997b2bedaf13` | open / Draft |
| [#239](https://github.com/pan277942135/Yujian_App/pull/239) | `e680737e7accbffd7e341232bd4f2e57c64f58e2` | open / Draft |
| [#234](https://github.com/pan277942135/Yujian_App/pull/234) | `bd5112f8e6860e53b87c44284d4e43613f039b74` | open / Draft |
| [#226](https://github.com/pan277942135/Yujian_App/pull/226) | `7feecfe5fb4625ebb767a5466468eeb69950d53b` | open |
| [#214](https://github.com/pan277942135/Yujian_App/pull/214) | `578a0fdc6c5119546933df26e0542383eb175b3d` | open / Draft |
| [#208](https://github.com/pan277942135/Yujian_App/pull/208) | `d8588432209405a7c97360b95f30a0077e5d6699` | open |
| [#197](https://github.com/pan277942135/Yujian_App/pull/197) | `4380d2db3d400944153ca9f25f09f16e650cadb5` | open / Draft |
| [#184](https://github.com/pan277942135/Yujian_App/pull/184) | `4c830da55fdc293230c63b824f0ff778680ccb80` | open / Draft |
| [#183](https://github.com/pan277942135/Yujian_App/pull/183) | `9888ab37abd6fbd92dc0362d32e4bba3a487a183` | open / Draft |
| [#182](https://github.com/pan277942135/Yujian_App/pull/182) | `b63dd326e16fd55485b0262970d1e9e20a08b00c` | open |
| [#176](https://github.com/pan277942135/Yujian_App/pull/176) | `ebee37f6716f9dbd7b2fdb9165568f7ac435fbeb` | open / Draft |
| [#175](https://github.com/pan277942135/Yujian_App/pull/175) | `637629fa439bba14625f1ef4b8aa1742aeb57f09` | open / Draft |
| [#168](https://github.com/pan277942135/Yujian_App/pull/168) | `aab4c5a42c717eec89bb9808f3c7279d9b4d3dfc` | open / Draft |
| [#166](https://github.com/pan277942135/Yujian_App/pull/166) | `d3685e876d9b4a744952834e6621e84874d63e6e` | open / Draft |
| [#163](https://github.com/pan277942135/Yujian_App/pull/163) | `08263fd378c6b2d51fc6b9a48df7c54daeeccd4f` | open / Draft |
| [#162](https://github.com/pan277942135/Yujian_App/pull/162) | `1d948b45a3ebc759acef72dd1f81769e9e832d0f` | open / Draft |
| [#161](https://github.com/pan277942135/Yujian_App/pull/161) | `584c576e2a1a47fee98f65474f05fe3f09072155` | open / Draft |
| [#131](https://github.com/pan277942135/Yujian_App/pull/131) | `34b1ece28436e7389f1cdb83fa9931e7c32a671f` | open |
| [#120](https://github.com/pan277942135/Yujian_App/pull/120) | `65a53e98db962bb71837c62e64eeed1b79bcb5fb` | open / Draft |
| [#84](https://github.com/pan277942135/Yujian_App/pull/84) | `1c4bcb5b1d1a5cee7f904c9895f326340a94663f` | open |

## 附录 C｜本轮验证边界

- 三个checkout均为只读浅克隆；未调用任何GitHub写入、workflow触发或merge操作。
- 静态检查：真实源码逐项读取、二进制ONNX输入输出元数据解析、模型/核心资源与算法文件SHA-256计算、Main远端SHA复核、开放PR metadata查询。
- 未执行：Android构建、仪器测试、模拟器、截图对比、实际模型推理、性能benchmark。
- 本报告仅有一个交付文件；未生成新视觉图或新Demo。