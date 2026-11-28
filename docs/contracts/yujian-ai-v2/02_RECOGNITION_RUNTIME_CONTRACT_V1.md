# 01｜Recognition Runtime Contract V1

**Contract ID:** `YUJIAN_RECOGNITION_RUNTIME_CONTRACT_V1`\
**Priority:** P0 · 模型运行基础

## 1. 目标与边界

冻结 Detector、Classifier、Vision Encoder、Mask Decoder 的输入输出、运行时、Native 生命周期、内存管理、Session/Embedding 缓存、并发、超时、取消和释放。Runtime 只产出真实模型结果及诊断事实，不负责推进视觉 Stage、生成 Glyph 或导航 Result。

## 2. 模型身份

每个模型独立记录：`modelId`、`modelVersion`、`modelSha256`、`modelFormat`、`inputSchemaHash`、`outputSchemaHash`、`preprocessVersion`、`postprocessVersion`、`runtimeName/version`、`executionProvider`、`sessionId`。模型包身份是四个独立模型身份的组合，不能用单一版本字符串替代。

Detector、Classifier、Vision Encoder、Mask Decoder 的实际输入输出 Schema 必须从本次 APK 内模型文件元数据读取并校验。不得仅凭历史文档或类名推断。

当前已知 SlimSAM Vision Encoder 输入合同为 `pixel_values: FLOAT [batch_size, 3, 1024, 1024]`，其中 batch 维是动态维度。验证器必须接受符号维度，并校验实际 Run 输入；禁止把 ONNX 动态 batch 维硬编码成固定维度后误报模型不兼容。Decoder 的实际 Tensor 名称、类型、Shape 和输出合同必须从目标模型元数据提取，不得猜测。

## 3. Runtime 生命周期

### 3.1 Session

- ORT/TFLite Session 由 Application 级 Model Runtime Manager 创建和管理，不由 Compose 页面持有。
- 页面离开、Activity 重建或 Result 导航不得关闭仍需复用的应用级 Session。
- Session 只有在 Runtime Manager 关闭、进程退出或明确的全局资源策略触发时才释放。
- Session READY 必须满足：模型 SHA 校验、输入/输出 Schema 校验、实际 Session 创建成功、必要的预热/元数据检查成功。
- 任何 Session 创建失败必须记录具体模型、阶段、错误码和原始异常摘要；不能将部分 Session READY 合并成 SlimSAM READY。

### 3.2 Inference Run

每次推理创建唯一 `inferenceId`，绑定 `recognitionSessionId`、`attemptId`、`photoId`、模型身份和输入身份。Run 的输入 Tensor、输出 Tensor、RunOptions、临时 Buffer 均有明确 owner。不得在 Native 仍使用 Tensor/Buffer 时释放或复用它们。

### 3.3 资源所有权

- Session：Application Runtime Manager。
- Embedding：Photo+Encoder Model+Preprocess 维度的缓存管理器。
- Decoder Prompt/Mask：Attempt 级 Runtime。
- Bitmap/Mask Buffer：生产者与消费者之间使用明确引用/Lease；消费者结束后释放。
- Path/Contour：Recognition Geometry 层，不由模型 Session 管理。
- Result UI 不得关闭模型 Session。

## 4. 输入输出合同

每个模型 Run 必须记录：输入身份、Tensor 名称、类型、Shape、预处理版本、开始/结束单调时间、输出名称/类型/Shape、输出摘要、验证状态和错误码。

- Detector：输出候选框/置信度/类别及原始 Photo 坐标变换；必须可追溯到当前 PhotoIdentity。
- Classifier：输出 `speciesKey`、真实 `speciesName`、confidence、模型身份；不负责 Mask 几何。
- Vision Encoder：输入当前标准化 Photo 的像素 Tensor；输出 Embedding，记录 Shape、Type、ByteSize、Hash 和模型身份。
- Mask Decoder：输入当前 Encoder Embedding 与真实 Prompt/Box/Point；输出 Mask Tensor/Mask 文件；必须绑定 Photo、Encoder Embedding、Prompt、Decoder Model 身份。

不允许把 Classifier confidence 当成 Mask quality；不允许把 Detector BBox 当作 Mask/Contour/FishPath。

## 5. 并发与缓存

### 5.1 调度

- Detector 是下游模型分支的前置条件。
- Detector 返回有效 BBox 后，Classifier 与 Vision Encoder 可并行。
- Mask Decoder 必须等待同一 Photo 的有效 Embedding 和有效 Prompt。
- 同一 Photo+Encoder Model+Preprocess 只计算一次 Embedding；并发请求通过同一计算 Future/Lease 合并，不能重复创建大型 Embedding。
- Decoder 的并发数必须受实际内存预算控制；未完成目标设备基准测试前，不允许无界并发。
- 新 Attempt 替换旧 Attempt 时，旧回调不得提交到新 Attempt；是否取消旧 Run 由 Attempt Coordinator 明确决定。

### 5.2 Embedding Cache Key

最少包含：`canonicalPixelSha256 + encoderModelSha256 + preprocessVersion + inputSchemaHash + inputShape`。不得只使用 Bitmap generationId 或 photoId。

### 5.3 缓存生命周期

- Embedding 只在 PhotoIdentity 和 Model/Preprocess 身份完全匹配时复用。
- 换图、模型更新、预处理更新、缓存替换、内存压力、明确清理或进程退出时失效/释放。
- 释放前必须确认没有活跃 Decoder Run 持有该 Embedding。
- 缓存命中/未命中、ByteSize、创建时间、最后使用时间和释放原因必须记录。

## 6. Native 取消、超时和终止

### 6.1 状态严格区分

`RUNNING → CANCEL_REQUESTED → TERMINATION_REQUESTED → NATIVE_CALL_RETURNED → OUTPUT_DISCARDED/ACCEPTED → RESOURCE_RELEASED`。

- CANCEL_REQUESTED 仅表示业务请求取消。
- 对 ORT Run 使用当前版本真实支持的 RunOptions termination 机制；必须记录设置成功与否。
- TERMINATION_REQUESTED 不等于 Native 已终止。
- 只有 Native `Session.run` 实际返回/抛出后，才可标记 NATIVE_CALL_RETURNED。
- Session Close 和 Buffer Release 必须等待所有相关 Native Run 不再使用资源。

### 6.2 Native Call 卡死

同进程线程中断不能被当作可强制终止 Native 算子的保证。达到 deadline 仍未返回：

1. 记录 `NATIVE_CALL_STUCK` 和最后可观测阶段。
2. 隔离当前 Attempt 的结果提交，丢弃迟到回调。
3. 不释放仍可能被 Native 使用的内存。
4. 不复用状态不确定的 Run/Session，按 Runtime Manager 策略 quarantine。
5. 需要硬终止时使用受控独立进程边界或进程重启恢复；不得宣称单纯 Thread.interrupt 已杀死 Native 调用。

### 6.3 60 秒等待合同

识别模型依赖的总等待上限为 60,000ms。Detector/Classifier/Encoder/Decoder 的并行工作使用各自 ModelClock 记录；同一 Attempt 的整体模型依赖门控不得通过串行重置 watchdog 无限延长。到达总 deadline 后，取消所有仍未终态的必要 Run，记录每个模型的实际状态并进入显式失败/降级分支。60s 是异常保护上限，不是正常推理目标，也不计入 6000ms Scene Presentation Clock。

## 7. Attention 与内存管理

必须记录实际模型图、算子、Tensor Shape、Provider、Arena/Memory Pattern 配置和 ORT Profile。对 Attention/MatMul/Softmax 等大张量算子，先确认实际执行图和输入维度，再决定优化。

禁止仅凭理论 Tensor Shape 宣称某算子就是 Native 最大分配者。必须区分：

1. 进程 RSS/PSS/Native Heap；
2. Native allocator 分配/释放及调用栈；
3. ORT Operator Profile 的执行耗时；
4. 能够通过 allocator instrumentation/调用区间关联到具体 Operator 的分配归因。

只有第4类证据成立，才允许报告具体 Operator Allocation Bytes。否则必须报告 `OPERATOR_MEMORY_ATTRIBUTION_INCOMPLETE`。

Arena、Memory Pattern、线程数、并发度、临时 Tensor 复用必须记录实际值；优化前后使用同设备、同 APK/模型、同输入进行对照。不得通过关闭质量门禁、缩小输入或换模型掩盖问题。

## 8. 失败状态

至少包括：`SESSION_CREATE_FAILED`、`MODEL_SCHEMA_MISMATCH`、`INPUT_INVALID`、`OUTPUT_INVALID`、`INFERENCE_TIMEOUT`、`NATIVE_CALL_STUCK`、`TERMINATION_REQUEST_FAILED`、`SESSION_CLOSE_FAILED`、`EMBEDDING_INVALID`、`EMBEDDING_IDENTITY_MISMATCH`、`MASK_OUTPUT_INVALID`、`MEMORY_SPIKE`、`MEMORY_LEAK_SUSPECTED`、`OPERATOR_MEMORY_ATTRIBUTION_INCOMPLETE`。

每个失败绑定模型、Attempt、Inference、输入身份、阶段、时间戳和错误摘要。

## 9. Runtime Acceptance

- 四个模型的实际模型 SHA 与 Schema 可核验。
- Session READY 仅在真实创建和校验成功后发布。
- Encoder/Decoder 可独立推理、独立验证输出和耗时。
- 同照片 Embedding 复用正确，不跨 Photo/Model/Preprocess 污染。
- 并发有界，取消与超时可观测。
- Native Call 返回前不提前释放资源。
- 60s 超时后无迟到结果污染新 Attempt。
- Native Memory 和 ORT Profile 分开记录；没有算子级归因证据时不伪报。
