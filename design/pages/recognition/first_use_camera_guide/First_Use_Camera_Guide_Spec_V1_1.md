> Historical V1.1 proposal snapshot. Its two-page orientation is adopted by active V1.2; use `First_Use_Camera_Guide_Spec_V1_2.md` for current authority.

# Historical proposal · First-use Camera Guide V1.1 (SUPERSEDED)

> Historical V1.1 proposal snapshot. The latest user decision re-adopts its two-page orientation in active V1.2; this file is history only. Current authority: `First_Use_Camera_Guide_Spec_V1_2.md`.

> Historical V1.1 proposal snapshot. The latest user decision re-adopts its two-page orientation in active V1.2; this file is history only. Current authority: `First_Use_Camera_Guide_Spec_V1_2.md`.

**Status:** SUPERSEDED for PR #145 Work Task V1\
**Freeze date:** 2026-10-16\
**Owner:** Recognition Capture / 首次拍照教学\
**Scope:** Capture 页面首次相机进入时的两页教学 Overlay；不替代 Recognition Capture V3.1 页面合同。

## Change from V1

V1 originally defined three teaching pages. V1.1 deliberately reduces the teaching to two orientation-matched pages:

1. P01 · 竖屏拍摄示意 — portrait phone + horizontal fish
2. P02 · 横屏拍摄示意 — landscape phone + horizontal fish

The previous vertical-fish teaching page is removed from the active sequence. The previous P03 landscape page is promoted to P02 without changing its frozen PNG bytes.

## Authority

The two frozen PNGs in `frozen/` are the page-level visual authorities for the first-use guide sequence.

- P01 · 竖屏拍摄示意
- P02 · 横屏拍摄示意

The guide is an additive overlay inside `recognition.capture.v3_1`. It does not redefine Camera Preview, Top Navigation, Gallery, the primary capture button, permission UI, or system Photo Picker.

## Interaction

- The guide is shown only once after camera permission is granted and the camera provider/preview is Ready.
- Overlay blocks capture-button touch-through while visible.
- P01 primary action: `下一张 →`.
- P02 primary action: `开始拍摄 →`.
- `跳过` closes the guide without triggering the shutter.
- `上一张` is available on P02.
- `开始拍摄` closes the guide and returns to normal Capture Ready; it does not automatically trigger the shutter.
- Completion is persisted only after the guide is actually dismissed by `跳过` or the final action.
- Camera permission denial, preview-not-ready, gallery entry, or abandoned/incomplete guide does not mark the guide completed.

## Teaching content

### P01 · 竖屏拍摄示意
- Phone: portrait
- Fish: horizontal
- Correct hero: fish fully visible and well framed
- Errors: fish head outside the frame; fish too small; excessive occlusion
- All three error examples are horizontal fish.

### P02 · 横屏拍摄示意
- Phone: landscape
- Fish: horizontal
- Correct hero: fish fully visible and well framed
- Errors: fish head outside the frame; fish too small; excessive occlusion
- All three error examples are horizontal fish.

## Visual rules

- Full-bleed natural camera photography remains dominant.
- Morning lake blue-gray / mist white / restrained gold.
- Dark legibility overlay is restrained; never create a solid black panel.
- Examples are photographic samples, not card-heavy UI.
- No HUD, scanner, AI radar, neon, metallic treatment, or decorative technology effects.
- The top hero is the correct example; do not duplicate a `正确` card below.
- The three lower examples are all error samples.
- The guide inherits Capture safe-area and shared component contracts.
- The two pages correspond directly to the two Capture Hi-Fi orientation states: portrait-horizontal and landscape-horizontal.

## Runtime boundary

This package freezes the design surface only. Camera runtime, permission, lifecycle, device responsive behavior, accessibility execution, and Android evidence remain separate acceptance work.
