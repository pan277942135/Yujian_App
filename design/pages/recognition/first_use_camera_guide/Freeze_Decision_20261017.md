# First-use Camera Guide · Freeze Decision 2026-10-17

**Decision:** Adopt the two-page guide sequence.  
**Status:** FROZEN — proposed for main review in this PR.  
**Active authority on approval:** `First_Use_Camera_Guide_Spec_V1_2.md`.  
**Supersedes:** `Freeze_Decision_20261016.md` for current page count and navigation only.

## Active sequence

| ID | Title | Phone | Fish | Primary action |
|---|---|---|---|---|
| P01 | 竖屏拍摄示意 | Portrait | Horizontal | 下一张 |
| P02 | 横屏拍摄示意 | Landscape | Horizontal | 开始拍摄 |

**Active page count: exactly 2.**

## Asset disposition

- P01 remains byte-identical to the existing frozen source.
- P02 is promoted from the former P03 landscape visual without pixel edits, resizing, recompression, or redrawing. SHA-256 remains `1a3008483ac5708a0649501cf2aef6c799d47b95bcc2c1b1272fa64e68f7f9b3`.
- The former P02 portrait-phone / vertical-fish visual is removed from the active `frozen/` directory and retained under `archive/P02_Portrait_Vertical_Fish_Superseded_20261016.png` for provenance only.

## Interaction

P01 → `下一张` → P02; P02 → `上一张` or `开始拍摄` → Capture Ready. `跳过` exits from either page. Only Skip or the final P02 Start completes the guide. Start never triggers the shutter automatically.

## Acceptance boundary

This decision establishes the active design scope. It does not claim Figma import, Android implementation, image-source readiness, Runtime visual parity, or Release PASS. Those require their own evidence gates.
