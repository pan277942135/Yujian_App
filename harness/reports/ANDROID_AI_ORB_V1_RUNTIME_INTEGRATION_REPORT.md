# 渔见 AI Orb V1 R02–R06 Android Runtime 集成报告

## 交付范围

本 stacked change 以 PR #304 的资源分支头 `64700592714b8fb1c06b659b9d79c92149d2ea95` 为父提交；目标分支为 `design/ai-orb-existing-asset-gap-closure-v1`。PR #304 保持 Open / Draft，不修改、不合并。新集成 PR 也保持 Draft。

候选渲染接入现有 `AIOrbCore` 与 `ProcessingWaterOrb`。Debug 默认展示 R02–R06 候选，Release 继续使用现有 Legacy Orb 视觉。Home/Capture、PhotoSession、CameraX、Gallery、分类器/检测器、SlimSAM、Mask/BBox、RR09、Scene Clock，以及 R00 核心球体资源都不在本次重构范围。

## Runtime 映射

| 资源 | Android 接入 |
| --- | --- |
| R02 | 三个固定 UV 与独立周期的水滴镜片；复用 `WaterDropletUnitLensGeometry`，未改变照片水滴渲染器的随机行为。 |
| R03 | 固定左上光源和右下折射方向；使用 profile 中的颜色、周期、位移和透明度上限；关闭逐叶水草驱动；新增额外叠加层总透明度预算。 |
| R04 | 仅保留 S1_IDLE / S2_ACTIVE；用同一 `opticalReferenceElapsedMs` 驱动状态过渡，时长为 450ms / 620ms；保留 900ms 相机入口 morph。 |
| R05 | 复用 `WaterCausticsField.sampleCaustic`；双缓冲、尺寸/profile/性能档缓存和 generation guard；后台生成，复用 R00 WebP Alpha 遮罩。 |
| R06 | 导入五条官方路径 G01/G02/A01/A02/C01 的两段三次曲线；按 128 点弧长 LUT 取样，使用单调距离映射、profile 宽度/透明度和前后层；支持 RELEASE/RETURN 连续切向切换及低性能/Reduced Motion。 |

资源 JSON 由 `scripts/android_runtime/generate_orb_runtime_profile.py` 投影为不可变 Kotlin profile；`--check` 比较生成结果和设计源文件，避免手工参数漂移。`AIOrbRuntimeLabActivity` 位于 `src/debug`，调用生产 `AIOrbCore` 并提供状态/尺寸/背景/性能档/光学时钟/内层开关及 PNG 导出入口。

## 离线校验

| 校验 | 结果 |
| --- | --- |
| R02–R06 profile 生成一致性 | PASS |
| R00 WebP：384×384 RGBA、透明范围 0…255、资源副本字节一致 | PASS；SHA-256 `daa85a1fc13fac4a7ca562959c2d19172ee0f1e94277c3eeb32ef1f09a1a7b09` |
| Capture/Processing 基线栅格与 canonical button byte identity | PASS；按钮 SHA-256 `501abcc58a65263dd879ad198a0a033630ab4bdcaf945eb02599e4f1249ca327` |
| Android runtime Harness contract suite | PASS；含基础设施失败分类、测试失败、证据缺失及通过场景 |
| `.github/workflows/android.yml` YAML 解析 | PASS |
| `git diff --check` | PASS |

本地环境没有 Gradle、Android SDK 或 adb，故这里不将 Android 编译、AndroidTest 编译、lint 或 API 28 视觉验收标记为通过。它们由该 stacked PR 的精确 head SHA 工作流执行。

## CI 交付与待回填证据

PR CI 将对 `github.event.pull_request.head.sha` 执行单测、Debug APK 构建、AndroidTest APK 编译、lint、R00 APK 内资源哈希验证及资源 profile parity。独立 API 28 Pixel 2 emulator job 安装同一构建产物中的 Debug APK，启动 Debug Runtime Lab，导出候选/Legacy、S1/S2、68/80dp、浅/深背景、R06 光学时钟、RELEASE/RETURN 和双向状态过渡截图，并上传设备、APK 与光学性能日志。

| 项目 | 最终值 |
| --- | --- |
| 集成 PR / source SHA | CI 首次完成后回填 |
| Gradle unit / AndroidTest compile / lint | CI 首次完成后回填 |
| Debug APK SHA-256 / 下载产物 | CI 首次完成后回填 |
| API 28 screenshot artifact / caustics timing | CI 首次完成后回填 |
| 产品负责人视觉验收 | Pending |

静态离线检查不能替代设备运行、APK 安装或产品视觉签收。最终包交付只对应成功 CI 的精确分支头；视觉签收仍由产品负责人完成。
