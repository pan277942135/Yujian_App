# V2.2 Demo / Android 视觉 QA 记录

## 参考基线

- Demo commit: `a790f145620ab32f96fe525bc38dd902653bd960`
- Demo 源码 SHA-256 与用户提供视频 SHA-256、尺寸、时长在 [`visual-reference/manifest.json`](visual-reference/manifest.json)。
- 两段用户视频和派生联系表未加入仓库；manifest 只记录校验值和媒体元数据。
- 已检查 Demo 最终 JSX / Shader 调用链及用户视频的定性参考画面。没有从 Demo 源项目导出新的静态关键帧；没有 Android 真机或模拟器画面。

## 源码级核对

- 鱼体：实际最终路径绘制 L0–L3；蓝金 Gradient 是连续渐变，金色段沿 contour length 移动；真实 APP Mask Path 仍是唯一鱼体几何。
- 外围：GLES2 同一 pass 中合成照片、三层 perimeter bloom、底部 domain-warped caustics/light spots、16 个节点及底饰；未发现固定横向扫描线。
- 区域：照片局部矩形来自 `RecognitionImageTransform.photoContentRect`；外围几何取 1.5% 边距、短边 12% 圆角。
- 叠加：Shader 先保护原照片并执行 Overlay，节点/珠光作为 Screen 风格的前景 alpha 合成；需要设备实拍确认亮暗照片上的视觉表现。
- 重复层：Processing 不再叠第二张低分辨率 perimeter-caustics Bitmap，Ambient edge field 关闭；其余 Ambient、水草、能量路径、水滴仍保留。

## 对照指标状态

| 项目 | 目标 | 当前证据 | 状态 |
|---|---|---|---|
| 照片边界 | 误差 ≤ 短边 1% | APP 使用 photoContentRect；未做同画面截图差分 | NOT RUN |
| 外围贴边/圆角 | 1.5% / 12% | Profile、path sampler、fragment shader 共用参数 | SOURCE_VERIFIED；实拍未运行 |
| 鱼体几何 | 贴合 Mask、保留鳍尾 | 闭环平滑回退单测与真实 Mask gate | UNIT TEST 等待 exact-head CI；视觉未运行 |
| 线宽/渐变/光晕 | 对齐 Demo 最终可见效果 | L0–L3 结构和 gradient stops 已核对；转 Android density | 设备显示比例未测 |
| 底部焦散/光斑 | 形态接近、中心通透 | 完整 Shader 源码静态迁移 | GLSL runtime 与实拍未运行 |
| 16 节点/底饰 | 数量和空间一致 | 16 节点 profile uniforms、底部 ornaments 代码 | runtime / motion 未运行 |
| Processing→Result | 无重启、身份一致 | handoff / Mask / geometry / Scene Frame 共用 | transition 连续实拍未运行 |

## 明确状态

- `SOURCE_VERIFIED`: PASS
- `UNIT_TEST_PASS`: 由本次 exact-head CI 决定；本地未运行
- `APK_BUILD_PASS`: 由本次 exact-head CI 决定
- `SHADER_COMPILE_PASS`: NOT RUN；CI Kotlin build 不会执行 Android GLES runtime shader compile
- `VISUAL_CAPTURE_PASS`: NOT RUN；未生成 Android 截图
- `DEVICE_VALIDATION`: NOT RUN
- `COORDINATE_MAPPING`: UNVERIFIED；未取得 Demo 实际 CSS width/height、devicePixelRatio 与 canvas backing width/height 元组。Android 侧会保留诊断日志，不会把候选 identity scale 标成测量通过。

不使用伪造 Android 图、不使用不同鱼/不同 Mask 的图片相似度替代对照。真机验收仍需在下载的 exact-head APK 上完成。
