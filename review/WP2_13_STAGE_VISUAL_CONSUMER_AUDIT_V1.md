# WP2 13 阶段视觉消费者审计 V1

审计源码基线：`51eeaa8c4621a9e68dddc30874e57a82aca0d120`（PR #327，Draft；Main `81200da2e41163b04cfd340de59c3f43700c329a`）。

本报告是源码调用链审计，不是设备视觉验收。每一项均从同一个
`RecognitionSceneAnimationController` 的 `RecognitionSceneFrame` 追到实际 Compose
组件或 Canvas 绘制；没有把状态存在误报成渲染存在。

| Stage | 状态 | Component / function | 实际输入 | 实际输出 | 限制与验证 |
|---|---|---|---|---|---|
| 01 PHOTO_STABLE | CONNECTED | `RecognizingScreen.kt:RecognitionPhoto` | `WaterLightFrame`、原图、`onPhotoFirstFramePresented` | `Canvas.drawImage`；首帧回调 `markPhotoFirstFramePresented` 启动唯一时钟 | 真实 PhotoSession 首帧前不推进；`RecognitionSceneFrameTest` |
| 02 ORB_WAKE | CONNECTED | `RecognizingScreen.kt:AIOrbCore` | 同一 `waterLightFrame.sceneFrame`、`orbEntranceMs` | 水核组件实际合成 | `WaterLightHost.ORB` 几何必须接受 |
| 03 ORB_RELEASE | CONDITIONAL | `WaterLightEnergy.at` → `WaterLightEnergyPathRenderer` | Stage 03、Orb surface 到 Photo perimeter 的 `WaterLightPathGeometry` | 实际路径 Canvas 绘制并以 `RELEASE` 事件解锁 | 需要有效 Host/路径；`WaterLightEnergyTest` |
| 04 PERIMETER_ARRIVAL | CONDITIONAL | `RecognitionPerimeterShaderOverlay` | `WaterLightFrame`、真实照片变换 | 外围水光 Shader/Canvas | 受 perimeter Host 与图层模式控制；`RecognitionPerimeterShaderMathTest` 覆盖 2600–2800ms 归零 |
| 05 PATH_TO_FISH | CONDITIONAL | `recognitionMaskAnchor` → `RecognitionNearestContourContactPoint.resolve` → `WaterLightEnergyPathRenderer` | 真实可信 Mask contour、Orb、照片变换 | Orb→perimeter→最近轮廓接触点的细光路 | 无可信 Mask 不创建鱼体路径；没有尾部/尾柄替代锚点 |
| 06 CONTOUR_A | CONDITIONAL | `RecognitionFishFocus` | `maskRevealProgress = p/3`、可信 Mask contour | 真实轮廓路径与蓝金材质 Canvas 绘制 | 可信 Mask、Bitmap、ROI 和 contour 缺一不可 |
| 07 CONTOUR_B | CONDITIONAL | `RecognitionFishFocus` | `1/3 + p/3`，同一 `RecognitionContourCacheIdentity` | 同一几何基准上的连续路径绘制 | 同上 |
| 08 CONTOUR_C | CONDITIONAL | `RecognitionFishFocus` / controller `contourClosed` | `2/3 + p/3`、闭合 contour | 闭合路径和 Mask 重采样 | 不可信 Mask 走 BBOX_ONLY，不进入轮廓绘制 |
| 09 FISH_GOLD_PEAK | CONDITIONAL | `recognitionFishGoldStrength` / `RecognitionFishFocus` | Stage 09 局部 progress、可信 Mask | 仅鱼体的 Mask 金光、Halo、Glow | `onFishGoldComplete` 只在峰值帧回报；外围水光由独立 `recognitionPerimeterOpacity` 控制 |
| 10 ORB_RETURN | CONDITIONAL | `RecognitionResultScreen` → `RecognitionRevealOpticalOverlay` | 同一 controller frame、`orbReturnPathLocal`、真实结果几何 | 反向回馈尾迹、粒子和原 Orb 接收 | 结果页几何就绪、HIGH 结果及有效回馈路径是必要门控 |
| 11 ORB_TO_NAME | CONDITIONAL | `RecognitionRevealOpticalOverlay` / `AIOrbCore` | Stage 11 frame、`orbToGlyphPathLocal` | Orb 横向伸展和实际 Canvas 液态笔势 | 需要 stage 10 已接收事件与真实 Glyph 可用 |
| 12 SPECIES_NAME_REVEAL | CONDITIONAL | `SpeciesIdentityRow` / `SpeciesNameReveal.draw` | 真实 `prediction.top1.speciesName`、Stage 12 frame | 实际 Text glyph path / Canvas 扫描揭晓 | 模型未就绪不会 `onGlyphAvailable`；不是 Mock/default 名称 |
| 13 RESIDUE_SETTLE | CONDITIONAL | `RecognitionRevealOpticalOverlay` / `WaterGrassRenderer` | Stage 13 frame、`glyphResiduePathLocal`、标题边界 | 余辉、粒子和水草在真实标题边界周围收束 | 以 `SETTLE` 事件结束并触发 `onRevealFinished` |

## 跨阶段连续性

| 路径 | 状态 | 证据 |
|---|---|---|
| Orb → WaterLight | CONNECTED | Stage 03 的 `RELEASE` 由同一 frame 生成路径、渲染，并由 controller 记录事件。 |
| WaterLight → Fish | CONDITIONAL | Stage 05 仅在可信 Mask 下以 `RecognitionNearestContourContactPoint.resolve` 的最近轮廓点为终点。 |
| Fish → Orb | CONDITIONAL | Stage 10 使用同一 ledger 的 return route 和 `RETURN` 事件；结果页绘制测得的 `orbReturnPathLocal`。 |
| Orb → Glyph | CONDITIONAL | Stage 11 的 `TRANSLATE` 路径、伸展 Orb 与 Stage 12 的 `SpeciesNameReveal.draw` 使用同一 scene frame。 |
| Glyph → Result | CONDITIONAL | `YujianApp.kt` 将单一 `SceneMotionCoordinator` 同时传给处理页和结果页；结果页没有重建生产时钟。 |

## 缺陷台账

本轮静态审计没有发现 `STATE_ONLY` 或 `NOT_CONNECTED` 的 P0/P1 项，因而没有进行无意义的
Runtime 改写。所有 `CONDITIONAL` 项都是冻结合同所要求的真实模型、可信 Mask、路径或布局门控，
不是断链。待执行的验证是现有 Android 实机测试 `RecognitionRevealProductionRuntimeTest` 和
`RecognitionFrozenFlowEmulatorTest`；它们不能替代产品负责人真机视觉验收。

本地 Gradle 在本报告创建时仍处于下载 Gradle 发行包的环境复验中；这不改变当前精确 Head 的
Android CI 通过记录，也不将设备验收标为通过。


## 最新精确 HEAD 验证补充（2026-11-09）

本报告的源码调用链审计绑定上述 `51eeaa8c4621a9e68dddc30874e57a82aca0d120`；不得将文档更新后的提交 SHA 冒充为本次源码审计基线。

- Android CI：Run [42552080491](https://github.com/pan277942135/Yujian_App/actions/runs/42552080491)，在该精确 HEAD 上 PASS。
- Control Plane Admission：PASS。
- Unit tests、Camera Runtime Harness、Debug APK、AndroidTest APK compile、APK resource verification、Lint、Release Kotlin compile：PASS。
- Android runtime、Orb visual evidence、fish optics API36 runtime jobs：SKIPPED；本报告不声称设备视觉验收通过。
- 该 HEAD 的 APK 模型追踪仍显示 Detector `DET_FISH_v0.1`（ONNX SHA-256 `12b97f7c081987f33f99d255cdd2e935fb9cf93b893146f54ff98b9c4e3a8e4f`）与 Classifier `MODEL_CROP_M1_v0.2`（TFLite SHA-256 `29c2f2fd1608178f023b8e35edf632c0dd685df4a7ae1c3562220a7352ccce18`）。该 APK 不是 DET_FISH v0.3 集成包。

本补充只更新审计基线与证据边界，不改变 13 阶段合同，也不将源码接线审计提升为 Runtime/设备验收。
