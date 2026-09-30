# Normal Home V1

Role: **Home / Catch Baseline**  
Status: **DESIGN FROZEN**  
Design package: **Closure V1**

## Frozen visual authority

- Canonical reference: `design/system/core_visual_v1/reference/normal_home_v1.png`
- SHA-256: `6ab9d3348b4a9a7e77ddca3a06235b4991798a309bd3512cc6fb9ea7aeb1d377`
- Reference canvas: 1080 × 1920
- System authority: `design/system/core_visual_v1/YuJian_Core_Visual_System_V1.md`
- Page source registry: `source/frozen/source_manifest.json`

The canonical PNG is not duplicated into this page folder. The manifest binds this package to the immutable Core UI V1 binary by path, dimensions and SHA-256.

## Product state

Normal Home is shown when one or more valid FishRecords exist. Login/session state, loading state and statistics do not choose Empty vs Normal Home.

## Frozen hierarchy

1. morning-lake environment
2. real recent catch
3. memory/context
4. statistics
5. secondary navigation
6. primary capture action

Real catch media must remain visually above statistics. The page must not drift into a dashboard, HUD or collection-game hierarchy.

## Package authority

- Feature scope: `spec/Normal_Home_Feature_Spec_V1.md`
- Behavior: `spec/Behavior_Spec_V1.md`
- Visual: `spec/Visual_Spec_V1.md`
- Acceptance: `spec/Acceptance_Criteria_V1.md`
- Motion: `motion/Normal_Home_Motion_Spec_V1.md`
- Haptic: `haptic/Normal_Home_Haptic_Spec_V1.md`
- Sound: `sound/Normal_Home_Sound_Spec_V1.md`
- Assets: `assets/asset_manifest.json`
- Authority order: `authority/authority_map.json`
- Closure record: `DESIGN_PACKAGE_CLOSURE_V1.md`
- Machine-readable status: `status.json`

## Shared components

- `YuJianCatchHeroCard / HOME`
- `YuJianPrimaryCaptureButton`
- shared YuJian typography, color, spacing and radius tokens

## Background authority

Closure V1 does **not** register an independent `Morning_Lake_Master_V1` because no such frozen source exists in the current repository. Until a future source is formally added and versioned, Normal Home background authority is:

`normal_home_v1.png` + `BG_ENV_HERO` rules in Core Visual System V1.

No screenshot crop may be promoted into a reusable background master.

## Runtime boundary

Runtime closure remains separately documented in `RUNTIME_CLOSURE_V1.md`. This Design Package Closure does not redefine Android implementation, backend, model or worker behavior.
