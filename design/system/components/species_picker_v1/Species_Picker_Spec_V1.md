# Shared Species Picker V1

Status: **PARTIAL**  
Single-select visual baseline: **FROZEN**  
Visual authority: `design/system/components/species_picker_v1/visual/authority/Species_Picker_V1_Reference_Board.png`

## Shared structure

- Search supports Chinese names, pinyin, pinyin initials, and aliases.
- Recent selections and common species appear before the complete species list.
- Species rows/cards use the Fish Knowledge species image and name.
- Selection state is explicit and consistent in recent, common, and full-list sections.
- Search with no match provides a clear empty result and a clear-search action.
- Fish imagery comes from Fish Knowledge. Do not generate replacement fish images in this component.

## Variants

### SINGLE_SELECT — visual baseline frozen

Used by Recognition Result and FishRecordDetail Edit Species. Tapping a species commits that species and returns to the caller. The recovered board documents default, selected, search, no-result, small-screen, and confidence-related caller states.

### MULTI_SELECT — behavior shared, visual closure pending

Used by My Catches F2. Multiple species can remain selected while the user browses and searches. Leaving F2 returns the selected set to F1, where multiple values in the species dimension combine with OR. The recovered visual board is single-select only; it does not freeze a My Catches multi-select screen.

## Scope boundaries

- This is a shared component, not a new product route.
- Callers own commit/cancel behavior and selected-value persistence.
- My Catches F2 references this component; it must not create a duplicate fish picker authority.
