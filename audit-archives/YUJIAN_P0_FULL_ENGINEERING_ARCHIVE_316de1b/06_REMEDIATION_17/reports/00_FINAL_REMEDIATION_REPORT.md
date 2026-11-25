# YuJian P0 六合同 17 项整改报告

## A. 源码基线

- 仓库：pan277942135/Yujian_App；PR：#346；分支：fix/26kf-path-boundary-and-fish-gate-v1。
- 审计基线 HEAD：babd6b3ad31ee02f70d74738a27d7f14cc6b5e10。
- PR Base：6ec43662ef76c6ffee8530148098649496ab7fb1（按任务附件给定；本轮 GitHub API 403，未能重新读取 PR 元数据）。
- 整改 Commit：316de1b705fb54ee7d6e2114259ec1f87773aedf，消息：fix(recognition): close P0 identity and timeline gates。
- 整改提交修改 75 个文件；本轮产生 1 个整改代码 Commit。推送前本地另有 state-only 前置提交 19dfde5eed1fa0f9fff64c25f561c8c509428c06；相对原审计 HEAD 推送了 2 个后续 Commit。
- 推送后 refs/heads/fix/26kf-path-boundary-and-fish-gate-v1 与 refs/pull/346/head 均为上述整改 Commit；Main 仍为 03db3692669847084d7e48fc3128f85cbf832af9。
- 未执行合并或 force push。PR Draft/Open 元数据无法通过当前 GitHub API 身份核验；没有发出任何修改 Draft 状态的请求。
- 六份冻结合同正文 SHA256 全部保持给定值。原始审计目录没有被修改。

## B. Gate Deadline

独立冻结规范已加入 docs/contracts/yujian-ai-v2/12_NON_MODEL_GATE_DEADLINE_POLICY_V1.md，SHA256：49b3bf8de8244a169d7779647cd24c08063d3427d62de95f5d139fe932b08d93。23 个 Gate 的超时、重试数和起始事件见本目录 02_GATE_DEADLINE_POLICY_REPORT.md。

- 主动呈现预算 6,000 ms；四模型共享 Attempt Deadline 60,000 ms；单个非模型 Gate 上限 3,000 ms；Attempt 非模型阻塞区间并集上限 10,000 ms。
- PixelCopy 为独立 1,500 ms 取证期限，不计入业务预算。并行 Gate 按阻塞区间并集累计；模型仍运行时不消耗非模型预算。
- 重试次数不含首次执行；重试保留原始 Gate Deadline、Gate Instance、Attempt 与累计预算。无效身份、无效几何、非可信 Mask 等终态不重试；PATH_BOUND 超时不自动降级到 ORB_ONLY。
- SceneClock、Model Deadline、Gate Deadline、Attempt 累计预算和 Evidence Clock 分离。Stage10 不等待 Result Geometry 或 Glyph Path；此处仅为源代码/单测结论，不构成 Stage10–13.5 真机闭环证明。
- Policy/Tracker、23 项常量与预算行为的单元测试通过；生产端只有部分 Gate 已接入统一调度器，全部 23 个 Gate 的真实生产触发、成功 Receipt 和下游阻断闭环仍未完成。

## C. 17 项整改结果

01_17_ISSUE_CLOSURE_MATRIX.csv 逐项列出根因、实际文件/函数、生产 Producer/Consumer、Commit、测试、跨层状态与未解风险。本轮没有将任何未做真机验证的事项标成 RUNTIME_VERIFIED。

可确认的源码改动包括 PhotoIdentity 双 Hash 与传播、commit-once Result Snapshot、Frame Commit 与平台 Presented Receipt 分离、ONNX RunOptions 终止请求及资源生命周期保护、Embedding cache identity/lease、Gate Policy/Tracker、Stage10 依赖解耦、Evidence 与 SceneClock 解耦、KF25 可选保存状态、独立 ZIP 校验器，以及 Orb-to-Glyph 采集/验证脚本。

仍为部分完成或未实现的关键项包括四模型独立 trace、PATH_BOUND 双端接受、完整 Result/Handoff Receipt、GPU 算子归因、完整 Evidence ZIP Schema 校验、设备 MP4 采集、完整 FIRST_FAILURE 因果字段及共享 GlyphPathProvider。详情见矩阵和 08_UNRESOLVED_ITEMS.md。

## D. 关键生产链

- Photo → Model → Mask → Contour → P* → FishPath：RecognitionImageStore 产生 PhotoIdentity，识别/分割引擎产生 Model 与 Mask 结果，RecognizingScreen 和 WaterLightPathLedger 传递轮廓/路径信息，RecognitionRevealSessionStore 接收并提交路径状态。身份模型和关键单测已增加；真实四模型独立 I/O Receipt 与 Controller/Store 双端路径绑定仍不完整。
- FishPath → Snapshot → Timeline：RecognitionSnapshotCommitter 在 SessionStore 中进行 Attempt 级 commit-once；Timeline 仍由 RecognitionSceneAnimationController 管理。缺少完整精确 HEAD 设备 Trace 证明各 Receipt 顺序。
- ORB_RETURN → Orb Deconstruction → Glyph：Timeline 与 Orb 路径代码已调整，Glyph 仍由现有 Result 文本布局路径供给；共享 GlyphPathProvider 尚未实现。
- Glyph → NAME_STABLE：代码区分 Draw Submitted 与 Android 帧提交/呈现信号；API 35 使用 SurfaceFlinger PresentFence，API 29–34 明确记录呈现不可用。本轮没有设备验证 PresentFence 或 PixelCopy 相关帧。
- PHOTO_SETTLE → HANDOFF_ACK → SCENE_OWNER_TRANSFER → INFO_ARRIVAL → RESULT_STABLE：YujianApp 对部分 Substage 和 Result Host Geometry 接入 Gate/回执；Consumer ACK、单一 Owner 转移和完整结果帧回执集合尚未形成可验证闭环。
- Runtime → Evidence → Independent Validator：Evidence Recorder、ZIP Exporter、RecognitionEvidenceArchiveVerifier 与独立单测已更新；生产 ZIP Schema、Attempt/Snapshot 链及真实 MP4 内容验收尚不完整。

## E. 测试

最终 Commit 的整组 Gradle 任务成功，BUILD SUCCESSFUL。单元测试 574 passed、0 failed、0 skipped；app AndroidTest Kotlin 编译通过但未运行 Instrumentation。详细任务、模块计数、报告路径及静态模型校验失败原因见 04_TEST_RESULTS.md。

合同包静态校验 13 项通过。APK 中 classifier、metadata、class map、tensor contract 字节与随包清单一致；独立的严格 Release Snapshot 校验器仍失败，因为已提交 model_release_contract.json 的 release_assets 行缺少验证器要求的 asset id。二者分别记录，没有把来源校验失败伪报为成功。

## F. APK

- 本地构建产物：/workspace/Yujian_P0_delivery/yujian-debug-316de1b7.apk。
- Exact Source Revision：316de1b705fb54ee7d6e2114259ec1f87773aedf。
- Package：com.yujian.ai.uiv2；Version Name：2.3.3-orb-wake-v1.1-20261103；Version Code：6。
- 大小：200,257,995 bytes；SHA256：ac98167b18ebb4d6d18d6d0161fe57da64bbf100c6ae345f621fe13166cccb7e。
- Android Debug 签名验证通过（APK Signature Scheme v2）。SOURCE_REVISION 已从 APK DEX 中核验与 Commit 相同。
- APK 已在本地构建并核验；GitHub Release yujian-debug-316de1b7 未能创建，故不存在经过验证的直接下载 URL。GitHub API 返回 403，且当前 GH_TOKEN 无效。APK、SHA256 文件及 metadata 没有上传到 Release。
- PR 页面：https://github.com/pan277942135/Yujian_App/pull/346 。Commit 页面：https://github.com/pan277942135/Yujian_App/commit/316de1b705fb54ee7d6e2114259ec1f87773aedf 。

## G. 未完成事项和验收状态

- DEVICE_RUNTIME_ACCEPTANCE=NOT_RUN
- VISUAL_AUTO_ACCEPTANCE=NOT_RUN
- FULL_FLOW_ACCEPTED=NOT_RUN
- Android Instrumentation、模拟器/真机识别、Stage10–13.5 全流程、API35 PresentFence、设备 Orb-to-Glyph MP4 采集均未运行。
- 严格模型 Release 来源校验失败；包内模型文件 SHA 与长度核验通过。
- GitHub Release 上传及直接下载链接受 GH_TOKEN 无效/API 403 阻塞；PR Draft/Open 元数据也因 API 403 未能读取。
- 17 项目前尚有部分项未完成，不能报告整体 remediation PASS。具体根因和恢复动作列于 08_UNRESOLVED_ITEMS.md。

本目录报告是本地交付文件；它们不属于上述 APK 源码 Commit。提交报告本身会改变 HEAD，因此本轮为保持 APK 的 Exact-HEAD 一致性没有把报告文件追加提交到 PR 分支。
