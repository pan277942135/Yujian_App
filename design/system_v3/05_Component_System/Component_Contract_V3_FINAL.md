# Component Contract V3.1 Final

**Aggregate status: READY · 16/16 FROZEN.** The existing public registry has 16 entries; no new component was added. Frozen page visuals and assets were not modified.

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
| `media.video` | fish_record_detail_v2 | **FROZEN** | Verify source ratio, contain/letterbox behavior, six states, accessible state announcements, and no focus trap; page controls remain page-owned. |
| `data.metadata_row` | recognition_result_v1, fish_record_detail_v2 | **FROZEN** | Trace every displayed value to its source/field contract. |
| `data.statistic_card` | home_normal_v1 | **FROZEN** | Compare the statistic group to NH04 and the Normal Home spec. |
| `input.selector` | recognition_result_v1, fish_record_detail_v2, my_catches_v2 | **FROZEN** | Verify 320dp two-column grid/search, ≥360dp common grid, 300ms search, single/multi-select commits, keyboard/focus, 44dp targets and fontScale. |
| `input.search` | my_catches_v2 | **FROZEN** | Verify B1–B5 against the frozen search spec: focused/recent, results, empty, search+filter, detail return. |
| `input.bottom_sheet` | profile_edit_v1, recognition_result_v1, account_privacy_v1 | **FROZEN** | Avatar: options are Camera / Photo Picker / Cancel; cancel leaves state unchanged; choosing/cropping does not upload until explicit profile Save. |

## Page authority and reuse rules

- Use the registered component ID; do not copy it under a new name or parameter set.
- Token references must resolve to the Typography, Color or Geometry registry. Asset and SVG references must resolve to Asset and Icon registries.
- Read `component_contract.page_variants` before applying a shared component to a page. A page authority owns its declared geometry and visual exceptions.
- A shared token is the default only where the page has no explicit frozen override. If the two disagree, preserve the Frozen page authority and record the conflict; do not auto-close it.
- An unlisted state is not implied. Review entries must not be implemented from guessed geometry or behavior.
- Android must use the registered SVG identity; system-icon substitution is not permitted.

## Selector Core + Species Picker Variant — FROZEN

**Component ID:** `input.selector`

- **Purpose:** search and choose a declared species/option across Recognition Result, FishRecordDetail and My Catches.
- **Visual:** reuse `design/system/components/species_picker_v1/visual/authority/Species_Picker_V1_Reference_Board.png` and the frozen Recognition Result selector board. Species picture/name and page-declared category/information/special-card slots remain sourced by the cited page authority. No new screen or alternative picker is introduced.
- **Geometry:** 320dp uses a 2×3 Common grid and one-column All list; ≥360dp uses three Common columns; Search remains full width. Row/media measurements stay in the frozen selector contract. Minimum actionable target is `touch.minimum` = 44×44dp.
- **States:** CLOSED, OPEN, QUERY_INPUT, SEARCHING, RESULTS, NO_RESULTS, EMPTY_DATA, ERROR, SELECTED, MULTI_SELECTED, COMMITTED, RETURNED, CANCELLED.
- **Interaction:** search Chinese formal name/pinyin/initials/aliases with 300ms debounce. Single-select tap commits `species_id` and returns immediately. Multi-select toggles persist while browsing, then return to F1 with OR semantics. Back dismisses IME before leaving; cancel does not alter the prior single-select value.
- **Tokens/assets:** `type.body`, `color.deep_lake_blue`, `color.action_primary`, `touch.minimum`, registered search/check SVGs, Fish Knowledge species media identity.
- **Responsive / accessibility:** search remains visible around IME; content may scroll. At larger `fontScale`, names wrap and containers grow; never shrink. Expose roles, selected state, results/empty/error announcements and deterministic focus order; no focus trap.
- **Acceptance:** verify 320/360/393/411dp, search/clear/debounce, single and multi-select commit/return behavior, 44×44dp targets, IME/back, accessibility focus and fontScale 1.0/1.15/1.3.
- **Boundary:** My Catches F2 reuses the same frozen picker and selection styling; no F2-specific bitmap was created. Its page shell and data-filter contract remain page-owned.

## Shared Video Surface + Page Video Experience — FROZEN

**Component ID:** `media.video`

- **Purpose:** display attached user video and expose its state without defining player controls.
- **Visual:** source-preserving media surface for Idle, Loading, Playing, Paused, Error and Unavailable. Use registered status/surface tokens; no shared player buttons.
- **Geometry:** preserve oriented intrinsic source ratio unless a cited page supplies an approved frame ratio; contain the entire source and letterbox. No forced 16:9, stretch or crop. Page placement and frame geometry remain page-owned.
- **Interaction:** Shared Video Surface reports media state. Page Video Experience owns buttons, focusable actions, autoplay/retry strategy, playback policy and business logic.
- **Tokens/assets:** `geometry.video.aspect_policy`, `geometry.media.contain`, `geometry.media.letterbox`, semantic Info/Error; attached media uses `runtime.user_media`.
- **Accessibility / focus:** expose title/description and current state; Loading/Error/Unavailable are polite status updates. Surface is not a focus stop by default; page-owned controls provide labels and focus order; never trap focus.
- **Acceptance:** exercise all six states, landscape/portrait/rotated media, source preservation, letterbox behavior, status announcements and focus traversal without adding page controls.

## Shared Foundation acceptance

A component is frozen for reuse only when its declared shared core has resolvable token/asset references, explicit page variants, named states, interaction and responsive behavior, an accessibility rule, and checks a tester can execute without guessing. The aggregate Component system is READY when all 18 entries are FROZEN, their dependencies resolve, acceptance checks are executable, and each entry declares its page boundary. Page-specific control policies remain outside the shared freeze.


## Recognition Capture runtime surfaces · Phase 3-A closure

### media.camera_preview

- **Component ID / status:** media.camera_preview · FROZEN · type runtime_surface.
- **Purpose / visual:** show only the live runtime camera provider stream. This surface is not Frozen Artwork and has no static asset SHA.
- **Geometry / tokens:** follow the oriented stream ratio; use geometry.media.cover in a flexible preview box without distortion; use the registered dark fallback only when unavailable.
- **State / responsive:** IDLE, STARTING, READY, SUSPENDED, ERROR, UNAVAILABLE; recalculate crop after viewport/inset changes.
- **Lifecycle / accessibility:** Capture host owns the provider binding; release it on exit, backgrounding or permission loss. Announce state changes, not frames.
- **Acceptance:** verify ratio, permission loss, lifecycle release and accessible status.

### camera.capture_surface

- **Component ID / status:** camera.capture_surface · FROZEN · type runtime_surface.
- **Purpose / visual:** compose Camera Preview → Overlay Layer → Bottom Action Area with registered Navigation/Action components and the existing capture-button asset.
- **Geometry / state:** flexible preview, inset-safe overlay, fixed action above navigation inset, target ≥ touch.minimum; CAPTURE_READY, CAPTURE_PERMISSION_REQUIRED, CAPTURE_PERMISSION_DENIED, CAPTURE_ERROR and CAPTURE_SUCCESS.
- **Interaction:** no capture before permission; Gallery remains independent; no system-edge gesture exclusion; prevent duplicate capture while pending.
- **Asset / acceptance:** preserve capture-button Asset Registry identity; 208×208px is not a dp size. Verify SHA, state guards, safe area, permission and lifecycle ownership.

These two registered runtime surfaces extend the previously frozen 16 shared contracts for the Phase 3-A Capture page. The effective registry and validators now cover 18 public contracts; no Frozen PNG or application code changed.
