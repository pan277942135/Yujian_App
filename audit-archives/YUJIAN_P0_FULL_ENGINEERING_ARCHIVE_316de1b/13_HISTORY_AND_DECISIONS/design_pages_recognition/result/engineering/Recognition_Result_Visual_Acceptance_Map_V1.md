# Recognition Result Visual Acceptance Map V1.1

Status: **FROZEN — USER PHYSICAL / VISUAL AUTHORITY**

Machine-readable authority: `visual_acceptance_map.json`.

Final acceptance compares the runtime against the seven Frozen PNG assets.
This document defines engineering invariants; it does not replace physical
visual acceptance.

## State map

| State | Hero target at 360dp | Required composition |
| --- | ---: | --- |
| High | ~322 × 210dp | Hero, Species Identity, vertical Metadata, Story, Dual CTA |
| Medium | ~322 × 178dp | Hero, unified Candidate Panel, then resolved record flow |
| Low | ~322 × 178dp | Hero, Low message/actions, Metadata, Story, Dual CTA |
| No Fish | ~322 × 245dp | Evidence Hero, unified Recovery Panel |
| Image Quality | ~322 × 214dp | Evidence Hero, unified Recovery Panel |

## Required visual assertions

- Result title is centered independently of Back;
- Species Selector remains left-title `BACK_TITLE`;
- High Metadata is vertical three-row, never a horizontal strip;
- Story title, placeholder, multiline editor, and `{count}/300` counter render;
- High/Medium/Low use original oriented photo with bbox-protected Subject Crop
  Fill when safe;
- Recovery uses full-source Evidence Fit;
- all Glass is shared MistGlass with restrained Result radius;
- Save uses the light Result Save variant with DeepLakeBlue content and
  MorningGold edge;
- Recovery buttons are not teal-solid;
- Medium suggested is weaker than explicit Gold selected;
- Selector selected remains Teal;
- Low unresolved Metadata and Story remain visible;
- unresolved Low save never creates a FishRecord and resumes only after manual
  species selection;
- Recovery title, guidance, Retake, and Gallery occupy one Glass panel.

No verifier threshold may be changed to obtain acceptance. Final visual
acceptance is intentionally not performed by this task.
