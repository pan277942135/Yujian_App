# NH01 Gemini Native Preview — Asset Closure V1

This page is a GitHub-native static preview of the checked-in Gemini Native V3 structure and its registered assets. It does not depend on live Figma/MCP calls, external image services, external font CDNs, or a paid design API.

## What it proves

- The approved Morning Lake background, registered brand artwork, Gemini V3 avatar fallback and complete camera artwork load from the repository.
- Their downloaded bytes are SHA-256 checked against the asset binding manifest.
- The page renders the Gemini Top / Middle / Footer structure with responsive viewport presets and a font-scale diagnostic control.
- A 1080×1920 Chromium screenshot is emitted as a CI artifact from a 360×640 CSS viewport at device scale factor 3.

## What it deliberately does not claim

- It is not a live Figma node readback and does not prove Figma import or export.
- It is not an Android screenshot or a Frozen pixel-parity pass.
- The Hero media area remains BLOCKED_ASSET_SOURCE: the candidate's provenance, usage rights and bbox ground truth are not verified. The photo is not copied or newly published to GitHub Pages because the source-photo manifest classifies the user-provided photos as sensitive.
- Neighbor cards are not populated with duplicated or invented photos.
- Runtime data and interactions remain the responsibility of the Android implementation and its exact-SHA API 28 gate.

## Entry point

design/manager/nh01_acceptance/gemini-native-preview.html

The page status and CI screenshot are diagnostics only. release_gate_effect remains NONE.
