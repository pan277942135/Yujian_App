# Fish Guide V2

Role: **Natural Collection / Personal Field Guide**

Status: **FROZEN**

## Frozen visual reference

- Canonical reference: design/system/core_visual_v1/reference/fish_guide_v2.png
- SHA-256: e1002e0ae2b7e87c070907fb14d25448e122b4479cb23eeccac88dedce230fcf
- System authority: YuJian Core Visual System V1
- Verification: reference_manifest.json and verify_core_ui_v1_references.py

## Formal specifications

- **01 · Fish Guide Home / 鱼鉴首页**  
  design/pages/fish_guide/Fish_Guide_Home_Spec_V1.md

- **02 · Species Detail / 鱼种详情 — Page Contract V1**  
  design/pages/fish_guide/species_detail/Species_Detail_Page_Contract_V1.md

- **02 · Species Detail / 鱼种详情 — Visual Authority V1**  
  design/pages/fish_guide/species_detail/Species_Detail_Visual_Authority_V1.md

- **02 · Species Detail / Frozen PNG**  
  design/pages/fish_guide/species_detail/frozen/Fish_Species_Detail_Baitiao_V1.png

- **03 · Fish Species States / 鱼种状态 — State Spec V1**  
  design/pages/fish_guide/species_states/Fish_Species_States_Spec_V1.md

- **03 · Fish Species States / Visual Authority V1**  
  design/pages/fish_guide/species_states/Fish_Species_States_Visual_Authority_V1.md

> Species Detail page-level layout and interaction are FROZEN. The internal content / visual system of the five black-gold knowledge cards remains PARTIAL and is intentionally reviewed separately.
>
> Species States V1 freezes state semantics and fallback behavior. The 03 state-flow board does not override the frozen base page shells from 01 / 02.

## Concept

A personal natural fish guide built from the user's real catches.

Fish Guide should communicate:

- discovery;
- observation;
- personal archive;
- natural field-guide knowledge.

Avoid:

- unlock-game semantics;
- rarity;
- level;
- achievement HUD;
- collectible-card hierarchy.

## Fish Guide Home V1

Keep:

- page title;
- low-weight `已点亮 N / T 种` progress;
- central Species Carousel;
- adjacent species-card previews;
- saved-record count as archive metadata.

Do **not** include Search in Fish Guide Home V1.

Search belonged to earlier exploration and is now a future scale feature. It must not be restored from legacy drafts.

## Visual semantics

FishGuideCard should present:

- realistic biological subject;
- species identity;
- natural / field-guide tone;
- restrained archive metadata.

Unlit species remain visible, readable, browsable, and tappable. Unlit is a personal encounter state, not a disabled or permission state.


## Top Navigation

- Fish Guide Home uses **Top Navigation V1 / TITLE_ONLY / FROZEN**.
- Title copy: `鱼鉴`.
- Typography: 28sp / Medium / 34sp, DeepLakeBlue.
- No Back / Utility action.
- Search is not part of Fish Guide Home V1 and must not be restored into the top navigation.
- Progress begins below Top Navigation as page content.

## Species Detail Page V1

Frozen page-level structure:

- Back;
- species name + one lightweight descriptor line;
- finite 5-position Knowledge Card Carousel;
- one centered active card with adjacent-card previews;
- `NN / 05` indicator;
- `我的{鱼种}` with saved FishRecord count and up to two recent real-catch previews.

Frozen interaction:

- Auto Flip: **NO**;
- autoplay / timed switching: **NO**;
- circular loop: **NO**;
- cold entry: **01 / 05**;
- active-card artwork: **Fit / no crop / no stretch**;
- My Species header → My Catches filtered to the current species;
- real-catch preview → selected FishRecordDetail;
- back-stack round trips restore the current species/detail state.

The five black-gold cards' internal copy, imagery, rating semantics and final naming remain outside this page-level freeze.

## Fish Species States V1

State contract axes:

- Encounter: UNLIT / LIT;
- Catch Count: 0 / 1 / 2 / 3+;
- Knowledge Content: Complete / Partial / Unavailable;
- Media: species / knowledge-card / FishRecord preview availability;
- Runtime: Loading / Offline with cache / Offline without cache / Error;
- Catalog Consistency: Unknown / Inactive / Orphan FishRecord.

Key frozen rules:

- UNLIT is an encounter state, not access control;
- LIT is derived from successfully saved FishRecord count;
- 0 / 1 / 2 / 3+ record display is deterministic;
- My Species uses real FishRecord media only and shows at most two recent previews;
- partial knowledge preserves all five positional slots and the NN/05 index;
- missing content/media is never silently replaced with fabricated data;
- 01 / 02 frozen page authorities always override miniature page-shell differences in the 03 flow board.
