# YuJian Primary / Secondary Action Button V1

Status: **FROZEN**  
Scope: **Shared Design System**  
Frozen date: **2026-09-29**

## Purpose

统一渔见普通业务动作按钮，不包含品牌级主拍摄按钮。

本系统覆盖：

- 登录
- 注册并登录
- 保存本次鱼获
- 继续记录记忆
- 手动选择
- 重新拍摄

## Variants

1. `PRIMARY`
2. `SECONDARY_STRONG`
3. `SECONDARY_MUTED`

Primary Capture Button 独立管理，不继承本系统的形态。

## Authority

- `Action_Button_Spec_V1.md`
- `visual_contract.json`
- `motion_contract.json`
- `usage_map.json`
- `manifest.json`

## Core rule

普通业务 Primary 使用 Deep Lake Teal，不使用 Morning Gold。

Gold 继续保留给：

- 品牌；
- 主拍摄按钮；
- 重要时刻；
- 有意义的记录。

页面可以决定按钮文案、布局位置与业务事件，但不得重新定义颜色、圆角、状态与层级。
