# Native Runtime / Frame Runtime 状态

## ONNX Run 生命周期

RecognitionNativeRunDeadlineGuard 为 ONNX 单次运行建立 RunOptions/终止状态；Detector、SlimSAM Encoder/Decoder 运行路径在超时时调用 setTerminate(true)，并在 Native Run 返回后释放输入输出与共享资源。RecognitionNativeRunDeadlineGuardTest、RecognitionNativeMemoryProfileTest、RecognitionModelWatchdogTest 和 SlimSamRuntimeCoordinatorTest 均包含单测覆盖。

这仍不是“真实 Native 已停止”的设备证据。setTerminate 是向 ONNX Runtime 请求终止；若 Native 不响应，请求协程不能强制结束本机代码。本轮未运行卡死 Native 的可重复实验，也没有独立进程隔离/强杀实现。TFLite 分类器调用不具备相同的 per-run native terminate 路径。不能把 coroutine cancellation 或 terminate request 当成 Native return。

## 资源与 Memory Profile

Native Tensor/Session 的释放等待实际 Native 调用返回，避免在使用中释放资源。Memory Profile 可报告进程级计数和 Native 生命周期状态；没有 GPU 算子级分配与归属数据。GPU operator attribution 未完成。

## Android 帧呈现

RecognitionAndroidFramePresentation 在 API 35 使用 SurfaceControl transaction 的 PresentFence，分开记录提交和实际 fence signal；API 29–34 使用 Frame Commit 并报告 PRESENT_FENCE_UNAVAILABLE。此实现只经编译/单测验证，本轮没有 API35 设备或 SurfaceFlinger 帧呈现验收。

## 视频与设备验收

Orb-to-Glyph MP4 capture/verify 工具与 3 个 verifier 单测通过，但本轮未启动设备录屏、未生成实际 MP4、未检查 KF14–KF20 的实拍视频。状态：DEVICE_RUNTIME_ACCEPTANCE=NOT_RUN；VISUAL_AUTO_ACCEPTANCE=NOT_RUN；FULL_FLOW_ACCEPTED=NOT_RUN。
