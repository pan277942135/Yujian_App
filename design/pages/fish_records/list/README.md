# My Catches V2

Role: **Chronological Archive**

Status: **CORE DESIGN FROZEN / WHOLE EXPERIENCE ACTIVE_CLOSURE**

## Frozen visual reference

- Canonical reference: design/system/core_visual_v1/reference/my_catches_v2.png
- SHA-256: d88e1542103aaa6af98da036b62ed6b18fca8e1b870dcc817fecea27e05072bd
- System authority: YuJian Core Visual System V1
- Verification: reference_manifest.json and verify_core_ui_v1_references.py

## Structure
- search / filter
- month grouping
- date timeline
- location/day summary
- FishRecordRowCard list
- shared Camera Button

## Visual rule
This is a memory timeline, not a sports analytics dashboard.

Meaningful-record annotations such as 最大记录 / 首条草鱼 / 最长记录 remain visually below the fish record itself.

Each record opens FishRecordDetail(recordId).

## 筛选 V1 · F1 · 筛选面板

- Status: **FROZEN**
- Visual Authority: `design/pages/fish_records/list/frozen/filter_v1/F1_Filter_Panel_Frozen_V1.png`
- Source: `晨曦湖畔鱼获筛选界面.png`
- Image: PNG / RGB, **941 × 1672**
- Bytes: **1,531,888**
- SHA-256: `c384ee67ad10997807cd9b3313e0e79ef8de9f39e192c34428022906bc58e246`
- F1 remains the top inline filter panel within 我的鱼获.

## Current filter authority

- Current contract: `My_Catches_Filter_Spec_V2.md`
- Current model: **four dimensions** — 鱼种 / 时间 / 尺寸 / 特殊记录; no location filter.
- F1 remains frozen as a top inline panel with immediate apply. The recovered five-dimension boards are historical references.
- F2 uses the shared `species_picker_v1 / MULTI_SELECT` component and remains partial until its multi-select visual is closed.

## Recovered Growth Mark V1

- Status: **FROZEN**
- Visual Authority: `design/pages/fish_records/list/frozen/growth_mark_v1/My_Catches_Growth_Mark_V1_Frozen.png`
- Source: `与自然相遇：Growth Mark V1 设计稿.png`
- PNG / RGB, **1448 × 1086**, **1,870,439 bytes**
- SHA-256: `25b8eebb71249c4fe24863ba8faca44a1f33f6830832365d08cec2e2de49fbfb`
- Manifest: `design/pages/fish_records/list/frozen/growth_mark_v1/manifest.json`

## Recovered reference boards

Asset roles and exact source metadata are indexed in `My_Catches_Design_Asset_Index_V1.md`. Recovered architecture/background boards, historical filter/search boards, and the earlier timeline board remain references where marked; they do not silently replace current authorities.
