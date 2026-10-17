# 05 · 10 条组合场景逐条审查

> 权威执行定义：`docs/qa/recognition_main_journey/e2e_scenarios_v1.json`。本报告逐条解释机器可读路径；报告不会取代 JSON。全部 `execution_status` 保持 `NOT_RUN`。

## 运行类型与审查状态

| 类型 | 用途 | 能够证明 | 不能证明 |
|---|---|---|---|
| `REAL_CAMERA_E2E` | 真实 CameraX + 产品 Detector/Crop/Classifier | CameraX 实际帧、捕获产物和真实模型事件链 | 固定某个模型分类结果；必须按实际输出判读 |
| `REAL_MODEL_GALLERY` | 系统相册输入 + 产品真实模型；不是 CameraX 捕获 E2E | 获批原图经系统 Photo Picker 和实际模型处理后的实际输出 | CameraX 捕获行为；任何被替换/预设的模型输出 |
| `CONTROLLED_UI` | 受控 Result 响应；只验证 UI / 用户决策 | 受控 Result 分支、输入、人工选择及保存边界 UI | 真实模型成功率、准确率或相机链路 |
| `CONTROLLED_FAILURE` | 受控故障或生命周期事件；不作为模型识别正确性证据 | 显式测试接口注入的失败、取消、生命周期所有权行为 | 真实模型故障频率/性能；普通 Release 可触发的故障入口 |

## 逐条路径与准入判定

### E2E-01 · 首次安装→权限→P01/P02/P03→真实拍照→真实结果→返回

- **执行类型：** `REAL_CAMERA_E2E`；真实 CameraX + 产品 Detector/Crop/Classifier。
- **用户 / 初态：** `QA_RECOGNITION_001` / `FIRST_USE`。
- **输入：** `DEVICE_FRESH`, `CAMERA_LIVE`；模型/输入版本要求：Actual model SHA/version must be read from the exact APK/build manifest and logged for each run.
- **基础用例：** `EN-01`, `EN-02`, `PER-01`, `PER-02`, `GUIDE-01`, `GUIDE-02`, `GUIDE-03`, `GUIDE-04`, `GUIDE-07`, `CAP-03`, `PROC-01`, `NAV-04`。
- **标准审查：** `READY_FOR_FREEZE`；**执行：** `NOT_RUN`。

| 检查点 | 页面/所有者 | 操作 | 预期状态与可观察断言 |
|---:|---|---|---|
| 1 | Empty Home | Install/reset debug guest seed FIRST_USE; launch | `EMPTY_HOME`；FishRecord count=0; CAMERA currently denied; guide=false; no permission request before camera intent |
| 2 | Capture | Tap camera entry | `PERMISSION_REQUIRED`；One Android CAMERA request; no duplicate prompt |
| 3 | Capture/Guide | Grant permission and wait CameraX Ready | `CAMERA_READY → GUIDE.P01`；Provider bound + Preview STREAMING + ImageCapture ready precede P01; shutter count=0 |
| 4 | First-use Guide | Next P01→P02→P03; tap Start | `GUIDE.COMPLETED → CAMERA_READY`；P01/P02/P03 order and page semantics; completed=true; Start does not shoot |
| 5 | CameraX/Processing | Capture one live frame and run production pipeline | `CAPTURING → CAPTURE_SUCCESS → CAPTURED → actual pipeline`；One capture output; normalized source decodes; real detector/pipeline events bind image ID and Attempt ID |
| 6 | Result/Recovery | Observe actual terminal output and tap Result/Recovery Back | `actual outcome → BACK_TO_SOURCE_HOME`；Do not assume High/species; actual model or real no-fish/quality result is shown; technical exception is failure; Back returns Empty Home |

**场景变体：**

- Permission granted on first prompt
- Model may emit any real supported non-technical outcome; classification branch is observed, never preselected

**事件时序：**

- `clock`：monotonic runtime event timestamps; stage minima from Processing Frozen contract only
- `minimum_visual_beats_ms`：CAPTURED ≥220ms; DETECTING ≥320ms; OUTLINE ≥350ms; CLASSIFYING ≥250ms
- `model_timeout`：PROC-07 configured parameter only; not assigned from these visual minima
- `ordering`：True

**可观察断言：**

- One real capture and inference with genuine device trace
- Render actual model result without assuming High
- Result top Back returns origin Empty Home with zero unsaved fish records
- Every checkpoint is bound to one run_id, source_sha, apk_sha256, test_user state and image/response input ID.
- A scenario can pass only from the observation type allowed by its execution_contract.

**通过条件：**

- All complete_path checkpoints satisfy their objective state and observable assertions.
- No unexpected FishRecord is created; expected count is zero unless a separately authorized Save boundary is explicitly in scope.
- All evidence hashes/IDs are internally consistent and execution_contract is honored.

**阻塞条件：**

- Runner lacks usable CameraX image source
- No actual model terminal trace or only a controlled response exists
- Camera fixture is unreadable or no output file is produced



**执行证据：** source_sha、apk_sha256、device_profile、camera_capture_trace、model_pipeline_events、native_screenshots、fish_record_count、test_user_id_and_state、seed_receipt_and_manifest_sha256、input_image_sha256_or_controlled_fixture_sha256、model_artifact_sha256_or_controlled=true、checkpoint_state_trace、navigation_trace、result_classification、run_id。

**测试方法：** `RecognitionFirstUseRealCameraE2ETest#firstRequestGuideCaptureActualInferenceAndReturn`。
**真实设备与模拟边界：** CameraX and production model must both emit traces.

### E2E-02 · 返回用户不重复引导的完整复访流程

- **执行类型：** `REAL_CAMERA_E2E`；真实 CameraX + 产品 Detector/Crop/Classifier。
- **用户 / 初态：** `QA_RECOGNITION_001` / `RETURNING`。
- **输入：** `CAMERA_LIVE`；模型/输入版本要求：Actual model SHA/version must be read from the exact APK/build manifest and logged for each run.
- **基础用例：** `EN-05`, `CAP-01`, `CAP-03`, `PROC-01`, `NAV-06`。
- **标准审查：** `READY_FOR_FREEZE`；**执行：** `NOT_RUN`。

| 检查点 | 页面/所有者 | 操作 | 预期状态与可观察断言 |
|---:|---|---|---|
| 1 | Empty Home | Seed RETURNING and verify environment | `EMPTY_HOME`；CAMERA granted by Host; guide_completed=true; no records |
| 2 | Capture | Enter camera and wait readiness | `CAMERA_INITIALIZING → CAMERA_READY`；No system permission dialog; no first-use guide; shutter enabled only after Ready |
| 3 | Processing | Capture one live image and run real model | `CAPTURING → Processing → actual outcome`；Unique capture and Attempt ID; detector/classifier trace actual and exact model SHA/version recorded |
| 4 | Navigation | Return Home, re-enter, capture again | `Result Back → EMPTY_HOME → new Capture`；Second entry does not show guide; second attempt ID/image ID differs; no stale Result; unsaved record count remains zero |

**场景变体：**

- First returning session
- After one prior unsaved recognition and Home return

**事件时序：**

- `clock`：monotonic runtime event timestamps; stage minima from Processing Frozen contract only
- `minimum_visual_beats_ms`：CAPTURED ≥220ms; DETECTING ≥320ms; OUTLINE ≥350ms; CLASSIFYING ≥250ms
- `model_timeout`：PROC-07 configured parameter only; not assigned from these visual minima
- `ordering`：True

**可观察断言：**

- 已授权且完成指引不重弹
- 一次识别后可返回再进入
- Every checkpoint is bound to one run_id, source_sha, apk_sha256, test_user state and image/response input ID.
- A scenario can pass only from the observation type allowed by its execution_contract.

**通过条件：**

- All complete_path checkpoints satisfy their objective state and observable assertions.
- No unexpected FishRecord is created; expected count is zero unless a separately authorized Save boundary is explicitly in scope.
- All evidence hashes/IDs are internally consistent and execution_contract is honored.

**阻塞条件：**

- No genuine CameraX feed
- Any outcome was supplied through controlled response instead of real model



**执行证据：** source_sha、apk_sha256、device_profile、camera_capture_trace、model_pipeline_events、native_screenshots、fish_record_count、test_user_id_and_state、seed_receipt_and_manifest_sha256、input_image_sha256_or_controlled_fixture_sha256、model_artifact_sha256_or_controlled=true、checkpoint_state_trace、navigation_trace、result_classification、run_id。

**测试方法：** `RecognitionReturningUserRealCameraE2ETest#repeatEntrySkipsPermissionAndGuide`。
**真实设备与模拟边界：** CameraX and production model must both emit traces.

### E2E-03 · 拒绝权限→设置恢复→拍照

- **执行类型：** `REAL_CAMERA_E2E`；真实 CameraX + 产品 Detector/Crop/Classifier。
- **用户 / 初态：** `QA_RECOGNITION_001` / `FIRST_USE`。
- **输入：** `SYSTEM_PERMISSION`, `CAMERA_LIVE`；模型/输入版本要求：Actual model SHA/version must be read from the exact APK/build manifest and logged for each run.
- **基础用例：** `PER-01`, `PER-03`, `PER-04`, `PER-06`, `CAP-01`。
- **标准审查：** `READY_FOR_FREEZE`；**执行：** `NOT_RUN`。

| 检查点 | 页面/所有者 | 操作 | 预期状态与可观察断言 |
|---:|---|---|---|
| 1 | Host + Empty Home | Reset permission history and seed FIRST_USE | `EMPTY_HOME`；Host confirms CAMERA denied and request flags known; app data clean |
| 2 | Capture | Tap camera; deny the first prompt | `PERMISSION_REQUIRED → PERMISSION_DENIED`；No provider/capture; Gallery and Back remain available |
| 3 | Capture | Enter again; verify no unsolicited repeat; test one explicit retry in requestable variant | `PERMISSION_DENIED`；Request count changes only after explicit tap; false rationale alone is not labeled permanent |
| 4 | System Settings | Tap Open Settings; grant CAMERA; return | `PERMISSION_DENIED → FOREGROUND recheck → CAMERA_INITIALIZING`；Intent targets current package; grant is read from OS after resume; no grant inferred from settings launch |
| 5 | CameraX | Wait ready, capture and invoke production pipeline | `CAMERA_READY → CAPTURING → actual pipeline`；One real capture; no capture occurred before grant; actual pipeline trace if result route is included |
| 6 | Recovery controls | Repeat denied/no-prompt variant; test Gallery and Back | `PERMISSION_DENIED → Gallery or EMPTY_HOME`；Settings remains available; Photo Picker works without CAMERA; Back returns source Home |

**场景变体：**

- Requestable after one denial
- USER_FIXED/non-requestable state confirmed by Host flags
- Return from Settings without granting
- Return from Settings after granting

**事件时序：**

- `clock`：monotonic runtime event timestamps; stage minima from Processing Frozen contract only
- `minimum_visual_beats_ms`：CAPTURED ≥220ms; DETECTING ≥320ms; OUTLINE ≥350ms; CLASSIFYING ≥250ms
- `model_timeout`：PROC-07 configured parameter only; not assigned from these visual minima
- `ordering`：True

**可观察断言：**

- Denial does not permit capture
- System requestability determines explicit retry versus app Settings recovery
- Gallery/Back remain accessible and Settings return checks actual permission
- Every checkpoint is bound to one run_id, source_sha, apk_sha256, test_user state and image/response input ID.
- A scenario can pass only from the observation type allowed by its execution_contract.

**通过条件：**

- All complete_path checkpoints satisfy their objective state and observable assertions.
- No unexpected FishRecord is created; expected count is zero unless a separately authorized Save boundary is explicitly in scope.
- All evidence hashes/IDs are internally consistent and execution_contract is honored.

**阻塞条件：**

- Host cannot prove permission request history/state
- Settings UI not available on the target image
- Permission is assumed from shouldShowRequestPermissionRationale=false without Host/system observation



**执行证据：** source_sha、apk_sha256、device_profile、camera_capture_trace、model_pipeline_events、native_screenshots、fish_record_count、test_user_id_and_state、seed_receipt_and_manifest_sha256、input_image_sha256_or_controlled_fixture_sha256、model_artifact_sha256_or_controlled=true、checkpoint_state_trace、navigation_trace、result_classification、run_id。

**测试方法：** `RecognitionPermissionRecoveryE2ETest#denyThenRecoverThroughSettingsAndCapture`。
**真实设备与模拟边界：** CameraX and production model must both emit traces.

### E2E-04 · 首次引导跳过并拍照

- **执行类型：** `REAL_CAMERA_E2E`；真实 CameraX + 产品 Detector/Crop/Classifier。
- **用户 / 初态：** `QA_RECOGNITION_001` / `GUIDE_PENDING`。
- **输入：** `CAMERA_LIVE`；模型/输入版本要求：Actual model SHA/version must be read from the exact APK/build manifest and logged for each run.
- **基础用例：** `GUIDE-01`, `GUIDE-02`, `GUIDE-08`, `CAP-03`。
- **标准审查：** `READY_FOR_FREEZE`；**执行：** `NOT_RUN`。

| 检查点 | 页面/所有者 | 操作 | 预期状态与可观察断言 |
|---:|---|---|---|
| 1 | Empty Home/Capture | Seed GUIDE_PENDING and wait CameraX Ready | `EMPTY_HOME → CAMERA_READY → GUIDE.P01`；No guide before Preview Ready; capture count=0 |
| 2 | Guide | Tap Skip | `P01 → COMPLETED → CAMERA_READY`；Completion true exactly once; shutter/picker request counts remain 0 |
| 3 | CameraX/Processing | Tap shutter once; capture and run actual pipeline | `CAMERA_READY → CAPTURING → actual pipeline outcome`；Exactly one live capture; actual production model; no result branch preselected |
| 4 | Return | Use Result Back (if result is produced) | `actual outcome → Empty Home`；NAV-04 source Home; no record unless an explicit save action was intentionally excluded |

**场景变体：**

- Skip from P01; skip is not a shutter action

**事件时序：**

- `clock`：monotonic runtime event timestamps; stage minima from Processing Frozen contract only
- `minimum_visual_beats_ms`：CAPTURED ≥220ms; DETECTING ≥320ms; OUTLINE ≥350ms; CLASSIFYING ≥250ms
- `model_timeout`：PROC-07 configured parameter only; not assigned from these visual minima
- `ordering`：True

**可观察断言：**

- 跳过前快门触发数零
- 跳过后持久化 completed=true
- Every checkpoint is bound to one run_id, source_sha, apk_sha256, test_user state and image/response input ID.
- A scenario can pass only from the observation type allowed by its execution_contract.

**通过条件：**

- All complete_path checkpoints satisfy their objective state and observable assertions.
- No unexpected FishRecord is created; expected count is zero unless a separately authorized Save boundary is explicitly in scope.
- All evidence hashes/IDs are internally consistent and execution_contract is honored.

**阻塞条件：**

- No camera feed or capture/inference trace is controlled rather than production



**执行证据：** source_sha、apk_sha256、device_profile、camera_capture_trace、model_pipeline_events、native_screenshots、fish_record_count、test_user_id_and_state、seed_receipt_and_manifest_sha256、input_image_sha256_or_controlled_fixture_sha256、model_artifact_sha256_or_controlled=true、checkpoint_state_trace、navigation_trace、result_classification、run_id。

**测试方法：** `RecognitionGuideSkipRealCameraE2ETest#skipGuideThenCaptureOnce`。
**真实设备与模拟边界：** CameraX and production model must both emit traces.

### E2E-05 · Medium/Low 结果受控 UI 分支

- **执行类型：** `CONTROLLED_UI`；受控 Result 响应；只验证 UI / 用户决策。
- **用户 / 初态：** `QA_RECOGNITION_001` / `RETURNING`。
- **输入：** `RESP-MEDIUM`, `RESP-LOW`；模型/输入版本要求：NOT_REAL_MODEL_EVIDENCE; controlled fixture/injection manifest is mandatory.
- **基础用例：** `RES-02`, `RES-03`。
- **标准审查：** `READY_FOR_FREEZE`；**执行：** `NOT_RUN`。

| 检查点 | 页面/所有者 | 操作 | 预期状态与可观察断言 |
|---:|---|---|---|
| 1 | Controlled Result Host | Seed RETURNING; load RESP-MEDIUM | `MEDIUM`；Fixture manifest says controlled=true; candidate list is visible but none preselected; persistence calls=0 |
| 2 | Medium Result | Tap Save/Continue before confirming; cancel selector; then explicitly choose a species | `MEDIUM → SPECIES_SELECTOR_MEDIUM → MEDIUM → SPECIES_CONFIRMED`；No persistence before explicit choice; cancel preserves branch; selection required |
| 3 | Controlled Result Host | Load RESP-LOW with a fresh state; edit metadata and tap Save | `LOW → SPECIES_SELECTOR_LOW_MANUAL`；Requested destination/draft preserved; no save callback before selected species |
| 4 | Low Result | Select species and verify one controlled save intent; stop at save boundary | `LOW_MANUAL → SPECIES_CONFIRMED → SAVE_IN_PROGRESS (controlled sink)`；Sink gets one request; production guest archive remains unchanged; do not count as model evidence |

**场景变体：**

- RESP-MEDIUM: tap a candidate vs cancel vs explicit choose-other
- RESP-LOW: Save Catch vs Continue Memory; selection then save callback

**事件时序：**

- `clock`：monotonic runtime event timestamps; stage minima from Processing Frozen contract only
- `minimum_visual_beats_ms`：CAPTURED ≥220ms; DETECTING ≥320ms; OUTLINE ≥350ms; CLASSIFYING ≥250ms
- `model_timeout`：PROC-07 configured parameter only; not assigned from these visual minima
- `ordering`：True

**可观察断言：**

- 分别注入 Medium 和 Low Outcome
- 未经人工确认不得自动保存
- Every checkpoint is bound to one run_id, source_sha, apk_sha256, test_user state and image/response input ID.
- A scenario can pass only from the observation type allowed by its execution_contract.

**通过条件：**

- All complete_path checkpoints satisfy their objective state and observable assertions.
- No unexpected FishRecord is created; expected count is zero unless a separately authorized Save boundary is explicitly in scope.
- All evidence hashes/IDs are internally consistent and execution_contract is honored.

**阻塞条件：**

- Any controlled response is reported as real model evidence
- Controlled sink writes production FishRecord or duplicate save callback occurs



**审查说明：** 受控 UI 验证而非真实模型端到端。

**执行证据：** source_sha、apk_sha256、controlled_injection_manifest、assertion_trace、test_user_id_and_state、seed_receipt_and_manifest_sha256、input_image_sha256_or_controlled_fixture_sha256、model_artifact_sha256_or_controlled=true、checkpoint_state_trace、navigation_trace、result_classification、run_id。

**测试方法：** `RecognitionResultControlledE2ETest#mediumAndLowRequireExplicitSpeciesConfirmation`。
**真实设备与模拟边界：** Controlled response/failure is not production-model evidence.

### E2E-06 · 真实无鱼图识别

- **执行类型：** `REAL_MODEL_GALLERY`；系统相册输入 + 产品真实模型；不是 CameraX 捕获 E2E。
- **用户 / 初态：** `QA_RECOGNITION_001` / `RETURNING`。
- **输入：** `IMG-05`；模型/输入版本要求：Actual model SHA/version must be read from the exact APK/build manifest and logged for each run.
- **基础用例：** `CAP-09`, `PROC-08`, `RES-04`。
- **标准审查：** `REVIEW_REQUIRED`；**执行：** `NOT_RUN`。

| 检查点 | 页面/所有者 | 操作 | 预期状态与可观察断言 |
|---:|---|---|---|
| 1 | Asset preflight | Verify IMG-05 source/rights/scene label and SHA | `NOT_RUN until approved fixture exists`；No existing file is called an approved no-fish photo; fixture source and authorization independently reviewed |
| 2 | Gallery/Capture | Seed RETURNING; open Photo Picker and select approved IMG-05 | `EMPTY_HOME → Gallery → CAPTURE_SUCCESS`；Camera permission/camera capture is not required; record picker URI provenance and normalized image SHA |
| 3 | Production Model | Run actual Detector and quality gate using pinned model artifact | `CAPTURED → DETECTING → actual detector result`；Only actual output can establish NO_FISH; record detector model SHA/version, event trace, input SHA and quality reason |
| 4 | No Fish Result | If and only if actual gate returns NO_FISH, inspect recovery and Back | `NO_FISH → BACK_TO_SOURCE_HOME or explicit retry`；No species/save record; return target source Home; zero FishRecord |

**场景变体：**

- Primary: independently reviewed, rights-approved natural no-fish image; source annotation must be distinct from model output.
- If actual product output is not NO_FISH, preserve the output and classify against the approved scene contract; do not inject RESP-NO_FISH.

**事件时序：**

- `clock`：monotonic runtime event timestamps; stage minima from Processing Frozen contract only
- `minimum_visual_beats_ms`：CAPTURED ≥220ms; DETECTING ≥320ms; OUTLINE ≥350ms; CLASSIFYING ≥250ms
- `model_timeout`：PROC-07 configured parameter only; not assigned from these visual minima
- `ordering`：True

**可观察断言：**

- 只有真实 Detector 判为 NO_FISH 才满足结果
- 不采用 Mock 冒充真实推理
- Every checkpoint is bound to one run_id, source_sha, apk_sha256, test_user state and image/response input ID.
- A scenario can pass only from the observation type allowed by its execution_contract.

**通过条件：**

- All complete_path checkpoints satisfy their objective state and observable assertions.
- No unexpected FishRecord is created.
- For the approved real no-fish scene, actual production Detector/quality route is NO_FISH; a different actual output is PRODUCT_FAIL against an approved target, never a substituted response.
- All evidence hashes/IDs are internally consistent and execution_contract is honored.

**阻塞条件：**

- IMG-05 missing, source/rights unapproved, or independent no-fish annotation absent
- No production model terminal trace or exact model artifact identity is available
- Any mocked result, expected-response substitution or forced label is present; evidence is invalid

**审查说明：** 需获取版本锁定的黄金无鱼输入并核验结果稳定性。

**执行证据：** source_sha、apk_sha256、device_profile、camera_capture_trace、model_pipeline_events、native_screenshots、fish_record_count、test_user_id_and_state、seed_receipt_and_manifest_sha256、input_image_sha256_or_controlled_fixture_sha256、model_artifact_sha256_or_controlled=true、checkpoint_state_trace、navigation_trace、result_classification、run_id、gallery_import_trace、camera_capture_count_zero、camera_capture_trace。

**测试方法：** `RecognitionNoFishRealModelGalleryE2ETest#actualNoFishPhotoUsesProductionDetector`。
**真实设备与模拟边界：** Gallery/model only: capture trace must prove zero CameraX requests.

### E2E-07 · 模糊图质量异常

- **执行类型：** `REAL_MODEL_GALLERY`；系统相册输入 + 产品真实模型；不是 CameraX 捕获 E2E。
- **用户 / 初态：** `QA_RECOGNITION_001` / `RETURNING`。
- **输入：** `IMG-06`；模型/输入版本要求：Actual model SHA/version must be read from the exact APK/build manifest and logged for each run.
- **基础用例：** `CAP-09`, `RES-05`。
- **标准审查：** `REVIEW_REQUIRED`；**执行：** `NOT_RUN`。

| 检查点 | 页面/所有者 | 操作 | 预期状态与可观察断言 |
|---:|---|---|---|
| 1 | Asset/model preflight | Verify IMG-06 original image source, rights, blur evidence and current model/gate version | `NOT_RUN until fixture and gate qualification`；Human blur label alone is not a measured INVALID result; current quality gate uses detector geometry/eligibility and has no standalone blur metric identified in source audit |
| 2 | Gallery | Select approved full-resolution IMG-06 through system Photo Picker | `EMPTY_HOME → Gallery → normalized image`；No CameraX requirement; record original/normalized SHA, dimensions and decode trace |
| 3 | Production Model | Run actual detector and quality gate; do not override response | `CAPTURED → DETECTING → actual gate outcome`；Record actual quality level/reason, detector boxes, model SHA/version and whether classifier was eligible |
| 4 | Image Quality recovery | Enter only if actual output is INVALID and reason is not NO_FISH | `INVALID → IMAGE_QUALITY_WARNING`；Exact Frozen generic quality copy; if actual output is NO_FISH, route NO_FISH; if actual output remains eligible, do not force Image Quality |

**场景变体：**

- Primary: rights-approved blur sample with independent, objective input-quality annotation; do not assume an INVALID product response.
- If Product/Model owners approve a blur outcome contract and the actual quality gate returns INVALID, verify the real recovery path; otherwise keep the target BLOCKED/CONTRACT_GAP.

**事件时序：**

- `clock`：monotonic runtime event timestamps; stage minima from Processing Frozen contract only
- `minimum_visual_beats_ms`：CAPTURED ≥220ms; DETECTING ≥320ms; OUTLINE ≥350ms; CLASSIFYING ≥250ms
- `model_timeout`：PROC-07 configured parameter only; not assigned from these visual minima
- `ordering`：True

**可观察断言：**

- Only actual quality-gate INVALID with an approved non-NO_FISH reason can enter image-quality recovery.
- Before the blur oracle is approved, this scenario cannot PASS; after approval, a different actual result is PRODUCT_FAIL and is not replaced.
- Every checkpoint binds one run_id, source_sha, apk_sha256, test_user state, input ID and exact model/gate artifact.
- A scenario can pass only from the observation type allowed by its execution_contract.

**通过条件：**

- All complete_path checkpoints satisfy their objective state and observable assertions.
- No unexpected FishRecord is created.
- After a measurable blur contract and fixture are approved, the actual quality gate returns INVALID for a permitted non-NO_FISH reason; mismatch is PRODUCT_FAIL.
- All evidence hashes/IDs are internally consistent and execution_contract is honored.

**阻塞条件：**

- IMG-06 missing, source/rights unapproved, or blur scene measurement absent
- Blur acceptance/quality-gate contract remains unapproved; target oracle is not frozen
- No actual production quality-gate trace or exact model/gate artifact identity is available
- A forced RESP-INVALID or human-only blur label is used as model evidence; evidence is invalid

**审查说明：** 图像质量 INVALID 不能借用 PROC-09 的技术故障断言；必须使用真实质量门证据确认目标分支。

**执行证据：** source_sha、apk_sha256、device_profile、camera_capture_trace、model_pipeline_events、native_screenshots、fish_record_count、test_user_id_and_state、seed_receipt_and_manifest_sha256、input_image_sha256_or_controlled_fixture_sha256、model_artifact_sha256_or_controlled=true、checkpoint_state_trace、navigation_trace、result_classification、run_id、gallery_import_trace、camera_capture_count_zero、camera_capture_trace。

**测试方法：** `RecognitionInvalidRealModelGalleryE2ETest#actualQualityGateInvalidUsesQualityRecovery`。
**真实设备与模拟边界：** Gallery/model only: capture trace must prove zero CameraX requests.

### E2E-08 · 识别故障注入后恢复

- **执行类型：** `CONTROLLED_FAILURE`；受控故障或生命周期事件；不作为模型识别正确性证据。
- **用户 / 初态：** `QA_RECOGNITION_001` / `RETURNING`。
- **输入：** `PIPELINE_FAILURE`, `CAPTURE_FAILURE`；模型/输入版本要求：NOT_REAL_MODEL_EVIDENCE; controlled fixture/injection manifest is mandatory.
- **基础用例：** `PROC-09`, `CAP-08`。
- **标准审查：** `READY_FOR_FREEZE`；**执行：** `NOT_RUN`。

| 检查点 | 页面/所有者 | 操作 | 预期状态与可观察断言 |
|---:|---|---|---|
| 1 | Controlled Failure Host | Seed RETURNING and choose one failure ID | `EMPTY_HOME → controlled image handoff`；Manifest names exact injection layer and failure code; controlled=true; no Release entry point can select Mock |
| 2 | Pipeline adapter | Inject Detector, Crop or Classifier exception; run one variant at a time | `PROCESSING stage → TECHNICAL_FAILURE`；Actual injected stage and Attempt ID recorded; no model species or NO_FISH/INVALID outcome |
| 3 | Technical Recovery | Tap explicit Retry with retained image or Retake/Gallery | `TECHNICAL_FAILURE → fresh Attempt/Capture action`；Old Attempt ID invalidated; retry creates new ID; one failure does not create a record |
| 4 | Optional verification run | If a real pipeline retry is run, report it as a separate real-model subrun | `new actual outcome or new technical error`；Do not attribute controlled first outcome to model accuracy; timeout case remains blocked until numeric policy is approved |

**场景变体：**

- DETECTOR_FAILURE
- CROP_FAILURE
- CLASSIFIER_FAILURE
- MODEL_TIMEOUT only after PROC-07 timeout parameter approval
- CAPTURE_FAILURE only in Capture subvariant

**事件时序：**

- `clock`：monotonic runtime event timestamps; stage minima from Processing Frozen contract only
- `minimum_visual_beats_ms`：CAPTURED ≥220ms; DETECTING ≥320ms; OUTLINE ≥350ms; CLASSIFYING ≥250ms
- `model_timeout`：PROC-07 configured parameter only; not assigned from these visual minima
- `ordering`：True

**可观察断言：**

- 注入明确技术故障
- 无假鱼种，用户可安全重试
- Every checkpoint is bound to one run_id, source_sha, apk_sha256, test_user state and image/response input ID.
- A scenario can pass only from the observation type allowed by its execution_contract.

**通过条件：**

- All complete_path checkpoints satisfy their objective state and observable assertions.
- No unexpected FishRecord is created; expected count is zero unless a separately authorized Save boundary is explicitly in scope.
- All evidence hashes/IDs are internally consistent and execution_contract is honored.

**阻塞条件：**

- Mock can be triggered through ordinary Release UI
- Controlled and real outcomes share one accuracy/result population
- Retry reuses old Attempt ID or automatically repeats without user action



**审查说明：** 故障注入场景不是正常真实模型成功识别。

**执行证据：** source_sha、apk_sha256、controlled_injection_manifest、assertion_trace、test_user_id_and_state、seed_receipt_and_manifest_sha256、input_image_sha256_or_controlled_fixture_sha256、model_artifact_sha256_or_controlled=true、checkpoint_state_trace、navigation_trace、result_classification、run_id。

**测试方法：** `RecognitionTechnicalFailureRecoveryE2ETest#injectedStageFailureIsDistinctAndRetryable`。
**真实设备与模拟边界：** Controlled response/failure is not production-model evidence.

### E2E-09 · 处理中返回/重新进入

- **执行类型：** `CONTROLLED_FAILURE`；受控故障或生命周期事件；不作为模型识别正确性证据。
- **用户 / 初态：** `QA_RECOGNITION_001` / `RETURNING`。
- **输入：** `CAMERA_LIVE`；模型/输入版本要求：NOT_REAL_MODEL_EVIDENCE; controlled fixture/injection manifest is mandatory.
- **基础用例：** `NAV-03`, `NAV-06`, `NAV-07`。
- **标准审查：** `READY_FOR_FREEZE`；**执行：** `NOT_RUN`。

| 检查点 | 页面/所有者 | 操作 | 预期状态与可观察断言 |
|---:|---|---|---|
| 1 | CameraX/Processing | Seed RETURNING; capture one real CameraX image; hold a controlled pipeline event at a chosen phase | `CAPTURING → PROCESSING active`；Camera capture itself is real; detector/classifier event sequence is controlled and never claimed as real-model evidence |
| 2 | NAV-03 Back | Tap Processing Back once; deliver the old callback afterward | `PROCESSING → CANCELLED → CAMERA_READY`；Old Attempt terminal before late callback; callback ignored; no Result/navigation/record |
| 3 | Lifecycle recoverable variant | Re-enter Processing with a controlled active owner and recreate Activity | `ACTIVITY_RECREATED → ATTEMPT_RECOVERABLE`；Same owner/Attempt ID; exactly one invocation; rebind observation only |
| 4 | Lifecycle owner-loss variant | Destroy owner; retain image or delete it, then foreground | `ATTEMPT_LOST → explicit retry (new ID) or IMAGE_LOST → Retake`；No time threshold; file existence/SHA determines retry eligibility; no automatic inference |

**场景变体：**

- User Back plus late callback
- Activity recreation with same owner still live
- Owner lost and image verified
- Owner lost and image missing/corrupt

**事件时序：**

- `clock`：monotonic runtime event timestamps; stage minima from Processing Frozen contract only
- `minimum_visual_beats_ms`：CAPTURED ≥220ms; DETECTING ≥320ms; OUTLINE ≥350ms; CLASSIFYING ≥250ms
- `model_timeout`：PROC-07 configured parameter only; not assigned from these visual minima
- `ordering`：True

**可观察断言：**

- Processing Back invalidates current attempt and routes Capture
- Stale model callback cannot auto navigate Result or save a record
- Background/Activity recreation safely reattaches same attempt or offers explicit Retry/Retake
- Every checkpoint is bound to one run_id, source_sha, apk_sha256, test_user state and image/response input ID.
- A scenario can pass only from the observation type allowed by its execution_contract.

**通过条件：**

- All complete_path checkpoints satisfy their objective state and observable assertions.
- No unexpected FishRecord is created; expected count is zero unless a separately authorized Save boundary is explicitly in scope.
- All evidence hashes/IDs are internally consistent and execution_contract is honored.

**阻塞条件：**

- A controlled delayed callback is labeled real model output
- Same attempt is dispatched twice
- Restore is based only on background elapsed seconds



**执行证据：** source_sha、apk_sha256、device_profile、camera_capture_trace、model_pipeline_events、native_screenshots、fish_record_count、test_user_id_and_state、seed_receipt_and_manifest_sha256、input_image_sha256_or_controlled_fixture_sha256、model_artifact_sha256_or_controlled=true、checkpoint_state_trace、navigation_trace、result_classification、run_id。

**测试方法：** `RecognitionLifecycleControlledE2ETest#backAndRecreationNeverDuplicateAttempt`。
**真实设备与模拟边界：** Real CameraX capture and controlled pipeline events are mixed in separate checkpoints; never claim the injected pipeline is real model evidence.

### E2E-10 · 同用户连续识别两次

- **执行类型：** `REAL_CAMERA_E2E`；真实 CameraX + 产品 Detector/Crop/Classifier。
- **用户 / 初态：** `QA_RECOGNITION_001` / `RETURNING`。
- **输入：** `CAMERA_LIVE`；模型/输入版本要求：Actual model SHA/version must be read from the exact APK/build manifest and logged for each run.
- **基础用例：** `CAP-04`, `NAV-06`, `RES-10`。
- **标准审查：** `READY_FOR_FREEZE`；**执行：** `NOT_RUN`。

| 检查点 | 页面/所有者 | 操作 | 预期状态与可观察断言 |
|---:|---|---|---|
| 1 | Empty Home | Seed RETURNING and record initial IDs/count | `EMPTY_HOME`；Permission granted; guide completed; record count=0 |
| 2 | First run | Real CameraX capture + production model run; do not save | `CAMERA_READY → Attempt A → actual result/recovery`；Capture A and model events bind exact input/model; result can be any actual supported branch |
| 3 | Return | Use Result top/System Back to source Home | `actual outcome → EMPTY_HOME`；Old result/image/attempt is cleared; no record created |
| 4 | Second run | Capture a new live frame and run production model again | `EMPTY_HOME → Capture B → Attempt B → actual outcome`；B image ID and Attempt ID differ from A; each run has one invocation; no stale callback crosses runs |
| 5 | Final state | Inspect guest archive and session cleanup | `EMPTY_HOME or active actual result per explicit test stop`；Unsaved two runs still create zero records; no duplicate record or stale result |

**场景变体：**

- Both results unsaved
- Attempt A returns recovery; Attempt B independently starts from Capture

**事件时序：**

- `clock`：monotonic runtime event timestamps; stage minima from Processing Frozen contract only
- `minimum_visual_beats_ms`：CAPTURED ≥220ms; DETECTING ≥320ms; OUTLINE ≥350ms; CLASSIFYING ≥250ms
- `model_timeout`：PROC-07 configured parameter only; not assigned from these visual minima
- `ordering`：True

**可观察断言：**

- 两次有独立 Capture/Inference Trace，未保存时鱼获仍零
- Every checkpoint is bound to one run_id, source_sha, apk_sha256, test_user state and image/response input ID.
- A scenario can pass only from the observation type allowed by its execution_contract.

**通过条件：**

- All complete_path checkpoints satisfy their objective state and observable assertions.
- No unexpected FishRecord is created; expected count is zero unless a separately authorized Save boundary is explicitly in scope.
- All evidence hashes/IDs are internally consistent and execution_contract is honored.

**阻塞条件：**

- Attempt B reuses Attempt A image/Attempt/result
- Controlled responses stand in for production inference
- Any record is created without explicit successful save



**执行证据：** source_sha、apk_sha256、device_profile、camera_capture_trace、model_pipeline_events、native_screenshots、fish_record_count、test_user_id_and_state、seed_receipt_and_manifest_sha256、input_image_sha256_or_controlled_fixture_sha256、model_artifact_sha256_or_controlled=true、checkpoint_state_trace、navigation_trace、result_classification、run_id。

**测试方法：** `RecognitionRepeatedCaptureRealCameraE2ETest#twoRunsUseIndependentImagesAndAttempts`。
**真实设备与模拟边界：** CameraX and production model must both emit traces.

## 专项审查结论

- **E2E-05 / E2E-08：**受控响应或故障，只能验证交互和恢复合同，不得报作真实模型成功识别。
- **E2E-06：**IMG-05 缺失且未获批；测试只有在真实模型对获批图像实际返回 NO_FISH 时才能对目标断言 PASS。若获批无鱼 scene contract 存在而模型实际返回其他分支，分类为 PRODUCT_FAIL；若目标合同本身尚未批准，分类为 CONTRACT_GAP/BLOCKED。不能置换响应。
- **E2E-07：**IMG-06 缺失；当前被审代码没有独立模糊度指标合同，因此“模糊”只能是经审查的输入属性，不能预设 `INVALID`。完成 blur oracle 审批后，只有被测产品实际输出 INVALID 且原因/事件证据符合合同才通过；目标未冻结时保持 CONTRACT_GAP/BLOCKED，批准后实际分支不匹配记 PRODUCT_FAIL。
- **E2E-09：**必须使用真实 CameraX 输入证明捕获链路，但流水线暂停/迟到回调、Activity recreation 与所有权恢复由隔离的受控测试接口注入；不得把受控回调称为真实模型事件。
- 所有路径均为计划定义，`execution_status` 是 `NOT_RUN`，没有运行证据或 PASS。
