# 01 — High / 高置信结果

Status: **UI REVIEWED V1 — FROZEN REFERENCE RETAINED**

## Frozen Hi-Fi

![High confidence Frozen](../../../design/05_Result_High_Frozen.png)

Canonical:
`design/pages/recognition/design/05_Result_High_Frozen.png`

SHA-256:
`f540ddc4b3844a453170537eaa70f506dc0eb1c312fc3410a960ad6cd87ba156`

## Page role

High is the fastest Capture → Record bridge.

The model has a usable default species result, but the user always retains correction authority.

## Frozen information hierarchy

1. Result Top Navigation
2. Real catch hero photo
3. Species identity + `修改鱼种`
4. Lightweight catch metadata
5. Catch-memory note
6. Dual bottom actions

## UI review V1

### A. Hero photo — KEEP

Decision:
- keep the real catch photo as the strongest page object;
- no detector box, AI contour, confidence percentage or debug overlay survives into Result;
- no second candidate/photo carousel in High.

### B. Species identity row — FREEZE

Decision:
- species name and `修改鱼种` stay on the same row;
- species name is dominant;
- correction action is visible but subordinate;
- do not add a separate `已识别` badge;
- do not add confidence score as a consumer-facing number.

### C. Main result/content card — REFINE WITHIN EXISTING FROZEN LANGUAGE

Decision:
- preserve the approved High composition;
- keep the information block slightly more open than the legacy Runtime settings-panel treatment;
- the card must read as “this catch” rather than “edit form/settings”;
- future runtime tuning may adjust the content-card height within the previously approved ~8–12% range only when needed for parity/adaptive layout;
- this is a layout calibration allowance, not authorization to redesign the card.

### D. Catch metadata — FREEZE AS LIGHTWEIGHT

Required:
- length
- weight
- location

Rules:
- optional;
- visible as quick record fields;
- do not require a separate generic “编辑记录” control as the primary mental model;
- missing values remain low-salience affordances;
- location remains user-triggered and permission-safe.

### E. Catch note / memory bridge — FREEZE

Role:
- short catch-memory note;
- visual copy role: `留下本次鱼获感言`;
- voice affordance may appear only if it performs a real action;
- note area must remain lighter than Species Identity and CTA.

### F. Bottom CTA — FREEZE

Two actions:

- light / secondary: `继续记录记忆`
- primary: `保存本次鱼获`

Contract:
- both first create/save the FishRecord;
- `继续记录记忆` then enters FishRecordDetail Memory;
- `保存本次鱼获` returns into the normal post-save flow/Home;
- no separate `查看鱼鉴` action is part of High's primary bottom-action model.

### G. Top Navigation — SHARED

Use the shared Result Top Navigation / P0 navigation system.

Do not maintain a page-private oversized back glyph.

### H. Visual tone — FREEZE

- BG_CONTENT family;
- documentary/natural;
- teal normal action accent;
- gold scarce;
- no success celebration;
- no game/achievement semantics;
- AI processing presentation has fully receded.

## High behavior contract

Initial species:
- Top-1 is selected by default.

Species correction:
- `修改鱼种` opens shared species selection;
- correction does not rerun Recognition;
- correction feedback remains attached to the save operation.

Save:
- block duplicate submit;
- preserve all user-entered metadata on retryable save failure;
- layout does not jump during loading.

## Adaptive rules

- short screen: page can scroll;
- hero may reduce without changing hierarchy;
- bottom actions remain reachable;
- keyboard/IME must not cover active metadata/note controls;
- 3+2 state identity must not change due to screen height.

## Explicitly rejected

- `已识别` success badge
- confidence percentage
- `查看鱼鉴` as High bottom primary action
- one giant generic settings card
- mandatory metadata before save
- AI glow/contour continuing into Result
- success confetti / reward animation

## Review status

UI structure: **CLOSED V1**

Copy roles: **CLOSED V1**

CTA semantics: **CLOSED V1**

Motion: inherits `../motion/Recognition_Result_Motion_Spec_V1.md`

Runtime parity: **NOT PART OF THIS DESIGN REVIEW**

## Hero Media Contract — FROZEN

High uses **Subject First** media placement.

Authority:
- `../media/Recognition_Result_Hero_Media_Contract_V1.md`
- `../media/hero_media_contract.json`

Rules:
- display source = original oriented user photo;
- use the recognition fish bbox only as focal guidance;
- FishSafeRect = bbox + 14% horizontal / 18% vertical expansion;
- Smart Crop Fill is allowed only when subject safety passes;
- otherwise use Subject Safe Fit;
- do not crop a source edge further when the fish already touches that edge;
- no blurred duplicate-photo support background.
