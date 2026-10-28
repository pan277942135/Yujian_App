# Normal Home — Short Window / Large Text / Multiwindow SAFE_OVERFLOW Freeze V1
Status: FROZEN_ENGINEERING_EXCEPTION_CONTRACT / NO_NEW_HIFI / RUNTIME_UNVERIFIED
Scope: only when Normal Home cannot fit the NORMAL_FIXED geometry safely in the measured window. Main NH01/NH02 9:16–21:9 visuals stay Frozen.

## Supported target envelope and deterministic trigger

All geometry uses measured app-window `W,H,L,T,R,B,d,fontScale` and P0 layout contract. The smallest **certified responsive target** is `Wsafe/d >= 320dp` AND `Hsafe/d >= 480dp` (usable safe window, not raw display) with accessibility fontScale up to **1.30**. This is the minimum acceptance *test envelope*, not permission to arbitrarily disable a smaller usable OS window.

On every composition/profile, layout first computes the NORMAL_FIXED mapped component rectangles **and** measured text and 48dp hit rectangles. Use NORMAL_FIXED iff all the following are true:
- Header, Statistics, Recent, Hero, CTA, Capture have non-overlapping mandatory regions and are reachable;
- glyph outlines and text layout do not clip after system fontScale;
- clickable hit rectangles lie inside same-window safe inset bounds; minimum 48x48dp for each action; and
- no decorative drawing overlaps another component's interaction target.

Otherwise enter SAFE_OVERFLOW. This is a measured branch, **not** a new aspect-ratio design: 1080x2340 MUST use normal NH05 A if it meets constraints.

When the safe-window size drops below certified 320x480dp, still attempt the same rules where feasible. The evidence status is `UNSUPPORTED_WINDOW` only if the viewport cannot fit both the 48dp capture hit target and a MIN 160dp reachable content viewport while respecting safe insets and required action spacing. Do not fail solely because the device width is <320dp; report `OUTSIDE_CERTIFIED_ENVELOPE` and separate runtime support status.

## Frozen SAFE_OVERFLOW composition

Edge-to-edge Morning Lake background is unchanged; all widths and typography follow NH05/A1 mapping. The screen is split into TWO presentation zones only:

A. **Bounded vertically scrolling content**: Header → Statistics → 最近鱼获/全部 → selected HOME Hero, in existing order. Left/right margins, card width 740S, card height 880S and 32S radius remain unchanged. The page may scroll to reveal the end of Hero. Keep top padding >= safe top; reserve bottom scroll padding >= 16dp under Hero so scrolling to the end cannot hide footer behind action group.
B. **Bottom safe action dock**: CTA "记录下一条鱼" immediately above the shared Capture button; pinned to safe bottom with **>=16dp inner bottom clearance**, horizontally centered in *safe horizontal span*. Preserve width-scaled CTA visual/font and Capture visual; reserve a 48dp-minimum accessible tap target independently from visible size and do not overlap the scroll touch area. CTA→Camera layout gap equals same relative relationship in standard reference (based on container and touch frames) scaled by S; no custom elastic gap. Background behind pinned action group is naturally the existing lake—no new opaque toolbar, gradient HUD or sheet.

Implementation must use one vertical-scroll container and one independent pinned action layer; only real user scroll moves content. Captured image pager still supports horizontal user drag; gesture direction arbitration must not seize vertical scroll. No fake adjacent card for one-record state. Do not hide statistics or rename labels. No sticky header or new navigation. Preserve user fontScale and 1-line elision for optional Hero text; header brand should have sufficient measured intrinsic space.

To avoid fake compatibility, for every device report root and safe window rect, mode, action dock bounds, available scroll viewport height, first/last reachable regions, 48dp targets, text clip/overlap; if scroll viewport <160dp or the capture target cannot fit, record `UNSUPPORTED_WINDOW/FAIL_CONSTRAINT`, never visual PASS.

## Test fixture profiles

- 360x640dp, d3, fontScale1.0: NORMAL_FIXED if measured constraints allow, otherwise SAFE_OVERFLOW.
- 320x480dp, d2 and d3, fontScale1.0/1.3: minimum certified target; assert scroll reaches full Hero footer and actions remain visible.
- 360x480dp, fontScale1.3: forced crowded fixture; assert SAFE_OVERFLOW and no clipping.
- 393x852dp, fontScale1.0: tall normal-mode reference under actual measured insets.
- 320x400dp and multiwindow: outside certified height; use feasibility calculation, not automatic false PASS.
- 21:9 long phone: must not choose SAFE_OVERFLOW merely because it is tall.

No audio, pager auto-advance, celebratory state, altered avatar, captured image crop redesign, or alternate theme. Captured status `SAFE_OVERFLOW` is an engineered exceptional rendering, not a newly generated independent frozen NH01/02 replacement.
