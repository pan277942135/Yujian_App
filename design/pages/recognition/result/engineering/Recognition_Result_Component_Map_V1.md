# Recognition Result Component Map V1.1

Status: **FROZEN — RESULT AUTHORITY RECONSTRUCTED**

Machine-readable authority: `component_map.json`.

Every visible Result element resolves to a shared component, a Result-private
component, or a media contract. The seven Frozen assets win over stale defaults.

## Shared mapping

| Visible element | Component | Variant |
| --- | --- | --- |
| Result pages Back + title | Top Navigation V1 | `BACK_CENTER_TITLE` |
| Species Selector Back + title | Top Navigation V1 | `BACK_TITLE` |
| Continue memory | Action Button V1.1 | `SECONDARY_MUTED` |
| Save catch | Action Button V1.1 | `RESULT_SAVE` |
| Manual species / Medium other | Action/Text Action V1 | Result-authorized variants |
| Recovery Retake | Action Button V1.1 | `SECONDARY_STRONG` |
| Recovery Gallery | Action Button V1.1 | `SECONDARY_MUTED` |
| All Result cards and panels | Mist Glass | Result radius token |
| Page background | Background System | `BG_CONTENT` |

## Result-private components

- `ResultHeroViewport`: original oriented photo placement governed by Hero Media;
- `ResultSpeciesIdentityRow`: species identity and correction action;
- `ResultMetadataStrip`: a vertical three-row Length / Weight / Location card;
- `ResultMemoryNote`: multiline Story Card with live 300-code-point counter;
- `ResultCandidateRow` / `ResultCandidateCard`: Medium-only explicit selection;
- `ResultRecoveryPanel`: unified No Fish / Image Quality copy and actions;
- `ResultNumericEditSheet` and `ResultLocationPickerSheet`: same-page metadata editing;
- `ResultInlineError`: retryable save error above the CTA.

## State composition

- High: Hero → Species Identity → vertical Metadata → Story → Dual CTA;
- Medium: Hero → one Candidate Glass Panel → Metadata / Story / CTA after selection;
- Low: Hero → message → manual/retake → Metadata → Story → Dual CTA;
- No Fish / Image Quality: Hero Evidence Fit → one unified Recovery Glass Panel.

Low unresolved CTA opens Species Selector with `LOW_MANUAL`, remembers the
requested destination, and resumes only after species selection. It never emits
an unknown-species FishRecord.

## Prohibited substitutions

- universal Hero aspect ratio;
- page-private white-alpha surfaces imitating Glass;
- teal solid Result Save or Recovery buttons;
- horizontal three-column High metadata;
- independent Recovery buttons outside their panel;
- automatic Medium selection;
- no-op voice controls;
- detector/classifier crops or Frozen screenshot crops as runtime media.

## Page-scoped shared-component exceptions · 2026-10-10 design audit

The machine-map keys `BACK_CENTER_TITLE` and `RESULT_SAVE` are **Result page composition profiles**, not additional globally frozen Top Navigation or Action Button variants. The generic systems keep their three variant definitions each. See:

- `design/system/components/top_navigation/exceptions/Recognition_Result_Centered_Title_V1.md`
- `design/system/components/action_button/exceptions/Recognition_Result_Actions_V1.md`

At the current Result V1.1 authority, `继续记忆` has the Result-muted treatment; `保存本次鱼获` uses Result Save's light/gold-edge treatment. Confirmed dual CTAs are equal width with 12dp gap as the Result geometry contract specifies, **not** the historical shared button usage example 44/56. Global buttons and Species Selector's left-title navigation retain their preexisting Frozen contracts.
