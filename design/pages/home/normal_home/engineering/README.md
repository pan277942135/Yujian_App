# Normal Home — Engineering Authority Index V1
Status: ACTIVE_ENGINEERING_CONTRACT / DESIGN_UNCHANGED / RUNTIME_ACCEPTANCE_PENDING
Scope: Normal Home with at least one resolved valid FishRecord, and its initial resolving/refresh continuity behavior.
Established: 2026-10-10
Branch base: Work A 1f83f8bc56a8622f37a16148bfc58ab309b21fd8. This documentation-only branch does not change Kotlin, runtime assets, test code, main, or PR #122.

## How Work and Validation must use this package

1. Read this index first, then Layout_Responsive_Contract_V1.md, State_Interaction_Contract_V1.md, Motion_Feedback_Contract_V1.md, and Visual_Runtime_Acceptance_V1.md.
2. Take image composition from immutable Frozen masters, not rendered Kotlin screens.
3. Implement only deviations necessary to meet applicable requirements. Kotlin is evidence of current implementation, not design authority.
4. Perform build/unit/lint and relevant instrumented tests. A green build is not a Visual PASS.
5. Validation independently evaluates the SAME requirements against a built APK and real evidence. Missing profile evidence is NOT_RUN or BLOCKED_INFRA, not PASS.
6. Do not ask for product A/B alternatives when this package determines the rule. Escalate only real product-contract contradictions that cannot be reconciled using anchor kinds and the authority order.

## One explicit authority order (by type)

| Rank | Normative source | Owns |
|---|---|---|
| 1 | design/system/core_visual_v1/reference/normal_home_v1.png, SHA-256 6ab9d3348b4a9a7e77ddca3a06235b4991798a309bd3512cc6fb9ea7aeb1d377, 1080x1920 | NH01 visible reference composition and actual visible raster |
| 2 | NH02 dedicated frozen PNG and its manifest | NH02 one-record rendered reference |
| 3 | design/system/backgrounds/morning_lake_v1/assets/Morning_Lake_Master_V1.png, SHA-256 5fba741088ea186e898cd3bee5777e35978436f427492e6e6122528ef6aa91d7 | reusable lake background pixels only |
| 4 | this engineering package: Layout, State, Motion, Acceptance | runtime coordinate semantics, responsive/safety fallback, state transitions, automated acceptance and reporting |
| 5 | NH01–NH06 page specs and frozen boards | page-specific details not overridden here; NH05 long-screen one-piece rule is RETAINED |
| 6 | shared YuJian component/token/font/avatar/capture system | shared visual primitives, hit targets, haptic and accessibility conventions |
| 7 | older closure reports, V2 DRAFT, comparison matrix, rejected B examples, code | historical/reference only, NEVER authorize contrary implementation |

Priority is field-specific: the background master does not define page geometry; a flattened screenshot does not define invisible touch rects; shared components do not override NH01's Frozen visible page bounds. Do not infer that an unresolved visual bbox can be decided by implementation constants.

## Decision register

- A1 APPROVED: V1.1 measured physical-pixel typography role numbers remain; no arbitrary enlargement, no cancellation of user fontScale.
- A2 APPROVED: NH05 single vertical shift using safe usable height, unchanged between-component gaps; seven-bucket elastic B is REJECTED.
- Window geometry: physical app-window top-left origin, with safe insets used once for width/height budget and safety constraint (not added a second time to y).
- CTA/Camera: implementation-known CTA container y1495 and camera touch-box y1564 are NOT equivalent to NH05 shorthand visual y1504 / y1582. Anchor kinds MUST be distinguished; checksum-verified Frozen ROI must decide visible anchor alignment. No guessing or forced identity.
- Short-screen and enlarged-font compatibility: use deterministic accessibility overflow policy described in Layout contract when normal fixed composition cannot fit safely; it is a **conditional safeguard**, not a replacement layout for standard target screens.
- No new ambient/page sounds. No new decorative lake motion. No new first-catch celebration.

## Freeze vs execution statuses

DESIGN_FROZEN is about approved product specification and assets only.
ENGINEERING_CONTRACT_ACTIVE means Work may implement deterministically using this entry.
RUNTIME_PASS requires evidence satisfying the acceptance matrix at the tested HEAD.
OPEN_VISUAL_EVIDENCE means screenshot/ROI/device proof still pending. It is never silently promoted to PASS.

The only currently unmeasured visual source dispute is CTA/Camera visible anchor interpretation. Required source acquisition: original immutable PNG bytes, 1080x1920, exact registered SHA. A visually similar image or screenshot crop is not authority. Touch rect derives from interaction contract and actual Android bounds, not raster inspection.

## Ownership

Work A: implement Normal Home, commit and push Kotlin checkpoints, run targeted CI.
Independent Validation Work: inspect APK provenance, run stated profiles, produce screenshots, UI bounds, motion evidence and separate disposition.
Product owner: only versioned design decisions / genuinely conflicting immutable product authorities, and final product acceptance.

## File index

- Layout_Responsive_Contract_V1.md — P0, sole implementation mapping and accessibility overflow rules.
- State_Interaction_Contract_V1.md — P1, state machine, navigation and data-content behavior.
- Motion_Feedback_Contract_V1.md — P1, motion, haptic, sound and lifecycle.
- Visual_Runtime_Acceptance_V1.md — P1, independent test matrix and evidence gates.
- normal_home_acceptance_matrix_v1.json — machine-readable checklist; reflects Markdown contracts, does not override them.
- Archive_And_Supersession_V1.md — P2, legacy source status and non-destructive cleanup.

Historical design-state package remains linked in ../README.md; frozen PNGs, manifests and historical commits remain immutable. 

## Repeatable static governance gate

Run from repository root: python3 scripts/design/verify_normal_home_contract_v1.py. This checks document pointers, authority priorities, 38 distinct test IDs, statuses and *actual checked-out Frozen PNG* hash/dimensions. A static gate PASS does NOT prove Android runtime behavior. Runtime requirements must still be implemented and measured against an exact APK SHA.
