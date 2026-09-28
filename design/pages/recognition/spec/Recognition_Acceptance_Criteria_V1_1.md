# Recognition Acceptance Criteria V1.1

Status: **FROZEN FOR WORK ACCEPTANCE**

## A. Frozen visual parity

PASS only when:
- all nine runtime states are produced;
- original photo remains the dominant processing frame;
- AI field is peripheral and restrained;
- no scanner box / HUD / crosshair / global tint appears;
- DETECTING has no fish-local focus;
- OUTLINE has a real fish-local halo/perimeter;
- CLASSIFYING keeps fish focus stable and breathing slowly;
- status copy and result/error copy match the frozen contract;
- High / Medium / Low / No Fish / Image Quality layouts remain consistent with their Frozen PNGs.

## B. AI Ambient Field

PASS only when:
- colors use frozen blue/gold tokens;
- seven fixed path geometry is preserved;
- filaments remain thin: 0.8–1.2dp core, 4dp mid, 10dp outer;
- hot alpha <=0.50;
- edge bloom remains peripheral;
- normal particles = 8, low-performance = 4;
- no obvious closed ring forms around the screen.

Immediate FAIL:
- neon/cyber appearance;
- whole-frame color wash;
- all seven filaments visibly synchronized;
- particle density that competes with the photograph.

## C. Fish Highlight

PASS only when:
- starts no earlier than OUTLINE;
- derives from real detector bbox;
- halo remains outside/around fish, not an opaque fish fill;
- radial halo alpha <=0.18;
- perimeter/contour remains warm gold, not white neon;
- Level A contour core is 1.5dp and 0.30–0.42 alpha;
- glow remains soft and subordinate;
- OUTLINE reveal ~360–380ms;
- CLASSIFYING breathing period ~1900ms;
- contour/halo does not drift away from the fish during breathing.

Fallback B is PASS when contour generation is unavailable but bbox halo is valid.
Segmentation failure must never block the recognition result.

## D. Timing

Frozen V1.1:
- CAPTURED 350ms
- DETECTING 600ms
- OUTLINE 600ms
- CLASSIFYING 1250ms
- nominal total 2800ms
- resolve fade 200ms

API28 measured fast-result acceptance:
- total 2500–3500ms;
- final fish focus stable >=1000ms.

Do not restore legacy 220/320/350/250ms or 900ms compression.

## E. Runtime semantics

PASS only when:
- real pipeline gates visual advancement;
- RESULT is shown only after a real result exists;
- FAILURE remains user-safe;
- detector/crop/classifier behavior is unchanged by visual implementation;
- deterministic visual test overrides do not alter production behavior.

## F. Evidence

Required:
- 9 PNGs;
- timing TXT;
- normal-speed processing MP4;
- runtime gate JSON.

Final acceptance:
`runtime_gate_result.json.classification == PASS`.

## Prohibited ways to obtain PASS

- skip/disable failing tests;
- `|| true` around required gates;
- delete assertions;
- increase Compose timeout without root-cause proof;
- lower Frozen visual/timing thresholds merely to get green CI;
- replace runtime screenshots/video with design source images;
- classify missing video as product PASS.
