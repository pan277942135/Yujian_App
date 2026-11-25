# 未完成事项与明确阻塞

## 远端发布 / 可下载链接

- 已成功用非强制 push 将 Commit 316de1b705fb54ee7d6e2114259ec1f87773aedf 推送至 PR #346 原分支；远端 refs/pull/346/head 同步为该 SHA，Main 未变化。
- Release 创建尝试：tag yujian-debug-316de1b7。GitHub API 在检查 Release 是否存在时返回 HTTP 403 Forbidden；gh auth status 显示当前 GH_TOKEN 无效。没有创建 Release、tag 或资产，不能提供直接 APK URL。
- 下一步：恢复可访问 pan277942135/Yujian_App Releases API 的有效 GitHub 授权后，创建上述 Release 并上传 APK、SHA256、build-metadata.json；再从远端 Release 下载回三个资产并复算 hash。
- PR API 同样 403，故 PR Draft/Open 状态未能读取。没有执行会改动 Draft 的操作。当前分支和 PR head refs 已读回最终 SHA，Main ref 仍为原值。

## 模型来源核验

- scripts/verify_production_model.py 失败于 “invalid asset id for fish_classifier_v0_2.tflite”。随包 model_release_contract.json 中 Release asset 行缺少脚本所需 id 等字段。
- 作为区分，APK 包内 classifier、metadata、class map、tensor contract bytes 的 SHA256/size 均与随包快照匹配；四份原始 model asset 未被本 Work 修改。
- 下一步：从授权可读的模型 Release 元数据补齐可信 asset provenance，或由代码所有者决定并审查明确区分本地资产与远端 Release 来源的 Snapshot schema；在此之前不宣称 strict Release provenance PASS。

## 17 项逐项剩余风险

- P0-01：双 Hash 类型和主生产数据路径已改；尚无生产设备 Trace 证明 Model/Mask/Contour/P*/FishPath/Result 每个消费者都绑定相同 canonical hash。
- P0-02：Attempt 级 Snapshot commit-once 和单测已加入；真实多消费者/迟到回调设备验收未运行。
- P0-03：Draw/Frame Commit/PresentFence 状态区分已实现；Android 29–34 不提供 PresentFence；API35 fence 和可见 Glyph Receipt 未真机核验。
- P0-04：ONNX RunOptions terminate 请求、资源等待与单测已加入；未证明卡死 Native 会返回，TFLite 没有同等 native terminate；必要时需进程隔离。
- P1-01：Embedding cache identity 和 lease 已更新并过单测；无真实多模型设备回放。
- P1-02：模型 watchdog/trace 有部分扩展；Detector、Classifier、Mask、Encoder/Decoder 的独立输入/输出身份与 Receipt 仍未完整。
- P1-03：FishPath Snapshot Store 接受路径已有实现；Controller 与 Store 的 PATH_BOUND 双端原子确认/失败收敛仍不完整。
- P1-04：KF25 可选 Save 语义调整并通过单测；未做真实保存交互录屏验收。
- P1-05：Result Geometry 与部分 Handoff Receipt 已处理；完整 HandOff ACK、唯一 Scene Owner、Result Info/Stable 证据集合仍缺。
- P1-06：Evidence 等待已从 SceneClock 推进中解耦；实际长时间 PixelCopy/ZIP/视频采集不阻断 AUTO 尚未在设备验证。
- P1-07：Native memory profile 是进程级；GPU 算子级内存/所有权数据无真实来源，未实现。
- P1-08：不再生成空占位证据，增加 ZIP independent verifier；attempt/snapshot/schema/hash/视频索引的完整校验契约尚未完成。
- P1-09：增加捕获/验证工具且 3 个脚本测试通过；未在设备产生 KF14–KF20 MP4。
- P1-10：FIRST_FAILURE 记录入口已接入；module、confidence、evidence refs、last-success 和全部 causal blocked edges 未齐全。
- P1-11：Stage10 不等待 Result Geometry/Glyph Path 的源码条件已移除；旧 fixture 不是本 SHA 的实机复现，Stage10–13.5 全链未运行。
- P1-12：Exact-HEAD Unit Tests/AndroidTest 编译/Debug APK 通过；严格模型 Release provenance 失败，GitHub Release/附件受授权阻塞。
- P1-13：共享 GlyphPathProvider 与从 committed Snapshot 预导航生成尚未实现，仍由现有 Result 文本布局生产路径提供 Glyph geometry。

## 本轮设备验收状态

DEVICE_RUNTIME_ACCEPTANCE=NOT_RUN  
VISUAL_AUTO_ACCEPTANCE=NOT_RUN  
FULL_FLOW_ACCEPTED=NOT_RUN

未运行 Android Instrumentation/Emulator、真机识别、API35 SurfaceFlinger PresentFence、PixelCopy、MP4 采集或 Stage10–13.5 全流程。574 个 JVM 单测和 APK 构建不能替代这些验收。
