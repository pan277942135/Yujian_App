# Web / SWOS visual asset closure V1

Task: YUJIAN_WEB_SWOS_FROZEN_VISUAL_ASSET_CLOSURE_V1

Baseline main: 3ccabae787c69acdf86be7f19e4c262f903c4671. PR #190 merged as e83124a3a1d5ce189b884e0e1d696597c6f3a237. This independent proposal does not merge main or alter Yujian PR #177.

## Original identity verification

| Reference | Dimensions | Bytes | SHA-256 |
|---|---|---|---|
| 渔见AI鱼获识别流程展示.png | 1536×1024 | 1804497 | 27c34ee5d3cf90921df433cb43c671e42b8ffa80b8f5cebfe9b397da2ee853e2 |
| 渔见AI鱼获识别动画分镜海报.png | 1672×941 | 2340788 | ea0cf2e5fa84aad64195487cbedaab07b1727e96e87c9b4e661dc3d0fa252c1c |

Both read from private handoff ZIP; bytes independently SHA-256 hashed and decoded via Pillow. Both inspected visually in private tool context. No original, ZIP, derivative frame or screenshot is included in this proposal. Publication permission NOT_CONFIRMED.

## Navigation / viewer

Existing routes retained:
- #page/recognition_flow_v1_2/hifi/web_hifi_contract_v2
- #page/recognition_flow_v1_2/hifi/swos_static_visual_v1_1

Both embed private_visual_references.html. User selects original PNG locally; viewer requires exact SHA, byte count and decoded dimensions. Browser-local screenshot selection supports side-by-side inspection and user-declared source SHA / viewport, always NOT_VERIFIED until independently bound to actual product HEAD. Region overlays locate brand, three stages and five-step strip. Manifest binds component IDs to existing Visual Tokens, Geometry and Responsive Rules, preserving measurement provenance.

Historical V0.3: 14_FRAME_V0_3_HISTORICAL_VISUAL_REFERENCE. Current V1.1: 8+7=15 with R0.5 at relative100/global2900ms. 2800+850=3650ms and RR09 V3 unchanged. Historical manual gold outline is not a production Mask. Example text, model values and species do not override current business contracts.

## Validation

Local PASS: Admission; private registration/public original exclusion; Web V2 nine machine contracts; SWOS Phase0 and static V1.1 consistency; Design Manager registry/navigation/hi-fi release integrity; JS syntax; git diff check.

Local browser: BLOCKED before execution. Bundled Playwright available; Chromium download returns invalid/truncated ZIP. No browser PASS claimed. CI regression uses only synthetic PNG bytes; optional local private inputs exercise exact originals without screenshots, video or trace. Real product-head screenshot parity is NOT_VERIFIED and outside the implementation changes of this task.

## Promotion boundary

Seven-condition visual-reference policy and validator added to existing governance and CI. Static JSON/registry PASS does not promote VISUAL_FROZEN. Runtime visual status remains false; public license remains NOT_CONFIRMED. Review any new derived binary separately before public release.

## Current execution authorization and freeze-state addendum — 2026-10-23

- Exact current product source snapshot: PR #177 HEAD `ca21e25116cebef0ed393392eb8997a2b7077689`; product main `6a2064c2a02a76437b6e09a5241bc199cfa05048`. This snapshot is a source binding, not runtime visual acceptance.
- `208758.jpg` was re-materialized from the private Library and independently checked in this execution: SHA-256 `ab7227cf7205ee2ac342b6cd75cd899747879506c95c33b888b6adf0acb47987`, 331421 bytes, JPEG, 1152×1536, EXIF orientation 1.
- The user-confirmed three-stage reference and historical 14-frame storyboard retain their previously recorded private-handoff SHA/dimension verification, but their raw bytes were not re-retrieved in this execution. Their declared hashes remain manifest identities; this report does not claim a new byte re-verification.
- All private source assets and derivatives: `PUBLIC_PERMISSION=NOT_CONFIRMED`; public repository commits, public CI artifacts and Pages uploads are prohibited. Private local browser review only; no persistence or upload.
- `PIXEL_FROZEN_SOURCE=NOT_CONFIRMED`. The 1536×1024 source is a user-confirmed visual-direction reference, not an approved pixel-exact target. Pixel-diff acceptance remains blocked until the user explicitly designates the exact source as the frozen pixel baseline.
- Production fish Mask remains `NOT_QUALIFIED`; manual outlines remain review-only and are not part of asset authorization.

## Product execution evidence addendum — 2026-10-23

- Current product source is PR #177 HEAD `7083ae691d424b4f6d5680fb77f019c9b437505b`, compared with product main `6a2064c2a02a76437b6e09a5241bc199cfa05048` (26 ahead / 0 behind). Exact-source CI [39956274544](https://github.com/pan277942135/Yujian/actions/runs/39956274544) passed.
- The public CI artifact contains actual Chromium frame captures, a deterministic repeated-P4 pixel comparison, shared-photo geometry trace and a complete fixture-only 3650ms WebM. The separate catch UI smoke includes 9 viewport screenshots but is explicitly UI simulation. These outputs contain only public digest-pinned fixtures; no private source image or derivative is included.
- PR #177 now contains a same-request opt-in SAM adapter on `POST /api/product/catch-recognition/infer`. No inference or mask output was collected from a current-source deployed Revision. The candidate no-traffic configuration is still open/unmerged in PR #182; the main preview pin remains stale at `4de911dc497c150545d938b60b2694f3c4723e37`.
- This CI evidence does not freeze the source artwork, verify pixel parity, qualify a production Mask, measure real-device frame performance or constitute user visual acceptance. Production Mask remains `NOT_QUALIFIED`; deployed runtime and visual acceptance remain `NOT_VERIFIED`.
- The existing unrestricted repository test path remains subject to metadata-access review. The successful exact-source CI run uses the approved targeted suite; an earlier PR #182 broad test step that completed before its cancellation request is not used as acceptance evidence.


### PR #182 scoped CI follow-up — 2026-10-23

Product PR #182 run [39956395808](https://github.com/pan277942135/Yujian/actions/runs/39956395808) completed successfully: the validate job (including offline SWOS quality-probe unit tests and metadata-review boundary record) and Chromium job both passed. This was configuration CI only: no cloud candidate deploy, real inference, SAM mask probe, or health-SHA verification occurred.
