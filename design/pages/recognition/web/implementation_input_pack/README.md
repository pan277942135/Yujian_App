# SWOS Web Implementation Input Pack V1

**Task:** `YUJIAN_SWOS_WEB_IMPLEMENTATION_INPUT_PACK_AND_LIVE_SHA_CLOSURE_V1`
**Status:** `SWOS_V1_0_BOUND; SOURCE_SYNCED; EXACT_HEAD_CHROMIUM_PLAYBACK_PASS; CANDIDATE_DEPLOY_NOT_RUN; PRODUCTION_MASK_NOT_QUALIFIED; VISUAL_ACCEPTANCE_PENDING`
**Role:** A traceable input snapshot; it does not replace or redefine the main-adopted design contracts.

## Ownership

- **Design/control preparation:** maintain the live SHA snapshot, asset provenance, source binding and validation entrypoint.
- **Work B implementation:** sync the product branch, rerun Admission and exact-source CI, then implement and verify the real browser animation.
- **User:** final visual acceptance after a current-source playable preview is available.

## Start here

1. Read the current `AGENTS.md` and `CONTROL_PLANE_REGISTRY.yaml` in `pan277942135/Yujian_App` and follow its indexed admission/contract rules.
2. Read [the manifest](SWOS_WEB_IMPLEMENTATION_INPUT_MANIFEST_V1.json). Its contract list pins the expected Git blob SHA for every input.
3. Run `bash scripts/verify_swos_web_input_pack_live.sh --report` from the design-authority repository with network access to GitHub's public API. Use `DESIGN_REF=main` after this input-pack PR is adopted; use the input-pack branch only to validate the proposal.
4. Before implementation, run `bash scripts/verify_swos_web_input_pack_live.sh --strict`. A strict block must be resolved, not bypassed.
5. After syncing the product branch to the current product `main`, refresh the live source audit fields and re-run Admission and CI against the resulting exact product SHA.

## Live source audit (2026-10-23; refresh before every Work run)

- Canonical design authority: `pan277942135/Yujian_App/main@da9976090021232f0eec936bc4d30f9fa59d90f7`. The control plane was revalidated from main before this evidence refresh.
- Product main: `pan277942135/Yujian/main@6a2064c2a02a76437b6e09a5241bc199cfa05048`.
- Product PR #177 HEAD: `7083ae691d424b4f6d5680fb77f019c9b437505b` (**26 ahead / 0 behind**; merge base is product main). PR #177 remains Draft/Open/Unmerged.
- Exact-head CI [run 39956274544](https://github.com/pan277942135/Yujian/actions/runs/39956274544) passed on that SHA. The CI artifact contains the 15 seek-point PNGs, geometry trace, deterministic P4 repeat-frame comparison, the 3650ms fixture-only WebM recording, and the 9 actual responsive-page screenshots from the separate Chromium UI smoke. It proves fixture rendering and controls only; it does not exercise live model inference, a deployed candidate, production Mask quality, device performance, or visual acceptance.
- The no-traffic deployment configuration is proposed in PR #182 at head `9d10dfce6d8f22c43917c1af849202f0c4be326f`, targeting product SHA `7083ae691d424b4f6d5680fb77f019c9b437505b`. PR #182 is open/unmerged and has not deployed a candidate. The main workflow still actually pins `4de911dc497c150545d938b60b2694f3c4723e37`; no current-source preview Revision, health SHA, or live browser run is verified. Its deploy trigger and preflight both require `main` (push on `main` or `workflow_dispatch` on `refs/heads/main`), so an open PR branch cannot produce the candidate Revision; the main-only tagged no-traffic path is awaiting the configuration PR decision.
- The canonical control plane remains in Yujian_App; do not create a competing registry in Yujian.
- **Same-request SAM adapter implemented; runtime not yet exercised.** `POST /api/product/catch-recognition/infer` passes that request's detector primary BBox and source SHA into the existing `generate_fish_cutout` pipeline. `CATCH_SWOS_SAM_CANDIDATE_ENABLED` defaults off; the proposed no-traffic candidate alone enables it. A returned mask remains `REVIEW_PROXY`; failures degrade to `BBOX_ONLY`. The debug route is not exposed to the browser, and no duplicate segmentation service or detector pass is added.
- Production Mask remains `NOT_QUALIFIED` until reviewed ground-truth masks, boundary metrics, difficult-case analysis and visual sign-off exist. Browser render-frame time is measured separately from SAM/API latency.
- The unrestricted repository suite remains blocked by the existing metadata-access review. A previous unscoped PR #182 workflow attempt had already completed its broad test step before cancellation took effect; it is not used as acceptance evidence. The scoped PR #182 workflow now skips that step and records the review boundary.

## SWOS Web Hi-Fi Motion Technical Contract V1.0

The implementation authority is [SWOS Web Hi-Fi Motion Technical Contract V1.0](../SWOS_WEB_HIFI_MOTION_TECHNICAL_CONTRACT_V1_0.md) and its [machine-readable contract](../contracts/SWOS_WEB_HIFI_MOTION_TECHNICAL_CONTRACT_V1_0.json). This contract binds the existing Web V2, SWOS 15-frame, shared-photo-transition, RR09 V3 and existing SAM experiment pipeline. It requires a real playable 3650ms browser preview, frame stepping/seeking, shared photo geometry, Chinese-only species title, nine viewports, multi-scene photos and actual frame-time evidence.

## Frozen behavior that Work must not reinterpret

- Processing: 2800ms; RR09: 850ms; nominal total: 3650ms.
- 15 samples: P0–P7 and R0/R0.5/R1–R5. R0.5 is a geometric midpoint at global 2900ms, not an added wait.
- The same source photo and one shared transform must continue through P7 → R0 → R0.5 → R1.
- Species name is **Chinese only**; do not render an English species-name row.
- The 3650ms visual target is not a fake inference countdown. If real inference is late, Processing may extend; no success reveal without a real eligible HIGH result.
- Water caustics must feel like sunlight refracting across water. Do not copy the Hongmeng assistant wake-up visual language, HUD frames, scanlines or radar motifs.
- No qualified production fish Mask is currently evidenced. A manual contour is a review proxy, never a production Mask. Without trusted geometry, do not draw a precise fish outline.

## Private assets

The original 1536×1024 Web reference and historical 1672×941 storyboard poster were byte-verified from the user's Library for this audit. Their SHA-256 values and permission states are in the manifest. They are deliberately **not committed to the public repository**; publication permission remains unconfirmed.

The separate `208758.jpg` processing photo is referenced by the existing asset manifest, but its raw bytes were not available in the current Library search and are not included. Do not claim its bytes or Mask quality are verified until the authorized private original is supplied.

## What CI does and does not prove

The exact-head CI pass is bound to the recorded product SHA and covers safe targeted checks plus Chromium UI simulation. It does not prove a production Mask, real-photo model inference, cloud save, pixel parity, animation quality or user acceptance. A preview is valid only when its deployed revision and health-reported SHA match the current reviewed PR head.


## Cross-repository admission binding

The canonical Work control plane is `pan277942135/Yujian_App/main`, pinned through `AGENTS.md` and `CONTROL_PLANE_REGISTRY.yaml`. The implementation source is maintained in the separate `pan277942135/Yujian` repository. This input pack provides the cross-repository source binding: exact PR head, live main comparison, exact-head CI, and pinned design contract blob SHAs. The product repo must not create a competing local control plane. Admission is complete only when the canonical control-plane checks and the live source audit both pass for the exact referenced SHA.


## Private handoff bundle V2

The verified private bundle is `SWOS_WEB_PRIVATE_VISUAL_INPUTS_V2.zip`, SHA-256 `2ffeb0014df59d646bf41c90ea98c8514185e7e96f8422e7e488b6e51d991db8`. It includes the user-confirmed Web reference, historical storyboard and actual source photo. Keep it private.

## 15-frame rendered references

The current 15-frame authority contract is pinned in the manifest. The actual 15 individual PNGs are **not present in the available private handoff**; the available storyboard is a historical 14-frame V0.3 reference. Do not claim that the historical poster is the current 15-frame set. Recover the previously rendered 15-frame set or create a separately labeled review-only set tied to the current 15-frame contract. Production Mask remains unqualified.


## Private 15-frame review proxy V3

A private review bundle has now been generated from the byte-verified source photo and the main-adopted 15-frame/shared-geometry contracts: `SWOS_WEB_15_FRAME_REVIEW_PROXY_V3.zip`, SHA-256 `5227986290402f3f4fe45872efd6d433a92923a2c27c67a7af00b3aac3247fe2` (28,939,939 bytes). It contains 15 individual 1080×1920 samples plus contact sheets and the private source assets. The frames are **REVIEW_PROXY_NOT_FROZEN**: they are not authoritative artwork, not a playable animation, and not evidence of browser pixel parity. Their manual fish contour is explicitly `MANUAL_REFERENCE_ONLY_NOT_PRODUCTION_MASK`. The archive is provided privately outside the public repository.
