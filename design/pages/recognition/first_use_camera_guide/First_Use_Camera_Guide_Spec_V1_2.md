# 渔见 · First-use Camera Guide V1.2

**Status:** FROZEN — TWO-PAGE AUTHORITY  
**Decision date:** 2026-10-17  
**Owner:** Recognition Capture / 首次拍照教学  
**Supersedes:** Three-page guide V1 active sequence  
**Scope:** Capture 页面首次相机进入时的两页教学 Overlay；不替代 Recognition Capture V3.1 页面合同。

## Authority

This V1.2 contract is the active page-count and sequence authority. The guide is an additive overlay inside `recognition.capture.v3_1`; it does not redefine Camera Preview, Top Navigation, Gallery, the primary capture button, permission UI, or system Photo Picker.

The active sequence contains exactly two pages:

| Page | Phone orientation | Fish orientation | Active frozen visual |
|---|---|---|---|
| P01 | Portrait | Horizontal | `frozen/P01_First_Use_Camera_Guide_Frozen.png` |
| P02 | Landscape | Horizontal | `frozen/P02_Landscape_Horizontal_Fish_Frozen.png` |

P02 is the former landscape P03 visual promoted byte-identically. No pixels are edited, resampled, redrawn, or recompressed.

## Superseded page

The former P02 portrait-phone / vertical-fish teaching page is removed from the active sequence and active asset directory. Its original bytes remain in `archive/P02_Portrait_Vertical_Fish_Superseded_20261016.png` for provenance only (SHA-256 `fb092235fd043a42aa8250aba9dfdb91a78e96549dd5a63f8e89cf7433685f8f`). It must not appear in active navigation, runtime page count, tests, or asset bindings.

The former active P03 page is promoted to P02 using its original PNG bytes (941×1672, 2,001,322 bytes, SHA-256 `1a3008483ac5708a0649501cf2aef6c799d47b95bcc2c1b1272fa64e68f7f9b3`).

## Interaction

- Show once after camera permission is granted and the camera provider/preview is Ready.
- Block shutter touch-through while the guide is visible.
- P01 primary action: `下一张`, advancing to P02.
- P02 primary action: `开始拍摄`, dismissing the guide and returning to Capture Ready.
- `上一张` is available on P02 and returns to P01.
- `跳过` dismisses the guide without triggering the shutter.
- Neither `跳过` nor `开始拍摄` automatically invokes capture.
- Completion is persisted only after actual dismissal by Skip or the final P02 action.
- Permission denial, preview-not-ready, gallery entry, or abandoned/incomplete guide does not mark completion.

## Teaching content

### P01 · 竖屏拍摄示意
- Phone: portrait
- Fish: horizontal
- Hero: fish fully visible and well framed
- Errors: fish head outside frame; fish too small; excessive occlusion

### P02 · 横屏拍摄示意
- Phone: landscape
- Fish: horizontal
- Hero: fish fully visible and well framed
- Errors: fish head outside frame; fish too small; excessive occlusion

## Visual rules

- Full-bleed natural camera photography remains dominant.
- Morning lake blue-gray / mist white / restrained gold.
- Dark legibility overlay is restrained; never create a solid black panel.
- Examples are photographic samples, not card-heavy UI.
- No HUD, scanner, AI radar, neon, metallic treatment, or decorative technology effects.
- The top hero is the only correct example; the three lower examples are all error samples.
- Inherit Capture safe-area, shared component, accessibility, and responsive contracts.

## Runtime boundary

This contract freezes design scope and navigation, not runtime acceptance. Android implementation must render the two-page sequence with Compose over the live camera route. Independent still-photo source assets, Android binding/hash checks, real-device visual evidence, and runtime acceptance remain separate gates.
