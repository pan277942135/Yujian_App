# WP1-B Contract Landing Audit V1

**Task:** YUJIAN_WP1B_RECOGNITION_PIPELINE_AND_VISUAL_HANDOFF_IMPLEMENTATION_V1
**Repository:** pan277942135/Yujian_App
**Main baseline:** 74cad95b4f673bd9c8c60d16839f036d5886d933
**Landing branch:** design/recognition-visual-contract-v1
**Landing commit:** a0bf7134a5bcbcbe0c2d126f65da7021e905511d
**Audit date:** 2026-11-06

## Contract and design-file identity

| File | Main at baseline | Source | SHA-256 | Git blob in landing commit | Identity result |
|---|---|---|---|---|---|
| 13-stage State Machine V3 | Present | Main | `ece79009f7f42ab4f68a60801d4e23d0ef41dd17722dff91ab2cb47aff46c4c9` | `30e2fd7ee64a5e8a86c315587a57b7826d14f5d6` | Existing bytes unchanged |
| Unified Lifecycle V3 | Present | Main | `0b233a1dcb2b1228cfa2f87d2b13ffa1dcf4db2d0c2c3107a5ff48a66dfed8d5` | `40cec4696e0980c11e6622e2e030f8cbb7faddfc` | Existing bytes unchanged |
| 21-frame matrix CSV | Missing | PR #318, HEAD `4199cd91c216b0cb43e9d1f7a08496c41f992386` | `bfc60ec73f228d3d9cb5ef2ded84c87d2966555b85f7b8d862d763810770fbfa` | `0fc9fca09516e9259e2847626c4710be42541df1` | Copied byte-for-byte; source review labels retained |
| 21-frame matrix JSON | Missing | PR #318, same HEAD | `44c2161ac25d5186f1b24986bd7174c3955587940b08ef1a8d2c098c60bd2a64` | `b505a157a753d9e73b7fce9b4b011d52a6c653b5` | Copied byte-for-byte; source review labels retained |
| Component Runtime mapping | Missing | PR #318, same HEAD | `22a4e48fd042a8e6732a5e991e48b8e433d081664f43f802e6240bac7dea2f76` | `5204fcca15914a38965c9f0bb74137f5f3344bbf` | Copied byte-for-byte |
| Frozen visual handoff addendum V1 | Missing | Product-owner message in this conversation | `ca7ab62ad975880301ca223a113d4ae1bedf6333bece1aa936f63e3e61c73b52` | `a1c43298c3243700b17fc2f3ee5c6595606a917c` | Landed as a new additive authority |
| WP1-B execution contract | Missing | User attachment `粘贴的文本 (1)(20261106-085157).txt` | `42ce8e49ac01ad4f9b69645ece3fca29338cfa819bb090204a1e550cdcaef31c` | `2bed90e47760d483a06729752db6831cdcfdae4c` | Copied from the attached file |

The visual addendum arrived as conversation text without a separate source file or source SHA. Its recorded SHA-256 and blob identify the repository copy; there is no independent source-file hash to compare. The existing 13-stage and lifecycle files were not edited. No Kotlin, model weights, frozen visual binaries or parameter values were changed by this landing commit.

## Registry and control-plane synchronization

- Registered the addendum in `experience_registry_v1.json`, the recognition processing indexes and `UNIFIED_CONTRACT_REGISTRY.yaml`.
- Added the WP1-B task to Feature Matrix, Phase/Task State Machine, unified task bindings and an exact-SHA gate.
- Added blocker B009 for the missing approved DET_FISH v0.3 binary/metadata/validation evidence. It is a target completion blocker; independent work is still authorized.
- Synchronized the exact SHA-256 lock for `experience_registry_v1.json` in the Empty Home, Normal Home and Login page resource bindings after the additive registry entry; the audit JSON records each resulting file SHA and Git blob.
- Recorded the 3650ms / 6000ms dispute as unresolved and carried forward without changing either value.
- The PR #318 matrix/component source contents were copied unchanged; their original draft/review status was not promoted to a frozen runtime result.

## Baseline and CI evidence

- Main was re-resolved as `74cad95b4f673bd9c8c60d16839f036d5886d933`, matching the requested historical check value.
- PR #304 is merged at `64700592714b8fb1c06b659b9d79c92149d2ea95`; PR #317 remains Draft/Open at `df07e7ecc82ea96493885ff9fd12c82cd9b1aa68`; PR #318 remains Draft/Open at `4199cd91c216b0cb43e9d1f7a08496c41f992386`.
- Main Android CI run `41934781336`, for the baseline SHA, failed in job `127102654838` at `Verify Android runtime asset contracts`. The Empty Home V2.2 measured-anchor/layer-order checks failed. Gradle, unit tests, APK packaging, lint and runtime steps were skipped. The run's job/step summary was re-read from GitHub during this task.
- The existing Main admission run `41934781361` was PASS, but it is not Android build evidence.

## Validation

- `python scripts/validate_control_plane_admission.py`: PASS on the control-plane proposal after the registry task/gate count was corrected to 78 tasks and 38 gates.
- `python3 scripts/design_manager.py validate`: PASS (16 shared systems and 15 page modules).
- Hi-fi release, SWOS/RR09 V1.2 authority, frozen camera guide asset, and private visual reference registration checks: PASS.
- JSON/YAML parsing and control-plane validators: PASS. `git diff --check` reports Markdown trailing-space line breaks in the byte-identical PR #318 frame matrix, the byte-identical attached WP1-B task text, and the transcribed frozen addendum; these were retained as source formatting for SHA provenance.
- No Android build or API28 validation was run during contract landing.

## Next gate

Push and open the control-plane proposal PR. Wait for its exact-head required checks, then merge it under the repository's green-check policy. Only after Main contains this control-plane commit may the product branch `wp1b/recognition-pipeline-visual-handoff-v1` be created, admitted and used for implementation. The production detector path remains BLOCKED until an approved v0.3 artifact and validation provenance are available; v0.1 must not be relabeled as v0.3.
