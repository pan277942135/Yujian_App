# Species Detail Zero Catch State V1

Status: **FROZEN — SPECIES DETAIL SUBSTATE**  
Date: **2026-09-29**  
Scope: **Fish Guide · 02 · 鱼种详情 · 无我的鱼获记录 / Zero Catch**  
Trigger: **savedCount = 0**

---

## 0. Authority

Parent page authorities remain unchanged:

1. `design/pages/fish_guide/species_detail/frozen/Fish_Species_Detail_Baitiao_V1.png`
2. `design/pages/fish_guide/species_detail/Species_Detail_Page_Contract_V1.md`

Zero-catch state visual:

- `design/pages/fish_guide/species_detail/frozen/Fish_Species_Detail_Zero_Catch_V1.png`
- Source: `白条钓法卡_湖畔清晨指南.png`
- Dimensions: **941 × 1672**
- Size: **1,815,753 bytes**
- SHA-256: `bd3d4b0cee6c34e3c275cc55bc88730f26dadbb4f390230ed06931ae0347b2b4`

### Critical authority boundary

The zero-catch PNG is authoritative **only for the My Species / 我的{鱼种} zero-record region and its relationship to the already-frozen Species Detail page**.

It does **not** reopen or replace:

- top navigation;
- species header;
- background treatment;
- Knowledge Card Carousel;
- black-gold card content;
- adjacent-card geometry;
- `NN / 05` behavior.

If any upper-page pixels differ from the parent frozen authority, the parent Species Detail authority wins.

The `04 / 05` shown in this visual is an example retained from the parent state. Zero Catch does not force card index 04; the state applies at any centered knowledge-card index.

---

## 1. State trigger

Canonical condition:

```
savedCount = count(
  FishRecord
  where saveStatus = SUCCESS
  and finalSpeciesId = currentSpeciesId
)

ZeroCatchState = savedCount == 0
```

This is equivalent to the Fish Guide encounter state **UNLIT** for the current species.

The user may still browse the full Species Detail knowledge experience.

---

## 2. Frozen zero-catch layout

Only the bottom My Species block changes.

Normal recorded state:

```
我的白条                         12次记录 >
[ real FishRecord preview ] [ real FishRecord preview ]
```

Zero-catch state:

```
我的白条                           0次记录

                 还没有记录

              去记录鱼获  >
```

### Invariants

- keep the existing Mist Glass outer container;
- remove both catch-image preview tiles completely;
- do not keep empty thumbnail frames;
- do not add an illustration;
- do not add an empty-state icon;
- do not add a second nested glass card;
- reduce the My Species module height naturally after image removal;
- preserve comfortable vertical breathing room;
- the module remains visually subordinate to the Knowledge Card.

---

## 3. Header behavior

Header:

```
我的{speciesName}                0次记录
```

Rules:

- `我的{speciesName}` remains left aligned;
- `0次记录` remains right aligned as factual metadata;
- **no chevron** at savedCount = 0;
- the header row is **not tappable** at savedCount = 0;
- `0次记录` is not a button.

Reason:

Opening `My Catches + species filter` would only produce a second empty surface and adds an unnecessary interaction step.

When savedCount becomes >=1, the normal recorded-state header behavior returns:

```
我的{speciesName}                N次记录 >
```

and the header/count/chevron may open species-filtered My Catches according to the parent contract.

---

## 4. Empty copy

Frozen primary copy:

`还没有记录`

Rules:

- one short line only;
- no long explanatory paragraph;
- no achievement language;
- no “解锁” language;
- no “第一条” task pressure in the primary message;
- do not repeat the species name unnecessarily.

Forbidden additions include:

- `保存第一条白条鱼获后，会出现在这里`
- `去解锁你的第一条白条`
- `捕获白条即可点亮`
- multi-line empty-state education.

The UI should feel like a quiet absence of personal archive content, not an onboarding screen.

---

## 5. Text Action

Frozen action:

`去记录鱼获  >`

Component:

- shared **Text Action V1**
- no filled button
- no outlined button
- no large gold CTA
- no standalone icon button

Navigation:

```
去记录鱼获 >
    ↓
Recognition Capture entry
```

Rules:

- opens the normal capture / recognition flow;
- does **not** preselect or force the current species;
- does **not** create a FishRecord until the normal save flow succeeds;
- does not change LIT/UNLIT state on tap;
- state changes only after a qualifying FishRecord is successfully saved.

---

## 6. What stays unchanged

Zero Catch must not modify:

- Back behavior;
- species identity;
- species descriptor;
- knowledge-card count;
- active card index;
- carousel swipe/snap behavior;
- adjacent card previews;
- `NN / 05`;
- vertical-scroll behavior;
- Reduce Motion semantics.

The user can continue browsing all available species knowledge even with zero personal catches.

---

## 7. Transition to recorded state

After the first qualifying FishRecord save succeeds:

```
savedCount: 0 → 1
Encounter: UNLIT → LIT
Zero Catch → One Record
```

On the next resolved render of Species Detail:

- `0次记录` becomes `1次记录 >`;
- Text Action empty body disappears;
- one real FishRecord preview appears;
- no fake second preview is inserted;
- My Species header becomes navigable to filtered My Catches;
- no celebratory unlock screen;
- no automatic page/card navigation.

---

## 8. Accessibility

- `我的{speciesName}` is announced as section heading;
- `0次记录` is announced as status text, not a control;
- `还没有记录` is readable body text;
- `去记录鱼获` exposes a clear action role and label;
- no information depends on low opacity alone;
- text scaling must not create a second line of explanatory copy by design.

---

## 9. Acceptance Gate

- [x] Parent Species Detail top region remains governed by the existing Frozen Authority.
- [x] Zero Catch changes only the My Species region.
- [x] savedCount = 0 is the trigger.
- [x] Header shows `我的{speciesName}` + `0次记录`.
- [x] No chevron is shown at 0.
- [x] Header/count are not tappable at 0.
- [x] Catch preview tiles are completely removed.
- [x] No placeholder fish/photo is fabricated.
- [x] No empty-state icon is added.
- [x] Primary copy is only `还没有记录`.
- [x] Only action is Text Action `去记录鱼获 >`.
- [x] Action opens normal Recognition Capture without species preselection.
- [x] Zero Catch applies at any knowledge-card index; the authority image's `04 / 05` is illustrative.
- [x] First successful qualifying save transitions naturally to the 1-record state.
- [ ] Runtime evidence is tracked in Overview / implementation closure without reopening this state contract.

The unchecked Runtime Evidence item does not reopen this frozen substate design.
