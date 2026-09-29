# YuJian Primary / Secondary Action Button

Status: **FROZEN**  
Current version: **V1.1**  
Scope: **Shared Design System**  
Frozen date: **2026-09-29**

## Purpose

统一渔见普通业务动作按钮，不包含品牌级主拍摄按钮。

覆盖：

- 登录
- 注册并登录
- 保存本次鱼获
- 继续记录记忆
- 手动选择
- 重新拍摄

## Current variants

1. `PRIMARY`
2. `SECONDARY_STRONG`
3. `SECONDARY_MUTED`

## Version history

- **V1** — semantic hierarchy / geometry baseline; historical frozen version.
- **V1.1** — current visual refinement; adds restrained surface depth, finalized state treatment and asymmetric paired-button ratios.

## Current Authority

1. **Frozen Visual Authority Set** — six independently usable UI references under `visual/authority/`:
   - `01_Base_Visual.svg`
   - `02_Single_Buttons.svg`
   - `03_Paired_Buttons.svg`
   - `04_PRIMARY_Five_States.svg`
   - `05_SECONDARY_STRONG_Five_States.svg`
   - `06_SECONDARY_MUTED_States.svg`

   The previous `visual/Action_Button_V1_1_Frozen_Visual.svg` is retained as **overview only**.
2. **Numeric / behavior contracts**:
   - `Action_Button_Spec_V1_1.md`
   - `visual_contract.json`
   - `motion_contract.json`
   - `usage_map.json`
   - `manifest.json`
   - `visual/reference_manifest.json`

Historical authority:

- `Action_Button_Spec_V1.md`

## Core rule

普通业务 Primary 使用 Deep Lake Teal，不使用 Morning Gold。

Gold 继续保留给：

- 品牌；
- 主拍摄按钮；
- 重要时刻；
- 有意义的记录。

页面可以决定按钮文案、布局位置与业务事件，但不得重新定义颜色、圆角、状态、层级或已冻结的双按钮比例。
