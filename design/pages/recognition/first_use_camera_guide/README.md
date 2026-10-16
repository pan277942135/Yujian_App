# Recognition Capture · 首次拍照教学

**Status: FROZEN — two-page V1**

Design Manager 入口：`识别过程 → 07 · 首次拍照教学`

## Active pages

| ID | Title | Phone | Fish |
|---|---|---|---|
| P01 | 竖屏拍摄示意 | Portrait | Horizontal |
| P02 | 横屏拍摄示意 | Landscape | Horizontal |

**Active sequence:** P01 → P02

- P01: `下一张`
- P02: `开始拍摄`
- `上一张` only appears on P02
- `跳过` may exit from either page
- `开始拍摄` only dismisses the guide and returns to Capture Ready; it never triggers the shutter automatically.

## Frozen assets

- P01: 941×1671, 1,988,159 bytes, SHA-256 `dc731cca5470ef2154bb06172b9836e823ea780b0833f04e34caaf883e465c50`
- P02: 941×1672, 2,001,322 bytes, SHA-256 `1a3008483ac5708a0649501cf2aef6c799d47b95bcc2c1b1272fa64e68f7f9b3`
- P02 source: `湖畔横屏拍摄指南(1).png`
- P02 repository asset: `frozen/P02_Landscape_Horizontal_Fish_Frozen.png`

P01 and P02 are Design Manager visual authorities, not Android runtime backgrounds.

## Historical assets

- Former P02 portrait + vertical fish: `archive/P02_Portrait_Vertical_Fish_Superseded_20261016.png`
- Former P03 landscape + horizontal fish: `archive/P03_Landscape_Horizontal_Fish_Superseded_20261016.png`

Historical assets must not be rebound as active page IDs.
