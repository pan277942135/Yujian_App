# Geometry System V3.1 Final

**System status: READY for shared design rules.** C-008 remains open for page-level contract/runtime evidence; this status does not claim a device or Android acceptance run.

## Reference devices and coordinate space

Reference widths are 360dp, 393dp, 412dp and 480dp. The 1080×1920 canvas is a design reference in physical source pixels, not a device viewport.

Coordinate ownership is Screen → Window Insets → Content Root → Component Layout. The host supplies status bar/cutout, navigation/gesture and IME insets. Apply each once. A page declares edge-to-edge/inset content and one origin. Component layout uses dp; text uses sp; source raster px converts only through its explicit page authority.

## Token scales

Shared spacing is 4, 8, 12, 16, 20, 24, 32, 40, 48 and 64dp. Shared radii are 8, 12, 16, 24 and 32dp plus Circle. Minimum action target remains 44×44dp; Back Title minimum bar is 56dp.

Conflicts are recorded, not normalized away: Core Visual V1 spacing/radius values are source reference px with page conversion formulas. The frozen Primary Button has a 28dp radius, so geometry registers a component-only exception. These rules do not edit existing Frozen visuals.

## Layout classes

- Fixed: keep a registered Frozen slot; sticky behavior must be explicit.
- Flexible: grow only inside page constraints.
- Scrollable: scroll dense content before compressing facts or controls.
- Adaptive: grow/reflow while retaining meaning and order.
- Hidden: remove both visual and interactive affordances for the declared state.

## Media

Fill, Fit, Cover, Contain, Evidence Fit, Letterbox and Blur Background are defined in Geometry_Tokens_V3_FINAL.json. Every media slot declares one mode and its crop authority. Preserve aspect ratio and source bytes; evidence is never cropped. Blur Background uses only the same source.

## Historical issue rules

- Normal Home: use the approved NH05 offset formula, one window origin and one inset pass. Runtime anchor measurement remains a page contract item.
- Recognition Result: state-specific Hero/Glass geometry sizes to content and scrolls; never stretch the glass.
- FishRecordDetail: safe Fill or same-source Safe Fit plus low-salience ambient; no arbitrary crop.
- Login: dynamic IME inset plus resize/scroll keeps the focused field and primary action visible.

## Acceptance

A design contract passes when it names a reference width, safe-area origin, one responsive class per layout slot, fontScale behavior, and a registered media mode. The documented rules are ready to reference; page-level measurements/runtime remain separate.

## Video aspect policy

`geometry.video.aspect_policy` preserves the oriented source aspect ratio unless a page authority supplies a frame ratio. Video uses contain plus registered letterbox; it is never stretched or cropped. This token defines source/frame fitting only, not player controls or page playback strategy.
