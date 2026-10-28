# FishRecordDetail Asset Generation Spec V1

Status: **FROZEN**  
Freeze date: **2026-09-30**  
Design Manager owner: **03 · 鱼体资产生成**  
Scope: **NOT_GENERATED / GENERATING / FAILED + READY handoff**

## 1. Product role

Asset generation is a stateful scenario inside the same FishRecordDetail. It is not a new route and must not replace the A-side record.

The user-facing lifecycle remains:

```text
NOT_GENERATED
      ↓ Generate
GENERATING
   ├─ success → READY
   └─ failure → FAILED
                  ↓ Retry
              GENERATING
```

READY is presented by **02 · B 面 · 鱼获记忆** and is not duplicated as a fourth generation-board page.

## 2. Frozen placement

Generation controls live in the A-side **鱼获记忆** section below the record content. The top navigation, A-side Hero, catch facts, note and media remain usable while generation is pending or failed.

The generation module must not become a full-screen blocking step.

## 3. NOT_GENERATED

Required treatment:

- Section title: **鱼获记忆**
- Primary message: **为这次相遇生成一份鱼获记忆**
- One clear action: **生成鱼获记忆**
- Keep the treatment quiet and documentary; no game unlock language.
- Flip Icon is hidden.
- Do not show an empty B-side placeholder.

Forbidden:

- “解锁 B 面”
- progress meter before work starts
- coin / reward / rarity language
- fake preview asset

## 4. GENERATING

Required treatment:

- Section title remains **鱼获记忆**
- Primary message: **正在生成鱼获记忆…**
- Supporting copy: **可以继续浏览或离开，完成后会保留在这条鱼获里。**
- A-side remains fully usable.
- Flip Icon remains hidden.
- User may leave FishRecordDetail; generation continues according to backend capability.

Progress policy:

- no fabricated percentage;
- no fabricated ETA;
- determinate progress may be added later only if the backend provides trustworthy progress;
- spinner / restrained breathing indicator is acceptable;
- no celebratory animation.

## 5. FAILED

Required treatment:

- Primary message: **鱼获记忆生成失败**
- Supporting copy: **原鱼获记录不受影响。**
- Action: **重新生成**
- Retry returns to GENERATING.
- A-side data and media remain unchanged.
- Flip Icon remains hidden.

Failure copy must not expose provider names, queue names, worker IDs, GPU details or internal error codes.

## 6. READY handoff

On success:

- lifecycle becomes READY;
- generation module no longer owns presentation;
- 02 · B 面 becomes available;
- Flip Icon becomes visible;
- if `first_b_reveal_done = false`, the frozen one-time reveal rule in Overview applies.

Foreground completion:
- stabilize current A-side;
- perform at most one automatic A → B reveal;
- mark reveal consumed only after B-side is actually visible.

Background completion:
- keep the reveal pending;
- next real FishRecordDetail presentation starts on A-side, then reveals B once.

## 7. Concurrency and retry rules

- one active generation job per FishRecord;
- repeated taps during GENERATING must not create parallel user-visible jobs;
- retry is allowed only from FAILED or a backend-expired equivalent mapped to FAILED;
- a previously valid READY asset wins over transient regeneration failure unless the product explicitly invalidates that asset.

## 8. Accessibility / Reduce Motion

- state meaning cannot depend on animation;
- screen readers announce state changes once;
- Reduce Motion removes non-essential breathing / flip movement;
- controls keep minimum 44dp touch targets.

## 9. Acceptance gate

FROZEN when all are true:

1. NOT_GENERATED / GENERATING / FAILED are represented in one scenario board.
2. A-side remains the stable page shell in all three states.
3. READY is not duplicated in 03.
4. Flip Icon is hidden until READY.
5. no fake percentage / ETA / internal infrastructure copy exists.
6. success handoff obeys the one-time first-reveal authority.

## 10. Frozen raster Visual Authority

Scenario board: `design/pages/fish_record/detail/frozen/generation/FishRecordDetail_Asset_Generation_States_V1_Frozen.png`. It shows NOT_GENERATED, GENERATING, and FAILED on one board. READY remains owned by 02 · B-side and is not a fourth generation state.

`AUTH_REQUIRED` is an action-availability variant, not a lifecycle state. Its supporting copy is **登录后可以生成鱼获记忆。** Do not add AUTH_REQUIRED as a fifth lifecycle column.
