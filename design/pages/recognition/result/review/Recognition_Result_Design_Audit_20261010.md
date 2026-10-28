# Recognition Result — Design System Audit (2026-10-10)

Scope: **Design governance, registry, Design Manager and written component exceptions only**.  
Android/Kotlin, detector/classifier, CI/AVD, APK, production main, Frozen PNG bytes and their manifests are **out of scope**.

## Authority

1. Five canonical Result state Frozen PNGs (05–09) define state-specific visual hierarchy.
2. Two additional Frozen boards own Species Selector V1 and Metadata Edit Flow V1 **within their own scopes**.
3. Current Result V1.1 state READMEs, visual/behavior specs and machine-readable geometry/component contracts specify current design behavior.
4. The generic `core_visual_v1/reference/recognition_result_v1.png` is shared visual-language reference only; it cannot override a state Frozen PNG.
5. Generic shared Top Navigation V1 / Action Button V1.1 govern normal uses; explicitly registered Result composition exceptions take priority only for RR01–RR05.
6. Android implementation is **not** design authority, and runtime parity remains separately unverified.

## Findings and decisions

| ID | Finding | Severity | Design-only disposition |
|---|---|---|---|
| DS-RR-01 | Result registry says `BACK_TITLE`, while current 05–09 Result contracts say centered `BACK_CENTER_TITLE`. Shared Top Navigation exposes only three left-aligned/standard variants. | P0 | Register a **page-scoped** centered-title composition; do not add an unapproved global fourth variant. Selector remains `BACK_TITLE`. |
| DS-RR-02 | Design Manager Low summary says there is no Metadata/Story or Save before selecting a species, directly contradicting current Low V1.1 Frozen state/readme and `LOW_MANUAL` behavior. | P0 | Correct RR03 summary and status/CTA notes; the editable draft is visible before species resolution, while persistence remains gated. |
| DS-RR-03 | Registry's global buttons mapping implies teal PRIMARY, strong continue and 44/56 pair; the latest Result component/geometry contracts require light `RESULT_SAVE`, muted continue and equal-width dual CTA. | P0 | Register explicit Result-only action exceptions; preserve the existing globally frozen three-level Action Button system and its non-Result mappings. |
| DS-RR-04 | Page index needs to expose state-specific Hero dimensions and source-photo policies without suggesting a generic Hero aspect ratio. | P1 | Update RR00–RR05 menu descriptions, preserve canonical PNG sources and media contract. |
| DS-RR-05 | Recovery pages and Medium selection can be confused if an index shows generic independent buttons or auto-selected candidates. | P1 | Clarify one Recovery Glass Panel, separate No Fish / Image Quality guidance, and Medium explicit selection. |
| DS-RR-06 | Design and Runtime status can be conflated in review. | P1 | Keep Result design frozen and Runtime/Evidence as PARTIAL / NEEDS_CLOSURE. No runtime change implied. |

## Approved 3+2 state map

| Menu | Hero at 360dp | Page design behavior |
|---|---|---|
| RR01 High | 322 × 210dp | Species identity; vertical Length/Weight/Location; Story; equal dual CTA; source photo fish-safe Crop Fill or Fit |
| RR02 Medium | 322 × 178dp | Unified candidates panel; model suggestion ≠ user selection; after confirmation reuse Metadata, Story and dual CTA |
| RR03 Low | 322 × 178dp | Manual/Retake **and editable Metadata/Story/Dual CTA before species selection**; unresolved CTA routes to LOW_MANUAL |
| RR04 No Fish | 322 × 245dp | Complete-source Evidence Fit; one Recovery Glass Panel with Retake/Gallery; no FishRecord |
| RR05 Image Quality | 322 × 214dp | Complete-source Evidence Fit; different quality guidance; one Recovery Glass Panel; no FishRecord |
| RR06 Content Edit | two existing Frozen boards | Species Selector and Metadata Edit remain independent authority indexes, not a sixth Result state |

## Design constraints retained

- No new state, classifier threshold, Hero source replacement, cropped design screenshots, generated fish-photo fill or Processing effect.
- Story copy follows `写下这次鱼获的故事` / `记录这一刻的感受……`, max 300 Unicode code points with live counter.
- Real voice action only; no decorative/no-op microphone.
- Action first-entry is idle; saving/loading only after activation, with duplicate-save prevention.
- Responsive layouts preserve reachable actions, safe insets, keyboard behavior, and real-photo orientation.
- Design Manager links to canonical references without copying or recompressing them.
- Existing 05–09 and the two edit authorities retain their Frozen status and fingerprints.

## Evidence boundary

This audit checks the repository's source text, registry, declared image paths and machine contracts. It does **not** claim a fresh pixel-by-pixel inspection or visually approved re-freeze of all seven binary PNGs; no new screenshots were generated. Exact visual deviations, if any, require direct visual inspection of the existing canonical authority set.

## Review gate

- All changes stay under `design/`.
- Seven visual authorities unchanged.
- No `app/`, `.github/`, CI or detector/crop/model edits.
- The current page-specific contracts remain the source of numerical geometry.
- Existing shared component variants remain frozen; exceptional uses are named and discoverable instead of silently altering generic definitions.
