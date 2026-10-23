# SWOS Web Implementation Input Pack V1

**Task:** `YUJIAN_SWOS_WEB_IMPLEMENTATION_INPUT_PACK_AND_LIVE_SHA_CLOSURE_V1`  
**Status:** `PREPARED_SOURCE_SYNC_REQUIRED`  
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

## Live audit at package creation

- Design authority baseline: `Yujian_App/main@01c7aec0c0e8216dd76f314f380ae3e0496bd28b`.
- Product main: `Yujian/main@3c79c00db68ace94d8f6a7dcb4105d0026a09323`.
- Product PR #177 HEAD: `32b9463887c6620b32a3dfdd986eccc6ff4a569b`.
- Comparison: PR #177 is **18 commits ahead and 3 behind** product main. Implementation readiness is `BLOCKED_BEHIND_MAIN` until synchronized.
- The live product PR head lacks root `AGENTS.md` and `CONTROL_PLANE_REGISTRY.yaml` (both returned 404). Product Admission remains `BLOCKED_MISSING_REGISTRY`; resolve a canonical cross-repository admission binding before claiming Admission PASS. Do not create a competing local control plane.
- Exact-head CI run [39874124153](https://github.com/pan277942135/Yujian/actions/runs/39874124153) passed targeted validation and Chromium UI simulation only.
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
