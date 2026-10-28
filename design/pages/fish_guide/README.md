# Fish Guide V2

Role: **Natural Collection / Personal Field Guide**

Design status: **DESIGN FROZEN**  
Runtime / Evidence: **independent closure; does not reopen design**

## Frozen visual references

- Fish Guide Home: `design/system/core_visual_v1/reference/fish_guide_v2.png`
- Home UNLIT state: `design/system/core_visual_v1/reference/supplemental/fish_guide_unlit_state.png`
- Species Detail: `design/pages/fish_guide/species_detail/frozen/Fish_Species_Detail_Baitiao_V1.png`
- Species Detail Zero Catch: `design/pages/fish_guide/species_detail/frozen/Fish_Species_Detail_Zero_Catch_V1.png`
- Species state board: `design/pages/fish_guide/species_states/frozen/Fish_Species_States_V1.png`

## Final Design Manager structure

The Fish Guide menu is intentionally compact.

```
00 · Overview

01 · 鱼鉴首页
└─ 01A · 未点亮状态

02 · 鱼种详情
└─ 02A · 无我的鱼获记录

03 · 页面状态

04 · 知识卡内容与资产

05 · 动效与交互

06 · 响应式与无障碍
```

### Removed standalone menus

The following old placeholders are no longer top-level Fish Guide menus:

- **鱼种导航** → navigation/return/deep-link rules are owned by 01, 02 and 05.
- **内容合同 + 资产** → merged into 04 · 知识卡内容与资产.
- **验收证据** → implementation/runtime closure belongs in Overview/status, not the design information architecture.
- **Archive** → historical material may remain in the repository but is not a product-design workspace.

No frozen image or historical spec is deleted by this navigation cleanup.

## Product concept

Fish Guide is a personal natural collection built from the user's real saved catches.

Keep:

- title and restrained `已点亮 N / T 种`;
- centered Species Carousel with adjacent browse affordance;
- real saved-record count;
- LIT / UNLIT as encounter states, not permission states;
- fixed five-card Species Detail knowledge system;
- real FishRecord linkage in “我的{鱼种}”.

Avoid:

- Search / Filter in V1 Home;
- rarity / legendary / level / stars;
- ranking as Fish Guide IA;
- large lock treatment;
- autoplay / Auto Flip;
- synthetic catch records or fake knowledge;
- duplicate navigation menus that redefine existing page contracts.

## 01 · 鱼鉴首页

Authority:

- `design/pages/fish_guide/Fish_Guide_Home_Spec_V1.md`

Home behavior:

- Carousel is finite and user-driven.
- Search and Filter are out of V1 scope.
- First-visit Discover Hint is allowed once and does not change selection.
- Returning from Species Detail restores the active species.
- UNLIT is nested as **01A**, not a separate card family.

## 02 · 鱼种详情

Authority:

- `design/pages/fish_guide/species_detail/Species_Detail_Page_Contract_V1.md`

Structure:

- Back + species identity;
- five fixed Knowledge Card positions;
- `NN / 05`;
- My Species / `我的{鱼种}`;
- real FishRecord navigation.

Zero saved record is nested as **02A** and changes only the My Species region.

## 03 · 页面状态

Authority:

- `design/pages/fish_guide/species_states/Fish_Species_States_Spec_V1.md`

Owns state deltas for:

- UNLIT / LIT;
- 0 / 1 / 2 / 3+ saved records;
- knowledge complete / partial / unavailable;
- species / card / FishRecord media missing;
- loading / offline / error;
- unknown / inactive / orphan catalog conditions.

01/02 Frozen page authorities always win over state-board thumbnail composition.

## 04 · 知识卡内容与资产

Authority:

- `design/pages/fish_guide/content/Fish_Guide_Knowledge_Card_Content_Asset_Contract_V1.md`

Five fixed positions:

1. `HERO` · 鱼种名片
2. `IDENTIFICATION` · 辨识特征
3. `ECO` · 生态习性
4. `GEAR` · 装备建议
5. `SKILL` · 作钓要点

Structured knowledge data is the factual source of truth.

Legacy `rarity / power / challenge` and ranking/game progression semantics are excluded from Fish Guide V1 presentation.

## 05 · 动效与交互

Authority:

- `design/pages/fish_guide/motion/Fish_Guide_Motion_Interaction_Spec_V1.md`
- `design/pages/fish_guide/motion/motion_contract.json`

Principle:

> Alive, not animated. User-driven by default.

No autoplay, no Auto Flip, no looping glow, no custom unlock sound/haptic.

## 06 · 响应式与无障碍

Authority:

- `design/pages/fish_guide/responsive/Fish_Guide_Responsive_Accessibility_Spec_V1.md`

Owns:

- portrait 9:16–21:9 adaptation;
- large text behavior;
- 44 dp intended touch targets;
- screen-reader semantics;
- insets / cutouts;
- state communication beyond color;
- Reduce Motion cross-page rules.

## Runtime boundary

Current runtime implementation may still contain legacy UI or data fields that conflict with the frozen design contract.

Those are runtime parity issues, not reasons to reopen design.

Design authority order:

1. Frozen page visual
2. Page contract
3. 03 · state delta
4. 04 · content / asset contract
5. 05 · motion / interaction
6. 06 · responsive / accessibility
7. Runtime implementation / evidence
