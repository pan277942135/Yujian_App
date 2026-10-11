# Shared Species Picker V1

Status: **FROZEN · SELECTOR CORE + SPECIES PICKER VARIANTS**
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

### MULTI_SELECT — shared selection state frozen; page shell remains page-owned

Used by My Catches F2. Reuse the existing frozen option/card geometry and selected visual state (teal selection border/fill/check); selection toggles remain selected while browsing/searching. Returning to F1 restores the selected set, and values within the species dimension combine with OR. No separate multi-select screen or new bitmap is introduced. The F1/F2 page shell remains governed by the My Catches page authority.

## Shared Selector Core contract

States: closed, open, query input, searching, results, no results, empty data, error, selected, multi-selected, committed, returned and cancelled. Search supports Chinese formal name, pinyin, initials and registered aliases with the Recognition Result authority's 300ms debounce. Single-select Tap Commit + Return; multi-select toggles and returns the retained set to the caller. Back dismisses the IME before leaving. Search stays visible around the IME; content may scroll. Use `touch.minimum` = 44×44dp for every actionable target, expose option/group/selected semantics, maintain logical focus with no focus trap, and honor OS fontScale by wrapping/growing rows rather than shrinking text.

Species image, category, species information and special-card fields render only when a named page authority supplies them. Recognition Result retains its exact frozen contents. Do not add inferred fields or alter its Frozen board.

## Scope boundaries

- This is a shared component, not a new product route.
- Callers own commit/cancel behavior and selected-value persistence.
- My Catches F2 references this component; it must not create a duplicate fish picker authority.
