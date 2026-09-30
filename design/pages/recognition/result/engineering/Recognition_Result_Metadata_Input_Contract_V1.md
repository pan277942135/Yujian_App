# Recognition Result Metadata Input Contract V1

Status: **FROZEN**

Applies to:
- High;
- Medium after explicit species confirmation;
- Low after manual species selection.

Machine-readable authority: `metadata_input_contract.json`.

## 1. Idle page presentation

### Metadata Strip
Canonical:
- width: full content width
- height: **72dp**
- radius: `YuJianRadius.medium`
- surface: GLASS_A-light treatment
- three equal fields
- internal dividers: 1dp low-contrast vertical hairlines
- no row chevrons
- no “编辑记录” button

Fields:

**Length**
- label: `长度`
- blank value: `添加`
- entered example: `42.6 cm`

**Weight**
- label: `重量`
- blank value: `添加`
- entered example: `1.28 kg`

**Location**
- label: `地点`
- blank value: `添加地点`
- value: one line, ellipsis

Field text:
- label: 12sp / 18sp / TextSecondary
- value: 16sp / 22sp / Medium / TextPrimary
- blank value: TextSecondary

Entire cell is a touch target.

## 2. Length editor

Tap Length → `ResultFieldEditorSheet`.

- title: `记录长度`
- input label: `长度`
- suffix: `cm`
- keyboard: decimal numeric
- empty is allowed
- normalized decimal separator: `.`
- valid range when nonempty: **0.1–999.9 cm**
- maximum precision: **1 decimal place**
- leading/trailing whitespace removed
- invalid text is not committed
- primary action: `完成`
- secondary Text Action: `清除` when a value exists

Error copy:
- `请输入 0.1–999.9 cm 的长度`

## 3. Weight editor

Tap Weight → `ResultFieldEditorSheet`.

- title: `记录重量`
- suffix: `kg`
- decimal numeric keyboard
- empty allowed
- valid range: **0.01–999.99 kg**
- maximum precision: **2 decimal places**

Error copy:
- `请输入 0.01–999.99 kg 的重量`

## 4. Field editor sheet geometry

Private Result sheet:
- Modal bottom sheet
- top radius: **28dp**
- horizontal padding: **24dp**
- top padding: **16dp**
- bottom padding: system inset + **24dp**
- title: 20sp / 26sp Medium
- title → field gap: **16dp**
- input min height: **56dp**
- field → action gap: **20dp**
- Primary action height: 56dp
- drag handle follows platform Material sheet behavior and is visually neutral

This sheet is interaction UI, not part of Frozen page screenshot parity.

## 5. Location

Tap Location opens `ResultLocationSheet`.

Content:
1. title `添加地点`
2. manual single-line input
3. Secondary Strong action `使用当前位置`
4. Primary action `完成`

Manual text:
- max **40 Unicode code points**
- trim outer whitespace
- collapse repeated line breaks; single-line only
- empty allowed

Current location:
- never requested on page entry
- first explicit tap on `使用当前位置` shows a purpose explanation before OS permission
- accept fine or coarse permission
- denial leaves manual input usable
- denial never blocks save
- permission failure never clears an already typed location

Purpose copy:
`只有当你主动选择“使用当前位置”时，渔见才会获取你的位置，用于为当前鱼获添加地点。拒绝不会影响识鱼和保存鱼获。`

Runtime may resolve the actual place name asynchronously. While resolving, show `正在获取位置…`; do not change page layout.

## 6. Catch memory note

Canonical page block:
- height: **80dp**
- label: `留下本次鱼获感言`
- label: 13sp / 18sp Medium
- text: 15sp / 22sp
- placeholder: `记录这一刻的感受…`
- max visible lines at canonical size: **3**
- maximum stored length: **120 Unicode code points**
- line breaks allowed
- IME action: Default/Newline
- outer block uses restrained GLASS_B/transparent treatment; it must remain lighter than Identity and CTA

The note edits in place.

When focused:
- preserve block width
- allow height growth up to **112dp**
- after 112dp, internal text scrolling may begin
- BringIntoView prevents IME coverage

## 7. Voice

Voice is optional capability, not decorative UI.

If speech-to-text is not implemented and usable:
- hide the mic action completely.

If implemented:
- component: Icon Action / CONTEXT / voice
- touch target: 44dp
- glyph: 18–20dp
- placement: trailing edge of note label/content block
- states:
  - Idle
  - Listening
  - Processing
  - Error
  - Disabled

Behavior:
- voice recognition writes transcript into the note field
- appends at current cursor position
- 120-character limit still applies
- user can edit transcript before save
- permission/recognizer failure never clears existing text
- no auto-submit/save

Accessibility labels:
- Idle: `语音输入鱼获感言`
- Listening: `正在听`
- Processing: `正在转换语音`
- Error: `语音输入失败，请重试`

No continuous pulsing is required; Reduce Motion disables decorative motion.

## 8. Save and validation

Metadata is optional.

Save is allowed when:
- species is resolved;
- required record persistence inputs outside metadata are valid.

Length/Weight invalid intermediate editor text:
- cannot be committed;
- does not disable unrelated page navigation until user attempts editor completion.

On page save error:
- preserve committed length, weight, location and note.
- do not reset editors.
- duplicate save is blocked by shared Loading state.

## 9. State ownership

All committed metadata belongs to the current recognition Result session.

Changing species:
- must not erase metadata.

Retake:
- abandons the current Result attempt; unsaved metadata may be discarded after navigation.

Back:
- does not silently save metadata.


## 10. Metadata Edit Flow authority

The lightweight modification interaction is additionally frozen by:

`Recognition_Result_Metadata_Edit_Flow_V1.md`

Machine-readable authority:

`metadata_edit_flow_contract.json`

Conflict rule:
- numeric range, precision, optionality, note and voice semantics remain owned by this base Metadata Input Contract;
- Bottom Sheet titles/geometry, numeric autofocus and IME behavior, commit/dismiss semantics, location search/current/recent flows and recent-history rules are owned by Metadata Edit Flow V1;
- Metadata Edit Flow V1 supersedes the earlier simplified editor descriptions in Sections 2–5 where the two differ.

The Result page itself remains the frozen 72dp Metadata Strip. The edit-flow review board does not authorize replacing it with a vertical settings list.
