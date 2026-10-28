# Recognition Result Species Selector V1

Status: **FROZEN**

Feature: `recognition_result_v1`  
Scope: shared species resolution interaction launched from Recognition Result; this authority owns the selector only and does not change Frozen Result states 05–09.

## Frozen visual authority

- Board: `design/pages/recognition/result/species_selector/frozen/Recognition_Result_Species_Selector_V1_Frozen.png`
- Manifest: `design/pages/recognition/result/species_selector/frozen/manifest.json`
- Source: `中文鱼种选择界面设计规范展板.png`
- SHA-256: `3c5a1ed62f8f55e2aad2cee01bce98b9e92e0f06e6ebfc799b85d24f6650d98d`

The composite PNG is the exact uploaded board. It is the only frozen visual authority for this selector. Keep the eight logical states in this spec; do not split or replace the board with HTML, CSS, SVG, screenshots, crops, or regenerated images.

## Navigation and structure

Use shared Top Navigation V1, variant `BACK_TITLE`, with title **选择鱼种**. Do not use a large centered title.

When query is empty, show:

`BACK_TITLE → Search → 最近选择 → 常见鱼种 → 全部鱼种 → Context-aware Bottom Safe Area`

When query is nonempty, show only:

`BACK_TITLE → Search → Search Results`

Search hides 最近选择、常见鱼种、全部鱼种. Search Empty copy is **没有找到相关鱼种** with action **清除搜索**. V1 does not show **找不到我的鱼种？**.

## Search behavior

Match the Chinese formal name, pinyin, pinyin initials, and registered aliases to the same `species_id`. For example, 草鱼、caoyu and cy resolve to the same species. Debounce is **300 ms**. States are `IDLE`, `SEARCHING`, `RESULTS`, and `EMPTY`.

## Recent and common species

- Recent list maximum: **3**.
- Add only after the user explicitly selects and commits a species; AI Top-1 suggestions never write Recent.
- Newest first; deduplicate by `species_id`; selecting a recent item again moves it to first.
- At widths ≥360dp, Common Species uses a 3-column × 2-row grid, maximum 6.
- At 320dp, use a 2-column × 3-row grid.
- Common cards contain only fish image and species name. Do not show confidence, rarity, Fish Guide detail, discovered badges, or descriptions.

## All Species and media

Group All Species by the pinyin initial of the formal Chinese name. A row targets 64–68dp height, 56×40dp fish media, 16sp/22sp name, and 18dp chevron. A selected row shows fish + name + check; it hides the chevron.

The single media source is `Fish Knowledge → species_id → cover_image`. Fish Guide, Medium candidates and this selector use the same species media authority. When unavailable, use the approved neutral fish placeholder. Never substitute another species, generate fish artwork, crop a frozen screenshot, show the text “鱼”, or create a selector-only artwork system.

## Selection and commit

Current V1 selection uses Teal **#0F7A78**, 2dp border, 6–8% teal fill, teal check circle and white check. Retire the old Gold selected state. If a species appears in Recent, Common and All, its selected state is synchronized in all three places.

Tap gives teal feedback for **100–150ms**, commits `species_id`, then returns to Result. Do not add a confirmation button.

## Entry contexts

Create `SpeciesSelectorEntryContext` with exactly these contexts:

| Context | Entry | Current selection / Back | Bottom action |
|---|---|---|---|
| `EDIT_CONFIRMED` | High or resolved Result → 修改鱼种 | Preselect current species; Back cancels and keeps the previous species | Hide 暂不确认鱼种 |
| `MEDIUM_OTHER` | Medium → 都不是？选择其他鱼种 | Return Medium unresolved until a species is committed | Allow 暂不确认鱼种; unresolved return creates no FishRecord |
| `LOW_MANUAL` | Low → 手动选择鱼种 | Return Low unresolved until a species is committed | Allow 暂不确认鱼种; unresolved return creates no FishRecord |

## Responsive, background and glass

Freeze widths **320, 360, 393 and 411dp**. At 320dp Recent chips wrap, Common is two columns and All is one column. At ≥360dp Common is three columns. With larger font scale, Common and list names may use two lines and rows may grow; never shrink text to force one line.

Use `BG_CONTENT`, not `BG_ENV_HERO`. Keep environment salience near 75% of Home. Preserve the lake through glass surfaces; raise the white base slightly behind All Species, avoid a hard pure-white card, and keep content legible ahead of the scenery.

## Frozen logical states

- SS01 Default
- SS02 Selected
- SS03 Search Results
- SS04 Search Empty
- SS05 320dp Compact
- SS06 EDIT_CONFIRMED
- SS07 MEDIUM_OTHER
- SS08 LOW_MANUAL

This composite board plus these logical definitions is sufficient; no additional PNGs are required.