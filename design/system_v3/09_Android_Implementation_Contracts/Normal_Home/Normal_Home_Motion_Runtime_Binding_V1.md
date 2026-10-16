# Normal Home Motion Runtime Binding V1

**Status:** FROZEN_IMPLEMENTATION_GUIDANCE  
**Design source:** design/system_v3/10_Page_Visual_Acceptance_Contracts/Normal_Home/Normal_Home_Motion_Contract_V1.md  
**Runtime validation:** NOT_RUN

The design motion contract remains the sole timing authority. Android maps these behaviors to runtime motion without changing duration, delay, easing source, repeat, spatial bounds, or trigger.

## Empty Home V2.2 Inherited Motion

| Element | Duration / cadence | Delay | Runtime behavior | Easing / repeat |
|---|---:|---:|---|---|
| Ripple | 3200ms loop | Inherited V2.2 contract | Scale 1.00→1.22, alpha 0.30→0; below bobber and above water | Inherit exact V2.2 interpolation; loop |
| Bobber | 4600ms loop | Inherited V2.2 contract | Y-only ±3 reference px; no rotation, scale or X movement | Inherit exact V2.2 interpolation; loop |
| Cloud | 60000ms baseline cycle | Inherited V2.2 contract | 0.2 reference px/s, extremely subtle | Inherit exact V2.2 interpolation; loop |
| Sun particle/beam | 4800ms | Inherited V2.2 contract | 6–12 particles; maximum alpha 0.18 | Inherit exact V2.2 interpolation; repeat per V2.2 contract |
| Camera breathing | 5000ms | Inherited shared capture contract | Scale ≤1.015 | Inherit shared capture contract; loop |
| Gold-rim sweep | 1400ms | First delay 3000ms | Sweep; next sweep starts 9000ms after prior start | Inherit shared capture contract; repeat |

## Normal Home Data State

| Element | Duration / cadence | Delay | Runtime behavior | Easing / repeat |
|---|---:|---:|---|---|
| Selected Hero idle breath | 6000ms period | 0 unless existing source specifies otherwise | Scale 1.000→1.008→1.000; Y 0→−2dp→0; no rotation/opacity; remain inside stable card/touch bounds | Inherit existing Normal Home Motion Spec; loop only while resumed |
| Neighbor cards | User gesture driven | None | scale=1−0.055q, translationY=4dp*q, alpha=1−0.12q | No autonomous repeat or auto-advance |
| First newly resolved card | 160ms | 0ms | Opacity 0→1 only, once and only without a prior valid snapshot | Linear; no repeat |
| Avatar state replacement | Event driven | None | Keep the same avatar frame; LOADING uses the approved placeholder/overlay; success or fallback replaces content in place | Source defines no independent easing or timed fade; do not invent one; no repeat |
| Capture camera | About 5000ms | Inherited capture-button delay | Scale ≤1.015; rim sweep inherited from the shared capture master | Inherit shared capture contract; loop while resumed |
| CTA text | None | None | Static | No repeat |

## Lifecycle and Reduce Motion

- Decorative loops run only while Home is resumed.
- Re-read platform motion preference on resume/settings change.
- When Reduce Motion is enabled or platform animator scale disables motion: hold Hero at scale 1.000 / Y 0, stop capture breathing and sweep, render first-card opacity at 1 immediately, and stop decorative loops.
- User-driven paging, scrolling and navigation remain available.
- Empty Home uses its exact V2.2 reduced-motion behavior. Do not introduce a second timing source.
- Runtime motion evidence records contract version, APK/source SHA, profile, lifecycle, Reduce Motion setting and timestamps/values; a neutral frame and the existing minimum 16-second capture are required.
