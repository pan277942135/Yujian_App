# 渔见 AI · SWOS Web 高保真动效技术合同 V1.0

**Contract ID:** YUJIAN-SWOS-WEB-HIFI-MOTION-TECHNICAL-V1_0  
**Version:** 1.0  
**Authority status:** MAIN_ADOPTED_DESIGN_ONLY（随本变更进入 Main 后生效）  
**Runtime / browser visual acceptance:** NOT_VERIFIED  
**Scope:** 渔见 AI Web 的真实可播放 SWOS 高保真动效、评审控制台与精确源码预览；不替代产品业务合同，也不代表 Android Runtime 已验收。

## 1. 本合同解决什么问题

本合同把现有 SWOS 光学规范、15 帧关键帧、Processing → Result 共享照片变换、RR09 V3、Web V2 页面合同及已有 SAM 鱼体分割实验链路绑定成一份 Work 可直接执行的工程输入。它不是另起一套设计，也不把静态分镜或 UI 模拟当成可播放动效。

**权威关系：**

1. 产品页面结构、状态、真实识别结果和响应式页面语义： [WEB_HIFI_CONTRACT_V2.md](WEB_HIFI_CONTRACT_V2.md) 及其机器合同。
2. 光学层职责和品牌视觉红线： [SWOS_Optical_Contract_V1.md](../processing/optics/SWOS_Optical_Contract_V1.md) 与 [SWOS_Static_Visual_Contract_V1_1.md](../processing/optics/SWOS_Static_Visual_Contract_V1_1.md)。
3. 精确时序与 15 帧： [SWOS_15_Frame_Keyframe_Contract_V1_1.json](../processing/optics/SWOS_15_Frame_Keyframe_Contract_V1_1.json)。
4. 同一张照片从 Processing 接续到 Result 的几何： [SWOS_Shared_Photo_Transition_Contract_V1.json](../continuity/SWOS_Shared_Photo_Transition_Contract_V1.json)。
5. RR09 V3 结果揭晓与现有 Hero 布局： [recognition_result_motion_contract_v3.json](../result/motion/recognition_result_motion_contract_v3.json) 与 [hero_media_contract.json](../result/media/hero_media_contract.json)。
6. 本合同拥有 **Web 播放器架构、SAM 实验链路复用边界、评审控件、响应式播放验收和性能证据要求**。既有 [SWOS_Dynamic_Demo_Technical_Handoff_V1.md](../processing/optics/SWOS_Dynamic_Demo_Technical_Handoff_V1.md) 保留为技术背景和实现拆解，不得覆盖本合同的验收边界。

若文档之间出现冲突，按以上领域归属解决；不得通过“测试通过”推断视觉合格，也不得为让画面看起来顺畅而修改冻结的产品语义。

## 2. 已确认且不得重新解释的结论

- **3650ms 名义视觉时间**：Processing 2800ms + RR09 850ms；不增加等待时间，不增加业务状态。
- **15 个样本**：P0–P7 八帧、R0/R0.5/R1/R2/R3/R4/R5 七帧。R0.5 是 R0→R1 既有 200ms 接续中的中点，位于全局 2900ms，不是额外 hold。
- **同源照片连续接续**：P7 → R0 → R0.5 → R1 使用相同的原图字节、EXIF 正向结果、鱼体几何和单一共享变换；不能用两张独立照片交叉淡入伪装。
- **鱼种名称只显示中文名**：不得预留英文科学名行。真实结果必须来自真实识别输出；Demo 占位文案必须明确标记为占位，不能冒充模型预测。
- **真实推理由事件驱动**：3650ms 不是模型推理截止时间。真实可用的 HIGH 结果尚未就绪时，不得提前揭晓；实际推理较慢时可延长克制的 Processing 状态，不得伪造进度或结果。
- **水光是渔见自己的自然光学语言**：清晨水面、低至中密度不规则面状焦散、柔和蓝绿与少量淡金；禁止助手唤醒框、HUD、雷达、机械扫描线、闭合霓虹边框、旧 Edge Field 七路径几何。
- **SAM 鱼体分割实验链路复用**：PR #177 已增加同请求适配器：`POST /api/product/catch-recognition/infer` 使用本次 detector primary BBox 与原图 SHA 调用现有 `generate_fish_cutout`；候选开关 `CATCH_SWOS_SAM_CANDIDATE_ENABLED` 默认关闭。浏览器不直接访问调试 API，不新增重复服务、不重训模型、不把分割结果当作分类输入。适配代码尚未在当前源码的候选 Revision 上运行；SAM 质量标签本身不等于生产级 Mask 认证。
- **私有照片不公开**：208758.jpg、用户确认的原始三阶段参考图、历史分镜及其衍生帧仍受既有隐私/发布限制；公开测试只使用具有明确来源、许可证和摘要校验的测试照片。私有照片不得被上传到公共测试端点或提交进公开仓库。
- **Result CTA 权威已统一**：使用冻结的 Result V1.1 页面级合同 `design/pages/recognition/result/engineering/layout_geometry_contract.json`（blob SHA `2df910cda117e7c8be1e211bae17899e14c3933f`）中的「继续记忆」与「保存本次鱼获」。该页面级权威覆盖通用 Action Button 示例文案；目标路由未开放时禁用并解释，不假跳转。
- **置信度权威已按接口职责统一，数值不变**：Web 只消费 `/api/product/catch-recognition/infer` 返回的 `result_state`。现有产品 Result 状态映射为 top1 < 0.45 → LOW、top1-second < 0.12 → MEDIUM，依据 `recognition_result_3plus2_decision_contract_v1_1.json`（blob SHA `a678f63082d2f045dd659ed42c36df14c475f9c1`）；`app/inference_api.py` 的 0.55 `low_confidence` 是独立诊断字段，不覆盖产品 `result_state`。本次不修改模型阈值、质量门或路由；未来校准需产品负责人批准及标注留出集证据。
- **资产授权与冻结状态明确**：`208758.jpg` 已于 2026-10-23 本次执行重新校验 SHA-256 `ab7227cf7205ee2ac342b6cd75cd899747879506c95c33b888b6adf0acb47987`、1152×1536、331421 bytes、JPEG、EXIF orientation 1。三阶段原图与历史海报沿用此前私有交接的 SHA/尺寸记录，本次未重新取得原始字节。所有原图/衍生帧 `PUBLIC_PERMISSION=NOT_CONFIRMED`、`PUBLIC_PUBLICATION_ALLOWED=false`；仅允许本地私有查看，不进入公共仓库、CI artifact 或 Pages。`PIXEL_FROZEN_SOURCE=NOT_CONFIRMED`：方向参考不等于像素冻结基准。

## 3. 精确时间线与揭晓行为

| 样本 | 相对所属阶段时间 | 全局名义时间 | 必须观察到的行为 |
|---|---:|---:|---|
| P0 | 0ms | 0ms | 原图完整、清晰，光场未抢占主体 |
| P1 | 350ms | 350ms | 水面环境光开始自然建立 |
| P2 | 950ms | 950ms | 不规则面状焦散变得可辨 |
| P3 | 1200ms | 1200ms | 水光向上扩散，不能形成扫描线 |
| P4 | 1550ms | 1550ms | 可信鱼体金光达到峰值 |
| P5 | 2050ms | 2050ms | 鱼体三层高亮稳定保持 |
| P6 | 2600ms | 2600ms | 外围水光开始退场，鱼体金光保留 |
| P7 | 2800ms | 2800ms | Processing 收束；可信鱼体仍是视觉中心 |
| R0 | 0ms | 2800ms | Result 首个实际呈现帧，继续使用同源照片 |
| R0.5 | 100ms | 2900ms | 照片与容器均进入几何中点，不可仅 crossfade |
| R1 | 200ms | 3000ms | 同一张照片落入冻结 HIGH Hero；之后才可启动金光转移 |
| R2 | 380ms | 3180ms | 金光主要转移到真实中文鱼种标题的目标区域 |
| R3 | 550ms | 3350ms | 中文字形遮罩仍处于不完整、不可读阶段 |
| R4 | 680ms | 3480ms | 真实中文鱼种名完整可读；无英文名行 |
| R5 | 850ms | 3650ms | 余辉完全消散，结果页静止且所有 CTA 可操作 |

播放器必须使用一个确定性时钟 elapsedMs，全部图层消费同一时间值。帧跳转、暂停、单步和慢放只改变播放器的采样，不改变合同时间、模型状态或业务状态。

## 4. 照片坐标、几何与接续

### 4.1 单一源与单一变换

照片只做一次 EXIF 正向处理，保存源图 SHA-256、正向宽高、session/image identity、Processing 的 photoContentRect、Result Hero 的目标矩形、fit/crop 策略和可信鱼体几何。用同一个变换矩阵 M(t) 驱动：

- 原图前景；
- 同源、低对比的模糊背景兜底；
- Mask / contour；
- Gold Core、Soft Glow、Halo；
- 从鱼体到中文标题的 Residual Gold Trace。

任何图层不得自行二次裁切、重新估计鱼体位置或创建另一套动画时钟。照片不得拉伸、换图、生成不存在的补画像素，letterbox 区域不得绘制光效。

### 4.2 R0.5 中点

R0.5 的外层容器矩形与照片前景矩形，分别取 R0 与 R1 对应矩形的数值中点。禁止把静态图片淡入/淡出作为几何接续的替代。R1 的目标尺寸、裁切和安全区以当前 Frozen Result HIGH 与 Hero Media Contract 为准，不在 Web Demo 内发明另一套 Result 版式。

### 4.3 纵横图与安全兜底

- 优先完整呈现原图；只有现有 Hero Media Contract 的可信安全裁切条件成立时才可采用安全裁切。
- 可信裁切会损伤鱼头、鱼尾或主要鱼体时，使用完整同源前景叠加同源低对比背景。
- 横图、竖图、窄图、EXIF 旋转、低分辨率图均不得拉伸。
- 若鱼体几何缺失或不可信，照片接续仍必须成立，但鱼体轮廓、精确金光起点与轮廓追踪全部关闭。

## 5. 七层光学渲染与降级

按既有 SWOS V1 的层职责实现，不能把旧 Edge Field 七条路径换色后冒充 SWOS：

1. **Photo Base**：原图，始终清晰、真实、可读。
2. **Ambient Water**：照片可视区域中的宽柔环境水光，不做整屏色罩。
3. **Caustic Field**：多尺度、不规则、边缘柔化的面状焦散，主要向上漫延；不可成为密集蜘蛛网。
4. **Ripple Field**：少量局部方向性水纹；不得组成同心扫描环。
5. **Peripheral Refraction**：外围低亮折射；不得构成闭合蓝金框。
6. **Fish Focus**：可信鱼体几何的三层高亮；与环境水光分开控制。
7. **Residual Gold Trace**：在照片落入 Result Hero 后，从可信鱼体几何向真实中文标题区域转移。

鱼体金光三层必须共用同一 Mask/contour 与 M(t)：

- **Gold Core**：窄、清晰；
- **Gold Soft Glow**：贴近同一轮廓；
- **Gold Halo**：较宽、低亮度。

既有相对强度曲线是视觉相对值，不是 CSS alpha、shader uniform 或已校准性能参数。渲染器应将焦散密度/尺度/柔化、环境光半径/亮度、波纹幅度、折射强度、Core 宽度/柔化和 Halo 扩散作为独立可调参数；不得通过只增大 Alpha 或椭圆尺寸来替代空间分布设计。

### 渲染器选择与降级顺序

1. 启动时探测 WebGL 2 能力与 context loss；具备能力不代表性能达标。
2. WebGL 2 不可用、被禁用、context 丢失或性能不达标时，降级到 Canvas 2D 的缓存低分辨率焦散纹理。
3. Canvas 2D 离屏层分配失败时，降级到静态光学样本，仍保留照片接续、结果文案与 CTA。
4. prefers-reduced-motion: reduce 时，取消装饰性焦散传播、鱼体辉光运动和字形旅行，直接呈现可操作的稳定结果。
5. 任何降级只允许降低装饰性分辨率、次级斑块数量、模糊精度和 Halo 扩散；**不得改变**源照片身份、鱼体坐标、时间线、揭晓顺序、结果内容、CTA 或 3650ms 名义视觉合同。

## 6. 复用现有 SAM 鱼体分割实验链路

### 6.1 现有实现，不另建重复链路

当前产品仓库 pan277942135/Yujian 已有实验链路：

- API/调试页：app/segmentation_api.py，现有实验接口为 POST /api/debug/fish-segmentation；
- 协调服务：app/segmentation/service.py；
- SAM Mask 生成：app/segmentation/mask_generator.py；
- 质量启发式：app/segmentation/quality_gate.py；
- 透明鱼体 cutout：app/segmentation/cutout_builder.py；
- 当前实验模型：SAM ViT-B，代码中的 checkpoint label 为 sam_vit_b_01ec64。

该路径已经包含“检测候选 → BBox prompt → SAM mask → mask 质量启发式 → 透明 cutout/叠加预览”的可复用能力。Web 实现应先复用它，并以服务端 adapter 隔离产品 UI 与实验接口；不得在浏览器中硬编码私有服务地址、凭据或直接把 debug endpoint 暴露成公开生产 API。服务可用性、候选 Revision、访问控制和当前源码 SHA 必须在执行时重新核验。

**不允许：** 再造一套 SAM API、重复下载/加载另一份 checkpoint、重训 SAM、让 Mask 参与鱼种分类、用 BBox 冒充轮廓、用启发式 GOOD 标签直接宣告生产合格。

### 6.2 几何信任门控

| 状态 | 允许的视觉表现 | 明确禁止 |
|---|---|---|
| TRUSTED_MASK | 由已验证 Mask 生成单一 contour；Core/Soft Glow/Halo 共用几何 | 各层分别拟合轮廓；跨照片复用 Mask；把分割作为分类结果 |
| TRUSTED_BBOX_ONLY | BBox 区域的克制柔光，可继续照片接续 | 精准鱼体描边、鳍尾轮廓、以矩形边缘模拟鱼体、基于假轮廓转移金光 |
| NONE / stale / invalid | 关闭鱼体空间聚焦与金光追踪；保留正常照片和业务页面 | 凭空生成鱼体轮廓、保留上一张照片的 Mask 或金光 |

进入 TRUSTED_MASK 至少要求：图像/session 身份匹配；EXIF 正向后的宽高与 Mask 维度一致；检测 BBox/confidence 与 Mask 绑定同一张图；Mask 非空且轮廓有限、稳定；既有质量门通过；输出未过期。沿用现有检测和业务阈值，不在本合同中重设模型阈值。任一校验失败即降级，不得通过放宽信任门“修复”视觉效果。

SAM 的 GOOD 是候选质量启发式，不等于生产资格。生产级 Mask 的晋级还需人工真值 Mask、IoU/Dice 与边界质量指标、夜拍/黑底/遮挡等硬案例审查及明确的视觉签核。在这些证据齐备之前，生产资格必须保持 NOT_QUALIFIED。

### 6.3 图片隐私与测试来源

- 公开云端质量测试只使用清单中带来源 URL、许可、归属和内容摘要校验的公开测试照片。
- 用户私有照片与未确认公开许可的 208758.jpg 只能按已批准的私有、本地查看流程处理；不得提交进公开仓库，不得上传到公共预览或公共 CI。
- API/SAM 耗时只代表模型服务链路，不等于 Web 光学渲染 FPS，也不等于照片视觉验收。

## 7. 中文标题金光转译

- 只有真实 RESULT_HIGH 结果满足现有业务门控、Result 页面实际呈现并到达 R1 后，才允许金光从可信鱼体几何向现有中文鱼种标题区域转移。
- 目标区域由现有 Result 标题组件测量，不得硬编码一个独立的发光标题。
- 用最终中文字体实际绘制 glyph mask；字体缺失时回退到静态中文标题，不得以英文替代，也不得阻塞 CTA。
- R3（全局 3350ms）字形不完整且不可读；R4（全局 3480ms）真实中文名完整可读；R5（全局 3650ms）装饰余辉清零。
- RESULT_MEDIUM/LOW、NO_FISH、IMAGE_QUALITY 和 TECHNICAL_FAILURE 不得伪装成 HIGH 揭晓；它们沿用 Web V2 既有状态与操作。
- 动效结束后标题、长度/重量/时间地点、故事区和现有双 CTA 必须可操作；动画不得拦截点击、键盘焦点、返回或保存。

## 8. 可播放评审控制台

本合同要求交付**浏览器中真实可播放的实现**，不是分镜图、静态 SVG 海报、假进度条或一组独立 PNG。

评审控件至少包括：

- Play / Pause；
- 前一帧 / 下一帧单步；
- 精确跳转到全部 15 个样本；
- 0.25× / 0.5× / 1× 播放速度；
- 图层开关与有限范围的强度参数；
- 原图 / 合成效果对照；
- 照片内容矩形、R0/R0.5/R1 几何叠加；
- 当前照片 ID/SHA、Mask 信任状态、渲染器/降级模式、viewport/DPR、浏览器版本、参数和精确源码 SHA；
- 导出可复现的参数 JSON；导出不能修改生产数据、模型参数或业务状态。

同一组输入 (elapsedMs, photo identity, geometry, opticalConfig, recognitionState) 必须得到可重复的渲染状态。暂停/单步/跳转不能启动独立计时器，切换照片必须清空旧 session 的检测框、Mask、鱼种和光效缓存。

## 9. 响应式与性能门槛

### 9.1 视口与照片矩阵

至少覆盖 Web V2 已列视口：1440×1000、1280×800、1024×768、768×1024、480dp 宽、430×932、390×844、360×640、320×480；另对竖图/横图、EXIF 旋转、低分辨率、岸边/草地、室内/夜拍/低光和缺失/不可信 BBox 做照片矩阵。

每组必须检查照片不拉伸、不误裁、Mask 与鱼体边缘贴合、R0.5 中点正确、标题不截断、键盘/安全区不遮挡 CTA、页面可返回，且手机/桌面均可完成 3650ms 播放或 Reduce Motion 静态回退。

### 9.2 可量化性能目标

以下为**待实测的验收目标，不是已经达成的结果**：

- 桌面目标环境：渲染帧时间 P95 ≤ 16.7ms，超出目标帧预算的帧比例 ≤ 5%。
- 代表性真实移动设备：渲染帧时间 P95 ≤ 33.3ms，超出目标帧预算的帧比例 ≤ 5%。
- 热身后不得出现持续性 >100ms 的渲染停顿；记录 Long Task、丢帧、context loss、所选 renderer、画布/纹理分辨率与设备信息。
- 同时记录 SAM API 的检测耗时、Mask 生成耗时和端到端网络耗时，但必须与浏览器 render frame-time 分开报告。
- CI 中的 Chromium UI simulation 只证明路由/交互/布局的自动化检查；除非实际采集并保存帧时间、截图/录屏和精确源码 SHA，否则不得报告浏览器动效性能 PASS。设备模拟尺寸不等于真实移动设备性能证据。

如果性能未达标，先定位耗时层并按既定顺序降级装饰性渲染；不得通过缩短合同时间、删除照片接续或降低鱼体几何精度来伪造性能通过。

## 10. Work 交付物与验收 Gate

每次交付必须绑定**同一个精确产品源码 SHA**，并提供：

1. 可直接访问、真实可播放的浏览器预览 URL；页面显示构建 SHA、合同版本与 renderer/fallback 状态。
2. 预览 Revision、健康检查的源码 SHA、HTTP 状态及“默认生产流量未改变”的证据（如使用 Cloud Run 无流量候选）。
3. 3650ms 完整录屏与逐帧采样，15 帧均可精确跳转；附 P7→R0→R0.5→R1 几何叠加与照片 SHA。
4. 多场景照片报告，逐张记录 detector/BBox、Mask 信任状态、质量启发式、Mask/cutout 预览与错误降级。
5. 桌面和真实移动设备的渲染帧时间、丢帧、renderer fallback、DPR/viewport、浏览器/设备版本。
6. 九视口响应式截图与失败案例；包括长图、横图、夜拍、无 Mask/低信任几何和 Reduce Motion。
7. 自动测试、控制台错误、HTTP 错误、精确 SHA、合同 SHA、参数导出及全部证据的索引。

**独立 Gate：**

- G1 CONTRACT：机器合同、正式 15 帧、共享几何、Web V2/RR09 V3 与隐私边界一致。
- G2 SOURCE：PR #177 当前 HEAD、产品 main 对比、精确 SHA CI、预览 Revision/健康 SHA 全部一致；源分支落后 main 时必须先同步并重跑门禁。
- G3 PLAYBACK：浏览器真实播放完整 3650ms，15 样本、暂停/单步/跳转、R0.5 几何和中文揭晓时序通过。
- G4 MASK：复用既有 SAM 实验链路；信任门与降级通过；未完成真值评估前生产 Mask 资格仍为 NOT_QUALIFIED。
- G5 RESPONSIVE：全部视口、照片场景、键盘/安全区、Reduce Motion 通过。
- G6 PERFORMANCE：按本合同记录真实帧时间与丢帧；SAM API latency 不可替代渲染性能。
- G7 VISUAL：对正式合同与真实浏览器录屏/截图逐项审查水光空间分布、真实鱼体轮廓、照片连续接续、金光向中文字体转译和余辉消散。

G1 或 CI PASS 不自动通过 G3–G7。静态关键帧、手绘 Mask、HTTP 200、SAM 启发式 GOOD、UI 模拟、构建成功和 AI 自述都不能代替真实浏览器视觉验收。

## 11. 当前已知状态（审计快照，不是永久 SHA）

- Design authority repository：pan277942135/Yujian_App，本次审计基线为 main@da9976090021232f0eec936bc4d30f9fa59d90f7。
- Product repository：pan277942135/Yujian。
- 本轮实时审计 PR #177 HEAD：f5b74cc7628131863f40a1a43458026ff49a2eb5；产品 main：738954105d545d9c354136b8b93f3562058fcf30；比较结果 48 ahead / 0 behind。精确 SHA CI [run 40054890544](https://github.com/pan277942135/Yujian/actions/runs/40054890544) 为 PASS（validate + Chromium/UI simulation + offline SWOS probe unit tests）；不包含认证后的真实推理或视觉验收。此前候选探测 [run 39982073543](https://github.com/pan277942135/Yujian/actions/runs/39982073543) 的 0/5 Mask 结论无效：2 次请求 401 未登录、3 次 Wikimedia 下载 429，未完成有效的真实推理测试。
- PR #182/#183 的基础设施变更已合入产品 main。当前 no-traffic workflow 为 manual-only，要求输入精确 PR #177 HEAD；旧候选 Revision `yujian-model-factory-console-00349-hes` 仍绑定 6da640859f063d7f42ee5432e6b35ecb162802ff，并非当前 HEAD f5b74cc7628131863f40a1a43458026ff49a2eb5。新探测使用 Actions secret `CONSOLE_ACCESS_KEY`，缺失时会明确失败为 `AUTH_NOT_CONFIGURED`。
- CI fixture playback 状态仍为 `BROWSER_VERIFIED_FIXTURE_ONLY`；当前 PR #177 HEAD 尚未部署；旧候选 Revision `yujian-model-factory-console-00349-hes` 的探测为 `BLOCKED_AUTH_AND_FIXTURE_429`；新探测认证/素材重试代码已合入 main，但尚未对当前 HEAD 运行；部署运行视觉状态 `NOT_VERIFIED`；生产级 Mask `NOT_QUALIFIED`。
- 上述 SHA 是本次审计快照；后续执行必须重新读取实时 PR、main、CI 与预览健康信息，不能把本文中的历史 SHA 当成永久常量。

## 12. 权威机器合同

机器可读合同：[SWOS Web Hi-Fi Motion Technical Contract V1.0 JSON](contracts/SWOS_WEB_HIFI_MOTION_TECHNICAL_CONTRACT_V1_0.json)。

本合同只冻结工程输入和验收方式，不宣告播放、Mask、性能或视觉已通过。
