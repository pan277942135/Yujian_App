# Recognition Processing — Page Contract V3.2

PAGE_ID: `recognition.processing.v3_2`
PAGE_NAME: Recognition Processing
VERSION: `3.2`
STATUS: `MAIN_ADOPTED_DESIGN_ONLY`
AUTHORITY_SOURCE: `design/system_v3/08_Page_Contracts/recognition/processing/State_Machine_V3_2.json`

## Adoption and version boundary

V3.2 is the main-adopted Processing design timeline and optical authority through PR #190. It supersedes V3.1 **only** for timeline, optical behavior and Processing-to-Result timing ownership. V3.1 remains unchanged history; its Layout and Data contracts remain reused where this version does not redefine them. The Frozen photo references, Capture/Result product semantics, model thresholds, route, CTA and error behavior remain unchanged.

## Purpose and user-visible states

Communicate the real recognition process while preserving the original photograph. The only user-visible Processing states remain:

`IMAGE_RECOGNIZING → FISH_LOCATED → SPECIES_RECOGNIZING`

CAPTURED and DETECTING are internal phases mapped to IMAGE_RECOGNIZING. The internal phase list is CAPTURED (350 ms), DETECTING (600 ms), OUTLINE (600 ms) and CLASSIFYING (1250 ms), with cumulative boundaries 0 / 350 / 950 / 1550 / 2800 ms. These are minimum visual beats, not progress estimates or guaranteed inference duration.

## Optical authority

`design/pages/recognition/processing/optics/SWOS_Optical_Contract_V1.md` defines the main-adopted design-only optical language. SWOS uses natural sunlight-on-water behavior: lower photo reflection, irregular caustics and ripple diffusion, real-fish focus, peripheral-water release and a residual fish-gold trace. The legacy seven-path Edge Field remains unchanged historical implementation authority and is not the SWOS geometry basis.

## State and handoff rules

- Each advance requires its minimum phase beat and the corresponding real pipeline event. No timer-only result, ETA, percentage or fake completion.
- If real inference exceeds 2800 ms, keep a quiet low-intensity CLASSIFYING hold until the real outcome and business gates permit navigation.
- If the real outcome is ready earlier, do not shorten the minimum optical story.
- During 2600–2800 ms, peripheral water light decays to zero inside CLASSIFYING; retain a narrow, soft gold trace only on trusted fish geometry.
- After actual result/business gates and the minimum story are met, route immediately. Do not append a 200 ms pre-navigation wait.
- `Recognition_Result_Hero_Media_Contract_V1.md` continues to own the Result photo placement/crop. `Processing_Result_Optical_Continuity_Contract_V1.md` owns shared identity, geometry and light-source handoff.

## Unchanged dependencies

- Page layout and photo geometry: `Layout_Contract_V3_1.json`.
- Data semantics and real result/failure routing: `Data_Contract_V3_1.json`.
- All other non-conflicting Capture, Result, accessibility, model and business authorities.

## Verification status

The V3.2 contract was structurally validated on the PR #190 head before main adoption. Android runtime, API 28 rendering, SWOS visual output and Stage 1 demo are `NOT_RUN`; the existing Android implementation still reflects earlier timing and Edge Field rendering and is listed as a Stage 1 gap.
