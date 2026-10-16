# First Use Camera Guide V1.2

Status: **FROZEN · TWO-PAGE AUTHORITY**. This control-plane proposal records the user decision of 2026-10-16 and requires main approval before product-branch synchronization or Figma edits.

## Scope
A two-page first-use teaching overlay inside Recognition Capture. It does not redefine Camera Preview, navigation, Gallery, shutter control, permission UI, or the system Photo Picker.

## Active pages
| Page | Phone | Fish | Frozen source | SHA-256 |
|---|---|---|---|---|
| P01 | Portrait | Horizontal | `frozen/P01_First_Use_Camera_Guide_Frozen.png` | `dc731cca5470ef2154bb06172b9836e823ea780b0833f04e34caaf883e465c50` |
| P02 | Landscape | Horizontal | `frozen/P02_Landscape_Horizontal_Fish_Frozen.png` | `1a3008483ac5708a0649501cf2aef6c799d47b95bcc2c1b1272fa64e68f7f9b3` |

P02 uses the exact original bytes formerly labeled P03. Only its semantic page identity changes; no pixels are edited, resampled, redrawn, or recompressed.

## Superseded asset
The former P02 portrait-phone / vertical-fish page is removed from the active sequence. Its original PNG remains byte-identically under `archive/P02_Portrait_Vertical_Fish_Superseded_20261016.png` for provenance (SHA-256 `fb092235fd043a42aa8250aba9dfdb91a78e96549dd5a63f8e89cf7433685f8f`). It is historical only and must not map to an active guide page.

## Interaction
- Show once after camera permission is granted and the camera preview is Ready.
- Block shutter touch-through while the guide is visible.
- P01 primary action: `下一张`; P02 primary action: `开始拍摄`.
- `跳过` closes without firing the shutter. `上一张` is available on P02.
- `开始拍摄` closes the guide and returns to Capture Ready; it does not fire the shutter.
- Persist completion only after `跳过` or the final action dismisses the guide. Permission denial, preview-not-ready, Gallery entry, or an incomplete guide does not mark it complete.

## Teaching and visual rules
Both pages teach horizontal-fish framing. P01 demonstrates portrait-phone capture; P02 demonstrates landscape-phone capture. Each has one correct hero and three horizontal-fish error examples: fish head outside frame, fish too small, and excessive occlusion. Keep natural camera photography dominant, preserve restrained legibility, and avoid HUD, scanner, radar, neon, metallic effects, or card-heavy UI. The guide inherits Capture safe-area and shared component rules.

## Acceptance boundary
`G-FIRST-USE-GUIDE-ASSET` requires exact P01/P02 SHA, bytes, dimensions, and Design Governance evidence. Repository byte integrity is verified in this proposal. Figma fill, aspect/crop review, runtime, and visual acceptance remain pending and are not claimed as PASS.
