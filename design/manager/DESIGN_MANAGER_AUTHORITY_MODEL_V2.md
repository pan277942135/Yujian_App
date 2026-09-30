# Design Manager Authority Model V2

Status: **FROZEN**
Date: 2026-09-29

## Goal

Design Manager separates three authority layers:

1. **Page Overview**
2. **Visual Authority**
3. **Behavior Authority**

A selected child authority must not render parent-page preview or page-level governance panels in the same detail view.

## 1. Page Overview

Route example:

`#page/my_catches_v2`

Shows:
- page identity / version;
- current page-family visual authority;
- page-level design sections;
- freeze review;
- shared design references;
- modality completeness;
- version history.

This is the only layer that shows `当前视觉权威` for the page family.

## 2. Visual Authority

Route example:

`#page/my_catches_v2/hifi/search/b1_recent`

Shows only:
- selected child title / status;
- selected frozen visual authority;
- local scenario coverage;
- local authority links.

Must hide:
- parent page visual preview;
- page-level overview metadata;
- full page design-section grid;
- page freeze checklist;
- page shared-ref grid;
- page modality grid;
- page version history.

FROZEN visual children must use a static image authority file, not a runtime-composed HTML mock.

## 3. Behavior Authority

Route example:

`#page/my_catches_v2/hifi/search/b5_interaction`

Shows only:
- selected behavior contract;
- interaction/state rules;
- behavior authority source.

Must not render a fake App screen when the item is not a product page.

## 4. Navigation

Example:

```
我的鱼获
├── 主页面高保真
├── Timeline Scroll V1
├── 筛选 V1
├── Empty States V1
├── Growth Mark V1
├── 搜索交互 V1
│   ├── 高保真页面
│   │   ├── B1 · 最近搜索 / 空输入
│   │   ├── B2 · 搜索有结果
│   │   ├── B3 · 搜索无结果
│   │   └── B4 · Search + Filter
│   └── 交互规范
│       └── B5 · 交互与返回规则
└── 加载 / 异常
```

## 5. Governance

For child Authority entries:

- `authority_type=visual` requires `visual_authority`.
- `authority_type=behavior` must not declare `visual_authority`.
- Authority paths must exist.
- Parent and child authorities must never be presented as co-equal previews in the same detail layer.

## 6. Search V1 migration

B1–B4 are archived as static frozen visual authorities under:

`design/pages/fish_records/list/frozen/search_v1/`

B5 remains:

`design/pages/fish_records/list/My_Catches_Search_Spec_V1.md`

The old dynamic Search canvas remains implementation history only and is not the frozen visual authority.


## 7. My Catches V2.2 clarification

- Base-page Top Navigation authority is `TITLE_ONLY`.
- Search Field / Filter Action are page-owned content utilities below Top Navigation.
- Filter V1 is F1–F5 and has four dimensions: 鱼种 / 时间 / 尺寸 / 特殊记录.
- The legacy five-dimensional / Location / Bottom Sheet filter document is SUPERSEDED.
- Frozen visual children use static raster authorities; Design Manager must not rebuild them from HTML mockups.
