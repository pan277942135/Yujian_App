# SWOS Web Implementation Input Pack V1

**Task:** `YUJIAN_SWOS_WEB_IMPLEMENTATION_INPUT_PACK_AND_LIVE_SHA_CLOSURE_V1`  
**Status:** `SOURCE_SYNCED_EXACT_HEAD_CI_PENDING`  
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

## Live audit after product-main sync

- Design authority baseline: `Yujian_App/main@01c7aec0c0e8216dd76f314f380ae3e0496bd28b`.
- Product main: `Yujian/main@3c79c00db68ace94d8f6a7dcb4105d0026a09323`.
- Product PR #177 HEAD: `d8f3aabfab058d58cac84e19af095b0186df5f85` (post-sync merge commit).
- Comparison: PR #177 is **19 commits ahead and 0 behind** product main. The latest three main commits have been merged into the feature branch without rebase or force-push. Exact-head CI for the post-sync SHA is pending.
- Canonical control-plane binding: governance remains authoritative in `Yujian_App/main`; the separate product repository `Yujian` is admitted through the exact-SHA cross-repository manifest and source audit. Do not duplicate a competing control plane in `Yujian`.
- The previous exact-head CI run [39874124153](https://github.com/pan277942135/Yujian/actions/runs/39874124153) passed targeted validation and Chromium UI simulation on the *previous* SHA only. It is not reused as evidence for the new post-sync SHA.
- Merged infrastructure PR #180 still pins `cb9e3780afb5e2904e3dfe5148210a4ccc3747b4`; a product preview for the current head is `NOT_VERIFIED`.

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
