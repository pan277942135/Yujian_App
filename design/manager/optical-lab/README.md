# Recognition Optical Lab · Web Review V1

This is a browser review surface based on the existing P1–P5 contract. Candidate rendering is explicitly Web Review only; it does not change P1–P5 or PR #165 Frozen PNGs and does not claim Android Runtime completion.

## Canonical preview

- Acceptance entry: <https://pan277942135.github.io/Yujian_App/design/manager/nh01_acceptance/edgeglow-fish-static/>
- Integrated static + dynamic review: <https://pan277942135.github.io/Yujian_App/design/manager/nh01_acceptance/edgeglow-fish-static/optical-review/>
- PR #166: <https://github.com/pan277942135/Yujian_App/pull/166>

## Receiving Field correction

The earlier automation compared pixels with the Receiving Light layer off and on. That proved the renderer changed pixels; it did not prove that a person could perceive a light field around the photo. In the actual cloud capture, the change read mostly as a faint blue/gold tint across the photo, while the edge still looked bare. The pixel-difference threshold was therefore too weak to support a visual claim.

The new candidates add a separate transparent, browser-only Receiving Field layer around the registered 3:4 photo area. Broad corner blooms establish the field first; four blurred, incomplete edge ribbons supply direction after it. Upper-right blue is strongest, upper-left gold is softer, lower-left gold is a weak residue, and lower-right blue is a short trace. Their independent phases make the field breathe unevenly in dynamic Preview. The existing Canvas paths remain visible beneath the candidate field, and Fish Focus remains a separate warm-gold layer.

| Candidate | Review layer gain | Character |
| --- | ---: | --- |
| `REVIEW_CANDIDATE_A` · Soft Field | 0.54 | Broad, low-contrast field; restrained edge cues |
| `REVIEW_CANDIDATE_B` · Balanced Field | 0.92 | Clearer blue/gold field with uneven corner hierarchy; start here |
| `REVIEW_CANDIDATE_C` · Strong Atmosphere | 1.18 | Visibility ceiling for comparison |

These are review parameters, not frozen product values. The overlay is labelled `REVIEW-ONLY`; it is composited into Web PNG/WebM export so evidence matches the visible preview. The native Android renderer is unchanged.

## Dynamic review

Dynamic A/B/C controls use the existing page playback, speed, state and recording controls. The candidate field breathes at four different periods (9.2s, 12.8s, 10.6s and 15.4s) with different phases; the breath amplitude varies by candidate (3.5%, 6.5%, 9.5%). Existing local path flow remains separate; no full-perimeter chase is introduced by the review layer. Three-state WebM is browser evidence only.

The added browser smoke captures desktop and narrow A/B/C comparisons and all three dynamic states. Its JSON status is `RENDER_SIGNAL_ONLY_REVIEW_REQUIRED`, and visual approval is `NOT_AUTOMATED`. A screenshot must still be reviewed by a person; automation cannot declare naturalness, taste, or visual PASS.

## Fish Focus provenance

- `Production`: **BLOCKED**. This review has no current production `FishSubjectResult` or production Mask to bind.
- `Review-only`: `REVIEW_PROXY_NOT_PRODUCTION`, using the Case B detector fixture, historical BBox evidence, and manually annotated alpha. The alpha is not a production Mask.
- `Blocked`: default mode when no trusted production input or selected Review-only proxy is available; no Fish Focus contour is drawn.
- Fish Focus uses a separate warm-gold palette. It is distinct from the blue/gold perimeter field.

## Evidence limits

The default photo is a P5 test fixture whose SHA is verified against its registry entry; it is not bound to the photo inside the Frozen PNG. Pixel Diff remains exploratory. Canvas output is not Android output. Web review and smoke evidence do not mean that P1–P5, Frozen fidelity, or visual approval passed.

Android Runtime: **not completed**. Native implementation: **not completed**. This is a Web Review stage only.
