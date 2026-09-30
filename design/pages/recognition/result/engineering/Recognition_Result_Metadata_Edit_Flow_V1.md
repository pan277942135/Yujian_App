# Recognition Result Metadata Edit Flow V1

Status: **FROZEN**

Purpose: close the missing edit/modify interaction for optional catch metadata after species is resolved.

Machine-readable authority: `metadata_edit_flow_contract.json`.

Frozen interaction visual authority: `design/pages/recognition/result/metadata_edit/frozen/Recognition_Result_Metadata_Edit_Flow_V1_Frozen.png`.
Visual manifest: `design/pages/recognition/result/metadata_edit/frozen/manifest.json`. The board is authoritative only for Length / Weight / Location Bottom Sheets, keyboard/focus, validation, clear/re-edit, location search, current location, and recent locations. It is not authority for the underlying Result page composition.

## 0. Scope and authority

Applies to High, Medium after explicit species confirmation, and Low after manual species resolution.

This contract defines only the edit interaction for Length / Weight / Location. It does **not** replace the frozen Result page composition.

The frozen board at `design/pages/recognition/result/metadata_edit/frozen/Recognition_Result_Metadata_Edit_Flow_V1_Frozen.png` is the interaction visual authority for lightweight Bottom Sheet editing only. It is not authority for the underlying Result page composition. Elements that conflict with the current Result package are not adopted, including confidence %, the legacy Result CTA, the old vertical Metadata List, old Species Tag, or Gold action.

Current page presentation remains governed by State Frozen 05–07, Layout Geometry V1, and Metadata Input Contract V1.

## 1. Global edit principles

1. Metadata is optional.
2. Editing never navigates away from Result.
3. Tapping one field opens one lightweight modal Bottom Sheet over the current Result page.
4. Scrim dims the Result page but preserves context.
5. Finishing or selecting a valid value returns immediately to the same Result state.
6. Editing never reruns Recognition or changes species resolution.
7. Empty metadata never blocks Save / Continue Memory.
8. A field may be reopened and edited repeatedly.
9. Save error preserves the latest committed metadata.

## 2. Result page field states

The Result page continues to use the frozen `ResultMetadataStrip`.

- Length empty: `添加`; committed: `{value} cm`
- Weight empty: `添加`; committed: `{value} kg`
- Location empty: `添加地点`; committed: selected place display name
- Location resolving: `正在获取位置…`

The vertical three-row presentation on the review board is an interaction illustration only and does not replace the frozen 72dp Result Metadata Strip in V1.

## 3. Numeric edit sheet family

Private component: `ResultNumericEditSheet`.

Used by Length and Weight.

Geometry:
- Modal Bottom Sheet;
- lightweight wrap-content;
- target content height excluding IME: about **25%** of available content height;
- preferred band: **168–220dp**;
- top radius: **28dp**;
- horizontal padding: **24dp**;
- neutral platform drag handle;
- no full-screen editor at normal 320–411dp widths.

Centered title:
- Length: `鱼获长度`
- Weight: `鱼获重量`
- 17sp / 24sp / SemiBold / DeepLakeBlue.

Value row:
- numeric input minimum height **56dp**;
- preferred input width at 360dp: **200–216dp**;
- numeric value **32sp / 40sp**;
- unit **14sp / 20sp**;
- optional trailing clear icon target >= **28dp**, clears draft only and keeps sheet open.

Units are fixed: Length = `cm`, Weight = `kg`.

## 4. Keyboard and focus

On numeric-sheet entry:
1. focus the numeric field after the sheet is presented;
2. request decimal numeric IME automatically;
3. place caret at end of existing value;
4. do not require a second tap.

IME Done uses the same validation/commit path as `完成`.

The OS keyboard is not counted in the 25% sheet-height target.

## 5. Numeric actions

Bottom row:

### 清除
- shared Text Action / MUTED;
- visible when a committed value exists;
- clears the committed value and dismisses;
- does not affect other metadata.

### 完成
- shared Text Action / NORMAL or STRONG;
- valid draft → commit + dismiss;
- invalid draft → remain + inline error;
- no navigation.

Blank is valid because metadata is optional. Manually clearing the draft then tapping 完成 commits empty.

## 6. Numeric validation

Length:
- range **0.1–999.9 cm**
- max 1 decimal
- gentle error: `请输入有效的长度`

Weight:
- range **0.01–999.99 kg**
- max 2 decimals
- gentle error: `请输入有效的重量`

Zero, negative, malformed, out-of-range, or over-precision values cannot commit.

Back gesture / scrim tap dismisses without committing draft and preserves the prior committed value.

## 7. Location picker sheet

Private component: `ResultLocationPickerSheet`.

Geometry:
- lightweight medium Bottom Sheet;
- target content height about **40%**;
- preferred band **280–420dp**;
- may grow to max **56%** for results;
- results list scrolls internally;
- top radius 28dp;
- horizontal padding 16–24dp.

Centered title: `鱼获地点`.

## 8. Location search

Search field:
- placeholder: `搜索地点（如：千岛湖、富春江）`;
- min height **48dp**;
- leading search icon;
- trailing query-clear icon when nonempty;
- single line;
- max query 40 Unicode code points;
- debounce **300ms**.

Rules:
- text search never requests location permission;
- query is search-only and does **not** mutate the Result field;
- result row = pin icon + place name + secondary region/address.

Tap a search result:
1. commit display name;
2. add/move to Recent;
3. dismiss immediately;
4. update Result metadata.

No extra `完成` tap.

## 9. Current location

Distinct row: `使用当前位置`.

Tap:
1. show purpose explanation if permission not granted;
2. request Fine/Coarse permission only after explicit tap;
3. resolve a readable place;
4. success → commit + Recent + dismiss;
5. failure → remain + restrained inline error;
6. denial → remain; search/recent stay usable.

Never request location permission on Result entry, Location-sheet entry, or place search.

## 10. Location states

- IDLE_RECENT
- SEARCHING
- RESULTS
- EMPTY: `没有找到相关地点`
- ERROR: `地点搜索暂不可用，请稍后重试`
- LOCATING: `正在获取位置…`
- PERMISSION_DENIED

## 11. Recent locations

Section title: `最近使用`; trailing action: `清除`.

Rules:
- device-local V1 history;
- maximum **3** entries;
- newest first;
- de-duplicate by normalized display name;
- selecting moves item to front;
- clearing history does not clear current Result location;
- no login or sync requirement.

Tap a recent item → commit immediately + dismiss.

## 12. Location commit model

Only these commit:
- search result selection;
- recent selection;
- successful current location;
- explicit clear of committed location.

These do not commit:
- typing query;
- search loading/error;
- permission denial;
- sheet dismissal.

When current Location exists, expose `清除` in the sheet. Clearing current Location does not clear Recent.

## 13. Accessibility

Numeric:
- announce label + value + unit;
- 清除 / 完成 are separate actions;
- validation error is announced.

Location:
- search label `搜索地点`;
- current-location row is a Button;
- result/recent rows expose place name + secondary address;
- locating state is announced;
- permission denial is not fatal.

## 14. Save / navigation invariants

- metadata remains optional;
- Save / Continue Memory remain available once species is resolved;
- Bottom Sheet never creates a FishRecord;
- Save never consumes an uncommitted numeric draft;
- opening/dismissing an editor does not alter Result navigation history.

## 15. Provider boundary

Design does not mandate a geocoder provider. Platform geocoder, approved backend place search, or equivalent privacy-compatible service is allowed.

Provider-independent requirements:
- deterministic commit rules;
- no implicit permission;
- no raw-query auto-commit;
- recent history;
- graceful failure.

## 16. Hard failures

FAIL if:
1. editing navigates to a new page;
2. numeric keyboard requires a second tap under normal conditions;
3. invalid numeric input commits;
4. dismissal silently commits draft;
5. location query writes directly into Result while typing;
6. selecting search/current/recent still requires a second 完成 tap;
7. location permission is requested automatically;
8. denial blocks Save;
9. clearing Recent clears current Result location;
10. metadata becomes mandatory;
11. editing changes species or reruns Recognition.
