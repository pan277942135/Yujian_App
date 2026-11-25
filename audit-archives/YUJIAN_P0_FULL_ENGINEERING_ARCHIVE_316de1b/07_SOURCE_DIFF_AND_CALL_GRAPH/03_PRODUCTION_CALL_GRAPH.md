# 生产调用链与边界状态

## Photo → Model → Mask → Contour → P* → FishPath

RecognitionImageStore.persistNormalized 解码/归一化来源照片，创建 PhotoIdentity（source bytes SHA256 + canonical pixel SHA256）并附加尺寸/归一化版本；Models/识别管线携带身份；FishDetectorEngine、FishRecognitionEngine、SlimSamSegmenter 产生检测、分类和分割结果；RecognizingScreen 做 Mask 身份/可信度检查并传递几何；WaterLightPathLedger 记录路径事件；RecognitionRevealSessionStore 接受路径快照。

代码已新增身份结构与过期身份拒绝路径及单测。四模型独立 ModelIdentity、输入输出 Receipt 和 PATH_BOUND 控制器与 Store 的原子双端确认还没有完整闭环/设备证据。

## FishPath → Snapshot → Timeline

RecognitionRevealSessionStore 的 commit 路径调用 recognition-domain 的 RecognitionSnapshotCommitter.commitOnce，生成不可变带 ID/hash 的 RecognitionResultSnapshot；RecognitionSceneAnimationController 管理 Timeline/SceneClock。Stage10 的 return 可推进条件已去掉 Result Geometry 和 Glyph Path 等待依赖；Result Geometry 可预挂载准备。真实运行时 Stage10–13.5 顺序未在设备上证明。

## ORB_RETURN → Orb Deconstruction → Glyph

RecognitionSceneAnimationController 与 RecognizingScreen/WaterLightPathLedger 提供返回段事件；Glyph/姓名渲染位于 Result UI。当前 GlyphPath 仍通过已有文本布局/Result 路径生成，没有共享 GlyphPathProvider 的单一生产提供者，路径的预导航准备未完成。

## Glyph → NAME_STABLE

RecognitionGlyphDrawReceipt 保留 Draw Submitted 状态。RecognitionAndroidFramePresentation 在 API 35 请求平台事务，并在 PresentFence signal 后发出平台呈现信号；API 29–34 只能收到 Frame Commit，显式记录 SurfaceFlinger PresentFence 不可用。帧身份与 Glyph 稳定门禁有单测。无 API35 设备验收，无实时 PixelCopy 匹配证据，因此没有视觉/帧呈现 PASS。

## PHOTO_SETTLE → HANDOFF_ACK → SCENE_OWNER_TRANSFER → INFO_ARRIVAL → RESULT_STABLE

YujianApp 根据 Result Handoff substage 选择部分 Gate，并对 Result Host Geometry 单独异步处理；Scene Owner 转移发生于 Consumer ACK 路径。HANDOFF_ACK、SCENE_OWNER_TRANSFER、RESULT_INFO_ARRIVAL、RESULT_SCENE_STABLE 还缺少统一完整的身份绑定 Receipt 契约和端到端生产验收。

## Runtime → Evidence → Independent Validator

RecognitionSessionEvidenceRecorder 记录 checkpoint/错误；RecognitionEvidenceZipExporter 导出并调用 RecognitionEvidenceArchiveVerifier；Orb-to-Glyph 验证脚本检查实际视频文件/时间戳/KF 标记。ZIP 不再生成空文件占位。现有独立 ZIP 校验尚未覆盖所有 Schema、Attempt/Snapshot 连贯性和完整证据引用；视频采集脚本已写但本轮未在设备运行。

## Gate 时钟入口

YujianApp 持有 RecognitionGateDeadlineTracker 和单调时钟唤醒循环；RecognitionSceneAnimationController 的 Stage 事件接入 PHOTO_FIRST_FRAME、ORB_ANCHOR、RELEASE_RENDER、PERIMETER_ARRIVAL_RENDER、CONTOUR_RENDER、FISH_GOLD_RENDER、RETURN_RENDER、ORB_TO_GLYPH_RENDER、GLYPH_REVEAL_NAME_STABLE、RESIDUE_SETTLE_RENDER 等入口；照片、Mask、Path、Result Geometry 和 Handoff 的部分事件通过显式回调接入。剩余 Gate 尚未全部拥有真实生产 start/success/failure producer，必须按 08_UNRESOLVED_ITEMS 继续完成。
