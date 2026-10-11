# Color System V3.1 Final

**System status: REVIEW.** All 64 previously unregistered explicit source literals are now mapped to identity-only legacy tokens. The shared Brand role mapping and global Success / Warning / Error / Info palette are not approved; D-015 remains open.

## Palette authority

| Group | Mapping | Rule |
|---|---|---|
| Brand | Primary and Secondary are review candidates; MorningGold is the sourced Accent. | Do not promote a page-scoped candidate into a global role without approval. |
| Background | Resolve through Background Registry V3. | No page-local background color. |
| Surface | LakeWhite and MistWhite. | Use the registered opaque/translucent surface role. |
| Glass | GlassWhite, GlassBorder and DeepOverlay; GLASS_A/B/C treatment remains in its source authority. | Preserve existing Glass treatment and page geometry. |
| Text | Primary, Secondary, Disabled and Inverse references are registered. | Use semantic token references only. |
| Border | Existing action borders are registered; Account/Privacy D5DFE6 is page-scope only. | A page border literal does not become a global border. |
| Status | Success, Warning and Info are missing. Error has an unapproved page-scoped Danger candidate. | No status color may be selected from a Frozen image or by convention. |

## Literal scan and legacy conflicts

The audit scanned 618 design sources: 478 text/spec/token/SVG files and 139 raster images for explicit metadata literals. It found 94 distinct RGBA literals: 30 already matched a shared token and 64 did not. The 0 explicit PNG metadata color literals were found.

The 64 uncovered values are registered in Color_Tokens_V3_FINAL.json as LegacyFrozen identity mappings with exact source paths, RGBA, opacity and a no-new-use rule. This preserves old Frozen SVG/page authorities without rewriting them. After the mapping, uncovered literal count is 0 within this scan scope. Tokenization records identity; it does not approve broader semantic use.

## Conflict record

Existing Frozen component/page authorities contain explicit color values outside the shared V1 palette. The V3.1 registry maps each exact value as a source-local compatibility token. No Frozen PNG/SVG was recolored, and no new semantic role was inferred.

## Acceptance

- Every scanned explicit production color literal resolves to a Color token.
- Every token declares HEX, RGBA, opacity, usage, forbidden usage and source paths.
- A missing semantic role blocks use; developers must not substitute a visually similar color.
- The registry scan can be rerun with verify_shared_foundation_v3.py.

**Freeze decision:** Color remains REVIEW until D-015 approves the global brand and status roles.