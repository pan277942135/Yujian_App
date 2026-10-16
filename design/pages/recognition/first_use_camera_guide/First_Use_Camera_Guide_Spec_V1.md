# 渔见 · First-use Camera Guide V1

**Status:** FROZEN  
**Freeze date:** 2026-10-16  
**Owner:** Recognition Capture / 首次拍照教学  
**Scope:** Capture 页面首次相机进入时的两页教学 Overlay；不替代 Recognition Capture V3.1 页面合同。

## Authority

The two frozen PNGs in `frozen/` are the page-level visual authorities for the first-use guide sequence.

- P01 · 竖屏拍摄示意
- P02 · 横屏拍摄示意

The guide is an additive overlay inside `recognition.capture.v3_1`. It does not redefine Camera Preview, Top Navigation, Gallery, the primary capture button, permission UI, or system Photo Picker.

## Interaction

- The guide is shown only once after camera permission is granted and the camera provider/preview are Ready.
- Overlay blocks capture-button touch-through while visible.
- P01 primary action: `下一张 →`.
- P02 primary action: `开始拍摄 →`.
- `跳过` closes the guide without triggering the shutter.
- `上一张` is available only on P02.
- `开始拍摄` closes the guide and returns to normal Capture Ready; it does not automatically trigger the shutter.
- Completion is persisted only after the guide is actually dismissed by `跳过` or the final action.
- Camera permission denial, preview-not-ready, gallery entry, or abandoned/incomplete guide does not mark the guide completed.

## Teaching content

### P01 · 竖屏拍摄示意
- Phone: portrait
- Fish: horizontal
- Correct hero: fish fully visible and well framed
- Errors: fish head outside the frame; fish too small; excessive occlusion
- All three error examples show a horizontal fish.

### P02 · 横屏拍摄示意
- Phone: landscape
- Fish: horizontal
- Correct hero: fish fully visible and well framed
- Errors: fish head outside the frame; fish too small; excessive occlusion
- All three error examples show a horizontal fish.
- Frozen source: `frozen/P02_Landscape_Horizontal_Fish_Frozen.png`
- Source filename: `湖畔横屏拍摄指南(1).png`
- Canvas: 941×1672
- Bytes: 2,001,322
- SHA-256: `1a3008483ac5708a0649501cf2aef6c799d47b95bcc2c1b1272fa64e68f7f9b3`

## Visual rules

- Full-bleed natural camera photography remains dominant.
- Morning lake blue-gray / mist white / restrained gold.
- Dark legibility overlay is restrained; never create a solid black panel.
- Examples are photographic samples, not card-heavy UI.
- No HUD, scanner, AI radar, neon, metallic treatment, or decorative technology effects.
- The top hero is the correct example; do not duplicate a `正确` card below.
- The three lower examples are all error samples.
- Both active pages use horizontal fish.
- The guide inherits Capture safe-area and shared component contracts.

## Superseded

The former **P02 · 竖拍鱼类拍摄示意** (portrait phone + vertical fish) is removed from the active flow and preserved only under `archive/`.

The former **P03 · 横屏拍摄示意** is removed as a page ID. Its approved PNG bytes are promoted unchanged to the active P02 asset above and the old path is preserved only under `archive/`.

## Runtime boundary

This package freezes the design surface and visual asset identity only. Camera runtime, permission, lifecycle, device responsive behavior, accessibility execution, and Android evidence remain separate acceptance work.
