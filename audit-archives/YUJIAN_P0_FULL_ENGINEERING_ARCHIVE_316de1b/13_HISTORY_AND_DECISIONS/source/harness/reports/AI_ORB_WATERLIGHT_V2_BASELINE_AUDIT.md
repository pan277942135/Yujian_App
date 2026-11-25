# AI Orb × WaterLight V2 baseline audit

Baseline Main: `afd9b5814da327a84cd41181acc08624fb5583ad`

PR #288 was merged as `eab269580026808bc6a9409dba8c908e7b7ff67d` before this continuation.

## Existing capabilities reused

| Capability | Existing implementation | Baseline finding |
|---|---|---|
| Recognition business timeline | `RecognitionVisualStateController.kt`, `RecognizingScreen.kt` | Existing adaptive CAPTURED/DETECTING/OUTLINE/CLASSIFYING/RESULT flow owns model readiness and trusted-mask timing. Preserve it. |
| WaterLight frame and identity | `WaterLightFrame.kt`, `WaterLightHost.kt` | Frame already carries elapsed/reference time, PhotoSession identity, content version, and geometry version. It still exposes RecognitionPhase to the shared rendering frame. |
| Reveal timeline | `reveal/RecognitionRevealController.kt` | `RecognitionRevealClock.evaluate` is a pure 850ms projection; the Result screen supplies its elapsed time. |
| Perimeter and fish contour | `RecognitionPerimeterShaderOverlay.kt`, `RecognitionFishFocus.kt` | Existing renderers are reusable; no 13-stage shared projection existed at this baseline. |
| RR09 name rendering | `reveal/RecognitionRevealOpticalRenderer.kt`, `SpeciesNameReveal.kt` | Existing real result/Glyph path remains authoritative. |
| Material roles | `WaterLightMaterialProfile.kt` | Host profiles exist, with non-ORB numeric values explicitly treated as candidates. |

## Gaps confirmed before this continuation

- Renderers received a shared WaterLightFrame, but there was no single deterministic 13-stage optical projection.
- WaterLightEnergy did not represent the complete ORB → photo → fish → original Orb → Glyph → residue sequence, and events omitted image/content identity.
- Processing Orb entrance and photo morph each had their own frame-driving loop in `RecognizingScreen.kt`; this continuation must preserve their 900ms/700ms parallel timing while consolidating stage projection on the existing recognition clock.
- `RecognitionRevealClock` already owns the frozen RR09 850ms curve; it should be consumed, not duplicated.
- No runtime evidence from API28 or a physical device was available at the baseline. CI must not be reported as visual acceptance.

## Scope of this increment

Add a stateless `RecognitionSceneCoordinator.frameAt(...)` projection from the existing optical reference timestamp, expose its identity-bearing frame from `WaterLightFrame`, and route WaterLightEnergy through the 13-stage boundaries. This increment does not claim that the path renderers, Orb return animation, Glyph deformation, or Debug stage controls are complete; those remain follow-up implementation items.
