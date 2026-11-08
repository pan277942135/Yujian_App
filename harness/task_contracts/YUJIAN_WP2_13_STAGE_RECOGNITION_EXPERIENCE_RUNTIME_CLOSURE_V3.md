# WP2 — 13-stage Recognition Experience Runtime Closure V3

**Task ID:** `YUJIAN_WP2_13_STAGE_RECOGNITION_EXPERIENCE_RUNTIME_CLOSURE_V3`
**Phase:** `P6`
**Gate:** `G-WP2-13-STAGE-RECOGNITION-RUNTIME-CLOSURE-V3`
**Status:** `ACTIVE_CLOSURE`

## Authority and frozen inputs

The two files in `design/pages/recognition/runtime/` registered by
`RECOGNITION-13-STAGE-TIMING-6000MS-V1` are the frozen, machine-readable
6000ms timing decision. They are imported unchanged from the reviewed source
and are not a license to alter the visual-freeze authority. The visual and
resource authority remains `RECOGNITION-VISUAL-HANDOFF-ADDENDUM-V1`, including
the 21-frame binding already present on Main.

This contract is a compatibility handoff record, not a replacement timing or
visual contract. It resolves only the coordinate wording in older timing prose:
references to a “real fish-tail contact/anchor” are descriptive and do not
select a geometry coordinate. Runtime MUST derive the single P* contact from
the current trusted exterior mask contour using
`RecognitionNearestContourContactPoint`, then reuse that same P* for seek,
Contour A, and return. If no trusted mask exists, the existing Orb-only branch
remains required and MUST NOT fabricate a contour, gold pass, mask, box, name,
or source image.

## Stage/controller compatibility

- Use the existing `RecognitionSceneAnimationController` and its one existing
  shared scene clock. No second scene clock, timing arbiter, or new state enum
  is authorized.
- The frozen external labels map to existing runtime states as follows:
  `PERIMETER_FORM` → `PERIMETER_ARRIVAL`, and `FISH_SEEK` → `PATH_TO_FISH`.
  All other stage names use the established current mapping.
- FULL-path minimums remain 0/350/600/125/125/450/450/450/1000/350/850/800/450
  milliseconds, totaling 6000ms. Each stage can extend for real animation,
  model, business-event, or geometry gates; later stages must never be
  compressed to create a hard 6000ms cutoff.
- Entry Photo Morph (700ms) and Camera Orb entrance (900ms) remain parallel
  pre-entry transitions and are not added to the 6000ms schedule.

## Model and delivery constraints

Only the current real classifier/SlimSAM pipeline and their recorded result
objects may drive this work. B009 does not block visual implementation, but it
does block a claim that DET_FISH v0.3 is licensed, packaged, publishable, or
shipped. Scope is limited to the recognition runtime and its focused tests;
unrelated pages, frozen assets, model replacement, and new Orb systems are
prohibited.

Required evidence is exact-source SHA, timing/stage tests, real model and
trusted-mask provenance, focused runtime tests, Android compile/lint/build,
and an exact-head Debug APK SHA-256. Product-owner real-device visual
acceptance remains pending and cannot be represented as passed by this task.
