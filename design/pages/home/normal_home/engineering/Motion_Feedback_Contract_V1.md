# Normal Home — Motion, Haptic and Sound Contract V1 (P1)
Status: ENGINEERING_ACTIVE / DESIGN_MOTION_FROZEN / RUNTIME_PROOF_REQUIRED
Visual direction: alive, not animated. This package interprets but does not redesign Motion Spec V1, Haptic Spec V1, Sound Spec V1 or shared capture motion.

## Authorized motion timeline

| Element | Trigger | Authoritative motion | Stop/restore | Evidence |
|---|---|---|---|---|
| Selected Hero idle | lifecycle RESUMED, decorative motion allowed | 6000ms period; scale 1.000 → 1.008 at t3000ms → 1.000 at t6000ms; y 0 → -2dp → 0; no rotation or opacity animation | pause on lifecycle PAUSED/STOPPED; neutral if Reduce Motion | instrumented clock values plus 0/3/6/9/12s video frames |
| Nonselected neighbor at normalized pager distance q in [0,1] | user horizontal drag | scale=1-0.055q; translationY=4dp*q; alpha=1-0.12q; horizontal translation direct user gesture | settle naturally, no autonomous swipe | pager gesture and interpolated state samples at q0/0.5/1 |
| Capture button idle | lifecycle RESUMED, decorative motion allowed | about 5000ms breathing; max scale <=1.015; first gold sweep starts about 3000ms, lasts about 1400ms, next sweep 9000ms after previous start | neutral and sweep hidden when Reduce Motion; no other-page variant | 16s real video plus trace of first/second sweep |
| Whole page/lake/stats | none | no independent environment animation, no counts-up, no transition choreography | none | no unauthorized loops |

### Units and a known implementation mismatch to test

Hero y amplitude is **2 Android dp**, not 2 reference physical px multiplied by width scale. The current NormalHomeContent.kt helper uses -2f*referenceScale and later converts to dp; these are not equivalent across widths. Acceptance is based on frozen dp spec; report observed dp at 360/393/480 logical widths and repair implementation if outside prescribed -2dp behavior, without rewriting the motion design. Adjacent translationY is likewise specified in **dp**. Shared capture motion uses the captured inherited engine and MUST NOT introduce an independent clock variant for Normal Home.

The animation system must not hide a temporarily unavailable card, move other page components, resize Hero outer layout frame, or affect hit targets. The visual transform occurs inside stable card bounds. No page wide synchronized loops.

## Lifecycle and accessibility contract

Decorative animations run only while page lifecycle state is RESUMED and reduced motion is not requested. When settings disable animation (including animator-duration-scale 0 or equivalent user/platform motion reduction), effects cease to neutral state and remain stopped while settings hold. Verify toggle without force-closing if supported, then screen pause/resume and one-record/two-record transitions. User-driven gestures and navigation still function under reduced motion; no decorative overshoot.

If platform API offers no direct Reduce Motion signal, document the exact Android system settings and detection mechanism used; do not assume the settings can be read once and never change. On resume, re-evaluate applicable motion policy.

## Haptic: FROZEN

Page entry, data resolution, background, selected Hero idle, Pager drag/settle, Hero tap, stats, avatar, media failure and refresh: NONE of page-authored haptics. Primary Capture: exactly the inherited shared light-impact convention per user tap (not a new custom pattern). Accessible system feedback is not prohibited. Verify no unexpected duplication from wrapper listeners.

## Sound: FROZEN NONE

No page-authored ambient lake audio, music, page entry, stats, hero, pager, CTA, capture UI sound, refresh or failure audio. Muting system audio must never affect core features. Acceptance is no audio player/soundpool/page-audio asset wiring or playback event authored by Normal Home; do not invent sound levels or audio deliverables.

## Verification method

M01 Hero keyframe trace with paused animation clock, q=0 and no pager movement.
M02 q0/0.5/1 selected/neighbor interpolation trace; vertical scroll does not steal horizontal intent.
M03 capture 0–16s real-device recording shows first and second sweep when motion allowed; frame sampling only proves visible behavior, timestamped values verify cadence.
M04 reduce motion enabled: scale=1, Y=0, sweep=off, swipe still works.
M05 background/foreground and state changes: no loop leaks or reentry bounce.
M06 exactly one light impact per Capture tap, no page-authored haptics elsewhere (instrument/mock interface plus device spot check).
M07 no page audio output or new sound resources; assert absence of Normal Home-specific playback wiring.
M08 one-record state no adjacent transform; idle breath only if allowed.
If a target cannot capture real motion, status BLOCKED_INFRA, not VISUAL_PASS. Record APK HEAD, device profile, capture duration, mp4 SHA and time points.
