# Fish Guide V2

Role: **Natural Collection / Personal Field Guide**

Status: **FROZEN**

## Frozen visual reference

- Canonical reference: design/system/core_visual_v1/reference/fish_guide_v2.png
- SHA-256: e1002e0ae2b7e87c070907fb14d25448e122b4479cb23eeccac88dedce230fcf
- System authority: YuJian Core Visual System V1
- Verification: reference_manifest.json and verify_core_ui_v1_references.py

## Concept
A personal natural fish guide built from the user's real catches.

Keep:
- title and low-salience recorded species count / progress
- one central FishGuideCard with adjacent previews
- real record count on lit species
- one shared card system for lit and unlit species

## Home behavior

- Search and filter are out of the frozen Home scope.
- The carousel is finite and changes species only through user interaction.
- The optional first-visit discover hint waits for 600 ms of idle time, shifts the
  carousel 14 dp toward the next species over 180 ms, then returns over 260 ms.
  It runs once, is canceled by user input, never changes the selected species, and
  is omitted when Reduce Motion is active.
- Unlit means that the user's saved records contain no catch for that species. It
  remains visible, readable, browseable, and navigable to its species detail.
- Lit state and record count come from successfully saved catch records. Demo
  catalog data must not create user discovery state.
- The home card is single-sided; species information belongs to Species Detail.
- There is no page indicator or bottom call to action.

## Visual semantics
Prefer discovery / observation / archive.
Avoid unlock / rarity / level / game-card semantics.

FishGuideCard should present a realistic biological subject, restrained progress and archive metadata.
