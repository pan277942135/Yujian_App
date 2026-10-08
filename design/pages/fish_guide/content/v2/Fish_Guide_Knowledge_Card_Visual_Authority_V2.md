# Fish Guide Knowledge Card Visual Authority V2

Status: **FROZEN — DESIGN VISUAL AUTHORITY V2**  
Scope: **Fish Guide · 04 · 知识卡内容与资产**  
Source package: `Fish_Guide_Knowledge_Card_V2_30PNG.zip`  
SHA authority: `Fish_Guide_Knowledge_Card_V2_manifest.csv`

## Authority

V2 is the current internal artwork authority for the five fixed Knowledge Card positions. The 30 PNGs under `frozen/` are exact source binaries from the canonical package. They are not screenshots, crops, re-exports, or regenerated copies.

The frozen Species Detail authority continues to own the page shell, header, carousel geometry, NN / 05 indicator, My Species area, and background. V2 owns only the internal visual of the five Knowledge Cards. Legacy black/gold card pixels remain preserved historical material and are marked `SUPERSEDED_VISUAL_REFERENCE`; they are not current card-internal authority.

## Visual language

- Morning Lake / lake-glass atmosphere.
- Documentary natural-history presentation.
- Gray-blue-green palette with restrained warm-gold editorial accents.
- Deep lake-blue typography and clear Chinese hierarchy.
- Realistic species identity, complete border, fit/no-stretch handling, and no collectible-game sparkle.
- Gold is an editorial annotation, never a reward or progression signal.

## Fixed mapping

| Slot | Card type | User question |
|---|---|---|
| 01 / 05 | HERO | 这是什么鱼？ |
| 02 / 05 | IDENTIFICATION | 怎么认出它？ |
| 03 / 05 | ECO | 它通常生活在哪里、怎么活动？ |
| 04 / 05 | GEAR | 钓它通常用什么装备和饵？ |
| 05 / 05 | SKILL | 实际怎么找、怎么诱、怎么操作？ |

Order is fixed and assets are keyed by `species_id + card_type + version`.

## HERO rating rule

The approved HERO rating panel may show five-star **稀有度 / 力道感（力量值） / 上手门槛（挑战值）** as descriptive species metadata. These values describe the species and are not collectible rarity, player power, difficulty progression, unlock state, ranking, or achievement progression. They must not alter navigation, card availability, encounter state, or activation state.

## Data and authority boundary

Structured knowledge remains the factual source of truth. If baked-in artwork text conflicts with structured data, record `KNOWN_CONTENT_MISMATCH`; do not silently edit the PNG or seed data. Activation remains a separate product decision and is outside this frozen design package.

## Acceptance

- 30 / 30 source PNGs present.
- Manifest contains 30 / 30 rows with dimensions, byte sizes, and SHA-256.
- Design paths retain exact PNG bytes.
- Legacy black/gold material is preserved and marked `SUPERSEDED_VISUAL_REFERENCE`.
- No Android runtime, APK, renderer, model, or new importer work is part of this package.
