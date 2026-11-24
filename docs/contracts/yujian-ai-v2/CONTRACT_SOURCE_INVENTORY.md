# Contract Source Inventory — YuJian AI V2 Core Contracts

**Inventory date:** 2026-11-24\
**Repository:** `pan277942135/Yujian_App`\
**Target PR:** [#346](https://github.com/pan277942135/Yujian_App/pull/346)\
**Canonical directory:** `docs/contracts/yujian-ai-v2/`\
**Inventory baseline:** branch `fix/26kf-path-boundary-and-fish-gate-v1`, parent HEAD `0acfa98afe96bb04768c8304b81cd5e1456b63d9`, tree `0f05a106b90d8d9e0e90320031b3e1bf98f68a34`

## 1. Frozen source archive

| Item | Value |
|---|---|
| Archive | `YuJian_AI_V2_Six_Core_Contracts_Frozen_Work_V1.0.zip` |
| Library location | `/YuJian_App/contracts/v2/` |
| Size | 41,891 bytes |
| SHA-256 | `b802e80a6640eaf7d1b6410cec79aa032682e6a5b51262e24c5742c94f85b07b` |
| Internal `SHA256SUMS` | 13/13 entries verified before repository integration |
| Package contents | 14 files: six core contracts, cross-review, Work instruction, acceptance matrix, registry, changelog, README, manifest and SHA list |

The six contract bodies (`02`–`07`) are copied from the archive without semantic edits. The archive itself remains unchanged in Library. The repository manifest and checksum list are regenerated to include repository-specific inventory/report/validator artifacts.

## 2. Existing repository source inventory

The remote tree at the baseline HEAD was searched for recognition runtime, timing, timeline, evidence, optical continuity, result and handoff contract documents. No existing `docs/contracts/yujian-ai-v2/` directory or same-name six-contract canonical set was present. Related documents below are retained; none was deleted or overwritten.

| Existing source path | Remote blob SHA | Classification for this V2 core set | Treatment |
|---|---|---|---|
| `design/pages/recognition/runtime/Recognition_Runtime_Contract_V1_1.md` | `0704d594fd6797904d25aae03eb298e8204fde12` | Legacy runtime/product-state contract | Retained unchanged; reference only for V2 core Runtime scope |
| `design/pages/recognition/runtime/RECOGNITION_13_STAGE_TIMING_CONTRACT_V1_0.md` | `729d51b753712c798aa15819cf5a56e098405413` | Prior 13-stage timing authority | Retained unchanged; V2 Timeline V1.0 is canonical for the six-contract set |
| `design/pages/recognition/evidence/Recognition_Evidence_Contract_V1_1.md` | `d2f96a8be7f5baac7bbe689cef614e07e66a068e` | Legacy visual/runtime evidence contract | Retained unchanged; reference only for V2 core Evidence scope |
| `design/pages/recognition/evidence/Recognition_Runtime_Evidence_V1_2.md` | `47d6d538d667932eb9be2dec3a3e4b44876fa180` | Prior runtime evidence specification | Retained unchanged; V2 Runtime Evidence V1.0 is canonical for the six-contract set |
| `design/pages/recognition/spec/Recognition_State_Timeline_Spec_V1_2.md` | `a35a2e7a9f9497869387ea0def9babbb099dac6a` | Prior state/timeline specification | Retained unchanged; historical reference |
| `design/pages/recognition/spec/Recognition_State_Timeline_Spec_V1_3.md` | `35c04dd427e4e4d5ec223f5fa9ad0eb42c42beed` | Product-state specification | Retained unchanged; page/product semantics remain contextual, not a replacement for V2 runtime contracts |
| `design/pages/recognition/spec/Recognition_State_Timeline_Review_V1_2.md` | `8c3a6941b377c178ce404bcc98a2529c9ec5845c` | Prior timeline review | Retained unchanged; historical reference |
| `design/pages/recognition/continuity/Processing_Result_Optical_Continuity_Contract_V1.md` | `b386981ee10c69de10a6ba8257472ce26127e91c` | Processing→Result optical continuity design | Retained unchanged; subordinate design reference; V2 Handoff owns core data/receipt semantics |
| `design/pages/recognition/result/engineering/Recognition_Result_Metadata_Input_Contract_V1.md` | `4f79361b671d90f426995c96750cdaca03e1157b` | Result metadata page contract | Retained unchanged; page-level scope, not a core handoff authority |
| `design/pages/recognition/result/media/Recognition_Result_Hero_Media_Contract_V1.md` | `350f6321b1c75550772689556aefba1bf5340644` | Result hero media design contract | Retained unchanged; page-level visual/media scope |
| `design/pages/recognition/processing/optics/SWOS_Optical_Contract_V1.md` | `933cad688fa2051b225c21cf2ee0221653e54264` | Prior optical presentation contract | Retained unchanged; visual reference only, not the V2 timeline/runtime authority |
| `design/pages/recognition/processing/optics/SWOS_Optical_Contract_V1.json` | `22091db21d5fb74f5cfd09eafd685c549b8c7395` | Prior machine-readable optical contract | Retained unchanged; legacy/reference for the V2 core set |
| `design/pages/recognition/processing/optics/YUJIAN_RECOGNITION_VISUAL_CONTRACT_FREEZE_ADDENDUM_V1.md` | `a1c43298c3243700b17fc2f3ee5c6595606a917c` | Prior visual contract addendum | Retained unchanged; visual authority remains separate from runtime data contracts |

## 3. Existing implementation references (not superseded)

| Implementation source | Remote blob SHA | Classification |
|---|---|---|
| `application-core/src/main/java/com/yujian/ai/ai/RecognitionRuntimeContract.kt` | `e5bc82babe59af58b6f7cdd011349d173bc81b0f` | Runtime implementation reference; conformance not assessed in this documentation-only change |
| `application-core/src/test/java/com/yujian/ai/ai/RecognitionRuntimeContractTest.kt` | `362345aa5f2ac28fc0970b94fdc7147b7b69b635` | Existing unit-test reference; not run in this change |
| `app/src/androidTest/java/com/yujian/ai/RecognitionResultHandoffGuardrailRuntimeTest.kt` | `d57c7729289ed4a568160126a9f5856d7a153a5a` | Existing handoff instrumentation reference; not run in this change |

These implementation and test files are not deleted, moved, or modified. The contract freeze does not claim that they conform to the new canonical specifications.

## 4. Canonical V2 contract ownership

| Contract ID | Canonical file | Owner domain |
|---|---|---|
| `YUJIAN_RECOGNITION_RUNTIME_CONTRACT_V1` | `02_RECOGNITION_RUNTIME_CONTRACT_V1.md` | `model-runtime` |
| `YUJIAN_RECOGNITION_RESULT_SNAPSHOT_CONTRACT_V1` | `03_RECOGNITION_RESULT_SNAPSHOT_CONTRACT_V1.md` | `recognition-data` |
| `YUJIAN_REVEAL_TIMELINE_CONTRACT_V1` | `04_REVEAL_TIMELINE_CONTRACT_V1.md` | `scene-timeline` |
| `YUJIAN_ORB_TO_GLYPH_RENDERING_CONTRACT_V1` | `05_ORB_TO_GLYPH_RENDERING_CONTRACT_V1.md` | `orb-glyph-rendering` |
| `YUJIAN_RESULT_HANDOFF_CONTRACT_V1` | `06_RESULT_HANDOFF_CONTRACT_V1.md` | `result-handoff` |
| `YUJIAN_RUNTIME_EVIDENCE_CONTRACT_V1` | `07_RUNTIME_EVIDENCE_CONTRACT_V1.md` | `runtime-evidence` |

`01_CROSS_CONTRACT_REVIEW.md` owns C01–C24 cross-contract rulings; `09_CROSS_CONTRACT_ACCEPTANCE_MATRIX.csv` maps the interfaces; `10_CONTRACT_REGISTRY.json` is the machine-readable registry. Each core rule has one owner contract; dependent contracts reference the owner rather than redefining it.

## 5. Supersession policy

- The canonical six-contract set is authoritative for YuJian AI V2 core runtime, snapshot, timeline, Orb-to-Glyph, result handoff and runtime evidence interfaces.
- Existing design/page-level files remain in the repository as historical or subordinate references. Their contents are not silently rewritten.
- Existing implementation sources and tests are not superseded by documentation; their conformance must be assessed in a separate implementation work item.
- This inventory is a source/provenance classification, not a source-code audit or device acceptance report.

## 6. Supporting policies

| Policy ID | Canonical file | Version / status | Authority |
|---|---|---|---|
| `YUJIAN_NON_MODEL_GATE_DEADLINE_POLICY_V1` | `12_NON_MODEL_GATE_DEADLINE_POLICY_V1.md` | `1.0.0` / `FROZEN_SPECIFICATION` | User-supplied Work supplement A; separately registered under `supportingPolicies`, outside the six-core `contracts` list |

The policy freezes 23 non-model Gate values and timer boundaries. It supplements the six core contracts without changing their content or hashes.
