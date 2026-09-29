# 渔见主拍摄按钮

Status: **FROZEN**
Component: **YuJianPrimaryCaptureButton**
Component version: **V1**
Frozen visual revision: **V2.2**
Frozen date: **2026-09-29**

## 1. 定稿结论

用户于 2026-09-29 再次确认当前 Empty Home 截图中的拍照按钮为正式公共样式。

仓库中现有独立资产：

`assets/capture_button_base.png`

与该批准样式一致，因此**不重新绘制、不重新导出、不重新压缩**，直接提升为跨页面公共静态视觉 Authority。

## 2. 唯一静态按钮母版

Canonical static asset:

`design/system/components/primary_capture_button/assets/capture_button_base.png`

Frozen properties:

- Size: **208 × 208 px**
- Format: **PNG RGBA**
- Alpha: **true**
- SHA-256: `2d15c1bfa187aecb0d7c572235f9e28476a39f341c9df468012066efc9bec69d`
- Source: `Empty_Home_Final_Design_V2`
- Role: **shared static visual master**

视觉组成已经包含在这张静态母版中：

- 白色实体圆形核心；
- 完整的暖金细边；
- 深湖蓝灰相机图标；
- 当前批准的图标比例；
- 轻微、克制的深度与高光。

页面不得重新拼装这些静态视觉元素。

## 3. 动效叠加层

以下两张不是第二套按钮样式：

### Gold Rim Sweep

`assets/capture_button_gold_rim.png`

- 208 × 208 RGBA
- SHA-256: `9cbee62354d015dfe3d003de2bdc43b3336a621635f88d4bde36fe10c7e7a43d`
- Role: **motion overlay only**
- 只用于间歇金边扫光

### Breath Glow

`assets/capture_button_breath_glow.png`

- 208 × 208 RGBA
- SHA-256: `de989d9c4021220198370b535e8e4c8c3fbba2db53547771fc52d6cdbc0ac00e`
- Role: **motion overlay only**
- 只用于轻呼吸 / 承托

静态设计预览和静态 Runtime 必须以 `capture_button_base.png` 单独成立。

## 4. 允许的页面变体

### home_primary_capture

用于：

- 空首页
- 有数据首页

按钮视觉完全相同，页面只能决定：

- 页面位置；
- 是否展示；
- 点击业务行为。

### archive_primary_capture

用于：

- 我的鱼获

仍使用同一静态按钮母版，不允许重新设计按钮。页面只允许调整布局落位。

## 5. Empty Home V2.2 参考落位

Reference canvas: `1080 × 1920`

- camera bbox: `x=430, y=1537, w=220, h=220`
- horizontal alignment: center
- prompt → camera clear gap: `≥25 px`
- camera → album clear gap: `≥23 px`

注意：208 × 208 是资产本身尺寸；220 × 220 是 Empty Home 冻结参考画布中的显示 bbox，两者不是冲突。

## 6. Motion

Authority:

`motion_contract.json`

- breathing: 5000 ms
- max scale: ≤ 1.015
- gold rim sweep first delay: ≈ 3000 ms
- sweep duration: ≈ 1400 ms
- repeat interval: ≈ 9000 ms

Motion 不得改变按钮静态身份。

## 7. Haptic

Authority:

`haptic_contract.json`

- camera tap: light impact
- no page-entry haptic
- no idle haptic

## 8. 禁止项

禁止：

- 页面重新画一颗“类似”的按钮；
- 蓝色实心圆；
- 粗金环；
- 霓虹边；
- 重阴影；
- 缩小或替换相机 glyph；
- 把 sweep / glow 烘焙成另一张长期静态按钮；
- 从页面截图再次裁按钮作为新的正式资产；
- 页面私自修改白色核心、金边或图标颜色。

任何真正的视觉变化必须升级公共组件版本 / revision。
