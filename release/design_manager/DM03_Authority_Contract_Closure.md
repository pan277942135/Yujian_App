# DM03 · Authority / Contract 收口

Date: 2026-10-16
Scope: Design Manager presentation and design navigation only. No Android Runtime, AI model, database, Backend/API, or Frozen binary edits.

## Authority trace

The presentation contract adds the Manager view layer while reusing the established authority chain:

`recognition_flow_v1_2 → Page / State → source manifest or contract → visual asset + SHA → versioned contract`

- Feature and stable Manager routes: `design/registry/experience_registry_v1.json`; the existing `recognition_flow_v1_2` and `recognition_result_v1` entries remain canonical.
- First-use visual children: `design/pages/recognition/first_use_camera_guide/navigation.json`, `manifest.json`, `First_Use_Camera_Guide_Spec_V1.md`; P01–P03 remain children of `recognition_flow_v1_2 → 07 · 首次拍照教学`.
- Capture Page / state authority: `design/system_v3/08_Page_Contracts/recognition/capture/Recognition_Capture_Page_Contract_V3_1.md`, `State_Machine_V3_1.json`, `Capture_Accessibility_Responsive_Matrix.md`, and `HiFi_Freeze_Manifest_V1.json`.
- Permission source: `Camera_Permission_Denied_Visual_Contract_V1.md`; declared PNG is absent and no operating-system permission dialog is mocked.
- Processing state/timing: `design/system_v3/08_Page_Contracts/recognition/processing/Recognition_Processing_Page_Contract_V3_1.md` and `State_Machine_V3_1.json`.
- Result: `design/system_v3/08_Page_Contracts/recognition/result/Recognition_Result_Page_Contract_V3_1.md`, `Result_Responsive_Accessibility_Matrix_V3_1.md`, and existing Result Visual/Motion contracts.
- Governance and effective authority: `Page_Registry_V3_1.json`, `Effective_Authority_Registry_V3_1.json`, asset manifests/registries, and the existing Control Plane gates remain the controlling sources; they are not forked into a competing Manager authority.

## Page and state contract presentation

`design/manager/RECOGNITION_HIFI_PRESENTATION_CONTRACT_V1.md` defines how Manager routes display the existing contracts. The detail panel exposes Page ID, State ID, display name, design version, Manager status, canonical Authority Path, SHA-256, entry, trigger, input, actions, next state, back/cancel/retake rules, contract links, and Runtime status where source evidence exists. Missing source fields are labelled unavailable rather than invented.

P01/P02/P03 are guide-step IDs in the approved manifest and remain additive Capture guidance; they are not redefined as Android state-machine IDs. `recognition.capture.v3_1` and `recognition.result.v3_1` remain stable.

## Motion authority

The effective Processing sequence is the existing V3.1 State Machine:

`CAPTURED (≥220ms) → DETECTING (≥320ms) → OUTLINE (≥350ms) → CLASSIFYING (≥250ms) → RESULT_READY`

Each transition still requires both its minimum beat and the corresponding real pipeline event; `RESULT_READY` immediately hands off to Result. No threshold, event rule, or failure route is changed. The older `Recognition_Processing_Motion_Spec_V1_2.md` remains a separate frozen visual-motion reference, not the effective V3.1 timing source.

No new duration is authored for permission guidance, the first-use tutorial, or Result entry/exit. Existing Result Motion / Reduced Motion rules continue to apply. The Manager contract explicitly prevents a presentation-only proposal from overriding these authorities.

## Responsive and accessibility coverage

- Android page contracts continue to define 320/360/411dp safe-width behavior, portrait/landscape rules, WindowInsets/safe areas, font scaling and keyboard/IME avoidance where specified; no Android contract threshold was changed by this Manager release.
- Capture matrix documents fontScale 1.0/1.3/2.0, TalkBack, 44×44dp targets, and orientation behavior. Result matrix documents large text, TalkBack, keyboard/orientation and safe width. Those sources record design-contract status separately from Android Runtime execution.
- Manager browser QA covers 1440, 768, 390, 360, and 320 CSS px. This is a web presentation check, not evidence that Android Runtime passed.
- The image dialog has labelled controls, Escape and backdrop/close dismissal, focus restoration, and retained image aspect ratio. It does not claim to implement Android IME or TalkBack runtime contracts.

## Registry and governance changes

- Updated only the existing `experience_registry_v1.json` with the Recognition overview, boundary/status entries, source links, and stable route targets; preserved the legacy guide route and other module IDs.
- `navigation.json`, V3.1 Page Registry, Effective Authority Registry, source manifests, and canonical product contracts do not need version churn: their Authority content did not change.
- Added a Manager-only presentation contract and release validators; no new Product Page Contract is asserted.
- Control Plane Admission is `PASS`; static Control Plane files are unchanged. The `PROJECT_MASTER_STATE.yaml` absence on main follows the root instructions allowing execution state to remain branch-local; no second master-state authority was added.
- P01–P03 design state remains `FROZEN`; all three Android Runtime states remain `NOT_RUN`. Page-level preview readiness does not imply main-journey completion.

Any future correction to missing binaries must enter through the applicable Authority/import and Control Plane Admission path before becoming a live visual preview.
