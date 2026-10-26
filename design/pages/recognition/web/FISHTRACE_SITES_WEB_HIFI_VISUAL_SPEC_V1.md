# FishTrace Sites Web 高保真视觉与结构化原型 V1

**Design ID:** FISHTRACE-SITES-WEB-HIFI-VISUAL-V1  
**Prototype route:** #page/recognition_flow_v1_2/hifi/fishtrace_sites_web_hifi_workspace  
**Status:** DESIGN_ONLY / INTERACTIVE_PROTOTYPE  
**Product runtime:** NOT_VERIFIED  
**Sites project:** appgprj_6a50a71561708191a6ea8c6194ce68d4

## 1. Design deliverables

This document defines the browser-based high-fidelity design and interactive structured prototype integrated into the existing Yujian Design Manager. It is not a production Sites change, a second Sites project, a parallel product, or a claim of real model inference.

- The high-fidelity view covers entry, source choice, photo preview, Processing, RR09 result reveal, result and recoverable error.
- The structured prototype exposes a selectable layer tree, layer properties, responsive viewport switch, state navigation, local photo selection and motion-timeline preview.
- The same user-approved Sites contract governs the prototype: immersive single scene, source choice first, desktop photo-left/result-right, phone photo-top/result-below.
- The existing public design asset real_catch_source.jpg is used only as a labeled visual fixture. It is not represented as a new user upload or an inference result.

## 2. Visual direction

- Documentary, natural lake-water atmosphere; quiet teal, lake-white, slate text and restrained morning gold.
- Main surface: #F7FAF8; deep text: #173A3B; teal action: #286B63; pale teal: #DCECE8; morning gold: #D9AE62; secondary text: #6D8180.
- Avoid neon, HUD frames, radar lines, rectangular fish outlines, game-like particles and oversized gradients.
- The user-facing page itself remains one product scene. The Design Manager editor frame and layer inspector are design tooling outside the product scene; they must not be implemented into Sites.
- Product shell maximum width 1280 CSS px; desktop gutter 32 px, tablet 24 px, phone 16 px. Main result split is 58:42 with 24 px gap at 1024 px and above; below 1024 px it stacks.
- Buttons have at least 44 CSS px target height, visible focus, explicit labels and clear enabled/disabled states.

## 3. Screen-by-screen high fidelity

### A. Entry — 拍照识鱼开始

- One calm scene, compact brand header and close/back action.
- A headline and one short explanation sit beside a soft, abstract morning-water visual; no sharp stock fish photo is used as an unexplained decorative object.
- Primary CTA: 「拍照识鱼开始」.
- A quiet privacy note states that photo processing is local in this prototype. This is a prototype property, not proof of Sites runtime behavior.
- CTA enters source choice. It does not open camera/gallery immediately.

### B. Source choice — 现场拍照 / 从相册选择

- Two equal-priority source cards are the first action after the entry CTA.
- Camera and gallery cards have distinct icons, clear Chinese labels, concise helper text and visible focus states.
- Gallery can be used directly without a prior camera capture.
- The prototype may open a local browser file picker for a real local photo preview. The camera control uses the browser capture intent where supported; this does not guarantee that a physical camera is available in the Design Manager iframe.
- No photo bytes are sent to a remote service.

### C. Photo preview

- Show the selected or clearly labeled design-fixture photo as the only sharp foreground image.
- The foreground uses object-fit: contain; do not stretch the source or crop off fish head/tail. If a backdrop is used, it must be the same source with strong blur and low contrast.
- Present file name, dimensions and source type as secondary information.
- Primary CTA: 「开始识别」. Secondary actions: 「重新选择」 and back/close.
- Desktop preview uses a spacious photo-led composition; phone stacks photo, metadata and actions.

### D. Processing / SWOS

- One photo-led scene; do not show a result panel or developer dashboard.
- The same source image remains visible. The background may use restrained water-light / caustic overlays, but the prototype has no trusted Mask for its fixture and must not draw a fish-shaped contour.
- Status is restrained and truthful about this being a visual prototype; there is no fake percent or false detector/classifier completion.
- The editor provides P0–P7 timeline controls for inspecting the nominal 2800 ms Processing optical rhythm. This is a visual-timeline demonstration, not model-work telemetry.
- RR09 timing remains 850 ms and the nominal total remains 3650 ms in the contract. The current prototype does not claim production-grade Mask-to-glyph rendering; with Mask trust NONE, the RR09 fallback is a restrained Chinese title fade.

### E. RR09 result reveal

- Show the same source photo through the 850 ms RR09 window; keep photo identity and geometry continuous.
- Expose R0, R0.5, R1, R2, R3, R4 and R5 in the structured timeline inspector, with global times 2800, 2900, 3000, 3180, 3350, 3480 and 3650 ms.
- The fixture has Mask trust NONE. Therefore the high-fidelity fallback is a restrained Chinese species-name fade only; no fish contour, rectangular outline or fabricated gold transfer.
- The reveal uses clearly labeled demo content and transitions to the result screen only as a visual-prototype interaction, not as real inference evidence.

### F. Result

- At desktop widths >=1024 CSS px, photo on the left and result content on the right, 58:42 with a 24 px gap.
- At widths below 1024 CSS px, photo first and result below.
- The prototype result uses clearly marked sample content for visual inspection only. The outer editor banner always states that it is demo data and no model inference is connected.
- Result hierarchy: state label, Chinese species-name slot, confidence/summary, only-present-if-real metadata, primary retry action and source replacement actions.
- Technical provenance and Mask state belong in a secondary/collapsed detail disclosure, not the consumer-facing hero.

### G. Error / recovery

- One focused error scene with a clear explanation, retry and source replacement.
- Camera permission failure must leave gallery available.
- No-fish, image-quality and technical-failure states remain semantically distinct in the product contract; this prototype's shared error view is a visual starting point, not the final full error-state runtime acceptance.

## 4. Structured layer contract

The JSON machine contract defines each scene's layer tree and property metadata. The Design Manager prototype supports:

- Select a layer from the tree to highlight its corresponding visible element.
- Inspect layer role, description, state, geometry and token references.
- Switch between layer, property and flow panels.
- Switch Desktop / Phone preview without changing the frozen layout decision.
- Navigate Entry, Source Choice, Photo Preview, Processing, RR09 Reveal, Result and Error.
- Choose a local photo from the file picker and preview it in memory.
- Inspect Processing frames P0–P7 and replay the nominal visual rhythm.
- Reset to the entry state.

Layer selection and state changes are design-tool interactions. The layer inspector is not part of the Sites product UI.

## 5. Responsive acceptance sizes

1440×1000, 1280×800, 1024×768, 768×1024, 480 px width, 430×932, 390×844, 360×640 and 320×480.

- Result layout switches at 1024 CSS px.
- At phone widths, all primary and recovery actions remain reachable with ordinary page scrolling.
- No horizontal body overflow, no nested scroll pane inside the product content, and no desktop two-column result squeezed onto a phone.
- Respect reduced-motion preference; motion controls must not be required to access content.

## 6. Authority and limits

- User decision authority: FISHTRACE-SITES-WEB-INTERACTION-V1.
- Motion authority: SWOS 15-frame V1.1, shared-photo transition contract V1, SWOS/RR09 optical acceptance V1.2 and the Web motion technical contract.
- Public visual fixture: design/system/fish_media_display_v1/assets/real_catch_source.jpg, already registered in the repository's public design asset set.
- No production Sites source is modified by this design task.
- No real inference, detector/classifier result, trusted Mask, deployment parity, or visual UAT is claimed.
- Product implementation must be a later task against the exact Main-adopted contract and this prototype, then verified on the exact deployed Sites source SHA.
