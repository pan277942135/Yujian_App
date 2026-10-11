# D-015 Color Semantic Approval Packet V3.1

**Decision:** `D-015` · **Status:** `AWAITING_OWNER_APPROVAL` · **Color gate:** `REVIEW`

This packet presents explicit candidates from the existing V3.1 palette and clearly labeled review-only proposals. It does not authorize a production color, modify any Frozen visual, or change the canonical 92-token registry.

## Existing evidence

- `color.action_primary` is an existing primary action token, not an approved global Brand Primary role.
- `color.lake_blue` is an existing Lake Blue token; Core Visual names Secondary Lake Blue, but the global semantic mapping remains unapproved.
- `color.morning_gold` is already mapped as a restricted brand/important-moment accent. Computed contrast on LakeWhite is **2.15:1**; it is not a body-text or generic status color.
- Success, Warning and Info have no shared semantic token. Account & Privacy labels `color.legacy_e66b61ff` as Danger; it remains page-scoped and is not a global Error token. Its contrast on LakeWhite is **3.02:1**, so it is not a normal-text candidate.

## Recommended choices for owner review

Ratios below are computed from exact opaque foreground/background pairs. Text proposals are compared to a conservative normal-text threshold of 4.5:1; large text and meaningful UI graphics are also reported at 3:1. These references follow WCAG 2.2 criteria 1.4.3 and 1.4.11 ([text contrast](https://www.w3.org/TR/WCAG22/#contrast-minimum), [non-text contrast](https://www.w3.org/TR/WCAG22/#non-text-contrast)).

| Role | Recommended candidate | Contrast | Alternative | Contrast | Status |
|---|---|---:|---|---:|---|
| Brand Primary | `#0F7A78` on `#F7FAFB` | 4.91:1 | `#0B2D4B` on `#F7FAFB` | 13.43:1 | PROPOSED / NOT APPROVED |
| Brand Secondary | `#34556F` on `#F7FAFB` | 7.49:1 | `#168B88` on `#F7FAFB` | 3.94:1 | PROPOSED / NOT APPROVED |
| Success | `#216B4A` on `#E6F2EB` | 5.59:1 | `#0F7A78` on `#EAF6F5` | 4.66:1 | PROPOSED / NOT APPROVED |
| Warning | `#735000` on `#FFF1CC` | 6.50:1 | `#7C5E25` on `#FFF7E5` | 5.65:1 | PROPOSED / NOT APPROVED |
| Error | `#8F2D25` on `#FCE8E6` | 6.94:1 | `#A1332B` on `#FDECE9` | 6.07:1 | PROPOSED / NOT APPROVED |
| Info | `#245D73` on `#E8F2F6` | 6.39:1 | `#34556F` on `#F7FAFB` | 7.49:1 | PROPOSED / NOT APPROVED |

The contrast ratios are color-pair calculations, not page/device acceptance. The active-accent alternative for Brand Secondary is below the normal-text threshold and is limited to large text or meaningful non-text use unless the owner chooses another context. The existing Danger value is retained only as a legacy-page reference; its 3.02:1 result on LakeWhite is insufficient for normal text.

## Glass and image context

`color.glass_white` uses 76.08% white. Its calculated composite is `#FDFEFE` over LakeWhite and `#C5CDD4` over DeepLakeBlue. DeepLakeBlue text contrasts strongly with these two test composites, while white text on the dark-base composite is only 1.61:1. These calculations describe two solid-base examples only. Photography, gradients, overlays, crop positions and local glass variants require page-specific evaluation against the actual composed surface.

## Frozen scope / non-promotion rules

- The 92 canonical color tokens remain unchanged in this packet. The 64 legacy identity mappings stay source-local with `NO_NEW_USE`.
- Review-only candidate colors in `Color_Semantic_Candidates_V3_1.json` and the HTML preview are not tokenized production values. Do not copy them into page/component work before approval.
- Do not promote Account & Privacy Danger to global Error without an explicit owner decision.
- Do not infer or recolor colors from Frozen PNGs or SVGs.
- No design page, runtime code, Android theme or component has been changed to preview these candidates.

## Approval fields

Owner must record: (1) Brand Primary mapping or alternate, (2) Brand Secondary mapping or alternate, (3) Success/Warning/Error/Info candidate pairs or replacements, (4) accepted foreground/background contexts and contrast thresholds, and (5) whether Account & Privacy Danger remains page-only or is approved for a specific global Error scope. Until those fields are explicitly approved, the JSON `approval_record` remains false/null and Color stays REVIEW.

## Review files

- [Structured candidates](Color_Semantic_Candidates_V3_1.json)
- [Static palette review](Color_D015_Review_V3_1.html)
- Canonical registry (read-only in this checkpoint): `design/system_v3/04_Design_Tokens/Color/Color_Tokens_V3_FINAL.json` (92 tokens; status REVIEW)

**Gate:** D-015 is the only Phase 2 open decision. Color is not READY; Shared Foundation is not READY; Phase 3 remains closed.
