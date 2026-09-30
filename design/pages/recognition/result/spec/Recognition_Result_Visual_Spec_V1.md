# Recognition Result Visual Spec V1

## Visual intent

Recognition Result is the moment where AI recedes and the user's catch becomes the subject again.

The visual tone is:
- natural
- calm
- documentary
- low-noise
- memory-led

It is not:
- scanner UI
- AI dashboard
- achievement screen
- form-heavy admin UI

## Shared base

The real captured photo remains the dominant visual object.

Background:
- uses the quiet Result treatment from Core Visual System V1
- must not become a full-frame Processing glow
- may use low-salience atmospheric treatment only as shown by Frozen references

Processing effects:
- no AI filaments
- no fish contour
- no halo breathing
- no detector rectangle
- no HUD

## Top Navigation

Use the shared P0 Top Navigation.

Title:
- `识别结果` where present in the Frozen state
- back affordance follows shared component geometry

Do not use an oversized glyph-only custom back button when the shared component is available.

## Hero photo

- real runtime media
- large radius from the shared visual system
- fish remains readable
- avoid destructive crop that removes fish head/tail when a fit-preserving treatment is required by the Frozen reference
- no debug overlay

## High

Species line:
- species name is the dominant text identity
- `修改鱼种 ›` sits on the same identity row
- do not add “已识别”

Metadata:
- length / weight / location remain lightweight
- approved icons/affordances are preserved
- the section must not look like a generic settings card

Catch note:
- label: `留下本次鱼获感言`
- voice affordance is lightweight and subordinate

Actions:
- bottom dual-action hierarchy
- light: `继续记录记忆`
- primary: `保存本次鱼获`

## Medium

- hero photo remains unchanged
- candidate region appears below/within the approved result hierarchy
- candidate cards remain compact
- selected candidate is visible but not game-like
- “都不是 / 选择其他鱼种” remains a low-weight escape
- do not replace the main result photo with candidate artwork

## Low

- simplify the information density
- `无法确认是什么鱼` is the main message
- `鱼种待确认` is the record state
- retake is visually downgraded
- preserving/saving the catch remains the primary product direction
- do not expose normal metadata blocks before they are useful

## No Fish / Image Quality

- keep the source photo visible
- use a calm recovery panel
- avoid alarm-red error chrome
- title and explanatory copy are centered/structured according to Frozen
- retake is primary
- gallery is secondary
- back is tertiary

## Color

- lake/ink neutrals dominate
- teal is the normal action accent
- gold remains scarce
- error states do not introduce aggressive red surfaces

## Typography

Use existing YuJian typography roles:
- page title
- species identity / hero title
- body
- caption
- button text

Do not introduce page-local type scales unless required by the Frozen reference.

## Compact height / small screen

On short devices:
- content may scroll
- hero may reduce within the frozen hierarchy
- action area must remain reachable
- no CTA may be covered by system bars or IME
- hierarchy must not collapse into a dense form

## Accessibility

- action touch target >= shared component contract
- species/change action has a meaningful label
- voice action has contentDescription
- candidate cards expose selected/unselected semantics
- image has useful semantics only when it helps navigation/understanding; decorative background is silent
