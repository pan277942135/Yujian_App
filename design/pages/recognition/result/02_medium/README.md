# 02 — Medium / 中置信结果

Status: **UI REVIEWED V1 — FROZEN REFERENCE RETAINED**

## Frozen Hi-Fi

![Medium confidence Frozen](../../../design/06_Result_Medium_Frozen.png)

Canonical:
`design/pages/recognition/design/06_Result_Medium_Frozen.png`

SHA-256:
`5a334f8deef344910865d43057a2df3d6d5137ca8ebfad3eef7557120586fcd3`

## Page role

Medium is a confirmation state, not an error state.

The system has plausible candidates but needs the user to confirm which species is correct.

## Frozen information hierarchy

1. Result Top Navigation
2. Real catch hero photo
3. One-line confirmation prompt
4. Horizontal candidate species row
5. Other-species escape
6. After confirmation: lightweight catch metadata + memory note
7. Dual bottom actions

## UI review V1

### A. Hero photo — KEEP

- use the same real catch photo hierarchy as High;
- do not replace the hero with candidate artwork;
- no AI processing contour/halo remains.

### B. Prompt — FREEZE

Use one combined line:

`帮我确认一下，这条鱼更像哪一种？`

Do not split into two competing headings such as:
- `帮我确认一下`
- `更像哪一种`

The prompt should be clear but visually lighter than a High species identity.

### C. Candidate row — FREEZE

- candidates are arranged horizontally;
- candidate cards are compact;
- each candidate clearly shows species identity;
- selected state is visible but restrained;
- no game-card styling;
- no rarity/confidence bars;
- no automatic candidate switching.

### D. Initial confirmation state — FREEZE

Medium requires an explicit user confirmation before the species is treated as resolved for save.

The model's Top-1 candidate may appear first in ordering, but must not silently equal user confirmation.

### E. Other-species escape — KEEP

Provide a low-weight action equivalent to:

`都不是？选择其他鱼种 ›`

It opens the shared species selector and returns to this Result flow with the chosen species.

### F. Post-confirmation content — SHARE HIGH CONTRACT

Once species is confirmed:
- species identity becomes resolved;
- lightweight length / weight / location are available;
- catch note uses the same memory role;
- bottom CTA uses the same dual contract:
  - `继续记录记忆`
  - `保存本次鱼获`

Do not create a separate Medium save system.

### G. CTA availability — FREEZE

Before explicit confirmation:
- do not present the save action as if the species is already confirmed.

After confirmation:
- use the normal dual Result CTA.

### H. Visual tone — FREEZE

- Medium is calm uncertainty, not warning/error;
- no yellow warning banner;
- no red error color;
- candidate cards stay subordinate to the real catch photo;
- BG_CONTENT remains consistent with High.

## Adaptive rules

- candidate row remains horizontally understandable on narrow screens;
- no candidate label truncation that makes species indistinguishable;
- if width is constrained, card width may adapt while the horizontal relationship remains;
- metadata/CTA remain reachable after confirmation.

## Explicitly rejected

- pre-confirmed Top-1 that bypasses user choice
- candidate auto-carousel
- confidence percentages
- candidate artwork replacing the hero
- two-line duplicated confirmation headings
- warning/error visual treatment
- separate Medium-only CTA system

## Review status

UI structure: **CLOSED V1**

Candidate behavior: **CLOSED V1**

Copy role: **CLOSED V1**

CTA semantics: **CLOSED V1**

Runtime parity: **NOT PART OF THIS DESIGN REVIEW**
