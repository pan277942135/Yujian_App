# 渔见 · First-use Camera Guide V1

**Status:** FROZEN  
**Freeze date:** 2026-10-16  
**Scope:** Capture 页面首次相机进入时的两页教学 Overlay。

## Final frozen teaching set

### P01 · 竖屏拍摄示意
- Phone: portrait
- Fish: horizontal
- Errors: 鱼头不在画面 / 鱼体过小 / 遮挡过多
- Primary action: `下一张`

### P02 · 横屏拍摄示意
- Phone: landscape
- Fish: horizontal
- Errors: 鱼头不在画面 / 鱼体过小 / 遮挡过多
- Primary action: `开始拍摄`
- **Active P02 landscape/horizontal-fish visual source is PENDING and must be newly supplied.**
- The former P03 binary must **not** be reused as active P02.

## Removed / historical
The former **P02 · 竖拍鱼类拍摄示意** (portrait phone + vertical fish) is removed from the active sequence.
The former **P03 · 横屏拍摄示意** binary remains historical/compatibility only.

## Interaction
- Active pager contains exactly two pages: `P01 → P02`.
- `上一张` appears only on P02.
- P01 → `下一张` → P02.
- P02 → `开始拍摄` → Capture Ready.
- `跳过` exits from either page.
- `开始拍摄` never auto-triggers the shutter.
- Completion is persisted only after `跳过` or final `开始拍摄`.
- Back / abandoned / permission-denied / gallery paths do not mark completion.

## Visual rules
- Both active pages use horizontal fish.
- One correct hero + three error examples.
- Natural morning lake / restrained gold visual language.
- No HUD, scanner, radar, neon or decorative technology effects.
- Guide inherits Capture safe-area and shared component contracts.

## Runtime boundary
This freezes the two-page design/interaction surface. It does not claim the pending P02 raster source or Android Runtime evidence as PASS.
