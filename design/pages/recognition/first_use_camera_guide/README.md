# Recognition Capture · 首次拍照教学

**Status: FROZEN · V1.1**

Design Manager 入口：

`识别过程 → 07 · 首次拍照教学`

## 子菜单

- P01 · 竖屏拍摄示意
- P02 · 横屏拍摄示意

## Authority

- Spec: `First_Use_Camera_Guide_Spec_V1_1.md`
- Manifest: `manifest.json`
- Freeze decision: `Freeze_Decision_20261016_V2.md`
- Frozen visuals:
  - `frozen/P01_First_Use_Camera_Guide_Frozen.png`
  - `frozen/P02_First_Use_Camera_Guide_Frozen.png`

## Freeze rules

The teaching sequence is deliberately reduced to **two orientation-matched pages**:

1. Portrait phone + horizontal fish.
2. Landscape phone + horizontal fish.

Both pages teach the same core capture rule: keep the complete fish visible and well framed. The three lower examples remain errors: 鱼头不在画面 / 鱼体过小 / 遮挡过多. All error examples are horizontal fish.

P01 uses `下一张`; P02 uses `开始拍摄`. `跳过` never triggers the shutter.

The former vertical-fish teaching page is removed from the active sequence. The former P03 landscape PNG is promoted to P02 with its bytes unchanged.
