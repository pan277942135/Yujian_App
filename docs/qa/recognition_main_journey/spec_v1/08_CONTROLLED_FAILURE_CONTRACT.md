# 08 · 受控响应与异常注入合同

**本文件只定义后续测试 seam 和数据协议；本阶段没有实现 Mock Engine、没有注入生产构建、没有运行任何异常测试。** 权威机器输入 ID 仍登记在 `../test_inputs_v1.json`。`CONTROLLED_*` 的报告单列统计，`real_model_inference=false`。

## 隔离与共通事件 Envelope

```json
{
  "schema_version": "recognition-controlled-injection/1",
  "fixture_id": "RESP-MEDIUM",
  "fixture_version": "1.0.0",
  "injection_id": "unique-per-invocation",
  "injection_target": "RESULT_PRESENTATION_ADAPTER",
  "trigger": "test-host-explicit-command",
  "session_id": "qa-session-id",
  "attempt_id": "attempt-id-or-null-before-attempt",
  "image_id": "image-id-or-null-before-image",
  "requested_event": "terminal-outcome-or-failure-event",
  "expected_outcome": "CONTROLLED_RESULT_OR_CONTROLLED_FAILURE",
  "real_model_inference": false,
  "release_accessible": false
}
```

- 仅可通过 debug/test Host 的显式命令和限定 test source set 注入；Release binary 不得含可由普通 UI、深链、导入文件或隐藏手势触发的 Mock 入口。若依赖编译期测试 seam，应验证 Release APK 中不可达/不可包含。
- Envelope 必须包含唯一 `injection_id`，有关联 Attempt 时必须匹配当前 active ID；PRE-CAPTURE 失败可用 `attempt_id=null`。不允许从文件名或用户动作推断 Mock 状态。
- 所有受控 event 带来源标签 `CONTROLLED_TEST_ONLY`、注入点、注入版本、事件顺序与单调时间戳。Model trace 与受控 trace 分开文件、分开结果集和准确率统计。
- Branch payload 必须通过当前 Frozen Result/page presentation 合同校验。不得在本文件制造看似真实的物种、置信度或模型 latency oracle；数值样例需要明确批准的 fixture 与模型门限规则。
- 注入操作具备一次性消费语义：一个命令最多触发一次，对应一个 Attempt terminal；重复调用必须拒绝或记为重复注入错误。用户明确 Retry 必须创建新 Attempt ID。

## 受控 UI 结果

| 输入 ID | 注入目标 / 触发 | 输出字段要求（逻辑 schema） | 预期 Outcome | Attempt / 恢复 | 可用层级 | 真实模型证据 |
|---|---|---|---|---|---|---|
| `RESP-HIGH` | Result presentation adapter；L2 Host 在已建立 session 后明确提交 | `result_branch=HIGH`; `result_payload` 满足冻结 HIGH Result DTO/page contract；`model_provenance=CONTROLLED` | 显示 HIGH 合同与对应可选操作；此 payload 不证明分类正确 | 绑定当前 attempt；退出后按 Back/Retake contract 清理 | L2 CONTROLLED_UI | 否 |
| `RESP-MEDIUM` | 同上 | `result_branch=MEDIUM`; 至少存在可供当前页面合同展示和用户选择的候选字段；候选值来自受控 DTO，不伪装模型分数 | 未选择前不允许保存；显式选择后进入批准的确认状态 | 一次 Result event；重新加载用新 attempt/session | L2 CONTROLLED_UI、E2E-05 | 否 |
| `RESP-LOW` | 同上 | `result_branch=LOW`; payload 满足低置信度/人工选择页面所需的 Result DTO contract | 必须由用户明确选定 species 后才允许保存 | 同上 | L2 CONTROLLED_UI、E2E-05 | 否 |
| `RESP-NO_FISH` | Result adapter 的输入分支 seam；不可调用真实 Detector | `result_branch=NO_FISH`, `reason_code` 来自冻结枚举；无 `prediction` species claim | 显示 NO_FISH 独立恢复页；不伪造图片或模型 trace | 绑定受控 attempt；Retry 创建新 ID | L2 CONTROLLED_UI | 否 |
| `RESP-WARNING` | Result adapter 的质量分支 seam | `quality_level=WARNING`, `quality_reason` 为合法合同值，实际结果 payload 仍符合可分类规则 | Warning 可继续显示真实/受控的 Result 分支；不得仅因 WARNING 跳到 INVALID | 新 attempt 规则不变；退出无记录 | L2 CONTROLLED_UI | 否 |
| `RESP-INVALID` | Result adapter 的质量分支 seam | `quality_level=INVALID`, `quality_reason` 为合同允许理由，保证 NO_FISH 优先级字段可核验 | 仅当 reason 不为 NO_FISH 时进入 IMAGE_QUALITY_WARNING | 绑定受控 attempt；Retry/Retake 明确动作 | L2 CONTROLLED_UI | 否 |

`result_payload` 字段名、Kotlin DTO 到页面状态 adapter 映射及“MEDIUM/LOW 的合成候选最小值”如不能引用当前冻结页面合同，实施前需与 Result owner 共同固定；不得复制正式模型阈值到 mock fixture 造成测试错误通过。

## 受控失败与迟到事件

| 输入 ID | 注入目标 | 触发 / 事件字段 | Expected Outcome | Attempt ID / 恢复与重试 | 层级 | 模型证据 |
|---|---|---|---|---|---|---|
| `DETECTOR_FAILURE` | Detector adapter seam（错误边界） | `stage=DETECTOR`, `failure_code=DETECTOR_FAILED`, `trigger=explicit_test_host` | TECHNICAL_FAILURE；绝不转 NO_FISH/INVALID | 当前 ID terminal；释放资源；显式 Retry 新 ID | L1 state machine + L2 controlled pipeline | 否 |
| `CROP_FAILURE` | Crop boundary seam | `stage=CROP`, `failure_code=INVALID_CROP`，携带输入 bounds fixture 引用 | TECHNICAL_FAILURE；保留错误类别 | 同上；不重复 dispatch classifier | L1/L2 | 否 |
| `CLASSIFIER_FAILURE` | Classifier adapter seam | `stage=CLASSIFIER`, `failure_code=CLASSIFIER_FAILED` | TECHNICAL_FAILURE；不保留预测 Result | 同上；重试只能由用户发起 | L1/L2 | 否 |
| `MODEL_TIMEOUT` | Attempt coordinator timeout event seam | `stage` 可列明实际阶段；触发 `deadline_elapsed` 的受控时序事件；不在 fixture 中定义产品 timeout 数值 | TECHNICAL_FAILURE / `TIMED_OUT`；迟到 terminal 被忽略 | terminalize-once；取消可取消 job；显式 Retry 新 ID；重试复用图像需 SHA 验证 | L1/L2；真实 deadline 数值另待 PROC-07 参数批准 | 否 |
| `CAMERA_CAPTURE_FAILURE` | Capture adapter/provider error boundary | `capture_error_code` 引用当前 CameraX error contract；最多一个 capture completion/error | CAPTURE_ERROR / 可恢复相机失败；不产生有效 image/Recognition attempt（若 Attempt 已分配则必须 terminalize） | 释放 provider/临时输出；显式 Retry Capture；图库独立 | L2 controlled failure；自然硬件失败需 L3 并另标 REAL_CAMERA | 否（此注入） |
| `IMAGE_DECODE_FAILURE` | 归一化/解码 boundary | `fixture_bytes_id=IMG-08`, `media_type`, `byte_length`, digest、decoder error class | 解码失败早于 Detector，进入技术恢复；不产生 NO_FISH/INVALID | 释放临时文件/Bitmap；有效原图重新输入后新 Attempt | L1/L2 controlled; exact malformed bytes 后续才创建 | 否 |
| `STALE_CALLBACK` | Attempt coordinator callback dispatch boundary | `callback_attempt_id != active_attempt_id` 或该 ID 已 terminal；引用 original event ID | `STALE_CALLBACK_IGNORED`；不改 route/result/persistence/UI；保留当前 active attempt | old ID 不复活；当前 attempt 不被重复 dispatch | L1 state machine + L2 lifecycle | 否 |

## 可验证的隔离断言

1. 每个异常 case 断言 input ID、injection ID、attempt ID、阶段、唯一 terminal count 与资源清理状态。
2. Release 构建中普通用户入口无法选择任何 `RESP-*`/failure ID；安全 Gate 以静态依赖/API 可见性检查及 Release artifact inspection 证明，不以“UI 未展示按钮”代替。
3. 受控结果不写入用户 FishRecord，除非该 case 明确覆盖保存 UI；保存 UI 使用隔离 sink 并记录 `CONTROLLED_SAVE_SINK`，不得改真实 archive。
4. `E2E-05`/`E2E-08` 结果标注 `CONTROLLED_UI`/`CONTROLLED_FAILURE`；不得进入真实推理准确率、成功率、时延或稳定性数据集。
5. `MODEL_TIMEOUT` 事件行为可以受控验证，但 timeout 数值仍为 `PARAMETER_REVIEW_REQUIRED`，运行表现 Gate 不得因此通过。
6. `IMAGE_DECODE_FAILURE` 的字节 fixture、哈希和 decoder 返回证据要到测试实现阶段才产生；本规范阶段不生成 malformed bytes。
