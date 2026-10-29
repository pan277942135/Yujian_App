# Normal Home Responsive Contract V1

**Status:** `READY_FOR_VISUAL_IMPLEMENTATION`  
**Authority:** `design/pages/home/normal_home/engineering/Layout_Responsive_Contract_V1.md`, `Short_Window_Adaptive_Freeze_V1.md`, `Normal_Home_V2_Coordinate_Origin_And_Anchor_Audit_20261010.md` (historical where explicitly superseded), and Empty Home V2.2 `responsive_mapping_contract.json`.

## Test profiles

- Canonical screenshot: 1080×1920 px, 360×640 dp at density 3.0, portrait, fontScale 1.0.
- Shared reference widths: 360, 393, 412 and 480 dp; include 320 dp compact and certified 320×480 dp short-window fixture.
- Long screen: 2340 px and taller, or measured safe viewport with positive excess height. The 1080×2340, zero-inset arithmetic fixture has `delta=151.2px`; it is synthetic unless captured from a native device.
- FontScale: 1.0, 1.3 and 1.5. 1.5 is an extended accessibility stress point, not a claim that the existing short-window certification extends beyond 1.3.

## Normal Home coordinate transform

Measure physical app-window `W,H`, safeDrawing insets `L,T,R,B`, local density `d`, and `fontScale` from the same app window. Let:

```text
Wsafe = max(0, W - L - R)
Hsafe = max(0, H - T - B)
S = Wsafe / 1080
E = max(0, Hsafe - 1920*S)
delta = min(0.36*E, 180*S)
windowX = L + frozenX*S
windowY = frozenY*S + delta        // no extra +T
windowWidth = frozenWidth*S
windowHeight = frozenHeight*S
```

Apply one common `delta` to Header, Statistics, Recent Catch, Hero, CTA and Camera. Insets affect usable bounds and `Hsafe` once; they do not create another Y origin. Preserve Normal Home's Morning Lake image camera view, use centered crop to fill the window, and apply no added tint, saturation, contrast, brightness or blur operation.

## Empty Home transform

Empty Home is a sibling screen with its own frozen V2.2 source. Use its approved `responsive_mapping_contract.json`: scene-locked elements share a uniform cover transform on the edge-to-edge scene canvas; safe-top UI and safe-bottom CTA group use safeDrawing bounds exactly once; Hero art uses aspect-preserving FIT. Do not copy the Normal Home common-delta formula onto Empty Home, and do not substitute the Morning Lake Normal Home master for the Empty Home scene.

## Fixed, flexible and scroll behavior

- **Fixed/page anchored:** Normal Home Header, Statistics, Recent Catch, Hero, CTA and Camera share the registered width/height mapping in `NORMAL_FIXED` mode. Empty Home top controls and bottom CTA group follow its separate responsive contract.
- **Flexible:** environmental background covers the viewport; only unused environment area may grow on tall screens. Hero media content fits/crops inside the frozen card frame under the EvidenceFit rules; outer card does not stretch.
- **Scrollable:** Normal Home content becomes vertically reachable only in the registered `SAFE_OVERFLOW` exception; user-driven Pager remains horizontal and must not seize vertical scroll.
- **Adaptive:** Home state selects empty/single/multiple frozen composition; responsive width variants follow the registered profile and source geometry. No new visual variant is created.
- **Hidden:** no required Home element is hidden to create fit. Optional missing metadata is omitted only by NH04 data rules.

## SAFE_OVERFLOW trigger and response

Enter SAFE_OVERFLOW only when measured NORMAL_FIXED bounds overlap, required text clips, a control leaves the safe window, content becomes unreachable, or the primary action cannot be operated. Preserve all content, statistics, required fontScale and Hero. Keep Header → Statistics → Recent → Hero in a bounded vertical scroll area; keep the CTA and Camera together in a safe bottom action group. Require a reachable content viewport of at least 160 dp and an action target of at least 48×48 dp. If these constraints cannot both fit, report `UNSUPPORTED_WINDOW / FAIL_CONSTRAINT`, with measured bounds; do not claim PASS or introduce a new design.

No global shrink, hidden statistics, deleted Hero, new fixed overlay, or extra page chrome is allowed. 1080×2340 and taller supported normal-height phones must retain `NORMAL_FIXED` whenever all registered checks fit; height alone does not trigger SAFE_OVERFLOW.

## IME, orientation and evidence

Normal Home has no Home-owned text-input flow; keyboard-visible evidence is required only if an accessibility/system IME is actually open, and Home must keep all interactive targets safe. Empty Home uses its own safe-bottom action-group rule. Record orientation, multiwindow/window bounds, full uncropped output, actual insets, density, fontScale, NORMAL_FIXED/SAFE_OVERFLOW mode and source SHA. Landscape and a window outside the certified envelope are reported as unverified profiles; do not relabel them as ordinary PASS.
