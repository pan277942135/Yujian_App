# Recognition Result · Scoped Top Navigation Exception V1

Status: **PAGE-SCOPED FROZEN COMPOSITION**  
Reviewed: 2026-10-10  
Applies only to: RR01–RR05 (High / Medium / Low / No Fish / Image Quality)

## Authority and purpose

Result V1.1 state references (05–09), `result/engineering/layout_geometry_contract.json`, and the Result Visual Spec require a **viewport-centered** title `识别结果` with a Back action at left. The global Top Navigation V1 `BACK_TITLE` contract instead requires a left-aligned title. These two compositions are not the same.

This is an **explicit page-level exception**, not a newly frozen fourth general-purpose Top Navigation variant. The three globally frozen menu variants remain TITLE_ONLY, BACK_TITLE and BACK_TITLE_ACTIONS.

## Required composition

- Back is the shared **Icon Action / NAVIGATION / Back**, retaining the shared 44dp target, glyph, semantics, focus and accessible label.
- Title is centered against the **viewport**, not centered between Back and a fabricated right-hand spacer.
- Safe-drawing/status-bar inset is applied by the host page exactly once.
- Typography, color and minimum touch targets are inherited from the shared design tokens unless the Result 05–09 Frozen state reference explicitly governs a visible difference.
- No Processing AI border, glass navigation capsule, unnecessary right-side utility icon, or page-private Back glyph.
- Species Selector V1 is **not** covered: it uses the normal shared `BACK_TITLE` variant and its own Frozen selector reference.

## Conflict-resolution rule

For RR01–RR05: Result 05–09 Frozen PNG > Result V1.1 geometry and component contracts > this scoped exception > generic `BACK_TITLE` usage examples. For other screens, the ordinary frozen Top Navigation V1 contracts remain unchanged.

## Governance

Machine profile name: `BACK_CENTER_TITLE`. This profile is a **Result-owned composition key**, not a selectable global `top_navigation_v1.variants[]` ID. Do not add it as a generic shared variant or silently relabel it as `BACK_TITLE`.

Related authority:
- `design/pages/recognition/result/engineering/layout_geometry_contract.json`
- `design/pages/recognition/result/engineering/component_map.json`
- `design/pages/recognition/design/05_Result_High_Frozen.png` through `09_Error_Image_Quality_Frozen.png`
