# RR09 Motion V2 Android Timeline Compatibility Report

**Work:** `YUJIAN_RR09_GOLD_TRANSFER_REVEAL_ANDROID_RUNTIME_V1`
**Status:** Android implementation compatibility decision · V1
**Authority base:** `main` at `a5a83781381078afeb62466c3c3e76451e2b15ca` (PR #179 merge)
**Scope:** Resolve only the Android clock origin for RR09 Motion V2. The six visual phases, ordering, durations, trigger policy, photo policy, and visual assets remain unchanged.

## Conflict

The frozen Motion V2 spec and machine contract define T0 as the start of an existing 200 ms Processing `RESOLVE` window. They place photo continuity in that interval and set the remaining phases at 200–850 ms.

The effective Recognition Processing State Machine V3.1 has no `RESOLVE` state. After `CLASSIFYING` satisfies its 250 ms minimum and the real pipeline produces a result, `RESULT_READY` is a zero-duration terminal handoff to Recognition Result. Adding a 200 ms Processing delay would create an unauthorized wait and conflict with the active state machine.

The Android implementation also has no 200 ms `RESOLVE` state in `RecognitionVisualStateController.DEFAULT_MIN_VISIBLE_MS`; it contains only the frozen `CAPTURED`, `DETECTING`, `OUTLINE`, and `CLASSIFYING` minimum beats. The old `RESOLVE` clock origin therefore cannot be used as an Android event timestamp.

## Android compatibility rule V1

1. The real recognition pipeline retains its existing minimum visual beats and result semantics.
2. When the state machine reaches `RESULT_READY`, Android dispatches to Result immediately. No extra Processing state, timeout, or wait is introduced.
3. **Android T0 is the monotonic timestamp of the first presented Recognition Result frame.** The Result controller samples one monotonic clock and evaluates the complete 0–850 ms reveal from that origin.
4. Photo continuity occupies Result elapsed time 0–200 ms. The photo overlay begins from the last measured Processing photo viewport and resolves into the existing Result Hero during this interval; this does not delay navigation.
5. The remaining frozen intervals remain 200–380 ms gold transfer, 380–550 ms title formation, 550–680 ms title reveal, and 680–850 ms afterglow fade.
6. Result controls are interactive at route entry. The animation clock never gates saving, editing, navigation, or persistence.

This is an Android clock-origin adapter only. It does not rewrite Motion V2's browser timeline or claim that the web prototype is Android runtime evidence. Where the frozen design wording names a Processing `RESOLVE` event that does not exist in the effective Processing state machine, this report is the implementation-side compatibility record for Android acceptance.

## Impact and protected behavior

- No Processing state or visual beat is added or lengthened.
- Model inference, confidence mapping, routing, and result persistence are unchanged.
- Motion V2 remains HIGH-only, one-shot per recognition session and attempt, and geometry-trust gated.
- Missing or untrusted subject geometry uses the specified restrained opacity-only result-title fallback.
- Reduce Motion removes spatial travel, particles, scale interpolation, and title sweep, with the species name readable immediately.
- The total Android reveal target remains 850 ms from first Result presentation, then drawing stops.

## Regression requirements

| Check | Expected result |
|---|---|
| Real pipeline reaches `RESULT_READY` | Result navigation is initiated immediately after the existing `CLASSIFYING` minimum is met. |
| Processing waits after real result | No new 200 ms delay is observed. |
| Result frame clock | First presented Result frame is exactly elapsed 0 for deterministic tests. |
| Photo transition | Intermediate frames occur from 0–200 ms and use one source photo identity. |
| Motion boundaries | 200, 380, 550, 680, and 850 ms select the frozen phase boundaries. |
| CTA at 300 ms | The existing save callback runs without waiting for animation completion. |
| After 850 ms | No active frame clock, particles, title sweep, or fish glow remains. |

## Sources checked

- `design/pages/recognition/result/motion/Recognition_Result_Motion_Spec_V2.md`
- `design/pages/recognition/result/motion/recognition_result_motion_contract_v2.json`
- `design/system_v3/10_Page_Visual_Acceptance_Contracts/Recognition_Result/Recognition_Result_Motion_Contract_V2.md`
- `design/system_v3/08_Page_Contracts/recognition/processing/State_Machine_V3_1.json`
- `app/src/main/java/com/yujian/ai/ui/recognition/RecognitionVisualStateController.kt`
- `app/src/main/java/com/yujian/ai/ui/screens/RecognizingScreen.kt`

**Verification status:** Conflict and compatibility behavior documented. Automated timing and navigation regressions remain `NOT_RUN` until implementation tests execute.
