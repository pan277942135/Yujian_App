# Core UI V1 Shared Visual Extraction Report

Status: **PASS**

This report is derived from the six frozen page references, their two supplemental
references, the existing Core Visual System V1, and the six page contracts. It does not
redraw, crop, or reconstruct any frozen PNG.

## Decision summary

| Classification | Frozen-system decision | Why |
| --- | --- | --- |
| Design token | Color, typography, spacing, radius, glass, overlay, and motion remain tokens | They repeat as visual rules, not as image pixels. Their screen evidence and runtime usage are recorded in token_usage_map.json. |
| Native UI component | Navigation, icons, glass surfaces, cards, text, pills, statistics, separators, chevrons, and capture button | These need adaptive layout, accessibility, dynamic content, and state. Screenshot crops would fail all four. |
| Shared image asset | None in V1 | The repository contains full-page frozen references, not independently authored reusable bitmap layers. |
| Page-specific asset | Real catch photos, record covers, timeline thumbnails, species subjects, user avatar, page-specific scene treatment | These are dynamic, tied to one page/record, or inseparable from a frozen composition. |
| Reference-only visual | The six frozen page PNGs and the two supplemental PNGs | They are authoritative visual references and must remain read-only. |

## What is genuinely shared

### Tokens

- Morning lake blue-gray / mist white / rare gold palette
- Brand, display, and UI typography roles
- Spacing rhythm and soft radius scale
- Three glass levels and the restrained dark hero overlay
- Ambient Empty Home motion and restrained content-screen feedback

Token provenance is machine-readable in token_usage_map.json. No new visual language was
introduced during this extraction.

### Components

The following component contracts are shared and implementation-ready:

- YuJianPrimaryCaptureButton
- YuJianCatchHeroCard with HOME and DETAIL variants
- MistGlassCard
- TopNavigation
- FishRecordRowCard
- FishGuideCard with lit and unlit states
- AchievementAnnotation

The full component contract, source screens, variants, token dependencies, and runtime
notes are in component_map.json.

## Hero-card decision

YuJianCatchHeroCard is one component family, not unrelated Home and Detail cards.

- HOME uses the latest real catch, species, measurements, and contextual time/location
  where needed.
- DETAIL uses the selected FishRecord's real catch media, species, measurements, location,
  and low-weight edit affordance.
- Both preserve the fish as the first visual subject, large shared radius, and only a
  light bottom readability gradient.

## Capture-button decision

YuJianPrimaryCaptureButton is shared by Empty Home, Normal Home, and My Catches primary
capture entry. It is a native circular component with a vector camera glyph:

- white core
- thin MorningGold rim
- deep blue-gray glyph
- restrained highlight
- shared motion tokens

It must never become a per-page bitmap or a separate page-specific design.

## Fish Guide decision

FishGuideCard is one Personal Natural Field Guide system. The lit reference is
fish_guide_v2.png; supplemental/fish_guide_unlit_state.png supplies the unlit state. The
state difference must not cause a second card family or game semantics. Discovery,
observation, and personal archive language remain primary.

## Why no shared bitmap was extracted

The shared environment is a visual world, not a common pixel layer. Each frozen page
combines scenery with page-specific composition, content, and UI. Extracting a lake,
camera, gradient, card, or fish from a screenshot would produce a partial asset and leak
unrelated text, layout, or content into production. The correct implementation is:

    frozen reference
    + shared token/component contract
    + page-specific environment treatment
    + runtime data/media

The exact category-level decisions are in shared_asset_manifest.json and
page_asset_map.json.

## Runtime guidance

- Compose/vector/native: TopNavigation, icons, glass, capture button, cards, pills,
  typography, dividers, gradients, annotations, and carousel behavior.
- Runtime media: real fish photographs, user avatar, record memory media, timeline
  thumbnails, and record covers.
- Separately approved source only: any future reusable environment or species bitmap.
- Reference-only: all current frozen PNGs.

## Follow-up boundary

Android implementation should consume the token and component contracts in a dedicated
follow-up. This design-source task intentionally makes no app, Android runtime, backend,
model, or worker change.
