# FishRecordDetail B-side Visual Authority V1

Status: **FROZEN SPEC — approved visual bytes fixed; repository binary gate pending**  
Freeze decision date: **2026-09-30**  
Design Manager owner: **02 · B 面 · 鱼获记忆**

## 1. Canonical visual

Target canonical asset:

`design/pages/fish_record/detail/frozen/FishRecordDetail_B_Side_V1_Frozen.png`

The approved source is the exact uploaded PNG selected on 2026-09-30.

- Dimensions: **941 × 1672**
- Format: **PNG / RGB**
- Bytes: **2,095,527**
- SHA-256: **c07e684f6068e71ac2188819f9f69297343116a0c5db5b1089ef97be2f15582b**
- Re-encode: **FORBIDDEN**
- Resize / crop / recompress: **FORBIDDEN**
- Screenshot reconstruction: **FORBIDDEN**

The **specification is frozen now**. The Design Manager visual itself becomes repository-level **FROZEN** only after the canonical path contains these exact approved bytes and the SHA-256 gate passes.

## 2. A/B relationship

B-side is not an independent detail page. It is the reverse visual surface of the same FishRecordDetail.

The approved B-side preserves the A-side page shell and content hierarchy. The intended transformation is limited to the Hero visual surface plus the Flip control.

```text
A-side
real catch photo: person + fish + real scene
        ↓ same FishRecord
B-side
isolated fish body + calm water environment
```

The species identity, catch metadata, record note, memory/media content and page navigation remain the same FishRecord.

## 3. Frozen page invariants

The approved high-fidelity visual freezes the following:

### Top navigation

100% same product structure as A-side:

- Back
- title: 鱼获详情
- 鱼鉴
- 分享

B-side Flip is **not** a Top Navigation action.

### Hero outer geometry

The Hero card keeps the same page role, rounded frame, size, position and text hierarchy as the A-side reference.

### Hero visual content

B-side replaces the A-side capture scene with:

- one isolated grass carp body;
- no person;
- calm, clean water environment;
- fish remains the absolute visual subject;
- natural water light and depth;
- no HUD / neon / sci-fi / game treatment.

The visual communicates a digital fish memory without adding explanatory copy.

### Hero text

Content remains identical to A-side:

- `草鱼`
- `42.6 cm · 1.28 kg · 浙江 · 千岛湖`
- `编辑 >`

Do not add labels such as:

- B面
- 数字鱼体
- AI生成
- 鱼体资产
- 已生成

### Flip Icon

A page-internal Flip Icon is placed at the **top-right inside the Hero card**.

Frozen semantics:

- available only when B-side status = `READY`;
- used for explicit B → A on this surface;
- same stable position is used for manual A → B when the A-side READY treatment is produced;
- utility / on-media visual role;
- low visual weight;
- no text label;
- not a refresh/sync action.

The approved visual icon is the visual reference for this control; exact vector construction may later be normalized through the shared Icon Action system without changing its position or semantic role.

## 4. Water environment

The B-side water treatment is frozen by the approved high-fidelity visual:

- natural water body, not aquarium UI;
- blue-green / gray-blue family;
- restrained underwater light;
- visible but subordinate environmental depth;
- no magical halo;
- no data lines;
- no energy particles;
- no technology overlay.

The environment exists only to support the isolated fish body.

## 5. Lower-page invariants

The following remain the same FishRecordDetail content and are not B-side-specific alternate modules:

- 关于这次鱼获
- the existing catch note
- 鱼获记忆
- photo/video count
- 添加照片/视频
- 继续拍照
- 录制视频
- media grid

B-side does not duplicate or fork FishRecord data.

## 6. Behavior authority

Behavior is governed by:

`design/pages/fish_record/detail/FishRecordDetail_Overview_Authority_V1.md`

In particular:

- first READY B-side is automatically revealed at most once per FishRecord;
- after first reveal, new entries default to A-side;
- subsequent A ↔ B switching is manual;
- Flip Icon is available only when B-side = READY.

This document freezes the **visual authority**, not separate runtime state-machine logic.

## 7. Acceptance gate

Repository freeze requires all of the following:

1. canonical PNG exists at the target path;
2. dimensions = 941 × 1672;
3. byte size = 2,095,527;
4. SHA-256 = c07e684f6068e71ac2188819f9f69297343116a0c5db5b1089ef97be2f15582b;
5. Design Manager 02 points directly to the canonical PNG;
6. the old supplemental `fish_memory_reference.png` is no longer the 02 visual authority;
7. no page re-generation, SVG reconstruction, screenshot crop, or substitute image is accepted.
