# 渔见 AI Capture → Result｜规范验收矩阵 V1

**Contract:** YUJIAN_ANDROID_CAPTURE_TO_RESULT_VISUAL_FLOW_V1  
**Status:** SPEC_FROZEN；实际 Android 运行、视频与像素验收均未由本次 GitHub 规范提交证明。  
**Owner:** 产品负责人负责真机最终视觉验收；Work 负责真实工程修复、基础测试、证据与 APK。

## A. 全链路 Gate

| Gate | 核心验收 | 最小证据 | 不得冒充 |
|---|---|---|---|
| G1 首页→相机 | 按钮反馈、轻量过渡、真实 CameraX 并行初始化、权限拒绝/重试、返回 | 当前 SHA 完整入口录像+日志 | 假预览图 |
| G2 拍摄/相册 | 快门后真实定格/原图接管；系统 Picker 选图/取消；两条路同一 PhotoSession | 入口种类、照片 SHA、状态/图片日志 | 预制鱼照片 |
| G3 照片承接 | 相机初态=真实取景矩形；相册初态宽≤88%/高≤68%、中心 Y≈44%；原图无闪黑和第二张图 | 连续录屏/首帧时间/实际几何矩阵 | 一帧截图推断连续性 |
| G4 照片目标框 | 横竖方超宽都 Fit/Contain，正确 EXIF，无背后模糊扩展，照片边缘与 Shader 几何重合 | 实测尺寸、照片 Rect、各案例图/视频 | 单一 390×844 静态图代表所有机型 |
| G5 自适应 Processing | 照片稳定后才计时；自然唤醒、焦散、完成高光和收敛；快模型不提前跳，慢模型不循环 | 设备时戳+原样完整视频+阶段 Trace | 原来 2800ms 单元测试当视频 PASS |
| G6 Mask / 无圈 | 仅 GOOD 且真实同源 Mask 绘制；WARNING/INVALID/超时/切图绝不画假轮廓、中央扩圈或 BBox 环 | Mask 质量/ROI/Contour/Session 与实际视频 | 假 Mask、椭圆退化 |
| G7 水光 | 新配色 #0B6E69/#4DD0C7/#DDF7F5 用在正式 Android 周边路径；焦散真实不规则上行 | Shader 编译状态/参数 Trace/同图 Demo 对照 | 只看配置常量 |
| G8 RR09 / Hero | 0–200ms 照片运动中的 R0.5 真实中点 100ms；照片唯一所有权；Result Hero 两模式；中文名揭晓 | 连续视频、源矩阵、Hero Bounds、title/Reveal trace | 单元几何测试代替实际重影验收 |
| G9 异常/生命周期 | 权限、拍摄/读取失败、取消/重试、后台、旧 Session、Reduce Motion、超时 | 失败场景视频或运行记录 | 旧图重现、伪造成功 |
| G10 证据交付 | 对应 Source SHA 的 CI、APK sha256、设备/Android 版本、全程录屏与已知 NOT_RUN | 文件+运行 ID+校验 | 编译成功称为真机验收 |

## B. 视频场景组（至少覆盖）

| Case | 入口与照片 | 主结果/Mask | 必看画面 |
|---|---|---|---|
| V01 | 首页→相机，3:4 竖图 | 真分类成功 + 可信 Mask | 定格/原图接管无闪 → 顶部归位 → 轮廓逐渐绘制 → 高光保持 → 同源 Hero |
| V02 | 首页→相机，4:3 横图 | 真分类成功 | 相机预览裁切连续展开为完整横图；UI 仍竖屏 |
| V03 | 系统相册，9:16 竖长图 | 真分类成功 | 88% 初始承接到顶部目标框，宽窄比例真实且照片不被拉伸 |
| V04 | 系统相册，1:1 方图 | 真分类成功 | 居中点不漂移、面板不变形 |
| V05 | 系统相册，16:9 / 极宽图 | 真分类成功 | 不裁两端、四周光贴真实照片边界 |
| V06 | 手机照片 EXIF 90/180/270 | 真分类成功 | 只校正一次，显示/检测/分割/结果坐标一致 |
| V07 | 相册选图/重试很快切换两张不同图 | 旧 Mask/结果迟到 | 旧光效、照片与结果不会插入新 Session |
| V08 | 完整鱼体 + 水草复杂背景 | Mask WARNING/INVALID | 不画轮廓、不画放大圈，保留四周水光，中文名真实揭晓 |
| V09 | Detector/Classifier 很快 | Mask 可信/不可用 | 不跳过视觉基本起伏 |
| V10 | Detector/Classifier 很慢 | Mask 任意 | 一次高光后稳定等待，真实结果到后才继续 |
| V11 | SlimSAM 较晚 / ERROR | 主模型成功 | 无 Mask 分支不强等分割；RR09 不伪造金光 |
| V12 | 无鱼 / 低质 / 多鱼 / 技术失败 | 真实非 HIGH 状态 | 现有正确 Result/Issue，不能当 HIGH 揭晓 |
| V13 | 相机权限拒绝、相机不可用或拍照失败 | 无真实图像 | 显示可重试/返回状态，不冒充原图或识别 |
| V14 | Photo Picker 取消或 URI 不可读 | 无有效新图片 | 不生成空会话，不闪上一张鱼获 |
| V15 | 后台→前台 / 旋转物理设备 | 当前会话 | 页面竖屏，旧动画不重复，不重复触发推理 |
| V16 | GLES 编译/首纹理失败 | 主模型可运行 | 真原图仍可显示；没有暗色遮罩、假圆环 |
| V17 | RR09 FULL_SOURCE_FIT + safe subject crop | HIGH | 同源照片不重影，Result Blur 只在 Hero 按合同出现，无二次裁剪跳变 |
| V18 | Reduced Motion | 任意结果 | 不强行播放空间缩放、闪烁粒子；真实结果可用 |

## C. 几何检查公式

归一化后的原图 iw×ih，内容区 W×Hc（已排除 Insets），导航 navH、面板 panelH：

- Pw=W−32dp
- Ph=max(0,min(0.52×Hc,Hc−navH−16dp−20dp−panelH−20dp))
- s=min(Pw/iw,Ph/ih)
- imageSize=(iw×s,ih×s)，imageX=(W−iw×s)/2，imageY=navH+16dp

在 320×480、360×640、390×844、430×932 上分别测试真实 safeInsets、面板与点击区域，不能把硬编码的 390×844 全屏数字当内容区尺寸。

**特殊重要：** Processing 照片后面禁止同源模糊扩图；Result Hero 的 Full Source Fit 可按已有 Hero Media 合同使用同源模糊背景。不能混为一个规则。

## D. 视频审美与连续性审查

评审必须逐段记录：第一张真实照片什么时候可见、实际照片边框何处、四周水光有没有空间层次、有没有粗横线/中心扩圈、真实鱼体线是否沿轮廓连续描绘并稳定保持、结果转场是否像一张照片移动到 Hero、中文名是否在正确时刻可读。

每条关键视频使用同一真实照片与相应原始 Demo 做对照；不要求不同渲染引擎像素完全相同，但**不允许两个不同画面的“视觉感觉差不多”被称为客观一致**。

## E. 完成状态词

- SPEC_FROZEN：合同和业务决策冻结，不代表像素或代码。
- IMPLEMENTED：相关代码存在，未说明 Runtime。
- UNIT_PASS / BUILD_PASS：只证明对应测试。
- RUNTIME_PASS：有与实际 APK SHA 对应的设备运行证据。
- VISUAL_APPROVED：用户实测完成签核。
- NOT_RUN / BLOCKED：严格标注，不得自动转为 PASS。

不修改 Model 权重、检测阈值、Hero 其他页面布局或用户历史鱼获。