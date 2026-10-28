# Normal Home Haptic Spec V1

Status: **FROZEN**

## Automatic haptics

None.

The following never trigger haptic feedback:

- page entry
- page resume
- hero idle micro-breath
- pager settling
- statistics refresh
- data refresh
- media load/failure

## User interactions

- Primary capture button: inherit the shared YuJian capture-button light-impact convention.
- Hero-card tap: no custom haptic in V1.
- Pager swipe: no custom detent haptic.
- 鱼种 / 鱼获 / 全部: no custom haptic.
- 记录天数: informational; no haptic.
- account/avatar tap: no custom haptic.

Platform accessibility feedback is not prohibited; this spec forbids page-authored extra haptic patterns.

No new haptic behavior may be added without a versioned spec revision.
