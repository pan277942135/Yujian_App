# Processing → Result Optical Continuity Contract V1

**Contract ID:** `YUJIAN-RECOGNITION-OPTICAL-CONTINUITY-V1`
**Status:** `PROPOSED_FOR_MAIN_ADOPTION`
**Scope:** Shared photo, subject geometry, session identity and light-source handoff from Processing into RR09 V3.
**Runtime:** `NOT_RUN`.

## Authority

This version resolves the cross-page clock and visual-source ambiguity between Recognition Processing Motion V1.3 and RR09 V3. On adoption, it is the shared authority used by both pages. It does not replace Processing state/data contracts, the Result static Page Contract or the Hero Media Contract; it coordinates only the optical handoff between them.

## Handoff identity

The handoff object must preserve one real recognition session and one source photograph from capture through the Result reveal. At minimum it carries:

- stable image/source identity and original source dimensions;
- EXIF-normalized photo identity and the same production crop/scale mapping;
- the single `photoContentRect`/`RecognitionImageTransform` used by both pages;
- actual detector BBox and confidence when present;
- subject geometry plus its trust/source classification when present;
- result identity, recognition session ID and attempt ID;
- the restrained residual-gold state and a monotonic handoff time.

Never substitute another/generated photo, infer missing identity, remap the fish against the screen rectangle, or restart the subject geometry at a guessed position. The Result Hero continues to use `Recognition_Result_Hero_Media_Contract_V1.md`.

## Geometry provenance

`FishRecognitionPipeline.kt` is the source of actual detector, confidence and classifier outputs. `FishSubjectPreviewEngine.kt` is a post-recognition visual preview engine and does not define classifier input. A contour is eligible for spatial gold only after the existing geometry-trust gate verifies matching image identity, normalized source dimensions, detector box/confidence, quality and valid non-empty mask/contour data.

If a trustworthy contour is absent but the real BBox is valid, the reveal may use a bounded regional glow; it must not outline the rectangle or claim a production contour. If the BBox or coordinate mapping is invalid, omit spatial transfer and use the restrained non-spatial title resolution. Do not manufacture a source point.

## Optical continuity timing

1. Processing has a 2800 ms minimum visual story defined in `Recognition_Processing_Motion_Spec_V1_3.md`; real events still gate state advancement.
2. From 2600–2800 ms, peripheral water light decays to zero. A narrow, soft gold trace remains on trusted fish geometry when available.
3. Once the Processing minimum and actual result/business gates are satisfied, navigate immediately. Do not wait another 200 ms before navigation.
4. RR09 V3 `T0` is the first frame actually presented by the Result page.
5. RR09's 0–200 ms photo-continuity segment runs after navigation within the RR09 850 ms clock. It does not define an additional Processing phase or wait.
6. Gold travels from the real fish location toward the existing Chinese species-title block below the photo. The title is not placed over the fish image.
7. After 850 ms, remove transient optical layers and return the existing static Result page to normal interaction.

The nominal visual durations add to 3650 ms (2800 + 850) only when the real pipeline permits the minimum Processing sequence to finish on that schedule. Inference may extend total elapsed time; this contract is not an inference deadline or result countdown.

## Invariants and failure behavior

- Same photo, crop and subject position remain continuous across page navigation.
- Peripheral water light is zero at the end of Processing; the gold trace is the only intentional visual light-source continuation.
- No new product state, confidence behavior, threshold, route, CTA delay or business data is introduced.
- RR09 remains a one-shot presentation-only reveal for eligible new HIGH results; non-success and technical-failure routes do not celebrate.
- CTA, saving, editing and back navigation remain actionable according to the static Result contract; no animation blocks them.
- Reduce Motion and invalid geometry preserve the real result and actions while removing spatial travel.
