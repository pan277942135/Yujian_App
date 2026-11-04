# 渔见 AI Orb × WaterLight 外部技术复用 V2 — 测试用例

**任务：** `YUJIAN_AI_ORB_WATERLIGHT_EXTERNAL_TECH_REUSE_ANDROID_INTEGRATION_P0_V2`
**基线 Main：** `99986f50944353b400594b4ceb0dd34be1df3b39`
**工作分支：** `feat/p0-ai-orb-external-optics-v2`
**版本：** `versionCode=6`，`versionName=2.3.3-orb-wake-v1.1-20261103`

## 结果标记

- **PASS（静态）**：已执行资源哈希、源码对比或合同检查；不代表设备渲染通过。
- **PASS（自动化）**：自动化测试实际执行并通过。
- **NOT_RUN**：本轮未执行。API 33+ AGSL 运行和真机观感需产品负责人后续验收。
- **FAIL**：执行后未满足预期。

## 执行环境与自动化结果

本工作区没有 `gradlew`、系统 Gradle、Android SDK（`ANDROID_HOME` / `ANDROID_SDK_ROOT`）或 Kotlin 编译器。`./gradlew` 因 wrapper 缺失退出 127；因此本地 Kotlin 编译、JUnit、Lint 和 APK 构建均为 **NOT_RUN**。没有运行模拟器、API 28 专项或真机验证。

已实际执行并通过：

- `python3 scripts/android_runtime/verify_orb_runtime_asset.py`
- `python3 scripts/android_runtime/verify_orb_wake_breath_release_assets_v1_1.py`
- `python3 scripts/android_runtime/generate_orb_runtime_profile.py --check`
- `python3 scripts/verify_capture_presentation_assets.py`
- 对本轮保护文件执行 `git diff --exit-code`，确认苏醒/呼吸/草叶几何/R03/R06/13 阶段控制器与规范没有改动。
- 静态比对两个 AGSL 文件的 `uniform float` 与对应 Kotlin `setFloatUniform`，16 个 fluid 与 15 个 grass uniform 全部匹配。

新加的 `OrbFluidOpticalIntegrationV1Test` 和既有 Android JUnit 套件尚未运行。新增用例的实际结果统一记为 **NOT_RUN**，不会把源码审阅当成运行结果。

## 用例

| ID | 测试目的 | 前置条件与操作 | 预期结果 | 自动化结果 | 真机观察项 | 状态 |
|---|---|---|---|---|---|---|
| T01 | 原始 Orb 资源未变 | 读取 Main 冻结 WebP 并计算 SHA-256 | SHA 为 `daa85a1f…a1a7b09`，尺寸 384×384 | 两项资源校验脚本通过 | 外轮廓、色彩、透明底一致 | PASS（静态哈希） |
| T02 | 四片单叶 Mask 未变 | 对 B1/B2/F1/F2 计算 SHA-256 | 与冻结 SHA 一致 | 冻结资源校验脚本通过 | 四片叶子位置和前后层次一致 | PASS（静态哈希） |
| T03 | 根部固定、叶尖有界 | 执行 `OrbFluidOpticalIntegrationV1Test` 的 envelope / displacement 用例 | 根部 envelope 为 0；每片位移不超过原 V1.1 amplitude | JUnit 未运行 | 叶根稳、叶尖轻摆 | NOT_RUN |
| T04 | 四片草叶共享水流关系 | 以相同 UV/场景时间采样共享低频分量，并执行 V1.2 运动用例 | 共享分量一致；叶片保留原周期和相位 | JUnit 未运行 | 叶片整体随同一水流缓慢摆动 | NOT_RUN |
| T05 | 液态水体连续性 | API 33+ 加载 `orb_fluid_refraction_v1.agsl`，连续观察至少 20 秒 | 双尺度域扭曲缓慢连续，无明显高频噪点 | RuntimeShader 未编译/运行 | 流动克制、无闪烁和跳变 | NOT_RUN |
| T06 | 玻璃边缘折射和 Alpha | 用原图输入 Shader，检查输出 Alpha 与输入 Alpha，并查看透明边缘 | 输出 Alpha 逐像素取自原图；无黑边 | 静态断言已加入；JUnit 未运行 | 轮廓无黑边、无外扩 | NOT_RUN |
| T07 | 折射与草叶合成顺序 | API 33+ 检查 RenderEffect 链：inner=fluid、outer=grass；验证草叶 Mask 排除 | 水体先折射；草叶后 Warp；草叶区域不重复液态扭曲 | 源码链顺序和 Mask 逻辑已审阅；设备未运行 | 无黑矩形、无重复扭叶或叶片重绘 | NOT_RUN |
| T08 | 软域扭曲焦散 | 执行新增采样单元测试并观察 96/64px 焦散纹理 | 低频软折叠、透明度辅助上限 0.035、总响应不超过 R03 0.14 | JUnit 未运行 | 无蜘蛛网亮线、无扫描圈 | NOT_RUN |
| T09 | 固定阳光与预算 | 检查 source U/V、固定方向和光线 alpha 分配 | 光源仍为 0.24/0.20；局部两条光线从既有折射预算中分配 | 源码参数审阅；JUnit 未运行 | 暖金光不旋转、不增曝光 | PASS（静态合同；观感未运行） |
| T10 | D1/D2/D3 水滴 | 对比 `OrbRuntimeProfileV1.kt` 冻结数据 | 数量、位置、尺寸、周期和相位不变 | 参数文件未修改；JUnit 未运行 | 水滴仍为原位置和身份 | PASS（静态差异审计） |
| T11 | 五条 R06 光丝 | 比对路径、LUT、材质与释放实现文件 | G01/G02/A01/A02/C01、路径与回馈运动不变 | 保护文件 diff 为空；profile parity 通过 | 前后层级、头尾衰减正常 | PASS（静态基线） |
| T12 | 单一场景时钟 | 检查 fluid / grass / Canvas 使用相同 `WaterLightFrame` | 不新增 Shader 时钟；时间来自 `frame.elapsedMs` | 静态检查；JUnit 未运行 | 暂停、恢复无相位跳变 | PASS（静态合同） |
| T13 | Pause/Resume 相位连续 | 暂停并恢复处理页面 | 恢复时继续原共享场景相位 | 控制器与帧类未改；运行用例未执行 | 观察叶片、阳光、焦散无重起 | PASS（静态基线；行为未运行） |
| T14 | 新照片任务隔离 | 旧场景生成中提交新照片，检查 session/attempt/image/geometry/generation | 旧 generation 不写入新场景双缓冲 | 原并发隔离代码未改；生命周期测试未运行 | 新照片无旧焦散残留 | PASS（静态基线；行为未运行） |
| T15 | 13 阶段顺序及真实门禁 | 对比 Stage controller/frame 与两份生命周期合同 | 13 阶段顺序和真实数据门禁不变 | 保护文件 diff 为空；测试未运行 | 名称形成后才进入详情 | PASS（静态基线） |
| T16 | 未可信 Mask 不发金光 | 检查 `RecognitionRevealDecisionGate` 与可信状态处理 | 未通过真实门禁时不显示鱼体金光 | 门禁文件未改；测试未运行 | 失败/未可信场景无成功视觉 | PASS（静态基线；行为未运行） |
| T17 | Orb 沿原路径回馈 | 检查原 R06 return movement 和统一运动事件 | 回馈沿既有路径连续反向 | R06 路径实现未改；测试未运行 | 回流无跳点 | PASS（静态基线；行为未运行） |
| T18 | 中文名称使用真实 Glyph | 检查分类结果与 Glyph 绑定路径 | 显示真实分类结果和已有 Glyph | 相关文件未改；测试未运行 | 字形内容、形成过程正确 | PASS（静态基线；行为未运行） |
| T19 | 详情表单出场顺序 | 检查 residue settle、名称形成及详情路由 | 先完成 Orb 回馈和名称形成，再进入详情 | 相关流程文件未改；测试未运行 | 余辉不遮挡详情内容 | PASS（静态基线；行为未运行） |
| T20 | 低性能与 API <33 降级 | 检查系统版本分支及低性能配置；设备上运行 | API <33 不调用 RuntimeShader；仍显示原图及 Canvas 光学层 | 版本分支源码审阅；设备未运行 | 无黑屏、崩溃、透明异常 | PASS（静态分支；设备未运行） |

## 待执行的 Android 命令

获得 Android 构建环境后，在候选分支精确 HEAD 执行：

```bash
gradle :app:compileDebugKotlin
gradle :app:testDebugUnitTest
gradle :app:assembleDebugAndroidTest
gradle :app:lintDebug
gradle :app:assembleDebug
```

本轮没有生成 `YuJian-AIOrb-Optics-P0-debug.apk`，所以 APK SHA、大小和构建时间均为 **NOT_RUN / NOT_PRODUCED**。
