# Empty Home Visual Spec V2

The immutable visual source is `Empty_Home_Final_Design_V2`, normalized to a 1080 × 1920,
9:16 reference space without cropping. The source manifest records original and normalized SHA256.

## Layer order

1. Scene Base: sky, mountains, trees, lake, fixed mist, shoreline, tackle and foreground.
2. Cloud / atmosphere: only the weak moveable cloud veil.
3. Sun / ambient: weak beam and particles.
4. Rod.
5. Line.
6. One ripple.
7. Bobber.
8. Native UI.

Scene Base contains no UI, rod, line, bobber, runtime ripple, or animated beam/particle. Ripple
is always below the bobber. The source composition, foreground, dawn colour, hero, account slot,
CTA, camera placement and fishing relationship must not be redesigned.

Exact normalized anchors are versioned in `shared/contracts/anchor_contract.json`.
