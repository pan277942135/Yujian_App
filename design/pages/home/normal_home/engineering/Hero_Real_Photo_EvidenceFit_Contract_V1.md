# Normal Home — Real Catch HOME Hero Media Contract V1.1

Status: **FROZEN_PRESENTATION_RULES V1.1 / ANDROID RUNTIME EVIDENCE SEPARATE**

Authority: NH01/NH02 shared HOME Hero, real source-photo evidence semantics, and current Fish Media Display V1.1 contract.

## Core principle

The card geometry responds to available screen width but retains the component's frozen aspect ratio. The source photo's portrait/landscape orientation never changes the card ratio. BBox controls presentation crop and subject positioning, not card geometry.

The 740×880 HOME Hero reference is a design canvas and aspect-ratio reference, not a fixed device-pixel size. Runtime width follows available content width after insets and margins; height follows the approved ratio. Keep the existing maximum-width and layout constraints. Do not alter footer metadata, radius, typography, or CTA as part of this media update.

## Frozen media rules

1. Use the exact real stored FishRecord photo, oriented according to EXIF/decoded orientation. Keep original color, species evidence, timestamp, and provenance. The original media bytes must remain unchanged.
2. If a trusted BBox exists, use it as the anchor for a safe presentation crop. Start with 10% horizontal and 12% vertical margin.
3. If a requested expansion reaches a source edge, stop on that side and transfer the unavailable expansion to the opposite side where space exists. Apply this independently horizontally and vertically.
4. If no legal crop window exists, retry with margin profiles 10%/12%, 8%/8%, 4%/4%, then 0%/0%. Recompute edge compensation and crop geometry for every profile.
5. Use crop-to-fill only when the complete valid BBox remains visible and the crop window is inside the oriented source after pixel rounding. Do not cut fish head, tail, fins, or other verified fish pixels.
6. If every safe crop profile fails, or BBox is absent/untrusted, use the complete original photo as the foreground and a soft low-contrast cover of the same photo as the background. The background is a presentation derivative, never a replacement source photo.
7. Avoid a hard rectangular foreground seam. Use restrained edge blending where it will not soften or obscure the known fish region. Do not apply a uniform blur to the fish body.
8. The final media surface fills the rounded card. No black bars, pure-color letterboxing, empty bands, non-uniform stretching, AI outpainting, inpainting, synthetic fish, or unrelated image substitution.
9. If the source image already contains dark areas or technical bars, do not trim them unless technical letterboxing is reliably identified. When uncertain, preserve the captured image and use same-source background fallback.
10. Missing or undecodable media uses the approved neutral placeholder while preserving FishRecord identity and metadata.

## Deterministic decision states

- VALID_BBOX_CROP: crop passes complete-BBox and source-boundary checks.
- EDGE_COMPENSATED_CROP: edge expansion was transferred to the opposite side and the resulting window passes all checks.
- REDUCED_MARGIN_CROP: a reduced margin profile passes all checks.
- SAME_SOURCE_AMBIENT_FALLBACK: no safe crop exists or BBox is not trustworthy; complete source remains in foreground.
- IMAGE_UNAVAILABLE: neutral approved placeholder.

Record the selected mode and reason in diagnostic output. Do not claim a BBox is valid solely because a visually attractive crop was produced.

## Responsive and device acceptance

- Test 320dp, 360dp, 393dp, 430dp content widths; include ≥600dp only if tablet/wide-window layout is supported.
- Test font scale 1.0× and 1.3×, plus 1.5× where supported.
- Test gesture navigation, three-button navigation, edge-to-edge and safe insets.
- Verify the card width follows available content width and the component ratio remains stable.
- Verify HOME and DETAIL surfaces use the same original FishRecord ID and preserve the original source hash.
- Verify loading/caching does not cause card geometry changes.
- Each case must record original SHA, oriented dimensions, EXIF orientation, BBox provenance, crop window or fallback reason, screenshot hash, APK commit and test environment.

Required source cases: landscape, portrait, extreme portrait, fish near each edge, embedded technical letterbox, night/flash capture, no reliable BBox, and decode failure. Missing source provenance remains BLOCKED/NOT_RUN; do not synthesize AI fish to fill test slots.

The design contract is frozen; Android runtime and real-device evidence remain separate gates.
