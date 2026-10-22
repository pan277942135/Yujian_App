# Recognition Result Motion Contract V3 — RR09 SWOS Continuation

**Status:** `PROPOSED_FOR_MAIN_ADOPTION` · visual/motion design contract only
**Runtime:** `NOT_RUN`
**Current proposed RR09 authority:** `design/pages/recognition/result/motion/Recognition_Result_Motion_Spec_V3.md`
**Machine companion:** `design/pages/recognition/result/motion/recognition_result_motion_contract_v3.json`

## Scope and versioning

V3 is the proposed current contract for the successful Result-entry motion. It supersedes V2 only for clock origin, Processing handoff interpretation and revealed species-title content. V2 remains unchanged and is retained as the historical baseline. Result Page Contract V3.1, static Result assets, Hero Media Contract V1, CTA/action semantics and all non-conflicting Motion V1 behavior remain in force.

## Required visual acceptance

1. `T0` equals the first actually presented Result frame. The 850 ms clock begins at that frame.
2. Navigation happens as soon as the real result, Processing minimums and existing business gates allow it. No additional 200 ms pre-navigation delay exists.
3. The same source photo, Hero crop and subject position remain continuous through the 0–200 ms photo-continuity phase.
4. Gold originates at trusted fish geometry and moves toward the existing Chinese title below the photo. A BBox-only fallback is a bounded regional glow, not a rectangular or invented fish outline.
5. The Chinese species name is the only revealed species title. No English scientific name or empty English row is shown.
6. High-only, valid-identity, once-per-new-session eligibility is preserved. Medium, Low, No Fish, Image Quality and Technical Failure do not run the successful reveal.
7. CTA, save, edit and back behavior stay available independently of motion completion.
8. Reduce Motion resolves to a stable title presentation without spatial travel. All temporary glow/particle activity ends by 850 ms.

## Timing authority

| Segment | RR09 elapsed time |
|---|---:|
| Photo continuity after navigation | 0–200 ms |
| Gold transfer to Chinese title | 200–380 ms |
| Chinese glyph formation | 380–550 ms |
| Chinese name becomes clear | 550–680 ms |
| Afterglow to static Result | 680–850 ms |

Processing's 2600–2800 ms peripheral-water release and RR09's 0–200 ms photo continuity are different phases on different sides of navigation. They are not two sequential waits. Their shared trace and photo identity are governed by `design/pages/recognition/continuity/Processing_Result_Optical_Continuity_Contract_V1.md`.

## Acceptance boundary

This contract defines visual intent and testable boundaries. It does not certify visual playback, Android API 28 performance, runtime parity, a production fish mask, or APK delivery. Those gates remain `NOT_RUN` until exact-SHA evidence is attached.
