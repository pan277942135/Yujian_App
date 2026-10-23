# NH01/NH02 · Gemini Native V3 / Adaptive V2 — 统一现行规范 V1

Contract ID: NH01_GEMINI_ADAPTIVE_V2_UNIFIED_V1
Status: SPEC_FROZEN; Android Runtime: NOT_VERIFIED
Scope: only populated NH01/NH02 routed through GeminiNormalHomeContent; not Empty Home, shared Profile, or legacy NormalHomeContent.

## 1. 规范权威和版本替代

| Field | Current NH01 Gemini route | Retained legacy authority |
| --- | --- | --- |
| Page IA, state, navigation | NH01/NH02 feature and NH03 state; current Gemini Top/Middle/Footer | Original NH01 1080x1920 and NH02 immutable reference images |
| Layout, geometry, fonts, page gaps | Adaptive V2.2, as defined here and in design/qa/NH01_ADAPTIVE_VISUAL_BALANCE_V2.md | NH05 historical A1/A2 frozen coordinates, valid on legacy NormalHomeContent only |
| Real catch metadata | NH04 identity/omission plus V2.2 anti-orphan override below | NH04 single-line specimen remains for legacy route |
| Lake background | NH06 immutable Morning_Lake_Master source, Gemini viewport TopCenter Crop | NH06 legacy center-Crop viewport rule, not current Gemini crop |
| Header avatar | NH01_Avatar_Visual_Contract_V1_1_FROZEN.md, mist silhouette | NH07 fisherman bitmap and V2 default asset retained byte-identically; not current Gemini fallback |
| Auth and interaction | Guest -> Login; signed-in -> Profile; preserve avatar URL/token isolation | Guest identity != signed-in, even if same line illustration |
| Hero, Pager | True FishRecord photo, safe crop/Fit same-source backdrop, finite stable-ID Pager | No fake fish, no cloned pages, no 1-record neighbors |
| Motion/feedback | Original subtle 6000ms Hero idle, manual pager, shared capture motion, Reduce Motion; no page audio | Legacy frozen motion, haptic and sound tokens remain applicable |
| Acceptance | New 20 current route gates + 24 native profile runs + original 46 G/V/S/M/F domains | Old fixed pixel-geometry rows are historical comparison, not automatically PASS |

Only the named field is superseded on the named route. All Frozen pixels and asset hashes stay intact. Gemini is an intentional new Compose layout and does not claim 1080x1920 Frozen Pixel Parity; original product data, navigation, accessibility and safety rules remain binding. Static or design SPEC_FROZEN does not imply runtime PASS.


## 1.1 NH01 Responsive Foundation V1 — additive frozen principle

The structural and surplus-allocation foundation is frozen in `design/pages/home/normal_home/08_multi_screen_static_hifi/NH01_MULTI_SCREEN_RESPONSIVE_CONTRACT_V1.md`, Appendix A, and its machine-readable `foundation_layout_principles` entry. Design Manager remains the single entry point.

- **A:** brand/avatar + three statistics (statistics remain a second row at 320×480dp).
- **B:** Recent Catch heading/All action + selected Hero + eligible real-neighbor Peeks.
- **C:** record-next-catch CTA + camera action.
- Measure the usable content height after applying system insets once. Let hA/hB/hC include fixed content and internal spacing, excluding the two inter-group gaps. Use the existing `stats_to_title` and `card_to_footer` formulas as base gaps, gAB_base and gBC_base.
- Calculate `S = max(0, H_safe - (hA + hB + hC + gAB_base + gBC_base))`. If S is positive, set `gAB = gAB_base + 2S/3` and `gBC = gBC_base + S/3`. The 2:1 ratio applies to added surplus, not final visible gaps. This is the explicit long-screen override to the final values of those two inter-group gaps; their formulas remain the base-gap policy.
- This does **not** alter the internal Title→Hero gap or its four explicit viewport targets, the Hero 247:293 ratio/size limits, typography roles, Peek geometry, camera/touch sizes, safe-area handling, or runtime acceptance. No separate bottom-reserve function is introduced.
- For 320×480dp at system fontScale=1.0, prioritize no scroll and retain the two-row header; compress nonessential whitespace and group gaps within existing short-screen constraints before resizing Hero within the existing exception.
- If a specific frozen constraint cannot be jointly satisfied, mark `SPEC_CONFLICT`; do not silently override it or claim runtime PASS.
## 2. Section Spacing V2.2 (new explicit visual targets)

Measure the rendered 最近鱼获 Text layout bottom to the actual selected Hero card outer top, via native boundsInWindow; NOT section Row bottom or center of the Pager.

| Native logical viewport profile | Visible gap at system fontScale=1.0 |
| --- | --- |
| 320x480dp | 8dp ±2 |
| 360x640dp | 12dp ±2 |
| 390x844dp | 18dp ±2 |
| 430x932dp | 20dp ±2 |

At system 1.3x/1.5x, compress nonessential spacing before shrinking readable content, retain >=8dp where feasible, otherwise accessible local Middle scroll; no text/CTA collisions. Record six intervals Header-Stats, Stats-Title, Title-Card, Card-CTA, CTA-Camera, Camera-BottomSafe. Do NOT pass a test merely because measured gap equals its own old layout policy (7-12dp). Environmental whitespace on long screens is legitimate; unbalanced tightly stacked Title/Hero is not.

## 3. Adaptive Hero/Peek / short viewport

247:293 card ratio, target width min(min(trackWidth,560dp)*0.70,337.2dp), height cap 400dp, normal readable-height floor 148-220dp. 124dp exception ONLY at 320x480dp, fontScale exactly 1.0 and only when card width >=145dp, required text readable and Middle does not scroll. Peek real neighbor: width <=82% main, height 66.7%, top +15dp, no overlap beyond 1dp, no infinite wrap or phantom NH02 side card. Footer outside Middle scroll, camera target >=72dp, all other actions >=48dp, no camera/CTA collision and safe inset applied once. At fixed font scale, larger width/height must not reduce key sp roles or hero width.

## 4. NH04 metadata priority and one-character orphan rule

Actual sanitized species first; positive measurement only; time outranks optional location. Omit missing rows and separators; never fake zero/unknown. Species normally one line, may use max 2 if card<240dp or system fontScale>=1.3; measurements preserve values and units. Location should be succinct on card; full value remains in FishRecordDetail. Standard metadata prefer one line, max two if legible; optional location is ellipsized/shortened before time is lost. NEVER leave a lone final CJK glyph (e.g. 号) or dangling punctuation. hasVisualOverflow=false alone is insufficient; test actual TextLayoutResult line ranges and native screenshots.

## 5. NH06 / NH07 / photo / motion

Exact immutable Morning Lake master SHA256 5fba741088ea186e898cd3bee5777e35978436f427492e6e6122528ef6aa91d7. Current Gemini viewport TopCenter/Crop with no extra recolor, global blur or screenshot-cropped master. Current avatar V1.1: 36-40dp thin mist glass, >=48dp touch, head/shoulder line, no glossy ring. Guest and signed-in missing photo may share ARTWORK but not account state, accessible label, click destination or user photo data; old NH07/V2 assets remain stored unchanged. Fish source remains real; use trusted BBox safety or full-photo same-source soft backdrop with no hard rectangular front-image seam. Selected Hero idle only when resumed and motion allowed: 6000ms scale 1.000->1.008->1.000, Y 0->-2dp->0; no auto-page, no new haptic except shared Capture, no ambient audio.

## 6. Independent admission

Acceptance: design/pages/home/normal_home/engineering/NH01_GEMINI_ADAPTIVE_V2_ACCEPTANCE_V1.md. Machine source: design/pages/home/normal_home/engineering/NH01_GEMINI_ADAPTIVE_V2_ACCEPTANCE_MATRIX_V1.json. Same exact git SHA, V2/V3 4 viewports x 3 fonts x 2 variants = 24 Android API28 executions plus 20 scoped cases and applicable 46 historical behavior/motion/photo domains. Missing or unverified evidence is NOT_RUN/REVIEW_REQUIRED, never PASS. Do not merge main or claim release approval.
