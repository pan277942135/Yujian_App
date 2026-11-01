# Empty Home Motion Spec V2

All numbers use the 1080 × 1920 reference space and are mirrored in `motion_contract.json`.

| Layer | Frozen behavior |
| --- | --- |
| Bobber | Y only, ±3 px, 4600 ms loop: 0 → -3 → 0 → +3 → 0 at 0/1150/2300/3450/4600 ms. No X, rotation or scale. |
| Ripple | One water-contact ripple group with three vector wave layers; 3,600 ms burst; wave delays 0 / 420 / 900 ms; radial growth 270 / 190 / 120 reference px; one stable contact ring remains in Reduce Motion. |
| Cloud | About 0.2 reference px/s, 60 s-scale, extremely subtle. |
| Sun particles | 4.8 s loop, 6–12 particles, each alpha ≤ 0.18. |
| Camera rim | First around 3 s; around 1.4 s duration; around 9 s repeat interval. |
| Camera breath | Around 5 s and scale ≤ 1.015. |

The environment phases are intentionally non-synchronous. A single subtle double-pulse is permitted only at the start of an actual scheduled bobber/ripple event. Page entry and idle without an event remain silent. Reduce Motion stops the event schedule and automatic haptics while preserving the static contact ring.


## Approved V2.2 amendment — 2026-11-01

The product owner approved one ripple group containing three independently expanding vector waves.
The event schedule repeats every 36,000 ms, with starts at 1,500 / 6,800 / 13,600 / 22,400 /
28,200 ms relative to the 420 ms ripple phase offset. Each burst lasts 3,600 ms. Bobber geometry
and its Y-only ±3 px / 4,600 ms motion remain unchanged.
