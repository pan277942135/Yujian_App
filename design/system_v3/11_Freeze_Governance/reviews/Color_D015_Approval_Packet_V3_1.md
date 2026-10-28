# D-015 Color Semantic Approval Packet V3.1

**Decision:** `D-015` · **Status:** `RESOLVED / APPROVED` · **Color gate:** `READY`

This packet is retained as decision evidence. The owner approved the recommended mappings listed below on 2026-10-11; the effective production values now live only in `Color_Tokens_V3_FINAL.json`. Alternatives remain non-effective. No Frozen visual was modified.

## Proposal-time evidence

These statements describe the source review before the D-015 approval; the selected mappings below now replace the unresolved role gap.

- `color.action_primary` is an existing primary action token and is now approved as Brand Primary.
- `color.lake_blue` is an existing Lake Blue token; Core Visual names Secondary Lake Blue; it is now approved as Brand Secondary.
- `color.morning_gold` is already mapped as a restricted brand/important-moment accent. Computed contrast on LakeWhite is **2.15:1**; it is not a body-text or generic status color.
- Before approval, Success, Warning and Info had no shared semantic token. Account & Privacy labels `color.legacy_e66b61ff` as Danger; it remains page-scoped and is not a global Error token. Its contrast on LakeWhite is **3.02:1**, so it is not a normal-text candidate.

## Approved recommended choices

Ratios below are computed from exact opaque foreground/background pairs. Text proposals are compared to a conservative normal-text threshold of 4.5:1; large text and meaningful UI graphics are also reported at 3:1. These references follow WCAG 2.2 criteria 1.4.3 and 1.4.11 ([text contrast](https://www.w3.org/TR/WCAG22/#contrast-minimum), [non-text contrast](https://www.w3.org/TR/WCAG22/#non-text-contrast)).

| Role | Recommended candidate | Contrast | Alternative | Contrast | Status |
|---|---|---:|---|---:|---|
| Brand Primary | `#0F7A78` on `#F7FAFB` | 4.91:1 | `#0B2D4B` on `#F7FAFB` | 13.43:1 | APPROVED: recommended |
| Brand Secondary | `#34556F` on `#F7FAFB` | 7.49:1 | `#168B88` on `#F7FAFB` | 3.94:1 | APPROVED: recommended |
| Success | `#216B4A` on `#E6F2EB` | 5.59:1 | `#0F7A78` on `#EAF6F5` | 4.66:1 | APPROVED: recommended |
| Warning | `#735000` on `#FFF1CC` | 6.50:1 | `#7C5E25` on `#FFF7E5` | 5.65:1 | APPROVED: recommended |
| Error | `#8F2D25` on `#FCE8E6` | 6.94:1 | `#A1332B` on `#FDECE9` | 6.07:1 | APPROVED: recommended |
| Info | `#245D73` on `#E8F2F6` | 6.39:1 | `#34556F` on `#F7FAFB` | 7.49:1 | APPROVED: recommended |

The contrast ratios are color-pair calculations, not page/device acceptance. The active-accent alternative for Brand Secondary is below the normal-text threshold and is limited to large text or meaningful non-text use unless the owner chooses another context. The existing Danger value is retained only as a legacy-page reference; its 3.02:1 result on LakeWhite is insufficient for normal text.

## Glass and image context

`color.glass_white` uses 76.08% white. Its calculated composite is `#FDFEFE` over LakeWhite and `#C5CDD4` over DeepLakeBlue. DeepLakeBlue text contrasts strongly with these two test composites, while white text on the dark-base composite is only 1.61:1. These calculations describe two solid-base examples only. Photography, gradients, overlays, crop positions and local glass variants require page-specific evaluation against the actual composed surface.

## Frozen scope / non-promotion rules

- The 92-token review snapshot is historical. The effective registry now contains 100 tokens (92 existing + 8 approved status tokens). The 64 legacy identity mappings stay source-local with `NO_NEW_USE`.
- `Color_Semantic_Candidates_V3_1.json` and the HTML preview are non-effective decision evidence. Approved recommended values are tokenized in the canonical registry; alternatives were not selected.
- D-015 resolves the global Error mapping to the approved dark-red semantic tokens. Account & Privacy Danger stays page-scoped and is not promoted.
- Do not infer or recolor colors from Frozen PNGs or SVGs.
- No design page, runtime code, Android theme or component has been changed to preview these candidates.

## Approval record

Approved mapping: the recommended candidates above for Brand Primary, Brand Secondary, Success, Warning, Error, and Info. Danger remains page-scoped. The canonical registry records token IDs and foreground/surface pairs. Contrast values are static color-pair calculations only.

## Review files

- [Structured candidates](Color_Semantic_Candidates_V3_1.json)
- [Static palette review](Color_D015_Review_V3_1.html)
- Effective registry: `design/system_v3/04_Design_Tokens/Color/Color_Tokens_V3_FINAL.json` (100 tokens; status READY).

**Gate:** D-015 is RESOLVED. Only the recommended mapping pairs are production tokens; this packet is historical review evidence. The effective authority is the final Color Token registry.
