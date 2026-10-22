# Web PR #177 → Web Hi-Fi V2 Gap Matrix

观察源：`pan277942135/Yujian` PR #177 HEAD `7da5c1100d981cfe88317b39b5b95713bdba91d7` 的 `app/templates/platform/catch_recognition.html`、`app/static/js/catch_recognition.js` 和 `app/templates/platform/sidebar.html`。这是代码审查，非运行像素差异。目标合同均指 `YUJIAN_AI_CATCH_RECOGNITION_WEB_HIFI_V1` 及相应机器文件。未经批准原图比对的量化差值保持 `NOT_MEASURED`。

| ID | 当前实现位置/观察 | 合同/优先级 | Work B 修复边界与简单验收 |
|---|---|---|---|
| G01 | `catch_recognition.html` extends `platform/base.html`，`sidebar.html` 保留模型工厂侧栏 | Page_Structure / P0 | 产品路由让侧栏收起或让宽，认证及平台返回不丢；1280 宽测三阶段可用内容宽 |
| G02 | `.catch-grid` 三列等宽、gap 13px | Component_Geometry / P0 | 按权重和原稿复核卡片，阶段对齐；桌面截图对照批准源，禁止称已像素 PASS |
| G03 | `.catch-page` 白色卡片/渐变环境有限 | Visual_Tokens / P1 | 接入共享背景与淡蓝金氛围但照片保持真实；检查主文案对比度 |
| G04 | kicker 10px、标题 17px、说明 11px | Visual_Tokens / P0 | 应用角色字号及换行，主界面正文不低于 14px；320/390/1440 检查 |
| G05 | `.catch-drop` min-height 186px，处理区 aspect 1.42 | Component_Geometry / P0 | 提升照片主体面积并保持 contain、EXIF 正向；横竖长图全显 |
| G06 | 处理卡首屏显示 Detector/质量门/Crop/Classifier 列表 | Page_Structure / P0 | 普通路径以真实照片和状态为主；模型阶段迁入默认折叠诊断；不伪造阶段进度 |
| G07 | `catchDiagnostic` pre 与 SAM 技术字样露出处理区 | Page_Structure / P0 | JSON/BBox/模型版本/SAM 状态仅内部诊断，正常用户能从上传直接识别 |
| G08 | `.catch-water` 为几层 radial-gradient、模糊椭圆 | Authority_References / P0 | 分离七个光学层并按照片下缘向上、面状不规则焦散实现；P2/P3/P6 静态帧审查 |
| G09 | `.catch-region-glow` 为 BBox 柔光；无可信 Mask 三层鱼体描光 | Authority_References / P0 | 保留 BBox 降级，不画假轮廓；真实可信 Mask 后三层共享同一几何；P4–P7 复核 |
| G10 | Result `.catch-result-media` 宽由三列卡约束，表单 2 列；CTA 次序与冻结文案待核 | Component_Geometry + Page_State_Matrix / P0 | HIGH 对照 322×210/360 参考、中文名、四字段、故事和双 CTA；文案权威冲突先解决 |
| G11 | JS 有 R0/R0.5/R1 常量和 fixed overlay，但缺同源像素级迁移证据 | Authority_References / P0 | 同一图像 ID 和一条几何链，抓三帧检查鱼体与金光不漂移/不二裁 |
| G12 | JS RR09 常量，但现有页面没有可信 Mask→文字字形转译的正式证据 | Authority_References / P0 | HIGH only，落位后转移，中文 glyph mask；无 Mask 使用降级，不凭空精准路径 |
| G13 | 顶部 `.catch-stages` 仅三个阶段；底部五节点不存在 | Page_Structure / P1 | 添加 5 个视觉节点及 pending/active/done/error，不代表真实模型事件 |
| G14 | 1320/980/520 断点保证基本折列，未覆盖指定九视口视觉证据 | Responsive_Rules / P0 | 完成九视口截图/布局检查、滚动与键盘、320×480 无 body 横溢 |
| G15 | `.catch-empty-icon` 与侧栏鱼图标为 emoji | Visual_Tokens / P1 | 映射共享图标注册；可访问 label 保留，不以 emoji 作为正式图标 |
| G16 | JS 结果分支及 `result.revealing` 需要查 HIGH-only 限制 | Page_State_Matrix / P0 | MEDIUM/LOW/恢复/技术失败均无成功庆祝；真实 HIGH 新会话一次；自动测试 |
| G17 | 后端默认低置信 0.55 与 App 观察 0.45 未统一 | Page_State_Matrix / P0 decision | 不改阈值；标明来源和语义，提交业务决定后同步唯一判定源 |
| G18 | 无已授权示例图，代码文本说明 | Asset_Manifest / P1 | 当前无需示例即可上传；未来仅接入有许可的一张真实示例，禁提交用户私图 |
