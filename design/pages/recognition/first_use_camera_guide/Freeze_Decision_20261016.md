# First-use Camera Guide · Freeze Decision 2026-10-16

**Design status: FROZEN**  
**Effective change:** Three-page guide → **two-page guide**

## Active sequence
| ID | Title | Phone | Fish | Primary action |
|---|---|---|---|---|
| P01 | 竖屏拍摄示意 | Portrait | Horizontal | 下一张 |
| P02 | 横屏拍摄示意 | Landscape | Horizontal | 开始拍摄 |

**Active page count: exactly 2.**

## Removed from active use
- Former P02 · 竖拍鱼类拍摄示意 — portrait + vertical fish — removed.
- Former P03 · 横屏拍摄示意 — historical/compatibility only — **not active P02**.

## Interaction freeze
P01 → 下一张 → P02; P02 → 开始拍摄 → Capture Ready. 上一张 only on P02. 跳过 exits. 开始拍摄 never auto-shoots. Only 跳过/final 开始拍摄 completes the guide.

## Asset boundary
P01 remains byte-verified. Active P02 landscape/horizontal-fish visual source is **PENDING**. Former P02 and former P03 binaries are historical/compatibility only; neither may be active.

## Acceptance boundary
The page count, semantics, orientation, interaction and authority are frozen. Pending P02 raster source and Android Runtime visual evidence are not claimed PASS.
