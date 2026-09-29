# 渔见主拍摄按钮

Status: **FROZEN**
Component: **Primary Capture Button**
Current visual revision: **V2.2**

这是渔见跨页面复用的主拍摄按钮。页面只能决定位置、是否显示和业务触发，不重新设计按钮本体。

## 当前视觉权威

完整按钮不是单张 `capture_button_base.png`。

正式视觉由三层组成：

1. `capture_button_breath_glow.png` — 呼吸光，位于按钮后方；
2. `capture_button_base.png` — 白色主体与相机图形；
3. `capture_button_gold_rim.png` — 前景暖金细边。

静态设计预览至少必须合成 **base + gold rim**。
动效预览再加入 **breath glow**。

## V2.2 样式

- 白色主体 / 轻玻璃承托感；
- 暖金细边；
- 深湖蓝灰相机图标；
- 轻深度，不做悬浮大阴影；
- 图标必须清楚，不允许缩回旧版紧凑比例；
- 禁止蓝色实心圆按钮、粗金环、霓虹光。

## Empty Home V2.2 尺寸权威

Reference canvas: `1080 × 1920`

- camera bbox: `x=430, y=1537, w=220, h=220`
- horizontally centered
- prompt → camera clear gap: `≥25 px`
- camera → album clear gap: `≥23 px`

## Motion

Authority: `motion_contract.json`

- breathing: 5000 ms, scale ≤ 1.015
- gold rim sweep: first ≈ 3000 ms, duration ≈ 1400 ms, repeat interval ≈ 9000 ms

## Haptic

Authority: `haptic_contract.json`

Camera tap uses light-impact semantics. No automatic page-entry or idle haptic.
