> **Superseded 2026-10-17:** this file records the original three-page freeze decision for history. The active authority is now `First_Use_Camera_Guide_Spec_V1_2.md` with exactly P01 portrait + horizontal fish and P02 landscape + horizontal fish. The former portrait + vertical-fish P02 is archived and not active.

# First-use Camera Guide · Freeze Decision 2026-10-16

**Design status: FROZEN**  
**Active authority:** `First_Use_Camera_Guide_Spec_V1.md`  
**Task:** `RECOGNITION-FIRST-USE-GUIDE-V1`

Historical decision text from 2026-10-16. At that time the three-page sequence was active; it was superseded by the approved V1.2 two-page decision on 2026-10-17.

## Active sequence

| ID | Title | Phone | Fish | Primary action |
|---|---|---|---|---|
| P01 | 竖屏拍摄示意 | Portrait | Horizontal | 下一张 |
| P02 | 竖拍鱼类拍摄示意 | Portrait | Vertical | 下一张 |
| P03 | 横屏拍摄示意 | Landscape | Horizontal | 开始拍摄 |

**Active page count: exactly 3.**

## Frozen source assets

The V1 artwork remains byte-identical to the imported originals. The active manifest and `asset_import_report.json` are the file-level sources of truth.

| ID | Path | Canvas | Bytes | SHA-256 | Git blob |
|---|---|---:|---:|---|---|
| P01 | `frozen/P01_First_Use_Camera_Guide_Frozen.png` | 941×1671 | 1,988,159 | `dc731cca5470ef2154bb06172b9836e823ea780b0833f04e34caaf883e465c50` | `3f73f744104777beee29bd2370ecac1f40197a1d` |
| P02 | `frozen/P02_First_Use_Camera_Guide_Frozen.png` | 941×1671 | 2,042,852 | `fb092235fd043a42aa8250aba9dfdb91a78e96549dd5a63f8e89cf7433685f8f` | `9c2dd981a04792c6ac51e3925727b4e4e6704034` |
| P03 | `frozen/P03_First_Use_Camera_Guide_Frozen.png` | 941×1672 | 2,001,322 | `1a3008483ac5708a0649501cf2aef6c799d47b95bcc2c1b1272fa64e68f7f9b3` | `2f46b2f2906f258eef8ca67181fc049a55d045af` |

## Interaction freeze

P01 → `下一张` → P02; P02 → `上一张` or `下一张`; P03 → `上一张` or `开始拍摄` → Capture Ready. `跳过` exits from any page. Back, close, and outside dismissal do not mark the guide complete. Only Skip or the final P03 Start completes it. Start never triggers the shutter automatically.

## Acceptance boundary

The V1 page count, semantics, orientation, interactions and original asset bytes are frozen. This records design authority and does not claim Android runtime behavior or visual parity. Runtime validation and independent page-specific photo-source availability remain separate acceptance evidence.
