# Normal Home — Independent Visual and Runtime Acceptance V1 (P1)
Status: ACTIVE_ACCEPTANCE_CONTRACT / TEST_IMPLEMENTATION_AND_RUNTIME_EVIDENCE_PENDING
Consumes Layout_Responsive_Contract_V1.md, State_Interaction_Contract_V1.md, Motion_Feedback_Contract_V1.md, frozen NH01–NH06 boards and machine matrix JSON in this folder. A PASS is a decision about a precise APK SHA and target device profile, not a PR label.

## Evidence provenance (mandatory)

Every instrumented result SHALL include: source commit SHA, APK filename/size/SHA256, test APK SHA256, physical app-window W,H, screenshot native W,H and SHA256, d/fontScale, measured L,T,R,B, portrait/landscape and window mode, OS/API, test class/test ID, runtime mode NORMAL_FIXED / SAFE_OVERFLOW / UNSUPPORTED_WINDOW, timestamp, bitmap source hashes, and recorded PASS/FAIL/BLOCKED_INFRA/NOT_RUN. Photos/mockups/derived resized images may aid review but cannot count as native screenshots.

Per visual node log reference anchor kind, target rectangle, boundsInWindow, boundsInRoot, full text line boxes, visible glyph path bounds, touch semantics rect, overlay/crop visibility, inferred pixel delta to same-kind Frozen rectangle, and confidence/measurement rule. Contrast/anti-alias thresholds are not invented here. Round for reports, not for each intermediate layout calculation. A source hash mismatch fails source provenance and blocks optical comparison.

## Acceptance scenarios

| ID | Scenario / profile | Automated assertion | Required visual/other evidence |
|---|---|---|---|
| G01 | Frozen 1080x1920 S=1, zero-inset mathematical fixture | exact nominal geometry and unit tests | checksum-verified NH01 reference and same-kind target bbox |
| G02 | measured phone 9:16 | no duplicated top inset, all required rects safe | screenshot, window insets, glyph and touch rectangles |
| G03 | 19.5:9 / 20:9 / 21:9 | same shared delta for six regions, no stretching | original native screenshots for each available profile |
| G04 | 1080x2340 no-inset synthetic fixture | delta=151.2px exactly | synthetic fixture labeled NON_DEVICE, not a Visual PASS |
| G05 | nonzero asymmetric insets / RTL | x uses physical left L, no +T y translation; ≥48dp targets | measured root/window rectangles |
| G06 | 360/393/480dp; density 2/2.75/3 as feasible | widths, TextLayoutResult, no clip/overlap | profile metadata, glyph render/ellipsis |
| G07 | fontScale 1 / 1.15 / 1.3 | no clipped visible text, no false fontScale cancellation | screenshots + text layout |
| G08 | constrained short window or multiwindow | deterministic SAFE_OVERFLOW with reserved Capture+CTA and scrollable content | gesture capture and all-region reachability |
| G09 | narrow avatar and all tap affordances | interactive >=48dp and within measured safe rect | unmerged semantics bounds |
| V01 | NH01 multi-record | Frozen composition, selected Hero/neighbor peeks and selected photo identity | original screenshot and optical differencing |
| V02 | NH02 one-record | same full Hero size, centered, no fake neighbors | original screenshot, asset reference hash |
| V03 | NH03 resolving/media-fail/error refresh | same scene, no fake Empty, placeholder only local | screen and state trace |
| V04 | NH04 complete/missing/long fields | one-line hierarchy, no fake metadata, no clip | text path/overflow metadata |
| V05 | NH06 environment | Morning_Lake_Master exact provenance, centered Crop, no Empty sunrise scene | manifest hashes and viewport crop samples |
| V06 | CTA/Camera optical arbitration | visual glyph / rim from Frozen, NOT inferred touch rect | verified 1080x1920 PNG crop ROI + measured visual-versus-touch bboxes |
| S01–S15 | state and navigation transitions | state records, route callbacks, pager selected ID, accessibility semantics | deterministic fake-repository event logs |
| M01–M08 | motion/haptic/sound | timed keyframes, Reduce Motion, lifecycle, no extra effects | 16-second video and device/test traces |

## Existing implementation coverage versus new acceptance requirements

Do not misrepresent existing tests as satisfying every row.
- Existing NormalHomeRuntimeContractTest: asset/provenance checks (partial V05).
- Existing NormalHomeDataParityTest: statistics, layout relations, text bounds, content/media and, in new revisions, Window metrics (partial G02/G05/G06/V01/V04).
- Existing NormalHomeHeroBehaviorTest: single-card navigation, manual pager, media fallback and default avatar (partial V02/V03/S11/S12/S13/S15).
- Existing HomeStatsSemanticsTest: non-clickable record days, stats, some width/text fixtures (partial S10/S11/G07).
- Existing Normal Home screenshot/motion gate: targets native output, but historical API28 jobs did not establish all target physical dimensions or guaranteed exact profile screenshots.
- New automated assertions must cover SAFE_OVERFLOW, RTL/asymmetric insets, preservation of selected FishRecord ID across refresh/reorder, dynamic Reduce Motion toggle, duration/keyframes and sound/haptic absence with inspectable logs. Existing coverage is not proof these tests already exist.

## Gates for decisions

- SOURCE_FAIL: wrong/missing frozen hash or APK provenance. No visual comparison permitted.
- PASS: all mandatory automated assertions pass AND all required evidence exists for the stated profile and matches requirements.
- FAIL: tested violation (including clipped glyph, unsafe hitbox, wrong media, unsanctioned animation, hash mismatch).
- BLOCKED_INFRA: target exact device geometry or capture capability unavailable despite an attempted run; attach runner/device log.
- NOT_RUN: no run or evidence. Neither NOT_RUN nor BLOCKED_INFRA may be reported as PASS.
- REVIEW_REQUIRED: measurable design contradiction genuinely unresolved (e.g. CTA/Camera optical edge); Work may close other rows but cannot mark V06 Visual PASS without verified raster.

CI build/unit/lint/model trace PASS is prerequisite engineering hygiene, never sufficient Visual PASS. PR jobs may skip API28 on pull_request; verify a push-triggered runtime job actually executed with the target SHA. Never rerun a huge unrelated matrix to paper over missing targeted profile.

## Acceptance workflow

1. Check source frozen manifests and SHA-256. Confirm Work branch HEAD and APK built for that exact commit.
2. Run unit + Compose/instrumentation against deterministic record fixtures. Collect device geometry and text traces.
3. Capture real native output on matching resolution where possible; compare every available state with corresponding Frozen raster, never resize to pass.
4. Capture motion ≥16s plus Reduce Motion and pause/resume samples.
5. Complete normal_home_acceptance_matrix_v1.json per ID with status and evidence path/hash.
6. Publish one validation report per commit: summary, delta table, exception ledger, blocked profiles, links, no invented tolerance and no unsupported "pixel perfect" claim.
7. Keep PR Draft and no merge until authorized by product owner; independent Validation Work may reject an APK even when its Build passed.

## Quality goals with objective decisions

Metrics: all required targets accessible, all page regions visible/reachable, source hashes exact, normal-mode shared shift exact, card geometry/ref proportion exact in synthetic fixture, non-transient real-device actual px bounds reported, motion numerical keyframes match frozen tokens and unapproved sound/haptic absent. Optical tolerance is explicitly UNSET until hash-verified reference ROI and repeatable measurement method; therefore optical criterion remains REVIEW_REQUIRED instead of inventing ±px thresholds.

## Pre-flight static governance test

Before device tests run python3 scripts/design/verify_normal_home_contract_v1.py from the repository root. Its PASS certifies references, matrix structure and Frozen source files in that checkout only. It is explicitly **not** a surrogate for G/V/S/M Android runtime PASS. CI integration of each runtime case is a separate implementation task, tracked as implementation_coverage in the JSON matrix.
