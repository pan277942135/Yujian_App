# Responsive Rules V3.1 Final

## Viewports

Use 360dp, 393dp, 412dp and 480dp as baseline widths. Record actual logical content width after host insets. The 1080×1920 design canvas remains source pixels and must not be treated as dp.

## Coordinate stack

Screen → Window Insets → Content Root → Component Layout. The host owns dynamic status/cutout, navigation/gesture and IME insets. Consume each once; declare one origin. No second full-window offset or screenshot-derived spacer.

## Slot classification

Every page contract assigns Fixed, Flexible, Scrollable, Adaptive or Hidden to each major slot. Fixed preserves a source slot; Flexible grows within constraints; Scrollable scrolls before compressing; Adaptive grows/reflows in source order; Hidden has no visible or interactive target.

## Text and IME

Layout uses dp; text uses sp. Honor fontScale, including 1.0 / 1.15 / 1.3 review points; permit container growth and scrolling. At IME open, recalculate the usable viewport and keep focused input plus primary action reachable.

## Media

Every slot declares Fill, Fit, Cover, Contain, Evidence Fit, Letterbox or Blur Background and identifies its crop authority. Preserve aspect ratio. Evidence Fit shows the full source; Cover crops only inside a registered safe rectangle; Blur Background reuses that same source behind Fit. Never stretch or replace media.

## Historical review rules

- Normal Home: NH05 approved whole-page offset; one window origin; apply insets once.
- Recognition Result: state-specific Hero/Glass geometry; content-height plus scroll; no glass stretching.
- FishRecordDetail: approved safe Fill or Safe Fit and same-source ambient.
- Login: dynamic IME insets with resize/scroll.

These rules freeze design inputs only. The page anchors, Results states and Login focus flow remain page-contract/runtime acceptance items; no code or device run was performed.