# Recognition Result Layout Geometry V1.1

Status: **FROZEN — STATE-SPECIFIC RESULT AUTHORITY**

Machine-readable authority: `layout_geometry_contract.json`.

The seven Frozen Result assets are the visual authority. Result does not use a
universal Hero aspect ratio.

## Coordinate model

- canonical viewport: 360dp wide;
- system insets remain host-owned;
- the Result content width is approximately 322dp at the canonical viewport;
- content remains vertically scrollable when text, IME, or accessibility scale requires it.

## State-specific Hero geometry

At the canonical 360dp viewport the target is approximately:

| State | Hero W × H | Aspect |
| --- | ---: | ---: |
| High | 322 × 210dp | 1.53 |
| Medium | 322 × 178dp | 1.81 |
| Low | 322 × 178dp | 1.81 |
| No Fish | 322 × 245dp | 1.31 |
| Image Quality | 322 × 214dp | 1.50 |

Width adaptation preserves each state's aspect family. A compact height may
reduce the Hero by at most 12% while preserving state identity and reachable
actions.

## Shared navigation and surfaces

- High, Medium, Low, No Fish, and Image Quality use `BACK_CENTER_TITLE`;
- title: `识别结果`; Back remains at the left and the title is centered by the
  viewport, independently of Back width;
- Species Selector remains `BACK_TITLE` with a left-aligned title;
- Result Hero and Result Glass use additive restrained Result radius tokens;
- all Result Glass surfaces use the shared Mist Glass implementation.

## State composition

### High

Hero → Species Identity → vertical three-row Metadata Card → Story Card →
equal-width Dual CTA.

Metadata rows are Length / Weight / Location with `请输入` / `请选择`
placeholders. The canonical examples are `42.6 cm`, `1.28 kg`, and `千岛湖`.

### Medium

Hero → one unified Candidate Glass Panel (prompt, candidate row, other-species
action) → Metadata → Story → Dual CTA after explicit selection.

The suggested candidate is not selected automatically. Selected Medium uses the
restrained Result Gold token; Species Selector selected remains Teal.

### Low

Hero → `无法确认是什么鱼` → manual/retake actions → vertical Metadata → Story
→ Dual CTA. Metadata and Story remain editable before species resolution.

An unresolved CTA remembers its requested destination, opens Species Selector
with `LOW_MANUAL`, and resumes only after a species is selected. No unknown-
species FishRecord is created. Returning unconfirmed preserves the draft and
clears the pending destination.

### No Fish / Image Quality

Hero → one unified Recovery Glass Panel containing title, guidance, Retake, and
Gallery. Recovery actions use non-teal-solid shared variants.

## Hero media

- High / Medium / Low use the original oriented photo and the detector bbox only
  as FishSafeRect guidance;
- prefer subject crop fill when the complete FishSafeRect fits;
- use Subject Safe Fit only when Fill would clip the safe region;
- No Fish / Image Quality use full-source Evidence Fit;
- no stretch, generated fill, blurred duplicate, detector box, or classifier
  crop is permitted.

## Story and CTA

- title: `写下这次鱼获的故事`;
- placeholder: `记录这一刻的感受……`;
- maximum: 300 Unicode code points;
- live counter is rendered as `0/300`;
- voice is omitted unless a real speech-recognition capability can write a
  transcript;
- CTA copy is `继续记忆` and `保存本次鱼获`;
- the right save action uses the additive Result Save variant: light surface,
  DeepLakeBlue content, and MorningGold edge/accent.
