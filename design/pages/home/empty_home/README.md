# Empty Home V2

Role: **Brand / Environment Baseline**  
Status: **EMPTY HOME · FROZEN**

## Design Manager

Spec closure authority:

- `spec/Empty_Home_Spec_Closure_V1.md`
- closure marker: **`EMPTY HOME · FROZEN`**
- design audit: `EXPERIENCE_AUDIT_V1.md`
- governance: `design/governance/YuJian_Experience_Source_of_Truth_V1.md`
- registry: `design/registry/experience_registry_v1.json`

The closure explicitly freezes:

1. State Relationship
2. Component Ownership
3. Layer / Z-order
4. Runtime Evidence

## Frozen visual authority

Base authority:

- Core UI V1 canonical reference: `design/system/core_visual_v1/reference/empty_home_v2.png`
- Core UI V1 SHA-256: `30f95fc68b65d5a55552ba279753cac1fd979216679217377e43cea8e33cb85b`
- immutable page source: `source/frozen/Empty_Home_Final_Design_V2.png`

Approved delta freeze:

- revision: **V2.2**
- contract: `spec/Empty_Home_Frozen_Visual_Revision_V2_2.md`
- approved visual source filename: `晨雾湖畔_静待第一条鱼.png`
- approved visual source SHA-256: `3071481ed7e58106381cdd5321267792491c21fd1a357e4362db1dad8e08e7ec`

V2.2 freezes the capture-button clarity/spacing and rod-line-bobber-water-contact geometry. V2 remains authority for everything not explicitly changed by V2.2. The historical V3 fidelity-closure document is deprecated.

## State relationship

Empty Home and Normal Home are states of the same `HomeScreen`.

- valid FishRecord count = 0 → Empty Home
- valid FishRecord count ≥ 1 → Normal Home
- guest/login state does not choose Empty vs Normal
- capture, album and account flows reuse the production callbacks; no duplicate navigation or picker stack

Full contract: `spec/Empty_Home_Spec_Closure_V1.md`.

## Component ownership

Shared:

- Background System V1 / `BG_ENV_HERO`
- `Morning_Lake_Sunrise_Hero_V1` for Empty Home
- Primary Capture Button / `home_primary_capture`
- Color & Typography
- Spacing & Radius
- production capture / album / account callbacks

Page-owned:

- Empty Home brand-header composition
- Hero copy / gold ending
- rod / line / bobber / water-contact / ripple composition
- Empty-specific scene layout and atmosphere relationship

The brand header is not `TopNavigation V1`.

## Layer / Z-order

Machine-readable authority:

- `shared/contracts/layer_contract.json`

The contract explicitly freezes water-surface occlusion of the submerged bobber, line-to-contact geometry, ripple placement and Camera CTA top-level action priority.

## Motion

Authority:

- `motion/Empty_Home_Motion_Spec_V2.md`
- `shared/contracts/motion_contract.json`

V2.2 retains the frozen bobber and ripple timing.

## Haptic

Authority:

- `haptic/Empty_Home_Haptic_Spec_V1.md`
- `shared/contracts/haptic_contract.json`

## Sound

Current decision: no automatic or interaction audio. Any packaged lake audio remains deferred and is not runtime authority.

## Runtime Evidence

Latest required revalidation: **PASS**

- main SHA: `38ba0e5c02731b99dcea825f88d4da9b48e11b81`
- Android CI: `36514649405`
- runtime job: `109235222108`
- gate: `empty-home-v2`
- API: `28`
- artifact: `11011440570`
- digest: `sha256:a63234258d02fd0487e01f49e78d9299f14971419be49f76cac04f5bad97969a`

Evidence authority: `evidence/manifest.json`.

The V2.2 gate uses valid V2 pixel parity for unchanged regions plus V2.2 frozen contract evidence for the revised CTA and fishing-composition regions.

## Change rule

After this closure, parity / defect work may repair runtime fidelity without reopening design. Any intentional design change requires a new explicit authority version.
