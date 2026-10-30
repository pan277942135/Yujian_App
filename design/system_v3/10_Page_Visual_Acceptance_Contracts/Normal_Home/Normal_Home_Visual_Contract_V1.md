# Normal Home Visual Acceptance Contract V1

**Contract page ID:** `normal_home_v1`  
**Status:** `READY_FOR_VISUAL_IMPLEMENTATION`  
**Visual gate:** `NORMAL_HOME_VISUAL_CONTRACT_READY = PASS`  
**Runtime acceptance:** `NOT_RUN`

This is the V3.1 visual acceptance layer over the existing frozen Home design and engineering authorities. It adds no page artwork, token, component, or Android behavior except the explicit one-shot card-enter motion recorded in the accompanying motion contract. Frozen PNG bytes remain unchanged.

## Scope and source-ID mapping

The `NH01`–`NH07` labels in this contract are **acceptance-area IDs**. They do not rename the legacy IDs in `design/pages/home/normal_home/README.md` or its registries.

| Contract area | Acceptance coverage | Existing authority identity |
|---|---|---|
| NH01 | Empty Home, zero resolved valid catches | `home.empty.v2_2`; separate Empty Home page and V2.2 Frozen PNG |
| NH02 | Data Home: one record and multiple records | `home_normal_v1`; original Normal Home main image plus NH02 first-catch image |
| NH03 | Hero image, content and real-photo evidence fit | Existing Normal Home NH01/NH02 Hero frame, NH04 content board, frozen Hero EvidenceFit contract |
| NH04 | Statistics, recent-catch header and record content | Existing Normal Home NH01 and NH04 component/content board |
| NH05 | CTA and camera visual/layout/touch bounds | Empty Home V2.2 anchors, Normal Home checksum-verified CTA/camera masks, shared capture-button master |
| NH06 | Background authority and page inheritance | Empty Home V2.2 scene; Normal Home Morning Lake master / `BG_ENV_HERO` |
| NH07 | Avatar identity states and registered assets | Existing NH07 state specification and its 1254/64/128/192 assets |

**Authority conflict resolution:** the repository defines its original `NH01` as the multiple-catch Normal Home image and `NH02` as the first-catch image. Empty Home is a sibling page: only a successfully resolved count of zero routes there; it is never an in-page Normal Home empty variant. This contract keeps both existing names and source identities intact.

## Authority chain

1. Empty Home: `design/pages/home/empty_home/shared/contracts/authority_manifest.json` and `design/pages/home/empty_home/source/frozen/Empty_Home_Frozen_Visual_V2_2.png`.
2. Normal Home: `design/pages/home/normal_home/authority/authority_map.json`, `design/pages/home/normal_home/source/frozen/source_manifest.json`, `design/system/core_visual_v1/reference/normal_home_v1.png`, and `design/pages/home/normal_home/02_first_catch/frozen/NH02_First_Catch_Home_V1_Frozen.png`.
3. State, content, responsive and avatar rules: NH03–NH07 specifications and frozen boards registered by `Normal_Home_Visual_Contract_Registry_V1.json`.
4. Implementation/measurement interpretation: `design/pages/home/normal_home/engineering/README.md` and its active linked contracts.
5. Shared dependencies: `design/system_v3/11_Freeze_Governance/Effective_Authority_Registry_V3_1.json`, `design/system_v3/05_Component_System/Component_Registry_V3_FINAL.json`, final Typography/Color/Geometry token registries, and `design/system_v3/06_Asset_System/Asset_Registry_V3_FINAL.json`.
6. This contract and its seven linked artifacts are registered in `Normal_Home_Visual_Contract_Registry_V1.json`.

A source image may be compared only with its own registered authority. NH03–NH06 combined boards and NH07 avatar art are explanatory/component authorities, not replacement full-page screens. The Empty Home source manifests say FROZEN while the V3 Asset Registry rows remain REVIEW; this contract preserves both classifications and does not promote the registry. Guest runtime art has a verified local path/SHA but no V3 Asset Registry row, which is recorded for follow-up. PNGs contain no embedded ICC profile; source color space remains `UNVERIFIED_NOT_RECORDED`. Record the runtime profile and use the established sRGB comparison path without rewriting source bytes.

## Visual states and data count

- **0 valid records:** after successful resolution, show the separate Empty Home V2.2 screen. The normal data page is not rendered as an empty dashboard.
- **1 valid record:** show the frozen NH02 first-catch composition; one centered full-size Hero, no neighbor peeks, Pager hint or celebration.
- **2+ valid records:** show the canonical Normal Home composition; selected Hero with user-driven adjacent-card Pager behavior.
- **Unresolved initial archive:** retain the NH03 resolving presentation; use dashes/placeholders, not fake zeros or Empty Home.
- **Refresh pending/failure with a prior snapshot:** keep the same page, statistics and selected stable record ID.
- **Record image failure/recovery:** keep the record/card and replace only the media region.

The full state-to-source, component, token, motion and accessibility binding is in `Normal_Home_State_Visual_Matrix_V1.json`.

## Layout and pixel authority

- Normal Home reference canvas: **1080×1920 px**, source SHA-256 `6ab9d3348b4a9a7e77ddca3a06235b4991798a309bd3512cc6fb9ea7aeb1d377`.
- Empty Home V2.2 artwork: **941×1672 px**, source SHA-256 `3071481ed7e58106381cdd5321267792491c21fd1a357e4362db1dad8e08e7ec`.
- NH02 single-catch artwork: **941×1672 px**, source SHA-256 `377e8e24d11d11428345c129129a26ca03477f83672723b47e3ec1ef01482e7b`.
- Reference output profile: 1080×1920 px, density 3.0 / 480 dpi, 360×640 dp, fontScale 1.0, portrait. Capture full uncropped screenshots and actual window insets.
- Compare against each source's own original bytes, exact SHA and source-specific anchors. Never crop a screenshot, stretch artwork, tune thresholds to a screenshot, or use a combined board as a full-screen target.
- Normal Home geometry and glyph tolerance come from `Quantitative_Visual_Acceptance_Contract_V1.md`: `max(2px, 0.002 × safeWidthPx)` and `max(3px, 0.003 × safeWidthPx)` respectively. Hit-target minimums are hard bounds, not tolerance bands.

## Hero evidence fit

Use the frozen shared Hero frame and existing `geometry.media.cover`, `geometry.media.fit`, `geometry.media.evidence_fit`, and `geometry.media.blur_background` semantics. SAFE_CROP is permitted only when an approved, correctly oriented fish region remains fully inside the crop with context. If crop could remove or obscure any fish evidence, use EVIDENCE_FIT: display the entire unchanged source over a quiet blurred crop of that same source. The background is a display derivative only; stored photo bytes and record identity remain unchanged. Never generate, inpaint, recolor, extend, stretch, or cut the fish.

For 4:3 landscape, 16:9 landscape, 3:4 portrait, 9:16 portrait, edge-touching fish, technical black bars, and missing/untrusted low-confidence boxes, use the decision table in `Normal_Home_Hero_EvidenceFit_Contract_V1.md`. Current repository fixture provenance is incomplete; those runtime cases remain `NOT_RUN` until independently sourced originals and evidence exist.

## Responsive and accessibility authority

- Shared geometry references: 360, 393, 412 and 480 dp. Normal Home page-specific scaling, one window origin, one NH05 common vertical offset, and conditional SAFE_OVERFLOW are defined by the active engineering contracts.
- Insets are measured in the same app window and applied once. Background may cover the full window; interactive bounds remain inside measured safe insets.
- Short-window fallback activates only for measured overlap, clipped required text, unreachable content, or an inaccessible action. It does not globally shrink content, hide statistics or remove Hero.
- FontScale test points: 1.0, 1.3 and 1.5. Preserve Android scaling; do not lower the user scale to fit. 1.5 is an extended accessibility profile beyond the existing certified short-window envelope.
- TalkBack, focus order, 48 dp Normal Home target size, content descriptions, contrast measurement and error recovery are in `Normal_Home_Accessibility_Contract_V1.md`.

## Motion

Empty Home uses the existing V2.2 ripple/cloud/sun-particle/capture motion values. Data Home retains existing Hero idle-breath and shared capture-button motion. This contract adds one restrained one-shot opacity reveal for a newly resolved Hero only when no previous valid snapshot exists; no movement, scale, per-field stagger, refresh animation, media-recovery animation, or Pager auto-advance. Reduce Motion leaves a neutral static frame and stops decorative loops. See `Normal_Home_Motion_Contract_V1.md`.

## Gate meaning and implementation readiness

`NORMAL_HOME_VISUAL_CONTRACT_READY = PASS` certifies static source identity, contract structure, shared dependency resolution and acceptance rules only. It does **not** certify Android runtime parity, device accessibility, asset use in the app, or image-fixture provenance.

The current V3.1 `Page_Registry_V3_1.json` explicitly defers Home, and `Android_Implementation_Contract_Registry_V1.json` has no Normal Home entry. Therefore this visual contract does not claim the aggregate `READY_FOR_ANDROID_IMPLEMENTATION` gate. Formal Normal Home V3.1 Page Contract and Android Implementation Contract registration remain required before that aggregate gate can pass. NH07 also remains a registered state specification/asset package, not a newly frozen full-page avatar composition.

Runtime result states are `PASS`, `FAIL`, `BLOCKED_INFRA`, and `REVIEW_REQUIRED`; no runtime result is asserted in this work. Required evidence inputs and gate procedure are in `Normal_Home_Runtime_Gate_V1.md`.
