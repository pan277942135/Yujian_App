# Recognition Visual Fidelity V1.2

Status: implementation authority for the physical-device visual closure.

## Scope

Presentation only. Do not change detector, classifier, crop semantics, confidence semantics, recognition copy, or the V1.1 visual-state timing contract.

Nominal visual story remains:

- CAPTURED: 350 ms
- DETECTING: 600 ms
- OUTLINE: 600 ms
- CLASSIFYING: 1250 ms
- final resolve fade: 200 ms inside CLASSIFYING

## Visual authority

The original captured fishing photo remains fully opaque and is always the primary visual layer.

Forbidden:

- detector rectangle
- scanner frame / crosshair
- radar / HUD
- full-frame color wash
- closed neon border
- synthetic AI replacement image

## State intent

### CAPTURED

- Blue/gold peripheral AI field is immediately and clearly perceptible.
- Primary paths remain broken and asymmetric.
- Secondary hairline filaments and sparse energy nodes provide texture.
- Photo remains dominant.

### DETECTING

- AI field stays visibly alive.
- Edge bloom and secondary activity begin to reduce while primary flow remains strong.
- No fish highlight is shown before a real detector bbox exists.

### OUTLINE

- This is the primary AI moment.
- Real detector bbox activates fish focus.
- When real subject alpha is ready, Level A contour follows the real fish silhouette.
- Level A uses three passes: restrained local bloom, mid glow, thin hot gold core.
- Fish interior remains transparent.
- When subject alpha is unavailable, Level B is an ellipse/halo fallback only; never draw a rectangle.

### CLASSIFYING

- Real fish contour remains visible.
- Peripheral AI field slows and reduces in prominence.
- Fish focus breathes on a 1900 ms cycle.
- Reduce Motion keeps the same semantic state and a static focus.

### RESULT

- During the final 200 ms resolve window, AI field and fish focus fade rapidly.
- Result UI returns to normal non-AI presentation.
- RESULT is routing state only; no fifth processing card.

## Performance / accessibility

- Reduce Motion: preserve state changes and static fish focus; remove continuous travel.
- Low-performance devices: reduce particles, remove secondary filaments, reduce bloom, and allow Level A -> Level B.
- Model behavior, confidence, bbox and result routing must remain unchanged.

## V1.2 implementation calibration

Ambient perceptual curve (final line-led calibration; edge bloom intentionally stays low so the photo is never washed out):

| Phase | Edge | Primary | Secondary | Particles | Speed |
| --- | ---: | ---: | ---: | ---: | ---: |
| CAPTURED | 0.34 | 0.88 | 0.52 | 0.42 | 1.00 |
| DETECTING | 0.30 | 0.82 | 0.42 | 0.38 | 0.92 |
| OUTLINE | 0.24 | 0.60 | 0.26 | 0.26 | 0.78 |
| CLASSIFYING | 0.16 | 0.42 | 0.14 | 0.16 | 0.52 |

Level A contour:

- OUTLINE max contour strength: 0.92 after 370 ms smooth reveal.
- CLASSIFYING contour strength: 0.78 × breath pulse.
- outer local bloom: 11 dp, low alpha
- mid glow: 4 dp
- hot core: 1.55 dp
- final resolve uses squared fade for fast release.
