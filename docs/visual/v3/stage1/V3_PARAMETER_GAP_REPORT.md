# YuJian V3 Stage1｜PARAMETER_GAP_REPORT
- `CRITICAL-01`：26份独立高清源母版未完整找到；只有V2高分辨率拼版（不是26张原图）。需原生单帧或明确批准拼版作为唯一原始参考，并裁决画布。
- `CRITICAL-02`：统一画布/PhotoRect/OrbAnchor没有全部可核实；只有15/26张照片SIFT近似匹配与11帧Orb Hough近似。
- `CRITICAL-03`：Orb内部运动、Ribbon独立角速度和相位无法从静态图片唯一反推；旧包只作为候选。
- `CRITICAL-04`：Task能量流数量不预设，缺少可独立测量的任务分支、Bezier和时间帧映射。
- `CRITICAL-05`：折射、Fresnel、Bloom、Alpha无法从已合成的PNG唯一反解；需要分层光学源或已批准参数。
- `CRITICAL-06`：真实Mask、P*与当前PhotoTransform/Attempt身份需要Runtime数据，拼版视觉不是真实模型Golden。
- `HIGH-07 RESOLVED`：旧3650ms历史设计不能覆盖6000ms现行时序合同。
- `HIGH-08 AUDIT`：新版13阶段文件与更早冻结清单有名称/哈希交叉复用，如现文件01 SHA=旧10，现02 SHA=旧01，不能按标题直接对齐。
**状态：BLOCKED_NOT_FROZEN；不修改生产代码。**
