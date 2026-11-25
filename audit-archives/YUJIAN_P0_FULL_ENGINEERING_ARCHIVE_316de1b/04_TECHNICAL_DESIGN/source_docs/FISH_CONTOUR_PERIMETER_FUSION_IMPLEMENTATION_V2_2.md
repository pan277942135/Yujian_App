# 渔见 AI 鱼体轮廓 × 照片 WaterLight × AI Orb 集成 V2.2

## 基线

- Repository: `pan277942135/Yujian_App`
- 唯一集成 PR: [#316](https://github.com/pan277942135/Yujian_App/pull/316)，保持 Open / Draft / Unmerged
- 目标分支: `feat/p0-ai-orb-external-optics-v2`
- 开工 Main SHA: `99986f50944353b400594b4ceb0dd34be1df3b39`
- PR 原始 HEAD: `f33b78a5a0086b907b8ac9089bb0f9512f6c6be9`
- Demo SHA: `a790f145620ab32f96fe525bc38dd902653bd960`
- Demo 文件及用户参考视频的 SHA-256、尺寸和探测时长见 [`visual-reference/manifest.json`](visual-reference/manifest.json)。

## 实现

### 真实 Mask 鱼体光学

- 保留 Detector → Classifier → SlimSAM → 可信 Mask → Marching Squares 的生产路径。没有可信 Mask 时仍不生成鱼形、椭圆或 BBox 描边。
- 对 APP 每个 Marching Squares 闭环做 3 次循环加权平滑、闭环 RDP（2.2 源图像像素）和 2 次细化；按面积、绕向、自交和双向边界偏差检查，逐级回退到更轻平滑，最后保留原始闭环。不同闭环不合并。
- 在相同真实 Path 上补齐 L0 深青 Under Stroke、L1 连续蓝金渐变、L2 冰青细高光、L3 PathMeasure 金色巡航。线宽通过 Android density 映射至照片显示像素，确切跨端视觉尺寸仍需参考 DPR 与设备验收。
- Path 缓存边界包含 session、attempt、image、photo SHA-256、Mask bitmap generation、contour hash 和 geometry version；Processing → Result handoff 传递 Mask version。
- Demo 内部反光函数不在最终鱼体 JSX 渲染路径中，因此未添加全图采样或新 Mask 内反光算法。APP 原有柔光/halo 按冻结合同保留。

### 照片四周 WaterLight

- 扩展原有单个 `RecognitionPerimeterTextureView` / EGL14 / GLES20 照片合成器，没有复制 TextureView 或新增 AGSL 后端。
- GLES2 Shader 包含 rounded-box SDF、双频周边扰动、wide/medium/sharp 三层光晕、三 octave domain-warped caustics、veins、haze、light spots、底部渐变与色流、Overlay 合成、16 个节点、底部白色核心、金色双水线和珠光端点。
- Effect 02 默认值由 `RecognitionPerimeterOpticalProfileV1` 提供；Effect 01 保留为内部对照。Caustics density 作为审计元数据保存，因为 Demo 最终 Shader 没有相应 uniform/use site。
- Shader、圆角路径、CPU 节点与底饰统一使用照片局部 1.5% 边距和短边 12% 圆角。缺少 Demo 浏览器 CSS/DPR/backing-store 测量值时，代码采用可用的 identity 候选映射并记录 `COORDINATE_MAPPING=NOT_VERIFIED`，不会报告为已测量通过。
- 移除覆盖照片的低分辨率 `WaterCausticsRenderer` Bitmap 和 Ambient edge field 重复绘制；保留其余 Ambient、Upward Caustic Surge、Energy Path、水草及水滴功能。

### 时钟、Orb 和 Result

- 外围动画与鱼体金光相位仅消费 `RecognitionSceneFrame` / `opticalReferenceElapsedMs` 和现有阶段局部进度；没有新增 Timer、Animator 或动画状态机。
- 保留现有 13 阶段枚举、阶段最短可见时间、AUTO/MANUAL 与暂停恢复、真实模型 gate 及共享 60 秒 attempt deadline。
- Orb 能量链路继续使用既有 Energy Event / Result Orb；Result handoff 使用相同 session、attempt、image、photo hash、Mask version、geometry version、Effect 02 profile 和 Scene Frame，不重新启动外围展开进度。
- Result 的短暂过渡承接使用已有 Canvas 照片层绘制同一圆角、16 节点和底部装饰，以免创建第二个 GLES 宿主。完整域扭曲焦散 Shader 仍运行于 Processing 的既有 GLES 宿主；Result 过渡 Canvas 不等于同一完整 Shader 的逐像素复刻，需由产品负责人评估这段过渡视觉。

## Demo 最终渲染路径审计

| Demo 源码 | 最终可见实现 | APP 对应与适配 | 未迁移 / 说明 |
|---|---|---|---|
| `FishContourGlowRenderer.tsx` | JSX 实际绘制 L0、抗锯齿垫层、L1 渐变、L2 高光、L3 流动金光；Soft Glow/Halo 的 Filter 定义不构成实际可见层 | `RecognitionFishFocus.kt` 与 Result 光学 Canvas，使用真实 Mask Path 和 APP Scene Frame | 不迁入 Demo 独立毫秒阶段；APP 原有 soft/halo 依冻结合同保留 |
| `contourExtraction.ts` | LCC、Moore 边界、循环加权平滑、RDP、Catmull-Rom→Cubic | `RecognitionMaskContourExtractor.kt` 在现有多闭环 Marching Squares 上做平滑/RDP/Bézier | 不采用 Demo 单一最大连通域，避免丢掉 APP 的有效子轮廓；APP 保留鞍点拓扑 |
| `FishCatchWaterOverlay.ts` | 被 `CanvasViewport` 使用的 WebGL perimeter fragment、Canvas screen nodes 与 CSS Overlay 合成 | `RecognitionPerimeterShaderOverlay.kt` 单 GLES2 pass 和同一照片纹理合成 | `causticsDensity` 无 Shader 使用点，不赋予新语义；运行时 Shader 编译需 Android 设备 |
| `CanvasViewport.tsx` | 最终页面中的照片容器、Shader 尺寸、Canvas 节点及照片变换 | `RecognitionImageTransform.photoContentRect`、原有 TextureView | 源 CSS/DPR/backing 尺寸尚未被实测注入 Android；见视觉 QA |
| `App.tsx` | 选择当前效果、参数并挂载 CanvasViewport / 鱼体 Renderer | Effect 02 默认，Effect 01 内部测试；既有 13 阶段驱动 | Demo 当前动画毫秒只用于形状/曲线参照，不作为 APP 阶段或期限 |

## 状态边界

- `SOURCE_VERIFIED`: PASS（源文件与最终调用路径已审计）
- `UNIT_TEST_PASS`: 以 PR #316 的本次 exact-head Android CI 为准
- `SHADER_COMPILE_PASS`: Android GLES Shader 仅在设备运行时编译，未用 Kotlin 编译状态替代
- `APK_BUILD_PASS`: 以 PR #316 的本次 exact-head CI 为准
- `VISUAL_CAPTURE_PASS`: NOT RUN；没有 Android 渲染截图
- `DEVICE_VALIDATION`: NOT RUN

最终 exact HEAD、CI Run ID、APK 字节数与 SHA-256 记录在 PR #316 的构建/交付记录中；APK SHA 是构建后值，不能预先写进其自身源码提交而保持同一 exact HEAD。
