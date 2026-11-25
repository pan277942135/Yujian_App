# 渔见 AI Orb × WaterLight 识鱼全流程视觉与结果交接补充冻结合同 V1

**Contract ID:** YUJIAN_RECOGNITION_VISUAL_CONTRACT_FREEZE_ADDENDUM_V1  
**状态：** FROZEN  
**适用范围：** YuJian Android Recognition Runtime  
**适用阶段：** Stage 05–13  
**优先级：** P0

## 权威设计文件

- AI_ORB_WATERLIGHT_13_STAGE_STATE_MACHINE_V3.md
- AI_ORB_WATERLIGHT_UNIFIED_LIFECYCLE_V3.md
- recognition_21_frame_matrix.csv
- recognition_21_frame_matrix.json
- component_runtime_mapping.md

本补充合同是冻结产品决策。它不替换既有 13 个主阶段、21 帧矩阵、统一场景时钟、状态机或既有冻结数值参数。

## 冻结决策 01：Orb 最近轮廓接触点，取消鱼尾锚点

不再识别鱼尾、尾柄或鱼尾尖端。寻鱼光丝的方向由 AI Orb 与真实鱼体轮廓之间的最近距离决定。唯一目标是从 AI Orb 朝鱼体最近的位置释放能量。

### 几何计算

1. 获取当前 Recognition Attempt 对应的可信 SlimSAM Mask。
2. 获取当前 AI Orb 既有能量出射锚点。
3. 将 Orb 锚点和 Mask 外轮廓转换到同一个照片坐标系。
4. 对真实鱼体外轮廓进行有效采样。
5. 计算 Orb 锚点到轮廓各采样点的距离。
6. 选择欧氏距离最小的有效轮廓点作为本次寻鱼路径的接触终点。

数学定义：P* = argmin(P ∈ C_trusted) ||P - O_orb||₂。其中 P* 是最近轮廓接触点，C_trusted 是当前可信鱼体 Mask 的有效外轮廓，O_orb 是当前 Orb 能量出射锚点。

### 实施约束

- 使用现有 PhotoGeometry。
- 使用同一个 PhotoIdentity、Session 和 Attempt。
- 不新增鱼尾识别模型、尾柄收窄算法或固定鱼尾坐标。
- 不使用 Detector BBox 边缘代替真实 Mask。
- 不混用不同鱼体或不同 Attempt 的 Mask。
- 不将内部孔洞或噪声轮廓作为接触点。
- Orb 或照片几何变化时，使用同一变换关系更新位置。
- PATH_TO_FISH、CONTOUR_A 初始接触位置和 ORB_RETURN 原路径回流必须共用同一个最近接触点；不得分别计算互不一致的锚点。

无可信 Mask（FAILED、UNTRUSTED 或 TIMED_OUT）时，不生成虚假接触点、鱼体轮廓或像素级鱼体金光，不退化到固定鱼尾坐标，进入既有 ORB_ONLY 分支。真实 Classifier 名称有效时仍允许 Orb Return 和 Glyph；分类结果不可用时不得伪造鱼种名称。

## 冻结决策 02：Contour A / B / C 三阶段真实轮廓形成

Contour A/B/C 是同一份真实鱼体 Mask 轮廓逐步形成的三个连续视觉阶段，不是三种算法或三条不同轮廓。

### 06｜CONTOUR_A：轮廓初显

- 从最近 Orb 接触位置附近开始呈现真实轮廓。
- 局部轮廓逐步显现，不提前绘制完整鱼体边框。
- 使用真实 Mask Path，线条细腻并贴合真实鱼体。

### 07｜CONTOUR_B：轮廓延展

- 从 A 阶段连续延展，沿真实鱼体边缘逐步绘制。
- 鱼头、鱼身、鱼鳍和尾部保持原始 Mask 几何。
- 使用蓝金交替渐变；水滴大小和间距自然变化；不使用统一粗描边。

### 08｜CONTOUR_C：轮廓闭合

- 真实鱼体轮廓完整闭合，保持真实鱼形和曲率，连续平滑。
- 支持真实 Mask 的有效多轮廓结构。
- 不使用固定 BBox 或虚构鱼体区域。

A/B/C 三个阶段必须共用相同的 Mask Identity、Photo Identity、Geometry Version 和 Recognition Attempt，不得在阶段之间重新生成不同鱼体几何。

### 09｜FISH_GOLD_PEAK

轮廓闭合后进入鱼体金光阶段，继续使用冻结的三层结构：Core 主体金光、Soft 柔性扩散光、Halo 外围光晕。三层光必须贴合真实 Mask；不能将整张照片染金，也不能用一条粗描边替代三层光学材质。

## 冻结决策 03：AI Orb 必须真实、可见地解构

本条进一步明确既有 G05 凝字视觉合同：禁止的是整颗 Orb 突然消失、再从另一处重新生成文字；这不等于禁止 Orb 解构。Orb 内部水核、玻璃结构、水体、水草、水滴和光丝必须通过连续、可见的形态变化，逐渐解构为液态笔势。

### 11.1｜完整水核

- 保持同一颗 Orb，玻璃壳和内部水体清晰可见。
- 内部水纹、折射、水滴和水草保持生命感，为形态变化做好准备。

### 11.2｜横向舒展

- 同一颗 Orb 的水核横向舒展，使用连续、非线性的形态变化。
- 内部水体同步拉伸；水草、水滴、焦散和光丝随水体运动。
- 不允许简单 scaleX，也不允许替换成一张拉宽的 PNG。

### 11.3｜水光解构

- 玻璃壳的闭合形态逐步松解，内部水核逐步转化为液态结构。
- 水体、光丝和水滴沿舒展方向释放；水草和光学元素参与连续转化。
- 球体边界逐渐消失，原有 Orb 能量转化为液态笔势。
- 必须让用户肉眼看到 Orb 从球体转变为流动水光的过程。
- 禁止 Orb 直接消失、突然切换到独立文字图层、仅靠透明度淡出或普通文字淡入冒充解构。

## 冻结决策 04：真实鱼种名称六子步凝字

必须完成以下连续视觉状态：

### 12.1｜笔势聚合

原 Orb 水光逐步聚集，形成初始中文笔势；水光和液态结构仍明显，尚不能完整读出鱼种名称。

### 12.2｜字形显现

水光开始捕获真实中文 Glyph Path，笔画逐渐形成且部分可辨认，仍有笔画未完成，并保持水光向墨韵的材质转化。

### 12.3｜名称定形

真实鱼种名称完整、清晰、稳定显示；保留青蓝主体、深墨骨架和淡金点睛。最终文字必须与本次 Classifier 真实结果完全一致。必须支持动态鱼种名称并使用其真实 Glyph Path，不得固定使用“黑鱼”“鲫鱼”等图片或文字。

连续性顺序必须是：11.1 完整水核 → 11.2 横向舒展 → 11.3 水光解构 → 12.1 笔势聚合 → 12.2 字形显现 → 12.3 名称定形。六步必须由同一颗 Orb 的材质和能量连续转化而来，不得拆成互不相关的动画，也不得直接显示最终名称代替过程。

## 冻结决策 05：凝字稳定后才能进入结果页面

先完成凝字，再稳定名称，最后才开始结果页面变化。

### 13.1｜RESULT_NAME_HOLD

进入条件：12.3 SPECIES_NAME_FINAL 完成；真实鱼种名称有效；Glyph Path 和名称布局几何有效；名称状态稳定。表现为最终名称保持完整、Orb 到名称转换完成且视觉焦点稳定，不提前移动照片。

### NAME_STABLE 门禁

进入 13.1 前必须同时满足：Classifier 真实名称有效；Glyph Path 已生成；名称文字布局完成；当前 Session 有效；当前 Attempt 有效；Photo Identity 一致；名称不再变化；Result 目标几何已准备好。任一条件未满足时，不得启动结果交接。

### 13.2｜PHOTO_REPOSITION

必须在名称稳定之后启动。原照片从识别场景移动到结果卡片；使用同一张照片且 PhotoIdentity 不变，不出现占位块并保持空间连续性。

### 13.3｜RESULT_HANDOFF

识别场景向 Result 页面交接，照片、名称和 Orb 能量余辉保持统一关系，不重新创建无关识别场景。

### 13.4｜RESULT_INFO_ARRIVAL

真实鱼种名称进入结果信息区域，真实识别属性逐步呈现并使用当前 RecognitionResult，不使用默认或测试数据。

### 13.5｜RESULT_SCENE_STABLE

完整结果页面稳定；照片和真实鱼种信息正确、名称稳定、Processing 阶段光效自然收敛且不残留不必要的识别动效。

严格禁止凝字未完成就切换结果页、名称变化时移动照片、Result 页面提前覆盖识别场景或通过提前导航掩盖 Glyph 问题，也禁止使用第二个时钟强制推进交接。

## 完整视觉因果链冻结

### FULL 路径

AI Orb 苏醒 → Orb 能量释放 → 照片外围 WaterLight → Orb 朝鱼体最近轮廓点释放寻鱼光丝 → Contour A → Contour B → Contour C → Fish Gold Peak → 沿原路径反向回流 Orb → Orb 水核舒展 → Orb 水光解构 → 笔势聚合 → 字形显现 → 真实鱼种名称定形 → 名称稳定 → 照片归位 → Result 页面交接 → 完整结果页面稳定。

### ORB_ONLY 路径

AI Orb 苏醒 → 能量释放 → WaterLight → Mask 不可信或失败 → 不伪造鱼体轮廓和金光 → Orb Return → Orb 水核舒展 → Orb 水光解构 → 真实鱼种 Glyph → 名称稳定 → Result 页面交接。

ORB_ONLY 仍必须使用真实 Classifier 结果，不允许伪造鱼种名称。

## 工程继承约束

1. 继续使用既有 13 个主阶段和 21 帧矩阵，不新增第二套状态机。
2. 不新增第二个 SceneClock，继续使用现有 RecognitionSceneAnimationController。
3. Orb 与 WaterLight 共享 SceneClock 和场景身份。
4. FULL 与 ORB_ONLY 共享 Orb、WaterLight、Glyph 和 Result。
5. 所有数据继续绑定 Session / Attempt / Photo Identity，不允许旧 Attempt 污染当前结果。
6. 不为展示动画绕过真实模型门控。
7. 本补充合同不修改 3650ms / 6000ms 时序争议，不新增固定总时长，也不擅自修改已经冻结的数值参数。
8. 所有阶段必须同时满足真实业务事件门控和视觉完成条件。

## 冻结验收条件

1. 寻鱼光丝明确从 Orb 朝最近真实 Mask 轮廓点运动。
2. 真机视频能清晰看到光丝抵达真实接触位置。
3. 不存在鱼尾识别或固定鱼尾锚点依赖。
4. Contour A/B/C 三阶段连续形成同一条真实轮廓。
5. Fish Gold 三层光与真实 Mask 一致。
6. Orb Return 沿原始释放路径回流。
7. Orb 水核舒展过程清晰可见。
8. Orb 水光解构过程清晰可见。
9. 六个凝字子步连续、完整、可辨认。
10. 凝字名称来自真实 Classifier 结果。
11. 名称稳定之前不得启动照片归位。
12. 名称稳定之后依次完成 13.1–13.5。
13. 照片身份在整个交接过程中保持一致。
14. FULL / ORB_ONLY 两条路径都能正确收敛。
15. 不允许用诊断状态或静态图片冒充真实 Runtime 视觉效果。

验收必须提供真实 Android Runtime 视频和关键帧，不能仅以单测、构建或代码存在作为视觉通过证据。

**以上规则均为 FROZEN_PRODUCT_DECISION。**
