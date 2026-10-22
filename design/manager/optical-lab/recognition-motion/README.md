# 识别动效 · Receiving Light Studio

This route is a Web-only, reviewable playback prototype. It does not indicate Android Runtime completion or production Fish Mask availability.

## Timeline

The studio uses the recognition timing already recorded in RecognitionOpticalConfigV2.json and the P4 motion handoff. One clock drives every optical layer.

| Time | Stage | Preview |
| --- | --- | --- |
| 0–220 ms | CAPTURED | Four perimeter light islands wake independently. |
| 220–540 ms | DETECTING | Blue and gold edge fields breathe slowly with a restrained path flow. |
| 540–890 ms | OUTLINE | Edge light recedes as a broad transfer wash moves inward and the warm-gold review contour appears. |
| 890–1140 ms | CLASSIFYING | Perimeter light settles into a faint ambient field; fish contour and halo stabilize. |

The page supports play/pause, replay, 0.5×/1×/1.5× speed, timeline seeking, 30 fps frame stepping, four visibility switches, and four responsive viewport presets. prefers-reduced-motion prevents autoplay; playback and frame inspection remain user controlled.

## Sources and limits

- Photo: design/system/fish_media_display_v1/assets/real_catch_source.jpg, 1152×1536, SHA-256 6e955087108f7463eca2dbac489699a4942028ebe1b3d456ddfca523286385ef.
- Primary path geometry and timing: design/system/recognition_optical_v2/RecognitionOpticalConfigV2.json.
- Shared Receiving Light renderer: design/manager/edgeglow-receiving-light.js, used with the P1 base-alpha and depth limits.
- Fish outline: MANUAL_REFERENCE_CONTOUR, from design/manager/nh01_acceptance/edgeglow-fish-static/fixture.json. It is a hand-traced review proxy, **not a production Mask**.
- The wide animated SVG field and transfer wash are review-only presentation layers. They do not change the P1 contract or the production renderer.

## Evidence

The Chromium capture workflow writes actual browser keyframes, four viewport captures, a complete WebM playback, a JSON run report, and a SHA-256 manifest to the recognition-motion-studio Actions artifact. Automated browser success is reported as WEB_PROTOTYPE_PASS; visual direction remains VISUAL_REVIEW_REQUIRED.
