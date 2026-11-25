# 00｜Master Goal · 唯一长期目标

**Goal ID**：`YUJIAN_AI_FULL_RECOGNITION_AND_VISUAL_FIDELITY`  
**Goal 状态**：ACTIVE_TARGET / NOT_ACCEPTED  
**最近已核实 PR #346 HEAD（建档时）**：`cafc289c27d7b7fc80d8bbc76183e799f5d28ae9`

## 产品终局
真正钓友拍摄或选择鱼获照片 → Detector 定位 → Classifier 真实鱼种 → SlimSAM 真实 Mask/Contour/P* → Orb/WaterLight 13 Stage 正确视觉表现 → ORB_RETURN → Orb 解体迁移到真实中文 Glyph → Result Photo/Info/Handoff → Result Stable → 用户按需保存鱼获。**不是鱼类百科演示，不以固定视觉样例充当真实识别结果。**

## 必须分别通过的业务分支
- **HIGH/FULL_REVEAL**：数据/身份条件成立后，走完整 13 Stage、真实 Mask Path、Gold、Return、Glyph、13.1–13.5。
- **MEDIUM/LOW/STATIC_RESULT**：合法业务结果应正确形成不可变 Snapshot、导航到与置信度匹配的 Result；不得为低置信度伪造 FULL_REVEAL，也不得无限等待 FULL 专属 Stage。
- **ORB_ONLY**：仅在 Mask 到终态且合同允许的降级条件下执行 Orb-only 真正路径，不伪造 Mask 轮廓；与 STATIC_RESULT **不是同一个含义**。
- **FAILURE/CANCEL/TIMEOUT**：安全失败且原因可追踪；旧 Attempt 不污染新照片。

**注意：** 当前冻结 Snapshot enum 仅含 FULL_REVEAL / ORB_ONLY / CLASSIFICATION_FAILED，而实际代码还具有 STATIC_RESULT 业务决策。两者的映射冲突被登记为 P0，需要走原合同及控制面批准的兼容修复，**不得直接将可信 Mask 伪写为失败、也不得未经授权悄悄修改冻结合同**。

## 唯一完成判定
1. 正确的模型结果及可审计的四模型身份；无假结果/固定 BBox。
2. HIGH 与 MEDIUM/LOW 两个固定历史回归样本，另含 Mask 失败、超时、取消/重试、换图/乱序回调等负例。
3. FULL 的 13 个主 Stage 及 Stage13.1–13.5 有真实 Producer / Consumer / Render Receipt 与时间线；STATIC_RESULT 走其合法不适用路径。
4. 唯一 SceneClock 主动呈现预算 6000ms，模型依赖上限 60000ms；非模型 Gate 等待满足已冻结独立预算；无无限等待。
5. 26KF (KF00–KF25) **26/26 状态行**，所有适用帧有真实 Frame Presented/PixelCopy；未触发保存的 KF25 明确 NOT_APPLICABLE，不可硬报 26 张全部 CAPTURED。
6. 真实 Orb 内核、五条 Resident、源参考决定的 Task Energy、真实鱼体光学、粒子/字形与 Result 视觉 **按冻结源图量化对照并验收**。
7. Native 终止、Lease、内存、重试得到实际 Android 证据；无被误报为“已解决”的 2.47 GiB 历史风险。
8. 真实 HUAWEI CMS-AL20 / Android 16 AUTO 回归、真实 Evidence ZIP、视频、日志、失败分类与安全可重复导出。
9. GitHub Exact-HEAD CI、APK 内 build SHA、Artifact SHA256 / APK 单文件 SHA256、PR HEAD 一致。
10. 原始冻结及历史故障账本的来源完整性、未验证缺口全部显式列出。

只有上述同时具备与相应源码 SHA 绑定的证据才能标记 `YUJIAN_FULL_FLOW_AND_VISUAL_FIDELITY_ACCEPTED`。否则持续标记 PENDING 或 BLOCKED；工具/预算/真机不可用不得伪称 COMPLETE。

## Goal 的执行边界
持续执行是有预算、授权、环境和可用数据前提的 best-effort 能力，不是无限独立运行承诺。当前没有直接操纵用户华为真机的设备通道；需要用户上传的新设备证据可作为允许暂停的真实外部依赖。每轮提交和回归依照 [09](09_AUTONOMOUS_EXECUTION_PROTOCOL.md)。
