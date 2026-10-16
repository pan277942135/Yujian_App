# First-use Camera Guide · Freeze Decision 2026-10-16

**Design status:** FROZEN  
**Scope:** P01 / P02 / P03 first-use Camera Guide  
**Manager:** `识别过程 → 07 · 首次拍照教学`

## Frozen source set

| ID | Title | Source file | Dimensions | Bytes | SHA-256 |
|---|---|---|---:|---:|---|
| P01 | 竖屏拍摄示意 | `a_vertical_smartphone_ui_screenshot_photo_guide.png` | 941×1671 | 1,988,159 | `dc731cca5470ef2154bb06172b9836e823ea780b0833f04e34caaf883e465c50` |
| P02 | 竖拍鱼类拍摄示意 | `a_vertical_smartphone_app_ui_screenshot_photograph.png` | 941×1671 | 2,042,852 | `fb092235fd043a42aa8250aba9dfdb91a78e96549dd5a63f8e89cf7433685f8f` |
| P03 | 横屏拍摄示意 | `湖畔横屏拍摄指南.png` | 941×1672 | 2,001,322 | `1a3008483ac5708a0649501cf2aef6c799d47b95bcc2c1b1272fa64e68f7f9b3` |

## Frozen content rules

- Hero is the single correct example.
- The three lower examples are all errors: 鱼头不在画面 / 鱼体过小 / 遮挡过多.
- P01 error examples: horizontal fish.
- P02 error examples: vertical fish.
- P03 error examples: horizontal fish.
- P01 primary action: `下一张`.
- P02 primary action: `下一张`.
- P03 primary action: `开始拍摄`.
- `跳过` never triggers the shutter.
- `开始拍摄` only dismisses the guide and returns to Capture Ready; it does not auto-capture.

## Repository import boundary

The source PNGs above are the approved frozen bytes. They must be copied into:

`design/pages/recognition/first_use_camera_guide/frozen/`

with no resize, recompression, screenshot conversion, or visual substitution.

**Current repository binary import:** PENDING. The connected GitHub write surface available in this session can create/update UTF-8 text and Git blobs from string/base64 payloads, but does not expose a local-file binary upload operation. The design-system registry and exact source hashes are committed now; the PNG byte import must be completed through a binary-capable GitHub upload path before the repository asset paths can be considered byte-present.
