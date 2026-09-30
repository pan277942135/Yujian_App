# Recognition Result Component Map V1

Status: **FROZEN**

Machine-readable authority: `component_map.json`.

Rule: every visible Result element must resolve to either:
1. a frozen shared design-system component;
2. a Result-private component defined by this package;
3. runtime media governed by a media contract.

No page-private lookalike of a shared component is authorized.

## Shared component mapping

| Visible element | Component authority | Variant |
| --- | --- | --- |
| Back + 识别结果 | Top Navigation V1 | BACK_TITLE |
| Back glyph | Icon Action V1 | NAVIGATION / Back |
| 修改鱼种 | Text Action V1 | NORMAL / LIGHT + separate Chevron |
| 都不是？选择其他鱼种 | Text Action V1 | MUTED / LIGHT + Chevron |
| 继续记录记忆 | Action Button V1.1 | SECONDARY_STRONG |
| 保存本次鱼获 | Action Button V1.1 | PRIMARY |
| 手动选择鱼种 | Action Button V1.1 | SECONDARY_STRONG |
| Low 重新拍摄 | Action Button V1.1 | SECONDARY_MUTED |
| Recovery 重新拍摄 | Action Button V1.1 | PRIMARY |
| Recovery 从相册选择 | Action Button V1.1 | SECONDARY_STRONG |
| Voice | Icon Action V1 | CONTEXT / voice |
| Fit support surface | Mist Glass | GLASS_A |
| Page background | Background System | BG_CONTENT |
| Spacing/radius | Core UI tokens | spacing_radius_v1 |

## Result-private components

### ResultHeroViewport
- outer page geometry only;
- media content supplied by Recognition Result Hero Media V1;
- never consumes frozen screenshot pixels.

### ResultSpeciesIdentityRow
- species title + optional Text Action;
- height: 44dp canonical;
- title takes remaining width;
- action never pushes title to two lines at normal font scale.

### ResultMetadataStrip
- exactly three fields: Length / Weight / Location;
- 72dp canonical height;
- no settings-list chevrons;
- no generic “编辑记录” row.

### ResultMetadataField
- label + value/placeholder;
- entire field hit target;
- editor behavior owned by Metadata Input Contract.

### ResultMemoryNote
- label: `留下本次鱼获感言`;
- multiline value/placeholder;
- optional real Voice action;
- no no-op mic.

### ResultNumericEditSheet
- lightweight Bottom Sheet for Length / Weight;
- geometry and commit behavior owned by Metadata Edit Flow V1;
- shared Text Actions for 清除 / 完成;
- OS decimal IME, not a custom keypad.

### ResultLocationPickerSheet
- medium Bottom Sheet for location search / current / recent;
- no separate-page navigation;
- selected location commits immediately;
- location permission only on explicit Use Current Location.

### ResultPlaceSearchField
- query-only place search;
- typing never commits Result metadata;
- 300ms debounce.

### ResultRecentLocationList
- device-local max 3;
- clearing history is independent from current Result location.

### ResultCandidateRow
- Medium only;
- 2–3 candidates;
- no carousel/autoplay;
- card geometry and states owned by Candidate Card V1.

### ResultCandidateCard
- states: Default / Suggested / Selected / Pressed / Disabled / Focus;
- not a Fish Guide card;
- no rarity/confidence UI.

### ResultRecoveryPanel
- used by No Fish and Image Quality;
- title + guidance only;
- no save/metadata controls.

### ResultInlineError
- retryable save error only;
- appears above CTA;
- does not replace the page.

## State composition

### High
BACK_TITLE → ResultHeroViewport → ResultSpeciesIdentityRow → ResultMetadataStrip → ResultMemoryNote → 44/56 action pair.

### Medium unresolved
BACK_TITLE → ResultHeroViewport → prompt → ResultCandidateRow → other-species Text Action.

### Medium resolved
Keep candidate confirmation visible → ResultMetadataStrip → ResultMemoryNote → 44/56 action pair.

### Low unresolved
BACK_TITLE → ResultHeroViewport → message → 58/42 action pair.

### Low after manual species selection
Reuse resolved-result composition; do not create Low-only metadata components.

### No Fish / Image Quality
BACK_TITLE → ResultHeroViewport(Evidence Fit) → ResultRecoveryPanel → Primary Retake → Secondary Gallery.

## Prohibited substitutions

- Material Button/OutlinedButton/TextButton directly styled per page when a shared action component exists.
- custom back row.
- generic settings ListItem for Metadata.
- Fish Guide card reused as Medium candidate.
- detector crop used as Hero.
- frozen screenshot crop used as a runtime asset.
