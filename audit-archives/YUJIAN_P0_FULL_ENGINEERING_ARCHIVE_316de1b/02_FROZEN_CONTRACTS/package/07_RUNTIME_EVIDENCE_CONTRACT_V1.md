# 06｜Runtime Evidence Contract V1

**Contract ID:** `YUJIAN_RUNTIME_EVIDENCE_CONTRACT_V1`\
**Priority:** P0 · 独立验收基础

## 1. 目标与只读原则

建立覆盖 Native、模型、Attempt、Stage/KF、Orb-to-Glyph、Result Handoff 和 AUTO 的独立证据链。Evidence 只读订阅业务事件；不得修改业务状态、推进 Stage、提交 Snapshot、补造 Glyph Receipt、生成 Handoff ACK 或控制 Renderer。

## 2. Evidence Identity

每个 Evidence Run 建立 evidenceSessionId，关联一项或多项 Attempt。每个文件/事件必须绑定实际 APK Commit/SHA、ModelBundle SHA、Device Identity、Android API、recognitionSessionId、attemptId、photoId、snapshotId（如已提交）、rendererVersion 和 schemaVersion。不得将不同 APK、模型、照片或 Attempt 混入同一运行身份。

## 3. Unified Event Journal

统一事件至少包含：eventId、eventType、category、sequenceNumber、evidenceSessionId、recognitionSessionId、attemptId、photoId、snapshotId、stageId、kfId、producerId、consumerId、sceneClockMs、motionClockNanos、modelClockNanos、wallClock、status、duration、payloadHash、evidenceRefs、errorCode。

必须保留迟到、乱序、拒绝和重复事件原貌；以单调时间和 sequence number 排序，不得为生成“整齐日志”而删除真实事件。

## 4. Native Memory Profile

分开采集：

- Java Heap；
- Native Heap；
- Process RSS/PSS/Anonymous/File-backed；
- GPU Memory（精确值或估算值必须标注）；
- ORT Session/Encoder/Decoder/Mask 等阶段级内存变化；
- Native Allocation Trace（若设备/工具可用）；
- Session/Embedding/Buffer 释放后观察点。

必须记录测量方法、采样频率、权限/设备能力、profile overhead、峰值和采样置信度。

### 4.1 算子归因

ORT Operator Profile 证明算子执行区间/耗时；heapprofd/native allocator trace 证明分配调用栈/大小。二者只有在可验证的 Session/Run/时间区间或 allocator tag 关联后，才能报告 Operator Allocation Attribution。无法建立关联时标记 `OPERATOR_MEMORY_ATTRIBUTION_INCOMPLETE`，不得用理论 Tensor 大小或 RSS 差值冒充某算子的实际分配量。

### 4.2 Native Termination Trace

记录 CANCEL_REQUESTED、TERMINATION_REQUESTED、NATIVE_CALL_RETURNED、SESSION_CLOSED、EMBEDDING_RELEASED、BUFFER_RELEASED。没有 Native Call 实际返回证据，不得报告终止成功。资源未确认安全前不得标记释放完成。

## 5. Model Inference Trace

Detector、Classifier、Vision Encoder、Mask Decoder 独立记录 model identity、session identity、inferenceId、输入/输出 Tensor 名称/类型/Shape、输入 Hash/摘要、开始/结束时间、duration、provider、ORT Profile ref、内存 Profile ref、输出校验、取消/超时/错误码。不得将大型原始 Tensor/Embedding 写入普通日志。

Encoder/Decoder 必须可独立检索和验证；Mask/Contour/P* 的身份链必须关联当前 Snapshot。

## 6. Stage/KF 证据

Stage Trace 记录 Stage 进入、完成、等待、失败、降级、主动时长、等待时长、输入 Receipt 和输出 Receipt。KF00–KF25 必须始终生成 26 行状态矩阵；每帧状态只能是 CAPTURED、CAPTURE_FAILED、NOT_REACHED、NOT_APPLICABLE 等明确状态。不得将 KF 状态变量改变当作 Frame Presented。

## 7. PixelCopy

对实际适用的 KF，在真实生产 Frame Presented 后请求 PixelCopy。记录 captureId、attemptId、snapshotId、kfId、stageId、frameSequence、source window、尺寸/格式、请求/回调时间、结果码、PNG 路径和 SHA256。

状态区分 REQUESTED、SUCCESS、RETRYING、FAILED、SOURCE_UNAVAILABLE、BUFFER_INVALID、TIMEOUT。失败不得复制前一帧填充。原始 PNG 不得裁剪、缩放、加标注或重编码；分析派生图另存。

KF25 只有真实保存动作触发时才采集；未触发标记 NOT_APPLICABLE。未到达的帧标记 NOT_REACHED。Full 26KF Acceptance 必须明确测试范围并覆盖所有适用检查点。

## 8. Orb-to-Glyph Video

必须提供来自真实生产屏幕的连续视频，至少覆盖 KF14–KF20（Orb Return、Core Stretch、Deconstruction、Glyph Emergence、Name Stable），并保留前后上下文。可使用经授权的 MediaProjection 或设备端 screenrecord 等真实屏幕采集方式；不得用独立 Demo、离屏合成、静态图拼接、人工补帧或后期替换。

记录视频尺寸、Codec、FPS、Frame Count、PTS、Dropped Frames、时长、采集方式、Attempt/Snapshot、KF 时间索引和 SHA256。视频帧必须能与 KF PixelCopy/SceneClock 对齐。

## 9. Result Handoff Evidence

记录 ResultGeometryReceipt、PhotoSettleReceipt、HandoffEnvelope、Consumer ACK、Scene Owner Commit、Info Arrival、Result Stable Receipt。Navigation Requested/Screen Composed 不等于 Handoff Accepted；布局几何不等于屏幕像素；Result Stable 必须关联 KF24 实际证据。

## 10. Evidence ZIP

标准 ZIP 至少包含：

```text
manifest.json
RUN_SUMMARY.md
SHA256SUMS
provenance/{git,apk,model,device}.json
recognition/{attempt,snapshot,photo,model,mask,contour,path}.json
native_memory/{summary,timeline,allocation_trace,ort_profile}/
model_inference/{detector,classifier,encoder,decoder}/
timeline/{scene_clock,stage_trace,kf_trace,kf_matrix,event_journal}/
pixelcopy/{KF00.png...KF25.png,pixelcopy_index.json,alpha_stats}/
orb_to_glyph/{video.mp4,video_index,glyph_path,particle_trace,receipts}/
result_handoff/{geometry,photo_settle,envelope,ack,info,stable}/
tests/{unit,instrumentation,auto,visual}/
failures/{summary,records,root_cause}/
export/{verification,archive_sha256}.json
```

仅生成真实存在的文件；缺失项必须在 Manifest 标记 NOT_RUN/UNAVAILABLE/NOT_REACHED，不得创建空文件冒充证据。

导出顺序：写临时文件→Manifest→SHA256→ZIP→CRC/解压/Schema/内容校验→原子 finalize。ZIP 可大于 20MB；不得为控制体积删除关键 PNG、视频或原始 Profile。遇到 32MiB 上传限制可分片，每片小于限制，记录原 ZIP 和每片 SHA256，重组后复验。

## 11. AUTO Acceptance 分层

分别输出：

- BUILD_PASS
- UNIT_TEST_PASS
- MODEL_ASSET_PASS
- MODEL_INFERENCE_PASS
- NATIVE_PROFILE_PASS
- RECOGNITION_PASS
- KF_RUNTIME_PASS
- PIXELCOPY_PASS
- ORB_TO_GLYPH_VIDEO_PASS
- RESULT_HANDOFF_PASS
- EVIDENCE_ARCHIVE_PASS
- AUTO_SMOKE_PASS
- VISUAL_AUTO_ACCEPTANCE_PASS
- FULL_FLOW_ACCEPTED

KVM/ADB/Emulator/Device/Artifact 可用性属于基础设施状态，不能伪装成产品 PASS 或产品代码 FAIL。

`FULL_FLOW_ACCEPTED` 要求同一 Exact APK/Model/Attempt 完成真实 Recognition、13 Stage、适用 26KF、Orb-to-Glyph、13.1–13.5、Result Stable 和独立证据校验。Native 算子级内存归因单独输出能力状态；不可用时不得宣称 Attribution PASS，但不得把基础设施不可用误报为识别功能失败。

## 12. Failure Classification

至少分类：NATIVE_MEMORY、NATIVE_TERMINATION、MODEL_INFERENCE、RECOGNITION_DATA、TIMELINE、ORB_TO_GLYPH、RESULT_HANDOFF、EVIDENCE_CAPTURE、EVIDENCE_EXPORT、TEST_INFRASTRUCTURE。

每次失败输出 FIRST_FAILURE、lastSuccessfulEvent、firstFailedEvent、Stage/KF、expected/actual、rootCauseConfidence、affectedModule、evidenceRefs、reproduction 和 nextAction。下游连锁失败标记 BLOCKED_BY_UPSTREAM_FAILURE。

根因状态区分 SOURCE_CODE_VERIFIED、RUNTIME_TRACE_VERIFIED、MEMORY_PROFILE_VERIFIED、DEVICE_REPRODUCED、ROOT_CAUSE_CONFIRMED、ROOT_CAUSE_UNCONFIRMED、EVIDENCE_INSUFFICIENT。

## 13. 性能与隐私

Evidence 写盘、压缩、Hash、PixelCopy 和视频封装不得阻塞 Model/Render 主线程。关键事件使用有界队列；溢出必须记录 EVIDENCE_EVENT_BUFFER_OVERFLOW 并标记 INCOMPLETE。Full Profiling 的开销必须测量并与标准模式区分。不得记录 Token、密码或不必要个人信息；原始照片和视频不得未经授权自动上传第三方。

## 14. Independent Validator

Validator 必须从最终 ZIP 文件读取 Manifest、事件、PNG、MP4、JSON、SHA256 和 CRC；不能直接读取 Recorder 内存状态宣布 PASS。缺文件、Hash 错误、身份不一致、时间倒退、KF 覆盖缺失、视频不可解码均必须拒绝对应 Gate。

## 15. Acceptance

Evidence 能独立还原：Exact APK/Model→Attempt→Snapshot→Model Trace/Native Memory→13 Stage/26KF→Orb-to-Glyph Video→Result Geometry/Handoff→Result Stable。所有 PASS 有真实文件/Receipt 支持；所有缺失/失败/未运行状态明确可见。
