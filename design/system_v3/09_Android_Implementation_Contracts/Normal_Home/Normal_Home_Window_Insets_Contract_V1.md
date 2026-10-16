# Normal Home Window Insets Contract V1

**Status:** FROZEN_IMPLEMENTATION_GUIDANCE  
**Visual authority:** Normal_Home_Visual_Contract_V1.md  
**Insets authority:** Normal_Home_Responsive_Contract_V1.md and Normal_Home_Pixel_Targets_V1.json

## Coordinate System

- Window Coordinate (0,0) is the top-left of the full app window.
- Measure W,H,L,T,R,B,d,fontScale from that same window.
- Content Coordinate is a named measurement space, not a second origin that may reapply the insets.
- The page transform maps frozen source coordinates to the window. Normal Home uses:
  - Wsafe = max(0, W - L - R)
  - Hsafe = max(0, H - T - B)
  - S = Wsafe / 1080
  - E = max(0, Hsafe - 1920*S)
  - delta = min(0.36*E, 180*S)
  - windowX = L + frozenX*S
  - windowY = frozenY*S + delta (do not add T again)
  - Width/height = frozen source dimension × S
- Use the same transform and common delta for Header, Statistics, Recent Catch, Hero, CTA and Camera.
- Background may draw edge-to-edge. Interactive content and hit targets must remain within measured safe bounds required by Visual/Accessibility Contracts.

## Edge-to-Edge and Single Consumption

- Exactly one host/root owns reading and applying system Window Insets.
- Consume or pass measured inset values once. A scaffold/root that already applies safe drawing padding must not be followed by another page-level safe-padding pass.
- Do not combine automatic inset padding with a second manual L/T/R/B offset.
- Keep status bar, navigation bar, display cutout, and IME measurements distinct in evidence.
- Empty Home uses its own safe-top UI, safe-bottom CTA group and uniform-cover scene mapping. Do not apply the Normal Home transform to that sibling screen.

## Keyboard / IME

Normal Home has no page-owned text input. The baseline Home profile records IME hidden. If an IME is actually visible because of a system/accessibility flow, record its measured window inset and keep Home targets safe; do not invent a Home keyboard layout or subtract IME twice.

## Short Screen

Enter implementation mode SHORT_SCREEN_MODE exactly when a measured NORMAL_FIXED layout causes one or more of:

- Required content overlap.
- Required text clipping.
- A required content region is unreachable.
- The primary CTA or another required action is outside the usable safe window or cannot be operated.

SHORT_SCREEN_MODE is an Android binding name for the existing Visual Contract SAFE_OVERFLOW exception. It is not a new page state or a new layout design.

Response:
- Keep Header → Statistics → Recent Catch → selected Hero reachable in registered order.
- Keep CTA and Camera as one safe-bottom action group.
- Preserve all required content, fontScale, Hero frame and component geometry.
- Maintain at least 160dp reachable content viewport and 48×48dp action targets.
- If both constraints cannot fit, emit UNSUPPORTED_WINDOW / FAIL_CONSTRAINT with measured bounds. Do not report PASS.

Do not trigger short mode from height alone when NORMAL_FIXED fits. Do not globally shrink content, hide statistics, remove Hero, or introduce a second fixed overlay.
