# 我的鱼获 · Timeline Scroll V1 · High-Fidelity Audit V1

Status: **ACTIVE_CLOSURE**
Date: 2026-09-29
Parent: `my_catches_v2`

## Authority inputs

Structural / interaction authority:
- `My_Catches_Timeline_Scroll_Spec_V1.md`

Current page visual authority:
- `design/system/core_visual_v1/reference/my_catches_v2.png`

Recovered historical behavior / layout source:
- `鱼获时间轴滚动规范展示板.png`

Recovered T0 high-fidelity board:
- `design/pages/fish_records/list/frozen/timeline_v1/My_Catches_Timeline_Final_Board.png`
- Source: `我的鱼获时间线设计定稿.png`
- 1491 × 1055, 2,125,355 bytes, SHA-256 `d247f9c28708004c00979e2c60ccdba8ea72a97291cf3e2c59daa3623b6937c5`
- Role: **DESIGN_ONLY / RECOVERED_REFERENCE**; not current visual authority.

Shared-system authority:
- `Morning_Lake_Master_V1 / BG_DATA`
- `YuJianPrimaryCaptureButton`

## Review conclusion

Both recovered boards remain useful as **behavior / layout references**, but neither is ready to be frozen as the current visual authority. T0 contains the state coverage requested by the earlier audit, but its prominent sunrise / visible-sun background conflicts with current BG_DATA. The image is preserved byte-for-byte and is not edited.

## T0 gate review

| Gate | Result | Evidence |
|---|---|---|
| Current no-sun BG_DATA | FAIL | T0 uses visible sunrise light and a strong golden water reflection; BG_DATA requires the no-sun Morning_Lake_Master_V1 treatment. |
| Legacy 5-item bottom navigation removed | PASS | The shown timeline states use a bottom-center camera action and do not show the legacy 5-item navigation. |
| Scenic day-summary thumbnail removed | PASS | Day summaries use location text / count cues; no separate scenic summary thumbnail is shown. |
| Per-record capture time removed | PASS | Rows show species and catch facts without a per-record clock time. |
| Current row geometry and image hierarchy | PASS | The board describes square real-photo thumbnails and keeps species / dimensions / chevron ahead of the low-weight mark. |
| Growth Mark low-weight treatment | REVIEW | Mark chips are present in the correct row position; their gold emphasis needs review against the current shared low-weight treatment before a new board freezes. |
| Month Sticky and month handoff | PASS | T0 includes a labeled scrolled Month Sticky state and a cross-month handoff strip. |
| 6–10 collapsed and expanded | PASS | Both states are explicitly shown. |
| >10 collapsed and day detail | PASS | T0 shows the five-row timeline state and a separate date-detail screen. |

The BG_DATA gate fails, so Timeline remains `ACTIVE_CLOSURE`; T0 stays `DESIGN_ONLY / RECOVERED_REFERENCE`. The current My Catches main visual remains the visual authority.

The final Timeline high-fidelity must be recomposed against the current My Catches V2 visual language and the current shared design system.

---

## P0 — must close before final visual freeze

### TL-HIFI-01 · Background source mismatch

Historical board uses a strong sunrise / visible sun background.

Current system authority for `my_catches_v2` is:

`Morning_Lake_Master_V1 → BG_DATA`

The visible-sun Sunrise Hero master is reserved for Empty Home only.

Final Timeline high-fidelity:
- use no-sun Morning_Lake_Master_V1;
- apply BG_DATA treatment;
- no page-specific new sun;
- no strong golden reflection competing with records.

### TL-HIFI-02 · Legacy bottom navigation

Historical board contains a legacy 5-item bottom navigation.

Current My Catches V2 frozen visual uses the shared bottom-center primary Camera Button and does not use that historical navigation composition.

Final Timeline board must:
- remove historical bottom navigation;
- use the current shared primary capture button;
- keep capture entry fixed and independent of list scrolling.

### TL-HIFI-03 · Legacy day-summary thumbnail card

Historical board renders a scenic/location thumbnail inside the day summary.

Current My Catches V2 structure is lighter:
- date node;
- location icon / location text;
- catch count / species count;
- then FishRecordRowCard list.

Final Timeline board must remove the extra scenic summary thumbnail/card.

### TL-HIFI-04 · Legacy per-record time

Historical board shows per-record clock time in the row.

Current V2 timeline prioritizes:
- real thumbnail;
- species;
- length / weight;
- Growth Mark if any;
- Chevron.

Per-record time is not part of the current primary row hierarchy and should not return in the final Timeline board.

### TL-HIFI-05 · >10 day-detail visual is not actually frozen

The interaction rule is frozen:

`>10 → show 5 → 查看全部 N 条鱼获 → 当天鱼获详情`

However, the historical board only documents the rule; it does not provide a final full high-fidelity day-detail screen.

Therefore:
- interaction semantics = FROZEN;
- day-detail visual = ACTIVE_CLOSURE.

A dedicated day-detail high-fidelity state is required before Timeline visual freeze.

### TL-HIFI-06 · Sticky Month needs a real visual state

Month Sticky behavior is frozen, but the current high-fidelity library does not contain a final real-screen state that demonstrates:
- month header pinned below page chrome;
- normal day content scrolling beneath it;
- handoff from previous month to next month.

The final Timeline board must include at least one true scrolled/sticky state.

---

## P1 — visual hierarchy closure

### TL-HIFI-07 · Date node weight

The early board makes `23 / 20 / 12` visually too dominant.

Final direction:
- reduce date-number scale about 10–15% from the early board;
- keep the date rail recognizable;
- do not let date numbers compete with fish thumbnails / species.

### TL-HIFI-08 · Day summary semantics

The final day summary should preserve both archive density cues:

Single valid location:
`地点 · N条鱼获 · M种鱼`

Multiple locations or no usable location:
`N条鱼获 · M种鱼`

After search / filter:
- N and M are recalculated from visible records.

### TL-HIFI-09 · Row image geometry

Final row must follow current List Image V1:
- real original FishRecord photo;
- 1:1 target;
- 82dp × 82dp target;
- 14dp radius;
- subject-aware crop when reliable metadata exists.

Do not use the older narrow landscape thumbnail geometry as the final V2 authority.

### TL-HIFI-10 · Growth Mark

At least one Timeline example must show the current low-weight Growth Mark system:
- `第100条`
- `首条草鱼`
- `最长`
- `最重`
- or `最长 · 最重`

No crown / star / trophy visual language.

### TL-HIFI-11 · 6–10 expansion affordance

Collapsed:
`查看另外 N 条鱼获⌄`

Expanded:
`收起⌃`

This row is Timeline furniture:
- no solid primary button;
- no gold emphasis;
- lower weight than FishRecordRowCard.

---

## Recommended final high-fidelity board

The Timeline high-fidelity subpage should show these actual UI states:

1. **Default / 1–5 catches**
2. **Scrolled / Month Sticky**
3. **6–10 collapsed**
4. **6–10 expanded**
5. **>10 collapsed**
6. **>10 Day Detail**
7. **Cross-month handoff** (can be a smaller behavior strip rather than a full phone)

This is enough to make the Timeline visual and behavior unambiguous for frontend development.

## Freeze gate

Timeline Scroll V1 can return to visual `FROZEN` only when:
- BG_DATA / no-sun background is applied;
- legacy nav / day-thumbnail / record-time elements are removed;
- row geometry matches My Catches V2;
- Sticky Month has real high-fidelity evidence;
- >10 Day Detail has real high-fidelity evidence;
- 6–10 collapsed + expanded are both shown;
- final visual is reviewed against current My Catches main page.
