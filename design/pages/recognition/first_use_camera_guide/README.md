# Recognition Capture · 首次拍照教学

**Status: FROZEN — three-page V1 for RECOGNITION-FIRST-USE-GUIDE-V1**

Design Manager 入口：`识别过程 → 07 · 首次拍照教学`

## Active pages

| ID | Title | Phone | Fish |
|---|---|---|---|
| P01 | 竖屏拍摄示意 | Portrait | Horizontal |
| P02 | 竖拍鱼类拍摄示意 | Portrait | Vertical |
| P03 | 横屏拍摄示意 | Landscape | Horizontal |

**Active sequence:** P01 → P02 → P03

P01 and P02 use `下一张`; P02/P03 expose `上一张`; P03 uses `开始拍摄`. Skip and final Start persist completion. Start returns to Capture Ready without taking a photo.

## Frozen assets

P01/P02/P03 are Design Manager visual authorities, not Android runtime backgrounds. Their original PNG bytes are imported and verified in `asset_import_report.json`:

- P01: 941×1671, 1,988,159 bytes, SHA-256 `dc731cca5470ef2154bb06172b9836e823ea780b0833f04e34caaf883e465c50`
- P02: 941×1671, 2,042,852 bytes, SHA-256 `fb092235fd043a42aa8250aba9dfdb91a78e96549dd5a63f8e89cf7433685f8f`
- P03: 941×1672, 2,001,322 bytes, SHA-256 `1a3008483ac5708a0649501cf2aef6c799d47b95bcc2c1b1272fa64e68f7f9b3`

The task-scope resolution is recorded in `Scope_Resolution_20261016.md`. The later two-page freeze proposal remains preserved as history and does not replace this explicitly requested V1 task scope.

## Design Manager resources

`识别过程 → 07 · 首次拍照教学 → 资源` exposes the three existing Frozen PNGs as static design resources. Each card preserves the original image ratio and provides the repository path, source filename, repository filename, dimensions, byte count, SHA-256, and a direct original-file download. `navigation.json` records this as a separate `resource_menus` entry; its `children` list remains the P01/P02/P03 teaching sequence. The cards resolve from those registered entries and are verified against `manifest.json`; the PNGs are not duplicated and are not Android runtime resources.
