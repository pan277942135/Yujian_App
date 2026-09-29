# Empty Home V2

Role: **Brand / Environment Baseline**
Status: **FROZEN V2 BASE + FROZEN VISUAL REVISION V2.2**

## Design Manager

Design audit and governance:

- `EXPERIENCE_AUDIT_V1.md`
- `design/governance/YuJian_Experience_Source_of_Truth_V1.md`
- `design/registry/experience_registry_v1.json`

## Frozen visual authority

Base authority:

- Core UI V1 canonical reference: `design/system/core_visual_v1/reference/empty_home_v2.png`
- Core UI V1 SHA-256: `30f95fc68b65d5a55552ba279753cac1fd979216679217377e43cea8e33cb85b`
- Immutable page source: `source/frozen/Empty_Home_Final_Design_V2.png`
- System authority: YuJian Core Visual System V1

Approved delta freeze:

- Revision: **V2.2**
- Contract: `spec/Empty_Home_Frozen_Visual_Revision_V2_2.md`
- Approved visual source filename: `晨雾湖畔_静待第一条鱼.png`
- Approved visual source SHA-256: `3071481ed7e58106381cdd5321267792491c21fd1a357e4362db1dad8e08e7ec`

V2.2 freezes the approved capture-button clarity/spacing and rod-line-bobber-water-contact
geometry. V2 remains authoritative for everything not explicitly changed by V2.2.

The historical V3 visual-fidelity closure document is not the current design authority after V2.2.

## Product state and navigation

The page is the existing `HomeScreen` EMPTY state, shown only when `fishRecords.isEmpty()`.
Login/session state does not choose Empty vs Normal. It reuses the production capture/identify,
album-picker, and account-entry callbacks; it does not introduce a second Home, camera, or
gallery flow.

## Visual contract

- quiet morning lake
- brand copy is the emotional focus
- rod/line/bobber/ripple support atmosphere
- shared `YuJianPrimaryCaptureButton`
- no dashboard / no empty-data utility tone
- V2.2 spacing/geometry delta is authoritative over the V2 base where explicitly specified

## Motion

Authority:

- `motion/Empty_Home_Motion_Spec_V2.md`
- `shared/contracts/motion_contract.json`

V2.2 explicitly retains the bobber and ripple timing values.

## Haptic

Authority:

- `haptic/Empty_Home_Haptic_Spec_V1.md`
- `shared/contracts/haptic_contract.json`

## Sound

Authority:

- `sound/Empty_Home_Sound_Spec_V1.md`
- `shared/contracts/sound_contract.json`

Current product decision: no automatic or interaction audio. The packaged `lake_morning.mp3`
resource is deferred and does not authorize runtime playback.

## Assets

- `shared/contracts/asset_manifest.json`
- `source/provenance/source_manifest.json`

## Runtime status

Runtime/evidence state is tracked separately in `status.json`. After the V2.2 visual revision,
Android runtime/evidence is currently pending revalidation; that does not change the design freeze.
