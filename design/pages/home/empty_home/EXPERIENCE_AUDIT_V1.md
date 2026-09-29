# Empty Home V2 — Experience Audit V1

Audit status: **COMPLETE WITH ONE ACTIVE VISUAL CLOSURE**
Governance: `design/governance/YuJian_Experience_Source_of_Truth_V1.md`

## 1. Executive result

Empty Home is the first YuJian reference module for full-experience governance.

Its Behavior, frozen Visual V2, Motion, Haptic, Sound policy, Assets, Android Runtime mapping and
runtime Evidence all have repository authority.

The only active closure is visual-fidelity work governed by
`spec/Empty_Home_Final_Visual_Fidelity_V3.md`. That document does not supersede the canonical
V2 frozen PNG. A future visual authority change requires a new canonical binary + manifest/hash.

## 2. Modality matrix

| Modality | Status | Authority | Audit result |
| --- | --- | --- | --- |
| Behavior | FROZEN | `spec/Empty_Home_Feature_Spec_V2.md` | EMPTY derives from no fish records; existing capture/album/account callbacks reused. |
| Visual | FROZEN | `design/system/core_visual_v1/reference/empty_home_v2.png` | Canonical V2 binary and SHA-256 registered. V3 is closure-only, not a replacement binary. |
| Motion | FROZEN | `motion/Empty_Home_Motion_Spec_V2.md` + `shared/contracts/motion_contract.json` | Bobber/ripple/cloud/sun/camera motion numerically defined; Reduce Motion defined. |
| Haptic | FROZEN | `haptic/Empty_Home_Haptic_Spec_V1.md` + `shared/contracts/haptic_contract.json` | Camera light impact; album platform light click; no entry/idle/environment haptics. |
| Sound | FROZEN | `sound/Empty_Home_Sound_Spec_V1.md` + `shared/contracts/sound_contract.json` | Explicit no-playback contract. Packaged lake audio is deferred and does not authorize playback. |
| Assets | FROZEN | `shared/contracts/asset_manifest.json` + `runtime_manifest.json` | Source/runtime derivative split and hashes are registered. |
| Runtime | FROZEN baseline | `shared/contracts/runtime_manifest.json` + Android Home implementation | API28 runtime gate passed on main at the recorded baseline. |
| Evidence | FROZEN baseline | `evidence/manifest.json` + `status.json` | Latest registered runtime-gate run is indexed; large video evidence remains an Actions artifact. |

## 3. Frozen visual authority

Canonical:

`design/system/core_visual_v1/reference/empty_home_v2.png`

SHA-256:

`30f95fc68b65d5a55552ba279753cac1fd979216679217377e43cea8e33cb85b`

Immutable page source:

`source/frozen/Empty_Home_Final_Design_V2.png`

The current `Empty_Home_Final_Visual_Fidelity_V3.md` is an **ACTIVE_CLOSURE contract**.
It may refine runtime fidelity against V2 but does not create a new frozen visual source by itself.

## 4. Interaction contract

- Empty Home is selected only when valid FishRecords are empty.
- Session/login state does not select Empty vs Normal Home.
- Camera uses the existing identify path.
- Album uses the existing picker path.
- Account/login uses the existing account path.
- No duplicate CameraX, picker or Home flow is permitted.

## 5. Motion contract

Frozen values include:

- Bobber: Y only, ±3 reference px, 4600 ms.
- Ripple: exactly one, 1.00→1.22, alpha 0.30→0, 3200 ms.
- Cloud: about 0.2 reference px/s.
- Sun particles: 4.8 s, 6–12 particles, alpha ≤0.18.
- Camera breath: ~5000 ms, scale ≤1.015.
- Camera gold rim: first ~3000 ms, ~1400 ms duration, ~9000 ms interval.
- Environmental phases are intentionally non-synchronous.
- Reduce Motion stops environmental looping while preserving the static scene.

## 6. Haptic contract

- Camera tap: Android light-impact equivalent, 20 ms semantic hint.
- Album tap: platform light-click convention.
- Page entry: none.
- Idle/bobber/ripple/environment animation: none.

No automatic haptic is permitted merely because the page appears or its atmosphere animates.

## 7. Sound contract

Current Empty Home V2 is intentionally silent.

`lake_morning.mp3` exists as a deferred packaged resource but is disabled and is not current sound
authority. Runtime must not play it until a future version explicitly freezes settings and playback rules.

## 8. Evidence baseline

Latest indexed Android CI runtime gate:

- run: `36386342604`
- main SHA: `94a99f9a7c848e4bb124c5c454dcf42b3236f74b`
- result: `success`
- Empty Home runtime artifact: `10955156190`
- artifact digest: `sha256:9edd3391deb6a11c8a9afb2f7a1248a79fe9d322ebd5c9f693d0a17dc57f683a`
- API: 28
- runtime classification: PASS

The evidence manifest also preserves the prior full visual-parity baseline for traceability.

## 9. Open closure

### EH-OPEN-01 — visual authority after V2

Repository state currently contains a V3 visual-fidelity closure document but no superseding canonical
V3 frozen PNG/manifest entry.

Therefore:

- V2 remains the only canonical pixel authority.
- Any newer approved visual adjustment must be archived as a new canonical visual version before
  Work or frontend treats it as source of truth.
- A screenshot/chat approval alone must not supersede V2.

## 10. Work / frontend handoff

A Work task implementing Empty Home must begin from this order:

1. this audit;
2. Feature Spec V2;
3. canonical V2 frozen visual + Visual Spec;
4. Motion V2;
5. Haptic V1;
6. Sound V1;
7. runtime/asset contracts;
8. Acceptance Criteria V2;
9. evidence manifest;
10. V3 active-closure document only for unresolved fidelity correction.

No implementation task may redesign a modality that is already FROZEN.
