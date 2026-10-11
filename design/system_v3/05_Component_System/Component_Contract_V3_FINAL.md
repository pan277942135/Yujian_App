# Component Contract V3.1 Final

**System status: REVIEW.** Registry contains the 16 existing public components; this work adds no component and does not rewrite any Frozen component visual. Eight entries retain REVIEW because their cross-page geometry/behavior is not approved.

The final registry is the queryable authority. Each entry has purpose, visual rule, geometry, state, interaction, token/component/asset dependencies, responsive rule, source authority, page references and acceptance rule.

| Component ID | Page references | Status | Acceptance focus |
|---|---|---|---|
| navigation.top_title | my_catches_v2, fish_guide_v2 | FROZEN | At each referenced viewport, the title uses remaining width, remains clear of system insets, and follows registered ellipsis/fontScale behavior. |
| navigation.back | recognition_result_v1, account_privacy_v1, profile_edit_v1 | FROZEN | Verify 56dp minimum bar, 44×44dp target, 22dp glyph, 8dp gap, registered SVG, and immediate-parent navigation. |
| navigation.action | fish_record_detail_v2 | FROZEN | Verify action order and declared icon identity at each page; no extra action or default glass pill; title may ellipsize. |
| action.primary_button | auth_login_v2, auth_register_v2, recognition_result_v1 | FROZEN | Verify each declared normal/pressed/loading/disabled/focus state, 28dp radius exception, label token, and disabled non-activation. |
| action.text_action | auth_login_v2, auth_register_v2, recognition_result_v1, home_normal_v1, fish_record_detail_v2 | FROZEN | Verify 44×44dp target, no visible container, declared tone/chevron and 90ms pressed transition. |
| action.icon_action | fish_record_detail_v2, my_catches_v2 | FROZEN | Verify registered SVG, 24×24 viewBox source, 44dp target and declared normal/pressed/disabled/focus state. |
| surface.glass_surface | recognition_result_v1, fish_record_detail_v2, my_catches_v2, fish_guide_v2 | REVIEW | Current source confirms glass colors and no-stretch behavior. Pass generic reuse only after each consumer registers padding/radius/height; currently REVIEW. |
| surface.card | home_normal_v1, recognition_result_v1, fish_record_detail_v2, my_catches_v2, fish_guide_v2 | REVIEW | Each consumer must name a card authority and crop/ratio rule. No universal shape is approved; currently REVIEW. |
| media.hero | home_normal_v1, fish_record_detail_v2, my_catches_v2 | FROZEN | Verify source bytes unchanged, approved frame, safe Fill or Safe Fit, and no crop outside FishSafeRect. |
| media.image | recognition_flow_v1_2, recognition_result_v1, fish_record_detail_v2, my_catches_v2, fish_guide_v2 | FROZEN | Verify declared mode, source preservation, loading/ready/fallback/error and no non-uniform stretch. |
| media.video | fish_record_detail_v2 | REVIEW | Verify page frame/aspect, not_added/ready/playing/paused/failed and control reachability; shared frame is not frozen. |
| data.metadata_row | recognition_result_v1, fish_record_detail_v2 | REVIEW | Every field must have source, long/missing behavior, type/color refs and documented row alignment; shared geometry is not frozen. |
| data.statistic_card | home_normal_v1 | REVIEW | Compare Normal Home group to its page authority, preserve Numeric/Caption roles, and do not promote Font A to a global scale. |
| input.selector | recognition_result_v1 | REVIEW | Pass only after C-014 closes with visual, behavior, accessibility, runtime and acceptance states; currently REVIEW. |
| input.search | my_catches_v2 | REVIEW | Verify idle/focus/typing/results/empty/clear/error, IME insets and query retention; shared geometry remains REVIEW. |
| input.bottom_sheet | my_catches_v2 | REVIEW | Verify closed/open/drag/submitting/error plus snap/dismiss/focus/IME and page-owned geometry; shared contract remains REVIEW. |

## Reuse rules

- Use the component ID; do not copy a component and assign a new name or parameter set.
- Every declared token reference must exist in Typography, Color or Geometry registry. Icon asset references must resolve to Asset Registry and Icon Registry.
- Page-specific visual geometry remains in the named authority. A review entry cannot be promoted to shared reuse until its token gaps close.
- State names and interaction transitions are explicit in the JSON. An unlisted state is not an implied state.
- Android must use the registered SVG identity; system-icon substitution is not allowed by this design contract.

## Review entries

Glass Surface and Card lack a shared cross-page padding/radius/height envelope. Video lacks a shared frame/aspect/control contract. Metadata Row and Statistic Card retain page-specific geometry. Selector remains C-014. Search and Bottom Sheet lack complete generic focus/IME/snap/dismiss contracts. Their per-page authorities stay valid; the shared entries remain REVIEW.

## Acceptance

A component is ready for shared reuse when its token and asset references resolve, states and interactions are testable, every visual property maps to a token or named Frozen authority, page references are explicit, and the acceptance rule can be checked without a visual guess.