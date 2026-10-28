# Recognition Result Motion Spec V1

## Scope

Recognition Result does not add a new animated AI phase.

The final Processing transition is already defined by Recognition Visual Fidelity V1.2:

- final resolve fade: 200 ms
- AI field and fish focus rapidly disappear
- normal Result presentation becomes dominant

## Result entry

Allowed:
- restrained content resolve/fade matching the Frozen transition
- shared button pressed state
- shared dialog/sheet transition
- user-driven scrolling

Forbidden:
- result-card celebration
- glow burst
- auto-pulsing confidence
- automatic candidate cycling
- fish-card auto-flip
- confetti
- continuous AI filament motion after Result

## Candidate interaction

Candidate selection may use the shared pressed/selected-state transition only.

No automatic candidate highlight travel is allowed.

## Save

While saving:
- shared Loading state only
- no layout jump
- no looping page-level animation

On success:
- navigation provides the completion transition
- no separate reward animation is required

## Recovery states

No Fish and Image Quality remain static except for shared control feedback.

## Reduce Motion

Reduce Motion:
- removes nonessential entry interpolation
- preserves state, hierarchy, selected candidate, saving state and navigation
