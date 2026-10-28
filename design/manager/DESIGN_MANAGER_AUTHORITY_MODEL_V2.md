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
├── 5维筛选 V1
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


## 7. Status semantics

Design Manager status is governance state, not implementation optimism.

### FROZEN

Use only when the declared authority exists and its required identity / contract checks are trusted.

### PARTIAL

Use when the scope is active and design work remains incomplete.

### MISSING

Use when a required authority does not yet exist.

### DEFERRED

Use when a known flow or design reference is intentionally outside the current product scope / release.  
DEFERRED is **not** the same as PARTIAL: it does not imply the team should continue closing that flow in the current scope.

Typical example:
- future Forgot Password flow while recovery capability is intentionally unavailable;
- data export or account deletion flows intentionally deferred from the MVP.

### BLOCKED_INTEGRITY

Use when an authority is expected to exist but its identity cannot currently be trusted, for example:
- repository binary SHA does not match the recorded Frozen fingerprint;
- canonical source is missing while a non-byte-identical derivative remains.

Rules:
- do not display the item as FROZEN;
- do not regenerate / re-encode a substitute merely to make fingerprints agree;
- retain valid behavior / machine contracts as independently frozen when appropriate;
- restore FROZEN only after the approved authority identity is recovered or a versioned replacement is explicitly approved.

### Runtime separation

Runtime existence, compile PASS, or production code does not upgrade Design status automatically.
