# RR09 · 动效接续 V3 / V2 历史基线

**菜单归属：** 识别结果 / Recognition Result → 动效接续
**设计状态：** V3 PROPOSED FOR MAIN ADOPTION · V2 FROZEN HISTORICAL BASELINE
**Runtime / playback:** NOT_RUN
**产品状态变更：** 无；不增加第四个 Processing 状态或第六个 Result 状态。

## 当前提案权威（待 main 采纳）

- [Recognition Result Motion Spec V3 — SWOS 金光接续](../motion/Recognition_Result_Motion_Spec_V3.md)
- [Machine-readable Motion V3 contract](../motion/recognition_result_motion_contract_v3.json)
- System contract: `design/system_v3/10_Page_Visual_Acceptance_Contracts/Recognition_Result/Recognition_Result_Motion_Contract_V3.md`
- Cross-page handoff: `design/pages/recognition/continuity/Processing_Result_Optical_Continuity_Contract_V1.md`
- Existing photo mapping authority: `design/pages/recognition/result/media/Recognition_Result_Hero_Media_Contract_V1.md`

V3 is proposed, not yet main-authoritative. V2 is preserved byte-for-byte at the three historical paths below and remains available under the Design Manager's explicitly deprecated `RR09 V2 · 历史基线` submenu.

## V3 proposed visual sequence

1. **照片自然接续 0–200ms** — first presented Result frame begins RR09; preserve the same photo/Hero crop and trusted fish-gold source.
2. **金光转移 200–380ms** — warm gold moves from the real fish region to the existing Chinese species title below the photo.
3. **中文名形成 380–550ms** — partial Chinese glyph strokes only.
4. **中文名揭晓 550–680ms** — Chinese name becomes clear; no English scientific name and no empty English row.
5. **余辉消散 680–850ms** — temporary optics fade and the static Result remains interactive.

## V3 timing proposal

| Phase | Time from first presented Result frame |
|---|---:|
| Photo continuity after navigation | 0–200 ms |
| Gold transfer | 200–380 ms |
| Name formation | 380–550 ms |
| Name reveal / gold sweep | 550–680 ms |
| Afterglow fade | 680–850 ms |

Navigation is not delayed by 200 ms. RR09 T0 is the first actual Result frame; its 0–200 ms photo-continuity segment runs after navigation. CTA readiness is independent of animation completion.

## V3 boundaries

- Full reveal is limited to a new `RESULT_HIGH` recognition session.
- Medium candidates, Low, No Fish, Image Quality, Technical Failure, and historical/reopened results do not replay the reveal.
- Keep only the Chinese species name below the photo. Do not display or reserve an English scientific-name row.
- Motion V1 remains the baseline for non-conflicting Result interactions; V3 supersedes V2 only for T0, Processing handoff and title content.
- No confetti, fireworks, sound, haptic, persistent glow, or new product state.
- Reduce Motion removes particle travel and the title sweep; result semantics and actions remain unchanged.

**RR09 V2 historical references:** `../motion/Recognition_Result_Motion_Spec_V2.md`, `../motion/recognition_result_motion_contract_v2.json`, and `design/system_v3/10_Page_Visual_Acceptance_Contracts/Recognition_Result/Recognition_Result_Motion_Contract_V2.md` are retained unchanged. V2's English title and Resolve-origin wording are not current under the V3 proposal.

**Important:** V3 is a contract proposal, not a visual asset or runtime implementation. Android runtime, browser playback, screenshot parity, and performance remain NOT_RUN until exact-SHA evidence is attached.
