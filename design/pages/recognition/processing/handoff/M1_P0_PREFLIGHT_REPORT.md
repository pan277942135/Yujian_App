# M1 Recognition Processing — P0 Preflight Checkpoint

**Task:** `M1_PROCESSING_FIGMA_ENGINEERING_HANDOFF_V1`  
**Checkpoint date:** 2026-10-18  
**Status:** `BLOCKED_P0_FIGMA_MCP_PLAN_LIMIT`  
**Overall handoff:** `NOT_PASS`

## 1. Baseline and scope

- Product/design authority: Draft PR [#165](https://github.com/pan277942135/Yujian_App/pull/165), branch `design/processing-remove-early-visual-v1`, head `31d1483b83038db78821625cd9339ad0e8c19ff3`.
- PR #165 is Draft / Open / Unmerged. This checkpoint is based on that exact head and does not modify PR #165, Android product code, or `main`.
- Current product visuals are exactly three Frozen states: `02_AI_Understanding_Frozen.png`, `03_Fish_Highlight_Frozen.png`, and `04_Fish_Identifying_Frozen.png`. The deleted Early image is not restored or used.
- Figma target: [Recognition Main Journey Master Board V1](https://www.figma.com/design/VjN7iFviVN9aKeLv8kJivM/Recognition-Main-Journey-Master-Board-V1).

## 2. P0 Figma access gate

A real read-only Figma page inventory was attempted before any write. The Figma connector returned:

> You've reached the Figma MCP tool call limit on the Starter plan. Upgrade your plan for more tool calls.

The connector returned no page metadata. Therefore page existence, Figma file editability, the required real Frame/Text write test, and all node IDs remain unknown. No Figma write was attempted after the access-limit response. No local HTML, JSON, or image is represented as a Figma result.

| Gate | Result | Evidence |
|---|---|---|
| Figma file/page preflight | `BLOCKED` | Figma MCP Starter-plan call-limit response |
| Real Frame + Text write test | `NOT_RUN` | Requires working Figma MCP access |
| Existing RP01 page duplicate check | `NOT_RUN` | Metadata query was blocked |
| Figma nodes / imports | `NONE` | No successful Figma operation |
| P1 entry gate | `BLOCKED` | P0 requires real Figma write evidence before P1 |

P1–P6 are not claimed complete. The next Figma step must resume with page inventory, avoid duplicate creation, then run the real write test. Only after that passes may the structural page be built.

## 3. Preliminary source authority matrix

Frozen image SHA values below are the current Visual State Set / reference-manifest declarations. The image files are approximately 1.9–2.2 MB; the GitHub file connector did not return their binary payload for independent SHA-256 recomputation in this run. The PR description says the three retained Frozen binaries were left byte-identical. Their declared hashes are recorded as declarations, not as re-computed checks.

| Source path / SHA | Asset role | Figma node | Import status | Authority / verification |
|---|---|---|---|---|
| `design/pages/recognition/design/02_AI_Understanding_Frozen.png` / declared SHA-256 `82fff89fabc368b01535dc94f4f084fee7b1e50e20948b9e82d5711c9a42473b`, 941×1672 | Sole Frozen authority for `IMAGE_RECOGNIZING`; CAPTURED and DETECTING share it | Unknown | `BLOCKED_BEFORE_IMPORT` | Visual State Set V1.2 + reference manifest; SHA not recomputed |
| `design/pages/recognition/design/03_Fish_Highlight_Frozen.png` / declared SHA-256 `632c9a7e6b45620d6a468acd0f83418ca6465423201e485f9699ebbc0d665284`, 941×1672 | `FISH_LOCATED` Frozen reference | Unknown | `BLOCKED_BEFORE_IMPORT` | Visual State Set V1.2 + reference manifest; SHA not recomputed |
| `design/pages/recognition/design/04_Fish_Identifying_Frozen.png` / declared SHA-256 `d6ad5b06ddd4eb6236dd5ee03b5e5f5e16bf1b23661689c0fc9d88c790dd15ec`, 941×1672 | `SPECIES_RECOGNIZING` Frozen reference | Unknown | `BLOCKED_BEFORE_IMPORT` | Visual State Set V1.2 + reference manifest; SHA not recomputed |
| `app/src/androidTest/assets/recognition_real_catch_fixture.jpg` / SHA-256 `6e955087108f7463eca2dbac489699a4942028ebe1b3d456ddfca523286385ef`, 292,222 bytes | Existing runtime photo fixture; test sample only | Unknown | `NOT_IMPORTED` | Recomputed from returned binary; byte-identical to `sample_recent_catch.jpg` and `real_catch_source.jpg`. Current source/rights review says provenance and usage rights are unverified; fixture metadata says no static bbox |
| `RecognitionFrozenFlowEmulatorTest.kt#createAnnotatedSubjectAlphaFixture` | Test-only manual subject-alpha polygon clipped to the runtime detector ROI; actual pipeline supplies the BBox in the production-flow test | Unknown | `NOT_IMPORTED` | Test code comment identifies the real 1152×1536 catch photo and fish silhouette; this is not a production mask asset. Source-photo rights remain unverified |
| `design/system/icon_library_v0_1/assets/ic_back_v1.svg` / SHA-256 `51c04e3263258f75a239392e7292d205ba1af83292f7bd11c104637d41f44d61` | Shared Back icon | Unknown | `BLOCKED_BEFORE_IMPORT` | Frozen Icon Assets V1 + Icon Registry V3; raw SVG SHA-256 recomputed from GitHub bytes |
| AI Edge Field paths B1–B4 / G1–G3 | Seven fixed normalized photo-space trajectories | Unknown | `NOT_IMPORTED` | Frozen Visual Spec V1.1, Static Shape Contract, and current `RecognitionAmbientField.kt`; source coordinates agree in code/spec |
| Status Card / typography / color / geometry tokens | Shared native component values | Unknown | `NOT_IMPORTED` | Recognition Processing V1.2 Contract and V3.1 Layout Contract; no Figma Token/Variable binding evidence exists |

**Asset boundary:** Frozen full-page references are visual authorities, not standalone runtime-photo sources. Do not crop their embedded photo and relabel it as an original photograph. The current runtime test fixture is a usable, clearly labeled test input, but it is not approved as a production photograph or a rights-cleared source.

## 4. Authority and timing arbitration

### Product state mapping

The effective mapping is:

| Internal V3.1 phase | User-visible design |
|---|---|
| CAPTURED | `IMAGE_RECOGNIZING` |
| DETECTING | `IMAGE_RECOGNIZING` |
| OUTLINE | `FISH_LOCATED` |
| CLASSIFYING | `SPECIES_RECOGNIZING` |
| RESULT_READY | Immediate Recognition Result handoff |

V3.1 is the effective event and minimum-beat authority: CAPTURED ≥220ms, DETECTING ≥320ms, OUTLINE ≥350ms, CLASSIFYING ≥250ms. Each transition also requires its real pipeline event. The V1.2 product-level 900/600/1250ms values are not added on top of V3.1 and do not authorize timer-only transitions. V1.2 remains the visual-motion reference where it does not conflict with V3.1.

### RESOLVE / Result

V3.1 declares RESULT_READY as a 0ms immediate handoff. The Result Motion Contract declares 0ms entry delay and a 200ms final resolve fade. The implementation mapping is therefore: show Result immediately when the real result is ready and the CLASSIFYING minimum is satisfied; run the 200ms Resolve fade as a non-blocking handoff visual. Do not add a 200ms wait before Result. Runtime proof of this overlap is still required.

### Edge Field ownership

The source contract defines one photo-viewport overlay and one shared coordinate transform. Seven fixed paths remain project data; the four Primary islands are B1/G1/G3/B3 with the frozen strength ranges, and B2/G2/B4 remain secondary. The four Quiet Gaps remain empty. Optical draw order is Receiving Light → Outer Bloom → Mid Glow → Primary Core → Hairline → Hot Node → Particles. The 10dp / 4dp glow widths and per-path Core widths are available in the Rendering Contract.

## 5. Android source audit for the handoff

This is a source audit only. It does not change Android code or establish Runtime PASS.

| RP task | Existing implementation | Audit result / handoff |
|---|---|---|
| RP.A01 Photo Transform | `RecognitionImageTransform.kt`, `RecognitionSourcePhoto.kt`, `RecognitionMediaPlanner` | Existing CROP/FIT transform maps the photo and is passed to both Edge Field and Fish Focus. Preserve one transform for all overlays; require the photo transform at the renderer boundary so the fallback full-canvas rectangle cannot become a screen-edge anchor. |
| RP.A02 Edge Optical Stack | `RecognitionAmbientField.kt` | Existing single Canvas, normalized seven paths, 10-second continuous clock, path-specific seeds, shared state strength, and four Primary weights. Static optical fidelity has not been compared against Frozen in Figma or a matched runtime capture. |
| RP.A03 Fish Focus | `RecognitionFishFocus.kt`, `FishSubjectPreviewEngine.kt` | Existing real BBox mapping and A/B/C render levels. Production mask path uses ML Kit foreground confidence from the detector ROI. Test evidence also uses a manual polygon annotation. Never substitute a BBox rectangle for fish contour or show focus without a real detector BBox. |
| RP.A04 Status Card / Back | `RecognitionStatusOverlay.kt`, `RecognitionProcessingVisualTokens.kt`, `RecognizingScreen.kt` | Native card and Back exist. **Copy mismatch:** CAPTURED currently says “正在准备识别 / AI 已获取这张照片”; DETECTING changes to “正在理解这张照片 / 寻找这次鱼获的线索”. Map both phases to the one approved V1.2 opening copy and remove separate Early copy assertions from active parity checks while retaining the CAPTURED event trace. |
| RP.A05 State & Motion Clock | `RecognitionVisualStateController.kt`, `RecognitionAmbientField.kt` | Existing event-gated controller and continuous 10,000ms clock; do not add a second state machine or clock. Confirm RESULT_READY handoff against the non-blocking Resolve mapping above. |
| RP.A06 Quality / Reduce Motion | `RecognitionMotionPolicy.kt`, V1.2 degradation/accessibility contracts | Existing FULL/BALANCED/LITE and D0–D4 composition; Reduce Motion is separate and preserves semantic states. Runtime visual evidence remains required. |
| RP.A07 Visual Runtime & Acceptance | `RecognitionFrozenFlowEmulatorTest.kt`, `RecognitionOpticalFidelityRuntimeTest.kt` | Existing tests cover production flow and controlled visual inputs. The test suite still includes the retired Early diagnostic image/copy and needs the product-state mapping correction before current three-state visual parity can pass. API28 acceptance remains separate. |

### Status Card tokens

Current Android values correspond to the V1.2 contract: surface `#1A2C35 @ 0.85`, 112dp height, 30dp corner radius, 28dp horizontal padding, 54dp indicator, 4dp ring, title 20sp, subtitle 14sp. CAPTURED and DETECTING use the same screen placement and card component, but their title/subtitle change and create the retired Early product distinction.

### Photo / focus fixture facts

- The SHA-verified runtime photo fixture is the same byte source registered for the shared catch-photo sample; repository documentation says it has no trusted static bbox and that source provenance/rights are unverified.
- The production-flow runtime test executes `FishRecognitionPipeline` to obtain the actual BBox, then uses an explicitly test-only manual polygon alpha fixture for Level A contour evidence. A separate optical-fidelity test uses a controlled ellipse BBox/mask and is not a fish-contour authority.
- A Figma sample must be labeled as a test fixture and keep source, BBox and mask provenance visible. It must not be described as an approved product photo or production segmentation output.

## 6. Live CI snapshot on PR #165 head

Workflow runs for `31d1483b83038db78821625cd9339ad0e8c19ff3`:

| Run | Result |
|---|---|
| Control Plane Admission `39138204928` | PASS |
| Design Governance `39138204926` | PASS |
| Android CI `39138204925` | FAIL |

Android CI failed at “Camera runtime harness contract tests”; its output is `expected classification=FAIL_ARTIFACT actual=BLOCKED_INFRA`. The later build, APK, lint and upload steps were skipped, and `android-runtime` was skipped. This failure is retained for the owning test/Android team; this design checkpoint does not change its classification or claim Runtime PASS.

## 7. Current gate status and open gaps

| Gate | Status |
|---|---|
| RP_SOURCE_AUTHORITY | `PARTIAL` — three Frozen SHA values are declarations; photo rights/provenance remains open; Back SVG path/SHA is verified |
| RP_FIGMA_STRUCTURE | `BLOCKED_P0_FIGMA_MCP_PLAN_LIMIT` |
| RP_STATIC_OPTICAL_FIDELITY | `NOT_RUN` |
| RP_THREE_STATE_VISUAL_PARITY | `NOT_RUN` |
| RP_MOTION_HANDOFF | `DESIGN_MAPPING_RECORDED; RUNTIME_NOT_RUN` |
| RP_ANDROID_IMPLEMENTABILITY | `PARTIAL` — code exists; CAPTURED copy mismatch and Figma-node mapping remain |
| RP_INDEPENDENT_QA | `NOT_RUN` |
| M1_PROCESSING_FIGMA_ENGINEERING_HANDOFF | `NOT_PASS` |

Next prerequisites are working Figma MCP access (or user-approved alternate access), successful real Figma Frame/Text write evidence, source/rights closure for the photo fixture, Figma import of the verified Back SVG, and independent review. PR #165 remains Draft / Open / Unmerged; this checkpoint must be reviewed after its base design authority and must not be merged directly to `main`.
