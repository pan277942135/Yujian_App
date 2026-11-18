# YuJian V3 Stage 1｜26帧源资产核验与参数冻结门禁

**STATUS: `VISUAL_ASSET_AUDITED / KF26_CONTACT_SHEET_EXTRACTED / VISUAL_SPEC_NOT_FROZEN`**  
**Stage 0:** PR #339, HEAD `7b050748b55773f68e61291525517e930dd277cf`  
**Target:** `pan277942135/Yujian_App`；**only documents, no Kotlin/Shader modified**.

## 真实资源核验
- 已读取 ChatGPT Library `/冻结高保/13阶段高保真` 与冻结历史Orb资源包。
- V2 26帧总览原图 `8677×5626`，46,486,538 bytes；SHA256 `173b1314bcfaebbf0510b71585a929eb67be962806b225b7660cedcaf115abac`。
- 第二份 V2 总览图同一 SHA，按相同文件去重；旧13阶段拼版 `4200×14480` 为历史对照。
- 现有13阶段高保真文件夹中25张PNG，含单帧和设计板，并非独立的26帧正式母版：其中18张941×1672、4张1672×941、1张2172×724、2张1254×1254。
- 从 V2 拼版连续定位 `KF00–KF25`，提取26张**派生裁切**及裁切坐标，不能把它们宣称为“26张独立原生母版”。
- 基于单一黑鱼照片的SIFT/RANSAC对比，15张帧图定位了近似PhotoRect；11帧Orb圆形边缘获Hough近似测量：半径中位数约105.4拼版裁切像素，中心归一化约(0.4886,0.9048)。该测量仅用于版式审计，不是生产GPU Uniform。
- 旧 `YUJIAN_AI_ORB_WATERLIGHT_13_STAGE_FREEZE_MANIFEST.md` 使用3650ms，已被Main的13阶段**最短局部显示时长合计6000ms**替代，必须禁止回退旧时序。
- **历史五条 R06 `G01/G02/A01/A02/C01` 是 Resident常驻光丝，不是五条 Task；Task数量不预设。**
- 旧Orb Gap Closure中的R06轨迹/材质/周期JSON状态为待视觉评审候选，不能当作已批准的V3视觉参数。

## 当前无法冻结的内容
1. 26份原生独立高清单帧及它们的统一画布/版心；拼版裁切不等价于母版。
2. 有时间戳或被批准的动态依据，以定量确认 Orb 内旋、独立Ribbon、液态流场。
3. Task能量流数量、Bezier分支、笔触宽度、运动相位与能量曲线；绝不能据R06 Resident推导固定5条。
4. 照片折射与光学分层来源，无法从合成PNG反推出Fresnel、Bloom、Shader Uniform。
5. 真实Attempt的Mask/P* Path/Geometry身份与Golden匹配；设计示意PNG不能冒充模型结果。
6. 每帧严格容差与GPU参数绑定，必须依据可复核原始值。

## STOP / PASS规则
本提交仅核验来源与派生量测，**不是视觉冻结Commit**。关键参数仍为UNKNOWN/null，因此不可设置 `VISUAL_SPEC_FROZEN`；禁止开始Stage2/3生产代码。补齐以上来源、参数表、容差、26帧完整性和Schema验证后，再产生独立 `VISUAL_SPEC_FREEZE_COMMIT`。
