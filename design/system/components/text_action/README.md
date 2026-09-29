# YuJian Text Action V1

Status: **FROZEN**  
Current version: **V1**  
Scope: **Shared Design System**

## Purpose

统一没有实体按钮容器的文字操作。

Text Action 不是普通正文，也不是 Primary / Secondary Action Button。
它用于轻量导航、局部编辑、流程切换和辅助动作。

## Frozen semantic levels

1. STRONG
2. NORMAL
3. MUTED

## Frozen real usage

- Login: 创建账号 → STRONG
- Register: 去登录 → STRONG
- Login: 忘记密码？ → MUTED
- Recognition Result: 修改鱼种 + trailing chevron → NORMAL
- Normal Home / section: 全部 + trailing chevron → NORMAL / ON_MEDIA
- FishRecordDetail Hero: 编辑 + trailing chevron → NORMAL / ON_MEDIA

跳过 / 取消 are not frozen into V1 usage_map yet because no approved cross-page visual authority was found for them.

## Current status

V1 is **FROZEN**. The approved refinement lowers Login `忘记密码？` to MUTED and page authority has been revised accordingly.

## Frozen visual authority

- visual/authority/01_Semantic_Roles.svg
- visual/authority/02_Real_Usage.svg
- visual/authority/03_STRONG_States.svg
- visual/authority/04_NORMAL_States.svg
- visual/authority/05_MUTED_States.svg
- visual/authority/06_ON_MEDIA_States.svg

These six references are the complete static visual authority for Text Action V1.
