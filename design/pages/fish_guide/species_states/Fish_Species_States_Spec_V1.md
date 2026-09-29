# Fish Species States Spec V1

Status: **FROZEN — STATE SEMANTICS / FALLBACK CONTRACT**  
Date: **2026-09-29**  
Scope: **Fish Guide · 03 · 鱼种状态 / Fish Species States**  

> This specification governs how Fish Guide Home and Species Detail behave when species encounter data, FishRecord counts, knowledge content, media, connectivity, or catalog consistency change.
>
> It does **not** redefine the frozen layout of **01 · 鱼鉴首页** or **02 · 鱼种详情**, and it does not freeze the internal visual/content system of the five black-gold knowledge cards.

---

## 0. Authority order

1. **Parent page authorities**
   - `design/pages/fish_guide/Fish_Guide_Home_Spec_V1.md`
   - `design/pages/fish_guide/species_detail/Species_Detail_Page_Contract_V1.md`
   - their frozen visual authorities
2. **State semantics / fallback authority**
   - `design/pages/fish_guide/species_states/Fish_Species_States_Spec_V1.md`
3. **03 visual flow reference**
   - `design/pages/fish_guide/species_states/Fish_Species_States_Visual_Authority_V1.md`
   - source UI: `Fish_Species_States_V1.png`
4. **UNLIT supporting visual**
   - `design/system/core_visual_v1/reference/supplemental/fish_guide_unlit_state.png`
5. Shared Design System contracts

If a miniature page shown inside the 03 flow poster conflicts with 01/02 frozen page layout, **01/02 always wins**.  
03 only owns the **state delta and fallback treatment**.

---

# 1. State model

Fish Guide state is decomposed into six independent axes:

```
SpeciesUiState
├── EncounterState
├── CatchCountState
├── KnowledgeContentState
├── MediaState
├── RuntimeState
└── CatalogConsistencyState
```

These axes must not be collapsed into one ambiguous boolean such as `locked`.

---

# 2. Encounter State

## 2.1 UNLIT

Definition:

> The current active catalog species has **0 successfully saved FishRecord entries** whose final species maps to this species.

UNLIT is a **personal encounter state**, not permission/access control.

### Home

- species remains visible in the Fish Guide carousel;
- species name remains readable;
- species remains tappable;
- card may use restrained desaturation / mist-white reduction;
- catch count is 0 / absent according to the frozen Home visual contract;
- no lock icon is required;
- never render as disabled.

### Species Detail

- detail remains accessible;
- five knowledge-card positions remain browseable when content exists;
- My Species resolves to the zero-record state;
- do not hide knowledge because the user has not caught the species.

### Forbidden

- grayscale disabled card;
- `???` species name;
- large lock icon;
- “unlock first” CTA;
- blocking Species Detail;
- rarity/achievement semantics.

---

## 2.2 LIT

Definition:

> The current active catalog species has **≥1 successfully saved FishRecord** whose final species maps to this species.

### Home

- normal species-card treatment;
- saved record count may be shown as secondary archive metadata.

### Species Detail

- normal page;
- My Species displays saved count;
- previews derive only from real FishRecord media.

---

## 2.3 Canonical derivation

Do **not** persist `isLit` as an independent source of truth in V1.

Canonical derivation:

```
savedCount = count(
  FishRecord
  where saveStatus = SUCCESS
  and finalSpeciesId = currentSpeciesId
)

EncounterState =
  savedCount >= 1 ? LIT : UNLIT
```

This prevents `LIT + 0 records` drift.

Recognition attempts, model predictions, unsaved results, deleted records, and raw photos do not light a species.

---

# 3. Catch Count State

Catch count uses the same canonical `savedCount`.

## 3.1 0 records

```
savedCount = 0
EncounterState = UNLIT
```

State semantics are owned here by 03, while the concrete Species Detail zero-record UI is frozen under 02:

- `design/pages/fish_guide/species_detail/Species_Detail_Zero_Catch_State_V1.md`
- `design/pages/fish_guide/species_detail/frozen/Fish_Species_Detail_Zero_Catch_V1.png`

My Species:

- title remains `我的{speciesName}`;
- factual count is `0次记录`;
- no chevron and no My Catches header navigation at zero;
- copy is `还没有记录`;
- the only action is Text Action `去记录鱼获 >`;
- no fake catch thumbnail;
- no B-side image;
- no generated placeholder fish pretending to be a catch.

## 3.2 1 record

- count: `1 次记录`;
- display exactly one qualifying recent real FishRecord preview;
- do **not** fabricate a second thumbnail for symmetry.

## 3.3 2 records

- count: `2 次记录`;
- display the two most recent qualifying real FishRecord previews.

## 3.4 3+ records

- count shows full total, e.g. `12 次记录`;
- preview area still shows only the **2 most recent** qualifying real FishRecord photos.

## 3.5 Ordering

Preview records:

1. catch/capture time descending when available;
2. otherwise FishRecord creation time descending.

If a qualifying record exists but its media is unavailable, keep the record in the count and resolve the preview through **Media State**, not by silently dropping the record from the total.

---

# 4. Knowledge Content State

The five page-level positions are frozen by **02 · 鱼种详情**.  
03 defines what happens if the knowledge payload is incomplete.

## 4.1 COMPLETE

```
availableKnowledgeSlots = 5 / 5
```

- normal five-position carousel;
- normal `NN / 05`.

## 4.2 PARTIAL

```
availableKnowledgeSlots = 1…4 / 5
```

Rules:

- carousel still contains exactly five positional slots;
- missing slot keeps its index;
- missing slot uses the official neutral “content unavailable” placeholder;
- user may continue to swipe to available slots;
- `NN / 05` remains positional and does not renumber around missing content;
- never copy another species' card;
- never duplicate a neighboring card;
- never AI-generate replacement knowledge automatically.

Example:

```
01 available
02 available
03 unavailable
04 available
05 available

→ still shows 01/05 ... 05/05
```

## 4.3 UNAVAILABLE

```
availableKnowledgeSlots = 0 / 5
```

- keep Species Header;
- preserve the Species Detail page hierarchy;
- keep the knowledge-card region footprint;
- show a restrained unavailable treatment;
- My Species remains independently usable when FishRecord data exists;
- do not convert the entire page into a generic full-screen error unless the species identity itself cannot be resolved.

---

# 5. Media State

Media availability must not change factual state.

## 5.1 Species artwork unavailable

When Fish Guide species artwork fails:

- species identity text remains;
- use the approved neutral system placeholder / silhouette treatment;
- preserve card geometry;
- do not generate a substitute fish;
- do not substitute another species image.

## 5.2 Knowledge-card artwork unavailable

If a specific card's rendered asset is unavailable:

- preserve its positional slot;
- use the official card-unavailable treatment;
- `NN / 05` remains unchanged;
- do not reuse an adjacent card.

If structured text content is separately available, later implementations may render an approved structured fallback only after that fallback is explicitly frozen; V1 must not invent one ad hoc.

## 5.3 FishRecord preview media unavailable

If a saved FishRecord exists but the preview photo cannot be rendered:

- count remains correct;
- preview slot uses neutral media-unavailable placeholder;
- if the FishRecord itself is valid, the slot remains tappable to FishRecordDetail;
- do not remove the record from the count;
- do not use B-side / species artwork / generated replacement.

---

# 6. Runtime State

Runtime state is independent from Encounter/Catch state.

## 6.1 LOADING

Use structural skeletons that preserve the frozen page hierarchy.

### Home

- preserve title/progress/carousel geometry;
- do not flash between LIT and UNLIT before savedCount is known.

### Species Detail

- preserve Header → Carousel → Indicator → My Species geometry;
- no fake knowledge/catch data during loading.

## 6.2 OFFLINE_WITH_CACHE

- show cached species/catalog/knowledge data;
- show locally available FishRecord metadata/media where possible;
- optional low-weight offline indicator may appear;
- browsing remains available;
- cached data must not be presented as newly refreshed.

## 6.3 OFFLINE_NO_CACHE

- preserve page identity and hierarchy;
- show restrained unavailable copy in the content region;
- allow Back;
- do not fabricate catalog or catch data;
- do not replace the page with unrelated generic artwork.

## 6.4 ERROR

For retriable network/data fetch error:

- preserve species identity when known;
- isolate failure to the smallest affected region;
- retry action may appear only in the failed region;
- no page-level primary CTA unless the whole page genuinely cannot resolve.

---

# 7. Catalog Consistency State

## 7.1 UNKNOWN_SPECIES_ID

A stale/deep-linked species ID that cannot be resolved against the active catalog:

- do not invent a species;
- do not map to a visually similar species;
- show a restrained unavailable state;
- Back remains available;
- this species does not increment Fish Guide `N / T`.

Detailed deep-link ownership remains in **04 · 鱼种导航**.

## 7.2 INACTIVE_SPECIES

An inactive/retired catalog species:

- excluded from current active catalog total `T`;
- existing historical FishRecords remain in My Catches;
- a stale Species Detail route must not silently redirect to another species;
- exact historical-detail policy is owned by 04 · 鱼种导航.

## 7.3 ORPHAN_FISH_RECORD

If a historical FishRecord references a species not present in the active catalog:

- preserve the FishRecord;
- do not count it toward current Fish Guide lit progress;
- do not synthesize a new catalog card.

---

# 8. Cross-state precedence

When multiple states occur together, use this precedence:

```
Catalog identity
    ↓
Runtime availability
    ↓
Knowledge/media availability
    ↓
Encounter / savedCount presentation
```

Examples:

### UNLIT + OFFLINE_WITH_CACHE

- show cached species knowledge;
- My Species remains 0;
- do not block detail because species is UNLIT.

### LIT + one catch preview image missing

- Encounter remains LIT;
- savedCount remains correct;
- missing preview uses media placeholder only.

### PARTIAL knowledge + 12 catches

- five positional card slots remain;
- missing knowledge slot shows unavailable treatment;
- My Species still shows `12 次记录` and recent real previews.

---

# 9. State Matrix

| State | Fish Guide Home | Species Detail | My Species |
|---|---|---|---|
| UNLIT / 0 | soft reduced emphasis, tappable | fully enterable when knowledge exists | zero-record treatment |
| LIT / 1 | normal card + count | normal detail | 1 real preview |
| LIT / 2 | normal card + count | normal detail | 2 real previews |
| LIT / 3+ | normal card + full count | normal detail | latest 2 previews only |
| Knowledge Partial | normal species browsing | missing slot placeholder, keep 5 positions | unaffected |
| Knowledge Unavailable | species remains addressable | preserve detail shell + unavailable knowledge region | remains usable if record data exists |
| Species artwork missing | neutral artwork placeholder | header/text preserved | unaffected |
| Catch preview missing | count still correct | page unchanged | neutral preview placeholder |
| Offline + cache | cached browse | cached detail | cached/local record data |
| Offline no cache | preserve home structure | preserve detail identity + unavailable content | no fabricated data |
| Unknown species ID | not added to catalog totals | restrained unavailable route | no fabricated mapping |

---

# 10. Motion / accessibility

- state transitions do not auto-navigate;
- no automatic haptic/sound;
- UNLIT/LIT cannot rely on color alone;
- unavailable placeholders have readable accessible labels;
- Reduce Motion removes decorative transition motion but does not alter state semantics;
- loading skeletons must not announce repeated noisy updates to accessibility services.

---

# 11. Ownership boundaries

**03 owns:**

- state definitions;
- data-to-state derivation;
- fallback semantics;
- zero/one/two/many catch behavior;
- missing content/media behavior;
- loading/offline/error semantics;
- cross-state precedence.

**03 does not own:**

- Fish Guide Home base layout → 01;
- Species Detail base layout / carousel interaction → 02;
- deep-link routing → 04;
- knowledge content schema → 05;
- asset production rules → 06;
- motion choreography beyond state-transition invariants → 07;
- global responsive framework → 08;
- runtime evidence → 09;
- five black-gold card internal visual/content system → remains separate PARTIAL review.

---

# 12. Canonical copy contract

State copy is intentionally short. A state message must describe the current condition, not shame the user or introduce game semantics.

| Situation | Primary copy | Secondary / action |
|---|---|---|
| UNLIT / 0 records | `还没有记录` | no unlock language |
| Missing knowledge slot | `内容暂不可用` | `该卡片资料正在完善` |
| All knowledge unavailable | `鱼种资料暂不可用` | keep Back and My Species when available |
| Loading | no factual copy required | skeleton only |
| Offline + cache | cached content | optional low-weight `离线内容` |
| Offline no cache | `当前无法加载鱼种资料` | `检查网络后重试` |
| Retriable region error | `暂时无法加载` | `重试` |
| Unknown species ID | `该鱼种资料暂不可用` | Back |

Forbidden copy:

- `未解锁`
- `捕获后解锁`
- `解锁图鉴`
- `稀有` / `传奇` state language
- `???`
- fabricated catch-count copy.

---

# 13. State transition contract

State changes are driven by committed data changes, not animation timers.

## 13.1 Save first qualifying FishRecord

When a save transaction succeeds and the saved record's final species maps to the current species:

```
0 → 1
UNLIT → LIT
```

Rules:

- count changes only after save success;
- Home progress `N / T` increments only if this species was previously UNLIT;
- My Species becomes the 1-record state;
- no celebratory unlock screen;
- no confetti;
- no automatic navigation;
- no automatic haptic is required.

Any transition animation is owned by **07 · 动效与交互** and must remain restrained.

## 13.2 Save additional record

```
N → N + 1
LIT remains LIT
```

- total count updates;
- recent preview ordering recomputes;
- only the latest two qualifying preview slots are shown.

## 13.3 Delete a FishRecord

After a deletion is committed:

- decrement the canonical saved count;
- recompute preview ordering;
- when `1 → 0`, the species returns to **UNLIT**;
- Fish Guide progress `N / T` decrements accordingly;
- do not permanently preserve a historical `isLit=true` flag in V1.

## 13.4 Correct a saved FishRecord species

If a saved FishRecord's final species changes from A → B:

1. remove the record from species A's canonical count;
2. add it to species B's canonical count;
3. recompute EncounterState for both A and B;
4. recompute Fish Guide lit-progress `N / T` only after the correction is persisted.

This may produce:

```
A: LIT → UNLIT
B: UNLIT → LIT
```

No state is updated from an unsaved candidate selection.

## 13.5 Catalog activation change

When an active catalog species becomes inactive:

- remove it from active total `T`;
- recompute `N` against the active catalog only;
- preserve historical FishRecords;
- do not delete user records just because the species is inactive.

When a species becomes active, it joins `T`; its LIT status is derived from qualifying saved historical records that map to that stable species ID.

---

# 14. Visual fallback contract

## 14.1 UNLIT visual

Supporting visual reference:

`design/system/core_visual_v1/reference/supplemental/fish_guide_unlit_state.png`

Invariants:

- lighter / softer / lower-emphasis than LIT;
- species identity remains readable;
- fish remains recognizable;
- card remains visibly interactive;
- no dark disabled overlay;
- no large lock glyph;
- no opacity so low that biological identification becomes difficult.

The exact base page composition remains owned by 01.

## 14.2 Missing knowledge slot

Keep:

- the same card container footprint;
- the same carousel position;
- the same external `NN / 05` index.

Replace only the missing card content region with the approved neutral unavailable treatment.

Do not collapse the carousel from five slots to four.

## 14.3 Missing real-catch preview

Keep the preview slot only when it corresponds to a real FishRecord.

The placeholder communicates **media unavailable**, not “no record”.

This distinction is required:

```
0 records
≠
1 real record whose photo failed
```

## 14.4 Error isolation

Failure should be isolated to the smallest factual region:

- artwork failure → artwork region;
- one knowledge-card failure → that card slot;
- FishRecord image failure → that preview;
- knowledge API failure → knowledge region;
- unresolved species identity → page-level unavailable state.

---

# 15. Data / analytics invariants

The following values must be derived from the same stable species ID mapping:

- Fish Guide active total `T`;
- lit count `N`;
- per-species saved count;
- My Species count;
- preview query;
- LIT / UNLIT state.

Do not derive one surface from model labels and another from user-corrected final species.

For V1, the user's **persisted final species** on the saved FishRecord is authoritative for personal collection state.

---

# 16. Acceptance Gate

- [x] Encounter is derived from successfully saved FishRecord count.
- [x] UNLIT remains visible, readable, tappable, and detail-accessible.
- [x] LIT is not a game unlock state.
- [x] 0 / 1 / 2 / 3+ record behavior is deterministic.
- [x] My Species never fabricates catch photos.
- [x] Missing knowledge preserves the fixed five positional slots.
- [x] Missing media never changes factual count/state.
- [x] Offline + cache remains browseable.
- [x] Offline no-cache never fabricates content.
- [x] Unknown/inactive species do not silently map to another species.
- [x] 01/02 frozen page layouts override miniature layout differences in the 03 poster.
- [x] Five black-gold card internals remain outside this state freeze.
- [ ] Runtime evidence is attached under **09 · 验收证据**.

The final Runtime Evidence item does not reopen the frozen state contract.
