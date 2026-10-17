# Recognition Main Flow Specification V1 · 收口报告

**任务编号：** RECOGNITION_MAIN_FLOW_SPEC_CLOSURE_V1  
**状态：** REVIEW_REQUIRED  
**规范包：** docs/qa/recognition_main_journey/spec_v1/  
**分支：** docs/recognition-main-flow-spec-v1  
**目标基线：** 最新远端 main 经核实为 0d3238e8a41b87363e9a17b04c9cc45fca4c426d  
**Draft PR：** 待创建并回填 URL  
**运行状态：** 本阶段无 Android Build、Instrumentation、API28 Runtime 或模型运行；所有 66 基础用例、10 E2E 和 G01–G15 执行状态均 NOT_RUN。

## 标准包结论

完成了从业务合同到用例/资产/运行 Gate 的 V1 规范包。统一状态机含 38 个状态、32 个转换；DEV-01…04 四项核心差距均有目标行为、代码范围、用例与 Gate；全部 66 个基础 Case ID 保留并逐条补齐标准字段；10 条 E2E 全部具有路径、检查点、执行模式、输入/模型绑定、证据、方法与 PASS/BLOCKED 条件；测试用户和执行架构、标准图片与异常数据、G01–G15、追踪 CSV 及后续工作清单已建立。

最终判定为 REVIEW_REQUIRED，暂不声明 SPEC_READY_FOR_DEVELOPMENT。原因是 E2E-07 的模糊质量目标没有可引用的 blur 指标/阈值产品合同，实际质量门审计未找到对应独立模糊指标，且 E2E-06/07 所需真实输入未获批或不存在。为避免把测试设计做成预设模型输出，E2E-06/07 的真实分支保持按实际模型结果验收；E2E-07 需 OD-01 owner review 后才能冻结目标 oracle。其他资源、超时、设备身份和控制面问题均已登记责任人/影响/关闭条件，详见 14。

## 完成统计

| 项目 | 结果 |
|---|---|
| 状态与转换 | 38 个 PAGE_ID/STATE_ID 状态对象；32 条统一转换；状态图引用该唯一 YAML |
| 核心业务差距 | DEV-01 Result Back；DEV-02 Permission；DEV-03 Model timeout/Retry；DEV-04 Lifecycle restore，均为规格合同，不改源码 |
| 基础用例 | 66/66 保留 ID 并逐条审查；7 FROZEN；59 READY_FOR_FREEZE；66 NOT_RUN |
| 组合场景 | 10/10 详细审查；8 READY_FOR_FREEZE；E2E-06/07 REVIEW_REQUIRED；10 NOT_RUN |
| 图片输入矩阵 | 8 项：2 CANDIDATE_REVIEW、5 MISSING、1 SPEC_ONLY；IMG-01/04 两个未授权候选内容；256 个仓库栅格文件完成只读扫描 |
| 其他识别图像 | catch 原图三条路径字节相同，只算 1 张内容；另有一张夜拍候选、一张 224×224 model Golden、一张派生裁剪；Case A 原图未在当前树/可见 git history 中找到 |
| 首用引导素材 | P01/P02/P03 三张 Frozen 整页参考图已记录尺寸与 SHA；独立运行时 Hero 原图和错误示例图未确认 |
| 受控数据 | 6 类 RESP + Detector/Crop/Classifier failure、timeout、capture/decode failure、stale callback 均有接口 schema / 来源标记 / Retry 边界；未实现 Mock Engine |
| 自动化追踪 | 76 行（66 base + 10 E2E）逐条映射到需求、状态、代码范围、方法、Gate、执行任务与证据 |
| 验收 Gate | G01–G15 定义了前提、PASS、自动/人工检查、证据、失败/阻塞及上下游 |
| 执行 | 全部 NOT_RUN；没有 APK、模型 inference 或视觉审核 PASS |

## 交付文件

1. 01_MASTER_JOURNEY.md — 完整旅程、状态合同、保存接口边界及状态图
2. 02_STATE_MACHINE.yaml — 统一 PAGE_ID/STATE_ID、Guard、Side Effect、Owner、错误/恢复、测试用例与转换
3. 03_ANDROID_DEVELOPMENT_CONTRACT.md — DEV-01…04 源码事实与目标实现合同
4. 04_BASE_CASE_REVIEW.md — 66 条逐行审查和状态/分层统计
5. 05_E2E_SCENARIO_REVIEW.md — 10 条组合场景逐点路径、证据与准入标准
6. 06_TEST_USER_AND_ENVIRONMENT.md — 固定用户、权限 Host、API28/设备 profile 与证据政策
7. 07_IMAGE_FIXTURE_AUDIT.md — IMG-01…08、历史候选与 P01/P02/P03 资源盘点
8. 08_CONTROLLED_FAILURE_CONTRACT.md — 受控响应/故障 schema、隔离与结果口径
9. 09_TEST_AUTOMATION_PLAN.md — L1/L2/L3 Runner、CI 矩阵、结果 JSON Schema 和执行方法
10. 10_RUNTIME_ACCEPTANCE_GATES.md — G01–G15 完整验收定义
11. 11_REQUIREMENT_CASE_TRACEABILITY.csv — 66+10 需求/代码/方法/Gate/任务双向追踪
12. 12_DEVELOPMENT_BACKLOG.md — DEV/RFX 顺序及 Gate 关闭条件
13. 13_TEST_IMPLEMENTATION_BACKLOG.md — QA-00…QA-14 后续测试任务
14. 14_OPEN_DECISIONS_AND_BLOCKERS.md — owner、证据、推荐、影响及关闭条件
15. SPEC_CLOSURE_REPORT.md — 本报告

机器可读的规范主源仍为 docs/qa/recognition_main_journey/test_case_catalog_v1.json 与 e2e_scenarios_v1.json；标准图片和受控输入主源为 test_inputs_v1.json；固定 QA 用户仍以原始 1.1.0 manifest 为准。本次没有另建平行用例目录或修改 frozen seven decisions。

## 已知阻塞与准确状态

- OD-01：E2E-07 blur 接受目标未冻结；真实实现没有独立 blur 指标契约。本任务不改质量阈值，也不把人审标签转换成模型结果。
- OD-02：PROC-07 timeout 行为已批准，数值无 Pixel_2 冷/热推理测量/产品 SLA；值保持 PARAMETER_REVIEW_REQUIRED。220/320/350/250ms 为视觉最短节拍，不是推理超时。
- OD-03/04/05：真实照片来源/授权与缺失项、Hero/错误示例素材待资产 owner 关闭。
- OD-06：现有 API28 Runner 选择第一个名字包含 28 的 AVD，不能单凭配置证明 Pixel_2；Pixel_2 专属 Gate 需以实际 Build.MODEL/AVD/profile 证据为准。
- OD-07/08：Camera permission 拒绝状态文案/未知 requestability fallback、受控 Result DTO seam 需对应 owner 审查。
- OD-09：只读 control-plane admission 检查在 main-based 文档分支缺少其需要的分支专属 PROJECT_MASTER_STATE.yaml 和 WORK_EXECUTION_STATE.json，无法获得 Admission PASS。按用户授权不复制或公开这些文件；由 Repository Control-plane owner 确认合规替代或豁免流程。
- OD-10：此前集成记录中与识别动效目标无关的 API28 Normal Home/Camera FAIL_EVIDENCE 与 N001/J01–J20 BLOCKED/NOT_RUN 在本规范阶段未重跑、未覆盖、未改写。后续正式基线应按原 Gate 合同分开报告。
- PR #145 的任何产品提交、状态文件或运行日志均不在本规范分支上；本阶段保留独立文档分支和原本地 63d63033 检查点，不合并 main。

## 静态检查

| 检查 | 结果 | 范围 |
|---|---|---|
| scripts/validate_recognition_test_case_catalog.py | PASS | 66 IDs、10 E2E、单一初态、输入引用、用户反向映射、七项批准决策与 NOT_RUN；不含 Android/Runtime 证明 |
| scripts/validate_recognition_qa_test_users.py | PASS | QA 用户 manifest 结构/版本/状态，不创建账号、不授权 CAMERA |
| YAML/JSON/CSV 结构审查 | PASS | 38 状态字段齐全；76 条追踪记录有唯一方法；IMG-01…08 必需数据字段齐全 |
| git diff --check | PASS | 当前文档差异空白检查 |
| Android build / instrumentation / model / Runtime | NOT RUN | 明确超出规范阶段授权 |

## 提交与 PR

S1–S5 已分别形成里程碑提交；S6 在本次规范包与本报告完成时提交。Git 历史为准确的提交 SHA 来源：

| 里程碑 | 提交 |
|---|---|
| S1 业务流程与状态机 | 0430f057 |
| S2 四项开发合同 | d6c23718 |
| S3 66 条逐条用例审查 | e3f4427c |
| S4 10 条 E2E 与标准图片审计 | bc7458a3 |
| S5 异常数据与自动化架构 | d1cc707d |
| S6 Gate、追踪、Backlog 与收口报告 | 将在本里程碑 commit 后回填 |

Draft PR URL 和最终 branch HEAD SHA 在创建/更新 GitHub Draft PR 后回填；PR 保持 Draft / Open / Unmerged，不合并 main。
