> **SUPERSESSION NOTICE (2026-10-10):** HISTORICAL USER APPROVAL EVIDENCE. A1 font and A2 NH05 remain approved and are implemented in the new engineering binding. This historical STOP clause does not block unrelated compliant fixes. Current Work/Validation entry: [Normal Home Engineering Authority](../engineering/README.md). No historical evidence or original numbered rows below are deleted.

# Normal Home V2 — A/A Design Decision Record

**Decision status:** FONT_A_AND_NH05_A_APPROVED / WINDOW_ORIGIN_DESIGN_RESOLVED / CTA_CAMERA_RASTER_MEASUREMENT_PENDING

**Decision authority:** user, 2026-10-10

**Product scope:** Normal Home (real catch data state), not Empty Home or other screens.

**Frozen visual:** `design/system/core_visual_v1/reference/normal_home_v1.png`, 1080×1920 reference physical px, registry SHA256 `6ab9d3348b4a9a7e77ddca3a06235b4991798a309bd3512cc6fb9ea7aeb1d377`. Do not modify this PNG.

## Decision A1 — Font A approved: retain V1.1 frozen typography

Source: `Normal_Home_Typography_Scaling_Contract_V1.md` (V1.1).

| Normal Home text role | Reference physical font px | Reference physical line height px |
|---|---:|---:|
| Brand “渔见” | 72 | 80 |
| Statistics value | 36 | 42 |
| Statistics label | 28 | 32 |
| “最近鱼获” | 48 | 52 |
| “全部” | 40 | 44 |
| Hero species | 56 | 64 |
| Hero measurement | 46 | 52 |
| Hero time and location | 36 | 42 |
| “记录下一条鱼” | 40 | 44 |

The source contract's density conversion, minimum reference physical-font floors, line-height constraints, system fontScale, and page-specific font family/weight remain intact. The expected 1080 physical px / density3 / fontScale1 computed Android sizes include stat label 9.33sp and Hero metadata 12sp: **this is now a retained implementation requirement**, not authority to automatically enlarge fonts. Real raster visible glyph bounds and Compose `TextLayoutResult` still require independent comparison; this approval does not mark the current device typography PASS.

**Explicitly rejected:** B/C enlarged typography candidates; arbitrary multipliers, editing global typography, or silently raising the reference font sizes. If measured runtime glyphs differ from the Frozen optical reference, fix the underlying mapping, not the approved numeric font contract.

## Decision A2 — Long-screen A approved: keep NH05 single shared vertical offset

Source: `05_responsive_interaction/README.md`, NH05 V1 Frozen rule.

Define `S = usableContentWidthPhysicalPx / 1080`, `H_ref = 1920*S`, `E = max(0, usableHeightPhysicalPx - H_ref)`.

**Approved responsive rule:**
```
offsetY = min(E * 0.36, 180 * S)
runtimeY = frozenY * S + offsetY
```

- Apply the **same** offset to Header, Statistics, Recent header, Hero, CTA and Camera.
- Preserve original between-component gaps and visual hierarchy (apart from the common S scaling); do not independently stretch the gaps.
- Extra height not used by the capped offset remains Morning Lake environmental breathing room; do not stretch Hero/image/button/font to fill the screen.
- Resolve system insets exactly **once**. The formula above describes a common layout-space Y; the exact window-vs-content origin binding is still subject to a separate coordinate audit. Never add top safeDrawing padding and a second full-window origin translation to the same component without accounting for it. This decision does NOT approve the unverified full-window `E=H−1920*S` alternate formula previously proposed in the V2 draft.

**Numeric example with no safe insets:** W=1080,H=2340, S=1, H_ref=1920, E=420, offsetY=151.2 physical px.

| Component | Frozen Y px | NH05 long-screen Y px |
|---|---:|---:|
| Header | 104 | 255.2 |
| Statistics | 304 | 455.2 |
| Recent header | 494 | 645.2 |
| Hero | 596 | 747.2 |
| CTA (code/V1.1 candidate) | 1495 | 1646.2 |
| Camera (code/V1.1 candidate) | 1564 | 1715.2 |

The CTA and Camera baseline Y values above are only one **unresolved baseline variant**, not a new authority. NH05 README gives CTA y1504 / Camera y1582. Resolve these from actual Frozen raster bounds before implementation.

**Hypothetical insets example (not measured phone):** W=1080,H=2340, L=R=0,T=80,B=90. With usable height 2170, E=250 and offsetY=90px. Record window/content coordinate origin and actual component measured bounds before declaring window-space Y values.

**Explicitly rejected:** the V2 candidate seven-bucket extra-height allocation `+80/+60/+60/+35/+35/+25/+125` (420px total). This is a design comparison artifact only, never a target to implement or test.

## Non-approval items (must not be silently inferred)

1. **CTA/Camera baseline conflict:** R1 NH05 y1504 / y1582 vs R2 V1.1 and code y1495 / y1564. Measure visual, container, text and touch rectangles directly from canonical Frozen image. Do not pick either version until grounded and reviewed.
2. **Actual insets + coordinate origin:** design-level interpretation is now resolved in `Normal_Home_V2_Coordinate_Origin_And_Anchor_Audit_20261010.md`: physical edge-to-edge window origin, `E=max(0,(H-T-B)-1920S)` for approved NH05, no extra `+T` in frozen Y placement. Real-device inset and `boundsInWindow` measurements must still confirm the implemented mapping before runtime PASS.
3. **Brand glyph bounds:** integrated Runtime Run 37915120879 / artifact 11608748556 recorded ≈3px beyond Header for brand text. Repair the true glyph/container geometry without shrinking approved 72px font or removing genuine overflow assertions.
4. **Short-screen fallback:** no new scroll/flex or touch compromise authorized here; prepare evidence if the unchanged Frozen-to-device mapping can't satisfy accessibility and safe-area constraints.
5. **Font raster acceptance tolerances:** not approved here; report measured discrepancies, don't invent allowable errors.
6. **Mockup authority:** the two generated A/B review boards are visual decision aids, NOT pixel-perfect YuJian Frozen screens, native Android screenshots, or a new source of app labels, navigation, background, fish photos and widgets. Approval of “A” applies only to typography and long-screen layout policies recorded above.

## Implementation gate

The two chosen design policies are now **APPROVED**. The overall V2 coordinate contract is **PARTIALLY_APPROVED / CTA_CAMERA_RASTER_EVIDENCE_PENDING**. Window coordinate/inset design interpretation has a documented proposed resolution; runtime proof and final CTA/Camera frozen-image measurements are still required.

Work A can prepare a mapping audit and Frozen measurement report. Do **not** treat this as permission to pick unresolved CTA/Camera coordinates, change Frozen PNG, adjust other pages, relax tests, merge main, or declare Visual PASS.

Next required action: obtain and review checksum-verified Frozen CTA/Camera pixel evidence, confirm the single-origin mapping in runtime measurements, then implement the smallest Normal Home mapping fix and run true-device visual parity tests. Each correction must retain the exact A1/A2 numbers and report target-vs-actual delta.
