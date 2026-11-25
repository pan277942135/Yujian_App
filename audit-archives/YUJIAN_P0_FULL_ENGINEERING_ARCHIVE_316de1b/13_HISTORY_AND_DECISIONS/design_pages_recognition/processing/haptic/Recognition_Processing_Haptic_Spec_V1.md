# Recognition Processing Haptic Spec V1

Status: **FROZEN**

Policy: **NONE**

Scope: **Recognition Processing only**.

## Frozen rule

Recognition Processing does not emit haptic feedback for:

- entering 图片识别中;
- entering 已定位到鱼体;
- entering 鱼种识别中;
- RESOLVE;
- automatic/background state changes.

The primary AI Moment remains visual. No vibration is added merely because the detector located a fish or because Processing advanced.

## Boundaries

- User-initiated controls may use their shared component haptic contract if that component already owns one.
- Recognition Result / Issue surfaces own any downstream haptic decision separately.
- Processing must not synthesize confirmation, warning, or impact haptics.
- System accessibility / vibration settings are naturally respected because Processing emits no haptic.

## Change control

Adding any Processing haptic requires a new explicit design decision and version. Runtime implementation must not invent one.
