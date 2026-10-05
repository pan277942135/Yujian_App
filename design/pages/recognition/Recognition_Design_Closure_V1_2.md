# Recognition Design Closure V1.2

Status: **DESIGN FROZEN**

Scope: **Recognition Processing design authority only**

Runtime status: **ACTIVE_CLOSURE**  
Evidence status: **ACTIVE_CLOSURE**

This closure supersedes V1.1 as the current **design** entry point. It does not claim that Android Runtime or Runtime Evidence has passed the V1.2 gates.

## 1. Frozen product model

Recognition Processing contains exactly three user-visible states:

```text
图片识别中
→ 已定位到鱼体
→ 鱼种识别中
→ RESOLVE
→ Result
```

`RESOLVE` is a transition, not a fourth Processing state.

Authority:

- `spec/Recognition_State_Timeline_Spec_V1_3.md`

## 2. Frozen Visual State Set

No replacement Processing PNGs are generated for V1.2.

The existing four Frozen PNGs are retained byte-for-byte and remapped into the three-state product model:

| Product state | Frozen keyframe authority |
| --- | --- |
| 图片识别中 | `01_Capture_Transition_Frozen.png` Early + `02_AI_Understanding_Frozen.png` Late |
| 已定位到鱼体 | `03_Fish_Highlight_Frozen.png` |
| 鱼种识别中 | `04_Fish_Identifying_Frozen.png` |

Machine mapping:

- `design/Recognition_Processing_Visual_State_Set_V1_2.json`
- original SHA authority remains `design/reference_manifest.json`

The Early/Late pair belongs to one product state and must never be reconstructed as CAPTURED + DETECTING product states.

## 3. AI Edge Field / Layer Ownership

The AI Edge Field parent and the Layer / Component Ownership design area are frozen.

Current authority is the combined contract set:

- `processing/components/AI_Edge_Field_V1_Static_Shape_Spec.md`
- `processing/components/AI_Edge_Field_V1_Rendering_Contract.md`
- R01–R06 references under `processing/references/`
- existing Processing visual references
- runtime evidence only where a reference spec explicitly delegates final performance proof

R02–R06 do **not** require newly generated design images merely to satisfy a menu count.

## 4. Motion Authority

Current motion authority:

- `processing/motion/Recognition_Processing_Motion_Spec_V1_2.md`
- `processing/motion/AI_Edge_Field_V1_Motion_Contract.md`

V1.2 supersedes V1.1 for current product motion.

Frozen minimum visual beats:

- 图片识别中 ≥ 900ms
- 已定位到鱼体 ≥ 600ms
- 鱼种识别中 ≥ 1250ms
- RESOLVE = 200ms

Historical V1.1 documents remain repository history / compatibility material only and do not override current V1.2 motion authority.

## 5. Haptic / Sound

Recognition Processing V1.2 adds no automatic feedback:

- Haptic = **NONE**
- Sound = **NONE**

Authorities:

- `processing/haptic/Recognition_Processing_Haptic_Spec_V1.md`
- `processing/sound/Recognition_Processing_Sound_Spec_V1.md`

Downstream Result / Issue feedback is owned by those surfaces separately.

## 6. Degradation & Accessibility

Frozen:

- FULL / BALANCED / LITE
- Reduce Motion
- D0→D4
- Semantic Invariants

Parent authority:

- `processing/accessibility/Recognition_Degradation_Accessibility_V1.md`

## 7. Machine Contract

Current machine-readable design contract:

- `processing/contracts/Recognition_Processing_Contract_V1_2.json`

The old V1.1 machine contract is retained for historical compatibility only.

## 8. Runtime Evidence Contract

The evidence **requirements** are frozen, but evidence completion remains independent:

- `evidence/Recognition_Runtime_Evidence_V1_2.md`

Design freeze does not convert Runtime or Evidence to PASS.

## 9. Final design status

```text
01 State Timeline            FROZEN
02 Visual States             FROZEN
03 Layer & Components        FROZEN
04 Motion & Transition       FROZEN
05 Degradation & Access      FROZEN
06 Runtime Evidence Contract FROZEN

Behavior       FROZEN
Visual         FROZEN
Motion         FROZEN
Haptic         FROZEN / NONE
Sound          FROZEN / NONE
Assets         FROZEN

DESIGN         FROZEN
RUNTIME        ACTIVE_CLOSURE
EVIDENCE       ACTIVE_CLOSURE
```

No design area should be reopened solely because Runtime or Evidence has not yet closed.
