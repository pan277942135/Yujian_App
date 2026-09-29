# YuJian Text Action V1

Status: **ACTIVE_CLOSURE**  
Current version: **V1 Candidate**  
Scope: **Shared Design System**

## Purpose

统一没有实体按钮容器的文字操作。

Text Action 不是普通正文，也不是 Primary / Secondary Action Button。
它用于轻量导航、局部编辑、流程切换和辅助动作。

## Candidate semantic levels

1. STRONG
2. NORMAL
3. MUTED

## Current real usage candidates

- Login: 创建账号 → STRONG
- Register: 去登录 → STRONG
- Login: 忘记密码？ → MUTED **candidate refinement**
- Recognition Result: 修改鱼种 + trailing chevron → NORMAL
- Normal Home / section: 全部 + trailing chevron → NORMAL / ON_MEDIA
- FishRecordDetail Hero: 编辑 + trailing chevron → NORMAL / ON_MEDIA

跳过 / 取消 are not frozen into V1 usage_map yet because no approved cross-page visual authority was found for them.

## Current status

This package is intentionally **not FROZEN yet**.

Reason: the existing Login frozen preview renders 忘记密码？ with the same teal accent family as 创建账号. V1 Candidate proposes lowering it to MUTED. This must be visually approved before page authority is revised.

## Candidate visual references

- visual/candidate/01_Semantic_Roles.svg
- visual/candidate/02_Real_Usage.svg
- visual/candidate/03_STRONG_States.svg
- visual/candidate/04_NORMAL_States.svg
- visual/candidate/05_MUTED_States.svg
- visual/candidate/06_ON_MEDIA_States.svg

After approval these references may be promoted to Frozen Authority without redrawing.
