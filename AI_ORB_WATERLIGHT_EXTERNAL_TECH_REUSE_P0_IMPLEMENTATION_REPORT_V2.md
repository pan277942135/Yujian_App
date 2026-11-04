# 渔见 AI Orb × WaterLight 外部技术复用 V2 — 实现报告

**Task ID：** `YUJIAN_AI_ORB_WATERLIGHT_EXTERNAL_TECH_REUSE_ANDROID_INTEGRATION_P0_V2`
**仓库：** `pan277942135/Yujian_App`
**工作分支：** `feat/p0-ai-orb-external-optics-v2`
**开发基线：** `main @ 99986f50944353b400594b4ceb0dd34be1df3b39`（PR #315 合入后）
**候选配置：** `ORB-FLUID-OPTICS-CANDIDATE-V1`，状态 `CALIBRATION_CANDIDATE`
**实现代码提交：** `b822d4ca9cc303b102a49a77d82d7af50e208959`（远端 GitHub 分支提交；tree SHA 与本地审计工作树一致）。
**APK 源码 SHA：** `NOT_BUILT`；尚无 APK，因此没有 APK 对应的源码 SHA。任何后续 PR/构建必须使用当时的最新分支 HEAD。
**APK：** 未构建（`NOT_PRODUCED`），不得据此报告 Android 编译或设备运行通过。

## 实现概要

在既有 `processing_water_core_v1.webp` 图像层中加入两个 API 33+ RuntimeShader，并按正确的 inner/outer 顺序组成一个 RenderEffect：液态水流和玻璃边缘折射先作用于原图，V1.2 四片草叶 Warp 后作用于前一结果。流体 Shader 按四张原有单叶 Mask 排除水草区域；输入 Alpha 被原样保留。四片 Mask 在候选 Shader 栈创建时解码一次，并供两个 RuntimeShader 共享。初始化失败时回退到冻结 V1.1 草叶 Shader；API 33 以下不创建 RuntimeShader，保留原始 Orb 图和已有 Canvas 水滴、阳光、焦散。

现有两层焦散之外加入低频、软边域扭曲辅助场，辅助 alpha 上限为 0.035，并继续受现有 R03 0.14 总响应上限约束。固定阳光源和既有主折射线保留；两条局部柔和光线从同一折射 alpha 预算拆分，不增加亮度上限。

## 外部仓库与复用记录

所有源仓库均在指定 commit 检查。没有复制外部图像、默认配色、状态机、组件或独立动画时钟。

| 仓库 / commit | 检查文件与技术 | 本轮处理 |
|---|---|---|
| [jamisonhill/aquarium](https://github.com/jamisonhill/aquarium/tree/2a6e16ce5df82b858183c02624d2ac5e605faec5) — `2a6e16ce5df82b858183c02624d2ac5e605faec5` | `src/engine/Flora.ts` (`GeoAccum`, `swayPars`, `swayHook`, `MOTION`)；`src/engine/CurrentField.ts` (`setup`, `sample`)。检查到叶根权重、共享低频水流、叶片相位和小幅高频分量；代码采用 MIT 声明。 | 算法级重实现为 `OrbGrassSharedCurrentV12.kt` 和 V1.2 AGSL。保留四条原 Bezier、周期、相位、Mask 和振幅。没有迁移 Three.js、植物几何、鱼或水族箱。Pinned tree 未提供独立完整 `LICENSE` 文件，本轮没有复制源代码片段。 |
| [macarchy/omarchy-aquarium](https://github.com/macarchy/omarchy-aquarium/tree/b575baa0d407c8accabc23a79db820ed9d641fe4) — `b575baa0d407c8accabc23a79db820ed9d641fe4` | `src/aquarium.frag` 的 `noise/fbm/fbm3c/caustics/rays`，`src/main.c` 的 ray LUT 组织；MIT。 | 将低频域扭曲与软折叠思想独立实现为 `sampleSoftDomainWarpedCaustic()`；阳光只用固定方向的局部柔和线，未迁移 Voronoi 亮线网、Ray LUT 或全屏背景。保留上游 MIT 声明于 `licenses/external-omarchy-aquarium-MIT.txt`。 |
| [LerSent001/orb](https://github.com/LerSent001/orb/tree/8d1736e1fc5b41a5037b524403f35b7952b038f0) — `8d1736e1fc5b41a5037b524403f35b7952b038f0` | `effect.wgsl` 的 `glsRefractiveBlobFluid`、`glsBlueDropFluid`、`glsRefractionProfile`、contour/refraction normal；`src/orb-states.ts` 状态控制；MIT。 | 液态双尺度流场、慢域扭曲、Alpha 梯度法线和局部边缘折射以 Android AGSL 独立实现。没有迁移预设色彩、粒子带、状态控制器或 384×96×6 粒子管线。保留 MIT 声明于 `licenses/external-lersent001-orb-MIT.txt`。 |
| [brkckr/LiquidOrb](https://github.com/brkckr/LiquidOrb/tree/4ecd7b7ed2f7a80c0ed52ab38896aac936fe3845) — `4ecd7b7ed2f7a80c0ed52ab38896aac936fe3845` | `LiquidOrb.kt`、`OrbShader.kt`：Android `RuntimeShader`、Uniform、Compose 绑定和资源生命周期。Pinned tree 没有 LICENSE 文件。 | 仅核对 Android API 接法和生命周期；未复制组件、Shader、色彩、状态动画或源代码。 |
| [zq52xy/glass-voice-orb-study](https://github.com/zq52xy/glass-voice-orb-study/tree/3d7e98199b385358df6ddf65a9d20644754f22eb) — `3d7e98199b385358df6ddf65a9d20644754f22eb` | `src/state.js` analytical spring；`src/shaders/glass.frag.glsl` shape gradient、refraction、highlight；许可为非商业 source-available。 | 仅作为边缘法线和连续过渡的概念参考；未复制代码/Shader/素材，也未迁移 Siri/audio/spring/state 或增加计时器。 |

### 实际迁移到 Android 的算法

1. 低频两场流动、慢域扭曲和有限像素偏移：`orb_fluid_refraction_v1.agsl`。
2. 依据源 Alpha 中心差分计算边缘法线，按距边缘衰减折射；输出 Alpha 固定为原图 Alpha；读取四张 Mask，水草区域以及折射采样跨入草叶时均压低液态混合。
3. Aquarium 风格的共享低频水流 + 原叶片周期/相位 + 小幅 flutter；三项权重和为 1，因此单叶最大位移仍不超过原幅度。
4. 软域扭曲的低频焦散辅助场，经过原 Orb Alpha mask，再限制于 0.035 辅助 alpha 和原 R03 焦散总上限。
5. 固定光源的两条局部光线从原 refraction alpha 中拆分，按原场景帧推进。

## 修改文件

### Kotlin

- `app/src/main/java/com/yujian/ai/ui/designsystem/components/aiorb/AIOrbCore.kt` — 仅在原始 Image 的现有 `graphicsLayer` 绑定合成效果，保留五层顺序及公共参数。
- `app/src/main/java/com/yujian/ai/ui/waterlight/renderer/OrbFluidOpticalProfileV1.kt` — 集中管理候选频率、尺度、位移、混合、折射、焦散、阳光和 V1.2 草叶权重，并校验 R03 预算。
- `OrbFluidRefractionProgramV1.kt` — RuntimeShader 创建、Uniform、Mask 共享、RenderEffect 合成、资源释放和 V1.1 fallback。
- `OrbGrassSharedCurrentV12.kt`、`OrbGrassWarpProgramV12.kt` — 可 seek 的共享水流计算及四 Mask V1.2 Renderer。
- `WaterCausticsField.kt` — 保留原 `octave/field/sampleCaustic` 并增加软域扭曲采样。
- `OrbCausticsAdapter.kt` — 增加受限焦散辅助场和固定方位阳光线；既有双缓冲、缓存、generation/session 隔离、纹理尺寸和更新间隔保持。
- `app/src/test/java/com/yujian/ai/ui/waterlight/OrbFluidOpticalIntegrationV1Test.kt` — 候选预算、叶片运动界限、软焦散有限性、Shader 链顺序、Alpha 与时钟静态合同测试。
- `OrbGrassFrozenAssetsV11Test.kt` — 将唯一 Image layer 断言更新为新复合效果接入，同时保留原冻结资源、路径、边界和振幅断言。

### AGSL 与许可

- 新增 `app/src/main/assets/orb_fluid_v1/orb_fluid_refraction_v1.agsl`。
- 新增 `app/src/main/assets/orb_r01b_v1_2/orb_grass_warp_v1_2.agsl`。
- 原 `orb_r01b_v1_1/orb_grass_warp_v1_1.agsl` 未改动，仍作为候选初始化失败时的回退。
- 新增 Omarchy 与 LerSent MIT 文件，置于 `licenses/`。

## 冻结要求逐项确认

| 项目 | 状态 | 证据 |
|---|---|---|
| 原始 Orb 母版 | 保持 | SHA-256 `daa85a1fc13fac4a7ca562959c2d19172ee0f1e94277c3eeb32ef1f09a1a7b09`，已有验证脚本通过。 |
| 四片水草 Mask | 保持 | B1 `d172b189…143c43`；B2 `3f034488…a4d22`；F1 `52b98859…515d0`；F2 `9a670899…a5fee`；冻结脚本通过。 |
| R06 五条光丝 | 保持 | `OrbRuntimeProfileV1.kt`、`OrbFilamentRenderer.kt`、`OrbFilamentPathMathV1.kt` diff 为空；Profile parity 通过。 |
| Wake/Breath/Release | 保持 | `OrbWakeBreathReleaseV11.kt` diff 为空；冻结参数脚本通过。 |
| R02/R03/R06 数值 | 保持 | `OrbRuntimeProfileV1.kt` diff 为空；新增候选预算独立管理。 |
| 水滴 | 保持 | R02 profile 未改，D1/D2/D3 原位置和尺寸仍由现有渲染器消费。 |
| 13 阶段与模型门禁 | 保持 | controller/frame/decision gate/13 阶段合同/生命周期文件均未修改。 |
| 第二时钟或状态机 | 无 | 新 Shader 时间全部读取同一个 `WaterLightFrame`；没有 Handler、计时器、独立状态机。 |
| API 33 以下 | 静态代码路径保留 | 不实例化 RuntimeShader；原图、Canvas 光学层和既有 R06 路径继续渲染。设备运行未验证。 |
| API 33+ 正常性能 | 已接入候选代码 | 一个链式 RenderEffect：fluid 在内，grass 在外；RuntimeShader 的设备编译和实际观感未验证。 |
| API 33+ 低性能配置 | V1.1 降级 | 继续使用冻结 V1.1 草叶 Shader，沿用 64px/120ms 焦散设置；不创建新增 fluid/grass RuntimeShader。设备运行未验证。 |

## 测试、构建与 APK

- Orb WebP/Mask/R01B/R06 冻结合同、资源解码、Capture/Presentation Raster、生成 profile parity，以及 16+15 个 AGSL/Kotlin uniform 名称静态对应：**PASS**。
- 本地 Gradle/JUnit/AndroidTest/Lint/Debug APK：**NOT_RUN**。环境没有 wrapper/system Gradle/Android SDK/Kotlin 编译器。
- 模拟器、API 28 专项、真机视觉验收：**NOT_RUN**，依照任务约束留给产品负责人。
- `AI_ORB_WATERLIGHT_EXTERNAL_TECH_REUSE_P0_TEST_CASES_V2.md`：记录 T01–T20 的目标、步骤、预期、自动化与设备观察状态。
- `YuJian-AIOrb-Optics-P0-debug.apk`：**NOT_PRODUCED**；没有可报告的 APK SHA-256、大小或构建时间。后续必须从最终精确分支 SHA 构建。

## 尚未完成的交付关口

本地缺少 Android 构建工具，而仓库现有 Android PR 工作流会在构建后触发 API 28 runtime gates；本任务禁止 Work 执行模拟器/API 28 验证。因此此分支尚未提交 PR、合并 Main 或生成 APK。需要在不触发 API 28 runtime gate 的 Android 构建路径上运行上述 Android 任务，或者先为本任务明确可用的 CI-only build 路径；不得把当前静态检查等同于 Gradle、Shader 编译或设备视觉通过。
