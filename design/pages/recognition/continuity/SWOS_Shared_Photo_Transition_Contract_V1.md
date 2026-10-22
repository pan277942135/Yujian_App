# SWOS Shared Photo Transition Contract V1

**Contract ID:** `SWOS_SHARED_PHOTO_TRANSITION_V1`
**Design status:** `FROZEN_DESIGN_DIRECTION`
**Adoption status:** `DRAFT_DEPENDENT_ON_PR_186`
**Machine authority:** [SWOS_Shared_Photo_Transition_Contract_V1.json](SWOS_Shared_Photo_Transition_Contract_V1.json)

## Purpose

Freeze the observable photo behavior from Processing P7 through Result R0, R0.5, and R1. The same original photograph, fish geometry, gold source, orientation normalization, and transform chain continue across both pages. R0.5 is a static sample at 100ms; it does not add a wait.

## Required identity and geometry data

The transition record carries:

- source photo ID and SHA-256;
- normalized orientation and original dimensions;
- Processing viewport and actual oriented photo-content rectangle;
- Result Hero rectangle and media transform;
- fish BBox/contour plus an explicit geometry-trust state;
- recognition-session identity and elapsed transition time;
- gold-trace state.

The fixture values and exact rectangles are in the machine contract. The source image is the 1152×1536 `208758.jpg` SHA recorded there.

## Shared transform

For the 360×640 reference viewport:

- Processing source foreground: **(0, 80, 360, 480)** dp.
- R0: same visible source rectangle and the same source identity.
- R0.5 at **100ms / global 2900ms**:
  - outer photo container: **(9.5, 26.5, 341, 425)** dp;
  - source-photo foreground: **(50.625, 66.5, 258.75, 345)** dp.
- R1 at **200ms / global 3000ms**:
  - Result Hero container: **(19, 53, 322, 210)** dp;
  - complete source foreground: **(101.25, 53, 157.5, 210)** dp.

The container bounds interpolate between the Processing viewport and the Result Hero. The source foreground uses a uniform scale and interpolated center, preserving its 3:4 aspect ratio. The R0.5 rectangle is a real in-between state, not an opacity blend or a second crop.

## Media policy

Use the existing Recognition Result Hero Media Contract. A trusted BBox may drive its existing safe-crop procedure. This fixture has no verified detector BBox, so it uses the complete oriented original as foreground and a low-contrast blurred cover derived from the same source. Do not stretch, outpaint, synthesize, switch photos, crop off the fish, or introduce a crop jump near R1.

The photo, SWOS optical layers, fish contour, and Gold Core/Soft Glow/Halo use the same source-photo transform. Gold must not remain at old screen coordinates or be redrawn in a second coordinate space.

## Fixture geometry trust

The sample BBox and contour are manually traced reference artwork. The head boundary is partly obscured by the holder's hand. The state is `MANUAL_REFERENCE_ONLY_NOT_PRODUCTION_MASK`; it is never reported as detector output and cannot unlock production fish highlighting. If trusted geometry is absent in runtime, omit the contour-following gold trace and follow the Phase 0 fallback.

## Result and timing

At R0, Result begins taking over on the first presented Result frame. Do not hide the Processing picture before showing the Result picture; do not insert navigation delay. R0.5 shows the same photo visibly shrinking while Result navigation/background/form layers enter. R1 lands in the frozen HIGH Hero. The existing RR09 gold transfer starts after R1, at relative 200ms. The full sum stays **2800ms + 850ms = 3650ms**.
