# Recognition Processing — Design Frozen V1.2

Current design source of truth for Recognition Processing presentation.

## Authority order

1. `../Recognition_Design_Closure_V1_2.md`
2. `../spec/Recognition_State_Timeline_Spec_V1_3.md`
3. `../design/Recognition_Processing_Visual_State_Set_V1_2.json`
4. `spec/Recognition_Processing_Visual_Spec_V1_1.md`
5. `motion/Recognition_Processing_Motion_Spec_V1_2.md`
6. `motion/AI_Edge_Field_V1_Motion_Contract.md`
7. `accessibility/Recognition_Degradation_Accessibility_V1.md`
8. `haptic/Recognition_Processing_Haptic_Spec_V1.md`
9. `sound/Recognition_Processing_Sound_Spec_V1.md`
10. `contracts/Recognition_Processing_Contract_V1_2.json`
11. `../evidence/Recognition_Runtime_Evidence_V1_2.md`

## Three-state model

```text
图片识别中
→ 已定位到鱼体
→ 鱼种识别中
→ RESOLVE
→ Result
```

The first state uses two Frozen visual keyframes (Early/Late). This does not create two product states.

## Current motion

V1.2 motion supersedes V1.1 for current product motion:

- 图片识别中 ≥ 900ms
- 已定位到鱼体 ≥ 600ms
- 鱼种识别中 ≥ 1250ms
- RESOLVE = 200ms

State changes preserve one continuous Edge Field motion clock.

## Feedback

- Haptic: NONE
- Sound: NONE

## Runtime boundary

This package freezes design authority only. Detector, crop, classifier, confidence semantics, production routing, Runtime closure, and Evidence PASS remain separate concerns.

Historical V1.1 files remain compatibility/history and must not override V1.2 where V1.2 defines the current authority.
