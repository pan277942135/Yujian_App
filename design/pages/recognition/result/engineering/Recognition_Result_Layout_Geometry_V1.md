# Recognition Result Layout Geometry V1

Status: **FROZEN**  
Purpose: convert the five 941×1672 Frozen references into deterministic adaptive layout geometry.

Machine-readable authority: `layout_geometry_contract.json`.

## 1. Coordinate model

Canonical engineering content canvas:

- width: **360dp**
- height: **640dp**
- origin: top-left of the app content area **after system top inset**
- host page owns system status/navigation/gesture insets
- all Y values below are relative to the post-top-inset content origin

Reference conversion:

- Frozen width: 941px → 360dp
- Frozen height: 1672px → 640dp
- X conversion: `x_dp = x_ref_px × 360 / 941`
- Y conversion: `y_dp = y_ref_px × 640 / 1672`

Do not use reference pixels directly in Android layout.

## 2. Global geometry

Shared:
- Top Navigation: `BACK_TITLE`, min height **56dp**
- Top Nav → Hero gap: **8dp**
- canonical content horizontal margin: **20dp**
- canonical Hero: **320 × 248dp**
- Hero aspect ratio: **1.2903**
- Hero radius: shared `YuJianRadius.heroCard`
- Hero → first state content: **16dp**
- normal intra-section gap: **12dp**
- compact related gap: **8dp**
- action height: **56dp**
- action pair gap: **12dp**
- bottom content padding: **20dp**
- paired action horizontal margin: **24dp**
- vertical scrolling is preferred over compressing frozen hierarchy

Hero media placement inside this box is owned by `../media/Recognition_Result_Hero_Media_Contract_V1.md`.

## 3. Canonical 360dp layouts

### RR01 High

| Region | X | Y | W | H |
| --- | ---: | ---: | ---: | ---: |
| Top Navigation | 0 | 0 | 360 | 56 |
| Hero | 20 | 64 | 320 | 248 |
| Species Identity | 20 | 328 | 320 | 44 |
| Metadata Strip | 20 | 384 | 320 | 72 |
| Memory Note | 20 | 468 | 320 | 80 |
| Dual CTA | 24 | 564 | 312 | 56 |

High dual CTA internal usable width = 300dp after 12dp gap:
- Continue Memory: **132dp**
- Save Catch: **168dp**
- ratio authority: **44 / 56**

### RR02 Medium — unresolved

| Region | X | Y | W | H |
| --- | ---: | ---: | ---: | ---: |
| Top Navigation | 0 | 0 | 360 | 56 |
| Hero | 20 | 64 | 320 | 248 |
| Confirmation Prompt | 20 | 328 | 320 | 28 |
| Candidate Row | 20 | 368 | 320 | 112 |
| Other-species Action | 20 | 488 | 320 | 44 |

No save CTA appears before explicit confirmation.

### RR02 Medium — resolved

Keep Hero, prompt, candidate row and other-species action visible. Append:
- Metadata Strip: top gap **16dp**
- Memory Note: top gap **12dp**
- Dual CTA: top gap **16dp**
- bottom padding **20dp**

Resolved Medium is intentionally scrollable on 360×640. Do not collapse the candidate confirmation region after selection.

### RR03 Low

| Region | X | Y | W | H |
| --- | ---: | ---: | ---: | ---: |
| Top Navigation | 0 | 0 | 360 | 56 |
| Hero | 20 | 64 | 320 | 248 |
| Main Message | 20 | 328 | 320 | 34 |
| Recovery Pair | 24 | 378 | 312 | 56 |

Low pair internal usable width = 300dp:
- Manual Species: **174dp**
- Retake: **126dp**
- ratio authority: **58 / 42**

Before species resolution, no Metadata / Note / Save regions exist.

### RR04 No Fish / RR05 Image Quality

Both use the same geometry family:

| Region | X | Y | W | H |
| --- | ---: | ---: | ---: | ---: |
| Top Navigation | 0 | 0 | 360 | 56 |
| Hero / Evidence Photo | 20 | 64 | 320 | 248 |
| Recovery Panel | 20 | 332 | 320 | 128 |
| Primary Retake | 24 | 480 | 312 | 56 |
| Secondary Gallery | 24 | 548 | 312 | 56 |

Recovery panel internal:
- padding: **20dp**
- title line-height target: **30dp**
- title → guidance gap: **8dp**
- guidance max lines: **3**

## 4. Width adaptation

| Window width | Content margin | Hero W × H | Candidate card width (3 cards) | CTA margin |
| ---: | ---: | ---: | ---: | ---: |
| 320dp | 16dp | 288 × 224dp | 90.7dp | 24dp |
| 360dp | 20dp | 320 × 248dp | 101.3dp | 24dp |
| 393dp | 20dp | 353 × 274dp | 112.3dp | 24dp |
| 411dp | 24dp | 363 × 282dp | 115.7dp | 24dp |

Rules:
- Hero height follows width / 1.2903 and rounds to the nearest whole dp.
- Do not exceed 282dp Hero height in V1.
- Do not go below 224dp solely because width is small.
- candidate gap remains 8dp.
- candidate width = `(contentWidth - 16dp) / 3`, clamped to **88–116dp**.
- 2-candidate state uses two centered **136dp max** cards with 8dp gap.
- >3 candidates: display top 3 only; do not create a horizontal carousel in V1.

## 5. Height adaptation

For post-inset content height:
- **>=640dp**: use canonical heights.
- **600–639dp**: preserve geometry; allow scroll when content exceeds viewport.
- **<600dp**: Hero may reduce by at most **12%**, never below **208dp**; preserve section order and action heights.
- **IME visible**: keep field in view with scroll/BringIntoView; do not resize action buttons below 56dp.
- font scale / accessibility expansion: allow vertical growth and scrolling; do not shrink text manually.

## 6. Safe areas

- top system inset is applied before Top Navigation.
- bottom system inset is applied after page content; bottom padding remains **20dp** in addition to the system inset.
- IME is not a substitute for bottom system inset handling.
- no required action may be hidden under status/navigation bars or IME.

## 7. Typography placement

Use shared tokens; geometry reserves:
- Top Nav title: 20sp / 26sp line height
- Species title: 30sp / 36sp; <=359dp width may use 28sp / 34sp
- Medium prompt: 20sp / 28sp
- Low main message: 26sp / 34sp
- Recovery title: 22sp / 30sp
- Body/guidance: 14–16sp / 20–24sp
- Candidate species: 14sp / 20sp

No arbitrary per-page font scaling beyond this contract.

## 8. Scroll ownership

Scrollable content:
- High: only when height/font/IME requires it.
- Medium unresolved: normally non-scroll on 360×640.
- Medium resolved: scroll allowed and expected.
- Low: normally non-scroll.
- Recovery states: normally non-scroll.

Top Navigation scroll behavior:
- remains in normal page flow in V1;
- do not introduce collapsing toolbar behavior.

## 9. Geometry acceptance

Critical anchors:
- Top Navigation, Hero frame, CTA group: **±4dp**
- state content blocks: **±6dp**
- candidate card size/gap: **±3dp**
- action height/gap/ratio: exact shared component contract
- Hero aspect ratio deviation: **<=1.5%**

If device adaptation uses the approved width/height rules, it is conformant even when absolute coordinates differ from 360dp canonical positions.
