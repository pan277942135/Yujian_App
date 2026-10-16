# First-use Camera Guide · Freeze Decision V2 · 2026-10-16

**Design status:** FROZEN  
**Scope:** P01 / P02 first-use Camera Guide  
**Manager:** `识别过程 → 07 · 首次拍照教学`

## Frozen teaching set

| ID | Title | Phone | Fish | Bytes | SHA-256 |
|---|---|---|---|---:|---|
| P01 | 竖屏拍摄示意 | Portrait | Horizontal | 1,988,159 | `dc731cca5470ef2154bb06172b9836e823ea780b0833f04e34caaf883e465c50` |
| P02 | 横屏拍摄示意 | Landscape | Horizontal | 2,001,322 | `1a3008483ac5708a0649501cf2aef6c799d47b95bcc2c1b1272fa64e68f7f9b3` |

P01 remains byte-identical. P02 reuses the former P03 Git blob without changing its bytes.

## Frozen content rules

- Hero is the single correct example.
- The three lower examples are all errors: 鱼头不在画面 / 鱼体过小 / 遮挡过多.
- P01 error examples are horizontal fish.
- P02 error examples are horizontal fish.
- P01 primary action: `下一张`.
- P02 primary action: `开始拍摄`.
- `跳过` never triggers the shutter.
- `开始拍摄` only dismisses the guide and returns to Capture Ready; it does not auto-capture.
- The former vertical-fish teaching page is removed from the active sequence.

## Superseded

This two-page V2 decision is retained as historical provenance only. It does not supersede PR #145 Work Task V1; the active page contract is the three-page P01/P02/P03 definition in `Freeze_Decision_20261016.md`.
