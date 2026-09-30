# Recognition Result — Design Package Closure V1

Status: **PASS — DESIGN FROZEN**

## Purpose

Close the existing Recognition Result 3+2 Hi-Fi states into one implementation-ready design package without redesigning the approved screens.

This closure covers:

- five state definitions
- authority order
- behavior
- visual hierarchy
- copy roles
- CTA semantics
- transition/motion
- accessibility / compact-height behavior
- shared-component use
- runtime alignment review
- acceptance criteria

It does not change:
- detector behavior
- crop behavior
- classifier behavior
- confidence thresholds
- Recognition Processing V1.1 timing
- the five approved Frozen PNGs

## Closure result

| Area | Result |
| --- | --- |
| 5 Frozen state references | PASS |
| Authority order | PASS |
| State matrix | PASS |
| Behavior contract | PASS |
| Visual contract | PASS |
| CTA contract | PASS |
| Result transition | PASS |
| Accessibility / compact-height rule | PASS |
| Shared component rule | PASS |
| Acceptance criteria | PASS |
| Runtime consistency | NEEDS IMPLEMENTATION CLOSURE |

## Frozen states

3 result states:
1. High
2. Medium
3. Low

2 recovery states:
4. No Fish
5. Image Quality

Technical failure is a runtime-safe fallback only and is not promoted into a sixth frozen Result design.

## Explicit authority decisions

1. The five state PNGs 05–09 are the final authority for state-specific composition, hierarchy and visible copy.
2. `recognition_result_v1.png` remains the system-level Result visual-language reference.
3. If the system reference and a state PNG differ, the state PNG wins for that state.
4. The user's real captured photo is runtime media and must never be replaced by a static design asset.
5. Result screens do not inherit Processing AI filaments, contour, halo or HUD-like effects.
6. Shared P0 actions/navigation must be reused; page-private button systems are not authorized.
7. Low confidence is a recoverable record state, not a forced-failure state.
8. No Fish and Image Quality are recovery screens and do not create FishRecords.

## Known implementation boundary

The current Android runtime does not fully match the closed design package.

The authoritative gap list is:

`review/Recognition_Result_Runtime_Alignment_Review_V1.md`

Design closure itself does not claim Runtime PASS.

## Freeze definition

Any change to:
- 3+2 state information architecture
- CTA meaning
- low-confidence save policy
- candidate-confirmation model
- state-level Frozen composition
- Result-to-memory flow

requires a versioned Result package revision.
