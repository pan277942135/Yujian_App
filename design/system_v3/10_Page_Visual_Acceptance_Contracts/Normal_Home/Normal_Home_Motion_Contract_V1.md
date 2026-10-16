# Normal Home Motion Contract V1

**Status:** `FROZEN_PRESENTATION_RULES / RUNTIME_NOT_RUN`  
**Authorities:** existing Empty Home V2.2 motion contract, Normal Home Motion Spec V1, and Normal Home Motion Feedback Contract V1.

## Empty Home V2.2

The following values are inherited exactly from `design/pages/home/empty_home/shared/contracts/motion_contract.json`; this contract does not change the frozen visual or component motion source.

| Motion | Duration / cadence | Behavior |
|---|---:|---|
| Ripple | 3200 ms loop | center (562,1320) reference px; scale 1.00→1.22; alpha 0.30→0; below bobber/above water |
| Bobber | 4600 ms loop | Y-only ±3 reference px; no rotation, scale or X movement |
| Cloud | 60000 ms baseline cycle | 0.2 reference px/s, extremely subtle |
| Sun particle/beam | 4800 ms | 6–12 particles; max alpha 0.18 |
| Camera breathing | 5000 ms | maximum scale 1.015 |
| Gold-rim sweep | 1400 ms | first delay 3000 ms; repeat interval 9000 ms |

The established V2.2 motion asset and contract are sole authority for easing/interpolation details not stated in this table. Do not infer new movement from a still frame.

## Normal Home data state

- **Selected Hero idle breath:** existing 6000 ms period; scale 1.000→1.008→1.000 and Y 0→−2dp→0; no rotation or opacity. Transform stays within the stable outer card and touch bounds.
- **Neighbor cards:** user-drag only, using existing normalized-distance rule `scale=1−0.055q`, `translationY=4dp*q`, `alpha=1−0.12q`; no autonomous advance.
- **Capture camera:** inherits the existing shared capture button cadence, about 5000 ms breathing with scale ≤1.015; first rim sweep about 3000 ms, 1400 ms duration, next sweep 9000 ms after prior start. No Normal Home timing fork. The text CTA itself remains static.
- **Newly resolved card entry (this contract addition):** only when the first valid data snapshot resolves with no prior valid snapshot, apply a 160 ms opacity-only transition from 0 to 1, delay 0 ms, linear easing, once. Keep all layout, image, card scale, card position, hit bounds and text rows fixed. Do not replay for refresh, retained snapshots, media recovery, selection changes or subsequent Pager items. No per-field stagger and no page-wide choreography.
- **Other content:** no automatic statistic count-up, no environmental Normal Home loops, no image-recovery entrance, no page transition choreography, no sound, and no new page-authored haptic.

## Lifecycle and reduced motion

Decorative loops run only while the Home lifecycle is resumed. Re-check system motion preference on resume/settings change. When Reduce Motion or the platform animator scale disables motion: stop Hero breathing at neutral scale/offset, stop capture breathing/sweep, and render the new card entry immediately at opacity 1 (no transition). User-driven paging/scroll/navigation remains available. Empty Home uses its existing V2.2 Reduce Motion behavior; do not author separate timing or replace its motion assets.

## Runtime evidence

A future evidence run must record the APK SHA, contract version, profile, lifecycle and Reduce Motion setting; sample motion timestamps/values; include a neutral frame for visual comparison; and retain at least the existing 16-second motion capture requirement. This contract reports no runtime motion as tested.
