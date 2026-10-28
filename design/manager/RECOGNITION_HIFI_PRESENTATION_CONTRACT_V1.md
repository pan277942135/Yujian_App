# Recognition Hi-Fi Presentation Contract V1

**Scope:** Design Manager preview and navigation for the Recognition main journey. This is a manager presentation contract; it does not replace a Capture, Processing, Result, or Android contract.

**Feature:** `recognition_flow_v1_2`
**Authority base:** `design/registry/experience_registry_v1.json`
**Runtime status:** `NOT_RUN` unless a separate current-head Runtime Evidence record says otherwise.

## Page and state identity

| Page ID | Design State ID | Source | Design status |
|---|---|---|---|
| `recognition.capture.v3_1` | `P01` | First-use guide overlay; `First_Use_Camera_Guide_Spec_V1.md` | FROZEN |
| `recognition.capture.v3_1` | `P02` | First-use guide overlay; `First_Use_Camera_Guide_Spec_V1.md` | FROZEN |
| `recognition.capture.v3_1` | `P03` | First-use guide overlay; `First_Use_Camera_Guide_Spec_V1.md` | FROZEN |
| `recognition.capture.v3_1` | `CAPTURE_READY_PORTRAIT_HORIZONTAL` | Capture Hi-Fi Freeze Manifest V1 | Design freeze recorded; preview `BLOCKED_INTEGRITY` |
| `recognition.capture.v3_1` | `CAPTURE_READY_LANDSCAPE_HORIZONTAL` | Capture Hi-Fi Freeze Manifest V1 | Design freeze recorded; preview `BLOCKED_INTEGRITY` |
| `recognition.capture.v3_1` | `CAPTURE_PERMISSION_DENIED` | Capture V3.1 Page Registry + Camera Permission Denied Visual Contract V1 | Contract FROZEN; preview `MISSING` |

P01/P02/P03 are guide-step IDs from the approved guide Registry and manifest, not new Android State Machine IDs. The first-use overlay remains additive inside `recognition.capture.v3_1`; its V1 spec does not replace the Capture Page Contract.

## Entry, trigger, input and actions

### First-use guide

- **Entry:** camera permission is granted and the camera provider / preview are Ready; the guide has not been completed.
- **Trigger:** show once after permission and provider readiness.
- **Input:** camera readiness and the guide-completion flag; the overlay does not select or create media.
- **P01:** `下一张` → P02. `跳过` closes the guide without triggering the shutter.
- **P02:** `上一张` → P01; `下一张` → P03; `跳过` closes without triggering the shutter.
- **P03:** `上一张` → P02; `开始拍摄` closes the guide and returns to Capture Ready without automatically triggering the shutter.
- **Completion:** persist only after actual dismissal by `跳过` or the final action. Permission denial, preview-not-ready, Gallery entry, and an abandoned / incomplete guide do not complete the guide.
- **Retake:** not specified; these guide states do not introduce a retake action.

### Capture and permission recovery

The V3.1 Capture Page Contract and State Machine govern entry, Capture Ready, permission, Capture / Gallery, error, back, cancellation, and normalized-image handoff. Permission dialogs and the Photo Picker remain platform-owned. A missing source PNG is shown as text and status only; no simulated permission screen is rendered.

The Capture Hi-Fi Freeze Manifest records two approved design identities, but both binary imports are `PENDING` and neither PNG exists at its registered repository path. Design approval (`FROZEN`) and preview integrity (`BLOCKED_INTEGRITY`) remain separate fields. The permission-denied contract records a source filename and SHA, but the PNG itself is absent; its preview state is `MISSING`.

## Asset display contract

- Read the PNG at its canonical Registry path. Keep the source bytes unchanged.
- Use intrinsic aspect ratio and `object-fit: contain`; do not stretch, crop, redraw, re-encode, or substitute screenshots.
- The preview opens an accessible modal with zoom in / out, Fit Window, and a separate original-file link. Escape and the close control dismiss it and restore focus to the invoking control.
- Display Page ID, Design State ID, design version, manager status, Authority Path, SHA-256, entry, trigger, input, actions, next state, back, cancel, retake, contract links, and Runtime status when those fields exist.
- A missing binary has no `<img>` and no hyperlink to the absent file. Its expected path is plain text, with the recorded reason and status.
- Runtime evidence is a separate field and is never inferred from a Frozen design asset.

## Journey overview and Master Board

`Recognition_Main_Journey_Overview_V1.png` is a `FROZEN_BUSINESS_OVERVIEW` communication flow, not a single-page Frozen Hi-Fi. It is shown in full, with a path to its original PNG and links to registered page / state routes.

`Recognition_Main_Journey_Master_Board_V1.json` and `.md` are the actual board schema and state reference. The target Master Board PNG/SVG files are absent and `asset_import_gate` is `BLOCKED_CONNECTOR_UPLOAD_STEP`. The manager renders the schema as a status table only; it does not use the overview PNG or an HTML mock as a substitute board. Master Board `REVIEW` maps to manager `PARTIAL`; `MISSING` maps to `MISSING`; system ownership stays an ownership label.

The combined `Recognition_New_States_HiFi_V1.png` is a review source: the first-use overlay it contains is superseded by P01/P02/P03, while Dirty Draft Discard Confirmation remains `ADDITIVE_UI_REVIEW`. Its manager state is `PARTIAL`; neither item is promoted to `FROZEN` by the image's existence.

## Processing motion and timing authority

The Recognition Processing Page Contract V3.1 identifies `State_Machine_V3_1.json` as the sole effective timeline / state authority. Its event-gated sequence is:

`CAPTURED (≥220ms) → DETECTING (≥320ms) → OUTLINE (≥350ms) → CLASSIFYING (≥250ms) → RESULT_READY (immediate handoff)`

Each minimum beat and its corresponding real pipeline event are both required; timers alone do not advance the state. These thresholds are copied from the existing Frozen V3.1 contract and are not changed by this manager work.

`Recognition_Processing_Motion_Spec_V1_2.md` remains unmodified and still records a three-visible-state pacing table (900 / 600 / 1250 / 200ms). The V3.1 Page Contract explicitly assigns effective state timing to the V3.1 State Machine; the manager uses that state machine for the five-state sequence and leaves the V1.2 motion document as its separate frozen visual-motion reference. This registry / document cross-reference must remain visible in closure evidence; do not silently combine or average the two timing tables.

The Result Motion Spec V1 continues to govern the resolve-to-Result visual handoff, shared control feedback, save loading, and reduced-motion behavior. Result entry does not add celebration, looping progress, or automatic candidate motion. Permission and first-use contracts do not establish new animation durations; none are introduced here.

## Responsive and accessibility contract

### Design Manager viewer

- Validate the static manager at 1440px, 768px, 390px, 360px, and 320px widths; do not introduce horizontal page overflow.
- Keep preview images within the viewport while preserving their original ratio. The modal remains scrollable for large source images.
- All new buttons and links have at least 44 CSS px block size, a visible keyboard focus ring, and an accessible name. The image itself is a labeled button.
- The native `<dialog>` provides modal semantics; close button, backdrop, and Escape dismiss it; focus returns to the image button. Zoom transition is disabled under `prefers-reduced-motion`.

### Product contracts (separate from manager rendering)

- Capture Accessibility / Responsive Matrix V3.1 covers 320dp, 360dp, 411dp, font scales 1.0 / 1.3 / 2.0, TalkBack focus order, 44×44dp targets, and portrait / landscape visual rules. It records contract PASS and Runtime execution `NOT_RUN`.
- Result Responsive / Accessibility Matrix V3.1 covers 320dp / 360dp / 411dp, large font, TalkBack, keyboard / IME, orientation, safe width, and 44×44dp targets. It records contract PASS and Runtime execution `NOT_RUN`.
- Processing follows its V3.1 Layout and State Machine contracts plus the existing Reduce Motion / degradation contract. These definitions do not establish a current Android Runtime pass.
- The permission dialog is OS-owned and is not imitated. Result IME avoidance is covered by its page contract; this manager modal does not claim Android keyboard behavior.

## Navigation contract

- Keep `recognition_flow_v1_2 → 07 · 首次拍照教学 → P01/P02/P03` and all existing hifi IDs intact. Add the separate Design Manager-only `资源` route at `#page/recognition_flow_v1_2/hifi/capture_first_use_guide/resources`; it lists the same P01/P02/P03 source PNGs and does not add an overlay step, Android route, or runtime asset mapping.
- Each page and state uses a stable `#page/{feature}/hifi/{view}[/{child}]` URL.
- User-selected navigation pushes a history entry. Direct Hash load, refresh, back, and forward resolve the same Registry item.
- An invalid Hash displays an explicit route error and never falls back to a valid Frozen page.
