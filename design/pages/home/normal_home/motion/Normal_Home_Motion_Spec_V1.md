# Normal Home Motion Spec V1

Principle: **alive, not animated**.

Normal Home has no independent decorative environment loop in V1. Motion is limited to the shared capture action and restrained catch-card feedback.

## Hero pager

### Selected card idle micro-breath

When motion is enabled:

- cycle: 6000 ms
- scale: 1.000 → 1.008 → 1.000
- midpoint: 3000 ms
- vertical offset: 0 → -2 dp → 0
- no rotation
- no opacity pulse
- no content animation inside the card

This must remain barely perceptible.

### Swipe transforms

For adjacent pager pages, transform is proportional to normalized page distance `d` in [0,1]:

- scale = `1.0 - d × 0.055`
- translationY = `4 dp × d`
- alpha = `1.0 - d × 0.12`

Horizontal movement is directly driven by user gesture. There is no automatic carousel advance.

## Capture button

Normal Home inherits the shared Home capture-button motion tokens:

- breathing cycle ≈5000 ms
- max scale ≤1.015
- first gold-rim sweep around 3000 ms
- sweep duration ≈1400 ms
- repeat interval ≈9000 ms

No page-local timing variant is authorized.

## Page/environment

- no page-wide entrance choreography required
- no automatic statistics count-up
- no looping lake HUD
- no synchronized decorative loops
- no autonomous pager movement

## Lifecycle and Reduce Motion

Decorative loops run only while the Home lifecycle is resumed.

When system animation/Reduce Motion disables motion:

- selected-card idle micro-breath stops at neutral state
- capture decorative breathing/sweep stops
- user-driven scrolling remains functional
- layout and visual hierarchy remain unchanged

No motion event triggers automatic sound or haptic feedback.
