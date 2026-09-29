# Empty Home V2 — Experience Audit V1

Audit status: **COMPLETE / FROZEN**  
Closure marker: **`EMPTY HOME · FROZEN`**  
Governance: `design/governance/YuJian_Experience_Source_of_Truth_V1.md`

## 1. Executive result

Empty Home has complete repository authority for Behavior, Visual, Motion, Haptic, Sound policy, Assets, Android Runtime and Runtime Evidence.

The current visual authority is **V2 base + V2.2 approved delta**. The previous V3 fidelity-closure document is deprecated and is not current authority.

`spec/Empty_Home_Spec_Closure_V1.md` closes the final four governance gaps:

- State Relationship
- Component Ownership
- Layer / Z-order
- Design Manager Runtime Evidence

There are no open design items.

## 2. Modality matrix

| Modality | Status | Authority | Audit result |
| --- | --- | --- | --- |
| Behavior | FROZEN | `spec/Empty_Home_Feature_Spec_V2.md` + Closure V1 | Empty derives only from valid FishRecord count; existing capture/album/account callbacks are reused. |
| Visual | FROZEN | V2 base + V2.2 delta | V2 remains base pixel authority; V2.2 owns approved CTA and fishing-composition deltas. |
| Motion | FROZEN | `motion/Empty_Home_Motion_Spec_V2.md` | Bobber/ripple/cloud/sun/camera motion and Reduce Motion are defined. |
| Haptic | FROZEN | `haptic/Empty_Home_Haptic_Spec_V1.md` | No automatic entry/environment haptic; user actions follow frozen semantics. |
| Sound | FROZEN | sound contract | No automatic or interaction playback. |
| Assets | FROZEN | asset/runtime manifests | Source/runtime split and hashes are registered. |
| Runtime | FROZEN / PASS | runtime manifest + production implementation | Required API28 gate passed at current main authority. |
| Evidence | FROZEN / PASS | `evidence/manifest.json` | V2.2 required runtime evidence is registered and passing. |

## 3. Visual authority

Base:

- `design/system/core_visual_v1/reference/empty_home_v2.png`
- SHA-256: `30f95fc68b65d5a55552ba279753cac1fd979216679217377e43cea8e33cb85b`

Approved delta:

- `spec/Empty_Home_Frozen_Visual_Revision_V2_2.md`
- approved source SHA-256: `3071481ed7e58106381cdd5321267792491c21fd1a357e4362db1dad8e08e7ec`

V2.2 supersedes V2 only for capture CTA clarity/spacing and rod-line-bobber-water-contact composition. The historical V3 document is **DEPRECATED**.

## 4. State Relationship

Empty Home and Normal Home are two content states of a single HomeScreen.

- 0 valid FishRecords → Empty
- ≥1 valid FishRecord → Normal
- authentication/session state is orthogonal
- first save / last deletion changes state through the same authoritative record collection
- no duplicate Home, camera, picker or navigation flow is allowed

## 5. Component Ownership

Shared system authority:

- Background System V1 / BG_ENV_HERO
- Empty-specific Sunrise Hero master selection
- Primary Capture Button / home_primary_capture
- Color & Typography
- Spacing & Radius
- existing production callbacks

Empty Home owns its brand-header composition, Hero composition and fishing/water-contact composition.

The brand header is not TopNavigation V1.

## 6. Layer / Z-order

Machine-readable authority: `shared/contracts/layer_contract.json`.

Critical frozen rules:

- water surface occludes the submerged bobber;
- ripple remains a single water-surface effect below the exposed bobber;
- line begins at rod tip and finishes behind/below the bobber contact;
- the complete bobber must not be rendered as a sticker above the water;
- native UI and Camera CTA remain above environment layers.

## 7. Motion / Haptic / Sound

Motion remains numerically frozen by Motion V2. Reduce Motion stops nonessential looping while preserving semantics.

No automatic haptic occurs on page entry or idle environmental motion.

Empty Home remains silent; packaged lake audio does not authorize playback.

## 8. Runtime Evidence

Latest required pass:

- Android CI run: `36514649405`
- main SHA: `38ba0e5c02731b99dcea825f88d4da9b48e11b81`
- empty-home-v2 runtime job: `109235222108`
- API: `28`
- result: **PASS**
- artifact: `11011440570`
- artifact digest: `sha256:a63234258d02fd0487e01f49e78d9299f14971419be49f76cac04f5bad97969a`

V2.2 evidence uses a hybrid model: valid V2 pixel parity for unchanged areas and frozen geometry/runtime evidence for approved V2.2 delta regions.

## 9. Closure result

| Closure item | Result |
| --- | --- |
| State Relationship | CONFIRMED |
| Component Ownership | CONFIRMED |
| Layer / Z-order | CONFIRMED |
| Runtime Evidence | PASS |

**Blocking open items: 0**

# `EMPTY HOME · FROZEN`

## 10. Work / frontend handoff

Implementation work must begin from:

1. `spec/Empty_Home_Spec_Closure_V1.md`
2. Feature Spec V2
3. V2 base visual
4. V2.2 delta contract
5. layer contract
6. Motion / Haptic / Sound contracts
7. runtime and asset manifests
8. Acceptance Criteria
9. evidence manifest

Existing frozen modalities must not be redesigned during runtime parity work.
