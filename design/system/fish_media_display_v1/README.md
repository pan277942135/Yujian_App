# 鱼获图片自适应显示设计 — Design Manager 入口
Status: **FROZEN / 公共设计系统 V1**（2026-10-10）

- 父级菜单：`#shared/fish_media_display_v1`
- 四个页面的真实照片适配案例由 `real_photo_cases.json` 索引；内容来自仓库已有真实鱼获测试素材，禁止 AI 假鱼。
- 子菜单：Fit/选择识别、Crop/识别结果、Adaptive/详情 A 面、Crop/我的鱼获、异常及验收。
- 既有三份专项媒体 FROZEN Authority 继续有效；若冲突，以状态专属合同为准。
- 只改变**图片如何映射到已冻结容器**，不重开页面外框、字体、CTA、动画或 Android。
- 视觉对照是设计演示，不是设备截图；没有真实 bbox 时无法宣称 Crop 保留鱼体的自动化验收已经通过。

GitHub Pages 仅发布 `design/`。真实鱼获照片通过原始 Git Blob `674bbe8eb1985702aa61c2aeca32579690ab068f` 在 `design/system/fish_media_display_v1/assets/real_catch_source.jpg` 复用，不进行重编码；原始测试素材保留在 `app/src/main/assets/home_normal/fish_record/sample_recent_catch.jpg`。
