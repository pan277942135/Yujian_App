# Normal Home — State and Interaction Contract V1 (P1)
Status: ENGINEERING_ACTIVE / DESIGN_UNCHANGED
Owners: NH01–NH04 visual states, Behavior Spec V1, shared avatar and HOME Hero contracts.

## Single source of truth for Home selection

Use valid resolved FishRecords, not login state, server statistics, thumbnail availability or transient errors. A valid record has nonblank ID and either nonblank speciesId OR speciesName. Resolve timestamp priority capturedAt -> createdAt -> unknown; newest first, unknown last, stable tie-break by record ID for deterministic ordering. Ignore invalid records in Home counts. A user-visible error is not a zero-record result.

| Given state / event | Display | Reconciliation |
|---|---|---|
| Initial archive unresolved, no prior snapshot | NH03 resolving; 3 stats dashes, calm placeholder, background/header/capture remain | No Empty/Normal definitive count, no fake card |
| Resolved 0 valid records | Empty Home | Transition only from actual successful resolved zero |
| Resolved 1 valid record | NH02 | single centered full-sized card, no pager affordance |
| Resolved 2+ valid records | NH01 | manual pager with adjacent visual peeks |
| Refresh pending with valid previous snapshot | prior NH01/NH02 preserved | hold values, selected FishRecord ID, media and actions |
| Refresh fails with previous valid snapshot | prior NH01/NH02 preserved | no error-page takeover, no transient Empty |
| Refresh returns new records, selected ID still exists | NH01/NH02 updated | keep same FishRecord ID selected, recompute page index from new order |
| Refresh returns new records, selected ID removed | NH01/NH02 updated or Empty | choose most recent valid record; no invalid/stale pointer |
| Media image unavailable or later recovered | stay NH01/NH02 | swap media subview only, preserve card, footer and ordering |
| After final valid FishRecord deleted and successful refresh | Empty Home | no earlier temporary Empty during network lag |
| Statistics server partial failure | keep existing valid local fallback/stale snapshot | no fake zeros during unresolved state |

## Statistics and formatted data

Three equal-weight groups: 鱼种 / 鱼获 / 记录天数. Positive server species/catch totals are authoritative when available; otherwise derive from sanitized valid local records. Record days = count of distinct valid sanitized catch dates. For initial unknown, show — — —. "记录天数" is informational with no click semantics/ripple/haptic even if a previous runtime wrapper exists.

Hero text hierarchy: species (fallback 鱼获), valid positive measurements length/weight only, context time/location only when present. Omit absent rows and separators, never display null, 0kg placeholder, unidentified species invention, fake fish photo or recognition confidence. Species, measure and context each one line; text end ellipsis; preserve time over location when space is constrained. Use real source images and same card outer media bounds/radius, with media failure placeholder only in the image region.

## Identity and navigation

| Gesture | Destination / action | Additional constraint |
|---|---|---|
| Avatar real or logged-in default | My/Profile | visual + hitbox parity; no setup toast |
| Guest entry | Login/Register | visual identity distinct from logged-in default |
| 鱼种 | Fish Guide | only when stats resolved |
| 鱼获 | My Catches | only when stats resolved |
| 记录天数 | none | no click/action semantics |
| 全部 | My Catches | even when one record |
| Selected HOME Hero tap | FishRecordDetail(recordId) | use stable ID, not pager index |
| Horizontal Hero swipe 2+ | select different record | user-driven; no auto-advance/circular wrap |
| One-record Hero gesture | no page transition | still tappable into detail |
| Primary capture | existing Identify flow | no gallery shortcut on Normal Home |
| Decorative lake/backdrop | none | no hidden hit region |

No new tabs, achievements, auto slideshow, first-catch success overlay or surprise navigation.

## Avatar contract

Shared canonical asset semantics: design/system/components/profile_avatar_v1/default_profile_avatar_contract.json. NH02 JSON is a compatibility consumer, not independent authority. Signed-in real avatar, signed-in missing/loading-failure default profile avatar and Guest entry are three distinct semantics. Android resource coincidence MUST NOT erase Guest-vs-default optical difference. Visual 92 reference-px diameter is NOT the hit target.

## Automated behavior checks

S01 initial unresolved -> NH03; S02 resolved zero -> Empty; S03 first save -> NH02; S04 second save -> NH01; S05 deletion of last -> Empty; S06 refresh error retain Home; S07 media failure/recovery retain record ID; S08 reorder/insertion retains selected ID; S09 removal selects latest surviving record; S10 stats precedence/local fallback; S11 independent navigation route callbacks; S12 one-record no pager swipe; S13 2+ manual pager not auto; S14 missing/long field presentation; S15 avatar real/missing/failure/guest.

Every test should assert state and routed record ID / semantics, not merely a screenshot. Time-based transitions should use controllable fake repository data. Avoid flakiness from network and arbitrary sleep.
