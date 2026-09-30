# NH03 · Page States & Exceptions / 页面状态与异常 · Design Spec V1

Status: **SPEC FROZEN — COMBINED BOARD PENDING**  
Scope: **Normal Home state continuity / failure containment**  
Output policy: **COMBINED_BOARD**

---

## 1. Purpose

NH03 freezes how Home behaves visually when record state is unresolved, refreshing, partially unavailable, or temporarily failing.

Core principle:

> **Unknown is not Empty. Failure is not Empty. Existing Normal Home content must remain stable whenever a valid resolved snapshot already exists.**

NH03 does not create a new Home style. Every state inherits the same Morning Lake environment, Header language, typography, spacing and capture system.

---

## 2. State priority

Home state must resolve in this order:

1. **Resolved valid records > 0** → Normal Home
2. **Resolved valid records = 0** → Empty Home
3. **Record collection unresolved** → Home Resolving
4. **Refresh/error with previous valid Normal Home snapshot** → preserve that Normal Home snapshot
5. **Media unavailable for one valid record** → keep the record and replace only its media surface

The following are forbidden as state selectors:

- authentication state
- statistics availability
- avatar availability
- catch-image availability
- transient network failure

---

## 3. Combined board coverage

NH03 must be expressed in **one 2×2 combined board**, not four separate page Hi-Fis.

### A · Home Resolving

Use when the application does not yet know whether valid FishRecords exist.

Required:

- Morning Lake environment remains visible;
- Header remains visible;
- Primary Capture Button remains available unless navigation itself is blocked;
- statistics use quiet unresolved placeholders such as `—`, not fake zeroes;
- no Empty Home copy;
- no bobber/empty-state illustration;
- no fake catch card;
- no celebratory or error copy;
- one restrained neutral Hero-region placeholder may occupy the canonical Hero footprint.

The placeholder must be quiet: Mist White / Blue-Gray surface, no shimmer sweep, no skeleton cascade, no fake fish.

### B · Catch Image Unavailable

A valid FishRecord remains valid even if its media cannot be loaded.

Required:

- preserve the HOME Hero geometry;
- preserve species and available metadata;
- media region uses an approved neutral fish/media placeholder;
- do not substitute another catch image;
- do not substitute another species illustration;
- do not collapse the card;
- do not remove the record from Pager ordering;
- do not switch to Empty Home.

The placeholder must remain visually subordinate to real media.

### C · Refresh while Normal Home is already resolved

If a valid Normal Home snapshot exists, refresh is **state-preserving**.

Required:

- keep Header, stats, section header, Hero and CTA in place;
- keep current Pager selection if the selected record still exists;
- no full-screen spinner;
- no page-wide opacity wash;
- no content blanking;
- no statistics reset to zero;
- no temporary switch to Empty Home.

Background refresh may be visually silent. If implementation exposes activity, it must be local and low-weight and may not alter layout.

### D · Error with previously resolved Normal Home

If refresh fails but a valid resolved snapshot exists:

- retain the last resolved Normal Home snapshot;
- keep navigation and capture available where safe;
- do not display a red error dashboard;
- do not replace Hero with an error card;
- do not switch to Empty Home;
- optional transient system feedback must not become persistent page chrome.

Only a later **successful resolved state of zero valid records** may transition to Empty Home.

---

## 4. Media fallback semantics

For unavailable Hero media:

- card container, radius, overlay and text hierarchy stay unchanged;
- placeholder is neutral and species-agnostic;
- fallback must not imply recognition confidence or rarity;
- fallback must not use the text character `鱼` as final production artwork;
- fallback must not introduce an action button inside the image surface.

If media later succeeds, replace only the media content; no card entrance animation is required.

---

## 5. Statistics during uncertainty

### Initial unresolved

Display:

```
—      —      —
鱼种   鱼获   记录天数
```

Do not show `0 / 0 / 0` before records are resolved.

### Refresh with stale snapshot

Keep the last resolved values until a new valid result replaces them.

### Partial server-stat failure

Follow Normal Home Behavior V1 data precedence. Missing remote totals do not erase locally derivable values.

---

## 6. Motion / Haptic / Sound

NH03 adds no new decorative motion.

Forbidden:

- loading shimmer across the whole page;
- Hero bounce after image recovery;
- error shake;
- automatic haptic on refresh/error/recovery;
- sound on failure/recovery.

Reduce Motion does not change state semantics.

---

## 7. Board production contract

The future NH03 board must contain exactly four labeled specimens:

1. `A · Resolving`
2. `B · Image Unavailable`
3. `C · Refresh Preserves Home`
4. `D · Error ≠ Empty`

The labels belong to the design board only and are not runtime UI.

The board must reuse NH01/NH02 visual language rather than inventing four page variants.

---

## 8. Acceptance criteria

- [ ] unresolved data never displays Empty Home
- [ ] unresolved stats never display fake zeroes
- [ ] media failure never removes a valid record
- [ ] stale Normal Home remains visible during refresh
- [ ] refresh failure preserves the last valid snapshot
- [ ] only resolved zero valid records transition to Empty Home
- [ ] no full-screen error/loading dashboard is introduced
- [ ] no new automatic motion, haptic or sound is introduced

---

## 9. Frozen decisions

1. Unknown ≠ Empty.
2. Error ≠ Empty.
3. Media failure is local to media.
4. Refresh preserves a resolved Normal Home snapshot.
5. Initial unresolved stats use `—`, never fake `0`.
6. NH03 is one combined board, not multiple independent Hi-Fis.
7. Board labels are documentation only, not runtime UI.
