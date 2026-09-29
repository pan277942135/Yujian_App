# Fish Species States Visual Authority V1

Status: **FROZEN — STATE FLOW VISUAL REFERENCE**  
Date: **2026-09-29**  
Scope: **Fish Guide · 03 · 鱼种状态**

## Current source UI

Persistent design asset:

- Source name: `a_clean_high_resolution_ui_ux_design_spec_poster_i.png`
- Canonical design-library name: `Fish_Species_States_V1.png`
- Library path: `/YuJian_App/design/pages/fish_guide/species_states/frozen/Fish_Species_States_V1.png`
- Library file id: `libfile_362ed70bc5848191ab115f370bc764da`
- Dimensions: **1024 × 1536**
- Size: **2,244,852 bytes**
- SHA-256: `181e1c51a830a42680df70c19673a3df9d8261eb35f9654104f412576a11a55d`

This is the current visual reference for the **03 · 鱼种状态** state-flow board.

## What this visual freezes

The board freezes the visual explanation pattern for these state families:

1. **Encounter State**
   - UNLIT / 未点亮
   - LIT / 已点亮
2. **Catch Count State**
   - 0
   - 1
   - 2
   - 3+
3. **Knowledge Content State**
   - complete
   - partial
   - unavailable
4. **Media State**
   - species artwork missing
   - knowledge-card artwork missing
   - FishRecord preview missing
5. **Runtime State**
   - loading
   - offline with cache
   - offline without cache / unavailable
6. **Navigation & State Restore**
   - Fish Guide → Species Detail
   - horizontal knowledge-card browsing
   - My Species / FishRecord round trip
   - Back state restoration

## Authority boundary

The board is **not** allowed to reopen or override the frozen base layouts of:

- `01 · 鱼鉴首页`
- `02 · 鱼种详情`

Miniature page mockups inside the state board are explanatory only. If their header, pagination, background, spacing, card treatment, or page chrome conflicts with 01/02 frozen authority, **01/02 wins**.

The board also does not freeze the internal content or internal visual system of the five black-gold cards.

## Semantic authority

Exact state definitions, derivation rules, fallback rules and cross-state precedence are governed by:

- `design/pages/fish_guide/species_states/Fish_Species_States_Spec_V1.md`

When the visual board and the semantic spec differ in wording, the semantic spec is authoritative for implementation behavior.

## Governance rule

Any future state-flow visual replacement must:

- preserve the state families and semantics in Fish Species States Spec V1;
- use the frozen 01/02 page authorities as its page shells;
- avoid fake catch imagery or fake species data;
- preserve UNLIT as browseable / tappable rather than locked/disabled;
- preserve five positional knowledge slots when content is partial.
