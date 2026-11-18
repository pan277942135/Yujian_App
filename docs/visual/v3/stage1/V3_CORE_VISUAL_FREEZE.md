# YuJian V3｜Stage 1 核心视觉规格冻结（允许后续细化）

**状态：CORE_VISUAL_FREEZE_PASS**
**完整参数状态：FULL_PARAMETER_FREEZE_PENDING**

本次不要求把26帧所有像素、每条曲线、每个 Bloom uniform 都反解出来。冻结的是用户一眼能感知、且会决定实现方向的视觉不变量；二级光学/运动参数允许在真机证据后校准，但不得改变核心视觉语义。

## 1. 核心视觉不变量
1. 26帧统一视觉画布与同一照片身份；KF02–KF25持续使用原黑鱼照片+女性钓友；Result中的草鱼图只作为Result UI布局母版。
2. 13主阶段保持唯一顺序；6000ms是13阶段最短展示预算合计，不是固定墙钟截断。模型未完成时继续等待，最多60s异常超时。
3. Orb是唯一能量主体。Orb Core在识别任务中持续可见、持续运动；Task Energy只能独立增发，不能把Resident Orb/R06变弱、替换或隐藏。
4. R06的G01/G02/A01/A02/C01是5条Resident常驻光丝，不是5条Task Filament；Task数量不预设。
5. Orb→Photo释放、Photo Perimeter、Photo→Fish Seek是同一能量系统的连续过程。
6. Perimeter是轻量水光：主脉+少量纤维/水草+少量水滴/粒子，沿照片真实圆角几何传播；禁止厚重、均匀、固定矩形描边，禁止侵入照片主体。
7. Fish Seek主要可见分流为1–2条细任务光丝；最终触达真实Mask外轮廓最近有效点P*。这不是整个Task系统的固定总数。
8. KF06–KF08的鱼体轮廓来自同一个真实Mask Geometry，A/B/C只改变revealProgress。
9. KF09 Gold Peak是同一真实Mask Path上的Core / Soft / Halo三层金色高光。
10. KF10 Return使用本次Attempt保存的原始Path ID/segments反向回收。
11. KF11–KF12表现为Orb横向舒展→液态解体→笔势聚合→真实中文鱼种Glyph成形；禁止固定中文文字直接淡入。
12. KF13.1–13.5是真实Result Handoff；13.5前不得认为Result Stable。

## 2. 13阶段最短展示合同

| Stage | 视觉阶段 | 最短展示 |
|---|---|---:|
| 01 | PHOTO_STABLE | 0ms |
| 02 | ORB_WAKE | 350ms |
| 03 | ORB_RELEASE | 600ms |
| 04 | PERIMETER_ARRIVAL | 125ms |
| 05 | PATH_TO_FISH | 125ms |
| 06 | CONTOUR_A | 450ms |
| 07 | CONTOUR_B | 450ms |
| 08 | CONTOUR_C | 450ms |
| 09 | FISH_GOLD_PEAK | 1000ms |
| 10 | ORB_RETURN | 350ms |
| 11 | ORB_TO_NAME | 850ms |
| 12 | SPECIES_NAME_REVEAL | 800ms |
| 13 | RESIDUE_SETTLE | 450ms |
| **合计** | | **6000ms** |

真实模型/Mask事件是推进门；模型慢不能跳过阶段，模型快也不能低于视觉最短预算。

## 3. Orb核心
- S1/Idle呼吸：6000ms，±0.8%。
- S2/Wake/Active呼吸：4800ms，±1.8%。
- Wake最短展示：350ms。
- Orb在Wake/Release/Seek/Contour/Gold/Return中不得突然消失。
- Release/Return通过能量方向、局部亮度和路径运动表达，不以Orb消失作为释放动画。
- Task Energy不得削弱Resident的数量、相位和基本可见强度。

## 4. Release / WaterLight
- Stage03最短600ms。
- R06五条仍属于Orb Resident。
- Task Energy独立从Orb分流，数量由实际任务决定。
- Perimeter：主脉+少量纤维/水草+少量水滴，沿真实照片圆角几何传播，不侵入照片主体。
- Fish Seek：1–2条主要可见细任务丝；终点为真实可信Mask外轮廓最近合法接触点P*；禁止鱼尾锚点。
- Mask不可信时不伪造P*，不把BBox当Mask。

## 5. Contour / Gold
- KF06–KF08：一个真实Mask、一个Geometry Version、一条连续外轮廓；A/B/C只改变revealProgress。
- KF09：Core→Soft→Halo三层结构；Gold高光沿真实Mask Path运行。
- 羽化半径、Alpha、Bloom radius、粒子数量允许后续通过设备Golden校准。

## 6. Return / Glyph / Result
- Return：Fish Contact→原Fish Path→原Perimeter/Release Path→Orb；禁止重新生成近似回程线。
- Glyph：Orb横向舒展→液态解体→光笔势聚合→中文鱼种Glyph。
- Result：NAME_STABLE→13.1→13.2→13.3→13.4→13.5；13.5前不允许Result Stable。

## 7. Mask/P*与视觉参考边界

| 内容 | 来源 | 用途 |
|---|---|---|
| 26帧照片/Orb/水光构图 | 视觉参考 | 定义视觉构图 |
| Result草鱼图 | UI Layout Master | 仅定义布局 |
| KF06–KF08鱼轮廓 | 真实SlimSAM Attempt Mask | Runtime真实几何 |
| P* | 真实Mask外轮廓最近合法接触点 | Runtime路径终点 |
| Detector BBox | Detector | 不得冒充真实轮廓 |
| 离线示意Mask | 视觉参考 | 不得冒充Runtime Mask |

## 8. 允许后续细化
- Task Bezier亚像素控制点、速度曲线、phase offset。
- 水滴数量/半径/生命周期。
- Perimeter Core/Soft/Glow精确Alpha。
- 折射强度、Fresnel、IOR近似、采样半径。
- Bloom radius/threshold/strength和Composite比例。
- GPU FBO尺寸、粒子噪声、A/B/C easing。

这些参数可以细化，但不能改变核心视觉不变量。

## 9. 阶段门禁
CORE_VISUAL_FREEZE_PASS = 已经足以指导后续GPU架构与实现方向。
FULL_PARAMETER_FREEZE_PENDING = 不把无法从当前素材可靠反解的二级参数伪装成精确冻结值。
因此Stage 2诊断基础可以启动；Stage 3 GPU实施必须遵守本核心视觉合同，二级参数调整必须记录在校准证据中。