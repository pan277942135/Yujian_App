# Recognition Result Motion Spec V3 — SWOS Gold Continuation

**Status:** `PROPOSED_FOR_MAIN_ADOPTION` · design contract only
**Runtime / playback:** `NOT_RUN`
**Menu:** Recognition Result → RR09 · 动效接续 V3

## Authority and V2 history

V3 is the proposed current RR09 reveal authority. It supersedes V2 only for the successful Result-entry reveal's clock origin, Processing handoff, optical source and title presentation. Frozen RR09 V2 remains byte-for-byte unchanged and is kept as a historical baseline. Motion V1 continues to govern non-conflicting Result interactions, CTA/save behavior and recovery states. The static Result layout, existing Hero Media Contract, real photo and result routing remain unchanged.

V3 resolves two conflicts in V2: `T0` is the first frame actually presented by Result (not a Processing RESOLVE timestamp), and the Chinese species name is the only revealed species title; no English scientific-name line or blank reserved row is shown.

## Trigger and replay policy

- Play once for a new recognition session that reaches `RESULT_HIGH` with a valid species identity and satisfies the existing business gates.
- Do not play for `RESULT_MEDIUM`, `RESULT_LOW`, `ERROR_NO_FISH`, `ERROR_IMAGE_QUALITY`, technical failure, invalid result identity, or a reopened/historical result.
- Recomposition, rotation, editor/selector return and repeated rendering of the same session do not replay the reveal.
- The animation never delays CTA, save, edit, back navigation or access to the valid Result data.

## Frozen design timeline — 850 ms from first Result frame

`T0` is the first Result frame that is actually presented. The route has already been allowed by the real result and existing gates. No extra 200 ms pre-navigation wait is permitted.

| RR09 time | Duration | Phase | Required visual behavior |
|---|---:|---|---|
| 0–200 ms | 200 ms | Photo continuity | The same source photo resolves inside the existing Result Hero crop. Peripheral water light is already gone. A restrained trusted fish-gold trace remains as the source. |
| 200–380 ms | 180 ms | Gold transfer | Warm light travels from the real fish region toward the existing Chinese species title below the photo. Keep the same image and subject position. |
| 380–550 ms | 170 ms | Chinese-name formation | The real Chinese name's glyph strokes form softly; the complete name is not readable early. No English line or empty title row is reserved. |
| 550–680 ms | 130 ms | Chinese-name reveal | The Chinese species name becomes clear in the existing title block. A single restrained highlight may resolve it. |
| 680–850 ms | 170 ms | Afterglow fade | Warm light dissipates and the existing static Result page remains fully visible and interactive. No persistent motion or glow loop. |

V3 total: **850 ms**. The 0–200 ms continuity segment belongs to this post-navigation clock. It is not an additional delay before navigation.

## Geometry, title and motion constraints

- Use the same original photo and the existing Result Hero container/mapping from `Recognition_Result_Hero_Media_Contract_V1.md`.
- Map fish geometry using the actual shared photo transform and continuity handoff. Never use raw bitmap coordinates as screen coordinates.
- A trusted contour may supply a contour-following gold source. A valid BBox without trusted contour permits a bounded regional glow only, never a BBox stroke. Invalid/untrusted geometry disables spatial transfer and uses a restrained non-spatial title resolution.
- The title remains below the photo in the existing title block. Reveal only the Chinese species name. Do not display or reserve a row for a scientific English name.
- The gold trace is the only carried optical source from Processing. No blue edge field, closed frame, scanner, full-screen tint, HUD, confetti, fireworks or hard beam appears in RR09.
- Do not change static Hero placement, metadata, CTA layout, thresholds, confidence semantics, data, route or Result state.

## Eligibility, Reduce Motion and engineering boundary

HIGH-only and one-shot behavior remain. Medium/Low/recovery/error states do not celebrate. Reduce Motion removes spatial travel, particle travel and sweep; a restrained static title resolution must remain immediately readable. Keep the reveal independent from CTA readiness. Limit transient particles to the existing V2 budget of 12–18 when enabled; low-performance may reduce or omit them. No custom sound or haptic.

The existing V2 spec, machine contract, system contract and V2 compatibility report are historical evidence only. Android source/test alignment, browser playback, visual parity, API 28 performance and APK provenance remain `NOT_RUN` in Phase 0.
