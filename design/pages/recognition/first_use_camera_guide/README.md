# Recognition Capture · 首次拍照教学

**Status: FROZEN**

Design Manager 入口：

`识别过程 → 07 · 首次拍照教学`

## 子菜单

- P01 · 竖屏拍摄示意
- P02 · 竖拍鱼类拍摄示意
- P03 · 横屏拍摄示意

## Authority

- Spec: `First_Use_Camera_Guide_Spec_V1.md`
- Manifest: `manifest.json`
- Frozen visuals: `frozen/P01_First_Use_Camera_Guide_Frozen.png`, `frozen/P02_First_Use_Camera_Guide_Frozen.png`, `frozen/P03_First_Use_Camera_Guide_Frozen.png`

## Freeze rules

P01/P02/P03 use one shared Capture teaching Overlay. The hero is the correct example; the three lower examples are all errors: 鱼头不在画面 / 鱼体过小 / 遮挡过多. P02 error samples are all vertical fish; P03 error samples are all horizontal fish.

P01 and P02 advance with `下一张`; P03 ends with `开始拍摄`. `跳过` never triggers the shutter.

The PNG binaries must be imported byte-identically from the approved high-fidelity source files. No re-encoding, resizing, or screenshot derivative may replace the frozen authority.
