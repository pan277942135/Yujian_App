# AI Studio 2026-11-01 ZIP：Main 差异审查

- 源文件：`remix-remix-渔见-1011.zip`
- ZIP SHA-256：`01295638560875dcf65addd6e5c31216df733976ede0be4fe2ea8cfb680218fb`
- Main 基线：`419f22a13be13fde87ca31e48e4a00da751bd8f8`
- 清单：1614 个 ZIP 条目；1229 个文件、385 个目录；未发现符号链接。
.env 内容与哈希未读取或记录。

## 文件级比对

- 与 Main 完全相同：1104 个文件。
- ZIP 新增：72 个文件。
- ZIP 与 Main 不同：53 个文件（其中 1 个敏感 `.env` 仅标记隔离、不读取内容或哈希）。
- Main 有、ZIP 无：702 个文件；ZIP 不是完整 Main 快照，不能整包覆盖。
- ZIP `CHANGESET_MANIFEST.md` 有 19 行，只有 15 行对应实际差异；未覆盖 109 个非敏感差异。

## 按模块审查实际差异文件

| 模块 | 数量 | 处理结论 |
|---|---:|---|
| AI Studio 导出素材 | 30 | 无正式素材来源/授权链；排除 |
| AI Studio 工作区产物 | 32 | 非产品源文件；排除 |
| CI / 工作流 | 1 | 移除既有门禁或变更验收范围；保留 Main |
| Design Manager | 3 | 移除失败关闭和安全路径校验；保留 Main |
| Empty Home 动效 | 2 | 回退 W5 几何、事件驱动 Y 轴运动或触感门禁；保留 Main |
| Empty Home 合同/资源绑定 | 12 | 与 Main W5 / 事件动效合同不一致；保留 Main |
| Web 识别设计资料 | 4 | 非 Android Runtime；单独审阅，不随代码导入 |
| 位置搜索 | 1 | 移除 SecurityException 兜底；保留 Main |
| 其他差异 | 5 | 与权威/来源不完整；保留 Main，待独立核验 |
| 拍照快门与转场 | 8 | 与 Main 冻结组件合同冲突；待负责人决策，暂不覆盖 |
| 敏感配置 | 1 | 敏感文件内容和哈希均不纳入报告；不导入 |
| 构建、网络与安全 | 8 | 含包名/明文流量/失败开放或 URL 校验回退；保留 Main |
| 测试代码 | 4 | 断言随 ZIP 冲突规格变化；未迁移、未运行 |
| 项目元数据 | 1 | 与产品代码无关；不导入 |
| 首次拍照引导 | 5 | 删除 P03 或绕过相机 Preview Ready 门禁；保留 Main |
| 首次拍照引导候选素材 | 8 | CANDIDATE_NEEDS_APPROVAL；未经负责人批准不进入 Runtime |

## 结论

这份 ZIP 中没有可以直接覆盖 Main 的完整版本。Empty Home 会回退 W5 锚点并移除事件驱动的 Y 轴运动/触感门禁；首次拍照引导会从三页变两页并绕过 Camera Preview Ready 门禁，且新增 8 个无来源授权记录的候选图片；应用包名、明文流量、模型初始化失败策略、URL 规范化和 Design Manager 失败关闭校验也有回退。HIGH Result `RecognitionResultScreen.kt` 无差异。

仅剩的产品级新增组是快门/转场重设计：按钮直径 88–96dp → 102–108dp；呼吸 5s、最大缩放 1.015 → 4.5s、最大缩放 1.06；转场引入 1.22 倍峰值并使用新的分层水光。它与 Main 的 FROZEN Primary Capture Button、共享动效合同和 Main 的按钮几何测试冲突；本次按保留 Main 合同处理。

因此本次不合入该快门组、不改写冻结合同、不合入候选图、不覆盖 API/构建/安全/CI/Empty Home/Guide 回退。API 28 Runtime 验收未启动。

## 差异清单

完整 ZIP 文件级清单（含每个文件 SHA-256、Main Git blob SHA、Manifest 命中、模块影响和处理结论）见同目录 `AI_STUDIO_ZIP_FULL_RESTORE_INVENTORY.json`。