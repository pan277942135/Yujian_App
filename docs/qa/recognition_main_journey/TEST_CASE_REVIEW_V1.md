# Recognition main journey · 用例审查索引

**当前审查结果以 2026-10-17 的 `RECOGNITION_MAIN_FLOW_SPEC_V1` 为准。** 本索引取代先前仅列目录与未补全字段的审查摘要；不声明任何用例已执行。

## 唯一机器可读来源

- 66 条基础用例：[`test_case_catalog_v1.json`](test_case_catalog_v1.json)，版本 `1.2.0`。
- 10 条组合场景：[`e2e_scenarios_v1.json`](e2e_scenarios_v1.json)，仍以同一文件为唯一机器可读来源。
- 测试输入：[`test_inputs_v1.json`](test_inputs_v1.json)，只登记候选图片、受控响应及 Host profile，不代表资源获批。
- V1 逐条审查：[`spec_v1/04_BASE_CASE_REVIEW.md`](spec_v1/04_BASE_CASE_REVIEW.md)。
- 七项不可降级的已批准决策：[`SEVEN_DECISIONS_APPROVED_V1.md`](SEVEN_DECISIONS_APPROVED_V1.md) 与 [`seven_decisions_v1.json`](seven_decisions_v1.json)。

## 当前目录状态

| 状态域 | 数量 | 含义 |
|---|---:|---|
| 模块 | EN 6 / PER 7 / GUIDE 11 / CAP 9 / PROC 9 / RES 10 / NAV 7 / SYS 7 | 合计 66；ID 与主初始状态全部保留 |
| 既有 `design_status=FROZEN` | 7 | PER-04、PER-05、PROC-07、NAV-03、NAV-04、NAV-05、NAV-07 的已批准行为合同 |
| 新增 `review_status=READY_FOR_FREEZE` | 59 | 逐条字段与执行路径已补全，待统一审批冻结 |
| 新增 `review_status=REVIEW_REQUIRED` | 0 | 66 条基础目录当前无其他设计歧义；图片/性能/设备缺口单列，不伪装为已关闭资产或 Runtime |
| `execution_status=NOT_RUN` | 66/66 | 本规范阶段不构建 APK、不运行 Android/Instrumentation 或 66+10 矩阵 |

`design_status` 保留目录 V1.1 的 `DRAFT/REVIEW/FROZEN` 兼容值；规范 V1 的审查进度以新增 `review_status` 为准；执行状态以 `execution_status` 为准。三者互不替代。

## 只读验证边界

`python scripts/validate_recognition_test_case_catalog.py` 只验证 catalog/E2E/input/user 索引及冻结决策的一致性。校验器输出 PASS 只表示目录结构一致，不表示 Build、模型、API28 Runtime、视觉或无障碍 PASS。V1 逐条检查方法名、字段、证据与输入依赖请查阅 `spec_v1/04_BASE_CASE_REVIEW.md`。
