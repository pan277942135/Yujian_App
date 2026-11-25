# Processing Early image retirement — 2026-10-18

User decision: **delete the separate Early design bitmap**, not the entire IMAGE_RECOGNIZING state or the internal CAPTURED phase.

- Removed from current repository tree: `design/pages/recognition/design/01_Capture_Transition_Frozen.png` (historical 941×1672; SHA-256 `e3101806e7e850cbcb46eb46b184ae049acbf379e2cf36ba04549421e8d16b58`). Its bytes remain recoverable from Git history.
- Remaining current Processing authority: `02_AI_Understanding_Frozen.png` → 图片识别中; `03_Fish_Highlight_Frozen.png` → 已定位到鱼体; `04_Fish_Identifying_Frozen.png` → 鱼种识别中.
- No surviving PNG bytes have been modified. Exactly 3 product states, 3 design keyframes. RESOLVE remains a transition.
- V3.1 real event phases CAPTURED, DETECTING, OUTLINE, CLASSIFYING and RESULT_READY remain unchanged in meaning and minimum-beat semantics; CAPTURED and DETECTING must share the one IMAGE_RECOGNIZING visible design.
- Existing instrumented test code still has a historical `IMAGE_RECOGNIZING_EARLY` diagnostic checkpoint for CAPTURED. It is **not** a fourth design and must not be required for current visual parity. Verify/adjust actual Android first-phase user-visible copy before Runtime acceptance.
- Figma existing Master Board page MB-08 still requires visual sync; the repository board contract retires MB-08. Do **not** claim Figma canvas or deployed Design Manager was changed by this PR. Synchronize downstream after merge/approval.
- R05 previously used the retired PNG to prove a strong-intensity limit. That limit is now **REVIEW_REQUIRED** until actual Android FULL/LITE runtime evidence closes it. Do not invent a replacement image or claim Release PASS.

Gate: DESIGN_CHANGE_REVIEW_REQUIRED, RUNTIME_NOT_RUN, MAIN_UNCHANGED.
