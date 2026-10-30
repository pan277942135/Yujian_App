# Recognition Result Android Implementation Contract V1

**Page:** RecognitionResult (`recognition.result.v3_1`)  
**Contract version:** V1  
**Status:** READY_FOR_IMPLEMENTATION  
**Runtime validation:** NOT_RUN  
**Upstream Page Contract commit:** `a0621beb206bb3b92aa056784a62466879b41dfa`

## Authority Chain

```text
Frozen Page Contract
        ↓
Android Implementation Contract
        ↓
Android component owners
        ↓
Runtime evidence
```

The Page Contract remains the product and visual authority. This document binds its frozen rules to implementation responsibilities and test evidence; it does not replace or reinterpret the Page Contract.

| Layer | Effective authority |
|---|---|
| Recognition Result Page | `design/system_v3/08_Page_Contracts/recognition/result/Recognition_Result_Page_Contract_V3_1.md` (FROZEN; Page Contract Registry gate: PAGE_CONTRACT_READY = PASS) |
| Page state machine | `design/system_v3/08_Page_Contracts/recognition/result/State_Machine_V3_1.json` |
| Page data and source lineage | `design/system_v3/08_Page_Contracts/recognition/result/Data_Contract_V3_1.json`; `design/system_v3/08_Page_Contracts/recognition/result/Recognition_Result_Data_Lineage_V1.json` |
| Shared components | `design/system_v3/05_Component_System/Component_Registry_V3_FINAL.json` |
| Typography | `design/system_v3/04_Design_Tokens/Typography/Typography_Tokens_V3_FINAL.json` |
| Color | `design/system_v3/04_Design_Tokens/Color/Color_Tokens_V3_FINAL.json` |
| Geometry and responsive rules | `design/system_v3/07_Responsive_System/Geometry_Tokens_V3_FINAL.json` |
| Asset identity and SHA | `design/system_v3/06_Asset_System/Asset_Registry_V3_FINAL.json`; page instances are referenced by `design/system_v3/08_Page_Contracts/recognition/result/Layout_Contract_V3_1.json` |
| Implementation binding | `design/system_v3/09_Android_Implementation_Contracts/Android_Implementation_Contract_Registry_V1.json`, `design/system_v3/09_Android_Implementation_Contracts/Recognition_Result_State_Binding_V1.json`, `design/system_v3/09_Android_Implementation_Contracts/Recognition_Result_Component_Mapping_V1.json` |
| Runtime acceptance | `design/system_v3/09_Android_Implementation_Contracts/Recognition_Result_Runtime_Gate_V1.md` (criteria only; NOT_RUN) |

Authority precedence: page Frozen visual/geometry authority controls its declared properties; Shared Foundation controls shared properties; this implementation contract only maps those authorities to owners and events.

## Screen Ownership

The Android screen owner is `RecognitionResultScreen`.

**Allowed ownership:** compose the registered page layout; render only the bound `RecognitionResultState`; dispatch declared user/navigation events to the existing host; retain drafts while the Result flow is active.

**Not owned by this screen:** new visual rules, tokens, shared components or product states; model inference; source-of-truth data generation; persistence schema; Backend/API/database changes; changing the Frozen page's route, copy, geometry, media treatment or component variants.

The owner labels in the Component Mapping are logical responsibility slots. They are not Kotlin class or file declarations. Android implementation may place them in existing project structure without creating new public Design System components.

## State Binding

`RecognitionResultState` is the sole page state binding. Its exact IDs and canonical page-state mappings are in `design/system_v3/09_Android_Implementation_Contracts/Recognition_Result_State_Binding_V1.json`.

Required IDs:

- `INITIAL`
- `RESULT_READY`
- `HIGH_CONFIDENCE`
- `MEDIUM_CONFIDENCE`
- `LOW_CONFIDENCE`
- `NO_FISH`
- `IMAGE_QUALITY_WARNING`
- `SAVE_LOADING`
- `SAVE_SUCCESS`
- `SAVE_ERROR`

`INITIAL` and `RESULT_READY` are non-rendered handoff boundaries; they do not add a Result loading screen. While no valid result payload exists, Processing remains the active route. `SAVE_LOADING` maps to the Page Contract's `SAVE_IN_PROGRESS`; `SAVE_ERROR` maps to `SAVE_ERROR`. `SAVE_SUCCESS` is transient and immediately dispatches the requested persisted destination.

The Page Contract's `TECHNICAL_FAILURE` remains a separate canonical outcome, outside the `RecognitionResultState` enum and outside Image Quality. No Android-only replacement states are permitted; in particular `SUCCESS_2`, `FAILED_TEMP`, and `UNKNOWN_RESULT` are forbidden.

## Component Mapping

Every Page Contract component maps to the identical frozen Component Registry ID. Full token and asset identities are recorded in `design/system_v3/09_Android_Implementation_Contracts/Recognition_Result_Component_Mapping_V1.json`.

| Page Contract component | Registered variant | Android implementation owner |
|---|---|---|
| `navigation.back` | `navigation.back` · BACK_CENTER_TITLE | `RecognitionResultScreen/navigation/back` |
| `navigation.top_title` | `navigation.top_title` · BACK_CENTER_TITLE | `RecognitionResultScreen/navigation/title` |
| `action.primary_button` | `action.primary_button` · RESULT_SAVE / SECONDARY | `RecognitionResultScreen/actions/save_and_secondary` |
| `action.text_action` | `action.text_action` · RESULT_TEXT_ACTION | `RecognitionResultScreen/actions/continue_memory` |
| `action.icon_action` | `action.icon_action` · RESULT_ICON_ACTION | `RecognitionResultScreen/actions/metadata_edit` |
| `surface.glass_surface` | `surface.glass_surface` · MIST_GLASS | `RecognitionResultScreen/result_surface` |
| `surface.card` | `surface.card` · PAGE_CARD_COMPOSITION | `RecognitionResultScreen/candidate_card` |
| `media.hero` | `media.hero` · RESULT_HERO | `RecognitionResultScreen/hero_media` |
| `media.image` | `media.image` · RESULT_EVIDENCE_MEDIA | `RecognitionResultScreen/recovery_evidence_media` |
| `data.metadata_row` | `data.metadata_row` · RESULT_METADATA_VERTICAL | `RecognitionResultScreen/metadata_rows` |
| `input.selector` | `input.selector` · SPECIES_PICKER | `RecognitionResultScreen/species_picker_entry` |
| `input.search` | `input.search` · SPECIES_SEARCH | `RecognitionResultScreen/species_picker_search` |
| `input.bottom_sheet` | `input.bottom_sheet` · METADATA_EDIT_SHEETS | `RecognitionResultScreen/metadata_edit_sheet` |

The five required bindings—`media.hero`, `surface.glass_surface`, `data.metadata_row`, `action.primary_button`, and `input.bottom_sheet`—are included. Other Page Contract references are also mapped. Implementations must preserve each registered variant; a page owner does not authorize a new shared component or a same-name component with different parameters.

## Data Binding

UI fields resolve through the Page Contract Data Contract and lineage registry. This mapping does not define an API or database schema.

| Page field | Source owner | Requiredness |
|---|---|---|
| `recognition.result_state` | Recognition Result State Matrix V1.1 and canonical Master Flow result nodes | required |
| `recognition.original_image` | Normalized user image handed off from Capture; Hero Media Contract owns its mapping inside the state-specific frame. | required for all five visual states |
| `recognition.species_identity` | design/system_v3/08_Page_Contracts/recognition/result/Recognition_Result_Data_Lineage_V1.json#species → MODEL_M1 | Top-1 default for High; explicit confirmation for Medium; optional until manual selection for Low. |
| `recognition.candidate_list` | design/system_v3/08_Page_Contracts/recognition/result/Recognition_Result_Data_Lineage_V1.json#candidate_list → MODEL_M1 | Conditional: Medium result only. |
| `recognition.image_quality_reason` | design/system_v3/08_Page_Contracts/recognition/result/ImageQualityCopy_V1.json#reason_taxonomy | Conditional: quality=INVALID and IMAGE_QUALITY_WARNING; NO_FISH uses its separate state. |
| `catch.length` | design/system_v3/08_Page_Contracts/recognition/result/Recognition_Result_Data_Lineage_V1.json#length → USER_INPUT | optional |
| `catch.weight` | design/system_v3/08_Page_Contracts/recognition/result/Recognition_Result_Data_Lineage_V1.json#weight → USER_INPUT | optional |
| `catch.location` | design/system_v3/08_Page_Contracts/recognition/result/Recognition_Result_Data_Lineage_V1.json#location → GPS (optional, explicit action/permission); existing location interaction remains page-owned. | optional |
| `catch.story` | design/system_v3/08_Page_Contracts/recognition/result/Recognition_Result_Data_Lineage_V1.json#story → USER_INPUT | optional |
| `save.requested_destination` | design/system_v3/08_Page_Contracts/recognition/result/Recognition_Result_Data_Lineage_V1.json#requested_destination → CTA_INTENT | required when persisting |
| `save.persisted_record_id` | design/system_v3/08_Page_Contracts/recognition/result/Recognition_Result_Data_Lineage_V1.json#persisted_record_id → PERSISTENCE_RESULT | required before either successful exit |
| `recognition.confidence_output` | design/system_v3/08_Page_Contracts/recognition/result/Recognition_Result_Data_Lineage_V1.json#confidence → MODEL_OUTPUT | required to determine the real confidence state |
| `recognition.quality_gate` | design/system_v3/08_Page_Contracts/recognition/result/ImageQualityCopy_V1.json#quality_gate → QUALITY_GATE | required when quality evaluation is returned |
| `catch.timestamp` | design/system_v3/08_Page_Contracts/recognition/result/Recognition_Result_Data_Lineage_V1.json#timestamp → DEVICE | optional display metadata |

The existing contracts govern fallbacks and error behavior. Missing optional values use their Frozen empty placeholder or are omitted as specified; never synthesize a timestamp, location, species, confidence, quality reason, candidate or image. Missing required original media returns through Capture recovery and cannot create a record. Medium candidates remain suggestions until explicit selection; Low confidence cannot persist before explicit manual species selection.

Persistence errors keep the user on Result with the draft and requested destination intact and retryable. Prevent duplicate record creation. Only a successfully persisted record identity may be passed to navigation. Save Catch → persist → Home; Continue Memory → persist → FishRecordDetail with initial section MEMORY.

## Runtime and Acceptance Boundary

Runtime evidence is defined in `design/system_v3/09_Android_Implementation_Contracts/Recognition_Result_Runtime_Gate_V1.md`. Its viewport, font-scale, accessibility and state criteria are READY_TO_TEST; execution remains NOT_RUN. A future runtime PASS requires evidence against the exact Page Contract and Registered Frozen assets at the tested build SHA. No code, APK, device run or screenshot evidence is claimed by this contract task.
