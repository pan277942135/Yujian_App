# NH01 Multi-Screen Static Hi-Fi Responsive Contract V1

| Field | Value |
| --- | --- |
| Contract ID | `NH01_MULTI_SCREEN_STATIC_HIFI_V1` |
| Contract version | V1 |
| Static Hi-Fi | `STATIC_HIFI_FROZEN` |
| Responsive rules | `STATIC_RULES_FROZEN` |
| Android Runtime | `NOT_VERIFIED` |
| Effective date | 2026-10-22 |
| Machine-readable contract | `NH01_MULTI_SCREEN_RESPONSIVE_CONTRACT_V1.json` |
| Asset manifest | `NH01_MULTI_SCREEN_STATIC_HIFI_V1_MANIFEST.json` |

## 1. Authority, scope, and original reference

This contract registers five recovered NH01 populated-home static hi-fi originals and the responsive rules used to review them in Design Manager. It adds a static design archive; it does not replace the original NH01 V1 page raster, the Gemini Adaptive V2 Android layout authority, the existing Android acceptance matrix, or any NH07 avatar asset.

The original frozen reference remains `design/system/core_visual_v1/reference/normal_home_v1.png`: **1080×1920 px**, logical reference **360×640 dp**, density relation 3×, SHA-256 `6ab9d3348b4a9a7e77ddca3a06235b4991798a309bd3512cc6fb9ea7aeb1d377`. Its repository bytes remain unchanged. M01–M05 are additive responsive design examples and do not replace or inherit the original image's SHA.

Target dp dimensions describe the intended logical viewport. PNG canvas dimensions describe the actual source raster. These are separate facts. In particular, M05 fixes width at 480 dp only; its Android window height is deliberately unspecified. Its 1024×1536 px artwork ratio does not define a native window height.

## 2. Frozen asset mapping

All five files are the actual recovered PNG originals. Their bytes were copied unchanged from the user-provided source archive and their SHA-256, byte count, PNG signature, and canvas dimensions were rechecked. The machine manifest is the exact per-asset source for paths, IDs, dp targets, pixel dimensions, bytes, and SHA-256.

| Asset ID | Target logical viewport | Actual PNG canvas | SHA-256 | Status |
| --- | --- | --- | --- | --- |
| `NH01-M01-320x480dp-STATIC-HIFI-V1` | 320×480 dp | 1024×1536 px | `a3808f2fb93d48494eafbb6ba061dbd9c1dd019348488355780c8f4504636692` | `STATIC_HIFI_FROZEN` |
| `NH01-M02-360x640dp-STATIC-HIFI-V1` | 360×640 dp | 941×1672 px | `8a6721552cec97113c2a77200a07bb76b954c1a16f4e0f10d8410b9e873a90c0` | `STATIC_HIFI_FROZEN` |
| `NH01-M03-390x844dp-STATIC-HIFI-V1` | 390×844 dp | 853×1844 px | `6a5e2e9fd18d2b06f9452b6a6c95802e41cfc0450b7ec77cf0d1500c6b3932ff` | `STATIC_HIFI_FROZEN` |
| `NH01-M04-430x932dp-STATIC-HIFI-V1` | 430×932 dp | 853×1844 px | `07846d3089b7c2a0b0e6c858dbd3f97890121b225092dde7d7622f0a461b9893` | `STATIC_HIFI_FROZEN` |
| `NH01-M05-480dp-WIDTH-STATIC-HIFI-V1` | 480 dp wide; height not fixed | 1024×1536 px | `ee2ed666107bd7f4263cf140438a5a1cbb171af0199e57646372e2e1d8cc8de2` | `STATIC_HIFI_FROZEN` |

## 3. Page structure

The NH01 composition retains these regions and relationships:

1. The existing Morning Lake environment remains behind the complete home composition.
2. The header includes brand title artwork and the profile avatar; the statistics region follows it.
3. The statistics region retains the three home statistics.
4. The Recent Catch heading and All entry introduce the catch-card area.
5. The selected main Hero is centered. Side Peek cards appear only for real adjacent FishRecords.
6. Each card presents species, positive length and/or weight with units, time, and optional location. Time has priority over optional location.
7. The record-next-catch CTA and camera control remain one bottom action group.
8. Top and bottom system safe areas constrain the full composition. No action may collide with a card or system navigation.

When only one FishRecord exists, show one centered main Hero at the standard main-card size. Do not create an adjacent card, cloned record, or pager hint.

## 4. Monotonic responsive behavior

Compare the same layout variant at the same system font scale while safe viewport width and/or height increases. Key text roles, the selected Hero width, and camera visual size must never decrease. They may stop growing at their defined caps.

The current Gemini route derives a bounded viewport scale as:

`min(min(safeWidthDp, 560) / 360, safeHeightDp / 568).coerceIn(1.0, 1.2)`

For each text role, the pre-font-scale size is `clamp(safeHeightDp × role ratio, role minimum, role maximum) × viewportScale`, emitted as Compose `sp`. Hero target width is `min(min(trackWidthDp, 560dp) × 0.70, 337.2dp)`. Camera artwork size is `min(min(safeWidthDp,560dp)×0.18, safeHeightDp×0.10, 80dp)`, with a 56 dp floor. Each formula is monotonic as its input bounds grow and plateaus at its cap.

Do not use a legacy inverse scaling rule where a taller or wider viewport shrinks a key font, Hero, or camera artwork. Do not shrink all text or cards to conceal a layout failure.

For M05, the width-derived part of the font viewport scale is capped at 1.2; the final multiplier still depends on the real `safeHeightDp`. Because M05 has no fixed height, this contract does not claim one fixed native multiplier or native layout result for it.

## 5. Hero and Peek geometry

- Main Hero aspect ratio: **247:293**.
- Target width: **min(min(trackWidth, 560 dp) × 0.70, 337.2 dp)**.
- Maximum Hero height: **400 dp**.
- Normal readable-height range: **148–220 dp**.
- Existing 124 dp floor exception applies only at **320×480 dp**, system `fontScale=1.0`, card width at least 145 dp, required copy readable, and no Middle scrolling.
- A Peek is a real neighboring FishRecord only. Its width is at most 82% of the main card, height is 66.7% of the main card, and its top is offset by 15 dp. Geometric overlap is no more than 1 dp.
- Pager IDs remain stable and finite. No fake side card or infinite wrapping.

For card content, retain the priority order species → positive measurements → time → optional location. Never invent a zero or unknown value. Preserve time when content is tight; shorten or ellipsize optional location first. Avoid a lone final CJK glyph or dangling separator. Existing Adaptive V2 wrapping may use up to two lines for species or auxiliary metadata in the constrained cases defined by that authority.

## 6. Typography roles and font-scale behavior

The viewport multiplier is separate from Android's system font scale. Compose applies system font scale once when it renders `sp`. Do not multiply system font scale into the `TextStyle` a second time. It may also be read for gap pressure, wrapping, and measured-height budgeting; those layout decisions are not another text-size multiplication.

| Role | Size formula before system font scale | Min–max | Line height | Notes |
| --- | --- | --- | --- | --- |
| Brand title | Approved title artwork, not a font; width `clamp(safeWidth×155/1080, 46, 70) dp`; height ratio 80:155 | Image geometry 46–70 dp wide | N/A | Font scale does not resize artwork as text. |
| Statistics number | `safeHeight×0.03×viewportScale` | 17–21 sp | 1.14× | System `sp` scale once. |
| Statistics label | `safeHeight×0.019×viewportScale` | 11–13 sp | 1.2× | System `sp` scale once. |
| Recent Catch heading | `safeHeight×0.027×viewportScale` | 16–19 sp | 1.2× | System `sp` scale once. |
| All entry | `safeHeight×0.021×viewportScale` | 13–15 sp | 1.2× | System `sp` scale once. |
| Species name | `safeHeight×0.036×viewportScale` | 20–26 sp | 1.2× | Existing local wrapping rule only. |
| Measurements | `safeHeight×0.023×viewportScale` | 13–16 sp | 1.2× | Preserve values and units. |
| Time and location | `safeHeight×0.019×viewportScale` | 11–13 sp | 1.3× | Preserve time; optional location yields first. |
| Record-next-catch CTA | `safeHeight×0.021×viewportScale` | 12–14 sp | 1.25× | Remains grouped with camera action. |

The actual base expression clamps `safeHeight × role ratio` to each role's min/max before applying the bounded viewport multiplier. At system font scales 1.3× and 1.5×, compress nonessential gaps first and use only the existing local wrapping rules. Never reduce fonts globally or apply arbitrary per-element shrink to hide overflow.

The old `Normal_Home_Typography_Scaling_Contract_V1.md` continues to document the legacy raster route; it is not the current Gemini V2 role-size source. Current Gemini role values are traceable to `GeminiNormalHomeContent.kt` and `GeminiHomeAdaptiveLayoutPolicy.kt`.

## 7. Camera target, action group, and safe bounds

- Camera artwork visual size stays within **56–80 dp**.
- Camera touch target is at least **72×72 dp**, measured separately from visible artwork.
- Other actions retain at least **48 dp** touch targets under the existing Gemini contract.
- The record-next-catch CTA and camera control form one footer action group.
- Keep that complete group inside the usable safe bounds. Apply system insets once. Keep the camera touch rectangle inside the safe content area and avoid overlap with the main card, CTA, or system navigation.
- Current footer geometry uses 6 dp between CTA and camera below 460 dp safe height, otherwise 11 dp. Footer top padding is 2 dp below 460 dp safe height, otherwise 4 dp. Footer bottom padding is 2 dp below 460 dp; otherwise 6 dp when the bottom inset is at least 40 dp, else 8 dp.

## 8. Spacing targets and measurement method

### 8.1 Recent Catch title to main card

Measure the rendered Recent Catch text layout bottom to the selected main Hero card's outer top using native `boundsInWindow`. Do not use the containing Row bottom or Pager center.

| Target viewport | System font scale | Visible gap |
| --- | ---: | ---: |
| 320×480 dp | 1.0× | 8±2 dp |
| 360×640 dp | 1.0× | 12±2 dp |
| 390×844 dp | 1.0× | 18±2 dp |
| 430×932 dp | 1.0× | 20±2 dp |
| 480 dp width extension; height unspecified | 1.0× | 24±2 dp |

**M05 addition:** the 480 dp width row is a new **static width-boundary rule**, version V1, effective 2026-10-22. It does not assign an Android window height or add a fifth viewport to the existing Android matrix. The current Gemini Adaptive V2.2 runtime title-to-card policy is height-based and its 1.0× target tops out at 20 dp. Keep the new 24±2 dp value within the static-preview scope until a native window height is specified and the runtime contract is explicitly reconciled. Do not change Android code or claim native acceptance based on this artifact.

### 8.2 Required adjacent-region measurements

Record all six intervals for each real native execution with the safe width, safe height, system font scale, layout variant, and screenshot/bounds evidence. Existing Gemini Adaptive V2 formulas remain the runtime reference; this archive does not silently replace them.

| Interval | Measure between | Current reference and check |
| --- | --- | --- |
| Header–Stats | Visible brand/avatar header content bottom → statistics row top | Adaptive V2: `max(9, clamp(safeHeight×0.035,14,28) − 3×fontPressure − 2×shortHeightPressure)` dp. Record actual gap; verify no header/statistics overlap. |
| Stats–Title | Statistics row bottom → Recent Catch text top | Adaptive V2: `max(12, base − 2×fontPressure)` dp; base 14 dp below 700 dp safe height, 16 dp below 840 dp, else 18 dp. Record actual gap and legibility. |
| Title–Card | Visible title layout bottom → selected Hero outer top | Targets in §8.1. Use native bounds, record font scale, never substitute Row/Pager bounds. |
| Card–CTA | Selected Hero outer bottom → visible CTA control top | Adaptive V2 Card–Footer formula plus existing footer top padding. Record actual gap; verify no collision and footer stays outside local Middle scrolling. |
| CTA–Camera | CTA visible control bottom → camera artwork top; also record each touch rectangle | Existing footer Column gap: 6 dp compact, otherwise 11 dp. Verify positive visible separation and no touch-bound collision. |
| Camera–BottomSafe | Camera artwork bottom → usable content bottom after bottom system inset | Existing footer bottom padding: 2 dp compact; 6 dp when bottom inset ≥40 dp; otherwise 8 dp. Record the inset once and verify camera touch bounds remain safe. |

For spacing pressure, `fontPressure=clamp((systemFontScale−1)/0.5,0,1)` and `shortHeightPressure=clamp((620−safeHeightDp)/220,0,1)`. Existing Card–Footer is `max(3, clamp(safeHeightDp×0.018,8,14) − 3×fontPressure − 2×shortHeightPressure)` dp. Do not reinterpret environmental whitespace on tall screens as a failure; do report tightly stacked title/Hero, overlap, or inaccessible controls.

## 9. Short screens and failure reporting

On short safe windows, compress nonessential whitespace first, preserve required content and readable type, and use only the existing local Middle-scroll behavior where permitted. Do not hide necessary content, globally shrink type/cards, or double-apply safe insets to claim a pass.

When the full required layout cannot fit the real safe window under the active contract, report `SAFE_OVERFLOW`, `UNSUPPORTED_WINDOW`, or `FAIL_CONSTRAINT` as appropriate. A static image or browser preview never proves that the real Android window passed.

## 10. Background, avatar, and fish photos

- Continue using the immutable Morning Lake master, SHA-256 `5fba741088ea186e898cd3bee5777e35978436f427492e6e6122528ef6aa91d7`, with the current Gemini route's TopCenter/Crop treatment. Do not add tint/blur or derive a new lake image from a screenshot.
- Follow `NH01_Avatar_Visual_Contract_V1_1_FROZEN.md` for the Gemini route avatar. A real-person avatar visible in one of these static sample images is sample content, not a default-avatar asset. This archive changes no login/guest state, avatar permissions, tap destination, or user-photo behavior.
- Preserve genuine FishRecord imagery and the existing photo crop/Fit and same-source backdrop constraints. Static examples do not supply runtime FishRecord data.
- Preserve current distinctions between guest identity and signed-in users even when artwork is shared.

## 11. Authority crosswalk and conflict handling

| Existing authority | Effective scope in this archive |
| --- | --- |
| `Normal_Home_Responsive_Contract_V1.md` | Retained legacy responsive contract; not overwritten or globally superseded. |
| `NH01_GEMINI_ADAPTIVE_V2_UNIFIED_AUTHORITY_V1.md` | Current Gemini route authority for layout, four existing visible title/card targets, Hero/Peek, metadata, background, avatar, motion, and acceptance. |
| `NH01_GEMINI_ADAPTIVE_V2_ACCEPTANCE_V1.md` | Current native and route acceptance process; unchanged. |
| `NH01_GEMINI_ADAPTIVE_V2_ACCEPTANCE_MATRIX_V1.json` | Current exact 24-run Android matrix; unchanged. |
| `NH01_ADAPTIVE_VISUAL_BALANCE_V2.md` | Current visual balance, monotonic scaling, and touch geometry. |
| `Normal_Home_Typography_Scaling_Contract_V1.md` | Legacy V1 raster typography only; not the Gemini V2 font-role source. |
| `GeminiNormalHomeContent.kt` / `GeminiHomeAdaptiveLayoutPolicy.kt` | Implementation-derived trace for current Gemini role and spacing formulas; no Android code is changed here. |
| `NH01_Avatar_Visual_Contract_V1_1_FROZEN.md` | Current Gemini avatar visual and interaction semantics. |

When this archive provides no explicit static-only extension, the current route-specific Gemini V2 authority controls Android behavior. The M05 width-only spacing target is explicitly additive static preview authority and is in conflict with the height-based runtime title/card policy; keep both scopes visible and require a separately authorized Android contract revision after a real height is set. Do not silently edit any of the referenced frozen contracts.

## 12. Android acceptance status

Android remains **`NOT_VERIFIED`**. The existing matrix remains 24 real native executions: 320×480, 360×640, 390×844, and 430×932 dp × system font scales 1.0×, 1.3×, and 1.5× × `ADAPTIVE_V2` and `V3_BASELINE`, on the current API 28 gate. M05 does not add another run because its window height is not defined. The matrix and evidence requirements remain in `NH01_GEMINI_ADAPTIVE_V2_ACCEPTANCE_V1.md` and `NH01_GEMINI_ADAPTIVE_V2_ACCEPTANCE_MATRIX_V1.json`. Static preview, Design Manager browser, and image-integrity checks do not change native `NOT_RUN` to `PASS`.


---

## Appendix A — NH01 Responsive Foundation V1.1

**Status: FROZEN · Approved 2026-10-23 · Version: V1.1 · Scope: foundation only**

This appendix defines the current NH01 responsive foundation revision V1.1: stable page-group structure and an explicit, computable 1:1 rule for allocating positive long-screen surplus. V1.1 supersedes V1's 2:1 allocation; all unrelated frozen constraints remain unchanged. It does not claim Android runtime verification.

### A.1 One layout policy, three stable groups

- **A — Top-information allocation band:** brand title + profile/avatar action + the three statistics. It ends at the statistics-row bottom; the 320×480dp compact target keeps this two-row header.
- **B — Catch-content allocation band:** starts at the “最近鱼获” heading top and includes the heading / “全部” action, selected Hero, and eligible real-neighbor Peek cards. It ends at the selected Hero's outer bottom.
- **C — Bottom-action allocation band:** starts at the CTA visible-control top and includes “记录下一条鱼” + the primary camera action.

A/B/C are **logical allocation bands, not new Compose containers**. Preserve the current Top/Middle/Footer implementation hierarchy: the Recent Catch heading may remain physically rendered in Top while logically belonging to band B. Use one constraint-driven policy across viewports; do not copy screenshot coordinates into device-specific layouts. Keep within-band relationships stable and adjust inter-band spacing before changing band internals.

### A.2 Long-screen surplus allocation

Measure the actual safe content height `H_safe` after applying system insets exactly once. Measure natural heights `hA`, `hB`, and `hC` of the logical allocation bands, including their content and internal spacing but excluding the two inter-band gaps. `hA` includes brand/avatar and statistics; `hB` includes the Recent Catch heading, the internal Title→Hero gap, and Hero/Pager; `hC` includes CTA, camera, and existing Footer internals. These logical bands do not require new Compose containers.

Calculate the existing base gaps:
- `gAB_base`: the existing Adaptive V2.2 `stats_to_title` formula.
- `gBC_base`: the existing Adaptive V2.2 `card_to_footer` formula.

Calculate the positive unassigned surplus:

```text
S = max(0, H_safe - (hA + hB + hC + gAB_base + gBC_base))
```

When `S > 0`, allocate it as follows:

```text
gAB = gAB_base + S/2
gBC = gBC_base + S/2
```

The **1:1 equal split applies only to the additional surplus**, not to the final visible gaps. When `S > 0`, add exactly `S/2` to each of A→B and B→C after calculating their existing base gaps. The existing formulas remain the base-gap policy and retain their short-screen pressure/minimum behavior. Verify the added portions after subtracting base gaps; do not require the final visible gaps themselves to be equal.

When `S = 0`, do not add extra space. If the safe window is too short for the base composition, use the short-screen compression order in A.3; do not force the long-screen formula or hide overflow. This rule does not change the internal Title→Hero gap or its four explicit viewport targets. Do not add a separate bottom-reserve function; keep the existing Footer padding and safe-area rules.

### A.3 Short-screen priority

For 320×480dp at system font scale 1.0, prioritize a complete **no-scroll** composition and retain the two-row header.

1. Compress nonessential whitespace.
2. Compress A→B and B→C gaps within the existing short-screen constraints and minimums.
3. Resize Hero only within the existing 247:293 ratio, width/height limits, readable-content requirements, and the existing 124dp short-screen exception.
4. Preserve required text, the camera touch target, and the Footer safe-area boundary.

Do not hide required content, clip text, globally shrink fonts, reduce touch targets, overlap components, double-apply insets, or introduce a screenshot-specific layout. For larger font scales, retain the existing Adaptive V2.2 accessibility and local-scroll fallback rules. If the constrained safe window still cannot fit the required composition, report the appropriate overflow/conflict state rather than claiming PASS.

### A.4 Authority boundary and conflicts

This foundation retains the A/B/C group structure and sets the current long-screen surplus rule to an equal 1:1 split, superseding the former V1 2:1 allocation. Existing Adaptive V2.2 authorities remain binding for exact typography, Title→Hero targets, Hero ratio/width/height limits, Peek geometry, camera/touch geometry, safe areas, and runtime acceptance. The only added long-screen override is the final value of the A→B and B→C inter-group gaps when (S>0), with S/2 added to each gap.

If the foundation and another specific frozen rule cannot be satisfied together, record **SPEC_CONFLICT** and resolve the authority before implementation. Do not silently alter a frozen numeric target or claim PASS from a static image.
