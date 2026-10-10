# 渔见 · 鱼获图片自适应显示设计 V1
Status: **FROZEN — DESIGN AUTHORITY** · Date: 2026-10-10
Scope: **Design Manager / 公共设计系统**。不包含 Android 实现、运行截图验收或冻结页面外层 Geometry 变更。

## 0. 权威关系与继承
本合同统一图片的**容器内部**显示，不改变各页面的原始布局、文字、交互或冻结 PNG。
冲突优先级：页面冻结外层 Geometry / 状态权威 > 页面既有媒体专项合同 > 本公共系统通用规则 > runtime。
- Recognition Result: `design/pages/recognition/result/media/Recognition_Result_Hero_Media_Contract_V1.md`、`hero_media_contract.json`（V1.1 FROZEN）
- Detail + HOME: `design/system/core_visual_v1/components/YuJianCatchHeroCard_Media_Edge_States_V1.md`（FROZEN）
- My Catches: `design/pages/fish_records/list/My_Catches_List_Image_Spec_V1.md`（FROZEN）
此 V1 仅统一入口、决策树、案例与引用；不覆盖原来的更严格限制。

## 1. 全局输入
`sourceMediaId`（同一 FishRecord 绑定的原始实拍照片）、`orientedSourceSize`、`containerContentRect`、可选 `fishBbox`（坐标换算到 EXIF 校正后空间）、`bboxReliability`、`pageState`。
原始字节不修改。应用 EXIF 后等比缩放，不允许 Stretch、AI 补全、鱼体修复、无关图库替换或图像重构。不以模型分类裁切图充当原始展示图。
FishRecordDetail B 面生成资产/鱼鉴知识图不得替换 A 面或列表的原图。

## 2. 模式与页面映射
| 页面/状态 | 主模式 | 安全回退 | 同源柔化背景 |
|---|---|---|---|
| 选择图片识别 / 识别前原图预览 | `EVIDENCE_FIT` | 无；始终完整 Fit | 禁止 |
| 识别结果 High/Medium/Low | `SUBJECT_CROP_FILL` | `SUBJECT_SAFE_FIT` | 禁止 |
| 识别结果 No Fish/Image Quality | `EVIDENCE_FIT` | 无；必须全图 | 禁止 |
| 详情 A 面 Hero | `HERO_ADAPTIVE`（安全 Fill） | 同源 ambient + 主图 Safe Fit | 允许 |
| 有数据首页共享 Hero | `HERO_ADAPTIVE`（复用组件） | 同源 ambient + 主图 Safe Fit | 允许 |
| 我的鱼获 V2 列表 | `THUMBNAIL_SUBJECT_CROP` | 保护主体的较宽松 Crop → Fit/SoftWater | 禁止 |

## 3. EVIDENCE_FIT
按完整原图（已校正方向） `contain`，四边必须可见。容器剩余空间使用页面已定义的中性/SoftWater 底色，不伪造相片内容，不用黑色硬条把 Fit 伪装为 Crop。
**相机实时预览的裁切由原 Camera 合同管理；已选择/实际提交识别的单张照片必须 Fit。**

## 4. SUBJECT_CROP_FILL — 识别结果
High/Medium/Low 沿用冻结 V1.1：
- 原图经 EXIF 矫正；从**真实可靠** Detector bbox 构建 FishSafeRect；横向外扩 0.14、纵向外扩 0.18；视觉安全内边距 12dp。
- 只在填充容器后 **100% FishSafeRect 可见**且 12dp 安全内边距成立时使用 Fill。
- 触及原图边缘（≤2%）不能进一步裁掉该边缘；鱼头、鱼尾与 bbox 不得丢失。
- 缺 bbox、无效 bbox、主体过长或安全区无法保证，则使用 `SUBJECT_SAFE_FIT` 展示整张原图。
- No Fish / Image Quality 必须 `EVIDENCE_FIT`；不得推测鱼的位置。
- 禁止模糊重复图作支撑、生成填补、后加载二次裁切跳动；切换候选鱼种不得改变展示 sourceRect。

## 5. HERO_ADAPTIVE — 详情 A 面 / HOME
沿用同一个 `YuJianCatchHeroCard` 的 frozen 外框、圆角、标题/元数据排版。若能完整保留可信鱼体，优先安全 Fill；否则主照片 Safe Fit、同一张原图可作**低对比、柔化、从属** ambient 底图，占据剩余空间。主照片仍清晰、主导。不能引入其他照片、AI 合成背景或纯黑信息条。
经检测确认的嵌入式均匀纯色黑带，仅可在 presentation 层剔除；夜景真实黑暗部分不可误删。点击全图时完整展示 original。Detail 与 HOME 不得分叉算法。

## 6. THUMBNAIL_SUBJECT_CROP — 我的鱼获
沿用 Frozen：82dp × 82dp，1:1，圆角 14dp；来源 `RemoteCatch.imageUrl` 对应真实原始现场照片。
有可靠 bbox 时以主体为锚点，优先保留鱼头尾与少量现场环境；无可靠 metadata 时中心裁切。无法兼顾主体完整性时先放宽 Crop，再 Fit/SoftWater。严禁 B 面资产、鱼鉴图或其他记忆照片自动替代。列表失败只显示中性鱼形占位，不影响 FishRecord。

## 7. 数学约束与渲染稳定性
原图尺寸 `W×H`，容器 `Cw×Ch`（dp 先换到同一像素坐标）：Fit `s=min(Cw/W,Ch/H)`，Fill `s=max(Cw/W,Ch/H)`；无非等比缩放。
Crop 的源矩形应限制在 oriented-source 边界内，并完整覆盖所有安全区；若不存在满足容器长宽比的源矩形，则立即回退 Safe Fit。
裁切只影响 presentation；原图和检测 bbox 不能被这个 sourceRect 覆盖。初次定稿后同一 Result Hero 不发生 Crop Jump。

## 8. 真实素材案例
演示媒体源（公开站点可访问）：`design/system/fish_media_display_v1/assets/real_catch_source.jpg`；原始仓库 fixture 为 `app/src/main/assets/home_normal/fish_record/sample_recent_catch.jpg`，二者为**同一 Git Blob 的字节完全一致副本**，原始 SHA-256:
`6e955087108f7463eca2dbac489699a4942028ebe1b3d456ddfca523286385ef`。
由现有冻结 Hero manifest 登记为真实仓库鱼获测试照片；同一张照片分别展示在不同目标容器中。**演示中的 object-fit:cover 是视觉几何比较，并非已通过检测框完整性验证的实际裁切。** 真实启用 Crop 前仍须 FishSafeRect 验收。不要把“演示容器为 9:16”说成“原图本身为 9:16”。
案例数据：`real_photo_cases.json`。网页只引用原素材路径，不改写源文件。

## 9. 验收矩阵
必须覆盖：4:3、16:9、1:1、3:4、9:16、极端横向≥3:1、极端竖向≤1:3、鱼体贴边、无 bbox、夜景、带真/假黑边、EXIF 90°、低分辨率、加载失败。
每种情况验证四页面的完整性与回退，包括 A 面与 HOME 的同源 Hero 一致性。无真实对应测试原图的场景，只能列为**待实测**，不得拿 CSS 容器比例模拟原图比例后伪称验收通过。
发布状态：设计规则 FROZEN；设备运行证据保持独立状态，不因设计冻结宣称 Android PASS。
