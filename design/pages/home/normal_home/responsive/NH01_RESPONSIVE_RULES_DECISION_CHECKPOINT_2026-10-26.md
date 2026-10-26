# NH01 Responsive Rules — Decision Checkpoint (2026-10-26)

**Status:** `WORKING_BASELINE`  
**Freeze status:** `NOT_FROZEN`  
**Machine contract:** `responsive/NH01_RESPONSIVE_LAYOUT_V2.json` (proposed; sole numeric source for the structured browser preview)
**Android Runtime:** `NOT_VERIFIED`  
**Target workspace:** `#page/home_normal_v1/hifi/multi_screen_static_hifi`  
**Purpose:** Persist the current product decision to canonical `main` during development. This is a durable decision checkpoint, not a claim that the browser workspace or Android runtime already conforms.

## 1. Product decision

The five frozen M01–M05 design images already exist. They are visual anchors for hierarchy, component relationships, proportions, and overall balance—not five separate layouts to copy pixel-for-pixel. The implementation should remain recognizably close to the frozen designs while adapting continuously to actual available space.

Use one constraint-driven responsive policy. Do not create viewport-specific layout branches, copied screenshot coordinates, global font shrinking, hidden required content, fake neighboring records, double-applied system insets, or overlapping controls.

## 2. Immutable visual anchors and scope

- Keep the original `normal_home_v1.png` and M01–M05 source PNGs unchanged.
- M01: 320×480dp compact/short-screen anchor.
- M02: 360×640dp reference anchor.
- M03: 390×844dp long-phone anchor.
- M04: 430×932dp large long-phone anchor.
- M05: 480dp width boundary only; its native window height is unspecified.
- PNG canvas pixels, browser CSS pixels, device pixels, and logical dp are distinct coordinate spaces.
- The existing Design Manager route remains the only workspace; do not create a parallel preview shell.

## 3. Responsive inputs and safe area

The single policy must use actual safe width and height, system font scale, actual parent/track constraints, content/record state, measured text layout, asset aspect ratios, and the agreed inset owner. The background may be edge-to-edge; interactive content must stay inside safe bounds. Each system inset is consumed exactly once.

Center the content canvas and cap its width at 560dp. A viewport profile is a test input, never a key selecting a separate layout.

## 4. Logical page structure

Keep the existing physical Top / Middle / Footer structure. Use these logical allocation bands without introducing duplicate Compose containers:

- **A — Top information:** brand title, avatar, and three statistics.
- **B — Catch content:** Recent Catch heading and All action, selected Hero, and eligible real-neighbor Peek cards.
- **C — Bottom actions:** record-next-catch prompt and primary camera action.

The Recent Catch heading may be physically rendered in Top while logically belonging to B. Footer stays outside Middle scrolling.

## 5. Vertical allocation decision

Let `H_safe` be the usable content height after top and bottom insets have each been consumed once. Measure `hA`, `hB`, and `hC` as logical content-band heights excluding the two inter-band gaps. Retain the existing base-gap functions `gAB_base = stats_to_title` and `gBC_base = card_to_footer`.

```text
S = max(0, H_safe - (hA + hB + hC + gAB_base + gBC_base))
```

The approved allocation principle is a **1:1 split of positive surplus**:

```text
gAB = gAB_base + S/2
gBC = gBC_base + S/2
```

The added portions are equal; the final visible gaps need not be equal because their base values differ. The candidate formula `2S/3` for A→B and `S/3` for B→C is not accepted. Do not silently restore it.

The browser candidate's extra gap caps and outer-breathing behavior are rejected. The structured preview applies the full positive surplus through the two base gaps: each receives exactly `S/2`; there is no added-gap cap, outer-breathing reserve, or separate footer reserve. The machine contract and validator must keep this rule executable and test its actual added portions.

## 6. Hero and Neighbor geometry

### Selected Hero

- Preserve the declared `247:293` width-to-height ratio.
- Existing target-width formula: `min(min(trackWidthDp, 560dp) × 0.70, 337.2dp)`.
- Maximum Hero height: 400dp.
- Preserve readable content and the approved source-photo treatment.
- The existing 124dp short-screen exception is limited to exactly 320×480dp at system fontScale 1.0, with width at least 145dp, all required copy readable, and no Middle scrolling.
- `124dp` is the short-screen minimum-readable floor, not a forced final card height. The final Hero must also preserve 247:293, be at least 145dp wide, keep required copy readable, and have no Middle/Pager scroll. If those conditions cannot fit together after allowed whitespace compression, report `SPEC_CONFLICT`/`FAIL_CONSTRAINT`; do not relax any one condition.
- Under height pressure, compress nonessential spacing first. If the required Hero and content still cannot fit, use local Middle/Pager scrolling while keeping Footer outside it. Do not globally shrink the Hero or fonts to conceal overflow.

The normal readable-height range (148–220dp), ratio-derived target size, 400dp cap, and short-screen exception must be reconciled into one executable calculation before freeze. A constraint conflict must be reported, not silently overridden.

### Neighbor Peek

- Only real adjacent FishRecords may be shown; one record means one centered Hero and no fake neighbor or pager hint.
- Neighbor width is at most 82% of Hero width.
- Neighbor height is 66.7% of Hero height, with a 15dp top offset.
- Left/right reveal is symmetric; maximum geometric overlap is 1dp.
- Use a finite pager with stable IDs. No phantom or cloned records.
- The structured preview's Peek gap and visible-width formula must be sourced from the same machine contract as Hero geometry.

## 7. Typography, Footer, and touch safety

- Use semantic text roles with declared size bounds, line height, wrapping, and content priority.
- Apply system fontScale exactly once. Measure actual rendered line ranges; do not rely on a boolean overflow flag alone.
- Preserve species, positive measurement values/units, and time before optional location. Do not fabricate zero/unknown values or leave a lone final CJK glyph or dangling separator.
- Keep the CTA and camera in one Footer action group.
- `记录下一条鱼` is a static text label in the current interaction contract/runtime; it has no independent touch target. Only the camera action owns the >=72dp target in this group.
- Measure camera artwork separately from its touch rectangle. Camera touch target is at least 72dp; other interactive targets are at least 48×48dp.
- Keep Footer within the safe area and outside Middle scrolling. Never overlap CTA, camera, cards, or system navigation.

## 8. Short-screen priority and failure states

Resolve space pressure in this order:

1. Compress nonessential whitespace.
2. Compress only declared gaps down to their allowed floors.
3. Apply approved local wrapping/reflow.
4. Enable local Middle/Pager scrolling while preserving Hero ratio/readability and Footer safety.
5. If required content or controls still cannot fit, report `FAIL_CONSTRAINT` or `UNSUPPORTED_WINDOW`.

A scroll fallback is not an automatic visual pass. Do not hide required content, reduce touch targets, double-apply insets, or introduce a new layout mode without recording the decision.

## 9. Required validation

- Compare the five frozen anchors for hierarchy, proportions, card relationships, and visual balance—not pixel identity.
- Include non-anchor interpolation views, including 412×915dp and 375×720dp.
- Test system font scales 1.0×, 1.3×, and 1.5×.
- Probe responsive boundaries around 459/460dp, 499/500dp, 699/700dp, and 839/840dp where the corresponding continuous curves apply.
- Record exact source SHA, actual safe bounds/insets, font scale, component rectangles, text line ranges, overlap and touch checks, screenshots, and actual pass/fail results.
- Browser preview, frozen-image integrity, Android build, and Android runtime are separate evidence domains. Missing evidence stays `NOT_RUN`/`NOT_VERIFIED`.

## 10. Immediate next step

Align the existing PR #191 candidate and its structured workspace with this checkpoint: make the V2 machine contract the single numeric source, have the existing Design Manager route consume it directly, and remove the independent preview-rule values. Keep the contract proposed while the exact-source browser matrix is verified. Promote it to `SPEC_FROZEN` only after the contract, M01 short-screen behavior, browser matrix, boundary probes, and candidate deployment pass their registered gates.

The `P3-05` and `P3-06` task IDs remain assigned to their existing SWOS/Web scopes. NH01 responsive work uses `NH01-RESPONSIVE-CONTRACT-V2` / `G-NH01-RESPONSIVE-CONTRACT-V2` and `NH01-RESPONSIVE-PREVIEW-V2` / `G-NH01-RESPONSIVE-PREVIEW-V2`; do not repurpose the canonical P3 gates. Generated illustrative media is checked only for independent files, manifest status, stable geometry, and failure fallback; photo authenticity, real-catch provenance, and rights are outside this layout freeze.

This checkpoint records the current working decision on `main`; it does not claim that the implementation, browser preview, or Android runtime has already passed.
