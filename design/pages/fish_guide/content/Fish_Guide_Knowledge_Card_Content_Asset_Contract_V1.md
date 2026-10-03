# Fish Guide Knowledge Card Content & Asset Contract V1

Status: **FROZEN — DESIGN CONTENT CONTRACT**  
Date: **2026-09-30**  
Scope: **Fish Guide · 04 · 知识卡内容与资产**  
Applies to: **02 · 鱼种详情 / five fixed Knowledge Card positions**

## 0. Purpose

This contract closes the previous split between “内容合同” and “资产” and defines one implementation-ready authority for the five Fish Guide knowledge cards.

It does **not** reopen the frozen Species Detail page shell.

Parent authorities remain:

1. `design/pages/fish_guide/species_detail/frozen/Fish_Species_Detail_Baitiao_V1.png`
2. `design/pages/fish_guide/species_detail/Species_Detail_Page_Contract_V1.md`
3. This document for card-internal content, slot meaning, data mapping and asset rules.

Core rule:

> The five positions are stable knowledge roles, not collectible-card rarity tiers.

---

## 1. Fixed five-card information architecture

The five card positions are frozen as:

| Position | Canonical type | Display role | User question |
|---|---|---|---|
| 01 / 05 | `HERO` | 鱼种名片 | “这是什么鱼？” |
| 02 / 05 | `IDENTIFICATION` | 辨识特征 | “怎么认出它？” |
| 03 / 05 | `ECO` | 生态习性 | “它通常生活在哪里、怎么活动？” |
| 04 / 05 | `GEAR` | 装备建议 | “钓它通常用什么装备和饵？” |
| 05 / 05 | `SKILL` | 作钓要点 | “实际怎么找、怎么诱、怎么操作？” |

The order is fixed. Runtime must not reorder cards based on available content, catch count, popularity or user history.

Forbidden card families:

Game/progression semantics remain forbidden:

- ranking / 排行
- legendary / epic collectible tiers
- star rating used as collectible progression

The legacy `rarity / 稀有度`, `power / 战力`, and `challenge / 挑战等级` fields may appear only in the approved HERO descriptive rating panel defined by Visual Authority V2. They describe species metadata and must never represent player progression, unlock state, ranking, achievement, or collectible tier.

---

## 2. Card 01 · HERO / 鱼种名片

Required content priority:

1. Chinese species name
2. realistic / approved species subject
3. one concise species summary
4. optional scientific name
5. optional family / genus / category tag

Do not turn this card into a second page header.

Guardrails:

- species name: max 2 visual lines;
- summary: max 4 visual lines;
- scientific name is secondary;
- no record count on the knowledge card itself;
- no “已解锁 / 稀有 / 等级” badge.

Primary sources:

- `species.name_cn`
- `species.scientific_name`
- `species.category / family / genus`
- `species.summary`
- approved species media.

---

### Approved HERO descriptive rating metadata

V2 HERO may display five-star `rarity`, `power`, and `challenge` values as species descriptive metadata. The labels may be localized as 稀有度、力道感（力量值）、上手门槛（挑战值）. This is an editorial field-guide summary only: it does not unlock cards, change encounter state, rank users, or imply a game progression system.

## 3. Card 02 · IDENTIFICATION / 辨识特征

Purpose: help the user distinguish this species from visually similar fish.

Content structure:

- 3–5 high-signal visual features;
- each feature = short label + short explanation;
- optional 1–3 similar species with one decisive difference each.

Good feature categories include:

- body shape
- head / mouth
- fin placement
- scale / lateral-line traits
- color pattern
- tail shape

Avoid:

- vague adjectives such as “漂亮”“霸气”;
- model-confidence language;
- AI recognition probability;
- long taxonomic prose.

Primary sources:

- `content.features[]`
- `content.similar[]`
- `profile.features[]`
- `similarity[]`.

---

## 4. Card 03 · ECO / 生态习性

Purpose: explain the natural context of the species.

Canonical field order:

1. habitat
2. water layer
3. active season
4. behavior
5. diet

Primary sources:

- `knowledge.ecology.habitat`
- `knowledge.ecology.water_layer`
- `knowledge.ecology.season`
- `knowledge.ecology.behavior`
- `knowledge.ecology.diet`
- profile/fishing fallbacks only when they describe the same fact.

Rules:

- factual, concise field-guide tone;
- no fake precision when source data is missing;
- do not infer a value from unrelated catch history.

---

## 5. Card 04 · GEAR / 装备建议

Purpose: show a practical baseline setup without presenting one setup as universally correct.

Canonical field order:

1. method
2. rod
3. line
4. hook
5. bait

Primary sources:

- `knowledge.gear.method`
- `knowledge.gear.rod`
- `knowledge.gear.line`
- `knowledge.gear.hook`
- `knowledge.gear.bait[]`.

Rules:

- use ranges or contextual language when appropriate;
- bait list should prioritize a small number of representative choices;
- avoid product-brand recommendations inside the canonical Fish Guide card;
- do not imply safety, legal or local fishing-rule compliance when no such source exists.

---

## 6. Card 05 · SKILL / 作钓要点

Purpose: turn species knowledge into a short, actionable fishing sequence.

Canonical content sequence:

1. `find` — where / when to look
2. `attract` — how to create or select feeding opportunity
3. `action` — core execution / bite response
4. `tip` — one concise practical reminder

Rules:

- keep steps short;
- no autoplay tutorial or hidden secondary page;
- no “success rate” percentages unless backed by explicit product data;
- no gamified difficulty score.

Primary sources:

- `knowledge.skill.find`
- `knowledge.skill.attract`
- `knowledge.skill.action`
- `knowledge.skill.tip`.

---

## 7. Missing-content rules

The five positional slots remain stable even when data is incomplete.

### Complete

All required high-value content for the card role is available.

### Partial

Some fields are missing, but the card still has enough truthful content to be useful.

Rules:

- keep the same position;
- omit missing rows instead of inventing values;
- do not shift later cards forward;
- do not copy another card's text.

### Unavailable

There is not enough verified content for that card role.

Rules:

- preserve the position and `NN / 05`;
- show the shared unavailable treatment owned by **03 · 页面状态**;
- do not duplicate neighboring card artwork;
- do not use another species' content;
- do not generate substitute facts automatically.

---

## 8. Asset authority

### 8.1 Species subject

Use Fish Knowledge species media authority.

Do not source Fish Guide species artwork from:

- Recognition result crops;
- My Catches thumbnails;
- FishRecordDetail B-side generated fish assets;
- unrelated web images;
- screenshots cropped from a frozen full-page reference.

### 8.2 Knowledge-card artwork

Artwork must map to:

`speciesId + cardType + version`

The asset may provide atmosphere / illustration / composition support, but factual text remains sourced from structured knowledge data.

If the artwork contains baked-in factual copy, that copy must match the current structured authority exactly. Otherwise the asset must be replaced or treated as historical reference.

### 8.3 Image fitting

Inside the frozen Species Detail carousel:

- card artwork: **Fit / no stretch**;
- the page contract's active-card geometry remains authoritative;
- do not crop away meaningful fish anatomy or card labels to force parity.

---

## 9. Visual language

Keep:

- Morning Lake / lake-glass atmosphere;
- documentary / natural-history tone;
- gray-blue-green palette with restrained warm-gold editorial accents;
- deep lake-blue typography;
- realistic biological subject;
- strong fish identity;
- clear hierarchy between title, fact and supporting copy.

The legacy black/gold card family is preserved as historical material and marked SUPERSEDED_VISUAL_REFERENCE; it is not current V2 card-internal authority.

Avoid:

- neon HUD;
- metallic rarity frame tiers;
- star progression;
- trophy / badge hierarchy;
- collectible-game sparkle;
- aggressive fantasy lighting;
- multiple competing gold accents.

Gold is an editorial accent, not a reward signal.

---

## 10. Data/API compatibility

Current Android types already expose the required canonical fields through:

- `FishKnowledgeSpecies`
- `FishKnowledgeCard`
- `FishKnowledgeCardContent`
- `FishKnowledgeStructured`
- `FishKnowledgeProfile`
- `FishKnowledgeFishing`
- `FishKnowledgeSimilarity`

Canonical card types remain:

`HERO / IDENTIFICATION / ECO / GEAR / SKILL`

Legacy transport fields `rarity / power / challenge` are descriptive species metadata when rendered in the approved HERO rating panel; they are **not progression semantics** and must never drive unlock, ranking, or activation.

Runtime tabs or destinations such as “排行榜” are outside this frozen V1 information architecture.

---

## 11. Accessibility

- critical information must exist as text, not only inside artwork;
- card title and position must be announced;
- unavailable state must have a semantic label;
- do not encode card role only by color;
- dynamic type may increase card height only where the parent responsive contract permits; text must never be clipped to preserve screenshot parity.

---

## 12. Acceptance Gate

- [x] Five positions are fixed and named.
- [x] Each position has a clear user question and data mapping.
- [x] Rarity / power / challenge / ranking semantics are excluded.
- [x] Missing content preserves position and truthfulness.
- [x] No synthetic replacement facts or cross-species duplication.
- [x] Species media authority is separated from FishRecord / Recognition / B-side assets.
- [x] Knowledge-card artwork has a species + type mapping rule.
- [x] Structured data remains the factual source of truth.
- [x] V2 visual language is Morning Lake / lake-glass / documentary gray-blue-green with restrained warm gold, not game-card progression.
- [x] Accessibility requires text equivalents for critical information.

This contract replaces the former standalone **05 · 内容合同** and **06 · 资产** Design Manager placeholders.
