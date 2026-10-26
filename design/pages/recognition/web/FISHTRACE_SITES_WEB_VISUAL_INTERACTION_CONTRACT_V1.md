# FishTrace Sites Web 视觉与交互合同 V1

**Contract ID:** `FISHTRACE-SITES-WEB-INTERACTION-V1`  
**Version:** `1.0`  
**Decision status:** `USER_DECISIONS_FROZEN`  
**Authority status:** `DESIGN_CONTRACT_ONLY`  
**Runtime visual status:** `NOT_VERIFIED`  
**Owner:** Product  
**Target Sites project:** `appgprj_6a50a71561708191a6ea8c6194ce68d4`  
**Target URL:** https://fishtrace-ai.pan277942135.chatgpt.site/  
**Current confirmed route:** `/` (single-page in-place flow; do not invent a new URL route)

## 1. Purpose and scope

This contract freezes the complete user-visible flow from clicking **「拍照识鱼开始」** through source selection, photo preview, local model preparation, SWOS processing, RR09 reveal and result/recovery actions for the existing FishTrace Sites project.

It is a Sites-specific page/interaction contract. It does not modify the separate `pan277942135/Yujian` product repository or PR #177. The Yujian Web V2 contract and its private three-stage pixel reference remain authorities for that separate product scope; they are not the layout authority for this Sites-specific V1.

The prior three-column Sites workbench and persistent five-node strip are superseded for this target flow. Do not create a second Sites project, a parallel demo, a second PhotoSession, or a second recognition state machine.

This contract freezes structural, interaction, state, responsive and visual invariants. It does **not** claim pixel-perfect runtime acceptance, production Mask qualification or user visual sign-off. Runtime evidence must be collected against the exact deployed source SHA after implementation.

## 2. User-approved decisions

1. **Immersive single scene:** the recognition experience is one focused product scene, not a developer workbench or three-column dashboard.
2. **Source choice first:** after clicking 「拍照识鱼开始」, show both 「现场拍照」 and 「从相册选择」 before opening either source.
3. **Result layout:** desktop uses photo on the left and result content on the right; phone uses photo above result content.

## 3. Visual language and shell

### 3.1 Product shell

- Preserve the existing Sites project and root route `/`; switch the in-page experience state without creating another site or route.
- Use one uncluttered product shell with brand/title, one back/close control and one main content scene.
- Background language: natural deep-water teal, soft lake-white/ivory surfaces and restrained morning gold. Reuse existing approved shared tokens where available; SWOS V1.2 optical colors remain calibration candidates and must not be silently promoted to frozen numeric values.
- No persistent three-stage cards, no developer model-import panel, no always-visible diagnostics, no fixed five-node workbench progress strip and no nested scrollbars.
- A small truthful status line is permitted. It must not pretend that animation time equals model progress.
- Interactive controls must have a minimum 44 CSS px target, visible focus, readable CJK text and clear disabled/error states.

### 3.2 Image invariant — highest priority

- The user's selected/captured, EXIF-normalized source photo is the **only sharp foreground photo** throughout preview, Processing and Result.
- Keep the full source photo visible with `object-fit: contain`; never stretch it or crop off the fish head/tail in the primary photo.
- A backdrop may be derived from the same source photo using a strongly softened, low-contrast cover treatment to fill empty space. It must not read as a second sharp photo.
- Never show multiple sharp crops, repeated tiles, a fish close-up beside the original, a collage, or a different source image during phase changes.
- Photo, BBox, qualified Mask and optical layers must share one source identity, one PhotoSession and one transform `M(t)`. Do not swap to a separately cropped image to simulate continuity.
- If the chosen image's aspect ratio leaves space, preserve that space or fill it with the softened same-source backdrop; do not fill it with crisp duplicate imagery.

### 3.3 Main composition

- **Entry / source choice:** centered single scene; two clear equal-priority source actions.
- **Photo preview:** one complete photo as the visual focus; filename and normalized dimensions are secondary metadata; primary action 「开始识别」; secondary actions 「重新选择」 and back/close.
- **Processing:** one large photo scene with restrained optical layers and a small status line. No result panel yet, no model package picker, no multi-photo strip and no fixed progress percentage.
- **Result on desktop (viewport width ≥ 1024 CSS px):** two-column composition; photo left, result content right; default design-completion ratio 58:42 with a 24 px gap and 32 px outer gutter. Keep the full photo contained in the left hero.
- **Result below 1024 CSS px:** stacked composition; photo first, result content below; 24 px tablet or 16 px phone outer gutter. Do not squeeze a desktop split into a narrow viewport.
- Content maximum width: 1280 CSS px. Desktop shell gutter 32 px, tablet 24 px, phone 16 px. These are implementation geometry defaults for this contract, not measurements claimed from the separate private 1536×1024 reference.
- Use one document scroll where needed. Do not place independent scrollbars inside the photo, processing or result columns. Result CTAs must remain reachable without being covered by browser safe areas or the keyboard.

## 4. Entry-to-result state machine

### S0 — Landing entry

Trigger: user clicks the existing **「拍照识鱼开始」** CTA.

Action: enter the Sites recognition scene at the existing route. Create no PhotoSession until a source photo is selected/captured. Start automatic model-asset preparation in the background; do not expose manual model upload.

### S1 — Choose source

Show:
- Primary action: 「现场拍照」
- Secondary action: 「从相册选择」
- Back/close action

The source choice appears before opening the camera or file picker. The gallery action must work without first taking a photo. Both sources converge to the same preview and recognition pipeline.

### S2 — Camera / permission

- Request camera access only after an explicit 「现场拍照」 click.
- If permission is denied, camera is unavailable or the browser blocks camera access, explain the issue and keep 「从相册选择」 available.
- A successful capture proceeds to S3. Camera permission failure must not break the gallery path.

### S3 — Photo preview

- Decode the selected photo, normalize EXIF orientation and establish the source SHA, normalized width/height and a new PhotoSession.
- Display the complete source photo in contain mode.
- Provide 「开始识别」, 「重新选择」 and back/close.
- Changing the photo aborts/invalidate the previous session and clears all previous result, BBox, Mask, species and optical state.

### S4 — Automatic model preparation

- Detector and Classifier assets are public, versioned static assets, explicitly authorized for public distribution by the product owner.
- Load and verify the existing model files automatically; cache by version and content hash. Do not require the user to locate or upload ZIP/model files.
- Model preparation may proceed while the user chooses a source. If the user requests recognition before the runtime is ready, show the truthful preparation state and prevent duplicate submissions; retry is available after failure.
- Model download/initialization failure must show a specific recoverable error. Never stay indefinitely in “recognizing” and never return a fabricated species.
- This contract does not authorize publishing SAM ViT-B checkpoints; production Mask qualification remains a separate gate.

### S5 — Processing / SWOS

- Reuse the same PhotoSession, source SHA, normalized source geometry and existing Detector → BBox → Crop → Classifier path.
- Run the existing 2800 ms nominal Processing timeline P0–P7. The timeline is visual presentation only; it does not mark model work as completed.
- If inference finishes before 2800 ms, keep a restrained Processing state until the nominal minimum is reached. If inference takes longer, continue a restrained truthful waiting state until the real response arrives; do not fabricate progress or a result.
- Status text reflects actual lifecycle events only. No invented percentage, fake detector/classifier completion checkmarks or model output.
- Keep the source photo visible throughout. No blank teal replacement, sudden image swap or independent result-photo render during Processing.

### S6 — Result reveal / RR09

- Start only after the real result is ready and the nominal Processing minimum is satisfied.
- A valid HIGH result with `TRUSTED_MASK` may use the full fish-focus and gold-to-Chinese-glyph transfer defined by the existing SWOS/RR09 contracts.
- If a HIGH result has no trusted Mask, preserve the nominal reveal window but use the approved restrained title fallback: no precise contour glow and no fabricated fish-shaped mask.
- MEDIUM, LOW, NO_FISH, IMAGE_QUALITY and TECHNICAL_FAILURE do not receive celebratory HIGH reveal.
- The reveal is not allowed to re-run inference. Replaying the visual animation must not create a second model request.

### S7 — Result

Desktop: full source photo on the left, result information on the right. Phone/tablet: full source photo above result information.

Show only data returned by the current real recognition session:
- Chinese species name when the result state allows it.
- Existing confidence/candidate information using the current result-state authority.
- Size/weight/location/time only when genuinely measured or supplied; omit unavailable metrics from prominent cards rather than presenting invented numbers.
- Optional technical provenance, detector score and Mask status inside a collapsed 「识别详情」 disclosure, not as the primary consumer interface.

Use the current Sites actions:
- 「重新识别」 — rerun recognition for the current photo/session under the existing pipeline; prevent duplicate in-flight requests.
- 「重新拍摄」 — return to camera source.
- 「从相册选择」 — open the system picker directly.
- 「关闭」 — cancel/clear the active session and return to the existing landing view.

### S8 — Error and recovery

- `NO_FISH`: say no fish was detected; offer camera/gallery selection.
- `IMAGE_QUALITY`: explain that the photo cannot be reliably recognized; offer replacement.
- `MEDIUM`: show the existing candidate-confirmation path; do not auto-promote to HIGH.
- `LOW`: communicate uncertainty and offer replacement/retry; do not invent a species.
- `TECHNICAL_FAILURE`: give a clear failure reason when safe to expose, plus retry and source replacement.
- Unsupported file, empty file, decode failure, oversize file, permission denial, model fetch/hash/runtime failure and inference failure must have distinct actionable recovery states.
- All errors preserve the privacy boundary; do not upload the source photo for fallback inference.

## 5. Frozen motion and continuity invariants

This contract does not change SWOS/RR09 timing or optical authority.

- Processing: 2800 ms; P0–P7 at 0, 350, 950, 1200, 1550, 2050, 2600 and 2800 ms.
- RR09: 850 ms; R0=2800, R0.5=2900, R1=3000, R2=3180, R3=3350, R4=3480 and R5=3650 ms.
- Nominal total: 3650 ms when a valid eligible result is ready within the Processing minimum. Slow inference extends the truthful wait; do not compress the reveal to meet a wall-clock deadline.
- R0.5 is a real geometry midpoint, not a crossfade or delay.
- The same photo source and transform `M(t)` govern the sharp foreground, BBox, trusted Mask and optical layers.
- At P7 ambient water contribution is zero; qualified fish gold may remain stable through P7/R0 without a photo/geometry flash.
- Full gold transfer requires trusted Mask geometry. BBox-only and NONE/STALE states must not draw a rectangle as a fish outline.
- Optical parameter values still marked `CALIBRATION_CANDIDATE` remain candidates until product-owner visual sign-off.
- Respect reduced-motion preferences with a static/shortened equivalent, while preserving real data, state and controls.

## 6. Responsive and accessibility rules

Acceptance viewports: 1440×1000, 1280×800, 1024×768, 768×1024, 480 px width, 430×932, 390×844, 360×640 and 320×480.

- At ≥1024 px, Result is photo-left/result-right. Below 1024 px, Result is photo-top/result-bottom.
- Processing remains one photo-led scene at all sizes; never restore a three-column workbench on desktop or shrink it to fit a phone.
- At 320×480, use a compact photo height and ordinary page scrolling; no nested scrollbars, clipped primary actions or horizontal body overflow.
- All actions work with keyboard and touch; focus is visible; escape/back cancels safely; dialog/screen-reader labels identify camera, gallery, status and error.
- Honor `prefers-reduced-motion`; do not rely on color alone for success/failure.
- The five-frame/15-frame motion timeline is not a user-facing claim that model stages are complete.

## 7. Privacy, model and business invariants

- Detector `DET_FISH_v0.3` and Classifier `MODEL_CROP_M1_v0.2` are loaded from public static assets after SHA/spec verification. Cache them locally by model version/hash.
- Public model download is allowed; automatic photo upload is not. Network evidence must show model-asset requests separately from photo processing and confirm `networkPhotoUpload=false`.
- Keep existing model preprocessing, class mapping, confidence thresholds, result-state authority and HIGH/MEDIUM/LOW policy unchanged.
- No new paid backend, no Cloud Run/GPU service and no Google Cloud billing activation.
- No fake result, fixture result or sample fish photo in normal user flow.
- SAM/Mask status is explicit internally; only qualified Mask enables precise fish contour and gold transfer.

## 8. Exact-source acceptance gate

Implementation may start only after this contract and its machine-readable acceptance are adopted by the design authority. Sites must not be modified as part of this contract-freeze task.

The later implementation gate requires:
1. Contract JSON validation and control-plane references synchronized.
2. Exact Sites source SHA, Version ID and Deployment ID recorded.
3. Browser capture of every state and each listed viewport.
4. One continuous recording of the same PhotoSession from Processing P0 through R5; no stitched keyframes as a substitute.
5. Source-image identity/SHA and photo geometry measurements across P0, P7, R0, R0.5 and R1.
6. Actual model auto-load, cache reuse, hash validation and failure recovery.
7. Real result-state evidence; source photo is not uploaded.
8. SWOS/RR09 visual comparison, Mask trust-state verification, reduced-motion and accessibility checks.
9. Any failed or unrun check remains `FAIL` or `NOT_RUN`; contract freeze never implies runtime PASS.

**Freeze statement:** the user-approved layout and interaction decisions are frozen for the FishTrace Sites target. Pixel-level visual acceptance and runtime implementation remain separate, unpassed gates.
