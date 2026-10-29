# 渔见 · 拍照识鱼主链路｜Master Board V1

Status: FROZEN — REVIEW BOARD SCHEMA
Freeze date: 2026-10-16
Purpose: 将“空首页 → 权限 → 首次教学 → Camera → Processing → Result → Recovery”全部高保真状态放入一张统一横向审视板。
Important: Master Board 仅用于统一审视、流程沟通和缺口管理；不替代任何单独页面的 Frozen Visual Authority。

## 1. Canvas and reading order
- Final board canvas: 5120×2880, horizontal.
- Reading direction: left → right.
- Primary flow occupies the upper 72%; Recovery / Boundary occupies the lower 28%.
- Every tile carries MB-xx, canonical PAGE_ID / STATE_ID and one status badge.
- Frozen source images are displayed as thumbnails only; the board never edits their source content.
- The board is not a runtime asset.

## 2. Status markers
### FROZEN
Approved visual authority exists for the exact page/state, or the surface is system-owned with frozen behavior. This does not mean Android runtime acceptance passed.
### REVIEW
Design/behavior contract or review composition exists, but the exact standalone visual authority is not frozen.
### MISSING
Required state exists in the route/acceptance model but no adequate high-fidelity visual authority exists.
### SYSTEM
Ownership tag only, not a fourth status. Android Camera Permission Dialog and Photo Picker remain OS-owned.

## 3. Primary visual sequence
| Board ID | Canonical page/state | Visual content | Status |
|---|---|---|---|
| MB-01 | HOME / EMPTY | Empty Home V2.2 | FROZEN |
| MB-02 | IDENTIFY / CAMERA_PERMISSION | Android Camera permission dialog | FROZEN · SYSTEM |
| MB-03 | GUIDE / P01 | 竖屏手机 + 横向鱼 | FROZEN |
| MB-04 | GUIDE / P02 | 竖屏手机 + 竖直鱼 | FROZEN |
| MB-05 | GUIDE / P03 | 横屏教学页 + 横向鱼 | FROZEN |
| MB-06 | IDENTIFY / CAPTURE_READY_PORTRAIT_HORIZONTAL | Camera Ready 竖屏 + 横鱼 | FROZEN |
| MB-07 | IDENTIFY / CAPTURE_READY_LANDSCAPE_HORIZONTAL | Camera Ready 横屏 + 横鱼 | FROZEN |
| MB-09 | RECOGNIZING / PROCESSING_IMAGE_RECOGNIZING | 图片识别中唯一 Frozen | FROZEN |
| MB-10 | RECOGNIZING / PROCESSING_FISH_LOCATED | 已定位到鱼体 | FROZEN |
| MB-11 | RECOGNIZING / PROCESSING_SPECIES_RECOGNIZING | 鱼种识别中 | FROZEN |
| MB-12 | RESULT / RESULT_HIGH | 高置信度 | FROZEN |
| MB-13 | RESULT / RESULT_MEDIUM | 中置信度 | FROZEN |
| MB-14 | RESULT / RESULT_LOW | 低置信度 | FROZEN |
| MB-15 | RESULT / NO_FISH | 未检测到鱼 | FROZEN |
| MB-16 | RESULT / IMAGE_QUALITY | 图片质量不足 | FROZEN |

Processing rule: three product states and three Frozen references. MB-08 Early is retired; MB-09 is the single 图片识别中 reference. The existing Figma Master Board must be synced separately before Figma visual acceptance.

## 4. Recovery / boundary lane
| Board ID | Canonical page/state | Status | Rule |
|---|---|---|---|
| MB-R01 | IDENTIFY / CAPTURE_PERMISSION_DENIED | FROZEN | One Capture state; shutter disabled; Back/Gallery remain; no custom Android permission dialog |
| MB-R02 | SYSTEM / PHOTO_PICKER | FROZEN · SYSTEM | OS-owned; no custom visual authority |
| MB-R03 | SYSTEM / PHOTO_PICKER_CANCEL | FROZEN · SYSTEM | Behavior-only return; no separate product page |
| MB-R04 | IDENTIFY / CAPTURE_ERROR | REVIEW | Recoverable camera/capture/normalization failure; no independent Frozen Hi-Fi source |
| MB-R05 | RECOGNITION / TECHNICAL_FAILURE | REVIEW | Outside 3+2; retry/recovery; never blame image; no independent Frozen Hi-Fi source |
| MB-R06 | RESULT / SAVE_IN_PROGRESS | REVIEW | Shared loading state exists; no page-level Frozen board |
| MB-R07 | RESULT / SAVE_ERROR | MISSING | Required recovery behavior exists; no adequate standalone Hi-Fi authority |
| MB-R08 | RESULT / DIRTY_DISCARD_CONFIRMATION | REVIEW | Review composition exists; not an independent Frozen page authority |

## 5. Semantic boundaries
- LOW = AI completed recognition but species is not reliable enough to claim.
- NO_FISH = recognition completed without eligible fish evidence.
- IMAGE_QUALITY = known quality gate blocks safe classification.
- TECHNICAL_FAILURE = model/runtime/crop/dependency failure; it is outside the five Result states and is not a sixth Result state.
- CAMERA_PERMISSION_DENIED = Capture state, not a global page.
- Camera Permission Dialog and Photo Picker are OS-owned and are never imitated.

## 6. Layout
Header: 渔见 · 拍照识鱼主链路 / MASTER BOARD V1 · HIGH-FIDELITY REVIEW.
Main lane: HOME → PERMISSION → GUIDE → CAMERA → PROCESSING → RESULT.
Result cluster: High / Medium / Low / No Fish / Image Quality.
Recovery lane: Permission Denied / Photo Picker / Capture Error / Technical Failure / Save Loading / Save Error / Dirty Discard.
Normal transitions use solid thin arrows. Recovery/exception transitions use restrained dashed arrows.

## 7. Source precedence
1. Page/state Frozen visual authority.
2. Page visual contract.
3. Shared Foundation / Component contract.
4. Runtime behavior contract.
5. Master Board composition.
The Master Board never overrides items 1–4.

## 8. Review objective
The board must let a reviewer answer at one glance: what is Frozen, what is Review, what is Missing, how normal and recovery routes connect, and whether Camera orientation / Processing product states / Result 3+2 semantics are separated correctly.

## 9. Inventory
Frozen: Empty Home; Camera Permission system surface; Camera Permission Denied; Guide P01/P02/P03; Camera Ready portrait/landscape; Processing four Frozen keyframes mapped to three product states; Result High/Medium/Low/No Fish/Image Quality.
Review: Capture Error; Technical Failure; Save Loading; Dirty Draft Confirmation.
Missing: Save Error.

Master Board is a review artifact only and must not be used as the visual authority of any individual page/state.

## Figma build record
- File key: `VjN7iFviVN9aKeLv8kJivM`
- Page: `MASTER BOARD V1`
- Board node: `6:2`
- Canvas: 5120×2880
- Structure: COMPLETE
- Frozen PNG binary import: PENDING CONNECTOR UPLOAD STEP
- No generated substitute artwork was used.
