# 03｜历史问题账本（保留、分类、逐轮更新）

**状态词典**：
`CONFIRMED_AT_SHA` 仅表示指定 APK 上有真机直接证据；
`HYPOTHESIS` 为待排除假说；
`FIXED_PENDING_DEVICE` 代码已改但未完成设备回归；
`VERIFIED` 必须关联通过证据；
`UNVERIFIED_LATEST` 代表 PR 已前进且暂无同类新设备验收；
`BLOCKED_SOURCE` 代表缺失权威资产或环境。

**重要**：2026-11-25 两份真机故障来自 `57ccf1b3`。PR #346 建档时已前进 5 个提交至 `cafc289c`，其中新控制面/路径修复尚无同类真实设备证据，因此本账本不会自动宣称最新版本仍必现或已解决。

| ID | 历史故障 / 风险 | 已知状态及验收动作 |
|---|---|---|
| H01 | Android Native Heap 曾观察到 2.47 GiB 峰值 | 原因/修复效果待设备内存证据；不能用宿主机 Arena 测试代替 |
| H02 | ORT 取消、Native Run 未终止、重复推理 | P0-2 源码已集成；API36 取消→NativeReturn→Cleanup→新 Attempt 仍未充分验证 |
| H03 | 早期 Mask 0 点 / contourClosed=false / maskTrusted=false | 两份新样本均真实 READY/可信；其他输入仍需回归 |
| H04 | `pStarGeometryVersion=0`、PhotoSnapshot `stableContactSource=null` | 历史缺陷；新样本 A 已成功绑定但版本仍待交叉检验 |
| H05 | `PATH_BOUND` 在 `MASK_CONTOUR_PSTAR` 前触发、`GATE_EVENT_ORDER_INVALID` | 在 57ccf1b 真机 A 证实；复核最新代码的 Producer/Consumer 提交顺序 |
| H06 | STATIC_RESULT 与冻结 ResultSnapshot 枚举/有效性不一致 | 真实 B 路径失败 + 源码不变量冲突；不得静默改冻结合同 |
| H07 | 低置信度 `COMPLETE_ATTEMPT_UNAVAILABLE` ×26、`NAVIGATION_NOT_ACCEPTED` | 57ccf1b 真机 B 证实；要输出 commitOnce INVALID/CONFLICT/STALE 细分 |
| H08 | FISH_GOLD_PEAK 处 `MASK_GEOMETRY_INVALID` | A 真机可证的阻塞**现象**；可能为 mask/photo version 或 Gold Receipt，不能仅凭字符串判断根因 |
| H09 | Gold Receipt producer/consumer 缺失/被拒 | 待核查真实 draw/pass/receipt 与布尔门控 |
| H10 | Stage10–13.5 未闭环 | A 停第九阶段；B 停第五阶段；上层总体未验收 |
| H11 | ORB_RETURN 3 段回流不正确/没看到 | 保留真实路径身份与 return receipt 负例 |
| H12 | Orb 分层解体→中文 Glyph 没有发生 | 真实视觉高保真与身份/笔画 receipt 未验收 |
| H13 | Result 页面「识别中」只显示照片、Handoff 子阶段缺失 | A 有可见 Result 但 Scene Stable 缺失；两 ZIP 无 result_handoff.jsonl |
| H14 | Result `LAYOUT_ROOT_PENDING` | 历史阶段待真机回归；不得以合成 hero raster 解几何门 |
| H15 | Result `RESULT_HERO_PENDING` / Raster 与 Geometry 混用 | 历史风险，分离 GPU Geometry/PixelCopy Screen |
| H16 | 照片左下孤立矩形 / ARRIVAL 起终点重合 / `between()<1px` | 历史视觉缺陷，最新截图与帧差要回归，不能只验证路径非空 |
| H17 | Evidence ZIP 导出失败、PixelCopy 空、原始 Mask 缺失、冗余截图 | 最新两 ZIP 有 Mask 和有限截图，但 Result 日志缺失；仍未达独立完整验收 |
| H18 | Orb Core 立体感、Resident/Task 数量、材质/光学与高保真差距 | 尚无逐帧冻结源图 ROI/GPU 收敛证据 |
| H19 | 13 Stage / 26KF AUTO 全流程 | 未验收；两份固定真机样本分别 8/13、4/13 已完成 |
| H20 | P* Snapshot 回调乱序，Controller/Store 路径绑定不原子 | 历史与 A Gate 顺序有交叉，最新 `RecognitionPathBindingCommitCoordinator` 需真实回归 |
| H21 | 高置信度业务 COMPLETE 先于视觉完成就导航 Result | A 视频出现只照片的「识别中」；核查真实 Result Handoff 路由/ACK |
| H22 | 远端提交与本地 Codex 工作树分叉，认证/提交丢失 | P0-2 已提交并验证；继续执行 exact-head/普通 push/不可 Force Push 防回退 |

## 关闭任一问题的最低证据
1. 原始失败样本与失败点真实日志；
2. 确切根因（Producer、Consumer、Gate、Identity、版本、状态），不是错误码推断；
3. 针对性失败前用例与修复后 PASS；
4. 真实设备同类证据/不适用说明；
5. PR 最新 SHA、CI、APK SHA、对应 ZIP/视频的哈希；
6. 记录关闭 Commit 与仍存风险；**永远不删除历史行**。

主工作树另有未推送状态/日志提交及未跟踪证据的历史报告；不能自动混进生产代码，也不能在未经实际核验时认定这些本地成果仍可访问。
