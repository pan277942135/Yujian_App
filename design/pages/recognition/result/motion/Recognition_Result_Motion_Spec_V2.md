# Recognition Result Motion Spec V2 — 金光转译揭晓

**Status:** FROZEN · DESIGN SPECIFICATION ONLY  
**Runtime:** NOT_RUN  
**Menu:** 识别结果 → RR09 · 动效接续  
**Scope:** One-shot successful Result entry; no Android/product-code changes in this specification task.

## 1. Decision

Freeze the **Gold Transfer Reveal / 金光转译揭晓** as the current Recognition Result entry-motion authority:

**鱼体高亮 → 照片自然接续到结果页 → 金光从鱼体向下转移 → 鱼种名称由光凝结成形 → 余辉消散。**

The reveal must communicate a causal chain. It must not look like a generic title fade, a scan line passing over pre-existing text, or a game-style celebration.

## 2. Authority and versioning

1. `Recognition_Visual_Fidelity_V1_2.md` remains authoritative for the three Processing states, recognition semantics, detector/classifier behavior, and the existing 200 ms RESOLVE window.
2. The five canonical Result state PNGs and `Recognition_Result_Hero_Media_Contract_V1.md` remain the layout and real-photo mapping authorities.
3. V2 supersedes the conflicting **Result-entry presentation** clauses in Motion V1 only for a new, successful `RESULT_HIGH` entry: the old opacity-only/“no glow burst or celebration” prohibition no longer blocks this single, finite, non-game-like reveal.
4. All non-conflicting Motion V1 rules remain active, including CTA behavior, save behavior, candidate interaction, recovery states, and Reduce Motion.
5. This is a presentation-only transition. It adds no Processing state, changes no model threshold or confidence meaning, and changes no result routing.

## 3. Trigger and replay policy

- Run only when a **new recognition session** resolves to `RESULT_HIGH` with a valid species identity.
- Play once per recognition-session identity. Recomposition, rotation, returning from a selector/editor, reopening a saved catch, or revisiting the same result must not replay it.
- `RESULT_MEDIUM`: no celebratory reveal while candidates remain unconfirmed.
- `RESULT_LOW`, `ERROR_NO_FISH`, `ERROR_IMAGE_QUALITY`, and `TECHNICAL_FAILURE`: no reveal.
- Missing or untrusted fish geometry must not fabricate a source point. Fall back to a restrained opacity-only title appearance.

## 4. Frozen timeline — 850 ms total

**T0 is the start of the existing 200 ms RESOLVE window.** The photo handoff is coordinated inside that window; do not append a second, independent 200 ms page-entry fade. The route changes normally and the CTA is never held hostage by the animation.

| Keyframe / time | Frozen phase | Required visual result |
|---|---|---|
| K01 · T0 | 鱼体高亮锁定 | Last Processing frame: original photo remains dominant; real fish contour/Halo is the source of the gold light. No species name is visible yet. |
| K02 · 0–200 ms | 照片自然接续 | An explicit intermediate transition frame: the Result header/surface begins to resolve while the same photo continues into the Result Hero. Preserve subject position and crop continuity; peripheral AI field recedes; a restrained gold trace remains on the fish. |
| K03 · 200–380 ms | 金光转移 | Warm gold leaves the fish contour in a controlled, downward-directed flow toward the existing species-title area **below the photo**. The fish stays visible as the source; the photo does not flash, jump, or disappear. |
| K04 · 380–550 ms | 名称凝结中 | Light and a small number of particles converge in the title area. Show a soft, incomplete glyph mask / partial strokes only; the full species name must not be readable early. Use the real typeset font mask, not randomly drawn particle letters. |
| K05 · 550–680 ms | 鱼种名称揭晓 | The Chinese species name becomes fully legible in the existing title area below the Hero. Show its validated scientific English name immediately beneath it when available. One restrained gold sweep crosses the title once. |
| K06 · 680–850 ms | 余辉消散 | Residual particles and glow fade away. The title returns to normal static typography; the Result page remains stable and fully interactive. No persistent AI filament, pulse, or glow loop. |

## 5. Layout and optical invariants

- The same original capture must remain the visual source throughout the handoff. Reuse the existing Hero container and Hero Media Contract; do not substitute a generated fish image.
- The Chinese species name is **below the photo**, never overlaid on the fish image. The scientific English name, when present in canonical species metadata, sits directly below the Chinese name in the same title block.
- The only permitted title-block addition in this motion revision is the subordinate scientific-name line; do not move the Hero, metadata cards, story field, or CTAs to make room. Revalidate text fit against the frozen Result layout.
- Map the detector BBox / subject contour through the actual photo viewport transform before choosing the light origin and destination. Never use raw bitmap coordinates as screen coordinates.
- Gold is the transfer signal; blue peripheral Receiving Light recedes during the handoff. No closed neon border, full-screen wash, HUD, confetti, fireworks, or hard-edged beam.
- Particles support the transfer; they must not obscure the fish or compete with the species name.
- Use only a validated scientific name from the species catalog. Never infer or invent one. “翘嘴鲌 / Culter alburnus” is an illustrative storyboard example, not a hardcoded runtime value.

## 6. Performance, interaction, and accessibility

- Particle budget: 12–18 visible particles maximum on the full-quality path; low-performance path may reduce to 0–6 or omit particles.
- No continuous animation after 850 ms. Avoid full-screen overdraw and unbounded blur layers.
- Result controls remain actionable at canonical `RESULT_READY` as soon as required data exists. No input interception, CTA delay, layout jump, or duplicate navigation.
- No custom sound or haptic is added.
- With Reduce Motion enabled, remove particle travel, transfer travel, scale interpolation, and title sweep. Use a restrained opacity-only resolution; keep the species name immediately readable and preserve all state/action semantics.
- If the photo BBox/mask or mapping is invalid, use the opacity-only fallback; do not emit light from an arbitrary location.

## 7. Acceptance checklist

- [ ] K02 visibly bridges Processing and Result; it is not a simple cut or duplicate static screen.
- [ ] The same photo and fish position remain continuous through the handoff.
- [ ] K03 makes the light's origin (fish contour) and destination (title below photo) unmistakable.
- [ ] K04 has no fully readable species name; K05 is the first complete reveal.
- [ ] Chinese name and validated English scientific name are placed below the photo, in the title block.
- [ ] K06 has no persistent glow or particles and matches the static Result hierarchy.
- [ ] HIGH-only trigger and no-replay behavior are preserved; Medium/Low/recovery/failure do not celebrate.
- [ ] CTA interaction is available independently of animation completion.
- [ ] Reduce Motion and low-performance fallbacks preserve the same result and actions.
- [ ] Android runtime, browser playback, pixel parity, and device performance remain marked NOT_RUN until real evidence is attached.

**Freeze boundary:** This document freezes design intent only. It does not claim a playable prototype, Android implementation, or runtime acceptance.
