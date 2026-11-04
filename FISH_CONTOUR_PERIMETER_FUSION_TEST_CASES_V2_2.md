# V2.2 基础测试用例与覆盖映射

测试命令由 `.github/workflows/android.yml` 的 exact-head Android CI 执行：

```bash
gradle -Pandroid.aapt2FromMavenOverride="$YUJIAN_AAPT2" :app:testDebugUnitTest --stacktrace --no-daemon --max-workers=2
gradle -Pandroid.aapt2FromMavenOverride="$YUJIAN_AAPT2" :app:lintDebug --stacktrace --no-daemon --max-workers=2
gradle -Pandroid.aapt2FromMavenOverride="$YUJIAN_AAPT2" :app:assembleDebug --stacktrace --no-daemon --max-workers=2
```

本地没有 Gradle Wrapper、Gradle 安装或 Android SDK，故本地 `./gradlew :app:testDebugUnitTest` 未能启动；该命令的环境失败不计为测试失败或通过。

| ID | 验证项 | 自动化覆盖/证据 | 本次实现状态 |
|---|---|---|---|
| F01–F02 | 真实 Mask、无可信 Mask 不绘制 | `RecognitionMaskContourExtractorTest`、`RecognitionFishFocusTest` | 新增闭环几何回归；精确 HEAD CI 待结果 |
| F03–F06 | 噪声、多闭环、鳍尾、闭环 RDP | `RecognitionMaskContourExtractorTest` 新增尖鳍/尾部/噪声用例；既有洞轮廓和亚像素用例 | 精确 HEAD CI 待结果 |
| F07–F08 | 平滑、拓扑、自交回退 | extractor 每级检查面积、绕向、自交和双向偏差；尖鳍/尾部回归 | 需 CI；算法级量化视觉验收未执行 |
| F09–F10 | EXIF、ROI 与照片空间 | `RecognitionImageTransformTest`、现有 Mask/Reveal policy 测试 | 既有映射逻辑保留；精确 HEAD CI 待结果 |
| F11–F12 | PathMeasure 与缓存边界 | `RecognitionFishFocusTest`；新增 session/attempt/image/hash/mask/contour/geometry 七字段失效断言 | 精确 HEAD CI 待结果 |
| C01–C05 | Under Stroke、渐变、高光、巡航、抗锯齿 | `RecognitionFishFocusTest` 与现有 Material/Contour 测试 | 精确 HEAD CI 待结果；视觉尺寸需设备复核 |
| C06–C09 | APP 光晕、内部反光、阶段连续、Result 连续 | 既有 Ambient、Fish Focus、Reveal policy 测试；handoff 传 Mask version | 未新增 Demo 内部反光；Result 使用轻量承接层，见实现报告 |
| P01–P05 | SDF、边距、圆角、双波、三层 bloom | `RecognitionPerimeterShaderMathTest` 几何/坐标用例；Profile 默认值测试 | 单元测试覆盖数学与配置；GLES 运行时未测 |
| P06–P10 | Caustics、光斑、底部焦散、16 节点、底饰 | 对照源码与 Shader static review；节点数/path sampler 单测 | Shader 编译/实际像素分布未测 |
| P11–P15 | Effect 02/01、Overlay、无扫描线、照片通透 | `RecognitionPerimeterShaderMathTest` 两 profile 与 lifetime；Shader 源码审计 | 单元/源码层可核验；屏幕视觉未测 |
| P16 | Processing / Result 连续 | 共用 Scene Frame、相同 `maskVersion` 和 Result 几何；同阶段 expansion/time 投影 | 集成实现；设备连续播放未测 |
| T01–T06 | 第一帧起钟、13 阶段、最短时长、MANUAL/AUTO、模型门禁 | `RecognitionSceneFrameTest`、`RecognitionVisualStateControllerTest`、`RecognitionAttemptDeadlineTest`、Reveal gate 测试 | 沿用冻结实现；精确 HEAD CI 待结果 |
| T07–T12 | 60 秒 attempt、Attempt 隔离、共用时钟、RETURN | `RecognitionAttemptDeadlineTest`、`WaterLightFrameTest`、`WaterLightEnergyTest`、`RecognitionVisualStateControllerTest` | 未改模型期限/状态顺序；精确 HEAD CI 待结果 |

## 运行结果记录

- Local unit tests: BLOCKED（缺少 Gradle/Android SDK；不是测试失败）
- Exact-head Unit Test: 以 PR #316 本次 Android CI 的 `Unit tests` job 为准
- Lint / Assemble Debug: 以同一 workflow run 的 `Android lint` / `Assemble debug APK` jobs 为准
- Shader runtime compile: NOT RUN（GLES 编译发生于 Android runtime）
- Device / API 28 / 视觉捕获: NOT RUN（本任务不要求 API 28 门禁）
