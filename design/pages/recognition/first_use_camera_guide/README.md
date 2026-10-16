# Recognition Capture · 首次拍照教学

**Status: FROZEN**

Design Manager 入口：`识别过程 → 07 · 首次拍照教学`

## Active pages
- **P01 · 竖屏拍摄示意** — 手机竖屏 + 横向鱼
- **P02 · 横屏拍摄示意** — 手机横屏 + 横向鱼

**Active sequence:** P01 → P02

- P01: `下一张`
- P02: `开始拍摄`
- `上一张` only appears on P02
- `跳过` may exit from either page
- `开始拍摄` only dismisses the guide and returns to Capture Ready; it never triggers the shutter automatically.

## Removed from active flow
- Former **P02 · 竖拍鱼类拍摄示意** (portrait phone + vertical fish) is removed from active use.
- Former **P03 · 横屏拍摄示意** is retained only as historical/compatibility material and **must not be rebound as active P02**.
- Historical binaries live under `archive/`.

## Active visual source
- P01: `frozen/P01_First_Use_Camera_Guide_Frozen.png` — byte-verified
- P02: `frozen/P02_Landscape_Horizontal_Fish_Frozen.png` — **source pending**

Do not substitute either historical binary for active P02.

## Runtime rule
Android runtime uses structured Compose UI + independent runtime photo assets. Frozen full-page PNGs are not runtime backgrounds.
