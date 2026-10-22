# AI 渔获识别 Web 全量高保真与工程合同 V2

**ID** `YUJIAN_AI_CATCH_RECOGNITION_WEB_HIFI_V1` · **状态** `VISUAL_CONTRACT_DEFINED`（Main 已采纳的设计合同） · 用户认可的三阶段与水光方向 `USER_APPROVED_VISUAL_DIRECTION`；`MAIN_ADOPTED=YES (DESIGN_CONTRACT_ONLY)`；`PIXEL_FROZEN_SOURCE=NOT_CONFIRMED`；`RUNTIME_VISUAL_VERIFIED=NO`。#186/#187 合同内容由 PR #190 纳入 Main；Web PR #177 当前审计 HEAD `655c4af07a0244fcf29441b5652c48ca74f2e6a2` 仍为 Draft/Unmerged。不得声称原图像素冻结、真实 Web 视觉 PASS 或产品代码已验收。机器文件在 `contracts/`，结构先行见 `WEB_HIFI_HANDOFF_A_V2.md`。

## 视觉来源与边界

用户确认的原始三阶段 Web 图在当前仓库未登记。Library 中找到 `yujian_ai_fish_identification_hifi_static_v1.png` 候选（2560×1440，文件身份 `libfile_4322907ab4ec8191b0199e89e4918483`），已核对本地字节 SHA-256 `5f07c2f65ea33f14c8517dddbb7f8d6b7369a6ed980cba11cc92014f4e6a75f9`，画面具有品牌顶栏、近等宽三阶段卡与下方五节点；但缺最终批准记录，精确像素合同和公开权限待核。此文件不进入公开仓库，也不作为 `PIXEL_FROZEN_SOURCE`。本合同的 Web 尺寸、色彩补全均标为 `DESIGN_COMPLETION_NOT_PIXEL_MEASURED`，只有现行共享系统与 Result 已冻结数值标为源冻结。`208758.jpg` 的已知 SHA 是 `ab7227cf7205ee2ac342b6cd75cd899747879506c95c33b888b6adf0acb47987`，含人物及水印，公开权限不明，不提交照片或派生图。

## 页面与视觉

三阶段结构、组件 ID 和底部五节点见 `Page_Structure_V2.json`。入口与认证保持 Platform V1；在产品路由采用宽阔的居中 Product Shell，侧栏让出空间，返回后恢复。桌面横向三阶段为视觉总览；当前阶段及未开始阶段必须可辨。平板两列、手机活跃阶段聚焦是设计补全，须在批准原稿完成对照后复核。底部节点不可冒充 Detector/Crop/Classifier 的实时进度。

颜色、字号、间距、卡片及按钮见 `Visual_Tokens_V2.json` 和 `Component_Geometry_V2.json`。CSS 示例：`.catch-product{max-width:1280px;margin-inline:auto;padding-inline:var(--web-gutter,32px)}`；`grid-template-columns:repeat(3,minmax(0,1fr));gap:18px` 仅是待原稿校准的桌面设计补全。正文和表单不使用 10–12px 微字；系统字族 CJK fallback；最小触控目标 44px。晨光金用于有意义时刻，不覆盖所有控件。上传、重选、识别、状态、地点、时间、返回应映射共享 `Icon_Registry_V3.json` 的语义 ID；Web 无可用图标资产时标注待映射，不以 emoji 冒充冻结图标。

## 上传

空态提供清楚的拖放与选择入口；拖入态边界反馈、可访问的键盘选择；选择后展示原图 EXIF 正向后的完整 `contain` 预览、文件名/尺寸、重选和清除。横图/竖图/长图/窄图不拉伸、不可切鱼头鱼尾。空文件、MIME 不支持、超服务上限、解码失败、提交失败分别提供对应错误和恢复 CTA。格式/大小从前后端验证结果读取，不能单凭概念稿写死。提交时防重复、允许取消，重新选择清除旧会话结果和 bbox。示例照片入口可预留，但此版本不依赖示例素材。

## SWOS 分层

唯一自然水光语言：晨光入水 → 水纹扩散 → 水光寻鱼 → 鱼体浮光 → 光归于鱼。任何岸上/室内/夜间真实鱼获均采用同一种光学隐喻；不改变照片场景。七层共享原始照片坐标，参数经实际视觉校准，曲线相对值不是 CSS alpha。

| 层 | 输入与区域 | 合成及降级 | 静态验收 |
|---|---|---|---|
| Photo Base | EXIF 正向原图；完整显示 | 正常色彩，始终可读；无补画 | P0 原图清晰 |
| Ambient Water | 照片下缘起的宽柔低密度水光 | 弱 screen/soft-light 意图；低性能先简化 | P1 温和苏醒，不染背景 |
| Caustic Field | 多尺度、非均匀面状焦散，向上漫延 | 独立场，边缘柔和；低性能减采样 | P2/P3 面状光斑而非蜘蛛网 |
| Ripple Field | 少量方向性局部水纹 | 不封闭扫描环；可先关闭 | P3 局部传播可辨 |
| Peripheral Refraction | 图片外围低亮折射 | 不强于鱼体；性能不足关闭 | P4 外围不形成蓝金边框 |
| Fish Focus | 可信 Mask 或 BBox 信任等级 | Mask: 共用几何的 Core、Soft Glow、Halo；仅 BBox: 克制区域柔光；无几何: 关闭 | P4–P7 鱼鳍鳞片不被盖住 |
| Residual Gold Trace | 已有鱼体光与共享照片变换 | 只对有效 HIGH、可信几何转移；无 Mask 用非精准降级 | R0–R5 同源、无凭空标题光 |

水光在 P0–P7 的相对强度 `0,10,45,75,60,35,10,0`；鱼体金光 `0,0,15,45,100,100,100,100`，P4（1550ms）达峰，至 P7（2800ms）保持 1250ms。P6/P7 外围消散而鱼体金光仍为视觉中心。独立调 `caustic density/scale/softness`, `ambient radius/luminance`, `ripple amplitude`, `refraction strength`, `gold core width/softness/halo spread`，并用多真实照片验收。禁止旧七路径 Edge Field、机械扫描线、雷达/HUD、封闭霓虹框、均匀蓝蒙版。SAM ViT-B 先评估；不达标才比 SAM 3/3.1。人工参考 contour 不授予生产 Mask 资格。

## 结果及业务

3+2 状态与技术失败的文案、Hero、操作见 `Page_State_Matrix_V2.json`。HIGH 复用 Frozen Result：导航、HIGH Hero 322×210dp/360dp、Hero 下中文鱼种名、必要置信信息、长度重量时间地点、故事、双 CTA。现行冻结布局中「继续记忆」与「保存本次鱼获」为 CTA 文案；产品目标如果要求「继续记录记忆」，应先在 Result Authority 中解决措辞差异，不能擅自覆盖。英文科学名及空行都不显示。Web 记忆目标路由不存在则禁用并说明，不制造假链接。MEDIUM 候选需确认，LOW 人工选择，NO_FISH 与 IMAGE_QUALITY 提供重选，TECHNICAL_FAILURE 显示重试。阈值不在设计层重设；App 观察低置信 0.45 与后端默认 0.55 的差异保持 OPEN。

业务图像校验 → Detector/Quality → Crop → Classifier → 3+2 由真实事件驱动。Processing 光效是视觉状态，不能用时间标记模型完成。真实推理晚于 2800ms 则延长克制处理态；真实 HIGH 未就绪不揭晓。取消或新会话清除全部旧照片引用、BBox、Mask、鱼种、光效和定时器；Reduce Motion 仍保留可操作结果。

## 共享照片与时序

直接引用 #187 机器合同，Processing 2800ms（P0–P7 八帧），RR09 850ms（R0/R0.5/R1/R2/R3/R4/R5 七帧），总 3650ms。R0 是 Result 首个可见帧；R0.5 +100ms/全局 2900ms；R1 +200ms 落 Hero；R2 +380ms 才完成主要金光转移；R3 +550ms 笔画仍未完整；R4 +680ms 中文名可读；R5 +850ms 余辉消失。R0.5 不加等待。

会话保留原图 ID/SHA、正向宽高、Processing 内容矩形、Result Hero、fit/crop、BBox/可信 Mask、session ID、goldTraceState。照片、鱼体几何与金光从同一源图坐标经同一矩阵变换；不能复制一张图交叉淡入，也不能第二次裁剪。360×640 参考 P 前景 `(0,80,360,480)`、R0.5 容器 `(9.5,26.5,341,425)`、前景 `(50.625,66.5,258.75,345)`、R1 Hero `(19,53,322,210)`、前景 `(101.25,53,157.5,210)` dp；桌面以实测源/目标 rect 推导，不硬编码手机坐标。纵图优先按可信 BBox 安全裁剪；不安全则同源完整前景+低对比同源背景。无可信 Mask 不能制造精准轮廓向标题转移。

## 响应式与验收

`Responsive_Rules_V2.json` 覆盖 1440×1000、1280×800、1024×768、768×1024、480dp、430×932、390×844、360×640、320×480。断点是设计补全，原稿身份确认后复核。大屏不缩字；小屏换列、表单单列、双 CTA 必要时纵排、五节点可横向滚动，正文不溢出；键盘及安全区不挡 CTA。静态合同通过不等于真实 Web 视觉通过。测试门见 `Acceptance_Matrix_V2.json`；Work B 按 `Work_B_Implementation_Mapping_V2.json` 和 `WEB_HIFI_GAP_MATRIX_V2.md` 修复既有 PR。
