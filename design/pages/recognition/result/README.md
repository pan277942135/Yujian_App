# Recognition Result V1

Role: **Capture → Record Bridge**

Status: **DESIGN FROZEN — 3+2 STATE PACKAGE**

## Scope

Recognition Result contains five frozen product states:

Result:
- RESULT_HIGH
- RESULT_MEDIUM
- RESULT_LOW

Recovery:
- ERROR_NO_FISH
- ERROR_IMAGE_QUALITY

`TECHNICAL_FAILURE` remains a runtime-safe generic fallback under the global Recognition runtime contract. It is not one of the 3+2 frozen Result states.

## Visual authority

### State-specific authority

These five PNGs are the final state-level composition authority:

- `design/pages/recognition/design/05_Result_High_Frozen.png`
- `design/pages/recognition/design/06_Result_Medium_Frozen.png`
- `design/pages/recognition/design/07_Result_Low_Frozen.png`
- `design/pages/recognition/design/08_Error_No_Fish_Frozen.png`
- `design/pages/recognition/design/09_Error_Image_Quality_Frozen.png`

Dimensions/SHA are frozen in:

`design/pages/recognition/design/reference_manifest.json`

### System-level authority

`design/system/core_visual_v1/reference/recognition_result_v1.png`

defines the shared Result visual language and base capture-to-record composition.

Conflict rule:

**state-specific Frozen PNG > Result package spec > Core Visual System shared reference > runtime implementation**

The Core Visual reference must not override a state-specific difference shown in 05–09.

## Package entry points

- `DESIGN_PACKAGE_CLOSURE_V1.md`
- `spec/Recognition_Result_Feature_Spec_V1.md`
- `spec/Recognition_Result_State_Matrix_V1.md`
- `spec/Recognition_Result_Behavior_Spec_V1.md`
- `spec/Recognition_Result_Visual_Spec_V1.md`
- `motion/Recognition_Result_Motion_Spec_V1.md`
- `spec/Recognition_Result_Acceptance_Criteria_V1.md`
- `authority/authority_map.json`
- `review/Recognition_Result_Runtime_Alignment_Review_V1.md`
- `status.json`

## Product responsibility

Recognition Result is a lightweight confirmation and record bridge.

It may contain:
- real captured fish photo
- species confirmation / correction
- length
- weight
- location
- short catch note
- voice-note affordance when implemented
- save / continue-memory actions

It must not become:
- a Fish Guide detail page
- a media gallery
- a statistics page
- a detector/debug UI
- an achievement/game screen

## CTA contract

When species is resolved:

- `保存本次鱼获` → create FishRecord → Normal Home
- `继续记录记忆` → create FishRecord first → FishRecordDetail(recordId, initialSection=MEMORY)

A FishRecord must exist before memory-media operations begin.

Low-confidence pending-species handling is defined in the Behavior Spec and must not be silently replaced by a mandatory species-selection design.
