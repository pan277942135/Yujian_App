# Component Contract V3.1 Final

**Aggregate status: REVIEW.** The existing public registry has 16 entries: 14 FROZEN and 2 REVIEW. No new component was added. Frozen page visuals and assets were not modified.

FROZEN means the shared contract boundary below is explicit. A page-owned size, radius, crop, placement, or interaction remains governed by its cited page authority; the shared contract does not overwrite it. Component freeze is a design contract, not runtime acceptance.

Each JSON entry contains purpose, visual rule, geometry boundary, state model, interaction, token and asset dependencies, responsive rule, accessibility rule, source authority, page variants, acceptance checks and unresolved blockers.

| Component ID | Page references | Status | Frozen/Review scope |
|---|---|---|---|
| `navigation.top_title` | my_catches_v2, fish_guide_v2 | **FROZEN** | At each referenced viewport, the title uses remaining width, remains clear of system insets, and follows registered ellipsis/fontScale behavior. |
| `navigation.back` | recognition_result_v1, account_privacy_v1, profile_edit_v1 | **FROZEN** | Verify 56dp minimum bar, 44×44dp target, 22dp glyph, 8dp gap, registered SVG, and immediate-parent navigation. |
| `navigation.action` | fish_record_detail_v2 | **FROZEN** | Verify action order and declared icon identity at each page; no extra action or default glass pill; title may ellipsize. |
| `action.primary_button` | auth_login_v2, auth_register_v2, recognition_result_v1 | **FROZEN** | Verify each declared normal/pressed/loading/disabled/focus state, 28dp radius exception, label token, and disabled non-activation. |
| `action.text_action` | auth_login_v2, auth_register_v2, recognition_result_v1, home_normal_v1, fish_record_detail_v2 | **FROZEN** | Verify 44×44dp target, no visible container, declared tone/chevron and 90ms pressed transition. |
| `action.icon_action` | fish_record_detail_v2, my_catches_v2 | **FROZEN** | Verify registered SVG, 24×24 viewBox source, 44dp target and declared normal/pressed/disabled/focus state. |
| `surface.glass_surface` | recognition_result_v1, fish_record_detail_v2, my_catches_v2, fish_guide_v2 | **FROZEN** | Resolve GLASS_A/B/C, border, opacity, blur and shadow to the glass token registry. |
| `surface.card` | home_normal_v1, recognition_result_v1, fish_record_detail_v2, my_catches_v2, fish_guide_v2 | **FROZEN** | For every registered page variant, identify its Frozen authority before rendering. |
| `media.hero` | home_normal_v1, fish_record_detail_v2, my_catches_v2 | **FROZEN** | Verify source bytes unchanged, approved frame, safe Fill or Safe Fit, and no crop outside FishSafeRect. |
| `media.image` | recognition_flow_v1_2, recognition_result_v1, fish_record_detail_v2, my_catches_v2, fish_guide_v2 | **FROZEN** | Verify declared mode, source preservation, loading/ready/fallback/error and no non-uniform stretch. |
| `media.video` | fish_record_detail_v2 | **REVIEW** | Confirm a video tile retains the FishRecordDetail grid and attached-media counting semantics. |
| `data.metadata_row` | recognition_result_v1, fish_record_detail_v2 | **FROZEN** | Trace every displayed value to its source/field contract. |
| `data.statistic_card` | home_normal_v1 | **FROZEN** | Compare the statistic group to NH04 and the Normal Home spec. |
| `input.selector` | recognition_result_v1, fish_record_detail_v2, my_catches_v2 | **REVIEW** | Verify Recognition Result single-select against its eight-state spec at 320/360/393/411dp. |
| `input.search` | my_catches_v2 | **FROZEN** | Verify B1–B5 against the frozen search spec: focused/recent, results, empty, search+filter, detail return. |
| `input.bottom_sheet` | profile_edit_v1, recognition_result_v1, account_privacy_v1 | **FROZEN** | Avatar: options are Camera / Photo Picker / Cancel; cancel leaves state unchanged; choosing/cropping does not upload until explicit profile Save. |

## Page authority and reuse rules

- Use the registered component ID; do not copy it under a new name or parameter set.
- Token references must resolve to the Typography, Color or Geometry registry. Asset and SVG references must resolve to Asset and Icon registries.
- Read `component_contract.page_variants` before applying a shared component to a page. A page authority owns its declared geometry and visual exceptions.
- A shared token is the default only where the page has no explicit frozen override. If the two disagree, preserve the Frozen page authority and record the conflict; do not auto-close it.
- An unlisted state is not implied. Review entries must not be implemented from guessed geometry or behavior.
- Android must use the registered SVG identity; system-icon substitution is not permitted.

## Remaining component review items

### `media.video` — REVIEW

FishRecordDetail freezes video as an attached-media grid item and preserves media counts. It does not freeze a shared video frame/aspect, playback controls, focus order, accessibility description, or play/pause/failure presentation. Keep the shared video component REVIEW until a page contract closes those fields.

### `input.selector` — REVIEW · C-014

Recognition Result single-select has a frozen visual baseline. FishRecordDetail uses the same shared picker. My Catches F2 requires multi-select, but its visual contract remains partial. Shared focus/TalkBack and acceptance coverage also remain open. Keep one shared Species Picker authority; do not fork a duplicate picker.

## Shared Foundation acceptance

A component is frozen for reuse only when its declared shared core has resolvable token/asset references, explicit page variants, named states, interaction and responsive behavior, an accessibility rule, and checks a tester can execute without guessing. The aggregate Component system remains REVIEW while either review entry is open.
