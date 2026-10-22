# M1 P5 · Compose / Token / Image / Data Binding Contract

Status: **ENGINEERING_HANDOFF_READY · FIGMA/DEVICE QA NOT PASS**

PR: [#166](https://github.com/pan277942135/Yujian_App/pull/166)  
P5 config commit: `7c38c631365d8b8c3274b4a508ce2646ab107b47`  
Unified importer input: [Processing_Engineering_Import_Config_V1.json](Processing_Engineering_Import_Config_V1.json)

## Component mapping

| Native/logical component | Android implementation | Bound data / coordinate contract |
| --- | --- | --- |
| Processing page | `RecognitionProcessingScene` | one pipeline, four internal phases, three product visuals |
| PhotoViewport | `RecognitionPhoto` + `RecognitionSourcePhoto` + `RecognitionImageTransform` | current `SelectedImage.bitmap`, primary FIT transform |
| Decorative FIT-band fill | `RecognitionSourcePhoto` | same user image, secondary CROP transform at alpha .16; decorative only |
| AI Edge Field V1 | `RecognitionAmbientField` | seven frozen cubics in `photoContentRect` |
| Fish Focus A/B/C | `RecognitionFishFocus` | real normalized bbox and generated subject mask alpha; no focus without real bbox |
| Status Card | `RecognitionStatusOverlay` + `RecognitionProcessingVisualTokens` | key by product state; exact shared first-state copy pending transcription from Frozen 02 |
| Back | `RecognizingScreen` IconButton | safe insets, exits without save; Layout Contract points to candidate `ic_back_v1.svg`, while current Android uses Material AutoMirrored ArrowBack |

The full component tree, token IDs/runtime values, test-only fixture constraints and field-level fallback contract are in the machine config.

## Image and data bindings

- Runtime photo: required `recognition.normalized_image` from Identify / `SelectedImage`. It stays opaque and unchanged; missing input returns to Capture.
- Frozen references: exactly the three manifest PNGs (02/03/04, 941×1672 with declared SHA-256 values). These are reference assets, not replacements for the runtime user photo.
- Detector phase and assessment: real `RecognitionProgress`; do not fabricate bbox, focus, classifier candidate, ETA or confidence.
- Fish Focus input: real `NormalizedFishBox` in source coordinates; Level A mask is the real `FishSubjectResult` bitmap alpha.
- Classifier outcome: real `ProductionRecognitionResult`; required before RESULT_READY. No Processing candidate/confidence is displayed.
- Error exits: No Fish, Image Quality Warning and Technical Failure remain distinct.

## Unified importer contract

This is importer configuration, **not another plugin**. Work A owns the single unified plugin. It may connect this file only after NH01 minimum import validation passes. Its target is the existing RP01 page: inspect first; never create a second RP01; stop if the existing page cannot be found. Logical frame keys are the three product states only. Frozen PNGs bind as locked references; native editable reconstruction is a sibling. Re-runs reuse/update stable logical keys and never duplicate nodes or delete source art.

Frame dimensions are deliberately null. The PNG's 941×1672 pixel size is not sufficient evidence for an editable Figma frame size. The config has no Figma node IDs, and all actual node IDs must come from a real Figma write.

## Status

Engineering mapping is complete across P1–P5 and is machine-validated for JSON syntax, exactly seven paths, exactly three product states/PNG references, and zero fabricated Figma IDs. This is **ENGINEERING_HANDOFF_READY**, not Android runtime acceptance or Frozen high-fidelity PASS.

Still open: RP-A01…A14 runtime/contract repair items; exact shared IMAGE_RECOGNIZING copy transcription; real production-mask fixture; edge-seed visual authority; Motion Resolve implementation; responsive Status/Back UI checks; unified plugin NH01 gate; real RP01 native nodes/export; Geometry / Glyph / Optical ROI / Pixel Diff and independent QA.

Acceptance IDs PROC-V-01…PROC-E-02 remain mapped with execution states in the JSON. Source tests were inspected but not run in this handoff; API28, device captures, Frozen diff and QA remain NOT_RUN.
