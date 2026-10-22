# 14 · 待决策项、资源缺口与阻塞

记录的 OPEN 不会由本规格阶段擅自变更实现、冻结业务规则、输入数据、模型或 Runner。已有七项用户批准规则继续保持批准状态，不重新打开。

| ID | 状态 | 问题 / 已有证据 | 推荐处理 | 责任人 | 关闭条件 | Gate / 影响 |
|---|---|---|---|---|---|---|
| OD-01 | OPEN · P0 test contract | E2E-07 要求真实模糊质量异常；代码审计中的 FishDetectionQualityGate 是检测几何/eligibility gate，没有发现独立 blur score/threshold 合同。IMG-06 缺失 | Product + Model owner 决定本版要验证的可观察业务目标；若无模糊 INVALID 产品能力，则保留场景为探索/blocked，不能强迫模型输出 INVALID；不增改阈值 | Recognition Product Owner + Model Owner | 批准 blur 定义、输入衡量方案和输出目标，或书面排除 INVALID 预期并修订 E2E scope | E2E-07、G05/G06/G08/G14；当前 REVIEW_REQUIRED |
| OD-02 | PARAMETER_REVIEW_REQUIRED | PROC-07 有行为批准，但无 API28 Pixel_2 冷/热推理分位数/用户 SLA 或已批准 inference timeout 数字；四阶段最短动效时间不可代替 | 按 model/artifact/runner profile 采集阶段和 end-to-end latency distribution，Product/Model owner 定 P95/P99、余量、取消 semantics 和 config version | Model Owner + Product Owner | 记录测量方法、样本量、冷/热分组、模型 SHA、批准 timeout config | PROC-07；G06/G07 BLOCKED |
| OD-03 | ASSET_BLOCKED | IMG-01 候选含人物/水印、场景纵向而非要求横向；授权和来源未核。IMG-04 夜拍候选含平台标识，来源/许可/采集参数未核 | 不继续公开重用；通过授权采购/自有采集取得原图并完成隐私/权利审查；本规范未下载/编辑 | Asset/Legal owner + QA | 源文件、作者/许可、人物授权、采集 metadata、source SHA + Git Blob SHA 分开记载并由独立审核批准 | G05/G12/G13/G14 |
| OD-04 | ASSET_MISSING | IMG-02/03/05/06/07 无候选；IMG-08 只定义 schema 尚无 bytes。Case A 原始图未发现，golden_yellow_catfish_224 仅224×224 model regression | 按07逐场景取得 approved originals；IMG-08 后续按批准的最小 malformed-byte 配方创建；不伪造模型标签 | QA Fixture owner + Model owner | 每输入 source/rights/hash/dimensions/EXIF/scene annotation 完整并批准 | G05/G06/G12/G13/G14；E2E-06/07 BLOCKED |
| OD-05 | SOURCE_MISSING | P01/P02/P03 有 Frozen 整页 PNG，但没有可确认的独立 Hero 原照片，也未盘到独立错误示例素材 | 资源 owner 提供运行时原片与错误示例源文件/权利信息；Frozen 整页图仅保留参考用途 | Recognition Design/Asset owner | 运行时资源、原图来源和 SHA 完成审批；与整页 PNG 用途分开登记 | G03/G10/G12 |
| OD-06 | DEVICE_PROFILE_REVIEW | 当前 android.yml 使用 API28 自托管 runner，按 AVD 名含 28 首个匹配，缺失则使用首个；1080x1920 / 480dpi / fontScale1.0 是配置/检查值。该选择逻辑自身不证明 Pixel_2 | QA owner 把 API28_PIXEL_2_PRIMARY 与实际 AVD/ro.product.model/display evidence 匹配；不足则批准明确的替代 profile，不能重命名代替测量 | Android Runtime/CI owner | 目标 AVD 唯一、真实 Build.MODEL/SDK/尺寸/Density 被同 run 证据验证 | G02–G15；Pixel_2 专属门禁 BLOCKED |
| OD-07 | COPY/PLATFORM_REVIEW | 首次拒绝时的具体说明文案非 Frozen；shouldShowRequestPermissionRationale=false 本身不能证明系统不再允许 request。当前合同以 REQUESTABILITY_UNKNOWN 留安全兜底；系统管理策略也需区分 | 由 Capture copy owner 冻结提示；Android owner 实现在未知状态不做“永久拒绝”断言，优先显式重试 + Settings/Gallery/Back | Recognition Product Owner + Android Permission owner | 提示 copy/回退选择批准，API28 request history/System flags/Settings return 可复现 | G02/G04/G13；不改变 PER-04/05 批准行为 |
| OD-08 | DTO_REVIEW | Controlled response/failure 测试 seam 所需 Result payload 与现有 ProductionRecognitionResult / RecognitionPrediction/UI adapter 的映射尚未有独立冻结 JSON schema；不能为了分支制造伪真实分数 | Result/Android owner 固定 test-only DTO adapter，branch payload 仅使用 UI 合同必需数据；验证 Release 隔离，mock trace 单独统计 | Android Result owner + QA | schema version、每个受控 ID payload、save sink 边界和 Release 不可达证明经 PR 审查 | G08/G12/G13；E2E-05/08 不能算真模型 |
| OD-09 | CONTROL_PLANE_ADMISSION_BLOCKED | 从最新 main 建立的规范分支缺少仓库 Admission validator 所需的分支专属 PROJECT_MASTER_STATE.yaml 与 WORK_EXECUTION_STATE.json；用户明确禁止将其复制/公开，且要求保持不披露。此前只读 validate_control_plane_admission.py 检查不能完成准入 | 维持分支缺失；由仓库控制面 owner 为 docs-only Draft PR 提供合规的脱敏/受控 admission 路径或说明无需分支运行状态，不在 PR 文件里伪造/补齐状态 | Repository Control-plane owner | 用获批路径对 exact PR HEAD 通过 admission，或 owner 明确书面豁免/替代规则 | 规范 Gate；不代表 Android runtime 失败或 PASS |
| OD-10 | BASELINE_EVIDENCE_SEPARATE | 集成候选工作记录称既有 API28 Normal Home 与 Camera evidence 为 FAIL_EVIDENCE、N001/J01–J20 BLOCKED/NOT_RUN；本规格未重新运行且不修改这些结果 | 在后续正式 acceptance record 中按原 run/source/APK 报原结果；识别动效目标无关失败单列，按自身合同阻止/豁免，不把它伪装为 PASS | N001/QA gate owner | 取得受控原始 run/artifact 并逐 Gate 分类；本 PR 只引用而不复制运行元数据 | 相关基线门禁独立 BLOCKED/FAIL_EVIDENCE，非本规范的执行结论 |
| OD-11 | SCOPE_BOUNDARY | 保存鱼获后的详情页/鱼鉴/Normal Home Hero 不在 Main Journey；未保存 Back 必须不产生 FishRecord | DEV-01 只实现结果离开和 save-in-flight 接口；保存成功后既有目的地不纳入本次 Result 主流程视觉验收 | Product owner | 冻结本流程出口与 Save Catch/Continue Memory 的接口事件，不把详情页放回本 E2E 范围 | G01/G08/G13；无扩张 scope |

## 当前不可关闭的工作

- 规范审核前不改 Product source / test implementation。
- 无权威测量不选 inference timeout 值。
- 无权利/来源审核不升级现有照片 candidate。
- E2E-06/07 在真实输出和素材未满足时保持 BLOCKED/REVIEW_REQUIRED；禁止替换真实模型响应。
- 本文件中的结论不得通过给状态文件填入“绿色值”消除仓库 admission blocker。


## OD-11 · First-use guide Android Runtime assets are absent

**Status:** BLOCKED_ASSET_SOURCE  
**Owner:** Design/Asset owner for source rights and semantic approval; Android owner for resource binding; QA owner for Runtime gate evidence.  
**Evidence:** The three Frozen P01/P02/P03 PNGs are full-page references, not independent runtime example images. The current Android policy has §heroAsset = null§ and §errorAssets = listOf(null, null, null)§.  
**Required scope:** Twelve required slots (P01/P02/P03 × correct Hero + head-out-of-frame + too-small + excessive-occlusion).  
**Closure conditions:** Populate and review the runtime asset manifest; approve source and app/repository/CI usage rights; verify fish direction and example semantics; bind byte-identical Android resources; pass G03A–G03E using real Runtime evidence.  
**Prohibited workaround:** Do not crop the Frozen full-page PNG, use live CameraX background as the Hero, substitute a model Golden or generic image, or downgrade a missing slot to a warning.  
**Downstream impact:** FIRST_USE_GUIDE_VISUAL remains BLOCKED; full-flow E2E paths that require this guide remain BLOCKED; overall recognition acceptance cannot PASS. This is a standards-stage blocker record, not an implementation or test result.
