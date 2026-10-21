# NH01 Gemini Native Preview — Asset Closure V1

This page is a GitHub-native static preview of the checked-in Gemini Native V3 structure and its registered assets. It does not depend on live Figma/MCP calls, external image services, external font CDNs, or a paid design API.

## What it proves

- The approved Morning Lake background, registered brand artwork, Gemini V3 avatar fallback and complete camera artwork load from the repository.
- Their downloaded bytes are SHA-256 checked against the asset binding manifest.
- The page renders the Gemini V3 Top / Middle / Footer structure and exposes a **content-first Adaptive V2 preview candidate** beside the unchanged V3 Baseline. The browser candidate measures the actual rendered metadata block to estimate its readable minimum, continuously adjusts spacing for font/short-height pressure, and only scrolls the Middle region when that minimum cannot fit. Typography scales from both usable width and height. Left/right peeks are sized relative to the active card and positioned outside its bounds; the 320×480 candidate compacts top/footer spacing so the card and CTA fit without unnecessary scrolling or bottom blank space. The camera artwork also scales from both safe canvas width and height (56–80dp), while its touch target remains at least 72dp; browser evidence records artwork and touch bounds separately. This is a review prototype; native Compose parity is not claimed until the separate Android policy implementation and exact-SHA API 28 matrix agree.
- A 1080×1920 Chromium screenshot is emitted as a CI artifact from a 360×640 CSS viewport at device scale factor 3. The screenshot simulates 24dp top / 24dp bottom safe insets to match the Compose responsive fixture; Android system status/navigation glyphs are not drawn.

## What it deliberately does not claim

- It is not a live Figma node readback and does not prove Figma import or export.
- It is not an Android screenshot or a Frozen pixel-parity pass.
- The Hero media area remains BLOCKED_ASSET_SOURCE: the candidate's provenance, usage rights and bbox ground truth are not verified. The photo is not copied or newly published to GitHub Pages because the source-photo manifest classifies the user-provided photos as sensitive.
- Neighbor cards are not populated with duplicated or invented photos.
- Runtime data and interactions remain the responsibility of the Android implementation and its exact-SHA API 28 gate.

## Entry point

design/manager/nh01_acceptance/gemini-native-preview.html

The native implementation of Adaptive V2 is tracked separately in the NH01 Android follow-up. Until its source and the browser candidate pass paired exact-SHA runtime checks, this page is a diagnostic prototype only. release_gate_effect remains NONE.
