# 渔见主拍摄按钮 V1

Status: **FROZEN**
Component: **Primary Capture Button**
Frozen date: **2026-09-29**
Source visual revision: **Empty Home V2.2**

## Canonical Asset

正式公共资源：

`assets/capture_button_main_v1.png`

- 208 × 208
- RGBA / transparent background
- 39,099 bytes
- SHA-256: `2d15c1bfa187aecb0d7c572235f9e28476a39f341c9df468012066efc9bec69d`
- 来源：Empty Home V2.2 已批准按钮透明资产
- 处理：**原字节提升为公共资源，没有重绘、重新生成或二次压缩**

这张 PNG 本身就是静态按钮完整视觉，不需要再叠加第二圈金边。

## Frozen Visual

- 白色实体 / 轻玻璃承托感圆盘
- 静态暖金细边已经包含在 Canonical PNG 内
- 深湖蓝灰相机图标
- 轻深度
- 图标必须清晰
- 页面背景不得烘焙进按钮资源

## Supporting Motion Assets

以下是动效辅助资产，不是静态按钮组成层：

- `capture_button_gold_rim.png`
  - 只用于间歇 Gold Rim Sweep
- `capture_button_breath_glow.png`
  - 只用于轻呼吸效果

禁止在 Design Manager 静态预览中把 Gold Rim Sweep mask 永久叠到 Canonical PNG 上。

## Size / Placement

Canonical asset native size: `208 × 208`

Empty Home V2.2 reference canvas `1080 × 1920`:

- rendered bbox: `x=430, y=1537, w=220, h=220`
- horizontally centered
- prompt → camera clear gap: `≥25 px`
- camera → album clear gap: `≥23 px`

页面允许按共享 size token 缩放，但不能重新绘制按钮。

## Motion

Authority: `motion_contract.json`

- breathing: 5000 ms, scale ≤ 1.015
- gold rim sweep: first ≈ 3000 ms, duration ≈ 1400 ms, repeat interval ≈ 9000 ms

## Haptic

Authority: `haptic_contract.json`

Camera tap uses light-impact semantics. No automatic page-entry or idle haptic.

## Usage Rule

当前公共引用：

- Empty Home
- Normal Home
- My Catches / archive capture entry

页面只决定位置、尺寸 token、可见性和业务触发；按钮视觉本身由本组件统一控制。
