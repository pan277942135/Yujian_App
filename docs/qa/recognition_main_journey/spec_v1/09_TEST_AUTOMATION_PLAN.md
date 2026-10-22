# 09 · 自动化测试架构与执行计划

**状态：** Runner 设计，不实现 Runner；66 条和 10 条均未执行。完整 76 条映射见 [`11_REQUIREMENT_CASE_TRACEABILITY.csv`](11_REQUIREMENT_CASE_TRACEABILITY.csv)，机器源仍为主 catalog 与 E2E JSON。

## 分层复核

| 层级 | 当前分配 | 审查与用途 | 限制 |
|---|---:|---|---|
| L1 `STATE_MACHINE` | 2 / 66 | 无 Android Runtime 的状态 reducer、Attempt deadline/late callback 行为 | 不证明权限、CameraX、图片或像素/视觉 |
| L2 `CONTROLLED_UI` | 26 / 66 | Compose 页面状态、Result 交互、受控异常/视觉最小事件时间边界 | 不证明真实模型输出；Gallery/CameraX 不可借 UI 注入冒充 |
| L3 `NATIVE_DEVICE` | 38 / 66 | API28 instrumentation、权限、CameraX capture、全链路/设备系统行为 | 要求精确 APK、设备 profile、运行 trace；不得将模拟输入称为真实相机/模型 |
| E2E | 10 | 按 JSON `execution_contract` 的真实相机、真实模型图库或受控 UI/失败分流 | E2E-05/08 只算受控；06/07 有素材/模型阻塞 |

现有 L1/L2/L3 分布保留；总量满足给定 2/26/38。L3 中 `PHOTO_PICKER` 的路径必须证明 CameraX 调用数为 0；只有 `CAMERA_LIVE` 才用于正向 CameraX E2E。L1/L2 可验证对受控 event 的响应，但不替代 Runtime Gate。

## Runner 接口顺序

1. **QA User Seed**：从冻结 `QA_RECOGNITION_001` manifest 选唯一 initial state；输出 seed receipt，不写 Camera permission。
2. **Environment Reset**：重置限定 debug guest fixture 和上一用例 lease；Host 单独重置设备权限/Activity/System UI。重置失败立即 BLOCKED。
3. **Permission Setup**：按 case 由 Host 设置、观察和保存 CAMERA state/request history；Gallery 场景保持零 Camera permission requirement。
4. **Input Load**：解析 `test_inputs_v1.json` 版本；校验批准 fixture 源 SHA、Git SHA 分开、URI 授权、EXIF、模型/manifest 约束。受控 ID 需由 test-only host 允许列表装载。
5. **Test Execution**：以 planned method 执行一个用例/变体；同一用例的不同设备/权限变体分别有独立 `case_id + variant_id + run_id`。
6. **Assertion Collection**：记录状态转换、UI semantics、平台权限、archive before/after、Attempt/image IDs、时序和 CameraX/模型 trace；原始事实不从截图推断替代。
7. **Evidence Capture**：截图/屏幕录像、Instrumentation 结果、设备 profile、APK/source/model/input SHA、manifest/trace 自洽绑定。敏感原图与身份日志留受控 artifact，不入公开 commit。
8. **Result Classification**：严格区分 PRODUCT_FAIL / CONTRACT_GAP / INFRA_BLOCKED / EVIDENCE_INCOMPLETE / PASS / BLOCKED / NOT_RUN。PASS 只有所有断言与必需证据同 run、同 SHA 满足时。
9. **Cleanup**：释放 CameraX、Attempt Job、Bitmap/文件 lease、controlled injection、状态数据；验证没有未预期 FishRecord。清理失败阻断后续依赖。

## 测试类与方法命名

- 测试类计划：`RecognitionHomeEntryRuntimeTest`, `RecognitionPermissionRuntimeTest`, `RecognitionGuideRuntimeTest`, `RecognitionCameraRuntimeTest`, `RecognitionPipelineRuntimeTest`, `RecognitionResultRuntimeTest`, `RecognitionNavigationRuntimeTest`, `RecognitionSystemAcceptanceTest`；E2E class/method 使用 JSON 的 `planned_test_method`。
- 方法格式：`Recognition{Area}{Runtime|StateMachine}Test#case{CASE_ID_WITHOUT_DASH}{BehaviorVerb}`；保留机器清单中现有完整 `planned_test_method` 字符串作为映射 key，不按需重新编号。
- 本标准阶段登记的是未来计划 method 名，不声称实现了类、方法或测试。每条记录有且只有一个 primary method/人工程序；scenario 可组合其基础 case，E2E method 单独保留。
- L3 必须标明 camera source：`CAMERA_LIVE`、`PHOTO_PICKER` 或相机系统异常；L2 test source 要显式标为 `CONTROLLED_UI`。

## Fixture 与环境规则

- 照片仅从获批 fixture registry 按 ID/version/hash 加载，不以 repo 路径存在作为授权。`source_sha256` 和当前文件字节 hash/Git blob SHA 是不同字段。
- `golden_yellow_catfish_224.jpg` 仅用于其现有分类/模型输入尺度适配回归，不作为全尺寸用户照片或 CameraX source。
- 真实模型 E2E 从模型 Release Contract 和精确 APK 导出 model id、model SHA、tensor/class map SHA；没有与 APK `build_sha` 匹配的 model release trace 则 BLOCKED。受控的 model response 必须标 `real_model_inference=false`。
- CameraX 正向路径需确认 provider 绑定、Preview 流、ImageCapture 输出字节、捕获 trace 的相同 imageId；模拟 CameraX feed 与物理设备摄像头需分别标注。
- 控制故障只由 debug/test source set 的显式 host 注入；普通 Release 入口必须不可达。自然设备失败单独登记，不和 injected failure 混算。
- `L1/L2` 适用于纯逻辑/UI/controlled path；`L3` 是所有 permissions/CameraX/真实模型/gallery/file orientation/设备/系统生命周期断言的计划层级。人工视觉审核仍需对实际截图。

## CI 矩阵与执行序列

| 阶段 / Profile | 用例 | 入口/执行源 | 退出条件 |
|---|---|---|---|
| Static specification preflight | 66 base + 10 E2E + input/user/trace schemas | JSON/YAML/CSV contract validator；只读 | schema/ID/version/reverse-index 无错误；不视为 runtime PASS |
| L1 JVM | 分配的 2 base cases | 无 Android / fake clock event source | reducer 与事件合同断言；数值型 timeout 参数需另行批准 |
| L2 controlled UI | 分配的 26 base cases + E2E-05/08 子路径 | debug-only adapter / state sink | UI/状态/交互/无 mock leakage 合格；标明 controlled |
| API28 Pixel_2 primary | L3 设备路径；G01–G15 所需子集 | API28 Instrumentation；需 profile 证据 | 所有精确 artifact 绑定；若 workflow AVD 实际非 Pixel_2，Pixel_2 目标 BLOCKED |
| API28 CameraX profile | CameraX 主流程、E2E-01/02/03/04/09/10 | workflow `camera-runtime-v1` 或等价批准实体 profile；实际 feed trace | 真实 image/camera trace；不能只靠 recognition-frozen 的 `camera-back none` |
| Gallery / model fixture | CAP/RES orientation、E2E-06/07 | Android Photo Picker + 真实产品模型 | fixture 授权/哈希有效且模型实际输出；不替代 CameraX Gate |
| System variation | SYS-02…SYS-06 | 单独尺寸、旋转、font scale、TalkBack profile | 每个 profile 完整记录；人工检查绑定截图/语义证据 |

现有 `.github/workflows/android.yml` 配有 `recognition-frozen`、`camera-runtime-v1` 等 API28 runtime gates；该配置本阶段不修改。Workflow 选择 AVD 的逻辑不能证明 Pixel_2，必须在每个 run 的基础证据中记录实际 AVD/Build.MODEL/尺寸。部署矩阵仍需产品/QA owner 确认是否将规格矩阵接入现有 gate，不得在文档提交中改变 CI 逻辑。

推荐执行顺序：静态 schema/版本验证 → seed/reset/profile preflight → L1 → L2 → L3 API28 real camera/permissions → real-model Gallery fixtures → E2E 组合 → 视觉/无障碍人工审核 → APK/evidence SHA 一致性 gate。上游 BLOCKED 时跳过依赖用例并写明传播关系，不填 PASS。

## 失败与重试

- 产品断言失败不自动重跑以求绿；保留原始失败证据。
- 仅证据明确证明为 runner/API/安装/AVD/系统 picker 故障时，允许最多一次 infra 重试；保留首次失败 run 和环境分类。第二次运行仍失败则该环境 Gate BLOCKED。
- 设备产品异常不可以“flaky”名义丢弃。若同一 SHA 不稳定，登记 PRODUCT_FAIL/FLAKY_PRODUCT，并冻结证据等待代码/合同审查。
- 测试数据/模型 artifact 不一致、缺 rights、缺权限观察、缺截图/trace 属 BLOCKED/EVIDENCE_INCOMPLETE，不能将测试重跑结果挑选性登记。
- 依赖关系：来源 Home/permission/guide/capture → normalize → pipeline → result → navigation/lifecycle；任一前置未建立时后续断言记 SKIPPED_BLOCKED，不算失败通过。

## 证据目录与文件名

未来受控 CI artifact 目录建议：`recognition-main-flow/v1/<run_id>/<case_id>/<variant_id>/`。每个目录至少含 `result.json`、`assertions.json`、`device_profile.json`、`evidence_manifest.json`，以及按需要的 `screen_<checkpoint>.png`、`camera_trace.json`、`pipeline_trace.json`、`permission_trace.json`、`attempt_events.json`、APK/model/input hash manifest 和人工 `visual_review.json`。

文件名仅用 case/input/profile ID、checkpoint、运行 ID；不写真实人名、账号、地理位置、内部凭证。公开 PR 仅提交已审过的非敏感摘要，原始图像/视频留在受控 CI 工件。Evidence manifest 的 SHA 列明相对路径、字节数、SHA-256、生成工具版本、source SHA、APK SHA；不混淆 Git Blob SHA。

## 测试结果 JSON Schema（规范草案）

设计与执行状态分开：`design_status/review_status` 是规范审批字段；`execution_status` 是实际运行结果；未运行固定为 `NOT_RUN`。

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "title": "Recognition QA Result V1",
  "type": "object",
  "required": ["schema_version", "run_id", "record_type", "record_id", "variant_id", "source_sha", "apk_sha256", "test_user_id", "initial_state", "test_method", "execution_contract", "input_ids", "execution_status", "failure_classification", "assertions", "evidence_manifest_sha256"],
  "properties": {
    "schema_version": {"const": "recognition-qa-result/1"},
    "run_id": {"type": "string", "minLength": 1},
    "record_type": {"enum": ["BASE_CASE", "E2E_SCENARIO"]},
    "record_id": {"type": "string", "pattern": "^(EN|PER|GUIDE|CAP|PROC|RES|NAV|SYS)-[0-9]{2}$|^E2E-[0-9]{2}$"},
    "variant_id": {"type": "string", "minLength": 1},
    "source_sha": {"type": "string", "pattern": "^[0-9a-f]{40}$"},
    "apk_sha256": {"type": "string", "pattern": "^[0-9a-f]{64}$"},
    "android_test_apk_sha256": {"type": "string", "pattern": "^[0-9a-f]{64}$"},
    "model_artifact_sha256": {"type": ["string", "null"]},
    "test_user_id": {"const": "QA_RECOGNITION_001"},
    "dataset_version": {"type": "string"},
    "manifest_sha256": {"type": "string", "pattern": "^[0-9a-f]{64}$"},
    "initial_state": {"enum": ["FIRST_USE", "GUIDE_PENDING", "RETURNING", "PERMISSION_DENIED"]},
    "test_method": {"type": "string", "minLength": 1},
    "execution_contract": {"enum": ["STATE_MACHINE", "CONTROLLED_UI", "DEVICE_NATIVE_UI", "REAL_CAMERA_E2E", "REAL_MODEL_GALLERY", "CONTROLLED_FAILURE", "MANUAL_REVIEW"]},
    "input_ids": {"type": "array", "items": {"type": "string"}},
    "execution_status": {"enum": ["PASS", "FAIL", "BLOCKED", "NOT_RUN"]},
    "failure_classification": {"enum": ["PRODUCT_FAIL", "CONTRACT_GAP", "INFRA_BLOCKED", "EVIDENCE_INCOMPLETE", "NONE"]},
    "assertions": {"type": "array", "items": {"type": "object", "required": ["assertion_id", "status", "observed"], "properties": {"assertion_id": {"type": "string"}, "status": {"enum": ["PASS", "FAIL", "BLOCKED", "NOT_RUN"]}, "expected": {}, "observed": {}}}},
    "evidence_manifest_sha256": {"type": "string", "pattern": "^[0-9a-f]{64}$"},
    "device_profile": {"type": ["object", "null"]},
    "started_at_utc": {"type": "string", "format": "date-time"},
    "finished_at_utc": {"type": "string", "format": "date-time"}
  },
  "additionalProperties": false
}
```

Schema 草案必须由测试 owner 在实现阶段做 JSON Schema 工具验证；结果文件不应进公开仓库，公开 PR 只放经审查汇总。`NOT_RUN` 不是 JSON Schema 的默认值，运行器必须显式输出。

## 66 + 10 用例执行路径映射

完整逐条表包含 66 个基础 Case ID 与 10 个 E2E ID、Test Method、输入、初态、层级/合同、Gate、结果状态、证据：[`11_REQUIREMENT_CASE_TRACEABILITY.csv`](11_REQUIREMENT_CASE_TRACEABILITY.csv)。每行一个唯一主执行方法/验收程序，不存在空 method。
