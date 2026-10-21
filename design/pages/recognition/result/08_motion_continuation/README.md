# RR09 · 动效接续

**菜单归属：** 识别结果 / Recognition Result → 动效接续  
**设计状态：** FROZEN · SPEC ONLY  
**Runtime / playback:** NOT_RUN  
**产品状态变更：** 无；不增加第四个 Processing 状态或第六个 Result 状态。

## 当前权威

- [Recognition Result Motion Spec V2 — 金光转译揭晓](../motion/Recognition_Result_Motion_Spec_V2.md)
- [Machine-readable Motion V2 contract](../motion/recognition_result_motion_contract_v2.json)
- System acceptance contract: `design/system_v3/10_Page_Visual_Acceptance_Contracts/Recognition_Result/Recognition_Result_Motion_Contract_V2.md`
- Inherited Processing authority: `design/pages/recognition/spec/Recognition_Visual_Fidelity_V1_2.md`
- Existing photo mapping authority: `design/pages/recognition/result/media/Recognition_Result_Hero_Media_Contract_V1.md`

## Frozen visual sequence

1. **鱼体高亮锁定** — real fish contour/Halo is the gold-light source; species name remains hidden.
2. **照片自然接续** — an explicit intermediate frame bridges Processing to the Result Hero inside the existing 200 ms RESOLVE window.
3. **金光转移** — gold moves from the fish toward the species title below the photo.
4. **名称凝结中** — only incomplete glyph fragments are visible; the full name must not appear early.
5. **鱼种名称揭晓** — the Chinese name becomes legible; a validated scientific English name sits directly beneath it when available; one restrained gold sweep completes the reveal.
6. **余辉消散** — the particles and glow disappear; the normal static Result page remains fully interactive.

## Frozen timing

| Phase | Time from existing RESOLVE start |
|---|---:|
| Photo continuity / Result handoff | 0–200 ms |
| Gold transfer | 200–380 ms |
| Name formation | 380–550 ms |
| Name reveal / gold sweep | 550–680 ms |
| Afterglow fade | 680–850 ms |

The existing 200 ms RESOLVE is reused for the photo handoff; do not add a second page-entry delay. CTA readiness is independent of animation completion.

## Boundaries

- Full reveal is limited to a new `RESULT_HIGH` recognition session.
- Medium candidates, Low, No Fish, Image Quality, Technical Failure, and historical/reopened results do not replay the reveal.
- Keep the Chinese species name below the photo, with the validated scientific name directly beneath it when available. Never place the species title over the fish image.
- Motion V1 remains the baseline for non-conflicting Result interactions; V2 supersedes only the scoped Result-entry reveal restrictions.
- No confetti, fireworks, sound, haptic, persistent glow, or new product state.
- Reduce Motion removes particle travel and the title sweep; result semantics and actions remain unchanged.

**Important:** This menu freezes the motion specification, not a visual asset or runtime implementation. Android runtime, browser playback, screenshot parity, and performance remain NOT_RUN until evidence is attached.
