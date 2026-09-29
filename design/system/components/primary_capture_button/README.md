# 渔见主拍摄按钮

Status: **FROZEN**  
Component: **YuJianPrimaryCaptureButton**  
Current version: **V1.1**  
Frozen date: **2026-09-29**

## 1. 最终视觉 Authority

用户明确指定《渔见湖畔鱼获记录界面.png》中的拍照按钮为正式公共样式。

Canonical asset:

`design/system/components/primary_capture_button/assets/capture_button_main_v1.png`

- 208 × 208
- PNG RGBA
- transparent background
- 58,154 bytes
- SHA-256: `501abcc58a65263dd879ad198a0a033630ab4bdcaf945eb02599e4f1249ca327`
- Source canvas: 941 × 1672
- Source crop: `x=382, y=1346, w=174, h=174`
- Processing: only remove the lake background outside the circular button and normalize to 208 × 208
- **No button redesign**

## 2. V1.1 与旧版关系

V1.0 旧资产：

`capture_button_base.png`

SHA-256:

`2d15c1bfa187aecb0d7c572235f9e28476a39f341c9df468012066efc9bec69d`

已经 **SUPERSEDED**，不得再作为设计 Authority。

原因：

- 旧版金边更暗、更偏橄榄金；
- 外圈玻璃 / 灰银承托层不足；
- 与用户确认截图中的按钮不一致。

V1.1 以截图中的视觉为准：

- 白色玻璃主体；
- 外围灰银 / 玻璃承托环；
- 更细、更亮的暖金高光环；
- 深湖蓝灰相机图标；
- 轻微内层高光和深度。

## 3. 页面引用

同一个 Canonical Asset 用于：

- 空首页
- 有数据首页
- 我的鱼获拍摄入口

页面只能改变：

- 页面位置；
- 尺寸 token；
- 是否显示；
- 点击业务行为。

不得复制 PNG 后私自改金边、图标、白色核心或玻璃环。

## 4. Motion

Authority:

`motion_contract.json`

当前时序保持冻结：

- breathing: 5000 ms
- max scale: 1.015
- gold rim sweep first delay: ≈ 3000 ms
- sweep duration: ≈ 1400 ms
- repeat interval: ≈ 9000 ms
- Reduce Motion: 关闭呼吸与扫光，保持 V1.1 静态按钮

Supporting overlays remain motion-only and are not static visual authority.

## 5. Haptic

Authority:

`haptic_contract.json`

- camera tap: light impact
- no page-entry haptic
- no idle haptic
