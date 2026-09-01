
# Yujian Android｜Detector → Crop → Classifier 数据闭环分析报告

> 分析范围：只盘点现有代码与可复用边界；本报告阶段不修改 Detector、Classifier、Android 推理逻辑或现有 Feedback 语义。
>
> 分析基线：Yujian_App/main，HEAD 64e285cbb67b920c9a28230fbe12155afb5918ed（feat: close Android production recognition UX）。
>
> 最新 Android CI：Run #50，PASS。后续以 GitHub main、最新 CI 和本报告为准。

## 1. 当前真实链路

~~~text
相机 / 相册
  ↓
RecognitionImageStore
  ↓
FishRecognitionPipeline
  ↓
FishDetectorEngine（DET_FISH_v0.1 / ONNX）
  ↓
FishDetectionQualityGate（QUALITY_GATE_v1.1）
  ↓
expanded bbox crop（expand 0.15）
  ↓
FishRecognitionEngine（MODEL_M1_v0.2 / TFLite）
  ↓
RecognitionPrediction（Top-1 / Top-K / confidence / latency）
  ↓
RecognitionResultScreen
  ↓
FeedbackDraft
  ↓
FeedbackRepository（本地队列 + multipart 上传）
~~~

当前 App 已经是 Detector-first 生产链路，不是旧的整图分类链路。后续应增加数据资产记录层和后端接收层，不应重写推理链路。

## 2. 可复用模块清单

| 能力 | 文件 / 类 | 当前输入 | 当前输出 | 复用结论 |
|---|---|---|---|---|
| Detector | app/.../ai/FishDetectorEngine.kt / FishDetectorEngine | EXIF 校正后的 Bitmap | DetectorRun：模型版本、ONNX SHA、416 输入信息、延迟、List<FishDetection> | 直接复用 |
| BBox | FishDetectionQualityGate.kt / NormalizedFishBox、FishDetection | 归一化坐标与置信度 | x1,y1,x2,y2（0–1）、面积比、边缘判断 | 直接复用；Contract 固定 normalized 坐标 |
| Quality Gate | FishDetectionQualityGate.assess() | Detector detections | READY、NO_FISH、UNCERTAIN、MULTIPLE_FISH、INCOMPLETE_FISH、FISH_TOO_SMALL，及 GOOD/WARNING/INVALID | 直接复用 |
| Crop | FishRecognitionPipeline.kt | 原图 + assessment.cropBox | cropPixels=[left,top,right,bottom]、临时 Bitmap | 复用算法；新增持久化 crop_path |
| Crop 参数 | FishDetectionQualityGate.CROP_EXPAND_RATIO | bbox | 固定 0.15 扩边、floor/ceil 像素取整 | 直接复用 |
| Classifier | app/.../ai/FishRecognitionEngine.kt / FishRecognitionEngine | detector crop 或 Bitmap | RecognitionPrediction：模型版本/SHA、Top-1、Top-K、延迟、224 输入图 | 直接复用，不改分类逻辑 |
| Pipeline | FishRecognitionPipeline.recognize() | 原图 Bitmap | ProductionRecognitionResult：status、DetectorRun、assessment、prediction、cropPixels | 作为 Recorder 唯一采集入口 |
| 图片生命周期 | media/RecognitionImageStore.kt | Camera File / Content URI | EXIF 校正、2048px 安全解码、JPEG 归一化、SelectedImage.filePath | 直接复用；在此生成稳定 image_id |
| 识别页 | ui/screens/IdentifyScreen.kt | 相机 / 相册操作 | SelectedImage | 直接复用 |
| 识别中 | ui/screens/RecognizingScreen.kt | recognize() 回调 | 调用 Pipeline 并分流结果/异常 | 只增加成功后 Recorder 调用 |
| 结果页 | ui/screens/RecognitionResultScreen.kt | Prediction + ProductionRecognitionResult | 原图 bbox/crop overlay、crop preview、确认/纠正 | 直接复用数据源 |
| Overlay | ui/screens/DetectorOverlay.kt | Bitmap + detector/crop box | FIT_CENTER 下绘制框 | 直接复用；当前只是展示，不是编辑器 |
| Feedback | feedback/FeedbackRepository.kt / FeedbackDraft | 图片文件 + 用户反馈 | filesDir/feedback_queue 下 JSON/JPG；上传 /api/feedback/ingest | 直接复用队列和传输 |
| Preprocess | app/src/main/assets/preprocess_contract_v1.json | CROP_CLASSIFIER_V1 | 224px RGB、ImageNet normalize、letterbox、padding [124,116,104]、crop expand 0.15 | 与 Trainer contract 对齐 |
| 调试元数据 | ai/InferenceTrace.kt / PipelineContext | Detector/Crop/Classifier 上下文 | 完整文本报告与 Logcat | 复用字段来源；不能代替 JSON Recorder |

## 3. 目标 Contract 映射

### 3.1 DetectionResult

- image_id：当前没有稳定字段；SelectedImage 只有 filePath、bitmap、source。应在图片归一化时生成一次 UUID/稳定文件 ID，并贯穿全链路。
- timestamp：当前没有记录；Recorder 生成 UTC ISO-8601。
- detector_version：FishDetectorEngine.MODEL_VERSION，即 DET_FISH_v0.1。
- image_width/image_height：Bitmap 有真实尺寸；PipelineContext 已记录原图尺寸。
- bbox：NormalizedFishBox(x1,y1,x2,y2)，范围 0–1，不是像素 xywh，必须写入 Contract。
- confidence：FishDetection.confidence。
- bbox_area_ratio：FishDetection.areaRatio 或 assessment.bboxAreaRatio。

结论：DetectionResult 约 80% 已有来源，只缺身份、时间和 JSON 序列化。

### 3.2 CropResult

已有 source-to-crop 关系、assessment.cropBox、扩边参数 0.15、cropPixels 和 crop 宽高。当前缺 source_image_id、crop_path，以及进程结束后可重新读取的 crop 文件。

结论：保留当前 crop 算法，只在 Recorder 阶段将 crop bitmap 写入 app 私有目录，例如 filesDir/yujian/inference/crops/image_id.jpg；不改变送入 Classifier 的内容。

### 3.3 ClassifierResult

RecognitionPrediction 已覆盖：

- model_version → prediction.modelVersion
- species_prediction → prediction.top1.speciesKey
- confidence → prediction.top1.confidence
- latency_ms → prediction.latencyMs

Top-K、model SHA、class index 可作为扩展字段。

### 3.4 FeedbackResult

FeedbackDraft 当前包含：

~~~text
sourceEventId
feedbackType = confirmed / corrected / new_species_candidate
modelVersion
predictedSpecies
confidence
correctedSpecies
userNote
~~~

建议归一化：

- confirmed → is_correct=true
- corrected 或 new_species_candidate → is_correct=false
- hard_case=true 只在用户明确纠正且与 AI 预测不同的时候触发

这仍然只是反馈事实，不是训练真值。现有“纠正进入待审核反馈池，不会直接当作训练真值”边界必须保留。

## 4. 后端现状与可复用能力

### 已有

- POST /api/feedback/ingest：multipart 图片与反馈字段。
- FeedbackEvent：source event、图片 GCS URI、模型版本、预测、置信度、反馈类型、纠正结果和 pipeline status。
- app/flywheel.py::record_feedback()：幂等记录、Species candidate 处理和 Review 前状态。
- 反馈图片现存路径：feedback/app/YYYYMMDD/<event>_<hash>.<ext>。
- trainer/build_detector_dataset.py：已有 GCS 下载、EXIF、bbox 顶点转换、确定性 split、negative 样本和数据集门禁；当前输出 COCO annotations，不是目标 YOLO images/labels。

### 尚不存在

- POST /api/v1/inference/upload。
- App Detector/Crop/Classifier 结果 JSON 持久化。
- app_feedback/detections/YYYY/MM/DD/ 记录路径。
- App record 到 Model Factory 的适配器。
- Detector Error Analyzer。

建议新增接口，不破坏现有 /api/feedback/ingest。旧 API 保持兼容，新 inference API 复用已有认证、GCS 幂等写入和 FeedbackEvent 语义。

## 5. 关键风险与边界

### 5.1 当前 Overlay 不是人工 bbox 标注

DetectorOverlayImage 只绘制 detector/crop 框，没有拖拽、调整、确认 bbox 的编辑能力。

因此：

~~~text
App Detector bbox = 机器候选框
不等于
人工确认的 Detector Ground Truth
~~~

若把 Detector 自己的输出直接转 YOLO 标签，会把漏检、错框和偏框固化为自训练标签。

在“不新增独立标注系统”的前提下，建议：

1. App bbox 先记录为 candidate。
2. 复用现有 Batch/Review/Presence 人工门禁。
3. 只有 accepted bbox/样本才生成 DS_DET_FISH_v0.1 训练标签。
4. 未确认样本只进入 Hard Case / Detector Improvement Task。
5. 不自动 Freeze、训练、改标签。

当前后端有 Presence 人工状态覆盖和 Dataset Freeze 门禁，但没有 bbox 几何 accepted 字段；开发时应落一个最小兼容的审核字段或 evidence 约定，不能把模型框直接冒充真值。

### 5.2 GCS 使用新增前缀

已有 feedback/app/... 不迁移、不改写。新增记录建议：

~~~text
gs://yujian-model-factory-571785698442/
  app_feedback/detections/YYYY/MM/DD/<image_id>.json
~~~

原图、crop 与 JSON 的关系写入 record；使用条件写入和 hash 校验，禁止覆盖不同内容。

### 5.3 用户纠正不能绕过 Review

Feedback 可以产生 Species candidate，但不能直接成为 accepted label。Dataset Freeze 仍是唯一训练集闸门。

## 6. 建议修改计划（确认后执行）

### Phase A｜Android Contract + Recorder

新增建议目录：

~~~text
app/src/main/java/com/yujian/ai/contracts/
  DetectionResult.kt
  CropResult.kt
  ClassifierResult.kt
  FeedbackResult.kt

app/src/main/java/com/yujian/ai/inference/
  InferenceRecord.kt
  InferenceRecorder.kt
~~~

工作内容：

- 不修改 Detector decode、NMS、Quality Gate、Crop 算法或 Classifier preprocess。
- 在 RecognitionImageStore 归一化阶段生成稳定 image_id。
- Pipeline 成功或明确 INVALID 时写一份 InferenceRecord.json。
- 保存原图引用、detector result、crop result、classifier result、quality gate 和时间。
- 用户确认/纠正后，原子更新同一 record 的 user_feedback。
- hard_case 只由用户纠正且不同于 AI 预测触发。
- 继续使用 FeedbackRepository 的离线队列和上传能力。

### Phase B｜Backend Inference Upload Contract

新增 POST /api/v1/inference/upload。

校验 image_id、模型/Detector 版本、Contract 版本、图片类型/大小/SHA、事件幂等和 JSON/图片引用一致性。

新增保存：

~~~text
app_feedback/detections/YYYY/MM/DD/<image_id>.json
app_feedback/detections/YYYY/MM/DD/images/<image_id>.<ext>
app_feedback/detections/YYYY/MM/DD/crops/<image_id>.jpg
~~~

保留现有 Feedback API，避免破坏已上线客户端。

### Phase C｜Detection Dataset Generator

复用 trainer/build_detector_dataset.py 的 GCS 下载、EXIF、split、negative 和门禁逻辑；复用 app.recognition_pipeline.BBox 的坐标约定。

新增适配：

- 输入 app_feedback/detections。
- 过滤 accepted bbox record。
- 输出 DS_DET_FISH_v0.1 的 images/labels 和兼容 manifest。
- YOLO 标签固定为 fish x_center y_center width height。
- candidate、拒绝框、缺失框只进入审计/Hard Case 报告。
- 不自动启动 Detector 训练或 Dataset Freeze。

### Phase D｜Detector Error Analyzer + Intelligence

接入现有 Model Intelligence，不新建平行系统：

- 漏检：人工确认鱼体但 Detector 无有效框。
- 多鱼：Detector 多框而审核确认单鱼。
- 框过大/过小：有 accepted bbox 或可靠几何参照时才判断。
- 错框：预测框与 accepted bbox 的 IoU 低于门限。
- 用户纠正：作为分类 Hard Case，并关联 Detector 状态。
- 输出 Detector Improvement Task，由人工决定是否创建新 Batch。

### Phase E｜测试与验收

Android：Contract 序列化、稳定 image_id、crop 文件、confirmed/corrected/new_species_candidate、hard_case、离线重试。

Backend：upload schema/auth/idempotency/hash、GCS 前缀、非法 record、冲突对象不覆盖。

Dataset：accepted bbox 才生成 YOLO label；坐标、边界、split、negative 和 candidate 隔离。

Intelligence：漏检、错框、过大、过小、多鱼分类；只生成建议，不自动改标签/Freeze/训练。

## 7. 预计 Commit 列表

确认开发后按以下边界逐阶段提交、推送并等待 CI：

1. docs: add Android detector crop pipeline analysis
2. feat: add Android inference contracts
3. feat: add Android inference recorder
4. feat: add inference upload contract to model factory
5. feat: generate reviewed detector dataset from app inference
6. feat: add detector error analyzer to model intelligence
7. test: add detector crop feedback end to end coverage

## 8. Production Pipeline v2 implementation status

The planned analysis has now been implemented without replacing the detector,
quality gate, crop algorithm, classifier or FeedbackRepository:

| Asset | Production contract | Persistence / hand-off |
|---|---|---|
| Original image | `image_id = yj_img_<UUID>` | `filesDir/yujian/inference/YYYY/MM/DD/<image_id>.jpg` |
| Detector output | `candidate_bbox` (normalized xywh) | `DetectionContract`; never a ground-truth label |
| Crop | detector-expanded crop, `expand_ratio=0.15` | `<image_id>_crop.jpg` plus `CropContract` |
| Classifier output | model version, species, confidence, latency | `ClassifierContract` |
| Complete record | `INFERENCE_RECORD_V2` | `<image_id>.json`, atomically written |
| User feedback | confirmed / corrected / new-species candidate | same record; correction marks `hard_case=true` |

The upload transport sends the record, original image and optional crop to the
Model Factory's `/api/v1/inference/upload`. Offline entries stay retryable in
`filesDir/inference_queue`. Backend review must write `accepted_bbox` before a
Detector or Crop dataset builder can consume the image; the App candidate box
is never promoted automatically.

The Android implementation commits are:

1. `ba17c87` — inference contracts
2. `2499b91` — recorder and crop persistence
3. `4cbc323` — authenticated inference upload transport

4. `27a4c872` — feedback identity field and shared preprocess contract

The existing `DET_FISH_v0.1` and `MODEL_M1_v0.2` runtime assets and their UAT
behavior remain unchanged.

执行规则：

~~~text
开发 → 测试 → git commit → git push → CI → 返回真实 SHA
~~~

## 8. 本阶段结论

- 可直接复用：Detector、BBox、Quality Gate、Crop 算法、Classifier、Overlay、Feedback Queue、Backend FeedbackEvent、现有 Detector Dataset 下载/split/门禁逻辑。
- 必须新增：统一 Contract、stable image_id、InferenceRecorder、crop 持久化、新 inference upload API、App record 到 Dataset 适配器、Detector Error Analyzer。
- 必须保持：DET_FISH_v0.1、MODEL_M1_v0.2、现有 Android UAT、Classifier 推理逻辑、Dataset Freeze 状态机和 Feedback 人工 Review 边界。
- Phase A → E 已完成并已推送到 `main`：Contract、Recorder/crop 持久化、上传传输、后端接收、review-gated 数据集适配与 Detector Error Analyzer 均已落地；后续只允许沿现有人工 Review → Dataset Freeze 闸门扩展，不把 candidate bbox 当作训练真值。

