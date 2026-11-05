# 渔见 AI 13 阶段 Runtime V1.2 最终实施与测试报告

**任务：** `YUJIAN_RECOGNITION_RUNTIME_13_STAGE_INTEGRATION_V1_2`
**仓库 / PR：** `pan277942135/Yujian_App` / #317（Open / Draft / Unmerged）
**Main 基线：** `74cad95b4f673bd9c8c60d16839f036d5886d933`
**最终 Runtime 构建源码：** `beed77ad31bb54010148c7bf459f3b445ccbcd23`
**最终状态文件提交：** 见 PR #317 最新 HEAD；APK 由上述精确 Runtime 源码构建。

## CP01–CP07 实施状态

| CP | 实施结果 | 代码源 SHA | 专项证据 |
|---|---|---|---|
| CP01 PHOTO_STABLE | 原始 Core/PHOTO 稳定帧展示、首帧 SceneClock 锚定 | `81a0c257417c0be050e8dd57ace95e97619babf4` | 此前 PHOTO_STABLE exact-source workflow 通过；视觉验收 `NOT_RUN` |
| CP02 ORB_WAKE | 原始 Core 独立绘制，光学效果叠加；CORE_ONLY / CORE_WITH_OPTICS / FULL_ORB_WAKE 诊断对照可切换 | `ab2407ab42459f12ccb8178df7ebe3badc396c13` | Orb workflow `42010365270` PASS；真机 AGSL 对照 `NOT_RUN` |
| CP03 ORB_RELEASE | 复用单 Orb、单 SceneClock 和现有 R06 五路径释放/回收 | `4dc78515bb006f2952386d046db079eb2dce3162` | Orb workflow `42011287356` PASS；R06 冻结资源身份 `NOT_CONFIRMED` |
| CP04 PATH_TO_FISH | 当前照片 Mask 推导鱼尾接触点；以当前 PhotoGeometry 转换坐标；两模型未终态时低强度外围光循环；同 Attempt 汇合 | `84c2bf30d51d38c3720f4124315ec799de601bd1` | Orb workflow `42019562279` PASS |
| CP05 CONTOUR_A/B/C | 可信 SlimSAM Mask 轮廓由鱼尾双向展开并汇合；Core / Soft / Halo 分层 | `ead45796f8332ef673fd615402c72b995fb5f09d` | Orb workflow `42023472919` PASS |
| CP06 ORB_RETURN / GLYPH | 回馈到同一 Orb；六个真实 Glyph 子步；Glyph Path 来自当前 Classifier 名称布局 | `f53580b5554d62e8ef26e485c2f902c885ec7121` | Orb workflow `42030743624` PASS |
| CP07 RESULT_HANDOFF | Stage 13 的 13.1–13.5 由同一父阶段 SceneFrame、照片几何、结果数据和稳定事件驱动 | `beed77ad31bb54010148c7bf459f3b445ccbcd23` | Exact-source Orb workflow `42037876704` PASS；最终 APK 已构建并上传 |

### CP02 根因定位补充

Core WebP 源文件及 APK 内副本哈希一致，Alpha 非空。根因在合成层：此前唯一 Core `Image` 把 `RenderEffect` 直接挂在自身 layer 上，Core 本体因此依赖 AGSL 的输入/输出 Alpha 与 layer bounds；Shader 输出透明、被裁切或创建失败时，没有独立的原始 Core 作视觉保底。修复后始终独立绘制原始 Core，再在透明独立层叠加光学效果。三个诊断模式只切换可选光学/细节层，使用同一张 Core 和同一 SceneFrame。Shader 构造失败有 Warning 日志并落到原始 Core + clipped Canvas 光效，不视为完整光学通过。

对应单测覆盖 Alpha、A/B/C 渲染策略、共享时钟、R06 参数和 Host 的 Session/Attempt/Image/照片哈希/内容版本/几何版本拒绝逻辑。AGSL 故障注入与视觉输出没有设备运行证据，保持 `NOT_RUN`。

### CP04 鱼尾锚点与等待路径

Mask Adapter 已把 SlimSAM ROI 轮廓归一到 EXIF 正规化后的整张照片坐标；旧寻鱼锚点又套了一次 Detector BBox 并对轮廓点取均值，所以并非真实尾柄接触点，还会随裁剪变化。修正后使用当前闭合轮廓的主轴和横截面宽度，查找尾柄收窄；形状不明确或点落在当前可见 Crop 之外时返回无锚点，不构造固定坐标。路径身份绑定 Session、Attempt、photoId、照片 SHA、geometryVersion 和 Mask 指纹。

Classifier 与 SlimSAM 未都进入终态时，外围 WaterLight 维持低强度共享 SceneClock 动态；终态汇合后由真实 FULL / ORB_ONLY 决策接管。60 秒共用预算、旧 Attempt 拒绝、真实 Mask 信任校验由既有 Coordinator/Decision Gate 执行。

### CP05 轮廓与鱼体金光

轮廓从当前 Mask 推导并经平滑、简化与几何校验；鱼尾锚点决定双向展开起点。A/B/C 由同一主阶段进度逐步描绘，其他 Mask 岛在 C 前不加入。Core、Soft、Halo 各自具有 alpha、宽度和模糊包络。ORB_ONLY、低可信 Mask、身份不匹配时均不绘制鱼体轮廓或金光。

### CP06 Orb Return 与 Glyph

Orb Return 使用现有 WaterLight 能量事件回收；Orb Core 身份保持不变，不创建第二 Orb 或新 SceneClock。11.1–11.3、12.1–12.3 均从共享 SceneFrame 投影；真实 Classifier 名称通过 TextLayout Path 形成 Glyph，缓存键包含 Session、Attempt、照片身份/哈希、species key 与名称。FULL / ORB_ONLY 汇入同一 GlyphRenderer。

### CP07 Result Handoff

13.1–13.5 共用冻结 Stage 13 的 450ms 父阶段，没有人为分配子帧毫秒或固定比例窗口。照片位移按该父阶段的共享进度连续变化；当前照片身份、几何有效性、真实结果数据到达以及稳定事件依序门控交接和一次性结果提交。每个 Attempt 的提交由 identity guard 限制一次。

最初 CP07 草稿曾用 `.08/.68/.78` 固定阈值切分子阶段；对照 `RECOGNITION_13_STAGE_TIMING_CONTRACT_V1_0.md` 后确认这种切分违反“不为子帧虚构时间窗口”的冻结约定，故被 `beed77ad…` 的事件驱动实现取代。该旧草稿不用于最终 APK。

## 13 阶段测试用例

以下是已执行单测中的语义验收映射，不代表真机视觉验收。

| 阶段 | 验收用例与自动化证据 |
|---|---|
| 01 PHOTO_STABLE | 首次实际展示帧作为 T0；Core 使用原始 Alpha，不带前一阶段残留 RenderEffect。`AIOrbPhotoStableRenderingPolicyTest`、`RecognitionSceneFrameTest.zeroDurationPhotoAnchorStartsRuntimeAtItsFirstPresentedFrame` |
| 02 ORB_WAKE | Core Alpha 保留；光学层使用相同 SceneFrame；Wake/呼吸包络按语义阶段前进。`OrbFluidOpticalIntegrationV1Test.agslAndComposeUseOneFrameAndKeepOriginalAlpha`、`OrbWakeBreathReleaseV11Test` |
| 03 ORB_RELEASE | 五条 R06 Bezier/弧长路径及释放—回收连续性；复用同一 SceneClock。`OrbRuntimeProfileV1Test.r06ContainsTheFiveOfficialBezierPathsAndArcLengthTables`、`r06PathClocksAreIndependentAndReleaseReturnSwitchWithoutJump` |
| 04 PERIMETER_FORM | Detector 锚点到照片外围路径保持坐标比例、几何有效并服从阶段事件。`RecognitionSceneFrameTest.perimeterArrivalCanBecomeRenderableAfterDetectorTargetArrives`、`RecognitionPerimeterShaderMathTest` |
| 05 FISH_SEEK | 当前 Mask 估算尾柄，不用 BBox 中心；旋转、Crop/Fit 映射、歧义拒绝；模型待决时外围低强度循环。`RecognitionFishTailAnchorTest`、`RecognitionPerimeterShaderMathTest.modelJoinWaitKeepsAQuietPerimeterLoopUntilBothModelsAreTerminal` |
| 06 CONTOUR_A | 可信 Mask 轮廓从尾点向两侧局部展开；不可信 Mask 不显形。`RecognitionFishFocusTest.trustedMaskContourRevealsAcrossTheThreeSemanticContourStages` |
| 07 CONTOUR_B | 两侧沿真实 Mask 连续推进；不同位置的轮廓进度连续。`RecognitionFishFocusTest.contourRevealStartsAtTailAndExpandsAlongBothSidesUntilTheyMeet` |
| 08 CONTOUR_C | 双侧前沿汇合、轮廓闭合；Core/Soft/Halo alpha 包络独立。`RecognitionFishFocusTest.contourOpticalLayersHaveSeparateProgressiveAlphaEnvelopes` |
| 09 FISH_GOLD_PEAK | 三层金光仅贴合 Mask；金光峰值与冻结时序一致。`RecognitionFishFocusTest.goldStrengthFollowsContourGoldPeakAndOrbReturnTiming`、`RecognitionRevealPolicyTest` |
| 10 ORB_RETURN | 能量沿既有路由返回同一 Orb；旧 GeometryVersion 不能完成回馈事件。`RecognitionVisualStateControllerTest.oldResultGeometryVersionCannotCompleteAnOrbReturnEvent` |
| 11 ORB_TO_NAME | 11.1 Hold、11.2 横向舒展、11.3 液态笔势连续；未生成新时钟。`RecognitionOrbGlyphTransitionTest.sharedSceneStagesProjectTheSixRequiredGlyphSubsteps` |
| 12 SPECIES_NAME_REVEAL | 12.1 笔势聚合、12.2 字形显现、12.3 真名称保持；缓存严格绑定当前名称/Attempt。`RecognitionOrbGlyphTransitionTest.glyphCacheIdentityIncludesCurrentAttemptPhotoAndRealSpeciesName` |
| 13 RESIDUE_SETTLE | 13.1–13.5 由共享 SceneFrame、照片几何、真实数据和稳定事件驱动；结果仅提交一次。`RecognitionResultHandoffTransitionTest` 四项测试 |

## 模型分支、超时与身份隔离

| 范围 | 自动化用例 | 结果 |
|---|---|---|
| FULL | Classifier 与可信 Mask 汇合后启用鱼体轮廓 | 通过：`RecognitionRevealDecisionGateTest.classifierFirstWaitsForTrustedSlimSamThenChoosesFishFocus`、`trustedSlimSamFirstWaitsForClassifier` |
| ORB_ONLY | Mask 失败/不可信时保留真实 Classifier 名称，不画轮廓；共用 GlyphRenderer | 通过：`classifierFirstWithUntrustedMaskChoosesOrbOnly`、`classifierFirstWithSlimSamFailureKeepsRealSpeciesOnOrbPath`、`RecognitionFishFocusTest.orbOnlyCannotRevealContourEvenAcrossContourStages` |
| 60 秒预算 | Detector/Classifier/SlimSAM 使用同一剩余预算；过期预算不可重启 | 通过：`RecognitionAttemptDeadlineTest.detectorClassifierAndSlimSamShareTheSameRemainingBudget`、`expiredBudgetCannotBeRestartedByALaterModelStage` |
| 实际模型仍在运行 | Watchdog 不把仍在进行的模型调用提前伪报失败 | 通过：`RecognitionModelWatchdogTest.modelRemainsPendingUntilTheRealCallCompletes` |
| 新旧 Attempt 隔离 | 旧 Attempt、Mask、Geometry、Glyph、能量事件不能覆盖新身份 | 通过：`RecognitionRevealDecisionGateTest.staleOrCancelledAttemptCannotCompleteReveal`、`RecognitionVisualStateControllerTest.aNewAttemptInvalidatesThePreviousIdentityAndItsEvents` 及 Mask/Glyph cache identity 测试 |

## 测试与 CI 结果

| 检查 | 结果 | 证据 |
|---|---|---|
| `:app:testDebugUnitTest` | PASS | Exact-source workflow `42037876704`，完整任务成功 |
| `:app:assembleDebug` | PASS | 同上，Gradle `BUILD SUCCESSFUL` |
| `:app:assembleDebugAndroidTest` | PASS | 同上，Gradle `BUILD SUCCESSFUL`；生成 AndroidTest APK |
| Capture/Core 打包资源校验 | PASS | CI 中源/包内 SHA 相同；Orb Core alpha 资产校验通过 |
| `git diff --check` | PASS | CP07 修正提交检查通过；最终状态提交后再次核验 |
| AndroidTest APK 运行 | NOT_RUN | 只构建 APK，未运行设备测试 |
| 完整 Android PR CI | FAIL（非功能门禁） | `42037876742` 在 Gradle 前由既有 `EMPTY_HOME_RUNTIME_V2` 测量锚点检查退出；本任务未改 Empty Home 代码/合同 |
| Control-plane/Main promotion | BLOCKED/FAIL | `42037885114` 拒绝 PR 分支中所需 `PROJECT_MASTER_STATE.yaml`、`WORK_EXECUTION_STATE.json`；未删除或绕过门禁 |
| 真机视觉/API28 | NOT_RUN | 本轮未执行设备视觉或 API28 专项 |

第一次 CP07 语法检查在 `6ceff3c…` 发现多余 `}` 并修正；事件时序合同复核又淘汰固定比例草稿。最终以 `beed77ad…` 精确源码重跑的 `42037876704` 为有效构建证据。

## 最终 APK

- 文件：`Yujian_Recognition_Runtime_V1_2_Debug.apk`
- Runtime 源码 SHA：`beed77ad31bb54010148c7bf459f3b445ccbcd23`
- 大小：144,083,254 bytes
- SHA-256：`1de29d32ec185b32b5c1cdc5f156e22a25afed79fe1bbe54cb10829391d0dd66`
- GitHub Actions Artifact：`13419188038`，ZIP 143,372,481 bytes，SHA-256 `38ba6c85d2bd6b5da2e3c84f25324d2ed9d2081b40a7ee83009d67f8b242f8fc`
- Library：`libfile_beb7dfacadc881918b79857f1be3c2cf`，替换操作成功，当前版本 1。

## 未闭环项

1. CP03 R06 运行路径通过专项测试，但冻结资源包的源 SHA/正式资源身份仍 `NOT_CONFIRMED`，本报告不将其伪报为已核验。
2. 真实设备上的 AGSL A/B/C、Orb Wake、鱼体金光、Glyph、Result 页面视觉均 `NOT_RUN`，留给产品负责人安装 APK 验收。
3. 完整 PR CI 与 Control-plane 准入仍受本任务之外的 Empty Home V2 合同和状态文件准入策略阻塞；PR #317 继续 Draft / Unmerged。
4. 本轮未执行 API28 专项或 AndroidTest APK 设备运行。

**最终交付状态：** `V1_2_RUNTIME_IMPLEMENTED_AND_DEBUG_APK_DELIVERED`（指代码实施、精确源码基础构建与 APK 交付；不代表真机视觉验收完成）。
