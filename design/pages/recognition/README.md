# YuJian Recognition Processing Design Package V1.2

Status: **DESIGN FROZEN**

Runtime: **ACTIVE_CLOSURE**  
Evidence: **ACTIVE_CLOSURE**

## Current design entry point

`Recognition_Design_Closure_V1_2.md`

This V1.2 closure is the current Recognition Processing design authority.

## Frozen product flow

```text
图片识别中
→ 已定位到鱼体
→ 鱼种识别中
→ RESOLVE
→ Recognition Result
```

Exactly three user-visible Processing states are allowed. RESOLVE is a transition.

## Visual authority

The existing four Frozen Processing PNGs are retained byte-identically and form the V1.2 three-state Keyframe Set:

- `design/01_Capture_Transition_Frozen.png` — 图片识别中 / Early
- `design/02_AI_Understanding_Frozen.png` — 图片识别中 / Late
- `design/03_Fish_Highlight_Frozen.png` — 已定位到鱼体
- `design/04_Fish_Identifying_Frozen.png` — 鱼种识别中

State-set mapping:

- `design/Recognition_Processing_Visual_State_Set_V1_2.json`

Binary hashes remain frozen in:

- `design/reference_manifest.json`

## Active design authorities

- State Timeline: `spec/Recognition_State_Timeline_Spec_V1_3.md`
- Visual: `processing/spec/Recognition_Processing_Visual_Spec_V1_1.md`
- Motion: `processing/motion/Recognition_Processing_Motion_Spec_V1_2.md`
- Edge Field motion: `processing/motion/AI_Edge_Field_V1_Motion_Contract.md`
- Degradation / Accessibility: `processing/accessibility/Recognition_Degradation_Accessibility_V1.md`
- Haptic: `processing/haptic/Recognition_Processing_Haptic_Spec_V1.md` — NONE
- Sound: `processing/sound/Recognition_Processing_Sound_Spec_V1.md` — NONE
- Machine contract: `processing/contracts/Recognition_Processing_Contract_V1_2.json`
- Runtime Evidence requirements: `evidence/Recognition_Runtime_Evidence_V1_2.md`

## Recognition Result

Recognition Result remains a separate design package under `result/`. Processing does not redefine Result High / Medium / Low or Issue surfaces.

## Compatibility

V1.1 Recognition Processing contracts remain in the repository for historical/runtime compatibility. They are not the current product-state or motion authority where V1.2 explicitly supersedes them.

## Runtime boundary

Design is frozen. Android Runtime and Evidence continue to close independently. Runtime gaps, runner issues, evidence gaps, or parity failures must not silently reopen the V1.2 design authority.
