# 渔见 AI · 2026-11-03 统一集成测试基线 V1（候选）

- 目标：将近期有效 Android Work 从统一测试分支收口到 Main，统一生成可安装 Debug APK，由产品负责人进行真机视觉和功能验收。
- 集成来源：`integration/yujian-unified-work-qa-20261102`，基线起点 SHA `2c09172cdfd9244abc86b25b51492ccf7e51db24`；当时 Main SHA `10cda91218b1b8d5b1600ef95a9d676b2c040a07`。
- 资源/视觉工作：#304 AI Orb R00–R07 资源、#309 R02–R06 Android Runtime、#308 Lake Glass V2、#294 13 阶段 WaterLight、#310 集成合并、Main 已纳入 #298–#301 首次拍照引导与相机导航、Empty Home W5/3.6s 多层涟漪。
- 本轮基线微修：三条服务器配置统一使用 `https://81.71.1.11`；Gradle 无显式配置时使用相同默认地址；按钮视觉 alpha=1f，交互 enabled 仅控制 click；保留现有同级 zIndex(100f) Hero；Android versionCode 5 / versionName `2.3.2-unified-qa-20261103`。
- First Capture Guide：当前实现为两页 P01/P02，手机保持竖屏；P01 展示横向鱼体，P02 展示竖向鱼体（并非手机横屏教学）。保留 CameraX Preview Ready 门禁及返回/跳过/开始拍摄逻辑。
- 不直接覆盖上传的旧接口版本 `YuJianCaptureButton.kt` / `CaptureEntryButtonHandoff.kt`：其中的 pulsePhase、settleProgress、CaptureButtonOptics(layer) 等当前接口不兼容；仅提取无条件核心 alpha 的可兼容修复，保留分支 580ms Hero / 600ms 水波时序。
- 已核对未完成项：`RecognitionModelWatchdog.execute` 记录真实模型耗时，但未实施 60 秒截止超时和终止失败结果，不得标记完成；#309 说明的 Release R02–R06 仍保留 Legacy 路径，不能把 Debug 预览等同 Release 正式视觉通过。
- 保持独立验收结论：GitHub-hosted 基础构建/单元测试、Debug APK、AndroidTest APK 编译、lint 与资源校验应在精确 SHA 下通过后才允许称为“工程基线通过”；API 28/真机视觉、CameraX 实拍、端侧模型运行与整机流畅度均不由 CI 基础测试替代，明确 NOT_RUN / 待负责人验收。
- 已知 HTTPS 风险：IP 直连可能与 TLS 证书 SAN 不符。Android APK 编译成功不能证明 API 可达；真机联网请求由产品负责人验证，不关闭 HTTPS 证书校验。
- 验收样例：Empty Home 鱼漂/鱼线/涟漪触感；首页到相机按钮固定中心及 580ms 无暗帧；按钮 disabled 视觉不暗；两页 Guide 与 PreviewReady；拍照/相册同真实照片流；13 阶段手动/自动；Mask/无 Mask 与 RR09；Lake Glass 非 Empty Home 页面；接口真实请求与失败提示。
- 基线锁定方式：PR 合并后以 Main 合并提交 SHA、GitHub Actions run_id、APK 文件 SHA-256 和 versionName 四项共同确认。不得引用未经过该提交构建的历史 APK 作为新基线。
