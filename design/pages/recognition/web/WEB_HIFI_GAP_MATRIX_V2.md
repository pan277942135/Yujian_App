# Web PR #177 → Web Hi-Fi V2 Gap Matrix

## Live source binding — current audit 2026-10-23

The G01–G18 rows remain a **historical code-review snapshot** of PR #177 HEAD 655c4af07a0244fcf29441b5652c48ca74f2e6a2; they are not proof that each gap remains identical on the live source. This binding records the current source and executable fixture evidence without rewriting those historical observations.

- Live PR #177 HEAD: `8edce561c3b6f25263eb32f1d72706fec4e329b1` (49 commits ahead / 0 behind product main).
- Live product main and merge base: `738954105d545d9c354136b8b93f3562058fcf30`.
- Exact-head CI: [run 40057399157](https://github.com/pan277942135/Yujian/actions/runs/40057399157) **PASS**. It includes targeted validation, offline SWOS probe unit tests and Chromium/UI simulation; it does not establish authenticated real-photo inference, SAM output, visual acceptance or production Mask qualification.
- Prior diagnostic CI [run 39980021942](https://github.com/pan277942135/Yujian/actions/runs/39980021942) identified GLSL reserved word `patch` as the reason for fragment shader compilation failure. The follow-up renamed it and added a required WebGL2 runtime assertion.
- The current workflow is manual-only with required exact target_sha input. Existing Candidate Revision `yujian-model-factory-console-00349-hes` remains on old source `6da640859f063d7f42ee5432e6b35ecb162802ff`; current PR #177 HEAD `8edce561c3b6f25263eb32f1d72706fec4e329b1` has not yet been deployed. Previous probe [39982073543](https://github.com/pan277942135/Yujian/actions/runs/39982073543) failed before valid inference: 2 requests HTTP 401, 3 fixture downloads HTTP 429.
- Current production Mask: **NOT_QUALIFIED**. Deployed runtime visual acceptance: **NOT_VERIFIED**. Chromium runtime renderer verification is fixture-only; real-photo browser performance and visual evidence are **NOT_RUN**.
- The canonical control plane remains in Yujian_App; the separate product repository must not create a competing registry.

观察源：`pan277942135/Yujian` PR #177 HEAD `655c4af07a0244fcf29441b5652c48ca74f2e6a2` 的 Product Shell 模板、CSS、识别控制器与照片会话/接续模块。这是代码审查，非运行像素差异；PR 仍 Draft/Open/Unmerged。目标合同为 Main 已采纳的 `YUJIAN_AI_CATCH_RECOGNITION_WEB_HIFI_V1` 及九份机器文件。未经授权原图比对的量化差值保持 `NOT_MEASURED`。

| ID | 当前实现位置/观察 | 合同/优先级 | Work B 修复边界与简单验收 |
|---|---|---|---|
| G01 | `catch_recognition.html` 设置 `product_shell=true`，共享 Platform 基础认证与返回导航；产品外壳 CSS 重置主栏边距并给出 1280px 居中宽度 | Page_Structure / P0 | 保留认证/返回；以真实路由确认外壳与侧栏状态，不因本次合同发布宣称 Web 产品已验收 |
| G02 | Product Shell 下三阶段网格按 ≥1280px 三列、768–1279px 两列、≤767px 单列；`.page-wrap` 最大 1280px | Component_Geometry / P0 | 以合同比例校准卡片和阶段对齐；九视口运行截图及批准源比对仍未完成，不得称像素 PASS |
| G03 | `.catch-page` 白色卡片/渐变环境有限 | Visual_Tokens / P1 | 接入共享背景与淡蓝金氛围但照片保持真实；检查主文案对比度 |
| G04 | 当前分支补充多数正文/标签 14px/20px、控件 16px/44px 最小高；窄屏标题规则与少量状态标记仍需按合同复核 | Visual_Tokens / P0 | 使用语义字号角色检查 320/390/1440；本次为代码检查，未运行真实页面视觉验收 |
| G05 | `.catch-drop` min-height 186px，处理区 aspect 1.42 | Component_Geometry / P0 | 提升照片主体面积并保持 contain、EXIF 正向；横竖长图全显 |
| G06 | Detector/Classifier/SAM 与质量门清单现在放在默认折叠的 `AdvancedDiagnostics` `<details>` 中 | Page_Structure / P0 | 诊断仍只在用户主动展开时可见；普通路径从上传进入真实识别，且不伪造阶段进度 |
| G07 | `catchDiagnostic` JSON 与 SAM 状态已收纳于默认折叠诊断区；普通页面初始视图不展开技术细节 | Page_Structure / P0 | 继续确保内部 JSON/BBox/模型版本不泄漏到默认用户路径；无真实 Mask 时不画精准轮廓 |
| G08 | `.catch-water` 为几层 radial-gradient、模糊椭圆 | Authority_References / P0 | 分离七个光学层并按照片下缘向上、面状不规则焦散实现；P2/P3/P6 静态帧审查 |
| G09 | `.catch-region-glow` 为 BBox 柔光；无可信 Mask 三层鱼体描光 | Authority_References / P0 | 保留 BBox 降级，不画假轮廓；真实可信 Mask 后三层共享同一几何；P4–P7 复核 |
| G10 | Result 卡在 ≥1280px 与三阶段并列；更窄时跨全行；结果表单仍为两列，桌面 Hero 实际尺寸尚无批准源测量 | Component_Geometry + Page_State_Matrix / P0 | 对照 322×210/360 参考、中文名、四字段、故事和双 CTA；文案/运行行为仍须独立验收 |
| G11 | 新 `PhotoSession` 固定 source/session ID；`photo-transition.js` 迁移同一 `<img>` 节点，并逐帧重测处理/结果容器几何 | Authority_References / P0 | 仍需 R0/R0.5/R1 实际浏览器帧验证同源照片与几何连续；代码结构不构成视觉证据 |
| G12 | JS RR09 常量，但现有页面没有可信 Mask→文字字形转译的正式证据 | Authority_References / P0 | HIGH only，落位后转移，中文 glyph mask；无 Mask 使用降级，不凭空精准路径 |
| G13 | 已引入独立五节点旅程模板与状态渲染；水光/鱼体两节点明确显示 `视觉效果待接入`，不冒充模型进度 | Page_Structure / P1 | 检查 pending/active/done/error 与真实阶段驱动；实现合同要求保留五节点语义和降级标识 |
| G14 | Product Shell 下 CSS 有 1280px/768px 三列、两列、单列断点；合同九视口覆盖未由该代码审查证明 | Responsive_Rules / P0 | 完成九视口滚动、键盘与 320×480 横溢检查；目前仍没有九视口截图验收证据 |
| G15 | `.catch-empty-icon` 与侧栏鱼图标为 emoji | Visual_Tokens / P1 | 映射共享图标注册；可访问 label 保留，不以 emoji 作为正式图标 |
| G16 | JS 结果分支及 `result.revealing` 需要查 HIGH-only 限制 | Page_State_Matrix / P0 | MEDIUM/LOW/恢复/技术失败均无成功庆祝；真实 HIGH 新会话一次；自动测试 |
| G17 | 后端默认低置信 0.55 与 App 观察 0.45 未统一 | Page_State_Matrix / P0 decision | 不改阈值；标明来源和语义，提交业务决定后同步唯一判定源 |
| G18 | 无已授权示例图，代码文本说明 | Asset_Manifest / P1 | 当前无需示例即可上传；未来仅接入有许可的一张真实示例，禁提交用户私图 |


## SWOS Web High-Fidelity Motion Technical Contract V1.0

The implementation authority is [SWOS Web Hi-Fi Motion Technical Contract V1.0](SWOS_WEB_HIFI_MOTION_TECHNICAL_CONTRACT_V1_0.md) and its machine-readable contract at [contracts/SWOS_WEB_HIFI_MOTION_TECHNICAL_CONTRACT_V1_0.json](contracts/SWOS_WEB_HIFI_MOTION_TECHNICAL_CONTRACT_V1_0.json). It binds the existing Web V2, SWOS 15-frame, shared-photo transition and RR09 V3 authorities.

| Workstream | Required closure evidence | Current status |
|---|---|---|
| Real playable browser animation | One-clock 3650ms playback, all 15 seek points, pause/step/speed controls, real recording and exact source SHA | NOT_VERIFIED |
| Optical renderer | Irregular water caustics, seven-layer responsibility, WebGL 2 → cached Canvas 2D → static fallback | NOT_VERIFIED |
| Photo continuity | Same photo identity and shared transform P7 → R0 → R0.5 → R1; exact R0.5 geometry midpoint | CONTRACT_DEFINED; BROWSER_NOT_VERIFIED |
| Fish focus | Existing SAM ViT-B experiment chain reused; trusted-mask / bbox-only / none gate; three gold layers share one contour | EXPERIMENT_REUSE_DEFINED; PRODUCTION_MASK_NOT_QUALIFIED |
| Chinese glyph transfer | Transfer only after R1; R3 unreadable, R4 Chinese-only title readable, R5 afterglow zero | CONTRACT_DEFINED; BROWSER_NOT_VERIFIED |
| Responsive and performance | Nine viewports, multi-scene photo matrix, desktop/mobile frame-time and dropped-frame evidence | NOT_RUN |

**SAM pipeline boundary:** reuse the existing product implementation in pan277942135/Yujian: app/segmentation_api.py, app/segmentation/service.py, app/segmentation/mask_generator.py, app/segmentation/quality_gate.py and app/segmentation/cutout_builder.py. The existing POST /api/debug/fish-segmentation endpoint is an experiment/debug route, not a public production API. Use a server adapter; do not create a duplicate service, retrain SAM, treat a BBox as a contour, or promote heuristic GOOD to production qualification.

**Current blocker:** Product source is synced with main and exact-source targeted CI passes. The current PR HEAD `8edce561c3b6f25263eb32f1d72706fec4e329b1` has not yet been deployed. The previous probe was invalid due to missing authentication and fixture rate limits; authenticated live inference/SAM, current-source Revision, real-photo browser playback, production Mask qualification and visual acceptance remain pending.


## Final visual baseline freeze addendum — 2026-10-24

The source-identity blocker is closed: `web-three-stage` is the unique private Web V2 pixel baseline, SHA-256 `27c34ee5d3cf90921df433cb43c671e42b8ffa80b8f5cebfe9b397da2ee853e2`, 1536×1024, 1,804,497 bytes. The freeze record is [VISUAL_BASELINE_FREEZE_V1.md](VISUAL_BASELINE_FREEZE_V1.md). The historical 14-frame poster remains direction-only; current timing authority is SWOS V1.1, 15 frames / 3650ms. This closes baseline identity and comparison-protocol definition only. Runtime screenshot parity, current-source deployment, real-photo playback, production Mask, performance evidence and user visual acceptance remain open. Public permission is still `NOT_CONFIRMED`; private assets stay local-only.
