# Color System V3.1 Final

**System status: READY.** The sole effective semantic color source is `Color_Tokens_V3_FINAL.json` as selected by the D-015 decision. Existing Frozen page colors retain their source-local legacy identities. No page may add an unregistered literal.

## Frozen semantic roles

| Class | Effective mapping | Rule |
|---|---|---|
| Brand Primary | `color.action_primary` on `color.lake_white` | Brand emphasis and core CTA. |
| Brand Secondary | `color.lake_blue` on `color.lake_white` | Secondary brand emphasis. |
| Brand Accent | `color.morning_gold` | Restricted to brand, important moments and meaningful catches; not normal text/status. |
| Success | `color.status_success_foreground` / `color.status_success_surface` | Completion, successful save, successful operation. |
| Warning | `color.status_warning_foreground` / `color.status_warning_surface` | Attention and non-blocking risk. |
| Error | `color.status_error_foreground` / `color.status_error_surface` | System, AI, network or upload failure. |
| Info | `color.status_info_foreground` / `color.status_info_surface` | Information and guidance. |
| Danger | `color.legacy_e66b61ff`, page-scoped | User-initiated destructive action only. Never promote to global semantic. |

## Semantic boundary

`Error` means a system or operation failure. `Danger` means a user-initiated destructive action such as account/data deletion or clearing records. Danger remains Page Scoped. It is not an alias for Error.

The approved solid-surface candidate pairs meet the packet's computed normal-text contrast threshold of 4.5:1. This is a static color-pair calculation; photography, gradients, and composed glass remain subject to consuming-page contrast acceptance. The recommendation pair and contrast context are recorded in the D-015 review packet.

## Other color groups

- Background resolves only through `Background_Registry_V3.json`.
- Surface: LakeWhite / MistWhite. Glass: GlassWhite / GlassBorder / DeepOverlay, with page-owned GLASS_A/B/C treatment.
- Text: Primary, Secondary, Disabled and Inverse use registered token IDs.
- Border: shared action borders are registered; Account & Privacy `#D5DFE6` remains page-scope identity only.
- The 64 legacy identity tokens preserve source authorities with `NO_NEW_USE`; they do not authorize broader use.

## Acceptance

1. Every production HEX/RGB/RGBA literal resolves to a Color token; current orphan count is 0.
2. Every token declares ID, HEX, RGBA, opacity, usage, forbidden usage and source.
3. Each semantic role resolves through this registry. Danger is absent from global semantic status and is marked page-scoped.
4. Run `scripts/design/verify_shared_foundation_v3.py` for static token, reference, orphan and gate validation.

D-015 is RESOLVED. The FINAL token registry is the only effective development source; candidate alternatives are historical and non-effective.
