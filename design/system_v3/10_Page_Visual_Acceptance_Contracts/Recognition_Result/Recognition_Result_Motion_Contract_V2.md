# Recognition Result Motion Contract V2

**Status:** FROZEN · DESIGN CONTRACT ONLY  
**Runtime acceptance:** NOT_RUN  
**Page:** `recognition_result_v1`  
**Menu:** `RR09 · 动效接续`

## Authority

- Current Result-entry motion authority: `design/pages/recognition/result/motion/Recognition_Result_Motion_Spec_V2.md`
- Machine-readable companion: `design/pages/recognition/result/motion/recognition_result_motion_contract_v2.json`
- Processing state / RESOLVE authority: `design/pages/recognition/spec/Recognition_Visual_Fidelity_V1_2.md`
- Result Hero media mapping: `design/pages/recognition/result/media/Recognition_Result_Hero_Media_Contract_V1.md`
- Historical baseline: `Recognition_Result_Motion_Contract_V1.md` and `Recognition_Result_Motion_Spec_V1.md`

V2 is the current authority for the one-shot successful Result-entry reveal. It supersedes V1 only where V1 forbids the scoped gold-transfer reveal or requires an opacity-only Result entry. V1 continues to govern all non-conflicting Result interactions, save behavior, recovery states, and shared control feedback.

## Page entry and timing

| Phase | Frozen rule |
|---|---|
| Timeline origin | Start of the existing 200 ms Processing RESOLVE window |
| Photo continuity | 0–200 ms; same captured photo naturally hands off into the Result Hero |
| Gold transfer | 200–380 ms; gold moves from the real fish contour toward the species title below the photo |
| Name formation | 380–550 ms; incomplete glyph mask only, no fully readable name before reveal |
| Name reveal | 550–680 ms; Chinese name resolves, validated scientific English name sits below it when available, one restrained gold sweep |
| Afterglow fade | 680–850 ms; all glow/particles disappear and the Result returns to normal static presentation |

The existing 200 ms RESOLVE is reused as the photo handoff; do not append an extra 200 ms page-entry delay. This is a presentation transition, not a fourth Processing state. The three-state Processing model and all detector/classifier semantics remain unchanged.

## Visual invariants

- The photo remains the primary visual source and is mapped using the Hero Media Contract.
- The species title is placed below the Hero photo, never over the fish image.
- The Chinese species name is the primary line. A validated scientific English name appears directly beneath it when available in canonical species metadata; no species name is invented.
- The light has an explicit causal path: real fish contour → controlled downward transfer → title region → static title. It must not read as a generic line sweep over text that was already visible.
- Particle budget is 12–18 on the full path and 0–6 on low-performance devices; no persistent motion after 850 ms.
- No confetti, fireworks, full-screen color wash, closed neon border, custom sound, or haptic.

## Trigger and exclusions

- Trigger once on a new recognition session entering `RESULT_HIGH` with a resolved species identity.
- Do not replay on recomposition, rotation, reopening a historical result, returning from an editor/selector, or revisiting the same result.
- `RESULT_MEDIUM` has no celebration while the candidate remains unconfirmed.
- `RESULT_LOW`, `ERROR_NO_FISH`, `ERROR_IMAGE_QUALITY`, and `TECHNICAL_FAILURE` do not trigger the reveal.
- If the fish BBox/contour-to-screen mapping is untrusted, use opacity-only resolution; never invent the light source.

## Interaction and accessibility

- CTA controls remain actionable as soon as canonical `RESULT_READY` is true; the animation cannot delay or intercept actions.
- No layout shift, duplicate navigation, or continuous title animation.
- Reduce Motion removes particle travel, transfer travel, scale interpolation, and gold sweep. Keep a restrained opacity-only transition and immediately readable species text.
- Low-performance fallback reduces/removes particles and blur while preserving the same result, layout, and actions.
- Sound and haptics remain NONE.

## Acceptance

Design acceptance requires proof of the intermediate photo handoff, real fish-to-title transfer direction, no early readable title, Chinese/English title placement, complete afterglow disappearance, non-blocking CTA, no replay, and Reduce Motion fallback.

**No implementation claim:** this contract does not establish browser playback, Android runtime, pixel parity, or device performance. Those remain NOT_RUN until exact-SHA evidence is attached.
