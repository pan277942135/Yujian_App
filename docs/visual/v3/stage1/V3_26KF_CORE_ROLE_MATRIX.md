# V3 Stage 1｜26帧核心视觉角色矩阵

26帧保持统一画布与主体身份；不要求每一帧都有独立精确Shader参数。

| KF | 核心视觉角色 | 核心冻结 |
|---|---|---|
| KF00 | 拍照前/入口参考 | 参考，不参与Recognition计时 |
| KF01 | PHOTO_STABLE | 照片稳定、Orb准备 |
| KF02 | ORB_WAKE | Orb苏醒、Core持续可见 |
| KF03 | ORB_RELEASE | Resident持续+Task独立分流 |
| KF04 | PERIMETER_ARRIVAL | 照片边缘圆角水光首次触达 |
| KF05 | PATH_TO_FISH | 1–2条Task Seek向真实P* |
| KF06 | CONTOUR_A | 真实Mask轮廓开始显现 |
| KF07 | CONTOUR_B | 同一Mask Path继续展开 |
| KF08 | CONTOUR_C | 同一Mask Path完成主要轮廓 |
| KF09 | FISH_GOLD_PEAK | Core/Soft/Halo金光 |
| KF10 | ORB_RETURN | 原Path身份回到Orb |
| KF11.1 | WATER_CORE_STRETCH | Orb横向舒展 |
| KF11.2 | HORIZONTAL_STRETCH | 液态结构继续解体 |
| KF11.3 | WATERLIGHT_DECONSTRUCTION | 解体为光笔势 |
| KF12.1 | STROKE_AGGREGATION | 笔势聚合 |
| KF12.2 | GLYPH_EMERGENCE | 中文Glyph逐渐成形 |
| KF12.3 | SPECIES_NAME_FINAL | 真实Classifier名称定形 |
| KF13.1 | RESULT_NAME_HOLD | 名称稳定 |
| KF13.2 | PHOTO_REPOSITION | 照片/结果布局稳定 |
| KF13.3 | RESULT_SCENE_HANDOFF | Processing→Result身份交接 |
| KF13.4 | RESULT_INFO_ARRIVAL | Result信息到位 |
| KF13.5 | RESULT_SCENE_STABLE | 最终稳定门控 |
| KF14–KF25 | 子帧/高保真补充帧 | 核心构图与身份冻结；细节参数可后续校准 |

关键规则：KF06–KF10的鱼体几何不由视觉板直接产生；运行时必须使用真实Attempt Mask。KF02–KF25统一照片身份；Result草鱼只用于UI布局。KF03的5条R06是Resident，不是Task。KF05的1–2条是主要可见寻鱼Task丝，不是整个Task系统总数。KF11–KF12不得通过固定中文字符串淡入模拟Glyph。KF13.3–KF13.5必须有真实事件生产者/消费者和稳定门控。