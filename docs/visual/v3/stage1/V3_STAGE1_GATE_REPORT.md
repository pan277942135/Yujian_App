# YuJian V3 Stage 1｜Visual Asset and Parameter Freeze Gate

**STATUS: CORE_VISUAL_SPEC_FROZEN_WITH_TUNABLES**  
**Repository:** pan277942135/Yujian_App  
**Branch:** docs/crystal-orb-waterlight-v3-stage1-asset-audit  
**Stage 0 base:** 7b050748b55773f68e61291525517e930dd277cf  
**Stage 1 source audit HEAD before this freeze update:** 15605c7002ecedcfe735fc5706907f855b472493

## Completed freeze scope

- Corrected 26KF sequence and source mapping, including independent KF00 Home and KF01 Capture supplements.
- KF14 return semantics confirmed: reuse KF05 path identity/geometry and reverse direction.
- KF25 behavior confirmed: save succeeds and returns to KF00 Home; no new save-success image required.
- 12.2 Glyph Emergence mapped to six-frame Glyph board Frame 5; no 27th KF.
- Orb source master and existing runtime WebP provenance/alpha geometry measured.
- Orb screen anchor and visible diameter measured from KF02/KF03/KF05 independent references.
- Resident topology frozen: five IDs, two BACK and three FRONT; Resident is not Task.
- Optical and fluid visual invariants frozen; unmeasurable numeric shader values explicitly remain tunable.

## Frozen vs tunable

| Area | Gate |
|---|---|
| 26KF source mapping | PASS |
| 13-stage 6000ms minimum timing inheritance | PASS |
| Orb immutable core source and geometry | PASS |
| Orb screen anchor reference measurement | PASS_APPROX_REFERENCE |
| Resident identity and front/back topology | PASS |
| KF14 same-path reverse | PASS |
| KF25 save → Home | PASS |
| Exact Resident period/phase/width/alpha | CANDIDATE_ONLY |
| Exact fluid velocity field | TUNABLE |
| Physical IOR / Fresnel numeric curve | TUNABLE |
| Bloom threshold / radius / strength | TUNABLE |
| Runtime Mask / P* | RUNTIME_ONLY; not sourced from visual assets |
| Android GPU / PixelCopy visual acceptance | NOT_RUN; Stage 3/5 evidence required |

## Important corrections to prior Stage 1 draft

1. Removed unsupported S1/S2 breath period and amplitude numbers; no approved source established them.
2. Corrected V3_26KF_CORE_ROLE_MATRIX.md to the canonical KF00–KF25 sequence.
3. Replaced the old KF14–KF25 catch-all with individual rows and the user-confirmed KF14/KF25 behavior.
4. The 26KF contact sheet remains a contact sheet; its crops are not independent original masters.
5. Old R02/R03/R04/R05/R06 profile numbers remain traceable candidates because their package status is TUNE_BASELINE or VISUAL_CANDIDATE_PENDING_PRODUCT_REVIEW.

## Stage 3 entry

Stage 1 is complete for the **core visual specification** and allows Stage 3 Orb-only GPU work. It does not authorize Task energy integration, Mask/P*, fish contour, Glyph, Result, or any parallel Runtime/Clock. No Kotlin, Shader, Runtime, Model, or APK changes were made in Stage 1.
