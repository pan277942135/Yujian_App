# First-use Camera Guide · Active Freeze Decision 2026-10-16

**Status:** FROZEN DESIGN AUTHORITY; canonical change is pending main review and admission.
**Canonical spec:** `First_Use_Camera_Guide_Spec_V1_2.md`
**Task:** `RECOGNITION-FIRST-USE-GUIDE-V1`

## Active two-page decision

| ID | Phone | Fish | Primary action | Original PNG SHA-256 |
|---|---|---|---|---|
| P01 | Portrait | Horizontal | 下一张 | `dc731cca5470ef2154bb06172b9836e823ea780b0833f04e34caaf883e465c50` |
| P02 | Landscape | Horizontal | 开始拍摄 | `1a3008483ac5708a0649501cf2aef6c799d47b95bcc2c1b1272fa64e68f7f9b3` |

The former portrait-phone + vertical-fish page is no longer active. Its original bytes remain in `archive/P02_Portrait_Vertical_Fish_Superseded_20261016.png` with SHA-256 `fb092235fd043a42aa8250aba9dfdb91a78e96549dd5a63f8e89cf7433685f8f`. The former P03 landscape PNG is byte-identical to active P02; it is promoted without image edits.

`跳过` exits without taking a photo. `开始拍摄` closes the guide and returns to Capture Ready; it does not trigger the shutter.

## Preserved review history

The earlier three-page decision was based on the former Work Task text. The latest user decision in the active Work request supersedes that page count and formally selects the two-page sequence above. Prior V1/V1.1/V2 files remain historical records and are not active authorities.

## Acceptance boundary

The PNG source SHA values and Git blob identities are verified in `asset_import_report.json`. Figma node fills, crop review, the complete Master Board visual, PNG/PDF export and product-branch Admission remain pending their required sequence. MB-05 portrait Capture source remains `SOURCE_MISSING`; no replacement is generated.
