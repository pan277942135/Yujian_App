# 首次拍照教学 V1.2

**Active authority:** `First_Use_Camera_Guide_Spec_V1_2.md`, `manifest.json`, and `navigation.json`.  
**Status:** FROZEN — two-page sequence.

Design Manager 入口：`识别过程 → 07 · 首次拍照教学`

## Active pages

| ID | Title | Phone | Fish | Primary action |
|---|---|---|---|---|
| P01 | 竖屏拍摄示意 | Portrait | Horizontal | 下一张 |
| P02 | 横屏拍摄示意 | Landscape | Horizontal | 开始拍摄 |

**Active sequence:** P01 → P02. The previous P02 portrait-phone / vertical-fish page is removed from active navigation. The former P03 landscape PNG is promoted byte-identically to active P02.

## Frozen assets

- P01: `frozen/P01_First_Use_Camera_Guide_Frozen.png` — 941×1671, 1,988,159 bytes, SHA-256 `dc731cca5470ef2154bb06172b9836e823ea780b0833f04e34caaf883e465c50`
- P02: `frozen/P02_Landscape_Horizontal_Fish_Frozen.png` — 941×1672, 2,001,322 bytes, SHA-256 `1a3008483ac5708a0649501cf2aef6c799d47b95bcc2c1b1272fa64e68f7f9b3`

The former vertical-fish P02 PNG is retained only under `archive/P02_Portrait_Vertical_Fish_Superseded_20261016.png` for provenance; it is not an active design or runtime resource.

## Runtime boundary

Frozen full-page PNGs are Design Manager visual authorities, not Android runtime backgrounds. Android must render the guide over the live CameraX route. Independent runtime example-photo sources and visual evidence remain separate acceptance gates.

## Design Manager resources

`识别过程 → 07 · 首次拍照教学 → 资源` exposes the two active Frozen PNGs as static design resources. Each card preserves the original image ratio and provides the repository path, source filename, repository filename, dimensions, byte count, SHA-256, and a direct original-file download. `navigation.json` records this as a separate `resource_menus` entry; its `children` list is the active P01/P02 teaching sequence. The former portrait-phone + vertical-fish P02 remains archive-only; the former P03 landscape PNG is byte-identically promoted to active P02. The cards resolve from those registered entries and are verified against `manifest.json`; the PNGs are not duplicated and are not Android runtime resources.
