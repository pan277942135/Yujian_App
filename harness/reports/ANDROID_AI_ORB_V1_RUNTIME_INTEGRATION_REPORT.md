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

## CI 交付与视觉验收状态

CI run `41461411691` 对精确 source SHA `1b2dc2704b8aae94f57560d8b5b1af57a13e268d` 完成构建与静态/打包校验。Debug APK 已从该 run 的 `YuJian-debug-1b2dc2704b8aae94f57560d8b5b1af57a13e268d-MODEL_CROP_M1_v0.2` artifact 解包，并核对 ZIP SHA-256 与 GitHub artifact digest 一致。

| 项目 | 最终值 |
| --- | --- |
| 集成 PR / source SHA | [PR #309](https://github.com/pan277942135/Yujian_App/pull/309) / `1b2dc2704b8aae94f57560d8b5b1af57a13e268d` |
| GitHub Actions run | `41461411691` — build `success` |
| Gradle unit tests / Harness / AndroidTest compile / APK 内容校验 / lint | PASS |
| Debug APK | `YuJian_AI_Orb_R02-R06_V1_Debug_1b2dc2704b8.apk`；138,455,370 bytes；SHA-256 `c66a1a537bee6f3f3be150f5ccdac2098033a66c5f39fbafa1ab2898c6b0d511` |
| GitHub Debug APK artifact | `YuJian-debug-1b2dc2704b8aae94f57560d8b5b1af57a13e268d-MODEL_CROP_M1_v0.2`；artifact ID `13149801502`；ZIP digest `sha256:70a6048514678f47db014899d3948b82998dc2d40ff2ec24a86272379a1dc7ea` |
| API 28 screenshot artifact / caustics timing | BLOCKED；视觉 job `125334812682` 失败，未生成截图或光学性能日志 |
| 产品负责人视觉验收 | Pending |

### API 28 设备阻塞

本次 `orb-visual-evidence` 在 GitHub-hosted M1 macOS runner 上安装 Android Emulator 与 API 28 `arm64-v8a` 镜像成功，但 runner 未暴露可用的 Hypervisor.framework。启动日志记录 `HVF error: HV_UNSUPPORTED`、`qemu-system-aarch64-headless: failed to initialize HVF`，随后 Emulator 退出；因此没有安装 APK 或导出截图。

前序 runner 诊断也确认 GitHub-hosted x86_64 runner 缺少 KVM，Emulator 37 拒绝 x86_64 guest 的软件执行；Linux ARM64 hosted runner 则不提供 Android Emulator SDK 包。原仓库 API 28 self-hosted runner 任务曾排队且未获分配。要完成设备验收，需要重新启用带 `/dev/kvm` 的 `[self-hosted, yujian-android, api28]` runner，或提供具备有效硬件虚拟化的 Android API 28 runner，并重跑 exact-head visual job。

静态检查和 APK 构建不能替代设备安装、API 28 截图或产品视觉签收。Release 仍保持 Legacy Orb；候选视觉继续留在 Debug，直至设备证据与产品负责人签收完成。
