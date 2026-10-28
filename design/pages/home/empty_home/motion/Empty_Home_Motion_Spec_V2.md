# Empty Home Motion Spec V2

All numbers use the 1080 × 1920 reference space and are mirrored in `motion_contract.json`.

| Layer | Frozen behavior |
| --- | --- |
| Bobber | Y only, ±3 px, 4600 ms loop: 0 → -3 → 0 → +3 → 0 at 0/1150/2300/3450/4600 ms. No X, rotation or scale. |
| Ripple | Exactly one; centre equals bobber water contact; 1.00 → 1.22 scale, 0.30 → 0 alpha, 3200 ms loop. |
| Cloud | About 0.2 reference px/s, 60 s-scale, extremely subtle. |
| Sun particles | 4.8 s loop, 6–12 particles, each alpha ≤ 0.18. |
| Camera rim | First around 3 s; around 1.4 s duration; around 9 s repeat interval. |
| Camera breath | Around 5 s and scale ≤ 1.015. |

The environment phases are intentionally non-synchronous. There is no automatic haptic for entry,
bobber, ripple or idle. Reduce Motion stops environmental looping while preserving the static scene.
